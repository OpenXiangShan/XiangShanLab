# Commit Log
- Issue: #3399
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3399
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3399
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3399
- Changed files: 1
- Additions: 8
- Deletions: 8

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
index f0113f99a79..ba8ad8e9ece 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
@@ -185,20 +185,20 @@ class VFAlu(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
   }
   def genMaskForRedFFlag(sew:UInt): UInt = {
     val default = "b11111111".U
-    val f64FoldMask = Mux(vecCtrl.fpu.isFoldTo1_2, "b00000001".U, default)
-    val f32Fold = vecCtrl.fpu.isFoldTo1_2 || vecCtrl.fpu.isFoldTo1_4
+    val f64FoldMask = Mux(outVecCtrl.fpu.isFoldTo1_2, "b00000001".U, default)
+    val f32Fold = outVecCtrl.fpu.isFoldTo1_2 || outVecCtrl.fpu.isFoldTo1_4
     val f32FoldMask = Mux1H(
       Seq(
-        vecCtrl.fpu.isFoldTo1_2 -> "b00000011".U,
-        vecCtrl.fpu.isFoldTo1_4 -> "b00000001".U,
+        outVecCtrl.fpu.isFoldTo1_2 -> "b00000011".U,
+        outVecCtrl.fpu.isFoldTo1_4 -> "b00000001".U,
       )
     )
-    val f16Fold = vecCtrl.fpu.isFoldTo1_2 || vecCtrl.fpu.isFoldTo1_4 || vecCtrl.fpu.isFoldTo1_8
+    val f16Fold = outVecCtrl.fpu.isFoldTo1_2 || outVecCtrl.fpu.isFoldTo1_4 || outVecCtrl.fpu.isFoldTo1_8
     val f16FoldMask = Mux1H(
       Seq(
-        vecCtrl.fpu.isFoldTo1_2 -> "b00001111".U,
-        vecCtrl.fpu.isFoldTo1_4 -> "b00000011".U,
-        vecCtrl.fpu.isFoldTo1_8 -> "b00000001".U,
+        outVecCtrl.fpu.isFoldTo1_2 -> "b00001111".U,
+        outVecCtrl.fpu.isFoldTo1_4 -> "b00000011".U,
+        outVecCtrl.fpu.isFoldTo1_8 -> "b00000001".U,
       )
     )
     Mux1H(
```
