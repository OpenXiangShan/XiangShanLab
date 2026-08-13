# Commit Log
- Issue: #3480
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3480
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3480
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3480
- Changed files: 17
- Additions: 343
- Deletions: 45

## Files
- `difftest`
- `ready-to-run`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`
- `src/main/scala/xiangshan/backend/fu/util/CSRConst.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index abfdcdf4df7..c66788be320 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit abfdcdf4df750d4990851309f77ddafbb91f8439
+Subproject commit c66788be3206b685e9241a6fa526bcc2e294438a
diff --git a/ready-to-run b/ready-to-run
index 4fe11f9fd97..ce04973152f 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 4fe11f9fd970f3ea6655105e571e99805d8a160c
+Subproject commit ce04973152f0e5a0dc02d3968977214852ef54aa
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index ea3f7b298f8..4db0a9e8e0a 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -359,6 +359,12 @@ class ExternalInterruptIO(implicit p: Parameters) extends XSBundle {
   val meip = Input(Bool())
   val seip = Input(Bool())
   val debug = Input(Bool())
+  val nmi = new NonmaskableInterruptIO()
+}
+
+class NonmaskableInterruptIO(implicit p: Parameters) extends XSBundle {
+  val nmi = Input(Bool())
+  // reserve for other nmi type
 }
 
 class CSRSpecialIO(implicit p: Parameters) extends XSBundle {
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index e3a33271283..52f10b14f3e 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -1716,6 +1716,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
     x.externalInterrupt.meip  := outer.plic_int_sink.in.head._1(0)
     x.externalInterrupt.seip  := outer.plic_int_sink.in.last._1(0)
     x.externalInterrupt.debug := outer.debug_int_sink.in.head._1(0)
+    x.externalInterrupt.nmi.nmi := false.B
     x.msiInfo           := DelayNWithValid(io.fromTopToBackend.msiInfo, 1)
     x.clintTime         := DelayNWithValid(io.fromTopToBackend.clintTime, 1)
   }
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 46b33b32db0..148963a034b 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -21,6 +21,7 @@ import chisel3._
 import chisel3.util._
 import freechips.rocketchip.rocket.CSRs
 import freechips.rocketchip.rocket.Instructions._
+import freechips.rocketchip.rocket.CustomInstructions._
 import freechips.rocketchip.util.uintToBitPat
 import utility._
 import utils._
