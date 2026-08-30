# Commit Log
- Issue: #5548
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5548
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5548
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5548
- Changed files: 54
- Additions: 6631
- Deletions: 5871

## Files
- `src/main/scala/utils/EnumUtils.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/fu/FuType.scala`
- `src/main/scala/xiangshan/backend/fu/PMP.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/rob/ExceptionGen.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`
- `src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala`
- `src/main/scala/xiangshan/backend/rob/RobEnqPtrWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/Uncache.scala`
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/mem/Bundles.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/MemCommon.scala`
- `src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQCommon.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadExceptionBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala`
- `src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/Bundles.scala`
- `src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/package.scala`
- `src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`
- `src/main/scala/xiangshan/mem/vector/VecBundle.scala`
- `src/main/scala/xiangshan/mem/vector/VecCommon.scala`
- `src/main/scala/xiangshan/mem/vector/VfofBuffer.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/src/main/scala/utils/EnumUtils.scala b/src/main/scala/utils/EnumUtils.scala
index 3ae0e0509ac..ab134a472a3 100644
--- a/src/main/scala/utils/EnumUtils.scala
+++ b/src/main/scala/utils/EnumUtils.scala
@@ -1,11 +1,51 @@
 package utils
 
+import chisel3._
 import MathUtils.IntToOH
 
 object EnumUtils {
   class OHEnumeration() extends Enumeration {
     protected class OHVal(i: Int, name: String) extends super.Val(i, name) {
       def ohid: BigInt = IntToOH(id)
+      def getName: String = name
+    }
+  }
+
+  class ChiselOHEnum extends OHEnumeration {
+    class OHType(i: Int, name: String) extends super.OHVal(i: Int, name: String)
+
+    def OHType(i: Int, name: String): OHType = new OHType(i, name)
+
+    implicit class fromOHValToLiteral(x: OHType) {
+      def U: UInt = x.ohid.U
+      def U(width: Width): UInt = x.ohid.U(width)
+    }
+    implicit def valueToOHType(x: Value): OHType = x.asInstanceOf[OHType]
+
+    protected var initVal = 0
+
+    protected def addType(name: String): OHType = {
+      val ohval = OHType(initVal, name)
+      initVal += 1
+      ohval
+    }
+
+    object IsOneOf {
+      def apply(ohtype: UInt, opt0: OHType, opts: OHType*): Bool = {
+        apply(ohtype, opt0 +: opts)
+      }
+
+      def apply(ohtype: UInt, opts: Seq[OHType]): Bool = {
+        opts.map(x => ohtype(x.id)).fold(false.B)(_ || _)
+      }
+
+      def apply(ohtype: OHType, opt0: OHType, opts: OHType*): Boolean = {
+        apply(ohtype, opt0 +: opts)
+      }
+
+      def apply(ohtype: OHType, opts: Seq[OHType]): Boolean = {
+        opts.map(x => x == ohtype).fold(false)(_ || _)
+      }
     }
   }
 }
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 2f200a6c9d9..600c8d0ecce 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -104,6 +104,7 @@ case class XSCoreParameters
   LoadUncacheBufferSize: Int = 16,
   LoadQueueNWriteBanks: Int = 8, // NOTE: make sure that LoadQueueRARSize/LoadQueueRAWSize is divided by LoadQueueNWriteBanks
   StoreQueueSize: Int = 56,
+  SQUnalignQueueSize: Int = 2,
   StoreQueueNWriteBanks: Int = 8, // NOTE: make sure that StoreQueueSize is divided by StoreQueueNWriteBanks
   StoreQueueForwardWithMask: Boolean = true,
   VlsQueueSize: Int = 8,
@@ -696,6 +697,7 @@ trait HasXSParameter {
   def LoadUncacheBufferSize = coreParams.LoadUncacheBufferSize
   def LoadQueueNWriteBanks = coreParams.LoadQueueNWriteBanks
   def StoreQueueSize = coreParams.StoreQueueSize
+  def SQUnalignQueueSize = coreParams.SQUnalignQueueSize
   def StoreQueueForceWriteSbufferUpper = coreParams.StoreQueueSize - 4
   def StoreQueueForceWriteSbufferLower = StoreQueueForceWriteSbufferUpper - 5
   def VirtualLoadQueueMaxStoreQueueSize = VirtualLoadQueueSize max StoreQueueSize
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 20d89eac0e7..5c94a9b7e70 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -154,7 +154,6 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   backend.io.mem.lqCancelCnt := memBlock.io.mem_to_ooo.lqCancelCnt
   backend.io.mem.sqCancelCnt := memBlock.io.mem_to_ooo.sqCancelCnt
   backend.io.mem.stIssuePtr := memBlock.io.mem_to_ooo.stIssuePtr
-  backend.io.mem.ldaIqFeedback := memBlock.io.mem_to_ooo.ldaIqFeedback
   backend.io.mem.staIqFeedback := memBlock.io.mem_to_ooo.staIqFeedback
   backend.io.mem.hyuIqFeedback := memBlock.io.mem_to_ooo.hyuIqFeedback
   backend.io.mem.vstuIqFeedback := memBlock.io.mem_to_ooo.vstuIqFeedback
@@ -163,8 +162,7 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   backend.io.mem.wakeup := memBlock.io.mem_to_ooo.wakeup
   backend.io.mem.intWriteback <> memBlock.io.mem_to_ooo.intWriteback
   backend.io.mem.vecWriteback <> memBlock.io.mem_to_ooo.vecWriteback
-  backend.io.mem.robLsqIO.mmio := memBlock.io.mem_to_ooo.lsqio.mmio
-  backend.io.mem.robLsqIO.uop := memBlock.io.mem_to_ooo.lsqio.uop
+  backend.io.mem.robLsqIO.mmioBusy := memBlock.io.mem_to_ooo.lsqio.mmioBusy
 
   backend.io.mem.exceptionAddr.vaddr  := memBlock.io.mem_to_ooo.lsqio.vaddr
   backend.io.mem.exceptionAddr.gpaddr := memBlock.io.mem_to_ooo.lsqio.gpaddr
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 9cc6659423c..041ba83fd48 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -294,13 +294,12 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
 
   intRegion.io.memWriteback.zip(io.mem.intWriteback).foreach { case (sinkWriteback, sourceWriteback) =>
     sinkWriteback.zip(sourceWriteback).foreach { case (sink, source) =>
-      connectMemNewExuOutput(sink, source)
+      sink <> source
     }
   }
   val lduWriteback = io.mem.intWriteback.flatten.filter(_.bits.params.hasLoadFu)
   fpRegion.io.lduWriteback.get.flatten.zip(lduWriteback).map { case (sink, source) =>
-    sink.valid := source.valid
-    sink.bits := source.bits
+    sink <> source
   }
   intRegion.io.wakeUpFromFp. foreach(x => x := fpRegion.io.wakeUpToDispatch)
   intRegion.io.wakeupFromF2I.foreach(x => x := fpRegion.io.cross.F2IWakeupOut.get)
@@ -627,7 +626,6 @@ class BackendMemIO(implicit p: Parameters, params: BackendParams) extends XSBund
   // In/Out // Todo: split it into one-direction bundle
   val lsqEnqIO = Flipped(new LsqEnqIO)
   val robLsqIO = new RobLsqIO
-  val ldaIqFeedback = Vec(params.LduCnt, Flipped(new MemRSFeedbackIO))
   val staIqFeedback = Vec(params.StaCnt, Flipped(new MemRSFeedbackIO))
   val hyuIqFeedback = Vec(params.HyuCnt, Flipped(new MemRSFeedbackIO))
   val vstuIqFeedback = Flipped(Vec(params.VstuCnt, new MemRSFeedbackIO(isVector = true)))
@@ -637,8 +635,8 @@ class BackendMemIO(implicit p: Parameters, params: BackendParams) extends XSBund
   val storePcRead = Vec(params.StaCnt, Output(UInt(VAddrBits.W)))
   val hyuPcRead = Vec(params.HyuCnt, Output(UInt(VAddrBits.W)))
   // Input
-  val intWriteback: MixedVec[MixedVec[DecoupledIO[ExuOutput]]] =
-    Flipped(intSchdParams.genExuOutputDecoupledBundleMemBlock)
+  val intWriteback: MixedVec[MixedVec[DecoupledIO[NewExuOutput]]] =
+    Flipped(intSchdParams.genNewExuOutputDecoupledBundleMemBlock)
   val vecWriteback: MixedVec[MixedVec[DecoupledIO[ExuOutput]]] =
     Flipped(vecSchdParams.genExuOutputDecoupledBundleMemBlock)
 
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index f43e5397850..11b0bee7d8a 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -95,6 +95,11 @@ object Bundles {
     sink.bits.toVlRf. foreach(_.bits  := source.bits.data(0))
   }
 
+  def connectMemDecoupledNewExuOutput(sink: DecoupledIO[NewExuOutput], source: NewExuOutput) = {
+    sink.valid := source.toRob.valid
+    sink.bits := source
+  }
+
   def connectWriteBackRob(sink: WriteBackRobBundle, source: NewExuOutput) = {
     connectSamePort(sink, source.toRob.bits)
     connectSamePort(sink, source)
@@ -622,6 +627,8 @@ object Bundles {
     // load inst will not be executed until ALL former store addr calcuated
     val loadWaitStrict  = Bool()
     val ssid            = UInt(SSIDWidth.W)
+    val nc = Bool()
+    val mmio = Bool()
     // Todo
     val lqIdx = new LqPtr
     val sqIdx = new SqPtr
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index cad02c4c316..1aa18c42582 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -560,9 +560,9 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     val intLoadWB = bypassNetwork.io.fromExus.int.flatten.filter(_.bits.params.hasLoadExu)
     intLoadWB.zip(io.lduWriteback.get.flatten).foreach { case (sink, source) =>
       sink.valid := source.valid
-      sink.bits.intWen := source.bits.intWen.getOrElse(false.B) && source.bits.isFromLoadUnit.getOrElse(true.B)
+      sink.bits.intWen := false.B
       sink.bits.pdest := source.bits.pdest
-      sink.bits.data := source.bits.data(source.bits.params.getForwardIndex)
+      sink.bits.data := source.bits.toFpRf.get.bits
     }
     bypassNetwork.io.fromExus.connectExuOutput(_.fp)(exuBlock.io.out)
     for (i <- 0 until exuBlock.io.in.length) {
@@ -826,8 +826,8 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
     val delayedOldestExuRedirect = Option.when(params.isIntSchd)(ValidIO(new Redirect))
   }
   val memWriteback: MixedVec[MixedVec[DecoupledIO[NewExuOutput]]] = Flipped(params.genNewExuOutputDecoupledBundleMemBlock)
-  val lduWriteback: Option[MixedVec[MixedVec[DecoupledIO[ExuOutput]]]] = Option.when(params.isFpSchd)(
-    Flipped(MixedVec(intSchdParam.issueBlockParams.filter(_.isLdAddrIQ).map(_.genExuOutputDecoupledBundle)))
+  val lduWriteback: Option[MixedVec[MixedVec[DecoupledIO[NewExuOutput]]]] = Option.when(params.isFpSchd)(
+    Flipped(MixedVec(intSchdParam.issueBlockParams.filter(_.isLdAddrIQ).map(_.genNewExuOutputDecoupledBundle)))
   )
   val lqDeqPtr = Option.when(params.isVecSchd)(Input(new LqPtr))
   val sqDeqPtr = Option.when(params.isVecSchd)(Input(new SqPtr))
diff --git a/src/main/scala/xiangshan/backend/fu/FuType.scala b/src/main/scala/xiangshan/backend/fu/FuType.scala
index 4b8191879f9..e236a13a0b2 100644
--- a/src/main/scala/xiangshan/backend/fu/FuType.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuType.scala
@@ -2,29 +2,15 @@ package xiangshan.backend.fu
 
 import chisel3._
 import chisel3.util.BitPat
-import utils.EnumUtils.OHEnumeration
+import utils.EnumUtils._
 import org.chipsalliance.cde.config.Parameters
 import xiangshan.XSCoreParamsKey
 
 import scala.language.implicitConversions
 
-object FuType extends OHEnumeration {
-  class OHType(i: Int, name: String) extends super.OHVal(i: Int, name: String)
-
-  def OHType(i: Int, name: String): OHType = new OHType(i, name)
-
-  implicit class fromOHValToLiteral(x: OHType) {
-    def U: UInt = x.ohid.U
-    def U(width: Width): UInt = x.ohid.U(width)
-  }
-
-  private var initVal = 0
-
-  private def addType(name: String): OHType = {
-    val ohval = OHType(initVal, name)
-    initVal += 1
-    ohval
-  }
+object FuType extends ChiselOHEnum {
+  type OHType = super.OHType
+  val FuTypeOrR: IsOneOf.type = IsOneOf
 
   // int
   val jmp = addType(name = "jmp")
@@ -223,24 +209,6 @@ object FuType extends OHEnumeration {
 
   def isBlockBackCompress(fuType: UInt): Bool = FuTypeOrR(fuType, blockBackCompress)
 
-  object FuTypeOrR {
-    def apply(fuType: UInt, fu0: OHType, fus: OHType*): Bool = {
-      apply(fuType, fu0 +: fus)
-    }
-
-    def apply(fuType: UInt, fus: Seq[OHType]): Bool = {
-      fus.map(x => fuType(x.id)).fold(false.B)(_ || _)
-    }
-
-    def apply(fuType: OHType, fu0: OHType, fus: OHType*): Boolean = {
-      apply(fuType, fu0 +: fus)
-    }
-
-    def apply(fuTupe: OHType, fus: Seq[OHType]): Boolean = {
-      fus.map(x => x == fuTupe).fold(false)(_ || _)
-    }
-  }
-
   val functionNameMap = Map(
     jmp -> "jmp",
     brh -> "brh",
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index 4932af88aa0..e7159a9839e 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -26,10 +26,8 @@ import utility.MaskedRegMap.WritableMask
 import xiangshan._
 import xiangshan.backend.fu.util.HasCSRConst
 import xiangshan.backend.decode.isa.CSRs
-import utils._
 import utility._
-import xiangshan.cache.mmu.{TlbCmd, TlbExceptionBundle}
-
+import xiangshan.cache.mmu.TlbCmd
 
 trait PMPConst extends HasPMParameters {
   val PMPOffBits = 2 // minimal 4bytes
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index af371c0f640..3fc29171086 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -431,7 +431,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
                                                           (commonIn.deqSel && !cancelBypassVec.asUInt.orR)  -> true.B,
                                                           (srcCancelByLoad || respIssueFail)                -> false.B,
                                                          ))
-    entryUpdate.status.firstIssue                     := Mux(status.firstIssue && status.issueTimer === "b11".U, !respIssueFail, status.firstIssue)
+    entryUpdate.status.firstIssue                     := Mux(status.firstIssue && status.issueTimer === params.issueTimerMaxValue.U, !respIssueFail, status.firstIssue)
     val updateIssueTimer = Mux(status.issueTimer === params.issueTimerMaxValue.U, status.issueTimer, status.issueTimer + 1.U)
     entryUpdate.status.issueTimer                     := Mux(validReg && status.issued, updateIssueTimer, 0.U)
     entryUpdate.status.deqPortIdx                     := Mux(commonIn.deqSel, commonIn.deqPortIdxWrite, Mux(status.issued, status.deqPortIdx, 0.U))
diff --git a/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala b/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
index f66b52e5f6a..1047d66d731 100644
--- a/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
+++ b/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
@@ -28,7 +28,6 @@ import xiangshan.backend.BackendParams
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
-import xiangshan.backend.ctrlblock.{DebugLSIO, DebugLsInfo, LsTopdownInfo}
 import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.rename.SnapshotGenerator
 
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index b6c8799c093..c050cbfe5ce 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -589,12 +589,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val writebackNum = PopCount(exuWBs.map(_.valid))
   XSInfo(writebackNum =/= 0.U, "writebacked %d insts\n", writebackNum)
 
-  for (i <- 0 until LoadPipelineWidth) {
-    when(RegNext(io.lsq.mmio(i))) {
-      robEntries(RegEnable(io.lsq.uop(i).robIdx, io.lsq.mmio(i)).value).mmio := true.B
-    }
-  }
-
 
   /**
    * RedirectOut: Interrupt and Exceptions
@@ -1684,8 +1678,8 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   generatePerfEvent()
 
   // max commit-stuck cycle
-  val deqismmio = Mux(robEntries(deqPtr.value).valid, robEntries(deqPtr.value).mmio, false.B)
-  val commitStuck = (!io.commits.commitValid.reduce(_ || _) || !io.commits.isCommit) && !deqismmio
+  val mmioBusy = io.lsq.mmioBusy // lsq know uncache request is rob head
+  val commitStuck = (!io.commits.commitValid.reduce(_ || _) || !io.commits.isCommit) && !mmioBusy
   val commitStuckCycle = RegInit(0.U(log2Up(maxCommitStuck).W))
   when(commitStuck) {
     commitStuckCycle := commitStuckCycle + 1.U
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 7cac7a4a2ae..4f998e88eb7 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -30,7 +30,6 @@ import xiangshan.backend.Bundles.{DynInst, ExceptionInfo, ExuOutput, UopIdx, Enq
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
-import xiangshan.backend.ctrlblock.{DebugLSIO, DebugLsInfo, LsTopdownInfo}
 import xiangshan.backend.fu.NewCSR.CSREvents.TargetPCBundle
 import xiangshan.backend.fu.vector.Bundles.{Nf, VLmul, VSew, VType}
 import xiangshan.backend.rename.SnapshotGenerator
@@ -60,7 +59,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val needVTB = Bool()
     val isHls = Bool()
     // data end
-    
+
     // trace
     val traceBlockInPipe = new TracePipe(IretireWidthEncoded)
     // status begin
@@ -261,9 +260,7 @@ class RobLsqIO(implicit p: Parameters) extends XSBundle {
   val pendingPtr = Output(new RobPtr)
   val pendingPtrNext = Output(new RobPtr)
 
-  val mmio = Input(Vec(LoadPipelineWidth, Bool()))
-  // Todo: what's this?
-  val uop = Input(Vec(LoadPipelineWidth, new DynInst))
+  val mmioBusy = Input(Bool())
 }
 
 class RobEnqIO(implicit p: Parameters) extends XSBundle {
diff --git a/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala b/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala
index a892b9b118d..68c636bcd62 100644
--- a/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala
@@ -30,7 +30,6 @@ import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
 import xiangshan.backend.Bundles.{DynInst, ExceptionInfo, ExuOutput}
-import xiangshan.backend.ctrlblock.{DebugLSIO, DebugLsInfo, LsTopdownInfo}
 import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.rename.SnapshotGenerator
 
diff --git a/src/main/scala/xiangshan/backend/rob/RobEnqPtrWrapper.scala b/src/main/scala/xiangshan/backend/rob/RobEnqPtrWrapper.scala
index 8249957d868..c2270043447 100644
--- a/src/main/scala/xiangshan/backend/rob/RobEnqPtrWrapper.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobEnqPtrWrapper.scala
@@ -29,7 +29,6 @@ import xiangshan.backend.Bundles.{ExceptionInfo, ExuOutput}
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
-import xiangshan.backend.ctrlblock.{DebugLSIO, DebugLsInfo, LsTopdownInfo}
 import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.rename.SnapshotGenerator
 
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 08e351c7b8e..54648bb96e1 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -31,7 +31,7 @@ import xiangshan._
 import xiangshan.backend.rob.{RobDebugRollingIO, RobPtr}
 import xiangshan.cache.wpu._
 import xiangshan.mem.prefetch._
-import xiangshan.mem.{AddPipelineReg, DataBufferEntry, HasL1PrefetchSourceParameter, HasMemBlockParameters, LqPtr}
+import xiangshan.mem.{AddPipelineReg, HasL1PrefetchSourceParameter, HasMemBlockParameters, LqPtr, MemorySize}
 
 // DCache specific parameters
 case class DCacheParameters
@@ -86,6 +86,8 @@ case class DCacheParameters
 
 // Default DCache size = 64 sets * 8 ways * 8 banks * 8 Byte = 32K Byte
 
+
+// TODO: do we really need so many traits?
 trait HasDCacheParameters
   extends HasMemBlockParameters
   with HasL1PrefetchSourceParameter
@@ -140,7 +142,7 @@ trait HasDCacheParameters
   val DCacheWordBits = 64 // hardcoded
   val DCacheWordBytes = DCacheWordBits / 8
   val MaxPrefetchEntry = cacheParams.nMaxPrefetchEntry
-  val DCacheVWordBytes = VLEN / 8
+  def DCacheVWordBytes = VLEN / 8
   require(DCacheSRAMRowBits == 64)
 
   val DCacheSetDivBits = log2Ceil(DCacheSetDiv)
@@ -153,7 +155,7 @@ trait HasDCacheParameters
 
   val DCacheSRAMRowBytes = DCacheSRAMRowBits / 8
   val DCacheWordOffset = log2Up(DCacheWordBytes)
-  val DCacheVWordOffset = log2Up(DCacheVWordBytes)
+  def DCacheVWordOffset = log2Up(DCacheVWordBytes)
 
   val DCacheBankOffset = log2Up(DCacheSRAMRowBytes)
   val DCacheSetOffset = DCacheBankOffset + log2Up(DCacheBanks)
@@ -307,6 +309,10 @@ class DCacheExtraMeta(implicit p: Parameters) extends DCacheBundle
 // memory request in word granularity(load, mmio, lr/sc, atomics)
 class DCacheWordReq(implicit p: Parameters) extends DCacheBundle
 {
+  /**
+    * TODO:
+    * remove data, mask, id, either cmd or instrtype
+    */
   val cmd    = UInt(M_SZ.W)
   val vaddr  = UInt(VAddrBits.W)
   val vaddr_dup = UInt(VAddrBits.W)
@@ -351,20 +357,6 @@ class DCacheWordReqWithVaddrAndPfFlag(implicit p: Parameters) extends DCacheWord
   val vecValid = Bool()
   val sqNeedDeq = Bool()
 
-  def fromDataBufferEntry(src: DataBufferEntry, cmd: UInt) = {
-    this := DontCare
-    this := DontCare
-    this.cmd := cmd
-    this.addr := src.addr
-    this.vaddr := src.vaddr
-    this.data := src.data
-    this.mask := src.mask
-    this.wline := src.wline && src.vecValid
-    this.prefetch := src.prefetch
-    this.vecValid := src.vecValid
-    this.sqNeedDeq := src.sqNeedDeq
-  }
-
   def toDCacheWordReqWithVaddr() = {
     val res = Wire(new DCacheWordReqWithVaddr)
     res.vaddr := vaddr
@@ -408,6 +400,7 @@ class BaseDCacheWordResp(implicit p: Parameters) extends DCacheBundle
 
 class DCacheWordResp(implicit p: Parameters) extends BaseDCacheWordResp
 {
+  // TODO: Signals from different stages should not be in the same bundle
   val meta_prefetch = UInt(L1PfSourceBits.W)
   val meta_access = Bool()
   val refill_latency = UInt(LATENCY_WIDTH.W)
@@ -485,11 +478,8 @@ class UncacheWordReq(implicit p: Parameters) extends DCacheBundle
   val data = UInt(XLEN.W)
   val mask = UInt((XLEN/8).W)
   val id   = UInt(uncacheIdxBits.W)
-  val instrtype = UInt(sourceTypeWidth.W)
   val nc = Bool()
   val memBackTypeMM = Bool()
-  val isFirstIssue = Bool()
-  val replayCarry = new ReplayCarry(nWays)
 
   def dump(cond: Bool) = {
     XSDebug(cond, "UncacheWordReq: cmd: %x addr: %x data: %x mask: %x id: %d\n",
@@ -576,7 +566,6 @@ class CMOResp(implicit p: Parameters) extends Bundle {
 class DCacheLoadIO(implicit p: Parameters) extends DCacheWordIO
 {
   // kill previous cycle's req
-  val s1_kill_data_read = Output(Bool()) // only kill bandedDataRead at s1
   val s1_kill           = Output(Bool()) // kill loadpipe req at s1
   val s2_kill           = Output(Bool())
   val s0_pc             = Output(UInt(VAddrBits.W))
@@ -627,67 +616,6 @@ class DCacheToSbufferIO(implicit p: Parameters) extends DCacheBundle {
   def hit_resps: Seq[ValidIO[DCacheLineResp]] = Seq(main_pipe_hit_resp)
 }
 
-// forward tilelink channel D's data to ldu
-class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
-  val valid = Bool()
-  val data = UInt(l1BusDataWidth.W)
-  val mshrid = UInt(log2Up(cfg.nMissEntries).W)
-  val last = Bool()
-  val denied = Bool()
-  val corrupt = Bool()
-
-  def apply(d: DecoupledIO[TLBundleD], edge: TLEdgeOut) = {
-    val isKeyword = d.bits.echo.lift(IsKeywordKey).getOrElse(false.B)
-    val (_, _, done, _) = edge.count(d)
-    valid := d.valid
-    data := d.bits.data
-    mshrid := d.bits.source
-    last := isKeyword ^ done
-    denied := d.bits.denied
-    corrupt := d.bits.corrupt
-  }
-
-  def dontCare() = {
-    valid := false.B
-    data := DontCare
-    mshrid := DontCare
-    last := DontCare
-    denied := false.B
-    corrupt := false.B
-  }
-
-  def forward(req_valid : Bool, req_mshr_id : UInt, req_paddr : UInt) = {
-    val all_match = req_valid && valid &&
-                req_mshr_id === mshrid &&
-                req_paddr(log2Up(refillBytes)) === last
-    val forward_D = RegInit(false.B)
-    val forwardData = RegInit(VecInit(List.fill(VLEN/8)(0.U(8.W))))
-    val forwardDenied = RegInit(false.B)
-    val forwardCorrupt = RegInit(false.B)
-
-    val block_idx = req_paddr(log2Up(refillBytes) - 1, 3)
-    val block_data = Wire(Vec(l1BusDataWidth / 64, UInt(64.W)))
-    (0 until l1BusDataWidth / 64).map(i => {
-      block_data(i) := data(64 * i + 63, 64 * i)
-    })
-    val selected_data = Wire(UInt(128.W))
-    selected_data := Mux(req_paddr(3), Fill(2, block_data(block_idx)), Cat(block_data(block_idx + 1.U), block_data(block_idx)))
-
-    forward_D := all_match
-    for (i <- 0 until VLEN/8) {
-      when (all_match) {
-        forwardData(i) := selected_data(8 * i + 7, 8 * i)
-      }
-    }
-    when (all_match) {
-      forwardDenied := denied
-      forwardCorrupt := corrupt
-    }
-
-    (forward_D, forwardData, forwardDenied, forwardCorrupt)
-  }
-}
-
 class MissEntryForwardIO(implicit p: Parameters) extends DCacheBundle {
   val inflight = Bool()
   val paddr = UInt(PAddrBits.W)
@@ -724,6 +652,35 @@ class MissEntryForwardIO(implicit p: Parameters) extends DCacheBundle {
   }
 }
 
+class DCacheForwardReqS0(implicit p: Parameters) extends DCacheBundle {
+  val vaddr = UInt(VAddrBits.W)
+  val size = UInt(MemorySize.Size.width.W)
+  val mshrId = UInt(log2Up(cfg.nMissEntries).W)
+}
+
+class DCacheForwardReqS1(implicit p: Parameters) extends DCacheBundle {
+  val paddr = UInt(PAddrBits.W)
+}
+
+class DCacheForwardResp(implicit p: Parameters) extends DCacheBundle {
+  val matchInvalid = Bool()
+  val forwardData = Vec((VLEN/8), UInt(8.W))
+  // denied and corrupt are only valid when forwarding matches
+  val denied = Bool()
+  val corrupt = Bool()
+}
+
+class DCacheForward(implicit p: Parameters) extends DCacheBundle {
+  val s0Req = ValidIO(new DCacheForwardReqS0)
+  val s1Req = Output(new DCacheForwardReqS1)
+  val s1Kill = Output(Bool())
+  val s2Resp = Flipped(ValidIO(new DCacheForwardResp))
+}
+
+class DCacheLoadWakeup(implicit p: Parameters) extends DCacheBundle {
+  val mshrId = UInt(log2Up(cfg.nMissEntries).W)
+}
+
 // forward mshr's data to ldu
 class LduToMissqueueForwardIO(implicit p: Parameters) extends DCacheBundle {
   // TODO: use separate Bundles for req and resp
@@ -763,13 +720,12 @@ class StorePrefetchReq(implicit p: Parameters) extends DCacheBundle {
 class DCacheToLsuIO(implicit p: Parameters) extends DCacheBundle {
   val load  = Vec(LoadPipelineWidth, Flipped(new DCacheLoadIO)) // for speculative load
   val sta   = Vec(StorePipelineWidth, Flipped(new DCacheStoreIO)) // for non-blocking store
-  //val lsq = ValidIO(new Refill)  // refill to load queue, wake up load misses
-  val tl_d_channel = Output(new DcacheToLduForwardIO)
+  val loadWakeup = ValidIO(new DCacheLoadWakeup())
   val store = new DCacheToSbufferIO // for sbuffer
   val atomics  = Flipped(new AtomicWordIO)  // atomics reqs
   val release = ValidIO(new Release) // cacheline release hint for ld-ld violation check
-  val forward_D = Output(Vec(LoadPipelineWidth, new DcacheToLduForwardIO))
-  val forward_mshr = Vec(LoadPipelineWidth, new LduToMissqueueForwardIO)
+  val forward_D = Flipped(Vec(LoadPipelineWidth, new DCacheForward))
+  val forward_mshr = Flipped(Vec(LoadPipelineWidth, new DCacheForward))
 }
 
 class DCacheTopDownIO(implicit p: Parameters) extends DCacheBundle {
@@ -917,7 +873,8 @@ class DCache()(implicit p: Parameters) extends LazyModule with HasDCacheParamete
 }
 
 
-class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParameters with HasPerfEvents with HasL1PrefetchSourceParameter {
+class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParameters with HasPerfEvents with
+  HasL1PrefetchSourceParameter {
 
   val io = IO(new DCacheIO)
 
@@ -964,8 +921,13 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   val bankedDataArray = if(dwpuParam.enWPU) Module(new SramedDataArray) else Module(new BankedDataArray)
   val metaArray = Module(new L1CohMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1))
   val errorArray = Module(new L1ErrorMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1, enableBypass = true))
-  val prefetchArray = Module(new L1PrefetchSourceArray(readPorts = PrefetchArrayReadPort, writePorts = 1 + LoadPipelineWidth)) // prefetch flag array
-  val latencyArray = Option.when(GenLatencyArray)(Module(new L1RefillLatencyArray(readPorts = PrefetchArrayReadPort, writePorts = 1 + LoadPipelineWidth)))
+  val prefetchArray = Module(new L1PrefetchSourceArray(
+    readPorts = PrefetchArrayReadPort, writePorts = 1 + LoadPipelineWidth
+  )) // prefetch flag array
+  val latencyArray = Option.when(GenLatencyArray)(Module(new L1RefillLatencyArray(
+    readPorts = PrefetchArrayReadPort, writePorts = 1 + LoadPipelineWidth
+  )))
+
   val accessArray = Module(new L1FlagMetaArray(readPorts = AccessArrayReadPort, writePorts = LoadPipelineWidth + 1))
   val tagArray = Module(new DuplicatedTagArray(readPorts = TagReadPort))
   val prefetcherMonitor = Module(new PrefetcherMonitor)
@@ -979,7 +941,11 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   // enableStorePrefetch: main pipe * 1 + load pipe * 2 + store pipe * 1 +
   // hybrid * 1; disable: main pipe * 1 + load pipe * 2 + hybrid * 1
   // higher priority is given to lower indices
-  val MissReqPortCount = if(StorePrefetchL1Enabled) 1 + backendParams.LduCnt + backendParams.StaCnt + backendParams.HyuCnt else 1 + backendParams.LduCnt + backendParams.HyuCnt
+  val MissReqPortCount = if (StorePrefetchL1Enabled) {
+    1 + backendParams.LduCnt + backendParams.StaCnt + backendParams.HyuCnt
+  } else {
+    1 + backendParams.LduCnt + backendParams.HyuCnt
+  }
   val MainPipeMissReqPort = 0
   val HybridMissReqBase = MissReqPortCount - backendParams.HyuCnt
 
@@ -1063,9 +1029,12 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     val HybridStoreMetaReadPort = HybridStoreReadBase + i
 
     hybrid_meta_read_ports(i).valid := ldu(HybridLoadMetaReadPort).io.meta_read.valid ||
-                                       (stu(HybridStoreMetaReadPort).io.meta_read.valid && StorePrefetchL1Enabled.B)
-    hybrid_meta_read_ports(i).bits := Mux(ldu(HybridLoadMetaReadPort).io.meta_read.valid, ldu(HybridLoadMetaReadPort).io.meta_read.bits,
-                                          stu(HybridStoreMetaReadPort).io.meta_read.bits)
+      stu(HybridStoreMetaReadPort).io.meta_read.valid && StorePrefetchL1Enabled.B
+    hybrid_meta_read_ports(i).bits := Mux(
+      ldu(HybridLoadMetaReadPort).io.meta_read.valid,
+      ldu(HybridLoadMetaReadPort).io.meta_read.bits,
+      stu(HybridStoreMetaReadPort).io.meta_read.bits
+    )
 
     ldu(HybridLoadMetaReadPort).io.meta_read.ready := hybrid_meta_read_ports(i).ready
     stu(HybridStoreMetaReadPort).io.meta_read.ready := hybrid_meta_read_ports(i).ready && StorePrefetchL1Enabled.B
@@ -1158,7 +1127,10 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     accessArray.io.read.last.bits.way_en := mainPipe.io.prefetch_flag_write.bits.way_en
 
     val extra_flag_valid = RegNext(mainPipe.io.prefetch_flag_write.valid)
-    val extra_flag_way_en = RegEnable(mainPipe.io.prefetch_flag_write.bits.way_en, mainPipe.io.prefetch_flag_write.valid)
+    val extra_flag_way_en = RegEnable(
+      mainPipe.io.prefetch_flag_write.bits.way_en,
+      mainPipe.io.prefetch_flag_write.valid
+    )
     val extra_flag_prefetch = Mux1H(extra_flag_way_en, prefetchArray.io.resp.last)
     val extra_flag_access = Mux1H(extra_flag_way_en, accessArray.io.resp.last)
 
@@ -1312,19 +1284,35 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     ldu(i).io.bank_conflict_slow := bankedDataArray.io.bank_conflict_slow(i)
   })
 
-  (0 until LoadPipelineWidth).map(i => {
-    when(bus.d.bits.opcode === TLMessages.GrantData) {
-      io.lsu.forward_D(i).apply(bus.d, edge)
-    }.otherwise {
-      io.lsu.forward_D(i).dontCare()
-    }
-  })
-  // tl D channel wakeup
-  when (bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.Grant) {
-    io.lsu.tl_d_channel.apply(bus.d, edge)
-  } .otherwise {
-    io.lsu.tl_d_channel.dontCare()
+  io.lsu.forward_D.zipWithIndex.foreach { case (forward, i) =>
+    val s0ReqValid = forward.s0Req.valid
+    val s0Req = forward.s0Req.bits
+    val s1ReqValid = RegNext(s0ReqValid)
+    val s1Req = RegEnable(s0Req, s0ReqValid)
+    val mshrId = s1Req.mshrId
+    val paddr = forward.s1Req.paddr
+
+    val (_, _, done, _) = edge.count(bus.d)
+    val mshrMatch = mshrId === bus.d.bits.source
+    val beatMatch = (bus.d.bits.echo.lift(IsKeywordKey).getOrElse(false.B) ^ done) === paddr(log2Up(refillBytes))
+    val paddrMatch = missQueue.io.forwardS1PAddrMatch(i)
+    val s1RespValid = s1ReqValid && bus.d.valid && bus.d.bits.opcode === TLMessages.GrantData &&
+      mshrMatch && beatMatch && paddrMatch
+    val s1RespForwardData = VecInit.tabulate(l1BusDataWidth / VLEN) { i =>
+      bus.d.bits.data((i + 1) * VLEN - 1, i * VLEN)
+    }(paddr(log2Up(VLEN / 8)))
+
+    val s2Resp = forward.s2Resp
+    s2Resp.valid := RegNext(s1RespValid)
+    s2Resp.bits.matchInvalid := false.B
+    s2Resp.bits.forwardData := RegEnable(s1RespForwardData.asTypeOf(s2Resp.bits.forwardData), s1ReqValid)
+    s2Resp.bits.denied := RegEnable(bus.d.bits.denied, s1ReqValid)
+    s2Resp.bits.corrupt := RegEnable(bus.d.bits.corrupt, s1ReqValid)
   }
+  // tl D channel wakeup
+  io.lsu.loadWakeup.valid := (bus.d.bits.opcode === TLMessages.GrantData || bus.d.bits.opcode === TLMessages.Grant) &&
+    bus.d.valid
+  io.lsu.loadWakeup.bits.mshrId := bus.d.bits.source
   mainPipe.io.force_write <> io.force_write
 
   /** dwpu */
@@ -1523,7 +1511,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   XSPerfAccumulate("miss_queue_has_muti_enq_but_not_fire", PopCount(VecInit(missReqArb.io.in.map(_.valid))) > 1.U && PopCount(VecInit(missReqArb.io.in.map(_.fire))) === 0.U)
 
   // forward missqueue
-  (0 until LoadPipelineWidth).map(i => io.lsu.forward_mshr(i).connect(missQueue.io.forward(i)))
+  missQueue.io.forward <> io.lsu.forward_mshr
 
   // refill to load queue
  // io.lsu.lsq <> missQueue.io.refill_to_ldq
diff --git a/src/main/scala/xiangshan/cache/dcache/Uncache.scala b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
index d17ac1b3e37..9279fdb20c5 100644
--- a/src/main/scala/xiangshan/cache/dcache/Uncache.scala
+++ b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
@@ -64,9 +64,7 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
   val memBackTypeMM = Bool()
 
   val resp_nderr = Bool()
-
-  val resp_denied = Bool()
-  val resp_corrupt = Bool()
+  val resp_derr = Bool()
 
   /* NOTE: if it support the internal forward logic, here can uncomment */
   // val fwd_data = UInt(XLEN.W)
@@ -81,6 +79,7 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
     nc := x.nc
     memBackTypeMM := x.memBackTypeMM
     resp_nderr := false.B
+    resp_derr := false.B
     // fwd_data := 0.U
     // fwd_mask := 0.U
   }
@@ -101,9 +100,8 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
     when(cmd === MemoryOpConstants.M_XRD) {
       data := x.data
     }
-    resp_nderr := x.denied || x.corrupt
-    resp_denied := x.denied
-    resp_corrupt := x.corrupt
+    resp_nderr := x.denied
+    resp_derr := x.corrupt && !x.denied
   }
 
   // def update(forwardData: UInt, forwardMask: UInt): Unit = {
@@ -121,14 +119,12 @@ class UncacheEntry(implicit p: Parameters) extends UncacheBundle {
     r.data := resp_fwd_data
     r.id := eid
     r.nderr := resp_nderr
-    r.denied := resp_denied
-    r.corrupt := resp_corrupt
     r.nc := nc
     r.is2lq := cmd === MemoryOpConstants.M_XRD
     r.miss := false.B
     r.replay := false.B
     r.tag_error := false.B
-    r.error := false.B
+    r.error := resp_derr
     r
   }
 }
@@ -180,7 +176,7 @@ class UncacheIO(implicit p: Parameters) extends DCacheBundle {
   val enableOutstanding = Input(Bool())
   val flush = Flipped(new UncacheFlushBundle)
   val lsq = Flipped(new UncacheWordIO)
-  val forward = Vec(LoadPipelineWidth, Flipped(new LoadForwardQueryIO))
+  val forward = Vec(LoadPipelineWidth, Flipped(new UncacheForward))
   val wfi = Flipped(new WfiReqBundle)
   val busError = Output(new L1BusErrorUnitInfo())
 }
@@ -517,28 +513,34 @@ class UncacheImp(outer: Uncache)extends LazyModuleImp(outer)
   f1_needDrain := f1_tagMismatchVec.asUInt.orR && !empty
 
   for ((forward, i) <- io.forward.zipWithIndex) {
-    val f0_fwdValid = forward.valid
+    val fn1_fwdValid = forward.s0Req.valid
+    val fn1_req = forward.s0Req.bits
+    val f0_fwdValid = RegNext(fn1_fwdValid)
+    val f0_req = RegEnable(fn1_req, fn1_fwdValid)
+    val f0_paddr = forward.s1Req.paddr
+    val f0_kill = forward.s1Kill
     val f1_fwdValid = RegNext(f0_fwdValid)
 
     /* f0 */
     // vaddr match
-    val f0_vtagMatches = sizeMap(w => addrMatch(entries(w).vaddr, forward.vaddr))
+    val f0_vtagMatches = sizeMap(w => addrMatch(entries(w).vaddr, f0_req.vaddr))
     val f0_flyTagMatches = sizeMap(w => f0_vtagMatches(w) && f0_validMask(w) && f0_fwdValid && states(w).isFwdOld())
     val f0_idleTagMatches = sizeMap(w => f0_vtagMatches(w) && f0_validMask(w) && f0_fwdValid && states(w).isFwdNew())
     // ONLY for fast use to get better timing
     val f0_flyMaskFast = shiftMaskToHigh(
-      forward.vaddr,
+      f0_req.vaddr,
       Mux1H(f0_flyTagMatches, f0_fwdMaskCandidates)
     ).asTypeOf(Vec(VDataBytes, Bool()))
     val f0_idleMaskFast = shiftMaskToHigh(
-      forward.vaddr,
+      f0_req.vaddr,
       Mux1H(f0_idleTagMatches, f0_fwdMaskCandidates)
     ).asTypeOf(Vec(VDataBytes, Bool()))
 
     /* f1 */
     val f1_flyTagMatches = RegEnable(f0_flyTagMatches, f0_fwdValid)
     val f1_idleTagMatches = RegEnable(f0_idleTagMatches, f0_fwdValid)
-    val f1_fwdPAddr = RegEnable(forward.paddr, f0_fwdValid)
+    val f1_fwdPAddr = RegEnable(f0_paddr, f0_fwdValid)
+    val f1_kill = RegEnable(f0_kill, f0_fwdValid)
     // select
     val f1_flyMask = Mux1H(f1_flyTagMatches, f1_fwdMaskCandidates)
     val f1_flyData = Mux1H(f1_flyTagMatches, f1_fwdDataCandidates)
@@ -552,28 +554,25 @@ class UncacheImp(outer: Uncache)extends LazyModuleImp(outer)
     val f1_ptagMatches = sizeMap(w => addrMatch(RegEnable(entries(w).addr, f0_fwdValid), f1_fwdPAddr))
     f1_tagMismatchVec(i) := sizeMap(w =>
       RegEnable(f0_vtagMatches(w), f0_fwdValid) =/= f1_ptagMatches(w) && RegEnable(f0_validMask(w), f0_fwdValid) && f1_fwdValid
-    ).asUInt.orR
+    ).asUInt.orR && !f1_kill
     XSDebug(
       f1_tagMismatchVec(i),
       "forward tag mismatch: pmatch %x vmatch %x vaddr %x paddr %x\n",
       f1_ptagMatches.asUInt,
       RegEnable(f0_vtagMatches.asUInt, f0_fwdValid),
-      RegEnable(forward.vaddr, f0_fwdValid),
-      RegEnable(forward.paddr, f0_fwdValid)
+      RegEnable(f0_req.vaddr, f0_fwdValid),
+      RegEnable(f0_paddr, f0_fwdValid)
     )
     // response
-    forward.addrInvalid := false.B // addr in ubuffer is always ready
-    forward.dataInvalid := false.B // data in ubuffer is always ready
-    forward.matchInvalid := f1_tagMismatchVec(i) // paddr / vaddr cam result does not match
+    forward.s2Resp.bits.matchInvalid := f1_tagMismatchVec(i) // paddr / vaddr cam result does not match
     for (j <- 0 until VDataBytes) {
-      forward.forwardMaskFast(j) := f0_flyMaskFast(j) || f0_idleMaskFast(j)
-
-      forward.forwardData(j) := f1_fwdData(j)
-      forward.forwardMask(j) := false.B
+      forward.s2Resp.bits.forwardData(j) := f1_fwdData(j)
+      forward.s2Resp.bits.forwardMask(j) := false.B
       when(f1_fwdMask(j) && f1_fwdValid) {
-        forward.forwardMask(j) := true.B
+        forward.s2Resp.bits.forwardMask(j) := true.B
       }
     }
+    forward.s2Resp.valid := f1_fwdValid
 
   }
 
@@ -610,7 +609,7 @@ class UncacheImp(outer: Uncache)extends LazyModuleImp(outer)
   XSPerfAccumulate("uncache_nc_store", io.lsq.req.fire && isStore(io.lsq.req.bits.cmd) && io.lsq.req.bits.nc)
   XSPerfAccumulate("uncache_nc_load", io.lsq.req.fire && !isStore(io.lsq.req.bits.cmd) && io.lsq.req.bits.nc)
   XSPerfAccumulate("uncache_outstanding", uState =/= s_idle && mem_acquire.fire)
-  XSPerfAccumulate("forward_count", PopCount(io.forward.map(_.forwardMask.asUInt.orR)))
+  XSPerfAccumulate("forward_count", PopCount(io.forward.map(_.s2Resp.bits.forwardMask.asUInt.orR)))
   XSPerfAccumulate("forward_vaddr_match_failed", PopCount(f1_tagMismatchVec))
 
   val perfEvents = Seq(
@@ -619,7 +618,7 @@ class UncacheImp(outer: Uncache)extends LazyModuleImp(outer)
     ("uncache_nc_store", io.lsq.req.fire && isStore(io.lsq.req.bits.cmd) && io.lsq.req.bits.nc),
     ("uncache_nc_load", io.lsq.req.fire && !isStore(io.lsq.req.bits.cmd) && io.lsq.req.bits.nc),
     ("uncache_outstanding", uState =/= s_idle && mem_acquire.fire),
-    ("forward_count", PopCount(io.forward.map(_.forwardMask.asUInt.orR))),
+    ("forward_count", PopCount(io.forward.map(_.s2Resp.bits.forwardMask.asUInt.orR))),
     ("forward_vaddr_match_failed", PopCount(f1_tagMismatchVec))
   )
 
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index 6acee163b43..2003056c355 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -57,7 +57,6 @@ class L1BankedDataReadReqWithMask(implicit p: Parameters) extends DCacheBundle
   val addr = Bits(PAddrBits.W)
   val addr_dup = Bits(PAddrBits.W)
   val bankMask = Bits(DCacheBanks.W)
-  val kill = Bool()
   val lqIdx = new LqPtr
 }
 
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index fcce82e5f91..6be1ca5fdfb 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -297,7 +297,6 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.banked_data_read.valid := s1_fire && !s1_nack && !s1_is_prefetch
   io.banked_data_read.bits.addr := s1_vaddr
   io.banked_data_read.bits.addr_dup := s1_vaddr_dup
-  io.banked_data_read.bits.kill := io.lsu.s1_kill_data_read
   io.banked_data_read.bits.way_en := s1_pred_tag_match_way_dup_dc
   io.banked_data_read.bits.bankMask := s1_bank_oh
   io.banked_data_read.bits.lqIdx := s1_req.lqIdx
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 20407c16afd..63c144a535f 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -500,6 +500,7 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   val acquire_not_sent = !s_acquire && !io.mem_acquire.ready
   val data_not_refilled = !w_grantfirst
 
+  val error = RegInit(false.B)
   val denied = RegInit(false.B)
   val corrupt = RegInit(false.B)
   val prefetch = RegInit(false.B)
@@ -589,6 +590,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     }
 
     should_refill_data_reg := miss_req_pipe_reg_bits.isFromLoad
+
+    error := false.B
     denied := false.B
     corrupt := false.B
     prefetch := input_req_is_prefetch && !io.miss_req_pipe_reg.prefetch_late_en(io.req.bits, io.req.valid)
@@ -689,8 +692,9 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
       hasData := false.B
     }
 
-    denied := io.mem_grant.bits.denied || denied
-    corrupt := io.mem_grant.bits.corrupt || corrupt
+    error := io.mem_grant.bits.denied || io.mem_grant.bits.corrupt || error
+    denied := denied || io.mem_grant.bits.denied
+    corrupt := corrupt || io.mem_grant.bits.corrupt
 
     refill_data_raw(refill_count ^ isKeyword) := io.mem_grant.bits.data
     isDirty := io.mem_grant.bits.echo.lift(DirtyKey).getOrElse(false.B)
@@ -1056,7 +1060,8 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val full = Output(Bool())
 
     // forward missqueue
-    val forward = Vec(LoadPipelineWidth, new LduToMissqueueForwardIO)
+    val forward = Flipped(Vec(LoadPipelineWidth, new DCacheForward))
+    val forwardS1PAddrMatch = Output(Vec(LoadPipelineWidth, Bool()))
     val l2_pf_store_only = Input(Bool())
 
     val memSetPattenDetected = Output(Bool())
@@ -1144,18 +1149,38 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.memSetPattenDetected := memSetPattenDetected
 
   val forwardInfo_vec = VecInit(entries.map(_.io.forwardInfo))
-  (0 until LoadPipelineWidth).map(i => {
-    val id = io.forward(i).mshrid
-    val req_valid = io.forward(i).valid
-    val paddr = io.forward(i).paddr
-
-    val (forward_mshr, forwardData) = forwardInfo_vec(id).forward(req_valid, paddr)
-    io.forward(i).forward_result_valid := forwardInfo_vec(id).check(req_valid, paddr)
-    io.forward(i).forward_mshr := forward_mshr
-    io.forward(i).forwardData := forwardData
-    io.forward(i).corrupt := RegNext(forwardInfo_vec(id).corrupt)
-    io.forward(i).denied := RegNext(forwardInfo_vec(id).denied)
-  })
+  io.forward.zipWithIndex.foreach { case (forward, i) =>
+    val s0ReqValid = forward.s0Req.valid
+    val s0Req = forward.s0Req.bits
+    val s1ReqValid = RegNext(s0ReqValid)
+    val s1Req = RegEnable(s0Req, s0ReqValid)
+    val mshrIdOH = UIntToOH(s1Req.mshrId)
+    val paddr = forward.s1Req.paddr
+
+    val s1PaddrMatchVec = VecInit(forwardInfo_vec.map{ case info =>
+      paddr(paddr.getWidth - 1, blockOffBits) === info.paddr(paddr.getWidth - 1, blockOffBits) &&
+      info.inflight})
+    val s1BeatMatchVec  = VecInit(forwardInfo_vec.map{ case info =>
+      Mux(paddr(log2Up(refillBytes)).asBool,
+        info.lastbeat_valid,
+        info.firstbeat_valid
+    )})
+    val s1SelectOH     = s1PaddrMatchVec.asUInt & s1BeatMatchVec.asUInt
+    val s1MshrForwardInfo = Mux1H(s1SelectOH, forwardInfo_vec)
+    val s1RespData = VecInit(
+      s1MshrForwardInfo.raw_data.grouped(VLEN / rowBits).map(VecInit(_).asUInt).toSeq
+    )(paddr(blockOffBits - 1, log2Up(VLEN / 8)))
+    val s1RespValid = s1ReqValid && s1SelectOH.orR
+
+
+    forward.s2Resp.valid := RegNext(s1RespValid)
+    forward.s2Resp.bits.matchInvalid := false.B
+    forward.s2Resp.bits.forwardData := RegEnable(s1RespData.asTypeOf(forward.s2Resp.bits.forwardData), s1ReqValid)
+    forward.s2Resp.bits.denied := RegEnable(s1MshrForwardInfo.denied, s1ReqValid)
+    forward.s2Resp.bits.corrupt := RegEnable(s1MshrForwardInfo.corrupt, s1ReqValid)
+    io.forwardS1PAddrMatch(i) := s1ReqValid && (mshrIdOH & s1PaddrMatchVec.asUInt).orR
+    XSError(((s1SelectOH - 1.U) & s1SelectOH).orR && s1RespValid, "multi mshr hit when forward!\n")
+  }
 
   assert(RegNext(PopCount(secondary_ready_vec) <= 1.U || !io.req.valid))
 //  assert(RegNext(PopCount(secondary_reject_vec) <= 1.U))
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 1917fcc009f..503984760e5 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -437,7 +437,7 @@ object Pbmt {
   def rsvd: UInt = "b11".U  // Reserved for future standard use
   def width: Int = 2
 
-  def apply() = UInt(2.W)
+  def apply() = UInt(width.W)
   def isUncache(a: UInt) = a===nc || a===io
   def isPMA(a: UInt) = a===pma
   def isNC(a: UInt) = a===nc
@@ -559,6 +559,11 @@ class MemBlockidxBundle(implicit p: Parameters) extends TlbBundle {
 }
 
 class TlbReq(implicit p: Parameters) extends TlbBundle {
+  /**
+    * TODO:
+    * 1. remove size, either kill or no_translate
+    * 2. move pmp_addr outside this Bundle
+    */
   val vaddr = Output(UInt(VAddrBits.W))
   val fullva = Output(UInt(XLEN.W))
   val checkfullva = Output(Bool())
@@ -585,9 +590,17 @@ class TlbReq(implicit p: Parameters) extends TlbBundle {
 }
 
 class TlbExceptionBundle(implicit p: Parameters) extends TlbBundle {
-  val ld = Output(Bool())
-  val st = Output(Bool())
-  val instr = Output(Bool())
+  val ld = Bool()
+  val st = Bool()
+  val instr = Bool()
+}
+
+class TlbRespExcp(implicit p: Parameters) extends TlbBundle {
+  val vaNeedExt = Bool()
+  val isHyper = Bool()
+  val gpf = new TlbExceptionBundle
+  val pf = new TlbExceptionBundle
+  val af = new TlbExceptionBundle
 }
 
 class TlbResp(nDups: Int = 1)(implicit p: Parameters) extends TlbBundle {
@@ -598,13 +611,7 @@ class TlbResp(nDups: Int = 1)(implicit p: Parameters) extends TlbBundle {
   val miss = Output(Bool())
   val fastMiss = Output(Bool())
   val isForVSnonLeafPTE = Output(Bool())
-  val excp = Vec(nDups, new Bundle {
-    val vaNeedExt = Output(Bool())
-    val isHyper = Output(Bool())
-    val gpf = new TlbExceptionBundle()
-    val pf = new TlbExceptionBundle()
-    val af = new TlbExceptionBundle()
-  })
+  val excp = Output(Vec(nDups, new TlbRespExcp))
   val ptwBack = Output(Bool()) // when ptw back, wake up replay rs's state
   val memidx = Output(new MemBlockidxBundle)
 
diff --git a/src/main/scala/xiangshan/mem/Bundles.scala b/src/main/scala/xiangshan/mem/Bundles.scala
index 8e6bcbfdeb0..affcc75d3a4 100644
--- a/src/main/scala/xiangshan/mem/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/Bundles.scala
@@ -26,7 +26,10 @@ import xiangshan.backend.Bundles._
 import xiangshan.backend.rob.RobPtr
 import xiangshan.cache._
 import xiangshan.cache.wpu.ReplayCarry
+import xiangshan.frontend.ftq.FtqPtr
+import xiangshan.frontend.PreDecodeInfo
 import xiangshan.mem.prefetch.{PrefetchReqBundle, TrainReqBundle}
+import xiangshan.backend.exu.ExeUnitParams
 
 import scala.math._
 
@@ -125,6 +128,41 @@ object Bundles {
     val updateAddrValid = Bool()
 
     def isSWPrefetch: Bool = isPrefetch && !isHWPrefetch
+    def toExuOutput(param: ExeUnitParams): ExuOutput = {
+      val output = Wire(new ExuOutput(param))
+      output.data   := VecInit(Seq.fill(param.wbPathNum)(this.data))
+      output.pdest  := this.uop.pdest
+      output.robIdx := this.uop.robIdx
+      output.intWen.foreach(_ := this.uop.rfWen)
+      output.fpWen.foreach(_ := this.uop.fpWen)
+      output.vecWen.foreach(_ := this.uop.vecWen)
+      output.v0Wen.foreach(_ := this.uop.v0Wen)
+      output.vlWen.foreach(_ := this.uop.vlWen)
+      output.exceptionVec.foreach(_ := this.uop.exceptionVec)
+      output.flushPipe.foreach(_ := this.uop.flushPipe)
+      output.replay.foreach(_ := this.uop.replayInst)
+      // output.debug := this.debug
+      output.perfDebugInfo.foreach(_ := this.uop.perfDebugInfo)
+      output.debug_seqNum.foreach(_ := this.uop.debug_seqNum)
+      output.lqIdx.foreach(_ := this.uop.lqIdx)
+      output.sqIdx.foreach(_ := this.uop.sqIdx)
+      output.isRVC.foreach(_ := this.uop.isRVC)
+      output.vls.foreach(x => {
+        // x.vdIdx := this.vdIdx.get
+        // x.vdIdxInField := this.vdIdxInField.get
+        x.vpu   := this.uop.vpu
+        x.oldVdPsrc := this.uop.psrc(2)
+        x.isIndexed := VlduType.isIndexed(this.uop.fuOpType)
+        x.isMasked := VlduType.isMasked(this.uop.fuOpType)
+        x.isStrided := VlduType.isStrided(this.uop.fuOpType)
+        x.isWhole := VlduType.isWhole(this.uop.fuOpType)
+        x.isVecLoad := VlduType.isVecLd(this.uop.fuOpType)
+        x.isVlm := VlduType.isMasked(this.uop.fuOpType) && VlduType.isVecLd(this.uop.fuOpType)
+      })
+      // output.isFromLoadUnit.foreach(_ := this.isFromLoadUnit)
+      output.trigger.foreach(_ := this.uop.trigger)
+      output
+    }
   }
 
   class LsPrefetchTrainBundle(implicit p: Parameters) extends LsPipelineBundle {
@@ -168,7 +206,6 @@ object Bundles {
     // load inst replay informations
     val rep_info = new LoadToLsqReplayIO
     val nc_with_data = Bool() // nc access with data
-    val nuke_first   = Bool() // When stld_nuke and storeset hit occur simultaneously, stld_nuke should be handled first.
     // queue entry data, except flag bits, will be updated if writeQueue is true,
     // valid bit in LqWriteBundle will be ignored
     val data_wen_dup = Vec(6, Bool()) // dirty reg dup
@@ -182,7 +219,6 @@ object Bundles {
       this.rep_info := DontCare
       this.nc_with_data := DontCare
       this.data_wen_dup := DontCare
-      this.nuke_first   := DontCare
     }
   }
 
@@ -190,6 +226,93 @@ object Bundles {
     val need_rep = Bool()
   }
 
+  class StoreForwardReqS0(implicit p: Parameters) extends XSBundle {
+    val vaddr = UInt(VAddrBits.W)
+    val sqIdx = new SqPtr
+    val size = UInt(MemorySize.Size.width.W)
+    // MDP
+    // load inst will not be executed until former store (predicted by mdp) addr calcuated
+    val loadWaitBit = Bool()
+    // If (loadWaitBit && loadWaitStrict), strict load wait is needed
+    // load inst will not be executed until ALL former store addr calcuated
+    val loadWaitStrict = Bool()
+    val ssid = UInt(SSIDWidth.W)
+    val storeSetHit = Bool() // inst has been allocated an store set
+    val waitForRobIdx = new RobPtr // store set predicted previous store robIdx
+  }
+
+  class StoreForwardReqS1(implicit p: Parameters) extends XSBundle {
+    val paddr = UInt(PAddrBits.W)
+  }
+
+  class SbufferForwardResp(implicit p: Parameters) extends XSBundle {
+    val forwardMask = Vec((VLEN/8), Bool())
+    val forwardData = Vec((VLEN/8), UInt(8.W))
+    val matchInvalid = Bool()
+  }
+
+  class SQForwardRespS1(implicit p: Parameters) extends XSBundle {
+    // dataInvalid: addr match, but data is not valid for now
+    val dataInvalidFast  = Bool() // resp to load_s1
+    val forwardMaskFast  = Vec((VLEN/8), Bool()) // resp to load_s1
+  }
+
+  class SQForwardRespS2(implicit p: Parameters) extends XSBundle {
+    val forwardMask = Vec((VLEN/8), Bool())
+    val forwardData = Vec((VLEN/8), UInt(8.W))
+    val forwardInvalid = Bool()
+    val matchInvalid = Bool()
+    val addrInvalid = Valid(new SqPtr)
+    val dataInvalid = Valid(new SqPtr)
+  }
+
+  class UncacheForwardResp(implicit p: Parameters) extends SbufferForwardResp // ?
+
+  class SbufferForward(implicit p: Parameters) extends XSBundle {
+    val s0Req = ValidIO(new StoreForwardReqS0)
+    val s1Req = Output(new StoreForwardReqS1)
+    val s1Kill = Output(Bool())
+    val s2Resp = Flipped(ValidIO(new SbufferForwardResp))
+  }
+
+  class SQForward(implicit p: Parameters) extends XSBundle {
+    val s0Req = ValidIO(new StoreForwardReqS0)
+    val s1Req = Output(new StoreForwardReqS1)
+    val s1Kill = Output(Bool())
+    val s1Resp = Flipped(ValidIO(new SQForwardRespS1))
+    val s2Resp = Flipped(ValidIO(new SQForwardRespS2))
+  }
+
+  class UncacheForward(implicit p: Parameters) extends XSBundle {
+    val s0Req = ValidIO(new StoreForwardReqS0)
+    val s1Req = Output(new StoreForwardReqS1)
+    val s1Kill = Output(Bool())
+    val s2Resp = Flipped(ValidIO(new UncacheForwardResp))
+  }
+
+  class UncacheBypassReqS0(implicit p: Parameters) extends XSBundle {
+    val lqIdx = new LqPtr
+    val isNCReplay = Bool()
+    val isMMIOReplay = Bool()
+  }
+
+  class UncacheBypassRespS1(implicit p: Parameters) extends XSBundle {
+    val paddr = UInt(PAddrBits.W)
+  }
+
+  class UncacheBypassRespS2(implicit p: Parameters) extends XSBundle {
+    val data = UInt(VLEN.W)
+    val nderr = Bool()
+    val derr = Bool()
+  }
+
+  class UncacheBypass(implicit p: Parameters) extends XSBundle {
+    val s0Req = ValidIO(new UncacheBypassReqS0)
+    val s1Resp = Flipped(ValidIO(new UncacheBypassRespS1))
+    val s2Resp = Flipped(ValidIO(new UncacheBypassRespS2))
+  }
+
+  // TODO: LoadForwardQueryIO = LoadForwardReq + LoadForwardResp
   class LoadForwardQueryIO(implicit p: Parameters) extends XSBundle {
     val vaddr = Output(UInt(VAddrBits.W))
     val paddr = Output(UInt(PAddrBits.W))
@@ -237,6 +360,9 @@ object Bundles {
     val addrInvalidSqIdx = Input(new SqPtr) // resp to load_s2, sqIdx
   }
 
+
+  // TODO: remove these
+
   // Query load queue for ld-ld violation
   //
   // Req should be send in load_s1
@@ -251,6 +377,7 @@ object Bundles {
 
     // paddr: load's paddr.
     val paddr      = UInt(PAddrBits.W)
+    // TODO: remove data_valid
     // dataInvalid: load data is invalid.
     val data_valid = Bool()
     // nc: is NC access
@@ -268,7 +395,40 @@ object Bundles {
     val revoke = Output(Bool())
   }
 
-  class StoreNukeQueryBundle(implicit p: Parameters) extends XSBundle {
+  class LoadNukeQueryReq(implicit p: Parameters) extends XSBundle {
+    val robIdx = new RobPtr
+    val paddr = UInt(PAddrBits.W)
+    val lqIdx = new LqPtr
+    val sqIdx = new SqPtr
+    val dataValid = Bool()
+    val nc = Bool() // always mark a writebacked NC load as released in RAR
+    val mask = UInt((VLEN/8).W)
+    val isRVC = Bool()
+    val ftqPtr = new FtqPtr
+    val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
+    val pc = UInt(VAddrBits.W)
+    val debugInfo = new PerfDebugInfo
+  }
+
+  class LoadNukeQueryResp(implicit p: Parameters) extends XSBundle {
+    val nuke = Bool()
+  }
+
+  class LoadRARNukeQuery(implicit p: Parameters) extends XSBundle {
+    val req = DecoupledIO(new LoadNukeQueryReq)
+    val resp = Flipped(ValidIO(new LoadNukeQueryResp))
+    val revokeLastCycle = Output(Bool()) // revoke the req in the last cycle
+    val revokeLastLastCycle = Output(Bool()) // revoke the req in the last cycle before last cycle
+  }
+
+  class LoadRAWNukeQuery(implicit p: Parameters) extends XSBundle {
+    // RAW nuke is generated in LoadQueueRAW, therefore there is no response to LDU
+    val req = DecoupledIO(new LoadNukeQueryReq)
+    val revokeLastCycle = Output(Bool())
+    val revokeLastLastCycle = Output(Bool())
+  }
+
+  class StoreNukeQueryReq(implicit p: Parameters) extends XSBundle {
     //  robIdx: Requestor's (a store instruction) rob index for match logic.
     val robIdx = new RobPtr
 
@@ -399,8 +559,17 @@ class VecMissalignedDebugBundle (implicit p: Parameters) extends XSBundle {
   val offset     = UInt(log2Up(XLEN).W) // indicate byte offset of unit-stride's element when unaligned
 }
 
+class DifftestPmaStoreIO(implicit p: Parameters) extends XSBundle {
+  val data           = UInt(VLEN.W)
+  val mask           = UInt((VLEN/8).W)
+  val addr           = UInt(PAddrBits.W)
+  val wline          = Bool()
+  val vecValid       = Bool()
+  val diffIsHighPart = Bool() // indicate whether valid data in high 64-bit, only for scalar store event!
+}
+
 class DiffStoreIO(implicit p: Parameters) extends XSBundle{
   val diffInfo = Vec(EnsbufferWidth, Flipped(new ToSbufferDifftestInfoBundle()))
-  val pmaStore = Vec(EnsbufferWidth, Flipped(Valid(new DCacheWordReqWithVaddrAndPfFlag())))
+  val pmaStore = Vec(EnsbufferWidth, Flipped(Valid(new DifftestPmaStoreIO)))
   val ncStore = Flipped(Valid(new UncacheWordReq()))
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 9b661444e98..944a46279bb 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -81,7 +81,11 @@ trait HasMemBlockParameters extends HasXSParameter {
   val AtomicWBPort   = 0
   val MisalignWBPort = 1
   val UncacheWBPort  = 2
-  val NCWBPorts = Seq(1, 2)
+  val NCWBPorts: Seq[Int] = 0 until LoadPipelineWidth // TODO: remove this
+
+  def debugEn: Boolean = p(DebugOptionsKey).EnableDifftest
+  def basicDebugEn(implicit p: Parameters): Boolean = p(DebugOptionsKey).AlwaysBasicDiff || debugEn
+  def pageOffset: Int      = PageOffsetWidth
 
   def arbiter[T <: Bundle](
     in: Seq[DecoupledIO[T]],
@@ -219,8 +223,7 @@ class mem_to_ooo(implicit p: Parameters) extends MemBlockBundle {
     val vl = Output(UInt((log2Up(VLEN) + 1).W))
     val gpaddr = Output(UInt(XLEN.W))
     val isForVSnonLeafPTE = Output(Bool())
-    val mmio = Output(Vec(LoadPipelineWidth, Bool()))
-    val uop = Output(Vec(LoadPipelineWidth, new DynInst))
+    val mmioBusy = Output(Bool())
     val lqCanAccept = Output(Bool())
     val sqCanAccept = Output(Bool())
   }
@@ -230,10 +233,9 @@ class mem_to_ooo(implicit p: Parameters) extends MemBlockBundle {
     val pc     = Input(UInt(VAddrBits.W))
   })
 
-  val intWriteback: MixedVec[MixedVec[DecoupledIO[ExuOutput]]] = intSchdParams.genExuOutputDecoupledBundleMemBlock
+  val intWriteback: MixedVec[MixedVec[DecoupledIO[NewExuOutput]]] = intSchdParams.genNewExuOutputDecoupledBundleMemBlock
   val vecWriteback: MixedVec[MixedVec[DecoupledIO[ExuOutput]]] = vecSchdParams.genExuOutputDecoupledBundleMemBlock
 
-  val ldaIqFeedback = Vec(LduCnt, new MemRSFeedbackIO)
   val staIqFeedback = Vec(StaCnt, new MemRSFeedbackIO)
   val hyuIqFeedback = Vec(HyuCnt, new MemRSFeedbackIO)
   val vstuIqFeedback= Vec(VstuCnt, new MemRSFeedbackIO(isVector = true))
@@ -444,7 +446,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   val issueStd = intIssue.filter(_.bits.params.hasStdFu)
   val issueVldu = vecIssue.filter(_.bits.params.hasVLoadFu)
 
-  val intWriteback: Seq[DecoupledIO[ExuOutput]] = io.mem_to_ooo.intWriteback.flatten
+  val intWriteback: Seq[DecoupledIO[NewExuOutput]] = io.mem_to_ooo.intWriteback.flatten
   val vecWriteback: Seq[DecoupledIO[ExuOutput]] = io.mem_to_ooo.vecWriteback.flatten
   val writeback = intWriteback ++ vecWriteback
   val writebackLda = intWriteback.filter(_.bits.params.hasLoadFu)
@@ -452,7 +454,10 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   val writebackStd = intWriteback.filter(_.bits.params.hasStdFu)
   val writebackVldu = vecWriteback.filter(_.bits.params.hasVLoadFu)
 
-  writeback.zipWithIndex.foreach{ case (wb, i) =>
+  intWriteback.zipWithIndex.foreach{ case (wb, i) =>
+    wb.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtBypassVal.id.U, wb.valid, clock, reset))
+  }
+  vecWriteback.zipWithIndex.foreach{ case (wb, i) =>
     wb.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtBypassVal.id.U, wb.valid, clock, reset))
   }
 
@@ -486,7 +491,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     io.uncacheError.ecc_error.valid := false.B
   }
 
-  val loadUnits = Seq.tabulate(LduCnt)(i => Module(new LoadUnit(ldaParams(i))))
+  val newLoadUnits = Seq.tabulate(LduCnt)(i => Module(new NewLoadUnit(ldaParams(i))))
   val storeUnits = Seq.tabulate(StaCnt)(i => Module(new StoreUnit(staParams(i))))
   val stdExeUnits = Seq.tabulate(StdCnt)(i => Module(new StdExeUnit(stdParams(i))))
   val atomicsUnit = Module(new AtomicsUnit(mouParam))
@@ -500,30 +505,32 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   val vfofBuffer    = Module(new VfofBuffer(vlduParams.head))
 
   // misalign Buffer
-  val loadMisalignBuffer = Module(new LoadMisalignBuffer(ldaParams.head))
   val storeMisalignBuffer = Module(new StoreMisalignBuffer)
 
-  loadUnits.zipWithIndex.map(x => x._1.suggestName("LoadUnit_"+x._2))
+  // exceptionInfoGen
+  val exceptionInfoGen = Module(new ExceptionInfoGen)
+
+  newLoadUnits.zipWithIndex.map(x => x._1.suggestName("LoadUnit_"+x._2))
   storeUnits.zipWithIndex.map(x => x._1.suggestName("StoreUnit_"+x._2))
 
+
   writebackLda.zipWithIndex.foreach { case (wb, i) =>
     if (i == AtomicWBPort) {
       // atomicsUnit writeback
-      oneHotArbiter(Seq(atomicsUnit.io.out, loadUnits(i).io.ldout), wb, Some("writebackLdaAtomic"))
-    } else if (i == MisalignWBPort) {
-      // misalign writeback
-      arbiter(Seq(loadUnits(i).io.ldout, loadMisalignBuffer.io.writeBack), wb, Some("writebackLdaMisalign"))
-    } else if (i == UncacheWBPort) {
-      // uncache writeback
-      wb <> loadUnits(i).io.ldout
+      val lduDecoupledOut = Wire(DecoupledIO(new NewExuOutput(ldaParams(i))))
+      val atomicDecoupledOut = Wire(DecoupledIO(new NewExuOutput(ldaParams(i))))
+      connectMemDecoupledNewExuOutput(lduDecoupledOut, newLoadUnits(i).io.ldout)
+      connectMemDecoupledNewExuOutput(atomicDecoupledOut, atomicsUnit.io.out)
+
+      oneHotArbiter(Seq(atomicDecoupledOut, lduDecoupledOut), wb, Some("writebackLdaAtomic"))
     } else {
       // normal load writeback
-      wb <> loadUnits(i).io.ldout
+      connectMemDecoupledNewExuOutput(wb, newLoadUnits(i).io.ldout)
     }
   }
 
   writebackStd.zipWithIndex.foreach { case (wb, i) =>
-    wb <> stdExeUnits(i).io.out
+    connectMemDecoupledNewExuOutput(wb, stdExeUnits(i).io.out)
   }
 
   val lsq     = Module(new LsqWrapper)
@@ -724,10 +731,10 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   prefetcher.io.fromDCache.refillTrain := dcache.io.refillTrain
   prefetcher.io.fromOOO.s1_loadPc := issueLda.map(x => RegNext(x.bits.pc.get)) ++ io.ooo_to_mem.hybridPc
   prefetcher.io.fromOOO.s1_storePc := io.ooo_to_mem.storePc ++ io.ooo_to_mem.hybridPc
-  prefetcher.io.trainSource.s1_loadFireHint := loadUnits.map(_.io.s1_prefetch_spec)
-  prefetcher.io.trainSource.s2_loadFireHint := loadUnits.map(_.io.s2_prefetch_spec)
-  prefetcher.io.trainSource.s3_load := loadUnits.map(_.io.prefetch_train)
-  prefetcher.io.trainSource.s3_ptrChasing := loadUnits.map(_ => false.B) // TODO: remove ptr chasing logic in prefetcher
+  prefetcher.io.trainSource.s1_loadFireHint := newLoadUnits.map(_.io.prefetchTrainHintS1)
+  prefetcher.io.trainSource.s2_loadFireHint := newLoadUnits.map(_.io.prefetchTrainHintS2)
+  prefetcher.io.trainSource.s3_load := newLoadUnits.map(_.io.prefetchTrain)
+  prefetcher.io.trainSource.s3_ptrChasing := newLoadUnits.map(_ => false.B) // TODO: remove ptr chasing logic in prefetcher
   prefetcher.io.trainSource.s1_storeFireHint := storeUnits.map(_.io.s1_prefetch_spec)
   prefetcher.io.trainSource.s2_storeFireHint := storeUnits.map(_.io.s2_prefetch_spec)
   prefetcher.io.trainSource.s3_store <> storeUnits.map(_.io.prefetch_train)
@@ -767,26 +774,16 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   // Because all the unfairness between ldu0 and ldu1/2, such as bank conflicts and lower entry priority in MissQueue,
   // belong to the replay channel, whose priority is higher than prefetch channel in loadunit.
   // Therefore, there is no need to distinguish among ldu0, ldu1, and ldu2 if **prefetch-request outstanding <= 1**.
-  val canAcceptHighConfPrefetch = loadUnits.map(_.io.canAcceptHighConfPrefetch)
-  val canAcceptLowConfPrefetch = loadUnits.map(_.io.canAcceptLowConfPrefetch)
-  val canAcceptPrefetch = (0 until LduCnt + HyuCnt).map{ case i =>
-    Mux(l1_pf_req.bits.confidence === 1.U, canAcceptHighConfPrefetch(i), canAcceptLowConfPrefetch(i))
-    /* // if it needs to distinguish ldu0 with others, use the code below
-    if (LduCnt > 1 && i == 0) {
-      Mux(l1_pf_req.bits.confidence === 1.U, canAcceptHighConfPrefetch(i), canAcceptLowConfPrefetch(i))
-    } else {
-      canAcceptLowConfPrefetch(i)
-    } */
-  }
-  l1_pf_req.ready := canAcceptPrefetch.reduce(_ || _)
+  val canAcceptPrefetch = newLoadUnits.map(_.io.prefetchReq.ready)
 
   val toPrefetchValidVec = (0 until LduCnt + HyuCnt).map{ case i =>
     if(i==0) l1_pf_req.valid
     else l1_pf_req.valid && !canAcceptPrefetch.take(i).reduce(_ || _)
   }
-  loadUnits.zipWithIndex.foreach { case(u, i) => {
-    u.io.prefetch_req.valid <> toPrefetchValidVec(i)
-    u.io.prefetch_req.bits <> l1_pf_req.bits
+  l1_pf_req.ready := Cat(canAcceptPrefetch).orR
+  newLoadUnits.zipWithIndex.foreach { case(u, i) => {
+    u.io.prefetchReq.valid <> toPrefetchValidVec(i)
+    u.io.prefetchReq.bits <> l1_pf_req.bits
   }}
 
   /** l1 pf fuzzer interface */
@@ -798,22 +795,22 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     fuzzer.io.paddr := DontCare
 
     // override load_unit prefetch_req
-    loadUnits.foreach(load_unit => {
-      load_unit.io.prefetch_req.valid <> fuzzer.io.req.valid
-      load_unit.io.prefetch_req.bits <> fuzzer.io.req.bits
+    newLoadUnits.foreach( ldu => {
+       ldu.io.prefetchReq.valid <> fuzzer.io.req.valid
+       ldu.io.prefetchReq.bits <> fuzzer.io.req.bits
     })
 
     fuzzer.io.req.ready := l1_pf_req.ready
   }
 
   for (i <- 0 until LduCnt) {
-    io.debug_ls.debugLsInfo(i) := loadUnits(i).io.debug_ls
+    io.debug_ls.debugLsInfo(i) := newLoadUnits(i).io.debugInfo
   }
   for (i <- 0 until StaCnt) {
     io.debug_ls.debugLsInfo.drop(LduCnt)(i) := storeUnits(i).io.debug_ls
   }
 
-  io.mem_to_ooo.lsTopdownInfo := loadUnits.map(_.io.lsTopdownInfo)
+  io.mem_to_ooo.lsTopdownInfo := newLoadUnits.map(_.io.topDownInfo)
 
   // trigger
   val tdata = RegInit(VecInit(Seq.fill(TriggerNum)(0.U.asTypeOf(new MatchTriggerIO))))
@@ -842,181 +839,117 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vSegmentFlag := false.B
   }
 
-  val misalign_allow_spec = RegInit(true.B)
-  val ldu_rollback_with_misalign_nack = loadUnits.map(ldu =>
-    ldu.io.lsq.ldin.bits.isFrmMisAlignBuf && ldu.io.lsq.ldin.bits.rep_info.rar_nack && ldu.io.rollback.valid
-  ).reduce(_ || _)
-  when (ldu_rollback_with_misalign_nack) {
-    misalign_allow_spec := false.B
-  } .elsewhen(lsq.io.rarValidCount < (LoadQueueRARSize - 4).U) {
-    misalign_allow_spec := true.B
-  }
-
   // LoadUnit
-  val correctMissTrain = Constantin.createRecord(s"CorrectMissTrain$hartId", initValue = false)
-
   for (i <- 0 until LduCnt) {
-    loadUnits(i).io.redirect <> redirect
-    loadUnits(i).io.misalign_allow_spec := misalign_allow_spec
+    newLoadUnits(i).io.redirect <> redirect
 
     // get input form dispatch
-    loadUnits(i).io.ldin <> issueLda(i)
-    loadUnits(i).io.feedback_slow <> io.mem_to_ooo.ldaIqFeedback(i).feedbackSlow
-    io.mem_to_ooo.ldaIqFeedback(i).feedbackFast := DontCare
-    loadUnits(i).io.correctMissTrain := correctMissTrain
-    io.mem_to_ooo.ldCancel(i) := loadUnits(i).io.ldCancel
-    io.mem_to_ooo.wakeup(i) := loadUnits(i).io.wakeup
-
-    // vector
-    if (i < VlduCnt) {
-      loadUnits(i).io.vecldout.ready := false.B
-    } else {
-      loadUnits(i).io.vecldin.valid := false.B
-      loadUnits(i).io.vecldin.bits := DontCare
-      loadUnits(i).io.vecldout.ready := false.B
-    }
-
-    // fast replay
-    loadUnits(i).io.fast_rep_in <> loadUnits(i).io.fast_rep_out
+    newLoadUnits(i).io.ldin <> issueLda(i)
+    io.mem_to_ooo.ldCancel(i).ld1Cancel := false.B
+    io.mem_to_ooo.ldCancel(i).ld2Cancel := newLoadUnits(i).io.cancel
+    io.mem_to_ooo.wakeup(i) := newLoadUnits(i).io.wakeup
 
-    // SoftPrefetch to frontend (prefetch.i)
-    loadUnits(i).io.ifetchPrefetch <> io.ifetchPrefetch(i)
+    // software prefetch to frontend (prefetch.i)
+    io.ifetchPrefetch(i) <> newLoadUnits(i).io.swInstrPrefetch
 
     // dcache access
-    loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
-    if(i == 0){
+    dcache.io.lsu.load(i) <> newLoadUnits(i).io.dcache
+    if (i == 0) {
       vSegmentUnit.io.rdcache := DontCare
-      dcache.io.lsu.load(i).req.valid := loadUnits(i).io.dcache.req.valid || vSegmentUnit.io.rdcache.req.valid
+      dcache.io.lsu.load(i).req.valid := newLoadUnits(i).io.dcache.req.valid || vSegmentUnit.io.rdcache.req.valid
       dcache.io.lsu.load(i).req.bits  := Mux1H(Seq(
         vSegmentUnit.io.rdcache.req.valid -> vSegmentUnit.io.rdcache.req.bits,
-        loadUnits(i).io.dcache.req.valid -> loadUnits(i).io.dcache.req.bits
+        newLoadUnits(i).io.dcache.req.valid -> newLoadUnits(i).io.dcache.req.bits
       ))
       vSegmentUnit.io.rdcache.req.ready := dcache.io.lsu.load(i).req.ready
+
+      when (vSegmentFlag) {
+        dcache.io.lsu.load(i).pf_source              := vSegmentUnit.io.rdcache.pf_source
+        dcache.io.lsu.load(i).s1_paddr_dup_lsu       := vSegmentUnit.io.rdcache.s1_paddr_dup_lsu
+        dcache.io.lsu.load(i).s1_paddr_dup_dcache    := vSegmentUnit.io.rdcache.s1_paddr_dup_dcache
+        dcache.io.lsu.load(i).s1_kill                := vSegmentUnit.io.rdcache.s1_kill
+        dcache.io.lsu.load(i).s2_kill                := vSegmentUnit.io.rdcache.s2_kill
+        dcache.io.lsu.load(i).s0_pc                  := vSegmentUnit.io.rdcache.s0_pc
+        dcache.io.lsu.load(i).s1_pc                  := vSegmentUnit.io.rdcache.s1_pc
+        dcache.io.lsu.load(i).s2_pc                  := vSegmentUnit.io.rdcache.s2_pc
+        dcache.io.lsu.load(i).is128Req               := vSegmentUnit.io.rdcache.is128Req
+      }.otherwise {
+        dcache.io.lsu.load(i).pf_source              := newLoadUnits(i).io.dcache.pf_source
+        dcache.io.lsu.load(i).s1_paddr_dup_lsu       := newLoadUnits(i).io.dcache.s1_paddr_dup_lsu
+        dcache.io.lsu.load(i).s1_paddr_dup_dcache    := newLoadUnits(i).io.dcache.s1_paddr_dup_dcache
+        dcache.io.lsu.load(i).s1_kill                := newLoadUnits(i).io.dcache.s1_kill
+        dcache.io.lsu.load(i).s2_kill                := newLoadUnits(i).io.dcache.s2_kill
+        dcache.io.lsu.load(i).s0_pc                  := newLoadUnits(i).io.dcache.s0_pc
+        dcache.io.lsu.load(i).s1_pc                  := newLoadUnits(i).io.dcache.s1_pc
+        dcache.io.lsu.load(i).s2_pc                  := newLoadUnits(i).io.dcache.s2_pc
+        dcache.io.lsu.load(i).is128Req               := newLoadUnits(i).io.dcache.is128Req
+      }
     }
 
     // Dcache requests must also be preempted by the segment.
     when(vSegmentFlag){
-      loadUnits(i).io.dcache.req.ready             := false.B // Dcache is preempted.
-
-      dcache.io.lsu.load(0).pf_source              := vSegmentUnit.io.rdcache.pf_source
-      dcache.io.lsu.load(0).s1_paddr_dup_lsu       := vSegmentUnit.io.rdcache.s1_paddr_dup_lsu
-      dcache.io.lsu.load(0).s1_paddr_dup_dcache    := vSegmentUnit.io.rdcache.s1_paddr_dup_dcache
-      dcache.io.lsu.load(0).s1_kill                := vSegmentUnit.io.rdcache.s1_kill
-      dcache.io.lsu.load(0).s2_kill                := vSegmentUnit.io.rdcache.s2_kill
-      dcache.io.lsu.load(0).s0_pc                  := vSegmentUnit.io.rdcache.s0_pc
-      dcache.io.lsu.load(0).s1_pc                  := vSegmentUnit.io.rdcache.s1_pc
-      dcache.io.lsu.load(0).s2_pc                  := vSegmentUnit.io.rdcache.s2_pc
-      dcache.io.lsu.load(0).is128Req               := vSegmentUnit.io.rdcache.is128Req
+      newLoadUnits(i).io.dcache.req.ready             := false.B // Dcache is preempted.
     }.otherwise {
-      loadUnits(i).io.dcache.req.ready             := dcache.io.lsu.load(i).req.ready
-
-      dcache.io.lsu.load(0).pf_source              := loadUnits(0).io.dcache.pf_source
-      dcache.io.lsu.load(0).s1_paddr_dup_lsu       := loadUnits(0).io.dcache.s1_paddr_dup_lsu
-      dcache.io.lsu.load(0).s1_paddr_dup_dcache    := loadUnits(0).io.dcache.s1_paddr_dup_dcache
-      dcache.io.lsu.load(0).s1_kill                := loadUnits(0).io.dcache.s1_kill
-      dcache.io.lsu.load(0).s2_kill                := loadUnits(0).io.dcache.s2_kill
-      dcache.io.lsu.load(0).s0_pc                  := loadUnits(0).io.dcache.s0_pc
-      dcache.io.lsu.load(0).s1_pc                  := loadUnits(0).io.dcache.s1_pc
-      dcache.io.lsu.load(0).s2_pc                  := loadUnits(0).io.dcache.s2_pc
-      dcache.io.lsu.load(0).is128Req               := loadUnits(0).io.dcache.is128Req
+      newLoadUnits(i).io.dcache.req.ready             := dcache.io.lsu.load(i).req.ready
     }
 
-    // forward
-    loadUnits(i).io.lsq.forward <> lsq.io.forward(i)
-    loadUnits(i).io.sbuffer <> sbuffer.io.forward(i)
-    loadUnits(i).io.ubuffer <> uncache.io.forward(i)
-    loadUnits(i).io.tl_d_channel := dcache.io.lsu.forward_D(i)
-    loadUnits(i).io.forward_mshr <> dcache.io.lsu.forward_mshr(i)
-    // ld-ld violation check
-    loadUnits(i).io.lsq.ldld_nuke_query <> lsq.io.ldu.ldld_nuke_query(i)
-    loadUnits(i).io.lsq.stld_nuke_query <> lsq.io.ldu.stld_nuke_query(i)
-    // loadqueue old ptr
-    loadUnits(i).io.lsq.lqDeqPtr := lsq.io.lqDeqPtr
-    loadUnits(i).io.csrCtrl       <> csrCtrl
-    // dcache refill req
-  // loadUnits(i).io.refill           <> delayedDcacheRefill
+    // forward & NC bypass
+    lsq.io.forward(i) <> newLoadUnits(i).io.sqForward
+    sbuffer.io.forward(i) <> newLoadUnits(i).io.sbufferForward
+    uncache.io.forward(i) <> newLoadUnits(i).io.uncacheForward
+    dcache.io.lsu.forward_D(i) <> newLoadUnits(i).io.tldForward
+    dcache.io.lsu.forward_mshr(i) <> newLoadUnits(i).io.mshrForward
+    lsq.io.bypass(i) <> newLoadUnits(i).io.uncacheBypass
+    // RAW / RAR violation check
+    lsq.io.ldu.rawNukeQuery(i) <> newLoadUnits(i).io.rawNukeQuery
+    lsq.io.ldu.rarNukeQuery(i) <> newLoadUnits(i).io.rarNukeQuery
+    // CSR control signals
+    newLoadUnits(i).io.csrCtrl <> csrCtrl
     // dtlb
-    loadUnits(i).io.tlb <> dtlb_reqs.take(LduCnt)(i)
-    if(i == 0 ){ // port 0 assign to vsegmentUnit
+    newLoadUnits(i).io.tlb <> dtlb_reqs.take(LduCnt)(i)
+    if (i == 0) { // port 0 assign to vsegmentUnit
       val vsegmentDtlbReqValid = vSegmentUnit.io.dtlb.req.valid // segment tlb resquest need to delay 1 cycle
-      dtlb_reqs.take(LduCnt)(i).req.valid := loadUnits(i).io.tlb.req.valid || RegNext(vsegmentDtlbReqValid)
+      dtlb_reqs.take(LduCnt)(i).req.valid := newLoadUnits(i).io.tlb.req.valid || RegNext(vsegmentDtlbReqValid)
       vSegmentUnit.io.dtlb.req.ready      := dtlb_reqs.take(LduCnt)(i).req.ready
       dtlb_reqs.take(LduCnt)(i).req.bits  := ParallelPriorityMux(Seq(
         RegNext(vsegmentDtlbReqValid)     -> RegEnable(vSegmentUnit.io.dtlb.req.bits, vsegmentDtlbReqValid),
-        loadUnits(i).io.tlb.req.valid     -> loadUnits(i).io.tlb.req.bits
+        newLoadUnits(i).io.tlb.req.valid     -> newLoadUnits(i).io.tlb.req.bits
       ))
     }
     // pmp
-    loadUnits(i).io.pmp <> pmp_check(i).resp
+    newLoadUnits(i).io.pmp <> pmp_check(i).resp
     // st-ld violation query
-    val stld_nuke_query = storeUnits.map(_.io.stld_nuke_query)
-    for (s <- 0 until StorePipelineWidth) {
-      loadUnits(i).io.stld_nuke_query(s) := stld_nuke_query(s)
-    }
+    newLoadUnits(i).io.staNukeQueryReq <> storeUnits.map(_.io.stld_nuke_query)
 
     // load replay
-    loadUnits(i).io.replay <> lsq.io.replay(i)
+    newLoadUnits(i).io.replay <> lsq.io.replay(i)
 
     val l2_hint = RegNext(io.l2_hint)
 
     // L2 Hint for DCache
     dcache.io.l2_hint <> l2_hint
 
-    loadUnits(i).io.tlb_hint.id := dtlbRepeater.io.hint.get.req(i).id
-    loadUnits(i).io.tlb_hint.full := dtlbRepeater.io.hint.get.req(i).full ||
+    newLoadUnits(i).io.tlbHint.id := dtlbRepeater.io.hint.get.req(i).id
+    newLoadUnits(i).io.tlbHint.full := dtlbRepeater.io.hint.get.req(i).full ||
       tlbreplay_reg(i) || dtlb_ld0_tlbreplay_reg(i)
 
-    // passdown to lsq (load s2)
-    lsq.io.ldu.ldin(i) <> loadUnits(i).io.lsq.ldin
-    if (i == UncacheWBPort) {
-      lsq.io.ldout(i) <> loadUnits(i).io.lsq.uncache
-    } else {
-      lsq.io.ldout(i).ready := true.B
-      loadUnits(i).io.lsq.uncache.valid := false.B
-      loadUnits(i).io.lsq.uncache.bits := DontCare
-    }
-    lsq.io.ld_raw_data(i) <> loadUnits(i).io.lsq.ld_raw_data
-    lsq.io.ncOut(i) <> loadUnits(i).io.lsq.nc_ldin
+    // passdown to lsq (load s3)
+    lsq.io.ldu.ldin(i) <> newLoadUnits(i).io.lqWrite
     lsq.io.l2_hint.valid := l2_hint.valid
     lsq.io.l2_hint.bits.sourceId := l2_hint.bits.sourceId
     lsq.io.l2_hint.bits.isKeyword := l2_hint.bits.isKeyword
 
     lsq.io.tlb_hint <> dtlbRepeater.io.hint.get
 
-    // connect misalignBuffer
-    loadMisalignBuffer.io.enq(i) <> loadUnits(i).io.misalign_enq
-
-    if (i == MisalignWBPort) {
-      loadUnits(i).io.misalign_ldin  <> loadMisalignBuffer.io.splitLoadReq
-      loadUnits(i).io.misalign_ldout <> loadMisalignBuffer.io.splitLoadResp
-    } else {
-      loadUnits(i).io.misalign_ldin.valid := false.B
-      loadUnits(i).io.misalign_ldin.bits := DontCare
-    }
-
-    // update mem dependency predictor
-    // io.memPredUpdate(i) := DontCare
-
     // --------------------------------
     // Load Triggers
     // --------------------------------
-    loadUnits(i).io.fromCsrTrigger.tdataVec := tdata
-    loadUnits(i).io.fromCsrTrigger.tEnableVec := tEnable
-    loadUnits(i).io.fromCsrTrigger.triggerCanRaiseBpExp := triggerCanRaiseBpExp
-    loadUnits(i).io.fromCsrTrigger.debugMode := debugMode
+    newLoadUnits(i).io.csrTrigger.tdataVec := tdata
+    newLoadUnits(i).io.csrTrigger.tEnableVec := tEnable
+    newLoadUnits(i).io.csrTrigger.triggerCanRaiseBpExp := triggerCanRaiseBpExp
+    newLoadUnits(i).io.csrTrigger.debugMode := debugMode
   }
 
-  // misalignBuffer
-  loadMisalignBuffer.io.redirect                <> redirect
-  loadMisalignBuffer.io.rob.lcommit             := io.ooo_to_mem.lsqio.lcommit
-  loadMisalignBuffer.io.rob.scommit             := io.ooo_to_mem.lsqio.scommit
-  loadMisalignBuffer.io.rob.commit              := io.ooo_to_mem.lsqio.commit
-  loadMisalignBuffer.io.rob.pendingPtr          := io.ooo_to_mem.lsqio.pendingPtr
-  loadMisalignBuffer.io.rob.pendingPtrNext      := io.ooo_to_mem.lsqio.pendingPtrNext
-
-  lsq.io.loadMisalignFull                       := loadMisalignBuffer.io.loadMisalignFull
-  lsq.io.misalignAllowSpec                      := misalign_allow_spec
-
   storeMisalignBuffer.io.redirect               <> redirect
   storeMisalignBuffer.io.rob.lcommit            := io.ooo_to_mem.lsqio.lcommit
   storeMisalignBuffer.io.rob.scommit            := io.ooo_to_mem.lsqio.scommit
@@ -1049,9 +982,10 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     stu.io.csrCtrl       <> csrCtrl
     stu.io.dcache        <> dcache.io.lsu.sta(i)
     stu.io.feedback_slow <> io.mem_to_ooo.staIqFeedback(i).feedbackSlow
-    stu.io.stin         <> issueSta(i)
-    stu.io.lsq          <> lsq.io.sta.storeAddrIn(i)
-    stu.io.lsq_replenish <> lsq.io.sta.storeAddrInRe(i)
+    stu.io.stin          <> issueSta(i)
+    stu.io.toLsq         <> lsq.io.sta.storeAddrIn(i)
+    stu.io.toLsqRe       <> lsq.io.sta.storeAddrInRe(i)
+    stu.io.toStoreUnalignQueue <> lsq.io.sta.unalignQueueReq(i)
     // dtlb
     stu.io.tlb          <> dtlb_st.head.requestor(i)
     stu.io.pmp          <> pmp_check(TlbStartVec(dtlb_st_idx) + i).resp
@@ -1091,8 +1025,6 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     // 2. when store issue, broadcast issued sqPtr to wake up the following insts
     io.mem_to_ooo.updateLFST(i) := stu.io.updateLFST
 
-    stu.io.stout.ready := true.B
-
     // vector
     if (i < VstuCnt) {
       stu.io.vecstin <> vsSplit(i).io.out
@@ -1105,22 +1037,21 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     stu.io.vec_isFirstIssue := true.B // TODO
   }
 
-  val sqStout, sqStoutLatch = Wire(DecoupledIO(new ExuOutput(staParams.head)))
-  oneHotArbiter(Seq(lsq.io.mmioStout, lsq.io.cboZeroStout), sqStout, Some("sqStout"))
-  NewPipelineConnect(sqStout, sqStoutLatch, sqStoutLatch.fire, false.B, Some("sqStout"))
+  val sqStoutLatch = Wire(DecoupledIO(new NewExuOutput(staParams.head)))
+  NewPipelineConnect(lsq.io.mmioStout, sqStoutLatch, sqStoutLatch.fire, false.B, Some("sqStout"))
   writebackSta.zipWithIndex.foreach { case (wb, i) =>
     if (i == 0) {
+      val staDecoupledOut = Wire(DecoupledIO(new NewExuOutput(staParams(i))))
+      connectMemDecoupledNewExuOutput(staDecoupledOut, storeUnits(i).io.stout)
       arbiter(
-        Seq(storeUnits(i).io.stout, sqStoutLatch, storeMisalignBuffer.io.writeBack),
+        Seq(staDecoupledOut, sqStoutLatch, storeMisalignBuffer.io.writeBack),
         wb, Some(s"writebackSta_$i")
       )
     } else {
-      wb <> storeUnits(i).io.stout
+      connectMemDecoupledNewExuOutput(wb, storeUnits(i).io.stout)
     }
   }
 
-  lsq.io.vecmmioStout.ready := false.B
-
   // Uncache
   uncache.io.enableOutstanding := io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable
   uncache.io.hartId := io.hartId
@@ -1128,8 +1059,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   lsq.io.uncacheOutstanding := io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable
 
   // Lsq
-  io.mem_to_ooo.lsqio.mmio       := lsq.io.rob.mmio
-  io.mem_to_ooo.lsqio.uop        := lsq.io.rob.uop
+  io.mem_to_ooo.lsqio.mmioBusy   := lsq.io.rob.mmioBusy
   lsq.io.rob.lcommit             := io.ooo_to_mem.lsqio.lcommit
   lsq.io.rob.scommit             := io.ooo_to_mem.lsqio.scommit
   lsq.io.rob.commit              := io.ooo_to_mem.lsqio.commit
@@ -1141,17 +1071,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   lsq.io.brqRedirect    <> redirect
 
   //  violation rollback
-  def selectOldestRedirect(xs: Seq[Valid[Redirect]]): Vec[Bool] = {
-    val compareVec = (0 until xs.length).map(i => (0 until i).map(j => isAfter(xs(j).bits.robIdx, xs(i).bits.robIdx)))
-    val resultOnehot = VecInit((0 until xs.length).map(i => Cat((0 until xs.length).map(j =>
-      (if (j < i) !xs(j).valid || compareVec(i)(j)
-      else if (j == i) xs(i).valid
-      else !xs(j).valid || !compareVec(j)(i))
-    )).andR))
-    resultOnehot
-  }
-  val allRedirect = loadUnits.map(_.io.rollback) ++ lsq.io.nack_rollback ++ lsq.io.nuke_rollback
-  val oldestOneHot = selectOldestRedirect(allRedirect)
+  val allRedirect = newLoadUnits.map(_.io.rollback) ++ lsq.io.nack_rollback ++ lsq.io.nuke_rollback
+  val oldestOneHot = Redirect.selectOldestRedirect(allRedirect)
   val oldestRedirect = WireDefault(Mux1H(oldestOneHot, allRedirect))
   // memory replay would not cause IAF/IPF/IGPF
   oldestRedirect.bits.backendIAF := false.B
@@ -1162,7 +1083,6 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   io.mem_to_ooo.lsqio.sqCanAccept  := lsq.io.sqCanAccept
   io.mem_to_ooo.mdpTrain           := lsq.io.mdpTrain
 
-  // lsq.io.uncache        <> uncache.io.lsq
   val s_idle :: s_scalar_uncache :: s_vector_uncache :: Nil = Enum(3)
   val uncacheState = RegInit(s_idle)
   val uncacheReq = Wire(Decoupled(new UncacheWordReq))
@@ -1235,22 +1155,22 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   // Todo: assign these
   io.mem_to_ooo.sqDeqPtr := lsq.io.sqDeqPtr
   io.mem_to_ooo.lqDeqPtr := lsq.io.lqDeqPtr
-  lsq.io.tl_d_channel <> dcache.io.lsu.tl_d_channel
+  lsq.io.loadWakeup := dcache.io.lsu.loadWakeup
 
   // LSQ to store buffer
   lsq.io.sbuffer        <> sbuffer.io.in
-  sbuffer.io.in(0).valid := lsq.io.sbuffer(0).valid || vSegmentUnit.io.sbuffer.valid
-  sbuffer.io.in(0).bits  := Mux1H(Seq(
+  sbuffer.io.in.req(0).valid := lsq.io.sbuffer.req(0).valid || vSegmentUnit.io.sbuffer.valid
+  sbuffer.io.in.req(0).bits  := Mux1H(Seq(
     vSegmentUnit.io.sbuffer.valid -> vSegmentUnit.io.sbuffer.bits,
-    lsq.io.sbuffer(0).valid       -> lsq.io.sbuffer(0).bits
+    lsq.io.sbuffer.req(0).valid       -> lsq.io.sbuffer.req(0).bits
   ))
-  vSegmentUnit.io.sbuffer.ready := sbuffer.io.in(0).ready
+  vSegmentUnit.io.sbuffer.ready := sbuffer.io.in.req(0).ready
   lsq.io.sqEmpty        <> sbuffer.io.sqempty
   dcache.io.force_write := lsq.io.force_write
 
   // Initialize when unenabled difftest.
   sbuffer.io.diffStore := DontCare
-  lsq.io.diffStore := DontCare
+  lsq.io.diffStore.foreach(_ := DontCare)
   vSegmentUnit.io.vecDifftestInfo := DontCare
   io.mem_to_ooo.storeDebugInfo := DontCare
   // store event difftest information
@@ -1260,17 +1180,16 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
       if(i == 0) {
         when(vSegmentUnit.io.sbuffer.valid) {
           sbuffer.io.diffStore.diffInfo(0) := vSegmentUnit.io.vecDifftestInfo.bits
-          sbuffer.io.diffStore.pmaStore(0).valid := vSegmentUnit.io.sbuffer.fire
-          sbuffer.io.diffStore.pmaStore(0).bits := vSegmentUnit.io.sbuffer.bits
+          sbuffer.io.diffStore.pmaStore(0) := vSegmentUnit.io.diffPmaStore.get
         }.otherwise{
-          sbuffer.io.diffStore.diffInfo(0) := lsq.io.diffStore.diffInfo(0)
-          sbuffer.io.diffStore.pmaStore(0) := lsq.io.diffStore.pmaStore(0)
+          sbuffer.io.diffStore.diffInfo(0) := lsq.io.diffStore.get.diffInfo(0)
+          sbuffer.io.diffStore.pmaStore(0) := lsq.io.diffStore.get.pmaStore(0)
         }
       }else{
-        sbuffer.io.diffStore.diffInfo(i) := lsq.io.diffStore.diffInfo(i)
-        sbuffer.io.diffStore.pmaStore(i) := lsq.io.diffStore.pmaStore(i)
+        sbuffer.io.diffStore.diffInfo(i) := lsq.io.diffStore.get.diffInfo(i)
+        sbuffer.io.diffStore.pmaStore(i) := lsq.io.diffStore.get.pmaStore(i)
       }
-      sbuffer.io.diffStore.ncStore := lsq.io.diffStore.ncStore
+      sbuffer.io.diffStore.ncStore := lsq.io.diffStore.get.ncStore
       io.mem_to_ooo.storeDebugInfo(i).robidx := sbuffer.io.diffStore.diffInfo(i).uop.robIdx
       sbuffer.io.diffStore.diffInfo(i).uop.pc := io.mem_to_ooo.storeDebugInfo(i).pc
     }
@@ -1333,7 +1252,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vsSplit(i).io.vstdMisalign.get.storePipeEmpty := !storeUnits.map(_.io.s0_s1_s2_valid).reduce(_||_)
 
   }
-  (0 until VlduCnt).foreach{i =>
+  (0 until VlduCnt).foreach { i =>
     vlSplit(i).io.redirect <> redirect
     vlSplit(i).io.in <> issueVldu(i)
     vlSplit(i).io.in.valid := issueVldu(i).valid &&
@@ -1341,31 +1260,30 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vlSplit(i).io.toMergeBuffer <> vlMergeBuffer.io.fromSplit(i)
     vlSplit(i).io.threshold.get.valid := vlMergeBuffer.io.toSplit.get.threshold
     vlSplit(i).io.threshold.get.bits  := lsq.io.lqDeqPtr
-    NewPipelineConnect(
-      vlSplit(i).io.out, loadUnits(i).io.vecldin, loadUnits(i).io.vecldin.fire,
-      Mux(vlSplit(i).io.out.fire, vlSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect), loadUnits(i).io.vecldin.bits.uop.robIdx.needFlush(io.redirect)),
-      Option("VlSplitConnectLdu")
-    )
 
     //Subsequent instrction will be blocked
     vfofBuffer.io.in(i).valid := issueVldu(i).valid
     vfofBuffer.io.in(i).bits  := issueVldu(i).bits
   }
-  (0 until LduCnt).foreach{i=>
-    loadUnits(i).io.vecldout.ready         := vlMergeBuffer.io.fromPipeline(i).ready
-    loadMisalignBuffer.io.vecWriteBack.ready := true.B
-
-    if (i == MisalignWBPort) {
-      when(loadUnits(i).io.vecldout.valid) {
-        vlMergeBuffer.io.fromPipeline(i).valid := loadUnits(i).io.vecldout.valid
-        vlMergeBuffer.io.fromPipeline(i).bits  := loadUnits(i).io.vecldout.bits
-      } .otherwise {
-        vlMergeBuffer.io.fromPipeline(i).valid   := loadMisalignBuffer.io.vecWriteBack.valid
-        vlMergeBuffer.io.fromPipeline(i).bits    := loadMisalignBuffer.io.vecWriteBack.bits
-      }
+  (0 until LduCnt).foreach { i=>
+    vlMergeBuffer.io.fromPipeline(i) <> newLoadUnits(i).io.vecldout
+    if (i < VlduCnt) {
+      val vlSplitOut = Wire(DecoupledIO(new VectorLoadIn()))
+      vlSplitOut.valid := vlSplit(i).io.out.valid
+      vlSplitOut.bits := vlSplit(i).io.out.bits.toVectorLoadIn()
+      vlSplit(i).io.out.ready := vlSplitOut.ready
+      NewPipelineConnect(
+        vlSplitOut, newLoadUnits(i).io.vecldin, newLoadUnits(i).io.vecldin.fire,
+        Mux(
+          vlSplitOut.fire,
+          vlSplitOut.bits.uop.robIdx.needFlush(io.redirect),
+          newLoadUnits(i).io.vecldin.bits.uop.robIdx.needFlush(io.redirect)
+        ),
+        Option("VlSplitConnectLdu")
+      )
     } else {
-      vlMergeBuffer.io.fromPipeline(i).valid := loadUnits(i).io.vecldout.valid
-      vlMergeBuffer.io.fromPipeline(i).bits  := loadUnits(i).io.vecldout.bits
+      newLoadUnits(i).io.vecldin.valid := false.B
+      newLoadUnits(i).io.vecldin.bits := DontCare
     }
   }
 
@@ -1384,7 +1302,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   }
 
   vlMergeBuffer.io.redirect <> redirect
-  vsMergeBuffer.map(_.io.redirect <> redirect)
+  vsMergeBuffer.foreach(_.io.redirect <> redirect)
   (0 until VlduCnt).foreach{i=>
     vlMergeBuffer.io.toLsq(i) <> lsq.io.ldvecFeedback(i)
   }
@@ -1430,8 +1348,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
       )
     }
 
-    vfofBuffer.io.mergeUopWriteback(i).valid := vlMergeBuffer.io.toLsq(i).valid
-    vfofBuffer.io.mergeUopWriteback(i).bits  := vlMergeBuffer.io.toLsq(i).bits
+    vfofBuffer.io.mergeUopWriteback(i).valid := vlMergeBuffer.io.exceptionInfo(i).valid
+    vfofBuffer.io.mergeUopWriteback(i).bits  := vlMergeBuffer.io.exceptionInfo(i).bits
   }
 
 
@@ -1470,7 +1388,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
 
     state := s_atomics(i)
   }
-  when (atomicsUnit.io.out.valid) {
+  when (atomicsUnit.io.out.toRob.valid) {
     state := s_normal
   }
 
@@ -1496,16 +1414,14 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   // for atomicsUnit, it uses loadUnit(0)'s TLB port
 
   when (state =/= s_normal) {
-    // use store wb port instead of load
-    loadUnits(0).io.ldout.ready := false.B
     // use load_0's TLB
     atomicsUnit.io.dtlb <> amoTlb
 
     // hw prefetch should be disabled while executing atomic insts
-    loadUnits.map(i => i.io.prefetch_req.valid := false.B)
+    newLoadUnits.foreach(_.io.prefetchReq.valid := false.B)
 
     // make sure there's no in-flight uops in load unit
-    assert(!loadUnits(0).io.ldout.valid)
+    assert(!newLoadUnits(0).io.ldout.toRob.valid)
   }
 
   lsq.io.flushSbuffer.empty := sbuffer.io.sbempty
@@ -1517,167 +1433,23 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     }
   }
 
-  lsq.io.exceptionAddr.isStore := io.ooo_to_mem.isStoreException
-  // Exception address is used several cycles after flush.
-  // We delay it by 10 cycles to ensure its flush safety.
-  val atomicsException = RegInit(false.B)
-  when (DelayN(redirect.valid, 10) && atomicsException) {
-    atomicsException := false.B
-  }.elsewhen (atomicsUnit.io.exceptionInfo.valid) {
-    atomicsException := true.B
-  }
+  exceptionInfoGen.io.redirect          <> redirect
+  exceptionInfoGen.io.fromCsr           <> tlbcsr
+  io.mem_to_ooo.lsqio.vaddr             := RegNext(exceptionInfoGen.io.exceptionInfo.vaddr)
+  io.mem_to_ooo.lsqio.vl                := RegNext(exceptionInfoGen.io.exceptionInfo.vl)
+  io.mem_to_ooo.lsqio.vstart            := RegNext(exceptionInfoGen.io.exceptionInfo.vstart)
+  io.mem_to_ooo.lsqio.isForVSnonLeafPTE := RegNext(exceptionInfoGen.io.exceptionInfo.isForVSnonLeafPTE)
+  io.mem_to_ooo.lsqio.gpaddr            := RegNext(exceptionInfoGen.io.exceptionInfo.gpaddr)
 
-  val misalignBufExceptionOverwrite = loadMisalignBuffer.io.overwriteExpBuf.valid || storeMisalignBuffer.io.overwriteExpBuf.valid
-  val misalignBufExceptionVaddr = Mux(loadMisalignBuffer.io.overwriteExpBuf.valid,
-    loadMisalignBuffer.io.overwriteExpBuf.vaddr,
-    storeMisalignBuffer.io.overwriteExpBuf.vaddr
-  )
-  val misalignBufExceptionIsHyper = Mux(loadMisalignBuffer.io.overwriteExpBuf.valid,
-    loadMisalignBuffer.io.overwriteExpBuf.isHyper,
-    storeMisalignBuffer.io.overwriteExpBuf.isHyper
-  )
-  val misalignBufExceptionGpaddr = Mux(loadMisalignBuffer.io.overwriteExpBuf.valid,
-    loadMisalignBuffer.io.overwriteExpBuf.gpaddr,
-    storeMisalignBuffer.io.overwriteExpBuf.gpaddr
-  )
-  val misalignBufExceptionIsForVSnonLeafPTE = Mux(loadMisalignBuffer.io.overwriteExpBuf.valid,
-    loadMisalignBuffer.io.overwriteExpBuf.isForVSnonLeafPTE,
-    storeMisalignBuffer.io.overwriteExpBuf.isForVSnonLeafPTE
-  )
-
-  val vSegmentException = RegInit(false.B)
-  when (DelayN(redirect.valid, 10) && vSegmentException) {
-    vSegmentException := false.B
-  }.elsewhen (vSegmentUnit.io.exceptionInfo.valid) {
-    vSegmentException := true.B
-  }
-  val atomicsExceptionAddress = RegEnable(atomicsUnit.io.exceptionInfo.bits.vaddr, atomicsUnit.io.exceptionInfo.valid)
-  val vSegmentExceptionVstart = RegEnable(vSegmentUnit.io.exceptionInfo.bits.vstart, vSegmentUnit.io.exceptionInfo.valid)
-  val vSegmentExceptionVl     = RegEnable(vSegmentUnit.io.exceptionInfo.bits.vl, vSegmentUnit.io.exceptionInfo.valid)
-  val vSegmentExceptionAddress = RegEnable(vSegmentUnit.io.exceptionInfo.bits.vaddr, vSegmentUnit.io.exceptionInfo.valid)
-  val atomicsExceptionGPAddress = RegEnable(atomicsUnit.io.exceptionInfo.bits.gpaddr, atomicsUnit.io.exceptionInfo.valid)
-  val vSegmentExceptionGPAddress = RegEnable(vSegmentUnit.io.exceptionInfo.bits.gpaddr, vSegmentUnit.io.exceptionInfo.valid)
-  val atomicsExceptionIsForVSnonLeafPTE = RegEnable(atomicsUnit.io.exceptionInfo.bits.isForVSnonLeafPTE, atomicsUnit.io.exceptionInfo.valid)
-  val vSegmentExceptionIsForVSnonLeafPTE = RegEnable(vSegmentUnit.io.exceptionInfo.bits.isForVSnonLeafPTE, vSegmentUnit.io.exceptionInfo.valid)
-
-  val exceptionVaddr = Mux(
-    atomicsException,
-    atomicsExceptionAddress,
-    Mux(misalignBufExceptionOverwrite,
-      misalignBufExceptionVaddr,
-      Mux(vSegmentException,
-        vSegmentExceptionAddress,
-        lsq.io.exceptionAddr.vaddr
-      )
-    )
-  )
-  // whether vaddr need ext or is hyper inst:
-  // VaNeedExt: atomicsException -> false; misalignBufExceptionOverwrite -> true; vSegmentException -> false
-  // IsHyper: atomicsException -> false; vSegmentException -> false
-  val exceptionVaNeedExt = !atomicsException &&
-    (misalignBufExceptionOverwrite ||
-      (!vSegmentException && lsq.io.exceptionAddr.vaNeedExt))
-  val exceptionIsHyper = !atomicsException &&
-    (misalignBufExceptionOverwrite && misalignBufExceptionIsHyper ||
-      (!vSegmentException && lsq.io.exceptionAddr.isHyper && !misalignBufExceptionOverwrite))
-
-  def GenExceptionVa(
-    mode: UInt, isVirt: Bool, vaNeedExt: Bool,
-    satp: TlbSatpBundle, vsatp: TlbSatpBundle, hgatp: TlbHgatpBundle,
-    vaddr: UInt
-  ) = {
-    require(VAddrBits >= 50)
-
-    val satpNone = satp.mode === 0.U
-    val satpSv39 = satp.mode === 8.U
-    val satpSv48 = satp.mode === 9.U
-
-    val vsatpNone = vsatp.mode === 0.U
-    val vsatpSv39 = vsatp.mode === 8.U
-    val vsatpSv48 = vsatp.mode === 9.U
-
-    val hgatpNone = hgatp.mode === 0.U
-    val hgatpSv39x4 = hgatp.mode === 8.U
-    val hgatpSv48x4 = hgatp.mode === 9.U
-
-    // For !isVirt, mode check is necessary, as we don't want virtual memory in M-mode.
-    // For isVirt, mode check is unnecessary, as virt won't be 1 in M-mode.
-    // Also, isVirt includes Hyper Insts, which don't care mode either.
-
-    val useBareAddr =
-      (isVirt && vsatpNone && hgatpNone) ||
-      (!isVirt && (mode === CSRConst.ModeM)) ||
-      (!isVirt && (mode =/= CSRConst.ModeM) && satpNone)
-    val useSv39Addr =
-      (isVirt && vsatpSv39) ||
-      (!isVirt && (mode =/= CSRConst.ModeM) && satpSv39)
-    val useSv48Addr =
-      (isVirt && vsatpSv48) ||
-      (!isVirt && (mode =/= CSRConst.ModeM) && satpSv48)
-    val useSv39x4Addr = isVirt && vsatpNone && hgatpSv39x4
-    val useSv48x4Addr = isVirt && vsatpNone && hgatpSv48x4
-
-    val bareAddr   = ZeroExt(vaddr(PAddrBits - 1, 0), XLEN)
-    val sv39Addr   = SignExt(vaddr.take(39), XLEN)
-    val sv39x4Addr = ZeroExt(vaddr.take(39 + 2), XLEN)
-    val sv48Addr   = SignExt(vaddr.take(48), XLEN)
-    val sv48x4Addr = ZeroExt(vaddr.take(48 + 2), XLEN)
-
-    val ExceptionVa = Wire(UInt(XLEN.W))
-    when (vaNeedExt) {
-      ExceptionVa := Mux1H(Seq(
-        (useBareAddr)   -> bareAddr,
-        (useSv39Addr)   -> sv39Addr,
-        (useSv48Addr)   -> sv48Addr,
-        (useSv39x4Addr) -> sv39x4Addr,
-        (useSv48x4Addr) -> sv48x4Addr,
-      ))
-    } .otherwise {
-      ExceptionVa := vaddr
-    }
+  val exceptionInfo = newLoadUnits.map(_.io.exceptionInfo) ++ storeUnits.map(_.io.exceptionInfo) ++
+    vlMergeBuffer.io.exceptionInfo ++ vsMergeBuffer.map(_.io.exceptionInfo.head) ++
+    Seq(lsq.io.stExceptionInfo) ++ Seq(lsq.io.ldExceptionInfo) ++
+    Seq(vSegmentUnit.io.exceptionInfo) ++ Seq(atomicsUnit.io.exceptionInfo)
 
-    ExceptionVa
+  exceptionInfoGen.io.req.zip(exceptionInfo).map{case (sink, source) =>
+    sink := source
   }
 
-  io.mem_to_ooo.lsqio.vaddr := RegNext(
-    GenExceptionVa(tlbcsr.priv.dmode, tlbcsr.priv.virt || exceptionIsHyper, exceptionVaNeedExt,
-    tlbcsr.satp, tlbcsr.vsatp, tlbcsr.hgatp, exceptionVaddr)
-  )
-
-  // vsegment instruction is executed atomic, which mean atomicsException and vSegmentException should not raise at the same time.
-  XSError(atomicsException && vSegmentException, "atomicsException and vSegmentException raise at the same time!")
-  io.mem_to_ooo.lsqio.vstart := RegNext(Mux(vSegmentException,
-                                            vSegmentExceptionVstart,
-                                            lsq.io.exceptionAddr.vstart)
-  )
-  io.mem_to_ooo.lsqio.vl     := RegNext(Mux(vSegmentException,
-                                            vSegmentExceptionVl,
-                                            lsq.io.exceptionAddr.vl)
-  )
-
-  XSError(atomicsException && atomicsUnit.io.in.valid, "new instruction before exception triggers\n")
-  io.mem_to_ooo.lsqio.gpaddr := RegNext(Mux(
-    atomicsException,
-    atomicsExceptionGPAddress,
-    Mux(misalignBufExceptionOverwrite,
-      misalignBufExceptionGpaddr,
-      Mux(vSegmentException,
-        vSegmentExceptionGPAddress,
-        lsq.io.exceptionAddr.gpaddr
-      )
-    )
-  ))
-  io.mem_to_ooo.lsqio.isForVSnonLeafPTE := RegNext(Mux(
-    atomicsException,
-    atomicsExceptionIsForVSnonLeafPTE,
-    Mux(misalignBufExceptionOverwrite,
-      misalignBufExceptionIsForVSnonLeafPTE,
-      Mux(vSegmentException,
-        vSegmentExceptionIsForVSnonLeafPTE,
-        lsq.io.exceptionAddr.isForVSnonLeafPTE
-      )
-    )
-  ))
   io.mem_to_ooo.topToBackendBypass match { case x =>
     x.hartId            := io.hartId
     x.l2FlushDone       := RegNext(io.l2_flush_done)
@@ -1825,7 +1597,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   pfevent.io.distribute_csr := csrCtrl.distribute_csr
   val csrevents = pfevent.io.hpmevent.slice(16,24)
 
-  val perfFromUnits = (loadUnits ++ Seq(sbuffer, lsq, dcache)).flatMap(_.getPerfEvents)
+  val perfFromUnits = (newLoadUnits ++ Seq(sbuffer, lsq, dcache)).flatMap(_.getPerfEvents)
   val perfFromTLB = perfEventsDTLBld ++ perfEventsDTLBst
   val perfFromPTW = perfEventsPTW.map(x => ("PTW_" + x._1, x._2))
   val perfBlock     = Seq(("ldDeqCount", ldDeqCount),
diff --git a/src/main/scala/xiangshan/mem/MemCommon.scala b/src/main/scala/xiangshan/mem/MemCommon.scala
index c6500b32ce4..1b5474b9925 100644
--- a/src/main/scala/xiangshan/mem/MemCommon.scala
+++ b/src/main/scala/xiangshan/mem/MemCommon.scala
@@ -129,3 +129,94 @@ object AddPipelineReg {
     pipelineReg.io.isFlush := isFlush
   }
 }
+
+object MemorySize {
+
+  sealed abstract class Size (uint: UInt) {
+    def U: UInt = this.uint
+    def ByteOffset: UInt
+  }
+
+  object Size {
+    def width:           Int = 3
+    def ByteOffsetWidth: Int = 5
+    val all:      List[Size] = List(B, H, W, D, Q)
+  }
+
+  /*
+  * ByteOffset is for generate ByteEnd, the range of request is [BytesStart, ByteEnd]
+  */
+  case object B extends Size("b000".U(Size.width.W)){
+    def ByteOffset = 0.U(Size.ByteOffsetWidth.W)
+  }
+  case object H extends Size("b001".U(Size.width.W)){
+    def ByteOffset = 1.U(Size.ByteOffsetWidth.W)
+  }
+  case object W extends Size("b010".U(Size.width.W)){
+    def ByteOffset = 3.U(Size.ByteOffsetWidth.W)
+  }
+  case object D extends Size("b011".U(Size.width.W)){
+    def ByteOffset = 7.U(Size.ByteOffsetWidth.W)
+  }
+  case object Q extends Size("b100".U(Size.width.W)){
+    def ByteOffset = 15.U(Size.ByteOffsetWidth.W)
+  }
+
+  /*
+  * According to memorySize to select byteOffset
+  */
+  def ByteOffset (size: UInt): UInt = {
+    require(size.getWidth == Size.width)
+    LookupTree(size, Size.all.map(s => s.U -> s.ByteOffset))
+  }
+
+  // The range of request is [BytesStart, ByteEnd]
+  def CalculateSelectMask(start: UInt, end: UInt): UInt = {
+    end - start + 1.U
+  }
+
+  def sizeIs(op: UInt, sz: Size): Bool = {
+    op === sz.U
+  }
+}
+
+class SelectOldest[T <: Data](gen: T, numIn: Int, f: (T, T) => Bool) extends Module {
+  val io = IO(new Bundle{
+    val in = Vec(numIn, Flipped(ValidIO(gen.cloneType)))
+    val out = ValidIO(gen.cloneType)
+  })
+  def findOlder: (T, T) => Bool = f
+
+  val validSeq = io.in.map(_.valid)
+  val bitSeq = io.in.map(_.bits)
+  def selectPartialOldest[T <: Data](
+    valid: Seq[Bool], bits: Seq[T], isOlderFu: (T, T) => Bool
+  ): (Seq[Bool], Seq[T]) = {
+    assert(valid.length == bits.length)
+    if (valid.length == 0 || valid.length == 1) {
+      (valid, bits)
+    } else if (valid.length == 2) {
+      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
+      for (i <- res.indices) {
+        res(i).valid := valid(i)
+        res(i).bits := bits(i)
+      }
+      val oldest = Mux(
+        valid(0) && valid(1),
+        Mux(isOlderFu(bits(0), bits(1)), res(0), res(1)),
+        Mux(valid(0) && !valid(1), res(0), res(1))
+      )
+      (Seq(oldest.valid), Seq(oldest.bits))
+    } else {
+      val left = selectPartialOldest(valid.take(valid.length / 2), bits.take(bits.length / 2), isOlderFu)
+      val right = selectPartialOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)), isOlderFu)
+      selectPartialOldest(left._1 ++ right._1, left._2 ++ right._2, isOlderFu)
+    }
+  }
+
+  val oldest = selectPartialOldest(validSeq, bitSeq, findOlder)
+
+  io.out.valid := oldest._1.head
+  io.out.bits := oldest._2.head
+
+}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala b/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala
new file mode 100644
index 00000000000..9c2dc33d425
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/lsqueue/ExceptionInfoGen.scala
@@ -0,0 +1,202 @@
+/***************************************************************************************
+ * Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+ * Copyright (c) 2020-2021 Peng Cheng Laboratory
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          https://license.coscl.org.cn/MulanPSL2
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package xiangshan.mem
+
+import chisel3._
+import chisel3.util._
+import difftest._
+import difftest.common.DifftestMem
+import org.chipsalliance.cde.config.Parameters
+import top.ArgParser
+import utility._
+import xiangshan.ExceptionNO.hardwareError
+import xiangshan._
+import xiangshan.backend.Bundles.{MemExuOutput, UopIdx, connectSamePort}
+import xiangshan.backend.datapath.NewPipelineConnect
+import xiangshan.backend.fu.util.CSRConst
+import xiangshan.backend.rob.{RobExceptionInfo, RobPtr}
+import xiangshan.cache.{DCacheWordReqWithVaddrAndPfFlag, MemoryOpConstants, UncacheWordIO}
+import xiangshan.mem.Bundles.LqWriteBundle
+
+class MemExceptionInfo(implicit p: Parameters) extends XSBundle {
+  val robIdx            = new RobPtr
+  val uopIdx            = UopIdx()
+  val exceptionVec      = ExceptionVec()
+  def hasException      = exceptionVec.asUInt.orR
+
+  val vaddr             = UInt(XLEN.W)
+  val vaNeedExt         = Bool()
+  val isHyper           = Bool()
+  val vstart            = UInt((log2Up(VLEN) + 1).W)
+  val vl                = UInt((log2Up(VLEN) + 1).W)
+  val gpaddr            = UInt(GPAddrBits.W)
+  val isForVSnonLeafPTE = Bool()
+}
+
+class ExceptionOut(implicit p: Parameters) extends XSBundle {
+  val vaddr             = Output(UInt(XLEN.W))
+  val vstart            = Output(UInt((log2Up(VLEN) + 1).W))
+  val vl                = Output(UInt((log2Up(VLEN) + 1).W))
+  val gpaddr            = Output(UInt(GPAddrBits.W))
+  val isForVSnonLeafPTE = Output(Bool())
+}
+
+class ExceptionInfoGen(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper{
+  // loadUnit, storeUnit, VLoad, VStore, storeQueue Uncache, LoadQueue Uncache, VSegmentUnit, Atomic
+  private val enqPortNum = StorePipelineWidth + LoadPipelineWidth + VecLoadPipelineWidth + VecStorePipelineWidth + 1 + 1 + 1 + 1
+  val io = IO(new Bundle{
+    val redirect      = Flipped(ValidIO(new Redirect))
+    val fromCsr       = Input(new TlbCsrBundle)
+    val req           = Vec(enqPortNum, Flipped(ValidIO(new MemExceptionInfo)))
+    val exceptionInfo = new ExceptionOut // don't have valid
+  })
+  private def isOlder(left: MemExceptionInfo, right: MemExceptionInfo): Bool = {
+    isBefore(left.robIdx, right.robIdx) || (left.robIdx === right.robIdx && left.uopIdx < right.uopIdx)
+  }
+  val selectOldestModule = Module(new SelectOldest(new MemExceptionInfo, enqPortNum, isOlder))
+
+  private def GenExceptionVa(
+                                mode: UInt, isVirt: Bool, vaNeedExt: Bool,
+                                satp: TlbSatpBundle, vsatp: TlbSatpBundle, hgatp: TlbHgatpBundle,
+                                vaddr: UInt
+                              ) = {
+    require(VAddrBits >= 50)
+
+    val satpNone = satp.mode === 0.U
+    val satpSv39 = satp.mode === 8.U
+    val satpSv48 = satp.mode === 9.U
+
+    val vsatpNone = vsatp.mode === 0.U
+    val vsatpSv39 = vsatp.mode === 8.U
+    val vsatpSv48 = vsatp.mode === 9.U
+
+    val hgatpNone = hgatp.mode === 0.U
+    val hgatpSv39x4 = hgatp.mode === 8.U
+    val hgatpSv48x4 = hgatp.mode === 9.U
+
+    // For !isVirt, mode check is necessary, as we don't want virtual memory in M-mode.
+    // For isVirt, mode check is unnecessary, as virt won't be 1 in M-mode.
+    // Also, isVirt includes Hyper Insts, which don't care mode either.
+
+    val useBareAddr =
+      (isVirt && vsatpNone && hgatpNone) ||
+        (!isVirt && (mode === CSRConst.ModeM)) ||
+        (!isVirt && (mode =/= CSRConst.ModeM) && satpNone)
+    val useSv39Addr =
+      (isVirt && vsatpSv39) ||
+        (!isVirt && (mode =/= CSRConst.ModeM) && satpSv39)
+    val useSv48Addr =
+      (isVirt && vsatpSv48) ||
+        (!isVirt && (mode =/= CSRConst.ModeM) && satpSv48)
+    val useSv39x4Addr = isVirt && vsatpNone && hgatpSv39x4
+    val useSv48x4Addr = isVirt && vsatpNone && hgatpSv48x4
+
+    val bareAddr   = ZeroExt(vaddr(PAddrBits - 1, 0), XLEN)
+    val sv39Addr   = SignExt(vaddr.take(39), XLEN)
+    val sv39x4Addr = ZeroExt(vaddr.take(39 + 2), XLEN)
+    val sv48Addr   = SignExt(vaddr.take(48), XLEN)
+    val sv48x4Addr = ZeroExt(vaddr.take(48 + 2), XLEN)
+
+    val ExceptionVa = Wire(UInt(XLEN.W))
+    when (vaNeedExt) {
+      ExceptionVa := Mux1H(Seq(
+        (useBareAddr)   -> bareAddr,
+        (useSv39Addr)   -> sv39Addr,
+        (useSv48Addr)   -> sv48Addr,
+        (useSv39x4Addr) -> sv39x4Addr,
+        (useSv48x4Addr) -> sv48x4Addr,
+      ))
+    } .otherwise {
+      ExceptionVa := vaddr
+    }
+
+    ExceptionVa
+  }
+
+  private val currentValid = RegInit(false.B)
+  private val currentExcp  = Reg(new MemExceptionInfo)
+
+  private val tlbcsr = io.fromCsr
+
+  /*===================================================== s0 stage ===================================================*/
+  private val s0Valid = io.req.map{case port =>
+    port.valid && !port.bits.robIdx.needFlush(io.redirect)
+  }
+  /*===================================================== s1 stage ===================================================*/
+  // select an oldest enq exception, compare the current exception.
+  private val s1Valid = s0Valid.map(x => RegNext(x))
+  private val s1Bits  = io.req.map(x => RegNext(x.bits)) // for timing, don't use RegEnable
+
+  // have exception and don't need to be flushed.
+  private val selectValid = s1Valid.zip(s1Bits).map{case (v, p) =>
+    v && p.hasException && !p.robIdx.needFlush(io.redirect)
+  } // for timing, generate selectValid here
+
+  selectOldestModule.io.in.zipWithIndex.map{case (sink, i) =>
+    sink.valid := selectValid(i)
+    sink.bits := s1Bits(i)
+  }
+  private val oldestBits = selectOldestModule.io.out.bits
+  private val s1OutValid = selectOldestModule.io.out.valid
+
+  when(currentValid) {
+    when(s1OutValid) {
+      when(currentExcp.robIdx > oldestBits.robIdx || oldestBits.robIdx === currentExcp.robIdx && currentExcp.uopIdx > oldestBits.uopIdx) {
+        currentExcp := oldestBits
+      }
+    }
+  }.otherwise {
+    currentExcp  := oldestBits
+  }
+
+  when(!currentValid && s1OutValid) { // TODO: need valid ? maby for debug.
+    currentValid := true.B
+  }.elsewhen(currentValid && currentExcp.robIdx.needFlush(io.redirect) && !s1OutValid) {
+    currentValid := false.B
+  }
+
+  // whether vaddr need ext or is hyper inst:
+  // VaNeedExt: atomicsException -> false; misalignBufExceptionOverwrite -> true; vSegmentException -> false
+  // IsHyper: atomicsException -> false; vSegmentException -> false
+
+  private val exceptionVa = GenExceptionVa(tlbcsr.priv.dmode, tlbcsr.priv.virt || currentExcp.isHyper, currentExcp.vaNeedExt,
+    tlbcsr.satp, tlbcsr.vsatp, tlbcsr.hgatp, currentExcp.vaddr)
+
+  io.exceptionInfo.vstart            := currentExcp.vstart
+  io.exceptionInfo.vl                := currentExcp.vl
+  io.exceptionInfo.vaddr             := exceptionVa
+  io.exceptionInfo.gpaddr            := currentExcp.gpaddr
+  io.exceptionInfo.isForVSnonLeafPTE := currentExcp.isForVSnonLeafPTE
+
+}
+
+import top.Generator
+object ExceptionBufferMain extends App {
+  val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(
+    args :+ "--disable-always-basic-diff" :+ "--fpga-platform" :+ "--target" :+ "verilog")
+
+  val defaultConfig = config.alterPartial({
+    // Get XSCoreParams and pass it to the "small module"
+    case XSCoreParamsKey => config(XSTileKey).head
+  })
+
+  Generator.execute(
+    firrtlOpts :+ "--full-stacktrace" :+ "--target-dir" :+ "exceptionBuffer" :+ "--throw-on-first-error",
+    new ExceptionInfoGen()(defaultConfig),
+    firtoolOpts
+  )
+
+  println("done")
+}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala
new file mode 100644
index 00000000000..445759d3c57
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala
@@ -0,0 +1,230 @@
+/***************************************************************************************
+ * Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+ * Copyright (c) 2020-2021 Peng Cheng Laboratory
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          https://license.coscl.org.cn/MulanPSL2
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package xiangshan.mem
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.InstSeqNum
+import xiangshan._
+import xiangshan.backend.Bundles.{DynInst, ExuOutput, MemExuOutput, NewExuOutput, UopIdx}
+import xiangshan.backend.exu.ExeUnitParams
+import xiangshan.backend.fu.FuType
+import xiangshan.backend.fu.vector.Bundles.NumLsElem
+import xiangshan.backend.rob.RobPtr
+import xiangshan.cache.{CMOReq, CMOResp, DCacheWordReqWithVaddrAndPfFlag, UncacheWordIO}
+import xiangshan.frontend.ftq.FtqPtr
+import xiangshan.mem.Bundles.{SQForward, StoreMaskBundle}
+
+class StoreQueueEnqIO(implicit p: Parameters) extends MemBlockBundle {
+  // Bundle define
+
+  // from Dispatch
+  class ReqUopInfo(implicit p: Parameters) extends MemBlockBundle {
+    val robIdx          = new RobPtr
+    val numLsElem       = NumLsElem()
+    val sqIdx           = new SqPtr
+    val lastUop         = Bool()
+    val fuType          = FuType()
+    val fuOpType        = FuOpType()
+    val uopIdx          = UopIdx()
+    // load inst will not be executed until former store (predicted by mdp) addr calcuated
+    val loadWaitBit     = Bool()
+    // If (loadWaitBit && loadWaitStrict), strict load wait is needed
+    // load inst will not be executed until ALL former store addr calcuated
+    val loadWaitStrict  = Bool()
+    val ssid            = UInt(SSIDWidth.W)
+    val storeSetHit     = Bool() // inst has been allocated an store set
+    // debug signal
+    val pc              = Option.when(debugEn)(UInt(VAddrBits.W))
+  }
+  class FromDispatchReq(implicit p: Parameters) extends MemBlockBundle {
+    val needAlloc       = Bool()
+    val uop             = new ReqUopInfo
+    // debug signal
+    val debugUop        = Option.when(debugEn)(new DynInst()) // only for difftest
+  }
+
+  // to Dispatch
+  class ToDispatchResp(implicit p: Parameters) extends MemBlockBundle {
+    val sqIdx           = new SqPtr
+  }
+
+  // IO define
+  val lqCanAccept       = Input(Bool())
+  val canAccept         = Output(Bool())
+  val req               = Vec(LSQEnqWidth, Flipped(ValidIO(new FromDispatchReq)))
+  val resp              = Vec(LSQEnqWidth, new ToDispatchResp)
+}
+
+class UnalignQueueIO(implicit p: Parameters) extends MemBlockBundle {
+  val sqIdx              = new SqPtr
+  val robIdx             = new RobPtr
+  val paddr              = UInt(PAddrBits.W)
+}
+
+class StaUopInfo(implicit p: Parameters) extends MemBlockBundle {
+  val sqIdx           = new SqPtr
+  val fuOpType        = FuOpType()
+  val robIdx          = new RobPtr
+
+  // used in RAW check, for MDP train
+  val ftqPtr          = new FtqPtr
+  val ftqOffset       = UInt(FetchBlockInstOffsetWidth.W)
+
+  // mdp
+  val isFirstIssue    = Bool()
+  val isRVC           = Bool()
+
+  // debug info
+  val pc              = Option.when(debugEn)(UInt(VAddrBits.W))
+  val debugInfo       = Option.when(debugEn)(new PerfDebugInfo)
+  val debug_seqNum    = Option.when(debugEn)(InstSeqNum())
+}
+// TODO: distinguish storeAddrIn and storeAddrInRe
+class StoreAddrIO(implicit p: Parameters) extends MemBlockBundle {
+  val uop             = new StaUopInfo
+  val tlbMiss         = Bool()
+  val cacheMiss       = Bool()
+  val vaddr           = UInt(VAddrBits.W)
+  val paddr           = UInt(PAddrBits.W)
+  val nc              = Bool() // indicate request is none-cacheable.
+  val mmio            = Bool()
+  val mask            = UInt((VLEN/8).W)
+  val size            = UInt(MemorySize.Size.width.W)
+  val memBackTypeMM   = Bool() // 1: main memory, 0: IO.
+  val hasException    = Bool() // indicate request has exception.
+  val af              = Bool() // indicate access fault.
+
+
+  /* only use in cmo.zero
+  * means this write request need to write whole cacheline.
+  * */
+  val wlineflag          = Bool() // store write the whole cache line.
+
+  // misalign
+  val isUnalign           = Bool()
+  val unalignWithin16Byte = Bool()
+
+  // ctrl signal
+  val isLastRequest      = Bool() /* It's last request to write to storeQueue. if is normal request, it will be true,
+                                      if it was unalign splited, first request will be false, second will be true. */
+  val cross4KPage        = Bool() // this unalign request is cross 4KPage
+}
+
+class StoreQueueDataWrite(implicit p: Parameters) extends MemBlockBundle {
+  val fuType             = FuType()
+  val fuOpType           = FuOpType()
+  val data               = UInt(VLEN.W)
+  val sqIdx              = new SqPtr
+  val vecDebug           = Option.when(debugEn)(new VecMissalignedDebugBundle)
+
+}
+
+class StaIO(implicit p: Parameters) extends MemBlockBundle {
+
+  val storeMaskIn      = Vec(StorePipelineWidth, Flipped(ValidIO(new StoreMaskBundle))) // store mask, send to sq
+  val storeAddrIn      = Vec(StorePipelineWidth, Flipped(ValidIO(new StoreAddrIO))) // store addr, data is not included
+  // this bundle will be removed in the feature.
+  val storeAddrInRe    = Vec(StorePipelineWidth, Input(new StoreAddrIO)) // store more mmio and exception
+  // ready indicate unaligned queue reject this unaligned request
+  val unalignQueueReq = Vec(StorePipelineWidth, Flipped(DecoupledIO(new UnalignQueueIO)))
+}
+
+class ToCacheIO(implicit p: Parameters) extends MemBlockBundle {
+  val req             = DecoupledIO(new CMOReq)
+  val resp            = Flipped(DecoupledIO(new CMOResp))
+}
+
+class FromRobIO(implicit p: Parameters) extends XSBundle {
+  val pendingPtr         = new RobPtr
+  val pendingPtrNext     = new RobPtr
+}
+
+class toRobIO(implicit p: Parameters) extends XSBundle {
+  val mmioBusy      = Bool()
+}
+
+class SbufferCtrlIO(implicit p: Parameters) extends XSBundle {
+  class Req(implicit p: Parameters) extends XSBundle {
+    val flush            = Bool() // flush is to empty sbuffer
+    val forceWrite       = Bool() // force write is to evict some sbuffer entries.
+  }
+  class Resp(implicit p: Parameters) extends XSBundle {
+    val empty            = Bool()
+  }
+
+  val req                = Output(new Req)
+  val resp               = Input(new Resp)
+}
+
+class StoreQueueToLoadQueueIO(implicit p: Parameters) extends XSBundle {
+  val stAddrReadySqPtr   = new SqPtr
+  val stAddrReadyVec     = Vec(StoreQueueSize, Bool())
+  val stDataReadySqPtr   = new SqPtr
+  val stDataReadyVec     = Vec(StoreQueueSize, Bool())
+
+  val stIssuePtr         = new SqPtr
+}
+
+class SbufferWriteIO(implicit p : Parameters) extends XSBundle {
+  val req                = Vec(EnsbufferWidth, DecoupledIO(new DCacheWordReqWithVaddrAndPfFlag))
+}
+
+class StoreQueueIO(val param: ExeUnitParams)(implicit p: Parameters) extends MemBlockBundle {
+  // for mulit Core Difftest
+  val hartId             = Input(UInt(hartIdLen.W))
+  val redirect           = Flipped(ValidIO(new Redirect))
+  // from dispatch
+  val enq                = new StoreQueueEnqIO
+  // when VStoreMergeBuffer writeback micro-op, storeQueue need to set `vecMbCommit`
+  val fromVMergeBuffer   = Vec(VecStorePipelineWidth, Flipped(ValidIO(new FeedbackToLsqIO))) //TODO: will be remove in the feature
+  // from std
+  val storeDataIn        = Vec(StorePipelineWidth, Flipped(Valid(new StoreQueueDataWrite))) // store data, send to sq from rs
+  // from storeUnit.
+  val fromStoreUnit      = new StaIO
+  // write committed store to sbuffer
+  val writeToSbuffer     = new SbufferWriteIO
+  // conctrl sbuffer, has two function:
+  // 1. It will evict some entries of sbuffer to dcache; 2. flush sbuffer.
+  val sbufferCtrl        = new SbufferCtrlIO
+  // cmo handle send clean, invalid, flush to dcache.
+  val toDCache           = new ToCacheIO
+  // from loadUnit, forward query.
+  val forward            = Flipped(Vec(LoadPipelineWidth, new SQForward))
+  val fromRob            = Input(new FromRobIO)
+  val toRob              = Output(new toRobIO)
+  // write store request to uncacheBuffer.
+  val toUncacheBuffer    = new UncacheWordIO
+  // to backend , used to writeback uop when request is mmio, cmo.
+  val writeBack          = DecoupledIO(new NewExuOutput(param))
+  // from misalignBuffer, will be remove in the feature
+//  val maControl          = Flipped(new StoreMaBufToSqControlIO)
+  val wfi                = Flipped(new WfiReqBundle)
+  val sqEmpty            = Output(Bool())
+  val sqFull             = Output(Bool())
+  val toLoadQueue        = Output(new StoreQueueToLoadQueueIO)
+  // to exceptionInfoGen, only for mmio/cbo writeback exception gen
+  val exceptionInfo      = ValidIO(new MemExceptionInfo)
+  // to backend, dispatch
+  val sqCancelCnt        = Output(UInt(log2Up(StoreQueueSize + 1).W))
+  val sqDeq              = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
+  // to store unit
+  val sqDeqPtr           = Output(new SqPtr)
+  val sqDeqUopIdx        = Output(UopIdx())
+  val sqDeqRobIdx        = Output(new RobPtr)
+  // for store difftest
+  val diffStore          = Option.when(debugEn)(Flipped(new DiffStoreIO))
+}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQCommon.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQCommon.scala
new file mode 100644
index 00000000000..a2289d06c4f
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQCommon.scala
@@ -0,0 +1,62 @@
+/***************************************************************************************
+ * Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+ * Copyright (c) 2020-2021 Peng Cheng Laboratory
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          https://license.coscl.org.cn/MulanPSL2
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package xiangshan.mem
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.{HasCircularQueuePtrHelper, HasPerfEvents}
+import xiangshan.{DebugOptionsKey, XSBundle, XSModule}
+import xiangshan.cache.HasDCacheParameters
+import xiangshan.mem.HasVLSUParameters
+
+abstract class LSQModule(implicit p: Parameters) extends XSModule
+  with HasDCacheParameters
+  with HasCircularQueuePtrHelper
+  with HasVLSUParameters
+  with HasMemBlockParameters
+
+
+object MemoryType {
+  def cacheable: UInt     = "b00".U
+  def pbmtNc: UInt        = "b01".U
+  def pbmtIo: UInt        = "b10".U
+  def io: UInt            = "b11".U // IO device
+
+  def isPMPIO(in: UInt):  Bool = in(0) && in(1)
+  def isMMIO(in: UInt):   Bool = in(1) // pbmt io and device io
+  def isPbmtIO(in: UInt): Bool = !in(0) && in(1)
+  def isPbmtNC(in: UInt): Bool = in(0) && !in(1)
+  def isCacheable(in: UInt): Bool = !in(0) && !in(1)
+
+  def width: Int = 2
+  def apply() = UInt(width.W)
+}
+
+object CboType {
+  def clean: UInt       = "b00".U
+  def flush: UInt       = "b01".U
+  def inval: UInt       = "b10".U
+  def zero:  UInt       = "b11".U
+
+  def isCboClean(in: UInt): Bool = in === this.clean
+  def isCboFlush(in: UInt): Bool = in === this.flush
+  def isCboInval(in: UInt): Bool = in === this.inval
+  def isCboZero(in: UInt):  Bool = in === this.zero
+
+  def width: Int = 2
+  def apply() = UInt(width.W)
+}
+
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index a321c28d941..cc11368708d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -22,7 +22,7 @@ import chisel3.util._
 import utils._
 import utility._
 import xiangshan._
-import xiangshan.backend.Bundles.{DynInst, ExuOutput, MemExuOutput, UopIdx}
+import xiangshan.backend.Bundles.{DynInst, ExuOutput, MemExuOutput, NewExuOutput, UopIdx, connectSamePort}
 import xiangshan.backend._
 import xiangshan.backend.rob.{RobLsqIO, RobPtr}
 import xiangshan.backend.fu.FuType
@@ -75,38 +75,35 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
     val ldvecFeedback = Vec(VecLoadPipelineWidth, Flipped(ValidIO(new FeedbackToLsqIO)))
     val enq = new LsqEnqIO
     val ldu = new Bundle() {
-        val stld_nuke_query = Vec(LoadPipelineWidth, Flipped(new LoadNukeQueryIO)) // from load_s2
-        val ldld_nuke_query = Vec(LoadPipelineWidth, Flipped(new LoadNukeQueryIO)) // from load_s2
-        val ldin = Vec(LoadPipelineWidth, Flipped(Decoupled(new LqWriteBundle))) // from load_s3
+      val rawNukeQuery = Vec(LoadPipelineWidth, Flipped(new LoadRAWNukeQuery()))
+      val rarNukeQuery = Vec(LoadPipelineWidth, Flipped(new LoadRARNukeQuery()))
+      val ldin = Vec(LoadPipelineWidth, Flipped(Decoupled(new LqWriteBundle))) // from load_s3
     }
     val sta = new Bundle() {
       val storeMaskIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreMaskBundle))) // from store_s0, store mask, send to sq from rs
-      val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new LsPipelineBundle))) // from store_s1
-      val storeAddrInRe = Vec(StorePipelineWidth, Input(new LsPipelineBundle())) // from store_s2
+      val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreAddrIO))) // from store_s1
+      val storeAddrInRe = Vec(StorePipelineWidth, Input(new StoreAddrIO)) // from store_s2
+      // ready indicate unaligned queue reject this unaligned request
+      val unalignQueueReq = Vec(StorePipelineWidth, Flipped(DecoupledIO(new UnalignQueueIO)))
     }
     val std = new Bundle() {
       val storeDataIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreQueueDataWrite))) // from store_s0, store data, send to sq from rs
     }
-    val ldout = Vec(LoadPipelineWidth, DecoupledIO(new MemExuOutput))
-    val ld_raw_data = Vec(LoadPipelineWidth, Output(new LoadDataFromLQBundle))
-    val ncOut = Vec(LoadPipelineWidth, DecoupledIO(new LsPipelineBundle))
-    val replay = Vec(LoadPipelineWidth, Decoupled(new LsPipelineBundle))
-    val sbuffer = Vec(EnsbufferWidth, Decoupled(new DCacheWordReqWithVaddrAndPfFlag))
-    val forward = Vec(LoadPipelineWidth, Flipped(new PipeLoadForwardQueryIO))
+    val bypass = Flipped(Vec(LoadPipelineWidth, new UncacheBypass))
+    val replay = Vec(LoadPipelineWidth, Decoupled(new LoadReplayIO))
+    val sbuffer = new SbufferWriteIO
+    val forward = Flipped(Vec(LoadPipelineWidth, new SQForward))
     val rob = Flipped(new RobLsqIO)
     val nuke_rollback = Vec(StorePipelineWidth, Output(Valid(new Redirect)))
     val nack_rollback = Vec(1, Output(Valid(new Redirect))) // uncahce
     // mdp train io
     val mdpTrain        = ValidIO(new Redirect)
     val release = Flipped(Valid(new Release))
-   // val refill = Flipped(Valid(new Refill))
-    val tl_d_channel  = Input(new DcacheToLduForwardIO)
+    val loadWakeup = Flipped(ValidIO(new DCacheLoadWakeup()))
     val maControl     = Flipped(new StoreMaBufToSqControlIO)
     val uncacheOutstanding = Input(Bool())
     val uncache = new UncacheWordIO
-    val mmioStout = DecoupledIO(new ExuOutput(staParams.head)) // writeback uncached store
-    val cboZeroStout = DecoupledIO(new ExuOutput(staParams.head))
-    val vecmmioStout = DecoupledIO(new ExuOutput(vstuParams.head)) // vec writeback uncached store
+    val mmioStout = DecoupledIO(new NewExuOutput(staParams.head)) // writeback uncached store
     val sqEmpty = Output(Bool())
     val lq_rep_full = Output(Bool())
     val sqFull = Output(Bool())
@@ -122,9 +119,6 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
     val sqCommitPtr = Output(new SqPtr)
     val sqCommitUopIdx = Output(UopIdx())
     val sqCommitRobIdx = Output(new RobPtr)
-    val exceptionAddr = new ExceptionAddrIO
-    val loadMisalignFull = Input(Bool())
-    val misalignAllowSpec = Input(Bool())
     val issuePtrExt = Output(new SqPtr)
     val l2_hint = Input(Valid(new L2ToL1Hint()))
     val tlb_hint = Flipped(new TlbHintIO)
@@ -135,22 +129,25 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
     val lqEmpty = Output(Bool())
     val rarValidCount = Output(UInt())
     val wfi = Flipped(new WfiReqBundle)
+    val stExceptionInfo = ValidIO(new MemExceptionInfo)
+    val ldExceptionInfo = ValidIO(new MemExceptionInfo)
     // top-down
     val debugTopDown = new LoadQueueTopDownIO
     val noUopsIssued = Input(Bool())
 
-    val diffStore = Flipped(new DiffStoreIO)
+    val diffStore = OptionWrapper(debugEn, Flipped(new DiffStoreIO))
   })
 
   val loadQueue = Module(new LoadQueue)
-  val storeQueue = Module(new StoreQueue)
+  val storeQueue = Module(new NewStoreQueue)
 
   storeQueue.io.hartId := io.hartId
-  storeQueue.io.uncacheOutstanding := io.uncacheOutstanding
   storeQueue.io.wfi <> io.wfi
 
   if (backendParams.debugEn){ dontTouch(loadQueue.io.tlbReplayDelayCycleCtrl) }
-
+  // TODO: Don't use for now, will be remove in the feature
+  io.maControl := DontCare
+  io.maControl.toStoreMisalignBuffer.doDeq := true.B // always true, cross page unalign handled by store queue.
   // Todo: imm
   val tlbReplayDelayCycleCtrl = WireInit(VecInit(Seq(14.U(ReSelectLen.W), 0.U(ReSelectLen.W), 125.U(ReSelectLen.W), 0.U(ReSelectLen.W))))
   loadQueue.io.tlbReplayDelayCycleCtrl := tlbReplayDelayCycleCtrl
@@ -165,107 +162,103 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
   storeQueue.io.enq.lqCanAccept := loadQueue.io.enq.canAccept
   io.lqDeqPtr := loadQueue.io.lqDeqPtr
   io.sqDeqPtr := storeQueue.io.sqDeqPtr
-  io.sqCommitRobIdx := storeQueue.io.sqCommitRobIdx
-  io.sqCommitUopIdx := storeQueue.io.sqCommitUopIdx
-  io.sqCommitPtr    := storeQueue.io.sqCommitPtr
+  io.sqCommitRobIdx := storeQueue.io.sqDeqRobIdx
+  io.sqCommitUopIdx := storeQueue.io.sqDeqUopIdx
+  io.sqCommitPtr    := storeQueue.io.sqDeqPtr
   io.rarValidCount := loadQueue.io.rarValidCount
   for (i <- io.enq.req.indices) {
     loadQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(0)
     loadQueue.io.enq.req(i).valid      := io.enq.needAlloc(i)(0) && io.enq.req(i).valid
     loadQueue.io.enq.req(i).bits       := io.enq.req(i).bits
-    loadQueue.io.enq.req(i).bits.sqIdx := storeQueue.io.enq.resp(i)
-
-    storeQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(1)
-    storeQueue.io.enq.req(i).valid      := io.enq.needAlloc(i)(1) && io.enq.req(i).valid
-    storeQueue.io.enq.req(i).bits       := io.enq.req(i).bits
-    storeQueue.io.enq.req(i).bits.lqIdx := loadQueue.io.enq.resp(i)
+    loadQueue.io.enq.req(i).bits.sqIdx := storeQueue.io.enq.resp(i).sqIdx
+
+    storeQueue.io.enq.req(i).bits.needAlloc := io.enq.needAlloc(i)(1)
+    storeQueue.io.enq.req(i).valid          := io.enq.needAlloc(i)(1) && io.enq.req(i).valid
+    connectSamePort(storeQueue.io.enq.req(i).bits.uop, io.enq.req(i).bits)
+    // only enable difftest, it will be used.
+    if(env.EnableDifftest){
+      storeQueue.io.enq.req(i).bits.debugUop.get := io.enq.req(i).bits
+    }
+//    storeQueue.io.enq.req(i).bits.lqIdx := loadQueue.io.enq.resp(i) // TODO: need it ?
 
     io.enq.resp(i).lqIdx := loadQueue.io.enq.resp(i)
-    io.enq.resp(i).sqIdx := storeQueue.io.enq.resp(i)
+    io.enq.resp(i).sqIdx := storeQueue.io.enq.resp(i).sqIdx
   }
 
   // store queue wiring
-  storeQueue.io.brqRedirect <> io.brqRedirect
-  storeQueue.io.vecFeedback   <> io.stvecFeedback
-  storeQueue.io.storeAddrIn <> io.sta.storeAddrIn // from store_s1
-  storeQueue.io.storeAddrInRe <> io.sta.storeAddrInRe // from store_s2
-  storeQueue.io.storeDataIn <> io.std.storeDataIn // from store_s0
-  storeQueue.io.storeMaskIn <> io.sta.storeMaskIn // from store_s0
-  storeQueue.io.sbuffer     <> io.sbuffer
-  storeQueue.io.mmioStout   <> io.mmioStout
-  storeQueue.io.cboZeroStout <> io.cboZeroStout
-  storeQueue.io.vecmmioStout <> io.vecmmioStout
-  storeQueue.io.rob         <> io.rob
-  storeQueue.io.exceptionAddr.isStore := DontCare
-  storeQueue.io.sqCancelCnt  <> io.sqCancelCnt
-  storeQueue.io.sqDeq        <> io.sqDeq
-  storeQueue.io.sqEmpty      <> io.sqEmpty
-  storeQueue.io.sqFull       <> io.sqFull
-  storeQueue.io.forward      <> io.forward // overlap forwardMask & forwardData, DO NOT CHANGE SEQUENCE
-  storeQueue.io.force_write  <> io.force_write
-  storeQueue.io.cmoOpReq     <> io.cmoOpReq
-  storeQueue.io.cmoOpResp    <> io.cmoOpResp
-  storeQueue.io.flushSbuffer <> io.flushSbuffer
-  storeQueue.io.maControl    <> io.maControl
-  io.diffStore := storeQueue.io.diffStore
+  storeQueue.io.redirect                      <> io.brqRedirect
+  storeQueue.io.fromVMergeBuffer              <> io.stvecFeedback
+  storeQueue.io.fromStoreUnit.unalignQueueReq <> io.sta.unalignQueueReq
+  storeQueue.io.fromStoreUnit.storeAddrIn     <> io.sta.storeAddrIn // from store_s1
+  storeQueue.io.fromStoreUnit.storeAddrInRe   <> io.sta.storeAddrInRe // from store_s2
+  storeQueue.io.storeDataIn                   <> io.std.storeDataIn // from store_s0
+  storeQueue.io.fromStoreUnit.storeMaskIn     <> io.sta.storeMaskIn // from store_s0
+  storeQueue.io.writeToSbuffer                <> io.sbuffer
+  storeQueue.io.writeBack                     <> io.mmioStout
+  storeQueue.io.fromRob.pendingPtr            := io.rob.pendingPtr
+  storeQueue.io.fromRob.pendingPtrNext        := io.rob.pendingPtrNext
+  storeQueue.io.exceptionInfo                 <> io.stExceptionInfo
+  storeQueue.io.sqCancelCnt                   <> io.sqCancelCnt
+  storeQueue.io.sqDeq                         <> io.sqDeq
+  storeQueue.io.sqEmpty                       <> io.sqEmpty
+  storeQueue.io.sqFull                        <> io.sqFull
+  storeQueue.io.forward                       <> io.forward // overlap forwardMask & forwardData, DO NOT CHANGE SEQUENCE
+  io.force_write                              := storeQueue.io.sbufferCtrl.req.forceWrite
+  storeQueue.io.toDCache.req                  <> io.cmoOpReq
+  storeQueue.io.toDCache.resp                 <> io.cmoOpResp
+  io.flushSbuffer.valid                       := storeQueue.io.sbufferCtrl.req.flush
+  storeQueue.io.sbufferCtrl.resp.empty        := io.flushSbuffer.empty
+//  storeQueue.io.maControl    <> io.maControl
+  io.diffStore.foreach{ case sink =>
+    storeQueue.io.diffStore.foreach(sink := _)
+  }
 
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
 
   //  load queue wiring
   loadQueue.io.redirect            <> io.brqRedirect
-  loadQueue.io.vecFeedback           <> io.ldvecFeedback
+  loadQueue.io.vecFeedback         <> io.ldvecFeedback
   loadQueue.io.ldu                 <> io.ldu
-  loadQueue.io.ldout               <> io.ldout
-  loadQueue.io.ld_raw_data         <> io.ld_raw_data
-  loadQueue.io.ncOut               <> io.ncOut
-  loadQueue.io.rob                 <> io.rob
+  loadQueue.io.rob.pendingPtr      := io.rob.pendingPtr
+  loadQueue.io.rob.pendingPtrNext  := io.rob.pendingPtrNext
+  loadQueue.io.rob.lcommit         := io.rob.lcommit
+  loadQueue.io.rob.scommit         := io.rob.scommit
+  loadQueue.io.rob.commit          := io.rob.commit
   loadQueue.io.nuke_rollback       <> io.nuke_rollback
   loadQueue.io.nack_rollback       <> io.nack_rollback
   loadQueue.io.replay              <> io.replay
- // loadQueue.io.refill              <> io.refill
-  loadQueue.io.tl_d_channel        <> io.tl_d_channel
+  loadQueue.io.loadWakeup          <> io.loadWakeup
   loadQueue.io.release             <> io.release
-  loadQueue.io.exceptionAddr.isStore := DontCare
-  loadQueue.io.loadMisalignFull    := io.loadMisalignFull
-  loadQueue.io.misalignAllowSpec   := io.misalignAllowSpec
+  loadQueue.io.exceptionInfo       <> io.ldExceptionInfo
   loadQueue.io.lqCancelCnt         <> io.lqCancelCnt
-  loadQueue.io.sq.stAddrReadySqPtr <> storeQueue.io.stAddrReadySqPtr
-  loadQueue.io.sq.stAddrReadyVec   <> storeQueue.io.stAddrReadyVec
-  loadQueue.io.sq.stDataReadySqPtr <> storeQueue.io.stDataReadySqPtr
-  loadQueue.io.sq.stDataReadyVec   <> storeQueue.io.stDataReadyVec
-  loadQueue.io.sq.stIssuePtr       <> storeQueue.io.stIssuePtr
+  loadQueue.io.sq.stAddrReadySqPtr <> storeQueue.io.toLoadQueue.stAddrReadySqPtr
+  loadQueue.io.sq.stAddrReadyVec   <> storeQueue.io.toLoadQueue.stAddrReadyVec
+  loadQueue.io.sq.stDataReadySqPtr <> storeQueue.io.toLoadQueue.stDataReadySqPtr
+  loadQueue.io.sq.stDataReadyVec   <> storeQueue.io.toLoadQueue.stDataReadyVec
+  loadQueue.io.sq.stIssuePtr       <> storeQueue.io.toLoadQueue.stIssuePtr
   loadQueue.io.sq.sqEmpty          <> storeQueue.io.sqEmpty
+  loadQueue.io.sq.sqDeqPtr         <> storeQueue.io.sqDeqPtr
   loadQueue.io.sta.storeAddrIn     <> io.sta.storeAddrIn // store_s1
   loadQueue.io.std.storeDataIn     <> io.std.storeDataIn // store_s0
   loadQueue.io.lqFull              <> io.lqFull
   loadQueue.io.lq_rep_full         <> io.lq_rep_full
+  loadQueue.io.bypass              <> io.bypass
   loadQueue.io.lqDeq               <> io.lqDeq
   loadQueue.io.l2_hint             <> io.l2_hint
   loadQueue.io.tlb_hint            <> io.tlb_hint
   loadQueue.io.lqEmpty             <> io.lqEmpty
   io.mdpTrain                      := loadQueue.io.mdpTrain
 
-  // rob commits for lsq is delayed for two cycles, which causes the delayed update for deqPtr in lq/sq
-  // s0: commit
-  // s1:               exception find
-  // s2:               exception triggered
-  // s3: ptr updated & new address
-  // address will be used at the next cycle after exception is triggered
-  io.exceptionAddr.vaddr := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.vaddr, loadQueue.io.exceptionAddr.vaddr)
-  io.exceptionAddr.vaNeedExt := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.vaNeedExt, loadQueue.io.exceptionAddr.vaNeedExt)
-  io.exceptionAddr.isHyper := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.isHyper, loadQueue.io.exceptionAddr.isHyper)
-  io.exceptionAddr.vstart := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.vstart, loadQueue.io.exceptionAddr.vstart)
-  io.exceptionAddr.vl     := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.vl, loadQueue.io.exceptionAddr.vl)
-  io.exceptionAddr.gpaddr := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.gpaddr, loadQueue.io.exceptionAddr.gpaddr)
-  io.exceptionAddr.isForVSnonLeafPTE:= Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.isForVSnonLeafPTE, loadQueue.io.exceptionAddr.isForVSnonLeafPTE)
-  io.issuePtrExt := storeQueue.io.stAddrReadySqPtr
+  io.issuePtrExt := storeQueue.io.toLoadQueue.stAddrReadySqPtr
 
+  // to rob
+  io.rob.mmioBusy                  := RegNext(storeQueue.io.toRob.mmioBusy || loadQueue.io.rob.mmioBusy)
   // naive uncache arbiter
   val s_idle :: s_load :: s_store :: Nil = Enum(3)
   val pendingstate = RegInit(s_idle)
-  val selectLq = (loadQueue.io.uncache.req.valid && !storeQueue.io.uncache.req.valid) || (
-    loadQueue.io.uncache.req.valid && storeQueue.io.uncache.req.valid &&
-    loadQueue.io.uncache.req.bits.robIdx < storeQueue.io.uncache.req.bits.robIdx
+  val selectLq = (loadQueue.io.uncache.req.valid && !storeQueue.io.toUncacheBuffer.req.valid) || (
+    loadQueue.io.uncache.req.valid && storeQueue.io.toUncacheBuffer.req.valid &&
+    loadQueue.io.uncache.req.bits.robIdx < storeQueue.io.toUncacheBuffer.req.bits.robIdx
   )
 
   switch(pendingstate){
@@ -290,18 +283,18 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
   }
 
   loadQueue.io.uncache := DontCare
-  storeQueue.io.uncache := DontCare
+  storeQueue.io.toUncacheBuffer := DontCare
   loadQueue.io.uncache.req.ready := false.B
-  storeQueue.io.uncache.req.ready := false.B
+  storeQueue.io.toUncacheBuffer.req.ready := false.B
   loadQueue.io.uncache.resp.valid := false.B
   loadQueue.io.uncache.idResp.valid := false.B
-  storeQueue.io.uncache.resp.valid := false.B
-  storeQueue.io.uncache.idResp.valid := false.B
+  storeQueue.io.toUncacheBuffer.resp.valid := false.B
+  storeQueue.io.toUncacheBuffer.idResp.valid := false.B
   when(pendingstate === s_idle){
     when(selectLq){
       io.uncache.req <> loadQueue.io.uncache.req
     }.otherwise{
-      io.uncache.req <> storeQueue.io.uncache.req
+      io.uncache.req <> storeQueue.io.toUncacheBuffer.req
     }
   }.otherwise{
     io.uncache.req.valid := false.B
@@ -310,21 +303,21 @@ class LsqWrapper(implicit p: Parameters) extends XSModule
   when (io.uncache.resp.bits.is2lq) {
     io.uncache.resp <> loadQueue.io.uncache.resp
   } .otherwise {
-    io.uncache.resp <> storeQueue.io.uncache.resp
+    io.uncache.resp <> storeQueue.io.toUncacheBuffer.resp
   }
   when(io.uncache.idResp.bits.is2lq) {
     loadQueue.io.uncache.idResp <> io.uncache.idResp
   }.otherwise {
-    storeQueue.io.uncache.idResp <> io.uncache.idResp
+    storeQueue.io.toUncacheBuffer.idResp <> io.uncache.idResp
   }
 
   loadQueue.io.debugTopDown <> io.debugTopDown
   loadQueue.io.noUopsIssed := io.noUopsIssued
 
-  assert(!(loadQueue.io.uncache.resp.valid && storeQueue.io.uncache.resp.valid))
-  assert(!(loadQueue.io.uncache.idResp.valid && storeQueue.io.uncache.idResp.valid))
+  assert(!(loadQueue.io.uncache.resp.valid && storeQueue.io.toUncacheBuffer.resp.valid))
+  assert(!(loadQueue.io.uncache.idResp.valid && storeQueue.io.toUncacheBuffer.idResp.valid))
   when (!io.uncacheOutstanding) {
-    assert(!((loadQueue.io.uncache.resp.valid || storeQueue.io.uncache.resp.valid) && pendingstate === s_idle))
+    assert(!((loadQueue.io.uncache.resp.valid || storeQueue.io.toUncacheBuffer.resp.valid) && pendingstate === s_idle))
   }
 
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadExceptionBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadExceptionBuffer.scala
deleted file mode 100644
index 48226c0d87c..00000000000
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadExceptionBuffer.scala
+++ /dev/null
@@ -1,105 +0,0 @@
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
-package xiangshan.mem
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import utils._
-import utility._
-import xiangshan._
-import xiangshan.ExceptionNO._
-import xiangshan.frontend.ftq.FtqPtr
-import xiangshan.backend.fu.FuConfig._
-import xiangshan.backend.fu.fpu.FPU
-import xiangshan.backend.rob.RobLsqIO
-import xiangshan.backend.rob.RobPtr
-import xiangshan.mem.Bundles._
-import xiangshan.cache._
-import xiangshan.cache.wpu.ReplayCarry
-
-class LqExceptionBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper {
-  val enqPortNum = LoadPipelineWidth + VecLoadPipelineWidth + 1 // 1 for mmio bus non-data error
-
-  val io = IO(new Bundle() {
-    val redirect      = Flipped(Valid(new Redirect))
-    val req           = Vec(enqPortNum, Flipped(Valid(new LqWriteBundle)))
-    val exceptionAddr = new ExceptionAddrIO
-  })
-
-  val req_valid = RegInit(false.B)
-  val req = Reg(new LqWriteBundle)
-
-  // enqueue
-  // s1:
-  val s1_req = VecInit(io.req.map(_.bits))
-  val s1_valid = VecInit(io.req.map(x => x.valid))
-
-  // s2: delay 1 cycle
-  val s2_req = (0 until enqPortNum).map(i => {
-    RegEnable(s1_req(i), s1_valid(i))})
-  val s2_valid = (0 until enqPortNum).map(i =>
-    RegNext(s1_valid(i)) &&
-    !s2_req(i).uop.robIdx.needFlush(RegNext(io.redirect)) &&
-    !s2_req(i).uop.robIdx.needFlush(io.redirect)
-  )
-  val s2_has_exception = s2_req.map(x => ExceptionNO.selectByFu(x.uop.exceptionVec, LduCfg).asUInt.orR)
-
-  val s2_enqueue = Wire(Vec(enqPortNum, Bool()))
-  for (w <- 0 until enqPortNum) {
-    s2_enqueue(w) := s2_valid(w) && s2_has_exception(w)
-  }
-
-  def selectOldest[T <: LqWriteBundle](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
-    assert(valid.length == bits.length)
-    if (valid.length == 0 || valid.length == 1) {
-      (valid, bits)
-    } else if (valid.length == 2) {
-      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
-      for (i <- res.indices) {
-        res(i).valid := valid(i)
-        res(i).bits := bits(i)
-      }
-      val oldest = Mux(valid(0) && valid(1),
-        Mux(isAfter(bits(0).uop.lqIdx, bits(1).uop.lqIdx), res(1), res(0)),
-        Mux(valid(0) && !valid(1), res(0), res(1)))
-      (Seq(oldest.valid), Seq(oldest.bits))
-    } else {
-      val left = selectOldest(valid.take(valid.length / 2), bits.take(bits.length / 2))
-      val right = selectOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)))
-      selectOldest(left._1 ++ right._1, left._2 ++ right._2)
-    }
-  }
-
-  val reqValid = req_valid && !req.uop.robIdx.needFlush(io.redirect)
-  val reqSel = selectOldest(s2_enqueue :+ reqValid, s2_req :+ req)
-
-  req_valid := reqSel._1(0)
-  req := reqSel._2(0)
-
-  io.exceptionAddr.vaddr  := req.fullva
-  io.exceptionAddr.vaNeedExt := req.vaNeedExt
-  io.exceptionAddr.isHyper := req.isHyper
-  io.exceptionAddr.vstart := req.uop.vpu.vstart
-  io.exceptionAddr.vl     := req.uop.vpu.vl
-  io.exceptionAddr.gpaddr := req.gpaddr
-  io.exceptionAddr.isForVSnonLeafPTE := req.isForVSnonLeafPTE
-
-  XSPerfAccumulate("exception", !RegNext(req_valid) && req_valid)
-
-  // end
-}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
deleted file mode 100644
index 5c1fd565032..00000000000
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ /dev/null
@@ -1,667 +0,0 @@
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
-package xiangshan.mem
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import utils._
-import utility._
-import xiangshan._
-import xiangshan.ExceptionNO._
-import xiangshan.frontend.ftq.FtqPtr
-import xiangshan.backend.exu.ExeUnitParams
-import xiangshan.backend.fu.FuConfig._
-import xiangshan.backend.fu.FuType
-import xiangshan.backend.fu.fpu.FPU
-import xiangshan.backend.rob.RobLsqIO
-import xiangshan.mem.Bundles._
-import xiangshan.backend.rob.RobPtr
-import xiangshan.backend.Bundles.{ExuOutput, DynInst}
-import xiangshan.backend.fu.FuConfig.LduCfg
-import xiangshan.cache.mmu.HasTlbConst
-import xiangshan.cache._
-import xiangshan.cache.wpu.ReplayCarry
-
-class LoadMisalignBuffer(val param: ExeUnitParams)(implicit p: Parameters) extends XSModule
-  with HasCircularQueuePtrHelper
-  with HasLoadHelper
-  with HasTlbConst
-{
-  private val enqPortNum = LoadPipelineWidth
-  private val maxSplitNum = 2
-
-  require(maxSplitNum == 2)
-
-  private val LB = "b00".U(2.W)
-  private val LH = "b01".U(2.W)
-  private val LW = "b10".U(2.W)
-  private val LD = "b11".U(2.W)
-
-  // encode of how many bytes to shift or truncate
-  private val BYTE0 = "b000".U(3.W)
-  private val BYTE1 = "b001".U(3.W)
-  private val BYTE2 = "b010".U(3.W)
-  private val BYTE3 = "b011".U(3.W)
-  private val BYTE4 = "b100".U(3.W)
-  private val BYTE5 = "b101".U(3.W)
-  private val BYTE6 = "b110".U(3.W)
-  private val BYTE7 = "b111".U(3.W)
-
-  def getMask(sizeEncode: UInt) = LookupTree(sizeEncode, List(
-    LB -> 0x1.U, // lb
-    LH -> 0x3.U, // lh
-    LW -> 0xf.U, // lw
-    LD -> 0xff.U  // ld
-  ))
-
-  def getShiftAndTruncateData(shiftEncode: UInt, truncateEncode: UInt, data: UInt) = {
-    val shiftData = LookupTree(shiftEncode, List(
-      BYTE0 -> data(63,    0),
-      BYTE1 -> data(63,    8),
-      BYTE2 -> data(63,   16),
-      BYTE3 -> data(63,   24),
-      BYTE4 -> data(63,   32),
-      BYTE5 -> data(63,   40),
-      BYTE6 -> data(63,   48),
-      BYTE7 -> data(63,   56)
-    ))
-    val truncateData = LookupTree(truncateEncode, List(
-      BYTE0 -> 0.U(XLEN.W), // can not truncate with 0 byte width
-      BYTE1 -> shiftData(7,    0),
-      BYTE2 -> shiftData(15,   0),
-      BYTE3 -> shiftData(23,   0),
-      BYTE4 -> shiftData(31,   0),
-      BYTE5 -> shiftData(39,   0),
-      BYTE6 -> shiftData(47,   0),
-      BYTE7 -> shiftData(55,   0)
-    ))
-    truncateData(XLEN - 1, 0)
-  }
-
-  def selectOldest[T <: LqWriteBundle](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
-    assert(valid.length == bits.length)
-    if (valid.length == 0 || valid.length == 1) {
-      (valid, bits)
-    } else if (valid.length == 2) {
-      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
-      for (i <- res.indices) {
-        res(i).valid := valid(i)
-        res(i).bits := bits(i)
-      }
-      val oldest = Mux(valid(0) && valid(1),
-        Mux(isAfter(bits(0).uop.robIdx, bits(1).uop.robIdx) ||
-          (bits(0).uop.robIdx === bits(1).uop.robIdx && bits(0).uop.uopIdx > bits(1).uop.uopIdx), res(1), res(0)),
-        Mux(valid(0) && !valid(1), res(0), res(1)))
-      (Seq(oldest.valid), Seq(oldest.bits))
-    } else {
-      val left = selectOldest(valid.take(valid.length / 2), bits.take(bits.length / 2))
-      val right = selectOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)))
-      selectOldest(left._1 ++ right._1, left._2 ++ right._2)
-    }
-  }
-
-  val io = IO(new Bundle() {
-    val redirect        = Flipped(Valid(new Redirect))
-    val enq             = Vec(enqPortNum, Flipped(new MisalignBufferEnqIO))
-    val rob             = Flipped(new RobLsqIO)
-    val splitLoadReq    = Decoupled(new LsPipelineBundle)
-    val splitLoadResp   = Flipped(Valid(new LqWriteBundle))
-    val writeBack       = Decoupled(new ExuOutput(param))
-    val vecWriteBack    = Decoupled(new VecPipelineFeedbackIO(isVStore = false))
-    val overwriteExpBuf = Output(new XSBundle {
-      val valid  = Bool()
-      val vaddr  = UInt(XLEN.W)
-      val isHyper = Bool()
-      val gpaddr = UInt(XLEN.W)
-      val isForVSnonLeafPTE = Bool()
-    })
-    val flushLdExpBuff  = Output(Bool())
-    val loadMisalignFull = Output(Bool())
-  })
-
-  io.rob.mmio := 0.U.asTypeOf(Vec(LoadPipelineWidth, Bool()))
-  io.rob.uop  := 0.U.asTypeOf(Vec(LoadPipelineWidth, new DynInst))
-
-  val req_valid = RegInit(false.B)
-  val req = Reg(new LqWriteBundle)
-
-  io.loadMisalignFull := req_valid
-
-  (0 until io.enq.length).map{i =>
-    if (i == 0) {
-      io.enq(0).req.ready := !req_valid && io.enq(0).req.valid
-    }
-    else {
-      io.enq(i).req.ready := !io.enq.take(i).map(_.req.ready).reduce(_ || _) && !req_valid && io.enq(i).req.valid
-    }
-  }
-
-  val select_req_bit   = ParallelPriorityMux(io.enq.map(_.req.valid), io.enq.map(_.req.bits))
-  val select_req_valid = io.enq.map(_.req.valid).reduce(_ || _)
-  val canEnqValid = !req_valid && !select_req_bit.uop.robIdx.needFlush(io.redirect) && select_req_valid
-  when(canEnqValid) {
-    req := select_req_bit
-    req_valid := true.B
-  }
-
-  // buffer control:
-  //  - s_idle:   idle
-  //  - s_split:  split misalign laod
-  //  - s_req:    issue a split memory access request
-  //  - s_resp:   Responds to a split load access request
-  //  - s_comb_wakeup_rep: Merge the data and issue a wakeup load
-  //  - s_wb: writeback yo rob/vecMergeBuffer
-  val s_idle :: s_split :: s_req :: s_resp :: s_comb_wakeup_rep :: s_wb :: Nil = Enum(6)
-  val bufferState = RegInit(s_idle)
-  val splitLoadReqs = RegInit(VecInit(List.fill(maxSplitNum)(0.U.asTypeOf(new LsPipelineBundle))))
-  val splitLoadResp = RegInit(VecInit(List.fill(maxSplitNum)(0.U.asTypeOf(new LqWriteBundle))))
-  val exceptionVec = RegInit(0.U.asTypeOf(ExceptionVec()))
-  val unSentLoads = RegInit(0.U(maxSplitNum.W))
-  val curPtr = RegInit(0.U(log2Ceil(maxSplitNum).W))
-  val needWakeUpReqsWire = Wire(Bool())
-  val needWakeUpWB       = RegInit(false.B)
-  val data_select        = RegEnable(genRdataOH(select_req_bit.uop), 0.U(genRdataOH(select_req_bit.uop).getWidth.W), canEnqValid)
-
-  // if there is exception or uncache in split load
-  val globalException = RegInit(false.B)
-  val globalUncache = RegInit(false.B)
-
-  // debug info
-  val globalMMIO = RegInit(false.B)
-  val globalNC   = RegInit(false.B)
-  val globalMemBackTypeMM = RegInit(false.B)
-
-  val hasException = io.splitLoadResp.bits.vecActive &&
-    ExceptionNO.selectByFu(io.splitLoadResp.bits.uop.exceptionVec, LduCfg).asUInt.orR || TriggerAction.isDmode(io.splitLoadResp.bits.uop.trigger)
-  val isUncache = io.splitLoadResp.bits.mmio || io.splitLoadResp.bits.nc
-  needWakeUpReqsWire := false.B
-  switch(bufferState) {
-    is (s_idle) {
-      when (req_valid) {
-        bufferState := s_split
-      }
-    }
-
-    is (s_split) {
-      bufferState := s_req
-    }
-
-    is (s_req) {
-      when (io.splitLoadReq.fire) {
-        bufferState := s_resp
-      }
-    }
-
-    is (s_resp) {
-      when (io.splitLoadResp.valid) {
-        val clearOh = UIntToOH(curPtr)
-        when (hasException || isUncache) {
-          // commit directly when exception ocurs
-          // if any split load reaches uncache space, delegate to software loadAddrMisaligned exception
-          bufferState := s_wb
-          globalException := hasException
-          globalUncache := isUncache
-          globalMMIO := io.splitLoadResp.bits.mmio
-          globalNC   := io.splitLoadResp.bits.nc
-          globalMemBackTypeMM := io.splitLoadResp.bits.memBackTypeMM
-        } .elsewhen(io.splitLoadResp.bits.rep_info.need_rep || (unSentLoads & ~clearOh).orR) {
-          // need replay or still has unsent requests
-          bufferState := s_req
-        } .otherwise {
-          // merge the split load results
-          bufferState := s_comb_wakeup_rep
-          needWakeUpWB := !req.isvec
-        }
-      }
-    }
-
-    is (s_comb_wakeup_rep) {
-      when(!req.isvec) {
-        when(io.splitLoadReq.fire) {
-          bufferState := s_wb
-        }.otherwise {
-          bufferState := s_comb_wakeup_rep
-        }
-        needWakeUpReqsWire := true.B
-      } .otherwise {
-        bufferState := s_wb
-      }
-
-    }
-
-    is (s_wb) {
-      when(req.isvec) {
-        when(io.vecWriteBack.fire) {
-          bufferState := s_idle
-          req_valid := false.B
-          curPtr := 0.U
-          unSentLoads := 0.U
-          globalException := false.B
-          globalUncache := false.B
-          needWakeUpWB := false.B
-
-          globalMMIO := false.B
-          globalNC   := false.B
-          globalMemBackTypeMM := false.B
-        }
-
-      } .otherwise {
-        when(io.writeBack.fire) {
-          bufferState := s_idle
-          req_valid := false.B
-          curPtr := 0.U
-          unSentLoads := 0.U
-          globalException := false.B
-          globalUncache := false.B
-          needWakeUpWB := false.B
-
-          globalMMIO := false.B
-          globalNC   := false.B
-          globalMemBackTypeMM := false.B
-        }
-      }
-
-    }
-  }
-
-  val alignedType = Mux(req.isvec, req.alignedType(1,0), LSUOpType.size(req.uop.fuOpType))
-  val highAddress = LookupTree(alignedType, List(
-    LB -> 0.U,
-    LH -> 1.U,
-    LW -> 3.U,
-    LD -> 7.U
-  )) + req.vaddr(4, 0)
-  // to see if (vaddr + opSize - 1) and vaddr are in the same 16 bytes region
-  val cross16BytesBoundary = req_valid && (highAddress(4) =/= req.vaddr(4))
-  val aligned16BytesAddr   = (req.vaddr >> 4) << 4// req.vaddr & ~("b1111".U)
-  val aligned16BytesSel    = req.vaddr(3, 0)
-
-  // meta of 128 bit load
-  val new128Load = WireInit(0.U.asTypeOf(new LsPipelineBundle))
-  // meta of split loads
-  val lowAddrLoad  = WireInit(0.U.asTypeOf(new LsPipelineBundle))
-  val highAddrLoad = WireInit(0.U.asTypeOf(new LsPipelineBundle))
-  val lowResultShift = RegInit(0.U(3.W)) // how many bytes should we shift right when got result
-  val lowResultWidth = RegInit(0.U(3.W)) // how many bytes should we take from result
-  val highResultShift = RegInit(0.U(3.W))
-  val highResultWidth = RegInit(0.U(3.W))
-
-  when (bufferState === s_split) {
-    when (!cross16BytesBoundary) {
-      assert(false.B, s"There should be no non-aligned access that does not cross 16Byte boundaries.")
-    } .otherwise {
-      // split this unaligned load into `maxSplitNum` aligned loads
-      unSentLoads := Fill(maxSplitNum, 1.U(1.W))
-      curPtr := 0.U
-      lowAddrLoad.uop := req.uop
-      lowAddrLoad.uop.exceptionVec(loadAddrMisaligned) := false.B
-      lowAddrLoad.fullva := req.fullva
-      highAddrLoad.uop := req.uop
-      highAddrLoad.uop.exceptionVec(loadAddrMisaligned) := false.B
-      highAddrLoad.fullva := req.fullva
-
-      switch (alignedType(1, 0)) {
-        is (LB) {
-          assert(false.B, "lb should not trigger miss align")
-        }
-
-        is (LH) {
-          lowAddrLoad.uop.fuOpType := LB
-          lowAddrLoad.vaddr := req.vaddr
-          lowAddrLoad.mask  := 0x1.U << lowAddrLoad.vaddr(3, 0)
-          lowResultShift    := BYTE0
-          lowResultWidth    := BYTE1
-
-          highAddrLoad.uop.fuOpType := LB
-          highAddrLoad.vaddr := req.vaddr + 1.U
-          highAddrLoad.mask  := 0x1.U << highAddrLoad.vaddr(3, 0)
-          highResultShift    := BYTE0
-          highResultWidth    := BYTE1
-        }
-
-        is (LW) {
-          switch (req.vaddr(1, 0)) {
-            is ("b00".U) {
-              assert(false.B, "should not trigger miss align")
-            }
-
-            is ("b01".U) {
-              lowAddrLoad.uop.fuOpType := LW
-              lowAddrLoad.vaddr := req.vaddr - 1.U
-              lowAddrLoad.mask  := 0xf.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE1
-              lowResultWidth    := BYTE3
-
-              highAddrLoad.uop.fuOpType := LB
-              highAddrLoad.vaddr := req.vaddr + 3.U
-              highAddrLoad.mask  := 0x1.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE1
-            }
-
-            is ("b10".U) {
-              lowAddrLoad.uop.fuOpType := LH
-              lowAddrLoad.vaddr := req.vaddr
-              lowAddrLoad.mask  := 0x3.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE0
-              lowResultWidth    := BYTE2
-
-              highAddrLoad.uop.fuOpType := LH
-              highAddrLoad.vaddr := req.vaddr + 2.U
-              highAddrLoad.mask  := 0x3.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE2
-            }
-
-            is ("b11".U) {
-              lowAddrLoad.uop.fuOpType := LB
-              lowAddrLoad.vaddr := req.vaddr
-              lowAddrLoad.mask  := 0x1.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE0
-              lowResultWidth    := BYTE1
-
-              highAddrLoad.uop.fuOpType := LW
-              highAddrLoad.vaddr := req.vaddr + 1.U
-              highAddrLoad.mask  := 0xf.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE3
-            }
-          }
-        }
-
-        is (LD) {
-          switch (req.vaddr(2, 0)) {
-            is ("b000".U) {
-              assert(false.B, "should not trigger miss align")
-            }
-
-            is ("b001".U) {
-              lowAddrLoad.uop.fuOpType := LD
-              lowAddrLoad.vaddr := req.vaddr - 1.U
-              lowAddrLoad.mask  := 0xff.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE1
-              lowResultWidth    := BYTE7
-
-              highAddrLoad.uop.fuOpType := LB
-              highAddrLoad.vaddr := req.vaddr + 7.U
-              highAddrLoad.mask  := 0x1.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE1
-            }
-
-            is ("b010".U) {
-              lowAddrLoad.uop.fuOpType := LD
-              lowAddrLoad.vaddr := req.vaddr - 2.U
-              lowAddrLoad.mask  := 0xff.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE2
-              lowResultWidth    := BYTE6
-
-              highAddrLoad.uop.fuOpType := LH
-              highAddrLoad.vaddr := req.vaddr + 6.U
-              highAddrLoad.mask  := 0x3.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE2
-            }
-
-            is ("b011".U) {
-              lowAddrLoad.uop.fuOpType := LD
-              lowAddrLoad.vaddr := req.vaddr - 3.U
-              lowAddrLoad.mask  := 0xff.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE3
-              lowResultWidth    := BYTE5
-
-              highAddrLoad.uop.fuOpType := LW
-              highAddrLoad.vaddr := req.vaddr + 5.U
-              highAddrLoad.mask  := 0xf.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE3
-            }
-
-            is ("b100".U) {
-              lowAddrLoad.uop.fuOpType := LW
-              lowAddrLoad.vaddr := req.vaddr
-              lowAddrLoad.mask  := 0xf.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE0
-              lowResultWidth    := BYTE4
-
-              highAddrLoad.uop.fuOpType := LW
-              highAddrLoad.vaddr := req.vaddr + 4.U
-              highAddrLoad.mask  := 0xf.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE4
-            }
-
-            is ("b101".U) {
-              lowAddrLoad.uop.fuOpType := LW
-              lowAddrLoad.vaddr := req.vaddr - 1.U
-              lowAddrLoad.mask  := 0xf.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE1
-              lowResultWidth    := BYTE3
-
-              highAddrLoad.uop.fuOpType := LD
-              highAddrLoad.vaddr := req.vaddr + 3.U
-              highAddrLoad.mask  := 0xff.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE5
-            }
-
-            is ("b110".U) {
-              lowAddrLoad.uop.fuOpType := LH
-              lowAddrLoad.vaddr := req.vaddr
-              lowAddrLoad.mask  := 0x3.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE0
-              lowResultWidth    := BYTE2
-
-              highAddrLoad.uop.fuOpType := LD
-              highAddrLoad.vaddr := req.vaddr + 2.U
-              highAddrLoad.mask  := 0xff.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE6
-            }
-
-            is ("b111".U) {
-              lowAddrLoad.uop.fuOpType := LB
-              lowAddrLoad.vaddr := req.vaddr
-              lowAddrLoad.mask  := 0x1.U << lowAddrLoad.vaddr(3, 0)
-              lowResultShift    := BYTE0
-              lowResultWidth    := BYTE1
-
-              highAddrLoad.uop.fuOpType := LD
-              highAddrLoad.vaddr := req.vaddr + 1.U
-              highAddrLoad.mask  := 0xff.U << highAddrLoad.vaddr(3, 0)
-              highResultShift    := BYTE0
-              highResultWidth    := BYTE7
-            }
-          }
-        }
-      }
-
-      splitLoadReqs(0) := lowAddrLoad
-      splitLoadReqs(1) := highAddrLoad
-    }
-    exceptionVec := 0.U.asTypeOf(exceptionVec.cloneType)
-  }
-
-  io.splitLoadReq.valid := req_valid && (bufferState === s_req || bufferState === s_comb_wakeup_rep && needWakeUpReqsWire && !req.isvec)
-  io.splitLoadReq.bits  := splitLoadReqs(curPtr)
-  io.splitLoadReq.bits.isvec  := req.isvec
-  io.splitLoadReq.bits.misalignNeedWakeUp  := needWakeUpReqsWire
-  io.splitLoadReq.bits.isFinalSplit        := curPtr(0) && !needWakeUpReqsWire
-  // Restore the information of H extension load
-  // bit encoding: | hlv 1 | hlvx 1 | is unsigned(1bit) | size(2bit) |
-  val reqIsHlv  = LSUOpType.isHlv(req.uop.fuOpType)
-  val reqIsHlvx = LSUOpType.isHlvx(req.uop.fuOpType)
-
-  val scalaLdOpType = LSUOpType.makeLsUop(isHlv = reqIsHlv, isHlvx = reqIsHlvx, size = LSUOpType.size(splitLoadReqs(curPtr).uop.fuOpType))
-  io.splitLoadReq.bits.uop.fuOpType := Mux(req.isvec, req.uop.fuOpType, scalaLdOpType)
-  io.splitLoadReq.bits.alignedType  := Mux(req.isvec, LSUOpType.size(splitLoadReqs(curPtr).uop.fuOpType), req.alignedType)
-
-  when (io.splitLoadResp.valid && bufferState === s_resp && req.uop.robIdx === io.splitLoadResp.bits.uop.robIdx) {
-    val resp = io.splitLoadResp.bits
-    splitLoadResp(curPtr) := io.splitLoadResp.bits
-    when (isUncache) {
-      unSentLoads := 0.U
-      exceptionVec := ExceptionNO.selectByFu(0.U.asTypeOf(exceptionVec.cloneType), LduCfg)
-      // delegate to software
-      exceptionVec(loadAddrMisaligned) := true.B
-    } .elsewhen (hasException) {
-      unSentLoads := 0.U
-      LduCfg.exceptionOut.map(no => exceptionVec(no) := exceptionVec(no) || resp.uop.exceptionVec(no))
-    } .elsewhen (!io.splitLoadResp.bits.rep_info.need_rep) {
-      unSentLoads := unSentLoads & ~UIntToOH(curPtr)
-      curPtr := curPtr + 1.U
-      exceptionVec := 0.U.asTypeOf(ExceptionVec())
-    }
-  }
-
-  val combinedData = RegInit(0.U(XLEN.W))
-
-  when (bufferState === s_comb_wakeup_rep) {
-    val lowAddrResult = getShiftAndTruncateData(lowResultShift, lowResultWidth, splitLoadResp(0).data)
-                          .asTypeOf(Vec(XLEN / 8, UInt(8.W)))
-    val highAddrResult = getShiftAndTruncateData(highResultShift, highResultWidth, splitLoadResp(1).data)
-                          .asTypeOf(Vec(XLEN / 8, UInt(8.W)))
-    val catResult = Wire(Vec(XLEN / 8, UInt(8.W)))
-    (0 until XLEN / 8) .map {
-      case i => {
-        when (i.U < lowResultWidth) {
-          catResult(i) := lowAddrResult(i)
-        } .otherwise {
-          catResult(i) := highAddrResult(i.U - lowResultWidth)
-        }
-      }
-    }
-    combinedData := Mux(req.isvec, rdataVecHelper(req.alignedType, (catResult.asUInt)(XLEN - 1, 0)), rdataHelper(req.uop, (catResult.asUInt)(XLEN - 1, 0)))
-
-  }
-
-  io.writeBack.valid := req_valid && bufferState === s_wb &&
-    (io.splitLoadResp.valid && io.splitLoadResp.bits.misalignNeedWakeUp || globalUncache || globalException) &&
-    !req.isvec
-  io.writeBack.bits.data := VecInit(Seq.fill(param.wbPathNum)(newRdataHelper(data_select, combinedData)))
-  io.writeBack.bits.pdest := req.uop.pdest
-  io.writeBack.bits.robIdx := req.uop.robIdx
-  io.writeBack.bits.intWen.get := !globalException && !globalUncache && req.uop.rfWen
-  io.writeBack.bits.fpWen.get := req.uop.fpWen
-  // all exception bits are false unless the indices of bits are contained in LduCfg.exceptionOut
-  io.writeBack.bits.exceptionVec.get := 0.U.asTypeOf(io.writeBack.bits.exceptionVec.get)
-  io.writeBack.bits.exceptionVec.foreach(excp => {
-    LduCfg.exceptionOut.foreach(no => excp(no) := (globalUncache || globalException) && exceptionVec(no))
-  })
-
-  io.writeBack.bits.flushPipe.foreach(_ := false.B)
-  io.writeBack.bits.replay.foreach(_ := false.B)
-  io.writeBack.bits.lqIdx.get := req.uop.lqIdx
-  io.writeBack.bits.trigger.get := req.uop.trigger
-  io.writeBack.bits.isRVC.get := req.uop.isRVC
-  io.writeBack.bits.vls.foreach { case x =>
-    x.vpu := req.uop.vpu
-    x.oldVdPsrc := req.uop.psrc(2)
-    x.vdIdx := DontCare
-    x.vdIdxInField := DontCare
-    x.isIndexed := VlduType.isIndexed(req.uop.fuOpType)
-    x.isMasked := VlduType.isMasked(req.uop.fuOpType)
-    x.isStrided := VlduType.isStrided(req.uop.fuOpType)
-    x.isWhole := VlduType.isWhole(req.uop.fuOpType)
-    x.isVecLoad := VlduType.isVecLd(req.uop.fuOpType)
-    x.isVlm := VlduType.isMasked(req.uop.fuOpType) && VlduType.isVecLd(req.uop.fuOpType)
-  }
-  io.writeBack.bits.isFromLoadUnit.get := true.B
-  io.writeBack.bits.perfDebugInfo.foreach(_ := req.uop.perfDebugInfo)
-  io.writeBack.bits.debug.isMMIO := globalMMIO
-  io.writeBack.bits.debug.isNCIO := globalNC && !globalMemBackTypeMM
-  io.writeBack.bits.debug.isPerfCnt := false.B
-  io.writeBack.bits.debug.paddr := req.paddr
-  io.writeBack.bits.debug.vaddr := req.vaddr
-  io.writeBack.bits.debug.vaddr := req.vaddr
-  io.writeBack.bits.debug_seqNum.foreach(_ := req.uop.debug_seqNum)
-  // vector output
-  io.vecWriteBack.valid := req_valid && (bufferState === s_wb) && req.isvec
-
-  io.vecWriteBack.bits.alignedType          := req.alignedType
-  io.vecWriteBack.bits.vecFeedback          := true.B
-  io.vecWriteBack.bits.vecdata.get          := combinedData
-  io.vecWriteBack.bits.isvec                := req.isvec
-  io.vecWriteBack.bits.elemIdx              := req.elemIdx
-  io.vecWriteBack.bits.elemIdxInsideVd.get  := req.elemIdxInsideVd
-  io.vecWriteBack.bits.mask                 := req.mask
-  io.vecWriteBack.bits.reg_offset.get       := 0.U
-  io.vecWriteBack.bits.usSecondInv          := req.usSecondInv
-  io.vecWriteBack.bits.mBIndex              := req.mbIndex
-  io.vecWriteBack.bits.hit                  := true.B
-  io.vecWriteBack.bits.sourceType           := RSFeedbackType.lrqFull
-  io.vecWriteBack.bits.trigger              := TriggerAction.None
-  io.vecWriteBack.bits.flushState           := DontCare
-  io.vecWriteBack.bits.exceptionVec         := ExceptionNO.selectByFu(exceptionVec, VlduCfg)
-  io.vecWriteBack.bits.hasException         := globalException
-  io.vecWriteBack.bits.vaddr                := req.fullva
-  io.vecWriteBack.bits.vaNeedExt            := req.vaNeedExt
-  io.vecWriteBack.bits.gpaddr               := req.gpaddr
-  io.vecWriteBack.bits.isForVSnonLeafPTE    := req.isForVSnonLeafPTE
-  io.vecWriteBack.bits.mmio                 := globalMMIO
-  io.vecWriteBack.bits.vstart               := req.uop.vpu.vstart
-  io.vecWriteBack.bits.vecTriggerMask       := req.vecTriggerMask
-  io.vecWriteBack.bits.nc                   := globalNC
-
-
-  val flush = req_valid && req.uop.robIdx.needFlush(io.redirect)
-
-  when (flush) {
-    bufferState := s_idle
-    req_valid := false.B
-    curPtr := 0.U
-    unSentLoads := 0.U
-    globalException := false.B
-    globalUncache := false.B
-
-    globalMMIO := false.B
-    globalNC   := false.B
-    globalMemBackTypeMM := false.B
-  }
-
-  // NOTE: spectial case (unaligned load cross page, page fault happens in next page)
-  // if exception happens in the higher page address part, overwrite the loadExceptionBuffer vaddr
-  val shouldOverwrite = req_valid && globalException
-  val overwriteExpBuf = GatedValidRegNext(shouldOverwrite)
-  val overwriteVaddr = RegEnable(
-    Mux(
-      cross16BytesBoundary && (curPtr === 1.U),
-      splitLoadResp(curPtr).vaddr,
-      splitLoadResp(curPtr).fullva),
-    shouldOverwrite)
-  val overwriteGpaddr = RegEnable(splitLoadResp(curPtr).gpaddr, shouldOverwrite)
-  val overwriteIsHyper = RegEnable(splitLoadResp(curPtr).isHyper, shouldOverwrite)
-  val overwriteIsForVSnonLeafPTE = RegEnable(splitLoadResp(curPtr).isForVSnonLeafPTE, shouldOverwrite)
-
-  //TODO In theory, there is no need to overwrite, but for now, the signal is retained in the code in this way.
-  // and the signal will be removed after sufficient verification.
-  io.overwriteExpBuf.valid := false.B
-  io.overwriteExpBuf.vaddr := overwriteVaddr
-  io.overwriteExpBuf.isHyper := overwriteIsHyper
-  io.overwriteExpBuf.gpaddr := overwriteGpaddr
-  io.overwriteExpBuf.isForVSnonLeafPTE := overwriteIsForVSnonLeafPTE
-
-  // when no exception or uncache, flush loadExceptionBuffer at s_wb
-  val flushLdExpBuff = GatedValidRegNext(req_valid && (bufferState === s_wb) && !(globalUncache || globalException))
-  io.flushLdExpBuff := flushLdExpBuff
-
-  XSPerfAccumulate("alloc",                  RegNext(!req_valid) && req_valid)
-  XSPerfAccumulate("flush",                  flush)
-  XSPerfAccumulate("flush_idle",             flush && (bufferState === s_idle))
-  XSPerfAccumulate("flush_non_idle",         flush && (bufferState =/= s_idle))
-}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index 94df79888bb..0649d92b39f 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -48,6 +48,7 @@ object LqPtr {
   }
 }
 
+// TODO: remove this
 trait HasLoadHelper { this: XSModule =>
   def rdataHelper(uop: DynInst, rdata: UInt): UInt = {
     val fpWen = uop.fpWen
@@ -78,6 +79,7 @@ trait HasLoadHelper { this: XSModule =>
     ))
   }
 
+  // TODO: remove genRdataOH and newRdataHelper when old LoadUnit is removed
   def genRdataOH(uop: DynInst): UInt = {
     val fuOpType = uop.fuOpType
     val fpWen    = uop.fpWen
@@ -163,12 +165,12 @@ class LoadQueue(implicit p: Parameters) extends XSModule
     val vecFeedback = Vec(VecLoadPipelineWidth, Flipped(ValidIO(new FeedbackToLsqIO)))
     val enq = new LqEnqIO
     val ldu = new Bundle() {
-        val stld_nuke_query = Vec(LoadPipelineWidth, Flipped(new LoadNukeQueryIO)) // from load_s2
-        val ldld_nuke_query = Vec(LoadPipelineWidth, Flipped(new LoadNukeQueryIO)) // from load_s2
-        val ldin         = Vec(LoadPipelineWidth, Flipped(Decoupled(new LqWriteBundle))) // from load_s3
+      val rawNukeQuery = Vec(LoadPipelineWidth, Flipped(new LoadRAWNukeQuery()))
+      val rarNukeQuery = Vec(LoadPipelineWidth, Flipped(new LoadRARNukeQuery()))
+      val ldin         = Vec(LoadPipelineWidth, Flipped(Decoupled(new LqWriteBundle))) // from load_s3
     }
     val sta = new Bundle() {
-      val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new LsPipelineBundle))) // from store_s1
+      val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreAddrIO))) // from store_s1
     }
     val std = new Bundle() {
       val storeDataIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreQueueDataWrite))) // from store_s0, store data, send to sq from rs
@@ -180,21 +182,17 @@ class LoadQueue(implicit p: Parameters) extends XSModule
       val stDataReadyVec   = Input(Vec(StoreQueueSize, Bool()))
       val stIssuePtr       = Input(new SqPtr)
       val sqEmpty          = Input(Bool())
+      val sqDeqPtr         = Input(new SqPtr)
     }
-    val ldout = Vec(LoadPipelineWidth, DecoupledIO(new MemExuOutput))
-    val ld_raw_data = Vec(LoadPipelineWidth, Output(new LoadDataFromLQBundle))
-    val ncOut = Vec(LoadPipelineWidth, DecoupledIO(new LsPipelineBundle))
-    val replay = Vec(LoadPipelineWidth, Decoupled(new LsPipelineBundle))
-  //  val refill = Flipped(ValidIO(new Refill))
-    val tl_d_channel  = Input(new DcacheToLduForwardIO)
+    val bypass = Flipped(Vec(LoadPipelineWidth, new UncacheBypass))
+    val replay = Vec(LoadPipelineWidth, Decoupled(new LoadReplayIO))
+    val loadWakeup  = Flipped(ValidIO(new DCacheLoadWakeup()))
     val release = Flipped(Valid(new Release))
     val nuke_rollback = Vec(StorePipelineWidth, Output(Valid(new Redirect)))
     val nack_rollback = Vec(1, Output(Valid(new Redirect))) // uncachebuffer
     val rob = Flipped(new RobLsqIO)
     val uncache = new UncacheWordIO
-    val exceptionAddr = new ExceptionAddrIO
-    val loadMisalignFull = Input(Bool())
-    val misalignAllowSpec = Input(Bool())
+    val exceptionInfo = ValidIO(new MemExceptionInfo())
     val lqFull = Output(Bool())
     val lqDeq = Output(UInt(log2Up(CommitWidth + 1).W))
     val lqCancelCnt = Output(UInt(log2Up(VirtualLoadQueueSize+1).W))
@@ -219,7 +217,6 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   val loadQueueRAW = Module(new LoadQueueRAW)  //  read-after-write violation
   val loadQueueReplay = Module(new LoadQueueReplay)  //  enqueue if need replay
   val virtualLoadQueue = Module(new VirtualLoadQueue)  //  control state
-  val exceptionBuffer = Module(new LqExceptionBuffer) // exception buffer
   val uncacheBuffer = Module(new LoadQueueUncache) // uncache
   /**
    * LoadQueueRAR
@@ -228,11 +225,7 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   loadQueueRAR.io.release   <> io.release
   loadQueueRAR.io.ldWbPtr   <> virtualLoadQueue.io.ldWbPtr
   loadQueueRAR.io.validCount<> io.rarValidCount
-  for (w <- 0 until LoadPipelineWidth) {
-    loadQueueRAR.io.query(w).req    <> io.ldu.ldld_nuke_query(w).req // from load_s1
-    loadQueueRAR.io.query(w).resp   <> io.ldu.ldld_nuke_query(w).resp // to load_s2
-    loadQueueRAR.io.query(w).revoke := io.ldu.ldld_nuke_query(w).revoke // from load_s3
-  }
+  loadQueueRAR.io.query     <> io.ldu.rarNukeQuery
 
   /**
    * LoadQueueRAW
@@ -241,12 +234,8 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   loadQueueRAW.io.storeIn          <> io.sta.storeAddrIn
   loadQueueRAW.io.stAddrReadySqPtr <> io.sq.stAddrReadySqPtr
   loadQueueRAW.io.stIssuePtr       <> io.sq.stIssuePtr
-  for (w <- 0 until LoadPipelineWidth) {
-    loadQueueRAW.io.query(w).req    <> io.ldu.stld_nuke_query(w).req // from load_s1
-    loadQueueRAW.io.query(w).resp   <> io.ldu.stld_nuke_query(w).resp // to load_s2
-    loadQueueRAW.io.query(w).revoke := io.ldu.stld_nuke_query(w).revoke // from load_s3
-  }
-  io.mdpTrain                       := loadQueueRAW.io.mdpTrain
+  loadQueueRAW.io.query            <> io.ldu.rawNukeQuery
+  io.mdpTrain                      := loadQueueRAW.io.mdpTrain
 
   /**
    * VirtualLoadQueue
@@ -261,51 +250,20 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   virtualLoadQueue.io.lqEmpty       <> io.lqEmpty
   virtualLoadQueue.io.ldWbPtr       <> io.lqDeqPtr
 
-  /**
-   * Load queue exception buffer
-   */
-  exceptionBuffer.io.redirect <> io.redirect
-  for (i <- 0 until LoadPipelineWidth) {
-    exceptionBuffer.io.req(i).valid := io.ldu.ldin(i).valid && !io.ldu.ldin(i).bits.isvec // from load_s3
-    exceptionBuffer.io.req(i).bits := io.ldu.ldin(i).bits
-  }
-  // vlsu exception!
-  for (i <- 0 until VecLoadPipelineWidth) {
-    exceptionBuffer.io.req(LoadPipelineWidth + i).valid                 := io.vecFeedback(i).valid && io.vecFeedback(i).bits.feedback(VecFeedbacks.FLUSH) // have exception
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits                  := DontCare
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.vaddr            := io.vecFeedback(i).bits.vaddr
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.fullva           := io.vecFeedback(i).bits.vaddr
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.vaNeedExt        := io.vecFeedback(i).bits.vaNeedExt
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.gpaddr           := io.vecFeedback(i).bits.gpaddr
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.uopIdx       := io.vecFeedback(i).bits.uopidx
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.robIdx       := io.vecFeedback(i).bits.robidx
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.vpu.vstart   := io.vecFeedback(i).bits.vstart
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.vpu.vl       := io.vecFeedback(i).bits.vl
-    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.exceptionVec := io.vecFeedback(i).bits.exceptionVec
-  }
-  // mmio non-data error exception
-  exceptionBuffer.io.req(LoadPipelineWidth + VecLoadPipelineWidth) := uncacheBuffer.io.exception
-  exceptionBuffer.io.req(LoadPipelineWidth + VecLoadPipelineWidth).bits.vaNeedExt := true.B
-
-  loadQueueReplay.io.loadMisalignFull := io.loadMisalignFull
-  loadQueueReplay.io.misalignAllowSpec := io.misalignAllowSpec
-
-  io.exceptionAddr <> exceptionBuffer.io.exceptionAddr
-
   /**
    * Load uncache buffer
    */
   uncacheBuffer.io.redirect <> io.redirect
-  uncacheBuffer.io.mmioOut <> io.ldout
-  uncacheBuffer.io.ncOut <> io.ncOut
-  uncacheBuffer.io.mmioRawData <> io.ld_raw_data
+  uncacheBuffer.io.bypass <> io.bypass
   uncacheBuffer.io.rob <> io.rob
   uncacheBuffer.io.uncache <> io.uncache
 
+  io.exceptionInfo <> uncacheBuffer.io.exceptionInfo
+
   for ((buff, w) <- uncacheBuffer.io.req.zipWithIndex) {
     // from load_s3
     val ldinBits = io.ldu.ldin(w).bits
-    buff.valid := io.ldu.ldin(w).valid && !ldinBits.nc_with_data
+    buff.valid := io.ldu.ldin(w).valid && ldinBits.rep_info.mmioOrNc
     buff.bits := ldinBits
   }
 
@@ -324,13 +282,13 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   loadQueueReplay.io.storeAddrIn      <> io.sta.storeAddrIn // from store_s1
   loadQueueReplay.io.storeDataIn      <> io.std.storeDataIn // from store_s0
   loadQueueReplay.io.replay           <> io.replay
-  //loadQueueReplay.io.refill           <> io.refill
-  loadQueueReplay.io.tl_d_channel     <> io.tl_d_channel
+  loadQueueReplay.io.loadWakeup       <> io.loadWakeup
   loadQueueReplay.io.stAddrReadySqPtr <> io.sq.stAddrReadySqPtr
   loadQueueReplay.io.stAddrReadyVec   <> io.sq.stAddrReadyVec
   loadQueueReplay.io.stDataReadySqPtr <> io.sq.stDataReadySqPtr
   loadQueueReplay.io.stDataReadyVec   <> io.sq.stDataReadyVec
   loadQueueReplay.io.sqEmpty          <> io.sq.sqEmpty
+  loadQueueReplay.io.sqDeqPtr         <> io.sq.sqDeqPtr
   loadQueueReplay.io.lqFull           <> io.lq_rep_full
   loadQueueReplay.io.ldWbPtr          <> virtualLoadQueue.io.ldWbPtr
   loadQueueReplay.io.rarFull          <> loadQueueRAR.io.lqFull
@@ -339,6 +297,8 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   loadQueueReplay.io.tlb_hint         <> io.tlb_hint
   loadQueueReplay.io.tlbReplayDelayCycleCtrl <> io.tlbReplayDelayCycleCtrl
 
+  loadQueueReplay.io.mmioWakeup := uncacheBuffer.io.mmioWakeup
+  loadQueueReplay.io.ncWakeup := uncacheBuffer.io.ncWakeup
   // TODO: implement it!
   loadQueueReplay.io.vecFeedback := io.vecFeedback
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
index d3c95ae26f8..d8af855cd1e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAR.scala
@@ -37,7 +37,7 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
     val redirect = Flipped(Valid(new Redirect))
 
     // violation query
-    val query = Vec(LoadPipelineWidth, Flipped(new LoadNukeQueryIO))
+    val query = Vec(LoadPipelineWidth, Flipped(new LoadRARNukeQuery()))
 
     // release cacheline
     val release = Flipped(Valid(new Release))
@@ -94,8 +94,12 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
   //  MicroOp     : Micro-op
   //  PAddr       : physical address.
   //  Released    : DCache released.
+  class UopEntry(implicit p: Parameters) extends XSBundle {
+    val robIdx = new RobPtr()
+    val lqIdx = new LqPtr()
+  }
   val allocated = RegInit(VecInit(List.fill(LoadQueueRARSize)(false.B))) // The control signals need to explicitly indicate the initial value
-  val uop = Reg(Vec(LoadQueueRARSize, new DynInst))
+  val uop = Reg(Vec(LoadQueueRARSize, new UopEntry))
   val paddrModule = Module(new LqPAddrModule(
     gen = UInt(PartialPAddrBits.W),
     numEntries = LoadQueueRARSize,
@@ -137,8 +141,8 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
   // There are still not completed load instructions before the current load instruction.
   // (e.g. "not completed" means that load instruction get the data or exception).
   val canEnqueue = io.query.map(_.req.valid)
-  val cancelEnqueue = io.query.map(_.req.bits.uop.robIdx.needFlush(io.redirect))
-  val hasNotWritebackedLoad = io.query.map(_.req.bits.uop.lqIdx).map(lqIdx => isAfter(lqIdx, io.ldWbPtr))
+  val cancelEnqueue = io.query.map(_.req.bits.robIdx.needFlush(io.redirect))
+  val hasNotWritebackedLoad = io.query.map(_.req.bits.lqIdx).map(lqIdx => isAfter(lqIdx, io.ldWbPtr))
   val needEnqueue = canEnqueue.zip(hasNotWritebackedLoad).zip(cancelEnqueue).map { case ((v, r), c) => v && r && !c }
 
   // Allocate logic
@@ -172,18 +176,19 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
       paddrModule.io.wdata(w) := genPartialPAddr(enq.bits.paddr)
 
       //  Fill info
-      uop(enqIndex) := enq.bits.uop
+      uop(enqIndex).robIdx := enq.bits.robIdx
+      uop(enqIndex).lqIdx := enq.bits.lqIdx
       //  NC is uncachable and will not be explicitly released.
       //  So NC requests are not allowed to have RAR
-      released(enqIndex) := enq.bits.is_nc || (
-        enq.bits.data_valid &&
+      released(enqIndex) := enq.bits.nc || (
+        enq.bits.dataValid &&
         (release2Cycle.valid &&
         enq.bits.paddr(PAddrBits-1, DCacheLineOffset) === release2Cycle.bits.paddr(PAddrBits-1, DCacheLineOffset) ||
         release1Cycle.valid &&
         enq.bits.paddr(PAddrBits-1, DCacheLineOffset) === release1Cycle.bits.paddr(PAddrBits-1, DCacheLineOffset))
       )
     }
-    val debug_robIdx = enq.bits.uop.robIdx.asUInt
+    val debug_robIdx = enq.bits.robIdx.asUInt
     XSError(
       needEnqueue(w) && enq.ready && allocated(enqIndex),
       p"LoadQueueRAR: You can not write an valid entry! check: ldu $w, robIdx $debug_robIdx")
@@ -209,15 +214,23 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
 
   // if need replay revoke entry
   val lastCanAccept = GatedRegNext(acceptedVec)
-  val lastAllocIndex = GatedRegNext(enqIndexVec)
-
-  for ((revoke, w) <- io.query.map(_.revoke).zipWithIndex) {
-    val revokeValid = revoke && lastCanAccept(w)
-    val revokeIndex = lastAllocIndex(w)
+  val lastAllocIndex = enqIndexVec.zip(acceptedVec).map(x => RegEnable(x._1, x._2))
+  val lastLastCanAccept = RegNext(lastCanAccept)
+  val lastLastAllocIndex = lastAllocIndex.zip(lastCanAccept).map(x => RegEnable(x._1, x._2))
 
-    when (allocated(revokeIndex) && revokeValid) {
-      allocated(revokeIndex) := false.B
-      freeMaskVec(revokeIndex) := true.B
+  for ((query, w) <- io.query.zipWithIndex) {
+    val revokeLastCycle = query.revokeLastCycle && lastCanAccept(w)
+    val revokeLastLastCycle = query.revokeLastLastCycle && lastLastCanAccept(w)
+    val revokeLastIndex = lastAllocIndex(w)
+    val revokeLastLastIndex = lastLastAllocIndex(w)
+
+    when (allocated(revokeLastIndex) && revokeLastCycle) {
+      allocated(revokeLastIndex) := false.B
+      freeMaskVec(revokeLastIndex) := true.B
+    }
+    when (allocated(revokeLastLastIndex) && revokeLastLastCycle) {
+      allocated(revokeLastLastIndex) := false.B
+      freeMaskVec(revokeLastLastIndex) := true.B
     }
   }
 
@@ -228,15 +241,13 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
   // 1. Physical address match by CAM port.
   // 2. release or nc_with_data is set.
   // 3. Younger than current load instruction.
-  val ldLdViolation = Wire(Vec(LoadPipelineWidth, Bool()))
   //val allocatedUInt = RegNext(allocated.asUInt)
   for ((query, w) <- io.query.zipWithIndex) {
-    ldLdViolation(w) := false.B
     paddrModule.io.releaseViolationMdata(w) := genPartialPAddr(query.req.bits.paddr)
 
     query.resp.valid := RegNext(query.req.valid)
     // Generate real violation mask
-    val robIdxMask = VecInit(uop.map(_.robIdx).map(isAfter(_, query.req.bits.uop.robIdx)))
+    val robIdxMask = VecInit(uop.map(_.robIdx).map(isAfter(_, query.req.bits.robIdx)))
     val matchMaskReg = Wire(Vec(LoadQueueRARSize, Bool()))
     for(i <- 0 until LoadQueueRARSize) {
       matchMaskReg(i) := (allocated(i) &
@@ -248,13 +259,12 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
     //  Load-to-Load violation check result
     val ldLdViolationMask = matchMask
     ldLdViolationMask.suggestName("ldLdViolationMask_" + w)
-    query.resp.bits.rep_frm_fetch := ParallelORR(ldLdViolationMask)
+    query.resp.bits.nuke := ParallelORR(ldLdViolationMask)
   }
 
 
   // When io.release.valid (release1cycle.valid), it uses the last ld-ld paddr cam port to
   // update release flag in 1 cycle
-  val releaseVioMask = Reg(Vec(LoadQueueRARSize, Bool()))
   when (release1Cycle.valid) {
     paddrModule.io.releaseMdata.takeRight(1)(0) := genPartialPAddr(release1Cycle.bits.paddr)
   }
@@ -274,7 +284,7 @@ class LoadQueueRAR(implicit p: Parameters) extends XSModule
   val canEnqCount = PopCount(io.query.map(_.req.fire))
   val validCount = freeList.io.validCount
   val allowEnqueue = validCount <= (LoadQueueRARSize - LoadPipelineWidth).U
-  val ldLdViolationCount = PopCount(io.query.map(_.resp).map(resp => resp.valid && resp.bits.rep_frm_fetch))
+  val ldLdViolationCount = PopCount(io.query.map(_.resp).map(resp => resp.valid && resp.bits.nuke))
 
   QueuePerf(LoadQueueRARSize, validCount, !allowEnqueue)
   XSPerfAccumulate("enq", canEnqCount)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
index 5712fef76ae..9c876939441 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
@@ -40,10 +40,10 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     val redirect = Flipped(ValidIO(new Redirect))
 
     // violation query
-    val query = Vec(LoadPipelineWidth, Flipped(new LoadNukeQueryIO))
+    val query = Vec(LoadPipelineWidth, Flipped(new LoadRAWNukeQuery()))
 
     // from store unit s1
-    val storeIn = Vec(StorePipelineWidth, Flipped(Valid(new LsPipelineBundle)))
+    val storeIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreAddrIO)))
 
     // global rollback flush
     val rollback = Vec(StorePipelineWidth,Output(Valid(new Redirect)))
@@ -76,8 +76,19 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
   //  Mask        : data mask
   //  Datavalid   : data valid
   //
+  class UopEntry(implicit p: Parameters) extends XSBundle {
+    val robIdx = new RobPtr()
+    val sqIdx = new SqPtr()
+    val isRVC = Bool()
+    val ftqPtr = new FtqPtr()
+    val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
+    // only fo
+    val pc = UInt(VAddrBits.W)
+    val debugInfo = new PerfDebugInfo
+  }
+  private def isOlder(left: UopEntry, right: UopEntry): Bool = isBefore(left.robIdx, right.robIdx)
   val allocated = RegInit(VecInit(List.fill(LoadQueueRAWSize)(false.B))) // The control signals need to explicitly indicate the initial value
-  val uop = Reg(Vec(LoadQueueRAWSize, new DynInst))
+  val uop = Reg(Vec(LoadQueueRAWSize, new UopEntry))
   val paddrModule = Module(new LqPAddrModule(
     gen = UInt(PartialPAddrWidth.W),
     numEntries = LoadQueueRAWSize,
@@ -117,9 +128,9 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
 
   //  LoadQueueRAW enqueue
   val canEnqueue = io.query.map(_.req.valid)
-  val cancelEnqueue = io.query.map(_.req.bits.uop.robIdx.needFlush(io.redirect))
+  val cancelEnqueue = io.query.map(_.req.bits.robIdx.needFlush(io.redirect))
   val allAddrCheck = io.stIssuePtr === io.stAddrReadySqPtr
-  val hasAddrInvalidStore = io.query.map(_.req.bits.uop.sqIdx).map(sqIdx => {
+  val hasAddrInvalidStore = io.query.map(_.req.bits.sqIdx).map(sqIdx => {
     Mux(!allAddrCheck, isBefore(io.stAddrReadySqPtr, sqIdx), false.B)
   })
   val needEnqueue = canEnqueue.zip(hasAddrInvalidStore).zip(cancelEnqueue).map { case ((v, r), c) => v && r && !c }
@@ -163,18 +174,19 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
       maskModule.io.wdata(w) := enq.bits.mask
 
       //  Fill info
-      uop(enqIndex) := enq.bits.uop
-      datavalid(enqIndex) := enq.bits.data_valid
+      uop(enqIndex).robIdx := enq.bits.robIdx
+      uop(enqIndex).sqIdx := enq.bits.sqIdx
+      uop(enqIndex).isRVC := enq.bits.isRVC
+      uop(enqIndex).ftqPtr := enq.bits.ftqPtr
+      uop(enqIndex).ftqOffset := enq.bits.ftqOffset
+      uop(enqIndex).pc := enq.bits.pc
+      uop(enqIndex).debugInfo := enq.bits.debugInfo
+      datavalid(enqIndex) := enq.bits.dataValid
     }
-    val debug_robIdx = enq.bits.uop.robIdx.asUInt
+    val debug_robIdx = enq.bits.robIdx.asUInt
     XSError(needEnqueue(w) && enq.ready && allocated(enqIndex), p"LoadQueueRAW: You can not write an valid entry! check: ldu $w, robIdx $debug_robIdx")
   }
 
-  for ((query, w) <- io.query.map(_.resp).zipWithIndex) {
-    query.valid := RegNext(io.query(w).req.valid)
-    query.bits.rep_frm_fetch := RegNext(false.B)
-  }
-
   //  LoadQueueRAW deallocate
   val freeMaskVec = Wire(Vec(LoadQueueRAWSize, Bool()))
 
@@ -194,18 +206,27 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
   }
 
   // if need replay deallocate entry
-  val lastCanAccept = GatedValidRegNext(acceptedVec)
-  val lastAllocIndex = GatedRegNext(enqIndexVec)
+  val lastCanAccept = RegNext(acceptedVec)
+  val lastAllocIndex = enqIndexVec.zip(acceptedVec).map(x => RegEnable(x._1, x._2))
+  val lastLastCanAccept = RegNext(lastCanAccept)
+  val lastLastAllocIndex = lastAllocIndex.zip(lastCanAccept).map(x => RegEnable(x._1, x._2))
   val willRevoke = WireInit(VecInit(List.fill(LoadQueueRAWSize)(false.B)))
 
-  for ((revoke, w) <- io.query.map(_.revoke).zipWithIndex) {
-    val revokeValid = revoke && lastCanAccept(w)
-    val revokeIndex = lastAllocIndex(w)
+  for ((query, w) <- io.query.zipWithIndex) {
+    val revokeLastCycle = query.revokeLastCycle && lastCanAccept(w)
+    val revokeLastLastCycle = query.revokeLastLastCycle && lastLastCanAccept(w)
+    val revokeLastIndex = lastAllocIndex(w)
+    val revokeLastLastIndex = lastLastAllocIndex(w)
 
-    when (allocated(revokeIndex) && revokeValid) {
-      allocated(revokeIndex) := false.B
-      freeMaskVec(revokeIndex) := true.B
-      willRevoke(revokeIndex) := true.B
+    when (allocated(revokeLastIndex) && revokeLastCycle) {
+      allocated(revokeLastIndex) := false.B
+      freeMaskVec(revokeLastIndex) := true.B
+      willRevoke(revokeLastIndex) := true.B
+    }
+    when (allocated(revokeLastLastIndex) && revokeLastLastCycle) {
+      allocated(revokeLastLastIndex) := false.B
+      freeMaskVec(revokeLastLastIndex) := true.B
+      willRevoke(revokeLastLastIndex) := true.B
     }
   }
   freeList.io.free := freeMaskVec.asUInt
@@ -248,26 +269,7 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
   val lgSelectGroupSize = log2Ceil(SelectGroupSize)
   val TotalSelectCycles = scala.math.ceil(log2Ceil(LoadQueueRAWSize).toFloat / lgSelectGroupSize).toInt + 1
 
-  def selectPartialOldest[T <: XSBundleWithMicroOp](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
-    assert(valid.length == bits.length)
-    if (valid.length == 0 || valid.length == 1) {
-      (valid, bits)
-    } else if (valid.length == 2) {
-      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
-      for (i <- res.indices) {
-        res(i).valid := valid(i)
-        res(i).bits := bits(i)
-      }
-      val oldest = Mux(valid(0) && valid(1), Mux(isAfter(bits(0).uop.robIdx, bits(1).uop.robIdx), res(1), res(0)), Mux(valid(0) && !valid(1), res(0), res(1)))
-      (Seq(oldest.valid), Seq(oldest.bits))
-    } else {
-      val left = selectPartialOldest(valid.take(valid.length / 2), bits.take(bits.length / 2))
-      val right = selectPartialOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)))
-      selectPartialOldest(left._1 ++ right._1, left._2 ++ right._2)
-    }
-  }
-
-  def selectOldest[T <: XSBundleWithMicroOp](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
+  def selectOldestByGroup[T <: UopEntry](valid: Seq[Bool], bits: Seq[T], level: Int, isOlderFu: (T, T) => Bool): (Seq[Bool], Seq[T]) = {
     assert(valid.length == bits.length)
     val numSelectGroups = scala.math.ceil(valid.length.toFloat / SelectGroupSize).toInt
 
@@ -276,18 +278,30 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     val selectBitsGroups = bits.grouped(SelectGroupSize).toList
     // select logic
     if (valid.length <= SelectGroupSize) {
-      val (selValid, selBits) = selectPartialOldest(valid, bits)
-      val selValidNext = GatedValidRegNext(selValid(0))
-      val selBitsNext = RegEnable(selBits(0), selValid(0))
-      (Seq(selValidNext && !selBitsNext.uop.robIdx.needFlush(RegNext(io.redirect))), Seq(selBitsNext))
+      val selectModule = Module(new SelectOldest(bits.head.cloneType, bits.length, isOlderFu).suggestName(s"selectModule_level_${level}"))
+      selectModule.io.in.zipWithIndex.map{case (sink, i) =>
+        sink.valid := valid(i)
+        sink.bits := bits(i)
+      }
+      val selValid = selectModule.io.out.valid
+      val selBits = selectModule.io.out.bits
+      val selValidNext = GatedValidRegNext(selValid)
+      val selBitsNext = RegEnable(selBits, selValid)
+      (Seq(selValidNext && !selBitsNext.robIdx.needFlush(RegNext(io.redirect))), Seq(selBitsNext))
     } else {
       val select = (0 until numSelectGroups).map(g => {
-        val (selValid, selBits) = selectPartialOldest(selectValidGroups(g), selectBitsGroups(g))
-        val selValidNext = RegNext(selValid(0))
-        val selBitsNext = RegEnable(selBits(0), selValid(0))
-        (selValidNext && !selBitsNext.uop.robIdx.needFlush(io.redirect) && !selBitsNext.uop.robIdx.needFlush(RegNext(io.redirect)), selBitsNext)
+        val selectModule = Module(new SelectOldest(bits.head.cloneType, selectValidGroups(g).length, isOlderFu).suggestName(s"selectModule_level_${level}_group_${g}"))
+        selectModule.io.in.zipWithIndex.map{case (sink, i) =>
+          sink.valid := selectValidGroups(g)(i)
+          sink.bits := selectBitsGroups(g)(i)
+        }
+        val selValid = selectModule.io.out.valid
+        val selBits = selectModule.io.out.bits
+        val selValidNext = RegNext(selValid)
+        val selBitsNext = RegEnable(selBits, selValid)
+        (selValidNext && !selBitsNext.robIdx.needFlush(io.redirect) && !selBitsNext.robIdx.needFlush(RegNext(io.redirect)), selBitsNext)
       })
-      selectOldest(select.map(_._1), select.map(_._2))
+      selectOldestByGroup(select.map(_._1), select.map(_._2), level + 1, isOlderFu)
     }
   }
 
@@ -306,24 +320,20 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
       addrMaskMatch(j) && entryNeedCheck(j)
     }))
 
-    val lqViolationSelUopExts = uop.map(uop => {
-      val wrapper = Wire(new XSBundleWithMicroOp)
-      wrapper.uop := uop
-      wrapper
-    })
-
     // select logic
-    val lqSelect: (Seq[Bool], Seq[XSBundleWithMicroOp]) = selectOldest(lqViolationSelVec, lqViolationSelUopExts)
+    val lqSelect: (Seq[Bool], Seq[UopEntry]) = selectOldestByGroup(lqViolationSelVec, uop, 0, isOlder)
 
     // select one inst
     val lqViolation = lqSelect._1(0)
-    val lqViolationUop = lqSelect._2(0).uop
-
-    XSDebug(
-      lqViolation,
-      "need rollback (ld wb before store) pc %x robidx %d target %x\n",
-      storeIn(i).bits.uop.pc, storeIn(i).bits.uop.robIdx.asUInt, lqViolationUop.robIdx.asUInt
-    )
+    val lqViolationUop = lqSelect._2(0)
+
+    if(debugEn) {
+      XSDebug(
+        lqViolation,
+        "need rollback (ld wb before store) pc %x robidx %d target %x\n",
+        storeIn(i).bits.uop.pc.get, storeIn(i).bits.uop.robIdx.asUInt, lqViolationUop.robIdx.asUInt
+      )
+    }
 
     (lqViolation, lqViolationUop)
   }
@@ -331,19 +341,19 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
   // select rollback (part1) and generate rollback request
   // rollback check
   // Lq rollback seq check is done in s3 (next stage), as getting rollbackLq MicroOp is slow
-  val rollbackLqWb = Wire(Vec(StorePipelineWidth, Valid(new DynInst)))
+  val rollbackLqWb = Wire(Vec(StorePipelineWidth, Valid(new UopEntry)))
   val stFtqIdx = Wire(Vec(StorePipelineWidth, new FtqPtr))
   val stFtqOffset = Wire(Vec(StorePipelineWidth, UInt(FetchBlockInstOffsetWidth.W)))
   val stIsRVC = Wire(Vec(StorePipelineWidth, Bool()))
   val stIsFirstIssue = Wire(Vec(StorePipelineWidth, Bool()))
   for (w <- 0 until StorePipelineWidth) {
     val detectedRollback = detectRollback(w)
-    rollbackLqWb(w).valid := detectedRollback._1 && DelayN(storeIn(w).valid && !storeIn(w).bits.miss, TotalSelectCycles)
+    rollbackLqWb(w).valid := detectedRollback._1 && DelayN(storeIn(w).valid && !storeIn(w).bits.tlbMiss, TotalSelectCycles)
     rollbackLqWb(w).bits  := detectedRollback._2
     stFtqIdx(w) := DelayNWithValid(storeIn(w).bits.uop.ftqPtr, storeIn(w).valid, TotalSelectCycles)._2
     stFtqOffset(w) := DelayNWithValid(storeIn(w).bits.uop.ftqOffset, storeIn(w).valid, TotalSelectCycles)._2
     stIsRVC(w) := DelayNWithValid(storeIn(w).bits.uop.isRVC, storeIn(w).valid, TotalSelectCycles)._2
-    stIsFirstIssue(w) := DelayNWithValid(storeIn(w).bits.isFirstIssue, storeIn(w).valid, TotalSelectCycles)._2 // for perf
+    stIsFirstIssue(w) := DelayNWithValid(storeIn(w).bits.uop.isFirstIssue, storeIn(w).valid, TotalSelectCycles)._2 // for perf
   }
 
   // select rollback (part2), generate rollback request, then fire rollback request
@@ -365,20 +375,13 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     redirect.bits.stFtqOffset := stFtqOffset(i)
     redirect.bits.level       := RedirectLevel.flush
     redirect.bits.target      := rollbackLqWb(i).bits.pc
-    redirect.bits.debug_runahead_checkpoint_id := rollbackLqWb(i).bits.perfDebugInfo.runahead_checkpoint_id
+    redirect.bits.debug_runahead_checkpoint_id := rollbackLqWb(i).bits.debugInfo.runahead_checkpoint_id
     redirect
   })
   io.rollback := allRedirect
 
-  val mdpTrainFilter = (0 until StorePipelineWidth).map(i => {
-    val redirect = Wire(Valid(new Redirect))
-    redirect.bits  := allRedirect(i).bits
-    redirect.valid := allRedirect(i).valid && stIsFirstIssue(i)
-    redirect
-  })
-
-  val oldestOH = Redirect.selectOldestRedirect(mdpTrainFilter)
-  io.mdpTrain := Mux1H(oldestOH, mdpTrainFilter)
+  val oldestOH = Redirect.selectOldestRedirect(allRedirect)
+  io.mdpTrain := Mux1H(oldestOH, allRedirect)
 
   // perf cnt
   val canEnqCount = PopCount(io.query.map(_.req.fire))
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index 20887e7c6c5..b15a6ecebf8 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -18,17 +18,11 @@ package xiangshan.mem
 import org.chipsalliance.cde.config._
 import chisel3._
 import chisel3.util._
-import utils._
 import utility._
 import xiangshan._
 import xiangshan.ExceptionNO._
-import xiangshan.frontend.ftq.FtqPtr
-import xiangshan.backend.rob.{RobLsqIO, RobPtr}
-import xiangshan.backend.fu.fpu.FPU
-import xiangshan.backend.fu.FuConfig._
-import xiangshan.backend.Bundles.{DynInst, ExuOutput}
+import xiangshan.backend.Bundles.{DynInst, ExuOutput, MemExuOutput}
 import xiangshan.mem.Bundles._
-import xiangshan.mem.mdp._
 import xiangshan.cache._
 import xiangshan.cache.wpu.ReplayCarry
 import xiangshan.cache.mmu._
@@ -48,30 +42,34 @@ object LoadReplayCauses {
    * ************************************************************
    *
    */
+  // uncache
+  val C_UNCACHE = 0
+  // storeQueue multi forward invalid
+  val C_SMF = 1
   // st-ld violation re-execute check
-  val C_MA  = 0
+  val C_MA  = 2
   // tlb miss check
-  val C_TM  = 1
+  val C_TM  = 3
   // store-to-load-forwarding check
-  val C_FF  = 2
+  val C_FF  = 4
   // dcache replay check
-  val C_DR  = 3
+  val C_DR  = 5
   // dcache miss check
-  val C_DM  = 4
+  val C_DM  = 6
   // wpu predict fail
-  val C_WF  = 5
-  // dcache bank conflict check
-  val C_BC  = 6
+  val C_WF  = 7
+  // dcache bank conflict check / unalign tail split fail
+  val C_BC  = 8
   // RAR queue accept check
-  val C_RAR = 7
+  val C_RAR = 9
   // RAW queue accept check
-  val C_RAW = 8
+  val C_RAW = 10
   // st-ld violation
-  val C_NK  = 9
+  val C_NK  = 11
   // misalignBuffer Full
-  val C_MF  = 10
+  val C_MF  = 12
   // total causes
-  val allCauses = 11
+  val allCauses = 13
 }
 
 class VecReplayInfo(implicit p: Parameters) extends XSBundle with HasVLSUParameters {
@@ -184,30 +182,32 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     val enq = Vec(LoadPipelineWidth, Flipped(Decoupled(new LqWriteBundle)))
 
     // from sta s1
-    val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new LsPipelineBundle)))
+    val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreAddrIO)))
 
     // from std s1
     val storeDataIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreQueueDataWrite)))
 
     // queue-based replay
-    val replay = Vec(LoadPipelineWidth, Decoupled(new LsPipelineBundle))
-   // val refill = Flipped(ValidIO(new Refill))
-    val tl_d_channel = Input(new DcacheToLduForwardIO)
+    val replay = Vec(LoadPipelineWidth, Decoupled(new LoadReplayIO))
+
+    val loadWakeup = Flipped(ValidIO(new DCacheLoadWakeup()))
 
     // from StoreQueue
     val stAddrReadySqPtr = Input(new SqPtr)
     val stAddrReadyVec   = Input(Vec(StoreQueueSize, Bool()))
     val stDataReadySqPtr = Input(new SqPtr)
     val stDataReadyVec   = Input(Vec(StoreQueueSize, Bool()))
+    val sqDeqPtr         = Input(new SqPtr)
 
+    // from LoadQueueUncache
+    val mmioWakeup = Flipped(ValidIO(new LqPtr()))
+    val ncWakeup = Flipped(ValidIO(new LqPtr()))
     //
     val sqEmpty = Input(Bool())
     val lqFull  = Output(Bool())
     val ldWbPtr = Input(new LqPtr)
     val rarFull = Input(Bool())
     val rawFull = Input(Bool())
-    val loadMisalignFull = Input(Bool())
-    val misalignAllowSpec = Input(Bool())
     val l2_hint  = Input(Valid(new L2ToL1Hint()))
     val tlb_hint = Flipped(new TlbHintIO)
     val tlbReplayDelayCycleCtrl = Vec(4, Input(UInt(ReSelectLen.W)))
@@ -228,6 +228,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   val allocated = RegInit(VecInit(List.fill(LoadQueueReplaySize)(false.B))) // The control signals need to explicitly indicate the initial value
   val scheduled = RegInit(VecInit(List.fill(LoadQueueReplaySize)(false.B)))
   val uop = Reg(Vec(LoadQueueReplaySize, new DynInst))
+  val isNC = RegInit(VecInit(List.fill(LoadQueueReplaySize)(false.B)))
   val vecReplay = Reg(Vec(LoadQueueReplaySize, new VecReplayInfo))
   val vaddrModule = Module(new LqVAddrModule(
     gen = UInt(VAddrBits.W),
@@ -311,7 +312,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     // store address execute
     storeAddrInSameCycleVec(i) := VecInit((0 until StorePipelineWidth).map(w => {
       io.storeAddrIn(w).valid &&
-      !io.storeAddrIn(w).bits.miss &&
+      !io.storeAddrIn(w).bits.tlbMiss &&
       blockSqIdx(i) === io.storeAddrIn(w).bits.uop.sqIdx
     })).asUInt.orR // for better timing
 
@@ -335,6 +336,14 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     stDataDeqVec(i) := allocated(i) && storeDataValidVec(i)
   })
 
+  // mmio/nc issue check
+  val lqIdxMatchMmio = VecInit((0 until LoadQueueReplaySize).map { i =>
+    io.mmioWakeup.valid && io.mmioWakeup.bits === uop(i).lqIdx
+  })
+
+  val lqIdxMatchNc = VecInit((0 until LoadQueueReplaySize).map { i =>
+    io.ncWakeup.valid && io.ncWakeup.bits === uop(i).lqIdx
+  })
   // update blocking condition
   (0 until LoadQueueReplaySize).map(i => {
     // case C_MA
@@ -353,7 +362,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     }
     // case C_DM
     when (cause(i)(LoadReplayCauses.C_DM)) {
-      blocking(i) := Mux(io.tl_d_channel.valid && io.tl_d_channel.mshrid === missMSHRId(i), false.B, blocking(i))
+      blocking(i) := Mux(io.loadWakeup.valid && io.loadWakeup.bits.mshrId === missMSHRId(i), false.B, blocking(i))
     }
     // case C_RAR
     when (cause(i)(LoadReplayCauses.C_RAR)) {
@@ -365,7 +374,16 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     }
     // case C_MF
     when (cause(i)(LoadReplayCauses.C_MF)) {
-      blocking(i) := Mux(!io.loadMisalignFull && (io.misalignAllowSpec || !isAfter(uop(i).lqIdx, io.ldWbPtr)), false.B, blocking(i))
+      assert(false.B) // TODO: remove C_MF
+      blocking(i) := false.B
+    }
+    // case C_UNCACHE
+    when (cause(i)(LoadReplayCauses.C_UNCACHE)) {
+      blocking(i) := Mux(lqIdxMatchMmio(i) || lqIdxMatchNc(i), false.B, blocking(i))
+    }
+    // casue C_SMF
+    when (cause(i)(LoadReplayCauses.C_SMF)) {
+      blocking(i) := Mux(!isAfter(uop(i).sqIdx, io.sqDeqPtr), false.B, blocking(i))
     }
   })
 
@@ -427,7 +445,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   // 2. higher priority load
   // 3. lower priority load
   val s0_loadHigherPriorityReplaySelMask = VecInit((0 until LoadQueueReplaySize).map(i => {
-    val hasHigherPriority = cause(i)(LoadReplayCauses.C_DM) || cause(i)(LoadReplayCauses.C_FF)
+    val hasHigherPriority = cause(i)(LoadReplayCauses.C_DM) || cause(i)(LoadReplayCauses.C_FF) || cause(i)(LoadReplayCauses.C_UNCACHE)
     allocated(i) && !scheduled(i) && !blocking(i) && hasHigherPriority
   })).asUInt // use uint instead vec to reduce verilog lines
   val s0_remLoadHigherPriorityReplaySelMask = VecInit((0 until LoadPipelineWidth).map(rem => getRemBits(s0_loadHigherPriorityReplaySelMask)(rem)))
@@ -499,7 +517,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   def replayCanFire(i: Int) = coldCounter(i) >= 0.U && coldCounter(i) < ColdDownThreshold
   def coldDownNow(i: Int) = coldCounter(i) >= ColdDownThreshold
 
-  val replay_req = Wire(Vec(LoadPipelineWidth, DecoupledIO(new LsPipelineBundle)))
+  val replay_req = Wire(Vec(LoadPipelineWidth, DecoupledIO(new LoadReplayIO())))
 
   for (i <- 0 until LoadPipelineWidth) {
     val s0_can_go = s1_can_go(i) ||
@@ -531,44 +549,51 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   for (i <- 0 until LoadPipelineWidth) {
     val s1_replayIdx = s1_oldestSel(i).bits
     val s2_replayUop = RegEnable(uop(s1_replayIdx), s1_can_go(i))
+    val s2_nc      = RegEnable(isNC(s1_replayIdx), s1_can_go(i))
     val s2_vecReplay = RegEnable(vecReplay(s1_replayIdx), s1_can_go(i))
     val s2_replayMSHRId = RegEnable(missMSHRId(s1_replayIdx), s1_can_go(i))
-    val s2_replacementUpdated = RegEnable(replacementUpdated(s1_replayIdx), s1_can_go(i))
     val s2_missDbUpdated = RegEnable(missDbUpdated(s1_replayIdx), s1_can_go(i))
     val s2_replayCauses = RegEnable(cause(s1_replayIdx), s1_can_go(i))
-    val s2_replayCarry = RegEnable(replayCarryReg(s1_replayIdx), s1_can_go(i))
-    val s2_replayCacheMissReplay = RegEnable(trueCacheMissReplay(s1_replayIdx), s1_can_go(i))
     s2_cancelReplay(i) := s2_replayUop.robIdx.needFlush(io.redirect)
 
     s2_can_go(i) := DontCare
-    replay_req(i).valid             := s2_oldestSel(i).valid
-    replay_req(i).bits              := DontCare
-    replay_req(i).bits.uop          := s2_replayUop
+    val replay_req_vaddr = vaddrModule.io.rdata(i)
+    val replay_req_size = LSUOpType.size(s2_replayUop.fuOpType)
+    replay_req(i).valid := s2_oldestSel(i).valid
+    replay_req(i).bits.entrance := Mux(
+      s2_replayCauses(LoadReplayCauses.C_DM) || s2_replayCauses(LoadReplayCauses.C_UNCACHE),
+      LoadEntrance.replayHiPrio.U,
+      LoadEntrance.replayLoPrio.U
+    )
+    replay_req(i).bits.accessType.instrType := Mux(s2_vecReplay.isvec, InstrType.vector.U, InstrType.scalar.U)
+    replay_req(i).bits.accessType.pftType := DontCare
+    replay_req(i).bits.accessType.pftCoh := DontCare
+    replay_req(i).bits.uop := s2_replayUop
     replay_req(i).bits.uop.exceptionVec(loadAddrMisaligned) := false.B
-    replay_req(i).bits.isvec        := s2_vecReplay.isvec
-    replay_req(i).bits.isLastElem   := s2_vecReplay.isLastElem
-    replay_req(i).bits.is128bit     := s2_vecReplay.is128bit
-    replay_req(i).bits.uop_unit_stride_fof := s2_vecReplay.uop_unit_stride_fof
-    replay_req(i).bits.usSecondInv  := s2_vecReplay.usSecondInv
-    replay_req(i).bits.elemIdx      := s2_vecReplay.elemIdx
-    replay_req(i).bits.alignedType  := s2_vecReplay.alignedType
-    replay_req(i).bits.mbIndex      := s2_vecReplay.mbIndex
-    replay_req(i).bits.elemIdxInsideVd := s2_vecReplay.elemIdxInsideVd
-    replay_req(i).bits.reg_offset   := s2_vecReplay.reg_offset
-    replay_req(i).bits.vecActive    := s2_vecReplay.vecActive
-    replay_req(i).bits.is_first_ele := s2_vecReplay.is_first_ele
-    replay_req(i).bits.mask         := s2_vecReplay.mask
-    replay_req(i).bits.vaddr        := vaddrModule.io.rdata(i)
-    replay_req(i).bits.isFirstIssue := false.B
-    replay_req(i).bits.isLoadReplay := true.B
-    replay_req(i).bits.replayCarry  := s2_replayCarry
-    replay_req(i).bits.mshrid       := s2_replayMSHRId
-    replay_req(i).bits.replacementUpdated := s2_replacementUpdated
+    replay_req(i).bits.vaddr := replay_req_vaddr
+    replay_req(i).bits.fullva := replay_req_vaddr
+    replay_req(i).bits.size := Mux(s2_vecReplay.isvec, s2_vecReplay.alignedType, replay_req_size)
+    replay_req(i).bits.mask := Mux(
+      s2_vecReplay.isvec,
+      s2_vecReplay.mask,
+      genVWmask(replay_req_vaddr, replay_req_size)
+    )
+    replay_req(i).bits.occupySource := DontCare
+    replay_req(i).bits.mshrId.get := s2_replayMSHRId
+    replay_req(i).bits.replayQueueIdx.get := s2_oldestSel(i).bits
+    replay_req(i).bits.cause.get := s2_replayCauses.asTypeOf(replay_req(i).bits.cause.get)
+    replay_req(i).bits.forwardDChannel.get := s2_replayCauses(LoadReplayCauses.C_DM)
+    replay_req(i).bits.uncacheReplay.get := s2_replayCauses(LoadReplayCauses.C_UNCACHE)
+    replay_req(i).bits.ncReplay.get := s2_replayCauses(LoadReplayCauses.C_UNCACHE) && s2_nc
+    replay_req(i).bits.elemIdx.get := s2_vecReplay.elemIdx
+    replay_req(i).bits.mbIndex.get := s2_vecReplay.mbIndex
+    replay_req(i).bits.regOffset.get := s2_vecReplay.reg_offset
+    replay_req(i).bits.elemIdxInsideVd.get := s2_vecReplay.elemIdxInsideVd
+    replay_req(i).bits.vecBaseVaddr.get := DontCare
+    replay_req(i).bits.vecVaddrOffset.get := DontCare
+    replay_req(i).bits.vecTriggerMask.get := DontCare
+    replay_req(i).bits.hasROBEntry := true.B
     replay_req(i).bits.missDbUpdated := s2_missDbUpdated
-    replay_req(i).bits.forward_tlDchannel := s2_replayCauses(LoadReplayCauses.C_DM)
-    replay_req(i).bits.schedIndex   := s2_oldestSel(i).bits
-    replay_req(i).bits.uop.loadWaitStrict := false.B
-    replay_req(i).bits.tlbMiss      := s2_replayCauses(LoadReplayCauses.C_TM)
 
     XSError(replay_req(i).fire && !allocated(s2_oldestSel(i).bits), p"LoadQueueReplay: why replay an invalid entry ${s2_oldestSel(i).bits} ?")
   }
@@ -582,7 +607,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     io.replay(2).valid := false.B
     io.replay(2).bits := DontCare
 
-    val arbiter = Module(new RRArbiter(new LsPipelineBundle, 2))
+    val arbiter = Module(new RRArbiter(new LoadReplayIO(), 2))
     arbiter.io.in(0) <> replay_req(1)
     arbiter.io.in(1) <> replay_req(2)
     io.replay(1) <> arbiter.io.out
@@ -599,9 +624,6 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     }
   }
 
-  // XSDebug(io.refill.valid, "miss resp: paddr:0x%x data %x\n", io.refill.bits.addr, io.refill.bits.data)
-
-
   // init
   freeMaskVec.map(e => e := false.B)
 
@@ -641,6 +663,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
       scheduled(enqIndex) := false.B
       uop(enqIndex)       := enq.bits.uop
       uop(enqIndex).exceptionVec := 0.U.asTypeOf(enq.bits.uop.exceptionVec)
+      isNC(enqIndex)      := enq.bits.nc && enq.bits.rep_info.cause(LoadReplayCauses.C_UNCACHE)
       vecReplay(enqIndex).isvec := enq.bits.isvec
       vecReplay(enqIndex).isLastElem := enq.bits.isLastElem
       vecReplay(enqIndex).is128bit := enq.bits.is128bit
@@ -693,8 +716,8 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
       // special case: dcache miss
       when (replayInfo.cause(LoadReplayCauses.C_DM) && enq.bits.handledByMSHR) {
         blocking(enqIndex) := !replayInfo.full_fwd && //  dcache miss
-                              !(io.tl_d_channel.valid && io.tl_d_channel.mshrid === replayInfo.mshr_id) && // no refill in this cycle
-                              !(RegNext(io.tl_d_channel.valid) && RegNext(io.tl_d_channel.mshrid) === replayInfo.mshr_id) // not refill in last cycle
+                              !(io.loadWakeup.valid && io.loadWakeup.bits.mshrId === replayInfo.mshr_id) && // no refill in this cycle
+                              !(RegNext(io.loadWakeup.valid) && RegNext(io.loadWakeup.bits.mshrId) === replayInfo.mshr_id) // no refill in last cycle
       }
 
       // special case: st-ld violation
@@ -820,6 +843,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   val replayDCacheReplayCount = PopCount(io.enq.map(enq => enq.fire && !enq.bits.isLoadReplay && enq.bits.rep_info.cause(LoadReplayCauses.C_DR)))
   val replayForwardFailCount  = PopCount(io.enq.map(enq => enq.fire && !enq.bits.isLoadReplay && enq.bits.rep_info.cause(LoadReplayCauses.C_FF)))
   val replayDCacheMissCount   = PopCount(io.enq.map(enq => enq.fire && !enq.bits.isLoadReplay && enq.bits.rep_info.cause(LoadReplayCauses.C_DM)))
+  val replayMultiMatchCount   = PopCount(io.enq.map(enq => enq.fire && !enq.bits.isLoadReplay && enq.bits.rep_info.cause(LoadReplayCauses.C_SMF)))
   XSPerfAccumulate("enq", enqNumber)
   XSPerfAccumulate("deq", deqNumber)
   XSPerfAccumulate("deq_block", deqBlockCount)
@@ -835,6 +859,52 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
   XSPerfAccumulate("replay_dcache_miss", replayDCacheMissCount)
   XSPerfAccumulate("replay_hint_wakeup", s0_hintSelValid)
   XSPerfAccumulate("replay_hint_priority_beat1", io.l2_hint.valid && io.l2_hint.bits.isKeyword)
+  XSPerfAccumulate("replay_storeQueue_multi_match", replayMultiMatchCount)
+
+  // replay counter
+  val perfReplayCounter = RegInit(VecInit(Seq.fill(LoadQueueReplaySize)(0.U(8.W))))
+  for((enq, i) <- io.enq.zipWithIndex){
+    //  Allocated ready
+    val offset = PopCount(newEnqueue.take(i))
+    val enqIndex = freeList.io.allocateSlot(offset)
+
+    when(newEnqueue(i) && enq.ready) { // first enqueue
+      perfReplayCounter(enqIndex) := 1.U
+    }
+
+    val schedIndex = enq.bits.schedIndex
+    when (enq.valid && enq.bits.isLoadReplay && needReplay(i)) { // re-relpay
+       perfReplayCounter(schedIndex) := perfReplayCounter(schedIndex) + 1.U
+    }
+
+    val enable = enq.valid && enq.bits.isLoadReplay && !needReplay(i) && allocated(schedIndex)
+    val replayCounter = LookupTree(schedIndex, perfReplayCounter.zipWithIndex.map{case (d, v) => (v.U, d)})
+    XSPerfHistogram(s"load_replay_count_${i}", replayCounter, enable, 1, 16, 1)
+  }
+
+  // count the number of each cause replay over 4 times.
+  val replayTlbMissOver4Count      = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_TM)))
+  val replayMemAmbOver4Count       = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_MA)))
+  val replayNukeOver4Count         = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_NK)))
+  val replayRARRejectOver4Count    = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_RAR)))
+  val replayRAWRejectOver4Count    = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_RAW)))
+  val replayBankConflictOver4Count = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_BC)))
+  val replayDCacheReplayOver4Count = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_DR)))
+  val replayForwardFailOver4Count  = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_FF)))
+  val replayDCacheMissOver4Count   = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_DM)))
+  val replayMultiMatchOver4Count   = PopCount(io.enq.map(enq => enq.valid && enq.bits.isLoadReplay && !enq.bits.rep_info.need_rep && (perfReplayCounter(enq.bits.schedIndex) > 4.U) && cause(enq.bits.schedIndex)(LoadReplayCauses.C_SMF)))
+
+  XSPerfAccumulate("replay_rar_nack_over4_times", replayRARRejectOver4Count)
+  XSPerfAccumulate("replay_raw_nack_over4_times", replayRAWRejectOver4Count)
+  XSPerfAccumulate("replay_nuke_over4_times", replayNukeOver4Count)
+  XSPerfAccumulate("replay_mem_amb_over4_times", replayMemAmbOver4Count)
+  XSPerfAccumulate("replay_tlb_miss_over4_times", replayTlbMissOver4Count)
+  XSPerfAccumulate("replay_bank_conflict_over4_times", replayBankConflictOver4Count)
+  XSPerfAccumulate("replay_dcache_replay_over4_times", replayDCacheReplayOver4Count)
+  XSPerfAccumulate("replay_forward_fail_over4_times", replayForwardFailOver4Count)
+  XSPerfAccumulate("replay_dcache_miss_over4_times", replayDCacheMissOver4Count)
+  XSPerfAccumulate("replay_storeQueue_multi_match_over4_times", replayMultiMatchOver4Count)
+
 
   val perfEvents: Seq[(String, UInt)] = Seq(
     ("enq", enqNumber),
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index 828a199a7f5..f222f0b568f 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -50,9 +50,11 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     // from ldu
     val req = Flipped(Valid(new LqWriteBundle))
     // to ldu: mmio, data
+    val mmioWakeup = DecoupledIO(new LqPtr)
     val mmioOut = DecoupledIO(new MemExuOutput)
     val mmioRawData = Output(new LoadDataFromLQBundle)
     // to ldu: nc with data
+    val ncWakeup = DecoupledIO(new LqPtr)
     val ncOut = DecoupledIO(new LsPipelineBundle)
     // <=> uncache
     val uncache = new UncacheWordIO
@@ -65,12 +67,11 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
   val slaveAccept = RegInit(false.B)
   val slaveId = Reg(UInt(UncacheBufferIndexWidth.W))
 
-  val s_idle :: s_req :: s_resp :: s_wait :: Nil = Enum(4)
+  val s_idle :: s_req :: s_resp :: s_wakeup :: s_wait :: Nil = Enum(5)
   val uncacheState = RegInit(s_idle)
   val uncacheData = Reg(io.uncache.resp.bits.data.cloneType)
   val nderr = RegInit(false.B)
-  val denied = RegInit(false.B)
-  val corrupt = RegInit(false.B)
+  val derr = RegInit(false.B)
 
   val writeback = Mux(req.nc, io.ncOut.fire, io.mmioOut.fire)
   val slaveAck = req_valid && io.uncache.idResp.valid && io.uncache.idResp.bits.mid === entryIndex.U
@@ -99,8 +100,7 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     slaveAccept := false.B
     req := io.req.bits
     nderr := false.B
-    denied := false.B
-    corrupt := false.B
+    derr := false.B
   } .elsewhen(slaveAck) {
     slaveAccept := true.B
     slaveId := io.uncache.idResp.bits.sid
@@ -147,10 +147,18 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
           uncacheState := s_idle
           flush := true.B
         }.otherwise{
-          uncacheState := s_wait
+          uncacheState := s_wakeup
         }
       }
     }
+    is (s_wakeup) {
+      when (needFlush || needFlushReg) {
+        uncacheState := s_idle
+        flush := true.B
+      }.elsewhen (io.mmioWakeup.fire || io.ncWakeup.fire) {
+        uncacheState := s_wait
+      }
+    }
     is (s_wait) {
       when (needFlush || writeback) {
         uncacheState := s_idle
@@ -161,8 +169,7 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
 
   /* control */
   io.flush := flush
-  io.rob.mmio := DontCare
-  io.rob.uop := DontCare
+  io.rob.mmioBusy := DontCare // will be assign later!
   io.mmioSelect := (uncacheState =/= s_idle) && req.mmio
   io.slaveId.valid := slaveAccept
   io.slaveId.bits := slaveId
@@ -176,8 +183,6 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
   io.uncache.req.bits.vaddr:= req.vaddr
   io.uncache.req.bits.mask := Mux(req.paddr(3), req.mask(15, 8), req.mask(7, 0))
   io.uncache.req.bits.id   := entryIndex.U
-  io.uncache.req.bits.instrtype := DontCare
-  io.uncache.req.bits.replayCarry := DontCare
   io.uncache.req.bits.robIdx := req.uop.robIdx
   io.uncache.req.bits.nc := req.nc
   io.uncache.req.bits.memBackTypeMM := req.memBackTypeMM
@@ -188,8 +193,7 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
   when (io.uncache.resp.fire) {
     uncacheData := io.uncache.resp.bits.data
     nderr := io.uncache.resp.bits.nderr
-    denied := io.uncache.resp.bits.denied
-    corrupt := io.uncache.resp.bits.corrupt
+    derr := io.uncache.resp.bits.error
   }
 
   /* uncache writeback */
@@ -204,8 +208,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.ncOut.bits := DontCare
     io.ncOut.bits.uop := req.uop
     io.ncOut.bits.uop.lqIdx := req.uop.lqIdx
-    io.ncOut.bits.uop.exceptionVec(hardwareError) := corrupt && !denied
-    io.ncOut.bits.uop.exceptionVec(loadAccessFault) := denied
+    io.ncOut.bits.uop.exceptionVec(loadAccessFault) := nderr
+    io.ncOut.bits.uop.exceptionVec(hardwareError) := derr
     io.ncOut.bits.data := uncacheData
     io.ncOut.bits.paddr := req.paddr
     io.ncOut.bits.vaddr := req.vaddr
@@ -220,8 +224,8 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.mmioOut.bits := DontCare
     io.mmioOut.bits.uop := req.uop
     io.mmioOut.bits.uop.lqIdx := req.uop.lqIdx
-    io.mmioOut.bits.uop.exceptionVec(hardwareError) := corrupt && !denied
-    io.mmioOut.bits.uop.exceptionVec(loadAccessFault) := denied
+    io.mmioOut.bits.uop.exceptionVec(loadAccessFault) := nderr
+    io.mmioOut.bits.uop.exceptionVec(hardwareError) := derr
     io.mmioOut.bits.data := uncacheData
     io.mmioOut.bits.debug.isMMIO := true.B
     io.mmioOut.bits.debug.isNCIO := false.B
@@ -231,11 +235,15 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.mmioRawData.uop := req.uop
     io.mmioRawData.addrOffset := req.paddr
   }
+  io.ncWakeup.valid := uncacheState === s_wakeup && req.nc && !needFlush && !needFlushReg
+  io.ncWakeup.bits := req.uop.lqIdx
+  io.mmioWakeup.valid := uncacheState === s_wakeup && req.mmio && !needFlush && !needFlushReg
+  io.mmioWakeup.bits := req.uop.lqIdx
 
   io.exception.valid := writeback
   io.exception.bits := req
-  io.exception.bits.uop.exceptionVec(hardwareError) := corrupt && !denied
-  io.exception.bits.uop.exceptionVec(loadAccessFault) := denied
+  io.exception.bits.uop.exceptionVec(loadAccessFault) := nderr
+  io.exception.bits.uop.exceptionVec(hardwareError) := derr
 
   /* debug log */
   XSDebug(io.uncache.req.fire,
@@ -277,18 +285,18 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
     // enqueue: from ldu s3
     val req = Vec(LoadPipelineWidth, Flipped(Decoupled(new LqWriteBundle)))
     // writeback: mmio to ldu s0, s3
-    val mmioOut = Vec(LoadPipelineWidth, DecoupledIO(new MemExuOutput))
-    val mmioRawData = Vec(LoadPipelineWidth, Output(new LoadDataFromLQBundle))
+    val mmioWakeup = ValidIO(new LqPtr)
     // writeback: nc to ldu s0--s3
-    val ncOut = Vec(LoadPipelineWidth, Decoupled(new LsPipelineBundle))
+    val ncWakeup = ValidIO(new LqPtr)
     // <=>uncache
     val uncache = new UncacheWordIO
-
+    // to ldu: mmio/nc data
+    val bypass = Flipped(Vec(LoadPipelineWidth, new UncacheBypass))
     /* except */
     // rollback from frontend when buffer is full
     val rollback = Output(Valid(new Redirect))
     // exception generated by outer bus
-    val exception = Valid(new LqWriteBundle)
+    val exceptionInfo = ValidIO(new MemExceptionInfo())
   })
 
   /******************************************************************
@@ -321,14 +329,6 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
   io.uncache.req.valid := false.B
   io.uncache.req.bits := DontCare
   io.uncache.resp.ready := false.B
-  for (w <- 0 until LoadPipelineWidth) {
-    io.mmioOut(w).valid := false.B
-    io.mmioOut(w).bits := DontCare
-    io.mmioRawData(w) := DontCare
-    io.ncOut(w).valid := false.B
-    io.ncOut(w).bits := DontCare
-  }
-
 
   /******************************************************************
    * Enqueue
@@ -355,7 +355,8 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
     !s2_req(i).uop.robIdx.needFlush(io.redirect)
   })
   val s2_has_exception = s2_req.map(x => ExceptionNO.selectByFu(x.uop.exceptionVec, LduCfg).asUInt.orR)
-  val s2_need_replay = s2_req.map(_.rep_info.need_rep)
+  val s2_need_replay = s2_req.map { req =>
+     req.rep_info.need_rep && !req.rep_info.mmioOrNc}
 
   for (w <- 0 until LoadPipelineWidth) {
     s2_enqueue(w) := s2_valid(w) && !s2_has_exception(w) && !s2_need_replay(w) && (s2_req(w).mmio || s2_req(w).nc)
@@ -395,9 +396,6 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
   // TODO lyq: It's best to choose in robIdx order / the order in which they enter
   val ncReqArb = Module(new RRArbiterInit(io.uncache.req.bits.cloneType, LoadUncacheBufferSize))
 
-  val mmioOut = Wire(DecoupledIO(io.mmioOut(0).bits.cloneType))
-  val mmioRawData = Wire(io.mmioRawData(0).cloneType)
-  val ncOut = Wire(chiselTypeOf(io.ncOut))
   val ncOutValidVec = VecInit(entries.map(e => e.io.ncOut.valid))
   val ncOutValidVecRem = SubVec.getMaskRem(ncOutValidVec, NC_WB_MOD)
 
@@ -406,17 +404,10 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
   uncacheReq.bits  := DontCare
   mmioReq.valid := false.B
   mmioReq.bits := DontCare
-  mmioOut.valid := false.B
-  mmioOut.bits := DontCare
-  mmioRawData := DontCare
   for (i <- 0 until LoadUncacheBufferSize) {
     ncReqArb.io.in(i).valid := false.B
     ncReqArb.io.in(i).bits := DontCare
   }
-  for (i <- 0 until LoadPipelineWidth) {
-    ncOut(i).valid := false.B
-    ncOut(i).bits := DontCare
-  }
 
   entries.zipWithIndex.foreach {
     case (e, i) =>
@@ -437,27 +428,10 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
         mmioReq.valid := e.io.uncache.req.valid
         mmioReq.bits := e.io.uncache.req.bits
         e.io.uncache.req.ready := mmioReq.ready
-
-        e.io.mmioOut.ready := mmioOut.ready
-        mmioOut.valid := e.io.mmioOut.valid
-        mmioOut.bits := e.io.mmioOut.bits
-        mmioRawData := e.io.mmioRawData
-
       }.otherwise{
         ncReqArb.io.in(i).valid := e.io.uncache.req.valid
         ncReqArb.io.in(i).bits := e.io.uncache.req.bits
         e.io.uncache.req.ready := ncReqArb.io.in(i).ready
-
-        (0 until NC_WB_MOD).map { w =>
-          val (idx, ncOutValid) = PriorityEncoderWithFlag(ncOutValidVecRem(w))
-          val port = NCWBPorts(w)
-          when((i.U === idx) && ncOutValid) {
-            ncOut(port).valid := ncOutValid
-            ncOut(port).bits := e.io.ncOut.bits
-            e.io.ncOut.ready := ncOut(port).ready
-          }
-        }
-
       }
 
       // uncache idResp
@@ -483,24 +457,101 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
   // uncache Request
   AddPipelineReg(uncacheReq, io.uncache.req, false.B)
 
-  // uncache Writeback
-  AddPipelineReg(mmioOut, io.mmioOut(UncacheWBPort), false.B)
-  io.mmioRawData(UncacheWBPort) := RegEnable(mmioRawData, mmioOut.fire)
-
-  (0 until LoadPipelineWidth).foreach { i => AddPipelineReg(ncOut(i), io.ncOut(i), false.B) }
-
+  // uncache Wakeup & Writeback
+  val mmioWakeup = Wire(DecoupledIO(new LqPtr()))
+  val ncWakeup = Wire(DecoupledIO(new LqPtr()))
+  io.mmioWakeup.valid := mmioWakeup.valid
+  io.mmioWakeup.bits := mmioWakeup.bits
+  mmioWakeup.ready := true.B
+  io.ncWakeup.valid := ncWakeup.valid
+  io.ncWakeup.bits := ncWakeup.bits
+  ncWakeup.ready := true.B
+  arbiter(entries.map(_.io.mmioWakeup), mmioWakeup, Some("mmioWakeup"))
+  arbiter(entries.map(_.io.ncWakeup), ncWakeup, Some("ncWakeup"))
   // uncache exception
-  io.exception.valid := Cat(entries.map(_.io.exception.valid)).orR
-  io.exception.bits := ParallelPriorityMux(entries.map(e =>
+  val exceptionEntry = ParallelPriorityMux(entries.map(e =>
     (e.io.exception.valid, e.io.exception.bits)
   ))
+  io.exceptionInfo.valid := Cat(entries.map(_.io.exception.valid)).orR
+  io.exceptionInfo.bits.robIdx       := exceptionEntry.uop.robIdx
+  io.exceptionInfo.bits.exceptionVec := ExceptionNO.selectByFu(exceptionEntry.uop.exceptionVec, LduCfg)
+  io.exceptionInfo.bits.vaddr        := exceptionEntry.fullva
+  io.exceptionInfo.bits.gpaddr       := exceptionEntry.gpaddr
+  io.exceptionInfo.bits.isForVSnonLeafPTE := exceptionEntry.isForVSnonLeafPTE
+  io.exceptionInfo.bits.vaNeedExt    := true.B
+  io.exceptionInfo.bits.isHyper      := exceptionEntry.isHyper
+  io.exceptionInfo.bits.uopIdx       := 0.U.asTypeOf(io.exceptionInfo.bits.uopIdx)
+  io.exceptionInfo.bits.vl           := 0.U.asTypeOf(io.exceptionInfo.bits.vl)
+  io.exceptionInfo.bits.vstart       := 0.U.asTypeOf(io.exceptionInfo.bits.vstart)
+
+  // rob, for commit-stuck detect
+  io.rob.mmioBusy := mmioSelect
+
+  /******************************************************************
+   * Forward Logic
+   * 
+   * s1 response paddr, s2 response forwardData
+   ******************************************************************/
+
+  val ncMatch = Wire(Vec(LoadPipelineWidth, Vec(LoadUncacheBufferSize, Bool())))
+  val mmioMatch = Wire(Vec(LoadPipelineWidth, Vec(LoadUncacheBufferSize, Bool())))
+  for (w <- 0 until LoadPipelineWidth) {
+    val matchedPaddr = WireInit(0.U(PAddrBits.W))
+    val matchedData = WireInit(0.U(XLEN.W))
+    val matchednderr = WireInit(false.B)
+    val matchedderr = WireInit(false.B)
+    val matchedAddrOffset = WireInit(0.U(3.W))
+
+    entries.zipWithIndex.foreach {
+      case (e, i) =>
+        // TODO: use the same lqIdx for ncOut and mmioOut
+        ncMatch(w)(i) := e.io.ncOut.valid &&
+          io.bypass(w).s0Req.valid && io.bypass(w).s0Req.bits.isNCReplay &&
+          e.io.ncOut.bits.uop.lqIdx === io.bypass(w).s0Req.bits.lqIdx
+
+        mmioMatch(w)(i) := e.io.mmioOut.valid &&
+          io.bypass(w).s0Req.valid && io.bypass(w).s0Req.bits.isMMIOReplay &&
+          e.io.mmioOut.bits.uop.lqIdx === io.bypass(w).s0Req.bits.lqIdx
+    }
+
+    val respNCMatch = ncMatch(w).asUInt.orR
+    val respMMIOMatch = mmioMatch(w).asUInt.orR
+    val respMatch = respNCMatch || respMMIOMatch
+    val respMatchNCOut = ParallelPriorityMux(ncMatch(w), entries.map(_.io.ncOut.bits))
+    val respMatchMMIOOut = ParallelPriorityMux(mmioMatch(w), entries.map(_.io.mmioOut.bits))
+    val respMatchMMIOData = ParallelPriorityMux(mmioMatch(w), entries.map(_.io.mmioRawData))
+
+    when (respNCMatch) {
+      matchedPaddr := respMatchNCOut.paddr
+      matchedData := respMatchNCOut.data
+      matchednderr := respMatchNCOut.uop.exceptionVec(loadAccessFault)
+      matchedderr := respMatchNCOut.uop.exceptionVec(hardwareError)
+    }.elsewhen (respMMIOMatch) {
+      matchedPaddr := respMatchMMIOOut.debug.paddr
+      matchedData := respMatchMMIOData.lqData
+      matchednderr := respMatchMMIOOut.uop.exceptionVec(loadAccessFault)
+      matchedderr := respMatchMMIOOut.uop.exceptionVec(hardwareError)
+      matchedAddrOffset := respMatchMMIOData.addrOffset
+    }
 
-  // rob
-  for (i <- 0 until LoadPipelineWidth) {
-    io.rob.mmio(i) := RegNext(s1_valid(i) && s1_req(i).mmio)
-    io.rob.uop(i) := RegEnable(s1_req(i).uop, s1_valid(i))
+    val s1RespValid = RegNext(respMatch)
+    val s1RespData = RegEnable(matchedData, respMatch)
+    val s1RespNderr = RegEnable(matchednderr, respMatch)
+    val s1RespDerr = RegEnable(matchedderr, respMatch)
+    val s2RespValid = RegNext(s1RespValid)
+    io.bypass(w).s1Resp.valid := s1RespValid
+    io.bypass(w).s1Resp.bits.paddr := RegEnable(matchedPaddr, respMatch)
+    io.bypass(w).s2Resp.valid := s2RespValid
+    io.bypass(w).s2Resp.bits.data := RegEnable(Fill(VLEN / XLEN, s1RespData), s1RespValid)
+    io.bypass(w).s2Resp.bits.nderr := RegEnable(s1RespNderr, s1RespValid)
+    io.bypass(w).s2Resp.bits.derr := RegEnable(s1RespDerr, s1RespValid)
   }
 
+  entries.zipWithIndex.foreach {
+    case (e, i) =>
+      e.io.ncOut.ready := Cat(ncMatch.map(_(i))).orR
+      e.io.mmioOut.ready := Cat(mmioMatch.map(_(i))).orR
+  }
 
   /******************************************************************
    * Deallocate
@@ -546,15 +597,6 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
    *                     rollback req
    *
    ******************************************************************/
-  def selectOldestRedirect(xs: Seq[Valid[Redirect]]): Vec[Bool] = {
-    val compareVec = (0 until xs.length).map(i => (0 until i).map(j => isAfter(xs(j).bits.robIdx, xs(i).bits.robIdx)))
-    val resultOnehot = VecInit((0 until xs.length).map(i => Cat((0 until xs.length).map(j =>
-      (if (j < i) !xs(j).valid || compareVec(i)(j)
-      else if (j == i) xs(i).valid
-      else !xs(j).valid || !compareVec(j)(i))
-    )).andR))
-    resultOnehot
-  }
   val reqNeedCheck = VecInit((0 until LoadPipelineWidth).map(w =>
     s2_enqueue(w) && !s2_enqValidVec(w)
   ))
@@ -572,7 +614,7 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
     redirect.bits.debug_runahead_checkpoint_id := reqSelUops(i).perfDebugInfo.runahead_checkpoint_id
     redirect
   })
-  val oldestOneHot = selectOldestRedirect(allRedirect)
+  val oldestOneHot = Redirect.selectOldestRedirect(allRedirect)
   val oldestRedirect = Mux1H(oldestOneHot, allRedirect)
   val lastCycleRedirect = Wire(Valid(new Redirect))
   lastCycleRedirect.valid := RegNext(io.redirect.valid)
@@ -595,20 +637,16 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
   QueuePerf(LoadUncacheBufferSize, validCount, !allowEnqueue)
 
   XSPerfAccumulate("mmio_uncache_req", io.uncache.req.fire && !io.uncache.req.bits.nc)
-  XSPerfAccumulate("mmio_writeback_success", io.mmioOut(0).fire)
-  XSPerfAccumulate("mmio_writeback_blocked", io.mmioOut(0).valid && !io.mmioOut(0).ready)
+  XSPerfAccumulate("mmio_bypass", PopCount(io.bypass.map(x => x.s0Req.valid && x.s0Req.bits.isMMIOReplay)))
   XSPerfAccumulate("nc_uncache_req", io.uncache.req.fire && io.uncache.req.bits.nc)
-  XSPerfAccumulate("nc_writeback_success", io.ncOut(0).fire)
-  XSPerfAccumulate("nc_writeback_blocked", io.ncOut(0).valid && !io.ncOut(0).ready)
+  XSPerfAccumulate("nc_bypass", PopCount(io.bypass.map(x => x.s0Req.valid && x.s0Req.bits.isNCReplay)))
   XSPerfAccumulate("uncache_full_rollback", io.rollback.valid)
 
   val perfEvents: Seq[(String, UInt)] = Seq(
     ("mmio_uncache_req", io.uncache.req.fire && !io.uncache.req.bits.nc),
-    ("mmio_writeback_success", io.mmioOut(0).fire),
-    ("mmio_writeback_blocked", io.mmioOut(0).valid && !io.mmioOut(0).ready),
+    ("mmio_bypass", PopCount(io.bypass.map(x => x.s0Req.valid && x.s0Req.bits.isMMIOReplay))),
     ("nc_uncache_req", io.uncache.req.fire && io.uncache.req.bits.nc),
-    ("nc_writeback_success", io.ncOut(0).fire),
-    ("nc_writeback_blocked", io.ncOut(0).valid && !io.ncOut(0).ready),
+    ("nc_bypass", PopCount(io.bypass.map(x => x.s0Req.valid && x.s0Req.bits.isNCReplay))),
     ("uncache_full_rollback", io.rollback.valid)
   )
   // end
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
new file mode 100644
index 00000000000..fe3233214d0
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -0,0 +1,2091 @@
+/***************************************************************************************
+ * Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+ * Copyright (c) 2020-2021 Peng Cheng Laboratory
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          https://license.coscl.org.cn/MulanPSL2
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package xiangshan.mem
+
+import chisel3._
+import chisel3.util._
+import difftest._
+import difftest.common.DifftestMem
+import org.chipsalliance.cde.config.Parameters
+import top.ArgParser
+import utility._
+import xiangshan.ExceptionNO.{hardwareError, storeAccessFault}
+import xiangshan._
+import xiangshan.backend.Bundles._
+import xiangshan.backend.exu.ExeUnitParams
+import xiangshan.backend.fu.FuConfig.StaCfg
+import xiangshan.backend.fu.FuType
+import xiangshan.backend.rob.RobPtr
+import xiangshan.cache.{DCacheWordReqWithVaddrAndPfFlag, MemoryOpConstants, UncacheWordIO}
+import xiangshan.mem.Bundles.SQForward
+
+class SqPtr(implicit p: Parameters) extends CircularQueuePtr[SqPtr](
+  p => p(XSCoreParamsKey).StoreQueueSize
+){
+}
+
+object SqPtr {
+  def apply(f: Bool, v: UInt)(implicit p: Parameters): SqPtr = {
+    val ptr = Wire(new SqPtr)
+    ptr.flag := f
+    ptr.value := v
+    ptr
+  }
+}
+
+// Unalign queue Ptr
+class UqPtr(implicit p: Parameters) extends CircularQueuePtr[UqPtr](
+  p => p(XSCoreParamsKey).SQUnalignQueueSize
+){
+}
+
+object UqPtr {
+  def apply(f: Bool, v: UInt)(implicit p: Parameters): UqPtr = {
+    val ptr = Wire(new UqPtr)
+    ptr.flag := f
+    ptr.value := v
+    ptr
+  }
+}
+
+class DataQueuePtr(implicit p: Parameters) extends CircularQueuePtr[DataQueuePtr](
+  p => p(XSCoreParamsKey).EnsbufferWidth
+){
+}
+
+object DataQueuePtr {
+  def apply(f: Bool, v: UInt)(implicit p: Parameters): DataQueuePtr = {
+    val ptr = Wire(new DataQueuePtr)
+    ptr.flag := f
+    ptr.value := v
+    ptr
+  }
+}
+
+
+// don't need to initial
+class SQDataEntryBundle(implicit p: Parameters) extends MemBlockBundle {
+  class UopInfo(implicit p: Parameters) extends MemBlockBundle {
+    // load inst will not be executed until former store (predicted by mdp) addr calcuated
+    val loadWaitBit      = Bool()
+    // If (loadWaitBit && loadWaitStrict), strict load wait is needed
+    // load inst will not be executed until ALL former store addr calcuated
+    val loadWaitStrict   = Bool()
+    val ssid             = UInt(SSIDWidth.W)
+    val storeSetHit      = Bool() // inst has been allocated an store set
+
+    val robIdx           = new RobPtr
+    val uopIdx           = UopIdx()
+
+  }
+  val uop                = new UopInfo
+  val size               = UInt(MemorySize.Size.width.W)
+  // data storage
+  val vaddr                    = UInt(VAddrBits.W)
+  val paddrHigh                = UInt((PAddrBits - pageOffset).W) //don't need to storage low 12 bit, which is same as vaddr(11, 0)
+  val byteMask                 = UInt((VLEN/8).W)
+  def paddr :UInt              = Cat(paddrHigh, vaddr(pageOffset - 1, 0))
+  val data                     = UInt(VLEN.W)
+
+  def byteStart: UInt          = vaddr(log2Ceil(VLEN/8) - 1, 0)
+  def byteEnd: UInt            = byteStart + MemorySize.ByteOffset(size)
+
+  val memoryType               = MemoryType()
+  val cboType                  = CboType()
+  val prefetch                 = Bool() //TODO: need it ?
+
+  // debug signal
+  val debugPaddr               = Option.when(debugEn)(UInt((PAddrBits).W))
+  val debugVaddr               = Option.when(debugEn)(UInt((VAddrBits).W))
+  val debugData                = Option.when(debugEn)(UInt((XLEN).W))
+  // only use for unit-stride difftest
+  val debugVecUnalignedStart   = Option.when(debugEn)(UInt((log2Up(XLEN)).W))
+  val debugVecUnalignedOffset  = Option.when(debugEn)(UInt((log2Up(XLEN)).W))
+  val debugUop                 = Option.when(debugEn)(new DynInst())
+
+}
+
+// need initial when reset
+class SQCtrlEntryBundle(implicit p: Parameters) extends MemBlockBundle {
+  val dataValid          = Bool()
+  val addrValid          = Bool()
+
+  val waitStoreS2        = Bool() //TODO: will be remove in the feature
+  val isVec              = Bool() // TODO: need it ?
+  // vecInactive indicate storage a inactive vector element, it will not write to Sbuffer. written when vector split.
+  val vecInactive        = Bool()
+  val cross16Byte        = Bool()
+  val hasException       = Bool()
+  val committed          = Bool()
+  val allocated          = Bool()
+  val handleFinish       = Bool() // this signal is for deqPtr move, true.B indicate NC/MMIO/cbo request can deq
+
+  val isCbo              = Bool() // Indicate if is cbo request, true is cbo request
+  val vecMbCommit        = Bool() //TODO: request was committed by MergeBuffer, will be remove in the future.
+
+  //debug information
+  val unalignWithin16Byte = Option.when(debugEn)(Bool())
+
+  def allValid: Bool     = dataValid && addrValid
+}
+
+class UnalignBufferEntry(implicit p: Parameters) extends MemBlockBundle {
+  val paddrHigh          = UInt((PAddrBits - pageOffset).W)
+  def paddr :UInt        = Cat(paddrHigh, 0.U(pageOffset.W))
+  val robIdx             = new RobPtr
+  val sqIdx              = new SqPtr
+}
+
+class WriteToSbufferReqEntry(implicit p: Parameters) extends MemBlockBundle {
+  val addr         = UInt(PAddrBits.W)
+  val prefetch     = Bool()
+  val vecValid     = Bool() //TODO: need to remove.
+  val wline        = Bool()
+  val vaddr        = UInt(VAddrBits.W)
+  val data         = UInt(VLEN.W)
+  val mask         = UInt((VLEN/8).W)
+}
+
+abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
+
+  def isMmio(in: UInt): Bool = {
+    require(in.getWidth == MemoryType.width)
+    MemoryType.isMMIO(in)
+  }
+  def isPbmtIO(in: UInt): Bool = {
+    require(in.getWidth == MemoryType.width)
+    MemoryType.isPbmtIO(in)
+  }
+  // is pbmt nc
+  def isPbmtNC(in: UInt): Bool = {
+    require(in.getWidth == MemoryType.width)
+    MemoryType.isPbmtNC(in)
+  }
+  // is cacheable
+  def isCacheable(in: UInt): Bool = {
+    require(in.getWidth == MemoryType.width)
+    MemoryType.isCacheable(in)
+  }
+  // is cbo zero
+  def isCboZero(in: UInt): Bool = {
+    require(in.getWidth == CboType.width)
+    CboType.isCboZero(in)
+  }
+
+  def isCboClean(in: UInt): Bool = {
+    require(in.getWidth == CboType.width)
+    CboType.isCboClean(in)
+  }
+
+  def isCboFlush(in: UInt): Bool = {
+    require(in.getWidth == CboType.width)
+    CboType.isCboFlush(in)
+  }
+
+  def isCboInval(in: UInt): Bool = {
+    require(in.getWidth == CboType.width)
+    CboType.isCboInval(in)
+  }
+
+  /**
+   * Circular Right Shift [step] byte
+   * */
+  def rotateByteRight(in: UInt, step: Int): UInt = {
+    val maxLen = in.getWidth
+    if(step == 0) in
+    else Cat(in(step - 1, 0), in(maxLen - 1, step))
+  }
+
+  val param = staParams.head
+  param.bindBackendParam(backendParams)
+
+//  val DCacheVWordBytes  = VLEN / 8
+//  val DCacheVWordOffset = log2Up(DCacheVWordBytes)
+  val DCacheLineBytes   = CacheLineSize / 8
+  val DCacheLineVWords  = DCacheLineBytes / DCacheVWordBytes
+  val DCacheLineVWordsOffset = log2Up(DCacheLineVWords)
+  val VWordOffset            = log2Up(VLENB)
+
+  private class ForwardModule(val param: ExeUnitParams)(implicit p: Parameters) extends LSQModule {
+    val io = IO(new Bundle {
+      val query           = Flipped(Vec(LoadPipelineWidth, new SQForward))
+      val dataEntriesIn   = Vec(StoreQueueSize, Input(new SQDataEntryBundle())) // from storeQueue data
+      val ctrlEntriesIn   = Vec(StoreQueueSize, Input(new SQCtrlEntryBundle())) // from storeQueue ctrl info
+      val ctrlInfo = new Bundle {
+        val deqPtr = Input(new SqPtr())
+        val enqPtr = Input(new SqPtr())
+      }
+    })
+
+    /**
+     * @param in The select vector
+     * @return (result, multiHit)
+     *
+     *         result: The one-hot vec of first true.
+     *
+     *         multiHit: select vector is not one-hot.
+     * @example
+     *         in: b00010100
+     *         -> lowHasOne: b11111000 => (result, multiHit): (b00000100, true.B)
+     *
+     *         in: b00000010
+     *         -> lowHasOne: b11111100 => (result, multiHit): (b00000010, false.B)
+     * */
+    def findYoungest(in: UInt): (UInt, Bool) = {
+      val lowHasOne = VecInit(Seq.fill(in.getWidth)(false.B))
+      for (i <- 1 until in.getWidth) {
+        lowHasOne(i) := lowHasOne(i - 1) | in(i - 1)
+      }
+      // (one-hot result, has multi match)
+      (in & (~lowHasOne.asUInt).asUInt, (in & lowHasOne.asUInt).orR)
+    }
+
+    /**
+     * [Load Forward Query]
+     *
+     * Checks Store Queue for older stores that can forward data to the load.
+     * Response becomes valid 2 cycles after request.
+     *
+     * Pipeline Overview:
+     *   Stage 0: Prepare masks and address ranges
+     *   Stage 1: Match stores and select youngest valid candidate
+     *   Stage 2: Generate forwarded data and mask
+     *
+     * +----------+     +----------+     +----------+
+     * | Stage 0  | --> | Stage 1  | --> | Stage 2  |
+     * | (Cycle 0)|     | (Cycle 1)|     | (Cycle 2)|
+     * +----------+     +----------+     +----------+
+     */
+    for (i <- 0 until LoadPipelineWidth) {
+      // Stage breakdown:
+      //   Stage 0:
+      //     1. Generate load sqIdx mask
+      //     2. Calculate byte start/end for load
+      //   Stage 1:
+      //     1. Match physical/virtual addresses
+      //     2. Check byte range overlap
+      //     3. Select youngest matching store
+      //   Stage 2:
+      //     1. Extract correct bytes from store data
+      //     2. Generate final forwarded data and mask
+
+      val addrValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j => io.ctrlEntriesIn(j).addrValid)))
+      // if is cbo zero, it can forward; other cbo type's data is invalid.
+      val dataValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j =>
+        io.ctrlEntriesIn(j).dataValid && (isCboZero(io.dataEntriesIn(j).cboType) || !io.ctrlEntriesIn(j).isCbo))))
+      val allValidVec  = WireInit(VecInit((0 until StoreQueueSize).map(j =>
+        io.ctrlEntriesIn(j).allValid)))
+      val allocatedVec = WireInit(VecInit((0 until StoreQueueSize).map(j => io.ctrlEntriesIn(j).allocated)))
+
+      /*================================================== Stage 0 ===================================================*/
+      // Circular Queue Handling:
+      //   Store Queue is circular (like a ring buffer). When deqPtr and sqIdx wrap around,
+      //   we need to check two segments:
+      //
+      //   Case 1: same flag (no wrap)
+      //              sqIdx            deqPtr
+      //               |                |
+      //               v                v
+      //     +-----+-----+-----+-----+-----+-----+-----+-----+
+      //     |  7  |  6  |  5  |  4  |  3  |  2  |  1  |  0  |
+      //     +-----+-----+-----+-----+-----+-----+-----+-----+
+      //               ^^^^^^^^^^^^^^^^
+      //               deqPtr -> sqIdx (one segment)
+      //
+      //   Case 2: different flags (wrap around)
+      //             deqPtr           sqIdx
+      //               |                |
+      //               v                v
+      //     +-----+-----+-----+-----+-----+-----+-----+-----+
+      //     |  7  |  6  |  5  |  4  |  3  |  2  |  1  |  0  |
+      //     +-----+-----+-----+-----+-----+-----+-----+-----+
+      //     ^^^^^^^^^                   ^^^^^^^^^^^^^^^^^^^
+      //     end <- deqPtr        +      sqIdx <- 0
+      //
+      //   Implementation:
+      //     ageMaskLow  = deqMask & forwardMask & differentFlag
+      //     ageMaskHigh = ~deqMask & (differentFlag | forwardMask)
+      //
+      // Example: SQ size=8, deqPtr=6 (flag=0), sqIdx=3 (flag=1)
+      //   differentFlag = true
+      //   ageMaskLow  = 0b00001111 (bits 0-3)
+      //   ageMaskHigh = 0b11000000 (bits 6-7)
+
+      val s0Req              = io.query(i).s0Req
+      val s0Valid            = s0Req.valid
+      val s0DeqMask          = UIntToMask(io.ctrlInfo.deqPtr.value, StoreQueueSize)
+      val s0DifferentFlag    = io.ctrlInfo.deqPtr.flag =/= s0Req.bits.sqIdx.flag
+      val s0ForwardMask      = UIntToMask(s0Req.bits.sqIdx.value, StoreQueueSize)
+      // generate load byte start and end
+      val s0LoadStart        = s0Req.bits.vaddr(VWordOffset - 1, 0)
+      val s0ByteOffset       = MemorySize.ByteOffset(s0Req.bits.size)
+      val s0LoadEnd          = s0LoadStart + s0ByteOffset
+
+      // mdp mask
+      val lfstEnable = Constantin.createRecord("LFSTEnable", LFSTEnable)
+      val s0StoreSetHitVec = Mux(lfstEnable,
+        WireInit(VecInit((0 until StoreQueueSize).map(j =>
+          s0Req.bits.loadWaitBit && io.dataEntriesIn(j).uop.robIdx === s0Req.bits.waitForRobIdx))),
+        WireInit(VecInit((0 until StoreQueueSize).map(j =>
+          io.dataEntriesIn(j).uop.storeSetHit && io.dataEntriesIn(j).uop.ssid === s0Req.bits.ssid)))
+      )
+      val s0LoadWaitStrict = s0Req.bits.loadWaitStrict
+      val s0LoadSqIdx      = s0Req.bits.sqIdx
+
+      val s0AgeMaskLow     = s0DeqMask & s0ForwardMask & VecInit(Seq.fill(StoreQueueSize)(s0DifferentFlag)).asUInt
+      val s0AgeMaskHigh    = (~s0DeqMask).asUInt & (VecInit(Seq.fill(StoreQueueSize)(s0DifferentFlag)).asUInt | s0ForwardMask)
+
+      val s1ForwardMask  = RegEnable(s0ForwardMask, s0Valid)
+      val s1LoadVaddr    = RegEnable(s0Req.bits.vaddr(VAddrBits - 1, VWordOffset), s0Valid)
+      val s1deqMask      = RegEnable(s0DeqMask, s0Valid)
+      val s1LoadStart    = RegEnable(s0LoadStart, s0Valid)
+      val s1LoadEnd      = RegEnable(s0LoadEnd, s0Valid)
+      val s1StoreSetHitVec = RegEnable(s0StoreSetHitVec, s0Valid)
+      val s1LoadWaitStrict = RegEnable(s0LoadWaitStrict, s0Valid)
+      val s1LoadSqIdx      = RegEnable(s0LoadSqIdx, s0Valid)
+
+      val s1AgeMaskLow   = RegEnable(s0AgeMaskLow, s0Valid)
+      val s1AgeMaskHigh  = RegEnable(s0AgeMaskHigh, s0Valid)
+      val s1Kill         = io.query(i).s1Kill
+      val s1Valid        = RegNext(s0Valid) && !s1Kill
+
+
+      /*================================================== Stage 1 ===================================================*/
+      // Matching Process:
+      //
+      //   Step 1: Virtual Address Match (high bits only)
+      //     +-------+-----------------+--------+
+      //     | Store | vaddr (high)    |  size  |
+      //     +-------+-----------------+--------+
+      //     |   0   | 0x100 (0x1000)  |   4B   |
+      //     |   1   | 0x100 (0x1004)  |   2B   |
+      //     |   2   | 0x200 (0x2000)  |   4B   |
+      //     |   3   | 0x100 (0x1002)  |   4B   |
+      //     +-------+-----------------+--------+
+      //     Load vaddr = 0x1003 -> high=0x100 -> matches stores 0 and 3
+      //
+      //   Step 2: Byte Overlap Check
+      //     Store 0: [0,3] vs Load [3,3] -> overlap (0<=3<=3)
+      //     Store 3: [2,5] vs Load [3,3] -> overlap (2<=3<=5)
+      //
+      //   Step 3: Select Youngest Valid Store
+      //     canForward = ageMask & overlap & vaddrMatch
+      //     Example: canForward = 0b1001 (stores 0 and 3 match)
+      //     findYoungest(Reverse(0b1001)) -> selects store 3 (index 3)
+
+      val s1Req = io.query(i).s1Req
+      val s1QueryPaddr = s1Req.paddr(PAddrBits - 1, VWordOffset)
+      // prevent X-state
+      // Virtual address match (high bits only, ignore byte offset)
+      val s1VaddrMatchVec  = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).map { case (dataEntry, ctrlEntry) =>
+        val storeIsCboZero = ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
+        val isCross16B     = ctrlEntry.cross16Byte
+        // vaddr two part match:
+        // [1]: not cross 16B: vaddr[VaddrBits - 1, log2Ceil(CacheLineSize / 8)] addr(maxLen -> cacheline) or
+        //      cross 16B: vaddr[VaddrBits - 1, log2Ceil(CacheLineSize / 8)] + 1.U addr(maxLen -> cacheline) [next cacheline]
+        // [2]: not cross 16B: vaddr[log2Ceil(CacheLineSize / 8) - 1, log2Ceil(VLENB)] or
+        //      cross 16B: vaddr[log2Ceil(CacheLineSize / 8) - 1, log2Ceil(VLENB)] + 1.U [next 16B] or
+        //      The bits within cacheline, if store is cboZero, it can be ignored.
+        //
+        ((dataEntry.vaddr(DCacheLineOffset - 1, VWordOffset) === s1LoadVaddr(DCacheLineOffset - VWordOffset - 1, 0) ||
+          isCross16B && (dataEntry.vaddr(DCacheLineOffset - 1, VWordOffset) + 1.U) === s1LoadVaddr(DCacheLineOffset - VWordOffset - 1, 0) ||
+          storeIsCboZero) &&
+        (dataEntry.vaddr(VAddrBits - 1, DCacheLineOffset) === s1LoadVaddr(s1LoadVaddr.getWidth - 1, DCacheLineOffset - VWordOffset) ||
+          isCross16B && (dataEntry.vaddr(VAddrBits - 1, DCacheLineOffset) + 1.U) === s1LoadVaddr(s1LoadVaddr.getWidth - 1, DCacheLineOffset - VWordOffset))) &&
+        ctrlEntry.addrValid
+      }).asUInt
+
+      // Byte overlap check: store covers any part of load's range
+      //   Example: store [2,5] and load [3,3] -> overlap (2<=3 && 5>=3)
+      val s1OverlapMask  = VecInit((0 until StoreQueueSize).map(j =>
+        io.dataEntriesIn(j).byteStart <= s1LoadEnd && io.dataEntriesIn(j).byteEnd >= s1LoadStart ||
+        io.ctrlEntriesIn(j).cross16Byte && io.dataEntriesIn(j).byteEnd(VWordOffset - 1, 0) <= s1LoadEnd // next 16B, store start always 0.
+      )).asUInt
+
+      XSError((s1LoadEnd < s1LoadStart) && s1Valid, "ByteStart > ByteEnd!\n")
+
+      // Two-step selection to handle circular queue segments
+      val s1CanForwardLow = s1AgeMaskLow & s1OverlapMask & s1VaddrMatchVec
+      val s1CanForwardHigh = s1AgeMaskHigh & s1OverlapMask & s1VaddrMatchVec
+
+      // find youngest entry, which is one-hot
+      // Find youngest store (highest index = most recent)
+      //   Reverse vector so we can find leftmost 1 (highest index)
+      val (s1SelectLowOH, _)             = findYoungest(Reverse(s1CanForwardLow))
+      val (s1ForwardHighOH, _)           = findYoungest(Reverse(s1CanForwardHigh))
+      val s1SelectHighOH                 = s1ForwardHighOH & VecInit(Seq.fill(StoreQueueSize)(!s1CanForwardLow.orR)).asUInt
+      val s1SelectOH                     = Reverse(s1SelectLowOH | s1SelectHighOH) // index higher, mean it younger
+      val s1SelectDataEntry              = Mux1H(s1SelectOH, io.dataEntriesIn)
+      val s1SelectCtrlEntry              = Mux1H(s1SelectOH, io.ctrlEntriesIn)
+      val s1DataInvalid                  = !(s1SelectOH & dataValidVec.asUInt).orR
+      val (_, s1MultiMatch)              = findYoungest(s1CanForwardLow | s1CanForwardHigh) // don't care
+
+      // select offset generate
+      val s1ByteSelectOffset   = s1LoadStart - s1SelectDataEntry.byteStart
+
+      // MDP
+      //                +-----------------------+
+      //                | Search a SSID for the |
+      //                |    load operation     |
+      //                +-----------------------+
+      //                           |
+      //                           V
+      //                 +-------------------+
+      //                 | load wait strict? |
+      //                 +-------------------+
+      //                           |
+      //                           V
+      //               +----------------------+
+      //            Set|                      |Clean
+      //               V                      V
+      //  +------------------------+   +------------------------------+
+      //  | Waiting for all older  |   | Wait until the corresponding |
+      //  |   stores operations    |   | older store operations       |
+      //  +------------------------+   +------------------------------+
+
+      val s1HasAddrInvalidVec  = (s1AgeMaskLow | s1AgeMaskHigh) & VecInit(addrValidVec.map(!_)).asUInt & allocatedVec.asUInt
+      val s1PreciseWait        = (s1HasAddrInvalidVec & s1StoreSetHitVec.asUInt).orR // precise storeset hit judgement
+      val s1HasAddrInvalid     = Mux(s1LoadWaitStrict, s1HasAddrInvalidVec.orR, s1PreciseWait)
+
+      // find youngest addrInvalid store
+      val s1AddrInvalidLow     = s1AgeMaskLow & VecInit(addrValidVec.map(!_)).asUInt & allocatedVec.asUInt &
+        s1StoreSetHitVec.asUInt
+      val s1AddrInvalidHigh    = s1AgeMaskHigh & VecInit(addrValidVec.map(!_)).asUInt & allocatedVec.asUInt &
+        s1StoreSetHitVec.asUInt & VecInit(Seq.fill(StoreQueueSize)(!s1AddrInvalidLow.orR)).asUInt
+
+      val (s1AddrInvLowOH, _)   = findYoungest(Reverse(s1AddrInvalidLow))
+      val (s1AddrInvHighOH, _)  = findYoungest(Reverse(s1AddrInvalidHigh))
+      val s1AddrInvSelectOH     = Reverse(s1AddrInvLowOH | s1AddrInvHighOH)
+
+      val s1DataInvalidSqIdx   = Wire(new SqPtr)
+      val s1AddrInvalidSqIdx   = Wire(new SqPtr)
+
+      s1DataInvalidSqIdx.value := OHToUInt(s1SelectOH)
+      s1DataInvalidSqIdx.flag  := Mux(s1SelectLowOH.orR, io.ctrlInfo.enqPtr.flag, io.ctrlInfo.deqPtr.flag)
+
+      s1AddrInvalidSqIdx.value := OHToUInt(s1AddrInvSelectOH)
+      s1AddrInvalidSqIdx.flag  := Mux(s1AddrInvLowOH.orR, io.ctrlInfo.enqPtr.flag, io.ctrlInfo.deqPtr.flag)
+
+      val s2ByteSelectOffset = RegEnable(s1ByteSelectOffset, s1Valid)
+      val s2SelectDataEntry  = RegEnable(s1SelectDataEntry, s1Valid)
+      val s2SelectCtrlEntry  = RegEnable(s1SelectCtrlEntry, s1Valid)
+      val s2DataInValid      = RegEnable(s1DataInvalid, s1Valid)
+      val s2HasAddrInvalid   = RegEnable(s1HasAddrInvalid, s1Valid)
+      val s2CanForward       = RegEnable((s1AgeMaskLow | s1AgeMaskHigh) & s1OverlapMask & addrValidVec.asUInt, s1Valid)
+      val s2SelectOH         = RegEnable(s1SelectOH, s1Valid)
+      val s2LoadMaskEnd      = RegEnable(UIntToMask(MemorySize.CalculateSelectMask(s1LoadStart, s1LoadEnd), VLENB), s1Valid)
+      val s2DataInvalidSqIdx = RegEnable(s1DataInvalidSqIdx, s1Valid)
+      val s2AddrInvalidSqIdx = RegEnable(s1AddrInvalidSqIdx, s1Valid)
+      val s2LoadWaitStrict   = RegEnable(s1LoadWaitStrict, s1Valid)
+      val s2WaitStrictSqIdx  = RegEnable(s1LoadSqIdx - 1.U, s1Valid)
+      val s2MultiMatch       = RegEnable(s1MultiMatch, s1Valid)
+      val s2LoadPaddr        = RegEnable(s1QueryPaddr, s1Valid)
+      val s2LoadStart        = RegEnable(s1LoadStart, s1Valid)
+      val s2ForwardValid     = RegEnable(s1SelectOH.orR, s1Valid) // indicate whether forward is valid.
+      val s2Valid            = RegNext(s1Valid)
+      // debug
+      XSError(s1SelectOH.orR && !s1SelectCtrlEntry.allocated && s1Valid, "forward select a invalid entry!\n")
+      /*================================================== Stage 2 ===================================================*/
+
+      // Data Generation Process:
+      //     Original Store Data (byteStart=1, size=4B):
+      //     +--------+--------+--------+--------+
+      //     | 0x88   | 0x77   | 0x66   | 0x55   |  <- Memory (LE)
+      //     +--------+--------+--------+--------+
+      //       0x1004   0x1003   0x1002   0x1001
+      //                                  ^^^^^^
+      //                                    Store starts here
+      //
+      //
+      //   Load at s2ByteSelectOffset=2 (loadStart=3, loadSize=1B):
+      //     +--------+--------+--------+--------+
+      //     | 0x66   | 0x55   | 0x88   | 0x77   |  <- rotateByteRight && ParallelLookUp
+      //     +--------+--------+--------+--------+
+      //                                  ^^^^
+      //                                  Load needs this byte (0x77)
+
+      // !Paddrmatch
+      val s2PaddrMatchVec       = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).map { case (dataEntry, ctrlEntry) =>
+        val storeIsCboZero      = ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
+        val isCross16B          = ctrlEntry.cross16Byte
+
+        (dataEntry.paddr(DCacheLineOffset - 1, VWordOffset) === s2LoadPaddr(DCacheLineOffset - VWordOffset - 1, 0) ||
+          isCross16B && (dataEntry.paddr(DCacheLineOffset - 1, VWordOffset) + 1.U) === s2LoadPaddr(DCacheLineOffset - VWordOffset - 1, 0) || // next 16B
+          storeIsCboZero) &&
+        (dataEntry.paddr(pageOffset - 1, DCacheLineOffset) === s2LoadPaddr(pageOffset - VWordOffset - 1, DCacheLineOffset - VWordOffset) ||
+          isCross16B && (dataEntry.paddr(pageOffset - 1, DCacheLineOffset) + 1.U) === s2LoadPaddr(pageOffset - VWordOffset - 1, DCacheLineOffset - VWordOffset)) && // next Cacheline
+        dataEntry.paddr(PAddrBits - 1, pageOffset) === s2LoadPaddr(s2LoadPaddr.getWidth - 1, pageOffset - VWordOffset)
+      }).asUInt
+
+      // two situation need to trigger paddr not match :
+      // [1]. vaddr match, but paddr not match.
+      // [2]. vaddr not match, but paddr match.
+      val s2PaddrNoMatch       = Mux(s2ForwardValid,
+        !(s2PaddrMatchVec & s2CanForward & s2SelectOH).orR, // if forward valid, select entry's paddr must match
+        (s2PaddrMatchVec & s2CanForward).orR) // if forward invalid, must no paddr match
+
+      val s2SelectData         = (0 until VLENB).map(j =>
+        j.U -> rotateByteRight(s2SelectDataEntry.data, j * 8)
+      )
+      val s2OutData            = ParallelLookUp(s2ByteSelectOffset, s2SelectData)
+
+      val s2SelectMask         = (0 until VLENB).map(j =>
+        j.U -> rotateByteRight(s2SelectDataEntry.byteMask, j)
+      )
+      val s2OutMask            = ParallelLookUp(s2ByteSelectOffset, s2SelectMask) & s2LoadMaskEnd
+
+      val s2FullOverlap        = (s2SelectDataEntry.byteMask & s2LoadMaskEnd) === s2LoadMaskEnd
+      // First condition: access extends beyond the lower log2Ceil(VLEN/8) bits.
+      // Second condition: higher bits of the virtual address within the page offset are non-zero, indicating a potential cross-page access.
+      val s2Cross4KPage        = s2SelectDataEntry.byteEnd(VWordOffset) && s2SelectDataEntry.vaddr(pageOffset - 1, VWordOffset).andR && s2ForwardValid
+      val s2SafeForward        = !s2MultiMatch || s2FullOverlap
+
+      //TODO: only use for 128-bit align forward, should revert when other forward source support rotate forward !!!!
+      val s2FinalData          = s2OutData << (s2LoadStart * 8.U)
+      val s2FinalMask          = s2OutMask << s2LoadStart
+
+      val s1Resp = io.query(i).s1Resp
+      val s2Resp = io.query(i).s2Resp
+      s1Resp.valid := false.B //TODO: need it?
+      s1Resp.bits := DontCare
+//      s2Resp.bits.forwardData.zipWithIndex.map{case (sink, j) =>
+//        sink := outData((j + 1) * 8 - 1, j * 8)}
+//      s2Resp.bits.forwardMask.zipWithIndex.map{case (sink, j) =>
+//        sink := outMask(j) && s2Valid} // TODO: FIX ME, when Resp.valid is false, do not use ByteMask!!
+      s2Resp.bits.forwardData.zipWithIndex.map{case (sink, j) =>
+        sink := s2FinalData((j + 1) * 8 - 1, j * 8)}
+      s2Resp.bits.forwardMask.zipWithIndex.map{case (sink, j) =>
+        sink := s2FinalMask(j) && s2ForwardValid} // TODO: FIX ME, when Resp.valid is false, do not use ByteMask!!
+      s2Resp.bits.dataInvalid.valid := s2DataInValid && s2ForwardValid // select is valid
+      s2Resp.bits.dataInvalid.bits := s2DataInvalidSqIdx
+      s2Resp.bits.addrInvalid.valid := s2HasAddrInvalid // maby can't select a entry
+      s2Resp.bits.addrInvalid.bits := Mux(s2LoadWaitStrict, s2WaitStrictSqIdx, s2AddrInvalidSqIdx)
+      s2Resp.bits.forwardInvalid   := !s2SafeForward || s2Cross4KPage // do not support cross page forward.
+      s2Resp.bits.matchInvalid     := s2PaddrNoMatch && !s2Cross4KPage && s2SafeForward // if cross Page/multi match, let load replay.
+      s2Resp.valid                 := s2Valid
+
+      if(debugEn) {
+        dontTouch(s1OverlapMask)
+        dontTouch(s1AgeMaskLow)
+        dontTouch(s1AgeMaskHigh)
+        dontTouch(s1CanForwardLow)
+        dontTouch(s1CanForwardHigh)
+        dontTouch(s1MultiMatch)
+        dontTouch(s1AddrInvLowOH)
+        dontTouch(s1AddrInvHighOH)
+        dontTouch(s1SelectOH)
+        dontTouch(s1AddrInvSelectOH)
+        dontTouch(s2OutMask)
+        dontTouch(s2OutData)
+        dontTouch(s2SafeForward)
+        dontTouch(s2PaddrMatchVec)
+        dontTouch(s2CanForward)
+      }
+    }
+  }
+
+  /*
+  * EnterSbufferQueue is a sequentially written data buffer for eliminating timing paths between the Sbuffer and StoreQueue.
+  *
+  * [NOTES]: Ideally, the n data at the StoreQueue head can be written into the Sbuffer, EnterSbufferQueue is a pipeline.
+  *          However, when the sbuffer becomes unable to write the n data in a single cycle,
+  *          the EnterSbufferQueue ensures that the n data are written into the Sbuffer in the correct order
+  *          while they are in EnterSbufferQueue.
+  *
+  * The structure of StoreQueue write to Sbuffer are as shown below:
+  *     +------------+                        +-------------------+
+  *     | StoreQueue |                        |                   |
+  *     +------------+                        |                   |
+  *     |      .     |                        | EnterSbufferQueue |
+  *     |      .     |                        |                   |
+  *     |      .     |                        |                   |
+  *     +------------+  [n = EnsbufferWidth]  +-------------------+               +-------------------+
+  *     |   head n   | ---------------------->|      Entry n      | ------------> |                   |
+  *     +------------+                        +-------------------+               |                   |
+  *     |      .     |                        |         .         |               |                   |
+  *     |      .     |                        |         .         |               |       Sbuffer     |
+  *     |      .     |                        |         .         |               |                   |
+  *     +------------+                        +-------------------+               |                   |
+  *     |   head 0   |----------------------> |      Entry 0      | ------------> |                   |
+  *     +------------+                        +-------------------+               +-------------------+
+  * */
+  private class EnterSbufferQueue(val param: ExeUnitParams)(implicit  p: Parameters) extends LSQModule {
+    val io = IO(new Bundle {
+      val fromDeqModule = Vec(EnsbufferWidth, Flipped(DecoupledIO(new WriteToSbufferReqEntry)))
+      val toSbuffer     = new SbufferWriteIO
+      val empty         = Output(Bool())
+      val full          = Output(Bool())
+      val freeCount     = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
+    })
+    def ToSbufferConnect(source: WriteToSbufferReqEntry, sink: DCacheWordReqWithVaddrAndPfFlag) = {
+      sink          := WireInit(0.U.asTypeOf(new DCacheWordReqWithVaddrAndPfFlag)) // TODO: init here.
+      sink.data     := source.data
+      sink.mask     := source.mask
+      sink.vaddr    := source.vaddr
+      sink.wline    := source.wline
+      sink.addr     := source.addr
+      sink.vecValid := source.vecValid
+      sink.prefetch := source.prefetch
+      sink
+    }
+
+    private val enqWidth: Int  = io.fromDeqModule.length
+    private val queueSize: Int = EnsbufferWidth
+
+    private val entries    = Reg(Vec(queueSize, new WriteToSbufferReqEntry)) // no need to reset!
+    private val allocated  = RegInit(VecInit(Seq.fill(queueSize)(false.B)))
+    private val enqPtrVec  = RegInit(VecInit((0 until io.fromDeqModule.length).map(_.U.asTypeOf(new DataQueuePtr))))
+    private val deqPtrVec  = RegInit(VecInit((0 until io.fromDeqModule.length).map(_.U.asTypeOf(new DataQueuePtr))))
+    private val headEntry  = entries(deqPtrVec.head.value)
+
+    private val empty      = enqPtrVec.head.value === deqPtrVec.head.value && enqPtrVec.head.flag === deqPtrVec.head.flag
+    private val full       = enqPtrVec.head.value === deqPtrVec.head.value && enqPtrVec.head.flag =/= deqPtrVec.head.flag
+
+    // enq
+    private val canEnq    = io.fromDeqModule.map(_.fire)
+    private val enqReq    = io.fromDeqModule.map(_.bits)
+
+    enqPtrVec.zip(canEnq).zipWithIndex.map{case ((ptr, v), i) =>
+      when(v) {
+        entries(ptr.value) := enqReq(i)
+      }
+    }
+
+    private val deqSameCycle   = WireInit(VecInit(Seq.fill(EnsbufferWidth)(false.B)))
+
+    // if Sbuffer counsume i request, within the same cycle, i entries may enter new request.
+    (0 until EnsbufferWidth).map {i =>
+      deqSameCycle(i) := deqPtrVec.zipWithIndex.map{case (ptr, j) =>
+        ptr.value === i.U && io.toSbuffer.req(j).fire
+      }.reduce(_ || _)
+    }
+
+    /**
+     * Update allocation status for each queue slot:
+     *   - Enqueue sets allocated = true (higher priority)
+     *   - Dequeue sets allocated = false (lower priority)
+     *
+     * Priority: Enqueue > Dequeue (allows same-cycle reuse)
+     */
+    (0 until queueSize).map{i =>
+      val deqCancel = deqPtrVec.zipWithIndex.map{case (ptr, j) =>
+        ptr.value === i.U && io.toSbuffer.req(j).fire
+      }.reduce(_ || _)
+      val enqSet    = enqPtrVec.zipWithIndex.map{case (ptr, j) =>
+        ptr.value === i.U && io.fromDeqModule(j).fire
+      }.reduce(_ || _)
+
+      when(enqSet) { // enq has high priority.
+        allocated(i) := true.B
+      }.elsewhen(deqCancel) {
+        allocated(i) := false.B
+      }
+    }
+
+    //update enq pointer
+    private val enqNum = PopCount(canEnq)
+    enqPtrVec := VecInit(enqPtrVec.map(_ + enqNum))
+
+    // deq
+    private val doDeqNum = PopCount(io.toSbuffer.req.map(_.fire))
+    deqPtrVec := VecInit(deqPtrVec.map(_ + doDeqNum))
+    // When enqPtr.flag = 1, enqPtr.value = 0, deqPtr.flag=0, deqPtr.value = 0, the API '<'  fails to function correctly.
+    XSError(enqPtrVec.head < deqPtrVec.head && !full, s"Something wrong in DataBufferQueue!\n")
+
+    // connection
+    for (i <- 0 until EnsbufferWidth) {
+      // if port 0, it can be enter queue whenever possible. However, for other ports, enter queue requires that
+      //  the port with the smaller sequencer number be ready.
+      if(i == 0) {
+        io.fromDeqModule(i).ready := !allocated(enqPtrVec(i).value) || deqSameCycle(enqPtrVec(i).value)
+      }
+      else {
+        io.fromDeqModule(i).ready := (!allocated(enqPtrVec(i).value) || deqSameCycle(enqPtrVec(i).value)) && io.fromDeqModule(i - 1).ready
+      }
+
+    }
+
+    for (i <- 0 until EnsbufferWidth) {
+      ToSbufferConnect(entries(deqPtrVec(i).value), io.toSbuffer.req(i).bits)
+      io.toSbuffer.req(i).valid := allocated(deqPtrVec(i).value)
+      if(i > 0){
+        XSError(io.toSbuffer.req(i).valid && !io.toSbuffer.req(i - 1).valid, s"low port is invalid, but ${i} port is valid!\n")
+      }
+    }
+
+    io.freeCount := PopCount((~allocated.asUInt).asUInt)
+    io.empty     := empty
+    io.full      := full
+
+    if(debugEn) {
+      dontTouch(deqSameCycle)
+      dontTouch(enqPtrVec)
+      dontTouch(deqPtrVec)
+    }
+  }
+
+  private class DeqModule(val param: ExeUnitParams)(implicit p: Parameters) extends LSQModule {
+    val io = IO(new Bundle {
+      val redirect         = Flipped(ValidIO(new Redirect))
+      //The head request of StoreQueue that will write to sbuffer. The rdataPtr point entries.
+      val rdataDataEntries = Vec(EnsbufferWidth, Input(new SQDataEntryBundle))
+      val rdataCtrlEntries = Vec(EnsbufferWidth, Input(new SQCtrlEntryBundle))
+      //The head request of StoreQueue that will dequeue, The deqPtr point entries.
+      val deqCtrlEntries  = Vec(EnsbufferWidth, Input(new SQCtrlEntryBundle))
+      val deqDataEntries  = Vec(EnsbufferWidth, Input(new SQDataEntryBundle))
+
+      val toUncacheBuffer = new UncacheWordIO
+      val toDCache        = new ToCacheIO
+      val fromRob         = Input(new FromRobIO)
+      val toRob           = Output(new toRobIO)
+      val writeToSbuffer  = new SbufferWriteIO
+      val writeBack       = DecoupledIO(new NewExuOutput(param))
+      val exceptionInfo   = ValidIO(new MemExceptionInfo)
+      val sbufferCtrl     = new SbufferCtrlIO
+
+      val deqPtrExtNext   = Output(Vec(EnsbufferWidth, new SqPtr))
+      val rdataPtrMoveCnt  = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
+      val sqDeqCnt        = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
+      val rdataPtrExt     = Input(Vec(EnsbufferWidth, new SqPtr))
+      val deqPtrExt       = Input(Vec(EnsbufferWidth, new SqPtr))
+      val validCnt        = Input(UInt(log2Ceil(StoreQueueSize + 1).W))
+      val fromUnalignQueue = Flipped(ValidIO(new Bundle {
+        val paddr         = UInt(PAddrBits.W)
+        val sqIdx         = new SqPtr
+      }))
+      // for debug
+      val pmaStore        = Option.when(debugEn)(Vec(EnsbufferWidth, ValidIO(new DifftestPmaStoreIO)))
+      // for perf
+      val perfMmioBusy    = Output(Bool())
+    })
+
+    private object UncacheState extends ChiselEnum {
+      val idle      = Value
+      val sendReq   = Value
+      val waitResp  = Value
+      val writeback = Value
+    }
+
+    private object CboState extends ChiselEnum {
+      val idle      = Value
+      val writeZero = Value
+      val flushSb   = Value
+      val sendReq   = Value
+      val waitResp  = Value
+      val writeback = Value
+    }
+
+    private val dataQueue        = Module(new EnterSbufferQueue(param))
+
+    private val dataEntries      = io.rdataDataEntries //The head request of StoreQueue that will write to sbuffer. The rdataPtr point entries.
+    private val ctrlEntries      = io.rdataCtrlEntries
+    private val deqCtrlEntries   = io.deqCtrlEntries //The deqPtr point entries
+    private val deqDataEntries   = io.deqDataEntries
+    private val headDataEntry    = dataEntries.head
+    private val headCtrlEntry    = ctrlEntries.head
+    private val headDeqPtr       = io.deqPtrExt.head
+    private val headrdataPtr     = io.rdataPtrExt.head
+
+    /*============================================ force write sbuffer ===============================================*/
+//    io.sqCancelCnt := redirectCancelCount
+    val ForceWriteUpper = Wire(UInt(log2Up(StoreQueueSize + 1).W))
+    ForceWriteUpper := Constantin.createRecord(s"ForceWriteUpper_${p(XSCoreParamsKey).HartId}", initValue = StoreQueueForceWriteSbufferUpper)
+    val ForceWriteLower = Wire(UInt(log2Up(StoreQueueSize + 1).W))
+    ForceWriteLower := Constantin.createRecord(s"ForceWriteLower_${p(XSCoreParamsKey).HartId}", initValue = StoreQueueForceWriteSbufferLower)
+
+    val valid_cnt = io.validCnt
+    io.sbufferCtrl.req.forceWrite := RegNext(Mux(valid_cnt >= ForceWriteUpper,
+      true.B,
+      valid_cnt >= ForceWriteLower && io.sbufferCtrl.req.forceWrite),
+      init = false.B)
+
+    /*=========================================== Data and Mask Generate =============================================*/
+    /**/
+
+    private val outData        = Wire(Vec(EnsbufferWidth , UInt(VLEN.W)))
+    private val outMask        = Wire(Vec(EnsbufferWidth , UInt((VLENB).W)))
+
+
+    for (i <- 0 until EnsbufferWidth) {
+      val selectOffset       = 0.U - dataEntries(i).vaddr(3, 0) // need to generate 0 align data and mask
+      val selectData         = (0 until VLENB).map(j => // generate circular right shift byte data.
+        j.U -> rotateByteRight(dataEntries(i).data, j * 8)
+      )
+
+      val byteMask           = dataEntries(i).byteMask
+      val selectMsk          = (0 until VLENB).map(j => // generate circular right shift byte data.
+        j.U -> rotateByteRight(byteMask, j)
+      )
+
+      outData(i)         := ParallelLookUp(selectOffset, selectData)
+      outMask(i)         := ParallelLookUp(selectOffset, selectMsk)
+
+    }
+
+    // exception check
+    private val hasHardwareError = RegInit(false.B)
+    private val hasAccessFault   = RegInit(false.B)
+    /*================================================================================================================*/
+    /*================================================= CBO.FSM ======================================================*/
+    /*                                           zero
+    *                           + ----------------------------------- +
+    *                           |                                     |
+    *       clean/flush/inval   |                                     v
+    *  idle ----------------> flushSb --> sednReq --> waitResp --> writeback
+    *   |                       ^
+    *   |   zero                |
+    *   + -------> writeZero -- +
+    * */
+    /*================================================================================================================*/
+
+    private val cboState: CboState.Type = RegInit(CboState.idle)
+    private val cboStateNext: CboState.Type = WireInit(cboState)
+    cboState := cboStateNext
+
+    private val cboCanHandle = headCtrlEntry.allValid && !headCtrlEntry.hasException && headCtrlEntry.allocated &&
+      headCtrlEntry.isCbo
+
+    switch(cboState) {
+      is(CboState.idle) {
+        when(cboCanHandle) {
+          cboStateNext := Mux(isCboZero(headDataEntry.cboType), CboState.writeZero, CboState.flushSb)
+        }
+      }
+      is(CboState.writeZero) {
+        when(io.writeToSbuffer.req.head.fire) {
+          cboStateNext := CboState.flushSb
+        }
+      }
+      is(CboState.flushSb) {
+        when(io.sbufferCtrl.resp.empty && dataQueue.io.empty) { // Ensure there are no in-flight request.
+          cboStateNext := Mux(isCboZero(headDataEntry.cboType), CboState.writeback, CboState.sendReq)
+        }
+      }
+      is(CboState.sendReq) {
+        when(io.toDCache.req.fire) {
+          cboStateNext := CboState.waitResp
+        }
+      }
+      is(CboState.waitResp) {
+        when(io.toDCache.resp.fire) {
+          cboStateNext := CboState.writeback
+        }
+      }
+      is(CboState.writeback) {
+        when(io.writeBack.fire) {
+          cboStateNext := CboState.idle
+        }
+      }
+    }
+
+    // cbo handle connection
+    io.sbufferCtrl.req.flush     := cboState === CboState.flushSb
+
+    io.toDCache.req.valid        := cboState === CboState.sendReq
+    io.toDCache.req.bits.address := headDataEntry.paddr
+    io.toDCache.req.bits.opcode  := headDataEntry.cboType
+    io.toDCache.resp.ready       := cboState === CboState.waitResp
+
+    /*================================================================================================================*/
+    /*=============================================== MMIO/NC.FSM ====================================================*/
+    /*
+    *       + --------- +
+    *       |    isNC   |
+    *       v           |
+    *     idel ---> sendReq ---> waitResp ---> writeback
+    * */
+    /*================================================================================================================*/
+    private val uncacheState: UncacheState.Type = RegInit(UncacheState.idle)
+    private val uncacheStateNext: UncacheState.Type = WireInit(uncacheState)
+    uncacheState := uncacheStateNext
+
+    private val isNC             = isPbmtNC(headDataEntry.memoryType)
+    private val isPBMTIO         = isPbmtIO(headDataEntry.memoryType)
+    private val uncacheCanHandle = !isCacheable(headDataEntry.memoryType) && !headCtrlEntry.isCbo &&
+      headCtrlEntry.allValid && !headCtrlEntry.hasException && headCtrlEntry.allocated && headCtrlEntry.committed
+
+    switch(uncacheState) {
+      is(UncacheState.idle) {
+        when(uncacheCanHandle) {
+          uncacheStateNext := UncacheState.sendReq
+        }
+      }
+      is(UncacheState.sendReq) {
+        when(io.toUncacheBuffer.req.fire) {
+          uncacheStateNext := Mux(isNC, UncacheState.idle, UncacheState.waitResp)
+        }
+      }
+      is(UncacheState.waitResp) {
+        when(io.toUncacheBuffer.resp.fire){
+          uncacheStateNext := UncacheState.writeback
+        }
+      }
+      is(UncacheState.writeback) {
+        when(io.writeBack.fire) {
+          uncacheStateNext := UncacheState.idle
+        }
+      }
+    }
+
+    // requestor, to UncacheBuffer.
+    private val brodenId = Wire(UInt(uncacheIdxBits.W))
+    if(uncacheIdxBits > headrdataPtr.value.getWidth){
+      brodenId := Cat(0.U((uncacheIdxBits - headrdataPtr.value.getWidth).W), headrdataPtr.value)
+    }
+    else {
+      brodenId := headrdataPtr.value
+    }
+    io.toUncacheBuffer.req.valid              := uncacheState === UncacheState.sendReq
+    io.toUncacheBuffer.req.bits.cmd           := MemoryOpConstants.M_XWR
+    io.toUncacheBuffer.req.bits.vaddr         := headDataEntry.vaddr
+    io.toUncacheBuffer.req.bits.addr          := headDataEntry.paddr
+    io.toUncacheBuffer.req.bits.data          := Mux(headDataEntry.vaddr(3), outData.head(VLEN - 1, 64), outData.head(63,0))
+    io.toUncacheBuffer.req.bits.mask          := Mux(headDataEntry.vaddr(3), outMask.head(VLENB - 1 , 8), outMask.head(7,0))
+    io.toUncacheBuffer.req.bits.robIdx        := headDataEntry.uop.robIdx
+    io.toUncacheBuffer.req.bits.memBackTypeMM := isNC || isPBMTIO
+    io.toUncacheBuffer.req.bits.nc            := isNC //TODO: remove it, why not use memBackTypeMM ?!
+    io.toUncacheBuffer.req.bits.id            := brodenId
+
+    // resp
+    io.toUncacheBuffer.resp.ready             := true.B
+
+    //stout
+    when(uncacheState === UncacheState.waitResp) {
+      hasHardwareError := io.toUncacheBuffer.resp.fire && io.toUncacheBuffer.resp.bits.corrupt
+    }.elsewhen(cboState === CboState.waitResp){
+      hasHardwareError := io.toDCache.resp.fire && io.toDCache.resp.bits.corrupt
+    }.elsewhen(uncacheState === UncacheState.writeback || cboState === CboState.writeback) {
+      hasHardwareError := false.B
+    }
+
+    when(uncacheState === UncacheState.waitResp) {
+      hasAccessFault := io.toUncacheBuffer.resp.fire && io.toUncacheBuffer.resp.bits.denied
+    }.elsewhen(cboState === CboState.waitResp){
+      hasAccessFault := io.toDCache.resp.fire && io.toDCache.resp.bits.denied
+    }.elsewhen(uncacheState === UncacheState.writeback || cboState === CboState.writeback) {
+      hasAccessFault := false.B
+    }
+
+    val writeBack = Wire(new NewExuOutput(staParams.head))
+    writeBack.toRob.valid                        := (uncacheState === UncacheState.writeback) || (cboState === CboState.writeback)
+    writeBack.toRob.bits.robIdx := dataEntries.head.uop.robIdx
+    writeBack.toRob.bits.exceptionVec.foreach{ case x =>
+      x := ExceptionNO.selectByFu(0.U.asTypeOf(ExceptionVec()), StaCfg)
+      x(hardwareError) := hasHardwareError
+      x(storeAccessFault) := hasAccessFault} // override
+    writeBack.toRob.bits.trigger.foreach(_ := DontCare)
+    writeBack.toRob.bits.isRVC.foreach(_ := DontCare)
+    writeBack.toRob.bits.sqIdx.foreach(_ := io.rdataPtrExt.head)
+    writeBack.pdest := DontCare
+    // for difftest, ref will skip mmio store
+    writeBack.debug := DontCare
+    if(debugEn) {
+      writeBack.debug.vaddr   := dataEntries.head.debugVaddr.get
+      writeBack.debug.paddr   := dataEntries.head.debugPaddr.get
+      writeBack.debug.isPerfCnt := DontCare
+      writeBack.debug_seqNum.foreach(_ := dataEntries.head.debugUop.get.debug_seqNum)
+      writeBack.perfDebugInfo.foreach(_ := dataEntries.head.debugUop.get.perfDebugInfo)
+    }
+    if(basicDebugEn) {
+      writeBack.debug.isMMIO  := isMmio(dataEntries.head.memoryType) || isPbmtIO(dataEntries.head.memoryType)
+      writeBack.debug.isNCIO  := isPbmtNC(dataEntries.head.memoryType)
+    }
+    connectMemDecoupledNewExuOutput(io.writeBack, writeBack)
+
+    io.exceptionInfo.valid             := (uncacheState === UncacheState.writeback) || (cboState === CboState.writeback)
+    io.exceptionInfo.bits.robIdx       := dataEntries.head.uop.robIdx
+    io.exceptionInfo.bits.exceptionVec := ExceptionNO.selectByFu(writeBack.toRob.bits.exceptionVec.get, StaCfg)
+    // TODO: why not fullVaddr and why don't have gpaddr ?
+    io.exceptionInfo.bits.vaddr        := dataEntries.head.vaddr
+    io.exceptionInfo.bits.gpaddr       := 0.U.asTypeOf(io.exceptionInfo.bits.gpaddr)
+    io.exceptionInfo.bits.isForVSnonLeafPTE := false.B
+    io.exceptionInfo.bits.vaNeedExt    := true.B
+    io.exceptionInfo.bits.uopIdx       := 0.U.asTypeOf(io.exceptionInfo.bits.uopIdx)
+    io.exceptionInfo.bits.vl           := 0.U.asTypeOf(io.exceptionInfo.bits.vl)
+    io.exceptionInfo.bits.vstart       := 0.U.asTypeOf(io.exceptionInfo.bits.vstart)
+    io.exceptionInfo.bits.isHyper      := false.B
+
+    /*============================================ cacheable handle ==================================================*/
+    /**
+    * This section has three functions:
+    * [1]. All aligned requestor will write to Sbuffer
+    * [2]. All unaligned requestor will be splited, then write to Sbuffer
+    * [3]. The cbo.zero will write zero to Sbuffer
+    * */
+
+    /*----------------------------------------------- Unalign Split --------------------------------------------------*/
+    /* unalign write situation
+    * [1]. effective bytes within 16B                  -----> shift to align with 16B
+    *                                                    +--> cross Cacheline     --+
+    *                                                    |                          v
+    * [2]. effective bytes cross 16B, not cross page   --+    shift to align with 16B, split into two write request
+    *                                                    |                          ^
+    *                                                    +--> not cross Cacheline --+
+    * [3]. effective bytes cross 16B, cross page       -----> shift to align with 16B, split into two write request
+    *                                                          (second paddr is from Unalign Queue)
+    *
+    * The request of split will be write to Sbuffer through writeToSbuffer[0] and writeToSbuffer[1].
+    */
+    /*----------------------------------------------------------------------------------------------------------------*/
+
+    private val unalignMask       = Wire(Vec(EnsbufferWidth , UInt((VLENB).W))) // select active bytes of split request
+    private val writeSbufferData  = Wire(Vec(EnsbufferWidth , UInt(VLEN.W)))
+    private val writeSbufferMask  = Wire(Vec(EnsbufferWidth , UInt((VLENB).W)))
+    private val writeSbufferPaddr = Wire(Vec(EnsbufferWidth , UInt(PAddrBits.W)))
+    private val writeSbufferVaddr = Wire(Vec(EnsbufferWidth , UInt(VAddrBits.W)))
+    private val headCross16B      = headCtrlEntry.cross16Byte
+    private val headCrossPage     = headrdataPtr === io.fromUnalignQueue.bits.sqIdx && io.fromUnalignQueue.valid
+    private val diffIsHighPart    = Wire(Vec(EnsbufferWidth, Bool())) //only for difftest
+
+    // paddrHigh and vaddrHigh only for cross16Byte split
+    private val paddrLow          = Cat(headDataEntry.paddr(headDataEntry.paddr.getWidth - 1, 4), 0.U(4.W))
+    private val paddrHigh         = Cat(headDataEntry.paddr(headDataEntry.paddr.getWidth - 1, 4), 0.U(4.W)) + 16.U
+    private val vaddrLow          = Cat(headDataEntry.vaddr(headDataEntry.vaddr.getWidth - 1, 4), 0.U(4.W))
+    private val vaddrHigh         = Cat(headDataEntry.vaddr(headDataEntry.vaddr.getWidth - 1, 4), 0.U(4.W)) + 16.U
+
+    for (i <- 0 until EnsbufferWidth) {
+      unalignMask(i)         := VecInit(Seq.fill(VLENB)(false.B)).asUInt >> dataEntries(i).vaddr(3, 0)
+      // unalignWithin16Byte is for old unalign framework difftest, will be remove in the future.
+      val unalignWithin16Byte = (if (debugEn) ctrlEntries(i).unalignWithin16Byte.get else false.B)
+      if(i == 0) {
+        writeSbufferData(i)  := outData(i)
+        writeSbufferMask(i)  := outMask(i) & unalignMask(i)
+        writeSbufferPaddr(i) := paddrLow
+        writeSbufferVaddr(i) := vaddrLow
+        diffIsHighPart(i)    := dataEntries(i).paddr(3) && !unalignWithin16Byte //TODO: will be fix in thefuture
+      }
+      if(i == 1) {
+        writeSbufferData(i)  := Mux(headCross16B, outData(0), outData(i))
+        writeSbufferMask(i)  := Mux(headCross16B, outMask(0) & (~unalignMask(0)).asUInt, outMask(i))
+        writeSbufferPaddr(i) := Mux(headCrossPage,
+          io.fromUnalignQueue.bits.paddr,
+          Mux(headCross16B, paddrHigh, Cat(dataEntries(i).paddr(dataEntries(i).paddr.getWidth - 1, 4), 0.U(4.W))))
+        // if unalign cross Page, it is must cross 16Byte
+        writeSbufferVaddr(i) := Mux(headCross16B,
+          vaddrHigh,
+          Cat(dataEntries(i).vaddr(dataEntries(i).vaddr.getWidth - 1, 4), 0.U(4.W)))
+        diffIsHighPart(i)    := Mux(headCross16B,
+                                      false.B,
+                                      dataEntries(i).paddr(3) && !unalignWithin16Byte //TODO: will be fix in thefuture
+                                    ) // if cross 16B, port 1 must low part
+      }
+      else {
+        writeSbufferData(i)  := outData(i)
+        writeSbufferMask(i)  := outMask(i)
+        writeSbufferPaddr(i) := Cat(dataEntries(i).paddr(dataEntries(i).paddr.getWidth - 1, 4), 0.U(4.W)) //align 128-bit
+        writeSbufferVaddr(i) := Cat(dataEntries(i).vaddr(dataEntries(i).vaddr.getWidth - 1, 4), 0.U(4.W)) //align 128-bit
+        diffIsHighPart(i)    := dataEntries(i).paddr(3) && !unalignWithin16Byte //TODO: will be fix in thefuture
+      }
+    }
+
+    /*---------------------------------------- Write to Sbuffer Interface --------------------------------------------*/
+    private val writeSbufferWire = Wire(Vec(EnsbufferWidth, DecoupledIO(new WriteToSbufferReqEntry)))
+    private val uncacheStall     = Wire(Vec(EnsbufferWidth, Bool()))
+    private val unalignStall     = Wire(Vec(EnsbufferWidth, Bool()))
+    private val cboStall         = Wire(Vec(EnsbufferWidth, Bool()))
+    private val toSbufferValid   = Wire(Vec(EnsbufferWidth, Bool()))
+    // cross16B will occupy two write port, so only need to use port 0 fire.
+    private val cross16BDeqReg   = RegEnable(headCross16B, writeSbufferWire(0).fire)
+
+    // when deq is MMIO/NC/CMO request, don't need to write sbuffer.
+    for (i <- 0 until EnsbufferWidth) {
+      val ctrlEntry = ctrlEntries(i)
+      val dataEntry = dataEntries(i)
+
+      if(i == 0) {
+        uncacheStall(i) := !isCacheable(dataEntry.memoryType)
+        cboStall(i)     := ctrlEntry.isCbo
+      }
+      else {
+        uncacheStall(i) := !isCacheable(dataEntry.memoryType) || uncacheStall(i - 1)
+        cboStall(i)     := ctrlEntry.isCbo || cboStall(i - 1)
+      }
+    }
+    // generate to sbuffer valid
+    /*
+    * NOTE: [1] only two port of dataQueue is ready, the request of cross16B can write to dataQueue.
+    *       [2] dataQueue.io.empty means dataQueue can enter two request at same time.
+    *       [3] entry.committed contains entry.allocated && entry.allValid && !entry.hasException && isRobHead.
+    */
+
+    // toSbufferValid(0) use dataQueue.io.empty to judge unalign split valid, need to modify if  EnsbufferWifth > 2,
+    // can use dataQueue.io.freeCount
+    require(EnsbufferWidth == 2)
+
+    for(i <- 0 until EnsbufferWidth) {
+      val ctrlEntry = ctrlEntries(i)
+      if(i == 0) {
+        toSbufferValid(i) := !uncacheStall(i) && !cboStall(i) && (!headCross16B || dataQueue.io.empty) &&
+          !unalignStall(i) && ctrlEntry.committed &&
+          !(ctrlEntry.vecMbCommit && !ctrlEntry.allValid || ctrlEntry.vecInactive) //TODO: vecMbCommit will be remove in the future
+        // [NOTE1]: entry.committed contains entry.allocated && entry.allValid && !entry.hasException && isRobHead.
+        // [NOTE2]: here I use dataQueue.io.empty because EnsbufferWifth == 2, if EnsbufferWifth > 2, need to modify.
+
+        unalignStall(i) := false.B // if first port is unalign, make it can write to sbuffer.
+      }
+      else if(i == 1) { // override port 1 to write second request of cross16B
+        // Regarding writing to port 1's Sbuffer, only the following two scenarios permit writing:
+        //  1. Port 0 write a unaligned request cross 16 bytes, preempting port 1's write port.
+        //  2. Port 0 is ready, and the Sbuffer can process two write requests simultaneously.
+        toSbufferValid(i) := !uncacheStall(i) && !cboStall(i) && ctrlEntry.committed &&
+          !(ctrlEntry.vecMbCommit && !ctrlEntry.allValid || ctrlEntry.vecInactive) && //TODO: vecMbCommit will be remove in the future
+          toSbufferValid(i - 1) || (headCross16B && toSbufferValid(0)) && !unalignStall(i)
+        // [NOTE]: entry.committed contains entry.allocated && entry.allValid && !entry.hasException && isRobHead.
+
+        unalignStall(i) := ctrlEntry.cross16Byte && !headCross16B
+      }
+      else {
+        toSbufferValid(i) := !uncacheStall(i) && !cboStall(i) && !unalignStall(i) && ctrlEntry.committed &&
+          !(ctrlEntry.vecMbCommit && !ctrlEntry.allValid || ctrlEntry.vecInactive) && //TODO: vecMbCommit will be remove in the future
+          toSbufferValid(i - 1)
+        // [NOTE]: entry.committed contains entry.allocated && entry.allValid && !entry.hasException && isRobHead.
+
+        unalignStall(i) := ctrlEntry.cross16Byte || headCross16B
+      }
+    }
+
+    for(i <- 0 until EnsbufferWidth) {
+      val port      = writeSbufferWire(i)
+      val dataEntry = dataEntries(i)
+      val ctrlEntry = ctrlEntries(i)
+
+      port.bits.data     := writeSbufferData(i)
+      port.bits.mask     := writeSbufferMask(i)
+      port.bits.addr     := writeSbufferPaddr(i)
+      port.bits.vaddr    := writeSbufferVaddr(i)
+
+      port.bits.wline    := ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
+      port.bits.prefetch := dataEntry.prefetch
+      port.bits.vecValid := true.B
+      port.valid         := toSbufferValid(i)
+
+      XSError(ctrlEntry.vecInactive && !ctrlEntry.isVec, s"inactive element must be vector! ${i}")
+      XSError(ctrlEntry.vecMbCommit && !ctrlEntry.isVec, s"vecMbCommit element must be vector! ${i}")
+    }
+
+    dataQueue.io.fromDeqModule.zip(writeSbufferWire).map{ case (sink, source) =>
+      sink               <> source
+    }
+
+    io.writeToSbuffer    <> dataQueue.io.toSbuffer
+
+    /*============================================ deqPtr generate ===================================================*/
+    /*
+    * NOTE: Only when port 0 and port 1 are ready can write cross16B request, so only use io.writeToSbuffer.req.head.fire
+    *       to calculate sbufferFireNum.
+    * deqPtr will move when [write to sbuffer / writeback / vector inactive element]
+    * rdataPtr will move when [nc request fire / write to SQ2SBPipelineConnect_i / vector inactive element]
+    * NOTE: when deq mmio/cbo, rdataPtr === deqPtr, because mmio/cbo need to execute at head of StoreQueue.
+    * */
+    private val sbufferFireNum = Mux(cross16BDeqReg,
+      Cat(RegNext(io.writeToSbuffer.req.head.fire), 0.U),
+      Cat(io.writeToSbuffer.req.map{case p => RegNext(p.fire)}))
+
+    // [NOTE]: when point a inactive entry, move pointer.
+    private val deqPtrVectorInactiveValid = WireInit(VecInit(Seq.fill(EnsbufferWidth)(false.B)))
+
+    deqCtrlEntries.zip(deqDataEntries).zipWithIndex.map{case ((ctrl, data), i) =>
+      deqPtrVectorInactiveValid(i) := ctrl.allocated && ctrl.committed &&
+        (ctrl.vecMbCommit && !ctrl.allValid || ctrl.vecInactive) //TODO: vecMbCommit will be remove in the future
+    }
+
+    private val deqPtrVectorInactiveMove = Cat(deqPtrVectorInactiveValid.zipWithIndex.map{case (v, i) =>
+      if(i == 0) v
+      else v && (deqPtrVectorInactiveValid(i - 1) || sbufferFireNum(i - 1).asBool)
+    })
+
+    private val uncacheMove = VecInit(deqCtrlEntries.map(x => x.allocated && x.handleFinish && x.committed)).asUInt
+
+    // sbufferFireNum need to RegNext, because write to sbuffer need 2 cycle, storeQueue need to forward 1 more cycle
+    val deqCount = Cat(sbufferFireNum, deqPtrVectorInactiveMove, uncacheMove) // timing is ok ?
+
+    io.sqDeqCnt := PopCount(VecInit(deqCount).asUInt)
+    io.deqPtrExtNext := io.deqPtrExt.map(_ + io.sqDeqCnt)
+
+    private val pipelineConnectFireNum = Mux(headCross16B,
+      Cat(writeSbufferWire.head.fire, 0.U),
+      Cat(writeSbufferWire.map(_.fire)))
+    // nc/mmio/cbo deq
+    private val otherMove        = uncacheState === UncacheState.sendReq && io.toUncacheBuffer.req.fire && isNC ||
+      io.writeBack.fire
+
+    // [NOTE]: when point a inactive entry, move pointer.
+    private val rdataPtrVectorInactiveValid = WireInit(VecInit(Seq.fill(EnsbufferWidth)(false.B)))
+
+    ctrlEntries.zip(dataEntries).zipWithIndex.map{case ((ctrl, data), i) =>
+      rdataPtrVectorInactiveValid(i) := ctrl.allocated && ctrl.committed && dataQueue.io.empty &&
+      (ctrl.vecMbCommit && !ctrl.allValid || ctrl.vecInactive) //TODO: vecMbCommit will be remove in the future
+    }
+
+    private val rdataPtrVectorInactiveMove = Cat(rdataPtrVectorInactiveValid.zipWithIndex.map{case (v, i) =>
+      if(i == 0) v
+      else v && (rdataPtrVectorInactiveValid(i - 1) || pipelineConnectFireNum(i - 1).asBool)
+    })
+
+    private val rdataMoveCnt = Cat(pipelineConnectFireNum, rdataPtrVectorInactiveMove, otherMove)
+
+    io.rdataPtrMoveCnt        := PopCount(rdataMoveCnt)
+
+    /*============================================ other connection ==================================================*/
+    io.perfMmioBusy := uncacheState =/= UncacheState.idle
+    io.toRob.mmioBusy := uncacheState =/= UncacheState.idle
+
+    if(debugEn) {
+      // [NOTE]: low 4 bit of addr/vaddr will be omitted in the sbuffer, but it will be used for difftest.
+      for (i <- 0 until EnsbufferWidth) {
+        io.pmaStore.foreach { case sink =>
+          sink(i).valid := writeSbufferWire(i).fire
+          sink(i).bits.addr := writeSbufferWire(i).bits.addr
+          sink(i).bits.data := writeSbufferWire(i).bits.data
+          sink(i).bits.mask := writeSbufferWire(i).bits.mask
+          sink(i).bits.wline := writeSbufferWire(i).bits.wline
+          sink(i).bits.vecValid := writeSbufferWire(i).bits.vecValid
+          sink(i).bits.diffIsHighPart := diffIsHighPart(i) // indicate whether valid data in high 64-bit, only for scalar store event!
+        }
+      }
+    }
+
+    /*=============================================== debug dontTouch =================================================*/
+    if(debugEn) {
+      dontTouch(toSbufferValid)
+      dontTouch(writeSbufferData)
+      dontTouch(writeSbufferMask)
+      dontTouch(writeSbufferPaddr)
+      dontTouch(writeSbufferVaddr)
+      dontTouch(unalignMask)
+      dontTouch(deqCount)
+      dontTouch(outMask)
+      dontTouch(outData)
+      dontTouch(writeSbufferWire)
+      dontTouch(deqPtrVectorInactiveValid)
+      dontTouch(deqPtrVectorInactiveMove)
+      dontTouch(rdataPtrVectorInactiveValid)
+      dontTouch(rdataPtrVectorInactiveMove)
+    }
+  }
+  /*==================================================================================================================*/
+  /* UnalignQueue will save the second physical address of the oldest SQUnalignQueueSize crossPage unaligned requests.*/
+  private class UnalignQueue(val param: ExeUnitParams)(implicit p: Parameters) extends LSQModule {
+    val io = IO(new Bundle {
+      val redirect       = Flipped(ValidIO(new Redirect))
+      val fromStaS2      = Vec(StorePipelineWidth, Flipped(DecoupledIO(new UnalignQueueIO)))
+      val fromSQ = new Bundle {
+        val addrReadyPtr = Input(new SqPtr)
+      }
+      val toDeqModule = ValidIO(new Bundle {
+        val paddr        = UInt(PAddrBits.W)
+        val sqIdx        = new SqPtr
+      })
+    })
+    private val enqWidth: Int  = io.fromStaS2.length
+    private val queueSize: Int = SQUnalignQueueSize
+
+    private val entries    = Reg(Vec(queueSize, new UnalignBufferEntry())) // no need to reset!
+    private val allocated  = RegInit(VecInit(Seq.fill(queueSize)(false.B)))
+    private val enqPtr     = RegInit(0.U.asTypeOf(new UqPtr))
+    private val deqPtr     = RegInit(0.U.asTypeOf(new UqPtr))
+    private val needCancel = WireInit(VecInit(Seq.fill(queueSize)(false.B)))
+
+    private val headEntry  = entries(deqPtr.value)
+
+    private val empty      = enqPtr.value === deqPtr.value && enqPtr.flag === deqPtr.flag
+    private val full       = enqPtr.value === deqPtr.value && enqPtr.flag =/= deqPtr.flag
+
+    // enq
+    private val canEnq     = io.fromStaS2.map{case port => port.fire} // one-hot, only second request of the unaligned need to enter.
+    private val doEnq      = canEnq.reduce(_ || _)
+    private val doEnqReq   = Mux1H(canEnq, io.fromStaS2.map(_.bits))
+
+    when(doEnq) {
+      entries(enqPtr.value).robIdx     := doEnqReq.robIdx
+      entries(enqPtr.value).paddrHigh  := doEnqReq.paddr(PAddrBits - 1, PageOffsetWidth)
+      entries(enqPtr.value).sqIdx      := doEnqReq.sqIdx
+    }
+
+    (0 until queueSize).map{i =>
+      when(needCancel(i)) { // when redirect, unalignQueue not allow enqueue.
+        allocated(i) := false.B
+      }.otherwise{
+        allocated(i) := (i.U === enqPtr.value) && doEnq
+      }
+    }
+
+    // update pointer
+    needCancel.zipWithIndex.map{case (sink, i) =>
+      sink := entries(i).robIdx.needFlush(io.redirect) && allocated(i)
+    }
+
+    private val redirectCount = PopCount(needCancel)
+
+    when(io.redirect.valid) {
+      enqPtr := enqPtr - redirectCount
+    }.otherwise {
+      when(doEnq) {
+        enqPtr := enqPtr + 1.U
+      }
+    }
+
+    when(io.toDeqModule.fire) {
+      deqPtr := deqPtr + 1.U
+    }
+    // When enqPtr.flag = 1, enqPtr.value = 0, deqPtr.flag=0, deqPtr.value = 0, the API '<'  fails to function correctly.
+    XSError(enqPtr < deqPtr && !full, s"Something wrong in UnalignQueue!")
+    // connection
+    io.toDeqModule.bits.paddr := headEntry.paddr
+    io.toDeqModule.bits.sqIdx := headEntry.sqIdx
+    io.toDeqModule.valid      := !empty
+
+    io.fromStaS2.map{case sink =>
+      sink.ready := !full && io.fromSQ.addrReadyPtr === sink.bits.sqIdx && !io.redirect.valid
+    }
+
+  }
+
+  val io = IO(new StoreQueueIO(param))
+  println("StoreQueue: size:" + StoreQueueSize)
+
+  // entries define
+  val dataEntries        = Reg(Vec(StoreQueueSize, new SQDataEntryBundle())) // no need to reset
+  val ctrlEntries        = RegInit(VecInit(Seq.fill(StoreQueueSize)(0.U.asTypeOf(new SQCtrlEntryBundle)))) // need to reset
+
+  // ptr define
+  val enqPtrExt          = RegInit(VecInit((0 until io.enq.req.length).map(_.U.asTypeOf(new SqPtr))))
+  // when io.writeToSbuffer_i.fire or writeback.fire, deqPtr will move.
+  //
+  // It should be noted that the deqPtr move is a store request at the end of the store queue lifecycle,
+  // whereas the rdataPtr move is not.
+  //
+  val deqPtrExt          = RegInit(VecInit((0 until EnsbufferWidth).map(_.U.asTypeOf(new SqPtr))))
+  // Because deq need multi cycle, use rdataPtr to read and split next EnsbufferWidth entries.
+  // when
+  // 1. head[Ctrl & Data]entries write to pipeline that between StoreQueue and Sbuffer.
+  // 2. nc send to uncacheBuffer.
+  // it will be move.
+  //
+  // rdataPtr may be equal to deqPtr when [MMIO/CBO].
+  val rdataPtrExt        = RegInit(VecInit((0 until EnsbufferWidth).map(_.U.asTypeOf(new SqPtr))))
+  val cmtPtrExt          = RegInit(VecInit((0 until CommitWidth).map(_.U.asTypeOf(new SqPtr))))
+  val addrReadyPtrExt    = RegInit(0.U.asTypeOf(new SqPtr))
+  val dataReadyPtrExt    = RegInit(0.U.asTypeOf(new SqPtr))
+
+  val validCount         = distanceBetween(enqPtrExt(0), deqPtrExt(0))
+  val allowEnqueue       = validCount <= (StoreQueueSize - LSQStEnqWidth).U
+  val needCancel         = Wire(Vec(StoreQueueSize, Bool()))
+
+  // the means of `head` is the next request that StoreQueue need to process.
+  val rdataDataEntries    = rdataPtrExt.map{ case ptr =>
+    dataEntries(ptr.value)
+  }
+  val rdataCtrlEntries    = rdataPtrExt.map{ case ptr =>
+    ctrlEntries(ptr.value)
+  }
+  val deqCtrlEntries      = deqPtrExt.map{ case ptr =>
+    ctrlEntries(ptr.value)
+  }
+  val deqDataEntries      = deqPtrExt.map{ case ptr =>
+    dataEntries(ptr.value)
+  }
+
+  /*========================================== Module define & connection ============================================*/
+  // use `private` to limit module connection within this module.
+
+  private val forwardModule         = Module(new ForwardModule(param))
+  private val deqModule             = Module(new DeqModule(param))
+  private val unalignQueue          = Module(new UnalignQueue(param))
+
+  // forward connection
+  forwardModule.io.query           <> io.forward
+  forwardModule.io.ctrlInfo.deqPtr := deqPtrExt(0)
+  forwardModule.io.ctrlInfo.enqPtr := enqPtrExt(0)
+  dataEntries.zip(forwardModule.io.dataEntriesIn).foreach{ case (source, sink) =>
+    sink := source
+  }
+  ctrlEntries.zip(forwardModule.io.ctrlEntriesIn).foreach { case (source, sink) =>
+    sink := source
+  }
+
+  // deqModule connection
+  deqModule.io.redirect         := io.redirect
+  deqModule.io.rdataCtrlEntries.zip(rdataCtrlEntries).foreach{ case (sink, source) =>
+    sink := source
+  }
+  deqModule.io.rdataDataEntries.zip(rdataDataEntries).foreach{ case (sink, source) =>
+    sink := source
+  }
+  deqModule.io.deqCtrlEntries.zip(deqCtrlEntries).foreach{ case (sink, source) =>
+    sink := source
+  }
+  deqModule.io.deqDataEntries.zip(deqDataEntries).foreach{ case (sink, source) =>
+    sink := source
+  }
+  deqModule.io.toUncacheBuffer  <> io.toUncacheBuffer
+  deqModule.io.toDCache         <> io.toDCache
+  deqModule.io.fromRob          <> io.fromRob
+  deqModule.io.toRob            <> io.toRob
+  deqModule.io.writeToSbuffer   <> io.writeToSbuffer
+  io.writeBack                  <> deqModule.io.writeBack
+  io.sbufferCtrl                <> deqModule.io.sbufferCtrl
+  deqModule.io.fromUnalignQueue <> unalignQueue.io.toDeqModule
+  deqModule.io.deqPtrExt        := deqPtrExt
+  deqModule.io.rdataPtrExt      := rdataPtrExt
+  deqModule.io.validCnt         := validCount
+  io.exceptionInfo              := deqModule.io.exceptionInfo
+
+  val deqPtrExtNext = deqModule.io.deqPtrExtNext
+  val sqDeqCnt      = deqModule.io.sqDeqCnt
+  val mmioBusy      = deqModule.io.perfMmioBusy
+  val diffPmaStore  = deqModule.io.pmaStore
+  val rdataMoveCnt  = deqModule.io.rdataPtrMoveCnt
+
+  // unalignQueue connection
+  unalignQueue.io.redirect            := io.redirect
+  unalignQueue.io.fromSQ.addrReadyPtr := addrReadyPtrExt
+  unalignQueue.io.fromStaS2.zip(io.fromStoreUnit.unalignQueueReq).map{case (sink, source) =>
+    sink <> source
+  }
+
+}
+
+
+class NewStoreQueue(implicit p: Parameters) extends NewStoreQueueBase with HasPerfEvents {
+
+  /**
+   * Enqueue at dispatch
+   *
+   * Currently, StoreQueue only allows enqueue when #emptyEntries > EnqWidth
+   * Dynamic enq based on numLsElem number
+   */
+
+  io.enq.canAccept := allowEnqueue
+  val canEnqueue = io.enq.req.map(_.valid)
+  val enqCancel = io.enq.req.map(_.bits.uop.robIdx.needFlush(io.redirect))
+  val vStoreFlow = io.enq.req.map(_.bits.uop.numLsElem.asTypeOf(UInt(elemIdxBits.W)))
+  val validVStoreFlow = vStoreFlow.zipWithIndex.map{case (vStoreFlowNumItem, index) =>
+    Mux(!RegNext(io.redirect.valid) && canEnqueue(index), vStoreFlowNumItem, 0.U)}
+  val validVStoreOffset = vStoreFlow.zip(io.enq.req).map{case (flow, req) => Mux(req.bits.needAlloc, flow, 0.U)}
+  val validVStoreOffsetRShift = 0.U +: validVStoreOffset.take(vStoreFlow.length - 1)
+
+  val enqLowBound = io.enq.req.map(_.bits.uop.sqIdx)
+  val enqUpBound  = io.enq.req.map(x => x.bits.uop.sqIdx + x.bits.uop.numLsElem)
+  val enqCrossLoop = enqLowBound.zip(enqUpBound).map{case (low, up) => low.flag =/= up.flag}
+
+  // TODO: vecMbCommit will be remove in the future.
+  val vecCommittmp = Wire(Vec(StoreQueueSize, Vec(VecStorePipelineWidth, Bool())))
+  val vecCommit = Wire(Vec(StoreQueueSize, Bool()))
+
+  for(i <- 0 until StoreQueueSize) {
+
+    /*================================================================================================================*/
+    /*================================================= enq ==========================================================*/
+    /*================================================================================================================*/
+
+    val entryCanEnqSeq = (0 until io.enq.req.length).map { j =>
+      val entryHitBound = Mux(
+        enqCrossLoop(j),
+        enqLowBound(j).value <= i.U || i.U < enqUpBound(j).value,
+        enqLowBound(j).value <= i.U && i.U < enqUpBound(j).value
+      )
+      canEnqueue(j) && !enqCancel(j) && entryHitBound
+    }
+
+    val entryCanEnq = entryCanEnqSeq.reduce(_ || _)
+    val selectBits = ParallelPriorityMux(entryCanEnqSeq, io.enq.req.map(_.bits))
+
+    val deqCancel = VecInit(deqPtrExt.zipWithIndex.map{case (ptr, j) =>
+      ptr.value === i.U && sqDeqCnt > j.U
+    }).asUInt.orR
+
+    val handleFinishSet = rdataPtrExt.head.value === i.U &&
+      (io.writeBack.fire || io.toUncacheBuffer.req.fire && isPbmtNC(dataEntries(i).memoryType))
+
+    when (entryCanEnq) {
+      connectSamePort(dataEntries(i).uop, selectBits.uop) //TODO: will be remove in the future.
+    }.elsewhen(deqCancel || needCancel(i)) {
+
+    }
+    if(debugEn) {
+      when (entryCanEnq){
+        dataEntries(i).debugUop.get := selectBits.debugUop.get
+      }
+    }
+
+    when (entryCanEnq) {
+      ctrlEntries(i).allocated  := true.B
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).allocated  := false.B
+    }
+
+    when(entryCanEnq) {
+      ctrlEntries(i).handleFinish := false.B
+    }.elsewhen(handleFinishSet) {
+      ctrlEntries(i).handleFinish := true.B
+    }
+
+    XSError(ctrlEntries(i).allocated && entryCanEnq, s"entry double allocate! index: ${i}\n")
+    XSError(!ctrlEntries(i).allocated && (ctrlEntries(i).addrValid || ctrlEntries(i).dataValid), s"invalid entry have addrValid or dataValid! index: ${i}\n")
+
+    for (i <- 0 until io.enq.req.length) {
+      val sqIdx = enqPtrExt(0) + validVStoreOffsetRShift.take(i + 1).reduce(_ + _)
+      val index = io.enq.req(i).bits.uop.sqIdx
+      XSError(canEnqueue(i) && !enqCancel(i) && (!io.enq.canAccept || !io.enq.lqCanAccept), s"must accept $i\n")
+      XSError(canEnqueue(i) && !enqCancel(i) && index.value =/= sqIdx.value, s"must be the same entry $i\n")
+      io.enq.resp(i).sqIdx := sqIdx
+    }
+    XSDebug(p"(ready, valid): ${io.enq.canAccept}, ${Binary(Cat(io.enq.req.map(_.valid)))}\n")
+
+    /*================================================================================================================*/
+    /*=============================================== sta ctrl =======================================================*/
+    /*================================================================================================================*/
+    /**
+     * In storeUnit stage 1, paddr is ready                --> set addrValid
+     *                       unalign check is ready        --> set unaligned, cross16Byte
+     * In storeUnit stage 2, PMP/PMA check result is ready --> set hasException, memoryType
+     *                       dcache resp is ready          --> set prefetch
+     * */
+
+    /*======================================== staIn [sta Stage 1] ===================================================*/
+
+    val staValidSetVec  = VecInit(io.fromStoreUnit.storeAddrIn.map{case port =>
+      val index         = port.bits.uop.sqIdx.value
+      val setValid      = index === i.U && port.fire && !needCancel(i)
+      setValid
+    }) // one-hot select vec
+
+    val staSetValid    = staValidSetVec.reduce(_ || _)
+    val addrValidSet   = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
+      port.bits.isLastRequest && !port.bits.tlbMiss && staValidSetVec(j)
+    }.reduce(_ || _)
+    val cross16ByteSet = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
+      port.bits.isUnalign && !port.bits.unalignWithin16Byte && staValidSetVec(j)
+    }.reduce(_ || _)
+    val cboSetVec = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
+      LSUOpType.isCboAll(port.bits.uop.fuOpType) && staValidSetVec(j)
+    }
+    val isCboSet = cboSetVec.reduce(_ || _)
+
+    when(staSetValid) {
+      ctrlEntries(i).addrValid    := addrValidSet // need hasException?
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).addrValid    := false.B
+    }
+
+    when(staSetValid) { // no need to clean when deq or cancel, because it will be set when set addrValid
+      ctrlEntries(i).cross16Byte  := cross16ByteSet
+      ctrlEntries(i).isCbo        := isCboSet
+    } // don't need to set false for low power, it will be set every instruction.
+
+    io.fromStoreUnit.storeAddrIn.zipWithIndex.map{case (port, j) =>
+      val index         = port.bits.uop.sqIdx.value
+      val setValid      = cboSetVec(j)
+      when(setValid) {
+        dataEntries(i).cboType   := Mux1H(List(
+          isCboClean(port.bits.uop.fuOpType(1, 0)) -> CboType.clean, // TODO: don't use (1, 0)
+          isCboFlush(port.bits.uop.fuOpType(1, 0)) -> CboType.flush,
+          isCboInval(port.bits.uop.fuOpType(1, 0)) -> CboType.inval,
+          isCboZero(port.bits.uop.fuOpType(1, 0))  -> CboType.zero
+        ))
+      }
+    }
+
+
+    if(debugEn) {
+      val unalignWithin16BSet = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
+        port.bits.isUnalign && port.bits.unalignWithin16Byte && staValidSetVec(j)
+      }.reduce(_ || _)
+      when(staSetValid) {
+        ctrlEntries(i).unalignWithin16Byte.foreach(_ := unalignWithin16BSet)
+      }
+    }
+
+    // TODO: fix this for unalign store
+    //TODO: vector element maybe set addrValid twice because of replay uop, which will be remove in the future.
+//    XSError(ctrlEntries(i).addrValid && staSetValid && !ctrlEntries(i).isVec, s"[addrValid] double allocate! index: ${i}\n")
+
+    /*======================================= staInRe [sta Stage 2] ==================================================*/
+
+    val staReValidVec = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
+      RegNext(!port.bits.tlbMiss && staValidSetVec(j)) && !needCancel(i) //TODO: use valid of s1, will be remove in the future.
+    } // at s2 stage of storeUnit
+
+    val staReValid = (0 until staReValidVec.length).map { j =>
+      staReValidVec(j) && io.fromStoreUnit.storeAddrInRe(j).isLastRequest
+    }.reduce(_ || _) // at s2 stage of storeUnit
+
+    val prefetchSet     = io.fromStoreUnit.storeAddrInRe.zipWithIndex.map { case (port, j) =>
+      port.cacheMiss && staReValidVec(j)
+    }.reduce(_ || _)
+    val hasExceptionSet = io.fromStoreUnit.storeAddrInRe.zipWithIndex.map { case (port, j) =>
+      port.hasException && staReValidVec(j)
+    }.reduce(_ || _)
+    val ncSet           = io.fromStoreUnit.storeAddrInRe.zipWithIndex.map { case (port, j) =>
+      port.nc && staReValidVec(j)
+    }.reduce(_ || _)
+    val mmioSet         = io.fromStoreUnit.storeAddrInRe.zipWithIndex.map { case (port, j) =>
+      port.mmio && staReValidVec(j)
+    }.reduce(_ || _)
+    val memBackTypeSet  = io.fromStoreUnit.storeAddrInRe.zipWithIndex.map { case (port, j) =>
+      port.memBackTypeMM && staReValidVec(j)
+    }.reduce(_ || _) // memBackTypeMM  = true.B means it is main memory region , false.B means it is IO region.
+
+    when(staReValid) { // no need to clean when deq or cancel, because it will be used when waitStoreS2 == false
+      dataEntries(i).prefetch := prefetchSet
+    }//  don't need to set false for low power, it will be set every instruction.
+
+    when(staSetValid) {
+      ctrlEntries(i).waitStoreS2  := true.B
+    }.elsewhen(staReValid) { // no need to clean when deq or cancel, because it will be set when set addrValid
+      ctrlEntries(i).waitStoreS2  := false.B
+    }
+
+    when(staReValid) {
+      ctrlEntries(i).hasException := hasExceptionSet
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).hasException := false.B
+    }
+
+    when(staReValid) { // no need to clean when deq or cancel, because it will be used when waitStoreS2 == false
+      /*
+         cacheable: "00".U
+         pbmtNc:    "01".U
+         pbmtIo:    "10".U
+         io:        "11".U // IO device
+      */
+      dataEntries(i).memoryType := Cat(mmioSet, ncSet || !memBackTypeSet)
+      /*
+       * [NOTE]: To explain the logical operations above, the truth table is as follows:
+       * The signal of [memBackTypeMM] means request is main memory region.
+       *
+       *           |  memBackTypeSet | !memBackTypeSet | ncSet | mmioSet | memoryType[1] | memoryType[0] |
+       * Cacheable |       1         |       0         |   0   |    0    |        0      |       0       |
+       * NC        |       1         |       0         |   1   |    0    |        0      |       1       |
+       * PbmtIO    |       1         |       0         |   0   |    1    |        1      |       0       |
+       * IO        |       0         |       1         |   0   |    1    |        1      |       1       |
+       *                                     |             |        |             ^              ^
+       *                                     |             |        +-------------+              |
+       *                                     +----- or ----+                                     |
+       *                                            |                                            |
+       *                                            +--------------------------------------------+
+       * */
+    }//  don't need to set false for low power, it will be set every instruction.
+
+    XSError(!mmioSet && !memBackTypeSet && !hasExceptionSet && staReValid, s"mmio not set but memBackTypeMM is zero! ${i}\n")
+
+    /*================================================================================================================*/
+    /*=============================================== std ctrl =======================================================*/
+    /*================================================================================================================*/
+
+    val dataValidSet = VecInit(io.storeDataIn.map{ case port =>
+      val index = port.bits.sqIdx.value
+      index === i.U && port.fire && !needCancel(i)
+    }).asUInt.orR
+
+    when(dataValidSet) {
+      ctrlEntries(i).dataValid := true.B
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).dataValid := false.B
+    }
+
+    //TODO: vector element maybe set dataValid twice because of replay uop, which will be remove in the future.
+//    XSError(ctrlEntries(i).dataValid && dataValidSet && !ctrlEntries(i).isVec, s"[dataValid] double allocate! index: ${i}\n")
+    XSError(!ctrlEntries(i).allocated && deqCancel, s"double deq! index: ${i}\n")
+
+    /*================================================================================================================*/
+    /*============================================== vector ctrl =====================================================*/
+    /*================================================================================================================*/
+
+    when(entryCanEnq) {
+      ctrlEntries(i).isVec := FuType.isVStore(selectBits.uop.fuType)
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).isVec := false.B
+    }
+
+    // TODO: vecMbCommit will be remove in the future.
+    val fbk = io.fromVMergeBuffer
+    for (j <- 0 until VecStorePipelineWidth) {
+      vecCommittmp(i)(j) := fbk(j).valid && (fbk(j).bits.isCommit || fbk(j).bits.isFlush) &&
+        dataEntries(i).uop.robIdx === fbk(j).bits.robidx && dataEntries(i).uop.uopIdx === fbk(j).bits.uopidx
+    }
+    // vector feedback may occur with deqCancel/needCancel at the same time
+    vecCommit(i) := vecCommittmp(i).reduce(_ || _) && !needCancel(i) && !deqCancel && ctrlEntries(i).allocated
+
+    when (vecCommit(i)) {
+      ctrlEntries(i).vecMbCommit := true.B
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).vecMbCommit := false.B
+    }
+
+    ctrlEntries(i).vecInactive := false.B //TODO: will be use in the future
+
+    /*================================================================================================================*/
+    /*============================================== cancel ctrl =====================================================*/
+    /*================================================================================================================*/
+
+    needCancel(i) := !ctrlEntries(i).committed && dataEntries(i).uop.robIdx.needFlush(io.redirect) && ctrlEntries(i).allocated
+
+    // debug don't touch
+    if(debugEn) {
+      dontTouch(deqCancel)
+      dontTouch(staSetValid)
+      dontTouch(staReValid)
+      dontTouch(prefetchSet)
+      dontTouch(hasExceptionSet)
+      dontTouch(ncSet)
+      dontTouch(memBackTypeSet)
+      dontTouch(vecCommittmp)
+      dontTouch(vecCommit)
+    }
+  }
+
+  /*=============================================== update ptr =======================================================*/
+
+  /**
+   * Update enqPtr
+   * */
+  val enqCancelValid = canEnqueue.zip(io.enq.req).map{case (v , x) =>
+    v && x.bits.uop.robIdx.needFlush(io.redirect)
+  }
+  val enqCancelNum = enqCancelValid.zip(vStoreFlow).map{case (v, flow) =>
+    Mux(v, flow, 0.U)
+  }
+  val lastEnqCancel = RegEnable(enqCancelNum.reduce(_ + _), io.redirect.valid) // 1 cycle after redirect
+
+  val lastCycleCancelCount = PopCount(RegEnable(needCancel, io.redirect.valid)) // 1 cycle after redirect
+  val lastCycleRedirect = RegNext(io.redirect.valid) // 1 cycle after redirect
+  val enqNumber = validVStoreFlow.reduce(_ + _)
+
+  val lastlastCycleRedirect=RegNext(lastCycleRedirect)// 2 cycle after redirect
+  val redirectCancelCount = RegEnable(lastCycleCancelCount + lastEnqCancel, 0.U, lastCycleRedirect) // 2 cycle after redirect
+
+  when (lastlastCycleRedirect) {
+    // we recover the pointers in 2 cycle after redirect for better timing
+    enqPtrExt := VecInit(enqPtrExt.map(_ - redirectCancelCount))
+  }.otherwise {
+    // lastCycleRedirect.valid or nornal case
+    // when lastCycleRedirect.valid, enqNumber === 0.U, enqPtrExt will not change
+    enqPtrExt := VecInit(enqPtrExt.map(_ + enqNumber))
+  }
+  assert(!(lastCycleRedirect && enqNumber =/= 0.U))
+
+  /**
+   * Update addr/dataReadyPtr when issue from rs
+   */
+  // update issuePtr
+  val IssuePtrMoveStride = 4
+  require(IssuePtrMoveStride >= 2)
+
+  val addrReadyLookupVec = (0 until IssuePtrMoveStride).map(addrReadyPtrExt + _.U)
+  val addrReadyLookup = addrReadyLookupVec.map(ptr =>
+//    (MemoryType.isPMPIO(ctrlEntries(ptr.value).memoryType) || ctrlEntries(ptr.value).addrValid || ctrlEntries(ptr.value).vecInactive)
+      (ctrlEntries(ptr.value).addrValid || ctrlEntries(ptr.value).vecInactive || ctrlEntries(ptr.value).vecMbCommit) &&
+        ctrlEntries(ptr.value).allocated && ptr =/= enqPtrExt(0))
+  val nextAddrReadyPtr = addrReadyPtrExt + PriorityEncoder(VecInit(addrReadyLookup.map(!_) :+ true.B))
+  addrReadyPtrExt := nextAddrReadyPtr
+
+  val stAddrReadyVecWire = Wire(Vec(StoreQueueSize, Bool()))
+  (0 until StoreQueueSize).map(i => {
+//    stAddrReadyVecReg(i) := ctrlEntries(i).allocated && (mmio(i) || addrvalid(i) || (isVec(i) && vecMbCommit(i)))
+    stAddrReadyVecWire(i) := (ctrlEntries(i).addrValid || ctrlEntries(i).vecInactive || ctrlEntries(i).vecMbCommit) &&
+      ctrlEntries(i).allocated
+  })
+
+  when (io.redirect.valid) {
+    addrReadyPtrExt := Mux(
+      isAfter(cmtPtrExt(0), deqPtrExt(0)),
+      cmtPtrExt(0),
+      deqPtrExtNext(0) // for mmio insts, deqPtr may be ahead of cmtPtr
+    )
+
+    dataReadyPtrExt := Mux(
+      isAfter(cmtPtrExt(0), deqPtrExt(0)),
+      cmtPtrExt(0),
+      deqPtrExtNext(0) // for mmio insts, deqPtr may be ahead of cmtPtr
+    )
+  }
+
+    // enqPtr update
+  val dataReadyLookupVec = (0 until IssuePtrMoveStride).map(dataReadyPtrExt + _.U)
+  val dataReadyLookup = dataReadyLookupVec.map(ptr =>
+      (ctrlEntries(ptr.value).addrValid && !ctrlEntries(ptr.value).waitStoreS2 && //TODO: remove waitStoreS2 in the future
+        (isMmio(dataEntries(ptr.value).memoryType) || ctrlEntries(ptr.value).dataValid) ||
+        ctrlEntries(ptr.value).vecMbCommit) && //TODO: vecMbCommit will be remove in the future, entry maybe inactive, so we nned to or vecMbCommit.
+      ctrlEntries(ptr.value).allocated &&
+      ptr =/= enqPtrExt(0)
+  )
+  val nextDataReadyPtr = dataReadyPtrExt + PriorityEncoder(VecInit(dataReadyLookup.map(!_) :+ true.B))
+  dataReadyPtrExt := nextDataReadyPtr
+
+  val stDataReadyVecReg = Wire(Vec(StoreQueueSize, Bool()))
+  (0 until StoreQueueSize).map(i => {
+    stDataReadyVecReg(i) := (ctrlEntries(i).addrValid && !ctrlEntries(i).waitStoreS2 && // ctrl memoryType is ready.
+        (isMmio(dataEntries(i).memoryType) || ctrlEntries(i).dataValid) ||
+      ctrlEntries(i).vecMbCommit) &&
+      ctrlEntries(i).allocated
+  })
+
+  // deqPtr logic
+  deqPtrExt := deqPtrExtNext
+  rdataPtrExt := rdataPtrExt.map(_ + rdataMoveCnt)
+
+  XSError(deqPtrExt(0) > rdataPtrExt(0), "Why deqPtr > rdataPtr? something error!")
+  XSError(deqPtrExt(0) > enqPtrExt(0),   "Why deqPtr > enqPtr? something error!")
+  /******************************************** store pipeline write **************************************************/
+  for (i <- 0 until StorePipelineWidth) {
+    val storeAddrIn   = io.fromStoreUnit.storeAddrIn(i)
+    val storeAddrInRe = io.fromStoreUnit.storeAddrInRe(i)
+    val stWbIdx       = storeAddrIn.bits.uop.sqIdx.value
+    val byteStart     = storeAddrIn.bits.vaddr(VWordOffset - 1, 0)
+    val byteOffset    = MemorySize.ByteOffset(storeAddrIn.bits.size)
+
+    // !isLastRequest && cross4KPage means it is first request of cross page unalign  --> save paddr
+    //  isLastRequest && cross4KPage means it is second request of cross page unalign --> not save paddr
+    // isLastRequest && !cross4KPage means it is normal request                       --> save paddr
+    when(storeAddrIn.fire && (!storeAddrIn.bits.isLastRequest || !storeAddrIn.bits.cross4KPage)){
+      // the second paddr of cross4KPage request will be write to unalign queue
+      dataEntries(stWbIdx).vaddr     := storeAddrIn.bits.vaddr
+      dataEntries(stWbIdx).paddrHigh := storeAddrIn.bits.paddr(PAddrBits - 1, PageOffsetWidth)
+      // only unit-stride use it, because unit-stride mask is not continue true.
+      dataEntries(stWbIdx).byteMask  := Mux(MemorySize.sizeIs(storeAddrIn.bits.size, MemorySize.Q),
+        storeAddrIn.bits.mask,
+        UIntToMask(MemorySize.CalculateSelectMask(byteStart, byteStart + byteOffset), VLENB))
+      dataEntries(stWbIdx).size      := storeAddrIn.bits.size
+
+      // debug singal
+      if(debugEn) {
+        dataEntries(stWbIdx).debugPaddr.get := storeAddrIn.bits.paddr
+      }
+    }
+    XSError(byteStart + byteOffset < byteStart && storeAddrIn.fire &&
+    (!storeAddrIn.bits.isLastRequest || !storeAddrIn.bits.cross4KPage),
+     "ByteStart > ByteEnd! at pipeline ${i}\n")
+  }
+
+  for (i <- 0 until StorePipelineWidth) {
+    val storeDataIn   = io.storeDataIn(i)
+    val stWbIdx       = storeDataIn.bits.sqIdx.value
+    when(storeDataIn.fire){
+      // if it's a cbo.zero, write zero.
+      dataEntries(stWbIdx).data  := Mux(storeDataIn.bits.fuOpType === LSUOpType.cbo_zero, 0.U, storeDataIn.bits.data)
+
+      // debug signal
+      if(debugEn) {
+        dataEntries(stWbIdx).debugVecUnalignedStart.get  := io.storeDataIn(i).bits.vecDebug.get.start
+        dataEntries(stWbIdx).debugVecUnalignedOffset.get := io.storeDataIn(i).bits.vecDebug.get.offset
+      }
+    }
+  }
+
+  /************************************************ commit logic ******************************************************/
+
+  /*
+  * If store have interrupt, do not to commit !!!!!!!!!
+  * At present don't have this situation.
+  * if is MMIO/NC/CBO, don't committed.
+  * */
+  val commitVec = WireDefault(VecInit(Seq.fill(CommitWidth)(false.B))) // default is false.B
+
+  for (i <- 0 until CommitWidth) {
+    val ptr = cmtPtrExt(i).value
+    val ctrlEntry = ctrlEntries(ptr)
+    val dataEntry = dataEntries(ptr)
+    val ptrNoRotate = cmtPtrExt(i) >= deqPtrExt.head // prevent pointer rotation
+    //TODO:
+    /*
+    * Currently three commit situation:
+    * [1]. normal Scalar Store Commit:      ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid --> move cmtPtr, set committed
+    * [2]. activate Vector Store Commit:    ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid && [vecMbcommit] --> move cmtPtr, set committed
+    * [3]. inactivate Vector Store Commit:  ptrNoRotate && allocated && noFlush && vecInactive  --> move cmtPtr, set committed
+    *
+    * Future three commit situation:
+    * [1]. normal Scalar Store Commit:      ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid --> move cmtPtr, set committed
+    * [2]. activate Vector Store Commit:    ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid --> move cmtPtr, set committed
+    * [3]. inactivate Vector Store Commit:  ptrNoRotate && allocated && noFlush && vecInactive  --> move cmtPtr, set committed
+    * */
+    when(ctrlEntries(ptr).allocated && !needCancel(ptr) &&
+      (isNotAfter(dataEntries(ptr).uop.robIdx, GatedRegNext(io.fromRob.pendingPtr)) &&
+      !ctrlEntries(ptr).hasException && !ctrlEntries(ptr).waitStoreS2 && (ctrlEntries(ptr).vecMbCommit || !ctrlEntries(ptr).isVec) &&
+      ctrlEntries(ptr).allValid || (ctrlEntries(ptr).vecMbCommit && !ctrlEntries(ptr).allValid || ctrlEntries(ptr).vecInactive))) { //TODO: vecMbCommit will be remove in the future
+      if(i == 0) {
+        commitVec(i)               := true.B
+      }
+      else {
+        commitVec(i)               := commitVec(i - 1)
+      }
+    } // commitVec default is false.B
+    //TODO: vecMbCommit will be remove in the future
+    ctrlEntries(ptr).committed   := Mux(ptrNoRotate, commitVec(i), ctrlEntries(ptr).committed)
+    XSError(!ctrlEntries(ptr).allocated && ctrlEntries(ptr).committed, "commit not allocated entry!\n")
+    XSError(ctrlEntries(ptr).allocated && ctrlEntries(ptr).vecInactive && !ctrlEntries(ptr).isVec, "inactive entry must be vector!\n")
+    XSError(ctrlEntries(ptr).allocated && ctrlEntries(ptr).vecMbCommit && !ctrlEntries(ptr).isVec, "vecMbCommit entry must be vector!\n")
+  }
+
+  val commitCount = PopCount(commitVec)
+  cmtPtrExt       := cmtPtrExt.map(_ + commitCount)
+
+  for (i <- 0 until EnsbufferWidth) {
+    val ptr = deqPtrExt(i).value
+    when(sqDeqCnt > i.U) {
+      ctrlEntries(ptr).committed := false.B
+    }
+  }
+
+  XSError(cmtPtrExt.head < deqPtrExt.head || cmtPtrExt.head < rdataPtrExt.head, "pointer update error!\n")
+  /************************************************* IO Assign ********************************************************/
+
+  io.toLoadQueue.stAddrReadySqPtr := addrReadyPtrExt
+  io.toLoadQueue.stDataReadySqPtr := dataReadyPtrExt
+
+  io.toLoadQueue.stDataReadyVec := GatedValidRegNext(stDataReadyVecReg)
+  io.toLoadQueue.stAddrReadyVec := GatedValidRegNext(stAddrReadyVecWire)
+
+  io.toLoadQueue.stIssuePtr := enqPtrExt(0)
+  io.sqDeqPtr := deqPtrExt(0)
+  io.sqDeqUopIdx := dataEntries(deqPtrExt(0).value).uop.uopIdx
+  io.sqDeqRobIdx := dataEntries(deqPtrExt(0).value).uop.robIdx
+
+  // Currently, storeQueue will always safe, no other uncommitted instructions may precede the wfi instruction.
+  io.wfi.wfiSafe := true.B
+  io.sqEmpty     := deqPtrExt(0) === enqPtrExt(0)
+  io.sqCancelCnt := redirectCancelCount
+  io.sqDeq       := RegNext(sqDeqCnt)
+
+  /*=============================================== debug ============================================================*/
+  if(debugEn) {
+    dontTouch(enqNumber)
+    dontTouch(lastlastCycleRedirect)
+    dontTouch(enqPtrExt)
+    dontTouch(deqPtrExt)
+    dontTouch(dataEntries)
+    dontTouch(commitVec)
+    dontTouch(ctrlEntries)
+  }
+
+  /************************************************* Difftest *********************************************************/
+  // Initialize when unenabled difftest.
+  io.diffStore.foreach(_ := DontCare) //TODO: FIX ME!!
+  // Consistent with the logic above.
+  // Only the vector store difftest required signal is separated from the rtl code.
+  val deqCanDoCbo = deqCtrlEntries.head.allValid && !deqCtrlEntries.head.hasException && deqCtrlEntries.head.allocated &&
+    deqCtrlEntries.head.isCbo
+  if (debugEn) {
+    /*=========================================== Data and Mask Generate =============================================*/
+
+    val outData        = Wire(Vec(EnsbufferWidth , UInt(VLEN.W)))
+    val outMask        = Wire(Vec(EnsbufferWidth , UInt((VLENB).W)))
+
+    for (i <- 0 until EnsbufferWidth) {
+      val selectOffset       = 0.U - dataEntries(i).byteStart // need to generate 0 align data and mask
+      val selectData         = (0 until VLENB).map(j => // generate circular right shift byte data.
+        j.U -> rotateByteRight(dataEntries(i).data, j * 8)
+      )
+
+      val byteMask           = dataEntries(i).byteMask
+      val selectMsk          = (0 until VLENB).map(j => // generate circular right shift byte data.
+        j.U -> rotateByteRight(byteMask, j)
+      )
+
+      outData(i)         := ParallelLookUp(selectOffset, selectData)
+      outMask(i)         := ParallelLookUp(selectOffset, selectMsk)
+    }
+//     commit cbo.inval to difftest
+    val cmoInvalEvent = DifftestModule(new DiffCMOInvalEvent)
+    cmoInvalEvent.coreid := io.hartId
+    cmoInvalEvent.valid  := io.writeBack.fire && deqCanDoCbo && isCboInval(deqDataEntries.head.cboType)
+    cmoInvalEvent.addr   := deqDataEntries.head.paddr
+
+//     DiffStoreEvent happens when rdataPtr moves.
+//     That is, pmsStore enter dataBuffer or ncStore enter Ubuffer
+    (0 until EnsbufferWidth).foreach { i =>
+      // when i = 0, the sqPtr is rdataPtr(0), which is rdataPtrExt(0), so it applies to NC as well.
+      val ptr = rdataPtrExt(i).value
+      io.diffStore.foreach{case sink =>
+        sink.diffInfo(i).uop            := dataEntries(ptr).debugUop.get
+        sink.diffInfo(i).start          := dataEntries(ptr).debugVecUnalignedStart.get
+        sink.diffInfo(i).offset         := dataEntries(ptr).debugVecUnalignedOffset.get
+        sink.pmaStore(i).valid          := diffPmaStore.get(i).valid
+        sink.pmaStore(i).bits           := diffPmaStore.get(i).bits
+      }
+    }
+
+    io.diffStore.foreach { case sink =>
+      sink.ncStore.valid := io.toUncacheBuffer.req.fire && io.toUncacheBuffer.req.bits.nc
+      sink.ncStore.bits := io.toUncacheBuffer.req.bits
+    }
+
+
+    (1 until EnsbufferWidth).foreach(i => when(io.writeToSbuffer.req(i).fire) { assert(io.writeToSbuffer.req(i - 1).fire) })
+    if (coreParams.dcacheParametersOpt.isEmpty) {
+      for (i <- 0 until EnsbufferWidth) {
+        val ptr = deqPtrExt(i).value
+        val ram = DifftestMem(64L * 1024 * 1024 * 1024, 8)
+        val wen = ctrlEntries(ptr).allocated && ctrlEntries(ptr).committed(ptr) && isCacheable(dataEntries(ptr).memoryType)
+        val waddr = ((rdataDataEntries(i).paddr - "h80000000".U) >> 3).asUInt
+        val wdata = Mux(rdataDataEntries(i).paddr(3), rdataDataEntries(i).data(127, 64), rdataDataEntries(i).data(63, 0))
+        val wmask = Mux(rdataDataEntries(i).paddr(3), outMask(i)(15, 8), outMask(i)(7, 0))
+        when (wen) {
+          ram.write(waddr, wdata.asTypeOf(Vec(8, UInt(8.W))), wmask.asBools)
+        }
+      }
+    }
+  }
+
+
+  /*********************************************** perf event *********************************************************/
+  val entriesUtilization = PopCount(ctrlEntries.map(e => (e.addrValid || e.dataValid) && e.allocated))
+  QueuePerf(StoreQueueSize, validCount, !allowEnqueue)
+  XSPerfHistogram("entries_util", entriesUtilization, true.B, 0, StoreQueueSize, 1)
+//  val vecValidVec = WireInit(VecInit((0 until StoreQueueSize).map(i => allocated(i) && isVec(i))))
+//  QueuePerf(StoreQueueSize, PopCount(vecValidVec), !allowEnqueue)
+  io.sqFull := !allowEnqueue
+  XSPerfAccumulate("mmioCycle", (mmioBusy)) // lq is busy dealing with uncache req
+  XSPerfAccumulate("mmioCnt", io.writeBack.fire && isMmio(rdataDataEntries.head.memoryType))
+  XSPerfAccumulate("mmio_wb_success", io.writeBack.fire && isMmio(rdataDataEntries.head.memoryType))
+  XSPerfAccumulate("mmio_wb_blocked", (io.writeBack.valid && !io.writeBack.ready && isMmio(rdataDataEntries.head.memoryType)))
+  XSPerfAccumulate("validEntryCnt", distanceBetween(enqPtrExt(0), deqPtrExt(0)))
+  XSPerfAccumulate("cmtEntryCnt", distanceBetween(cmtPtrExt(0), deqPtrExt(0)))
+  XSPerfAccumulate("nCmtEntryCnt", distanceBetween(enqPtrExt(0), cmtPtrExt(0)))
+
+  val perfValidCount = distanceBetween(enqPtrExt(0), deqPtrExt(0))
+  val perfEvents = Seq(
+    ("mmioCycle      ", WireInit(mmioBusy)),
+    ("mmioCnt        ", io.toUncacheBuffer.req.fire && !io.toUncacheBuffer.req.bits.nc),
+    ("mmio_wb_success", io.writeBack.fire && isMmio(rdataDataEntries.head.memoryType)),
+    ("mmio_wb_blocked", io.writeBack.valid && !io.writeBack.ready && isMmio(rdataDataEntries.head.memoryType)),
+    ("stq_1_4_valid  ", (perfValidCount < (StoreQueueSize.U/4.U))),
+    ("stq_2_4_valid  ", (perfValidCount > (StoreQueueSize.U/4.U)) & (perfValidCount <= (StoreQueueSize.U/2.U))),
+    ("stq_3_4_valid  ", (perfValidCount > (StoreQueueSize.U/2.U)) & (perfValidCount <= (StoreQueueSize.U*3.U/4.U))),
+    ("stq_4_4_valid  ", (perfValidCount > (StoreQueueSize.U*3.U/4.U))),
+  )
+  generatePerfEvent()
+
+}
+import top.Generator
+object NewStoreQueueMain extends App {
+  val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(
+    args :+ "--disable-always-basic-diff" :+ "--dump-fir" :+ "--fpga-platform" :+ "--target" :+ "verilog")
+
+  val defaultConfig = config.alterPartial({
+    // Get XSCoreParams and pass it to the "small module"
+    case XSCoreParamsKey => config(XSTileKey).head
+  })
+
+  Generator.execute(
+    firrtlOpts :+ "--full-stacktrace" :+ "--target-dir" :+ "storeQueue" :+ "--throw-on-first-error",
+    new NewStoreQueue()(defaultConfig),
+    firtoolOpts :+ "-O=release" :+ "--disable-annotation-unknown" :+ "--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none"
+  )
+//  emitVerilog(new NewStoreQueue()(defaultConfig), Array("--target-dir", "build/storeQueue", "--full-stacktrace"))
+
+  println("done")
+}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index e72959c718a..22044f4e4d3 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -71,42 +71,13 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     SD -> 0xff.U
   ))
 
-  def selectOldest[T <: LsPipelineBundle](valid: Seq[Bool], bits: Seq[T], index: Seq[UInt]): (Seq[Bool], Seq[T], Seq[UInt]) = {
-    assert(valid.length == bits.length)
-    if (valid.length == 0 || valid.length == 1) {
-      (valid, bits, index)
-    } else if (valid.length == 2) {
-      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
-      val resIndex = Seq.fill(2)(Wire(chiselTypeOf(index(0))))
-      for (i <- res.indices) {
-        res(i).valid := valid(i)
-        res(i).bits := bits(i)
-        resIndex(i) := index(i)
-      }
-      val oldest = Mux(valid(0) && valid(1),
-        Mux(isAfter(bits(0).uop.robIdx, bits(1).uop.robIdx) ||
-          (isNotBefore(bits(0).uop.robIdx, bits(1).uop.robIdx) && bits(0).uop.uopIdx > bits(1).uop.uopIdx), res(1), res(0)),
-        Mux(valid(0) && !valid(1), res(0), res(1)))
-
-      val oldestIndex = Mux(valid(0) && valid(1),
-        Mux(isAfter(bits(0).uop.robIdx, bits(1).uop.robIdx) ||
-          (bits(0).uop.robIdx === bits(1).uop.robIdx && bits(0).uop.uopIdx > bits(1).uop.uopIdx), resIndex(1), resIndex(0)),
-        Mux(valid(0) && !valid(1), resIndex(0), resIndex(1)))
-      (Seq(oldest.valid), Seq(oldest.bits), Seq(oldestIndex))
-    } else {
-      val left = selectOldest(valid.take(valid.length / 2), bits.take(bits.length / 2), index.take(index.length / 2))
-      val right = selectOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)), index.takeRight(index.length - (index.length / 2)))
-      selectOldest(left._1 ++ right._1, left._2 ++ right._2, left._3 ++ right._3)
-    }
-  }
-
   val io = IO(new Bundle() {
     val redirect        = Flipped(Valid(new Redirect))
     val enq             = Vec(enqPortNum, Flipped(new MisalignBufferEnqIO))
     val rob             = Flipped(new RobLsqIO)
     val splitStoreReq   = Decoupled(new LsPipelineBundle)
     val splitStoreResp  = Flipped(Valid(new SqWriteBundle))
-    val writeBack       = Decoupled(new ExuOutput(staParams.head))
+    val writeBack       = DecoupledIO(new NewExuOutput(staParams.head))
     val vecWriteBack    = Vec(VecStorePipelineWidth, Decoupled(new VecPipelineFeedbackIO(isVStore = true)))
     val overwriteExpBuf = Output(new XSBundle {
       val valid = Bool()
@@ -120,18 +91,24 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     val toVecSplit = Output(new MisBuffertoVecSplitIO) // robIdx in misalignedBuffer
   })
 
-  io.rob.mmio := 0.U.asTypeOf(Vec(LoadPipelineWidth, Bool()))
-  io.rob.uop  := 0.U.asTypeOf(Vec(LoadPipelineWidth, new DynInst))
+  io.rob.mmioBusy := false.B
 
   class StoreMisalignBufferEntry(implicit p: Parameters) extends LsPipelineBundle {
     val portIndex = UInt(log2Up(enqPortNum).W)
   }
+  private def selectOlder(left: StoreMisalignBufferEntry, right: StoreMisalignBufferEntry): Bool = {
+    isBefore(left.uop.robIdx, right.uop.robIdx) ||
+      (left.uop.robIdx === right.uop.robIdx && left.uop.uopIdx < right.uop.uopIdx)
+  }
+
   val req_valid = RegInit(false.B)
   val req = Reg(new StoreMisalignBufferEntry)
 
   val cross4KBPageBoundary = Wire(Bool())
   val needFlushPipe = RegInit(false.B)
 
+  val selectOldestModule = Module(new SelectOldest(new StoreMisalignBufferEntry, enqPortNum, selectOlder))
+
   // buffer control:
   //  - s_idle:  Idle
   //  - s_split: Split miss-aligned store into aligned stores
@@ -148,11 +125,19 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   val s1_valid = VecInit(io.enq.map(x => x.req.valid))
 
   val s1_index = (0 until io.enq.length).map(_.asUInt)
-  val reqSel = selectOldest(s1_valid, s1_req, s1_index)
+  val selectEntries = Wire(Vec(enqPortNum, new StoreMisalignBufferEntry))
+  selectEntries.zipWithIndex.map{case (sink, i) =>
+    connectSamePort(sink, s1_req(i))
+    sink.portIndex := s1_index(i)
+  }
+  selectOldestModule.io.in.zip(selectEntries).zip(s1_valid).map {case ((sink, source), v) =>
+    sink.bits := source
+    sink.valid := v
+  }
+  val reqSel = selectOldestModule.io.out
 
-  val reqSelValid = reqSel._1(0)
-  val reqSelBits  = reqSel._2(0)
-  val reqSelPort  = reqSel._3(0)
+  val reqSelValid = reqSel.valid
+  val reqSelBits  = reqSel.bits
 
   val reqRedirect = reqSelBits.uop.robIdx.needFlush(io.redirect)
 
@@ -161,14 +146,13 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   val robMatch = req_valid && (io.rob.pendingPtr === req.uop.robIdx)
 
   val s2_canEnq = GatedRegNext(canEnq)
-  val s2_reqSelPort = GatedRegNext(reqSelPort)
+  val s2_reqSelPort = GatedRegNext(reqSelBits.portIndex)
   val s2_needRevoke = s2_canEnq && (0 until enqPortNum).map {
     case i => io.enq(i).revoke && s2_reqSelPort === i.U
   }.reduce(_|_)
 
   when(canEnq) {
     connectSamePort(req, reqSelBits)
-    req.portIndex := reqSelPort
     req_valid := true.B
   }
   val cross4KBPageEnq = WireInit(false.B)
@@ -179,7 +163,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
       bufferState === s_idle
     ) {
       connectSamePort(req, reqSelBits)
-      req.portIndex := reqSelPort
       cross4KBPageEnq := true.B
       needFlushPipe   := true.B
       canEnq := true.B
@@ -189,7 +172,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     }
   }
 
-  val reqSelCanEnq = UIntToOH(reqSelPort)
+  val reqSelCanEnq = UIntToOH(reqSelBits.portIndex)
 
   io.enq.zipWithIndex.map{
     case (reqPort, index) => reqPort.req.ready := reqSelCanEnq(index) && (!req_valid || cross4KBPageBoundary && cross4KBPageEnq)
@@ -391,9 +374,9 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
         }
 
         is (SH) {
-          lowAddrStore.uop.fuOpType := SB
+          lowAddrStore.uop.fuOpType := SH
           lowAddrStore.vaddr := req.vaddr
-          lowAddrStore.mask  := 0x1.U << lowAddrStore.vaddr(3, 0)
+          lowAddrStore.mask  := 0x3.U
           lowResultWidth    := BYTE1
 
           highAddrStore.uop.fuOpType := SB
@@ -410,8 +393,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b01".U) {
               lowAddrStore.uop.fuOpType := SW
-              lowAddrStore.vaddr := req.vaddr - 1.U
-              lowAddrStore.mask  := 0xf.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xf.U
               lowResultWidth    := BYTE3
 
               highAddrStore.uop.fuOpType := SB
@@ -421,9 +404,9 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
             }
 
             is ("b10".U) {
-              lowAddrStore.uop.fuOpType := SH
+              lowAddrStore.uop.fuOpType := SW
               lowAddrStore.vaddr := req.vaddr
-              lowAddrStore.mask  := 0x3.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.mask  := 0xf.U
               lowResultWidth    := BYTE2
 
               highAddrStore.uop.fuOpType := SH
@@ -433,9 +416,9 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
             }
 
             is ("b11".U) {
-              lowAddrStore.uop.fuOpType := SB
+              lowAddrStore.uop.fuOpType := SW
               lowAddrStore.vaddr := req.vaddr
-              lowAddrStore.mask  := 0x1.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.mask  := 0xf.U
               lowResultWidth    := BYTE1
 
               highAddrStore.uop.fuOpType := SW
@@ -454,8 +437,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b001".U) {
               lowAddrStore.uop.fuOpType := SD
-              lowAddrStore.vaddr := req.vaddr - 1.U
-              lowAddrStore.mask  := 0xff.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE7
 
               highAddrStore.uop.fuOpType := SB
@@ -466,8 +449,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b010".U) {
               lowAddrStore.uop.fuOpType := SD
-              lowAddrStore.vaddr := req.vaddr - 2.U
-              lowAddrStore.mask  := 0xff.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE6
 
               highAddrStore.uop.fuOpType := SH
@@ -478,8 +461,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b011".U) {
               lowAddrStore.uop.fuOpType := SD
-              lowAddrStore.vaddr := req.vaddr - 3.U
-              lowAddrStore.mask  := 0xff.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE5
 
               highAddrStore.uop.fuOpType := SW
@@ -489,9 +472,9 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
             }
 
             is ("b100".U) {
-              lowAddrStore.uop.fuOpType := SW
+              lowAddrStore.uop.fuOpType := SD
               lowAddrStore.vaddr := req.vaddr
-              lowAddrStore.mask  := 0xf.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE4
 
               highAddrStore.uop.fuOpType := SW
@@ -502,8 +485,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b101".U) {
               lowAddrStore.uop.fuOpType := SD
-              lowAddrStore.vaddr := req.vaddr - 5.U
-              lowAddrStore.mask  := 0xff.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE3
 
               highAddrStore.uop.fuOpType := SD
@@ -514,8 +497,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b110".U) {
               lowAddrStore.uop.fuOpType := SD
-              lowAddrStore.vaddr := req.vaddr - 6.U
-              lowAddrStore.mask  := 0xff.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE2
 
               highAddrStore.uop.fuOpType := SD
@@ -526,8 +509,8 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
 
             is ("b111".U) {
               lowAddrStore.uop.fuOpType := SD
-              lowAddrStore.vaddr := req.vaddr - 7.U
-              lowAddrStore.mask  := 0xff.U << lowAddrStore.vaddr(3, 0)
+              lowAddrStore.vaddr := req.vaddr
+              lowAddrStore.mask  := 0xff.U
               lowResultWidth    := BYTE1
 
               highAddrStore.uop.fuOpType := SD
@@ -597,26 +580,27 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     }
   }
 
-  io.writeBack.valid := req_valid && (bufferState === s_wb) && !req.isvec
-  io.writeBack.bits := 0.U.asTypeOf(io.writeBack.bits)
-  io.writeBack.bits.pdest := req.uop.pdest
-  io.writeBack.bits.robIdx := req.uop.robIdx
-  io.writeBack.bits.intWen.foreach(_ := req.uop.rfWen)
-  io.writeBack.bits.exceptionVec.foreach(x => {
+  val writeBack = Wire(new NewExuOutput(staParams.head))
+  writeBack.toRob.valid := req_valid && (bufferState === s_wb) && !req.isvec
+  writeBack.pdest := req.uop.pdest
+  writeBack.toRob.bits.robIdx := req.uop.robIdx
+  writeBack.toRob.bits.exceptionVec.foreach(x => {
     x := 0.U.asTypeOf(x)
     StaCfg.exceptionOut.map(no => x(no) := (globalUncache || globalException) && exceptionVec(no))
   })
-  io.writeBack.bits.flushPipe.foreach(_ := false.B)
-  io.writeBack.bits.lqIdx.foreach(_ := req.uop.lqIdx)
-  io.writeBack.bits.sqIdx.foreach(_ := req.uop.sqIdx)
-  io.writeBack.bits.trigger.foreach(_ := req.uop.trigger)
-  io.writeBack.bits.debug.isMMIO := globalMMIO
-  io.writeBack.bits.debug.isNCIO := globalNC && !globalMemBackTypeMM
-  io.writeBack.bits.debug.isPerfCnt := false.B
-  io.writeBack.bits.debug.paddr := req.paddr
-  io.writeBack.bits.debug.vaddr := req.vaddr
-  io.writeBack.bits.perfDebugInfo.foreach(_  := req.uop.perfDebugInfo)
-  io.writeBack.bits.debug_seqNum.foreach(_  := req.uop.debug_seqNum)
+  writeBack.toRob.bits.lqIdx.foreach(_ := req.uop.lqIdx)
+  writeBack.toRob.bits.sqIdx.foreach(_ := req.uop.sqIdx)
+  writeBack.toRob.bits.trigger.foreach(_ := req.uop.trigger)
+  writeBack.toRob.bits.isRVC.foreach(_ := req.uop.isRVC)
+  writeBack.debug.isMMIO := globalMMIO
+  writeBack.debug.isNCIO := globalNC && !globalMemBackTypeMM
+  writeBack.debug.isPerfCnt := false.B
+  writeBack.debug.paddr := req.paddr
+  writeBack.debug.vaddr := req.vaddr
+  writeBack.perfDebugInfo.foreach(_  := req.uop.perfDebugInfo)
+  writeBack.debug_seqNum.foreach(_  := req.uop.debug_seqNum)
+
+  connectMemDecoupledNewExuOutput(io.writeBack, writeBack)
 
   io.vecWriteBack.zipWithIndex.map{
     case (wb, index) => {
@@ -631,8 +615,6 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
       wb.bits.mmio              := globalMMIO
       wb.bits.exceptionVec      := ExceptionNO.selectByFu(exceptionVec, VstuCfg)
       wb.bits.hasException      := globalException
-      wb.bits.usSecondInv       := req.usSecondInv
-      wb.bits.vecFeedback       := true.B
       wb.bits.elemIdx           := req.elemIdx
       wb.bits.alignedType       := req.alignedType
       wb.bits.mask              := req.mask
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
deleted file mode 100644
index e2d09bcbab3..00000000000
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ /dev/null
@@ -1,1581 +0,0 @@
-/***************************************************************************************
-* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
-* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
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
-package xiangshan.mem
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import utility._
-import utils._
-import xiangshan._
-import xiangshan.ExceptionNO._
-import xiangshan.backend._
-import xiangshan.backend.rob.{RobLsqIO, RobPtr}
-import xiangshan.backend.Bundles.{DynInst, ExuOutput, UopIdx}
-import xiangshan.backend.decode.isa.bitfield.{Riscv32BitInst, XSInstBitFields}
-import xiangshan.backend.fu.FuConfig._
-import xiangshan.backend.fu.FuType
-import xiangshan.mem.Bundles._
-import xiangshan.cache._
-import xiangshan.cache.{CMOReq, CMOResp, DCacheLineIO, DCacheWordIO, MemoryOpConstants}
-import difftest._
-import difftest.common.DifftestMem
-
-class SqPtr(implicit p: Parameters) extends CircularQueuePtr[SqPtr](
-  p => p(XSCoreParamsKey).StoreQueueSize
-){
-}
-
-object SqPtr {
-  def apply(f: Bool, v: UInt)(implicit p: Parameters): SqPtr = {
-    val ptr = Wire(new SqPtr)
-    ptr.flag := f
-    ptr.value := v
-    ptr
-  }
-}
-
-class SqEnqIO(implicit p: Parameters) extends MemBlockBundle {
-  val canAccept = Output(Bool())
-  val lqCanAccept = Input(Bool())
-  val needAlloc = Vec(LSQEnqWidth, Input(Bool()))
-  val req = Vec(LSQEnqWidth, Flipped(ValidIO(new DynInst)))
-  val resp = Vec(LSQEnqWidth, Output(new SqPtr))
-}
-
-class StoreQueueDataWrite(implicit p: Parameters) extends MemBlockBundle {
-  val fuType = FuType()
-  val fuOpType = FuOpType()
-  val data = UInt(VLEN.W)
-  val sqIdx = new SqPtr
-  val vecDebug = new VecMissalignedDebugBundle
-}
-
-class DataBufferEntry (implicit p: Parameters)  extends DCacheBundle {
-  val addr   = UInt(PAddrBits.W)
-  val vaddr  = UInt(VAddrBits.W)
-  val data   = UInt(VLEN.W)
-  val mask   = UInt((VLEN/8).W)
-  val wline = Bool()
-  val sqPtr  = new SqPtr
-  val prefetch = Bool()
-  val vecValid = Bool()
-  val sqNeedDeq = Bool()
-}
-
-class StoreExceptionBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper {
-  // The 1st StorePipelineWidth ports: sta exception generated at s1, except for af
-  // The 2nd StorePipelineWidth ports: sta af generated at s2
-  // The following VecStorePipelineWidth ports: vector st exception
-  // The last port: non-data error generated in SoC
-  val enqPortNum = StorePipelineWidth * 2 + VecStorePipelineWidth + 1
-
-  val io = IO(new Bundle() {
-    val redirect = Flipped(ValidIO(new Redirect))
-    val storeAddrIn = Vec(enqPortNum, Flipped(ValidIO(new LsPipelineBundle())))
-    val exceptionAddr = new ExceptionAddrIO
-  })
-
-  val req_valid = RegInit(false.B)
-  val req = Reg(new LsPipelineBundle())
-
-  // enqueue
-  // S1:
-  val s1_req = VecInit(io.storeAddrIn.map(_.bits))
-  val s1_valid = VecInit(io.storeAddrIn.map(x =>
-      x.valid && !x.bits.uop.robIdx.needFlush(io.redirect) && ExceptionNO.selectByFu(x.bits.uop.exceptionVec, StaCfg).asUInt.orR
-  ))
-
-  // S2: delay 1 cycle
-  val s2_req = (0 until enqPortNum).map(i =>
-    RegEnable(s1_req(i), s1_valid(i)))
-  val s2_valid = (0 until enqPortNum).map(i =>
-    RegNext(s1_valid(i)) && !s2_req(i).uop.robIdx.needFlush(io.redirect)
-  )
-
-  val s2_enqueue = Wire(Vec(enqPortNum, Bool()))
-  for (w <- 0 until enqPortNum) {
-    s2_enqueue(w) := s2_valid(w)
-  }
-
-  def selectOldest[T <: LsPipelineBundle](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
-    assert(valid.length == bits.length)
-    if (valid.length == 0 || valid.length == 1) {
-      (valid, bits)
-    } else if (valid.length == 2) {
-      val res = Seq.fill(2)(Wire(Valid(chiselTypeOf(bits(0)))))
-      for (i <- res.indices) {
-        res(i).valid := valid(i)
-        res(i).bits := bits(i)
-      }
-      val oldest = Mux(valid(0) && valid(1),
-        Mux(isAfter(bits(0).uop.sqIdx, bits(1).uop.sqIdx), res(1), res(0)),
-        Mux(valid(0) && !valid(1), res(0), res(1)))
-      (Seq(oldest.valid), Seq(oldest.bits))
-    } else {
-      val left = selectOldest(valid.take(valid.length / 2), bits.take(bits.length / 2))
-      val right = selectOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)))
-      selectOldest(left._1 ++ right._1, left._2 ++ right._2)
-    }
-  }
-
-  val reqValid = req_valid && !req.uop.robIdx.needFlush(io.redirect)
-  val reqSel = selectOldest(s2_enqueue :+ reqValid, s2_req :+ req)
-
-  req_valid := reqSel._1(0)
-  req := reqSel._2(0)
-
-  io.exceptionAddr.vaddr     := req.fullva
-  io.exceptionAddr.vaNeedExt := req.vaNeedExt
-  io.exceptionAddr.isHyper   := req.isHyper
-  io.exceptionAddr.gpaddr    := req.gpaddr
-  io.exceptionAddr.vstart    := req.uop.vpu.vstart
-  io.exceptionAddr.vl        := req.uop.vpu.vl
-  io.exceptionAddr.isForVSnonLeafPTE := req.isForVSnonLeafPTE
-
-}
-
-class GenerateInfoFromSBuffer extends Bundle{
-  val diffStoreEventCount = UInt(64.W)
-}
-
-// Store Queue
-class StoreQueue(implicit p: Parameters) extends XSModule
-  with HasDCacheParameters
-  with HasMemBlockParameters
-  with HasCircularQueuePtrHelper
-  with HasPerfEvents
-  with HasVLSUParameters {
-  val io = IO(new Bundle() {
-    val hartId = Input(UInt(hartIdLen.W))
-    val enq = new SqEnqIO
-    val brqRedirect = Flipped(ValidIO(new Redirect))
-    val vecFeedback = Vec(VecLoadPipelineWidth, Flipped(ValidIO(new FeedbackToLsqIO)))
-    val storeAddrIn = Vec(StorePipelineWidth, Flipped(Valid(new LsPipelineBundle))) // store addr, data is not included
-    val storeAddrInRe = Vec(StorePipelineWidth, Input(new LsPipelineBundle())) // store more mmio and exception
-    val storeDataIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreQueueDataWrite))) // store data, send to sq from rs
-    val storeMaskIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreMaskBundle))) // store mask, send to sq from rs
-    val sbuffer = Vec(EnsbufferWidth, Decoupled(new DCacheWordReqWithVaddrAndPfFlag)) // write committed store to sbuffer
-    val uncacheOutstanding = Input(Bool())
-    val cmoOpReq  = DecoupledIO(new CMOReq)
-    val cmoOpResp = Flipped(DecoupledIO(new CMOResp))
-    val cboZeroStout = DecoupledIO(new ExuOutput(staParams.head))
-    val mmioStout = DecoupledIO(new ExuOutput(staParams.head)) // writeback uncached store
-    val vecmmioStout = DecoupledIO(new ExuOutput(vstuParams.head))
-    val forward = Vec(LoadPipelineWidth, Flipped(new PipeLoadForwardQueryIO))
-    // TODO: scommit is only for scalar store
-    val rob = Flipped(new RobLsqIO)
-    val uncache = new UncacheWordIO
-    // val refill = Flipped(Valid(new DCacheLineReq ))
-    val exceptionAddr = new ExceptionAddrIO
-    val flushSbuffer = new SbufferFlushBundle
-    val sqEmpty = Output(Bool())
-    val stAddrReadySqPtr = Output(new SqPtr)
-    val stAddrReadyVec = Output(Vec(StoreQueueSize, Bool()))
-    val stDataReadySqPtr = Output(new SqPtr)
-    val stDataReadyVec = Output(Vec(StoreQueueSize, Bool()))
-    val stIssuePtr = Output(new SqPtr)
-    val sqDeqPtr = Output(new SqPtr)
-    val sqCommitPtr = Output(new SqPtr)
-    val sqCommitUopIdx = Output(UopIdx())
-    val sqCommitRobIdx = Output(new RobPtr)
-    val sqFull = Output(Bool())
-    val sqCancelCnt = Output(UInt(log2Up(StoreQueueSize + 1).W))
-    val sqDeq = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
-    val force_write = Output(Bool())
-    val maControl   = Flipped(new StoreMaBufToSqControlIO)
-    val wfi = Flipped(new WfiReqBundle)
-    val diffStore = Flipped(new DiffStoreIO)
-  })
-
-  println("StoreQueue: size:" + StoreQueueSize)
-
-  // data modules
-  val uop = Reg(Vec(StoreQueueSize, new DynInst))
-  // val data = Reg(Vec(StoreQueueSize, new LsqEntry))
-  val dataModule = Module(new SQDataModule(
-    numEntries = StoreQueueSize,
-    numRead = EnsbufferWidth,
-    numWrite = StorePipelineWidth,
-    numForward = LoadPipelineWidth
-  ))
-  dataModule.io := DontCare
-  val paddrModule = Module(new SQAddrModule(
-    dataWidth = PAddrBits,
-    numEntries = StoreQueueSize,
-    numRead = EnsbufferWidth,
-    numWrite = StorePipelineWidth,
-    numForward = LoadPipelineWidth
-  ))
-  paddrModule.io := DontCare
-  val vaddrModule = Module(new SQAddrModule(
-    dataWidth = VAddrBits,
-    numEntries = StoreQueueSize,
-    numRead = EnsbufferWidth, // sbuffer; badvaddr will be sent from exceptionBuffer
-    numWrite = StorePipelineWidth,
-    numForward = LoadPipelineWidth
-  ))
-  vaddrModule.io := DontCare
-  val dataBuffer = Module(new DatamoduleResultBuffer(new DataBufferEntry))
-  val exceptionBuffer = Module(new StoreExceptionBuffer)
-  exceptionBuffer.io.redirect := io.brqRedirect
-  exceptionBuffer.io.exceptionAddr.isStore := DontCare
-  // vlsu exception!
-  for (i <- 0 until VecStorePipelineWidth) {
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).valid               := io.vecFeedback(i).valid && io.vecFeedback(i).bits.feedback(VecFeedbacks.FLUSH) // have exception
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits                := DontCare
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.fullva         := io.vecFeedback(i).bits.vaddr
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.vaNeedExt      := io.vecFeedback(i).bits.vaNeedExt
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.gpaddr         := io.vecFeedback(i).bits.gpaddr
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.uopIdx     := io.vecFeedback(i).bits.uopidx
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.robIdx     := io.vecFeedback(i).bits.robidx
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.vpu.vstart := io.vecFeedback(i).bits.vstart
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.vpu.vl     := io.vecFeedback(i).bits.vl
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.isForVSnonLeafPTE := io.vecFeedback(i).bits.isForVSnonLeafPTE
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.exceptionVec  := io.vecFeedback(i).bits.exceptionVec
-  }
-
-
-  val debug_paddr = Reg(Vec(StoreQueueSize, UInt((PAddrBits).W)))
-  val debug_vaddr = Reg(Vec(StoreQueueSize, UInt((VAddrBits).W)))
-  val debug_data = Reg(Vec(StoreQueueSize, UInt((XLEN).W)))
-  val debug_vec_unaligned_start = Reg(Vec(StoreQueueSize, UInt((log2Up(XLEN)).W))) // only use for unit-stride difftest
-  val debug_vec_unaligned_offset = Reg(Vec(StoreQueueSize, UInt((log2Up(XLEN)).W))) // only use for unit-stride difftest
-
-  // state & misc
-  val allocated = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // sq entry has been allocated
-  val completed = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
-  val addrvalid = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
-  val datavalid = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
-  val allvalid  = VecInit((0 until StoreQueueSize).map(i => addrvalid(i) && datavalid(i)))
-  val committed = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // inst has been committed by rob
-  val unaligned = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // unaligned store
-  val cross16Byte = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // unaligned cross 16Byte boundary
-  val pending = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // mmio pending: inst is an mmio inst, it will not be executed until it reachs the end of rob
-  val nc = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // nc: inst is a nc inst
-  val mmio = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // mmio: inst is an mmio inst
-  val memBackTypeMM = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
-  val prefetch = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // need prefetch when committing this store to sbuffer?
-  val isVec = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // vector store instruction
-  val vecLastFlow = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // last uop the last flow of vector store instruction
-  val vecMbCommit = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // vector store committed from merge buffer to rob
-  val hasException = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // store has exception, should deq but not write sbuffer
-  val waitStoreS2 = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // wait for mmio and exception result until store_s2
-  // val vec_robCommit = Reg(Vec(StoreQueueSize, Bool())) // vector store committed by rob
-  // val vec_secondInv = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // Vector unit-stride, second entry is invalid
-  val vecExceptionFlag = RegInit(0.U.asTypeOf(Valid(new DynInst)))
-  val noPending = RegInit(true.B)
-
-  // ptr
-  val enqPtrExt = RegInit(VecInit((0 until io.enq.req.length).map(_.U.asTypeOf(new SqPtr))))
-  val rdataPtrExt = RegInit(VecInit((0 until EnsbufferWidth).map(_.U.asTypeOf(new SqPtr))))
-  val deqPtrExt = RegInit(VecInit((0 until EnsbufferWidth).map(_.U.asTypeOf(new SqPtr))))
-  val cmtPtrExt = RegInit(VecInit((0 until CommitWidth).map(_.U.asTypeOf(new SqPtr))))
-  val addrReadyPtrExt = RegInit(0.U.asTypeOf(new SqPtr))
-  val dataReadyPtrExt = RegInit(0.U.asTypeOf(new SqPtr))
-
-  val enqPtr = enqPtrExt(0).value
-  val deqPtr = deqPtrExt(0).value
-  val cmtPtr = cmtPtrExt(0).value
-  val rdPtr = rdataPtrExt(0).value
-
-  val validCount = distanceBetween(enqPtrExt(0), deqPtrExt(0))
-  val allowEnqueue = validCount <= (StoreQueueSize - LSQStEnqWidth).U
-
-  val deqMask = UIntToMask(deqPtr, StoreQueueSize)
-  val enqMask = UIntToMask(enqPtr, StoreQueueSize)
-
-  val commitCount = WireInit(0.U(log2Ceil(CommitWidth + 1).W))
-  val scommit = GatedRegNext(io.rob.scommit)
-  val mmioReq = Wire(chiselTypeOf(io.uncache.req))
-  val ncWaitRespPtrReg = RegInit(0.U(uncacheIdxBits.W)) // it's valid only in non-outstanding situation
-  val ncReq = Wire(chiselTypeOf(io.uncache.req))
-  val ncResp = Wire(chiselTypeOf(io.uncache.resp))
-  val ncDoReq = Wire(Bool())
-  val ncSlaveAck = Wire(Bool())
-  val ncSlaveAckMid = Wire(UInt(uncacheIdxBits.W))
-  val ncDoResp = Wire(Bool())
-  val ncReadNextTrigger = Mux(io.uncacheOutstanding, ncSlaveAck, ncDoResp)
-  val ncDeqTrigger = Mux(io.uncacheOutstanding, ncSlaveAck, ncDoResp)
-  val ncPtr = Mux(io.uncacheOutstanding, ncSlaveAckMid, ncWaitRespPtrReg)
-
-  // store can be committed by ROB
-  io.rob.mmio := DontCare
-  io.rob.uop := DontCare
-
-  // Read dataModule
-  assert(EnsbufferWidth <= 2)
-  // rdataPtrExtNext and rdataPtrExtNext+1 entry will be read from dataModule
-  val rdataPtrExtNext = Wire(Vec(EnsbufferWidth, new SqPtr))
-  val sqReadCnt = WireInit(0.U(log2Ceil(EnsbufferWidth + 1).W))
-  val readyReadGoVec = WireInit(VecInit((0 until EnsbufferWidth).map(i =>
-    if(i == 0) {
-      dataBuffer.io.enq(i).fire && dataBuffer.io.enq(i).bits.sqNeedDeq ||
-      allocated(rdataPtrExt(i).value) && completed(rdataPtrExt(i).value) && nc(rdataPtrExt(i).value) ||
-      io.mmioStout.fire || io.vecmmioStout.fire
-    } else {
-      dataBuffer.io.enq(i).fire && dataBuffer.io.enq(i).bits.sqNeedDeq ||
-      allocated(rdataPtrExt(i).value) && completed(rdataPtrExt(i).value) && nc(rdataPtrExt(i).value)
-    }
-  )))
-  for (i <- 0 until EnsbufferWidth) {
-    when(readyReadGoVec.take(i + 1).reduce(_ && _)) {
-      sqReadCnt := (i + 1).U // increase one by one
-    }
-  }
-  rdataPtrExtNext := rdataPtrExt.map(_ + sqReadCnt)
-
-  // deqPtrExtNext traces which inst is about to leave store queue
-  val deqPtrExtNext = Wire(Vec(EnsbufferWidth, new SqPtr))
-  val sqDeqCnt = WireInit(0.U(log2Ceil(EnsbufferWidth + 1).W))
-  val readyDeqVec = WireInit(VecInit((0 until EnsbufferWidth).map(i =>
-    allocated(deqPtrExt(i).value) && completed(deqPtrExt(i).value)
-  )))
-  for (i <- 0 until EnsbufferWidth) {
-    val ptr = deqPtrExt(i).value
-    when(readyDeqVec.take(i + 1).reduce(_ && _)) {
-      sqDeqCnt := (i + 1).U
-      allocated(ptr) := false.B
-      completed(ptr) := false.B
-    }
-  }
-  deqPtrExtNext := deqPtrExt.map(_ + sqDeqCnt)
-  io.sqDeq := RegNext(sqDeqCnt)
-
-  assert(!RegNext(RegNext(io.sbuffer(0).fire) && (io.mmioStout.fire || io.vecmmioStout.fire)))
-
-  for (i <- 0 until EnsbufferWidth) {
-    dataModule.io.raddr(i) := rdataPtrExtNext(i).value
-    paddrModule.io.raddr(i) := rdataPtrExtNext(i).value
-    vaddrModule.io.raddr(i) := rdataPtrExtNext(i).value
-  }
-
-  /**
-    * Enqueue at dispatch
-    *
-    * Currently, StoreQueue only allows enqueue when #emptyEntries > EnqWidth
-    * Dynamic enq based on numLsElem number
-    */
-  io.enq.canAccept := allowEnqueue
-  val canEnqueue = io.enq.req.map(_.valid)
-  val enqCancel = io.enq.req.map(_.bits.robIdx.needFlush(io.brqRedirect))
-  val vStoreFlow = io.enq.req.map(_.bits.numLsElem.asTypeOf(UInt(elemIdxBits.W)))
-  val validVStoreFlow = vStoreFlow.zipWithIndex.map{case (vStoreFlowNumItem, index) => Mux(!RegNext(io.brqRedirect.valid) && canEnqueue(index), vStoreFlowNumItem, 0.U)}
-  val validVStoreOffset = vStoreFlow.zip(io.enq.needAlloc).map{case (flow, needAllocItem) => Mux(needAllocItem, flow, 0.U)}
-  val validVStoreOffsetRShift = 0.U +: validVStoreOffset.take(vStoreFlow.length - 1)
-
-  val enqLowBound = io.enq.req.map(_.bits.sqIdx)
-  val enqUpBound  = io.enq.req.map(x => x.bits.sqIdx + x.bits.numLsElem)
-  val enqCrossLoop = enqLowBound.zip(enqUpBound).map{case (low, up) => low.flag =/= up.flag}
-
-  for(i <- 0 until StoreQueueSize) {
-    val entryCanEnqSeq = (0 until io.enq.req.length).map { j =>
-      val entryHitBound = Mux(
-        enqCrossLoop(j),
-        enqLowBound(j).value <= i.U || i.U < enqUpBound(j).value,
-        enqLowBound(j).value <= i.U && i.U < enqUpBound(j).value
-      )
-      canEnqueue(j) && !enqCancel(j) && entryHitBound
-    }
-
-    val entryCanEnq = entryCanEnqSeq.reduce(_ || _)
-    val selectBits = ParallelPriorityMux(entryCanEnqSeq, io.enq.req.map(_.bits))
-    val selectUpBound = ParallelPriorityMux(entryCanEnqSeq, enqUpBound)
-    when (entryCanEnq) {
-      uop(i) := selectBits
-      if (i + 1 == StoreQueueSize)
-        vecLastFlow(i) := Mux(0.U === selectUpBound.value, selectBits.lastUop, false.B) else
-        vecLastFlow(i) := Mux((i + 1).U === selectUpBound.value, selectBits.lastUop, false.B)
-      allocated(i) := true.B
-      completed(i) := false.B
-      datavalid(i) := false.B
-      addrvalid(i) := false.B
-      unaligned(i) := false.B
-      cross16Byte(i) := false.B
-      committed(i) := false.B
-      pending(i) := false.B
-      prefetch(i) := false.B
-      nc(i) := false.B
-      mmio(i) := false.B
-      isVec(i) :=  FuType.isVStore(selectBits.fuType)
-      vecMbCommit(i) := false.B
-      hasException(i) := false.B
-      waitStoreS2(i) := true.B
-    }
-  }
-
-  for (i <- 0 until io.enq.req.length) {
-    val sqIdx = enqPtrExt(0) + validVStoreOffsetRShift.take(i + 1).reduce(_ + _)
-    val index = io.enq.req(i).bits.sqIdx
-    XSError(canEnqueue(i) && !enqCancel(i) && (!io.enq.canAccept || !io.enq.lqCanAccept), s"must accept $i\n")
-    XSError(canEnqueue(i) && !enqCancel(i) && index.value =/= sqIdx.value, s"must be the same entry $i\n")
-    io.enq.resp(i) := sqIdx
-  }
-  XSDebug(p"(ready, valid): ${io.enq.canAccept}, ${Binary(Cat(io.enq.req.map(_.valid)))}\n")
-
-  /**
-    * Update addr/dataReadyPtr when issue from rs
-    */
-  // update issuePtr
-  val IssuePtrMoveStride = 4
-  require(IssuePtrMoveStride >= 2)
-
-  val addrReadyLookupVec = (0 until IssuePtrMoveStride).map(addrReadyPtrExt + _.U)
-  val addrReadyLookup = addrReadyLookupVec.map(ptr => allocated(ptr.value) &&
-   (mmio(ptr.value) || addrvalid(ptr.value) || vecMbCommit(ptr.value))
-    && ptr =/= enqPtrExt(0))
-  val nextAddrReadyPtr = addrReadyPtrExt + PriorityEncoder(VecInit(addrReadyLookup.map(!_) :+ true.B))
-  addrReadyPtrExt := nextAddrReadyPtr
-
-  val stAddrReadyVecReg = Wire(Vec(StoreQueueSize, Bool()))
-  (0 until StoreQueueSize).map(i => {
-    stAddrReadyVecReg(i) := allocated(i) && (mmio(i) || addrvalid(i) || (isVec(i) && vecMbCommit(i)))
-  })
-  io.stAddrReadyVec := GatedValidRegNext(stAddrReadyVecReg)
-
-  when (io.brqRedirect.valid) {
-    addrReadyPtrExt := Mux(
-      isAfter(cmtPtrExt(0), deqPtrExt(0)),
-      cmtPtrExt(0),
-      deqPtrExtNext(0) // for mmio insts, deqPtr may be ahead of cmtPtr
-    )
-  }
-
-  io.stAddrReadySqPtr := addrReadyPtrExt
-
-  // update
-  val dataReadyLookupVec = (0 until IssuePtrMoveStride).map(dataReadyPtrExt + _.U)
-  val dataReadyLookup = dataReadyLookupVec.map(ptr =>
-    allocated(ptr.value) &&
-    (addrvalid(ptr.value) && (mmio(ptr.value) || datavalid(ptr.value)) || vecMbCommit(ptr.value)) &&
-    !unaligned(ptr.value) &&
-    ptr =/= enqPtrExt(0)
-  )
-  val nextDataReadyPtr = dataReadyPtrExt + PriorityEncoder(VecInit(dataReadyLookup.map(!_) :+ true.B))
-  dataReadyPtrExt := nextDataReadyPtr
-
-  // move unalign ptr
-  val deqGroupHasUnalign = deqPtrExt.map { case ptr => unaligned(ptr.value) }.reduce(_|_)
-  val dataPtrInDeqGroupRangeVec = VecInit(deqPtrExt.zipWithIndex.map { case (ptr, i) =>
-    dataReadyPtrExt === ptr && sqDeqCnt > i.U
-  })
-  val unalignedCanMove = deqGroupHasUnalign && dataPtrInDeqGroupRangeVec.asUInt.orR
-  when (unalignedCanMove) {
-    val step = sqDeqCnt - PriorityEncoder(dataPtrInDeqGroupRangeVec)
-    dataReadyPtrExt := dataReadyPtrExt + step
-  }
-
-  val stDataReadyVecReg = Wire(Vec(StoreQueueSize, Bool()))
-  (0 until StoreQueueSize).map(i => {
-    stDataReadyVecReg(i) := allocated(i) &&
-      (addrvalid(i) && (mmio(i) || datavalid(i)) || (isVec(i) && vecMbCommit(i))) && !unaligned(i)
-  })
-  io.stDataReadyVec := GatedValidRegNext(stDataReadyVecReg)
-
-  when (io.brqRedirect.valid) {
-    dataReadyPtrExt := Mux(
-      isAfter(cmtPtrExt(0), deqPtrExt(0)),
-      cmtPtrExt(0),
-      deqPtrExtNext(0) // for mmio insts, deqPtr may be ahead of cmtPtr
-    )
-  }
-
-  io.stDataReadySqPtr := dataReadyPtrExt
-  io.stIssuePtr := enqPtrExt(0)
-  io.sqDeqPtr := deqPtrExt(0)
-
-  /**
-    * Writeback store from store units
-    *
-    * Most store instructions writeback to regfile in the previous cycle.
-    * However,
-    *   (1) For an mmio instruction with exceptions, we need to mark it as addrvalid
-    * (in this way it will trigger an exception when it reaches ROB's head)
-    * instead of pending to avoid sending them to lower level.
-    *   (2) For an mmio instruction without exceptions, we mark it as pending.
-    * When the instruction reaches ROB's head, StoreQueue sends it to uncache channel.
-    * Upon receiving the response, StoreQueue writes back the instruction
-    * through arbiter with store units. It will later commit as normal.
-    */
-
-  // Write addr to sq
-  for (i <- 0 until StorePipelineWidth) {
-    paddrModule.io.wen(i) := false.B
-    vaddrModule.io.wen(i) := false.B
-    dataModule.io.mask.wen(i) := false.B
-    val stWbIndex = io.storeAddrIn(i).bits.uop.sqIdx.value
-    exceptionBuffer.io.storeAddrIn(i).valid := io.storeAddrIn(i).fire && !io.storeAddrIn(i).bits.miss && !io.storeAddrIn(i).bits.isvec
-    exceptionBuffer.io.storeAddrIn(i).bits := io.storeAddrIn(i).bits
-    // will re-enter exceptionbuffer at store_s2
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth + i).valid := false.B
-    exceptionBuffer.io.storeAddrIn(StorePipelineWidth + i).bits := 0.U.asTypeOf(new LsPipelineBundle)
-
-    when (io.storeAddrIn(i).fire && io.storeAddrIn(i).bits.updateAddrValid) {
-      val addr_valid = !io.storeAddrIn(i).bits.miss
-      addrvalid(stWbIndex) := addr_valid //!io.storeAddrIn(i).bits.mmio
-      nc(stWbIndex) := io.storeAddrIn(i).bits.nc
-
-    }
-    when (io.storeAddrIn(i).fire && !io.storeAddrIn(i).bits.isFrmMisAlignBuf) {
-      // pending(stWbIndex) := io.storeAddrIn(i).bits.mmio
-      unaligned(stWbIndex) := io.storeAddrIn(i).bits.isMisalign
-      cross16Byte(stWbIndex) := io.storeAddrIn(i).bits.isMisalign && !io.storeAddrIn(i).bits.misalignWith16Byte
-
-      paddrModule.io.waddr(i) := stWbIndex
-      paddrModule.io.wdata(i) := io.storeAddrIn(i).bits.paddr
-      paddrModule.io.wmask(i) := io.storeAddrIn(i).bits.mask
-      paddrModule.io.wlineflag(i) := io.storeAddrIn(i).bits.wlineflag
-      paddrModule.io.wen(i) := true.B
-
-      vaddrModule.io.waddr(i) := stWbIndex
-      vaddrModule.io.wdata(i) := io.storeAddrIn(i).bits.vaddr
-      vaddrModule.io.wmask(i) := io.storeAddrIn(i).bits.mask
-      vaddrModule.io.wlineflag(i) := io.storeAddrIn(i).bits.wlineflag
-      vaddrModule.io.wen(i) := true.B
-
-      debug_paddr(paddrModule.io.waddr(i)) := paddrModule.io.wdata(i)
-
-      // mmio(stWbIndex) := io.storeAddrIn(i).bits.mmio
-    }
-    when (io.storeAddrIn(i).fire) {
-      uop(stWbIndex) := io.storeAddrIn(i).bits.uop
-      uop(stWbIndex).perfDebugInfo := io.storeAddrIn(i).bits.uop.perfDebugInfo
-      uop(stWbIndex).debug_seqNum := io.storeAddrIn(i).bits.uop.debug_seqNum
-    }
-    XSInfo(io.storeAddrIn(i).fire && !io.storeAddrIn(i).bits.isFrmMisAlignBuf,
-      "store addr write to sq idx %d pc 0x%x miss:%d vaddr %x paddr %x mmio %x isvec %x\n",
-      io.storeAddrIn(i).bits.uop.sqIdx.value,
-      io.storeAddrIn(i).bits.uop.pc,
-      io.storeAddrIn(i).bits.miss,
-      io.storeAddrIn(i).bits.vaddr,
-      io.storeAddrIn(i).bits.paddr,
-      io.storeAddrIn(i).bits.mmio,
-      io.storeAddrIn(i).bits.isvec
-    )
-
-    // re-replinish mmio, for pma/pmp will get mmio one cycle later
-    val storeAddrInFireReg = RegNext(io.storeAddrIn(i).fire && !io.storeAddrIn(i).bits.miss) && io.storeAddrInRe(i).updateAddrValid
-    //val stWbIndexReg = RegNext(stWbIndex)
-    val stWbIndexReg = RegEnable(stWbIndex, io.storeAddrIn(i).fire)
-    when (storeAddrInFireReg) {
-      pending(stWbIndexReg) := io.storeAddrInRe(i).mmio
-      mmio(stWbIndexReg) := io.storeAddrInRe(i).mmio
-      memBackTypeMM(stWbIndexReg) := io.storeAddrInRe(i).memBackTypeMM
-      hasException(stWbIndexReg) := io.storeAddrInRe(i).hasException
-      addrvalid(stWbIndexReg) := addrvalid(stWbIndexReg) || io.storeAddrInRe(i).hasException
-      waitStoreS2(stWbIndexReg) := false.B
-    }
-    // dcache miss info (one cycle later than storeIn)
-    // if dcache report a miss in sta pipeline, this store will trigger a prefetch when committing to sbuffer (if EnableAtCommitMissTrigger)
-    when (storeAddrInFireReg) {
-      prefetch(stWbIndexReg) := io.storeAddrInRe(i).miss
-    }
-    // enter exceptionbuffer again
-    when (storeAddrInFireReg) {
-      exceptionBuffer.io.storeAddrIn(StorePipelineWidth + i).valid := io.storeAddrInRe(i).hasException && !io.storeAddrInRe(i).isvec
-      exceptionBuffer.io.storeAddrIn(StorePipelineWidth + i).bits := io.storeAddrInRe(i)
-      exceptionBuffer.io.storeAddrIn(StorePipelineWidth + i).bits.uop.exceptionVec(storeAccessFault) := io.storeAddrInRe(i).af
-    }
-
-    when(vaddrModule.io.wen(i)){
-      debug_vaddr(vaddrModule.io.waddr(i)) := vaddrModule.io.wdata(i)
-    }
-  }
-
-  // Write data to sq
-  // Now store data pipeline is actually 2 stages
-  for (i <- 0 until StorePipelineWidth) {
-    dataModule.io.data.wen(i) := false.B
-    val stWbIndex = io.storeDataIn(i).bits.sqIdx.value
-    val isVec     = FuType.isVStore(io.storeDataIn(i).bits.fuType)
-    // sq data write takes 2 cycles:
-    // sq data write s0
-    when (io.storeDataIn(i).fire) {
-      // send data write req to data module
-      dataModule.io.data.waddr(i) := stWbIndex
-      dataModule.io.data.wdata(i) := Mux(io.storeDataIn(i).bits.fuOpType === LSUOpType.cbo_zero,
-        0.U,
-        Mux(isVec,
-          io.storeDataIn(i).bits.data,
-          genVWdata(io.storeDataIn(i).bits.data, LSUOpType.size(io.storeDataIn(i).bits.fuOpType)))
-      )
-      dataModule.io.data.wen(i) := true.B
-
-      debug_data(dataModule.io.data.waddr(i)) := dataModule.io.data.wdata(i)
-      debug_vec_unaligned_start(dataModule.io.data.waddr(i)) := io.storeDataIn(i).bits.vecDebug.start
-      debug_vec_unaligned_offset(dataModule.io.data.waddr(i)) := io.storeDataIn(i).bits.vecDebug.offset
-    }
-
-    // sq data write s1
-    val lastStWbIndex = RegEnable(stWbIndex, io.storeDataIn(i).fire)
-    when (
-      RegNext(io.storeDataIn(i).fire) && allocated(lastStWbIndex)
-    ) {
-      datavalid(lastStWbIndex) := true.B
-    }
-  }
-
-  // Write mask to sq
-  for (i <- 0 until StorePipelineWidth) {
-    // sq mask write s0
-    when (io.storeMaskIn(i).fire) {
-      // send data write req to data module
-      dataModule.io.mask.waddr(i) := io.storeMaskIn(i).bits.sqIdx.value
-      dataModule.io.mask.wdata(i) := io.storeMaskIn(i).bits.mask
-      dataModule.io.mask.wen(i) := true.B
-    }
-  }
-
-  /**
-    * load forward query
-    *
-    * Check store queue for instructions that is older than the load.
-    * The response will be valid at the next cycle after req.
-    */
-  // check over all lq entries and forward data from the first matched store
-  for (i <- 0 until LoadPipelineWidth) {
-    // Compare deqPtr (deqPtr) and forward.sqIdx, we have two cases:
-    // (1) if they have the same flag, we need to check range(tail, sqIdx)
-    // (2) if they have different flags, we need to check range(tail, VirtualLoadQueueSize) and range(0, sqIdx)
-    // Forward1: Mux(same_flag, range(tail, sqIdx), range(tail, VirtualLoadQueueSize))
-    // Forward2: Mux(same_flag, 0.U,                   range(0, sqIdx)    )
-    // i.e. forward1 is the target entries with the same flag bits and forward2 otherwise
-    val differentFlag = deqPtrExt(0).flag =/= io.forward(i).sqIdx.flag
-    val forwardMask = io.forward(i).sqIdxMask
-    // all addrvalid terms need to be checked
-    // Real Vaild: all scalar stores, and vector store with (!inactive && !secondInvalid)
-    val addrRealValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j => addrvalid(j) && allocated(j))))
-    // vector store will consider all inactive || secondInvalid flows as valid
-    val addrValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j => addrvalid(j) && allocated(j))))
-    val dataValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j => datavalid(j))))
-    val allValidVec  = WireInit(VecInit((0 until StoreQueueSize).map(j => addrvalid(j) && datavalid(j) && allocated(j))))
-
-    val lfstEnable = Constantin.createRecord("LFSTEnable", LFSTEnable)
-    val storeSetHitVec = Mux(lfstEnable,
-      WireInit(VecInit((0 until StoreQueueSize).map(j => io.forward(i).uop.loadWaitBit && uop(j).robIdx === io.forward(i).uop.waitForRobIdx))),
-      WireInit(VecInit((0 until StoreQueueSize).map(j => uop(j).storeSetHit && uop(j).ssid === io.forward(i).uop.ssid)))
-    )
-
-    val forwardMask1 = Mux(differentFlag, ~deqMask, deqMask ^ forwardMask)
-    val forwardMask2 = Mux(differentFlag, forwardMask, 0.U(StoreQueueSize.W))
-    val canForward1 = forwardMask1 & allValidVec.asUInt
-    val canForward2 = forwardMask2 & allValidVec.asUInt
-    val needForward = Mux(differentFlag, ~deqMask | forwardMask, deqMask ^ forwardMask)
-
-    XSDebug(p"$i f1 ${Binary(canForward1)} f2 ${Binary(canForward2)} " +
-      p"sqIdx ${io.forward(i).sqIdx} pa ${Hexadecimal(io.forward(i).paddr)}\n"
-    )
-
-    // do real fwd query (cam lookup in load_s1)
-    dataModule.io.needForward(i)(0) := canForward1 & vaddrModule.io.forwardMmask(i).asUInt
-    dataModule.io.needForward(i)(1) := canForward2 & vaddrModule.io.forwardMmask(i).asUInt
-
-    vaddrModule.io.forwardMdata(i) := io.forward(i).vaddr
-    vaddrModule.io.forwardDataMask(i) := io.forward(i).mask
-    paddrModule.io.forwardMdata(i) := io.forward(i).paddr
-    paddrModule.io.forwardDataMask(i) := io.forward(i).mask
-
-    // vaddr cam result does not equal to paddr cam result
-    // replay needed
-    // val vpmaskNotEqual = ((paddrModule.io.forwardMmask(i).asUInt ^ vaddrModule.io.forwardMmask(i).asUInt) & needForward) =/= 0.U
-    // val vaddrMatchFailed = vpmaskNotEqual && io.forward(i).valid
-    val vpmaskNotEqual = (
-      (RegEnable(paddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid) ^ RegEnable(vaddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid)) &
-      RegNext(needForward) &
-      GatedRegNext(addrRealValidVec.asUInt)
-    ) =/= 0.U
-    val vaddrMatchFailed = vpmaskNotEqual && RegNext(io.forward(i).valid)
-    XSInfo(vaddrMatchFailed,
-      "vaddrMatchFailed: pc %x pmask %x vmask %x\n",
-      RegEnable(io.forward(i).uop.pc, io.forward(i).valid),
-      RegEnable(needForward & paddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid),
-      RegEnable(needForward & vaddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid)
-    );
-    XSPerfAccumulate("vaddr_match_failed", vpmaskNotEqual)
-    XSPerfAccumulate("vaddr_match_really_failed", vaddrMatchFailed)
-
-    // Fast forward mask will be generated immediately (load_s1)
-    io.forward(i).forwardMaskFast := dataModule.io.forwardMaskFast(i)
-
-    // Forward result will be generated 1 cycle later (load_s2)
-    io.forward(i).forwardMask := dataModule.io.forwardMask(i)
-    io.forward(i).forwardData := dataModule.io.forwardData(i)
-
-    //TODO If the previous store appears out of alignment, then simply FF, this is a very unreasonable way to do it.
-    //TODO But for the time being, this is the way to ensure correctness. Such a suitable opportunity to support unaligned forward.
-    // If addr match, data not ready, mark it as dataInvalid
-    // load_s1: generate dataInvalid in load_s1 to set fastUop
-    val dataInvalidMask1 = ((addrValidVec.asUInt & ~dataValidVec.asUInt & vaddrModule.io.forwardMmask(i).asUInt) | unaligned.asUInt & allocated.asUInt) & forwardMask1.asUInt
-    val dataInvalidMask2 = ((addrValidVec.asUInt & ~dataValidVec.asUInt & vaddrModule.io.forwardMmask(i).asUInt) | unaligned.asUInt & allocated.asUInt) & forwardMask2.asUInt
-    val dataInvalidMask = dataInvalidMask1 | dataInvalidMask2
-    io.forward(i).dataInvalidFast := dataInvalidMask.orR
-
-    // make chisel happy
-    val dataInvalidMask1Reg = Wire(UInt(StoreQueueSize.W))
-    dataInvalidMask1Reg := RegNext(dataInvalidMask1)
-    // make chisel happy
-    val dataInvalidMask2Reg = Wire(UInt(StoreQueueSize.W))
-    dataInvalidMask2Reg := RegNext(dataInvalidMask2)
-    val dataInvalidMaskReg = dataInvalidMask1Reg | dataInvalidMask2Reg
-
-    // If SSID match, address not ready, mark it as addrInvalid
-    // load_s2: generate addrInvalid
-    val addrInvalidMask1 = (~addrValidVec.asUInt & storeSetHitVec.asUInt & forwardMask1.asUInt)
-    val addrInvalidMask2 = (~addrValidVec.asUInt & storeSetHitVec.asUInt & forwardMask2.asUInt)
-    // make chisel happy
-    val addrInvalidMask1Reg = Wire(UInt(StoreQueueSize.W))
-    addrInvalidMask1Reg := RegNext(addrInvalidMask1)
-    // make chisel happy
-    val addrInvalidMask2Reg = Wire(UInt(StoreQueueSize.W))
-    addrInvalidMask2Reg := RegNext(addrInvalidMask2)
-    val addrInvalidMaskReg = addrInvalidMask1Reg | addrInvalidMask2Reg
-
-    // load_s2
-    io.forward(i).dataInvalid := RegNext(io.forward(i).dataInvalidFast)
-    // check if vaddr forward mismatched
-    io.forward(i).matchInvalid := vaddrMatchFailed
-
-    // data invalid sq index
-    // check whether false fail
-    // check flag
-    val s2_differentFlag = RegNext(differentFlag)
-    val s2_enqPtrExt = RegNext(enqPtrExt(0))
-    val s2_deqPtrExt = RegNext(deqPtrExt(0))
-
-    // addr invalid sq index
-    // make chisel happy
-    val addrInvalidMaskRegWire = Wire(UInt(StoreQueueSize.W))
-    addrInvalidMaskRegWire := addrInvalidMaskReg
-    val addrInvalidFlag = addrInvalidMaskRegWire.orR
-    val hasInvalidAddr = (~addrValidVec.asUInt & needForward).orR
-
-    val addrInvalidSqIdx1 = OHToUInt(Reverse(PriorityEncoderOH(Reverse(addrInvalidMask1Reg))))
-    val addrInvalidSqIdx2 = OHToUInt(Reverse(PriorityEncoderOH(Reverse(addrInvalidMask2Reg))))
-    val addrInvalidSqIdx = Mux(addrInvalidMask2Reg.orR, addrInvalidSqIdx2, addrInvalidSqIdx1)
-
-    // store-set content management
-    //                +-----------------------+
-    //                | Search a SSID for the |
-    //                |    load operation     |
-    //                +-----------------------+
-    //                           |
-    //                           V
-    //                 +-------------------+
-    //                 | load wait strict? |
-    //                 +-------------------+
-    //                           |
-    //                           V
-    //               +----------------------+
-    //            Set|                      |Clean
-    //               V                      V
-    //  +------------------------+   +------------------------------+
-    //  | Waiting for all older  |   | Wait until the corresponding |
-    //  |   stores operations    |   | older store operations       |
-    //  +------------------------+   +------------------------------+
-
-
-
-    when (RegEnable(io.forward(i).uop.loadWaitStrict, io.forward(i).valid)) {
-      io.forward(i).addrInvalidSqIdx := RegEnable((io.forward(i).uop.sqIdx - 1.U), io.forward(i).valid)
-    } .elsewhen (addrInvalidFlag) {
-      io.forward(i).addrInvalidSqIdx.flag := Mux(!s2_differentFlag || addrInvalidSqIdx >= s2_deqPtrExt.value, s2_deqPtrExt.flag, s2_enqPtrExt.flag)
-      io.forward(i).addrInvalidSqIdx.value := addrInvalidSqIdx
-    } .otherwise {
-      // may be store inst has been written to sbuffer already.
-      io.forward(i).addrInvalidSqIdx := RegEnable(io.forward(i).uop.sqIdx, io.forward(i).valid)
-    }
-    io.forward(i).addrInvalid := Mux(RegEnable(io.forward(i).uop.loadWaitStrict, io.forward(i).valid), RegNext(hasInvalidAddr), addrInvalidFlag)
-
-    // data invalid sq index
-    // make chisel happy
-    val dataInvalidMaskRegWire = Wire(UInt(StoreQueueSize.W))
-    dataInvalidMaskRegWire := dataInvalidMaskReg
-    val dataInvalidFlag = dataInvalidMaskRegWire.orR
-
-    val dataInvalidSqIdx1 = OHToUInt(Reverse(PriorityEncoderOH(Reverse(dataInvalidMask1Reg))))
-    val dataInvalidSqIdx2 = OHToUInt(Reverse(PriorityEncoderOH(Reverse(dataInvalidMask2Reg))))
-    val dataInvalidSqIdx = Mux(dataInvalidMask2Reg.orR, dataInvalidSqIdx2, dataInvalidSqIdx1)
-
-    when (dataInvalidFlag) {
-      io.forward(i).dataInvalidSqIdx.flag := Mux(!s2_differentFlag || dataInvalidSqIdx >= s2_deqPtrExt.value, s2_deqPtrExt.flag, s2_enqPtrExt.flag)
-      io.forward(i).dataInvalidSqIdx.value := dataInvalidSqIdx
-    } .otherwise {
-      // may be store inst has been written to sbuffer already.
-      io.forward(i).dataInvalidSqIdx := RegEnable(io.forward(i).uop.sqIdx, io.forward(i).valid)
-    }
-  }
-
-  /**
-    * Memory mapped IO / other uncached operations / CMO
-    *
-    * States:
-    * (1) writeback from store units: mark as pending
-    * (2) when they reach ROB's head, they can be sent to uncache channel
-    * (3) response from uncache channel: mark as datavalidmask.wen
-    * (4) writeback to ROB (and other units): mark as writebacked
-    * (5) ROB commits the instruction: same as normal instructions
-    */
-  //(2) when they reach ROB's head, they can be sent to uncache channel
-  // TODO: CAN NOT deal with vector mmio now!
-  val s_idle :: s_req :: s_resp :: s_wb :: s_wait :: Nil = Enum(5)
-  val mmioState = RegInit(s_idle)
-  val uncacheUop = Reg(new DynInst)
-  val cboFlushedSb = RegInit(false.B)
-  val cmoOpCode = LSUOpType.getCmoOpcode(uncacheUop.fuOpType)
-  val mmioDoReq = io.uncache.req.fire && !io.uncache.req.bits.nc
-  val cboMmioPAddr = Reg(UInt(PAddrBits.W))
-  switch(mmioState) {
-    is(s_idle) {
-      when(RegNext(uop(deqPtr).robIdx === io.rob.pendingPtr && pending(deqPtr) && allocated(deqPtr) && datavalid(deqPtr) && addrvalid(deqPtr) && !hasException(deqPtr))) {
-        mmioState := s_req
-        uncacheUop := uop(deqPtr)
-        uncacheUop.exceptionVec := 0.U.asTypeOf(ExceptionVec())
-        uncacheUop.trigger := 0.U.asTypeOf(TriggerAction())
-        cboFlushedSb := false.B
-        cboMmioPAddr := paddrModule.io.rdata(0)
-      }
-    }
-    is(s_req) {
-      when (mmioDoReq) {
-        noPending := false.B
-        mmioState := s_resp
-      }
-    }
-    is(s_resp) {
-      when(io.uncache.resp.fire && !io.uncache.resp.bits.nc) {
-        noPending := true.B
-        mmioState := s_wb
-
-        when (io.uncache.resp.bits.denied || io.cmoOpResp.bits.denied) {
-          uncacheUop.exceptionVec(storeAccessFault) := true.B
-        }
-
-        when (io.uncache.resp.bits.corrupt && !io.uncache.resp.bits.denied ||
-              io.cmoOpResp.bits.corrupt && !io.cmoOpResp.bits.denied) {
-          uncacheUop.exceptionVec(hardwareError) := true.B
-        }
-      }
-    }
-    is(s_wb) {
-      when (io.mmioStout.fire || io.vecmmioStout.fire) {
-        when (ExceptionNO.selectByFu(uncacheUop.exceptionVec, StaCfg).asUInt.orR) {
-          mmioState := s_idle
-        }.otherwise {
-          mmioState := s_wait
-        }
-      }
-    }
-    is(s_wait) {
-      // A MMIO store can always move cmtPtrExt as it must be ROB head
-      when(scommit > 0.U) {
-        mmioState := s_idle // ready for next mmio
-      }
-    }
-  }
-
-  mmioReq.valid := mmioState === s_req && !LSUOpType.isCbo(uop(deqPtr).fuOpType) && !io.wfi.wfiReq
-  mmioReq.bits := DontCare
-  mmioReq.bits.cmd  := MemoryOpConstants.M_XWR
-  mmioReq.bits.addr := paddrModule.io.rdata(0) // data(deqPtr) -> rdata(0)
-  mmioReq.bits.vaddr:= vaddrModule.io.rdata(0)
-  mmioReq.bits.data := shiftDataToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).data)
-  mmioReq.bits.mask := shiftMaskToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).mask)
-  mmioReq.bits.robIdx := uop(GatedRegNext(rdataPtrExtNext(0)).value).robIdx
-  mmioReq.bits.memBackTypeMM := memBackTypeMM(GatedRegNext(rdataPtrExtNext(0)).value)
-  mmioReq.bits.nc := false.B
-  mmioReq.bits.id := rdataPtrExt(0).value
-
-  /**
-    * NC Store
-    * (1) req: when it has been commited, it can be sent to lower level.
-    * (2) resp: because SQ data forward is required, it can only be deq when ncResp is received
-    *
-    * NOTE: nc_req_ack is used to make sure that the request is written by the ubuffer and
-    * the ubuffer can forward the required data
-    */
-  // TODO: CAN NOT deal with vector nc now!
-  val nc_idle :: nc_req :: nc_req_ack :: nc_resp :: Nil = Enum(4)
-  val ncState = RegInit(nc_idle)
-  val rptr0 = rdataPtrExt(0).value
-  switch(ncState){
-    is(nc_idle) {
-      when(
-        nc(rptr0) && allocated(rptr0) && !completed(rptr0) && committed(rptr0) &&
-        allvalid(rptr0) && !isVec(rptr0) && !hasException(rptr0) && !mmio(rptr0)
-      ) {
-        ncState := nc_req
-        ncWaitRespPtrReg := rptr0
-      }
-    }
-    is(nc_req) {
-      when(ncDoReq) {
-        ncState := nc_req_ack
-      }
-    }
-    is(nc_req_ack) {
-      when(ncSlaveAck) {
-        when(io.uncacheOutstanding) {
-          ncState := nc_idle
-        }.otherwise{
-          ncState := nc_resp
-        }
-      }
-    }
-    is(nc_resp) {
-      when(ncResp.fire) {
-        ncState := nc_idle
-      }
-    }
-  }
-
-  ncDoReq := io.uncache.req.fire && io.uncache.req.bits.nc
-  ncDoResp := ncResp.fire
-  ncSlaveAck := io.uncache.idResp.valid && io.uncache.idResp.bits.nc
-  ncSlaveAckMid := io.uncache.idResp.bits.mid
-
-  ncReq.valid := ncState === nc_req && !io.wfi.wfiReq
-  ncReq.bits := DontCare
-  ncReq.bits.cmd  := MemoryOpConstants.M_XWR
-  ncReq.bits.addr := paddrModule.io.rdata(0)
-  ncReq.bits.vaddr:= vaddrModule.io.rdata(0)
-  ncReq.bits.data := shiftDataToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).data)
-  ncReq.bits.mask := shiftMaskToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).mask)
-  ncReq.bits.robIdx := uop(GatedRegNext(rdataPtrExtNext(0)).value).robIdx
-  ncReq.bits.memBackTypeMM := memBackTypeMM(GatedRegNext(rdataPtrExtNext(0)).value)
-  ncReq.bits.nc := true.B
-  ncReq.bits.id := rptr0
-
-  ncResp.ready := io.uncache.resp.ready
-  ncResp.valid := io.uncache.resp.fire && io.uncache.resp.bits.nc
-  ncResp.bits <> io.uncache.resp.bits
-  when (ncDeqTrigger) {
-    completed(ncPtr) := true.B
-  }
-  XSDebug(ncDeqTrigger,"nc fire: ptr %d\n", ncPtr)
-
-  mmioReq.ready := io.uncache.req.ready
-  ncReq.ready := io.uncache.req.ready && !mmioReq.valid
-  io.uncache.req.valid := mmioReq.valid || ncReq.valid
-  io.uncache.req.bits := Mux(mmioReq.valid, mmioReq.bits, ncReq.bits)
-
-  // CBO op type check can be delayed for 1 cycle,
-  // as uncache op will not start in s_idle
-  val cboMmioAddr = get_block_addr(cboMmioPAddr)
-  val deqCanDoCbo = GatedRegNext(LSUOpType.isCbo(uop(deqPtr).fuOpType) && allocated(deqPtr) && addrvalid(deqPtr) && !hasException(deqPtr))
-
-  val isCboZeroToSbVec = (0 until EnsbufferWidth).map{ i =>
-    io.sbuffer(i).fire && io.sbuffer(i).bits.vecValid && io.sbuffer(i).bits.wline && allocated(dataBuffer.io.deq(i).bits.sqPtr.value)
-  }
-  val cboZeroToSb        = isCboZeroToSbVec.reduce(_ || _)
-  val cboZeroFlushSb     = GatedRegNext(cboZeroToSb)
-
-  val cboZeroUop         = RegEnable(PriorityMux(isCboZeroToSbVec, dataBuffer.io.deq.map(x=>uop(x.bits.sqPtr.value))), cboZeroToSb)
-  val cboZeroSqIdx       = RegEnable(PriorityMux(isCboZeroToSbVec, dataBuffer.io.deq.map(_.bits.sqPtr)), cboZeroToSb)
-  val cboZeroValid       = RegInit(false.B)
-  val cboZeroWaitFlushSb = RegInit(false.B)
-
-  assert(!(PopCount(isCboZeroToSbVec) > 1.U), "Multiple cbo zero instructions cannot be executed at the same time")
-
-  when (cboZeroToSb) {
-    cboZeroValid       := true.B
-    cboZeroWaitFlushSb := true.B
-  }
-
-  when (deqCanDoCbo) {
-    // disable uncache channel
-    io.uncache.req.valid := false.B
-
-    when (io.cmoOpReq.fire) {
-      noPending := false.B
-      mmioState := s_resp
-    }
-
-    when (mmioState === s_resp) {
-      when (io.cmoOpResp.fire) {
-        noPending := true.B
-        mmioState := s_wb
-      }
-    }
-  }
-
-  io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb && (mmioState === s_req) && !io.wfi.wfiReq
-  io.cmoOpReq.bits.opcode  := cmoOpCode
-  io.cmoOpReq.bits.address := cboMmioAddr
-
-  io.cmoOpResp.ready := deqCanDoCbo && (mmioState === s_resp)
-
-  io.wfi.wfiSafe := GatedValidRegNext(noPending && io.wfi.wfiReq)
-
-  io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb && (mmioState === s_req) && !io.flushSbuffer.empty || cboZeroFlushSb
-
-  when(deqCanDoCbo && !cboFlushedSb && (mmioState === s_req) && io.flushSbuffer.empty) {
-    cboFlushedSb := true.B
-  }
-
-  when(mmioDoReq){
-    // mmio store should not be committed until uncache req is sent
-    pending(deqPtr) := false.B
-  }
-  XSDebug(
-    mmioDoReq,
-    p"uncache req: pc ${Hexadecimal(uop(deqPtr).pc)} " +
-    p"addr ${Hexadecimal(io.uncache.req.bits.addr)} " +
-    p"data ${Hexadecimal(io.uncache.req.bits.data)} " +
-    p"op ${Hexadecimal(io.uncache.req.bits.cmd)} " +
-    p"mask ${Hexadecimal(io.uncache.req.bits.mask)}\n"
-  )
-
-  // (3) response from uncache channel: mark as datavalid
-  io.uncache.resp.ready := true.B
-
-  // (4) scalar store: writeback to ROB (and other units): mark as writebacked
-  io.mmioStout.valid := mmioState === s_wb && !isVec(deqPtr)
-  io.mmioStout.bits := 0.U.asTypeOf(io.mmioStout.bits)
-  io.mmioStout.bits.data := VecInit(Seq.fill(staParams.head.wbPathNum)(shiftDataToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).data)))
-  io.mmioStout.bits.pdest := uncacheUop.pdest
-  io.mmioStout.bits.robIdx := uncacheUop.robIdx
-  io.mmioStout.bits.intWen.foreach(_ := uncacheUop.rfWen)
-  io.mmioStout.bits.exceptionVec.foreach(_ := ExceptionNO.selectByFu(uncacheUop.exceptionVec, StaCfg))
-  io.mmioStout.bits.flushPipe.foreach(_ := deqCanDoCbo) // flush Pipeline to keep order in CMO
-  io.mmioStout.bits.sqIdx.foreach(_ := deqPtrExt(0))
-  io.mmioStout.bits.trigger.foreach(_ := uncacheUop.trigger)
-  io.mmioStout.bits.perfDebugInfo.foreach(_ := uncacheUop.perfDebugInfo)
-  io.mmioStout.bits.debug_seqNum.foreach(_ := uncacheUop.debug_seqNum)
-  io.mmioStout.bits.debug.isMMIO := true.B
-  io.mmioStout.bits.debug.isNCIO := false.B
-  io.mmioStout.bits.debug.paddr := DontCare
-  io.mmioStout.bits.debug.isPerfCnt := false.B
-  io.mmioStout.bits.debug.vaddr := DontCare
-  // Remove MMIO inst from store queue after MMIO request is being sent
-  // That inst will be traced by uncache state machine
-  when (io.mmioStout.fire) {
-    completed(deqPtr) := true.B
-  }
-
-  // cbo Zero writeback to ROB
-  io.cboZeroStout.valid                := cboZeroValid && !cboZeroWaitFlushSb
-  io.cboZeroStout.bits := 0.U.asTypeOf(io.cboZeroStout.bits)
-  io.cboZeroStout.bits.pdest := cboZeroUop.pdest
-  io.cboZeroStout.bits.robIdx := cboZeroUop.robIdx
-  io.cboZeroStout.bits.intWen.foreach(_ := cboZeroUop.rfWen)
-  io.cboZeroStout.bits.exceptionVec.foreach(_ := cboZeroUop.exceptionVec)
-  io.cboZeroStout.bits.flushPipe.foreach(_ := cboZeroUop.flushPipe) // false.B ?
-  io.cboZeroStout.bits.sqIdx.foreach(_ := cboZeroSqIdx)
-  io.cboZeroStout.bits.trigger.foreach(_ := cboZeroUop.trigger)
-  io.cboZeroStout.bits.perfDebugInfo.foreach(_ := cboZeroUop.perfDebugInfo)
-  io.cboZeroStout.bits.debug_seqNum.foreach(_ := cboZeroUop.debug_seqNum)
-
-  when (cboZeroWaitFlushSb && io.flushSbuffer.empty) {
-    cboZeroWaitFlushSb    := false.B
-  }
-  when (io.cboZeroStout.fire) {
-    completed(cboZeroSqIdx.value) := true.B
-    cboZeroValid := false.B
-  }
-
-  exceptionBuffer.io.storeAddrIn.last.valid := io.mmioStout.fire
-  exceptionBuffer.io.storeAddrIn.last.bits := DontCare
-  exceptionBuffer.io.storeAddrIn.last.bits.fullva := vaddrModule.io.rdata.head
-  exceptionBuffer.io.storeAddrIn.last.bits.vaNeedExt := true.B
-  exceptionBuffer.io.storeAddrIn.last.bits.uop := uncacheUop
-
-  // (4) or vector store:
-  // TODO: implement it!
-  io.vecmmioStout.valid := false.B
-  io.vecmmioStout.bits := DontCare
-  // Remove MMIO inst from store queue after MMIO request is being sent
-  // That inst will be traced by uncache state machine
-  when (io.vecmmioStout.fire) {
-    completed(deqPtr) := true.B
-  }
-
-  /**
-    * ROB commits store instructions (mark them as committed)
-    *
-    * (1) When store commits, mark it as committed.
-    * (2) They will not be cancelled and can be sent to lower level.
-    */
-  XSError(mmioState =/= s_idle && mmioState =/= s_wait && commitCount > 0.U,
-   "should not commit instruction when MMIO has not been finished\n")
-
-  val commitVec = WireInit(VecInit(Seq.fill(CommitWidth)(false.B)))
-  val needCancel = Wire(Vec(StoreQueueSize, Bool())) // Will be assigned later
-
-  if (backendParams.debugEn){ dontTouch(commitVec) }
-
-  // TODO: Deal with vector store mmio
-  for (i <- 0 until CommitWidth) {
-    // don't mark misalign store as committed
-    val ptr = cmtPtrExt(i).value
-    val isCommit = WireInit(false.B)
-    when (
-      allocated(ptr) &&
-      isNotAfter(uop(ptr).robIdx, GatedRegNext(io.rob.pendingPtr)) &&
-      !needCancel(ptr) &&
-      (!waitStoreS2(ptr) || isVec(ptr))) {
-      if (i == 0){
-        // TODO: fixme for vector mmio
-        when ((mmioState === s_idle) || (mmioState === s_wait && scommit > 0.U)){
-          when ((isVec(ptr) && vecMbCommit(ptr)) || !isVec(ptr)) {
-            isCommit := true.B
-            committed(ptr) := true.B
-            commitVec(0) := true.B
-          }
-        }
-      } else {
-        when ((isVec(ptr) && vecMbCommit(ptr)) || !isVec(ptr)) {
-          isCommit := commitVec(i - 1) || committed(ptr)
-          committed(ptr) := commitVec(i - 1) || committed(ptr)
-          commitVec(i) := commitVec(i - 1)
-        }
-      }
-    }
-    when(isCommit && nc(ptr) && hasException(ptr)) {
-      completed(ptr) := true.B
-    }
-  }
-
-  commitCount := PopCount(commitVec)
-  cmtPtrExt := cmtPtrExt.map(_ + commitCount)
-  io.sqCommitPtr := cmtPtrExt(0)
-  io.sqCommitUopIdx := uop(cmtPtrExt(0).value).uopIdx
-  io.sqCommitRobIdx := uop(cmtPtrExt(0).value).robIdx
-
-  /**
-   * committed stores will not be cancelled and can be sent to lower level.
-   *
-   * 1. Store NC: Read data to uncache
-   *    implement as above
-   *
-   * 2. Store Cache: Read data from data module
-   *    remove retired insts from sq, add retired store to sbuffer.
-   *    as store queue grows larger and larger, time needed to read data from data
-   *    module keeps growing higher. Now we give data read a whole cycle.
-   */
-
-  //TODO An unaligned command can only be sent out if the databuffer can enter more than two.
-  //TODO For now, hardcode the number of ENQs for the databuffer.
-  val canDeqMisaligned = dataBuffer.io.enq(0).ready && dataBuffer.io.enq(1).ready
-  val firstWithMisalign = unaligned(rdataPtrExt(0).value)
-  val firstWithCross16Byte = cross16Byte(rdataPtrExt(0).value)
-
-  val isCross4KPage = io.maControl.toStoreQueue.crossPageWithHit
-  val isCross4KPageCanDeq = io.maControl.toStoreQueue.crossPageCanDeq
-  // When encountering a cross page store, a request needs to be sent to storeMisalignBuffer for the high page table's paddr.
-  io.maControl.toStoreMisalignBuffer.sqPtr := rdataPtrExt(0)
-  io.maControl.toStoreMisalignBuffer.doDeq := isCross4KPage && isCross4KPageCanDeq && dataBuffer.io.enq(0).fire
-  io.maControl.toStoreMisalignBuffer.uop := uop(rdataPtrExt(0).value)
-  for (i <- 0 until EnsbufferWidth) {
-    val ptr = rdataPtrExt(i).value
-    val mmioStall = if(i == 0) mmio(rdataPtrExt(0).value) else (mmio(rdataPtrExt(i).value) || mmio(rdataPtrExt(i-1).value))
-    val ncStall = if(i == 0) nc(rdataPtrExt(0).value) else (nc(rdataPtrExt(i).value) || nc(rdataPtrExt(i-1).value))
-    val exceptionValid = if(i == 0) hasException(rdataPtrExt(0).value) else {
-      hasException(rdataPtrExt(i).value) || (hasException(rdataPtrExt(i-1).value) && uop(rdataPtrExt(i).value).robIdx === uop(rdataPtrExt(i-1).value).robIdx)
-    }
-    val vecNotAllMask = dataModule.io.rdata(i).mask.orR
-    // Vector instructions that prevent triggered exceptions from being written to the 'databuffer'.
-    val vecHasExceptionFlagValid = vecExceptionFlag.valid && isVec(ptr) && vecExceptionFlag.bits.robIdx === uop(ptr).robIdx
-
-    val misalignToDataBufferValid = allocated(rdataPtrExt(0).value) && committed(rdataPtrExt(0).value) &&
-                                    (!isVec(rdataPtrExt(0).value) && allvalid(rdataPtrExt(0).value) || vecMbCommit(rdataPtrExt(0).value)) &&
-                                    canDeqMisaligned && (!isCross4KPage || isCross4KPageCanDeq || hasException(rdataPtrExt(0).value))
-    // Only the first interface can write unaligned directives.
-    // Simplified design, even if the two ports have exceptions, but still only one unaligned dequeue.
-    val assert_flag = WireInit(false.B)
-    when(firstWithMisalign && firstWithCross16Byte) {
-      dataBuffer.io.enq(i).valid := misalignToDataBufferValid
-      assert_flag := dataBuffer.io.enq(1).valid
-    }.otherwise {
-      dataBuffer.io.enq(i).valid := (
-        allocated(ptr) && committed(ptr)
-          && ((!isVec(ptr) && (allvalid(ptr) || hasException(ptr))) || vecMbCommit(ptr))
-          && !mmioStall && !ncStall
-          && (!unaligned(ptr) || !cross16Byte(ptr) && (allvalid(ptr) || hasException(ptr)))
-        )
-    }
-
-    val misalignAddrLow = vaddrModule.io.rdata(0)(2, 0)
-    val cross16ByteAddrLow4bit = vaddrModule.io.rdata(0)(3, 0)
-    val addrLow4bit = vaddrModule.io.rdata(i)(3, 0)
-
-    // For unaligned, we need to generate a base-aligned mask in storeunit and then do a shift split in StoreQueue.
-    val Cross16ByteMask = Wire(UInt(32.W))
-    val Cross16ByteData = Wire(UInt(256.W))
-    Cross16ByteMask := dataModule.io.rdata(0).mask << cross16ByteAddrLow4bit
-    Cross16ByteData := dataModule.io.rdata(0).data << (cross16ByteAddrLow4bit << 3)
-
-    val paddrLow  = Cat(paddrModule.io.rdata(0)(paddrModule.io.rdata(0).getWidth - 1, 3), 0.U(3.W))
-    val paddrHigh = Cat(paddrModule.io.rdata(0)(paddrModule.io.rdata(0).getWidth - 1, 3), 0.U(3.W)) + 8.U
-
-    val vaddrLow  = Cat(vaddrModule.io.rdata(0)(vaddrModule.io.rdata(0).getWidth - 1, 3), 0.U(3.W))
-    val vaddrHigh = Cat(vaddrModule.io.rdata(0)(vaddrModule.io.rdata(0).getWidth - 1, 3), 0.U(3.W)) + 8.U
-
-    val maskLow   = Cross16ByteMask(15, 0)
-    val maskHigh  = Cross16ByteMask(31, 16)
-
-    val dataLow   = Cross16ByteData(127, 0)
-    val dataHigh  = Cross16ByteData(255, 128)
-
-    val toSbufferVecValid = (!isVec(ptr) || (vecMbCommit(ptr) && allvalid(ptr) && vecNotAllMask)) && !exceptionValid && !vecHasExceptionFlagValid
-    when(canDeqMisaligned && firstWithMisalign && firstWithCross16Byte) {
-      when(isCross4KPage && isCross4KPageCanDeq) {
-        if (i == 0) {
-          dataBuffer.io.enq(i).bits.addr      := paddrLow
-          dataBuffer.io.enq(i).bits.vaddr     := vaddrLow
-          dataBuffer.io.enq(i).bits.data      := dataLow
-          dataBuffer.io.enq(i).bits.mask      := maskLow
-          dataBuffer.io.enq(i).bits.wline     := false.B
-          dataBuffer.io.enq(i).bits.sqPtr     := rdataPtrExt(0)
-          dataBuffer.io.enq(i).bits.prefetch  := false.B
-          dataBuffer.io.enq(i).bits.sqNeedDeq := true.B
-          dataBuffer.io.enq(i).bits.vecValid  := toSbufferVecValid
-        }
-        else {
-          dataBuffer.io.enq(i).bits.addr      := io.maControl.toStoreQueue.paddr
-          dataBuffer.io.enq(i).bits.vaddr     := vaddrHigh
-          dataBuffer.io.enq(i).bits.data      := dataHigh
-          dataBuffer.io.enq(i).bits.mask      := maskHigh
-          dataBuffer.io.enq(i).bits.wline     := false.B
-          dataBuffer.io.enq(i).bits.sqPtr     := rdataPtrExt(0)
-          dataBuffer.io.enq(i).bits.prefetch  := false.B
-          dataBuffer.io.enq(i).bits.sqNeedDeq := false.B
-          dataBuffer.io.enq(i).bits.vecValid  := dataBuffer.io.enq(0).bits.vecValid
-        }
-      } .otherwise {
-        if (i == 0) {
-          dataBuffer.io.enq(i).bits.addr      := paddrLow
-          dataBuffer.io.enq(i).bits.vaddr     := vaddrLow
-          dataBuffer.io.enq(i).bits.data      := dataLow
-          dataBuffer.io.enq(i).bits.mask      := maskLow
-          dataBuffer.io.enq(i).bits.wline     := false.B
-          dataBuffer.io.enq(i).bits.sqPtr     := rdataPtrExt(0)
-          dataBuffer.io.enq(i).bits.prefetch  := false.B
-          dataBuffer.io.enq(i).bits.sqNeedDeq  := true.B
-          dataBuffer.io.enq(i).bits.vecValid  := toSbufferVecValid
-        }
-        else {
-          dataBuffer.io.enq(i).bits.addr      := paddrHigh
-          dataBuffer.io.enq(i).bits.vaddr     := vaddrHigh
-          dataBuffer.io.enq(i).bits.data      := dataHigh
-          dataBuffer.io.enq(i).bits.mask      := maskHigh
-          dataBuffer.io.enq(i).bits.wline     := false.B
-          dataBuffer.io.enq(i).bits.sqPtr     := rdataPtrExt(0)
-          dataBuffer.io.enq(i).bits.prefetch  := false.B
-          dataBuffer.io.enq(i).bits.sqNeedDeq  := false.B
-          dataBuffer.io.enq(i).bits.vecValid  := dataBuffer.io.enq(0).bits.vecValid
-        }
-      }
-
-
-    }.elsewhen(!cross16Byte(ptr) && unaligned(ptr)) {
-      dataBuffer.io.enq(i).bits.addr     := Cat(paddrModule.io.rdata(i)(PAddrBits - 1, 4), 0.U(4.W))
-      dataBuffer.io.enq(i).bits.vaddr    := Cat(vaddrModule.io.rdata(i)(VAddrBits - 1, 4), 0.U(4.W))
-      dataBuffer.io.enq(i).bits.data     := dataModule.io.rdata(i).data << (addrLow4bit << 3)
-      dataBuffer.io.enq(i).bits.mask     := dataModule.io.rdata(i).mask
-      dataBuffer.io.enq(i).bits.wline    := paddrModule.io.rlineflag(i)
-      dataBuffer.io.enq(i).bits.sqPtr    := rdataPtrExt(i)
-      dataBuffer.io.enq(i).bits.prefetch := prefetch(ptr)
-      dataBuffer.io.enq(i).bits.sqNeedDeq := true.B
-      // when scalar has exception, will also not write into sbuffer
-      dataBuffer.io.enq(i).bits.vecValid := toSbufferVecValid
-    }.otherwise {
-      dataBuffer.io.enq(i).bits.addr     := paddrModule.io.rdata(i)
-      dataBuffer.io.enq(i).bits.vaddr    := vaddrModule.io.rdata(i)
-      dataBuffer.io.enq(i).bits.data     := dataModule.io.rdata(i).data
-      dataBuffer.io.enq(i).bits.mask     := dataModule.io.rdata(i).mask
-      dataBuffer.io.enq(i).bits.wline    := paddrModule.io.rlineflag(i)
-      dataBuffer.io.enq(i).bits.sqPtr    := rdataPtrExt(i)
-      dataBuffer.io.enq(i).bits.prefetch := prefetch(ptr)
-      dataBuffer.io.enq(i).bits.sqNeedDeq := true.B
-      // when scalar has exception, will also not write into sbuffer
-      dataBuffer.io.enq(i).bits.vecValid := toSbufferVecValid
-
-    }
-
-    // Note that store data/addr should both be valid after store's commit
-    assert(!dataBuffer.io.enq(i).valid || allvalid(ptr) || hasException(ptr) || (allocated(ptr) && vecMbCommit(ptr)) || assert_flag)
-  }
-
-  // Send data stored in sbufferReqBitsReg to sbuffer
-  for (i <- 0 until EnsbufferWidth) {
-    io.sbuffer(i).valid := dataBuffer.io.deq(i).valid
-    dataBuffer.io.deq(i).ready := io.sbuffer(i).ready
-    io.sbuffer(i).bits.fromDataBufferEntry(dataBuffer.io.deq(i).bits, MemoryOpConstants.M_XWR)
-    // io.sbuffer(i).fire is RegNexted, as sbuffer data write takes 2 cycles.
-    // Before data write finish, sbuffer is unable to provide store to load
-    // forward data. As an workaround, deqPtrExt and allocated flag update
-    // is delayed so that load can get the right data from store queue.
-    // ---
-    // Only sqNeedDeq can move the ptr.
-    // ---
-    // however, `completed` is register, when it turn true, the data has already been written to sbuffer
-    // Besides, we should not have cbozero completed. (wline is currently only for cbozero)
-    val ptr = dataBuffer.io.deq(i).bits.sqPtr.value
-    when (io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq && !io.sbuffer(i).bits.wline) {
-
-      completed(ptr) := true.B
-    }
-    XSDebug(RegNext(io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq), "sbuffer "+i+" fire: ptr %d\n", ptr)
-  }
-
-  // All vector instruction uop normally dequeue, but the Uop after the exception is raised does not write to the 'sbuffer'.
-  // Flags are used to record whether there are any exceptions when the queue is displayed.
-  // This is determined each time a write is made to the 'databuffer', prevent subsequent uop of the same instruction from writing to the 'dataBuffer'.
-  val vecCommitHasException = (0 until EnsbufferWidth).map{ i =>
-    val ptr = rdataPtrExt(i).value
-    val mmioStall = if(i == 0) mmio(rdataPtrExt(0).value) else (mmio(rdataPtrExt(i).value) || mmio(rdataPtrExt(i-1).value))
-    val ncStall = if(i == 0) nc(rdataPtrExt(0).value) else (nc(rdataPtrExt(i).value) || nc(rdataPtrExt(i-1).value))
-    val exceptionVliad      = isVec(ptr) && hasException(ptr) && dataBuffer.io.enq(i).fire && dataBuffer.io.enq(i).bits.sqNeedDeq
-    (exceptionVliad, uop(ptr), vecLastFlow(ptr))
-  }
-
-  val vecCommitHasExceptionValid      = vecCommitHasException.map(_._1)
-  val vecCommitHasExceptionUop        = vecCommitHasException.map(_._2)
-  val vecCommitHasExceptionLastFlow   = vecCommitHasException.map(_._3)
-  val vecCommitHasExceptionValidOR    = vecCommitHasExceptionValid.reduce(_ || _)
-  // Just select the last Uop tah has an exception.
-  val vecCommitHasExceptionSelectUop  = ParallelPosteriorityMux(vecCommitHasExceptionValid, vecCommitHasExceptionUop)
-  // If the last flow with an exception is the LastFlow of this instruction, the flag is not set.
-  // compare robidx to select the last flow
-  require(EnsbufferWidth == 2, "The vector store exception handle process only support EnsbufferWidth == 2 yet.")
-  val robidxEQ = dataBuffer.io.enq(0).fire && dataBuffer.io.enq(1).fire &&
-    uop(rdataPtrExt(0).value).robIdx === uop(rdataPtrExt(1).value).robIdx
-  val robidxNE = dataBuffer.io.enq(0).fire && dataBuffer.io.enq(1).fire && (
-    uop(rdataPtrExt(0).value).robIdx =/= uop(rdataPtrExt(1).value).robIdx
-  )
-  val onlyCommit0 = dataBuffer.io.enq(0).fire && !dataBuffer.io.enq(1).fire
-
-  val vecCommitLastFlow =
-    // robidx equal => check if 1 is last flow
-    robidxEQ && vecCommitHasExceptionLastFlow(1) ||
-    // robidx not equal => 0 must be the last flow, just check if 1 is last flow when 1 has exception
-    robidxNE && (vecCommitHasExceptionValid(1) && vecCommitHasExceptionLastFlow(1) || !vecCommitHasExceptionValid(1)) ||
-    onlyCommit0 && vecCommitHasExceptionLastFlow(0)
-
-
-  val vecExceptionFlagCancel  = (0 until EnsbufferWidth).map{ i =>
-    val ptr = rdataPtrExt(i).value
-    val vecLastFlowCommit = vecLastFlow(ptr) && (uop(ptr).robIdx === vecExceptionFlag.bits.robIdx) &&
-                            dataBuffer.io.enq(i).fire && dataBuffer.io.enq(i).bits.sqNeedDeq
-    vecLastFlowCommit
-  }.reduce(_ || _)
-
-  // When a LastFlow with an exception instruction is commited, clear the flag.
-  when(!vecExceptionFlag.valid && vecCommitHasExceptionValidOR && !vecCommitLastFlow) {
-    vecExceptionFlag.valid  := true.B
-    vecExceptionFlag.bits   := vecCommitHasExceptionSelectUop
-  }.elsewhen(vecExceptionFlag.valid && vecExceptionFlagCancel) {
-    vecExceptionFlag.valid  := false.B
-    vecExceptionFlag.bits   := 0.U.asTypeOf(new DynInst)
-  }
-
-  // A dumb defensive code. The flag should not be placed for a long period of time.
-  // A relatively large timeout period, not have any special meaning.
-  // If an assert appears and you confirm that it is not a Bug: Increase the timeout or remove the assert.
-  TimeOutAssert(vecExceptionFlag.valid, 3000, "vecExceptionFlag timeout, Plase check for bugs or add timeouts.")
-
-  /* difftest */
-  // Initialize when unenabled difftest.
-  io.diffStore := DontCare
-  // Consistent with the logic above.
-  // Only the vector store difftest required signal is separated from the rtl code.
-  if (env.EnableDifftest) {
-    // commit cbo.inval to difftest
-    val cmoInvalEvent = DifftestModule(new DiffCMOInvalEvent)
-    cmoInvalEvent.coreid := io.hartId
-    cmoInvalEvent.valid := io.mmioStout.fire && deqCanDoCbo && LSUOpType.isCboInval(uop(deqPtr).fuOpType)
-    cmoInvalEvent.addr := cboMmioAddr
-
-    // DiffStoreEvent happens when rdataPtr moves.
-    // That is, pmsStore enter dataBuffer or ncStore enter Ubuffer
-    (0 until EnsbufferWidth).foreach { i =>
-      // when i = 0, the sqPtr is rdataPtr(0), which is rdataPtrExt(0), so it applies to NC as well.
-      val ptr = dataBuffer.io.enq(i).bits.sqPtr.value
-      io.diffStore.diffInfo(i).uop := uop(ptr)
-      io.diffStore.diffInfo(i).start := debug_vec_unaligned_start(ptr)
-      io.diffStore.diffInfo(i).offset := debug_vec_unaligned_offset(ptr)
-      io.diffStore.pmaStore(i).valid := dataBuffer.io.enq(i).fire
-      io.diffStore.pmaStore(i).bits.fromDataBufferEntry(dataBuffer.io.enq(i).bits, MemoryOpConstants.M_XWR)
-    }
-    io.diffStore.ncStore.valid := ncReq.fire && ncReq.bits.memBackTypeMM
-    io.diffStore.ncStore.bits := ncReq.bits
-  }
-
-
-  (1 until EnsbufferWidth).foreach(i => when(io.sbuffer(i).fire) { assert(io.sbuffer(i - 1).fire) })
-  if (coreParams.dcacheParametersOpt.isEmpty) {
-    for (i <- 0 until EnsbufferWidth) {
-      val ptr = deqPtrExt(i).value
-      val ram = DifftestMem(64L * 1024 * 1024 * 1024, 8)
-      val wen = allocated(ptr) && committed(ptr) && !mmio(ptr)
-      val waddr = ((paddrModule.io.rdata(i) - "h80000000".U) >> 3).asUInt
-      val wdata = Mux(paddrModule.io.rdata(i)(3), dataModule.io.rdata(i).data(127, 64), dataModule.io.rdata(i).data(63, 0))
-      val wmask = Mux(paddrModule.io.rdata(i)(3), dataModule.io.rdata(i).mask(15, 8), dataModule.io.rdata(i).mask(7, 0))
-      when (wen) {
-        ram.write(waddr, wdata.asTypeOf(Vec(8, UInt(8.W))), wmask.asBools)
-      }
-    }
-  }
-
-  // Read vaddr for mem exception
-  io.exceptionAddr.vaddr     := exceptionBuffer.io.exceptionAddr.vaddr
-  io.exceptionAddr.vaNeedExt := exceptionBuffer.io.exceptionAddr.vaNeedExt
-  io.exceptionAddr.isHyper   := exceptionBuffer.io.exceptionAddr.isHyper
-  io.exceptionAddr.gpaddr    := exceptionBuffer.io.exceptionAddr.gpaddr
-  io.exceptionAddr.vstart    := exceptionBuffer.io.exceptionAddr.vstart
-  io.exceptionAddr.vl        := exceptionBuffer.io.exceptionAddr.vl
-  io.exceptionAddr.isForVSnonLeafPTE := exceptionBuffer.io.exceptionAddr.isForVSnonLeafPTE
-
-  // vector commit or replay from
-  val vecCommittmp = Wire(Vec(StoreQueueSize, Vec(VecStorePipelineWidth, Bool())))
-  val vecCommit = Wire(Vec(StoreQueueSize, Bool()))
-  for (i <- 0 until StoreQueueSize) {
-    val fbk = io.vecFeedback
-    for (j <- 0 until VecStorePipelineWidth) {
-      vecCommittmp(i)(j) := fbk(j).valid && (fbk(j).bits.isCommit || fbk(j).bits.isFlush) &&
-        uop(i).robIdx === fbk(j).bits.robidx && uop(i).uopIdx === fbk(j).bits.uopidx && allocated(i)
-    }
-    vecCommit(i) := vecCommittmp(i).reduce(_ || _)
-
-    when (vecCommit(i)) {
-      vecMbCommit(i) := true.B
-    }
-  }
-
-  // For vector, when there is a store across pages with the same uop in storeMisalignBuffer, storequeue needs to mark this item as committed.
-  // TODO FIXME Can vecMbCommit be removed?
-  when(io.maControl.toStoreQueue.withSameUop && allvalid(rdataPtrExt(0).value)) {
-    vecMbCommit(rdataPtrExt(0).value) := true.B
-  }
-
-  // misprediction recovery / exception redirect
-  // invalidate sq term using robIdx
-  for (i <- 0 until StoreQueueSize) {
-    needCancel(i) := allocated(i) && !committed(i) && Mux(
-        vecExceptionFlag.valid,
-        isAfter(uop(i).robIdx, io.brqRedirect.bits.robIdx) && io.brqRedirect.valid,
-        uop(i).robIdx.needFlush(io.brqRedirect)
-      )
-    when (needCancel(i)) {
-      allocated(i) := false.B
-      completed(i) := false.B
-    }
-  }
-
- /**
-* update pointers
-**/
-  val enqCancelValid = canEnqueue.zip(io.enq.req).map{case (v , x) =>
-    v && x.bits.robIdx.needFlush(io.brqRedirect)
-  }
-  val enqCancelNum = enqCancelValid.zip(vStoreFlow).map{case (v, flow) =>
-    Mux(v, flow, 0.U)
-  }
-  val lastEnqCancel = RegEnable(enqCancelNum.reduce(_ + _), io.brqRedirect.valid) // 1 cycle after redirect
-
-  val lastCycleCancelCount = PopCount(RegEnable(needCancel, io.brqRedirect.valid)) // 1 cycle after redirect
-  val lastCycleRedirect = RegNext(io.brqRedirect.valid) // 1 cycle after redirect
-  val enqNumber = validVStoreFlow.reduce(_ + _)
-
-  val lastlastCycleRedirect=RegNext(lastCycleRedirect)// 2 cycle after redirect
-  val redirectCancelCount = RegEnable(lastCycleCancelCount + lastEnqCancel, 0.U, lastCycleRedirect) // 2 cycle after redirect
-
-  when (lastlastCycleRedirect) {
-    // we recover the pointers in 2 cycle after redirect for better timing
-    enqPtrExt := VecInit(enqPtrExt.map(_ - redirectCancelCount))
-  }.otherwise {
-    // lastCycleRedirect.valid or nornal case
-    // when lastCycleRedirect.valid, enqNumber === 0.U, enqPtrExt will not change
-    enqPtrExt := VecInit(enqPtrExt.map(_ + enqNumber))
-  }
-  assert(!(lastCycleRedirect && enqNumber =/= 0.U))
-
-  deqPtrExt := deqPtrExtNext
-  rdataPtrExt := rdataPtrExtNext
-
-  // val dequeueCount = Mux(io.sbuffer(1).fire, 2.U, Mux(io.sbuffer(0).fire || io.mmioStout.fire, 1.U, 0.U))
-
-  // If redirect at T0, sqCancelCnt is at T2
-  io.sqCancelCnt := redirectCancelCount
-  val ForceWriteUpper = Wire(UInt(log2Up(StoreQueueSize + 1).W))
-  ForceWriteUpper := Constantin.createRecord(s"ForceWriteUpper_${p(XSCoreParamsKey).HartId}", initValue = StoreQueueForceWriteSbufferUpper)
-  val ForceWriteLower = Wire(UInt(log2Up(StoreQueueSize + 1).W))
-  ForceWriteLower := Constantin.createRecord(s"ForceWriteLower_${p(XSCoreParamsKey).HartId}", initValue = StoreQueueForceWriteSbufferLower)
-
-  val valid_cnt = PopCount(allocated)
-  io.force_write := RegNext(Mux(valid_cnt >= ForceWriteUpper, true.B, valid_cnt >= ForceWriteLower && io.force_write), init = false.B)
-
-  // io.sqempty will be used by sbuffer
-  // We delay it for 1 cycle for better timing
-  // When sbuffer need to check if it is empty, the pipeline is blocked, which means delay io.sqempty
-  // for 1 cycle will also promise that sq is empty in that cycle
-  io.sqEmpty := RegNext(
-    enqPtrExt(0).value === deqPtrExt(0).value &&
-    enqPtrExt(0).flag === deqPtrExt(0).flag
-  )
-  // perf counter
-  QueuePerf(StoreQueueSize, validCount, !allowEnqueue)
-  val vecValidVec = WireInit(VecInit((0 until StoreQueueSize).map(i => allocated(i) && isVec(i))))
-  QueuePerf(StoreQueueSize, PopCount(vecValidVec), !allowEnqueue)
-  io.sqFull := !allowEnqueue
-  XSPerfAccumulate("mmioCycle", mmioState =/= s_idle) // lq is busy dealing with uncache req
-  XSPerfAccumulate("mmioCnt", mmioDoReq)
-  XSPerfAccumulate("mmio_wb_success", io.mmioStout.fire || io.vecmmioStout.fire)
-  XSPerfAccumulate("mmio_wb_blocked", (io.mmioStout.valid && !io.mmioStout.ready) || (io.vecmmioStout.valid && !io.vecmmioStout.ready))
-  XSPerfAccumulate("validEntryCnt", distanceBetween(enqPtrExt(0), deqPtrExt(0)))
-  XSPerfAccumulate("cmtEntryCnt", distanceBetween(cmtPtrExt(0), deqPtrExt(0)))
-  XSPerfAccumulate("nCmtEntryCnt", distanceBetween(enqPtrExt(0), cmtPtrExt(0)))
-
-  val perfValidCount = distanceBetween(enqPtrExt(0), deqPtrExt(0))
-  val perfEvents = Seq(
-    ("mmioCycle      ", mmioState =/= s_idle),
-    ("mmioCnt        ", mmioDoReq),
-    ("mmio_wb_success", io.mmioStout.fire || io.vecmmioStout.fire),
-    ("mmio_wb_blocked", (io.mmioStout.valid && !io.mmioStout.ready) || (io.vecmmioStout.valid && !io.vecmmioStout.ready)),
-    ("stq_1_4_valid  ", (perfValidCount < (StoreQueueSize.U/4.U))),
-    ("stq_2_4_valid  ", (perfValidCount > (StoreQueueSize.U/4.U)) & (perfValidCount <= (StoreQueueSize.U/2.U))),
-    ("stq_3_4_valid  ", (perfValidCount > (StoreQueueSize.U/2.U)) & (perfValidCount <= (StoreQueueSize.U*3.U/4.U))),
-    ("stq_4_4_valid  ", (perfValidCount > (StoreQueueSize.U*3.U/4.U))),
-  )
-  generatePerfEvent()
-
-  // debug info
-  XSDebug("enqPtrExt %d:%d deqPtrExt %d:%d\n", enqPtrExt(0).flag, enqPtr, deqPtrExt(0).flag, deqPtr)
-
-  def PrintFlag(flag: Bool, name: String): Unit = {
-    XSDebug(false, flag, name) // when(flag)
-    XSDebug(false, !flag, " ") // otherwirse
-  }
-
-  for (i <- 0 until StoreQueueSize) {
-    XSDebug(s"$i: pc %x va %x pa %x data %x ",
-      uop(i).pc,
-      debug_vaddr(i),
-      debug_paddr(i),
-      debug_data(i)
-    )
-    PrintFlag(allocated(i), "a")
-    PrintFlag(allocated(i) && addrvalid(i), "a")
-    PrintFlag(allocated(i) && datavalid(i), "d")
-    PrintFlag(allocated(i) && committed(i), "c")
-    PrintFlag(allocated(i) && pending(i), "p")
-    PrintFlag(allocated(i) && mmio(i), "m")
-    XSDebug(false, true.B, "\n")
-  }
-
-}
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala
deleted file mode 100644
index 66dc241a60b..00000000000
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala
+++ /dev/null
@@ -1,350 +0,0 @@
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
-package xiangshan.mem
-
-import org.chipsalliance.cde.config.Parameters
-import chisel3._
-import chisel3.util._
-import utils._
-import utility._
-import xiangshan._
-import xiangshan.cache._
-import xiangshan.cache.{DCacheWordIO, DCacheLineIO, MemoryOpConstants}
-import xiangshan.mem._
-import xiangshan.backend.rob.RobPtr
-
-
-// Data module define
-// These data modules are like SyncDataModuleTemplate, but support cam-like ops
-class SQAddrModule(dataWidth: Int, numEntries: Int, numRead: Int, numWrite: Int, numForward: Int)(implicit p: Parameters) extends XSModule with HasDCacheParameters {
-  val io = IO(new Bundle {
-    // sync read
-    val raddr = Input(Vec(numRead, UInt(log2Up(numEntries).W)))
-    val rdata = Output(Vec(numRead, UInt(dataWidth.W))) // rdata: store addr
-    val rlineflag = Output(Vec(numRead, Bool())) // rdata: line op flag
-    // write
-    val wen   = Input(Vec(numWrite, Bool()))
-    val waddr = Input(Vec(numWrite, UInt(log2Up(numEntries).W)))
-    val wdata = Input(Vec(numWrite, UInt(dataWidth.W))) // wdata: store addr
-    val wmask = Input(Vec(numWrite, UInt((VLEN/8).W)))
-    val wlineflag = Input(Vec(numWrite, Bool())) // wdata: line op flag
-    // forward addr cam
-    val forwardMdata = Input(Vec(numForward, UInt(dataWidth.W))) // addr
-    val forwardDataMask = Input(Vec(numForward, UInt((VLEN/8).W))) // forward mask
-    val forwardMmask = Output(Vec(numForward, Vec(numEntries, Bool()))) // cam result mask
-    // debug
-    val debug_data = Output(Vec(numEntries, UInt(dataWidth.W)))
-  })
-
-  val data = Reg(Vec(numEntries, UInt(dataWidth.W)))
-  val mask = Reg(Vec(numEntries, UInt((VLEN/8).W)))
-  val lineflag = Reg(Vec(numEntries, Bool())) // cache line match flag
-  // if lineflag == true, this address points to a whole cacheline
-  io.debug_data := data
-
-  // read ports
-  for (i <- 0 until numRead) {
-    io.rdata(i) := data(GatedRegNext(io.raddr(i)))
-    io.rlineflag(i) := lineflag(GatedRegNext(io.raddr(i)))
-  }
-
-  // below is the write ports (with priorities)
-  for (i <- 0 until numWrite) {
-    when (io.wen(i)) {
-      data(io.waddr(i)) := io.wdata(i)
-      mask(io.waddr(i)) := io.wmask(i)
-      lineflag(io.waddr(i)) := io.wlineflag(i)
-    }
-  }
-
-  // content addressed match
-  for (i <- 0 until numForward) {
-    for (j <- 0 until numEntries) {
-      // io.forwardMmask(i)(j) := io.forwardMdata(i)(dataWidth-1, 3) === data(j)(dataWidth-1, 3)
-      val linehit = io.forwardMdata(i)(dataWidth-1, DCacheLineOffset) === data(j)(dataWidth-1, DCacheLineOffset)
-      val hit128bit = (io.forwardMdata(i)(DCacheLineOffset-1, DCacheVWordOffset) === data(j)(DCacheLineOffset-1, DCacheVWordOffset)) &&
-                    (!StoreQueueForwardWithMask.B || (mask(j) & io.forwardDataMask(i)).orR)
-      io.forwardMmask(i)(j) := linehit && (hit128bit || lineflag(j))
-    }
-  }
-
-  // DataModuleTemplate should not be used when there're any write conflicts
-  for (i <- 0 until numWrite) {
-    for (j <- i+1 until numWrite) {
-      assert(!(io.wen(i) && io.wen(j) && io.waddr(i) === io.waddr(j)))
-    }
-  }
-}
-
-class SQData8Entry(implicit p: Parameters) extends XSBundle {
-  val valid = Bool() // this byte is valid
-  val data = UInt((XLEN/8).W)
-}
-
-class SQData8Module(numEntries: Int, numRead: Int, numWrite: Int, numForward: Int)(implicit p: Parameters) extends XSModule
-  with HasDCacheParameters
-  with HasCircularQueuePtrHelper
-{
-  val io = IO(new Bundle() {
-    // sync read port
-    val raddr = Vec(numRead, Input(UInt(log2Up(numEntries).W)))
-    val rdata = Vec(numRead, Output(new SQData8Entry))
-    // data write port
-    val data = new Bundle() {
-      val wen   = Vec(numWrite, Input(Bool()))
-      val waddr = Vec(numWrite, Input(UInt(log2Up(numEntries).W)))
-      val wdata = Vec(numWrite, Input(UInt((XLEN/8).W)))
-    }
-    // mask (data valid) write port
-    val mask = new Bundle() {
-      val wen   = Vec(numWrite, Input(Bool()))
-      val waddr = Vec(numWrite, Input(UInt(log2Up(numEntries).W)))
-      val wdata = Vec(numWrite, Input(Bool()))
-    }
-
-    // st-ld forward addr cam result input, used to select forward data
-    val needForward = Input(Vec(numForward, Vec(2, UInt(numEntries.W))))
-    // forward result valid bit generated in current cycle
-    val forwardValidFast = Vec(numForward, Output(Bool()))
-    // forward result generated in the next cycle
-    val forwardValid = Vec(numForward, Output(Bool())) // forwardValid = RegNext(forwardValidFast)
-    val forwardData = Vec(numForward, Output(UInt(8.W)))
-  })
-
-  io := DontCare
-
-  val data = Reg(Vec(numEntries, new SQData8Entry))
-
-  require(isPow2(StoreQueueNWriteBanks))
-  require(StoreQueueNWriteBanks > 1)
-  def get_bank(in: UInt): UInt = in(log2Up(StoreQueueNWriteBanks) -1, 0)
-  def get_bank_index(in: UInt): UInt = in >> log2Up(StoreQueueNWriteBanks)
-  def get_vec_index(index: Int, bank: Int): Int = {
-    (index << log2Up(StoreQueueNWriteBanks)) + bank
-  }
-
-  // writeback to sq
-  // store queue data write takes 2 cycles
-  // (0 until numWrite).map(i => {
-  //   when(RegNext(io.data.wen(i))){
-  //     data(RegNext(io.data.waddr(i))).data := RegNext(io.data.wdata(i))
-  //   }
-  // })
-  (0 until numWrite).map(i => {
-     val s0_wenVec = Wire(Vec(StoreQueueNWriteBanks, Bool())) 
-    for(bank <- 0 until StoreQueueNWriteBanks) {
-      s0_wenVec(bank) := io.data.wen(i) && get_bank(io.data.waddr(i)) === bank.U
-    }
-   val s1_wenVec = GatedValidRegNext(s0_wenVec)
-    (0 until StoreQueueNWriteBanks).map(bank => {
-      val s0_wen = s0_wenVec(bank)
-      val s1_wen = s1_wenVec(bank)
-      val s1_wdata = RegEnable(io.data.wdata(i), s0_wen)
-      val s1_waddr = RegEnable(get_bank_index(io.data.waddr(i)), s0_wen)
-      val numRegsPerBank = StoreQueueSize / StoreQueueNWriteBanks
-      (0 until numRegsPerBank).map(index => {
-        when(s1_wen && s1_waddr === index.U){
-          data(get_vec_index(index, bank)).data := s1_wdata
-        }
-      })
-      s0_wen.suggestName("data_s0_wen_" + i +"_bank_" + bank)
-      s1_wen.suggestName("data_s1_wen_" + i +"_bank_" + bank)
-      s1_wdata.suggestName("data_s1_wdata_" + i +"_bank_" + bank)
-      s1_waddr.suggestName("data_s1_waddr_" + i +"_bank_" + bank)
-    })
-  })
-
-  // (0 until numWrite).map(i => {
-  //   when(RegNext(io.mask.wen(i))){
-  //     data(RegNext(io.mask.waddr(i))).valid := RegNext(io.mask.wdata(i))
-  //   }
-  // })
-  (0 until numWrite).map(i => {
-    val s0_wenVec = Wire(Vec(StoreQueueNWriteBanks, Bool())) 
-    for(bank <- 0 until StoreQueueNWriteBanks) {
-      s0_wenVec(bank) := io.mask.wen(i) && get_bank(io.mask.waddr(i)) === bank.U
-    }
-    val s1_wenVec = GatedValidRegNext(s0_wenVec)
-
-    (0 until StoreQueueNWriteBanks).map(bank => {
-      // val s0_wen = io.mask.wen(i) && get_bank(io.mask.waddr(i)) === bank.U
-      // val s1_wen = RegNext(s0_wen)
-      val s0_wen = s0_wenVec(bank)
-      val s1_wen = s1_wenVec(bank)
-      val s1_wdata = RegEnable(io.mask.wdata(i), s0_wen)
-      val s1_waddr = RegEnable(get_bank_index(io.mask.waddr(i)), s0_wen)
-      val numRegsPerBank = StoreQueueSize / StoreQueueNWriteBanks
-      (0 until numRegsPerBank).map(index => {
-        when(s1_wen && s1_waddr === index.U){
-          data(get_vec_index(index, bank)).valid := s1_wdata
-        }
-      })
-      s0_wen.suggestName("mask_s0_wen_" + i +"_bank_" + bank)
-      s1_wen.suggestName("mask_s1_wen_" + i +"_bank_" + bank)
-      s1_wdata.suggestName("mask_s1_wdata_" + i +"_bank_" + bank)
-      s1_waddr.suggestName("mask_s1_waddr_" + i +"_bank_" + bank)
-    })
-  })
-
-  // destorequeue read data
-  (0 until numRead).map(i => {
-      io.rdata(i) := data(RegNext(io.raddr(i)))
-  })
-
-  // DataModuleTemplate should not be used when there're any write conflicts
-  for (i <- 0 until numWrite) {
-    for (j <- i+1 until numWrite) {
-      assert(!(io.data.wen(i) && io.data.wen(j) && io.data.waddr(i) === io.data.waddr(j)))
-    }
-  }
-  for (i <- 0 until numWrite) {
-    for (j <- i+1 until numWrite) {
-      assert(!(io.mask.wen(i) && io.mask.wen(j) && io.mask.waddr(i) === io.mask.waddr(j)))
-    }
-  }
-
-  // forwarding
-  // Compare ringBufferTail (deqPtr) and forward.sqIdx, we have two cases:
-  // (1) if they have the same flag, we need to check range(tail, sqIdx)
-  // (2) if they have different flags, we need to check range(tail, VirtualLoadQueueSize) and range(0, sqIdx)
-  // Forward1: Mux(same_flag, range(tail, sqIdx), range(tail, VirtualLoadQueueSize))
-  // Forward2: Mux(same_flag, 0.U,                   range(0, sqIdx)    )
-  // i.e. forward1 is the target entries with the same flag bits and forward2 otherwise
-
-  // entry with larger index should have higher priority since it's data is younger
-
-  (0 until numForward).map(i => {
-    // parallel fwd logic
-    val matchResultVec = Wire(Vec(numEntries * 2, new FwdEntry))
-
-    def parallelFwd(xs: Seq[Data]): Data = {
-      ParallelOperation(xs, (a: Data, b: Data) => {
-        val l = a.asTypeOf(new FwdEntry)
-        val r = b.asTypeOf(new FwdEntry)
-        val res = Wire(new FwdEntry)
-        res.validFast := l.validFast || r.validFast
-        res.valid := l.valid || r.valid
-        // res.valid := RegNext(res.validFast)
-        res.data := Mux(r.valid, r.data, l.data)
-        res
-      })
-    }
-    
-    for (j <- 0 until numEntries) {
-      val needCheck0 = io.needForward(i)(0)(j)
-      val needCheck1 = io.needForward(i)(1)(j)
-      val needCheck0Reg = RegNext(needCheck0)
-      val needCheck1Reg = RegNext(needCheck1)
-
-      matchResultVec(j).validFast := needCheck0 && data(j).valid
-      matchResultVec(j).valid := needCheck0Reg && data(j).valid
-      matchResultVec(j).data := data(j).data
-      matchResultVec(numEntries + j).validFast := needCheck1 && data(j).valid
-      matchResultVec(numEntries + j).valid := needCheck1Reg && data(j).valid
-      matchResultVec(numEntries + j).data := data(j).data
-    }
-
-    val parallelFwdResult = parallelFwd(matchResultVec).asTypeOf(new FwdEntry)
-
-    // validFast is generated the same cycle with query
-    io.forwardValidFast(i) := parallelFwdResult.validFast
-    // valid is generated 1 cycle after query request
-    io.forwardValid(i) := parallelFwdResult.valid
-    // data is generated 1 cycle after query request
-    io.forwardData(i) := parallelFwdResult.data
-  })
-}
-
-class SQDataEntry(implicit p: Parameters) extends XSBundle {
-  val mask = UInt((VLEN/8).W)
-  val data = UInt(VLEN.W)
-}
-
-// SQDataModule is a wrapper of SQData8Modules
-class SQDataModule(numEntries: Int, numRead: Int, numWrite: Int, numForward: Int)(implicit p: Parameters) extends XSModule with HasDCacheParameters with HasCircularQueuePtrHelper {
-  val io = IO(new Bundle() {
-    // sync read port
-    val raddr = Vec(numRead,  Input(UInt(log2Up(numEntries).W)))
-    val rdata = Vec(numRead,  Output(new SQDataEntry))
-    // data write port
-    val data = new Bundle() {
-      val wen   = Vec(numWrite, Input(Bool()))
-      val waddr = Vec(numWrite, Input(UInt(log2Up(numEntries).W)))
-      val wdata = Vec(numWrite, Input(UInt(VLEN.W)))
-    }
-    // mask (data valid) write port
-    val mask = new Bundle() {
-      val wen   = Vec(numWrite, Input(Bool()))
-      val waddr = Vec(numWrite, Input(UInt(log2Up(numEntries).W)))
-      val wdata = Vec(numWrite, Input(UInt((VLEN/8).W)))
-    }
-
-    // st-ld forward addr cam result input, used to select forward data
-    val needForward = Input(Vec(numForward, Vec(2, UInt(numEntries.W))))
-    // forward result valid bit generated in current cycle
-    val forwardMaskFast = Vec(numForward, Output(Vec((VLEN/8), Bool())))
-    // forward result generated in the next cycle
-    val forwardMask = Vec(numForward, Output(Vec((VLEN/8), Bool()))) // forwardMask = RegNext(forwardMaskFast)
-    val forwardData = Vec(numForward, Output(Vec((VLEN/8), UInt(8.W))))
-  })
-
-  val data16 = Seq.fill(16)(Module(new SQData8Module(numEntries, numRead, numWrite, numForward)))
-
-  // writeback to lq/sq
-  for (i <- 0 until numWrite) {
-    // write to data16
-    for (j <- 0 until 16) {
-      data16(j).io.mask.waddr(i) := io.mask.waddr(i)
-      data16(j).io.mask.wdata(i) := io.mask.wdata(i)(j)
-      data16(j).io.mask.wen(i)   := io.mask.wen(i)
-      data16(j).io.data.waddr(i) := io.data.waddr(i)
-      data16(j).io.data.wdata(i) := io.data.wdata(i)(8*(j+1)-1, 8*j)
-      data16(j).io.data.wen(i)   := io.data.wen(i)
-    }
-  }
-
-  // destorequeue read data
-  for (i <- 0 until numRead) {
-    for (j <- 0 until 16) {
-      data16(j).io.raddr(i) := io.raddr(i)
-    }
-    io.rdata(i).mask := VecInit((0 until 16).map(j => data16(j).io.rdata(i).valid)).asUInt
-    io.rdata(i).data := VecInit((0 until 16).map(j => data16(j).io.rdata(i).data)).asUInt
-  }
-
-  // DataModuleTemplate should not be used when there're any write conflicts
-  for (i <- 0 until numWrite) {
-    for (j <- i+1 until numWrite) {
-      assert(!(io.data.wen(i) && io.data.wen(j) && io.data.waddr(i) === io.data.waddr(j)))
-    }
-  }
-  for (i <- 0 until numWrite) {
-    for (j <- i+1 until numWrite) {
-      assert(!(io.mask.wen(i) && io.mask.wen(j) && io.mask.waddr(i) === io.mask.waddr(j)))
-    }
-  }
-
-  (0 until numForward).map(i => {
-    // parallel fwd logic
-    for (j <- 0 until 16) {
-      data16(j).io.needForward(i) <> io.needForward(i)
-      io.forwardMaskFast(i) := VecInit((0 until 16).map(j => data16(j).io.forwardValidFast(i)))
-      io.forwardMask(i) := VecInit((0 until 16).map(j => data16(j).io.forwardValid(i)))
-      io.forwardData(i) := VecInit((0 until 16).map(j => data16(j).io.forwardData(i)))
-    }
-  })
-}
diff --git a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
index 551a2c88ad5..5fb2f40f2b1 100644
--- a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
@@ -26,7 +26,7 @@ import xiangshan.ExceptionNO._
 import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.backend.fu.FuConfig.MouCfg
 import xiangshan.backend.fu.FuType
-import xiangshan.backend.Bundles.{DynInst, ExuInput, ExuOutput}
+import xiangshan.backend.Bundles.{DynInst, ExceptionInfo, ExuInput, ExuOutput, NewExuOutput}
 import xiangshan.backend.fu.NewCSR.TriggerUtil
 import xiangshan.backend.fu.util.SdtrigExt
 import xiangshan.backend.exu.ExeUnitParams
@@ -46,18 +46,14 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
     val in            = Flipped(Decoupled(new ExuInput(param, hasCopySrc = true)))
     val storeDataIn   = Flipped(Vec(StdCnt, Valid(new ExuInput(moudParam))))
     // AtomicsUnit re-uses lda port to write back
-    val out           = Decoupled(new ExuOutput(ldaParams.head))
+    val out           = new NewExuOutput(ldaParams.head)
     val dcache        = new AtomicWordIO
     val dtlb          = new TlbRequestIO(2)
     val pmpResp       = Flipped(new PMPRespBundle())
     val flush_sbuffer = new SbufferFlushBundle
     val feedbackSlow  = ValidIO(new RSFeedback)
     val redirect      = Flipped(ValidIO(new Redirect))
-    val exceptionInfo = ValidIO(new Bundle {
-      val vaddr = UInt(XLEN.W)
-      val gpaddr = UInt(XLEN.W)
-      val isForVSnonLeafPTE = Bool()
-    })
+    val exceptionInfo = ValidIO(new MemExceptionInfo)
     val csrCtrl       = Flipped(new CustomCSRCtrlIO)
   })
   io.in.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.in.valid, clock, reset))
@@ -413,7 +409,7 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   }
 
   when (state === s_finish) {
-    when (io.out.fire) {
+    when (io.out.toRob.fire) {
       when (LSUOpType.isAMOCASQ(uop.fuOpType)) {
         // enter `s_finish2` to write the 2nd uop back
         state := s_finish2
@@ -430,19 +426,19 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   }
 
   when (state === s_finish2) {
-    when (io.out.fire) {
+    when (io.out.toRob.fire) {
       state := s_extra_wb2
     }
   }
 
   when (state === s_extra_wb2) {
-    when (io.out.fire) {
+    when (io.out.toRob.fire) {
       state := s_extra_wb
     }
   }
 
   when (state === s_extra_wb) {
-    when (io.out.fire) {
+    when (io.out.toRob.fire) {
       resetFSM()
     }
   }
@@ -463,10 +459,17 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   /**
     * IO assignment
     */
-  io.exceptionInfo.valid := atom_override_xtval
-  io.exceptionInfo.bits.vaddr := vaddr
-  io.exceptionInfo.bits.gpaddr := gpaddr
+  io.exceptionInfo.valid                  := atom_override_xtval
+  io.exceptionInfo.bits.robIdx            := uop.robIdx
+  io.exceptionInfo.bits.vaddr             := vaddr
+  io.exceptionInfo.bits.gpaddr            := gpaddr
   io.exceptionInfo.bits.isForVSnonLeafPTE := isForVSnonLeafPTE
+  io.exceptionInfo.bits.exceptionVec      := exceptionVec
+  io.exceptionInfo.bits.vaNeedExt         := false.B
+  io.exceptionInfo.bits.isHyper           := false.B
+  io.exceptionInfo.bits.uopIdx            := 0.U.asTypeOf(io.exceptionInfo.bits.uopIdx)
+  io.exceptionInfo.bits.vl                := 0.U.asTypeOf(io.exceptionInfo.bits.vl)
+  io.exceptionInfo.bits.vstart            := 0.U.asTypeOf(io.exceptionInfo.bits.vstart)
 
   // Send TLB feedback to store issue queue
   // we send feedback right after we receives request
@@ -507,20 +510,30 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
 
   val state_sta_wb = state === s_finish || state === s_finish2
   val state_std_wb = state === s_extra_wb || state === s_extra_wb2
-  io.out.valid := out_valid && Mux(state === s_finish2, pdest2Valid, pdest1Valid)
+  io.out.toRob.valid := out_valid && Mux(state === s_finish2, pdest2Valid, pdest1Valid)
   assert(!out_valid || state_sta_wb || state_std_wb, "out_valid reg error\n")
-  io.out.bits := 0.U.asTypeOf(io.out.bits)
-  io.out.bits.data := VecInit(Seq.fill(param.wbPathNum)(Mux(state === s_finish2, resp_data >> XLEN, resp_data)))
-  io.out.bits.pdest := Mux(state === s_finish2, pdest2, pdest1)
-  io.out.bits.robIdx := uop.robIdx
-  io.out.bits.intWen.foreach(_ := state_sta_wb)
-  io.out.bits.redirect.foreach(_ := 0.U.asTypeOf(Valid(new Redirect)))
-  io.out.bits.exceptionVec.foreach(_ := exceptionVec)
-  io.out.bits.trigger.foreach(_ := trigger)
-  io.out.bits.isFromLoadUnit.foreach(_ := false.B) // atomics are not issued from LoadUnit
-  io.out.bits.isRVC.foreach(_ := uop.isRVC)
-  io.out.bits.debug.isMMIO := is_mmio
-  io.out.bits.debug.paddr := paddr
+  io.out.toIntRf.foreach{case port =>
+    port.valid := state_sta_wb
+    port.bits := Mux(state === s_finish2, resp_data >> XLEN, resp_data)
+  }
+  io.out.toFpRf.foreach{case port =>
+    port.valid := false.B // amo will never write fp
+    port.bits := Mux(state === s_finish2, resp_data >> XLEN, resp_data)
+  }
+  io.out.pdest := Mux(state === s_finish2, pdest2, pdest1)
+  io.out.toRob.bits.robIdx := uop.robIdx
+  io.out.toRob.bits.exceptionVec.foreach(_ := exceptionVec)
+  io.out.toRob.bits.trigger.foreach(_ := trigger)
+  io.out.toRob.bits.isRVC.foreach(_ := uop.isRVC)
+  io.out.toRob.bits.lqIdx.foreach(_ := uop.lqIdx)
+  io.out.isFromLoadUnit.foreach(_ := false.B) // atomics are not issued from LoadUnit
+  io.out.debug.isNCIO := false.B
+  io.out.debug.isPerfCnt := DontCare
+  io.out.debug.isMMIO := is_mmio
+  io.out.debug.paddr := paddr
+  io.out.debug.vaddr := vaddr
+  io.out.debug_seqNum.foreach(_ := uop.debug_seqNum)
+  io.out.perfDebugInfo.foreach(_ := uop.perfDebugInfo)
 
   io.dcache.req.valid := Mux(
     io.dcache.req.bits.cmd === M_XLR,
@@ -587,7 +600,7 @@ class AtomicsUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   if (env.EnableDifftest || env.AlwaysBasicDiff) {
     val difftest = DifftestModule(new DiffLrScEvent)
     difftest.coreid := io.hartId
-    difftest.valid := io.out.fire && state === s_finish && isSc
+    difftest.valid := io.out.toRob.fire && state === s_finish && isSc
     difftest.success := success
   }
 }
diff --git a/src/main/scala/xiangshan/mem/pipeline/Bundles.scala b/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
new file mode 100644
index 00000000000..9f81440786d
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
@@ -0,0 +1,211 @@
+/***************************************************************************************
+* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2020-2021 Peng Cheng Laboratory
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http://license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
+
+package xiangshan.mem
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utils._
+import xiangshan._
+import xiangshan.backend.fu.FuConfig.LduCfg
+import xiangshan.backend.fu.PMPRespBundle
+import xiangshan.backend.Bundles.DynInst
+import xiangshan.cache._
+import xiangshan.cache.mmu._
+import xiangshan.mem.LoadStage._
+
+sealed trait HasLoadPipeBundleParam {
+  def hasPAddr: Boolean = false
+  def hasAddrTrans: Boolean = false
+  def hasNoQuery: Boolean = false
+  def hasPAddrChecked: Boolean = false
+  def hasNC: Boolean = false
+  def hasMMIO: Boolean = false
+  def replayToLRQ: Boolean = false
+  def replayFromLRQ: Boolean = false
+  def hasVector: Boolean = false
+  def hasS2PreProcess: Boolean = false
+  def hasS3PreProcess: Boolean = false
+  def hasS4PreProcess: Boolean = false
+  def hasUnalignHandling: Boolean = false
+
+  def replayFromToLRQ = replayToLRQ || replayFromLRQ
+}
+case class DefaultLoadPipeBundleParam() extends HasLoadPipeBundleParam
+
+class LoadPipeBundle(
+  param: HasLoadPipeBundleParam = DefaultLoadPipeBundleParam()
+)(
+  implicit p: Parameters
+) extends XSBundle
+  with HasLoadPipeBundleParam
+  with HasTlbConst
+  with HasDCacheParameters
+  with HasVLSUParameters
+  with HasMemBlockParameters {
+  // basic info
+  val entrance = LoadEntrance()
+  val accessType = LoadAccessType()
+  val uop = new DynInst
+  val vaddr = UInt(VAddrBits.W)
+  val fullva = UInt(XLEN.W)
+  val size = UInt(MemorySize.Size.width.W)
+  val mask = UInt((VLEN/8).W)
+  val paddr = Option.when(param.hasAddrTrans || param.hasPAddr)(UInt(PAddrBits.W))
+  val noQuery = Option.when(param.hasNoQuery)(Bool())
+
+  // unalign handling
+  val align = Option.when(param.hasUnalignHandling)(Bool())
+  val unalignHead = Option.when(param.hasUnalignHandling)(Bool())
+  val readWholeBank = Option.when(param.hasUnalignHandling)(Bool()) // TODO: remove this
+
+  // MMU & exception handling
+  val tlbAccessResult = Option.when(param.hasAddrTrans)(TlbAccessResult())
+  val tlbException = Option.when(param.hasAddrTrans)(new TlbRespExcp)
+  val pbmt = Option.when(param.hasAddrTrans)(Pbmt())
+  val gpaddr = Option.when(param.hasAddrTrans)(UInt(XLEN.W))
+  val isForVSnonLeafPTE = Option.when(param.hasAddrTrans)(Bool())
+
+  val pmp = Option.when(param.hasPAddrChecked)(new PMPRespBundle)
+  val nc = Option.when(param.hasNC)(Bool())
+  val mmio = Option.when(param.hasMMIO)(Bool())
+
+  // replay
+  val mshrId = Option.when(param.replayFromToLRQ)(UInt(log2Up(cfg.nMissEntries).W)) // valid when `handledByMSHR` is HIGH
+  val replayQueueIdx = Option.when(param.replayFromToLRQ)(UInt(log2Up(LoadQueueReplaySize+1).W)) // valid when `entrance` is replay
+  val cause = Option.when(param.replayFromToLRQ)(Vec(LoadReplayCauses.allCauses, Bool()))
+  val fastReplayNukeFirst = Option.when(param.hasS2PreProcess)(Bool())// When stld_nuke and storeset hit occur simultaneously, stld_nuke should be handled first.
+
+  val handledByMSHR = Option.when(param.replayToLRQ)(Bool())
+  val dataInvalidSqIdx = Option.when(param.replayToLRQ)(new SqPtr)
+  val addrInvalidSqIdx = Option.when(param.replayToLRQ)(new SqPtr)
+  val tlbId = Option.when(param.replayToLRQ)(UInt(log2Up(loadfiltersize).W))
+  val tlbFull = Option.when(param.replayToLRQ)(Bool())
+
+  val forwardDChannel = Option.when(param.replayFromLRQ)(Bool())
+  val uncacheReplay = Option.when(param.replayFromLRQ)(Bool())
+  val ncReplay = Option.when(param.replayFromLRQ)(Bool())
+  def isNCReplay(): Bool = uncacheReplay.getOrElse(false.B) && ncReplay.getOrElse(false.B)
+  def isMMIOReplay(): Bool = uncacheReplay.getOrElse(false.B) && !ncReplay.getOrElse(false.B)
+  def isUncacheReplay(): Bool = uncacheReplay.getOrElse(false.B)
+
+  // vector
+  val elemIdx = Option.when(param.hasVector)(UInt(elemIdxBits.W))
+  val mbIndex = Option.when(param.hasVector)(UInt(vlmBindexBits.W))
+  val regOffset = Option.when(param.hasVector)(UInt(vOffsetBits.W))
+  val elemIdxInsideVd = Option.when(param.hasVector)(UInt(elemIdxBits.W))
+  val vecBaseVaddr = Option.when(param.hasVector)(UInt(VAddrBits.W))
+  val vecVaddrOffset = Option.when(param.hasVector)(UInt(VAddrBits.W)) // only used in s1 & s2, to generate vstart
+  val vecTriggerMask = Option.when(param.hasVector)(UInt((VLEN/8).W))
+
+  // To optimize timing, part of the combinational logic is precomputed in advance
+  // S1 -> S2
+  val shouldFastReplay = Option.when(param.hasS2PreProcess)(Bool())
+  // S2 -> S3
+  val troubleMaker = Option.when(param.hasS3PreProcess)(Bool())
+  val matchInvalid = Option.when(param.hasS3PreProcess)(Bool())
+  val shouldWakeup = Option.when(param.hasS3PreProcess)(Bool())
+  val shouldWriteback = Option.when(param.hasS3PreProcess)(Bool())
+  // S3 -> S4
+  val hasException = Option.when(param.hasS4PreProcess)(Bool())
+  val headAlwaysWriteback = Option.when(param.hasS4PreProcess)(Bool())
+  val writebackDependOnTail = Option.when(param.hasS4PreProcess)(Bool())
+  val shouldRarViolation = Option.when(param.hasS4PreProcess)(Bool())
+
+  // debug info and top-down
+  // TODO: use Option
+  val hasROBEntry = Bool()
+  val missDbUpdated = Bool()
+  val occupySource = LoadEntrance()
+
+  def offset(): UInt = vaddr.take(DCacheLineOffset)
+  def bankOffset(): UInt = vaddr.take(DCacheVWordOffset)
+  def DontCarePAddr(): Unit = {
+    paddr.get := DontCare
+  }
+  def DontCareUnalign(): Unit = {
+    align.get := DontCare
+    unalignHead.get := DontCare
+    readWholeBank.get := DontCare
+  }
+  def DontCareReplayFromLRQFields(): Unit = {
+    mshrId.get := DontCare
+    replayQueueIdx.get := DontCare
+    cause.get := 0.U.asTypeOf(cause.get)
+    forwardDChannel.get := false.B
+    uncacheReplay.get := false.B
+    ncReplay.get := false.B
+  }
+  def DontCareVectorFields(): Unit = {
+    elemIdx.get := 0.U
+    mbIndex.get := 0.U
+    regOffset.get := 0.U
+    elemIdxInsideVd.get := 0.U
+    vecBaseVaddr.get := 0.U
+    vecVaddrOffset.get := 0.U
+    vecTriggerMask.get := 0.U
+  }
+  def isFirstIssue(): Bool = {
+    LoadEntrance.isScalarIssue(entrance) || LoadEntrance.isVectorIssue(entrance)
+  }
+}
+
+case class LoadReplayIOParam(
+  override val replayFromLRQ: Boolean = true,
+  override val hasVector: Boolean = true
+) extends HasLoadPipeBundleParam
+
+case class FastReplayIOParam(
+  override val hasPAddr: Boolean = true,
+  override val hasAddrTrans: Boolean = true,
+  override val replayFromLRQ: Boolean = true,
+  override val hasVector: Boolean = true
+) extends HasLoadPipeBundleParam
+
+case class VectorLoadInParam(
+  override val hasVector: Boolean = true
+) extends HasLoadPipeBundleParam
+
+case class LoadStageIOParam()(
+  implicit val s: LoadStage
+) extends HasLoadPipeBundleParam with OnLoadStage {
+  override val hasPAddr: Boolean = true
+  override val hasAddrTrans: Boolean = afterS1
+  override val hasNoQuery: Boolean = isS0
+  override val hasPAddrChecked: Boolean = afterS2
+  override val hasNC: Boolean = afterS2
+  override val hasMMIO: Boolean = afterS2
+  override val replayToLRQ: Boolean = afterS2
+  override val replayFromLRQ: Boolean = true
+  override val hasVector: Boolean = true
+  override val hasS2PreProcess: Boolean = afterS1
+  override val hasS3PreProcess: Boolean = afterS2
+  override val hasS4PreProcess: Boolean = afterS3
+  override val hasUnalignHandling: Boolean = true
+}
+
+class LoadReplayIO(implicit p: Parameters)
+  extends LoadPipeBundle(LoadReplayIOParam())
+
+class FastReplayIO(implicit p: Parameters)
+  extends LoadPipeBundle(FastReplayIOParam())
+
+class VectorLoadIn(implicit p: Parameters)
+  extends LoadPipeBundle(VectorLoadInParam())
+
+class LoadStageIO(implicit p: Parameters, implicit val s: LoadStage)
+  extends LoadPipeBundle(LoadStageIOParam())
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala b/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala
new file mode 100644
index 00000000000..e69de29bb2d
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index d6d49fa09d9..37db1b14eaa 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -75,1813 +75,7 @@ class LoadToLsqReplayIO(implicit p: Parameters) extends XSBundle
   def raw_nack      = cause(LoadReplayCauses.C_RAW)
   def misalign_nack = cause(LoadReplayCauses.C_MF)
   def nuke          = cause(LoadReplayCauses.C_NK)
+  def mmioOrNc      = cause(LoadReplayCauses.C_UNCACHE)
+  def storeMultiFwd = cause(LoadReplayCauses.C_SMF)
   def need_rep      = cause.asUInt.orR
 }
-
-
-class LoadToLsqIO(implicit p: Parameters) extends XSBundle {
-  // ldu -> lsq UncacheBuffer
-  val ldin            = DecoupledIO(new LqWriteBundle)
-  // uncache-mmio -> ldu
-  val uncache         = Flipped(DecoupledIO(new MemExuOutput))
-  val ld_raw_data     = Input(new LoadDataFromLQBundle)
-  // uncache-nc -> ldu
-  val nc_ldin = Flipped(DecoupledIO(new LsPipelineBundle))
-  // storequeue -> ldu
-  val forward         = new PipeLoadForwardQueryIO
-  // ldu -> lsq LQRAW
-  val stld_nuke_query = new LoadNukeQueryIO
-  // ldu -> lsq LQRAR
-  val ldld_nuke_query = new LoadNukeQueryIO
-  // lq -> ldu for misalign
-  val lqDeqPtr = Input(new LqPtr)
-}
-
-class LoadToLoadIO(implicit p: Parameters) extends XSBundle {
-  val valid      = Bool()
-  val data       = UInt(XLEN.W) // load to load fast path is limited to ld (64 bit) used as vaddr src1 only
-  val dly_ld_err = Bool()
-}
-
-class LoadUnitTriggerIO(implicit p: Parameters) extends XSBundle {
-  val tdata2      = Input(UInt(64.W))
-  val matchType   = Input(UInt(2.W))
-  val tEnable     = Input(Bool()) // timing is calculated before this
-  val addrHit     = Output(Bool())
-}
-
-class LoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModule
-  with HasLoadHelper
-  with HasPerfEvents
-  with HasDCacheParameters
-  with HasCircularQueuePtrHelper
-  with HasVLSUParameters
-  with SdtrigExt
-{
-  val io = IO(new Bundle() {
-    // control
-    val redirect      = Flipped(ValidIO(new Redirect))
-    val csrCtrl       = Flipped(new CustomCSRCtrlIO)
-
-    // int issue path
-    val ldin          = Flipped(Decoupled(new ExuInput(param, hasCopySrc = true)))
-    val ldout         = Decoupled(new ExuOutput(param))
-
-    // vec issue path
-    val vecldin = Flipped(Decoupled(new VecPipeBundle))
-    val vecldout = Decoupled(new VecPipelineFeedbackIO(isVStore = false))
-
-    // misalignBuffer issue path
-    val misalign_ldin = Flipped(Decoupled(new LsPipelineBundle))
-    val misalign_ldout = Valid(new LqWriteBundle)
-
-    // data path
-    val tlb           = new TlbRequestIO(2)
-    val pmp           = Flipped(new PMPRespBundle()) // arrive same to tlb now
-    val dcache        = new DCacheLoadIO
-    val sbuffer       = new LoadForwardQueryIO
-    val ubuffer       = new LoadForwardQueryIO
-    val lsq           = new LoadToLsqIO
-    val tl_d_channel  = Input(new DcacheToLduForwardIO)
-    val forward_mshr  = Flipped(new LduToMissqueueForwardIO)
-    val tlb_hint      = Flipped(new TlbHintReq)
-
-    // trigger
-    val fromCsrTrigger = Input(new CsrTriggerBundle)
-
-    // prefetch
-    val prefetch_train            = ValidIO(new LsPrefetchTrainBundle()) // provide prefetch info
-
-    // speculative for gated control
-    val s1_prefetch_spec = Output(Bool())
-    val s2_prefetch_spec = Output(Bool())
-
-    val prefetch_req              = Flipped(ValidIO(new L1PrefetchReq)) // hardware prefetch to l1 cache req
-    val canAcceptLowConfPrefetch  = Output(Bool())
-    val canAcceptHighConfPrefetch = Output(Bool())
-
-    // ifetchPrefetch
-    val ifetchPrefetch = ValidIO(new SoftIfetchPrefetchBundle)
-
-    // rs feedback
-    val wakeup = ValidIO(new MemWakeUpBundle)
-    val feedback_slow = ValidIO(new RSFeedback) // stage 3
-    val ldCancel = Output(new LoadCancelIO()) // use to cancel the uops waked by this load, and cancel load
-
-    // schedule error query
-    val stld_nuke_query = Flipped(Vec(StorePipelineWidth, Valid(new StoreNukeQueryBundle)))
-
-    // queue-based replay
-    val replay       = Flipped(Decoupled(new LsPipelineBundle))
-
-    // Load fast replay path
-    val fast_rep_in  = Flipped(Decoupled(new LqWriteBundle))
-    val fast_rep_out = Decoupled(new LqWriteBundle)
-
-    // to misalign buffer
-    val misalign_enq = new MisalignBufferEnqIO
-    val misalign_allow_spec = Input(Bool())
-
-    // Load RAR rollback
-    val rollback = Valid(new Redirect)
-
-    // perf
-    val debug_ls         = Output(new DebugLsInfoBundle)
-    val lsTopdownInfo    = Output(new LsTopdownInfo)
-    val correctMissTrain = Input(Bool())
-  })
-
-
-  io.ldin.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.ldin.valid, clock, reset))
-  val s1_ready, s2_ready, s3_ready = WireInit(false.B)
-
-  // Pipeline
-  // --------------------------------------------------------------------------------
-  // stage 0
-  // --------------------------------------------------------------------------------
-  // generate addr, use addr to query DCache and DTLB
-  val s0_valid         = Wire(Bool())
-  val s0_mmio_select   = Wire(Bool())
-  val s0_nc_select     = Wire(Bool())
-  val s0_misalign_select= Wire(Bool())
-  val s0_kill          = Wire(Bool())
-  val s0_can_go        = s1_ready
-  val s0_fire          = s0_valid && s0_can_go
-  val s0_mmio_fire     = s0_mmio_select && s0_can_go
-  val s0_nc_fire       = s0_nc_select && s0_can_go
-  val s0_out           = Wire(new LqWriteBundle)
-  val s0_tlb_valid     = Wire(Bool())
-  val s0_tlb_hlv       = Wire(Bool())
-  val s0_tlb_hlvx      = Wire(Bool())
-  val s0_tlb_vaddr     = Wire(UInt(VAddrBits.W))
-  val s0_tlb_fullva    = Wire(UInt(XLEN.W))
-  val s0_dcache_vaddr  = Wire(UInt(VAddrBits.W))
-  val s0_is128bit      = Wire(Bool())
-  val s0_misalign_wakeup_fire = s0_misalign_select && s0_can_go &&
-    io.dcache.req.ready &&
-    io.misalign_ldin.bits.misalignNeedWakeUp
-
-  // flow source bundle
-  class FlowSource extends Bundle {
-    val vaddr         = UInt(VAddrBits.W)
-    val mask          = UInt((VLEN/8).W)
-    val uop           = new DynInst
-    val has_rob_entry = Bool()
-    val rep_carry     = new ReplayCarry(nWays)
-    val mshrid        = UInt(log2Up(cfg.nMissEntries).W)
-    val isFirstIssue  = Bool()
-    val repForTlbMiss = Bool()
-    val fast_rep      = Bool()
-    val ld_rep        = Bool()
-    val prf           = Bool()
-    val prf_rd        = Bool()
-    val prf_wr        = Bool()
-    val prf_i         = Bool()
-    val sched_idx     = UInt(log2Up(LoadQueueReplaySize+1).W)
-    // Record the issue port idx of load issue queue. This signal is used by load cancel.
-    val deqPortIdx    = UInt(log2Ceil(LoadPipelineWidth).W)
-    val frm_mabuf     = Bool()
-    // vec only
-    val isvec         = Bool()
-    val is128bit      = Bool()
-    val uop_unit_stride_fof = Bool()
-    val reg_offset    = UInt(vOffsetBits.W)
-    val vecActive     = Bool() // 1: vector active element or scala mem operation, 0: vector not active element
-    val is_first_ele  = Bool()
-    // val flowPtr       = new VlflowPtr
-    val usSecondInv   = Bool()
-    val mbIndex       = UInt(vlmBindexBits.W)
-    val elemIdx       = UInt(elemIdxBits.W)
-    val elemIdxInsideVd = UInt(elemIdxBits.W)
-    val alignedType   = UInt(alignTypeBits.W)
-    val vecBaseVaddr  = UInt(VAddrBits.W)
-    //for Svpbmt NC
-    val isnc          = Bool()
-    val paddr         = UInt(PAddrBits.W)
-    val data          = UInt((VLEN+1).W)
-  }
-  val s0_sel_src = Wire(new FlowSource)
-
-  // load flow select/gen
-  // src 0: misalignBuffer load (io.misalign_ldin)
-  // src 1: super load replayed by LSQ (cache miss replay) (io.replay)
-  // src 2: fast load replay (io.fast_rep_in)
-  // src 3: mmio (io.lsq.uncache)
-  // src 4: nc (io.lsq.nc_ldin)
-  // src 5: load replayed by LSQ (io.replay)
-  // src 6: hardware prefetch from prefetchor (high confidence) (io.prefetch)
-  // NOTE: Now vec/int loads are sent from same RS
-  //       A vec load will be splited into multiple uops,
-  //       so as long as one uop is issued,
-  //       the other uops should have higher priority
-  // src 7: vec read from RS (io.vecldin)
-  // src 8: int read / software prefetch first issue from RS (io.in)
-  // src 9: hardware prefetch from prefetchor (low confidence) (io.prefetch)
-  // priority: high to low
-  val s0_rep_stall           = io.ldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.ldin.bits.lqIdx.get) ||
-                               io.vecldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.vecldin.bits.uop.lqIdx)
-  private val SRC_NUM = 10
-  private val Seq(
-    mab_idx, super_rep_idx, fast_rep_idx, lsq_rep_idx, high_pf_idx,
-    vec_iss_idx, int_iss_idx, mmio_idx, nc_idx, low_pf_idx
-  ) = (0 until SRC_NUM).toSeq
-  // load flow source valid
-  val s0_src_valid_vec = WireInit(VecInit(Seq(
-    io.misalign_ldin.valid,
-    io.replay.valid && io.replay.bits.forward_tlDchannel,
-    io.fast_rep_in.valid,
-    io.replay.valid && !io.replay.bits.forward_tlDchannel && !s0_rep_stall,
-    io.prefetch_req.valid && io.prefetch_req.bits.confidence > 0.U,
-    io.vecldin.valid,
-    io.ldin.valid, // int flow first issue or software prefetch
-    io.lsq.uncache.valid,
-    io.lsq.nc_ldin.valid,
-    io.prefetch_req.valid, // lower confidence prefetch or lower prefetch-priority ldu
-  )))
-  // load flow source ready
-  val s0_src_ready_vec = Wire(Vec(SRC_NUM, Bool()))
-  s0_src_ready_vec(0) := true.B
-  for(i <- 1 until SRC_NUM){
-    s0_src_ready_vec(i) := !s0_src_valid_vec.take(i).reduce(_ || _)
-  }
-  // load flow source select (OH)
-  val s0_src_select_vec = WireInit(VecInit((0 until SRC_NUM).map{i => s0_src_valid_vec(i) && s0_src_ready_vec(i)}))
-  val s0_hw_prf_select = s0_src_select_vec(high_pf_idx) || s0_src_select_vec(low_pf_idx)
-
-  val s0_tlb_no_query = s0_hw_prf_select || s0_sel_src.prf_i ||
-    s0_src_select_vec(fast_rep_idx) || s0_src_select_vec(mmio_idx) ||
-    s0_src_select_vec(nc_idx)
-  s0_valid := !s0_kill && (s0_src_select_vec(nc_idx) || ((
-    s0_src_valid_vec(mab_idx) ||
-    s0_src_valid_vec(super_rep_idx) ||
-    s0_src_valid_vec(fast_rep_idx) ||
-    s0_src_valid_vec(lsq_rep_idx) ||
-    s0_src_valid_vec(high_pf_idx) ||
-    s0_src_valid_vec(vec_iss_idx) ||
-    s0_src_valid_vec(int_iss_idx) ||
-    s0_src_valid_vec(low_pf_idx)
-  ) && !s0_src_select_vec(mmio_idx) && io.dcache.req.ready &&
-    !(io.misalign_ldin.fire && io.misalign_ldin.bits.misalignNeedWakeUp) // Currently, misalign is the highest priority
-  ))
-
-  s0_mmio_select := s0_src_select_vec(mmio_idx) && !s0_kill
-  s0_nc_select := s0_src_select_vec(nc_idx) && !s0_kill
-  //judgment: is NC with data or not.
-  //If true, it's from `io.lsq.nc_ldin` or `io.fast_rep_in`
-  val s0_nc_with_data = s0_sel_src.isnc && !s0_kill
-  s0_misalign_select := s0_src_select_vec(mab_idx) && !s0_kill
-
-   // if is hardware prefetch or fast replay, don't send valid to tlb
-  s0_tlb_valid := (
-    s0_src_valid_vec(mab_idx) ||
-    s0_src_valid_vec(super_rep_idx) ||
-    s0_src_valid_vec(lsq_rep_idx) ||
-    s0_src_valid_vec(vec_iss_idx) ||
-    s0_src_valid_vec(int_iss_idx)
-  ) && io.dcache.req.ready
-
-  s0_kill := false.B
-
-  // prefetch related ctrl signal
-  io.canAcceptLowConfPrefetch  := s0_src_ready_vec(low_pf_idx) && io.dcache.req.ready
-  io.canAcceptHighConfPrefetch := s0_src_ready_vec(high_pf_idx) && io.dcache.req.ready
-
-  // query DTLB
-  io.tlb.req.valid                   := s0_tlb_valid
-  io.tlb.req.bits.cmd                := Mux(s0_sel_src.prf,
-                                         Mux(s0_sel_src.prf_wr, TlbCmd.write, TlbCmd.read),
-                                         TlbCmd.read
-                                       )
-  io.tlb.req.bits.isPrefetch         := s0_sel_src.prf
-  io.tlb.req.bits.vaddr              := s0_tlb_vaddr
-  io.tlb.req.bits.fullva             := s0_tlb_fullva
-  io.tlb.req.bits.checkfullva        := s0_src_select_vec(vec_iss_idx) || s0_src_select_vec(int_iss_idx)
-  io.tlb.req.bits.hyperinst          := s0_tlb_hlv
-  io.tlb.req.bits.hlvx               := s0_tlb_hlvx
-  io.tlb.req.bits.size               := Mux(s0_sel_src.isvec, s0_sel_src.alignedType(2,0), LSUOpType.size(s0_sel_src.uop.fuOpType))
-  io.tlb.req.bits.kill               := s0_kill || s0_tlb_no_query // if does not need to be translated, kill it
-  io.tlb.req.bits.memidx.is_ld       := true.B
-  io.tlb.req.bits.memidx.is_st       := false.B
-  io.tlb.req.bits.memidx.idx         := s0_sel_src.uop.lqIdx.value
-  io.tlb.req.bits.debug.robIdx       := s0_sel_src.uop.robIdx
-  io.tlb.req.bits.no_translate       := s0_tlb_no_query  // hardware prefetch and fast replay does not need to be translated, need this signal for pmp check
-  io.tlb.req.bits.debug.pc           := s0_sel_src.uop.pc
-  io.tlb.req.bits.debug.isFirstIssue := s0_sel_src.isFirstIssue
-
-  // query DCache
-  io.dcache.req.valid             := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
-  io.dcache.req.bits.cmd          := Mux(s0_sel_src.prf_rd,
-                                      MemoryOpConstants.M_PFR,
-                                      Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD)
-                                    )
-  io.dcache.req.bits.vaddr        := s0_dcache_vaddr
-  io.dcache.req.bits.vaddr_dup    := s0_dcache_vaddr
-  io.dcache.req.bits.mask         := s0_sel_src.mask
-  io.dcache.req.bits.data         := DontCare
-  io.dcache.req.bits.isFirstIssue := s0_sel_src.isFirstIssue
-  io.dcache.req.bits.instrtype    := Mux(s0_sel_src.prf, DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
-  io.dcache.req.bits.debug_robIdx := s0_sel_src.uop.robIdx.value
-  io.dcache.req.bits.replayCarry  := s0_sel_src.rep_carry
-  io.dcache.req.bits.id           := DontCare // TODO: update cache meta
-  io.dcache.req.bits.lqIdx        := s0_sel_src.uop.lqIdx
-  io.dcache.pf_source             := Mux(s0_hw_prf_select, io.prefetch_req.bits.pf_source.value, L1_HW_PREFETCH_NULL)
-  io.dcache.is128Req              := s0_is128bit
-
-  // load flow priority mux
-  def fromNullSource(): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out
-  }
-
-  def fromMisAlignBufferSource(src: LsPipelineBundle): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.vaddr
-    out.mask          := src.mask
-    out.uop           := src.uop
-    out.has_rob_entry := false.B
-    out.rep_carry     := src.replayCarry
-    out.mshrid        := src.mshrid
-    out.frm_mabuf     := true.B
-    out.isFirstIssue  := false.B
-    out.fast_rep      := false.B
-    out.ld_rep        := false.B
-    out.prf           := false.B
-    out.prf_rd        := false.B
-    out.prf_wr        := false.B
-    out.sched_idx     := src.schedIndex
-    out.isvec         := src.isvec
-    out.is128bit      := src.is128bit
-    out.vecActive     := true.B
-    out
-  }
-
-  def fromFastReplaySource(src: LqWriteBundle): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr         := src.vaddr
-    out.paddr         := src.paddr
-    out.mask          := src.mask
-    out.uop           := src.uop
-    out.has_rob_entry := src.hasROBEntry
-    out.rep_carry     := src.rep_info.rep_carry
-    out.mshrid        := src.rep_info.mshr_id
-    out.frm_mabuf     := src.isFrmMisAlignBuf
-    out.isFirstIssue  := false.B
-    out.fast_rep      := true.B
-    out.ld_rep        := src.isLoadReplay
-    out.prf           := LSUOpType.isPrefetch(src.uop.fuOpType) && !src.isvec
-    out.prf_rd        := src.uop.fuOpType === LSUOpType.prefetch_r
-    out.prf_wr        := src.uop.fuOpType === LSUOpType.prefetch_w
-    out.prf_i         := false.B
-    out.sched_idx     := src.schedIndex
-    out.isvec         := src.isvec
-    out.is128bit      := src.is128bit
-    out.uop_unit_stride_fof := src.uop_unit_stride_fof
-    out.reg_offset    := src.reg_offset
-    out.vecActive     := src.vecActive
-    out.is_first_ele  := src.is_first_ele
-    out.usSecondInv   := src.usSecondInv
-    out.mbIndex       := src.mbIndex
-    out.elemIdx       := src.elemIdx
-    out.elemIdxInsideVd := src.elemIdxInsideVd
-    out.alignedType   := src.alignedType
-    out.data          := src.data
-    out
-  }
-
-  // TODO: implement vector mmio
-  def fromMmioSource(src: MemExuOutput) = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.mask          := 0.U
-    out.uop           := src.uop
-    out.has_rob_entry := false.B
-    out.rep_carry     := 0.U.asTypeOf(out.rep_carry)
-    out.mshrid        := 0.U
-    out.frm_mabuf     := false.B
-    out.isFirstIssue  := false.B
-    out.fast_rep      := false.B
-    out.ld_rep        := false.B
-    out.prf           := false.B
-    out.prf_rd        := false.B
-    out.prf_wr        := false.B
-    out.prf_i         := false.B
-    out.sched_idx     := 0.U
-    out.vecActive     := true.B
-    out
-  }
-
-  def fromNcSource(src: LsPipelineBundle): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.vaddr := src.vaddr
-    out.paddr := src.paddr
-    out.mask := genVWmask(src.vaddr, LSUOpType.size(src.uop.fuOpType))
-    out.uop := src.uop
-    out.has_rob_entry := true.B
-    out.sched_idx := src.schedIndex
-    out.isvec := src.isvec
-    out.is128bit := src.is128bit
-    out.vecActive := src.vecActive
-    out.isnc := true.B
-    out.data := src.data
-    out
-  }
-
-  def fromNormalReplaySource(src: LsPipelineBundle): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.mask          := Mux(src.isvec, src.mask, genVWmask(src.vaddr, LSUOpType.size(src.uop.fuOpType)))
-    out.uop           := src.uop
-    out.has_rob_entry := true.B
-    out.rep_carry     := src.replayCarry
-    out.mshrid        := src.mshrid
-    out.frm_mabuf     := false.B
-    out.isFirstIssue  := false.B
-    out.fast_rep      := false.B
-    out.ld_rep        := true.B
-    out.prf           := LSUOpType.isPrefetch(src.uop.fuOpType) && !src.isvec
-    out.prf_rd        := src.uop.fuOpType === LSUOpType.prefetch_r
-    out.prf_wr        := src.uop.fuOpType === LSUOpType.prefetch_w
-    out.prf_i         := false.B
-    out.sched_idx     := src.schedIndex
-    out.isvec         := src.isvec
-    out.is128bit      := src.is128bit
-    out.uop_unit_stride_fof := src.uop_unit_stride_fof
-    out.reg_offset    := src.reg_offset
-    out.vecActive     := src.vecActive
-    out.is_first_ele  := src.is_first_ele
-    out.usSecondInv   := src.usSecondInv
-    out.mbIndex       := src.mbIndex
-    out.elemIdx       := src.elemIdx
-    out.elemIdxInsideVd := src.elemIdxInsideVd
-    out.alignedType   := src.alignedType
-    out.repForTlbMiss := src.tlbMiss
-    out
-  }
-
-  // TODO: implement vector prefetch
-  def fromPrefetchSource(src: L1PrefetchReq): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.mask          := 0.U
-    out.uop           := DontCare
-    out.has_rob_entry := false.B
-    out.rep_carry     := 0.U.asTypeOf(out.rep_carry)
-    out.mshrid        := 0.U
-    out.frm_mabuf     := false.B
-    out.isFirstIssue  := false.B
-    out.fast_rep      := false.B
-    out.ld_rep        := false.B
-    out.prf           := true.B
-    out.prf_rd        := !src.is_store
-    out.prf_wr        := src.is_store
-    out.prf_i         := false.B
-    out.sched_idx     := 0.U
-    out
-  }
-
-  def fromVecIssueSource(src: VecPipeBundle): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.mask          := src.mask
-    out.uop           := src.uop
-    out.has_rob_entry := true.B
-    // TODO: VLSU, implement replay carry
-    out.rep_carry     := 0.U.asTypeOf(out.rep_carry)
-    out.mshrid        := 0.U
-    out.frm_mabuf     := false.B
-    // TODO: VLSU, implement first issue
-//    out.isFirstIssue  := src.isFirstIssue
-    out.fast_rep      := false.B
-    out.ld_rep        := false.B
-    out.prf           := false.B
-    out.prf_rd        := false.B
-    out.prf_wr        := false.B
-    out.prf_i         := false.B
-    out.sched_idx     := 0.U
-    // Vector load interface
-    out.isvec               := true.B
-    // vector loads only access a single element at a time, so 128-bit path is not used for now
-    out.is128bit            := is128Bit(src.alignedType)
-    out.uop_unit_stride_fof := src.uop_unit_stride_fof
-    // out.rob_idx_valid       := src.rob_idx_valid
-    // out.inner_idx           := src.inner_idx
-    // out.rob_idx             := src.rob_idx
-    out.reg_offset          := src.reg_offset
-    // out.offset              := src.offset
-    out.vecActive           := src.vecActive
-    out.is_first_ele        := src.is_first_ele
-    // out.flowPtr             := src.flowPtr
-    out.usSecondInv         := src.usSecondInv
-    out.mbIndex             := src.mBIndex
-    out.elemIdx             := src.elemIdx
-    out.elemIdxInsideVd     := src.elemIdxInsideVd
-    out.vecBaseVaddr        := src.basevaddr
-    out.alignedType         := src.alignedType
-    out
-  }
-
-  def fromIntIssueSource(src: ExuInput): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    val addr           = src.src(0) + SignExt(src.imm(11, 0), VAddrBits)
-    out.mask          := genVWmask(addr, LSUOpType.size(src.fuOpType))
-    out.uop           := src.toDynInst()
-    out.has_rob_entry := true.B
-    out.rep_carry     := 0.U.asTypeOf(out.rep_carry)
-    out.mshrid        := 0.U
-    out.frm_mabuf     := false.B
-    out.isFirstIssue  := true.B
-    out.fast_rep      := false.B
-    out.ld_rep        := false.B
-    out.prf           := LSUOpType.isPrefetch(src.fuOpType)
-    out.prf_rd        := src.fuOpType === LSUOpType.prefetch_r
-    out.prf_wr        := src.fuOpType === LSUOpType.prefetch_w
-    out.prf_i         := src.fuOpType === LSUOpType.prefetch_i
-    out.sched_idx     := 0.U
-    out.vecActive     := true.B // true for scala load
-    out
-  }
-
-  def fromLoadToLoadSource(src: LoadToLoadIO): FlowSource = {
-    val out = WireInit(0.U.asTypeOf(new FlowSource))
-    out.mask               := genVWmask(0.U, LSUOpType.ld)
-    // When there's no valid instruction from RS and LSQ, we try the load-to-load forwarding.
-    // Assume the pointer chasing is always ld.
-    out.uop.fuOpType       := LSUOpType.ld
-    // we dont care out.isFirstIssue and out.rsIdx and s0_sqIdx in S0 when trying pointchasing
-    // because these signals will be updated in S1
-    out.has_rob_entry      := false.B
-    out.mshrid             := 0.U
-    out.frm_mabuf          := false.B
-    out.rep_carry          := 0.U.asTypeOf(out.rep_carry)
-    out.isFirstIssue       := true.B
-    out.fast_rep           := false.B
-    out.ld_rep             := false.B
-    out.prf                := false.B
-    out.prf_rd             := false.B
-    out.prf_wr             := false.B
-    out.prf_i              := false.B
-    out.sched_idx          := 0.U
-    out
-  }
-
-  // set default
-  val s0_src_selector = WireInit(s0_src_valid_vec)
-  val s0_src_format = Seq(
-    fromMisAlignBufferSource(io.misalign_ldin.bits),
-    fromNormalReplaySource(io.replay.bits),
-    fromFastReplaySource(io.fast_rep_in.bits),
-    fromNormalReplaySource(io.replay.bits),
-    fromPrefetchSource(io.prefetch_req.bits),
-    fromVecIssueSource(io.vecldin.bits),
-    fromIntIssueSource(io.ldin.bits),
-    fromMmioSource(io.lsq.uncache.bits),
-    fromNcSource(io.lsq.nc_ldin.bits),
-    fromPrefetchSource(io.prefetch_req.bits)
-  )
-  s0_sel_src := ParallelPriorityMux(s0_src_selector, s0_src_format)
-
-  // fast replay and hardware prefetch don't need to query tlb
-  val int_issue_vaddr = io.ldin.bits.src(0) + SignExt(io.ldin.bits.imm(11, 0), VAddrBits)
-  val int_vec_vaddr = Mux(s0_src_valid_vec(vec_iss_idx), io.vecldin.bits.vaddr(VAddrBits - 1, 0), int_issue_vaddr)
-  s0_tlb_vaddr := Mux(
-    s0_src_valid_vec(mab_idx),
-    io.misalign_ldin.bits.vaddr,
-    Mux(
-      s0_src_valid_vec(super_rep_idx) || s0_src_valid_vec(lsq_rep_idx),
-      io.replay.bits.vaddr,
-      int_vec_vaddr
-    )
-  )
-  s0_dcache_vaddr := Mux(
-    s0_src_select_vec(fast_rep_idx), io.fast_rep_in.bits.vaddr,
-    Mux(s0_hw_prf_select, io.prefetch_req.bits.getVaddr(),
-    Mux(s0_src_select_vec(nc_idx), io.lsq.nc_ldin.bits.vaddr, // not for dcache access, but for address alignment check
-    s0_tlb_vaddr))
-  )
-
-  val s0_alignType = Mux(s0_sel_src.isvec, s0_sel_src.alignedType(1,0), LSUOpType.size(s0_sel_src.uop.fuOpType))
-
-  val s0_addr_aligned = LookupTree(s0_alignType, List(
-    "b00".U   -> true.B,                   //b
-    "b01".U   -> (s0_dcache_vaddr(0)    === 0.U), //h
-    "b10".U   -> (s0_dcache_vaddr(1, 0) === 0.U), //w
-    "b11".U   -> (s0_dcache_vaddr(2, 0) === 0.U)  //d
-  ))
-  // address align check
-  XSError(s0_sel_src.isvec && s0_dcache_vaddr(3, 0) =/= 0.U && s0_sel_src.alignedType(2), "unit-stride 128 bit element is not aligned!")
-
-  val s0_check_vaddr_low = s0_dcache_vaddr(4, 0)
-  val s0_check_vaddr_Up_low = LookupTree(s0_alignType, List(
-    "b00".U -> 0.U,
-    "b01".U -> 1.U,
-    "b10".U -> 3.U,
-    "b11".U -> 7.U
-  )) + s0_check_vaddr_low
-  //TODO vec?
-  val s0_rs_cross16Bytes = s0_check_vaddr_Up_low(4) =/= s0_check_vaddr_low(4)
-  val s0_misalignWith16Byte = !s0_rs_cross16Bytes && !s0_addr_aligned && !s0_hw_prf_select
-  val s0_misalignNeedWakeUp = s0_sel_src.frm_mabuf && io.misalign_ldin.bits.misalignNeedWakeUp
-  val s0_finalSplit = s0_sel_src.frm_mabuf && io.misalign_ldin.bits.isFinalSplit
-  s0_is128bit := s0_sel_src.is128bit || s0_misalignWith16Byte
-
-  // only first issue of int / vec load intructions need to check full vaddr
-  s0_tlb_fullva := Mux(s0_src_valid_vec(mab_idx),
-    io.misalign_ldin.bits.fullva,
-    Mux(s0_src_select_vec(vec_iss_idx),
-      io.vecldin.bits.vaddr,
-      Mux(
-        s0_src_select_vec(int_iss_idx),
-        io.ldin.bits.src(0) + SignExt(io.ldin.bits.imm(11, 0), XLEN),
-        s0_dcache_vaddr
-      )
-    )
-  )
-
-  s0_tlb_hlv := Mux(
-    s0_src_valid_vec(mab_idx),
-    LSUOpType.isHlv(io.misalign_ldin.bits.uop.fuOpType),
-    Mux(
-      s0_src_valid_vec(super_rep_idx) || s0_src_valid_vec(lsq_rep_idx),
-      LSUOpType.isHlv(io.replay.bits.uop.fuOpType),
-      Mux(
-        s0_src_valid_vec(int_iss_idx),
-        LSUOpType.isHlv(io.ldin.bits.fuOpType),
-        false.B
-      )
-    )
-  )
-  s0_tlb_hlvx := Mux(
-    s0_src_valid_vec(mab_idx),
-    LSUOpType.isHlvx(io.misalign_ldin.bits.uop.fuOpType),
-    Mux(
-      s0_src_valid_vec(super_rep_idx) || s0_src_valid_vec(lsq_rep_idx),
-      LSUOpType.isHlvx(io.replay.bits.uop.fuOpType),
-      Mux(
-        s0_src_valid_vec(int_iss_idx),
-        LSUOpType.isHlvx(io.ldin.bits.fuOpType),
-        false.B
-      )
-    )
-  )
-
-  // accept load flow if dcache ready (tlb is always ready)
-  // TODO: prefetch need writeback to loadQueueFlag
-  s0_out               := DontCare
-  s0_out.vaddr         := Mux(s0_nc_with_data, s0_sel_src.vaddr, s0_dcache_vaddr)
-  s0_out.fullva        := Mux(s0_sel_src.frm_mabuf, s0_out.vaddr, s0_tlb_fullva)
-  s0_out.mask          := s0_sel_src.mask
-  s0_out.uop           := s0_sel_src.uop
-  s0_out.isFirstIssue  := s0_sel_src.isFirstIssue
-  s0_out.hasROBEntry   := s0_sel_src.has_rob_entry
-  s0_out.isPrefetch    := s0_sel_src.prf
-  s0_out.isHWPrefetch  := s0_hw_prf_select
-  s0_out.isFastReplay  := s0_sel_src.fast_rep
-  s0_out.isLoadReplay  := s0_sel_src.ld_rep
-  s0_out.isFastPath    := false.B
-  s0_out.mshrid        := s0_sel_src.mshrid
-  s0_out.isvec           := s0_sel_src.isvec
-  s0_out.is128bit        := s0_is128bit
-  s0_out.isFrmMisAlignBuf    := s0_sel_src.frm_mabuf
-  s0_out.uop_unit_stride_fof := s0_sel_src.uop_unit_stride_fof
-  s0_out.paddr         :=
-    Mux(s0_src_select_vec(nc_idx), io.lsq.nc_ldin.bits.paddr,
-    Mux(s0_src_select_vec(fast_rep_idx), io.fast_rep_in.bits.paddr,
-    Mux(s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i, 0.U,
-    io.prefetch_req.bits.paddr))) // only for nc, fast_rep, prefetch
-  s0_out.tlbNoQuery    := s0_tlb_no_query
-  // s0_out.rob_idx_valid   := s0_rob_idx_valid
-  // s0_out.inner_idx       := s0_inner_idx
-  // s0_out.rob_idx         := s0_rob_idx
-  s0_out.reg_offset      := s0_sel_src.reg_offset
-  // s0_out.offset          := s0_offset
-  s0_out.vecActive             := s0_sel_src.vecActive
-  s0_out.usSecondInv    := s0_sel_src.usSecondInv
-  s0_out.is_first_ele   := s0_sel_src.is_first_ele
-  s0_out.elemIdx        := s0_sel_src.elemIdx
-  s0_out.elemIdxInsideVd := s0_sel_src.elemIdxInsideVd
-  s0_out.alignedType    := s0_sel_src.alignedType
-  s0_out.mbIndex        := s0_sel_src.mbIndex
-  s0_out.vecBaseVaddr   := s0_sel_src.vecBaseVaddr
-  // s0_out.flowPtr         := s0_sel_src.flowPtr
-  s0_out.uop.exceptionVec(loadAddrMisaligned) := (!s0_addr_aligned || s0_sel_src.uop.exceptionVec(loadAddrMisaligned)) && s0_sel_src.vecActive && !s0_misalignWith16Byte
-  s0_out.isMisalign := (!s0_addr_aligned || s0_sel_src.uop.exceptionVec(loadAddrMisaligned)) && s0_sel_src.vecActive
-  s0_out.forward_tlDchannel := s0_src_select_vec(super_rep_idx)
-  when(io.tlb.req.valid && (s0_sel_src.isFirstIssue || s0_sel_src.repForTlbMiss)) {
-    s0_out.uop.perfDebugInfo.tlbFirstReqTime := GTimer()
-  }.otherwise{
-    s0_out.uop.perfDebugInfo.tlbFirstReqTime := s0_sel_src.uop.perfDebugInfo.tlbFirstReqTime
-  }
-  s0_out.schedIndex     := s0_sel_src.sched_idx
-  //for Svpbmt Nc
-  s0_out.nc := s0_sel_src.isnc
-  s0_out.data := s0_sel_src.data
-  s0_out.misalignWith16Byte    := s0_misalignWith16Byte
-  s0_out.misalignNeedWakeUp := s0_misalignNeedWakeUp
-  s0_out.isFinalSplit := s0_finalSplit
-
-  // load fast replay
-  io.fast_rep_in.ready := (s0_can_go && io.dcache.req.ready && s0_src_ready_vec(fast_rep_idx))
-
-  // mmio
-  io.lsq.uncache.ready := s0_mmio_fire
-  io.lsq.nc_ldin.ready := s0_src_ready_vec(nc_idx) && s0_can_go
-
-  // load flow source ready
-  // cache missed load has highest priority
-  // always accept cache missed load flow from load replay queue
-  io.replay.ready := (s0_can_go && io.dcache.req.ready && (s0_src_ready_vec(lsq_rep_idx) && !s0_rep_stall || s0_src_select_vec(super_rep_idx)))
-
-  // accept load flow from rs when:
-  // 1) there is no lsq-replayed load
-  // 2) there is no fast replayed load
-  // 3) there is no high confidence prefetch request
-  io.vecldin.ready := s0_can_go && io.dcache.req.ready && s0_src_ready_vec(vec_iss_idx)
-  io.ldin.ready := s0_can_go && io.dcache.req.ready && s0_src_ready_vec(int_iss_idx)
-  io.misalign_ldin.ready := s0_can_go && io.dcache.req.ready && s0_src_ready_vec(mab_idx)
-
-  // for hw prefetch load flow feedback, to be added later
-  // io.prefetch_in.ready := s0_hw_prf_select
-
-  // dcache replacement extra info
-  // TODO: should prefetch load update replacement?
-  io.dcache.replacementUpdated := Mux(s0_src_select_vec(lsq_rep_idx) || s0_src_select_vec(super_rep_idx), io.replay.bits.replacementUpdated, false.B)
-
-  // load wakeup
-  // TODO: vector load wakeup? frm_mabuf wakeup?
-  val s0_wakeup_selector = Seq(
-    s0_misalign_wakeup_fire,
-    s0_src_valid_vec(super_rep_idx),
-    s0_src_valid_vec(fast_rep_idx),
-    s0_mmio_fire,
-    s0_nc_fire,
-    s0_src_valid_vec(lsq_rep_idx),
-    s0_src_valid_vec(int_iss_idx)
-  )
-  val s0_wakeup_format = Seq(
-    io.misalign_ldin.bits.uop,
-    io.replay.bits.uop,
-    io.fast_rep_in.bits.uop,
-    io.lsq.uncache.bits.uop,
-    io.lsq.nc_ldin.bits.uop,
-    io.replay.bits.uop,
-    io.ldin.bits.toDynInst(),
-  )
-  val s0_wakeup_uop = ParallelPriorityMux(s0_wakeup_selector, s0_wakeup_format)
-  io.wakeup.valid := s0_fire && !s0_sel_src.isvec && !s0_sel_src.frm_mabuf && (
-    s0_src_valid_vec(super_rep_idx) ||
-    s0_src_valid_vec(fast_rep_idx) ||
-    s0_src_valid_vec(lsq_rep_idx) ||
-    (s0_src_valid_vec(int_iss_idx) && !s0_sel_src.prf &&
-    !s0_src_valid_vec(vec_iss_idx) && !s0_src_valid_vec(high_pf_idx))
-  ) || s0_mmio_fire || s0_nc_fire || s0_misalign_wakeup_fire
-  connectSamePort(io.wakeup.bits, s0_wakeup_uop)
-
-  // prefetch.i(Zicbop)
-  io.ifetchPrefetch.valid := RegNext(s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
-  io.ifetchPrefetch.bits.vaddr := RegEnable(s0_out.vaddr, 0.U, s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
-
-  XSDebug(io.dcache.req.fire,
-    p"[DCACHE LOAD REQ] pc ${Hexadecimal(s0_sel_src.uop.pc)}, vaddr ${Hexadecimal(s0_dcache_vaddr)}\n"
-  )
-  XSDebug(s0_valid,
-    p"S0: pc ${Hexadecimal(s0_out.uop.pc)}, lId ${Hexadecimal(s0_out.uop.lqIdx.asUInt)}, " +
-    p"vaddr ${Hexadecimal(s0_out.vaddr)}, mask ${Hexadecimal(s0_out.mask)}\n")
-
-  // Pipeline
-  // --------------------------------------------------------------------------------
-  // stage 1
-  // --------------------------------------------------------------------------------
-  // TLB resp (send paddr to dcache)
-  val s1_valid      = RegInit(false.B)
-  val s1_in         = Wire(new LqWriteBundle)
-  val s1_out        = Wire(new LqWriteBundle)
-  val s1_kill       = Wire(Bool())
-  val s1_can_go     = s2_ready
-  val s1_fire       = s1_valid && !s1_kill && s1_can_go
-  val s1_vecActive        = RegEnable(s0_out.vecActive, true.B, s0_fire)
-  val s1_nc_with_data = RegNext(s0_nc_with_data)
-
-  s1_ready := !s1_valid || s1_kill || s2_ready
-  when (s0_fire) { s1_valid := true.B }
-  .elsewhen (s1_fire) { s1_valid := false.B }
-  .elsewhen (s1_kill) { s1_valid := false.B }
-  s1_in   := RegEnable(s0_out, s0_fire)
-
-  val s1_fast_rep_dly_kill = RegEnable(io.fast_rep_in.bits.lateKill, io.fast_rep_in.valid) && s1_in.isFastReplay
-  val s1_fast_rep_dly_err =  RegEnable(io.fast_rep_in.bits.delayedLoadError, io.fast_rep_in.valid) && s1_in.isFastReplay
-  val s1_dly_err          = s1_fast_rep_dly_err
-  val s1_vaddr_hi         = Wire(UInt())
-  val s1_vaddr_lo         = Wire(UInt())
-  val s1_vaddr            = Wire(UInt())
-  val s1_paddr_dup_lsu    = Wire(UInt())
-  val s1_gpaddr_dup_lsu   = Wire(UInt())
-  val s1_paddr_dup_dcache = Wire(UInt())
-  val s1_exception        = ExceptionNO.selectByFu(s1_out.uop.exceptionVec, LduCfg).asUInt.orR   // af & pf exception were modified below.
-  val s1_tlb_miss         = io.tlb.resp.bits.miss && io.tlb.resp.valid && s1_valid
-  val s1_tlb_fast_miss    = io.tlb.resp.bits.fastMiss && io.tlb.resp.valid && s1_valid
-  val s1_tlb_hit          = !io.tlb.resp.bits.miss && io.tlb.resp.valid && s1_valid
-  val s1_pbmt             = Mux(s1_tlb_hit, io.tlb.resp.bits.pbmt.head, 0.U(Pbmt.width.W))
-  val s1_nc               = s1_in.nc
-  val s1_prf              = s1_in.isPrefetch
-  val s1_hw_prf           = s1_in.isHWPrefetch
-  val s1_sw_prf           = s1_prf && !s1_hw_prf
-  val s1_tlb_memidx       = io.tlb.resp.bits.memidx
-
-  s1_vaddr_hi         := s1_in.vaddr(VAddrBits - 1, 6)
-  s1_vaddr_lo         := s1_in.vaddr(5, 0)
-  s1_vaddr            := Cat(s1_vaddr_hi, s1_vaddr_lo)
-  s1_paddr_dup_lsu    := io.tlb.resp.bits.paddr(0)
-  s1_paddr_dup_dcache := io.tlb.resp.bits.paddr(1)
-  s1_gpaddr_dup_lsu   := Mux(s1_in.isFastReplay, s1_in.paddr, io.tlb.resp.bits.gpaddr(0))
-
-  when (io.tlb.resp.valid && !s1_tlb_miss) {
-    s1_out.uop.perfDebugInfo.tlbRespTime := GTimer()
-  }.elsewhen (io.tlb.resp.valid && s1_tlb_miss) {
-    s1_out.uop.perfDebugInfo.tlbRespTime := s1_in.uop.perfDebugInfo.tlbFirstReqTime
-  }.otherwise {
-    s1_out.uop.perfDebugInfo.tlbRespTime := s1_in.uop.perfDebugInfo.tlbRespTime
-  }
-
-  io.tlb.req_kill   := s1_kill || s1_dly_err
-  io.tlb.req.bits.pmp_addr := s1_in.paddr
-  io.tlb.resp.ready := true.B
-
-  io.dcache.s1_paddr_dup_lsu    <> s1_paddr_dup_lsu
-  io.dcache.s1_paddr_dup_dcache <> s1_paddr_dup_dcache
-  io.dcache.s1_kill             := s1_kill || s1_dly_err || s1_tlb_miss || s1_exception
-  io.dcache.s1_kill_data_read   := s1_kill || s1_dly_err || s1_tlb_fast_miss
-
-  // store to load forwarding
-  io.sbuffer.valid := s1_valid && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
-  io.sbuffer.vaddr := s1_vaddr
-  io.sbuffer.paddr := s1_paddr_dup_lsu
-  io.sbuffer.uop   := s1_in.uop
-  io.sbuffer.sqIdx := s1_in.uop.sqIdx
-  io.sbuffer.mask  := s1_in.mask
-  io.sbuffer.pc    := s1_in.uop.pc // FIXME: remove it
-
-  io.ubuffer.valid := s1_valid && s1_nc_with_data && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
-  io.ubuffer.vaddr := s1_vaddr
-  io.ubuffer.paddr := s1_paddr_dup_lsu
-  io.ubuffer.uop   := s1_in.uop
-  io.ubuffer.sqIdx := s1_in.uop.sqIdx
-  io.ubuffer.mask  := s1_in.mask
-  io.ubuffer.pc    := s1_in.uop.pc // FIXME: remove it
-
-  io.lsq.forward.valid     := s1_valid && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
-  io.lsq.forward.vaddr     := s1_vaddr
-  io.lsq.forward.paddr     := s1_paddr_dup_lsu
-  io.lsq.forward.uop       := s1_in.uop
-  io.lsq.forward.sqIdx     := s1_in.uop.sqIdx
-  io.lsq.forward.sqIdxMask := 0.U
-  io.lsq.forward.mask      := s1_in.mask
-  io.lsq.forward.pc        := s1_in.uop.pc // FIXME: remove it
-
-  // st-ld violation query
-    // if store unit is 128-bits memory access, need match 128-bit
-  val s1_nuke_paddr_match = VecInit((0 until StorePipelineWidth).map{
-    case index => {
-      val stMathType = io.stld_nuke_query(index).bits.matchType
-      val stAddr = io.stld_nuke_query(index).bits.paddr
-      val isMatch128Bit = StLdNukeMatchType.isQuadWord(stMathType) || ((s1_in.isvec || s1_in.misalignWith16Byte) && s1_in.is128bit)
-      PriorityMux(Seq(
-        StLdNukeMatchType.isCacheLine(stMathType) -> (s1_paddr_dup_lsu(PAddrBits-1, blockOffBits) === stAddr(PAddrBits-1, blockOffBits)),
-        isMatch128Bit                             -> (s1_paddr_dup_lsu(PAddrBits-1, 4) === stAddr(PAddrBits-1, 4)),
-        StLdNukeMatchType.isNormal(stMathType)    -> (s1_paddr_dup_lsu(PAddrBits-1, 3) === stAddr(PAddrBits-1, 3)))
-      )
-    }
-  })
-  val s1_nuke = VecInit((0 until StorePipelineWidth).map(w => {
-                       io.stld_nuke_query(w).valid && // query valid
-                       isAfter(s1_in.uop.robIdx, io.stld_nuke_query(w).bits.robIdx) && // older store
-                       s1_nuke_paddr_match(w) && // paddr match
-                       (s1_in.mask & io.stld_nuke_query(w).bits.mask).orR // data mask contain
-                      })).asUInt.orR && !s1_tlb_miss
-  val s1_nuke_first =  VecInit((0 until StorePipelineWidth).map(w => {
-                          s1_in.uop.waitForRobIdx === io.stld_nuke_query(w).bits.robIdx || !s1_in.uop.loadWaitBit
-                        })).asUInt.orR
-
-  s1_out                   := s1_in
-  s1_out.vaddr             := s1_vaddr
-  s1_out.fullva            := Mux(s1_in.isFrmMisAlignBuf, s1_in.vaddr, io.tlb.resp.bits.fullva)
-  s1_out.vaNeedExt         := io.tlb.resp.bits.excp(0).vaNeedExt
-  s1_out.isHyper           := io.tlb.resp.bits.excp(0).isHyper
-  s1_out.paddr             := s1_paddr_dup_lsu
-  s1_out.gpaddr            := s1_gpaddr_dup_lsu
-  s1_out.isForVSnonLeafPTE := io.tlb.resp.bits.isForVSnonLeafPTE
-  s1_out.tlbMiss           := s1_tlb_miss
-  s1_out.ptwBack           := io.tlb.resp.bits.ptwBack
-  s1_out.rep_info.debug    := s1_in.uop.perfDebugInfo
-  s1_out.rep_info.nuke     := s1_nuke && !s1_sw_prf
-  s1_out.delayedLoadError  := s1_dly_err
-  s1_out.nc := (s1_nc || Pbmt.isNC(s1_pbmt)) && !s1_prf
-  s1_out.nuke_first        := s1_nuke_first
-  s1_out.mmio := Pbmt.isIO(s1_pbmt)
-
-  when (!s1_dly_err) {
-    // current ori test will cause the case of ldest == 0, below will be modifeid in the future.
-    // af & pf exception were modified
-    // if is tlbNoQuery request, don't trigger exception from tlb resp
-    s1_out.uop.exceptionVec(loadPageFault)   := io.tlb.resp.bits.excp(0).pf.ld && s1_vecActive && !s1_tlb_miss && !s1_in.tlbNoQuery
-    s1_out.uop.exceptionVec(loadGuestPageFault)   := io.tlb.resp.bits.excp(0).gpf.ld && !s1_tlb_miss && !s1_in.tlbNoQuery
-    s1_out.uop.exceptionVec(loadAccessFault) := io.tlb.resp.bits.excp(0).af.ld && s1_vecActive && !s1_tlb_miss && !s1_in.tlbNoQuery
-    when (RegNext(io.tlb.req.bits.checkfullva) &&
-      (s1_out.uop.exceptionVec(loadPageFault) ||
-        s1_out.uop.exceptionVec(loadGuestPageFault) ||
-        s1_out.uop.exceptionVec(loadAccessFault))) {
-      s1_out.uop.exceptionVec(loadAddrMisaligned) := false.B
-      s1_out.isMisalign := false.B
-    }
-  } .otherwise {
-    s1_out.uop.exceptionVec(loadPageFault)      := false.B
-    s1_out.uop.exceptionVec(loadGuestPageFault) := false.B
-    s1_out.uop.exceptionVec(loadAddrMisaligned) := false.B
-    s1_out.uop.exceptionVec(loadAccessFault)    := false.B
-    s1_out.uop.exceptionVec(hardwareError)      := s1_dly_err && s1_vecActive
-    s1_out.isMisalign := false.B
-  }
-
-  // pointer chasing
-  val s1_fu_op_type_not_ld     = WireInit(false.B)
-  val s1_not_fast_match        = WireInit(false.B)
-  val s1_addr_mismatch         = WireInit(false.B)
-  val s1_addr_misaligned       = WireInit(false.B)
-  val s1_fast_mismatch         = WireInit(false.B)
-
-  val s1_redirect_reg = Wire(Valid(new Redirect))
-  s1_redirect_reg.bits := RegEnable(io.redirect.bits, io.redirect.valid)
-  s1_redirect_reg.valid := GatedValidRegNext(io.redirect.valid)
-
-  s1_kill := s1_fast_rep_dly_kill ||
-    s1_in.uop.robIdx.needFlush(io.redirect) ||
-    s1_in.uop.robIdx.needFlush(s1_redirect_reg) ||
-    RegEnable(s0_kill, false.B, io.ldin.valid ||
-      io.vecldin.valid || io.replay.valid ||
-      io.fast_rep_in.valid || io.misalign_ldin.valid ||
-      io.lsq.nc_ldin.valid
-    )
-
-  // pre-calcuate sqIdx mask in s0, then send it to lsq in s1 for forwarding
-  val s1_sqIdx_mask = RegEnable(UIntToMask(s0_out.uop.sqIdx.value, StoreQueueSize), s0_fire)
-  // to enable load-load, sqIdxMask must be calculated based on ldin.uop
-  // If the timing here is not OK, load-load forwarding has to be disabled.
-  // Or we calculate sqIdxMask at RS??
-  io.lsq.forward.sqIdxMask := s1_sqIdx_mask
-
-  io.forward_mshr.valid  := s1_valid && s1_out.forward_tlDchannel
-  io.forward_mshr.mshrid := s1_out.mshrid
-  io.forward_mshr.paddr  := s1_out.paddr
-
-  val loadTrigger = Module(new MemTrigger(MemType.LOAD))
-  loadTrigger.io.fromCsrTrigger.tdataVec             := io.fromCsrTrigger.tdataVec
-  loadTrigger.io.fromCsrTrigger.tEnableVec           := io.fromCsrTrigger.tEnableVec
-  loadTrigger.io.fromCsrTrigger.triggerCanRaiseBpExp := io.fromCsrTrigger.triggerCanRaiseBpExp
-  loadTrigger.io.fromCsrTrigger.debugMode            := io.fromCsrTrigger.debugMode
-  loadTrigger.io.fromLoadStore.vaddr                 := s1_vaddr
-  loadTrigger.io.fromLoadStore.isVectorUnitStride    := s1_in.isvec && s1_in.is128bit
-  loadTrigger.io.fromLoadStore.mask                  := s1_in.mask
-  loadTrigger.io.isPrf.get                           := s1_prf
-
-  val s1_trigger_action = loadTrigger.io.toLoadStore.triggerAction
-  val s1_trigger_debug_mode = TriggerAction.isDmode(s1_trigger_action)
-  val s1_trigger_breakpoint = TriggerAction.isExp(s1_trigger_action)
-  s1_out.uop.trigger                  := s1_trigger_action
-  s1_out.uop.exceptionVec(breakPoint) := s1_trigger_breakpoint
-  s1_out.vecVaddrOffset := Mux(
-    s1_trigger_debug_mode || s1_trigger_breakpoint,
-    loadTrigger.io.toLoadStore.triggerVaddr - s1_in.vecBaseVaddr,
-    s1_in.vaddr + genVFirstUnmask(s1_in.mask).asUInt - s1_in.vecBaseVaddr
-  )
-  s1_out.vecTriggerMask := Mux(s1_trigger_debug_mode || s1_trigger_breakpoint, loadTrigger.io.toLoadStore.triggerMask, 0.U)
-
-  XSDebug(s1_valid,
-    p"S1: pc ${Hexadecimal(s1_out.uop.pc)}, lId ${Hexadecimal(s1_out.uop.lqIdx.asUInt)}, tlb_miss ${io.tlb.resp.bits.miss}, " +
-    p"paddr ${Hexadecimal(s1_out.paddr)}, mmio ${s1_out.mmio}\n")
-
-  // Pipeline
-  // --------------------------------------------------------------------------------
-  // stage 2
-  // --------------------------------------------------------------------------------
-  // s2: DCache resp
-  val s2_valid  = RegInit(false.B)
-  val s2_in     = Wire(new LqWriteBundle)
-  val s2_out    = Wire(new LqWriteBundle)
-  val s2_kill   = Wire(Bool())
-  val s2_can_go = s3_ready
-  val s2_fire   = s2_valid && !s2_kill && s2_can_go
-  val s2_vecActive = RegEnable(s1_out.vecActive, true.B, s1_fire)
-  val s2_isvec  = RegEnable(s1_out.isvec, false.B, s1_fire)
-  val s2_data_select  = genRdataOH(s2_out.uop)
-  val s2_data_select_by_offset = genDataSelectByOffset(s2_out.paddr(3, 0))
-  val s2_frm_mabuf = s2_in.isFrmMisAlignBuf
-  val s2_pbmt = RegEnable(s1_pbmt, s1_fire)
-  val s2_trigger_debug_mode = RegEnable(s1_trigger_debug_mode, false.B, s1_fire)
-  val s2_nc_with_data = RegNext(s1_nc_with_data)
-  val s2_mmio_req = Wire(Valid(new ExuOutput(param)))
-  s2_mmio_req.valid := RegNextN(io.lsq.uncache.fire, 2, Some(false.B))
-  s2_mmio_req.bits  := RegNextN(io.lsq.uncache.bits.toExuOutput(param), 2)
-
-  val s3_misalign_wakeup_req = Wire(Valid(new LqWriteBundle))
-  val s3_misalign_wakeup_req_bits = WireInit(0.U.asTypeOf(new LqWriteBundle))
-  connectSamePort(s3_misalign_wakeup_req_bits, io.misalign_ldin.bits)
-  s3_misalign_wakeup_req.valid := RegNextN(io.misalign_ldin.bits.misalignNeedWakeUp && io.misalign_ldin.fire, 3, Some(false.B))
-  s3_misalign_wakeup_req.bits  := RegNextN(s3_misalign_wakeup_req_bits, 3)
-
-  s2_kill := s2_in.uop.robIdx.needFlush(io.redirect)
-  s2_ready := !s2_valid || s2_kill || s3_ready
-  when (s1_fire) { s2_valid := true.B }
-  .elsewhen (s2_fire) { s2_valid := false.B }
-  .elsewhen (s2_kill) { s2_valid := false.B }
-  s2_in := RegEnable(s1_out, s1_fire)
-
-  val s2_pmp = WireInit(io.pmp)
-  val s2_isMisalign = WireInit(s2_in.isMisalign)
-
-  val s2_prf    = s2_in.isPrefetch
-  val s2_hw_prf = s2_in.isHWPrefetch
-  val s2_exception_vec = WireInit(s2_in.uop.exceptionVec)
-
-  // exception that may cause load addr to be invalid / illegal
-  // if such exception happen, that inst and its exception info
-  // will be force writebacked to rob
-
-  // The response signal of `pmp/pma` is credible only after the physical address is actually generated.
-  // Therefore, the response signals of pmp/pma generated after an address translation has produced an `access fault` or a `page fault` are completely unreliable.
-  val s2_un_access_exception =  s2_vecActive && (
-    s2_in.uop.exceptionVec(loadAccessFault) ||
-    s2_in.uop.exceptionVec(loadPageFault)   ||
-    s2_in.uop.exceptionVec(loadGuestPageFault)
-  )
-  // This real physical address is located in uncache space.
-  val s2_actually_uncache = !s2_in.tlbMiss && !s2_un_access_exception && Pbmt.isPMA(s2_pbmt) && (s2_pmp.mmio && !s2_pmp.ld) || s2_in.nc || s2_in.mmio
-  val s2_uncache = !s2_prf && s2_actually_uncache
-  val s2_memBackTypeMM = !s2_pmp.mmio
-  when (!s2_in.delayedLoadError) {
-    s2_exception_vec(loadAccessFault) := s2_vecActive && (
-      s2_in.uop.exceptionVec(loadAccessFault) ||
-      s2_pmp.ld ||
-      s2_isvec && s2_uncache ||
-      io.dcache.resp.bits.tag_error && GatedValidRegNext(io.csrCtrl.cache_error_enable)
-    )
-  }
-
-  // soft prefetch will not trigger any exception (but ecc error interrupt may
-  // be triggered)
-  val s2_tlb_unrelated_exceps = s2_in.uop.exceptionVec(breakPoint)
-  when (!s2_in.delayedLoadError && (s2_prf || s2_in.tlbMiss && !s2_tlb_unrelated_exceps)) {
-    s2_exception_vec := 0.U.asTypeOf(s2_exception_vec.cloneType)
-    s2_isMisalign := false.B
-  }
-  val s2_exception = s2_vecActive &&
-                    (s2_trigger_debug_mode || ExceptionNO.selectByFu(s2_exception_vec, LduCfg).asUInt.orR)
-  val s2_mis_align = s2_valid && GatedValidRegNext(io.csrCtrl.hd_misalign_ld_enable) &&
-                     s2_out.isMisalign && !s2_in.misalignWith16Byte && !s2_exception_vec(breakPoint) && !s2_trigger_debug_mode && !s2_uncache
-  val (s2_fwd_frm_d_chan, s2_fwd_data_frm_d_chan, s2_d_denied, s2_d_corrupt) = io.tl_d_channel.forward(s1_valid && s1_out.forward_tlDchannel, s1_out.mshrid, s1_out.paddr)
-  val (s2_fwd_data_valid, s2_fwd_frm_mshr, s2_fwd_data_frm_mshr, s2_mshr_denied, s2_mshr_corrupt) = io.forward_mshr.forward()
-  val s2_fwd_frm_d_chan_or_mshr = s2_fwd_data_valid && (s2_fwd_frm_d_chan || s2_fwd_frm_mshr)
-
-  // writeback access fault caused by ecc error / bus error
-  // * ecc data error is slow to generate, so we will not use it until load stage 3
-  // * in load stage 3, an extra signal io.load_error will be used to
-  // * if pbmt =/= 0, mmio is up to pbmt; otherwise, it's up to pmp
-  val s2_tlb_hit = RegNext(s1_tlb_hit)
-  val s2_mmio = !s2_prf &&
-    !s2_exception && !s2_in.tlbMiss &&
-    Mux(Pbmt.isUncache(s2_pbmt), s2_in.mmio, s2_tlb_hit && s2_pmp.mmio)
-
-  val s2_full_fwd      = Wire(Bool())
-  val s2_mem_amb       = s2_in.uop.storeSetHit &&
-                         io.lsq.forward.addrInvalid && RegNext(io.lsq.forward.valid)
-
-  val s2_tlb_miss      = s2_in.tlbMiss
-  val s2_fwd_fail      = io.lsq.forward.dataInvalid && RegNext(io.lsq.forward.valid)
-  val s2_dcache_miss   = io.dcache.resp.bits.miss &&
-                         !s2_fwd_frm_d_chan_or_mshr &&
-                         !s2_full_fwd && !s2_in.nc
-
-  val s2_mq_nack       = io.dcache.s2_mq_nack &&
-                         !s2_fwd_frm_d_chan_or_mshr &&
-                         !s2_full_fwd && !s2_in.nc
-
-  val s2_bank_conflict = io.dcache.s2_bank_conflict &&
-                         !s2_fwd_frm_d_chan_or_mshr &&
-                         !s2_full_fwd && !s2_in.nc
-
-  val s2_wpu_pred_fail = io.dcache.s2_wpu_pred_fail &&
-                        !s2_fwd_frm_d_chan_or_mshr &&
-                        !s2_full_fwd && !s2_in.nc
-
-  val s2_rar_nack      = io.lsq.ldld_nuke_query.req.valid &&
-                         !io.lsq.ldld_nuke_query.req.ready
-
-  val s2_raw_nack      = io.lsq.stld_nuke_query.req.valid &&
-                         !io.lsq.stld_nuke_query.req.ready
-  // st-ld violation query
-  //  NeedFastRecovery Valid when
-  //  1. Fast recovery query request Valid.
-  //  2. Load instruction is younger than requestors(store instructions).
-  //  3. Physical address match.
-  //  4. Data contains.
-  val s2_nuke_paddr_match = VecInit((0 until StorePipelineWidth).map{
-    case index => {
-      val stMathType = io.stld_nuke_query(index).bits.matchType
-      val stAddr = io.stld_nuke_query(index).bits.paddr
-      val isMatch128Bit = StLdNukeMatchType.isQuadWord(stMathType) || ((s2_in.isvec || s2_in.misalignWith16Byte) && s2_in.is128bit)
-      PriorityMux(Seq(
-        StLdNukeMatchType.isCacheLine(stMathType) -> (s2_in.paddr(PAddrBits-1, blockOffBits) === stAddr(PAddrBits-1, blockOffBits)),
-        isMatch128Bit                             -> (s2_in.paddr(PAddrBits-1, 4) === stAddr(PAddrBits-1, 4)),
-        StLdNukeMatchType.isNormal(stMathType)    -> (s2_in.paddr(PAddrBits-1, 3) === stAddr(PAddrBits-1, 3)))
-      )
-    }
-  })
-  val s2_nuke          = VecInit((0 until StorePipelineWidth).map(w => {
-                          io.stld_nuke_query(w).valid && // query valid
-                          isAfter(s2_in.uop.robIdx, io.stld_nuke_query(w).bits.robIdx) && // older store
-                          s2_nuke_paddr_match(w) && // paddr match
-                          (s2_in.mask & io.stld_nuke_query(w).bits.mask).orR // data mask contain
-                        })).asUInt.orR && !s2_tlb_miss || s2_in.rep_info.nuke
-
-  val s2_nuke_first    = VecInit((0 until StorePipelineWidth).map(w => {
-                          s2_in.uop.waitForRobIdx === io.stld_nuke_query(w).bits.robIdx || !s2_in.uop.loadWaitBit
-                        })).asUInt.orR || s2_in.nuke_first
-  val s2_cache_handled   = io.dcache.resp.bits.handled
-
-  //if it is NC with data, it should handle the replayed situation.
-  //else s2_uncache will enter uncache buffer.
-  val s2_troublem        = !s2_exception &&
-                           (!s2_uncache || s2_nc_with_data) &&
-                           !s2_prf &&
-                           !s2_in.delayedLoadError
-
-  io.dcache.resp.ready  := true.B
-  val s2_dcache_should_resp = !(s2_in.tlbMiss || s2_exception || s2_in.delayedLoadError || s2_uncache || s2_prf)
-  assert(!(s2_valid && (s2_dcache_should_resp && !io.dcache.resp.valid)), "DCache response got lost")
-
-  // fast replay require
-  val s2_dcache_fast_rep = (s2_mq_nack || !s2_dcache_miss && (s2_bank_conflict || s2_wpu_pred_fail))
-  val s2_nuke_fast_rep   = !s2_mq_nack &&
-                           !s2_dcache_miss &&
-                           !s2_bank_conflict &&
-                           !s2_wpu_pred_fail &&
-                           s2_nuke
-
-  val s2_fast_rep = !s2_in.isFastReplay &&
-                    !s2_tlb_miss &&
-                    !s2_fwd_fail &&
-                    (!s2_mem_amb && s2_dcache_fast_rep || s2_nuke_fast_rep && s2_nuke_first) &&
-                    s2_troublem
-
-  // need allocate new entry
-  val s2_dcache_no_query = !s2_dcache_miss && (s2_bank_conflict || s2_wpu_pred_fail)
-  val s2_can_query = !(s2_dcache_no_query || s2_in.rep_info.nuke) && s2_troublem
-
-  val s2_data_fwded = s2_dcache_miss && s2_full_fwd
-
-  // For misaligned, we will keep the misaligned exception at S2 and before.
-  // Here a judgement is made as to whether a misaligned exception needs to actually be generated.
-  // We will generate misaligned exceptions at mmio.
-  val s2_real_exceptionVec = WireInit(s2_exception_vec)
-  s2_real_exceptionVec(loadAddrMisaligned) := (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_uncache && !s2_isvec
-  s2_real_exceptionVec(loadAccessFault) := s2_exception_vec(loadAccessFault) ||
-    s2_fwd_frm_d_chan && s2_d_denied ||
-    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_denied
-  s2_real_exceptionVec(hardwareError) := s2_exception_vec(hardwareError) ||
-    s2_fwd_frm_d_chan && s2_d_corrupt && !s2_d_denied ||
-    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_corrupt && !s2_mshr_denied
-
-  val s2_real_exception = s2_vecActive &&
-    (s2_trigger_debug_mode || ExceptionNO.selectByFu(s2_real_exceptionVec, LduCfg).asUInt.orR)
-
-  val s2_fwd_vp_match_invalid = io.lsq.forward.matchInvalid || io.sbuffer.matchInvalid || io.ubuffer.matchInvalid
-  val s2_vp_match_fail = s2_fwd_vp_match_invalid && s2_troublem
-  val s2_safe_wakeup = !s2_out.rep_info.need_rep && !s2_mmio && (!s2_in.nc || s2_nc_with_data) && !s2_mis_align && !s2_real_exception // don't need to replay and is not a mmio\misalign no data
-  val s2_safe_writeback = s2_real_exception || s2_safe_wakeup || s2_vp_match_fail
-
-  // ld-ld violation require
-  /**
-    * In order to ensure timing, the RAR enqueue conditions need to be compromised, worst source of timing from pmp and missQueue.
-    *   * if LoadQueueRARSize == VirtualLoadQueueSize, just need to exclude prefetching.
-    *   * if LoadQueueRARSize < VirtualLoadQueueSize, need to consider the situation of s2_can_query
-    */
-  if (LoadQueueRARSize == VirtualLoadQueueSize) {
-    io.lsq.ldld_nuke_query.req.valid           := s2_valid && !s2_prf
-  } else {
-    io.lsq.ldld_nuke_query.req.valid           := s2_valid && s2_can_query
-  }
-  io.lsq.ldld_nuke_query.req.bits.uop        := s2_in.uop
-  io.lsq.ldld_nuke_query.req.bits.mask       := s2_in.mask
-  io.lsq.ldld_nuke_query.req.bits.paddr      := s2_in.paddr
-  io.lsq.ldld_nuke_query.req.bits.data_valid := Mux(s2_full_fwd || s2_fwd_data_valid || s2_nc_with_data, true.B, !s2_dcache_miss)
-  io.lsq.ldld_nuke_query.req.bits.is_nc := s2_nc_with_data
-
-  // st-ld violation require
-  io.lsq.stld_nuke_query.req.valid           := s2_valid && s2_can_query
-  io.lsq.stld_nuke_query.req.bits.uop        := s2_in.uop
-  io.lsq.stld_nuke_query.req.bits.mask       := s2_in.mask
-  io.lsq.stld_nuke_query.req.bits.paddr      := s2_in.paddr
-  io.lsq.stld_nuke_query.req.bits.data_valid := Mux(s2_full_fwd || s2_fwd_data_valid || s2_nc_with_data, true.B, !s2_dcache_miss)
-  io.lsq.stld_nuke_query.req.bits.is_nc := s2_nc_with_data
-
-  // merge forward result
-  // lsq has higher priority than sbuffer
-  val s2_fwd_mask = Wire(Vec((VLEN/8), Bool()))
-  val s2_fwd_data = Wire(Vec((VLEN/8), UInt(8.W)))
-  s2_full_fwd := ((~s2_fwd_mask.asUInt).asUInt & s2_in.mask) === 0.U && !io.lsq.forward.dataInvalid
-  // generate XLEN/8 Muxs
-  for (i <- 0 until VLEN / 8) {
-    s2_fwd_mask(i) := io.lsq.forward.forwardMask(i) || io.sbuffer.forwardMask(i) || io.ubuffer.forwardMask(i)
-    s2_fwd_data(i) :=
-      Mux(io.lsq.forward.forwardMask(i), io.lsq.forward.forwardData(i),
-      Mux(s2_nc_with_data, io.ubuffer.forwardData(i),
-      io.sbuffer.forwardData(i)))
-  }
-
-  XSDebug(s2_fire, "[FWD LOAD RESP] pc %x fwd %x(%b) + %x(%b)\n",
-    s2_in.uop.pc,
-    io.lsq.forward.forwardData.asUInt, io.lsq.forward.forwardMask.asUInt,
-    s2_in.forwardData.asUInt, s2_in.forwardMask.asUInt
-  )
-
-  //
-  s2_out                     := s2_in
-  s2_out.uop.fpWen           := s2_in.uop.fpWen
-  s2_out.nc                  := s2_in.nc
-  s2_out.mmio                := s2_mmio
-  s2_out.memBackTypeMM       := s2_memBackTypeMM
-  s2_out.isMisalign          := s2_isMisalign
-  s2_out.uop.flushPipe       := false.B
-  s2_out.uop.exceptionVec    := s2_real_exceptionVec
-  s2_out.forwardMask         := s2_fwd_mask
-  s2_out.forwardData         := s2_fwd_data
-  s2_out.handledByMSHR       := s2_cache_handled
-  s2_out.miss                := s2_dcache_miss && s2_troublem
-  s2_out.feedbacked          := false.B
-  s2_out.uop.vpu.vstart      := Mux(s2_in.isLoadReplay || s2_in.isFastReplay, s2_in.uop.vpu.vstart, s2_in.vecVaddrOffset >> s2_in.uop.vpu.veew)
-
-  // Generate replay signal caused by:
-  // * st-ld violation check
-  // * tlb miss
-  // * dcache replay
-  // * forward data invalid
-  // * dcache miss
-  s2_out.rep_info.mem_amb         := s2_mem_amb && s2_troublem
-  s2_out.rep_info.tlb_miss        := s2_tlb_miss && s2_troublem
-  s2_out.rep_info.fwd_fail        := s2_fwd_fail && s2_troublem
-  s2_out.rep_info.dcache_rep      := s2_mq_nack && s2_troublem
-  s2_out.rep_info.dcache_miss     := s2_dcache_miss && s2_troublem
-  s2_out.rep_info.bank_conflict   := s2_bank_conflict && s2_troublem
-  s2_out.rep_info.wpu_fail        := s2_wpu_pred_fail && s2_troublem
-  s2_out.rep_info.rar_nack        := s2_rar_nack && s2_troublem
-  s2_out.rep_info.raw_nack        := s2_raw_nack && s2_troublem
-  s2_out.rep_info.nuke            := s2_nuke && s2_troublem
-  s2_out.rep_info.full_fwd        := s2_data_fwded
-  s2_out.rep_info.data_inv_sq_idx := io.lsq.forward.dataInvalidSqIdx
-  s2_out.rep_info.addr_inv_sq_idx := io.lsq.forward.addrInvalidSqIdx
-  s2_out.rep_info.rep_carry       := io.dcache.resp.bits.replayCarry
-  s2_out.rep_info.mshr_id         := io.dcache.resp.bits.mshr_id
-  s2_out.rep_info.last_beat       := s2_in.paddr(log2Up(refillBytes))
-  s2_out.rep_info.debug           := s2_in.uop.perfDebugInfo
-  s2_out.rep_info.tlb_id          := io.tlb_hint.id
-  s2_out.rep_info.tlb_full        := io.tlb_hint.full
-
-  // if forward fail, replay this inst from fetch
-  val debug_fwd_fail_rep = s2_fwd_fail && !s2_troublem && !s2_in.tlbMiss
-  // if ld-ld violation is detected, replay from this inst from fetch
-  val debug_ldld_nuke_rep = false.B // s2_ldld_violation && !s2_mmio && !s2_is_prefetch && !s2_in.tlbMiss
-
-  io.ldCancel.ld1Cancel := false.B
-
-  // RegNext prefetch train for better timing
-  // ** Now, prefetch train is valid at load s3 **
-  // s2_un_access_exception can guarantee the physical address is valid
-  val s2_prefetch_train_valid = s2_fire && s2_in.isFirstIssue && !s2_actually_uncache && !s2_un_access_exception
-  io.prefetch_train.valid := GatedValidRegNext(s2_prefetch_train_valid)
-  io.prefetch_train.bits.fromLsPipelineBundle(s2_in, latch = true, enable = s2_prefetch_train_valid)
-  io.prefetch_train.bits.miss := RegEnable(io.dcache.resp.bits.miss, s2_prefetch_train_valid) // TODO: use trace with bank conflict?
-  io.prefetch_train.bits.meta_prefetch := RegEnable(io.dcache.resp.bits.meta_prefetch, s2_prefetch_train_valid)
-  io.prefetch_train.bits.meta_access := RegEnable(io.dcache.resp.bits.meta_access, s2_prefetch_train_valid)
-  io.prefetch_train.bits.is_from_hw_pf := RegNext(s2_hw_prf)
-  io.prefetch_train.bits.refillLatency := RegEnable(io.dcache.resp.bits.refill_latency, s2_prefetch_train_valid)
-  io.prefetch_train.bits.isFinalSplit := false.B
-  io.prefetch_train.bits.misalignWith16Byte := false.B
-  io.prefetch_train.bits.misalignNeedWakeUp := false.B
-  io.prefetch_train.bits.updateAddrValid := false.B
-  io.prefetch_train.bits.isMisalign := false.B
-  io.prefetch_train.bits.hasException := false.B
-  io.s1_prefetch_spec := s1_fire && s1_in.isFirstIssue
-  io.s2_prefetch_spec := s2_prefetch_train_valid
-
-  if (env.FPGAPlatform){
-    io.dcache.s0_pc := DontCare
-    io.dcache.s1_pc := DontCare
-    io.dcache.s2_pc := DontCare
-  }else{
-    io.dcache.s0_pc := s0_out.uop.pc
-    io.dcache.s1_pc := s1_out.uop.pc
-    io.dcache.s2_pc := s2_out.uop.pc
-  }
-  io.dcache.s2_kill := s2_pmp.ld || s2_pmp.st || s2_actually_uncache || s2_kill
-
-  val s1_ld_left_fire = s1_valid && !s1_kill && s2_ready
-  val s2_ld_valid_dup = RegInit(0.U(6.W))
-  s2_ld_valid_dup := 0x0.U(6.W)
-  when (s1_ld_left_fire && !s1_out.isHWPrefetch) { s2_ld_valid_dup := 0x3f.U(6.W) }
-  when (s1_kill || s1_out.isHWPrefetch) { s2_ld_valid_dup := 0x0.U(6.W) }
-  assert(RegNext((s2_valid === s2_ld_valid_dup(0)) || RegNext(s1_out.isHWPrefetch)))
-
-  // Pipeline
-  // --------------------------------------------------------------------------------
-  // stage 3
-  // --------------------------------------------------------------------------------
-  // writeback and update load queue
-  val s3_valid        = GatedValidRegNext(s2_valid && !s2_out.isHWPrefetch && !s2_out.uop.robIdx.needFlush(io.redirect))
-  val s3_in           = RegEnable(s2_out, s2_fire)
-  val s3_out          = Wire(Valid(new LqWriteBundle))
-  val s3_wb           = Wire(new ExuOutput(param))
-  val s3_dcache_rep   = RegEnable(s2_dcache_fast_rep && s2_troublem, false.B, s2_fire)
-  val s3_ld_valid_dup = RegEnable(s2_ld_valid_dup, s2_fire)
-  val s3_fast_rep     = Wire(Bool())
-  val s3_nc_with_data = RegNext(s2_nc_with_data)
-  val s3_troublem     = GatedValidRegNext(s2_troublem)
-  val s3_kill         = s3_in.uop.robIdx.needFlush(io.redirect)
-  val s3_vecout       = Wire(new OnlyVecExuOutput)
-  val s3_vecActive    = RegEnable(s2_out.vecActive, true.B, s2_fire)
-  val s3_isvec        = RegEnable(s2_out.isvec, false.B, s2_fire)
-  val s3_vec_alignedType = RegEnable(s2_out.alignedType, s2_fire)
-  val s3_vec_mBIndex     = RegEnable(s2_out.mbIndex, s2_fire)
-  val s3_frm_mabuf       = s3_in.isFrmMisAlignBuf
-  val s3_mmio_req     = RegNext(s2_mmio_req)
-  val s3_pdest        = RegNext(Mux(s2_valid, s2_out.uop.pdest, s2_mmio_req.bits.pdest))
-  val s3_rfWen        = RegEnable(Mux(s2_valid, s2_out.uop.rfWen, s2_mmio_req.bits.intWen.get), s2_valid || s2_mmio_req.valid)
-  val s3_fpWen        = RegEnable(Mux(s2_valid, s2_out.uop.fpWen, s2_mmio_req.bits.fpWen.get), s2_valid || s2_mmio_req.valid)
-  val s3_data_select  = RegEnable(s2_data_select, 0.U(s2_data_select.getWidth.W), s2_fire)
-  val s3_data_select_by_offset = RegEnable(s2_data_select_by_offset, 0.U.asTypeOf(s2_data_select_by_offset), s2_fire)
-  val s3_hw_err   =
-      if (EnableAccurateLoadError) {
-        io.dcache.resp.bits.error_delayed && GatedValidRegNext(io.csrCtrl.cache_error_enable) && s3_troublem
-      } else {
-        WireInit(false.B)
-      }
-  val s3_safe_wakeup  = RegEnable(s2_safe_wakeup, s2_fire)
-  val s3_safe_writeback = RegEnable(s2_safe_writeback, s2_fire) || s3_hw_err
-  val s3_exception = RegEnable(s2_real_exception, s2_fire)
-  val s3_mis_align = RegEnable(s2_mis_align, s2_fire) && !s3_exception
-  val s3_misalign_can_go = RegEnable(!isAfter(s2_out.uop.lqIdx, io.lsq.lqDeqPtr) || io.misalign_allow_spec, s2_fire)
-  val s3_trigger_debug_mode = RegEnable(s2_trigger_debug_mode, false.B, s2_fire)
-
-  // TODO: Fix vector load merge buffer nack
-  val s3_vec_mb_nack  = Wire(Bool())
-  s3_vec_mb_nack     := false.B
-  XSError(s3_valid && s3_vec_mb_nack, "Merge buffer should always accept vector loads!")
-
-  s3_ready := !s3_valid || s3_kill || io.ldout.ready
-
-
-  // forwrad last beat
-  val s3_fast_rep_canceled = io.replay.valid && io.replay.bits.forward_tlDchannel || io.misalign_ldin.valid || !io.dcache.req.ready
-
-  val s3_can_enter_lsq_valid = s3_valid && (!s3_fast_rep || s3_fast_rep_canceled) && !s3_in.feedbacked
-  io.lsq.ldin.valid := s3_can_enter_lsq_valid
-  // TODO: check this --by hx
-  // io.lsq.ldin.valid := s3_valid && (!s3_fast_rep || !io.fast_rep_out.ready) && !s3_in.feedbacked && !s3_in.lateKill
-  io.lsq.ldin.bits := s3_in
-  io.lsq.ldin.bits.miss := s3_in.miss
-
-  // connect to misalignBuffer
-  val toMisalignBufferValid = s3_can_enter_lsq_valid && s3_mis_align && !s3_frm_mabuf
-  io.misalign_enq.req.valid := toMisalignBufferValid && s3_misalign_can_go
-  io.misalign_enq.req.bits  := s3_in
-  io.misalign_enq.revoke := false.B
-
-  /* <------- DANGEROUS: Don't change sequence here ! -------> */
-  io.lsq.ldin.bits.nc_with_data := s3_nc_with_data
-  io.lsq.ldin.bits.data_wen_dup := s3_ld_valid_dup.asBools
-  io.lsq.ldin.bits.replacementUpdated := io.dcache.resp.bits.replacementUpdated
-  io.lsq.ldin.bits.missDbUpdated := GatedValidRegNext(s2_fire && s2_in.hasROBEntry && !s2_in.tlbMiss && !s2_in.missDbUpdated)
-  io.lsq.ldin.bits.updateAddrValid := !s3_mis_align && (!s3_frm_mabuf || s3_in.isFinalSplit) || s3_exception
-  io.lsq.ldin.bits.hasException := false.B
-
-  io.lsq.ldin.bits.dcacheRequireReplay  := s3_dcache_rep
-
-  val s3_vp_match_fail = GatedValidRegNext(s2_fwd_vp_match_invalid) && s3_troublem
-  val s3_rep_frm_fetch = s3_vp_match_fail
-  val s3_ldld_rep_inst =
-      io.lsq.ldld_nuke_query.resp.valid &&
-      io.lsq.ldld_nuke_query.resp.bits.rep_frm_fetch &&
-      GatedValidRegNext(io.csrCtrl.ldld_vio_check_enable)
-  val s3_flushPipe = s3_ldld_rep_inst
-
-  val s3_lrq_rep_info = WireInit(s3_in.rep_info)
-  s3_lrq_rep_info.misalign_nack := toMisalignBufferValid && !(io.misalign_enq.req.ready && s3_misalign_can_go)
-  val s3_lrq_sel_rep_cause = PriorityEncoderOH(s3_lrq_rep_info.cause.asUInt)
-  val s3_replayqueue_rep_cause = WireInit(0.U.asTypeOf(s3_in.rep_info.cause))
-
-  val s3_mab_rep_info = WireInit(s3_in.rep_info)
-  val s3_mab_sel_rep_cause = PriorityEncoderOH(s3_mab_rep_info.cause.asUInt)
-  val s3_misalign_rep_cause = WireInit(0.U.asTypeOf(s3_in.rep_info.cause))
-
-  s3_misalign_rep_cause := VecInit(s3_mab_sel_rep_cause.asBools)
-
-  when (s3_rep_frm_fetch || s3_frm_mabuf) {
-    s3_replayqueue_rep_cause := 0.U.asTypeOf(s3_lrq_rep_info.cause.cloneType)
-  } .otherwise {
-    s3_replayqueue_rep_cause := VecInit(s3_lrq_sel_rep_cause.asBools)
-
-  }
-  io.lsq.ldin.bits.rep_info.cause := s3_replayqueue_rep_cause
-
-  // Int load, if hit, will be writebacked at s3
-  s3_out.valid := s3_valid && s3_safe_writeback && !toMisalignBufferValid
-  s3_out.bits := s3_in
-  s3_out.bits.uop.exceptionVec(loadAccessFault) := (s3_in.uop.exceptionVec(loadAccessFault) || io.dcache.resp.bits.tl_error_delayed.tl_denied) && s3_vecActive
-  s3_out.bits.uop.exceptionVec(hardwareError) := (s3_in.uop.exceptionVec(hardwareError) || s3_hw_err ||
-                                                 io.dcache.resp.bits.tl_error_delayed.tl_corrupt && !io.dcache.resp.bits.tl_error_delayed.tl_denied) && s3_vecActive
-  s3_out.bits.uop.flushPipe   := false.B
-  s3_out.bits.uop.replayInst  := false.B
-
-  // Vector load, writeback to merge buffer
-  // TODO: Add assertion in merge buffer, merge buffer must accept vec load writeback
-  s3_vecout.isvec             := s3_isvec
-  s3_vecout.vecdata           := 0.U // Data will be assigned later
-  s3_vecout.mask              := s3_in.mask
-  // s3_vecout.rob_idx_valid     := s3_in.rob_idx_valid
-  // s3_vecout.inner_idx         := s3_in.inner_idx
-  // s3_vecout.rob_idx           := s3_in.rob_idx
-  // s3_vecout.offset            := s3_in.offset
-  s3_vecout.reg_offset        := s3_in.reg_offset
-  s3_vecout.vecActive         := s3_vecActive
-  s3_vecout.is_first_ele      := s3_in.is_first_ele
-  // s3_vecout.uopQueuePtr       := DontCare // uopQueuePtr is already saved in flow queue
-  // s3_vecout.flowPtr           := s3_in.flowPtr
-  s3_vecout.elemIdx           := s3_in.elemIdx // elemIdx is already saved in flow queue // TODO:
-  s3_vecout.elemIdxInsideVd   := s3_in.elemIdxInsideVd
-  s3_vecout.trigger           := s3_in.uop.trigger
-  s3_vecout.vstart            := s3_in.uop.vpu.vstart
-  s3_vecout.vecTriggerMask    := s3_in.vecTriggerMask
-  val s3_usSecondInv          = s3_in.usSecondInv
-
-  val s3_frm_mis_flush     = s3_frm_mabuf &&
-    (io.misalign_ldout.bits.rep_info.fwd_fail || io.misalign_ldout.bits.rep_info.mem_amb || io.misalign_ldout.bits.rep_info.nuke
-      || io.misalign_ldout.bits.rep_info.rar_nack || io.misalign_ldout.bits.rep_info.raw_nack)
-
-  io.rollback.valid := s3_valid && (s3_rep_frm_fetch || s3_flushPipe || s3_frm_mis_flush) && !s3_exception
-  io.rollback.bits           := DontCare
-  io.rollback.bits.isRVC     := s3_out.bits.uop.isRVC
-  io.rollback.bits.robIdx    := s3_out.bits.uop.robIdx
-  io.rollback.bits.ftqIdx    := s3_out.bits.uop.ftqPtr
-  io.rollback.bits.ftqOffset := s3_out.bits.uop.ftqOffset
-  io.rollback.bits.level     := Mux(s3_rep_frm_fetch || s3_frm_mis_flush, RedirectLevel.flush, RedirectLevel.flushAfter)
-  io.rollback.bits.target    := s3_out.bits.uop.pc
-  io.rollback.bits.debug_runahead_checkpoint_id := s3_out.bits.uop.perfDebugInfo.runahead_checkpoint_id
-  /* <------- DANGEROUS: Don't change sequence here ! -------> */
-
-  io.lsq.ldin.bits.uop := s3_out.bits.uop
-//  io.lsq.ldin.bits.uop.exceptionVec(loadAddrMisaligned) := Mux(s3_in.onlyMisalignException, false.B, s3_in.uop.exceptionVec(loadAddrMisaligned))
-
-  val s3_revoke = s3_exception || io.lsq.ldin.bits.rep_info.need_rep || s3_mis_align || (s3_frm_mabuf && io.misalign_ldout.bits.rep_info.need_rep)
-  io.lsq.ldld_nuke_query.revoke := s3_revoke
-  io.lsq.stld_nuke_query.revoke := s3_revoke
-
-  // feedback slow
-  s3_fast_rep := RegNext(s2_fast_rep)
-
-  val s3_fb_no_waiting = !s3_in.isLoadReplay &&
-                        (!(s3_fast_rep && !s3_fast_rep_canceled)) &&
-                        !s3_in.feedbacked
-
-  // feedback: scalar load will send feedback to RS
-  //           vector load will send signal to VL Merge Buffer, then send feedback at granularity of uops
-  io.feedback_slow.valid                 := s3_valid && s3_fb_no_waiting && !s3_isvec && !s3_frm_mabuf
-  io.feedback_slow.bits.hit              := !s3_lrq_rep_info.need_rep || io.lsq.ldin.ready
-  io.feedback_slow.bits.flushState       := s3_in.ptwBack
-  io.feedback_slow.bits.robIdx           := s3_in.uop.robIdx
-  io.feedback_slow.bits.sqIdx            := s3_in.uop.sqIdx
-  io.feedback_slow.bits.lqIdx            := s3_in.uop.lqIdx
-  io.feedback_slow.bits.sourceType       := RSFeedbackType.lrqFull
-  io.feedback_slow.bits.dataInvalidSqIdx := DontCare
-
-  // TODO: vector wakeup?
-  io.ldCancel.ld2Cancel := s3_valid && !s3_safe_wakeup && !s3_isvec
-
-  // LqWriteBundle -> ExuOutput
-  s3_wb.pdest := s3_out.bits.uop.pdest
-  s3_wb.robIdx := s3_out.bits.uop.robIdx
-  s3_wb.intWen.foreach(_ := s3_out.bits.uop.rfWen)
-  s3_wb.fpWen.foreach(_ := s3_out.bits.uop.fpWen)
-  s3_wb.vecWen.foreach(_ := s3_out.bits.uop.vecWen)
-  s3_wb.v0Wen.foreach(_ := s3_out.bits.uop.v0Wen)
-  s3_wb.vlWen.foreach(_ := s3_out.bits.uop.vlWen)
-  s3_wb.redirect.foreach(_ := 0.U.asTypeOf(ValidIO(new Redirect)))
-  s3_wb.exceptionVec.foreach(_ := s3_out.bits.uop.exceptionVec)
-  s3_wb.flushPipe.foreach(_ := s3_out.bits.uop.flushPipe)
-  s3_wb.replay.foreach(_ := s3_out.bits.uop.replayInst)
-  s3_wb.lqIdx.foreach(_ := s3_out.bits.uop.lqIdx)
-  s3_wb.sqIdx.foreach(_ := s3_out.bits.uop.sqIdx)
-  s3_wb.trigger.foreach(_ := s3_out.bits.uop.trigger)
-  s3_wb.isRVC.foreach(_ := s3_out.bits.uop.isRVC)
-  s3_wb.vls.foreach(x => {
-    x.vpu := s3_out.bits.uop.vpu
-    x.oldVdPsrc := s3_out.bits.uop.psrc(2)
-    x.vdIdx := DontCare
-    x.vdIdxInField := DontCare
-    x.isIndexed := VlduType.isIndexed(s3_out.bits.uop.fuOpType)
-    x.isMasked := VlduType.isMasked(s3_out.bits.uop.fuOpType)
-    x.isStrided := VlduType.isStrided(s3_out.bits.uop.fuOpType)
-    x.isWhole := VlduType.isWhole(s3_out.bits.uop.fuOpType)
-    x.isVecLoad := VlduType.isVecLd(s3_out.bits.uop.fuOpType)
-    x.isVlm := VlduType.isMasked(s3_out.bits.uop.fuOpType) && VlduType.isVecLd(s3_out.bits.uop.fuOpType)
-  })
-  s3_wb.isFromLoadUnit.foreach(_ := true.B)
-  s3_wb.debug.isMMIO := s3_in.mmio
-  s3_wb.debug.isNCIO := s3_in.nc && !s3_in.memBackTypeMM
-  s3_wb.debug.isPerfCnt := false.B
-  s3_wb.debug.paddr := s3_in.paddr
-  s3_wb.debug.vaddr := s3_in.vaddr
-  s3_wb.perfDebugInfo.foreach(_ := s3_out.bits.uop.perfDebugInfo)
-  s3_wb.debug_seqNum.foreach(_  := s3_out.bits.uop.debug_seqNum)
-
-  val s3_ld_wb_meta = Wire(new ExuOutput(param))
-  s3_ld_wb_meta := Mux(s3_valid, s3_wb, s3_mmio_req.bits)
-
-  // data from load queue refill
-  val s3_ld_raw_data_frm_mmio = RegNextN(io.lsq.ld_raw_data, 3)
-  val s3_merged_data_frm_mmio = s3_ld_raw_data_frm_mmio.mergedData()
-  val s3_picked_data_frm_mmio = LookupTree(s3_ld_raw_data_frm_mmio.addrOffset, List(
-    "b000".U -> s3_merged_data_frm_mmio(63,  0),
-    "b001".U -> s3_merged_data_frm_mmio(63,  8),
-    "b010".U -> s3_merged_data_frm_mmio(63, 16),
-    "b011".U -> s3_merged_data_frm_mmio(63, 24),
-    "b100".U -> s3_merged_data_frm_mmio(63, 32),
-    "b101".U -> s3_merged_data_frm_mmio(63, 40),
-    "b110".U -> s3_merged_data_frm_mmio(63, 48),
-    "b111".U -> s3_merged_data_frm_mmio(63, 56)
-  ))
-  val s3_ld_data_frm_mmio = rdataHelper(s3_ld_raw_data_frm_mmio.uop, s3_picked_data_frm_mmio)
-  s3_ld_wb_meta.data := Mux(s3_valid, s3_wb.data, VecInit(Seq.fill(param.wbPathNum)(s3_ld_data_frm_mmio)))
-
-  /* data from pipe, which forward from respectively
-   *  dcache hit: [D channel, mshr, sbuffer, sq]
-   *  nc_with_data: [sq]
-   */
-
-  val s2_ld_data_frm_nc = shiftDataToHigh(s2_out.paddr, s2_out.data)
-  val s2_ld_raw_data_frm_pipe = Wire(new LoadDataFromDcacheBundle)
-  s2_ld_raw_data_frm_pipe.respDcacheData       := Mux(s2_nc_with_data, s2_ld_data_frm_nc, io.dcache.resp.bits.data)
-  s2_ld_raw_data_frm_pipe.forward_D            := s2_fwd_frm_d_chan && !s2_nc_with_data
-  s2_ld_raw_data_frm_pipe.forwardData_D        := s2_fwd_data_frm_d_chan
-  s2_ld_raw_data_frm_pipe.forward_mshr         := s2_fwd_frm_mshr && !s2_nc_with_data
-  s2_ld_raw_data_frm_pipe.forwardData_mshr     := s2_fwd_data_frm_mshr
-  s2_ld_raw_data_frm_pipe.forward_result_valid := s2_fwd_data_valid
-
-  s2_ld_raw_data_frm_pipe.forwardMask          := s2_fwd_mask
-  s2_ld_raw_data_frm_pipe.forwardData          := s2_fwd_data
-  s2_ld_raw_data_frm_pipe.uop                  := s2_out.uop
-  s2_ld_raw_data_frm_pipe.addrOffset           := s2_out.paddr(3, 0)
-
-  val s2_ld_raw_data_frm_tlD = s2_ld_raw_data_frm_pipe.mergeTLData()
-  val s2_merged_data_frm_pipe = s2_ld_raw_data_frm_pipe.mergeLsqFwdData(s2_ld_raw_data_frm_tlD)
-  val s3_merged_data_frm_pipe = RegEnable(s2_merged_data_frm_pipe, s2_fire)
-
-  // duplicate reg for ldout and vecldout
-  private val LdDataDup = 3
-  require(LdDataDup >= 2)
-
-  val s3_data_frm_pipe = VecInit((0 until LdDataDup).map(i => {
-    VecInit(Seq(
-      s3_merged_data_frm_pipe(63,      0),
-      s3_merged_data_frm_pipe(71,      8),
-      s3_merged_data_frm_pipe(79,     16),
-      s3_merged_data_frm_pipe(87,     24),
-      s3_merged_data_frm_pipe(95,     32),
-      s3_merged_data_frm_pipe(103,    40),
-      s3_merged_data_frm_pipe(111,    48),
-      s3_merged_data_frm_pipe(119,    56),
-      s3_merged_data_frm_pipe(127,    64),
-      s3_merged_data_frm_pipe(127,    72),
-      s3_merged_data_frm_pipe(127,    80),
-      s3_merged_data_frm_pipe(127,    88),
-      s3_merged_data_frm_pipe(127,    96),
-      s3_merged_data_frm_pipe(127,   104),
-      s3_merged_data_frm_pipe(127,   112),
-      s3_merged_data_frm_pipe(127,   120),
-    ))
-  }))
-  val s3_picked_data_frm_pipe = VecInit((0 until LdDataDup).map(i => {
-    Mux1H(s3_data_select_by_offset, s3_data_frm_pipe(i))
-  }))
-  val s3_ld_data_frm_pipe = VecInit((0 until LdDataDup).map(i => {
-    newRdataHelper(s3_data_select, s3_picked_data_frm_pipe(i))
-  }))
-
-  // FIXME: add 1 cycle delay ?
-  // io.lsq.uncache.ready := !s3_valid
-  val s3_ldout_valid  = s3_mmio_req.valid ||
-                        s3_out.valid && RegNext(!s2_out.isvec && !s2_out.isFrmMisAlignBuf)
-  s3_wb.data := VecInit(Seq.fill(param.wbPathNum)(s3_ld_data_frm_pipe(0)))
-
-  io.ldout.valid       := s3_ldout_valid
-  io.ldout.bits        := s3_ld_wb_meta
-  io.ldout.bits.isFromLoadUnit.foreach(_ := true.B)
-
-  XSError(s3_valid && s3_vecout.isvec && s3_in.vecActive && !s3_vecout.mask.orR, "In vecActive, mask complement should not be 0")
-  // TODO: check this --hx
-  // io.ldout.valid       := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) && !s3_vecout.isvec ||
-  //   io.lsq.uncache.valid && !io.lsq.uncache.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid && !io.lsq.uncache.bits.isVls
-  //  io.ldout.bits.data   := Mux(s3_out.valid, s3_ld_data_frm_pipe, s3_ld_data_frm_mmio)
-  //  io.ldout.valid       := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) ||
-  //                         s3_mmio_req.valid && !s3_mmio_req.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid
-
-  // s3 load fast replay
-  io.fast_rep_out.valid := s3_valid && s3_fast_rep
-  io.fast_rep_out.bits := s3_in
-  io.fast_rep_out.bits.lateKill := s3_rep_frm_fetch
-  io.fast_rep_out.bits.delayedLoadError := s3_hw_err
-
-  val vecFeedback = s3_valid && s3_fb_no_waiting && s3_lrq_rep_info.need_rep && !io.lsq.ldin.ready && s3_isvec
-
-  // vector output
-  io.vecldout.bits.alignedType := s3_vec_alignedType
-  // vec feedback
-  io.vecldout.bits.vecFeedback := vecFeedback
-  // TODO: VLSU, uncache data logic
-  val vecdata = rdataVecHelper(s3_vec_alignedType(1,0), s3_picked_data_frm_pipe(1))
-  io.vecldout.bits.vecdata.get := Mux(
-    s3_in.misalignWith16Byte,
-    s3_picked_data_frm_pipe(1),
-    Mux(
-      s3_in.is128bit,
-      s3_merged_data_frm_pipe,
-      vecdata
-    )
-  )
-  io.vecldout.bits.isvec := s3_vecout.isvec
-  io.vecldout.bits.elemIdx := s3_vecout.elemIdx
-  io.vecldout.bits.elemIdxInsideVd.get := s3_vecout.elemIdxInsideVd
-  io.vecldout.bits.mask := s3_vecout.mask
-  io.vecldout.bits.hasException := s3_exception
-  io.vecldout.bits.reg_offset.get := s3_vecout.reg_offset
-  io.vecldout.bits.usSecondInv := s3_usSecondInv
-  io.vecldout.bits.mBIndex := s3_vec_mBIndex
-  io.vecldout.bits.hit := !s3_lrq_rep_info.need_rep || io.lsq.ldin.ready
-  io.vecldout.bits.sourceType := RSFeedbackType.lrqFull
-  io.vecldout.bits.trigger := s3_vecout.trigger
-  io.vecldout.bits.flushState := DontCare
-  io.vecldout.bits.exceptionVec := ExceptionNO.selectByFu(s3_out.bits.uop.exceptionVec, VlduCfg)
-  io.vecldout.bits.vaddr := s3_in.fullva
-  io.vecldout.bits.vaNeedExt := s3_in.vaNeedExt
-  io.vecldout.bits.gpaddr := s3_in.gpaddr
-  io.vecldout.bits.isForVSnonLeafPTE := s3_in.isForVSnonLeafPTE
-  io.vecldout.bits.mmio := DontCare
-  io.vecldout.bits.vstart := s3_vecout.vstart
-  io.vecldout.bits.vecTriggerMask := s3_vecout.vecTriggerMask
-  io.vecldout.bits.nc := DontCare
-
-  io.vecldout.valid := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) && s3_vecout.isvec && !s3_mis_align && !s3_frm_mabuf //||
-  // TODO: check this, why !io.lsq.uncache.bits.isVls before?
-  // Now vector instruction don't support mmio.
-    // io.lsq.uncache.valid && !io.lsq.uncache.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid && io.lsq.uncache.bits.isVls
-    //io.lsq.uncache.valid && !io.lsq.uncache.bits.uop.robIdx.needFlush(io.redirect) && !s3_out.valid && !io.lsq.uncache.bits.isVls
-
-  io.misalign_ldout.valid     := s3_valid && (!s3_fast_rep || s3_fast_rep_canceled) && s3_frm_mabuf || s3_misalign_wakeup_req.valid
-  io.misalign_ldout.bits      := Mux(s3_misalign_wakeup_req.valid, s3_misalign_wakeup_req.bits, io.lsq.ldin.bits)
-  io.misalign_ldout.bits.data := s3_picked_data_frm_pipe(2)
-  io.misalign_ldout.bits.rep_info.cause := Mux(s3_misalign_wakeup_req.valid, 0.U.asTypeOf(s3_in.rep_info.cause), s3_misalign_rep_cause)
-
-  // s1
-  io.debug_ls.s1_robIdx := s1_in.uop.robIdx.value
-  io.debug_ls.s1_isLoadToLoadForward := false.B
-  io.debug_ls.s1_isTlbFirstMiss := s1_fire && s1_tlb_miss && s1_in.isFirstIssue
-  // s2
-  io.debug_ls.s2_robIdx := s2_in.uop.robIdx.value
-  io.debug_ls.s2_isBankConflict := s2_fire && (!s2_kill && s2_bank_conflict)
-  io.debug_ls.s2_isDcacheFirstMiss := s2_fire && io.dcache.resp.bits.miss && s2_in.isFirstIssue
-  io.debug_ls.s2_isForwardFail := s2_fire && s2_fwd_fail
-  // s3
-  io.debug_ls.s3_robIdx := s3_in.uop.robIdx.value
-  io.debug_ls.s3_isReplayFast := s3_valid && s3_fast_rep && !s3_fast_rep_canceled
-  io.debug_ls.s3_isReplayRS := io.feedback_slow.valid && !io.feedback_slow.bits.hit
-  io.debug_ls.s3_isReplaySlow := io.lsq.ldin.valid && io.lsq.ldin.bits.rep_info.need_rep
-  io.debug_ls.s3_isReplay := s3_valid && s3_lrq_rep_info.need_rep // include fast+slow+rs replay
-  io.debug_ls.replayCause := s3_lrq_rep_info.cause
-  io.debug_ls.replayCnt := 1.U
-
-  // Topdown
-  io.lsTopdownInfo.s1.robIdx          := s1_in.uop.robIdx.value
-  io.lsTopdownInfo.s1.vaddr_valid     := s1_valid && s1_in.hasROBEntry
-  io.lsTopdownInfo.s1.vaddr_bits      := s1_vaddr
-  io.lsTopdownInfo.s2.robIdx          := s2_in.uop.robIdx.value
-  io.lsTopdownInfo.s2.paddr_valid     := s2_fire && s2_in.hasROBEntry && !s2_in.tlbMiss
-  io.lsTopdownInfo.s2.paddr_bits      := s2_in.paddr
-  io.lsTopdownInfo.s2.first_real_miss := io.dcache.resp.bits.real_miss
-  io.lsTopdownInfo.s2.cache_miss_en   := s2_fire && s2_in.hasROBEntry && !s2_in.tlbMiss && !s2_in.missDbUpdated
-
-  // perf cnt
-  XSPerfAccumulate("s0_in_valid",                  io.ldin.valid)
-  XSPerfAccumulate("s0_in_block",                  io.ldin.valid && !io.ldin.fire)
-  XSPerfAccumulate("s0_vecin_valid",               io.vecldin.valid)
-  XSPerfAccumulate("s0_vecin_block",               io.vecldin.valid && !io.vecldin.fire)
-  XSPerfAccumulate("s0_in_fire_first_issue",       s0_valid && s0_sel_src.isFirstIssue)
-  XSPerfAccumulate("s0_lsq_replay_issue",          io.replay.fire)
-  XSPerfAccumulate("s0_lsq_replay_vecissue",       io.replay.fire && io.replay.bits.isvec)
-  XSPerfAccumulate("s0_ldu_fire_first_issue",      io.ldin.fire && s0_sel_src.isFirstIssue)
-  XSPerfAccumulate("s0_fast_replay_issue",         io.fast_rep_in.fire)
-  XSPerfAccumulate("s0_fast_replay_vecissue",      io.fast_rep_in.fire && io.fast_rep_in.bits.isvec)
-  XSPerfAccumulate("s0_stall_out",                 s0_valid && !s0_can_go)
-  XSPerfAccumulate("s0_stall_dcache",              s0_valid && !io.dcache.req.ready)
-  XSPerfAccumulate("s0_addr_spec_success",         s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) === io.ldin.bits.src(0)(VAddrBits-1, 12))
-  XSPerfAccumulate("s0_addr_spec_failed",          s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) =/= io.ldin.bits.src(0)(VAddrBits-1, 12))
-  XSPerfAccumulate("s0_addr_spec_success_once",    s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) === io.ldin.bits.src(0)(VAddrBits-1, 12) && s0_sel_src.isFirstIssue)
-  XSPerfAccumulate("s0_addr_spec_failed_once",     s0_fire && s0_dcache_vaddr(VAddrBits-1, 12) =/= io.ldin.bits.src(0)(VAddrBits-1, 12) && s0_sel_src.isFirstIssue)
-  XSPerfAccumulate("s0_vec_addr_vlen_aligned",     s0_fire && s0_sel_src.isvec && s0_dcache_vaddr(3, 0) === 0.U)
-  XSPerfAccumulate("s0_vec_addr_vlen_unaligned",   s0_fire && s0_sel_src.isvec && s0_dcache_vaddr(3, 0) =/= 0.U)
-  XSPerfAccumulate("s0_forward_tl_d_channel",      s0_out.forward_tlDchannel)
-  XSPerfAccumulate("s0_hardware_prefetch_fire",    s0_fire && s0_hw_prf_select)
-  XSPerfAccumulate("s0_software_prefetch_fire",    s0_fire && s0_sel_src.prf && s0_src_select_vec(int_iss_idx))
-  XSPerfAccumulate("s0_hardware_prefetch_blocked", io.prefetch_req.valid && !s0_hw_prf_select)
-  XSPerfAccumulate("s0_hardware_prefetch_total",   io.prefetch_req.valid)
-
-  XSPerfAccumulate("s3_rollback_total",             io.rollback.valid)
-  XSPerfAccumulate("s3_rep_frm_fetch_rollback",     io.rollback.valid && s3_rep_frm_fetch)
-  XSPerfAccumulate("s3_flushPipe_rollback",         io.rollback.valid && s3_flushPipe)
-  XSPerfAccumulate("s3_frm_mis_flush_rollback",     io.rollback.valid && s3_frm_mis_flush)
-
-  XSPerfAccumulate("s1_in_valid",                  s1_valid)
-  XSPerfAccumulate("s1_in_fire",                   s1_fire)
-  XSPerfAccumulate("s1_in_fire_first_issue",       s1_fire && s1_in.isFirstIssue)
-  XSPerfAccumulate("s1_tlb_miss",                  s1_fire && s1_tlb_miss)
-  XSPerfAccumulate("s1_tlb_miss_first_issue",      s1_fire && s1_tlb_miss && s1_in.isFirstIssue)
-  XSPerfAccumulate("s1_stall_out",                 s1_valid && !s1_can_go)
-  XSPerfAccumulate("s1_dly_err",                   s1_valid && s1_fast_rep_dly_err)
-
-  XSPerfAccumulate("s2_in_valid",                  s2_valid)
-  XSPerfAccumulate("s2_in_fire",                   s2_fire)
-  XSPerfAccumulate("s2_in_fire_first_issue",       s2_fire && s2_in.isFirstIssue)
-  XSPerfAccumulate("s2_dcache_miss",               s2_fire && io.dcache.resp.bits.miss)
-  XSPerfAccumulate("s2_dcache_miss_first_issue",   s2_fire && io.dcache.resp.bits.miss && s2_in.isFirstIssue)
-  XSPerfAccumulate("s2_dcache_real_miss_first_issue",   s2_fire && io.dcache.resp.bits.miss && s2_in.isFirstIssue)
-  XSPerfAccumulate("s2_full_forward",              s2_fire && s2_full_fwd)
-  XSPerfAccumulate("s2_dcache_miss_full_forward",  s2_fire && s2_dcache_miss)
-  XSPerfAccumulate("s2_fwd_frm_d_can",             s2_valid && s2_fwd_frm_d_chan)
-  XSPerfAccumulate("s2_fwd_frm_d_chan_or_mshr",    s2_valid && s2_fwd_frm_d_chan_or_mshr)
-  XSPerfAccumulate("s2_stall_out",                 s2_fire && !s2_can_go)
-  XSPerfAccumulate("s2_prefetch",                  s2_fire && s2_prf)
-  XSPerfAccumulate("s2_prefetch_ignored",          s2_fire && s2_prf && io.dcache.s2_mq_nack) // ignore prefetch for mshr full / miss req port conflict
-  XSPerfAccumulate("s2_prefetch_miss",             s2_fire && s2_prf && io.dcache.resp.bits.miss) // prefetch req miss in l1
-  XSPerfAccumulate("s2_prefetch_hit",              s2_fire && s2_prf && !io.dcache.resp.bits.miss) // prefetch req hit in l1
-  XSPerfAccumulate("s2_prefetch_accept",           s2_fire && s2_prf && io.dcache.resp.bits.miss && !io.dcache.s2_mq_nack) // prefetch a missed line in l1, and l1 accepted it
-  XSPerfAccumulate("s2_forward_req",               s2_fire && s2_in.forward_tlDchannel)
-  XSPerfAccumulate("s2_successfully_forward_channel_D", s2_fire && s2_fwd_frm_d_chan && s2_fwd_data_valid)
-  XSPerfAccumulate("s2_successfully_forward_mshr",      s2_fire && s2_fwd_frm_mshr && s2_fwd_data_valid)
-
-  XSPerfAccumulate("nc_ld_writeback", io.ldout.valid && s3_nc_with_data)
-  XSPerfAccumulate("nc_ld_exception", s3_valid && s3_nc_with_data && s3_in.uop.exceptionVec.reduce(_ || _))
-  XSPerfAccumulate("nc_ldld_vio", s3_valid && s3_nc_with_data && s3_ldld_rep_inst)
-  XSPerfAccumulate("nc_stld_vio", s3_valid && s3_nc_with_data && s3_in.rep_info.nuke)
-  XSPerfAccumulate("nc_ldld_vioNack", s3_valid && s3_nc_with_data && s3_in.rep_info.rar_nack)
-  XSPerfAccumulate("nc_stld_vioNack", s3_valid && s3_nc_with_data && s3_in.rep_info.raw_nack)
-  XSPerfAccumulate("nc_stld_fwd", s3_valid && s3_nc_with_data && RegNext(s2_full_fwd))
-  XSPerfAccumulate("nc_stld_fwdNotReady", s3_valid && s3_nc_with_data && RegNext(s2_mem_amb || s2_fwd_fail))
-  XSPerfAccumulate("nc_stld_fwdAddrMismatch", s3_valid && s3_nc_with_data && s3_vp_match_fail)
-
-  // bug lyq: some signals in perfEvents are no longer suitable for the current MemBlock design
-  // hardware performance counter
-  val perfEvents = Seq(
-    ("load_s0_in_fire         ", s0_fire                                                        ),
-    ("stall_dcache            ", s0_valid && s0_can_go && !io.dcache.req.ready                  ),
-    ("load_s1_in_fire         ", s0_fire                                                        ),
-    ("load_s1_tlb_miss        ", s1_fire && io.tlb.resp.bits.miss                               ),
-    ("load_s2_in_fire         ", s1_fire                                                        ),
-    ("load_s2_dcache_miss     ", s2_fire && io.dcache.resp.bits.miss                            ),
-    ("l1D_load_hw_prf_access  ", s2_fire && s2_hw_prf                                           ),// Only hw prf
-    ("l1D_load_hw_prf_miss    ", s2_fire && s2_hw_prf && io.dcache.resp.bits.miss               ) // Only hw prf
-  )
-  generatePerfEvent()
-
-  if (backendParams.debugEn){
-    dontTouch(s0_src_valid_vec)
-    dontTouch(s0_src_ready_vec)
-    dontTouch(s0_src_select_vec)
-    dontTouch(s3_ld_data_frm_pipe)
-    s3_data_select_by_offset.map(x=> dontTouch(x))
-    s3_data_frm_pipe.map(x=> dontTouch(x))
-    s3_picked_data_frm_pipe.map(x=> dontTouch(x))
-  }
-
-  // perfcct load trace
-  val recordLoadReplayEn = s3_valid && !s3_kill && s3_replayqueue_rep_cause.asUInt.orR && !s3_in.feedbacked
-  val recordLoadReplayCause = ParallelPriorityMux(Seq(
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_MA)  -> PerfCCT.ReplayReason.Nuke.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_TM)  -> PerfCCT.ReplayReason.TLBMiss.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_DM)  -> PerfCCT.ReplayReason.CacheMiss.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_RAR) -> PerfCCT.ReplayReason.RARReplay.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_RAW) -> PerfCCT.ReplayReason.RAWReplay.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_BC)  -> PerfCCT.ReplayReason.BankConflict.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_FF)  -> PerfCCT.ReplayReason.STDForwardFail.id.U,
-      s3_replayqueue_rep_cause(LoadReplayCauses.C_DR)  -> PerfCCT.ReplayReason.DcacheStall.id.U,
-      true.B                                           -> PerfCCT.ReplayReason.OtherReplay.id.U
-  ))
-  val recordLoadAddrEn = io.ldout.fire && !s3_kill
-
-  val timer = GTimer()
-  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.ReplayStr.id.U, recordLoadReplayCause, recordLoadReplayEn, clock, reset)
-  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.LastReplay.id.U, timer, recordLoadReplayEn, clock, reset)
-  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.VAddress.id.U, s3_in.vaddr, recordLoadAddrEn, clock, reset)
-  PerfCCT.updateInstMeta(s3_in.uop.debug_seqNum, PerfCCT.InstDetail.PAddress.id.U, s3_in.paddr, recordLoadAddrEn, clock, reset)
-  // end
-}
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
new file mode 100644
index 00000000000..29b7334743c
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -0,0 +1,2130 @@
+/***************************************************************************************
+* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2020-2021 Peng Cheng Laboratory
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http://license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
+
+package xiangshan.mem
+
+import org.chipsalliance.cde.config.Parameters
+import chisel3._
+import chisel3.util._
+import top.{ArgParser, Generator}
+import utility._
+import xiangshan._
+import xiangshan.ExceptionNO._
+import xiangshan.backend.Bundles.{ExuInput, ExuOutput, MemWakeUpBundle, NewExuOutput, UopIdx, connectSamePort}
+import xiangshan.backend.fu.PMPRespBundle
+import xiangshan.backend.fu.FuConfig._
+import xiangshan.backend.fu.fpu.FPU
+import xiangshan.backend.ctrlblock.{DebugLsInfoBundle, LsTopdownInfo}
+import xiangshan.backend.fu.NewCSR._
+import xiangshan.backend.exu.ExeUnitParams
+import xiangshan.mem.Bundles._
+import xiangshan.mem.LoadReplayCauses._
+import xiangshan.mem.LoadStage._
+import xiangshan.cache._
+import xiangshan.cache.mmu._
+
+class LoadUnitS0(param: ExeUnitParams)(
+  implicit p: Parameters,
+  override implicit val s: LoadStage = LoadS0()
+) extends LoadUnitStage(param)
+  with HasL1PrefetchSourceParameter
+  with HasPerfEvents {
+  val io = IO(new Bundle() {
+    /**
+      * Request sources
+      */
+    val unalignTail = Flipped(DecoupledIO(new LoadStageIO))
+    val replay = Flipped(DecoupledIO(new LoadReplayIO))
+    val fastReplay = Flipped(DecoupledIO(new FastReplayIO))
+    // TODO: canAcceptHigh/LowConfPrefetch
+    val prefetchReq = Flipped(DecoupledIO(new L1PrefetchReq))
+    val vecldin = Flipped(DecoupledIO(new VectorLoadIn))
+    val ldin = Flipped(DecoupledIO(new ExuInput(param, hasCopySrc = true)))
+
+    // Tlb request
+    val tlbReq = DecoupledIO(new TlbReq)
+
+    // DCache request
+    // TODO: move pf_source, is128Req, debug info like s*_pc into req
+    // TODO: remove replacement updated
+    val dcacheReq = DecoupledIO(new DCacheWordReq)
+    val is128Req = Bool()
+    val replacementUpdated = Bool()
+    val pfSource = Output(UInt(L1PfSourceBits.W))
+
+    /**
+      * Data forward request, including:
+      * 1. LSQ / Sbuffer STLF
+      * 2. UncacheBuffer
+      * 3. MSHR and TileLink-D channel
+      */
+    val sqSbForwardReq = ValidIO(new StoreForwardReqS0)
+    val uncacheForwardReq = ValidIO(new StoreForwardReqS0)
+    val mshrForwardReq = ValidIO(new DCacheForwardReqS0)
+    val tldForwardReq = ValidIO(new DCacheForwardReqS0)
+    val uncacheBypassReq = ValidIO(new UncacheBypassReqS0)
+
+    // IQ wakeup
+    val wakeup = ValidIO(new MemWakeUpBundle)
+
+    // Debug info
+    val debugInfo = Output(new Bundle() {
+      val pc = Output(UInt(VAddrBits.W))
+    })
+  })
+
+  /**
+    * Request sources arbitration, in order of priority:
+    * 0. unalign tail inject from s1
+    * 1. high-priority replay from LRQ, including NC / MMIO replay
+    * 2. fast replay from s3
+    * 3. low-priority replay from LRQ
+    * 4. high-confidence prefetch
+    * 5. vector elements splited by VSplit
+    * 6. loads issued from IQ
+    * 7. low-confidence prefetch
+    */
+  val unalignTail,
+    replayHiPrio,
+    fastReplay,
+    replayLoPrio,
+    prefetchHiConf,
+    vectorIssue,
+    scalarIssue,
+    prefetchLoConf = Wire(DecoupledIO(new LoadStageIO))
+
+  val sources = Seq(
+    unalignTail,
+    replayHiPrio,
+    fastReplay,
+    replayLoPrio,
+    prefetchHiConf,
+    vectorIssue,
+    scalarIssue,
+    prefetchLoConf
+  )
+  val sink = Wire(DecoupledIO(new LoadStageIO))
+
+  // 0. unalign tail inject from s1
+  unalignTail <> io.unalignTail
+  unalignTail.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // 1. high-priority replay from LRQ, including NC / MMIO replay
+  val replay = Wire(new LoadStageIO)
+  connectSamePort(replay, io.replay.bits)
+  replay.noQuery.get := io.replay.bits.uncacheReplay.get
+  replay.DontCarePAddr()
+  replay.DontCareUnalign() // assign later in sink
+  val replayIsHiPrio = io.replay.bits.forwardDChannel.get || io.replay.bits.isUncacheReplay()
+  replayHiPrio.valid := io.replay.valid && replayIsHiPrio
+  replayHiPrio.bits := replay
+  replayHiPrio.bits.entrance := LoadEntrance.replayHiPrio.U
+  replayHiPrio.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // 2. fast replay from s3
+  fastReplay.valid := io.fastReplay.valid
+  connectSamePort(fastReplay.bits, io.fastReplay.bits)
+  fastReplay.bits.noQuery.get := true.B
+  fastReplay.bits.entrance := io.fastReplay.bits.entrance | LoadEntrance.fastReplay.U
+  fastReplay.bits.DontCareUnalign() // assign later in sink
+  fastReplay.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // 3. low-priority replay from LRQ
+  val replayStall = io.ldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.ldin.bits.lqIdx.get) ||
+    io.vecldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.vecldin.bits.uop.lqIdx)
+  val replayIsLoPrio = !io.replay.bits.forwardDChannel.get && !io.replay.bits.isUncacheReplay() && !replayStall
+  replayLoPrio.valid := io.replay.valid && replayIsLoPrio
+  replayLoPrio.bits := replay
+  replayLoPrio.bits.entrance := LoadEntrance.replayLoPrio.U
+  replayLoPrio.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // 4. high-confidence prefetch
+  val prefetch = Wire(new LoadStageIO)
+  val prefetchIsHiConf = io.prefetchReq.bits.confidence > 0.U
+  prefetch.entrance := 0.U // assign later
+  prefetch.accessType.instrType := InstrType.prefetch.U
+  prefetch.accessType.pftType := PrefetchType.hwData
+  prefetch.accessType.pftCoh := Mux(io.prefetchReq.bits.is_store, PrefetchCoh.write, PrefetchCoh.read)
+  prefetch.uop := DontCare
+  prefetch.vaddr := io.prefetchReq.bits.getVaddr() // not actual vaddr, but Cat(alias, page offset)
+  prefetch.fullva := io.prefetchReq.bits.getVaddr()
+  prefetch.size := DontCare
+  prefetch.mask := 0.U
+  prefetch.paddr.get := io.prefetchReq.bits.paddr
+  prefetch.noQuery.get := true.B
+  prefetch.DontCareUnalign() // assign later in sink
+  prefetch.DontCareReplayFromLRQFields()
+  prefetch.DontCareVectorFields()
+  prefetch.hasROBEntry := false.B
+  prefetch.missDbUpdated := false.B
+  prefetch.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+  prefetchHiConf.valid := io.prefetchReq.valid && prefetchIsHiConf
+  prefetchHiConf.bits := prefetch
+  prefetchHiConf.bits.entrance := LoadEntrance.prefetchHiConf.U
+
+  // 5. vector elements splited by VSplit
+  vectorIssue.valid := io.vecldin.valid
+  connectSamePort(vectorIssue.bits, io.vecldin.bits)
+  vectorIssue.bits.entrance := LoadEntrance.vectorIssue.U
+  vectorIssue.bits.accessType.instrType := InstrType.vector.U
+  vectorIssue.bits.accessType.pftType := DontCare
+  vectorIssue.bits.accessType.pftCoh := DontCare
+  vectorIssue.bits.DontCarePAddr()
+  vectorIssue.bits.noQuery.get := false.B
+  vectorIssue.bits.DontCareUnalign() // assign later in sink
+  vectorIssue.bits.DontCareReplayFromLRQFields()
+  vectorIssue.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // 6. loads issued from IQ
+  val ldin = io.ldin.bits
+  val ldinVAddr = ldin.src(0) + SignExt(ldin.imm(11, 0), VAddrBits)
+  val ldinFullva = ldin.src(0) + SignExt(ldin.imm(11, 0), XLEN)
+  val ldinSize = LSUOpType.size(ldin.fuOpType) // B, H, W, D, excluding of Q
+  scalarIssue.valid := io.ldin.valid
+  scalarIssue.bits.entrance := LoadEntrance.scalarIssue.U
+  scalarIssue.bits.accessType.instrType := Mux(
+    LSUOpType.isPrefetch(ldin.fuOpType),
+    InstrType.prefetch.U, // software prefetch
+    InstrType.scalar.U
+  )
+  scalarIssue.bits.accessType.pftType := Mux( // valid only when instrType is prefetch
+    ldin.fuOpType === LSUOpType.prefetch_i,
+    PrefetchType.swInstr,
+    PrefetchType.swData
+  )
+  scalarIssue.bits.accessType.pftCoh := Mux( // valid only when pftType is not swInstr
+    ldin.fuOpType === LSUOpType.prefetch_w,
+    PrefetchCoh.write,
+    PrefetchCoh.read
+  )
+  scalarIssue.bits.uop := ldin.toDynInst()
+  scalarIssue.bits.vaddr := ldinVAddr
+  scalarIssue.bits.fullva := ldinFullva
+  scalarIssue.bits.size := ldinSize
+  scalarIssue.bits.mask := Mux(LSUOpType.isPrefetch(ldin.fuOpType), 0.U, genVWmask(ldinVAddr, ldinSize))
+  scalarIssue.bits.DontCarePAddr()
+  scalarIssue.bits.noQuery.get := ldin.fuOpType === LSUOpType.prefetch_i // swInstr
+  scalarIssue.bits.DontCareUnalign() // assign later in sink
+  scalarIssue.bits.DontCareReplayFromLRQFields()
+  scalarIssue.bits.DontCareVectorFields()
+  scalarIssue.bits.hasROBEntry := true.B
+  scalarIssue.bits.missDbUpdated := false.B
+  scalarIssue.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // 7. low-confidence prefetch
+  prefetchLoConf.valid := io.prefetchReq.valid
+  prefetchLoConf.bits := prefetch
+  prefetchLoConf.bits.entrance := LoadEntrance.prefetchLoConf.U
+  prefetchLoConf.bits.occupySource := VecInit(sources.map(_.valid)).asUInt // for perf
+
+  // sources arbitration
+  arbiter(sources, sink, Some("RequestSources"))
+  val pipeIn = Wire(DecoupledIO(new LoadStageIO))
+  pipeIn.valid := sink.valid && io.dcacheReq.ready
+  sink.ready := pipeIn.ready && io.dcacheReq.ready
+  connectSamePort(pipeIn.bits, sink.bits)
+
+  // alias for arbitration result
+  val uop = sink.bits.uop
+  val isPrefetch = sink.bits.accessType.isPrefetch()
+  val isSwInstrPrefetch = sink.bits.accessType.isInstrPrefetch()
+  val isHwPrefetch = sink.bits.accessType.isHwPrefetch()
+  val isUncacheReplay = sink.bits.isUncacheReplay()
+
+  /**
+    * Tlb access
+    *
+    * It should be noted that when sending a request to TLB, `req.valid` does not need to be a strict valid signal. We
+    * only need to ensure that `req.valid` is HIGH in all cases requiring TLB translation. Meanwhile the strict signal
+    * over whether addr translation is actually performed is controled by `noQuery`.
+    */
+  val needTlbTransSources = Seq(unalignTail, replayHiPrio, replayLoPrio, vectorIssue, scalarIssue)
+  val tlbReqValid = Cat(needTlbTransSources.map(_.valid)).orR && io.dcacheReq.ready
+  val tlbFuOpType = ParallelPriorityMux(needTlbTransSources.map(s => (s.valid -> s.bits.uop.fuOpType)))
+  val tlbVAddr = ParallelPriorityMux(needTlbTransSources.map(s => (s.valid -> s.bits.vaddr)))
+  val tlbFullva = sink.bits.fullva
+  val tlbHlv = LSUOpType.isHlv(tlbFuOpType)
+  val tlbHlvx = LSUOpType.isHlvx(tlbFuOpType)
+
+  val noQuery = sink.bits.noQuery.get
+
+  val firstIssueSources = Seq(vectorIssue, scalarIssue)
+  val firstIssue = Cat(firstIssueSources.map(_.fire)).orR
+  val tlbCheckFullva = firstIssue
+
+  /**
+    * Unalign handling
+    *
+    * 1. For requests that are not unalign tail or prefetch
+    *   1.1 Align check: check if the address is aligned, which is used to detect misalign exception in later stages
+    *   1.2 Bank bound check: simultaneously check whether this address crosses an aligned 16B bank boundary, which is
+    *     used to inject an unalign tail in the next stage
+    *   1.3 Word bound check: simultaneously check whether this address crosses an aligned 8B bank boundary. If yes,
+    *     read the whole 16B bank when accessing DCache
+    * 2. For requests that are unalign tail
+    *   Do nothing
+    *
+    * Some terminology explanations:
+    * - **align** indicates whether the addr is aligned with the operation size. `!align` does not necessary mean
+    *   splitting is required, but is only used for determining exception in subsequent stages.
+    * - **unalign** indicates that under the condition of align, the operation range exceeds aligned 16B bank boundary,
+    *   requiring splitting into 2 operations on DCache.
+    * - **misalign** is used specifically to denote misalign exception.
+    */
+  val needAlignCheckSources = Seq(replayHiPrio, fastReplay, replayLoPrio, vectorIssue, scalarIssue)
+  val needAlignCheckValids = needAlignCheckSources.map(_.valid)
+  val noAlignCheckSources = sources.filterNot(needAlignCheckSources.contains) // unalign tail, hardware prefetch
+  val noAlignCheck = Cat(noAlignCheckSources.map(_.fire)).orR || isPrefetch // unalign tail, hardware & software prefetch
+  val needAlignCheck = !noAlignCheck
+
+  val alignCheckResults = needAlignCheckSources.map(s => alignCheck(s.bits.bankOffset(), s.bits.size, s.valid)).unzip3
+  val _align = ParallelPriorityMux(needAlignCheckValids, alignCheckResults._1)
+  val _crossWordInsideBank = ParallelPriorityMux(needAlignCheckValids, alignCheckResults._2)
+  val _crossBank = ParallelPriorityMux(needAlignCheckValids, alignCheckResults._3)
+  val align = noAlignCheck || _align
+  val crossWordInsideBank = needAlignCheck && _crossWordInsideBank
+  val crossBank = needAlignCheck && _crossBank
+  val readWholeBank = unalignTail.valid || crossWordInsideBank
+
+  sink.bits.align.get := align
+  sink.bits.unalignHead.get := crossBank
+  sink.bits.readWholeBank.get := readWholeBank
+
+  def alignCheck(bankOffset: UInt, size: UInt, valid: Bool): (Bool, Bool, Bool) = {
+    require(bankOffset.getWidth == DCacheVWordOffset)
+    require(size.getWidth == MemorySize.Size.width)
+    // 1.1 Align check
+    val align = LookupTree(size, List( // TODO: parameterize this
+      "b00".U -> true.B,
+      "b01".U -> (bankOffset.take(1) === 0.U),
+      "b10".U -> (bankOffset.take(2) === 0.U),
+      "b11".U -> (bankOffset.take(3) === 0.U)
+    ))
+    assert(size =/= MemorySize.Q.U || bankOffset === 0.U || !valid)
+    // 1.2 Bank bound check
+    // 1.3 Word bound check
+    val upBoundBankOffset = LookupTree(size, List(
+      MemorySize.B.U -> 0.U,
+      MemorySize.H.U -> 1.U,
+      MemorySize.W.U -> 3.U,
+      MemorySize.D.U -> 7.U,
+      MemorySize.Q.U -> 15.U
+    )) +& bankOffset
+    val wordIdx = bankOffset(DCacheWordOffset)
+    val upBoundWordIdx = upBoundBankOffset(DCacheWordOffset)
+    val crossBank = upBoundBankOffset.head(1).asBool
+    val crossWordInsideBank = !crossBank && wordIdx === 0.U && upBoundWordIdx =/= 0.U
+    (align, crossWordInsideBank, crossBank)
+  }
+
+  /**
+    * DCache access
+    *
+    * Access to an aligned 16B bank is required in the following 2 cases:
+    * 1. Unalign tail: For simplicity, we do not calculate the exact # of bytes that an unalignTail needs to access,
+    *   but directly access the entire bank
+    * 2. Access that is not align, but inside a 16B bank
+    * 3. Vector unit-stride
+    */
+  val dcacheReqValid = sink.valid // all sources need to access DCache
+  val dcacheVAddr = sink.bits.vaddr
+  val noDCacheAccessSwInstrPrefetch = isSwInstrPrefetch // software instruction prefetch
+  val noDCacheAccessUncacheReplay = isUncacheReplay // uncache replay
+  val noDCacheAccess = noDCacheAccessSwInstrPrefetch || noDCacheAccessUncacheReplay
+
+  /**
+    * Data forward
+    */
+  val storeForwardReq = Wire(new StoreForwardReqS0)
+  storeForwardReq.vaddr := sink.bits.vaddr
+  storeForwardReq.sqIdx := uop.sqIdx
+  storeForwardReq.size := sink.bits.size
+  storeForwardReq.loadWaitBit := uop.loadWaitBit
+  storeForwardReq.loadWaitStrict := uop.loadWaitStrict
+  storeForwardReq.ssid := uop.ssid
+  storeForwardReq.storeSetHit := uop.storeSetHit
+  storeForwardReq.waitForRobIdx := uop.waitForRobIdx
+
+  val uncacheForwardReqValid = replayHiPrio.fire && replayHiPrio.bits.isUncacheReplay()
+
+  val dcacheForwardReqValid = replayHiPrio.fire && replayHiPrio.bits.forwardDChannel.get
+  val dcacheForwardReq = Wire(new DCacheForwardReqS0)
+  dcacheForwardReq.vaddr := sink.bits.vaddr
+  dcacheForwardReq.size := sink.bits.size
+  dcacheForwardReq.mshrId := sink.bits.mshrId.get
+
+  val uncacheBypassReqValid = uncacheForwardReqValid
+  val uncacheBypassReq = Wire(new UncacheBypassReqS0)
+  uncacheBypassReq.lqIdx := uop.lqIdx
+  uncacheBypassReq.isNCReplay := replayHiPrio.bits.isNCReplay()
+  uncacheBypassReq.isMMIOReplay := replayHiPrio.bits.isMMIOReplay()
+
+  /**
+    * IQ wakeup
+    */
+  // Select between 2 options based on timing result:
+  // Option 1
+  val needWakeupSources = Seq(unalignTail, replayHiPrio, fastReplay, replayLoPrio, scalarIssue)
+  val needWakeupValids = needWakeupSources.map(s => s.fire && s.bits.accessType.isScalar()) // exclude vector and prefetch
+  // Option 2
+  // val needWakeupSources = sources
+  // val needWakeupValids = needWakeupSources.map(s => s.valid && sink.ready && s.bits.accessType.isScalar())
+  val wakeupValid = Cat(needWakeupValids).orR && !sink.bits.unalignHead.get
+  val wakeupSource = ParallelPriorityMux(needWakeupValids, needWakeupSources.map(_.bits))
+  val wakeup = Wire(new MemWakeUpBundle)
+  connectSamePort(wakeup, wakeupSource.uop)
+
+  /**
+    * Pipeline connect
+    */
+  val pipeOutValid = RegInit(false.B)
+  val pipeOutBits = RegEnable(pipeIn.bits, pipeIn.fire)
+  when (pipeIn.fire) { pipeOutValid := true.B }
+  .elsewhen (io_pipeOut.get.fire) { pipeOutValid := false.B }
+  pipeIn.ready := !pipeOutValid || io_pipeOut.get.ready
+
+  /**
+    * IO assignment
+    */
+  io_pipeOut.get.valid := pipeOutValid
+  io_pipeOut.get.bits := pipeOutBits
+
+  assert(!sink.ready || unalignTail.ready, "unalignTail should always be ready")
+  io.replay.ready := replayIsHiPrio && replayHiPrio.ready || replayIsLoPrio && replayLoPrio.ready
+  io.fastReplay.ready := fastReplay.ready
+  io.prefetchReq.ready := Mux(prefetchIsHiConf, prefetchHiConf.ready, prefetchLoConf.ready)
+  io.vecldin.ready := vectorIssue.ready
+  io.ldin.ready := scalarIssue.ready
+
+  io.tlbReq.valid := tlbReqValid
+  io.tlbReq.bits.vaddr := tlbVAddr
+  io.tlbReq.bits.fullva := tlbFullva
+  io.tlbReq.bits.checkfullva := tlbCheckFullva
+  io.tlbReq.bits.cmd := TlbCmd.read
+  io.tlbReq.bits.hyperinst := tlbHlv
+  io.tlbReq.bits.hlvx := tlbHlvx
+  io.tlbReq.bits.size := DontCare
+  io.tlbReq.bits.kill := noQuery
+  io.tlbReq.bits.memidx.is_ld := true.B
+  io.tlbReq.bits.memidx.is_st := false.B
+  io.tlbReq.bits.memidx.idx := uop.lqIdx.value
+  io.tlbReq.bits.isPrefetch := isPrefetch
+  io.tlbReq.bits.no_translate := noQuery
+  io.tlbReq.bits.pmp_addr := DontCare // TODO: move this outside of TlbReq
+  io.tlbReq.bits.debug.pc := uop.pc
+  io.tlbReq.bits.debug.robIdx := uop.robIdx
+  io.tlbReq.bits.debug.isFirstIssue := firstIssue
+
+  io.dcacheReq.valid := dcacheReqValid && !noDCacheAccess
+  io.dcacheReq.bits.cmd := Mux(isPrefetch, MemoryOpConstants.M_PFR, MemoryOpConstants.M_XRD)
+  io.dcacheReq.bits.vaddr := dcacheVAddr
+  io.dcacheReq.bits.vaddr_dup := dcacheVAddr
+  io.dcacheReq.bits.data := DontCare
+  io.dcacheReq.bits.mask := DontCare
+  io.dcacheReq.bits.id := DontCare
+  io.dcacheReq.bits.instrtype := Mux(isPrefetch, DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
+  io.dcacheReq.bits.isFirstIssue := firstIssue
+  io.dcacheReq.bits.replayCarry := DontCare
+  io.dcacheReq.bits.lqIdx := uop.lqIdx
+  io.dcacheReq.bits.debug_robIdx := uop.robIdx.value
+  io.is128Req := readWholeBank
+  io.replacementUpdated := DontCare
+  io.pfSource := Mux(isHwPrefetch, io.prefetchReq.bits.pf_source.value, L1_HW_PREFETCH_NULL)
+
+  io.sqSbForwardReq.valid := sink.valid
+  io.sqSbForwardReq.bits := storeForwardReq
+
+  io.uncacheForwardReq.valid := uncacheForwardReqValid
+  io.uncacheForwardReq.bits := storeForwardReq
+
+  io.mshrForwardReq.valid := dcacheForwardReqValid
+  io.mshrForwardReq.bits := dcacheForwardReq
+  io.tldForwardReq.valid := dcacheForwardReqValid
+  io.tldForwardReq.bits := dcacheForwardReq
+
+  io.uncacheBypassReq.valid := uncacheBypassReqValid
+  io.uncacheBypassReq.bits := uncacheBypassReq
+
+  io.wakeup.valid := wakeupValid
+  io.wakeup.bits := wakeup
+
+  io.debugInfo.pc := uop.pc
+
+  /**
+    *  Perf counters
+    */
+  XSPerfAccumulate("ldin_valid", io.ldin.valid)
+  XSPerfAccumulate("ldin_block", io.ldin.valid && !io.ldin.ready)
+  XSPerfAccumulate("ldin_fire_first_issue", io.ldin.fire && firstIssue)
+  XSPerfAccumulate("vecldin_valid", io.vecldin.valid)
+  XSPerfAccumulate("vecldin_block", io.vecldin.valid && !io.vecldin.ready)
+  XSPerfAccumulate("first_issue", firstIssue)
+  XSPerfAccumulate("replay_fire", io.replay.fire)
+  XSPerfAccumulate("replay_fire_vector", io.replay.fire && io.replay.bits.accessType.isVector())
+  XSPerfAccumulate("fast_replay_fire", io.fastReplay.fire)
+  XSPerfAccumulate("fast_replay_fire_vector", io.fastReplay.fire && io.fastReplay.bits.accessType.isVector())
+  XSPerfAccumulate("stall_out", sink.valid && !sink.ready)
+  XSPerfAccumulate("stall_dcache", sink.valid && !io.dcacheReq.ready)
+  XSPerfAccumulate("vector_addr_vlen_align", io.vecldin.fire && io.vecldin.bits.bankOffset() === 0.U)
+  XSPerfAccumulate("vector_addr_vlen_unalign", io.vecldin.fire && io.vecldin.bits.bankOffset() =/= 0.U)
+  XSPerfAccumulate("forward_tld_channel", io.replay.fire && io.replay.bits.forwardDChannel.get)
+  XSPerfAccumulate("hardware_prefetch_fire", io.prefetchReq.fire)
+  XSPerfAccumulate("software_prefetch_fire", io.ldin.fire && LSUOpType.isPrefetch(io.ldin.bits.fuOpType))
+  XSPerfAccumulate("hardware_prefetch_block", io.prefetchReq.valid && !io.prefetchReq.ready)
+  XSPerfAccumulate("hardware_prefetch_total", io.prefetchReq.valid)
+  val perfEvents = Seq(
+    ("s0_in_fire", pipeIn.fire),
+    ("s0_stall_dcache", sink.valid && !io.dcacheReq.ready)
+  )
+  generatePerfEvent()
+
+  /**
+   * PerfCCT
+   */
+  io.ldin.bits.debug_seqNum.foreach(x =>
+    PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.ldin.valid, clock, reset)
+  )
+}
+
+class LoadUnitS1(param: ExeUnitParams)(
+  implicit p: Parameters,
+  override implicit val s: LoadStage = LoadS1()
+) extends LoadUnitStage(param)
+  with HasVLSUParameters
+  with HasNukePAddrMatch
+  with HasPerfEvents {
+  val io = IO(new Bundle() {
+    val redirect = Flipped(ValidIO(new Redirect))
+    val kill = Input(Bool())
+
+    // Tlb response
+    val tlbResp = Flipped(DecoupledIO(new TlbResp(2))) // TODO: parameterize 2
+    val tlbReqKill = Output(Bool())
+    val tlbPAddr = Output(UInt(PAddrBits.W)) // only used for no_translate
+
+    // DCache request: paddr and s1 kill signal
+    val dcachePAddr = Output(UInt(PAddrBits.W))
+    val dcacheKill = Output(Bool())
+
+    /**
+      * Data forward request and kill
+      */
+    val storeForwardReq = Output(new StoreForwardReqS1) // including SQ, Sbuffer and Uncache
+    val mshrForwardReq = Output(new DCacheForwardReqS1)
+    val tldForwardReq = Output(new DCacheForwardReqS1)
+    val sqForwardKill = Output(Bool())
+    val sbufferForwardKill = Output(Bool())
+    val uncacheForwardKill = Output(Bool())
+    val mshrForwardKill = Output(Bool())
+    val tldForwardKill = Output(Bool())
+    // early reponse from SQ, unused for now
+    val sqForwardResp = Flipped(ValidIO(new SQForwardRespS1))
+
+    val uncacheBypassResp = Flipped(ValidIO(new UncacheBypassRespS1))
+
+    // Data path
+    val dataPathMeta = ValidIO(new LoadUnitDataPathMeta)
+
+    // Unalign tail inject to s0
+    val unalignTail = DecoupledIO(new LoadStageIO()(p, prevStage(s)))
+
+    // Nuke check with StoreUnit
+    val staNukeQueryReq = Flipped(Vec(StorePipelineWidth, ValidIO(new StoreNukeQueryReq)))
+
+    // prefetch train hint
+    val prefetchTrainHint = Output(Bool())
+
+    // Software instruction prefetch
+    val swInstrPrefetch = ValidIO(new SoftIfetchPrefetchBundle)
+
+    // Load trigger
+    val csrTrigger = Input(new CsrTriggerBundle)
+
+    // Debug info
+    val debugInfo = Output(new Bundle() {
+      val isTlbFirstMiss = Bool()
+      val isLoadToLoadForward = Bool()
+      val robIdx = UInt(log2Ceil(RobSize).W)
+      val hasROBEntry = Bool()
+      val vaddr = ValidIO(UInt(VAddrBits.W))
+      val pc = Output(UInt(VAddrBits.W))
+    })
+  })
+
+  val pipeIn = io_pipeIn.get
+  val pipeOut = io_pipeOut.get
+  val in = pipeIn.bits
+
+  val entrance = in.entrance
+  val accessType = in.accessType
+  val uop = in.uop
+  val robIdx = uop.robIdx
+  val fuOpType = uop.fuOpType
+  val vaddr = in.vaddr
+  val mask = in.mask
+
+  val isSwInstrPrefetch = accessType.isSwPrefetch() && accessType.isInstrPrefetch()
+
+  // storeset
+  val isStoreSetHit = uop.storeSetHit
+  val waitRobIdx = uop.waitForRobIdx
+
+  /**
+    * Redirect
+    */
+  val redirect = io.redirect
+  val redirectNext = Wire(redirect.cloneType)
+  redirectNext.valid := GatedValidRegNext(redirect.valid)
+  redirectNext.bits := RegEnable(redirect.bits, redirect.valid)
+  val kill = io.kill || isSwInstrPrefetch || robIdx.needFlush(redirect) || robIdx.needFlush(redirectNext) || !pipeIn.valid
+
+  /**
+    * Tlb & DCache
+    */
+  val tlbResp = io.tlbResp
+  val noQuery = in.noQuery.get
+  val tlbHit = tlbResp.valid && !tlbResp.bits.miss && !noQuery
+  val tlbMiss = tlbResp.valid && tlbResp.bits.miss
+  val paddrEffective = tlbHit || noQuery // hit or noQuery
+  val pbmt = Mux(tlbHit, tlbResp.bits.pbmt.head, Pbmt.pma)
+  val noQueryPAddr = Mux(io.uncacheBypassResp.valid, io.uncacheBypassResp.bits.paddr, in.paddr.get)
+  val paddr = Mux(noQuery, noQueryPAddr, tlbResp.bits.paddr(0))
+  val paddrDCache = Mux(noQuery, noQueryPAddr, tlbResp.bits.paddr(1))
+  val gpaddr = tlbResp.bits.gpaddr(0)
+  val fullva = tlbResp.bits.fullva
+
+  val pf = tlbHit && tlbResp.bits.excp.head.pf.ld
+  val af = tlbHit && tlbResp.bits.excp.head.af.ld
+  val gpf = tlbHit && tlbResp.bits.excp.head.gpf.ld
+  val tlbException = pf || af || gpf
+
+  val killDCache = kill || tlbMiss || tlbException
+
+  assert(!(pipeIn.valid && !tlbResp.valid && !noQuery))
+
+  /**
+    * Load trigger
+    */
+  val loadTrigger = Module(new MemTrigger(MemType.LOAD))
+  loadTrigger.io.fromCsrTrigger.tdataVec := io.csrTrigger.tdataVec
+  loadTrigger.io.fromCsrTrigger.tEnableVec := io.csrTrigger.tEnableVec
+  loadTrigger.io.fromCsrTrigger.triggerCanRaiseBpExp := io.csrTrigger.triggerCanRaiseBpExp
+  loadTrigger.io.fromCsrTrigger.debugMode := io.csrTrigger.debugMode
+  loadTrigger.io.fromLoadStore.vaddr := vaddr
+  loadTrigger.io.fromLoadStore.isVectorUnitStride := accessType.isVector() && VlduType.isUnitStride(fuOpType)
+  loadTrigger.io.fromLoadStore.mask := mask
+  loadTrigger.io.isPrf.get := accessType.isPrefetch()
+
+  val triggerAction = loadTrigger.io.toLoadStore.triggerAction
+  val isDebugMode = TriggerAction.isDmode(triggerAction)
+  val bp = TriggerAction.isExp(triggerAction)
+  val vecVaddrOffset = Mux(
+    isDebugMode || bp,
+    loadTrigger.io.toLoadStore.triggerVaddr - in.vecBaseVaddr.get,
+    vaddr + genVFirstUnmask(mask).asUInt - in.vecBaseVaddr.get
+  )
+  val vecTriggerMask = Mux(
+    isDebugMode || bp,
+    loadTrigger.io.toLoadStore.triggerMask,
+    0.U
+  )
+
+  /**
+    * Unalign tail inject to s0
+    */
+  val unalignTailInjectValid = pipeIn.valid && !kill && in.unalignHead.get
+  val unalignTail = Wire(io.unalignTail.bits.cloneType)
+  connectSamePort(unalignTail, in)
+  unalignTail.entrance := LoadEntrance.unalignTail.U
+  unalignTail.vaddr := ((vaddr >> DCacheVWordOffset) + 1.U) << DCacheVWordOffset
+  unalignTail.fullva := ((in.fullva >> DCacheVWordOffset) + 1.U) << DCacheVWordOffset
+  unalignTail.size := MemorySize.Q.U
+  unalignTail.mask := genVWmask(vaddr, LSUOpType.size(fuOpType)) >> DCacheVWordBytes
+  unalignTail.align.get := false.B
+  unalignTail.unalignHead.get := false.B
+  unalignTail.readWholeBank.get := true.B
+  // TODO: only cross page unalign need to query tlb, but timing will be worse,
+  // this let unalignTail always query tlb, even if it come from fastReplay/replay.
+  unalignTail.noQuery.get := false.B // unalignTail always need to query tlb
+
+  val unalignTailNack = unalignTailInjectValid && !io.unalignTail.ready
+
+  /**
+    * Nuke check with StoreUnit
+    */
+  val nukeQueryValids = io.staNukeQueryReq.map(_.valid)
+  val nukeQueryReqs = io.staNukeQueryReq.map(_.bits)
+  val nukePAddrMatches = nukeQueryReqs.map(req => nukePAddrMatch(req.paddr, req.matchType, paddr))
+  val nukeStoreOlders = nukeQueryReqs.map(req => isAfter(robIdx, req.robIdx))
+  val nukeMaskMatches = nukeQueryReqs.map(req => (req.mask & in.mask).orR)
+  val nuke = Cat((nukeQueryValids lazyZip nukePAddrMatches lazyZip nukeStoreOlders lazyZip nukeMaskMatches).map {
+    case (valid, paddrMatch, storeOlder, maskMatch) => valid && paddrMatch && storeOlder && maskMatch
+  }).orR && paddrEffective
+  // if nuke is storeSetHit store, let load fast replay
+  val fastReplayNukeFirst = isStoreSetHit && nukeQueryReqs.zip(nukeQueryValids).map{case (req, v) =>
+    req.robIdx === waitRobIdx && v}.reduce(_ || _)
+
+  /**
+    * Pipeline connect
+    */
+  val pipeOutValid = RegInit(false.B)
+  val pipeOutBits = Reg(new LoadStageIO)
+  when (kill) { pipeOutValid := false.B }
+  .elsewhen (pipeIn.fire) { pipeOutValid := true.B }
+  .elsewhen (pipeOut.fire) { pipeOutValid := false.B }
+
+  // exception
+  val exception = tlbException || bp
+  val stageInfo = Wire(pipeOut.bits.cloneType)
+  connectSamePort(stageInfo, in)
+  stageInfo.uop.trigger := triggerAction
+  stageInfo.uop.exceptionVec(breakPoint) := bp
+  stageInfo.uop.exceptionVec(loadPageFault) := pf
+  stageInfo.uop.exceptionVec(loadAccessFault) := af
+  stageInfo.uop.exceptionVec(loadGuestPageFault) := gpf
+  stageInfo.uop.perfDebugInfo.tlbRespTime := Mux(
+    pipeIn.valid && paddrEffective,
+    GTimer(),
+    Mux(pipeIn.valid && tlbMiss, uop.perfDebugInfo.tlbFirstReqTime, uop.perfDebugInfo.tlbRespTime)
+  )
+  // update tlb response
+  stageInfo.fullva := tlbResp.bits.fullva
+  stageInfo.paddr.get := paddr
+  stageInfo.tlbAccessResult.get := Mux(
+    noQuery,
+    TlbAccessResult.noQuery.U,
+    Mux(tlbHit, TlbAccessResult.hit.U, TlbAccessResult.miss.U)
+  )
+  stageInfo.tlbException.get := tlbResp.bits.excp.head
+  stageInfo.pbmt.get := pbmt
+  stageInfo.gpaddr.get := gpaddr
+  stageInfo.isForVSnonLeafPTE.get := tlbResp.bits.isForVSnonLeafPTE
+  // update replay cause (only nuke is detected in S1)
+  stageInfo.cause.get := 0.U.asTypeOf(stageInfo.cause.get)
+  stageInfo.cause.get(LoadReplayCauses.C_NK) := nuke
+  stageInfo.fastReplayNukeFirst.get := fastReplayNukeFirst
+  // update trigger info
+  stageInfo.vecVaddrOffset.get := vecVaddrOffset
+  stageInfo.vecTriggerMask.get := vecTriggerMask
+  stageInfo.shouldFastReplay.get := unalignTailNack && !tlbMiss && !exception
+
+  when (pipeIn.fire) { pipeOutBits := stageInfo }
+
+  /**
+    * IO assignment
+    */
+  io_pipeOut.get.valid := pipeOutValid
+  io_pipeOut.get.bits := pipeOutBits
+  io_pipeIn.get.ready := !pipeOutValid || kill || pipeOut.ready
+
+  io.tlbResp.ready := true.B
+  io.tlbReqKill := kill
+  io.tlbPAddr := noQueryPAddr
+
+  io.dcachePAddr := paddrDCache
+  io.dcacheKill := killDCache
+
+  // use kill instead of killDCache if timing does not allow it
+  io.storeForwardReq.paddr := paddr
+  io.mshrForwardReq.paddr := paddr
+  io.tldForwardReq.paddr := paddr
+  io.sqForwardKill := killDCache
+  io.sbufferForwardKill := killDCache
+  io.uncacheForwardKill := kill
+  io.mshrForwardKill := killDCache
+  io.tldForwardKill := killDCache
+
+  io.dataPathMeta.valid := pipeIn.valid && !kill
+  io.dataPathMeta.bits.bankOffset := paddr.take(DCacheVWordOffset)
+  io.dataPathMeta.bits.fuOpType := fuOpType
+  io.dataPathMeta.bits.fpWen := uop.fpWen
+  io.dataPathMeta.bits.isNCReplay := in.isNCReplay()
+  io.dataPathMeta.bits.isMMIOReplay := in.isMMIOReplay()
+  io.dataPathMeta.bits.isUnalignHead := in.unalignHead.get
+
+  io.unalignTail.valid := unalignTailInjectValid
+  io.unalignTail.bits := unalignTail
+
+  io.prefetchTrainHint := pipeIn.valid && !kill && in.isFirstIssue()
+
+  io.swInstrPrefetch.valid := pipeIn.valid && isSwInstrPrefetch
+  io.swInstrPrefetch.bits.vaddr := vaddr
+
+  io.debugInfo.isTlbFirstMiss := pipeIn.valid && !kill && tlbMiss && in.isFirstIssue()
+  io.debugInfo.isLoadToLoadForward := false.B
+  io.debugInfo.robIdx := robIdx.value
+  io.debugInfo.hasROBEntry := in.hasROBEntry
+  io.debugInfo.vaddr.valid := pipeIn.valid && !kill
+  io.debugInfo.vaddr.bits := vaddr
+  io.debugInfo.pc := uop.pc
+
+  assert(!(pipeIn.valid && in.isUncacheReplay()) || kill || io.uncacheBypassResp.valid,
+    "uncache bypass should always success")
+
+  /**
+   *  Perf counters
+   */
+  val fire = pipeIn.fire && !kill
+  XSPerfAccumulate("valid", pipeIn.valid)
+  XSPerfAccumulate("fire", fire)
+  XSPerfAccumulate("fire_first_issue", fire && in.isFirstIssue())
+  XSPerfAccumulate("tlb_miss", fire && tlbMiss)
+  XSPerfAccumulate("tlb_miss_first_issue", fire && tlbMiss && in.isFirstIssue())
+
+  val perfEvents = Seq(
+    ("s1_in_fire", pipeIn.fire),
+    ("s1_tlb_miss", pipeIn.fire && io.tlbResp.bits.miss)
+  )
+  generatePerfEvent()
+}
+
+class LoadUnitS2(param: ExeUnitParams)(
+  implicit p: Parameters,
+  override implicit val s: LoadStage = LoadS2()
+) extends LoadUnitStage(param)
+  with HasNukePAddrMatch
+  with HasPerfEvents {
+  val io = IO(new Bundle() {
+    val redirect = Flipped(ValidIO(new Redirect))
+    val kill = Input(Bool())
+    val unalignTailValid = Output(Bool())
+
+    // PMP result
+    val pmp = Flipped(new PMPRespBundle)
+    // TLB Hint
+    val tlbHint = Flipped(new TlbHintReq)
+
+    // DCache request: s2 kill signal
+    val dcacheKill = Output(Bool())
+
+    // DCache response
+    val dcacheResp = Flipped(DecoupledIO(new DCacheWordResp))
+    // TODO: move this inside of dcacheResp
+    val dcacheBankConflict = Input(Bool())
+    val dcacheMSHRNack = Input(Bool())
+
+    /**
+      * Data forward response
+      */
+    val sqForwardResp = Flipped(ValidIO(new SQForwardRespS2))
+    val sbufferForwardResp = Flipped(ValidIO(new SbufferForwardResp))
+    val uncacheForwardResp = Flipped(ValidIO(new UncacheForwardResp))
+    val mshrForwardResp = Flipped(ValidIO(new DCacheForwardResp))
+    val tldForwardResp = Flipped(ValidIO(new DCacheForwardResp))
+
+    val uncacheBypassResp = Flipped(ValidIO(new UncacheBypassRespS2))
+
+    // Nuke query from StoreUnit
+    val staNukeQueryReq = Flipped(Vec(StorePipelineWidth, ValidIO(new StoreNukeQueryReq)))
+    // Nuke query to LQRAR / LQRAW
+    val rarNukeQueryReq = DecoupledIO(new LoadNukeQueryReq)
+    val rawNukeQueryReq = DecoupledIO(new LoadNukeQueryReq)
+
+    // Prefetch train
+    // TODO: this bundle is tooooooo big, define a smaller one
+    val prefetchTrain = ValidIO(new LsPrefetchTrainBundle)
+
+    // CSR control signals
+    val csrCtrl = Flipped(new CustomCSRCtrlIO)
+
+    // Debug info
+    val debugInfo = Output(new Bundle() {
+      val isBankConflict = Bool()
+      val isDCacheMiss = Bool()
+      val isDCacheFirstMiss = Bool() // ?
+      val isDCacheRealMiss = Bool() // ?
+      val isForwardFail = Bool()
+      val isTlbNotMiss = Bool()
+      val robIdx = UInt(log2Ceil(RobSize).W)
+      val hasROBEntry = Bool()
+      val paddr = ValidIO(UInt(PAddrBits.W))
+      val pc = Output(UInt(VAddrBits.W))
+    })
+  })
+
+  val pipeIn = io_pipeIn.get
+  val pipeOut = io_pipeOut.get
+  val in = pipeIn.bits
+
+  val entrance = in.entrance
+  val accessType = in.accessType
+  val uop = in.uop
+  val robIdx = uop.robIdx
+  val paddr = in.paddr.get
+  val isMMIOReplay = in.isMMIOReplay()
+  val isNCReplay = in.isNCReplay()
+  val isUncacheReplay = in.isUncacheReplay()
+  val isPrefetch = accessType.isPrefetch()
+  val isHwPrefetch = accessType.isHwPrefetch()
+  val isSwPrefetch = accessType.isSwPrefetch()
+  val isUnalignHead = in.unalignHead.get
+  val isUnalignTail = LoadEntrance.isUnalignTail(entrance)
+  val isUnalign = isUnalignHead || isUnalignTail
+  val isStoreSetHit = uop.storeSetHit
+  val waitRobIdx = uop.waitForRobIdx
+
+  /**
+    * Redirect
+    *
+    * Some terminology explanations:
+    * Both **kill** and **endPipe** indicate that a request should terminate at the current stage without proceeding to
+    * the next pipeline stage. However their difference lies in:
+    * - **kill** means the request is canceled due to some reason. A killed request will not produce any side effects
+    *   on other modules or micro-arch states, such as not writing back to Backend
+    * - **endPipe** means the request is not canceled but completes execution at this stage, without advancing to the
+    *   next stage, but it may produce side effects on micro-arch
+    */
+  val redirect = io.redirect
+  val kill = io.kill || robIdx.needFlush(redirect)
+  val endPipe = isHwPrefetch
+
+  /**
+    * PMP result & exception handling
+    */
+  val pmp = io.pmp
+  val pbmt = in.pbmt.get
+  val tlbAccessResult = in.tlbAccessResult.get
+  val tlbHit = TlbAccessResult.isHit(tlbAccessResult)
+  val tlbMiss = TlbAccessResult.isMiss(tlbAccessResult)
+  val tlbNotMiss = TlbAccessResult.isNotMiss(tlbAccessResult)
+  val tlbUnaccessable = uop.exceptionVec(loadAccessFault) ||
+    uop.exceptionVec(loadPageFault) ||
+    uop.exceptionVec(loadGuestPageFault)
+  val tlbAccessable = !tlbUnaccessable
+  val pmpUnaccessable = pmp.ld && tlbHit
+
+  val isNC = tlbHit && tlbAccessable && Pbmt.isNC(pbmt)
+  val isMMIO = tlbHit && tlbAccessable && (Pbmt.isIO(pbmt) || Pbmt.isPMA(pbmt) && pmp.mmio)
+  val isUncache = isNC || isMMIO
+  val isVector  = accessType.isVector()
+
+  // load access fault
+  val afUnaccessable = uop.exceptionVec(loadAccessFault) || pmpUnaccessable
+  val afVectorUncache = accessType.isVector() && isUncache
+  val afTagError = io.dcacheResp.bits.tag_error && tlbHit && io.csrCtrl.cache_error_enable
+  val afForwardDenied = Wire(Bool())
+  val afBypassDenied = Wire(Bool())
+  val af = afUnaccessable || afVectorUncache || afTagError || afForwardDenied || afBypassDenied
+  // load address misaligned
+  val am = !in.align.get && accessType.isScalar() && isUncache && !pmpUnaccessable
+  // hardware error
+  val hweForwardCorrupt = Wire(Bool())
+  val hweBypassCorrupt = Wire(Bool())
+  val hwe = uop.exceptionVec(hardwareError) || hweForwardCorrupt || hweBypassCorrupt
+
+  val exceptionVec = WireInit(uop.exceptionVec)
+  val exception = TriggerAction.isDmode(uop.trigger) || ExceptionNO.selectByFu(exceptionVec, LduCfg).asUInt.orR
+  exceptionVec(loadAddrMisaligned) := am
+  exceptionVec(loadAccessFault) := af
+  exceptionVec(hardwareError) := hwe
+
+  /**
+    * Data forward response
+    */
+  // SQ / Sbuffer / Uncache forward
+  val storeForwardMask = io.sqForwardResp.bits.forwardMask.asUInt |
+    io.sbufferForwardResp.bits.forwardMask.asUInt |
+    io.uncacheForwardResp.bits.forwardMask.asUInt
+
+  val sqAddrInvalid = io.sqForwardResp.valid && io.sqForwardResp.bits.addrInvalid.valid
+  val sqAddrInvalidSqIdx = io.sqForwardResp.bits.addrInvalid.bits
+  val sqDataInvalid = io.sqForwardResp.valid && io.sqForwardResp.bits.dataInvalid.valid
+  val sqDataInvalidSqIdx = io.sqForwardResp.bits.dataInvalid.bits
+
+  val matchInvalid = io.sqForwardResp.valid && io.sqForwardResp.bits.matchInvalid ||
+    io.sbufferForwardResp.valid && io.sbufferForwardResp.bits.matchInvalid ||
+    io.uncacheForwardResp.valid && io.uncacheForwardResp.bits.matchInvalid
+
+  val forwardInvalid = io.sqForwardResp.valid && io.sqForwardResp.bits.forwardInvalid
+
+  // MSHR / TileLink-D channel
+  val mshrForwardDenied = io.mshrForwardResp.valid && io.mshrForwardResp.bits.denied
+  val tldForwardDenied = io.tldForwardResp.valid && io.tldForwardResp.bits.denied
+  val mshrForwardCorrupt = io.mshrForwardResp.valid && io.mshrForwardResp.bits.corrupt && !io.mshrForwardResp.bits.denied
+  val tldForwardCorrupt = io.tldForwardResp.valid && io.tldForwardResp.bits.corrupt && !io.tldForwardResp.bits.denied
+  afForwardDenied := mshrForwardDenied || tldForwardDenied
+  hweForwardCorrupt := mshrForwardCorrupt || tldForwardCorrupt
+
+  val dcacheFullForward = io.mshrForwardResp.valid || io.tldForwardResp.valid
+  val uncacheFullForward = (~io.uncacheForwardResp.bits.forwardMask.asUInt & in.mask) === 0.U && !sqDataInvalid
+  val storeFullForward = (~storeForwardMask & in.mask) === 0.U && !sqDataInvalid
+  val fullForward = storeFullForward || dcacheFullForward
+  val needDCacheAccess = !fullForward && !isUncache && !isUncacheReplay
+
+  // Uncache bypass
+  afBypassDenied := io.uncacheBypassResp.valid && io.uncacheBypassResp.bits.nderr
+  hweBypassCorrupt := io.uncacheBypassResp.valid && io.uncacheBypassResp.bits.derr
+
+  /**
+    * DCache early response
+    */
+  val dcacheMiss = io.dcacheResp.bits.miss
+  val mshrNack = io.dcacheMSHRNack
+  val bankConflict = io.dcacheBankConflict
+
+  /**
+    * Nuke query from StoreUnit
+    */
+  val prevStageNuke = in.cause.get(C_NK)
+  val nukeQueryValids = io.staNukeQueryReq.map(_.valid)
+  val nukeQueryReqs = io.staNukeQueryReq.map(_.bits)
+  val nukePAddrMatches = nukeQueryReqs.map(req => nukePAddrMatch(req.paddr, req.matchType, paddr))
+  val nukeStoreOlders = nukeQueryReqs.map(req => isAfter(robIdx, req.robIdx))
+  val nukeMaskMatches = nukeQueryReqs.map(req => (req.mask & in.mask).orR)
+  val nuke = Cat((nukeQueryValids lazyZip nukePAddrMatches lazyZip nukeStoreOlders lazyZip nukeMaskMatches).map {
+    case (valid, paddrMatch, storeOlder, maskMatch) => valid && paddrMatch && storeOlder && maskMatch
+  }).orR && tlbNotMiss || prevStageNuke
+  // if nuke is storeSetHit store, let load fast replay
+  val prevStageFastReplayNukeFirst = in.fastReplayNukeFirst.get
+  val fastReplayNukeFirst = isStoreSetHit && nukeQueryReqs.zip(nukeQueryValids).map{case (req, v) =>
+    req.robIdx === waitRobIdx && v}.reduce(_ || _) || prevStageFastReplayNukeFirst
+
+  /**
+    * Preliminary assessment of the load exit
+    *
+    * We categorize the request exit into one of the following 3 categories:
+    * 1. Trouble maker: loads that may need replay and may require RAR / RAW violation check, including exits:
+    *   1.1 writeback: no need to replay or fast replay, but need to do violation check
+    *   1.2 replay: no need to do violation check (or revoke later)
+    *   1.3 fast replay: no need to do violation check (or revoke later). It should be noted that requests marked as
+    *     `fastReplay` here are not guaranteed to undergo fast replay. They may fail arbitration at s0, in which case
+    *     such requests will enter LRQ
+    *     1.3.1 loads that have already fast replay should not fast replay again
+    *     1.3.2 loads that are bank conflict, or mshr nacked, or nuked may fast replay, except there are other higher-
+    *       priority replay causes
+    *     1.3.3 loads that are unaligned should not fast replay for simplicity
+    * 2. Always writeback: loads that definitely do not require replay or RAR / RAW violation check, and can be directly
+    *   written back to Backend, including:
+    *   - exception
+    *   - MMIO replay
+    * 3. Prefetch: hardware or software prefetch requests, which do not require replay or violation check, but never
+    *   write back to Backend
+    */
+  // 2. Always writeback
+  val alwaysWriteback = exception || isMMIOReplay
+  // 1. Trouble maker
+  val troubleMaker = !isPrefetch && !alwaysWriteback
+  // 1.3 fast replay
+  val cause = Wire(in.cause.get.cloneType)
+  val fastReplayMSHRNack = cause(C_DR) && !hasHigherPriorityCauses(cause, C_DR)
+  val fastReplayBankConflict = cause(C_BC) && !hasHigherPriorityCauses(cause, C_BC)
+  // if store pipeline is waiting for store, send this load to fast replay
+  val fastReplayNuke = cause(C_NK) &&  // TODO: use C_RAR or C_NK?
+    !hasHigherPriorityCauses(VecInit(cause.patch(C_MA, Seq(cause(C_MA) && !fastReplayNukeFirst), 1)), C_RAR)
+  val fastReplay = !LoadEntrance.isFastReplay(entrance) && // 1.3.1
+    (fastReplayMSHRNack || fastReplayBankConflict || fastReplayNuke) && // 1.3.2
+    !isUnalign && !tlbMiss // 1.3.3, if tlb miss, should not to fast replay
+
+  /**
+    * Nuke query to LQRAR / LQRAW
+    *
+    * For timing considerations, violation check requests issued in s2 do not need to be accurate. But MUST ensure that
+    * accurate `revoke` signals are given in s3 to withdraw requests that do not require violation check.
+    */
+  val nukeQueryReqValid = troubleMaker && !(prevStageNuke || cause(C_BC))
+  val nukeQueryReq = Wire(new LoadNukeQueryReq)
+  nukeQueryReq.robIdx := robIdx
+  nukeQueryReq.paddr := paddr
+  nukeQueryReq.lqIdx := uop.lqIdx
+  nukeQueryReq.sqIdx := uop.sqIdx
+  nukeQueryReq.dataValid := fullForward || isNCReplay || needDCacheAccess && !dcacheMiss && !bankConflict
+  nukeQueryReq.nc := isNCReplay
+  nukeQueryReq.mask := in.mask
+  nukeQueryReq.isRVC := uop.isRVC
+  nukeQueryReq.ftqPtr := uop.ftqPtr
+  nukeQueryReq.ftqOffset := uop.ftqOffset
+  nukeQueryReq.pc := uop.pc
+  nukeQueryReq.debugInfo := uop.perfDebugInfo
+
+  val rarNack = io.rarNukeQueryReq.valid && !io.rarNukeQueryReq.ready
+  val rawNack = io.rawNukeQueryReq.valid && !io.rawNukeQueryReq.ready
+
+  /**
+    * Load replay
+    */
+  val shouldReplay = cause.asUInt.orR || in.shouldFastReplay.get // including fast replay
+  cause(C_UNCACHE) := troubleMaker && Mux(
+    isUncacheReplay,
+    isNCReplay && (sqDataInvalid || rarNack || rawNack || nuke || forwardInvalid),
+    isUncache
+  )
+  cause(C_MA) := troubleMaker && uop.storeSetHit && sqAddrInvalid
+  cause(C_TM) := troubleMaker && tlbMiss
+  cause(C_FF) := troubleMaker && sqDataInvalid
+  cause(C_DR) := troubleMaker && needDCacheAccess && mshrNack
+  cause(C_DM) := troubleMaker && needDCacheAccess && dcacheMiss
+  cause(C_WF) := false.B
+  cause(C_BC) := troubleMaker && (needDCacheAccess && bankConflict || isUnalignHead && in.shouldFastReplay.get)
+  cause(C_RAR) := troubleMaker && rarNack
+  cause(C_RAW) := troubleMaker && rawNack
+  cause(C_NK) := troubleMaker && nuke
+  cause(C_MF) := false.B
+  cause(C_SMF) := troubleMaker && forwardInvalid
+
+  def hasHigherPriorityCauses(cause: Vec[Bool], index: Int): Bool = {
+    if (index == 0) false.B
+    else Cat(cause.take(index)).orR
+  }
+
+  /**
+    * Writeback and wakeup
+    *
+    * For timing considerations, the control signals required for writeback and wakeup in the S3 stage are pre-computed
+    * here. Both signals are asserted only when there is no need for replay and no need to access the uncache path, i.e.
+    * when load completed execution. The differences between them are as follows:
+    * 1. `wakeup` should not asserted when exception occurs
+    * 2. `wakeup` should not asserted in case of a vaddr / paddr mismatch (debatable yet)
+    */
+  val shouldWakeup = !shouldReplay && !isUncache && !exception && !isSwPrefetch
+  val shouldWriteback = shouldWakeup || exception || matchInvalid || isSwPrefetch
+
+  /**
+    * Pipeline connect
+    */
+  val pipeOutValid = RegInit(false.B)
+  val pipeOutBits = Reg(new LoadStageIO) // TODO
+  when (kill || endPipe) { pipeOutValid := false.B }
+  .elsewhen (pipeIn.fire) { pipeOutValid := true.B }
+  .elsewhen (pipeOut.fire) { pipeOutValid := false.B }
+
+  val stageInfo = Wire(pipeOut.bits.cloneType)
+  connectSamePort(stageInfo, in)
+  stageInfo.uop.flushPipe := false.B
+  stageInfo.uop.exceptionVec := exceptionVec
+  stageInfo.uop.vpu.vstart := Mux(
+    LoadEntrance.isReplay(entrance) || LoadEntrance.isFastReplay(entrance),
+    uop.vpu.vstart,
+    in.vecVaddrOffset.get >> uop.vpu.veew
+  )
+  stageInfo.pmp.get := pmp
+  stageInfo.nc.get := isNC
+  stageInfo.mmio.get := isMMIO
+  stageInfo.mshrId.get := io.dcacheResp.bits.mshr_id
+  stageInfo.cause.get := cause
+  stageInfo.handledByMSHR.get := io.dcacheResp.bits.handled
+  stageInfo.dataInvalidSqIdx.get := sqDataInvalidSqIdx
+  stageInfo.addrInvalidSqIdx.get := sqAddrInvalidSqIdx
+  stageInfo.tlbId.get := io.tlbHint.id
+  stageInfo.tlbFull.get := io.tlbHint.full
+  // Pre-process for s3
+  stageInfo.troubleMaker.get := troubleMaker
+  stageInfo.shouldFastReplay.get := in.shouldFastReplay.get || fastReplay && !exception
+  stageInfo.matchInvalid.get := matchInvalid && troubleMaker
+  stageInfo.shouldWakeup.get := shouldWakeup
+  stageInfo.shouldWriteback.get := shouldWriteback
+
+  when (pipeIn.fire) { pipeOutBits := stageInfo }
+
+  /**
+    * IO assignment
+    */
+  io_pipeOut.get.valid := pipeOutValid
+  io_pipeOut.get.bits := pipeOutBits
+  io_pipeIn.get.ready := !pipeOutValid || kill || endPipe || pipeOut.ready
+
+  io.unalignTailValid := pipeIn.valid && isUnalignTail
+
+  io.dcacheKill := kill || exception || isUncache || isUncacheReplay
+  io.dcacheResp.ready := true.B
+
+  io.rarNukeQueryReq.valid := nukeQueryReqValid && pipeIn.valid
+  io.rarNukeQueryReq.bits := nukeQueryReq
+  io.rawNukeQueryReq.valid := nukeQueryReqValid && pipeIn.valid
+  io.rawNukeQueryReq.bits := nukeQueryReq
+
+  // TODO: Currently, we don't train prefetcher on vector request, because vector instruction PC is incorrect.
+  io.prefetchTrain.valid := pipeIn.valid && tlbHit && !exception && !isUncache && !isUncacheReplay &&
+    in.isFirstIssue() && !isVector
+  io.prefetchTrain.bits := DontCare
+  io.prefetchTrain.bits.uop := uop
+  io.prefetchTrain.bits.vaddr := in.vaddr
+  io.prefetchTrain.bits.paddr := paddr
+  io.prefetchTrain.bits.miss := io.dcacheResp.bits.miss
+  io.prefetchTrain.bits.isFirstIssue := in.isFirstIssue()
+  io.prefetchTrain.bits.meta_prefetch := io.dcacheResp.bits.meta_prefetch
+  io.prefetchTrain.bits.meta_access := io.dcacheResp.bits.meta_access
+  io.prefetchTrain.bits.is_from_hw_pf := accessType.isHwPrefetch()
+  io.prefetchTrain.bits.refillLatency := io.dcacheResp.bits.refill_latency
+
+  io.debugInfo.isBankConflict := pipeIn.valid && !kill && cause(C_BC)
+  io.debugInfo.isDCacheMiss := pipeIn.valid && !kill && cause(C_DM)
+  io.debugInfo.isDCacheFirstMiss := pipeIn.valid && !kill && cause(C_DM) && in.isFirstIssue()
+  io.debugInfo.isDCacheRealMiss := pipeIn.valid && !kill && io.dcacheResp.bits.real_miss
+  io.debugInfo.isForwardFail := pipeIn.valid && !kill && cause(C_FF)
+  io.debugInfo.isTlbNotMiss := pipeIn.valid && !kill && tlbNotMiss
+  io.debugInfo.robIdx := robIdx.value
+  io.debugInfo.hasROBEntry := in.hasROBEntry
+  io.debugInfo.paddr.valid := pipeIn.valid && !kill
+  io.debugInfo.paddr.bits := paddr
+  io.debugInfo.pc := uop.pc
+
+  /**
+   *  Perf counters
+   */
+  val fire = pipeIn.fire && !kill
+  XSPerfAccumulate("valid", pipeIn.valid)
+  XSPerfAccumulate("fire", fire)
+  XSPerfAccumulate("fire_first_issue", fire && in.isFirstIssue())
+  XSPerfAccumulate("dcache_miss", fire && io.dcacheResp.bits.miss)
+  XSPerfAccumulate("dcache_miss_first_issue", fire && io.dcacheResp.bits.miss && in.isFirstIssue())
+  XSPerfAccumulate("store_full_forward", fire && storeFullForward)
+  XSPerfAccumulate("nc_store_full_forward", fire && uncacheFullForward)
+  XSPerfAccumulate("mshr_full_forward", fire && io.mshrForwardResp.valid)
+  XSPerfAccumulate("tld_full_forward", fire && io.tldForwardResp.valid)
+  XSPerfAccumulate("full_forward", fire && fullForward)
+  XSPerfAccumulate("prefetch", fire && isPrefetch)
+  XSPerfAccumulate("prefetch_ignore", fire && isPrefetch && io.dcacheMSHRNack)
+  XSPerfAccumulate("prefetch_miss", fire && isPrefetch && io.dcacheResp.bits.miss)
+  XSPerfAccumulate("prefetch_hit", fire && isPrefetch && !io.dcacheResp.bits.miss)
+  XSPerfAccumulate("prefetch_accept", fire && isPrefetch && io.dcacheResp.bits.miss && !io.dcacheMSHRNack)
+  XSPerfAccumulate("forward_tld_replay", fire && in.forwardDChannel.get)
+  XSPerfAccumulate("forward_tld_replay_succeed_mshr", fire && in.forwardDChannel.get && io.mshrForwardResp.valid)
+  XSPerfAccumulate("forward_tld_replay_succeed_tld", fire && in.forwardDChannel.get && io.tldForwardResp.valid)
+  XSPerfAccumulate("nc_replay", fire && isNCReplay)
+  XSPerfAccumulate("mmio_replay", fire && isMMIOReplay)
+  XSPerfAccumulate("nc_raw_violation", fire && isNCReplay && cause(C_NK))
+  XSPerfAccumulate("nc_rar_nack", fire && isNCReplay && cause(C_RAR))
+  XSPerfAccumulate("nc_raw_nack", fire && isNCReplay && cause(C_RAW))
+  XSPerfAccumulate("nc_forward_not_ready", fire && isNCReplay && (cause(C_MA) || cause(C_FF)))
+  XSPerfAccumulate("nc_forward_match_invalid", fire && isNCReplay && matchInvalid)
+
+  val perfEvents = Seq(
+    ("s2_in_fire", pipeIn.fire),
+    ("s2_dcache_miss", pipeIn.fire && !kill && cause(C_DM)),
+    ("s2_hw_pf_access", pipeIn.fire && isHwPrefetch),
+    ("s2_hw_pf_miss", pipeIn.fire && isHwPrefetch && !kill && cause(C_DM))
+  )
+  generatePerfEvent()
+}
+
+class LoadUnitS3(param: ExeUnitParams)(
+  implicit p: Parameters,
+  override implicit val s: LoadStage = LoadS3()
+) extends LoadUnitStage(param) {
+  val io = IO(new Bundle() {
+    val redirect = Flipped(ValidIO(new Redirect))
+    val kill = Input(Bool())
+    val unalignTailValid = Input(Bool())
+
+    // DCache response
+    val dcacheError = Input(Bool())
+
+    // Unalign head from S4
+    val unalignConcat = Flipped(ValidIO(new LoadStageIO))
+
+    // Writeback to Backend / LQ / VLMergeBuffer
+    val ldout = new NewExuOutput(param)
+    val lqWrite = DecoupledIO(new LqWriteBundle)
+    val vecldout = Decoupled(new VecPipelineFeedbackIO(isVStore = false))
+
+    // Fast replay
+    val fastReplay = DecoupledIO(new FastReplayIO)
+
+    // RAR / RAW revoke and RAR response
+    val rarNukeQueryResp = Flipped(ValidIO(new LoadNukeQueryResp))
+    val revokeLastCycle, revokeLastLastCycle = Output(Bool())
+
+    /**
+      * Rollback and re-fetch from IFU, including:
+      * 1. RAR violation
+      * 2. vaddr-paddr mismatch happens in STLF
+      */
+    val rollback = ValidIO(new Redirect)
+
+    // Exception info
+    val exceptionInfo = ValidIO(new MemExceptionInfo)
+
+    // Load cancel
+    val cancel = Output(Bool())
+
+    // CSR control signals
+    val csrCtrl = Flipped(new CustomCSRCtrlIO)
+
+    // Debug info
+    val debugInfo = Output(new Bundle() {
+      val isReplayFast = Bool()
+      val isReplaySlow = Bool()
+      val isReplayRS = Bool()
+      val isReplay = Bool()
+      val replayCause = Vec(LoadReplayCauses.allCauses, Bool())
+      val replayCnt = UInt(XLEN.W)
+      val robIdx = UInt(log2Ceil(RobSize).W)
+    })
+  })
+
+  val pipeIn = io_pipeIn.get
+  val pipeOut = io_pipeOut.get
+  val in = pipeIn.bits
+
+  val entrance = in.entrance
+  val accessType = in.accessType
+  val uop = in.uop
+  val robIdx = uop.robIdx
+  val isScalar = accessType.isScalar()
+  val isVector = accessType.isVector()
+  val isUnalignHead = in.unalignHead.get
+  val isUnalignTail = LoadEntrance.isUnalignTail(entrance)
+  val troubleMaker = in.troubleMaker.get
+  val cause = in.cause.get
+  val shouldReplay = cause.asUInt.orR || in.shouldFastReplay.get
+
+  assert(!pipeIn.valid || !accessType.isHwPrefetch(), "HwPrefetch should be killed in S2")
+  assert(!io.vecldout.valid || io.vecldout.ready, "Writeback to VLMergeBuffer should always be ready")
+
+  /**
+    * Redirect
+    */
+  val redirect = io.redirect
+  val kill = io.kill || robIdx.needFlush(redirect)
+  val endPipe = !(isUnalignHead && io.unalignTailValid) // unalign head will flow to next stage
+
+  /**
+    * Unalign concatenation
+    *
+    * We divide the `shouldWriteback` into two scenarios:
+    * 1. **s4HeadAlwaysWriteback**: Head does not need to consider tail – it writes back directly, which applies when
+    *   the head is an exception or a matchInvalid case
+    * 2. **s4HeadWritebackDependOnTail** Head needs to consider tail – it decides whether to write back, replay, or take
+    *   other actions based on the tail. This applies when the head can be written back normally (i.e., no exception or
+    *   matchInvalid case)
+    */
+  val s4HeadValid = io.unalignConcat.valid
+  val s4Head = io.unalignConcat.bits
+  val s4HeadExceptionVec = s4Head.uop.exceptionVec
+  val s4HeadVAddr = s4Head.vaddr
+  val s4HeadMask = s4Head.mask
+  val s4HeadPAddr = s4Head.paddr.get
+  val s4HeadReplayCause = s4Head.cause.get
+  val s4HeadMatchInvalid = s4Head.matchInvalid.get
+  val s4HeadShouldWakeup = s4Head.shouldWakeup.get
+  val s4HeadAlwaysWriteback = s4Head.headAlwaysWriteback.get
+  val s4HeadWritebackDependOnTail = s4Head.writebackDependOnTail.get
+  val s4HeadHasException = s4Head.hasException.get
+  val s4HeadShouldReplay = s4HeadReplayCause.asUInt.orR
+  val s4HeadShouldRARViolation = s4Head.shouldRarViolation.get
+  val s4HeadIsReplay = LoadEntrance.isReplay(s4Head.entrance)
+  val s4HeadCacheMiss = s4Head.cause.get(C_DM)
+  val s4HeadMshrId    = s4Head.mshrId.get
+
+  val vaddr = Mux(s4HeadValid, s4HeadVAddr, in.vaddr)
+  val paddr = Mux(s4HeadValid, s4HeadPAddr, in.paddr.get)
+  val mask = Mux(s4HeadValid, s4HeadMask, in.mask)
+  /**
+    * DCache error handling & exception handling
+    *
+    * Noted that exception can affect control signals for wakeup and writeback
+    */
+  val dcacheError = EnableAccurateLoadError.B && io.csrCtrl.cache_error_enable && troubleMaker && io.dcacheError
+  val s3ExceptionVec = WireInit(uop.exceptionVec)
+  val s3Exception = ExceptionNO.selectByFu(s3ExceptionVec, LduCfg).asUInt.orR || TriggerAction.isDmode(uop.trigger)
+  val exceptionVec = Mux(
+    s4HeadValid && s4HeadHasException,
+    s4HeadExceptionVec,
+    s3ExceptionVec
+  )
+  val exception = s4HeadValid && s4HeadHasException || s3Exception
+  val exceptionFullva = Mux(s4HeadValid && s4HeadHasException, s4Head.fullva, in.fullva)
+  val exceptionGpaddr = Mux(s4HeadValid && s4HeadHasException, s4Head.gpaddr.get, in.gpaddr.get)
+  val exceptionIsForVSnonLeafPTE = Mux(
+    s4HeadValid && s4HeadHasException,
+    s4Head.isForVSnonLeafPTE.get,
+    in.isForVSnonLeafPTE.get
+  )
+  val exceptionVaNeedExt = Mux(
+    s4HeadValid && s4HeadHasException,
+    s4Head.tlbException.get.vaNeedExt,
+    in.tlbException.get.vaNeedExt
+  )
+
+  val s3ShouldWakeup = in.shouldWakeup.get && !dcacheError
+  val s3ShouldWriteback = in.shouldWriteback.get || dcacheError
+  val shouldWakeup = s3ShouldWakeup && (!s4HeadValid || s4HeadShouldWakeup)
+  val shouldWriteback = Mux(
+    s4HeadValid,
+    s4HeadAlwaysWriteback || s4HeadWritebackDependOnTail && s3ShouldWriteback,
+    s3ShouldWriteback
+  )
+
+  s3ExceptionVec(hardwareError) := uop.exceptionVec(hardwareError) || dcacheError
+
+  /**
+    * Fast replay
+    */
+  val shouldFastReplay = in.shouldFastReplay.get
+  val allowFastReplay = io.fastReplay.ready
+  val doFastReplay = shouldFastReplay && allowFastReplay
+  val fastReplay = Wire(new FastReplayIO)
+  connectSamePort(fastReplay, in)
+  fastReplay.cause.get := 0.U.asTypeOf(fastReplay.cause.get)
+
+  /**
+    * RAR / RAW revoke
+    */
+  val s3RevokeException = s3Exception
+  val s3RevokeReplay = cause.asUInt.orR
+  val s3Revoke = s3RevokeException || s3RevokeReplay
+  val s4HeadRevoke = s4HeadHasException || s4HeadShouldReplay
+  val revokeLastCycle = s3Revoke || s4HeadValid && s4HeadRevoke
+  val revokeLastLastCycle = s4HeadValid && !s4HeadRevoke && s3Revoke
+
+  /**
+    * Pipeline flush
+    * - RAR violation
+    * - vaddr / paddr mismatch in STLF
+    */
+  // RAR violation
+  val rarResp = io.rarNukeQueryResp
+  val s3RarViolation = rarResp.valid && rarResp.bits.nuke && io.csrCtrl.ldld_vio_check_enable && !s3Exception
+  val rarViolation = s3RarViolation || s4HeadValid && s4HeadShouldRARViolation
+  // vaddr / paddr mismatch in STLF
+  val s3MatchInvalid = in.matchInvalid.get && !s3Exception
+  val matchInvalid = s3MatchInvalid || s4HeadValid && s4HeadMatchInvalid
+
+  val rollbackValid = pipeIn.valid && (rarViolation || matchInvalid) && endPipe
+  val rollbackLevel = Mux(matchInvalid, RedirectLevel.flush, RedirectLevel.flushAfter)
+
+  /**
+    * Load cancel
+    */
+  // For unaligned head (endPipe = 0), always cancel, because the unaligned head needs to be combined with tail.
+  val cancel = pipeIn.valid && (!endPipe || !shouldWakeup && isScalar)
+
+  /**
+    * Writeback to Backend / LQ / VLMergeBuffer
+    */
+  // Writeback to Backend
+  val ldoutValid = pipeIn.valid && shouldWriteback && !isVector && endPipe
+  val ldout = Wire(new NewExuOutput(param))
+  ldout.toIntRf.foreach { case port =>
+    port.valid := uop.rfWen && pipeIn.valid && endPipe && shouldWakeup
+    port.bits := DontCare // assign data from LoadUnitDataPath
+  }
+  ldout.toFpRf.foreach { case port =>
+    port.valid := uop.fpWen && pipeIn.valid && endPipe && shouldWakeup
+    port.bits := DontCare
+  }
+  ldout.pdest := uop.pdest
+  ldout.toRob.valid := ldoutValid
+  ldout.toRob.bits.robIdx := uop.robIdx
+  ldout.toRob.bits.exceptionVec.get := exceptionVec
+  ldout.toRob.bits.lqIdx.get := uop.lqIdx
+  ldout.toRob.bits.trigger.get := uop.trigger
+  ldout.toRob.bits.isRVC.get := uop.isRVC
+  ldout.isFromLoadUnit.get := true.B
+  ldout.debug.isMMIO := in.isMMIOReplay()
+  ldout.debug.isNCIO := in.isNCReplay() && in.pmp.get.mmio
+  ldout.debug.isPerfCnt := false.B
+  ldout.debug.paddr := paddr
+  ldout.debug.vaddr := vaddr
+  ldout.perfDebugInfo.foreach(_ := uop.perfDebugInfo)
+  ldout.debug_seqNum.foreach(_ := uop.debug_seqNum)
+
+  // Writeback to LQ
+  val lqWriteValid = pipeIn.valid && !doFastReplay && endPipe
+  val lqWriteReady = io.lqWrite.ready
+  val lqWriteCause = Mux(s4HeadValid && s4HeadShouldReplay, s4HeadReplayCause, cause)
+  val lqWriteCauseOH = PriorityEncoderOH(lqWriteCause)
+  val lqWrite = Wire(new LqWriteBundle)
+  val lqWriteMshrId = Mux(s4HeadCacheMiss && s4HeadValid, s4HeadMshrId, in.mshrId.get)
+  // TODO: remove useless fields after old LoadUnit is removed
+  lqWrite.uop := uop
+  lqWrite.uop.exceptionVec := exceptionVec
+  lqWrite.vaddr := vaddr
+  lqWrite.fullva := exceptionFullva
+  lqWrite.vaNeedExt := exceptionVaNeedExt
+  lqWrite.paddr := paddr
+  lqWrite.gpaddr := exceptionGpaddr
+  lqWrite.mask := mask
+  lqWrite.data := DontCare // TODO: remove this
+  lqWrite.wlineflag := false.B // TODO: remove this
+  lqWrite.miss := cause(C_DM) // TODO: remove this
+  lqWrite.tlbMiss := TlbAccessResult.isMiss(in.tlbAccessResult.get)// TODO: remove this
+  lqWrite.ptwBack := false.B // TODO: remove this
+  lqWrite.af := exceptionVec(loadAccessFault) // TODO: remove this
+  lqWrite.nc := in.nc.get || in.isNCReplay()
+  lqWrite.mmio := in.mmio.get
+  lqWrite.memBackTypeMM := !in.pmp.get.mmio
+  lqWrite.hasException := false.B // LQ is no longer responsible for handling exception for timing reason
+  lqWrite.isHyper := in.tlbException.get.isHyper
+  lqWrite.isForVSnonLeafPTE := exceptionIsForVSnonLeafPTE
+  lqWrite.isPrefetch := false.B // TODO: remove this
+  lqWrite.isHWPrefetch := false.B // TODO: remove this
+  lqWrite.forwardMask := DontCare // TODO: remove this
+  lqWrite.forwardData := DontCare // TODO: remove this
+  lqWrite.ldCancel := DontCare // TODO: remove this
+  lqWrite.isvec := isVector
+  lqWrite.isLastElem := DontCare // TODO: remove this
+  lqWrite.is128bit := in.size === MemorySize.Q.U
+  lqWrite.uop_unit_stride_fof := DontCare // TODO: remove this
+  lqWrite.usSecondInv := DontCare // TODO: remove this
+  lqWrite.elemIdx := in.elemIdx.get
+  lqWrite.alignedType := in.size
+  lqWrite.mbIndex := in.mbIndex.get
+  lqWrite.reg_offset := in.regOffset.get
+  lqWrite.elemIdxInsideVd := in.elemIdxInsideVd.get
+  lqWrite.is_first_ele := DontCare // TODO: remove this
+  lqWrite.vecBaseVaddr := DontCare
+  lqWrite.vecVaddrOffset := DontCare
+  lqWrite.vecTriggerMask := DontCare
+  lqWrite.vecActive := true.B // TODO: remove this
+  lqWrite.isLoadReplay := LoadEntrance.isReplay(entrance) || s4HeadIsReplay && s4HeadValid
+  lqWrite.isFastPath := DontCare // TODO: remove this
+  lqWrite.isFastReplay := DontCare // TODO: remove this
+  lqWrite.replayCarry := DontCare // TODO: remove this
+  lqWrite.isFirstIssue := DontCare // TODO: remove this
+  lqWrite.hasROBEntry := DontCare // TODO: remove this
+  lqWrite.mshrid := DontCare // TODO: remove this
+  lqWrite.handledByMSHR := in.handledByMSHR.get
+  lqWrite.replacementUpdated := DontCare // TODO: remove this
+  lqWrite.missDbUpdated := DontCare // TODO: remove this
+  lqWrite.forward_tlDchannel := DontCare // TODO: remove this
+  lqWrite.dcacheRequireReplay := DontCare // TODO: remove this
+  lqWrite.delayedLoadError := DontCare // TODO: remove this
+  lqWrite.lateKill := DontCare // TODO: remove this
+  lqWrite.feedbacked := DontCare // TODO: remove this
+  lqWrite.schedIndex := in.replayQueueIdx.get
+  lqWrite.tlbNoQuery := DontCare // TODO: remove this
+  lqWrite.isFrmMisAlignBuf := false.B // TODO: remove this
+  lqWrite.isMisalign := DontCare // TODO: remove this
+  lqWrite.isFinalSplit := DontCare // TODO: remove this
+  lqWrite.misalignWith16Byte := DontCare // TODO: remove this
+  lqWrite.misalignNeedWakeUp := DontCare // TODO: remove this
+  lqWrite.updateAddrValid := ldoutValid
+  lqWrite.rep_info.mshr_id := lqWriteMshrId
+  lqWrite.rep_info.full_fwd := false.B
+  lqWrite.rep_info.data_inv_sq_idx := in.dataInvalidSqIdx.get
+  lqWrite.rep_info.addr_inv_sq_idx := in.addrInvalidSqIdx.get
+  lqWrite.rep_info.rep_carry := DontCare
+  lqWrite.rep_info.last_beat := paddr(log2Up(refillBytes))
+  lqWrite.rep_info.cause := lqWriteCauseOH
+  lqWrite.rep_info.debug := uop.perfDebugInfo
+  lqWrite.rep_info.tlb_id := in.tlbId.get
+  lqWrite.rep_info.tlb_full := in.tlbFull.get
+  lqWrite.nc_with_data := in.isNCReplay() && !cause(C_UNCACHE)
+  lqWrite.data_wen_dup := DontCare // TODO: remove this
+
+  // Writeback to VLMergeBuffer
+  val vecldoutValid = pipeIn.valid && !kill && shouldWriteback && isVector && endPipe
+  val vecldout = Wire(new VecPipelineFeedbackIO(isVStore = false))
+  vecldout.mBIndex := in.mbIndex.get
+  vecldout.hit := !shouldReplay || lqWriteReady
+  vecldout.isvec := isVector
+  vecldout.flushState := DontCare
+  vecldout.sourceType := RSFeedbackType.lrqFull
+  vecldout.trigger := uop.trigger
+  vecldout.nc := false.B
+  vecldout.mmio := false.B
+  vecldout.exceptionVec := exceptionVec
+  vecldout.hasException := exception
+  vecldout.vaddr := exceptionFullva
+  vecldout.vaNeedExt := exceptionVaNeedExt
+  vecldout.gpaddr := exceptionGpaddr
+  vecldout.isForVSnonLeafPTE := exceptionIsForVSnonLeafPTE
+  vecldout.vstart := uop.vpu.vstart
+  vecldout.vecTriggerMask := in.vecTriggerMask.get
+  vecldout.elemIdx := in.elemIdx.get
+  vecldout.mask := in.mask
+  vecldout.alignedType := in.size
+  vecldout.reg_offset.get := in.regOffset.get
+  vecldout.elemIdxInsideVd.get := in.elemIdxInsideVd.get
+  vecldout.vecdata.get := DontCare // assign data from LoadUnitDataPath
+
+  /**
+    * Exception info
+    */
+  val exceptionInfoValid = ldoutValid && !in.isMMIOReplay() // MMIO replay sends exceptionInfo independently
+  val exceptionInfo = Wire(new MemExceptionInfo)
+  exceptionInfo.robIdx := robIdx
+  exceptionInfo.exceptionVec := exceptionVec
+  exceptionInfo.vaddr := exceptionFullva
+  exceptionInfo.gpaddr := exceptionGpaddr
+  exceptionInfo.isForVSnonLeafPTE := exceptionIsForVSnonLeafPTE
+  exceptionInfo.vaNeedExt := exceptionVaNeedExt
+  exceptionInfo.isHyper := in.tlbException.get.isHyper
+  exceptionInfo.uopIdx := 0.U.asTypeOf(UopIdx())
+  exceptionInfo.vl := 0.U
+  exceptionInfo.vstart := 0.U
+
+  /**
+    * Pipeline connect
+    */
+  val pipeOutValid = RegInit(false.B)
+  val pipeOutBits = Reg(new LoadStageIO)
+  when (kill || endPipe) { pipeOutValid := false.B }
+  .elsewhen (pipeIn.fire) { pipeOutValid := true.B }
+  .elsewhen (pipeOut.fire) { pipeOutValid := false.B }
+
+  // Consider only unalign head
+  val stageInfo = Wire(pipeOut.bits.cloneType)
+  connectSamePort(stageInfo, in)
+  stageInfo.uop.exceptionVec := s3ExceptionVec
+  stageInfo.matchInvalid.get := s3MatchInvalid
+  stageInfo.shouldWakeup.get := s3ShouldWakeup
+  stageInfo.shouldWriteback.get := s3ShouldWriteback
+  stageInfo.hasException.get := s3Exception
+  stageInfo.headAlwaysWriteback.get := s3Exception || s3MatchInvalid
+  stageInfo.writebackDependOnTail.get := s3ShouldWriteback && !s3Exception && !s3MatchInvalid
+  stageInfo.shouldRarViolation.get := s3RarViolation
+
+  when (pipeIn.fire) { pipeOutBits := stageInfo }
+
+  /**
+    * IO assignment
+    */
+  io_pipeOut.get.valid := pipeOutValid
+  io_pipeOut.get.bits := pipeOutBits
+  io_pipeIn.get.ready := !pipeOutValid || kill || endPipe || pipeOut.ready
+
+  io.ldout := ldout
+  io.lqWrite.valid := lqWriteValid
+  io.lqWrite.bits := lqWrite
+  io.vecldout.valid := vecldoutValid
+  io.vecldout.bits := vecldout
+
+  io.fastReplay.valid := pipeIn.valid && shouldFastReplay
+  io.fastReplay.bits := fastReplay
+
+  io.revokeLastCycle := revokeLastCycle
+  io.revokeLastLastCycle := revokeLastLastCycle
+
+  io.rollback.valid := rollbackValid
+  io.rollback.bits := DontCare
+  io.rollback.bits.isRVC := uop.isRVC
+  io.rollback.bits.robIdx := robIdx
+  io.rollback.bits.ftqIdx := uop.ftqPtr
+  io.rollback.bits.ftqOffset := uop.ftqOffset
+  io.rollback.bits.level := rollbackLevel
+  io.rollback.bits.target := uop.pc
+  io.rollback.bits.debug_runahead_checkpoint_id := uop.perfDebugInfo.runahead_checkpoint_id
+
+  io.exceptionInfo.valid := exceptionInfoValid
+  io.exceptionInfo.bits := exceptionInfo
+
+  io.cancel := cancel
+
+  io.debugInfo.isReplayFast := pipeIn.valid && !kill && doFastReplay
+  io.debugInfo.isReplaySlow := lqWriteValid && cause.asUInt.orR
+  io.debugInfo.isReplayRS := false.B // load never replays from RS
+  io.debugInfo.isReplay := pipeIn.valid && !kill && cause.asUInt.orR
+  io.debugInfo.replayCause := cause
+  io.debugInfo.replayCnt := 1.U
+  io.debugInfo.robIdx := robIdx.value
+
+  /**
+    * Perf counters
+    */
+  XSPerfAccumulate("rollback_total", rollbackValid)
+  XSPerfAccumulate("rollback_rar_violation", rollbackValid && rarViolation)
+  XSPerfAccumulate("rollback_match_invalid", rollbackValid && matchInvalid)
+  XSPerfAccumulate("nc_writeback", io.ldout.toRob.fire && (in.isNCReplay() || in.nc.get))
+  XSPerfAccumulate("nc_exception", io.ldout.toRob.fire && (in.isNCReplay() || in.nc.get) && exception)
+  XSPerfAccumulate("nc_rar_violation", pipeIn.valid && in.isNCReplay() && rarViolation)
+
+  // source occupy others but fail perf counter
+  val executeFail = lqWriteValid && lqWriteCause.asUInt.orR || pipeIn.valid && shouldFastReplay
+  for (i <- 0 until LoadEntrance.num) {
+    val highPrioNume = LoadEntrance.findNameById(i)
+    for (j <- i + 1 until LoadEntrance.num) {
+      val lowPrioNume = LoadEntrance.findNameById(j)
+      println(s"[${param.name}] Add S0 Occupy PerfEvents of ${highPrioNume} oocupy ${lowPrioNume}, index: ${i} and ${j}")
+      val enable = pipeIn.bits.occupySource(j.U) && pipeIn.bits.entrance(i.U)
+      XSPerfAccumulate(s"${highPrioNume}_occupy_${lowPrioNume}", executeFail && enable)
+    }
+  }
+
+  // source execute success perfEvent
+  for (i <- 0 until LoadEntrance.num) {
+    val sourceNum = LoadEntrance.findNameById(i)
+    println(s"[${param.name}] Add execute successed PerfEvents of ${sourceNum}, index: ${i}")
+    val enable = pipeIn.bits.entrance(i.U) && ldoutValid // success writeback
+    XSPerfAccumulate(s"${sourceNum}_execute_success", enable)
+  }
+
+  // source execute failed perfEvent
+  for (i <- 0 until LoadEntrance.num) {
+    val sourceNum = LoadEntrance.findNameById(i)
+    println(s"[${param.name}] Add execute failed PerfEvents of ${sourceNum}, index: ${i}")
+    val enable = pipeIn.bits.entrance(i.U) && executeFail
+    XSPerfAccumulate(s"${sourceNum}_execute_fail", enable)
+  }
+
+  /**
+   * PerfCCT
+   */
+  val perfCCTReplayEn = pipeIn.valid && !kill && endPipe && lqWriteCause.asUInt.orR
+  val perfCCTReplayCause = ParallelPriorityMux(Seq(
+    lqWriteCause(C_TM) -> PerfCCT.ReplayReason.TLBMiss.id.U,
+    lqWriteCause(C_DM) -> PerfCCT.ReplayReason.CacheMiss.id.U,
+    lqWriteCause(C_RAR) -> PerfCCT.ReplayReason.RARReplay.id.U,
+    lqWriteCause(C_RAW) -> PerfCCT.ReplayReason.RAWReplay.id.U,
+    lqWriteCause(C_BC) -> PerfCCT.ReplayReason.BankConflict.id.U,
+    lqWriteCause(C_SMF) -> PerfCCT.ReplayReason.STDForwardFail.id.U, // TODO
+    lqWriteCause(C_FF) -> PerfCCT.ReplayReason.STDForwardFail.id.U,
+    lqWriteCause(C_DR) -> PerfCCT.ReplayReason.DcacheStall.id.U,
+    true.B -> PerfCCT.ReplayReason.OtherReplay.id.U
+  ))
+  val perfCCTRecordAddrEn = io.ldout.toRob.fire && !kill
+  val timer = GTimer()
+  PerfCCT.updateInstMeta(
+    uop.debug_seqNum, PerfCCT.InstDetail.ReplayStr.id.U, perfCCTReplayCause, perfCCTReplayEn, clock, reset
+  )
+  PerfCCT.updateInstMeta(uop.debug_seqNum, PerfCCT.InstDetail.LastReplay.id.U, timer, perfCCTReplayEn, clock, reset)
+  PerfCCT.updateInstMeta(uop.debug_seqNum, PerfCCT.InstDetail.VAddress.id.U, vaddr, perfCCTRecordAddrEn, clock, reset)
+  PerfCCT.updateInstMeta(uop.debug_seqNum, PerfCCT.InstDetail.PAddress.id.U, paddr, perfCCTRecordAddrEn, clock, reset)
+}
+
+class LoadUnitS4(param: ExeUnitParams)(
+  implicit p: Parameters,
+  override implicit val s: LoadStage = LoadS4()
+) extends LoadUnitStage(param) {
+  val io = IO(new Bundle() {
+    val unalignConcat = ValidIO(new LoadStageIO()(p, LoadS3()))
+  })
+
+  io_pipeIn.get.ready := true.B
+  io.unalignConcat.valid := io_pipeIn.get.valid
+  io.unalignConcat.bits := io_pipeIn.get.bits
+}
+
+class LoadUnitDataPathMeta(implicit p: Parameters) extends XSBundle with HasDCacheParameters {
+  val bankOffset = UInt(DCacheVWordOffset.W)
+  val fuOpType = FuOpType()
+  val fpWen = Bool()
+  val isNCReplay = Bool()
+  val isMMIOReplay = Bool()
+  val isUnalignHead = Bool()
+}
+
+class LoadUnitDataPath(val param: ExeUnitParams)(implicit p: Parameters) extends XSModule with HasNewLoadHelper {
+  val io = IO(new Bundle() {
+    val s1Meta = Flipped(ValidIO(new LoadUnitDataPathMeta))
+    val s2SqForwardResp = Flipped(ValidIO(new SQForwardRespS2))
+    val s2SbufferForwardResp = Flipped(ValidIO(new SbufferForwardResp))
+    val s2UncacheForwardResp = Flipped(ValidIO(new UncacheForwardResp))
+    val s2MSHRForwardResp = Flipped(ValidIO(new DCacheForwardResp))
+    val s2TLDForwardResp = Flipped(ValidIO(new DCacheForwardResp))
+    val s2UncacheBypassResp = Flipped(ValidIO(new UncacheBypassRespS2))
+    val s2DCacheResp = Flipped(ValidIO(new DCacheWordResp))
+    val s3ShiftData = Output(UInt(VLEN.W)) // used by vector writeback
+    val s3ShiftAndExtData = Output(UInt(VLEN.W)) // used by scalar writeback
+  })
+
+  // S1
+  val s1Valid = io.s1Meta.valid
+  val s1Meta = io.s1Meta.bits
+
+  // S2
+  val s2Valid = RegNext(s1Valid, false.B)
+  val s2Meta = RegEnable(s1Meta, s1Valid)
+  val bankOffset = s2Meta.bankOffset
+  val fuOpType = s2Meta.fuOpType
+  val fpWen = s2Meta.fpWen
+  val isNCReplay = s2Meta.isNCReplay
+  val isMMIOReplay = s2Meta.isMMIOReplay
+  val isUncacheReplay = isNCReplay || isMMIOReplay
+  val isUnalignHead = s2Meta.isUnalignHead
+  val isUnalignTail = RegNext(isUnalignHead && s2Valid, false.B)
+  val unalignHeadBankOffset = RegEnable(bankOffset, isUnalignHead && s2Valid) // from the perspective of unalign tail
+
+  val uncacheBypassData = io.s2UncacheBypassResp.bits.data
+  val dcacheData = io.s2DCacheResp.bits.data
+  val rawData = Mux(isUncacheReplay, uncacheBypassData, dcacheData)
+
+  val sqForwardMask = io.s2SqForwardResp.bits.forwardMask.asUInt
+  val sqForwardData = io.s2SqForwardResp.bits.forwardData.asUInt
+  val ncForwardMask = io.s2UncacheForwardResp.bits.forwardMask.asUInt & Fill(VLEN / 8, isNCReplay)
+  val ncForwardData = io.s2UncacheForwardResp.bits.forwardData.asUInt
+  val sbufferForwardMask = io.s2SbufferForwardResp.bits.forwardMask.asUInt
+  val sbufferForwardData = io.s2SbufferForwardResp.bits.forwardData.asUInt
+  val tldMask = Fill(VLEN / 8, io.s2TLDForwardResp.valid)
+  val tldData = io.s2TLDForwardResp.bits.forwardData.asUInt
+  val mshrMask = Fill(VLEN / 8, io.s2MSHRForwardResp.valid)
+  val mshrData = io.s2MSHRForwardResp.bits.forwardData.asUInt
+  val (masks, datas) = Seq(
+    // DO NOT change the priority here
+    (sqForwardMask, sqForwardData),
+    (ncForwardMask, ncForwardData),
+    (sbufferForwardMask, sbufferForwardData),
+    (tldMask, tldData),
+    (mshrMask, mshrData)
+  ).unzip
+
+  val s2Data = mergeData(rawData, datas, masks)
+  val s2RdataTypeOH = genRdataOH(fuOpType, fpWen)
+  val s2RdataSelByOffset = VecInit((0 until VLEN / 8).map(i => bankOffset === i.U))
+  // If the load is unaligned, its bank offset must reside in (8, 15]
+  val unalignHeadBankOffsetUpperBound = VLEN / 8 - 1 // 15
+  val unalignHeadBankOffsetLowerBound = XLEN / 8 // 8
+  val s2TailRdataSelByHeadOffset = VecInit(
+    (unalignHeadBankOffsetUpperBound until unalignHeadBankOffsetLowerBound by -1).map(i =>
+      unalignHeadBankOffset === i.U
+    )
+  )
+
+  // S3
+  val s3Valid = RegNext(s2Valid, false.B)
+  val s3Data = RegEnable(s2Data, s2Valid)
+  val s3IsUnalignTail = RegEnable(isUnalignTail, s2Valid)
+  val s3RdataTypeOH = RegEnable(s2RdataTypeOH, s2Valid)
+  val s3RdataSelByOffset = RegEnable(s2RdataSelByOffset, s2Valid)
+  val s3TailRdataSelByHeadOffset = RegEnable(s2TailRdataSelByHeadOffset, s2Valid)
+  // Data shifting
+  val s3ShiftHeadList = (0 until VLEN by 8).map(i => (s3Data >> i))
+  val s3ShiftTailList = (1*8 until XLEN by 8).map(i => (s3Data << i).take(VLEN))
+  val s3ShiftHead = Mux1H(s3RdataSelByOffset, s3ShiftHeadList)
+  val s3ShiftTail = Mux1H(s3TailRdataSelByHeadOffset, s3ShiftTailList)
+  val s4ShiftHead = RegEnable(s3ShiftHead, s3Valid)
+  val s3ShiftData = Mux(
+    s3IsUnalignTail,
+    s4ShiftHead | s3ShiftTail,
+    s3ShiftHead
+  )
+  // Sign / Zero extension
+  val s3ShiftAndExtData = genRdata(s3RdataTypeOH, s3ShiftData.take(XLEN))
+
+  // IO assignment
+  io.s3ShiftData := s3ShiftData
+  io.s3ShiftAndExtData := s3ShiftAndExtData
+
+  def mergeData(oldData: UInt, newData: Seq[UInt], mask: Seq[UInt]): UInt = {
+    val bytesNum = mask.head.getWidth
+    require(oldData.getWidth == newData.head.getWidth)
+    require(oldData.getWidth == (bytesNum * 8))
+    VecInit((0 until bytesNum).map { case i =>
+      val sels = mask.map(_(i).asBool) :+ true.B
+      val bytes = newData.map(getByte(_, i)) :+ getByte(oldData, i)
+      ParallelPriorityMux(sels, bytes)
+    }).asUInt
+  }
+
+  def getByte(data: UInt, i: Int): UInt = data((i + 1) * 8 - 1, i * 8)
+}
+
+class LoadUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
+  val redirect = Flipped(ValidIO(new Redirect))
+  // Request sources
+  val ldin = Flipped(DecoupledIO(new ExuInput(param, hasCopySrc = true)))
+  val vecldin = Flipped(DecoupledIO(new VectorLoadIn))
+  val replay = Flipped(DecoupledIO(new LoadReplayIO))
+  val prefetchReq = Flipped(DecoupledIO(new L1PrefetchReq))
+  // Writeback to Backend / LQ / VLMergeBuffer
+  val ldout = new NewExuOutput(param)
+  val lqWrite = DecoupledIO(new LqWriteBundle)
+  val vecldout = Decoupled(new VecPipelineFeedbackIO(isVStore = false))
+  // TLB / PMA / PMP
+  val tlb = new TlbRequestIO(2)
+  val tlbHint = Flipped(new TlbHintReq)
+  val pmp = Flipped(new PMPRespBundle)
+  // DCache
+  val dcache = new DCacheLoadIO
+  // IQ wakeup and load cancel
+  val wakeup = ValidIO(new MemWakeUpBundle)
+  val cancel = Output(Bool())
+  // Exception info
+  val exceptionInfo = ValidIO(new MemExceptionInfo)
+  // Data forwarding and bypass
+  val sqForward = new SQForward
+  val sbufferForward = new SbufferForward
+  val uncacheForward = new UncacheForward
+  val mshrForward = new DCacheForward
+  val tldForward = new DCacheForward
+  val uncacheBypass = new UncacheBypass
+  // Nuke check with StoreUnit
+  val staNukeQueryReq = Flipped(Vec(StorePipelineWidth, ValidIO(new StoreNukeQueryReq)))
+  // Nuke check with RAR / RAW
+  val rarNukeQuery = new LoadRARNukeQuery
+  val rawNukeQuery = new LoadRAWNukeQuery
+  val rollback = ValidIO(new Redirect)
+  // Prefetch Train
+  val prefetchTrainHintS1 = Output(Bool())
+  val prefetchTrainHintS2 = Output(Bool())
+  val prefetchTrain = ValidIO(new LsPrefetchTrainBundle)
+  // Software instruction prefetch
+  val swInstrPrefetch = ValidIO(new SoftIfetchPrefetchBundle)
+  // CSR control signals and load trigger
+  val csrCtrl = Flipped(new CustomCSRCtrlIO)
+  val csrTrigger = Input(new CsrTriggerBundle)
+  // Debug info and top-down info
+  val debugInfo = Output(new DebugLsInfoBundle)
+  val topDownInfo = Output(new LsTopdownInfo)
+}
+
+class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModule with HasPerfEvents {
+  val io = IO(new LoadUnitIO(param))
+
+  val s0 = Module(new LoadUnitS0(param))
+  val s1 = Module(new LoadUnitS1(param))
+  val s2 = Module(new LoadUnitS2(param))
+  val s3 = Module(new LoadUnitS3(param))
+  val s4 = Module(new LoadUnitS4(param))
+  val dataPath = Module(new LoadUnitDataPath(param))
+  val stages = Seq(s0, s1, s2, s3, s4)
+
+  // Internal wiring
+  s1 <> s0
+  s2 <> s1
+  s3 <> s2
+  s4 <> s3
+  s0.io.unalignTail <> s1.io.unalignTail
+  s0.io.fastReplay <> s3.io.fastReplay
+  s3.io.unalignTailValid := s2.io.unalignTailValid
+  s3.io.unalignConcat <> s4.io.unalignConcat
+  s1.io.kill := false.B
+  s2.io.kill := false.B
+  s3.io.kill := false.B
+  dataPath.io.s1Meta := s1.io.dataPathMeta
+
+  // IO wiring
+  // S0
+  s0.io.replay <> io.replay
+  s0.io.prefetchReq <> io.prefetchReq
+  s0.io.vecldin <> io.vecldin
+  s0.io.ldin <> io.ldin
+  io.tlb.req <> s0.io.tlbReq
+  io.dcache.req <> s0.io.dcacheReq
+  io.dcache.is128Req := s0.io.is128Req
+  io.dcache.replacementUpdated := s0.io.replacementUpdated
+  io.dcache.pf_source := s0.io.pfSource
+  io.sqForward.s0Req := s0.io.sqSbForwardReq
+  io.sbufferForward.s0Req := s0.io.sqSbForwardReq
+  io.uncacheForward.s0Req := s0.io.uncacheForwardReq
+  io.mshrForward.s0Req := s0.io.mshrForwardReq
+  io.tldForward.s0Req := s0.io.tldForwardReq
+  io.uncacheBypass.s0Req := s0.io.uncacheBypassReq
+  io.wakeup := s0.io.wakeup
+
+  // S1
+  s1.io.redirect := io.redirect
+  s1.io.tlbResp <> io.tlb.resp
+  io.tlb.req_kill := s1.io.tlbReqKill
+  io.tlb.req.bits.pmp_addr := s1.io.tlbPAddr // TODO
+  io.dcache.s1_paddr_dup_lsu := s1.io.dcachePAddr
+  io.dcache.s1_paddr_dup_dcache := s1.io.dcachePAddr
+  io.dcache.s1_kill := s1.io.dcacheKill
+  io.sqForward.s1Req := s1.io.storeForwardReq
+  io.sqForward.s1Kill := s1.io.sqForwardKill
+  s1.io.sqForwardResp := io.sqForward.s1Resp
+  io.sbufferForward.s1Req := s1.io.storeForwardReq
+  io.sbufferForward.s1Kill := s1.io.sbufferForwardKill
+  io.uncacheForward.s1Req := s1.io.storeForwardReq
+  io.uncacheForward.s1Kill := s1.io.uncacheForwardKill
+  io.mshrForward.s1Req := s1.io.mshrForwardReq
+  io.mshrForward.s1Kill := s1.io.mshrForwardKill
+  io.tldForward.s1Req := s1.io.tldForwardReq
+  io.tldForward.s1Kill := s1.io.tldForwardKill
+  s1.io.uncacheBypassResp := io.uncacheBypass.s1Resp
+  s1.io.staNukeQueryReq := io.staNukeQueryReq
+  io.prefetchTrainHintS1 := s1.io.prefetchTrainHint
+  io.swInstrPrefetch := s1.io.swInstrPrefetch
+  s1.io.csrTrigger := io.csrTrigger
+
+  // S2
+  s2.io.redirect := io.redirect
+  s2.io.pmp := io.pmp
+  s2.io.tlbHint := io.tlbHint
+  io.dcache.s2_kill := s2.io.dcacheKill
+  s2.io.dcacheResp <> io.dcache.resp
+  s2.io.dcacheBankConflict := io.dcache.s2_bank_conflict
+  s2.io.dcacheMSHRNack := io.dcache.s2_mq_nack
+  s2.io.sqForwardResp := io.sqForward.s2Resp
+  s2.io.sbufferForwardResp := io.sbufferForward.s2Resp
+  s2.io.uncacheForwardResp := io.uncacheForward.s2Resp
+  s2.io.mshrForwardResp := io.mshrForward.s2Resp
+  s2.io.tldForwardResp := io.tldForward.s2Resp
+  s2.io.uncacheBypassResp := io.uncacheBypass.s2Resp
+  s2.io.staNukeQueryReq := io.staNukeQueryReq
+  io.rarNukeQuery.req <> s2.io.rarNukeQueryReq
+  io.rawNukeQuery.req <> s2.io.rawNukeQueryReq
+  io.prefetchTrainHintS2 := s2.io.prefetchTrain.valid
+  io.prefetchTrain.valid := GatedValidRegNext(s2.io.prefetchTrain.valid)
+  io.prefetchTrain.bits := RegEnable(s2.io.prefetchTrain.bits, s2.io.prefetchTrain.valid)
+  s2.io.csrCtrl := io.csrCtrl
+
+  // S3
+  s3.io.redirect := io.redirect
+  s3.io.dcacheError := io.dcache.resp.bits.error_delayed
+  io.ldout <> s3.io.ldout
+  io.lqWrite <> s3.io.lqWrite
+  io.vecldout <> s3.io.vecldout
+  s3.io.rarNukeQueryResp := io.rarNukeQuery.resp
+  io.rarNukeQuery.revokeLastCycle := s3.io.revokeLastCycle
+  io.rarNukeQuery.revokeLastLastCycle := s3.io.revokeLastLastCycle
+  io.rawNukeQuery.revokeLastCycle := s3.io.revokeLastCycle
+  io.rawNukeQuery.revokeLastLastCycle := s3.io.revokeLastLastCycle
+  io.rollback := s3.io.rollback
+  io.cancel := s3.io.cancel
+  io.exceptionInfo := s3.io.exceptionInfo
+  s3.io.csrCtrl := io.csrCtrl
+
+  // Data path
+  dataPath.io.s2SqForwardResp := io.sqForward.s2Resp
+  dataPath.io.s2SbufferForwardResp := io.sbufferForward.s2Resp
+  dataPath.io.s2UncacheForwardResp := io.uncacheForward.s2Resp
+  dataPath.io.s2MSHRForwardResp := io.mshrForward.s2Resp
+  dataPath.io.s2TLDForwardResp := io.tldForward.s2Resp
+  dataPath.io.s2UncacheBypassResp := io.uncacheBypass.s2Resp
+  dataPath.io.s2DCacheResp.valid := io.dcache.resp.valid
+  dataPath.io.s2DCacheResp.bits := io.dcache.resp.bits
+  io.ldout.toFpRf.foreach(_.bits := dataPath.io.s3ShiftAndExtData(io.ldout.toFpRf.get.bits.getWidth - 1, 0))
+  io.ldout.toIntRf.foreach(_.bits := dataPath.io.s3ShiftAndExtData(io.ldout.toIntRf.get.bits.getWidth - 1, 0))
+  io.vecldout.bits.vecdata.get := dataPath.io.s3ShiftData
+
+  // Debug info
+  io.debugInfo.s1_isTlbFirstMiss := s1.io.debugInfo.isTlbFirstMiss
+  io.debugInfo.s1_isLoadToLoadForward := s1.io.debugInfo.isLoadToLoadForward
+  io.debugInfo.s2_isBankConflict := s2.io.debugInfo.isBankConflict
+  io.debugInfo.s2_isDcacheFirstMiss := s2.io.debugInfo.isDCacheMiss
+  io.debugInfo.s2_isForwardFail := s2.io.debugInfo.isForwardFail
+  io.debugInfo.s3_isReplayFast := s3.io.debugInfo.isReplayFast
+  io.debugInfo.s3_isReplaySlow := s3.io.debugInfo.isReplaySlow
+  io.debugInfo.s3_isReplayRS := s3.io.debugInfo.isReplayRS
+  io.debugInfo.s3_isReplay := s3.io.debugInfo.isReplay
+  io.debugInfo.replayCause := s3.io.debugInfo.replayCause
+  io.debugInfo.replayCnt := s3.io.debugInfo.replayCnt
+  io.debugInfo.s1_robIdx := s1.io.debugInfo.robIdx
+  io.debugInfo.s2_robIdx := s2.io.debugInfo.robIdx
+  io.debugInfo.s3_robIdx := s3.io.debugInfo.robIdx
+
+  io.topDownInfo.s1.robIdx := s1.io.debugInfo.robIdx
+  io.topDownInfo.s1.vaddr_valid := s1.io.debugInfo.hasROBEntry && s1.io.debugInfo.vaddr.valid
+  io.topDownInfo.s1.vaddr_bits := s1.io.debugInfo.vaddr.bits
+  io.topDownInfo.s2.robIdx := s2.io.debugInfo.robIdx
+  io.topDownInfo.s2.paddr_valid := s2.io.debugInfo.hasROBEntry && s2.io.debugInfo.paddr.valid &&
+    s2.io.debugInfo.isTlbNotMiss
+  io.topDownInfo.s2.paddr_bits := s2.io.debugInfo.paddr.bits
+  io.topDownInfo.s2.first_real_miss := s2.io.debugInfo.isDCacheRealMiss
+  io.topDownInfo.s2.cache_miss_en := s2.io.debugInfo.hasROBEntry && s2.io.debugInfo.paddr.valid &&
+    s2.io.debugInfo.isTlbNotMiss // missDbUpdated is always DontCare
+
+  io.dcache.s0_pc := s0.io.debugInfo.pc
+  io.dcache.s1_pc := s1.io.debugInfo.pc
+  io.dcache.s2_pc := s2.io.debugInfo.pc
+
+  val perfEvents = stages.collect { case stage if stage.isInstanceOf[HasPerfEvents] =>
+    stage.asInstanceOf[HasPerfEvents]
+  }.map(_.getPerfEvents).flatten
+  generatePerfEvent()
+}
+
+abstract class LoadUnitStage(val param: ExeUnitParams)(
+  implicit p: Parameters,
+  implicit val s: LoadStage
+) extends XSModule with OnLoadStage
+  with HasDCacheParameters
+  with HasCircularQueuePtrHelper {
+  val io_pipeIn = if (afterS1) {
+    Some(IO(Flipped(DecoupledIO(new LoadStageIO()(p, prevStage(s))))))
+  } else None
+  val io_pipeOut = if (!lastStage) {
+    Some(IO(DecoupledIO(new LoadStageIO)))
+  } else None
+
+  def <>(that: LoadUnitStage): Unit = {
+    this.io_pipeIn.foreach(_ <> that.io_pipeOut.get)
+  }
+}
+
+trait HasNukePAddrMatch { this: LoadUnitStage =>
+  def nukePAddrMatch(storePAddr: UInt, storeMatchType: UInt, loadPAddr: UInt): Bool = {
+    Mux(
+      StLdNukeMatchType.isCacheLine(storeMatchType),
+      (storePAddr >> blockOffBits) === (loadPAddr >> blockOffBits),
+      (storePAddr >> DCacheVWordOffset) === (loadPAddr >> DCacheVWordOffset)
+    )
+  }
+}
+
+case class RdataType(
+  selFu: (UInt, Bool) => Bool, // (fuOpType, fpWen) => sel
+  dataFu: UInt => UInt
+)
+
+trait HasNewLoadHelper { this: XSModule =>
+  val LBU = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lbu || fuOpType === LSUOpType.hlvbu,
+    dataFu = data => ZeroExt(data(7, 0), XLEN)
+  )
+  val LHU = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lhu || fuOpType === LSUOpType.hlvhu || fuOpType === LSUOpType.hlvxhu,
+    dataFu = data => ZeroExt(data(15, 0), XLEN)
+  )
+  val LWU = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lwu || fuOpType === LSUOpType.hlvwu || fuOpType === LSUOpType.hlvxwu,
+    dataFu = data => ZeroExt(data(31, 0), XLEN)
+  )
+  val LD = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.ld || fuOpType === LSUOpType.hlvd,
+    dataFu = data => data(63, 0)
+  )
+  val LB = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lb || fuOpType === LSUOpType.hlvb,
+    dataFu = data => SignExt(data(7, 0) , XLEN)
+  )
+  val LH = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lh && !fpWen || fuOpType === LSUOpType.hlvh,
+    dataFu = data => SignExt(data(15, 0) , XLEN)
+  )
+  val LW = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lw && !fpWen || fuOpType === LSUOpType.hlvw,
+    dataFu = data => SignExt(data(31, 0) , XLEN)
+  )
+  val LH_FP = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lh && fpWen,
+    dataFu = data => FPU.box(data, FPU.H)
+  )
+  val LW_FP = RdataType(
+    selFu = (fuOpType, fpWen) => fuOpType === LSUOpType.lw && fpWen,
+    dataFu = data => FPU.box(data, FPU.S)
+  )
+
+  val types: Seq[RdataType] = Seq(LBU, LHU, LWU, LD, LB, LH, LW, LH_FP, LW_FP)
+  val num = types.length
+  def genRdataOH(fuOpType: UInt, fpWen: Bool): Vec[Bool] = VecInit(types.map(_.selFu(fuOpType, fpWen)))
+  def genRdata(sel: Vec[Bool], data: UInt): UInt = {
+    Mux1H(sel, types.map(_.dataFu(data)))
+  }
+}
+
+/**
+  * Only for compiling the module independently
+  */
+class NewLoadUnitTop(implicit val p: Parameters) extends Module
+  with HasXSParameter
+  with HasMemBlockParameters {
+  val param = ldaParams.head
+  param.bindBackendParam(backendParams)
+  val io = IO(new LoadUnitIO(param))
+  val ldu = Module(new NewLoadUnit(param))
+  io <> ldu.io
+}
+
+object NewLoadUnitMain extends App {
+  val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(
+    args :+ "--disable-always-basic-diff" :+ "--dump-fir" :+ "--fpga-platform" :+ "--target" :+ "verilog")
+
+  val defaultConfig = config.alterPartial({
+    // Get XSCoreParams and pass it to the "small module"
+    case XSCoreParamsKey => config(XSTileKey).head
+  })
+
+  Generator.execute(
+    firrtlOpts,
+    new NewLoadUnitTop()(defaultConfig),
+    firtoolOpts
+  )
+
+  println("done")
+}
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala
index 8ffc312cb5e..805ea573548 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StdExeUnit.scala
@@ -29,7 +29,7 @@ class StdExeUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends XSB
   val flush = Flipped(ValidIO(new Redirect()))
   val in = Flipped(DecoupledIO(new ExuInput(param, hasCopySrc = true)))
   val vstdIn = Flipped(ValidIO(new StoreQueueDataWrite))
-  val out = DecoupledIO(new ExuOutput(param)) // std -> wb
+  val out = new NewExuOutput(param) // std -> wb
   val atomicData = Valid(new ExuInput(param)) // std -> atomicsUnit
   val sqData = Valid(new StoreQueueDataWrite) // std -> sq
 }
@@ -38,26 +38,26 @@ class StdExeUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMod
   val io = IO(new StdExeUnitIO(param))
 
   // Arbitrate between scalar std and vector std
-  io.in.ready := io.out.ready && !io.vstdIn.valid
+  io.in.ready := !io.vstdIn.valid
 
   // writeback of scalar stds but not vector stds
-  io.out.valid := io.in.valid && !io.vstdIn.valid && !FuType.storeIsAMO(io.in.bits.fuType)
-  io.out.bits := 0.U.asTypeOf(io.out.bits)
-  io.out.bits.data := VecInit(Seq.fill(param.wbPathNum)(io.in.bits.src(0)))
-  io.out.bits.robIdx := io.in.bits.robIdx
-  io.out.bits.pdest := io.in.bits.pdest
-  io.out.bits.sqIdx.foreach(_ := io.in.bits.sqIdx.get)
-  io.out.bits.perfDebugInfo.foreach(_ := io.in.bits.perfDebugInfo.get)
-  io.out.bits.debug_seqNum.foreach(_ := io.in.bits.debug_seqNum.get)
+  io.out.toRob.valid := io.in.valid && !io.vstdIn.valid && !FuType.storeIsAMO(io.in.bits.fuType)
+  io.out.toRob.bits.robIdx := io.in.bits.robIdx
+  io.out.toRob.bits.isRVC.foreach(_ := DontCare)
+  io.out.toRob.bits.sqIdx.foreach(_ := io.in.bits.sqIdx.get)
+  io.out.pdest := io.in.bits.pdest
+  io.out.debug := DontCare
+  io.out.perfDebugInfo.foreach(_ := io.in.bits.perfDebugInfo.get)
+  io.out.debug_seqNum.foreach(_ := io.in.bits.debug_seqNum.get)
 
   io.atomicData.valid := io.in.fire && FuType.storeIsAMO(io.in.bits.fuType)
   io.atomicData.bits := io.in.bits
 
   // sq writeback of both scalar and vector stds
-  io.sqData.valid := io.out.fire || io.vstdIn.valid
+  io.sqData.valid := io.out.toRob.fire || io.vstdIn.valid
   io.sqData.bits.fuType := Mux(io.vstdIn.valid, io.vstdIn.bits.fuType, io.in.bits.fuType)
   io.sqData.bits.fuOpType := Mux(io.vstdIn.valid, io.vstdIn.bits.fuOpType, io.in.bits.fuOpType)
   io.sqData.bits.data := Mux(io.vstdIn.valid, io.vstdIn.bits.data, io.in.bits.src(0))
   io.sqData.bits.sqIdx := Mux(io.vstdIn.valid, io.vstdIn.bits.sqIdx, io.in.bits.sqIdx.get)
-  io.sqData.bits.vecDebug := io.vstdIn.bits.vecDebug // DontCare for scalar stds
+  io.sqData.bits.vecDebug.foreach(_ := io.vstdIn.bits.vecDebug.get) // DontCare for scalar stds
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 97f525b0d46..a0eed729099 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -23,7 +23,7 @@ import utils._
 import utility._
 import xiangshan._
 import xiangshan.ExceptionNO._
-import xiangshan.backend.Bundles.{ExuInput, ExuOutput, connectSamePort, StoreUnitToLFST, UopIdx}
+import xiangshan.backend.Bundles.{ExuInput, ExuOutput, NewExuOutput, StoreUnitToLFST, UopIdx, connectSamePort}
 import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.fu.FuType._
@@ -50,8 +50,10 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
     val tlb             = new TlbRequestIO()
     val dcache          = new DCacheStoreIO
     val pmp             = Flipped(new PMPRespBundle())
-    val lsq             = ValidIO(new LsPipelineBundle)
-    val lsq_replenish   = Output(new LsPipelineBundle())
+    val toLsq           = ValidIO(new StoreAddrIO)
+    val toLsqRe         = Output(new StoreAddrIO) // write some exception info and memory type generate in s2
+    // ready indicate unaligned queue reject this unaligned request;
+    val toStoreUnalignQueue = DecoupledIO(new UnalignQueueIO)
     val feedback_slow   = ValidIO(new RSFeedback)
     val prefetch_req    = Flipped(DecoupledIO(new StorePrefetchReq))
     // provide prefetch info to sms
@@ -59,8 +61,8 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
     // speculative for gated control
     val s1_prefetch_spec = Output(Bool())
     val s2_prefetch_spec = Output(Bool())
-    val stld_nuke_query = Valid(new StoreNukeQueryBundle)
-    val stout           = DecoupledIO(new ExuOutput(param)) // writeback store
+    val stld_nuke_query = Valid(new StoreNukeQueryReq)
+    val stout           = new NewExuOutput(param) // writeback store
     val vecstout        = DecoupledIO(new VecPipelineFeedbackIO(isVStore = true))
     // store mask, send to sq in store_s0
     val st_mask_out     = Valid(new StoreMaskBundle)
@@ -76,6 +78,9 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
     val sqCommitUopIdx = Input(UopIdx())
     val sqCommitRobIdx = Input(new RobPtr)
 
+    // exception, last stage signal
+    val exceptionInfo  = ValidIO(new MemExceptionInfo)
+
     val s0_s1_s2_valid = Output(Bool())
   })
   io.stin.bits.debug_seqNum.foreach(x => PerfCCT.updateInstPos(x, PerfCCT.InstPos.AtFU.id.U, io.stin.valid, clock, reset))
@@ -130,7 +135,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   // val s0_isLastElem   = s0_vecstin.isLastElem
   val s0_secondInv    = s0_vecstin.usSecondInv
   val s0_elemIdx      = s0_vecstin.elemIdx
-  val s0_alignedType  = s0_vecstin.alignedType
+  val s0_alignedType  = Mux(s0_use_flow_vec, s0_vecstin.alignedType, Cat(0.U, s0_uop.fuOpType(1, 0)))
   val s0_mBIndex      = s0_vecstin.mBIndex
   val s0_vecBaseVaddr = s0_vecstin.basevaddr
   val s0_isFinalSplit = io.misalign_stin.valid && io.misalign_stin.bits.isFinalSplit
@@ -171,16 +176,17 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   XSError(s0_use_flow_vec && s0_vaddr(3, 0) =/= 0.U && s0_vecstin.alignedType(2), "unit stride 128 bit element is not aligned!")
 
   val s0_isMisalign = Mux(s0_use_non_prf_flow, (!s0_addr_aligned || s0_vecstin.uop.exceptionVec(storeAddrMisaligned) && s0_vecActive), false.B)
-  val s0_addr_low = s0_vaddr(4, 0)
+  val s0_addr_low = s0_vaddr(12, 0)
   val s0_addr_Up_low = LookupTree(s0_alignType, List(
     "b00".U -> 0.U,
     "b01".U -> 1.U,
     "b10".U -> 3.U,
     "b11".U -> 7.U
   )) + s0_addr_low
+  val s0_rs_corss4KPage = s0_addr_Up_low(12) =/= s0_addr_low(12)
   val s0_rs_corss16Bytes = s0_addr_Up_low(4) =/= s0_addr_low(4)
   val s0_misalignWith16Byte = !s0_rs_corss16Bytes && !s0_addr_aligned && !s0_use_flow_prf
-  val s0_misalignNeedReplay = (s0_use_flow_vec || s0_rs_corss16Bytes) && !(s0_uop.sqIdx === io.sqCommitPtr || s0_uop.robIdx === io.sqCommitRobIdx && s0_uop.uopIdx === io.sqCommitUopIdx)
+  val s0_misalignNeedReplay = (s0_use_flow_vec || s0_rs_corss4KPage && !s0_use_flow_ma) && !(s0_uop.sqIdx === io.sqCommitPtr || s0_uop.robIdx === io.sqCommitRobIdx && s0_uop.uopIdx === io.sqCommitUopIdx)
   s0_is128bit := Mux(s0_use_flow_ma, io.misalign_stin.bits.is128bit, is128Bit(s0_vecstin.alignedType) || s0_misalignWith16Byte)
 
   s0_fullva := Mux(
@@ -247,7 +253,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   s0_out.uop          := s0_uop
   s0_out.miss         := false.B
   // For unaligned, we need to generate a base-aligned mask in storeunit and then do a shift split in StoreQueue.
-  s0_out.mask         := Mux(s0_rs_corss16Bytes && !s0_addr_aligned, genBasemask(s0_saddr,s0_alignType(1,0)), s0_mask)
+  s0_out.mask         := s0_mask
   s0_out.isFirstIssue := s0_isFirstIssue
   s0_out.isHWPrefetch := s0_use_flow_prf
   s0_out.wlineflag    := s0_wlineflag
@@ -311,6 +317,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   //For example: `StoreQueue` is exceptionBuffer
   val s1_frm_mab_vec = RegEnable(s0_use_flow_ma && io.misalign_stin.bits.isvec, false.B, s0_fire)
   // val s1_isLastElem = RegEnable(s0_isLastElem, false.B, s0_fire)
+  val s1_cross4KPage = RegEnable(s0_rs_corss4KPage, s0_fire)
   s1_kill := s1_in.uop.robIdx.needFlush(io.redirect) || (s1_tlb_miss && !s1_isvec && !s1_frm_mabuf)
 
   s1_ready := !s1_valid || s1_kill || s2_ready
@@ -406,11 +413,39 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
 
   // scalar store and scalar load nuke check, and also other purposes
   //A 128-bit aligned unaligned memory access requires changing the unaligned flag bit in sq
-  io.lsq.valid     := s1_valid && !s1_in.isHWPrefetch
-  io.lsq.bits      := s1_out
-  io.lsq.bits.miss := s1_tlb_miss
-  io.lsq.bits.isvec := s1_out.isvec || s1_frm_mab_vec
-  io.lsq.bits.updateAddrValid := (!s1_in.isMisalign || s1_in.misalignWith16Byte) && (!s1_frm_mabuf || s1_in.isFinalSplit) || s1_exception
+  //TODO: FIX this connect!!
+  io.toLsq.valid          := s1_valid && !s1_in.isHWPrefetch
+  io.toLsq.bits.paddr     := s1_out.paddr
+  io.toLsq.bits.vaddr     := s1_out.vaddr
+  io.toLsq.bits.cacheMiss := false.B // will be set in stage 2
+  io.toLsq.bits.tlbMiss   := s1_out.tlbMiss
+  io.toLsq.bits.wlineflag := s1_out.wlineflag
+  io.toLsq.bits.mask      := s1_out.mask
+  io.toLsq.bits.size      := s1_out.alignedType
+  io.toLsq.bits.uop.sqIdx     := s1_out.uop.sqIdx
+  io.toLsq.bits.uop.robIdx    := s1_out.uop.robIdx
+  io.toLsq.bits.uop.fuOpType  := s1_out.uop.fuOpType
+  io.toLsq.bits.uop.ftqPtr    := s1_out.uop.ftqPtr
+  io.toLsq.bits.uop.ftqOffset := s1_out.uop.ftqOffset
+  io.toLsq.bits.uop.isRVC     := s1_out.uop.isRVC
+  io.toLsq.bits.uop.isFirstIssue := s1_out.isFirstIssue
+  io.toLsq.bits.nc            := DontCare // will be set in stage 2
+  io.toLsq.bits.mmio          := DontCare // will be set in stage 2
+  io.toLsq.bits.memBackTypeMM := DontCare // will be set in stage 2
+  io.toLsq.bits.hasException  := DontCare // will be set in stage 2
+  io.toLsq.bits.af            := DontCare // will be set in stage 2
+  io.toLsq.bits.uop.pc.foreach(_ := s1_out.uop.pc)
+  io.toLsq.bits.uop.debugInfo.foreach(_  := s1_out.uop.perfDebugInfo)
+  io.toLsq.bits.uop.debug_seqNum.foreach(_ := s1_out.uop.debug_seqNum)
+
+
+  //TODO: `isLastRequest` means it's last request to write to storeQueue. if is normal request, it will be true,
+  // if it was unalign splited, first request will be false, second will be true.
+  io.toLsq.bits.isLastRequest       := s1_frm_mabuf && s1_out.isFinalSplit || !s1_cross4KPage && !s1_frm_mabuf //TODO: support cross page unalign feature!
+  io.toLsq.bits.cross4KPage         := s1_frm_mabuf //TODO: support cross page unalign feature!
+  io.toLsq.bits.unalignWithin16Byte := s1_out.misalignWith16Byte && !s1_frm_mabuf
+  io.toLsq.bits.isUnalign           := s1_out.isMisalign || s1_frm_mabuf
+
   // kill dcache write intent request when tlb miss or exception
   io.dcache.s1_kill  := (s1_tlb_miss || s1_exception || s1_out.mmio || s1_out.nc || s1_in.uop.robIdx.needFlush(io.redirect))
   io.dcache.s1_paddr := s1_paddr
@@ -422,10 +457,10 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
     s1_out.uop.perfDebugInfo.tlbRespTime := GTimer()
   }
   val s1_mis_align = s1_valid && !s1_tlb_miss && !s1_in.isHWPrefetch && !s1_isCbo && !s1_out.nc && !s1_out.mmio &&
-                      GatedValidRegNext(io.csrCtrl.hd_misalign_st_enable) && s1_in.isMisalign && !s1_in.misalignWith16Byte &&
+                      GatedValidRegNext(io.csrCtrl.hd_misalign_st_enable) && s1_in.isMisalign && s1_cross4KPage &&
                       !s1_trigger_breakpoint && !s1_trigger_debug_mode
   val s1_toMisalignBufferValid = s1_valid && !s1_tlb_miss && !s1_in.isHWPrefetch &&
-    !s1_frm_mabuf && !s1_isCbo && s1_in.isMisalign && !s1_in.misalignWith16Byte &&
+    !s1_frm_mabuf && !s1_isCbo && s1_in.isMisalign && s1_cross4KPage && // only cross page unalign need enter misalignBuffer.
     GatedValidRegNext(io.csrCtrl.hd_misalign_st_enable)
   io.misalign_enq.req.valid := s1_toMisalignBufferValid && !s1_misalignNeedReplay
   io.misalign_enq.req.bits.fromLsPipelineBundle(s1_in)
@@ -447,6 +482,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   val s2_pbmt   = RegEnable(s1_pbmt, s1_fire)
   val s2_trigger_debug_mode = RegEnable(s1_trigger_debug_mode, false.B, s1_fire)
   val s2_tlb_hit = RegEnable(s1_tlb_hit, s1_fire)
+  val s2_cross4KPage = RegEnable(s1_cross4KPage, s1_fire)
 
   s2_ready := !s2_valid || s2_kill || s3_ready
   when (s1_fire) { s2_valid := true.B }
@@ -497,8 +533,14 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   val s2_mis_align = s2_valid && RegEnable(s1_mis_align, s1_fire)
   // goto misalignBuffer
   io.misalign_enq.revoke := s2_exception
+
   val s2_misalignNeedReplay = RegEnable(s1_toMisalignBufferValid && (!io.misalign_enq.req.ready || s1_misalignNeedReplay), false.B, s1_fire)
   val s2_misalignBufferNack = !io.misalign_enq.revoke && s2_misalignNeedReplay
+  //TODO: when implement new Unalign, it need to assign, cross page second request paddr.
+  io.toStoreUnalignQueue.valid              := s2_frm_mabuf && s2_out.isFinalSplit && s2_valid//TODO: support cross page unalign feature!
+  io.toStoreUnalignQueue.bits.sqIdx         := s2_out.uop.sqIdx
+  io.toStoreUnalignQueue.bits.paddr         := s2_out.paddr
+  io.toStoreUnalignQueue.bits.robIdx        := s2_out.uop.robIdx
 
   // feedback tlb miss to RS in store_s2
   val feedback_slow_valid = WireInit(false.B)
@@ -519,18 +561,27 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
 
   val s2_misalign_cango = !s2_mis_align || s2_in.isvec && (s2_misalignNeedReplay || s2_exception) || !s2_in.isvec && !s2_misalignNeedReplay && s2_exception
 
-  // mmio and exception
-  io.lsq_replenish := s2_out
-  io.lsq_replenish.af := s2_out.af && s2_valid && !s2_kill
-  io.lsq_replenish.mmio := (s2_mmio || s2_isCbo_noZero) && !s2_exception // reuse `mmiostall` logic in sq
-
+  // mmio and exception TODO:
+  io.toLsqRe.memBackTypeMM   := s2_out.memBackTypeMM
+  io.toLsqRe.isLastRequest   := s2_frm_mabuf && s2_out.isFinalSplit || !s2_cross4KPage && !s2_frm_mabuf //TODO: support cross page unalign feature!
+  io.toLsqRe.af              := s2_out.af && s2_valid && !s2_kill
+  io.toLsqRe.mmio            := (s2_mmio || s2_isCbo_noZero) && !s2_exception // reuse `mmiostall` logic in sq
+  io.toLsqRe.nc              := s2_out.nc
   // prefetch related
-  io.lsq_replenish.miss := io.dcache.resp.fire && io.dcache.resp.bits.miss // miss info
-  io.lsq_replenish.updateAddrValid := !s2_mis_align && (!s2_frm_mabuf || s2_out.isFinalSplit) || s2_exception
-  io.lsq_replenish.isvec := s2_out.isvec || s2_frm_mab_vec
-
-  io.lsq_replenish.hasException := (ExceptionNO.selectByFu(s2_out.uop.exceptionVec, StaCfg).asUInt.orR ||
+  io.toLsqRe.cacheMiss       := io.dcache.resp.fire && io.dcache.resp.bits.miss // miss info
+  // when support new unalign, hasException need to consider second request.
+  io.toLsqRe.hasException    := (ExceptionNO.selectByFu(s2_out.uop.exceptionVec, StaCfg).asUInt.orR ||
     TriggerAction.isDmode(s2_out.uop.trigger) || s2_out.af) && s2_valid && !s2_kill
+  io.toLsqRe.paddr               := DontCare
+  io.toLsqRe.vaddr               := DontCare
+  io.toLsqRe.tlbMiss             := DontCare
+  io.toLsqRe.wlineflag           := DontCare
+  io.toLsqRe.mask                := DontCare
+  io.toLsqRe.size                := DontCare
+  io.toLsqRe.uop                 := DontCare
+  io.toLsqRe.cross4KPage         := false.B
+  io.toLsqRe.unalignWithin16Byte := s2_out.misalignWith16Byte && !s2_frm_mabuf
+  io.toLsqRe.isUnalign          := s2_out.isMisalign || s2_frm_mabuf
 
 
   // RegNext prefetch train for better timing
@@ -631,6 +682,7 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
       sx_in(i).mask        := s3_in.mask
       sx_in(i).vaddr       := s3_in.fullva
       sx_in(i).vaNeedExt   := s3_in.vaNeedExt
+      sx_in(i).isHyper     := s3_in.isHyper
       sx_in(i).gpaddr      := s3_in.gpaddr
       sx_in(i).isForVSnonLeafPTE     := s3_in.isForVSnonLeafPTE
       sx_in(i).vecTriggerMask := s3_in.vecTriggerMask
@@ -646,14 +698,14 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
       sx_in_vls(i).isWhole := VlduType.isWhole(s3_in.uop.fuOpType)
       sx_in_vls(i).isVecLoad := VlduType.isVecLd(s3_in.uop.fuOpType)
       sx_in_vls(i).isVlm := VlduType.isMasked(s3_in.uop.fuOpType) && VlduType.isVecLd(s3_in.uop.fuOpType)
-      sx_ready(i) := !s3_valid(i) || sx_in(i).output.robIdx.needFlush(io.redirect) || (if (RAWTotalDelayCycles == 0) io.stout.ready else sx_ready(i+1))
+      sx_ready(i) := !s3_valid(i) || sx_in(i).output.robIdx.needFlush(io.redirect) || (if (RAWTotalDelayCycles == 0) true.B else sx_ready(i+1))
     } else {
       val cur_kill   = sx_in(i).output.robIdx.needFlush(io.redirect)
-      val cur_can_go = (if (i == RAWTotalDelayCycles) io.stout.ready else sx_ready(i+1))
+      val cur_can_go = (if (i == RAWTotalDelayCycles) true.B else sx_ready(i+1))
       val cur_fire   = sx_valid(i) && !cur_kill && cur_can_go
       val prev_fire  = sx_valid(i-1) && !sx_in(i-1).output.robIdx.needFlush(io.redirect) && sx_ready(i)
 
-      sx_ready(i) := !sx_valid(i) || cur_kill || (if (i == RAWTotalDelayCycles) io.stout.ready else sx_ready(i+1))
+      sx_ready(i) := !sx_valid(i) || cur_kill || (if (i == RAWTotalDelayCycles) true.B else sx_ready(i+1))
       val sx_valid_can_go = prev_fire || cur_fire || cur_kill
       sx_valid(i) := RegEnable(Mux(prev_fire, true.B, false.B), false.B, sx_valid_can_go)
       sx_in(i) := RegEnable(sx_in(i-1), prev_fire)
@@ -666,12 +718,33 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   val sx_last_in    = sx_in.takeRight(1).head
   val sx_last_in_vec = sx_in_vec.takeRight(1).head
   val sx_last_in_vls = sx_in_vls.takeRight(1).head
-  sx_last_ready := !sx_last_valid || sx_last_in.output.robIdx.needFlush(io.redirect) || io.stout.ready
+  sx_last_ready := true.B
 
   // write back: normal store, nc store
-  io.stout.valid := sx_last_valid && !sx_last_in_vec
-  io.stout.bits := sx_last_in.output
-  io.stout.bits.exceptionVec.foreach(_ := ExceptionNO.selectByFu(sx_last_in.output.exceptionVec.get, StaCfg))
+  io.stout.toRob.valid := sx_last_valid && !sx_last_in_vec
+  io.stout.toRob.bits.robIdx := sx_last_in.output.robIdx
+  io.stout.toRob.bits.isRVC.foreach(_ := sx_last_in.output.isRVC.get)
+  io.stout.toRob.bits.trigger.foreach(_ := sx_last_in.output.trigger.get)
+  io.stout.toRob.bits.sqIdx.foreach(_ := sx_last_in.output.sqIdx.get)
+  io.stout.toRob.bits.lqIdx.foreach(_  := sx_last_in.output.lqIdx.get)
+  io.stout.pdest := DontCare
+  io.stout.debug := sx_last_in.output.debug
+  io.stout.debug_seqNum.foreach(_  := sx_last_in.output.debug_seqNum.get)
+  io.stout.perfDebugInfo.foreach(_  := sx_last_in.output.perfDebugInfo.get)
+  io.stout.toRob.bits.exceptionVec.foreach(_ := ExceptionNO.selectByFu(sx_last_in.output.exceptionVec.get, StaCfg))
+
+  // exceptionInfo Gen
+  io.exceptionInfo.valid             := sx_last_valid && !sx_last_in_vec // normal writeback, not vector writeback
+  io.exceptionInfo.bits.robIdx       := sx_last_in.output.robIdx
+  io.exceptionInfo.bits.exceptionVec := ExceptionNO.selectByFu(sx_last_in.output.exceptionVec.get, StaCfg)
+  io.exceptionInfo.bits.vaddr        := sx_last_in.vaddr
+  io.exceptionInfo.bits.gpaddr       := sx_last_in.gpaddr
+  io.exceptionInfo.bits.isForVSnonLeafPTE := sx_last_in.isForVSnonLeafPTE
+  io.exceptionInfo.bits.vaNeedExt    := sx_last_in.vaNeedExt
+  io.exceptionInfo.bits.isHyper      := sx_last_in.isHyper
+  io.exceptionInfo.bits.uopIdx       := 0.U.asTypeOf(io.exceptionInfo.bits.uopIdx)
+  io.exceptionInfo.bits.vl           := 0.U.asTypeOf(io.exceptionInfo.bits.vl)
+  io.exceptionInfo.bits.vstart       := 0.U.asTypeOf(io.exceptionInfo.bits.vstart)
 
   io.vecstout.valid := sx_last_valid && sx_last_in_vec
   // TODO: implement it!
@@ -685,8 +758,6 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   io.vecstout.bits.mmio := sx_last_in.mmio
   io.vecstout.bits.exceptionVec := ExceptionNO.selectByFu(sx_last_in.output.exceptionVec.get, VstuCfg)
   io.vecstout.bits.hasException := sx_last_in.hasException
-  io.vecstout.bits.usSecondInv := sx_last_in.usSecondInv
-  io.vecstout.bits.vecFeedback := sx_last_in.vecFeedback
   io.vecstout.bits.elemIdx     := sx_last_in.elemIdx
   io.vecstout.bits.alignedType := sx_last_in.alignedType
   io.vecstout.bits.mask        := sx_last_in.mask
diff --git a/src/main/scala/xiangshan/mem/pipeline/package.scala b/src/main/scala/xiangshan/mem/pipeline/package.scala
new file mode 100644
index 00000000000..3bbeba2cefb
--- /dev/null
+++ b/src/main/scala/xiangshan/mem/pipeline/package.scala
@@ -0,0 +1,150 @@
+/***************************************************************************************
+* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2020-2021 Peng Cheng Laboratory
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http://license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
+
+package xiangshan.mem
+
+import chisel3._
+import chisel3.util._
+import utils._
+import utils.EnumUtils.ChiselOHEnum
+
+object LoadStage extends Enumeration {
+  val s0, s1, s2, s3, s4 = Value
+  sealed abstract class LoadStage(val stage: Value) {
+    def id: Int = stage.id
+  }
+  case class LoadS0() extends LoadStage(s0)
+  case class LoadS1() extends LoadStage(s1)
+  case class LoadS2() extends LoadStage(s2)
+  case class LoadS3() extends LoadStage(s3)
+  case class LoadS4() extends LoadStage(s4)
+}
+
+trait OnLoadStage {
+  import LoadStage._
+  implicit val s: LoadStage
+
+  def is(sn: LoadStage): Boolean = s.id == sn.id
+  def isS0: Boolean = is(LoadS0())
+  def after(s1: LoadStage, s2: LoadStage): Boolean = s1.id >= s2.id
+  def afterS1: Boolean = after(s, LoadS1())
+  def afterS2: Boolean = after(s, LoadS2())
+  def afterS3: Boolean = after(s, LoadS3())
+  def lastStage: Boolean = s match {
+    case _: LoadS4 => true
+    case _ => false
+  }
+  def prevStage(stage: LoadStage): LoadStage = stage match {
+    case LoadS0() =>
+      require(false)
+      LoadS0()
+    case LoadS1() => LoadS0()
+    case LoadS2() => LoadS1()
+    case LoadS3() => LoadS2()
+    case LoadS4() => LoadS3()
+  }
+}
+
+object LoadEntrance extends ChiselOHEnum {
+  type OHType = super.OHType
+
+  val unalignTail = addType(name = "unalignTail")
+  val replayHiPrio = addType(name = "replayHiPrio")
+  val fastReplay = addType(name = "fastReplay")
+  val replayLoPrio = addType(name = "replayLoPrio")
+  val prefetchHiConf = addType(name = "prefetchHiConf")
+  val vectorIssue = addType(name = "vectorIssue")
+  val scalarIssue = addType(name = "scalarIssue")
+  val prefetchLoConf = addType(name = "prefetchLoConf")
+
+  def num = this.values.size
+
+  def apply() = UInt(num.W)
+
+  def isUnalignTail(source: UInt): Bool = IsOneOf(source, unalignTail)
+  def isReplay(source: UInt): Bool = IsOneOf(source, replayHiPrio, replayLoPrio)
+  def isFastReplay(source: UInt): Bool = IsOneOf(source, fastReplay)
+  def isHWPrefetch(source: UInt): Bool = IsOneOf(source, prefetchHiConf, prefetchLoConf)
+  def isVectorIssue(source: UInt): Bool = IsOneOf(source, vectorIssue)
+  def isScalarIssue(source: UInt): Bool = IsOneOf(source, scalarIssue)
+
+  def findNameById(id: Int): String = {
+    values.find(_.id == id).map(_.getName).getOrElse("UNKNOWN")
+  }
+}
+
+class LoadAccessType extends Bundle {
+  val instrType = InstrType()
+  val pftType = PrefetchType() // only 
+  val pftCoh = PrefetchCoh()
+
+  import InstrType._
+  def isScalar(): Bool = IsOneOf(instrType, scalar)
+  def isVector(): Bool = IsOneOf(instrType, vector)
+  def isPrefetch(): Bool = IsOneOf(instrType, prefetch)
+  def isHwPrefetch(): Bool = isPrefetch() && PrefetchType.isHwPrefetch(pftType)
+  def isSwPrefetch(): Bool = isPrefetch() && PrefetchType.isSwPrefetch(pftType)
+  def isInstrPrefetch(): Bool = isPrefetch() && PrefetchType.isInstrPrefetch(pftType)
+  def isDataPrefetch(): Bool = isPrefetch() && PrefetchType.isDataPrefetch(pftType)
+}
+
+object LoadAccessType {
+  def apply() = new LoadAccessType
+}
+
+object InstrType extends ChiselOHEnum {
+  type OHType = super.OHType
+
+  val scalar = addType("scalar")
+  val vector = addType("vector")
+  val prefetch = addType("prefetch")
+  def apply() = UInt(this.values.size.W)
+}
+
+object PrefetchType {
+  def hw = "b0".U
+  def sw = "b1".U
+  def data = "b0".U
+  def instr = "b1".U
+
+  def hwData = Cat(hw, data)
+  def swData = Cat(sw, data)
+  def swInstr = Cat(sw, instr)
+
+  def apply() = UInt(2.W)
+  def isDataPrefetch(t: UInt): Bool = t(0) === data
+  def isInstrPrefetch(t: UInt): Bool = t(0) === instr
+  def isSwPrefetch(t: UInt): Bool = t(1) === sw
+  def isHwPrefetch(t: UInt): Bool = t(1) === hw
+}
+
+object PrefetchCoh {
+  def read = "b0".U
+  def write = "b1".U
+  def apply() = UInt(1.W)
+}
+
+object TlbAccessResult extends ChiselOHEnum {
+  val unknown = 0
+  val noQuery = addType("noQuery")
+  val hit = addType("hit")
+  val miss = addType("miss")
+
+  def apply() = UInt(this.values.size.W)
+  def isHit(r: UInt): Bool = IsOneOf(r, hit)
+  def isMiss(r: UInt): Bool = IsOneOf(r, miss)
+  def isNotMiss(r: UInt): Bool = !isMiss(r)
+}
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
index 1c6a593fdf3..e2518be693a 100644
--- a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
+++ b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
@@ -193,9 +193,9 @@ class Sbuffer(implicit p: Parameters)
     with HasPerfEvents {
   val io = IO(new Bundle() {
     val hartId = Input(UInt(hartIdLen.W))
-    val in = Vec(EnsbufferWidth, Flipped(Decoupled(new DCacheWordReqWithVaddrAndPfFlag)))  //Todo: store logic only support Width == 2 now
+    val in = Flipped(new SbufferWriteIO)  //Todo: store logic only support Width == 2 now
     val dcache = Flipped(new DCacheToSbufferIO)
-    val forward = Vec(LoadPipelineWidth, Flipped(new LoadForwardQueryIO))
+    val forward = Vec(LoadPipelineWidth, Flipped(new SbufferForward))
     val sqempty = Input(Bool())
     val sbempty = Output(Bool())
     val flush = Flipped(new SbufferFlushBundle)
@@ -316,11 +316,11 @@ class Sbuffer(implicit p: Parameters)
 
   val inflightMask = VecInit(stateVec.map(s => s.isInflight()))
 
-  val inptags = io.in.map(in => getPTag(in.bits.addr))
-  val invtags = io.in.map(in => getVTag(in.bits.vaddr))
-  val sameTag = inptags(0) === inptags(1) && io.in(0).valid && io.in(1).valid && io.in(0).bits.vecValid && io.in(1).bits.vecValid
-  val firstWord = getVWord(io.in(0).bits.addr)
-  val secondWord = getVWord(io.in(1).bits.addr)
+  val inptags = io.in.req.map(in => getPTag(in.bits.addr))
+  val invtags = io.in.req.map(in => getVTag(in.bits.vaddr))
+  val sameTag = inptags(0) === inptags(1) && io.in.req(0).valid && io.in.req(1).valid && io.in.req(0).bits.vecValid && io.in.req(1).bits.vecValid
+  val firstWord = getVWord(io.in.req(0).bits.addr)
+  val secondWord = getVWord(io.in.req(1).bits.addr)
   // merge condition
   val mergeMask = Wire(Vec(EnsbufferWidth, Vec(StoreBufferSize, Bool())))
   val mergeIdx = mergeMask.map(PriorityEncoder(_)) // avoid using mergeIdx for better timing
@@ -331,7 +331,7 @@ class Sbuffer(implicit p: Parameters)
     mergeMask(i) := widthMap(j =>
       inptags(i) === ptag(j) && activeMask(j)
     )
-    assert(!(PopCount(mergeMask(i).asUInt) > 1.U && io.in(i).fire && io.in(i).bits.vecValid))
+    assert(!(PopCount(mergeMask(i).asUInt) > 1.U && io.in.req(i).fire && io.in.req(i).bits.vecValid))
   }
 
   // insert condition
@@ -361,7 +361,7 @@ class Sbuffer(implicit p: Parameters)
   val oddInsertVec = GetOddBits.reverse(oddRawInsertVec)
 
   val enbufferSelReg = RegInit(false.B)
-  when(io.in(0).valid) {
+  when(io.in.req(0).valid) {
     enbufferSelReg := ~enbufferSelReg
   }
 
@@ -385,15 +385,15 @@ class Sbuffer(implicit p: Parameters)
   val do_uarch_drain = GatedValidRegNext(forward_need_uarch_drain) || GatedValidRegNext(GatedValidRegNext(merge_need_uarch_drain))
   XSPerfAccumulate("do_uarch_drain", do_uarch_drain)
 
-  io.in(0).ready := firstCanInsert
-  io.in(1).ready := secondCanInsert && io.in(0).ready
+  io.in.req(0).ready := firstCanInsert
+  io.in.req(1).ready := secondCanInsert && io.in.req(0).ready
 
   for (i <- 0 until EnsbufferWidth) {
     // train
     if (EnableStorePrefetchSPB) {
-      prefetcher.io.sbuffer_enq(i).valid := io.in(i).fire && io.in(i).bits.vecValid
+      prefetcher.io.sbuffer_enq(i).valid := io.in.req(i).fire && io.in.req(i).bits.vecValid
       prefetcher.io.sbuffer_enq(i).bits := DontCare
-      prefetcher.io.sbuffer_enq(i).bits.vaddr := io.in(i).bits.vaddr
+      prefetcher.io.sbuffer_enq(i).bits.vaddr := io.in.req(i).bits.vaddr
     } else {
       prefetcher.io.sbuffer_enq(i).valid := false.B
       prefetcher.io.sbuffer_enq(i).bits := DontCare
@@ -402,12 +402,12 @@ class Sbuffer(implicit p: Parameters)
     // prefetch req
     if (EnableStorePrefetchAtCommit) {
       if (EnableAtCommitMissTrigger) {
-        io.store_prefetch(i).valid := prefetcher.io.prefetch_req(i).valid || (io.in(i).fire && io.in(i).bits.vecValid && io.in(i).bits.prefetch)
+        io.store_prefetch(i).valid := prefetcher.io.prefetch_req(i).valid || (io.in.req(i).fire && io.in.req(i).bits.vecValid && io.in.req(i).bits.prefetch)
       } else {
-        io.store_prefetch(i).valid := prefetcher.io.prefetch_req(i).valid || (io.in(i).fire && io.in(i).bits.vecValid)
+        io.store_prefetch(i).valid := prefetcher.io.prefetch_req(i).valid || (io.in.req(i).fire && io.in.req(i).bits.vecValid)
       }
       io.store_prefetch(i).bits.paddr := DontCare
-      io.store_prefetch(i).bits.vaddr := Mux(prefetcher.io.prefetch_req(i).valid, prefetcher.io.prefetch_req(i).bits.vaddr, io.in(i).bits.vaddr)
+      io.store_prefetch(i).bits.vaddr := Mux(prefetcher.io.prefetch_req(i).valid, prefetcher.io.prefetch_req(i).bits.vaddr, io.in.req(i).bits.vaddr)
       prefetcher.io.prefetch_req(i).ready := io.store_prefetch(i).ready
     } else {
       if (EnableStorePrefetchSPB) {
@@ -467,7 +467,7 @@ class Sbuffer(implicit p: Parameters)
     })
   }
 
-  for(((in, vwordOffset), i) <- io.in.zip(Seq(firstWord, secondWord)).zipWithIndex){
+  for(((in, vwordOffset), i) <- io.in.req.zip(Seq(firstWord, secondWord)).zipWithIndex){
     writeReq(i).valid := in.fire && in.bits.vecValid
     writeReq(i).bits.vwordOffset := vwordOffset
     writeReq(i).bits.mask := in.bits.mask
@@ -514,7 +514,7 @@ class Sbuffer(implicit p: Parameters)
     )
   }
 
-  for((req, i) <- io.in.zipWithIndex){
+  for((req, i) <- io.in.req.zipWithIndex){
     XSDebug(req.fire && req.bits.vecValid,
       p"accept req [$i]: " +
         p"addr:${Hexadecimal(req.bits.addr)} " +
@@ -532,7 +532,7 @@ class Sbuffer(implicit p: Parameters)
   // ---------------------- Send Dcache Req ---------------------
 
   val sbuffer_empty = Cat(invalidMask).andR
-  val sq_empty = !Cat(io.in.map(_.valid)).orR
+  val sq_empty = !Cat(io.in.req.map(_.valid)).orR
   val empty = sbuffer_empty && sq_empty
   val threshold = Wire(UInt(5.W)) // RegNext(io.csrCtrl.sbuffer_threshold +& 1.U)
   threshold := Constantin.createRecord(s"StoreBufferThreshold_${p(XSCoreParamsKey).HartId}", initValue = 7)
@@ -779,13 +779,21 @@ class Sbuffer(implicit p: Parameters)
   val mismatch = Wire(Vec(LoadPipelineWidth, Bool()))
   XSPerfAccumulate("vaddr_match_failed", mismatch(0) || mismatch(1))
   for ((forward, i) <- io.forward.zipWithIndex) {
-    val vtag_matches = VecInit(widthMap(w => vtag(w) === getVTag(forward.vaddr)))
+    val s0ReqValid = forward.s0Req.valid
+    val s0Req = forward.s0Req.bits
+    val s1ReqValid = RegNext(s0ReqValid)
+    val s1Req = RegEnable(s0Req, s0ReqValid)
+    val s1Paddr = forward.s1Req.paddr
+    val s1Kill = forward.s1Kill
+    val s2RespValid = forward.s2Resp.valid
+    val s2Resp = forward.s2Resp.bits
+    val vtag_matches = VecInit(widthMap(w => vtag(w) === getVTag(s1Req.vaddr)))
     // ptag_matches uses paddr from dtlb, which is far from sbuffer
-    val ptag_matches = VecInit(widthMap(w => RegEnable(ptag(w), forward.valid) === RegEnable(getPTag(forward.paddr), forward.valid)))
+    val ptag_matches = VecInit(widthMap(w => RegEnable(ptag(w), s1ReqValid) === RegEnable(getPTag(s1Paddr), s1ReqValid)))
     val tag_matches = vtag_matches
-    val tag_mismatch = RegNext(forward.valid) && VecInit(widthMap(w =>
+    val tag_mismatch = RegNext(s1ReqValid) && VecInit(widthMap(w =>
       GatedValidRegNext(vtag_matches(w)) =/= ptag_matches(w) && GatedValidRegNext((activeMask(w) || inflightMask(w)))
-    )).asUInt.orR
+    )).asUInt.orR && !RegEnable(s1Kill, s1ReqValid)
     mismatch(i) := tag_mismatch
     when (tag_mismatch) {
       forward_need_uarch_drain := true.B
@@ -795,22 +803,22 @@ class Sbuffer(implicit p: Parameters)
       "forward tag mismatch: pmatch %x vmatch %x vaddr %x paddr %x\n",
       RegNext(ptag_matches.asUInt),
       RegNext(vtag_matches.asUInt),
-      RegNext(forward.vaddr),
-      RegNext(forward.paddr)
+      RegNext(s1Req.vaddr),
+      RegNext(s1Paddr)
     )
     val valid_tag_matches = widthMap(w => tag_matches(w) && activeMask(w))
     val inflight_tag_matches = widthMap(w => tag_matches(w) && inflightMask(w))
-    val line_offset_mask = UIntToOH(getVWordOffset(forward.paddr))
+    val line_offset_mask = UIntToOH(getVWordOffset(s1Paddr))
 
-    val valid_tag_match_reg = valid_tag_matches.map(RegEnable(_, forward.valid))
-    val inflight_tag_match_reg = inflight_tag_matches.map(RegEnable(_, forward.valid))
+    val valid_tag_match_reg = valid_tag_matches.map(RegEnable(_, s1ReqValid))
+    val inflight_tag_match_reg = inflight_tag_matches.map(RegEnable(_, s1ReqValid))
     val forward_mask_candidate_reg = RegEnable(
-      VecInit(mask.map(entry => entry(getVWordOffset(forward.paddr)))),
-      forward.valid
+      VecInit(mask.map(entry => entry(getVWordOffset(s1Paddr)))),
+      s1ReqValid
     )
     val forward_data_candidate_reg = RegEnable(
-      VecInit(data.map(entry => entry(getVWordOffset(forward.paddr)))),
-      forward.valid
+      VecInit(data.map(entry => entry(getVWordOffset(s1Paddr)))),
+      s1ReqValid
     )
 
     val selectedValidMask = Mux1H(valid_tag_match_reg, forward_mask_candidate_reg)
@@ -827,25 +835,22 @@ class Sbuffer(implicit p: Parameters)
     val selectedInflightMaskFast = Mux1H(line_offset_mask, Mux1H(inflight_tag_matches, mask).asTypeOf(Vec(CacheLineVWords, Vec(VDataBytes, Bool()))))
     val selectedValidMaskFast = Mux1H(line_offset_mask, Mux1H(valid_tag_matches, mask).asTypeOf(Vec(CacheLineVWords, Vec(VDataBytes, Bool()))))
 
-    forward.dataInvalid := false.B // data in store line merge buffer is always ready
-    forward.matchInvalid := tag_mismatch // paddr / vaddr cam result does not match
+    s2Resp.matchInvalid := tag_mismatch // paddr / vaddr cam result does not match
     for (j <- 0 until VDataBytes) {
-      forward.forwardMask(j) := false.B
-      forward.forwardData(j) := DontCare
+      s2Resp.forwardMask(j) := false.B
+      s2Resp.forwardData(j) := DontCare
 
       // valid entries have higher priority than inflight entries
       when(selectedInflightMask(j)) {
-        forward.forwardMask(j) := true.B
-        forward.forwardData(j) := selectedInflightData(j)
+        s2Resp.forwardMask(j) := true.B
+        s2Resp.forwardData(j) := selectedInflightData(j)
       }
       when(selectedValidMask(j)) {
-        forward.forwardMask(j) := true.B
-        forward.forwardData(j) := selectedValidData(j)
+        s2Resp.forwardMask(j) := true.B
+        s2Resp.forwardData(j) := selectedValidData(j)
       }
-
-      forward.forwardMaskFast(j) := selectedInflightMaskFast(j) || selectedValidMaskFast(j)
     }
-    forward.addrInvalid := DontCare
+    s2RespValid := RegNext(s1ReqValid)
   }
 
   for (i <- 0 until StoreBufferSize) {
@@ -967,9 +972,14 @@ class Sbuffer(implicit p: Parameters)
         difftestCommon.mask     := rawMask
         assert(!storeCommitValid || rawData === 0.U, "wline only supports whole zero write now")
       }.otherwise { // Normal scalar store
-        val waddr = ZeroExt(Cat(rawAddr(PAddrBits - 1, 3), 0.U(3.W)), 64)
-        val sbufferMask = shiftMaskToLow(rawAddr, rawMask)
-        val sbufferData = shiftDataToLow(rawAddr, rawData)
+        val isHighPart        = io.diffStore.pmaStore(i).bits.diffIsHighPart
+        val waddr             = ZeroExt(Cat(io.diffStore.pmaStore(i).bits.addr(PAddrBits - 1, 4), isHighPart, 0.U(3.W)), 64)
+        val sbufferMask       = Mux(isHighPart,
+          io.diffStore.pmaStore(i).bits.mask(io.diffStore.pmaStore(i).bits.mask.getWidth - 1, 8),
+          io.diffStore.pmaStore(i).bits.mask(7, 0))
+        val sbufferData       = Mux(isHighPart,
+          io.diffStore.pmaStore(i).bits.data(io.diffStore.pmaStore(i).bits.data.getWidth - 1, 64),
+          io.diffStore.pmaStore(i).bits.data(63, 0))
         val wmask = sbufferMask
         val wdata = sbufferData & MaskExpand(sbufferMask)
         difftestCommon.addr     := waddr
@@ -1001,11 +1011,11 @@ class Sbuffer(implicit p: Parameters)
 
   val perf_valid_entry_count = RegNext(PopCount(VecInit(stateVec.map(s => !s.isInvalid())).asUInt))
   XSPerfHistogram("util", perf_valid_entry_count, true.B, 0, StoreBufferSize, 1)
-  XSPerfAccumulate("sbuffer_req_valid", PopCount(VecInit(io.in.map(_.valid)).asUInt))
-  XSPerfAccumulate("sbuffer_req_fire", PopCount(VecInit(io.in.map(_.fire)).asUInt))
-  XSPerfAccumulate("sbuffer_req_fire_vecinvalid", PopCount(VecInit(io.in.map(data => data.fire && !data.bits.vecValid)).asUInt))
-  XSPerfAccumulate("sbuffer_merge", PopCount(VecInit(io.in.zipWithIndex.map({case (in, i) => in.fire && canMerge(i)})).asUInt))
-  XSPerfAccumulate("sbuffer_newline", PopCount(VecInit(io.in.zipWithIndex.map({case (in, i) => in.fire && !canMerge(i)})).asUInt))
+  XSPerfAccumulate("sbuffer_req_valid", PopCount(VecInit(io.in.req.map(_.valid)).asUInt))
+  XSPerfAccumulate("sbuffer_req_fire", PopCount(VecInit(io.in.req.map(_.fire)).asUInt))
+  XSPerfAccumulate("sbuffer_req_fire_vecinvalid", PopCount(VecInit(io.in.req.map(data => data.fire && !data.bits.vecValid)).asUInt))
+  XSPerfAccumulate("sbuffer_merge", PopCount(VecInit(io.in.req.zipWithIndex.map({case (in, i) => in.fire && canMerge(i)})).asUInt))
+  XSPerfAccumulate("sbuffer_newline", PopCount(VecInit(io.in.req.zipWithIndex.map({case (in, i) => in.fire && !canMerge(i)})).asUInt))
   XSPerfAccumulate("dcache_req_valid", io.dcache.req.valid)
   XSPerfAccumulate("dcache_req_fire", io.dcache.req.fire)
   XSPerfAccumulate("sbuffer_idle", sbuffer_state === x_idle)
@@ -1023,10 +1033,10 @@ class Sbuffer(implicit p: Parameters)
   // XSPerfAccumulate("store_req", io.lsu.req.fire)
 
   val perfEvents = Seq(
-    ("sbuffer_req_valid ", PopCount(VecInit(io.in.map(_.valid)).asUInt)                                                                ),
-    ("sbuffer_req_fire  ", PopCount(VecInit(io.in.map(_.fire)).asUInt)                                                               ),
-    ("sbuffer_merge     ", PopCount(VecInit(io.in.zipWithIndex.map({case (in, i) => in.fire && canMerge(i)})).asUInt)                ),
-    ("sbuffer_newline   ", PopCount(VecInit(io.in.zipWithIndex.map({case (in, i) => in.fire && !canMerge(i)})).asUInt)               ),
+    ("sbuffer_req_valid ", PopCount(VecInit(io.in.req.map(_.valid)).asUInt)                                                                ),
+    ("sbuffer_req_fire  ", PopCount(VecInit(io.in.req.map(_.fire)).asUInt)                                                               ),
+    ("sbuffer_merge     ", PopCount(VecInit(io.in.req.zipWithIndex.map({case (in, i) => in.fire && canMerge(i)})).asUInt)                ),
+    ("sbuffer_newline   ", PopCount(VecInit(io.in.req.zipWithIndex.map({case (in, i) => in.fire && !canMerge(i)})).asUInt)               ),
     ("dcache_req_valid  ", io.dcache.req.valid                                                                                         ),
     ("dcache_req_fire   ", io.dcache.req.fire                                                                                        ),
     ("sbuffer_idle      ", sbuffer_state === x_idle                                                                                    ),
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index a63787af0fc..6af4184578c 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -74,6 +74,9 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
   val pipeWidth = io.fromPipeline.length
   lazy val fuCfg = if (isVStore) VstuCfg else VlduCfg
 
+  private def isOlder(left: VecPipelineFeedbackIO, right: VecPipelineFeedbackIO): Bool = {
+    left.elemIdx < right.elemIdx
+  }
   def EnqConnect(source: MergeBufferReq, sink: MBufferBundle) = {
     sink.data         := source.data
     sink.mask         := source.mask
@@ -131,11 +134,16 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     val hasExp                               = ExceptionNO.selectByFu(source.exceptionVec, fuCfg).asUInt.orR
     sink.robidx                             := source.uop.robIdx
     sink.uopidx                             := source.uop.uopIdx
-    sink.sqIdx                              := source.uop.sqIdx
-    sink.lqIdx                              := source.uop.lqIdx
     sink.feedback(VecFeedbacks.COMMIT)      := !hasExp
     sink.feedback(VecFeedbacks.FLUSH)       := hasExp
     sink.feedback(VecFeedbacks.LAST)        := true.B
+    sink
+  }
+
+  def toExceptionGenConnect(source: MBufferBundle): MemExceptionInfo = {
+    val sink                                 = WireInit(0.U.asTypeOf(new MemExceptionInfo))
+    sink.robIdx                             := source.uop.robIdx
+    sink.uopIdx                             := source.uop.uopIdx
     sink.vstart                             := source.vstart // TODO: if lsq need vl for fof?
     sink.vaddr                              := source.vaddr
     sink.vaNeedExt                          := source.vaNeedExt
@@ -143,6 +151,7 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     sink.isForVSnonLeafPTE                  := source.isForVSnonLeafPTE
     sink.vl                                 := source.vl
     sink.exceptionVec                       := ExceptionNO.selectByFu(source.exceptionVec, fuCfg)
+    sink.isHyper                            := false.B
     sink
   }
 
@@ -229,35 +238,6 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     dontTouch(mergedByPrevPortVec)
   }
 
-  // for exception, select exception, when multi port writeback exception, we need select oldest one
-  def selectOldest[T <: VecPipelineFeedbackIO](valid: Seq[Bool], bits: Seq[T], sel: Seq[UInt]): (Seq[Bool], Seq[T], Seq[UInt]) = {
-    assert(valid.length == bits.length)
-    assert(valid.length == sel.length)
-    if (valid.length == 0 || valid.length == 1) {
-      (valid, bits, sel)
-    } else if (valid.length == 2) {
-      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
-      for (i <- res.indices) {
-        res(i).valid := valid(i)
-        res(i).bits := bits(i)
-      }
-      val oldest = Mux(valid(0) && valid(1),
-        Mux(sel(0) < sel(1),
-            res(0), res(1)),
-        Mux(valid(0) && !valid(1), res(0), res(1)))
-
-      val oldidx = Mux(valid(0) && valid(1),
-        Mux(sel(0) < sel(1),
-          sel(0), sel(1)),
-        Mux(valid(0) && !valid(1), sel(0), sel(1)))
-      (Seq(oldest.valid), Seq(oldest.bits), Seq(oldidx))
-    } else {
-      val left  = selectOldest(valid.take(valid.length / 2), bits.take(bits.length / 2), sel.take(sel.length / 2))
-      val right = selectOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)), sel.takeRight(sel.length - (sel.length / 2)))
-      selectOldest(left._1 ++ right._1, left._2 ++ right._2, left._3 ++ right._3)
-    }
-  }
-
   val pipeValid        = io.fromPipeline.map(_.valid)
   val pipeBits         = io.fromPipeline.map(_.bits)
   val pipeValidReg     = io.fromPipeline.map(x => RegNext(x.valid))
@@ -270,6 +250,7 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
   val portHasExcp       = mergePortMatrixHasExcpWrap.map{_.reduce(_ || _)}
 
   for(i <- io.fromPipeline.indices){
+    val selectModule        = Module(new SelectOldest(io.fromPipeline.head.bits.cloneType, pipeWidth, isOlder).suggestName(s"selectModule_${i}"))
     val pipewbvalid         = if(isVStore) pipeValid(i) else pipeValidReg(i)
     val pipewb              = if(isVStore) pipeBits(i)  else pipeBitsReg(i)
     val pipeWbMbIndex       = pipewb.mBIndex
@@ -282,22 +263,26 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     val entryVstart         = entry.vstart
     val entryElemIdx        = entry.elemIdx
 
-    val sel                    = selectOldest(mergePortMatrixHasExcpWrap(i), selBits, wbElemIdx)
-    val selPort                = sel._2
-    val selElemInfield         = selPort(0).elemIdx & (entries(pipeWbMbIndex).vlmax - 1.U)
-    val selExceptionVec        = selPort(0).exceptionVec
-    val selVaddr               = selPort(0).vaddr
-    val selElemIdx             = selPort(0).elemIdx
+    selectModule.io.in.zipWithIndex.map{case (sink, j) =>
+      sink.valid := mergePortMatrixHasExcpWrap(i)(j)
+      sink.bits  := selBits(j)
+    }
+
+    val selPort                = selectModule.io.out.bits
+    val selElemInfield         = selPort.elemIdx & (entries(pipeWbMbIndex).vlmax - 1.U)
+    val selExceptionVec        = selPort.exceptionVec
+    val selVaddr               = selPort.vaddr
+    val selElemIdx             = selPort.elemIdx
 
-    val isUSFirstUop           = !selPort(0).elemIdx.orR
+    val isUSFirstUop           = !selPort.elemIdx.orR
     // Only the first unaligned uop of unit-stride needs to be offset.
     // When unaligned, the lowest bit of mask is 0.
     //  example: 16'b1111_1111_1111_0000
-    val firstUnmask            = genVFirstUnmask(selPort(0).mask).asUInt
+    val firstUnmask            = genVFirstUnmask(selPort.mask).asUInt
     val addrOffset             = Mux(entryIsUS, firstUnmask, 0.U)
     val vaddr                  = selVaddr + addrOffset
-    val gpaddr                 = selPort(0).gpaddr + addrOffset
-    val vstart                 = Mux(entryIsUS, selPort(0).vstart, selElemInfield)
+    val gpaddr                 = selPort.gpaddr + addrOffset
+    val vstart                 = Mux(entryIsUS, selPort.vstart, selElemInfield)
 
     // select oldest port to raise exception
     when((((entryElemIdx >= selElemIdx) && entryExcp && portHasExcp(i)) || (!entryExcp && portHasExcp(i))) && pipewbvalid && !mergedByPrevPortVecWrap(i)) {
@@ -306,11 +291,11 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
         // For fof loads, if element 0 raises an exception, vl is not modified, and the trap is taken.
         entry.vstart       := vstart
         entry.exceptionVec := ExceptionNO.selectByFu(selExceptionVec, fuCfg)
-        entry.uop.trigger     := selPort(0).trigger
+        entry.uop.trigger  := selPort.trigger
         entry.vaddr        := vaddr
-        entry.vaNeedExt    := selPort(0).vaNeedExt
+        entry.vaNeedExt    := selPort.vaNeedExt
         entry.gpaddr       := gpaddr
-        entry.isForVSnonLeafPTE := selPort(0).isForVSnonLeafPTE
+        entry.isForVSnonLeafPTE := selPort.isForVSnonLeafPTE
       }.otherwise{
         entry.vl           := Mux(entry.vl < vstart, entry.vl, vstart)
       }
@@ -374,6 +359,9 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     //to lsq
     lsqport.bits := ToLsqConnect(selEntry) // when uopwriteback, free MBuffer entry, write to lsq
     lsqport.valid:= selFire && selAllocated && !needRSReplay(entryIdx)
+    //to exceptionGen
+    io.exceptionInfo(i).bits  := toExceptionGenConnect(selEntry)
+    io.exceptionInfo(i).valid := selFire && selAllocated && !needRSReplay(entryIdx)
     //to RS
     val feedbackOut                       = WireInit(0.U.asTypeOf(io.feedback(i).bits)).suggestName(s"feedbackOut_${i}")
     val feedbackValid                     = selFire && selAllocated
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 557bd55ca11..44c7a03710e 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -709,7 +709,6 @@ class VSegmentUnit(val param: ExeUnitParams)(implicit p: Parameters) extends VLS
   io.rdcache.s1_paddr_dup_lsu       := dcacheReqPaddr
   io.rdcache.s1_paddr_dup_dcache    := dcacheReqPaddr
   io.rdcache.s1_kill                := false.B
-  io.rdcache.s1_kill_data_read      := false.B
   io.rdcache.s2_kill                := false.B
   if (env.FPGAPlatform){
     io.rdcache.s0_pc                := DontCare
@@ -782,6 +781,16 @@ class VSegmentUnit(val param: ExeUnitParams)(implicit p: Parameters) extends VLS
   io.vecDifftestInfo.bits.uop      := uopq(deqPtr.value).uop
   io.vecDifftestInfo.bits.start    := 0.U // only use in no-segment unit-stride
   io.vecDifftestInfo.bits.offset   := 0.U
+  io.diffPmaStore.foreach{case sink =>
+    sink.valid                     := io.sbuffer.valid
+    sink.bits.data                 := io.sbuffer.bits.data
+    sink.bits.addr                 := io.sbuffer.bits.addr
+    sink.bits.data                 := io.sbuffer.bits.data
+    sink.bits.mask                 := io.sbuffer.bits.mask
+    sink.bits.wline                := io.sbuffer.bits.wline
+    sink.bits.vecValid             := io.sbuffer.bits.vecValid
+    sink.bits.diffIsHighPart       := io.sbuffer.bits.addr(3)  // segment store event is treat as scalar store!
+  }
 
   /**
    * update ptr
@@ -993,13 +1002,15 @@ class VSegmentUnit(val param: ExeUnitParams)(implicit p: Parameters) extends VLS
 
   // exception
   io.exceptionInfo                    := DontCare
-  io.exceptionInfo.bits.robidx        := instMicroOp.uop.robIdx
-  io.exceptionInfo.bits.uopidx        := uopq(deqPtr.value).uop.vpu.vuopIdx
+  io.exceptionInfo.bits.robIdx        := instMicroOp.uop.robIdx
+  io.exceptionInfo.bits.uopIdx        := uopq(deqPtr.value).uop.vpu.vuopIdx
   io.exceptionInfo.bits.vstart        := instMicroOp.exceptionVstart
   io.exceptionInfo.bits.vaddr         := instMicroOp.exceptionVaddr
   io.exceptionInfo.bits.gpaddr        := instMicroOp.exceptionGpaddr
   io.exceptionInfo.bits.isForVSnonLeafPTE := instMicroOp.exceptionIsForVSnonLeafPTE
   io.exceptionInfo.bits.vl            := instMicroOp.exceptionVl.bits
+  io.exceptionInfo.bits.exceptionVec  := instMicroOp.uop.exceptionVec
+  io.exceptionInfo.bits.isHyper       := false.B
   io.exceptionInfo.valid              := (state === s_finish) && instMicroOp.uop.exceptionVec.asUInt.orR && !isEmpty(enqPtr, deqPtr)
 }
 
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 0e5348af893..7e71c2e334f 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -489,7 +489,7 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
   vstd.bits.fuType := FuType.vstu.U
   vstd.bits.fuOpType := issueUop.fuOpType
   vstd.bits.data := Mux(!issuePreIsSplit, usSplitData, flowData)
-  vstd.bits.vecDebug := DontCare
+  vstd.bits.vecDebug.foreach(_ := DontCare)
 
   if(env.EnableDifftest){
     val usVaddrOffset   = LookupTree(issueEew, List(
@@ -499,8 +499,8 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
       "b11".U -> issueUopAddr(2, 0)
     ))
 
-    vstd.bits.vecDebug.start  := Mux(splitIdx === 0.U, usVaddrOffset, 0.U)// for unaligned store event
-    vstd.bits.vecDebug.offset := usVaddrOffset
+    vstd.bits.vecDebug.foreach(_.start  := Mux(splitIdx === 0.U, usVaddrOffset, 0.U))// for unaligned store event
+    vstd.bits.vecDebug.foreach(_.offset := usVaddrOffset)
   }
 
 }
diff --git a/src/main/scala/xiangshan/mem/vector/VecBundle.scala b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
index 010cd0ea4c7..2419411c6fd 100644
--- a/src/main/scala/xiangshan/mem/vector/VecBundle.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
@@ -126,11 +126,6 @@ class VecPipelineFeedbackIO(isVStore: Boolean=false) (implicit p: Parameters) ex
   val vstart               = UInt(elemIdxBits.W)
   val vecTriggerMask       = UInt((VLEN/8).W)
 
-  //val vec                  = new OnlyVecExuOutput
-   // feedback
-  val vecFeedback          = Bool()
-
-  val usSecondInv          = Bool() // only for unit stride, second flow is Invalid
   val elemIdx              = UInt(elemIdxBits.W) // element index
   val mask                 = UInt(VLENB.W)
   val alignedType          = UInt(alignTypeBits.W)
@@ -158,6 +153,32 @@ class VecPipeBundle(isVStore: Boolean=false)(implicit p: Parameters) extends VLS
   val mBIndex             = if(isVStore) UInt(vsmBindexBits.W) else UInt(vlmBindexBits.W)
   val elemIdx             = UInt(elemIdxBits.W)
   val elemIdxInsideVd     = UInt(elemIdxBits.W) // only use in unit-stride
+
+  // TODO: remove this after unifying interface with vssplit
+  def toVectorLoadIn(): VectorLoadIn = {
+    require(!isVStore)
+    val out = Wire(new VectorLoadIn())
+    out.entrance := LoadEntrance.vectorIssue.U
+    out.accessType.instrType := InstrType.vector.U
+    out.accessType.pftType := DontCare
+    out.accessType.pftCoh := DontCare
+    out.uop := uop
+    out.vaddr := vaddr
+    out.fullva := vaddr
+    out.size := alignedType
+    out.mask := mask
+    out.elemIdx.get := elemIdx
+    out.mbIndex.get := mBIndex
+    out.regOffset.get := reg_offset
+    out.elemIdxInsideVd.get := elemIdxInsideVd
+    out.vecBaseVaddr.get := DontCare
+    out.vecVaddrOffset.get := DontCare
+    out.vecTriggerMask.get := DontCare
+    out.hasROBEntry := true.B
+    out.missDbUpdated := false.B
+    out.occupySource := DontCare
+    out
+  }
 }
 
 object VecFeedbacks {
@@ -206,17 +227,7 @@ class FeedbackToSplitIO(implicit p: Parameters) extends VLSUBundle{
 class FeedbackToLsqIO(implicit p: Parameters) extends VLSUBundle{
   val robidx = new RobPtr
   val uopidx = UopIdx()
-  val sqIdx = new SqPtr
-  val lqIdx = new LqPtr
-  val vaddr = UInt(XLEN.W)
-  val vaNeedExt = Bool()
-  val gpaddr = UInt(GPAddrBits.W)
-  val isForVSnonLeafPTE = Bool()
   val feedback = Vec(VecFeedbacks.allFeedbacks, Bool())
-    // for exception
-  val vstart           = UInt(elemIdxBits.W)
-  val vl               = UInt(elemIdxBits.W)
-  val exceptionVec     = ExceptionVec()
 
   def isFlush  = feedback(VecFeedbacks.FLUSH)
   def isCommit = feedback(VecFeedbacks.COMMIT)
@@ -260,13 +271,14 @@ class VMergeBufferIO(isVStore : Boolean=false)(implicit p: Parameters) extends V
   val fromPipeline        = if(isVStore) Vec(StorePipelineWidth, Flipped(DecoupledIO(new VecPipelineFeedbackIO(isVStore)))) else Vec(LoadPipelineWidth, Flipped(DecoupledIO(new VecPipelineFeedbackIO(isVStore))))
   val fromSplit           = if(isVStore) Vec(VecStorePipelineWidth, new FromSplitIO) else Vec(VecLoadPipelineWidth, new FromSplitIO) // req mergebuffer entry, inactive elem issue
   val uopWriteback        = if(isVStore)  {
-    Vec(VSUopWritebackWidth, DecoupledIO(new ExuOutput(vstuParams.head))) 
+    Vec(VSUopWritebackWidth, DecoupledIO(new ExuOutput(vstuParams.head)))
   } else {
     Vec(VLUopWritebackWidth, DecoupledIO(new ExuOutput(vlduParams.head)))
   }
   val toSplit             = OptionWrapper(!isVStore, new FeedbackToSplitIO())
   val toLsq               = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new FeedbackToLsqIO)) else Vec(VLUopWritebackWidth, ValidIO(new FeedbackToLsqIO)) // for lsq deq
   val feedback            = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new RSFeedback(isVector = true))) else Vec(VLUopWritebackWidth, ValidIO(new RSFeedback(isVector = true)))//for rs replay
+  val exceptionInfo       = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new MemExceptionInfo)) else Vec(VLUopWritebackWidth, ValidIO(new MemExceptionInfo)) //for exceptionInfoGen
 
 //  val fromMisalignBuffer  = OptionWrapper(isVStore, Flipped(new StoreMaBufToVecStoreMergeBufferIO))
 }
@@ -283,15 +295,17 @@ class VSegmentUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends V
   val flush_sbuffer       = new SbufferFlushBundle
   val feedback            = ValidIO(new RSFeedback(isVector = true))
   val redirect            = Flipped(ValidIO(new Redirect))
-  val exceptionInfo       = ValidIO(new FeedbackToLsqIO)
+  val exceptionInfo       = ValidIO(new MemExceptionInfo)
   //trigger
   val fromCsrTrigger      = Input(new CsrTriggerBundle)
+  //difftest
+  val diffPmaStore        = Option.when(debugEn)(ValidIO(new DifftestPmaStoreIO))
 }
 
 class VfofDataBuffIO(val param: ExeUnitParams)(implicit p: Parameters) extends VLSUBundle{
   val redirect            = Flipped(ValidIO(new Redirect))
   val in                  = Vec(VecLoadPipelineWidth, Flipped(Decoupled(new ExuInput(param, hasCopySrc = true))))
-  val mergeUopWriteback   = Vec(VLUopWritebackWidth, Flipped(DecoupledIO(new FeedbackToLsqIO)))
+  val mergeUopWriteback   = Vec(VLUopWritebackWidth, Flipped(DecoupledIO(new MemExceptionInfo)))
 
   val uopWriteback        = DecoupledIO(new ExuOutput(param))
 }
diff --git a/src/main/scala/xiangshan/mem/vector/VecCommon.scala b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
index dcc0f082941..ebc7ade159b 100644
--- a/src/main/scala/xiangshan/mem/vector/VecCommon.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
@@ -222,6 +222,7 @@ abstract class VLSUModule(implicit p: Parameters) extends XSModule
 abstract class VLSUBundle(implicit p: Parameters) extends XSBundle
   with HasVLSUParameters
 
+
 class VLSUBundleWithMicroOp(implicit p: Parameters) extends VLSUBundle {
   val uop = new DynInst
 }
@@ -301,6 +302,7 @@ class VecMemExuOutput(val param: ExeUnitParams)(implicit p: Parameters) extends
   val mask        = UInt(VLENB.W)
   val vaddr       = UInt(XLEN.W)
   val vaNeedExt   = Bool()
+  val isHyper     = Bool()
   val gpaddr      = UInt(GPAddrBits.W)
   val isForVSnonLeafPTE = Bool()
   val vecTriggerMask = UInt((VLEN/8).W)
diff --git a/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala b/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
index 2b6911f2ee0..3d8a5d414e6 100644
--- a/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VfofBuffer.scala
@@ -39,10 +39,14 @@ class VfofDataBundle(implicit p: Parameters) extends VLSUBundle{
 class VfofBuffer(val param: ExeUnitParams)(implicit p: Parameters) extends VLSUModule{
   val io = IO(new VfofDataBuffIO(param))
 
+  private def isOlder(left: DynInst, right: DynInst): Bool = {
+    (right.vpu.vl > left.vpu.vl || left.exceptionVec.asUInt.orR) && !right.exceptionVec.asUInt.orR
+  }
   implicit val vfofParam: ExeUnitParams = param
 
   val entries = RegInit(0.U.asTypeOf(new VfofDataBundle()))
   val valid   = RegInit(false.B)
+  val selectOldestModule = Module(new SelectOldest(new DynInst, VLUopWritebackWidth, isOlder))
 
   val entriesIsFixVl = entries.uop.vpu.lastUop && entries.uop.vpu.isVleff
 
@@ -84,48 +88,23 @@ class VfofBuffer(val param: ExeUnitParams)(implicit p: Parameters) extends VLSUM
 
 
   //Gather writeback information
-  val wbIsfof = io.mergeUopWriteback.map{ x => x.valid && x.bits.robidx === entries.uop.robIdx }
-
-  def getOldest(valid: Seq[Bool], bits: Seq[DynInst]): DynInst = {
-    def getOldest_recursion[T <: Data](valid: Seq[Bool], bits: Seq[DynInst]): (Seq[Bool], Seq[DynInst]) = {
-      assert(valid.length == bits.length)
-      if (valid.length == 1) {
-        (valid, bits)
-      } else if (valid.length == 2) {
-        val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
-        for (i <- res.indices) {
-          res(i).valid := valid(i)
-          res(i).bits := bits(i)
-        }
-        val withExcep0 = bits(0).exceptionVec.asUInt.orR
-        val withExcep1 = bits(1).exceptionVec.asUInt.orR
-        XSError(this.valid && withExcep0 && withExcep1 && valid(0) && valid(1), "Writeback to multiple Uop with exceptions at the same time!\n")
-        val oldest = Mux(
-          valid(0) && valid(1),
-          Mux((bits(1).vpu.vl > bits(0).vpu.vl || withExcep0) && !withExcep1, res(0), res(1)),
-          Mux(valid(0) && !valid(1), res(0), res(1))
-        )
-        (Seq(oldest.valid), Seq(oldest.bits))
-      } else {
-        val left = getOldest_recursion(valid.take(valid.length / 2), bits.take(valid.length / 2))
-        val right = getOldest_recursion(valid.drop(valid.length / 2), bits.drop(valid.length / 2))
-        getOldest_recursion(left._1 ++ right._1, left._2 ++ right._2)
-      }
-    }
-    getOldest_recursion(valid, bits)._2.head
-  }
+  val wbIsfof = io.mergeUopWriteback.map{ x => x.valid && x.bits.robIdx === entries.uop.robIdx }
 
   //Update uop vl
   io.mergeUopWriteback.map{_.ready := true.B}
   val portUop         = Wire(Vec(VLUopWritebackWidth, new DynInst))
   portUop.zip(io.mergeUopWriteback.map(_.bits)).map{ case(sink, source) =>
     sink              := WireInit(0.U.asTypeOf(new DynInst))
-    sink.robIdx       := source.robidx
+    sink.robIdx       := source.robIdx
     sink.vpu.vl       := source.vl
     sink.exceptionVec := source.exceptionVec
   }
-  val wbBits          = getOldest(wbIsfof, portUop)
-  val wbValid         = wbIsfof.reduce(_ || _)
+  selectOldestModule.io.in.zipWithIndex.map{case (sink, i) =>
+    sink.valid := wbIsfof(i)
+    sink.bits := portUop(i)
+  }
+  val wbBits          = selectOldestModule.io.out.bits
+  val wbValid         = selectOldestModule.io.out.valid
   val wbHasException  = wbBits.exceptionVec.asUInt.orR
   val wbUpdateValid = wbValid && (wbBits.vpu.vl < entries.vl || wbHasException) && valid && !needRedirect && !entries.hasException
 
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index a42e51be4bc..8c677d6b0cd 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -94,8 +94,9 @@ package object xiangshan {
     def vlse      = "b01_10_00000".U // strided
     def vloxe     = "b01_11_00000".U // index
 
-    def isWhole  (fuOpType: UInt): Bool = fuOpType(6, 5) === "b00".U && fuOpType(4, 0) === "b01000".U && (fuOpType(8) ^ fuOpType(7))
-    def isMasked (fuOpType: UInt): Bool = fuOpType(6, 5) === "b00".U && fuOpType(4, 0) === "b01011".U && (fuOpType(8) ^ fuOpType(7))
+    def isUnitStride(fuOpType: UInt): Bool = fuOpType(6, 5) === "b00".U
+    def isWhole  (fuOpType: UInt): Bool = isUnitStride(fuOpType) && fuOpType(4, 0) === "b01000".U && (fuOpType(8) ^ fuOpType(7))
+    def isMasked (fuOpType: UInt): Bool = isUnitStride(fuOpType) && fuOpType(4, 0) === "b01011".U && (fuOpType(8) ^ fuOpType(7))
     def isStrided(fuOpType: UInt): Bool = fuOpType(6, 5) === "b10".U && (fuOpType(8) ^ fuOpType(7))
     def isIndexed(fuOpType: UInt): Bool = fuOpType(5) && (fuOpType(8) ^ fuOpType(7))
     def isVecLd  (fuOpType: UInt): Bool = fuOpType(8, 7) === "b01".U
```
