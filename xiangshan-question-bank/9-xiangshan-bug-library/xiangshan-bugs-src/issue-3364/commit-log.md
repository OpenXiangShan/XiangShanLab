# Commit Log
- Issue: #3364
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3364
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3364
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3364
- Changed files: 5
- Additions: 27
- Deletions: 17

## Files
- `src/main/scala/xiangshan/backend/issue/EnqEntry.scala`
- `src/main/scala/xiangshan/backend/issue/Entries.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/backend/issue/OthersEntry.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
index 22985132801..f6ff0f948d2 100644
--- a/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/EnqEntry.scala
@@ -52,7 +52,7 @@ class EnqEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams
   CommonWireConnect(common, hasWakeupIQ, validReg, currentStatus, io.commonIn, true)
 
   when(io.commonIn.enq.valid) {
-    assert(common.enqReady, "Entry is not ready when enq is valid\n")
+    assert(common.enqReady, s"${params.getIQName}'s EnqEntry is not ready when enq is valid\n")
   }
 
   when(io.commonIn.enq.valid && common.enqReady) {
diff --git a/src/main/scala/xiangshan/backend/issue/Entries.scala b/src/main/scala/xiangshan/backend/issue/Entries.scala
index e66cde5621d..0f8335ce7a2 100644
--- a/src/main/scala/xiangshan/backend/issue/Entries.scala
+++ b/src/main/scala/xiangshan/backend/issue/Entries.scala
@@ -81,6 +81,8 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   val entries             = Wire(Vec(params.numEntries, ValidIO(new EntryBundle)))
   val robIdxVec           = Wire(Vec(params.numEntries, new RobPtr))
   val validVec            = Wire(Vec(params.numEntries, Bool()))
+  val issuedVec           = Wire(Vec(params.numEntries, Bool()))
+  val validForTrans       = VecInit(validVec.zip(issuedVec).map(x => x._1 && !x._2))
   val canIssueVec         = Wire(Vec(params.numEntries, Bool()))
   val fuTypeVec           = Wire(Vec(params.numEntries, FuType()))
   val isFirstIssueVec     = Wire(Vec(params.numEntries, Bool()))
@@ -181,11 +183,11 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
     // note that dispatch does not guarantee the validity of enq entries with low index.
     // that means in some cases enq entry [0] is invalid while enq entry [1] is valid.
     // in this case, enq entry [1] should use result [0] of TransPolicy.
-    othersTransSelVec.get(0).valid := othersTransPolicy.get.io.enqSelOHVec(0).valid && validVec(0)
+    othersTransSelVec.get(0).valid := othersTransPolicy.get.io.enqSelOHVec(0).valid && validForTrans(0)
     othersTransSelVec.get(0).bits  := othersTransPolicy.get.io.enqSelOHVec(0).bits
     if (params.numEnq == 2) {
-      othersTransSelVec.get(1).valid := Mux(!validVec(0), othersTransPolicy.get.io.enqSelOHVec(0).valid, othersTransPolicy.get.io.enqSelOHVec(1).valid)
-      othersTransSelVec.get(1).bits  := Mux(!validVec(0), othersTransPolicy.get.io.enqSelOHVec(0).bits,  othersTransPolicy.get.io.enqSelOHVec(1).bits)
+      othersTransSelVec.get(1).valid := Mux(!validForTrans(0), othersTransPolicy.get.io.enqSelOHVec(0).valid, othersTransPolicy.get.io.enqSelOHVec(1).valid) && validForTrans(1)
+      othersTransSelVec.get(1).bits  := Mux(!validForTrans(0), othersTransPolicy.get.io.enqSelOHVec(0).bits,  othersTransPolicy.get.io.enqSelOHVec(1).bits)
     }
 
     finalOthersTransSelVec.get.zip(othersTransSelVec.get).zipWithIndex.foreach { case ((finalOH, selOH), enqIdx) =>
@@ -219,15 +221,15 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
     // note that dispatch does not guarantee the validity of enq entries with low index.
     // that means in some cases enq entry [0] is invalid while enq entry [1] is valid.
     // in this case, enq entry [1] should use result [0] of TransPolicy.
-    simpTransSelVec.get(0).valid := simpTransPolicy.get.io.enqSelOHVec(0).valid && validVec(0)
+    simpTransSelVec.get(0).valid := simpTransPolicy.get.io.enqSelOHVec(0).valid && validForTrans(0)
     simpTransSelVec.get(0).bits  := simpTransPolicy.get.io.enqSelOHVec(0).bits
-    compTransSelVec.get(0).valid := compTransPolicy.get.io.enqSelOHVec(0).valid && validVec(0)
+    compTransSelVec.get(0).valid := compTransPolicy.get.io.enqSelOHVec(0).valid && validForTrans(0)
     compTransSelVec.get(0).bits  := compTransPolicy.get.io.enqSelOHVec(0).bits
     if (params.numEnq == 2) {
-      simpTransSelVec.get(1).valid := Mux(!validVec(0), simpTransPolicy.get.io.enqSelOHVec(0).valid, simpTransPolicy.get.io.enqSelOHVec(1).valid)
-      simpTransSelVec.get(1).bits  := Mux(!validVec(0), simpTransPolicy.get.io.enqSelOHVec(0).bits,  simpTransPolicy.get.io.enqSelOHVec(1).bits)
-      compTransSelVec.get(1).valid := Mux(!validVec(0), compTransPolicy.get.io.enqSelOHVec(0).valid, compTransPolicy.get.io.enqSelOHVec(1).valid)
-      compTransSelVec.get(1).bits  := Mux(!validVec(0), compTransPolicy.get.io.enqSelOHVec(0).bits,  compTransPolicy.get.io.enqSelOHVec(1).bits)
+      simpTransSelVec.get(1).valid := Mux(!validForTrans(0), simpTransPolicy.get.io.enqSelOHVec(0).valid, simpTransPolicy.get.io.enqSelOHVec(1).valid) && validForTrans(1)
+      simpTransSelVec.get(1).bits  := Mux(!validForTrans(0), simpTransPolicy.get.io.enqSelOHVec(0).bits,  simpTransPolicy.get.io.enqSelOHVec(1).bits)
+      compTransSelVec.get(1).valid := Mux(!validForTrans(0), compTransPolicy.get.io.enqSelOHVec(0).valid, compTransPolicy.get.io.enqSelOHVec(1).valid) && validForTrans(1)
+      compTransSelVec.get(1).bits  := Mux(!validForTrans(0), compTransPolicy.get.io.enqSelOHVec(0).bits,  compTransPolicy.get.io.enqSelOHVec(1).bits)
     }
 
     finalSimpTransSelVec.get.zip(simpTransSelVec.get).zipWithIndex.foreach { case ((finalOH, selOH), enqIdx) =>
@@ -385,6 +387,7 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
   }
 
   io.valid                          := validVec.asUInt
+  io.issued                         := issuedVec.asUInt
   io.canIssue                       := canIssueVec.asUInt
   io.fuType                         := fuTypeVec
   io.dataSources                    := dataSourceVec
@@ -416,6 +419,7 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
       in.fromLsq.get.lqDeqPtr   := io.vecMemIn.get.lqDeqPtr
     }
     validVec(entryIdx)          := out.valid
+    issuedVec(entryIdx)         := out.issued
     canIssueVec(entryIdx)       := out.canIssue
     fuTypeVec(entryIdx)         := out.fuType
     robIdxVec(entryIdx)         := out.robIdx
@@ -538,6 +542,7 @@ class EntriesIO(implicit p: Parameters, params: IssueBlockParams) extends XSBund
   val ldCancel            = Vec(backendParams.LdExuCnt, Flipped(new LoadCancelIO))
   //entries status
   val valid               = Output(UInt(params.numEntries.W))
+  val issued              = Output(UInt(params.numEntries.W))
   val canIssue            = Output(UInt(params.numEntries.W))
   val fuType              = Vec(params.numEntries, Output(FuType()))
   val dataSources         = Vec(params.numEntries, Vec(params.numRegSrc, Output(DataSource())))
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index 50f9e5b4df8..0ae5e675d94 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -124,6 +124,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
   class CommonOutBundle(implicit p: Parameters, params: IssueBlockParams) extends XSBundle {
     //status
     val valid                 = Output(Bool())
+    val issued                = Output(Bool())
     val canIssue              = Output(Bool())
     val fuType                = Output(FuType())
     val robIdx                = Output(new RobPtr)
@@ -372,6 +373,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
   def CommonOutConnect(commonOut: CommonOutBundle, common: CommonWireBundle, hasIQWakeup: Option[CommonIQWakeupBundle], validReg: Bool, entryUpdate: EntryBundle, entryReg: EntryBundle, status: Status, commonIn: CommonInBundle, isEnq: Boolean, isComp: Boolean)(implicit p: Parameters, params: IssueBlockParams) = {
     val hasIQWakeupGet                                 = hasIQWakeup.getOrElse(0.U.asTypeOf(new CommonIQWakeupBundle))
     commonOut.valid                                   := validReg
+    commonOut.issued                                  := entryReg.status.issued
     commonOut.canIssue                                := (if (isComp) (common.canIssue || hasIQWakeupGet.canIssueBypass) && !common.flushed
                                                           else common.canIssue && !common.flushed)
     commonOut.fuType                                  := IQFuType.readFuType(status.fuType, params.getFuCfgs.map(_.fuType)).asUInt
@@ -445,7 +447,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     }
 
     commonOut.enqReady                                := common.enqReady
-    commonOut.transEntry.valid                        := validReg && !common.flushed && !common.deqSuccess
+    commonOut.transEntry.valid                        := validReg && !common.flushed && !status.issued
     commonOut.transEntry.bits                         := entryUpdate
     // debug
     commonOut.entryInValid                            := commonIn.enq.valid
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index d41a6475282..ccff6ac2547 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -217,6 +217,8 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
   val finalDeqSelOHVec    = Wire(Vec(params.numDeq, UInt(params.numEntries.W)))
 
   val validVec = VecInit(entries.io.valid.asBools)
+  val issuedVec = VecInit(entries.io.issued.asBools)
+  val requestForTrans = VecInit(validVec.zip(issuedVec).map(x => x._1 && !x._2))
   val canIssueVec = VecInit(entries.io.canIssue.asBools)
   dontTouch(canIssueVec)
   val deqFirstIssueVec = entries.io.isFirstIssue
@@ -443,9 +445,9 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
     else {
       simpAgeDetectRequest.get(0) := canIssueVec.asUInt(params.numEnq + params.numSimp - 1, params.numEnq)
       simpAgeDetectRequest.get(1) := DontCare
-      simpAgeDetectRequest.get(params.numDeq) := VecInit(validVec.drop(params.numEnq).take(params.numSimp)).asUInt
+      simpAgeDetectRequest.get(params.numDeq) := VecInit(requestForTrans.drop(params.numEnq).take(params.numSimp)).asUInt
       if (params.numEnq == 2) {
-        simpAgeDetectRequest.get(params.numDeq + 1) := VecInit(validVec.drop(params.numEnq).take(params.numSimp)).asUInt & ~simpEntryOldestSel.get(params.numDeq).bits
+        simpAgeDetectRequest.get(params.numDeq + 1) := VecInit(requestForTrans.drop(params.numEnq).take(params.numSimp)).asUInt & ~simpEntryOldestSel.get(params.numDeq).bits
       }
 
       simpEntryOldestSel.get := AgeDetector(numEntries = params.numSimp,
@@ -513,9 +515,9 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
       deqCanIssue.zipWithIndex.foreach { case (req, i) =>
         simpAgeDetectRequest.get(i) := req(params.numEnq + params.numSimp - 1, params.numEnq)
       }
-      simpAgeDetectRequest.get(params.numDeq) := VecInit(validVec.drop(params.numEnq).take(params.numSimp)).asUInt
+      simpAgeDetectRequest.get(params.numDeq) := VecInit(requestForTrans.drop(params.numEnq).take(params.numSimp)).asUInt
       if (params.numEnq == 2) {
-        simpAgeDetectRequest.get(params.numDeq + 1) := VecInit(validVec.drop(params.numEnq).take(params.numSimp)).asUInt & ~simpEntryOldestSel.get(params.numDeq).bits
+        simpAgeDetectRequest.get(params.numDeq + 1) := VecInit(requestForTrans.drop(params.numEnq).take(params.numSimp)).asUInt & ~simpEntryOldestSel.get(params.numDeq).bits
       }
 
       simpEntryOldestSel.get := AgeDetector(numEntries = params.numSimp,
@@ -816,6 +818,7 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
 
   // Todo: better counter implementation
   private val enqHasValid = validVec.take(params.numEnq).reduce(_ | _)
+  private val enqHasIssued = validVec.zip(issuedVec).take(params.numEnq).map(x => x._1 & x._2).reduce(_ | _)
   private val enqEntryValidCnt = PopCount(validVec.take(params.numEnq))
   private val othersValidCnt = PopCount(validVec.drop(params.numEnq))
   private val enqEntryValidCntDeq0 = PopCount(
@@ -849,7 +852,7 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
   private val othersLeftOne = othersLeftOneCaseVec.map(_ === VecInit(validVec.drop(params.numEnq)).asUInt).reduce(_ | _)
   private val othersCanotIn = othersLeftOne || validVec.drop(params.numEnq).reduce(_ & _)
 
-  io.enq.foreach(_.ready := !othersCanotIn || !enqHasValid)
+  io.enq.foreach(_.ready := (!othersCanotIn || !enqHasValid) && !enqHasIssued)
   io.status.empty := !Cat(validVec).orR
   io.status.full := othersCanotIn
   io.status.validCnt := PopCount(validVec)
diff --git a/src/main/scala/xiangshan/backend/issue/OthersEntry.scala b/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
index 95e83a6c04b..b0f0276c0ba 100644
--- a/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
+++ b/src/main/scala/xiangshan/backend/issue/OthersEntry.scala
@@ -44,7 +44,7 @@ class OthersEntry(isComp: Boolean)(implicit p: Parameters, params: IssueBlockPar
   }
 
   when(io.commonIn.enq.valid) {
-    assert(common.enqReady, "Entry is not ready when enq is valid\n")
+    assert(common.enqReady, s"${params.getIQName}'s OthersEntry is not ready when enq is valid\n")
   }
 
   when(io.commonIn.enq.valid) {
```
