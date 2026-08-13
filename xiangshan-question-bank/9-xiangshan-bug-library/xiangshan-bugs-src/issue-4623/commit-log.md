# Commit Log
- Issue: #4623
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4623
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4623
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4623
- Changed files: 1
- Additions: 8
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index da8b78de2b4..afaed355798 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -399,12 +399,14 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   io.cpu_halt := hasWFI
   // WFI Timeout: 2^20 = 1M cycles
   val wfi_cycles = RegInit(0.U(20.W))
-  when(hasWFI) {
-    wfi_cycles := wfi_cycles + 1.U
-  }.elsewhen(!hasWFI && RegNext(hasWFI)) {
-    wfi_cycles := 0.U
+  if (wfiResume) {
+    when(hasWFI) {
+      wfi_cycles := wfi_cycles + 1.U
+    }.elsewhen(!hasWFI && RegNext(hasWFI)) {
+      wfi_cycles := 0.U
+    }
   }
-  val wfi_timeout = if (wfiResume) wfi_cycles.andR else false.B
+  val wfi_timeout = wfi_cycles.andR
   when(RegNext(RegNext(io.csr.wfiEvent)) || io.flushOut.valid || wfi_timeout) {
     hasWFI := false.B
   }
@@ -1620,7 +1622,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     commitStuckCycle := 0.U
   }
   // check if stuck > 2^maxCommitStuckCycle
-  val commitStuck_overflow = commitStuckCycle.andR
+  val commitStuck_overflow = commitStuckCycle.andR && (if (wfiResume) true.B else (!hasWFI))
   val criticalErrors = Seq(
     ("rob_commit_stuck  ", commitStuck_overflow),
   )
```
