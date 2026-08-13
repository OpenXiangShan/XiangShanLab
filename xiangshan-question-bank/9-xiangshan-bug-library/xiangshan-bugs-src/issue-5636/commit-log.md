# Commit Log
- Issue: #5636
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5636
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5636
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5636
- Changed files: 15
- Additions: 270
- Deletions: 205

## Files
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/issue/EnqEntry.scala`
- `src/main/scala/xiangshan/backend/issue/Entries.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/backend/issue/OthersEntry.scala`
- `src/main/scala/xiangshan/backend/regfile/Regfile.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 48fdd96b712..2f200a6c9d9 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -328,11 +328,11 @@ case class XSCoreParameters
       IssueBlockParams(Seq(
         ExeUnitParams("ALU0", Seq(AluCfg, CsrCfg, FenceCfg), Seq(IntWB(port = 0, 0)), Seq(Seq(IntRD(0, 0)), Seq(IntRD(1, 0))), true, 2),
         ExeUnitParams("BJU0", Seq(BrhCfg, JmpCfg), Seq(), Seq(Seq(IntRD(1, 1)), Seq(IntRD(7, 2))))
-      ), numEntries = IssueQueueSize, numEnq = 2, numComp = IssueQueueCompEntrySize),
+      ), numEntries = 18, numEnq = 2, numComp = 10),
       IssueBlockParams(Seq(
         ExeUnitParams("ALU1", Seq(AluCfg, DivCfg), Seq(IntWB(port = 1, 0)), Seq(Seq(IntRD(2, 0)), Seq(IntRD(3, 0))), true, 2),
         ExeUnitParams("BJU1", Seq(BrhCfg, JmpCfg), Seq(), Seq(Seq(IntRD(3, 1)), Seq(IntRD(9, 2))))
-      ), numEntries = IssueQueueSize, numEnq = 2, numComp = IssueQueueCompEntrySize),
+      ), numEntries = 18, numEnq = 2, numComp = 10),
       IssueBlockParams(Seq(
         ExeUnitParams(
           "ALU2",
@@ -344,7 +344,7 @@ case class XSCoreParameters
           vlWB = VlWB(port = intSchdVlWbPort, 0),
         ),
         ExeUnitParams("BJU2", Seq(BrhCfg, JmpCfg), Seq(), Seq(Seq(IntRD(5, 1)), Seq(IntRD(11, 2))))
-      ), numEntries = IssueQueueSize, numEnq = 2, numComp = IssueQueueCompEntrySize),
+      ), numEntries = 18, numEnq = 2, numComp = 10),
       IssueBlockParams(Seq(
         ExeUnitParams("ALU3", Seq(AluCfg, BkuCfg), Seq(IntWB(port = 3, 0)), Seq(Seq(IntRD(6, 0)), Seq(IntRD(7, 1))), true, 2)
       ), numEntries = IssueQueueSize, numEnq = 2, numComp = IssueQueueCompEntrySize),
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index b017be839b2..9cc6659423c 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -234,8 +234,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   ctrlBlock.io.toDispatch.wakeUpVec := vecRegion.io.wakeUpToDispatch
   ctrlBlock.io.toDispatch.IQValidNumVec := intRegion.io.IQValidNumVec ++ fpRegion.io.IQValidNumVec ++ vecRegion.io.IQValidNumVec
   ctrlBlock.io.toDispatch.ldCancel := io.mem.ldCancel
