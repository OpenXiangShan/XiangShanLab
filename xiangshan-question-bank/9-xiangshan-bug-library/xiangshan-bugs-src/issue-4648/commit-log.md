# Commit Log
- Issue: #4648
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4648
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4648
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4648
- Changed files: 3
- Additions: 5
- Deletions: 2

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index 276342dd8f0..c73ba81be39 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 276342dd8f08cb789bb355b4d0b127d6736f6482
+Subproject commit c73ba81be39b21d4d11b4e024b1074c9e9001fa2
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 4f45cd4086f..b08edf49318 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -230,6 +230,7 @@ class HvipBundle extends InterruptPendingBundle {
   // VSSIP, VSTIP, VSEIP, localIP is writable
   this.getVS.foreach(_.setRW().withReset(0.U))
   this.getLocal.foreach(_.setRW().withReset(0.U))
+  this.LCOFIP.setRO().withReset(0.U)
 }
 
 class HieBundle extends InterruptEnableBundle {
@@ -249,7 +250,7 @@ class HvienBundle extends InterruptEnableBundle {
   // For interrupt numbers 13–63, implementations may freely choose which bits of hvien are writable
   // and which bits are read-only zero or one.
   this.getLocal.foreach(_.setRW().withReset(0.U))
-
+  this.LCOFIE.setRO().withReset(0.U)
 }
 
 class HgeieBundle(implicit val p: Parameters) extends CSRBundle with HasSoCParameter {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index f106aa5600b..b7028567bf3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -640,11 +640,13 @@ class MvienBundle extends InterruptEnableBundle {
   this.SSIE.setRW().withReset(0.U)
   this.SEIE.setRW().withReset(0.U)
   this.getLocal.foreach(_.setRW().withReset(0.U))
+  this.LCOFIE.setRO().withReset(0.U)
 }
 
 class MvipBundle extends InterruptPendingBundle {
   this.getHS.foreach(_.setRW().withReset(0.U))
   this.getLocal.foreach(_.setRW().withReset(0.U))
+  this.LCOFIP.setRO().withReset(0.U)
 }
 
 class Epc extends CSRBundle {
```
