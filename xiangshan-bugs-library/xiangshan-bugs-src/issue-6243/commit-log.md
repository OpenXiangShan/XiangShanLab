# Commit Log
- Issue: #6243
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6243
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6243
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6243
- Changed files: 30
- Additions: 226
- Deletions: 148

## Files
- `ready-to-run`
- `src/main/scala/device/TLPMA/TLPMA.scala`
- `src/main/scala/system/SoC.scala`
- `src/main/scala/top/Top.scala`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/PMParameters.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeStage.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/IndirectCSRPermitModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala`
- `src/main/scala/xiangshan/backend/fu/PMA.scala`
- `src/main/scala/xiangshan/backend/fu/PMP.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index e131ac5ffa1..377a8548f5c 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit e131ac5ffa12e71756b992b5b867aff981a6dcba
+Subproject commit 377a8548f5cde0fc0c37468d055ef46e52870784
diff --git a/src/main/scala/device/TLPMA/TLPMA.scala b/src/main/scala/device/TLPMA/TLPMA.scala
index c039820d942..cf36ceb97e8 100644
--- a/src/main/scala/device/TLPMA/TLPMA.scala
+++ b/src/main/scala/device/TLPMA/TLPMA.scala
@@ -49,7 +49,7 @@ class TLPMA(implicit p: Parameters) extends LazyModule with PMAConst with MMPMAM
         mmpma.sameCycle/* pmaParam.sameCycle*/,
         false)).io
     ))
