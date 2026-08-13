# Commit Log
- Issue: #5254
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5254
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5254
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5254
- Changed files: 1
- Additions: 16
- Deletions: 17

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index a899d2c1df4..6a26baa530d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -355,7 +355,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     updateInfo.useAlt    := useAlt
     updateInfo.finalPred := finalPred
 
-    updateInfo.needAllocate := isCond && branch.bits.mispredict && !(hasProvider && providerTableOH(NumTables - 1))
+    updateInfo.needAllocate := isCond && finalPred =/= actualTaken && !(hasProvider && providerTableOH(NumTables - 1))
 
     updateInfo.increaseUseAlt := increaseUseAlt
     updateInfo.decreaseUseAlt := decreaseUseAlt
@@ -404,14 +404,11 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
       }
   }
 
-  private val t2_needAllocateBranchMask = t2_allBranchUpdateInfo.map(_.needAllocate)
-  when(t2_valid) {
-    assert(PopCount(t2_needAllocateBranchMask) <= 1.U)
-  }
-  private val t2_needAllocate   = t2_needAllocateBranchMask.reduce(_ || _)
-  private val t2_allocateBranch = Mux1H(t2_needAllocateBranchMask, t2_branches)
+  private val t2_needAllocate         = t2_allBranchUpdateInfo.map(_.needAllocate).reduce(_ || _)
+  private val t2_needAllocateBranchOH = PriorityEncoderOH(t2_allBranchUpdateInfo.map(_.needAllocate))
+  private val t2_allocateBranch       = Mux1H(t2_needAllocateBranchOH, t2_branches)
   private val t2_allocateBranchProviderTableOH =
-    Mux1H(t2_needAllocateBranchMask, t2_allBranchUpdateInfo.map(_.providerTableOH))
+    Mux1H(t2_needAllocateBranchOH, t2_allBranchUpdateInfo.map(_.providerTableOH))
 
   // allocate new entry to the table with a longer history
   private val t2_longerHistoryTableMask = Mux(
@@ -421,16 +418,18 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   )
   dontTouch(t2_longerHistoryTableMask)
 
-  private val t2_allTableNotUsefulWayMask = t2_allTableUsefulCtrs.map { ctrsPerTable =>
-    ctrsPerTable.map(_.value === 0.U).asUInt
-  }
-  private val t2_hasNotUsefulEntryTableMask = t2_allTableNotUsefulWayMask.map(_.orR).asUInt
-  private val t2_canAllocateTableMask       = t2_longerHistoryTableMask & t2_hasNotUsefulEntryTableMask
-  private val t2_canAllocate                = t2_canAllocateTableMask.orR
+  private val t2_allTableCanAllocateWayMask =
+    t2_allTableEntries.zip(t2_allTableUsefulCtrs).map { case (entriesPerTable, ctrsPerTable) =>
+      entriesPerTable.zip(ctrsPerTable).map { case (entry, usefulCtr) =>
+        !entry.valid || entry.valid && entry.takenCtr.isWeak && usefulCtr.value === 0.U
+      }.asUInt
+    }
+  private val t2_canAllocateTableMask = t2_longerHistoryTableMask & t2_allTableCanAllocateWayMask.map(_.orR).asUInt
+  private val t2_canAllocate          = t2_canAllocateTableMask.orR
+  private val t2_allocate             = t2_needAllocate && t2_canAllocate
 
-  private val t2_allocateTableMaskOH =
-    PriorityEncoderOH(t2_canAllocateTableMask) & Fill(NumTables, t2_needAllocate && t2_canAllocate)
-  private val t2_allocateWayMaskOH = PriorityEncoderOH(Mux1H(t2_allocateTableMaskOH, t2_allTableNotUsefulWayMask))
+  private val t2_allocateTableMaskOH = PriorityEncoderOH(t2_canAllocateTableMask) & Fill(NumTables, t2_allocate)
+  private val t2_allocateWayMaskOH   = PriorityEncoderOH(Mux1H(t2_allocateTableMaskOH, t2_allTableCanAllocateWayMask))
   dontTouch(t2_allocateTableMaskOH)
   dontTouch(t2_allocateWayMaskOH)
```
