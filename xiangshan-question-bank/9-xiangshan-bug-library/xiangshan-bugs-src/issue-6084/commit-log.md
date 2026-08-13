# Commit Log
- Issue: #6084
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6084
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6084
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6084
- Changed files: 1
- Additions: 7
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
index 2608b89c8e6..b62851b07cb 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
@@ -58,6 +58,9 @@ class TrapInstMod(implicit p: Parameters) extends Module with HasCircularQueuePt
   newCSRInst.ftqPtr := io.faultCsrUop.bits.ftqInfo.ftqPtr
   newCSRInst.ftqOffset := io.faultCsrUop.bits.ftqInfo.ftqOffset
 
+  val newCSRInstOlder = (newCSRInst.ftqPtr === trapInstInfo.ftqPtr && newCSRInst.ftqOffset < trapInstInfo.ftqOffset) ||
+    newCSRInst.ftqPtr < trapInstInfo.ftqPtr
+
   when (flush.valid && valid ) {
     when (trapInstInfo.needFlush(flush.bits.ftqPtr, flush.bits.ftqOffset)) {
       when (newCSRInstValid && !newCSRInst.needFlush(flush.bits.ftqPtr, flush.bits.ftqOffset)) {
@@ -72,15 +75,15 @@ class TrapInstMod(implicit p: Parameters) extends Module with HasCircularQueuePt
     }.elsewhen (trapInstInfo.sameInst(flush.bits.ftqPtr, flush.bits.ftqOffset) && io.fromRob.isInterrupt.valid && io.fromRob.isInterrupt.bits) {
       // check whether the exception store is attached with an interrupt
       valid := false.B
+    }.elsewhen(newCSRInstValid && !newCSRInst.needFlush(flush.bits.ftqPtr, flush.bits.ftqOffset) && newCSRInstOlder) {
+      // keep the oldest trap instruction when flush and CSR exception happen together
+      trapInstInfo := newCSRInst
     }
   }.elsewhen(newCSRInstValid) {
     valid := true.B
     when (!valid) {
       trapInstInfo := newCSRInst
-    }.elsewhen(valid &&
-      (newCSRInst.ftqPtr === trapInstInfo.ftqPtr && newCSRInst.ftqOffset < trapInstInfo.ftqOffset ||
-      newCSRInst.ftqPtr < trapInstInfo.ftqPtr)
-    ) {
+    }.elsewhen(valid && newCSRInstOlder) {
       trapInstInfo := newCSRInst
     }
   }.elsewhen(newTrapInstInfo.valid && !valid) {
```
