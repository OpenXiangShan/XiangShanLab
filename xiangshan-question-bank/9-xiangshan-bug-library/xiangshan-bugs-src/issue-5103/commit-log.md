# Commit Log
- Issue: #5103
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5103
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5103
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5103
- Changed files: 12
- Additions: 61
- Deletions: 42

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/fu/Branch.scala`
- `src/main/scala/xiangshan/backend/fu/FuncUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/frontend/Bundles.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`
- `src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 261e79816a5..ed1ca3b2a80 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -99,7 +99,8 @@ class CtrlFlow(implicit p: Parameters) extends XSBundle {
   val backendException = Bool()
   val trigger = TriggerAction()
   val pd = new PreDecodeInfo
-  val pred_taken = Bool()
+  val fixedTaken = Bool()
+  val predTaken  = Bool()
   val crossPageIPFFix = Bool()
   val storeSetHit = Bool() // inst has been allocated an store set
   val waitForRobIdx = new RobPtr // store set predicted previous store robIdx
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 304172df720..a778ae839d4 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -59,7 +59,8 @@ object Bundles {
     val isFetchMalAddr = Bool()
     val trigger = TriggerAction()
     val preDecodeInfo = new PreDecodeInfo
-    val pred_taken = Bool()
+    val fixedTaken = Bool()
+    val predTaken  = Bool()
     val crossPageIPFFix = Bool()
     val ftqPtr = new FtqPtr
     val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
@@ -70,7 +71,6 @@ object Bundles {
     def connectCtrlFlow(source: CtrlFlow): Unit = {
       connectSamePort(this, source)
       this.preDecodeInfo := source.pd
-      this.pred_taken := source.pred_taken
       this.isFetchMalAddr := source.backendException
       this.debug.foreach(_.pc := source.pc)
       this.debug.foreach(_.debug_seqNum := source.debug_seqNum)
@@ -88,7 +88,8 @@ object Bundles {
     val isFetchMalAddr = Bool()
     val trigger = TriggerAction()
     val preDecodeInfo = new PreDecodeInfo
-    val pred_taken = Bool()
+    val fixedTaken = Bool()
+    val predTaken  = Bool()
     val crossPageIPFFix = Bool()
     val ftqPtr = new FtqPtr
     val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
@@ -180,7 +181,8 @@ object Bundles {
     val isFetchMalAddr = Bool()
     val trigger = TriggerAction()
     val preDecodeInfo = new PreDecodeInfo
-    val pred_taken = Bool()
+    val fixedTaken = Bool()
+    val predTaken = Bool()
     val crossPageIPFFix = Bool()
     val ftqPtr = new FtqPtr
     val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
@@ -253,7 +255,8 @@ object Bundles {
     def numSrc = backendParams.numSrc
     // from frontend
     val preDecodeInfo = new PreDecodeInfo
-    val pred_taken = Bool()
+    val fixedTaken = Bool()
+    val predTaken = Bool()
     val ftqPtr = new FtqPtr
     val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
     // from decode
@@ -309,7 +312,8 @@ object Bundles {
     def numSrc = backendParams.numSrc
     // from frontend
     val preDecodeInfo = new PreDecodeInfo
-    val pred_taken = Bool()
+    val fixedTaken = Bool()
+    val predTaken = Bool()
     val ftqPtr = new FtqPtr
     val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
     // from decode
@@ -375,7 +379,8 @@ object Bundles {
     val hasException    = Bool()
     val trigger         = TriggerAction()
     val preDecodeInfo   = new PreDecodeInfo
-    val pred_taken      = Bool()
+    val fixedTaken      = Bool()
+    val predTaken       = Bool()
     val crossPageIPFFix = Bool()
     val ftqPtr          = new FtqPtr
     val ftqOffset       = UInt(FetchBlockInstOffsetWidth.W)
@@ -858,7 +863,8 @@ object Bundles {
                                                   Some(UInt(FetchBlockInstOffsetWidth.W))  else None
     val predictInfo   = if (params.needPdInfo)  Some(new Bundle {
       val target = UInt(VAddrData().dataWidth.W)
-      val taken = Bool()
+      val fixedTaken = Bool()
+      val predTaken = Bool()
     }) else None
     val loadWaitBit    = OptionWrapper(params.hasLoadExu, Bool())
     val waitForRobIdx  = OptionWrapper(params.hasLoadExu, new RobPtr) // store set predicted previous store robIdx
diff --git a/src/main/scala/xiangshan/backend/fu/Branch.scala b/src/main/scala/xiangshan/backend/fu/Branch.scala
index b9cca783fbe..582741e5b88 100644
--- a/src/main/scala/xiangshan/backend/fu/Branch.scala
+++ b/src/main/scala/xiangshan/backend/fu/Branch.scala
@@ -26,7 +26,7 @@ class BranchModule(implicit p: Parameters) extends XSModule {
   val io = IO(new Bundle() {
     val src = Vec(2, Input(UInt(XLEN.W)))
     val func = Input(FuOpType())
-    val pred_taken = Input(Bool())
+    val fixedTaken = Input(Bool())
     val taken, mispredict = Output(Bool())
   })
   val (src1, src2, func) = (io.src(0), io.src(1), io.func)
@@ -47,5 +47,5 @@ class BranchModule(implicit p: Parameters) extends XSModule {
   val taken = LookupTree(BRUOpType.getBranchType(func), branchOpTable) ^ BRUOpType.isBranchInvert(func)
 
   io.taken := taken
-  io.mispredict := io.pred_taken ^ taken
+  io.mispredict := io.fixedTaken ^ taken
 }
diff --git a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
index c9a93f17c5b..bd31fadfd33 100644
--- a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
@@ -31,8 +31,9 @@ class FuncUnitCtrlInput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle
   val ftqIdx      = OptionWrapper(cfg.needPc || cfg.replayInst || cfg.isSta || cfg.isCsr, new FtqPtr)
   val ftqOffset   = OptionWrapper(cfg.needPc || cfg.replayInst || cfg.isSta || cfg.isCsr, UInt(FetchBlockInstOffsetWidth.W))
   val predictInfo = OptionWrapper(cfg.needPdInfo, new Bundle {
-    val target    = UInt(VAddrData().dataWidth.W)
-    val taken     = Bool()
+    val target     = UInt(VAddrData().dataWidth.W)
+    val fixedTaken = Bool()
+    val predTaken  = Bool()
   })
   val fpu         = OptionWrapper(cfg.writeFflags, new FPUCtrlSignals)
   val vpu         = OptionWrapper(cfg.needVecCtrl, new VPUCtrlSignals)
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
index 1aa5f9d713f..1c167159edc 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
@@ -35,7 +35,7 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   dataModule.io.src(0) := io.in.bits.data.src(0) // rs1
   dataModule.io.src(1) := io.in.bits.data.src(1) // rs2
   dataModule.io.func := io.in.bits.ctrl.fuOpType
-  dataModule.io.pred_taken := io.in.bits.ctrl.predictInfo.get.taken
+  dataModule.io.fixedTaken := io.in.bits.ctrl.predictInfo.get.fixedTaken
 
   val pcExtend = Mux(io.instrAddrTransType.get.shouldBeSext,
     SignExt(io.in.bits.data.pc.get, VAddrBits + 1),
@@ -52,7 +52,8 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
 
   val brhPredictTarget = io.in.bits.ctrl.predictInfo.get.target
   val brhRealTarget = addModule.io.target
-  val isMisPred = dataModule.io.mispredict || dataModule.io.pred_taken && dataModule.io.taken && (brhRealTarget =/= brhPredictTarget)
+  val targetWrong = dataModule.io.fixedTaken && dataModule.io.taken && (brhRealTarget =/= brhPredictTarget)
+  val isMisPred = dataModule.io.mispredict || targetWrong
   io.out.bits.res.data := 0.U
   io.out.bits.res.redirect.get match {
     case redirect =>
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
index 60701c26fa5..b229648b602 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
@@ -33,10 +33,14 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   jumpDataModule.io.func := func
   jumpDataModule.io.isRVC := isRVC
 
-  val predTaken = io.in.bits.ctrl.predictInfo.get.taken
+  val fixedTaken = io.in.bits.ctrl.predictInfo.get.fixedTaken
+  val predTaken  = io.in.bits.ctrl.predictInfo.get.predTaken
   val jmpPredictTarget = io.in.bits.ctrl.predictInfo.get.target
   val jumpRealTarget = jumpDataModule.io.target(VAddrData().dataWidth - 1, 0)
-  val isMisPred = !predTaken || (jumpRealTarget =/= jmpPredictTarget)
+
+  val targetWrong = jumpRealTarget =/= jmpPredictTarget
+  val needRedirect = !fixedTaken || targetWrong
+  val needTrain = !predTaken || targetWrong
 
   val redirect = io.out.bits.res.redirect.get.bits
   val redirectValid = io.out.bits.res.redirect.get.valid
@@ -50,7 +54,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   redirect.taken := true.B
   redirect.target := jumpDataModule.io.target
   redirect.pc := io.in.bits.data.pc.get
-  redirect.isMisPred := isMisPred
+  redirect.isMisPred := needRedirect
   redirect.backendIAF := io.instrAddrTransType.get.checkAccessFault(jumpDataModule.io.target)
   redirect.backendIPF := io.instrAddrTransType.get.checkPageFault(jumpDataModule.io.target)
   redirect.backendIGPF := io.instrAddrTransType.get.checkGuestPageFault(jumpDataModule.io.target)
@@ -66,7 +70,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   io.toFrontendBJUResolve.get.bits.pc := PrunedAddrInit(pc)
   io.toFrontendBJUResolve.get.bits.target := PrunedAddrInit(jumpDataModule.io.target)
   io.toFrontendBJUResolve.get.bits.taken := true.B
-  io.toFrontendBJUResolve.get.bits.mispredict := isMisPred
+  io.toFrontendBJUResolve.get.bits.mispredict := needTrain
   io.toFrontendBJUResolve.get.bits.attribute.branchType := io.in.bits.ctrl.preDecode.get.brType
   io.toFrontendBJUResolve.get.bits.attribute.rasAction :=  Mux1H(
     Seq(io.in.bits.ctrl.preDecode.get.isCall, io.in.bits.ctrl.preDecode.get.isRet),
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index a7b16bb69ba..eb516f2f822 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -1125,7 +1125,8 @@ class IssueQueueIntImp(implicit p: Parameters, params: IssueBlockParams)  extend
     deq.bits.common.ftqOffset.foreach(_ := deqEntryVec(i).bits.payload.ftqOffset)
     deq.bits.common.predictInfo.foreach(x => {
       x.target := DontCare
-      x.taken := deqEntryVec(i).bits.payload.pred_taken
+      x.fixedTaken := deqEntryVec(i).bits.payload.fixedTaken
+      x.predTaken  := deqEntryVec(i).bits.payload.predTaken
     })
     // for std
     deq.bits.common.sqIdx.foreach(_ := deqEntryVec(i).bits.payload.sqIdx)
diff --git a/src/main/scala/xiangshan/frontend/Bundles.scala b/src/main/scala/xiangshan/frontend/Bundles.scala
index ed10bee898c..69eeb787321 100644
--- a/src/main/scala/xiangshan/frontend/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/Bundles.scala
@@ -284,8 +284,9 @@ class FtqPcOffset(implicit p: Parameters) extends FrontendBundle {
 }
 
 class InstrEndOffset(implicit p: Parameters) extends FrontendBundle {
-  val taken:  Bool = Bool()
-  val offset: UInt = UInt(FetchBlockInstOffsetWidth.W)
+  val predTaken:  Bool = Bool()
+  val fixedTaken: Bool = Bool()
+  val offset:     UInt = UInt(FetchBlockInstOffsetWidth.W)
 }
 
 class FetchToIBuffer(implicit p: Parameters) extends FrontendBundle {
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 6a76395d307..4a02409bbc2 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -240,7 +240,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
     for (i <- 0 until DecodeWidth - 1) {
       // for instrs that is not the last, if a taken br, the next instr should not have the same ftqPtr
       // for instrs that is the last, record and check next request
-      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.pred_taken) {
+      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.fixedTaken) {
         when(ibuffer.io.out(i + 1).fire) {
           // not last br, check now
         }.otherwise {
@@ -250,15 +250,14 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
         }
       }
       XSError(
-        ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.pred_taken &&
+        ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.fixedTaken &&
           ibuffer.io.out(i + 1).fire &&
           (checkTargetPtr(i) + 1.U).value =/= checkTargetPtr(i + 1).value,
         "taken br should have consecutive ftqPtr\n"
       )
     }
-    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr && ibuffer.io.out(
-      DecodeWidth - 1
-    ).bits.pred_taken) {
+    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr &&
+      ibuffer.io.out(DecodeWidth - 1).bits.fixedTaken) {
       // last instr is a br, record its info
       prevTakenValid  := true.B
       prevTakenFtqPtr := checkTargetPtr(DecodeWidth - 1)
@@ -283,7 +282,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
     val prevNotTakenValid = RegInit(0.B)
 
     for (i <- 0 until DecodeWidth - 1) {
-      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && !ibuffer.io.out(i).bits.pred_taken) {
+      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && !ibuffer.io.out(i).bits.fixedTaken) {
         when(ibuffer.io.out(i + 1).fire) {}.otherwise {
           prevNotTakenValid := true.B
           prevIsRVC         := ibuffer.io.out(i).bits.pd.isRVC
@@ -291,7 +290,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
         }
       }
       XSError(
-        ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && !ibuffer.io.out(i).bits.pred_taken &&
+        ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && !ibuffer.io.out(i).bits.fixedTaken &&
           ibuffer.io.out(i + 1).fire &&
           ibuffer.io.out(i).bits.pc + Mux(ibuffer.io.out(i).bits.pd.isRVC, 2.U, 4.U) =/= ibuffer.io.out(
             i + 1
@@ -299,9 +298,8 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
         "not-taken br should have consecutive pc\n"
       )
     }
-    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr && !ibuffer.io.out(
-      DecodeWidth - 1
-    ).bits.pred_taken) {
+    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr &&
+      !ibuffer.io.out(DecodeWidth - 1).bits.fixedTaken) {
       prevNotTakenValid := true.B
       prevIsRVC         := ibuffer.io.out(DecodeWidth - 1).bits.pd.isRVC
       prevNotTakenPC    := ibuffer.io.out(DecodeWidth - 1).bits.pc
@@ -327,22 +325,21 @@ class FrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBase(
     prevTakenTarget := checkPcMem((prevTakenFtqPtr + 1.U).value)
 
     for (i <- 0 until DecodeWidth - 1) {
-      when(ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.pred_taken) {
+      when(ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.fixedTaken) {
         when(ibuffer.io.out(i + 1).fire) {}.otherwise {
           prevTakenValid  := true.B
           prevTakenFtqPtr := checkTargetPtr(i)
         }
       }
       XSError(
-        ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.pred_taken &&
+        ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.fixedTaken &&
           ibuffer.io.out(i + 1).fire &&
           checkTarget(i) =/= PrunedAddrInit(ibuffer.io.out(i + 1).bits.pc),
         "taken instr should follow target pc\n"
       )
     }
-    when(ibuffer.io.out(DecodeWidth - 1).fire && !ibuffer.io.out(DecodeWidth - 1).bits.pd.notCFI && ibuffer.io.out(
-      DecodeWidth - 1
-    ).bits.pred_taken) {
+    when(ibuffer.io.out(DecodeWidth - 1).fire && !ibuffer.io.out(DecodeWidth - 1).bits.pd.notCFI &&
+      ibuffer.io.out(DecodeWidth - 1).bits.fixedTaken) {
       prevTakenValid  := true.B
       prevTakenFtqPtr := checkTargetPtr(DecodeWidth - 1)
     }
diff --git a/src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala b/src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala
index 1c1f6c46c83..bee938fedee 100644
--- a/src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala
@@ -89,6 +89,7 @@ class IBufEntry(implicit p: Parameters) extends IBufferBundle {
   val foldpc:           UInt          = UInt(MemPredPCWidth.W)
   val pd:               PreDecodeInfo = new PreDecodeInfo
   val predTaken:        Bool          = Bool()
+  val fixedTaken:       Bool          = Bool()
   val ftqPtr:           FtqPtr        = new FtqPtr
   val instrEndOffset:   UInt          = UInt(FetchBlockInstOffsetWidth.W)
   val exceptionType:    UInt          = IBufferExceptionType()
@@ -103,7 +104,8 @@ class IBufEntry(implicit p: Parameters) extends IBufferBundle {
     pc             := fetch.pc(i)
     foldpc         := fetch.foldpc(i)
     pd             := fetch.pd(i)
-    predTaken      := fetch.instrEndOffset(i).taken
+    predTaken      := fetch.instrEndOffset(i).predTaken
+    fixedTaken     := fetch.instrEndOffset(i).fixedTaken
     ftqPtr         := fetch.ftqPtr
     instrEndOffset := fetch.instrEndOffset(i).offset
     exceptionType := IBufferExceptionType.cvtFromFetchExcpAndCrossPageAndRVCII(
@@ -125,6 +127,7 @@ class IBufEntry(implicit p: Parameters) extends IBufferBundle {
     result.foldpc           := foldpc
     result.pd               := pd
     result.predTaken        := predTaken
+    result.fixedTaken       := fixedTaken
     result.ftqPtr           := ftqPtr
     result.exceptionType    := exceptionType
     result.backendException := backendException
@@ -145,6 +148,7 @@ class IBufOutEntry(implicit p: Parameters) extends IBufferBundle {
   val foldpc:           UInt          = UInt(MemPredPCWidth.W)
   val pd:               PreDecodeInfo = new PreDecodeInfo
   val predTaken:        Bool          = Bool()
+  val fixedTaken:       Bool          = Bool()
   val ftqPtr:           FtqPtr        = new FtqPtr
   val exceptionType:    UInt          = IBufferExceptionType()
   val backendException: Bool          = Bool()
@@ -167,7 +171,8 @@ class IBufOutEntry(implicit p: Parameters) extends IBufferBundle {
     cf.backendException                              := backendException
     cf.trigger                                       := triggered
     cf.pd                                            := pd
-    cf.pred_taken                                    := predTaken
+    cf.fixedTaken                                    := fixedTaken
+    cf.predTaken                                     := predTaken
     cf.crossPageIPFFix                               := IBufferExceptionType.isCrossPage(exceptionType)
     cf.storeSetHit                                   := DontCare
     cf.waitForRobIdx                                 := DontCare
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index 65bd930a634..4cf99b81da2 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -662,8 +662,9 @@ class Ifu(implicit p: Parameters) extends IfuModule
   // Find last using PriorityMux
   io.toIBuffer.bits.isLastInFtqEntry := Reverse(PriorityEncoderOH(Reverse(io.toIBuffer.bits.enqEnable))).asBools
   io.toIBuffer.bits.instrEndOffset.zipWithIndex.foreach { case (a, i) =>
-    a.taken  := checkerOutStage1.fixedTwoFetchTaken(i) && !s4_reqIsUncache
-    a.offset := s4_alignCompactInfo.instrEndOffset(i)
+    a.predTaken  := s4_alignIsPredTaken(i) && !s4_reqIsUncache
+    a.fixedTaken := checkerOutStage1.fixedTwoFetchTaken(i) && !s4_reqIsUncache
+    a.offset     := s4_alignCompactInfo.instrEndOffset(i)
   }
   io.toIBuffer.bits.foldpc := s4_alignFoldPc
   // mark the exception only on first instruction
diff --git a/src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala b/src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala
index d7a95a53fc0..1d0ceafb9d3 100644
--- a/src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala
+++ b/src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala
@@ -250,7 +250,8 @@ class SimFrontendInlinedImp(outer: FrontendInlined) extends FrontendInlinedImpBa
     cfVec.bits.pd.isCall := fetchOut.preDecode(4)
     cfVec.bits.pd.isRet  := fetchOut.preDecode(5)
 
-    cfVec.bits.pred_taken := fetchOut.preDecode(6)
+    cfVec.bits.fixedTaken := fetchOut.preDecode(6)
+    cfVec.bits.predTaken  := fetchOut.preDecode(6)
 
     cfVec.bits.ftqPtr.value := fetchOut.preDecode(12, 7)
     cfVec.bits.ftqPtr.flag  := fetchOut.preDecode(13)
```
