# Commit Log
- Issue: #4122
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4122
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4122
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4122
- Changed files: 22
- Additions: 176
- Deletions: 11

## Files
- `coupledL2`
- `huancun`
- `openLLC`
- `src/main/scala/top/Top.scala`
- `src/main/scala/top/XSNoCTop.scala`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/XSTileWrap.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeStage.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/IBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala`

## Diff
```diff
diff --git a/coupledL2 b/coupledL2
index 394b7392f58..0f9f9351585 160000
--- a/coupledL2
+++ b/coupledL2
@@ -1 +1 @@
-Subproject commit 394b7392f5899ae277b0ff55b9ad694afbf3e4f9
+Subproject commit 0f9f93515853e66b8b1480fbe3f73508b1e270cc
diff --git a/huancun b/huancun
index 3fc7e7e0c21..90aaf593520 160000
--- a/huancun
+++ b/huancun
@@ -1 +1 @@
-Subproject commit 3fc7e7e0c2127c601b2a7d180f49845421a86d8d
+Subproject commit 90aaf5935206ff322e461c3d021436c20dd0ac85
diff --git a/openLLC b/openLLC
index 466bfd76634..8bec4d029bd 160000
--- a/openLLC
+++ b/openLLC
@@ -1 +1 @@
-Subproject commit 466bfd766349934a3898b78d8307dd343f3977e8
+Subproject commit 8bec4d029bdf985f075396e641d514d1a7a19c15
diff --git a/src/main/scala/top/Top.scala b/src/main/scala/top/Top.scala
index 9a8b34795cd..6949db5e857 100644
--- a/src/main/scala/top/Top.scala
+++ b/src/main/scala/top/Top.scala
@@ -354,6 +354,9 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc() with HasSoCParameter
         core_with_l2.zip(chi_openllc_opt.get.io.debugTopDown.addrMatch).foreach { case (tile, l3Match) =>
           tile.module.io.debugTopDown.l3MissMatch := l3Match
         }
+        core_with_l2.zip(chi_openllc_opt).foreach { case (tile, l3) =>
+          tile.module.io.l3Miss := l3.io.l3Miss
+        }
       }
     }
 
@@ -379,11 +382,14 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc() with HasSoCParameter
         }
         l3.module.io.debugTopDown.robHeadPaddr := core_with_l2.map(_.module.io.debugTopDown.robHeadPaddr)
         core_with_l2.zip(l3.module.io.debugTopDown.addrMatch).foreach { case (tile, l3Match) => tile.module.io.debugTopDown.l3MissMatch := l3Match }
+        core_with_l2.foreach(_.module.io.l3Miss := l3.module.io.l3Miss)
       case None =>
     }
 
     (chi_openllc_opt, l3cacheOpt) match {
-      case (None, None) => core_with_l2.foreach(_.module.io.debugTopDown.l3MissMatch := false.B)
+      case (None, None) =>
+        core_with_l2.foreach(_.module.io.debugTopDown.l3MissMatch := false.B)
+        core_with_l2.foreach(_.module.io.l3Miss := false.B)
       case _ =>
     }
 
diff --git a/src/main/scala/top/XSNoCTop.scala b/src/main/scala/top/XSNoCTop.scala
index 7a25523a05a..25fb23de446 100644
--- a/src/main/scala/top/XSNoCTop.scala
+++ b/src/main/scala/top/XSNoCTop.scala
@@ -207,6 +207,7 @@ class XSNoCTop()(implicit p: Parameters) extends BaseXSSoc with HasSoCParameter
     core_rst_node.out.head._1 := false.B.asAsyncReset
 
     core_with_l2.module.io.debugTopDown.l3MissMatch := false.B
+    core_with_l2.module.io.l3Miss := false.B
   }
 
   lazy val module = new XSNoCTopImp(this)
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index b5d8a0f4bbe..d52faf3d3ef 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -785,3 +785,15 @@ class L2ToL1Hint(implicit p: Parameters) extends XSBundle with HasDCacheParamete
   val isKeyword = Bool()                             // miss entry keyword -> L1 load queue replay
 }
 
