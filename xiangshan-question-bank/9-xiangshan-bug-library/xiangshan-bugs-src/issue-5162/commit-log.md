# Commit Log
- Issue: #5162
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5162
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5162
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5162
- Changed files: 3
- Additions: 176
- Deletions: 82

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala`
- `src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 41b80bc6a4c..7d58a23bba4 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -85,7 +85,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
   private val s0_bankMask = UIntToOH(s0_bankIdx, NumBanks)
 
   baseTable.io.readReqValid := s0_fire
-  baseTable.io.startPc      := s0_startVAddr
+  baseTable.io.startVAddr   := s0_startVAddr
 
   // to stall resolveQueue when bank conflict
   io.readBankIdx := s0_bankIdx
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
index 5d66ea359bf..cfce66493ad 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTable.scala
@@ -27,70 +27,35 @@ import xiangshan.frontend.bpu.SaturateCounter
 class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
   class TageBaseTableIO extends TageBundle {
     val readReqValid: Bool                 = Input(Bool())
-    val startPc:      PrunedAddr           = Input(PrunedAddr(VAddrBits))
+    val startVAddr:   PrunedAddr           = Input(PrunedAddr(VAddrBits))
     val takenCtrs:    Vec[SaturateCounter] = Output(Vec(FetchBlockInstNum, new SaturateCounter(BaseTableTakenCtrWidth)))
     val train:        Valid[BpuTrain]      = Input(Valid(new BpuTrain))
     val resetDone:    Bool                 = Output(Bool())
   }
   val io: TageBaseTableIO = IO(new TageBaseTableIO)
 
