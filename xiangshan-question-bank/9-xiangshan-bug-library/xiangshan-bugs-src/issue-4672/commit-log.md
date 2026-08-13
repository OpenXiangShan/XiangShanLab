# Commit Log
- Issue: #4672
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4672
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4672
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4672
- Changed files: 6
- Additions: 187
- Deletions: 37

## Files
- `ready-to-run`
- `rocket-chip`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRIND.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index c73ba81be39..9d41b342d58 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit c73ba81be39b21d4d11b4e024b1074c9e9001fa2
+Subproject commit 9d41b342d589248a8bf318855fb457fe08e6c930
diff --git a/rocket-chip b/rocket-chip
index bea0af5d855..bb010851f1a 160000
--- a/rocket-chip
+++ b/rocket-chip
@@ -1 +1 @@
-Subproject commit bea0af5d855a871699b907c1988961141bbf28e7
+Subproject commit bb010851f1a3766ae7d1ce2a7cfb983b9c77b14f
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRIND.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRIND.scala
new file mode 100644
index 00000000000..39fcac6ec3d
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRIND.scala
@@ -0,0 +1,137 @@
+package xiangshan.backend.fu.NewCSR
+
+import chisel3._
+import chisel3.util._
+import freechips.rocketchip.rocket.CSRs
+import org.chipsalliance.cde.config.Parameters
+import CSRConfig._
+import system.HasSoCParameter
+import xiangshan.backend.fu.NewCSR.CSRBundles._
+import xiangshan.backend.fu.NewCSR.CSRConfig._
+import xiangshan.backend.fu.NewCSR.CSRDefines.{CSRROField => RO, CSRRWField => RW, _}
+import xiangshan.XSBundle
+
+import scala.collection.immutable.SeqMap
+
+trait CSRIND { self: NewCSR with HypervisorLevel =>
+  val mireg2 = Module(new CSRModule("Mireg2") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.mireg2)
+
+  val mireg3 = Module(new CSRModule("Mireg3") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.mireg3)
+
+  val mireg4 = Module(new CSRModule("Mireg4") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.mireg4)
+
+  val mireg5 = Module(new CSRModule("Mireg5") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.mireg5)
+
+  val mireg6 = Module(new CSRModule("Mireg6") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.mireg6)
+
+  val sireg2 = Module(new CSRModule("Sireg2") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.sireg2)
+
+  val sireg3 = Module(new CSRModule("Sireg3") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.sireg3)
+
+  val sireg4 = Module(new CSRModule("Sireg4") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.sireg4)
+
+  val sireg5 = Module(new CSRModule("Sireg5") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.sireg5)
+
+  val sireg6 = Module(new CSRModule("Sireg6") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.sireg6)
+
+  val vsireg2 = Module(new CSRModule("VSireg2") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.vsireg2)
+
+  val vsireg3 = Module(new CSRModule("VSireg3") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.vsireg3)
+
+  val vsireg4 = Module(new CSRModule("VSireg4") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.vsireg4)
+
+  val vsireg5 = Module(new CSRModule("VSireg5") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.vsireg5)
+
+  val vsireg6 = Module(new CSRModule("VSireg6") {
+    rdata := 0.U
+  })
+    .setAddr(CSRs.vsireg6)
+
+  val indCSRMods = Seq(
+    mireg2,
+    mireg3,
+    mireg4,
+    mireg5,
+    mireg6,
+    sireg2,
+    sireg3,
+    sireg4,
+    sireg5,
+    sireg6,
+    vsireg2,
+    vsireg3,
+    vsireg4,
+    vsireg5,
+    vsireg6,
+  )
+
+  val indCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_ <: CSRBundle], UInt)] = SeqMap.from(
+    indCSRMods.map(csr => (csr.addr -> (csr.w -> csr.rdata))).iterator
+  )
+
+  val indCSROutMap: SeqMap[Int, UInt] = SeqMap.from(
+    indCSRMods.map(csr => (csr.addr -> csr.regOut.asInstanceOf[CSRBundle].asUInt)).iterator
+  )
+
+}
+
+object Iselect {
+  def isInAIA(iselect: UInt): Bool = iselect >= 0x30.U && iselect <= 0x3f.U
+  def isInImsic(iselect: UInt): Bool = iselect >= 0x70.U && iselect <= 0xff.U
+  def isInOthers(iselect: UInt): Bool = !(isInAIA(iselect) || isInImsic(iselect))
+  def isOdd(iselect: UInt): Bool = iselect(0) === 1.U
+}
+
+object Ireg {
+  def isInMCsrInd(ireg: UInt): Bool = ireg >= CSRs.miselect.U && ireg <= CSRs.mireg6.U && ireg =/= CSRs.miph.U
+  def isInSCsrInd(ireg: UInt): Bool = ireg >= CSRs.siselect.U && ireg <= CSRs.sireg6.U && ireg =/= CSRs.siph.U
+  def isInVSCsrInd(ireg: UInt): Bool = ireg >= CSRs.vsiselect.U && ireg <= CSRs.vsireg6.U && ireg =/= CSRs.vsiph.U
+  def isInMiregX(ireg: UInt): Bool = ireg >= CSRs.mireg.U && ireg <= CSRs.mireg6.U && ireg =/= CSRs.miph.U
+  def isInSiregX(ireg: UInt): Bool = ireg >= CSRs.sireg.U && ireg <= CSRs.sireg6.U && ireg =/= CSRs.siph.U
+  def isInVSiregX(ireg: UInt): Bool = ireg >= CSRs.vsireg.U && ireg <= CSRs.vsireg6.U && ireg =/= CSRs.vsiph.U
+  def isInMireg2_6(ireg: UInt): Bool = ireg >= CSRs.mireg2.U && ireg <= CSRs.mireg6.U && ireg =/= CSRs.miph.U
+  def isInSireg2_6(ireg: UInt): Bool = ireg >= CSRs.sireg2.U && ireg <= CSRs.sireg6.U && ireg =/= CSRs.siph.U
+  def isInVSireg2_6(ireg: UInt): Bool = ireg >= CSRs.vsireg2.U && ireg <= CSRs.vsireg6.U && ireg =/= CSRs.vsiph.U
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
index 454caba58e9..fb8986390b0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
@@ -244,8 +244,8 @@ class MLevelPermitModule extends Module {
 
   // CSRIND bit 60 indirect reg (Sscsrind extensions), this is not implemented
   // csr addr S: [0x150, 0x157]     VS: [0x250, 0x257]
-  private val csrIsSi = addr.head(9) === CSRs.siselect.U.head(9)
-  private val csrIsVSi = addr.head(9) === CSRs.vsiselect.U.head(9)
+  private val csrIsSi = Ireg.isInSCsrInd(addr)
+  private val csrIsVSi = Ireg.isInVSCsrInd(addr)
   private val csrIsIND = csrIsSi || csrIsVSi
   private val accessIND_EX_II = csrIsIND && !privState.isModeM && !mstateen0.CSRIND.asBool
 
@@ -449,7 +449,7 @@ class VirtualLevelPermitModule(implicit val p: Parameters) extends Module with H
 
   // CSRIND bit 60 indirect reg (Sscsrind extensions), this is not implemented
   // csr addr S: [0x150, 0x157]
-  private val csrIsSi = addr.head(9) === CSRs.siselect.U.head(9)
+  private val csrIsSi = Ireg.isInSCsrInd(addr)
   private val accessIND_EX_VI = csrIsSi && privState.isVirtual && !hstateen0.CSRIND.asBool
 
   // AIA bit 59
@@ -500,13 +500,8 @@ class IndirectCSRPermitModule extends Module {
     io.in.privState,
   )
 
-  private val (miselectIsIllegal, siselectIsIllegal, vsiselectIsIllegal) = (
-    io.in.aia.miselectIsIllegal,
-    io.in.aia.siselectIsIllegal,
-    io.in.aia.vsiselectIsIllegal,
-  )
-
-  private val (siselect, vsiselect) = (
+  private val (miselect, siselect, vsiselect) = (
+    io.in.aia.miselect,
     io.in.aia.siselect,
     io.in.aia.vsiselect,
   )
@@ -518,27 +513,45 @@ class IndirectCSRPermitModule extends Module {
 
   private val mvienSEIE = io.in.aia.mvienSEIE
 
-  private val rwMireg_EX_II = miselectIsIllegal && addr === CSRs.mireg.U
+  private val rwMireg_EX_II = (
+      Iselect.isInAIA(miselect) && Iselect.isOdd(miselect) ||
+      Iselect.isInOthers(miselect)
+    ) && addr === CSRs.mireg.U
+
+  private val rwMireg2_6_EX_II = Ireg.isInMireg2_6(addr)
 
   private val rwSireg_EX_II = (
-      (privState.isModeHS && mvienSEIE && siselect >= 0x70.U && siselect <= 0xFF.U) ||
-      ((privState.isModeM || privState.isModeHS) && siselectIsIllegal) ||
-      (privState.isModeVS && (vsiselect < 0x30.U || (vsiselect >= 0x40.U && vsiselect < 0x70.U) || vsiselect > 0xFF.U)) ||
-      (!privState.isModeM && !mstateen0.IMSIC.asBool && siselect >= 0x70.U && siselect <= 0xFF.U) ||  // xstateen.IMISC
-      (!privState.isModeM && !mstateen0.AIA.asBool && siselect >= 0x30.U && siselect <= 0x3F.U)       // xstateen.AIA
+      !privState.isVirtual && (
+        Iselect.isInAIA(siselect) && Iselect.isOdd(siselect) ||
+        Iselect.isInOthers(siselect)
+      ) ||
+      privState.isModeHS && (
+        mvienSEIE && Iselect.isInImsic(siselect) ||
+        !mstateen0.AIA.asBool && Iselect.isInAIA(siselect) ||
+        !mstateen0.IMSIC.asBool && Iselect.isInImsic(siselect)
+      ) ||
+      privState.isVirtual && (
+        Iselect.isInOthers(vsiselect) ||
+        !mstateen0.AIA.asBool && Iselect.isInAIA(vsiselect) ||
+        !mstateen0.IMSIC.asBool && Iselect.isInImsic(vsiselect)
+      )
     ) && addr === CSRs.sireg.U
 
-  private val rwSireg_EX_VI = (
-      privState.isModeVS && (vsiselect >= 0x30.U && vsiselect <= 0x3F.U) ||
-      privState.isVirtual && !hstateen0.IMSIC.asBool && !vsiselectIsIllegal
-    ) && addr === CSRs.sireg.U
+  private val rwSireg_EX_VI = privState.isVirtual && (Iselect.isInAIA(vsiselect) || Iselect.isInImsic(vsiselect) && !hstateen0.IMSIC.asBool) && addr === CSRs.sireg.U
+
+  private val rwSireg2_6_EX_VI = privState.isVirtual && (Iselect.isInAIA(vsiselect) || Iselect.isInImsic(vsiselect)) && Ireg.isInSireg2_6(addr)
+
+  private val rwSireg2_6_EX_II = Ireg.isInSireg2_6(addr) && !rwSireg2_6_EX_VI
 
   private val rwVSireg_EX_II = (
-      vsiselectIsIllegal || !privState.isModeM && !mstateen0.IMSIC.asBool
+      !Iselect.isInImsic(vsiselect) ||
+      !privState.isModeM && !mstateen0.IMSIC.asBool
     ) && addr === CSRs.vsireg.U
 
-  io.out.indirectCSR_EX_II := rwMireg_EX_II || rwSireg_EX_II || rwVSireg_EX_II
-  io.out.indirectCSR_EX_VI := rwSireg_EX_VI
+  private val rwVSireg2_6_EX_II = Ireg.isInVSireg2_6(addr)
+
+  io.out.indirectCSR_EX_II := rwMireg_EX_II || rwMireg2_6_EX_II || rwSireg_EX_II || rwSireg2_6_EX_II || rwVSireg_EX_II || rwVSireg2_6_EX_II
+  io.out.indirectCSR_EX_VI := rwSireg_EX_VI || rwSireg2_6_EX_VI
 }
 
 class csrAccessIO extends Bundle {
@@ -599,9 +612,7 @@ class xstateenIO extends Bundle {
 }
 
 class aiaIO extends Bundle {
-  val miselectIsIllegal = Bool()
-  val siselectIsIllegal = Bool()
-  val vsiselectIsIllegal = Bool()
+  val miselect = UInt(64.W)
   val siselect = UInt(64.W)
   val vsiselect = UInt(64.W)
   val mvienSEIE = Bool()
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 4912ce1bb1d..618df3ea777 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -105,6 +105,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   with VirtualSupervisorLevel
   with Unprivileged
   with CSRAIA
+  with CSRIND
   with HasExternalInterruptBundle
   with HasNonMaskableIRPBundle
   with CSREvents
@@ -317,6 +318,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     debugCSRMap ++
     aiaCSRMap ++
     customCSRMap ++
+    indCSRMap ++
     pmpCSRMap ++
     pmaCSRMap
 
@@ -328,6 +330,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     unprivilegedCSRMods ++
     debugCSRMods ++
     aiaCSRMods ++
+    indCSRMods ++
     customCSRMods ++
     pmpCSRMods ++
     pmaCSRMods
@@ -340,6 +343,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     unprivilegedCSROutMap ++
     debugCSROutMap ++
     aiaCSROutMap ++
+    indCSROutMap ++
     customCSROutMap ++
     pmpCSROutMap ++
     pmaCSROutMap
@@ -499,9 +503,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   permitMod.io.in.status.vsstatusFSOff := vsstatus.regOut.FS === ContextStatus.Off
   permitMod.io.in.status.vsstatusVSOff := vsstatus.regOut.VS === ContextStatus.Off
 
-  permitMod.io.in.aia.miselectIsIllegal  := miselect.isIllegal
-  permitMod.io.in.aia.siselectIsIllegal  := siselect.isIllegal
-  permitMod.io.in.aia.vsiselectIsIllegal := vsiselect.isIllegal
+  permitMod.io.in.aia.miselect := miselect.rdata
   permitMod.io.in.aia.siselect := siselect.rdata
   permitMod.io.in.aia.vsiselect := vsiselect.rdata
   permitMod.io.in.aia.mvienSEIE := mvien.regOut.SEIE.asBool
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
index 4a5845c4ac7..f94478801e9 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/StateEnBundle.scala
@@ -9,18 +9,18 @@ class SstateenBundle0 extends CSRBundle {
   override val len: Int = 32
   val JVT  = RO(2).withReset(0.U) // jvt CSR in Zcmt extension
   val FCSR = RO(1).withReset(0.U) // fp inst op 'x' register not f in Zfinx, Zdinx; misa.F =1 -> RO 0; misa.F=0 & this=0 -> V/EX_II
-  val C    = RW(0).withReset(1.U) // custom state enable, [m|h|s]stateen is standard, not custom.
+  val C    = RW(0).withReset(0.U) // custom state enable, [m|h|s]stateen is standard, not custom.
 }
 
 class HstateenBundle0 extends SstateenBundle0 {
   override val len: Int = 64
-  val SE0     = RW(63).withReset(1.U) // m: [h|s]stateen                h: sstateen
-  val ENVCFG  = RW(62).withReset(1.U) // m: [h|s]envcfg                 h: senvcfg
+  val SE0     = RW(63).withReset(0.U) // m: [h|s]stateen                h: sstateen
+  val ENVCFG  = RW(62).withReset(0.U) // m: [h|s]envcfg                 h: senvcfg
   // Bits in any stateen CSR that are defined to control state that a hart doesn’t implement are read-only
   // zeros for that hart. Smcsrind/Sscsrind is not implemented.
-  val CSRIND  = RO(60).withReset(1.U) // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
-  val AIA     = RW(59).withReset(1.U) // all other state added by the AIA and not controlled by bits 60 and 58
-  val IMSIC   = RW(58).withReset(1.U) // m: [vs|s]topei                 h: stopei
+  val CSRIND  = RW(60).withReset(0.U) // m: [vs|s]iselect, [vs|s]ireg*  h: siselect, sireg*
+  val AIA     = RW(59).withReset(0.U) // all other state added by the AIA and not controlled by bits 60 and 58
+  val IMSIC   = RW(58).withReset(0.U) // m: [vs|s]topei                 h: stopei
   val CONTEXT = RO(57).withReset(0.U) // m: [h|s]context in Sdtrig      h: scontext
 }
```
