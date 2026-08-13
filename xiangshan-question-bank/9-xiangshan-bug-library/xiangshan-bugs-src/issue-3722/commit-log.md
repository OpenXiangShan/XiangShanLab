# Commit Log
- Issue: #3722
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3722
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3722
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3722
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 6876f40ed93..e8443fa894e 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -549,7 +549,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val deqHasException = deqNeedFlushAndHitExceptionGenState && exceptionGenStateIsException
   val deqHasFlushPipe = deqNeedFlushAndHitExceptionGenState && exceptionDataRead.bits.flushPipe && !deqHasException && (!deqPtrEntry.isVls || RegNext(RegNext(deqPtrEntry.commit_w)))
   val deqHasReplayInst = deqNeedFlushAndHitExceptionGenState && exceptionDataRead.bits.replayInst
-  val deqIsVlsException = deqHasException && deqPtrEntry.isVls
+  val deqIsVlsException = deqHasException && deqPtrEntry.isVls && !Cat(ExceptionNO.selectFrontend(exceptionDataRead.bits.exceptionVec)).orR
   // delay 2 cycle wait exceptionGen out
   deqVlsCanCommit := RegNext(RegNext(deqIsVlsException && deqPtrEntry.commit_w))
```
