# Commit Log
- Issue: #5677
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5677
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5677
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5677
- Changed files: 1
- Additions: 11
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 55f7071bf36..d52741d4ae7 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -466,9 +466,18 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   dontTouch(t2_longerHistoryTableMask)
 
   private val t2_allTableCanAllocateWayMask = t2_readResp.map { tableReadResp =>
-    tableReadResp.entries.zip(tableReadResp.usefulCtrs).map { case (entry, usefulCtr) =>
-      !entry.valid || entry.valid && entry.takenCtr.isWeak && usefulCtr.isSaturateNegative
+    val notValidMask  = tableReadResp.entries.map(!_.valid).asUInt
+    val notUsefulMask = tableReadResp.usefulCtrs.map(_.isSaturateNegative).asUInt
+    val ctrWeakAndNotUsefulMask = tableReadResp.entries.zip(tableReadResp.usefulCtrs).map { case (entry, usefulCtr) =>
+      entry.takenCtr.isWeak && usefulCtr.isSaturateNegative
     }.asUInt
+    MuxCase(
+      notUsefulMask,
+      Seq(
+        notValidMask.orR            -> notValidMask,
+        ctrWeakAndNotUsefulMask.orR -> ctrWeakAndNotUsefulMask
+      )
+    )
   }
   private val t2_canAllocateTableMask = t2_longerHistoryTableMask & t2_allTableCanAllocateWayMask.map(_.orR).asUInt
   private val t2_canAllocate          = t2_canAllocateTableMask.orR
```
