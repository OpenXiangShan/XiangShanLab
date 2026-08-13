# Commit Log
- Issue: #5159
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5159
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5159
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5159
- Changed files: 11
- Additions: 531
- Deletions: 408

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index aefed20c31c..a9271b3855e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -260,37 +260,35 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
 
   private val s2_mbtbResult    = mbtb.io.result
   private val s2_condTakenMask = tage.io.condTakenMask
-  private val s2_jumpMask = VecInit(s2_mbtbResult.hitMask.zip(s2_mbtbResult.attributes).map { case (hit, attribute) =>
-    hit && (attribute.isDirect || attribute.isIndirect)
+  private val s2_jumpMask = VecInit(s2_mbtbResult.map { e =>
+    e.valid && (e.bits.attribute.isDirect || e.bits.attribute.isIndirect)
   })
   private val s2_takenMask = VecInit(s2_condTakenMask.zip(s2_jumpMask).map { case (a, b) => a || b })
   private val s2_taken     = s2_takenMask.reduce(_ || _)
 
-  private val s2_compareMatrix      = CompareMatrix(s2_mbtbResult.positions)
+  private val s2_compareMatrix      = CompareMatrix(VecInit(s2_mbtbResult.map(_.bits.cfiPosition)))
   private val s2_firstTakenBranchOH = s2_compareMatrix.getLeastElementOH(s2_takenMask)
 
   private val s3_taken                      = RegEnable(s2_taken, s2_fire)
   private val s3_mbtbResult                 = RegEnable(s2_mbtbResult, s2_fire)
   private val s3_firstTakenBranchOH         = RegEnable(s2_firstTakenBranchOH, s2_fire)
-  private val s3_takenBranchPosition        = Mux1H(s3_firstTakenBranchOH, s3_mbtbResult.positions)
-  private val s3_takenBranchAttribute       = Mux1H(s3_firstTakenBranchOH, s3_mbtbResult.attributes)
-  private val s3_mbtbTarget                 = Mux1H(s3_firstTakenBranchOH, s3_mbtbResult.targets)
-  private val s3_firstTakenBranchIsReturn   = s3_takenBranchAttribute.isReturn
-  private val s3_firstTakenBranchIsIndirect = s3_takenBranchAttribute.isOtherIndirect
+  private val s3_firstTakenBranch           = Mux1H(s3_firstTakenBranchOH, s3_mbtbResult)
+  private val s3_firstTakenBranchIsReturn   = s3_firstTakenBranch.bits.attribute.isReturn
+  private val s3_firstTakenBranchIsIndirect = s3_firstTakenBranch.bits.attribute.isOtherIndirect
 
   private val s2_fallThroughPrediction = RegEnable(fallThrough.io.prediction, s1_fire)
   private val s3_fallThroughPrediction = RegEnable(s2_fallThroughPrediction, s2_fire)
 
   s3_prediction.taken       := s3_taken
-  s3_prediction.cfiPosition := Mux(s3_taken, s3_takenBranchPosition, s3_fallThroughPrediction.cfiPosition)
-  s3_prediction.attribute   := Mux(s3_taken, s3_takenBranchAttribute, s3_fallThroughPrediction.attribute)
+  s3_prediction.cfiPosition := Mux(s3_taken, s3_firstTakenBranch.bits.cfiPosition, s3_fallThroughPrediction.cfiPosition)
+  s3_prediction.attribute   := Mux(s3_taken, s3_firstTakenBranch.bits.attribute, s3_fallThroughPrediction.attribute)
   s3_prediction.target :=
     MuxCase(
       s3_fallThroughPrediction.target,
       Seq(
 //        (s3_taken && s3_firstTakenBranchIsReturn)                               -> ras.io.topRetAddr,
         (s3_taken && s3_firstTakenBranchIsIndirect && ittage.io.prediction.hit) -> ittage.io.prediction.target,
-        s3_taken                                                                -> s3_mbtbTarget
+        s3_taken                                                                -> s3_firstTakenBranch.bits.target
       )
     )
 
@@ -486,15 +484,14 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   XSPerfAccumulate("s1Invalid", !s1_valid)
 
   /* *** perf train *** */
-
   private val t0_mispredictBranch = train.bits.mispredictBranch
   private val t0_mbtbMeta         = train.bits.meta.mbtb
   private val t0_branches         = train.bits.branches
-  private val t0_mbtbHit =
-    t0_mbtbMeta.hitMask.zip(t0_mbtbMeta.positions).zip(t0_mbtbMeta.attributes).map {
-      case ((hit, position), attribute) =>
-        hit && position === t0_mispredictBranch.bits.cfiPosition && attribute === t0_mispredictBranch.bits.attribute
-    }.reduce(_ || _)
+  private val t0_mbtbHit = t0_mbtbMeta.entries.map { e =>
+    e.rawHit &&
+    e.position === t0_mispredictBranch.bits.cfiPosition &&
+    e.attribute === t0_mispredictBranch.bits.attribute
+  }.reduce(_ || _)
 
   private val perf_conditionalMispredict =
     train.valid && t0_mispredictBranch.valid && t0_mispredictBranch.bits.attribute.isConditional
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
index b1a6daabc22..f6cd45252d3 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
@@ -273,12 +273,14 @@ class StageCtrl(implicit p: Parameters) extends BpuBundle {
 }
 
 // sub predictors -> Bpu top
-class Prediction(implicit p: Parameters) extends BpuBundle {
-  val taken:       Bool            = Bool()
+class BtbInfo(implicit p: Parameters) extends BpuBundle {
   val cfiPosition: UInt            = UInt(CfiPositionWidth.W)
   val target:      PrunedAddr      = PrunedAddr(VAddrBits)
   val attribute:   BranchAttribute = new BranchAttribute
-  // TODO: what else do we need?
+}
+
+class Prediction(implicit p: Parameters) extends BtbInfo {
+  val taken: Bool = Bool()
 
   def isIdentical(other: Prediction): Bool =
     this.taken === other.taken &&
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
index 6990a3441a9..f813e48d879 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
@@ -18,13 +18,11 @@ package xiangshan.frontend.bpu.mbtb
 import chisel3._
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
-import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.bpu.TargetCarry
 import xiangshan.frontend.bpu.WriteReqBundle
 
 class MainBtbEntry(implicit p: Parameters) extends MainBtbBundle {
-
   // whether the entry is valid
   val valid: Bool = Bool()
 
@@ -34,7 +32,7 @@ class MainBtbEntry(implicit p: Parameters) extends MainBtbBundle {
   // Whether a branch is bias toward a single target
   // For conditional branch, this means bias toward same direction
   // For indirect branch, this means bias toward single target
-  val stronglyBiased: Bool = Bool()
+//  val stronglyBiased: Bool = Bool() // TODO
 
   // Relative position to the aligned start addr
   val position: UInt = UInt(CfiAlignedPositionWidth.W)
@@ -43,7 +41,7 @@ class MainBtbEntry(implicit p: Parameters) extends MainBtbBundle {
   val targetCarry:     TargetCarry = new TargetCarry
   val targetLowerBits: UInt        = UInt(TargetWidth.W)
 
-  val replaceCnt: UInt = UInt(2.W) // FIXME: not used for now
+//  val replaceCnt: UInt = UInt(2.W) // TODO: not used for now
 }
 
 class MainBtbSramWriteReq(implicit p: Parameters) extends WriteReqBundle with HasMainBtbParameters {
@@ -52,30 +50,12 @@ class MainBtbSramWriteReq(implicit p: Parameters) extends WriteReqBundle with Ha
   override def tag: Option[UInt] = Some(Cat(entry.tag, entry.position)) // use entry's tag directly
 }
 
-class ReplacerIO(implicit p: Parameters) extends MainBtbBundle {
-  // prediction hit update Replacer
-  val predictionSetIndxVec: Vec[UInt] = Input(Vec(NumAlignBanks, UInt(SetIdxLen.W)))
-  val predictionHitMask:    Vec[Bool] = Input(Vec(NumAlignBanks, Bool()))
-  val predictionTouchWays: Vec[Vec[Valid[UInt]]] =
-    Input(Vec(NumAlignBanks, Vec(NumWay, Valid(UInt(log2Up(NumWay).W)))))
-
-  // training hit update Replacer
-  val trainWriteValid:    Bool      = Input(Bool())
-  val trainSetIndx:       UInt      = Input(UInt(SetIdxLen.W))
-  val trainAlignBankMask: Vec[Bool] = Input(Vec(NumAlignBanks, Bool()))
-  val victimWayIdx:       UInt      = Output(UInt(log2Up(NumWay).W))
+class MainBtbMetaEntry(implicit p: Parameters) extends MainBtbBundle {
+  val rawHit:    Bool            = Bool()
+  val position:  UInt            = UInt(CfiPositionWidth.W)
+  val attribute: BranchAttribute = new BranchAttribute
 }
 
 class MainBtbMeta(implicit p: Parameters) extends MainBtbBundle {
-  val hitMask            = Vec(NumBtbResultEntries, Bool())
-  val stronglyBiasedMask = Vec(NumBtbResultEntries, Bool())
-  val positions          = Vec(NumBtbResultEntries, UInt(CfiPositionWidth.W)) // FIXME: use correct one
-  val attributes         = Vec(NumBtbResultEntries, new BranchAttribute)
-}
-
-class MainBtbResult(implicit p: Parameters) extends MainBtbBundle {
-  val hitMask    = Vec(NumBtbResultEntries, Bool())
-  val positions  = Vec(NumBtbResultEntries, UInt(CfiPositionWidth.W)) // FIXME: use correct one
-  val targets    = Vec(NumBtbResultEntries, PrunedAddr(VAddrBits))
-  val attributes = Vec(NumBtbResultEntries, new BranchAttribute)
+  val entries: Vec[MainBtbMetaEntry] = Vec(NumBtbResultEntries, new MainBtbMetaEntry)
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
index 28df6745f37..bf257d7fa4e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
@@ -16,10 +16,6 @@
 package xiangshan.frontend.bpu.mbtb
 
 import chisel3._
-import chisel3.util.MuxLookup
-import chisel3.util.isPow2
-import chisel3.util.log2Ceil
-import chisel3.util.log2Up
 import xiangshan.HasXSParameter
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.CrossPageHelper
@@ -32,18 +28,15 @@ trait Helpers extends HasMainBtbParameters
   def getSetIndex(pc: PrunedAddr): UInt =
     pc(SetIdxLen + InternalBankIdxLen + FetchBlockSizeWidth - 1, InternalBankIdxLen + FetchBlockSizeWidth)
 
-  def getNextSetIndex(pc: PrunedAddr): UInt =
-    getSetIndex(getNextAlignedAddr(pc))
-
   def getReplacerSetIndex(pc: PrunedAddr): UInt =
     pc(SetIdxLen + FetchBlockAlignWidth - 1, FetchBlockAlignWidth)
 
-  def getNextReplacerSetIndex(pc: PrunedAddr): UInt =
-    getReplacerSetIndex(pc) + 1.U
-
   def getAlignBankIndex(pc: PrunedAddr): UInt =
     pc(FetchBlockSizeWidth - 1, FetchBlockAlignWidth)
 
+  def getAlignBankIndexFromPosition(cfiPosition: UInt): UInt =
+    cfiPosition(CfiPositionWidth - 1, CfiPositionWidth - AlignBankIdxLen)
+
   def getTargetUpper(pc: PrunedAddr): UInt =
     pc(VAddrBits - 1, TargetWidth + instOffsetBits)
 
@@ -59,26 +52,20 @@ trait Helpers extends HasMainBtbParameters
       InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth
     )
 
-  def detectMultiHit(hitMask: IndexedSeq[Bool], position: IndexedSeq[UInt]): (Bool, Bool, UInt, Vec[Bool]) = {
+  def detectMultiHit(hitMask: IndexedSeq[Bool], position: IndexedSeq[UInt]): UInt = {
     require(hitMask.length == position.length)
     require(hitMask.length >= 2)
-    val isMultiHit        = WireDefault(false.B)
-    val isHigherAlignBank = WireDefault(false.B)
-    val multiHitWayIdx    = WireDefault(0.U(log2Up(NumWay).W))
-    val multiHitMask      = VecInit(Seq.fill(NumWay * NumAlignBanks)(false.B))
+    val multiHitMask = VecInit(Seq.fill(NumWay)(false.B))
     for {
-      i <- 0 until NumWay * NumAlignBanks
-      j <- i + 1 until NumWay * NumAlignBanks
+      i <- 0 until NumWay
+      j <- i + 1 until NumWay
     } {
       val bothHit      = hitMask(i) && hitMask(j)
       val samePosition = position(i) === position(j)
       when(bothHit && samePosition) {
-        isMultiHit        := true.B
-        isHigherAlignBank := i.U >= NumWay.U
-        multiHitWayIdx    := (i % NumWay).U
-        multiHitMask(i)   := true.B
+        multiHitMask(i) := true.B
       }
     }
-    (isMultiHit, isHigherAlignBank, multiHitWayIdx, multiHitMask)
+    multiHitMask.asUInt
   }
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
index 7cad9e754c0..36cfaac543a 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
@@ -18,304 +18,122 @@ package xiangshan.frontend.bpu.mbtb
 import chisel3._
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
-import utility.XSError
 import utility.XSPerfAccumulate
 import utility.XSPerfHistogram
-import utility.sram.SRAMTemplate
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
-import xiangshan.frontend.bpu.WriteBuffer
+import xiangshan.frontend.bpu.BtbInfo
 
 class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParameters with Helpers {
   class MainBtbIO(implicit p: Parameters) extends BasePredictorIO {
     // prediction specific bundle
-    val result: MainBtbResult = Output(new MainBtbResult)
-    val meta:   MainBtbMeta   = Output(new MainBtbMeta)
+    val result: Vec[Valid[BtbInfo]] = Output(Vec(NumBtbResultEntries, Valid(new BtbInfo)))
+    val meta:   MainBtbMeta         = Output(new MainBtbMeta)
   }
 
   val io: MainBtbIO = IO(new MainBtbIO)
 
-  /* *** internal parameters *** */
-  private val Alignment = FetchBlockSize / NumAlignBanks
-
   /* *** submodules *** */
-  private val sramBanks =
-    Seq.tabulate(NumAlignBanks, NumInternalBanks, NumWay) { (alignIdx, bankIdx, wayIdx) =>
-      Module(
-        new SRAMTemplate(
-          new MainBtbEntry,
-          set = NumSets,
-          way = 1, // Not using way in the template, preparing for future skewed assoc
-          singlePort = true,
-          shouldReset = true,
-          holdRead = true,
-          withClockGate = true,
-          hasMbist = hasMbist,
-          hasSramCtl = hasSramCtl
-        )
-      ).suggestName(s"mbtb_sram_align${alignIdx}_bank${bankIdx}_way${wayIdx}")
-    }
-  private val writeBuffers = Seq.tabulate(NumAlignBanks, NumInternalBanks) { (_, _) =>
-    Module(new WriteBuffer(new MainBtbSramWriteReq, WriteBufferSize, NumWay))
-  }
-
-  private val resetDone = RegInit(false.B)
-  when(sramBanks.flatMap(_.flatMap(_.map(_.io.r.req.ready))).reduce(_ && _)) {
-    resetDone := true.B
-  }
-  io.resetDone := resetDone
+  private val alignBanks = Seq.tabulate(NumAlignBanks)(alignIdx => Module(new MainBtbAlignBank(alignIdx)))
 
-  private val replacer = Module(new MainBtbReplacer)
+  io.resetDone := alignBanks.map(_.io.resetDone).reduce(_ && _)
 
-  sramBanks.map(_.map(_.map { m =>
-    m.io.r.req.valid       := false.B
-    m.io.r.req.bits.setIdx := 0.U
-    m.io.w.req.valid       := false.B
-    m.io.w.req.bits.setIdx := 0.U
-    m.io.w.req.bits.data   := DontCare
-  })) // Default closed, addrs are pulled to 0 to reduce power.
-
-  sramBanks.flatten.zip(writeBuffers.flatten).foreach {
-    case (ways, buffer) =>
-      ways zip buffer.io.read foreach {
-        case (way: SRAMTemplate[MainBtbEntry], buf: DecoupledIO[MainBtbSramWriteReq]) =>
-          way.io.w.req.valid        := buf.valid && !way.io.r.req.valid
-          way.io.w.req.bits.data(0) := buf.bits.entry
-          way.io.w.req.bits.setIdx  := buf.bits.setIdx
-          buf.ready                 := way.io.w.req.ready && !way.io.r.req.valid
-      }
+  private val s0_fire, s1_fire, s2_fire = Wire(Bool())
+  alignBanks.foreach { b =>
+    b.io.stageCtrl.s0_fire := s0_fire
+    b.io.stageCtrl.s1_fire := s1_fire
+    b.io.stageCtrl.s2_fire := s2_fire
+    b.io.stageCtrl.s3_fire := false.B
   }
 
-  /* predict stage 0
-   * setup SRAM
+  /* *** s0 ***
+   * calculate per-bank startVAddr and posHigherBits
+   * send read request to alignBanks
    */
-  private val s0_fire             = io.stageCtrl.s0_fire && io.enable
-  private val s0_startVAddr       = io.startVAddr
-  private val s0_thisSetIdx       = getSetIndex(s0_startVAddr)
-  private val s0_nextSetIdx       = getNextSetIndex(s0_startVAddr)
-  private val s0_internalBankIdx  = getInternalBankIndex(s0_startVAddr)
-  private val s0_internalBankMask = UIntToOH(s0_internalBankIdx) & Fill(NumInternalBanks, s0_fire)
-  private val s0_alignBankIdx     = getAlignBankIndex(s0_startVAddr)
-  private val s0_setIdxVec: Vec[UInt] =
-    VecInit.tabulate(NumAlignBanks)(bankIdx => Mux(bankIdx.U < s0_alignBankIdx, s0_nextSetIdx, s0_thisSetIdx))
-  require(s0_thisSetIdx.getWidth == SetIdxLen, s"Set index width mismatch: ${s0_thisSetIdx.getWidth} != $SetIdxLen")
-  XSError(
-    s0_internalBankIdx >= NumInternalBanks.U,
-    s"Invalid internal bank index: $s0_internalBankIdx, max: ${NumInternalBanks - 1}"
+  s0_fire := io.stageCtrl.s0_fire && io.enable
+  private val s0_startVAddr        = io.startVAddr
+  private val s0_firstAlignBankIdx = getAlignBankIndex(s0_startVAddr)
+  private val s0_startVAddrVec = vecRotateRight(
+    VecInit.tabulate(NumAlignBanks) { i =>
+      if (i == 0)
+        s0_startVAddr // keep lower bits for the first one
+      else
+        getAlignedAddr(s0_startVAddr + (i << FetchBlockAlignWidth).U) // use aligned for others
+    },
+    s0_firstAlignBankIdx
   )
-  sramBanks zip s0_setIdxVec foreach { case (alignmentBank, setIdx) =>
-    alignmentBank zip s0_internalBankMask.asBools foreach { case (internalBank, bankEnable) =>
-      when(bankEnable) {
-        internalBank.foreach { way =>
-          way.io.r.req.valid       := true.B
-          way.io.r.req.bits.setIdx := setIdx
-        }
-      }.otherwise {
-        // pull to 0 when not firing to reduce power.
-        internalBank.foreach { way =>
-          way.io.r.req.valid       := false.B
-          way.io.r.req.bits.setIdx := 0.U
-        }
-      }
-    }
-  }
-
-  /* predict stage 1
-   *
-   * get result from SRAM
-   * rotate SRAM result
-   */
-  private val s1_fire                      = io.stageCtrl.s1_fire && io.enable
-  private val s1_startVAddr                = RegEnable(s0_startVAddr, s0_fire)
-  private val s1_internalBankMask          = RegEnable(s0_internalBankMask, s0_fire)
-  private val s1_setIdxVec                 = RegEnable(s0_setIdxVec, s0_fire)
-  private val s1_tag                       = getTag(s1_startVAddr)
-  private val s1_alignBankIdx              = getAlignBankIndex(s1_startVAddr)
-  private val s1_posHigherBitsPerAlignBank = vecRotateRight(VecInit.tabulate(NumAlignBanks)(i => i.U), s1_alignBankIdx)
-  private val s1_posHigherBits             = VecInit(s1_posHigherBitsPerAlignBank.flatMap(Seq.fill(NumWay)(_)))
-
-  private val s1_rawBtbEntries = VecInit(sramBanks.flatMap { alignBank =>
-    Mux1H(s1_internalBankMask, alignBank.map(bank => VecInit(bank.flatMap(_.io.r.resp.data))))
-  })
-
-  private val s1_alignBankCrossPageMask = (0 until NumAlignBanks).map { i =>
-    val currentAlignStartVAddr = getAlignedAddr(s1_startVAddr + (i * FetchBlockAlignSize).U)
-    isCrossPage(s1_startVAddr, currentAlignStartVAddr)
-  }
-  private val s1_rotatedAlignBankCrossPageMask = vecRotateRight(VecInit(s1_alignBankCrossPageMask), s1_alignBankIdx)
-  private val s1_crossPageMask                 = VecInit(s1_rotatedAlignBankCrossPageMask.flatMap(Seq.fill(NumWay)(_)))
-
-  require(s1_alignBankIdx.getWidth == log2Ceil(NumAlignBanks))
-
-  /* predict stage 2
-   *
-   * do tag compare and position compare
-   * calculate target
-   * map results into a per-slot vec
-   * resolve multi-hit
-   */
-  private val s2_fire             = io.stageCtrl.s2_fire && io.enable
-  private val s2_startVAddr       = RegEnable(s1_startVAddr, s1_fire)
-  private val s2_setIdxVec        = RegEnable(s1_setIdxVec, s1_fire)
-  private val s2_internalBankMask = RegEnable(s1_internalBankMask, s1_fire)
-  private val s2_rawBtbEntries    = RegEnable(s1_rawBtbEntries, s1_fire)
-  private val s2_tag              = RegEnable(s1_tag, s1_fire)
-  private val s2_posHigherBits    = RegEnable(s1_posHigherBits, s1_fire)
-  private val s2_crossPageMask    = RegEnable(s1_crossPageMask, s1_fire)
-  private val s2_alignBankIdx     = RegEnable(s1_alignBankIdx, s1_fire)
-  private val s2_positions = s2_posHigherBits.zip(s2_rawBtbEntries).map { case (h, entry) =>
-    Cat(h, entry.position) // Add higher bits before using
-  }
-  private val s2_rawHitMask = s2_rawBtbEntries.map(entry => entry.valid && entry.tag === s2_tag)
-  private val s2_hitMask = s2_rawHitMask.zip(s2_rawBtbEntries).zip(s2_crossPageMask).zipWithIndex.map {
-    case (((hit, entry), isCrossPage), i) =>
-      hit && !isCrossPage && (
-        (i / NumWay).U =/= s2_alignBankIdx ||
-          entry.position >= getAlignedInstOffset(s2_startVAddr)
-      )
-  }
-  private val s2_targets =
-    s2_rawBtbEntries.map(e =>
-      getFullTarget(s2_startVAddr, e.targetLowerBits, Some(e.targetCarry))
-    ) // FIXME: parameterize target carry
-
-  private val s2_thisReplacerSetIdx = getReplacerSetIndex(s2_startVAddr)
-  private val s2_nextReplacerSetIdx = getNextReplacerSetIndex(s2_startVAddr)
-  private val s2_replacerSetIdxVec: Vec[UInt] = VecInit.tabulate(NumAlignBanks)(bankIdx =>
-    Mux(bankIdx.U < s2_alignBankIdx, s2_nextReplacerSetIdx, s2_thisReplacerSetIdx)
+  private val s0_posHigherBitsVec = vecRotateRight(
+    VecInit.tabulate(NumAlignBanks)(i => i.U(AlignBankIdxLen.W)),
+    s0_firstAlignBankIdx
   )
 
-  private val s2_stateTouchs: Vec[Vec[Valid[UInt]]] =
-    Wire(Vec(NumAlignBanks, Vec(NumWay, Valid(UInt(log2Up(NumWay).W)))))
-  // FIXME: this is not a good way to do this, but it works for now
-  for (alignIdx <- 0 until NumAlignBanks; wayIdx <- 0 until NumWay) {
-    s2_stateTouchs(alignIdx)(wayIdx).valid := s2_fire && s2_hitMask(alignIdx * NumWay + wayIdx)
-    s2_stateTouchs(alignIdx)(wayIdx).bits  := wayIdx.U
+  alignBanks.zipWithIndex.foreach { case (b, i) =>
+    b.io.read.req.startVAddr    := s0_startVAddrVec(i)
+    b.io.read.req.posHigherBits := s0_posHigherBitsVec(i)
+    b.io.read.req.crossPage     := isCrossPage(s0_startVAddrVec(i), s0_startVAddr)
   }
-  replacer.io.predictionSetIndxVec := s2_replacerSetIdxVec
-  replacer.io.predictionTouchWays  := s2_stateTouchs
-  replacer.io.predictionHitMask    := VecInit(s2_stateTouchs.map(_.map(_.valid).reduce(_ || _) && s2_fire))
-
-  dontTouch(s2_replacerSetIdxVec)
-  dontTouch(s2_stateTouchs)
-  // dontTouch(s2_nextState)
 
-  private val (s2_multihit, s2_isHigherAlignBank, s2_multiHitWayIdx, s2_multiHitMask) =
-    detectMultiHit(s2_hitMask, s2_positions)
-  private val s2_multiWriteAlignBankMask: Seq[Bool]    = UIntToOH(s2_isHigherAlignBank).asBools
-  private val s2_multiWayIdxMask:         UInt         = UIntToOH(s2_multiHitWayIdx)
-  private val s2_multiSetIdx:             UInt         = Mux1H(s2_multiWriteAlignBankMask, s2_setIdxVec)
-  private val s2_multiWriteEntry:         MainBtbEntry = WireInit(0.U.asTypeOf(new MainBtbEntry))
-
-  dontTouch(s2_multihit)
-  dontTouch(s2_isHigherAlignBank)
-  dontTouch(s2_multiHitWayIdx)
+  /* *** s1 ***
+   * just wait alignBanks
+   */
+  s1_fire := io.stageCtrl.s1_fire && io.enable
 
-  io.result.hitMask    := s2_hitMask
-  io.result.positions  := s2_positions
-  io.result.targets    := s2_targets
-  io.result.attributes := s2_rawBtbEntries.map(_.attribute)
+  /* *** s2 ***
+   * receive read response from alignBanks
+   * send out prediction result and meta info
+   */
+  s2_fire := io.stageCtrl.s2_fire && io.enable
 
-  io.meta.hitMask            := s2_rawHitMask
-  io.meta.positions          := s2_positions
-  io.meta.stronglyBiasedMask := DontCare // FIXME: add bias logic
-  io.meta.attributes         := s2_rawBtbEntries.map(_.attribute)
+  io.result       := VecInit(alignBanks.flatMap(_.io.read.resp.map(_.info)))
+  io.meta.entries := VecInit(alignBanks.flatMap(_.io.read.resp.map(_.meta)))
 
-  /* training stage 0 */
+  /* *** t0 ***
+   * receive training data and latch
+   */
   private val t0_valid = io.train.valid && io.enable
   private val t0_train = io.train.bits
 
-  /* training stage 1 */
+  /* *** t1 ***
+   * calculate write data and write to alignBanks
+   */
   private val t1_valid = RegNext(t0_valid) && io.enable
   private val t1_train = RegEnable(t0_train, t0_valid)
 
-  private val t1_internalBankIdx  = getInternalBankIndex(t1_train.startVAddr)
-  private val t1_internalBankMask = UIntToOH(t1_internalBankIdx)
-  private val t1_thisSetIdx       = getSetIndex(t1_train.startVAddr)
-  private val t1_nextSetIdx       = getNextSetIndex(t1_train.startVAddr)
-  private val t1_alignBankIdx     = getAlignBankIndex(t1_train.startVAddr)
+  private val t1_startVAddr        = t1_train.startVAddr
+  private val t1_firstAlignBankIdx = getAlignBankIndex(t1_startVAddr)
+  private val t1_startVAddrVec = vecRotateRight(
+    VecInit.tabulate(NumAlignBanks)(i => getAlignedAddr(t1_startVAddr + (i << FetchBlockAlignWidth).U)),
+    t1_firstAlignBankIdx
+  )
   private val t1_meta             = t1_train.meta.mbtb
-  private val t1_setIdxVec =
-    VecInit.tabulate(NumAlignBanks)(bankIdx => Mux(bankIdx.U < t1_alignBankIdx, t1_nextSetIdx, t1_thisSetIdx))
-
   private val t1_mispredictBranch = t1_train.mispredictBranch
 
-  private val t1_hitMispredictBranch = t1_meta.hitMask.zip(t1_meta.positions).zip(t1_meta.attributes).map {
-    case ((hit, position), attribute) =>
-      hit && position === t1_mispredictBranch.bits.cfiPosition && attribute === t1_mispredictBranch.bits.attribute
+  private val t1_hitMispredictBranch = t1_meta.entries.map { e =>
+    e.rawHit &&
+    e.position === t1_mispredictBranch.bits.cfiPosition &&
+    e.attribute === t1_mispredictBranch.bits.attribute
   }.reduce(_ || _)
 
   private val t1_writeValid = t1_valid && t1_mispredictBranch.valid && !t1_hitMispredictBranch
 
-  private val t1_writeEntry = Wire(new MainBtbEntry)
-  t1_writeEntry.valid           := true.B   // FIXME: invalidate
-  t1_writeEntry.tag             := getTag(t1_train.startVAddr)
-  t1_writeEntry.position        := t1_mispredictBranch.bits.cfiPosition
-  t1_writeEntry.targetLowerBits := getTargetLowerBits(t1_mispredictBranch.bits.target)
-  t1_writeEntry.targetCarry     := getTargetCarry(t1_train.startVAddr, t1_mispredictBranch.bits.target)
-  t1_writeEntry.attribute       := t1_mispredictBranch.bits.attribute
-  t1_writeEntry.stronglyBiased  := false.B  // FIXME
-  t1_writeEntry.replaceCnt      := DontCare // FIXME:
-
-  private val t1_writeAlignBankIdx =
-    t1_mispredictBranch.bits.cfiPosition(CfiPositionWidth - 1, CfiPositionWidth - log2Ceil(NumAlignBanks))
-  private val t1_rawWriteAlignBankMask = VecInit(UIntToOH(t1_writeAlignBankIdx).asBools)
-  private val t1_writeAlignBankMask    = vecRotateRight(t1_rawWriteAlignBankMask, t1_alignBankIdx)
-
-  private val t1_thisReplacerSetIdx = getReplacerSetIndex(t1_train.startVAddr)
-  private val t1_nextReplacerSetIdx = getNextReplacerSetIndex(t1_train.startVAddr)
-  private val t1_replacerSetIdxVec: Vec[UInt] = VecInit.tabulate(NumAlignBanks)(bankIdx =>
-    Mux(bankIdx.U < t1_alignBankIdx, t1_nextReplacerSetIdx, t1_thisReplacerSetIdx)
+  private val t1_writeAlignBankIdx = getAlignBankIndexFromPosition(t1_mispredictBranch.bits.cfiPosition)
+  private val t1_writeAlignBankMask = vecRotateRight(
+    VecInit(UIntToOH(t1_writeAlignBankIdx).asBools),
+    t1_firstAlignBankIdx
   )
-  private val t1_replacerSetIdx = Mux1H(t1_writeAlignBankMask, t1_replacerSetIdxVec)
-  private val t1_replacerBankMask: Vec[Bool] = t1_writeAlignBankMask
-  replacer.io.trainWriteValid    := t1_writeValid
-  replacer.io.trainSetIndx       := t1_replacerSetIdx
-  replacer.io.trainAlignBankMask := t1_writeAlignBankMask
-
-  dontTouch(t1_replacerSetIdxVec)
-  dontTouch(t1_replacerSetIdx)
-  dontTouch(t1_writeAlignBankMask)
-  private val t1_writeWayMask = UIntToOH(replacer.io.victimWayIdx)
-  require(t1_writeWayMask.getWidth == NumWay, s"Write way mask width mismatch: ${t1_writeWayMask.getWidth} != $NumWay")
 
-  private val multiWriteConflict = s2_multihit && s2_fire && t1_writeValid &&
-    (s2_multiWriteAlignBankMask zip t1_writeAlignBankMask map { case (a, b) => a && b }).reduce(_ || _) &&
-    (s2_internalBankMask.asBools zip t1_internalBankMask.asBools map { case (a, b) => a && b }).reduce(_ || _)
-
-  // Write to SRAM
-  writeBuffers zip t1_setIdxVec zip t1_writeAlignBankMask zip s2_multiWriteAlignBankMask foreach {
-    case (((alignmentBank, setIdx), alignBankEnable), multiAlignBankEnable) =>
-      alignmentBank zip t1_internalBankMask.asBools zip s2_internalBankMask.asBools foreach {
-        case ((buffer, bankEnable), multiBankEnable) =>
-          buffer.io.write zip t1_writeWayMask.asBools zip s2_multiWayIdxMask.asBools foreach {
-            case ((port, wayEnable), multiWayEnable) =>
-              val multiWriteEnable = s2_multihit && s2_fire && multiAlignBankEnable && multiBankEnable && multiWayEnable
-              val writeEnable      = t1_writeValid && wayEnable && alignBankEnable && bankEnable
-              port.valid := writeEnable || (multiWriteEnable && !multiWriteConflict)
-              port.bits.setIdx := Mux(
-                writeEnable,
-                setIdx,
-                Mux(multiWriteEnable && !multiWriteConflict, s2_multiSetIdx, 0.U)
-              ) // pull to 0 when not firing to reduce power
-              port.bits.entry := Mux(writeEnable, t1_writeEntry, 0.U.asTypeOf(new MainBtbEntry))
-          }
-      }
+  alignBanks.zipWithIndex.foreach { case (b, i) =>
+    b.io.write.req.valid           := t1_writeValid && t1_writeAlignBankMask(i)
+    b.io.write.req.bits.startVAddr := t1_startVAddrVec(i)
+    b.io.write.req.bits.branchInfo := t1_mispredictBranch.bits
   }
 
-  dontTouch(t1_writeValid)
-  dontTouch(t1_writeAlignBankMask)
-  dontTouch(t1_internalBankMask)
-  dontTouch(t1_writeWayMask)
-
-  /* ** statistics ** */
-
+  /* *** statistics *** */
+  private val perf_s2HitMask = VecInit(alignBanks.flatMap(_.io.read.resp.map(_.info.valid)))
   XSPerfAccumulate("total_train", t1_valid)
-  XSPerfAccumulate("pred_hit", s2_fire && s2_hitMask.reduce(_ || _))
-  XSPerfHistogram("pred_hit_count", PopCount(s2_hitMask), s2_fire, 0, NumWay * NumAlignBanks)
+  XSPerfAccumulate("pred_hit", s2_fire && perf_s2HitMask.reduce(_ || _))
+  XSPerfHistogram("pred_hit_count", PopCount(perf_s2HitMask), s2_fire, 0, NumWay * NumAlignBanks)
   XSPerfAccumulate("train_write_new_entry", t1_writeValid)
   XSPerfAccumulate("train_has_mispredict", t1_valid && t1_mispredictBranch.valid)
   XSPerfAccumulate("train_hit_mispredict", t1_valid && t1_mispredictBranch.valid && t1_hitMispredictBranch)
-  XSPerfAccumulate("multihit_write_conflict", multiWriteConflict)
-  XSPerfHistogram("multihit_count", PopCount(s2_multiHitMask), s2_fire, 0, NumWay * NumAlignBanks)
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
new file mode 100644
index 00000000000..3a443c00531
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
@@ -0,0 +1,212 @@
+// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+// Copyright (c) 2020-2021 Peng Cheng Laboratory
+//
+// XiangShan is licensed under Mulan PSL v2.
+// You can use this software according to the terms and conditions of the Mulan PSL v2.
+// You may obtain a copy of Mulan PSL v2 at:
+//          https://license.coscl.org.cn/MulanPSL2
+//
+// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+//
+// See the Mulan PSL v2 for more details.
+
+package xiangshan.frontend.bpu.mbtb
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.XSPerfHistogram
+import xiangshan.frontend.PrunedAddr
+import xiangshan.frontend.bpu.BranchInfo
+import xiangshan.frontend.bpu.BtbInfo
+import xiangshan.frontend.bpu.StageCtrl
+
+class MainBtbAlignBank(
+    alignIdx: Int
+)(implicit p: Parameters) extends MainBtbModule with Helpers {
+  class MainBtbAlignBankIO extends Bundle {
+    class Read extends Bundle {
+      class Req extends Bundle {
+        // NOTE: this startVAddr is not from Bpu top, it's calculated in MainBtb top
+        // i.e. vecRotateRight(VecInit.tabulate(NumAlignBanks)(startVAddr + _ * alignSize), startAlignIdx)(alignIdx)
+        val startVAddr:    PrunedAddr = new PrunedAddr(VAddrBits)
+        val posHigherBits: UInt       = UInt(AlignBankIdxLen.W)
+        val crossPage:     Bool       = Bool()
+      }
+
+      class Resp extends Bundle {
+        val info: Valid[BtbInfo]   = Valid(new BtbInfo)
+        val meta: MainBtbMetaEntry = new MainBtbMetaEntry
+      }
+
+      // don't need Valid or Decoupled here, AlignBank's pipeline is coupled with top, so we use stageCtrl to control
+      val req: Req = Input(new Req)
+
+      val resp: Vec[Resp] = Output(Vec(NumWay, new Resp))
+    }
+
+    class Write extends Bundle {
+      class Req extends Bundle {
+        // similar to Read.Req.startVAddr, calculated in MainBtb top
+        val startVAddr: PrunedAddr = new PrunedAddr(VAddrBits)
+        val branchInfo: BranchInfo = new BranchInfo
+      }
+
+      val req: Valid[Req] = Flipped(Valid(new Req))
+    }
+
+    val resetDone: Bool      = Output(Bool())
+    val stageCtrl: StageCtrl = Input(new StageCtrl)
+
+    val read:  Read  = new Read
+    val write: Write = new Write
+  }
+
+  val io: MainBtbAlignBankIO = IO(new MainBtbAlignBankIO)
+
+  // alias
+  private val r = io.read
+  private val w = io.write
+
+  private val internalBanks = Seq.tabulate(NumInternalBanks) { bankIdx =>
+    Module(new MainBtbInternalBank(alignIdx, bankIdx))
+  }
+
+  private val replacer = Module(new MainBtbReplacer)
+
+  io.resetDone := internalBanks.map(_.io.resetDone).reduce(_ && _)
+
+  /* *** s0 ***
+   * send read req to internal banks (srams)
+   */
+  private val s0_fire             = io.stageCtrl.s0_fire
+  private val s0_startVAddr       = r.req.startVAddr
+  private val s0_posHigherBits    = r.req.posHigherBits
+  private val s0_crossPage        = r.req.crossPage
+  private val s0_setIdx           = getSetIndex(s0_startVAddr)
+  private val s0_internalBankIdx  = getInternalBankIndex(s0_startVAddr)
+  private val s0_internalBankMask = UIntToOH(s0_internalBankIdx, NumInternalBanks)
+  private val s0_alignBankIdx     = getAlignBankIndex(s0_startVAddr)
+
+  // mainBtb top is responsible for sending the correct startVAddr to alignBanks,
+  // so here we should always see getAlignBankIndex(s0_startVAddr) == physical alignIdx.
+  assert(!s0_fire || s0_alignBankIdx === alignIdx.U, "MainBtbAlignBank alignIdx mismatch")
+
+  internalBanks.zipWithIndex.foreach { case (b, i) =>
+    // NOTE: if crossPage, we need to drop the entries to satisfy Ifu/ICache's requirement,
+    //       so we also can drop read req to save power.
+    // FIXME: but this might be timing critical, need to be verified.
+    b.io.read.req.valid       := s0_fire && s0_internalBankMask(i) && !s0_crossPage
+    b.io.read.req.bits.setIdx := s0_setIdx
+  }
+
+  /* *** s1 ***
+   * receive read resp from internal banks
+   * select 1 internal bank's resp
+   */
+  private val s1_fire             = io.stageCtrl.s1_fire
+  private val s1_startVAddr       = RegEnable(s0_startVAddr, s0_fire)
+  private val s1_posHigherBits    = RegEnable(s0_posHigherBits, s0_fire)
+  private val s1_crossPage        = RegEnable(s0_crossPage, s0_fire)
+  private val s1_internalBankMask = RegEnable(s0_internalBankMask, s0_fire)
+
+  private val s1_rawEntries = Mux1H(
+    s1_internalBankMask,
+    internalBanks.map(_.io.read.resp.entries)
+  )
+
+  /* *** s2 ***
+   * check entries hit
+   * filter-out unneeded entries
+   * send resp to top
+   */
+  private val s2_fire             = io.stageCtrl.s2_fire
+  private val s2_startVAddr       = RegEnable(s1_startVAddr, s1_fire)
+  private val s2_posHigherBits    = RegEnable(s1_posHigherBits, s1_fire)
+  private val s2_crossPage        = RegEnable(s1_crossPage, s1_fire)
+  private val s2_internalBankMask = RegEnable(s1_internalBankMask, s1_fire)
+  private val s2_rawEntries       = RegEnable(s1_rawEntries, s1_fire)
+
+  private val s2_setIdx = getSetIndex(s2_startVAddr)
+  private val s2_tag    = getTag(s2_startVAddr)
+
+  // NOTE: when we calculate startVAddr in MainBtb top, we have selected whether lower bits should be masked
+  //       (see s0_startVAddrVec)
+  //       so here, if this alignBank is not the first alignBank of the fetch block, we'll get s2_alignedInstOffset = 0
+  //       and, we'll do a (e.position >= 0) check later, which is always true
+  private val s2_alignedInstOffset = getAlignedInstOffset(s2_startVAddr)
+
+  // send resp
+  (r.resp zip s2_rawEntries).foreach { case (resp, e) =>
+    // send rawHit for training
+    val rawHit = e.valid && e.tag === s2_tag
+    // filter out branches before alignedInstOffset
+    // also filter out all entries if crossPage to satisfy Ifu/ICache's requirement
+    val hit = rawHit && e.position >= s2_alignedInstOffset && !s2_crossPage
+    resp.info.valid            := hit
+    resp.info.bits.cfiPosition := Cat(s2_posHigherBits, e.position)
+    resp.info.bits.target      := getFullTarget(s2_startVAddr, e.targetLowerBits, Some(e.targetCarry))
+    resp.info.bits.attribute   := e.attribute
+
+    resp.meta.rawHit    := rawHit
+    resp.meta.attribute := e.attribute
+    resp.meta.position  := Cat(s2_posHigherBits, e.position)
+  }
+
+  // add an alias for hitMask for later use & debug purpose
+  private val s2_hitMask = VecInit(r.resp.map(_.info.valid))
+  dontTouch(s2_hitMask)
+
+  // update replacer
+  replacer.io.predictTouch.valid        := s2_fire && s2_hitMask.reduce(_ || _)
+  replacer.io.predictTouch.bits.setIdx  := getReplacerSetIndex(s2_startVAddr)
+  replacer.io.predictTouch.bits.wayMask := s2_hitMask.asUInt
+
+  /* *** t1 ***
+   * send write req to internal banks (srams)
+   */
+  private val t1_valid            = w.req.valid
+  private val t1_startVAddr       = w.req.bits.startVAddr
+  private val t1_branchInfo       = w.req.bits.branchInfo
+  private val t1_setIdx           = getSetIndex(t1_startVAddr)
+  private val t1_internalBankIdx  = getInternalBankIndex(t1_startVAddr)
+  private val t1_internalBankMask = UIntToOH(t1_internalBankIdx, NumInternalBanks)
+  private val t1_alignBankIdx     = getAlignBankIndex(t1_startVAddr)
+  private val t1_wayMask          = replacer.io.victim.wayMask
+
+  private val t1_entry = Wire(new MainBtbEntry)
+  t1_entry.valid           := true.B
+  t1_entry.tag             := getTag(t1_startVAddr)
+  t1_entry.position        := t1_branchInfo.cfiPosition
+  t1_entry.targetLowerBits := getTargetLowerBits(t1_branchInfo.target)
+  t1_entry.targetCarry     := getTargetCarry(t1_startVAddr, t1_branchInfo.target)
+  t1_entry.attribute       := t1_branchInfo.attribute
+
+  // similar to s0 case
+  assert(!t1_valid || t1_alignBankIdx === alignIdx.U, "MainBtbAlignBank alignIdx mismatch")
+
+  internalBanks.zipWithIndex.foreach { case (b, i) =>
+    b.io.write.req.valid        := t1_valid && t1_internalBankMask(i)
+    b.io.write.req.bits.setIdx  := t1_setIdx
+    b.io.write.req.bits.wayMask := t1_wayMask
+    b.io.write.req.bits.entry   := t1_entry
+  }
+
+  // update replacer
+  replacer.io.trainTouch.valid       := t1_valid
+  replacer.io.trainTouch.bits.setIdx := getReplacerSetIndex(t1_startVAddr)
+
+  /* *** multi-hit detection & flush *** */
+  private val s2_multiHitMask = detectMultiHit(s2_hitMask, VecInit(s2_rawEntries.map(_.position)))
+
+  internalBanks.zipWithIndex.foreach { case (b, i) =>
+    b.io.flush.req.valid        := s2_fire && s2_multiHitMask.orR && s2_internalBankMask(i)
+    b.io.flush.req.bits.setIdx  := s2_setIdx
+    b.io.flush.req.bits.wayMask := s2_multiHitMask
+  }
+
+  XSPerfHistogram("multihit_count", PopCount(s2_multiHitMask), s2_fire, 0, NumWay)
+}
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
new file mode 100644
index 00000000000..bc0200fb44e
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
@@ -0,0 +1,138 @@
+// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+// Copyright (c) 2020-2021 Peng Cheng Laboratory
+//
+// XiangShan is licensed under Mulan PSL v2.
+// You can use this software according to the terms and conditions of the Mulan PSL v2.
+// You may obtain a copy of Mulan PSL v2 at:
+//          https://license.coscl.org.cn/MulanPSL2
+//
+// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+//
+// See the Mulan PSL v2 for more details.
+
+package xiangshan.frontend.bpu.mbtb
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.XSPerfAccumulate
+import utility.sram.SRAMTemplate
+import xiangshan.frontend.bpu.WriteBuffer
+
+class MainBtbInternalBank(
+    alignIdx: Int,
+    bankIdx:  Int
+)(implicit p: Parameters) extends MainBtbModule with Helpers {
+  class MainBtbInternalBankIO extends Bundle {
+    class Read extends Bundle {
+      class Req extends Bundle {
+        val setIdx: UInt = UInt(SetIdxLen.W)
+      }
+      class Resp extends Bundle {
+        val entries: Vec[MainBtbEntry] = Vec(NumWay, new MainBtbEntry)
+      }
+
+      val req:  Valid[Req] = Flipped(Valid(new Req))
+      val resp: Resp       = Output(new Resp)
+    }
+
+    class Write extends Bundle {
+      class Req extends Bundle {
+        val setIdx:  UInt         = UInt(SetIdxLen.W)
+        val wayMask: UInt         = UInt(NumWay.W)
+        val entry:   MainBtbEntry = new MainBtbEntry
+      }
+
+      val req: Valid[Req] = Flipped(Valid(new Req))
+    }
+
+    // flush interface for multi-hit
+    class Flush extends Bundle {
+      class Req extends Bundle {
+        val setIdx:  UInt = UInt(SetIdxLen.W)
+        val wayMask: UInt = UInt(NumWay.W)
+      }
+
+      val req: Valid[Req] = Flipped(Valid(new Req))
+    }
+
+    val resetDone: Bool = Output(Bool())
+
+    val read:  Read  = new Read
+    val write: Write = new Write
+    val flush: Flush = new Flush
+  }
+
+  val io: MainBtbInternalBankIO = IO(new MainBtbInternalBankIO)
+
+  // alias
+  private val r     = io.read
+  private val w     = io.write
+  private val flush = io.flush
+
+  private val ways = Seq.tabulate(NumWay) { wayIdx =>
+    Module(
+      new SRAMTemplate(
+        new MainBtbEntry,
+        set = NumSets,
+        way = 1, // Not using way in the template, preparing for future skewed assoc
+        singlePort = true,
+        shouldReset = true,
+        holdRead = true,
+        withClockGate = true,
+        hasMbist = hasMbist,
+        hasSramCtl = hasSramCtl
+      )
+    ).suggestName(s"mbtb_sram_align${alignIdx}_bank${bankIdx}_way${wayIdx}")
+  }
+
+  private val writeBuffer = Module(new WriteBuffer(new MainBtbSramWriteReq, WriteBufferSize, NumWay))
+
+  private val resetDone = RegInit(false.B)
+  when(ways.map(_.io.r.req.ready).reduce(_ && _)) {
+    resetDone := true.B
+  }
+  io.resetDone := resetDone
+
+  // sram -> io
+  ways.foreach { way =>
+    way.io.r.req.valid       := r.req.valid
+    way.io.r.req.bits.setIdx := r.req.bits.setIdx
+  }
+  // magic (0): each sram template has 1 way, so we only read data(0)
+  r.resp.entries := VecInit(ways.map(_.io.r.resp.data(0)))
+
+  // writeBuffer -> sram
+  (ways zip writeBuffer.io.read).foreach { case (way, bufRead) =>
+    way.io.w.req.valid        := bufRead.valid && !way.io.r.req.valid
+    way.io.w.req.bits.data(0) := bufRead.bits.entry
+    way.io.w.req.bits.setIdx  := bufRead.bits.setIdx
+    bufRead.ready             := way.io.w.req.ready && !way.io.r.req.valid
+  }
+
+  // io -> writeBuffer
+  writeBuffer.io.write.zipWithIndex.foreach { case (bufWrite, i) =>
+    val writeValid = w.req.valid && w.req.bits.wayMask(i)
+    val flushValid = flush.req.valid && flush.req.bits.wayMask(i)
+    bufWrite.valid := writeValid || flushValid
+    bufWrite.bits.setIdx := Mux(
+      writeValid,
+      w.req.bits.setIdx,
+      flush.req.bits.setIdx
+    )
+    bufWrite.bits.entry := Mux(
+      writeValid,
+      w.req.bits.entry,
+      0.U.asTypeOf(new MainBtbEntry)
+    )
+  }
+
+  XSPerfAccumulate(
+    "multihit_write_conflict",
+    w.req.valid && flush.req.valid && w.req.bits.setIdx === flush.req.bits.setIdx &&
+      (w.req.bits.wayMask & flush.req.bits.wayMask).orR
+  )
+}
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
index 47659ae5b12..ba9e376229c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
@@ -22,86 +22,74 @@ import xiangshan.frontend.bpu.PlruStateGen
 import xiangshan.frontend.bpu.ReplacerState
 
 class MainBtbReplacer(implicit p: Parameters) extends MainBtbModule {
-  val io: ReplacerIO = IO(new ReplacerIO)
+  class MainBtbReplacerIO extends Bundle {
+    class PredictTouch extends Bundle {
+      val setIdx:  UInt = UInt(SetIdxLen.W)
+      val wayMask: UInt = UInt(NumWay.W)
+    }
 
-  private val statesBanks = Seq.tabulate(NumAlignBanks)(alignIdx => Module(new ReplacerState(NumSets, NumWay)))
+    class TrainTouch extends Bundle {
+      val setIdx: UInt = UInt(SetIdxLen.W)
+    }
 
-  private val predictSetIndexVec = io.predictionSetIndxVec
-  private val predictStateEntries: Vec[UInt] =
-    WireInit(VecInit(Seq.fill(NumAlignBanks)(0.U((NumWay - 1).W))))
-  predictSetIndexVec zip statesBanks zip predictStateEntries foreach { case ((setIdx, states), state) =>
-    states.io.predictReadSetIdx := setIdx
-    state                       := states.io.predictReadState
-  }
-  require(
-    predictSetIndexVec(0).getWidth == SetIdxLen,
-    s"S2 stage set index width mismatch: ${predictSetIndexVec(0).getWidth} != $SetIdxLen"
-  )
-  require(
-    predictSetIndexVec.length == NumAlignBanks,
-    s"predictSetIndexVec width mismatch: ${predictSetIndexVec.length} != $NumAlignBanks"
-  )
-  private val predictStateTouchs: Vec[Vec[Valid[UInt]]] = io.predictionTouchWays
-  private val predictNextState: Vec[UInt] =
-    WireInit(VecInit(Seq.fill(NumAlignBanks)(0.U((NumWay - 1).W))))
-
-  private val plruStateGen = Seq.tabulate(NumAlignBanks) { alignIdx =>
-    Module(new PlruStateGen(NumWay, accessSize = NumWay))
-  }
-  private val hits = io.predictionHitMask
-  plruStateGen zip predictStateEntries zip predictStateTouchs zip predictNextState zip hits foreach {
-    case ((((gen, state), touchs), nextState), hit) =>
-      gen.io.stateIn   := state
-      gen.io.touchWays := touchs
-      nextState        := Mux(hit, gen.io.nextState, state)
-  }
-  predictSetIndexVec zip predictNextState zip statesBanks zip hits foreach {
-    case (((setIdx, nextState), states), hit) =>
-      states.io.predictWriteValid  := hit
-      states.io.predictWriteSetIdx := setIdx
-      states.io.predictWriteState  := nextState
-  }
+    class Victim extends Bundle {
+      val wayMask: UInt = UInt(NumWay.W)
+    }
 
-  private val trainSetIdx = io.trainSetIndx
-  private val trainValid  = io.trainWriteValid
-  require(
-    trainSetIdx.getWidth == SetIdxLen,
-    s"train set index width mismatch: ${trainSetIdx.getWidth} != $SetIdxLen"
-  )
-  private val trainAlignBankMask: Vec[Bool] = io.trainAlignBankMask
-  private val trainStateEntries: Vec[UInt] =
-    WireInit(VecInit(Seq.fill(NumAlignBanks)(0.U((NumWay - 1).W))))
-  trainAlignBankMask zip statesBanks zip trainStateEntries foreach { case ((bankEnable, states), state) =>
-    states.io.trainReadSetIdx := trainSetIdx
-    state                     := states.io.trainReadState
+    val predictTouch: Valid[PredictTouch] = Flipped(Valid(new PredictTouch))
+    val trainTouch:   Valid[TrainTouch]   = Flipped(Valid(new TrainTouch))
+    val victim:       Victim              = Output(new Victim)
   }
 
-  private val trainStateEntry: UInt = Mux1H(trainAlignBankMask, trainStateEntries)
-  private val writeReplacerGen = Module(new PlruStateGen(NumWay, accessSize = 1))
-  private val trainTouchWay    = Wire(Valid(UInt(log2Up(NumWay).W)))
-  private val trainVictimWay   = Wire(UInt(log2Up(NumWay).W))
-  private val trainNextState: UInt = WireInit(0.U((NumWay - 1).W))
-  writeReplacerGen.io.stateIn   := trainStateEntry
-  trainVictimWay                := writeReplacerGen.io.replaceWay
-  trainNextState                := Mux(trainValid, writeReplacerGen.io.nextState, trainStateEntry)
-  trainTouchWay.valid           := trainValid
-  trainTouchWay.bits            := trainVictimWay
-  writeReplacerGen.io.touchWays := Seq(trainTouchWay)
-
-  statesBanks zip trainAlignBankMask foreach { case (states, alignBankEnable) =>
-    states.io.trainWriteValid  := trainValid && alignBankEnable
-    states.io.trainWriteSetIdx := trainSetIdx
-    states.io.trainWriteState  := trainNextState
-  }
-  require(
-    trainVictimWay.getWidth == log2Ceil(NumWay),
-    s"Replace way width mismatch: ${trainVictimWay.getWidth} != ${log2Ceil(NumWay)}"
-  )
+  val io: MainBtbReplacerIO = IO(new MainBtbReplacerIO)
+
+  private val stateBank       = Module(new ReplacerState(NumSets, NumWay))
+  private val predictStateGen = Module(new PlruStateGen(NumWay, accessSize = NumWay))
+  private val trainStateGen   = Module(new PlruStateGen(NumWay, accessSize = 1))
+
+  /* *** predict *** */
+  // read current state
+  stateBank.io.predictReadSetIdx := io.predictTouch.bits.setIdx
+  private val predictState = stateBank.io.predictReadState
+
+  // compose touch way vec
+  private val predictTouchWay = VecInit((0 until NumWay).map { i =>
+    val wayValid = Wire(Valid(UInt(log2Up(NumWay).W)))
+    wayValid.valid := io.predictTouch.valid && io.predictTouch.bits.wayMask(i)
+    wayValid.bits  := i.U
+    wayValid
+  })
+
+  // generate next state
+  predictStateGen.io.stateIn   := predictState
+  predictStateGen.io.touchWays := predictTouchWay
+  private val predictNextState = Mux(io.predictTouch.valid, predictStateGen.io.nextState, predictState)
+
+  // write back next state
+  stateBank.io.predictWriteValid  := io.predictTouch.valid
+  stateBank.io.predictWriteSetIdx := io.predictTouch.bits.setIdx
+  stateBank.io.predictWriteState  := predictNextState
+
+  /* *** train *** */
+  // read current state
+  stateBank.io.trainReadSetIdx := io.trainTouch.bits.setIdx
+  private val trainState = stateBank.io.trainReadState
+
+  // compose touch way vec
+  private val trainTouchWay = Wire(Valid(UInt(log2Up(NumWay).W)))
+  trainTouchWay.valid := io.trainTouch.valid
+  trainTouchWay.bits  := trainStateGen.io.replaceWay
+
+  // generate next state
+  trainStateGen.io.stateIn   := trainState
+  trainStateGen.io.touchWays := VecInit(Seq(trainTouchWay))
+  private val trainNextState = Mux(io.trainTouch.valid, trainStateGen.io.nextState, trainState)
 
-  io.victimWayIdx := trainVictimWay
-  require(
-    io.victimWayIdx.getWidth == log2Up(NumWay),
-    s"Write way mask width mismatch: ${io.victimWayIdx.getWidth} != $log2Up(NumWay)"
-  )
+  // write back next state
+  stateBank.io.trainWriteValid  := io.trainTouch.valid
+  stateBank.io.trainWriteSetIdx := io.trainTouch.bits.setIdx
+  stateBank.io.trainWriteState  := trainNextState
 
+  /* *** victim *** */
+  io.victim.wayMask := UIntToOH(trainStateGen.io.replaceWay)
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Parameters.scala
index 5294bcd502d..32b8a423b5f 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Parameters.scala
@@ -48,8 +48,9 @@ trait HasMainBtbParameters extends HasBpuParameters {
   def TargetWidth:        Int = mbtbParameters.TargetWidth
   def SetIdxLen:          Int = log2Ceil(NumSets)
   def InternalBankIdxLen: Int = log2Ceil(NumInternalBanks)
+  def AlignBankIdxLen:    Int = log2Ceil(NumAlignBanks)
   def WriteBufferSize:    Int = mbtbParameters.WriteBufferSize
 
   // Used in any aligned-addr-indexed predictor, indicates the position relative to the aligned start addr
-  def CfiAlignedPositionWidth: Int = CfiPositionWidth - log2Ceil(NumAlignBanks)
+  def CfiAlignedPositionWidth: Int = CfiPositionWidth - AlignBankIdxLen
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
index ce6aec304e6..3bde4b536dc 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
@@ -22,8 +22,8 @@ import scala.math.min
 import utility.XSPerfAccumulate
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
+import xiangshan.frontend.bpu.BtbInfo
 import xiangshan.frontend.bpu.FoldedHistoryInfo
-import xiangshan.frontend.bpu.mbtb.MainBtbResult
 import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories
 
 /**
@@ -32,7 +32,7 @@ import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories
 class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with Helpers {
 
   class ScIO(implicit p: Parameters) extends BasePredictorIO with HasScParameters {
-    val mbtbResult:          MainBtbResult         = Input(new MainBtbResult)
+    val mbtbResult:          Vec[Valid[BtbInfo]]   = Input(Vec(NumBtbResultEntries, Valid(new BtbInfo)))
     val foldedPathHist:      PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val trainFoldedPathHist: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val takenMask:           Vec[Bool]             = Output(Vec(NumBtbResultEntries, Bool()))
@@ -94,9 +94,9 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   private val s2_resp       = VecInit(s1_resp.map(entries => VecInit(entries.map(RegEnable(_, s1_fire)))))
   private val s2_pathPercsum: Vec[Vec[SInt]] = VecInit(s1_pathPercsum.map(ps => VecInit(ps.map(RegEnable(_, s1_fire)))))
 
-  private val s2_mbtbHitMask    = io.mbtbResult.hitMask
-  private val s2_mbtbPositions  = io.mbtbResult.positions
-  private val s2_mbtbAttributes = io.mbtbResult.attributes
+  private val s2_mbtbHitMask    = VecInit(io.mbtbResult.map(_.valid))
+  private val s2_mbtbPositions  = VecInit(io.mbtbResult.map(_.bits.cfiPosition))
+  private val s2_mbtbAttributes = VecInit(io.mbtbResult.map(_.bits.attribute))
   private val s2_totalPercsum: Vec[SInt] = WireInit(VecInit.fill(NumWays)(0.S(ctrWidth.W)))
   private val s2_hitMask:      Vec[Bool] = WireInit(VecInit.fill(NumWays)(false.B))
   require(NumWays == s2_mbtbHitMask.length, s"NumWays $NumWays != s2_mbtbHitMask.length ${s2_mbtbHitMask.length}")
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index eea73982442..41b80bc6a4c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -23,9 +23,9 @@ import scala.math.min
 import utility.XSPerfAccumulate
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
+import xiangshan.frontend.bpu.BtbInfo
 import xiangshan.frontend.bpu.FoldedHistoryInfo
 import xiangshan.frontend.bpu.SaturateCounter
-import xiangshan.frontend.bpu.mbtb.MainBtbResult
 import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories
 
 /**
@@ -33,7 +33,7 @@ import xiangshan.frontend.bpu.phr.PhrAllFoldedHistories
  */
 class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters with Helpers {
   class TageIO(implicit p: Parameters) extends BasePredictorIO {
-    val mbtbResult:             MainBtbResult         = Input(new MainBtbResult)
+    val mbtbResult:             Vec[Valid[BtbInfo]]   = Input(Vec(NumBtbResultEntries, Valid(new BtbInfo)))
     val foldedPathHist:         PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val foldedPathHistForTrain: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val condTakenMask:          Vec[Bool]             = Output(Vec(NumBtbResultEntries, Bool()))
@@ -121,9 +121,9 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
   private val s2_tempTag = s1_tempTag.map(RegEnable(_, s1_fire))
 
-  private val s2_mbtbHitMask    = io.mbtbResult.hitMask
-  private val s2_mbtbPositions  = io.mbtbResult.positions
-  private val s2_mbtbAttributes = io.mbtbResult.attributes
+  private val s2_mbtbHitMask    = VecInit(io.mbtbResult.map(_.valid))
+  private val s2_mbtbPositions  = VecInit(io.mbtbResult.map(_.bits.cfiPosition))
+  private val s2_mbtbAttributes = VecInit(io.mbtbResult.map(_.bits.attribute))
 
   private val s2_mbtbHitCondMask = s2_mbtbHitMask.zip(s2_mbtbAttributes).map {
     case (hit, attribute) => hit && attribute.isConditional
```
