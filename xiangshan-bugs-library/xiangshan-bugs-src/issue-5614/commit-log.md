# Commit Log
- Issue: #5614
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5614
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5614
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5614
- Changed files: 6
- Additions: 83
- Deletions: 56

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index b0a82d64fb3..b94212f6064 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -220,6 +220,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   ras.io.specIn.bits.cfiPosition := s3_prediction.cfiPosition
 
   tage.io.fromMainBtb.result             := mbtb.io.result
+  tage.io.fromMainBtb.s1_positions       := mbtb.io.s1_positions
   tage.io.fromPhr.foldedPathHist         := phr.io.s0_foldedPhr
   tage.io.fromPhr.foldedPathHistForTrain := phr.io.trainFoldedPhr
   tage.io.debug_trainValid               := io.fromFtq.train.valid // for perf counters
@@ -262,7 +263,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   // s0_stall should be exclusive with any other PC source
   s0_stall := !(s1_valid || s3_override || redirect.valid)
 
-  // * *** s1 prediction selection *** */
+  /* *** s1 prediction selection *** */
   private val s1_btbPrediction = VecInit(ubtb.io.prediction) ++ abtb.io.prediction
   private val s1_utageHitMask = VecInit(s1_btbPrediction.map { pred =>
     pred.valid && utage.io.prediction.valid && utage.io.prediction.bits.cfiPosition === pred.bits.cfiPosition
@@ -316,41 +317,35 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   s1_utageMeta := utage.io.meta.bits
   s1_utageMeta.debug_useMicroTage.foreach(_ := s1_utageHitMask.reduce(_ || _))
 
-  private val s2_mbtbResult  = mbtb.io.result
-  private val s2_condHitMask = VecInit(s2_mbtbResult.map(e => e.valid && e.bits.attribute.isConditional))
-  private val s2_scUsed      = sc.io.scUsed
-  private val s2_scTakenMask = sc.io.scTakenMask
-  private val s2_scFlipTage = VecInit((tage.io.prediction zip s2_scUsed zip s2_scTakenMask).map {
-    case ((p, useSc), scTaken) =>
-      useSc && (p.providerPred =/= scTaken)
-  }) // for bpSource counter
-  private val s2_condTakenMask = VecInit((s2_mbtbResult zip tage.io.prediction zip s2_scUsed zip s2_scTakenMask).map {
-    case (((e, p), useSc), scTaken) =>
-      e.valid && e.bits.attribute.isConditional &&
-      MuxCase(
-        e.bits.taken,
-        Seq(
-          useSc         -> scTaken,
-          p.useProvider -> p.providerPred,
-          p.hasAlt      -> p.altPred
+  /* *** s3 prediction selection *** */
+  private val s3_mbtbResult     = RegEnable(mbtb.io.result, s2_fire)
+  private val s3_tagePrediction = RegEnable(tage.io.prediction, s2_fire)
+  private val s3_scUsed         = RegEnable(sc.io.scUsed, s2_fire)
+  private val s3_scTakenMask    = RegEnable(sc.io.scTakenMask, s2_fire)
+
+  private val s3_takenMask = VecInit(s3_mbtbResult.zipWithIndex.map { case (entry, i) =>
+    val tagePred = s3_tagePrediction(i)
+    val useSc    = s3_scUsed(i)
+    val scTaken  = s3_scTakenMask(i)
+
+    entry.valid && (
+      entry.bits.attribute.isDirect ||
+        entry.bits.attribute.isIndirect ||
+        entry.bits.attribute.isConditional &&
+        MuxCase(
+          entry.bits.taken, // default: base table
+          Seq(
+            useSc                -> scTaken,
+            tagePred.useProvider -> tagePred.providerPred,
+            tagePred.hasAlt      -> tagePred.altPred
+          )
         )
-      )
-  })
-
-  private val s2_jumpMask = VecInit(s2_mbtbResult.map { e =>
-    e.valid && (e.bits.attribute.isDirect || e.bits.attribute.isIndirect)
+    )
   })
-  private val s2_takenMask = VecInit(s2_condTakenMask.zip(s2_jumpMask).map { case (a, b) => a || b })
-  private val s2_taken     = s2_takenMask.reduce(_ || _)
+  private val s3_taken = s3_takenMask.reduce(_ || _)
 
-  private val s2_compareMatrix      = CompareMatrix(VecInit(s2_mbtbResult.map(_.bits.cfiPosition)))
-  private val s2_firstTakenBranchOH = s2_compareMatrix.getLeastElementOH(s2_takenMask)
-
-  /* *** s3 prediction selection *** */
-  private val s3_taken              = RegEnable(s2_taken, s2_fire)
-  private val s3_condHitMask        = RegEnable(s2_condHitMask, s2_fire)
-  private val s3_mbtbResult         = RegEnable(s2_mbtbResult, s2_fire)
-  private val s3_firstTakenBranchOH = RegEnable(s2_firstTakenBranchOH, s2_fire)
+  private val s3_compareMatrix      = CompareMatrix(VecInit(s3_mbtbResult.map(_.bits.cfiPosition)))
+  private val s3_firstTakenBranchOH = s3_compareMatrix.getLeastElementOH(s3_takenMask)
   private val s3_firstTakenBranch   = Mux1H(s3_firstTakenBranchOH, s3_mbtbResult)
   private val s3_useRas             = s3_firstTakenBranch.bits.attribute.isReturn
   private val s3_useIttage          = s3_firstTakenBranch.bits.attribute.needIttage && ittage.io.prediction.hit
@@ -358,12 +353,14 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   private val s2_fallThroughPrediction = RegEnable(fallThrough.io.prediction, s1_fire)
   private val s3_fallThroughPrediction = RegEnable(s2_fallThroughPrediction, s2_fire)
 
-  private val s3_takenMask = RegEnable(s2_takenMask, s2_fire)
+  // used for mainBTB replacer
   mbtb.io.s3_takenMask := s3_takenMask
 
-  s3_prediction.taken       := s3_taken
-  s3_prediction.cfiPosition := Mux(s3_taken, s3_firstTakenBranch.bits.cfiPosition, s3_fallThroughPrediction.cfiPosition)
-  s3_prediction.attribute   := Mux(s3_taken, s3_firstTakenBranch.bits.attribute, s3_fallThroughPrediction.attribute)
+  // used for ghr
+  private val s3_condHitMask = VecInit(s3_mbtbResult.map(e => e.valid && e.bits.attribute.isConditional))
+
+  s3_prediction       := Mux(s3_taken, s3_firstTakenBranch.bits, s3_fallThroughPrediction)
+  s3_prediction.taken := s3_taken
   s3_prediction.target :=
     MuxCase(
       s3_fallThroughPrediction.target,
@@ -505,8 +502,25 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
 
   /* *** Debug Meta *** */
   // used for performance counters
-  private val s3_condTakenMask      = RegEnable(s2_condTakenMask, s2_fire)
-  private val s3_scFlipTage         = RegEnable(s2_scFlipTage, s2_fire)
+  private val s3_scFlipTage = VecInit((s3_tagePrediction zip s3_scUsed zip s3_scTakenMask).map {
+    case ((tagePred, useSc), scTaken) =>
+      useSc && (tagePred.providerPred =/= scTaken)
+  }) // for bpSource counter
+  private val s3_condTakenMask = VecInit(s3_mbtbResult.zipWithIndex.map { case (entry, i) =>
+    val tagePred = s3_tagePrediction(i)
+    val useSc    = s3_scUsed(i)
+    val scTaken  = s3_scTakenMask(i)
+
+    entry.valid && entry.bits.attribute.isConditional &&
+    MuxCase(
+      entry.bits.taken, // default: base table
+      Seq(
+        useSc                -> scTaken,
+        tagePred.useProvider -> tagePred.providerPred,
+        tagePred.hasAlt      -> tagePred.altPred
+      )
+    )
+  })
   private val s3_firstTakenPosition = Mux1H(s3_firstTakenBranchOH, VecInit(s3_mbtbResult.map(_.bits.cfiPosition)))
   private val s3_firstTakenBlameSc  = Mux1H(s3_firstTakenBranchOH, s3_scFlipTage)
   // if the branch before the first take has a flipped and !s3_taken && flipped, then blamed on sc
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
index b0eb8cc6fc5..e939069a35d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
@@ -32,6 +32,9 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
     val result: Vec[Valid[Prediction]] = Output(Vec(NumBtbResultEntries, Valid(new Prediction)))
     val meta:   MainBtbMeta            = Output(new MainBtbMeta)
 
+    // timing optimization: send positions earlier to TAGE
+    val s1_positions: Vec[UInt] = Output(Vec(NumBtbResultEntries, UInt(CfiPositionWidth.W)))
+
     // final s3_takenMask (mbtb + tage + sc), used to touch replacer accurately
     val s3_takenMask: Vec[Bool] = Input(Vec(NumBtbResultEntries, Bool()))
   }
@@ -95,6 +98,8 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
    */
   s1_fire := io.stageCtrl.s1_fire && io.enable
 
+  io.s1_positions := VecInit(alignBanks.flatMap(_.io.read.s1_positions))
+
   /* *** s2 ***
    * receive read response from alignBanks
    * send out prediction result and meta info
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
index fabd3fdfda3..606c4a7410b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
@@ -46,6 +46,8 @@ class MainBtbAlignBank(
       val req: Req = Input(new Req)
 
       val resp: Resp = Output(new Resp)
+
+      val s1_positions: Vec[UInt] = Output(Vec(NumWay, UInt(CfiPositionWidth.W)))
     }
 
     class Write extends Bundle {
@@ -135,6 +137,8 @@ class MainBtbAlignBank(
     internalBanks.map(_.io.read.resp.counters)
   )
 
+  io.read.s1_positions := VecInit(s1_rawEntries.map(e => Cat(s1_posHigherBits, e.position)))
+
   /* *** s2 ***
    * check entries hit
    * filter-out unneeded entries
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
index 60aa389c61f..69f47c27d55 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
@@ -70,7 +70,8 @@ class PhrToTageIO(implicit p: Parameters) extends TageBundle {
 }
 
 class MainBtbToTageIO(implicit p: Parameters) extends TageBundle {
-  val result: Vec[Valid[Prediction]] = Input(Vec(NumBtbResultEntries, Valid(new Prediction)))
+  val result:       Vec[Valid[Prediction]] = Input(Vec(NumBtbResultEntries, Valid(new Prediction)))
+  val s1_positions: Vec[UInt]              = Input(Vec(NumBtbResultEntries, UInt(CfiPositionWidth.W)))
 }
 
 class TageToScIO(implicit p: Parameters) extends TageBundle {
@@ -119,8 +120,8 @@ class TageMeta(implicit p: Parameters) extends TageBundle {
 }
 
 class TageFoldedHist(implicit p: Parameters, info: TageTableInfo) extends TageBundle {
-  val forIdx: UInt = UInt(SetIdxWidth.W)
-  val forTag: UInt = UInt(TagWidth.W)
+  val forIdx: UInt      = UInt(SetIdxWidth.W)
+  val forTag: Vec[UInt] = Vec(2, UInt(TagWidth.W))
 }
 
 class PredictTagMatchResult(implicit p: Parameters) extends TageBundle {
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala
index 2caec101058..7fb3bdb947a 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala
@@ -19,7 +19,6 @@ import chisel3._
 import chisel3.util._
 import utils.AddrField
 import xiangshan.frontend.PrunedAddr
-import xiangshan.frontend.bpu.SaturateCounter
 import xiangshan.frontend.bpu.TageTableInfo
 import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 
@@ -30,8 +29,9 @@ trait TopHelper extends HasTageParameters {
         allFoldedPathHist.getHistWithInfo(histInfo).foldedHist
       }
       val foldedHist = Wire(new TageFoldedHist)
-      foldedHist.forIdx := tageFoldedHist.head
-      foldedHist.forTag := tageFoldedHist(1) ^ Cat(tageFoldedHist(2), 0.U(1.W))
+      foldedHist.forIdx    := tageFoldedHist.head
+      foldedHist.forTag(0) := tageFoldedHist(1)
+      foldedHist.forTag(1) := Cat(tageFoldedHist(2), 0.U(1.W))
       foldedHist
     })
 
@@ -64,6 +64,9 @@ trait TableHelper extends TopHelper { // extends TopHelper for getBankIndex
   def getSetIndex(pc: PrunedAddr, hist: UInt): UInt =
     addrFields.extract("setIdx", pc) ^ hist
 
-  def getRawTag(pc: PrunedAddr, hist: UInt): UInt =
-    addrFields.extract("tag", pc) ^ hist
+  def getRawTag(pc: PrunedAddr, hist: Vec[UInt]): UInt =
+    addrFields.extract("tag", pc) ^ hist(0) ^ hist(1)
+
+  def getTag(pc: PrunedAddr, hist: Vec[UInt], position: UInt): UInt =
+    addrFields.extract("tag", pc) ^ hist(0) ^ hist(1) ^ position
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 4bb18b60b47..b6d5b64071f 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -26,8 +26,6 @@ import utility.XSPerfHistogram
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
 import xiangshan.frontend.bpu.HalfAlignHelper
-import xiangshan.frontend.bpu.SaturateCounter
-import xiangshan.frontend.bpu.SaturateCounterInit
 import xiangshan.frontend.bpu.TageTableInfo
 
 /**
@@ -86,17 +84,19 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
   /* --------------------------------------------------------------------------------------------------------------
      predict pipeline stage 1
-     - get read data from tables
-     - compute temp tag
+     - get read resp from tables
+     - compute tag
      -------------------------------------------------------------------------------------------------------------- */
 
   private val s1_fire       = io.stageCtrl.s1_fire
   private val s1_startPc    = RegEnable(s0_startPc, s0_fire)
   private val s1_foldedHist = RegEnable(s0_foldedHist, s0_fire)
 
-  // A tag without branch position, position will be hashed into after BTB result
-  private val s1_rawTag = VecInit((tables zip s1_foldedHist).map { case (table, hist) =>
-    table.getRawTag(s1_startPc, hist.forTag)
+  // Vec[NumBtbResultEntries][NumTables]
+  private val s1_tag = VecInit(io.fromMainBtb.s1_positions.map { position =>
+    VecInit((tables zip s1_foldedHist).map { case (table, hist) =>
+      table.getTag(s1_startPc, hist.forTag, position)
+    })
   })
 
   private val s1_readResp = DataHoldBypass(VecInit(tables.map(_.io.predictReadResp)), RegNext(s0_fire))
@@ -109,7 +109,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
   private val s2_fire     = io.stageCtrl.s2_fire
   private val s2_startPc  = RegEnable(s1_startPc, s1_fire)
-  private val s2_rawTag   = RegEnable(s1_rawTag, s1_fire)
+  private val s2_tag      = RegEnable(s1_tag, s1_fire)
   private val s2_readResp = RegEnable(s1_readResp, s1_fire)
 
   private val s2_branches = io.fromMainBtb.result
@@ -122,7 +122,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
     // compare tags of each branch with all tables
     val allTableTagMatchResults = s2_readResp.zipWithIndex.map { case (tableReadResp, tableIdx) =>
-      val tag          = s2_rawTag(tableIdx) ^ position
+      val tag          = s2_tag(i)(tableIdx)
       val hitWayMask   = tableReadResp.entries.map(entry => entry.valid && entry.tag === tag)
       val hitWayMaskOH = PriorityEncoderOH(hitWayMask)
```
