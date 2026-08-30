# Commit Log
- Issue: #5413
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5413
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5413
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5413
- Changed files: 26
- Additions: 154
- Deletions: 151

## Files
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/Fence.scala`
- `src/main/scala/xiangshan/backend/fu/FuncUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VfofBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index fdf96786fc7..b6fca914521 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -249,7 +249,7 @@ object Bundles {
     val debug_seqNum = InstSeqNum()
     val instr = UInt(32.W)
     val fusionNum = UInt(2.W)
-    val debugInfo = new PerfDebugInfo
+    val perfDebugInfo = new PerfDebugInfo
     val debug_sim_trig = Bool()
   }
 
@@ -484,7 +484,7 @@ object Bundles {
 
     // Take snapshot at this CFI inst
     val snapshot        = Bool()
-    val debugInfo       = new PerfDebugInfo
+    val perfDebugInfo   = new PerfDebugInfo
     val debug_seqNum    = InstSeqNum()
     val storeSetHit     = Bool() // inst has been allocated an store set
     val waitForRobIdx   = new RobPtr // store set predicted previous store robIdx
@@ -542,7 +542,7 @@ object Bundles {
         this.debug_seqNum := x.debug_seqNum
         this.instr := x.instr
         this.fusionNum := x.fusionNum
-        this.debugInfo := x.debugInfo
+        this.perfDebugInfo := x.perfDebugInfo
         this.debug_sim_trig.get := x.debug_sim_trig
       })
     }
