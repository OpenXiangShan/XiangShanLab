# Commit Log
- Issue: #5601
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5601
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5601
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5601
- Changed files: 2
- Additions: 3
- Deletions: 4

## Files
- `src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
index 912ae666010..6b50b62a70d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
@@ -412,7 +412,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   private val t1_oldBWEntries   = VecInit(t1_meta.scBWResp.map(v => VecInit(v.map(r => r.asTypeOf(new ScEntry())))))
   private val t1_oldBiasEntries = VecInit(t1_meta.scBiasResp.map(v => v.asTypeOf(new ScEntry())))
   private val t1_oldBiasLowBits = t1_meta.scBiasLowerBits
-  private val t1_mbtbPosition   = VecInit(t1_train.meta.mbtb.entries.flatten.map(e => e.position))
+  private val t1_mbtbEntries    = t1_train.meta.mbtb.entries.flatten
 
   private val t1_branchesWayIdxVec = VecInit(t1_branches.map(b => getWayIdx(b.bits.cfiPosition)))
   private val t1_branchesScIdxVec  = WireInit(VecInit.fill(ResolveEntryBranchNumber)(0.U(log2Ceil(NumWays).W)))
@@ -430,7 +430,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   // MBTB may invalidate entry with larger idx during multihit, and the order needs to be reversed
   t1_branches.zipWithIndex.foreach { case (branch, branchIdx) =>
     for (i <- (0 until NumWays).reverse) {
-      when(branch.valid && (t1_mbtbPosition(i) === branch.bits.cfiPosition)) { // branch.valid may have been recalculated on t1_writeValidVec
+      when(branch.valid && t1_mbtbEntries(i).hit(branch.bits)) { // branch.valid may have been recalculated on t1_writeValidVec
         t1_branchesScIdxHitVec(branchIdx) := true.B
         t1_branchesScIdxVec(branchIdx)    := i.U
       }
@@ -715,7 +715,6 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   dontTouch(t1_branchesWayIdxVec)
   dontTouch(t1_writeThresVec)
   dontTouch(t1_meta)
-  dontTouch(t1_mbtbPosition)
   dontTouch(scCorrectVec)
   dontTouch(scWrongVec)
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 4bb18b60b47..45eaf903046 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -154,7 +154,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     io.prediction(i).hasAlt       := hasAlt
     io.prediction(i).altPred      := alt.takenCtr.isPositive
 
-    io.toSc.providerTakenCtrVec(i).valid := hasProvider
+    io.toSc.providerTakenCtrVec(i).valid := hasProvider && branch.valid
     io.toSc.providerTakenCtrVec(i).bits  := provider.takenCtr
 
     io.meta.entries(i).useProvider       := useProvider
```