-  val og0Cancel = (intRegion.io.og0Cancel.asUInt | fpRegion.io.og0Cancel.asUInt | vecRegion.io.og0Cancel.asUInt).asBools
-  ctrlBlock.io.toDispatch.og0Cancel := og0Cancel
+  ctrlBlock.io.toDispatch.og0Cancel := intRegion.io.og0Cancel
   ctrlBlock.io.toDispatch.wbPregsInt.zip(intRegion.io.toIntPreg).map(x => {
     x._1.valid := x._2.wen && x._2.rfWen
     x._1.bits := x._2.pdest
@@ -387,6 +386,11 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   intRegion.io.fpRfRdataIn.get := fpRegion.io.fpRfRdataOut.get
   // for fpIQ write int regfile arbiter
   intRegion.io.fromFpIQ.get <> fpRegion.io.fpIQOut.get
+  // for vecIQ read int/fp regfile
+  vecRegion.io.fromIntIQ.get <> intRegion.io.intIQOut.get
+  vecRegion.io.fromFpIQ.get <> fpRegion.io.fpIQOut.get
+  intRegion.io.fromVecIQ.get <> vecRegion.io.vecIQOut.get
+  fpRegion.io.fromVecIQ.get <> vecRegion.io.vecIQOut.get
 
   vecRegion.io.diffVlRat.foreach(_ := ctrlBlock.io.diff_vl_rat.get)
   vecRegion.io.fromVecExcpMod.get.r := vecExcpMod.o.toVPRF.r
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index a2270d2c7df..f43e5397850 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -432,14 +432,14 @@ object Bundles {
     val useRegCache       = Vec(backendParams.numIntRegSrc, Bool())
     val regCacheIdx       = Vec(backendParams.numIntRegSrc, UInt(RegCacheIdxWidth.W))
     val srcStateVl        = Option.when(params.readVlRf)(SrcState())
-    val lqIdx             = Option.when(params.isLdAddrIQ || params.isVecMemIQ)(new LqPtr)
-    val sqIdx             = Option.when(params.isLdAddrIQ || params.isStAddrIQ || params.isStdIQ || params.isVecMemIQ)(new SqPtr) // load unit need sqIdx
+    val lqIdx             = Option.when(params.needLqIdx)(new LqPtr)
+    val sqIdx             = Option.when(params.needSqIdx)(new SqPtr) // load unit need sqIdx
     // cas ctrl
     val isDropAmocasSta = Bool()
     val debug = OptionWrapper(backendParams.debugEn, new IssueQueueInDebug)
   }
 
-  class Og1Payload(val params: IssueBlockParams)(implicit p: Parameters) extends XSBundle {
+  class EntryOg1Payload(val params: IssueBlockParams)(implicit p: Parameters) extends XSBundle {
     def numSrc = params.numSrc
 
     // from frontend
@@ -465,13 +465,50 @@ object Bundles {
     val loadWaitStrict = Option.when(params.isLdAddrIQ)(Bool())
     val ssid           = Option.when(params.isLdAddrIQ || params.isStAddrIQ)(UInt(SSIDWidth.W))
     // from dispatch
-    val lqIdx = Option.when(params.isLdAddrIQ || params.isVecMemIQ)(new LqPtr)
-    val sqIdx = Option.when(params.isStAddrIQ || params.isStdIQ || params.isVecMemIQ || params.isLdAddrIQ)(new SqPtr) // load unit need sqIdx
+    val lqIdx = Option.when(params.needLqIdx)(new LqPtr)
+    val sqIdx = Option.when(params.needSqIdx)(new SqPtr) // load unit need sqIdx
+  }
+
+  class IssueQueueDeqOg1Payload(val params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
+    def numSrc = params.numSrc
+    // from frontend
+    val isRVC      = Option.when(params.needIsRVC || params.aluNeedPc)(Bool())
+    val fixedTaken = Option.when(params.needTaken)(Bool())
+    val predTaken  = Option.when(params.needTaken)(Bool())
+    // from decode
+    val fuOpType = FuOpType()
+    val selImm   = Option.when(params.needImm)(SelImm())
+    val imm      = Option.when(params.needImm)(UInt((params.deqImmTypesMaxLen).W))
+    val fpu      = Option.when(params.writeFflags)(new FPUCtrlSignals)
+    val vpu      = Option.when(params.issueBlockParam.inVfSchd)(new VPUCtrlSignals)
+    val wfflags  = Option.when(params.writeFflags)(Bool())
+    val uopIdx   = Option.when(params.issueBlockParam.inVfSchd)(UopIdx())
+    val lastUop  = Option.when(params.issueBlockParam.inVfSchd)(Bool())
+    // from rename
+    val numLsElem = Option.when(params.issueBlockParam.isVecMemIQ)(NumLsElem())
+    val rasAction = Option.when(params.needRasAction)(BranchAttribute.RasAction())
+    // for mdp
+    val storeSetHit    = Option.when(params.issueBlockParam.isLdAddrIQ || params.issueBlockParam.isStAddrIQ)(Bool())
+    val waitForRobIdx  = Option.when(params.issueBlockParam.isLdAddrIQ)(new RobPtr)
+    val loadWaitBit    = Option.when(params.issueBlockParam.isLdAddrIQ)(Bool())
+    val loadWaitStrict = Option.when(params.issueBlockParam.isLdAddrIQ)(Bool())
+    val ssid           = Option.when(params.issueBlockParam.isLdAddrIQ || params.issueBlockParam.isStAddrIQ)(UInt(SSIDWidth.W))
+    // from dispatch
+    val lqIdx = Option.when(params.issueBlockParam.needLqIdx)(new LqPtr)
+    val sqIdx = Option.when(params.issueBlockParam.needSqIdx)(new SqPtr) // load unit need sqIdx
+    // for read regfile, rf and rfVl are only for param config
+    private val rfReadDataCfgSet: Seq[Set[DataConfig]] = params.getRfReadDataCfgSet
+    val rf: MixedVec[MixedVec[RfReadPortWithConfig]] = MixedVec(
+      rfReadDataCfgSet.map((set: Set[DataConfig]) =>
+        MixedVec(set.map((x: DataConfig) => new RfReadPortWithConfig(x, params.rdPregIdxWidth)).toSeq)
+      )
+    )
+    val rfVl = Option.when(params.readVlRf)(new RfReadPortWithConfig(VlData(), params.backendParam.getPregParams(VlData()).addrWidth))
   }
 
   class IssueQueuePayload(val params: IssueBlockParams)(implicit p: Parameters) extends XSBundle {
     def numSrc = params.numSrc
-    val og1Payload = new Og1Payload(params)
+    val og1Payload = new EntryOg1Payload(params)
     // from frontend
     val ftqPtr     = Option.when(params.needFtqPtr)(new FtqPtr)
     val ftqOffset  = Option.when(params.needFtqPtrOffset)(UInt(FetchBlockInstOffsetWidth.W))
@@ -484,7 +521,6 @@ object Bundles {
     val v0Wen    = Option.when(params.needV0Wen )(Bool())
     val vlWen    = Option.when(params.needVlWen )(Bool())
     // from rename
-    val psrc      = Vec(numSrc, UInt(PhyRegIdxWidth.W))
     val pdest     = UInt(PhyRegIdxWidth.W)
     val pdestVl   = Option.when(params.writeVlRf)(UInt(VlPhyRegIdxWidth.W))
     // from dispatch
@@ -886,22 +922,16 @@ object Bundles {
                                   val iqParams: IssueBlockParams,
                                   val exuParams: ExeUnitParams,
                                 )(implicit p: Parameters) extends XSBundle {
-    private val rfReadDataCfgSet: Seq[Set[DataConfig]] = exuParams.getRfReadDataCfgSet
-
-    val rf: MixedVec[MixedVec[RfReadPortWithConfig]] = Flipped(MixedVec(
-      rfReadDataCfgSet.map((set: Set[DataConfig]) =>
-        MixedVec(set.map((x: DataConfig) => new RfReadPortWithConfig(x, exuParams.rdPregIdxWidth)).toSeq)
-      )
-    ))
-
-    val rfVl = Option.when(exuParams.readVlRf)(new RfReadPortWithConfig(VlData(), iqParams.backendParam.getPregParams(VlData()).addrWidth))
-
-    val srcType        = Vec(exuParams.numRegSrc, SrcType()) // used to select imm or reg data
     val rcIdx          = Option.when(exuParams.needReadRegCache)(Vec(exuParams.numRegSrc, UInt(RegCacheIdxWidth.W))) // used to select regcache data
     val fuType         = FuType()
     val robIdx         = new RobPtr
     val iqIdx          = UInt(log2Up(iqParams.numEntries).W)
     val isFirstIssue   = Bool()
+    val rfRen          = Option.when(exuParams.readIntRf)(Vec(exuParams.numRegSrc, Bool()))
+    val fpRen          = Option.when(exuParams.readFpRf )(Vec(exuParams.numRegSrc, Bool()))
+    val vecRen         = Option.when(exuParams.readVecRf)(Vec(exuParams.numRegSrc, Bool()))
+    val v0Ren          = Option.when(exuParams.readV0Rf )(Vec(exuParams.numRegSrc, Bool()))
+    val vlRen          = Option.when(exuParams.readVlRf )(Bool())
     val rfWen          = Option.when(exuParams.needIntWen)(Bool())
     val fpWen          = Option.when(exuParams.needFpWen )(Bool())
     val vecWen         = Option.when(exuParams.needVecWen)(Bool())
@@ -912,34 +942,31 @@ object Bundles {
     val flushPipe      = Option.when(exuParams.flushPipe)    (Bool())
     val ftqIdx         = Option.when(exuParams.needFtqPtr)   (new FtqPtr)
     val ftqOffset      = Option.when(exuParams.needFtqPtrOffset)(UInt(FetchBlockInstOffsetWidth.W))
+    // psrc are used in datapath to generate regfile's bank Ren
+    val psrc           = Vec(exuParams.numRegSrc, UInt(exuParams.rdPregIdxWidth.W))
+    val psrcVl         = Option.when(exuParams.readVlRf)(UInt(VlPhyRegIdxWidth.W))
+    // dataSources are used in issueQueue to generate regfile Ren
     val dataSources    = Vec(exuParams.numRegSrc, DataSource())
     val exuSources     = Option.when(exuParams.isIQWakeUpSink)(Vec(exuParams.numRegSrc, ExuSource(exuParams)))
     val loadDependency = OptionWrapper(exuParams.needLoadDependency, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
 
     val perfDebugInfo = OptionWrapper(backendParams.debugEn, new PerfDebugInfo())
     val debug_seqNum  = OptionWrapper(backendParams.debugEn, InstSeqNum())
-
-    def getRfReadValidBundle(issueValid: Bool): Seq[ValidIO[RfReadPortWithConfig]] = {
-      rf.zip(srcType).map {
-        case (rfRd: MixedVec[RfReadPortWithConfig], t: UInt) =>
-          makeValid(issueValid, rfRd.head)
-      }.toSeq
-    }
-
-    def genVlRdReadValidBundle(issueValid: Bool): Option[ValidIO[RfReadPortWithConfig]] = {
-      rfVl.map(x => makeValid(issueValid, x))
-    }
   }
 
   class Og1InUop(
                   val iqParams: IssueBlockParams,
                   val exuParams: ExeUnitParams,
                 )(implicit p: Parameters) extends XSBundle {
-    val srcType        = Vec(exuParams.numRegSrc, SrcType()) // used to select imm or reg data
     val fuType         = FuType()
     val robIdx         = new RobPtr
     val iqIdx          = UInt(log2Up(iqParams.numEntries).W)
     val isFirstIssue   = Bool()
+    val rfRen          = Option.when(exuParams.readIntRf)(Vec(exuParams.numRegSrc, Bool()))
+    val fpRen          = Option.when(exuParams.readFpRf )(Vec(exuParams.numRegSrc, Bool()))
+    val vecRen         = Option.when(exuParams.readVecRf)(Vec(exuParams.numRegSrc, Bool()))
+    val v0Ren          = Option.when(exuParams.readV0Rf )(Vec(exuParams.numRegSrc, Bool()))
+    val vlRen          = Option.when(exuParams.readVlRf )(Bool())
     val rfWen          = Option.when(exuParams.needIntWen)(Bool())
     val fpWen          = Option.when(exuParams.needFpWen )(Bool())
     val vecWen         = Option.when(exuParams.needVecWen)(Bool())
@@ -953,13 +980,12 @@ object Bundles {
     val dataSources    = Vec(exuParams.numRegSrc, DataSource())
     val exuSources     = Option.when(exuParams.isIQWakeUpSink)(Vec(exuParams.numRegSrc, ExuSource(exuParams)))
     val loadDependency = OptionWrapper(exuParams.needLoadDependency, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
-
-    val isRVC          = Option.when(exuParams.hasIsRVC || exuParams.aluNeedPc)(Bool())
+    val isRVC          = Option.when(exuParams.needIsRVC || exuParams.aluNeedPc)(Bool())
     val fixedTaken     = Option.when(exuParams.needTaken)(Bool())
     val predTaken      = Option.when(exuParams.needTaken)(Bool())
     val fuOpType       = FuOpType()
     val selImm         = Option.when(exuParams.needImm)(SelImm())
-    val imm            = Option.when(exuParams.needImm)(UInt(iqParams.deqImmTypesMaxLen.W))
+    val imm            = Option.when(exuParams.needImm)(UInt(exuParams.deqImmTypesMaxLen.W))
     val fpu      = Option.when(exuParams.writeFflags)(new FPUCtrlSignals)
     val vpu      = Option.when(iqParams.inVfSchd)(new VPUCtrlSignals)
     val wfflags  = Option.when(exuParams.writeFflags)(Bool())
@@ -970,8 +996,8 @@ object Bundles {
     val loadWaitBit    = Option.when(iqParams.isLdAddrIQ)(Bool())
     val loadWaitStrict = Option.when(iqParams.isLdAddrIQ)(Bool())
     val ssid           = Option.when(iqParams.isLdAddrIQ || iqParams.isStAddrIQ)(UInt(SSIDWidth.W))
-    val lqIdx = Option.when(iqParams.isLdAddrIQ || iqParams.isVecMemIQ)(new LqPtr)
-    val sqIdx = Option.when(iqParams.isStAddrIQ || iqParams.isStdIQ || iqParams.isVecMemIQ || iqParams.isLdAddrIQ)(new SqPtr)
+    val lqIdx          = Option.when(iqParams.needLqIdx)(new LqPtr)
+    val sqIdx          = Option.when(iqParams.needSqIdx)(new SqPtr)
 
     val src = Vec(exuParams.numRegSrc, UInt(exuParams.srcDataBitsMax.W))
     val vl  = Option.when(exuParams.readVlRf)(Vl())
@@ -1084,7 +1110,7 @@ object Bundles {
     val flushPipe     = if (params.flushPipe)     Some(Bool())                        else None
     val rasAction     = if (params.hasRasAction)  Some(BranchAttribute.RasAction())   else None
     val pc            = if (params.needPc || params.aluNeedPc)        Some(UInt(VAddrData().dataWidth.W)) else None
-    val isRVC         = if (params.hasIsRVC || params.aluNeedPc)      Some(Bool())                        else None
+    val isRVC         = if (params.needIsRVC || params.aluNeedPc)      Some(Bool())                        else None
     val ftqIdx        = if (params.needFtqPtr)    Some(new FtqPtr)                    else None
     val ftqOffset     = if (params.needFtqPtrOffset) Some(UInt(FetchBlockInstOffsetWidth.W))  else None
     val predictInfo   = if (params.needPdInfo)  Some(new PredictInfo) else None
@@ -1126,7 +1152,7 @@ object Bundles {
       this.vialuCtrl     .foreach(_ := 0.U.asTypeOf(new VIAluCtrlSignals))
     }
 
-    def fromIssueOg1PayloadBundle(source: Og1Payload): Unit = {
+    def fromIssueOg1PayloadBundle(source: EntryOg1Payload): Unit = {
       this.isRVC         .foreach(_ := source.isRVC.get)
       this.predictInfo.foreach(_.fixedTaken := source.fixedTaken.get)
       this.predictInfo.foreach(_.predTaken  := source.predTaken.get)
@@ -1201,7 +1227,7 @@ object Bundles {
     val vialuCtrl      = Option.when(params.needVIaluCtrl)(new VIAluCtrlSignals)
     val flushPipe      = Option.when(params.flushPipe)(Bool())
     val rasAction      = Option.when(params.hasRasAction)(BranchAttribute.RasAction())
-    val isRVC          = Option.when(params.hasIsRVC || params.aluNeedPc)(Bool())
+    val isRVC          = Option.when(params.needIsRVC || params.aluNeedPc)(Bool())
     val ftqIdx         = Option.when(params.needFtqPtr)(new FtqPtr)
     val ftqOffset      = Option.when(params.needFtqPtrOffset)(UInt(FetchBlockInstOffsetWidth.W))
     val predictInfo    = Option.when(params.needPdInfo)(new PredictInfo)
@@ -1292,7 +1318,7 @@ object Bundles {
                                                 Some(new SqPtr())             else None
     val trigger      = if (params.trigger)      Some(TriggerAction())         else None
     // uop info
-    val isRVC        = if(params.hasIsRVC)      Some(Bool())                  else None
+    val isRVC        = if(params.needIsRVC)      Some(Bool())                  else None
     // vldu used only
     val vls = OptionWrapper(params.hasVLoadFu, new Bundle {
       val vpu = new VPUCtrlSignals
@@ -1334,7 +1360,7 @@ class ExuOutputVLoad(val params: ExeUnitParams)(implicit val p: Parameters) exte
     val exceptionVec = Option.when(params.exceptionOut.nonEmpty)(ExceptionVec())
     val flushPipe    = Option.when(params.flushPipe)(Bool())
     val trigger      = Option.when(params.trigger)(TriggerAction())
-    val isRVC        = Option.when(params.hasIsRVC)(Bool())
+    val isRVC        = Option.when(params.needIsRVC)(Bool())
     val replay       = Option.when(params.replayInst)(Bool())
     val lqIdx        = Option.when(params.hasLoadFu)(new LqPtr())
     val sqIdx        = Option.when(params.hasStoreAddrFu || params.hasStdFu)(new SqPtr())
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index 29bff123ce1..cad02c4c316 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -315,6 +315,9 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     x.valid := false.B
     x.bits := 0.U.asTypeOf(x.bits)
   })
+  dataPath.io.fromIntIQDeqOg1Payload := 0.U.asTypeOf(dataPath.io.fromIntIQDeqOg1Payload)
+  dataPath.io.fromFpIQDeqOg1Payload  := 0.U.asTypeOf(dataPath.io.fromFpIQDeqOg1Payload )
+  dataPath.io.fromVecIQDeqOg1Payload := 0.U.asTypeOf(dataPath.io.fromVecIQDeqOg1Payload)
   val dataPathToExus = (dataPath.io.toIntExu ++ dataPath.io.toFpExu ++ dataPath.io.toVecExu).flatten
   dataPathToExus.map(x => {
     x.ready := false.B
@@ -421,7 +424,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
         source.io.deqDelay(i).ready := s.ready && iqOut(i).ready
       }
     }
-    dataPath.io.fromIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
+    dataPath.io.fromIntIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
       sink.zipWithIndex.map { case (s, i) =>
         s := source.io.deqOg1Payload(i)
       }
@@ -430,7 +433,9 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     dataPath.io.fromFpIQ.zip(io.fromFpIQ.get).map { case (sink, source) =>
       sink <> source
     }
-    io.intToFpIQResp.get := dataPath.io.toFpIQ
+    dataPath.io.fromVfIQ.zip(io.fromVecIQ.get).map { case (sink, source) =>
+      sink <> source
+    }
     dataPath.io.fromIntWb.get := wbDataPath.io.toIntPreg
     dataPath.io.fromPcTargetMem <> io.fromPcTargetMem.get
     dataPath.io.fromBypassNetwork := bypassNetwork.io.toDataPath
@@ -526,6 +531,10 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
       sink.bits := source.bits
     }
     dataPath.io.ldCancel := io.ldCancel
+    // for read fp regfile
+    dataPath.io.fromIntIQ.zip(io.fromIntIQ.get).map { case (sink, source) =>
+      sink <> source
+    }
     dataPath.io.fromFpIQ.zip(issueQueues).zip(io.fpIQOut.get).map { case ((sink, source), iqOut) =>
       sink.zipWithIndex.map { case (s, i) =>
         s.valid := source.io.deqDelay(i).valid
@@ -535,16 +544,14 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
         source.io.deqDelay(i).ready := s.ready && iqOut(i).ready
       }
     }
-    // for read fp regfile and resp
-    dataPath.io.fromIntIQ.zip(io.fromIntIQ.get).map { case (sink, source) =>
-      sink <> source
-    }
-    dataPath.io.fromIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
+    dataPath.io.fromFpIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
       sink.zipWithIndex.map { case (s, i) =>
         s := source.io.deqOg1Payload(i)
       }
     }
-    io.fpToIntIQResp.get := dataPath.io.toIntIQ
+    dataPath.io.fromVfIQ.zip(io.fromVecIQ.get).map { case (sink, source) =>
+      sink <> source
+    }
     dataPath.io.fromFpWb.get := wbDataPath.io.toFpPreg
     dataPath.io.fromBypassNetwork <> bypassNetwork.io.toDataPath
     io.toFpPreg := wbDataPath.io.toFpPreg
@@ -620,11 +627,23 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     io.toV0Preg := wbDataPath.io.toV0Preg
     io.toVlPreg := wbDataPath.io.toVlPreg
     io.toVecExcpMod.foreach(_ := dataPath.io.toVecExcpMod.get)
-
-    dataPath.io.fromVfIQ.zip(issueQueues).map { case (sink, source) =>
-      sink <> source.io.deqDelay
+    // for read int/fp regfile
+    dataPath.io.fromIntIQ.zip(io.fromIntIQ.get).map { case (sink, source) =>
+      sink <> source
+    }
+    dataPath.io.fromFpIQ.zip(io.fromFpIQ.get).map { case (sink, source) =>
+      sink <> source
+    }
+    dataPath.io.fromVfIQ.zip(issueQueues).zip(io.vecIQOut.get).map { case ((sink, source), iqOut) =>
+      sink.zipWithIndex.map { case (s, i) =>
+        s.valid := source.io.deqDelay(i).valid
+        iqOut(i).valid := source.io.deqDelay(i).valid
+        s.bits := source.io.deqDelay(i).bits
+        iqOut(i).bits := source.io.deqDelay(i).bits
+        source.io.deqDelay(i).ready := s.ready && iqOut(i).ready
+      }
     }
-    dataPath.io.fromIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
+    dataPath.io.fromVecIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
       sink.zipWithIndex.map { case (s, i) =>
         s := source.io.deqOg1Payload(i)
       }
@@ -847,15 +866,15 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
   val fromFpExuBlockOut = Option.when(params.isIntSchd)(Flipped(fpSchdParam.genNewExuOutputDecoupledBundle))
   // to read fp regfile
   val intIQOut  = Option.when(params.isIntSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
-  val fromIntIQ = Option.when(params.isFpSchd)(Flipped(MixedVec(intSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
-  val fpToIntIQResp = Option.when(params.isFpSchd)(MixedVec(intSchdParam.issueBlockParams.map(_.genOGRespBundle)))
+  val fromIntIQ = Option.when(params.isFpSchd || params.isVecSchd)(Flipped(MixedVec(intSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
   // fp regfile read data
   val fpRfRdataIn = Option.when(params.isIntSchd)(Input(Vec(backendParams.numPregRd(FpData()), UInt(backendParams.fpSchdParams.get.rfDataWidth.W))))
   val fpRfRdataOut = Option.when(params.isFpSchd)(Output(Vec(backendParams.numPregRd(FpData()), UInt(backendParams.fpSchdParams.get.rfDataWidth.W))))
   // to write int regfile
   val fpIQOut = Option.when(params.isFpSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
-  val fromFpIQ = Option.when(params.isIntSchd)(Flipped(MixedVec(fpSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
-  val intToFpIQResp = Option.when(params.isIntSchd)(MixedVec(fpSchdParam.issueBlockParams.map(_.genOGRespBundle)))
+  val fromFpIQ = Option.when(params.isIntSchd || params.isVecSchd)(Flipped(MixedVec(fpSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
+  val vecIQOut = Option.when(params.isVecSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
+  val fromVecIQ = Option.when(params.isIntSchd || params.isFpSchd)(Flipped(MixedVec(vecSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
   // TopDown
   val uopTopDown = new UopTopDown
 }
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 80df37fd772..87cecb118a0 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -38,6 +38,8 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   // just refences for convience
   private val fromIQ: Seq[MixedVec[DecoupledIO[Og0InUop]]] = (fromIntIQ ++ fromFpIQ ++ fromVfIQ).toSeq
 
+  private val fromIQDeqOg1Payload: Seq[MixedVec[IssueQueueDeqOg1Payload]] = (io.fromIntIQDeqOg1Payload ++ io.fromFpIQDeqOg1Payload ++ io.fromVecIQDeqOg1Payload).toSeq
+
   private val toIQs = toIntIQ ++ toFpIQ ++ toVfIQ
 
   private val toExu: Seq[MixedVec[DecoupledIO[Og1InUop]]] = (toIntExu ++ toFpExu ++ toVfExu).toSeq
@@ -96,11 +98,17 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   private val vfRdNotBlock: Seq2[Bool] = vfRdArbWinner.map(_.map(_.asUInt.andR))
   private val v0RdNotBlock: Seq2[Bool] = v0RdArbWinner.map(_.map(_.asUInt.andR))
 
-  private val intRFReadReq: Seq3[ValidIO[RfReadPortWithConfig]] = fromIQ.map(x => x.map(xx => xx.bits.getRfReadValidBundle(xx.valid)).toSeq).toSeq
-  private val fpRFReadReq: Seq3[ValidIO[RfReadPortWithConfig]] = fromIQ.map(x => x.map(xx => xx.bits.getRfReadValidBundle(xx.valid)).toSeq).toSeq
-  private val vfRFReadReq: Seq3[ValidIO[RfReadPortWithConfig]] = fromIQ.map(x => x.map(xx => xx.bits.getRfReadValidBundle(xx.valid)).toSeq).toSeq
-  private val v0RFReadReq: Seq3[ValidIO[RfReadPortWithConfig]] = fromIQ.map(x => x.map(xx => xx.bits.getRfReadValidBundle(xx.valid)).toSeq).toSeq
-  private val vlRFReadReq: Seq2[Option[ValidIO[RfReadPortWithConfig]]] = fromIQ.map(x => x.map(xx => xx.bits.genVlRdReadValidBundle(xx.valid)).toSeq).toSeq
+  private val intRFReadReq: Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
+  private val fpRFReadReq : Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
+  private val vecRFReadReq: Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
+  private val v0RFReadReq : Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
+  private val vlRFReadReq : Seq2[Option[RfReadPortWithConfig]] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rfVl)).toSeq
+
+  private val intRFRen: Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.rfRen).toSeq)
+  private val fpRFRen : Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.fpRen).toSeq)
+  private val vecRFRen: Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.vecRen).toSeq)
+  private val v0RFRen : Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.v0Ren).toSeq)
+  private val vlRFRen : Seq2[Option[Bool]]      = fromIQ.map(x => x.map(xx => xx.bits.vlRen)).toSeq
 
   private val allDataSources: Seq[Seq[Vec[DataSource]]] = fromIQ.map(x => x.map(xx => xx.bits.dataSources).toSeq)
   private val allNumRegSrcs: Seq[Seq[Int]] = fromIQ.map(x => x.map(xx => xx.bits.exuParams.numRegSrc).toSeq)
@@ -110,11 +118,14 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
       val srcIndices: Seq[Int] = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
         if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
-          arbInSeq(srcIdx).valid := inRFReadReqSeq(srcIdx).valid && allDataSources(iqIdx)(exuIdx)(srcIdx).readReg
-          arbInSeq(srcIdx).bits.addr := inRFReadReqSeq(srcIdx).bits.addr
-          arbInSeq(srcIdx).bits.robIdx := inRFReadReqSeq(srcIdx).bits.robIdx
-          arbInSeq(srcIdx).bits.issueValid := inRFReadReqSeq(srcIdx).valid
+          arbInSeq(srcIdx).valid := intRFRen(iqIdx)(exuIdx).get(srcIdx)
+          arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
+          arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
+          arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
         } else {
+          println(s"inRFReadReqSeq.size = ${inRFReadReqSeq.size}")
+          println(s"srcIndices.contains(srcIdx) = ${srcIndices.contains(srcIdx)}, inRFReadReqSeq.isDefinedAt(srcIdx) = ${inRFReadReqSeq.isDefinedAt(srcIdx)}")
+          println(s"iqIdx = $iqIdx, exuIdx = $exuIdx, srcIdx = $srcIdx, ${fromIQDeqOg1Payload(iqIdx)(exuIdx).params.name}")
           arbInSeq(srcIdx).valid := false.B
           arbInSeq(srcIdx).bits := 0.U.asTypeOf(arbInSeq(srcIdx).bits)
         }
@@ -126,10 +137,10 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
       val srcIndices: Seq[Int] = FpRegSrcDataSet.flatMap(data => fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(data)).toSeq.sorted
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
         if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
-          arbInSeq(srcIdx).valid := inRFReadReqSeq(srcIdx).valid && allDataSources(iqIdx)(exuIdx)(srcIdx).readReg
-          arbInSeq(srcIdx).bits.addr := inRFReadReqSeq(srcIdx).bits.addr
-          arbInSeq(srcIdx).bits.robIdx := inRFReadReqSeq(srcIdx).bits.robIdx
-          arbInSeq(srcIdx).bits.issueValid := inRFReadReqSeq(srcIdx).valid
+          arbInSeq(srcIdx).valid := fpRFRen(iqIdx)(exuIdx).get(srcIdx)
+          arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
+          arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
+          arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
         } else {
           arbInSeq(srcIdx).valid := false.B
           arbInSeq(srcIdx).bits := 0.U.asTypeOf(arbInSeq(srcIdx).bits)
@@ -138,15 +149,15 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
     }
   }
 
-  vfRFReadArbiter.io.in.zip(vfRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq2), iqIdx) =>
+  vfRFReadArbiter.io.in.zip(vecRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq2), iqIdx) =>
     arbInSeq2.zip(inRFReadReqSeq2).zipWithIndex.foreach { case ((arbInSeq, inRFReadReqSeq), exuIdx) =>
       val srcIndices: Seq[Int] = VecRegSrcDataSet.flatMap(data => fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(data)).toSeq.sorted
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
         if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
-          arbInSeq(srcIdx).valid := inRFReadReqSeq(srcIdx).valid && allDataSources(iqIdx)(exuIdx)(srcIdx).readReg
-          arbInSeq(srcIdx).bits.addr := inRFReadReqSeq(srcIdx).bits.addr
-          arbInSeq(srcIdx).bits.robIdx := inRFReadReqSeq(srcIdx).bits.robIdx
-          arbInSeq(srcIdx).bits.issueValid := inRFReadReqSeq(srcIdx).valid
+          arbInSeq(srcIdx).valid := vecRFRen(iqIdx)(exuIdx).get(srcIdx)
+          arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
+          arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
+          arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
         } else {
           arbInSeq(srcIdx).valid := false.B
           arbInSeq(srcIdx).bits := 0.U.asTypeOf(arbInSeq(srcIdx).bits)
@@ -160,10 +171,10 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
       val srcIndices: Seq[Int] = V0RegSrcDataSet.flatMap(data => fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(data)).toSeq.sorted
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
         if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
-          arbInSeq(srcIdx).valid := inRFReadReqSeq(srcIdx).valid && allDataSources(iqIdx)(exuIdx)(srcIdx).readReg
-          arbInSeq(srcIdx).bits.addr := inRFReadReqSeq(srcIdx).bits.addr
-          arbInSeq(srcIdx).bits.robIdx := inRFReadReqSeq(srcIdx).bits.robIdx
-          arbInSeq(srcIdx).bits.issueValid := inRFReadReqSeq(srcIdx).valid
+          arbInSeq(srcIdx).valid := v0RFRen(iqIdx)(exuIdx).get(srcIdx)
+          arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
+          arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
+          arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
         } else {
           arbInSeq(srcIdx).valid := false.B
           arbInSeq(srcIdx).bits := 0.U.asTypeOf(arbInSeq(srcIdx).bits)
@@ -174,10 +185,10 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
 
   vlRFReadArbiter.io.in.zip(vlRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq), iqIdx) =>
     arbInSeq2.zip(inRFReadReqSeq).zipWithIndex.foreach { case ((arbInSeq, inRFReadReq), exuIdx) =>
-      arbInSeq.headOption.foreach(_.valid := inRFReadReq.map(_.valid).get)
-      arbInSeq.headOption.foreach(_.bits.addr := inRFReadReq.map(_.bits.addr).get)
-      arbInSeq.headOption.foreach(_.bits.robIdx := inRFReadReq.map(_.bits.robIdx).get)
-      arbInSeq.headOption.foreach(_.bits.issueValid := inRFReadReq.map(_.valid).get)
+      arbInSeq.headOption.foreach(_.valid := vlRFRen(iqIdx)(exuIdx).get)
+      arbInSeq.headOption.foreach(_.bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrcVl.get)
+      arbInSeq.headOption.foreach(_.bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx)
+      arbInSeq.headOption.foreach(_.bits.issueValid := fromIQ(iqIdx)(exuIdx).valid)
     }
   }
 
@@ -488,9 +499,8 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   val s1_toExuDataWire: MixedVec[MixedVec[Og1InUop]] = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.bits.cloneType).toSeq)).toSeq))
   s1_toExuData := s1_toExuDataWire
   val s1_toExuReady = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.ready.cloneType).toSeq))))
-  val s1_srcType: MixedVec[MixedVec[Vec[UInt]]] = MixedVecInit(fromIQ.map(x => MixedVecInit(x.map(xx => RegEnable(xx.bits.srcType, xx.fire)).toSeq)))
-  val s1_intRfBankRaddr: MixedVec[MixedVec[Vec[UInt]]] = MixedVecInit(intRFReadReq.map(x => MixedVecInit(x.map(xx => VecInit(xx.map(xxx =>
-      RegEnable(xxx.bits.addr.pad(intSchdParams.pregIdxWidth).head(intRfBankRaddrWidth), xxx.valid)))))))
+  val s1_intRfBankRaddr: MixedVec[MixedVec[Vec[UInt]]] = MixedVecInit(fromIQ.map(x => MixedVecInit(x.map(xx => VecInit(xx.bits.psrc.map(xxx =>
+      RegNext(xxx.pad(intSchdParams.pregIdxWidth).head(intRfBankRaddrWidth))))))))
   val s1_intPregRData: MixedVec[MixedVec[Vec[UInt]]] = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.bits.src.cloneType).toSeq))))
   val s1_fpPregRData: MixedVec[MixedVec[Vec[UInt]]] = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.bits.src.cloneType).toSeq))))
   val s1_vfPregRData: MixedVec[MixedVec[Vec[UInt]]] = Wire(MixedVec(toExu.map(x => MixedVec(x.map(_.bits.src.cloneType).toSeq))))
