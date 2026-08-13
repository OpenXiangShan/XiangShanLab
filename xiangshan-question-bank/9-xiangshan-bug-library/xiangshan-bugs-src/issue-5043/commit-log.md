# Commit Log
- Issue: #5043
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5043
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5043
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5043
- Changed files: 3
- Additions: 36
- Deletions: 52

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index e94dfecd7d9..a0938452fea 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -73,7 +73,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper with Co
   private val ctrl = DelayN(io.ctrl, 2) // delay 2 cycle for timing
   fallThrough.io.enable := true.B // fallThrough is always enabled
   ubtb.io.enable        := ctrl.ubtbEnable
-  abtb.io.enable        := false.B
+  abtb.io.enable        := ctrl.abtbEnable
   mbtb.io.enable        := ctrl.mbtbEnable
   tage.io.enable        := ctrl.tageEnable
   ittage.io.enable      := ctrl.ittageEnable
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 88044d87a6d..9ceca6c2778 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -86,13 +86,6 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   baseTable.io.readReqValid := s0_fire
   baseTable.io.startPc      := s0_startVAddr
 
-  tables.zipWithIndex.foreach {
-    case (table, tableIdx) =>
-      table.io.readReq.valid         := s0_fire
-      table.io.readReq.bits.setIdx   := s0_setIdx(tableIdx)
-      table.io.readReq.bits.bankMask := s0_bankMask
-  }
-
   // to stall resolveQueue when bank conflict
   io.readBankIdx := s0_bankIdx
 
@@ -174,17 +167,17 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     branch.valid && branch.bits.attribute.isConditional
   }.reduce(_ || _)
 
+  private val t0_trainValid = io.train.valid && t0_hasConditionalBranch
   private val t0_startVAddr = io.train.bits.startVAddr
+  private val t0_branches   = io.train.bits.branches
 
   private val t0_bankIdx  = getBankIndex(t0_startVAddr)
   private val t0_bankMask = UIntToOH(t0_bankIdx, NumBanks)
 
   // TODO: should block resolveQueue when bank conflict
-  private val t0_bankConflict =
-    io.train.valid && t0_hasConditionalBranch && io.enable && s0_fire && t0_bankMask =/= s0_bankMask
+  private val t0_readBankConflict = t0_trainValid && s0_fire && t0_bankMask === s0_bankMask
 
-  private val t0_fire     = io.train.valid && t0_hasConditionalBranch && io.enable && !t0_bankConflict
-  private val t0_branches = io.train.bits.branches
+  private val t0_valid = t0_trainValid && !t0_readBankConflict && io.enable
 
   private val t0_foldedHistForIdx = histInfoForIdx.map(io.foldedPathHistForTrain.getHistWithInfo(_).foldedHist)
   private val t0_foldedHistForTag = histInfoForTag.map(io.foldedPathHistForTrain.getHistWithInfo(_).foldedHist)
@@ -196,14 +189,14 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
       getSetIndex(t0_startVAddr, hist, tableInfo.NumSets)
   }
 
