# Commit Log
- Issue: #5415
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5415
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5415
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5415
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 49dbfaae11c..0d4e57a52c3 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -493,7 +493,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   // TODO: dcache resp
   io.dcache.resp.ready := true.B
 
-  val s2_mis_align = s2_valid && RegEnable(s1_mis_align, s1_fire) && !s2_exception
+  val s2_mis_align = s2_valid && RegEnable(s1_mis_align, s1_fire)
   // goto misalignBuffer
   io.misalign_enq.revoke := s2_exception
   val s2_misalignNeedReplay = RegEnable(s1_toMisalignBufferValid && (!io.misalign_enq.req.ready || s1_misalignNeedReplay), false.B, s1_fire)
@@ -516,7 +516,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   s2_misalign_stout.bits.need_rep := RegEnable(s1_tlb_miss, s1_fire)
   io.misalign_stout := s2_misalign_stout
 
-  val s2_misalign_cango = !s2_mis_align || s2_in.isvec && s2_misalignBufferNack
+  val s2_misalign_cango = !s2_mis_align || s2_in.isvec && (s2_misalignNeedReplay || s2_exception) || !s2_in.isvec && !s2_misalignNeedReplay && s2_exception
 
   // mmio and exception
   io.lsq_replenish := s2_out
```
