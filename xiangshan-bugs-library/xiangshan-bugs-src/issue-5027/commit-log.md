# Commit Log
- Issue: #5027
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5027
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5027
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5027
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
index 56ab3b19131..185da79094a 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
@@ -50,7 +50,7 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
 
   val brhPredictTarget = io.in.bits.ctrl.predictInfo.get.target
   val brhRealTarget = addModule.io.target
-  val isMisPred = dataModule.io.mispredict || (brhRealTarget =/= brhPredictTarget)
+  val isMisPred = dataModule.io.mispredict || dataModule.io.pred_taken && dataModule.io.taken && (brhRealTarget =/= brhPredictTarget)
   io.out.bits.res.data := 0.U
   io.out.bits.res.redirect.get match {
     case redirect =>
```
