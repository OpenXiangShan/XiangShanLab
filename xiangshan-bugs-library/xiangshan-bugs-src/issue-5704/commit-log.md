# Commit Log
- Issue: #5704
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5704
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5704
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5704
- Changed files: 3
- Additions: 19
- Deletions: 26

## Files
- `src/main/scala/xiangshan/backend/issue/Entries.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/issue/Entries.scala b/src/main/scala/xiangshan/backend/issue/Entries.scala
index 700e8452624..040cc243063 100644
--- a/src/main/scala/xiangshan/backend/issue/Entries.scala
+++ b/src/main/scala/xiangshan/backend/issue/Entries.scala
@@ -68,7 +68,6 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   val validVecRegNext     = Wire(Vec(params.numEntries, Bool()))
   val issuedVecRegNext    = Wire(Vec(params.numEntries, Bool()))
   //src status
-  val dataSourceVec       = Wire(Vec(params.numEntries, Vec(params.numRegSrc, DataSource())))
   val loadDependencyVec   = Wire(Vec(params.numEntries, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W))))
   val exuSourceVec        = OptionWrapper(params.hasIQWakeUp, Wire(Vec(params.numEntries, Vec(params.numRegSrc, ExuSource()))))
   //deq sel
@@ -408,7 +407,6 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   io.srcReady                       := srcReadyVec.asUInt
   io.rfWen                          := rfWenVec.asUInt
   io.fuType                         := fuTypeVec
-  io.dataSources                    := dataSourceVec
   io.exuSources.foreach(_           := exuSourceVec.get)
   io.loadDependency                 := loadDependencyVec
   io.isFirstIssue.zipWithIndex.foreach{ case (isFirstIssue, deqIdx) =>
@@ -444,7 +442,6 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
     srcReadyVec(entryIdx)       := out.srcReady
     fuTypeVec(entryIdx)         := out.fuType
     robIdxVec(entryIdx)         := out.robIdx
-    dataSourceVec(entryIdx)     := out.dataSources
     isFirstIssueVec(entryIdx)   := out.isFirstIssue
     entries(entryIdx)           := out.entry
     deqPortIdxReadVec(entryIdx) := out.deqPortIdxRead
@@ -575,7 +572,6 @@ class EntriesIO(implicit p: Parameters, params: IssueBlockParams) extends XSBund
   val rfWen               = Output(UInt(params.numEntries.W))
   val srcReady            = Output(UInt(params.numEntries.W))
   val fuType              = Vec(params.numEntries, Output(FuType()))
-  val dataSources         = Vec(params.numEntries, Vec(params.numRegSrc, Output(DataSource())))
   val loadDependency      = Vec(params.numEntries, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
   val exuSources          = OptionWrapper(params.hasIQWakeUp, Vec(params.numEntries, Vec(params.numRegSrc, Output(ExuSource()))))
   // for enq.ready timing
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index e8aac1d7f71..f9f7bea50d8 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -166,7 +166,6 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val validRegNext          = Output(Bool())
     val issuedRegNext         = Output(Bool())
     //src
-    val dataSources           = Vec(params.numRegSrc, Output(DataSource()))
     val exuSources            = Option.when(params.hasIQWakeUp)(Vec(params.numRegSrc, Output(ExuSource())))
     //deq
     val isFirstIssue          = Output(Bool())
@@ -454,13 +453,24 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     commonOut.srcReady                                := common.canIssue
     commonOut.fuType                                  := IQFuType.readFuType(status.fuType, params.getFuCfgs.map(_.fuType)).asUInt
     commonOut.robIdx                                  := status.robIdx
-    commonOut.dataSources.zipWithIndex.foreach{ case (dataSourceOut, srcIdx) =>
+    commonOut.isFirstIssue                            := status.firstIssue
+    commonOut.entry.valid                             := validReg
+    commonOut.entry.bits.status                       := entryReg.status
+    commonOut.entry.bits.payload                      := entryReg.payload
+    entryWireGenRen.status                            := entryReg.status
+    entryWireGenRen.payload                           := entryReg.payload
+    commonOut.entry.bits.genXrfRen(entryWireGenRen)
+    if(isEnq) {
+      commonOut.entry.bits.status                     := status
+      entryWireGenRen.status                          := status
+    }
+    commonOut.entry.bits.status.srcStatus.zip(entryWireGenRen.status.srcStatus).zipWithIndex.foreach{ case ((dataSourceOut, entryWire), srcIdx) =>
       val wakeupByIQWithoutCancel = hasIQWakeupGet.srcWakeupByIQWithoutCancel(srcIdx).asUInt.orR
       val wakeupByIQIsUncertain = hasIQWakeupGet.srcWakeupByIQIsUncertain(srcIdx).asUInt.orR
       val wakeupByIQWithoutCancelOH = hasIQWakeupGet.srcWakeupByIQWithoutCancel(srcIdx)
       val isWakeupByMemIQ = wakeupByIQWithoutCancelOH.zip(commonIn.wakeUpFromIQ).filter(_._2.bits.params.isMemExeUnit).map(_._1).fold(false.B)(_ || _)
       val useRegCache = status.srcStatus(srcIdx).useRegCache.getOrElse(false.B) && status.srcStatus(srcIdx).dataSources.readReg
-      dataSourceOut.value                             := (if (isComp)
+      dataSourceOut.dataSources.value                 := (if (isComp)
                                                             if (params.inVfSchd && params.readVfRf && params.hasWakeupFromMem) {
                                                               MuxCase(status.srcStatus(srcIdx).dataSources.value, Seq(
                                                                 (wakeupByIQWithoutCancel && !isWakeupByMemIQ)  -> DataSource.forward,
@@ -477,19 +487,8 @@ object EntryBundles extends HasCircularQueuePtrHelper {
                                                                 useRegCache                                    -> DataSource.regcache,
                                                               ))
                                                           })
+      entryWire.dataSources := dataSourceOut.dataSources
     }
-    commonOut.isFirstIssue                            := status.firstIssue
-    commonOut.entry.valid                             := validReg
-    commonOut.entry.bits.status                       := entryReg.status
-    commonOut.entry.bits.payload                      := entryReg.payload
-    entryWireGenRen.status                            := entryReg.status
-    entryWireGenRen.payload                           := entryReg.payload
-    commonOut.entry.bits.genXrfRen(entryWireGenRen)
-    if(isEnq) {
-      commonOut.entry.bits.status                     := status
-      entryWireGenRen.status                          := status
-    }
-    entryWireGenRen.status.srcStatus.zip(commonOut.dataSources).map{case(ss, ds) => ss.dataSources := ds}
     if (isEnq || !isComp) {
       // Enq and Simp can back to back trans
       commonOut.entry.bits.payload.og1Payload         := RegNext(entryReg.payload.og1Payload)
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index 6811b32698d..816154ec6e1 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -222,8 +222,6 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
   dontTouch(canIssueVec)
   val deqFirstIssueVec = entries.io.isFirstIssue
 
-  val dataSources: Vec[Vec[DataSource]] = entries.io.dataSources
-  val finalDataSources: Vec[Vec[DataSource]] = VecInit(finalDeqSelOHVec.map(oh => Mux1H(oh, dataSources)))
   val loadDependency: Vec[Vec[UInt]] = entries.io.loadDependency
   val finalLoadDependency: IndexedSeq[Vec[UInt]] = VecInit(finalDeqSelOHVec.map(oh => Mux1H(oh, loadDependency)))
   // (entryIdx)(srcIdx)
@@ -882,13 +880,13 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     deq.bits.pdestVl.foreach(_ := deqEntryVec(i).bits.payload.pdestVl.get)
     deq.bits.robIdx := deqEntryVec(i).bits.status.robIdx
 
-    require(deq.bits.dataSources.size <= finalDataSources(i).size)
-    deq.bits.dataSources.zip(finalDataSources(i)).foreach { case (sink, source) => sink := source}
+    val deqDataSources = deqEntryVec.map(_.bits.status.srcStatus.map(_.dataSources))
+    deq.bits.dataSources.zip(deqDataSources(i)).foreach { case (sink, source) => sink := source}
     deq.bits.exuSources.foreach(_.zip(finalExuSources.get(i)).foreach { case (sink, source) => sink := source})
     deq.bits.loadDependency.foreach(_.zip(finalLoadDependency(i)).foreach { case (sink, source) => sink := source})
-    // when alu select jump uop, src0's dataSource change to imm
+    // when alu select jump uop, src0's refren change to false
     if (params.aluDeqNeedPickJump && (i == 0)) {
-      val src0IsReadReg = finalDataSources(i)(0).readReg
+      val src0IsReadReg = deqDataSources(i)(0).readReg
       when(entries.io.aluDeqSelectJump.get && src0IsReadReg){
         deq.bits.rfBankRen.get(0) := 0.U.asTypeOf(deq.bits.rfBankRen.get(0))
       }
@@ -896,7 +894,7 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     else if (params.aluDeqNeedPickJump && (i == 1)) {
       // assign jump uop form alu deq
       when(entries.io.aluDeqSelectJump.get) {
-        deq.bits.dataSources := finalDataSources(0)
+        deq.bits.dataSources := deqDataSources(0)
         deq.bits.exuSources.foreach(_ := deqBeforeDly(0).bits.exuSources.get)
         deq.bits.loadDependency.foreach(_ := deqBeforeDly(0).bits.loadDependency.get)
       }
```