-  private val sramBanks =
-    Seq.fill(BaseTableNumAlignBanks, NumBanks)(
-      Module(new SRAMTemplate(
-        new SaturateCounter(BaseTableTakenCtrWidth),
-        set = BaseTableNumSets,
-        way = FetchBlockAlignInstNum,
-        singlePort = true,
-        shouldReset = true,
-        holdRead = true,
-        withClockGate = true,
-        hasMbist = hasMbist,
-        hasSramCtl = hasSramCtl
-      ))
-    )
-
-  // use a write buffer to store the write requests when read and write are both valid
-  private val writeBuffers =
-    Seq.fill(BaseTableNumAlignBanks, NumBanks)(
-      Module(new Queue(new BaseTableSramWriteReq, WriteBufferSize, pipe = true, flow = true))
-    )
-
-  // Connect write buffers to SRAMs
-  sramBanks.flatten.zip(writeBuffers.flatten).foreach { case (bank, buffer) =>
-    val valid   = buffer.io.deq.valid && !bank.io.r.req.valid
-    val data    = buffer.io.deq.bits.takenCtrs
-    val setIdx  = buffer.io.deq.bits.setIdx
-    val wayMask = buffer.io.deq.bits.wayMask
-    bank.io.w.apply(valid, data, setIdx, wayMask)
-
-    buffer.io.deq.ready := bank.io.w.req.ready && !bank.io.r.req.valid
+  private val alignBanks = Seq.tabulate(BaseTableNumAlignBanks) { alignIdx =>
+    Module(new TageBaseTableAlignBank(alignIdx))
   }
 
-  io.resetDone := sramBanks.flatten.map(_.io.r.req.ready).reduce(_ && _)
+  io.resetDone := alignBanks.map(_.io.resetDone).reduce(_ && _)
 
   /* --------------------------------------------------------------------------------------------------------------
      stage 0
      - send read request to SRAM
      -------------------------------------------------------------------------------------------------------------- */
 
-  private val s0_fire    = io.readReqValid
-  private val s0_startPc = io.startPc
-
-  private val s0_firstAlignBankIdx = getBaseTableAlignBankIndex(s0_startPc)
-  private val s0_setIdx            = getBaseTableSetIndex(s0_startPc)
-  private val s0_nextSetIdx        = getBaseTableSetIndex(getNextAlignedAddr(s0_startPc))
-  private val s0_setIdxVec = VecInit.tabulate(BaseTableNumAlignBanks)(idx =>
-    Mux(idx.U < s0_firstAlignBankIdx, s0_nextSetIdx, s0_setIdx)
+  private val s0_fire              = io.readReqValid
+  private val s0_startVAddr        = io.startVAddr
+  private val s0_firstAlignBankIdx = getBaseTableAlignBankIndex(s0_startVAddr)
+  private val s0_startVAddrVec = vecRotateRight(
+    VecInit.tabulate(BaseTableNumAlignBanks)(i => getAlignedAddr(s0_startVAddr + (i << FetchBlockAlignWidth).U)),
+    s0_firstAlignBankIdx
   )
 
-  private val s0_bankIdx  = getBaseTableBankIndex(s0_startPc)
-  private val s0_bankMask = UIntToOH(s0_bankIdx, NumBanks)
-
-  sramBanks.zipWithIndex.foreach { case (alignBank, alignBankIdx) =>
-    alignBank.zipWithIndex.foreach { case (bank, bankIdx) =>
-      bank.io.r.req.valid       := s0_fire && s0_bankMask(bankIdx)
-      bank.io.r.req.bits.setIdx := s0_setIdxVec(alignBankIdx)
-    }
+  alignBanks.zipWithIndex.foreach { case (b, i) =>
+    b.io.read.req.valid           := s0_fire
+    b.io.read.req.bits.startVAddr := s0_startVAddrVec(i)
   }
 
   /* --------------------------------------------------------------------------------------------------------------
@@ -100,11 +65,7 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
      -------------------------------------------------------------------------------------------------------------- */
 
   private val s1_firstAlignBankIdx = RegEnable(s0_firstAlignBankIdx, s0_fire)
-  private val s1_bankMask          = RegEnable(s0_bankMask, s0_fire)
-
-  private val s1_rawCtrs = VecInit(sramBanks.map(alignBank =>
-    Mux1H(s1_bankMask, alignBank.map(_.io.r.resp.data))
-  ))
+  private val s1_rawCtrs           = VecInit(alignBanks.map(_.io.read.resp.takenCtrs))
 
   /*
    * rotate ctrs
@@ -113,6 +74,7 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
    * if BaseTableNumAlignBanks = 4, alignBankIdx = 1,
    * then io.ctrs := s1_rawCtrs(1) ++ s1_rawCtrs(2) ++ s1_rawCtrs(3) ++ s1_rawCtrs(0)
    */
+  // FIXME: maybe rotateLeft? Not sure. But anyway results are the same for NumAlignBanks = 2
   io.takenCtrs := vecRotateRight(s1_rawCtrs, s1_firstAlignBankIdx).flatten
 
   /* --------------------------------------------------------------------------------------------------------------
@@ -131,24 +93,20 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
   private val t1_valid = RegNext(t0_valid)
   private val t1_train = RegEnable(t0_train, t0_valid)
 
-  private val t1_startVAddr = t1_train.startVAddr
-  private val t1_branches   = t1_train.branches
-  private val t1_oldCtrs    = t1_train.meta.tage.baseTableCtrs
-
+  private val t1_startVAddr        = t1_train.startVAddr
+  private val t1_branches          = t1_train.branches
+  private val t1_oldCtrs           = t1_train.meta.tage.baseTableCtrs
   private val t1_firstAlignBankIdx = getBaseTableAlignBankIndex(t1_startVAddr)
-  private val t1_setIdx            = getBaseTableSetIndex(t1_startVAddr)
-  private val t1_nextSetIdx        = getBaseTableSetIndex(getNextAlignedAddr(t1_startVAddr))
-  private val t1_setIdxVec = VecInit.tabulate(BaseTableNumAlignBanks)(idx =>
-    Mux(idx.U < t1_firstAlignBankIdx, t1_nextSetIdx, t1_setIdx)
+  private val t1_startVAddrVec = vecRotateRight(
+    VecInit.tabulate(BaseTableNumAlignBanks)(i => getAlignedAddr(t1_startVAddr + (i << FetchBlockAlignWidth).U)),
+    t1_firstAlignBankIdx
   )
-  private val t1_bankIdx  = getBankIndex(t1_startVAddr)
-  private val t1_bankMask = UIntToOH(t1_bankIdx, NumBanks)
 
-  private val t1_updateMask = Wire(Vec(BaseTableNumAlignBanks, Vec(FetchBlockAlignInstNum, Bool())))
-  private val t1_newCtrs =
+  private val t1_updateMaskVec = Wire(Vec(BaseTableNumAlignBanks, Vec(FetchBlockAlignInstNum, Bool())))
+  private val t1_newCtrsVec =
     Wire(Vec(BaseTableNumAlignBanks, Vec(FetchBlockAlignInstNum, new SaturateCounter(BaseTableTakenCtrWidth))))
 
-  t1_newCtrs.flatten.zip(t1_updateMask.flatten).zipWithIndex.foreach { case ((newCtr, needUpdate), position) =>
+  t1_newCtrsVec.flatten.zip(t1_updateMaskVec.flatten).zipWithIndex.foreach { case ((newCtr, needUpdate), position) =>
     val hitMask = t1_branches.map { branch =>
       branch.valid && branch.bits.attribute.isConditional && position.U === branch.bits.cfiPosition
     }
@@ -157,21 +115,13 @@ class TageBaseTable(implicit p: Parameters) extends TageModule with Helpers {
     newCtr.value := t1_oldCtrs(position).getUpdate(taken)
   }
 
-  private val t1_rotatedNewCtrs    = vecRotateRight(t1_newCtrs, t1_firstAlignBankIdx)
-  private val t1_rotatedUpdateMask = vecRotateRight(t1_updateMask, t1_firstAlignBankIdx)
+  private val t1_rotatedNewCtrsVec    = vecRotateRight(t1_newCtrsVec, t1_firstAlignBankIdx)
+  private val t1_rotatedUpdateMaskVec = vecRotateRight(t1_updateMaskVec, t1_firstAlignBankIdx)
 
-  writeBuffers.zipWithIndex.foreach { case (buffersPerAlignBank, alignBankIdx) =>
-    buffersPerAlignBank.zipWithIndex.foreach { case (buffer, bankIdx) =>
-      buffer.io.enq.valid          := t1_valid && t1_bankMask(bankIdx)
-      buffer.io.enq.bits.setIdx    := t1_setIdxVec(alignBankIdx)
-      buffer.io.enq.bits.takenCtrs := t1_rotatedNewCtrs(alignBankIdx)
-      buffer.io.enq.bits.wayMask   := t1_rotatedUpdateMask(alignBankIdx).asUInt
-    }
+  alignBanks.zipWithIndex.foreach { case (b, i) =>
+    b.io.write.req.valid           := t1_valid
+    b.io.write.req.bits.startVAddr := t1_startVAddrVec(i)
+    b.io.write.req.bits.wayMask    := t1_rotatedUpdateMaskVec(i).asUInt
+    b.io.write.req.bits.takenCtrs  := t1_rotatedNewCtrsVec(i)
   }
-
-  XSPerfAccumulate("train_update_ctr", t1_valid && t1_updateMask.flatten.reduce(_ || _))
-  XSPerfAccumulate(
-    "write_buffer_drop_write",
-    PopCount(writeBuffers.flatten.map(b => !b.io.enq.ready && b.io.enq.valid))
-  )
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala
new file mode 100644
index 00000000000..77f87093b55
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/TageBaseTableAlignBank.scala
@@ -0,0 +1,144 @@
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
+package xiangshan.frontend.bpu.tage
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.XSPerfAccumulate
+import utility.sram.SRAMTemplate
+import xiangshan.frontend.PrunedAddr
+import xiangshan.frontend.bpu.SaturateCounter
+
+class TageBaseTableAlignBank(
+    alignIdx: Int
+)(implicit p: Parameters) extends TageModule with Helpers {
+  class TageBaseTableAlignBankIO extends Bundle {
+    class Read extends Bundle {
+      class Req extends Bundle {
+        // NOTE: this startVAddr is not from Bpu top, it's calculated in TageBaseTable top
+        // i.e. vecRotateRight(VecInit.tabulate(NumAlignBanks)(startVAddr + _ * alignSize), startAlignIdx)(alignIdx)
+        val startVAddr: PrunedAddr = new PrunedAddr(VAddrBits)
+      }
+
+      class Resp extends Bundle {
+        val takenCtrs: Vec[SaturateCounter] = Vec(FetchBlockAlignInstNum, new SaturateCounter(BaseTableTakenCtrWidth))
+      }
+
+      val req:  Valid[Req] = Flipped(Valid(new Req))
+      val resp: Resp       = Output(new Resp)
+    }
+
+    class Write extends Bundle {
+      class Req extends Bundle {
+        // NOTE: this startVAddr is not from Bpu top, it's calculated in TageBaseTable top
+        // i.e. vecRotateRight(VecInit.tabulate(NumAlignBanks)(startVAddr + _ * alignSize), startAlignIdx)(alignIdx)
+        val startVAddr: PrunedAddr           = new PrunedAddr(VAddrBits)
+        val takenCtrs:  Vec[SaturateCounter] = Vec(FetchBlockAlignInstNum, new SaturateCounter(BaseTableTakenCtrWidth))
+        val wayMask:    UInt                 = UInt(FetchBlockAlignInstNum.W)
+      }
+
+      val req: Valid[Req] = Flipped(Valid(new Req))
+    }
+
+    val resetDone: Bool  = Output(Bool())
+    val read:      Read  = new Read
+    val write:     Write = new Write
+  }
+
+  val io: TageBaseTableAlignBankIO = IO(new TageBaseTableAlignBankIO)
+
+  /* *** alias *** */
+  private val r = io.read
+  private val w = io.write
+
+  private val sramBanks = Seq.tabulate(NumBanks) { i =>
+    Module(new SRAMTemplate(
+      new SaturateCounter(BaseTableTakenCtrWidth),
+      set = BaseTableNumSets,
+      way = FetchBlockAlignInstNum,
+      singlePort = true,
+      shouldReset = true,
+      holdRead = true,
+      withClockGate = true,
+      hasMbist = hasMbist,
+      hasSramCtl = hasSramCtl
+    )).suggestName(s"tage_sram_align${alignIdx}_bank${i}")
+  }
+
+  // use a write buffer to store the write requests when read and write are both valid
+  private val writeBuffers = Seq.tabulate(NumBanks) { i =>
+    Module(new Queue(
+      new BaseTableSramWriteReq,
+      WriteBufferSize,
+      pipe = true,
+      flow = true
+    ))
+  }
+
+  io.resetDone := sramBanks.map(_.io.r.req.ready).reduce(_ && _)
+
+  /* *** read *** */
+  private val s0_fire       = r.req.valid
+  private val s0_startVAddr = r.req.bits.startVAddr
+  private val s0_bankIdx    = getBaseTableBankIndex(s0_startVAddr)
+  private val s0_bankMask   = UIntToOH(s0_bankIdx, NumBanks)
+  private val s0_setIdx     = getBaseTableSetIndex(s0_startVAddr)
+
+  sramBanks.zipWithIndex.foreach { case (bank, i) =>
+    bank.io.r.req.valid       := s0_fire && s0_bankMask(i)
+    bank.io.r.req.bits.setIdx := s0_setIdx
+  }
+
+  private val s1_bankMask = RegEnable(s0_bankMask, s0_fire)
+
+  io.read.resp.takenCtrs := Mux1H(s1_bankMask, sramBanks.map(_.io.r.resp.data))
+
+  /* *** write *** */
+  private val t1_valid      = w.req.valid
+  private val t1_startVAddr = w.req.bits.startVAddr
+  private val t1_takenCtrs  = w.req.bits.takenCtrs
+  private val t1_wayMask    = w.req.bits.wayMask
+
+  private val t1_setIdx   = getBaseTableSetIndex(t1_startVAddr)
+  private val t1_bankIdx  = getBankIndex(t1_startVAddr)
+  private val t1_bankMask = UIntToOH(t1_bankIdx, NumBanks)
+
+  writeBuffers.zipWithIndex.foreach { case (buffer, bankIdx) =>
+    buffer.io.enq.valid          := t1_valid && t1_bankMask(bankIdx)
+    buffer.io.enq.bits.setIdx    := t1_setIdx
+    buffer.io.enq.bits.takenCtrs := t1_takenCtrs
+    buffer.io.enq.bits.wayMask   := t1_wayMask
+  }
+
+  // write back to sram
+  (sramBanks zip writeBuffers).foreach { case (bank, buffer) =>
+    val valid   = buffer.io.deq.valid && !bank.io.r.req.valid
+    val data    = buffer.io.deq.bits.takenCtrs
+    val setIdx  = buffer.io.deq.bits.setIdx
+    val wayMask = buffer.io.deq.bits.wayMask
+    bank.io.w.apply(valid, data, setIdx, wayMask)
+
+    buffer.io.deq.ready := bank.io.w.req.ready && !bank.io.r.req.valid
+  }
+
+  /* *** perf *** */
+  XSPerfAccumulate("train_update_ctr", t1_valid && t1_wayMask.orR)
+  XSPerfAccumulate(
+    "write_buffer_drop_write",
+    PopCount(writeBuffers.map(b => !b.io.enq.ready && b.io.enq.valid))
+  )
+}
```
