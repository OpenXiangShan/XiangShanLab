# Commit Log
- Issue: #5674
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5674
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5674
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5674
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 0952892788a..8c27aa208f1 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -498,7 +498,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
 
   val s2_mis_align = s2_valid && RegEnable(s1_mis_align, s1_fire)
   // goto misalignBuffer
-  io.misalign_enq.revoke := s2_exception || s2_in.tlbMiss
+  io.misalign_enq.revoke := s2_exception || RegNext(s1_tlb_miss)
   val s2_misalignNeedReplay = RegEnable(s1_toMisalignBufferValid && (!io.misalign_enq.req.ready || s1_misalignNeedReplay), false.B, s1_fire)
   val s2_misalignBufferNack = !io.misalign_enq.revoke && s2_misalignNeedReplay
```
