# Commit Log
- Issue: #5012
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5012
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5012
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5012
- Changed files: 6
- Additions: 175
- Deletions: 205

## Files
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/BackendParams.scala`
- `src/main/scala/xiangshan/frontend/ifu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`
- `src/main/scala/xiangshan/frontend/ifu/InstrBoundary.scala`
- `src/main/scala/xiangshan/frontend/ifu/PreDecodeBoundary.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index a2f98dd9894..7c78b9b948f 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -114,7 +114,7 @@ case class XSCoreParameters
   StoreQueueNWriteBanks: Int = 8, // NOTE: make sure that StoreQueueSize is divided by StoreQueueNWriteBanks
   StoreQueueForwardWithMask: Boolean = true,
   VlsQueueSize: Int = 8,
-  RobSize: Int = 160,
+  RobSize: Int = 224,
   RabSize: Int = 256,
   VTypeBufferSize: Int = 64, // used to reorder vtype
   IssueQueueSize: Int = 20,
diff --git a/src/main/scala/xiangshan/backend/BackendParams.scala b/src/main/scala/xiangshan/backend/BackendParams.scala
index d036450f601..81162d09d32 100644
--- a/src/main/scala/xiangshan/backend/BackendParams.scala
+++ b/src/main/scala/xiangshan/backend/BackendParams.scala
@@ -41,7 +41,7 @@ case class BackendParams(
 
   def debugEn(implicit p: Parameters): Boolean = p(DebugOptionsKey).EnableDifftest
 
-  def robCompressEn: Boolean = true
+  def robCompressEn: Boolean = false
 
   def basicDebugEn(implicit p: Parameters): Boolean = p(DebugOptionsKey).AlwaysBasicDiff || debugEn
 
diff --git a/src/main/scala/xiangshan/frontend/ifu/Bundles.scala b/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
index cc3bf9f696b..5b564fc28b8 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
@@ -68,7 +68,7 @@ class FetchBlockInfo(implicit p: Parameters) extends IfuBundle {
   val startVAddr:     PrunedAddr  = PrunedAddr(VAddrBits)
   val target:         PrunedAddr  = PrunedAddr(VAddrBits)
   val instrRange:     UInt        = UInt(FetchBlockInstNum.W)
-  val rawInstrValid:  UInt        = UInt(FetchBlockInstNum.W)
+  val rawInstrEndVec: UInt        = UInt(FetchBlockInstNum.W)
   val pcHigh:         UInt        = UInt((VAddrBits - PcCutPoint).W)
   val pcHighPlus1:    UInt        = UInt((VAddrBits - PcCutPoint).W)
   val fetchSize:      UInt        = UInt(log2Ceil(FetchBlockInstNum + 1).W)
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index 15d71f7b8cd..a377cb29c6b 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -101,12 +101,13 @@ class Ifu(implicit p: Parameters) extends IfuModule
   val io: IfuIO = IO(new IfuIO)
 
   // submodule
-  private val preDecoder       = Module(new PreDecode)
-  private val preDecodeBounder = Module(new PreDecodeBoundary)
-  private val predChecker      = Module(new PredChecker)
-  private val frontendTrigger  = Module(new FrontendTrigger)
-  private val rvcExpanders     = Seq.fill(IBufferEnqueueWidth)(Module(new RvcExpander))
-  private val mmioRvcExpander  = Module(new RvcExpander)
+
+  private val preDecoder      = Module(new PreDecode)
+  private val instrBoundary   = Module(new InstrBoundary)
+  private val predChecker     = Module(new PredChecker)
+  private val frontendTrigger = Module(new FrontendTrigger)
+  private val rvcExpanders    = Seq.fill(IBufferEnqueueWidth)(Module(new RvcExpander))
+  private val mmioRvcExpander = Module(new RvcExpander)
 
   // alias
   private val (toFtq, fromFtq)              = (io.toFtq, io.fromFtq)
@@ -344,16 +345,19 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
   private val s2_rawData  = fromICache.bits.data
   private val s2_perfInfo = io.fromICache.perf
-  preDecodeBounder.io.req.valid                  := fromICache.valid
-  preDecodeBounder.io.req.bits.instrRange        := s2_totalInstrRange.asTypeOf(Vec(FetchBlockInstNum, Bool()))
-  preDecodeBounder.io.req.bits.firstEndPos       := s2_firstEndPos
-  preDecodeBounder.io.req.bits.endPos            := s2_totalEndPos
-  preDecodeBounder.io.req.bits.prevLastIsHalfRvi := s2_prevLastIsHalfRvi
-  preDecodeBounder.io.req.bits.cacheData :=
-    (Cat(s2_rawData, s2_rawData) >> Cat(s2_ftqFetch(0).startVAddr(5, 0), 0.U(3.W))).asUInt
 
-  private val s2_firstFetchEndIsHalf = preDecodeBounder.io.resp.bits.isFirstLastHalfRvi
-  private val s2_fetchEndIsHalf      = preDecodeBounder.io.resp.bits.isLastHalfRvi
+  instrBoundary.io.req.valid                 := s2_valid && fromICache.valid
+  instrBoundary.io.req.instrRange            := s2_totalInstrRange.asTypeOf(Vec(FetchBlockInstNum, Bool()))
+  instrBoundary.io.req.firstFetchBlockEndPos := s2_firstEndPos
+  instrBoundary.io.req.endPos                := s2_totalEndPos
+  instrBoundary.io.req.firstInstrIsHalfRvi   := s2_prevLastIsHalfRvi
+  instrBoundary.io.req.cacheData := (Cat(s2_rawData, s2_rawData) >> Cat(
+    s2_ftqFetch(0).startVAddr(5, 0),
+    0.U(3.W)
+  )).asUInt
+
+  private val s2_firstFetchEndIsHalf = instrBoundary.io.resp.firstFetchBlockLastInstrIsHalfRvi
+  private val s2_fetchEndIsHalf      = instrBoundary.io.resp.lastInstrIsHalfRvi
 
   private val wbStage2Check = Wire(Vec(FetchPorts, new FinalPredCheckResult))
 
@@ -371,8 +375,12 @@ class Ifu(implicit p: Parameters) extends IfuModule
 // rawInstrValid(i) and instrCountBeforeCurrent(i) also handle instructions
 // spanning across prediction blocks. This design aligns with the logic
 // used for s3_prevLastHalfData calculation.
-  private val rawInstrValid = preDecodeBounder.io.resp.bits.instrValid
-  private val rawIsRvc      = preDecodeBounder.io.resp.bits.isRvc
+  private val dealInstrValid = Wire(Vec(FetchBlockInstNum, Bool()))
+  dealInstrValid    := instrBoundary.io.resp.instrValid
+  dealInstrValid(0) := instrBoundary.io.resp.instrValid(0) | s2_prevLastIsHalfRvi
+
+  private val rawInstrEndVec = instrBoundary.io.resp.instrEndVec
+  private val rawIsRvc       = instrBoundary.io.resp.isRvc
 
   /* *****************************************************************************
    * instrCountBeforeCurrent(i), not include rawInstrValid(i)
@@ -380,9 +388,9 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val instrCountBeforeCurrent =
     WireDefault(VecInit.fill(FetchBlockInstNum + 1)(0.U(log2Ceil(FetchBlockInstNum + 1).W)))
   for (i <- 0 until FetchBlockInstNum) {
-    instrCountBeforeCurrent(i) := PopCount(rawInstrValid.take(i))
+    instrCountBeforeCurrent(i) := PopCount(dealInstrValid.take(i))
   }
-  instrCountBeforeCurrent(FetchBlockInstNum) := PopCount(rawInstrValid)
+  instrCountBeforeCurrent(FetchBlockInstNum) := PopCount(dealInstrValid)
 
   private val instrIndexEntry = Wire(Vec(FetchBlockInstNum, new InstrIndexEntry))
   private val fetchBlockSelect =
@@ -402,6 +410,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
     )
   )
 
+  // FIXME: This is wrong when 2-taken is enabled
   private val twoFetchBlockIndex = VecInit.tabulate(FetchBlockInstNum)(i =>
     Mux(s2_fetchSize(0) > i.U, s2_fetchBlockIndex(0)(i), s2_fetchBlockIndex(1)(i))
   )
@@ -410,6 +419,15 @@ class Ifu(implicit p: Parameters) extends IfuModule
     Mux(s2_fetchSize(0) > i.U, s2_fetchPcLowerResult(0)(i), s2_fetchPcLowerResult(1)(i))
   )
 
+  // FIXME: This is wrong when 2-taken is enabled
+  private val twoFetchIdentifiedCfi = VecInit.tabulate(FetchBlockInstNum) { i =>
+    // This is a dirty hack, make sure it's correct.
+    val identifiedCfi = s2_ftqFetch(0).identifiedCfi
+    if (i == 0) Mux(s2_prevLastIsHalfRvi | rawIsRvc(0), identifiedCfi(0), identifiedCfi(1))
+    else if (i < FetchBlockInstNum - 1) Mux(rawIsRvc(i), identifiedCfi(i), identifiedCfi(i + 1))
+    else identifiedCfi(i)
+  }
+
   private val s2_rawPcLowerResult = twoFetchPcLowerResult
 
   private val instrSelectLowIndex   = WireDefault(VecInit.fill(FetchBlockInstNum)(true.B))
@@ -421,7 +439,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
       val instrRange = idx until Math.min(2 * idx + 2, FetchBlockInstNum)
 
       val validOH = instrRange.map {
-        i => rawInstrValid(i) & (instrCountBeforeCurrent(i) === idx.U)
+        i => dealInstrValid(i) & (instrCountBeforeCurrent(i) === idx.U)
       }
 
       val index         = instrRange.map(twoFetchBlockIndex(_))
@@ -430,7 +448,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
       val isRvc         = instrRange.map(rawIsRvc(_))
       val instrOffset   = instrRange.map(i => Mux(rawIsRvc(i), i.U, (i + 1).U))
       // FIXME: This is wrong when 2-taken is enabled
-      val identifiedCfi = instrRange.map(s2_ftqFetch(0).identifiedCfi(_))
+      val identifiedCfi = instrRange.map(twoFetchIdentifiedCfi(_))
 
       instrIndex.valid           := validOH.reduce(_ || _)
       instrIndex.value           := Mux1H(validOH, index)
@@ -445,7 +463,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s2_fetchTakenIdx = VecInit((0 until FetchPorts).map { i =>
     val b = Wire(new Valid(UInt(FetchBlockInstOffsetWidth.W)))
     b.valid := s2_takenCfiOffset(i).valid
-    b.bits  := PopCount(rawInstrValid.asUInt & s2_instrRange(i)) - 1.U
+    // This is the main reason for using Start — it makes index calculation easier when handling lastRvi.
+    b.bits := PopCount(dealInstrValid.asUInt & s2_instrRange(i)) - 1.U
     b
   })
   s2_fetchTakenIdx(0).valid := s2_takenCfiOffset(0).valid && s2_firstValid
@@ -456,8 +475,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
     b.ftqIdx       := s2_ftqFetch(i).ftqIdx
     b.doubleline   := s2_doubleline(i)
     b.predTakenIdx := s2_fetchTakenIdx(i)
-    b.invalidTaken :=
-      rawInstrValid(s2_takenCfiOffset(i).bits) && !rawIsRvc(s2_takenCfiOffset(i).bits) && s2_takenCfiOffset(i).valid
+    // This is the main reason for using End — it makes invalidTaken calculation easier when handling lastRvi.
+    b.invalidTaken         := !rawInstrEndVec(s2_takenCfiOffset(i).bits) && s2_takenCfiOffset(i).valid
     b.takenCfiOffset.valid := s2_takenCfiOffset(i).valid
     b.takenCfiOffset.bits  := s2_takenCfiOffset(i).bits
     b.instrRange           := s2_instrRange(i)
@@ -466,7 +485,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
     b.startVAddr           := s2_ftqFetch(i).startVAddr
     b.target               := s2_ftqFetch(i).nextStartVAddr
     b.fetchSize            := s2_fetchSize(i)
-    b.rawInstrValid        := rawInstrValid.asUInt & s2_instrRange(i)
+    b.rawInstrEndVec       := rawInstrEndVec.asUInt & s2_instrRange(i)
     b.identifiedCfi        := s2_ftqFetch(i).identifiedCfi
     b
   })
@@ -479,17 +498,13 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
   // After completing the adjustment of a half prediction block, the instruction
   // valid signals at the end and beginning need to be updated.
-  s2_fetchBlock(0).rawInstrValid := (rawInstrValid.asUInt & s2_instrRange(0)) &
-    Mux(s2_firstFetchEndIsHalf, s2_firstMaskEndPos, Fill(FetchBlockInstNum, 1.U(1.W)))
-  s2_fetchBlock(1).rawInstrValid := (rawInstrValid.asUInt >> s2_fetchSize(0)).asUInt & s2_instrRange(1) &
-    Mux(s2_fetchEndIsHalf, s2_secondMaskEndPos, Fill(FetchBlockInstNum, 1.U(1.W)))
+  s2_fetchBlock(0).rawInstrEndVec := rawInstrEndVec.asUInt & s2_instrRange(0)
+  s2_fetchBlock(1).rawInstrEndVec := (rawInstrEndVec.asUInt >> s2_fetchSize(0)).asUInt & s2_instrRange(1)
   private val s2_rawFirstData         = s2_rawData
   private val s2_rawSecondData        = 0.U((ICacheLineBytes * 8).W)
   private val s2_rawFirstDataDupWire  = VecInit(Seq.fill(FetchPorts)(s2_rawFirstData))
   private val s2_rawSecondDataDupWire = VecInit(Seq.fill(FetchPorts)(s2_rawSecondData))
   private val s2_firstEndIdx          = s2_fetchTakenIdx(0).bits
-  private val s2_realRawInstrValid =
-    Mux(s2_fetchEndIsHalf, rawInstrValid.asUInt & s2_totalMaskEndPos, rawInstrValid.asUInt)
   // Special case for MMIO:
   // If two fetches occur and the first is non-MMIO while the second is MMIO,
   // delay the second fetch by one cycle to split into a one-fetch.
@@ -511,11 +526,11 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s3_instrIndex       = RegEnable(instrIndexEntry, s2_fire)
   private val s3_selectFetchBlock = RegEnable(instrSelectFetchBlock, s2_fire)
   private val s3_instrIsRvc       = RegEnable(s2_instrIsRvc, s2_fire)
-  private val s3_instrCount       = RegEnable(PopCount(s2_realRawInstrValid), s2_fire)
-  private val s3_instrValid       = RegEnable(UIntToMask(PopCount(s2_realRawInstrValid), FetchBlockInstNum), s2_fire)
+  private val s3_instrCount       = RegEnable(PopCount(rawInstrEndVec), s2_fire)
+  private val s3_instrValid       = RegEnable(UIntToMask(PopCount(rawInstrEndVec), FetchBlockInstNum), s2_fire)
 
   private val s3_rawIndex           = RegEnable(instrCountBeforeCurrent, s2_fire)
-  private val s3_rawInstrValid      = RegEnable(s2_realRawInstrValid, s2_fire)
+  private val s3_rawInstrEndVec     = RegEnable(rawInstrEndVec, s2_fire)
   private val s3_prevLastIsHalfRvi  = RegEnable(s2_prevLastIsHalfRvi, s2_fire)
   private val s3_prevLastHalfData   = RegInit(0.U(16.W))
   private val s3_prevLastHalfPc     = RegInit(0.U.asTypeOf(PrunedAddr(VAddrBits)))
@@ -744,7 +759,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   )
 
   private val s4_alignFoldPc        = RegEnable(s3_alignFoldPc, s3_fire)
-  private val s4_rawInstrValid      = RegEnable(s3_rawInstrValid, s3_fire)
+  private val s4_rawInstrEndVec     = RegEnable(s3_rawInstrEndVec, s3_fire)
   private val s4_prevLastIsHalfRvi  = RegEnable(s3_prevLastIsHalfRvi, s3_fire)
   private val s4_mmioLowerPc        = RegEnable(s3_alignInstrPcLower(s3_alignShiftNum), s3_fire)
   private val s4_alignBlockStartPos = RegEnable(s3_alignBlockStartPos, s3_fire)
@@ -1183,22 +1198,22 @@ class Ifu(implicit p: Parameters) extends IfuModule
   // Therefore, this part of the logic will not be optimized further and will be removed later.
   private val firstRawPds  = WireDefault(VecInit.fill(FetchBlockInstNum)(0.U.asTypeOf(new PreDecodeInfo)))
   private val secondRawPds = WireDefault(VecInit.fill(FetchBlockInstNum)(0.U.asTypeOf(new PreDecodeInfo)))
-  firstRawPds.zipWithIndex.foreach {
-    case (rawPd, i) =>
-      rawPd := Mux(
-        s4_rawInstrValid(i),
-        s4_alignPds(s4_rawIndex(i) + s4_prevIBufEnqPtr.value(1, 0)),
-        0.U.asTypeOf(new PreDecodeInfo)
-      )
-  }
-  secondRawPds.zipWithIndex.foreach {
-    case (rawPd, i) =>
-      rawPd := Mux(
-        s4_rawInstrValid(i.U + s4_fetchBlock(0).fetchSize),
-        s4_alignPds(s4_rawIndex(i.U + s4_fetchBlock(0).fetchSize) + s4_prevIBufEnqPtr.value(1, 0)),
-        0.U.asTypeOf(new PreDecodeInfo)
-      )
-  }
+  // firstRawPds.zipWithIndex.foreach {
+  //   case (rawPd, i) =>
+  //     rawPd := Mux(
+  //       s4_rawInstrValid(i),
+  //       s4_alignPds(s4_rawIndex(i) + s4_prevIBufEnqPtr.value(1, 0)),
+  //       0.U.asTypeOf(new PreDecodeInfo)
+  //     )
+  // }
+  // secondRawPds.zipWithIndex.foreach {
+  //   case (rawPd, i) =>
+  //     rawPd := Mux(
+  //       s4_rawInstrValid(i.U + s4_fetchBlock(0).fetchSize),
+  //       s4_alignPds(s4_rawIndex(i.U + s4_fetchBlock(0).fetchSize) + s4_prevIBufEnqPtr.value(1, 0)),
+  //       0.U.asTypeOf(new PreDecodeInfo)
+  //     )
+  // }
 
   private val wbEnable              = RegNext(s3_fire && !s3_flush) && !s4_reqIsMmio && !s4_flush
   private val wbValid               = RegNext(wbEnable, init = false.B)
diff --git a/src/main/scala/xiangshan/frontend/ifu/InstrBoundary.scala b/src/main/scala/xiangshan/frontend/ifu/InstrBoundary.scala
new file mode 100644
index 00000000000..f51d1fa2d5f
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/ifu/InstrBoundary.scala
@@ -0,0 +1,106 @@
+// Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+// Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
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
+package xiangshan.frontend.ifu
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.XSError
+
+class InstrBoundary(implicit p: Parameters) extends IfuModule with PreDecodeHelper {
+  class InstrBoundaryIO(implicit p: Parameters) extends IfuBundle {
+    class InstrBoundaryReq(implicit p: Parameters) extends IfuBundle {
+      val valid:      Bool      = Bool()
+      val instrRange: Vec[Bool] = Vec(FetchBlockInstNum, Bool())
+      // FIXME: magic number 512
+      val cacheData:             UInt = UInt((FetchBlockSize * 8).W)
+      val firstInstrIsHalfRvi:   Bool = Bool()
+      val firstFetchBlockEndPos: UInt = UInt(log2Ceil(FetchBlockInstNum).W)
+      val endPos:                UInt = UInt(log2Ceil(FetchBlockInstNum).W)
+    }
+    class InstrBoundaryResp(implicit p: Parameters) extends IfuBundle {
+      val instrValid:                        Vec[Bool] = Vec(FetchBlockInstNum, Bool())
+      val instrEndVec:                       Vec[Bool] = Vec(FetchBlockInstNum, Bool())
+      val isRvc:                             Vec[Bool] = Vec(FetchBlockInstNum, Bool())
+      val firstFetchBlockLastInstrIsHalfRvi: Bool      = Bool()
+      val lastInstrIsHalfRvi:                Bool      = Bool()
+    }
+
+    val req:  InstrBoundaryReq  = Flipped(new InstrBoundaryReq)
+    val resp: InstrBoundaryResp = new InstrBoundaryResp
+  }
+  val io: InstrBoundaryIO = IO(new InstrBoundaryIO)
+
+  // FIXME: magic number 32, shoule be FetBlockInstNum after it changes to 32
+  private val data = io.req.cacheData.asTypeOf(Vec(FetchBlockInstNum, UInt((instBytes * 8).W)))
+  private val rawInstrs = VecInit((0 until FetchBlockInstNum).map(i =>
+    if (i == FetchBlockInstNum - 1) data(i) else Cat(data(i + 1), data(i))
+  ))
+  private val isRvc = VecInit(rawInstrs.map(isRVC))
+
+  // We compute the boundaries of instructions in the first half of the fetch block directly, and compute the boundaries
+  // of instructions in the latter half in two cases in parallel. Then we can choose the correct case according to
+  // whether the last instruction in the first half is a 16-bit instruction or not.
+  private val boundary            = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(false.B)))
+  private val latterHalfBoundary1 = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(false.B)))
+  private val latterHalfBoundary2 = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(false.B)))
+
+  private def generateBoundary(
+      boundary:            Vec[Bool],
+      start:               Int,
+      end:                 Int,
+      firstInstrIsHalfRvi: Bool
+  ): Unit = {
+    require(HasCExtension, "C Extension can not be disabled in XiangShan")
+    for (i <- start until end) {
+      boundary(i) := {
+        if (i == start) !firstInstrIsHalfRvi else !boundary(i - 1) || isRvc(i - 1)
+      }
+    }
+  }
+
+  generateBoundary(boundary, 0, FetchBlockInstNum / 2, io.req.firstInstrIsHalfRvi)
+  generateBoundary(latterHalfBoundary1, FetchBlockInstNum / 2, FetchBlockInstNum, true.B)
+  generateBoundary(latterHalfBoundary2, FetchBlockInstNum / 2, FetchBlockInstNum, false.B)
+
+  for (i <- FetchBlockInstNum / 2 until FetchBlockInstNum) {
+    boundary(i) := Mux(
+      boundary(FetchBlockInstNum / 2 - 1) && !isRvc(FetchBlockInstNum / 2 - 1),
+      latterHalfBoundary1(i),
+      latterHalfBoundary2(i)
+    )
+  }
+
+  io.resp.instrValid := boundary.zip(io.req.instrRange).map { case (boundary, range) =>
+    boundary && range
+  }
+  io.resp.instrEndVec := boundary.zip(isRvc).zip(io.req.instrRange).map { case ((boundary, isRvc), range) =>
+    (!boundary || (boundary && isRvc)) && range
+  }
+  io.resp.isRvc := boundary.zip(isRvc).zip(io.req.instrRange).map { case ((boundary, isRvc), range) =>
+    boundary && isRvc && range
+  }
+  io.resp.firstFetchBlockLastInstrIsHalfRvi :=
+    boundary(io.req.firstFetchBlockEndPos) && !isRvc(io.req.firstFetchBlockEndPos)
+  io.resp.lastInstrIsHalfRvi := boundary(io.req.endPos) && !isRvc(io.req.endPos)
+
+  // For differential test only. Will be optimized out in release
+  private val boundDiff = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(false.B)))
+  generateBoundary(boundDiff, 0, FetchBlockInstNum, io.req.firstInstrIsHalfRvi)
+  boundary.zip(boundDiff).foreach {
+    case (a, b) => XSError(io.req.valid && (a =/= b), p"boundary different: $a vs $b\n")
+  }
+}
diff --git a/src/main/scala/xiangshan/frontend/ifu/PreDecodeBoundary.scala b/src/main/scala/xiangshan/frontend/ifu/PreDecodeBoundary.scala
deleted file mode 100644
index 83be4d84301..00000000000
--- a/src/main/scala/xiangshan/frontend/ifu/PreDecodeBoundary.scala
+++ /dev/null
@@ -1,151 +0,0 @@
-// Copyright (c) 2024-2025 Beijing Institute of Open Source Chip (BOSC)
-// Copyright (c) 2020-2025 Institute of Computing Technology, Chinese Academy of Sciences
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
-package xiangshan.frontend.ifu
-
-import chisel3._
-import chisel3.util._
-import org.chipsalliance.cde.config.Parameters
-import utility.XSError
-
-class PreDecodeBoundary(implicit p: Parameters) extends IfuModule with PreDecodeHelper {
-  class PreDecodeBoundIO(implicit p: Parameters) extends IfuBundle {
-    class PreDecodeBoundReq(implicit p: Parameters) extends IfuBundle {
-      val instrRange:        Vec[Bool] = Vec(FetchBlockInstNum, Bool())
-      val cacheData:         UInt      = UInt((FetchBlockSize * 8).W)
-      val prevLastIsHalfRvi: Bool      = Bool()
-      val firstEndPos:       UInt      = UInt(FetchBlockInstOffsetWidth.W)
-      val endPos:            UInt      = UInt(FetchBlockInstOffsetWidth.W)
-    }
-    class PreDecodeBoundResp(implicit p: Parameters) extends IfuBundle {
-      val instrValid:         Vec[Bool] = Vec(FetchBlockInstNum, Bool())
-      val isRvc:              Vec[Bool] = Vec(FetchBlockInstNum, Bool())
-      val isFirstLastHalfRvi: Bool      = Bool()
-      val isLastHalfRvi:      Bool      = Bool()
-    }
-
-    val req:  Valid[PreDecodeBoundReq]  = Flipped(ValidIO(new PreDecodeBoundReq))
-    val resp: Valid[PreDecodeBoundResp] = ValidIO(new PreDecodeBoundResp)
-  }
-  val io: PreDecodeBoundIO = IO(new PreDecodeBoundIO)
-
-  private class BoundInfo extends Bundle {
-    val isStart: Bool = Bool()
-    val isEnd:   Bool = Bool()
-  }
-
-  private val data = io.req.bits.cacheData.asTypeOf(Vec(FetchBlockInstNum, UInt((instBytes * 8).W)))
-  private val rawInsts = VecInit((0 until FetchBlockInstNum).map(i =>
-    if (i == FetchBlockInstNum - 1) data(i) else Cat(data(i + 1), data(i))
-  ))
-
-  // if the former fetch block's last 2 Bytes is a valid end, we need delimitation from data(0)
-  //   we compute the first half directly -> bound(0, FetchBlockInstNum/2-1) is correct
-  private val bound = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(0.U.asTypeOf(new BoundInfo))))
-  //   and compute two cases of the second half in parallel, we can choose later and reduce latency
-  //     - case 1: data(FetchBlockInstNum/2-1) is a valid end, so data(FetchBlockInstNum/2) is a valid start
-  //               -> readBound(FetchBlockInstNum/2, FetchBlockInstNum-1) is correct
-  //     - case 2: data(FetchBlockInstNum/2) is a valid end, so data(FetchBlockInstNum/2+1) is a valid start
-  //               -> readBoundPlus1(FetchBlockInstNum/2, FetchBlockInstNum-1) is correct
-  //
-  //     compute directly: 0 -> 1 -> ... -> 32 => bound(0, FetchBlockInstNum-1)
-  //
-  //     ours:        first half 0  -> 1  -> ... -> 16 ->  |  => bound(0, FetchBlockInstNum/2-1)
-  //                                                       v
-  //           second half case1 17 -> 18 -> ... -> 32 -> Mux => bound(FetchBlockInstNum/2, FetchBlockInstNum-1)
-  //           second half case2 17 -> 18 -> ... -> 32 --/
-  //
-  //   NOTE: we use (FetchBlockInstNum/2, FetchBlockInstNum-1) only, but we still use Vec[FetchBlockInstNum] to simplify
-  private val rearBound      = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(0.U.asTypeOf(new BoundInfo))))
-  private val rearBoundPlus1 = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(0.U.asTypeOf(new BoundInfo))))
-
-  private val currentIsRvc     = VecInit(rawInsts.map(isRVC))
-  private val realCurrentIsRvc = Wire(Vec(FetchBlockInstNum, Bool()))
-  realCurrentIsRvc    := currentIsRvc
-  realCurrentIsRvc(0) := Mux(io.req.bits.prevLastIsHalfRvi, false.B, currentIsRvc(0))
-  def genBound(
-      bound:        Vec[BoundInfo],
-      start:        Int,
-      end:          Int,
-      preIsHalfRvi: Bool = false.B
-  ): Unit = {
-    // when !HasCExtension, data is stepped by 4, and every data is a valid instruction start
-    // otherwise, data is stepped by 2, and data on pc+i*2 is:
-    //   - a valid instruction start iff data on pc+(i-1)*2 is a valid instruction end
-    //     - when i == startPoint, we need to check whether last half is a valid end
-    def checkThisIsStart(lastIsEnd: Bool): Bool =
-      if (!HasCExtension) true.B
-      else lastIsEnd
-    //   - a valid end iff:
-    //     - it is a valid start and is a RVC instruction
-    //     - or it is not a valid start (which implies pc+(i-1)*2 is a valid start)
-    def checkThisIsEnd(thisIsStart: Bool, thisIsRvc: Bool): Bool =
-      if (!HasCExtension) true.B
-      else thisIsStart && thisIsRvc || !thisIsStart
-
-    for (i <- start until end) {
-      if (i == 0) {
-        bound(0).isStart := checkThisIsStart(true.B)
-        bound(0).isEnd   := Mux(preIsHalfRvi, true.B, checkThisIsEnd(bound(0).isStart, realCurrentIsRvc(0)))
-      } else {
-        bound(i).isStart := checkThisIsStart(if (i == start) true.B else bound(i - 1).isEnd)
-        bound(i).isEnd   := checkThisIsEnd(bound(i).isStart, realCurrentIsRvc(i))
-      }
-    }
-  }
-
-  genBound(bound, 0, FetchBlockInstNum / 2, io.req.bits.prevLastIsHalfRvi)
-  genBound(rearBound, FetchBlockInstNum / 2, FetchBlockInstNum, false.B)
-  genBound(rearBoundPlus1, FetchBlockInstNum / 2 + 1, FetchBlockInstNum, false.B)
-
-  // for xxxPlus1, FetchBlockInstNum / 2 must be a valid end, since we assume FetchBlockInstNum / 2 + 1 is a valid start
-  // and, it must not be a valid start, otherwise, FetchBlockInstNum/2-1 is a valid end and rearBound should be selected
-  rearBoundPlus1(FetchBlockInstNum / 2).isStart := false.B
-  rearBoundPlus1(FetchBlockInstNum / 2).isEnd   := true.B
-
-  // if FetchBlockInstNum / 2 - 1 is a valid end, FetchBlockInstNum / 2 is a valid start, then rearBound is correct
-  // otherwise, rearBoundPlus1 is correct
-  private val rearBoundCorrect = bound(FetchBlockInstNum / 2 - 1).isEnd
-
-  for (i <- FetchBlockInstNum / 2 until FetchBlockInstNum) {
-    bound(i) := Mux(rearBoundCorrect, rearBound(i), rearBoundPlus1(i))
-  }
-
-  // we also compute the whole block directly for differential testing, this will be optimized out in released code
-  private val boundDiff = WireInit(VecInit(Seq.fill(FetchBlockInstNum)(0.U.asTypeOf(new BoundInfo))))
-
-  genBound(boundDiff, 0, FetchBlockInstNum, io.req.bits.prevLastIsHalfRvi)
-
-  private val startMismatch = Wire(Bool())
-  private val endMismatch   = Wire(Bool())
-
-  startMismatch := (bound zip boundDiff).map { case (a, b) => a.isStart =/= b.isStart }.reduce(_ || _)
-  endMismatch   := (bound zip boundDiff).map { case (a, b) => a.isEnd =/= b.isEnd }.reduce(_ || _)
-
-  XSError(io.req.valid && startMismatch, p"start mismatch\n")
-  XSError(io.req.valid && endMismatch, p"end mismatch\n")
-
-  for (i <- 0 until FetchBlockInstNum) {
-    io.resp.bits.instrValid(i) := bound(i).isStart && io.req.bits.instrRange(i)
-  }
-  io.resp.valid := io.req.valid
-  io.resp.bits.isRvc := VecInit(io.resp.bits.instrValid.zip(realCurrentIsRvc).map { case (valid, rvc) =>
-    valid & rvc
-  })
-  io.resp.bits.isFirstLastHalfRvi := io.resp.bits.instrValid(io.req.bits.firstEndPos) &&
-    !realCurrentIsRvc(io.req.bits.firstEndPos) && !((io.req.bits.firstEndPos === 0.U) && io.req.bits.prevLastIsHalfRvi)
-  io.resp.bits.isLastHalfRvi := io.resp.bits.instrValid(io.req.bits.endPos) &&
-    !realCurrentIsRvc(io.req.bits.endPos) && !((io.req.bits.endPos === 0.U) && io.req.bits.prevLastIsHalfRvi)
-}
```
