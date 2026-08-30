# Commit Log
- Issue: #5399
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5399
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5399
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5399
- Changed files: 13
- Additions: 256
- Deletions: 230

## Files
- `src/main/scala/xiangshan/frontend/bpu/Abstracts.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala`
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala`
- `src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`
- `src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala`
- `src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Abstracts.scala b/src/main/scala/xiangshan/frontend/bpu/Abstracts.scala
index a3c6e6264f5..3a497661498 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Abstracts.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Abstracts.scala
@@ -38,7 +38,8 @@ abstract class BasePredictorIO(implicit p: Parameters) extends BpuBundle {
   // predict request
   val startPc: PrunedAddr = Input(PrunedAddr(VAddrBits))
   // resolve train
-  val train: DecoupledIO[BpuTrain] = Flipped(Decoupled(new BpuTrain))
+  val trainReady: Bool     = Output(Bool())
+  val train:      BpuTrain = Input(new BpuTrain)
   // fast train for s1 predictors
   val fastTrain: Option[Valid[BpuFastTrain]] = None
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 841f0a99446..17de02a1675 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -166,6 +166,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   stageCtrl.s1_fire := s1_fire
   stageCtrl.s2_fire := s2_fire
   stageCtrl.s3_fire := s3_fire
+  stageCtrl.t0_fire := io.fromFtq.train.fire
 
   private val t0_compareMatrix = CompareMatrix(VecInit(io.fromFtq.train.bits.branches.map(_.bits.cfiPosition)))
   // mark all branches after the first mispredict as invalid
@@ -176,13 +177,11 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     VecInit(io.fromFtq.train.bits.branches.map(b => b.valid && b.bits.mispredict))
   )
 