@@ -918,8 +918,8 @@ object Bundles {
     val srcTimer = OptionWrapper(params.isIQWakeUpSink, Vec(params.numRegSrc, UInt(3.W)))
     val loadDependency = OptionWrapper(params.needLoadDependency, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
 
-    val perfDebugInfo = new PerfDebugInfo()
-    val debug_seqNum = InstSeqNum()
+    val perfDebugInfo = OptionWrapper(backendParams.debugEn, new PerfDebugInfo())
+    val debug_seqNum  = OptionWrapper(backendParams.debugEn, InstSeqNum())
 
     def exuIdx = this.params.exuIdx
 
@@ -946,7 +946,7 @@ object Bundles {
       this.isFirstIssue  := source.common.isFirstIssue // Only used by mem debug log
       this.iqIdx         := source.common.iqIdx        // Only used by mem feedback
       this.dataSources   := source.common.dataSources
-      this.debug_seqNum  := source.common.debug_seqNum
+      this.debug_seqNum  .foreach(_ := source.common.debug_seqNum.get)
       this.exuSources    .foreach(_ := source.common.exuSources.get)
       this.rfWen         .foreach(_ := source.common.rfWen.get)
       this.fpWen         .foreach(_ := source.common.fpWen.get)
@@ -999,8 +999,8 @@ object Bundles {
       uop.sqIdx          := this.sqIdx.getOrElse(0.U.asTypeOf(new SqPtr))
       uop.ftqPtr         := this.ftqIdx.getOrElse(0.U.asTypeOf(new FtqPtr))
       uop.ftqOffset      := this.ftqOffset.getOrElse(0.U)
-      uop.debugInfo      := this.perfDebugInfo
-      uop.debug_seqNum   := this.debug_seqNum
+      uop.perfDebugInfo      := this.perfDebugInfo.getOrElse(0.U.asTypeOf(new PerfDebugInfo))
+      uop.debug_seqNum   := this.debug_seqNum.getOrElse(0.U.asTypeOf(InstSeqNum()))
       uop.vpu            := this.vpu.getOrElse(0.U.asTypeOf(new VPUCtrlSignals))
       uop.isRVC          := this.isRVC.getOrElse(false.B)
       uop.rasAction      := this.rasAction.getOrElse(0.U)
@@ -1053,8 +1053,8 @@ object Bundles {
     // isFromLoadUnit indicates whether this ExuOutput is issued from LoadUnit (e.g., not so for atomics)
     val isFromLoadUnit = if (params.hasLoadFu) Some(Bool()) else None
     val debug = new DebugBundle
-    val debugInfo = new PerfDebugInfo
-    val debug_seqNum = InstSeqNum()
+    val perfDebugInfo = OptionWrapper(backendParams.debugEn, new PerfDebugInfo())
+    val debug_seqNum = OptionWrapper(backendParams.debugEn, InstSeqNum())
   }
 
   // ExuOutput + DynInst --> WriteBackBundle
@@ -1074,8 +1074,8 @@ object Bundles {
     val vxsat = Bool()
     val exceptionVec = ExceptionVec()
     val debug = new DebugBundle
-    val debugInfo = new PerfDebugInfo
-    val debug_seqNum = InstSeqNum()
+    val perfDebugInfo = OptionWrapper(backendParams.debugEn, new PerfDebugInfo())
+    val debug_seqNum = OptionWrapper(backendParams.debugEn, InstSeqNum())
 
     this.wakeupSource = s"WB(${params.toString})"
 
@@ -1097,8 +1097,8 @@ object Bundles {
       this.vxsat := source.vxsat.getOrElse(0.U.asTypeOf(this.vxsat))
       this.exceptionVec := source.exceptionVec.getOrElse(0.U.asTypeOf(this.exceptionVec))
       this.debug := source.debug
-      this.debugInfo := source.debugInfo
-      this.debug_seqNum := source.debug_seqNum
+      this.perfDebugInfo.foreach(_ := source.perfDebugInfo.get)
+      this.debug_seqNum.foreach(_ := source.debug_seqNum.get)
     }
 
     def asIntRfWriteBundle(fire: Bool): RfWritePortBundle = {
@@ -1273,8 +1273,8 @@ object Bundles {
       output.flushPipe.foreach(_ := this.uop.flushPipe)
       output.replay.foreach(_ := this.uop.replayInst)
       output.debug := this.debug
-      output.debugInfo := this.uop.debugInfo
-      output.debug_seqNum := this.uop.debug_seqNum
+      output.perfDebugInfo.foreach(_ := this.uop.perfDebugInfo)
+      output.debug_seqNum.foreach(_ := this.uop.debug_seqNum)
       output.lqIdx.foreach(_ := this.uop.lqIdx)
       output.sqIdx.foreach(_ := this.uop.sqIdx)
       output.isRVC.foreach(_ := this.uop.isRVC)
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 723c6e033ee..6c6cd994879 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -137,7 +137,7 @@ class CtrlBlockImp(
     val delayed = Wire(Valid(new ExuOutput(x.bits.params)))
     delayed.valid := GatedValidRegNext(valid && !killedByOlder)
     delayed.bits := RegEnable(x.bits, x.valid)
-    delayed.bits.debugInfo.writebackTime := GTimer()
+    delayed.bits.perfDebugInfo.foreach(_.writebackTime := GTimer())
     delayed
   }).toSeq
   private val delayedWriteBack = Wire(chiselTypeOf(io.fromWB.wbData))
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index 3663fc2dea0..3431c03187f 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -141,10 +141,8 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     stdiq.io.vlFromVfIsVlmax := false.B
     }
   }
-  issueQueues.filter(_.param.needUncertainWakeupFromExu).zip(exuBlock.io.uncertainWakeupOut.get).map { case (iq, exuWakeUpIn) =>
-    iq.io.wakeupFromExu.get.map(x => x.valid := false.B)
-    iq.io.wakeupFromExu.get.map(x => x.bits := 0.U.asTypeOf(x.bits))
-    iq.io.wakeupFromExu.get.head <> exuWakeUpIn
+  issueQueues.filter(_.param.needUncertainWakeupFromExu).map(_.io.wakeupFromExu.get).flatten.zip(exuBlock.io.uncertainWakeupOut.get).map { case (iq, exuWakeUpIn) =>
+    iq <> exuWakeUpIn
   }
   val iqWakeUpOutMap: Map[Int, ValidIO[IssueQueueIQWakeUpBundle]] =
     issueQueues.flatMap(_.io.wakeupToIQ)
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 62cc40dc082..97ac0945b3c 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -584,8 +584,8 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
       val s1_data = s1_toExuData(i)(j)
       val s1_addrOH = s1_addrOHs(i)(j)
       val s0 = fromIQ(i)(j) // s0
-      PerfCCT.updateInstPos(s0.bits.common.debug_seqNum, PerfCCT.InstPos.AtIssueArb.id.U, s0.valid, clock, reset)
-      PerfCCT.updateInstPos(s1_data.debug_seqNum, PerfCCT.InstPos.AtIssueReadReg.id.U, s1_valid, clock, reset)
+      s0.bits.common.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtIssueArb.id.U, s0.valid, clock, reset))
+      s1_data.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtIssueReadReg.id.U, s1_valid, clock, reset))
 
       val srcNotBlock = Wire(Bool())
       srcNotBlock := s0.bits.common.dataSources.zip(intRdArbWinner(i)(j) zip fpRdArbWinner(i)(j) zip vfRdArbWinner(i)(j) zip v0RdArbWinner(i)(j) zip vlRdArbWinner(i)(j)).map {
diff --git a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
index 33650ee56ae..6b54f9741b3 100644
--- a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
@@ -170,8 +170,10 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
   // update isrvc to dispatch: branch need last isrvc, rob need first isrvc as rob should attach interrupt to first uop
   for (i <- 0 until RenameWidth) {
     fromRenameUpdate(i).valid := fromRename(i).valid
-    // srcLoadDependency and srcState
-    fromRenameUpdate(i).bits := 0.U.asTypeOf(fromRenameUpdate(i).bits)
+    // v0 and vl don't need srcLoadDependency, srcState unpdated with allSrcState
+    fromRenameUpdate(i).bits.srcLoadDependency(3) := 0.U.asTypeOf(fromRenameUpdate(i).bits.srcLoadDependency(3))
+    fromRenameUpdate(i).bits.srcLoadDependency(4) := 0.U.asTypeOf(fromRenameUpdate(i).bits.srcLoadDependency(4))
+    fromRenameUpdate(i).bits.srcState := 0.U.asTypeOf(fromRenameUpdate(i).bits.srcState)
     connectSamePort(fromRenameUpdate(i).bits, fromRename(i).bits)
     fromRenameUpdate(i).bits.debug.foreach(connectSamePort(_, fromRename(i).bits.debug.get))
     fromRenameUpdate(i).bits.ftqOffset := fromRename(i).bits.ftqLastOffset
@@ -286,7 +288,6 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     val readAddr = VecInit(fromRename.map(x => x.bits.psrc.zipWithIndex.filter(xx => idxseq.contains(xx._2)).map(_._1)).flatten)
     val readValid = VecInit(fromRename.map(x => x.bits.psrc.zipWithIndex.filter(xx => idxseq.contains(xx._2)).map(y => x.valid && SrcType.isXp(x.bits.srcType(y._2)))).flatten)
     b.io.read.map(_.req).zip(readAddr).map(x => x._1 := x._2)
-    // only int src need srcLoadDependency, src0 src1
     if (i == 0) {
       val srcLoadDependencyUpdate = fromRenameUpdate.map(x => x.bits.srcLoadDependency)
       val srcType = fromRenameUpdate.map(x => x.bits.srcType)
@@ -765,7 +766,7 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
   for (i <- 0 until RenameWidth) {
 
     updatedUop(i).connectRenameOutUop(fromRename(i).bits)
-    updatedUop(i).debugInfo.eliminatedMove := fromRename(i).bits.isMove
+    updatedUop(i).perfDebugInfo.eliminatedMove := fromRename(i).bits.isMove
     // For the LUI instruction: psrc(0) is from register file and should always be zero.
     when (fromRename(i).bits.isLUI) {
       updatedUop(i).psrc(0) := 0.U
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
index bfe0b7076f5..534d14490fa 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
@@ -30,6 +30,7 @@ import xiangshan.backend.fu.vector.Bundles.{VType, Vxrm}
 import xiangshan.backend.fu.fpu.Bundles.Frm
 import xiangshan.backend.fu.wrapper.{CSRInput, CSRToDecode}
 import xiangshan.backend.fu.FuConfig.{AluCfg, I2fCfg, needUncertainWakeupFuConfigs}
+import xiangshan._
 
 class ExeUnitIO(params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
   val flush = Flipped(ValidIO(new Redirect()))
@@ -195,8 +196,8 @@ class ExeUnitImp(implicit p: Parameters, val exuParams: ExeUnitParams) extends X
       sink.bits.ctrl.vpu         .foreach(x => x.fpu.isFpToVecInst := 0.U)
       sink.bits.ctrl.vpu         .foreach(x => x.fpu.isFP32Instr   := 0.U)
       sink.bits.ctrl.vpu         .foreach(x => x.fpu.isFP64Instr   := 0.U)
-      sink.bits.perfDebugInfo    := source.bits.perfDebugInfo
-      sink.bits.debug_seqNum     := source.bits.debug_seqNum
+      sink.bits.perfDebugInfo    .foreach(_ := source.bits.perfDebugInfo.get)
+      sink.bits.debug_seqNum     .foreach(_ := source.bits.debug_seqNum.get)
   }
   funcUnits.filter(_.cfg.latency.latencyVal.nonEmpty).map{ fu =>
     val latency = fu.cfg.latency.latencyVal.getOrElse(0)
@@ -417,8 +418,8 @@ class ExeUnitImp(implicit p: Parameters, val exuParams: ExeUnitParams) extends X
   // debug info
   io.out.bits.debug     := 0.U.asTypeOf(io.out.bits.debug)
   io.out.bits.debug.isPerfCnt := funcUnits.map(_.io.csrio.map(_.isPerfCnt)).map(_.getOrElse(false.B)).reduce(_ || _)
-  io.out.bits.debugInfo := Mux1H(fuOutValidOH, fuOutBitsVec.map(_.perfDebugInfo))
-  io.out.bits.debug_seqNum := Mux1H(fuOutValidOH, fuOutBitsVec.map(_.debug_seqNum))
+  io.out.bits.perfDebugInfo.foreach(_ := Mux1H(fuOutValidOH, fuOutBitsVec.map(_.perfDebugInfo.getOrElse(0.U.asTypeOf(new PerfDebugInfo)))))
+  io.out.bits.debug_seqNum.foreach(_ := Mux1H(fuOutValidOH, fuOutBitsVec.map(_.debug_seqNum.getOrElse(0.U.asTypeOf(InstSeqNum())))))
 }
 
 class DispatcherIO[T <: Data](private val gen: T, n: Int) extends Bundle {
diff --git a/src/main/scala/xiangshan/backend/fu/Fence.scala b/src/main/scala/xiangshan/backend/fu/Fence.scala
index 0e1bdf81967..a883611a1d5 100644
--- a/src/main/scala/xiangshan/backend/fu/Fence.scala
+++ b/src/main/scala/xiangshan/backend/fu/Fence.scala
@@ -87,8 +87,8 @@ class Fence(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   io.out.bits.ctrl.pdest := uop.ctrl.pdest
   io.out.bits.ctrl.flushPipe.get := uop.ctrl.flushPipe.get
   io.out.bits.ctrl.exceptionVec.get := 0.U.asTypeOf(io.out.bits.ctrl.exceptionVec.get)
-  io.out.bits.perfDebugInfo := io.in.bits.perfDebugInfo
-  io.out.bits.debug_seqNum := io.in.bits.debug_seqNum
+  io.out.bits.perfDebugInfo.foreach(_ := io.in.bits.perfDebugInfo.get)
+  io.out.bits.debug_seqNum.foreach(_ := io.in.bits.debug_seqNum.get)
 
   XSDebug(io.in.valid, p"In(${io.in.valid} ${io.in.ready}) state:${state} InrobIdx:${io.in.bits.ctrl.robIdx}\n")
   XSDebug(state =/= s_idle, p"state:${state} sbuffer(flush:${sbuffer} empty:${sbEmpty}) fencei:${fencei} sfence:${sfence}\n")
diff --git a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
index ea51bd84e73..87f1032efa6 100644
--- a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
@@ -108,15 +108,15 @@ class FuncUnitInput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle {
   val validPipe = OptionWrapper(needCtrlPipe, Vec(cfg.latency.latencyVal.get + 1, Bool()))
   val data = new FuncUnitDataInput(cfg)
   val dataPipe = OptionWrapper(needCtrlPipe, Vec(cfg.latency.latencyVal.get + 1, new FuncUnitDataInput(cfg)))
-  val perfDebugInfo = new PerfDebugInfo()
-  val debug_seqNum = InstSeqNum()
+  val perfDebugInfo = OptionWrapper(backendParams.debugEn, new PerfDebugInfo())
+  val debug_seqNum = OptionWrapper(backendParams.debugEn, InstSeqNum())
 }
 
 class FuncUnitOutput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle {
   val ctrl = new FuncUnitCtrlOutput(cfg)
   val res = new FuncUnitDataOutput(cfg)
-  val perfDebugInfo = new PerfDebugInfo()
-  val debug_seqNum = InstSeqNum()
+  val perfDebugInfo = OptionWrapper(backendParams.debugEn, new PerfDebugInfo())
+  val debug_seqNum = OptionWrapper(backendParams.debugEn, InstSeqNum())
 }
 
 class FuncUnitIO(cfg: FuConfig)(implicit p: Parameters) extends XSBundle {
@@ -142,8 +142,8 @@ class FuncUnitIO(cfg: FuConfig)(implicit p: Parameters) extends XSBundle {
 
 abstract class FuncUnit(val cfg: FuConfig)(implicit p: Parameters) extends XSModule with HasCriticalErrors {
   val io = IO(new FuncUnitIO(cfg))
-  PerfCCT.updateInstPos(io.in.bits.debug_seqNum, PerfCCT.InstPos.AtFU.id.U, io.in.valid, clock, reset)
-  PerfCCT.updateInstPos(io.out.bits.debug_seqNum, PerfCCT.InstPos.AtBypassVal.id.U, io.out.valid, clock, reset)
+  io.in.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.in.valid, clock, reset))
+  io.out.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtBypassVal.id.U, io.out.valid, clock, reset))
   val criticalErrors = Seq(("none", false.B))
 
   // should only be used in non-piped fu
@@ -159,8 +159,8 @@ abstract class FuncUnit(val cfg: FuConfig)(implicit p: Parameters) extends XSMod
     io.out.bits.ctrl.isRVC.foreach(_ := RegEnable(io.in.bits.ctrl.isRVC.get, io.in.fire))
     io.out.bits.ctrl.fpu      .foreach(_ := RegEnable(io.in.bits.ctrl.fpu.get, io.in.fire))
     io.out.bits.ctrl.vpu      .foreach(_ := RegEnable(io.in.bits.ctrl.vpu.get, io.in.fire))
-    io.out.bits.perfDebugInfo := RegEnable(io.in.bits.perfDebugInfo, io.in.fire)
-    io.out.bits.debug_seqNum := RegEnable(io.in.bits.debug_seqNum, io.in.fire)
+    io.out.bits.perfDebugInfo.foreach(_ := RegEnable(io.in.bits.perfDebugInfo.get, io.in.fire))
+    io.out.bits.debug_seqNum.foreach(_ := RegEnable(io.in.bits.debug_seqNum.get, io.in.fire))
   }
 
   def connectNonPipedCtrlDataHoldBypass: Unit = {
@@ -175,8 +175,8 @@ abstract class FuncUnit(val cfg: FuConfig)(implicit p: Parameters) extends XSMod
     io.out.bits.ctrl.isRVC.foreach(_ := DataHoldBypass(io.in.bits.ctrl.isRVC.get, io.in.fire))
     io.out.bits.ctrl.fpu.foreach(_ := DataHoldBypass(io.in.bits.ctrl.fpu.get, io.in.fire))
     io.out.bits.ctrl.vpu.foreach(_ := DataHoldBypass(io.in.bits.ctrl.vpu.get, io.in.fire))
-    io.out.bits.perfDebugInfo := DataHoldBypass(io.in.bits.perfDebugInfo, io.in.fire)
-    io.out.bits.debug_seqNum := DataHoldBypass(io.in.bits.debug_seqNum, io.in.fire)
+    io.out.bits.perfDebugInfo.foreach(_ := DataHoldBypass(io.in.bits.perfDebugInfo.get, io.in.fire))
+    io.out.bits.debug_seqNum.foreach(_ := DataHoldBypass(io.in.bits.debug_seqNum.get, io.in.fire))
   }
 
   def connect0LatencyCtrlSingal: Unit = {
@@ -191,8 +191,8 @@ abstract class FuncUnit(val cfg: FuConfig)(implicit p: Parameters) extends XSMod
     io.out.bits.ctrl.isRVC.foreach(_ := io.in.bits.ctrl.isRVC.get)
     io.out.bits.ctrl.fpu.foreach(_ := io.in.bits.ctrl.fpu.get)
     io.out.bits.ctrl.vpu.foreach(_ := io.in.bits.ctrl.vpu.get)
-    io.out.bits.perfDebugInfo := io.in.bits.perfDebugInfo
-    io.out.bits.debug_seqNum := io.in.bits.debug_seqNum
+    io.out.bits.perfDebugInfo.foreach(_ := io.in.bits.perfDebugInfo.get)
+    io.out.bits.debug_seqNum.foreach(_ := io.in.bits.debug_seqNum.get)
   }
   io.outValidAhead3Cycle.foreach{x =>
     println(s"${cfg.name}: has outValidAhead3Cycle")
@@ -215,8 +215,8 @@ trait HasPipelineReg { this: FuncUnit =>
     val validVec = valid +: Seq.fill(latency)(RegInit(false.B))
     val ctrlVec = init.ctrl +: Seq.fill(latency)(Reg(chiselTypeOf(io.in.bits.ctrl)))
     val dataVec = init.data +: Seq.fill(latency)(Reg(chiselTypeOf(io.in.bits.data)))
-    val perfVec = init.perfDebugInfo +: Seq.fill(latency)(Reg(chiselTypeOf(io.in.bits.perfDebugInfo)))
-    val seqNumVec = init.debug_seqNum +: Seq.fill(latency)(Reg(chiselTypeOf(io.in.bits.debug_seqNum)))
+    val perfVec = init.perfDebugInfo.map(_ +: Seq.fill(latency)(Reg(chiselTypeOf(io.in.bits.perfDebugInfo.get))))
+    val seqNumVec = init.debug_seqNum.map(_ +: Seq.fill(latency)(Reg(chiselTypeOf(io.in.bits.debug_seqNum.get))))
 
     val robIdxVec = ctrlVec.map(_.robIdx)
 
@@ -231,21 +231,21 @@ trait HasPipelineReg { this: FuncUnit =>
       when(rdyVec(i - 1) && validVec(i - 1)) {
         ctrlVec(i) := ctrlVec(i - 1)
         dataVec(i) := dataVec(i - 1)
-        perfVec(i) := perfVec(i - 1)
-        seqNumVec(i) := seqNumVec(i-1)
+        perfVec.foreach(_(i) := perfVec.get(i - 1))
+        seqNumVec.foreach(_(i) := seqNumVec.get(i-1))
       }
     }
 
-    (ctrlVec.zip(dataVec).zip(perfVec).zip(seqNumVec).map{
-      case(((ctrl,data), perf), debug_seqNum) => {
+    (ctrlVec.zip(dataVec).zipWithIndex.map{
+      case((ctrl,data), i) => {
         val out = Wire(new FuncUnitInput(cfg))
         out.ctrl := ctrl
         out.ctrlPipe.foreach(_ := 0.U.asTypeOf(out.ctrlPipe.get))
         out.validPipe.foreach(_ := 0.U.asTypeOf(out.validPipe.get))
         out.dataPipe.foreach(_ := 0.U.asTypeOf(out.dataPipe.get))
         out.data := data
-        out.perfDebugInfo := perf
-        out.debug_seqNum := debug_seqNum
+        out.perfDebugInfo.foreach(_ := perfVec.get(i))
+        out.debug_seqNum.foreach(_ := seqNumVec.get(i))
         out
       }
     },validVec, rdyVec)
@@ -264,8 +264,8 @@ trait HasPipelineReg { this: FuncUnit =>
   fixtiminginit.validPipe.foreach(_ := 0.U.asTypeOf(fixtiminginit.validPipe.get))
   fixtiminginit.dataPipe.foreach(_ := 0.U.asTypeOf(fixtiminginit.dataPipe.get))
   fixtiminginit.data := dataVec.last
-  fixtiminginit.perfDebugInfo := perfVec.last
-  fixtiminginit.debug_seqNum := seqNumVec.last
+  fixtiminginit.perfDebugInfo.foreach(_ := perfVec.last.get)
+  fixtiminginit.debug_seqNum.foreach(_ := seqNumVec.last.get)
 
   // fixtiming pipelinereg
   val (fixpipeReg : Seq[FuncUnitInput], fixValidVec, fixRdyVec) = pipelineReg(fixtiminginit, validVec.last,rdyVec.head ,latdiff, io.flush)
@@ -286,8 +286,8 @@ trait HasPipelineReg { this: FuncUnit =>
   io.out.bits.ctrl.vlWen.foreach(_ := ctrlVec.last.vlWen.get)
   io.out.bits.ctrl.fpu.foreach(_ := ctrlVec.last.fpu.get)
   io.out.bits.ctrl.vpu.foreach(_ := ctrlVec.last.vpu.get)
-  io.out.bits.perfDebugInfo := fixPerfVec.last
-  io.out.bits.debug_seqNum := fixSeqNumVec.last
+  io.out.bits.perfDebugInfo.foreach(_ := fixPerfVec.last.get)
+  io.out.bits.debug_seqNum.foreach(_ := fixSeqNumVec.last.get)
 
   // vstart illegal
   if (cfg.exceptionOut.nonEmpty) {
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 4d40bf3cb4e..21b4077db7d 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -318,10 +318,10 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   io.out.bits.ctrl.rfWen.foreach(_ := Mux(isXRetReg, rfWenReg, DelayNWithValid(rfWenReg, csrModOutValid, 3)._2))
   val isRVCReg = RegEnable(io.in.bits.ctrl.isRVC.get, io.in.fire)
   io.out.bits.ctrl.isRVC.foreach(_ := Mux(isXRetReg, isRVCReg, DelayNWithValid(isRVCReg, csrModOutValid, 3)._2))
-  val perfDebugInfoReg = RegEnable(io.in.bits.perfDebugInfo, io.in.fire)
-  io.out.bits.perfDebugInfo := Mux(isXRetReg, perfDebugInfoReg, DelayNWithValid(perfDebugInfoReg, csrModOutValid, 3)._2)
-  val debug_seqNumReg = RegEnable(io.in.bits.debug_seqNum, io.in.fire)
-  io.out.bits.debug_seqNum := Mux(isXRetReg, debug_seqNumReg, DelayNWithValid(debug_seqNumReg, csrModOutValid, 3)._2)
+  val perfDebugInfoReg = io.in.bits.perfDebugInfo.map(x => RegEnable(io.in.bits.perfDebugInfo.get, io.in.fire))
+  io.out.bits.perfDebugInfo.foreach(_ := Mux(isXRetReg, perfDebugInfoReg.get, DelayNWithValid(perfDebugInfoReg.get, csrModOutValid, 3)._2))
+  val debug_seqNumReg = io.in.bits.debug_seqNum.map(x => RegEnable(io.in.bits.debug_seqNum.get, io.in.fire))
+  io.out.bits.debug_seqNum.foreach(_ := Mux(isXRetReg, debug_seqNumReg.get, DelayNWithValid(debug_seqNumReg.get, csrModOutValid, 3)._2))
 
   override val criticalErrors = csrMod.getCriticalErrors
   generateCriticalErrors()
diff --git a/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala b/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
index 3920982b748..f854b4ae0d3 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
@@ -398,6 +398,15 @@ case class IssueBlockParams(
     MixedVec(exuBlockParams.filterNot(_.fakeUnit).map(x => ValidIO(new IssueQueueIssueBundle(this, x))))
   }
 
+  def genExuWakeUpOutValidBundle(implicit p: Parameters): MixedVec[DecoupledIO[IssueQueueIQWakeUpBundle]] = {
+    val uncertainExuParams = this.allExuParams.filter(_.needUncertainWakeup)
+    MixedVec(uncertainExuParams.map(param => {
+      val isCopyPdest = param.copyWakeupOut
+      val copyNum = param.copyNum
+      DecoupledIO(new IssueQueueIQWakeUpBundle(backendParam.getExuIdx(param.name), backendParam, isCopyPdest, copyNum))
+    }))
+  }
+
   def genWBWakeUpSinkValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = {
     val intBundle: Seq[ValidIO[IssueQueueWBWakeUpBundle]] = schdType match {
       case IntScheduler() => needWakeupFromIntWBPort.map(x => ValidIO(new IssueQueueWBWakeUpBundle(x._2.map(_.exuIdx), backendParam))).toSeq
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index de38a786308..b2003005096 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -38,7 +38,7 @@ class IssueQueueIO()(implicit p: Parameters, params: IssueBlockParams) extends X
   val wbBusyTableWrite = Output(params.genWbFuBusyTableWriteBundle)
   val wakeupFromWB: MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = Flipped(params.genWBWakeUpSinkValidBundle)
   val wakeupFromIQ: MixedVec[ValidIO[IssueQueueIQWakeUpBundle]] = Flipped(params.genIQWakeUpSinkValidBundle)
-  val wakeupFromExu: Option[MixedVec[DecoupledIO[IssueQueueIQWakeUpBundle]]] = Option.when(params.needUncertainWakeupFromExu)(Flipped(backendParams.schdParams(params.schdType).genExuWakeUpOutValidBundle))
+  val wakeupFromExu: Option[MixedVec[DecoupledIO[IssueQueueIQWakeUpBundle]]] = Option.when(params.needUncertainWakeupFromExu)(Flipped(params.genExuWakeUpOutValidBundle))
   val wakeupFromI2F: Option[ValidIO[IssueQueueIQWakeUpBundle]] = Option.when(params.needWakeupFromI2F)(Flipped(ValidIO(new IssueQueueIQWakeUpBundle(params.backendParam.getExuIdxI2F, params.backendParam))))
   val wakeupFromF2I: Option[ValidIO[IssueQueueIQWakeUpBundle]] = Option.when(params.needWakeupFromF2I)(Flipped(ValidIO(new IssueQueueIQWakeUpBundle(params.backendParam.getExuIdxF2I, params.backendParam))))
   val wakeupFromWBDelayed: MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = Flipped(params.genWBWakeUpSinkValidBundle)
@@ -913,14 +913,10 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     deq.bits.common.nextPcOffset.foreach(_ := 0.U)
     deq.bits.rcIdx.foreach(_ := deqEntryVec(i).bits.status.srcStatus.map(_.regCacheIdx.get))
 
-    deq.bits.common.perfDebugInfo := 0.U.asTypeOf(deq.bits.common.perfDebugInfo)
-    deq.bits.common.debug_seqNum := 0.U.asTypeOf(deq.bits.common.debug_seqNum)
-    deqEntryVec(i).bits.payload.debug.foreach(x => {
-      deq.bits.common.perfDebugInfo := x.perfDebugInfo
-      deq.bits.common.debug_seqNum := x.debug_seqNum
-    })
-    deq.bits.common.perfDebugInfo.selectTime := GTimer()
-    deq.bits.common.perfDebugInfo.issueTime := GTimer() + 1.U
+    deq.bits.common.perfDebugInfo.foreach(_ := deqEntryVec(i).bits.payload.debug.get.perfDebugInfo)
+    deq.bits.common.debug_seqNum.foreach(_ := deqEntryVec(i).bits.payload.debug.get.debug_seqNum)
+    deq.bits.common.perfDebugInfo.foreach(_.selectTime := GTimer())
+    deq.bits.common.perfDebugInfo.foreach(_.issueTime := GTimer() + 1.U)
   }
 
   val deqDelay = Reg(params.genIssueValidBundle)
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 9a11f695d2d..af4f7a882ce 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -422,7 +422,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     ))
 
     // Assign performance counters
-    uops(i).debug.foreach(_.debugInfo.renameTime := GTimer())
+    uops(i).debug.foreach(_.perfDebugInfo.renameTime := GTimer())
 
     io.out(i).valid := io.in(i).valid && intFreeList.io.canAllocate && fpFreeList.io.canAllocate && vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && !io.rabCommits.isWalk
     io.out(i).bits := uops(i)
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 47da19e7cd7..5dfadded8b5 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -147,10 +147,11 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val jmpWBs = io.exuWriteback.filter(_.bits.params.hasJmpFu).toSeq
   val csrWBs = io.exuWriteback.filter(x => x.bits.params.hasCSR).toSeq
 
-  PerfCCT.tick(clock, reset)
-
-  io.exuWriteback.zipWithIndex.foreach{ case (wb, i) =>
-    PerfCCT.updateInstPos(wb.bits.debug_seqNum, PerfCCT.InstPos.AtWriteVal.id.U, wb.valid, clock, reset)
+  if (backendParams.debugEn){
+    PerfCCT.tick(clock, reset)
+    io.exuWriteback.zipWithIndex.foreach{ case (wb, i) =>
+      PerfCCT.updateInstPos(wb.bits.debug_seqNum.get, PerfCCT.InstPos.AtWriteVal.id.U, wb.valid, clock, reset)
+    }
   }
 
   val numExuWbPorts = exuWBs.length
@@ -455,13 +456,13 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       val enqIndex = allocatePtrVec(i).value
       // store uop in data module and debug_microOp Vec
       debug_microOp(enqIndex) := enqUop
-      debug_microOp(enqIndex).debugInfo.dispatchTime := timer
-      debug_microOp(enqIndex).debugInfo.enqRsTime := timer
-      debug_microOp(enqIndex).debugInfo.selectTime := timer
-      debug_microOp(enqIndex).debugInfo.issueTime := timer
-      debug_microOp(enqIndex).debugInfo.writebackTime := timer
-      debug_microOp(enqIndex).debugInfo.tlbFirstReqTime := timer
-      debug_microOp(enqIndex).debugInfo.tlbRespTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.dispatchTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.enqRsTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.selectTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.issueTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.writebackTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.tlbFirstReqTime := timer
+      debug_microOp(enqIndex).perfDebugInfo.tlbRespTime := timer
       debug_lsInfo(enqIndex) := DebugLsInfo.init
       debug_lsTopdownInfo(enqIndex) := LsTopdownInfo.init
       debug_lqIdxValid(enqIndex) := false.B
@@ -561,13 +562,14 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     when(wb.valid) {
       debug_exuData(wbIdx) := wb.bits.data(0)
       debug_exuDebug(wbIdx) := wb.bits.debug
-      debug_microOp(wbIdx).debugInfo.enqRsTime := wb.bits.debugInfo.enqRsTime
-      debug_microOp(wbIdx).debugInfo.selectTime := wb.bits.debugInfo.selectTime
-      debug_microOp(wbIdx).debugInfo.issueTime := wb.bits.debugInfo.issueTime
-      debug_microOp(wbIdx).debugInfo.writebackTime := wb.bits.debugInfo.writebackTime
-      debug_microOp(wbIdx).debugInfo.tlbFirstReqTime := wb.bits.debugInfo.tlbFirstReqTime
-      debug_microOp(wbIdx).debugInfo.tlbRespTime := wb.bits.debugInfo.tlbRespTime
-
+      wb.bits.perfDebugInfo.foreach { x =>
+        debug_microOp(wbIdx).perfDebugInfo.enqRsTime := x.enqRsTime
+        debug_microOp(wbIdx).perfDebugInfo.selectTime := x.selectTime
+        debug_microOp(wbIdx).perfDebugInfo.issueTime := x.issueTime
+        debug_microOp(wbIdx).perfDebugInfo.writebackTime := x.writebackTime
+        debug_microOp(wbIdx).perfDebugInfo.tlbFirstReqTime := x.tlbFirstReqTime
+        debug_microOp(wbIdx).perfDebugInfo.tlbRespTime := x.tlbRespTime
+      }
       // debug for lqidx and sqidx
       debug_microOp(wbIdx).lqIdx := wb.bits.lqIdx.getOrElse(0.U.asTypeOf(new LqPtr))
       debug_microOp(wbIdx).sqIdx := wb.bits.sqIdx.getOrElse(0.U.asTypeOf(new SqPtr))
@@ -1384,14 +1386,14 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     XSPerfAccumulate(s"commitCompressCnt${i}", PopCount(io.commits.commitValid.zip(instrSizeCommit).map { case (valid, instrSize) => io.commits.isCommit && valid && instrSize === i.U }))
   )
   XSPerfAccumulate("compressSize", io.commits.commitValid.zip(instrSizeCommit).map { case (valid, instrSize) => Mux(io.commits.isCommit && valid && instrSize > 1.U, instrSize, 0.U) }.reduce(_ +& _))
-  val dispatchLatency = commitDebugUop.map(uop => uop.debugInfo.dispatchTime - uop.debugInfo.renameTime)
-  val enqRsLatency = commitDebugUop.map(uop => uop.debugInfo.enqRsTime - uop.debugInfo.dispatchTime)
-  val selectLatency = commitDebugUop.map(uop => uop.debugInfo.selectTime - uop.debugInfo.enqRsTime)
-  val issueLatency = commitDebugUop.map(uop => uop.debugInfo.issueTime - uop.debugInfo.selectTime)
-  val executeLatency = commitDebugUop.map(uop => uop.debugInfo.writebackTime - uop.debugInfo.issueTime)
-  val rsFuLatency = commitDebugUop.map(uop => uop.debugInfo.writebackTime - uop.debugInfo.enqRsTime)
-  val commitLatency = commitDebugUop.map(uop => timer - uop.debugInfo.writebackTime)
-  val tlbLatency = commitDebugUop.map(uop => uop.debugInfo.tlbRespTime - uop.debugInfo.tlbFirstReqTime)
+  val dispatchLatency = commitDebugUop.map(uop => uop.perfDebugInfo.dispatchTime - uop.perfDebugInfo.renameTime)
+  val enqRsLatency = commitDebugUop.map(uop => uop.perfDebugInfo.enqRsTime - uop.perfDebugInfo.dispatchTime)
+  val selectLatency = commitDebugUop.map(uop => uop.perfDebugInfo.selectTime - uop.perfDebugInfo.enqRsTime)
+  val issueLatency = commitDebugUop.map(uop => uop.perfDebugInfo.issueTime - uop.perfDebugInfo.selectTime)
+  val executeLatency = commitDebugUop.map(uop => uop.perfDebugInfo.writebackTime - uop.perfDebugInfo.issueTime)
+  val rsFuLatency = commitDebugUop.map(uop => uop.perfDebugInfo.writebackTime - uop.perfDebugInfo.enqRsTime)
+  val commitLatency = commitDebugUop.map(uop => timer - uop.perfDebugInfo.writebackTime)
+  val tlbLatency = commitDebugUop.map(uop => uop.perfDebugInfo.tlbRespTime - uop.perfDebugInfo.tlbFirstReqTime)
 
   def latencySum(cond: Seq[Bool], latency: Seq[UInt]): UInt = {
     cond.zip(latency).map(x => Mux(x._1, x._2, 0.U)).reduce(_ +& _)
@@ -1448,15 +1450,15 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
         debug_instData.robIdx := idx
         debug_instData.dvaddr := wb.bits.debug.vaddr
         debug_instData.dpaddr := wb.bits.debug.paddr
-        debug_instData.issueTime := wb.bits.debugInfo.issueTime
-        debug_instData.writebackTime := wb.bits.debugInfo.writebackTime
-        debug_instData.dispatchLatency := wb.bits.debugInfo.dispatchTime - wb.bits.debugInfo.renameTime
-        debug_instData.enqRsLatency := wb.bits.debugInfo.enqRsTime - wb.bits.debugInfo.dispatchTime
-        debug_instData.selectLatency := wb.bits.debugInfo.selectTime - wb.bits.debugInfo.enqRsTime
-        debug_instData.issueLatency := wb.bits.debugInfo.issueTime - wb.bits.debugInfo.selectTime
-        debug_instData.executeLatency := wb.bits.debugInfo.writebackTime - wb.bits.debugInfo.issueTime
-        debug_instData.rsFuLatency := wb.bits.debugInfo.writebackTime - wb.bits.debugInfo.enqRsTime
-        debug_instData.tlbLatency := wb.bits.debugInfo.tlbRespTime - wb.bits.debugInfo.tlbFirstReqTime
+        debug_instData.issueTime := wb.bits.perfDebugInfo.get.issueTime
+        debug_instData.writebackTime := wb.bits.perfDebugInfo.get.writebackTime
+        debug_instData.dispatchLatency := wb.bits.perfDebugInfo.get.dispatchTime - wb.bits.perfDebugInfo.get.renameTime
+        debug_instData.enqRsLatency := wb.bits.perfDebugInfo.get.enqRsTime - wb.bits.perfDebugInfo.get.dispatchTime
+        debug_instData.selectLatency := wb.bits.perfDebugInfo.get.selectTime - wb.bits.perfDebugInfo.get.enqRsTime
+        debug_instData.issueLatency := wb.bits.perfDebugInfo.get.issueTime - wb.bits.perfDebugInfo.get.selectTime
+        debug_instData.executeLatency := wb.bits.perfDebugInfo.get.writebackTime - wb.bits.perfDebugInfo.get.issueTime
+        debug_instData.rsFuLatency := wb.bits.perfDebugInfo.get.writebackTime - wb.bits.perfDebugInfo.get.enqRsTime
+        debug_instData.tlbLatency := wb.bits.perfDebugInfo.get.tlbRespTime - wb.bits.perfDebugInfo.get.tlbFirstReqTime
         debug_instData.exceptType := Cat(wb.bits.exceptionVec.getOrElse(ExceptionVec(false.B)))
         debug_instData.lsInfo := debug_lsInfo(idx)
         // debug_instData.globalID := wb.bits.uop.ctrl.debug_globalID
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index bd9622b5105..fda4e7a5c75 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -449,7 +449,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   val writebackVldu = vecWriteback.filter(_.bits.params.hasVLoadFu)
 
   writeback.zipWithIndex.foreach{ case (wb, i) =>
-    PerfCCT.updateInstPos(wb.bits.debug_seqNum, PerfCCT.InstPos.AtBypassVal.id.U, wb.valid, clock, reset)
+    wb.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtBypassVal.id.U, wb.valid, clock, reset))
   }
 
   dontTouch(io.inner_hartId)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index e51158cc6b7..f4448f2a394 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -582,14 +582,14 @@ class LoadMisalignBuffer(val param: ExeUnitParams)(implicit p: Parameters) exten
     x.isVlm := VlduType.isMasked(req.uop.fuOpType) && VlduType.isVecLd(req.uop.fuOpType)
   }
   io.writeBack.bits.isFromLoadUnit.get := true.B
-  io.writeBack.bits.debugInfo := req.uop.debugInfo
+  io.writeBack.bits.perfDebugInfo.foreach(_ := req.uop.perfDebugInfo)
   io.writeBack.bits.debug.isMMIO := globalMMIO
   io.writeBack.bits.debug.isNCIO := globalNC && !globalMemBackTypeMM
   io.writeBack.bits.debug.isPerfCnt := false.B
   io.writeBack.bits.debug.paddr := req.paddr
   io.writeBack.bits.debug.vaddr := req.vaddr
   io.writeBack.bits.debug.vaddr := req.vaddr
-  io.writeBack.bits.debug_seqNum := req.uop.debug_seqNum
+  io.writeBack.bits.debug_seqNum.foreach(_ := req.uop.debug_seqNum)
   // vector output
   io.vecWriteBack.valid := req_valid && (bufferState === s_wb) && req.isvec
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
index 97a503f70a7..60d11995b00 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
@@ -355,7 +355,7 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     redirect.bits.stFtqOffset := stFtqOffset(i)
     redirect.bits.level       := RedirectLevel.flush
     redirect.bits.target      := rollbackLqWb(i).bits.pc
-    redirect.bits.debug_runahead_checkpoint_id := rollbackLqWb(i).bits.debugInfo.runahead_checkpoint_id
+    redirect.bits.debug_runahead_checkpoint_id := rollbackLqWb(i).bits.perfDebugInfo.runahead_checkpoint_id
     redirect
   })
   io.rollback := allRedirect
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index 7990d256c38..f1f730a35d7 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -560,7 +560,7 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
     redirect.bits.ftqOffset   := reqSelUops(i).ftqOffset
     redirect.bits.level       := RedirectLevel.flush
     redirect.bits.target      := reqSelUops(i).pc // TODO: check if need pc
-    redirect.bits.debug_runahead_checkpoint_id := reqSelUops(i).debugInfo.runahead_checkpoint_id
+    redirect.bits.debug_runahead_checkpoint_id := reqSelUops(i).perfDebugInfo.runahead_checkpoint_id
     redirect
   })
   val oldestOneHot = selectOldestRedirect(allRedirect)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 8d067b3fd09..e72959c718a 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -615,8 +615,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   io.writeBack.bits.debug.isPerfCnt := false.B
   io.writeBack.bits.debug.paddr := req.paddr
   io.writeBack.bits.debug.vaddr := req.vaddr
-  io.writeBack.bits.debugInfo := req.uop.debugInfo
-  io.writeBack.bits.debug_seqNum := req.uop.debug_seqNum
+  io.writeBack.bits.perfDebugInfo.foreach(_  := req.uop.perfDebugInfo)
+  io.writeBack.bits.debug_seqNum.foreach(_  := req.uop.debug_seqNum)
 
   io.vecWriteBack.zipWithIndex.map{
     case (wb, index) => {
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index a88800f9e9b..f859fc97a81 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -567,7 +567,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     }
     when (io.storeAddrIn(i).fire) {
       uop(stWbIndex) := io.storeAddrIn(i).bits.uop
-      uop(stWbIndex).debugInfo := io.storeAddrIn(i).bits.uop.debugInfo
+      uop(stWbIndex).perfDebugInfo := io.storeAddrIn(i).bits.uop.perfDebugInfo
       uop(stWbIndex).debug_seqNum := io.storeAddrIn(i).bits.uop.debug_seqNum
     }
     XSInfo(io.storeAddrIn(i).fire && !io.storeAddrIn(i).bits.isFrmMisAlignBuf,
@@ -1062,8 +1062,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   io.mmioStout.bits.flushPipe.foreach(_ := deqCanDoCbo) // flush Pipeline to keep order in CMO
   io.mmioStout.bits.sqIdx.foreach(_ := deqPtrExt(0))
   io.mmioStout.bits.trigger.foreach(_ := uncacheUop.trigger)
-  io.mmioStout.bits.debugInfo := uncacheUop.debugInfo
-  io.mmioStout.bits.debug_seqNum := uncacheUop.debug_seqNum
+  io.mmioStout.bits.perfDebugInfo.foreach(_ := uncacheUop.perfDebugInfo)
+  io.mmioStout.bits.debug_seqNum.foreach(_ := uncacheUop.debug_seqNum)
   io.mmioStout.bits.debug.isMMIO := true.B
   io.mmioStout.bits.debug.isNCIO := false.B
   io.mmioStout.bits.debug.paddr := DontCare
@@ -1085,8 +1085,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   io.cboZeroStout.bits.flushPipe.foreach(_ := cboZeroUop.flushPipe) // false.B ?
   io.cboZeroStout.bits.sqIdx.foreach(_ := cboZeroSqIdx)
   io.cboZeroStout.bits.trigger.foreach(_ := cboZeroUop.trigger)
-  io.cboZeroStout.bits.debugInfo := cboZeroUop.debugInfo
-  io.cboZeroStout.bits.debug_seqNum := cboZeroUop.debug_seqNum
+  io.cboZeroStout.bits.perfDebugInfo.foreach(_ := cboZeroUop.perfDebugInfo)
+  io.cboZeroStout.bits.debug_seqNum.foreach(_ := cboZeroUop.debug_seqNum)
 
   when (cboZeroWaitFlushSb && io.flushSbuffer.empty) {
     cboZeroWaitFlushSb    := false.B
diff --git a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
index 96f89d449f3..6105ff4fdb8 100644
--- a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
@@ -60,9 +60,7 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
     })
     val csrCtrl       = Flipped(new CustomCSRCtrlIO)
   })
-
-  PerfCCT.updateInstPos(io.in.bits.debug_seqNum, PerfCCT.InstPos.AtFU.id.U, io.in.valid, clock, reset)
-
+  io.in.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.in.valid, clock, reset))
   //-------------------------------------------------------
   // Atomics Memory Accsess FSM
   //-------------------------------------------------------
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 0cf820ea963..b78e194b763 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -192,8 +192,7 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   })
 
 
-  PerfCCT.updateInstPos(io.ldin.bits.debug_seqNum, PerfCCT.InstPos.AtFU.id.U, io.ldin.valid, clock, reset)
-
+  io.ldin.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.ldin.valid, clock, reset))
   val s1_ready, s2_ready, s3_ready = WireInit(false.B)
 
   // Pipeline
@@ -764,9 +763,9 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   s0_out.isMisalign := (!s0_addr_aligned || s0_sel_src.uop.exceptionVec(loadAddrMisaligned)) && s0_sel_src.vecActive
   s0_out.forward_tlDchannel := s0_src_select_vec(super_rep_idx)
   when(io.tlb.req.valid && (s0_sel_src.isFirstIssue || s0_sel_src.repForTlbMiss)) {
-    s0_out.uop.debugInfo.tlbFirstReqTime := GTimer()
+    s0_out.uop.perfDebugInfo.tlbFirstReqTime := GTimer()
   }.otherwise{
-    s0_out.uop.debugInfo.tlbFirstReqTime := s0_sel_src.uop.debugInfo.tlbFirstReqTime
+    s0_out.uop.perfDebugInfo.tlbFirstReqTime := s0_sel_src.uop.perfDebugInfo.tlbFirstReqTime
   }
   s0_out.schedIndex     := s0_sel_src.sched_idx
   //for Svpbmt Nc
@@ -892,11 +891,11 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   s1_gpaddr_dup_lsu   := Mux(s1_in.isFastReplay, s1_in.paddr, io.tlb.resp.bits.gpaddr(0))
 
   when (io.tlb.resp.valid && !s1_tlb_miss) {
-    s1_out.uop.debugInfo.tlbRespTime := GTimer()
+    s1_out.uop.perfDebugInfo.tlbRespTime := GTimer()
   }.elsewhen (io.tlb.resp.valid && s1_tlb_miss) {
-    s1_out.uop.debugInfo.tlbRespTime := s1_in.uop.debugInfo.tlbFirstReqTime
+    s1_out.uop.perfDebugInfo.tlbRespTime := s1_in.uop.perfDebugInfo.tlbFirstReqTime
   }.otherwise {
-    s1_out.uop.debugInfo.tlbRespTime := s1_in.uop.debugInfo.tlbRespTime
+    s1_out.uop.perfDebugInfo.tlbRespTime := s1_in.uop.perfDebugInfo.tlbRespTime
   }
 
   io.tlb.req_kill   := s1_kill || s1_dly_err
@@ -965,7 +964,7 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   s1_out.isForVSnonLeafPTE := io.tlb.resp.bits.isForVSnonLeafPTE
   s1_out.tlbMiss           := s1_tlb_miss
   s1_out.ptwBack           := io.tlb.resp.bits.ptwBack
-  s1_out.rep_info.debug    := s1_in.uop.debugInfo
+  s1_out.rep_info.debug    := s1_in.uop.perfDebugInfo
   s1_out.rep_info.nuke     := s1_nuke && !s1_sw_prf
   s1_out.delayedLoadError  := s1_dly_err
   s1_out.nc := (s1_nc || Pbmt.isNC(s1_pbmt)) && !s1_prf
@@ -1327,7 +1326,7 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   s2_out.rep_info.rep_carry       := io.dcache.resp.bits.replayCarry
   s2_out.rep_info.mshr_id         := io.dcache.resp.bits.mshr_id
   s2_out.rep_info.last_beat       := s2_in.paddr(log2Up(refillBytes))
-  s2_out.rep_info.debug           := s2_in.uop.debugInfo
+  s2_out.rep_info.debug           := s2_in.uop.perfDebugInfo
   s2_out.rep_info.tlb_id          := io.tlb_hint.id
   s2_out.rep_info.tlb_full        := io.tlb_hint.full
 
@@ -1518,7 +1517,7 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   io.rollback.bits.ftqOffset := s3_out.bits.uop.ftqOffset
   io.rollback.bits.level     := Mux(s3_rep_frm_fetch || s3_frm_mis_flush, RedirectLevel.flush, RedirectLevel.flushAfter)
   io.rollback.bits.target    := s3_out.bits.uop.pc
-  io.rollback.bits.debug_runahead_checkpoint_id := s3_out.bits.uop.debugInfo.runahead_checkpoint_id
+  io.rollback.bits.debug_runahead_checkpoint_id := s3_out.bits.uop.perfDebugInfo.runahead_checkpoint_id
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
 
   io.lsq.ldin.bits.uop := s3_out.bits.uop
@@ -1583,8 +1582,8 @@ class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModul
   s3_wb.debug.isPerfCnt := false.B
   s3_wb.debug.paddr := s3_in.paddr
   s3_wb.debug.vaddr := s3_in.vaddr
-  s3_wb.debugInfo := s3_out.bits.uop.debugInfo
-  s3_wb.debug_seqNum := s3_out.bits.uop.debug_seqNum
+  s3_wb.perfDebugInfo.foreach(_ := s3_out.bits.uop.perfDebugInfo)
+  s3_wb.debug_seqNum.foreach(_  := s3_out.bits.uop.debug_seqNum)
 
   val s3_ld_wb_meta = Wire(new ExuOutput(param))
   s3_ld_wb_meta := Mux(s3_valid, s3_wb, s3_mmio_req.bits)
diff --git a/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala
index 258dd5ad410..8ffc312cb5e 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala
@@ -47,8 +47,8 @@ class StdExeUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMod
   io.out.bits.robIdx := io.in.bits.robIdx
   io.out.bits.pdest := io.in.bits.pdest
   io.out.bits.sqIdx.foreach(_ := io.in.bits.sqIdx.get)
-  io.out.bits.debugInfo := io.in.bits.perfDebugInfo
-  io.out.bits.debug_seqNum := io.in.bits.debug_seqNum
+  io.out.bits.perfDebugInfo.foreach(_ := io.in.bits.perfDebugInfo.get)
+  io.out.bits.debug_seqNum.foreach(_ := io.in.bits.debug_seqNum.get)
 
   io.atomicData.valid := io.in.fire && FuType.storeIsAMO(io.in.bits.fuType)
   io.atomicData.bits := io.in.bits
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 935c2d37060..47a6937fefb 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -78,8 +78,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
 
     val s0_s1_s2_valid = Output(Bool())
   })
