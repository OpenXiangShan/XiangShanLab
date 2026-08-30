# Commit Log
- Issue: #5756
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5756
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5756
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5756
- Changed files: 10
- Additions: 174
- Deletions: 134

## Files
- `src/main/scala/xiangshan/frontend/bpu/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/Types.scala`
- `src/main/scala/xiangshan/frontend/bpu/history/commonhr/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala`
- `src/main/scala/xiangshan/frontend/bpu/history/commonhr/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala`
- `src/main/scala/xiangshan/frontend/bpu/sc/ScTable.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/Parameters.scala
index c417269d8ff..639ab6adb6a 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Parameters.scala
@@ -67,9 +67,10 @@ trait HasBpuParameters extends HasFrontendParameters {
 
   def NumBtbResultEntries: Int = bpuParameters.mbtbParameters.NumWay * bpuParameters.mbtbParameters.NumAlignBanks
 
-  def GhrShamt:         Int = NumBtbResultEntries
-  def GhrHistoryLength: Int = bpuParameters.scParameters.GlobalTableInfos.map(_.HistoryLength).max
-  def BWHistoryLength:  Int = bpuParameters.scParameters.BackwardTableInfos.map(_.HistoryLength).max
+  def GhrShamt:          Int = NumBtbResultEntries
+  def GhrHistoryLength:  Int = bpuParameters.scParameters.GlobalTableInfos.map(_.HistoryLength).max
+  def BWHistoryLength:   Int = bpuParameters.scParameters.BackwardTableInfos.map(_.HistoryLength).max
+  def ImliHistoryLength: Int = bpuParameters.scParameters.ImliTableInfo.HistoryLength
 
   // phr history
   def AllFoldedHistoryInfo: Set[FoldedHistoryInfo] =
@@ -80,7 +81,7 @@ trait HasBpuParameters extends HasFrontendParameters {
         _.getFoldedHistoryInfoSet(bpuParameters.ittageParameters.TagWidth, bpuParameters.ittageParameters.NumBanks)
       }.reduce(_ ++ _) ++
       bpuParameters.scParameters.PathTableInfos.map {
-        _.getFoldedHistoryInfoSet(NumBtbResultEntries, bpuParameters.scParameters.NumBanks)
+        _.getFoldedHistoryInfoSet()
       }.reduce(_ ++ _) ++
       bpuParameters.utageParameters.TableInfos.map {
         _.getFoldedHistoryInfoSet()
diff --git a/src/main/scala/xiangshan/frontend/bpu/Types.scala b/src/main/scala/xiangshan/frontend/bpu/Types.scala
index 182a3af7b10..55823388a89 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Types.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Types.scala
@@ -130,24 +130,22 @@ class IttageTableInfo(
 }
 
 class ScTableInfo(
-    val Size:          Int,
+    val NumSets:       Int,
     val HistoryLength: Int
 ) extends NamedTuple[(Int, Int)] {
-  require(Size > 0, "Size must be > 0")
+  require(NumSets > 0, "NumSets must be > 0")
   require(HistoryLength >= 0, "HistoryLength must be >= 0")
 
   def asTuple: (Int, Int) =
-    (Size, HistoryLength)
+    (NumSets, HistoryLength)
 
-  def getFoldedHistoryInfoSet(numWays: Int, numBanks: Int): Set[FoldedHistoryInfo] = {
-    require(numBanks > 0, "numBanks must be > 0")
+  def getFoldedHistoryInfoSet(): Set[FoldedHistoryInfo] =
     if (HistoryLength > 0)
       Set(
-        new FoldedHistoryInfo(HistoryLength, min(HistoryLength, log2Ceil(Size)))
+        new FoldedHistoryInfo(HistoryLength, min(HistoryLength, log2Ceil(NumSets)))
       )
     else
       Set[FoldedHistoryInfo]()
-  }
 }
 
 class FoldedHistoryInfo(
diff --git a/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Bundles.scala
index 10a2f2ae0ac..2503b6743fb 100644
--- a/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Bundles.scala
@@ -46,12 +46,12 @@ class CommonHRResolveMeta(implicit p: Parameters) extends CommonHRBundle {
   val ghr:   UInt = UInt(GhrHistoryLength.W)
   val bw:    UInt = UInt(BWHistoryLength.W)
 
-  val imli: UInt = UInt(ImliWidth.W)
+  val imli: UInt = UInt(ImliHistoryLength.W)
 }
 class CommonHRMeta(implicit p: Parameters) extends CommonHRBundle {
   val ghr:       UInt                 = UInt(GhrHistoryLength.W)
   val bw:        UInt                 = UInt(BWHistoryLength.W)
-  val imli:      UInt                 = UInt(ImliWidth.W)
+  val imli:      UInt                 = UInt(ImliHistoryLength.W)
   val hitMask:   Vec[Bool]            = Vec(NumBtbResultEntries, Bool())
   val attribute: Vec[BranchAttribute] = Vec(NumBtbResultEntries, new BranchAttribute)
   val position:  Vec[UInt]            = Vec(NumBtbResultEntries, UInt(CfiPositionWidth.W))
diff --git a/src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala b/src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala
index a22e3c42a89..f57858459b0 100644
--- a/src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala
@@ -32,7 +32,7 @@ class CommonHR(implicit p: Parameters) extends CommonHRModule with Helpers with
     val s1_imliTaken:  Bool                = Input(Bool())
     val update:        CommonHRUpdate      = Input(new CommonHRUpdate)
     val redirect:      CommonHRRedirect    = Input(new CommonHRRedirect)
-    val s0_imli:       UInt                = Output(UInt(ImliWidth.W))
+    val s0_imli:       UInt                = Output(UInt(ImliHistoryLength.W))
     val s0_commonHR:   CommonHREntry       = Output(new CommonHREntry)
     val s3ResolveMeta: CommonHRResolveMeta = Output(new CommonHRResolveMeta)
 
@@ -49,15 +49,15 @@ class CommonHR(implicit p: Parameters) extends CommonHRModule with Helpers with
   private val s3_override = io.update.s3Override
 
   // common history register
-  private val s0_imli                = WireInit(0.U(ImliWidth.W))
+  private val s0_imli                = WireInit(0.U(ImliHistoryLength.W))
   private val s1_imli                = RegEnable(s0_imli, s0_fire)
   private val s2_imli                = RegEnable(s1_imli, s1_fire)
   private val s3_imli                = RegEnable(s2_imli, s2_fire)
-  private val imli                   = RegInit(0.U(ImliWidth.W))
+  private val imli                   = RegInit(0.U(ImliHistoryLength.W))
   private val s0_commonHR            = WireInit(0.U.asTypeOf(new CommonHREntry))
-  private val s1_commonHR            = RegEnable(s0_commonHR, s0_fire)
-  private val s2_commonHR            = RegEnable(s1_commonHR, s1_fire)
-  private val s3_commonHR            = RegEnable(s2_commonHR, s2_fire)
+  private val s1_commonHR            = RegEnable(s0_commonHR, 0.U.asTypeOf(new CommonHREntry), s0_fire)
+  private val s2_commonHR            = RegEnable(s1_commonHR, 0.U.asTypeOf(new CommonHREntry), s1_fire)
+  private val s3_commonHR            = RegEnable(s2_commonHR, 0.U.asTypeOf(new CommonHREntry), s2_fire)
   private val commonHR               = RegInit(0.U.asTypeOf(new CommonHREntry))
   private val s3_commonHRResolveMeta = WireInit(0.U.asTypeOf(new CommonHRResolveMeta))
 
@@ -187,10 +187,11 @@ class CommonHR(implicit p: Parameters) extends CommonHRModule with Helpers with
   initCommonHR.predStartPc.get := io.s0_startPc.get
 
   when(r0_valid) {
-    enqPtr                    := writePtr + 1.U
-    recoverPtr                := writePtr
-    predPtr                   := writePtr
-    histQueue(writePtr.value) := r0_commonHR // The queue value during redirect is used for diff
+    enqPtr                            := writePtr + 1.U
+    recoverPtr                        := writePtr - 1.U
+    predPtr                           := writePtr - 1.U
+    histQueue(writePtr.value)         := initCommonHR // The queue value during redirect is used for diff
+    histQueue((writePtr - 1.U).value) := r0_commonHR  // The queue value during redirect is used for diff
   }.elsewhen(s3_override) {
     val realRecoverPtr = Mux(hasOverrideHist, recoverPtr + 1.U, recoverPtr)
     histQueue(writePtr.value)         := s3_newCommonHR // update s3_fire block
@@ -212,7 +213,13 @@ class CommonHR(implicit p: Parameters) extends CommonHRModule with Helpers with
     }
   }
 
-  XSError(enqEnable && (writePtr < predPtr || predPtr < recoverPtr), "The predPtr exceeds the correct range")
+  // Use distance-based checks for circular pointers to avoid wrap-around ordering ambiguity.
+  private val writeToPredDist   = distanceBetween(writePtr, predPtr)
+  private val predToRecoverDist = distanceBetween(predPtr, recoverPtr)
+  XSError(
+    enqEnable && (writeToPredDist > 3.U || predToRecoverDist > 2.U),
+    "The predPtr exceeds the correct range"
+  )
   XSError(
     writeEnable && s3_update.startPc =/= histQueue(writePtr.value).predStartPc.get,
     "update history maybe mismatched!"
@@ -221,10 +228,10 @@ class CommonHR(implicit p: Parameters) extends CommonHRModule with Helpers with
   s0_commonHR := MuxCase(
     0.U.asTypeOf(new CommonHREntry),
     Seq(
-      r0_valid          -> r0_commonHR,
-      s3_override       -> histQueue(recoverPtr.value),
-      (s0_fire && sync) -> s3_newCommonHR, // bypass s3_newCommonHR
-      s0_fire           -> histQueue(predPtr.value)
+      r0_valid                     -> r0_commonHR,
+      s3_override                  -> histQueue(recoverPtr.value),
+      (s0_fire && s3_fire && sync) -> s3_newCommonHR, // bypass s3_newCommonHR
+      s0_fire                      -> histQueue(predPtr.value)
     )
   )
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Parameters.scala
index 95e8fd8612e..5c6f13c9fea 100644
--- a/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/history/commonhr/Parameters.scala
@@ -18,13 +18,11 @@ package xiangshan.frontend.bpu.history.commonhr
 import xiangshan.frontend.bpu.HasBpuParameters
 
 case class CommonHRParameters(
-    HistQueueSize: Int = 8,
-    ImliWidth:     Int = 8
+    HistQueueSize: Int = 8
 ) {}
 
 trait HasCommonHRParameters extends HasBpuParameters {
   def commonHRParameters: CommonHRParameters = bpuParameters.commonHRParameters
 
   def HistQueueSize: Int = commonHRParameters.HistQueueSize
-  def ImliWidth:     Int = commonHRParameters.ImliWidth
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Bundles.scala
index 14a4cfcf4ab..1cf03d19e97 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Bundles.scala
@@ -88,14 +88,14 @@ class ScMeta(implicit p: Parameters) extends ScBundle with HasScParameters {
   val debug_scBWTakenVec:     Option[Vec[Bool]] = Some(Vec(NumWays, Bool()))
   val debug_scImliTakenVec:   Option[Vec[Bool]] = Some(Vec(NumWays, Bool()))
   val debug_scBiasTakenVec:   Option[Vec[Bool]] = Some(Vec(NumWays, Bool()))
-  val debug_predPathIdx: Option[Vec[UInt]] =
-    Some(Vec(NumPathTables, UInt(log2Ceil(scParameters.PathTableInfos(0).Size).W)))
-  val debug_predGlobalIdx: Option[Vec[UInt]] =
-    Some(Vec(NumGlobalTables, UInt(log2Ceil(scParameters.GlobalTableInfos(0).Size).W)))
-  val debug_predBWIdx: Option[Vec[UInt]] =
-    Some(Vec(NumBWTables, UInt(log2Ceil(scParameters.BackwardTableInfos(0).Size).W)))
-  val debug_predImliIdx: Option[UInt] = Some(UInt(log2Ceil(ImliTableSize).W))
-  val debug_predBiasIdx: Option[UInt] = Some(UInt(log2Ceil(BiasTableSize).W))
+  val debug_predPathIdx: Option[MixedVec[UInt]] =
+    Some(MixedVec(PathTableInfos.map(info => UInt(log2Ceil(info.NumSets).W))))
+  val debug_predGlobalIdx: Option[MixedVec[UInt]] =
+    Some(MixedVec(GlobalTableInfos.map(info => UInt(log2Ceil(info.NumSets).W))))
+  val debug_predBWIdx: Option[MixedVec[UInt]] =
+    Some(MixedVec(BackwardTableInfos.map(info => UInt(log2Ceil(info.NumSets).W))))
+  val debug_predImliIdx: Option[UInt] = Some(UInt(log2Ceil(ImliTableInfo.NumSets).W))
+  val debug_predBiasIdx: Option[UInt] = Some(UInt(log2Ceil(BiasTableInfo.NumSets).W))
 }
 
 class ScConditionalBranchTrace(implicit p: Parameters) extends ScBundle with HasScParameters {
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala
index 2845bbdfb10..22ff2dfe655 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Helpers.scala
@@ -19,10 +19,11 @@ import chisel3._
 import chisel3.util._
 import scala.math.min
 import utility.ParallelXOR
-import xiangshan.HasXSParameter
+import utils.AddrField
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.FoldedHistoryInfo
 import xiangshan.frontend.bpu.PhrHelper
+import xiangshan.frontend.bpu.ScTableInfo
 import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 
 trait Helpers extends HasScParameters with PhrHelper {
@@ -30,8 +31,19 @@ trait Helpers extends HasScParameters with PhrHelper {
   def pos(x:  SInt): Bool = !sign(x)
   def neg(x:  SInt): Bool = sign(x)
 
+  protected def generateAddrField(setIdxWidth: Option[Int] = None): AddrField = AddrField(
+    Seq(
+      ("shiftBit", FetchBlockAlignWidth),
+      ("bankIdx", BankWidth)
+    ) ++ (if (setIdxWidth.isDefined) Seq(("setIdx", setIdxWidth.get)) else Seq()),
+    maxWidth = Option(VAddrBits)
+  )
+
+  lazy val addrFields = generateAddrField()
+
+  // sc should start using startPc as setIdx from the highest bit of CfiPosition
   def getBankMask(pc: PrunedAddr): UInt =
-    UIntToOH((pc >> (instOffsetBits + log2Ceil(NumWays)))(BankWidth - 1, 0))
+    UIntToOH(addrFields.extract("bankIdx", pc))
 
   def getWayIdx(cfiPosition: UInt): UInt = {
     val nChunks = (cfiPosition.getWidth + log2Ceil(NumWays) - 1) / log2Ceil(NumWays)
@@ -41,41 +53,10 @@ trait Helpers extends HasScParameters with PhrHelper {
     ParallelXOR(hashChunks)
   }
 
-  // get pc ^ foldedHist for index
-  def getPathTableIdx(pc: PrunedAddr, info: FoldedHistoryInfo, allFh: PhrAllFoldedHistories, numSets: Int): UInt =
-    if (info.HistoryLength > 0) {
-      val idxFoldedHist = allFh.getHistWithInfo(info).foldedHist
-      ((pc >> (instOffsetBits + log2Ceil(NumWays) + BankWidth)) ^ idxFoldedHist)(log2Ceil(numSets) - 1, 0)
-    } else {
-      (pc >> (instOffsetBits + log2Ceil(NumWays) + BankWidth))(log2Ceil(numSets) - 1, 0)
-    }
-
-  // get pc ^ foldedGhr for index
-  def getGlobalTableIdx(pc: PrunedAddr, ghr: UInt, numSets: Int, ghrLen: Int): UInt = {
-    val foldedGhr = computeFoldedHist(ghr, log2Ceil(numSets))(ghrLen)
-    ((pc >> (instOffsetBits + log2Ceil(NumWays) + BankWidth)) ^ foldedGhr)(log2Ceil(numSets) - 1, 0)
-  }
-
-  // get pc ^ foldedBW for index
-  def getBWTableIdx(pc: PrunedAddr, bw: UInt, numSets: Int, bwLen: Int): UInt = {
-    val foldedBW = computeFoldedHist(bw, log2Ceil(numSets))(bwLen)
-    ((pc >> (instOffsetBits + log2Ceil(NumWays) + BankWidth)) ^ foldedBW)(log2Ceil(numSets) - 1, 0)
-  }
-
-  // get pc ^ foldedImli index
-  def getImliTableIdx(pc: PrunedAddr, imli: UInt, numSets: Int, imliLen: Int): UInt = {
-    val foldedImli = computeFoldedHist(imli, log2Ceil(numSets))(imliLen)
-    ((pc >> (instOffsetBits + log2Ceil(NumWays) + BankWidth)) ^ foldedImli)(log2Ceil(numSets) - 1, 0)
-  }
-
-  // get bias index
-  def getBiasTableIdx(pc: PrunedAddr, numSets: Int): UInt =
-    (pc >> (instOffsetBits + log2Ceil(NumWays) + BankWidth))(log2Ceil(numSets) - 1, 0)
-
   def getPercsum(ctr: SInt): SInt = Cat(ctr, 1.U(1.W)).asSInt
 
   def aboveThreshold(scSum: SInt, threshold: UInt): Bool =
-    (scSum > threshold.zext) && pos(scSum) || (scSum < -threshold.zext) && neg(scSum)
+    ((scSum > threshold.zext) && pos(scSum)) || ((scSum < -threshold.zext) && neg(scSum))
 
   // Accumulate update information for multiple branches using update methods
   def updateEntry(
@@ -88,11 +69,12 @@ trait Helpers extends HasScParameters with PhrHelper {
   ): Vec[ScEntry] = {
     require(
       writeValidVec.length == takenMask.length &&
-        writeValidVec.length == wayIdxVec.length,
-      "Length of writeValidVec, takenMask and wayIdxVec should be the same"
+        writeValidVec.length == wayIdxVec.length &&
+        writeValidVec.length == branchIdxVec.length,
+      "Length of writeValidVec, takenMask, wayIdxVec and branchIdxVec should be the same"
     )
     val newEntries = Wire(Vec(oldEntries.length, new ScEntry()))
-    // For each reslove branch, record its update direction, whether it has been updated, and which way it has been updated to
+    // For each resolved branch, record its update direction, update requirement, and target way.
     val writeNeedMask = VecInit(Seq.fill(writeValidVec.length)(VecInit(Seq.fill(oldEntries.length)(false.B))))
     val writeDirMask  = VecInit(Seq.fill(writeValidVec.length)(VecInit(Seq.fill(oldEntries.length)(false.B))))
     writeValidVec.zip(takenMask).zip(wayIdxVec).zip(branchIdxVec).zipWithIndex.foreach {
@@ -132,3 +114,41 @@ trait Helpers extends HasScParameters with PhrHelper {
     updateWayMask
   }
 }
+
+trait AbstractTableHelper extends Helpers {
+  protected def TableInfo: ScTableInfo
+
+  final protected def NumSets: Int = TableInfo.NumSets
+
+  final protected def SetIdxWidth: Int = log2Ceil(NumSets)
+
+  override lazy val addrFields = generateAddrField(Option(SetIdxWidth))
+}
+
+trait PathTableHelper extends AbstractTableHelper {
+
+  def getPathTableIdx(pc: PrunedAddr, info: FoldedHistoryInfo, allFh: PhrAllFoldedHistories): UInt =
+    if (info.HistoryLength > 0) {
+      val idxFoldedHist = allFh.getHistWithInfo(info).foldedHist
+      addrFields.extract("setIdx", pc) ^ idxFoldedHist
+    } else {
+      addrFields.extract("setIdx", pc)
+    }
+}
+
+trait CommonTableHelper extends AbstractTableHelper {
+  final protected def HistoryLength: Int = TableInfo.HistoryLength
+
+  // get pc ^ foldedHist for index
+  // ghr/imli/bw using getTableIdx to calculate setIdx
+  def getTableIdx(pc: PrunedAddr, hist: UInt): UInt = {
+    val foldedHist = computeFoldedHist(hist, SetIdxWidth)(HistoryLength)
+    addrFields.extract("setIdx", pc) ^ foldedHist
+  }
+}
+
+trait BiasTableHelper extends AbstractTableHelper {
+
+  def getBiasTableIdx(pc: PrunedAddr): UInt =
+    addrFields.extract("setIdx", pc)
+}
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala
index a563383b95b..3dcd0c2ba8b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Parameters.scala
@@ -33,11 +33,11 @@ case class ScParameters(
       new ScTableInfo(128, 4),
       new ScTableInfo(128, 8)
     ),
-    ImliTableSize:       Int = 128,
-    BiasTableSize:       Int = 128,
+    ImliTableInfo:       ScTableInfo = new ScTableInfo(128, 8),
+    BiasTableInfo:       ScTableInfo = new ScTableInfo(128, 0),
     BiasUseTageBitWidth: Int = 2,    // use tage_taken as index bits
     PathEnable:          Boolean = true,
-    GlobalEnable:        Boolean = false,
+    GlobalEnable:        Boolean = true,
     BWEnable:            Boolean = false,
     ImliEnable:          Boolean = true,
     BiasEnable:          Boolean = true,
@@ -71,26 +71,30 @@ trait HasScParameters extends HasBpuParameters {
   def GlobalTableInfos: Seq[ScTableInfo] = scParameters.GlobalTableInfos
   def NumGlobalTables:  Int              = GlobalTableInfos.length
 
-  def ImliTableSize: Int = scParameters.ImliTableSize
-  def ImliWidth:     Int = bpuParameters.commonHRParameters.ImliWidth
-  def NumImliTable:  Int = 1
+  def ImliTableInfo: ScTableInfo = scParameters.ImliTableInfo
+  def NumImliTable:  Int         = 1
 
-  def BiasTableSize:       Int = scParameters.BiasTableSize
-  def BiasUseTageBitWidth: Int = scParameters.BiasUseTageBitWidth
-  def BiasTableNumWays:    Int = NumWays << BiasUseTageBitWidth // add tage_taken bits as wayIdx
-  def NumBiasTable:        Int = 1
+  def BiasTableInfo:       ScTableInfo = scParameters.BiasTableInfo
+  def BiasUseTageBitWidth: Int         = scParameters.BiasUseTageBitWidth
+  def BiasTableNumWays:    Int         = NumWays << BiasUseTageBitWidth // add tage_taken bits as wayIdx
+  def NumBiasTable:        Int         = 1
 
   def BackwardTableInfos: Seq[ScTableInfo] = scParameters.BackwardTableInfos
   def NumBWTables:        Int              = BackwardTableInfos.length
 
-  // If tage LowConf, the totoalSum should be at least NumTables + 5, Threshold should be (NumTables + 5) << 6(threshold >> 3 + lowConf threshold >> 3)
+  // If tage LowConf, the totalSum should be at least NumTables + 5, Threshold should be (NumTables + 5) << 6(threshold >> 3 + lowConf threshold >> 3)
   // The value of ctr saturation is 63.
   // If all ctrs are saturated, the corresponding Threshold should be (NumTables * 63) << 4(threshold >> 3 + highConf threshold >> 1)
-  def NumTables:    Int = NumPathTables + NumGlobalTables + NumBiasTable + NumBWTables + NumImliTable
-  def MinThreshold: Int = (NumTables + 5) << 6
-  def MaxThreshold: Int = min((NumTables * 63) << 4, (1 << ThresholdWidth) - 1)
+  def NumTables:     Int = NumPathTables + NumGlobalTables + NumBiasTable + NumBWTables + NumImliTable
+  def MinThreshold:  Int = (NumTables + 5) << 6
+  def MaxThreshold:  Int = min((NumTables * 63) << 4, (1 << ThresholdWidth) - 1)
+  def ThresholdInit: Int = scParameters.ThresholdInit
+  require(
+    ThresholdInit >= MinThreshold && ThresholdInit <= MaxThreshold,
+    s"ThresholdInit($ThresholdInit) should be in [$MinThreshold, $MaxThreshold]"
+  )
 
   def WriteBufferSize: Int = scParameters.WriteBufferSize
-  def TotalSumWidth: Int = CtrWidth + 1 + log2Ceil(NumPathTables + NumGlobalTables + NumBiasTable) // +1 for counter * 2
+
   def EnableScTrace: Boolean = scParameters.EnableScTrace
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
index da219cbbd8c..e0dfa302c75 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala
@@ -29,6 +29,7 @@ import xiangshan.frontend.bpu.BasePredictorIO
 import xiangshan.frontend.bpu.FoldedHistoryInfo
 import xiangshan.frontend.bpu.Prediction
 import xiangshan.frontend.bpu.SaturateCounter
+import xiangshan.frontend.bpu.ScTableInfo
 import xiangshan.frontend.bpu.history.commonhr.CommonHREntry
 import xiangshan.frontend.bpu.history.phr.PhrAllFoldedHistories
 import xiangshan.frontend.bpu.tage.{TakenCounter => TageTakenCounter}
@@ -43,7 +44,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
     val providerTakenCtrs: Vec[Valid[SaturateCounter]] =
       Input(Vec(NumBtbResultEntries, Valid(TageTakenCounter()))) // s2 stage tage info
     val foldedPathHist:      PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
-    val imli:                UInt                  = Input(UInt(ImliWidth.W))
+    val imli:                UInt                  = Input(UInt(ImliHistoryLength.W))
     val commonHR:            CommonHREntry         = Input(new CommonHREntry())
     val trainFoldedPathHist: PhrAllFoldedHistories = Input(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
     val scTakenMask:         Vec[Bool]             = Output(Vec(NumBtbResultEntries, Bool()))
@@ -64,19 +65,30 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
    *  instantiate tables
    */
   private val pathTable = PathTableInfos.zipWithIndex.map { case (info, i) =>
-    Module(new ScTable(info.Size, NumWays, "pathTable", i))
+    Module(new ScTable(info.NumSets, NumWays, "pathTable", i) with PathTableHelper {
+      override protected def TableInfo: ScTableInfo = info
+    })
   }
 
   private val globalTable = GlobalTableInfos.zipWithIndex.map { case (info, i) =>
-    Module(new ScTable(info.Size, NumWays, "globalTable", i))
+    Module(new ScTable(info.NumSets, NumWays, "globalTable", i) with CommonTableHelper {
+      override protected def TableInfo: ScTableInfo = info
+    })
   }
 
   private val bwTable = BackwardTableInfos.zipWithIndex.map { case (info, i) =>
-    Module(new ScTable(info.Size, NumWays, "bwTable", i))
+    Module(new ScTable(info.NumSets, NumWays, "bwTable", i) with CommonTableHelper {
+      override protected def TableInfo: ScTableInfo = info
+    })
   }
-  private val imliTable = Module(new ScTable(ImliTableSize, NumWays, "imliTable", 0))
+  private val imliTable = Module(new ScTable(ImliTableInfo.NumSets, NumWays, "imliTable", 0) with CommonTableHelper {
+    override protected def TableInfo: ScTableInfo = ImliTableInfo
+  })
 
-  private val biasTable = Module(new ScTable(BiasTableSize, BiasTableNumWays, "biasTable", 0))
+  private val biasTable =
+    Module(new ScTable(BiasTableInfo.NumSets, BiasTableNumWays, "biasTable", 0) with BiasTableHelper {
+      override protected def TableInfo: ScTableInfo = BiasTableInfo
+    })
 
   private val scThreshold = RegInit(VecInit.tabulate(NumWays)(_ => ThresholdCounter.Init))
 
@@ -103,36 +115,35 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
    */
   private val s0_startPc  = io.startPc
   private val s0_bankMask = getBankMask(s0_startPc)
-  private val s0_pathIdx = PathTableInfos.map(info =>
-    getPathTableIdx(
+  private val s0_pathIdx = PathTableInfos.zip(pathTable).map { case (info, table) =>
+    table.getPathTableIdx(
       s0_startPc,
-      new FoldedHistoryInfo(info.HistoryLength, min(info.HistoryLength, log2Ceil(info.Size))),
-      io.foldedPathHist,
-      info.Size
+      new FoldedHistoryInfo(info.HistoryLength, min(info.HistoryLength, log2Ceil(info.NumSets))),
+      io.foldedPathHist
     )
-  )
+  }
 
   private val s1_pathIdx = s0_pathIdx.map(RegEnable(_, s0_fire)) // for debug
   private val s2_pathIdx = s1_pathIdx.map(RegEnable(_, s1_fire)) // for debug
 
-  private val s0_globalIdx = GlobalTableInfos.map(info =>
-    getGlobalTableIdx(s0_startPc, s0_commonHR.ghr(info.HistoryLength - 1, 0), info.Size, info.HistoryLength)
-  )
+  private val s0_globalIdx = GlobalTableInfos.zip(globalTable).map { case (info, table) =>
+    table.getTableIdx(s0_startPc, s0_commonHR.ghr(info.HistoryLength - 1, 0))
+  }
 
   private val s1_globalIdx = s0_globalIdx.map(RegEnable(_, s0_fire)) // for debug
   private val s2_globalIdx = s1_globalIdx.map(RegEnable(_, s1_fire)) // for debug
 
-  private val s0_imliIdx = getImliTableIdx(s0_startPc, io.imli, ImliTableSize, ImliWidth)
+  private val s0_imliIdx = imliTable.getTableIdx(s0_startPc, io.imli)
   private val s1_imliIdx = RegEnable(s0_imliIdx, s0_fire) // for debug
   private val s2_imliIdx = RegEnable(s1_imliIdx, s1_fire) // for debug
 
-  private val s0_biasIdx = getBiasTableIdx(s0_startPc, BiasTableSize)
+  private val s0_biasIdx = biasTable.getBiasTableIdx(s0_startPc)
   private val s1_biasIdx = RegEnable(s0_biasIdx, s0_fire) // for debug
   private val s2_biasIdx = RegEnable(s1_biasIdx, s1_fire) // for debug
 
-  private val s0_bwIdx = BackwardTableInfos.map(info =>
-    getBWTableIdx(s0_startPc, s0_commonHR.bw(info.HistoryLength - 1, 0), info.Size, info.HistoryLength)
-  )
+  private val s0_bwIdx = BackwardTableInfos.zip(bwTable).map { case (info, table) =>
+    table.getTableIdx(s0_startPc, s0_commonHR.bw(info.HistoryLength - 1, 0))
+  }
   private val s1_bwIdx = s0_bwIdx.map(RegEnable(_, s0_fire)) // for debug
   private val s2_bwIdx = s1_bwIdx.map(RegEnable(_, s1_fire)) // for debug
 
@@ -332,9 +343,9 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   io.meta.debug_scImliTakenVec.get   := VecInit(s2_imliPred.map(RegEnable(_, s2_fire)))
   io.meta.debug_scBiasTakenVec.get   := VecInit(s2_biasPred.map(RegEnable(_, s2_fire)))
 
-  io.meta.debug_predPathIdx.get   := RegEnable(VecInit(s2_pathIdx), s2_fire) // for debug
-  io.meta.debug_predGlobalIdx.get := RegEnable(VecInit(s2_globalIdx), s2_fire)
-  io.meta.debug_predBWIdx.get     := RegEnable(VecInit(s2_bwIdx), s2_fire)
+  io.meta.debug_predPathIdx.get   := RegEnable(MixedVecInit(s2_pathIdx), s2_fire) // for debug
+  io.meta.debug_predGlobalIdx.get := RegEnable(MixedVecInit(s2_globalIdx), s2_fire)
+  io.meta.debug_predBWIdx.get     := RegEnable(MixedVecInit(s2_bwIdx), s2_fire)
   io.meta.debug_predImliIdx.get   := RegEnable(s2_imliIdx, s2_fire)
   io.meta.debug_predBiasIdx.get   := RegEnable(s2_biasIdx, s2_fire)
 
@@ -354,28 +365,22 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
 
   private val t1_bankMask = getBankMask(t1_train.startPc)
 
-  private val t1_pathSetIdx = PathTableInfos.map(info =>
-    getPathTableIdx(
+  private val t1_pathSetIdx = PathTableInfos.zip(pathTable).map { case (info, table) =>
+    table.getPathTableIdx(
       t1_train.startPc,
-      new FoldedHistoryInfo(info.HistoryLength, min(info.HistoryLength, log2Ceil(info.Size))),
-      RegEnable(io.trainFoldedPathHist, t0_fire),
-      info.Size
+      new FoldedHistoryInfo(info.HistoryLength, min(info.HistoryLength, log2Ceil(info.NumSets))),
+      RegEnable(io.trainFoldedPathHist, t0_fire)
     )
-  )
-  private val t1_globalSetIdx = GlobalTableInfos.map(info =>
-    getGlobalTableIdx(
-      t1_train.startPc,
-      t1_commonHR.ghr(info.HistoryLength - 1, 0),
-      info.Size,
-      info.HistoryLength
-    )
-  )
+  }
+  private val t1_globalSetIdx = GlobalTableInfos.zip(globalTable).map { case (info, table) =>
+    table.getTableIdx(t1_train.startPc, t1_commonHR.ghr(info.HistoryLength - 1, 0))
+  }
 
-  private val t1_bwSetIdx = BackwardTableInfos.map(info =>
-    getBWTableIdx(t1_train.startPc, t1_commonHR.bw(info.HistoryLength - 1, 0), info.Size, info.HistoryLength)
-  )
-  private val t1_imliSetIdx = getImliTableIdx(t1_train.startPc, t1_commonHR.imli, BiasTableSize, ImliWidth)
-  private val t1_biasSetIdx = getBiasTableIdx(t1_train.startPc, BiasTableSize)
+  private val t1_bwSetIdx = BackwardTableInfos.zip(bwTable).map { case (info, table) =>
+    table.getTableIdx(t1_train.startPc, t1_commonHR.bw(info.HistoryLength - 1, 0))
+  }
+  private val t1_imliSetIdx = imliTable.getTableIdx(t1_train.startPc, t1_commonHR.imli)
+  private val t1_biasSetIdx = biasTable.getBiasTableIdx(t1_train.startPc)
 
   private val t1_oldPathEntries = VecInit(t1_meta.scPathResp.map(v => VecInit(v.map(r => r.asTypeOf(new ScEntry())))))
   private val t1_oldGlobalEntries =
@@ -596,7 +601,7 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
     case (((table, idx), writeEntries), writeWayMask) =>
       table.io.update.valid    := t2_writeValid && t2_commonHR.valid && GlobalEnable.B
       table.io.update.setIdx   := idx
-      table.io.update.bankMask := t1_bankMask
+      table.io.update.bankMask := t2_bankMask
       table.io.update.wayMask  := writeWayMask
       table.io.update.entryVec := writeEntries
   }
@@ -745,6 +750,8 @@ class Sc(implicit p: Parameters) extends BasePredictor with HasScParameters with
   )
   XSPerfAccumulate(s"total_sc_correct", scCorrectVec.reduce(_ || _))
   XSPerfAccumulate(s"total_sc_wrong", scWrongVec.reduce(_ || _))
+  XSPerfAccumulate(s"total_tage_correct", tageCorrectVec.reduce(_ || _))
+  XSPerfAccumulate(s"total_tage_wrong", tageWrongVec.reduce(_ || _))
 
   XSPerfAccumulate(s"total_sc_path_correct", scPathCorrectVec.reduce(_ || _))
   XSPerfAccumulate(s"total_sc_path_wrong", scPathWrongVec.reduce(_ || _))
diff --git a/src/main/scala/xiangshan/frontend/bpu/sc/ScTable.scala b/src/main/scala/xiangshan/frontend/bpu/sc/ScTable.scala
index a2f0d4f8125..e3a11d72870 100644
--- a/src/main/scala/xiangshan/frontend/bpu/sc/ScTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/sc/ScTable.scala
@@ -41,6 +41,11 @@ class ScTable(
 
   val io = IO(new ScTableIO())
 
+  println(f"Sc$tableType[$tableIdx]:")
+  println(f"  Size(set, bank, way): $numSets * $NumBanks * $numWays")
+  println(f"  Address fields:")
+  addrFields.show(indent = 4)
+
   def numRows: Int = numSets
 
   private val sram = Seq.fill(NumBanks)(
```
