# Commit Log
- Issue: #5232
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5232
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5232
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5232
- Changed files: 13
- Additions: 491
- Deletions: 492

## Files
- `src/main/scala/xiangshan/frontend/icache/Bundles.scala`
- `src/main/scala/xiangshan/frontend/icache/Helpers.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheDataArray.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheDataBank.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMetaArray.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMetaInterleavedBank.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala`
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala`
- `src/main/scala/xiangshan/frontend/icache/Parameters.scala`
- `src/main/scala/xiangshan/frontend/icache/SRAMTemplateWithFixedWidth.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/Bundles.scala b/src/main/scala/xiangshan/frontend/icache/Bundles.scala
index 91002632ed9..6319aea9262 100644
--- a/src/main/scala/xiangshan/frontend/icache/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/icache/Bundles.scala
@@ -55,29 +55,42 @@ object ICacheMetadata {
   }
 }
 
+class ICacheMetaEntry(implicit p: Parameters) extends ICacheBundle {
+  val meta: ICacheMetadata = new ICacheMetadata
+  val code: UInt           = UInt(MetaEccBits.W)
+}
+
+class ICacheDataEntry(implicit p: Parameters) extends ICacheBundle {
+  val data:    UInt = UInt(ICacheDataBits.W)
+  val code:    UInt = UInt(DataEccBits.W)
+  val padding: UInt = UInt(DataPaddingBits.W)
+}
+
+class MetaInfo(implicit p: Parameters) extends ICacheBundle {
+  val waymask:     UInt = UInt(nWays.W)
+  val maybeRvcMap: UInt = UInt(MaxInstNumPerBlock.W)
+  val metaCodes:   UInt = UInt(MetaEccBits.W)
+}
+
 /* ***** Array write ***** */
 // ICacheMissUnit <-> ICacheMetaArray
 class MetaWriteBundle(implicit p: Parameters) extends ICacheBundle {
-  class MetaWriteReqBundle(implicit p: Parameters) extends ICacheBundle {
-    val meta:    ICacheMetadata = new ICacheMetadata
-    val vSetIdx: UInt           = UInt(idxBits.W)
-    val waymask: UInt           = UInt(nWays.W)
-    val bankIdx: Bool           = Bool()
-    val poison:  Bool           = Bool()
+  class MetaWriteReqBundle(implicit p: Parameters) extends ICacheBundle with ICacheEccHelper {
+    val entry:   ICacheMetaEntry = new ICacheMetaEntry
+    val vSetIdx: UInt            = UInt(idxBits.W)
+    val waymask: UInt            = UInt(nWays.W)
 
     def generate(
         phyTag:      UInt,
         maybeRvcMap: UInt,
         vSetIdx:     UInt,
         waymask:     UInt,
-        bankIdx:     Bool,
         poison:      Bool
     ): Unit = {
-      this.meta    := ICacheMetadata(phyTag, maybeRvcMap)
-      this.vSetIdx := vSetIdx
-      this.waymask := waymask
-      this.bankIdx := bankIdx
-      this.poison  := poison
+      this.entry.meta := ICacheMetadata(phyTag, maybeRvcMap)
+      this.entry.code := encodeMetaEccByPort(this.entry.meta, poison)
+      this.vSetIdx    := vSetIdx
+      this.waymask    := waymask
     }
   }
   val req: DecoupledIO[MetaWriteReqBundle] = DecoupledIO(new MetaWriteReqBundle)
@@ -85,19 +98,19 @@ class MetaWriteBundle(implicit p: Parameters) extends ICacheBundle {
 
 // ICacheMissUnit <-> ICacheDataArray
 class DataWriteBundle(implicit p: Parameters) extends ICacheBundle {
-  class DataWriteReqBundle(implicit p: Parameters) extends ICacheBundle {
-    val data:    UInt = UInt(blockBits.W)
-    val vSetIdx: UInt = UInt(idxBits.W)
-    val waymask: UInt = UInt(nWays.W)
-    val bankIdx: Bool = Bool()
-    val poison:  Bool = Bool()
-
-    def generate(data: UInt, vSetIdx: UInt, waymask: UInt, bankIdx: Bool, poison: Bool): Unit = {
-      this.data    := data
+  class DataWriteReqBundle(implicit p: Parameters) extends ICacheBundle with ICacheEccHelper {
+    val entries: Vec[ICacheDataEntry] = Vec(DataBanks, new ICacheDataEntry)
+    val vSetIdx: UInt                 = UInt(idxBits.W)
+    val waymask: UInt                 = UInt(nWays.W)
+
+    def generate(data: UInt, vSetIdx: UInt, waymask: UInt, poison: Bool): Unit = {
+      (this.entries zip data.asTypeOf(Vec(DataBanks, UInt(ICacheDataBits.W)))).foreach { case (e, d) =>
+        e.data    := d
+        e.code    := encodeDataEccByBank(d, poison)
+        e.padding := 0.U // for better SRAM area
+      }
       this.vSetIdx := vSetIdx
       this.waymask := waymask
-      this.bankIdx := bankIdx
-      this.poison  := poison
     }
   }
   val req: DecoupledIO[DataWriteReqBundle] = DecoupledIO(new DataWriteReqBundle)
@@ -123,14 +136,7 @@ class ArrayReadReqBundle(implicit p: Parameters) extends ICacheBundle {
 class MetaReadBundle(implicit p: Parameters) extends ICacheBundle {
   class MetaReadReqBundle(implicit p: Parameters) extends ArrayReadReqBundle
   class MetaReadRespBundle(implicit p: Parameters) extends ICacheBundle {
-    val metas:      Vec[Vec[ICacheMetadata]] = Vec(PortNumber, Vec(nWays, new ICacheMetadata))
-    val codes:      Vec[Vec[UInt]]           = Vec(PortNumber, Vec(nWays, UInt(MetaEccBits.W)))
-    val entryValid: Vec[Vec[Bool]]           = Vec(PortNumber, Vec(nWays, Bool()))
-    // for compatibility
-    def tags: Vec[Vec[UInt]] =
-      VecInit(metas.map(port => VecInit(port.map(way => way.phyTag))))
-    def maybeRvcMap: Vec[Vec[UInt]] =
-      VecInit(metas.map(port => VecInit(port.map(way => way.maybeRvcMap.getOrElse(0.U(MaxInstNumPerBlock.W))))))
+    val entries: Vec[Vec[Valid[ICacheMetaEntry]]] = Vec(PortNumber, Vec(nWays, Valid(new ICacheMetaEntry)))
   }
   val req:  DecoupledIO[MetaReadReqBundle] = DecoupledIO(new MetaReadReqBundle)
   val resp: MetaReadRespBundle             = Input(new MetaReadRespBundle)
@@ -139,9 +145,9 @@ class MetaReadBundle(implicit p: Parameters) extends ICacheBundle {
 // ICacheMainPipe -> ICacheDataArray
 class DataReadBundle(implicit p: Parameters) extends ICacheBundle {
   class DataReadReqBundle(implicit p: Parameters) extends ArrayReadReqBundle {
-    val waymask:      Vec[Vec[Bool]] = Vec(PortNumber, Vec(nWays, Bool()))
-    val blkOffset:    UInt           = UInt(log2Ceil(blockBytes).W)
-    val blkEndOffset: UInt           = UInt(log2Ceil(blockBytes).W)
+    val waymask:      Vec[UInt] = Vec(PortNumber, UInt(nWays.W))
+    val blkOffset:    UInt      = UInt(log2Ceil(blockBytes).W)
+    val blkEndOffset: UInt      = UInt(log2Ceil(blockBytes).W)
   }
   class DataReadRespBundle(implicit p: Parameters) extends ICacheBundle {
     val datas: Vec[UInt] = Vec(DataBanks, UInt(ICacheDataBits.W))
@@ -239,6 +245,20 @@ class WayLookupEntry(implicit p: Parameters) extends ICacheBundle {
   val metaCodes:   Vec[UInt] = Vec(PortNumber, UInt(MetaEccBits.W))
   val pTag:        UInt      = UInt(tagBits.W)
   val itlbPbmt:    UInt      = UInt(Pbmt.width.W)
+
+  def getMetaInfo(i: Int): MetaInfo = {
+    val info = Wire(new MetaInfo)
+    info.waymask     := waymask(i)
+    info.maybeRvcMap := maybeRvcMap(i)
+    info.metaCodes   := metaCodes(i)
+    info
+  }
+
+  def updateMetaInfo(i: Int, info: MetaInfo): Unit = {
+    waymask(i)     := info.waymask
+    maybeRvcMap(i) := info.maybeRvcMap
+    metaCodes(i)   := info.metaCodes
+  }
 }
 
 class WayLookupExceptionEntry(implicit p: Parameters) extends ICacheBundle {
diff --git a/src/main/scala/xiangshan/frontend/icache/Helpers.scala b/src/main/scala/xiangshan/frontend/icache/Helpers.scala
index ebafc0b7b05..ad71e2beeed 100644
--- a/src/main/scala/xiangshan/frontend/icache/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/icache/Helpers.scala
@@ -28,8 +28,9 @@ trait ICacheEccHelper extends HasICacheParameters {
   }
 
   // per-port
-  def checkMetaEccByPort(meta: ICacheMetadata, code: UInt, waymask: Vec[Bool], enable: Bool): Bool = {
+  def checkMetaEccByPort(meta: ICacheMetadata, code: UInt, waymask: UInt, enable: Bool): Bool = {
     require(code.getWidth == MetaEccBits)
+    require(waymask.getWidth == nWays)
     val hitNum = PopCount(waymask)
     // NOTE: if not hit, encodeMetaECC(meta) =/= code can also be true, but we don't care about it
     // hit one way, but parity code does not match => ECC failure
@@ -43,14 +44,13 @@ trait ICacheEccHelper extends HasICacheParameters {
   def checkMetaEcc(
       metaVec:    Vec[ICacheMetadata],
       codeVec:    Vec[UInt],
-      waymaskVec: Vec[Vec[Bool]],
+      waymaskVec: Vec[UInt],
       enable:     Bool,
       doubleline: Bool
   ): Vec[Bool] = {
     require(metaVec.length == PortNumber)
     require(codeVec.length == PortNumber)
     require(waymaskVec.length == PortNumber)
-    require(waymaskVec.head.length == nWays)
     VecInit((metaVec zip codeVec zip waymaskVec).zipWithIndex.map { case (((meta, code), mask), i) =>
       val needThisLine = if (i == 0) true.B else doubleline
       checkMetaEccByPort(meta, code, mask, enable) && needThisLine
@@ -103,18 +103,11 @@ trait ICacheEccHelper extends HasICacheParameters {
 }
 
 trait ICacheMetaHelper extends HasICacheParameters {
-  def getWaymask(reqPTag: UInt, metaPTag: Vec[UInt], metaValid: Vec[Bool]): UInt = {
-    require(metaPTag.length == nWays)
-    require(metaValid.length == nWays)
-    VecInit((metaPTag zip metaValid).map { case (wayPTag, wayValid) =>
-      wayValid && (wayPTag === reqPTag)
-    }).asUInt
-  }
+  def getWaymask(reqPTag: UInt, pTags: Vec[UInt], valids: Vec[Bool]): UInt =
+    VecInit((pTags zip valids).map { case (pt, v) => v && pt === reqPTag }).asUInt
 
-  def getWaymask(reqPTagVec: Vec[UInt], metaPTagVec: Vec[Vec[UInt]], metaValidVec: Vec[Vec[Bool]]): Vec[UInt] =
-    VecInit((reqPTagVec zip metaPTagVec zip metaValidVec).map { case ((reqPTag, metaPTag), metaValid) =>
-      getWaymask(reqPTag, metaPTag, metaValid)
-    })
+  def getWaymask(reqPTag: UInt, entries: Vec[Valid[ICacheMetaEntry]]): UInt =
+    getWaymask(reqPTag, VecInit(entries.map(_.bits.meta.phyTag)), VecInit(entries.map(_.valid)))
 }
 
 trait ICacheDataHelper extends HasICacheParameters {
@@ -156,42 +149,43 @@ trait ICacheAddrHelper extends HasICacheParameters {
 
   def getPAddrFromPTag(vAddr: PrunedAddr, pTag: UInt): PrunedAddr =
     PrunedAddrInit(Cat(pTag, vAddr(pgUntagBits - 1, 0)))
+
+  def getInterleavedBankIdx(vSetIdx: UInt): UInt =
+    vSetIdx(InterleavedBankIdxBits - 1, 0)
+
+  def getInterleavedSetIdx(vSetIdx: UInt): UInt =
+    vSetIdx(idxBits - 1, InterleavedBankIdxBits)
 }
 
 trait ICacheMissUpdateHelper extends HasICacheParameters with ICacheEccHelper with ICacheAddrHelper {
   def updateMetaInfo(
-      update:      Valid[MissRespBundle],
-      waymask:     UInt,
-      vSetIdx:     UInt,
-      pTag:        UInt,
-      maybeRvcMap: UInt,
-      code:        UInt
-  ): (Bool, UInt, UInt, UInt) = {
-    require(waymask.getWidth == nWays)
-    val newMask        = WireInit(waymask)
-    val newMaybeRvcMap = WireInit(maybeRvcMap)
-    val newCode        = WireInit(code)
-    val valid          = update.valid && !update.bits.corrupt
-    val vSetSame       = update.bits.vSetIdx === vSetIdx
-    val pTagSame       = getPTagFromBlk(update.bits.blkPAddr) === pTag
-    val waySame        = update.bits.waymask === waymask
+      update:  Valid[MissRespBundle],
+      vSetIdx: UInt,
+      pTag:    UInt,
+      info:    MetaInfo
+  ): (Bool, MetaInfo) = {
+    val newInfo  = WireInit(info)
+    val valid    = update.valid && !update.bits.corrupt
+    val vSetSame = update.bits.vSetIdx === vSetIdx
+    val pTagSame = getPTagFromBlk(update.bits.blkPAddr) === pTag
+    val waySame  = update.bits.waymask === info.waymask
     when(valid && vSetSame) {
       when(pTagSame) {
         // vSetIdx & pTag match => update has newer data
-        newMask := update.bits.waymask
+        newInfo.waymask := update.bits.waymask
         // also update maybeRvcMap and ecc code
-        newMaybeRvcMap := update.bits.maybeRvcMap
+        newInfo.maybeRvcMap := update.bits.maybeRvcMap
         // we have getPhyTagFromBlk(fromMSHR.bits.blkPAddr) === pTag, so we can use pTag directly for better timing
-        newCode := encodeMetaEccByPort(ICacheMetadata(pTag, update.bits.maybeRvcMap))
+        newInfo.metaCodes := encodeMetaEccByPort(ICacheMetadata(pTag, update.bits.maybeRvcMap))
       }.elsewhen(waySame) {
         // vSetIdx & way match, but pTag not match => older hit data has been replaced, treat as a miss
-        newMask := 0.U
+        newInfo.waymask := 0.U
         // we don't care about maybeRvcMap/code, since it's not used for a missed request
       }
       // otherwise is an irrelevant update, ignore it
     }
     val updated = valid && vSetSame && (pTagSame || waySame)
-    (updated, newMask, newMaybeRvcMap, newCode)
+    (updated, newInfo)
   }
 
   def checkMshrHit(
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala
index 44352c79aeb..993036b14f7 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala
@@ -173,7 +173,6 @@ class ICacheCtrlUnit(implicit p: Parameters) extends LazyModule
       maybeRvcMap = 0.U,
       vSetIdx = iVSetIdx,
       waymask = iWaymask,
-      bankIdx = iVSetIdx(0),
       poison = true.B
     )
 
@@ -182,7 +181,6 @@ class ICacheCtrlUnit(implicit p: Parameters) extends LazyModule
       data = 0.U, // inject poisoned data, don't care actual data
       vSetIdx = iVSetIdx,
       waymask = iWaymask,
-      bankIdx = iVSetIdx(0),
       poison = true.B
     )
 
@@ -200,7 +198,7 @@ class ICacheCtrlUnit(implicit p: Parameters) extends LazyModule
       }
       is(InjectFsmState.ReadMetaResp) {
         // metaArray ensures resp is valid one cycle after req
-        val waymask = getWaymask(iPTag, io.metaRead.resp.tags.head, io.metaRead.resp.entryValid.head)
+        val waymask = getWaymask(iPTag, io.metaRead.resp.entries.head) // we need first port only
         iWaymask := waymask
         when(!waymask.orR) {
           // not hit, refuse to inject
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheDataArray.scala b/src/main/scala/xiangshan/frontend/icache/ICacheDataArray.scala
index 84ba7e57c2e..04d6d3c3e06 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheDataArray.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheDataArray.scala
@@ -1,5 +1,5 @@
-// Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
-// Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
+// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
 // Copyright (c) 2020-2021 Peng Cheng Laboratory
 //
 // XiangShan is licensed under Mulan PSL v2.
@@ -16,11 +16,9 @@
 package xiangshan.frontend.icache
 
 import chisel3._
-import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
-import utility.mbist.MbistPipeline
 
-class ICacheDataArray(implicit p: Parameters) extends ICacheModule with ICacheEccHelper with ICacheDataHelper {
+class ICacheDataArray(implicit p: Parameters) extends ICacheModule with ICacheDataHelper {
   class ICacheDataArrayIO(implicit p: Parameters) extends ICacheBundle {
     val write: DataWriteBundle = Flipped(new DataWriteBundle)
     val read:  DataReadBundle  = Flipped(new DataReadBundle)
@@ -28,100 +26,45 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheModule with ICacheEc
 
   val io: ICacheDataArrayIO = IO(new ICacheDataArrayIO)
 
-  class ICacheDataEntry(implicit p: Parameters) extends ICacheBundle {
-    val data: UInt = UInt(ICacheDataBits.W)
-    val code: UInt = UInt(DataEccBits.W)
-  }
-
-  private object ICacheDataEntry {
-    def apply(data: UInt, poison: Bool)(implicit p: Parameters): ICacheDataEntry = {
-      val entry = Wire(new ICacheDataEntry)
-      entry.data := data
-      entry.code := encodeDataEccByBank(data, poison)
-      entry
-    }
-  }
+  // sanity check
+  require(DataSramWidth == (new ICacheDataEntry).getWidth)
 
-  /**
-   ******************************************************************************
-   * data array
-   ******************************************************************************
-   */
-  private val writeDatas   = io.write.req.bits.data.asTypeOf(Vec(DataBanks, UInt(ICacheDataBits.W)))
-  private val writeEntries = writeDatas.map(ICacheDataEntry(_, io.write.req.bits.poison).asUInt)
+  private val banks = Seq.tabulate(DataBanks)(i => Module(new ICacheDataBank(i)))
 
-  private val bankSel =
+  /* *** read *** */
+  private val r0_valid  = io.read.req.valid
+  private val r0_setIdx = io.read.req.bits.vSetIdx
+  private val r0_bankSel =
     getBankSel(io.read.req.bits.blkOffset, io.read.req.bits.blkEndOffset, io.read.req.bits.isDoubleLine)
-  private val lineSel  = getLineSel(io.read.req.bits.blkOffset)
-  private val waymasks = io.read.req.bits.waymask
-  private val masks    = Wire(Vec(nWays, Vec(DataBanks, Bool())))
-  (0 until nWays).foreach { way =>
-    (0 until DataBanks).foreach { bank =>
-      masks(way)(bank) := Mux(
-        lineSel(bank),
-        waymasks(1)(way) && bankSel(1)(bank),
-        waymasks(0)(way) && bankSel(0)(bank)
-      )
-    }
-  }
+  private val r0_lineSel = getLineSel(io.read.req.bits.blkOffset)
+  private val r0_waymask = io.read.req.bits.waymask
 
-  private val dataArrays = (0 until nWays).map { way =>
-    val banks = (0 until DataBanks).map { bank =>
-      val sramBank = Module(new SRAMTemplateWithFixedWidth(
-        UInt(DataEntryBits.W),
-        set = nSets,
-        width = DataSramWidth, // DataEntryBits + DataPaddingBits
-        shouldReset = true,
-        singlePort = true,
-        withClockGate = false, // enable signal timing is bad, no gating here
-        hasMbist = hasMbist,
-        hasSramCtl = hasSramCtl
-      ))
-
-      // read
-      sramBank.io.r.req.valid := io.read.req.valid && masks(way)(bank)
-      sramBank.io.r.req.bits.apply(setIdx =
-        Mux(lineSel(bank), io.read.req.bits.vSetIdx(1), io.read.req.bits.vSetIdx(0))
-      )
-      // write
-      sramBank.io.w.req.valid := io.write.req.valid && io.write.req.bits.waymask(way).asBool
-      sramBank.io.w.req.bits.apply(
-        data = writeEntries(bank),
-        setIdx = io.write.req.bits.vSetIdx,
-        // waymask is invalid when way of SRAMTemplate <= 1
-        waymask = 0.U
-      )
-      sramBank
-    }
-    MbistPipeline.PlaceMbistPipeline(1, s"MbistPipeIcacheDataWay${way}", hasMbist)
-    banks
+  io.read.req.ready := banks.map(_.io.read.req.ready).reduce(_ || _)
+  banks.zipWithIndex.foreach { case (b, i) =>
+    b.io.read.req.valid        := r0_valid && r0_bankSel(r0_lineSel(i))(i)
+    b.io.read.req.bits.setIdx  := r0_setIdx(r0_lineSel(i))
+    b.io.read.req.bits.waymask := r0_waymask(r0_lineSel(i))
   }
 
-  /**
-   ******************************************************************************
-   * read logic
-   ******************************************************************************
-   */
-  private val masksReg = RegEnable(masks, 0.U.asTypeOf(masks), io.read.req.valid)
-  private val readDataWithCode = (0 until DataBanks).map { bank =>
-    Mux1H(VecInit(masksReg.map(_(bank))).asTypeOf(UInt(nWays.W)), dataArrays.map(_(bank).io.r.resp.asUInt))
-  }
-  private val readEntries = readDataWithCode.map(_.asTypeOf(new ICacheDataEntry()))
-  private val readDatas   = VecInit(readEntries.map(_.data))
-  private val readCodes   = VecInit(readEntries.map(_.code))
+  io.read.resp.datas := banks.map(_.io.read.resp.entry.data)
+  io.read.resp.codes := banks.map(_.io.read.resp.entry.code)
 
-  // TEST: force ECC to fail by setting readCodes to 0
+  // TEST: force ECC to fail by setting parity codes to 0
   if (ForceDataEccFail) {
-    readCodes.foreach(_ := 0.U)
+    io.read.resp.codes.foreach(_ := 0.U)
   }
 
-  /**
-   ******************************************************************************
-   * IO
-   ******************************************************************************
-   */
-  io.read.resp.datas := readDatas
-  io.read.resp.codes := readCodes
-  io.write.req.ready := true.B
-  io.read.req.ready  := !io.write.req.valid
+  /* *** write *** */
+  private val w0_valid   = io.write.req.valid
+  private val w0_setIdx  = io.write.req.bits.vSetIdx
+  private val w0_waymask = io.write.req.bits.waymask
+  private val w0_entries = io.write.req.bits.entries
+
+  io.write.req.ready := banks.map(_.io.write.req.ready).reduce(_ && _)
+  banks.zipWithIndex.foreach { case (b, i) =>
+    b.io.write.req.valid        := w0_valid
+    b.io.write.req.bits.setIdx  := w0_setIdx
+    b.io.write.req.bits.waymask := w0_waymask
+    b.io.write.req.bits.entry   := w0_entries(i)
+  }
 }
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheDataBank.scala b/src/main/scala/xiangshan/frontend/icache/ICacheDataBank.scala
new file mode 100644
index 00000000000..d4d2d712f39
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheDataBank.scala
@@ -0,0 +1,96 @@
+// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+// Copyright (c) 2020-2021 Peng Cheng Laboratory
+//
+// XiangShan is licensed under Mulan PSL v2.
+// You can use this software according to the terms and conditions of the Mulan PSL v2.
+// You may obtain a copy of Mulan PSL v2 at:
+//          https://license.coscl.org.cn/MulanPSL2
+//
+// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+//
+// See the Mulan PSL v2 for more details.
+
+package xiangshan.frontend.icache
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.mbist.MbistPipeline
+import utility.sram.SRAMTemplate
+
+class ICacheDataBank(bankIdx: Int)(implicit p: Parameters) extends ICacheModule {
+  class ICacheDataBankIO extends Bundle {
+    class Read extends Bundle {
+      class Req extends Bundle {
+        val setIdx:  UInt = UInt(idxBits.W)
+        val waymask: UInt = UInt(nWays.W)
+      }
+      class Resp extends Bundle {
+        val entry: ICacheDataEntry = new ICacheDataEntry
+      }
+      val req:  DecoupledIO[Req] = Flipped(Decoupled(new Req))
+      val resp: Resp             = Output(new Resp)
+    }
+
+    class Write extends Bundle {
+      class Req extends Bundle {
+        val setIdx:  UInt            = UInt(idxBits.W)
+        val waymask: UInt            = UInt(nWays.W)
+        val entry:   ICacheDataEntry = new ICacheDataEntry
+      }
+      val req: DecoupledIO[Req] = Flipped(Decoupled(new Req))
+    }
+
+    val read:  Read  = new Read
+    val write: Write = new Write
+  }
+
+  val io: ICacheDataBankIO = IO(new ICacheDataBankIO)
+
+  // sanity check
+  require(DataSramWidth == (new ICacheDataEntry).getWidth)
+
+  // manually handle ways instead of using SRAMTemplate way parameter for better power (r.req.valid control)
+  private val ways = Seq.tabulate(nWays) { i =>
+    Module(new SRAMTemplate(
+      new ICacheDataEntry,
+      set = nSets,
+      way = 1,
+      shouldReset = true,
+      singlePort = true,
+      withClockGate = false, // enable signal timing is bad, no gating here
+      hasMbist = hasMbist,
+      hasSramCtl = hasSramCtl,
+      suffix = Option("icache_data")
+    ))
+  }
+  private val mbistPl = MbistPipeline.PlaceMbistPipeline(1, s"MbistPipeICacheData_bank$bankIdx", hasMbist)
+
+  /* *** read *** */
+  io.read.req.ready := !io.write.req.valid && ways.map(_.io.r.req.ready).reduce(_ && _)
+
+  ways.zipWithIndex.foreach { case (w, i) =>
+    w.io.r.req.valid := io.read.req.valid && io.read.req.bits.waymask(i)
+    w.io.r.req.bits.apply(
+      setIdx = io.read.req.bits.setIdx
+    )
+  }
+
+  private val readReqReg = RegEnable(io.read.req.bits, 0.U.asTypeOf(io.read.req.bits), io.read.req.fire)
+
+  io.read.resp.entry := Mux1H(readReqReg.waymask, ways.map(_.io.r.resp.data.head))
+
+  /* *** write *** */
+  io.write.req.ready := ways.map(_.io.w.req.ready).reduce(_ && _)
+  ways.zipWithIndex.foreach { case (w, i) =>
+    w.io.w.req.valid := io.write.req.valid && io.write.req.bits.waymask(i)
+    w.io.w.req.bits.apply(
+      setIdx = io.write.req.bits.setIdx,
+      data = io.write.req.bits.entry,
+      waymask = 0.U // ignored in SRAMTemplate with way = 1, but required
+    )
+  }
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index af97b2b8c50..78e0ca0474d 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -123,7 +123,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     ******************************************************************************
     */
   fromWayLookup.ready := s0_fire
-  private val s0_waymasks          = VecInit(fromWayLookup.bits.waymask.map(_.asTypeOf(Vec(nWays, Bool()))))
+  private val s0_waymasks          = fromWayLookup.bits.waymask
   private val s0_pTag              = fromWayLookup.bits.pTag
   private val s0_gpAddr            = fromWayLookup.bits.gpAddr
   private val s0_isForVSnonLeafPTE = fromWayLookup.bits.isForVSnonLeafPTE
@@ -199,9 +199,14 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   private val s1_sramHits  = RegEnable(s0_hits, 0.U.asTypeOf(s0_hits), s0_fire)
   private val s1_sramDatas = fromData.datas
   private val s1_sramCodes = fromData.codes
+  private val s1_sramValid = VecInit(Seq(
+    RegNext(s0_fire),
+    RegNext(s0_fire) && s1_doubleline
+  ))
+  private val s1_bankSramValid = getBankValid(s1_sramValid, s1_offset)
 
   // mshr: valid when fromMiss.valid
-  private val s1_mshrHits = checkMshrHitVec(
+  private val s1_mshrValid = checkMshrHitVec(
     fromMiss,
     s1_vSetIdx,
     s1_pTag,
@@ -211,12 +216,12 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   private val s1_mshrDatas = fromMiss.bits.data.asTypeOf(Vec(DataBanks, UInt(ICacheDataBits.W)))
 
   // select data
-  private val s1_bankMshrHit = getBankValid(s1_mshrHits, s1_offset)
+  private val s1_bankMshrValid = getBankValid(s1_mshrValid, s1_offset)
 
   private val s1_dataIsFromMshr = VecInit((0 until DataBanks).map { i =>
     DataHoldBypass(
-      s1_bankMshrHit(i),
-      s1_bankMshrHit(i) || RegNext(s0_fire)
+      s1_bankMshrValid(i),
+      s1_bankMshrValid(i) || s1_bankSramValid(i)
     )
   })
 
@@ -228,40 +233,40 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
 
   private val s1_hits = VecInit((0 until PortNumber).map { i =>
     DataHoldBypass(
-      s1_mshrHits(i) || s1_sramHits(i),
-      s1_mshrHits(i) || RegNext(s0_fire)
+      s1_mshrValid(i) || s1_sramHits(i),
+      s1_mshrValid(i) || s1_sramValid(i)
     )
   })
 
   private val s1_datas = VecInit((0 until DataBanks).map { i =>
     DataHoldBypass(
-      Mux(s1_bankMshrHit(i), s1_mshrDatas(i), s1_sramDatas(i)),
-      s1_bankMshrHit(i) || RegNext(s0_fire)
+      Mux(s1_bankMshrValid(i), s1_mshrDatas(i), s1_sramDatas(i)),
+      s1_bankMshrValid(i) || s1_bankSramValid(i)
     )
   })
 
   private val s1_maybeRvcMap = VecInit((0 until DataBanks).map { i =>
     DataHoldBypass(
       Mux(
-        s1_bankMshrHit(i),
+        s1_bankMshrValid(i),
         s1_mshrMaybeRvcMap(i),
         Mux(getLineSel(s1_offset)(i), s1_sramMaybeRvcMap(1)(i), s1_sramMaybeRvcMap(0)(i))
       ),
-      s1_bankMshrHit(i) || RegNext(s0_fire)
+      s1_bankMshrValid(i) || s1_bankSramValid(i)
     )
   })
 
   private val s1_tlCorrupt = VecInit((0 until PortNumber).map { i =>
     DataHoldBypass(
-      s1_mshrHits(i) && fromMiss.bits.corrupt,
-      s1_mshrHits(i) || RegNext(s0_fire)
+      s1_mshrValid(i) && fromMiss.bits.corrupt,
+      s1_mshrValid(i) || s1_sramValid(i)
     )
   })
 
   private val s1_tlDenied = VecInit((0 until PortNumber).map { i =>
     DataHoldBypass(
-      s1_mshrHits(i) && fromMiss.bits.denied,
-      s1_mshrHits(i) || RegNext(s0_fire)
+      s1_mshrValid(i) && fromMiss.bits.denied,
+      s1_mshrValid(i) || s1_sramValid(i)
     )
   })
 
@@ -336,7 +341,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     toMetaFlush(i).bits.vSetIdx := s1_vSetIdx(i)
     // if is meta corrupt, clear all way (since waymask may be unreliable)
     // if is data corrupt, only clear the way that has error
-    toMetaFlush(i).bits.waymask := Mux(s1_metaCorrupt(i), Fill(nWays, true.B), s1_waymasks(i).asUInt)
+    toMetaFlush(i).bits.waymask := Mux(s1_metaCorrupt(i), Fill(nWays, true.B), s1_waymasks(i))
   }
   // PERF: count the number of data parity errors
   XSPerfAccumulate("data_corrupt_0", s1_dataCorrupt(0) && RegNext(s0_fire))
@@ -352,7 +357,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   private val s1_corruptRefetch = VecInit((0 until PortNumber).map { i =>
     ValidHoldBypass(
       (s1_metaCorrupt(i) || s1_dataCorrupt(i)) && RegNext(s0_fire),
-      s1_mshrHits(i), // clear re-fetch flag when re-fetched from mshr
+      s1_mshrValid(i), // clear re-fetch flag when re-fetched from mshr
       s1_flush
     )
   })
@@ -390,8 +395,12 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   private val s1_fetchFinish = !s1_shouldFetch.reduce(_ || _)
 
   // also raise af if l2 corrupt is detected
-  private val s1_tlException = (s1_tlCorrupt zip s1_tlDenied).map { case (corrupt, denied) =>
-    ExceptionType.fromTileLink(corrupt, denied, s1_valid) // s1_valid used only for assertion
+  private val s1_tlException = (s1_tlCorrupt zip s1_tlDenied).zipWithIndex.map { case ((corrupt, denied), i) =>
+    val portValid   = if (i == 0) true.B else s1_doubleline
+    val realCorrupt = corrupt && portValid
+    val realDenied  = denied && portValid
+    val canAssert   = s1_valid && portValid
+    ExceptionType.fromTileLink(realCorrupt, realDenied, canAssert)
   }.reduce(_ || _)
   // NOTE: do NOT raise af if meta/data corrupt is detected, they are automatically recovered by re-fetching from L2
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMetaArray.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMetaArray.scala
index 4f23a7ad194..22db65504b7 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMetaArray.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMetaArray.scala
@@ -1,5 +1,5 @@
-// Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
-// Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
+// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
 // Copyright (c) 2020-2021 Peng Cheng Laboratory
 //
 // XiangShan is licensed under Mulan PSL v2.
@@ -19,10 +19,9 @@ import chisel3._
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import utility.XSPerfAccumulate
-import utility.mbist.MbistPipeline
-import utility.sram.SplittedSRAMTemplate
+import utils.VecRotate
 
-class ICacheMetaArray(implicit p: Parameters) extends ICacheModule with ICacheEccHelper {
+class ICacheMetaArray(implicit p: Parameters) extends ICacheModule with ICacheAddrHelper {
   class ICacheMetaArrayIO(implicit p: Parameters) extends ICacheBundle {
     val write:    MetaWriteBundle = Flipped(new MetaWriteBundle)
     val read:     MetaReadBundle  = Flipped(new MetaReadBundle)
@@ -32,153 +31,83 @@ class ICacheMetaArray(implicit p: Parameters) extends ICacheModule with ICacheEc
 
   val io: ICacheMetaArrayIO = IO(new ICacheMetaArrayIO)
 
-  class ICacheMetaEntry(implicit p: Parameters) extends ICacheBundle {
-    val meta: ICacheMetadata = new ICacheMetadata
-    val code: UInt           = UInt(MetaEccBits.W)
-  }
-
-  private object ICacheMetaEntry {
-    def apply(meta: ICacheMetadata, poison: Bool)(implicit p: Parameters): ICacheMetaEntry = {
-      val entry = Wire(new ICacheMetaEntry)
-      entry.meta := meta
-      entry.code := encodeMetaEccByPort(meta, poison)
-      entry
-    }
-  }
-
   // sanity check
   require(MetaEntryBits == (new ICacheMetaEntry).getWidth)
 
-  private val port0Read0 = io.read.req.valid && !io.read.req.bits.vSetIdx(0)(0)
-  private val port0Read1 = io.read.req.valid && io.read.req.bits.vSetIdx(0)(0)
-  private val port1Read1 = io.read.req.valid && io.read.req.bits.vSetIdx(1)(0) && io.read.req.bits.isDoubleLine
-  private val port1Read0 = io.read.req.valid && !io.read.req.bits.vSetIdx(1)(0) && io.read.req.bits.isDoubleLine
-
-  private val port0Read0Reg = RegEnable(port0Read0, 0.U.asTypeOf(port0Read0), io.read.req.fire)
-  private val port0Read1Reg = RegEnable(port0Read1, 0.U.asTypeOf(port0Read1), io.read.req.fire)
-  private val port1Read1Reg = RegEnable(port1Read1, 0.U.asTypeOf(port1Read1), io.read.req.fire)
-  private val port1Read0Reg = RegEnable(port1Read0, 0.U.asTypeOf(port1Read0), io.read.req.fire)
-
-  private val bank0Idx = Mux(port0Read0, io.read.req.bits.vSetIdx(0), io.read.req.bits.vSetIdx(1))
-  private val bank1Idx = Mux(port0Read1, io.read.req.bits.vSetIdx(0), io.read.req.bits.vSetIdx(1))
-
-  private val writeBank0 = io.write.req.valid && !io.write.req.bits.bankIdx
-  private val writeBank1 = io.write.req.valid && io.write.req.bits.bankIdx
+  private val banks = Seq.tabulate(PortNumber)(i => Module(new ICacheMetaInterleavedBank(i)))
 
-  private val writeMetaBits = ICacheMetaEntry(
-    meta = io.write.req.bits.meta,
-    poison = io.write.req.bits.poison
+  /* *** read *** */
+  // vSetIdx(1) must be vSetIdx(0) + 1 if isDoubleLine, it's pre-computed in Ftq for better timing (maybe)
+  assert(
+    !(
+      io.read.req.valid && io.read.req.bits.isDoubleLine &&
+        io.read.req.bits.vSetIdx(0) + 1.U =/= io.read.req.bits.vSetIdx(1)
+    ),
+    "2 read setIdx must be adjacent!"
   )
 
-  private val tagArrays = (0 until PortNumber) map { bank =>
-    val tagArray = Module(new SplittedSRAMTemplate(
-      new ICacheMetaEntry(),
-      set = nSets / PortNumber,
-      way = nWays,
-      waySplit = 2,
-      dataSplit = 1,
-      shouldReset = true,
-      singlePort = true,
-      withClockGate = true,
-      hasMbist = hasMbist,
-      hasSramCtl = hasSramCtl
-    ))
-
-    // meta connection
-    if (bank == 0) {
-      tagArray.io.r.req.valid := port0Read0 || port1Read0
-      tagArray.io.r.req.bits.apply(setIdx = bank0Idx(idxBits - 1, 1))
-      tagArray.io.w.req.valid := writeBank0
-      tagArray.io.w.req.bits.apply(
-        data = writeMetaBits,
-        setIdx = io.write.req.bits.vSetIdx(idxBits - 1, 1),
-        waymask = io.write.req.bits.waymask
-      )
-    } else {
-      tagArray.io.r.req.valid := port0Read1 || port1Read1
-      tagArray.io.r.req.bits.apply(setIdx = bank1Idx(idxBits - 1, 1))
-      tagArray.io.w.req.valid := writeBank1
-      tagArray.io.w.req.bits.apply(
-        data = writeMetaBits,
-        setIdx = io.write.req.bits.vSetIdx(idxBits - 1, 1),
-        waymask = io.write.req.bits.waymask
-      )
-    }
-
-    tagArray
-  }
-  private val mbistPl = MbistPipeline.PlaceMbistPipeline(1, "MbistPipeIcacheTag", hasMbist)
-
-  private val readSetIdxNext =
-    RegEnable(io.read.req.bits.vSetIdx, 0.U.asTypeOf(io.read.req.bits.vSetIdx), io.read.req.fire)
-  private val validArray = RegInit(VecInit(Seq.fill(nWays)(0.U(nSets.W))))
-  private val validMetas = Wire(Vec(PortNumber, Vec(nWays, Bool())))
-  // valid read
-  (0 until PortNumber).foreach(i =>
-    (0 until nWays).foreach(way =>
-      validMetas(i)(way) := validArray(way)(readSetIdxNext(i))
-    )
+  // rotate setIdxVec to match interleaved banking
+  // e.g. 2-interleaved, if vSetIdx(0) is even (getInterleavedBankIdx == 0), we don't need to rotate
+  //      i.e. vSetIdx(0) goes to bank0, vSetIdx(1) goes to bank1
+  //      if vSetIdx(0) is odd (getInterleavedBankIdx == 1), we need to rotate right once
+  //      i.e. vSetIdx(0) goes to bank1, vSetIdx(1) goes to bank0
+  private val r0_rotator = VecRotate(getInterleavedBankIdx(io.read.req.bits.vSetIdx(0)), storeOneHot = true)
+  private val r0_validVec = r0_rotator.rotate(
+    VecInit(Seq(io.read.req.valid, io.read.req.valid && io.read.req.bits.isDoubleLine))
+  )
+  private val r0_setIdxVec = r0_rotator.rotate(
+    VecInit(io.read.req.bits.vSetIdx.map(getInterleavedSetIdx))
   )
-  io.read.resp.entryValid := validMetas
-
-  io.read.req.ready := !io.write.req.valid && !io.flush.req.map(_.valid).reduce(_ || _) && !io.flushAll &&
-    tagArrays.map(_.io.r.req.ready).reduce(_ && _)
 
-  // valid write
-  private val writeWayNum = OHToUInt(io.write.req.bits.waymask)
-  when(io.write.req.valid) {
-    validArray(writeWayNum) := validArray(writeWayNum).bitSet(io.write.req.bits.vSetIdx, true.B)
+  io.read.req.ready := banks.map(_.io.read.req.ready).reduce(_ && _)
+  banks.zipWithIndex.foreach { case (b, i) =>
+    b.io.read.req.valid       := r0_validVec(i)
+    b.io.read.req.bits.setIdx := r0_setIdxVec(i)
   }
 
-  io.read.resp.metas <> DontCare
-  io.read.resp.codes <> DontCare
-  private val readMetaEntries = tagArrays.map(port => port.io.r.resp.asTypeOf(Vec(nWays, new ICacheMetaEntry())))
-  private val readMetas       = readMetaEntries.map(_.map(_.meta))
-  private val readCodes       = readMetaEntries.map(_.map(_.code))
+  private val r1_rotator = RegEnable(r0_rotator, io.read.req.fire)
+  // rotate back to original order
+  io.read.resp.entries := r1_rotator.revert(VecInit(banks.map(_.io.read.resp.entries)))
 
-  // TEST: force ECC to fail by setting readCodes to 0
+  // TEST: force ECC to fail by setting parity codes to 0
   if (ForceMetaEccFail) {
-    readCodes.foreach(_.foreach(_ := 0.U))
+    io.read.resp.entries.foreach(_.foreach(_.bits.code := 0.U(MetaEccBits.W)))
   }
 
-  when(port0Read0Reg) {
-    io.read.resp.metas(0) := readMetas(0)
-    io.read.resp.codes(0) := readCodes(0)
-  }.elsewhen(port0Read1Reg) {
-    io.read.resp.metas(0) := readMetas(1)
-    io.read.resp.codes(0) := readCodes(1)
+  /* *** write *** */
+  private val w0_bankIdx = getInterleavedBankIdx(io.write.req.bits.vSetIdx)
+  private val w0_valid   = io.write.req.valid
+  private val w0_setIdx  = getInterleavedSetIdx(io.write.req.bits.vSetIdx)
+  private val w0_waymask = io.write.req.bits.waymask
+  private val w0_entry   = io.write.req.bits.entry
+
+  io.write.req.ready := banks.map(_.io.write.req.ready).reduce(_ && _)
+  banks.zipWithIndex.foreach { case (b, i) =>
+    b.io.write.req.valid        := w0_valid && (i.U === w0_bankIdx)
+    b.io.write.req.bits.setIdx  := w0_setIdx
+    b.io.write.req.bits.waymask := w0_waymask
+    b.io.write.req.bits.entry   := w0_entry
   }
 
-  when(port1Read0Reg) {
-    io.read.resp.metas(1) := readMetas(0)
-    io.read.resp.codes(1) := readCodes(0)
-  }.elsewhen(port1Read1Reg) {
-    io.read.resp.metas(1) := readMetas(1)
-    io.read.resp.codes(1) := readCodes(1)
-  }
+  /* *** flush *** */
+  // similar to read
+  assert(
+    !(
+      io.flush.req(0).valid && io.flush.req(1).valid &&
+        io.flush.req(0).bits.vSetIdx + 1.U =/= io.flush.req(1).bits.vSetIdx
+    ),
+    "2 flush setIdx must be adjacent!"
+  )
 
-  io.write.req.ready := true.B
-
-  /*
-   * flush logic
-   */
-  // flush standalone set (e.g. flushed by mainPipe before doing re-fetch)
-  when(io.flush.req.map(_.valid).reduce(_ || _)) {
-    (0 until nWays).foreach { w =>
-      validArray(w) := (0 until PortNumber).map { i =>
-        Mux(
-          // check if set `vSetIdx` in way `w` is requested to be flushed by port `i`
-          io.flush.req(i).valid && io.flush.req(i).bits.waymask(w),
-          validArray(w).bitSet(io.flush.req(i).bits.vSetIdx, false.B),
-          validArray(w)
-        )
-      }.reduce(_ & _)
-    }
-  }
+  private val f0_rotator = VecRotate(getInterleavedBankIdx(io.flush.req(0).bits.vSetIdx))
+  private val f0_reqVec  = f0_rotator.rotate(io.flush.req)
+
+  banks.zipWithIndex.foreach { case (b, i) =>
+    b.io.flush.req.valid        := f0_reqVec(i).valid
+    b.io.flush.req.bits.setIdx  := getInterleavedSetIdx(f0_reqVec(i).bits.vSetIdx)
+    b.io.flush.req.bits.waymask := f0_reqVec(i).bits.waymask
 
-  // flush all (e.g. fence.i)
-  when(io.flushAll) {
-    (0 until nWays).foreach(w => validArray(w) := 0.U)
+    b.io.flushAll := io.flushAll
   }
 
   /* *** perf *** */
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMetaInterleavedBank.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMetaInterleavedBank.scala
new file mode 100644
index 00000000000..17fc4d164c5
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMetaInterleavedBank.scala
@@ -0,0 +1,118 @@
+// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
+// Copyright (c) 2020-2021 Peng Cheng Laboratory
+//
+// XiangShan is licensed under Mulan PSL v2.
+// You can use this software according to the terms and conditions of the Mulan PSL v2.
+// You may obtain a copy of Mulan PSL v2 at:
+//          https://license.coscl.org.cn/MulanPSL2
+//
+// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+//
+// See the Mulan PSL v2 for more details.
+
+package xiangshan.frontend.icache
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.mbist.MbistPipeline
+import utility.sram.SplittedSRAMTemplate
+
+class ICacheMetaInterleavedBank(bankIdx: Int)(implicit p: Parameters) extends ICacheModule {
+  class ICacheMetaInterleavedBankIO extends Bundle {
+    class Read extends Bundle {
+      class Req extends Bundle {
+        val setIdx: UInt = UInt(InterleavedSetIdxBits.W)
+      }
+      class Resp extends Bundle {
+        val entries: Vec[Valid[ICacheMetaEntry]] = Vec(nWays, Valid(new ICacheMetaEntry))
+      }
+      val req:  DecoupledIO[Req] = Flipped(DecoupledIO(new Req))
+      val resp: Resp             = Output(new Resp)
+    }
+
+    class Write extends Bundle {
+      class Req extends Bundle {
+        val setIdx:  UInt            = UInt(InterleavedSetIdxBits.W)
+        val waymask: UInt            = UInt(nWays.W)
+        val entry:   ICacheMetaEntry = new ICacheMetaEntry
+      }
+      val req: DecoupledIO[Req] = Flipped(DecoupledIO(new Req))
+    }
+
+    class Flush extends Bundle {
+      class Req extends Bundle {
+        val setIdx:  UInt = UInt(InterleavedSetIdxBits.W)
+        val waymask: UInt = UInt(nWays.W)
+      }
+      val req: Valid[Req] = Flipped(Valid(new Req))
+    }
+
+    val read:     Read  = new Read
+    val write:    Write = new Write
+    val flush:    Flush = new Flush
+    val flushAll: Bool  = Input(Bool())
+  }
+
+  val io: ICacheMetaInterleavedBankIO = IO(new ICacheMetaInterleavedBankIO)
+
+  private val tagArray = Module(new SplittedSRAMTemplate(
+    new ICacheMetaEntry,
+    set = NumInterleavedSet,
+    way = nWays,
+    waySplit = MetaWaySplit,
+    dataSplit = MetaDataSplit,
+    shouldReset = true,
+    singlePort = true,
+    withClockGate = true,
+    hasMbist = hasMbist,
+    hasSramCtl = hasSramCtl,
+    suffix = Option("icache_meta")
+  ))
+  private val mbistPl = MbistPipeline.PlaceMbistPipeline(1, s"MbistPipeICacheTag_bank$bankIdx", hasMbist)
+
+  private val validArray = RegInit(VecInit.fill(NumInterleavedSet)(0.U(nWays.W)))
+
+  /* *** read *** */
+  io.read.req.ready := !io.write.req.valid && !io.flush.req.valid && !io.flushAll && tagArray.io.r.req.ready
+
+  tagArray.io.r.req.valid := io.read.req.valid
+  tagArray.io.r.req.bits.apply(
+    setIdx = io.read.req.bits.setIdx
+  )
+
+  private val readReqReg = RegEnable(io.read.req.bits, 0.U.asTypeOf(io.read.req.bits), io.read.req.fire)
+
+  io.read.resp.entries.zipWithIndex.foreach { case (e, i) =>
+    e.valid := validArray(readReqReg.setIdx)(i)
+    e.bits  := tagArray.io.r.resp.data(i)
+  }
+
+  /* *** write *** */
+  io.write.req.ready := tagArray.io.w.req.ready
+
+  tagArray.io.w.req.valid := io.write.req.valid
+  tagArray.io.w.req.bits.apply(
+    data = io.write.req.bits.entry,
+    setIdx = io.write.req.bits.setIdx,
+    waymask = io.write.req.bits.waymask
+  )
+
+  when(io.write.req.valid) {
+    validArray(io.write.req.bits.setIdx) := validArray(io.write.req.bits.setIdx) | io.write.req.bits.waymask
+  }
+
+  /* *** flush *** */
+  // flush standalone set (e.g. flushed by mainPipe before doing re-fetch)
+  when(io.flush.req.valid) {
+    validArray(io.flush.req.bits.setIdx) := validArray(io.flush.req.bits.setIdx) & (~io.flush.req.bits.waymask).asUInt
+  }
+
+  // flush all (e.g. fence.i)
+  when(io.flushAll) {
+    validArray := 0.U.asTypeOf(validArray)
+  }
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
index 6e629a9214b..43f79c76920 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
@@ -238,14 +238,12 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
     maybeRvcMap = maybeRvcMap,
     vSetIdx = mshrResp.vSetIdx,
     waymask = waymask,
-    bankIdx = mshrResp.vSetIdx(0),
     poison = false.B
   )
   io.dataWrite.req.bits.generate(
     data = respDataReg.asUInt,
     vSetIdx = mshrResp.vSetIdx,
     waymask = waymask,
-    bankIdx = mshrResp.vSetIdx(0),
     poison = false.B
   )
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
index 9dba3d9b254..cc60a6d0fcf 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
@@ -208,32 +208,28 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
     * Receive resp from IMeta and check
     ******************************************************************************
     */
-  private val s1_metaPTags  = fromMeta.tags
-  private val s1_metaValids = fromMeta.entryValid
-
-  private val s1_sramWaymasks = VecInit((0 until PortNumber).map { port =>
-    getWaymask(s1_pTag, s1_metaPTags(port), s1_metaValids(port))
-  })
-
-  private val s1_sramMaybeRvcMap = VecInit((0 until PortNumber).map { port =>
-    Mux1H(s1_sramWaymasks(port), fromMeta.maybeRvcMap(port))
-  })
-
-  // select ecc code
-  /* NOTE:
-   * When ECC check fails, s1_waymasks may be corrupted, so this selected meta_codes may be wrong.
-   * However, we can guarantee that the request sent to the l2 cache and the response to the IFU are both correct,
-   * considering the probability of bit flipping abnormally is very small, consider there's up to 1 bit being wrong:
-   * 1. miss -> fake hit: The wrong bit in s1_waymasks was set to true.B, thus selects the wrong meta_codes,
-   *                      but we can detect this by checking whether `encodeMetaECC(req_pTags) === meta_codes`.
-   * 2. hit -> fake multi-hit: In normal situation, multi-hit never happens, so multi-hit indicates ECC failure,
-   *                           we can detect this by checking whether `PopCount(waymasks) <= 1.U`,
-   *                           and meta_codes is not important in this situation.
-   * 3. hit -> fake miss: We can't detect this, but we can (pre)fetch the correct data from L2 cache, so it's not a problem.
-   * 4. hit -> hit / miss -> miss: ECC failure happens in an irrelevant way, so we don't care about it this time.
-   */
-  private val s1_sramMetaCodes = VecInit((0 until PortNumber).map { port =>
-    Mux1H(s1_sramWaymasks(port), fromMeta.codes(port))
+  private val s1_sramMetaInfo = VecInit(fromMeta.entries.map { portEntries =>
+    val waymask       = getWaymask(s1_pTag, portEntries)
+    val selectedEntry = Mux1H(waymask, portEntries)
+
+    val info = Wire(new MetaInfo)
+    info.waymask     := waymask
+    info.maybeRvcMap := selectedEntry.bits.meta.maybeRvcMap.getOrElse(0.U.asTypeOf(info.maybeRvcMap))
+    // select ecc code
+    /* NOTE:
+     * When ECC check fails, s1_waymasks may be corrupted, so this selected meta_codes may be wrong.
+     * However, we can guarantee that the request sent to the l2 cache and the response to the IFU are both correct,
+     * considering the probability of bit flipping abnormally is very small, consider there's up to 1 bit being wrong:
+     * 1. miss -> fake hit: The wrong bit in s1_waymasks was set to true.B, thus selects the wrong meta_codes,
+     *                      but we can detect this by checking whether `encodeMetaECC(req_pTags) === meta_codes`.
+     * 2. hit -> fake multi-hit: In normal situation, multi-hit never happens, so multi-hit indicates ECC failure,
+     *                           we can detect this by checking whether `PopCount(waymasks) <= 1.U`,
+     *                           and meta_codes is not important in this situation.
+     * 3. hit -> fake miss: We can't detect this, but we can (pre)fetch the correct data from L2 cache, so it's not a problem.
+     * 4. hit -> hit / miss -> miss: ECC failure happens in an irrelevant way, so we don't care about it this time.
+     */
+    info.metaCodes := selectedEntry.bits.code
+    info
   })
 
   /**
@@ -247,34 +243,25 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
     (s0_fireNext || RegNext(s1_needMeta && toMeta.ready)) && s1_doubleline
   ))
   private val s1_mshrValid = fromMiss.valid && !fromMiss.bits.corrupt
-  private val s1_waymasks  = WireInit(VecInit(Seq.fill(PortNumber)(0.U(nWays.W))))
-  private val s1_waymasksReg = VecInit((s1_waymasks zip s1_sramValid).map { case (d, v) =>
-    RegEnable(d, 0.U.asTypeOf(d), v || s1_mshrValid)
-  })
-  private val s1_maybeRvcMap = WireInit(VecInit(Seq.fill(PortNumber)(0.U(MaxInstNumPerBlock.W))))
-  private val s1_maybeRvcMapReg = VecInit((s1_maybeRvcMap zip s1_sramValid).map { case (d, v) =>
-    RegEnable(d, 0.U.asTypeOf(d), v || s1_mshrValid)
-  })
-  private val s1_metaCodes = WireInit(VecInit(Seq.fill(PortNumber)(0.U(MetaEccBits.W))))
-  private val s1_metaCodesReg = VecInit((s1_metaCodes zip s1_sramValid).map { case (d, v) =>
+
+  private val s1_metaInfo = Wire(Vec(PortNumber, new MetaInfo))
+  private val s1_metaInfoReg = VecInit((s1_metaInfo zip s1_sramValid).map { case (d, v) =>
     RegEnable(d, 0.U.asTypeOf(d), v || s1_mshrValid)
   })
 
-  // update waymasks and meta_codes
-  (0 until PortNumber).foreach { i =>
-    val (_, newMask, newMaybeRvcMap, newCode) = updateMetaInfo(
+  // assign metaInfo wire to updated value ((sram or reg) + miss)
+  s1_metaInfo.zipWithIndex.foreach { case (info, i) =>
+    val (_, newInfo) = updateMetaInfo(
       fromMiss,
-      Mux(s1_sramValid(i), s1_sramWaymasks(i), s1_waymasksReg(i)),
       s1_vSetIdx(i),
       s1_pTag,
-      Mux(s1_sramValid(i), s1_sramMaybeRvcMap(i), s1_maybeRvcMapReg(i)),
-      Mux(s1_sramValid(i), s1_sramMetaCodes(i), s1_metaCodesReg(i))
+      Mux(s1_sramValid(i), s1_sramMetaInfo(i), s1_metaInfoReg(i))
     )
-    s1_waymasks(i)    := newMask
-    s1_metaCodes(i)   := newCode
-    s1_maybeRvcMap(i) := newMaybeRvcMap
+    info := newInfo
   }
 
+  private val s1_sramHits = VecInit(s1_metaInfo.map(_.waymask.orR))
+
   /**
     ******************************************************************************
     * send enqueue req to ICacheWayLookup
@@ -287,17 +274,17 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
   ) && !s1_flush && !fromMiss.valid && !s1_isSoftPrefetch // do not enqueue soft prefetch
   toWayLookup.bits.ftqIdx            := s1_ftqIdx
   toWayLookup.bits.vSetIdx           := s1_vSetIdx
-  toWayLookup.bits.waymask           := s1_waymasks
+  toWayLookup.bits.waymask           := VecInit(s1_metaInfo.map(_.waymask))
   toWayLookup.bits.pTag              := s1_pTag
-  toWayLookup.bits.maybeRvcMap       := s1_maybeRvcMap
+  toWayLookup.bits.maybeRvcMap       := VecInit(s1_metaInfo.map(_.maybeRvcMap))
   toWayLookup.bits.gpAddr            := s1_gpAddr(PAddrBitsMax - 1, 0)
   toWayLookup.bits.isForVSnonLeafPTE := s1_isForVSnonLeafPTE
-  toWayLookup.bits.metaCodes         := s1_metaCodes
+  toWayLookup.bits.metaCodes         := VecInit(s1_metaInfo.map(_.metaCodes))
   toWayLookup.bits.itlbException     := s1_itlbException
   toWayLookup.bits.itlbPbmt          := s1_itlbPbmt
 
   when(toWayLookup.fire) {
-    val waymasksVec = s1_waymasks.map(_.asTypeOf(Vec(nWays, Bool())))
+    val waymasksVec = s1_metaInfo.map(_.waymask.asTypeOf(Vec(nWays, Bool())))
     assert(
       PopCount(waymasksVec(0)) <= 1.U && (PopCount(waymasksVec(1)) <= 1.U || !s1_doubleline),
       "Multi-hit:\nport0: count=%d pTag=0x%x vSet=0x%x vAddr=0x%x\nport1: count=%d pTag=0x%x vSet=0x%x vAddr=0x%x",
@@ -409,31 +396,11 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
   private val s2_pTag           = RegEnable(s1_pTag, 0.U.asTypeOf(s1_pTag), s1_realFire)
   private val s2_exception =
     RegEnable(s1_exceptionOut, 0.U.asTypeOf(s1_exceptionOut), s1_realFire) // includes itlb/pmp exception
-  // disabled for timing consideration
-// private val s2_exceptionIn =
-//   RegEnable(s1_exceptionOut, 0.U.asTypeOf(s1_exceptionOut), s1_realFire)
   private val s2_isMmio   = RegEnable(s1_isMmio, 0.U.asTypeOf(s1_isMmio), s1_realFire)
-  private val s2_waymasks = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_realFire)
-  // disabled for timing consideration
-// private val s2_metaCodes   = RegEnable(s1_metaCodes, 0.U.asTypeOf(s1_metaCodes), s1_realFire)
+  private val s2_sramHits = RegEnable(s1_sramHits, 0.U.asTypeOf(s1_sramHits), s1_realFire)
 
   private val s2_vSetIdx = s2_vAddr.map(get_idx)
 
-  // disabled for timing consideration
-//  // do metaArray ECC check
-//  val s2_metaCorrupt = VecInit((s2_pTags zip s2_metaCodes zip s2_waymasks).map{ case ((meta, code), waymask) =>
-//    val hit_num = PopCount(waymask)
-//    // NOTE: if not hit, encodeMetaECC(meta) =/= code can also be true, but we don't care about it
-//    (encodeMetaECC(meta) =/= code && hit_num === 1.U) ||  // hit one way, but parity code does not match, ECC failure
-//      hit_num > 1.U                                       // hit multi-way, must be an ECC failure
-//  })
-//
-//  // generate exception
-//  val s2_metaException = VecInit(s2_metaCorrupt.map(ExceptionType.fromECC(io.ecc_enable, _)))
-//
-//  // merge meta exception and itlb/pmp exception
-//  val s2_exception = ExceptionType.merge(s2_exceptionIn, s2_metaException)
-
   /**
     ******************************************************************************
     * Monitor the requests from missUnit to write to SRAM
@@ -451,8 +418,7 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
     )
   )
 
-  private val s2_sramHits = s2_waymasks.map(_.orR)
-  private val s2_hits     = VecInit((0 until PortNumber).map(i => s2_mshrHits(i) || s2_sramHits(i)))
+  private val s2_hits = VecInit((0 until PortNumber).map(i => s2_mshrHits(i) || s2_sramHits(i)))
 
   // do prefetch if not hit and no exception/mmio
   private val s2_miss = VecInit((0 until PortNumber).map { i =>
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
index 31725d9e413..0d0c6096901 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
@@ -105,18 +105,14 @@ class ICacheWayLookup(implicit p: Parameters) extends ICacheModule
   /* *** update *** */
   private val entryUpdate = VecInit(entries.map { entry =>
     (0 until PortNumber).map { i =>
-      val (updated, newMask, newMaybeRvcMap, newCode) = updateMetaInfo(
+      val (updated, newInfo) = updateMetaInfo(
         io.update,
-        entry.waymask(i),
         entry.vSetIdx(i),
         entry.pTag,
-        entry.maybeRvcMap(i),
-        entry.metaCodes(i)
+        entry.getMetaInfo(i)
       )
       when(updated) {
-        entry.waymask(i)     := newMask
-        entry.maybeRvcMap(i) := newMaybeRvcMap
-        entry.metaCodes(i)   := newCode
+        entry.updateMetaInfo(i, newInfo)
       }
       updated
     }.reduce(_ || _)
diff --git a/src/main/scala/xiangshan/frontend/icache/Parameters.scala b/src/main/scala/xiangshan/frontend/icache/Parameters.scala
index de30bf74d84..698572fe120 100644
--- a/src/main/scala/xiangshan/frontend/icache/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/icache/Parameters.scala
@@ -31,7 +31,6 @@ case class ICacheParameters(
     rowBits:    Int = 64, // per bank, by default we split 64B cacheline into 8 banks, so each bank has 8B (64b)
     blockBytes: Int = 64, // cacheline size
     /* *** ICache specific *** */
-    PortNumber: Int = 2, // TODO: remove this when dropping cross-page fetch
     // replacer
     Replacer: String = "setplru", // "random", "setlru", "setplru"
     // missUnit
@@ -45,6 +44,11 @@ case class ICacheParameters(
     MetaEcc:     String = "parity",  // "none", "identity", "parity", "sec", "secded"
     DataEcc:     String = "parity",  // "none", "identity", "parity", "sec", "secded"
     DataEccUnit: Option[Int] = None, // if None, use blockBytes
+    // meta array
+    // by default, odd and even meta entries are stored in different banks to allow concurrent access
+    NumInterleavedBank: Int = 2,
+    MetaWaySplit:       Int = 2, // for ppa optimization, split metadata into several SRAMs by way
+    MetaDataSplit:      Int = 1, // for ppa optimization, split metadata into several SRAMs by data
     // data array
     // by default we store 64data + 1parity + 1padding, this is better than 65bits (from physical design)
     DataPaddingBits: Int = 1,
@@ -57,14 +61,27 @@ case class ICacheParameters(
     ForceMetaEccFail: Boolean = false,
     ForceDataEccFail: Boolean = false
 ) extends L1CacheParameters {
+  // this is used to prevent magic number in the code, DO NOT CHANGE, it won't work other than 2
+  // explanation: we allow concurrent access to consecutive cachelines
+  def PortNumber: Int = 2
+
   require(isPow2(nSets), s"nSets($nSets) must be pow2")
   require(isPow2(nWays), s"nWays($nWays) must be pow2")
   require(isPow2(rowBits), s"rowBits($rowBits) must be pow2")
   require(isPow2(blockBytes), s"blockBytes($blockBytes) must be pow2")
   require(rowBits < blockBytes * 8, s"rowBits($rowBits) must be less than blockBits(${blockBytes * 8})")
+
+  // Interleaved bank number must be pow2, and smaller than nSets, and greater than possible concurrent access number
+  require(isPow2(NumInterleavedBank), s"NumInterleavedBank($NumInterleavedBank) must be pow2")
+  require(NumInterleavedBank <= nSets, s"NumInterleavedBank($NumInterleavedBank) must be <= nSets($nSets)")
+  require(
+    NumInterleavedBank >= PortNumber,
+    s"NumInterleavedBank($NumInterleavedBank) must be >= 2 to allow concurrent access to consecutive cachelines"
+  )
 }
 
-trait HasICacheParameters extends HasFrontendParameters with HasL1CacheParameters {
+trait HasICacheParameters extends HasFrontendParameters // scalastyle:ignore number.of.methods
+    with HasL1CacheParameters {
   def icacheParameters: ICacheParameters = frontendParameters.icacheParameters
 
   // implement cacheParams to use HasL1CacheParameters trait
@@ -106,6 +123,14 @@ trait HasICacheParameters extends HasFrontendParameters with HasL1CacheParameter
   def MetaEntryBits: Int    = MetaCode.width(MetaBits)
   def MetaEccBits:   Int    = MetaEntryBits - MetaBits
 
+  def NumInterleavedBank:     Int = icacheParameters.NumInterleavedBank
+  def NumInterleavedSet:      Int = nSets / NumInterleavedBank
+  def InterleavedBankIdxBits: Int = log2Ceil(NumInterleavedBank)
+  def InterleavedSetIdxBits:  Int = idxBits - InterleavedBankIdxBits
+
+  def MetaWaySplit:  Int = icacheParameters.MetaWaySplit
+  def MetaDataSplit: Int = icacheParameters.MetaDataSplit
+
   // dataArray w/ parity
   def DataEcc:         String = icacheParameters.DataEcc
   def DataEccUnit:     Int    = icacheParameters.DataEccUnit.getOrElse(blockBytes)
@@ -116,8 +141,7 @@ trait HasICacheParameters extends HasFrontendParameters with HasL1CacheParameter
   def DataEccSegments: Int    = math.ceil(ICacheDataBits / DataEccUnit).toInt
   def DataEccBitsPerSegment: Int = DataCode.width(DataEccUnit) - DataEccUnit // ecc bits per segment
   def DataEccBits:           Int = DataEccSegments * DataEccBitsPerSegment
-  def DataEntryBits:         Int = ICacheDataBits + DataEccBits
-  def DataSramWidth:         Int = DataEntryBits + DataPaddingBits
+  def DataSramWidth:         Int = ICacheDataBits + DataEccBits + DataPaddingBits
 
   // submodule enable
   def EnableCtrlUnit: Boolean = icacheParameters.EnableCtrlUnit
diff --git a/src/main/scala/xiangshan/frontend/icache/SRAMTemplateWithFixedWidth.scala b/src/main/scala/xiangshan/frontend/icache/SRAMTemplateWithFixedWidth.scala
deleted file mode 100644
index 68a46124dc7..00000000000
--- a/src/main/scala/xiangshan/frontend/icache/SRAMTemplateWithFixedWidth.scala
+++ /dev/null
@@ -1,92 +0,0 @@
-// Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
-// Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
-// Copyright (c) 2020-2021 Peng Cheng Laboratory
-//
-// XiangShan is licensed under Mulan PSL v2.
-// You can use this software according to the terms and conditions of the Mulan PSL v2.
-// You may obtain a copy of Mulan PSL v2 at:
-//          https://license.coscl.org.cn/MulanPSL2
-//
-// THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-// EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-// MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-//
-// See the Mulan PSL v2 for more details.
-
-package xiangshan.frontend.icache
-
-import chisel3._
-import utility.sram.SRAMReadBus
-import utility.sram.SRAMTemplate
-import utility.sram.SRAMWriteBus
-
-// FIXME: should move to utility
-
-// Automatically partition the SRAM based on the width of the data and the desired width.
-// final SRAM width = width * way
-class SRAMTemplateWithFixedWidth[T <: Data](
-    gen:           T,
-    set:           Int,
-    width:         Int,
-    way:           Int = 1,
-    shouldReset:   Boolean = false,
-    holdRead:      Boolean = false,
-    singlePort:    Boolean = false,
-    bypassWrite:   Boolean = false,
-    withClockGate: Boolean = false,
-    hasMbist:      Boolean = false,
-    hasSramCtl:    Boolean = false
-) extends Module {
-  class SRAMTemplateWithFixedWidthIO[TT <: Data](gen: TT, set: Int, way: Int) extends Bundle {
-    val r: SRAMReadBus[TT]  = Flipped(new SRAMReadBus(gen, set, way))
-    val w: SRAMWriteBus[TT] = Flipped(new SRAMWriteBus(gen, set, way))
-  }
-  val io: SRAMTemplateWithFixedWidthIO[T] = IO(new SRAMTemplateWithFixedWidthIO(gen, set, way))
-
-  private def dataBits  = gen.getWidth
-  private def bankNum   = math.ceil(dataBits.toDouble / width.toDouble).toInt
-  private def totalBits = bankNum * width
-
-  private val wordType = UInt(width.W)
-  private val writeDatas = (0 until bankNum).map { bank =>
-    VecInit((0 until way).map { i =>
-      io.w.req.bits.data(i).asTypeOf(UInt(totalBits.W)).asTypeOf(Vec(bankNum, wordType))(bank)
-    })
-  }
-
-  private val srams = (0 until bankNum) map { bank =>
-    val sramBank = Module(new SRAMTemplate(
-      wordType,
-      set = set,
-      way = way,
-      shouldReset = shouldReset,
-      holdRead = holdRead,
-      singlePort = singlePort,
-      bypassWrite = bypassWrite,
-      withClockGate = withClockGate,
-      hasMbist = hasMbist,
-      hasSramCtl = hasSramCtl
-    ))
-    // read req
-    sramBank.io.r.req.valid       := io.r.req.valid
-    sramBank.io.r.req.bits.setIdx := io.r.req.bits.setIdx
-
-    // write req
-    sramBank.io.w.req.valid       := io.w.req.valid
-    sramBank.io.w.req.bits.setIdx := io.w.req.bits.setIdx
-    sramBank.io.w.req.bits.data   := writeDatas(bank)
-    sramBank.io.w.req.bits.waymask.foreach(_ := io.w.req.bits.waymask.get)
-
-    sramBank
-  }
-
-  io.r.req.ready := !io.w.req.valid
-  (0 until way).foreach { i =>
-    io.r.resp.data(i) := VecInit((0 until bankNum).map(bank =>
-      srams(bank).io.r.resp.data(i)
-    )).asTypeOf(UInt(totalBits.W))(dataBits - 1, 0).asTypeOf(gen.cloneType)
-  }
-
-  io.r.req.ready := srams.head.io.r.req.ready
-  io.w.req.ready := srams.head.io.w.req.ready
-}
```