-
-  PerfCCT.updateInstPos(io.stin.bits.debug_seqNum, PerfCCT.InstPos.AtFU.id.U, io.stin.valid, clock, reset)
+  io.stin.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.stin.valid, clock, reset))
 
   val s1_ready, s2_ready, s3_ready = WireInit(false.B)
 
@@ -263,7 +262,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   s0_out.isMisalign      := s0_isMisalign
   s0_out.vecBaseVaddr := s0_vecBaseVaddr
   when(s0_valid && s0_isFirstIssue) {
-    s0_out.uop.debugInfo.tlbFirstReqTime := GTimer()
+    s0_out.uop.perfDebugInfo.tlbFirstReqTime := GTimer()
   }
   s0_out.isFrmMisAlignBuf := s0_use_flow_ma
   s0_out.isFinalSplit := s0_isFinalSplit
@@ -420,7 +419,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   val s1_tlb_memidx = io.tlb.resp.bits.memidx
   when(s1_tlb_memidx.is_st && io.tlb.resp.valid && !s1_tlb_miss && s1_tlb_memidx.idx === s1_out.uop.sqIdx.value) {
     // printf("Store idx = %d\n", s1_tlb_memidx.idx)
-    s1_out.uop.debugInfo.tlbRespTime := GTimer()
+    s1_out.uop.perfDebugInfo.tlbRespTime := GTimer()
   }
   val s1_mis_align = s1_valid && !s1_tlb_miss && !s1_in.isHWPrefetch && !s1_isCbo && !s1_out.nc && !s1_out.mmio &&
                       GatedValidRegNext(io.csrCtrl.hd_misalign_st_enable) && s1_in.isMisalign && !s1_in.misalignWith16Byte &&