+class TopDownInfo(implicit p: Parameters) extends XSBundle {
+  val lqEmpty = Input(Bool())
+  val sqEmpty = Input(Bool())
+  val l1Miss = Input(Bool())
+  val noUopsIssued = Output(Bool())
+  val l2TopMiss = Input(new TopDownFromL2Top)
+}
+
+class TopDownFromL2Top(implicit p: Parameters) extends XSBundle {
+  val l2Miss = Bool()
+  val l3Miss = Bool()
+}
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index d7699262b26..bd714a12084 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -183,6 +183,11 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
         val robHeadPaddr = Flipped(Valid(UInt(36.W)))
         val l2MissMatch = Output(Bool())
       }
+      val l2Miss = Output(Bool())
+      val l3Miss = new Bundle {
+        val fromTile = Input(Bool())
+        val toCore = Output(Bool())
+      }
       val chi = if (enableCHI) Some(new PortIO) else None
       val nodeID = if (enableCHI) Some(Input(UInt(NodeIDWidth.W))) else None
       val l2_tlb_req = new TlbRequestIO(nRespDups = 2)
@@ -204,7 +209,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
     io.cpu_halt.toTile := io.cpu_halt.fromCore
     io.cpu_critical_error.toTile := io.cpu_critical_error.fromCore
     io.l2_flush_done := true.B //TODO connect CoupleedL2
-
+    io.l3Miss.toCore := io.l3Miss.fromTile
     // trace interface
     val traceToTile = io.traceCoreInterface.toTile
     val traceFromCore = io.traceCoreInterface.fromCore
@@ -248,6 +253,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
       l2.io.debugTopDown.robHeadPaddr := io.debugTopDown.robHeadPaddr
       l2.io.debugTopDown.robTrueCommit := io.debugTopDown.robTrueCommit
       io.debugTopDown.l2MissMatch := l2.io.debugTopDown.l2MissMatch
+      io.l2Miss := l2.io.l2Miss
 
       /* l2 tlb */
       io.l2_tlb_req.req.bits := DontCare
@@ -294,6 +300,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
     } else {
       io.l2_hint := 0.U.asTypeOf(io.l2_hint)
       io.debugTopDown <> DontCare
+      io.l2Miss := false.B
 
       io.l2_tlb_req.req.valid := false.B
       io.l2_tlb_req.req.bits := DontCare
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index f7b72ce5b56..51935c5ace8 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -104,6 +104,10 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
       val l2MissMatch = Input(Bool())
       val l3MissMatch = Input(Bool())
     }
+    val topDownInfo = Input(new Bundle {
+      val l2Miss = Bool()
+      val l3Miss = Bool()
+    })
   })
 
   dontTouch(io.l2_flush_done)
@@ -262,6 +266,14 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   io.resetInFrontend := memBlock.io.resetInFrontendBypass.toL2Top
   memBlock.io.traceCoreInterfaceBypass.fromBackend <> backend.io.traceCoreInterface
   io.traceCoreInterface <> memBlock.io.traceCoreInterfaceBypass.toL2Top
+  memBlock.io.topDownInfo.fromL2Top.l2Miss := io.topDownInfo.l2Miss
+  memBlock.io.topDownInfo.fromL2Top.l3Miss := io.topDownInfo.l3Miss
+  memBlock.io.topDownInfo.toBackend.noUopsIssued := backend.io.topDownInfo.noUopsIssued
+  backend.io.topDownInfo.lqEmpty := memBlock.io.topDownInfo.toBackend.lqEmpty
+  backend.io.topDownInfo.sqEmpty := memBlock.io.topDownInfo.toBackend.sqEmpty
+  backend.io.topDownInfo.l1Miss := memBlock.io.topDownInfo.toBackend.l1Miss
+  backend.io.topDownInfo.l2TopMiss.l2Miss := memBlock.io.topDownInfo.toBackend.l2TopMiss.l2Miss
+  backend.io.topDownInfo.l2TopMiss.l3Miss := memBlock.io.topDownInfo.toBackend.l2TopMiss.l3Miss
 
 
   if (debugOpts.ResetGen) {
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index feef7454d1a..a08b9de62a1 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -111,6 +111,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
         val robHeadPaddr = Valid(UInt(PAddrBits.W))
         val l3MissMatch = Input(Bool())
       }
