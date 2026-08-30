# Commit Log
- Issue: #6286
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6286
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6286
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6286
- Changed files: 11
- Additions: 458
- Deletions: 21

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/dispatch/Dispatch.scala`
- `src/main/scala/xiangshan/mem/Bundles.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`
- `src/main/scala/xiangshan/mem/mdp/StoreSet.scala`
- `src/main/scala/xiangshan/mem/pipeline/Bundles.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index ba10a9f14b7..d078e2fa1d5 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -602,6 +602,53 @@ class MemPredUpdateReq(implicit p: Parameters) extends XSBundle  {
   val stpc = UInt(MemPredPCWidth.W)
 }
 
+class StoreSetPredDBEntry(implicit p: Parameters) extends XSBundle {
+  val timeCnt = UInt(64.W)
+  val robIdx = UInt(log2Ceil(RobSize).W)
+  val foldPc = UInt(MemPredPCWidth.W)
+  val isStore = Bool()
+  val ssid = UInt(SSIDWidth.W)
+  val ssitStrict = Bool()
+  val lfstShouldWait = Bool()
+  val lfstNotIssuedStoreGt1 = Bool()
+  val finalLoadWaitBit = Bool()
+  val finalLoadWaitStrict = Bool()
+}
+
+class StoreSetTrainDBEntry(implicit p: Parameters) extends XSBundle {
+  val timeCnt = UInt(64.W)
+  val ldFoldPc = UInt(MemPredPCWidth.W)
+  val stFoldPc = UInt(MemPredPCWidth.W)
+}
+
+class StoreSetUpdateDBEntry(implicit p: Parameters) extends XSBundle {
+  val timeCnt = UInt(64.W)
+  val ldFoldPc = UInt(MemPredPCWidth.W)
+  val stFoldPc = UInt(MemPredPCWidth.W)
+  val loadOldSSID = UInt(SSIDWidth.W)
+  val storeOldSSID = UInt(SSIDWidth.W)
+  val loadOldStrict = Bool()
+  val winnerSSID = UInt(SSIDWidth.W)
+  val newLoadSSID = UInt(SSIDWidth.W)
+  val newLoadStrict = Bool()
+  val updateType = UInt(3.W)
+}
+
+class StoreSetLoadUnitCheckDBEntry(implicit p: Parameters) extends XSBundle {
+  val timeCnt = UInt(64.W)
+  val robIdx = UInt(log2Ceil(RobSize).W)
+  val foldPc = UInt(MemPredPCWidth.W)
+  val ssid = UInt(SSIDWidth.W)
+  val loadSqIdx = UInt(log2Ceil(StoreQueueSize).W)
+  val storeSqIdx = UInt(log2Ceil(StoreQueueSize).W)
+  val loadWaitBit = Bool()
+  val loadWaitStrict = Bool()
+  val mdpAddrValid = Bool()
+  val mdpAddrStrict = Bool()
+  val mdpAddrHit = Bool()
+  val storeSqIdxValid = Bool()
+}
+
 class CustomCSRCtrlIO(implicit p: Parameters) extends XSBundle {
   // Prefetcher
   val pf_ctrl = Output(new PrefetchCtrl)
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index cb268d5906c..8209a72f5f1 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -827,6 +827,7 @@ trait HasXSParameter {
   def LFSTSize = 64
   def SSIDWidth = log2Up(LFSTSize)
   def LFSTWidth = 2
+  def strictResetPeriod = 8192
   def StoreSetEnable = true // LWT will be disabled if SS is enabled
   def LFSTEnable = true
 
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 452b9cfbb2b..0b2e404dd60 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -1191,7 +1191,7 @@ object Bundles {
       uop.v0Wen          := this.v0Wen.getOrElse(false.B)
       uop.vlWen          := this.vlWen.getOrElse(false.B)
       uop.flushPipe      := this.flushPipe.getOrElse(false.B)
-      uop.pc             := this.pc.getOrElse(0.U) + (this.ftqOffset.getOrElse(0.U) << instOffsetBits)
+      uop.pc             := this.pc.getOrElse(0.U)
       uop.loadWaitBit    := this.loadWaitBit.getOrElse(false.B)
       uop.waitForRobIdx  := this.waitForRobIdx.getOrElse(0.U.asTypeOf(new RobPtr))
       uop.storeSetHit    := this.storeSetHit.getOrElse(false.B)
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index b5a046fee26..dc412c0bf36 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -237,6 +237,21 @@ class CtrlBlockImp(
   }
   memCtrl.io.memPredUpdate.valid := RegNext(mdpTrainValid) // pc is ready, 1 cycle later
 
+  // StoreSet ChiselDB trace
+  val storeSetTrainHartId = p(XSCoreParamsKey).HartId
+  val storeSetTrainTable = ChiselDB.createTable(s"StoreSetTrainDB$storeSetTrainHartId", new StoreSetTrainDBEntry, basicDB = false)
+  val storeSetTrainEntry = Wire(new StoreSetTrainDBEntry)
+  storeSetTrainEntry.timeCnt := GTimer()
+  storeSetTrainEntry.ldFoldPc := memCtrl.io.memPredUpdate.ldpc
+  storeSetTrainEntry.stFoldPc := memCtrl.io.memPredUpdate.stpc
+  storeSetTrainTable.log(
+    data = storeSetTrainEntry,
+    en = memCtrl.io.memPredUpdate.valid,
+    site = s"CtrlBlock$storeSetTrainHartId",
+    clock = clock,
+    reset = reset
+  )
+
   for ((pcMemIdx, i) <- pcMemRdIndexes("bjuPc").zipWithIndex) {
     val ren = io.toDataPath.pcToDataPathIO.fromDataPathValid(i)
     val raddr = io.toDataPath.pcToDataPathIO.fromDataPathFtqPtr(i).value
diff --git a/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala b/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
index 997f25a652b..e1563f7a35a 100644
--- a/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
@@ -760,12 +760,14 @@ class Dispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with
     io.lfst.req(i).bits.isstore := isStore(i)
     io.lfst.req(i).bits.ssid := updatedUop(i).ssid
     io.lfst.req(i).bits.robIdx := updatedUop(i).robIdx // speculatively assigned in rename
+    io.lfst.req(i).bits.perfStrictPred := fromRename(i).bits.loadWaitStrict
 
     // override load delay ctrl signal with store set result
     if(StoreSetEnable) {
       fromRenameUpdate(i).bits.loadWaitBit := io.lfst.resp(i).bits.shouldWait
       fromRenameUpdate(i).bits.waitForRobIdx := io.lfst.resp(i).bits.robIdx
-      fromRenameUpdate(i).bits.loadWaitStrict := fromRename(i).bits.loadWaitStrict && io.lfst.resp(i).bits.shouldWait
+      fromRenameUpdate(i).bits.loadWaitStrict := fromRename(i).bits.loadWaitStrict && // filter strict pprediction
+        io.lfst.resp(i).bits.shouldWait && io.lfst.resp(i).bits.strictShouldWait
     } else {
       fromRenameUpdate(i).bits.loadWaitBit := isLs(i) && !isStore(i) && fromRename(i).bits.loadWaitBit
     }
@@ -788,6 +790,33 @@ class Dispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with
     }
   }
 
+  // StoreSet ChiselDB trace
+  val storeSetPredHartId = p(XSCoreParamsKey).HartId
+  val storeSetPredTable = ChiselDB.createTable(s"StoreSetPredDB$storeSetPredHartId", new StoreSetPredDBEntry, basicDB = false)
+  for (i <- 0 until RenameWidth) {
+    val storeSetPredEntry = Wire(new StoreSetPredDBEntry)
+    storeSetPredEntry.timeCnt := GTimer()
+    storeSetPredEntry.robIdx := updatedUop(i).robIdx.value
+    storeSetPredEntry.foldPc := updatedUop(i).debug
+      .map(debug => XORFold(debug.pc(VAddrBits - 1, 1), MemPredPCWidth))
+      .getOrElse(0.U(MemPredPCWidth.W))
+    storeSetPredEntry.isStore := isStore(i)
+    storeSetPredEntry.ssid := updatedUop(i).ssid
+    storeSetPredEntry.ssitStrict := fromRename(i).bits.loadWaitStrict
+    storeSetPredEntry.lfstShouldWait := io.lfst.resp(i).bits.shouldWait
+    storeSetPredEntry.lfstNotIssuedStoreGt1 := io.lfst.resp(i).bits.perfNotIssuedStoreGt1
+    storeSetPredEntry.finalLoadWaitBit := fromRenameUpdate(i).bits.loadWaitBit
+    storeSetPredEntry.finalLoadWaitStrict := fromRenameUpdate(i).bits.loadWaitStrict
+
+    storeSetPredTable.log(
+      data = storeSetPredEntry,
+      en = fromRename(i).fire && updatedUop(i).storeSetHit,
+      site = s"Dispatch$storeSetPredHartId",
+      clock = clock,
+      reset = reset
+    )
+  }
+
   // store set perf count
   XSPerfAccumulate("waittable_load_wait", PopCount((0 until RenameWidth).map(i =>
     fromRename(i).fire && fromRename(i).bits.loadWaitBit && !isStore(i) && isLs(i)
@@ -796,7 +825,8 @@ class Dispatch(implicit p: Parameters) extends XSModule with HasPerfEvents with
     fromRename(i).fire && fromRenameUpdate(i).bits.loadWaitBit && !isStore(i) && isLs(i)
   )))
   XSPerfAccumulate("storeset_load_strict_wait", PopCount((0 until RenameWidth).map(i =>
-    fromRename(i).fire && fromRenameUpdate(i).bits.loadWaitBit && updatedUop(i).loadWaitStrict && !isStore(i) && isLs(i)
+    fromRename(i).fire && fromRenameUpdate(i).bits.loadWaitBit &&
+      fromRenameUpdate(i).bits.loadWaitStrict && !isStore(i) && isLs(i)
   )))
   XSPerfAccumulate("storeset_store_wait", PopCount((0 until RenameWidth).map(i =>
     fromRename(i).fire && fromRenameUpdate(i).bits.loadWaitBit && isStore(i)
diff --git a/src/main/scala/xiangshan/mem/Bundles.scala b/src/main/scala/xiangshan/mem/Bundles.scala
index 4d9a42bf6b9..e5975c7463f 100644
--- a/src/main/scala/xiangshan/mem/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/Bundles.scala
@@ -125,6 +125,25 @@ object Bundles {
     val matchInvalid = Bool()
     val addrInvalid = Valid(new SqPtr)
     val dataInvalid = Valid(new SqPtr)
+    val perfMdpAddrValid = Bool()
+    val perfMdpAddrStrict = Bool()
+    val perfMdpAddrHit = Bool()
+    val perfWaitStoreRetired = Bool()
+  }
+
+  class PerfMdpAddr(implicit p: Parameters) extends XSBundle {
+    val loadUnitNonStrictHit = Bool()
+    val loadUnitNonStrictMiss = Bool()
+    val loadUnitStrictHit = Bool()
+    val loadUnitStrictMiss = Bool()
+    val replayNonStrictHit = Bool()
+    val replayNonStrictMiss = Bool()
+    val replayStrictHit = Bool()
+    val replayStrictMiss = Bool()
+    val waitStoreRetired = Bool()
+    val perfAtRobHead = Bool()
+    val perfAtLqHead = Bool()
+    val perfLqFull = Bool()
   }
 
   class UncacheForwardResp(implicit p: Parameters) extends SbufferForwardResp // ?
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 62a32da8325..7bb65fc649c 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -861,6 +861,11 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     io.mem_to_ooo.ldCancel(i).ld2Cancel := newLoadUnits(i).io.cancel
     io.mem_to_ooo.wakeup(i) := newLoadUnits(i).io.wakeup
 
+    // Perf-only head/full qualifiers for MDP counters.
+    newLoadUnits(i).io.perfRobHeadPtr := io.ooo_to_mem.lsqio.pendingPtr
+    newLoadUnits(i).io.perfLqHeadPtr := lsq.io.lqDeqPtr
+    newLoadUnits(i).io.perfLqFull := lsq.io.lqFull
+
     // software prefetch to frontend (prefetch.i)
     io.ifetchPrefetch(i) <> newLoadUnits(i).io.swInstrPrefetch
 
@@ -1579,6 +1584,121 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   XSPerfHistogram("store_iq_deq_count", stDeqCount, true.B, 0, StAddrCnt + 1)
   XSPerfAccumulate("ls_iq_deq_count", iqDeqCount)
 
+  val perfMdpAddr = newLoadUnits.map(_.io.perfMdpAddr)
+  val perfLoadUnitMdpNonStrictAddrHit = PopCount(perfMdpAddr.map(_.loadUnitNonStrictHit))
+  val perfLoadUnitMdpNonStrictAddrMiss = PopCount(perfMdpAddr.map(_.loadUnitNonStrictMiss))
+  val perfLoadUnitMdpStrictAddrHit = PopCount(perfMdpAddr.map(_.loadUnitStrictHit))
+  val perfLoadUnitMdpStrictAddrMiss = PopCount(perfMdpAddr.map(_.loadUnitStrictMiss))
+  val perfReplayMdpNonStrictAddrHit = PopCount(perfMdpAddr.map(_.replayNonStrictHit))
+  val perfReplayMdpNonStrictAddrMiss = PopCount(perfMdpAddr.map(_.replayNonStrictMiss))
+  val perfReplayMdpStrictAddrHit = PopCount(perfMdpAddr.map(_.replayStrictHit))
+  val perfReplayMdpStrictAddrMiss = PopCount(perfMdpAddr.map(_.replayStrictMiss))
+  val perfMdpWaitStoreRetired = PopCount(perfMdpAddr.map(_.waitStoreRetired))
+  val perfLoadUnitMdpAddrHit = perfLoadUnitMdpNonStrictAddrHit +& perfLoadUnitMdpStrictAddrHit
+  val perfLoadUnitMdpAddrMiss = perfLoadUnitMdpNonStrictAddrMiss +& perfLoadUnitMdpStrictAddrMiss
+  val perfReplayMdpAddrHit = perfReplayMdpNonStrictAddrHit +& perfReplayMdpStrictAddrHit
+  val perfReplayMdpAddrMiss = perfReplayMdpNonStrictAddrMiss +& perfReplayMdpStrictAddrMiss
+  val perfMdpSuccessNonStrictAddrHit = perfLoadUnitMdpNonStrictAddrHit +& perfReplayMdpNonStrictAddrHit
+  val perfMdpSuccessNonStrictAddrMiss = perfLoadUnitMdpNonStrictAddrMiss +& perfReplayMdpNonStrictAddrMiss
+  val perfMdpSuccessStrictAddrHit = perfLoadUnitMdpStrictAddrHit +& perfReplayMdpStrictAddrHit
+  val perfMdpSuccessStrictAddrMiss = perfLoadUnitMdpStrictAddrMiss +& perfReplayMdpStrictAddrMiss
+  val perfMdpSuccessAddrHit = perfMdpSuccessNonStrictAddrHit +& perfMdpSuccessStrictAddrHit
+  val perfMdpSuccessAddrMiss = perfMdpSuccessNonStrictAddrMiss +& perfMdpSuccessStrictAddrMiss
+
+  val perfLoadUnitMdpNonStrictAddrHitRobHead =
+    PopCount(perfMdpAddr.map(e => e.loadUnitNonStrictHit && e.perfAtRobHead))
+  val perfLoadUnitMdpNonStrictAddrMissRobHead =
+    PopCount(perfMdpAddr.map(e => e.loadUnitNonStrictMiss && e.perfAtRobHead))
+  val perfLoadUnitMdpStrictAddrHitRobHead =
+    PopCount(perfMdpAddr.map(e => e.loadUnitStrictHit && e.perfAtRobHead))
+  val perfLoadUnitMdpStrictAddrMissRobHead =
+    PopCount(perfMdpAddr.map(e => e.loadUnitStrictMiss && e.perfAtRobHead))
+  val perfReplayMdpNonStrictAddrHitRobHead =
+    PopCount(perfMdpAddr.map(e => e.replayNonStrictHit && e.perfAtRobHead))
+  val perfReplayMdpNonStrictAddrMissRobHead =
+    PopCount(perfMdpAddr.map(e => e.replayNonStrictMiss && e.perfAtRobHead))
+  val perfReplayMdpStrictAddrHitRobHead =
+    PopCount(perfMdpAddr.map(e => e.replayStrictHit && e.perfAtRobHead))
+  val perfReplayMdpStrictAddrMissRobHead =
+    PopCount(perfMdpAddr.map(e => e.replayStrictMiss && e.perfAtRobHead))
+  val perfLoadUnitMdpAddrHitRobHead =
+    perfLoadUnitMdpNonStrictAddrHitRobHead +& perfLoadUnitMdpStrictAddrHitRobHead
+  val perfLoadUnitMdpAddrMissRobHead =
+    perfLoadUnitMdpNonStrictAddrMissRobHead +& perfLoadUnitMdpStrictAddrMissRobHead
+  val perfReplayMdpAddrHitRobHead =
+    perfReplayMdpNonStrictAddrHitRobHead +& perfReplayMdpStrictAddrHitRobHead
+  val perfReplayMdpAddrMissRobHead =
+    perfReplayMdpNonStrictAddrMissRobHead +& perfReplayMdpStrictAddrMissRobHead
+  val perfMdpSuccessNonStrictAddrHitRobHead =
+    perfLoadUnitMdpNonStrictAddrHitRobHead +& perfReplayMdpNonStrictAddrHitRobHead
+  val perfMdpSuccessNonStrictAddrMissRobHead =
+    perfLoadUnitMdpNonStrictAddrMissRobHead +& perfReplayMdpNonStrictAddrMissRobHead
+  val perfMdpSuccessStrictAddrHitRobHead =
+    perfLoadUnitMdpStrictAddrHitRobHead +& perfReplayMdpStrictAddrHitRobHead
+  val perfMdpSuccessStrictAddrMissRobHead =
+    perfLoadUnitMdpStrictAddrMissRobHead +& perfReplayMdpStrictAddrMissRobHead
+  val perfMdpSuccessAddrHitRobHead =
+    perfMdpSuccessNonStrictAddrHitRobHead +& perfMdpSuccessStrictAddrHitRobHead
+  val perfMdpSuccessAddrMissRobHead =
+    perfMdpSuccessNonStrictAddrMissRobHead +& perfMdpSuccessStrictAddrMissRobHead
+
+  val perfReplayMdpNonStrictAddrHitLqHeadFull =
+    PopCount(perfMdpAddr.map(e => e.replayNonStrictHit && e.perfAtLqHead && e.perfLqFull))
+  val perfReplayMdpNonStrictAddrMissLqHeadFull =
+    PopCount(perfMdpAddr.map(e => e.replayNonStrictMiss && e.perfAtLqHead && e.perfLqFull))
+  val perfReplayMdpStrictAddrHitLqHeadFull =
+    PopCount(perfMdpAddr.map(e => e.replayStrictHit && e.perfAtLqHead && e.perfLqFull))
+  val perfReplayMdpStrictAddrMissLqHeadFull =
+    PopCount(perfMdpAddr.map(e => e.replayStrictMiss && e.perfAtLqHead && e.perfLqFull))
+  val perfReplayMdpAddrHitLqHeadFull =
+    perfReplayMdpNonStrictAddrHitLqHeadFull +& perfReplayMdpStrictAddrHitLqHeadFull
+  val perfReplayMdpAddrMissLqHeadFull =
+    perfReplayMdpNonStrictAddrMissLqHeadFull +& perfReplayMdpStrictAddrMissLqHeadFull
+
+  XSPerfAccumulate("loadunit_mdp_hit_addr_hit", perfLoadUnitMdpAddrHit)
+  XSPerfAccumulate("loadunit_mdp_hit_addr_miss", perfLoadUnitMdpAddrMiss)
+  XSPerfAccumulate("loadunit_mdp_hit_non_strict_addr_hit", perfLoadUnitMdpNonStrictAddrHit)
+  XSPerfAccumulate("loadunit_mdp_hit_non_strict_addr_miss", perfLoadUnitMdpNonStrictAddrMiss)
+  XSPerfAccumulate("loadunit_mdp_hit_strict_addr_hit", perfLoadUnitMdpStrictAddrHit)
+  XSPerfAccumulate("loadunit_mdp_hit_strict_addr_miss", perfLoadUnitMdpStrictAddrMiss)
+  XSPerfAccumulate("replay_mdp_hit_addr_hit", perfReplayMdpAddrHit)
+  XSPerfAccumulate("replay_mdp_hit_addr_miss", perfReplayMdpAddrMiss)
+  XSPerfAccumulate("replay_mdp_hit_non_strict_addr_hit", perfReplayMdpNonStrictAddrHit)
+  XSPerfAccumulate("replay_mdp_hit_non_strict_addr_miss", perfReplayMdpNonStrictAddrMiss)
+  XSPerfAccumulate("replay_mdp_hit_strict_addr_hit", perfReplayMdpStrictAddrHit)
+  XSPerfAccumulate("replay_mdp_hit_strict_addr_miss", perfReplayMdpStrictAddrMiss)
+  XSPerfAccumulate("mdp_hit_addr_hit", perfMdpSuccessAddrHit)
+  XSPerfAccumulate("mdp_hit_addr_miss", perfMdpSuccessAddrMiss)
+  XSPerfAccumulate("mdp_hit_non_strict_addr_hit", perfMdpSuccessNonStrictAddrHit)
+  XSPerfAccumulate("mdp_hit_non_strict_addr_miss", perfMdpSuccessNonStrictAddrMiss)
+  XSPerfAccumulate("mdp_hit_strict_addr_hit", perfMdpSuccessStrictAddrHit)
+  XSPerfAccumulate("mdp_hit_strict_addr_miss", perfMdpSuccessStrictAddrMiss)
+  XSPerfAccumulate("mdp_wait_store_retired", perfMdpWaitStoreRetired)
+  XSPerfAccumulate("loadunit_mdp_hit_addr_hit_rob_head", perfLoadUnitMdpAddrHitRobHead)
+  XSPerfAccumulate("loadunit_mdp_hit_addr_miss_rob_head", perfLoadUnitMdpAddrMissRobHead)
+  XSPerfAccumulate("loadunit_mdp_hit_non_strict_addr_hit_rob_head", perfLoadUnitMdpNonStrictAddrHitRobHead)
+  XSPerfAccumulate("loadunit_mdp_hit_non_strict_addr_miss_rob_head", perfLoadUnitMdpNonStrictAddrMissRobHead)
+  XSPerfAccumulate("loadunit_mdp_hit_strict_addr_hit_rob_head", perfLoadUnitMdpStrictAddrHitRobHead)
+  XSPerfAccumulate("loadunit_mdp_hit_strict_addr_miss_rob_head", perfLoadUnitMdpStrictAddrMissRobHead)
+  XSPerfAccumulate("replay_mdp_hit_addr_hit_rob_head", perfReplayMdpAddrHitRobHead)
+  XSPerfAccumulate("replay_mdp_hit_addr_miss_rob_head", perfReplayMdpAddrMissRobHead)
+  XSPerfAccumulate("replay_mdp_hit_non_strict_addr_hit_rob_head", perfReplayMdpNonStrictAddrHitRobHead)
+  XSPerfAccumulate("replay_mdp_hit_non_strict_addr_miss_rob_head", perfReplayMdpNonStrictAddrMissRobHead)
+  XSPerfAccumulate("replay_mdp_hit_strict_addr_hit_rob_head", perfReplayMdpStrictAddrHitRobHead)
+  XSPerfAccumulate("replay_mdp_hit_strict_addr_miss_rob_head", perfReplayMdpStrictAddrMissRobHead)
+  XSPerfAccumulate("mdp_hit_addr_hit_rob_head", perfMdpSuccessAddrHitRobHead)
+  XSPerfAccumulate("mdp_hit_addr_miss_rob_head", perfMdpSuccessAddrMissRobHead)
+  XSPerfAccumulate("mdp_hit_non_strict_addr_hit_rob_head", perfMdpSuccessNonStrictAddrHitRobHead)
+  XSPerfAccumulate("mdp_hit_non_strict_addr_miss_rob_head", perfMdpSuccessNonStrictAddrMissRobHead)
+  XSPerfAccumulate("mdp_hit_strict_addr_hit_rob_head", perfMdpSuccessStrictAddrHitRobHead)
+  XSPerfAccumulate("mdp_hit_strict_addr_miss_rob_head", perfMdpSuccessStrictAddrMissRobHead)
+  XSPerfAccumulate("replay_mdp_hit_addr_hit_lq_head_full", perfReplayMdpAddrHitLqHeadFull)
+  XSPerfAccumulate("replay_mdp_hit_addr_miss_lq_head_full", perfReplayMdpAddrMissLqHeadFull)
+  XSPerfAccumulate("replay_mdp_hit_non_strict_addr_hit_lq_head_full", perfReplayMdpNonStrictAddrHitLqHeadFull)
+  XSPerfAccumulate("replay_mdp_hit_non_strict_addr_miss_lq_head_full", perfReplayMdpNonStrictAddrMissLqHeadFull)
+  XSPerfAccumulate("replay_mdp_hit_strict_addr_hit_lq_head_full", perfReplayMdpStrictAddrHitLqHeadFull)
+  XSPerfAccumulate("replay_mdp_hit_strict_addr_miss_lq_head_full", perfReplayMdpStrictAddrMissLqHeadFull)
+
   val pfevent = Module(new PFEvent)
   pfevent.io.distribute_csr := csrCtrl.distribute_csr
   val csrevents = pfevent.io.hpmevent.slice(16,24)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index 42239ad2d3a..c0d4b107ec0 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -529,6 +529,7 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       val s2LoadMaskEnd      = RegEnable(UIntToMask(MemorySize.CalculateSelectMask(s1LoadStart, s1LoadEnd), VLENB), s1Valid)
       val s2DataInvalidSqIdx = RegEnable(s1DataInvalidSqIdx, s1Valid)
       val s2LoadWaitStrict   = RegEnable(s1LoadWaitStrict, s1Valid)
+      val s2OverlapMask      = RegEnable(s1OverlapMask, s1Valid)
       val s2WaitStrictSqIdx  = RegEnable(s1LoadSqIdx - 1.U, s1Valid)
       val s2MultiMatch       = RegEnable(s1MultiMatch, s1Valid)
       val s2LoadPaddr        = RegEnable(s1QueryPaddr, s1Valid)
@@ -623,6 +624,23 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       s2Resp.bits.matchInvalid     := s2PaddrNoMatch && !s2Cross4KPage && s2SafeForward // if cross Page/multi match, let load replay.
       s2Resp.valid                 := s2Valid
 
+      // Perf-only response fields
+      val perfS1HasWaitStore = RegEnable(s0Req.bits.loadWaitBit, s0Valid)
+      val perfS2HasWaitStore = RegEnable(perfS1HasWaitStore, s1Valid)
+      val s2AgeMask = RegEnable(s1AgeMaskLow | s1AgeMaskHigh, s1Valid)
+      val perfS2WaitStoreRetired = perfS2HasWaitStore && !s2MdpQueryRespValid
+      val perfS2MdpHitVec = VecInit((0 until StoreQueuePhysicalSize).map(j =>
+        s2MdpQueryRespValid && !s2MdpHitOutOfRange && s2AddrInvalidSqIdx.value === j.U)).asUInt
+      val perfS2MdpCandidate = Mux(s2LoadWaitStrict, s2AgeMask, s2AgeMask & perfS2MdpHitVec)
+      val perfS2MdpSelectedAddrMatch = (perfS2MdpCandidate & s2OverlapMask & s2PaddrMatchVec &
+        addrValidVec.asUInt & s2SelectOH).orR
+      val perfS2MdpForwardMaskMatch = s2ForwardValid && s2FinalMask.orR
+
+      s2Resp.bits.perfMdpAddrValid  := s2Valid && perfS2MdpCandidate.orR
+      s2Resp.bits.perfMdpAddrStrict := s2LoadWaitStrict
+      s2Resp.bits.perfMdpAddrHit    := perfS2MdpSelectedAddrMatch && perfS2MdpForwardMaskMatch
+      s2Resp.bits.perfWaitStoreRetired := s2Valid && perfS2WaitStoreRetired
+
       if(debugEn) {
         dontTouch(io.query)
         dontTouch(s1OverlapMask)
diff --git a/src/main/scala/xiangshan/mem/mdp/StoreSet.scala b/src/main/scala/xiangshan/mem/mdp/StoreSet.scala
index 83bcd18721f..4b2e5a933fb 100644
--- a/src/main/scala/xiangshan/mem/mdp/StoreSet.scala
+++ b/src/main/scala/xiangshan/mem/mdp/StoreSet.scala
@@ -46,7 +46,6 @@ class SSITEntry(implicit p: Parameters) extends XSBundle {
 // Store Set Identifier Table Entry
 class SSITDataEntry(implicit p: Parameters) extends XSBundle {
   val ssid = UInt(SSIDWidth.W) // store set identifier
-  val strict = Bool() // strict load wait is needed
 }
 
 // Store Set Identifier Table
@@ -101,7 +100,7 @@ class SSIT(implicit p: Parameters) extends XSModule {
   ))
 
   // TODO: use SRAM or not?
-  (0 until SSIT_WRITE_PORT_NUM).map(i => {
+  (0 until SSIT_WRITE_PORT_NUM).foreach(i => {
     valid_array.io.wen(i) := false.B
     valid_array.io.waddr(i) := 0.U
     valid_array.io.wdata(i) := false.B
@@ -112,16 +111,20 @@ class SSIT(implicit p: Parameters) extends XSModule {
 
   val debug_valid = RegInit(VecInit(Seq.fill(SSITSize)(false.B)))
   val debug_ssid = Reg(Vec(SSITSize, UInt(SSIDWidth.W)))
-  val debug_strict = Reg(Vec(SSITSize, Bool()))
+  val strictArray = RegInit(VecInit(Seq.fill(SSITSize)(false.B)))
   if(!env.FPGAPlatform){
     dontTouch(debug_valid)
     dontTouch(debug_ssid)
-    dontTouch(debug_strict)
+    dontTouch(strictArray)
   }
 
   val resetCounter = RegInit(0.U(ResetTimeMax2Pow.W))
   resetCounter := resetCounter + 1.U
 
+  val strictReadAddr = Wire(Vec(SSIT_READ_PORT_NUM, UInt(MemPredPCWidth.W)))
+  val strictReadEnable = Wire(Vec(SSIT_READ_PORT_NUM, Bool()))
+  val strictReadData = Wire(Vec(SSIT_READ_PORT_NUM, Bool()))
+
   for (i <- 0 until DecodeWidth) {
     // io.rdata(i).valid := RegNext(valid(io.raddr(i)))
     // io.rdata(i).ssid := RegNext(ssid(io.raddr(i)))
@@ -132,11 +135,14 @@ class SSIT(implicit p: Parameters) extends XSModule {
     data_array.io.ren.get(i) := io.ren(i)
     valid_array.io.raddr(i) := io.raddr(i)
     data_array.io.raddr(i) := io.raddr(i)
+    strictReadAddr(i) := io.raddr(i)
+    strictReadEnable(i) := io.ren(i)
+    strictReadData(i) := RegEnable(strictArray(strictReadAddr(i)), strictReadEnable(i))
 
     // gen result in rename stage
     io.rdata(i).valid := valid_array.io.rdata(i)
     io.rdata(i).ssid := data_array.io.rdata(i).ssid
-    io.rdata(i).strict := data_array.io.rdata(i).strict
+    io.rdata(i).strict := strictReadData(i)
   }
 
   // flush SSIT
@@ -162,7 +168,7 @@ class SSIT(implicit p: Parameters) extends XSModule {
       valid_array.io.wen(SSIT_MISC_WRITE_PORT) := true.B
       valid_array.io.waddr(SSIT_MISC_WRITE_PORT) := resetStepCounter
       valid_array.io.wdata(SSIT_MISC_WRITE_PORT) := false.B
-      debug_valid(resetStepCounter) := false.B
+      debug_valid(resetStepCounter(log2Ceil(SSITSize) - 1, 0)) := false.B
     }
   }
   XSPerfAccumulate("reset_timeout", state === s_flush && resetCounter === 0.U)
@@ -184,6 +190,10 @@ class SSIT(implicit p: Parameters) extends XSModule {
     valid_array.io.ren.get(SSIT_UPDATE_STORE_READ_PORT) := true.B
     data_array.io.ren.get(SSIT_UPDATE_LOAD_READ_PORT)   := true.B
     data_array.io.ren.get(SSIT_UPDATE_STORE_READ_PORT)  := true.B
+    strictReadAddr(SSIT_UPDATE_LOAD_READ_PORT) := io.update.ldpc
+    strictReadAddr(SSIT_UPDATE_STORE_READ_PORT) := io.update.stpc
+    strictReadEnable(SSIT_UPDATE_LOAD_READ_PORT) := true.B
+    strictReadEnable(SSIT_UPDATE_STORE_READ_PORT) := true.B
   }
 
   // update stage 1: get ssit read result
@@ -192,11 +202,10 @@ class SSIT(implicit p: Parameters) extends XSModule {
   // load has already been assigned with a store set
   val s1_loadAssigned = valid_array.io.rdata(SSIT_UPDATE_LOAD_READ_PORT)
   val s1_loadOldSSID = data_array.io.rdata(SSIT_UPDATE_LOAD_READ_PORT).ssid
-  val s1_loadStrict = data_array.io.rdata(SSIT_UPDATE_LOAD_READ_PORT).strict
+  val s1_loadStrict = strictReadData(SSIT_UPDATE_LOAD_READ_PORT)
   // store has already been assigned with a store set
   val s1_storeAssigned = valid_array.io.rdata(SSIT_UPDATE_STORE_READ_PORT)
   val s1_storeOldSSID = data_array.io.rdata(SSIT_UPDATE_STORE_READ_PORT).ssid
-  val s1_storeStrict = data_array.io.rdata(SSIT_UPDATE_STORE_READ_PORT).strict
   // val s1_ssidIsSame = s1_loadOldSSID === s1_storeOldSSID
 
   // update stage 2, update ssit data_array
@@ -217,6 +226,24 @@ class SSIT(implicit p: Parameters) extends XSModule {
   // but load's store set ID is smaller
   val s2_winnerSSID = Mux(s2_loadOldSSID < s2_storeOldSSID, s2_loadOldSSID, s2_storeOldSSID)
 
+  val strictTrain = s2_mempred_update_req_valid && s2_loadAssigned && s2_storeAssigned && s2_ssidIsSame
+  val strictResetCounter = RegInit(0.U(log2Ceil(strictResetPeriod + 1).W))
+  val strictClearTrigger = strictResetCounter === strictResetPeriod.U
+
+  when(strictClearTrigger) {
+    strictResetCounter := Mux(strictTrain, 1.U, 0.U)
+  }.elsewhen(strictResetCounter =/= 0.U) {
+    strictResetCounter := strictResetCounter + 1.U
+  }.elsewhen(strictTrain) {
+    strictResetCounter := 1.U
+  }
+
+  when(strictClearTrigger) {
+    strictArray.foreach(_ := false.B)
+  }
+
+  val strictWriteData = WireInit(VecInit(Seq.fill(SSIT_WRITE_PORT_NUM)(false.B)))
+
   def update_ld_ssit_entry(pc: UInt, valid: Bool, ssid: UInt, strict: Bool) = {
     valid_array.io.wen(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
     valid_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) := pc
@@ -224,10 +251,9 @@ class SSIT(implicit p: Parameters) extends XSModule {
     data_array.io.wen(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
     data_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) := pc
     data_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT).ssid := ssid
-    data_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT).strict := strict
+    strictWriteData(SSIT_UPDATE_LOAD_WRITE_PORT) := strict
     debug_valid(pc) := valid
     debug_ssid(pc) := ssid
-    debug_strict(pc) := strict
   }
 
   def update_st_ssit_entry(pc: UInt, valid: Bool, ssid: UInt, strict: Bool) = {
@@ -237,10 +263,9 @@ class SSIT(implicit p: Parameters) extends XSModule {
     data_array.io.wen(SSIT_UPDATE_STORE_WRITE_PORT) := true.B
     data_array.io.waddr(SSIT_UPDATE_STORE_WRITE_PORT) := pc
     data_array.io.wdata(SSIT_UPDATE_STORE_WRITE_PORT).ssid := ssid
-    data_array.io.wdata(SSIT_UPDATE_STORE_WRITE_PORT).strict := strict
+    strictWriteData(SSIT_UPDATE_STORE_WRITE_PORT) := strict
     debug_valid(pc) := valid
     debug_ssid(pc) := ssid
-    debug_strict(pc) := strict
   }
 
   when(s2_mempred_update_req_valid){
@@ -267,7 +292,7 @@ class SSIT(implicit p: Parameters) extends XSModule {
         update_st_ssit_entry(
           pc = s2_mempred_update_req.stpc,
           valid = true.B,
-          ssid = s2_ldSsidAllocate,
+          ssid = s2_loadOldSSID,
           strict = false.B
         )
       }
@@ -277,7 +302,7 @@ class SSIT(implicit p: Parameters) extends XSModule {
         update_ld_ssit_entry(
           pc = s2_mempred_update_req.ldpc,
           valid = true.B,
-          ssid = s2_stSsidAllocate,
+          ssid = s2_storeOldSSID,
           strict = false.B
         )
       }
@@ -298,8 +323,7 @@ class SSIT(implicit p: Parameters) extends XSModule {
           strict = false.B
         )
         when(s2_ssidIsSame){
-          data_array.io.wdata(SSIT_UPDATE_LOAD_READ_PORT).strict := true.B
-          debug_strict(s2_mempred_update_req.ldpc) := true.B
+          strictWriteData(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
         }
       }
     }
@@ -314,6 +338,62 @@ class SSIT(implicit p: Parameters) extends XSModule {
     data_array.io.wen(SSIT_UPDATE_STORE_WRITE_PORT) := false.B
   }
 
+  for (i <- 0 until SSIT_WRITE_PORT_NUM) {
+    val writeEnable = RegNext(data_array.io.wen(i), false.B)
+    val writeAddr = RegEnable(data_array.io.waddr(i), data_array.io.wen(i))
+    val writeData = RegEnable(strictWriteData(i), data_array.io.wen(i))
+    when(writeEnable && !strictClearTrigger) {
+      strictArray(writeAddr) := writeData
+    }
+  }
+
+  // StoreSet ChiselDB trace
+  val storeSetUpdateHartId = p(XSCoreParamsKey).HartId
+  val storeSetUpdateTable = ChiselDB.createTable(s"StoreSetUpdateDB$storeSetUpdateHartId", new StoreSetUpdateDBEntry, basicDB = false)
+
+  val storeSetUpdateTypeLxsx = 0.U(3.W)
+  val storeSetUpdateTypeLysx = 1.U(3.W)
+  val storeSetUpdateTypeLxsy = 2.U(3.W)
+  val storeSetUpdateTypeLysyMerge = 3.U(3.W)
+  val storeSetUpdateTypeSameSsidStrict = 4.U(3.W)
+
+  val storeSetUpdateType = Wire(UInt(3.W))
+  storeSetUpdateType := MuxCase(storeSetUpdateTypeLxsx, Seq(
+    (s2_loadAssigned && !s2_storeAssigned) -> storeSetUpdateTypeLysx,
+    (!s2_loadAssigned && s2_storeAssigned) -> storeSetUpdateTypeLxsy,
+    (s2_loadAssigned && s2_storeAssigned && !s2_ssidIsSame) -> storeSetUpdateTypeLysyMerge,
+    (s2_loadAssigned && s2_storeAssigned && s2_ssidIsSame) -> storeSetUpdateTypeSameSsidStrict
+  ))
+
+  val storeSetNewLoadSSID = MuxCase(s2_allocSsid, Seq(
+    (s2_loadAssigned && !s2_storeAssigned) -> s2_loadOldSSID,
+    (!s2_loadAssigned && s2_storeAssigned) -> s2_storeOldSSID,
+    (s2_loadAssigned && s2_storeAssigned) -> s2_winnerSSID
+  ))
+  val storeSetNewLoadStrict = MuxCase(false.B, Seq(
+    (s2_loadAssigned && !s2_storeAssigned) -> s2_loadStrict,
+    (s2_loadAssigned && s2_storeAssigned && s2_ssidIsSame) -> true.B
+  ))
+
+  val storeSetUpdateEntry = Wire(new StoreSetUpdateDBEntry)
+  storeSetUpdateEntry.timeCnt := GTimer()
+  storeSetUpdateEntry.ldFoldPc := s2_mempred_update_req.ldpc
+  storeSetUpdateEntry.stFoldPc := s2_mempred_update_req.stpc
+  storeSetUpdateEntry.loadOldSSID := s2_loadOldSSID
+  storeSetUpdateEntry.storeOldSSID := s2_storeOldSSID
+  storeSetUpdateEntry.loadOldStrict := s2_loadStrict
+  storeSetUpdateEntry.winnerSSID := s2_winnerSSID
+  storeSetUpdateEntry.newLoadSSID := storeSetNewLoadSSID
+  storeSetUpdateEntry.newLoadStrict := storeSetNewLoadStrict
+  storeSetUpdateEntry.updateType := storeSetUpdateType
+  storeSetUpdateTable.log(
+    data = storeSetUpdateEntry,
+    en = s2_mempred_update_req_valid,
+    site = s"SSIT$storeSetUpdateHartId",
+    clock = clock,
+    reset = reset
+  )
+
   XSPerfAccumulate("ssit_update_lxsx", s2_mempred_update_req_valid && !s2_loadAssigned && !s2_storeAssigned)
   XSPerfAccumulate("ssit_update_lysx", s2_mempred_update_req_valid && s2_loadAssigned && !s2_storeAssigned)
   XSPerfAccumulate("ssit_update_lxsy", s2_mempred_update_req_valid && !s2_loadAssigned && s2_storeAssigned)
@@ -322,6 +402,7 @@ class SSIT(implicit p: Parameters) extends XSModule {
   XSPerfAccumulate("ssit_update_strict_failed",
     s2_mempred_update_req_valid && s2_ssidIsSame && s2_loadStrict && s2_loadAssigned && s2_storeAssigned
   ) // should be zero
+  XSPerfAccumulate("ssit_strict_clear", strictClearTrigger)
 
   val pred_dependence = io.ren.zip(io.rdata).map{case (v, rdata) =>
     RegNext(v) && rdata.valid
@@ -349,11 +430,14 @@ class LFSTReq(implicit p: Parameters) extends XSBundle {
   val isstore = Bool()
   val ssid = UInt(SSIDWidth.W) // use ssid to lookup LFST
   val robIdx = new RobPtr
+  val perfStrictPred = Bool()
 }
 
 class LFSTResp(implicit p: Parameters) extends XSBundle {
   val shouldWait = Bool()
+  val strictShouldWait = Bool()
   val robIdx = new RobPtr
+  val perfNotIssuedStoreGt1 = Bool()
 }
 
 class DispatchLFSTIO(implicit p: Parameters) extends XSBundle {
@@ -409,6 +493,13 @@ class LFST(implicit p: Parameters) extends XSModule {
         }
       )
     }
+
+    // Older stores in the same dispatch bundle become LFST entries in this cycle.
+    val notIssuedStoreCount = PopCount(validVec(io.dispatch.req(i).bits.ssid)) + PopCount(hitInDispatchBundleVec)
+    val notIssuedStoreGt1 = notIssuedStoreCount > 1.U
+    io.dispatch.resp(i).bits.perfNotIssuedStoreGt1 := notIssuedStoreGt1
+    io.dispatch.resp(i).bits.strictShouldWait := io.dispatch.req(i).valid &&
+      !io.dispatch.req(i).bits.isstore && notIssuedStoreGt1
   }
 
   // when store is issued, mark it as invalid
@@ -459,4 +550,11 @@ class LFST(implicit p: Parameters) extends XSModule {
   }
 
   XSPerfAccumulate("LFST_Overflow_Count", PopCount(overflowVec))
+  XSPerfAccumulate("lfst_strict_pred_not_issued_store_greater1", PopCount(io.dispatch.resp.zip(io.dispatch.req).map {
+    case (resp, req) => resp.valid && req.bits.perfStrictPred && resp.bits.perfNotIssuedStoreGt1
+  }))
+  XSPerfAccumulate("lfst_strict_pred_filtered", PopCount(io.dispatch.resp.zip(io.dispatch.req).map {
+    case (resp, req) => resp.valid && !req.bits.isstore && req.bits.perfStrictPred &&
+      resp.bits.shouldWait && !resp.bits.strictShouldWait
+  }))
 }
diff --git a/src/main/scala/xiangshan/mem/pipeline/Bundles.scala b/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
index ef298edce9e..ff9d0b941c8 100644
--- a/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/Bundles.scala
@@ -96,6 +96,11 @@ class LoadPipeBundle(
   val addrInvalidSqIdx = Option.when(param.replayToLRQ)(new SqPtr)
   val tlbId = Option.when(param.replayToLRQ)(UInt(log2Up(loadfiltersize).W))
   val tlbFull = Option.when(param.replayToLRQ)(Bool())
+  val perfMdpAddrValid = Option.when(param.replayToLRQ)(Bool())
+  val perfMdpAddrStrict = Option.when(param.replayToLRQ)(Bool())
+  val perfMdpAddrHit = Option.when(param.replayToLRQ)(Bool())
+  val perfWaitStoreRetired = Option.when(param.replayToLRQ)(Bool())
+  val perfIsCmaReplay = Option.when(param.hasS2PreProcess)(Bool())
 
   val forwardDChannel = Option.when(param.replayFromLRQ)(Bool())
   val uncacheReplay = Option.when(param.replayFromLRQ)(Bool())
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index ab31889ca00..28a8ed3cdcc 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -30,6 +30,7 @@ import xiangshan.backend.fu.fpu.FPU
 import xiangshan.backend.ctrlblock.{DebugLsInfoBundle, LsTopdownInfo}
 import xiangshan.backend.fu.NewCSR._
 import xiangshan.backend.exu.ExeUnitParams
+import xiangshan.backend.rob.RobPtr
 import xiangshan.mem.Bundles._
 import xiangshan.mem.LoadReplayCauses._
 import xiangshan.mem.LoadStage._
@@ -601,11 +602,11 @@ class LoadUnitS1(param: ExeUnitParams)(
   val redirectNextNext = Wire(redirect.cloneType)
   redirectNextNext.valid := GatedValidRegNext(redirectNext.valid)
   redirectNextNext.bits := RegEnable(redirectNext.bits, redirectNext.valid)
-  
+
   val isUnalignTail = LoadEntrance.isUnalignTail(entrance)
 
   val kill = !pipeIn.valid || io.kill || isSwInstrPrefetch ||
-             robIdx.needFlush(redirect) || robIdx.needFlush(redirectNext) || 
+             robIdx.needFlush(redirect) || robIdx.needFlush(redirectNext) ||
              (robIdx.needFlush(redirectNextNext) && isUnalignTail)
 
   /**
@@ -733,6 +734,7 @@ class LoadUnitS1(param: ExeUnitParams)(
   stageInfo.cause.get := 0.U.asTypeOf(stageInfo.cause.get)
   stageInfo.cause.get(LoadReplayCauses.C_NK) := nuke
   stageInfo.fastReplayNukeFirst.get := fastReplayNukeFirst
+  stageInfo.perfIsCmaReplay.get := LoadEntrance.isReplay(entrance) && in.cause.get(LoadReplayCauses.C_MA)
   // update trigger info
   stageInfo.vecVaddrOffset.get := vecVaddrOffset
   stageInfo.vecTriggerMask.get := vecTriggerMask
@@ -884,6 +886,7 @@ class LoadUnitS2(param: ExeUnitParams)(
   val isMMIOReplay = in.isMMIOReplay()
   val isNCReplay = in.isNCReplay()
   val isUncacheReplay = in.isUncacheReplay()
+  val perfIsCmaReplay = in.perfIsCmaReplay.get
   val isPrefetch = accessType.isPrefetch()
   val isHwPrefetch = accessType.isHwPrefetch()
   val isSwPrefetch = accessType.isSwPrefetch()
@@ -1138,6 +1141,11 @@ class LoadUnitS2(param: ExeUnitParams)(
   stageInfo.addrInvalidSqIdx.get := sqAddrInvalidSqIdx
   stageInfo.tlbId.get := io.tlbHint.id
   stageInfo.tlbFull.get := io.tlbHint.full
+  stageInfo.perfMdpAddrValid.get := io.sqForwardResp.valid && io.sqForwardResp.bits.perfMdpAddrValid
+  stageInfo.perfMdpAddrStrict.get := io.sqForwardResp.bits.perfMdpAddrStrict
+  stageInfo.perfMdpAddrHit.get := io.sqForwardResp.bits.perfMdpAddrHit
+  stageInfo.perfWaitStoreRetired.get := io.sqForwardResp.valid && io.sqForwardResp.bits.perfWaitStoreRetired
+  stageInfo.perfIsCmaReplay.get := perfIsCmaReplay
   // Pre-process for s3
   stageInfo.troubleMaker.get := troubleMaker
   stageInfo.shouldFastReplay.get := in.shouldFastReplay.get || fastReplay && !exception
@@ -1269,6 +1277,11 @@ class LoadUnitS3(param: ExeUnitParams)(
     // Load cancel
     val cancel = Output(Bool())
 
+    val perfRobHeadPtr = Input(new RobPtr)
+    val perfLqHeadPtr = Input(new LqPtr)
+    val perfLqFull = Input(Bool())
+    val perfMdpAddr = Output(new PerfMdpAddr)
+
     // CSR control signals
     val csrCtrl = Flipped(new CustomCSRCtrlIO)
 
@@ -1505,6 +1518,68 @@ class LoadUnitS3(param: ExeUnitParams)(
   lqWrite.rep_info.tlb_id := in.tlbId.get
   lqWrite.rep_info.tlb_full := in.tlbFull.get
 
+  val perfIsReplayExec = LoadEntrance.isReplay(entrance) || s4HeadIsReplay && s4HeadValid
+  val perfMdpAddrValid = Mux(s4HeadValid, s4Head.perfMdpAddrValid.get, in.perfMdpAddrValid.get)
+  val perfMdpAddrStrict = Mux(s4HeadValid, s4Head.perfMdpAddrStrict.get, in.perfMdpAddrStrict.get)
+  val perfMdpAddrHit = Mux(s4HeadValid, s4Head.perfMdpAddrHit.get, in.perfMdpAddrHit.get)
+  val perfWaitStoreRetired = Mux(s4HeadValid, s4Head.perfWaitStoreRetired.get, in.perfWaitStoreRetired.get)
+  val perfIsCmaReplay = Mux(s4HeadValid, s4Head.perfIsCmaReplay.get, in.perfIsCmaReplay.get)
+  val perfMdpUop = Mux(s4HeadValid, s4Head.uop, uop)
+  val perfMdpAddrCanCount = lqWriteValid && !lqWriteNeedReplay
+  val perfMdpAddrNonStrict = !perfMdpAddrStrict
+  val perfMdpAddrMiss = !perfMdpAddrHit
+  val perfLoadUnitMdpAddrCanCount = perfMdpAddrCanCount && !perfIsReplayExec && perfMdpAddrValid
+  val perfReplayMdpAddrCanCount = perfMdpAddrCanCount && perfIsReplayExec && perfIsCmaReplay
+  val perfWaitStoreRetiredCanCount = perfMdpAddrCanCount && perfWaitStoreRetired &&
+    (!perfIsReplayExec || perfIsCmaReplay)
+  val perfMdpAddr = Wire(new PerfMdpAddr)
+  perfMdpAddr.loadUnitNonStrictHit := perfLoadUnitMdpAddrCanCount && perfMdpAddrNonStrict && perfMdpAddrHit
+  perfMdpAddr.loadUnitNonStrictMiss := perfLoadUnitMdpAddrCanCount && perfMdpAddrNonStrict && perfMdpAddrMiss
+  perfMdpAddr.loadUnitStrictHit := perfLoadUnitMdpAddrCanCount && perfMdpAddrStrict && perfMdpAddrHit
+  perfMdpAddr.loadUnitStrictMiss := perfLoadUnitMdpAddrCanCount && perfMdpAddrStrict && perfMdpAddrMiss
+  perfMdpAddr.replayNonStrictHit := perfReplayMdpAddrCanCount && perfMdpAddrNonStrict && perfMdpAddrHit
+  perfMdpAddr.replayNonStrictMiss := perfReplayMdpAddrCanCount && perfMdpAddrNonStrict && perfMdpAddrMiss
+  perfMdpAddr.replayStrictHit := perfReplayMdpAddrCanCount && perfMdpAddrStrict && perfMdpAddrHit
+  perfMdpAddr.replayStrictMiss := perfReplayMdpAddrCanCount && perfMdpAddrStrict && perfMdpAddrMiss
+  perfMdpAddr.waitStoreRetired := perfWaitStoreRetiredCanCount
+  perfMdpAddr.perfAtRobHead := perfMdpUop.robIdx === io.perfRobHeadPtr
+  perfMdpAddr.perfAtLqHead := perfMdpUop.lqIdx === io.perfLqHeadPtr
+  perfMdpAddr.perfLqFull := io.perfLqFull
+
+  // StoreSet ChiselDB trace
+  val storeSetLoadUnitCheckHartId = p(XSCoreParamsKey).HartId
+  val storeSetLoadUnitCheckTable = ChiselDB.createTable(
+    s"StoreSetLoadUnitCheckDB$storeSetLoadUnitCheckHartId",
+    new StoreSetLoadUnitCheckDBEntry,
+    basicDB = false
+  )
+  val storeSetLoadUnitCheckEntry = Wire(new StoreSetLoadUnitCheckDBEntry)
+  val storeSetLoadUnitCheckUop = Mux(s4HeadValid, s4Head.uop, uop)
+  val storeSetLoadUnitCheckAddrInvalidSqIdx = Mux(s4HeadValid, s4Head.addrInvalidSqIdx.get, in.addrInvalidSqIdx.get)
+  val storeSetLoadUnitCheckStoreSqIdxValid = lqWriteCause(LoadReplayCauses.C_MA)
+  val storeSetLoadUnitCheckFoldPc =
+    XORFold(storeSetLoadUnitCheckUop.pc(VAddrBits - 1, 1), MemPredPCWidth)
+  storeSetLoadUnitCheckEntry.timeCnt := GTimer()
+  storeSetLoadUnitCheckEntry.robIdx := storeSetLoadUnitCheckUop.robIdx.value
+  storeSetLoadUnitCheckEntry.foldPc := storeSetLoadUnitCheckFoldPc
+  storeSetLoadUnitCheckEntry.ssid := storeSetLoadUnitCheckUop.ssid
+  storeSetLoadUnitCheckEntry.loadSqIdx := storeSetLoadUnitCheckUop.sqIdx.value
+  storeSetLoadUnitCheckEntry.storeSqIdx := storeSetLoadUnitCheckAddrInvalidSqIdx.value
+  storeSetLoadUnitCheckEntry.loadWaitBit := storeSetLoadUnitCheckUop.loadWaitBit
+  storeSetLoadUnitCheckEntry.loadWaitStrict := storeSetLoadUnitCheckUop.loadWaitStrict
+  storeSetLoadUnitCheckEntry.mdpAddrValid := perfMdpAddrValid
+  storeSetLoadUnitCheckEntry.mdpAddrStrict := perfMdpAddrStrict
+  storeSetLoadUnitCheckEntry.mdpAddrHit := perfMdpAddrHit
+  storeSetLoadUnitCheckEntry.storeSqIdxValid := storeSetLoadUnitCheckStoreSqIdxValid
+  storeSetLoadUnitCheckTable.log(
+    data = storeSetLoadUnitCheckEntry,
+    en = lqWriteValid &&
+      storeSetLoadUnitCheckUop.storeSetHit && storeSetLoadUnitCheckUop.loadWaitBit,
+    site = s"${param.name}_StoreSetLoadUnitCheck$storeSetLoadUnitCheckHartId",
+    clock = clock,
+    reset = reset
+  )
+
   // Writeback to VLMergeBuffer
   val vecldoutValid = pipeIn.valid && !kill && shouldWriteback && isVector && endPipe
   val vecldout = Wire(new VecPipelineFeedbackIO(isVStore = false))
@@ -1603,6 +1678,7 @@ class LoadUnitS3(param: ExeUnitParams)(
   io.exceptionInfo.bits := exceptionInfo
 
   io.cancel := cancel
+  io.perfMdpAddr := perfMdpAddr
 
   io.debugInfo.isReplayFast := pipeIn.valid && !kill && doFastReplay
   io.debugInfo.isReplaySlow := lqWriteValid && cause.asUInt.orR
@@ -1824,6 +1900,10 @@ class LoadUnitIO(val param: ExeUnitParams)(implicit p: Parameters) extends XSBun
   // IQ wakeup and load cancel
   val wakeup = ValidIO(new MemWakeUpBundle)
   val cancel = Output(Bool())
+  val perfRobHeadPtr = Input(new RobPtr)
+  val perfLqHeadPtr = Input(new LqPtr)
+  val perfLqFull = Input(Bool())
+  val perfMdpAddr = Output(new PerfMdpAddr)
   // Exception info
   val exceptionInfo = ValidIO(new MemExceptionInfo)
   // Data forwarding and bypass
@@ -1957,6 +2037,10 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.rawNukeQuery.revokeLastLastCycle := s3.io.revokeLastLastCycle
   io.rollback := s3.io.rollback
   io.cancel := s3.io.cancel
+  s3.io.perfRobHeadPtr := io.perfRobHeadPtr
+  s3.io.perfLqHeadPtr := io.perfLqHeadPtr
+  s3.io.perfLqFull := io.perfLqFull
+  io.perfMdpAddr := s3.io.perfMdpAddr
   io.exceptionInfo := s3.io.exceptionInfo
   s3.io.csrCtrl := io.csrCtrl
```
