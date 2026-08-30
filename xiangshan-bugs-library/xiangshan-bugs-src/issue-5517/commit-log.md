# Commit Log
- Issue: #5517
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5517
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5517
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5517
- Changed files: 9
- Additions: 357
- Deletions: 331

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala`
- `src/main/scala/xiangshan/frontend/bpu/abtb/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/BypassShadowBuffer.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/MicroTageTable.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/Parameters.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 43a7c6b199b..013d86f9082 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -199,15 +199,23 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   io.fromFtq.train.ready := predictors.map(_.io.trainReady).reduce(_ && _)
 
   /* *** predictor specific inputs *** */
-  abtb.io.redirectValid := redirect.valid
-  abtb.io.overrideValid := s3_override
-
-  utage.io.foldedPathHist         := phr.io.s0_foldedPhr
-  utage.io.foldedPathHistForTrain := phr.io.trainFoldedPhr
-  utage.io.abtbPrediction         := abtb.io.abtbResult
-  utage.io.abtbPosVec             := abtb.io.abtbPos
-  utage.io.overrideValid          := s3_override
-  utage.io.redirectValid          := redirect.valid
+  abtb.io.redirectValid  := redirect.valid
+  abtb.io.overrideValid  := s3_override
+  abtb.io.normalPathHist := phr.io.oldFoldedPhr
+
+  // utage.io.foldedPathHist         := phr.io.oldFoldedPhr
+  // utage.io.foldedPathHistForTrain := phr.io.trainFoldedPhr
+  utage.io.abtbPrediction := abtb.io.abtbResult
+  utage.io.abtbPosVec     := abtb.io.abtbPos
+  utage.io.overrideValid  := s3_override
+  utage.io.redirectValid  := redirect.valid
+
+  utage.io.normalPathHist   := phr.io.oldFoldedPhr
+  utage.io.s1PathHist       := phr.io.s1_foldedPhr
+  utage.io.overridePathHist := phr.io.s3_foldedPhr
+
+  utage.io.s1StartPc       := s1_prediction.target
+  utage.io.overrideStartPc := s3_prediction.target
 
   // uras
   uras.io.specIn.startPc                := s1_startPc
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
index 54b072078aa..c05ecabc510 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
@@ -24,6 +24,7 @@ import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
 import xiangshan.frontend.bpu.HasFastTrainIO
 import xiangshan.frontend.bpu.Prediction