@@ -602,8 +601,8 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   s3_out.debug.isPerfCnt := false.B
   s3_out.debug.paddr := s3_in.paddr
   s3_out.debug.vaddr := s3_in.vaddr
-  s3_out.debugInfo := s3_in.uop.debugInfo
-  s3_out.debug_seqNum := s3_in.uop.debug_seqNum
+  s3_out.perfDebugInfo.foreach(_ := s3_in.uop.perfDebugInfo)
+  s3_out.debug_seqNum.foreach(_ := s3_in.uop.debug_seqNum)
 
   XSError(s3_valid && s3_in.isvec && s3_in.vecActive && !s3_in.mask.orR, "In vecActive, mask complement should not be 0")
   // Pipeline
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index d5cc3419af3..9d88c6d9ea5 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -122,8 +122,8 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
       vls.isVecLoad := VlduType.isVecLd(source.uop.fuOpType)
       vls.isVlm := VlduType.isMasked(source.uop.fuOpType) && VlduType.isVecLd(source.uop.fuOpType)
     })
-    sink.debugInfo := source.uop.debugInfo
-    sink.debug_seqNum := source.uop.debug_seqNum
+    sink.perfDebugInfo.foreach(_ := source.uop.perfDebugInfo)
+    sink.debug_seqNum.foreach(_ := source.uop.debug_seqNum)
     sink
   }
   def ToLsqConnect(source: MBufferBundle): FeedbackToLsqIO = {
@@ -514,8 +514,8 @@ class VSMergeBufferImp(implicit p: Parameters) extends BaseVMergeBuffer(isVStore
       vls.isVecLoad := VlduType.isVecLd(source.uop.fuOpType)
       vls.isVlm := VlduType.isMasked(source.uop.fuOpType) && VlduType.isVecLd(source.uop.fuOpType)
     })
