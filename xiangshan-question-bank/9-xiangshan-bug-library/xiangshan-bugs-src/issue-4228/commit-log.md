# Commit Log
- Issue: #4228
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4228
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4228
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4228
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 17057016e1f..427dc2a2527 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -307,7 +307,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   .elsewhen (s1_kill) { s1_valid := false.B }
 
   // st-ld violation dectect request.
-  io.stld_nuke_query.valid       := s1_valid && !s1_tlb_miss && !s1_in.isHWPrefetch && !s1_frm_mabuf
+  io.stld_nuke_query.valid       := s1_valid && !s1_tlb_miss && !s1_in.isHWPrefetch
   io.stld_nuke_query.bits.robIdx := s1_in.uop.robIdx
   io.stld_nuke_query.bits.paddr  := s1_paddr
   io.stld_nuke_query.bits.mask   := s1_in.mask
```
