# Commit Log
- Issue: #5611
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5611
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5611
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5611
- Changed files: 1
- Additions: 22
- Deletions: 22

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 9b3a1528337..bec71d07e6e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -255,33 +255,33 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   private val s1_utageHitMask = VecInit(s1_btbPrediction.map { pred =>
     pred.valid && utage.io.prediction.valid && utage.io.prediction.bits.cfiPosition === pred.bits.cfiPosition
   })
-  private val s1_takenMask = VecInit(s1_btbPrediction.zipWithIndex.map { case (pred, i) =>
-    val utageHit   = s1_utageHitMask(i)
-    val utageTaken = utage.io.prediction.bits.taken
-    pred.valid && (
-      pred.bits.attribute.isDirect ||
-        pred.bits.attribute.isIndirect ||
-        pred.bits.attribute.isConditional && Mux(utageHit, utageTaken, pred.bits.taken)
-    )
-  })
-
-  // the old way to get taken mask
-//  private val s1_baseTakenMask = VecInit(s1_btbPrediction.map { pred =>
+//  private val s1_takenMask = VecInit(s1_btbPrediction.zipWithIndex.map { case (pred, i) =>
+//    val utageHit   = s1_utageHitMask(i)
+//    val utageTaken = utage.io.prediction.bits.taken
 //    pred.valid && (
 //      pred.bits.attribute.isDirect ||
 //        pred.bits.attribute.isIndirect ||
-//        pred.bits.attribute.isConditional && pred.bits.taken
-//    )
-//  })
-//  private val s1_microTageTakenMask = VecInit(s1_btbPrediction.zip(s1_utageHitMask).map { case (e, microTageHit) =>
-//    e.valid && (
-//      e.bits.attribute.isDirect ||
-//        e.bits.attribute.isIndirect ||
-//        e.bits.attribute.isConditional && Mux(microTageHit, utage.io.prediction.bits.taken, false.B)
+//        pred.bits.attribute.isConditional && Mux(utageHit, utageTaken, pred.bits.taken)
 //    )
 //  })
-//  private val s1_useMicroTage = s1_utageHitMask.reduce(_ || _)
-//  private val s1_takenMask    = Mux(s1_useMicroTage, s1_microTageTakenMask, s1_baseTakenMask)
+
+  // the old way to get taken mask
+  private val s1_baseTakenMask = VecInit(s1_btbPrediction.map { pred =>
+    pred.valid && (
+      pred.bits.attribute.isDirect ||
+        pred.bits.attribute.isIndirect ||
+        pred.bits.attribute.isConditional && pred.bits.taken
+    )
+  })
+  private val s1_microTageTakenMask = VecInit(s1_btbPrediction.zip(s1_utageHitMask).map { case (e, microTageHit) =>
+    e.valid && (
+      e.bits.attribute.isDirect ||
+        e.bits.attribute.isIndirect ||
+        e.bits.attribute.isConditional && Mux(microTageHit, utage.io.prediction.bits.taken, false.B)
+    )
+  })
+  private val s1_useMicroTage = s1_utageHitMask.reduce(_ || _)
+  private val s1_takenMask    = Mux(s1_useMicroTage, s1_microTageTakenMask, s1_baseTakenMask)
 
   private val s1_taken              = s1_takenMask.reduce(_ || _)
   private val s1_compareMatrix      = CompareMatrix(VecInit(s1_btbPrediction.map(_.bits.cfiPosition)))
```
