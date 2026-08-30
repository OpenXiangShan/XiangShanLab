# Commit Log
- Issue: #5652
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5652
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5652
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5652
- Changed files: 8
- Additions: 70
- Deletions: 90

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/issue/EnqEntry.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/backend/issue/OthersEntry.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 9cc6659423c..8fad5cfcb41 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -234,6 +234,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   ctrlBlock.io.toDispatch.wakeUpVec := vecRegion.io.wakeUpToDispatch
   ctrlBlock.io.toDispatch.IQValidNumVec := intRegion.io.IQValidNumVec ++ fpRegion.io.IQValidNumVec ++ vecRegion.io.IQValidNumVec
   ctrlBlock.io.toDispatch.ldCancel := io.mem.ldCancel
+  // Todo: when add cross domain wake up, it is necessary to add assertions that fp and vec do not have 0 lat fu.
   ctrlBlock.io.toDispatch.og0Cancel := intRegion.io.og0Cancel
   ctrlBlock.io.toDispatch.wbPregsInt.zip(intRegion.io.toIntPreg).map(x => {
     x._1.valid := x._2.wen && x._2.rfWen
@@ -389,8 +390,6 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   // for vecIQ read int/fp regfile
   vecRegion.io.fromIntIQ.get <> intRegion.io.intIQOut.get
   vecRegion.io.fromFpIQ.get <> fpRegion.io.fpIQOut.get
-  intRegion.io.fromVecIQ.get <> vecRegion.io.vecIQOut.get
-  fpRegion.io.fromVecIQ.get <> vecRegion.io.vecIQOut.get
 
   vecRegion.io.diffVlRat.foreach(_ := ctrlBlock.io.diff_vl_rat.get)
   vecRegion.io.fromVecExcpMod.get.r := vecExcpMod.o.toVPRF.r
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index f43e5397850..35fb27fce08 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -496,14 +496,6 @@ object Bundles {
     // from dispatch
     val lqIdx = Option.when(params.issueBlockParam.needLqIdx)(new LqPtr)
     val sqIdx = Option.when(params.issueBlockParam.needSqIdx)(new SqPtr) // load unit need sqIdx
-    // for read regfile, rf and rfVl are only for param config
-    private val rfReadDataCfgSet: Seq[Set[DataConfig]] = params.getRfReadDataCfgSet
-    val rf: MixedVec[MixedVec[RfReadPortWithConfig]] = MixedVec(
-      rfReadDataCfgSet.map((set: Set[DataConfig]) =>
-        MixedVec(set.map((x: DataConfig) => new RfReadPortWithConfig(x, params.rdPregIdxWidth)).toSeq)
-      )
-    )
-    val rfVl = Option.when(params.readVlRf)(new RfReadPortWithConfig(VlData(), params.backendParam.getPregParams(VlData()).addrWidth))
   }
 
   class IssueQueuePayload(val params: IssueBlockParams)(implicit p: Parameters) extends XSBundle {
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index cad02c4c316..04c1f1b873a 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -433,9 +433,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     dataPath.io.fromFpIQ.zip(io.fromFpIQ.get).map { case (sink, source) =>
       sink <> source
     }
-    dataPath.io.fromVfIQ.zip(io.fromVecIQ.get).map { case (sink, source) =>
-      sink <> source
-    }
+
     dataPath.io.fromIntWb.get := wbDataPath.io.toIntPreg
     dataPath.io.fromPcTargetMem <> io.fromPcTargetMem.get
     dataPath.io.fromBypassNetwork := bypassNetwork.io.toDataPath
@@ -549,9 +547,7 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
         s := source.io.deqOg1Payload(i)
       }
     }
-    dataPath.io.fromVfIQ.zip(io.fromVecIQ.get).map { case (sink, source) =>
-      sink <> source
-    }
+
     dataPath.io.fromFpWb.get := wbDataPath.io.toFpPreg
     dataPath.io.fromBypassNetwork <> bypassNetwork.io.toDataPath
     io.toFpPreg := wbDataPath.io.toFpPreg
@@ -634,14 +630,8 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     dataPath.io.fromFpIQ.zip(io.fromFpIQ.get).map { case (sink, source) =>
       sink <> source
     }
-    dataPath.io.fromVfIQ.zip(issueQueues).zip(io.vecIQOut.get).map { case ((sink, source), iqOut) =>
-      sink.zipWithIndex.map { case (s, i) =>
-        s.valid := source.io.deqDelay(i).valid
-        iqOut(i).valid := source.io.deqDelay(i).valid
-        s.bits := source.io.deqDelay(i).bits
-        iqOut(i).bits := source.io.deqDelay(i).bits
-        source.io.deqDelay(i).ready := s.ready && iqOut(i).ready
-      }
+    dataPath.io.fromVfIQ.zip(issueQueues).map { case (sink, source) =>
+      sink <> source.io.deqDelay
     }
     dataPath.io.fromVecIQDeqOg1Payload.zip(issueQueues).map { case (sink, source) =>
       sink.zipWithIndex.map { case (s, i) =>
@@ -873,8 +863,6 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
   // to write int regfile
   val fpIQOut = Option.when(params.isFpSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
   val fromFpIQ = Option.when(params.isIntSchd || params.isVecSchd)(Flipped(MixedVec(fpSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
-  val vecIQOut = Option.when(params.isVecSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
-  val fromVecIQ = Option.when(params.isIntSchd || params.isFpSchd)(Flipped(MixedVec(vecSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
   // TopDown
   val uopTopDown = new UopTopDown
 }
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 87cecb118a0..711f00c3040 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -98,45 +98,33 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   private val vfRdNotBlock: Seq2[Bool] = vfRdArbWinner.map(_.map(_.asUInt.andR))
   private val v0RdNotBlock: Seq2[Bool] = v0RdArbWinner.map(_.map(_.asUInt.andR))
 
-  private val intRFReadReq: Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
-  private val fpRFReadReq : Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
-  private val vecRFReadReq: Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
-  private val v0RFReadReq : Seq3[RfReadPortWithConfig] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rf.map(_.head)).toSeq).toSeq
-  private val vlRFReadReq : Seq2[Option[RfReadPortWithConfig]] = fromIQDeqOg1Payload.map(x => x.map(xx => xx.rfVl)).toSeq
-
   private val intRFRen: Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.rfRen).toSeq)
   private val fpRFRen : Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.fpRen).toSeq)
   private val vecRFRen: Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.vecRen).toSeq)
   private val v0RFRen : Seq2[Option[Vec[Bool]]] = fromIQ.map(x => x.map(xx => xx.bits.v0Ren).toSeq)
   private val vlRFRen : Seq2[Option[Bool]]      = fromIQ.map(x => x.map(xx => xx.bits.vlRen)).toSeq
 
-  private val allDataSources: Seq[Seq[Vec[DataSource]]] = fromIQ.map(x => x.map(xx => xx.bits.dataSources).toSeq)
-  private val allNumRegSrcs: Seq[Seq[Int]] = fromIQ.map(x => x.map(xx => xx.bits.exuParams.numRegSrc).toSeq)
-
-  intRFReadArbiter.io.in.zip(intRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq2), iqIdx) =>
-    arbInSeq2.zip(inRFReadReqSeq2).zipWithIndex.foreach { case ((arbInSeq, inRFReadReqSeq), exuIdx) =>
+  intRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
+    arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
       val srcIndices: Seq[Int] = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
-        if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
+        if (srcIndices.contains(srcIdx)) {
           arbInSeq(srcIdx).valid := intRFRen(iqIdx)(exuIdx).get(srcIdx)
           arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
           arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
           arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
         } else {
-          println(s"inRFReadReqSeq.size = ${inRFReadReqSeq.size}")
-          println(s"srcIndices.contains(srcIdx) = ${srcIndices.contains(srcIdx)}, inRFReadReqSeq.isDefinedAt(srcIdx) = ${inRFReadReqSeq.isDefinedAt(srcIdx)}")
-          println(s"iqIdx = $iqIdx, exuIdx = $exuIdx, srcIdx = $srcIdx, ${fromIQDeqOg1Payload(iqIdx)(exuIdx).params.name}")
           arbInSeq(srcIdx).valid := false.B
           arbInSeq(srcIdx).bits := 0.U.asTypeOf(arbInSeq(srcIdx).bits)
         }
       }
     }
   }
