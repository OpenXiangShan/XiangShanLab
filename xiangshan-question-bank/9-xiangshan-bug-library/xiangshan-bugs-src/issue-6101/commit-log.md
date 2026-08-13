# Commit Log
- Issue: #6101
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6101
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6101
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6101
- Changed files: 3
- Additions: 69
- Deletions: 21

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ftq/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
index cd6d6c277cd..6fcb6200bd3 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
@@ -233,6 +233,14 @@ class BranchInfo(implicit p: Parameters) extends BpuBundle with HalfAlignHelper
     this.cfiPosition := getAlignedPosition(resolve.pc, resolve.ftqOffset)._1
     this.attribute   := resolve.attribute
     this.mispredict  := resolve.mispredict
+
+    if (!env.FPGAPlatform) {
+      this.debug_realCfiPc.get := getRealCfiPcFromOffset(
+        resolve.pc,
+        resolve.ftqOffset,
+        resolve.debug_isRVC.get
+      )
+    }
   }
 }
 
diff --git a/src/main/scala/xiangshan/frontend/ftq/Bundles.scala b/src/main/scala/xiangshan/frontend/ftq/Bundles.scala
index d122f9a3532..5977d3f873a 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Bundles.scala
@@ -25,6 +25,7 @@ import xiangshan.frontend.TwoFetchInfo
 import xiangshan.frontend.TwoPrefetchCase
 import xiangshan.frontend.bpu.BpuMeta
 import xiangshan.frontend.bpu.BpuPerfMeta
+import xiangshan.frontend.bpu.BranchAttribute
 import xiangshan.frontend.bpu.BranchInfo
 import xiangshan.frontend.icache.ICacheCacheLineHelper
 import xiangshan.frontend.icache.ICacheDataHelper
@@ -88,7 +89,8 @@ class PerfMeta(implicit p: Parameters) extends FtqBundle {
   val bpuPerf: BpuPerfMeta = new BpuPerfMeta
 
   // Whether a position is a Control-Flow Instruction
-  val isCfi: Vec[Bool] = Vec(FetchBlockInstNum, Bool())
+  val isCfi:   Vec[Bool]            = Vec(FetchBlockInstNum, Bool())
+  val cfiAttr: Vec[BranchAttribute] = Vec(FetchBlockInstNum, new BranchAttribute)
 
   // This block mispredicted
   // no matter how many mispredictions happened before, count correct-path only
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 0a3eaa909d5..a191e0b8bec 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -52,6 +52,8 @@ import xiangshan.frontend.bpu.BpuPredictionSource
 import xiangshan.frontend.bpu.BpuRedirectMeta
 import xiangshan.frontend.bpu.BpuResolveMeta
 import xiangshan.frontend.bpu.BpuTrain
+import xiangshan.frontend.bpu.BranchAttribute
+import xiangshan.frontend.bpu.BranchInfo
 import xiangshan.frontend.bpu.HalfAlignHelper
 import xiangshan.frontend.icache.ICacheCacheLineHelper
 import xiangshan.frontend.icache.ICacheToFtqIO
@@ -207,15 +209,16 @@ class Ftq(implicit p: Parameters) extends FtqModule
     )
   }
 
+  private val s3PerfQueue = WireInit(perfQueue)
   when(io.fromBpu.meta.valid) {
     val s3BpuPtr = io.fromBpu.s3FtqPtr.value
     metaQueueRedirect(s3BpuPtr) := io.fromBpu.meta.bits.redirectMeta
     metaQueueResolve(s3BpuPtr)  := io.fromBpu.meta.bits.resolveMeta
     metaQueueCommit(s3BpuPtr)   := io.fromBpu.meta.bits.commitMeta
 
-    perfQueue(s3BpuPtr).bpuPerf := io.fromBpu.perfMeta
-    perfQueue(s3BpuPtr).isCfi.foreach(_ := false.B)
-    perfQueue(s3BpuPtr).mispredict := false.B
+    s3PerfQueue(s3BpuPtr).bpuPerf := io.fromBpu.perfMeta
+    s3PerfQueue(s3BpuPtr).isCfi.foreach(_ := false.B)
+    s3PerfQueue(s3BpuPtr).mispredict := false.B
   }
 
   resolveQueue.io.bpuEnqueue    := bpuEnqueue
@@ -388,19 +391,47 @@ class Ftq(implicit p: Parameters) extends FtqModule
   io.toBpu.train.valid := trainCache.valid && !flushTrain
   io.toBpu.train.bits  := trainCache.bits
 
