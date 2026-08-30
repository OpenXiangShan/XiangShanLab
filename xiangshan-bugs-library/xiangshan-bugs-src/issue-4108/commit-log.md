# Commit Log
- Issue: #4108
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4108
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4108
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4108
- Changed files: 1
- Additions: 5
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/decode/FusionDecoder.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/FusionDecoder.scala b/src/main/scala/xiangshan/backend/decode/FusionDecoder.scala
index fbbdb4c69f8..a4f21323667 100644
--- a/src/main/scala/xiangshan/backend/decode/FusionDecoder.scala
+++ b/src/main/scala/xiangshan/backend/decode/FusionDecoder.scala
@@ -586,8 +586,12 @@ class FusionDecoder(implicit p: Parameters) extends XSModule {
     val fire = io.in(i).valid && io.inReady(i)
     val instrPairValid = RegEnable(VecInit(pair.map(_.valid)).asUInt.andR, false.B, io.inReady(i))
     val fusionVec = RegEnable(VecInit(fusionList.map(_.isValid)), fire)
+    // HINT instructions are not considered for fusion.
+    // NOTE: The RD of some FENCE instructions are not 0, but they are also HINT instructions.
+    //       However, as FENCE instructions can never be fused, we do not need to consider them.
+    val notHint = RegEnable(VecInit(pair.map(_.bits(11, 7) =/= 0.U)).asUInt.andR, fire)
     val thisCleared = io.clear(i)
-    out.valid := instrPairValid && !thisCleared && fusionVec.asUInt.orR
+    out.valid := instrPairValid && !thisCleared && fusionVec.asUInt.orR && notHint
     XSError(instrPairValid && PopCount(fusionVec) > 1.U, "more then one fusion matched\n")
     def connectByInt(field: FusionDecodeReplace => Valid[UInt], replace: Seq[Option[Int]]): Unit = {
       field(out.bits).valid := false.B
```
