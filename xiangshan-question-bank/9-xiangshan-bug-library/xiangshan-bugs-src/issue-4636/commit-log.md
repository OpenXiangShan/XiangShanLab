# Commit Log
- Issue: #4636
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4636
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4636
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4636
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 6382235eb81..eb7254a894b 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1007,7 +1007,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s1_out.rep_info.debug    := s1_in.uop.debugInfo
   s1_out.rep_info.nuke     := s1_nuke && !s1_sw_prf
   s1_out.delayedLoadError  := s1_dly_err
-  s1_out.nc := s1_nc || Pbmt.isNC(s1_pbmt)
+  s1_out.nc := (s1_nc || Pbmt.isNC(s1_pbmt)) && !s1_prf
   s1_out.mmio := Pbmt.isIO(s1_pbmt)
 
   when (!s1_dly_err) {
```