-  io.fromBackend.resolve.foreach { branch =>
-    val ftqIdx      = branch.bits.ftqIdx.value
-    val cfiPosition = getAlignedPosition(branch.bits.pc, branch.bits.ftqOffset)._1
+  // default next state receives s3 prediction meta
+  perfQueue := s3PerfQueue
+
+  // resolve override next state
+  private val lastPerfMetas = Wire(Vec(backendParams.BrhCnt, new PerfMeta))
+  io.fromBackend.resolve.zipWithIndex.foreach { case (branch, i) =>
+    val ftqIdx = branch.bits.ftqIdx.value
+    val lastPerfMeta = WireInit(MuxCase(
+      s3PerfQueue(ftqIdx),
+      (0 until i).reverse.map { j =>
+        val prevBranch = io.fromBackend.resolve(j)
+        (prevBranch.valid && prevBranch.bits.ftqIdx.value === ftqIdx) -> lastPerfMetas(j)
+      }
+    ))
+    val curPerfMeta = WireInit(lastPerfMeta)
+
     when(branch.valid) {
-      perfQueue(ftqIdx).isCfi(cfiPosition) := true.B
-      when(branch.bits.mispredict) {
-        // Mark mispredict and flush the cfi after its position
-        perfQueue(ftqIdx).mispredict := true.B
-        perfQueue(ftqIdx).mispredictBranchInfo.fromResolve(branch.bits)
-        val mask = UIntToMask(cfiPosition + 1.U, FetchBlockInstNum)
-        perfQueue(ftqIdx).isCfi := (perfQueue(ftqIdx).isCfi.asUInt & mask).asBools // BUGGY: not really correct flush
+      val newBranchInfo = Wire(new BranchInfo)
+      newBranchInfo.fromResolve(branch.bits)
+
+      val curOH     = UIntToOH(newBranchInfo.cfiPosition, FetchBlockInstNum)
+      val validMask = UIntToMask(newBranchInfo.cfiPosition +& 1.U, FetchBlockInstNum)
+      val beforeKnownMispredict =
+        Mux(
+          lastPerfMeta.mispredict,
+          newBranchInfo.cfiPosition < lastPerfMeta.mispredictBranchInfo.cfiPosition,
+          true.B
+        )
+
+      when(beforeKnownMispredict) {
+        curPerfMeta.isCfi(newBranchInfo.cfiPosition)   := true.B
+        curPerfMeta.cfiAttr(newBranchInfo.cfiPosition) := newBranchInfo.attribute
+        when(branch.bits.mispredict) {
+          curPerfMeta.mispredict           := true.B
+          curPerfMeta.mispredictBranchInfo := newBranchInfo
+          curPerfMeta.isCfi                := ((lastPerfMeta.isCfi.asUInt | curOH) & validMask).asBools
+        }
       }
+      perfQueue(ftqIdx) := curPerfMeta
     }
+    lastPerfMetas(i) := curPerfMeta
   }
 
   // --------------------------------------------------------------------------------
@@ -519,20 +550,27 @@ class Ftq(implicit p: Parameters) extends FtqModule
       ("mispredicts", true.B, commitPerfMeta.mispredict)
     )
   )
+
+  private def PerfNumCfiWithAttr(
+      perfMeta: PerfMeta,
+      withAttr: BranchAttribute => Bool
+  ): UInt =
+    PopCount(perfMeta.isCfi zip perfMeta.cfiAttr map { case (v, attr) => v && withAttr(attr) })
+
   XSPerfSeqAccumulate(
     "commit_branch_type",
     commit,
     Seq(
-      ("conditional", commitPerfMeta.mispredictBranchInfo.attribute.isConditional),
-      ("direct", commitPerfMeta.mispredictBranchInfo.attribute.isDirect),
-      ("indirect", commitPerfMeta.mispredictBranchInfo.attribute.isIndirect),
+      ("conditional", true.B, PerfNumCfiWithAttr(commitPerfMeta, attr => attr.isConditional)),
+      ("direct", true.B, PerfNumCfiWithAttr(commitPerfMeta, attr => attr.isDirect)),
+      ("indirect", true.B, PerfNumCfiWithAttr(commitPerfMeta, attr => attr.isIndirect)),
       (
         "indirect_retcall",
-        commitPerfMeta.mispredictBranchInfo.attribute.isReturnAndCall
-          && commitPerfMeta.mispredictBranchInfo.attribute.isIndirect
+        true.B,
+        PerfNumCfiWithAttr(commitPerfMeta, attr => attr.isReturnAndCall && attr.isIndirect)
       ),
-      ("call", commitPerfMeta.mispredictBranchInfo.attribute.isCall),
-      ("ret", commitPerfMeta.mispredictBranchInfo.attribute.isReturn)
+      ("call", true.B, PerfNumCfiWithAttr(commitPerfMeta, attr => attr.isCall)),
+      ("ret", true.B, PerfNumCfiWithAttr(commitPerfMeta, attr => attr.isReturn))
     )
   )
```
