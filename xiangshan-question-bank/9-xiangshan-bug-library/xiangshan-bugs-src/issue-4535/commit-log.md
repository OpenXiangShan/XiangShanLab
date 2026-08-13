# Commit Log
- Issue: #4535
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4535
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4535
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4535
- Changed files: 1
- Additions: 4
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index d999d89365a..64c9ada9b10 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -379,7 +379,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   vtypeBuffer.io.fromRob.walkSize := PopCount(walkIsVTypeVec)
   vtypeBuffer.io.snpt := io.snpt
   vtypeBuffer.io.snpt.snptEnq := snptEnq
-  io.toDecode.isResumeVType := vtypeBuffer.io.toDecode.isResumeVType
   io.toDecode.walkToArchVType := vtypeBuffer.io.toDecode.walkToArchVType
   io.toDecode.commitVType := vtypeBuffer.io.toDecode.commitVType
   io.toDecode.walkVType := vtypeBuffer.io.toDecode.walkVType
@@ -604,10 +603,13 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
   val isFlushPipe = deqPtrEntry.commit_w && (deqHasFlushPipe || deqHasReplayInst)
 
-  val isVsetFlushPipe = deqPtrEntry.commit_w && deqHasFlushPipe && exceptionDataRead.bits.isVset
+  // vsetvl instruction need another one cycle to write to vtype gen
+  val isVsetFlushPipe = deqPtrEntry.commit_w && deqHasFlushed && exceptionDataRead.bits.isVset
+  val isVsetFlushPipeReg = RegNext(isVsetFlushPipe)
   //  val needModifyFtqIdxOffset = isVsetFlushPipe && (vsetvlState === vs_waitFlush)
   val needModifyFtqIdxOffset = false.B
   io.isVsetFlushPipe := isVsetFlushPipe
+  io.toDecode.isResumeVType := vtypeBuffer.io.toDecode.isResumeVType || isVsetFlushPipeReg
   // io.flushOut will trigger redirect at the next cycle.
   // Block any redirect or commit at the next cycle.
   val lastCycleFlush = RegNext(io.flushOut.valid)
```