-  baseTable.io.train.valid := t0_fire
+  baseTable.io.train.valid := t0_valid
   baseTable.io.train.bits  := io.train.bits
 
   tables.zipWithIndex.foreach {
     case (table, tableIdx) =>
-      table.io.readReq.valid         := t0_fire
-      table.io.readReq.bits.setIdx   := t0_setIdx(tableIdx)
-      table.io.readReq.bits.bankMask := t0_bankMask
+      table.io.readReq.valid         := s0_fire || t0_valid
+      table.io.readReq.bits.setIdx   := Mux(t0_valid, t0_setIdx(tableIdx), s0_setIdx(tableIdx))
+      table.io.readReq.bits.bankMask := Mux(t0_valid, t0_bankMask, s0_bankMask)
   }
 
   /* --------------------------------------------------------------------------------------------------------------
@@ -212,15 +205,15 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
      - compute temp tag
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val t1_fire       = RegNext(t0_fire) && io.enable
-  private val t1_startVAddr = RegEnable(t0_startVAddr, t0_fire)
-  private val t1_branches   = RegEnable(t0_branches, t0_fire)
+  private val t1_valid      = RegNext(t0_valid) && io.enable
+  private val t1_startVAddr = RegEnable(t0_startVAddr, t0_valid)
+  private val t1_branches   = RegEnable(t0_branches, t0_valid)
 
-  private val t1_setIdx   = t0_setIdx.map(RegEnable(_, t0_fire))
-  private val t1_bankMask = RegEnable(t0_bankMask, t0_fire)
+  private val t1_setIdx   = t0_setIdx.map(RegEnable(_, t0_valid))
+  private val t1_bankMask = RegEnable(t0_bankMask, t0_valid)
 
-  private val t1_foldedHistForTag        = t0_foldedHistForTag.map(RegEnable(_, t0_fire))
-  private val t1_anotherFoldedHistForTag = t0_anotherFoldedHistForTag.map(RegEnable(_, t0_fire))
+  private val t1_foldedHistForTag        = t0_foldedHistForTag.map(RegEnable(_, t0_valid))
+  private val t1_anotherFoldedHistForTag = t0_anotherFoldedHistForTag.map(RegEnable(_, t0_valid))
 
   private val t1_allTableEntries = tables.map(_.io.readResp.entries)
   private val t1_allocFailCtr    = tables.map(_.io.readResp.allocFailCtr)
@@ -245,18 +238,18 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
      - allocate a new entry when mispredict
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val t2_fire     = RegNext(t1_fire) && io.enable
-  private val t2_branches = RegEnable(t1_branches, t1_fire)
+  private val t2_valid    = RegNext(t1_valid) && io.enable
+  private val t2_branches = RegEnable(t1_branches, t1_valid)
 
-  private val t2_setIdx   = t1_setIdx.map(RegEnable(_, t1_fire))
-  private val t2_bankMask = RegEnable(t1_bankMask, t1_fire)
+  private val t2_setIdx   = t1_setIdx.map(RegEnable(_, t1_valid))
+  private val t2_bankMask = RegEnable(t1_bankMask, t1_valid)
 
-  private val t2_allTableEntries = t1_allTableEntries.map(RegEnable(_, t1_fire))
-  private val t2_allocFailCtr    = t1_allocFailCtr.map(RegEnable(_, t1_fire))
-  private val t2_tempTag         = t1_tempTag.map(RegEnable(_, t1_fire))
+  private val t2_allTableEntries = t1_allTableEntries.map(RegEnable(_, t1_valid))
+  private val t2_allocFailCtr    = t1_allocFailCtr.map(RegEnable(_, t1_valid))
+  private val t2_tempTag         = t1_tempTag.map(RegEnable(_, t1_valid))
 
-  private val t2_hasMispredictBranch = RegEnable(t1_hasMispredictBranch, t1_fire)
-  private val t2_mispredictBranch    = RegEnable(t1_mispredictBranch, t1_fire)
+  private val t2_hasMispredictBranch = RegEnable(t1_hasMispredictBranch, t1_valid)
+  private val t2_mispredictBranch    = RegEnable(t1_mispredictBranch, t1_valid)
 
   private val t2_newTakenCtr  = Wire(Vec(NumTables, Vec(NumWays, new SaturateCounter(TakenCtrWidth))))
   private val t2_newUsefulCtr = Wire(Vec(NumTables, Vec(NumWays, new SaturateCounter(UsefulCtrWidth))))
@@ -340,19 +333,19 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
       table.io.writeSetIdx   := t2_setIdx(tableIdx)
       table.io.writeBankMask := t2_bankMask
 
-      table.io.updateReq.valid             := t2_fire && t2_updateMask(tableIdx).reduce(_ || _)
+      table.io.updateReq.valid             := t2_valid && t2_updateMask(tableIdx).reduce(_ || _)
       table.io.updateReq.bits.wayMask      := t2_updateMask(tableIdx).asUInt
       table.io.updateReq.bits.newTakenCtr  := t2_newTakenCtr(tableIdx)
       table.io.updateReq.bits.newUsefulCtr := t2_newUsefulCtr(tableIdx)
 
-      table.io.allocateReq.valid        := t2_fire && t2_needAllocate && t2_allocateTableMaskOH(tableIdx)
+      table.io.allocateReq.valid        := t2_valid && t2_needAllocate && t2_allocateTableMaskOH(tableIdx)
       table.io.allocateReq.bits.wayMask := t2_allocateWayMaskOH
       table.io.allocateReq.bits.tag     := t2_tempTag(tableIdx) ^ t2_mispredictBranch.bits.cfiPosition
       table.io.allocateReq.bits.takenCtr.value :=
         Mux(t2_mispredictBranch.bits.taken, (1 << (TakenCtrWidth - 1)).U, (1 << (TakenCtrWidth - 1) - 1).U)
 
-      table.io.needResetUsefulCtr       := t2_fire && t2_needAllocate && t2_needResetUsefulCtr(tableIdx)
-      table.io.needIncreaseAllocFailCtr := t2_fire && t2_needAllocate && t2_needIncreaseAllocFailCtr(tableIdx)
+      table.io.needResetUsefulCtr       := t2_valid && t2_needAllocate && t2_needResetUsefulCtr(tableIdx)
+      table.io.needIncreaseAllocFailCtr := t2_valid && t2_needAllocate && t2_needIncreaseAllocFailCtr(tableIdx)
       table.io.oldAllocFailCtr          := t2_allocFailCtr(tableIdx)
   }
 
@@ -360,7 +353,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
      performance counter
      -------------------------------------------------------------------------------------------------------------- */
 
