# Commit Log
- Issue: #5131
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5131
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5131
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5131
- Changed files: 1
- Additions: 12
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
index a9edbd3fa8c..ab0e0a37783 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
@@ -29,6 +29,12 @@ object CSROoORead {
     CSRs.mtopei,
     CSRs.stopei,
     CSRs.vstopei,
+    CSRs.mip,
+    CSRs.sip,
+    CSRs.vsip,
+    CSRs.hip,
+    CSRs.hvip,
+    CSRs.mvip,
   )
   val blockBackwardInOrderCsrReadList = List(
     CSRs.mireg,
@@ -40,5 +46,11 @@ object CSROoORead {
     CSRs.mtopei,
     CSRs.stopei,
     CSRs.vstopei,
+    CSRs.mip,
+    CSRs.sip,
+    CSRs.vsip,
+    CSRs.hip,
+    CSRs.hvip,
+    CSRs.mvip,
   )
 }
```
