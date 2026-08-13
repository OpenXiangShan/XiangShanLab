# Commit Log
- Issue: #4929
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4929
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4929
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4929
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
index 9ee6f830dda..6b5eaa2e4a9 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
@@ -42,7 +42,7 @@ trait CSRPMA { self: NewCSR =>
   )
 
   val pmaCSROutMap: SeqMap[Int, UInt] = SeqMap.from(
-    pmpCSRMods.map(csr => csr.addr -> csr.regOut.asInstanceOf[CSRBundle].asUInt).iterator
+    pmaCSRMods.map(csr => csr.addr -> csr.regOut.asInstanceOf[CSRBundle].asUInt).iterator
   )
 
   private val pmaCfgRead = Cat(pmacfgs.map(_.rdata(7, 0)).reverse)
```
