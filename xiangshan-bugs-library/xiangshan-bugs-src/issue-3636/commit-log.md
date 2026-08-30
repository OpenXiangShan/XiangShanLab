# Commit Log
- Issue: #3636
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3636
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3636
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3636
- Changed files: 5
- Additions: 19
- Deletions: 17

## Files
- `src/main/scala/xiangshan/frontend/BPU.scala`
- `src/main/scala/xiangshan/frontend/FTB.scala`
- `src/main/scala/xiangshan/frontend/FauFTB.scala`
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala`
- `src/main/scala/xiangshan/frontend/newRAS.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/BPU.scala b/src/main/scala/xiangshan/frontend/BPU.scala
index 98bd35d2ea1..bef4a76f8e1 100644
--- a/src/main/scala/xiangshan/frontend/BPU.scala
+++ b/src/main/scala/xiangshan/frontend/BPU.scala
@@ -725,16 +725,11 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
     )
   }
 
-  val s3_redirect_target_dup = dup_wire(UInt(VAddrBits.W))
-  for ((((s3_redirect_target, s3_fall_thru_error), s3_fallThroughAddr), s3_target) <- s3_redirect_target_dup zip s3_redirect_on_fall_thru_error_dup zip resp.s3.fallThroughAddr zip resp.s3.getTarget){
-    s3_redirect_target := Mux(s3_fall_thru_error, s3_fallThroughAddr, s3_target)
-  }
-
   XSPerfAccumulate(f"s3_redirect_on_br_taken", s3_fire_dup(0) && s3_redirect_on_br_taken_dup(0))
   XSPerfAccumulate(f"s3_redirect_on_jalr_target", s3_fire_dup(0) && s3_redirect_on_jalr_target_dup(0))
   XSPerfAccumulate(f"s3_redirect_on_others", s3_redirect_dup(0) && !(s3_redirect_on_br_taken_dup(0) || s3_redirect_on_jalr_target_dup(0)))
 
-  for (((npcGen, s3_redirect), s3_target) <- npcGen_dup zip s3_redirect_dup zip s3_redirect_target_dup)
+  for (((npcGen, s3_redirect), s3_target) <- npcGen_dup zip s3_redirect_dup zip resp.s3.getTarget)
     npcGen.register(s3_redirect, s3_target, Some("s3_target"), 3)
   for (((foldedGhGen, s3_redirect), s3_predicted_fh) <- foldedGhGen_dup zip s3_redirect_dup zip s3_predicted_fh_dup)
     foldedGhGen.register(s3_redirect, s3_predicted_fh, Some("s3_FGH"), 3)
diff --git a/src/main/scala/xiangshan/frontend/FTB.scala b/src/main/scala/xiangshan/frontend/FTB.scala
index 701417b2a3d..6c01618e339 100644
--- a/src/main/scala/xiangshan/frontend/FTB.scala
+++ b/src/main/scala/xiangshan/frontend/FTB.scala
@@ -643,14 +643,14 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
     s2_fauftb_ftb_entry_dup zip s2_ftbBank_dup zip s2_ftb_entry_dup){
       s2_ftb_entry := Mux(s2_close_ftb_req, s2_fauftb_entry, s2_ftbBank_entry)
   }
-  val s3_ftb_entry_dup = io.s2_fire.zip(s2_ftb_entry_dup).map {case (f, e) => RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit_entry, e), f)}
+  val s3_ftb_entry_dup  = io.s2_fire.zip(s2_ftb_entry_dup).map {case (f, e) => RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit_entry, e), f)}
   val real_s2_ftb_entry = Mux(s2_multi_hit_enable, s2_multi_hit_entry, s2_ftb_entry_dup(0))
   val real_s2_pc        = s2_pc_dup(0).getAddr()
   val real_s2_startLower          = Cat(0.U(1.W),    real_s2_pc(instOffsetBits+log2Ceil(PredictWidth)-1, instOffsetBits))
   val real_s2_endLowerwithCarry   = Cat(real_s2_ftb_entry.carry, real_s2_ftb_entry.pftAddr)
   val real_s2_fallThroughErr      = real_s2_startLower >= real_s2_endLowerwithCarry || real_s2_endLowerwithCarry > (real_s2_startLower + (PredictWidth).U)
   val real_s3_fallThroughErr_dup  = io.s2_fire.map {f => RegEnable(real_s2_fallThroughErr, f)}
-
+  
   //After closing ftb, the hit output from s2 is the hit of FauFTB cached in s1.
   //s1_hit is the ftbBank hit.
   val s1_hit = Mux(s1_close_ftb_req, false.B, ftbBank.io.read_hits.valid && io.ctrl.btb_enable)
diff --git a/src/main/scala/xiangshan/frontend/FauFTB.scala b/src/main/scala/xiangshan/frontend/FauFTB.scala
index ea600647f52..fd4100e3b88 100644
--- a/src/main/scala/xiangshan/frontend/FauFTB.scala
+++ b/src/main/scala/xiangshan/frontend/FauFTB.scala
@@ -101,7 +101,7 @@ class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
   val s1_hit_oh = VecInit(ways.map(_.io.resp_hit)).asUInt
   val s1_hit = s1_hit_oh.orR
   val s1_hit_way = OHToUInt(s1_hit_oh)
