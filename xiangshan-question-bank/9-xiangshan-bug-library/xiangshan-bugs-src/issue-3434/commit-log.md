# Commit Log
- Issue: #3434
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3434
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3434
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3434
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 44bbfec6301..7bbafb26c55 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -62,7 +62,7 @@ trait HypervisorLevel { self: NewCSR =>
     .setAddr(CSRs.hvictl)
 
   val henvcfg = Module(new CSRModule("Henvcfg", new HEnvCfg) with HasHypervisorEnvBundle {
-    when (!menvcfg.STCE.asBool && !privState.isModeM && accessStimecmp) {
+    when (!menvcfg.STCE.asBool) {
       regOut.STCE := 0.U
     }
   })
```