@@ -220,6 +221,7 @@ object XDecode extends DecodeConstants {
     ECALL   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
     SRET    -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
     MRET    -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
+    MNRET   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
     DRET    -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.jmp, SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
     WFI     -> XSDecode(SrcType.pc , SrcType.imm, SrcType.X, FuType.csr, CSROpType.wfi, SelImm.X    , xWen = T, noSpec = T, blockBack = T),
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index 0b3bcebd81c..a1a52ce2ac8 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -15,15 +15,19 @@ trait CSREvents { self: NewCSR =>
 
   val trapEntryMEvent = Module(new TrapEntryMEventModule)
 
+  val trapEntryMNEvent = Module(new TrapEntryMNEventModule())
+
   val trapEntryHSEvent = Module(new TrapEntryHSEventModule)
 
   val trapEntryVSEvent = Module(new TrapEntryVSEventModule)
 
-  val mretEvent = Module(new MretEventModule)
+  val mretEvent  = Module(new MretEventModule)
+
+  val mnretEvent = Module(new MNretEventModule)
 
-  val sretEvent = Module(new SretEventModule)
+  val sretEvent  = Module(new SretEventModule)
 
-  val dretEvent = Module(new DretEventModule)
+  val dretEvent  = Module(new DretEventModule)
 
   val events: Seq[Module with CSREventBase] = Seq(
     trapEntryDEvent,
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
new file mode 100644
index 00000000000..e42551acaa6
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
@@ -0,0 +1,65 @@
+package xiangshan.backend.fu.NewCSR.CSREvents
+
+import chisel3._
+import chisel3.util._
+import utility.{SignExt, ZeroExt}
+import xiangshan.ExceptionNO
+import xiangshan.ExceptionNO._
+import xiangshan.backend.fu.NewCSR.CSRBundles.{CauseBundle, OneFieldBundle, PrivState}
+import xiangshan.backend.fu.NewCSR.CSRConfig.{VaddrMaxWidth, XLEN}
+import xiangshan.backend.fu.NewCSR.CSRDefines.{PrivMode, SatpMode, VirtMode}
+import xiangshan.backend.fu.NewCSR._
+
+
+class MNretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
+  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.MNPP, _.MNPV, _.NMIE))
+  val mstatus   = ValidIO((new MstatusBundle).addInEvent(_.MPRV))
+  val targetPc  = ValidIO(UInt(VaddrMaxWidth.W))
+
+  override def getBundleByName(name: String): ValidIO[CSRBundle] = {
+    name match {
+      case "mnstatus"  => this.mnstatus
+      case "mstatus"   => this.mstatus
+    }
+  }
+}
+
+class MNretEventInput extends Bundle {
+  val mnstatus = Input(new MnstatusBundle)
+  val mstatus  = Input(new MstatusBundle)
+  val mnepc   = Input(new Epc())
+}
+
+class MNretEventModule extends Module with CSREventBase {
+  val in = IO(new MNretEventInput)
+  val out = IO(new MNretEventOutput)
+
+  out := DontCare
+
+  out.privState.valid := valid
+  out.mnstatus .valid := valid
+  out.targetPc .valid := valid
+
+  out.privState.bits.PRVM := in.mnstatus.MNPP
+  out.privState.bits.V    := Mux(in.mnstatus.MNPP === PrivMode.M, VirtMode.Off.asUInt, in.mnstatus.MNPV.asUInt)
+  out.mnstatus.bits.MNPP  := PrivMode.U
+  out.mnstatus.bits.MNPV  := VirtMode.Off.asUInt
+  out.mnstatus.bits.NMIE  := 1.U
+  out.mstatus.bits.MPRV   := Mux(in.mnstatus.MNPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
+  out.targetPc.bits       := in.mnepc.asUInt
+}
+
+trait MNretEventSinkBundle { self: CSRModule[_] =>
+  val retFromMN = IO(Flipped(new MNretEventOutput))
+
+  private val updateBundle: ValidIO[CSRBundle] = retFromMN.getBundleByName(self.modName.toLowerCase())
+
+  (reg.asInstanceOf[CSRBundle].getFields zip updateBundle.bits.getFields).foreach { case (sink, source) =>
+    if (updateBundle.bits.eventFields.contains(source)) {
+      when(updateBundle.valid) {
+        sink := source
+      }
+    }
+  }
+
+}
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
index 91f793f28a4..95f41199a1f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
@@ -44,6 +44,7 @@ class MretEventModule extends Module with CSREventBase {
   out.privState.bits.PRVM := in.mstatus.MPP
   out.privState.bits.V    := Mux(in.mstatus.MPP === PrivMode.M, VirtMode.Off.asUInt, in.mstatus.MPV.asUInt)
   out.mstatus.bits.MPP    := PrivMode.U
+  out.mstatus.bits.MPV    := VirtMode.Off.asUInt
   out.mstatus.bits.MIE    := in.mstatus.MPIE
   out.mstatus.bits.MPIE   := 1.U
   out.mstatus.bits.MPRV   := Mux(in.mstatus.MPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala
new file mode 100644
index 00000000000..3d2a98bc0f9
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala
@@ -0,0 +1,78 @@
+package xiangshan.backend.fu.NewCSR.CSREvents
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.SignExt
+import xiangshan.ExceptionNO
+import xiangshan.backend.fu.NewCSR.CSRBundles.{CauseBundle, OneFieldBundle, PrivState}
+import xiangshan.backend.fu.NewCSR.CSRConfig.{VaddrMaxWidth, XLEN}
+import xiangshan.backend.fu.NewCSR._
+
+class TrapEntryMNEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
+  val mnstatus = ValidIO((new MnstatusBundle ).addInEvent(_.MNPP, _.MNPV, _.NMIE))
+  val mnepc    = ValidIO((new Epc           ).addInEvent(_.epc))
+  val mncause  = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
+  val targetPc = ValidIO(UInt(VaddrMaxWidth.W))
+
+  def getBundleByName(name: String): Valid[CSRBundle] = {
+    name match {
+      case "mnstatus"  => this.mnstatus
+      case "mnepc"     => this.mnepc
+      case "mncause"   => this.mncause
+    }
+  }
+}
+
+class TrapEntryMNEventModule(implicit val p: Parameters) extends Module with CSREventBase {
+  val in = IO(new TrapEntryEventInput)
+  val out = IO(new TrapEntryMNEventOutput)
+
+  private val current = in
+  private val iMode = current.iMode
+  private val satp  = current.satp
+  private val vsatp = current.vsatp
+  private val hgatp = current.hgatp
+
+  private val highPrioTrapNO = in.causeNO.ExceptionCode.asUInt
+  private val isInterrupt = in.causeNO.Interrupt.asBool
+
+  private val trapPC = genTrapVA(
+    iMode,
+    satp,
+    vsatp,
+    hgatp,
+    in.trapPc,
+  )
+  out := DontCare
+
+  out.privState.valid := valid
+  out.mnstatus.valid  := valid
+  out.mnepc.valid     := valid
+  out.mncause.valid   := valid
+  out.targetPc.valid  := valid
+
+  out.privState.bits             := PrivState.ModeM
+  out.mnstatus.bits.MNPP         := current.privState.PRVM
+  out.mnstatus.bits.MNPV         := current.privState.V
+  out.mnstatus.bits.NMIE         := 0.U
+  out.mnepc.bits.epc             := trapPC(VaddrMaxWidth - 1, 1)
+  out.mncause.bits.Interrupt     := isInterrupt
+  out.mncause.bits.ExceptionCode := highPrioTrapNO
+  out.targetPc.bits              := in.pcFromXtvec
+
+}
+
+trait TrapEntryMNEventSinkBundle { self: CSRModule[_] =>
+  val trapToMN = IO(Flipped(new TrapEntryMNEventOutput))
+
+  private val updateBundle: ValidIO[CSRBundle] = trapToMN.getBundleByName(self.modName.toLowerCase())
+
+  (reg.asInstanceOf[CSRBundle].getFields zip updateBundle.bits.getFields).foreach { case (sink, source) =>
+    if (updateBundle.bits.eventFields.contains(source)) {
+      when(updateBundle.valid) {
+        sink := source
+      }
+    }
+  }
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
index 4affb290d1a..cd3368dce77 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
@@ -20,7 +20,8 @@ class CSRPermitModule extends Module {
 
   private val csrAccess = WireInit(ren || wen)
 
-  private val (mret, sret, dret) = (
+  private val (mnret, mret, sret, dret) = (
+    io.in.mnret,
     io.in.mret,
     io.in.sret,
     io.in.dret,
@@ -122,6 +123,10 @@ class CSRPermitModule extends Module {
 
   private val rwIllegal = csrIsRO && wen
 
+  private val mnret_EX_II = mnret && !privState.isModeM
+  private val mnret_EX_VI = false.B
+  private val mnretIllegal = mnret_EX_VI || mnret_EX_II
+
   private val mret_EX_II = mret && !privState.isModeM
   private val mret_EX_VI = false.B
   private val mretIllegal = mret_EX_II || mret_EX_VI
@@ -262,16 +267,17 @@ class CSRPermitModule extends Module {
 
   // Todo: check correct
   io.out.EX_II :=  csrAccess && !privilegeLegal && (!privState.isVirtual || privState.isVirtual && csrIsM) ||
-    rwIllegal || mret_EX_II || sret_EX_II || rwSatp_EX_II || accessHPM_EX_II ||
+    rwIllegal || mnret_EX_II || mret_EX_II || sret_EX_II || rwSatp_EX_II || accessHPM_EX_II ||
     rwStimecmp_EX_II || rwCustom_EX_II || fpVec_EX_II || dret_EX_II || xstateControlAccess_EX_II || rwStopei_EX_II ||
     rwMireg_EX_II || rwSireg_EX_II || rwVSireg_EX_II
   io.out.EX_VI := (csrAccess && !privilegeLegal && privState.isVirtual && !csrIsM ||
-    mret_EX_VI || sret_EX_VI || rwSatp_EX_VI || accessHPM_EX_VI || rwStimecmp_EX_VI || rwSireg_EX_VI || rwSip_Sie_EX_VI) && !rwIllegal || xstateControlAccess_EX_VI
+    mnret_EX_VI || mret_EX_VI || sret_EX_VI || rwSatp_EX_VI || accessHPM_EX_VI || rwStimecmp_EX_VI || rwSireg_EX_VI || rwSip_Sie_EX_VI) && !rwIllegal || xstateControlAccess_EX_VI
 
-  io.out.hasLegalWen  := wen  && !(io.out.EX_II || io.out.EX_VI)
-  io.out.hasLegalMret := mret && !mretIllegal
-  io.out.hasLegalSret := sret && !sretIllegal
-  io.out.hasLegalDret := dret && !dretIllegal
+  io.out.hasLegalWen   := wen   && !(io.out.EX_II || io.out.EX_VI)
+  io.out.hasLegalMNret := mnret && !mnretIllegal
+  io.out.hasLegalMret  := mret  && !mretIllegal
+  io.out.hasLegalSret  := sret  && !sretIllegal
+  io.out.hasLegalDret  := dret  && !dretIllegal
 
   io.out.hasLegalWriteFcsr := wen && csrIsFp && !fsEffectiveOff
   io.out.hasLegalWriteVcsr := wen && csrIsWritableVec && !vsEffectiveOff
@@ -288,6 +294,7 @@ class CSRPermitIO extends Bundle {
     }
     val privState = new PrivState
     val debugMode = Bool()
+    val mnret = Bool()
     val mret = Bool()
     val sret = Bool()
     val dret = Bool()
@@ -338,10 +345,11 @@ class CSRPermitIO extends Bundle {
   })
 
   val out = Output(new Bundle {
-    val hasLegalWen  = Bool()
-    val hasLegalMret = Bool()
-    val hasLegalSret = Bool()
-    val hasLegalDret = Bool()
+    val hasLegalWen   = Bool()
+    val hasLegalMNret = Bool()
+    val hasLegalMret  = Bool()
+    val hasLegalSret  = Bool()
+    val hasLegalDret  = Bool()
     val hasLegalWriteFcsr = Bool()
     val hasLegalWriteVcsr = Bool()
     val EX_II = Bool()
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
index 2070a1e8447..14704fc9d06 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
@@ -288,6 +288,25 @@ class InterruptEnableBundle extends CSRBundle {
   def getRW = getALL.filter(_.isRW)
 }
 
+class NonMaskableIRPendingBundle extends CSRBundle {
+  val NMI = RW(1).withReset(0.U)
+  // reserve for more NMI type
+}
+object NonMaskableIRNO{
+  final val NMI = 1
+  // reserve for more NMI type
+
+  val interruptDefaultPrio = Seq(
+    NMI
+  )
+  def getIRQHigherThan(irq: Int): Seq[Int] = {
+    val idx = this.interruptDefaultPrio.indexOf(irq, 0)
+    require(idx != -1, s"The irq($irq) does not exists in IntPriority Seq")
+    this.interruptDefaultPrio.slice(0, idx)
+  }
+
+}
+
 object InterruptNO {
   // Software Interrupt
   final val SSI  = 1
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 1dfc2d0ce3a..86476daa368 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -294,17 +294,24 @@ class InterruptFilter extends Module {
   dontTouch(vsMapHostIRVec)
 
   // support debug interrupt
-  val disableInterrupt = io.in.debugMode || (io.in.dcsr.STEP.asBool && !io.in.dcsr.STEPIE.asBool)
+  // support smrnmi when NMIE is 0, all interrupt disable
+  val disableInterrupt = io.in.debugMode || (io.in.dcsr.STEP.asBool && !io.in.dcsr.STEPIE.asBool) || !io.in.mnstatusNMIE
   val debugInterupt = ((io.in.debugIntr && !io.in.debugMode) << CSRConst.IRQ_DEBUG).asUInt
 
-  val intrVec = VecInit((mIRVec | hsIRVec | vsMapHostIRVec | debugInterupt).asBools.map(IR => IR && !disableInterrupt)).asUInt
+  val normalIntrVec = mIRVec | hsIRVec | vsMapHostIRVec | debugInterupt
+  val intrVec = VecInit(Mux(io.in.nmi, io.in.nmiVec, normalIntrVec).asBools.map(IR => IR && !disableInterrupt)).asUInt
   // delay at least 6 cycles to maintain the atomic of sret/mret
+  // 65bit indict current interrupt is NMI
   val intrVecReg = RegInit(0.U(64.W))
+  val nmiReg = RegInit(false.B)
   intrVecReg := intrVec
+  nmiReg := io.in.nmi
   val delayedIntrVec = DelayN(intrVecReg, 5)
+  val delayedNMI = DelayN(nmiReg, 5)
 
   io.out.interruptVec.valid := delayedIntrVec.orR
   io.out.interruptVec.bits := delayedIntrVec
+  io.out.nmi := delayedNMI
 
   dontTouch(hsip)
   dontTouch(hsie)
@@ -342,9 +349,14 @@ class InterruptFilterIO extends Bundle {
 
     val miprios = UInt((64*8).W)
     val hsiprios = UInt((64*8).W)
+    //smrnmi
+    val nmi = Bool()
+    val nmiVec = UInt(64.W)
+    val mnstatusNMIE = Bool()
   })
 
   val out = Output(new Bundle {
+    val nmi = Bool()
     val interruptVec = ValidIO(UInt(64.W))
     val mtopi  = new TopIBundle
     val stopi  = new TopIBundle
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 1ad3801d3bb..8b37d809460 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -1,6 +1,7 @@
 package xiangshan.backend.fu.NewCSR
 
 import chisel3._
+import chisel3.experimental.SourceInfo
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import freechips.rocketchip.rocket.CSRs
@@ -14,6 +15,7 @@ import xiangshan.backend.fu.NewCSR.CSREnumTypeImplicitCast._
 import xiangshan.backend.fu.NewCSR.ChiselRecordForField._
 import xiangshan.backend.fu.PerfCounterIO
 import xiangshan.backend.fu.NewCSR.CSRConfig._
+import xiangshan.backend.fu.NewCSR.CSRFunc.wNoEffectWhen
 
 import scala.collection.immutable.SeqMap
 
@@ -353,6 +355,27 @@ trait MachineLevel { self: NewCSR =>
 
   val mstateen0 = Module(new CSRModule("Mstateen", new MstateenBundle0)).setAddr(CSRs.mstateen0)
 
+  // smrnmi extension
+  val mnepc = Module(new CSRModule("Mnepc", new Epc) with TrapEntryMNEventSinkBundle {
+    rdata := SignExt(Cat(reg.epc.asUInt, 0.U(1.W)), XLEN)
+  })
+    .setAddr(CSRs.mnepc)
+
+  val mncause = Module(new CSRModule("Mncause", new CauseBundle) with TrapEntryMNEventSinkBundle)
+    .setAddr(CSRs.mncause)
+  val mnstatus = Module(new CSRModule("Mnstatus", new MnstatusBundle)
+    with TrapEntryMNEventSinkBundle
+    with MNretEventSinkBundle{
+    // NMIE write 0 with no effect
+    // as opensbi not support smrnmi, we init nmie with 1,and allow software to set nmie close for testing
+    // Attension, when set nmie to zero ,do not cause double trap when nmi interrupt has triggered
+//    when(!wdata.NMIE.asBool) {
+//      reg.NMIE := reg.NMIE
+//    }
+  }).setAddr(CSRs.mnstatus)
+  val mnscratch = Module(new CSRModule("Mnscratch"))
+    .setAddr(CSRs.mnscratch)
+
   val machineLevelCSRMods: Seq[CSRModule[_]] = Seq(
     mstatus,
     misa,
@@ -381,6 +404,10 @@ trait MachineLevel { self: NewCSR =>
     mhartid,
     mconfigptr,
     mstateen0,
+    mnepc,
+    mncause,
+    mnstatus,
+    mnscratch,
   ) ++ mhpmevents ++ mhpmcounters
 
   val machineLevelCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap.from(
@@ -441,6 +468,7 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   with TrapEntryHSEventSinkBundle
   with DretEventSinkBundle
   with MretEventSinkBundle
+  with MNretEventSinkBundle
   with SretEventSinkBundle
   with HasRobCommitBundle
 {
@@ -469,7 +497,13 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   rdata := mstatus.asUInt
   sstatusRdata := sstatus.asUInt
 }
+class MnstatusBundle extends CSRBundle {
 
+  val NMIE   = CSRRWField  (3).withReset(1.U) // as opensbi not support smrnmi, we init nmie open
+  val MNPV   = VirtMode    (7).withReset(0.U)
+  val MNPELP = CSRRWField  (9).withReset(0.U)
+  val MNPP   = PrivMode    (12, 11).withReset(PrivMode.U)
+}
 class MisaBundle extends CSRBundle {
   // Todo: reset with ISA string
   val A = RO( 0).withReset(1.U) // Atomic extension
@@ -642,6 +676,11 @@ trait HasExternalInterruptBundle {
     val debugIP = Input(Bool())
   })
 }
+trait HasNonMaskableIRPBundle {
+  val nonMaskableIRP = IO(new Bundle {
+    val NMI = Input(Bool())
+  })
+}
 
 trait HasMachineCounterControlBundle { self: CSRModule[_] =>
   val mcountinhibit = IO(Input(new McountinhibitBundle))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 9a9ea93321f..8006fabcc27 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -11,7 +11,7 @@ import utils.{HPerfMonitor, OptionWrapper, PerfEvent}
 import xiangshan.backend.fu.NewCSR.CSRBundles.{CSRCustomState, PrivState, RobCommitCSR}
 import xiangshan.backend.fu.NewCSR.CSRDefines.{ContextStatus, PrivMode, SatpMode, VirtMode}
 import xiangshan.backend.fu.NewCSR.CSREnumTypeImplicitCast._
-import xiangshan.backend.fu.NewCSR.CSREvents.{CSREvents, DretEventSinkBundle, EventUpdatePrivStateOutput, MretEventSinkBundle, SretEventSinkBundle, TrapEntryDEventSinkBundle, TrapEntryEventInput, TrapEntryHSEventSinkBundle, TrapEntryMEventSinkBundle, TrapEntryVSEventSinkBundle}
+import xiangshan.backend.fu.NewCSR.CSREvents.{CSREvents, DretEventSinkBundle, EventUpdatePrivStateOutput, MNretEventSinkBundle, MretEventSinkBundle, SretEventSinkBundle, TrapEntryDEventSinkBundle, TrapEntryEventInput, TrapEntryHSEventSinkBundle, TrapEntryMEventSinkBundle, TrapEntryMNEventSinkBundle, TrapEntryVSEventSinkBundle}
 import xiangshan.backend.fu.fpu.Bundles.Frm
 import xiangshan.backend.fu.util.CSRConst
 import xiangshan.backend.fu.vector.Bundles.{Vl, Vstart, Vxrm, Vxsat}
@@ -78,6 +78,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   with Unprivileged
   with CSRAIA
   with HasExternalInterruptBundle
+  with HasNonMaskableIRPBundle
   with CSREvents
   with DebugLevel
   with CSRCustom
@@ -99,6 +100,7 @@ class NewCSR(implicit val p: Parameters) extends Module
       val addr = UInt(12.W)
       val src = UInt(64.W)
       val wdata = UInt(64.W)
+      val mnret = Input(Bool())
       val mret = Input(Bool())
       val sret = Input(Bool())
       val dret = Input(Bool())
@@ -233,9 +235,10 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   private val wenLegal = permitMod.io.out.hasLegalWen
 
-  val legalSret = permitMod.io.out.hasLegalSret
-  val legalMret = permitMod.io.out.hasLegalMret
-  val legalDret = permitMod.io.out.hasLegalDret
+  val legalSret  = permitMod.io.out.hasLegalSret
+  val legalMret  = permitMod.io.out.hasLegalMret
+  val legalMNret = permitMod.io.out.hasLegalMNret
+  val legalDret  = permitMod.io.out.hasLegalDret
 
   var csrRwMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] =
     machineLevelCSRMap ++
@@ -295,13 +298,27 @@ class NewCSR(implicit val p: Parameters) extends Module
   intrMod.io.in.hviprio2 := hviprio2.rdata.asUInt
   intrMod.io.in.miprios := Cat(miregiprios.map(_.rdata).reverse)
   intrMod.io.in.hsiprios := Cat(siregiprios.map(_.rdata).reverse)
+  intrMod.io.in.mnstatusNMIE := mnstatus.regOut.NMIE.asBool
 
+  val nmip = RegInit(new NonMaskableIRPendingBundle, (new NonMaskableIRPendingBundle).init)
+  when(nonMaskableIRP.NMI) {
+    nmip.NMI := true.B
+  }
+  
+  intrMod.io.in.nmi := nmip.asUInt.orR
+  intrMod.io.in.nmiVec := nmip.asUInt
+
+  when(intrMod.io.out.nmi && intrMod.io.out.interruptVec.valid) {
+    nmip.NMI := false.B
+  }
   val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
+  val nmi = RegEnable(intrMod.io.out.nmi, false.B, intrMod.io.out.interruptVec.valid)
 
   val trapHandleMod = Module(new TrapHandleModule)
 
   trapHandleMod.io.in.trapInfo.valid := hasTrap
   trapHandleMod.io.in.trapInfo.bits.trapVec := trapVec.asUInt
+  trapHandleMod.io.in.trapInfo.bits.nmi := nmi
   trapHandleMod.io.in.trapInfo.bits.intrVec := intrVec
   trapHandleMod.io.in.trapInfo.bits.isInterrupt := trapIsInterrupt
   trapHandleMod.io.in.privState := privState
@@ -351,9 +368,10 @@ class NewCSR(implicit val p: Parameters) extends Module
   permitMod.io.in.privState := privState
   permitMod.io.in.debugMode := debugMode
 
-  permitMod.io.in.mret := io.in.bits.mret && valid
-  permitMod.io.in.sret := io.in.bits.sret && valid
-  permitMod.io.in.dret := io.in.bits.dret && valid
+  permitMod.io.in.mnret := io.in.bits.mnret && valid
+  permitMod.io.in.mret  := io.in.bits.mret  && valid
+  permitMod.io.in.sret  := io.in.bits.sret  && valid
+  permitMod.io.in.dret  := io.in.bits.dret  && valid
   permitMod.io.in.csrIsCustom := customCSRMods.map(_.addr.U === addr).reduce(_ || _).orR
 
   permitMod.io.in.status.tsr := mstatus.regOut.TSR.asBool
@@ -473,6 +491,11 @@ class NewCSR(implicit val p: Parameters) extends Module
         m.trapToM := trapEntryMEvent.out
       case _ =>
     }
+    mod match {
+      case m: TrapEntryMNEventSinkBundle =>
+        m.trapToMN := trapEntryMNEvent.out
+      case _ =>
+    }
     mod match {
       case m: TrapEntryHSEventSinkBundle =>
         m.trapToHS := trapEntryHSEvent.out
@@ -488,6 +511,11 @@ class NewCSR(implicit val p: Parameters) extends Module
         m.retFromM := mretEvent.out
       case _ =>
     }
+    mod match {
+      case m: MNretEventSinkBundle =>
+        m.retFromMN := mnretEvent.out
+      case _ =>
+    }
     mod match {
       case m: SretEventSinkBundle =>
         m.retFromS := sretEvent.out
@@ -593,12 +621,13 @@ class NewCSR(implicit val p: Parameters) extends Module
     println(s"${mod.modName}: ")
     println(mod.dumpFields)
   }
+  
+  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !entryDebugMode  && !debugMode && !nmi
+  trapEntryMNEvent .valid := hasTrap && nmi && !debugMode
+  trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode
+  trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode
 
-  trapEntryMEvent .valid := hasTrap && entryPrivState.isModeM && !entryDebugMode && !debugMode
-  trapEntryHSEvent.valid := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode
-  trapEntryVSEvent.valid := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode
-
-  Seq(trapEntryMEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
+  Seq(trapEntryMEvent, trapEntryMNEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
     eMod.in match {
       case in: TrapEntryEventInput =>
         in.causeNO := trapHandleMod.io.out.causeNO
@@ -610,8 +639,9 @@ class NewCSR(implicit val p: Parameters) extends Module
 
         in.iMode.PRVM := PRVM
         in.iMode.V := V
-        in.dMode.PRVM := Mux(mstatus.regOut.MPRV.asBool, mstatus.regOut.MPP, PRVM)
-        in.dMode.V := V.asUInt.asBool || mstatus.regOut.MPRV && (mstatus.regOut.MPP =/= PrivMode.M) && mstatus.regOut.MPV
+        // when NMIE is zero, force to behave as MPRV is zero
+        in.dMode.PRVM := Mux(mstatus.regOut.MPRV.asBool && mnstatus.regOut.NMIE.asBool, mstatus.regOut.MPP, PRVM)
+        in.dMode.V := V.asUInt.asBool || mstatus.regOut.MPRV && mnstatus.regOut.NMIE.asBool && (mstatus.regOut.MPP =/= PrivMode.M) && mstatus.regOut.MPV
 
         in.privState := privState
         in.mstatus := mstatus.regOut
@@ -630,6 +660,14 @@ class NewCSR(implicit val p: Parameters) extends Module
     }
   }
 
+  mnretEvent.valid := legalMNret
+  mnretEvent.in match {
+    case in =>
+      in.mstatus := mstatus.regOut
+      in.mnepc   := mnepc.regOut
+      in.mnstatus:= mnstatus.regOut
+  }
+
   mretEvent.valid := legalMret
   mretEvent.in match {
     case in =>
@@ -767,8 +805,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     }
   })
 
-  private val needTargetUpdate = mretEvent.out.targetPc.valid || sretEvent.out.targetPc.valid || dretEvent.out.targetPc.valid ||
-    trapEntryMEvent.out.targetPc.valid || trapEntryHSEvent.out.targetPc.valid || trapEntryVSEvent.out.targetPc.valid || trapEntryDEvent.out.targetPc.valid
+  private val needTargetUpdate = mnretEvent.out.targetPc.valid || mretEvent.out.targetPc.valid || sretEvent.out.targetPc.valid || dretEvent.out.targetPc.valid ||
+    trapEntryMEvent.out.targetPc.valid || trapEntryMNEvent.out.targetPc.valid || trapEntryHSEvent.out.targetPc.valid || trapEntryVSEvent.out.targetPc.valid || trapEntryDEvent.out.targetPc.valid
 
   private val noCSRIllegal = (ren || wen) && Cat(csrRwMap.keys.toSeq.sorted.map(csrAddr => !(addr === csrAddr.U))).andR
 
@@ -819,10 +857,12 @@ class NewCSR(implicit val p: Parameters) extends Module
     Mux(trapEntryDEvent.out.targetPc.valid,
       trapEntryDEvent.out.targetPc.bits,
       Mux1H(Seq(
-        mretEvent.out.targetPc.valid -> mretEvent.out.targetPc.bits,
-        sretEvent.out.targetPc.valid -> sretEvent.out.targetPc.bits,
-        dretEvent.out.targetPc.valid -> dretEvent.out.targetPc.bits,
+        mnretEvent.out.targetPc.valid -> mnretEvent.out.targetPc.bits,
+        mretEvent.out.targetPc.valid  -> mretEvent.out.targetPc.bits,
+        sretEvent.out.targetPc.valid  -> sretEvent.out.targetPc.bits,
+        dretEvent.out.targetPc.valid  -> dretEvent.out.targetPc.bits,
         trapEntryMEvent.out.targetPc.valid -> trapEntryMEvent.out.targetPc.bits,
+        trapEntryMNEvent.out.targetPc.valid -> trapEntryMNEvent.out.targetPc.bits,
         trapEntryHSEvent.out.targetPc.valid -> trapEntryHSEvent.out.targetPc.bits,
         trapEntryVSEvent.out.targetPc.valid -> trapEntryVSEvent.out.targetPc.bits)
       )
@@ -1067,13 +1107,14 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.tlb.spvp :=  hstatus.regOut.SPVP.asBool
 
   io.tlb.imode := PRVM.asUInt
+  // when NMIE is zero, force to behave as MPRV is zero
   io.tlb.dmode := Mux(
-    (debugMode && dcsr.regOut.MPRVEN || !debugMode) && mstatus.regOut.MPRV,
+    (debugMode && dcsr.regOut.MPRVEN || !debugMode) && mstatus.regOut.MPRV && mnstatus.regOut.NMIE,
     mstatus.regOut.MPP.asUInt,
     PRVM.asUInt
   )
   io.tlb.dvirt := Mux(
-    (debugMode && dcsr.regOut.MPRVEN || !debugMode) && mstatus.regOut.MPRV && mstatus.regOut.MPP =/= PrivMode.M,
+    (debugMode && dcsr.regOut.MPRVEN || !debugMode) && mstatus.regOut.MPRV && mnstatus.regOut.NMIE && mstatus.regOut.MPP =/= PrivMode.M,
     mstatus.regOut.MPV.asUInt,
     V.asUInt
   )
@@ -1099,6 +1140,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     val trapValid = io.fromRob.trap.valid
     val trapNO = trapHandleMod.io.out.causeNO.ExceptionCode.asUInt
     val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool
+    val hasNMI = nmi && hasTrap
     val interruptNO = Mux(interrupt, trapNO, 0.U)
     val exceptionNO = Mux(!interrupt, trapNO, 0.U)
     val isSv39: Bool =
@@ -1124,6 +1166,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     diffArchEvent.interrupt := interruptNO
     diffArchEvent.exception := exceptionNO
     diffArchEvent.exceptionPC := exceptionPC
+    diffArchEvent.hasNMI := hasNMI
     if (env.EnableDifftest) {
       diffArchEvent.exceptionInst := io.fromRob.trap.bits.instr
     }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index 5d2be30ddd0..c8ef673e32b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -21,6 +21,7 @@ class TrapHandleModule extends Module {
   private val hvien = io.in.hvien.asUInt
 
   private val hasTrap = trapInfo.valid
+  private val hasNMI = hasTrap && trapInfo.bits.nmi
   private val hasIR = hasTrap && trapInfo.bits.isInterrupt
   private val hasEX = hasTrap && !trapInfo.bits.isInterrupt
 
@@ -42,7 +43,6 @@ class TrapHandleModule extends Module {
   private val filteredIRQs: Seq[UInt] = interruptGroups.map {
     case (irqGroup, name) => (getMaskFromIRQGroup(irqGroup) & hasIRVec).suggestName(s"filteredIRQs_$name")
   }
-
   private val hasIRQinGroup: Seq[Bool] = interruptGroups.map {
     case (irqGroup, name) => dontTouch(Cat(filterIRQs(irqGroup, hasIRVec)).orR.suggestName(s"hasIRQinGroup_$name"))
   }
@@ -56,6 +56,18 @@ class TrapHandleModule extends Module {
     0.U.asTypeOf(Vec(64, Bool())),
     hasIRQinGroup zip highestIRQinGroup map{ case (hasIRQ: Bool, highestIRQ: Vec[Bool]) => hasIRQ -> highestIRQ }
   )
+  private val highestPrioNMIVec = Wire(Vec(64, Bool()))
+  highestPrioNMIVec.zipWithIndex.foreach { case (irq, i) =>
+    if (NonMaskableIRNO.interruptDefaultPrio.contains(i)) {
+      val higherIRSeq = NonMaskableIRNO.getIRQHigherThan(i)
+      irq := (
+        higherIRSeq.nonEmpty.B && Cat(higherIRSeq.map(num => !hasIRVec(num))).andR ||
+          higherIRSeq.isEmpty.B
+        ) && hasIRVec(i)
+      dontTouch(irq)
+    } else
+      irq := false.B
+  }
 
   private val highestPrioEXVec = Wire(Vec(64, Bool()))
   highestPrioEXVec.zipWithIndex.foreach { case (excp, i) =>
@@ -69,8 +81,10 @@ class TrapHandleModule extends Module {
       excp := false.B
   }
 
-  private val highestPrioIR = highestPrioIRVec.asUInt
-  private val highestPrioEX = highestPrioEXVec.asUInt
+  private val highestPrioIR  = highestPrioIRVec.asUInt
+  private val highestPrioNMI = highestPrioNMIVec.asUInt
+  private val highestPrioEX  = highestPrioEXVec.asUInt
+
 
   private val mIRVec  = dontTouch(WireInit(highestPrioIR))
   private val hsIRVec = (mIRVec  & mideleg) | (mIRVec  & mvien & ~mideleg)
@@ -80,9 +94,10 @@ class TrapHandleModule extends Module {
   private val hsEXVec = highestPrioEX & medeleg
   private val vsEXVec = highestPrioEX & medeleg & hedeleg
 
+  // nmi handle in MMode only and default handler is mtvec
   private val  mHasIR =  mIRVec.orR
-  private val hsHasIR = hsIRVec.orR
-  private val vsHasIR = vsIRVec.orR
+  private val hsHasIR = hsIRVec.orR & !hasNMI
+  private val vsHasIR = vsIRVec.orR & !hasNMI
 
   private val  mHasEX =  mEXVec.orR
   private val hsHasEX = hsEXVec.orR
@@ -97,7 +112,7 @@ class TrapHandleModule extends Module {
 
   // Todo: support more interrupt and exception
   private val exceptionRegular = OHToUInt(highestPrioEX)
-  private val interruptNO = OHToUInt(highestPrioIR)
+  private val interruptNO = OHToUInt(Mux(hasNMI, highestPrioNMI, highestPrioIR))
   private val exceptionNO = Mux(trapInfo.bits.singleStep, ExceptionNO.breakPoint.U, exceptionRegular)
 
   private val causeNO = Mux(hasIR, interruptNO, exceptionNO)
@@ -151,6 +166,7 @@ class TrapHandleIO extends Bundle {
   val in = Input(new Bundle {
     val trapInfo = ValidIO(new Bundle {
       val trapVec = UInt(64.W)
+      val nmi = Bool()
       val intrVec = UInt(64.W)
       val isInterrupt = Bool()
       val singleStep = Bool()
diff --git a/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala b/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
index bef44c62ec4..a6ce0e49135 100644
--- a/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
+++ b/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
@@ -269,6 +269,7 @@ trait HasCSRConst {
 
   def privEcall  = 0x000.U
   def privEbreak = 0x001.U
+  def privMNret  = 0x702.U
   def privMret   = 0x302.U
   def privSret   = 0x102.U
   def privUret   = 0x002.U
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 9013a433fb9..12a637030fb 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -54,6 +54,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   private val isEcall  = CSROpType.isSystemOp(func) && addr === privEcall
   private val isEbreak = CSROpType.isSystemOp(func) && addr === privEbreak
+  private val isMNret  = CSROpType.isSystemOp(func) && addr === privMNret
   private val isMret   = CSROpType.isSystemOp(func) && addr === privMret
   private val isSret   = CSROpType.isSystemOp(func) && addr === privSret
   private val isDret   = CSROpType.isSystemOp(func) && addr === privDret
@@ -96,6 +97,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       in.bits.src := src
       in.bits.wdata := wdata
       in.bits.mret := isMret
+      in.bits.mnret := isMNret
       in.bits.sret := isSret
       in.bits.dret := isDret
   }
@@ -144,6 +146,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   csrMod.platformIRP.VSEIP := false.B // Todo
   csrMod.platformIRP.VSTIP := false.B // Todo
   csrMod.platformIRP.debugIP := csrIn.externalInterrupt.debug
+  csrMod.nonMaskableIRP.NMI := csrIn.externalInterrupt.nmi.nmi
 
   csrMod.io.fromTop.hartId := io.csrin.get.hartId
   csrMod.io.fromTop.clintTime := io.csrin.get.clintTime
```
