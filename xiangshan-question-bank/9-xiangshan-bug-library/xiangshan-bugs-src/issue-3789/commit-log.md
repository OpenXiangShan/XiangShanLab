# Commit Log
- Issue: #3789
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3789
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3789
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3789
- Changed files: 16
- Additions: 197
- Deletions: 45

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index c9c1451937e..9d322ae0dfc 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit c9c1451937e61eefe5adfcf6e197d0ee7ca188f0
+Subproject commit 9d322ae0dfc3900696be2389b891e1d79da2b6d7
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
index 83816dd2b3e..99fd28f7c70 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
@@ -47,6 +47,7 @@ object CSRBundles {
     val STCE  =      RO(    63)           .withReset(0.U) // Sstc Enable
     val PBMTE =      RO(    62)           .withReset(0.U) // Svpbmt Enable
     val ADUE  =      RO(    61)           .withReset(0.U) // Svadu extension Enable
+    val DTE   =      RO(    59)           .withReset(0.U) // Ssdbltrp extension Enable
     val PMM   =      RO(33, 32)           .withReset(0.U) // Smnpm extension
     val CBZE  =      RW(     7)           .withReset(1.U) // Zicboz extension
     val CBCFE =      RW(     6)           .withReset(1.U) // Zicbom extension
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index 2a1f5b402e0..7b9ce4e6704 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -125,6 +125,7 @@ class TrapEntryEventInput(implicit val p: Parameters) extends Bundle with HasXSP
   val isFetchMalAddr = Input(Bool())
   val isFetchBkpt = Input(Bool())
   val trapIsForVSnonLeafPTE = Input(Bool())
+  val hasDTExcp = Input(Bool())
 
   // always current privilege
   val iMode = Input(new PrivState())
@@ -136,6 +137,9 @@ class TrapEntryEventInput(implicit val p: Parameters) extends Bundle with HasXSP
   val hstatus = Input(new HstatusBundle)
   val sstatus = Input(new SstatusBundle)
   val vsstatus = Input(new SstatusBundle)
+  // envcfg
+  val menvcfg = Input(new MEnvCfg)
+  val henvcfg = Input(new HEnvCfg)
 
   val pcFromXtvec = Input(UInt(XLEN.W))
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
index b4bbb2ffbca..b4e8437f3f2 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
@@ -15,7 +15,8 @@ import xiangshan.AddrTransType
 
 class MNretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
   val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.MNPP, _.MNPV, _.NMIE))
-  val mstatus   = ValidIO((new MstatusBundle).addInEvent(_.MPRV))
+  val mstatus   = ValidIO((new MstatusBundle).addInEvent(_.MPRV, _.MDT, _.SDT))
+  val vsstatus  = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val targetPc  = ValidIO(new TargetPCBundle)
 }
 
@@ -26,6 +27,7 @@ class MNretEventInput extends Bundle {
   val satp     = Input(new SatpBundle)
   val vsatp    = Input(new SatpBundle)
   val hgatp    = Input(new HgatpBundle)
+  val vsstatus = Input(new SstatusBundle)
 }
 
 class MNretEventModule(implicit p: Parameters) extends Module with CSREventBase {
@@ -49,18 +51,29 @@ class MNretEventModule(implicit p: Parameters) extends Module with CSREventBase
     sv48x4 = nextPrivState.isVirtual && vsatp.MODE === SatpMode.Bare && hgatp.MODE === HgatpMode.Sv48x4
   )
 
+  val outPrivState   = Wire(new PrivState)
+  outPrivState.PRVM := in.mnstatus.MNPP
+  outPrivState.V    := Mux(in.mnstatus.MNPP === PrivMode.M, VirtMode.Off.asUInt, in.mnstatus.MNPV.asUInt)
+
+  val mnretToM  = outPrivState.isModeM
+  val mnretToS  = outPrivState.isModeHS
+  val mnretToVU = outPrivState.isModeVU
+
   out := DontCare
 
   out.privState.valid := valid
   out.mnstatus .valid := valid
   out.targetPc .valid := valid
 
