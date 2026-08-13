# Commit Log
- Issue: #4807
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4807
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4807
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4807
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index dedc2bac2e0..dc1570957cb 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -509,6 +509,8 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   s2_misalign_stout.bits.need_rep := RegEnable(s1_tlb_miss, s1_fire)
   io.misalign_stout := s2_misalign_stout
 
+  val s2_misalign_cango = !s2_mis_align || s2_in.isvec && s2_misalignBufferNack
+
   // mmio and exception
   io.lsq_replenish := s2_out
   io.lsq_replenish.af := s2_out.af && s2_valid && !s2_kill
@@ -565,7 +567,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s3_exception     = RegEnable(s2_exception, s2_fire)
 
   // store misalign will not writeback to rob now
-  when (s2_fire) { s3_valid := (!s2_mmio && !s2_isCbo || s2_exception) && !s2_out.isHWPrefetch && !s2_mis_align && !s2_frm_mabuf }
+  when (s2_fire) { s3_valid := (!s2_mmio && !s2_isCbo || s2_exception) && !s2_out.isHWPrefetch && s2_misalign_cango && !s2_frm_mabuf }
   .elsewhen (s3_fire) { s3_valid := false.B }
   .elsewhen (s3_kill) { s3_valid := false.B }
```
