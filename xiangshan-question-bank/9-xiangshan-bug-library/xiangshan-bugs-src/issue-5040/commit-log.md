# Commit Log
- Issue: #5040
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5040
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5040
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5040
- Changed files: 13
- Additions: 131
- Deletions: 128

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala`
- `src/main/scala/xiangshan/frontend/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`
- `src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala`
- `src/main/scala/xiangshan/frontend/ifu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`
- `src/main/scala/xiangshan/frontend/ifu/PredChecker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 89bb52cc038..97ff19e6527 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -34,6 +34,7 @@ import xiangshan.frontend.bpu.BpuCtrl
 import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.frontend.ftq.FtqToCtrlIO
+import xiangshan.frontend.FrontendRedirect
 
 import xiangshan.backend.Bundles.DynInst
 import xiangshan.backend.Bundles.UopIdx
@@ -245,15 +246,9 @@ class XSBundleWithMicroOp(implicit p: Parameters) extends XSBundle {
   val uop = new DynInst
 }
 
-class Redirect(implicit p: Parameters) extends XSBundle {
+class Redirect(implicit p: Parameters) extends FrontendRedirect {
   // for frontend
-  val ftqIdx = new FtqPtr
-  val ftqOffset: UInt = UInt(FetchBlockInstOffsetWidth.W)
-  val target: UInt = UInt(VAddrBits.W)
-  val isRVC: Bool = Bool()
-  val level: UInt = RedirectLevel()
-  val pc: UInt = UInt(VAddrBits.W)
-  val taken: Bool = Bool()
+  val level:       Bool = Bool()
   val backendIGPF: Bool = Bool() // instruction guest page fault
   val backendIPF: Bool = Bool() // instruction page fault
   val backendIAF: Bool = Bool() // instruction access fault
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
index 185da79094a..095e49f23fb 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
@@ -2,6 +2,7 @@ package xiangshan.backend.fu.wrapper
 
 import org.chipsalliance.cde.config.Parameters
 import chisel3._
+import chisel3.util._
 import chisel3.util.log2Up
 import utility.{SignExt, ZeroExt}
 import xiangshan.backend.decode.ImmUnion
@@ -9,6 +10,7 @@ import xiangshan.backend.fu.{BranchModule, FuConfig, FuncUnit}
 import xiangshan.backend.datapath.DataConfig.VAddrData
 import xiangshan.{RedirectLevel, SelImm, XSModule}
 import xiangshan.frontend.PrunedAddrInit
+import xiangshan.frontend.bpu.BranchAttribute
 
 class AddrAddModule(implicit p: Parameters) extends XSModule {
   val io = IO(new Bundle {
@@ -68,6 +70,7 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
       redirect.bits.backendIAF := io.instrAddrTransType.get.checkAccessFault(addModule.io.target)
       redirect.bits.backendIPF := io.instrAddrTransType.get.checkPageFault(addModule.io.target)
       redirect.bits.backendIGPF := io.instrAddrTransType.get.checkGuestPageFault(addModule.io.target)
+      redirect.bits.attribute := io.toFrontendBJUResolve.get.bits.attribute
   }
   io.toFrontendBJUResolve.get.valid := io.out.valid && (io.in.bits.ctrl.identifiedCfi.get || isMisPred)
   io.toFrontendBJUResolve.get.bits.ftqIdx := io.in.bits.ctrl.ftqIdx.get
@@ -77,6 +80,9 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   io.toFrontendBJUResolve.get.bits.taken := dataModule.io.taken
   io.toFrontendBJUResolve.get.bits.mispredict := isMisPred
   io.toFrontendBJUResolve.get.bits.attribute.branchType := io.in.bits.ctrl.preDecode.get.brType
-  io.toFrontendBJUResolve.get.bits.attribute.rasAction := 0.U
+  io.toFrontendBJUResolve.get.bits.attribute.rasAction := Mux1H(
+    Seq(io.in.bits.ctrl.preDecode.get.isCall, io.in.bits.ctrl.preDecode.get.isRet),
+    Seq(BranchAttribute.RasAction.Push, BranchAttribute.RasAction.Pop)
+  )
   connect0LatencyCtrlSingal
 }
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
index 47bc0a6124a..e69f6aee92a 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
@@ -2,12 +2,14 @@ package xiangshan.backend.fu.wrapper
 
 import org.chipsalliance.cde.config.Parameters
 import chisel3._
+import chisel3.util._
 import utility.{SignExt, ZeroExt}
 import xiangshan.RedirectLevel
 import xiangshan.backend.fu.{FuConfig, FuncUnit, JumpDataModule, PipedFuncUnit}
 import xiangshan.JumpOpType
 import xiangshan.backend.datapath.DataConfig.VAddrData
 import xiangshan.frontend.PrunedAddrInit
+import xiangshan.frontend.bpu.BranchAttribute
 
 class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg) {
   private val jumpDataModule = Module(new JumpDataModule)
@@ -52,6 +54,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   redirect.backendIAF := io.instrAddrTransType.get.checkAccessFault(jumpDataModule.io.target)
   redirect.backendIPF := io.instrAddrTransType.get.checkPageFault(jumpDataModule.io.target)
   redirect.backendIGPF := io.instrAddrTransType.get.checkGuestPageFault(jumpDataModule.io.target)
+  redirect.attribute := io.toFrontendBJUResolve.get.bits.attribute
 //  redirect.debug_runahead_checkpoint_id := uop.debugInfo.runahead_checkpoint_id // Todo: assign it
 
   io.in.ready := io.out.ready
@@ -65,6 +68,9 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   io.toFrontendBJUResolve.get.bits.taken := true.B
   io.toFrontendBJUResolve.get.bits.mispredict := isMisPred
   io.toFrontendBJUResolve.get.bits.attribute.branchType := io.in.bits.ctrl.preDecode.get.brType
-  io.toFrontendBJUResolve.get.bits.attribute.rasAction :=  0.U
+  io.toFrontendBJUResolve.get.bits.attribute.rasAction :=  Mux1H(
+    Seq(io.in.bits.ctrl.preDecode.get.isCall, io.in.bits.ctrl.preDecode.get.isRet),
+    Seq(BranchAttribute.RasAction.Push, BranchAttribute.RasAction.Pop)
+  )
   connect0LatencyCtrlSingal
 }
diff --git a/src/main/scala/xiangshan/frontend/Bundles.scala b/src/main/scala/xiangshan/frontend/Bundles.scala
index 30d6cce51eb..3966323206b 100644
--- a/src/main/scala/xiangshan/frontend/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/Bundles.scala
@@ -23,16 +23,19 @@ import org.chipsalliance.cde.config.Parameters
 import utils.EnumUInt
 import xiangshan.InstSeqNum
 import xiangshan.Redirect
+import xiangshan.RedirectLevel
 import xiangshan.TopDownCounters
 import xiangshan.TriggerAction
 import xiangshan.backend.GPAMemEntry
 import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.cache.mmu.TlbResp
+import xiangshan.frontend.bpu.BpuCommit
 import xiangshan.frontend.bpu.BpuMeta
 import xiangshan.frontend.bpu.BpuPrediction
 import xiangshan.frontend.bpu.BpuRedirect
 import xiangshan.frontend.bpu.BpuSpeculationMeta
 import xiangshan.frontend.bpu.BpuTrain
+import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.ibuffer.IBufPtr
 import xiangshan.frontend.icache.ICacheCacheLineHelper
 import xiangshan.frontend.icache.ICachePerfInfo
@@ -57,6 +60,7 @@ class BpuToFtqIO(implicit p: Parameters) extends FrontendBundle {
 class FtqToBpuIO(implicit p: Parameters) extends FrontendBundle {
   val redirect:        Valid[BpuRedirect] = Valid(new BpuRedirect)
   val train:           Valid[BpuTrain]    = Valid(new BpuTrain)
+  val commit:          Valid[BpuCommit]   = Valid(new BpuCommit)
   val bpuPtr:          FtqPtr             = Output(new FtqPtr)
   val redirectFromIFU: Bool               = Output(Bool())
 }
@@ -141,21 +145,20 @@ class FtqToIfuIO(implicit p: Parameters) extends FrontendBundle {
   val flushFromBpu:    BpuFlushInfo             = new BpuFlushInfo
 }
 
-class IfuToFtqIO(implicit p: Parameters) extends FrontendBundle {
-  val mmioCommitRead: MmioCommitRead                       = new MmioCommitRead
-  val pdWb:           Vec[Valid[PredecodeWritebackBundle]] = Vec(FetchPorts, Valid(new PredecodeWritebackBundle))
+class FrontendRedirect(implicit p: Parameters) extends FrontendBundle {
+  val ftqIdx: FtqPtr = new FtqPtr
+  val pc:     UInt   = UInt(VAddrBits.W)
+  val taken:  Bool   = Bool()
+  // The early end position may not always be a branch instruction.
+  val ftqOffset: UInt            = UInt(FetchBlockInstOffsetWidth.W) // maybe use later
+  val isRVC:     Bool            = Bool()                            // seems unused for now, keep it.
+  val attribute: BranchAttribute = new BranchAttribute
+  val target:    UInt            = UInt(VAddrBits.W)
 }
 
-class PredecodeWritebackBundle(implicit p: Parameters) extends FrontendBundle {
-  val pd:             Vec[PreDecodeInfo] = Vec(FetchBlockInstNum, new PreDecodeInfo) // TODO: redefine Predecode
-  val pc:             PrunedAddr         = PrunedAddr(VAddrBits)
-  val ftqIdx:         FtqPtr             = new FtqPtr
-  val takenCfiOffset: UInt               = UInt(FetchBlockInstOffsetWidth.W)
-  val misEndOffset:   Valid[UInt]        = Valid(UInt(FetchBlockInstOffsetWidth.W))
-  val cfiEndOffset:   Valid[UInt]        = Valid(UInt(FetchBlockInstOffsetWidth.W))
-  val target:         PrunedAddr         = PrunedAddr(VAddrBits)
-  val jalTarget:      PrunedAddr         = PrunedAddr(VAddrBits)
-  val instrRange:     Vec[Bool]          = Vec(FetchBlockInstNum, Bool())
+class IfuToFtqIO(implicit p: Parameters) extends FrontendBundle {
+  val mmioCommitRead: MmioCommitRead               = new MmioCommitRead
+  val wbRedirect:     Vec[Valid[FrontendRedirect]] = Vec(FetchPorts, Valid(new FrontendRedirect))
 }
 
 class MmioCommitRead(implicit p: Parameters) extends FrontendBundle {
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 50d7adc2dff..5b60d2b0b8d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -66,7 +66,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper with Co
 
   /* *** aliases *** */
   private val train        = io.fromFtq.train
-  private val commitUpdate = io.fromFtq.train
+  private val commitUpdate = io.fromFtq.commit
   private val redirect     = io.fromFtq.redirect
 
   /* *** CSR ctrl sub-predictor enable *** */
@@ -184,11 +184,9 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper with Co
   ras.io.redirect.bits.meta      := redirect.bits.speculationMeta.rasMeta
   ras.io.redirect.bits.level     := 0.U(1.W)
   ras.io.commit.valid            := commitUpdate.valid
-  ras.io.commit.bits.attribute   := commitUpdate.bits.branches(0).bits.attribute
-  ras.io.commit.bits.startPc     := commitUpdate.bits.startVAddr.toUInt
-  ras.io.commit.bits.isRvc       := false.B // commitUpdate.bits.isRvc
-  ras.io.commit.bits.meta        := commitUpdate.bits.meta.ras
-  ras.io.commit.bits.cfiPosition := commitUpdate.bits.branches(0).bits.cfiPosition
+  ras.io.commit.bits.attribute   := commitUpdate.bits.attribute
+  ras.io.commit.bits.meta        := commitUpdate.bits.rasMeta
+  ras.io.commit.bits.pushAddr    := commitUpdate.bits.pushAddr
   ras.io.specIn.valid            := s3_fire
   ras.io.specIn.bits.startPc     := s3_pc.toUInt
   ras.io.specIn.bits.isRvc       := false.B
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
index 991d0cbea5a..cbaf9d15bd2 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
@@ -64,7 +64,7 @@ object BranchAttribute {
     // indirect branches: jr, jalr
     def Indirect: UInt = 3.U(width.W)
   }
-  private object RasAction extends EnumUInt(4) {
+  object RasAction extends EnumUInt(4) {
     def popBit:  Int = 0
     def pushBit: Int = 1
     // no action
@@ -162,6 +162,13 @@ class BpuTrain(implicit p: Parameters) extends BpuBundle with HalfAlignHelper {
     Mux1H(branches.map(b => (b.valid && b.bits.mispredict, b)))
 }
 
+class BpuCommit(implicit p: Parameters) extends BpuBundle with HalfAlignHelper {
+  val rasMeta:   RasMeta         = new RasMeta
+  val pushAddr:  PrunedAddr      = PrunedAddr(VAddrBits)
+  val attribute: BranchAttribute = new BranchAttribute
+  // TODO: and maybe more
+}
+
 // metadata for redirect (e.g. speculative state recovery) & training (e.g. rasPtr, phr)
 class BpuSpeculationMeta(implicit p: Parameters) extends BpuBundle {
   val phrHistPtr: PhrPtr          = new PhrPtr
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
index e07c5e1e113..8cbee34b8c4 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
@@ -108,11 +108,9 @@ class RasSpecInfo(implicit p: Parameters) extends RasBundle {
 }
 
 class RasCommitInfo(implicit p: Parameters) extends RasBundle {
-  val attribute:   BranchAttribute = new BranchAttribute
-  val cfiPosition: UInt            = UInt(CfiPositionWidth.W)
-  val startPc:     UInt            = UInt(VAddrBits.W)
-  val isRvc:       Bool            = Bool()
-  val meta:        RasMeta         = new RasMeta
+  val attribute: BranchAttribute = new BranchAttribute
+  val pushAddr:  PrunedAddr      = PrunedAddr(VAddrBits)
+  val meta:      RasMeta         = new RasMeta
 }
 
 class RasRedirectInfo(implicit p: Parameters) extends RasBundle {
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
index 1ac735371c4..4034e458740 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
@@ -85,20 +85,20 @@ class Ras(implicit p: Parameters) extends BasePredictor with HasRasParameters wi
   private val stackTOSW    = stack.meta.tosw
   private val redirectTOSW = redirect.bits.meta.tosw
 
-  stack.redirect.valid    := redirect.valid && (isBefore(redirectTOSW, stackTOSW) || !stackNearOverflow)
-  stack.redirect.isCall   := redirect.bits.attribute.isCall && (redirect.bits.level === 0.U)
-  stack.redirect.isRet    := redirect.bits.attribute.isReturn && (redirect.bits.level === 0.U)
-  stack.redirect.meta     := redirect.bits.meta
-  stack.redirect.callAddr := redirect.bits.brPc + Mux(redirect.bits.isRvc, 2.U, 4.U)
-
-  private val commitValid     = RegNext(io.commit.valid, init = false.B)
-  private val commitInfo      = RegEnable(io.commit.bits, io.commit.valid)
-  private val commitBrAlignPc = commitInfo.startPc & alignMask
-  private val commitPushAddr  = commitBrAlignPc + commitInfo.cfiPosition + Mux(commitInfo.isRvc, 2.U, 4.U)
+  stack.redirect.valid  := redirect.valid && (isBefore(redirectTOSW, stackTOSW) || !stackNearOverflow)
+  stack.redirect.isCall := redirect.bits.attribute.isCall && (redirect.bits.level === 0.U)
+  stack.redirect.isRet  := redirect.bits.attribute.isReturn && (redirect.bits.level === 0.U)
+  stack.redirect.meta   := redirect.bits.meta
+  // Redirected branch PC points to end of instruction.
+  stack.redirect.callAddr := redirect.bits.brPc + 2.U
+
+  private val commitValid    = RegNext(io.commit.valid, init = false.B)
+  private val commitInfo     = RegEnable(io.commit.bits, io.commit.valid)
+  private val commitPushAddr = commitInfo.pushAddr
   stack.commit.valid     := commitValid
   stack.commit.pushValid := commitValid && commitInfo.attribute.isCall
   stack.commit.popValid  := commitValid && commitInfo.attribute.isReturn
-  stack.commit.pushAddr  := PrunedAddrInit(commitPushAddr)
+  stack.commit.pushAddr  := commitPushAddr
   stack.commit.metaTosw  := commitInfo.meta.tosw
   stack.commit.metaSsp   := commitInfo.meta.ssp
 
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 8c52a1bb011..a6928162358 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -27,6 +27,7 @@ import utility.HasCircularQueuePtrHelper
 import utility.HasPerfEvents
 import utility.ParallelPriorityMux
 import utility.XSError
+import xiangshan.Redirect
 import xiangshan.RedirectLevel
 import xiangshan.backend.CtrlToFtqIO
 import xiangshan.frontend.BpuToFtqIO
@@ -39,6 +40,7 @@ import xiangshan.frontend.IfuToFtqIO
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.PrunedAddrInit
 import xiangshan.frontend.bpu.BpuSpeculationMeta
+import xiangshan.frontend.bpu.ras.RasMeta
 
 class Ftq(implicit p: Parameters) extends FtqModule
     with HasPerfEvents
@@ -101,7 +103,8 @@ class Ftq(implicit p: Parameters) extends FtqModule
   // resolveQueue stores branch resolve information from backend.
   private val resolveQueue = Module(new ResolveQueue)
 
-  private val ifuRedirect = receiveIfuRedirect(io.fromIfu.pdWb(0))
+  private val specTopAddr = speculationQueue(io.fromIfu.wbRedirect(0).bits.ftqIdx.value).topRetAddr.toUInt
+  private val ifuRedirect = receiveIfuRedirect(io.fromIfu.wbRedirect(0), specTopAddr)
 
   private val (backendRedirectFtqIdx, backendRedirect) = receiveBackendRedirect(io.fromBackend)
 
@@ -283,7 +286,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
   io.toBpu.redirect.bits.target          := redirect.bits.target
   io.toBpu.redirect.bits.isRvc           := redirect.bits.isRVC
   io.toBpu.redirect.bits.taken           := redirect.bits.taken
-  io.toBpu.redirect.bits.attribute       := DontCare
+  io.toBpu.redirect.bits.attribute       := redirect.bits.attribute
   io.toBpu.redirect.bits.speculationMeta := speculationQueue(redirect.bits.ftqIdx.value)
   io.toBpu.redirectFromIFU               := ifuRedirect.valid
 
@@ -324,6 +327,8 @@ class Ftq(implicit p: Parameters) extends FtqModule
   when(commit) {
     commitPtr := commitPtr + 1.U
   }
+  // FIXME: commit info for return stack, connected once ready.
+  io.toBpu.commit := DontCare
 
   // --------------------------------------------------------------------------------
   // MMIO fetch
diff --git a/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala b/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala
index 82362cf5ecd..527b2d3dd50 100644
--- a/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala
@@ -19,21 +19,25 @@ import chisel3._
 import chisel3.util._
 import xiangshan.Redirect
 import xiangshan.RedirectLevel
-import xiangshan.frontend.PredecodeWritebackBundle
+import xiangshan.frontend.FrontendRedirect
 
 trait IfuRedirectReceiver extends HasFtqParameters {
   def receiveIfuRedirect(
-      pdWb: Valid[PredecodeWritebackBundle]
+      wbRedirect:  Valid[FrontendRedirect],
+      specTopAddr: UInt
   ): Valid[Redirect] = {
     val redirect = WireInit(0.U.asTypeOf(Valid(new Redirect)))
 
-    redirect.valid          := pdWb.valid && pdWb.bits.misEndOffset.valid
-    redirect.bits.ftqIdx    := pdWb.bits.ftqIdx
-    redirect.bits.ftqOffset := pdWb.bits.takenCfiOffset
+    redirect.valid          := wbRedirect.valid
+    redirect.bits.ftqIdx    := wbRedirect.bits.ftqIdx
+    redirect.bits.ftqOffset := wbRedirect.bits.ftqOffset
     redirect.bits.level     := RedirectLevel.flushAfter
-    redirect.bits.pc        := pdWb.bits.pc.toUInt
-    redirect.bits.target    := pdWb.bits.target.toUInt
-    redirect.bits.taken     := pdWb.bits.cfiEndOffset.valid
+    redirect.bits.isRVC     := wbRedirect.bits.isRVC
+    redirect.bits.attribute := wbRedirect.bits.attribute
+    redirect.bits.pc        := wbRedirect.bits.pc
+    // redirect.bits.target    := Mux(pdWb.bits.attribute.isReturn, specTopAddr, pdWb.bits.target.toUInt)
+    redirect.bits.target := wbRedirect.bits.target
+    redirect.bits.taken  := wbRedirect.bits.taken
     redirect
   }
 }
diff --git a/src/main/scala/xiangshan/frontend/ifu/Bundles.scala b/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
index 5b564fc28b8..ad9800a45a4 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
@@ -22,6 +22,7 @@ import utils.EnumUInt
 import xiangshan.cache.mmu.Pbmt
 import xiangshan.frontend.ExceptionType
 import xiangshan.frontend.PrunedAddr
+import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.ftq.FtqPtr
 import xiangshan.frontend.ibuffer.IBufPtr
 import xiangshan.frontend.icache.HasICacheParameters
@@ -87,11 +88,13 @@ class ICacheInfo(implicit p: Parameters) extends IfuBundle with HasICacheParamet
 }
 
 class FinalPredCheckResult(implicit p: Parameters) extends IfuBundle {
-  val target:       PrunedAddr  = PrunedAddr(VAddrBits)
-  val misIdx:       Valid[UInt] = Valid(UInt(log2Ceil(IBufferEnqueueWidth).W))
-  val cfiIdx:       Valid[UInt] = Valid(UInt(log2Ceil(IBufferEnqueueWidth).W))
-  val instrRange:   UInt        = UInt(FetchBlockInstNum.W)
-  val invalidTaken: Bool        = Bool()
+  val target:       PrunedAddr      = PrunedAddr(VAddrBits)
+  val misIdx:       Valid[UInt]     = Valid(UInt(log2Ceil(IBufferEnqueueWidth).W))
+  val cfiIdx:       Valid[UInt]     = Valid(UInt(log2Ceil(IBufferEnqueueWidth).W))
+  val instrRange:   UInt            = UInt(FetchBlockInstNum.W)
+  val invalidTaken: Bool            = Bool()
+  val isRVC:        Bool            = Bool()
+  val attribute:    BranchAttribute = new BranchAttribute
 }
 
 /* ***** DB ***** */
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index a377cb29c6b..2357b026fd7 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -42,6 +42,7 @@ import xiangshan.cache.mmu.TlbCmd
 import xiangshan.cache.mmu.TlbRequestIO
 import xiangshan.frontend.ExceptionType
 import xiangshan.frontend.FetchToIBuffer
+import xiangshan.frontend.FrontendRedirect
 import xiangshan.frontend.FrontendTopDownBundle
 import xiangshan.frontend.FtqToIfuIO
 import xiangshan.frontend.ICacheToIfuIO
@@ -51,9 +52,9 @@ import xiangshan.frontend.IfuToICacheIO
 import xiangshan.frontend.IfuToInstrUncacheIO
 import xiangshan.frontend.InstrUncacheToIfuIO
 import xiangshan.frontend.PreDecodeInfo
-import xiangshan.frontend.PredecodeWritebackBundle
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.PrunedAddrInit
+import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.ibuffer.IBufPtr
 import xiangshan.frontend.icache.PmpCheckBundle
 
@@ -1119,7 +1120,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   )
 
   // Write back to Ftq
-  private val mmioFlushWb         = Wire(Valid(new PredecodeWritebackBundle))
+  private val mmioFlushWb         = Wire(Valid(new FrontendRedirect))
   private val s4_mmioMisEndOffset = Wire(ValidUndirectioned(UInt(FetchBlockInstOffsetWidth.W)))
   s4_mmioMisEndOffset.valid := s4_reqIsMmio
   s4_mmioMisEndOffset.bits  := Mux(s4_prevLastIsHalfRvi || mmioIsRvc, 0.U, 1.U)
@@ -1127,18 +1128,16 @@ class Ifu(implicit p: Parameters) extends IfuModule
   // Send mmioFlushWb back to FTQ 1 cycle after uncache fetch return
   // When backend redirect, mmioState reset after 1 cycle.
   // In this case, mask .valid to avoid overriding backend redirect
+  private val mmioTarget = Mux(mmioIsRvc, s4_fetchBlock(0).startVAddr + 2.U, s4_fetchBlock(0).startVAddr + 4.U)
   mmioFlushWb.valid := (s4_reqIsMmio && mmioState === MmioFsmState.WaitCommit && RegNext(fromUncache.fire) &&
     s4_mmioUseSnpc && !s4_ftqFlushSelf && !s4_ftqFlushByOlder)
-  mmioFlushWb.bits.pc := s4_mmioPc
-  mmioFlushWb.bits.pd := 0.U.asTypeOf(Vec(FetchBlockInstNum, new PreDecodeInfo))
-  mmioFlushWb.bits.pd.zipWithIndex.foreach { case (instr, i) => instr.valid := s4_mmioRange(i) }
-  mmioFlushWb.bits.ftqIdx         := s4_fetchBlock(0).ftqIdx
-  mmioFlushWb.bits.takenCfiOffset := s4_fetchBlock(0).takenCfiOffset.bits
-  mmioFlushWb.bits.misEndOffset   := s4_mmioMisEndOffset
-  mmioFlushWb.bits.cfiEndOffset   := DontCare
-  mmioFlushWb.bits.target     := Mux(mmioIsRvc, s4_fetchBlock(0).startVAddr + 2.U, s4_fetchBlock(0).startVAddr + 4.U)
-  mmioFlushWb.bits.jalTarget  := DontCare
-  mmioFlushWb.bits.instrRange := s4_mmioRange
+  mmioFlushWb.bits.ftqIdx    := s4_fetchBlock(0).ftqIdx
+  mmioFlushWb.bits.pc        := s4_fetchBlock(0).startVAddr.toUInt
+  mmioFlushWb.bits.taken     := false.B
+  mmioFlushWb.bits.ftqOffset := s4_mmioMisEndOffset.bits
+  mmioFlushWb.bits.isRVC     := mmioIsRvc
+  mmioFlushWb.bits.attribute := BranchAttribute.None
+  mmioFlushWb.bits.target    := mmioTarget.toUInt
 
   mmioRvcExpander.io.in      := Mux(s4_reqIsMmio, Cat(mmioData(1), mmioData(0)), 0.U)
   mmioRvcExpander.io.fsIsOff := io.csrFsIsOff
@@ -1170,11 +1169,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
     io.toIBuffer.bits.enqEnable := s4_alignBlockStartPos.asUInt // s4_mmioRange.asUInt
 
-    mmioFlushWb.bits.pd(s4_shiftNum).valid  := true.B
-    mmioFlushWb.bits.pd(s4_shiftNum).isRVC  := mmioIsRvc
-    mmioFlushWb.bits.pd(s4_shiftNum).brType := brType
-    mmioFlushWb.bits.pd(s4_shiftNum).isCall := isCall
-    mmioFlushWb.bits.pd(s4_shiftNum).isRet  := isRet
+    mmioFlushWb.bits.isRVC     := mmioIsRvc
+    mmioFlushWb.bits.attribute := BranchAttribute(brType, Cat(isCall, isRet))
   }
 
   mmioRedirect.valid := s4_reqIsMmio && mmioState === MmioFsmState.WaitCommit &&
@@ -1193,28 +1189,6 @@ class Ifu(implicit p: Parameters) extends IfuModule
    * - redirect if found fault prediction
    * - redirect if false hit last half(last PC is not start + 32 Bytes, but in the middle of an notCFI RVI instruction)
    * ***************************************************************************** */
-
-  // According to the discussed version, IFU will no longer need to send predecode information to FTQ in the future.
-  // Therefore, this part of the logic will not be optimized further and will be removed later.
-  private val firstRawPds  = WireDefault(VecInit.fill(FetchBlockInstNum)(0.U.asTypeOf(new PreDecodeInfo)))
-  private val secondRawPds = WireDefault(VecInit.fill(FetchBlockInstNum)(0.U.asTypeOf(new PreDecodeInfo)))
-  // firstRawPds.zipWithIndex.foreach {
-  //   case (rawPd, i) =>
-  //     rawPd := Mux(
-  //       s4_rawInstrValid(i),
-  //       s4_alignPds(s4_rawIndex(i) + s4_prevIBufEnqPtr.value(1, 0)),
-  //       0.U.asTypeOf(new PreDecodeInfo)
-  //     )
-  // }
-  // secondRawPds.zipWithIndex.foreach {
-  //   case (rawPd, i) =>
-  //     rawPd := Mux(
-  //       s4_rawInstrValid(i.U + s4_fetchBlock(0).fetchSize),
-  //       s4_alignPds(s4_rawIndex(i.U + s4_fetchBlock(0).fetchSize) + s4_prevIBufEnqPtr.value(1, 0)),
-  //       0.U.asTypeOf(new PreDecodeInfo)
-  //     )
-  // }
-
   private val wbEnable              = RegNext(s3_fire && !s3_flush) && !s4_reqIsMmio && !s4_flush
   private val wbValid               = RegNext(wbEnable, init = false.B)
   private val wbFirstValid          = RegEnable(s4_firstValid, wbEnable)
@@ -1223,8 +1197,6 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val wbPrevIBufEnqPtr      = RegEnable(s4_prevIBufEnqPtr, wbEnable)
   private val wbInstrCount          = RegEnable(PopCount(io.toIBuffer.bits.enqEnable), wbEnable)
   private val wbAlignInstrEndOffset = RegEnable(s4_alignInstrEndOffset, wbEnable)
-  private val wbFirstRawPds         = RegEnable(firstRawPds, wbEnable)
-  private val wbSecondRawPds        = RegEnable(secondRawPds, wbEnable)
 
   private val wbCurrentLastHalfData = RegEnable(s4_currentLastHalfData, wbEnable)
   // private val wbPrevLastHalfPc      = RegEnable(s4_prevLastHalfPc, wbEnable)
@@ -1242,33 +1214,27 @@ class Ifu(implicit p: Parameters) extends IfuModule
   }
 
   private val checkFlushWb = VecInit((0 until FetchPorts).map { i =>
-    val b       = Wire(Valid(new PredecodeWritebackBundle))
+    val b       = Wire(Valid(new FrontendRedirect))
     val missIdx = wbStage2Check(i).misIdx.bits
-    b.valid               := wbValid && wbFirstValid // Primarily used as a placeholder; the value will be overwritten.
-    b.bits.pd             := wbFirstRawPds           // Primarily used as a placeholder; the value will be overwritten.
-    b.bits.pc             := catPC(wbAlignInstrPcLower(missIdx), wbFetchBlock(i).pcHigh, wbFetchBlock(i).pcHighPlus1)
-    b.bits.ftqIdx         := wbFetchBlock(i).ftqIdx
-    b.bits.takenCfiOffset := wbFetchBlock(i).takenCfiOffset.bits
-    b.bits.misEndOffset.valid := wbStage2Check(i).misIdx.valid
-    b.bits.misEndOffset.bits  := wbAlignInstrEndOffset(wbStage2Check(i).misIdx.bits)
-    b.bits.cfiEndOffset.valid := wbStage2Check(i).cfiIdx.valid
-    b.bits.cfiEndOffset.bits  := wbAlignInstrEndOffset(wbStage2Check(i).cfiIdx.bits)
-    b.bits.target             := wbStage2Check(i).target
-    b.bits.jalTarget          := wbStage2Check(i).target
-    b.bits.instrRange         := wbInstrRange(i).asTypeOf(Vec(FetchBlockInstNum, Bool()))
+    // TODO: Logic is redundant, to be cleaned up later.
+    b.valid          := wbValid && wbFirstValid && wbStage2Check(i).misIdx.valid
+    b.bits.ftqIdx    := wbFetchBlock(i).ftqIdx
+    b.bits.pc        := wbFetchBlock(i).startVAddr.toUInt
+    b.bits.taken     := wbStage2Check(i).cfiIdx.valid
+    b.bits.ftqOffset := wbAlignInstrEndOffset(wbStage2Check(i).misIdx.bits)
+    b.bits.isRVC     := wbStage2Check(i).isRVC
+    b.bits.attribute := wbStage2Check(i).attribute
+    b.bits.target    := wbStage2Check(i).target.toUInt
     b
   })
 
-  checkFlushWb(0).valid   := wbValid && wbFirstValid
-  checkFlushWb(1).valid   := wbValid && wbSecondValid
-  checkFlushWb(0).bits.pd := wbFirstRawPds
-  checkFlushWb(1).bits.pd := wbSecondRawPds
+  checkFlushWb(0).valid := wbValid && wbFirstValid && wbStage2Check(0).misIdx.valid
+  checkFlushWb(1).valid := wbValid && wbSecondValid && wbStage2Check(1).misIdx.valid
 
-  toFtq.pdWb(0) := Mux(wbValid, checkFlushWb(0), mmioFlushWb)
-  toFtq.pdWb(1) := checkFlushWb(1)
+  toFtq.wbRedirect(0) := Mux(wbValid, checkFlushWb(0), mmioFlushWb)
+  toFtq.wbRedirect(1) := checkFlushWb(1)
 
-  wbRedirect.valid := (checkFlushWb(0).bits.misEndOffset.valid && checkFlushWb(0).valid) ||
-    (checkFlushWb(1).bits.misEndOffset.valid && checkFlushWb(1).valid)
+  wbRedirect.valid          := checkFlushWb(0).valid || checkFlushWb(1).valid
   wbRedirect.isHalfInstr    := wbCurrentLastRvi && wbStage2Check(0).invalidTaken
   wbRedirect.instrCount     := wbInstrCount
   wbRedirect.prevIBufEnqPtr := wbPrevIBufEnqPtr
@@ -1376,8 +1342,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val ifuWbToFtqDumpData = Wire(new IfuWbToFtqDB)
   for (i <- 0 until FetchPorts) {
     ifuWbToFtqDumpData.startAddr(i)      := wbFetchBlock(i).startVAddr.toUInt
-    ifuWbToFtqDumpData.isMissPred(i)     := checkFlushWb(i).bits.misEndOffset.valid
-    ifuWbToFtqDumpData.missPredOffset(i) := checkFlushWb(i).bits.misEndOffset.bits
+    ifuWbToFtqDumpData.isMissPred(i)     := checkFlushWb(i).valid
+    ifuWbToFtqDumpData.missPredOffset(i) := checkFlushWb(i).bits.ftqOffset
   }
   ifuWbToFtqDumpData.checkJalFault     := checkJalFault(0) | checkJalFault(1)
   ifuWbToFtqDumpData.checkJalrFault    := checkJalrFault(0) | checkJalrFault(1)
diff --git a/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala b/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala
index 027c1d47fd2..137f644cdef 100644
--- a/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala
@@ -24,6 +24,7 @@ import utility.ParallelPriorityEncoder
 import xiangshan.ValidUndirectioned
 import xiangshan.frontend.PreDecodeInfo
 import xiangshan.frontend.PrunedAddr
+import xiangshan.frontend.bpu.BranchAttribute
 
 class PredChecker(implicit p: Parameters) extends IfuModule {
   class PredCheckerIO extends IfuBundle {
@@ -182,6 +183,11 @@ class PredChecker(implicit p: Parameters) extends IfuModule {
       mispredInstrIdx.valid &&
       (pds(mispredInstrIdx.bits).isJal || pds(mispredInstrIdx.bits).isBr)
 
+  private val firstFinalIdx   = Mux(mispredInstrIdx.valid, mispredInstrIdx.bits, firstTakenIdx)
+  private val firstFinalIsRVC = pds(firstFinalIdx).isRVC
+  private val firstAttribute  = WireDefault(0.U.asTypeOf(new BranchAttribute))
+  firstAttribute.branchType := pds(firstFinalIdx).brType
+  firstAttribute.rasAction  := Cat(pds(firstFinalIdx).isCall, pds(firstFinalIdx).isRet)
   /* *****************************************************************************
    * PredChecker Stage 2
    * ***************************************************************************** */
@@ -201,9 +207,10 @@ class PredChecker(implicit p: Parameters) extends IfuModule {
   private val jumpTargetsNext = RegEnable(jumpTargets, io.req.valid)
   private val seqTargetsNext  = RegEnable(seqTargets, io.req.valid)
 
-  private val firstPredTakenNext = RegEnable(firstPredTaken, io.req.valid)
-  private val pdsNext            = RegEnable(pds, io.req.valid)
-  private val fixedRangeNext     = RegEnable(fixedRange, io.req.valid)
+  private val firstPredTakenNext  = RegEnable(firstPredTaken, io.req.valid)
+  private val firstFinalIsRVCNext = RegEnable(firstFinalIsRVC, io.req.valid)
+  private val firstAttributeNext  = RegEnable(firstAttribute, io.req.valid)
+  private val fixedRangeNext      = RegEnable(fixedRange, io.req.valid)
   // --------- These registers are only for performance debugging purposes ---------------------/
   private val jalFaultVecNext  = RegEnable(jalFaultVec, io.req.valid)
   private val jalrFaultVecNext = RegEnable(jalrFaultVec, io.req.valid)
@@ -230,6 +237,7 @@ class PredChecker(implicit p: Parameters) extends IfuModule {
   private val mispredTarget =
     Mux(mispredIsJumpNext, jumpTargetsNext(mispredIdxNext.bits), seqTargetsNext(mispredIdxNext.bits))
 
+  // TODO: Need to rethink this interface
   io.resp.stage2Out.fixedFirst.target       := Mux(fixFirstMispred, mispredTarget, firstPredTargetNext)
   io.resp.stage2Out.fixedFirst.misIdx.valid := fixFirstMispred
   io.resp.stage2Out.fixedFirst.misIdx.bits  := Mux(fixFirstMispred, mispredIdxNext.bits, firstTakenIdxNext)
@@ -237,6 +245,8 @@ class PredChecker(implicit p: Parameters) extends IfuModule {
   io.resp.stage2Out.fixedFirst.cfiIdx.bits  := fixedFirstTakenInstrIdxNext.bits
   io.resp.stage2Out.fixedFirst.instrRange   := fixedFirstRawInstrRange
   io.resp.stage2Out.fixedFirst.invalidTaken := fixFirstMispred && invalidTakenNext(mispredIdxNext.bits)
+  io.resp.stage2Out.fixedFirst.isRVC        := firstFinalIsRVCNext
+  io.resp.stage2Out.fixedFirst.attribute    := firstAttributeNext
 
   io.resp.stage2Out.fixedSecond.target       := Mux(fixSecondMispred, mispredTarget, secondPredTargetNext)
   io.resp.stage2Out.fixedSecond.misIdx.valid := fixSecondMispred
@@ -246,6 +256,8 @@ class PredChecker(implicit p: Parameters) extends IfuModule {
   io.resp.stage2Out.fixedSecond.instrRange   := fixedSecondRawInstrRange
   // FIXME: second fetch block invalid taken
   io.resp.stage2Out.fixedSecond.invalidTaken := false.B
+  io.resp.stage2Out.fixedSecond.isRVC        := false.B
+  io.resp.stage2Out.fixedSecond.attribute    := DontCare
 
   private val faultType = MuxCase(
     PreDecodeFaultType.NoFault,
```
