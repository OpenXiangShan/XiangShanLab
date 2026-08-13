# Commit Log
- Issue: #5648
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5648
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5648
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5648
- Changed files: 3
- Additions: 110
- Deletions: 65

## Files
- `src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala
index aef591760a8..9652dfe46b6 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala
@@ -78,17 +78,22 @@ trait Helpers extends HasScParameters with PhrHelper {
       "Length of writeValidVec, takenMask and wayIdxVec should be the same"
     )
     val newEntries = Wire(Vec(oldEntries.length, new ScEntry()))
-    oldEntries.zip(newEntries).zipWithIndex.foreach { case ((oldEntry, newEntry), wayIdx) =>
-      val newCtr = writeValidVec.zip(takenMask).zip(wayIdxVec).zip(branchIdxVec).foldLeft(oldEntry.ctr) {
-        case (prevCtr, (((writeValid, writeTaken), writeWayIdx), branchIdx)) =>
-          val needUpdate = writeValid && writeWayIdx === wayIdx.U && metaData.tagePredValid(branchIdx) &&
-            (metaData.scPred(branchIdx) =/= writeTaken || !metaData.sumAboveThres(branchIdx))
-          prevCtr.getUpdate(
-            writeTaken,
-            needUpdate
-          )
-      }
-      newEntry.ctr := WireInit(newCtr)
+    // For each reslove branch, record its update direction, whether it has been updated, and which way it has been updated to
+    val writeNeedMask = VecInit(Seq.fill(writeValidVec.length)(VecInit(Seq.fill(oldEntries.length)(false.B))))
+    val writeDirMask  = VecInit(Seq.fill(writeValidVec.length)(VecInit(Seq.fill(oldEntries.length)(false.B))))
+    writeValidVec.zip(takenMask).zip(wayIdxVec).zip(branchIdxVec).zipWithIndex.foreach {
+      case ((((valid, taken), writeIdx), oldIdx), i) =>
+        val needUpdate = valid && metaData.tagePredValid(oldIdx) &&
+          (metaData.scPred(oldIdx) =/= taken || !metaData.sumAboveThres(oldIdx))
+        writeNeedMask(i)(writeIdx) := needUpdate
+        writeDirMask(i)(writeIdx)  := taken
+    }
+    oldEntries.zip(newEntries).zipWithIndex.foreach { case ((oldEntry, newEntry), i) =>
+      val writeHit = writeNeedMask.map(_(i))
+      val writeDir = writeDirMask.map(_(i))
+      val inc      = PopCount(writeHit.zip(writeDir).map { case (hit, dir) => hit && dir })
+      val dec      = PopCount(writeHit.zip(writeDir).map { case (hit, dir) => hit && !dir })
+      newEntry.ctr := Mux(inc >= dec, oldEntry.ctr.getIncrease(inc - dec), oldEntry.ctr.getDecrease(dec - inc))
     }
     newEntries
   }
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala
index 92a3f50441a..12b67e68402 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala
@@ -16,6 +16,7 @@
 package xiangshan.frontend.bpu.sc
 
 import chisel3.util._
+import scala.math.min
 import xiangshan.frontend.bpu.HasBpuParameters
 import xiangshan.frontend.bpu.ScTableInfo
 
@@ -33,20 +34,16 @@ case class ScParameters(
       new ScTableInfo(128, 8)
     ),
     BiasTableSize:       Int = 128,
-    BiasUseTageBitWidth: Int = 2, // use tage_taken as index bits
+    BiasUseTageBitWidth: Int = 2,    // use tage_taken as index bits
     PathEnable:          Boolean = true,
     GlobalEnable:        Boolean = false,
     BWEnable:            Boolean = false,
     BiasEnable:          Boolean = true,
     CtrWidth:            Int = 6,
     ThresholdWidth:      Int = 13,
-    ThresholdInit:       Int = 1130,
-    MinThreshold:        Int = 768,
-    MaxThreshold:        Int = 7100,
-    NumTables:           Int = 2,
+    ThresholdInit:       Int = 1130, // magic number,greater than min and less than max
     NumBanks:            Int = 2,
     WriteBufferSize:     Int = 4,
-    TagWidth:            Int = 12,
     EnableScTrace:       Boolean = false
 ) {}
 
@@ -64,8 +61,6 @@ trait HasScParameters extends HasBpuParameters {
   def NumBanks:          Int = scParameters.NumBanks
   def BankWidth:         Int = log2Ceil(NumBanks)
   def ThresholdWidth:    Int = scParameters.ThresholdWidth
-  def MinThreshold:      Int = scParameters.MinThreshold
-  def MaxThreshold:      Int = scParameters.MaxThreshold
 
   def PathTableInfos: Seq[ScTableInfo] = scParameters.PathTableInfos
   def NumPathTables:  Int              = PathTableInfos.length
@@ -81,6 +76,13 @@ trait HasScParameters extends HasBpuParameters {
   def BackwardTableInfos: Seq[ScTableInfo] = scParameters.BackwardTableInfos
   def NumBWTables:        Int              = BackwardTableInfos.length
 
+  // If tage LowConf, the totoalSum should be at least NumTables + 4, Threshold should be (NumTables + 4) << 6(threshold >> 3 + lowConf threshold >> 3)
+  // The value of ctr saturation is 63.
+  // If all ctrs are saturated, the corresponding Threshold should be (NumTables * 63) << 4(threshold >> 3 + highConf threshold >> 1)
+  def NumTables:    Int = NumPathTables + NumGlobalTables + NumBiasTable + NumBWTables
+  def MinThreshold: Int = (NumTables + 4) << 6
+  def MaxThreshold: Int = min((NumTables * 63) << 4, (1 << ThresholdWidth) - 1)
+
   def WriteBufferSize: Int = scParameters.WriteBufferSize
   def TotalSumWidth: Int = CtrWidth + 1 + log2Ceil(NumPathTables + NumGlobalTables + NumBiasTable) // +1 for counter * 2
   def EnableScTrace: Boolean = scParameters.EnableScTrace
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
index 713944884d3..3b8da7794df 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
@@ -332,6 +332,8 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
       conf            := false.B
       s2_useScPred(i) := false.B
     }
+    // If the sum is greater than threshold/2, then the current threshold can already use the sc result under tage high Confidence.
+    // And if scWrang does not occur at this time, there is no need to update ctr/threshold again
     s2_sumAboveThres(i) := aboveThreshold(sum, thres >> 1)
     dontTouch(tageConfHigh)
     dontTouch(tageConfMid)
@@ -442,28 +444,39 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
     s"t1_branchesWayIdxVec entry width: ${t1_branchesWayIdxVec(0).getWidth} " +
       s"should be the same as log2Ceil(NumWays): ${log2Ceil(NumWays)}"
   )
+
+  /************ get new threshold************/
   private val t1_thresholdOverflowVec  = WireInit(VecInit.fill(NumWays)(false.B))
   private val t1_thresholdUnderflowVec = WireInit(VecInit.fill(NumWays)(false.B))
-
-  private val t1_writeThresVec = VecInit(scThreshold.indices.map { wayIdx =>
-    val updated =
-      t1_writeValidVec.zip(t1_branchesWayIdxVec).zip(t1_writeTakenVec).zip(t1_branchesScIdxVec).foldLeft(scThreshold(
-        wayIdx
-      )) {
-        case (prevThres, (((writeValid, writeWayIdx), taken), branchIdx)) =>
-          val scWrong = taken =/= t1_meta.scPred(branchIdx)
-          val shouldUpdate = writeValid && writeWayIdx === wayIdx.U && t1_meta.tagePredValid(branchIdx) &&
-            (scWrong || !t1_meta.sumAboveThres(branchIdx))
-          prevThres.getUpdate(scWrong, en = shouldUpdate)
-      }
-    t1_thresholdOverflowVec(wayIdx)  := updated.value > MaxThreshold.U
-    t1_thresholdUnderflowVec(wayIdx) := updated.value < MinThreshold.U
-    WireInit(Mux(
+  private val t1_writeThresVec         = VecInit.tabulate(NumWays)(_ => ThresholdCounter.Init)
+
+  // For each reslove branch, record its update direction, whether it has been updated, and which way it has been updated to
+  private val thresholdWayMask =
+    VecInit(Seq.fill(ResolveEntryBranchNumber)(VecInit(Seq.fill(NumWays)(false.B))))
+  private val thresholdDirMask =
+    VecInit(Seq.fill(ResolveEntryBranchNumber)(VecInit(Seq.fill(NumWays)(false.B))))
+  t1_writeValidVec.zip(t1_writeTakenVec).zip(t1_branchesWayIdxVec).zip(t1_branchesScIdxVec).zipWithIndex.foreach {
+    case ((((valid, taken), writeIdx), oldIdx), i) =>
+      val scWrong = taken =/= t1_meta.scPred(oldIdx)
+      val needUpdate = valid && t1_meta.tagePredValid(oldIdx) &&
+        (scWrong || !t1_meta.sumAboveThres(oldIdx))
+      thresholdWayMask(i)(writeIdx) := needUpdate
+      thresholdDirMask(i)(writeIdx) := scWrong
+  }
+  scThreshold.zip(t1_writeThresVec).zipWithIndex.foreach { case ((oldEntry, newEntry), i) =>
+    val writeHit = thresholdWayMask.map(_(i))
+    val writeDir = thresholdDirMask.map(_(i))
+    val inc      = PopCount(writeHit.zip(writeDir).map { case (hit, dir) => hit && dir })
+    val dec      = PopCount(writeHit.zip(writeDir).map { case (hit, dir) => hit && !dir })
+    val updated  = Mux(inc >= dec, oldEntry.getIncrease(inc - dec), oldEntry.getDecrease(dec - inc))
+    t1_thresholdOverflowVec(i)  := updated.value > MaxThreshold.U
+    t1_thresholdUnderflowVec(i) := updated.value < MinThreshold.U
+    newEntry := Mux(
       updated.value >= MinThreshold.U && updated.value <= MaxThreshold.U,
       updated,
-      scThreshold(wayIdx)
-    ))
-  })
+      scThreshold(i)
+    )
+  }
   dontTouch(t1_writeThresVec)
 
   // calculate new path table entries
@@ -481,6 +494,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
         t1_meta
       )
   }
+  dontTouch(t1_writePathEntryVec)
 
   private val t1_writePathWayMaskVec =
     t1_oldPathEntries.zip(t1_writePathEntryVec).map { case (oldEntries, newEntries) =>
@@ -539,35 +553,59 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
       }
   }
 
-  t1_oldBiasEntries.zip(t1_writeBiasEntryVec).zipWithIndex.foreach { case ((oldEntry, newEntry), wayIdx) =>
-    val newCtr = t1_writeValidVec.zip(t1_writeTakenVec).zip(t1_branchesWayIdxVec).zip(
-      t1_branchesScIdxVec
-    ).foldLeft(oldEntry.ctr) {
-      case (prevCtr, (((writeValid, writeTaken), writeWayIdx), branchIdx)) =>
-        val biasWayIdx = Cat(writeWayIdx, t1_oldBiasLowBits(branchIdx))
-        val needUpdate = writeValid && biasWayIdx === wayIdx.U && t1_meta.tagePredValid(branchIdx) &&
-          (t1_meta.scPred(branchIdx) =/= writeTaken || !t1_meta.sumAboveThres(branchIdx))
-        prevCtr.getUpdate(
-          writeTaken,
-          needUpdate
-        )
-    }
-    newEntry.ctr := WireInit(newCtr)
+  // For each reslove branch, record its update direction, whether it has been updated, and which way it has been updated to
+  private val writeBiasWayMask =
+    VecInit(Seq.fill(t1_writeValidVec.length)(VecInit(Seq.fill(t1_oldBiasEntries.length)(false.B))))
+  private val writeBiasDirMask =
+    VecInit(Seq.fill(t1_writeValidVec.length)(VecInit(Seq.fill(t1_oldBiasEntries.length)(false.B))))
+  t1_writeValidVec.zip(t1_writeTakenVec).zip(t1_branchesWayIdxVec).zip(t1_branchesScIdxVec).zipWithIndex.foreach {
+    case ((((valid, taken), writeIdx), oldIdx), i) =>
+      val biasWayIdx = Cat(writeIdx, t1_oldBiasLowBits(oldIdx))
+      val needUpdate = valid && t1_meta.tagePredValid(oldIdx) &&
+        (t1_meta.scPred(oldIdx) =/= taken || !t1_meta.sumAboveThres(oldIdx))
+      writeBiasWayMask(i)(biasWayIdx) := needUpdate
+      writeBiasDirMask(i)(biasWayIdx) := taken
   }
+  t1_oldBiasEntries.zip(t1_writeBiasEntryVec).zipWithIndex.foreach { case ((oldEntry, newEntry), i) =>
+    val writeHit = writeBiasWayMask.map(_(i))
+    val writeDir = writeBiasDirMask.map(_(i))
+    val inc      = PopCount(writeHit.zip(writeDir).map { case (hit, dir) => hit && dir })
+    val dec      = PopCount(writeHit.zip(writeDir).map { case (hit, dir) => hit && !dir })
+    newEntry.ctr := Mux(inc >= dec, oldEntry.ctr.getIncrease(inc - dec), oldEntry.ctr.getDecrease(dec - inc))
+  }
+
+  dontTouch(t1_writeBiasEntryVec)
+
+  /*
+   *  train pipeline stage 2
+   */
+  private val t2_writeValid                 = RegEnable(t1_writeValid, false.B, t1_fire)
+  private val t2_bankMask                   = RegEnable(t1_bankMask, t1_fire)
+  private val t2_pathSetIdx                 = RegEnable(VecInit(t1_pathSetIdx), t1_fire)
+  private val t2_globalSetIdx               = RegEnable(VecInit(t1_globalSetIdx), t1_fire)
+  private val t2_biasSetIdx                 = RegEnable(t1_biasSetIdx, t1_fire)
+  private val t2_commonMeta                 = RegEnable(t1_meta.scCommonHR, t1_fire)
+  private val t2_writePathWayMaskVec        = RegEnable(VecInit(t1_writePathWayMaskVec), t1_fire)
+  private val t2_writePathEntryVec          = RegEnable(t1_writePathEntryVec, t1_fire)
+  private val t2_writeGlobalEntryWayMaskVec = RegEnable(VecInit(t1_writeGlobalEntryWayMaskVec), t1_fire)
+  private val t2_writeGlobalEntryVec        = RegEnable(t1_writeGlobalEntryVec, t1_fire)
+  private val t2_writeBiasWayMask           = RegEnable(t1_writeBiasWayMask, t1_fire)
+  private val t2_writeBiasEntryVec          = RegEnable(t1_writeBiasEntryVec, t1_fire)
+  private val t2_writeThresVec              = RegEnable(t1_writeThresVec, t1_fire)
 
   // new entries write back to tables
-  pathTable.zip(t1_pathSetIdx).zip(t1_writePathEntryVec).zip(t1_writePathWayMaskVec).foreach {
+  pathTable.zip(t2_pathSetIdx).zip(t2_writePathEntryVec).zip(t2_writePathWayMaskVec).foreach {
     case (((table, idx), writeEntries), writeWayMask) =>
-      table.io.update.valid    := t1_writeValid && PathEnable.B
+      table.io.update.valid    := t2_writeValid && PathEnable.B
       table.io.update.setIdx   := idx
-      table.io.update.bankMask := t1_bankMask
+      table.io.update.bankMask := t2_bankMask
       table.io.update.wayMask  := writeWayMask
       table.io.update.entryVec := writeEntries
   }
 
-  globalTable.zip(t1_globalSetIdx).zip(t1_writeGlobalEntryVec).zip(t1_writeGlobalEntryWayMaskVec).foreach {
+  globalTable.zip(t2_globalSetIdx).zip(t2_writeGlobalEntryVec).zip(t2_writeGlobalEntryWayMaskVec).foreach {
     case (((table, idx), writeEntries), writeWayMask) =>
-      table.io.update.valid    := t1_writeValid && t1_meta.scCommonHR.valid && GlobalEnable.B
+      table.io.update.valid    := t2_writeValid && t2_commonMeta.valid && GlobalEnable.B
       table.io.update.setIdx   := idx
       table.io.update.bankMask := t1_bankMask
       table.io.update.wayMask  := writeWayMask
@@ -576,21 +614,21 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
 
   bwTable.zip(t1_bwSetIdx).zip(t1_writeBWEntryVec).zip(t1_writeBWEntryWayMaskVec).foreach {
     case (((table, idx), writeEntries), writeWayMask) =>
-      table.io.update.valid    := t1_writeValid && t1_meta.scCommonHR.valid && BWEnable.B
+      table.io.update.valid    := t2_writeValid && t2_commonMeta.valid && BWEnable.B
       table.io.update.setIdx   := idx
-      table.io.update.bankMask := t1_bankMask
+      table.io.update.bankMask := t2_bankMask
       table.io.update.wayMask  := writeWayMask
       table.io.update.entryVec := writeEntries
   }
 
-  biasTable.io.update.valid    := t1_writeValid && BiasEnable.B
-  biasTable.io.update.setIdx   := t1_biasSetIdx
-  biasTable.io.update.bankMask := t1_bankMask
-  biasTable.io.update.wayMask  := t1_writeBiasWayMask
-  biasTable.io.update.entryVec := t1_writeBiasEntryVec
+  biasTable.io.update.valid    := t2_writeValid && BiasEnable.B
+  biasTable.io.update.setIdx   := t2_biasSetIdx
+  biasTable.io.update.bankMask := t2_bankMask
+  biasTable.io.update.wayMask  := t2_writeBiasWayMask
+  biasTable.io.update.entryVec := t2_writeBiasEntryVec
 
-  when(t1_writeValid) {
-    scThreshold := t1_writeThresVec
+  when(t2_writeValid) {
+    scThreshold := t2_writeThresVec
   }
 
   private val scCorrectVec   = WireInit(VecInit.fill(NumWays)(false.B))
```
