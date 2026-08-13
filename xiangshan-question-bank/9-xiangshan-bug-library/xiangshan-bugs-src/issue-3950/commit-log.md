# Commit Log
- Issue: #3950
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3950
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3950
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3950
- Changed files: 4
- Additions: 42
- Deletions: 21

## Files
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`
- `src/main/scala/xiangshan/frontend/PreDecode.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 5957649b5e8..8f1b5865cba 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -199,43 +199,43 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
     checkPcMem(ftq.io.toBackend.pc_mem_waddr) := ftq.io.toBackend.pc_mem_wdata
   }
 
-  val checkTargetIdx = Wire(Vec(DecodeWidth, UInt(log2Up(FtqSize).W)))
+  val checkTargetPtr = Wire(Vec(DecodeWidth, new FtqPtr))
   val checkTarget    = Wire(Vec(DecodeWidth, UInt(VAddrBits.W)))
 
   for (i <- 0 until DecodeWidth) {
-    checkTargetIdx(i) := ibuffer.io.out(i).bits.ftqPtr.value
+    checkTargetPtr(i) := ibuffer.io.out(i).bits.ftqPtr
     checkTarget(i) := Mux(
-      ftq.io.toBackend.newest_entry_ptr.value === checkTargetIdx(i),
+      ftq.io.toBackend.newest_entry_ptr.value === checkTargetPtr(i).value,
       ftq.io.toBackend.newest_entry_target,
-      checkPcMem(checkTargetIdx(i) + 1.U).startAddr
+      checkPcMem((checkTargetPtr(i) + 1.U).value).startAddr
     )
   }
 
   // commented out for this br could be the last instruction in the fetch block
   def checkNotTakenConsecutive = {
     val prevNotTakenValid  = RegInit(0.B)
-    val prevNotTakenFtqIdx = Reg(UInt(log2Up(FtqSize).W))
+    val prevNotTakenFtqPtr = Reg(new FtqPtr)
     for (i <- 0 until DecodeWidth - 1) {
       // for instrs that is not the last, if a not-taken br, the next instr should have the same ftqPtr
       // for instrs that is the last, record and check next request
       when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr) {
         when(ibuffer.io.out(i + 1).fire) {
           // not last br, check now
-          XSError(checkTargetIdx(i) =/= checkTargetIdx(i + 1), "not-taken br should have same ftqPtr\n")
+          XSError(checkTargetPtr(i).value =/= checkTargetPtr(i + 1).value, "not-taken br should have same ftqPtr\n")
         }.otherwise {
           // last br, record its info
           prevNotTakenValid  := true.B
-          prevNotTakenFtqIdx := checkTargetIdx(i)
+          prevNotTakenFtqPtr := checkTargetPtr(i)
         }
       }
     }
     when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr) {
       // last instr is a br, record its info
       prevNotTakenValid  := true.B
-      prevNotTakenFtqIdx := checkTargetIdx(DecodeWidth - 1)
+      prevNotTakenFtqPtr := checkTargetPtr(DecodeWidth - 1)
     }
     when(prevNotTakenValid && ibuffer.io.out(0).fire) {
-      XSError(prevNotTakenFtqIdx =/= checkTargetIdx(0), "not-taken br should have same ftqPtr\n")
+      XSError(prevNotTakenFtqPtr.value =/= checkTargetPtr(0).value, "not-taken br should have same ftqPtr\n")
       prevNotTakenValid := false.B
     }
     when(needFlush) {
@@ -245,18 +245,21 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
 
   def checkTakenNotConsecutive = {
     val prevTakenValid  = RegInit(0.B)
-    val prevTakenFtqIdx = Reg(UInt(log2Up(FtqSize).W))
+    val prevTakenFtqPtr = Reg(new FtqPtr)
     for (i <- 0 until DecodeWidth - 1) {
       // for instrs that is not the last, if a taken br, the next instr should not have the same ftqPtr
       // for instrs that is the last, record and check next request
       when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.pred_taken) {
         when(ibuffer.io.out(i + 1).fire) {
           // not last br, check now
-          XSError(checkTargetIdx(i) + 1.U =/= checkTargetIdx(i + 1), "taken br should have consecutive ftqPtr\n")
+          XSError(
+            (checkTargetPtr(i) + 1.U).value =/= checkTargetPtr(i + 1).value,
+            "taken br should have consecutive ftqPtr\n"
+          )
         }.otherwise {
           // last br, record its info
           prevTakenValid  := true.B
-          prevTakenFtqIdx := checkTargetIdx(i)
+          prevTakenFtqPtr := checkTargetPtr(i)
         }
       }
     }
@@ -265,10 +268,10 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
     ).bits.pred_taken) {
       // last instr is a br, record its info
       prevTakenValid  := true.B
-      prevTakenFtqIdx := checkTargetIdx(DecodeWidth - 1)
+      prevTakenFtqPtr := checkTargetPtr(DecodeWidth - 1)
     }
     when(prevTakenValid && ibuffer.io.out(0).fire) {
-      XSError(prevTakenFtqIdx + 1.U =/= checkTargetIdx(0), "taken br should have consecutive ftqPtr\n")
+      XSError((prevTakenFtqPtr + 1.U).value =/= checkTargetPtr(0).value, "taken br should have consecutive ftqPtr\n")
       prevTakenValid := false.B
     }
     when(needFlush) {
@@ -317,10 +320,10 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
   }
 
   def checkTakenPC = {
-    val prevTakenFtqIdx = Reg(UInt(log2Up(FtqSize).W))
+    val prevTakenFtqPtr = Reg(new FtqPtr)
     val prevTakenValid  = RegInit(0.B)
     val prevTakenTarget = Wire(UInt(VAddrBits.W))
-    prevTakenTarget := checkPcMem(prevTakenFtqIdx + 1.U).startAddr
+    prevTakenTarget := checkPcMem((prevTakenFtqPtr + 1.U).value).startAddr
 
     for (i <- 0 until DecodeWidth - 1) {
       when(ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.pred_taken) {
@@ -328,7 +331,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
           XSError(checkTarget(i) =/= ibuffer.io.out(i + 1).bits.pc, "taken instr should follow target pc\n")
         }.otherwise {
           prevTakenValid  := true.B
-          prevTakenFtqIdx := checkTargetIdx(i)
+          prevTakenFtqPtr := checkTargetPtr(i)
         }
       }
     }
