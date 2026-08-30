# Commit Log
- Issue: #3397
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3397
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3397
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3397
- Changed files: 2
- Additions: 6
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
index 89a878d857c..ee556139c34 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
@@ -102,9 +102,9 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   ))
 
   private val tval2 = Mux1H(Seq(
-    (isFetchGuestExcp                  ) -> trapPCGPA,
-    (isFetchGuestExcp && fetchCrossPage) -> (trapPCGPA + 2.U),
-    (isLSGuestExcp                     ) -> trapMemGPA,
+    (isFetchGuestExcp && !fetchCrossPage) -> trapPCGPA,
+    (isFetchGuestExcp && fetchCrossPage ) -> (trapPCGPA + 2.U),
+    (isLSGuestExcp                      ) -> trapMemGPA,
   ))
 
   out := DontCare
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
index 38317365f7e..18d6b384c99 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
@@ -100,9 +100,9 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   ))
 
   private val tval2 = Mux1H(Seq(
-    (isFetchGuestExcp                  ) -> trapPCGPA,
-    (isFetchGuestExcp && fetchCrossPage) -> (trapPCGPA + 2.U),
-    (isLSGuestExcp                     ) -> trapMemGPA,
+    (isFetchGuestExcp && !fetchCrossPage) -> trapPCGPA,
+    (isFetchGuestExcp && fetchCrossPage ) -> (trapPCGPA + 2.U),
+    (isLSGuestExcp                      ) -> trapMemGPA,
   ))
 
   out := DontCare
```