+import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 
 /**
  * This module is the implementation of the ahead BTB (Branch Target Buffer).
@@ -39,6 +40,7 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
     val abtbPos:       Vec[UInt]                  = Output(Vec(NumAheadBtbPredictionEntries, UInt(CfiPositionWidth.W)))
     val meta:          AheadBtbMeta               = Output(new AheadBtbMeta)
     val debug_startPc: PrunedAddr                 = Output(PrunedAddr(VAddrBits))
+    val normalPathHist: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
   }
   val io: AheadBtbIO = IO(new AheadBtbIO)
 
@@ -106,9 +108,11 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
 
   private val s0_previousStartPc = io.startPc
 
-  private val s0_setIdx   = getSetIndex(s0_previousStartPc)
-  private val s0_bankIdx  = getBankIndex(s0_previousStartPc)
-  private val s0_bankMask = UIntToOH(s0_bankIdx)
+  private val s0_simpleHash = io.normalPathHist.getHistWithInfo(AbtbHashFhInfo).foldedHist(AheadBtbHashBitWidth - 1, 0)
+  private val s0_hashIndex  = s0_previousStartPc(log2Ceil(NumEntries / NumWays) - 1, 0) ^ s0_simpleHash
+  private val s0_setIdx     = s0_hashIndex(log2Ceil(NumEntries / NumWays) - 1, log2Ceil(NumBanks))
+  private val s0_bankIdx    = s0_hashIndex(log2Ceil(NumBanks) - 1, 0)
+  private val s0_bankMask   = UIntToOH(s0_bankIdx)
 
   banks.zipWithIndex.foreach { case (b, i) =>
     b.io.readReq.valid       := predictReqValid && s0_bankMask(i)
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/Parameters.scala
index c8c38413086..f937316226d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/Parameters.scala
@@ -16,12 +16,14 @@
 package xiangshan.frontend.bpu.abtb
 
 import chisel3.util._
+import scala.math.min
+import xiangshan.frontend.bpu.FoldedHistoryInfo
 import xiangshan.frontend.bpu.HasBpuParameters
 
 case class AheadBtbParameters(
     NumEntries:           Int = 1024,
     NumBanks:             Int = 4,
-    NumWays:              Int = 8,
+    NumWays:              Int = 4,
     TagWidth:             Int = 24,
     TargetLowerBitsWidth: Int = 22,
     WriteBufferSize:      Int = 4,
@@ -47,4 +49,17 @@ trait HasAheadBtbParameters extends HasBpuParameters {
   def TakenCounterWidth:    Int = abtbParameters.TakenCounterWidth
 
   def EnableTargetFix: Boolean = abtbParameters.EnableTargetFix
+
+  // Bit width participating in the index hash, used to disperse hot branches and bias bank conflicts.
+  def AheadBtbHashBitWidth: Int = 4
+
+  // Reuse the folded PHR component already maintained for MicroTage table 0.
+  // ABTB only uses it for index dispersion.
+  def AbtbHashFhInfo: FoldedHistoryInfo = {
+    val tableInfo     = bpuParameters.utageParameters.TableInfos.head
+    val historyLength = tableInfo.HistoryLength
+    val foldedLength  = min(log2Ceil(tableInfo.NumSets), historyLength)
+    require(AheadBtbHashBitWidth <= foldedLength)
+    new FoldedHistoryInfo(historyLength, foldedLength)
+  }
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala b/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala
index 425760f9720..2970817435b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala
@@ -36,6 +36,7 @@ class Phr(implicit p: Parameters) extends PhrModule with HasPhrParameters with H
     val train:          PhrUpdate             = Input(new PhrUpdate)       // redirect from backend
     val s1Train:        S1Train               = Input(new S1Train)
     val commit:         Valid[BpuTrain]       = Input(Valid(new BpuTrain)) // trian bp data from reslove
+    val oldFoldedPhr:   PhrAllFoldedHistories = Output(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val trainFoldedPhr: PhrAllFoldedHistories = Output(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
   }
   val io: PhrIO = IO(new PhrIO)
@@ -282,6 +283,14 @@ class Phr(implicit p: Parameters) extends PhrModule with HasPhrParameters with H
     metaPhrFolded.getHistWithInfo(info).foldedHist :=
       computeFoldedHist(predictHist, info.FoldedLength)(info.HistoryLength)
   }
+  private val oldFoldedPhr = MuxCase(
+    s1_foldedPhrReg,
+    Seq(
+      redirectData.valid -> computeAllFoldedPhr(redirectPhr),
+      s3_override        -> s3_foldedPhrReg,
+      s1_valid           -> s1_foldedPhrReg
+    )
+  )
 
   io.phrMeta.phrPtr     := s1_phrPtr
   io.phrMeta.phrLowBits := s1_phrValue(PathHashHighWidth - 1, 0)
@@ -292,6 +301,7 @@ class Phr(implicit p: Parameters) extends PhrModule with HasPhrParameters with H
   io.s2_foldedPhr   := s2_foldedPhrReg
   io.s3_foldedPhr   := s3_foldedPhrReg
   io.trainFoldedPhr := metaPhrFolded
+  io.oldFoldedPhr   := oldFoldedPhr
 
   // TODO: Currently unavailable，waiting for ftq commit info
   // commit time phr checker
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/utage/Bundles.scala
index c6b3cc15ae0..6dd67c79f19 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/Bundles.scala
@@ -21,6 +21,7 @@ import org.chipsalliance.cde.config.Parameters
 import xiangshan.XSCoreParamsKey
 import xiangshan.frontend.bpu.SaturateCounter
 import xiangshan.frontend.bpu.SaturateCounterFactory
+import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 
 object TakenCounter extends SaturateCounterFactory {
   def width(implicit p: Parameters): Int =
@@ -42,6 +43,9 @@ class MicroTagePrediction(implicit p: Parameters) extends MicroTageBundle {
 class MicroTageMeta(implicit p: Parameters) extends MicroTageBundle {
   val readIndex:  Vec[UInt]       = Vec(NumTables, UInt(log2Ceil(MaxNumSets).W))
   val abtbResult: Vec[AbtbResult] = Vec(NumAheadBtbPredictionEntries, new AbtbResult)
+  // Separate backup for MicroTage history to help with timing.
+  // We'll evaluate redundancy after the timing issue is resolved.
+  val foldedPathHistForTrain: PhrAllFoldedHistories = new PhrAllFoldedHistories(AllFoldedHistoryInfo)
 }
 
 class MicroTageDebug(implicit p: Parameters) extends MicroTageBundle {
@@ -59,7 +63,6 @@ class AbtbResult(implicit p: Parameters) extends MicroTageBundle {
   val hit:              Bool            = Bool()
   val predTaken:        Bool            = Bool()
   val tableId:          UInt            = UInt(log2Ceil(NumTables).W)
-  val wayId:            UInt            = UInt(log2Ceil(NumWays).W)
   val cfiPosition:      UInt            = UInt(CfiPositionWidth.W)
   val takenCtr:         SaturateCounter = TakenCounter()
 }
@@ -83,7 +86,6 @@ class MicroTageTrainResult(implicit p: Parameters) extends MicroTageBundle {
   val baseIsStrongBias: Bool            = Bool()
   val cfiPosition:      UInt            = UInt(CfiPositionWidth.W)
   val tableId:          UInt            = UInt(log2Ceil(NumTables).W)
-  val wayId:            UInt            = UInt(log2Ceil(NumWays).W)
   val takenCtr:         SaturateCounter = TakenCounter()
 }
 
@@ -97,7 +99,6 @@ class TraceBranch(implicit p: Parameters) extends MicroTageBundle {
   val baseIsStrongBias: Bool = Bool()
   val cfiPosition:      UInt = UInt(CfiPositionWidth.W)
   val tableId:          UInt = UInt(log2Ceil(NumTables).W)
-  val wayId:            UInt = UInt(log2Ceil(NumWays).W)
 }
 
 class MicroTageTrace(implicit p: Parameters) extends MicroTageBundle {
@@ -115,6 +116,8 @@ class MicroTageEntry(implicit p: Parameters) extends MicroTageBundle {
   val tag:         UInt            = UInt(MaxTagLen.W)
   val cfiPosition: UInt            = UInt(CfiPositionWidth.W)
   val takenCtr:    SaturateCounter = TakenCounter()
+  // Placeholder, tied to 0.U, only for padding to even bit width.
+  val dummy: UInt = UInt(1.W)
 }
 
 class MicroTageUpdateInfo(implicit p: Parameters) extends MicroTageBundle {
@@ -126,9 +129,8 @@ class MicroTageUpdateInfo(implicit p: Parameters) extends MicroTageBundle {
   val needUseful:        Bool            = Bool()
 }
 
-class MicroTageAllocInfo(numWay: Int)(implicit p: Parameters) extends MicroTageBundle {
+class MicroTageAllocInfo(implicit p: Parameters) extends MicroTageBundle {
   val taken:       Bool = Bool()
-  val wayMask:     UInt = UInt(numWay.W)
   val cfiPosition: UInt = UInt(CfiPositionWidth.W)
   // val tag:         UInt = UInt(MaxTagLen.W)
 }
@@ -139,10 +141,10 @@ class MicroTageTrainRead(implicit p: Parameters) extends MicroTageBundle {
   val useful:         UInt = UInt(UsefulWidth.W)
 }
 
-class MicroTageTrain(numWay: Int, numSets: Int)(implicit p: Parameters) extends MicroTageBundle {
-  val t0_trainIndex: Valid[UInt]                     = Input(Valid(UInt(log2Ceil(numSets).W)))
-  val t0_read:       Vec[MicroTageTrainRead]         = Output(Vec(numWay, new MicroTageTrainRead))
-  val t1_tag:        UInt                            = Input(UInt(MaxTagLen.W))
-  val t1_update:     Vec[Valid[MicroTageUpdateInfo]] = Input(Vec(numWay, Valid(new MicroTageUpdateInfo)))
-  val t1_alloc:      Valid[MicroTageAllocInfo]       = Input(Valid(new MicroTageAllocInfo(numWay)))
+class MicroTageTrain(numSets: Int)(implicit p: Parameters) extends MicroTageBundle {
+  val t0_trainIndex: Valid[UInt]                = Input(Valid(UInt(log2Ceil(numSets).W)))
+  val t0_read:       MicroTageTrainRead         = Output(new MicroTageTrainRead)
+  val t1_tag:        UInt                       = Input(UInt(MaxTagLen.W))
+  val t1_update:     Valid[MicroTageUpdateInfo] = Input(Valid(new MicroTageUpdateInfo))
+  val t1_alloc:      Valid[MicroTageAllocInfo]  = Input(Valid(new MicroTageAllocInfo))
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/BypassShadowBuffer.scala b/src/main/scala/xiangshan/frontend/bpu/utage/BypassShadowBuffer.scala
index f372df7b27f..2b23c83ce59 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/BypassShadowBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/BypassShadowBuffer.scala
@@ -46,37 +46,35 @@ import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
  */
 class BypassShadowBuffer(
     val numSets:  Int,
-    val numWay:   Int,
     val tableId:  Int,
     val numBanks: Int = 4,
-    val numEntry: Int = 16
+    val numEntry: Int = 8
 )(implicit p: Parameters) extends MicroTageModule with HasCircularQueuePtrHelper with Helpers {
   class BypassBufferIO extends MicroTageBundle {
     class Req extends MicroTageBundle {
       val readIndex: UInt = UInt(log2Ceil(MaxNumSets).W)
     }
     class Resp extends MicroTageBundle {
-      val hit:         Vec[Bool]           = Vec(numWay, Bool())
-      val readEntries: Vec[MicroTageEntry] = Vec(numWay, new MicroTageEntry)
+      val hit:       Bool           = Bool()
+      val readEntry: MicroTageEntry = new MicroTageEntry
     }
     class WriteReq extends MicroTageBundle {
-      val writeIndex: UInt                = UInt(log2Ceil(MaxNumSets).W)
-      val writeData:  Vec[MicroTageEntry] = Vec(numWay, new MicroTageEntry)
-      val forceWrite: Bool                = Bool()
-      val wMask:      UInt                = UInt(numWay.W)
+      val writeIndex: UInt           = UInt(log2Ceil(MaxNumSets).W)
+      val writeData:  MicroTageEntry = new MicroTageEntry
+      val forceWrite: Bool           = Bool()
     }
     val req:          Req             = Input(new Req)
     val resp:         Resp            = Output(new Resp)
-    val train:        MicroTageTrain  = new MicroTageTrain(numWay, numSets)
+    val train:        MicroTageTrain  = new MicroTageTrain(numSets)
     val tryWrite:     Valid[WriteReq] = Output(Valid(new WriteReq))
     val writeSuccess: Bool            = Input(Bool())
     val usefulReset:  Bool            = Input(Bool())
   }
   val io = IO(new BypassBufferIO)
   class BufferEntry extends Bundle {
-    val valid:     Bool                       = Bool()
-    val entryData: Vec[Valid[MicroTageEntry]] = Vec(numWay, Valid(new MicroTageEntry))
-    val index:     UInt                       = UInt(log2Ceil(MaxNumSets).W)
+    val valid:     Bool                  = Bool()
+    val entryData: Valid[MicroTageEntry] = Valid(new MicroTageEntry)
+    val index:     UInt                  = UInt(log2Ceil(MaxNumSets).W)
   }
 
   class ReplaceItem extends Bundle {
@@ -94,7 +92,7 @@ class BypassShadowBuffer(
   // Banked useful registers
   private val usefulEntries = RegInit(VecInit.tabulate(numBanks) { bankIdx =>
     VecInit(Seq.fill(numSets / numBanks)(
-      VecInit(Seq.fill(numWay)(0.U.asTypeOf(new SaturateCounter(UsefulWidth))))
+      0.U.asTypeOf(new SaturateCounter(UsefulWidth))
     ))
   })
 
@@ -103,20 +101,20 @@ class BypassShadowBuffer(
   private val a0_entryHit  = Wire(Vec(numEntry, Bool()))
   a0_entryHit := entries.map(e => (e.index === io.req.readIndex) && e.valid)
 
-  private val a0_chosenFirst = (a0_entryHit.asUInt & priorityMask).orR
-  private val a1_chosenFirst = RegNext(a0_chosenFirst, false.B)
-  private val a1_firstHit    = RegInit(VecInit(Seq.fill(numEntry)(false.B)))
-  private val a1_entryHit    = RegInit(VecInit(Seq.fill(numEntry)(false.B)))
+  private val a0_chosenFirstMask = a0_entryHit.asUInt & priorityMask
+  private val a1_chosenFirstMask = RegNext(a0_chosenFirstMask, 0.U(numEntry.W))
+  private val a1_chosenFirst     = a1_chosenFirstMask.orR
+  private val a1_firstHit        = RegInit(VecInit(Seq.fill(numEntry)(false.B)))
+  private val a1_entryHit        = RegInit(VecInit(Seq.fill(numEntry)(false.B)))
   a1_firstHit := (a0_entryHit.asUInt & priorityMask).asBools
   a1_entryHit := a0_entryHit
-  private val a1_firstEntry        = ParallelPriorityMux(a1_firstHit.reverse, entryDataVec.reverse)
-  private val a1_secondEntry       = ParallelPriorityMux(a1_entryHit.reverse, entryDataVec.reverse)
-  private val a1_bufferEntry       = Mux(a1_chosenFirst, a1_firstEntry, a1_secondEntry)
-  private val a1_hasHit            = a1_entryHit.reduce(_ || _)
-  private val a1_microTageHitVec   = a1_bufferEntry.map(e => e.valid && a1_hasHit)
-  private val a1_microTageEntryVec = a1_bufferEntry.map(e => e.bits)
-  io.resp.hit         := a1_microTageHitVec
-  io.resp.readEntries := a1_microTageEntryVec
+  private val a1_firstEntry  = ParallelPriorityMux(a1_firstHit.reverse, entryDataVec.reverse)
+  private val a1_secondEntry = ParallelPriorityMux(a1_entryHit.reverse, entryDataVec.reverse)
+  private val a1_bufferEntry = Mux(a1_chosenFirst, a1_firstEntry, a1_secondEntry)
+  private val a1_hasHit      = a1_entryHit.reduce(_ || _)
+
+  io.resp.hit       := a1_bufferEntry.valid && a1_hasHit
+  io.resp.readEntry := a1_bufferEntry.bits
 
   // Training logic - stage 0
   private val t0_fire       = io.train.t0_trainIndex.valid
@@ -130,129 +128,117 @@ class BypassShadowBuffer(
   private val t0_firstEntry  = ParallelPriorityMux(t0_firstHit.reverse, entryDataVec.reverse)
   private val t0_secondEntry = ParallelPriorityMux(t0_entryHit.reverse, entryDataVec.reverse)
 
-  private val t0_hasHit            = t0_entryHit.reduce(_ || _)
-  private val t0_bufferEntry       = Mux(t0_chosenFirst, t0_firstEntry, t0_secondEntry)
-  private val t0_microTageHitVec   = VecInit(t0_bufferEntry.map(e => e.valid && t0_hasHit))
-  private val t0_microTageEntryVec = VecInit(t0_bufferEntry.map(e => e.bits))
+  private val t0_hasHit         = t0_entryHit.reduce(_ || _)
+  private val t0_bufferEntry    = Mux(t0_chosenFirst, t0_firstEntry, t0_secondEntry)
+  private val t0_microTageHit   = t0_bufferEntry.valid && t0_hasHit
+  private val t0_microTageEntry = t0_bufferEntry.bits
   // Access useful registers for t0 stage
   private val t0_bankIdx         = getBankId(t0_trainIndex, numBanks)
   private val t0_bankOffset      = getBankInnerIndex(t0_trainIndex, numBanks, numSets)
   private val t0_trainReadUseful = usefulEntries(t0_bankIdx)(t0_bankOffset)
   private val t0_cleanId =
     Mux(t0_chosenFirst, ~PriorityEncoder(t0_firstHit.reverse), ~PriorityEncoder(t0_entryHit.reverse))
-  private val t0_trainReadEntries = t0_microTageEntryVec
+  private val t0_trainReadEntry = t0_microTageEntry
 
-  for (way <- 0 until numWay) {
-    val entry  = t0_trainReadEntries(way)
-    val useful = t0_trainReadUseful(way)
-    io.train.t0_read(way).canGetPosition := t0_microTageHitVec(way)
-    io.train.t0_read(way).cfiPosition    := entry.cfiPosition
-    io.train.t0_read(way).useful         := useful.value
-  }
+  io.train.t0_read.canGetPosition := t0_microTageHit
+  io.train.t0_read.cfiPosition    := t0_trainReadEntry.cfiPosition
+  io.train.t0_read.useful         := t0_trainReadUseful.value
 
   // ==========================================================================
   // Bypass Logic: entries with the same readIndex are not allowed
   // ==========================================================================
   // Buffer write logic is pipelined across two cycles,
   // creating potential for writes to the same index in consecutive cycles.
-  private val needBypass        = WireDefault(false.B)
-  private val bypasscleanId     = WireDefault(0.U(log2Ceil(numEntry).W))
-  private val bypassHasHit      = Wire(Bool())
-  private val bypassHitVec      = Wire(Vec(numWay, Bool()))
-  private val bypassReadEntries = Wire(Vec(numWay, new MicroTageEntry))
+  private val needBypass      = WireDefault(false.B)
+  private val bypasscleanId   = WireDefault(0.U(log2Ceil(numEntry).W))
+  private val bypassHasHit    = Wire(Bool())
+  private val bypassHit       = Wire(Bool())
+  private val bypassReadEntry = Wire(new MicroTageEntry)
 
-  private val t1_fire             = RegNext(t0_fire, false.B)
-  private val t1_trainIndex       = RegNext(t0_trainIndex, 0.U(log2Ceil(MaxNumSets).W))
-  private val t1_hasHit           = RegNext(Mux(needBypass, bypassHasHit, t0_hasHit), false.B)
-  private val t1_microTageHitVec  = RegNext(Mux(needBypass, bypassHitVec, t0_microTageHitVec))
-  private val t1_trainReadEntries = RegNext(Mux(needBypass, bypassReadEntries, t0_trainReadEntries))
-  private val t1_cleanId          = RegNext(Mux(needBypass, bypasscleanId, t0_cleanId))
+  private val t1_fire           = RegNext(t0_fire, false.B)
+  private val t1_trainIndex     = RegNext(t0_trainIndex, 0.U(log2Ceil(MaxNumSets).W))
+  private val t1_hasHit         = RegNext(Mux(needBypass, bypassHasHit, t0_hasHit), false.B)
+  private val t1_microTageHit   = RegNext(Mux(needBypass, bypassHit, t0_microTageHit))
+  private val t1_trainReadEntry = RegNext(Mux(needBypass, bypassReadEntry, t0_trainReadEntry))
+  private val t1_cleanId        = RegNext(Mux(needBypass, bypasscleanId, t0_cleanId))
   // Access useful registers for t1 stage
   private val t1_bankIdx         = getBankId(t1_trainIndex, numBanks)
   private val t1_bankOffset      = getBankInnerIndex(t1_trainIndex, numBanks, numSets)
   private val t1_trainReadUseful = usefulEntries(t1_bankIdx)(t1_bankOffset)
 
-  private val writeBufferValid     = WireDefault(VecInit(Seq.fill(numWay)(false.B)))
-  private val newMicroTageEntryVec = WireDefault(VecInit(Seq.fill(numWay)(0.U.asTypeOf(new MicroTageEntry))))
-  for (way <- 0 until numWay) {
-    val oldEntry       = t1_trainReadEntries(way)
-    val oldTakenCtr    = oldEntry.takenCtr
-    val updateTakenCtr = io.train.t1_update(way).bits.updateTakenCtr
+  private val writeBufferValid  = WireDefault(false.B)
+  private val newMicroTageEntry = WireDefault(0.U.asTypeOf(new MicroTageEntry))
 
-    // Update logic: either allocation or update
-    val doAlloc  = io.train.t1_alloc.valid && io.train.t1_alloc.bits.wayMask(way)
-    val doUpdate = io.train.t1_update(way).valid && io.train.t1_update(way).bits.updateValid
-    writeBufferValid(way) := doAlloc || doUpdate
+  // -------------------- Calculate the new entry to be updated -----------------------
+  private val oldEntry       = t1_trainReadEntry
+  private val oldTakenCtr    = oldEntry.takenCtr
+  private val updateTakenCtr = io.train.t1_update.bits.updateTakenCtr
 
-    // New entry values
-    newMicroTageEntryVec(way).valid := true.B
-    newMicroTageEntryVec(way).tag   := io.train.t1_tag
-    newMicroTageEntryVec(way).cfiPosition :=
-      Mux(doAlloc, io.train.t1_alloc.bits.cfiPosition, io.train.t1_update(way).bits.updateCfiPosition)
-    newMicroTageEntryVec(way).takenCtr := Mux(
-      doAlloc,
-      Mux(io.train.t1_alloc.bits.taken, TakenCounter.WeakPositive, TakenCounter.WeakNegative),
-      // updateTakenCtr.getUpdate(io.train.t1_update(way).bits.updateTaken)
-      Mux(
-        t1_microTageHitVec(way) && (oldEntry.tag === io.train.t1_tag),
-        oldTakenCtr.getUpdate(io.train.t1_update(way).bits.updateTaken),
-        updateTakenCtr.getUpdate(io.train.t1_update(way).bits.updateTaken)
-      )
-    )
-  }
+  // Update logic: either allocation or update
+  private val doAlloc  = io.train.t1_alloc.valid
+  private val doUpdate = io.train.t1_update.valid
+  writeBufferValid := doAlloc || doUpdate
 
-  for (way <- 0 until numWay) {
-    val doAlloc   = io.train.t1_alloc.valid && io.train.t1_alloc.bits.wayMask(way)
-    val oldUseful = t1_trainReadUseful(way)
-    val newUseful = Mux(
-      doAlloc,
-      // UsefulCounter.WeakPositive,
-      if (tableId < NumTables / 2) UsefulCounter.WeakNegative else UsefulCounter.WeakPositive,
-      oldUseful.getUpdate(io.train.t1_update(way).bits.needUseful)
+  // New entry values
+  newMicroTageEntry.valid := true.B
+  newMicroTageEntry.tag   := io.train.t1_tag
+  newMicroTageEntry.cfiPosition :=
+    Mux(doAlloc, io.train.t1_alloc.bits.cfiPosition, io.train.t1_update.bits.updateCfiPosition)
+  newMicroTageEntry.takenCtr := Mux(
+    doAlloc,
+    Mux(io.train.t1_alloc.bits.taken, TakenCounter.WeakPositive, TakenCounter.WeakNegative),
+    Mux(
+      t1_microTageHit && (oldEntry.tag === io.train.t1_tag),
+      oldTakenCtr.getUpdate(io.train.t1_update.bits.updateTaken),
+      updateTakenCtr.getUpdate(io.train.t1_update.bits.updateTaken)
     )
-    when(doAlloc || (io.train.t1_update(way).valid && io.train.t1_update(way).bits.usefulValid)) {
-      t1_trainReadUseful(way) := newUseful
-    }
+  )
+
+  // ------------------------ Calculate the new useful entry -----------------------------
+  private val oldUseful = t1_trainReadUseful
+  private val newUseful = Mux(
+    doAlloc,
+    // UsefulCounter.WeakPositive,
+    if (tableId < NumTables / 2) UsefulCounter.WeakNegative else UsefulCounter.WeakPositive,
+    oldUseful.getUpdate(io.train.t1_update.bits.needUseful)
+  )
+  when(doAlloc || (io.train.t1_update.valid && io.train.t1_update.bits.usefulValid)) {
+    t1_trainReadUseful := newUseful
   }
 
   // Useful counter reset logic
   when(io.usefulReset) {
     for (bankIdx <- 0 until numBanks) {
       for (setIdx <- 0 until numSets / numBanks) {
-        for (wayIdx <- 0 until numWay) {
-          val entry = usefulEntries(bankIdx)(setIdx)(wayIdx)
-          if (tableId < NumTables / 2) {
-            usefulEntries(bankIdx)(setIdx)(wayIdx).value :=
-              Mux(entry.value === 0.U, 0.U, entry.value - 1.U)
-          } else {
-            usefulEntries(bankIdx)(setIdx)(wayIdx).value := entry.value >> 1.U
-          }
-          // usefulEntries(bankIdx)(setIdx)(wayIdx).value := entry.value >> 1.U
+        val entry = usefulEntries(bankIdx)(setIdx)
+        if (tableId < NumTables / 2) {
+          usefulEntries(bankIdx)(setIdx).value :=
+            Mux(entry.value === 0.U, 0.U, entry.value - 1.U)
+        } else {
+          usefulEntries(bankIdx)(setIdx).value := entry.value >> 1.U
         }
       }
     }
   }
 
   private val newBufferEntry = Wire(new BufferEntry)
-  newBufferEntry.valid := true.B
-  newBufferEntry.index := t1_trainIndex
-  for (i <- 0 until numWay) {
-    newBufferEntry.entryData(i).valid := writeBufferValid(i)
-    newBufferEntry.entryData(i).bits  := newMicroTageEntryVec(i)
-  }
+  newBufferEntry.valid           := true.B
+  newBufferEntry.index           := t1_trainIndex
+  newBufferEntry.entryData.valid := writeBufferValid
+  newBufferEntry.entryData.bits  := newMicroTageEntry
 
-  private val t1_hasWrite = writeBufferValid.reduce(_ || _)
+  private val t1_hasWrite = writeBufferValid
   when(t1_hasWrite) {
     entries(enqPtr.value) := newBufferEntry
     enqPtr                := enqPtr + 1.U
     priorityMask          := Fill(numEntry, 1.U(1.W)) >> ~enqPtr.value
   }
 
-  needBypass        := (t1_trainIndex === t0_trainIndex) && t1_hasWrite
-  bypassHasHit      := true.B
-  bypasscleanId     := enqPtr.value
-  bypassHitVec      := newBufferEntry.entryData.map(e => e.valid)
-  bypassReadEntries := newBufferEntry.entryData.map(e => e.bits)
+  needBypass      := (t1_trainIndex === t0_trainIndex) && t1_hasWrite
+  bypassHasHit    := true.B
+  bypasscleanId   := enqPtr.value
+  bypassHit       := newBufferEntry.entryData.valid
+  bypassReadEntry := newBufferEntry.entryData.bits
 
   private val isEmpty = deqPtr === enqPtr
   when(io.writeSuccess || (!statusEntries(deqPtr.value).dirty && !isEmpty)) {
@@ -278,9 +264,8 @@ class BypassShadowBuffer(
   forceWrite                  := distanceBetween(enqPtr, deqPtr) > (numEntry - 2).U
   io.tryWrite.valid           := statusEntries(deqPtr.value).valid && statusEntries(deqPtr.value).dirty
   io.tryWrite.bits.writeIndex := entries(deqPtr.value).index
-  io.tryWrite.bits.writeData  := entries(deqPtr.value).entryData.map(_.bits)
+  io.tryWrite.bits.writeData  := entries(deqPtr.value).entryData.bits
   io.tryWrite.bits.forceWrite := forceWrite && statusEntries(deqPtr.value).valid && statusEntries(deqPtr.value).dirty
-  io.tryWrite.bits.wMask      := VecInit(entries(deqPtr.value).entryData.map(_.valid)).asUInt
 
   // ==========================================================================
   // Buffer Performance Diagnostic Counters
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
index 2fc7c34ef25..782dd19d508 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
@@ -20,11 +20,13 @@ import freechips.rocketchip.util.SeqToAugmentedSeq
 import org.chipsalliance.cde.config.Parameters
 import scala.math.min
 import utility.ChiselDB
+import utility.ParallelPriorityMux
 import utility.XSPerfAccumulate
 import utility.XSPerfSeqAccumulate
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
+import xiangshan.frontend.bpu.BpuFastTrain
 import xiangshan.frontend.bpu.BpuTrain
 import xiangshan.frontend.bpu.CompareMatrix
 import xiangshan.frontend.bpu.FoldedHistoryInfo
@@ -39,16 +41,21 @@ import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
  */
 class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageParameters with Helpers {
   class MicroTageIO(implicit p: Parameters) extends BasePredictorIO with HasFastTrainIO {
-    val foldedPathHist:         PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
-    val foldedPathHistForTrain: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
-    val prediction:             MicroTagePrediction   = Output(new MicroTagePrediction)
-    val meta:                   Valid[MicroTageMeta]  = Output(Valid(new MicroTageMeta))
+    val prediction: MicroTagePrediction  = Output(new MicroTagePrediction)
+    val meta:       Valid[MicroTageMeta] = Output(Valid(new MicroTageMeta))
     // Send ABTB position early, pipeline registers inside the module.
-    // Consideration: improve routing and enhance driving capability.
+    // Consideration: improve routing and enhance driving capablility.
     val abtbPosVec:     Vec[UInt]                  = Input(Vec(NumAheadBtbPredictionEntries, UInt(CfiPositionWidth.W)))
     val abtbPrediction: Vec[Valid[AheadBtbResult]] = Input(Vec(NumAheadBtbPredictionEntries, Valid(new AheadBtbResult)))
     val overrideValid:  Bool                       = Input(Bool())
     val redirectValid:  Bool                       = Input(Bool())
+
+    val normalPathHist:   PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
+    val s1PathHist:       PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
+    val overridePathHist: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
+
+    val s1StartPc:       PrunedAddr = Input(new PrunedAddr(VAddrBits))
+    val overrideStartPc: PrunedAddr = Input(new PrunedAddr(VAddrBits))
   }
   val io: MicroTageIO = IO(new MicroTageIO)
   io.trainReady := true.B
@@ -56,22 +63,19 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   // Ahead pipeline implementation. Advantage: get data one cycle earlier.
   // Disadvantage: multi-position competition for the same entry.
   // Problem scenario: the same entry accessed by different branches in different cycles.
-  private val a0_fire           = io.enable && io.stageCtrl.s0_fire
-  private val a1_fire           = a0_fire
-  private val a2_fire           = io.stageCtrl.s1_fire
-  private val a0_startPc        = io.startPc
-  private val a0_foldedPathHist = io.foldedPathHist
-  private val a1_startPc        = io.startPc
-  private val a1_foldedPathHist = io.foldedPathHist
-
-  private val overrideValid = io.overrideValid
+  private val a0_fire                = io.enable && io.stageCtrl.s0_fire
+  private val a1_fire                = a0_fire
+  private val a2_fire                = io.stageCtrl.s1_fire
+  private val overrideValid          = io.overrideValid
+  private val redirectValid          = io.redirectValid
+  private val a0_indexPc             = io.startPc
+  private val a0_indexFoldedPathHist = io.normalPathHist
 
   /* *** submodules *** */
   private val tables = TableInfos.zipWithIndex.map {
     case (info, i) =>
       val t = Module(new MicroTageTable(
         numSets = info.NumSets,
-        numWay = NumWays,
         tableId = i
       )).io
       t
@@ -82,7 +86,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   private val lowTickCounter  = RegInit(0.U((LowTickWidth + 1).W))
   private val highTickCounter = RegInit(0.U((HighTickWidth + 1).W))
   private val a0_readIndex = VecInit.tabulate(NumTables) {
-    i => computeHashIdx(a0_startPc, a0_foldedPathHist, TableInfos, i)
+    i => computeHashIdx(a0_indexPc, a0_indexFoldedPathHist, TableInfos, i)
   }
   // Predict
   tables.zipWithIndex.foreach {
@@ -97,50 +101,80 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
         case _ => t.usefulReset := false.B
       }
   }
-  private val a1_predEntries = tables.map(_.resps.readEntries)
+  private val a1_predEntries = tables.map(_.resps.readEntry)
   private val a1_readIndex   = RegEnable(a0_readIndex, a0_fire)
-  private val a1_predRead    = Wire(Vec(NumTables, Vec(NumWays, new MicroTageTablePred)))
+  private val a1_predRead    = Wire(Vec(NumTables, new MicroTageTablePred))
   for (i <- 0 until NumTables) {
-    val predTag = computeHashTag(a1_startPc, a1_foldedPathHist, TableInfos, i)
-    for (j <- 0 until NumWays) {
-      a1_predRead(i)(j).taken := a1_predEntries(i)(j).takenCtr.isPositive
-      a1_predRead(i)(j).valid := a1_predEntries(i)(j).valid
-      a1_predRead(i)(j).tag   := a1_predEntries(i)(j).tag
-      // Timing might be tight, consider using older PHR.
-      a1_predRead(i)(j).tagHit      := a1_predEntries(i)(j).tag === predTag
-      a1_predRead(i)(j).cfiPosition := a1_predEntries(i)(j).cfiPosition
-      a1_predRead(i)(j).posHit      := false.B
-      a1_predRead(i)(j).takenCtr    := a1_predEntries(i)(j).takenCtr
-    }
+    val predTag = computeHashTag(io.s1StartPc, io.s1PathHist, TableInfos, i)
+    a1_predRead(i).taken := a1_predEntries(i).takenCtr.isPositive
+    a1_predRead(i).valid := a1_predEntries(i).valid
+    a1_predRead(i).tag   := a1_predEntries(i).tag
+    // Timing might be tight, consider using older PHR.
+    a1_predRead(i).tagHit      := a1_predEntries(i).tag === predTag
+    a1_predRead(i).cfiPosition := a1_predEntries(i).cfiPosition
+    a1_predRead(i).posHit      := false.B
+    a1_predRead(i).takenCtr    := a1_predEntries(i).takenCtr
   }
 
   // Prioritize early position comparison at the cost of ABTB SRAM timing margin,
   // ensuring glitch-free valid signals for the next stage.
-  private val a1_posHitVec = Wire(Vec(NumAheadBtbPredictionEntries, Vec(NumTables, Vec(NumWays, Bool()))))
+  private val a1_posHitVec = Wire(Vec(NumAheadBtbPredictionEntries, Vec(NumTables, Bool())))
   for (i <- 0 until NumAheadBtbPredictionEntries) {
     for (j <- 0 until NumTables) {
-      for (k <- 0 until NumWays) {
-        a1_posHitVec(i)(j)(k) :=
-          a1_predEntries(j)(k).valid && (a1_predEntries(j)(k).cfiPosition === io.abtbPosVec(i))
-      }
+      a1_posHitVec(i)(j) := a1_predEntries(j).valid && (a1_predEntries(j).cfiPosition === io.abtbPosVec(i))
+    }
+  }
+  // Get finally selected Table ID for each branch instruction of abtb.
+  // (Pre-calculate timing-critical signals for BPU S1.
+  // These include the Hit and Taken signals required for MicroTag and aBTB coordination.)
+  private val a1_abtbTableIDVec = Wire(Vec(NumAheadBtbPredictionEntries, UInt(log2Ceil(NumTables).W)))
+  private val a1_abtbTakenVec   = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
+  private val a1_abtbHitVec     = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
+  private val tabeIDVec         = VecInit.tabulate(NumTables)(i => i.U)
+  for (i <- 0 until NumAheadBtbPredictionEntries) {
+    // tmp prefix highlights the temporary scope within the loop.
+    val tmpTableHitVec   = Wire(Vec(NumTables, Bool()))
+    val tmpTableTakenVec = Wire(Vec(NumTables, Bool()))
+    for (j <- 0 until NumTables) {
+      tmpTableHitVec(j)   := a1_predRead(j).tagHit && a1_posHitVec(i)(j)
+      tmpTableTakenVec(j) := a1_predRead(j).taken
     }
+    a1_abtbHitVec(i)     := tmpTableHitVec.reduce(_ || _)
+    a1_abtbTakenVec(i)   := ParallelPriorityMux(tmpTableHitVec.reverse, tmpTableTakenVec.reverse)
+    a1_abtbTableIDVec(i) := ParallelPriorityMux(tmpTableHitVec.reverse, tabeIDVec.reverse)
   }
 
-  private val a3_readIndex     = RegInit(0.U.asTypeOf(a1_readIndex))
-  private val a3_predRead      = RegInit(0.U.asTypeOf(a1_predRead))
-  private val a3_posHitVec     = RegInit(0.U.asTypeOf(a1_posHitVec))
-  private val overridePredRead = Wire(Vec(NumTables, Vec(NumWays, new MicroTageTablePred)))
+  private val a3_readIndex = RegInit(0.U.asTypeOf(a1_readIndex))
+  private val a3_predRead  = RegInit(0.U.asTypeOf(a1_predRead))
+  private val a3_posHitVec = RegInit(0.U.asTypeOf(a1_posHitVec))
+
+  // ------------------------------  ----------------------------------------- //
+  private val overridePredRead = Wire(Vec(NumTables, new MicroTageTablePred))
   for (i <- 0 until NumTables) {
-    val predTag = computeHashTag(a1_startPc, a1_foldedPathHist, TableInfos, i)
-    for (j <- 0 until NumWays) {
-      overridePredRead(i)(j).taken       := a3_predRead(i)(j).taken
-      overridePredRead(i)(j).valid       := a3_predRead(i)(j).valid
-      overridePredRead(i)(j).tag         := a3_predRead(i)(j).tag
-      overridePredRead(i)(j).tagHit      := a3_predRead(i)(j).tag === predTag
-      overridePredRead(i)(j).cfiPosition := a3_predRead(i)(j).cfiPosition
-      overridePredRead(i)(j).posHit      := false.B
-      overridePredRead(i)(j).takenCtr    := a3_predRead(i)(j).takenCtr
+    val predTag = computeHashTag(io.overrideStartPc, io.overridePathHist, TableInfos, i)
+    overridePredRead(i).taken       := a3_predRead(i).taken
+    overridePredRead(i).valid       := a3_predRead(i).valid
+    overridePredRead(i).tag         := a3_predRead(i).tag
+    overridePredRead(i).tagHit      := a3_predRead(i).tag === predTag
+    overridePredRead(i).cfiPosition := a3_predRead(i).cfiPosition
+    overridePredRead(i).posHit      := false.B
+    overridePredRead(i).takenCtr    := a3_predRead(i).takenCtr
+  }
+
+  private val a3_abtbTableIDVec = Wire(Vec(NumAheadBtbPredictionEntries, UInt(log2Ceil(NumTables).W)))
+  private val a3_abtbTakenVec   = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
+  private val a3_abtbHitVec     = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
+  for (i <- 0 until NumAheadBtbPredictionEntries) {
+    // tmp prefix highlights the temporary scope within the loop.
+    val tmpTableHitVec   = Wire(Vec(NumTables, Bool()))
+    val tmpTableTakenVec = Wire(Vec(NumTables, Bool()))
+    for (j <- 0 until NumTables) {
+      tmpTableHitVec(j)   := overridePredRead(j).tagHit && a3_posHitVec(i)(j)
+      tmpTableTakenVec(j) := overridePredRead(j).taken
     }
+    a3_abtbHitVec(i)     := tmpTableHitVec.reduce(_ || _)
+    a3_abtbTakenVec(i)   := ParallelPriorityMux(tmpTableHitVec.reverse, tmpTableTakenVec.reverse)
+    a3_abtbTableIDVec(i) := ParallelPriorityMux(tmpTableHitVec.reverse, tabeIDVec.reverse)
   }
 
   private val a2_readIndex = RegEnable(Mux(overrideValid, a3_readIndex, a1_readIndex), a1_fire)
@@ -148,39 +182,30 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
     RegEnable(Mux(overrideValid, overridePredRead, a1_predRead), 0.U.asTypeOf(a1_predRead), a1_fire)
   private val a2_posHitVec =
     RegEnable(Mux(overrideValid, a3_posHitVec, a1_posHitVec), 0.U.asTypeOf(a1_posHitVec), a1_fire)
-  private val a2_fromAbtbPos     = RegEnable(io.abtbPosVec, a1_fire)
-  private val a2_abtbHitVec      = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
-  private val a2_abtbTakenVec    = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
-  private val a2_abtbUseTableId  = Wire(Vec(NumAheadBtbPredictionEntries, UInt(log2Ceil(NumTables).W)))
-  private val a2_abtbUseWayId    = Wire(Vec(NumAheadBtbPredictionEntries, UInt(log2Ceil(NumWays).W)))
-  private val a2_tableIdVec      = VecInit.tabulate(NumTables)(i => i.U)
+  private val a2_foldedPathHist =
+    RegEnable(Mux(overrideValid, io.overridePathHist, io.s1PathHist), a1_fire)
+  private val a2_fromAbtbPos = RegEnable(io.abtbPosVec, a1_fire)
+  private val a2_abtbUseTableIDVec =
+    RegEnable(Mux(overrideValid, a3_abtbTableIDVec, a1_abtbTableIDVec), 0.U.asTypeOf(a1_abtbTableIDVec), a1_fire)
+  private val a2_abtbTakenVec =
+    RegEnable(Mux(overrideValid, a3_abtbTakenVec, a1_abtbTakenVec), 0.U.asTypeOf(a1_abtbTakenVec), a1_fire)
   private val a2_abtbTakenCtrVec = Wire(Vec(NumAheadBtbPredictionEntries, TakenCounter()))
 
+  private val a2_abtbHitVec = RegInit(VecInit.fill(NumAheadBtbPredictionEntries)(false.B))
+  when(redirectValid) {
+    a2_abtbHitVec := 0.U.asTypeOf(Vec(NumAheadBtbPredictionEntries, Bool()))
+  }.elsewhen(overrideValid) {
+    a2_abtbHitVec := a3_abtbHitVec
+  }.elsewhen(a1_fire) {
+    a2_abtbHitVec := a1_abtbHitVec
+  }
+
   for (i <- 0 until NumAheadBtbPredictionEntries) {
-    val tableHitVec         = Wire(Vec(NumTables, Bool()))
-    val tableCfiPositionVec = Wire(Vec(NumTables, UInt(CfiPositionWidth.W)))
-    val tableTakenVec       = Wire(Vec(NumTables, Bool()))
-    val tableTakenCtrVec    = Wire(Vec(NumTables, TakenCounter()))
-    val tableWayIdVec       = Wire(Vec(NumTables, UInt(log2Ceil(NumWays).W)))
+    val tableTakenCtrVec = Wire(Vec(NumTables, TakenCounter()))
     for (j <- 0 until NumTables) {
-      val wayHitVec = Wire(Vec(NumWays, Bool()))
-      for (k <- 0 until NumWays) {
-        wayHitVec(k) := a2_predRead(j)(k).tagHit && a2_posHitVec(i)(j)(k)
-      }
-      tableHitVec(j) := wayHitVec.asUInt.orR
-      val priorityWayHitVec = PriorityEncoderOH(wayHitVec)
-      tableCfiPositionVec(j) := Mux1H(priorityWayHitVec, a2_predRead(j).map(_.cfiPosition))
-      tableTakenVec(j)       := Mux1H(priorityWayHitVec, a2_predRead(j).map(_.taken))
-      tableTakenCtrVec(j)    := Mux1H(priorityWayHitVec, a2_predRead(j).map(_.takenCtr))
-      tableWayIdVec(j)       := PriorityEncoder(wayHitVec)
+      tableTakenCtrVec(j) := a2_predRead(j).takenCtr
     }
-    a2_abtbHitVec(i) := tableHitVec.asUInt.orR
-    // Find the hit result from the highest-priority table
-    val priorityTableHitVec = PriorityEncoderOH(tableHitVec.reverse)
-    a2_abtbTakenVec(i)    := Mux1H(priorityTableHitVec, tableTakenVec.reverse)
-    a2_abtbTakenCtrVec(i) := Mux1H(priorityTableHitVec, tableTakenCtrVec.reverse)
-    a2_abtbUseTableId(i)  := Mux1H(priorityTableHitVec, a2_tableIdVec.reverse)
-    a2_abtbUseWayId(i)    := Mux1H(priorityTableHitVec, tableWayIdVec.reverse)
+    a2_abtbTakenCtrVec(i) := tableTakenCtrVec(a2_abtbUseTableIDVec(i))
   }
 
   private val s1_predMeta = Wire(Valid(new MicroTageMeta))
@@ -189,15 +214,17 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
     NumAheadBtbPredictionEntries,
     new AbtbResult
   )) // no use, only for placeholder.
-  s1_predMeta.bits.readIndex := a2_readIndex
+  s1_predMeta.bits.readIndex              := a2_readIndex
+  s1_predMeta.bits.foldedPathHistForTrain := a2_foldedPathHist
   for (i <- 0 until NumAheadBtbPredictionEntries) {
+    // On the cycle following a redirect, MicroTage provides no prediction;
+    // therefore, this cycle is excluded from training.
     s1_predMeta.bits.abtbResult(i).valid :=
-      io.abtbPrediction(i).valid && io.abtbPrediction(i).bits.attribute.isConditional
+      io.abtbPrediction(i).valid && io.abtbPrediction(i).bits.attribute.isConditional && RegNext(!redirectValid)
     s1_predMeta.bits.abtbResult(i).baseTaken        := io.abtbPrediction(i).bits.taken
     s1_predMeta.bits.abtbResult(i).hit              := a2_abtbHitVec(i) && io.abtbPrediction(i).valid
     s1_predMeta.bits.abtbResult(i).predTaken        := a2_abtbTakenVec(i)
-    s1_predMeta.bits.abtbResult(i).tableId          := a2_abtbUseTableId(i)
-    s1_predMeta.bits.abtbResult(i).wayId            := a2_abtbUseWayId(i)
+    s1_predMeta.bits.abtbResult(i).tableId          := a2_abtbUseTableIDVec(i)
     s1_predMeta.bits.abtbResult(i).cfiPosition      := a2_fromAbtbPos(i) // io.abtbPrediction(i).bits.cfiPosition
     s1_predMeta.bits.abtbResult(i).baseIsStrongBias := io.abtbPrediction(i).bits.isStrongBias
     s1_predMeta.bits.abtbResult(i).takenCtr         := a2_abtbTakenCtrVec(i)
@@ -216,13 +243,14 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   }
 
   // ------------ MicroTage is only concerned with conditional branches ---------- //
-  private val t0_fire                = io.stageCtrl.t0_fire && io.enable
-  private val t0_trainMeta           = io.train.meta.utage
-  private val t0_trainData           = io.train
-  private val t0_trainBranch         = io.train.branches
-  private val t0_abtbResult          = t0_trainMeta.abtbResult
-  private val t0_trainRead           = VecInit(tables.map(_.train.t0_read))
-  private val t0_trainBranchTakenVec = t0_trainBranch.map(x => x.bits.taken)
+  private val t0_train                  = RegNext(io.fastTrain.get.bits, 0.U.asTypeOf(new BpuFastTrain))
+  private val t0_fire                   = RegNext(io.fastTrain.get.valid, false.B)
+  private val t0_trainMeta              = t0_train.utageMeta
+  private val t0_abtbResult             = t0_trainMeta.abtbResult
+  private val t0_trainRead              = VecInit(tables.map(_.train.t0_read))
+  private val t0_foldedPathHistForTrain = t0_trainMeta.foldedPathHistForTrain
+  private val t0_trainStartPc           = t0_train.startPc
+  private val finalPrediction           = t0_train.finalPrediction
 
   private val t0_hasHitMisPredVec  = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
   private val t0_missHitMisPredVec = Wire(Vec(NumAheadBtbPredictionEntries, Bool()))
@@ -232,28 +260,29 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   // Rationale: MicroTage is a correction to ABTB results. ABTB is MicroTage's base table.
   // Important constraint: Do not predict branches not provided by ABTB to avoid over-generalization.
   for (i <- 0 until NumAheadBtbPredictionEntries) {
-    val hitMisPredVec = t0_trainBranch.map(x =>
-      x.valid && t0_abtbResult(i).valid && t0_abtbResult(i).hit &&
-        (x.bits.cfiPosition === t0_abtbResult(i).cfiPosition) && (x.bits.taken =/= t0_abtbResult(i).predTaken)
-    )
-    t0_hasHitMisPredVec(i) := hitMisPredVec.reduce(_ || _)
-    val missHitMisPredVec = t0_trainBranch.map(x =>
-      x.valid && t0_abtbResult(i).valid && !t0_abtbResult(i).hit &&
-        (x.bits.cfiPosition === t0_abtbResult(i).cfiPosition) && (x.bits.taken =/= t0_abtbResult(i).baseTaken)
+    t0_hasHitMisPredVec(i) := t0_abtbResult(i).valid && t0_abtbResult(i).hit && (
+      (finalPrediction.attribute.isConditional && (finalPrediction.cfiPosition === t0_abtbResult(i).cfiPosition) &&
+        (t0_abtbResult(i).predTaken =/= finalPrediction.taken)) ||
+        ((finalPrediction.cfiPosition > t0_abtbResult(i).cfiPosition) && t0_abtbResult(i).predTaken)
     )
-    t0_missHitMisPredVec(i) := missHitMisPredVec.reduce(_ || _)
-    val trainHasAbtbBranch = t0_trainBranch.map(x =>
-      x.valid && t0_abtbResult(i).valid && (x.bits.cfiPosition === t0_abtbResult(i).cfiPosition)
+
+    t0_missHitMisPredVec(i) := t0_abtbResult(i).valid && !t0_abtbResult(i).hit && (
+      (finalPrediction.attribute.isConditional && (finalPrediction.cfiPosition === t0_abtbResult(i).cfiPosition) &&
+        (t0_abtbResult(i).baseTaken =/= finalPrediction.taken)) ||
+        ((finalPrediction.cfiPosition > t0_abtbResult(i).cfiPosition) && t0_abtbResult(i).baseTaken)
     )
-    t0_trainResult(i).valid            := trainHasAbtbBranch.reduce(_ || _)
-    t0_trainResult(i).hit              := t0_abtbResult(i).hit
-    t0_trainResult(i).baseTaken        := t0_abtbResult(i).baseTaken
-    t0_trainResult(i).actualTaken      := Mux1H(trainHasAbtbBranch, t0_trainBranchTakenVec)
+    val trainHasAbtbBranch = t0_abtbResult(i).valid && (finalPrediction.cfiPosition >= t0_abtbResult(i).cfiPosition)
+
+    t0_trainResult(i).valid     := trainHasAbtbBranch
+    t0_trainResult(i).hit       := t0_abtbResult(i).hit
+    t0_trainResult(i).baseTaken := t0_abtbResult(i).baseTaken
+    t0_trainResult(i).actualTaken := (finalPrediction.cfiPosition === t0_abtbResult(
+      i
+    ).cfiPosition) && finalPrediction.taken
     t0_trainResult(i).predTaken        := t0_abtbResult(i).predTaken
     t0_trainResult(i).baseIsStrongBias := t0_abtbResult(i).baseIsStrongBias
     t0_trainResult(i).cfiPosition      := t0_abtbResult(i).cfiPosition
     t0_trainResult(i).tableId          := t0_abtbResult(i).tableId
-    t0_trainResult(i).wayId            := t0_abtbResult(i).wayId
     t0_trainResult(i).takenCtr         := t0_abtbResult(i).takenCtr
   }
   private val t0_trainMisPredVec = VecInit(t0_hasHitMisPredVec.zip(t0_missHitMisPredVec).map {
@@ -276,7 +305,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   private val t0_trainIdx = t0_trainMeta.readIndex
 
   private val t1_fire                   = RegNext(t0_fire, false.B)
-  private val t1_foldedPathHistForTrain = RegEnable(io.foldedPathHistForTrain, t0_fire)
+  private val t1_foldedPathHistForTrain = RegEnable(t0_foldedPathHistForTrain, t0_fire)
   private val t1_trainRead              = RegEnable(t0_trainRead, t0_fire)
   private val t1_trainResult            = RegEnable(t0_trainResult, t0_fire)
   private val t1_misPredProviderOH      = RegEnable(t0_misPredProviderOH, t0_fire)
@@ -286,62 +315,57 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   // Select entries eligible for allocation
   private val t1_keepUseMask = Wire(Vec(NumTables, Bool()))
   for (i <- 0 until NumTables) {
-    t1_keepUseMask(i) := t1_trainRead(i).map(x => x.useful =/= 0.U).reduce(_ && _)
+    t1_keepUseMask(i) := t1_trainRead(i).useful =/= 0.U
   }
   private val t1_lowerFillMask =
     Mux(t1_misPredProviderOH === 0.U, 0.U, t1_misPredProviderOH | (t1_misPredProviderOH - 1.U))
   private val t1_allocCandidateMask = ~(t1_lowerFillMask | t1_keepUseMask.asUInt)
   private val t1_normalAllocMask    = PriorityEncoderOH(t1_allocCandidateMask)
-  private val t1_trainStartPc       = RegEnable(t0_trainData.startPc, t0_fire)
+  private val t1_trainStartPc       = RegEnable(t0_trainStartPc, t0_fire)
 
   for (i <- 0 until NumTables) {
     tables(i).train.t0_trainIndex.valid := t0_fire
     tables(i).train.t0_trainIndex.bits  := t0_trainIdx(i)
-    val t1_trainTag = computeHashTag(t1_trainStartPc, t1_foldedPathHistForTrain, TableInfos, i)
-    for (j <- 0 until NumWays) {
-      val predCfiPosition = t1_trainRead(i)(j).cfiPosition
-      val canGetPosition  = t1_trainRead(i)(j).canGetPosition
-      // Use cfiPosition as an additional check to ensure the entry being updated
-      // hasn't been evicted or overwritten during the update window.
-      // Leverages the buffering effect: within a certain time window,
-      // recently evicted/updated entries are likely still in the buffer.
-      // Using notHitPosition is beneficial if available, no harm otherwise.
-      val entryHitVec = t1_trainResult.map { x =>
-        val notHitPosition = canGetPosition && (predCfiPosition =/= x.cfiPosition)
-        x.valid && x.hit && (x.tableId === i.U) && (x.wayId === j.U) && !notHitPosition
-        // x.valid && x.hit && (x.tableId === i.U) && (x.wayId === j.U)
-      }
-      val entryBaseTakenVec   = t1_trainResult.map(_.baseTaken)
-      val entryStrongBiasVec  = t1_trainResult.map(_.baseIsStrongBias)
-      val entryPredTakenVec   = t1_trainResult.map(_.predTaken)
-      val entryActualTakenVec = t1_trainResult.map(_.actualTaken)
-      val entryCfiPositionVec = t1_trainResult.map(_.cfiPosition)
-      val entryTakenCtrVec    = t1_trainResult.map(_.takenCtr)
-      val select              = entryHitVec.reduce(_ || _)
-      val entryHitOH          = PriorityEncoderOH(entryHitVec)
-      val predTaken           = Mux1H(entryHitOH, entryPredTakenVec)
-      val baseTaken           = Mux1H(entryHitOH, entryBaseTakenVec)
-      val baseIsStrongBias    = Mux1H(entryHitOH, entryStrongBiasVec)
-      val updateTaken         = Mux1H(entryHitOH, entryActualTakenVec)
-      val updateCfiPosition   = Mux1H(entryHitOH, entryCfiPositionVec)
-      val updateTakenCtr      = Mux1H(entryHitOH, entryTakenCtrVec)
-      val usefulValid = (predTaken ^ updateTaken) || // the prediction is not equal actual.
-        ((baseTaken ^ predTaken) && (predTaken === updateTaken)) // ||
-      // ((baseTaken === updateTaken) && baseIsStrongBias) // baseTaken can predict good.
-
-      tables(i).train.t1_update(j).valid                  := select && t1_fire
-      tables(i).train.t1_update(j).bits.updateValid       := select
-      tables(i).train.t1_update(j).bits.updateTaken       := updateTaken
-      tables(i).train.t1_update(j).bits.usefulValid       := usefulValid
-      tables(i).train.t1_update(j).bits.needUseful        := ((predTaken === updateTaken) && (baseTaken ^ predTaken))
-      tables(i).train.t1_update(j).bits.updateCfiPosition := updateCfiPosition
-      tables(i).train.t1_update(j).bits.updateTakenCtr    := updateTakenCtr
+    val t1_trainTag     = computeHashTag(t1_trainStartPc, t1_foldedPathHistForTrain, TableInfos, i)
+    val predCfiPosition = t1_trainRead(i).cfiPosition
+    val canGetPosition  = t1_trainRead(i).canGetPosition
+    // Use cfiPosition as an additional check to ensure the entry being updated
+    // hasn't been evicted or overwritten during the update window.
+    // Leverages the buffering effect: within a certain time window,
+    // recently evicted/updated entries are likely still in the buffer.
+    // Using notHitPosition is beneficial if available, no harm otherwise.
+    val entryHitVec = t1_trainResult.map { x =>
+      val notHitPosition = canGetPosition && (predCfiPosition =/= x.cfiPosition)
+      x.valid && x.hit && (x.tableId === i.U) && !notHitPosition
     }
-    val canAllocWay = VecInit(t1_trainRead(i).map(x => x.useful === 0.U))
-    tables(i).train.t1_alloc.valid            := t1_needAlloc && t1_fire && t1_normalAllocMask(i)
-    tables(i).train.t1_alloc.bits.taken       := t1_allocTaken
-    tables(i).train.t1_alloc.bits.wayMask     := PriorityEncoderOH(canAllocWay).asUInt
-    tables(i).train.t1_alloc.bits.cfiPosition := t1_allocCfiPosition
+    val entryBaseTakenVec   = t1_trainResult.map(_.baseTaken)
+    val entryStrongBiasVec  = t1_trainResult.map(_.baseIsStrongBias)
+    val entryPredTakenVec   = t1_trainResult.map(_.predTaken)
+    val entryActualTakenVec = t1_trainResult.map(_.actualTaken)
+    val entryCfiPositionVec = t1_trainResult.map(_.cfiPosition)
+    val entryTakenCtrVec    = t1_trainResult.map(_.takenCtr)
+    val select              = entryHitVec.reduce(_ || _)
+    val entryHitOH          = PriorityEncoderOH(entryHitVec)
+    val predTaken           = Mux1H(entryHitOH, entryPredTakenVec)
+    val baseTaken           = Mux1H(entryHitOH, entryBaseTakenVec)
+    val baseIsStrongBias    = Mux1H(entryHitOH, entryStrongBiasVec)
+    val updateTaken         = Mux1H(entryHitOH, entryActualTakenVec)
+    val updateCfiPosition   = Mux1H(entryHitOH, entryCfiPositionVec)
+    val updateTakenCtr      = Mux1H(entryHitOH, entryTakenCtrVec)
+    val usefulValid = (predTaken ^ updateTaken) || // the prediction is not equal actual.
+      ((baseTaken ^ predTaken) && (predTaken === updateTaken)) // ||
+    // ((baseTaken === updateTaken) && baseIsStrongBias) // baseTaken can predict good.
+
+    tables(i).train.t1_update.valid                  := select && t1_fire
+    tables(i).train.t1_update.bits.updateValid       := select
+    tables(i).train.t1_update.bits.updateTaken       := updateTaken
+    tables(i).train.t1_update.bits.usefulValid       := usefulValid
+    tables(i).train.t1_update.bits.needUseful        := ((predTaken === updateTaken) && (baseTaken ^ predTaken))
+    tables(i).train.t1_update.bits.updateCfiPosition := updateCfiPosition
+    tables(i).train.t1_update.bits.updateTakenCtr    := updateTakenCtr
+    tables(i).train.t1_alloc.valid                   := t1_needAlloc && t1_fire && t1_normalAllocMask(i)
+    tables(i).train.t1_alloc.bits.taken              := t1_allocTaken
+    tables(i).train.t1_alloc.bits.cfiPosition        := t1_allocCfiPosition
     // tables(i).train.t1_alloc.bits.tag         := t1_trainTag
     tables(i).train.t1_tag := t1_trainTag
   }
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTageTable.scala b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTageTable.scala
index 551272ef6e6..6958bc89950 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTageTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTageTable.scala
@@ -29,7 +29,6 @@ import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 // MicroTage table module implementing a banked SRAM with write buffer
 class MicroTageTable(
     val numSets:  Int,
-    val numWay:   Int,
     val tableId:  Int,
     val numBanks: Int = 4
 )(implicit p: Parameters) extends MicroTageModule with Helpers {
@@ -41,24 +40,24 @@ class MicroTageTable(
     }
     // Response bundle for table read
     class MicroTageResp extends Bundle {
-      val readEntries: Vec[MicroTageEntry] = Vec(numWay, new MicroTageEntry)
+      val readEntry: MicroTageEntry = new MicroTageEntry
     }
     val req:           Valid[MicroTageReq] = Input(Valid(new MicroTageReq))
     val resps:         MicroTageResp       = Output(new MicroTageResp)
-    val train:         MicroTageTrain      = new MicroTageTrain(numWay, numSets)
+    val train:         MicroTageTrain      = new MicroTageTrain(numSets)
     val usefulReset:   Bool                = Input(Bool())
     val sramResetDone: Bool                = Output(Bool())
   }
   val io = IO(new MicroTageTableIO)
   // Write buffer to handle write conflicts
-  private val wbuffer = Module(new BypassShadowBuffer(numSets, numWay, tableId, numBanks))
+  private val wbuffer = Module(new BypassShadowBuffer(numSets, tableId, numBanks))
 
   // Banked SRAM for storing MicroTage entries
   private val entrySram = Seq.tabulate(numBanks) { bankIdx =>
     Module(new SRAMTemplate(
       new MicroTageEntry,
       set = numSets / numBanks,
-      way = numWay,
+      way = 1,
       singlePort = true,
       shouldReset = true,
       withClockGate = true,
@@ -67,24 +66,6 @@ class MicroTageTable(
       suffix = Option("bpu_utage")
     )).suggestName(s"utage_entry_sram_bank${bankIdx}")
   }
-  // private val entrySram = Seq.tabulate(numBanks) { bankIdx =>
-  //   Module(new FoldedSRAMTemplate(
-  //     Vec(numWay, new MicroTageEntry),
-  //     setSplit = 1,
-  //     waySplit = 1,
-  //     dataSplit = 1,
-  //     set = numSets / numBanks,
-  //     width = 1,
-  //     shouldReset = true,
-  //     holdRead = false,
-  //     singlePort = true,
-  //     useBitmask = true,
-  //     withClockGate = false,
-  //     hasMbist = hasMbist,
-  //     hasSramCtl = hasSramCtl,
-  //     suffix = Option("bpu_utage")
-  //   )).suggestName(s"utage_entry_sram_bank$bankIdx")
-  // }
 
   // Calculate bank selection for read access
   private val bankId             = getBankId(io.req.bits.readIndex, numBanks)
@@ -108,22 +89,18 @@ class MicroTageTable(
 
   // Check if requested data is in write buffer
   wbuffer.io.req.readIndex := io.req.bits.readIndex
-  private val bufferHit         = wbuffer.io.resp.hit
-  private val bufferReadEntries = wbuffer.io.resp.readEntries
+  private val bufferHit       = wbuffer.io.resp.hit
+  private val bufferReadEntry = wbuffer.io.resp.readEntry
   // Convert SRAM response to proper type
-  private val sramRealReadEntries = WireDefault(0.U.asTypeOf(Vec(numWay, new MicroTageEntry)))
-  private val sramReadEntries     = bankReadEntries.asTypeOf(Vec(numWay, new MicroTageEntry))
-  sramRealReadEntries := sramReadEntries
+  private val sramRealReadEntry = WireDefault(0.U.asTypeOf(new MicroTageEntry))
+  private val sramReadEntry     = bankReadEntries.asTypeOf(new MicroTageEntry)
+  sramRealReadEntry := sramReadEntry
 
   // Select data from buffer (if hit) or SRAM (if miss)
-  private val readEntries = VecInit(
-    (bufferHit zip bufferReadEntries zip sramRealReadEntries).map {
-      case ((hit, bufferEntry), sramEntry) => Mux(hit, bufferEntry, sramEntry)
-    }
-  )
+  private val readEntry = Mux(bufferHit, bufferReadEntry, sramRealReadEntry)
 
   // Output read entries
-  io.resps.readEntries := readEntries
+  io.resps.readEntry := readEntry
 
   // Determine if write can proceed to SRAM
   // Write succeeds if accessing different banks or forceWrite is set
@@ -141,7 +118,9 @@ class MicroTageTable(
   private val writeEntry     = wbuffer.io.tryWrite.bits.writeData
   private val bankWriteIndex = getBankInnerIndex(wbuffer.io.tryWrite.bits.writeIndex, numBanks, numSets)
   private val forceWrite     = wbuffer.io.tryWrite.bits.forceWrite
-  private val writeMask      = wbuffer.io.tryWrite.bits.wMask
+  // The set-associative implementation is overly complex and fails to meet timing constraints
+  // with the current structure. Therefore, Way is hardcoded to 1.
+  private val writeMask = 1.U(1.W)
   entrySram.zipWithIndex.foreach { case (bank, bankIdx) =>
     val writeValid = (!bank.io.r.req.valid || forceWrite) && tryWrite && (writeBankId === bankIdx.U)
     bank.io.w(writeValid, writeEntry, bankWriteIndex, writeMask)
@@ -151,7 +130,5 @@ class MicroTageTable(
   private val needCheckConflict = RegNext(forceWrite && tryWrite, false.B)
   private val writeOHNext       = RegNext(UIntToOH(writeBankId))
   // Handle read-write conflicts in SRAM.
-  for (i <- 0 until numWay) {
-    sramRealReadEntries(i).valid := !((writeOHNext === a1_bankOH) && needCheckConflict) && sramReadEntries(i).valid
-  }
+  sramRealReadEntry.valid := !((writeOHNext === a1_bankOH) && needCheckConflict) && sramReadEntry.valid
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/utage/Parameters.scala
index c613d705e65..d8bade1099d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/Parameters.scala
@@ -23,11 +23,11 @@ import xiangshan.frontend.bpu.TageTableInfo
 case class MicroTageParameters(
     // TODO: The length of the Tag and its alias status will need to be adjusted later. The same applies to the number of items.
     TableInfos: Seq[MicroTageInfo] = Seq(
-      new MicroTageInfo(512, 5, 5, 15),
-      new MicroTageInfo(512, 9, 9, 15), // 3Taken maybe better than 2Taken
-      // new MicroTageInfo(512, 12, 12, 15),
-      new MicroTageInfo(512, 16, 10, 16), // follow Tage.
-      new MicroTageInfo(512, 24, 12, 16)
+      new MicroTageInfo(512, 5, 5, 8),
+      new MicroTageInfo(512, 9, 8, 8) // 3Taken maybe better than 2Taken
+      // new MicroTageInfo(512, 12, 8, 8),
+      // new MicroTageInfo(512, 16, 8, 8), // follow Tage.
+      // new MicroTageInfo(512, 24, 8, 8)
     ),
     TakenCtrWidth:       Int = 3,
     LowTickWidth:        Int = 7,
@@ -35,8 +35,7 @@ case class MicroTageParameters(
     UsefulWidth:         Int = 2,
     PCHighTagStart:      Int = 7,
     EnableTraceAndDebug: Boolean = false,
-    BaseTableSize:       Int = 512, // TODO: Not necessarily required; currently unused.
-    NumWays:             Int = 1
+    BaseTableSize:       Int = 512 // TODO: Not necessarily required; currently unused.
 ) {}
 
 trait HasMicroTageParameters extends HasBpuParameters {
@@ -49,10 +48,11 @@ trait HasMicroTageParameters extends HasBpuParameters {
   def UsefulWidth:     Int                 = utageParameters.UsefulWidth
   def BaseTableSize:   Int                 = utageParameters.BaseTableSize
   def PCHighTagStart:  Int                 = utageParameters.PCHighTagStart
-  def NumWays:         Int                 = utageParameters.NumWays
+  def NumWays:         Int                 = 1
+  def WayIdWidth:      Int                 = 1 max log2Ceil(NumWays)
 
   def MaxNumSets:        Int = 512
-  def MaxTagLen:         Int = 16
+  def MaxTagLen:         Int = 8
   def DebugPredIdxWidth: Int = log2Ceil(TableInfos(0).NumSets)
   def DebugPredTagWidth: Int = TableInfos(0).TagWidth
 
@@ -74,10 +74,11 @@ trait HasMicroTageParameters extends HasBpuParameters {
   def PCTagConcatBitsForLongHistory:     Seq[Int] = Seq(11, 10, 9, 7, 5, 3)
   def PCTagConcatBitsForVeryLongHistory: Seq[Int] = Seq(11, 7, 5, 3)
 
+  // Adjust the utilized PC bits according to the Tag length.
   def PCTagXorBitsForShortHistory:    Seq[Int] = Seq(12, 10, 8, 6, 4, 2)
-  def PCTagXorBitsForMediumHistory:   Seq[Int] = Seq(16, 14, 12, 10, 8, 6, 4, 2, 0)
-  def PCTagXorBitsForLongHistory:     Seq[Int] = Seq(18, 16, 14, 12, 10, 8, 6, 4, 2, 0)
-  def PCTagXorBitsForVeryLongHistory: Seq[Int] = Seq(20, 18, 16, 14, 12, 10, 8, 6, 4, 2, 1, 0)
+  def PCTagXorBitsForMediumHistory:   Seq[Int] = Seq(14, 12, 10, 8, 6, 4, 2, 0)
+  def PCTagXorBitsForLongHistory:     Seq[Int] = Seq(14, 13, 12, 10, 8, 6, 4, 3)
+  def PCTagXorBitsForVeryLongHistory: Seq[Int] = Seq(15, 13, 11, 9, 7, 6, 5, 2)
 
   def PCTagConcatBitsDefault: Seq[Int] = PCTagConcatBitsForShortHistory
   def PCTagXorBitsDefault:    Seq[Int] = PCTagXorBitsForShortHistory
```
