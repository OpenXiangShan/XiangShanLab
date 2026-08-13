# Commit Log
- Issue: #4292
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4292
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4292
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4292
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index a83065123fc..129d85912b8 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1298,7 +1298,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s2_real_exceptionVec(loadAddrMisaligned) := s2_out.isMisalign && s2_check_mmio
   s2_real_exceptionVec(loadAccessFault) := s2_exception_vec(loadAccessFault) ||
     s2_fwd_frm_d_chan && s2_d_corrupt ||
-    s2_fwd_frm_mshr && s2_mshr_corrupt
+    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_corrupt
   val s2_real_exception = s2_vecActive &&
     (s2_trigger_debug_mode || ExceptionNO.selectByFu(s2_real_exceptionVec, LduCfg).asUInt.orR)
```