+      val l3Miss = Input(Bool())
       val chi = if (enableCHI) Some(new PortIO) else None
       val nodeID = if (enableCHI) Some(Input(UInt(NodeIDWidth.W))) else None
       val clintTime = Input(ValidIO(UInt(64.W)))
@@ -160,6 +161,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
       l2top.module.io.debugTopDown.robTrueCommit := core.module.io.debugTopDown.robTrueCommit
       l2top.module.io.l2_pmp_resp := core.module.io.l2_pmp_resp
       core.module.io.l2_tlb_req <> l2top.module.io.l2_tlb_req
+      core.module.io.topDownInfo.l2Miss := l2top.module.io.l2Miss
 
       core.module.io.perfEvents <> l2top.module.io.perfEvents
     } else {
@@ -171,6 +173,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
       core.module.io.l2PfqBusy := false.B
       core.module.io.debugTopDown.l2MissMatch := false.B
+      core.module.io.topDownInfo.l2Miss := false.B
 
       core.module.io.l2_tlb_req.req.valid := false.B
       core.module.io.l2_tlb_req.req.bits := DontCare
@@ -182,6 +185,8 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
     io.debugTopDown.robHeadPaddr := core.module.io.debugTopDown.robHeadPaddr
     core.module.io.debugTopDown.l3MissMatch := io.debugTopDown.l3MissMatch
+    l2top.module.io.l3Miss.fromTile := io.l3Miss
+    core.module.io.topDownInfo.l3Miss := l2top.module.io.l3Miss.toCore
 
     io.chi.foreach(_ <> l2top.module.io.chi.get)
     l2top.module.io.nodeID.foreach(_ := io.nodeID.get)
diff --git a/src/main/scala/xiangshan/XSTileWrap.scala b/src/main/scala/xiangshan/XSTileWrap.scala
index ad78a089b68..b7ec11d0c49 100644
--- a/src/main/scala/xiangshan/XSTileWrap.scala
+++ b/src/main/scala/xiangshan/XSTileWrap.scala
@@ -68,6 +68,7 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
         val robHeadPaddr = Valid(UInt(PAddrBits.W))
         val l3MissMatch = Input(Bool())
       }
