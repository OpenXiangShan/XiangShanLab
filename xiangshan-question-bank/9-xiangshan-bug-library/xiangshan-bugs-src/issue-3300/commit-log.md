# Commit Log
- Issue: #3300
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3300
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3300
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3300
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index c3ab2877d19..342d54c2728 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -688,7 +688,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     (addr >= CSRs.mcycle.U) && (addr <= CSRs.mhpmcounter31.U) ||
     (addr >= mcountinhibit.addr.U) && (addr <= mhpmevents.last.addr.U) ||
     (addr >= CSRs.cycle.U) && (addr <= CSRs.hpmcounter31.U) ||
-    (addr === CSRs.mip.U) ||
+    (addr === CSRs.mip.U) || (addr === CSRs.sip.U) || (addr === CSRs.vsip.U) ||
+    (addr === CSRs.hip.U) || (addr === CSRs.mvip.U) || (addr === CSRs.hvip.U) ||
     Cat(aiaSkipCSRs.map(_.addr.U === addr)).orR ||
     (addr === CSRs.stimecmp.U) ||
     (addr === CSRs.mcounteren.U) ||
```
