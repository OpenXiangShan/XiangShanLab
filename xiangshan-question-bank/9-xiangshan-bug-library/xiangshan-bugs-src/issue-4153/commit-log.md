# Commit Log
- Issue: #4153
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4153
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4153
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4153
- Changed files: 1
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index a83eabd6fa9..f5e8e82e849 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -932,11 +932,11 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val enqOH = VecInit(canEnqueue.zip(allocatePtrVec.map(_.value === i.U)).map(x => x._1 && x._2))
     val commitCond = io.commits.isCommit && io.commits.commitValid.zip(deqPtrVec.map(_.value === i.U)).map(x => x._1 && x._2).reduce(_ || _)
     assert(PopCount(enqOH) < 2.U, s"robEntries$i enqOH is not one hot")
-    val needFlush = redirectValidReg && Mux(
-      (redirectEnd > redirectBegin) && !redirectAll,
+    val needFlush = redirectValidReg && (Mux(
+      redirectEnd > redirectBegin,
       (i.U > redirectBegin) && (i.U < redirectEnd),
       (i.U > redirectBegin) || (i.U < redirectEnd)
-    )
+    ) || redirectAll)
     when(commitCond) {
       robEntries(i).valid := false.B
     }.elsewhen(enqOH.asUInt.orR && !io.redirect.valid) {
```