+      val l3Miss = Input(Bool())
       val chi = EnableCHIAsyncBridge match {
         case Some(param) => new AsyncPortIO(param)
         case None => new PortIO
@@ -98,6 +99,7 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
     io.hartIsInReset := tile.module.io.hartIsInReset
     io.traceCoreInterface <> tile.module.io.traceCoreInterface
     io.debugTopDown <> tile.module.io.debugTopDown
+    tile.module.io.l3Miss := io.l3Miss
     tile.module.io.nodeID.foreach(_ := io.nodeID.get)
 
     // CLINT Async Queue Sink
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index bcd19fe04ce..1798710f711 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -492,6 +492,11 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   dataPath.io.fromBypassNetwork := bypassNetwork.io.toDataPath
   dataPath.io.fromVecExcpMod.r := vecExcpMod.o.toVPRF.r
   dataPath.io.fromVecExcpMod.w := vecExcpMod.o.toVPRF.w
+  dataPath.io.topDownInfo.lqEmpty := DelayN(io.topDownInfo.lqEmpty, 2)
+  dataPath.io.topDownInfo.sqEmpty := DelayN(io.topDownInfo.sqEmpty, 2)
+  dataPath.io.topDownInfo.l1Miss := RegNext(io.topDownInfo.l1Miss)
+  dataPath.io.topDownInfo.l2TopMiss.l2Miss := io.topDownInfo.l2TopMiss.l2Miss
+  dataPath.io.topDownInfo.l2TopMiss.l3Miss := io.topDownInfo.l2TopMiss.l3Miss
 
   og2ForVector.io.flush := ctrlBlock.io.toDataPath.flush
   og2ForVector.io.ldCancel := io.mem.ldCancel
@@ -852,6 +857,8 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
 
   io.debugRolling := ctrlBlock.io.debugRolling
 
+  io.topDownInfo.noUopsIssued := RegNext(dataPath.io.topDownInfo.noUopsIssued)
+
   if(backendParams.debugEn) {
     dontTouch(memScheduler.io)
     dontTouch(dataPath.io.toMemExu)
@@ -898,10 +905,12 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   val fpSchedulerPerf  = fpScheduler.asInstanceOf[SchedulerArithImp].getPerfEvents
   val vecSchedulerPerf = vfScheduler.asInstanceOf[SchedulerArithImp].getPerfEvents
   val memSchedulerPerf = memScheduler.asInstanceOf[SchedulerMemImp].getPerfEvents
+  val dataPathPerf = dataPath.getPerfEvents
 
   val perfBackend  = Seq()
   // let index = 0 be no event
-  val allPerfEvents = Seq(("noEvent", 0.U)) ++ ctrlBlockPerf ++ intSchedulerPerf ++ fpSchedulerPerf ++ vecSchedulerPerf ++ memSchedulerPerf ++ perfBackend
+  val allPerfEvents = Seq(("noEvent", 0.U)) ++ ctrlBlockPerf  ++ dataPathPerf ++
+    intSchedulerPerf ++ fpSchedulerPerf ++ vecSchedulerPerf ++ memSchedulerPerf ++ perfBackend
 
 
   if (printEventCoding) {
@@ -1063,4 +1072,5 @@ class BackendIO(implicit p: Parameters, params: BackendParams) extends XSBundle
     val fromCore = new CoreDispatchTopDownIO
   }
   val debugRolling = new RobDebugRollingIO
+  val topDownInfo = new TopDownInfo
 }
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index ed90083bf5e..a3eed2e8d2a 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -354,6 +354,11 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
       val fromBackend = Flipped(new TraceCoreInterface(hasOffset = true))
       val toL2Top     = new TraceCoreInterface
     }
+
+    val topDownInfo = new Bundle {
+      val fromL2Top = Input(new TopDownFromL2Top)
+      val toBackend = Flipped(new TopDownInfo)
+    }
   })
 
   dontTouch(io.inner_hartId)
@@ -2001,6 +2006,13 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   dcache.io.debugTopDown.robHeadOtherReplay := lsq.io.debugTopDown.robHeadOtherReplay
   dcache.io.debugRolling := io.debugRolling
 
+  lsq.io.noUopsIssued := io.topDownInfo.toBackend.noUopsIssued
+  io.topDownInfo.toBackend.lqEmpty := lsq.io.lqEmpty
+  io.topDownInfo.toBackend.sqEmpty := lsq.io.sqEmpty
+  io.topDownInfo.toBackend.l1Miss := dcache.io.l1Miss
+  io.topDownInfo.toBackend.l2TopMiss.l2Miss := RegNext(io.topDownInfo.fromL2Top.l2Miss)
+  io.topDownInfo.toBackend.l2TopMiss.l3Miss := RegNext(io.topDownInfo.fromL2Top.l3Miss)
+
   val hyLdDeqCount = PopCount(io.ooo_to_mem.issueHya.map(x => x.valid && FuType.isLoad(x.bits.uop.fuType)))
   val hyStDeqCount = PopCount(io.ooo_to_mem.issueHya.map(x => x.valid && FuType.isStore(x.bits.uop.fuType)))
   val ldDeqCount = PopCount(io.ooo_to_mem.issueLda.map(_.valid)) +& hyLdDeqCount
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 7196d5de98a..b7cb67ee20c 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -18,6 +18,7 @@ import xiangshan.backend.issue.{FpScheduler, ImmExtractor, IntScheduler, MemSche
 import xiangshan.backend.issue.EntryBundles._
 import xiangshan.backend.regfile._
 import xiangshan.backend.regcache._
+import xiangshan.backend.fu.FuConfig
 import xiangshan.backend.fu.FuType.is0latency
 import xiangshan.mem.{LqPtr, SqPtr}
 
@@ -36,7 +37,7 @@ class DataPath(params: BackendParams)(implicit p: Parameters) extends LazyModule
 }
 
 class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params: BackendParams)
