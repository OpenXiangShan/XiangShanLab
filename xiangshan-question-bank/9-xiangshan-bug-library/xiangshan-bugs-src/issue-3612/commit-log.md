# Commit Log
- Issue: #3612
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3612
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3612
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3612
- Changed files: 2
- Additions: 3
- Deletions: 3

## Files
- `src/main/resources/aia`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/resources/aia b/src/main/resources/aia
index 475284c5c2e..9464aee3c77 160000
--- a/src/main/resources/aia
+++ b/src/main/resources/aia
@@ -1 +1 @@
-Subproject commit 475284c5c2e5ea5744aa5ed71ea29d2cf5a9b8cd
+Subproject commit 9464aee3c77b021a037f28aa1b3fe53c71b516f6
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index e0773415d44..8ae8829309e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -859,9 +859,9 @@ class NewCSR(implicit val p: Parameters) extends Module
    * Asynchronous read operation of CSR. Check whether a read is asynchronous when read-enable is high.
    * AIA registers are designed to be read asynchronously, so newCSR will wait for response.
    **/
-  private val asyncRead = ren && (
+  private val asyncRead = ren && !(permitMod.io.out.EX_II || permitMod.io.out.EX_VI) && (
     mireg.addr.U === addr && miselect.inIMSICRange ||
-    sireg.addr.U === addr && siselect.inIMSICRange ||
+    sireg.addr.U === addr && ((!V.asUInt.asBool && siselect.inIMSICRange) || (V.asUInt.asBool && vsiselect.inIMSICRange)) ||
     vsireg.addr.U === addr && vsiselect.inIMSICRange
   )
```
