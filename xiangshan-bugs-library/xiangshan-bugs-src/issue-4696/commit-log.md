# Commit Log
- Issue: #4696
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4696
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4696
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4696
- Changed files: 7
- Additions: 44
- Deletions: 5

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index 92df33988ef..eaeaedead92 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 92df33988ef50a6444aee628d856c211d244f461
+Subproject commit eaeaedead925c0f32348e2d09b76ebcd5cd33f06
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
index e474d34fb06..6b75845c36a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
@@ -269,7 +269,7 @@ class MLevelPermitModule extends Module {
   private val csrIsTpoie = csrIsStopei || csrIsVStopei
   private val accessTopie_EX_II = csrIsTpoie && !privState.isModeM && !mstateen0.IMSIC.asBool
 
-  // CONTEXT bit 57 context reg (Sdtrig extensions), this is not implemented
+  // CONTEXT bit 57 context reg (Sdtrig extensions)
   private val csrIsHcontext = addr === CSRs.hcontext.U
   private val csrIsScontext = addr === CSRs.scontext.U
   private val csrIsContext = csrIsHcontext || csrIsScontext
@@ -475,7 +475,7 @@ class VirtualLevelPermitModule(implicit val p: Parameters) extends Module with H
   private val csrIsStopei = addr === CSRs.stopei.U
   private val accessTopie_EX_VI = csrIsStopei && privState.isVirtual && !hstateen0.IMSIC.asBool
 
-  // CONTEXT bit 57 context reg (Sdtrig extensions), this is not implemented
+  // CONTEXT bit 57 context reg (Sdtrig extensions)
   private val csrIsScontext = addr === CSRs.scontext.U
   private val accessContext_EX_VI = csrIsScontext && privState.isVirtual && !hstateen0.CONTEXT.asBool
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index bfcac882e7f..5e30fdd2dca 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -185,6 +185,14 @@ trait HypervisorLevel { self: NewCSR =>
     regOut := reg.asUInt & fromMstateen3.asUInt
   }).setAddr(CSRs.hstateen3)
 
