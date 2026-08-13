# Commit Log
- Issue: #5327
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5327
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5327
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5327
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index f4e4bd2f624..49b2daed6ff 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -691,7 +691,8 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
       // special case: dcache miss
       when (replayInfo.cause(LoadReplayCauses.C_DM) && enq.bits.handledByMSHR) {
         blocking(enqIndex) := !replayInfo.full_fwd && //  dcache miss
-                              !(io.tl_d_channel.valid && io.tl_d_channel.mshrid === replayInfo.mshr_id) // no refill in this cycle
+                              !(io.tl_d_channel.valid && io.tl_d_channel.mshrid === replayInfo.mshr_id) && // no refill in this cycle
+                              !(RegNext(io.tl_d_channel.valid) && RegNext(io.tl_d_channel.mshrid) === replayInfo.mshr_id) // not refill in last cycle
       }
 
       // special case: st-ld violation
```
