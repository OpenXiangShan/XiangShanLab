# Commit Log
- Issue: #5551
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5551
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5551
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5551
- Changed files: 2
- Additions: 12
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/SaturateCounter.scala`
- `src/main/scala/xiangshan/frontend/bpu/SignedSaturateCounter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/SaturateCounter.scala b/src/main/scala/xiangshan/frontend/bpu/SaturateCounter.scala
index e9ac8185abd..9b7462468db 100644
--- a/src/main/scala/xiangshan/frontend/bpu/SaturateCounter.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/SaturateCounter.scala
@@ -29,6 +29,11 @@ class SaturateCounter(width: Int) extends Bundle { // scalastyle:ignore number.o
   def >=(that:  SaturateCounter): Bool = this.value >= that.value
 
   /* *** state methods *** */
+  /* example: 3bit
+   *  0   1   2   3 | 4   5   6   7
+   *       Negative | Positive
+   *  sat  mid weak | weak mid  sat
+   */
   // direction
   def isPositive: Bool = value(width - 1)  // value >= (1 << (width - 1)).U
   def isNegative: Bool = !value(width - 1) // value < (1 << (width - 1)).U
diff --git a/src/main/scala/xiangshan/frontend/bpu/SignedSaturateCounter.scala b/src/main/scala/xiangshan/frontend/bpu/SignedSaturateCounter.scala
index e2b75533170..1a9cafe2342 100644
--- a/src/main/scala/xiangshan/frontend/bpu/SignedSaturateCounter.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/SignedSaturateCounter.scala
@@ -29,6 +29,11 @@ class SignedSaturateCounter(width: Int) extends Bundle { // scalastyle:ignore nu
   def >=(that:  SignedSaturateCounter): Bool = this.value >= that.value
 
   /* *** state methods *** */
+  /* example: 3bit
+   * -4  -3  -2  -1 | 0   1   2   3
+   *       Negative | Positive
+   *  sat  mid weak | weak mid  sat
+   */
   // direction
   def isPositive: Bool = value >= 0.S
   def isNegative: Bool = value < 0.S
@@ -40,9 +45,9 @@ class SignedSaturateCounter(width: Int) extends Bundle { // scalastyle:ignore nu
     isSaturatePositive && positive || isSaturateNegative && !positive
 
   // weak
-  def isWeakPositive: Bool = { // value === 1.S
+  def isWeakPositive: Bool = { // value === 0.S
     require(width >= 2, "SignedSaturateCounter width must be at least 2 to have weak states")
-    value === 1.S
+    value === 0.S
   }
   def isWeakNegative: Bool = { // value === -1.S
     require(width >= 2, "SignedSaturateCounter width must be at least 2 to have weak states")
```
