# Commit Log
- Issue: #4075
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4075
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4075
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4075
- Changed files: 3
- Additions: 12
- Deletions: 10

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/rob/Rab.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 1a196f1e762..9af154e3aa6 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -732,20 +732,22 @@ class CtrlBlockImp(
   io.toVecExcpMod.ratOldPest match {
     case fromRat =>
       (0 until RabCommitWidth).foreach { idx =>
-        fromRat.v0OldVdPdest(idx).valid := RegNext(
+        val v0Valid = RegNext(
           rat.io.rabCommits.isCommit &&
           rat.io.rabCommits.isWalk &&
           rat.io.rabCommits.commitValid(idx) &&
           rat.io.rabCommits.info(idx).v0Wen
         )
-        fromRat.v0OldVdPdest(idx).bits := rat.io.v0_old_pdest(idx)
-        fromRat.vecOldVdPdest(idx).valid := RegNext(
+        fromRat.v0OldVdPdest(idx).valid := RegNext(v0Valid)
+        fromRat.v0OldVdPdest(idx).bits := RegEnable(rat.io.v0_old_pdest(idx), v0Valid)
+        val vecValid = RegNext(
           rat.io.rabCommits.isCommit &&
           rat.io.rabCommits.isWalk &&
           rat.io.rabCommits.commitValid(idx) &&
           rat.io.rabCommits.info(idx).vecWen
         )
-        fromRat.vecOldVdPdest(idx).bits := rat.io.vec_old_pdest(idx)
+        fromRat.vecOldVdPdest(idx).valid := RegNext(vecValid)
+        fromRat.vecOldVdPdest(idx).bits := RegEnable(rat.io.vec_old_pdest(idx), vecValid)
       }
   }
 
diff --git a/src/main/scala/xiangshan/backend/rob/Rab.scala b/src/main/scala/xiangshan/backend/rob/Rab.scala
index 34d8c106e01..d9ce79a8469 100644
--- a/src/main/scala/xiangshan/backend/rob/Rab.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rab.scala
@@ -271,12 +271,12 @@ class RenameBuffer(size: Int)(implicit p: Parameters) extends XSModule with HasC
   io.status.commitEnd := commitEndNext
 
   for (i <- 0 until RabCommitWidth) {
-    io.toVecExcpMod.logicPhyRegMap(i).valid := (state === s_special_walk) && vecLoadExcp.valid &&
-      io.commits.commitValid(i)
+    val valid = (state === s_special_walk) && vecLoadExcp.valid && io.commits.commitValid(i)
+    io.toVecExcpMod.logicPhyRegMap(i).valid := RegNext(valid)
     io.toVecExcpMod.logicPhyRegMap(i).bits match {
       case x =>
-        x.lreg := io.commits.info(i).ldest
-        x.preg := io.commits.info(i).pdest
+        x.lreg := RegEnable(io.commits.info(i).ldest, valid)
+        x.preg := RegEnable(io.commits.info(i).pdest, valid)
     }
   }
 
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 75e27abf3c5..a83eabd6fa9 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -565,7 +565,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val deqHitExceptionGenState = exceptionDataRead.valid && exceptionDataRead.bits.robIdx === deqPtr
   val deqNeedFlushAndHitExceptionGenState = deqNeedFlush && deqHitExceptionGenState
   val exceptionGenStateIsException = exceptionDataRead.bits.exceptionVec.asUInt.orR || exceptionDataRead.bits.singleStep || TriggerAction.isDmode(exceptionDataRead.bits.trigger)
-  val deqHasException = deqNeedFlushAndHitExceptionGenState && exceptionGenStateIsException
+  val deqHasException = deqNeedFlushAndHitExceptionGenState && exceptionGenStateIsException && (!deqPtrEntry.isVls || RegNext(RegNext(deqPtrEntry.commit_w)))
   val deqHasFlushPipe = deqNeedFlushAndHitExceptionGenState && exceptionDataRead.bits.flushPipe && !deqHasException && (!deqPtrEntry.isVls || RegNext(RegNext(deqPtrEntry.commit_w)))
   val deqHasReplayInst = deqNeedFlushAndHitExceptionGenState && exceptionDataRead.bits.replayInst
   val deqIsVlsException = deqHasException && deqPtrEntry.isVls && !exceptionDataRead.bits.isEnqExcp
@@ -763,7 +763,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   for (i <- 0 until CommitWidth) {
     // defaults: state === s_idle and instructions commit
     // when intrBitSetReg, allow only one instruction to commit at each clock cycle
-    val isBlocked = intrEnable || (deqNeedFlush && !deqHasFlushed && !deqHasFlushPipe)
+    val isBlocked = intrEnable || (deqNeedFlush && !deqHasFlushed)
     val isBlockedByOlder = if (i != 0) commit_block.asUInt(i, 0).orR || allowOnlyOneCommit && !hasCommitted.asUInt(i - 1, 0).andR else false.B
     commitValidThisLine(i) := commit_vDeqGroup(i) && commit_wDeqGroup(i) && !isBlocked && !isBlockedByOlder && !hasCommitted(i)
     io.commits.info(i) := commitInfo(i)
```
