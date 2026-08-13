# Commit Log
- Issue: #5266
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5266
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5266
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5266
- Changed files: 1
- Additions: 8
- Deletions: 9

## Files
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
index 1878debec1b..8fc473fa2fb 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
@@ -230,23 +230,22 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
   private val t1_trainTarget          = t1_train.finalPrediction.target
   private val t1_trainTargetLowerBits = getTargetLowerBits(t1_trainTarget)
 
-  private val t1_positionBeforeMask = t1_meta.hitMask.zip(t1_meta.positions).zip(t1_meta.attributes).map {
-    case ((hit, position), attribute) => hit && position < t1_trainPosition && attribute.isConditional
-  }
-  private val t1_positionEqualMask = t1_meta.hitMask.zip(t1_meta.positions).zip(t1_meta.attributes).map {
-    case ((hit, position), attribute) => hit && position === t1_trainPosition && attribute.isConditional
+  private val t1_condMask = t1_meta.hitMask.zip(t1_meta.attributes).map {
+    case (hit, attribute) => hit && attribute.isConditional
   }
+  private val t1_positionBeforeMask = t1_meta.positions.map(_ < t1_trainPosition)
+  private val t1_positionEqualMask  = t1_meta.positions.map(_ === t1_trainPosition)
 
   takenCounter.zip(banks).zipWithIndex.foreach { case ((ctrsPerBank, bank), bankIdx) =>
     ctrsPerBank.zipWithIndex.foreach { case (ctrsPerSet, setIdx) =>
-      ctrsPerSet.zip(t1_positionBeforeMask).zip(t1_positionEqualMask).zipWithIndex.foreach {
-        case (((ctr, before), equal), wayIdx) =>
+      ctrsPerSet.zip(t1_condMask).zip(t1_positionBeforeMask).zip(t1_positionEqualMask).zipWithIndex.foreach {
+        case ((((ctr, isCond), before), equal), wayIdx) =>
           val needReset = bank.io.writeResp.valid && bank.io.writeResp.bits.needResetCtr &&
             setIdx.U === bank.io.writeResp.bits.setIdx && wayIdx.U === bank.io.writeResp.bits.wayIdx
 
           val updateThisSet = t1_valid && t1_bankMask(bankIdx) && t1_setMask(setIdx)
-          val needDecrease  = updateThisSet && (!t1_trainTaken || t1_trainTaken && before)
-          val needIncrease  = updateThisSet && t1_trainTaken && equal
+          val needDecrease  = updateThisSet && (!t1_trainTaken || t1_trainTaken && before) && isCond
+          val needIncrease  = updateThisSet && t1_trainTaken && equal && isCond
 
           when(needReset)(ctr.resetNeutral())
             .elsewhen(needDecrease)(ctr.decrease())
```
