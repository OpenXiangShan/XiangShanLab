# Commit Log
- Issue: #4033
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4033
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4033
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4033
- Changed files: 12
- Additions: 95
- Deletions: 169

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/dispatch/Dispatch2IqFpImp.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala`
- `src/main/scala/xiangshan/backend/issue/CancelNetwork.scala`
- `src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala`
- `src/main/scala/xiangshan/backend/issue/EnqEntry.scala`
- `src/main/scala/xiangshan/backend/issue/Entries.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 2d3dd044a05..10146f0e7ad 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -44,7 +44,7 @@ import xiangshan.backend.exu.ExuBlock
 import xiangshan.backend.fu.vector.Bundles.{VConfig, VType}
 import xiangshan.backend.fu.{FenceIO, FenceToSbuffer, FuConfig, FuType, PFEvent, PerfCounterIO}
 import xiangshan.backend.issue.EntryBundles._
-import xiangshan.backend.issue.{CancelNetwork, Scheduler, SchedulerArithImp, SchedulerImpBase, SchedulerMemImp}
+import xiangshan.backend.issue.{Scheduler, SchedulerArithImp, SchedulerImpBase, SchedulerMemImp}
 import xiangshan.backend.rob.{RobCoreTopDownIO, RobDebugRollingIO, RobLsqIO, RobPtr}
 import xiangshan.backend.trace.TraceCoreInterface
 import xiangshan.frontend.{FtqPtr, FtqRead, PreDecodeInfo}
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 90cf37b118e..4a738d65f44 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -23,6 +23,7 @@ import xiangshan.frontend._
 import xiangshan.mem.{LqPtr, SqPtr}
 import yunsuan.vector.VIFuParam
 import xiangshan.backend.trace._
