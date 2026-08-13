# Commit Log
- Issue: #3409
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3409
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3409
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3409
- Changed files: 2
- Additions: 36
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/decode/Instructions.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 3142fee4209..1d1482ad409 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -32,6 +32,7 @@ import xiangshan.backend.decode.isa.PseudoInstructions
 import xiangshan.backend.decode.isa.bitfield.{InstVType, OPCODE5Bit, XSInstBitFields}
 import xiangshan.backend.fu.vector.Bundles.{VType, Vl}
 import xiangshan.backend.fu.wrapper.CSRToDecode
+import xiangshan.backend.decode.Zimop._
 
 /**
  * Abstract trait giving defaults and other relevant values to different Decode constants/
@@ -492,6 +493,17 @@ object ZicondDecode extends DecodeConstants {
   )
 }
 
+/**
+  * "Zimop" Extension for May-Be-Operations
+  */
+object ZimopDecode extends DecodeConstants {
+  override val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
+    // temp use addi to decode MOP_R and MOP_RR
+    MOP_R  -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.alu, ALUOpType.add, SelImm.IMM_I, xWen = T, canRobCompress = T),
+    MOP_RR -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.alu, ALUOpType.add, SelImm.IMM_I, xWen = T, canRobCompress = T),
+  )
+}
+
 /**
  * XiangShan Trap Decode constants
  */
@@ -723,7 +735,8 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     SvinvalDecode.table ++
     HypervisorDecode.table ++
     VecDecoder.table ++
-    ZicondDecode.table
+    ZicondDecode.table ++
+    ZimopDecode.table
 
   require(decode_table.map(_._2.length == 15).reduce(_ && _), "Decode tables have different column size")
   // assertion for LUI: only LUI should be assigned `selImm === SelImm.IMM_U && fuType === FuType.alu`
@@ -747,8 +760,12 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   decodedInst.numUops := 1.U
   decodedInst.numWB   := 1.U
 
+  val isZimop = (BitPat("b1?00??0111??_?????_100_?????_1110011") === ctrl_flow.instr) ||
+                (BitPat("b1?00??1?????_?????_100_?????_1110011") === ctrl_flow.instr)
+
   val isMove = BitPat("b000000000000_?????_000_?????_0010011") === ctrl_flow.instr
-  decodedInst.isMove := isMove && ctrl_flow.instr(RD_MSB, RD_LSB) =/= 0.U && !io.csrCtrl.singlestep
+  // temp decode zimop as move
+  decodedInst.isMove := (isMove || isZimop) && ctrl_flow.instr(RD_MSB, RD_LSB) =/= 0.U && !io.csrCtrl.singlestep
 
   // fmadd - b1000011
   // fmsub - b1000111
@@ -989,7 +1006,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     decodedInst.waitForward   := false.B
     decodedInst.blockBackward := false.B
     decodedInst.exceptionVec(illegalInstr) := io.fromCSR.illegalInst.vsIsOff
-  }.elsewhen(isCsrrVlenb){
+  }.elsewhen (isCsrrVlenb) {
     // convert to addi instruction
     decodedInst.srcType(0) := SrcType.reg
     decodedInst.srcType(1) := SrcType.imm
@@ -1001,7 +1018,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     decodedInst.blockBackward := false.B
     decodedInst.canRobCompress := true.B
     decodedInst.exceptionVec(illegalInstr) := io.fromCSR.illegalInst.vsIsOff
-  }.elsewhen(isPreW || isPreR || isPreI){
+  }.elsewhen (isPreW || isPreR || isPreI) {
     decodedInst.selImm := SelImm.IMM_S
     decodedInst.fuType := FuType.ldu.U
     decodedInst.canRobCompress := false.B
@@ -1010,7 +1027,12 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
       isPreR -> LSUOpType.prefetch_r,
       isPreI -> LSUOpType.prefetch_i,
     ))
-
+  }.elsewhen (isZimop) {
+    // set srcType for zimop
+    decodedInst.srcType(0) := SrcType.reg
+    decodedInst.srcType(1) := SrcType.imm
+    // use x0 as src1
+    decodedInst.lsrc(0) := 0.U
   }
 
   io.deq.decodedInst := decodedInst
@@ -1030,7 +1052,10 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     // MOP =/= b00                    : strided and indexed load
     ( FuType.FuTypeOrR(decodedInst.fuType, FuType.vldu)              && inst.NF =/= 0.U && ((inst.MOP === "b00".U && inst.LUMOP =/= "b01000".U) || inst.MOP =/= "b00".U)) -> FuType.vsegldu.U,
   ))
-  io.deq.decodedInst.imm := Mux(isCsrrVlenb, (VLEN / 8).U, decodedInst.imm)
+  io.deq.decodedInst.imm := MuxCase(decodedInst.imm, Seq(
+    isCsrrVlenb -> (VLEN / 8).U,
+    isZimop     -> 0.U,
+  ))
 
   io.deq.decodedInst.fuOpType := MuxCase(decodedInst.fuOpType, Seq(
     isCsrrVl    -> VSETOpType.csrrvl,
diff --git a/src/main/scala/xiangshan/backend/decode/Instructions.scala b/src/main/scala/xiangshan/backend/decode/Instructions.scala
index 8967d5c6d40..0b2f535abbd 100644
--- a/src/main/scala/xiangshan/backend/decode/Instructions.scala
+++ b/src/main/scala/xiangshan/backend/decode/Instructions.scala
@@ -20,3 +20,8 @@ object Zvbb {
   def VWSLL_VV = BitPat("b110101???????????000?????1010111")
   def VWSLL_VX = BitPat("b110101???????????100?????1010111")
 }
+
+object Zimop {
+  def MOP_R  = BitPat("b1?00??0111???????100?????1110011")
+  def MOP_RR = BitPat("b1?00??1??????????100?????1110011")
+}
```
