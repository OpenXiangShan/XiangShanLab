# Commit Log
- Issue: #5184
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5184
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5184
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5184
- Changed files: 1
- Additions: 3
- Deletions: 7

## Files
- `src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
index 8b67970dcb0..86da001412f 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
@@ -148,15 +148,11 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
   )
 
   // Select the branch needed for training
-  val trainBranchIdxOH: Vec[Bool] = VecInit(t1_train.branches.map(b =>
+  val trainBranchIdxVec: Vec[Bool] = VecInit(t1_train.branches.map(b =>
     b.valid && b.bits.attribute.isOtherIndirect && b.bits.taken
   ))
-  assert(
-    PopCount(trainBranchIdxOH) <= 1.U,
-    "At most one branch in branches should be valid and isOtherIndirect for ITTAGE update"
-  )
-  val trainBranchIdx: UInt = OHToUInt(trainBranchIdxOH)
-  val hasTrainBranch: Bool = trainBranchIdxOH.asUInt.orR
+  val trainBranchIdx: UInt = PriorityEncoder(trainBranchIdxVec)
+  val hasTrainBranch: Bool = trainBranchIdxVec.asUInt.orR
 
   // Update condition for ittage
   private val updateValid = hasTrainBranch && RegNext(io.train.valid, init = false.B)
```
