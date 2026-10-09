package minixiangshan.mem.L2cache

import chisel3._
import chisel3.util._
import minixiangshan.config._

/**
  * Conservative I-side next-line prefetcher.
  *
  * Training is a fire-and-forget Valid event from a registered L2 demand
  * boundary.  The candidate is held in a one-entry non-flowing buffer, so no
  * downstream ready signal can feed back into the demand path.
  */
class L2IPrefetch(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val train = Flipped(Valid(UInt(XLEN.W)))
    val clear = Input(Bool())
    val candidate = Decoupled(UInt(XLEN.W))
  })

  val candidateValid = RegInit(false.B)
  val candidateAddr = RegInit(0.U(XLEN.W))

  io.candidate.valid := candidateValid
  io.candidate.bits := candidateAddr

  val trainAtLastLineOfPage = io.train.bits(11, l2BlockOffBits).andR
  val trainLine = io.train.bits(XLEN - 1, l2BlockOffBits)
  val nextLineAddr = Cat(trainLine + 1.U, 0.U(l2BlockOffBits.W))
  val candidateSlotAvailable = !candidateValid || io.candidate.ready

  when(io.clear) {
    candidateValid := false.B
  }.elsewhen(io.train.valid && candidateSlotAvailable) {
    candidateValid := !trainAtLastLineOfPage
    candidateAddr := nextLineAddr
  }.elsewhen(io.candidate.fire) {
    candidateValid := false.B
  }
}

/**
  * Small, timing-oriented D-side Best-Offset prefetcher.
  *
  * One registered demand event tests one of four offsets against a 16-entry
  * recent-request table.  Offset selection is a four-cycle serial scan, rather
  * than a parallel CAM plus winner tree.  The demand path has no ready signal:
  * training may be discarded during the short scan, and a full candidate slot
  * drops newer candidates without ever applying backpressure.
  */
class L2DPrefetch(implicit p: Parameters) extends NSModule {
  private val recentEntries = 16
  private val recentIndexBits = log2Ceil(recentEntries)
  private val lineBits = XLEN - l2BlockOffBits
  private val pageLineBits = 12 - l2BlockOffBits
  private val offsetLines = Seq(1, 2, 4, 8)
  private val offsetCount = offsetLines.size
  private val offsetIndexBits = log2Ceil(offsetCount)
  private val scoreBits = 3
  private val roundSamples = 16
  private val confidenceThreshold = 2

  val io = IO(new Bundle {
    val train = Flipped(Valid(UInt(XLEN.W)))
    val clear = Input(Bool())
    val candidate = Decoupled(UInt(XLEN.W))
  })

  val recentValid = RegInit(VecInit(Seq.fill(recentEntries)(false.B)))
  val recentTags = Reg(Vec(recentEntries, UInt((lineBits - recentIndexBits).W)))
  val scores = RegInit(VecInit(Seq.fill(offsetCount)(0.U(scoreBits.W))))

  val trainValid = RegInit(false.B)
  val trainLine = RegInit(0.U(lineBits.W))
  val offsetIndex = RegInit(0.U(offsetIndexBits.W))
  val sampleCount = RegInit(0.U(log2Ceil(roundSamples).W))

  val scanActive = RegInit(false.B)
  val scanIndex = RegInit(0.U(offsetIndexBits.W))
  val scanBestScore = RegInit(0.U(scoreBits.W))
  val scanBestOffset = RegInit(1.U(lineBits.W))
  val learnedEnabled = RegInit(false.B)
  val learnedOffset = RegInit(1.U(lineBits.W))

  val candidateValid = RegInit(false.B)
  val candidateAddr = RegInit(0.U(XLEN.W))

  io.candidate.valid := candidateValid
  io.candidate.bits := candidateAddr

  val offsetValues = VecInit(offsetLines.map(_.U(lineBits.W)))
  val selectedOffset = offsetValues(offsetIndex)
  val testedLine = trainLine - selectedOffset
  val testedIndex = testedLine(recentIndexBits - 1, 0)
  val testedTag = testedLine(lineBits - 1, recentIndexBits)
  val recentHit = recentValid(testedIndex) && recentTags(testedIndex) === testedTag
  val selectedScore = scores(offsetIndex)
  val scoreMax = ((1 << scoreBits) - 1).U
  val scoreAfter = Mux(
    recentHit && selectedScore =/= scoreMax,
    selectedScore + 1.U,
    selectedScore
  )

  val processTrain = trainValid && !scanActive
  val startScan = processTrain && sampleCount === (roundSamples - 1).U
  val acceptTrain = io.train.valid && !scanActive && !startScan

  val trainedCandidateLine = trainLine + learnedOffset
  val trainedCandidateSamePage =
    trainedCandidateLine(lineBits - 1, pageLineBits) ===
      trainLine(lineBits - 1, pageLineBits)
  val candidateSlotAvailable = !candidateValid || io.candidate.ready

  val scanScore = scores(scanIndex)
  val scanOffset = offsetValues(scanIndex)
  val scanSelectCurrent = scanScore > scanBestScore
  val scanWinnerScore = Mux(scanSelectCurrent, scanScore, scanBestScore)
  val scanWinnerOffset = Mux(scanSelectCurrent, scanOffset, scanBestOffset)

