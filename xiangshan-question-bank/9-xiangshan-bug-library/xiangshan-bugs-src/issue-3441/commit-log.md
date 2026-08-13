# Commit Log
- Issue: #3441
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3441
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3441
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3441
- Changed files: 1
- Additions: 6
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index dc68498849a..5aab24670e2 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -849,9 +849,12 @@ class NewCSR(implicit val p: Parameters) extends Module
   /**
    * debug_begin
    */
-
-  val tdata1Update  = tdata1.w.wen
-  val tdata2Update  = tdata2.w.wen
+  val tdata1Selected = Wire(new Tdata1Bundle)
+  tdata1Selected := tdata1.rdata
+  val dmodeInSelectedTrigger = tdata1Selected.DMODE.asBool
+  val triggerCanWrite = dmodeInSelectedTrigger && debugMode || !dmodeInSelectedTrigger
+  val tdata1Update  = tdata1.w.wen && triggerCanWrite
+  val tdata2Update  = tdata2.w.wen && triggerCanWrite
   val tdata1Vec = tdata1RegVec.map{ mod => {
     val tdata1Wire = Wire(new Tdata1Bundle)
     tdata1Wire := mod.rdata
```
