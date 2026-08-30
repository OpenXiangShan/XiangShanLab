# Commit Log
- Issue: #3720
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3720
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3720
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3720
- Changed files: 1
- Additions: 3
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 25e9d14650f..6876f40ed93 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -555,7 +555,8 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
   // lock at assertion of deqVlsExceptionNeedCommit until condition not assert
   val deqVlsExcpLock = RegInit(false.B)
-  when(deqIsVlsException && deqVlsCanCommit && !deqVlsExcpLock) {
+  val handleVlsExcp = deqIsVlsException && deqVlsCanCommit && !deqVlsExcpLock && state === s_idle
+  when(handleVlsExcp) {
     deqVlsExcpLock := true.B
   }.elsewhen(deqPtrVec.head =/= deqPtrVec_next.head) {
     deqVlsExcpLock := false.B
@@ -564,7 +565,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   // Only assert once when deqVlsExcp occurs until condition not assert to avoid multi message passed to RAB
   when (deqVlsExceptionNeedCommit) {
     deqVlsExceptionNeedCommit := false.B
-  }.elsewhen(deqIsVlsException && deqVlsCanCommit && !deqVlsExcpLock){
+  }.elsewhen(handleVlsExcp){
     deqVlsExceptionCommitSize := deqPtrEntry.realDestSize
     deqVlsExceptionNeedCommit := true.B
   }
```
