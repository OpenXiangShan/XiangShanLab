# Commit Log
- Issue: #3607
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3607
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3607
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3607
- Changed files: 1
- Additions: 4
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala
index 86d95ef23cc..fa8c5dd8b1b 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala
@@ -70,7 +70,10 @@ class VCVT(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
   val outputWidth1H = output1H
   val outIs32bits = RegNext(RegNext(outputWidth1H(2)))
   val outIsInt = !outCtrl.fuOpType(6)
-  val outIsMvInst = outCtrl.fuOpType === FuOpType.FMVXF
+  
+  // May be useful in the future.
+  // val outIsMvInst = outCtrl.fuOpType === FuOpType.FMVXF
+  val outIsMvInst = false.B
 
   val outEew = RegEnable(RegEnable(Mux1H(output1H, Seq(0,1,2,3).map(i => i.U)), fire), fireReg)
   private val needNoMask = outVecCtrl.fpu.isFpToVecInst
```
