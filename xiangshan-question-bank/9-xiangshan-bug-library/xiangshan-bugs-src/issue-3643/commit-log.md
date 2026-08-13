# Commit Log
- Issue: #3643
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3643
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3643
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3643
- Changed files: 6
- Additions: 78
- Deletions: 37

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala`
- `src/main/scala/xiangshan/backend/issue/Entries.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`
- `src/main/scala/xiangshan/backend/issue/Scheduler.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 81d3b6e0ec6..3d06ca3504e 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -225,8 +225,10 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
 
   private val og1Cancel = dataPath.io.og1Cancel
   private val og0Cancel = dataPath.io.og0Cancel
-  private val vlIsZero = intExuBlock.io.vlIsZero.get
-  private val vlIsVlmax = intExuBlock.io.vlIsVlmax.get
+  private val vlFromIntIsZero = intExuBlock.io.vlIsZero.get
+  private val vlFromIntIsVlmax = intExuBlock.io.vlIsVlmax.get
+  private val vlFromVfIsZero = vfExuBlock.io.vlIsZero.get
+  private val vlFromVfIsVlmax = vfExuBlock.io.vlIsVlmax.get
 
   ctrlBlock.io.intIQValidNumVec := intScheduler.io.intIQValidNumVec
   ctrlBlock.io.fpIQValidNumVec := fpScheduler.io.fpIQValidNumVec
@@ -267,8 +269,10 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   intScheduler.io.fromDataPath.og1Cancel := og1Cancel
   intScheduler.io.ldCancel := io.mem.ldCancel
   intScheduler.io.fromDataPath.replaceRCIdx.get := dataPath.io.toWakeupQueueRCIdx.take(params.getIntExuRCWriteSize)
-  intScheduler.io.vlWriteBackInfo.vlIsZero := false.B
-  intScheduler.io.vlWriteBackInfo.vlIsVlmax := false.B
+  intScheduler.io.vlWriteBackInfo.vlFromIntIsZero := false.B
+  intScheduler.io.vlWriteBackInfo.vlFromIntIsVlmax := false.B
+  intScheduler.io.vlWriteBackInfo.vlFromVfIsZero := false.B
+  intScheduler.io.vlWriteBackInfo.vlFromVfIsVlmax := false.B
 
   fpScheduler.io.fromTop.hartId := io.fromTop.hartId
   fpScheduler.io.fromCtrlBlock.flush := ctrlBlock.io.toIssueBlock.flush
@@ -284,8 +288,10 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   fpScheduler.io.fromDataPath.og0Cancel := og0Cancel
   fpScheduler.io.fromDataPath.og1Cancel := og1Cancel
   fpScheduler.io.ldCancel := io.mem.ldCancel
-  fpScheduler.io.vlWriteBackInfo.vlIsZero := false.B
-  fpScheduler.io.vlWriteBackInfo.vlIsVlmax := false.B
+  fpScheduler.io.vlWriteBackInfo.vlFromIntIsZero := false.B
+  fpScheduler.io.vlWriteBackInfo.vlFromIntIsVlmax := false.B
+  fpScheduler.io.vlWriteBackInfo.vlFromVfIsZero := false.B
+  fpScheduler.io.vlWriteBackInfo.vlFromVfIsVlmax := false.B
 
   memScheduler.io.fromTop.hartId := io.fromTop.hartId
   memScheduler.io.fromCtrlBlock.flush := ctrlBlock.io.toIssueBlock.flush
@@ -321,8 +327,10 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   memScheduler.io.fromDataPath.og1Cancel := og1Cancel
   memScheduler.io.ldCancel := io.mem.ldCancel
   memScheduler.io.fromDataPath.replaceRCIdx.get := dataPath.io.toWakeupQueueRCIdx.takeRight(params.getMemExuRCWriteSize)
