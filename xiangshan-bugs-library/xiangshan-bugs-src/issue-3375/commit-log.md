# Commit Log
- Issue: #3375
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3375
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3375
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3375
- Changed files: 2
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
index ddde5ede6ea..89a878d857c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
@@ -102,7 +102,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   ))
 
   private val tval2 = Mux1H(Seq(
-    (isFetchGuestExcp                  ) -> trapPC,
+    (isFetchGuestExcp                  ) -> trapPCGPA,
     (isFetchGuestExcp && fetchCrossPage) -> (trapPCGPA + 2.U),
     (isLSGuestExcp                     ) -> trapMemGPA,
   ))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
index ece7ee77e7c..38317365f7e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
@@ -100,7 +100,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   ))
 
   private val tval2 = Mux1H(Seq(
-    (isFetchGuestExcp                  ) -> trapPC,
+    (isFetchGuestExcp                  ) -> trapPCGPA,
     (isFetchGuestExcp && fetchCrossPage) -> (trapPCGPA + 2.U),
     (isLSGuestExcp                     ) -> trapMemGPA,
   ))
```
