# Commit Log
- Issue: #4244
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4244
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4244
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4244
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
index 805b9ade0bb..200eeb4013b 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
@@ -407,7 +407,7 @@ class VFAlu(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
     dontTouch(allFFlagsEn)
     dontTouch(fflagsRedMask)
   }
-  allFFlagsEn := Mux(outIsResuction, Cat(Fill(4*numVecModule - 1, firstNeedFFlags || outIsVfRedUnSum) & fflagsRedMask(4*numVecModule - 1, 1),
+  allFFlagsEn := Mux(outIsResuction, Cat(Fill(4*numVecModule - 1, firstNeedFFlags || outIsVfRedUnSum && !outVecCtrl.lastUop) & fflagsRedMask(4*numVecModule - 1, 1),
     lastNeedFFlags || firstNeedFFlags || outIsVfRedOrdered || outIsVfRedUnSum), fflagsEn & vlMaskEn).asTypeOf(allFFlagsEn)
 
   val allFFlags = fflagsData.asTypeOf(Vec( 4*numVecModule,UInt(5.W)))
```
