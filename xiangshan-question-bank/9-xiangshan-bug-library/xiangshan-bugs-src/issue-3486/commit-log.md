# Commit Log
- Issue: #3486
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3486
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3486
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3486
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 9a9ea93321f..5250378aaa6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -694,7 +694,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   // perf
   val addrInPerfCnt = (wen || ren) && (
     (addr >= CSRs.mcycle.U) && (addr <= CSRs.mhpmcounter31.U) ||
-    (addr >= mcountinhibit.addr.U) && (addr <= mhpmevents.last.addr.U) ||
+    (addr === mcountinhibit.addr.U) ||
     (addr >= CSRs.cycle.U) && (addr <= CSRs.hpmcounter31.U) ||
     (addr === CSRs.mip.U) || (addr === CSRs.sip.U) || (addr === CSRs.vsip.U) ||
     (addr === CSRs.hip.U) || (addr === CSRs.mvip.U) || (addr === CSRs.hvip.U) ||
```