-  fpRFReadArbiter.io.in.zip(fpRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq2), iqIdx) =>
-    arbInSeq2.zip(inRFReadReqSeq2).zipWithIndex.foreach { case ((arbInSeq, inRFReadReqSeq), exuIdx) =>
+  fpRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
+    arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
       val srcIndices: Seq[Int] = FpRegSrcDataSet.flatMap(data => fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(data)).toSeq.sorted
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
-        if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
+        if (srcIndices.contains(srcIdx)) {
           arbInSeq(srcIdx).valid := fpRFRen(iqIdx)(exuIdx).get(srcIdx)
           arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
           arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
@@ -149,11 +137,11 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
     }
   }
 
-  vfRFReadArbiter.io.in.zip(vecRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq2), iqIdx) =>
-    arbInSeq2.zip(inRFReadReqSeq2).zipWithIndex.foreach { case ((arbInSeq, inRFReadReqSeq), exuIdx) =>
+  vfRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
+    arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
       val srcIndices: Seq[Int] = VecRegSrcDataSet.flatMap(data => fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(data)).toSeq.sorted
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
-        if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
+        if (srcIndices.contains(srcIdx)) {
           arbInSeq(srcIdx).valid := vecRFRen(iqIdx)(exuIdx).get(srcIdx)
           arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
           arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
@@ -166,11 +154,11 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
     }
   }
 