@@ -595,9 +605,7 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
       // same name, need shift logic, not simple connection
       s1_data.loadDependency.foreach(_ := s0.bits.loadDependency.get.map(_ << 1))
       // timing Optimize, clock gate can use RegNext(s0.valid)
-      val og1Payload = io.fromIQDeqOg1Payload.find(_.head.params == s1_data.iqParams)
-      if (og1Payload.nonEmpty) connectSamePort(s1_data, og1Payload.get(j))
-      else                     connectSamePort(s1_data, 0.U.asTypeOf(s1_data))
+      connectSamePort(s1_data, fromIQDeqOg1Payload(i)(j))
       s0.ready := notBlock && !s0_cancel
       // IQ(s0) --[Ctrl]--> s1Reg ---------- end
     }
@@ -671,19 +679,19 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
             Seq(None)
             :+
             OptionWrapper(s1_v0PregRData(i)(j).isDefinedAt(k) && srcDataTypeSet.intersect(V0RegSrcDataSet).nonEmpty, 
-              (SrcType.isV0(s1_srcType(i)(j)(k)) -> s1_v0PregRData(i)(j)(k)))
+              (sinkData.v0Ren.get(k) -> s1_v0PregRData(i)(j)(k)))
           )}
           else {(
             Seq(None)
             :+
             OptionWrapper(s1_intPregRData(i)(j).isDefinedAt(k) && srcDataTypeSet.intersect(IntRegSrcDataSet).nonEmpty, 
-              (SrcType.isXp(s1_srcType(i)(j)(k)) -> s1_intPregRData(i)(j)(k)))
+              (sinkData.rfRen.get(k) -> s1_intPregRData(i)(j)(k)))
             :+
             OptionWrapper(s1_vfPregRData(i)(j).isDefinedAt(k) && srcDataTypeSet.intersect(VecRegSrcDataSet).nonEmpty,
-              (SrcType.isVp(s1_srcType(i)(j)(k)) -> s1_vfPregRData(i)(j)(k)))
+              (sinkData.vecRen.get(k) -> s1_vfPregRData(i)(j)(k)))
             :+
             OptionWrapper(s1_fpPregRData(i)(j).isDefinedAt(k) && srcDataTypeSet.intersect(FpRegSrcDataSet).nonEmpty, 
-              (SrcType.isFp(s1_srcType(i)(j)(k)) -> s1_fpPregRData(i)(j)(k)))
+              (sinkData.fpRen.get(k) -> s1_fpPregRData(i)(j)(k)))
           )}
         ).filter(_.nonEmpty).map(_.get)
 
