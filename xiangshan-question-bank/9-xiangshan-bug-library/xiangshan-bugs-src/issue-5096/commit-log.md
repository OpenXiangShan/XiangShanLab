# Commit Log
- Issue: #5096
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5096
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5096
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5096
- Changed files: 1
- Additions: 19
- Deletions: 16

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
index 6dd8bdefb8f..838f26558cf 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
@@ -161,20 +161,23 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   private val s2_tag              = RegEnable(s1_tag, s1_fire)
   private val s2_posHigherBits    = RegEnable(s1_posHigherBits, s1_fire)
   private val s2_crossPageMask    = RegEnable(s1_crossPageMask, s1_fire)
+  private val s2_alignBankIdx     = RegEnable(s1_alignBankIdx, s1_fire)
   private val s2_positions = s2_posHigherBits.zip(s2_rawBtbEntries).map { case (h, entry) =>
     Cat(h, entry.position) // Add higher bits before using
   }
   private val s2_rawHitMask = s2_rawBtbEntries.map(entry => entry.valid && entry.tag === s2_tag)
-  private val s2_hitMask = s2_rawHitMask.zip(s2_positions).zip(s2_crossPageMask).map {
-    case ((hit, position), isCrossPage) =>
-      hit && position > s2_startVAddr(FetchBlockSizeWidth - 1, 1) && !isCrossPage
+  private val s2_hitMask = s2_rawHitMask.zip(s2_rawBtbEntries).zip(s2_crossPageMask).zipWithIndex.map {
+    case (((hit, entry), isCrossPage), i) =>
+      hit && !isCrossPage && (
+        (i / NumWay).U =/= s2_alignBankIdx ||
+          entry.position > getAlignedInstOffset(s2_startVAddr)
+      )
   }
   private val s2_targets =
     s2_rawBtbEntries.map(e =>
       getFullTarget(s2_startVAddr, e.targetLowerBits, Some(e.targetCarry))
     ) // FIXME: parameterize target carry
 
-  private val s2_alignBankIdx       = getAlignBankIndex(s2_startVAddr)
   private val s2_thisReplacerSetIdx = getReplacerSetIndex(s2_startVAddr)
   private val s2_nextReplacerSetIdx = s2_thisReplacerSetIdx + 2.U
   private val s2_replacerSetIdxVec: Vec[UInt] = VecInit.tabulate(NumAlignBanks)(bankIdx =>
@@ -212,7 +215,7 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   io.result.targets    := s2_targets
   io.result.attributes := s2_rawBtbEntries.map(_.attribute)
 
-  io.meta.hitMask            := s2_hitMask
+  io.meta.hitMask            := s2_rawHitMask
   io.meta.positions          := s2_positions
   io.meta.stronglyBiasedMask := DontCare // FIXME: add bias logic
   io.meta.attributes         := s2_rawBtbEntries.map(_.attribute)
@@ -234,14 +237,14 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   private val t1_setIdxVec =
     VecInit.tabulate(NumAlignBanks)(bankIdx => Mux(bankIdx.U < t1_alignBankIdx, t1_nextSetIdx, t1_thisSetIdx))
 
-  private val t1_mispredictBranch = io.train.bits.mispredictBranch
+  private val t1_mispredictBranch = t1_train.mispredictBranch
 
   private val t1_hitMispredictBranch = t1_meta.hitMask.zip(t1_meta.positions).zip(t1_meta.attributes).map {
     case ((hit, position), attribute) =>
       hit && position === t1_mispredictBranch.bits.cfiPosition && attribute === t1_mispredictBranch.bits.attribute
-  }.reduce(_ || _) && t1_mispredictBranch.valid
+  }.reduce(_ || _)
 
-  private val t1_writeValid = t1_valid && !t1_hitMispredictBranch
+  private val t1_writeValid = t1_valid && t1_mispredictBranch.valid && !t1_hitMispredictBranch
 
   private val t1_writeEntry = Wire(new MainBtbEntry)
   t1_writeEntry.valid           := true.B   // FIXME: invalidate
@@ -256,7 +259,7 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   private val t1_writeAlignBankIdx =
     t1_mispredictBranch.bits.cfiPosition(CfiPositionWidth - 1, CfiPositionWidth - log2Ceil(NumAlignBanks))
   private val t1_rawWriteAlignBankMask = VecInit(UIntToOH(t1_writeAlignBankIdx).asBools)
-  private val t1_writeAlignBankMask    = vecRotateRight(t1_rawWriteAlignBankMask, s1_alignBankIdx)
+  private val t1_writeAlignBankMask    = vecRotateRight(t1_rawWriteAlignBankMask, t1_alignBankIdx)
 
   private val t1_thisReplacerSetIdx = getReplacerSetIndex(t1_train.startVAddr)
   private val t1_nextReplacerSetIdx = t1_thisReplacerSetIdx + 2.U
@@ -307,11 +310,11 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   /* ** statistics ** */
 
   XSPerfAccumulate("total_train", t1_valid)
-  XSPerfAccumulate("mbtb_pred_has_hit", s2_fire && s2_hitMask.reduce(_ || _))
-  XSPerfHistogram("mbtb_pred_hit_count", PopCount(s2_hitMask), s2_fire, 0, NumWay * NumAlignBanks)
-  XSPerfAccumulate("mbtb_update_new_entry", t1_writeValid)
-  XSPerfAccumulate("mbtb_update_has_mispredict", t1_valid && t1_mispredictBranch.valid)
-  XSPerfAccumulate("mbtb_update_hit_mispredict", t1_valid && t1_hitMispredictBranch)
-  XSPerfAccumulate("mbtb_multihit_write_conflict", multiWriteConflict)
-  XSPerfHistogram("mbtb_multihit_count", PopCount(s2_multiHitMask), s2_fire, 0, NumWay * NumAlignBanks)
+  XSPerfAccumulate("pred_hit", s2_fire && s2_hitMask.reduce(_ || _))
+  XSPerfHistogram("pred_hit_count", PopCount(s2_hitMask), s2_fire, 0, NumWay * NumAlignBanks)
+  XSPerfAccumulate("train_write_new_entry", t1_writeValid)
+  XSPerfAccumulate("train_has_mispredict", t1_valid && t1_mispredictBranch.valid)
+  XSPerfAccumulate("train_hit_mispredict", t1_valid && t1_mispredictBranch.valid && t1_hitMispredictBranch)
+  XSPerfAccumulate("multihit_write_conflict", multiWriteConflict)
+  XSPerfHistogram("multihit_count", PopCount(s2_multiHitMask), s2_fire, 0, NumWay * NumAlignBanks)
 }
```
