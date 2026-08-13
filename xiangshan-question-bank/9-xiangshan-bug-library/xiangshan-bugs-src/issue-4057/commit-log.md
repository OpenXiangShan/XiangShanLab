# Commit Log
- Issue: #4057
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4057
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4057
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4057
- Changed files: 1
- Additions: 5
- Deletions: 5

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index ec9875c07a0..d02cf383152 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1281,11 +1281,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   val s2_data_fwded = s2_dcache_miss && s2_full_fwd
 
-  val s2_fwd_vp_match_invalid = io.lsq.forward.matchInvalid || io.sbuffer.matchInvalid || io.ubuffer.matchInvalid
-  val s2_vp_match_fail = s2_fwd_vp_match_invalid && s2_troublem
-  val s2_safe_wakeup = !s2_out.rep_info.need_rep && !s2_mmio && (!s2_in.nc || s2_nc_with_data) && !s2_mis_align && !s2_exception || s2_in.misalignNeedWakeUp // don't need to replay and is not a mmio\misalign no data
-  val s2_safe_writeback = s2_exception || s2_safe_wakeup || s2_vp_match_fail || s2_in.misalignNeedWakeUp
-
   // For misaligned, we will keep the misaligned exception at S2 and before.
   // Here a judgement is made as to whether a misaligned exception needs to actually be generated.
   // We will generate misaligned exceptions at mmio.
@@ -1294,6 +1289,11 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s2_real_exception = s2_vecActive &&
     (s2_trigger_debug_mode || ExceptionNO.selectByFu(s2_real_exceptionVec, LduCfg).asUInt.orR)
 
+  val s2_fwd_vp_match_invalid = io.lsq.forward.matchInvalid || io.sbuffer.matchInvalid || io.ubuffer.matchInvalid
+  val s2_vp_match_fail = s2_fwd_vp_match_invalid && s2_troublem
+  val s2_safe_wakeup = !s2_out.rep_info.need_rep && !s2_mmio && (!s2_in.nc || s2_nc_with_data) && !s2_mis_align && !s2_real_exception || s2_in.misalignNeedWakeUp // don't need to replay and is not a mmio\misalign no data
+  val s2_safe_writeback = s2_real_exception || s2_safe_wakeup || s2_vp_match_fail || s2_in.misalignNeedWakeUp
+
   // ld-ld violation require
   io.lsq.ldld_nuke_query.req.valid           := s2_valid && s2_can_query
   io.lsq.ldld_nuke_query.req.bits.uop        := s2_in.uop
```
