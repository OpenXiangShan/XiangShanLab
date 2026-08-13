# Commit Log
- Issue: #5603
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5603
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5603
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5603
- Changed files: 2
- Additions: 6
- Deletions: 3

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/CompareMatrix.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 864a96bc522..dbe5ad50d8b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -317,6 +317,9 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   s1_utageMeta := utage.io.meta.bits
   s1_utageMeta.debug_useMicroTage.foreach(_ := s1_utageHitMask.reduce(_ || _))
 
+  // calculate compare matrix in s2 to optimize timing
+  private val s2_compareMatrix = CompareMatrix(VecInit(mbtb.io.result.map(_.bits.cfiPosition)))
+
   /* *** s3 prediction selection *** */
   private val s3_mbtbResult     = RegEnable(mbtb.io.result, s2_fire)
   private val s3_tagePrediction = RegEnable(tage.io.prediction, s2_fire)
@@ -344,7 +347,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   })
   private val s3_taken = s3_takenMask.reduce(_ || _)
 
-  private val s3_compareMatrix      = CompareMatrix(VecInit(s3_mbtbResult.map(_.bits.cfiPosition)))
+  private val s3_compareMatrix      = RegEnable(s2_compareMatrix, s2_fire)
   private val s3_firstTakenBranchOH = s3_compareMatrix.getLeastElementOH(s3_takenMask)
   private val s3_firstTakenBranch   = Mux1H(s3_firstTakenBranchOH, s3_mbtbResult)
   private val s3_useRas             = s3_firstTakenBranch.bits.attribute.isReturn
diff --git a/src/main/scala/xiangshan/frontend/bpu/CompareMatrix.scala b/src/main/scala/xiangshan/frontend/bpu/CompareMatrix.scala
index d309b536723..01f46974451 100644
--- a/src/main/scala/xiangshan/frontend/bpu/CompareMatrix.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/CompareMatrix.scala
@@ -19,7 +19,7 @@ import chisel3._
 import chisel3.util._
 
 class CompareMatrix(n: Int) extends Bundle {
-  val m: Vec[Vec[Bool]] = Wire(Vec(n, Vec(n, Bool())))
+  val m: Vec[Vec[Bool]] = Vec(n, Vec(n, Bool()))
 
   def apply(i: Int): Vec[Bool] = m(i)
 
@@ -119,7 +119,7 @@ object CompareMatrix {
       order: (UInt, UInt) => Bool = (a: UInt, b: UInt) => a < b
   ): CompareMatrix = {
     val n = value.length
-    val m = new CompareMatrix(n)
+    val m = Wire(new CompareMatrix(n))
     (0 until n).foreach { i =>
       (0 until n).foreach { j =>
         if (i == j)
```