+import utility._
 
 object Bundles {
   /**
@@ -636,7 +637,7 @@ object Bundles {
     val sqIdx = if (params.hasMemAddrFu || params.hasStdFu) Some(new SqPtr) else None
     val lqIdx = if (params.hasMemAddrFu) Some(new LqPtr) else None
     val dataSources = Vec(params.numRegSrc, DataSource())
-    val l1ExuOH = OptionWrapper(params.isIQWakeUpSink, Vec(params.numRegSrc, ExuVec()))
+    val exuSources = OptionWrapper(params.isIQWakeUpSink, Vec(params.numRegSrc, ExuSource(params)))
     val srcTimer = OptionWrapper(params.isIQWakeUpSink, Vec(params.numRegSrc, UInt(3.W)))
     val loadDependency = OptionWrapper(params.needLoadDependency, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
 
@@ -644,22 +645,6 @@ object Bundles {
 
     def exuIdx = this.params.exuIdx
 
-    def needCancel(og0CancelOH: UInt, og1CancelOH: UInt) : Bool = {
-      if (params.isIQWakeUpSink) {
-        require(
-          og0CancelOH.getWidth == l1ExuOH.get.head.getWidth,
-          s"cancelVecSize: {og0: ${og0CancelOH.getWidth}, og1: ${og1CancelOH.getWidth}}"
-        )
-        val l1Cancel: Bool = l1ExuOH.get.zip(srcTimer.get).map {
-          case(exuOH: Vec[Bool], srcTimer: UInt) =>
-            (exuOH.asUInt & og0CancelOH).orR && srcTimer === 1.U
-        }.reduce(_ | _)
-        l1Cancel
-      } else {
-        false.B
-      }
-    }
-
     def fromIssueBundle(source: IssueQueueIssueBundle): Unit = {
       // src is assigned to rfReadData
       this.fuType        := source.common.fuType
@@ -670,7 +655,7 @@ object Bundles {
       this.isFirstIssue  := source.common.isFirstIssue // Only used by mem debug log
       this.iqIdx         := source.common.iqIdx        // Only used by mem feedback
       this.dataSources   := source.common.dataSources
-      this.l1ExuOH       .foreach(_ := source.common.l1ExuOH.get)
+      this.exuSources    .foreach(_ := source.common.exuSources.get)
       this.rfWen         .foreach(_ := source.common.rfWen.get)
       this.fpWen         .foreach(_ := source.common.fpWen.get)
       this.vecWen        .foreach(_ := source.common.vecWen.get)
@@ -886,12 +871,43 @@ object Bundles {
     def width = 4 // 0~15 // Todo: assosiate it with FuConfig
   }
 
-  object ExuOH {
-    def apply(exuNum: Int): UInt = UInt(exuNum.W)
+  class ExuSource(exuNum: Int)(implicit p: Parameters) extends XSBundle {
+    val value = UInt(log2Ceil(exuNum + 1).W)
 
-    def apply()(implicit p: Parameters): UInt = UInt(width.W)
+    val allExuNum = p(XSCoreParamsKey).backendParams.numExu
 
-    def width(implicit p: Parameters): Int = p(XSCoreParamsKey).backendParams.numExu
+    def toExuOH(num: Int, filter: Seq[Int]): Vec[Bool] = {
+      require(num == filter.size)
+      val encodedExuOH = UIntToOH(this.value)(num, 1)
+      val ext = Module(new UIntExtractor(allExuNum, filter))
+      ext.io.in := encodedExuOH
+      VecInit(ext.io.out.asBools.zipWithIndex.map{ case(out, idx) =>
+        if (filter.contains(idx)) out
+        else false.B
+      })
+    }
+
+    def toExuOH(exuParams: ExeUnitParams): Vec[Bool] = {
+      toExuOH(exuParams.numWakeupFromIQ, exuParams.iqWakeUpSinkPairs.map(x => x.source.getExuParam(p(XSCoreParamsKey).backendParams.allExuParams).exuIdx))
+    }
+
+    def toExuOH(iqParams: IssueBlockParams): Vec[Bool] = {
+      toExuOH(iqParams.numWakeupFromIQ, iqParams.wakeUpSourceExuIdx)
+    }
+
+    def fromExuOH(iqParams: IssueBlockParams, exuOH: UInt): UInt = {
+      val comp = Module(new UIntCompressor(allExuNum, iqParams.wakeUpSourceExuIdx))
+      comp.io.in := exuOH
+      OHToUInt(Cat(comp.io.out, 0.U(1.W)))
+    }
+  }
+
+  object ExuSource {
+    def apply(exuNum: Int)(implicit p: Parameters) = new ExuSource(exuNum)
+
+    def apply(params: ExeUnitParams)(implicit p: Parameters) = new ExuSource(params.numWakeupFromIQ)
+
+    def apply()(implicit p: Parameters, params: IssueBlockParams) = new ExuSource(params.numWakeupFromIQ)
   }
 
   object ExuVec {
diff --git a/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala b/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
index 8ebb01b937f..ae6e8055e8a 100644
--- a/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
+++ b/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
@@ -6,7 +6,7 @@ import chisel3.util._
 import utility.{GatedValidRegNext, SignExt, ZeroExt}
 import xiangshan.{XSBundle, XSModule}
 import xiangshan.backend.BackendParams
-import xiangshan.backend.Bundles.{ExuBypassBundle, ExuInput, ExuOH, ExuOutput, ExuVec, ImmInfo}
+import xiangshan.backend.Bundles.{ExuBypassBundle, ExuInput, ExuOutput, ExuVec, ImmInfo}
 import xiangshan.backend.issue.{FpScheduler, ImmExtractor, IntScheduler, MemScheduler, VfScheduler}
 import xiangshan.backend.datapath.DataConfig.RegDataMaxWidth
 import xiangshan.backend.decode.ImmUnion
@@ -83,22 +83,11 @@ class BypassNetwork()(implicit p: Parameters, params: BackendParams) extends XSM
   // (exuIdx, srcIdx, bypassExuIdx)
   private val forwardOrBypassValidVec3: MixedVec[Vec[Vec[Bool]]] = MixedVecInit(
     fromDPs.map { (x: DecoupledIO[ExuInput]) =>
-      val wakeUpSourceIdx = x.bits.params.iqWakeUpSinkPairs.map(x => x.source.getExuParam(params.allExuParams).exuIdx)
-      val mask = Wire(chiselTypeOf(x.bits.l1ExuOH.getOrElse(VecInit(Seq.fill(x.bits.params.numRegSrc max 1)(VecInit(0.U(ExuVec.width.W).asBools))))))
-      mask.map{ case m =>
-        val vecMask = Wire(Vec(m.getWidth, Bool()))
-        vecMask.zipWithIndex.map{ case(v, i) =>
-          if (wakeUpSourceIdx.contains(i)) v := true.B else v := false.B
-        }
-        m := vecMask
-      }
       println(s"[BypassNetwork] ${x.bits.params.name} numRegSrc: ${x.bits.params.numRegSrc}")
-      VecInit(x.bits.l1ExuOH.getOrElse(
+      VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.params))).getOrElse(
         // TODO: remove tmp max 1 for fake HYU1
-        VecInit(Seq.fill(x.bits.params.numRegSrc max 1)(VecInit(0.U(ExuVec.width.W).asBools)))
-      ).zip(mask).map{ case (l,m) =>
-        VecInit(l.zip(m).map(x => x._1 && x._2))
-      })
+        VecInit(Seq.fill(x.bits.params.numRegSrc max 1)(VecInit(0.U(params.numExu.W).asBools)))
+      ))
     }
   )
 
@@ -121,8 +110,8 @@ class BypassNetwork()(implicit p: Parameters, params: BackendParams) extends XSM
   private val fromDPsHasBypass2Sink   = fromDPs.filter(x => x.bits.params.isIQWakeUpSink && x.bits.params.readVfRf && (x.bits.params.isVfExeUnit || x.bits.params.isMemExeUnit)).map(_.bits.params.exuIdx)
 
   private val bypass2ValidVec3 = MixedVecInit(
-    fromDPsHasBypass2Sink.map(forwardOrBypassValidVec3(_)).map(exu => VecInit(exu.map(l1ExuOH => 
-      VecInit(fromDPsHasBypass2Source.map(l1ExuOH(_))).asUInt
+    fromDPsHasBypass2Sink.map(forwardOrBypassValidVec3(_)).map(exu => VecInit(exu.map(exuOH => 
+      VecInit(fromDPsHasBypass2Source.map(exuOH(_))).asUInt
     )))
   )
   if(params.debugEn){
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 3ad5ef5f0ca..c019dc56be0 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -575,8 +575,6 @@ class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params
   val is_0latency = Wire(Vec(og0_cancel_no_load.size, Bool()))
   is_0latency := exuParamsNoLoad.map(x => is0latency(x._1.bits.common.fuType))
   val og0_cancel_delay = RegNext(VecInit(og0_cancel_no_load.zip(is_0latency).map(x => x._1 && x._2)))
-  val isVfScheduler = VecInit(exuParamsNoLoad.map(x => x._2.schdType.isInstanceOf[VfScheduler].B))
-  val og0_cancel_delay_for_mem = VecInit(og0_cancel_delay.zip(isVfScheduler).map(x => x._1 && !x._2))
   for (i <- fromIQ.indices) {
     for (j <- fromIQ(i).indices) {
       // IQ(s0) --[Ctrl]--> s1Reg ---------- begin
@@ -596,11 +594,10 @@ class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params
       val s1_flush = s0.bits.common.robIdx.needFlush(Seq(io.flush, RegNextWithEnable(io.flush)))
       val s1_cancel = og1FailedVec2(i)(j)
       val s0_cancel = Wire(Bool())
-      val og0_cancel_delay_need = if (s0.bits.exuParams.schdType.isInstanceOf[MemScheduler]) og0_cancel_delay_for_mem else og0_cancel_delay
       if (s0.bits.exuParams.isIQWakeUpSink) {
-        val exuOHNoLoad = s0.bits.common.l1ExuOH.get.map(x => x.asTypeOf(Vec(x.getWidth, Bool())).zip(params.allExuParams).filter(!_._2.hasLoadFu).map(_._1))
+        val exuOHNoLoad = s0.bits.common.exuSources.get.map(x => x.toExuOH(s0.bits.exuParams).zip(params.allExuParams).filter(!_._2.hasLoadFu).map(_._1))
         s0_cancel := exuOHNoLoad.zip(s0.bits.common.dataSources).map{
-          case (exuOH, dataSource) => (VecInit(exuOH).asUInt & og0_cancel_delay_need.asUInt).orR && dataSource.readForward
+          case (exuOH, dataSource) => (VecInit(exuOH).asUInt & og0_cancel_delay.asUInt).orR && dataSource.readForward
         }.reduce(_ || _) && s0.valid
       } else s0_cancel := false.B
       val s0_ldCancel = LoadShouldCancel(s0.bits.common.loadDependency, io.ldCancel)
@@ -668,8 +665,6 @@ class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params
     dontTouch(og0_cancel_no_load)
     dontTouch(is_0latency)
     dontTouch(og0_cancel_delay)
-    dontTouch(isVfScheduler)
-    dontTouch(og0_cancel_delay_for_mem)
   }
   for (i <- toExu.indices) {
     for (j <- toExu(i).indices) {
diff --git a/src/main/scala/xiangshan/backend/dispatch/Dispatch2IqFpImp.scala b/src/main/scala/xiangshan/backend/dispatch/Dispatch2IqFpImp.scala
index ed3ed0b2cde..c3aabe0ebf3 100644
--- a/src/main/scala/xiangshan/backend/dispatch/Dispatch2IqFpImp.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/Dispatch2IqFpImp.scala
@@ -10,7 +10,7 @@ import xiangshan._
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.backend.rename.BusyTableReadIO
 import xiangshan.mem.LsqEnqIO
-import xiangshan.backend.Bundles.{DynInst, ExuOH}
+import xiangshan.backend.Bundles.DynInst
 import xiangshan.backend.datapath.DataSource
 import xiangshan.backend.fu.FuType.{FuTypeOrR, falu}
 import xiangshan.backend.issue._
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
index 46e05805259..2adfa8458b6 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
@@ -337,6 +337,8 @@ case class ExeUnitParams(
 
   def isIQWakeUpSink = this.iqWakeUpSinkPairs.nonEmpty
 
+  def numWakeupFromIQ = this.iqWakeUpSinkPairs.size
+
   def getIntWBPort = {
     wbPortConfigs.collectFirst {
       case x: IntWB => x
diff --git a/src/main/scala/xiangshan/backend/issue/CancelNetwork.scala b/src/main/scala/xiangshan/backend/issue/CancelNetwork.scala
deleted file mode 100644
index 5a130dd0c4a..00000000000
--- a/src/main/scala/xiangshan/backend/issue/CancelNetwork.scala
+++ /dev/null
@@ -1,64 +0,0 @@
-package xiangshan.backend.issue
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
-import xiangshan.backend.BackendParams
-import xiangshan.backend.Bundles.{ExuOH, IssueQueueIssueBundle}
-
-class CancelNetworkIO(backendParams: BackendParams)(implicit p: Parameters) extends Bundle {
-  private val numExu = backendParams.numExu
-
-  val in = new Bundle {
-    val int = Flipped(MixedVec(backendParams.intSchdParams.get.issueBlockParams.map(_.genIssueDecoupledBundle)))
-    val vf  = Flipped(MixedVec(backendParams.vfSchdParams.get.issueBlockParams.map(_.genIssueDecoupledBundle)))
-    val mem = Flipped(MixedVec(backendParams.memSchdParams.get.issueBlockParams.map(_.genIssueDecoupledBundle)))
-    val og0CancelOH = Input(ExuOH(numExu))
-    // Todo: remove this when no uop would be canceled at og1
-    val og1CancelOH = Input(ExuOH(numExu))
-
-    def allIssue: Seq[DecoupledIO[IssueQueueIssueBundle]] = (Seq() :+ int :+ vf :+ mem).flatten.flatten
-  }
-  val out = new Bundle {
-    val int = MixedVec(backendParams.intSchdParams.get.issueBlockParams.map(_.genIssueDecoupledBundle))
-    val vf  = MixedVec(backendParams.vfSchdParams.get.issueBlockParams.map(_.genIssueDecoupledBundle))
-    val mem = MixedVec(backendParams.memSchdParams.get.issueBlockParams.map(_.genIssueDecoupledBundle))
-    val og0CancelOH = Output(ExuOH(numExu))
-    def allIssue: Seq[DecoupledIO[IssueQueueIssueBundle]] = (Seq() :+ int :+ vf :+ mem).flatten.flatten
-  }
-}
-
-class CancelNetwork(backendParams: BackendParams)(implicit p: Parameters) extends LazyModule {
-  override def shouldBeInlined: Boolean = false
-
-  lazy val module = new CancelNetworkImp(backendParams, this)
-}
-
-class CancelNetworkImp(backendParams: BackendParams, override val wrapper: LazyModule)(implicit p: Parameters) extends LazyModuleImp(wrapper) {
-  private val numExu = backendParams.numExu
-  private val allExuParams = backendParams.allExuParams
-
-  val io = IO(new CancelNetworkIO(backendParams))
-
-  private val og0CancelOH = Wire(ExuOH(numExu))
-  private val og1CancelOH = WireInit(io.in.og1CancelOH)
-  private val transferredCancelOH = RegInit(0.U(numExu.W))
-
-  private val isInferWakeUpVec = WireInit(VecInit(allExuParams.map(_.isIQWakeUpSink.B)))
-  if(backendParams.debugEn) {
-    dontTouch(isInferWakeUpVec)
-  }
-
-  og0CancelOH := io.in.og0CancelOH | transferredCancelOH
-
-  transferredCancelOH := VecInit(io.in.allIssue.zip(io.out.allIssue).map(x => x._1.fire && !x._2.fire)).asUInt
-
-  io.out.allIssue.zip(io.in.allIssue).zipWithIndex.foreach { case ((out, in), i) =>
-    out.valid := in.valid && !in.bits.common.needCancel(og0CancelOH, og1CancelOH)
-    out.bits := in.bits
-    in.ready := out.ready
-  }
-
-  io.out.og0CancelOH := transferredCancelOH
-}
diff --git a/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala b/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala
index 5c22f0fb4fd..106403410fa 100644
--- a/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala
+++ b/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala
@@ -10,7 +10,7 @@ import xiangshan._
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.backend.rename.{BusyTableReadIO,VlBusyTableReadIO}
 import xiangshan.mem._
-import xiangshan.backend.Bundles.{DynInst, ExuOH}
+import xiangshan.backend.Bundles.DynInst
 import xiangshan.backend.datapath.DataSource
 import xiangshan.backend.fu.FuType.FuTypeOrR
 import xiangshan.backend.dispatch.Dispatch2IqFpImp
diff --git a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
index c35999c4f35..41efcbf36e1 100644
--- a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
@@ -38,7 +38,7 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
   val currentStatus               = Wire(new Status())
   val enqDelaySrcState            = Wire(Vec(params.numRegSrc, SrcState()))
   val enqDelayDataSources         = Wire(Vec(params.numRegSrc, DataSource()))
-  val enqDelaySrcWakeUpL1ExuOH    = OptionWrapper(params.hasIQWakeUp, Wire(Vec(params.numRegSrc, ExuVec())))
+  val enqDelayExuSources          = OptionWrapper(params.hasIQWakeUp, Wire(Vec(params.numRegSrc, ExuSource())))
   val enqDelaySrcLoadDependency   = Wire(Vec(params.numRegSrc, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))))
   val enqDelayUseRegCache         = OptionWrapper(params.needReadRegCache, Wire(Vec(params.numRegSrc, Bool())))
   val enqDelayRegCacheIdx         = OptionWrapper(params.needReadRegCache, Wire(Vec(params.numRegSrc, UInt(RegCacheIdxWidth.W))))
@@ -92,23 +92,23 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
                                                     (enqDelayOut1.srcWakeUpByIQ(i).asBool && enqDelay1IsWakeupByMemIQ)   -> DataSource.bypass2,
                                                     (enqDelayOut2.srcWakeUpByIQ(i).asBool && !enqDelay2IsWakeupByMemIQ)  -> DataSource.bypass2,
                                                  ))
-      enqDelaySrcWakeUpL1ExuOH.get(i)         := Mux(enqDelay1WakeUpValid, 
-                                                      Mux1H(enqDelay1WakeUpOH, params.wakeUpSourceExuIdx.map(x => VecInit(MathUtils.IntToOH(x).U(backendParams.numExu.W).asBools)).toSeq),
-                                                      Mux1H(enqDelay2WakeUpOH, params.wakeUpSourceExuIdx.map(x => VecInit(MathUtils.IntToOH(x).U(backendParams.numExu.W).asBools)).toSeq))
+      enqDelayExuSources.get(i).value         := Mux(enqDelay1WakeUpValid, 
+                                                      ExuSource().fromExuOH(params, Mux1H(enqDelay1WakeUpOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(backendParams.numExu.W)))),
+                                                      ExuSource().fromExuOH(params, Mux1H(enqDelay2WakeUpOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(backendParams.numExu.W)))))
     }
     else if (params.inMemSchd && params.readVfRf && params.hasIQWakeUp) {
       enqDelayDataSources(i).value            := MuxCase(entryReg.status.srcStatus(i).dataSources.value, Seq(
                                                     enqDelayOut1.srcWakeUpByIQ(i).asBool                                 -> DataSource.bypass,
                                                     (enqDelayOut2.srcWakeUpByIQ(i).asBool && enqDelay2IsWakeupByVfIQ)    -> DataSource.bypass2,
                                                  ))
-      enqDelaySrcWakeUpL1ExuOH.get(i)         := Mux(enqDelay1WakeUpValid, 
-                                                      Mux1H(enqDelay1WakeUpOH, params.wakeUpSourceExuIdx.map(x => VecInit(MathUtils.IntToOH(x).U(backendParams.numExu.W).asBools)).toSeq),
-                                                      Mux1H(enqDelay2WakeUpOH,  params.wakeUpSourceExuIdx.map(x => VecInit(MathUtils.IntToOH(x).U(backendParams.numExu.W).asBools)).toSeq))
+      enqDelayExuSources.get(i).value         := Mux(enqDelay1WakeUpValid, 
+                                                      ExuSource().fromExuOH(params, Mux1H(enqDelay1WakeUpOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(backendParams.numExu.W)))),
+                                                      ExuSource().fromExuOH(params, Mux1H(enqDelay2WakeUpOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(backendParams.numExu.W)))))
     }
     else {
       enqDelayDataSources(i).value            := Mux(enqDelayOut1.srcWakeUpByIQ(i).asBool, DataSource.bypass, entryReg.status.srcStatus(i).dataSources.value)
       if (params.hasIQWakeUp) {
-        enqDelaySrcWakeUpL1ExuOH.get(i)       := Mux1H(enqDelay1WakeUpOH, params.wakeUpSourceExuIdx.map(x => VecInit(MathUtils.IntToOH(x).U(backendParams.numExu.W).asBools)).toSeq)
+        enqDelayExuSources.get(i).value       := ExuSource().fromExuOH(params, Mux1H(enqDelay1WakeUpOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(backendParams.numExu.W))))
       }
     }
 
@@ -143,10 +143,9 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
   }
 
   if (params.hasIQWakeUp) {
-    currentStatus.srcStatus.map(_.srcWakeUpL1ExuOH.get).zip(entryReg.status.srcStatus.map(_.srcWakeUpL1ExuOH.get)).zip(enqDelaySrcWakeUpL1ExuOH.get).foreach {
-      case ((currExuOH, regExuOH), enqDelayExuOH) =>
-        currExuOH := 0.U.asTypeOf(currExuOH)
-        params.wakeUpSourceExuIdx.foreach(x => currExuOH(x) := Mux(enqDelayValidReg, enqDelayExuOH(x), regExuOH(x)))
+    currentStatus.srcStatus.map(_.exuSources.get).zip(entryReg.status.srcStatus.map(_.exuSources.get)).zip(enqDelayExuSources.get).foreach {
+      case ((currExu, regExu), enqDelayExu) =>
+        currExu := Mux(enqDelayValidReg, enqDelayExu, regExu)
     }
   }
 
diff --git a/src/main/scala/xiangshan/backend/issue/Entries.scala b/src/main/scala/xiangshan/backend/issue/Entries.scala
index 26319bcc040..c5284506702 100644
--- a/src/main/scala/xiangshan/backend/issue/Entries.scala
+++ b/src/main/scala/xiangshan/backend/issue/Entries.scala
@@ -88,7 +88,7 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   //src status
   val dataSourceVec       = Wire(Vec(params.numEntries, Vec(params.numRegSrc, DataSource())))
   val loadDependencyVec   = Wire(Vec(params.numEntries, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))))
-  val srcWakeUpL1ExuOHVec = OptionWrapper(params.hasIQWakeUp, Wire(Vec(params.numEntries, Vec(params.numRegSrc, ExuVec()))))
+  val exuSourceVec        = OptionWrapper(params.hasIQWakeUp, Wire(Vec(params.numEntries, Vec(params.numRegSrc, ExuSource()))))
   //deq sel
   val deqSelVec           = Wire(Vec(params.numEntries, Bool()))
   val issueRespVec        = Wire(Vec(params.numEntries, ValidIO(new EntryDeqRespBundle)))
@@ -389,7 +389,7 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   io.canIssue                       := canIssueVec.asUInt
   io.fuType                         := fuTypeVec
   io.dataSources                    := dataSourceVec
-  io.srcWakeUpL1ExuOH.foreach(_     := srcWakeUpL1ExuOHVec.get)
+  io.exuSources.foreach(_           := exuSourceVec.get)
   io.loadDependency                 := loadDependencyVec
   io.isFirstIssue.zipWithIndex.foreach{ case (isFirstIssue, deqIdx) =>
     isFirstIssue                    := io.deqSelOH(deqIdx).valid && Mux1H(io.deqSelOH(deqIdx).bits, isFirstIssueVec)
@@ -423,16 +423,14 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
     canIssueVec(entryIdx)       := out.canIssue
     fuTypeVec(entryIdx)         := out.fuType
     robIdxVec(entryIdx)         := out.robIdx
-    dataSourceVec(entryIdx)     := out.dataSource
+    dataSourceVec(entryIdx)     := out.dataSources
     isFirstIssueVec(entryIdx)   := out.isFirstIssue
     entries(entryIdx)           := out.entry
     deqPortIdxReadVec(entryIdx) := out.deqPortIdxRead
     issueTimerVec(entryIdx)     := out.issueTimerRead
     loadDependencyVec(entryIdx) := out.entry.bits.status.mergedLoadDependency
     cancelBypassVec(entryIdx)   := out.cancelBypass
-    if (params.hasIQWakeUp) {
-      srcWakeUpL1ExuOHVec.get(entryIdx)     := out.srcWakeUpL1ExuOH.get
-    }
+    exuSourceVec.foreach(_(entryIdx) := out.exuSources.get)
     if (params.needFeedBackSqIdx || params.needFeedBackLqIdx) {
       sqIdxVec.get(entryIdx) := out.entry.bits.payload.sqIdx
       lqIdxVec.get(entryIdx) := out.entry.bits.payload.lqIdx
@@ -549,7 +547,7 @@ class EntriesIO(implicit p: Parameters, params: IssueBlockParams) extends XSBund
   val fuType              = Vec(params.numEntries, Output(FuType()))
   val dataSources         = Vec(params.numEntries, Vec(params.numRegSrc, Output(DataSource())))
   val loadDependency      = Vec(params.numEntries, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
-  val srcWakeUpL1ExuOH    = OptionWrapper(params.hasIQWakeUp, Vec(params.numEntries, Vec(params.numRegSrc, Output(ExuVec()))))
+  val exuSources          = OptionWrapper(params.hasIQWakeUp, Vec(params.numEntries, Vec(params.numRegSrc, Output(ExuSource()))))
   //deq status
   val isFirstIssue        = Vec(params.numDeq, Output(Bool()))
   val deqEntry            = Vec(params.numDeq, ValidIO(new EntryBundle))
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index 5eb2fbd3e5e..bb642113534 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -52,7 +52,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val srcState              = SrcState()
     val dataSources           = DataSource()
     val srcLoadDependency     = Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))
-    val srcWakeUpL1ExuOH      = Option.when(params.hasIQWakeUp)(ExuVec())
+    val exuSources            = Option.when(params.hasIQWakeUp)(ExuSource())
     //reg cache
     val useRegCache           = Option.when(params.needReadRegCache)(Bool())
     val regCacheIdx           = Option.when(params.needReadRegCache)(UInt(RegCacheIdxWidth.W))
@@ -132,8 +132,8 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val robIdx                = Output(new RobPtr)
     val uopIdx                = Option.when(params.isVecMemIQ)(Output(UopIdx()))
     //src
-    val dataSource            = Vec(params.numRegSrc, Output(DataSource()))
-    val srcWakeUpL1ExuOH      = Option.when(params.hasIQWakeUp)(Vec(params.numRegSrc, Output(ExuVec())))
+    val dataSources           = Vec(params.numRegSrc, Output(DataSource()))
+    val exuSources            = Option.when(params.hasIQWakeUp)(Vec(params.numRegSrc, Output(ExuSource())))
     //deq
     val isFirstIssue          = Output(Bool())
     val entry                 = ValidIO(new EntryBundle)
@@ -224,7 +224,6 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val srcWakeupByIQ                             = Vec(params.numRegSrc, Vec(params.numWakeupFromIQ, Bool()))
     val srcWakeupByIQWithoutCancel                = Vec(params.numRegSrc, Vec(params.numWakeupFromIQ, Bool()))
     val srcWakeupByIQButCancel                    = Vec(params.numRegSrc, Vec(params.numWakeupFromIQ, Bool()))
-    val srcWakeupL1ExuOH                          = Vec(params.numRegSrc, ExuVec())
     val wakeupLoadDependencyByIQVec               = Vec(params.numWakeupFromIQ, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
     val shiftedWakeupLoadDependencyByIQVec        = Vec(params.numWakeupFromIQ, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
     val canIssueBypass                            = Bool()
@@ -247,11 +246,6 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     hasIQWakeupGet.srcWakeupByIQButCancel           := wakeupVec.map(x => VecInit(x.zip(cancelSel).map { case (wakeup, cancel) => wakeup && cancel }))
     hasIQWakeupGet.srcWakeupByIQWithoutCancel       := wakeupVec.map(x => VecInit(x))
     hasIQWakeupGet.wakeupLoadDependencyByIQVec      := commonIn.wakeUpFromIQ.map(_.bits.loadDependency).toSeq
-    hasIQWakeupGet.srcWakeupL1ExuOH.zip(status.srcStatus.map(_.srcWakeUpL1ExuOH.get)).foreach {
-      case (exuOH, regExuOH) =>
-        exuOH                                       := 0.U.asTypeOf(exuOH)
-        params.wakeUpSourceExuIdx.foreach(x => exuOH(x) := regExuOH(x))
-    }
     hasIQWakeupGet.canIssueBypass                   := validReg && !status.issued && !status.blocked &&
       VecInit(status.srcStatus.map(_.srcState).zip(hasIQWakeupGet.srcWakeupByIQWithoutCancel).zipWithIndex.map { case ((state, wakeupVec), srcIdx) =>
         wakeupVec.asUInt.orR | state
@@ -273,9 +267,9 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     }
   }
 
-  def wakeUpByVf(OH: Vec[Bool])(implicit p: Parameters): Bool = {
+  def wakeUpByVf(exuSource: ExuSource)(implicit p: Parameters, params: IssueBlockParams): Bool = {
     val allExuParams = p(XSCoreParamsKey).backendParams.allExuParams
-    OH.zip(allExuParams).map{case (oh,e) =>
+    exuSource.toExuOH(params).zip(allExuParams).map{case (oh,e) =>
       if (e.isVfExeUnit) oh else false.B
     }.reduce(_ || _)
   }
@@ -343,8 +337,8 @@ object EntryBundles extends HasCircularQueuePtrHelper {
                                                             // Vf / Int -> Mem
                                                             MuxCase(srcStatus.dataSources.value, Seq(
                                                               wakeupByIQ                                                               -> DataSource.bypass,
-                                                              (srcStatus.dataSources.readBypass && wakeUpByVf(srcStatus.srcWakeUpL1ExuOH.get)) -> DataSource.bypass2,
-                                                              (srcStatus.dataSources.readBypass && !wakeUpByVf(srcStatus.srcWakeUpL1ExuOH.get)) -> DataSource.reg,
+                                                              (srcStatus.dataSources.readBypass && wakeUpByVf(srcStatus.exuSources.get)) -> DataSource.bypass2,
+                                                              (srcStatus.dataSources.readBypass && !wakeUpByVf(srcStatus.exuSources.get)) -> DataSource.reg,
                                                               srcStatus.dataSources.readBypass2                                        -> DataSource.reg,
                                                             ))
                                                           }
@@ -356,7 +350,9 @@ object EntryBundles extends HasCircularQueuePtrHelper {
                                                             ))
                                                           })
       if(params.hasIQWakeUp) {
-        ExuOHGen(srcStatusNext.srcWakeUpL1ExuOH.get, wakeupByIQOH, hasIQWakeupGet.srcWakeupL1ExuOH(srcIdx))
+        srcStatusNext.exuSources.get.value            := Mux(wakeupByIQOH.asUInt.orR,
+                                                            ExuSource().fromExuOH(params, Mux1H(wakeupByIQOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(p(XSCoreParamsKey).backendParams.numExu.W)))),
+                                                            srcStatus.exuSources.get.value)
         srcStatusNext.srcLoadDependency               := Mux(wakeupByIQ,
                                                             Mux1H(wakeupByIQOH, hasIQWakeupGet.shiftedWakeupLoadDependencyByIQVec),
                                                             common.srcLoadDependencyNext(srcIdx))
@@ -397,7 +393,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
                                                           else common.canIssue && !common.flushed)
     commonOut.fuType                                  := IQFuType.readFuType(status.fuType, params.getFuCfgs.map(_.fuType)).asUInt
     commonOut.robIdx                                  := status.robIdx
-    commonOut.dataSource.zipWithIndex.foreach{ case (dataSourceOut, srcIdx) =>
+    commonOut.dataSources.zipWithIndex.foreach{ case (dataSourceOut, srcIdx) =>
       val wakeupByIQWithoutCancel = hasIQWakeupGet.srcWakeupByIQWithoutCancel(srcIdx).asUInt.orR
       val wakeupByIQWithoutCancelOH = hasIQWakeupGet.srcWakeupByIQWithoutCancel(srcIdx)
       val isWakeupByMemIQ = wakeupByIQWithoutCancelOH.zip(commonIn.wakeUpFromIQ).filter(_._2.bits.params.isMemExeUnit).map(_._1).fold(false.B)(_ || _)
@@ -430,12 +426,14 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     commonOut.deqPortIdxRead                          := status.deqPortIdx
 
     if(params.hasIQWakeUp) {
-      commonOut.srcWakeUpL1ExuOH.get.zipWithIndex.foreach{ case (exuOHOut, srcIdx) =>
+      commonOut.exuSources.get.zipWithIndex.foreach{ case (exuSourceOut, srcIdx) =>
         val wakeupByIQWithoutCancelOH = hasIQWakeupGet.srcWakeupByIQWithoutCancel(srcIdx)
         if (isComp)
-          ExuOHGen(exuOHOut, wakeupByIQWithoutCancelOH, hasIQWakeupGet.srcWakeupL1ExuOH(srcIdx))
+          exuSourceOut.value := Mux(wakeupByIQWithoutCancelOH.asUInt.orR,
+                                    ExuSource().fromExuOH(params, Mux1H(wakeupByIQWithoutCancelOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(p(XSCoreParamsKey).backendParams.numExu.W)))),
+                                    status.srcStatus(srcIdx).exuSources.get.value)
         else
-          ExuOHGen(exuOHOut, 0.U.asTypeOf(wakeupByIQWithoutCancelOH), hasIQWakeupGet.srcWakeupL1ExuOH(srcIdx))
+          exuSourceOut.value := status.srcStatus(srcIdx).exuSources.get.value
       }
     }
 
@@ -491,17 +489,6 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     entryUpdate.status.blocked                        := !isFirstLoad && isVleff
   }
 
-  def ExuOHGen(exuOH: Vec[Bool], wakeupByIQOH: Vec[Bool], regSrcExuOH: Vec[Bool])(implicit p: Parameters, params: IssueBlockParams) = {
-    val origExuOH = Wire(chiselTypeOf(exuOH))
-    when(wakeupByIQOH.asUInt.orR) {
-      origExuOH := Mux1H(wakeupByIQOH, params.wakeUpSourceExuIdx.map(x => MathUtils.IntToOH(x).U(p(XSCoreParamsKey).backendParams.numExu.W)).toSeq).asBools
-    }.otherwise {
-      origExuOH := regSrcExuOH
-    }
-    exuOH := 0.U.asTypeOf(exuOH)
-    params.wakeUpSourceExuIdx.foreach(x => exuOH(x) := origExuOH(x))
-  }
-
   object IQFuType {
     def num = FuType.num
 
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index 6458d547357..b34892b0e8f 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -102,6 +102,12 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
 
   println(s"[IssueQueueImp] ${params.getIQName} fuLatencyMaps: ${wakeupFuLatencyMaps}")
   println(s"[IssueQueueImp] ${params.getIQName} commonFuCfgs: ${commonFuCfgs.map(_.name)}")
+  if (params.hasIQWakeUp) {
+    val exuSourcesEncodeString = params.wakeUpSourceExuIdx.map(x => 1 << x).reduce(_ + _).toBinaryString
+    println(s"[IssueQueueImp] ${params.getIQName} exuSourcesWidth: ${ExuSource().value.getWidth}, " +
+      s"exuSourcesEncodeMask: ${"0" * (p(XSCoreParamsKey).backendParams.numExu - exuSourcesEncodeString.length) + exuSourcesEncodeString}")
+  }
+
   lazy val io = IO(new IssueQueueIO())
 
   // Modules
@@ -229,10 +235,10 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
   val finalDataSources: Vec[Vec[DataSource]] = VecInit(finalDeqSelOHVec.map(oh => Mux1H(oh, dataSources)))
   val loadDependency: Vec[Vec[UInt]] = entries.io.loadDependency
   val finalLoadDependency: IndexedSeq[Vec[UInt]] = VecInit(finalDeqSelOHVec.map(oh => Mux1H(oh, loadDependency)))
-  // (entryIdx)(srcIdx)(exuIdx)
-  val wakeUpL1ExuOH: Option[Vec[Vec[Vec[Bool]]]] = entries.io.srcWakeUpL1ExuOH
-  // (deqIdx)(srcIdx)(exuIdx)
-  val finalWakeUpL1ExuOH: Option[Vec[Vec[Vec[Bool]]]] = wakeUpL1ExuOH.map(x => VecInit(finalDeqSelOHVec.map(oh => Mux1H(oh, x))))
+  // (entryIdx)(srcIdx)
+  val exuSources: Option[Vec[Vec[ExuSource]]] = entries.io.exuSources
+  // (deqIdx)(srcIdx)
+  val finalExuSources: Option[Vec[Vec[ExuSource]]] = exuSources.map(x => VecInit(finalDeqSelOHVec.map(oh => Mux1H(oh, x))))
 
   val fuTypeVec = Wire(Vec(params.numEntries, FuType()))
   val deqEntryVec = Wire(Vec(params.numDeq, ValidIO(new EntryBundle)))
@@ -304,9 +310,7 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
                                                                       ))
                                                                     })
         enq.bits.status.srcStatus(j).srcLoadDependency          := VecInit(s0_enqBits(enqIdx).srcLoadDependency(j).map(x => x << 1))
-        if(params.hasIQWakeUp) {
-          enq.bits.status.srcStatus(j).srcWakeUpL1ExuOH.get     := 0.U.asTypeOf(ExuVec())
-        }
+        enq.bits.status.srcStatus(j).exuSources.foreach(_       := 0.U.asTypeOf(ExuSource()))
         enq.bits.status.srcStatus(j).useRegCache.foreach(_      := s0_enqBits(enqIdx).useRegCache(j))
         enq.bits.status.srcStatus(j).regCacheIdx.foreach(_      := s0_enqBits(enqIdx).regCacheIdx(j))
       }
@@ -754,7 +758,7 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
 
     require(deq.bits.common.dataSources.size <= finalDataSources(i).size)
     deq.bits.common.dataSources.zip(finalDataSources(i)).foreach { case (sink, source) => sink := source}
-    deq.bits.common.l1ExuOH.foreach(_.zip(finalWakeUpL1ExuOH.get(i)).foreach { case (sink, source) => sink := source})
+    deq.bits.common.exuSources.foreach(_.zip(finalExuSources.get(i)).foreach { case (sink, source) => sink := source})
     deq.bits.common.srcTimer.foreach(_ := DontCare)
     deq.bits.common.loadDependency.foreach(_.zip(finalLoadDependency(i)).foreach { case (sink, source) => sink := source})
     deq.bits.common.src := DontCare
```