-  memScheduler.io.vlWriteBackInfo.vlIsZero := vlIsZero
-  memScheduler.io.vlWriteBackInfo.vlIsVlmax := vlIsVlmax
+  memScheduler.io.vlWriteBackInfo.vlFromIntIsZero := vlFromIntIsZero
+  memScheduler.io.vlWriteBackInfo.vlFromIntIsVlmax := vlFromIntIsVlmax
+  memScheduler.io.vlWriteBackInfo.vlFromVfIsZero := vlFromVfIsZero
+  memScheduler.io.vlWriteBackInfo.vlFromVfIsVlmax := vlFromVfIsVlmax
   memScheduler.io.fromOg2Resp.get := og2ForVector.io.toMemIQOg2Resp
 
   vfScheduler.io.fromTop.hartId := io.fromTop.hartId
@@ -339,8 +347,10 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   vfScheduler.io.fromDataPath.og0Cancel := og0Cancel
   vfScheduler.io.fromDataPath.og1Cancel := og1Cancel
   vfScheduler.io.ldCancel := io.mem.ldCancel
-  vfScheduler.io.vlWriteBackInfo.vlIsZero := vlIsZero
-  vfScheduler.io.vlWriteBackInfo.vlIsVlmax := vlIsVlmax
+  vfScheduler.io.vlWriteBackInfo.vlFromIntIsZero := vlFromIntIsZero
+  vfScheduler.io.vlWriteBackInfo.vlFromIntIsVlmax := vlFromIntIsVlmax
+  vfScheduler.io.vlWriteBackInfo.vlFromVfIsZero := vlFromVfIsZero
+  vfScheduler.io.vlWriteBackInfo.vlFromVfIsVlmax := vlFromVfIsVlmax
   vfScheduler.io.fromOg2Resp.get := og2ForVector.io.toVfIQOg2Resp
 
   dataPath.io.hartId := io.fromTop.hartId
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala
index f8f2a673b3d..ef961d4a673 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala
@@ -79,8 +79,8 @@ class VSetRiWvf(cfg: FuConfig)(implicit p: Parameters) extends VSetBase(cfg) {
 
   if (cfg.writeVlRf) io.vtype.get.bits := vsetModule.io.out.vconfig.vtype
   if (cfg.writeVlRf) io.vtype.get.valid := io.out.valid && isVsetvl
-  if (cfg.writeVlRf) io.vlIsZero.get := vl === 0.U
-  if (cfg.writeVlRf) io.vlIsVlmax.get := vl === vlmax
+  if (cfg.writeVlRf) io.vlIsZero.get := io.out.valid && vl === 0.U
+  if (cfg.writeVlRf) io.vlIsVlmax.get := io.out.valid && vl === vlmax
 
   debugIO.vconfig := vsetModule.io.out.vconfig
 }
