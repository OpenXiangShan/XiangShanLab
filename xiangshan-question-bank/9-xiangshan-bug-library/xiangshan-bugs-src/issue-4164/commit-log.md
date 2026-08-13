# Commit Log
- Issue: #4164
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4164
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4164
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4164
- Changed files: 12
- Additions: 91
- Deletions: 5

## Files
- `difftest`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index e0739825904..7c070eee8d7 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit e073982590481ffe1c4850986bedcb65a7337001
+Subproject commit 7c070eee8d73c0f47d8c814a51701291ad8ee419
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index a72daa2e45f..b5d8a0f4bbe 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -602,6 +602,8 @@ class CustomCSRCtrlIO(implicit p: Parameters) extends XSBundle {
   val uncache_write_outstanding_enable = Output(Bool())
   val hd_misalign_st_enable = Output(Bool())
   val hd_misalign_ld_enable = Output(Bool())
+  val power_down_enable = Output(Bool())
+  val flush_l2_enable = Output(Bool())
   // Rename
   val fusion_enable = Output(Bool())
   val wfi_enable = Output(Bool())
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index 937580f4386..d7699262b26 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -189,6 +189,8 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
       val l2_pmp_resp = Flipped(new PMPRespBundle)
       val l2_hint = ValidIO(new L2ToL1Hint())
       val perfEvents = Output(Vec(numPCntHc * coreParams.L2NBanks + 1, new PerfEvent))
+      val l2_flush_en = Input(Bool())
+      val l2_flush_done = Output(Bool())
       // val reset_core = IO(Output(Reset()))
     })
 
@@ -201,6 +203,8 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
     io.hartId.toCore := io.hartId.fromTile
     io.cpu_halt.toTile := io.cpu_halt.fromCore
     io.cpu_critical_error.toTile := io.cpu_critical_error.fromCore
+    io.l2_flush_done := true.B //TODO connect CoupleedL2
+
     // trace interface
     val traceToTile = io.traceCoreInterface.toTile
     val traceFromCore = io.traceCoreInterface.fromCore
@@ -328,4 +332,4 @@ class L2Top()(implicit p: Parameters) extends LazyModule
   }
 
   lazy val module = new Imp(this)
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 0848e106fae..f7b72ce5b56 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -85,6 +85,9 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
     val clintTime = Input(ValidIO(UInt(64.W)))
     val reset_vector = Input(UInt(PAddrBits.W))
     val cpu_halt = Output(Bool())
+    val l2_flush_done = Input(Bool())
+    val l2_flush_en = Output(Bool())
+    val power_down_en = Output(Bool())
     val cpu_critical_error = Output(Bool())
     val resetInFrontend = Output(Bool())
     val traceCoreInterface = new TraceCoreInterface
@@ -103,6 +106,10 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
     }
   })
 
+  dontTouch(io.l2_flush_done)
+  dontTouch(io.l2_flush_en)
+  dontTouch(io.power_down_en)
+
   println(s"FPGAPlatform:${env.FPGAPlatform} EnableDebug:${env.EnableDebug}")
 
   val frontend = outer.frontend.module
@@ -182,6 +189,7 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   memBlock.io.fromTopToBackend.clintTime := io.clintTime
   memBlock.io.fromTopToBackend.msiInfo := io.msiInfo
   memBlock.io.hartId := io.hartId
+  memBlock.io.l2_flush_done := io.l2_flush_done
   memBlock.io.outer_reset_vector := io.reset_vector
   memBlock.io.outer_hc_perfEvents := io.perfEvents
   // frontend -> memBlock
@@ -242,6 +250,8 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   memBlock.io.debugRolling := backend.io.debugRolling
 
   io.cpu_halt := memBlock.io.outer_cpu_halt
+  io.l2_flush_en := memBlock.io.outer_l2_flush_en
+  io.power_down_en := memBlock.io.outer_power_down_en
   io.cpu_critical_error := memBlock.io.outer_cpu_critical_error
   io.beu_errors.icache <> memBlock.io.outer_beu_errors_icache
   io.beu_errors.dcache <> memBlock.io.error.bits.toL1BusErrorUnitInfo(memBlock.io.error.valid)
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index e88be0db82f..feef7454d1a 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -103,6 +103,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
       val msiInfo = Input(ValidIO(new MsiInfoBundle))
       val reset_vector = Input(UInt(PAddrBits.W))
       val cpu_halt = Output(Bool())
