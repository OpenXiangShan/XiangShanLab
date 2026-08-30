# Commit Log
- Issue: #5300
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5300
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5300
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5300
- Changed files: 1
- Additions: 6
- Deletions: 6

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 8f444063835..367f6e2e502 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -305,20 +305,20 @@ class LoadUnit(implicit p: Parameters) extends XSModule
                                io.vecldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.vecldin.bits.uop.lqIdx)
   private val SRC_NUM = 11
   private val Seq(
-    mab_idx, super_rep_idx, fast_rep_idx, mmio_idx, nc_idx, lsq_rep_idx,
-    high_pf_idx, vec_iss_idx, int_iss_idx, l2l_fwd_idx, low_pf_idx
+    mab_idx, super_rep_idx, fast_rep_idx, lsq_rep_idx, high_pf_idx,
+    vec_iss_idx, int_iss_idx, mmio_idx, nc_idx, l2l_fwd_idx, low_pf_idx
   ) = (0 until SRC_NUM).toSeq
   // load flow source valid
   val s0_src_valid_vec = WireInit(VecInit(Seq(
     io.misalign_ldin.valid,
     io.replay.valid && io.replay.bits.forward_tlDchannel,
     io.fast_rep_in.valid,
-    io.lsq.uncache.valid,
-    io.lsq.nc_ldin.valid,
     io.replay.valid && !io.replay.bits.forward_tlDchannel && !s0_rep_stall,
     io.prefetch_req.valid && io.prefetch_req.bits.confidence > 0.U,
     io.vecldin.valid,
     io.ldin.valid, // int flow first issue or software prefetch
+    io.lsq.uncache.valid,
+    io.lsq.nc_ldin.valid,
     io.l2l_fwd_in.valid,
     io.prefetch_req.valid && io.prefetch_req.bits.confidence === 0.U,
   )))
@@ -674,12 +674,12 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     fromMisAlignBufferSource(io.misalign_ldin.bits),
     fromNormalReplaySource(io.replay.bits),
     fromFastReplaySource(io.fast_rep_in.bits),
-    fromMmioSource(io.lsq.uncache.bits),
-    fromNcSource(io.lsq.nc_ldin.bits),
     fromNormalReplaySource(io.replay.bits),
     fromPrefetchSource(io.prefetch_req.bits),
     fromVecIssueSource(io.vecldin.bits),
     fromIntIssueSource(io.ldin.bits),
+    fromMmioSource(io.lsq.uncache.bits),
+    fromNcSource(io.lsq.nc_ldin.bits),
     (if (EnableLoadToLoadForward) fromLoadToLoadSource(io.l2l_fwd_in) else fromNullSource()),
     fromPrefetchSource(io.prefetch_req.bits)
   )
```