@@ -336,7 +339,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
       DecodeWidth - 1
     ).bits.pred_taken) {
       prevTakenValid  := true.B
-      prevTakenFtqIdx := checkTargetIdx(DecodeWidth - 1)
+      prevTakenFtqPtr := checkTargetPtr(DecodeWidth - 1)
     }
     when(prevTakenValid && ibuffer.io.out(0).fire) {
       XSError(prevTakenTarget =/= ibuffer.io.out(0).bits.pc, "taken instr should follow target pc\n")
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 5fa6978536f..3b684c8f24c 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -433,6 +433,11 @@ class NewIFU(implicit p: Parameters) extends XSModule
   val f2_foldpc = VecInit(f2_pc.map(i => XORFold(i(VAddrBits - 1, 1), MemPredPCWidth)))
   val f2_jump_range =
     Fill(PredictWidth, !f2_ftq_req.ftqOffset.valid) | Fill(PredictWidth, 1.U(1.W)) >> ~f2_ftq_req.ftqOffset.bits
+  require(
+    isPow2(PredictWidth),
+    "If PredictWidth does not satisfy the power of 2," +
+      "expression: Fill(PredictWidth, 1.U(1.W)) >> ~f2_ftq_req.ftqOffset.bits is not right !!"
+  )
   val f2_ftr_range = Fill(PredictWidth, f2_ftq_req.ftqOffset.valid) | Fill(PredictWidth, 1.U(1.W)) >> ~getBasicBlockIdx(
     f2_ftq_req.nextStartAddr,
     f2_ftq_req.startAddr
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index c140a51ae1c..3e39c529340 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -307,9 +307,16 @@ class FTBEntryGen(implicit p: Parameters) extends XSModule with HasBackendRedire
   val jmpPft = getLower(io.start_addr) +& pd.jmpOffset +& Mux(pd.rvcMask(pd.jmpOffset), 1.U, 2.U)
   init_entry.pftAddr := Mux(entry_has_jmp && !last_jmp_rvi, jmpPft, getLower(io.start_addr))
   init_entry.carry   := Mux(entry_has_jmp && !last_jmp_rvi, jmpPft(carryPos - instOffsetBits), true.B)
-  init_entry.isJalr  := new_jmp_is_jalr
-  init_entry.isCall  := new_jmp_is_call
-  init_entry.isRet   := new_jmp_is_ret
+
+  require(
+    isPow2(PredictWidth),
+    "If PredictWidth does not satisfy the power of 2," +
+      "pftAddr := getLower(io.start_addr) and carry := true.B  not working!!"
+  )
+
+  init_entry.isJalr := new_jmp_is_jalr
+  init_entry.isCall := new_jmp_is_call
+  init_entry.isRet  := new_jmp_is_ret
   // that means fall thru points to the middle of an inst
   init_entry.last_may_be_rvi_call := pd.jmpOffset === (PredictWidth - 1).U && !pd.rvcMask(pd.jmpOffset)
 
diff --git a/src/main/scala/xiangshan/frontend/PreDecode.scala b/src/main/scala/xiangshan/frontend/PreDecode.scala
index 4601cd922ce..42f206c11b1 100644
--- a/src/main/scala/xiangshan/frontend/PreDecode.scala
+++ b/src/main/scala/xiangshan/frontend/PreDecode.scala
@@ -369,6 +369,12 @@ class PredChecker(implicit p: Parameters) extends XSModule with HasPdConst {
   val needRemask  = ParallelOR(remaskFault)
   val fixedRange  = instrRange.asUInt & (Fill(PredictWidth, !needRemask) | Fill(PredictWidth, 1.U(1.W)) >> ~remaskIdx)
 
+  require(
+    isPow2(PredictWidth),
+    "If PredictWidth does not satisfy the power of 2," +
+      "expression: Fill(PredictWidth, 1.U(1.W)) >> ~remaskIdx is not right !!"
+  )
+
   io.out.stage1Out.fixedRange := fixedRange.asTypeOf(Vec(PredictWidth, Bool()))
 
   io.out.stage1Out.fixedTaken := VecInit(pds.zipWithIndex.map { case (pd, i) =>
```
