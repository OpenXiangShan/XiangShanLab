# Commit Log
- Issue: #4359
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4359
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4359
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4359
- Changed files: 1
- Additions: 4
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 3d0f1fe60d8..c4f48af206b 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -330,7 +330,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     s0_src_select_vec(fast_rep_idx) || s0_src_select_vec(mmio_idx) ||
     s0_src_select_vec(nc_idx)
   s0_valid := !s0_kill && (s0_src_select_vec(nc_idx) || ((
-    s0_src_valid_vec(mab_idx) && !io.misalign_ldin.bits.misalignNeedWakeUp ||
+    s0_src_valid_vec(mab_idx) ||
     s0_src_valid_vec(super_rep_idx) ||
     s0_src_valid_vec(fast_rep_idx) ||
     s0_src_valid_vec(lsq_rep_idx) ||
@@ -339,7 +339,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     s0_src_valid_vec(int_iss_idx) ||
     s0_src_valid_vec(l2l_fwd_idx) ||
     s0_src_valid_vec(low_pf_idx)
-  ) && !s0_src_select_vec(mmio_idx) && io.dcache.req.ready))
+  ) && !s0_src_select_vec(mmio_idx) && io.dcache.req.ready &&
+    !(io.misalign_ldin.fire && io.misalign_ldin.bits.misalignNeedWakeUp) // Currently, misalign is the highest priority
+  ))
 
   s0_mmio_select := s0_src_select_vec(mmio_idx) && !s0_kill
   s0_nc_select := s0_src_select_vec(nc_idx) && !s0_kill
```
