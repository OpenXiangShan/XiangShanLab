# Commit Log
- Issue: #3786
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3786
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3786
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3786
- Changed files: 11
- Additions: 136
- Deletions: 61

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/fu/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index b0cad552de4..8f777af9e06 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -232,6 +232,8 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   private val vlFromVfIsZero = vfExuBlock.io.vlIsZero.get
   private val vlFromVfIsVlmax = vfExuBlock.io.vlIsVlmax.get
 
+  private val backendCriticalError = Wire(Bool())
+
   ctrlBlock.io.intIQValidNumVec := intScheduler.io.intIQValidNumVec
   ctrlBlock.io.fpIQValidNumVec := fpScheduler.io.fpIQValidNumVec
   ctrlBlock.io.fromTop.hartId := io.fromTop.hartId
@@ -247,6 +249,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   ctrlBlock.io.robio.csr.trapTarget := intExuBlock.io.csrio.get.trapTarget
   ctrlBlock.io.robio.csr.isXRet := intExuBlock.io.csrio.get.isXRet
   ctrlBlock.io.robio.csr.wfiEvent := intExuBlock.io.csrio.get.wfi_event
+  ctrlBlock.io.robio.csr.criticalErrorState := intExuBlock.io.csrio.get.criticalErrorState
   ctrlBlock.io.robio.lsq <> io.mem.robLsqIO
   ctrlBlock.io.robio.lsTopdownInfo <> io.mem.lsTopdownInfo
   ctrlBlock.io.robio.debug_ls <> io.mem.debugLS
@@ -454,6 +457,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   csrin.clintTime.bits := RegEnable(io.fromTop.clintTime.bits, io.fromTop.clintTime.valid)
   csrin.trapInstInfo := ctrlBlock.io.toCSR.trapInstInfo
   csrin.fromVecExcpMod.busy := vecExcpMod.o.status.busy
+  csrin.criticalErrorState := backendCriticalError
 
   private val csrio = intExuBlock.io.csrio.get
   csrio.hartId := io.fromTop.hartId
@@ -820,11 +824,9 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   }
 
   // expand to collect frontend/memblock/L2 critical errors
-  val backendCriticalError = criticalErrors.map(_._2).reduce(_ || _)
-
-  ctrlBlock.io.robio.criticalError := backendCriticalError
-  io.toTop.cpuCriticalError := backendCriticalError
+  backendCriticalError := criticalErrors.map(_._2).reduce(_ || _)
 