-  v0RFReadArbiter.io.in.zip(v0RFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq2), iqIdx) =>
-    arbInSeq2.zip(inRFReadReqSeq2).zipWithIndex.foreach { case ((arbInSeq, inRFReadReqSeq), exuIdx) =>
+  v0RFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
+    arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
       val srcIndices: Seq[Int] = V0RegSrcDataSet.flatMap(data => fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(data)).toSeq.sorted
       for (srcIdx <- 0 until fromIQ(iqIdx)(exuIdx).bits.exuParams.numRegSrc) {
-        if (srcIndices.contains(srcIdx) && inRFReadReqSeq.isDefinedAt(srcIdx)) {
+        if (srcIndices.contains(srcIdx)) {
           arbInSeq(srcIdx).valid := v0RFRen(iqIdx)(exuIdx).get(srcIdx)
           arbInSeq(srcIdx).bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrc(srcIdx)
           arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
@@ -183,8 +171,8 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
     }
   }
 
-  vlRFReadArbiter.io.in.zip(vlRFReadReq).zipWithIndex.foreach { case ((arbInSeq2, inRFReadReqSeq), iqIdx) =>
-    arbInSeq2.zip(inRFReadReqSeq).zipWithIndex.foreach { case ((arbInSeq, inRFReadReq), exuIdx) =>
+  vlRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
+    arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
       arbInSeq.headOption.foreach(_.valid := vlRFRen(iqIdx)(exuIdx).get)
       arbInSeq.headOption.foreach(_.bits.addr := fromIQ(iqIdx)(exuIdx).bits.psrcVl.get)
       arbInSeq.headOption.foreach(_.bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx)
diff --git a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
index 0c9c35d5369..39e7790e6b3 100644
--- a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
@@ -32,7 +32,6 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
 
   val common              = Wire(new CommonWireBundle)
   val entryUpdate         = Wire(new EntryBundle)
-  val entryRegNext        = Wire(new EntryBundle)
   val enqDelayValidRegNext= Wire(Bool())
   val hasWakeupIQ         = OptionWrapper(params.hasIQWakeUp, Wire(new CommonIQWakeupBundle))
 
@@ -46,7 +45,11 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
 
   //Reg
   val validReg = GatedValidRegNext(common.validRegNext, false.B)
-  val entryReg = RegNext(entryRegNext)
+  val entryReg = RegNext(Mux(
+    io.commonIn.enq.valid && common.enqReady,
+    io.commonIn.enq.bits,
+    entryUpdate
+  ))
   val enqDelayValidReg = GatedValidRegNext(enqDelayValidRegNext, false.B)
 
   //Wire
@@ -56,12 +59,6 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
     assert(common.enqReady, s"${params.getIQName}'s EnqEntry is not ready when enq is valid\n")
   }
 
-  when(io.commonIn.enq.valid && common.enqReady) {
-    entryRegNext := io.commonIn.enq.bits
-  }.otherwise {
-    entryRegNext := entryUpdate
-  }
-
   when(io.commonIn.enq.valid && common.enqReady) {
     enqDelayValidRegNext := true.B
   }.otherwise {
@@ -148,7 +145,7 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
   EntryRegCommonConnect(common, hasWakeupIQ, validReg, entryUpdate, entryReg, currentStatus, io.commonIn, true, isComp)
 
   //output
-  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryRegNext, currentStatus, io.commonIn, true, isComp)
+  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, currentStatus, io.commonIn, true, isComp)
 }
 
 class EnqEntryVecMem(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) extends EnqEntry(isComp)
@@ -156,7 +153,7 @@ class EnqEntryVecMem(isComp: Boolean)(implicit p: Parameters, params: IssueBlock
 
   require(params.isVecMemIQ, "EnqEntryVecMem can only be instance of VecMem IQ")
 
-  EntryVecMemConnect(io.commonIn, common, validReg, entryReg, entryRegNext, entryUpdate)
+  EntryVecMemConnect(io.commonIn, entryReg, entryUpdate)
 }
 
 object EnqEntry {
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index af371c0f640..5606770c54e 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -103,18 +103,6 @@ object EntryBundles extends HasCircularQueuePtrHelper {
       connectSamePort(deqOg1Payload, payload.og1Payload)
       // imm's width may be diffrent
       deqOg1Payload.imm.foreach(_ := payload.og1Payload.imm.get)
-      deqOg1Payload.rf.zip(status.srcStatus.map(_.psrc)).zip(status.srcStatus.map(_.srcType)).foreach { case ((rf, psrc), srcType) =>
-        // psrc in status array can be pregIdx of IntRegFile or VfRegFile
-        rf.foreach(_.addr := psrc)
-        rf.foreach(_.robIdx := status.robIdx)
-        rf.foreach(_.issueValid := status.issued)
-      }
-      deqOg1Payload.rfVl lazyZip status.srcStatusVl.map(_.psrc) foreach {
-        case (rf, psrc) =>
-          rf.addr := psrc
-          rf.robIdx := status.robIdx
-          rf.issueValid := status.issued
-      }
       deqOg1Payload
     }
   }
@@ -438,7 +426,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     entryUpdate.payload                               := entryReg.payload
   }
 