-  extends LazyModuleImp(wrapper) with HasXSParameter {
+  extends LazyModuleImp(wrapper) with HasXSParameter with HasPerfEvents {
 
   val io = IO(new DataPathIO())
 
@@ -837,6 +838,52 @@ class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params
       XSPerfAccumulate(s"MEM_ExuId${exuParams.exuIdx}_src0_dataSource_zero",     exu.fire && exu.bits.common.dataSources(0).readZero)
     }
   })
+
+  // Top-Down
+  def FewUops = 4
+
+  val lqEmpty = io.topDownInfo.lqEmpty
+  val sqEmpty = io.topDownInfo.sqEmpty
+  val l1Miss = io.topDownInfo.l1Miss
+  val l2Miss = io.topDownInfo.l2TopMiss.l2Miss
+  val l3Miss = io.topDownInfo.l2TopMiss.l3Miss
+
+  val uopsIssued = fromIQ.flatten.map(_.fire).reduce(_ || _)
+  val uopsIssuedCnt = PopCount(fromIQ.flatten.map(_.fire))
+  val fewUopsIssued = (0 until FewUops).map(_.U === uopsIssuedCnt).reduce(_ || _)
+
+  val stallLoad = !uopsIssued
+
+  val noStoreIssued = !fromMemIQ.flatten.filter(memIq => memIq.bits.exuParams.fuConfigs.contains(FuConfig.StaCfg) ||
+                                                         memIq.bits.exuParams.fuConfigs.contains(FuConfig.StdCfg)
+  ).map(_.fire).reduce(_ || _)
+  val stallStore = uopsIssued && noStoreIssued
+
+  val stallLoadReg = DelayN(stallLoad, 2)
+  val stallStoreReg = DelayN(stallStore, 2)
+
+  val memStallAnyLoad = stallLoadReg && !lqEmpty
+  val memStallStore = stallStoreReg && !sqEmpty
+  val memStallL1Miss = memStallAnyLoad && l1Miss
+  val memStallL2Miss = memStallL1Miss && l2Miss
+  val memStallL3Miss = memStallL2Miss && l3Miss
+
+  io.topDownInfo.noUopsIssued := stallLoad
+
+  XSPerfAccumulate("exec_stall_cycle",   fewUopsIssued)
+  XSPerfAccumulate("mem_stall_store",    memStallStore)
+  XSPerfAccumulate("mem_stall_l1miss",   memStallL1Miss)
+  XSPerfAccumulate("mem_stall_l2miss",   memStallL2Miss)
+  XSPerfAccumulate("mem_stall_l3miss",   memStallL3Miss)
+
+  val perfEvents = Seq(
+    ("EXEC_STALL_CYCLE",  fewUopsIssued),
+    ("MEMSTALL_STORE",    memStallStore),
+    ("MEMSTALL_L1MISS",   memStallL1Miss),
+    ("MEMSTALL_L2MISS",   memStallL2Miss),
+    ("MEMSTALL_L3MISS",   memStallL3Miss),
+  )
+  generatePerfEvent()
 }
 
 class DataPathIO()(implicit p: Parameters, params: BackendParams) extends XSBundle {
@@ -923,4 +970,6 @@ class DataPathIO()(implicit p: Parameters, params: BackendParams) extends XSBund
   val diffV0Rat  = if (params.basicDebugEn) Some(Input(Vec(1, UInt(log2Up(V0PhyRegs).W)))) else None
   val diffVlRat  = if (params.basicDebugEn) Some(Input(Vec(1, UInt(log2Up(VlPhyRegs).W)))) else None
   val diffVl     = if (params.basicDebugEn) Some(Output(UInt(VlData().dataWidth.W))) else None
+
+  val topDownInfo = new TopDownInfo
 }
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
index b003a39f1be..45cf5891a5f 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
@@ -294,11 +294,20 @@ class DecodeStage(implicit p: Parameters) extends XSModule
   io.toCSR.trapInstInfo.valid := hasIllegalInst && !io.redirect
   io.toCSR.trapInstInfo.bits.fromDecodedInst(illegalInst)
 
