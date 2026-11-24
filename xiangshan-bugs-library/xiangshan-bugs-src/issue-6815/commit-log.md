# Commit Log
- Issue: #6815
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6815
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6801
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6801
- Changed files: 42
- Additions: 521
- Deletions: 73

## Files
- `rocket-chip`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/BackendParams.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/archZicfilp.scala`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala`
- `src/main/scala/xiangshan/backend/decode/SpecZicfilp.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnit.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala`
- `src/main/scala/xiangshan/backend/fu/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/FuConfig.scala`
- `src/main/scala/xiangshan/backend/fu/FuncUnit.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/DretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala`
- `src/main/scala/xiangshan/backend/rename/CompressUnit.scala`
- `src/main/scala/xiangshan/backend/rob/ExceptionGen.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`
- `src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannels.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/DecodeStage.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/rocket-chip b/rocket-chip
index a2df1a42399..9b59a4b0ee0 160000
--- a/rocket-chip
+++ b/rocket-chip
@@ -1 +1 @@
-Subproject commit a2df1a42399cfe2b343eeb5293796268dc2bc211
+Subproject commit 9b59a4b0ee05e0cf4eb7f64dc683b3f06d277c72
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index e752f9cf8bf..10ed185c755 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -84,6 +84,12 @@ object RSFeedbackType {
   }
 }
 
+// Zicfilp
+class ZicfilpInfo(implicit p: Parameters) extends XSBundle {
+  val ZicfilpJalr = Bool()
+  val ZicfilpLPADValid = Bool()
+}
+
 // Dequeue DecodeWidth insts from Ibuffer
 class CtrlFlow(implicit p: Parameters) extends XSBundle {
   val instr = UInt(32.W)
@@ -222,6 +228,11 @@ class Redirect(implicit p: Parameters) extends FrontendRedirect {
   val debugIsCtrl = Bool()
   val debugIsMemVio = Bool()
 
+  // Zicfilp
+  val ZicfilpJalr = OptionWrapper(HasZicfilp, Bool())
+  val ZicfilpXRetValid = OptionWrapper(HasZicfilp, Bool())
+  val ZicfilpRetELP = OptionWrapper(HasZicfilp, Bool())
+
   def flushItself() = RedirectLevel.flushItself(level)
 
   def getPcOffset() = {
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 3d3f2d4f5b9..c5fc329f7bf 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -53,6 +53,7 @@ case class XSCoreParameters
   VLEN: Int = 128,
   ELEN: Int = 64,
   HSXLEN: Int = 64,
+  HasZicfilp: Boolean = false, // Zicfilp
   HasMptCheck: Boolean = false, //enable mpt
   HasBitmapCheck: Boolean = true,
   HasMExtension: Boolean = true,
@@ -667,6 +668,8 @@ trait HasXSParameter {
   def hartIDDmodeWidth = coreParams.hartIDDmodeWidth
   def enableResetMtvec = coreParams.enableResetMtvec
   val xLen = XLEN
+  // Zicfilp
+  def HasZicfilp = coreParams.HasZicfilp
   assert(!(HasMptCheck == true && HasBitmapCheck == true), "Conflicts: MPT and Bitmap can't be used together")
   def HasMptCheck = coreParams.HasMptCheck && !coreParams.HasBitmapCheck
   def HasBitmapCheck = coreParams.HasBitmapCheck
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 5b8ace0088c..2bb01fa3303 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -211,6 +211,9 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   ctrlBlock.io.fromCSR.toDecode := intRegion.io.csrToDecode.get
   ctrlBlock.io.fromCSR.traceCSR := intRegion.io.csrio.get.traceCSR
   ctrlBlock.io.fromCSR.instrAddrTransType := RegNext(intRegion.io.csrio.get.instrAddrTransType)
+  ctrlBlock.io.fromCSR.zicfilpEntryClear.foreach { sink =>
+    sink := intRegion.io.csrio.get.zicfilpEntryClear.get
+  }
   val wbDataPathToCtrlBlock = intRegion.io.wbDataPathToCtrlBlock.writeback ++
     fpRegion.out.toRob.writeback.flatMap(_.map(_.map(x => x.toWriteBackRobBundle))) ++
     vecRegion.out.toRob.writeback.flatMap(_.map(_.map(x => x.toWriteBackRobBundle))) ++
@@ -524,6 +527,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   csrin.l2FlushDone := RegNext(io.fromTop.l2FlushDone)
   csrin.reset_mtvec.foreach(_ := io.fromTop.reset_mtvec.get)
   csrin.trapInstInfo := ctrlBlock.io.toCSR.trapInstInfo
+  csrin.ZicfilpELP.foreach(_ := ctrlBlock.io.toCSR.ZicfilpELP.getOrElse(false.B))
   csrin.fromVecExcpMod.busy := vecExcpMod.o.status.busy
   csrin.criticalErrorState := backendCriticalError
 
diff --git a/src/main/scala/xiangshan/backend/BackendParams.scala b/src/main/scala/xiangshan/backend/BackendParams.scala
index 12ac0a5b7a5..d75d9c65e4e 100644
--- a/src/main/scala/xiangshan/backend/BackendParams.scala
+++ b/src/main/scala/xiangshan/backend/BackendParams.scala
@@ -138,6 +138,7 @@ case class BackendParams(
   def numExu = allSchdParams.map(_.numExu).sum
 
   def numException(implicit p: Parameters) = getWrite2RobSize(_.needExceptionGen)
+  def numException(hasZicfilp: Boolean)(implicit p: Parameters) = getWrite2RobSize(_.needExceptionGen(hasZicfilp))
 
   def numRedirect = 1 // only for ahead info to frontend
 
@@ -565,6 +566,10 @@ case class BackendParams(
 
   def exceptionOut = allExuParams.flatMap(_.exceptionOut).distinct.sorted
   def getExceptionOutList = allRealExuParams.map(_.exceptionOut).filter(_.nonEmpty)
+  // Zicfilp
+  def exceptionOut(hasZicfilp: Boolean) = allExuParams.flatMap(_.effectiveExceptionOut(hasZicfilp)).distinct.sorted
+  def getExceptionOutList(hasZicfilp: Boolean) =
+    allRealExuParams.map(_.effectiveExceptionOut(hasZicfilp)).filter(_.nonEmpty)
 
   private def isContinuous(portIndices: Seq[Int]): Boolean = {
     val portIndicesSet = portIndices.toSet
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 45145526b8e..d84da91e9db 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -124,6 +124,8 @@ object Bundles {
     val vtype = new VType()
     val specvtype = new VType()
     val instr = UInt(32.W)
+    // Zicfilp
+    val ZicfilpPCAligned = OptionWrapper(HasZicfilp, Bool())
     val debug = OptionWrapper(backendParams.debugEn, new DecodeInUopDebug())
 
     def connectCtrlFlow(source: CtrlFlow): Unit = {
@@ -132,6 +134,7 @@ object Bundles {
       this.isFetchMalAddr := source.backendException
       this.vtype            := source.vtype
       this.specvtype        := source.specvtype
+      this.ZicfilpPCAligned.foreach(_ := source.pc(1, 0) === 0.U)
       this.debug.foreach(_.pc := source.pc)
       this.debug.foreach(_.debug_seqNum := source.debug_seqNum)
     }
@@ -144,7 +147,7 @@ object Bundles {
   // DecodeInUop --[Decode]--> DecodeOutUop
   class DecodeOutUop(implicit p: Parameters) extends XSBundle {
     val foldpc = UInt(MemPredPCWidth.W) // for mdp
-    val exceptionVec = ExceptSparseVec(ExceptionNO.decodeSet)
+    val exceptionVec = ExceptSparseVec(ExceptionNO.decodeSet(HasZicfilp))
     val satpFlushFirstFetchFault = Bool()
     val isFetchMalAddr = Bool()
     val trigger = TriggerAction()
@@ -197,6 +200,9 @@ object Bundles {
     val isJr = Bool()
     val numWB = NumWB() // rob need this
     val latency = Latency()
+    // Zicfilp
+    val ZicfilpInfos = OptionWrapper(HasZicfilp, new ZicfilpInfo)
+    val ZicfilpLPAD = OptionWrapper(HasZicfilp, Bool())
 
     val debug = OptionWrapper(backendParams.debugEn, new DecodeOutUopDebug())
 
@@ -237,7 +243,7 @@ object Bundles {
 
   class RenameOutUop(implicit p: Parameters) extends XSBundle {
     def numSrc = backendParams.numSrc
-    val exceptionVec = ExceptSparseVec(ExceptionNO.decodeSet)
+    val exceptionVec = ExceptSparseVec(ExceptionNO.decodeSet(HasZicfilp))
     val satpFlushFirstFetchFault = Bool()
     val isFetchMalAddr = Bool()
     val trigger = TriggerAction()
@@ -319,6 +325,9 @@ object Bundles {
     val lsqIdxEnd = new LSIdx
     val hasException = Bool()
     val lastIsRVC = Bool() // store isrvc before change in rename
+    // Zicfilp
+    val ZicfilpInfos = OptionWrapper(HasZicfilp, new ZicfilpInfo)
+    val ZicfilpLPAD = OptionWrapper(HasZicfilp, Bool())
     val debug = OptionWrapper(backendParams.debugEn, new RenameOutUopDebug())
     def isLUI: Bool = this.fuType === FuType.alu.U && (this.selImm === SelImm.IMM_U || this.selImm === SelImm.IMM_LUI32)
     def needWriteRf: Bool = rfWen || fpWen || vecWen || v0Wen || vlWen
@@ -439,6 +448,8 @@ object Bundles {
     val regCacheIdx = Vec(backendParams.numIntRegSrc, UInt(RegCacheIdxWidth.W))
     val lqIdx = new LqPtr
     val sqIdx = new SqPtr
+    // Zicfilp
+    val ZicfilpInfos = OptionWrapper(HasZicfilp, new ZicfilpInfo)
     val debug = OptionWrapper(backendParams.debugEn, new IssueQueueInDebug)
   }
   class IssueQueueInDebug(implicit p: Parameters) extends XSBundle {
@@ -508,6 +519,8 @@ object Bundles {
     val sqIdx             = Option.when(params.needSqIdx)(new SqPtr) // load unit need sqIdx
     // cas ctrl
     val isDropAmocasSta = Bool()
+    // Zicfilp
+    val ZicfilpInfos = OptionWrapper(HasZicfilp && params.JmpCnt > 0, new ZicfilpInfo)
     val debug = OptionWrapper(backendParams.debugEn, new IssueQueueInDebug)
   }
 
@@ -539,6 +552,8 @@ object Bundles {
     // from dispatch
     val lqIdx = Option.when(params.needLqIdx)(new LqPtr)
     val sqIdx = Option.when(params.needSqIdx)(new SqPtr) // load unit need sqIdx
+    // Zicfilp
+    val ZicfilpInfos = OptionWrapper(HasZicfilp && params.JmpCnt > 0, new ZicfilpInfo)
   }
 
   class IssueQueueDeqOg1Payload(val params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
@@ -571,6 +586,8 @@ object Bundles {
     // from dispatch
     val lqIdx = Option.when(params.issueBlockParam.needLqIdx)(new LqPtr)
     val sqIdx = Option.when(params.issueBlockParam.needSqIdx)(new SqPtr) // load unit need sqIdx
+    // Zicfilp
+    val ZicfilpInfos = OptionWrapper(HasZicfilp && params.hasJmpFu, new ZicfilpInfo)
   }
 
   class IssueQueuePayload(val params: IssueBlockParams)(implicit p: Parameters) extends XSBundle {
@@ -711,7 +728,6 @@ object Bundles {
     val debug_sim_trig  = Option.when(backendParams.debugEn)(Bool())
 
     val numLsElem       = NumLsElem()
-
     def getDebugFuType: UInt = debug_fuType.getOrElse(fuType)
 
     def isLUI: Bool = this.fuType === FuType.alu.U && (this.selImm === SelImm.IMM_U || this.selImm === SelImm.IMM_LUI32)
@@ -986,6 +1002,8 @@ object Bundles {
     val ssid           = Option.when(iqParams.isLdAddrIQ || iqParams.isStAddrIQ)(UInt(SSIDWidth.W))
     val lqIdx          = Option.when(iqParams.needLqIdx)(new LqPtr)
     val sqIdx          = Option.when(iqParams.needSqIdx)(new SqPtr)
+    // Zicfilp
+    val ZicfilpInfos   = OptionWrapper(HasZicfilp && exuParams.hasJmpFu, new ZicfilpInfo)
 
     val src = Vec(exuParams.numRegSrc, UInt(exuParams.srcDataBitsMax.W))
     val v0  = Option.when(exuParams.readV0Rf)(V0())
@@ -1082,6 +1100,9 @@ object Bundles {
 
       this.lqIdx.foreach(_ := source.lqIdx.get)
       this.sqIdx.foreach(_ := source.sqIdx.get)
+
+      // Zicfilp metadata is carried by the issue-queue dequeue payload.
+      this.ZicfilpInfos.foreach(_ := source.ZicfilpInfos.get)
     }
   }
 
@@ -1197,6 +1218,8 @@ object Bundles {
     val ftqIdx        = if (params.needFtqPtr)    Some(new FtqPtr)                    else None
     val ftqOffset     = if (params.needFtqPtrOffset) Some(UInt(FetchBlockInstOffsetWidth.W))  else None
     val predictInfo   = if (params.needPdInfo)  Some(new PredictInfo) else None
+    // Zicfilp
+    val ZicfilpInfos   = OptionWrapper(HasZicfilp && params.hasJmpFu, new ZicfilpInfo)
     val loadWaitBit    = OptionWrapper(params.hasLoadExu, Bool())
     val waitSqIdx      = OptionWrapper(params.hasLoadExu, new SqPtr) // store set predicted previous store sqIdx
     val storeSetHit    = OptionWrapper(params.hasLoadExu || params.hasStoreAddrExu, Bool()) // inst has been allocated an store set
@@ -1313,6 +1336,8 @@ object Bundles {
     val ftqIdx         = Option.when(params.needFtqPtr)(new FtqPtr)
     val ftqOffset      = Option.when(params.needFtqPtrOffset)(UInt(FetchBlockInstOffsetWidth.W))
     val predictInfo    = Option.when(params.needPdInfo)(new PredictInfo)
+    // Zicfilp
+    val ZicfilpInfos   = OptionWrapper(HasZicfilp && params.hasJmpFu, new ZicfilpInfo)
     val dataSources    = Vec(params.numRegSrc, DataSource())
     val exuSources     = Option.when(params.isIQWakeUpSink)(Vec(params.numRegSrc, ExuSource(params)))
     val loadDependency = Option.when(params.needLoadDependency)(Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
@@ -1400,7 +1425,7 @@ object Bundles {
     val fflags       = if (params.writeFflags)  Some(UInt(5.W))               else None
     val fflagsWen    = if (params.writeFflags)  Some(Bool())                  else None
     val vxsat        = if (params.writeVxsat)   Some(Bool())                  else None
-    val exceptionVec = ExceptSparseVec(params.exceptionOut)
+    val exceptionVec = ExceptSparseVec(params.effectiveExceptionOut(HasZicfilp))
     val flushPipe    = if (params.flushPipe)    Some(Bool())                  else None
     val replay       = if (params.replayInst)   Some(Bool())                  else None
     val lqIdx        = if (params.hasLoadFu)    Some(new LqPtr())             else None
@@ -1423,7 +1448,7 @@ object Bundles {
     val fflags       = Option.when(params.writeFflags)(UInt(5.W))
     val fflagsWen    = Option.when(params.writeFflags)(Bool())
     val vxsat        = Option.when(params.writeVxsat)(Bool())
-    val exceptionVec = ExceptSparseVec(params.exceptionOut)
+    val exceptionVec = ExceptSparseVec(params.effectiveExceptionOut(p(XSCoreParamsKey).HasZicfilp))
     val flushPipe    = Option.when(params.flushPipe)(Bool())
     val satpFlush    = Option.when(params.satpFlush)(Bool())
     val trigger      = Option.when(params.trigger)(TriggerAction())
@@ -1710,7 +1735,7 @@ object Bundles {
     val fflags        = Option.when(params.writeFflags)(UInt(5.W))
     val fflagsWen     = Option.when(params.writeFflags)(Bool())
     val vxsat         = Option.when(params.writeVxsat)(Bool())
-    val exceptionVec  = ExceptSparseVec(params.exceptionOut)
+    val exceptionVec  = ExceptSparseVec(params.effectiveExceptionOut(p(XSCoreParamsKey).HasZicfilp))
     val lqIdx         = Option.when(params.hasLoadFu)(new LqPtr())
     val sqIdx         = Option.when(params.hasStoreAddrFu || params.hasStdFu)(new SqPtr())
     val trigger       = Option.when(params.trigger)(TriggerAction())
@@ -1736,6 +1761,7 @@ object Bundles {
 
   class ExceptionInfo(implicit p: Parameters) extends XSBundle {
     val pc = UInt((VAddrData().dataWidth + 1).W)
+    val ZicfilpELP = OptionWrapper(HasZicfilp, Bool())
     val instr = UInt(32.W)
     val commitType = CommitType()
     val isStore = Bool()
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index cb999f57a27..c373feb8f50 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -131,6 +131,10 @@ class CtrlBlockImp(
   s1_robFlushRedirect.valid := GatedValidRegNext(s0_robFlushRedirect.valid, false.B)
   s1_robFlushRedirect.bits := RegEnable(s0_robFlushRedirect.bits, s0_robFlushRedirect.valid)
   s1_robFlushPcInfo := RegEnable(s0_robFlushPcInfo, s0_robFlushRedirect.valid)
+  // Zicfilp: ROB redirects are traps, interrupts, replays, or flush-pipe events, not JALR/xRET redirects.
+  s1_robFlushRedirect.bits.ZicfilpJalr.foreach(_ := false.B)
+  s1_robFlushRedirect.bits.ZicfilpXRetValid.foreach(_ := false.B)
+  s1_robFlushRedirect.bits.ZicfilpRetELP.foreach(_ := false.B)
 
   println(s"[CtrlBlock] pcMem read port for \"robFlush\": ${pcMemRdIndexes("robFlush").head}.")
   pcMem.io.ren.get(pcMemRdIndexes("robFlush").head) := s0_robFlushRedirect.valid
@@ -169,8 +173,9 @@ class CtrlBlockImp(
     x.valid := GatedValidRegNext(io.fromWB.wbData(i).valid)
     x.bits := delayedNotFlushedWriteBack(i).bits
   }
-  val delayedNotFlushedWriteBackNeedFlush = Wire(Vec(params.getWrite2RobSize(_.needExceptionGen), Bool()))
-  delayedNotFlushedWriteBackNeedFlush := delayedNotFlushedWriteBack.filter(_.bits.params.needExceptionGen).map{ x =>
+  // Match the ROB's exception writeback ports, including landing-pad checks.
+  val delayedNotFlushedWriteBackNeedFlush = Wire(Vec(params.getWrite2RobSize(_.needExceptionGen(HasZicfilp)), Bool()))
+  delayedNotFlushedWriteBackNeedFlush := delayedNotFlushedWriteBack.filter(_.bits.params.needExceptionGen(HasZicfilp)).map{ x =>
     x.bits.exceptionVec.orR || x.bits.flushPipe.getOrElse(false.B) || x.bits.replay.getOrElse(false.B) ||
       (if (x.bits.trigger.nonEmpty) TriggerAction.isDmode(x.bits.trigger.get) else false.B)
   }
@@ -233,6 +238,10 @@ class CtrlBlockImp(
   loadReplay.bits.isFromLoad := true.B
   loadReplay.bits.debugIsCtrl := false.B
   loadReplay.bits.debugIsMemVio := true.B
+  // Zicfilp: a memory replay does not change ELP by itself.
+  loadReplay.bits.ZicfilpJalr.foreach(_ := false.B)
+  loadReplay.bits.ZicfilpXRetValid.foreach(_ := false.B)
+  loadReplay.bits.ZicfilpRetELP.foreach(_ := false.B)
 
   println(s"[CtrlBlock] pcMem read port for \"redirect\": ${pcMemRdIndexes("redirect").head}.")
   pcMem.io.ren.get(pcMemRdIndexes("redirect").head) := memViolation.valid
@@ -440,6 +449,24 @@ class CtrlBlockImp(
     crc.bits.ftqPtr := RegEnable(blk.bits.ftqIdx.get, vld)
     crc.bits.rasAction := RegEnable(Itype.isPush(blk.bits.tracePipe.itype) ## Itype.isPop(blk.bits.tracePipe.itype), vld)
   }
+
+  // Zicfilp architectural ELP follows only instructions that actually commit.
+  val archZicfilp = Option.when(HasZicfilp)(Module(new ArchZicfilp))
+  archZicfilp.foreach { arch =>
+    for (i <- 0 until CommitWidth) {
+      arch.io.commitValid(i) := rob.io.commits.isCommit && rob.io.commits.commitValid(i)
+      arch.io.commitJalr(i) := rob.io.commits.info(i).ZicfilpJalr.get
+      arch.io.commitLPAD(i) := rob.io.commits.info(i).ZicfilpLPAD.get
+    }
+    arch.io.enable := io.fromCSR.toDecode.enableZicfilp.get
+    arch.io.trap := rob.io.exception.valid
+    arch.io.entryClear := io.fromCSR.zicfilpEntryClear.get
+    arch.io.xret.valid := s1_s3_redirect.valid && s1_s3_redirect.bits.ZicfilpXRetValid.get
+    arch.io.xret.bits := s1_s3_redirect.bits.ZicfilpRetELP.get
+    io.toCSR.ZicfilpELP.get := arch.io.archELP
+    rob.io.ZicfilpELP.get := arch.io.archELP
+  }
+
   // Be careful here:
   // T0: rob.io.flushOut, s0_robFlushRedirect
   // T1: s1_robFlushRedirect, rob.io.exception.valid
@@ -484,6 +511,22 @@ class CtrlBlockImp(
   io.frontend.toIBuf.vsetvlVType := io.toDecode.vsetvlVType
   io.frontend.toIBuf.commitVType := rob.io.toDecode.commitVType
 
+  // Zicfilp
+  if (HasZicfilp) {
+    val decodeRedirectCandidates = Seq(s1_s3_redirect, s2_s4_redirect)
+    val decodeOldestRedirectOH = Redirect.selectOldestRedirect(decodeRedirectCandidates)
+    val decodeOldestRedirect = Mux1H(decodeOldestRedirectOH, decodeRedirectCandidates)
+    val decodeHasNewRedirect = decodeRedirectCandidates.map(_.valid).reduce(_ || _)
+    val enableZicfilp = io.fromCSR.toDecode.enableZicfilp.get
+
+    decode.in.ZicfilpRedirect.get.valid := decodeHasNewRedirect
+    decode.in.ZicfilpRedirect.get.bits := Mux(
+      decodeOldestRedirect.bits.ZicfilpXRetValid.get,
+      decodeOldestRedirect.bits.ZicfilpRetELP.get,
+      decodeOldestRedirect.bits.ZicfilpJalr.get && enableZicfilp
+    )
+  }
+
   // add decode Buf for in.ready better timing
   /**
    * Decode buffer: when decode.in cannot accept all insts, use this buffer to temporarily store insts that cannot
@@ -943,6 +986,8 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
     val toDecode = Input(new CSRToDecode)
     val traceCSR = Input(new TraceCSR)
     val instrAddrTransType = Input(new AddrTransType)
+    // NewCSR entry pulse for Debug/RNMI/MN ELP clearing.
+    val zicfilpEntryClear = OptionWrapper(HasZicfilp, Input(Bool()))
   }
   val toIssueBlock = new Bundle {
     val flush = ValidIO(new Redirect)
@@ -994,6 +1039,8 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
   }
   val toCSR = new Bundle {
     val trapInstInfo = Output(ValidIO(new TrapInstInfo))
+    // Zicfilp
+    val ZicfilpELP = OptionWrapper(HasZicfilp, Output(Bool()))
   }
   val fromWB = new Bundle {
     val wbData = Flipped(MixedVec(params.genWrite2RobBundles))
diff --git a/src/main/scala/xiangshan/backend/archZicfilp.scala b/src/main/scala/xiangshan/backend/archZicfilp.scala
new file mode 100644
index 00000000000..0ac4cf02cc9
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/archZicfilp.scala
@@ -0,0 +1,43 @@
+package xiangshan.backend
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import xiangshan._
+
+class ArchZicfilpIO(implicit p: Parameters) extends XSBundle {
+  val commitValid = Input(Vec(CommitWidth, Bool()))
+  val commitJalr = Input(Vec(CommitWidth, Bool()))
+  val commitLPAD = Input(Vec(CommitWidth, Bool()))
+  val enable = Input(Bool())
+  val trap = Input(Bool())
+  // Entry from NewCSR for Debug/RNMI paths that do not rely solely on ROB exception.valid.
+  val entryClear = Input(Bool())
+  val xret = Flipped(ValidIO(Bool()))
+  val archELP = Output(Bool())
+}
+
+class ArchZicfilp(implicit p: Parameters) extends XSModule {
+  val io = IO(new ArchZicfilpIO)
+
+  val archELP = RegInit(false.B)
+  val hasCommit = io.commitValid.asUInt.orR
+  val commitELP = io.commitValid.zip(io.commitJalr).zip(io.commitLPAD).foldLeft(archELP) {
+    case (elp, ((valid, jalr), lpad)) =>
+      Mux(io.enable && valid && jalr, true.B, Mux(io.enable && valid && lpad, false.B, elp))
+  }
+
+  // Normal ROB traps and CSR-originated entry pulses both start the handler with ELP=0.
+  // The pre-entry value is saved separately in the corresponding CSR field.
+  when(io.trap || io.entryClear) {
+    archELP := false.B
+  }.elsewhen(io.xret.valid) {
+    archELP := io.xret.bits
+  }.elsewhen(!io.enable) {
+    archELP := false.B
+  }.elsewhen(hasCommit) {
+    archELP := commitELP
+  }
+
+  io.archELP := archELP && io.enable
+}
diff --git a/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala b/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
index 9aba5eabc16..fe3c2648fed 100644
--- a/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
+++ b/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
@@ -222,7 +222,8 @@ class BypassNetwork()(implicit p: Parameters, params: BackendParams) extends XSM
       val thisPcOffset = exuInput.bits.getPcOffset()
       val nextPcOffset = exuInput.bits.getNextPcOffset()
       val isJR = FuType.isJump(fuType) && JumpOpType.jumpUopisjr(fuOpType)
-      val immBJU = imm + Mux(isJR, 0.U, SignExt(thisPcOffset, imm.getWidth))
+      val isLPAD = exuInput.bits.ZicfilpInfos.map(_.ZicfilpLPADValid).getOrElse(false.B)
+      val immBJU = imm + Mux(isJR || isLPAD, 0.U, SignExt(thisPcOffset, imm.getWidth))
       val immCsrFence = fromDPs(exuIdx).bits.imm.get
       exuInput.bits.imm := Mux((FuType.isCsr(fuType) || FuType.isFence(fuType)) && exuParm.hasCSR.B, immCsrFence, immBJU)
       exuInput.bits.nextPcOffset.foreach(_ := nextPcOffset)
diff --git a/src/main/scala/xiangshan/backend/decode/SpecZicfilp.scala b/src/main/scala/xiangshan/backend/decode/SpecZicfilp.scala
new file mode 100644
index 00000000000..3ed693c5952
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/decode/SpecZicfilp.scala
@@ -0,0 +1,47 @@
+package xiangshan.backend.decode
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import xiangshan.{XSBundle, XSModule}
+
+// Zicfilp
+class SpecZicfilpIO(mopWidth: Int)(implicit p: Parameters) extends XSBundle {
+  val enable = Input(Bool())
+  val redirect = Flipped(ValidIO(Bool()))
+  val valid = Input(Vec(mopWidth, Bool()))
+  val fire = Input(Vec(mopWidth, Bool()))
+  val isJalr = Input(Vec(mopWidth, Bool()))
+  val isLPAD = Input(Vec(mopWidth, Bool()))
+  val elp = Output(Vec(mopWidth, Bool()))
+  val exception = Output(Vec(mopWidth, Bool()))
+  val lpadValid = Output(Vec(mopWidth, Bool()))
+}
+
+class SpecZicfilp(mopWidth: Int)(implicit p: Parameters) extends XSModule {
+  require(mopWidth > 0)
+  val io = IO(new SpecZicfilpIO(mopWidth))
+
+  val speculativeELP = RegInit(false.B)
+  val outputELP = Wire(Vec(mopWidth + 1, Bool()))
+  val nextELP = Wire(Vec(mopWidth + 1, Bool()))
+  outputELP.head := speculativeELP
+  nextELP.head := speculativeELP
+
+  for (i <- 0 until mopWidth) {
+    val checkLandingPad = io.valid(i) && outputELP(i)
+    io.elp(i) := outputELP(i)
+    io.exception(i) := io.enable && checkLandingPad && !io.isLPAD(i)
+    io.lpadValid(i) := io.enable && checkLandingPad && io.isLPAD(i)
+    outputELP(i + 1) := Mux(io.valid(i), io.isJalr(i), outputELP(i))
+    nextELP(i + 1) := Mux(io.fire(i), io.isJalr(i), nextELP(i))
+  }
+
+  when(io.redirect.valid) {
+    speculativeELP := io.redirect.bits
+  }.elsewhen(!io.enable) {
+    speculativeELP := false.B
+  }.otherwise {
+    speculativeELP := nextELP.last
+  }
+}
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
index 76101a6f935..c842bdd446f 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
@@ -199,6 +199,8 @@ class ExeUnitImp(implicit p: Parameters, val exuParams: ExeUnitParams) extends X
       sink.bits.ctrl.vtype       .foreach(x => x := source.bits.ctrl.vtype.get)
       sink.bits.ctrl.frm         .foreach(x => x := source.bits.ctrl.frm.get)
       sink.bits.ctrl.oldVType    .foreach(x => x := source.bits.ctrl.oldVType.get)
+      // Zicfilp
+      sink.bits.ctrl.ZicfilpInfos.foreach(x => x := source.bits.ctrl.ZicfilpInfos.get)
       sink.bits.perfDebugInfo    .foreach(_ := source.bits.perfDebugInfo.get)
       sink.bits.debug_seqNum     .foreach(_ := source.bits.debug_seqNum.get)
   }
@@ -234,6 +236,8 @@ class ExeUnitImp(implicit p: Parameters, val exuParams: ExeUnitParams) extends X
       sink.vtype.foreach(x => x := source.ctrl.vtype.get)
       sink.oldVType.foreach(x => x := source.ctrl.oldVType.get)
       sink.frm.foreach(_ := source.ctrl.frm.get)
+      // Zicfilp
+      sink.ZicfilpInfos.foreach(x => x := source.ctrl.ZicfilpInfos.get)
       val sinkData = fu.io.in.bits.dataPipe.get(i)
       val sourceData = inPipe._1(i)
       sinkData.src.zip(sourceData.data.src).foreach { case (fuSrc, exuSrc) => fuSrc := exuSrc }
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
index 419b504e0b4..67f1844c30d 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
@@ -89,12 +89,18 @@ case class ExeUnitParams(
   val needIsRVC: Boolean = fuConfigs.map(_.hasIsRVC).reduce(_ || _)
   val hasRasAction: Boolean = fuConfigs.map(_.hasRasAction).reduce(_ || _)
   val exceptionOut: Seq[Int] = fuConfigs.map(_.exceptionOut).reduce(_ ++ _).distinct.sorted
+  // Zicfilp
+  def effectiveExceptionOut(hasZicfilp: Boolean): Seq[Int] =
+    fuConfigs.flatMap(_.effectiveExceptionOut(hasZicfilp)).distinct.sorted
   val hasLoadError: Boolean = fuConfigs.map(_.hasLoadError).reduce(_ || _)
   val flushPipe: Boolean = fuConfigs.map(_.flushPipe).reduce(_ || _)
   val satpFlush: Boolean = fuConfigs.map(_.isCsr).reduce(_ || _)
   val replayInst: Boolean = fuConfigs.map(_.replayInst).reduce(_ || _)
   val trigger: Boolean = fuConfigs.map(_.trigger).reduce(_ || _)
   val needExceptionGen: Boolean = exceptionOut.nonEmpty || flushPipe || replayInst || trigger
+  // Zicfilp
+  def needExceptionGen(hasZicfilp: Boolean): Boolean =
+    effectiveExceptionOut(hasZicfilp).nonEmpty || flushPipe || replayInst || trigger
   val needPc: Boolean = fuConfigs.map(_.needPc).reduce(_ || _)
   val aluBjuNeedPc: Boolean = fuConfigs.map(_.aluBjuNeedPc).reduce(_ || _)
   def needFtqPtr: Boolean = this.needPc || this.replayInst || this.hasStoreAddrFu || this.hasCSR
diff --git a/src/main/scala/xiangshan/backend/fu/CSR.scala b/src/main/scala/xiangshan/backend/fu/CSR.scala
index dd0dbc0688e..f97f987855d 100644
--- a/src/main/scala/xiangshan/backend/fu/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/CSR.scala
@@ -111,6 +111,8 @@ class CSRFileIO(implicit p: Parameters) extends XSBundle with HasSoCParameter {
   // Debug Mode
   // val singleStep = Output(Bool())
   val debugMode = Output(Bool())
+  // One-cycle Debug/RNMI/MN entry pulse for architectural LPAD state clearing.
+  val zicfilpEntryClear = OptionWrapper(HasZicfilp, Output(Bool()))
   // Custom microarchiture ctrl signal
   val customCtrl = Output(new CustomCSRCtrlIO)
   // instruction fetch address translation type
diff --git a/src/main/scala/xiangshan/backend/fu/FuConfig.scala b/src/main/scala/xiangshan/backend/fu/FuConfig.scala
index 89757ef88e4..a6929554d99 100644
--- a/src/main/scala/xiangshan/backend/fu/FuConfig.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuConfig.scala
@@ -195,6 +195,11 @@ case class FuConfig (
   def isJmp: Boolean = fuType == FuType.jmp
 
   def isLink: Boolean = fuType == FuType.link
+  // Zicfilp
+  def effectiveExceptionOut(hasZicfilp: Boolean): Seq[Int] = {
+    val zicfilpExceptions = if (hasZicfilp && isJmp) Seq(softwareCheck) else Seq.empty
+    (exceptionOut ++ zicfilpExceptions).distinct.sorted
+  }
 
   def isFence: Boolean = fuType == FuType.fence
 
@@ -240,7 +245,7 @@ object FuConfig {
       Seq(IntData()), // jal -> Seq(ja, link)
     ),
     piped = true,
-    immType = Set(Imm_I(), Imm_J()),
+    immType = Set(Imm_I(), Imm_J(), Imm_U()),
   )
 
   val LinkCfg: FuConfig = FuConfig (
diff --git a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
index b7733424cc6..fd02f3c5f00 100644
--- a/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuncUnit.scala
@@ -73,6 +73,8 @@ class FuncUnitCtrlInput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle
   val lastUop     = OptionWrapper(cfg.needVecCtrl, Bool())
   val vtype       = OptionWrapper(cfg.needVecCtrl, VType())
   val oldVType    = Option.when(cfg.writeVType)(VType())
+  // Zicfilp
+  val ZicfilpInfos = OptionWrapper(HasZicfilp && cfg.isJmp, new ZicfilpInfo)
 }
 
 class FuncUnitCtrlOutput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle {
@@ -85,7 +87,7 @@ class FuncUnitCtrlOutput(cfg: FuConfig)(implicit p: Parameters) extends XSBundle
   val vecWen        = OptionWrapper(cfg.needVecWen, Bool())
   val v0Wen         = OptionWrapper(cfg.needV0Wen, Bool())
   val vlWen         = OptionWrapper(cfg.needVlWen, Bool())
-  val exceptionVec  = ExceptSparseVec(cfg.exceptionOut)
+  val exceptionVec  = ExceptSparseVec(cfg.effectiveExceptionOut(HasZicfilp))
   val flushPipe     = OptionWrapper(cfg.flushPipe,  Bool())
   val satpFlush     = OptionWrapper(cfg.isCsr,      Bool())
   val replay        = OptionWrapper(cfg.replayInst, Bool())
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index 3da0999ed7c..7c6895d69f6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -129,6 +129,7 @@ class TrapEntryEventInput(implicit val p: Parameters) extends Bundle with HasXSP
   val trapIsForVSnonLeafPTE = Input(Bool())
   val hasDTExcp = Input(Bool())
   val satpFlushFirstFetchFault = Input(Bool())
+  val ZicfilpELP = Option.when(HasZicfilp)(Input(Bool()))
 
   // always current privilege
   val iMode = Input(new PrivState())
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/DretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/DretEvent.scala
index 31ecf862f0e..7711713fdd0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/DretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/DretEvent.scala
@@ -10,7 +10,7 @@ import xiangshan.AddrTransType
 
 
 class DretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val dcsr = ValidIO((new DcsrBundle).addInEvent(_.V, _.PRV))
+  val dcsr = ValidIO((new DcsrBundle).addInEvent(_.V, _.PRV, _.PELP))
   val mstatus = ValidIO((new MstatusBundle).addInEvent(_.MPRV, _.MDT, _.SDT))
   val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val debugMode = ValidIO(Bool())
@@ -61,6 +61,11 @@ class DretEventModule(implicit p: Parameters) extends Module with CSREventBase {
 
   out.privState.bits.PRVM     := in.dcsr.PRV.asUInt
   out.privState.bits.V        := Mux(in.dcsr.PRV === PrivMode.M, VirtMode.Off.asUInt, in.dcsr.V.asUInt)
+  out.dcsr.bits.V             := in.dcsr.V
+  out.dcsr.bits.PRV           := in.dcsr.PRV
+  // Debug Spec 1.0 §4.8：恢复 ELP 后消费 PELP。NewCSR 在本次返回事务中
+  // 先用旧 PELP 计算并保存 retELP，因此该字段清零不会丢失正常 DRET 的恢复值。
+  out.dcsr.bits.PELP          := 0.U
   out.mstatus.bits.MPRV       := Mux(!out.privState.bits.isModeM, 0.U, in.mstatus.MPRV.asUInt)
   out.mstatus.bits.MDT        := Mux(!out.privState.bits.isModeM, 0.U, in.mstatus.MDT.asBool)
   out.mstatus.bits.SDT        := Mux(out.privState.bits.isVirtual || out.privState.bits.isModeHU, 0.U, in.mstatus.SDT.asBool)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
index c09aae03dd3..6b10f1c9878 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
@@ -12,7 +12,7 @@ import xiangshan.AddrTransType
 
 
 class MNretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.NMIE))
+  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.NMIE, _.MNPELP))
   val mstatus   = ValidIO((new MstatusBundle).addInEvent(_.MPRV, _.MDT, _.SDT))
   val vsstatus  = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val targetPc  = ValidIO(new TargetPCBundle)
@@ -67,6 +67,7 @@ class MNretEventModule(implicit p: Parameters) extends Module with CSREventBase
 
   out.privState.bits          := outPrivState
   out.mnstatus.bits.NMIE      := 1.U
+  out.mnstatus.bits.MNPELP    := 0.U
   out.mstatus.bits.MPRV       := Mux(in.mnstatus.MNPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
   // clear MDT when mnret to below M
   out.mstatus.bits.MDT        := Mux(mnretToM, in.mstatus.MDT.asBool, 0.U)
@@ -84,4 +85,4 @@ trait MNretEventSinkBundle extends EventSinkBundle { self: CSRModule[_ <: CSRBun
   addUpdateBundleInCSREnumType(retFromMN.getBundleByName(self.modName.toLowerCase()))
 
   reconnectReg()
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
index 95920daf038..a695dce04fe 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
@@ -12,7 +12,7 @@ import xiangshan.AddrTransType
 
 
 class MretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val mstatus  = ValidIO((new MstatusBundle).addInEvent(_.MPP, _.MPV, _.MIE, _.MPIE, _.MPRV, _.MDT, _.SDT))
+  val mstatus  = ValidIO((new MstatusBundle).addInEvent(_.MPP, _.MPV, _.MIE, _.MPIE, _.MPRV, _.MDT, _.SDT, _.MPELP))
   val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val targetPc = ValidIO(new TargetPCBundle)
 }
@@ -66,6 +66,7 @@ class MretEventModule(implicit p: Parameters) extends Module with CSREventBase {
   out.mstatus.bits.MPV        := VirtMode.Off.asUInt
   out.mstatus.bits.MIE        := in.mstatus.MPIE
   out.mstatus.bits.MPIE       := 1.U
+  out.mstatus.bits.MPELP      := 0.U
   out.mstatus.bits.MPRV       := Mux(in.mstatus.MPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
   // clear MDT when return mret always execute in M mode
   out.mstatus.bits.MDT    := 0.U
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala
index 415c30187c9..af5faa154d1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/SretEvent.scala
@@ -13,9 +13,9 @@ import xiangshan.AddrTransType
 
 class SretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
   // Todo: write sstatus instead of mstatus
-  val mstatus = ValidIO((new MstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP, _.MPRV, _.MDT, _.SDT))
+  val mstatus = ValidIO((new MstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP, _.MPRV, _.MDT, _.SDT, _.SPELP))
   val hstatus = ValidIO((new HstatusBundle).addInEvent(_.SPV))
-  val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP))
+  val vsstatus = ValidIO((new SstatusBundle).addInEvent(_.SIE, _.SPIE, _.SPP, _.SPELP))
   val targetPc = ValidIO(new TargetPCBundle)
 }
 
@@ -100,6 +100,7 @@ class SretEventModule(implicit p: Parameters) extends Module with CSREventBase {
   out.mstatus.bits.SPP        := PrivMode.U.asUInt(0, 0) // SPP is not PrivMode enum type, so asUInt and shrink the width
   out.mstatus.bits.SIE        := in.mstatus.SPIE
   out.mstatus.bits.SPIE       := 1.U
+  out.mstatus.bits.SPELP      := 0.U
   out.mstatus.bits.MPRV       := 0.U // sret will always leave M mode
   out.mstatus.bits.MDT        := Mux(sretInM, 0.U, in.mstatus.MDT.asBool) // when execute return in M mode, set MDT 0
   out.mstatus.bits.SDT        := MuxCase(in.mstatus.SDT.asBool, Seq(
@@ -113,6 +114,7 @@ class SretEventModule(implicit p: Parameters) extends Module with CSREventBase {
   out.vsstatus.bits.SPP       := PrivMode.U.asUInt(0, 0) // SPP is not PrivMode enum type, so asUInt and shrink the width
   out.vsstatus.bits.SIE       := in.vsstatus.SPIE
   out.vsstatus.bits.SPIE      := 1.U
+  out.vsstatus.bits.SPELP     := 0.U
 
   outSDT.vsstatus.valid       := valid && (sretToVU || sretInVS) // clear SDT when return to VU or sret in vs
   outSDT.vsstatus.bits.SDT    := 0.U
@@ -142,4 +144,3 @@ trait SretEventSDTSinkBundle extends EventSinkBundle { self: CSRModule[_ <: CSRB
 
   reconnectReg()
 }
-
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
index 1da5dddda8c..0b981b21a8e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
@@ -12,7 +12,7 @@ import xiangshan.backend.fu.NewCSR._
 
 
 class TrapEntryDEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val dcsr            = ValidIO((new DcsrBundle).addInEvent(_.CAUSE, _.V, _.PRV))
+  val dcsr            = ValidIO((new DcsrBundle).addInEvent(_.CAUSE, _.V, _.PRV, _.PELP))
   val dpc             = ValidIO((new Epc       ).addInEvent(_.epc))
   val targetPc        = ValidIO(new TargetPCBundle)
   val debugMode       = ValidIO(Bool())
@@ -91,6 +91,7 @@ class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSRE
   out.dcsr.bits.V             := current.privState.V.asUInt
   out.dcsr.bits.PRV           := current.privState.PRVM.asUInt
   out.dcsr.bits.CAUSE         := cause
+  out.dcsr.bits.PELP          := current.ZicfilpELP.getOrElse(false.B)
   out.dpc.bits.epc            := Mux(satpFlushFirstFetchFault,
                                   trapPC(63, 1),
                                   Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1)))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
index 77a0b4f6640..5e5729d4e67 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
@@ -15,7 +15,7 @@ import xiangshan.AddrTransType
 class TrapEntryHSEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
 
   // Todo: use sstatus instead of mstatus
-  val mstatus = ValidIO((new MstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE, _.SDT))
+  val mstatus = ValidIO((new MstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE, _.SDT, _.SPELP))
   val hstatus = ValidIO((new HstatusBundle ).addInEvent(_.SPV, _.SPVP, _.GVA))
   val sepc    = ValidIO((new Epc           ).addInEvent(_.epc))
   val scause  = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
@@ -78,6 +78,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   private val isFetchMalAddr = in.isFetchMalAddr
   private val isFetchMalAddrExcp = isException && isFetchMalAddr
   private val isIllegalInst  = isException && (ExceptionNO.EX_II.U === highPrioTrapNO || ExceptionNO.EX_VI.U === highPrioTrapNO)
+  private val isZicfilp      = isException && ExceptionNO.EX_SWC.U === highPrioTrapNO
 
   private val isLSGuestExcp    = isException && ExceptionNO.getLSGuestPageFault.map(_.U === highPrioTrapNO).reduce(_ || _)
   private val isFetchGuestExcp = isException && ExceptionNO.EX_IGPF.U === highPrioTrapNO
@@ -98,6 +99,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
     (tvalFillPcPlus2                   ) -> (trapPC + 2.U),
     (tvalFillMemVaddr || isLSGuestExcp ) -> trapMemVA,
     (tvalFillInst                      ) -> trapInst,
+    (isZicfilp                         ) -> 2.U,
   ))
 
   private val tval2 = Mux1H(Seq(
@@ -131,6 +133,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   out.mstatus.bits.SPIE         := current.sstatus.SIE
   out.mstatus.bits.SIE          := 0.U
   out.mstatus.bits.SDT          := in.menvcfg.DTE.asBool // when DTE open set SDT to 1, else SDT is readonly 0
+  out.mstatus.bits.SPELP        := current.ZicfilpELP.getOrElse(false.B)
   // hstatus
   out.hstatus.bits.SPV          := current.privState.V
     // SPVP is not PrivMode enum type, so asUInt and shrink the width
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
index 24f925c139f..852aae22974 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
@@ -13,7 +13,7 @@ import xiangshan.AddrTransType
 
 class TrapEntryMEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
 
-  val mstatus   = ValidIO((new MstatusBundle ).addInEvent(_.MPV, _.MPP, _.GVA, _.MPIE, _.MIE, _.MDT))
+  val mstatus   = ValidIO((new MstatusBundle ).addInEvent(_.MPV, _.MPP, _.GVA, _.MPIE, _.MIE, _.MDT, _.MPELP))
   val mepc      = ValidIO((new Epc           ).addInEvent(_.epc))
   val mcause    = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
   val mtval     = ValidIO((new OneFieldBundle).addInEvent(_.ALL))
@@ -76,6 +76,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   private val isFetchMalAddr = in.isFetchMalAddr
   private val isFetchMalAddrExcp = isException && isFetchMalAddr
   private val isIllegalInst  = isException && (ExceptionNO.EX_II.U === highPrioTrapNO || ExceptionNO.EX_VI.U === highPrioTrapNO)
+  private val isZicfilp      = isException && ExceptionNO.EX_SWC.U === highPrioTrapNO
 
   private val isLSGuestExcp    = isException && ExceptionNO.getLSGuestPageFault.map(_.U === highPrioTrapNO).reduce(_ || _)
   private val isFetchGuestExcp = isException && ExceptionNO.EX_IGPF.U === highPrioTrapNO
@@ -96,6 +97,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
     (tvalFillPcPlus2                   ) -> (trapPC + 2.U),
     (tvalFillMemVaddr || isLSGuestExcp ) -> trapMemVA,
     (tvalFillInst                      ) -> trapInst,
+    (isZicfilp                         ) -> 2.U,
   ))
 
   private val tval2 = Mux1H(Seq(
@@ -131,6 +133,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   out.mstatus.bits.MPIE         := current.mstatus.MIE
   out.mstatus.bits.MIE          := 0.U
   out.mstatus.bits.MDT          := 1.U
+  out.mstatus.bits.MPELP        := current.ZicfilpELP.getOrElse(false.B)
   out.mepc.bits.epc             := Mux(satpFlushFirstFetchFault,
                                     trapPC(63, 1),
                                     Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1)))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala
index 9ab6c2da70e..a8f313a8f0d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala
@@ -10,7 +10,7 @@ import xiangshan.backend.fu.NewCSR._
 import xiangshan.AddrTransType
 
 class TrapEntryMNEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
-  val mnstatus = ValidIO((new MnstatusBundle ).addInEvent(_.MNPP, _.MNPV, _.NMIE))
+  val mnstatus = ValidIO((new MnstatusBundle ).addInEvent(_.MNPP, _.MNPV, _.NMIE, _.MNPELP))
   val mnepc    = ValidIO((new Epc           ).addInEvent(_.epc))
   val mncause  = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
 }
@@ -51,6 +51,7 @@ class TrapEntryMNEventModule(implicit val p: Parameters) extends Module with CSR
   out.mnstatus.bits.MNPP         := current.privState.PRVM
   out.mnstatus.bits.MNPV         := current.privState.V
   out.mnstatus.bits.NMIE         := 0.U
+  out.mnstatus.bits.MNPELP       := current.ZicfilpELP.getOrElse(false.B)
   out.mnepc.bits.epc             := Mux(satpFlushFirstFetchFault,
                                       trapPC(63, 1),
                                       Mux(isFetchMalAddr, in.fetchMalTval(63, 1), trapPC(63, 1)))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
index a7506d9022b..cbcd1063322 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
@@ -14,7 +14,7 @@ import xiangshan.AddrTransType
 
 class TrapEntryVSEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase  {
 
-  val vsstatus = ValidIO((new SstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE, _.SDT))
+  val vsstatus = ValidIO((new SstatusBundle ).addInEvent(_.SPP, _.SPIE, _.SIE, _.SDT, _.SPELP))
   val vsepc    = ValidIO((new Epc           ).addInEvent(_.epc))
   val vscause  = ValidIO((new CauseBundle   ).addInEvent(_.Interrupt, _.ExceptionCode))
   val vstval   = ValidIO((new OneFieldBundle).addInEvent(_.ALL))
@@ -83,6 +83,7 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   private val isFetchMalAddrExcp = isException && isFetchMalAddr
   private val isIllegalInst  = isException && (EX_II.U === highPrioTrapNO || EX_VI.U === highPrioTrapNO)
   private val satpFlushFirstFetchFault = in.satpFlushFirstFetchFault
+  private val isZicfilp      = isException && EX_SWC.U === highPrioTrapNO
 
   // Software breakpoint exceptions are permitted to write either 0 or the pc to xtval
   // We fill pc here
@@ -99,6 +100,7 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
     tvalFillPcPlus2  -> (trapPC + 2.U),
     tvalFillMemVaddr -> trapMemVA,
     tvalFillInst     -> trapInst,
+    isZicfilp        -> 2.U,
   ))
 
   out := DontCare
@@ -116,6 +118,7 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   out.vsstatus.bits.SPIE         := current.vsstatus.SIE
   out.vsstatus.bits.SIE          := 0.U
   out.vsstatus.bits.SDT          := in.henvcfg.DTE.asBool // when DTE open set SDT to 1, else SDT is readonly 0
+  out.vsstatus.bits.SPELP        := current.ZicfilpELP.getOrElse(false.B)
   // SPVP is not PrivMode enum type, so asUInt and shrink the width
   out.vsepc.bits.epc             := Mux(satpFlushFirstFetchFault,
                                       trapPC(63, 1),
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index ad977713765..543103ed8ff 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -64,7 +64,7 @@ trait DebugLevel { self: NewCSR =>
   val tinfo = Module(new CSRModule("Tinfo", new TinfoBundle))
     .setAddr(CSRs.tinfo)
 
-  val dcsr = Module(new CSRModule("Dcsr", new DcsrBundle) with TrapEntryDEventSinkBundle with DretEventSinkBundle with HasNmipBundle {
+  val dcsr = Module(new CSRModule("Dcsr", new DcsrBundle(HasZicfilp)) with TrapEntryDEventSinkBundle with DretEventSinkBundle with HasNmipBundle {
     regOut.NMIP := nmip
   })
     .setAddr(CSRs.dcsr)
@@ -349,11 +349,12 @@ object TriggerVer extends CSREnum with ROApply {
 class DscratchBundle extends OneFieldBundle(Some("Debug scratch register."))
 
 
-class DcsrBundle extends CSRBundle {
+class DcsrBundle(hasZicfilp: Boolean = false) extends CSRBundle {
   override val len: Int = 32
   val DEBUGVER  = DcsrDebugVer(31, 28).withReset(DcsrDebugVer.Spec).withDescription("Debug specification version implemented by this hart.")
   val EXTCAUSE  =           RO(26, 24).withReset(0.U).withDescription("Additional cause detail for debug entry.")
   val CETRIG    =           RW(    19).withReset(0.U).withDescription("Trigger re-entry control for critical-error debug entry.")
+  val PELP      =           RO(    18).withReset(0.U).withDescription("Saved landing-pad state from before Debug Mode entry.")
   val EBREAKVS  =           RW(    17).withReset(0.U).withDescription("Enter Debug Mode on VS-mode EBREAK.")
   val EBREAKVU  =           RW(    16).withReset(0.U).withDescription("Enter Debug Mode on VU-mode EBREAK.")
   val EBREAKM   =           RW(    15).withReset(0.U).withDescription("Enter Debug Mode on M-mode EBREAK.")
@@ -368,6 +369,10 @@ class DcsrBundle extends CSRBundle {
   val NMIP      =           RO(     3).withReset(0.U).withDescription("Indicates pending non-maskable interrupt state while in Debug Mode.")
   val STEP      =           RW(     2).withReset(0.U).withDescription("Enable single-step execution.")
   val PRV       =     PrivMode( 1,  0).withReset(PrivMode.M).withDescription("Privilege mode active before entering Debug Mode.")
+
+  if (hasZicfilp) {
+    PELP.setRW()
+  }
 }
 
 object DcsrDebugVer extends CSREnum with ROApply {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 9905fed7e8b..dca4dbe4abc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -70,7 +70,7 @@ trait HypervisorLevel { self: NewCSR =>
   val hvictl = Module(new CSRModule("Hvictl", new HvictlBundle))
     .setAddr(CSRs.hvictl)
 
-  val henvcfg = Module(new CSRModule("Henvcfg", new HEnvCfg) with HasHypervisorEnvBundle {
+  val henvcfg = Module(new CSRModule("Henvcfg", new HEnvCfg(HasZicfilp)) with HasHypervisorEnvBundle {
     when(!menvcfg.STCE) {
       regOut.STCE := 0.U
     }
@@ -361,7 +361,10 @@ class HgatpBundle extends CSRBundle {
   val PPN = RW(43, 0).withReset(0.U).withDescription("Root page-table physical page number for G-stage translation.")
 }
 
-class HEnvCfg extends EnvCfg {
+class HEnvCfg(hasZicfilp: Boolean = false) extends EnvCfg {
+  if (hasZicfilp) {
+    this.LPE.setRW()
+  }
   if (CSRConfig.EXT_SSTC) {
     this.STCE.setRW().withReset(1.U)
   }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index e50314f13af..8a9b6837359 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -49,7 +49,7 @@ trait MachineLevel { self: NewCSR =>
   })
     .setAddr(Mmpt)) else None
 
-  val mstatus = Module(new MstatusModule)
+  val mstatus = Module(new MstatusModule(HasZicfilp))
     .setAddr(CSRs.mstatus)
 
   val misa = Module(new CSRModule("Misa", new MisaBundle))
@@ -205,7 +205,7 @@ trait MachineLevel { self: NewCSR =>
     }
   }).setAddr(CSRs.mvip)
 
-  val menvcfg = Module(new CSRModule("Menvcfg", new MEnvCfg))
+  val menvcfg = Module(new CSRModule("Menvcfg", new MEnvCfg(HasZicfilp)))
     .setAddr(CSRs.menvcfg)
 
   val mcountinhibit = Module(new CSRModule("Mcountinhibit", new McountinhibitBundle){
@@ -355,7 +355,10 @@ trait MachineLevel { self: NewCSR =>
 
   val mseccfg = Module(new CSRModule("Mseccfg", new CSRBundle {
     val PMM   = EnvPMM(33, 32, wNoFilter).withReset(EnvPMM.Disable).withDescription("Machine security memory protection mode from the Smmpm extension.")
-    val MLPE  = RO(10).withDescription("Machine landing-pad enable from the Zicfilp extension.")
+    val MLPE  = RO(10).withReset(0.U).withDescription("Machine landing-pad enable from the Zicfilp extension.")
+    if (HasZicfilp) {
+      MLPE.setRW()
+    }
     val SSEED = RO( 9).withDescription("Seed CSR enable from the Zkr extension.")
     val USEED = RO( 8).withDescription("User seed CSR enable from the Zkr extension.")
     val RLB   = RO( 2).withDescription("Rule-locking bypass control from the Smepmp extension.")
@@ -476,7 +479,7 @@ trait MachineLevel { self: NewCSR =>
 
   val mncause = Module(new CSRModule("Mncause", new CauseBundle) with TrapEntryMNEventSinkBundle)
     .setAddr(CSRs.mncause)
-  val mnstatus = Module(new CSRModule("Mnstatus", new MnstatusBundle)
+  val mnstatus = Module(new CSRModule("Mnstatus", new MnstatusBundle(HasZicfilp))
     with TrapEntryMNEventSinkBundle
     with MNretEventSinkBundle{
     // NMIE write 0 with no effect
@@ -566,7 +569,7 @@ class MmptBundle extends CSRBundle { //HasMptCheck
   val PPN = RW(PPNLengthMpt-1, 0).withReset(0.U).withDescription("Mpt level3 talble adress.")
 }
 
-class MstatusBundle extends CSRBundle {
+class MstatusBundle(hasZicfilp: Boolean = false) extends CSRBundle {
 
   val SIE  = CSRRWField     (1).withReset(0.U).withDescription("Global interrupt enable for S-mode.")
   val MIE  = CSRRWField     (3).withReset(0.U).withDescription("Global interrupt enable for M-mode.")
@@ -584,6 +587,7 @@ class MstatusBundle extends CSRBundle {
   val TVM  = CSRRWField     (20).withReset(0.U).withDescription("Trap virtual-memory management operations in S-mode when set.")
   val TW   = CSRRWField     (21).withReset(0.U).withDescription("Trap WFI in lower privilege modes when set.")
   val TSR  = CSRRWField     (22).withReset(0.U).withDescription("Trap SRET when set.")
+  val SPELP = CSRROField    (23).withReset(0.U).withDescription("Saved landing-pad state for traps into S-mode.")
   val SDT  = CSRRWField     (24).withReset(0.U).withDescription("S-mode disable-trap bit used by the Ssdbltrp extension.")
   val UXL  = XLENField      (33, 32).withReset(XLENField.XLEN64).withDescription("Effective XLEN for U-mode.")
   val SXL  = XLENField      (35, 34).withReset(XLENField.XLEN64).withDescription("Effective XLEN for S-mode.")
@@ -591,13 +595,20 @@ class MstatusBundle extends CSRBundle {
   val MBE  = CSRROField     (37).withReset(0.U).withDescription("M-mode endianness selector.")
   val GVA  = CSRRWField     (38).withReset(0.U).withDescription("Indicates that trap information was derived from a guest virtual address.")
   val MPV  = VirtMode       (39).withReset(0.U).withDescription("Saved virtualization mode from before trap entry to M-mode.")
+  val MPELP = CSRROField    (41).withReset(0.U).withDescription("Saved landing-pad state for traps into M-mode.")
   val MDT  = CSRRWField     (42).withReset(1.U).withDescription("M-mode disable-trap bit used by the Smdbltrp extension.")
   val SD   = CSRROField     (63,
     (_, _) => FS === ContextStatus.Dirty || VS === ContextStatus.Dirty
   ).withDescription("Dirty summary bit for the floating-point or vector context.")
+
+  if (hasZicfilp) {
+    SPELP.setRW()
+    MPELP.setRW()
+  }
 }
 
-class MstatusModule(implicit override val p: Parameters) extends CSRModule("MStatus", new MstatusBundle)
+class MstatusModule(hasZicfilp: Boolean)(implicit override val p: Parameters)
+  extends CSRModule("MStatus", new MstatusBundle(hasZicfilp))
   with TrapEntryMEventSinkBundle
   with TrapEntryHSEventSinkBundle
   with DretEventSinkBundle
@@ -608,10 +619,10 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   with HasMachineEnvBundle
 {
   val mstatus = IO(Output(bundle))
-  val sstatus = IO(Output(new SstatusBundle))
+  val sstatus = IO(Output(new SstatusBundle(hasZicfilp)))
   val sstatusRdata = IO(Output(UInt(64.W)))
 
-  val wAliasSstatus = IO(Input(new CSRAddrWriteBundle(new SstatusBundle)))
+  val wAliasSstatus = IO(Input(new CSRAddrWriteBundle(new SstatusBundle(hasZicfilp))))
   for ((name, field) <- wAliasSstatus.wdataFields.elements) {
     reg.elements(name).asInstanceOf[CSREnumType].addOtherUpdate(
       wAliasSstatus.wen && field.asInstanceOf[CSREnumType].isLegal,
@@ -662,11 +673,15 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   sstatusRdata := sstatus.asUInt
 }
 
-class MnstatusBundle extends CSRBundle {
+class MnstatusBundle(hasZicfilp: Boolean = false) extends CSRBundle {
   val NMIE   = CSRRWField  (3).withReset(0.U).withDescription("Enable non-maskable interrupt handling.")
   val MNPV   = VirtMode    (7).withReset(0.U).withDescription("Saved virtualization mode for resumable NMI handling.")
   val MNPELP = RO          (9).withReset(0.U).withDescription("Saved landing-pad state for resumable NMI handling.")
   val MNPP   = PrivMode    (12, 11).withReset(PrivMode.U).withDescription("Saved privilege level for resumable NMI handling.")
+
+  if (hasZicfilp) {
+    MNPELP.setRW()
+  }
 }
 
 class MisaBundle extends CSRBundle {
@@ -775,7 +790,10 @@ class Mtval2Bundle extends FieldInitBundle(Some("Guest physical address or addit
 
 class MhpmcounterBundle extends FieldInitBundle(Some("Hardware performance-monitoring counter value."))
 
-class MEnvCfg extends EnvCfg {
+class MEnvCfg(hasZicfilp: Boolean = false) extends EnvCfg {
+  if (hasZicfilp) {
+    this.LPE.setRW()
+  }
   if (CSRConfig.EXT_SSTC) {
     this.STCE.setRW().withReset(1.U)
   }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 8f1f4729b2e..b6bbca0ee73 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -96,6 +96,8 @@ class NewCSROutput(implicit p: Parameters) extends Bundle {
   val regOut = UInt(64.W)
   // perf
   val isPerfCnt = Bool()
+  // Zicfilp
+  val retELP = OptionWrapper(p(XSCoreParamsKey).HasZicfilp, Bool())
 }
 
 class NewCSR(implicit val p: Parameters) extends Module
@@ -132,6 +134,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     })
     val in = Flipped(DecoupledIO(new NewCSRInput))
     val trapInst = Input(ValidIO(UInt(InstWidth.W)))
+    // Zicfilp
+    val ZicfilpELP = OptionWrapper(HasZicfilp, Input(Bool()))
     val fromMem = Input(new Bundle {
       val excpVA  = UInt(XLEN.W)
       val excpGPA = UInt(XLEN.W)
@@ -150,6 +154,7 @@ class NewCSR(implicit val p: Parameters) extends Module
         val isInterrupt = Bool()
         val isHls = Bool()
         val isFetchMalAddr = Bool()
+        val ZicfilpELP = OptionWrapper(HasZicfilp, Bool())
         val isForVSnonLeafPTE = Bool()
         val satpFlushFirstFetchFault = Bool()
         val isFormer = Bool()
@@ -196,6 +201,9 @@ class NewCSR(implicit val p: Parameters) extends Module
       val vsatp = Valid(UInt(SatpMode.getWidth.W))
       // debug
       val debugMode = Bool()
+      // One-cycle entry pulse for Debug/RNMI/MN paths. Unlike debugMode, this
+      // is asserted only on entry and is consumed by ArchZicfilp.
+      val zicfilpEntryClear = OptionWrapper(HasZicfilp, Bool())
       val singleStepFlag = Bool()
       // trigger
       val frontendTrigger = new FrontendTdataDistributeIO()
@@ -869,6 +877,9 @@ class NewCSR(implicit val p: Parameters) extends Module
 
         in.iMode.PRVM := Mux(trapIsSatpFlushFirstFetchFault, oldPrivState.PRVM, PRVM)
         in.iMode.V := Mux(trapIsSatpFlushFirstFetchFault, oldPrivState.V, V)
+        in.ZicfilpELP.foreach { sink =>
+          sink := Mux(hasTrap, io.fromRob.trap.bits.ZicfilpELP.get, io.ZicfilpELP.getOrElse(false.B))
+        }
         // when NMIE is zero, force to behave as MPRV is zero
         in.dMode.PRVM := Mux(mstatus.regOut.MPRV.asBool && mnstatus.regOut.NMIE.asBool, mstatus.regOut.MPP, PRVM)
         in.dMode.V := V.asUInt.asBool || mstatus.regOut.MPRV && mnstatus.regOut.NMIE.asBool && (mstatus.regOut.MPP =/= PrivMode.M) && mstatus.regOut.MPV
@@ -1051,9 +1062,21 @@ class NewCSR(implicit val p: Parameters) extends Module
   // flush pipe when write xcontext.
   val writeContext = Cat(Seq(mcontext, hcontext, scontext).map(_.addr.U === addr)).orR && wenLegalReg
 
+  // Zicfilp: younger instructions may already have been decoded with the old xLPE value.
+  val zicfilpLPEChange = if (HasZicfilp) {
+    Seq(
+      mseccfg.w.wen && mseccfg.w.wdataFields.MLPE =/= mseccfg.regOut.MLPE,
+      menvcfg.w.wen && menvcfg.w.wdataFields.LPE =/= menvcfg.regOut.LPE,
+      henvcfg.w.wen && henvcfg.w.wdataFields.LPE =/= henvcfg.regOut.LPE,
+      senvcfg.w.wen && senvcfg.w.wdataFields.LPE =/= senvcfg.regOut.LPE,
+    ).reduce(_ || _)
+  } else {
+    false.B
+  }
+
   val flushPipe = resetSatp ||
     triggerFrontendChange || floatStatusOnOff || vectorStatusOnOff ||
-    vstartChange || frmChange || writeContext
+    vstartChange || frmChange || writeContext || zicfilpLPEChange
 
   /**
    * Look up id in vsMapS and sMapVS.
@@ -1168,6 +1191,23 @@ class NewCSR(implicit val p: Parameters) extends Module
     waitIMSICValid -> imsic_EX_VI,
   )), false.B, normalCSRValid || waitIMSICValid)
   io.out.bits.flushPipe := flushPipe
+  io.out.bits.retELP.foreach { out =>
+    def targetLPE(targetPrv: UInt, targetVirtual: Bool): Bool = MuxCase(false.B, Seq(
+      (targetPrv === PrivMode.M.asUInt) -> mseccfg.regOut.MLPE.asBool,
+      (targetPrv === PrivMode.S.asUInt && !targetVirtual) -> menvcfg.regOut.LPE.asBool,
+      (targetPrv === PrivMode.S.asUInt && targetVirtual) -> henvcfg.regOut.LPE.asBool,
+      (targetPrv === PrivMode.U.asUInt) -> senvcfg.regOut.LPE.asBool,
+    ))
+
+    val sretPELP = Mux(isModeVS, vsstatus.regOut.SPELP.asBool, mstatus.regOut.SPELP.asBool)
+    val restoredELP = MuxCase(false.B, Seq(
+      legalMNret -> (mnstatus.regOut.MNPELP.asBool && targetLPE(mnretEvent.out.privState.bits.PRVM.asUInt, mnretEvent.out.privState.bits.V.asBool)),
+      legalMret  -> (mstatus.regOut.MPELP.asBool && targetLPE(mretEvent.out.privState.bits.PRVM.asUInt, mretEvent.out.privState.bits.V.asBool)),
+      legalSret  -> (sretPELP && targetLPE(sretEvent.out.privState.bits.PRVM.asUInt, sretEvent.out.privState.bits.V.asBool)),
+      legalDret  -> (dcsr.regOut.PELP.asBool && targetLPE(dretEvent.out.privState.bits.PRVM.asUInt, dretEvent.out.privState.bits.V.asBool)),
+    ))
+    out := DataHoldBypass(restoredELP, false.B, normalCSRValid)
+  }
 
   /** Prepare read data for output */
   io.out.bits.rData := DataHoldBypass(
@@ -1291,6 +1331,13 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   entryDebugMode := debugMod.io.out.hasDebugTrap && !debugMode
 
+  // RNMI and double-trap use the CSR trap machinery as well. Export the entry
+  // event so ArchZicfilp can clear live ELP even when the entry is not exposed
+  // as an ordinary ROB exception at the CtrlBlock boundary.
+  io.status.zicfilpEntryClear.foreach { clear =>
+    clear := entryDebugMode || (hasTrap && nmi) || dbltrpToMN
+  }
+
   trapEntryDEvent.valid                           := entryDebugMode
   trapEntryDEvent.in.hasDebugIntr                 := debugMod.io.out.hasDebugIntr
   trapEntryDEvent.in.debugMode                    := debugMode
@@ -1589,6 +1636,19 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.tlb.pmm.hstatus := RegNext(hstatus.regOut.HUPMM.asUInt)
   io.tlb.pmm.senvcfg := RegNext(senvcfg.regOut.PMM.asUInt)
 
+  // Zicfilp
+  io.toDecode.enableZicfilp.foreach { enable =>
+    val modeLPE = MuxCase(false.B, Seq(
+      isModeM  -> mseccfg.regOut.MLPE.asBool,
+      isModeHS -> menvcfg.regOut.LPE.asBool,
+      isModeVS -> henvcfg.regOut.LPE.asBool,
+      (isModeHU || isModeVU) -> senvcfg.regOut.LPE.asBool,
+    ))
+    // Debug 期间 ELP 保持 NO_LP_EXPECTED，LPAD 按 no-op 执行；不改写任何 LPE CSR。
+    // 这是 Decode/提交侧的有效使能。DRET 的 targetLPE 仍读取返回目标的原始 LPE 配置。
+    enable := modeLPE && !debugMode
+  }
+
   io.toDecode.illegalInst.mfence.foreach(_ := !isModeM)
 
   io.toDecode.illegalInst.sfenceVMA  := isModeHS && mstatus.regOut.TVM  || isModeHU
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
index f644e09abe9..66402c66eb1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
@@ -67,7 +67,7 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
   val scounteren = Module(new CSRModule("Scounteren", new Counteren))
     .setAddr(CSRs.scounteren)
 
-  val senvcfg = Module(new CSRModule("Senvcfg", new SEnvCfg))
+  val senvcfg = Module(new CSRModule("Senvcfg", new SEnvCfg(HasZicfilp)))
     .setAddr(CSRs.senvcfg)
 
   val scountinhibit = Module(new CSRModule("Scountinhibit", new McountinhibitBundle) with HasMcounterenBundle {
@@ -222,7 +222,7 @@ trait SupervisorLevel { self: NewCSR with MachineLevel =>
   )
 }
 
-class SstatusBundle extends CSRBundle {
+class SstatusBundle(hasZicfilp: Boolean = false) extends CSRBundle {
   val SIE  = CSRWARLField   (1, wNoFilter).withDescription("Global interrupt enable for S-mode.")
   val SPIE = CSRWARLField   (5, wNoFilter).withDescription("Saved SIE value from before trap entry.")
   val UBE  = CSRROField     (6).withReset(0.U).withDescription("U-mode endianness selector.")
@@ -232,9 +232,14 @@ class SstatusBundle extends CSRBundle {
   val XS   = ContextStatusRO(16, 15).withReset(0.U).withDescription("Additional user extension state summary.")
   val SUM  = CSRWARLField   (18, wNoFilter).withReset(0.U).withDescription("Permit S-mode data accesses to pages marked as user.")
   val MXR  = CSRWARLField   (19, wNoFilter).withReset(0.U).withDescription("Make executable pages readable when set.")
+  val SPELP = CSRROField    (23).withReset(0.U).withDescription("Saved landing-pad state for traps into S-mode.")
   val SDT  = CSRWARLField   (24, wNoFilter).withReset(0.U).withDescription("S-mode disable-trap bit used by the Ssdbltrp extension.")
   val UXL  = XLENField      (33, 32).withReset(XLENField.XLEN64).withDescription("Effective XLEN for U-mode.")
   val SD   = CSRROField     (63, (_, _) => FS === ContextStatus.Dirty || VS === ContextStatus.Dirty).withDescription("Dirty summary bit for the floating-point or vector context.")
+
+  if (hasZicfilp) {
+    SPELP.setRW()
+  }
 }
 
 class SieBundle extends InterruptEnableBundle {
@@ -267,7 +272,11 @@ class ScontextBundle extends CSRBundle {
   val ALL = RW(31, 0).withReset(0.U).withDescription("Supervisor trigger context value.")
 }
 
-class SEnvCfg extends EnvCfg
+class SEnvCfg(hasZicfilp: Boolean = false) extends EnvCfg {
+  if (hasZicfilp) {
+    this.LPE.setRW()
+  }
+}
 
 class SipToMip extends IpValidBundle {
   this.SSIP.bits.setRW()
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
index b3da1b7836c..61b84c7622c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
@@ -18,7 +18,7 @@ import scala.collection.immutable.SeqMap
 trait VirtualSupervisorLevel { self: NewCSR with SupervisorLevel with HypervisorLevel =>
 
   val vsstatus = Module(
-    new CSRModule("VSstatus", new SstatusBundle)
+    new CSRModule("VSstatus", new SstatusBundle(HasZicfilp))
       with SretEventSinkBundle
       with SretEventSDTSinkBundle
       with MretEventSinkBundle
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index ed21e0adb85..c7e7bc60910 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -4,6 +4,7 @@ import chisel3._
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils.OptionWrapper
 import xiangshan._
 import xiangshan.backend.fu.NewCSR._
 import xiangshan.backend.fu.util._
@@ -117,6 +118,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       in.bits.dret := isDret
       in.bits.redirectFlush := redirectFlush
   }
+  csrMod.io.ZicfilpELP.foreach(_ := io.csrin.get.ZicfilpELP.getOrElse(false.B))
   csrMod.io.trapInst := trapInstMod.io.currentTrapInst
   csrMod.io.fetchMalTval := trapTvalMod.io.tval
   csrMod.io.fromMem.excpVA  := csrIn.memExceptionVAddr
@@ -128,6 +130,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   csrMod.io.fromRob.trap.valid := csrIn.exception.valid
   csrMod.io.fromRob.trap.bits.pc := csrIn.exception.bits.pc
+  csrMod.io.fromRob.trap.bits.ZicfilpELP.zip(csrIn.exception.bits.ZicfilpELP).foreach { case (sink, source) => sink := source }
   csrMod.io.fromRob.trap.bits.instr := csrIn.exception.bits.instr
   csrMod.io.fromRob.trap.bits.pcGPA := csrIn.exception.bits.gpaddr
   // Todo: shrink the width of trap vector.
@@ -355,6 +358,9 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   redirect.backendIGPF := csrMod.io.xretTargetPc.bits.raiseIGPF
   // Only mispred will send redirect to frontend
   redirect.isMisPred := false.B
+  val legalXRetOut = io.out.valid && isXRetReg && !csrModOut.EX_II && !csrModOut.EX_VI
+  redirect.ZicfilpXRetValid.foreach(_ := legalXRetOut)
+  redirect.ZicfilpRetELP.foreach(_ := csrModOut.retELP.getOrElse(false.B))
 
   val rfWenReg = RegEnable(io.in.bits.ctrl.rfWen.get, io.in.fire)
   val pdestReg = RegEnable(io.in.bits.ctrl.pdest, io.in.fire)
@@ -386,6 +392,9 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   csrOut.tlb := tlb
 
   csrOut.debugMode := csrMod.io.status.debugMode
+  csrOut.zicfilpEntryClear.foreach { sink =>
+    sink := csrMod.io.status.zicfilpEntryClear.get
+  }
 
   csrOut.traceCSR := csrMod.io.status.traceCSR
 
@@ -445,12 +454,17 @@ class CSRInput(implicit p: Parameters) extends XSBundle with HasSoCParameter {
   val l2FlushDone = Input(Bool())
   val reset_mtvec = Option.when(enableResetMtvec)(Input(UInt(PAddrBits.W)))
   val trapInstInfo = Input(ValidIO(new TrapInstInfo))
+  // Zicfilp
+  val ZicfilpELP = OptionWrapper(HasZicfilp, Input(Bool()))
   val fromVecExcpMod = Input(new Bundle {
     val busy = Bool()
   })
 }
 
 class CSRToDecode(implicit p: Parameters) extends XSBundle {
+  // Zicfilp
+  val enableZicfilp = OptionWrapper(HasZicfilp, Bool())
+
   val illegalInst = new Bundle {
 
     val mfence = Option.when(HasMptCheck) (Bool())
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
index 5d73268e7ab..76e4e4e882c 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
@@ -7,7 +7,7 @@ import xiangshan.backend.datapath.DataConfig.VAddrData
 import xiangshan.backend.fu.{FuConfig, PipedFuncUnit}
 import xiangshan.frontend.{PcInit, PrunedAddrInit}
 import xiangshan.frontend.bpu.BranchAttribute.BranchType
-import xiangshan.{JumpOpType, Redirect, RedirectLevel}
+import xiangshan.{ExceptionNO, JumpOpType, Redirect, RedirectLevel}
 
 class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg) {
   // associated with AddrData's position of NJmpCfg.srcData
@@ -15,6 +15,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   private val pc = io.instrAddrTransType.get.extend(io.in.bits.data.pc.get, cfg.destDataBits)
   private val imm = io.in.bits.data.imm
   private val func = io.in.bits.ctrl.fuOpType
+  private val isLPAD = io.in.bits.ctrl.ZicfilpInfos.map(_.ZicfilpLPADValid).getOrElse(false.B)
   // XSError(func =/= NewJumpOpType.j && func =/= NewJumpOpType.jr, p"func ${Binary(func)} is not new_jump type uop")
 
   // j:  pc       + imm -> pc
@@ -32,7 +33,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
 
   val redirect: Redirect = io.out.bits.res.redirect.get.bits
   val redirectValid: Bool = io.out.bits.res.redirect.get.valid
-  redirectValid := io.in.valid && (needRedirect || redirect.hasBackendFault)
+  redirectValid := io.in.valid && !isLPAD && (needRedirect || redirect.hasBackendFault)
   redirect := 0.U.asTypeOf(redirect)
   redirect.level := RedirectLevel.flushAfter
   redirect.robIdx := io.in.bits.ctrl.robIdx
@@ -47,12 +48,20 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
   redirect.backendIPF := io.instrAddrTransType.get.checkPageFault(jumpTarget)
   redirect.backendIGPF := io.instrAddrTransType.get.checkGuestPageFault(jumpTarget)
   redirect.attribute := io.toFrontendBJUResolve.get.bits.attribute
+  redirect.ZicfilpJalr.foreach(_ := io.in.bits.ctrl.ZicfilpInfos.get.ZicfilpJalr)
   //  redirect.debug_runahead_checkpoint_id := uop.debugInfo.runahead_checkpoint_id // Todo: assign it
 
+  if (HasZicfilp) {
+    // A zero LPAD label matches any x7 value.
+    val lpadLabel = imm(31, 12)
+    io.out.bits.ctrl.exceptionVec(ExceptionNO.softwareCheck) :=
+      isLPAD && lpadLabel.orR && src(31, 12) =/= lpadLabel
+  }
+
   io.in.ready := io.out.ready
   io.out.valid := io.in.valid
   io.out.bits.res.data := 0.U
-  io.toFrontendBJUResolve.get.valid := io.out.valid
+  io.toFrontendBJUResolve.get.valid := io.out.valid && !isLPAD
   io.toFrontendBJUResolve.get.bits.ftqIdx := io.in.bits.ctrl.ftqIdx.get
   io.toFrontendBJUResolve.get.bits.ftqOffset := io.in.bits.ctrl.ftqOffset.get
   io.toFrontendBJUResolve.get.bits.pc := PcInit(pc(VAddrBits - 1, 0))
diff --git a/src/main/scala/xiangshan/backend/rename/CompressUnit.scala b/src/main/scala/xiangshan/backend/rename/CompressUnit.scala
index bae63b0ff50..6d50e89d6f0 100644
--- a/src/main/scala/xiangshan/backend/rename/CompressUnit.scala
+++ b/src/main/scala/xiangshan/backend/rename/CompressUnit.scala
@@ -104,10 +104,16 @@ class CompressUnit(implicit p: Parameters) extends XSModule{
       inst.OPCODE === "b1110111".U
   })
   val cannotCompressVec = VecInit(io.in.zip(isVectorInstrVec).map { case (x, isVectorInstr) =>
+    // Zicfilp: ELP-changing instructions must keep a dedicated ROB entry.
+    val changesELP = if (HasZicfilp) {
+      x.bits.ZicfilpInfos.get.ZicfilpJalr || x.bits.ZicfilpLPAD.get
+    } else {
+      false.B
+    }
     // All vector instructions retain the mainline retirement/exception path.
     // Split instructions also keep its firstUop/lastUop allocation semantics.
     x.valid && (x.bits.waitForward || x.bits.blockBackward ||
-      isVectorInstr ||
+      isVectorInstr || changesELP ||
       !x.bits.firstUop || !x.bits.lastUop)
   })
 
diff --git a/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala b/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
index c7b6d681781..032d008323a 100644
--- a/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
+++ b/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
@@ -32,23 +32,23 @@ import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.rename.SnapshotGenerator
 
 class ExceptionGen(params: BackendParams)(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper {
-  val allExceptions = ExceptionNO.exceptionGenSet(params)
+  val allExceptions = ExceptionNO.exceptionGenSet(params, HasZicfilp)
 
   val io = IO(new Bundle {
     val redirect = Input(Valid(new Redirect))
     val flush = Input(Bool())
-    val enq = Vec(RenameWidth, Flipped(ValidIO(new RobExceptionInfo(ExceptionNO.decodeSet))))
+    val enq = Vec(RenameWidth, Flipped(ValidIO(new RobExceptionInfo(ExceptionNO.decodeSet(HasZicfilp)))))
     // csr + load + store + varith + vload + vstore
     val wb = MixedVec(
-      params.getWrite2RobParams().filter(_.needExceptionGen).map { x =>
-        Flipped(ValidIO(new RobExceptionInfo(x.exceptionOut)))
+      params.getWrite2RobParams().filter(_.needExceptionGen(HasZicfilp)).map { x =>
+        Flipped(ValidIO(new RobExceptionInfo(x.effectiveExceptionOut(HasZicfilp))))
       }
     )
     val out = ValidIO(new RobExceptionInfo(allExceptions))
     val state = ValidIO(new RobExceptionInfo(allExceptions))
   })
 
-  val wbExuParams = params.getWrite2RobParams().filter(_.needExceptionGen)
+  val wbExuParams = params.getWrite2RobParams().filter(_.needExceptionGen(HasZicfilp))
 
   def getOldest(valid: Seq[Bool], bits: Seq[RobExceptionInfo]): RobExceptionInfo = {
     def getOldest_recursion(valid: Seq[Bool], bits: Seq[RobExceptionInfo]): (Seq[Bool], Seq[RobExceptionInfo]) = {
@@ -81,7 +81,7 @@ class ExceptionGen(params: BackendParams)(implicit p: Parameters) extends XSModu
   val current = Reg(new RobExceptionInfo(allExceptions))
 
   val enqAllExcept = Wire(Vec(RenameWidth, Valid(new RobExceptionInfo(allExceptions))))
-  val wbAllExcept = Wire(Vec(params.numException, Valid(new RobExceptionInfo(allExceptions))))
+  val wbAllExcept = Wire(Vec(params.numException(HasZicfilp), Valid(new RobExceptionInfo(allExceptions))))
 
   enqAllExcept.zip(io.enq) foreach { case (sink, source) =>
     (sink: Data).waiveAll :<= (source: Data).waiveAll
@@ -104,7 +104,9 @@ class ExceptionGen(params: BackendParams)(implicit p: Parameters) extends XSModu
   val store_wb = wbAllExcept.zip(wbExuParams).filter(_._2.fuConfigs.filter(t => t.isSta || t.fuType == FuType.mou).nonEmpty).map(_._1)
   val varith_wb = wbAllExcept.zip(wbExuParams).filter(_._2.fuConfigs.filter(_.isVecArith).nonEmpty).map(_._1)
 
-  val writebacks = Seq(csr_wb, load_wb, store_wb, varith_wb).filter(_.nonEmpty)
+  // Zicfilp: landing-pad label checks write back through the jump unit.
+  val jmp_wb = wbAllExcept.zip(wbExuParams).filter(_._2.fuConfigs.exists(_.isJmp)).map(_._1)
+  val writebacks = Seq(csr_wb, load_wb, store_wb, varith_wb, jmp_wb).filter(_.nonEmpty)
   val in_wb_valids = writebacks.map(_.map(w => w.valid && w.bits.has_exception && !lastCycleFlush))
   val wb_valid = in_wb_valids.zip(writebacks).map { case (valid, wb) =>
     valid.zip(wb.map(_.bits)).map { case (v, bits) => v && !(bits.robIdx.needFlush(io.redirect) || io.flush) }.reduce(_ || _)
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 89a6dc6d089..d214c09d6a3 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -73,11 +73,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val exception = ValidIO(new ExceptionInfo)
     val diffCommitForTrap = Option.when(env.EnableDifftest || env.AlwaysBasicDiff)(Output(Bool()))
     val diffArchEvent = Option.when(env.EnableDifftest || env.AlwaysBasicDiff)(Input(Valid(Bool())))
+    val ZicfilpELP = OptionWrapper(HasZicfilp, Input(Bool()))
     // exu + brq
     val writeback: MixedVec[ValidIO[WriteBackRobBundle]] = Flipped(params.genWrite2RobBundles)
     val exuWriteback: MixedVec[ValidIO[WriteBackRobBundle]] = Flipped(params.genWrite2RobBundles)
     val writebackNums = Flipped(Vec(writeback.size, ValidIO(UInt(writeback.size.U.getWidth.W))))
-    val writebackNeedFlush = Input(Vec(params.getWrite2RobSize(_.needExceptionGen), Bool()))
+    val writebackNeedFlush = Input(Vec(params.getWrite2RobSize(_.needExceptionGen(HasZicfilp)), Bool()))
     val commits = Output(new RobCommitIO)
     val trace = new Bundle {
       val blockCommit = Input(Bool())
@@ -154,7 +155,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   })
 
   val exuWBs: Seq[ValidIO[WriteBackRobBundle]] = io.exuWriteback
-  val exceptionWBs = io.writeback.filter(x => x.bits.params.needExceptionGen).toSeq
+  val exceptionWBs = io.writeback.filter(x => x.bits.params.needExceptionGen(HasZicfilp)).toSeq
   val redirectWBs = io.writeback.filter(x => x.bits.redirect.nonEmpty).toSeq
   val branchWBs = io.exuWriteback.filter(_.bits.params.hasBrhFu).toSeq
   val isBrhOrJmpWBs = io.exuWriteback.filter(x => (x.bits.params.hasBrhFu || x.bits.params.hasJmpFu)).toSeq
@@ -915,6 +916,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val exceptionDebug = debugMeta(exceptionUop)
   io.exception.valid := RegNext(exceptionHappen)
   io.exception.bits.pc := RegEnable(exceptionDebug.pc, exceptionHappen)
+  io.exception.bits.ZicfilpELP.foreach(_ := RegEnable(io.ZicfilpELP.get, exceptionHappen))
   io.exception.bits.gpaddr := io.readGPAMemData.gpaddr
   io.exception.bits.isForVSnonLeafPTE := io.readGPAMemData.isForVSnonLeafPTE
   io.exception.bits.instr := RegEnable(exceptionDebug.instr, exceptionHappen)
@@ -1753,7 +1755,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   }
 
   println(s"ExceptionGen:")
-  println(s"num of exceptions: ${params.numException}")
+  println(s"num of exceptions: ${params.numException(HasZicfilp)}")
   require(exceptionWBs.length == exceptionGen.io.wb.length,
     f"exceptionWBs.length: ${exceptionWBs.length}, " +
       f"exceptionGen.io.wb.length: ${exceptionGen.io.wb.length}")
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index fdfca6d4d11..e3629db2a7f 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -155,6 +155,9 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val debug_vecWen     = OptionWrapper(backendParams.debugEn, Bool())
     val debug_v0Wen      = OptionWrapper(backendParams.debugEn, Bool())
     val debug_commitType = OptionWrapper(backendParams.debugEn, CommitType())
+    // Zicfilp
+    val ZicfilpJalr = OptionWrapper(HasZicfilp, Bool())
+    val ZicfilpLPAD = OptionWrapper(HasZicfilp, Bool())
     val topdownIssued    = OptionWrapper(backendParams.debugEn, Bool())
     val topdownCanceled  = OptionWrapper(backendParams.debugEn, Bool())
     val topdownRobHead   = OptionWrapper(backendParams.debugEn, Bool())
@@ -204,7 +207,9 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val debug_fuType = OptionWrapper(backendParams.debugEn, FuType())
     val debug_fusionNum = OptionWrapper(backendParams.debugEn, UInt(log2Ceil(RenameWidth / 2 + 1).W))
     // debug_end
-
+    // Zicfilp
+    val ZicfilpJalr = OptionWrapper(HasZicfilp, Bool())
+    val ZicfilpLPAD = OptionWrapper(HasZicfilp, Bool())
   }
 
   def connectEnq(robEntry: RobEntryBundle, robEnq: EnqRobUop)(implicit p: Parameters): Unit = {
@@ -254,6 +259,13 @@ object RobBundles extends HasCircularQueuePtrHelper {
     robEntry.topdownCancelSource.foreach(_ := IQCancelSource.none)
     robEntry.topdownCancelTimeVec.foreach(_.foreach(_ := 0.U))
     robEntry.topdownCancelTimeFixVec.foreach(_.foreach(_ := 0.U))
+    // Zicfilp
+    robEntry.ZicfilpJalr.zip(robEnq.ZicfilpInfos).foreach { case (sink, source) =>
+      sink := source.ZicfilpJalr
+    }
+    robEntry.ZicfilpLPAD.zip(robEnq.ZicfilpLPAD).foreach { case (sink, source) =>
+      sink := source
+    }
   }
 
   def connectCommitEntry(
@@ -286,6 +298,13 @@ object RobBundles extends HasCircularQueuePtrHelper {
     robCommitEntry.basicDebug.foreach(_ := robEntry.basicDebug.get)
     robCommitEntry.debug_fuType.foreach(_ := robEntry.debug_fuType.get)
     robCommitEntry.debug_fusionNum.foreach(_ := robEntry.debug_fusionNum.get)
+    // Zicfilp
+    robCommitEntry.ZicfilpJalr.zip(robEntry.ZicfilpJalr).foreach { case (sink, source) =>
+      sink := source
+    }
+    robCommitEntry.ZicfilpLPAD.zip(robEntry.ZicfilpLPAD).foreach { case (sink, source) =>
+      sink := source
+    }
   }
 }
 
diff --git a/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala b/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala
index b582bb8f1e9..44f87c63afb 100644
--- a/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala
@@ -41,7 +41,7 @@ class NewRobDeqPtrWrapper(implicit p: Parameters, params: BackendParams) extends
     val deq_w = Vec(CommitWidth, Input(Bool()))
     val hasCommitted = Vec(CommitWidth, Input(Bool()))
     val allCommitted = Input(Bool())
-    val exception_state = Flipped(ValidIO(new RobExceptionInfo(ExceptionNO.exceptionGenSet(params))))
+    val exception_state = Flipped(ValidIO(new RobExceptionInfo(ExceptionNO.exceptionGenSet(params, HasZicfilp))))
     // for flush: when exception occurs, reset deqPtrs to range(0, CommitWidth)
     val intrBitSetReg = Input(Bool())
     val allowOnlyOneCommit = Input(Bool())
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala
index 5011e0ebd57..c26cef99799 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala
@@ -2,13 +2,13 @@ package xiangshan.backend.vector.Decoder.DecodeChannel
 
 import chisel3._
 import chisel3.experimental.hierarchy.{instantiable, public}
-import chisel3.util.{BitPat, ValidIO}
+import chisel3.util.{BitPat, ValidIO, is, switch}
 import freechips.rocketchip.rocket.CSRs
 import org.chipsalliance.cde.config.Parameters
 import xiangshan.backend.decode.opcode.OpcodeTraits._
 import sourcecode.{Name => SourceName}
 import top.ArgParser
-import xiangshan.{XSCoreParameters, XSCoreParamsKey}
+import xiangshan.{HasXSParameter, XSCoreParameters, XSCoreParamsKey}
 import xiangshan.CommitType
 import xiangshan.backend.decode.isa.PseudoInstructions
 import xiangshan.backend.decode.isa.bitfield.XSInstBitFields
@@ -24,7 +24,6 @@ import xiangshan.backend.vector.util.ChiselTypeExt.{BitPatToExt, UIntToUIntField
 import xiangshan.backend.vector.util.ScalaTypeExt.BooleanToExt
 import xiangshan.backend.vector.util.Select.Mux1HLookUp
 import xiangshan.backend.vector.util.Verilog
-import xiangshan.backend.vector.HasVectorSettings
 import xiangshan.backend.decode.ImmUnion
 
 import scala.collection.SeqMap
@@ -33,7 +32,7 @@ import scala.language.implicitConversions
 @instantiable
 class PseudoDecodeChannel(
   instSeq: Seq[InstPattern] = PseudoDecodeChannel.uopTable.keys.toSeq
-)(implicit val p: Parameters) extends Module with HasVectorSettings {
+)(implicit val p: Parameters) extends Module with HasXSParameter {
   import PseudoDecodeChannel._
 
   @public
@@ -43,7 +42,7 @@ class PseudoDecodeChannel(
 
   val instBitFields: XSInstBitFields = in.rawInst.asTypeOf(new XSInstBitFields)
 
-  val patterns = instSeq
+  val patterns = if (HasZicfilp) instSeq else instSeq.filterNot(_ == InstPatterns.LPAD)
 
   val fields = Seq(
     legalField,
@@ -62,12 +61,17 @@ class PseudoDecodeChannel(
     canRobCompressField,
     isJField,
     isJrField,
+    isLPADField,
   )
 
   val table = new DecodeTable(patterns, fields)
 
   val bundle = table.decode(in.rawInst)
 
+  val lpadEnabled = if (HasZicfilp) {
+    in.fromCSR.enableZicfilp.get && in.ZicfilpELP.get && in.ZicfilpPCAligned.get
+  } else false.B
+
   val selImm = Wire(ValidIO(DecodeSelImm()))
   val imm = Wire(UInt(32.W))
 
@@ -76,18 +80,22 @@ class PseudoDecodeChannel(
     selImm.bits,
     Seq(
       DecodeSelImm.I         -> ImmUnion.I.minBitsFromInstr(in.rawInst).ensuring(_.getWidth == ImmUnion.I.len),
+      DecodeSelImm.U         -> ImmUnion.U.minBitsFromInstr(in.rawInst).ensuring(_.getWidth == ImmUnion.U.len),
       DecodeSelImm.UJ        -> ImmUnion.J.minBitsFromInstr(in.rawInst).ensuring(_.getWidth == ImmUnion.J.len),
       DecodeSelImm.CSRRVLENB -> (VLEN / 8).U,
       DecodeSelImm.S         -> ImmUnion.S.minBitsFromInstr(in.rawInst).ensuring(_.getWidth == ImmUnion.S.len),
     )
   )
 
-  out.valid := bundle(legalField)
+  out.valid := bundle(legalField) && (!bundle(isLPADField) || lpadEnabled)
   out.bits.fuType := bundle(fuTypeField)
   out.bits.opcode := bundle(opcodeField)
   out.bits.src1RenType := bundle(src1Field)
   out.bits.src2RenType := bundle(src2Field)
   out.bits.lsrc1 := instBitFields.RS1
+  switch(bundle(isLPADField)) {
+    is(true.B) { out.bits.lsrc1 := 7.U }
+  }
   out.bits.lsrc2 := instBitFields.RS2
   out.bits.ldest := instBitFields.RD
   out.bits.gpWen := bundle(gpWenField)
@@ -126,6 +134,8 @@ object PseudoDecodeChannel {
   class In(implicit p: Parameters) extends Bundle {
     val rawInst = UInt(32.W)
     val fromCSR = new CSRToDecode
+    val ZicfilpELP = Option.when(p(XSCoreParamsKey).HasZicfilp)(Bool())
+    val ZicfilpPCAligned = Option.when(p(XSCoreParamsKey).HasZicfilp)(Bool())
   }
 
   class Out extends Bundle {
@@ -161,6 +171,7 @@ object PseudoDecodeChannel {
 
     val J            = PseudoInstPattern(PseudoInstructions.J)
     val JALR_RD_ZERO = PseudoInstPattern(PseudoInstructions.JALR_RD_ZERO)
+    val LPAD         = PseudoInstPattern(PseudoInstructions.LPAD)
   }
 
   class DecodeFieldGen[-T <: InstPattern, +D <: Data](
@@ -193,6 +204,7 @@ object PseudoDecodeChannel {
     PREFETCH_W    -> (LduOpcodes.prefetch_w + Src1Gp + Src2Imm(DecodeSelImm.S) + CannotRobCompress),
     J             -> (JmpOpcodes.j),
     JALR_RD_ZERO  -> (JmpOpcodes.jr),
+    LPAD          -> JmpOpcodes.j.copy().setTraits(Src1Gp, Src2Imm(DecodeSelImm.U), CannotRobCompress),
   )
 
   val legalField = new DecodeFieldGen(
@@ -330,6 +342,11 @@ object PseudoDecodeChannel {
     (op: InstPattern) => (op == JALR_RD_ZERO).toBitPat
   )
 
+  val isLPADField = new DecodeFieldGen(
+    Bool(),
+    (op: InstPattern) => (op == LPAD).toBitPat,
+  )
+
   def makeCSRRBitPat(csrno: Int): BitPat = {
     new BitPat(csrno, 0xFFF, 12) ## BitPat("b00000_010_?????_1110011")
   }
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannels.scala b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannels.scala
index d35b93cc71e..ede0ca0ca8f 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannels.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannels.scala
@@ -8,6 +8,8 @@ import top.{ArgParser, Generator}
 import utils.BundleUtils.makeValid
 import xiangshan._
 import xiangshan.backend.Bundles.{DecodeInUopDebug, UopIdx}
+import xiangshan.backend.decode.SpecZicfilp
+import xiangshan.backend.decode.isa.{Instructions, PseudoInstructions}
 import xiangshan.backend.decode.isa.Extensions._
 import xiangshan.backend.decode.isa.bitfield.{BitFieldsVec, Riscv32BitInst}
 import xiangshan.backend.decode.opcode.Opcode
@@ -65,6 +67,7 @@ class DecodeChannels(
     val redirect = Input(Bool())
     // if rename can accept all UopWidth uops
     val renameCanAccept = Input(Bool())
+    val ZicfilpRedirect = Option.when(p(XSCoreParamsKey).HasZicfilp)(Flipped(ValidIO(Bool())))
     val mops = Flipped(Vec(mopWidth, DecoupledIO(new Bundle {
       val info = new DecodeChannelInput
       val ctrl = new MopCtrlBundle
@@ -75,6 +78,7 @@ class DecodeChannels(
     val uops = Vec(uopWidth, ValidIO(new Bundle {
       val info = new DecodeChannelOutput
       val ctrl = new MopCtrlBundle
+      val zicfilp = Option.when(p(XSCoreParamsKey).HasZicfilp)(new ZicfilpDecodeInfo)
     }))
   }))
 
@@ -82,6 +86,29 @@ class DecodeChannels(
   val insts: Seq[Riscv32BitInst with BitFieldsVec] = in.mops.map(_.bits.info.rawInst.asTypeOf(new Riscv32BitInst with BitFieldsVec))
   val inMopCtrl: Seq[MopCtrlBundle] = in.mops.map(_.bits.ctrl)
 
+  val specZicfilp = Option.when(p(XSCoreParamsKey).HasZicfilp)(Module(new SpecZicfilp(mopWidth)))
+  val decodedMopCtrl = Wire(Vec(mopWidth, new DecodedMopCtrlBundle))
+  decodedMopCtrl.zip(inMopCtrl).foreach { case (sink, source) => sink.ctrl := source }
+  specZicfilp.foreach { spec =>
+    spec.io.enable := in.mops.head.bits.info.fromCSR.enableZicfilp.get
+    spec.io.redirect := in.ZicfilpRedirect.get
+    for (i <- 0 until mopWidth) {
+      val inst = in.mops(i).bits.info.rawInst
+      val rs1 = inst(19, 15)
+      val isJalr = inst === Instructions.JALR && rs1 =/= 1.U && rs1 =/= 5.U && rs1 =/= 7.U
+      val isLPAD = inst === PseudoInstructions.LPAD && inMopCtrl(i).ZicfilpPCAligned.get
+      spec.io.valid(i) := in.mops(i).valid && !in.redirect
+      spec.io.fire(i) := in.mops(i).fire && !in.redirect
+      spec.io.isJalr(i) := isJalr
+      spec.io.isLPAD(i) := isLPAD
+      val info = decodedMopCtrl(i).zicfilp.get
+      info.info.ZicfilpJalr := isJalr
+      info.info.ZicfilpLPADValid := spec.io.lpadValid(i)
+      info.isLPAD := isLPAD
+      info.exception := spec.io.exception(i)
+    }
+  }
+
   // Instantiate vector decode channel by vector instruction patterns
   val vectorDecodeChannelM8: Definition[VectorDecodeChannel] = Definition(new VectorDecodeChannel(vectorInsts))
   val vsetDecodeChannel: Definition[VsetDecoder] = Definition(new VsetDecoder)
@@ -155,7 +182,7 @@ class DecodeChannels(
       RegEnable(next, update)
   }
 
-  val bufferedMopCtrlNext = Wire(new MopCtrlBundle)
+  val bufferedMopCtrlNext = Wire(new DecodedMopCtrlBundle)
   val bufferedMopCtrl = RegEnable(bufferedMopCtrlNext, uopBufferUpdate)
 
   val uopBufferCtrlDecoder = Module(new UopBufferCtrlDecoder(
@@ -167,7 +194,7 @@ class DecodeChannels(
 
   val uopSelectMod = Module(new UopSelectModule(
     uopBundle = new DecodeChannelOutput,
-    mopBundle = new MopCtrlBundle,
+    mopBundle = new DecodedMopCtrlBundle,
     mopWidth = mopWidth,
     uopWidth = uopWidth,
     uopBufferSize = uopBufferSize,
@@ -176,7 +203,7 @@ class DecodeChannels(
 
   val bufferSelectMod = Module(new BufferSelectModule(
     uopBundle = new DecodeChannelOutput,
-    mopBundle = new MopCtrlBundle,
+    mopBundle = new DecodedMopCtrlBundle,
     mopWidth = mopWidth,
     uopWidth = uopWidth,
     uopBufferLength = uopBufferSize,
@@ -213,6 +240,8 @@ class DecodeChannels(
   psdDecodeChannelsIn.zipWithIndex.foreach { case (modIn, i) =>
     modIn.rawInst := in.mops(i).bits.info.rawInst
     modIn.fromCSR := in.mops(i).bits.info.fromCSR
+    modIn.ZicfilpELP.foreach(_ := specZicfilp.get.io.elp(i) && !in.redirect)
+    modIn.ZicfilpPCAligned.foreach(_ := inMopCtrl(i).ZicfilpPCAligned.get)
   }
 
   /**
@@ -277,7 +306,7 @@ class DecodeChannels(
     }
   }
   uopSelectMod.in.uopFromBuffer := uopBuffer
-  uopSelectMod.in.mopFromInput := in.mops.map(_.bits.ctrl)
+  uopSelectMod.in.mopFromInput := decodedMopCtrl
   uopSelectMod.in.mopFromBuffer := bufferedMopCtrl
   uopSelectMod.in.uopSelect := uopBufferCtrlDecoder.out.selForUop
 
@@ -287,7 +316,7 @@ class DecodeChannels(
 
   bufferSelectMod.in.uopFromChannel := uopSelectMod.in.uopFromChannel
   bufferSelectMod.in.uopFromBuffer := uopBuffer
-  bufferSelectMod.in.mopFromInput := in.mops.map(_.bits.ctrl)
+  bufferSelectMod.in.mopFromInput := decodedMopCtrl
   bufferSelectMod.in.mopFromBuffer := bufferedMopCtrl
   bufferSelectMod.in.uopSelect := uopBufferCtrlDecoder.out.selForBufffer
   bufferSelectMod.in.mopAcceptVec := uopBufferCtrlDecoder.out.acceptVec.map(_ && in.renameCanAccept)
@@ -303,7 +332,8 @@ class DecodeChannels(
   for (i <- 0 until uopWidth) {
     out.uops(i).valid := uopBufferCtrlDecoder.out.uopValids(i)
     out.uops(i).bits.info := uopSelectMod.out.decodedInfoOut(i)
-    out.uops(i).bits.ctrl := uopSelectMod.out.bypassInfoOut(i)
+    out.uops(i).bits.ctrl := uopSelectMod.out.bypassInfoOut(i).ctrl
+    out.uops(i).bits.zicfilp.foreach(_ := uopSelectMod.out.bypassInfoOut(i).zicfilp.get)
   }
 }
 
@@ -650,6 +680,17 @@ object DecodeChannelOutput {
   }
 }
 
+class ZicfilpDecodeInfo(implicit p: Parameters) extends XSBundle {
+  val info = new ZicfilpInfo
+  val isLPAD = Bool()
+  val exception = Bool()
+}
+
+class DecodedMopCtrlBundle(implicit p: Parameters) extends XSBundle {
+  val ctrl = new MopCtrlBundle
+  val zicfilp = Option.when(HasZicfilp)(new ZicfilpDecodeInfo)
+}
+
 class MopCtrlBundle(implicit p: Parameters) extends XSBundle {
   val foldpc           = UInt(MemPredPCWidth.W)
   val exceptionVec     = ExceptSparseVec(ExceptionNO.fromFrontendSet)
@@ -665,6 +706,7 @@ class MopCtrlBundle(implicit p: Parameters) extends XSBundle {
   val vtype            = VType()
   val oldVType         = VType()
   val rawInst          = UInt(32.W)
+  val ZicfilpPCAligned = Option.when(HasZicfilp)(Bool())
   val debug            = Option.when(backendParams.debugEn)(new DecodeInUopDebug())
 }
 
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeStage.scala b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeStage.scala
index 3fec52b97ca..9311df8cf68 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeStage.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeStage.scala
@@ -109,6 +109,7 @@ class DecodeStageImp(
         ctrl.vtype            := inMopBits.vtype
         ctrl.oldVType         := inMopBits.specvtype
         ctrl.rawInst          := inMopBits.instr
+        ctrl.ZicfilpPCAligned.foreach(_ := inMopBits.ZicfilpPCAligned.get)
         ctrl.debug.foreach(_  := inMopBits.debug.get)
     }
   }
@@ -118,6 +119,8 @@ class DecodeStageImp(
       mop.ready := decodeChannels.in.mops(i).ready
   }
 
+  decodeChannels.in.ZicfilpRedirect.foreach(_ := in.ZicfilpRedirect.get)
+
   out.uop.zipWithIndex.foreach { case (uop, i) =>
     val coUop = decodeChannels.out.uops(i)
     val mopInfo = coUop.bits.ctrl
@@ -188,6 +191,11 @@ class DecodeStageImp(
         bits.isJ := uopInfo.isJ
         bits.isJr := uopInfo.isJr
         bits.numWB := uopInfo.numWb +& 1.U
+        coUop.bits.zicfilp.foreach { info =>
+          bits.ZicfilpInfos.get := info.info
+          bits.ZicfilpLPAD.get := info.isLPAD
+          bits.exceptionVec(softwareCheck) := info.exception && uopInfo.isFirstUop
+        }
         bits.latency := LatDecoder(bits.fuType, bits.fuOpType)
         bits.debug.foreach{ x =>
           x.pc := mopInfo.debug.get.pc
@@ -227,6 +235,7 @@ class DecodeStageImp(
 object DecodeStage {
   class In(implicit p: Parameters) extends XSBundle {
     val redirect = Input(ValidIO(new Redirect))
+    val ZicfilpRedirect = Option.when(HasZicfilp)(Input(ValidIO(Bool())))
 
     // The ready of mop means this mop is accepted by DecodeStage
     // Ready signal depends on valid
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index fd4b9d2912d..c3ae9291911 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -800,7 +800,7 @@ package object xiangshan {
       hardwareError,
       instrGuestPageFault
     )
-    def decodeSet = Seq(
+    private def baseDecodeSet: Seq[Int] = Seq(
       instrAccessFault,
       illegalInstr,
       breakPoint, // new
@@ -809,7 +809,14 @@ package object xiangshan {
       instrGuestPageFault,
       virtualInstr, // new
     )
-    def exceptionGenSet(params: BackendParams) = (params.exceptionOut ++ decodeSet).distinct.sorted
+    def decodeSet: Seq[Int] = baseDecodeSet
+    def decodeSet(hasZicfilp: Boolean): Seq[Int] = {
+      if (hasZicfilp) (baseDecodeSet :+ softwareCheck).distinct.sorted else baseDecodeSet
+    }
+    def exceptionGenSet(params: BackendParams): Seq[Int] = exceptionGenSet(params, false)
+    def exceptionGenSet(params: BackendParams, hasZicfilp: Boolean): Seq[Int] = {
+      (params.exceptionOut(hasZicfilp) ++ decodeSet(hasZicfilp)).distinct.sorted
+    }
   }
 
   object TopDownCounters extends Enumeration {
```
