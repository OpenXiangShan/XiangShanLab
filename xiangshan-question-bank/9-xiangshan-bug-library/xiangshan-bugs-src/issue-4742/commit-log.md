# Commit Log
- Issue: #4742
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4742
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4742
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4742
- Changed files: 1
- Additions: 5
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 94cb1b08e09..74387f849fb 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -932,6 +932,11 @@ class NewCSR(implicit val p: Parameters) extends Module
   val addrInPerfCnt = (wenLegal || ren) && (
     (addr >= CSRs.mcycle.U) && (addr <= CSRs.mhpmcounter31.U) ||
     (addr >= CSRs.cycle.U) && (addr <= CSRs.hpmcounter31.U)
+  ) || 
+  ren && (
+    (addr === CSRs.vstopi.U) ||
+    (addr === CSRs.stopi.U) || 
+    (addr === CSRs.mtopi.U)
   )
 
   val resetSatp = WireInit(false.B)
```
