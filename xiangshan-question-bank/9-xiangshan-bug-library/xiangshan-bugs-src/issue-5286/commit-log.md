# Commit Log
- Issue: #5286
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5286
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5286
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5286
- Changed files: 1
- Additions: 20
- Deletions: 0

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 33517e4a218..0cf820ea963 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1853,5 +1853,25 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
     s3_picked_data_frm_pipe.map(x=> dontTouch(x))
   }
 
+  // perfcct load trace
+  val recordLoadReplayEn = s3_valid && !s3_kill && s3_replayqueue_rep_cause.asUInt.orR && !s3_in.feedbacked
+  val recordLoadReplayCause = ParallelPriorityMux(Seq(
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_MA)  -> PerfCCT.ReplayReason.Nuke.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_TM)  -> PerfCCT.ReplayReason.TLBMiss.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_DM)  -> PerfCCT.ReplayReason.CacheMiss.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_RAR) -> PerfCCT.ReplayReason.RARReplay.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_RAW) -> PerfCCT.ReplayReason.RAWReplay.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_BC)  -> PerfCCT.ReplayReason.BankConflict.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_FF)  -> PerfCCT.ReplayReason.STDForwardFail.id.U,
+      s3_replayqueue_rep_cause(LoadReplayCauses.C_DR)  -> PerfCCT.ReplayReason.DcacheStall.id.U,
+      true.B                                           -> PerfCCT.ReplayReason.OtherReplay.id.U
+  ))
+  val recordLoadAddrEn = io.ldout.fire && !s3_kill
+
+  val timer = GTimer()
+  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.ReplayStr.id.U, recordLoadReplayCause, recordLoadReplayEn, clock, reset)
+  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.LastReplay.id.U, timer, recordLoadReplayEn, clock, reset)
+  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.VAddress.id.U, s3_in.vaddr, recordLoadAddrEn, clock, reset)
+  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.PAddress.id.U, s3_in.paddr, recordLoadAddrEn, clock, reset)
   // end
 }
```
