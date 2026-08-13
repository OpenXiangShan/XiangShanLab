# Commit Log
- Issue: #5306
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5306
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5306
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5306
- Changed files: 9
- Additions: 183
- Deletions: 124

## Files
- `src/main/scala/top/Configs.scala`
- `src/main/scala/xiangshan/frontend/bpu/Types.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala`

## Diff
```diff
diff --git a/src/main/scala/top/Configs.scala b/src/main/scala/top/Configs.scala
index 9979cf4c15f..e21792d910b 100644
--- a/src/main/scala/top/Configs.scala
+++ b/src/main/scala/top/Configs.scala
@@ -111,10 +111,11 @@ class MinimalConfig(n: Int = 1) extends Config(
             ),
             tageParameters = TageParameters(
               TableInfos = Seq(
-                new TageTableInfo(512, 6),
-                new TageTableInfo(512, 9),
-                new TageTableInfo(512, 17),
-                new TageTableInfo(512, 31)
+                // Size, NumWays, HistoryLength
+                new TageTableInfo(1024, 2, 6),
+                new TageTableInfo(1024, 2, 9),
+                new TageTableInfo(1024, 2, 17),
+                new TageTableInfo(1024, 2, 31)
               ),
             ),
             // FIXME: these are from V2 SC, we don't have equivalent parameters now
diff --git a/src/main/scala/xiangshan/frontend/bpu/Types.scala b/src/main/scala/xiangshan/frontend/bpu/Types.scala
index e95c5322103..898f92cefca 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Types.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Types.scala
@@ -35,21 +35,28 @@ abstract class NamedTuple[T <: Product] {
 }
 
 class TageTableInfo(
-    val NumSets:       Int,
+    val Size:          Int,
+    val NumWays:       Int,
     val HistoryLength: Int
-) extends NamedTuple[(Int, Int)] {
-  require(NumSets > 0, "NumSets must be > 0")
+) extends NamedTuple[(Int, Int, Int)] {
+  require(Size > 0, "Size must be > 0")
+  require(NumWays > 0, "NumWays must be > 0")
   require(HistoryLength >= 0, "HistoryLength must be >= 0")
 
-  def asTuple: (Int, Int) =
-    (NumSets, HistoryLength)
+  def asTuple: (Int, Int, Int) =
+    (Size, NumWays, HistoryLength)
+
+  def getNumSets(numBanks: Int): Int = {
+    require(numBanks > 0, "numBanks must be > 0")
+    Size / NumWays / numBanks
+  }
 
   def getFoldedHistoryInfoSet(numBanks: Int, tagWidth: Int): Set[FoldedHistoryInfo] = {
     require(numBanks > 0, "numBanks must be > 0")
     require(tagWidth > 0, "tagWidth must be > 0")
     if (HistoryLength > 0)
       Set( // FoldedHistoryInfo(unfolded history length, folded history length)
-        new FoldedHistoryInfo(HistoryLength, min(HistoryLength, log2Ceil(NumSets / numBanks))),
+        new FoldedHistoryInfo(HistoryLength, min(HistoryLength, log2Ceil(getNumSets(numBanks)))),
         new FoldedHistoryInfo(HistoryLength, min(HistoryLength, tagWidth)),
         new FoldedHistoryInfo(HistoryLength, min(HistoryLength, tagWidth - 1))
       )
@@ -62,7 +69,7 @@ class TageTableInfo(
     require(tagWidth > 0, "tagWidth must be > 0")
     if (HistoryLength > 0)
       List( // FoldedHistoryInfo(unfolded history length, folded history length)
-        new FoldedHistoryInfo(HistoryLength, min(HistoryLength, log2Ceil(NumSets / numBanks))),
+        new FoldedHistoryInfo(HistoryLength, min(HistoryLength, log2Ceil(getNumSets(numBanks)))),
         new FoldedHistoryInfo(HistoryLength, min(HistoryLength, tagWidth)),
         new FoldedHistoryInfo(HistoryLength, min(HistoryLength, tagWidth - 1))
       )
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
index 3ef65211ea2..3c6c0be4b6c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
@@ -20,6 +20,7 @@ import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.SaturateCounter
+import xiangshan.frontend.bpu.TageTableInfo
 import xiangshan.frontend.bpu.WriteReqBundle
 
 class TageEntry(implicit p: Parameters) extends TageBundle {
@@ -34,19 +35,19 @@ class BaseTableSramWriteReq(implicit p: Parameters) extends TageBundle {
   val takenCtrs: Vec[SaturateCounter] = Vec(FetchBlockAlignInstNum, new SaturateCounter(BaseTableTakenCtrWidth))
 }
 
-class TableReadReq(numSets: Int)(implicit p: Parameters) extends TageBundle {
-  val setIdx:   UInt = UInt(log2Ceil(numSets / NumBanks).W)
+class TableReadReq(implicit p: Parameters, info: TageTableInfo) extends TageBundle {
+  val setIdx:   UInt = UInt(SetIdxWidth.W)
   val bankMask: UInt = UInt(NumBanks.W)
 }
 
-class TableReadResp(implicit p: Parameters) extends TageBundle {
+class TableReadResp(implicit p: Parameters, info: TageTableInfo) extends TageBundle {
   val entries:    Vec[TageEntry]       = Vec(NumWays, new TageEntry)
   val usefulCtrs: Vec[SaturateCounter] = Vec(NumWays, new SaturateCounter(UsefulCtrWidth))
 }
 
-class EntrySramWriteReq(numSets: Int)(implicit p: Parameters) extends WriteReqBundle
+class EntrySramWriteReq(implicit p: Parameters, info: TageTableInfo) extends WriteReqBundle
     with HasTageParameters {
-  val setIdx:         UInt                    = UInt(log2Ceil(numSets / NumBanks).W)
+  val setIdx:         UInt                    = UInt(SetIdxWidth.W)
   val entry:          TageEntry               = new TageEntry
   val usefulCtr:      SaturateCounter         = new SaturateCounter(UsefulCtrWidth)
   override def tag:   Option[UInt]            = Some(entry.tag)
@@ -54,8 +55,8 @@ class EntrySramWriteReq(numSets: Int)(implicit p: Parameters) extends WriteReqBu
   override def taken: Option[Bool]            = Some(entry.takenCtr.isPositive) // FIXME: use actualTaken
 }
 
-class TableWriteReq(numSets: Int)(implicit p: Parameters) extends TageBundle {
-  val setIdx:     UInt                 = UInt(log2Ceil(numSets / NumBanks).W)
+class TableWriteReq(implicit p: Parameters, info: TageTableInfo) extends TageBundle {
+  val setIdx:     UInt                 = UInt(SetIdxWidth.W)
   val bankMask:   UInt                 = UInt(NumBanks.W)
   val wayMask:    UInt                 = UInt(NumWays.W)
   val entries:    Vec[TageEntry]       = Vec(NumWays, new TageEntry)
@@ -68,27 +69,27 @@ class TageMeta(implicit p: Parameters) extends TageBundle {
   val debug_tempTag: Vec[UInt]            = Vec(NumTables, UInt(TagWidth.W)) // TODO: remove it
 }
 
-class TageFoldedHist(numSets: Int)(implicit p: Parameters) extends TageBundle {
-  val forIdx: UInt = UInt(log2Ceil(numSets / NumBanks).W)
+class TageFoldedHist(implicit p: Parameters, info: TageTableInfo) extends TageBundle {
+  val forIdx: UInt = UInt(NumSets.W)
   val forTag: UInt = UInt(TagWidth.W)
 }
 
 class TagMatchResult(implicit p: Parameters) extends TageBundle {
   val hit:          Bool            = Bool()
-  val hitWayMaskOH: UInt            = UInt(NumWays.W)
+  val hitWayMaskOH: UInt            = UInt(MaxNumWays.W)
   val entry:        TageEntry       = new TageEntry
   val usefulCtr:    SaturateCounter = new SaturateCounter(UsefulCtrWidth)
 }
 
 class UpdateInfo(implicit p: Parameters) extends TageBundle {
   val providerTableOH:      UInt            = UInt(NumTables.W)
-  val providerWayOH:        UInt            = UInt(NumWays.W)
+  val providerWayOH:        UInt            = UInt(MaxNumWays.W)
   val providerEntry:        TageEntry       = new TageEntry
   val providerOldUsefulCtr: SaturateCounter = new SaturateCounter(UsefulCtrWidth)
   val providerNewUsefulCtr: SaturateCounter = new SaturateCounter(UsefulCtrWidth)
 
   val altTableOH:      UInt            = UInt(NumTables.W)
-  val altWayOH:        UInt            = UInt(NumWays.W)
+  val altWayOH:        UInt            = UInt(MaxNumWays.W)
   val altEntry:        TageEntry       = new TageEntry
   val altOldUsefulCtr: SaturateCounter = new SaturateCounter(UsefulCtrWidth)
 
@@ -106,16 +107,16 @@ class ConditionalBranchTrace(implicit p: Parameters) extends TageBundle {
   val branchVAddr: PrunedAddr = PrunedAddr(VAddrBits)
 
   val hasProvider:       Bool            = Bool()
-  val providerTableIdx:  UInt            = UInt(log2Ceil(NumTables).W)
+  val providerTableIdx:  UInt            = UInt(TableIdxWidth.W)
   val providerSetIdx:    UInt            = UInt(16.W)
-  val providerWayIdx:    UInt            = UInt(log2Ceil(NumWays).W)
+  val providerWayIdx:    UInt            = UInt(MaxWayIdxWidth.W)
   val providerTakenCtr:  SaturateCounter = new SaturateCounter(TakenCtrWidth)
   val providerUsefulCtr: SaturateCounter = new SaturateCounter(UsefulCtrWidth)
 
   val hasAlt:       Bool            = Bool()
-  val altTableIdx:  UInt            = UInt(log2Ceil(NumTables).W)
+  val altTableIdx:  UInt            = UInt(TableIdxWidth.W)
   val altSetIdx:    UInt            = UInt(16.W)
-  val altWayIdx:    UInt            = UInt(log2Ceil(NumWays).W)
+  val altWayIdx:    UInt            = UInt(MaxWayIdxWidth.W)
   val altTakenCtr:  SaturateCounter = new SaturateCounter(TakenCtrWidth)
   val altUsefulCtr: SaturateCounter = new SaturateCounter(UsefulCtrWidth)
 
@@ -125,7 +126,7 @@ class ConditionalBranchTrace(implicit p: Parameters) extends TageBundle {
   val finalPred:      Bool = Bool()
   val actualTaken:    Bool = Bool()
   val allocSuccess:   Bool = Bool()
-  val allocTableIdx:  UInt = UInt(log2Ceil(NumTables).W)
+  val allocTableIdx:  UInt = UInt(TableIdxWidth.W)
   val allocateSetIdx: UInt = UInt(16.W)
-  val allocWayIdx:    UInt = UInt(log2Ceil(NumWays).W)
+  val allocWayIdx:    UInt = UInt(MaxWayIdxWidth.W)
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala
index 0df06e8191a..4863215a560 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Helpers.scala
@@ -17,47 +17,44 @@ package xiangshan.frontend.bpu.tage
 
 import chisel3._
 import chisel3.util._
-import xiangshan.HasXSParameter
+import utils.AddrField
 import xiangshan.frontend.PrunedAddr
-import xiangshan.frontend.bpu.HalfAlignHelper
+import xiangshan.frontend.bpu.TageTableInfo
 import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 
-trait Helpers extends HasTageParameters with HasXSParameter with HalfAlignHelper {
-  def getBaseTableSetIndex(pc: PrunedAddr): UInt =
-    pc(BaseTableSetIdxWidth - 1 + BankIdxWidth + FetchBlockSizeWidth, BankIdxWidth + FetchBlockSizeWidth)
+trait BaseTableHelper extends HasTageParameters {
+  val addrFields = AddrField(
+    Seq(
+      ("alignOffset", FetchBlockAlignWidth),
+      ("alignBankIdx", FetchBlockSizeWidth - FetchBlockAlignWidth),
+      ("bankIdx", BankIdxWidth),
+      ("setIdx", BaseTableSetIdxWidth)
+    ),
+    maxWidth = Option(VAddrBits)
+  )
 
-  def getBaseTableBankIndex(pc: PrunedAddr): UInt =
-    pc(BankIdxWidth - 1 + FetchBlockSizeWidth, FetchBlockSizeWidth)
+  def getSetIndex(pc: PrunedAddr): UInt =
+    addrFields.extract("setIdx", pc)
 
-  def getBaseTableAlignBankIndex(pc: PrunedAddr): UInt =
-    pc(FetchBlockSizeWidth - 1, FetchBlockAlignWidth)
+  def getBankIndex(pc: PrunedAddr): UInt =
+    addrFields.extract("bankIdx", pc)
+
+  def getAlignBankIndex(pc: PrunedAddr): UInt =
+    addrFields.extract("alignBankIdx", pc)
+}
 
+trait TopHelper extends HasTageParameters {
   def getFoldedHist(allFoldedPathHist: PhrAllFoldedHistories): Vec[TageFoldedHist] =
-    VecInit(TableInfos.map { tableInfo =>
+    VecInit(TableInfos.map { implicit tableInfo =>
       val tageFoldedHist = tableInfo.getTageFoldedHistoryInfo(NumBanks, TagWidth).map { histInfo =>
         allFoldedPathHist.getHistWithInfo(histInfo).foldedHist
       }
-      val foldedHist = Wire(new TageFoldedHist(tableInfo.NumSets))
+      val foldedHist = Wire(new TageFoldedHist)
       foldedHist.forIdx := tageFoldedHist.head
       foldedHist.forTag := tageFoldedHist(1) ^ Cat(tageFoldedHist(2), 0.U(1.W))
       foldedHist
     })
 
-  def getBankIndex(pc: PrunedAddr): UInt =
-    pc(BankIdxWidth - 1 + instOffsetBits, instOffsetBits)
-
-  def getSetIndex(pc: PrunedAddr, hist: UInt, numSets: Int): UInt = {
-    val setIdxWidth = log2Ceil(numSets / NumBanks)
-    val offset      = BankIdxWidth + instOffsetBits
-    pc(setIdxWidth - 1 + offset, offset) ^ hist
-  }
-
-  def getRawTag(pc: PrunedAddr, hist: UInt, numSets: Int): UInt = {
-    val setIdxWidth = log2Ceil(numSets / NumBanks)
-    val offset      = setIdxWidth + BankIdxWidth + instOffsetBits
-    pc(TagWidth - 1 + offset, offset) ^ hist
-  }
-
   def getLongestHistTableOH(hitTableMask: Seq[Bool]): Seq[Bool] =
     PriorityEncoderOH(hitTableMask.reverse).reverse
 
@@ -66,3 +63,27 @@ trait Helpers extends HasTageParameters with HasXSParameter with HalfAlignHelper
     pc(useAltIdxWidth - 1 + instOffsetBits, instOffsetBits)
   }
 }
+
+trait TableHelper extends TopHelper { // extends TopHelper for getBankIndex
+  // varies between different tables
+  implicit val info: TageTableInfo
+
+  val addrFields = AddrField(
+    Seq(
+      ("instOffset", instOffsetBits),
+      ("bankIdx", BankIdxWidth),
+      ("setIdx", SetIdxWidth),
+      ("tag", TagWidth)
+    ),
+    maxWidth = Option(VAddrBits)
+  )
+
+  def getBankIndex(pc: PrunedAddr): UInt =
+    addrFields.extract("bankIdx", pc)
+
+  def getSetIndex(pc: PrunedAddr, hist: UInt): UInt =
+    addrFields.extract("setIdx", pc) ^ hist
+
+  def getRawTag(pc: PrunedAddr, hist: UInt): UInt =
+    addrFields.extract("tag", pc) ^ hist
+}
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Parameters.scala
index 83f453ef96e..ae612e2a3c3 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Parameters.scala
@@ -24,17 +24,16 @@ case class TageParameters(
     BaseTableSize:          Int = 1024 * 8,
     BaseTableTakenCtrWidth: Int = 2,
     TableInfos: Seq[TageTableInfo] = Seq(
-      // TageTableInfo(NumSets, HistoryLength)
-      new TageTableInfo(2048, 4),
-      new TageTableInfo(2048, 9),
-      new TageTableInfo(2048, 17),
-      new TageTableInfo(2048, 29),
-      new TageTableInfo(2048, 56),
-      new TageTableInfo(2048, 109),
-      new TageTableInfo(2048, 211),
-      new TageTableInfo(2048, 397)
+      // TageTableInfo(Size, NumWays, HistoryLength)
+      new TageTableInfo(4096, 2, 4),
+      new TageTableInfo(4096, 2, 9),
+      new TageTableInfo(4096, 2, 17),
+      new TageTableInfo(4096, 2, 29),
+      new TageTableInfo(4096, 2, 56),
+      new TageTableInfo(4096, 2, 109),
+      new TageTableInfo(4096, 2, 211),
+      new TageTableInfo(4096, 2, 397)
     ),
-    NumWays:             Int = 2,
     NumBanks:            Int = 4, // to alleviate read-write conflicts in single-port SRAM
     TagWidth:            Int = 13,
     TakenCtrWidth:       Int = 3,
@@ -50,12 +49,12 @@ case class TageParameters(
 trait HasTageParameters extends HasBpuParameters {
   def tageParameters: TageParameters = bpuParameters.tageParameters
 
-  def BaseTableNumSets:       Int = tageParameters.BaseTableSize / NumBanks / FetchBlockInstNum
+  def BaseTableSize:          Int = tageParameters.BaseTableSize
+  def BaseTableNumSets:       Int = BaseTableSize / NumBanks / FetchBlockInstNum
   def BaseTableSetIdxWidth:   Int = log2Ceil(BaseTableNumSets)
   def BaseTableNumAlignBanks: Int = FetchBlockSize / FetchBlockAlignSize
   def BaseTableTakenCtrWidth: Int = tageParameters.BaseTableTakenCtrWidth
 
-  def NumWays:            Int = tageParameters.NumWays
   def NumBanks:           Int = tageParameters.NumBanks
   def BankIdxWidth:       Int = log2Ceil(NumBanks)
   def TagWidth:           Int = tageParameters.TagWidth
@@ -69,7 +68,18 @@ trait HasTageParameters extends HasBpuParameters {
   def NumUseAltCtrs:       Int = tageParameters.NumUseAltCtrs
 
   def TableInfos: Seq[TageTableInfo] = tageParameters.TableInfos
-  def NumTables:  Int                = TableInfos.length
+
+  def NumTables:     Int = TableInfos.length
+  def TableIdxWidth: Int = log2Ceil(NumTables)
+
+  def MaxNumWays:     Int = TableInfos.map(_.NumWays).max
+  def MaxWayIdxWidth: Int = log2Ceil(MaxNumWays)
+
+  // per table parameters
+  def NumSets(implicit info:     TageTableInfo): Int = info.getNumSets(NumBanks)
+  def SetIdxWidth(implicit info: TageTableInfo): Int = log2Ceil(NumSets)
+  def NumWays(implicit info:     TageTableInfo): Int = info.NumWays
+  def WayIdxWidth(implicit info: TageTableInfo): Int = log2Ceil(NumWays)
 
   def EnableTageTrace: Boolean = tageParameters.EnableTageTrace
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 8532eeea7a4..c1375149722 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -25,13 +25,15 @@ import utility.XSPerfAccumulate
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
 import xiangshan.frontend.bpu.BtbInfo
+import xiangshan.frontend.bpu.HalfAlignHelper
 import xiangshan.frontend.bpu.SaturateCounter
+import xiangshan.frontend.bpu.TageTableInfo
 import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 
 /**
  * This module is the implementation of the TAGE (TAgged GEometric history length predictor).
  */
-class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters with Helpers {
+class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters with TopHelper with HalfAlignHelper {
   class TageIO(implicit p: Parameters) extends BasePredictorIO {
     val foldedPathHist:         PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val foldedPathHistForTrain: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
@@ -45,7 +47,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 
   /* *** submodules *** */
   private val baseTable = Module(new TageBaseTable)
-  private val tables    = TableInfos.zipWithIndex.map { case (info, i) => Module(new TageTable(info.NumSets, i)) }
+  private val tables    = TableInfos.zipWithIndex.map { case (info, i) => Module(new TageTable(i, info)) }
 
   // reset usefulCtr of all entries when usefulResetCtr saturated
   private val usefulResetCtr = RegInit(0.U.asTypeOf(new SaturateCounter(UsefulResetCtrWidth)))
@@ -69,11 +71,12 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val s0_startVAddr = io.startVAddr
 
   private val s0_foldedHist = getFoldedHist(io.foldedPathHist)
-  private val s0_setIdx = VecInit(TableInfos.zip(s0_foldedHist).map { case (tableInfo, hist) =>
-    getSetIndex(s0_startVAddr, hist.forIdx, tableInfo.NumSets)
+  private val s0_setIdx = VecInit((tables zip s0_foldedHist).map { case (table, hist) =>
+    table.getSetIndex(s0_startVAddr, hist.forIdx)
   })
 
-  private val s0_bankIdx  = getBankIndex(s0_startVAddr)
+  // currently all tables share the same bank index
+  private val s0_bankIdx  = tables.head.getBankIndex(s0_startVAddr)
   private val s0_bankMask = UIntToOH(s0_bankIdx, NumBanks)
 
   baseTable.io.readReqValid := s0_fire
@@ -103,8 +106,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     DataHoldBypass(VecInit(tables.map(_.io.predictReadResp.usefulCtrs)), RegNext(s0_fire))
 
   private val s1_foldedHist = RegEnable(s0_foldedHist, s0_fire)
-  private val s1_rawTag = VecInit(s1_foldedHist.zip(TableInfos).map { case (hist, tableInfo) =>
-    getRawTag(s1_startVAddr, hist.forTag, tableInfo.NumSets)
+  private val s1_rawTag = VecInit((tables zip s1_foldedHist).map { case (table, hist) =>
+    table.getRawTag(s1_startVAddr, hist.forTag)
   })
 
   /* --------------------------------------------------------------------------------------------------------------
@@ -196,7 +199,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val t0_startVAddr = io.train.bits.startVAddr
   private val t0_branches   = io.train.bits.branches
 
-  private val t0_bankIdx  = getBankIndex(t0_startVAddr)
+  // currently all tables share the same bank index
+  private val t0_bankIdx  = tables.head.getBankIndex(t0_startVAddr)
   private val t0_bankMask = UIntToOH(t0_bankIdx, NumBanks)
 
   private val t0_condMask = VecInit(t0_branches.map(branch => branch.valid && branch.bits.attribute.isConditional))
@@ -211,8 +215,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val t0_baseTableCtrs = io.train.bits.meta.tage.baseTableCtrs
 
   private val t0_foldedHist = getFoldedHist(io.foldedPathHistForTrain)
-  private val t0_setIdx = VecInit(TableInfos.zip(t0_foldedHist).map { case (tableInfo, hist) =>
-    getSetIndex(t0_startVAddr, hist.forIdx, tableInfo.NumSets)
+  private val t0_setIdx = VecInit((tables zip t0_foldedHist).map { case (table, hist) =>
+    table.getSetIndex(t0_startVAddr, hist.forIdx)
   })
   dontTouch(t0_setIdx)
 
@@ -253,8 +257,8 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val t1_baseTableCtrs = RegEnable(t0_baseTableCtrs, t0_valid)
 
   private val t1_foldedHist = RegEnable(t0_foldedHist, t0_valid)
-  private val t1_rawTag = VecInit(t1_foldedHist.zip(TableInfos).map { case (hist, tableInfo) =>
-    getRawTag(t1_startVAddr, hist.forTag, tableInfo.NumSets)
+  private val t1_rawTag = VecInit((tables zip t1_foldedHist).map { case (table, hist) =>
+    table.getRawTag(t1_startVAddr, hist.forTag)
   })
 
   private val t1_debugTempTag = RegEnable(io.train.bits.meta.tage.debug_tempTag, t0_valid)
@@ -382,32 +386,6 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     }
   }
 
-  private val t2_updateMask       = Wire(Vec(NumTables, Vec(NumWays, Bool())))
-  private val t2_updateEntries    = Wire(Vec(NumTables, Vec(NumWays, new TageEntry)))
-  private val t2_updateUsefulCtrs = Wire(Vec(NumTables, Vec(NumWays, new SaturateCounter(UsefulCtrWidth))))
-  dontTouch(t2_updateEntries)
-  dontTouch(t2_updateUsefulCtrs)
-
-  t2_updateMask.zip(t2_updateEntries).zip(t2_updateUsefulCtrs).zipWithIndex.map {
-    case (((updateEnPerTable, entriesPerTable), usefulCtrsPerTable), tableIdx) =>
-      updateEnPerTable.zip(entriesPerTable).zip(usefulCtrsPerTable).zipWithIndex.map {
-        case (((updateEn, entry), usefulCtr), wayIdx) =>
-          val hitBranchProviderMask = t2_allBranchUpdateInfo.map { branch =>
-            branch.providerTableOH(tableIdx) && branch.providerWayOH(wayIdx)
-          }
-          val hitBranchAltMask = t2_allBranchUpdateInfo.map { branch =>
-            branch.altTableOH(tableIdx) && branch.altWayOH(wayIdx)
-          }
-          val hitBranchProvider  = hitBranchProviderMask.reduce(_ || _)
-          val hitBranchAlt       = hitBranchAltMask.reduce(_ || _)
-          val providerUpdateInfo = Mux1H(hitBranchProviderMask, t2_allBranchUpdateInfo)
-          val altUpdateInfo      = Mux1H(hitBranchAltMask, t2_allBranchUpdateInfo)
-          updateEn  := hitBranchProvider || hitBranchAlt
-          entry     := Mux(hitBranchProvider, providerUpdateInfo.providerEntry, altUpdateInfo.altEntry)
-          usefulCtr := Mux(hitBranchProvider, providerUpdateInfo.providerNewUsefulCtr, altUpdateInfo.altOldUsefulCtr)
-      }
-  }
-
   private val t2_needAllocate         = t2_allBranchUpdateInfo.map(_.needAllocate).reduce(_ || _)
   private val t2_needAllocateBranchOH = PriorityEncoderOH(t2_allBranchUpdateInfo.map(_.needAllocate))
   private val t2_allocateBranch       = Mux1H(t2_needAllocateBranchOH, t2_branches)
@@ -447,7 +425,30 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   )
 
   tables.zipWithIndex.foreach { case (table, tableIdx) =>
-    val thisTableNeedUpdate   = t2_updateMask(tableIdx).reduce(_ || _)
+    implicit val info: TageTableInfo = TableInfos(tableIdx) // used by NumWays
+
+    val updateMask       = Wire(Vec(NumWays, Bool()))
+    val updateEntries    = Wire(Vec(NumWays, new TageEntry))
+    val updateUsefulCtrs = Wire(Vec(NumWays, new SaturateCounter(UsefulCtrWidth)))
+
+    updateMask.zip(updateEntries).zip(updateUsefulCtrs).zipWithIndex.foreach {
+      case (((updateEn, entry), usefulCtr), wayIdx) =>
+        val hitBranchProviderMask = t2_allBranchUpdateInfo.map { branch =>
+          branch.providerTableOH(tableIdx) && branch.providerWayOH(wayIdx)
+        }
+        val hitBranchAltMask = t2_allBranchUpdateInfo.map { branch =>
+          branch.altTableOH(tableIdx) && branch.altWayOH(wayIdx)
+        }
+        val hitBranchProvider  = hitBranchProviderMask.reduce(_ || _)
+        val hitBranchAlt       = hitBranchAltMask.reduce(_ || _)
+        val providerUpdateInfo = Mux1H(hitBranchProviderMask, t2_allBranchUpdateInfo)
+        val altUpdateInfo      = Mux1H(hitBranchAltMask, t2_allBranchUpdateInfo)
+        updateEn  := hitBranchProvider || hitBranchAlt
+        entry     := Mux(hitBranchProvider, providerUpdateInfo.providerEntry, altUpdateInfo.altEntry)
+        usefulCtr := Mux(hitBranchProvider, providerUpdateInfo.providerNewUsefulCtr, altUpdateInfo.altOldUsefulCtr)
+    }
+
+    val thisTableNeedUpdate   = updateMask.reduce(_ || _)
     val thisTableNeedAllocate = t2_allocateTableMaskOH(tableIdx)
     table.io.writeReq.valid         := t2_valid && (thisTableNeedUpdate || thisTableNeedAllocate)
     table.io.writeReq.bits.setIdx   := t2_setIdx(tableIdx)
@@ -458,10 +459,10 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
     val writeWayMask    = Wire(Vec(NumWays, Bool()))
 
     writeEntries.zip(writeUsefulCtrs).zipWithIndex.foreach { case ((entry, usefulCtr), wayIdx) =>
-      val thisWayNeedUpdate   = t2_updateMask(tableIdx)(wayIdx)
+      val thisWayNeedUpdate   = updateMask(wayIdx)
       val thisWayNeedAllocate = thisTableNeedAllocate && t2_allocateWayMaskOH(wayIdx)
-      entry           := Mux(thisWayNeedAllocate, t2_allocateEntry, t2_updateEntries(tableIdx)(wayIdx))
-      usefulCtr.value := Mux(thisWayNeedAllocate, UsefulCtrInitValue.U, t2_updateUsefulCtrs(tableIdx)(wayIdx).value)
+      entry                := Mux(thisWayNeedAllocate, t2_allocateEntry, updateEntries(wayIdx))
+      usefulCtr.value      := Mux(thisWayNeedAllocate, UsefulCtrInitValue.U, updateUsefulCtrs(wayIdx).value)
       writeWayMask(wayIdx) := thisWayNeedUpdate || thisWayNeedAllocate
     }
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
index 8421890f944..b09aab7675d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
@@ -21,9 +21,10 @@ import org.chipsalliance.cde.config.Parameters
 import utils.VecRotate
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.BpuTrain
+import xiangshan.frontend.bpu.HalfAlignHelper
 import xiangshan.frontend.bpu.SaturateCounter
 
-class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
+class TageBaseTable(implicit p: Parameters) extends TageModule with BaseTableHelper with HalfAlignHelper {
   class TageBaseTableIO extends TageBundle {
     val readReqValid: Bool                 = Input(Bool())
     val startVAddr:   PrunedAddr           = Input(PrunedAddr(VAddrBits))
@@ -33,6 +34,12 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
   }
   val io: TageBaseTableIO = IO(new TageBaseTableIO)
 
+  // print params
+  println(f"TageBaseTable:")
+  println(f"  Size(set, bank, cnt): $BaseTableNumSets * $NumBanks * $FetchBlockInstNum = $BaseTableSize")
+  println(f"  Address fields:")
+  addrFields.show(indent = 4)
+
   private val alignBanks = Seq.tabulate(BaseTableNumAlignBanks) { alignIdx =>
     Module(new TageBaseTableAlignBank(alignIdx))
   }
@@ -52,7 +59,7 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
   // i.e. we have VecInit.tabulate(...)'s alignBankIdx = (1, 2, 3, 0),
   // they always needs to goes to physical alignBank (0, 1, 2, 3),
   // so we need to rotate it right by 1.
-  private val s0_rotator = VecRotate(getBaseTableAlignBankIndex(s0_startVAddr))
+  private val s0_rotator = VecRotate(getAlignBankIndex(s0_startVAddr))
   private val s0_startVAddrVec = s0_rotator.rotate(
     VecInit.tabulate(BaseTableNumAlignBanks)(i => getAlignedAddr(s0_startVAddr + (i << FetchBlockAlignWidth).U))
   )
@@ -97,7 +104,7 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
   private val t1_startVAddr = t1_train.startVAddr
   private val t1_branches   = t1_train.branches
   private val t1_oldCtrs    = t1_train.meta.tage.baseTableCtrs
-  private val t1_rotator    = VecRotate(getBaseTableAlignBankIndex(t1_startVAddr))
+  private val t1_rotator    = VecRotate(getAlignBankIndex(t1_startVAddr))
   private val t1_startVAddrVec = t1_rotator.rotate(
     VecInit.tabulate(BaseTableNumAlignBanks)(i => getAlignedAddr(t1_startVAddr + (i << FetchBlockAlignWidth).U))
   )
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala
index b3becc23f89..47663d0d518 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala
@@ -25,7 +25,7 @@ import xiangshan.frontend.bpu.SaturateCounter
 
 class TageBaseTableAlignBank(
     alignIdx: Int
-)(implicit p: Parameters) extends TageModule with Helpers {
+)(implicit p: Parameters) extends TageModule with BaseTableHelper {
   class TageBaseTableAlignBankIO extends Bundle {
     class Read extends Bundle {
       class Req extends Bundle {
@@ -95,9 +95,9 @@ class TageBaseTableAlignBank(
   /* *** read *** */
   private val s0_fire       = r.req.valid
   private val s0_startVAddr = r.req.bits.startVAddr
-  private val s0_bankIdx    = getBaseTableBankIndex(s0_startVAddr)
+  private val s0_bankIdx    = getBankIndex(s0_startVAddr)
   private val s0_bankMask   = UIntToOH(s0_bankIdx, NumBanks)
-  private val s0_setIdx     = getBaseTableSetIndex(s0_startVAddr)
+  private val s0_setIdx     = getSetIndex(s0_startVAddr)
 
   sramBanks.zipWithIndex.foreach { case (bank, i) =>
     bank.io.r.req.valid       := s0_fire && s0_bankMask(i)
@@ -114,7 +114,7 @@ class TageBaseTableAlignBank(
   private val t1_takenCtrs  = w.req.bits.takenCtrs
   private val t1_wayMask    = w.req.bits.wayMask
 
-  private val t1_setIdx   = getBaseTableSetIndex(t1_startVAddr)
+  private val t1_setIdx   = getSetIndex(t1_startVAddr)
   private val t1_bankIdx  = getBankIndex(t1_startVAddr)
   private val t1_bankMask = UIntToOH(t1_bankIdx, NumBanks)
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala b/src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala
index 53e4dfad26e..da53ae380f2 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala
@@ -20,25 +20,36 @@ import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import utility.sram.SRAMTemplate
 import xiangshan.frontend.bpu.SaturateCounter
+import xiangshan.frontend.bpu.TageTableInfo
 import xiangshan.frontend.bpu.WriteBuffer
 
-class TageTable(numSets: Int, tableIdx: Int)(implicit p: Parameters) extends TageModule with Helpers {
+class TageTable(
+    tableIdx:          Int,
+    implicit val info: TageTableInfo // declare info as implicit val to pass it to Bundles / methods like TableReadReq
+)(implicit p: Parameters) extends TageModule with TableHelper {
   class TageTableIO extends TageBundle {
-    val predictReadReq:  Valid[TableReadReq]  = Flipped(Valid(new TableReadReq(numSets)))
-    val trainReadReq:    Valid[TableReadReq]  = Flipped(Valid(new TableReadReq(numSets)))
+    val predictReadReq:  Valid[TableReadReq]  = Flipped(Valid(new TableReadReq))
+    val trainReadReq:    Valid[TableReadReq]  = Flipped(Valid(new TableReadReq))
     val predictReadResp: TableReadResp        = Output(new TableReadResp)
     val trainReadResp:   TableReadResp        = Output(new TableReadResp)
-    val writeReq:        Valid[TableWriteReq] = Flipped(Valid(new TableWriteReq(numSets)))
+    val writeReq:        Valid[TableWriteReq] = Flipped(Valid(new TableWriteReq))
     val resetUseful:     Bool                 = Input(Bool())
     val resetDone:       Bool                 = Output(Bool())
   }
+
   val io: TageTableIO = IO(new TageTableIO)
 
+  println(f"TageTable[$tableIdx]:")
+  println(f"  Size(set, bank, way): $NumSets * $NumBanks * $NumWays = ${info.Size}")
+  println(f"  History length: ${info.HistoryLength}")
+  println(f"  Address fields:")
+  addrFields.show(indent = 4)
+
   private val entrySram =
     Seq.tabulate(NumBanks, NumWays) { (bankIdx, wayIdx) =>
       Module(new SRAMTemplate(
         new TageEntry,
-        set = numSets / NumBanks,
+        set = NumSets,
         way = 1,
         singlePort = true,
         shouldReset = true,
@@ -53,7 +64,7 @@ class TageTable(numSets: Int, tableIdx: Int)(implicit p: Parameters) extends Tag
   private val usefulCtrs = RegInit(
     VecInit.fill(NumBanks)(
       VecInit.fill(NumWays)(
-        VecInit.fill(numSets / NumBanks)(
+        VecInit.fill(NumSets)(
           0.U.asTypeOf(new SaturateCounter(UsefulCtrWidth))
         )
       )
@@ -65,7 +76,7 @@ class TageTable(numSets: Int, tableIdx: Int)(implicit p: Parameters) extends Tag
   private val entryWriteBuffers =
     Seq.tabulate(NumBanks) { bankIdx =>
       Module(new WriteBuffer(
-        new EntrySramWriteReq(numSets),
+        new EntrySramWriteReq,
         WriteBufferSize,
         numPorts = NumWays,
         hasCnt = false, // FIXME: set to true when bug fixed
```
