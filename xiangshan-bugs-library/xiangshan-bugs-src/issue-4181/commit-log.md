# Commit Log
- Issue: #4181
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4181
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4181
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4181
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
index 47ce81386da..2e2b93af51a 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
@@ -105,7 +105,7 @@ class VFAlu(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
     )
     val f16FirstFoldMaskUnorder = Mux1H(
       Seq(
-        vecCtrl.fpu.isFoldTo1_2 -> Cat(f16Mask(7,4), f16Mask(3,0)),
+        vecCtrl.fpu.isFoldTo1_2 -> Cat(f16Mask(3,0), f16Mask(7,4)),
         vecCtrl.fpu.isFoldTo1_4 -> Cat(0.U(2.W), f16Mask(1), f16Mask(0), 0.U(2.W), f16Mask(3), f16Mask(2)),
         vecCtrl.fpu.isFoldTo1_8 -> Cat(0.U(3.W), f16Mask(0), 0.U(3.W), f16Mask(1)),
       )
```