@@ -797,14 +805,20 @@ class DataPathIO()(implicit p: Parameters, params: BackendParams, param: SchdBlo
   val fromIntIQ: MixedVec[MixedVec[DecoupledIO[Og0InUop]]] =
     Flipped(MixedVec(intSchdParams.issueBlockParams.map(_.genIssueDecoupledBundle)))
 
-  val fromIQDeqOg1Payload: MixedVec[MixedVec[Og1Payload]] =
-    Flipped(MixedVec(param.issueBlockParams.map(_.genIssueDeqOg1PayloadBundle)))
-
   val fromFpIQ: MixedVec[MixedVec[DecoupledIO[Og0InUop]]] =
     Flipped(MixedVec(fpSchdParams.issueBlockParams.map(_.genIssueDecoupledBundle)))
 
   val fromVfIQ = Flipped(MixedVec(vecSchdParams.issueBlockParams.map(_.genIssueDecoupledBundle)))
 
+  val fromIntIQDeqOg1Payload: MixedVec[MixedVec[IssueQueueDeqOg1Payload]] =
+    Input(MixedVec(intSchdParams.issueBlockParams.map(_.genIssueDeqOg1PayloadBundle)))
+
+  val fromFpIQDeqOg1Payload: MixedVec[MixedVec[IssueQueueDeqOg1Payload]] =
+    Input(MixedVec(fpSchdParams.issueBlockParams.map(_.genIssueDeqOg1PayloadBundle)))
+
+  val fromVecIQDeqOg1Payload: MixedVec[MixedVec[IssueQueueDeqOg1Payload]] =
+    Input(MixedVec(vecSchdParams.issueBlockParams.map(_.genIssueDeqOg1PayloadBundle)))
+
   val fromVecExcpMod = Option.when(param.isVecSchd)(Input(new ExcpModToVprf(maxMergeNumPerCycle * 2, maxMergeNumPerCycle)))
 
   val toIntIQ = MixedVec(intSchdParams.issueBlockParams.map(_.genOGRespBundle))
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
index 8e2b9eae360..6605b0237d9 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
@@ -58,6 +58,7 @@ case class ExeUnitParams(
   val readFpRf: Boolean = numFpSrc > 0
   val readVecRf: Boolean = numVecSrc > 0
   val readVfRf: Boolean = numVfSrc > 0
+  val readV0Rf: Boolean = numV0Src > 0
   val readVlRf: Boolean = fuConfigs.exists(_.readVl)
   val writeIntRf: Boolean = fuConfigs.map(_.writeIntRf).reduce(_ || _)
   val writeFpRf: Boolean = fuConfigs.map(_.writeFpRf).reduce(_ || _)
@@ -73,12 +74,15 @@ case class ExeUnitParams(
   val needTaken: Boolean = fuConfigs.map(x => x.isJmp || x.isBrh).reduce(_ || _)
   val needRasAction: Boolean = fuConfigs.map(x => x.isJmp).reduce(_ || _)
   val needImm: Boolean = fuConfigs.map(x => x.immType.nonEmpty).reduce(_ || _)
+  def deqImmTypes: Seq[Imm] = fuConfigs.flatMap(_.immType).distinct
+  // set load imm to 32-bit for fused_lui_load
+  def deqImmTypesMaxLen: Int = if (hasLoadFu || hasHyldaFu) 32 else deqImmTypes.map(x => x).maxBy(_.len).len
   val writeVfRf: Boolean = writeVecRf
   val writeFflags: Boolean = fuConfigs.map(_.writeFflags).reduce(_ || _)
   val writeVxsat: Boolean = fuConfigs.map(_.writeVxsat).reduce(_ || _)
   val hasNoDataWB: Boolean = fuConfigs.map(_.hasNoDataWB).reduce(_ && _)
   val hasRedirect: Boolean = fuConfigs.map(_.hasRedirect).reduce(_ || _)
-  val hasIsRVC: Boolean = fuConfigs.map(_.hasIsRVC).reduce(_ || _)
+  val needIsRVC: Boolean = fuConfigs.map(_.hasIsRVC).reduce(_ || _)
   val hasRasAction: Boolean = fuConfigs.map(_.hasRasAction).reduce(_ || _)
   val exceptionOut: Seq[Int] = fuConfigs.map(_.exceptionOut).reduce(_ ++ _).distinct.sorted
   val hasLoadError: Boolean = fuConfigs.map(_.hasLoadError).reduce(_ || _)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 0b9d24b71a1..b27c1f04c0b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -480,16 +480,20 @@ class InterruptFilter extends Module {
     0.U
   )
 
-  val mIRNotZero  = mIRVecTmp.orR
-  val hsIRNotZero = hsIRVecTmp.orR
-  val vsIRNotZero = vsIRVecTmp.orR
+  val mIRVecReg  = RegNext(mIRVecTmp,  0.U.asTypeOf(mIRVecTmp))
+  val hsIRVecReg = RegNext(hsIRVecTmp, 0.U.asTypeOf(hsIRVecTmp))
+  val vsIRVecReg = RegNext(vsIRVecTmp, 0.U.asTypeOf(vsIRVecTmp))
+
+  val mIRNotZero  = mIRVecReg.orR
+  val hsIRNotZero = hsIRVecReg.orR
+  val vsIRNotZero = vsIRVecReg.orR
 
   val irToHS = !mIRNotZero && hsIRNotZero
   val irToVS = !mIRNotZero && !hsIRNotZero && vsIRNotZero
 
-  val mIRVec  = mIRVecTmp
-  val hsIRVec = Mux(irToHS, hsIRVecTmp, 0.U)
-  val vsIRVec = Mux(irToVS, UIntToOH(vsIRVecTmp, 64), 0.U)
+  val mIRVec  = mIRVecReg
+  val hsIRVec = Mux(irToHS, hsIRVecReg, 0.U)
+  val vsIRVec = Mux(irToVS, UIntToOH(vsIRVecReg, 64), 0.U)
 
   val vsMapHostIRVecTmp = Cat((0 until vsIRVec.getWidth).map { num =>
     // 2,6,10
@@ -530,10 +534,10 @@ class InterruptFilter extends Module {
   val disableDebugIntr = io.in.debugMode || (io.in.dcsr.STEP.asBool && !io.in.dcsr.STEPIE.asBool)
   val enableDebugIntr = io.in.debugIntr && !disableDebugIntr
 
-  val disableAllIntr = disableDebugIntr || !io.in.mnstatusNMIE
+  val disableAllIntr = RegNext(disableDebugIntr || !io.in.mnstatusNMIE, false.B)
 
   val normalIntrVec = mIRVec | hsIRVec | vsMapHostIRVec
-  val intrVec = Mux(disableAllIntr, 0.U, Mux(io.in.nmi, nmiVec, normalIntrVec))
+  val intrVec = Mux(disableAllIntr, 0.U, Mux(RegNext(io.in.nmi, false.B), RegNext(nmiVec, 0.U.asTypeOf(nmiVec)), normalIntrVec))
 
   // virtual interrupt with hvictl injection
   val vsIRModeCond = privState.isModeVS && vsstatusSIE || privState < PrivState.ModeVS
@@ -553,7 +557,7 @@ class InterruptFilter extends Module {
   viIsHvictlInjectReg := vsIRModeCond && SelectCandidate5 && io.in.mnstatusNMIE
   irToHSReg := irToHS
   irToVSReg := irToVS
-  val delayedIntrVec = DelayN(intrVecReg, 5)
+  val delayedIntrVec = DelayN(intrVecReg, 4)
   val delayedDebugIntr = DelayN(debugIntrReg, 5)
   val delayedNMI = DelayN(nmiReg, 5)
   val delayedVIIsHvictlInjectReg = DelayN(viIsHvictlInjectReg, 5)
diff --git a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
index 66ae320800c..0c9c35d5369 100644
--- a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
@@ -148,7 +148,7 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
   EntryRegCommonConnect(common, hasWakeupIQ, validReg, entryUpdate, entryReg, currentStatus, io.commonIn, true, isComp)
 
   //output
-  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, currentStatus, io.commonIn, true, isComp)
+  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryRegNext, currentStatus, io.commonIn, true, isComp)
 }
 
 class EnqEntryVecMem(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) extends EnqEntry(isComp)
diff --git a/src/main/scala/xiangshan/backend/issue/Entries.scala b/src/main/scala/xiangshan/backend/issue/Entries.scala
index 9b5a454c9d0..fe01cf06e24 100644
--- a/src/main/scala/xiangshan/backend/issue/Entries.scala
+++ b/src/main/scala/xiangshan/backend/issue/Entries.scala
@@ -65,6 +65,8 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   val issueTimerVec       = Wire(Vec(params.numEntries, UInt(params.issueTimerWidth.W)))
   val sqIdxVec            = OptionWrapper(params.needFeedBackSqIdx, Wire(Vec(params.numEntries, new SqPtr())))
   val lqIdxVec            = OptionWrapper(params.needFeedBackLqIdx, Wire(Vec(params.numEntries, new LqPtr())))
+  val validVecRegNext     = Wire(Vec(params.numEntries, Bool()))
+  val issuedVecRegNext    = Wire(Vec(params.numEntries, Bool()))  
   //src status
   val dataSourceVec       = Wire(Vec(params.numEntries, Vec(params.numRegSrc, DataSource())))
   val loadDependencyVec   = Wire(Vec(params.numEntries, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))))
@@ -348,7 +350,7 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
         io.cancelDeqVec(i) := Mux(sel.valid, othersEntryOldestCancel.get(i), enqEntryOldestCancel(i))
       }
       io.othersEntryOldestSelDelay.get.zipWithIndex.foreach { case (sel, i) =>
-        io.deqOg1Payload(i) := Mux(sel.valid, othersEntryOldestDelay.get(i), enqEntryOldestDelay(i)).bits.payload.og1Payload
+        io.deqOg1Payload(i) := Mux(sel.valid, othersEntryOldestDelay.get(i), enqEntryOldestDelay(i)).bits.toDeqOg1Payload(i)
       }
     }
     else {
@@ -384,14 +386,14 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
       io.compEntryOldestSelDelay.get.zip(io.simpEntryOldestSelDelay.get).zipWithIndex.foreach { case ((compSel, simpSel), i) =>
         val deqOg1Payload = Mux(compSel.valid,
                            compEntryOldestDelay.get(i),
-                           Mux(simpSel.valid, simpEntryOldestDelay.get(i), enqEntryOldestDelay(i))).bits.payload.og1Payload
+                           Mux(simpSel.valid, simpEntryOldestDelay.get(i), enqEntryOldestDelay(i))).bits.toDeqOg1Payload(i)
         io.deqOg1Payload(i) := deqOg1Payload
         if (params.aluDeqNeedPickJump) {
           val aluDeqSelectJump = RegNext(io.deqEntry(0).valid && io.deqEntry(0).bits.payload.rfWen.get && FuType.isJump(io.deqEntry(0).bits.payload.fuType))
           if (params.deqFuCfgs(i).contains(JmpCfg)) {
             val deqOg1Payload0 = Mux(io.compEntryOldestSelDelay.get(0).valid,
                                 compEntryOldestDelay.get(0),
-                                Mux(io.simpEntryOldestSelDelay.get(0).valid, simpEntryOldestDelay.get(0), enqEntryOldestDelay(0))).bits.payload.og1Payload
+                                Mux(io.simpEntryOldestSelDelay.get(0).valid, simpEntryOldestDelay.get(0), enqEntryOldestDelay(0))).bits.toDeqOg1Payload(i)
             // jump uop use alu uop before change
             io.deqOg1Payload(i) := Mux(aluDeqSelectJump, deqOg1Payload0, deqOg1Payload)
           }
@@ -416,6 +418,8 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   io.compEntryEnqSelVec.foreach(_   := finalCompTransSelVec.get.zip(compEnqVec.get).map(x => x._1 & Fill(CompEntryNum, x._2.valid)))
   io.othersEntryEnqSelVec.foreach(_ := finalOthersTransSelVec.get.zip(enqEntryTransVec).map(x => x._1 & Fill(OthersEntryNum, x._2.valid)))
   io.robIdx.foreach(_               := robIdxVec)
+  io.validRegNext                   := validVecRegNext.asUInt
+  io.issuedRegNext                  := issuedVecRegNext.asUInt
 
 
   def EntriesConnect(in: CommonInBundle, out: CommonOutBundle, entryIdx: Int) = {
@@ -459,6 +463,8 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
       perfOg0CancelVec.get(entryIdx)  := out.perfOg0Cancel.get
       perfWakeupByIQVec.get(entryIdx) := out.perfWakeupByIQ.get
     }
+    validVecRegNext(entryIdx)   := out.validRegNext
+    issuedVecRegNext(entryIdx)  := out.issuedRegNext
   }
 
   // entries perf counter
