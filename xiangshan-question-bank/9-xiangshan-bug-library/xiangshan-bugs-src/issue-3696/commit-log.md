# Commit Log
- Issue: #3696
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3696
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3696
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3696
- Changed files: 2
- Additions: 41
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/decode/isa/bitfield/RiscvInst.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index fc4a6cce5b3..ebd5cd76b5e 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -872,10 +872,12 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     io.fromCSR.illegalInst.hfenceVVMA && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.hfence_v ||
     io.fromCSR.illegalInst.hlsv       && FuType.FuTypeOrR(decodedInst.fuType, FuType.ldu)   && (LSUOpType.isHlv(decodedInst.fuOpType) || LSUOpType.isHlvx(decodedInst.fuOpType)) ||
     io.fromCSR.illegalInst.hlsv       && FuType.FuTypeOrR(decodedInst.fuType, FuType.stu)   && LSUOpType.isHsv(decodedInst.fuOpType) ||
-    io.fromCSR.illegalInst.fsIsOff    && (FuType.FuTypeOrR(decodedInst.fuType, FuType.fpOP ++ Seq(FuType.f2v)) ||
-                                          (FuType.FuTypeOrR(decodedInst.fuType, FuType.ldu) && (decodedInst.fuOpType === LSUOpType.lw || decodedInst.fuOpType === LSUOpType.ld) ||
-                                           FuType.FuTypeOrR(decodedInst.fuType, FuType.stu) && (decodedInst.fuOpType === LSUOpType.sw || decodedInst.fuOpType === LSUOpType.sd)) && decodedInst.instr(2) ||
-                                           isVecOPF) ||
+    io.fromCSR.illegalInst.fsIsOff    && (
+      FuType.FuTypeOrR(decodedInst.fuType, FuType.fpOP ++ Seq(FuType.f2v)) ||
+      (FuType.FuTypeOrR(decodedInst.fuType, FuType.ldu) && (decodedInst.fuOpType === LSUOpType.lw || decodedInst.fuOpType === LSUOpType.ld) ||
+      FuType.FuTypeOrR(decodedInst.fuType, FuType.stu) && (decodedInst.fuOpType === LSUOpType.sw || decodedInst.fuOpType === LSUOpType.sd)) && decodedInst.instr(2) ||
+      inst.isOPFVF || inst.isOPFVV
+    ) ||
     io.fromCSR.illegalInst.vsIsOff    && FuType.FuTypeOrR(decodedInst.fuType, FuType.vecAll) ||
     io.fromCSR.illegalInst.wfi        && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWfi(decodedInst.fuOpType) ||
     (decodedInst.needFrm.scalaNeedFrm || FuType.isScalaNeedFrm(decodedInst.fuType)) && (((decodedInst.fpu.rm === 5.U) || (decodedInst.fpu.rm === 6.U)) || ((decodedInst.fpu.rm === 7.U) && io.fromCSR.illegalInst.frm)) ||
diff --git a/src/main/scala/xiangshan/backend/decode/isa/bitfield/RiscvInst.scala b/src/main/scala/xiangshan/backend/decode/isa/bitfield/RiscvInst.scala
index 3353f3c06e9..735bbf9e785 100644
--- a/src/main/scala/xiangshan/backend/decode/isa/bitfield/RiscvInst.scala
+++ b/src/main/scala/xiangshan/backend/decode/isa/bitfield/RiscvInst.scala
@@ -80,6 +80,41 @@ trait BitFieldsVec { this: Riscv32BitInst =>
   def isVecLoad = {
     this.OPCODE === "b0000111".U && (this.WIDTH === 0.U || this.WIDTH(2) === 1.B)
   }
+
+  def isOPIVV = {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b000".U
+  }
+
+  def isOPFVV = {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b001".U
+  }
+
+  def isOPMVV = {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b010".U
+  }
+
+  def isOPIVI= {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b011".U
+  }
+
+  def isOPIVX = {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b100".U
+  }
+
+  def isOPFVF = {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b101".U
+  }
+
+  def isOPMVX = {
+    this.OPCODE === xiangshan.backend.decode.isa.bitfield.OPCODE7Bit.VECTOR_ARITH &&
+      this.FUNCT3 === "b110".U
+  }
 }
 
 class XSInstBitFields extends Riscv32BitInst
```
