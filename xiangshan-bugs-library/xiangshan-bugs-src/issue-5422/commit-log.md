# Commit Log
- Issue: #5422
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5422
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5422
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5422
- Changed files: 1
- Additions: 11
- Deletions: 13

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 8263fa60ff4..2285d691a2f 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -869,6 +869,16 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   decodedInst.v0Wen := false.B
   decodedInst.vlWen := false.B
 
+  val isCsr = inst.OPCODE5Bit === OPCODE5Bit.SYSTEM && inst.FUNCT3(1, 0) =/= 0.U
+  val isCsrr = isCsr && inst.FUNCT3 === BitPat("b?1?") && inst.RS1 === 0.U
+  val isCsrw = isCsr && inst.FUNCT3 === BitPat("b?01") && inst.RD  === 0.U
+  dontTouch(isCsrr)
+  dontTouch(isCsrw)
+
+  // for csrr vl instruction, convert to vsetvl
+  val isCsrrVlenb = isCsrr && inst.CSRIDX === CSRs.vlenb.U
+  val isCsrrVl    = isCsrr && inst.CSRIDX === CSRs.vl.U
+
   private val isCboClean = CBO_CLEAN === io.enq.ctrlFlow.instr
   private val isCboFlush = CBO_FLUSH === io.enq.ctrlFlow.instr
   private val isCboInval = CBO_INVAL === io.enq.ctrlFlow.instr
@@ -895,7 +905,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
       FuType.FuTypeOrR(decodedInst.fuType, FuType.stu) && (decodedInst.fuOpType === LSUOpType.sh || decodedInst.fuOpType === LSUOpType.sw || decodedInst.fuOpType === LSUOpType.sd)) && decodedInst.instr(2) ||
       inst.isOPFVF || inst.isOPFVV
     ) ||
-    io.fromCSR.illegalInst.vsIsOff    && FuType.FuTypeOrR(decodedInst.fuType, FuType.vecAll) ||
+    io.fromCSR.illegalInst.vsIsOff    && (FuType.FuTypeOrR(decodedInst.fuType, FuType.vecAll) || isCsrrVl || isCsrrVlenb)||
     io.fromCSR.illegalInst.wfi        && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWfi(decodedInst.fuOpType) ||
     io.fromCSR.illegalInst.wrs_nto    && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWrsNto(decodedInst.fuOpType) ||
     (decodedInst.needFrm.scalaNeedFrm || FuType.isScalaNeedFrm(decodedInst.fuType)) && (((decodedInst.fpu.rm === 5.U) || (decodedInst.fpu.rm === 6.U)) || ((decodedInst.fpu.rm === 7.U) && io.fromCSR.illegalInst.frm)) ||
@@ -1089,16 +1099,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   io.deq.uopInfo.numOfWB := uopInfoGen.io.out.uopInfo.numOfWB
   io.deq.uopInfo.lmul := uopInfoGen.io.out.uopInfo.lmul
 
-  val isCsr = inst.OPCODE5Bit === OPCODE5Bit.SYSTEM && inst.FUNCT3(1, 0) =/= 0.U
-  val isCsrr = isCsr && inst.FUNCT3 === BitPat("b?1?") && inst.RS1 === 0.U
-  val isCsrw = isCsr && inst.FUNCT3 === BitPat("b?01") && inst.RD  === 0.U
-  dontTouch(isCsrr)
-  dontTouch(isCsrw)
-
-  // for csrr vl instruction, convert to vsetvl
-  val isCsrrVlenb = isCsrr && inst.CSRIDX === CSRs.vlenb.U
-  val isCsrrVl    = isCsrr && inst.CSRIDX === CSRs.vl.U
-
   // decode for SoftPrefetch instructions (prefetch.w / prefetch.r / prefetch.i)
   val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") && inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
   val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
@@ -1118,7 +1118,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     decodedInst.lsrc(4)    := Vl_IDX.U
     decodedInst.waitForward   := false.B
     decodedInst.blockBackward := false.B
-    decodedInst.exceptionVec(illegalInstr) := io.fromCSR.illegalInst.vsIsOff
   }.elsewhen (isCsrrVlenb) {
     // convert to addi instruction
     decodedInst.srcType(0) := SrcType.reg
@@ -1130,7 +1129,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     decodedInst.waitForward := false.B
     decodedInst.blockBackward := false.B
     decodedInst.canRobCompress := true.B
-    decodedInst.exceptionVec(illegalInstr) := io.fromCSR.illegalInst.vsIsOff
   }.elsewhen (isPreW || isPreR || isPreI) {
     decodedInst.selImm := SelImm.IMM_S
     decodedInst.fuType := FuType.ldu.U
```