+      val cpu_poff = Output(Bool())
       val cpu_crtical_error = Output(Bool())
       val hartIsInReset = Output(Bool())
       val traceCoreInterface = new TraceCoreInterface
@@ -117,6 +118,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
     dontTouch(io.hartId)
     dontTouch(io.msiInfo)
+    dontTouch(io.cpu_poff)
     if (!io.chi.isEmpty) { dontTouch(io.chi.get) }
 
     val core_soft_rst = core_reset_sink.in.head._1 // unused
@@ -139,6 +141,11 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
     l2top.module.io.beu_errors.icache <> core.module.io.beu_errors.icache
     l2top.module.io.beu_errors.dcache <> core.module.io.beu_errors.dcache
+
+    //lower power
+    l2top.module.io.l2_flush_en := core.module.io.l2_flush_en
+    core.module.io.l2_flush_done := l2top.module.io.l2_flush_done
+    io.cpu_poff := core.module.io.power_down_en
     if (enableL2) {
       // TODO: add ECC interface of L2
 
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index b0e208b7583..bcd19fe04ce 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -562,6 +562,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   csrin.msiInfo.bits := RegEnable(io.fromTop.msiInfo.bits, io.fromTop.msiInfo.valid)
   csrin.clintTime.valid := RegNext(io.fromTop.clintTime.valid)
   csrin.clintTime.bits := RegEnable(io.fromTop.clintTime.bits, io.fromTop.clintTime.valid)
+  csrin.l2FlushDone := RegNext(io.fromTop.l2FlushDone)
   csrin.trapInstInfo := ctrlBlock.io.toCSR.trapInstInfo
   csrin.fromVecExcpMod.busy := vecExcpMod.o.status.busy
   csrin.criticalErrorState := backendCriticalError
@@ -1027,6 +1028,7 @@ class TopToBackendBundle(implicit p: Parameters) extends XSBundle {
   val externalInterrupt = Output(new ExternalInterruptIO)
   val msiInfo           = Output(ValidIO(new MsiInfoBundle))
   val clintTime         = Output(ValidIO(UInt(64.W)))
+  val l2FlushDone       = Output(Bool())
 }
 
 class BackendToTopBundle extends Bundle {
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index b7472c93050..ed90083bf5e 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -316,6 +316,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     val l2PfqBusy = Input(Bool())
     val l2_tlb_req = Flipped(new TlbRequestIO(nRespDups = 2))
     val l2_pmp_resp = new PMPRespBundle
+    val l2_flush_done = Input(Bool())
 
     val debugTopDown = new Bundle {
       val robHeadVaddr = Flipped(Valid(UInt(VAddrBits.W)))
@@ -332,6 +333,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     val inner_reset_vector = Output(UInt(PAddrBits.W))
     val outer_reset_vector = Input(UInt(PAddrBits.W))
     val outer_cpu_halt = Output(Bool())
+    val outer_l2_flush_en = Output(Bool())
+    val outer_power_down_en = Output(Bool())
     val outer_cpu_critical_error = Output(Bool())
     val inner_beu_errors_icache = Input(new L1BusErrorUnitInfo)
     val outer_beu_errors_icache = Output(new L1BusErrorUnitInfo)
@@ -357,6 +360,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   dontTouch(io.inner_reset_vector)
   dontTouch(io.outer_reset_vector)
   dontTouch(io.outer_cpu_halt)
+  dontTouch(io.outer_l2_flush_en)
+  dontTouch(io.outer_power_down_en)
   dontTouch(io.outer_cpu_critical_error)
   dontTouch(io.inner_beu_errors_icache)
   dontTouch(io.outer_beu_errors_icache)
@@ -1870,6 +1875,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   ))
   io.mem_to_ooo.topToBackendBypass match { case x =>
     x.hartId            := io.hartId
+    x.l2FlushDone       := RegNext(io.l2_flush_done)
     x.externalInterrupt.msip  := outer.clint_int_sink.in.head._1(0)
     x.externalInterrupt.mtip  := outer.clint_int_sink.in.head._1(1)
     x.externalInterrupt.meip  := outer.plic_int_sink.in.head._1(0)
@@ -1888,6 +1894,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   io.inner_hartId := io.hartId
   io.inner_reset_vector := RegNext(io.outer_reset_vector)
   io.outer_cpu_halt := io.ooo_to_mem.backendToTopBypass.cpuHalted
+  io.outer_l2_flush_en := io.ooo_to_mem.csrCtrl.flush_l2_enable
+  io.outer_power_down_en := io.ooo_to_mem.csrCtrl.power_down_enable
   io.outer_cpu_critical_error := io.ooo_to_mem.backendToTopBypass.cpuCriticalError
   io.outer_beu_errors_icache := RegNext(io.inner_beu_errors_icache)
   io.outer_l2_pf_enable := io.inner_l2_pf_enable
@@ -2044,4 +2052,4 @@ class MemBlockImp(wrapper: MemBlock) extends LazyModuleImp(wrapper) {
   if (p(DebugOptionsKey).ResetGen) {
     ResetGen(ResetGenNode(Seq(ModuleNode(wrapper.inner.module))), reset, sim = false)
   }
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
index c5180b0e5b3..67e923d10d9 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
@@ -197,6 +197,8 @@ object CSRBundles {
     val uncache_write_outstanding_enable = Output(Bool())
     val hd_misalign_st_enable = Output(Bool())
     val hd_misalign_ld_enable = Output(Bool())
+    val power_down_enable = Output(Bool())
+    val flush_l2_enable = Output(Bool())
     // Rename
     val fusion_enable = Output(Bool())
     val wfi_enable = Output(Bool())
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
index 63f63239f75..5b29b766236 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
@@ -4,7 +4,10 @@ import chisel3._
 import chisel3.util._
 import freechips.rocketchip.util._
 import org.chipsalliance.cde.config.Parameters
-import xiangshan.backend.fu.NewCSR.CSRDefines.{CSRRWField => RW}
+import xiangshan.backend.fu.NewCSR.CSRDefines.{
+  CSRRWField => RW,
+  CSRROField => RO,
+}
 import xiangshan.HasXSParameter
 
 import scala.collection.immutable.SeqMap
@@ -31,12 +34,28 @@ trait CSRCustom { self: NewCSR =>
   val srnctl = Module(new CSRModule("Srnctl", new SrnctlBundle))
     .setAddr(0x5C4)
 
+  // Machine Level Custom Read/Write
+
+  // mcorepwr: Core Power Down Status Enable
+  val mcorepwr = Module(new CSRModule("Mcorepwr", new McorepwrBundle))
+    .setAddr(0xBC0)
+
+  // mflushpwr: Flush L2 Cache Enable
+  val mflushpwr = Module(new CSRModule("Mflushpwr", new MflushpwrBundle)
+    with HasMachineFlushL2Bundle
+  {
+    regOut.L2_FLUSH_DONE := l2FlushDone
+  })
+    .setAddr(0xBC1)
+
   val customCSRMods = Seq(
     sbpctl,
     spfctl,
     slvpredctl,
     smblockctl,
     srnctl,
+    mcorepwr,
+    mflushpwr,
   )
 
   val customCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_ <: CSRBundle], UInt)] = SeqMap.from(
@@ -95,6 +114,15 @@ class SrnctlBundle extends CSRBundle {
   val FUSION_ENABLE  = RW(0).withReset(true.B)
 }
 
+class McorepwrBundle extends CSRBundle {
+  val POWER_DOWN_ENABLE = RW(0).withReset(false.B)
+}
+
+class MflushpwrBundle extends CSRBundle {
+  val FLUSH_L2_ENABLE = RW(0).withReset(false.B)
+  val L2_FLUSH_DONE   = RO(1).withReset(false.B)
+}
+
 object SbufferThreshold extends CSREnum with RWApply {
   val initValue = Value(7.U)
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index bf7c716d958..f664a6447eb 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -763,4 +763,8 @@ trait HasPerfEventBundle { self: CSRModule[_] =>
 
 trait HasLocalInterruptReqBundle { self: CSRModule[_] =>
   val lcofiReq = IO(Input(Bool()))
-}
\ No newline at end of file
+}
+
+trait HasMachineFlushL2Bundle { self: CSRModule[_] =>
+  val l2FlushDone = IO(Input(Bool()))
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index fd209a6bec7..832038603ea 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -126,6 +126,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     val fromTop = Input(new Bundle {
       val hartId = UInt(hartIdLen.W)
       val clintTime = Input(ValidIO(UInt(64.W)))
+      val l2FlushDone = Input(Bool())
       val criticalErrorState = Input(Bool())
     })
     val in = Flipped(DecoupledIO(new NewCSRInput))
@@ -729,6 +730,11 @@ class NewCSR(implicit val p: Parameters) extends Module
         m.nmip := nmip.asUInt.orR
       case _ =>
     }
+    mod match {
+      case m: HasMachineFlushL2Bundle =>
+        m.l2FlushDone := io.fromTop.l2FlushDone
+      case _ =>
+    }
   }
 
   csrMods.foreach { mod =>
@@ -1254,6 +1260,10 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.status.custom.fusion_enable           := srnctl.regOut.FUSION_ENABLE.asBool
   io.status.custom.wfi_enable              := srnctl.regOut.WFI_ENABLE.asBool && (!io.status.singleStepFlag) && !debugMode
 
+  io.status.custom.power_down_enable := mcorepwr.regOut.POWER_DOWN_ENABLE.asBool
+
+  io.status.custom.flush_l2_enable := mflushpwr.regOut.FLUSH_L2_ENABLE.asBool
+
   io.status.instrAddrTransType.bare := privState.isModeM ||
     (!privState.isVirtual && satp.regOut.MODE === SatpMode.Bare) ||
     (privState.isVirtual && vsatp.regOut.MODE === SatpMode.Bare && hgatp.regOut.MODE === HgatpMode.Bare)
@@ -1548,6 +1558,11 @@ class NewCSR(implicit val p: Parameters) extends Module
     diffAIAXtopeiEvent.mtopei := mtopei.rdata
     diffAIAXtopeiEvent.stopei := stopei.rdata
     diffAIAXtopeiEvent.vstopei := vstopei.rdata
+
+    val diffCustomMflushpwr = DifftestModule(new DiffSyncCustomMflushpwrEvent)
+    diffCustomMflushpwr.coreid := hartId
+    diffCustomMflushpwr.valid := RegNext(io.fromTop.l2FlushDone) =/= io.fromTop.l2FlushDone
+    diffCustomMflushpwr.l2FlushDone := io.fromTop.l2FlushDone
   }
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 465edd33bfc..a3a15729f99 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -176,6 +176,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   csrMod.io.fromTop.hartId := io.csrin.get.hartId
   csrMod.io.fromTop.clintTime := io.csrin.get.clintTime
+  csrMod.io.fromTop.l2FlushDone := io.csrin.get.l2FlushDone
   csrMod.io.fromTop.criticalErrorState := io.csrin.get.criticalErrorState
   private val csrModOutValid = csrMod.io.out.valid
   private val csrModOut      = csrMod.io.out.bits
@@ -349,6 +350,8 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       custom.uncache_write_outstanding_enable := csrMod.io.status.custom.uncache_write_outstanding_enable
       custom.hd_misalign_st_enable            := csrMod.io.status.custom.hd_misalign_st_enable
       custom.hd_misalign_ld_enable            := csrMod.io.status.custom.hd_misalign_ld_enable
+      custom.power_down_enable                := csrMod.io.status.custom.power_down_enable
+      custom.flush_l2_enable                  := csrMod.io.status.custom.flush_l2_enable
       // Rename
       custom.fusion_enable            := csrMod.io.status.custom.fusion_enable
       custom.wfi_enable               := csrMod.io.status.custom.wfi_enable
@@ -379,6 +382,7 @@ class CSRInput(implicit p: Parameters) extends XSBundle with HasSoCParameter{
   val msiInfo = Input(ValidIO(new MsiInfoBundle))
   val criticalErrorState = Input(Bool())
   val clintTime = Input(ValidIO(UInt(64.W)))
+  val l2FlushDone = Input(Bool())
   val trapInstInfo = Input(ValidIO(new TrapInstInfo))
   val fromVecExcpMod = Input(new Bundle {
     val busy = Bool()
```