+  io.toTop.cpuCriticalError := csrio.criticalErrorState
 }
 
 class BackendMemIO(implicit p: Parameters, params: BackendParams) extends XSBundle {
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index d75b8712c60..1c8202315e3 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -617,7 +617,7 @@ class CtrlBlockImp(
   rob.io.debug_ls := io.robio.debug_ls
   rob.io.debugHeadLsIssue := io.robio.robHeadLsIssue
   rob.io.lsTopdownInfo := io.robio.lsTopdownInfo
-  rob.io.criticalError := io.robio.criticalError
+  rob.io.csr.criticalErrorState := io.robio.csr.criticalErrorState
   rob.io.debugEnqLsq := io.debugEnqLsq
 
   io.robio.robDeqPtr := rob.io.robDeqPtr
@@ -731,7 +731,6 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
       val vtype = Output(ValidIO(VType()))
       val hasVsetvl = Output(Bool())
     }
-    val criticalError = Input(Bool())
 
     // store event difftest information
     val storeDebugInfo = Vec(EnsbufferWidth, new Bundle {
diff --git a/src/main/scala/xiangshan/backend/fu/CSR.scala b/src/main/scala/xiangshan/backend/fu/CSR.scala
index c828166f97d..4554cbddee9 100644
--- a/src/main/scala/xiangshan/backend/fu/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/CSR.scala
@@ -85,6 +85,7 @@ class CSRFileIO(implicit p: Parameters) extends XSBundle {
   val hartId = Input(UInt(hartIdLen.W))
   // output (for func === CSROpType.jmp)
   val perf = Input(new PerfCounterIO)
+  val criticalErrorState = Output(Bool())
   val isPerfCnt = Output(Bool())
   // to FPU
   val fpu = Flipped(new FpuCsrIO)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
index 9f0c5288452..e8dee70914a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
@@ -22,13 +22,14 @@ class TrapEntryDEventOutput extends Bundle with EventUpdatePrivStateOutput with
 }
 
 class TrapEntryDEventInput(implicit override val p: Parameters) extends TrapEntryEventInput{
-  val hasTrap                 = Input(Bool())
-  val debugMode               = Input(Bool())
-  val hasDebugIntr            = Input(Bool())
-  val triggerEnterDebugMode   = Input(Bool())
-  val hasDebugEbreakException = Input(Bool())
-  val hasSingleStep           = Input(Bool())
-  val breakPoint              = Input(Bool())
+  val hasTrap                      = Input(Bool())
+  val debugMode                    = Input(Bool())
+  val hasDebugIntr                 = Input(Bool())
+  val triggerEnterDebugMode        = Input(Bool())
+  val hasDebugEbreakException      = Input(Bool())
+  val hasSingleStep                = Input(Bool())
+  val breakPoint                   = Input(Bool())
+  val criticalErrorStateEnterDebug = Input(Bool())
 }
 
 class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSREventBase with DebugMMIO {
@@ -41,20 +42,22 @@ class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSRE
   private val vsatp   = current.vsatp
   private val hgatp   = current.hgatp
 
-  private val hasTrap                 = in.hasTrap
-  private val debugMode               = in.debugMode
-  private val hasDebugIntr            = in.hasDebugIntr
-  private val breakPoint              = in.breakPoint
-  private val triggerEnterDebugMode   = in.triggerEnterDebugMode
-  private val hasDebugEbreakException = in.hasDebugEbreakException
-  private val hasSingleStep           = in.hasSingleStep
+  private val hasTrap                      = in.hasTrap
+  private val debugMode                    = in.debugMode
+  private val hasDebugIntr                 = in.hasDebugIntr
+  private val breakPoint                   = in.breakPoint
+  private val triggerEnterDebugMode        = in.triggerEnterDebugMode
+  private val hasDebugEbreakException      = in.hasDebugEbreakException
+  private val hasSingleStep                = in.hasSingleStep
+  private val criticalErrorStateEnterDebug = in.criticalErrorStateEnterDebug
 
   private val hasExceptionInDmode = debugMode && hasTrap
   val causeIntr = DcsrCause.Haltreq.asUInt
-  val causeExp = MuxCase(0.U, Seq(
-    triggerEnterDebugMode   -> DcsrCause.Trigger.asUInt,
-    hasDebugEbreakException -> DcsrCause.Ebreak.asUInt,
-    hasSingleStep           -> DcsrCause.Step.asUInt
+  val causeExp = MuxCase(DcsrCause.None.asUInt, Seq(
+    criticalErrorStateEnterDebug -> DcsrCause.Other.asUInt,
+    triggerEnterDebugMode        -> DcsrCause.Trigger.asUInt,
+    hasDebugEbreakException      -> DcsrCause.Ebreak.asUInt,
+    hasSingleStep                -> DcsrCause.Step.asUInt
   ))
 
   private val trapPC = genTrapVA(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
index 5dfa0233ecb..8a2e833579b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
@@ -38,6 +38,7 @@ class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
    *    2. ebreak inst in nonDmode
    *    3. trigger fire in nonDmode
    *    4. single step(debug module set dcsr.step before hart resume)
+   *    5. critical error state(when dcsr.cetrig assert)
    */
   // debug_intr
   val hasIntr = hasTrap && trapIsInterrupt
@@ -68,7 +69,11 @@ class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
   // debug_exception_single
   val hasSingleStep = hasExp && singleStep
 
-  val hasDebugException = hasDebugEbreakException || triggerEnterDebugMode || hasSingleStep
+
+  // critical error state
+  val criticalErrorStateEnterDebug = trapInfo.bits.criticalErrorState && dcsr.CETRIG.asBool
+
+  val hasDebugException = hasDebugEbreakException || triggerEnterDebugMode || hasSingleStep || criticalErrorStateEnterDebug
   val hasDebugTrap = hasDebugException || hasDebugIntr
 
   val tselect1H = UIntToOH(tselect.asUInt, TriggerNum).asBools
@@ -126,12 +131,13 @@ class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
   io.out.triggerFrontendChange  := frontendTriggerUpdate
   io.out.newTriggerChainIsLegal := newTriggerChainIsLegal
 
-  io.out.hasDebugTrap            := hasDebugTrap
-  io.out.hasDebugIntr            := hasDebugIntr
-  io.out.hasSingleStep           := hasSingleStep
-  io.out.triggerEnterDebugMode   := triggerEnterDebugMode
-  io.out.hasDebugEbreakException := hasDebugEbreakException
-  io.out.breakPoint              := breakPoint
+  io.out.hasDebugTrap                 := hasDebugTrap
+  io.out.hasDebugIntr                 := hasDebugIntr
+  io.out.hasSingleStep                := hasSingleStep
+  io.out.triggerEnterDebugMode        := triggerEnterDebugMode
+  io.out.hasDebugEbreakException      := hasDebugEbreakException
+  io.out.breakPoint                   := breakPoint
+  io.out.criticalErrorStateEnterDebug := criticalErrorStateEnterDebug
 }
 
 class DebugIO(implicit val p: Parameters) extends Bundle with HasXSParameter {
@@ -142,6 +148,7 @@ class DebugIO(implicit val p: Parameters) extends Bundle with HasXSParameter {
       val isInterrupt = Bool()
       val singleStep = Bool()
       val trigger = TriggerAction()
+      val criticalErrorState = Bool()
     })
 
     val privState = new PrivState
@@ -172,6 +179,7 @@ class DebugIO(implicit val p: Parameters) extends Bundle with HasXSParameter {
     val triggerEnterDebugMode = Bool()
     val hasDebugEbreakException = Bool()
     val breakPoint = Bool()
+    val criticalErrorStateEnterDebug = Bool()
   })
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index 21e462a84e1..2c7cb6c0565 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -56,7 +56,11 @@ trait DebugLevel { self: NewCSR =>
   val tinfo = Module(new CSRModule("Tinfo", new TinfoBundle))
     .setAddr(CSRs.tinfo)
 
-  val dcsr = Module(new CSRModule("Dcsr", new DcsrBundle) with TrapEntryDEventSinkBundle with DretEventSinkBundle)
+  val dcsr = Module(new CSRModule("Dcsr", new DcsrBundle) with TrapEntryDEventSinkBundle with DretEventSinkBundle with HasNmipBundle {
+    when(nmip){
+      reg.NMIP := nmip
+    }
+  })
     .setAddr(CSRs.dcsr)
 
   val dpc = Module(new CSRModule("Dpc", new Epc) with TrapEntryDEventSinkBundle)
@@ -269,6 +273,8 @@ class DscratchBundle extends OneFieldBundle
 class DcsrBundle extends CSRBundle {
   override val len: Int = 32
   val DEBUGVER  = DcsrDebugVer(31, 28).withReset(DcsrDebugVer.Spec) // Debug implementation as it described in 0.13 draft // todo
+  val EXTCAUSE  =           RO(26, 24).withReset(0.U)
+  val CETRIG    =           RW(    19).withReset(0.U)
   // All ebreak Privileges are RW, instead of WARL, since XiangShan support U/S/VU/VS.
   val EBREAKVS  =           RW(    17).withReset(0.U)
   val EBREAKVU  =           RW(    16).withReset(0.U)
@@ -277,8 +283,8 @@ class DcsrBundle extends CSRBundle {
   val EBREAKU   =           RW(    12).withReset(0.U)
   // STEPIE is RW, instead of WARL, since XiangShan support interrupts being enabled single stepping.
   val STEPIE    =           RW(    11).withReset(0.U)
-  val STOPCOUNT =           RO(    10).withReset(0.U) // Stop count updating has not been supported
-  val STOPTIME  =           RO(     9).withReset(0.U) // Stop time updating has not been supported
+  val STOPCOUNT =           RW(    10).withReset(0.U)
+  val STOPTIME  =           RW(     9).withReset(0.U)
   val CAUSE     =    DcsrCause( 8,  6).withReset(DcsrCause.None)
   val V         =     VirtMode(     5).withReset(VirtMode.Off)
   // MPRVEN is RW, instead of WARL, since XiangShan support use mstatus.mprv in debug mode
@@ -305,6 +311,7 @@ object DcsrCause extends CSREnum with ROApply {
   val Step         = Value(4.U)
   val Resethaltreq = Value(5.U)
   val Group        = Value(6.U)
+  val Other        = Value(7.U)
 }
 
 trait HasTdataSink { self: CSRModule[_] =>
@@ -318,6 +325,10 @@ trait HasdebugModeBundle { self: CSRModule[_] =>
   val chainable = IO(Input(Bool()))
 }
 
+trait HasNmipBundle { self: CSRModule[_] =>
+  val nmip = IO(Input(Bool()))
+}
+
 /**
  * debug Module MMIO Addr
  */
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index a48191c43b1..b8f23efc9cc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -124,6 +124,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     val fromTop = Input(new Bundle {
       val hartId = UInt(hartIdLen.W)
       val clintTime = Input(ValidIO(UInt(64.W)))
+      val criticalErrorState = Input(Bool())
     })
     val in = Flipped(DecoupledIO(new NewCSRInput))
     val trapInst = Input(ValidIO(UInt(InstWidth.W)))
@@ -189,6 +190,7 @@ class NewCSR(implicit val p: Parameters) extends Module
       val instrAddrTransType = new AddrTransType
       // custom
       val custom = new CSRCustomState
+      val criticalErrorState = Bool()
     })
     // tlb
     val tlb = Output(new Bundle {
@@ -257,6 +259,15 @@ class NewCSR(implicit val p: Parameters) extends Module
   val V = RegInit(VirtMode(0), VirtMode.Off)
   val debugMode = RegInit(false.B)
 
+  // dcsr stopcount 
+  val debugModeStopCountNext = debugMode && dcsr.regOut.STOPCOUNT
+  val debugModeStopTimeNext  = debugMode && dcsr.regOut.STOPTIME
+  val debugModeStopCount = RegNext(debugModeStopCountNext)
+  val unprivCountUpdate  = !debugModeStopCount && debugModeStopCountNext
+
+  val criticalErrorStateInCSR = Wire(Bool())
+  val criticalErrorState = RegEnable(true.B, false.B, io.fromTop.criticalErrorState || criticalErrorStateInCSR)
+
   private val privState = Wire(new PrivState)
   privState.PRVM := PRVM
   privState.V := V
@@ -350,6 +361,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   intrMod.io.in.mnstatusNMIE := mnstatus.regOut.NMIE.asBool
   intrMod.io.in.nmi := nmip.asUInt.orR
   intrMod.io.in.nmiVec := nmip.asUInt
+  intrMod.io.in.debugMode := debugMode
+  intrMod.io.in.debugIntr := debugIntr
+  intrMod.io.in.dcsr      := dcsr.regOut
 
   when(intrMod.io.out.nmi && intrMod.io.out.interruptVec.valid) {
     nmip.NMI_31 := nmip.NMI_31 & !intrMod.io.out.interruptVec.bits(NonMaskableIRNO.NMI_31).asBool
@@ -380,6 +394,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapHandleMod.io.in.stvec := stvec.regOut
   trapHandleMod.io.in.vstvec := vstvec.regOut
   trapHandleMod.io.in.virtualInterruptIsHvictlInject := virtualInterruptIsHvictlInject
+  trapHandleMod.io.in.trapInfo.bits.singleStep  := hasTrap && !trapIsInterrupt && singleStep
 
   val entryPrivState = trapHandleMod.io.out.entryPrivState
   val entryDebugMode = WireInit(false.B)
@@ -675,6 +690,18 @@ class NewCSR(implicit val p: Parameters) extends Module
         m.privState     := privState
       case _ =>
     }
+    mod match {
+      case m: HasDebugStopBundle =>
+        m.debugModeStopCount := debugModeStopCount
+        m.debugModeStopTime  := debugModeStopTimeNext
+        m.unprivCountUpdate  := unprivCountUpdate
+      case _ =>
+    }
+    mod match {
+      case m: HasNmipBundle =>
+        m.nmip := nmip.asUInt.orR
+      case _ =>
+    }
   }
 
   csrMods.foreach { mod =>
@@ -1030,6 +1057,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   debugMod.io.in.trapInfo.bits.isInterrupt := trapIsInterrupt
   debugMod.io.in.trapInfo.bits.trigger     := trigger
   debugMod.io.in.trapInfo.bits.singleStep  := singleStep
+  debugMod.io.in.trapInfo.bits.criticalErrorState := criticalErrorState
   debugMod.io.in.privState                 := privState
   debugMod.io.in.debugMode                 := debugMode
   debugMod.io.in.dcsr                      := dcsr.regOut
@@ -1044,20 +1072,15 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   entryDebugMode := debugMod.io.out.hasDebugTrap && !debugMode
 
-  trapEntryDEvent.valid                       := entryDebugMode
-  trapEntryDEvent.in.hasDebugIntr             := debugMod.io.out.hasDebugIntr
-  trapEntryDEvent.in.debugMode                := debugMode
-  trapEntryDEvent.in.hasTrap                  := hasTrap
-  trapEntryDEvent.in.hasSingleStep            := debugMod.io.out.hasSingleStep
-  trapEntryDEvent.in.triggerEnterDebugMode    := debugMod.io.out.triggerEnterDebugMode
-  trapEntryDEvent.in.hasDebugEbreakException  := debugMod.io.out.hasDebugEbreakException
-  trapEntryDEvent.in.breakPoint               := debugMod.io.out.breakPoint
-
-  trapHandleMod.io.in.trapInfo.bits.singleStep  := debugMod.io.out.hasSingleStep
-
-  intrMod.io.in.debugMode := debugMode
-  intrMod.io.in.debugIntr := debugIntr
-  intrMod.io.in.dcsr      := dcsr.regOut
+  trapEntryDEvent.valid                           := entryDebugMode
+  trapEntryDEvent.in.hasDebugIntr                 := debugMod.io.out.hasDebugIntr
+  trapEntryDEvent.in.debugMode                    := debugMode
+  trapEntryDEvent.in.hasTrap                      := hasTrap
+  trapEntryDEvent.in.hasSingleStep                := debugMod.io.out.hasSingleStep
+  trapEntryDEvent.in.triggerEnterDebugMode        := debugMod.io.out.triggerEnterDebugMode
+  trapEntryDEvent.in.hasDebugEbreakException      := debugMod.io.out.hasDebugEbreakException
+  trapEntryDEvent.in.breakPoint                   := debugMod.io.out.breakPoint
+  trapEntryDEvent.in.criticalErrorStateEnterDebug := debugMod.io.out.criticalErrorStateEnterDebug
 
   tdata1RegVec.foreach { mod =>
     mod match {
@@ -1295,10 +1318,12 @@ class NewCSR(implicit val p: Parameters) extends Module
   )
 
   io.distributedWenLegal := wenLegal
+  io.status.criticalErrorState := criticalErrorState && !dcsr.regOut.CETRIG.asBool
 
   val criticalErrors = Seq(
     ("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !entryDebugMode),
   )
+  criticalErrorStateInCSR := criticalErrors.map(criticalError => criticalError._2).reduce(_ || _).asBool
   generateCriticalErrors()
 
   // Always instantiate basic difftest modules.
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala
index d642b8a8391..89d527a2c63 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Unprivileged.scala
@@ -131,14 +131,19 @@ trait Unprivileged { self: NewCSR with MachineLevel with SupervisorLevel =>
 
   val cycle = Module(new CSRModule("cycle", new CSRBundle {
     val cycle = RO(63, 0)
-  }) with HasMHPMSink {
-    regOut.cycle := mHPM.cycle
+  }) with HasMHPMSink with HasDebugStopBundle {
+    when(unprivCountUpdate) {
+      reg := mHPM.cycle
+    }.otherwise{
+      reg := reg
+    }
+    regOut := Mux(debugModeStopCount, reg.asUInt, mHPM.cycle)
   })
     .setAddr(CSRs.cycle)
 
   val time = Module(new CSRModule("time", new CSRBundle {
     val time = RO(63, 0)
-  }) with HasMHPMSink {
+  }) with HasMHPMSink with HasDebugStopBundle {
     val updated = IO(Output(Bool()))
     val stime  = IO(Output(UInt(64.W)))
     val vstime = IO(Output(UInt(64.W)))
@@ -146,11 +151,13 @@ trait Unprivileged { self: NewCSR with MachineLevel with SupervisorLevel =>
     val stimeTmp  = mHPM.time.bits
     val vstimeTmp = mHPM.time.bits + htimedelta
 
-    when (mHPM.time.valid) {
+    when(mHPM.time.valid && !debugModeStopTime) {
       reg.time := Mux(v, vstimeTmp, stimeTmp)
+    }.otherwise {
+      reg := reg
     }
 
-    updated := GatedValidRegNext(mHPM.time.valid)
+    updated := GatedValidRegNext(mHPM.time.valid && !debugModeStopTime)
     stime  := stimeTmp
     vstime := vstimeTmp
   })
@@ -158,16 +165,26 @@ trait Unprivileged { self: NewCSR with MachineLevel with SupervisorLevel =>
 
   val instret = Module(new CSRModule("instret", new CSRBundle {
     val instret = RO(63, 0)
-  }) with HasMHPMSink {
-    regOut.instret := mHPM.instret
+  }) with HasMHPMSink with HasDebugStopBundle {
+    when(unprivCountUpdate) {
+      reg := mHPM.instret
+    }.otherwise{
+      reg := reg
+    }
+    regOut := Mux(debugModeStopCount, reg.asUInt, mHPM.instret)
   })
     .setAddr(CSRs.instret)
 
   val hpmcounters: Seq[CSRModule[_]] = (3 to 0x1F).map(num =>
     Module(new CSRModule(s"Hpmcounter$num", new CSRBundle {
       val hpmcounter = RO(63, 0).withReset(0.U)
-    }) with HasMHPMSink {
-      regOut.hpmcounter := mHPM.hpmcounters(num - 3)
+    }) with HasMHPMSink with HasDebugStopBundle {
+      when(unprivCountUpdate) {
+        reg := mHPM.hpmcounters(num - 3)
+      }.otherwise{
+        reg := reg
+      }
+      regOut := Mux(debugModeStopCount, reg.asUInt, mHPM.hpmcounters(num - 3))
     }).setAddr(CSRs.cycle + num)
   )
 
@@ -253,3 +270,9 @@ trait HasMHPMSink { self: CSRModule[_] =>
   val v = IO(Input(Bool()))
   val htimedelta = IO(Input(UInt(64.W)))
 }
+
+trait HasDebugStopBundle { self: CSRModule[_] =>
+  val debugModeStopCount = IO(Input(Bool()))
+  val debugModeStopTime  = IO(Input(Bool()))
+  val unprivCountUpdate  = IO(Input(Bool()))
+}
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 8ecac7460e4..af132ab3834 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -162,6 +162,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   csrMod.io.fromTop.hartId := io.csrin.get.hartId
   csrMod.io.fromTop.clintTime := io.csrin.get.clintTime
+  csrMod.io.fromTop.criticalErrorState := io.csrin.get.criticalErrorState
   private val csrModOutValid = csrMod.io.out.valid
   private val csrModOut      = csrMod.io.out.bits
 
@@ -356,6 +357,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   }
 
   csrOut.instrAddrTransType := csrMod.io.status.instrAddrTransType
+  csrOut.criticalErrorState := csrMod.io.status.criticalErrorState
 
   csrToDecode := csrMod.io.toDecode
 }
@@ -363,6 +365,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 class CSRInput(implicit p: Parameters) extends XSBundle with HasSoCParameter{
   val hartId = Input(UInt(8.W))
   val msiInfo = Input(ValidIO(new MsiInfoBundle))
+  val criticalErrorState = Input(Bool())
   val clintTime = Input(ValidIO(UInt(64.W)))
   val trapInstInfo = Input(ValidIO(new TrapInstInfo))
   val fromVecExcpMod = Input(new Bundle {
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index cdb9f996700..a38b0c730ea 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -99,7 +99,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       val logicPhyRegMap = Vec(RabCommitWidth, ValidIO(new RegWriteFromRab))
       val excpInfo = ValidIO(new VecExcpInfo)
     })
-    val criticalError = Input(Bool())
     val debug_ls = Flipped(new DebugLSIO)
     val debugRobHead = Output(new DynInst)
     val debugEnqLsq = Input(new LsqEnqIO)
@@ -719,7 +718,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val deqFlushBlock = deqFlushBlockCounter(0)
   val deqHasCommitted = io.commits.isCommit && io.commits.commitValid(0)
   val deqHitRedirectReg = RegNext(io.redirect.valid && io.redirect.bits.robIdx === deqPtr)
-  val criticalErrorState = RegEnable(true.B, false.B, io.criticalError)
+  val criticalErrorState = io.csr.criticalErrorState
   when(deqNeedFlush && deqHitRedirectReg){
     deqFlushBlockCounter := "b111".U
   }.otherwise{
@@ -1492,9 +1491,9 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     }
 
     val diffCriticalErrorEvent = DifftestModule(new DiffCriticalErrorEvent)
-    diffCriticalErrorEvent.valid := io.criticalError && !RegNext(io.criticalError)
+    diffCriticalErrorEvent.valid := criticalErrorState && !RegNext(criticalErrorState)
     diffCriticalErrorEvent.coreid := io.hartId
-    diffCriticalErrorEvent.criticalError := io.criticalError
+    diffCriticalErrorEvent.criticalError := criticalErrorState
   }
 
   //store evetn difftest information
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 15b10b4bec8..ac1dc82b76d 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -216,6 +216,7 @@ class RobCSRIO(implicit p: Parameters) extends XSBundle {
   val trapTarget = Input(new TargetPCBundle)
   val isXRet     = Input(Bool())
   val wfiEvent   = Input(Bool())
+  val criticalErrorState = Input(Bool())
 
   val fflags     = Output(Valid(UInt(5.W)))
   val vxsat      = Output(Valid(Bool()))
```
