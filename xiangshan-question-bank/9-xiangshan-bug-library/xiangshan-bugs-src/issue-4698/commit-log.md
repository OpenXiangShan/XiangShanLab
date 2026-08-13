# Commit Log
- Issue: #4698
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4698
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4698
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4698
- Changed files: 1
- Additions: 12
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
index db725166f17..a9edbd3fa8c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
@@ -23,10 +23,22 @@ object CSROoORead {
     CSRs.mireg,
     CSRs.sireg,
     CSRs.vsireg,
+    CSRs.mtopi,
+    CSRs.stopi,
+    CSRs.vstopi,
+    CSRs.mtopei,
+    CSRs.stopei,
+    CSRs.vstopei,
   )
   val blockBackwardInOrderCsrReadList = List(
     CSRs.mireg,
     CSRs.sireg,
     CSRs.vsireg,
+    CSRs.mtopi,
+    CSRs.stopi,
+    CSRs.vstopi,
+    CSRs.mtopei,
+    CSRs.stopei,
+    CSRs.vstopei,
   )
 }
```