-  def CommonOutConnect(commonOut: CommonOutBundle, common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, entryUpdate: EntryBundle, entryReg: EntryBundle, entryRegNext: EntryBundle, status: Status, commonIn: CommonInBundle, isEnq: Boolean, isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
+  def CommonOutConnect(commonOut: CommonOutBundle, common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, entryUpdate: EntryBundle, entryReg: EntryBundle, status: Status, commonIn: CommonInBundle, isEnq: Boolean, isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
     val hasIQWakeupGet                                 = hasIQWakeup.getOrElse(0.U.asTypeOf(new CommonIQWakeupBundle))
     commonOut.valid                                   := validReg
     commonOut.issued                                  := entryReg.status.issued
@@ -534,10 +522,10 @@ object EntryBundles extends HasCircularQueuePtrHelper {
       commonOut.uopIdx.get                            := entryReg.payload.og1Payload.uopIdx.get
     }
     commonOut.validRegNext                            := common.validRegNext
-    commonOut.issuedRegNext                           := entryRegNext.status.issued
+    commonOut.issuedRegNext                           := Mux(commonIn.enq.valid && common.enqReady, commonIn.enq.bits.status.issued, entryUpdate.status.issued)
   }
 
-  def EntryVecMemConnect(commonIn: CommonInBundle, common: CommonWireBundle, validReg: Bool, entryReg: EntryBundle, entryRegNext: EntryBundle, entryUpdate: EntryBundle)(implicit p: Parameters, params: IssueBlockParams) = {
+  def EntryVecMemConnect(commonIn: CommonInBundle, entryReg: EntryBundle, entryUpdate: EntryBundle)(implicit p: Parameters, params: IssueBlockParams) = {
     val fromLsq                                        = commonIn.vecMemIn.get
     val isFirstLoad                                    = entryReg.payload.og1Payload.lqIdx.get === fromLsq.lqDeqPtr
     val isVleff                                        = entryReg.payload.og1Payload.vpu.get.isVleff
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index 10ea00a3b6c..85133ce894e 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -1086,7 +1086,7 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward })
   }.reduce(_ +& _))
   XSPerfAccumulate("issue_datasource_noreg", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && (ds.value =/= DataSource.imm) })
   }.reduce(_ +& _))
 
   XSPerfHistogram("issue_datasource_reg_hist", deqBeforeDly.map{ deq =>
@@ -1099,8 +1099,40 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && ds.value === DataSource.forward })
   }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
   XSPerfHistogram("issue_datasource_noreg_hist", deqBeforeDly.map{ deq =>
-    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid })
+    PopCount(deq.bits.dataSources.zipWithIndex.map{ case (ds, j) => deq.valid && (ds.value =/= DataSource.imm) })
   }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