-    pma_check.map(_.check_env.apply(mmpma.lgMaxSize.U, pma/*placeHolder*/, pma))
+    pma_check.map(_.check_env.apply(mmpma.lgMaxSize.U, false.B, pma/*placeHolder*/, pma))
     for (i <- 0 until mmpma.num) {
       pma_check(i).req_apply(req(i).valid, req(i).bits.addr)
       resp(i) := pma_check(i).resp
diff --git a/src/main/scala/system/SoC.scala b/src/main/scala/system/SoC.scala
index 881e3bee667..9a8f02a77c8 100644
--- a/src/main/scala/system/SoC.scala
+++ b/src/main/scala/system/SoC.scala
@@ -31,7 +31,7 @@ import freechips.rocketchip.diplomacy.{AddressSet, IdRange, InModuleBody, LazyMo
 import freechips.rocketchip.interrupts.{IntSourceNode, IntSourcePortSimple}
 import freechips.rocketchip.regmapper.{RegField, RegFieldDesc, RegFieldGroup}
 import freechips.rocketchip.tilelink._
-import freechips.rocketchip.util.{AsyncQueueParams}
+import freechips.rocketchip.util.{AsyncQueueParams, AsyncQueueSource, AsyncBundle}
 import top.BusPerfMonitor
 import xiangshan.backend.fu.{MemoryRange, PMAConfigEntry, PMAConst}
 import xiangshan.{DebugOptionsKey, PMParameKey, XSTileKey}
@@ -570,7 +570,10 @@ class MemMisc()(implicit p: Parameters) extends BaseSoC
     val pll0_lock = IO(Input(Bool()))
     val pll0_ctrl = IO(Output(Vec(6, UInt(32.W))))
     val cacheable_check = IO(new TLPMAIO)
-    val clintTime = IO(Output(ValidIO(UInt(64.W))))
+    val clintTime = IO(EnableClintAsyncBridge match {
+      case Some(param) => new AsyncBundle(UInt(64.W), param)
+      case None => (ValidIO(UInt(64.W)))
+    })
     val scntIO = IO(new Bundle {
       val update_en = Input(Bool())
       val update_value = Input(UInt(timeWidth.W))
@@ -602,7 +605,17 @@ class MemMisc()(implicit p: Parameters) extends BaseSoC
     val pll_lock = RegNext(next = pll0_lock, init = false.B)
 
     // timer instance
-    clintTime :=   syscnt.module.io.time // syscnt ->timeasync
+    EnableClintAsyncBridge match {
+      case Some(param) =>
+        withClockAndReset(rtc_clock, rtc_reset) {
+          val time_source = Module(new AsyncQueueSource(UInt(64.W), param))
+          time_source.io.enq.valid := syscnt.module.io.time.valid
+          time_source.io.enq.bits := syscnt.module.io.time.bits
+          clintTime <> time_source.io.async
+        }
+      case None =>
+        clintTime <> syscnt.module.io.time
+    }
     timer.module.io.time <> syscnt.module.io.time
     timer.module.io.hartId := 0.U
 
diff --git a/src/main/scala/top/Top.scala b/src/main/scala/top/Top.scala
index ab4234475af..73522af046b 100644
--- a/src/main/scala/top/Top.scala
+++ b/src/main/scala/top/Top.scala
@@ -40,6 +40,7 @@ import freechips.rocketchip.tilelink._
 import freechips.rocketchip.interrupts._
 import freechips.rocketchip.amba.axi4._
 import freechips.rocketchip.jtag.JTAGIO
+import freechips.rocketchip.util.{AsyncQueueParams, AsyncQueueSink}
 import chisel3.experimental.annotate
 
 import scala.collection.mutable.Map
@@ -275,13 +276,23 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc()
     misc.module.bus_clock := io.clock
     misc.module.bus_reset := io.reset
 
-
+    val clintTime = WireInit(0.U.asTypeOf(ValidIO(UInt(64.W))))
+    EnableClintAsyncBridge match {
+      case Some(param) =>
+        val time_sink = withClockAndReset(core_with_l2.head.module.clock, core_with_l2.head.module.reset)(Module(new AsyncQueueSink(UInt(64.W), param)))
+        time_sink.io.async <> misc.module.clintTime
+        time_sink.io.deq.ready := true.B
+        clintTime.valid := time_sink.io.deq.valid
+        clintTime.bits  := time_sink.io.deq.bits
+      case None =>
+       clintTime := misc.module.clintTime
+    }
 
     for ((core, i) <- core_with_l2.zipWithIndex) {
       core.module.io.hartId := i.U
       core.module.io.msiInfo := msiInfo
       core.module.io.teemsiInfo.foreach(_ := msiInfo)
-      core.module.io.clintTime := misc.module.clintTime
+      core.module.io.clintTime := clintTime
       io.riscv_wfi(i) := core.module.io.cpu_wfi
       io.riscv_critical_error(i) := core.module.io.cpu_crtical_error
       // trace Interface
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 1c549f68c17..ba10a9f14b7 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -551,6 +551,7 @@ class TlbCsrBundle(implicit p: Parameters) extends XSBundle {
     val spvp = UInt(1.W)
     val imode = UInt(2.W)
     val dmode = UInt(2.W)
+    val debug = Bool()
   }
   val mPBMTE = Bool()
   val hPBMTE = Bool()
diff --git a/src/main/scala/xiangshan/PMParameters.scala b/src/main/scala/xiangshan/PMParameters.scala
index 6ec43d11b09..64c83c65daa 100644
--- a/src/main/scala/xiangshan/PMParameters.scala
+++ b/src/main/scala/xiangshan/PMParameters.scala
@@ -19,6 +19,7 @@ package xiangshan
 import chisel3.util.log2Ceil
 import org.chipsalliance.cde.config.{Field, Parameters}
 import freechips.rocketchip.tile.XLen
+import freechips.rocketchip.devices.debug.DebugModuleKey
 import system.{CVMParamsKey, SoCParamsKey}
 import xiangshan.backend.fu.{MMPMAConfig, MMPMAMethod}
 
@@ -57,4 +58,7 @@ trait HasPMParameters {
 
   def PlatformGrain = pmParams.PlatformGrain
   def mmpma = pmParams.mmpma
+
+  def debugStart = p(DebugModuleKey).get.baseAddress
+  def debugEnd = debugStart + 0xFFF
 }
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 68ee9ea72ef..d26a548f5fc 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -597,7 +597,13 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
 
   val topDownPerf = topDownMod.getPerfEvents
 
-  val perfBackend  = Seq()
+  XSPerfAccumulate("cpu_cycle", true.B)
+  XSPerfAccumulate("ref_cpu_cycle", io.fromTop.clintTime.valid)
+
+  val perfBackend  = Seq(
+    ("cpu_cycle",     true.B),
+    ("ref_cpu_cycle", io.fromTop.clintTime.valid)
+  )
   // let index = 0 be no event
   val allPerfEvents = Seq(("noEvent", 0.U)) ++ ctrlBlockPerf ++ topDownPerf ++ perfBackend
 
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index e5002c03e15..b5a046fee26 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -537,6 +537,7 @@ class CtrlBlockImp(
   decode.io.vlRat  <> rename.io.vlReadPorts
   decode.io.fusion := 0.U.asTypeOf(decode.io.fusion) // Todo
   decode.io.stallReason.in <> io.frontend.stallReason
+  decode.io.backendCanAccept := io.frontend.canAccept
 
   // snapshot check
   class CFIRobIdx extends Bundle {
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
index 763537cc3ad..792c6a17849 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
@@ -58,6 +58,7 @@ class DecodeStageIO(implicit p: Parameters) extends XSBundle {
   val csrCtrl = Input(new CustomCSRCtrlIO)
   val fromCSR = Input(new CSRToDecode)
   val fusion = Vec(DecodeWidth - 1, Input(Bool()))
+  val backendCanAccept = Input(Bool())
 
   // vtype update
   val fromRob = new Bundle {
@@ -426,6 +427,8 @@ class DecodeStage(implicit p: Parameters) extends XSModule
   XSPerfAccumulate("wait_cycle", !io.in.head.valid && io.out.head.ready)
   XSPerfAccumulate("inst_spec", PopCount(io.in.map(_.fire)))
   XSPerfAccumulate("recovery_bubble", recoveryFlag)
+  XSPerfAccumulate("frontend_stall_cycle", GatedValidRegNext(!io.in.head.valid && io.backendCanAccept))
+  XSPerfAccumulate("backend_stall_cycle",  GatedValidRegNext(io.in.head.valid && !io.in.head.ready))
 
   XSPerfHistogram("in_valid_range", PopCount(io.in.map(_.valid)), true.B, 0, DecodeWidth + 1, 1)
   XSPerfHistogram("in_fire_range", PopCount(io.in.map(_.fire)), true.B, 0, DecodeWidth + 1, 1)
@@ -434,7 +437,7 @@ class DecodeStage(implicit p: Parameters) extends XSModule
 
   val fusionValid = VecInit(io.fusion.map(x => GatedValidRegNext(x)))
   val inValidNotReady = io.in.map(in => GatedValidRegNext(in.valid && !in.ready))
-  val frontendStallReg = GatedValidRegNext(!io.in.head.valid && io.in.head.ready)
+  val frontendStallReg = GatedValidRegNext(!io.in.head.valid && io.backendCanAccept)
   val backendStall  = GatedValidRegNext(io.in.head.valid && !io.in.head.ready)
   val perfEvents = Seq(
     ("decoder_fused_instr",  PopCount(fusionValid)       ),
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
index 8e22c183ae6..0ea490dc5ef 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
@@ -13,7 +13,63 @@ import xiangshan.XSBundle
 
 import scala.collection.immutable.SeqMap
 
-trait CSRAIA { self: NewCSR with HypervisorLevel =>
+trait CSRAIA extends HasSoCParameter { self: NewCSR with HypervisorLevel =>
+  private def fieldWritableMap(fields: Seq[CSREnumType]): Map[Int, Boolean] =
+    fields.map(f => f.lsb -> !f.isRO).toMap
+
+  private def validFieldWritableMap(fields: Seq[Valid[CSREnumType]]): Map[Int, Boolean] =
+    fields.map(f => f.bits.lsb -> !f.bits.isRO).toMap
+
+  private def iprioMask(base: Int, writableMaps: Seq[Map[Int, Boolean]]): UInt = {
+    val mask = (0 until 8).foldLeft(BigInt(0)) { case (m, i) =>
+      val intNo = base + i
+      val writable = writableMaps.exists(_.getOrElse(intNo, false))
+      if (writable) m | (BigInt(0xff) << (8 * i)) else m
+    }
+    mask.U(64.W)
+  }
+
+  private lazy val mieFieldWritable: Map[Int, Boolean] = {
+    val m = fieldWritableMap(new MieBundle().getFields)
+    if (soc.IMSICParams.HasTEEIMSIC) m else m.updated(InterruptNO.ASNI, false)
+  }
+
+  /*
+    For a given interrupt number, if the corresponding bit in mie is read-only zero,
+    then the interrupt’s priority number in the iprio array must be read-only zero as well.
+    static mask
+  */
+  private def miprioMask(base: Int): UInt =
+    iprioMask(base, Seq(mieFieldWritable))
+
+  private def gatedWritable(writableMap: Map[Int, Boolean], intNo: Int, gate: Bool): Bool =
+    if (writableMap.getOrElse(intNo, false)) gate else false.B
+
+  /*
+    For a given interrupt number, if the corresponding bit is not writable either in sie or,
+    if the H extension is implemented, in hie,
+    then the interrupt’s priority number in the supervisor-level iprio array must be read-only zero as well.
+    Dynamic mask
+  */
+  private def siprioMask(base: Int, midelegBits: UInt, mvienBits: UInt): UInt = {
+    val sieRegWritable = fieldWritableMap(new SieBundle().getFields)
+    val sieToMieWritable = validFieldWritableMap(new SieToMie().getAll)
+    val hieToMieWritable = validFieldWritableMap(new HieToMie().getAll)
+
+    Cat((0 until 8).reverse.map { i =>
+      val intNo = base + i
+      val delegated = midelegBits(intNo)
+      val virtualized = mvienBits(intNo)
+      val writableInSie =
+        gatedWritable(sieToMieWritable, intNo, delegated) ||
+        gatedWritable(sieRegWritable, intNo, !delegated && virtualized)
+      val writableInHie =
+        gatedWritable(hieToMieWritable, intNo, delegated)
+
+      Fill(8, writableInSie || writableInHie)
+    })
+  }
+
   val mtopei = Module(new CSRModule("Mtopei", new TopEIBundle) with HasAIABundle {
     regOut := aiaToCSR.mtopei
   })
@@ -47,60 +103,36 @@ trait CSRAIA { self: NewCSR with HypervisorLevel =>
   })
     .setAddr(CSRs.vstopi)
 
-  val miprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, mie.asUInt(i))
-    }
-    regOut := reg & mask.asUInt
+  val miprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) {
+    regOut := reg & miprioMask(0)
   })
     .setAddr(0x30)
 
-  val miprio2 = Module(new CSRModule(s"Iprio2", new MIprio2Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, mie.asUInt(i+8))
-    }
-    regOut := reg & mask.asUInt
+  val miprio2 = Module(new CSRModule(s"Iprio2", new MIprio2Bundle) {
+    regOut := reg & miprioMask(8)
   })
     .setAddr(0x32)
 
   val miprios: Seq[CSRModule[_]] = (4 to (0xF, 2)).map(num =>
-    Module(new CSRModule(s"Iprio$num", new IprioBundle) with HasIeBundle {
-      val mask = Wire(Vec(8, UInt(8.W)))
-      for (i <- 0 until 8) {
-        mask(i) := Fill(8, mie.asUInt(num*4+i))
-      }
-      regOut := reg & mask.asUInt
+    Module(new CSRModule(s"Iprio$num", new IprioBundle) {
+      regOut := reg & miprioMask(num * 4)
     })
       .setAddr(0x30 + num)
   )
 
-  val siprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, sie.asUInt(i))
-    }
-    regOut := reg & mask.asUInt
+  val siprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) with HasSiprios {
+    regOut := reg & siprioMask(0, mideleg.asUInt, mvien.asUInt)
   })
     .setAddr(0x30)
 
-  val siprio2 = Module(new CSRModule(s"Iprio2", new SIprio2Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, sie.asUInt(i+8))
-    }
-    regOut := reg & mask.asUInt
+  val siprio2 = Module(new CSRModule(s"Iprio2", new SIprio2Bundle) with HasSiprios {
+    regOut := reg & siprioMask(8, mideleg.asUInt, mvien.asUInt)
   })
     .setAddr(0x32)
 
   val siprios: Seq[CSRModule[_]] = (4 to (0xF, 2)).map(num =>
-    Module(new CSRModule(s"Iprio$num", new IprioBundle) with HasIeBundle{
-      val mask = Wire(Vec(8, UInt(8.W)))
-      for (i <- 0 until 8) {
-        mask(i) := Fill(8, sie.asUInt(num*4+i))
-      }
-      regOut := reg & mask.asUInt
+    Module(new CSRModule(s"Iprio$num", new IprioBundle) with HasSiprios {
+      regOut := reg & siprioMask(num * 4, mideleg.asUInt, mvien.asUInt)
     })
     .setAddr(0x30 + num)
   )
@@ -109,8 +141,6 @@ trait CSRAIA { self: NewCSR with HypervisorLevel =>
 
   val siregiprios: Seq[CSRModule[_]] = Seq(siprio0, siprio2) ++: siprios
 
-  val iregiprios = miregiprios ++ siregiprios
-
   val aiaCSRMods = Seq(
     mtopei,
     mtopi,
@@ -282,7 +312,7 @@ trait HasIregSink { self: CSRModule[_] =>
   }))
 }
 
-trait HasIeBundle { self: CSRModule[_] =>
-  val mie = IO(Input(new MieBundle))
-  val sie = IO(Input(new SieBundle))
+trait HasSiprios { self: CSRModule[_] =>
+  val mideleg = IO(Input(new MidelegBundle))
+  val mvien = IO(Input(new MvienBundle))
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
index 3763ce1d472..c09aae03dd3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
@@ -12,7 +12,7 @@ import xiangshan.AddrTransType
 
 
 class MNretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.MNPP, _.MNPV, _.NMIE))
+  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.NMIE))
   val mstatus   = ValidIO((new MstatusBundle).addInEvent(_.MPRV, _.MDT, _.SDT))
   val vsstatus  = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val targetPc  = ValidIO(new TargetPCBundle)
@@ -66,8 +66,6 @@ class MNretEventModule(implicit p: Parameters) extends Module with CSREventBase
   out.targetPc .valid := valid
 
   out.privState.bits          := outPrivState
-  out.mnstatus.bits.MNPP      := PrivMode.U
-  out.mnstatus.bits.MNPV      := VirtMode.Off.asUInt
   out.mnstatus.bits.NMIE      := 1.U
   out.mstatus.bits.MPRV       := Mux(in.mnstatus.MNPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
   // clear MDT when mnret to below M
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
index 7ff2c8d9c40..95920daf038 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
@@ -58,6 +58,7 @@ class MretEventModule(implicit p: Parameters) extends Module with CSREventBase {
 
   out.privState.valid := valid
   out.mstatus  .valid := valid
+  out.vsstatus .valid := valid
   out.targetPc .valid := valid
 
   out.privState.bits          := outPrivState
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
index 6021d4c3594..4dcb79549f4 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
@@ -77,10 +77,9 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   private val tvalFillPcPlus2  = (isFetchExcp || isFetchGuestExcp) && fetchCrossPage
   private val tvalFillMemVaddr = isMemExcp || isMemBkpt
   private val tvalFillGVA      =
-    isHlsExcp && isMemExcp ||
     isLSGuestExcp|| isFetchGuestExcp ||
     (isFetchExcp || isFetchBkpt) && fetchIsVirt ||
-    (isMemExcp || isMemBkpt) && memIsVirt
+    (isMemExcp || isMemBkpt) && (memIsVirt || isHlsExcp)
   private val tvalFillInst     = isIllegalInst
 
   private val tval = Mux1H(Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
index 4d041efab8c..653f0e3176e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
@@ -75,10 +75,9 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   private val tvalFillPcPlus2  = (isFetchExcp || isFetchGuestExcp) && fetchCrossPage
   private val tvalFillMemVaddr = isMemExcp || isMemBkpt
   private val tvalFillGVA      =
-    isHlsExcp && isMemExcp ||
     isLSGuestExcp|| isFetchGuestExcp ||
     (isFetchExcp || isFetchBkpt) && fetchIsVirt ||
-    (isMemExcp || isMemBkpt) && memIsVirt
+    (isMemExcp || isMemBkpt) && (memIsVirt || isHlsExcp)
   private val tvalFillInst     = isIllegalInst
 
   private val tval = Mux1H(Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
index a89be860285..0f1ef35c2e1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
@@ -116,9 +116,8 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   // SPVP is not PrivMode enum type, so asUInt and shrink the width
   out.vsepc.bits.epc             := Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1))
   out.vscause.bits.Interrupt     := isInterrupt
-  out.vscause.bits.ExceptionCode := Mux(virtualInterruptIsHvictlInject, hvictlIID, highPrioTrapNO)
+  out.vscause.bits.ExceptionCode := Mux(virtualInterruptIsHvictlInject && isInterrupt, hvictlIID, highPrioTrapNO)
   out.vstval.bits.ALL            := Mux(isFetchMalAddrExcp, in.fetchMalTval, tval)
-
   dontTouch(tvalFillGVA)
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
index fd0486bf53d..1723b0f14ee 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
@@ -29,8 +29,7 @@ trait CSRPMA { self: NewCSR =>
   val pmaaddr: Seq[CSRModule[_]] = Range(0, p(PMParameKey).NumPMAReal).map(num =>
     Module(new CSRModule(s"Pmaaddr$num") with HasPMAAddrSink {
       // read condition
-      regOut := addrRegOut(num)
-      rdata := addrRData(num)
+      regOut := addrRData(num)
     })
       .setAddr(CSRConst.PmaaddrBase + num)
   )
@@ -77,7 +76,6 @@ trait HasPMACfgRSink { self: CSRModule[_] =>
 
 trait HasPMAAddrSink { self: CSRModule[_] =>
   val addrRData = IO(Input(Vec(p(PMParameKey).NumPMAReal, UInt(64.W))))
-  val addrRegOut = IO(Input(Vec(p(PMParameKey).NumPMAReal, UInt(64.W))))
 }
 
 trait PMAInit extends HasPMParameters with PMAReadWrite {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala
index a1e5bcddebb..40e88b4ac8d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala
@@ -44,7 +44,7 @@ trait CSRPMP { self: NewCSR =>
     if (num < p(PMParameKey).NumPMPReal) {
       Module(new CSRModule(s"Pmpaddr$num", new PMPAddrBundle) with HasPMPAddrSink {
         // read condition
-        rdata := addrRData(num)
+        regOut := addrRData(num)
       })
         .setAddr(CSRs.pmpaddr0 + num)
     } else {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
index 93847660598..4532d469d99 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
@@ -225,7 +225,9 @@ class MLevelPermitModule extends Module {
 
   private val fpVec_EX_II = fpOff_EX_II || vecOff_EX_II
 
-  private val rwStimecmp_EX_II = !privState.isModeM && (!mcounterenTM || !menvcfgSTCE) && (addr === CSRs.vstimecmp.U || addr === CSRs.stimecmp.U)
+  private val rwStimecmp_EX_II = !privState.isModeM &&
+                                (!mcounterenTM && (addr === CSRs.vstimecmp.U || addr === CSRs.stimecmp.U) ||
+                                 !menvcfgSTCE && (addr === CSRs.stimecmp.U))
 
   private val accessHPM_EX_II = csrIsHPM && !privState.isModeM && !mcounteren(counterAddr)
 
@@ -547,7 +549,7 @@ class xcounterenIO extends Bundle {
 
 class xenvcfgIO extends Bundle {
   // Machine environment configuration register.
-  // Accessing stimecmp or vstimecmp from **Non-M level** will trap EX_II, if menvcfg.STCE=0
+  // Accessing stimecmp from **Non-M level** will trap EX_II, if menvcfg.STCE=0
   val menvcfg = UInt(64.W)
   // Hypervisor environment configuration register.
   // Accessing vstimecmp from ** V level** will trap EX_VI, if menvcfg.STCE=1 && henvcfg.STCE=0
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/IndirectCSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/IndirectCSRPermitModule.scala
index 18aaba9110b..ea7369f135b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/IndirectCSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/IndirectCSRPermitModule.scala
@@ -100,8 +100,7 @@ class IndirectAIAPermitModule extends IndirectCSRWindowPermitModule {
 
   private val rwSireg_EX_II = (
     !isVirtual && Iselect.isOdd(siselect) ||
-    isModeHS && !mstateen0.AIA.asBool ||
-    isModeVS && !mstateen0.AIA.asBool
+    isModeHS && !mstateen0.AIA.asBool
   ) && siregInAIA && isSireg
 
   private val rwSireg_EX_VI = isModeVS && siregInAIA && isSireg
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index edbf55fc4a9..2f2019d0462 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -36,8 +36,11 @@ class InterruptFilter extends Module {
   val miprios = io.in.miprios
   val hsiprios = io.in.hsiprios
   val hviprios = Cat(hviprio2.asUInt, hviprio1.asUInt)
-  val fromAIAValid = io.in.fromAIA.meip || io.in.fromAIA.seip || io.in.fromAIA.notice_pending
   val platformValid = io.in.platform.meip || io.in.platform.seip
+  val mvienSEIE = io.in.mvienSEIE
+  val mvipSEIP = io.in.mvipSEIP
+  val midelegSEI = io.in.mideleg.SEI.asBool
+  val SEIfromEIC = io.in.platform.seip || io.in.fromAIA.seip
 
   /**
    * Sort by implemented interrupt default priority
@@ -59,12 +62,7 @@ class InterruptFilter extends Module {
   val hstopigather = hsip & hsie & (~hideleg).asUInt
   val vstopigather = vsip & vsie & NoSEIMask
 
-  val flag = RegInit(false.B)
-  when (platformValid) {
-    flag := true.B
-  }.elsewhen(fromAIAValid) {
-    flag := false.B
-  }
+  val injectSEI = !(midelegSEI === mvienSEIE) && mvipSEIP
 
   val mipriosSort = Wire(Vec(InterruptNO.interruptDefaultPrio.size, new IpriosSort))
   mipriosSort.zip(InterruptNO.interruptDefaultPrio).zipWithIndex.foreach { case ((iprio, defaultPrio), i) =>
@@ -72,7 +70,7 @@ class InterruptFilter extends Module {
     when (mtopigather(defaultPrio)) {
       iprio.enable := true.B
       when (defaultPrio.U === InterruptNO.MEI.U) {
-        iprio.isZero := platformValid || flag
+        iprio.isZero := platformValid
         val mtopeiGreaterThan255 = mtopei.IPRIO.asUInt(10, 8).orR
         iprio.greaterThan255 := mtopeiGreaterThan255
         iprio.prioNum := mtopei.IPRIO.asUInt(7, 0)
@@ -94,9 +92,9 @@ class InterruptFilter extends Module {
     when (hstopigather(defaultPrio)) {
       iprio.enable := true.B
       when (defaultPrio.U === InterruptNO.SEI.U) {
-        iprio.isZero := platformValid || flag
+        iprio.isZero := platformValid
         val stopeiGreaterThan255 = stopei.IPRIO.asUInt(10, 8).orR
-        iprio.greaterThan255 := stopeiGreaterThan255
+        iprio.greaterThan255 := (injectSEI && !SEIfromEIC) || stopeiGreaterThan255
         iprio.prioNum := stopei.IPRIO.asUInt(7, 0)
       }.otherwise {
         iprio.isZero := !hsiprios(7 + 8 * defaultPrio, 8 * defaultPrio).orR
@@ -209,7 +207,7 @@ class InterruptFilter extends Module {
 
   private val meiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
   private val seiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
-  private val vseiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.VSEI).U
+  private val vseiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
 
   private val mipriosTmp = Wire(Vec(8, new IpriosSort))
   mipriosSortTmp.zipWithIndex.foreach { case (iprios, i) =>
@@ -360,8 +358,9 @@ class InterruptFilter extends Module {
   val C1GreaterThan255 = vstopeiReg.IPRIO.asUInt(10, 8).orR
   val C4IsZero = !hvipriosRegTmp.prioNum.orR
   val C2C5IsZero = !hvictlReg.IPRIO.asUInt.orR
-  val C4HighVSEI = iidC4Idx < findIndex(InterruptNO.VSEI.U)
-  val SEIHighC4 = findIndex(InterruptNO.SEI.U) < iidC4Idx
+  val SEIIdx = findIndex(InterruptNO.SEI.U)
+  val C4HighVSEI = iidC4Idx < SEIIdx
+  val SEIHighC4 = SEIIdx < iidC4Idx
   val iprioC1GreaterThan255 = Mux(C1GreaterThan255, 255.U, iprioC1Tmp)
 
   iprioC1 := vstopeiReg.IPRIO.asUInt
@@ -426,7 +425,7 @@ class InterruptFilter extends Module {
   iidC3C4 := Mux(C4IsZero, Mux(C4HighVSEI, iidOnlyC4, iidOnlyC1), iidOnlyC4)
   iprioC3C4 := iprioC4Tmp
   // C3,C5 enable
-  iidC3C5 := Mux(C2C5IsZero, Mux(hvictlReg.DPR.asBool, iidOnlyC5, iidOnlyC1), iidOnlyC5)
+  iidC3C5 := Mux(C2C5IsZero, Mux(hvictlReg.DPR.asBool, iidOnlyC1, iidOnlyC5), iidOnlyC5)
   iprioC3C5 := iprioC3C5Tmp
 
   // update vstopi
@@ -543,8 +542,10 @@ class InterruptFilter extends Module {
 
   // virtual interrupt with hvictl injection
   val vsIRModeCond = privState.isModeVS && vsstatusSIE || privState < PrivState.ModeVS
-  val SelectCandidate5 = onlyC5EnableReg || C3C5EnableReg ||
-                         C1C5EnableReg && (iprioC1 === iprioC2C5 && !hvictlReg.DPR.asBool || iprioC1 > iprioC2C5)
+  val SelectCandidate5 = onlyC5EnableReg ||
+                         C1C5EnableReg && ((!C2C5IsZero && (iprioC1 > iprioC2C5 || (iprioC1 === iprioC2C5) && !hvictlReg.DPR.asBool)) ||
+                                           (C2C5IsZero && !hvictlReg.DPR.asBool)) ||
+                         C3C5EnableReg && (!C2C5IsZero || !hvictlReg.DPR.asBool)
   val viIsHvictlInjectReg = RegNext(vsIRModeCond && SelectCandidate5 && io.in.mnstatusNMIE, false.B)
 
   io.out.interruptVec.valid := intrVecReg.orR || debugIntrReg
@@ -604,6 +605,8 @@ class InterruptFilterIO extends Bundle {
       val seip = Bool()
       val notice_pending = Bool()
     }
+    val mvienSEIE = Bool()
+    val mvipSEIP = Bool()
   })
 
   val out = Output(new Bundle {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 72bf9870db6..5af39e7de6c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -215,6 +215,7 @@ class NewCSR(implicit val p: Parameters) extends Module
       val spvp = Bool()
       val imode = UInt(2.W)
       val dmode = UInt(2.W)
+      val debug = Bool()
       val dvirt = Bool()
       val mPBMTE = Bool()
       val hPBMTE = Bool()
@@ -403,11 +404,13 @@ class NewCSR(implicit val p: Parameters) extends Module
   intrMod.io.in.fromAIA.meip := fromAIA.meip
   intrMod.io.in.fromAIA.seip := fromAIA.seip
   intrMod.io.in.fromAIA.notice_pending := fromAIA.notice_pending
+  intrMod.io.in.mvienSEIE := mvien.regOut.SEIE.asBool
+  intrMod.io.in.mvipSEIP := mvip.regOut.SEIP.asBool
 
   val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
   val debug = RegEnable(intrMod.io.out.debug, false.B, intrMod.io.out.interruptVec.valid)
   val nmi = intrMod.io.out.nmi
-  val virtualInterruptIsHvictlInject = RegEnable(intrMod.io.out.virtualInterruptIsHvictlInject, false.B, intrMod.io.out.interruptVec.valid)
+  val virtualInterruptIsHvictlInject = intrMod.io.out.virtualInterruptIsHvictlInject
   val irToHS = RegEnable(intrMod.io.out.irToHS, false.B, intrMod.io.out.interruptVec.valid)
   val irToVS = RegEnable(intrMod.io.out.irToVS, false.B, intrMod.io.out.interruptVec.valid)
 
@@ -443,7 +446,6 @@ class NewCSR(implicit val p: Parameters) extends Module
   // PMP
   val pmpEntryMod = Module(new PMPEntryHandleModule)
   pmpEntryMod.io.in.pmpCfg  := pmpcfgs.map(_.regOut.asInstanceOf[PMPCfgBundle])
-  pmpEntryMod.io.in.pmpAddr := pmpaddr.take(NumPMPReal).map(_.regOut.asInstanceOf[PMPAddrBundle])
   pmpEntryMod.io.in.ren   := ren
   pmpEntryMod.io.in.wen   := wenLegalReg
   pmpEntryMod.io.in.addr  := addr
@@ -571,11 +573,11 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod.w.wdata := wdata
   }
 
-  iregiprios.foreach { mod =>
+  siregiprios.foreach { mod =>
     mod match {
-      case m: HasIeBundle =>
-        m.mie := mie.regOut
-        m.sie := sie.regOut
+      case m: HasSiprios =>
+        m.mideleg := mideleg.regOut
+        m.mvien   := mvien.regOut
       case _ =>
     }
   }
@@ -587,13 +589,6 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod.w.wdata := pmpEntryMod.io.out.pmpCfgWData(8*((i%8)+1)-1,8*(i%8))
   }
 
-  pmpaddr.zipWithIndex.foreach { case (mod, i) =>
-    if (i < NumPMPReal) {
-      mod.w.wen   := wenLegalReg && (addr === (CSRs.pmpaddr0 + i).U)
-      mod.w.wdata := pmpEntryMod.io.out.pmpAddrWData(i)
-    }
-  }
-
   pmacfgs.zipWithIndex.foreach { case (mod, i) =>
     mod.w.wen   := wenLegalReg && (addr === (CSRConst.PmacfgBase + i / 8 * 2).U)
     mod.w.wdata := pmaEntryMod.io.out.pmaCfgWdata(8*((i%8)+1)-1,8*(i%8))
@@ -729,7 +724,6 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod match {
       case m: HasPMAAddrSink =>
         m.addrRData := pmaEntryMod.io.out.pmaAddrRData
-        m.addrRegOut := pmaEntryMod.io.out.pmaAddrRegOut
       case _ =>
     }
     mod match {
@@ -1164,7 +1158,10 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.status.vecState.vlenb := vlenb.rdata.asUInt
   io.status.vecState.off := mstatus.regOut.VS === ContextStatus.Off
   io.status.interrupt := intrMod.io.out.interruptVec.valid
-  io.status.wfiEvent := debugIntr || (mie.rdata.asUInt & mip.rdata.asUInt).orR || nmip.asUInt.orR
+  io.status.wfiEvent := debugIntr || nmip.asUInt.orR ||
+                        (mtopi.regOut.IID.asUInt =/= 0.U) ||
+                        (stopi.regOut.IID.asUInt =/= 0.U) ||
+                        (vstopi.regOut.IID.asUInt =/= 0.U)
   io.status.debugMode := debugMode
   io.status.singleStepFlag := !debugMode && dcsr.regOut.STEP
 
@@ -1543,6 +1540,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     mstatus.regOut.MPV.asUInt,
     V.asUInt
   )
+  io.tlb.debug := debugMode
   io.tlb.mPBMTE := RegNext(menvcfg.regOut.PBMTE.asBool)
   io.tlb.hPBMTE := RegNext(henvcfg.regOut.PBMTE.asBool)
   io.tlb.pmm.mseccfg := RegNext(mseccfg.regOut.PMM.asUInt)
@@ -1597,7 +1595,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.status.criticalErrorState := criticalErrorState && !dcsr.regOut.CETRIG.asBool
 
   val criticalErrors = Seq(
-    ("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !entryDebugMode),
+    ("csr_dbltrp_inMN", !mnstatus.regOut.NMIE && hasTrap && !(entryDebugMode || debugMode)),
   )
   criticalErrorStateInCSR := criticalErrors.map(criticalError => criticalError._2).reduce(_ || _).asBool
   generateCriticalErrors()
@@ -1626,8 +1624,8 @@ class NewCSR(implicit val p: Parameters) extends Module
 
     val hartId = io.fromTop.hartId
     val trapValid = pendingTrap && !io.fromVecExcpMod.busy
-    val trapNO = Mux(virtualInterruptIsHvictlInject && hasTrap, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt)
     val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool
+    val trapNO = Mux(virtualInterruptIsHvictlInject && interrupt, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt)
     val hasNMI = nmi && hasTrap
     val interruptNO = Mux(interrupt, trapNO, 0.U)
     val exceptionNO = Mux(!interrupt, trapNO, 0.U)
@@ -1655,7 +1653,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     diffArchEvent.exception := RegEnable(exceptionNO, hasTrap)
     diffArchEvent.exceptionPC := RegEnable(exceptionPC, hasTrap)
     diffArchEvent.hasNMI := RegEnable(hasNMI, hasTrap)
-    diffArchEvent.virtualInterruptIsHvictlInject := RegNext(virtualInterruptIsHvictlInject && hasTrap)
+    diffArchEvent.virtualInterruptIsHvictlInject := RegNext(virtualInterruptIsHvictlInject && interrupt)
     diffArchEvent.irToHS := RegEnable(irToHS, hasTrap)
     diffArchEvent.irToVS := RegEnable(irToVS, hasTrap)
     if (env.EnableDifftest) {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala
index 3a61b19dbdd..e500ccff445 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala
@@ -91,7 +91,6 @@ class PMAEntryHandleModule(implicit p: Parameters) extends PMAModule with PMAIni
   }
 
   io.out.pmaAddrRData := pmaAddrR
-  io.out.pmaAddrRegOut := pmaAddr
 
 }
 
@@ -107,7 +106,6 @@ class PMAEntryHandleIOBundle(implicit p: Parameters) extends PMABundle {
   val out = Output(new Bundle {
     val pmaCfgWdata = UInt(PMXLEN.W)
     val pmaAddrRData = Vec(NumPMAReal, UInt(64.W))
-    val pmaAddrRegOut = Vec(NumPMAReal, UInt(64.W))
   })
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
index 766eef744bd..aa6fcee4b4a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
@@ -19,13 +19,13 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
   val io = IO(new PMPEntryHandleIOBundle)
 
   val pmpCfg   = io.in.pmpCfg
-  val pmpAddr  = io.in.pmpAddr
 
   val ren   = io.in.ren
   val wen   = io.in.wen
   val addr  = io.in.addr
   val wdata = io.in.wdata
 
+  val pmpAddr  = RegInit(VecInit(Seq.fill(p(PMParameKey).NumPMPReal)(0.U.asTypeOf(new PMPAddrBundle))))
   val pmpMask  = RegInit(VecInit(Seq.fill(p(PMParameKey).NumPMPReal)(0.U(PMPAddrBits.W))))
 
   val pmpEntry = Wire(Vec(p(PMParameKey).NumPMPReal, new PMPEntry))
@@ -58,22 +58,20 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
 
   io.out.pmpCfgWData := Cat(cfgVec.map(_.asUInt).reverse)
 
-  val pmpAddrW = Wire(Vec(p(PMParameKey).NumPMPReal, UInt(64.W)))
   val pmpAddrR = Wire(Vec(p(PMParameKey).NumPMPReal, UInt(64.W)))
 
   for (i <- 0 until p(PMParameKey).NumPMPReal) {
-    pmpAddrW(i) := pmpEntry(i).addr.ADDRESS.asUInt
     pmpAddrR(i) := pmpEntry(i).addr.ADDRESS.asUInt
     // write pmpAddr
     when (wen && (addr === (CSRs.pmpaddr0 + i).U)) {
       if (i != (p(PMParameKey).NumPMPReal - 1)) {
         val addrNextLocked: Bool = PMPCfgLField.addrLocked(pmpEntry(i).cfg, pmpEntry(i + 1).cfg)
         pmpMask(i) := Mux(!addrNextLocked, pmpEntry(i).matchMask(wdata), pmpEntry(i).mask)
-        pmpAddrW(i) := Mux(!addrNextLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
+        pmpAddr(i) := Mux(!addrNextLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
       } else {
         val addrLocked: Bool = PMPCfgLField.addrLocked(pmpEntry(i).cfg)
         pmpMask(i) := Mux(!addrLocked, pmpEntry(i).matchMask(wdata), pmpEntry(i).mask)
-        pmpAddrW(i) := Mux(!addrLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
+        pmpAddr(i) := Mux(!addrLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
       }
     }
     // read pmpAddr
@@ -82,7 +80,6 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
     }
   }
 
-  io.out.pmpAddrWData := pmpAddrW
   io.out.pmpAddrRData := pmpAddrR
 
 }
@@ -94,13 +91,11 @@ class PMPEntryHandleIOBundle(implicit p: Parameters) extends PMPBundle {
     val addr  = UInt(12.W)
     val wdata = UInt(64.W)
     val pmpCfg  = Vec(NumPMPReal, new PMPCfgBundle)
-    val pmpAddr = Vec(NumPMPReal, new PMPAddrBundle)
   })
 
   val out = Output(new Bundle {
     val pmpCfgWData  = UInt(PMXLEN.W)
     val pmpAddrRData = Vec(NumPMPReal, UInt(64.W))
-    val pmpAddrWData = Vec(NumPMPReal, UInt(64.W))
   })
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
index b6f37228909..3ebb9a0eac5 100644
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
diff --git a/src/main/scala/xiangshan/backend/fu/PMA.scala b/src/main/scala/xiangshan/backend/fu/PMA.scala
index ae7d37575eb..be9610a76bc 100644
--- a/src/main/scala/xiangshan/backend/fu/PMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMA.scala
@@ -223,6 +223,7 @@ trait PMACheckMethod extends PMPConst {
     size: UInt,
     pmaEntries: Vec[PMPEntry],
     mode: UInt,
+    debug: Bool,
     lgMaxSize: Int
   ) = {
     val num = pmaEntries.size
@@ -237,7 +238,8 @@ trait PMACheckMethod extends PMPConst {
     val cfg_vec = Wire(Vec(num+1, new PMPEntry()))
 
     pmaEntries.zip(pmaDefault +: pmaEntries.take(num-1)).zipWithIndex.foreach{ case ((pma, last_pma), i) =>
-      val is_match = pma.is_match(addr, size, lgMaxSize, last_pma)
+      val is_match = pma.is_match(addr, size, lgMaxSize, last_pma) &&
+                     Mux(addr >= debugStart.U && addr <= debugEnd.U, debug, true.B)
       val aligned = pma.aligned(addr, size, lgMaxSize, last_pma)
 
       val cur = WireInit(pma)
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index e7159a9839e..e099717e141 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -416,6 +416,7 @@ trait PMPCheckMethod extends PMPConst {
     size: UInt,
     pmpEntries: Vec[PMPEntry],
     mode: UInt,
+    debug: Bool,
     lgMaxSize: Int
   ) = {
     val num = pmpEntries.size
@@ -431,7 +432,8 @@ trait PMPCheckMethod extends PMPConst {
     val cfg_vec = Wire(Vec(num+1, new PMPEntry()))
 
     pmpEntries.zip(pmpDefault +: pmpEntries.take(num-1)).zipWithIndex.foreach{ case ((pmp, last_pmp), i) =>
-      val is_match = pmp.is_match(addr, size, lgMaxSize, last_pmp)
+      val is_match = pmp.is_match(addr, size, lgMaxSize, last_pmp) &&
+                     Mux(addr >= debugStart.U && addr <= debugEnd.U, debug, true.B)
       val ignore = passThrough && !pmp.cfg.l
       val aligned = pmp.aligned(addr, size, lgMaxSize, last_pmp)
 
@@ -461,29 +463,33 @@ class PMPCheckerEnv(implicit p: Parameters) extends PMPBundle {
   val keyIDen = Bool()
   val cmode = Bool()
   val mode = UInt(2.W)
+  val debug = Bool()
   val pmp = Vec(NumPMPReal, new PMPEntry())
   val pma = Vec(NumPMAReal, new PMPEntry())
 
-  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
+  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
     this.keyIDen := keyIDen
     this.cmode := cmode
     this.mode := mode
+    this.debug := debug
     this.pmp := pmp
     this.pma := pma
   }
 
-  def apply(cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
+  def apply(cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
     this.keyIDen := false.B
     this.cmode := cmode
     this.mode := mode
+    this.debug := debug
     this.pmp := pmp
     this.pma := pma
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
     this.keyIDen := false.B
     this.cmode := true.B
     this.mode := mode
+    this.debug := debug
     this.pmp := pmp
     this.pma := pma
   }
@@ -494,20 +500,20 @@ class PMPCheckIO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
   val req = Flipped(Valid(new PMPReqBundle(lgMaxSize))) // usage: assign the valid to fire signal
   val resp = new PMPRespBundle()
 
-  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(keyIDen, cmode, mode, pmp, pma)
+  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(keyIDen, cmode, mode, debug, pmp, pma)
     this.req := req
     resp
   }
 
-  def apply(cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(cmode, mode, pmp, pma)
+  def apply(cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(cmode, mode, debug, pmp, pma)
     this.req := req
     resp
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(mode, debug, pmp, pma)
     this.req := req
     resp
   }
@@ -517,8 +523,8 @@ class PMPCheckIO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
     this.req.bits.apply(addr)
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
+    check_env.apply(mode, debug, pmp, pma)
     req_apply(valid, addr)
     resp
   }
@@ -529,14 +535,14 @@ class PMPCheckv2IO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
   val req = Flipped(Valid(new PMPReqBundle(lgMaxSize))) // usage: assign the valid to fire signal
   val resp = Output(new PMPConfig())
 
-  def apply(cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
-    check_env.apply(cmode, mode, pmp, pma)
+  def apply(cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
+    check_env.apply(cmode, mode, debug, pmp, pma)
     req_apply(valid, addr)
     resp
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(mode, debug, pmp, pma)
     this.req := req
     resp
   }
@@ -546,8 +552,8 @@ class PMPCheckv2IO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
     this.req.bits.apply(addr)
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
+    check_env.apply(mode, debug, pmp, pma)
     req_apply(valid, addr)
     resp
   }
@@ -583,13 +589,13 @@ class PMPChecker
    */
 
   val check_addr = Mux(io.check_env.keyIDen, req.addr(PMPAddrBits-PMPKeyIDBits-1, 0), req.addr)
-  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pmp, io.check_env.mode, lgMaxSize)
-  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pma, io.check_env.mode, lgMaxSize)
+  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pmp, io.check_env.mode, io.check_env.debug, lgMaxSize)
+  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pma, io.check_env.mode, io.check_env.debug, lgMaxSize)
 
   val cmd = if(leaveHitMux) RegEnable(req.cmd, io.req.valid) else req.cmd
   val resp_pmp = pmp_check(cmd, res_pmp.cfg)
   val resp_pma = pma_check(cmd, res_pma.cfg)
-  
+
   def keyid_check(leaveHitMux: Boolean = false, valid: Bool = true.B, addr: UInt) = {
     val resp = Wire(new PMPRespBundle)
     val keyid_nz = if (PMPKeyIDBits > 0) addr(PMPAddrBits-1, PMPAddrBits-PMPKeyIDBits) =/= 0.U else false.B
@@ -631,8 +637,8 @@ class PMPCheckerv2
 
   val req = io.req.bits
 
-  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pmp, io.check_env.mode, lgMaxSize)
-  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pma, io.check_env.mode, lgMaxSize)
+  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pmp, io.check_env.mode, io.check_env.debug, lgMaxSize)
+  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pma, io.check_env.mode, io.check_env.debug, lgMaxSize)
 
   val resp = and(res_pmp, res_pma)
 
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index c75e042176c..f8cd6ba0978 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -292,6 +292,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   tlb.priv.virt_changed := DataChanged(tlb.priv.virt)
   tlb.priv.imode := csrMod.io.tlb.imode
   tlb.priv.dmode := csrMod.io.tlb.dmode
+  tlb.priv.debug := csrMod.io.tlb.debug
 
   // Svpbmt extension enable
   tlb.mPBMTE := csrMod.io.tlb.mPBMTE
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index 3afc8fde0a4..3bf6d869b19 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -101,12 +101,12 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   pmp.io.distribute_csr := io.csr.distribute_csr
   if (HasBitmapCheck) {
     if (KeyIDBits > 0) {
-      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.KEYIDEN.asBool, csr_dup(0).mbmc.CMODE.asBool, ModeS, pmp.io.pmp, pmp.io.pma))
+      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.KEYIDEN.asBool, csr_dup(0).mbmc.CMODE.asBool, ModeS, csr_dup(0).priv.debug, pmp.io.pmp, pmp.io.pma))
     } else {
-      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.CMODE.asBool, ModeS, pmp.io.pmp, pmp.io.pma))
+      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.CMODE.asBool, ModeS, csr_dup(0).priv.debug, pmp.io.pmp, pmp.io.pma))
     }
   } else {
-    pmp_check.foreach(_.check_env.apply(ModeS, pmp.io.pmp, pmp.io.pma))
+    pmp_check.foreach(_.check_env.apply(ModeS, csr_dup(0).priv.debug, pmp.io.pmp, pmp.io.pma))
   }
 
   // add bitmapcheck
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index ef206f0131e..d2883bf0951 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -169,15 +169,23 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
           tlbCsr.mbmc.KEYIDEN.asBool,
           tlbCsr.mbmc.CMODE.asBool,
           tlbCsr.priv.imode,
+          tlbCsr.priv.debug,
           pmp.io.pmp,
           pmp.io.pma,
           requestor.req
         )
       } else {
-        checker.apply(tlbCsr.mbmc.CMODE.asBool, tlbCsr.priv.imode, pmp.io.pmp, pmp.io.pma, requestor.req)
+        checker.apply(
+          tlbCsr.mbmc.CMODE.asBool,
+          tlbCsr.priv.imode,
+          tlbCsr.priv.debug,
+          pmp.io.pmp,
+          pmp.io.pma,
+          requestor.req
+        )
       }
     } else {
-      checker.apply(tlbCsr.priv.imode, pmp.io.pmp, pmp.io.pma, requestor.req)
+      checker.apply(tlbCsr.priv.imode, tlbCsr.priv.debug, pmp.io.pmp, pmp.io.pma, requestor.req)
     }
     requestor.resp := checker.resp
   }
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 80198cba845..62a32da8325 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -713,12 +713,12 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   for ((p,d) <- pmp_check zip dtlb_pmps) {
     if (HasBitmapCheck) {
       if (KeyIDBits > 0) {
-        p.apply(tlbcsr.mbmc.KEYIDEN.asBool, tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, pmp.io.pmp, pmp.io.pma, d)
+        p.apply(tlbcsr.mbmc.KEYIDEN.asBool, tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
       } else {
-        p.apply(tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, pmp.io.pmp, pmp.io.pma, d)
+        p.apply(tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
       }
     } else {
-      p.apply(tlbcsr.priv.dmode, pmp.io.pmp, pmp.io.pma, d)
+      p.apply(tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
     }
     require(p.req.bits.size.getWidth == d.bits.size.getWidth)
   }
```