@@ -572,10 +578,13 @@ class EntriesIO(implicit p: Parameters, params: IssueBlockParams) extends XSBund
   val dataSources         = Vec(params.numEntries, Vec(params.numRegSrc, Output(DataSource())))
   val loadDependency      = Vec(params.numEntries, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
   val exuSources          = OptionWrapper(params.hasIQWakeUp, Vec(params.numEntries, Vec(params.numRegSrc, Output(ExuSource()))))
+  // for enq.ready timing
+  val validRegNext        = Output(UInt(params.numEntries.W))
+  val issuedRegNext       = Output(UInt(params.numEntries.W))
   //deq status
   val isFirstIssue        = Vec(params.numDeq, Output(Bool()))
   val deqEntry            = Vec(params.numDeq, ValidIO(new EntryBundle))
-  val deqOg1Payload       = Vec(params.numDeq, new Og1Payload(params))
+  val deqOg1Payload       = Output(MixedVec(params.exuBlockParams.map(x => new IssueQueueDeqOg1Payload(x))))
   val cancelDeqVec        = Vec(params.numDeq, Output(Bool()))
   val aluDeqSelectJump    = Option.when(params.aluDeqNeedPickJump)(Output(Bool()))
 
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index 15e3ca8928a..af371c0f640 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -29,8 +29,6 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val firstIssue            = Bool()
     val issueTimer            = UInt(params.issueTimerWidth.W)
     val deqPortIdx            = UInt(1.W)
-    //vector mem status
-    val vecMem                = Option.when(params.isVecMemIQ)(new StatusVecMemPart)
 
     def srcReady: Bool        = {
       VecInit(srcStatus.map(_.srcState).map(SrcState.isReady)).asUInt.andR &&
@@ -100,6 +98,25 @@ object EntryBundles extends HasCircularQueuePtrHelper {
   class EntryBundle(implicit p: Parameters, params: IssueBlockParams) extends XSBundle {
     val status                = new Status()
     val payload               = new IssueQueuePayload(params)
+    def toDeqOg1Payload(deqIdx: Int): IssueQueueDeqOg1Payload = {
+      val deqOg1Payload = Wire(new IssueQueueDeqOg1Payload(params.exuBlockParams(deqIdx)))
+      connectSamePort(deqOg1Payload, payload.og1Payload)
+      // imm's width may be diffrent
+      deqOg1Payload.imm.foreach(_ := payload.og1Payload.imm.get)
+      deqOg1Payload.rf.zip(status.srcStatus.map(_.psrc)).zip(status.srcStatus.map(_.srcType)).foreach { case ((rf, psrc), srcType) =>
+        // psrc in status array can be pregIdx of IntRegFile or VfRegFile
+        rf.foreach(_.addr := psrc)
+        rf.foreach(_.robIdx := status.robIdx)
+        rf.foreach(_.issueValid := status.issued)
+      }
+      deqOg1Payload.rfVl lazyZip status.srcStatusVl.map(_.psrc) foreach {
+        case (rf, psrc) =>
+          rf.addr := psrc
+          rf.robIdx := status.robIdx
+          rf.issueValid := status.issued
+      }
+      deqOg1Payload
+    }
   }
 
   class CommonInBundle(implicit p: Parameters, params: IssueBlockParams) extends XSBundle {
@@ -139,6 +156,9 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val fuType                = Output(FuType())
     val robIdx                = Output(new RobPtr)
     val uopIdx                = Option.when(params.isVecMemIQ)(Output(UopIdx()))
+    // for enq.ready
+    val validRegNext          = Output(Bool())
+    val issuedRegNext         = Output(Bool())
     //src
     val dataSources           = Vec(params.numRegSrc, Output(DataSource()))
     val exuSources            = Option.when(params.hasIQWakeUp)(Vec(params.numRegSrc, Output(ExuSource())))
@@ -177,7 +197,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val srcLoadDependencyNext = Vec(params.numRegSrc, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
   }
 
-  def CommonWireConnect(common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, og1Payload: Og1Payload, status: Status, commonIn: CommonInBundle, isEnq: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
+  def CommonWireConnect(common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, og1Payload: EntryOg1Payload, status: Status, commonIn: CommonInBundle, isEnq: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
     val hasIQWakeupGet        = hasIQWakeup.getOrElse(0.U.asTypeOf(new CommonIQWakeupBundle))
     common.flushed            := status.robIdx.needFlush(commonIn.flush)
     val finalSuccess           = (if (params.needFeedBackSqIdx)
@@ -307,7 +327,8 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val cancelBypassVec                                = Wire(Vec(params.numRegSrc, Bool()))
     val srcCancelByLoad                                = common.srcLoadCancelVec.asUInt.orR
     val sqIdxHit                                       = (if (params.needFeedBackSqIdx)
-                                                            status.issueTimer =/= params.issueTimerMaxValue.U || status.issueTimer === params.issueTimerMaxValue.U && status.vecMem.get.sqIdx === commonIn.issueResp.sqIdx.get
+                                                            status.issueTimer =/= params.issueTimerMaxValue.U ||
+                                                            status.issueTimer === params.issueTimerMaxValue.U && entryReg.payload.og1Payload.sqIdx.get === commonIn.issueResp.sqIdx.get
                                                           else true.B)
     val respIssueFail                                  = commonIn.issueResp.failed && sqIdxHit
     entryUpdate.status.robIdx                         := status.robIdx
@@ -415,12 +436,9 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     entryUpdate.status.issueTimer                     := Mux(validReg && status.issued, updateIssueTimer, 0.U)
     entryUpdate.status.deqPortIdx                     := Mux(commonIn.deqSel, commonIn.deqPortIdxWrite, Mux(status.issued, status.deqPortIdx, 0.U))
     entryUpdate.payload                               := entryReg.payload
-    if (params.isVecMemIQ) {
-      entryUpdate.status.vecMem.get := entryReg.status.vecMem.get
-    }
   }
 
-  def CommonOutConnect(commonOut: CommonOutBundle, common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, entryUpdate: EntryBundle, entryReg: EntryBundle, status: Status, commonIn: CommonInBundle, isEnq: Boolean, isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
+  def CommonOutConnect(commonOut: CommonOutBundle, common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, entryUpdate: EntryBundle, entryReg: EntryBundle, entryRegNext: EntryBundle, status: Status, commonIn: CommonInBundle, isEnq: Boolean, isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
     val hasIQWakeupGet                                 = hasIQWakeup.getOrElse(0.U.asTypeOf(new CommonIQWakeupBundle))
     commonOut.valid                                   := validReg
     commonOut.issued                                  := entryReg.status.issued
@@ -515,16 +533,13 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     if (params.isVecMemIQ) {
       commonOut.uopIdx.get                            := entryReg.payload.og1Payload.uopIdx.get
     }
+    commonOut.validRegNext                            := common.validRegNext
+    commonOut.issuedRegNext                           := entryRegNext.status.issued
   }
 
   def EntryVecMemConnect(commonIn: CommonInBundle, common: CommonWireBundle, validReg: Bool, entryReg: EntryBundle, entryRegNext: EntryBundle, entryUpdate: EntryBundle)(implicit p: Parameters, params: IssueBlockParams) = {
     val fromLsq                                        = commonIn.vecMemIn.get
-    val vecMemStatus                                   = entryReg.status.vecMem.get
-    val vecMemStatusUpdate                             = entryUpdate.status.vecMem.get
-    vecMemStatusUpdate                                := vecMemStatus
-
-    val isFirstLoad = entryReg.status.vecMem.get.lqIdx === fromLsq.lqDeqPtr
-
+    val isFirstLoad                                    = entryReg.payload.og1Payload.lqIdx.get === fromLsq.lqDeqPtr
     val isVleff                                        = entryReg.payload.og1Payload.vpu.get.isVleff
     // update blocked
     entryUpdate.status.blocked                        := !isFirstLoad && isVleff
diff --git a/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala b/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
index d475430fe1e..f454f9adbca 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
@@ -67,6 +67,10 @@ case class IssueBlockParams(
 
   def isVecMemIQ: Boolean = isVecLduIQ || isVecStuIQ
 
+  def needLqIdx: Boolean = isLdAddrIQ || isVecMemIQ
+
+  def needSqIdx: Boolean = isStAddrIQ || isStdIQ || isVecMemIQ || isLdAddrIQ
+
   def needFeedBackSqIdx: Boolean = isVecStuIQ
 
   // There is no snresp for load, so there is no need to provide feedback on lqidx
@@ -132,7 +136,7 @@ case class IssueBlockParams(
 
   def needRasAction: Boolean = exuBlockParams.map(_.hasRasAction).reduce(_ || _)
 
-  def needIsRVC: Boolean = exuBlockParams.map(_.hasIsRVC).reduce(_ || _)
+  def needIsRVC: Boolean = exuBlockParams.map(_.needIsRVC).reduce(_ || _)
 
   def needTaken: Boolean = JmpCnt + BrhCnt > 0
 
@@ -434,8 +438,8 @@ case class IssueBlockParams(
     MixedVec(exuBlockParams.filterNot(_.fakeUnit).map(x => ValidIO(new Og0InUop(this, x))))
   }
 
-  def genIssueDeqOg1PayloadBundle(implicit p: Parameters): MixedVec[Og1Payload] = {
-    MixedVec(exuBlockParams.filterNot(_.fakeUnit).map(x => new Og1Payload(x.issueBlockParam)))
+  def genIssueDeqOg1PayloadBundle(implicit p: Parameters): MixedVec[IssueQueueDeqOg1Payload] = {
+    MixedVec(exuBlockParams.filterNot(_.fakeUnit).map(x => new IssueQueueDeqOg1Payload(x)))
   }
 
   def genExuWakeUpOutValidBundle(implicit p: Parameters): MixedVec[DecoupledIO[IssueQueueIQWakeUpBundle]] = {
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index 924bf1e5c56..10ea00a3b6c 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -56,7 +56,7 @@ class IssueQueueIO()(implicit p: Parameters, params: IssueBlockParams) extends X
   val srcReadyVec = Output(Vec(params.numEntries, Bool()))
 
   val deqDelay: MixedVec[DecoupledIO[Og0InUop]] = params.genIssueDecoupledBundle// = deq.cloneType
-  val deqOg1Payload: MixedVec[Og1Payload] = params.genIssueDeqOg1PayloadBundle
+  val deqOg1Payload: MixedVec[IssueQueueDeqOg1Payload] = params.genIssueDeqOg1PayloadBundle
   def allWakeUp = wakeupFromWB ++ wakeupFromIQ
 }
 
@@ -213,6 +213,8 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
   val canIssueVec = VecInit(entries.io.canIssue.asBools)
   val rfWenVec = VecInit(entries.io.rfWen.asBools)
   val srcReadyVec = VecInit(entries.io.srcReady.asBools)
+  val validVecRegNext = VecInit(entries.io.validRegNext.asBools)
+  val issuedVecRegNext = VecInit(entries.io.issuedRegNext.asBools)
   io.validVec := validVec
   io.issuedVec := issuedVec
   io.canIssueVec := canIssueVec
@@ -387,15 +389,13 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     entriesIO.simpEntryDeqSelVec.foreach(_                      := VecInit(simpEntryOldestSel.get.takeRight(params.numEnq).map(_.bits)))
     if (params.isVecMemIQ){
       entries.io.enq.zipWithIndex.map{ case(enqData, i) =>
-        val enqStatus = enqData.bits.status
-        enqStatus.vecMem.get.sqIdx := s0_enqBits(i).sqIdx.get
-        enqStatus.vecMem.get.lqIdx := s0_enqBits(i).lqIdx.get
-        // MemAddrIQ also handle vector insts
-        enqStatus.vecMem.get.numLsElem := s0_enqBits(i).numLsElem.get
+        val enqPayload = enqData.bits.payload.og1Payload
+        enqPayload.sqIdx.get := s0_enqBits(i).sqIdx.get
+        enqPayload.lqIdx.get := s0_enqBits(i).lqIdx.get
 
         val isFirstLoad = s0_enqBits(i).lqIdx.get <= io.memIO.get.lqDeqPtr.get
         val isVleff = s0_enqBits(i).vpu.get.isVleff
-        enqStatus.blocked := !isFirstLoad && isVleff
+        enqData.bits.status.blocked := !isFirstLoad && isVleff
       }
     }
     entries.io.vecMemIn.foreach(_.sqDeqPtr := io.memIO.get.sqDeqPtr.get)
@@ -864,6 +864,12 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     deq.bits.isFirstIssue := deqFirstIssueVec(i)
     deq.bits.iqIdx    := OHToUInt(finalDeqSelOHVec(i))
     deq.bits.fuType   := IQFuType.readFuType(deqEntryVec(i).bits.status.fuType, params.getFuCfgs.map(_.fuType)).asUInt
+    // TODO: entries use Mux1H sel oldest uop, there can remove deq.valid
+    deq.bits.rfRen .foreach(x => x.zipWithIndex.foreach{case (xx, idx) => xx := deq.valid && SrcType.isXp(deqEntryVec(i).bits.payload.srcType(idx)) && deqEntryVec(i).bits.status.srcStatus(idx).dataSources.readReg})
+    deq.bits.fpRen .foreach(x => x.zipWithIndex.foreach{case (xx, idx) => xx := deq.valid && SrcType.isFp(deqEntryVec(i).bits.payload.srcType(idx)) && deqEntryVec(i).bits.status.srcStatus(idx).dataSources.readReg})
+    deq.bits.vecRen.foreach(x => x.zipWithIndex.foreach{case (xx, idx) => xx := deq.valid && SrcType.isVp(deqEntryVec(i).bits.payload.srcType(idx)) && deqEntryVec(i).bits.status.srcStatus(idx).dataSources.readReg})
+    deq.bits.v0Ren .foreach(x => x.zipWithIndex.foreach{case (xx, idx) => xx := deq.valid && SrcType.isV0(deqEntryVec(i).bits.payload.srcType(idx)) && deqEntryVec(i).bits.status.srcStatus(idx).dataSources.readReg})
+    deq.bits.vlRen .foreach(x => x := deq.valid && deqEntryVec(i).bits.status.srcStatusVl.get.dataSource.readReg)
     deq.bits.rfWen.foreach(_ := deqEntryVec(i).bits.payload.rfWen.get)
     deq.bits.fpWen.foreach(_ := deqEntryVec(i).bits.payload.fpWen.get)
     deq.bits.vecWen.foreach(_ := deqEntryVec(i).bits.payload.vecWen.get)
@@ -875,6 +881,8 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     deq.bits.robIdx := deqEntryVec(i).bits.status.robIdx
 
     require(deq.bits.dataSources.size <= finalDataSources(i).size)
+    deq.bits.psrc.zip(deqEntryVec(i).bits.status.srcStatus).map(x => x._1 := x._2.psrc)
+    deq.bits.psrcVl.zip(deqEntryVec(i).bits.status.srcStatusVl).map(x => x._1 := x._2.psrc)
     deq.bits.dataSources.zip(finalDataSources(i)).foreach { case (sink, source) => sink := source}
     deq.bits.exuSources.foreach(_.zip(finalExuSources.get(i)).foreach { case (sink, source) => sink := source})
     deq.bits.loadDependency.foreach(_.zip(finalLoadDependency(i)).foreach { case (sink, source) => sink := source})
@@ -892,23 +900,6 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
       }
     }
 
-    deq.bits.rf.zip(deqEntryVec(i).bits.status.srcStatus.map(_.psrc)).zip(deqEntryVec(i).bits.status.srcStatus.map(_.srcType)).foreach { case ((rf, psrc), srcType) =>
-      // psrc in status array can be pregIdx of IntRegFile or VfRegFile
-      rf.foreach(_.addr := psrc)
-      rf.foreach(_.srcType := srcType)
-      rf.foreach(_.robIdx := deqEntryVec(i).bits.status.robIdx)
-      rf.foreach(_.issueValid := deqEntryVec(i).valid)
-    }
-    deq.bits.rfVl lazyZip deqEntryVec(i).bits.status.srcStatusVl.map(_.psrc) foreach {
-      case (rf, psrc) =>
-        rf.addr := psrc
-        rf.srcType := SrcType.vp // this is vl
-        rf.robIdx := deqEntryVec(i).bits.status.robIdx
-        rf.issueValid := deqEntryVec(i).valid
-    }
-    deq.bits.srcType.zip(deqEntryVec(i).bits.status.srcStatus.map(_.srcType)).foreach { case (sink, source) =>
-      sink := source
-    }
     deq.bits.rcIdx.foreach(_ := deqEntryVec(i).bits.status.srcStatus.map(_.regCacheIdx.get))
     deq.bits.ftqIdx.foreach(_ := deqEntryVec(i).bits.payload.ftqPtr.get)
     deq.bits.ftqOffset.foreach(_ := deqEntryVec(i).bits.payload.ftqOffset.get)
@@ -924,6 +915,12 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     when(validVec.asUInt.orR) {
       deqDly.bits := deq.bits
     }
+    // for oldestArbiter readReq valid
+    deqDly.bits.rfRen .foreach(_ := deq.bits.rfRen .get)
+    deqDly.bits.fpRen .foreach(_ := deq.bits.fpRen .get)
+    deqDly.bits.vecRen.foreach(_ := deq.bits.vecRen.get)
+    deqDly.bits.v0Ren .foreach(_ := deq.bits.v0Ren .get)
+    deqDly.bits.vlRen .foreach(_ := deq.bits.vlRen .get)
     // deqBeforeDly.ready is always true
     deq.ready := true.B
     // for int scheduler fdiv has high priority than alu
@@ -986,7 +983,9 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
 
   // Todo: better counter implementation
   private val enqHasValid = validVec.take(params.numEnq).reduce(_ | _)
+  private val enqHasValidRegNext = validVecRegNext.take(params.numEnq).reduce(_ | _)
   private val enqHasIssued = validVec.zip(issuedVec).take(params.numEnq).map(x => x._1 & x._2).reduce(_ | _)
+  private val enqHasIssuedRegNext = validVecRegNext.zip(issuedVecRegNext).take(params.numEnq).map(x => x._1 & x._2).reduce(_ | _)
   private val enqEntryValidCnt = PopCount(validVec.take(params.numEnq))
   private val othersValidCnt = PopCount(validVec.drop(params.numEnq))
   private val enqEntryValidCntDeq0 = PopCount(
@@ -1013,20 +1012,21 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
   othersLeftOneCaseVec.zipWithIndex.foreach { case (leftone, i) =>
     leftone := ~(1.U((params.numEntries - params.numEnq).W) << i)
   }
-  private val othersLeftOne = othersLeftOneCaseVec.map(_ === VecInit(validVec.drop(params.numEnq)).asUInt).reduce(_ | _)
+  private val othersLeftOne = othersLeftOneCaseVec.map(_ === VecInit(validVecRegNext.drop(params.numEnq)).asUInt).reduce(_ | _)
   private val othersCanotIn = Wire(Bool())
-  othersCanotIn := othersLeftOne || validVec.drop(params.numEnq).reduce(_ & _)
+  othersCanotIn := othersLeftOne || validVecRegNext.drop(params.numEnq).reduce(_ & _)
   // if has simp Entry, othersCanotIn will be simpCanotIn
   if (params.numSimp > 0) {
     val simpLeftOneCaseVec = Wire(Vec(params.numSimp, UInt((params.numSimp).W)))
     simpLeftOneCaseVec.zipWithIndex.foreach { case (leftone, i) =>
       leftone := ~(1.U((params.numSimp).W) << i)
     }
-    val simpLeftOne = simpLeftOneCaseVec.map(_ === VecInit(validVec.drop(params.numEnq).take(params.numSimp)).asUInt).reduce(_ | _)
-    val simpCanotIn = simpLeftOne || validVec.drop(params.numEnq).take(params.numSimp).reduce(_ & _)
+    val simpLeftOne = simpLeftOneCaseVec.map(_ === VecInit(validVecRegNext.drop(params.numEnq).take(params.numSimp)).asUInt).reduce(_ | _)
+    val simpCanotIn = simpLeftOne || validVecRegNext.drop(params.numEnq).take(params.numSimp).reduce(_ & _)
     othersCanotIn := simpCanotIn
   }
-  io.enq.foreach(_.ready := (!othersCanotIn || !enqHasValid) && !enqHasIssued)
+  val enqReady = GatedValidRegNext((!othersCanotIn || !enqHasValidRegNext) && !enqHasIssuedRegNext, false.B)
+  io.enq.foreach(_.ready := enqReady)
 
   protected def getDeqLat(deqPortIdx: Int, fuType: UInt) : UInt = {
     Mux(FuType.isUncertain(fuType),
@@ -1077,62 +1077,30 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
 
   // deq instr data source count
   XSPerfAccumulate("issue_datasource_reg", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.reg && !SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.reg })
   }.reduce(_ +& _))
   XSPerfAccumulate("issue_datasource_bypass", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.bypass && !SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.bypass })
   }.reduce(_ +& _))
   XSPerfAccumulate("issue_datasource_forward", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward && !SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward })
   }.reduce(_ +& _))
   XSPerfAccumulate("issue_datasource_noreg", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid })
   }.reduce(_ +& _))
 
   XSPerfHistogram("issue_datasource_reg_hist", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.reg && !SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.reg })
   }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
   XSPerfHistogram("issue_datasource_bypass_hist", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.bypass && !SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.bypass })
   }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
   XSPerfHistogram("issue_datasource_forward_hist", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward && !SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward })
   }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
   XSPerfHistogram("issue_datasource_noreg_hist", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && SrcType.isNotReg(deq.bits.srcType(j)) })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid })
   }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