-  val s1_possible_full_preds = Wire(Vec(numWays, new FullBranchPrediction))
+  val s1_possible_full_preds = Wire(Vec(numWays, new FullBranchPrediction(isNotS3 = true)))
 
   val s1_all_entries = VecInit(ways.map(_.io.resp))
   for (c & fp & e <- ctrs zip s1_possible_full_preds zip s1_all_entries) {
diff --git a/src/main/scala/xiangshan/frontend/FrontendBundle.scala b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
index d0756c7e29c..17db4324807 100644
--- a/src/main/scala/xiangshan/frontend/FrontendBundle.scala
+++ b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
@@ -547,7 +547,7 @@ object selectByTaken {
   }
 }
 
-class FullBranchPrediction(implicit p: Parameters) extends XSBundle with HasBPUConst with BasicPrediction {
+class FullBranchPrediction(val isNotS3: Boolean)(implicit p: Parameters) extends XSBundle with HasBPUConst with BasicPrediction {
   val br_taken_mask = Vec(numBr, Bool())
 
   val slot_valids = Vec(totalSlot, Bool())
@@ -618,7 +618,11 @@ class FullBranchPrediction(implicit p: Parameters) extends XSBundle with HasBPUC
   def brTaken = (br_valids zip br_taken_mask).map{ case (a, b) => a && b && hit}.reduce(_||_)
 
   def target(pc: UInt): UInt = {
-    selectByTaken(taken_mask_on_slot, hit, allTarget(pc))
+    if (isNotS3){
+      selectByTaken(taken_mask_on_slot, hit, allTarget(pc))
+    }else {
+      selectByTaken(taken_mask_on_slot, hit && !fallThroughErr, allTarget(pc))
+    }
   }
 
   // allTarget return a Vec of all possible target of a BP stage
@@ -692,13 +696,14 @@ class SpeculativeInfo(implicit p: Parameters) extends XSBundle
   val topAddr = UInt(VAddrBits.W)
 }
 
-class BranchPredictionBundle(implicit p: Parameters) extends XSBundle
+// 
+class BranchPredictionBundle(val isNotS3: Boolean)(implicit p: Parameters) extends XSBundle
   with HasBPUConst with BPUUtils {
   val pc    = Vec(numDup, UInt(VAddrBits.W))
   val valid = Vec(numDup, Bool())
   val hasRedirect  = Vec(numDup, Bool())
   val ftq_idx = new FtqPtr
-  val full_pred    = Vec(numDup, new FullBranchPrediction)
+  val full_pred    = Vec(numDup, new FullBranchPrediction(isNotS3))
 
 
   def target(pc: UInt) = VecInit(full_pred.map(_.target(pc)))
@@ -710,7 +715,6 @@ class BranchPredictionBundle(implicit p: Parameters) extends XSBundle
   def shouldShiftVec   = VecInit(full_pred.map(_.shouldShiftVec))
   def fallThruError    = VecInit(full_pred.map(_.fallThruError))
   def ftbMultiHit      = VecInit(full_pred.map(_.ftbMultiHit))
-  def fallThroughAddr  = VecInit(full_pred.map(_.fallThroughAddr))
 
   def taken = VecInit(cfiIndex.map(_.valid))
 
@@ -724,9 +728,9 @@ class BranchPredictionBundle(implicit p: Parameters) extends XSBundle
 }
 
 class BranchPredictionResp(implicit p: Parameters) extends XSBundle with HasBPUConst {
-  val s1 = new BranchPredictionBundle
-  val s2 = new BranchPredictionBundle
-  val s3 = new BranchPredictionBundle
+  val s1 = new BranchPredictionBundle(isNotS3 = true)
+  val s2 = new BranchPredictionBundle(isNotS3 = true)
+  val s3 = new BranchPredictionBundle(isNotS3 = false)
 
   val s1_uftbHit = Bool()
   val s1_uftbHasIndirect = Bool()
diff --git a/src/main/scala/xiangshan/frontend/newRAS.scala b/src/main/scala/xiangshan/frontend/newRAS.scala
index 737e8b505b1..3348cbd797a 100644
--- a/src/main/scala/xiangshan/frontend/newRAS.scala
+++ b/src/main/scala/xiangshan/frontend/newRAS.scala
@@ -582,6 +582,9 @@ class RAS(implicit p: Parameters) extends BasePredictor {
   val s3_top = RegEnable(stack.spec_pop_addr, io.s2_fire(2))
   val s3_spec_new_addr = RegEnable(s2_spec_new_addr, io.s2_fire(2))
 
+  // val s3_jalr_target = io.out.s3.full_pred.jalr_target
+  // val s3_last_target_in = io.in.bits.resp_in(0).s3.full_pred(2).targets.last
+  // val s3_last_target_out = io.out.s3.full_pred(2).targets.last
   val s3_is_jalr = io.in.bits.resp_in(0).s3.full_pred(2).is_jalr && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
   val s3_is_ret = io.in.bits.resp_in(0).s3.full_pred(2).is_ret && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
   // assert(is_jalr && is_ret || !is_ret)
```
