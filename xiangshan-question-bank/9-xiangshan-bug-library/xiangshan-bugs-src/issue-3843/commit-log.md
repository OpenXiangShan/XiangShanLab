# Commit Log
- Issue: #3843
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3843
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3843
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3843
- Changed files: 24
- Additions: 521
- Deletions: 64

## Files
- `src/main/scala/system/SoC.scala`
- `src/main/scala/top/Top.scala`
- `src/main/scala/top/XSNoCTop.scala`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/XSTileWrap.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/backend/fu/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/rename/CompressUnit.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`
- `src/main/scala/xiangshan/backend/trace/Interface.scala`
- `src/main/scala/xiangshan/backend/trace/Trace.scala`
- `src/main/scala/xiangshan/backend/trace/TraceBuffer.scala`
- `src/test/scala/top/SimTop.scala`

## Diff
```diff
diff --git a/src/main/scala/system/SoC.scala b/src/main/scala/system/SoC.scala
index 003e5e7925f..729c6dea553 100644
--- a/src/main/scala/system/SoC.scala
+++ b/src/main/scala/system/SoC.scala
@@ -98,6 +98,16 @@ trait HasSoCParameter {
   val NumCores = tiles.size
   val EnableILA = soc.EnableILA
 
+  // Parameters for trace extension
+  val TraceTraceGroupNum          = tiles.head.traceParams.TraceGroupNum
+  val TraceCauseWidth             = tiles.head.XLEN
+  val TraceTvalWidth              = tiles.head.traceParams.IaddrWidth
+  val TracePrivWidth              = tiles.head.traceParams.PrivWidth
+  val TraceIaddrWidth             = tiles.head.traceParams.IaddrWidth
+  val TraceItypeWidth             = tiles.head.traceParams.ItypeWidth
+  val TraceIretireWidthCompressed = log2Up(tiles.head.RenameWidth * tiles.head.CommitWidth * 2)
+  val TraceIlastsizeWidth         = tiles.head.traceParams.IlastsizeWidth
+
   // L3 configurations
   val L3InnerBusWidth = soc.L3InnerBusWidth
   val L3BlockSize = soc.L3BlockSize
diff --git a/src/main/scala/top/Top.scala b/src/main/scala/top/Top.scala
index 32637ac49be..56d025db8c2 100644
--- a/src/main/scala/top/Top.scala
+++ b/src/main/scala/top/Top.scala
@@ -258,6 +258,21 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc() with HasSoCParameter
       val riscv_halt = Output(Vec(NumCores, Bool()))
       val riscv_critical_error = Output(Vec(NumCores, Bool()))
       val riscv_rst_vec = Input(Vec(NumCores, UInt(soc.PAddrBits.W)))
+      val traceCoreInterface = Vec(NumCores, new Bundle {
+        val fromEncoder = Input(new Bundle {
+          val enable = Bool()
+          val stall  = Bool()
+        })
+        val toEncoder   = Output(new Bundle {
+          val cause     = UInt(TraceCauseWidth.W)
+          val tval      = UInt(TraceTvalWidth.W)
+          val priv      = UInt(TracePrivWidth.W)
+          val iaddr     = UInt((TraceTraceGroupNum * TraceIaddrWidth).W)
+          val itype     = UInt((TraceTraceGroupNum * TraceItypeWidth).W)
+          val iretire   = UInt((TraceTraceGroupNum * TraceIretireWidthCompressed).W)
+          val ilastsize = UInt((TraceTraceGroupNum * TraceIlastsizeWidth).W)
+        })
+      })
     })
 
     val reset_sync = withClockAndReset(io.clock.asClock, io.reset) { ResetGen() }
@@ -296,6 +311,17 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc() with HasSoCParameter
       core.module.io.clintTime := misc.module.clintTime
       io.riscv_halt(i) := core.module.io.cpu_halt
       io.riscv_critical_error(i) := core.module.io.cpu_crtical_error
+      // trace Interface
+      val traceInterface = core.module.io.traceCoreInterface
+      traceInterface.fromEncoder := io.traceCoreInterface(i).fromEncoder
+      io.traceCoreInterface(i).toEncoder.priv := traceInterface.toEncoder.priv
+      io.traceCoreInterface(i).toEncoder.cause := traceInterface.toEncoder.trap.cause
+      io.traceCoreInterface(i).toEncoder.tval := traceInterface.toEncoder.trap.tval
+      io.traceCoreInterface(i).toEncoder.iaddr := VecInit(traceInterface.toEncoder.groups.map(_.bits.iaddr)).asUInt
+      io.traceCoreInterface(i).toEncoder.itype := VecInit(traceInterface.toEncoder.groups.map(_.bits.itype)).asUInt
+      io.traceCoreInterface(i).toEncoder.iretire := VecInit(traceInterface.toEncoder.groups.map(_.bits.iretire)).asUInt
+      io.traceCoreInterface(i).toEncoder.ilastsize := VecInit(traceInterface.toEncoder.groups.map(_.bits.ilastsize)).asUInt
+
       core.module.io.reset_vector := io.riscv_rst_vec(i)
     }
 
diff --git a/src/main/scala/top/XSNoCTop.scala b/src/main/scala/top/XSNoCTop.scala
index 0eac365e1c6..34189ced588 100644
--- a/src/main/scala/top/XSNoCTop.scala
+++ b/src/main/scala/top/XSNoCTop.scala
@@ -116,6 +116,21 @@ class XSNoCTop()(implicit p: Parameters) extends BaseXSSoc with HasSoCParameter
       val chi = new PortIO
       val nodeID = Input(UInt(soc.NodeIDWidthList(issue).W))
       val clintTime = Input(ValidIO(UInt(64.W)))
+      val traceCoreInterface = new Bundle {
+        val fromEncoder = Input(new Bundle {
+          val enable = Bool()
+          val stall  = Bool()
+        })
+        val toEncoder   = Output(new Bundle {
+          val cause     = UInt(TraceCauseWidth.W)
+          val tval      = UInt(TraceTvalWidth.W)
+          val priv      = UInt(TracePrivWidth.W)
+          val iaddr     = UInt((TraceTraceGroupNum * TraceIaddrWidth).W)
+          val itype     = UInt((TraceTraceGroupNum * TraceItypeWidth).W)
+          val iretire   = UInt((TraceTraceGroupNum * TraceIretireWidthCompressed).W)
+          val ilastsize = UInt((TraceTraceGroupNum * TraceIlastsizeWidth).W)
+        })
+      }
     })
     // imsic axi4lite io
     val imsic_axi4lite = wrapper.u_imsic_bus_top.module.axi4lite.map(x => IO(chiselTypeOf(x)))
@@ -150,6 +165,16 @@ class XSNoCTop()(implicit p: Parameters) extends BaseXSSoc with HasSoCParameter
     io.riscv_critical_error := core_with_l2.module.io.cpu_crtical_error
     io.hartIsInReset := core_with_l2.module.io.hartIsInReset
     core_with_l2.module.io.reset_vector := io.riscv_rst_vec
