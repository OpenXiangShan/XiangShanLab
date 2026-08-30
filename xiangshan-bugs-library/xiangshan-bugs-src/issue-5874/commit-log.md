# Commit Log
- Issue: #5874
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5874
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5874
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5874
- Changed files: 2
- Additions: 17
- Deletions: 4

## Files
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`
- `src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index c2c27dedd62..e99df64a441 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -199,6 +199,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s1_maybeRvc    = Cat(s1_maybeRvcMap, s1_maybeRvcMap) >> s1_fetchBlock(0).startVAddr(5, 1)
   private val s1_rawData     = fromICache.bits.data
   private val s1_perfInfo    = io.fromICache.perf
+  private val s1_firstHasException = s1_icacheMeta(0).exception.hasException
 
   instrBoundary.io.req.valid                 := s1_valid
   instrBoundary.io.req.instrRange            := s1_totalInstrRange.asTypeOf(Vec(FetchBlockInstNum, Bool()))
@@ -274,6 +275,15 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s1_rawSecondData        = 0.U((ICacheLineBytes * 8).W)
   private val s1_rawFirstDataDupWire  = VecInit(Seq.fill(FetchPorts)(s1_rawFirstData))
   private val s1_rawSecondDataDupWire = VecInit(Seq.fill(FetchPorts)(s1_rawSecondData))
+  // When an exception occurs, forward the exception information immediately instead of
+  // waiting for instruction concatenation to complete.
+  private val s1_realInstrValid =
+    Mux(s1_firstHasException, 1.U(FetchBlockInstNum.W), UIntToMask(PopCount(rawInstrEndVec), FetchBlockInstNum))
+  private val s1_realInstrCount = Mux(
+    s1_firstHasException,
+    1.U(log2Ceil(FetchBlockInstNum).W),
+    PopCount(rawInstrEndVec)
+  )
   // Special case for MMIO:
   // If two fetches occur and the first is non-MMIO while the second is MMIO,
   // delay the second fetch by one cycle to split into a one-fetch.
@@ -294,8 +304,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
   s2_ready := s2_fire || !s2_valid
 
   private val s2_instrCompactInfo = RegEnable(instrCompactInfo, s1_fire)
-  private val s2_instrCount       = RegEnable(PopCount(rawInstrEndVec), s1_fire)
-  private val s2_instrValid       = RegEnable(UIntToMask(PopCount(rawInstrEndVec), FetchBlockInstNum), s1_fire)
+  private val s2_instrCount       = RegEnable(s1_realInstrCount, s1_fire)
+  private val s2_instrValid       = RegEnable(s1_realInstrValid, s1_fire)
 
   private val s2_rawIndex           = RegEnable(instrCountBeforeCurrent, s1_fire)
   private val s2_rawInstrEndVec     = RegEnable(rawInstrEndVec, s1_fire)
@@ -561,7 +571,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
   s3_valid := ValidHold(
     // infire: s2 -> s3 fire
     s2_fire && !s2_flush,
-    // outfire:
+    // outfire: When an uncache cross-page occurs and it is not an exception,
+    // this instruction fetch should end and prepare to receive the next fetch signal.
     io.toIBuffer.fire || s3_uncacheCrossPageMask,
     // On flush, waiting for uncache response is handled by the channel itself.
     s3_flush
diff --git a/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala b/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
index e84b667130a..27a1961c0a5 100644
--- a/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
@@ -116,7 +116,9 @@ class IfuUncacheUnit(implicit p: Parameters) extends IfuModule with IfuHelper {
         val crossPage = fromUncache.bits.incomplete
         uncacheState     := UncacheFsmState.Idle
         uncacheException := exception
-        uncacheCrossPage := crossPage
+        // Cross-page exception: only when triggered at the exact page crossing;
+        // exceptions before crossing are normal exceptions
+        uncacheCrossPage := crossPage && !exception.hasException
         uncacheData      := fromUncache.bits.data
       }
     }
```
