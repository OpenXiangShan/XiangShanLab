# Commit Log
- Issue: #5072
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5072
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5072
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5072
- Changed files: 6
- Additions: 13
- Deletions: 17

## Files
- `src/main/scala/xiangshan/frontend/ftq/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/Bundles.scala b/src/main/scala/xiangshan/frontend/ftq/Bundles.scala
index c98ee77e239..3a4ba3ff62a 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Bundles.scala
@@ -55,23 +55,19 @@ class FtqRead[T <: Data](private val gen: T)(implicit p: Parameters) extends Ftq
 }
 
 class BpuFlushInfo(implicit p: Parameters) extends FtqBundle with HasCircularQueuePtrHelper {
-  // when ifu pipeline is not stalled,
-  // a packet from bpu s3 can reach f1 at most
-  val s2 = Valid(new FtqPtr)
   val s3 = Valid(new FtqPtr)
 
   def stage(idx: Int): Valid[FtqPtr] = {
-    require(idx >= 2 && idx <= 3)
+    require(idx >= 3 && idx <= 3)
     idx match {
-      case 2 => s2
       case 3 => s3
     }
   }
 
-  def shouldFlushBy(src: Valid[FtqPtr], idx_to_flush: FtqPtr) =
-    src.valid && !isAfter(src.bits, idx_to_flush)
-  def shouldFlushByStage2(idx: FtqPtr) = shouldFlushBy(s2, idx)
-  def shouldFlushByStage3(idx: FtqPtr) = shouldFlushBy(s3, idx)
+  private def shouldFlushBy(src: Valid[FtqPtr], idxToFlush: FtqPtr, valid: Bool): Bool =
+    valid && src.valid && !isAfter(src.bits, idxToFlush)
+
+  def shouldFlushByStage3(idx: FtqPtr, valid: Bool): Bool = shouldFlushBy(s3, idx, valid)
 }
 
 class FtqToCtrlIO(implicit p: Parameters) extends FtqBundle {
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index a6928162358..5e82f4efa31 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -182,7 +182,7 @@ class Ftq(implicit p: Parameters) extends FtqModule
   }
 
   // TODO: wait for Ifu/ICache to remove bpu s2 flush
-  for (stage <- 2 to 3) {
+  for (stage <- 3 to 3) {
     val redirect = if (stage == 3) prediction.bits.s3Override else false.B
     val ftqIdx   = if (stage == 3) io.fromBpu.s3FtqPtr else 0.U.asTypeOf(new FtqPtr)
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 55bd72a6824..c504db942f5 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -157,7 +157,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   toData.bits.waymask      := s0_waymasks
 
   private val s0_canGo = toData.ready && fromWayLookup.valid && s1_ready
-  s0_flush := io.flush || io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx)
+  s0_flush := io.flush || io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx, s0_valid)
   s0_fire  := s0_valid && s0_canGo && !s0_flush
 
   fromFtq.ready := s0_canGo
@@ -378,7 +378,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   toIfu.bits.gpAddr            := s1_gpAddr
   toIfu.bits.isForVSnonLeafPTE := s1_isForVSnonLeafPTE
 
-  s1_flush := io.flush || io.flushFromBpu.shouldFlushByStage3(s1_ftqIdx)
+  s1_flush := io.flush || io.flushFromBpu.shouldFlushByStage3(s1_ftqIdx, s1_valid)
   s1_ready := (s1_fetchFinish && !io.respStall) || !s1_valid
   s1_fire  := s1_valid && s1_fetchFinish && !io.respStall && !s1_flush
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
index d4511122f39..43745761c21 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
@@ -90,7 +90,7 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
   private val s0_vSetIdx          = VecInit(s0_vAddr.map(get_idx))
   private val s0_backendException = io.req.bits.backendException
 
-  fromBpuS0Flush := !s0_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx)
+  fromBpuS0Flush := !s0_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s0_ftqIdx, s0_valid)
   s0_flush       := io.flush || fromBpuS0Flush || s1_flush
 
   private val s0_canGo = s1_ready && toItlb.ready && toMeta.ready
@@ -367,7 +367,7 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
   }
 
   /** Stage 1 control */
-  fromBpuS1Flush := s1_valid && !s1_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s1_ftqIdx)
+  fromBpuS1Flush := !s1_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s1_ftqIdx, s1_valid)
   s1_flush       := io.flush || fromBpuS1Flush
   // when s1 is flushed, itlb pipeline should also be flushed
   io.itlbFlushPipe := s1_flush
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
index 92b6af48166..7ec8aaa8f4c 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
@@ -63,7 +63,7 @@ class ICacheWayLookup(implicit p: Parameters) extends ICacheModule
   // so the tailing 0 (already bypassed to if1) or 1 (if1 stall, stored here) entries might be flushed by bp3,
   // therefore, when shouldFlushByStage3, we need to move back writePtr by 0 (empty) or 1.
   // If in future we have bp4 (or even more) flush, this might not be enough.
-  private val bpuS3FlushValid = !empty && io.flushFromBpu.shouldFlushByStage3(tailFtqIdx)
+  private val bpuS3FlushValid = io.flushFromBpu.shouldFlushByStage3(tailFtqIdx, !empty)
   private val bpuS3FlushPtr   = writePtr - 1.U
 
   when(io.flush) {
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index 399416136f9..7cff1df78b0 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -204,7 +204,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   s0_fire := fromFtq.req.fire
 
   s0_flushFromBpu := s0_ftqFetch.map(fetch =>
-    fromFtq.flushFromBpu.shouldFlushByStage3(fetch.ftqIdx)
+    fromFtq.flushFromBpu.shouldFlushByStage3(fetch.ftqIdx, fetch.valid)
   )
 
   private val backendRedirect          = WireInit(false.B)
@@ -249,7 +249,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s1_doubleline = RegEnable(s0_doubleline, s0_fire)
 
   s1_flushFromBpu := s1_ftqFetch.map(fetch =>
-    fromFtq.flushFromBpu.shouldFlushByStage3(fetch.ftqIdx)
+    fromFtq.flushFromBpu.shouldFlushByStage3(fetch.ftqIdx, fetch.valid)
   )
 
   private val icacheRespAllValid = WireInit(false.B)
```
