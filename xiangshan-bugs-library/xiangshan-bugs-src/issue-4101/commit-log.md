# Commit Log
- Issue: #4101
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4101
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4101
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4101
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 50edbab5a58..3a0e5fddb7f 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -292,7 +292,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // src 9: load try pointchaising when no issued or replayed load (io.fastpath)
   // src10: hardware prefetch from prefetchor (high confidence) (io.prefetch)
   // priority: high to low
-  val s0_rep_stall           = io.ldin.valid && isAfter(io.replay.bits.uop.robIdx, io.ldin.bits.uop.robIdx)
+  val s0_rep_stall           = io.ldin.valid && isAfter(io.replay.bits.uop.robIdx, io.ldin.bits.uop.robIdx) ||
+                               io.vecldin.valid && isAfter(io.replay.bits.uop.robIdx, io.vecldin.bits.uop.robIdx)
   private val SRC_NUM = 11
   private val Seq(
     mab_idx, super_rep_idx, fast_rep_idx, mmio_idx, nc_idx, lsq_rep_idx,
```
