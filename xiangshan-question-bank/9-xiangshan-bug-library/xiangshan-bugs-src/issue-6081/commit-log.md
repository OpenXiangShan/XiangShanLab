# Commit Log
- Issue: #6081
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6081
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6081
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6081
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index bdf99707edf..fc7355aa10f 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -893,7 +893,7 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
     cboState := cboStateNext
 
     private val cboCanHandle = headCtrlEntry.allValid && !headCtrlEntry.hasException && headCtrlEntry.allocated &&
-      headCtrlEntry.isCbo
+      headCtrlEntry.isCbo && headCtrlEntry.committed
 
     switch(cboState) {
       is(CboState.idle) {
```