  when(io.clear) {
    recentValid.foreach(_ := false.B)
    scores.foreach(_ := 0.U)
    trainValid := false.B
    offsetIndex := 0.U
    sampleCount := 0.U
    scanActive := false.B
    scanIndex := 0.U
    scanBestScore := 0.U
    scanBestOffset := offsetValues.head
    learnedEnabled := false.B
    learnedOffset := offsetValues.head
    candidateValid := false.B
  }.otherwise {
    trainValid := acceptTrain
    when(acceptTrain) {
      trainLine := io.train.bits(XLEN - 1, l2BlockOffBits)
    }

    when(io.candidate.fire) {
      candidateValid := false.B
    }

    when(processTrain) {
      val insertIndex = trainLine(recentIndexBits - 1, 0)
      recentValid(insertIndex) := true.B
      recentTags(insertIndex) := trainLine(lineBits - 1, recentIndexBits)
      scores(offsetIndex) := scoreAfter

      when(learnedEnabled && candidateSlotAvailable && trainedCandidateSamePage) {
        candidateValid := true.B
        candidateAddr := Cat(trainedCandidateLine, 0.U(l2BlockOffBits.W))
      }

      when(offsetIndex === (offsetCount - 1).U) {
        offsetIndex := 0.U
      }.otherwise {
        offsetIndex := offsetIndex + 1.U
      }

      when(startScan) {
        scanActive := true.B
        scanIndex := 0.U
        scanBestScore := 0.U
        scanBestOffset := offsetValues.head
      }.otherwise {
        sampleCount := sampleCount + 1.U
      }
    }

    when(scanActive) {
      when(scanIndex === (offsetCount - 1).U) {
        learnedEnabled := scanWinnerScore >= confidenceThreshold.U
        learnedOffset := scanWinnerOffset
        scores.foreach(_ := 0.U)
        sampleCount := 0.U
        offsetIndex := 0.U
        scanActive := false.B
      }.otherwise {
        scanBestScore := scanWinnerScore
        scanBestOffset := scanWinnerOffset
        scanIndex := scanIndex + 1.U
      }
    }
  }
}

class L2PrefetchCandidate(implicit p: Parameters) extends NSBundle {
  val addr = UInt(XLEN.W)
  val isInstruction = Bool()
}

/**
  * Timing boundary between the two predictors and L2 arbitration.
  *
  * Each predictor already owns a registered output.  This hub adds a
  * non-flowing two-entry queue and a tiny round-robin arbiter, so neither L2
  * candidate readiness nor the other predictor can feed back into demand
  * training.  A clear drains queued speculation without affecting demand.
  */
class L2PrefetchHub(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val iTrain = Flipped(Valid(UInt(XLEN.W)))
    val dTrain = Flipped(Valid(UInt(XLEN.W)))
    val iClear = Input(Bool())
    val dClear = Input(Bool())
    val queueClear = Input(Bool())
    val candidate = Decoupled(new L2PrefetchCandidate)
  })

  val iCandidateValid = WireDefault(false.B)
  val iCandidateAddr = WireDefault(0.U(XLEN.W))
  val iCandidateReady = WireDefault(false.B)
  val dCandidateValid = WireDefault(false.B)
  val dCandidateAddr = WireDefault(0.U(XLEN.W))
  val dCandidateReady = WireDefault(false.B)

  if (l2IPrefetchEnabled) {
    val iPrefetch = Module(new L2IPrefetch)
    iPrefetch.io.train.valid := io.iTrain.valid
    iPrefetch.io.train.bits := io.iTrain.bits
    iPrefetch.io.clear := io.iClear
    iCandidateValid := iPrefetch.io.candidate.valid
    iCandidateAddr := iPrefetch.io.candidate.bits
    iPrefetch.io.candidate.ready := iCandidateReady
  }

  if (l2DPrefetchEnabled) {
    val dPrefetch = Module(new L2DPrefetch)
    dPrefetch.io.train.valid := io.dTrain.valid
    dPrefetch.io.train.bits := io.dTrain.bits
    dPrefetch.io.clear := io.dClear
    dCandidateValid := dPrefetch.io.candidate.valid
    dCandidateAddr := dPrefetch.io.candidate.bits
    dPrefetch.io.candidate.ready := dCandidateReady
  }

  val queueReset = reset.asBool || io.queueClear
  val queue = withReset(queueReset) {
    Module(new Queue(new L2PrefetchCandidate, 2, pipe = false, flow = false))
  }
  val preferD = RegInit(false.B)
  val chooseI = iCandidateValid && (!dCandidateValid || !preferD)
  val chooseD = dCandidateValid && !chooseI

  queue.io.enq.valid := !io.queueClear && (chooseI || chooseD)
  queue.io.enq.bits.addr := Mux(chooseD, dCandidateAddr, iCandidateAddr)
  queue.io.enq.bits.isInstruction := !chooseD
  iCandidateReady := !io.queueClear && chooseI && queue.io.enq.ready
  dCandidateReady := !io.queueClear && chooseD && queue.io.enq.ready

  when(queue.io.enq.fire) {
    preferD := chooseI
  }

  io.candidate.valid := queue.io.deq.valid && !io.queueClear
  io.candidate.bits := queue.io.deq.bits
  queue.io.deq.ready := io.candidate.ready
}
