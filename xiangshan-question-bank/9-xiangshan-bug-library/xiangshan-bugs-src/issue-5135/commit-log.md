# Commit Log
- Issue: #5135
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5135
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5135
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5135
- Changed files: 14
- Additions: 328
- Deletions: 741

## Files
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/BackendParams.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/datapath/WbArbiter.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala`
- `src/main/scala/xiangshan/backend/issue/BypassNetwork.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala`
- `src/main/scala/xiangshan/backend/issue/Scheduler.scala`
- `src/main/scala/xiangshan/backend/regfile/Regfile.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 4af34511e94..87c06d13145 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -216,9 +216,6 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   memBlock.io.ooo_to_mem.storePc := backend.io.mem.storePcRead
   memBlock.io.ooo_to_mem.hybridPc := backend.io.mem.hyuPcRead
   memBlock.io.ooo_to_mem.flushSb := backend.io.fenceio.sbuffer.flushSb
-  memBlock.io.ooo_to_mem.loadFastMatch := 0.U.asTypeOf(memBlock.io.ooo_to_mem.loadFastMatch)
-  memBlock.io.ooo_to_mem.loadFastImm := 0.U.asTypeOf(memBlock.io.ooo_to_mem.loadFastImm)
-  memBlock.io.ooo_to_mem.loadFastFuOpType := 0.U.asTypeOf(memBlock.io.ooo_to_mem.loadFastFuOpType)
 
   memBlock.io.ooo_to_mem.sfence <> backend.io.mem.sfence
 
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index c2cf61f4962..343212def53 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -26,7 +26,6 @@ package xiangshan.backend
 import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import difftest._
 import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
 import system.HasSoCParameter
 import utility._
@@ -35,23 +34,18 @@ import xiangshan._
 import xiangshan.backend.Bundles._
 import xiangshan.backend.ctrlblock.{DebugLSIO, LsTopdownInfo}
 import xiangshan.backend.datapath.DataConfig.{IntData, VecData, FpData}
-import xiangshan.backend.datapath.RdConfig.{IntRD, VfRD}
 import xiangshan.backend.datapath.WbConfig._
 import xiangshan.backend.datapath.DataConfig._
-import xiangshan.backend.datapath._
 import xiangshan.backend.dispatch.CoreDispatchTopDownIO
-import xiangshan.backend.fu.vector.Bundles.{VConfig, VType}
-import xiangshan.backend.fu.{FenceIO, FenceToSbuffer, FuConfig, FuType, PerfCounterIO}
+import xiangshan.backend.fu.vector.Bundles.VType
+import xiangshan.backend.fu.{FenceIO, FuConfig, PerfCounterIO}
 import xiangshan.backend.fu.NewCSR.PFEvent
-import xiangshan.backend.issue.EntryBundles._
-import xiangshan.backend.issue.Region
 import xiangshan.backend.rob.{RobCoreTopDownIO, RobDebugRollingIO, RobLsqIO, RobPtr}
 import xiangshan.backend.trace.TraceCoreInterface
 import xiangshan.frontend.{PreDecodeInfo}
-import xiangshan.frontend.ftq.{FtqPtr, FtqRead}
+import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
 