-  XSPerfAccumulate("total_train", t0_fire)
-  XSPerfAccumulate("read_conflict", t0_bankConflict)
+  XSPerfAccumulate("total_train", t0_valid)
+  XSPerfAccumulate("read_conflict", t0_readBankConflict)
   // TODO: add more
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
index de22a340c7d..9e44f7f446e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
@@ -77,22 +77,13 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
   private val s0_startPc = io.startPc
 
   private val s0_alignBankIdx = getBaseTableAlignBankIndex(s0_startPc)
-  private val s0_setIdx       = getBaseTableSetIndex(s0_startPc)
-  private val s0_setIdxVec =
-    Seq.tabulate(BaseTableNumAlignBanks)(bankIdx => Mux(bankIdx.U < s0_alignBankIdx, s0_setIdx + 1.U, s0_setIdx))
+  private val s0_rawSetIdx    = getBaseTableSetIndex(s0_startPc)
+  private val s0_setIdx =
+    Seq.tabulate(BaseTableNumAlignBanks)(bankIdx => Mux(bankIdx.U < s0_alignBankIdx, s0_rawSetIdx + 1.U, s0_rawSetIdx))
 
   private val s0_bankIdx  = getBaseTableBankIndex(s0_startPc)
   private val s0_bankMask = UIntToOH(s0_bankIdx, NumBanks)
 
-  sramBanks.zip(s0_setIdxVec).foreach {
-    case (alignBank, setIdx) =>
-      alignBank.zipWithIndex.foreach {
-        case (bank, bankIdx) =>
-          bank.io.r.req.valid       := s0_fire && s0_bankMask(bankIdx)
-          bank.io.r.req.bits.setIdx := setIdx
-      }
-  }
-
   /* --------------------------------------------------------------------------------------------------------------
      stage 1
      - get raw ctrs from SRAM
@@ -136,8 +127,8 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
     case (alignBank, alignIdx) =>
       alignBank.zipWithIndex.foreach {
         case (bank, bankIdx) =>
-          bank.io.r.req.valid       := t0_valid && t0_bankMask(bankIdx)
-          bank.io.r.req.bits.setIdx := t0_setIdx(alignIdx)
+          bank.io.r.req.valid       := s0_fire && s0_bankMask(bankIdx) || t0_valid && t0_bankMask(bankIdx)
+          bank.io.r.req.bits.setIdx := Mux(t0_valid, t0_setIdx(alignIdx), s0_setIdx(alignIdx))
       }
   }
```
