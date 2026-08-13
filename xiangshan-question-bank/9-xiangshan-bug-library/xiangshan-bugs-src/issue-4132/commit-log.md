# Commit Log
- Issue: #4132
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4132
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4132
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4132
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala
index ceb9004fb65..52b1d8e7da3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala
@@ -153,7 +153,9 @@ trait Unprivileged { self: NewCSR with MachineLevel with SupervisorLevel =>
 
     // Update when rtc clock tick and not dcsr.STOPTIME
     // or virtual mode changed
-    when(mHPM.time.valid && !debugModeStopTime || this.nextV =/= this.v) {
+    // Note: we delay a cycle and use `v` for better timing
+    val virtModeChanged = RegNext(nextV =/= v, false.B)
+    when(mHPM.time.valid && !debugModeStopTime || virtModeChanged) {
       reg.time := Mux(v, vstimeTmp, stimeTmp)
     }.otherwise {
       reg := reg
```
