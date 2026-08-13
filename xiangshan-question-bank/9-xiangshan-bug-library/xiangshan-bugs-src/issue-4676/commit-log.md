# Commit Log
- Issue: #4676
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4676
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4676
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4676
- Changed files: 7
- Additions: 121
- Deletions: 34

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
index 9d41b342d58..92df33988ef 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 9d41b342d589248a8bf318855fb457fe08e6c930
+Subproject commit 92df33988ef50a6444aee628d856c211d244f461
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
index fb8986390b0..e474d34fb06 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
@@ -186,6 +186,9 @@ class MLevelPermitModule extends Module {
   private val mcounteren = io.in.xcounteren.mcounteren
 
   private val mstateen0 = io.in.xstateen.mstateen0
+  private val mstateen1 = io.in.xstateen.mstateen1
+  private val mstateen2 = io.in.xstateen.mstateen2
+  private val mstateen3 = io.in.xstateen.mstateen3
 
   private val mcounterenTM = mcounteren(1)
 
@@ -228,13 +231,17 @@ class MLevelPermitModule extends Module {
   private val rwStopei_EX_II = privState.isModeHS && mvienSEIE && (addr === CSRs.stopei.U)
 
   /**
-   * Sm/Ssstateen0 begin
+   * Sm/Ssstateen begin
    */
-  // SE0 bit 63
-  private val csrIsHstateen0 = addr === CSRs.hstateen0.U
-  private val csrIsSstateen0 = addr === CSRs.sstateen0.U
-  private val csrIsStateen0 = csrIsHstateen0 || csrIsSstateen0
-  private val accessStateen0_EX_II = csrIsStateen0 && !privState.isModeM && !mstateen0.SE0.asBool
+  // SE bit 63
+  private val accessStateen_EX_II = (
+    mstateen0.SE0.asBool +: Seq(mstateen1, mstateen2, mstateen3).map(_.SE.asBool)
+    ).zipWithIndex.map{ case(se, i) => {
+    val csrIsHstateen = addr === (CSRs.hstateen0 + i).U
+    val csrIsSstateen = addr === (CSRs.sstateen0 + i).U
+    val csrIsStateen = csrIsHstateen || csrIsSstateen
+    csrIsStateen && !privState.isModeM && !se
+  }}.reduce(_ || _)
 
   // ENVCFG bit 62
   private val csrIsHenvcfg = addr === CSRs.henvcfg.U
@@ -280,7 +287,7 @@ class MLevelPermitModule extends Module {
   private val allCustom      = csrIsHVSCustom || csrIsSCustom || csrIsUCustom
   private val accessCustom_EX_II = allCustom && !privState.isModeM && !mstateen0.C.asBool
 
-  val xstateControlAccess_EX_II = accessStateen0_EX_II || accessEnvcfg_EX_II || accessIND_EX_II || accessAIA_EX_II ||
+  private val xstateControlAccess_EX_II = accessStateen_EX_II || accessEnvcfg_EX_II || accessIND_EX_II || accessAIA_EX_II ||
     accessTopie_EX_II || accessContext_EX_II || accessCustom_EX_II
   /**
    * Sm/Ssstateen end
@@ -411,8 +418,11 @@ class VirtualLevelPermitModule(implicit val p: Parameters) extends Module with H
 
   private val henvcfgSTCE = henvcfg(63)
 
-  private val (hstateen0, sstateen0) = (
+  private val (hstateen0, hstateen1, hstateen2, hstateen3, sstateen0) = (
     io.in.xstateen.hstateen0,
+    io.in.xstateen.hstateen1,
+    io.in.xstateen.hstateen2,
+    io.in.xstateen.hstateen3,
     io.in.xstateen.sstateen0,
   )
 
@@ -437,11 +447,16 @@ class VirtualLevelPermitModule(implicit val p: Parameters) extends Module with H
     )
 
   /**
-   * Sm/Ssstateen0 begin
+   * Sm/Ssstateen begin
    */
-  // SE0 bit 63
-  private val csrIsSstateen0 = addr === CSRs.sstateen0.U
-  private val accessStateen0_EX_VI = csrIsSstateen0 && privState.isVirtual && !hstateen0.SE0.asBool
+
+  //  SE0 bit 63
+  private val accessStateen_EX_VI = (
+    hstateen0.SE0.asBool +: Seq(hstateen1, hstateen2, hstateen3).map(_.SE.asBool)
+    ).zipWithIndex.map{case(se, i) => {
+    val csrIsSstateen = addr === (CSRs.sstateen0 + i).U
+    csrIsSstateen && privState.isVirtual && !se
+  }}.reduce(_ || _)
 
   // ENVCFG bit 62
   private val csrIsSenvcfg = addr === CSRs.senvcfg.U
@@ -474,7 +489,7 @@ class VirtualLevelPermitModule(implicit val p: Parameters) extends Module with H
   private val accessCustom_EX_VI = (csrIsSCustom || csrIsUCustom) && privState.isVirtual && !hstateen0.C.asBool ||
     csrIsUCustom && privState.isModeVU && hstateen0.C.asBool && !sstateen0.C.asBool
 
-  private val xstateControlAccess_EX_VI = accessStateen0_EX_VI || accessEnvcfg_EX_VI || accessIND_EX_VI || accessAIA_EX_VI ||
+  private val xstateControlAccess_EX_VI = accessStateen_EX_VI || accessEnvcfg_EX_VI || accessIND_EX_VI || accessAIA_EX_VI ||
     accessTopie_EX_VI || accessContext_EX_VI || accessCustom_EX_VI
 
   io.out.virtualLevelPermit_EX_II := rwVStopei_EX_II
@@ -606,9 +621,15 @@ class xenvcfgIO extends Bundle {
 
 class xstateenIO extends Bundle {
   // Sm/Ssstateen: to control state access
-  val mstateen0 = new MstateenBundle0
-  val hstateen0 = new HstateenBundle0
-  val sstateen0 = new SstateenBundle0
+  val mstateen0 = new Mstateen0Bundle
+  val mstateen1 = new MstateenNonZeroBundle
+  val mstateen2 = new MstateenNonZeroBundle
+  val mstateen3 = new MstateenNonZeroBundle
+  val hstateen0 = new Hstateen0Bundle
+  val hstateen1 = new HstateenNonZeroBundle
+  val hstateen2 = new HstateenNonZeroBundle
+  val hstateen3 = new HstateenNonZeroBundle
+  val sstateen0 = new Sstateen0Bundle
 }
 
 class aiaIO extends Bundle {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index b08edf49318..bfcac882e7f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -167,12 +167,24 @@ trait HypervisorLevel { self: NewCSR =>
   })
     .setAddr(CSRs.hgeip)
 
-  val hstateen0 = Module(new CSRModule("Hstateen", new HstateenBundle0) with HasStateen0Bundle {
+  val hstateen0 = Module(new CSRModule("Hstateen0", new Hstateen0Bundle) with HasStateenBundle {
     // For every bit in an mstateen CSR that is zero (whether read-only zero or set to zero), the same bit
     // appears as read-only zero in the matching hstateen and sstateen CSRs.
     regOut := reg.asUInt & fromMstateen0.asUInt
   }).setAddr(CSRs.hstateen0)
 
+  val hstateen1 = Module(new CSRModule("Hstateen1", new HstateenNonZeroBundle) with HasStateenBundle {
+    regOut := reg.asUInt & fromMstateen1.asUInt
+  }).setAddr(CSRs.hstateen1)
+
+  val hstateen2 = Module(new CSRModule("Hstateen2", new HstateenNonZeroBundle) with HasStateenBundle {
+    regOut := reg.asUInt & fromMstateen2.asUInt
+  }).setAddr(CSRs.hstateen2)
+
+  val hstateen3 = Module(new CSRModule("Hstateen3", new HstateenNonZeroBundle) with HasStateenBundle {
+    regOut := reg.asUInt & fromMstateen3.asUInt
+  }).setAddr(CSRs.hstateen3)
+
   val hypervisorCSRMods: Seq[CSRModule[_]] = Seq(
     hstatus,
     hedeleg,
@@ -193,6 +205,9 @@ trait HypervisorLevel { self: NewCSR =>
     hgatp,
     hgeip,
     hstateen0,
+    hstateen1,
+    hstateen2,
+    hstateen3,
   )
 
   val hypervisorCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap.from(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index b7028567bf3..666f4adcfcc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -381,7 +381,13 @@ trait MachineLevel { self: NewCSR =>
   }))
     .setAddr(CSRs.mconfigptr)
 
-  val mstateen0 = Module(new CSRModule("Mstateen", new MstateenBundle0)).setAddr(CSRs.mstateen0)
+  val mstateen0 = Module(new CSRModule("Mstateen0", new Mstateen0Bundle)).setAddr(CSRs.mstateen0)
+
+  val mstateen1 = Module(new CSRModule("Mstateen1", new MstateenNonZeroBundle)).setAddr(CSRs.mstateen1)
+
+  val mstateen2 = Module(new CSRModule("Mstateen2", new MstateenNonZeroBundle)).setAddr(CSRs.mstateen2)
+
+  val mstateen3 = Module(new CSRModule("Mstateen3", new MstateenNonZeroBundle)).setAddr(CSRs.mstateen3)
 
   // smrnmi extension
   val mnepc = Module(new CSRModule("Mnepc", new Epc) with TrapEntryMNEventSinkBundle {
@@ -432,6 +438,9 @@ trait MachineLevel { self: NewCSR =>
     mhartid,
     mconfigptr,
     mstateen0,
+    mstateen1,
+    mstateen2,
+    mstateen3,
     mnepc,
     mncause,
     mnstatus,
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 618df3ea777..1fe8e1ff92a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -492,7 +492,13 @@ class NewCSR(implicit val p: Parameters) extends Module
   permitMod.io.in.xcounteren.scounteren := scounteren.rdata
 
   permitMod.io.in.xstateen.mstateen0 := mstateen0.rdata
+  permitMod.io.in.xstateen.mstateen1 := mstateen1.rdata
+  permitMod.io.in.xstateen.mstateen2 := mstateen2.rdata
+  permitMod.io.in.xstateen.mstateen3 := mstateen3.rdata
   permitMod.io.in.xstateen.hstateen0 := hstateen0.rdata
+  permitMod.io.in.xstateen.hstateen1 := hstateen1.rdata
+  permitMod.io.in.xstateen.hstateen2 := hstateen2.rdata
+  permitMod.io.in.xstateen.hstateen3 := hstateen3.rdata
   permitMod.io.in.xstateen.sstateen0 := sstateen0.rdata
 
   permitMod.io.in.xenvcfg.menvcfg := menvcfg.rdata
@@ -752,8 +758,11 @@ class NewCSR(implicit val p: Parameters) extends Module
       case _ =>
     }
     mod match {
-      case m: HasStateen0Bundle =>
+      case m: HasStateenBundle =>
         m.fromMstateen0 := mstateen0.regOut
+        m.fromMstateen1 := mstateen1.regOut
+        m.fromMstateen2 := mstateen2.regOut
+        m.fromMstateen3 := mstateen3.regOut
         m.fromHstateen0 := hstateen0.regOut
         m.privState     := privState
       case _ =>
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
index f94478801e9..24f481a28a0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
@@ -5,31 +5,54 @@ import xiangshan.backend.fu.NewCSR.CSRDefines.{CSRROField => RO, CSRRWField => R
 import xiangshan.backend.fu.NewCSR.CSRBundles.PrivState
 
 
-class SstateenBundle0 extends CSRBundle {
+class Sstateen0Bundle extends CSRBundle {
   override val len: Int = 32
   val JVT  = RO(2).withReset(0.U) // jvt CSR in Zcmt extension
   val FCSR = RO(1).withReset(0.U) // fp inst op 'x' register not f in Zfinx, Zdinx; misa.F =1 -> RO 0; misa.F=0 & this=0 -> V/EX_II
-  val C    = RW(0).withReset(0.U) // custom state enable, [m|h|s]stateen is standard, not custom.
+  val C    = RW(0)                // custom state enable, [m|h|s]stateen is standard, not custom.
 }
 
-class HstateenBundle0 extends SstateenBundle0 {
+class Hstateen0Bundle extends Sstateen0Bundle {
   override val len: Int = 64
-  val SE0     = RW(63).withReset(0.U) // m: [h|s]stateen                h: sstateen
-  val ENVCFG  = RW(62).withReset(0.U) // m: [h|s]envcfg                 h: senvcfg
+  val SE0     = RW(63)                // m: [h|s]stateen                h: sstateen
+  val ENVCFG  = RW(62)                // m: [h|s]envcfg                 h: senvcfg
   // Bits in any stateen CSR that are defined to control state that a hart doesn’t implement are read-only
   // zeros for that hart. Smcsrind/Sscsrind is not implemented.
-  val CSRIND  = RW(60).withReset(0.U) // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
-  val AIA     = RW(59).withReset(0.U) // all other state added by the AIA and not controlled by bits 60 and 58
-  val IMSIC   = RW(58).withReset(0.U) // m: [vs|s]topei                 h: stopei
+  val CSRIND  = RW(60)                // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
+  val AIA     = RW(59)                // all other state added by the AIA and not controlled by bits 60 and 58
+  val IMSIC   = RW(58)                // m: [vs|s]topei                 h: stopei
   val CONTEXT = RO(57).withReset(0.U) // m: [h|s]context in Sdtrig      h: scontext
 }
 
-class MstateenBundle0 extends HstateenBundle0 {
-  val P1P13   = RO(56).withReset(0.U) // hedelegh in Priv Spec V1.13
+class Mstateen0Bundle extends Hstateen0Bundle {
+  override val SE0     = RW(63).withReset(0.U) // m: [h|s]stateen                h: sstateen
+  override val ENVCFG  = RW(62).withReset(0.U) // m: [h|s]envcfg                 h: senvcfg
+  // Bits in any stateen CSR that are defined to control state that a hart doesn’t implement are read-only
+  // zeros for that hart. Smcsrind/Sscsrind is not implemented.
+  override val CSRIND  = RW(60).withReset(0.U) // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
+  override val AIA     = RW(59).withReset(0.U) // all other state added by the AIA and not controlled by bits 60 and 58
+  override val IMSIC   = RW(58).withReset(0.U) // m: [vs|s]topei                 h: stopei
+  val P1P13            = RO(56).withReset(0.U) // hedelegh in Priv Spec V1.13
+  override val C       = RW(0).withReset(0.U)  // custom state enable, [m|h|s]stateen is standard, not custom.
+}
+
+class SstateenNonZeroBundle extends CSRBundle {  // for sstateen[1|2|3]
+  override val len = 32
+  val ALL = RO(31, 0).withReset(0.U)
+}
+
+class HstateenNonZeroBundle extends CSRBundle {  // for hstateen[1|2|3]
+  val SE = RW(63)
+}
+class MstateenNonZeroBundle extends HstateenNonZeroBundle {  // for mstateen[1|2|3]
+  override val SE = RW(63).withReset(0.U)
 }
 
-trait HasStateen0Bundle { self: CSRModule[_] =>
-  val fromMstateen0 = IO(Input(new MstateenBundle0))
-  val fromHstateen0 = IO(Input(new HstateenBundle0))
+trait HasStateenBundle { self: CSRModule[_] =>
+  val fromMstateen0 = IO(Input(new Mstateen0Bundle))
+  val fromMstateen1 = IO(Input(new MstateenNonZeroBundle))
+  val fromMstateen2 = IO(Input(new MstateenNonZeroBundle))
+  val fromMstateen3 = IO(Input(new MstateenNonZeroBundle))
+  val fromHstateen0 = IO(Input(new Hstateen0Bundle))
   val privState     = IO(Input(new PrivState))
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
index b5ce5f545bd..f0c995e7583 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
@@ -161,7 +161,7 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
     ))
   }).setAddr(CSRs.scountovf)
 
-  val sstateen0 = Module(new CSRModule("Sstateen", new SstateenBundle0) with HasStateen0Bundle {
+  val sstateen0 = Module(new CSRModule("Sstateen0", new Sstateen0Bundle) with HasStateenBundle {
     // For every bit in an mstateen CSR that is zero (whether read-only zero or set to zero), the same bit
     // appears as read-only zero in the matching hstateen and sstateen CSRs. For every bit in an hstateen
     // CSR that is zero (whether read-only zero or set to zero), the same bit appears as read-only zero in
@@ -169,6 +169,13 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
     regOut := Mux(privState.isVirtual, fromHstateen0.asUInt, fromMstateen0.asUInt) & reg.asUInt
   }).setAddr(CSRs.sstateen0)
 
+  // sstateen[1|2|3] read-only zero
+  val sstateen1 = Module(new CSRModule("Sstateen1", new SstateenNonZeroBundle)).setAddr(CSRs.sstateen1)
+
+  val sstateen2 = Module(new CSRModule("Sstateen2", new SstateenNonZeroBundle)).setAddr(CSRs.sstateen2)
+
+  val sstateen3 = Module(new CSRModule("Sstateen3", new SstateenNonZeroBundle)).setAddr(CSRs.sstateen3)
+
   val supervisorLevelCSRMods: Seq[CSRModule[_]] = Seq(
     sie,
     stvec,
@@ -183,6 +190,9 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
     satp,
     scountovf,
     sstateen0,
+    sstateen1,
+    sstateen2,
+    sstateen3,
   )
 
   val supervisorLevelCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap(
```