+    // trace Interface
+    val traceInterface = core_with_l2.module.io.traceCoreInterface
+    traceInterface.fromEncoder := io.traceCoreInterface.fromEncoder
+    io.traceCoreInterface.toEncoder.priv := traceInterface.toEncoder.priv
+    io.traceCoreInterface.toEncoder.cause := traceInterface.toEncoder.trap.cause
+    io.traceCoreInterface.toEncoder.tval := traceInterface.toEncoder.trap.tval
+    io.traceCoreInterface.toEncoder.iaddr := VecInit(traceInterface.toEncoder.groups.map(_.bits.iaddr)).asUInt
+    io.traceCoreInterface.toEncoder.itype := VecInit(traceInterface.toEncoder.groups.map(_.bits.itype)).asUInt
+    io.traceCoreInterface.toEncoder.iretire := VecInit(traceInterface.toEncoder.groups.map(_.bits.iretire)).asUInt
+    io.traceCoreInterface.toEncoder.ilastsize := VecInit(traceInterface.toEncoder.groups.map(_.bits.ilastsize)).asUInt
 
     EnableClintAsyncBridge match {
       case Some(param) =>
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 727d1dccefa..a5875e8494e 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -46,6 +46,7 @@ import xiangshan.frontend.AllFoldedHistories
 import xiangshan.frontend.AllAheadFoldedHistoryOldestBits
 import xiangshan.frontend.RASPtr
 import xiangshan.backend.rob.RobBundles.RobCommitEntryBundle
+import xiangshan.backend.trace._
 
 class ValidUndirectioned[T <: Data](gen: T) extends Bundle {
   val valid = Bool()
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index a93e98e32eb..b42821f6414 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -33,6 +33,7 @@ import top.BusPerfMonitor
 import utility._
 import xiangshan.cache.mmu.TlbRequestIO
 import xiangshan.backend.fu.PMPRespBundle
+import xiangshan.backend.trace.{Itype, TraceCoreInterface}
 
 class L1BusErrorUnitInfo(implicit val p: Parameters) extends Bundle with HasSoCParameter {
   val ecc_error = Valid(UInt(soc.PAddrBits.W))
@@ -161,6 +162,10 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
         val resetInFrontend = Input(Bool())
         val toTile = Output(Bool())
       }
+      val traceCoreInterface = new Bundle{
+        val fromCore = Flipped(new TraceCoreInterface)
+        val toTile   = new TraceCoreInterface
+      }
       val debugTopDown = new Bundle() {
         val robTrueCommit = Input(UInt(64.W))
         val robHeadPaddr = Flipped(Valid(UInt(36.W)))
@@ -184,6 +189,32 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
     io.hartId.toCore := io.hartId.fromTile
     io.cpu_halt.toTile := io.cpu_halt.fromCore
     io.cpu_critical_error.toTile := io.cpu_critical_error.fromCore
+    // trace interface
+    val traceToTile = io.traceCoreInterface.toTile
+    val traceFromCore = io.traceCoreInterface.fromCore
+    traceFromCore.fromEncoder := RegNext(traceToTile.fromEncoder)
+    traceToTile.toEncoder.trap := RegEnable(
+      traceFromCore.toEncoder.trap,
+      traceFromCore.toEncoder.groups(0).valid && Itype.isTrap(traceFromCore.toEncoder.groups(0).bits.itype)
+    )
+    traceToTile.toEncoder.priv := RegEnable(
+      traceFromCore.toEncoder.priv,
+      traceFromCore.toEncoder.groups(0).valid
+    )
+    (0 until TraceGroupNum).foreach{ i =>
+      traceToTile.toEncoder.groups(i).valid := RegNext(traceFromCore.toEncoder.groups(i).valid)
+      traceToTile.toEncoder.groups(i).bits.iretire := RegNext(traceFromCore.toEncoder.groups(i).bits.iretire)
+      traceToTile.toEncoder.groups(i).bits.itype := RegNext(traceFromCore.toEncoder.groups(i).bits.itype)
+      traceToTile.toEncoder.groups(i).bits.ilastsize := RegEnable(
+        traceFromCore.toEncoder.groups(i).bits.ilastsize,
+        traceFromCore.toEncoder.groups(i).valid
+      )
+      traceToTile.toEncoder.groups(i).bits.iaddr := RegEnable(
+        traceFromCore.toEncoder.groups(i).bits.iaddr,
+        traceFromCore.toEncoder.groups(i).valid
+      )
+    }
+
     dontTouch(io.hartId)
     dontTouch(io.cpu_halt)
     dontTouch(io.cpu_critical_error)
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 0b8ca035e46..435b7d050c7 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -29,6 +29,7 @@ import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.issue.{IntScheduler, IssueBlockParams, MemScheduler, SchdBlockParams, SchedulerType, VfScheduler, FpScheduler}
 import xiangshan.backend.regfile._
 import xiangshan.backend.BackendParams
+import xiangshan.backend.trace._
 import xiangshan.cache.DCacheParameters
 import xiangshan.cache.prefetch._
 import xiangshan.frontend.{BasePredictor, BranchPredictionResp, FTB, FakePredictor, RAS, Tage, ITTage, Tage_SC, FauFTB}
@@ -561,7 +562,13 @@ case class XSCoreParameters
 
   // Parameters for trace extension.
   // Trace parameters is useful for XSTOP.
-  val TraceGroupNum          = 3 // Width to Encoder
+  val traceParams: TraceParams = new TraceParams(
+    TraceGroupNum  = 3,
+    IaddrWidth     = GPAddrBitsSv48x4,
+    PrivWidth      = 3,
+    ItypeWidth     = 4,
+    IlastsizeWidth = 1,
+  )
 }
 
 case object DebugOptionsKey extends Field[DebugOptions]
@@ -907,5 +914,13 @@ trait HasXSParameter {
   protected def TriggerChainMaxLength = 2
 
   // Parameters for Trace extension
-  def TraceGroupNum          = coreParams.TraceGroupNum
+  def TraceGroupNum          = coreParams.traceParams.TraceGroupNum
+  def CauseWidth             = XLEN
+  def TvalWidth              = coreParams.traceParams.IaddrWidth
+  def PrivWidth              = coreParams.traceParams.PrivWidth
+  def IaddrWidth             = coreParams.traceParams.IaddrWidth
+  def ItypeWidth             = coreParams.traceParams.ItypeWidth
+  def IretireWidthInPipe     = log2Up(RenameWidth * 2)
+  def IretireWidthCompressed = log2Up(RenameWidth * CommitWidth * 2)
+  def IlastsizeWidth         = coreParams.traceParams.IlastsizeWidth
 }
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 5d99e3598bc..11196085d05 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -28,6 +28,7 @@ import utils._
 import utility._
 import xiangshan.backend._
 import xiangshan.backend.fu.PMPRespBundle
+import xiangshan.backend.trace.TraceCoreInterface
 import xiangshan.cache.mmu._
 import xiangshan.frontend._
 import xiangshan.mem.L1PrefetchFuzzer
@@ -84,6 +85,7 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
     val cpu_halt = Output(Bool())
     val cpu_critical_error = Output(Bool())
     val resetInFrontend = Output(Bool())
+    val traceCoreInterface = new TraceCoreInterface
     val l2_pf_enable = Output(Bool())
     val perfEvents = Input(Vec(numPCntHc * coreParams.L2NBanks + 1, new PerfEvent))
     val beu_errors = Output(new XSL1BusErrors())
@@ -247,6 +249,9 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
 
   memBlock.io.resetInFrontendBypass.fromFrontend := frontend.io.resetInFrontend
   io.resetInFrontend := memBlock.io.resetInFrontendBypass.toL2Top
+  memBlock.io.traceCoreInterfaceBypass.fromBackend <> backend.io.traceCoreInterface
+  io.traceCoreInterface <> memBlock.io.traceCoreInterfaceBypass.toL2Top
+
 
   if (debugOpts.ResetGen) {
     backend.reset := memBlock.io.reset_backend
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index 2b198db80ec..daa996275df 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -30,6 +30,7 @@ import top.{BusPerfMonitor, ArgParser, Generator}
 import utility.{DelayN, ResetGen, TLClientsMerger, TLEdgeBuffer, TLLogger, Constantin, ChiselDB, FileRegisters}
 import coupledL2.EnableCHI
 import coupledL2.tl2chi.PortIO
+import xiangshan.backend.trace.TraceCoreInterface
 
 class XSTile()(implicit p: Parameters) extends LazyModule
   with HasXSParameter
@@ -101,6 +102,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
       val cpu_halt = Output(Bool())
       val cpu_crtical_error = Output(Bool())
       val hartIsInReset = Output(Bool())
+      val traceCoreInterface = new TraceCoreInterface
       val debugTopDown = new Bundle {
         val robHeadPaddr = Valid(UInt(PAddrBits.W))
         val l3MissMatch = Input(Bool())
@@ -129,6 +131,8 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
     l2top.module.io.hartIsInReset.resetInFrontend := core.module.io.resetInFrontend
     io.hartIsInReset := l2top.module.io.hartIsInReset.toTile
+    l2top.module.io.traceCoreInterface.fromCore <> core.module.io.traceCoreInterface
+    io.traceCoreInterface <> l2top.module.io.traceCoreInterface.toTile
 
     l2top.module.io.beu_errors.icache <> core.module.io.beu_errors.icache
     l2top.module.io.beu_errors.dcache <> core.module.io.beu_errors.dcache
diff --git a/src/main/scala/xiangshan/XSTileWrap.scala b/src/main/scala/xiangshan/XSTileWrap.scala
index 1a2477279c6..3ce65b1ad9a 100644
--- a/src/main/scala/xiangshan/XSTileWrap.scala
+++ b/src/main/scala/xiangshan/XSTileWrap.scala
@@ -26,6 +26,7 @@ import system.HasSoCParameter
 import device.{IMSICAsync, MsiInfoBundle}
 import coupledL2.tl2chi.{PortIO, AsyncPortIO, CHIAsyncBridgeSource}
 import utility.{IntBuffer, ResetGen}
+import xiangshan.backend.trace.TraceCoreInterface
 
 // This module is used for XSNoCTop for async time domain and divide different
 // voltage domain. Everything in this module should be in the core clock domain
@@ -61,6 +62,7 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
       val cpu_halt = Output(Bool())
       val cpu_crtical_error = Output(Bool())
       val hartIsInReset = Output(Bool())
+      val traceCoreInterface = new TraceCoreInterface
       val debugTopDown = new Bundle {
         val robHeadPaddr = Valid(UInt(PAddrBits.W))
         val l3MissMatch = Input(Bool())
@@ -93,6 +95,7 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
     io.cpu_halt := tile.module.io.cpu_halt
     io.cpu_crtical_error := tile.module.io.cpu_crtical_error
     io.hartIsInReset := tile.module.io.hartIsInReset
+    io.traceCoreInterface <> tile.module.io.traceCoreInterface
     io.debugTopDown <> tile.module.io.debugTopDown
     tile.module.io.nodeID.foreach(_ := io.nodeID.get)
 
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 483b4c42774..2d3dd044a05 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -46,6 +46,7 @@ import xiangshan.backend.fu.{FenceIO, FenceToSbuffer, FuConfig, FuType, PFEvent,
 import xiangshan.backend.issue.EntryBundles._
 import xiangshan.backend.issue.{CancelNetwork, Scheduler, SchedulerArithImp, SchedulerImpBase, SchedulerMemImp}
 import xiangshan.backend.rob.{RobCoreTopDownIO, RobDebugRollingIO, RobLsqIO, RobPtr}
+import xiangshan.backend.trace.TraceCoreInterface
 import xiangshan.frontend.{FtqPtr, FtqRead, PreDecodeInfo}
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
 
@@ -246,6 +247,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   ctrlBlock.io.fromTop.hartId := io.fromTop.hartId
   ctrlBlock.io.frontend <> io.frontend
   ctrlBlock.io.fromCSR.toDecode := intExuBlock.io.csrToDecode.get
+  ctrlBlock.io.fromCSR.traceCSR := intExuBlock.io.csrio.get.traceCSR
   ctrlBlock.io.fromWB.wbData <> wbDataPath.io.toCtrlBlock.writeback
   ctrlBlock.io.fromMem.stIn <> io.mem.stIn
   ctrlBlock.io.fromMem.violation <> io.mem.memoryViolation
@@ -752,6 +754,8 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
 
   io.toTop.cpuHalted := ctrlBlock.io.toTop.cpuHalt
 
+  io.traceCoreInterface <> ctrlBlock.io.traceCoreInterface
+
   io.debugTopDown.fromRob := ctrlBlock.io.debugTopDown.fromRob
   ctrlBlock.io.debugTopDown.fromCore := io.debugTopDown.fromCore
 
@@ -947,6 +951,8 @@ class BackendIO(implicit p: Parameters, params: BackendParams) extends XSBundle
 
   val toTop = new BackendToTopBundle
 
+  val traceCoreInterface = new TraceCoreInterface
+
   val fenceio = new FenceIO
   // Todo: merge these bundles into BackendFrontendIO
   val frontend = Flipped(new FrontendToCtrlIO)
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index cb05f983236..90cf37b118e 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -220,7 +220,7 @@ object Bundles {
     val instrSize       = UInt(log2Ceil(RenameWidth + 1).W)
     val dirtyFs         = Bool()
     val dirtyVs         = Bool()
-    val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))
+    val traceBlockInPipe = new TracePipe(IretireWidthInPipe)
 
     val eliminatedMove  = Bool()
     // Take snapshot at this CFI inst
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 1c8202315e3..5afe7e1d813 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -37,6 +37,7 @@ import xiangshan.backend.rob.{Rob, RobCSRIO, RobCoreTopDownIO, RobDebugRollingIO
 import xiangshan.frontend.{FtqPtr, FtqRead, Ftq_RF_Components}
 import xiangshan.mem.{LqPtr, LsqEnqIO}
 import xiangshan.backend.issue.{FpScheduler, IntScheduler, MemScheduler, VfScheduler}
+import xiangshan.backend.trace._
 
 class CtrlToFtqIO(implicit p: Parameters) extends XSBundle {
   val rob_commits = Vec(CommitWidth, Valid(new RobCommitInfo))
@@ -72,7 +73,8 @@ class CtrlBlockImp(
     "robFlush"  -> 1,
     "load"      -> params.LduCnt,
     "hybrid"    -> params.HyuCnt,
-    "store"     -> (if(EnableStorePrefetchSMS) params.StaCnt else 0)
+    "store"     -> (if(EnableStorePrefetchSMS) params.StaCnt else 0),
+    "trace"     -> TraceGroupNum
   ))
 
   private val numPcMemReadForExu = params.numPcReadPort
@@ -239,6 +241,42 @@ class CtrlBlockImp(
     io.memStPcRead.foreach(_.data := 0.U)
   }
 
+  /**
+   * trace begin
+   */
+  val trace = Module(new Trace)
+  trace.io.in.fromEncoder.stall  := io.traceCoreInterface.fromEncoder.stall
+  trace.io.in.fromEncoder.enable := io.traceCoreInterface.fromEncoder.enable
+  trace.io.in.fromRob            := rob.io.trace.traceCommitInfo
+  rob.io.trace.blockCommit       := trace.io.out.blockRobCommit
+  
+  for ((pcMemIdx, i) <- pcMemRdIndexes("trace").zipWithIndex) {
+    val traceValid = trace.toPcMem.blocks(i).valid
+    pcMem.io.ren.get(pcMemIdx) := traceValid
+    pcMem.io.raddr(pcMemIdx) := trace.toPcMem.blocks(i).bits.ftqIdx.get.value
+    trace.io.in.fromPcMem(i) := pcMem.io.rdata(pcMemIdx).getPc(RegEnable(trace.toPcMem.blocks(i).bits.ftqOffset.get, traceValid))
+  }
+
+  // Trap/Xret only occur in block(0).
+  val tracePriv = Mux(Itype.isTrapOrXret(trace.toEncoder.blocks(0).bits.tracePipe.itype),
+    io.fromCSR.traceCSR.lastPriv,
+    io.fromCSR.traceCSR.currentPriv
+  )
+  io.traceCoreInterface.toEncoder.trap.cause := io.fromCSR.traceCSR.cause.asUInt
+  io.traceCoreInterface.toEncoder.trap.tval  := io.fromCSR.traceCSR.tval.asUInt
+  io.traceCoreInterface.toEncoder.priv       := tracePriv
+  (0 until TraceGroupNum).foreach(i => {
+    io.traceCoreInterface.toEncoder.groups(i).valid := trace.io.out.toEncoder.blocks(i).valid
+    io.traceCoreInterface.toEncoder.groups(i).bits.iaddr := trace.io.out.toEncoder.blocks(i).bits.iaddr.getOrElse(0.U)
+    io.traceCoreInterface.toEncoder.groups(i).bits.itype := trace.io.out.toEncoder.blocks(i).bits.tracePipe.itype
+    io.traceCoreInterface.toEncoder.groups(i).bits.iretire := trace.io.out.toEncoder.blocks(i).bits.tracePipe.iretire
+    io.traceCoreInterface.toEncoder.groups(i).bits.ilastsize := trace.io.out.toEncoder.blocks(i).bits.tracePipe.ilastsize
+  })
+  /**
+   * trace end
+   */
+
+
   redirectGen.io.hartId := io.fromTop.hartId
   redirectGen.io.oldestExuRedirect.valid := GatedValidRegNext(oldestExuRedirect.valid)
   redirectGen.io.oldestExuRedirect.bits := RegEnable(oldestExuRedirect.bits, oldestExuRedirect.valid)
@@ -686,6 +724,7 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
   val frontend = Flipped(new FrontendToCtrlIO())
   val fromCSR = new Bundle{
     val toDecode = Input(new CSRToDecode)
+    val traceCSR = Input(new TraceCSR)
   }
   val toIssueBlock = new Bundle {
     val flush = ValidIO(new Redirect)
@@ -754,6 +793,8 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
     val ratOldPest = new RatToVecExcpMod
   })
 
+  val traceCoreInterface = new TraceCoreInterface
+
   val perfInfo = Output(new Bundle{
     val ctrlInfo = new Bundle {
       val robFull   = Bool()
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index 74656132247..86b109fcd4e 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -46,6 +46,7 @@ import xiangshan.backend.datapath.NewPipelineConnect
 import system.SoCParamsKey
 import xiangshan.backend.fu.NewCSR.TriggerUtil
 import xiangshan.ExceptionNO._
+import xiangshan.backend.trace.{Itype, TraceCoreInterface}
 
 trait HasMemBlockParameters extends HasXSParameter {
   // number of memory units
@@ -327,6 +328,10 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
       val fromFrontend = Input(Bool())
       val toL2Top      = Output(Bool())
     }
+    val traceCoreInterfaceBypass = new Bundle{
+      val fromBackend = Flipped(new TraceCoreInterface)
+      val toL2Top     = new TraceCoreInterface
+    }
   })
 
   dontTouch(io.inner_hartId)
@@ -1881,6 +1886,32 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     io.reset_backend := DontCare
   }
   io.resetInFrontendBypass.toL2Top := io.resetInFrontendBypass.fromFrontend
+  // trace interface
+  val traceToL2Top = io.traceCoreInterfaceBypass.toL2Top
+  val traceFromBackend = io.traceCoreInterfaceBypass.fromBackend
+  traceFromBackend.fromEncoder := RegNext(traceToL2Top.fromEncoder)
+  traceToL2Top.toEncoder.trap  := RegEnable(
+    traceFromBackend.toEncoder.trap,
+    traceFromBackend.toEncoder.groups(0).valid && Itype.isTrap(traceFromBackend.toEncoder.groups(0).bits.itype)
+  )
+  traceToL2Top.toEncoder.priv := RegEnable(
+    traceFromBackend.toEncoder.priv,
+    traceFromBackend.toEncoder.groups(0).valid
+  )
+  (0 until TraceGroupNum).foreach{ i =>
+    traceToL2Top.toEncoder.groups(i).valid := RegNext(traceFromBackend.toEncoder.groups(i).valid)
+    traceToL2Top.toEncoder.groups(i).bits.iretire := RegNext(traceFromBackend.toEncoder.groups(i).bits.iretire)
+    traceToL2Top.toEncoder.groups(i).bits.itype := RegNext(traceFromBackend.toEncoder.groups(i).bits.itype)
+    traceToL2Top.toEncoder.groups(i).bits.ilastsize := RegEnable(
+      traceFromBackend.toEncoder.groups(i).bits.ilastsize,
+      traceFromBackend.toEncoder.groups(i).valid
+    )
+    traceToL2Top.toEncoder.groups(i).bits.iaddr := RegEnable(
+      traceFromBackend.toEncoder.groups(i).bits.iaddr,
+      traceFromBackend.toEncoder.groups(i).valid
+    )
+  }
+
 
   io.mem_to_ooo.storeDebugInfo := DontCare
   // store event difftest information
diff --git a/src/main/scala/xiangshan/backend/fu/CSR.scala b/src/main/scala/xiangshan/backend/fu/CSR.scala
index 4554cbddee9..ac07c2755d9 100644
--- a/src/main/scala/xiangshan/backend/fu/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/CSR.scala
@@ -33,6 +33,7 @@ import xiangshan.backend.fu.NewCSR.CSREvents.TargetPCBundle
 import xiangshan.backend.fu.NewCSR.CSRNamedConstant.ContextStatus
 import xiangshan.backend.rob.RobPtr
 import utils.MathUtils.{BigIntGenMask, BigIntNot}
+import xiangshan.backend.trace._
 
 class FpuCsrIO extends Bundle {
   val fflags = Output(Valid(UInt(5.W)))
@@ -99,6 +100,8 @@ class CSRFileIO(implicit p: Parameters) extends XSBundle {
   val trapTarget = Output(new TargetPCBundle)
   val interrupt = Output(Bool())
   val wfi_event = Output(Bool())
+  //trace
+  val traceCSR = Output(new TraceCSR)
   // from LSQ
   val memExceptionVAddr = Input(UInt(XLEN.W))
   val memExceptionGPAddr = Input(UInt(XLEN.W))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 1abd03599ce..27cf5a67bee 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -20,6 +20,7 @@ import xiangshan.backend.rob.RobPtr
 import xiangshan._
 import xiangshan.backend.fu.PerfCounterIO
 import xiangshan.ExceptionNO._
+import xiangshan.backend.trace._
 
 import scala.collection.immutable.SeqMap
 
@@ -189,6 +190,8 @@ class NewCSR(implicit val p: Parameters) extends Module
       val memTrigger = new MemTdataDistributeIO()
       // Instruction fetch address translation type
       val instrAddrTransType = new AddrTransType
+      // trace
+      val traceCSR = Output(new TraceCSR)
       // custom
       val custom = new CSRCustomState
       val criticalErrorState = Bool()
@@ -1122,6 +1125,29 @@ class NewCSR(implicit val p: Parameters) extends Module
    * debug_end
    */
 
+  // trace
+  val privForTrace = Mux(debugMode,
+    Priv.D,
+    Mux1H(
+      Seq(privState.isModeM, privState.isModeHS, privState.isModeVS, privState.isModeHU, privState.isModeVU),
+      Seq(Priv.M,            Priv.HS,            Priv.VS,            Priv.HU,            Priv.VU)
+    )
+  )
+  val xret = legalDret || legalMNret || legalMret || legalSret
+  val currentPriv = privForTrace
+  val lastPriv = RegEnable(privForTrace, Priv.M, (xret || io.fromRob.trap.valid))
+
+  io.status.traceCSR.lastPriv       := lastPriv
+  io.status.traceCSR.currentPriv    := privForTrace
+  io.status.traceCSR.cause := Mux1H(
+    Seq(privState.isModeM, privState.isModeHS, privState.isModeVS),
+    Seq(mcause.rdata,      scause.rdata,       vscause.rdata)
+  )
+  io.status.traceCSR.tval  := Mux1H(
+    Seq(privState.isModeM, privState.isModeHS, privState.isModeVS),
+    Seq(mtval.rdata,       stval.rdata,        vstval.rdata)
+  )
+  
   /**
    * perf_begin
    * perf number: 29 (frontend 8, ctrlblock 8, memblock 8, huancun 5)
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 93bd5c8edd8..790e2758024 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -228,13 +228,6 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   val isXRet = valid && func === CSROpType.jmp && !isEcall && !isEbreak
 
-  // ctrl block will use theses later for flush // Todo: optimize isXRetFlag's DelayN
-  val isXRetFlag = RegInit(false.B)
-  isXRetFlag := Mux1H(Seq(
-    DelayN(flush, 5) -> false.B,
-    isXRet -> true.B,
-  ))
-
   flushPipe := csrMod.io.out.bits.flushPipe
 
   // tlb
@@ -306,7 +299,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   csrOut.vpu.vstart := csrMod.io.status.vecState.vstart.asUInt
   csrOut.vpu.vxrm   := csrMod.io.status.vecState.vxrm.asUInt
 
-  csrOut.isXRet := RegEnable(isXRetFlag, false.B, io.in.fire)
+  csrOut.isXRet := isXRet
 
   csrOut.trapTarget := csrMod.io.out.bits.targetPc
   csrOut.interrupt := csrMod.io.status.interrupt
@@ -316,6 +309,8 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   csrOut.debugMode := csrMod.io.status.debugMode
 
+  csrOut.traceCSR := csrMod.io.status.traceCSR
+
   csrOut.customCtrl match {
     case custom =>
       custom.l1I_pf_enable            := csrMod.io.status.custom.l1I_pf_enable
diff --git a/src/main/scala/xiangshan/backend/rename/CompressUnit.scala b/src/main/scala/xiangshan/backend/rename/CompressUnit.scala
index c60a5ea78dd..b03dea2a8af 100644
--- a/src/main/scala/xiangshan/backend/rename/CompressUnit.scala
+++ b/src/main/scala/xiangshan/backend/rename/CompressUnit.scala
@@ -42,6 +42,7 @@ class CompressUnit(implicit p: Parameters) extends XSModule{
       val needRobFlags = Vec(RenameWidth, Output(Bool()))
       val instrSizes = Vec(RenameWidth, Output(UInt(log2Ceil(RenameWidth + 1).W)))
       val masks = Vec(RenameWidth, Output(UInt(RenameWidth.W)))
+      val canCompressVec = Vec(RenameWidth, Output(Bool()))
     }
   })
 
@@ -89,4 +90,5 @@ class CompressUnit(implicit p: Parameters) extends XSModule{
   (io.out.needRobFlags ++ io.out.instrSizes ++ io.out.masks).zip(decoder).foreach {
     case (sink, source) => sink := source
   }
+  io.out.canCompressVec := VecInit(canCompress)
 }
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 6dc87eb8a29..360850ac02d 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -443,34 +443,38 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   /**
    * trace begin
    */
+  // note: fusionInst can't robcompress
   val inVec = io.in.map(_.bits)
-  val canRobCompressVec = inVec.map(_.canRobCompress)
   val isRVCVec = inVec.map(_.preDecodeInfo.isRVC)
-  val halfWordNumVec = (0 until RenameWidth).map{
-    i => compressMasksVec(i).asBools.zip(isRVCVec).map{
-      case (mask, isRVC) => Mux(mask, Mux(isRVC, 1.U, 2.U), 0.U)
+  val isFusionVec = inVec.map(_.commitType).map(ctype => CommitType.isFused(ctype))
+
+  val canRobCompressVec = compressUnit.io.out.canCompressVec
+  val iLastSizeVec = isRVCVec.map(isRVC => Mux(isRVC, Ilastsize.HalfWord, Ilastsize.Word))
+  val halfWordNumVec = isRVCVec.map(isRVC => Mux(isRVC, 1.U, 2.U))
+  val halfWordNumMatrix = (0 until RenameWidth).map(
+    i => compressMasksVec(i).asBools.zipWithIndex.map{ case(mask, j) =>
+      Mux(mask, halfWordNumVec(j), 0.U)
     }
-  }
+  )
 
   for (i <- 0 until RenameWidth) {
     // iretire
     uops(i).traceBlockInPipe.iretire := Mux(canRobCompressVec(i),
-      halfWordNumVec(i).reduce(_ +& _),
-      Mux(isRVCVec(i), 1.U, 2.U)
+      halfWordNumMatrix(i).reduce(_ +& _),
+      (if(i < RenameWidth -1) Mux(isFusionVec(i), halfWordNumVec(i+1), 0.U) else 0.U) +& halfWordNumVec(i)
     )
 
     // ilastsize
-    val j = i
+    val tmp = i
     val lastIsRVC = WireInit(false.B)
-    (j until RenameWidth).map { j =>
+    (tmp until RenameWidth).map { j =>
       when(compressMasksVec(i)(j)) {
         lastIsRVC := io.in(j).bits.preDecodeInfo.isRVC
       }
     }
-
     uops(i).traceBlockInPipe.ilastsize := Mux(canRobCompressVec(i),
       Mux(lastIsRVC, Ilastsize.HalfWord, Ilastsize.Word),
-      Mux(isRVCVec(i), Ilastsize.HalfWord, Ilastsize.Word)
+      (if(i < RenameWidth -1) Mux(isFusionVec(i), iLastSizeVec(i+1), iLastSizeVec(i)) else iLastSizeVec(i))
     )
 
     // itype
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 9efba065546..4ef7252587b 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -73,6 +73,10 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val writebackNums = Flipped(Vec(writeback.size - params.StdCnt, ValidIO(UInt(writeback.size.U.getWidth.W))))
     val writebackNeedFlush = Input(Vec(params.allExuParams.filter(_.needExceptionGen).length, Bool()))
     val commits = Output(new RobCommitIO)
+    val trace = new Bundle {
+      val blockCommit = Input(Bool())
+      val traceCommitInfo = new TraceBundle(hasIaddr = false, CommitWidth, IretireWidthInPipe)
+    }
     val rabCommits = Output(new RabCommitIO)
     val diffCommits = if (backendParams.basicDebugEn) Some(Output(new DiffCommitIO)) else None
     val isVsetFlushPipe = Output(Bool())
@@ -245,7 +249,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   // Instructions in multiple Ftq entries compressed to one RobEntry do not occur.
   for (i <- 0 until CommitWidth) {
     val lastOffset = (rawInfo(i).traceBlockInPipe.iretire - (1.U << rawInfo(i).traceBlockInPipe.ilastsize.asUInt).asUInt) + rawInfo(i).ftqOffset
-    commitInfo(i).ftqOffset := lastOffset
+    commitInfo(i).ftqOffset := Mux(CommitType.isFused(rawInfo(i).commitType), rawInfo(i).ftqOffset, lastOffset)
   }
 
   // data for debug
@@ -739,8 +743,9 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   }.elsewhen(deqNeedFlush && io.flushOut.valid && !io.flushOut.bits.flushItself()){
     deqHasFlushed := true.B
   }
+  val traceBlock = io.trace.blockCommit
   val blockCommit = misPredBlock || lastCycleFlush || hasWFI || io.redirect.valid ||
-    (deqNeedFlush && !deqHasFlushed) || deqFlushBlock || criticalErrorState
+    (deqNeedFlush && !deqHasFlushed) || deqFlushBlock || criticalErrorState || traceBlock
 
   io.commits.isWalk := state === s_walk
   io.commits.isCommit := state === s_idle && !blockCommit
@@ -1033,13 +1038,9 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
     // trace
     val taken = branchWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U && writeback.bits.redirect.get.bits.cfiUpdate.taken).reduce(_ || _)
-    val xret = csrWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U && io.csr.isXRet).reduce(_ || _)
-
-    when(xret){
-      robEntries(i).traceBlockInPipe.itype := Itype.ExpIntReturn
-    }.elsewhen(Itype.isBranchType(robEntries(i).traceBlockInPipe.itype)){
-      // BranchType code(itype = 5) must be correctly replaced!
-      robEntries(i).traceBlockInPipe.itype := Mux(taken, Itype.Taken, Itype.NonTaken)
+    when(robEntries(i).valid && Itype.isBranchType(robEntries(i).traceBlockInPipe.itype) && taken){
+      // BranchType code(notaken itype = 4) must be correctly replaced!
+      robEntries(i).traceBlockInPipe.itype := Itype.Taken
     }
   }
 
@@ -1098,6 +1099,13 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val vxsatCanWbSeq = vxsat_wb.map(writeback => writeback.valid && writeback.bits.robIdx.value === needUpdateRobIdx(i))
     val vxsatRes = vxsatCanWbSeq.zip(vxsat_wb).map { case (canWb, wb) => Mux(canWb, wb.bits.vxsat.get, 0.U) }.fold(false.B)(_ | _)
     needUpdate(i).vxsat := Mux(!robBanksRdata(i).valid && instCanEnqFlag, 0.U, robBanksRdata(i).vxsat | vxsatRes)
+
+    // trace
+    val taken = branchWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === needUpdateRobIdx(i) && writeback.bits.redirect.get.bits.cfiUpdate.taken).reduce(_ || _)
+    when(robBanksRdata(i).valid && Itype.isBranchType(robBanksRdata(i).traceBlockInPipe.itype) && taken){
+      // BranchType code(notaken itype = 4) must be correctly replaced!
+      needUpdate(i).traceBlockInPipe.itype := Itype.Taken
+    }
   }
   robBanksRdataThisLineUpdate := VecInit(needUpdate.take(8))
   robBanksRdataNextLineUpdate := VecInit(needUpdate.drop(8))
@@ -1215,6 +1223,45 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   io.toVecExcpMod.logicPhyRegMap := rab.io.toVecExcpMod.logicPhyRegMap
   io.toVecExcpMod.excpInfo := vecExcpInfo
 
+  /**
+   * trace
+   */
+
+  // trace output
+  val traceValids = io.trace.traceCommitInfo.blocks.map(_.valid)
+  val traceBlocks = io.trace.traceCommitInfo.blocks
+  val traceBlockInPipe = io.trace.traceCommitInfo.blocks.map(_.bits.tracePipe)
+
+  // The reg 'isTraceXret' only for trace xret instructions. xret only occur in block(0).
+  val isTraceXret = RegInit(false.B)
+  when(io.csr.isXRet){
+    isTraceXret := true.B
+  }.elsewhen(isTraceXret && io.commits.isCommit && io.commits.commitValid(0)){
+    isTraceXret := false.B
+  }
+
+  for (i <- 0 until CommitWidth) {
+    traceBlocks(i).bits.ftqIdx.foreach(_ := rawInfo(i).ftqIdx)
+    traceBlocks(i).bits.ftqOffset.foreach(_ := rawInfo(i).ftqOffset)
+    traceBlockInPipe(i).itype := rawInfo(i).traceBlockInPipe.itype
+    traceBlockInPipe(i).iretire := rawInfo(i).traceBlockInPipe.iretire
+    traceBlockInPipe(i).ilastsize := rawInfo(i).traceBlockInPipe.ilastsize
+    traceValids(i) := io.commits.isCommit && io.commits.commitValid(i)
+    // exception/xret only occur in block(0).
+    if(i == 0) {
+      when(isTraceXret && io.commits.isCommit && io.commits.commitValid(0)){ // trace xret
+        traceBlocks(i).bits.tracePipe.itype := Itype.ExpIntReturn
+      }.elsewhen(io.exception.valid){ // trace exception
+        traceBlocks(i).bits.tracePipe.itype := Mux(io.exception.bits.isInterrupt,
+          Itype.Interrupt,
+          Itype.Exception
+        )
+        traceValids(i) := true.B
+        traceBlockInPipe(i).iretire := 0.U
+      }
+    }
+  }
+  
   /**
    * debug info
    */
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 5292f685106..410832fbf8c 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -64,7 +64,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
     // data end
     
     // trace
-    val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))
+    val traceBlockInPipe = new TracePipe(IretireWidthInPipe)
     // status begin
     val valid = Bool()
     val fflags = UInt(5.W)
@@ -111,7 +111,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val rfWen = Bool()
     val needFlush = Bool()
     // trace
-    val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))
+    val traceBlockInPipe = new TracePipe(IretireWidthInPipe)
     // debug_begin
     val debug_pc = OptionWrapper(backendParams.debugEn, UInt(VAddrBits.W))
     val debug_instr = OptionWrapper(backendParams.debugEn, UInt(32.W))
diff --git a/src/main/scala/xiangshan/backend/trace/Interface.scala b/src/main/scala/xiangshan/backend/trace/Interface.scala
index a765ed4d79a..e80066ee343 100644
--- a/src/main/scala/xiangshan/backend/trace/Interface.scala
+++ b/src/main/scala/xiangshan/backend/trace/Interface.scala
@@ -7,10 +7,11 @@ import utils.NamedUInt
 import xiangshan.HasXSParameter
 import xiangshan.frontend.{BrType, FtqPtr, PreDecodeInfo}
 
-class TraceTrap(implicit val p: Parameters) extends Bundle with HasXSParameter {
-  val cause = UInt(XLEN.W)
-  val tval  = UInt(XLEN.W)
-  val priv  = Priv()
+class TraceCSR(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  val cause = UInt(CauseWidth.W)
+  val tval  = UInt(TvalWidth.W)
+  val lastPriv    = Priv()
+  val currentPriv = Priv()
 }
 
 class TracePipe(iretireWidth: Int)(implicit val p: Parameters) extends Bundle with HasXSParameter {
@@ -20,14 +21,13 @@ class TracePipe(iretireWidth: Int)(implicit val p: Parameters) extends Bundle wi
 }
 
 class TraceBlock(hasIaddr: Boolean, iretireWidth: Int)(implicit val p: Parameters) extends Bundle with HasXSParameter {
-  val iaddr     = if (hasIaddr)   Some(UInt(XLEN.W))                      else None
+  val iaddr     = if (hasIaddr)   Some(UInt(IaddrWidth.W))                else None
   val ftqIdx    = if (!hasIaddr)  Some(new FtqPtr)                        else None
-  val ftqOffset = if (!hasIaddr)  Some( UInt(log2Up(PredictWidth).W))     else None
+  val ftqOffset = if (!hasIaddr)  Some(UInt(log2Up(PredictWidth).W))      else None
   val tracePipe = new TracePipe(iretireWidth)
 }
 
 class TraceBundle(hasIaddr: Boolean, blockSize: Int, iretireWidth: Int)(implicit val p: Parameters) extends Bundle with HasXSParameter {
-  val trap = Output(new TraceTrap)
   val blocks = Vec(blockSize, ValidIO(new TraceBlock(hasIaddr, iretireWidth)))
 }
 
@@ -37,29 +37,22 @@ class FromEncoder extends Bundle {
 }
 
 class TraceCoreInterface(implicit val p: Parameters) extends Bundle with HasXSParameter {
-  // parameter
-  val CauseWidth             = XLEN
-  val TvalWidth              = XLEN
-  val PrivWidth              = 3
-  val IaddrWidth             = XLEN
-  val ItypeWidth             = 4
-  val IretireWidthInPipe     = log2Up(RenameWidth * 2)
-  val IretireWidthCompressed = log2Up(RenameWidth * CommitWidth * 2)
-  val IlastsizeWidth         = 1
-  val GroupNum               = TraceGroupNum
-  
   val fromEncoder = Input(new Bundle {
     val enable = Bool()
     val stall  = Bool()
   })
-  val toEncoder   = Output(new Bundle {
-    val cause     = UInt(CauseWidth.W)
-    val tval      = UInt(TvalWidth.W)
-    val priv      = UInt(PrivWidth.W)
-    val iaddr     = UInt((GroupNum * IaddrWidth).W)
-    val itype     = UInt((GroupNum * ItypeWidth).W)
-    val iretire   = UInt((GroupNum * IretireWidthCompressed).W)
-    val ilastsize = UInt((GroupNum * IlastsizeWidth).W)
+  val toEncoder = Output(new Bundle {
+    val priv   = Priv()
+    val trap   = new Bundle{
+      val cause = UInt(CauseWidth.W)
+      val tval  = UInt(TvalWidth.W)
+    }
+    val groups = Vec(TraceGroupNum, ValidIO(new Bundle{
+      val iaddr     = UInt(IaddrWidth.W)
+      val itype     = UInt(ItypeWidth.W)
+      val iretire   = UInt(IretireWidthCompressed.W)
+      val ilastsize = UInt(IlastsizeWidth.W)
+    }))
   })
 }
 
@@ -81,8 +74,8 @@ object Itype extends NamedUInt(4) {
   def OtherUninferableJump = 14.U   //rename
   def OtherInferableJump   = 15.U   //rename
 
-  // Assuming the branchType is taken here, it will be correctly modified after writeBack.
-  def Branch = 5.U
+  // Assuming the branchType is NonTaken here, it will be correctly modified after writeBack.
+  def Branch = NonTaken
 
   def jumpTypeGen(brType: UInt, rd: OpRegType, rs: OpRegType): UInt = {
 
@@ -131,6 +124,8 @@ object Itype extends NamedUInt(4) {
 
   def isTrap(itype: UInt) = Seq(Exception, Interrupt).map(_ === itype).reduce(_ || _)
 
+  def isTrapOrXret(itype: UInt) = Seq(Exception, Interrupt, ExpIntReturn).map(_ === itype).reduce(_ || _)
+
   def isNotNone(itype: UInt) = itype =/= None
 
   def isBranchType(itype: UInt) = itype === Branch
@@ -155,9 +150,9 @@ object Priv extends NamedUInt(3) {
 }
 
 class OpRegType extends Bundle {
-  val value = UInt(3.W)
+  val value = UInt(6.W)
   def isX0   = this.value === 0.U
   def isX1   = this.value === 1.U
   def isX5   = this.value === 5.U
-  def isLink = Seq(isX1, isX5).map(_ === this.value).reduce(_ || _)
+  def isLink = Seq(isX1, isX5).reduce(_ || _)
 }
diff --git a/src/main/scala/xiangshan/backend/trace/Trace.scala b/src/main/scala/xiangshan/backend/trace/Trace.scala
new file mode 100644
index 00000000000..49a6f0086bb
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/trace/Trace.scala
@@ -0,0 +1,74 @@
+package xiangshan.backend.trace
+
+import chisel3._
+import chisel3.util.{RegEnable, ValidIO, log2Up}
+import org.chipsalliance.cde.config.Parameters
+import xiangshan.HasXSParameter
+
+class TraceParams(
+  val TraceGroupNum  : Int,
+  val IaddrWidth     : Int,
+  val PrivWidth      : Int,
+  val ItypeWidth     : Int,
+  val IlastsizeWidth : Int,
+)
+
+class TraceIO(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  val in = new Bundle {
+    val fromEncoder    = Input(new FromEncoder)
+    val fromRob        = Flipped(new TraceBundle(hasIaddr = false, CommitWidth, IretireWidthInPipe))
+    val fromPcMem      = Input(Vec(TraceGroupNum, UInt(IaddrWidth.W)))
+  }
+  val out = new Bundle {
+    val toPcMem        = new TraceBundle(hasIaddr = false, TraceGroupNum, IretireWidthCompressed)
+    val toEncoder      = new TraceBundle(hasIaddr = true,  TraceGroupNum, IretireWidthCompressed)
+    val blockRobCommit = Output(Bool())
+  }
+}
+
+class Trace(implicit val p: Parameters) extends Module with HasXSParameter {
+  val io = IO(new TraceIO)
+  val (fromEncoder, fromRob, fromPcMem, toPcMem, toEncoder) = (io.in.fromEncoder, io.in.fromRob, io.in.fromPcMem, io.out.toPcMem, io.out.toEncoder)
+
+  /**
+   * stage 0: CommitInfo from rob
+   */
+  val blockCommit = Wire(Bool())
+  io.out.blockRobCommit := blockCommit
+
+  /**
+   * stage 1: regNext(robCommitInfo)
+   */
+  val s1_in = fromRob
+  val s1_out = WireInit(0.U.asTypeOf(s1_in))
+  for(i <- 0 until CommitWidth) {
+    s1_out.blocks(i).valid := RegEnable(s1_in.blocks(i).valid, false.B, !blockCommit)
+    s1_out.blocks(i).bits := RegEnable(s1_in.blocks(i).bits, 0.U.asTypeOf(s1_in.blocks(i).bits), s1_in.blocks(i).valid)
+  }
+
+  /**
+   * stage 2: compress, s2_out(deq from traceBuffer) -> pcMem
+   */
+  val s2_in = s1_out
+  val traceBuffer = Module(new TraceBuffer)
+  traceBuffer.io.in.fromEncoder := fromEncoder
+  traceBuffer.io.in.fromRob := s2_in
+  val s2_out_groups = traceBuffer.io.out.groups
+  blockCommit := traceBuffer.io.out.blockCommit
+
+  /**
+   * stage 3: groups with iaddr from pcMem(ftqidx & ftqOffset -> iaddr) -> encoder
+   */
+  val s3_in_groups = s2_out_groups
+  val s3_out_groups = RegNext(s3_in_groups)
+  toPcMem := s3_in_groups
+
+  for(i <- 0 until TraceGroupNum) {
+    toEncoder.blocks(i).valid := s3_out_groups.blocks(i).valid
+    toEncoder.blocks(i).bits.iaddr.foreach(_ := Mux(s3_out_groups.blocks(i).valid, fromPcMem(i), 0.U))
+    toEncoder.blocks(i).bits.tracePipe := s3_out_groups.blocks(i).bits.tracePipe
+  }
+  if(backendParams.debugEn) {
+    dontTouch(io.out.toEncoder)
+  }
+}
diff --git a/src/main/scala/xiangshan/backend/trace/TraceBuffer.scala b/src/main/scala/xiangshan/backend/trace/TraceBuffer.scala
new file mode 100644
index 00000000000..e7e7337ae28
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/trace/TraceBuffer.scala
@@ -0,0 +1,111 @@
+package xiangshan.backend.trace
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.{CircularQueuePtr, HasCircularQueuePtrHelper}
+import xiangshan.{HasXSParameter, XSCoreParamsKey}
+
+class TraceBuffer(implicit val p: Parameters) extends Module
+  with HasXSParameter
+  with HasCircularQueuePtrHelper {
+
+  val io = IO(new Bundle {
+    val in = new Bundle{
+      val fromEncoder = Input(new FromEncoder)
+      val fromRob     = Flipped(new TraceBundle(hasIaddr = false, CommitWidth, IretireWidthCompressed))
+    }
+    val out = new Bundle { // output groups to pcMem
+      val blockCommit = Output(Bool())
+      val groups = new TraceBundle(hasIaddr = false, TraceGroupNum, IretireWidthCompressed)
+    }
+  })
+
+  // buffer: compress info from robCommit
+  val traceEntries = Reg(Vec(CommitWidth, ValidIO(new TraceBlock(false, IretireWidthCompressed))))
+  val blockCommit = RegInit(false.B) // to rob
+
+  /**
+   * compress, update blocks
+   */
+  val inValidVec = VecInit(io.in.fromRob.blocks.map(_.valid))
+  val inTypeIsNotNoneVec = VecInit(io.in.fromRob.blocks.map(block => Itype.isNotNone(block.bits.tracePipe.itype)))
+  val needPcVec = Wire(Vec(CommitWidth, Bool()))
+  for(i <- 0 until CommitWidth) {
+    val rightHasValid = if(i == CommitWidth - 1) false.B  else (inValidVec.asUInt(CommitWidth-1, i+1).orR)
+    needPcVec(i) := inValidVec(i) & (inTypeIsNotNoneVec(i) || !rightHasValid) & !blockCommit
+  }
+
+  val blocksUpdate = WireInit(io.in.fromRob.blocks)
+  for(i <- 1 until CommitWidth){
+    when(!needPcVec(i-1)){
+      blocksUpdate(i).bits.tracePipe.iretire := blocksUpdate(i - 1).bits.tracePipe.iretire + io.in.fromRob.blocks(i).bits.tracePipe.iretire
+      blocksUpdate(i).bits.ftqOffset.get := blocksUpdate(i - 1).bits.ftqOffset.get
+      blocksUpdate(i).bits.ftqIdx.get := blocksUpdate(i - 1).bits.ftqIdx.get
+     }
+  }
+
+  /**
+   * enq to traceEntries
+   */
+  val countVec = VecInit((0 until CommitWidth).map(i => PopCount(needPcVec.asUInt(i, 0))))
+  val numNeedPc = countVec(CommitWidth-1)
+
+  val enqPtr = RegInit(TracePtr(false.B, 0.U))
+  val deqPtr = RegInit(TracePtr(false.B, 0.U))
+  val deqPtrPre = RegNext(deqPtr)
+  val enqPtrNext = WireInit(enqPtr)
+  val deqPtrNext = WireInit(deqPtr)
+  enqPtr := enqPtrNext
+  deqPtr := deqPtrNext
+  val canNotTraceAll = distanceBetween(enqPtrNext, deqPtrNext) > 0.U
+  blockCommit := io.in.fromEncoder.enable && (canNotTraceAll || io.in.fromEncoder.stall)
+
+  enqPtrNext := enqPtr + numNeedPc
+  deqPtrNext := Mux(deqPtr + TraceGroupNum.U > enqPtrNext, enqPtrNext, deqPtr + TraceGroupNum.U)
+
+  val traceIdxVec = VecInit(countVec.map(count => (enqPtr + count - 1.U).value))
+  for(i <- 0 until CommitWidth){
+    when(needPcVec(i)){
+      traceEntries(traceIdxVec(i)) := blocksUpdate(i)
+    }
+  }
+
+  /**
+   * deq from traceEntries
+   */
+  val blockOut = WireInit(0.U.asTypeOf(io.out.groups))
+  for(i <- 0 until TraceGroupNum) {
+    when(deqPtrPre + i.U < enqPtr) {
+      blockOut.blocks(i) := traceEntries((deqPtrPre + i.U).value)
+    } .otherwise {
+      blockOut.blocks(i).valid := false.B
+    }
+  }
+
+  io.out.blockCommit := blockCommit
+  io.out.groups := blockOut
+
+  if(backendParams.debugEn){
+    dontTouch(countVec)
+    dontTouch(numNeedPc)
+    dontTouch(traceIdxVec)
+  }
+}
+
+class TracePtr(entries: Int) extends CircularQueuePtr[TracePtr](
+  entries
+) with HasCircularQueuePtrHelper {
+
+  def this()(implicit p: Parameters) = this(p(XSCoreParamsKey).CommitWidth)
+
+}
+
+object TracePtr {
+  def apply(f: Bool, v: UInt)(implicit p: Parameters): TracePtr = {
+    val ptr = Wire(new TracePtr)
+    ptr.flag := f
+    ptr.value := v
+    ptr
+  }
+}
diff --git a/src/test/scala/top/SimTop.scala b/src/test/scala/top/SimTop.scala
index ed9343b40b7..029a2069449 100644
--- a/src/test/scala/top/SimTop.scala
+++ b/src/test/scala/top/SimTop.scala
@@ -66,6 +66,8 @@ class SimTop(implicit p: Parameters) extends Module {
   soc.io.cacheable_check := DontCare
   soc.io.riscv_rst_vec.foreach(_ := 0x10000000L.U)
   l_soc.nmi.foreach(_.foreach(intr => { intr := false.B; dontTouch(intr) }))
+  soc.io.traceCoreInterface.foreach(_.fromEncoder.enable := false.B)
+  soc.io.traceCoreInterface.foreach(_.fromEncoder.stall  := false.B)
 
   // soc.io.rtc_clock is a div100 of soc.io.clock
   val rtcClockDiv = 100
```