+  val hcontext = Module(new CSRModule("Hcontext", new McontextBundle) {
+    val fromMcontext = IO(Input(new McontextBundle))
+    val toMcontext   = IO(ValidIO(new McontextBundle))
+    toMcontext.valid := wen
+    toMcontext.bits.HCONTEXT := wdata.HCONTEXT.asUInt
+    regOut.HCONTEXT := fromMcontext.HCONTEXT.asUInt
+  }).setAddr(CSRs.hcontext)
+
   val hypervisorCSRMods: Seq[CSRModule[_]] = Seq(
     hstatus,
     hedeleg,
@@ -208,6 +216,7 @@ trait HypervisorLevel { self: NewCSR =>
     hstateen1,
     hstateen2,
     hstateen3,
+    hcontext,
   )
 
   val hypervisorCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap.from(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 666f4adcfcc..9c77d6f460b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -410,6 +410,18 @@ trait MachineLevel { self: NewCSR =>
   val mnscratch = Module(new CSRModule("Mnscratch"))
     .setAddr(CSRs.mnscratch)
 
+  val mcontext = Module(new CSRModule("Mcontext", new McontextBundle) {
+    val fromHcontext = IO(Flipped(ValidIO(new McontextBundle)))
+    val toHcontext   = IO(Output(new McontextBundle))
+    toHcontext.HCONTEXT := regOut.HCONTEXT.asUInt
+    when(wen) {
+      reg.HCONTEXT := wdata.HCONTEXT.asUInt
+    }.elsewhen(fromHcontext.valid) {
+      reg.HCONTEXT := fromHcontext.bits.HCONTEXT
+    }
+  })
+    .setAddr(CSRs.mcontext)
+
   val machineLevelCSRMods: Seq[CSRModule[_]] = Seq(
     mstatus,
     misa,
@@ -445,6 +457,7 @@ trait MachineLevel { self: NewCSR =>
     mncause,
     mnstatus,
     mnscratch,
+    mcontext,
   ) ++ mhpmevents ++ mhpmcounters ++ (if (HasBitmapCheck) Seq(mbmc.get) else Seq())
 
 
@@ -741,6 +754,11 @@ object OPTYPE extends CSREnum with WARLApply {
   override def isLegal(enumeration: CSREnumType): Bool = enumeration.isOneOf(OR, AND, XOR, ADD)
 }
 
+class McontextBundle extends CSRBundle {
+  override val len = 14
+  val HCONTEXT = RW(13, 0)
+}
+
 trait HasOfFromPerfCntBundle { self: CSRModule[_] =>
   val ofFromPerfCnt = IO(Input(Bool()))
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 1fe8e1ff92a..94cb1b08e09 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1216,6 +1216,9 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   io.status.frontendTrigger := debugMod.io.out.frontendTrigger
   io.status.memTrigger      := debugMod.io.out.memTrigger
+
+  mcontext.fromHcontext <> hcontext.toMcontext
+  hcontext.fromMcontext <> mcontext.toHcontext
   /**
    * debug_end
    */
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
index 24f481a28a0..32f503b1f18 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
@@ -21,7 +21,7 @@ class Hstateen0Bundle extends Sstateen0Bundle {
   val CSRIND  = RW(60)                // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
   val AIA     = RW(59)                // all other state added by the AIA and not controlled by bits 60 and 58
   val IMSIC   = RW(58)                // m: [vs|s]topei                 h: stopei
-  val CONTEXT = RO(57).withReset(0.U) // m: [h|s]context in Sdtrig      h: scontext
+  val CONTEXT = RW(57)                // m: [h|s]context in Sdtrig      h: scontext
 }
 
 class Mstateen0Bundle extends Hstateen0Bundle {
@@ -32,6 +32,7 @@ class Mstateen0Bundle extends Hstateen0Bundle {
   override val CSRIND  = RW(60).withReset(0.U) // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
   override val AIA     = RW(59).withReset(0.U) // all other state added by the AIA and not controlled by bits 60 and 58
   override val IMSIC   = RW(58).withReset(0.U) // m: [vs|s]topei                 h: stopei
+  override val CONTEXT = RW(57).withReset(0.U) // m: [h|s]context in Sdtrig      h: scontext
   val P1P13            = RO(56).withReset(0.U) // hedelegh in Priv Spec V1.13
   override val C       = RW(0).withReset(0.U)  // custom state enable, [m|h|s]stateen is standard, not custom.
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
index f0c995e7583..b6cd2a685b4 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
@@ -176,6 +176,8 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
 
   val sstateen3 = Module(new CSRModule("Sstateen3", new SstateenNonZeroBundle)).setAddr(CSRs.sstateen3)
 
+  val scontext = Module(new CSRModule("Scontext", new ScontextBundle)).setAddr(CSRs.scontext)
+
   val supervisorLevelCSRMods: Seq[CSRModule[_]] = Seq(
     sie,
     stvec,
@@ -193,6 +195,7 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
     sstateen1,
     sstateen2,
     sstateen3,
+    scontext,
   )
 
   val supervisorLevelCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap(
@@ -248,6 +251,11 @@ class SatpBundle extends CSRBundle {
   val PPN  = RW(43, 0).withReset(0.U)
 }
 
+class ScontextBundle extends CSRBundle {
+  override val len = 32
+  val ALL = RW(31, 0)
+}
+
 class SEnvCfg extends EnvCfg
 
 class SipToMip extends IpValidBundle {
@@ -270,4 +278,4 @@ trait HasMhpmeventOfBundle { self: CSRModule[_] =>
   val privState = IO(Input(new PrivState))
   val mcounteren = IO(Input(new Counteren))
   val hcounteren = IO(Input(new Counteren))
-}
\ No newline at end of file
+}
```