+  val recoveryFlag = RegInit(false.B)
+  when(io.redirect) {
+    recoveryFlag := true.B
+  }.elsewhen(io.in.map(_.fire).reduce(_ || _)) {
+    recoveryFlag := false.B
+  }
+
   XSPerfAccumulate("in_valid_count", PopCount(io.in.map(_.valid)))
   XSPerfAccumulate("in_fire_count", PopCount(io.in.map(_.fire)))
   XSPerfAccumulate("in_valid_not_ready_count", PopCount(io.in.map(x => x.valid && !x.ready)))
   XSPerfAccumulate("stall_cycle", io.in.head match { case x => x.valid && !x.ready})
   XSPerfAccumulate("wait_cycle", !io.in.head.valid && io.out.head.ready)
+  XSPerfAccumulate("inst_spec", PopCount(io.in.map(_.fire)))
+  XSPerfAccumulate("recovery_bubble", recoveryFlag)
 
   XSPerfHistogram("in_valid_range", PopCount(io.in.map(_.valid)), true.B, 0, DecodeWidth + 1, 1)
   XSPerfHistogram("in_fire_range", PopCount(io.in.map(_.fire)), true.B, 0, DecodeWidth + 1, 1)
@@ -312,6 +321,8 @@ class DecodeStage(implicit p: Parameters) extends XSModule
     ("decoder_waitInstr",   PopCount(inValidNotReady)   ),
     ("decoder_stall_cycle", hasValid && !io.out(0).ready),
     ("decoder_utilization", PopCount(io.in.map(_.valid))),
+    ("INST_SPEC",           PopCount(io.in.map(_.fire))),
+    ("RECOVERY_BUBBLE",     recoveryFlag)
   )
   generatePerfEvent()
 
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index f5e8e82e849..e0dd42bdb0b 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -137,6 +137,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val redirectWBs = io.writeback.filter(x => x.bits.redirect.nonEmpty).toSeq
   val vxsatWBs = io.exuWriteback.filter(x => x.bits.vxsat.nonEmpty).toSeq
   val branchWBs = io.exuWriteback.filter(_.bits.params.hasBrhFu).toSeq
+  val jmpWBs = io.exuWriteback.filter(_.bits.params.hasJmpFu).toSeq
   val csrWBs = io.exuWriteback.filter(x => x.bits.params.hasCSR).toSeq
 
   val numExuWbPorts = exuWBs.length
@@ -611,6 +612,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   io.flushOut.bits.ftqOffset := Mux(needModifyFtqIdxOffset, firstVInstrFtqOffset, deqPtrEntry.ftqOffset)
   io.flushOut.bits.level := Mux(deqHasReplayInst || intrEnable || deqHasException || needModifyFtqIdxOffset, RedirectLevel.flush, RedirectLevel.flushAfter) // TODO use this to implement "exception next"
   io.flushOut.bits.interrupt := true.B
+  XSPerfAccumulate("flush_num", io.flushOut.valid)
   XSPerfAccumulate("interrupt_num", io.flushOut.valid && intrEnable)
   XSPerfAccumulate("exception_num", io.flushOut.valid && deqHasException)
   XSPerfAccumulate("flush_pipe_num", io.flushOut.valid && isFlushPipe)
@@ -1560,6 +1562,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     }
   }
 
+  val brhMispred = PopCount(branchWBs.map(wb => wb.valid & wb.bits.redirect.get.valid))
+  val jmpMispred = PopCount(jmpWBs.map(wb => wb.valid && wb.bits.redirect.get.valid))
+  val misPred = brhMispred +& jmpMispred
+
+  XSPerfAccumulate("br_mis_pred", misPred)
+
   val commitLoadVec = VecInit(commitLoadValid)
   val commitBranchVec = VecInit(commitBranchValid)
   val commitStoreVec = VecInit(io.commits.commitValid.zip(commitIsStore).map { case (v, t) => v && t })
