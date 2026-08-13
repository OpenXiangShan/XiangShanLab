# Commit Log
- Issue: #5461
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5461
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5461
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5461
- Changed files: 1
- Additions: 7
- Deletions: 8

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index c3f22539891..f4402011144 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -198,15 +198,14 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     (mbtbHit, basePred, meta)
   }.unzip3
 
-  private val t0_allHitBranchUseProvider = t0_mbtbHitMask.zip(t0_meta).map { case (mbtbHit, meta) =>
-    !mbtbHit || meta.useProvider // if branch not in mbtb, tage won't train it
+  // if all hit conditional branches use provider and no mispredict, use meta to train
+  private val t0_useMeta = t0_branches.zipWithIndex.map { case (branch, i) =>
+    val mbtbHit      = t0_mbtbHitMask(i)
+    val isCond       = t0_condMask(i)
+    val useProvider  = t0_meta(i).useProvider
+    val mispredicted = branch.bits.mispredict
+    !(mbtbHit && isCond) || (useProvider && !mispredicted)
   }.reduce(_ && _)
-  private val t0_hasCondMispredict = t0_condMask.zip(t0_branches).map { case (isCond, branch) =>
-    isCond && branch.bits.mispredict
-  }.reduce(_ || _)
-
-  // if all hit branches use provider and no mispredict, use meta to train
-  private val t0_useMeta  = t0_allHitBranchUseProvider && !t0_hasCondMispredict
   private val t0_needRead = !t0_useMeta
 
   private val t0_readBankConflict = t0_hasCond && t0_needRead && s0_fire && t0_bankIdx === s0_bankIdx
```
