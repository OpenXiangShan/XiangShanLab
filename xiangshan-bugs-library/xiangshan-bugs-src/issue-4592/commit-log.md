# Commit Log
- Issue: #4592
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4592
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4592
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4592
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 4f5882ae65f..cc276bba92b 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -114,7 +114,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s0_rob_idx      = Mux(s0_use_non_prf_flow, s0_uop.robIdx, 0.U.asTypeOf(s0_uop.robIdx))
   val s0_pc           = Mux(s0_use_non_prf_flow, s0_uop.pc, 0.U)
   val s0_instr_type   = Mux(s0_use_non_prf_flow, STORE_SOURCE.U, DCACHE_PREFETCH_SOURCE.U)
-  val s0_wlineflag    = Mux(s0_use_flow_rs, s0_uop.fuOpType === LSUOpType.cbo_zero, false.B)
+  val s0_wlineflag    = Mux(s0_use_flow_rs, LSUOpType.isCboAll(s0_uop.fuOpType), false.B)
   val s0_out          = Wire(new LsPipelineBundle)
   val s0_kill         = s0_uop.robIdx.needFlush(io.redirect)
   val s0_can_go       = s1_ready
```
