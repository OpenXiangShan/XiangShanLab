# Commit Log
- Issue: #3670
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3670
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3670
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3670
- Changed files: 1
- Additions: 35
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/BPU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/BPU.scala b/src/main/scala/xiangshan/frontend/BPU.scala
index 6d9236959ab..e599192c804 100644
--- a/src/main/scala/xiangshan/frontend/BPU.scala
+++ b/src/main/scala/xiangshan/frontend/BPU.scala
@@ -779,7 +779,41 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
     )
   )
 
-  val previous_s2_pred = RegEnable(resp.s2, 0.U.asTypeOf(resp.s2), s2_fire_dup(0))
+  // To optimize Clock Gating Efficiency of previous_s2_*
+  val previous_s2_pred = Wire(new BranchPredictionBundle(isNotS3 = true))
+  previous_s2_pred.pc := RegEnable(resp.s2.pc, 0.U.asTypeOf(resp.s2.pc), s2_fire_dup(0)).suggestName(
+    s"previous_s2_pred_pc"
+  )
+  previous_s2_pred.valid := RegEnable(resp.s2.valid, 0.U.asTypeOf(resp.s2.valid), s2_fire_dup(0)).suggestName(
+    s"previous_s2_pred_valid"
+  )
+  previous_s2_pred.hasRedirect := RegEnable(
+    resp.s2.hasRedirect,
+    0.U.asTypeOf(resp.s2.hasRedirect),
+    s2_fire_dup(0)
+  ).suggestName(s"previous_s2_pred_hasRedirect")
+  previous_s2_pred.ftq_idx := RegEnable(resp.s2.ftq_idx, 0.U.asTypeOf(resp.s2.ftq_idx), s2_fire_dup(0)).suggestName(
+    s"previous_s2_pred_ftq_idx"
+  )
+  previous_s2_pred.full_pred := RegEnable(
+    resp.s2.full_pred,
+    0.U.asTypeOf(resp.s2.full_pred),
+    s2_fire_dup(0)
+  ).suggestName(s"previous_s2_pred_full_pred")
+  previous_s2_pred.full_pred.zip(resp.s2.full_pred.zipWithIndex).map { case (prev_fp, (new_fp, dupIdx)) =>
+    prev_fp.targets.zip(new_fp.taken_mask_on_slot.zipWithIndex).map { case (target, (taken_mask, slotIdx)) =>
+      // This enable signal can better improve CGE, but it may lead to timing violations:
+      //    s2_fire_dup(0) && !new_fp.taken_mask_on_slot.take(slotIdx).fold(false.B)(_||_) && taken_mask && new_fp.hit
+      target := RegEnable(new_fp.targets(slotIdx), 0.U.asTypeOf(new_fp.targets(slotIdx)), s2_fire_dup(0) && taken_mask)
+    }
+    // This enable signal can better improve CGE, but it may lead to timing violations:
+    //    s2_fire_dup(0) && new_fp.hit && !new_fp.taken_mask_on_slot.reduce(_||_)
+    prev_fp.fallThroughAddr := RegEnable(
+      new_fp.fallThroughAddr,
+      0.U.asTypeOf(new_fp.fallThroughAddr),
+      s2_fire_dup(0) && resp.s2.full_pred(0).hit && !resp.s2.full_pred(0).taken_mask_on_slot(0)
+    )
+  }
 
   val s3_redirect_on_br_taken_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map { case (fp1, fp2) =>
     fp1.real_br_taken_mask().asUInt =/= fp2.real_br_taken_mask().asUInt
```