@@ -104,7 +104,8 @@ class VSetRvfWvf(cfg: FuConfig)(implicit p: Parameters) extends VSetBase(cfg) {
   val isVsetvl = VSETOpType.isVsetvl(in.ctrl.fuOpType)
   val isReadVl = in.ctrl.fuOpType === VSETOpType.csrrvl
   res.vl := Mux(vsetModule.io.out.vconfig.vtype.illegal, 0.U,
-              Mux(VSETOpType.isKeepVl(in.ctrl.fuOpType), oldVL, vsetModule.io.out.vconfig.vl))
+              Mux(VSETOpType.isKeepVl(in.ctrl.fuOpType), 
+                Mux(oldVL < vlmax, oldVL, vlmax), vsetModule.io.out.vconfig.vl))
   res.vtype := vsetModule.io.out.vconfig.vtype
 
   out.res.data := Mux(isReadVl, oldVL,
@@ -114,8 +115,8 @@ class VSetRvfWvf(cfg: FuConfig)(implicit p: Parameters) extends VSetBase(cfg) {
 
   if (cfg.writeVlRf) io.vtype.get.bits := vsetModule.io.out.vconfig.vtype
   if (cfg.writeVlRf) io.vtype.get.valid := isVsetvl && io.out.valid
-  if (cfg.writeVlRf) io.vlIsZero.get := !isReadVl && res.vl === 0.U
-  if (cfg.writeVlRf) io.vlIsVlmax.get := !isReadVl && res.vl === vlmax
+  if (cfg.writeVlRf) io.vlIsZero.get := io.out.valid && !isReadVl && res.vl === 0.U
+  if (cfg.writeVlRf) io.vlIsVlmax.get := io.out.valid && !isReadVl && res.vl === vlmax
 
   debugIO.vconfig := res
 }
diff --git a/src/main/scala/xiangshan/backend/issue/Entries.scala b/src/main/scala/xiangshan/backend/issue/Entries.scala
index ea3fc6d4737..8b977f1c8a2 100644
--- a/src/main/scala/xiangshan/backend/issue/Entries.scala
+++ b/src/main/scala/xiangshan/backend/issue/Entries.scala
@@ -408,8 +408,10 @@ class Entries(implicit p: Parameters, params: IssueBlockParams) extends XSModule
     in.flush                    := io.flush
     in.wakeUpFromWB             := io.wakeUpFromWB
     in.wakeUpFromIQ             := io.wakeUpFromIQ
-    in.vlIsZero                 := io.vlIsZero
-    in.vlIsVlmax                := io.vlIsVlmax
+    in.vlFromIntIsZero          := io.vlFromIntIsZero
+    in.vlFromIntIsVlmax         := io.vlFromIntIsVlmax
+    in.vlFromVfIsZero           := io.vlFromVfIsZero
+    in.vlFromVfIsVlmax          := io.vlFromVfIsVlmax
     in.og0Cancel                := io.og0Cancel
     in.og1Cancel                := io.og1Cancel
     in.ldCancel                 := io.ldCancel
@@ -537,8 +539,10 @@ class EntriesIO(implicit p: Parameters, params: IssueBlockParams) extends XSBund
   // wakeup
   val wakeUpFromWB: MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = Flipped(params.genWBWakeUpSinkValidBundle)
   val wakeUpFromIQ: MixedVec[ValidIO[IssueQueueIQWakeUpBundle]] = Flipped(params.genIQWakeUpSinkValidBundle)
-  val vlIsZero            = Input(Bool())
-  val vlIsVlmax           = Input(Bool())
+  val vlFromIntIsZero     = Input(Bool())
+  val vlFromIntIsVlmax    = Input(Bool())
+  val vlFromVfIsZero      = Input(Bool())
+  val vlFromVfIsVlmax     = Input(Bool())
   val og0Cancel           = Input(ExuVec())
   val og1Cancel           = Input(ExuVec())
   val ldCancel            = Vec(backendParams.LdExuCnt, Flipped(new LoadCancelIO))
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index 7bc328f17fe..4695ea3fe66 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -102,8 +102,10 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val wakeUpFromWB: MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = Flipped(params.genWBWakeUpSinkValidBundle)
     val wakeUpFromIQ: MixedVec[ValidIO[IssueQueueIQWakeUpBundle]] = Flipped(params.genIQWakeUpSinkValidBundle)
     // vl
-    val vlIsZero              = Input(Bool())
-    val vlIsVlmax             = Input(Bool())
+    val vlFromIntIsZero       = Input(Bool())
+    val vlFromIntIsVlmax      = Input(Bool())
+    val vlFromVfIsZero        = Input(Bool())
+    val vlFromVfIsVlmax       = Input(Bool())
     //cancel
     val og0Cancel             = Input(ExuVec())
     val og1Cancel             = Input(ExuVec())
@@ -160,7 +162,8 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     val deqSuccess            = Bool()
     val srcWakeup             = Vec(params.numRegSrc, Bool())
     val srcWakeupByWB         = Vec(params.numRegSrc, Bool())
-    val vlWakeupByWb          = Bool()
+    val vlWakeupByIntWb       = Bool()
+    val vlWakeupByVfWb        = Bool()
     val srcCancelVec          = Vec(params.numRegSrc, Bool())
     val srcLoadCancelVec      = Vec(params.numRegSrc, Bool())
     val srcLoadDependencyNext = Vec(params.numRegSrc, Vec(LoadPipelineWidth, UInt(LoadDependencyWidth.W)))
@@ -200,9 +203,19 @@ object EntryBundles extends HasCircularQueuePtrHelper {
     }
     if (params.numRegSrc == 5) {
       // only when numRegSrc == 5 need vl
-      common.vlWakeupByWb     := common.srcWakeupByWB(4)
+      val wakeUpFromVl = VecInit(commonIn.wakeUpFromWB.map{ bundle => 
+        val psrcSrcTypeVec = status.srcStatus.map(_.psrc) zip status.srcStatus.map(_.srcType)
+        bundle.bits.wakeUpVl(psrcSrcTypeVec(4), bundle.valid)
+      })
+      var numVecWb = params.backendParam.getVfWBExeGroup.size
+      var numV0Wb = params.backendParam.getV0WBExeGroup.size
+      // int wb is first bit of vlwb, which is after vfwb and v0wb
+      common.vlWakeupByIntWb  := wakeUpFromVl(numVecWb + numV0Wb)
+      // vf wb is second bit of wb
+      common.vlWakeupByVfWb   := wakeUpFromVl(numVecWb + numV0Wb + 1)
     } else {
-      common.vlWakeupByWb     := false.B
+      common.vlWakeupByIntWb  := false.B
+      common.vlWakeupByVfWb   := false.B
     }
   }
 