+
+  // deq instr data source count for each futype
+  for (t <- FuType.functionNameMap.keys) {
+    val fuName = FuType.functionNameMap(t)
+    if (params.getFuCfgs.map(_.fuType == t).reduce(_ | _)) {
+      XSPerfAccumulate(s"issue_datasource_reg_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && ds.value === DataSource.reg && deq.bits.fuType === t.U })
+      }.reduce(_ +& _))
+      XSPerfAccumulate(s"issue_datasource_bypass_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && ds.value === DataSource.bypass && deq.bits.fuType === t.U })
+      }.reduce(_ +& _))
+      XSPerfAccumulate(s"issue_datasource_forward_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && ds.value === DataSource.forward && deq.bits.fuType === t.U })
+      }.reduce(_ +& _))
+      XSPerfAccumulate(s"issue_datasource_noreg_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && (ds.value =/= DataSource.imm) && deq.bits.fuType === t.U })
+      }.reduce(_ +& _))
+
+      XSPerfHistogram(s"issue_datasource_reg_hist_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && ds.value === DataSource.reg && deq.bits.fuType === t.U })
+      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
+      XSPerfHistogram(s"issue_datasource_bypass_hist_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && ds.value === DataSource.bypass && deq.bits.fuType === t.U })
+      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
+      XSPerfHistogram(s"issue_datasource_forward_hist_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && ds.value === DataSource.forward && deq.bits.fuType === t.U })
+      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
+      XSPerfHistogram(s"issue_datasource_noreg_hist_futype_${fuName}", deqBeforeDly.map { deq =>
+        PopCount(deq.bits.dataSources.zipWithIndex.map { case (ds, j) => deq.valid && (ds.value =/= DataSource.imm) && deq.bits.fuType === t.U })
+      }.reduce(_ +& _), true.B, 0, params.numDeq * params.numRegSrc + 1, 1)
+    }
+  }
 }
 
 class IssueQueueMemBundle(implicit p: Parameters, params: IssueBlockParams) extends Bundle {
diff --git a/src/main/scala/xiangshan/backend/issue/OthersEntry.scala b/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
index 4248378542f..d767b65a919 100644
--- a/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
@@ -29,12 +29,15 @@ class OthersEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockPar
 
   val common          = Wire(new CommonWireBundle)
   val entryUpdate     = Wire(new EntryBundle)
-  val entryRegNext    = Wire(new EntryBundle)
   val hasWakeupIQ     = OptionWrapper(params.hasIQWakeUp, Wire(new CommonIQWakeupBundle))
 
   //Reg
   val validReg = GatedValidRegNext(common.validRegNext, false.B)
-  val entryReg = RegNext(entryRegNext)
+  val entryReg = RegNext(Mux(
+    io.commonIn.enq.valid,
+    io.commonIn.enq.bits,
+    entryUpdate
+  ))
 
   //Wire
   CommonWireConnect(common, hasWakeupIQ, validReg, entryReg.payload.og1Payload, entryReg.status, io.commonIn, false)
@@ -48,16 +51,10 @@ class OthersEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockPar
     assert(common.enqReady, s"${params.getIQName}'s OthersEntry is not ready when enq is valid\n")
   }
 
-  when(io.commonIn.enq.valid) {
-    entryRegNext := io.commonIn.enq.bits
-  }.otherwise {
-    entryRegNext := entryUpdate
-  }
-
   EntryRegCommonConnect(common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryReg.status, io.commonIn, false, isComp)
 
   //output
-  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryRegNext, entryReg.status, io.commonIn, false, isComp)
+  CommonOutConnect(io.commonOut, common, hasWakeupIQ, validReg, entryUpdate, entryReg, entryReg.status, io.commonIn, false, isComp)
   hasWakeupIQ.foreach(dontTouch(_))
   hasWakeupIQ.foreach(x => dontTouch(x.srcWakeupByIQIsUncertain))
 }
@@ -66,8 +63,7 @@ class OthersEntryVecMem(isComp: Boolean)(implicit p: Parameters, params: IssueBl
   with HasCircularQueuePtrHelper {
 
   require(params.isVecMemIQ, "OthersEntryVecMem can only be instance of VecMem IQ")
-
-  EntryVecMemConnect(io.commonIn, common, validReg, entryReg, entryRegNext, entryUpdate)
+  EntryVecMemConnect(io.commonIn, entryReg, entryUpdate)
 }
 
 object OthersEntry {
```