-    sink.debugInfo := source.uop.debugInfo
-    sink.debug_seqNum := source.uop.debug_seqNum
+    sink.perfDebugInfo.foreach(_ := source.uop.perfDebugInfo)
+    sink.debug_seqNum.foreach(_ := source.uop.debug_seqNum)
     sink
   }
 }
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 223766b01c9..edd7784aaf1 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -932,8 +932,8 @@ class VSegmentUnit(val param: ExeUnitParams)(implicit p: Parameters) extends VLS
       vls.isVecLoad := VlduType.isVecLd(fofBuffer.fuOpType)
       vls.isVlm := VlduType.isMasked(fofBuffer.fuOpType) && VlduType.isVecLd(fofBuffer.fuOpType)
     })
-    writebackOut.debugInfo := fofBuffer.debugInfo
-    writebackOut.debug_seqNum := fofBuffer.debug_seqNum
+    writebackOut.perfDebugInfo.foreach(_ := fofBuffer.perfDebugInfo)
+    writebackOut.debug_seqNum.foreach(_ := fofBuffer.debug_seqNum)
   }.otherwise{
     writebackOut.data := VecInit(Seq.fill(param.wbPathNum)(data(deqPtr.value)))
     writebackOut.pdest := uopq(deqPtr.value).uop.pdest
@@ -964,8 +964,8 @@ class VSegmentUnit(val param: ExeUnitParams)(implicit p: Parameters) extends VLS
       vls.isVlm := VlduType.isMasked(instMicroOp.uop.fuOpType) && VlduType.isVecLd(instMicroOp.uop.fuOpType)
     })
     writebackOut.debug := DontCare
-    writebackOut.debugInfo := uopq(deqPtr.value).uop.debugInfo
-    writebackOut.debug_seqNum := uopq(deqPtr.value).uop.debug_seqNum
+    writebackOut.perfDebugInfo.foreach(_ := uopq(deqPtr.value).uop.perfDebugInfo)
+    writebackOut.debug_seqNum.foreach(_ := uopq(deqPtr.value).uop.debug_seqNum)
   }
 
   io.uopwriteback.valid               := RegNext(writebackValid)
diff --git a/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala b/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
index 7642fd8d275..9cf4576a1bb 100644
--- a/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
@@ -155,7 +155,7 @@ class VfofBuffer(val param: ExeUnitParams)(implicit p: Parameters) extends VLSUM
     vls.vpu.vl := entries.vl
     vls.vpu.vmask := Fill(VLEN, 1.U)
   })
-  io.uopWriteback.bits.debugInfo := entries.uop.debugInfo
-  io.uopWriteback.bits.debug_seqNum := entries.uop.debug_seqNum
+  io.uopWriteback.bits.perfDebugInfo.foreach(_ := entries.uop.perfDebugInfo)
+  io.uopWriteback.bits.debug_seqNum.foreach(_ := entries.uop.debug_seqNum)
 
 }
```