@@ -1580,6 +1588,8 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     ("rob_2_4_valid          ", numValidEntries > (RobSize / 4).U && numValidEntries <= (RobSize / 2).U),
     ("rob_3_4_valid          ", numValidEntries > (RobSize / 2).U && numValidEntries <= (RobSize * 3 / 4).U),
     ("rob_4_4_valid          ", numValidEntries > (RobSize * 3 / 4).U),
+    ("BR_MIS_PRED            ", misPred),
+    ("TOTAL_FLUSH            ", io.flushOut.valid)
   )
   generatePerfEvent()
 
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 22197b50a7d..c584dfe2a9f 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -810,6 +810,7 @@ class DCacheIO(implicit p: Parameters) extends DCacheBundle {
   val l2_hint = Input(Valid(new L2ToL1Hint()))
   val cmoOpReq = Flipped(DecoupledIO(new CMOReq))
   val cmoOpResp = DecoupledIO(new CMOResp)
+  val l1Miss = Output(Bool())
 }
 
 private object ArbiterCtrl {
@@ -1676,6 +1677,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   XSPerfAccumulate("num_loads", num_loads)
 
   io.mshrFull := missQueue.io.full
+  io.l1Miss := missQueue.io.l1Miss
 
   // performance counter
   // val ld_access = Wire(Vec(LoadPipelineWidth, missQueue.io.debug_early_replace.last.cloneType))
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index f79050ae07a..00a19839c75 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -414,6 +414,7 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     }
     val nMaxPrefetchEntry = Input(UInt(64.W))
     val matched = Output(Bool())
+    val l1Miss = Output(Bool())
   })
 
   assert(!RegNext(io.primary_valid && !io.primary_ready))
@@ -846,6 +847,7 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     prefetch := false.B
   }
 
+  io.l1Miss := req_valid
   // refill latency monitor
   val start_counting = GatedValidRegNext(io.mem_acquire.fire) || (GatedValidRegNextN(primary_fire, 2) && s_acquire)
   io.latency_monitor.load_miss_refilling  := req_valid && req_primary_fire.isFromLoad     && BoolStopWatch(start_counting, io.mem_grant.fire && !refill_done, true, true)
@@ -948,6 +950,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val mq_enq_cancel = Output(Bool())
 
     val debugTopDown = new DCacheTopDownIO
+    val l1Miss = Output(Bool())
   })
 
   // 128KBL1: FIXME: provide vaddr for l2
@@ -1199,6 +1202,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   XSPerfAccumulate("max_inflight", max_inflight)
   QueuePerf(cfg.nMissEntries, num_valids, num_valids === cfg.nMissEntries.U)
   io.full := num_valids === cfg.nMissEntries.U
+  io.l1Miss := RegNext(Cat(entries.map(_.io.l1Miss)).orR)
   XSPerfHistogram("num_valids", num_valids, true.B, 0, cfg.nMissEntries, 1)
 
   XSPerfHistogram("L1DMLP_CPUData", PopCount(VecInit(entries.map(_.io.perf_pending_normal)).asUInt), true.B, 0, cfg.nMissEntries, 1)
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 5491c6a2e80..fd416a38cbc 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -410,8 +410,6 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
 
   itlbRepeater1.io.debugTopDown.robHeadVaddr := io.debugTopDown.robHeadVaddr
 
-  val frontendBubble = Mux(io.backend.canAccept, DecodeWidth.U - PopCount(ibuffer.io.out.map(_.valid)), 0.U)
-  XSPerfAccumulate("FrontendBubble", frontendBubble)
   io.frontendInfo.ibufFull := RegNext(ibuffer.io.full)
   io.resetInFrontend       := reset.asBool
 
diff --git a/src/main/scala/xiangshan/frontend/IBuffer.scala b/src/main/scala/xiangshan/frontend/IBuffer.scala
index c33c3bfb245..808b1a93415 100644
--- a/src/main/scala/xiangshan/frontend/IBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/IBuffer.scala
@@ -479,6 +479,11 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
 
   val FrontBubble = Mux(decodeCanAccept, DecodeWidth.U - numOut, 0.U)
 
