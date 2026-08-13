# Commit Log
- Issue: #3640
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3640
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3640
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3640
- Changed files: 4
- Additions: 11
- Deletions: 3

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/ExceptionBundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index 8ca5b197050..80fe1e445bf 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 8ca5b1970508b1dad167ce22047daf9d3e4ead75
+Subproject commit 80fe1e445bf80477b2c5ed1cdeb35b51ae10aae0
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/ExceptionBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/ExceptionBundle.scala
index 72214e2ebb1..fd5c6706933 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/ExceptionBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/ExceptionBundle.scala
@@ -21,7 +21,13 @@ class ExceptionBundle extends CSRBundle {
   val EX_LPF    = RW(13)
   // 14 Reserved
   val EX_SPF    = RW(15)
-  // 16-19 Reserved
+  // double trap
+  val EX_DBLTRP = RW(16)
+  // 17 Reserved
+  // software check
+  val EX_SWC    = RW(18)
+  // hardware error
+  val EX_HWE    = RW(19)
   val EX_IGPF   = RW(20)
   val EX_LGPF   = RW(21)
   val EX_VI     = RW(22)
@@ -47,6 +53,6 @@ class ExceptionBundle extends CSRBundle {
 
   def getStoreFault = Seq(EX_SAM, EX_SAF, EX_SPF)
 
-  def getALL = Seq(EX_SGPF, EX_VI, EX_LGPF, EX_IGPF, EX_SPF, EX_LPF, EX_IPF, EX_MCALL, EX_VSCALL,
+  def getALL = Seq(EX_SGPF, EX_VI, EX_LGPF, EX_IGPF, EX_HWE, EX_SWC, EX_DBLTRP, EX_SPF, EX_LPF, EX_IPF, EX_MCALL, EX_VSCALL,
     EX_HSCALL, EX_UCALL, EX_SAF, EX_SAM, EX_LAF, EX_LAM, EX_BP, EX_II, EX_IAF, EX_IAM)
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 5f4d13ac75b..094d5ab587f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -262,6 +262,7 @@ class HedelegBundle extends ExceptionBundle {
   this.EX_LGPF  .setRO().withReset(0.U)
   this.EX_VI    .setRO().withReset(0.U)
   this.EX_SGPF  .setRO().withReset(0.U)
+  this.EX_DBLTRP.setRO().withReset(0.U) // double trap is not delegatable
 }
 
 class HidelegBundle extends InterruptBundle {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 5544a06c987..e30639993ac 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -548,6 +548,7 @@ class MedelegBundle extends ExceptionBundle {
   this.getALL.foreach(_.setRW().withReset(0.U))
   this.EX_MCALL.setRO().withReset(0.U) // never delegate machine level ecall
   this.EX_BP.setRO().withReset(0.U)    // Parter 5.4 in debug spec. tcontrol is implemented. medeleg [3] is hard-wired to 0.
+  this.EX_DBLTRP.setRO().withReset(0.U)// double trap is not delegatable
 }
 
 class MidelegBundle extends InterruptBundle {
```
