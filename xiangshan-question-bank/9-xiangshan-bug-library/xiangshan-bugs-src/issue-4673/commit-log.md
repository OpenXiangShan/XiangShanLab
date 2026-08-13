# Commit Log
- Issue: #4673
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4673
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4673
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4673
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 772f8b04bbd..5f8aceccc28 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -774,7 +774,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // TODO: prefetch need writeback to loadQueueFlag
   s0_out               := DontCare
   s0_out.vaddr         := Mux(s0_nc_with_data, s0_sel_src.vaddr, s0_dcache_vaddr)
-  s0_out.fullva        := s0_tlb_fullva
+  s0_out.fullva        := Mux(s0_sel_src.frm_mabuf, s0_out.vaddr, s0_tlb_fullva)
   s0_out.mask          := s0_sel_src.mask
   s0_out.uop           := s0_sel_src.uop
   s0_out.isFirstIssue  := s0_sel_src.isFirstIssue
```