+  val fetchLatency = decodeCanAccept && !headBubble && numOut === 0.U
+
+  XSPerfAccumulate("if_fetch_bubble", FrontBubble)
+  XSPerfAccumulate("if_fetch_bubble_eq_max", fetchLatency)
+
   val perfEvents = Seq(
     ("IBuffer_Flushed  ", io.flush),
     ("IBuffer_hungry   ", instrHungry),
@@ -487,7 +492,8 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
     ("IBuffer_3_4_valid", (numValid >= (2 * (IBufSize / 4)).U) & (numValid < (3 * (IBufSize / 4)).U)),
     ("IBuffer_4_4_valid", (numValid >= (3 * (IBufSize / 4)).U) & (numValid < (4 * (IBufSize / 4)).U)),
     ("IBuffer_full     ", numValid.andR),
-    ("Front_Bubble     ", FrontBubble)
+    ("Front_Bubble     ", FrontBubble),
+    ("Fetch_Latency_Bound", fetchLatency)
   )
   generatePerfEvent()
 }
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index 4103509d826..cf84ba152d3 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -128,6 +128,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
 
     // top-down
     val debugTopDown = new LoadQueueTopDownIO
+    val noUopsIssued = Input(Bool())
   })
 
   val loadQueue = Module(new LoadQueue)
@@ -288,6 +289,7 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
   }
 
   loadQueue.io.debugTopDown <> io.debugTopDown
+  loadQueue.io.noUopsIssed := io.noUopsIssued
 
   assert(!(loadQueue.io.uncache.resp.valid && storeQueue.io.uncache.resp.valid))
   when (!io.uncacheOutstanding) {
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index 460de7bf56d..77e5debef95 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -204,6 +204,7 @@ class LoadQueue(implicit p: Parameters) extends XSModule
     val lqDeqPtr = Output(new LqPtr)
 
     val debugTopDown = new LoadQueueTopDownIO
+    val noUopsIssed = Input(Bool())
   })
 
   val loadQueueRAR = Module(new LoadQueueRAR)  //  read-after-read violation
@@ -332,6 +333,8 @@ class LoadQueue(implicit p: Parameters) extends XSModule
 
   loadQueueReplay.io.debugTopDown <> io.debugTopDown
 
+  virtualLoadQueue.io.noUopsIssued := io.noUopsIssed
+
   val full_mask = Cat(loadQueueRAR.io.lqFull, loadQueueRAW.io.lqFull, loadQueueReplay.io.lqFull)
   XSPerfAccumulate("full_mask_000", full_mask === 0.U)
   XSPerfAccumulate("full_mask_001", full_mask === 1.U)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala
index ef87aa45b6d..9b6817ffbfe 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala
@@ -52,6 +52,8 @@ class VirtualLoadQueue(implicit p: Parameters) extends XSModule
     // to dispatch
     val lqDeq       = Output(UInt(log2Up(CommitWidth + 1).W))
     val lqCancelCnt = Output(UInt(log2Up(VirtualLoadQueueSize+1).W))
+    // for topdown
+    val noUopsIssued = Input(Bool())
   })
 
   println("VirtualLoadQueue: size: " + VirtualLoadQueueSize)
@@ -276,7 +278,18 @@ class VirtualLoadQueue(implicit p: Parameters) extends XSModule
   val vecValidVec = WireInit(VecInit((0 until VirtualLoadQueueSize).map(i => allocated(i) && isvec(i))))
   QueuePerf(VirtualLoadQueueSize, PopCount(vecValidVec), !allowEnqueue)
   io.lqFull := !allowEnqueue
-  val perfEvents: Seq[(String, UInt)] = Seq()
+
+  def NLoadNotCompleted = 1
+  val validCountReg = RegNext(validCount)
+  val noUopsIssued = io.noUopsIssued
+  val stallLoad = io.noUopsIssued && (validCountReg >= NLoadNotCompleted.U)
+  val memStallAnyLoad = RegNext(stallLoad)
+
+  XSPerfAccumulate("mem_stall_anyload", memStallAnyLoad)
+
+  val perfEvents: Seq[(String, UInt)] = Seq(
+    ("MEMSTALL_ANY_LOAD", memStallAnyLoad),
+  )
   generatePerfEvent()
 
   // debug info
```