-import scala.collection.mutable
 
 class Backend(val params: BackendParams)(implicit p: Parameters) extends LazyModule
   with HasXSParameter {
@@ -218,8 +212,6 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   ctrlBlock.io.sqCanAccept := io.mem.sqCanAccept
 
   io.mem.wfi <> ctrlBlock.io.toMem.wfi
-  io.mem.loadFastMatch := 0.U.asTypeOf(io.mem.loadFastMatch)
-  io.mem.loadFastImm := 0.U.asTypeOf(io.mem.loadFastImm)
 
   io.mem.lsqEnqIO <> ctrlBlock.io.toMem.lsqEnqIO
   ctrlBlock.io.fromMemToDispatch.scommit := io.mem.sqDeq
@@ -236,24 +228,24 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   val og0Cancel = (intRegion.io.og0Cancel.asUInt | fpRegion.io.og0Cancel.asUInt | vecRegion.io.og0Cancel.asUInt).asBools
   ctrlBlock.io.toDispatch.og0Cancel := og0Cancel
   ctrlBlock.io.toDispatch.wbPregsInt.zip(intRegion.io.toIntPreg).map(x => {
-    x._1.valid := x._2.wen && x._2.intWen
-    x._1.bits := x._2.addr
+    x._1.valid := x._2.wen && x._2.rfWen
+    x._1.bits := x._2.pdest
   })
   ctrlBlock.io.toDispatch.wbPregsFp.zip(fpRegion.io.toFpPreg).map(x => {
     x._1.valid := x._2.wen && x._2.fpWen
-    x._1.bits := x._2.addr
+    x._1.bits := x._2.pdest
   })
   ctrlBlock.io.toDispatch.wbPregsVec.zip(vecRegion.io.toVfPreg).map(x => {
     x._1.valid := x._2.wen && x._2.vecWen
-    x._1.bits := x._2.addr
+    x._1.bits := x._2.pdest
   })
   ctrlBlock.io.toDispatch.wbPregsV0.zip(vecRegion.io.toV0Preg).map(x => {
     x._1.valid := x._2.wen && x._2.v0Wen
-    x._1.bits := x._2.addr
+    x._1.bits := x._2.pdest
   })
   ctrlBlock.io.toDispatch.wbPregsVl.zip(vecRegion.io.toVlPreg).map(x => {
     x._1.valid := x._2.wen && x._2.vlWen
-    x._1.bits := x._2.addr
+    x._1.bits := x._2.pdest
   })
   ctrlBlock.io.toDispatch.vlWriteBackInfo.vlFromIntIsZero := vlFromIntIsZero
   ctrlBlock.io.toDispatch.vlWriteBackInfo.vlFromIntIsVlmax := vlFromIntIsVlmax
@@ -353,7 +345,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   vecRegion.io.fromIntExu.get := intRegion.io.exuOut
   vecRegion.io.fromFpExu.get := fpRegion.io.exuOut
   // for fast wakeup data
-  intRegion.io.formFpExuBlockOut.get <> fpRegion.io.fpExuBlockOut.get
+  intRegion.io.fromFpExuBlockOut.get <> fpRegion.io.fpExuBlockOut.get
   intRegion.io.intSchdBusyTable := intRegion.io.wbFuBusyTableWriteOut
   intRegion.io.fpSchdBusyTable := fpRegion.io.wbFuBusyTableWriteOut
   intRegion.io.vfSchdBusyTable := vecRegion.io.wbFuBusyTableWriteOut
@@ -657,9 +649,6 @@ class BackendMemIO(implicit p: Parameters, params: BackendParams) extends XSBund
   val issueHysta = MixedVec(Seq.fill(params.HyuCnt)(DecoupledIO(new MemExuInput())))
   val issueVldu = MixedVec(Seq.fill(params.VlduCnt)(DecoupledIO(new MemExuInput(true))))
 
-  val loadFastMatch = Vec(params.LduCnt, Output(UInt(params.LduCnt.W)))
-  val loadFastImm   = Vec(params.LduCnt, Output(UInt(12.W))) // Imm_I
-
   val tlbCsr = Output(new TlbCsrBundle)
   val csrCtrl = Output(new CustomCSRCtrlIO)
   val sfence = Output(new SfenceBundle)
diff --git a/src/main/scala/xiangshan/backend/BackendParams.scala b/src/main/scala/xiangshan/backend/BackendParams.scala
index 6488d741377..c3800d276c5 100644
--- a/src/main/scala/xiangshan/backend/BackendParams.scala
+++ b/src/main/scala/xiangshan/backend/BackendParams.scala
@@ -141,27 +141,23 @@ case class BackendParams(
   }
 
   def genIntWriteBackBundle(implicit p: Parameters) = {
-    Seq.fill(this.getIntRfWriteSize)(new RfWritePortWithConfig(IntData(), intPregParams.addrWidth))
+    Vec(this.getIntRfWriteSize, new RfWritePortBundle(intPregParams))
   }
 
   def genFpWriteBackBundle(implicit p: Parameters) = {
-    Seq.fill(this.getFpRfWriteSize)(new RfWritePortWithConfig(FpData(), fpPregParams.addrWidth))
+    Vec(this.getFpRfWriteSize, new RfWritePortBundle(fpPregParams))
   }
 
   def genVfWriteBackBundle(implicit p: Parameters) = {
-    Seq.fill(this.getVfRfWriteSize)(new RfWritePortWithConfig(VecData(), vfPregParams.addrWidth))
+    Vec(this.getVfRfWriteSize, new RfWritePortBundle(vfPregParams))
   }
 
   def genV0WriteBackBundle(implicit p: Parameters) = {
-    Seq.fill(this.getV0RfWriteSize)(new RfWritePortWithConfig(V0Data(), v0PregParams.addrWidth))
+    Vec(this.getV0RfWriteSize, new RfWritePortBundle(v0PregParams))
   }
 
   def genVlWriteBackBundle(implicit p: Parameters) = {
-    Seq.fill(this.getVlRfWriteSize)(new RfWritePortWithConfig(VlData(), vlPregParams.addrWidth))
-  }
-
-  def genWriteBackBundles(implicit p: Parameters): Seq[RfWritePortWithConfig] = {
-    genIntWriteBackBundle ++ genVfWriteBackBundle
+    Vec(this.getVlRfWriteSize, new RfWritePortBundle(vlPregParams))
   }
 
   def genWrite2CtrlBundles(implicit p: Parameters): MixedVec[ValidIO[ExuOutput]] = {
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 6ed771fe073..2a7bc3e6aff 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -17,11 +17,11 @@ import xiangshan.backend.fu.fpu.Bundles.Frm
 import xiangshan.backend.fu.vector.Bundles._
 import xiangshan.backend.issue.{IssueBlockParams, IssueQueueDeqRespBundle, SchedulerType}
 import xiangshan.backend.issue.EntryBundles._
-import xiangshan.backend.regfile.{RfReadPortWithConfig, RfWritePortWithConfig}
+import xiangshan.backend.regfile.{IntPregParams, RfReadPortWithConfig, RfWritePortBundle}
 import xiangshan.backend.rob.RobPtr
 import xiangshan.frontend._
 import xiangshan.mem.{LqPtr, SqPtr}
-import xiangshan.mem.{VecMissalignedDebugBundle}
+import xiangshan.mem.VecMissalignedDebugBundle
 import yunsuan.vector.VIFuParam
 import xiangshan.backend.trace._
 import utility._
@@ -1027,67 +1027,52 @@ object Bundles {
       this.debug_seqNum := source.debug_seqNum
     }
 
-    def asIntRfWriteBundle(fire: Bool): RfWritePortWithConfig = {
-      val rfWrite = Wire(Output(new RfWritePortWithConfig(this.params.dataCfg, backendParams.getPregParams(IntData()).addrWidth)))
+    def asIntRfWriteBundle(fire: Bool): RfWritePortBundle = {
+      val rfWrite = Wire(new RfWritePortBundle(backendParams.intPregParams))
+      rfWrite := 0.U.asTypeOf(rfWrite)
       rfWrite.wen := this.rfWen && fire
-      rfWrite.addr := this.pdest
+      rfWrite.pdest := this.pdest
       rfWrite.data := this.data
-      rfWrite.intWen := this.rfWen
-      rfWrite.fpWen := false.B
-      rfWrite.vecWen := false.B
-      rfWrite.v0Wen := false.B
-      rfWrite.vlWen := false.B
+      rfWrite.rfWen := this.rfWen
       rfWrite
     }
 
-    def asFpRfWriteBundle(fire: Bool): RfWritePortWithConfig = {
-      val rfWrite = Wire(Output(new RfWritePortWithConfig(this.params.dataCfg, backendParams.getPregParams(FpData()).addrWidth)))
+    def asFpRfWriteBundle(fire: Bool): RfWritePortBundle = {
+      val rfWrite = Wire(new RfWritePortBundle(backendParams.fpPregParams))
+      rfWrite := 0.U.asTypeOf(rfWrite)
       rfWrite.wen := this.fpWen && fire
-      rfWrite.addr := this.pdest
+      rfWrite.pdest := this.pdest
       rfWrite.data := this.data
-      rfWrite.intWen := false.B
       rfWrite.fpWen := this.fpWen
-      rfWrite.vecWen := false.B
-      rfWrite.v0Wen := false.B
-      rfWrite.vlWen := false.B
       rfWrite
     }
 
-    def asVfRfWriteBundle(fire: Bool): RfWritePortWithConfig = {
-      val rfWrite = Wire(Output(new RfWritePortWithConfig(this.params.dataCfg, backendParams.getPregParams(VecData()).addrWidth)))
+    def asVfRfWriteBundle(fire: Bool): RfWritePortBundle = {
+      val rfWrite = Wire(new RfWritePortBundle(backendParams.vfPregParams))
+      rfWrite := 0.U.asTypeOf(rfWrite)
       rfWrite.wen := this.vecWen && fire
-      rfWrite.addr := this.pdest
+      rfWrite.pdest := this.pdest
       rfWrite.data := this.data
-      rfWrite.intWen := false.B
-      rfWrite.fpWen := false.B
       rfWrite.vecWen := this.vecWen
-      rfWrite.v0Wen := false.B
-      rfWrite.vlWen := false.B
       rfWrite
     }
 
-    def asV0RfWriteBundle(fire: Bool): RfWritePortWithConfig = {
-      val rfWrite = Wire(Output(new RfWritePortWithConfig(this.params.dataCfg, backendParams.getPregParams(V0Data()).addrWidth)))
+    def asV0RfWriteBundle(fire: Bool): RfWritePortBundle = {
+      val rfWrite = Wire(new RfWritePortBundle(backendParams.v0PregParams))
+      rfWrite := 0.U.asTypeOf(rfWrite)
       rfWrite.wen := this.v0Wen && fire
-      rfWrite.addr := this.pdest
+      rfWrite.pdest := this.pdest
       rfWrite.data := this.data
-      rfWrite.intWen := false.B
-      rfWrite.fpWen := false.B
-      rfWrite.vecWen := false.B
       rfWrite.v0Wen := this.v0Wen
-      rfWrite.vlWen := false.B
       rfWrite
     }
 
-    def asVlRfWriteBundle(fire: Bool): RfWritePortWithConfig = {
-      val rfWrite = Wire(Output(new RfWritePortWithConfig(this.params.dataCfg, backendParams.getPregParams(VlData()).addrWidth)))
+    def asVlRfWriteBundle(fire: Bool): RfWritePortBundle = {
+      val rfWrite = Wire(new RfWritePortBundle(backendParams.vlPregParams))
+      rfWrite := 0.U.asTypeOf(rfWrite)
       rfWrite.wen := this.vlWen && fire
-      rfWrite.addr := this.pdest
+      rfWrite.pdest := this.pdest
       rfWrite.data := this.data
-      rfWrite.intWen := false.B
-      rfWrite.fpWen := false.B
-      rfWrite.vecWen := false.B
-      rfWrite.v0Wen := false.B
       rfWrite.vlWen := this.vlWen
       rfWrite
     }
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index d1965b15f0c..49f846557b8 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -1,28 +1,37 @@
-package xiangshan.backend.issue
+/***************************************************************************************
+ * Copyright (c) 2025 Beijing Institute of Open Source Chip (BOSC)
+ *
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          http://license.coscl.org.cn/MulanPSL2
+ *
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ *
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package xiangshan.backend
 
 import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
-import utility.HasPerfEvents
-import utils.OptionWrapper
 import xiangshan._
 import xiangshan.backend.Bundles._
-import xiangshan.backend.{BackendMemIO, ExcpModToVprf, PcToDataPathIO, VprfToExcpMod}
 import xiangshan.backend.datapath.DataConfig._
 import xiangshan.backend.datapath._
 import xiangshan.backend.datapath.WbConfig._
 import xiangshan.backend.fu.{CSRFileIO, FenceIO, FuType}
-import xiangshan.backend.regfile.{RfWritePortBundle, RfWritePortWithConfig, VlPregParams}
-import xiangshan.backend.datapath.WbConfig.V0WB
+import xiangshan.backend.regfile.RfWritePortBundle
 import xiangshan.backend.exu.ExuBlock
-import xiangshan.backend.regcache.RegCacheTagTable
-import xiangshan.mem.{LqPtr, LsqEnqCtrl, LsqEnqIO, SqPtr}
-import xiangshan.mem.Bundles.MemWaitUpdateReqBundle
+import xiangshan.mem._
 import utility._
 import xiangshan.backend.fu.vector.Bundles.{VType, Vstart}
 import xiangshan.backend.fu.wrapper.{CSRInput, CSRToDecode}
 import xiangshan.backend.issue.EntryBundles.RespType
+import xiangshan.backend.issue._
 
 
 class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModule with HasCriticalErrors {
@@ -190,24 +199,14 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     case _ =>
   }
   // other wakeup, int vec need WB wakeup
-  def connectWakeupWB(sink: ValidIO[IssueQueueWBWakeUpBundle], source: RfWritePortWithConfig): Unit = {
+  def connectWakeupWB(sink: ValidIO[IssueQueueWBWakeUpBundle], source: RfWritePortBundle): Unit = {
     sink.valid := source.wen
-    sink.bits.rfWen := source.intWen
+    sink.bits.rfWen := source.rfWen
     sink.bits.fpWen := source.fpWen
     sink.bits.vecWen := source.vecWen
     sink.bits.v0Wen := source.v0Wen
     sink.bits.vlWen := source.vlWen
-    sink.bits.pdest := source.addr
-  }
-
-  def connectWakeupWBDelay(sink: ValidIO[IssueQueueWBWakeUpBundle], source: ValidIO[RfWritePortBundle]): Unit = {
-    sink.valid := source.valid
-    sink.bits.rfWen := source.bits.rfWen
-    sink.bits.fpWen := source.bits.fpWen
-    sink.bits.vecWen := source.bits.vecWen
-    sink.bits.v0Wen := source.bits.v0Wen
-    sink.bits.vlWen := source.bits.vlWen
-    sink.bits.pdest := source.bits.pdest
+    sink.bits.pdest := source.pdest
   }
 
   if (params.isIntSchd) {
@@ -215,17 +214,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     val idxes = backendParams.vecSchdParams.get.exuBlockParams.filter(_.writeIntRf).map(_.wbPortConfigs.filter(_.isInstanceOf[IntWB]).head.port)
     println(s"[Region] vec write int port = ${idxes}")
     val wakeupFromWB = MixedVecInit(idxes.map(x => io.fromIntWb(x)))
-    val wakeupFromWBDelayed = Reg(MixedVec(Vec(wakeupFromWB.size,
-      Valid(new RfWritePortBundle(backendParams.intPregParams.dataCfg, backendParams.intPregParams.addrWidth)))))
-    wakeupFromWBDelayed.zip(wakeupFromWB).map{case (sink, source) =>
-      sink.valid := source.wen
-      sink.bits.rfWen := source.intWen
-      sink.bits.fpWen := source.fpWen
-      sink.bits.vecWen := source.vecWen
-      sink.bits.v0Wen := source.v0Wen
-      sink.bits.vlWen := source.vlWen
-      sink.bits.pdest := source.addr
-    }
+    val wakeupFromWBDelayed = RegNext(wakeupFromWB)
     issueQueues.map { case iq =>
       val vecExuIndices = params.backendParam.allExuParams.filter(x => x.isVfExeUnit || x.isMemExeUnit && x.needVecWen).map(_.exuIdx)
       println(s"[Region_int] vecExuIndices = ${vecExuIndices}")
@@ -233,7 +222,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
       println(s"[Region_int] vecWBIndices = ${vecWBIndices}")
       vecWBIndices.zip(wakeupFromWB).zip(wakeupFromWBDelayed).map { case ((i, source1), source2) =>
         connectWakeupWB(iq.io.wakeupFromWB(i), source1)
-        connectWakeupWBDelay(iq.io.wakeupFromWBDelayed(i), source2)
+        connectWakeupWB(iq.io.wakeupFromWBDelayed(i), source2)
       }
       iq.io.wakeupFromF2I.foreach(_ := io.wakeupFromF2I.get)
       println(s"[Region_int] wakeupFromWB.size = ${wakeupFromWB.size}")
@@ -245,17 +234,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     val idxes = backendParams.vecSchdParams.get.exuBlockParams.filter(_.writeFpRf).map(_.wbPortConfigs.filter(_.isInstanceOf[FpWB]).head.port)
     println(s"[Region] vec write fp port = ${idxes}")
     val wakeupFromWB = MixedVecInit(idxes.map(x => io.fromFpWb(x)))
-    val wakeupFromWBDelayed = Reg(MixedVec(Vec(wakeupFromWB.size,
-      Valid(new RfWritePortBundle(backendParams.fpPregParams.dataCfg, backendParams.fpPregParams.addrWidth)))))
-    wakeupFromWBDelayed.zip(wakeupFromWB).map { case (sink, source) =>
-      sink.valid := source.wen
-      sink.bits.rfWen := source.intWen
-      sink.bits.fpWen := source.fpWen
-      sink.bits.vecWen := source.vecWen
-      sink.bits.v0Wen := source.v0Wen
-      sink.bits.vlWen := source.vlWen
-      sink.bits.pdest := source.addr
-    }
+    val wakeupFromWBDelayed = RegNext(wakeupFromWB)
     issueQueues.map { case iq =>
       val vecExuIndices = params.backendParam.allExuParams.filter(x => x.isVfExeUnit || x.isMemExeUnit && x.needVecWen).map(_.exuIdx)
       println(s"[Region_fp] vecExuIndices = ${vecExuIndices}")
@@ -263,52 +242,22 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
       println(s"[Region_fp] vecWBIndices = ${vecWBIndices}")
       vecWBIndices.zip(wakeupFromWB).zip(wakeupFromWBDelayed).map { case ((i, source1), source2) =>
         connectWakeupWB(iq.io.wakeupFromWB(i), source1)
-        connectWakeupWBDelay(iq.io.wakeupFromWBDelayed(i), source2)
+        connectWakeupWB(iq.io.wakeupFromWBDelayed(i), source2)
       }
       iq.io.wakeupFromI2F.foreach(_ := io.wakeupFromI2F.get)
     }
   }
   else if (params.isVecSchd) {
     val wakeupFromWB = io.fromVfWb ++ io.fromV0Wb ++ io.fromVlWb
-    val wakeupFromWBDelayedVf = Reg((Vec(io.fromVfWb.size,
-      Valid(new RfWritePortBundle(backendParams.vfPregParams.dataCfg, backendParams.vfPregParams.addrWidth)))))
-    val wakeupFromWBDelayedV0 = Reg((Vec(io.fromV0Wb.size,
-      Valid(new RfWritePortBundle(backendParams.v0PregParams.dataCfg, backendParams.v0PregParams.addrWidth)))))
-    val wakeupFromWBDelayedVl = Reg((Vec(io.fromVlWb.size,
-      Valid(new RfWritePortBundle(backendParams.vlPregParams.dataCfg, backendParams.vlPregParams.addrWidth)))))
-    wakeupFromWBDelayedVf.zip(io.fromVfWb).map { case (sink, source) =>
-      sink.valid := source.wen
-      sink.bits.rfWen := source.intWen
-      sink.bits.fpWen := source.fpWen
-      sink.bits.vecWen := source.vecWen
-      sink.bits.v0Wen := source.v0Wen
-      sink.bits.vlWen := source.vlWen
-      sink.bits.pdest := source.addr
-    }
-    wakeupFromWBDelayedV0.zip(io.fromV0Wb).map { case (sink, source) =>
-      sink.valid := source.wen
-      sink.bits.rfWen := source.intWen
-      sink.bits.fpWen := source.fpWen
-      sink.bits.vecWen := source.vecWen
-      sink.bits.v0Wen := source.v0Wen
-      sink.bits.vlWen := source.vlWen
-      sink.bits.pdest := source.addr
-    }
-    wakeupFromWBDelayedVl.zip(io.fromVlWb).map { case (sink, source) =>
-      sink.valid := source.wen
-      sink.bits.rfWen := source.intWen
-      sink.bits.fpWen := source.fpWen
-      sink.bits.vecWen := source.vecWen
-      sink.bits.v0Wen := source.v0Wen
-      sink.bits.vlWen := source.vlWen
-      sink.bits.pdest := source.addr
-    }
+    val wakeupFromWBDelayedVf = RegNext(io.fromVfWb)
+    val wakeupFromWBDelayedV0 = RegNext(io.fromV0Wb)
+    val wakeupFromWBDelayedVl = RegNext(io.fromVlWb)
     issueQueues.map { case iq =>
       println(s"[Region_vec] wakeupFromWB.size = ${wakeupFromWB.size}")
       println(s"[Region_vec] iq.io.wakeupFromWB.size = ${iq.io.wakeupFromWB.size}")
       println(s"[Region_vec] ${iq.param.getIQName}: iq.param.needWakeupFromVfWBPort = ${iq.param.needWakeupFromVfWBPort.map(x => (x._1, x._2.map(_.name)))}")
       iq.io.wakeupFromWB.zip(wakeupFromWB).map(x => connectWakeupWB(x._1, x._2))
-      iq.io.wakeupFromWBDelayed.zip(wakeupFromWBDelayedVf ++ wakeupFromWBDelayedV0 ++ wakeupFromWBDelayedVl).map(x => connectWakeupWBDelay(x._1, x._2))
+      iq.io.wakeupFromWBDelayed.zip(wakeupFromWBDelayedVf ++ wakeupFromWBDelayedV0 ++ wakeupFromWBDelayedVl).map(x => connectWakeupWB(x._1, x._2))
     }
   }
   // std dispatch
@@ -375,11 +324,6 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     x.ready := false.B
   })
   dataPath.io.ldCancel := 0.U.asTypeOf(dataPath.io.ldCancel)
-  dataPath.io.fromIntWb := 0.U.asTypeOf(dataPath.io.fromIntWb)
-  dataPath.io.fromFpWb := 0.U.asTypeOf(dataPath.io.fromFpWb)
-  dataPath.io.fromVfWb := 0.U.asTypeOf(dataPath.io.fromVfWb)
-  dataPath.io.fromV0Wb := 0.U.asTypeOf(dataPath.io.fromV0Wb)
-  dataPath.io.fromVlWb := 0.U.asTypeOf(dataPath.io.fromVlWb)
   dataPath.io.wbConfictRead := 0.U.asTypeOf(dataPath.io.wbConfictRead)
   dataPath.io.fromBypassNetwork := 0.U.asTypeOf(dataPath.io.fromBypassNetwork)
   dataPath.io.fromPcTargetMem.toDataPathTargetPC := 0.U.asTypeOf(dataPath.io.fromPcTargetMem.toDataPathTargetPC)
@@ -530,7 +474,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
       sink <> source
     }
     io.intToFpIQResp.get := dataPath.io.toFpIQ
-    dataPath.io.fromIntWb := wbDataPath.io.toIntPreg
+    dataPath.io.fromIntWb.get := wbDataPath.io.toIntPreg
     dataPath.io.fromPcTargetMem <> io.fromPcTargetMem.get
     dataPath.io.fromBypassNetwork := bypassNetwork.io.toDataPath
     dataPath.io.diffIntRat.foreach(_ := io.diffIntRat.get)
@@ -539,9 +483,9 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     bypassNetwork.io.fromDataPath.immInfo := dataPath.io.og1ImmInfo
     bypassNetwork.io.fromDataPath.rcData := dataPath.io.toBypassNetworkRCData
     bypassNetwork.io.fromExus.connectExuOutput(_.int)(exuBlock.io.out)
-    bypassNetwork.io.fromExus.connectExuOutput(_.fp)(io.formFpExuBlockOut.get)
+    bypassNetwork.io.fromExus.connectExuOutput(_.fp)(io.fromFpExuBlockOut.get)
     // no use
-    io.formFpExuBlockOut.get.flatten.map(_.ready := true.B)
+    io.fromFpExuBlockOut.get.flatten.map(_.ready := true.B)
     val intLoadWB = bypassNetwork.io.fromExus.int.flatten.filter(_.bits.params.hasLoadExu)
     intLoadWB.zip(io.fromMemExuOutput.take(intLoadWB.size)).foreach { case (sink, source) =>
       sink.valid := source.valid
@@ -654,7 +598,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
       sink <> source
     }
     io.fpToIntIQResp.get := dataPath.io.toIntIQ
-    dataPath.io.fromFpWb := wbDataPath.io.toFpPreg
+    dataPath.io.fromFpWb.get := wbDataPath.io.toFpPreg
     dataPath.io.fromBypassNetwork <> bypassNetwork.io.toDataPath
     dataPath.io.diffFpRat.foreach(_ := io.diffFpRat.get)
     io.toFpPreg := wbDataPath.io.toFpPreg
@@ -770,9 +714,9 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     dataPath.io.fromVfIQ.zip(issueQueues).map { case (sink, source) =>
       sink <> source.io.deqDelay
     }
-    dataPath.io.fromVfWb := wbDataPath.io.toVfPreg
-    dataPath.io.fromV0Wb := wbDataPath.io.toV0Preg
-    dataPath.io.fromVlWb := wbDataPath.io.toVlPreg
+    dataPath.io.fromVfWb.get := wbDataPath.io.toVfPreg
+    dataPath.io.fromV0Wb.get := wbDataPath.io.toV0Preg
+    dataPath.io.fromVlWb.get := wbDataPath.io.toVlPreg
     dataPath.io.fromBypassNetwork <> bypassNetwork.io.toDataPath
     dataPath.io.diffVecRat.foreach(_ := io.diffVecRat.get)
     dataPath.io.diffV0Rat.foreach(_ := io.diffV0Rat.get)
@@ -871,116 +815,45 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
   // perf counter
   if (params.isIntSchd) {
     val iqNum = issueQueues.size
-    for (i <- 0 until iqNum) {
-      for (j <- 0 until iqNum) {
-        if ((i != j) && (issueQueues(i).param.AluCnt > 0) && (issueQueues(j).param.AluCnt > 0)) {
-          val iqi = issueQueues(i).io
-          val iqiSeq = iqi.validVec.zip(iqi.issuedVec).zip(iqi.canIssueVec).zip(iqi.fuTypeVec).map {
-            case (((v, i), c), f) => v && !i && c && FuType.isAlu(f)
-          }
-          val iqj = issueQueues(j).io
-          val iqjSeq = iqj.validVec.zip(iqj.issuedVec).zip(iqj.canIssueVec).zip(iqj.fuTypeVec).map {
-            case (((v, i), c), f) => v && !i && c && FuType.isAlu(f)
-          }
-          val cond = (PopCount(iqiSeq) > 1.U) && (PopCount(iqjSeq) === 0.U)
-          XSPerfAccumulate(s"ALU_CanIssue_IQ${i}_more1_IQ${j}_none", PopCount(cond))
-        }
-      }
-    }
-  }
-  if (params.isIntSchd) {
-    val iqNum = issueQueues.size
-    for (i <- 0 until iqNum) {
-      for (j <- 0 until iqNum) {
-        if ((i != j) && (issueQueues(i).param.AluCnt > 0) && (issueQueues(j).param.AluCnt > 0)) {
-          val iqi = issueQueues(i).io
-          val iqiSeq = iqi.validVec.zip(iqi.issuedVec).zip(iqi.canIssueVec).zip(iqi.fuTypeVec).zip(iqi.srcReadyVec).map {
-            case ((((v, i), c), f), s) => v && !i && c && FuType.isAlu(f) && s
-          }
-          val iqj = issueQueues(j).io
-          val iqjSeq = iqj.validVec.zip(iqj.issuedVec).zip(iqj.canIssueVec).zip(iqj.fuTypeVec).zip(iqi.srcReadyVec).map {
-            case ((((v, i), c), f), s) => v && !i && c && FuType.isAlu(f) && s
-          }
-          val cond = (PopCount(iqiSeq) > 1.U) && (PopCount(iqjSeq) === 0.U)
-          XSPerfAccumulate(s"ALU_SrcReady_IQ${i}_more1_IQ${j}_none", PopCount(cond))
-        }
-      }
-    }
-  }
-  if (params.isIntSchd) {
-    val iqNum = issueQueues.size
-    for (i <- 0 until iqNum) {
-      for (j <- 0 until iqNum) {
-        if ((i != j) && (issueQueues(i).param.BrhCnt > 0) && (issueQueues(j).param.BrhCnt > 0)) {
-          val iqi = issueQueues(i).io
-          val iqiSeq = iqi.validVec.zip(iqi.issuedVec).zip(iqi.canIssueVec).zip(iqi.fuTypeVec).map {
-            case (((v, i), c), f) => v && !i && c && FuType.isBJU(f)
-          }
-          val iqj = issueQueues(j).io
-          val iqjSeq = iqj.validVec.zip(iqj.issuedVec).zip(iqj.canIssueVec).zip(iqj.fuTypeVec).map {
-            case (((v, i), c), f) => v && !i && c && FuType.isBJU(f)
-          }
-          val cond = (PopCount(iqiSeq) > 1.U) && (PopCount(iqjSeq) === 0.U)
-          XSPerfAccumulate(s"BJU_CanIssue_IQ${i}_more1_IQ${j}_none", PopCount(cond))
-        }
-      }
-    }
-  }
-  if (params.isIntSchd) {
-    val iqNum = issueQueues.size
-    for (i <- 0 until iqNum) {
-      for (j <- 0 until iqNum) {
-        if ((i != j) && (issueQueues(i).param.BrhCnt > 0) && (issueQueues(j).param.BrhCnt > 0)) {
-          val iqi = issueQueues(i).io
-          val iqiSeq = iqi.validVec.zip(iqi.issuedVec).zip(iqi.canIssueVec).zip(iqi.fuTypeVec).zip(iqi.srcReadyVec).map {
-            case ((((v, i), c), f), s) => v && !i && c && FuType.isBJU(f) && s
-          }
-          val iqj = issueQueues(j).io
-          val iqjSeq = iqj.validVec.zip(iqj.issuedVec).zip(iqj.canIssueVec).zip(iqj.fuTypeVec).zip(iqi.srcReadyVec).map {
-            case ((((v, i), c), f), s) => v && !i && c && FuType.isBJU(f) && s
-          }
-          val cond = (PopCount(iqiSeq) > 1.U) && (PopCount(iqjSeq) === 0.U)
-          XSPerfAccumulate(s"BJU_SrcReady_IQ${i}_more1_IQ${j}_none", PopCount(cond))
-        }
-      }
-    }
-  }
-  if (params.isIntSchd) {
-    val iqNum = issueQueues.size
-    for (i <- 0 until iqNum) {
-      for (j <- 0 until iqNum) {
-        if ((i != j) && (issueQueues(i).param.LduCnt > 0) && (issueQueues(j).param.LduCnt > 0)) {
-          val iqi = issueQueues(i).io
-          val iqiSeq = iqi.validVec.zip(iqi.issuedVec).zip(iqi.canIssueVec).zip(iqi.fuTypeVec).map {
-            case (((v, i), c), f) => v && !i && c && FuType.isLoad(f)
-          }
-          val iqj = issueQueues(j).io
-          val iqjSeq = iqj.validVec.zip(iqj.issuedVec).zip(iqj.canIssueVec).zip(iqj.fuTypeVec).map {
-            case (((v, i), c), f) => v && !i && c && FuType.isLoad(f)
+    case class FUConfig(filter: UInt => Bool, name: String, paramCheck: IssueBlockParams => Boolean)
+    val fuConfigs = Seq(
+      FUConfig(FuType.isAlu, "ALU", _.AluCnt > 0),
+      FUConfig(FuType.isBJU, "BJU", _.BrhCnt > 0),
+      FUConfig(FuType.isLoad, "LDU", _.LduCnt > 0)
+    )
+    def generatePerfCounters(fuConfig: FUConfig, checkSrcReady: Boolean): Unit = {
+      for (i <- 0 until iqNum) {
+        for (j <- 0 until iqNum) {
+          if ((i != j) && fuConfig.paramCheck(issueQueues(i).param) && fuConfig.paramCheck(issueQueues(j).param)) {
+
+            def getIssueSignals(iq: IssueQueueIO): Seq[Bool] = {
+              val baseSignals = iq.validVec.zip(iq.issuedVec).zip(iq.canIssueVec).zip(iq.fuTypeVec).map {
+                case (((v, issued), canIssue), fuType) =>
+                  (v, !issued, canIssue, fuConfig.filter(fuType))
+              }
+              if (checkSrcReady) {
+                baseSignals.zip(iq.srcReadyVec).map {
+                  case ((v, notIssued, canIssue, fuMatch), srcReady) =>
+                    v && notIssued && canIssue && fuMatch && srcReady
+                }
+              } else {
+                baseSignals.map { case (v, notIssued, canIssue, fuMatch) =>
+                  v && notIssued && canIssue && fuMatch
+                }
+              }
+            }
+            val iqiSignals = getIssueSignals(issueQueues(i).io)
+            val iqjSignals = getIssueSignals(issueQueues(j).io)
+            val cond = (PopCount(iqiSignals) > 1.U) && (PopCount(iqjSignals) === 0.U)
+            val suffix = if (checkSrcReady) "SrcReady" else "CanIssue"
+            XSPerfAccumulate(s"${fuConfig.name}_${suffix}_IQ${i}_more1_IQ${j}_none", PopCount(cond))
           }
-          val cond = (PopCount(iqiSeq) > 1.U) && (PopCount(iqjSeq) === 0.U)
-          XSPerfAccumulate(s"LDU_CanIssue_IQ${i}_more1_IQ${j}_none", PopCount(cond))
         }
       }
     }
-  }
-  if (params.isIntSchd) {
-    val iqNum = issueQueues.size
-    for (i <- 0 until iqNum) {
-      for (j <- 0 until iqNum) {
-        if ((i != j) && (issueQueues(i).param.LduCnt > 0) && (issueQueues(j).param.LduCnt > 0)) {
-          val iqi = issueQueues(i).io
-          val iqiSeq = iqi.validVec.zip(iqi.issuedVec).zip(iqi.canIssueVec).zip(iqi.fuTypeVec).zip(iqi.srcReadyVec).map {
-            case ((((v, i), c), f), s) => v && !i && c && FuType.isLoad(f) && s
-          }
-          val iqj = issueQueues(j).io
-          val iqjSeq = iqj.validVec.zip(iqj.issuedVec).zip(iqj.canIssueVec).zip(iqj.fuTypeVec).zip(iqj.srcReadyVec).map {
-            case ((((v, i), c), f), s) => v && !i && c && FuType.isLoad(f) && s
-          }
-          val cond = (PopCount(iqiSeq) > 1.U) && (PopCount(iqjSeq) === 0.U)
-          XSPerfAccumulate(s"LDU_SrcReady_IQ${i}_more1_IQ${j}_none", PopCount(cond))
-        }
-      }
+    fuConfigs.foreach { config =>
+      generatePerfCounters(config, checkSrcReady = false)
+      generatePerfCounters(config, checkSrcReady = true)
     }
   }
 }
@@ -1045,26 +918,16 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
   val IssueQueueDeqSum = allIssueParams.map(_.numDeq).sum
   val maxIQSize = allIssueParams.map(_.numEntries).max
   val IQValidNumVec = Output(Vec(IssueQueueDeqSum, UInt((maxIQSize).U.getWidth.W)))
-  val toIntPreg = Flipped(MixedVec(Vec(backendParams.numPregWb(IntData()),
-    new RfWritePortWithConfig(backendParams.intPregParams.dataCfg, backendParams.intPregParams.addrWidth))))
-  val toFpPreg = Flipped(MixedVec(Vec(backendParams.numPregWb(FpData()),
-    new RfWritePortWithConfig(backendParams.fpPregParams.dataCfg, backendParams.fpPregParams.addrWidth))))
-  val toVfPreg = Flipped(MixedVec(Vec(backendParams.numPregWb(VecData()),
-    new RfWritePortWithConfig(backendParams.vfPregParams.dataCfg, backendParams.vfPregParams.addrWidth))))
-  val toV0Preg = Flipped(MixedVec(Vec(backendParams.numPregWb(V0Data()),
-    new RfWritePortWithConfig(backendParams.v0PregParams.dataCfg, backendParams.v0PregParams.addrWidth))))
-  val toVlPreg = Flipped(MixedVec(Vec(backendParams.numPregWb(VlData()),
-    new RfWritePortWithConfig(backendParams.vlPregParams.dataCfg, backendParams.vlPregParams.addrWidth))))
-  val fromIntWb = MixedVec(Vec(backendParams.numPregWb(IntData()),
-    new RfWritePortWithConfig(backendParams.intPregParams.dataCfg, backendParams.intPregParams.addrWidth)))
-  val fromFpWb = MixedVec(Vec(backendParams.numPregWb(FpData()),
-    new RfWritePortWithConfig(backendParams.fpPregParams.dataCfg, backendParams.fpPregParams.addrWidth)))
-  val fromVfWb = MixedVec(Vec(backendParams.numPregWb(VecData()),
-    new RfWritePortWithConfig(backendParams.vfPregParams.dataCfg, backendParams.vfPregParams.addrWidth)))
-  val fromV0Wb = MixedVec(Vec(backendParams.numPregWb(V0Data()),
-    new RfWritePortWithConfig(backendParams.v0PregParams.dataCfg, backendParams.v0PregParams.addrWidth)))
-  val fromVlWb = MixedVec(Vec(backendParams.numPregWb(VlData()),
-    new RfWritePortWithConfig(backendParams.vlPregParams.dataCfg, backendParams.vlPregParams.addrWidth)))
+  val toIntPreg = Output(backendParams.genIntWriteBackBundle)
+  val toFpPreg = Output(backendParams.genFpWriteBackBundle)
+  val toVfPreg = Output(backendParams.genVfWriteBackBundle)
+  val toV0Preg = Output(backendParams.genV0WriteBackBundle)
+  val toVlPreg = Output(backendParams.genVlWriteBackBundle)
+  val fromIntWb = Input(backendParams.genIntWriteBackBundle)
+  val fromFpWb = Input(backendParams.genFpWriteBackBundle)
+  val fromVfWb = Input(backendParams.genVfWriteBackBundle)
+  val fromV0Wb = Input(backendParams.genV0WriteBackBundle)
+  val fromVlWb = Input(backendParams.genVlWriteBackBundle)
   val I2FWakeupIn = Option.when(params.isFpSchd)(Flipped(ValidIO(new IssueQueueIQWakeUpBundle(params.backendParam.getExuIdxI2F, params.backendParam))))
   val F2IWakeupIn = Option.when(params.isIntSchd)(Flipped(ValidIO(new IssueQueueIQWakeUpBundle(params.backendParam.getExuIdxF2I, params.backendParam))))
   val og0Cancel = Output(ExuVec())
@@ -1083,7 +946,7 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
   val wbFuBusyTableWriteOut = MixedVec(params.issueBlockParams.map(x => Output(x.genWbFuBusyTableWriteBundle)))
   val toFrontendBJUResolve = Option.when(params.isIntSchd)(Vec(backendParams.BrhCnt, Valid(new Resolve)))
   val fpExuBlockOut = Option.when(params.isFpSchd)(params.genExuOutputDecoupledBundle)
-  val formFpExuBlockOut = Option.when(params.isIntSchd)(Flipped(fpSchdParam.genExuOutputDecoupledBundle))
+  val fromFpExuBlockOut = Option.when(params.isIntSchd)(Flipped(fpSchdParam.genExuOutputDecoupledBundle))
   // to read fp regfile
   val intIQOut  = Option.when(params.isIntSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
   val fromIntIQ = Option.when(params.isFpSchd)(Flipped(MixedVec(intSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 8fbcb4e4c4e..9b695143d41 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -23,20 +23,6 @@ import xiangshan.backend.fu.FuType.is0latency
 import xiangshan.backend.fu.FuType.isUncertain
 import xiangshan.mem.{LqPtr, SqPtr}
 
-//class DataPath(params: BackendParams)(implicit p: Parameters) extends LazyModule {
-//  override def shouldBeInlined: Boolean = false
-//
-//  private implicit val dpParams: BackendParams = params
-//  lazy val module = new DataPathImp(this)
-//
-//  println(s"[DataPath] Preg Params: ")
-//  println(s"[DataPath]   Int R(${params.getRfReadSize(IntData())}), W(${params.getRfWriteSize(IntData())}) ")
-//  println(s"[DataPath]   Fp R(${params.getRfReadSize(FpData())}), W(${params.getRfWriteSize(FpData())}) ")
-//  println(s"[DataPath]   Vf R(${params.getRfReadSize(VecData())}), W(${params.getRfWriteSize(VecData())}) ")
-//  println(s"[DataPath]   V0 R(${params.getRfReadSize(V0Data())}), W(${params.getRfWriteSize(V0Data())}) ")
-//  println(s"[DataPath]   Vl R(${params.getRfReadSize(VlData())}), W(${params.getRfWriteSize(VlData())}) ")
-//}
-
 class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockParams)
   extends XSModule with HasXSParameter with HasPerfEvents {
 
@@ -224,37 +210,11 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   private val pcReadFtqOffset = Wire(chiselTypeOf(io.fromPcTargetMem.fromDataPathFtqOffset))
   private val targetPCRdata = io.fromPcTargetMem.toDataPathTargetPC
   private val pcRdata = io.fromPcTargetMem.toDataPathPC
-  private val intRfRaddr = Wire(Vec(params.numPregRd(IntData()), UInt(intSchdParams.pregIdxWidth.W)))
   private val intRfRdata = Option.when(param.isIntSchd)(Wire(Vec(params.numPregRd(IntData()), UInt(intSchdParams.rfDataWidth.W))))
-  private val intRfWen = Wire(Vec(io.fromIntWb.length, Bool()))
-  private val intRfWaddr = Wire(Vec(io.fromIntWb.length, UInt(intSchdParams.pregIdxWidth.W)))
-  private val intRfWdata = Wire(Vec(io.fromIntWb.length, UInt(intSchdParams.rfDataWidth.W)))
-
-  private val fpRfRaddr = Wire(Vec(params.numPregRd(FpData()), UInt(fpSchdParams.pregIdxWidth.W)))
   private val fpRfRdata = Option.when(param.isFpSchd)(Wire(Vec(params.numPregRd(FpData()), UInt(fpSchdParams.rfDataWidth.W))))
-  private val fpRfWen = Wire(Vec(io.fromFpWb.length, Bool()))
-  private val fpRfWaddr = Wire(Vec(io.fromFpWb.length, UInt(fpSchdParams.pregIdxWidth.W)))
-  private val fpRfWdata = Wire(Vec(io.fromFpWb.length, UInt(fpSchdParams.rfDataWidth.W)))
-
-  private val vfRfRaddr = Wire(Vec(params.numPregRd(VecData()), UInt(vecSchdParams.pregIdxWidth.W)))
   private val vfRfRdata = Option.when(param.isVecSchd)(Wire(Vec(params.numPregRd(VecData()), UInt(vecSchdParams.rfDataWidth.W))))
-  val splitNum = if (backendParams.debugEn) 1 else 4
-  private val vfRfWen = Wire(Vec(splitNum, Vec(io.fromVfWb.length, Bool())))
-  private val vfRfWaddr = Wire(Vec(io.fromVfWb.length, UInt(vecSchdParams.pregIdxWidth.W)))
-  private val vfRfWdata = Wire(Vec(io.fromVfWb.length, UInt(vecSchdParams.rfDataWidth.W)))
-
-  private val v0RfSplitNum = VLEN / XLEN
-  private val v0RfRaddr = Wire(Vec(params.numPregRd(V0Data()), UInt(log2Up(V0PhyRegs).W)))
   private val v0RfRdata = Option.when(param.isVecSchd)(Wire(Vec(params.numPregRd(V0Data()), UInt(V0Data().dataWidth.W))))
-  private val v0RfWen = Wire(Vec(v0RfSplitNum, Vec(io.fromV0Wb.length, Bool())))
-  private val v0RfWaddr = Wire(Vec(io.fromV0Wb.length, UInt(log2Up(V0PhyRegs).W)))
-  private val v0RfWdata = Wire(Vec(io.fromV0Wb.length, UInt(V0Data().dataWidth.W)))
-
-  private val vlRfRaddr = Wire(Vec(params.numPregRd(VlData()), UInt(log2Up(VlPhyRegs).W)))
   private val vlRfRdata = Option.when(param.isVecSchd)(Wire(Vec(params.numPregRd(VlData()), UInt(VlData().dataWidth.W))))
-  private val vlRfWen = Wire(Vec(io.fromVlWb.length, Bool()))
-  private val vlRfWaddr = Wire(Vec(io.fromVlWb.length, UInt(log2Up(VlPhyRegs).W)))
-  private val vlRfWdata = Wire(Vec(io.fromVlWb.length, UInt(VlData().dataWidth.W)))
 
   val pcReadFtqPtrFormIQ = fromIntIQ.flatten.filter(x => x.bits.exuParams.needPc)
   assert(pcReadFtqPtrFormIQ.size == pcReadFtqPtr.size, s"pcReadFtqPtrFormIQ.size ${pcReadFtqPtrFormIQ.size} not equal pcReadFtqPtr.size ${pcReadFtqPtr.size}")
@@ -304,21 +264,123 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   )
 
   io.diffVl.foreach(_ := vlDiffReadData.get)
+
+  io.toWakeupQueueRCIdx := 0.U.asTypeOf(io.toWakeupQueueRCIdx)
+  io.toBypassNetworkRCData := 0.U.asTypeOf(io.toBypassNetworkRCData)
+  val splitNum = if (backendParams.debugEn) 1 else 4
   if (param.isIntSchd) {
+    val intRfRaddr = Wire(Vec(params.numPregRd(IntData()), UInt(intSchdParams.pregIdxWidth.W)))
+    val intRfWen = Wire(Vec(io.fromIntWb.get.length, Bool()))
+    val intRfWaddr = Wire(Vec(io.fromIntWb.get.length, UInt(intSchdParams.pregIdxWidth.W)))
+    val intRfWdata = Wire(Vec(io.fromIntWb.get.length, UInt(intSchdParams.rfDataWidth.W)))
     IntRegFileSplit("IntRegFile", intSchdParams.numPregs, splitNum, intRfRaddr, intRfRdata.get, intRfWen, intRfWaddr, intRfWdata,
       bankNum = 1,
       debugReadAddr = intDiffRead.map(_._1),
       debugReadData = intDiffRead.map(_._2)
     )
+    intRfWaddr := io.fromIntWb.get.map(x => RegEnable(x.pdest, x.wen)).toSeq
+    intRfWdata := io.fromIntWb.get.map(x => RegEnable(x.data, x.wen)).toSeq
+    intRfWen := RegNext(VecInit(io.fromIntWb.get.map(_.wen).toSeq))
+    for (portIdx <- intRfRaddr.indices) {
+      if (intRFReadArbiter.io.out.isDefinedAt(portIdx))
+        intRfRaddr(portIdx) := intRFReadArbiter.io.out(portIdx).bits.addr
+      else
+        intRfRaddr(portIdx) := 0.U
+    }
+    // regcache
+    val regCache = Module(new RegCache())
+    def IssueBundle2RCReadPort(issue: DecoupledIO[IssueQueueIssueBundle]): Vec[RCReadPort] = {
+      val readPorts = Wire(Vec(issue.bits.exuParams.numIntSrc, new RCReadPort(params.intSchdParams.get.rfDataWidth, RegCacheIdxWidth)))
+      readPorts.zipWithIndex.foreach { case (r, idx) =>
+        r.ren := issue.valid && issue.bits.common.dataSources(idx).readRegCache
+        r.addr := issue.bits.rcIdx.get(idx)
+        r.data := DontCare
+      }
+      readPorts
+    }
+    val regCacheReadReq = fromIntIQ.flatten.filter(_.bits.exuParams.numIntSrc > 0).flatMap(IssueBundle2RCReadPort(_))
+    val regCacheReadData = regCache.io.readPorts.map(_.data)
+    println(s"[${param.getName}DataPath] regCache readPorts size: ${regCache.io.readPorts.size}, regCacheReadReq size: ${regCacheReadReq.size}")
+    require(regCache.io.readPorts.size == regCacheReadReq.size, "reg cache's readPorts size should be equal to regCacheReadReq")
+    regCache.io.readPorts.zip(regCacheReadReq).foreach { case (r, req) =>
+      r.ren := req.ren
+      r.addr := req.addr
+    }
+    val s1_RCReadData: MixedVec[MixedVec[Vec[UInt]]] = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.bits.src.cloneType).toSeq))))
+    s1_RCReadData.foreach(_.foreach(_.foreach(_ := 0.U)))
+    s1_RCReadData.zip(toExu).filter(_._2.map(_.bits.params.isIntExeUnit).reduce(_ || _)).flatMap(_._1).flatten
+      .zip(regCacheReadData.take(params.getIntExuRCReadSize)).foreach { case (s1_data, rdata) =>
+      s1_data := rdata
+    }
+    s1_RCReadData.zip(toExu).filter(_._2.map(x => x.bits.params.isMemExeUnit && x.bits.params.readIntRf).reduce(_ || _)).flatMap(_._1).flatten
+      .zip(regCacheReadData.takeRight(params.getMemExuRCReadSize)).foreach { case (s1_data, rdata) =>
+      s1_data := rdata
+    }
+    println(s"[${param.getName}DataPath] s1_RCReadData.int.size: ${s1_RCReadData.zip(toExu).filter(_._2.map(_.bits.params.isIntExeUnit).reduce(_ || _)).flatMap(_._1).flatten.size}, RCRdata.int.size: ${params.getIntExuRCReadSize}")
+    println(s"[${param.getName}DataPath] s1_RCReadData.mem.size: ${s1_RCReadData.zip(toExu).filter(_._2.map(x => x.bits.params.isMemExeUnit && x.bits.params.readIntRf).reduce(_ || _)).flatMap(_._1).flatten.size}, RCRdata.mem.size: ${params.getMemExuRCReadSize}")
+    io.toWakeupQueueRCIdx := regCache.io.toWakeupQueueRCIdx
+    io.toBypassNetworkRCData := s1_RCReadData
+    regCache.io.writePorts := io.fromBypassNetwork
+    // perf counter
+    val int_regcache_size = 48
+    val int_regcache_tag = RegInit(VecInit(Seq.fill(int_regcache_size)(0.U(intSchdParams.pregIdxWidth.W))))
+    val int_regcache_enqPtr = RegInit(0.U(log2Up(int_regcache_size).W))
+    int_regcache_enqPtr := int_regcache_enqPtr + PopCount(intRfWen)
+    for (i <- intRfWen.indices) {
+      when(intRfWen(i)) {
+        int_regcache_tag(int_regcache_enqPtr + PopCount(intRfWen.take(i))) := intRfWaddr(i)
+      }
+    }
+    val int_regcache_part32 = (1 until 33).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
+    val int_regcache_part24 = (1 until 24).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
+    val int_regcache_part16 = (1 until 17).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
+    val int_regcache_part8 = (1 until 9).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
+    val int_regcache_48_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_tag.map(_ === x.bits.addr).reduce(_ || _))
+    val int_regcache_8_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part8.map(_ === x.bits.addr).reduce(_ || _))
+    val int_regcache_16_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part16.map(_ === x.bits.addr).reduce(_ || _))
+    val int_regcache_24_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part24.map(_ === x.bits.addr).reduce(_ || _))
+    val int_regcache_32_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part32.map(_ === x.bits.addr).reduce(_ || _))
+    XSPerfAccumulate("IntRegCache48Hit", PopCount(int_regcache_48_hit_vec))
+    XSPerfAccumulate("IntRegCache8Hit", PopCount(int_regcache_8_hit_vec))
+    XSPerfAccumulate("IntRegCache16Hit", PopCount(int_regcache_16_hit_vec))
+    XSPerfAccumulate("IntRegCache24Hit", PopCount(int_regcache_24_hit_vec))
+    XSPerfAccumulate("IntRegCache32Hit", PopCount(int_regcache_32_hit_vec))
+    XSPerfHistogram("IntRegCache48Hit_hist", PopCount(int_regcache_48_hit_vec), true.B, 0, 16, 2)
   }
   else if (param.isFpSchd) {
+    val fpRfRaddr = Wire(Vec(params.numPregRd(FpData()), UInt(fpSchdParams.pregIdxWidth.W)))
+    val fpRfWen = Wire(Vec(io.fromFpWb.get.length, Bool()))
+    val fpRfWaddr = Wire(Vec(io.fromFpWb.get.length, UInt(fpSchdParams.pregIdxWidth.W)))
+    val fpRfWdata = Wire(Vec(io.fromFpWb.get.length, UInt(fpSchdParams.rfDataWidth.W)))
     FpRegFileSplit("FpRegFile", fpSchdParams.numPregs, splitNum, fpRfRaddr, fpRfRdata.get, fpRfWen, fpRfWaddr, fpRfWdata,
       bankNum = 1,
       debugReadAddr = fpDiffRead.map(_._1),
       debugReadData = fpDiffRead.map(_._2)
     )
+    fpRfWaddr := io.fromFpWb.get.map(x => RegEnable(x.pdest, x.wen)).toSeq
+    fpRfWdata := io.fromFpWb.get.map(x => RegEnable(x.data, x.wen)).toSeq
+    fpRfWen := RegNext(VecInit(io.fromFpWb.get.map(_.wen).toSeq))
+    for (portIdx <- fpRfRaddr.indices) {
+      if (fpRFReadArbiter.io.out.isDefinedAt(portIdx))
+        fpRfRaddr(portIdx) := fpRFReadArbiter.io.out(portIdx).bits.addr
+      else
+        fpRfRaddr(portIdx) := 0.U
+    }
   }
   else {
+    val vfRfRaddr = Wire(Vec(params.numPregRd(VecData()), UInt(vecSchdParams.pregIdxWidth.W)))
+    val vfRfWen = Wire(Vec(splitNum, Vec(io.fromVfWb.get.length, Bool())))
+    val vfRfWaddr = Wire(Vec(io.fromVfWb.get.length, UInt(vecSchdParams.pregIdxWidth.W)))
+    val vfRfWdata = Wire(Vec(io.fromVfWb.get.length, UInt(vecSchdParams.rfDataWidth.W)))
+    val v0RfSplitNum = VLEN / XLEN
+    val v0RfRaddr = Wire(Vec(params.numPregRd(V0Data()), UInt(log2Up(V0PhyRegs).W)))
+    val v0RfWen = Wire(Vec(v0RfSplitNum, Vec(io.fromV0Wb.get.length, Bool())))
+    val v0RfWaddr = Wire(Vec(io.fromV0Wb.get.length, UInt(log2Up(V0PhyRegs).W)))
+    val v0RfWdata = Wire(Vec(io.fromV0Wb.get.length, UInt(V0Data().dataWidth.W)))
+    val vlRfRaddr = Wire(Vec(params.numPregRd(VlData()), UInt(log2Up(VlPhyRegs).W)))
+    val vlRfWen = Wire(Vec(io.fromVlWb.get.length, Bool()))
+    val vlRfWaddr = Wire(Vec(io.fromVlWb.get.length, UInt(log2Up(VlPhyRegs).W)))
+    val vlRfWdata = Wire(Vec(io.fromVlWb.get.length, UInt(VlData().dataWidth.W)))
     VfRegFile("VfRegFile", vecSchdParams.numPregs, splitNum, vfRfRaddr, vfRfRdata.get, vfRfWen, vfRfWaddr, vfRfWdata,
       debugReadAddr = vfDiffRead.map(_._1),
       debugReadData = vfDiffRead.map(_._2)
@@ -333,72 +395,79 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
       debugReadAddr = vlDiffRead.map(_._1),
       debugReadData = vlDiffRead.map(_._2)
     )
+    vfRfWaddr := io.fromVfWb.get.map(x => RegEnable(x.pdest, x.wen)).toSeq
+    vfRfWdata := io.fromVfWb.get.map(x => RegEnable(x.data, x.wen)).toSeq
+    vfRfWen.foreach(_.zip(io.fromVfWb.get.map(x => RegNext(x.wen))).foreach { case (wenSink, wenSource) => wenSink := wenSource })
+    for (portIdx <- vfRfRaddr.indices) {
+      if (vfRFReadArbiter.io.out.isDefinedAt(portIdx))
+        vfRfRaddr(portIdx) := vfRFReadArbiter.io.out(portIdx).bits.addr
+      else
+        vfRfRaddr(portIdx) := 0.U
+    }
+    v0RfWaddr := io.fromV0Wb.get.map(x => RegEnable(x.pdest, x.wen)).toSeq
+    v0RfWdata := io.fromV0Wb.get.map(x => RegEnable(x.data, x.wen)).toSeq
+    v0RfWen.foreach(_.zip(io.fromV0Wb.get.map(x => RegNext(x.wen))).foreach { case (wenSink, wenSource) => wenSink := wenSource })
+    for (portIdx <- v0RfRaddr.indices) {
+      if (v0RFReadArbiter.io.out.isDefinedAt(portIdx))
+        v0RfRaddr(portIdx) := v0RFReadArbiter.io.out(portIdx).bits.addr
+      else
+        v0RfRaddr(portIdx) := 0.U
+    }
+    vlRfWaddr := io.fromVlWb.get.map(x => RegEnable(x.pdest, x.wen)).toSeq
+    vlRfWdata := io.fromVlWb.get.map(x => RegEnable(x.data, x.wen)).toSeq
+    vlRfWen := io.fromVlWb.get.map(x => RegNext(x.wen)).toSeq
+    for (portIdx <- vlRfRaddr.indices) {
+      if (vlRFReadArbiter.io.out.isDefinedAt(portIdx))
+        vlRfRaddr(portIdx) := vlRFReadArbiter.io.out(portIdx).bits.addr
+      else
+        vlRfRaddr(portIdx) := 0.U
+    }
+
+    val vecExcpUseVecRdPorts = Seq(6, 7, 8, 9, 10, 11, 0, 1)
+    val vecExcpUseVecWrPorts = Seq(0, 1, 2, 3)
+    val vecExcpUseV0RdPorts = Seq(2, 3)
+    val vecExcpUsev0WrPorts = Seq(0)
+    var v0RdPortsIter: Iterator[Int] = vecExcpUseV0RdPorts.iterator
+    val v0WrPortsIter: Iterator[Int] = vecExcpUsev0WrPorts.iterator
+    val (fromVecExcp, toVecExcp) = (io.fromVecExcpMod.get, io.toVecExcpMod.get)
+    for (i <- fromVecExcp.r.indices) {
+      when(fromVecExcp.r(i).valid && !fromVecExcp.r(i).bits.isV0) {
+        vfRfRaddr(vecExcpUseVecRdPorts(i)) := fromVecExcp.r(i).bits.addr
+      }
+      if (i % maxMergeNumPerCycle == 0) {
+        val v0RdPort = v0RdPortsIter.next()
+        when(fromVecExcp.r(i).valid && fromVecExcp.r(i).bits.isV0) {
+          v0RfRaddr(v0RdPort) := fromVecExcp.r(i).bits.addr
+        }
+      }
+    }
+    for (i <- fromVecExcp.w.indices) {
+      when(fromVecExcp.w(i).valid && !fromVecExcp.w(i).bits.isV0) {
+        val vecWrPort = vecExcpUseVecWrPorts(i)
+        vfRfWen.foreach(_(vecWrPort) := true.B)
+        vfRfWaddr(vecWrPort) := fromVecExcp.w(i).bits.newVdAddr
+        vfRfWdata(vecWrPort) := fromVecExcp.w(i).bits.newVdData
+      }
+      if (i % maxMergeNumPerCycle == 0) {
+        when(fromVecExcp.w(i).valid && fromVecExcp.w(i).bits.isV0) {
+          val v0WrPort = v0WrPortsIter.next()
+          v0RfWen.foreach(_(v0WrPort) := true.B)
+          v0RfWaddr(v0WrPort) := fromVecExcp.w(i).bits.newVdAddr
+          v0RfWdata(v0WrPort) := fromVecExcp.w(i).bits.newVdData
+        }
+      }
+    }
+    v0RdPortsIter = vecExcpUseV0RdPorts.iterator
+    for (i <- toVecExcp.rdata.indices) {
+      toVecExcp.rdata(i).valid := RegNext(fromVecExcp.r(i).valid)
+      toVecExcp.rdata(i).bits := Mux(
+        RegEnable(!fromVecExcp.r(i).bits.isV0, fromVecExcp.r(i).valid),
+        vfRfRdata.get(vecExcpUseVecRdPorts(i)),
+        if (i % maxMergeNumPerCycle == 0) v0RfRdata.get(v0RdPortsIter.next()) else 0.U,
+      )
+    }
   }
 
-  intRfWaddr := io.fromIntWb.map(x => RegEnable(x.addr, x.wen)).toSeq
-  intRfWdata := io.fromIntWb.map(x => RegEnable(x.data, x.wen)).toSeq
-  intRfWen := RegNext(VecInit(io.fromIntWb.map(_.wen).toSeq))
-
-  for (portIdx <- intRfRaddr.indices) {
-    if (intRFReadArbiter.io.out.isDefinedAt(portIdx))
-      intRfRaddr(portIdx) := intRFReadArbiter.io.out(portIdx).bits.addr
-    else
-      intRfRaddr(portIdx) := 0.U
-  }
-
-  fpRfWaddr := io.fromFpWb.map(x => RegEnable(x.addr, x.wen)).toSeq
-  fpRfWdata := io.fromFpWb.map(x => RegEnable(x.data, x.wen)).toSeq
-  fpRfWen := RegNext(VecInit(io.fromFpWb.map(_.wen).toSeq))
-
-  for (portIdx <- fpRfRaddr.indices) {
-    if (fpRFReadArbiter.io.out.isDefinedAt(portIdx))
-      fpRfRaddr(portIdx) := fpRFReadArbiter.io.out(portIdx).bits.addr
-    else
-      fpRfRaddr(portIdx) := 0.U
-  }
-
-  vfRfWaddr := io.fromVfWb.map(x => RegEnable(x.addr, x.wen)).toSeq
-  vfRfWdata := io.fromVfWb.map(x => RegEnable(x.data, x.wen)).toSeq
-  vfRfWen.foreach(_.zip(io.fromVfWb.map(x => RegNext(x.wen))).foreach { case (wenSink, wenSource) => wenSink := wenSource } )
-
-  for (portIdx <- vfRfRaddr.indices) {
-    if (vfRFReadArbiter.io.out.isDefinedAt(portIdx))
-      vfRfRaddr(portIdx) := vfRFReadArbiter.io.out(portIdx).bits.addr
-    else
-      vfRfRaddr(portIdx) := 0.U
-  }
-
-  v0RfWaddr := io.fromV0Wb.map(x => RegEnable(x.addr, x.wen)).toSeq
-  v0RfWdata := io.fromV0Wb.map(x => RegEnable(x.data, x.wen)).toSeq
-  v0RfWen.foreach(_.zip(io.fromV0Wb.map(x => RegNext(x.wen))).foreach { case (wenSink, wenSource) => wenSink := wenSource } )
-
-  for (portIdx <- v0RfRaddr.indices) {
-    if (v0RFReadArbiter.io.out.isDefinedAt(portIdx))
-      v0RfRaddr(portIdx) := v0RFReadArbiter.io.out(portIdx).bits.addr
-    else
-      v0RfRaddr(portIdx) := 0.U
-  }
-
-  private val vecExcpUseVecRdPorts = Seq(6, 7, 8, 9, 10, 11, 0, 1)
-  private val vecExcpUseVecWrPorts = Seq(0, 1, 2, 3)
-  private val vecExcpUseV0RdPorts = Seq(2, 3)
-  private val vecExcpUsev0WrPorts = Seq(0)
-
-  private var v0RdPortsIter: Iterator[Int] = vecExcpUseV0RdPorts.iterator
-  private val v0WrPortsIter: Iterator[Int] = vecExcpUsev0WrPorts.iterator
-
-  vlRfWaddr := io.fromVlWb.map(x => RegEnable(x.addr, x.wen)).toSeq
-  vlRfWdata := io.fromVlWb.map(x => RegEnable(x.data, x.wen)).toSeq
-  vlRfWen := io.fromVlWb.map(x => RegNext(x.wen)).toSeq
-
-  for (portIdx <- vlRfRaddr.indices) {
-    if (vlRFReadArbiter.io.out.isDefinedAt(portIdx))
-      vlRfRaddr(portIdx) := vlRFReadArbiter.io.out(portIdx).bits.addr
-    else
-      vlRfRaddr(portIdx) := 0.U
-  }
-
-
   intDiffRead.foreach { case (addr, _) =>
     addr := io.diffIntRat.get
   }
@@ -417,62 +486,13 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
     addr := io.diffVlRat.get
   }
 
-  println(s"[DataPath] " +
+  println(s"[${param.getName}DataPath] " +
     s"has intDiffRead: ${intDiffRead.nonEmpty}, " +
     s"has fpDiffRead: ${fpDiffRead.nonEmpty}, " +
     s"has vecDiffRead: ${vfDiffRead.nonEmpty}, " +
     s"has v0DiffRead: ${v0DiffRead.nonEmpty}, " +
     s"has vlDiffRead: ${vlDiffRead.nonEmpty}")
 
-  // regcache
-  private val regCache = Module(new RegCache())
-
-  def IssueBundle2RCReadPort(issue: DecoupledIO[IssueQueueIssueBundle]): Vec[RCReadPort] = {
-    val readPorts = Wire(Vec(issue.bits.exuParams.numIntSrc, new RCReadPort(params.intSchdParams.get.rfDataWidth, RegCacheIdxWidth)))
-    readPorts.zipWithIndex.foreach{ case (r, idx) =>
-      r.ren  := issue.valid && issue.bits.common.dataSources(idx).readRegCache
-      r.addr := issue.bits.rcIdx.get(idx)
-      r.data := DontCare
-    }
-    readPorts
-  }
-
-  private val regCacheReadReq = fromIntIQ.flatten.filter(_.bits.exuParams.numIntSrc > 0).flatMap(IssueBundle2RCReadPort(_))
-  private val regCacheReadData = regCache.io.readPorts.map(_.data)
-
-  println(s"[DataPath] regCache readPorts size: ${regCache.io.readPorts.size}, regCacheReadReq size: ${regCacheReadReq.size}")
-  require(regCache.io.readPorts.size == regCacheReadReq.size, "reg cache's readPorts size should be equal to regCacheReadReq")
-  if (param.isIntSchd){
-    regCache.io.readPorts.zip(regCacheReadReq).foreach { case (r, req) =>
-      r.ren := req.ren
-      r.addr := req.addr
-    }
-  }
-  else {
-    regCache.io.readPorts.map{ case x =>
-      x.ren := false.B
-      x.addr := 0.U
-    }
-  }
-
-  val s1_RCReadData: MixedVec[MixedVec[Vec[UInt]]] = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.bits.src.cloneType).toSeq))))
-  s1_RCReadData.foreach(_.foreach(_.foreach(_ := 0.U)))
-  s1_RCReadData.zip(toExu).filter(_._2.map(_.bits.params.isIntExeUnit).reduce(_ || _)).flatMap(_._1).flatten
-    .zip(regCacheReadData.take(params.getIntExuRCReadSize)).foreach{ case (s1_data, rdata) => 
-      s1_data := rdata
-    }
-  s1_RCReadData.zip(toExu).filter(_._2.map(x => x.bits.params.isMemExeUnit && x.bits.params.readIntRf).reduce(_ || _)).flatMap(_._1).flatten
-    .zip(regCacheReadData.takeRight(params.getMemExuRCReadSize)).foreach{ case (s1_data, rdata) => 
-      s1_data := rdata
-    }
-
-  println(s"[DataPath] s1_RCReadData.int.size: ${s1_RCReadData.zip(toExu).filter(_._2.map(_.bits.params.isIntExeUnit).reduce(_ || _)).flatMap(_._1).flatten.size}, RCRdata.int.size: ${params.getIntExuRCReadSize}")
-  println(s"[DataPath] s1_RCReadData.mem.size: ${s1_RCReadData.zip(toExu).filter(_._2.map(x => x.bits.params.isMemExeUnit && x.bits.params.readIntRf).reduce(_ || _)).flatMap(_._1).flatten.size}, RCRdata.mem.size: ${params.getMemExuRCReadSize}")
-
-  io.toWakeupQueueRCIdx := regCache.io.toWakeupQueueRCIdx
-  io.toBypassNetworkRCData := s1_RCReadData
-  regCache.io.writePorts := io.fromBypassNetwork
-
   val s1_addrOHs = Reg(MixedVec(
     fromIQ.map(x => MixedVec(x.map(_.bits.addrOH.cloneType).toSeq)).toSeq
   ))
@@ -732,66 +752,6 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
     }
   }
 
-  val int_regcache_size = 48
-  val int_regcache_tag = RegInit(VecInit(Seq.fill(int_regcache_size)(0.U(intSchdParams.pregIdxWidth.W))))
-  val int_regcache_enqPtr = RegInit(0.U(log2Up(int_regcache_size).W))
-  int_regcache_enqPtr := int_regcache_enqPtr + PopCount(intRfWen)
-  for (i <- intRfWen.indices) {
-    when (intRfWen(i)) {
-      int_regcache_tag(int_regcache_enqPtr + PopCount(intRfWen.take(i))) := intRfWaddr(i)
-    }
-  }
-
-  val vf_regcache_size = 48
-  val vf_regcache_tag = RegInit(VecInit(Seq.fill(vf_regcache_size)(0.U(vecSchdParams.pregIdxWidth.W))))
-  val vf_regcache_enqPtr = RegInit(0.U(log2Up(vf_regcache_size).W))
-  vf_regcache_enqPtr := vf_regcache_enqPtr + PopCount(vfRfWen.head)
-  for (i <- vfRfWen.indices) {
-    when (vfRfWen.head(i)) {
-      vf_regcache_tag(vf_regcache_enqPtr + PopCount(vfRfWen.head.take(i))) := vfRfWaddr(i)
-    }
-  }
-
-  if (param.isVecSchd) {
-    val (fromVecExcp, toVecExcp) = (io.fromVecExcpMod.get, io.toVecExcpMod.get)
-    for (i <- fromVecExcp.r.indices) {
-      when(fromVecExcp.r(i).valid && !fromVecExcp.r(i).bits.isV0) {
-        vfRfRaddr(vecExcpUseVecRdPorts(i)) := fromVecExcp.r(i).bits.addr
-      }
-      if (i % maxMergeNumPerCycle == 0) {
-        val v0RdPort = v0RdPortsIter.next()
-        when(fromVecExcp.r(i).valid && fromVecExcp.r(i).bits.isV0) {
-          v0RfRaddr(v0RdPort) := fromVecExcp.r(i).bits.addr
-        }
-      }
-    }
-    for (i <- fromVecExcp.w.indices) {
-      when(fromVecExcp.w(i).valid && !fromVecExcp.w(i).bits.isV0) {
-        val vecWrPort = vecExcpUseVecWrPorts(i)
-        vfRfWen.foreach(_(vecWrPort) := true.B)
-        vfRfWaddr(vecWrPort) := fromVecExcp.w(i).bits.newVdAddr
-        vfRfWdata(vecWrPort) := fromVecExcp.w(i).bits.newVdData
-      }
-      if (i % maxMergeNumPerCycle == 0) {
-        when(fromVecExcp.w(i).valid && fromVecExcp.w(i).bits.isV0) {
-          val v0WrPort = v0WrPortsIter.next()
-          v0RfWen.foreach(_(v0WrPort) := true.B)
-          v0RfWaddr(v0WrPort) := fromVecExcp.w(i).bits.newVdAddr
-          v0RfWdata(v0WrPort) := fromVecExcp.w(i).bits.newVdData
-        }
-      }
-    }
-    v0RdPortsIter = vecExcpUseV0RdPorts.iterator
-    for (i <- toVecExcp.rdata.indices) {
-      toVecExcp.rdata(i).valid := RegNext(fromVecExcp.r(i).valid)
-      toVecExcp.rdata(i).bits := Mux(
-        RegEnable(!fromVecExcp.r(i).bits.isV0, fromVecExcp.r(i).valid),
-        vfRfRdata.get(vecExcpUseVecRdPorts(i)),
-        if (i % maxMergeNumPerCycle == 0) v0RfRdata.get(v0RdPortsIter.next()) else 0.U,
-      )
-    }
-  }
-
   XSPerfHistogram(s"IntRegFileRead_hist", PopCount(intRFReadArbiter.io.in.flatten.flatten.map(_.valid)), true.B, 0, 20, 1)
   XSPerfHistogram(s"FpRegFileRead_hist", PopCount(fpRFReadArbiter.io.in.flatten.flatten.map(_.valid)), true.B, 0, 20, 1)
   XSPerfHistogram(s"VfRegFileRead_hist", PopCount(vfRFReadArbiter.io.in.flatten.flatten.map(_.valid)), true.B, 0, 20, 1)
@@ -799,23 +759,6 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   XSPerfHistogram(s"FpRegFileWrite_hist", PopCount(fpRFWriteReq.flatten), true.B, 0, 20, 1)
   XSPerfHistogram(s"VfRegFileWrite_hist", PopCount(vfRFWriteReq.flatten), true.B, 0, 20, 1)
 
-  val int_regcache_part32 = (1 until 33).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
-  val int_regcache_part24 = (1 until 24).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
-  val int_regcache_part16 = (1 until 17).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
-  val int_regcache_part8 = (1 until 9).map(i => int_regcache_tag(int_regcache_enqPtr - i.U))
-
-  val int_regcache_48_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_tag.map(_ === x.bits.addr).reduce(_ || _))
-  val int_regcache_8_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part8.map(_ === x.bits.addr).reduce(_ || _))
-  val int_regcache_16_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part16.map(_ === x.bits.addr).reduce(_ || _))
-  val int_regcache_24_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part24.map(_ === x.bits.addr).reduce(_ || _))
-  val int_regcache_32_hit_vec = intRFReadArbiter.io.in.flatten.flatten.map(x => x.valid && int_regcache_part32.map(_ === x.bits.addr).reduce(_ || _))
-  XSPerfAccumulate("IntRegCache48Hit", PopCount(int_regcache_48_hit_vec))
-  XSPerfAccumulate("IntRegCache8Hit", PopCount(int_regcache_8_hit_vec))
-  XSPerfAccumulate("IntRegCache16Hit", PopCount(int_regcache_16_hit_vec))
-  XSPerfAccumulate("IntRegCache24Hit", PopCount(int_regcache_24_hit_vec))
-  XSPerfAccumulate("IntRegCache32Hit", PopCount(int_regcache_32_hit_vec))
-  XSPerfHistogram("IntRegCache48Hit_hist", PopCount(int_regcache_48_hit_vec), true.B, 0, 16, 2)
-
   XSPerfAccumulate(s"IntRFReadBeforeArb", PopCount(intRFReadArbiter.io.in.flatten.flatten.map(_.valid)))
   XSPerfAccumulate(s"IntRFReadAfterArb", PopCount(intRFReadArbiter.io.out.map(_.valid)))
   XSPerfAccumulate(s"FpRFReadBeforeArb", PopCount(fpRFReadArbiter.io.in.flatten.flatten.map(_.valid)))
@@ -954,15 +897,15 @@ class DataPathIO()(implicit p: Parameters, params: BackendParams, param: SchdBlo
 
   val og1ImmInfo: Vec[ImmInfo] = Output(Vec(params.allExuParams.size, new ImmInfo))
 
-  val fromIntWb: MixedVec[RfWritePortWithConfig] = MixedVec(params.genIntWriteBackBundle)
+  val fromIntWb = Option.when(param.isIntSchd)(Input(params.genIntWriteBackBundle))
 
-  val fromFpWb: MixedVec[RfWritePortWithConfig] = MixedVec(params.genFpWriteBackBundle)
+  val fromFpWb = Option.when(param.isFpSchd)(Input(params.genFpWriteBackBundle))
 
-  val fromVfWb: MixedVec[RfWritePortWithConfig] = MixedVec(params.genVfWriteBackBundle)
+  val fromVfWb = Option.when(param.isVecSchd)(Input(params.genVfWriteBackBundle))
 
-  val fromV0Wb: MixedVec[RfWritePortWithConfig] = MixedVec(params.genV0WriteBackBundle)
+  val fromV0Wb = Option.when(param.isVecSchd)(Input(params.genV0WriteBackBundle))
 
-  val fromVlWb: MixedVec[RfWritePortWithConfig] = MixedVec(params.genVlWriteBackBundle)
+  val fromVlWb = Option.when(param.isVecSchd)(Input(params.genVlWriteBackBundle))
 
   val fromPcTargetMem = Flipped(new PcToDataPathIO(params))
 
diff --git a/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala b/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala
index 16e94d602e9..687e05d441b 100644
--- a/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala
+++ b/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala
@@ -8,9 +8,8 @@ import utility.XSError
 import xiangshan.backend.BackendParams
 import xiangshan.backend.Bundles.{ExuOutput, WriteBackBundle}
 import xiangshan.backend.datapath.DataConfig._
-import xiangshan.backend.regfile.RfWritePortWithConfig
+import xiangshan.backend.regfile.RfWritePortBundle
 import xiangshan.{Redirect, XSBundle, XSModule}
-import xiangshan.SrcType.v0
 import xiangshan.backend.fu.vector.Bundles.Vstart
 import xiangshan.backend.issue.SchdBlockParams
 
@@ -100,20 +99,11 @@ class WbDataPathIO()(implicit p: Parameters, params: BackendParams, schdParams:
     val vstart = Vstart()
   })
 
-  val toIntPreg = Flipped(MixedVec(Vec(params.numPregWb(IntData()),
-    new RfWritePortWithConfig(params.intPregParams.dataCfg, params.intPregParams.addrWidth))))
-
-  val toFpPreg = Flipped(MixedVec(Vec(params.numPregWb(FpData()),
-    new RfWritePortWithConfig(params.fpPregParams.dataCfg, params.fpPregParams.addrWidth))))
-
-  val toVfPreg = Flipped(MixedVec(Vec(params.numPregWb(VecData()),
-    new RfWritePortWithConfig(params.vfPregParams.dataCfg, params.vfPregParams.addrWidth))))
-
-  val toV0Preg = Flipped(MixedVec(Vec(params.numPregWb(V0Data()),
-    new RfWritePortWithConfig(params.v0PregParams.dataCfg, params.v0PregParams.addrWidth))))
-
-  val toVlPreg = Flipped(MixedVec(Vec(params.numPregWb(VlData()),
-    new RfWritePortWithConfig(params.vlPregParams.dataCfg, params.vlPregParams.addrWidth))))
+  val toIntPreg = Output(backendParams.genIntWriteBackBundle)
+  val toFpPreg = Output(backendParams.genFpWriteBackBundle)
+  val toVfPreg = Output(backendParams.genVfWriteBackBundle)
+  val toV0Preg = Output(backendParams.genV0WriteBackBundle)
+  val toVlPreg = Output(backendParams.genVlWriteBackBundle)
 
   val toCtrlBlock = new Bundle {
     val writeback: MixedVec[ValidIO[ExuOutput]] = MixedVec(schdParams.genExuOutputValidBundle.flatten)
@@ -376,11 +366,11 @@ class WbDataPath(params: BackendParams, schdParams: SchdBlockParams)(implicit p:
   (vfExuWBs zip vfExuInputs).foreach { case (wb, input) => wb.valid := input.fire }
 
   // io assign
-  private val toIntPreg: MixedVec[RfWritePortWithConfig] = MixedVecInit(intWbArbiterOut.map(x => x.bits.asIntRfWriteBundle(x.fire)).toSeq)
-  private val toFpPreg: MixedVec[RfWritePortWithConfig] = MixedVecInit(fpWbArbiterOut.map(x => x.bits.asFpRfWriteBundle(x.fire)).toSeq)
-  private val toVfPreg: MixedVec[RfWritePortWithConfig] = MixedVecInit(vfWbArbiterOut.map(x => x.bits.asVfRfWriteBundle(x.fire)).toSeq)
-  private val toV0Preg: MixedVec[RfWritePortWithConfig] = MixedVecInit(v0WbArbiterOut.map(x => x.bits.asV0RfWriteBundle(x.fire)).toSeq)
-  private val toVlPreg: MixedVec[RfWritePortWithConfig] = MixedVecInit(vlWbArbiterOut.map(x => x.bits.asVlRfWriteBundle(x.fire)).toSeq)
+  private val toIntPreg: MixedVec[RfWritePortBundle] = MixedVecInit(intWbArbiterOut.map(x => x.bits.asIntRfWriteBundle(x.fire)).toSeq)
+  private val toFpPreg: MixedVec[RfWritePortBundle] = MixedVecInit(fpWbArbiterOut.map(x => x.bits.asFpRfWriteBundle(x.fire)).toSeq)
+  private val toVfPreg: MixedVec[RfWritePortBundle] = MixedVecInit(vfWbArbiterOut.map(x => x.bits.asVfRfWriteBundle(x.fire)).toSeq)
+  private val toV0Preg: MixedVec[RfWritePortBundle] = MixedVecInit(v0WbArbiterOut.map(x => x.bits.asV0RfWriteBundle(x.fire)).toSeq)
+  private val toVlPreg: MixedVec[RfWritePortBundle] = MixedVecInit(vlWbArbiterOut.map(x => x.bits.asVlRfWriteBundle(x.fire)).toSeq)
 
   private val wb2Ctrl = if (schdParams.isIntSchd) intExuWBs
                         else if (schdParams.isFpSchd) fpExuWBs
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
index ab6002b83d2..4a9a1ff882b 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
@@ -185,16 +185,16 @@ case class ExeUnitParams(
     *
     * @return Map[ [[BigInt]], Latency]
     */
-  def fuLatencyMap(addBJU: Boolean = false): Map[FuType.OHType, Int] = {
-    val addBJUFuConfigs = if (addBJU) fuConfigs :+ BrhCfg :+ JmpCfg else fuConfigs
+  def fuLatencyMap(addJump: Boolean = false): Map[FuType.OHType, Int] = {
+    val addBJUFuConfigs = if (addJump) fuConfigs :+ JmpCfg else fuConfigs
     if (latencyCertain)
       if(needOg2) addBJUFuConfigs.map(x => (x.fuType, x.latency.latencyVal.get + 1)).toMap else addBJUFuConfigs.map(x => (x.fuType, x.latency.latencyVal.get)).toMap
     else if (hasUncertainLatencyVal)
       addBJUFuConfigs.map(x => (x.fuType, x.latency.uncertainLatencyVal)).toMap.filter(_._2.nonEmpty).map(x => (x._1, x._2.get))
     else {
-      val latencyCertainFuConfigsAddBJU = if (addBJU) latencyCertainFuConfigs :+ BrhCfg :+ JmpCfg else latencyCertainFuConfigs
-      println(s"${this.name}: latencyCertainFuConfigs = $latencyCertainFuConfigsAddBJU")
-      latencyCertainFuConfigsAddBJU.map(x => (x.fuType, x.latency.latencyVal.get)).toMap
+      val latencyCertainFuConfigsAddJump = if (addJump) latencyCertainFuConfigs :+ JmpCfg else latencyCertainFuConfigs
+      println(s"${this.name}: latencyCertainFuConfigs = $latencyCertainFuConfigsAddJump")
+      latencyCertainFuConfigsAddJump.map(x => (x.fuType, x.latency.latencyVal.get)).toMap
     }
   }
   def wakeUpFuLatencyMap: Map[FuType.OHType, Int] = {
diff --git a/src/main/scala/xiangshan/backend/issue/BypassNetwork.scala b/src/main/scala/xiangshan/backend/issue/BypassNetwork.scala
deleted file mode 100644
index d88599743e9..00000000000
--- a/src/main/scala/xiangshan/backend/issue/BypassNetwork.scala
+++ /dev/null
@@ -1,117 +0,0 @@
-/***************************************************************************************
-* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
-* Copyright (c) 2020-2021 Peng Cheng Laboratory
-*
-* XiangShan is licensed under Mulan PSL v2.
-* You can use this software according to the terms and conditions of the Mulan PSL v2.
-* You may obtain a copy of Mulan PSL v2 at:
-*          http://license.coscl.org.cn/MulanPSL2
-*
-* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-*
-* See the Mulan PSL v2 for more details.
-***************************************************************************************/
-
-package xiangshan.backend.issue
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
-
-class BypassInfo(numWays: Int, dataBits: Int) extends Bundle {
-  val valid = Vec(numWays, Bool())
-  val data = UInt(dataBits.W)
-}
-
-class BypassNetworkIO(numWays: Int, numBypass: Int, dataBits: Int) extends Bundle {
-  val hold = Input(Bool())
-  val source = Vec(numWays, Input(UInt(dataBits.W)))
-  val target = Vec(numWays, Output(UInt(dataBits.W)))
-  val bypass = Vec(numBypass, Input(new BypassInfo(numWays, dataBits)))
-}
-
-class BypassNetwork(numWays: Int, numBypass: Int, dataBits: Int)(implicit p: Parameters)
-  extends XSModule {
-
-  val io = IO(new BypassNetworkIO(numWays, numBypass, dataBits))
-
-  def doBypass(bypassValid: Seq[Bool], bypassData: Seq[UInt], baseData: UInt, debugIndex: Int = 0): UInt = {
-    val bypassVec = VecInit(bypassValid)
-    val target = Mux(bypassVec.asUInt.orR, ParallelMux(bypassValid, bypassData), baseData)
-
-    XSError(PopCount(bypassVec) > 1.U, p"bypass mask ${Binary(bypassVec.asUInt)} is not one-hot\n")
-    bypassVec.zipWithIndex.map { case (m, i) =>
-      XSDebug(bypassVec(i), p"target($debugIndex) bypassed from $i:0x${Hexadecimal(bypassData(i))}\n")
-    }
-
-    target
-  }
-
-}
-
-// Bypass at the right: RegNext(data) and compute the bypassed data at the next clock cycle
-class BypassNetworkRight(numWays: Int, numBypass: Int, dataBits: Int)(implicit p: Parameters)
-  extends BypassNetwork(numWays, numBypass, dataBits) {
-
-  val last_cycle_hold = RegInit(false.B)
-  last_cycle_hold := io.hold
-
-  val target_reg = Reg(Vec(numWays, UInt(dataBits.W)))
-  val bypass_reg = Reg(Vec(numBypass, new BypassInfo(numWays, dataBits)))
-
-  // When last cycle holds the data, no need to update it.
-  when (io.hold && !last_cycle_hold) {
-    bypass_reg.map(_.valid.map(_ := false.B))
-    target_reg := io.target
-  }.elsewhen(!io.hold) {
-    target_reg := io.source
-    for ((by_reg, by_io) <- bypass_reg.zip(io.bypass)) {
-      by_reg.data := by_io.data
-      by_reg.valid := by_io.valid
-    }
-  }
-
-  // bypass data to target
-  for (i <- 0 until numWays) {
-    io.target(i) := doBypass(bypass_reg.map(_.valid(i)), bypass_reg.map(_.data), target_reg(i))
-  }
-
-}
-
-// Bypass at the left: compute the bypassed data and RegNext(bypassed_data)
-class BypassNetworkLeft(numWays: Int, numBypass: Int, dataBits: Int)(implicit p: Parameters)
-  extends BypassNetwork(numWays, numBypass, dataBits) {
-
-  val bypassedData = Reg(io.target.cloneType)
-
-  when (!io.hold) {
-    for ((by, i) <- bypassedData.zipWithIndex) {
-      by := doBypass(io.bypass.map(_.valid(i)), io.bypass.map(_.data), io.source(i))
-    }
-  }
-
-  io.target := bypassedData
-
-}
-
-object BypassNetwork {
-  def apply(
-    numWays: Int,
-    numBypass: Int,
-    dataBits: Int,
-    optFirstStage: Boolean
-  )(implicit p: Parameters): BypassNetwork = {
-    if (optFirstStage) {
-      Module(new BypassNetworkLeft(numWays, numBypass, dataBits))
-    }
-    else {
-      Module(new BypassNetworkRight(numWays, numBypass, dataBits))
-    }
-  }
-}
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index da2e940bd37..887d761ffe4 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -112,7 +112,7 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
 
   // Modules
   val entries = Module(new Entries)
-  val fuBusyTableWrite = params.exuBlockParams.map { case x => Option.when(x.latencyValMax > 0)(Module(new FuBusyTableWrite(x.fuLatencyMap()))) }
+  val fuBusyTableWrite = params.exuBlockParams.map { case x => Option.when(x.latencyValMax > 0)(Module(new FuBusyTableWrite(x.fuLatencyMap(param.aluDeqNeedPickJump)))) }
   val fuBusyTableRead = params.exuBlockParams.map { case x => Option.when(x.latencyValMax > 0)(Module(new FuBusyTableRead(x.fuLatencyMap(param.aluDeqNeedPickJump)))) }
   val intWbBusyTableWrite = params.exuBlockParams.map { case x => Option.when(x.intLatencyCertain)(Module(new FuBusyTableWrite(x.intFuLatencyMap))) }
   val intWbBusyTableRead = params.exuBlockParams.map { case x => Option.when(x.intLatencyCertain)(Module(new FuBusyTableRead(x.intFuLatencyMap))) }
@@ -1216,7 +1216,6 @@ class IssueQueueMemBundle(implicit p: Parameters, params: IssueBlockParams) exte
     val stIssuePtr = Input(new SqPtr)
     val memWaitUpdateReq = Flipped(new MemWaitUpdateReqBundle)
   }
-  val loadFastMatch = Output(Vec(params.LdExuCnt, new IssueQueueLoadBundle))
 
   // load wakeup
   val loadWakeUp = Input(Vec(params.LdExuCnt, ValidIO(new MemWakeUpBundle)))
@@ -1241,8 +1240,6 @@ class IssueQueueMemAddrImp(implicit p: Parameters, params: IssueBlockParams)
   override lazy val io = IO(new IssueQueueMemIO).suggestName("io")
   private val memIO = io.memIO.get
 
-  memIO.loadFastMatch := 0.U.asTypeOf(memIO.loadFastMatch) // TODO: is still needed?
-
   entries.io.fromMem.get.slowResp.zipWithIndex.foreach { case (slowResp, i) =>
     slowResp.valid       := memIO.feedbackIO(i).feedbackSlow.valid
     slowResp.bits.robIdx := memIO.feedbackIO(i).feedbackSlow.bits.robIdx
diff --git a/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala b/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala
index 21ae9b4c088..112d1769969 100644
--- a/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala
+++ b/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala
@@ -2,12 +2,17 @@ package xiangshan.backend.issue
 
 import org.chipsalliance.cde.config.Parameters
 import chisel3.util._
-import utils.SeqUtils
 import xiangshan.backend.BackendParams
 import xiangshan.backend.Bundles._
 import xiangshan.backend.datapath.WakeUpSource
 import xiangshan.backend.datapath.WbConfig.PregWB
-import xiangshan.backend.fu.FuConfig.I2fCfg
+
+sealed trait SchedulerType
+
+case class IntScheduler() extends SchedulerType
+case class FpScheduler() extends SchedulerType
+case class VecScheduler() extends SchedulerType
+case class NoScheduler() extends SchedulerType
 
 case class SchdBlockParams(
   issueBlockParams: Seq[IssueBlockParams],
@@ -174,16 +179,6 @@ case class SchdBlockParams(
       .distinctBy(_.name)
   }
 
-  def genIQWakeUpInValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueIQWakeUpBundle]] = {
-    MixedVec(this.wakeUpInExuSources.map(x => {
-      val param = x.getExuParam(backendParam.allExuParams)
-      val isCopyPdest = param.copyWakeupOut
-      val copyNum = param.copyNum
-      ValidIO(new IssueQueueIQWakeUpBundle(backendParam.getExuIdx(x.name), backendParam, isCopyPdest, copyNum))
-      })
-    )
-  }
-
   def genIQWakeUpOutValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueIQWakeUpBundle]] = {
     MixedVec(this.wakeUpOutExuSources.map(x => {
       val param = x.getExuParam(backendParam.allExuParams)
@@ -228,32 +223,6 @@ case class SchdBlockParams(
     MixedVec(intBundle ++ fpBundle ++ vfBundle ++ v0Bundle ++ vlBundle)
   }
 
-  def genIntWBWakeUpSinkValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = {
-    MixedVec(backendParam.getIntWBExeGroup.map(x => ValidIO(new IssueQueueWBWakeUpBundle(x._2.map(_.exuIdx), backendParam))).toSeq)
-  }
-
-  def genFpWBWakeUpSinkValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = {
-    MixedVec(backendParam.getFpWBExeGroup.map(x => ValidIO(new IssueQueueWBWakeUpBundle(x._2.map(_.exuIdx), backendParam))).toSeq)
-  }
-
-  def genVfWBWakeUpSinkValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = {
-    MixedVec(backendParam.getVfWBExeGroup.map(x => ValidIO(new IssueQueueWBWakeUpBundle(x._2.map(_.exuIdx), backendParam))).toSeq)
-  }
-
-  def genV0WBWakeUpSinkValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = {
-    MixedVec(backendParam.getV0WBExeGroup.map(x => ValidIO(new IssueQueueWBWakeUpBundle(x._2.map(_.exuIdx), backendParam))).toSeq)
-  }
-
-  def genVlWBWakeUpSinkValidBundle(implicit p: Parameters): MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = {
-    MixedVec(backendParam.getVlWBExeGroup.map(x => ValidIO(new IssueQueueWBWakeUpBundle(x._2.map(_.exuIdx), backendParam))).toSeq)
-  }
-
-  def genWriteBackBundle(implicit p: Parameters) =  schdType match {
-    case IntScheduler() => backendParam.genIntWriteBackBundle
-    case FpScheduler() => backendParam.genFpWriteBackBundle
-    case VecScheduler() => backendParam.genVfWriteBackBundle
-
-  }
   // cfgs(issueIdx)(exuIdx)(set of exu's wb)
   def getWbCfgs: Seq[Seq[Set[PregWB]]] = {
     this.issueBlockParams.map(_.getWbCfgs)
diff --git a/src/main/scala/xiangshan/backend/issue/Scheduler.scala b/src/main/scala/xiangshan/backend/issue/Scheduler.scala
deleted file mode 100644
index 29d65450ab2..00000000000
--- a/src/main/scala/xiangshan/backend/issue/Scheduler.scala
+++ /dev/null
@@ -1,8 +0,0 @@
-package xiangshan.backend.issue
-
-sealed trait SchedulerType
-
-case class IntScheduler() extends SchedulerType
-case class FpScheduler() extends SchedulerType
-case class VecScheduler() extends SchedulerType
-case class NoScheduler() extends SchedulerType
diff --git a/src/main/scala/xiangshan/backend/regfile/Regfile.scala b/src/main/scala/xiangshan/backend/regfile/Regfile.scala
index b32f74728cd..8a307270836 100644
--- a/src/main/scala/xiangshan/backend/regfile/Regfile.scala
+++ b/src/main/scala/xiangshan/backend/regfile/Regfile.scala
@@ -45,26 +45,12 @@ class RfReadPortWithConfig(val rfReadDataCfg: DataConfig, addrWidth: Int) extend
   def readVf : Boolean = VecRegSrcDataSet .contains(rfReadDataCfg)
 }
 
-class RfWritePortWithConfig(val rfWriteDataCfg: DataConfig, addrWidth: Int) extends Bundle {
-  val wen = Input(Bool())
-  val addr = Input(UInt(addrWidth.W))
-  val data = Input(UInt(rfWriteDataCfg.dataWidth.W))
-  val intWen = Input(Bool())
-  val fpWen = Input(Bool())
-  val vecWen = Input(Bool())
-  val v0Wen = Input(Bool())
-  val vlWen = Input(Bool())
-  def writeInt: Boolean = rfWriteDataCfg.isInstanceOf[IntData]
-  def writeFp : Boolean = rfWriteDataCfg.isInstanceOf[FpData]
-  def writeVec: Boolean = rfWriteDataCfg.isInstanceOf[VecData]
-  def writeV0 : Boolean = rfWriteDataCfg.isInstanceOf[V0Data]
-  def writeVl : Boolean = rfWriteDataCfg.isInstanceOf[VlData]
-}
-
-class RfWritePortBundle(val rfWriteDataCfg: DataConfig, addrWidth: Int) extends Bundle {
+class RfWritePortBundle(val pregParams: PregParams) extends Bundle {
+  val dataWidth = pregParams.dataCfg.dataWidth
+  val addrWidth = pregParams.addrWidth
   val wen = Bool()
   val pdest = UInt(addrWidth.W)
-  val data = UInt(rfWriteDataCfg.dataWidth.W)
+  val data = UInt(dataWidth.W)
   val rfWen = Bool()
   val fpWen = Bool()
   val vecWen = Bool()
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 5d2dbe02ed9..7ee4d70c48b 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -90,9 +90,6 @@ class Std(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
 class ooo_to_mem(implicit p: Parameters) extends MemBlockBundle {
   val backendToTopBypass = Flipped(new BackendToTopBundle)
 
-  val loadFastMatch = Vec(LdExuCnt, Input(UInt(LdExuCnt.W)))
-  val loadFastFuOpType = Vec(LdExuCnt, Input(FuOpType()))
-  val loadFastImm = Vec(LdExuCnt, Input(UInt(12.W)))
   val sfence = Input(new SfenceBundle)
   val tlbCsr = Input(new TlbCsrBundle)
   val lsqio = new Bundle {
```