-  private val train = Wire(Decoupled(new BpuTrain))
-  train.valid := io.fromFtq.train.valid
-  train.bits  := io.fromFtq.train.bits
-  train.bits.branches.zipWithIndex.foreach { case (b, i) =>
+  private val train = Wire(new BpuTrain)
+  train := io.fromFtq.train.bits
+  train.branches.zipWithIndex.foreach { case (b, i) =>
     b.valid := io.fromFtq.train.bits.branches(i).valid && t0_firstMispredictMask(i)
   }
-  io.fromFtq.train.ready := train.ready
 
   private val fastTrain = Wire(Valid(new BpuFastTrain))
   fastTrain.valid                := s3_valid
@@ -194,13 +193,12 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
 
   predictors.foreach { p =>
     // TODO: duplicate pc and fire to solve high fan-out issue
-    p.io.startPc     := s0_startPc
-    p.io.stageCtrl   := stageCtrl
-    p.io.train.valid := train.valid
-    p.io.train.bits  := train.bits
+    p.io.startPc   := s0_startPc
+    p.io.stageCtrl := stageCtrl
+    p.io.train     := train
     p.io.fastTrain.foreach(_ := fastTrain) // fastTrain is an Option[Valid[BpuFastTrain]]
   }
-  train.ready := predictors.map(_.io.train.ready).reduce(_ && _)
+  io.fromFtq.train.ready := predictors.map(_.io.trainReady).reduce(_ && _)
 
   /* *** predictor specific inputs *** */
   // FIXME: should use s3_prediction to train ubtb
@@ -234,6 +232,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   tage.io.mbtbResult             := mbtb.io.result
   tage.io.foldedPathHist         := phr.io.s0_foldedPhr
   tage.io.foldedPathHistForTrain := phr.io.trainFoldedPhr
+  tage.io.debug_trainValid       := io.fromFtq.train.valid // for perf counters
 
   // ittage
   ittage.io.s1_foldedPhr   := phr.io.s1_foldedPhr
@@ -461,8 +460,8 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   phr.io.train.s1_prediction := s1_prediction
   phr.io.train.s1_startPc    := s1_startPc
 
-  phr.io.commit.valid := train.fire
-  phr.io.commit.bits  := train.bits
+  phr.io.commit.valid := io.fromFtq.train.fire
+  phr.io.commit.bits  := train
 
   s0_foldedPhr   := phr.io.s0_foldedPhr
   s1_foldedPhr   := phr.io.s1_foldedPhr
@@ -574,7 +573,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   predictionTrace.perfMeta := s3_perfMeta
 
   private val trainTrace = Wire(new TrainTrace)
-  trainTrace.train := train.bits
+  trainTrace.train := train
 
   predictionTable.log(
     data = predictionTrace,
@@ -585,7 +584,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
 
   trainTable.log(
     data = trainTrace,
-    en = train.fire,
+    en = io.fromFtq.train.fire,
     clock = clock,
     reset = reset
   )
@@ -725,15 +724,22 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   )
 
   /* *** perf train *** */
-  private val t0_mispredictBranch = train.bits.mispredictBranch
-  private val t0_mbtbMeta         = train.bits.meta.mbtb
-  private val t0_branches         = train.bits.branches
+  private val t0_mispredictBranch = train.mispredictBranch
+  private val t0_mbtbMeta         = train.meta.mbtb
+  private val t0_branches         = train.branches
   private val t0_mbtbHit          = t0_mbtbMeta.entries.flatten.map(_.hit(t0_mispredictBranch.bits)).reduce(_ || _)
 
-  XSPerfAccumulate("train", train.fire)
+  XSPerfAccumulate(
+    "train",
+    io.fromFtq.train.fire,
+    Seq(
+      ("total", true.B),
+      ("stall", !io.fromFtq.train.ready)
+    )
+  )
   XSPerfAccumulate(
     "train_branch",
-    train.fire,
+    io.fromFtq.train.fire,
     Seq(
       ("total", true.B, PopCount(t0_branches.map(_.valid))),
       ("direct", true.B, PopCount(t0_branches.map(b => b.valid && b.bits.attribute.isDirect))),
@@ -745,7 +751,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   )
   XSPerfAccumulate(
     "train_mispredict",
-    train.fire && t0_mispredictBranch.valid,
+    io.fromFtq.train.fire && t0_mispredictBranch.valid,
     Seq(
       ("total", true.B),
       ("direct", t0_mispredictBranch.bits.attribute.isDirect),
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
index f86d8015764..7e28f6e2818 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
@@ -329,6 +329,8 @@ class StageCtrl(implicit p: Parameters) extends BpuBundle {
   val s1_fire: Bool = Bool()
   val s2_fire: Bool = Bool()
   val s3_fire: Bool = Bool()
+
+  val t0_fire: Bool = Bool()
 }
 
 // sub predictors -> Bpu top
diff --git a/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala b/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala
index 70b01039921..829032b4df3 100644
--- a/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala
@@ -31,7 +31,7 @@ class FallThroughPredictor(implicit p: Parameters) extends BasePredictor
 
   io.resetDone := true.B
 
-  io.train.ready := true.B
+  io.trainReady := true.B
 
   /* *** predict stage 0 *** */
   private val s0_fire    = io.stageCtrl.s0_fire
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
index 46e85137c09..2a2daf5be18 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
@@ -60,7 +60,7 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
   }
   io.resetDone := resetDone
 
-  io.train.ready := true.B
+  io.trainReady := true.B
 
   private val takenCounter = RegInit(
     VecInit.fill(NumBanks)(
@@ -247,7 +247,7 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
 
   private val t0_train = io.fastTrain.get.bits
 
-  private val t0_valid = io.enable && io.fastTrain.get.valid && t0_train.abtbMeta.valid && io.previousStartPc.valid
+  private val t0_fire = io.enable && io.fastTrain.get.valid && t0_train.abtbMeta.valid && io.previousStartPc.valid
   private val t0_previousStartPc = io.previousStartPc.bits
 
   /* --------------------------------------------------------------------------------------------------------------
@@ -256,9 +256,9 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
      - write a new entry or modify an existing entry if needed
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val t1_valid           = RegNext(t0_valid) & io.enable
-  private val t1_train           = RegEnable(t0_train, t0_valid)
-  private val t1_previousStartPc = RegEnable(t0_previousStartPc, t0_valid)
+  private val t1_fire            = RegNext(t0_fire) & io.enable
+  private val t1_train           = RegEnable(t0_train, t0_fire)
+  private val t1_previousStartPc = RegEnable(t0_previousStartPc, t0_fire)
 
   private val t1_setIdx  = getSetIndex(t1_previousStartPc)
   private val t1_setMask = UIntToOH(t1_setIdx)
@@ -293,7 +293,7 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
           val needReset = bank.io.writeResp.valid && bank.io.writeResp.bits.needResetCtr &&
             setIdx.U === bank.io.writeResp.bits.setIdx && wayIdx.U === bank.io.writeResp.bits.wayIdx
 
-          val updateThisSet = t1_valid && t1_bankMask(bankIdx) && t1_setMask(setIdx)
+          val updateThisSet = t1_fire && t1_bankMask(bankIdx) && t1_setMask(setIdx)
           val needDecrease  = updateThisSet && (!t1_trainTaken || t1_trainTaken && before) && isCond
           val needIncrease  = updateThisSet && t1_trainTaken && equal && isCond
 
@@ -333,13 +333,13 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
 
   // TODO: the prioriority of write?
   banks.zipWithIndex.foreach { case (b, i) =>
-    when(t1_valid && t1_needWriteNewEntry && t1_bankMask(i)) {
+    when(t1_fire && t1_needWriteNewEntry && t1_bankMask(i)) {
       b.io.writeReq.valid             := true.B
       b.io.writeReq.bits.needResetCtr := true.B
       b.io.writeReq.bits.setIdx       := t1_setIdx
       b.io.writeReq.bits.wayIdx       := victimWayIdx(i)
       b.io.writeReq.bits.entry        := t1_writeEntry
-    }.elsewhen(t1_valid && t1_needCorrectTarget && t1_bankMask(i)) {
+    }.elsewhen(t1_fire && t1_needCorrectTarget && t1_bankMask(i)) {
       b.io.writeReq.valid             := true.B
       b.io.writeReq.bits.needResetCtr := false.B
       b.io.writeReq.bits.setIdx       := t1_setIdx
@@ -367,31 +367,31 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
      performance counter
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val perf_directionWrong = t1_valid &&
+  private val perf_directionWrong = t1_fire &&
     ((!t1_predictTaken && t1_trainTaken) || (t1_predictTaken && !t1_trainTaken))
 
-  private val perf_missWrong = t1_valid && !t1_predictTaken && t1_trainTaken && !t1_hitTakenBranch
+  private val perf_missWrong = t1_fire && !t1_predictTaken && t1_trainTaken && !t1_hitTakenBranch
 
-  private val perf_takenPositionWrong = t1_valid && t1_predictTaken && t1_trainTaken &&
+  private val perf_takenPositionWrong = t1_fire && t1_predictTaken && t1_trainTaken &&
     t1_predictPosition =/= t1_trainPosition
 
-  private val perf_targetWrong = t1_valid && t1_predictTaken && t1_trainTaken &&
+  private val perf_targetWrong = t1_fire && t1_predictTaken && t1_trainTaken &&
     t1_predictAttribute === t1_trainAttribute && t1_predictPosition === t1_trainPosition &&
     t1_predictTargetLowerBits =/= t1_trainTargetLowerBits
 
-  private val perf_predictNotTakenRight = t1_valid && !t1_predictTaken && !t1_trainTaken
+  private val perf_predictNotTakenRight = t1_fire && !t1_predictTaken && !t1_trainTaken
 
-  private val perf_predictTakenRight = t1_valid && t1_predictTaken && t1_trainTaken &&
+  private val perf_predictTakenRight = t1_fire && t1_predictTaken && t1_trainTaken &&
     t1_predictAttribute === t1_trainAttribute && t1_predictPosition === t1_trainPosition &&
     t1_predictTargetLowerBits === t1_trainTargetLowerBits
 
-  private val perf_condTakenRight = t1_valid && t1_predictTaken && t1_trainTaken &&
+  private val perf_condTakenRight = t1_fire && t1_predictTaken && t1_trainTaken &&
     t1_predictAttribute.isConditional && t1_trainAttribute.isConditional && t1_predictPosition === t1_trainPosition
 
-  private val perf_directRight = t1_valid && t1_predictTaken && t1_trainTaken &&
+  private val perf_directRight = t1_fire && t1_predictTaken && t1_trainTaken &&
     t1_predictAttribute.isDirect && t1_trainAttribute.isDirect && t1_predictPosition === t1_trainPosition
 
-  private val perf_indirectRight = t1_valid && t1_predictTaken && t1_trainTaken &&
+  private val perf_indirectRight = t1_fire && t1_predictTaken && t1_trainTaken &&
     t1_predictAttribute.isIndirect && t1_trainAttribute.isIndirect && t1_predictPosition === t1_trainPosition &&
     t1_predictTargetLowerBits === t1_trainTargetLowerBits
 
@@ -405,20 +405,20 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
   XSPerfAccumulate("predict_multi_hit", s2_fire && s2_multiHit)
 
   XSPerfAccumulate("train_req_num", io.fastTrain.get.valid)
-  XSPerfAccumulate("train_num", t1_valid)
-  XSPerfAccumulate("train_hit_path", t1_valid && t1_meta.hitMask.reduce(_ || _))
-  XSPerfAccumulate("train_hit_taken_branch", t1_valid && t1_hitTakenBranch)
-  XSPerfAccumulate("train_predict_taken", t1_valid && t1_predictTaken)
-  XSPerfAccumulate("train_predict_not_taken", t1_valid && t1_meta.hitMask.reduce(_ || _) && !t1_predictTaken)
-  XSPerfAccumulate("train_actual_taken", t1_valid && t1_trainTaken)
-  XSPerfAccumulate("train_actual_not_taken", t1_valid && !t1_trainTaken)
-
-  XSPerfAccumulate("total_write", t1_valid && (t1_needWriteNewEntry || t1_needCorrectTarget) || s2_valid && s2_multiHit)
-  XSPerfAccumulate("train_write_new_entry", t1_valid && t1_needWriteNewEntry)
-  XSPerfAccumulate("train_correct_target", t1_valid && t1_needCorrectTarget)
+  XSPerfAccumulate("train_num", t1_fire)
+  XSPerfAccumulate("train_hit_path", t1_fire && t1_meta.hitMask.reduce(_ || _))
+  XSPerfAccumulate("train_hit_taken_branch", t1_fire && t1_hitTakenBranch)
+  XSPerfAccumulate("train_predict_taken", t1_fire && t1_predictTaken)
+  XSPerfAccumulate("train_predict_not_taken", t1_fire && t1_meta.hitMask.reduce(_ || _) && !t1_predictTaken)
+  XSPerfAccumulate("train_actual_taken", t1_fire && t1_trainTaken)
+  XSPerfAccumulate("train_actual_not_taken", t1_fire && !t1_trainTaken)
+
+  XSPerfAccumulate("total_write", t1_fire && (t1_needWriteNewEntry || t1_needCorrectTarget) || s2_valid && s2_multiHit)
+  XSPerfAccumulate("train_write_new_entry", t1_fire && t1_needWriteNewEntry)
+  XSPerfAccumulate("train_correct_target", t1_fire && t1_needCorrectTarget)
   XSPerfAccumulate(
     "train_write_conflict",
-    t1_valid && (t1_needWriteNewEntry || t1_needCorrectTarget) && s2_valid && s2_multiHit
+    t1_fire && (t1_needWriteNewEntry || t1_needCorrectTarget) && s2_valid && s2_multiHit
   )
 
   XSPerfAccumulate("train_direction_wrong", perf_directionWrong)
diff --git a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
index 43527b2612a..a42cb8d2a64 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
@@ -55,7 +55,7 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
 
   io.resetDone := true.B // FIXME: sram read ready
 
-  io.train.ready := true.B
+  io.trainReady := true.B
 
   private val s0_startPc = io.startPc
   private val s0_fire    = io.stageCtrl.s0_fire && io.enable
@@ -112,41 +112,43 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
   private val ittageMeta = WireDefault(0.U.asTypeOf(new IttageMeta))
   io.meta := ittageMeta
 
+  private val t0_fire = io.enable && io.stageCtrl.t0_fire
+
   private val t1_train = Wire(new BpuTrain)
-  t1_train := RegEnable(io.train.bits, 0.U.asTypeOf(new BpuTrain), io.train.fire)
+  t1_train := RegEnable(io.train, 0.U.asTypeOf(new BpuTrain), t0_fire)
 
   private val t1_meta = Wire(new IttageMeta)
   t1_train.meta.ittage := t1_meta
 
-  private val t1_trainFoldedPhr = RegEnable(io.trainFoldedPhr, io.train.fire)
+  private val t1_trainFoldedPhr = RegEnable(io.trainFoldedPhr, t0_fire)
 
   private val updateStartPc   = t1_train.startPc
   private val updateFoldedPhr = t1_trainFoldedPhr
 
   // To improve Clock Gating Efficiency
-  private val t0_meta = io.train.bits.meta.ittage
-  t1_meta := RegEnable(t0_meta, io.train.fire)
+  private val t0_meta = io.train.meta.ittage
+  t1_meta := RegEnable(t0_meta, t0_fire)
   t1_meta.provider.bits := RegEnable(
     t0_meta.provider.bits,
-    io.train.fire && t0_meta.provider.valid
+    t0_fire && t0_meta.provider.valid
   )
   t1_meta.providerTarget := RegEnable(
     t0_meta.providerTarget,
     0.U.asTypeOf(t0_meta.providerTarget),
-    io.train.fire && t0_meta.provider.valid
+    t0_fire && t0_meta.provider.valid
   )
   t1_meta.allocate.bits := RegEnable(
     t0_meta.allocate.bits,
-    io.train.fire && t0_meta.allocate.valid
+    t0_fire && t0_meta.allocate.valid
   )
   t1_meta.altProvider.bits := RegEnable(
     t0_meta.altProvider.bits,
-    io.train.fire && t0_meta.altProvider.valid
+    t0_fire && t0_meta.altProvider.valid
   )
   t1_meta.altProviderTarget := RegEnable(
     t0_meta.altProviderTarget,
     0.U.asTypeOf(t0_meta.altProviderTarget),
-    io.train.fire && t0_meta.provider.valid && t0_meta.altProvider.valid && t0_meta.providerCnt.isSaturateNegative
+    t0_fire && t0_meta.provider.valid && t0_meta.altProvider.valid && t0_meta.providerCnt.isSaturateNegative
   )
 
   // Select the branch needed for training
@@ -161,7 +163,7 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
   val hasTrainBranch: Bool = trainBranchIdxVec.asUInt.orR
 
   // Update condition for ittage
-  private val updateValid = hasTrainBranch && RegNext(io.train.fire, init = false.B)
+  private val updateValid = hasTrainBranch && RegNext(t0_fire, init = false.B)
 
   private val updateMask            = WireInit(0.U.asTypeOf(Vec(NumTables, Bool())))
   private val updateUsefulCntMask   = WireInit(0.U.asTypeOf(Vec(NumTables, Bool())))
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
index 6c66e22d081..0785b4321cf 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
@@ -45,14 +45,15 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
 
   io.resetDone := alignBanks.map(_.io.resetDone).reduce(_ && _)
 
-  io.train.ready := true.B
+  io.trainReady := true.B
 
   private val s0_fire, s1_fire, s2_fire = Wire(Bool())
   alignBanks.foreach { b =>
     b.io.stageCtrl.s0_fire := s0_fire
     b.io.stageCtrl.s1_fire := s1_fire
     b.io.stageCtrl.s2_fire := s2_fire
-    b.io.stageCtrl.s3_fire := false.B
+    b.io.stageCtrl.s3_fire := false.B // we don't have a s3 stage in mainBtb
+    b.io.stageCtrl.t0_fire := false.B // dont care, alignBank is using t1
   }
 
   /* *** s0 ***
@@ -106,14 +107,14 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   /* *** t0 ***
    * receive training data and latch
    */
-  private val t0_valid = io.train.fire && io.enable
-  private val t0_train = io.train.bits
+  private val t0_fire  = io.stageCtrl.t0_fire && io.enable
+  private val t0_train = io.train
 
   /* *** t1 ***
    * calculate write data and write to alignBanks
    */
-  private val t1_valid = RegNext(t0_valid) && io.enable
-  private val t1_train = RegEnable(t0_train, t0_valid)
+  private val t1_fire  = RegNext(t0_fire) && io.enable
+  private val t1_train = RegEnable(t0_train, t0_fire)
 
   private val t1_startPc = t1_train.startPc
   private val t1_rotator = VecRotate(getAlignBankIndex(t1_startPc))
@@ -127,7 +128,7 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   private val t1_writeAlignBankMask = t1_rotator.rotate(VecInit(UIntToOH(t1_writeAlignBankIdx).asBools))
 
   alignBanks.zipWithIndex.foreach { case (b, i) =>
-    b.io.write.req.valid         := t1_valid && t1_writeAlignBankMask(i)
+    b.io.write.req.valid         := t1_fire && t1_writeAlignBankMask(i)
     b.io.write.req.bits.startPc  := t1_startPcVec(i)
     b.io.write.req.bits.branches := t1_train.branches
     b.io.write.req.bits.meta     := t1_meta.entries(i)
@@ -139,10 +140,10 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   private val perf_s2HitMask             = VecInit(alignBanks.flatMap(_.io.read.resp.predictions.map(_.valid)))
   private val perf_t1HitMispredictBranch = t1_meta.entries.flatten.map(_.hit(t1_mispredictInfo.bits)).reduce(_ || _)
 
-  XSPerfAccumulate("total_train", t1_valid)
+  XSPerfAccumulate("total_train", t1_fire)
   XSPerfAccumulate("pred_hit", s2_fire && perf_s2HitMask.reduce(_ || _))
   XSPerfHistogram("pred_hit_count", PopCount(perf_s2HitMask), s2_fire, 0, NumWay * NumAlignBanks + 1)
-  XSPerfAccumulate("train_has_mispredict", t1_valid && t1_mispredictInfo.valid)
-  XSPerfAccumulate("train_hit_mispredict", t1_valid && t1_mispredictInfo.valid && perf_t1HitMispredictBranch)
+  XSPerfAccumulate("train_has_mispredict", t1_fire && t1_mispredictInfo.valid)
+  XSPerfAccumulate("train_hit_mispredict", t1_fire && t1_mispredictInfo.valid && perf_t1HitMispredictBranch)
   XSPerfAccumulate("pred_miss", s2_fire && perf_s2HitMask.reduce(!_ && !_))
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
index 2c3402977ba..bd846ac8cc1 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
@@ -194,7 +194,7 @@ class MainBtbAlignBank(
   /* *** t1 ***
    * send write req to internal banks (srams)
    */
-  private val t1_valid            = w.req.valid
+  private val t1_fire             = w.req.valid
   private val t1_startPc          = w.req.bits.startPc
   private val t1_branches         = w.req.bits.branches
   private val t1_meta             = w.req.bits.meta
@@ -231,17 +231,17 @@ class MainBtbAlignBank(
   t1_entry.attribute       := t1_mispredictInfo.bits.attribute
 
   // similar to s0 case
-  assert(!t1_valid || t1_alignBankIdx === alignIdx.U, "MainBtbAlignBank alignIdx mismatch")
+  assert(!t1_fire || t1_alignBankIdx === alignIdx.U, "MainBtbAlignBank alignIdx mismatch")
 
   internalBanks.zipWithIndex.foreach { case (b, i) =>
-    b.io.writeEntry.req.valid        := t1_valid && t1_entryNeedWrite && t1_internalBankMask(i)
+    b.io.writeEntry.req.valid        := t1_fire && t1_entryNeedWrite && t1_internalBankMask(i)
     b.io.writeEntry.req.bits.setIdx  := t1_setIdx
     b.io.writeEntry.req.bits.wayMask := t1_entryWayMask
     b.io.writeEntry.req.bits.entry   := t1_entry
   }
 
   // update replacer
-  replacer.io.trainTouch.valid        := t1_valid && t1_entryNeedWrite
+  replacer.io.trainTouch.valid        := t1_fire && t1_entryNeedWrite
   replacer.io.trainTouch.bits.setIdx  := getReplacerSetIndex(t1_startPc)
   replacer.io.trainTouch.bits.wayMask := t1_entryWayMask
 
@@ -265,7 +265,7 @@ class MainBtbAlignBank(
   private val t1_counterNeedWrite = t1_counterWayMask.reduce(_ || _)
 
   internalBanks.zipWithIndex.foreach { case (b, i) =>
-    b.io.writeCounter.req.valid         := t1_valid && t1_counterNeedWrite && t1_internalBankMask(i)
+    b.io.writeCounter.req.valid         := t1_fire && t1_counterNeedWrite && t1_internalBankMask(i)
     b.io.writeCounter.req.bits.setIdx   := t1_setIdx
     b.io.writeCounter.req.bits.wayMask  := t1_counterWayMask.asUInt
     b.io.writeCounter.req.bits.counters := t1_newCounters
@@ -284,7 +284,7 @@ class MainBtbAlignBank(
 
   XSPerfAccumulate(
     "", // no common prefix is needed
-    t1_valid && t1_mispredictInfo.valid,
+    t1_fire && t1_mispredictInfo.valid,
     Seq(
       ("allocate", !t1_hit),
       ("fixTarget", t1_hit && t1_mispredictInfo.bits.attribute.needIttage),
@@ -292,5 +292,5 @@ class MainBtbAlignBank(
     )
   )
 
-  XSPerfAccumulate("updateCounter", Mux(t1_valid, PopCount(t1_counterWayMask), 0.U))
+  XSPerfAccumulate("updateCounter", Mux(t1_fire, PopCount(t1_counterWayMask), 0.U))
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
index fa9c7eebb02..6871d5bf94d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
@@ -51,7 +51,7 @@ class Ras(implicit p: Parameters) extends BasePredictor with HasRasParameters wi
 
   io.resetDone := true.B
 
-  io.train.ready := true.B
+  io.trainReady := true.B
 
   def alignMask: UInt = ((~0.U(VAddrBits.W)) << FetchBlockAlignWidth).asUInt
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
index 33c57e7af1d..dccea5f1b49 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
@@ -78,7 +78,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   }
   io.resetDone := resetDone
 
-  io.train.ready := true.B
+  io.trainReady := true.B
 
   /*
    * ghr stage ctrl signals
@@ -313,13 +313,18 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   io.meta.predGlobalIdx   := RegEnable(VecInit(s2_globalIdx), s2_fire)
   io.meta.predBiasIdx     := RegEnable(s2_biasIdx, s2_fire)
 
+  /*
+   *  train pipeline stage 0
+   */
+  private val t0_fire = io.stageCtrl.t0_fire
+
   /*
    *  train pipeline stage 1
    */
-  private val t1_trainValid = RegNext(io.train.fire, init = false.B)
-  private val t1_train      = RegEnable(io.train.bits, io.train.fire)
-  private val t1_branches   = t1_train.branches
-  private val t1_meta       = t1_train.meta.sc
+  private val t1_fire     = RegNext(t0_fire, false.B)
+  private val t1_train    = RegEnable(io.train, t0_fire)
+  private val t1_branches = t1_train.branches
+  private val t1_meta     = t1_train.meta.sc
 
   private val t1_bankMask = getBankMask(t1_train.startPc)
 
@@ -327,7 +332,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
     getPathTableIdx(
       t1_train.startPc,
       new FoldedHistoryInfo(info.HistoryLength, min(info.HistoryLength, log2Ceil(info.Size / NumWays / NumBanks))),
-      RegEnable(io.trainFoldedPathHist, io.train.valid),
+      RegEnable(io.trainFoldedPathHist, t0_fire),
       info.Size / NumWays / NumBanks
     )
   )
@@ -354,7 +359,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
     WireInit(VecInit.fill(ResolveEntryBranchNumber)(false.B)) // if the branch cfi not in mbtbResult, do not train
   private val t1_writeValidVec =
     VecInit(t1_branches.zip(t1_branchesScIdxHitVec).map { case (b, hit) =>
-      b.valid && b.bits.attribute.isConditional && t1_trainValid && hit
+      b.valid && b.bits.attribute.isConditional && t1_fire && hit
     })
   private val t1_writeValid = t1_writeValidVec.reduce(_ || _)
   private val t1_writeTakenVec =
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 9e1b733695f..0a93f2c616c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -42,6 +42,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     val prediction:             Vec[TagePrediction]    = Output(Vec(NumBtbResultEntries, new TagePrediction))
     val toSc:                   TageToScIO             = new TageToScIO
     val meta:                   TageMeta               = Output(new TageMeta)
+
+    val debug_trainValid: Bool = Input(Bool())
   }
   val io: TageIO = IO(new TageIO)
 
@@ -193,8 +195,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
      - send read request to tables
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val t0_startPc  = io.train.bits.startPc
-  private val t0_branches = io.train.bits.branches
+  private val t0_startPc  = io.train.startPc
+  private val t0_branches = io.train.branches
 
   // currently all tables share the same bank index
   private val t0_bankIdx  = tables.head.getBankIndex(t0_startPc)
@@ -203,12 +205,17 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val t0_condMask = VecInit(t0_branches.map(branch => branch.valid && branch.bits.attribute.isConditional))
   private val t0_hasCond  = t0_condMask.reduce(_ || _)
 
-  private val t0_readBankConflict = io.train.valid && t0_hasCond && s0_fire && t0_bankIdx === s0_bankIdx
-  io.train.ready := !t0_readBankConflict
+  private val t0_readBankConflict = t0_hasCond && s0_fire && t0_bankIdx === s0_bankIdx
+
+  // t0_readBankConflict can be high even there's no train.valid, causing perf counters to be inaccurate
+  // so we use a debug_ signal for perf counters
+  private val debug_readBankConflict = io.debug_trainValid && t0_readBankConflict
 
-  private val t0_valid = io.train.fire && t0_hasCond && io.enable
+  io.trainReady := !t0_readBankConflict
 
-  private val t0_mbtbMeta = io.train.bits.meta.mbtb.entries.flatten
+  private val t0_fire = io.stageCtrl.t0_fire && t0_hasCond && io.enable
+
+  private val t0_mbtbMeta = io.train.meta.mbtb.entries.flatten
   private val t0_basePred = VecInit(t0_branches.map { branch =>
     Mux1H(t0_mbtbMeta.map(_.hit(branch.bits)), t0_mbtbMeta.map(_.counter.isPositive))
   })
@@ -220,45 +227,49 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   dontTouch(t0_setIdx)
 
   // only for perf
-  private val t0_readBankConflictReg     = RegNext(t0_readBankConflict)
-  private val t0_readBankConflictPos     = t0_readBankConflict && (!t0_readBankConflictReg)
-  private val t0_readBankConflictNeg     = !t0_readBankConflict && t0_readBankConflictReg
-  private val t0_readBankConflictDistCnt = RegInit(0.U(4.W))
-  private val perf_s0AlignedPc           = getAlignedPc(s0_startPc)
-  private val perf_s1AlignedPc           = getAlignedPc(s1_startPc)
-  private val perf_s1BankIdx             = RegEnable(s0_bankIdx, s0_fire)
+  private val debug_readBankConflictReg     = RegNext(debug_readBankConflict)
+  private val debug_readBankConflictPos     = debug_readBankConflict && (!debug_readBankConflictReg)
+  private val debug_readBankConflictNeg     = !debug_readBankConflict && debug_readBankConflictReg
+  private val debug_readBankConflictDistCnt = RegInit(0.U(4.W))
+  private val debug_s0AlignedPc             = getAlignedPc(s0_startPc)
+  private val debug_s1AlignedPc             = getAlignedPc(s1_startPc)
+  private val debug_s1BankIdx               = RegEnable(s0_bankIdx, s0_fire)
   // pred target within align 64B,and not blocked by s2
-  private val t0_readBankConflictShortLoop = t0_readBankConflictReg && s1_fire &&
-    (perf_s1BankIdx === s0_bankIdx) &&
-    (perf_s0AlignedPc.toUInt - perf_s1AlignedPc.toUInt <= FetchBlockSize.U ||
-      perf_s1AlignedPc.toUInt - perf_s0AlignedPc.toUInt <= FetchBlockSize.U) && s0_fire
-  private val t0_readBankConflictShortLoopReg     = RegNext(t0_readBankConflictShortLoop)
-  private val t0_readBankConflictShortLoopNeg     = !t0_readBankConflictShortLoop & t0_readBankConflictShortLoopReg
-  private val t0_readBankConflictShortLoopdistCnt = RegInit(0.U(4.W))
+  private val debug_readBankConflictShortLoop = debug_readBankConflictReg && s1_fire &&
+    (debug_s1BankIdx === s0_bankIdx) &&
+    (debug_s0AlignedPc.toUInt - debug_s1AlignedPc.toUInt <= FetchBlockSize.U ||
+      debug_s1AlignedPc.toUInt - debug_s0AlignedPc.toUInt <= FetchBlockSize.U) && s0_fire
+  private val debug_readBankConflictShortLoopReg = RegNext(debug_readBankConflictShortLoop)
+  private val debug_readBankConflictShortLoopNeg = !debug_readBankConflictShortLoop & debug_readBankConflictShortLoopReg
+  private val debug_readBankConflictShortLoopdistCnt = RegInit(0.U(4.W))
   // dist cnt
-  t0_readBankConflictShortLoopdistCnt := Mux(
-    t0_readBankConflictShortLoopNeg,
+  debug_readBankConflictShortLoopdistCnt := Mux(
+    debug_readBankConflictShortLoopNeg,
     0.U,
-    Mux(t0_readBankConflictShortLoop, t0_readBankConflictShortLoopdistCnt + 1.U, t0_readBankConflictShortLoopdistCnt)
+    Mux(
+      debug_readBankConflictShortLoop,
+      debug_readBankConflictShortLoopdistCnt + 1.U,
+      debug_readBankConflictShortLoopdistCnt
+    )
   )
 
-  t0_readBankConflictDistCnt := Mux(
-    t0_readBankConflictNeg,
+  debug_readBankConflictDistCnt := Mux(
+    debug_readBankConflictNeg,
     0.U,
-    Mux(t0_readBankConflict, t0_readBankConflictDistCnt + 1.U, t0_readBankConflictDistCnt)
+    Mux(debug_readBankConflict, debug_readBankConflictDistCnt + 1.U, debug_readBankConflictDistCnt)
   )
 
-//  when(t0_valid) {
-//    assert(t0_setIdx === io.train.bits.meta.tage.debug_setIdx, "predict setIdx != train setIdx")
+//  when(t0_fire) {
+//    assert(t0_setIdx === io.train.meta.tage.debug_setIdx, "predict setIdx != train setIdx")
 //  }
 
   tables.zipWithIndex.foreach { case (table, tableIdx) =>
-    table.io.trainReadReq.valid         := t0_valid
+    table.io.trainReadReq.valid         := t0_fire
     table.io.trainReadReq.bits.setIdx   := t0_setIdx(tableIdx)
     table.io.trainReadReq.bits.bankMask := t0_bankMask
   }
 
-  when(t0_valid) {
+  when(t0_fire) {
     assert(!(s0_fire && s0_bankIdx === t0_bankIdx), "TageTable: predictReadReq and trainReadReq conflict")
   }
 
@@ -268,26 +279,26 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
      - compute temp tag
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val t1_valid    = RegNext(t0_valid) && io.enable
-  private val t1_startPc  = RegEnable(t0_startPc, t0_valid)
-  private val t1_branches = RegEnable(t0_branches, t0_valid)
-  private val t1_condMask = RegEnable(t0_condMask, t0_valid)
+  private val t1_fire     = RegNext(t0_fire) && io.enable
+  private val t1_startPc  = RegEnable(t0_startPc, t0_fire)
+  private val t1_branches = RegEnable(t0_branches, t0_fire)
+  private val t1_condMask = RegEnable(t0_condMask, t0_fire)
 
-  private val t1_setIdx   = RegEnable(t0_setIdx, t0_valid)
-  private val t1_bankMask = RegEnable(t0_bankMask, t0_valid)
+  private val t1_setIdx   = RegEnable(t0_setIdx, t0_fire)
+  private val t1_bankMask = RegEnable(t0_bankMask, t0_fire)
 
   private val t1_allTableEntries    = VecInit(tables.map(_.io.trainReadResp.entries))
   private val t1_allTableUsefulCtrs = VecInit(tables.map(_.io.trainReadResp.usefulCtrs))
 
-  private val t1_basePred = RegEnable(t0_basePred, t0_valid)
+  private val t1_basePred = RegEnable(t0_basePred, t0_fire)
 
-  private val t1_foldedHist = RegEnable(t0_foldedHist, t0_valid)
+  private val t1_foldedHist = RegEnable(t0_foldedHist, t0_fire)
   private val t1_rawTag = VecInit((tables zip t1_foldedHist).map { case (table, hist) =>
     table.getRawTag(t1_startPc, hist.forTag)
   })
 
-  private val t1_debugTempTag = RegEnable(io.train.bits.meta.tage.debug_tempTag, t0_valid)
-//  when(t1_valid) {
+  private val t1_debugTempTag = RegEnable(io.train.meta.tage.debug_tempTag, t0_fire)
+//  when(t1_fire) {
 //    assert(t1_rawTag === t1_debugTempTag, "predict tag != train tag")
 //  }
 
@@ -301,25 +312,25 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
      - allocate a new entry when mispredict
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val t2_valid    = RegNext(t1_valid) && io.enable
-  private val t2_branches = RegEnable(t1_branches, t1_valid)
-  private val t2_condMask = RegEnable(t1_condMask, t1_valid)
+  private val t2_fire     = RegNext(t1_fire) && io.enable
+  private val t2_branches = RegEnable(t1_branches, t1_fire)
+  private val t2_condMask = RegEnable(t1_condMask, t1_fire)
 
-  private val t2_startPc = RegEnable(t1_startPc, t1_valid)
+  private val t2_startPc = RegEnable(t1_startPc, t1_fire)
   dontTouch(t2_startPc)
 
-  private val t2_setIdx   = RegEnable(t1_setIdx, t1_valid)
-  private val t2_bankMask = RegEnable(t1_bankMask, t1_valid)
+  private val t2_setIdx   = RegEnable(t1_setIdx, t1_fire)
+  private val t2_bankMask = RegEnable(t1_bankMask, t1_fire)
 
-  private val t2_allTableEntries    = RegEnable(t1_allTableEntries, t1_valid)
-  private val t2_allTableUsefulCtrs = RegEnable(t1_allTableUsefulCtrs, t1_valid)
+  private val t2_allTableEntries    = RegEnable(t1_allTableEntries, t1_fire)
+  private val t2_allTableUsefulCtrs = RegEnable(t1_allTableUsefulCtrs, t1_fire)
 
-  private val t2_rawTag = RegEnable(t1_rawTag, t1_valid)
+  private val t2_rawTag = RegEnable(t1_rawTag, t1_fire)
 
-  private val t2_basePred = RegEnable(t1_basePred, t1_valid)
+  private val t2_basePred = RegEnable(t1_basePred, t1_fire)
 
-  private val t2_cfiPcVec        = RegEnable(t1_cfiPcVec, t1_valid)
-  private val t2_cfiUseAltIdxVec = RegEnable(t1_cfiUseAltIdxVec, t1_valid)
+  private val t2_cfiPcVec        = RegEnable(t1_cfiPcVec, t1_fire)
+  private val t2_cfiUseAltIdxVec = RegEnable(t1_cfiUseAltIdxVec, t1_fire)
   dontTouch(t2_cfiPcVec)
 
   private val t2_allBranchUpdateInfo = t2_branches.zipWithIndex.map { case (branch, i) =>
@@ -365,7 +376,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
     XSPerfAccumulate(
       s"t2_branch_${i}_mispredict_diff",
-      t2_valid && branch.valid && isCond && ((finalPred =/= actualTaken) =/= branch.bits.mispredict)
+      t2_fire && branch.valid && isCond && ((finalPred =/= actualTaken) =/= branch.bits.mispredict)
     )
 
     val providerNewTakenCtr = providerInfo.takenCtr.getUpdate(actualTaken)
@@ -428,9 +439,9 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
       idxMatch && updateInfo.decreaseUseAlt
     }.reduce(_ || _)
 
-    when(t2_valid && increase) {
+    when(t2_fire && increase) {
       ctr.increase()
-    }.elsewhen(t2_valid && decrease) {
+    }.elsewhen(t2_fire && decrease) {
       ctr.decrease()
     }
   }
@@ -460,7 +471,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val t2_canAllocateTableMask = t2_longerHistoryTableMask & t2_allTableCanAllocateWayMask.map(_.orR).asUInt
   private val t2_canAllocate          = t2_canAllocateTableMask.orR
   private val t2_allocate             = t2_needAllocate && t2_canAllocate
-  private val t2_usefulReset          = t2_valid && usefulResetCtr.isSaturatePositive
+  private val t2_usefulReset          = t2_fire && usefulResetCtr.isSaturatePositive
 
   private val t2_allocateTableMaskOH = PriorityEncoderOH(t2_canAllocateTableMask) & Fill(NumTables, t2_allocate)
   private val t2_allocateWayMaskOH   = PriorityEncoderOH(Mux1H(t2_allocateTableMaskOH, t2_allTableCanAllocateWayMask))
@@ -502,7 +513,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
     val thisTableNeedUpdate   = updateMask.reduce(_ || _)
     val thisTableNeedAllocate = t2_allocateTableMaskOH(tableIdx)
-    table.io.writeReq.valid         := t2_valid && (thisTableNeedUpdate || thisTableNeedAllocate)
+    table.io.writeReq.valid         := t2_fire && (thisTableNeedUpdate || thisTableNeedAllocate)
     table.io.writeReq.bits.setIdx   := t2_setIdx(tableIdx)
     table.io.writeReq.bits.bankMask := t2_bankMask
 
@@ -527,7 +538,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
   when(t2_usefulReset) {
     usefulResetCtr.resetZero()
-  }.elsewhen(t2_valid && t2_needAllocate && !t2_canAllocate) {
+  }.elsewhen(t2_fire && t2_needAllocate && !t2_canAllocate) {
     usefulResetCtr.increase()
   }
 
@@ -568,7 +579,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   tageTraceDBTables.zip(condTraceVec).foreach { case (dbTable, condTrace) =>
     dbTable.log(
       data = condTrace.bits,
-      en = t2_valid && condTrace.valid,
+      en = t2_fire && condTrace.valid,
       clock = clock,
       reset = reset
     )
@@ -581,70 +592,70 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val s2_condMask = s2_branches.map(branch => branch.valid && branch.bits.attribute.isConditional)
   XSPerfAccumulate("predict_cond", Mux(io.stageCtrl.s2_fire, PopCount(s2_condMask), 0.U))
 
-  XSPerfAccumulate("total_train", io.train.fire)
-  XSPerfAccumulate("train_has_cond", t0_valid)
+  XSPerfAccumulate("total_train", io.stageCtrl.t0_fire)
+  XSPerfAccumulate("train_has_cond", t0_fire)
 
   XSPerfAccumulate(
     "total_condbr_mispredicted",
     t2_allBranchUpdateInfo.map(e =>
-      (t2_valid && e.valid && e.mispredicted).asUInt
+      (t2_fire && e.valid && e.mispredicted).asUInt
     ).reduce(_ +& _)
   )
   XSPerfAccumulate(
     "total_first_condbr_mispredicted",
     t2_allBranchUpdateInfo.map(e =>
-      t2_valid && e.valid && e.mispredicted
+      t2_fire && e.valid && e.mispredicted
     ).reduce(_ || _)
   )
   XSPerfAccumulate(
     "total_allbr_mispredicted",
-    io.train.bits.branches.map(b => (io.train.valid && b.valid && b.bits.mispredict).asUInt).reduce(_ +& _)
+    io.train.branches.map(b => (io.stageCtrl.t0_fire && b.valid && b.bits.mispredict).asUInt).reduce(_ +& _)
   )
   XSPerfAccumulate(
     "mispredict_branch_use_basetable",
     t2_allBranchUpdateInfo.map(e =>
-      (t2_valid && e.valid && e.mispredicted && !e.providerTableOH.orR).asUInt
+      (t2_fire && e.valid && e.mispredicted && !e.providerTableOH.orR).asUInt
     ).reduce(_ +& _)
   )
   XSPerfAccumulate(
     "mispredict_branch_has_provider",
     t2_allBranchUpdateInfo.map(e =>
-      (t2_valid && e.valid && e.mispredicted && e.providerTableOH.orR).asUInt
+      (t2_fire && e.valid && e.mispredicted && e.providerTableOH.orR).asUInt
     ).reduce(_ +& _)
   )
   XSPerfAccumulate(
     "resolve_branch_use_basetable",
     t2_allBranchUpdateInfo.map(e =>
-      (t2_valid && e.valid && !e.providerTableOH.orR).asUInt
+      (t2_fire && e.valid && !e.providerTableOH.orR).asUInt
     ).reduce(_ +& _)
   )
   XSPerfAccumulate(
     "resolve_branch_has_provider",
     t2_allBranchUpdateInfo.map(e =>
-      (t2_valid && e.valid && e.providerTableOH.orR).asUInt
+      (t2_fire && e.valid && e.providerTableOH.orR).asUInt
     ).reduce(_ +& _)
   )
   XSPerfAccumulate(
     "resolve_total_use_alt",
     t2_allBranchUpdateInfo.map(e =>
-      (t2_valid && e.valid && e.useAlt).asUInt
+      (t2_fire && e.valid && e.useAlt).asUInt
     ).reduce(_ +& _)
   )
   for (i <- 0 until NumTables) {
     XSPerfAccumulate(
       s"allocate_branch_provider_is_table_${i}",
-      t2_valid && t2_allocateBranchProviderTableOH.orR && t2_allocateBranchProviderTableOH(i)
+      t2_fire && t2_allocateBranchProviderTableOH.orR && t2_allocateBranchProviderTableOH(i)
     )
     XSPerfAccumulate(
       s"resolve_branch_hit_table_${i}",
       t2_allBranchUpdateInfo.map(e =>
-        (t2_valid && e.valid && e.hitTableMask(i)).asUInt
+        (t2_fire && e.valid && e.hitTableMask(i)).asUInt
       ).reduce(_ +& _)
     )
     XSPerfAccumulate(
       s"resolve_branch_provider_is_table_${i}",
       t2_allBranchUpdateInfo.map(e =>
-        (t2_valid && e.valid && e.providerTableOH(i)).asUInt
+        (t2_fire && e.valid && e.providerTableOH(i)).asUInt
       ).reduce(_ +& _)
     )
   }
@@ -655,8 +666,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
    */
   XSPerfHistogram(
     "read_conflict_bubble_dist",
-    t0_readBankConflictDistCnt,
-    t0_readBankConflictNeg,
+    debug_readBankConflictDistCnt,
+    debug_readBankConflictNeg,
     0,
     16
   )
@@ -668,22 +679,22 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
    */
   XSPerfHistogram(
     "read_conflict_loop_dist",
-    t0_readBankConflictShortLoopdistCnt,
-    t0_readBankConflictShortLoopNeg,
+    debug_readBankConflictShortLoopdistCnt,
+    debug_readBankConflictShortLoopNeg,
     0,
     16
   )
-  XSPerfAccumulate("read_conflict", t0_readBankConflict)
+  XSPerfAccumulate("read_conflict", debug_readBankConflict)
   XSPerfAccumulate("reset_useful", t2_usefulReset)
   XSPerfAccumulate(
     "allocate_not_needed_due_to_already_on_highest_table",
     t2_mispredictBranchUpdateInfo.valid && t2_mispredictBranchUpdateInfo.mispredicted &&
       !t2_mispredictBranchUpdateInfo.needAllocate
   )
-  XSPerfAccumulate("allocate_needed", t2_valid && t2_needAllocate)
-  XSPerfAccumulate("allocate_success", t2_valid && t2_needAllocate && t2_canAllocate)
-  XSPerfAccumulate("allocate_failure", t2_valid && t2_needAllocate && !t2_canAllocate)
+  XSPerfAccumulate("allocate_needed", t2_fire && t2_needAllocate)
+  XSPerfAccumulate("allocate_success", t2_fire && t2_needAllocate && t2_canAllocate)
+  XSPerfAccumulate("allocate_failure", t2_fire && t2_needAllocate && !t2_canAllocate)
   for (i <- 0 until NumTables) {
-    XSPerfAccumulate(s"table_${i}_allocate", t2_valid && t2_allocateTableMaskOH(i))
+    XSPerfAccumulate(s"table_${i}_allocate", t2_fire && t2_allocateTableMaskOH(i))
   }
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
index 375fe90246b..a1563129ed1 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
@@ -42,9 +42,8 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   println(f"  Address fields:")
   addrFields.show(indent = 4)
 
-  io.resetDone := true.B
-
-  io.train.ready := true.B
+  io.resetDone  := true.B
+  io.trainReady := true.B
 
   /* *** submodules *** */
   private val entries = RegInit(VecInit(Seq.fill(NumEntries)(0.U.asTypeOf(new MicroBtbEntry))))
@@ -92,7 +91,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
    * - check if hits t1 stage
    * - calculate hit flags
    */
-  private val t0_valid       = Wire(Bool())
+  private val t0_fire        = Wire(Bool())
   private val t0_startPc     = Wire(PrunedAddr(VAddrBits))
   private val t0_actualTaken = Wire(Bool())
   private val t0_position    = Wire(UInt(CfiPositionWidth.W))
@@ -100,7 +99,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   private val t0_attribute   = Wire(new BranchAttribute)
 
   if (UseFastTrain) {
-    t0_valid       := io.fastTrain.get.valid && io.enable
+    t0_fire        := io.fastTrain.get.valid && io.enable
     t0_startPc     := io.fastTrain.get.bits.startPc
     t0_actualTaken := io.fastTrain.get.bits.finalPrediction.taken
     t0_position    := io.fastTrain.get.bits.finalPrediction.cfiPosition
@@ -108,12 +107,12 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
     t0_attribute   := io.fastTrain.get.bits.finalPrediction.attribute
   } else {
     // FIXME: not sure if first mispredict is the best, maybe first taken?
-    t0_valid       := io.train.fire && io.train.bits.mispredictBranch.valid && io.enable
-    t0_startPc     := io.train.bits.startPc
-    t0_actualTaken := io.train.bits.mispredictBranch.bits.taken
-    t0_position    := io.train.bits.mispredictBranch.bits.cfiPosition
-    t0_fullTarget  := io.train.bits.mispredictBranch.bits.target
-    t0_attribute   := io.train.bits.mispredictBranch.bits.attribute
+    t0_fire        := io.stageCtrl.t0_fire && io.train.mispredictBranch.valid && io.enable
+    t0_startPc     := io.train.startPc
+    t0_actualTaken := io.train.mispredictBranch.bits.taken
+    t0_position    := io.train.mispredictBranch.bits.cfiPosition
+    t0_fullTarget  := io.train.mispredictBranch.bits.target
+    t0_attribute   := io.train.mispredictBranch.bits.attribute
   }
 
   private val t0_tag         = getTag(t0_startPc)
@@ -129,7 +128,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   // the second train might be a false "not hit" and allocate a new entry, causing a multi-hit;
   // or, the first may replace the entry, causing a false "hit" in the second train, causing wrong update.
   // So, we define some of the t1 signals in advance, and use them to check if the contiguous trains are hit.
-  private val t1_valid        = Wire(Bool())
+  private val t1_fire         = Wire(Bool())
   private val t1_tag          = Wire(UInt(TagWidth.W))
   private val t1_updateIdx    = Wire(UInt(log2Up(NumEntries).W))
   private val t1_hitEntry     = Wire(new MicroBtbEntry)
@@ -139,7 +138,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   // if t0_tag === t1_tag, t1 must be updating the entry, so we can see it as a hit, and use t1_updateIdx as hitIdx
   private val t0_hitT1Update = Wire(Bool())
   // if t0 hits but t1 is replacing it, we should see it as not hit
-  private val t0_hitT1Victim = t1_valid && t0_realHitIdx === replacer.io.victim && t1_allocate
+  private val t0_hitT1Victim = t1_fire && t0_realHitIdx === replacer.io.victim && t1_allocate
 
   // fix final hit
   private val t0_hit = t0_realHit && !t0_hitT1Victim || t0_hitT1Update
@@ -159,25 +158,25 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
    * - update entries
    * - update replacer
    */
-  t1_valid := RegNext(t0_valid, false.B)
-  t1_tag   := RegEnable(t0_tag, t0_valid)
-  private val t1_actualTaken = RegEnable(t0_actualTaken, t0_valid)
-  private val t1_position    = RegEnable(t0_position, t0_valid)
-  private val t1_target      = RegEnable(t0_target, t0_valid)
-  private val t1_attribute   = RegEnable(t0_attribute, t0_valid)
-  private val t1_targetCarry = t0_targetCarry.map(w => RegEnable(w, t0_valid)) // if (EnableTargetFix)
-
-  private val t1_hit    = RegEnable(t0_hit, t0_valid)
-  private val t1_hitIdx = RegEnable(t0_hitIdx, t0_valid)
-  t1_hitEntry := RegEnable(t0_hitEntry, t0_valid)
+  t1_fire := RegNext(t0_fire, false.B)
+  t1_tag  := RegEnable(t0_tag, t0_fire)
+  private val t1_actualTaken = RegEnable(t0_actualTaken, t0_fire)
+  private val t1_position    = RegEnable(t0_position, t0_fire)
+  private val t1_target      = RegEnable(t0_target, t0_fire)
+  private val t1_attribute   = RegEnable(t0_attribute, t0_fire)
+  private val t1_targetCarry = t0_targetCarry.map(w => RegEnable(w, t0_fire)) // if (EnableTargetFix)
+
+  private val t1_hit    = RegEnable(t0_hit, t0_fire)
+  private val t1_hitIdx = RegEnable(t0_hitIdx, t0_fire)
+  t1_hitEntry := RegEnable(t0_hitEntry, t0_fire)
 
   // hit states (flags), valid only when t1_hit
-  private val t1_hitNotUseful     = RegEnable(t0_hitNotUseful, t0_valid)
-  private val t1_hitPositionSame  = RegEnable(t0_hitPositionSame, t0_valid)
-  private val t1_hitAttributeSame = RegEnable(t0_hitAttributeSame, t0_valid)
-  private val t1_hitTargetSame    = RegEnable(t0_hitTargetSame, t0_valid)
+  private val t1_hitNotUseful     = RegEnable(t0_hitNotUseful, t0_fire)
+  private val t1_hitPositionSame  = RegEnable(t0_hitPositionSame, t0_fire)
+  private val t1_hitAttributeSame = RegEnable(t0_hitAttributeSame, t0_fire)
+  private val t1_hitTargetSame    = RegEnable(t0_hitTargetSame, t0_fire)
   // only when t1 is updating/allocating can t0 hit it
-  t0_hitT1Update := t1_valid && t0_tag === t1_tag && (t1_hit || t1_allocate)
+  t0_hitT1Update := t1_fire && t0_tag === t1_tag && (t1_hit || t1_allocate)
   // init a new entry
   private def initEntryIfNotUseful(notUseful: Bool): Unit =
     when(notUseful) {
@@ -195,7 +194,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
       t1_updatedEntry.usefulCnt.value := t1_hitEntry.usefulCnt.getDecrease
     }
 
-  when(t1_valid) {
+  when(t1_fire) {
     when(!t1_hit) {
       // not hit
       // init a new entry if actually taken
@@ -219,34 +218,34 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   t1_allocate  := !t1_hit && t1_actualTaken
   t1_updateIdx := Mux(t1_hit, t1_hitIdx, replacer.io.victim)
   // and write back the updated entry
-  when(t1_valid && (t1_hit || t1_allocate)) { // update entry if hit, or alloc entry only for taken branches
+  when(t1_fire && (t1_hit || t1_allocate)) { // update entry if hit, or alloc entry only for taken branches
     entries(t1_updateIdx) := t1_updatedEntry
   }
 
   // update replacer
-  replacer.io.trainTouch.valid := t1_valid
+  replacer.io.trainTouch.valid := t1_fire
   replacer.io.trainTouch.bits  := t1_updateIdx
 
   /* *** perf *** */
   XSPerfAccumulate("predHit", s1_hit && s1_fire)
   XSPerfAccumulate("predMiss", !s1_hit && s1_fire)
 
-  XSPerfAccumulate("s1Hits3FallThrough", t1_valid && t1_hit && !t1_actualTaken)
-  XSPerfAccumulate("s1Misses3Taken", t1_valid && !t1_hit && t1_actualTaken)
-  XSPerfAccumulate("s1Hits3Taken", t1_valid && t1_hit && t1_actualTaken)
-  XSPerfAccumulate("s1Misses3FallThrough", t1_valid && !t1_hit && !t1_actualTaken)
+  XSPerfAccumulate("s1Hits3FallThrough", t1_fire && t1_hit && !t1_actualTaken)
+  XSPerfAccumulate("s1Misses3Taken", t1_fire && !t1_hit && t1_actualTaken)
+  XSPerfAccumulate("s1Hits3Taken", t1_fire && t1_hit && t1_actualTaken)
+  XSPerfAccumulate("s1Misses3FallThrough", t1_fire && !t1_hit && !t1_actualTaken)
 
-  XSPerfAccumulate("s1InvalidatedEntries", t1_valid && t1_hit && !t1_actualTaken && t1_hitNotUseful)
+  XSPerfAccumulate("s1InvalidatedEntries", t1_fire && t1_hit && !t1_actualTaken && t1_hitNotUseful)
 
-  XSPerfAccumulate("trainHitEntries", t0_valid && t0_realHit)
-  XSPerfAccumulate("trainHitT1Update", t0_valid && t0_hitT1Update)
-  XSPerfAccumulate("trainHitT1Victim", t0_valid && t0_hitT1Victim)
+  XSPerfAccumulate("trainHitEntries", t0_fire && t0_realHit)
+  XSPerfAccumulate("trainHitT1Update", t0_fire && t0_hitT1Update)
+  XSPerfAccumulate("trainHitT1Victim", t0_fire && t0_hitT1Victim)
 
-  XSPerfAccumulate("allocateNotUseful", t1_valid && t1_allocate && replacer.io.perf.replaceNotUseful)
-  XSPerfAccumulate("allocatePlru", t1_valid && t1_allocate && !replacer.io.perf.replaceNotUseful)
+  XSPerfAccumulate("allocateNotUseful", t1_fire && t1_allocate && replacer.io.perf.replaceNotUseful)
+  XSPerfAccumulate("allocatePlru", t1_fire && t1_allocate && !replacer.io.perf.replaceNotUseful)
   XSPerfAccumulate(
     "replace",
-    t1_valid && t1_hit && (
+    t1_fire && t1_hit && (
       !t1_hitAttributeSame && t1_hitNotUseful ||
         t1_hitAttributeSame && !t1_hitPositionSame && t1_hitNotUseful
     )
diff --git a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
index b471d152896..c14b385292b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/utage/MicroTage.scala
@@ -42,8 +42,8 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
     val meta:                   Valid[MicroTageMeta]       = Output(Valid(new MicroTageMeta))
   }
   val io: MicroTageIO = IO(new MicroTageIO)
-  io.resetDone   := true.B
-  io.train.ready := true.B
+  io.resetDone  := true.B
+  io.trainReady := true.B
 
   /* *** submodules *** */
   private val tables = TableInfos.zipWithIndex.map {
@@ -103,9 +103,9 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   io.meta       := RegEnable(predMeta, 0.U.asTypeOf(Valid(new MicroTageMeta)), io.stageCtrl.s0_fire)
 
   // ------------ MicroTage is only concerned with conditional branches ---------- //
+  private val t0_fire                    = io.fastTrain.get.valid
   private val t0_trainMeta               = io.fastTrain.get.bits.utageMeta
   private val t0_trainData               = io.fastTrain.get.bits.finalPrediction
-  private val t0_trainValid              = io.fastTrain.get.valid
   private val t0_trainStartPc            = io.fastTrain.get.bits.startPc
   private val t0_trainOverride           = io.fastTrain.get.bits.hasOverride
   private val t0_histTableTakenMap       = t0_trainMeta.histTableTakenMap
@@ -134,8 +134,8 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
     !t0_predHit && t0_trainData.attribute.isConditional && t0_trainData.taken && io.fastTrain.get.bits.hasOverride
 
   private val t0_misPred             = t0_histHitMisPred || t0_histMissHitMisPred
-  private val t0_histTableNeedAlloc  = t0_misPred && t0_trainValid
-  private val t0_histTableNeedUpdate = t0_predHit && t0_trainValid
+  private val t0_histTableNeedAlloc  = t0_misPred && t0_fire
+  private val t0_histTableNeedUpdate = t0_predHit && t0_fire
   private val t0_updateTaken         = (t0_predCfiPosition === t0_trainData.cfiPosition) && t0_trainData.taken
   private val t0_updateCfiPosition   = t0_predCfiPosition
   private val t0_actualTaken         = t0_trainData.attribute.isConditional && t0_trainData.taken
@@ -154,7 +154,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
 
   when(tickCounter(TickWidth)) {
     tickCounter := 0.U
-  }.elsewhen((t0_allocMask === 0.U) && t0_histTableNeedAlloc && t0_trainValid) {
+  }.elsewhen((t0_allocMask === 0.U) && t0_histTableNeedAlloc && t0_fire) {
     tickCounter := tickCounter + 1.U
   }
 
@@ -181,7 +181,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
 // - Decrement on misprediction.
 // - Increment only if prediction is correct AND base table failed to predict correctly.
   tables.zipWithIndex.foreach { case (t, i) =>
-    t.update.valid := t0_trainValid &&
+    t.update.valid := t0_fire &&
       ((t0_allocMask(i) && t0_histTableNeedAlloc) || (t0_providerMask(i) && t0_histTableNeedUpdate))
     t.update.bits.allocValid  := (t0_allocMask(i) && t0_histTableNeedAlloc)
     t.update.bits.updateValid := (t0_providerMask(i) && t0_histTableNeedUpdate) && fastTrainHasPredBr
@@ -208,7 +208,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   private val debug_tableMeta  = MuxCase(0.U.asTypeOf(new MicroTageDebug), debug_metaCases.reverse)
 
   private val utageTrace = Wire(Valid(new MicroTageTrace))
-  utageTrace.valid            := t0_trainValid && (t0_histTableNeedAlloc || t0_histTableNeedUpdate)
+  utageTrace.valid            := t0_fire && (t0_histTableNeedAlloc || t0_histTableNeedUpdate)
   utageTrace.bits.startVAddr  := t0_trainStartPc.toUInt
   utageTrace.bits.branchPc    := getCfiPcFromPosition(t0_trainStartPc, t0_actualCfiPosition).toUInt
   utageTrace.bits.cfiPosition := t0_actualCfiPosition
@@ -227,7 +227,7 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   private val utageTraceDBTables = ChiselDB.createTable(s"microTageTrace", new MicroTageTrace, EnableTraceAndDebug)
   utageTraceDBTables.log(
     data = utageTrace.bits,
-    en = t0_trainValid && utageTrace.valid,
+    en = t0_fire && utageTrace.valid,
     clock = clock,
     reset = reset
   )
@@ -246,19 +246,18 @@ class MicroTage(implicit p: Parameters) extends BasePredictor with HasMicroTageP
   private val trainIdx0 = debug_tableMetas(0).debug_idx
   private val trainTag0 = debug_tableMetas(0).debug_tag
 
-  XSPerfAccumulate("train_needAlloc", t0_trainValid && t0_histTableNeedAlloc)
-  XSPerfAccumulate("train_needUpdate", t0_trainValid && t0_histTableNeedUpdate)
-  XSPerfAccumulate("train_histHitMisPred", t0_trainValid && t0_histHitMisPred)
+  XSPerfAccumulate("train_needAlloc", t0_fire && t0_histTableNeedAlloc)
+  XSPerfAccumulate("train_needUpdate", t0_fire && t0_histTableNeedUpdate)
+  XSPerfAccumulate("train_histHitMisPred", t0_fire && t0_histHitMisPred)
   if (EnableTraceAndDebug) {
     XSPerfAccumulate(
       "train_useMicroTage_and_override_fromFastTrain",
-      t0_trainValid && t0_trainMeta.debug_useMicroTage.get && io.fastTrain.get.bits.hasOverride
+      t0_fire && t0_trainMeta.debug_useMicroTage.get && io.fastTrain.get.bits.hasOverride
     )
-    XSPerfAccumulate("train_useMicroTage_fromFastTrain", t0_trainValid && t0_trainMeta.debug_useMicroTage.get)
-    XSPerfAccumulate("train_idx_hit", t0_trainValid && (t0_trainMeta.debug_predIdx0.get === trainIdx0))
-    XSPerfAccumulate("train_tag_hit", t0_trainValid && (t0_trainMeta.debug_predTag0.get === trainTag0))
-    XSPerfAccumulate("train_idx_miss", t0_trainValid && (t0_trainMeta.debug_predIdx0.get =/= trainIdx0))
-    XSPerfAccumulate("train_tag_miss", t0_trainValid && (t0_trainMeta.debug_predTag0.get =/= trainTag0))
+    XSPerfAccumulate("train_useMicroTage_fromFastTrain", t0_fire && t0_trainMeta.debug_useMicroTage.get)
+    XSPerfAccumulate("train_idx_hit", t0_fire && (t0_trainMeta.debug_predIdx0.get === trainIdx0))
+    XSPerfAccumulate("train_tag_hit", t0_fire && (t0_trainMeta.debug_predTag0.get === trainTag0))
+    XSPerfAccumulate("train_idx_miss", t0_fire && (t0_trainMeta.debug_predIdx0.get =/= trainIdx0))
+    XSPerfAccumulate("train_tag_miss", t0_fire && (t0_trainMeta.debug_predTag0.get =/= trainTag0))
   }
-
 }
```