-
-  // deq instr data source count for each futype
-  for (t <- FuType.functionNameMap.keys) {
-    val fuName = FuType.functionNameMap(t)
-    if (params.getFuCfgs.map(_.fuType == t).reduce(_ | _)) {
-      XSPerfAccumulate(s"issue_datasource_reg_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.reg && !SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _))
-      XSPerfAccumulate(s"issue_datasource_bypass_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.bypass && !SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _))
-      XSPerfAccumulate(s"issue_datasource_forward_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward && !SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _))
-      XSPerfAccumulate(s"issue_datasource_noreg_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _))
-
-      XSPerfHistogram(s"issue_datasource_reg_hist_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.reg && !SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
-      XSPerfHistogram(s"issue_datasource_bypass_hist_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.bypass && !SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
-      XSPerfHistogram(s"issue_datasource_forward_hist_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward && !SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
-      XSPerfHistogram(s"issue_datasource_noreg_hist_futype_${fuName}", deqBeforeDly.map{ deq =>
-        PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && SrcType.isNotReg(deq.bits.srcType(j)) && deq.bits.fuType === t.U })
-      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
-    }
-  }
 }
 
 class IssueQueueMemBundle(implicit p: Parameters, params: IssueBlockParams) extends Bundle {
diff --git a/src/main/scala/xiangshan/backend/issue/OthersEntry.scala b/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
index bc5019669b0..4248378542f 100644
--- a/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
@@ -57,7 +57,7 @@ class OthersEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockPar
   EntryRegCommonConnect(common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryReg.status, io.commonIn, false, isComp)
 
   //output
-  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryReg.status, io.commonIn, false, isComp)
+  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryRegNext, entryReg.status, io.commonIn, false, isComp)
   hasWakeupIQ.foreach(dontTouch(_))
   hasWakeupIQ.foreach(x => dontTouch(x.srcWakeupByIQIsUncertain))
 }
diff --git a/src/main/scala/xiangshan/backend/regfile/Regfile.scala b/src/main/scala/xiangshan/backend/regfile/Regfile.scala
index 4b08ff0d9f7..771fa384f70 100644
--- a/src/main/scala/xiangshan/backend/regfile/Regfile.scala
+++ b/src/main/scala/xiangshan/backend/regfile/Regfile.scala
@@ -41,8 +41,7 @@ class RfWritePort(dataWidth: Int, addrWidth: Int) extends Bundle {
 }
 
 class RfReadPortWithConfig(val rfReadDataCfg: DataConfig, addrWidth: Int)(implicit p: Parameters) extends Bundle {
-  val addr    = Input(UInt(addrWidth.W))
-  val srcType = Input(UInt(3.W))
+  val addr    = UInt(addrWidth.W)
   val robIdx  = new RobPtr
   val issueValid = Bool()
 
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index 6430d151712..a42e51be4bc 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -44,7 +44,6 @@ package object xiangshan {
 
     def isPc(srcType: UInt) = srcType===pc
     def isImm(srcType: UInt) = srcType===imm
-    def isReg(srcType: UInt) = srcType(0)
     def isXp(srcType: UInt) = srcType(0)
     def isFp(srcType: UInt) = srcType(1)
     def isVp(srcType: UInt) = srcType(2)
```
