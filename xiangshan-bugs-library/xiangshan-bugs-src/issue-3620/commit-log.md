# Commit Log
- Issue: #3620
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3620
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3620
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3620
- Changed files: 3
- Additions: 8
- Deletions: 5

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 3c7977579a2..5f4d13ac75b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -347,6 +347,4 @@ trait HypervisorBundle { self: CSRModule[_] =>
 
 trait HasHypervisorEnvBundle { self: CSRModule[_] =>
   val menvcfg = IO(Input(new MEnvCfg))
-  val privState = IO(Input(new PrivState))
-  val accessStimecmp = IO(Input(Bool()))
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index e0773415d44..a378f16913a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -595,8 +595,6 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod match {
       case m: HasHypervisorEnvBundle =>
         m.menvcfg := menvcfg.regOut
-        m.privState := privState
-        m.accessStimecmp := (ren || wen) && (addr === CSRs.stimecmp.U || addr === CSRs.vstimecmp.U)
       case _ =>
     }
     mod match {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
index dd6a68387da..5bbd2ef26de 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
@@ -62,7 +62,14 @@ class TrapInstMod(implicit p: Parameters) extends Module with HasCircularQueuePt
     valid := false.B
   }.elsewhen(newCSRInstValid) {
     valid := true.B
-    trapInstInfo := newCSRInst
+    when (!valid) {
+      trapInstInfo := newCSRInst
+    }.elsewhen(valid &&
+      (newCSRInst.ftqPtr === trapInstInfo.ftqPtr && newCSRInst.ftqOffset < trapInstInfo.ftqOffset ||
+      newCSRInst.ftqPtr < trapInstInfo.ftqPtr)
+    ) {
+      trapInstInfo := newCSRInst
+    }
   }.elsewhen(newTrapInstInfo.valid && !valid) {
     valid := true.B
     trapInstInfo := newTrapInstInfo.bits
```