-  out.privState.bits.PRVM     := in.mnstatus.MNPP
-  out.privState.bits.V        := Mux(in.mnstatus.MNPP === PrivMode.M, VirtMode.Off.asUInt, in.mnstatus.MNPV.asUInt)
+  out.privState.bits          := outPrivState
   out.mnstatus.bits.MNPP      := PrivMode.U
   out.mnstatus.bits.MNPV      := VirtMode.Off.asUInt
   out.mnstatus.bits.NMIE      := 1.U
   out.mstatus.bits.MPRV       := Mux(in.mnstatus.MNPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
+  // clear MDT when mnret to below M
+  out.mstatus.bits.MDT        := Mux(mnretToM, in.mstatus.MDT.asBool, 0.U)
+  out.mstatus.bits.SDT        := Mux(mnretToM || mnretToS, in.mstatus.SDT.asBool, 0.U)
+  out.vsstatus.bits.SDT       := Mux(mnretToVU, 0.U, in.vsstatus.SDT.asBool)
   out.targetPc.bits.pc        := in.mnepc.asUInt
   out.targetPc.bits.raiseIPF  := instrAddrTransType.checkPageFault(in.mnepc.asUInt)
   out.targetPc.bits.raiseIAF  := instrAddrTransType.checkAccessFault(in.mnepc.asUInt)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
index a5f939e3346..6d725d749fc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
@@ -14,12 +14,14 @@ import xiangshan.AddrTransType
 
 
 class MretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val mstatus  = ValidIO((new MstatusBundle).addInEvent(_.MPP, _.MPV, _.MIE, _.MPIE, _.MPRV))
+  val mstatus  = ValidIO((new MstatusBundle).addInEvent(_.MPP, _.MPV, _.MIE, _.MPIE, _.MPRV, _.MDT, _.SDT))
+  val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val targetPc = ValidIO(new TargetPCBundle)
 }
 
 class MretEventInput extends Bundle {
   val mstatus  = Input(new MstatusBundle)
+  val vsstatus = Input(new SstatusBundle)
   val mepc     = Input(new Epc())
   val satp     = Input(new SatpBundle)
   val vsatp    = Input(new SatpBundle)
@@ -46,6 +48,13 @@ class MretEventModule(implicit p: Parameters) extends Module with CSREventBase {
     sv39x4 = nextPrivState.isVirtual && vsatp.MODE === SatpMode.Bare && hgatp.MODE === HgatpMode.Sv39x4,
     sv48x4 = nextPrivState.isVirtual && vsatp.MODE === SatpMode.Bare && hgatp.MODE === HgatpMode.Sv48x4
   )
+  val outPrivState   = Wire(new PrivState)
+  outPrivState.PRVM := in.mstatus.MPP
+  outPrivState.V    := Mux(in.mstatus.MPP === PrivMode.M, VirtMode.Off.asUInt, in.mstatus.MPV.asUInt)
+
+  val mretToM  = outPrivState.isModeM
+  val mretToS  = outPrivState.isModeHS
+  val mretToVu = outPrivState.isModeVU
 
   out := DontCare
 
@@ -53,13 +62,19 @@ class MretEventModule(implicit p: Parameters) extends Module with CSREventBase {
   out.mstatus  .valid := valid
   out.targetPc .valid := valid
 
-  out.privState.bits.PRVM     := in.mstatus.MPP
-  out.privState.bits.V        := Mux(in.mstatus.MPP === PrivMode.M, VirtMode.Off.asUInt, in.mstatus.MPV.asUInt)
+  out.privState.bits          := outPrivState
   out.mstatus.bits.MPP        := PrivMode.U
   out.mstatus.bits.MPV        := VirtMode.Off.asUInt
   out.mstatus.bits.MIE        := in.mstatus.MPIE
   out.mstatus.bits.MPIE       := 1.U
   out.mstatus.bits.MPRV       := Mux(in.mstatus.MPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
+  // clear MDT when return mret always execute in M mode
+  out.mstatus.bits.MDT    := 0.U
+  // clear sstatus.SDT when return mode below M and HS
+  out.mstatus.bits.SDT    := Mux(mretToM || mretToS, in.mstatus.SDT.asBool, 0.U)
+  // clear vsstatus.SDT when return to VU
+  out.vsstatus.bits.SDT   := Mux(mretToVu, 0.U, in.vsstatus.SDT.asBool)
+
   out.targetPc.bits.pc        := in.mepc.asUInt
   out.targetPc.bits.raiseIPF  := instrAddrTransType.checkPageFault(in.mepc.asUInt)
   out.targetPc.bits.raiseIAF  := instrAddrTransType.checkAccessFault(in.mepc.asUInt)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala
index 0fa64efd417..16d5c451a84 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala
@@ -15,15 +15,15 @@ import xiangshan.AddrTransType
 
 class SretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
   // Todo: write sstatus instead of mstatus
-  val mstatus = ValidIO((new MstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP, _.MPRV))
+  val mstatus = ValidIO((new MstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP, _.MPRV, _.MDT, _.SDT))
   val hstatus = ValidIO((new HstatusBundle).addInEvent(_.SPV))
-  val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP))
+  val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP, _.SDT))
   val targetPc = ValidIO(new TargetPCBundle)
 }
 
 class SretEventInput extends Bundle {
   val privState = Input(new PrivState)
-  val sstatus   = Input(new SstatusBundle)
+  val mstatus   = Input(new MstatusBundle)
   val hstatus   = Input(new HstatusBundle)
   val vsstatus  = Input(new SstatusBundle)
   val sepc      = Input(new Epc())
@@ -53,7 +53,9 @@ class SretEventModule(implicit p: Parameters) extends Module with CSREventBase {
     sv48x4 = nextPrivState.isVirtual && vsatp.MODE === SatpMode.Bare && hgatp.MODE === HgatpMode.Sv48x4
   )
 
-  private val sretInHSorM = in.privState.isModeM || in.privState.isModeHS
+  private val sretInM     = in.privState.isModeM
+  private val sretInHS    = in.privState.isModeHS
+  private val sretInHSorM = sretInM || sretInHS
   private val sretInVS    = in.privState.isModeVS
 
   private val xepc = Mux1H(Seq(
@@ -61,21 +63,29 @@ class SretEventModule(implicit p: Parameters) extends Module with CSREventBase {
     sretInVS    -> in.vsepc,
   )).asUInt
 
-  out := DontCare
-
-  out.privState.valid := valid
-  out.targetPc .valid := valid
 
-  out.privState.bits.PRVM     := Mux1H(Seq(
+  private val outPrivState   = Wire(new PrivState)
+  outPrivState.PRVM := Mux1H(Seq(
     // SPP is not PrivMode enum type, so asUInt
-    sretInHSorM -> in.sstatus.SPP.asUInt,
+    sretInHSorM -> in.mstatus.SPP.asUInt,
     sretInVS    -> in.vsstatus.SPP.asUInt,
   ))
-  out.privState.bits.V        := Mux1H(Seq(
+  outPrivState.V := Mux1H(Seq(
     sretInHSorM -> in.hstatus.SPV,
     sretInVS    -> in.privState.V, // keep
   ))
 
+  private val sretToVU    = outPrivState.isModeVU
+  private val sretToVS    = outPrivState.isModeVS
+  private val sretToU     = outPrivState.isModeHU
+
+  out := DontCare
+
+  out.privState.valid := valid
+  out.targetPc .valid := valid
+
+  out.privState.bits      := outPrivState
+
   // hstatus
   out.hstatus.valid           := valid && sretInHSorM
   out.hstatus.bits.SPV        := VirtMode.Off
@@ -83,15 +93,22 @@ class SretEventModule(implicit p: Parameters) extends Module with CSREventBase {
   // sstatus
   out.mstatus.valid           := valid && sretInHSorM
   out.mstatus.bits.SPP        := PrivMode.U.asUInt(0, 0) // SPP is not PrivMode enum type, so asUInt and shrink the width
-  out.mstatus.bits.SIE        := in.sstatus.SPIE
+  out.mstatus.bits.SIE        := in.mstatus.SPIE
   out.mstatus.bits.SPIE       := 1.U
   out.mstatus.bits.MPRV       := 0.U // sret will always leave M mode
+  out.mstatus.bits.MDT        := Mux(sretInM, 0.U, in.mstatus.MDT.asBool) // when execute return in M mode, set MDT 0
+  out.mstatus.bits.SDT        := MuxCase(in.mstatus.SDT.asBool, Seq(
+    sretInHS   -> 0.U, // sret will alway leave M mode
+    (sretInM && (sretToU || sretToVS || sretToVU))    -> 0.U
+  ))
+
 
   // vsstatus
   out.vsstatus.valid          := valid && sretInVS
   out.vsstatus.bits.SPP       := PrivMode.U.asUInt(0, 0) // SPP is not PrivMode enum type, so asUInt and shrink the width
   out.vsstatus.bits.SIE       := in.vsstatus.SPIE
   out.vsstatus.bits.SPIE      := 1.U
+  out.vsstatus.bits.SDT       := Mux(sretToVU || sretInVS, 0.U, in.vsstatus.SDT.asBool) // clear SDT when return to VU or sret in vs
 
   out.targetPc.bits.pc        := xepc
   out.targetPc.bits.raiseIPF  := instrAddrTransType.checkPageFault(xepc)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
index 8b796c58623..81b933f726f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
@@ -15,7 +15,7 @@ import xiangshan.AddrTransType
 class TrapEntryHSEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
 
   // Todo: use sstatus instead of mstatus
-  val mstatus = ValidIO((new MstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE))
+  val mstatus = ValidIO((new MstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE, _.SDT))
   val hstatus = ValidIO((new HstatusBundle ).addInEvent(_.SPV, _.SPVP, _.GVA))
   val sepc    = ValidIO((new Epc           ).addInEvent(_.epc))
   val scause  = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
@@ -124,6 +124,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   out.mstatus.bits.SPP          := current.privState.PRVM.asUInt(0, 0) // SPP is not PrivMode enum type, so asUInt and shrink the width
   out.mstatus.bits.SPIE         := current.sstatus.SIE
   out.mstatus.bits.SIE          := 0.U
+  out.mstatus.bits.SDT          := in.menvcfg.DTE.asBool // when DTE open set SDT to 1, else SDT is readonly 0
   // hstatus
   out.hstatus.bits.SPV          := current.privState.V
     // SPVP is not PrivMode enum type, so asUInt and shrink the width
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
index 0c7f8f21d85..5ce1bc5b37f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
@@ -13,7 +13,7 @@ import xiangshan.AddrTransType
 
 class TrapEntryMEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
 
-  val mstatus   = ValidIO((new MstatusBundle ).addInEvent(_.MPV, _.MPP, _.GVA, _.MPIE, _.MIE))
+  val mstatus   = ValidIO((new MstatusBundle ).addInEvent(_.MPV, _.MPP, _.GVA, _.MPIE, _.MIE, _.MDT))
   val mepc      = ValidIO((new Epc           ).addInEvent(_.epc))
   val mcause    = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
   val mtval     = ValidIO((new OneFieldBundle).addInEvent(_.ALL))
@@ -32,6 +32,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   private val satp  = current.satp
   private val vsatp = current.vsatp
   private val hgatp = current.hgatp
+  private val isDTExcp = current.hasDTExcp
 
   private val highPrioTrapNO = in.causeNO.ExceptionCode.asUInt
   private val isException = !in.causeNO.Interrupt.asBool
@@ -96,6 +97,8 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
     (isLSGuestExcp                                         ) -> trapMemGPA,
   ))
 
+  private val precause = Cat(isInterrupt, highPrioTrapNO)
+
   out := DontCare
 
   out.privState.valid := valid
@@ -113,11 +116,12 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   out.mstatus.bits.GVA          := tvalFillGVA
   out.mstatus.bits.MPIE         := current.mstatus.MIE
   out.mstatus.bits.MIE          := 0.U
+  out.mstatus.bits.MDT          := 1.U
   out.mepc.bits.epc             := Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1))
   out.mcause.bits.Interrupt     := isInterrupt
-  out.mcause.bits.ExceptionCode := highPrioTrapNO
+  out.mcause.bits.ExceptionCode := Mux(isDTExcp, ExceptionNO.EX_DT.U, highPrioTrapNO)
   out.mtval.bits.ALL            := Mux(isFetchMalAddr, in.fetchMalTval, tval)
-  out.mtval2.bits.ALL           := tval2 >> 2
+  out.mtval2.bits.ALL           := Mux(isDTExcp, precause, tval2 >> 2)
   out.mtinst.bits.ALL           := Mux(isFetchGuestExcp && in.trapIsForVSnonLeafPTE || isLSGuestExcp && in.memExceptionIsForVSnonLeafPTE, 0x3000.U, 0.U)
   out.targetPc.bits.pc          := in.pcFromXtvec
   out.targetPc.bits.raiseIPF    := false.B
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
index 63045276d5d..d93720bd41f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
@@ -14,7 +14,7 @@ import xiangshan.AddrTransType
 
 class TrapEntryVSEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
 
-  val vsstatus = ValidIO((new SstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE))
+  val vsstatus = ValidIO((new SstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE, _.SDT))
   val vsepc    = ValidIO((new Epc           ).addInEvent(_.epc))
   val vscause  = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
   val vstval   = ValidIO((new OneFieldBundle).addInEvent(_.ALL))
@@ -122,6 +122,7 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   out.vsstatus.bits.SPP          := current.privState.PRVM.asUInt(0, 0) // SPP is not PrivMode enum type, so asUInt and shrink the width
   out.vsstatus.bits.SPIE         := current.vsstatus.SIE
   out.vsstatus.bits.SIE          := 0.U
+  out.vsstatus.bits.SDT          := in.henvcfg.DTE.asBool // when DTE open set SDT to 1, else SDT is readonly 0
   // SPVP is not PrivMode enum type, so asUInt and shrink the width
   out.vsepc.bits.epc             := Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1))
   out.vscause.bits.Interrupt     := isInterrupt
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 094d5ab587f..edd669b206c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -62,14 +62,16 @@ trait HypervisorLevel { self: NewCSR =>
     .setAddr(CSRs.hvictl)
 
   val henvcfg = Module(new CSRModule("Henvcfg", new HEnvCfg) with HasHypervisorEnvBundle {
-    when (!menvcfg.STCE.asBool) {
+    when(!menvcfg.STCE) {
       regOut.STCE := 0.U
     }
-    when (!menvcfg.PBMTE) {
+    when(!menvcfg.PBMTE) {
       regOut.PBMTE := 0.U
     }
-  })
-    .setAddr(CSRs.henvcfg)
+    when(!menvcfg.DTE) {
+      regOut.DTE := 0.U
+    }
+  }).setAddr(CSRs.henvcfg)
 
   val htval = Module(new CSRModule("Htval", new XtvalBundle) with TrapEntryHSEventSinkBundle)
     .setAddr(CSRs.htval)
@@ -340,6 +342,11 @@ class HEnvCfg extends EnvCfg {
     this.STCE.setRW().withReset(1.U)
   }
   this.PBMTE.setRW().withReset(0.U)
+  if (CSRConfig.EXT_DBLTRP) {
+    // software write envcfg to open ssdbltrp if need
+    // set 0 to pass ci
+    this.DTE.setRW().withReset(0.U)
+  }
 }
 
 trait HypervisorBundle { self: CSRModule[_] =>
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index ec23342a36c..72bde5b7d1a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -452,12 +452,14 @@ class MstatusBundle extends CSRBundle {
   val TVM  = CSRRWField     (20).withReset(0.U)
   val TW   = CSRRWField     (21).withReset(0.U)
   val TSR  = CSRRWField     (22).withReset(0.U)
+  val SDT  = CSRRWField     (24).withReset(0.U)
   val UXL  = XLENField      (33, 32).withReset(XLENField.XLEN64)
   val SXL  = XLENField      (35, 34).withReset(XLENField.XLEN64)
   val SBE  = CSRROField     (36).withReset(0.U)
   val MBE  = CSRROField     (37).withReset(0.U)
   val GVA  = CSRRWField     (38).withReset(0.U)
   val MPV  = VirtMode       (39).withReset(0.U)
+  val MDT  = CSRRWField     (42).withReset(mdtInit.U)
   val SD   = CSRROField     (63,
     (_, _) => FS === ContextStatus.Dirty || VS === ContextStatus.Dirty
   )
@@ -471,6 +473,7 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   with MNretEventSinkBundle
   with SretEventSinkBundle
   with HasRobCommitBundle
+  with HasMachineEnvBundle
 {
   val mstatus = IO(Output(bundle))
   val sstatus = IO(Output(new SstatusBundle))
@@ -496,9 +499,23 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
     assert(reg.VS =/= ContextStatus.Off, "The [m|s]status.VS should not be Off when set dirty, please check decode")
     reg.VS := ContextStatus.Dirty
   }
-
+  // when MDT is explicitly written by 1, clear MIE
+  // only when reg.MDT is zero or wdata.MDT is zero , MIE can be explicitly written by 1
+  when (w.wdataFields.MDT && w.wen) {
+    reg.MIE := false.B
+  }
+  // when DTE is zero, SDT field is read-only zero(write any, read zero, side effect of write 1 is block)
+  val writeSDT = Wire(Bool())
+  writeSDT := Mux(this.menvcfg.DTE.asBool, (w.wdataFields.SDT && w.wen) || (wAliasSstatus.wdataFields.SDT && wAliasSstatus.wen), 0.U)
+  when (!this.menvcfg.DTE) {
+    regOut.SDT := false.B
+  }
+  // SDT and SIE is the same as MDT and MIE
+  when (writeSDT) {
+    reg.SIE := false.B
+  }
   // read connection
-  mstatus :|= reg
+  mstatus :|= regOut
   sstatus := mstatus
   rdata := mstatus.asUInt
   sstatusRdata := sstatus.asUInt
@@ -628,6 +645,11 @@ class MEnvCfg extends EnvCfg {
     this.STCE.setRW().withReset(1.U)
   }
   this.PBMTE.setRW().withReset(0.U)
+  if (CSRConfig.EXT_DBLTRP) {
+    // software write envcfg to open ssdbltrp if need
+    // set 0 to pass ci
+    this.DTE.setRW().withReset(0.U)
+  }
 }
 
 object MarchidField extends CSREnum with ROApply {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 508b98cd00d..b1d6e1bde00 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -67,7 +67,12 @@ object CSRConfig {
 
   final val EXT_SSTC = true
 
+  final val EXT_DBLTRP = true
+
   final val PPNLength = 44
+  // TODO: as current test not support clean mdt , we set mstatus->mdt = 0 to allow exception in m-mode
+  final val mdtInit = 0
+
 }
 
 class NewCSRInput(implicit p: Parameters) extends Bundle {
@@ -355,10 +360,13 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapHandleMod.io.in.trapInfo.bits.intrVec := intrVec
   trapHandleMod.io.in.trapInfo.bits.isInterrupt := trapIsInterrupt
   trapHandleMod.io.in.privState := privState
-  trapHandleMod.io.in.mideleg := mideleg.regOut
-  trapHandleMod.io.in.medeleg := medeleg.regOut
-  trapHandleMod.io.in.hideleg := hideleg.regOut
-  trapHandleMod.io.in.hedeleg := hedeleg.regOut
+  trapHandleMod.io.in.mstatus  := mstatus.regOut
+  trapHandleMod.io.in.vsstatus := vsstatus.regOut
+  trapHandleMod.io.in.mnstatus := mnstatus.regOut
+  trapHandleMod.io.in.mideleg  := mideleg.regOut
+  trapHandleMod.io.in.medeleg  := medeleg.regOut
+  trapHandleMod.io.in.hideleg  := hideleg.regOut
+  trapHandleMod.io.in.hedeleg  := hedeleg.regOut
   trapHandleMod.io.in.mvien := mvien.regOut
   trapHandleMod.io.in.hvien := hvien.regOut
   trapHandleMod.io.in.mtvec := mtvec.regOut
@@ -368,6 +376,8 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   val entryPrivState = trapHandleMod.io.out.entryPrivState
   val entryDebugMode = WireInit(false.B)
+  val dbltrpToMN     = trapHandleMod.io.out.dbltrpToMN
+  val hasDTExcp      = trapHandleMod.io.out.hasDTExcp
 
   // PMP
   val pmpEntryMod = Module(new PMPEntryHandleModule)
@@ -612,6 +622,12 @@ class NewCSR(implicit val p: Parameters) extends Module
         m.menvcfg := menvcfg.regOut
       case _ =>
     }
+    mod match {
+      case m: HasVirtualSupervisorEnvBundle =>
+        m.henvcfg := henvcfg.regOut
+        m.menvcfg := menvcfg.regOut
+      case _ =>
+    }
     mod match {
       case m: HasIpIeBundle =>
         m.mideleg := mideleg.regOut
@@ -655,10 +671,10 @@ class NewCSR(implicit val p: Parameters) extends Module
     println(mod.dumpFields)
   }
 
-  trapEntryMEvent.valid  := hasTrap && entryPrivState.isModeM && !entryDebugMode  && !debugMode && !nmi
-  trapEntryMNEvent.valid := hasTrap && nmi && !debugMode
-  trapEntryHSEvent.valid := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode
-  trapEntryVSEvent.valid := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode
+  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !debugMode && mnstatus.regOut.NMIE
+  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
+  trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
+  trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
 
   Seq(trapEntryMEvent, trapEntryMNEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
     eMod.in match {
@@ -673,6 +689,7 @@ class NewCSR(implicit val p: Parameters) extends Module
         in.isFetchMalAddr := trapIsFetchMalAddr
         in.isFetchBkpt := trapIsFetchBkpt
         in.trapIsForVSnonLeafPTE := trapIsForVSnonLeafPTE
+        in.hasDTExcp := hasDTExcp
 
         in.iMode.PRVM := PRVM
         in.iMode.V := V
@@ -687,6 +704,9 @@ class NewCSR(implicit val p: Parameters) extends Module
         in.vsstatus := vsstatus.regOut
         in.pcFromXtvec := trapHandleMod.io.out.pcFromXtvec
 
+        in.menvcfg := menvcfg.regOut
+        in.henvcfg := henvcfg.regOut
+
         in.satp  := satp.regOut
         in.vsatp := vsatp.regOut
         in.hgatp := hgatp.regOut
@@ -704,6 +724,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   mnretEvent.in match {
     case in =>
       in.mstatus := mstatus.regOut
+      in.vsstatus := vsstatus.regOut
       in.mnepc   := mnepc.regOut
       in.mnstatus:= mnstatus.regOut
       in.satp := satp.regOut
@@ -715,6 +736,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   mretEvent.in match {
     case in =>
       in.mstatus := mstatus.regOut
+      in.vsstatus := vsstatus.regOut
       in.mepc := mepc.regOut
       in.satp := satp.regOut
       in.vsatp := vsatp.regOut
@@ -725,7 +747,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   sretEvent.in match {
     case in =>
       in.privState := privState
-      in.sstatus := mstatus.sstatus
+      in.mstatus := mstatus.regOut
       in.hstatus := hstatus.regOut
       in.vsstatus := vsstatus.regOut
       in.sepc := sepc.regOut
@@ -1010,7 +1032,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   debugMod.io.in.tdata1Wdata               := wdata
   debugMod.io.in.triggerCanRaiseBpExp      := triggerCanRaiseBpExp
 
-  entryDebugMode := debugMod.io.out.hasDebugTrap && !debugMode
+  entryDebugMode := debugMod.io.out.hasDebugTrap && !debugMode && !nmi
 
   trapEntryDEvent.valid                       := entryDebugMode
   trapEntryDEvent.in.hasDebugIntr             := debugMod.io.out.hasDebugIntr
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
index 3a4af320d6e..a5ee6dfd23e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
@@ -210,6 +210,7 @@ class SstatusBundle extends CSRBundle {
   val XS   = ContextStatusRO(16, 15).withReset(0.U)
   val SUM  = CSRWARLField   (18, wNoFilter).withReset(0.U)
   val MXR  = CSRWARLField   (19, wNoFilter).withReset(0.U)
+  val SDT  = CSRWARLField   (24, wNoFilter).withReset(0.U)
   val UXL  = XLENField      (33, 32).withReset(XLENField.XLEN64)
   val SD   = CSRROField     (63, (_, _) => FS === ContextStatus.Dirty || VS === ContextStatus.Dirty)
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index ac431ce859e..3c2eee627c6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -13,6 +13,9 @@ class TrapHandleModule extends Module {
 
   private val trapInfo = io.in.trapInfo
   private val privState = io.in.privState
+  private val mstatus  = io.in.mstatus
+  private val vsstatus = io.in.vsstatus
+  private val mnstatus = io.in.mnstatus
   private val mideleg = io.in.mideleg.asUInt
   private val hideleg = io.in.hideleg.asUInt
   private val medeleg = io.in.medeleg.asUInt
@@ -110,6 +113,7 @@ class TrapHandleModule extends Module {
 
   private val handleTrapUnderHS = !privState.isModeM && hsHasTrap
   private val handleTrapUnderVS = privState.isVirtual && vsHasTrap
+  private val handleTrapUnderM = !handleTrapUnderVS && !handleTrapUnderHS
 
   // Todo: support more interrupt and exception
   private val exceptionRegular = OHToUInt(highestPrioEX)
@@ -118,20 +122,33 @@ class TrapHandleModule extends Module {
 
   private val causeNO = Mux(hasIR, interruptNO, exceptionNO)
 
+  // sm/ssdbltrp
+  private val m_EX_DT  = handleTrapUnderM  && mstatus.MDT.asBool  && hasTrap
+  private val s_EX_DT  = handleTrapUnderHS && mstatus.SDT.asBool  && hasTrap
+  private val vs_EX_DT = handleTrapUnderVS && vsstatus.SDT.asBool && hasTrap
+
+  private val dbltrpToMN = m_EX_DT && mnstatus.NMIE.asBool // NMI not allow double trap
+  private val hasDTExcp  = m_EX_DT || s_EX_DT || vs_EX_DT
+
+  private val trapToHS = handleTrapUnderHS && !s_EX_DT && !vs_EX_DT
+  private val traptoVS = handleTrapUnderVS && !vs_EX_DT
+
   private val xtvec = MuxCase(io.in.mtvec, Seq(
-    handleTrapUnderVS -> io.in.vstvec,
-    handleTrapUnderHS -> io.in.stvec
+    traptoVS -> io.in.vstvec,
+    trapToHS -> io.in.stvec
   ))
   private val pcFromXtvec = Cat(xtvec.addr.asUInt + Mux(xtvec.mode === XtvecMode.Vectored && hasIR, interruptNO(5, 0), 0.U), 0.U(2.W))
 
   io.out.entryPrivState := MuxCase(default = PrivState.ModeM, mapping = Seq(
-    handleTrapUnderVS -> PrivState.ModeVS,
-    handleTrapUnderHS -> PrivState.ModeHS,
+    traptoVS -> PrivState.ModeVS,
+    trapToHS -> PrivState.ModeHS,
   ))
 
   io.out.causeNO.Interrupt := hasIR
   io.out.causeNO.ExceptionCode := causeNO
   io.out.pcFromXtvec := pcFromXtvec
+  io.out.hasDTExcp := hasDTExcp
+  io.out.dbltrpToMN := dbltrpToMN
 
   def filterIRQs(group: Seq[Int], originIRQ: UInt): Seq[Bool] = {
     group.map(irqNum => originIRQ(irqNum))
@@ -173,6 +190,9 @@ class TrapHandleIO extends Bundle {
       val singleStep = Bool()
     })
     val privState = new PrivState
+    val mstatus = new MstatusBundle
+    val vsstatus = new SstatusBundle
+    val mnstatus = new MnstatusBundle
     val mideleg = new MidelegBundle
     val medeleg = new MedelegBundle
     val hideleg = new HidelegBundle
@@ -190,6 +210,8 @@ class TrapHandleIO extends Bundle {
   val out = new Bundle {
     val entryPrivState = new PrivState
     val causeNO = new CauseBundle
+    val dbltrpToMN = Bool()
+    val hasDTExcp = Bool()
     val pcFromXtvec = UInt()
   }
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
index 0dbe0da7ce2..99f836569a8 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
@@ -7,7 +7,7 @@ import freechips.rocketchip.rocket.CSRs
 import utility.{SignExt, ZeroExt}
 import xiangshan.backend.fu.NewCSR.CSRBundles._
 import xiangshan.backend.fu.NewCSR.CSRDefines.{VirtMode, CSRROField => RO, CSRRWField => RW, CSRWARLField => WARL, CSRWLRLField => WLRL, _}
-import xiangshan.backend.fu.NewCSR.CSREvents.{SretEventSinkBundle, TrapEntryVSEventSinkBundle}
+import xiangshan.backend.fu.NewCSR.CSREvents.{SretEventSinkBundle, MretEventSinkBundle, MNretEventSinkBundle, TrapEntryVSEventSinkBundle}
 import xiangshan.backend.fu.NewCSR.CSREnumTypeImplicitCast._
 import xiangshan.backend.fu.NewCSR.CSRBundleImplicitCast._
 import xiangshan.backend.fu.NewCSR.CSRConfig.PPNLength
@@ -20,8 +20,11 @@ trait VirtualSupervisorLevel { self: NewCSR with SupervisorLevel with Hypervisor
   val vsstatus = Module(
     new CSRModule("VSstatus", new SstatusBundle)
       with SretEventSinkBundle
+      with MretEventSinkBundle
+      with MNretEventSinkBundle
       with TrapEntryVSEventSinkBundle
       with HasRobCommitBundle
+      with HasVirtualSupervisorEnvBundle
     {
       when ((robCommit.fsDirty || writeFCSR) && isVirtMode) {
         assert(reg.FS =/= ContextStatus.Off, "The vsstatus.FS should not be Off when set dirty, please check decode")
@@ -32,6 +35,17 @@ trait VirtualSupervisorLevel { self: NewCSR with SupervisorLevel with Hypervisor
         assert(reg.VS =/= ContextStatus.Off, "The vsstatus.VS should not be Off when set dirty, please check decode")
         reg.VS := ContextStatus.Dirty
       }
+      // when menvcfg or henvcfg.DTE close,  vsstatus.SDT is read-only
+      val writeSDT = Wire(Bool())
+      writeSDT := Mux(this.menvcfg.DTE && this.henvcfg.DTE, w.wdataFields.SDT.asBool, 0.U)
+      when (!(this.menvcfg.DTE && this.henvcfg.DTE)) {
+        regOut.SDT := false.B
+      }
+      // SDT and SIE is the same as mstatus
+      when (writeSDT && w.wen ) {
+        reg.SIE := false.B
+      }
+
     }
   )
     .setAddr(CSRs.vsstatus)
@@ -296,3 +310,8 @@ trait VirtualSupervisorBundle { self: CSRModule[_] =>
   val v = IO(Input(Bool()))
   val hgatp = IO(Input(new HgatpBundle))
 }
+
+trait HasVirtualSupervisorEnvBundle { self: CSRModule[_] =>
+  val menvcfg = IO(Input(new MEnvCfg))
+  val henvcfg = IO(Input(new HEnvCfg))
+}
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index 4b1d3a4ef09..37842aa06d7 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -817,6 +817,7 @@ package object xiangshan {
     def loadPageFault       = 13
     // def singleStep          = 14
     def storePageFault      = 15
+    def doubleTrap          = 16
     def instrGuestPageFault = 20
     def loadGuestPageFault  = 21
     def virtualInstr        = 22
@@ -838,6 +839,7 @@ package object xiangshan {
     def EX_IPF    = instrPageFault
     def EX_LPF    = loadPageFault
     def EX_SPF    = storePageFault
+    def EX_DT     = doubleTrap
     def EX_IGPF   = instrGuestPageFault
     def EX_LGPF   = loadGuestPageFault
     def EX_VI     = virtualInstr
@@ -860,6 +862,7 @@ package object xiangshan {
     def getStoreFault = Seq(EX_SAM, EX_SAF, EX_SPF)
 
     def priorities = Seq(
+      doubleTrap,
       breakPoint, // TODO: different BP has different priority
       instrPageFault,
       instrGuestPageFault,
```
