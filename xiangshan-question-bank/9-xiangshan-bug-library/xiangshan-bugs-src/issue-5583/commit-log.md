# Commit Log
- Issue: #5583
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5583
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5583
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5583
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 190bc3119ea..7ab80b9ae74 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -657,7 +657,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   io.flushOut.bits.ftqIdx := Mux(needModifyFtqIdxOffset, firstVInstrFtqPtr, deqPtrEntry.ftqIdx)
   io.flushOut.bits.ftqOffset := Mux(needModifyFtqIdxOffset, firstVInstrFtqOffset, deqPtrEntry.ftqOffset)
   io.flushOut.bits.level := Mux(deqHasReplayInst || intrEnable || deqHasException || needModifyFtqIdxOffset, RedirectLevel.flush, RedirectLevel.flushAfter) // TODO use this to implement "exception next"
-  io.flushOut.bits.interrupt := true.B
+  io.flushOut.bits.interrupt := !isFlushPipe
   XSPerfAccumulate("flush_num", io.flushOut.valid)
   XSPerfAccumulate("interrupt_num", io.flushOut.valid && intrEnable)
   XSPerfAccumulate("exception_num", io.flushOut.valid && deqHasException)
```
