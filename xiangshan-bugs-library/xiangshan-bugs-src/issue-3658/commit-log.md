# Commit Log
- Issue: #3658
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3658
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3658
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3658
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
index ba8ad8e9ece..4a52c28e27f 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala
@@ -413,7 +413,6 @@ class VFAlu(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
   val outFFlags = allFFlagsEn.zip(allFFlags).map{
     case(en,fflags) => Mux(en, fflags, 0.U(5.W))
   }.reduce(_ | _)
-  io.out.bits.res.fflags.get := outFFlags
 
 
   val cmpResultOldVd = Wire(UInt(cmpResultWidth.W))
@@ -498,6 +497,7 @@ class VFAlu(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
   resultFpMask := Mux(isFclass || isCmp, Fill(16, 1.U(1.W)), Fill(VLEN, 1.U(1.W)))
   // when dest is mask, the result need to be masked by mgtu
   io.out.bits.res.data := Mux(notModifyVd, outOldVd, Mux(outVecCtrl.isDstMask, mgtu.io.out.vd, mgu.io.out.vd) & resultFpMask)
+  io.out.bits.res.fflags.get := Mux(notModifyVd, 0.U(5.W), outFFlags)
   io.out.bits.ctrl.exceptionVec.get(ExceptionNO.illegalInstr) := mgu.io.out.illegal
 
 }
```