@@ -280,16 +293,21 @@ object EntryBundles extends HasCircularQueuePtrHelper {
       val wakeup = common.srcWakeup(srcIdx)
 
       val ignoreOldVd = Wire(Bool())
-      val vlWakeUpByWb = common.vlWakeupByWb
+      val vlWakeUpByIntWb = common.vlWakeupByIntWb
+      val vlWakeUpByVfWb = common.vlWakeupByVfWb
       val isDependOldvd = entryReg.payload.vpu.isDependOldvd
       val isWritePartVd = entryReg.payload.vpu.isWritePartVd
       val vta = entryReg.payload.vpu.vta
       val vma = entryReg.payload.vpu.vma
       val vm = entryReg.payload.vpu.vm
-      val vlIsZero = commonIn.vlIsZero
-      val vlIsVlmax = commonIn.vlIsVlmax
+      val vlFromIntIsZero = commonIn.vlFromIntIsZero
+      val vlFromIntIsVlmax = commonIn.vlFromIntIsVlmax
+      val vlFromVfIsZero = commonIn.vlFromVfIsZero
+      val vlFromVfIsVlmax = commonIn.vlFromVfIsVlmax
+      val vlIsVlmax = (vlFromIntIsVlmax && vlWakeUpByIntWb) || (vlFromVfIsVlmax && vlWakeUpByVfWb)
+      val vlIsNonZero = (!vlFromIntIsZero && vlWakeUpByIntWb) || (!vlFromVfIsZero && vlWakeUpByVfWb)
       val ignoreTail = vlIsVlmax && (vm =/= 0.U || vma) && !isWritePartVd
-      val ignoreWhole = !vlIsVlmax && (vm =/= 0.U || vma) && vta
+      val ignoreWhole = (vm =/= 0.U || vma) && vta
       val srcIsVec = SrcType.isVp(srcStatus.srcType)
       if (params.numVfSrc > 0 && srcIdx == 2) {
         /**
@@ -298,7 +316,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
           * 2. when vl = 0, we cannot set the srctype to imm because the vd keep the old value
           * 3. when vl = vlmax, we can set srctype to imm when vta is not set
           */
-        ignoreOldVd := srcIsVec && vlWakeUpByWb && !isDependOldvd && !vlIsZero && (ignoreTail || ignoreWhole)
+        ignoreOldVd := srcIsVec && vlIsNonZero && !isDependOldvd && (ignoreTail || ignoreWhole)
       } else {
         ignoreOldVd := false.B
       }
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index 18d8eb930bb..179d2b5b76f 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -57,8 +57,10 @@ class IssueQueueIO()(implicit p: Parameters, params: IssueBlockParams) extends X
   val wbBusyTableWrite = Output(params.genWbFuBusyTableWriteBundle)
   val wakeupFromWB: MixedVec[ValidIO[IssueQueueWBWakeUpBundle]] = Flipped(params.genWBWakeUpSinkValidBundle)
   val wakeupFromIQ: MixedVec[ValidIO[IssueQueueIQWakeUpBundle]] = Flipped(params.genIQWakeUpSinkValidBundle)
-  val vlIsZero = Input(Bool())
-  val vlIsVlmax = Input(Bool())
+  val vlFromIntIsZero = Input(Bool())
+  val vlFromIntIsVlmax = Input(Bool())
+  val vlFromVfIsZero = Input(Bool())
+  val vlFromVfIsVlmax = Input(Bool())
   val og0Cancel = Input(ExuVec())
   val og1Cancel = Input(ExuVec())
   val ldCancel = Vec(backendParams.LduCnt + backendParams.HyuCnt, Flipped(new LoadCancelIO))
@@ -353,8 +355,10 @@ class IssueQueueImp(override val wrapper: IssueQueue)(implicit p: Parameters, va
     }
     entriesIO.wakeUpFromWB                                      := io.wakeupFromWB
     entriesIO.wakeUpFromIQ                                      := wakeupFromIQ
-    entriesIO.vlIsZero                                          := io.vlIsZero
-    entriesIO.vlIsVlmax                                         := io.vlIsVlmax
+    entriesIO.vlFromIntIsZero                                   := io.vlFromIntIsZero
+    entriesIO.vlFromIntIsVlmax                                  := io.vlFromIntIsVlmax
+    entriesIO.vlFromVfIsZero                                    := io.vlFromVfIsZero
+    entriesIO.vlFromVfIsVlmax                                   := io.vlFromVfIsVlmax
     entriesIO.og0Cancel                                         := io.og0Cancel
     entriesIO.og1Cancel                                         := io.og1Cancel
     entriesIO.ldCancel                                          := io.ldCancel
diff --git a/src/main/scala/xiangshan/backend/issue/Scheduler.scala b/src/main/scala/xiangshan/backend/issue/Scheduler.scala
index 47492aea3b3..f499cc1bf7c 100644
--- a/src/main/scala/xiangshan/backend/issue/Scheduler.scala
+++ b/src/main/scala/xiangshan/backend/issue/Scheduler.scala
@@ -80,8 +80,10 @@ class SchedulerIO()(implicit params: SchdBlockParams, p: Parameters) extends XSB
   val toDataPathAfterDelay: MixedVec[MixedVec[DecoupledIO[IssueQueueIssueBundle]]] = MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle))
 
   val vlWriteBackInfo = new Bundle {
-    val vlIsZero = Input(Bool())
-    val vlIsVlmax = Input(Bool())
+    val vlFromIntIsZero  = Input(Bool())
+    val vlFromIntIsVlmax = Input(Bool())
+    val vlFromVfIsZero   = Input(Bool())
+    val vlFromVfIsVlmax  = Input(Bool())
   }
 
   val fromSchedulers = new Bundle {
@@ -384,8 +386,10 @@ abstract class SchedulerImpBase(wrapper: Scheduler)(implicit params: SchdBlockPa
 
   // connect the vl writeback informatino to the issue queues
   issueQueues.zipWithIndex.foreach { case(iq, i) =>
-    iq.io.vlIsVlmax := io.vlWriteBackInfo.vlIsVlmax
-    iq.io.vlIsZero := io.vlWriteBackInfo.vlIsZero
+    iq.io.vlFromIntIsVlmax := io.vlWriteBackInfo.vlFromIntIsVlmax
+    iq.io.vlFromIntIsZero := io.vlWriteBackInfo.vlFromIntIsZero
+    iq.io.vlFromVfIsVlmax := io.vlWriteBackInfo.vlFromVfIsVlmax
+    iq.io.vlFromVfIsZero := io.vlWriteBackInfo.vlFromVfIsZero
   }
 
   private val iqWakeUpOutMap: Map[Int, ValidIO[IssueQueueIQWakeUpBundle]] =
```
