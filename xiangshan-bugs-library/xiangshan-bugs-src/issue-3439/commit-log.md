# Commit Log
- Issue: #3439
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3439
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3439
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3439
- Changed files: 9
- Additions: 237
- Deletions: 13

## Files
- `rocket-chip`
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/decode/FPDecoder.scala`
- `src/main/scala/xiangshan/backend/fu/FuConfig.scala`
- `src/main/scala/xiangshan/backend/fu/fpu/FliTable.scala`
- `src/main/scala/xiangshan/backend/fu/fpu/IntFPToVec.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala`
- `yunsuan`

## Diff
```diff
diff --git a/rocket-chip b/rocket-chip
index c0955429aa3..27998d74803 160000
--- a/rocket-chip
+++ b/rocket-chip
@@ -1 +1 @@
-Subproject commit c0955429aa3d9fe2be560f28599ece700fb0466c
+Subproject commit 27998d74803a3ef7a3a3c73c461aa0bf2b44cc1d
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 5e3c785ceaf..46b33b32db0 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -33,6 +33,7 @@ import xiangshan.backend.decode.isa.bitfield.{InstVType, OPCODE5Bit, XSInstBitFi
 import xiangshan.backend.fu.vector.Bundles.{VType, Vl}
 import xiangshan.backend.fu.wrapper.CSRToDecode
 import xiangshan.backend.decode.Zimop._
+import yunsuan.{VfaluType, VfcvtType}
 
 /**
  * Abstract trait giving defaults and other relevant values to different Decode constants/
@@ -507,6 +508,33 @@ object ZimopDecode extends DecodeConstants {
   )
 }
 
+object ZfaDecode extends DecodeConstants {
+  override val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
+    FLI_H       -> FDecode(SrcType.no, SrcType.X, SrcType.X, FuType.f2v, FuOpType.X, fWen = T, canRobCompress = T),
+    FLI_S       -> FDecode(SrcType.no, SrcType.X, SrcType.X, FuType.f2v, FuOpType.X, fWen = T, canRobCompress = T),
+    FLI_D       -> FDecode(SrcType.no, SrcType.X, SrcType.X, FuType.f2v, FuOpType.X, fWen = T, canRobCompress = T),
+    FMINM_H     -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fminm, fWen = T, canRobCompress = T),
+    FMINM_S     -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fminm, fWen = T, canRobCompress = T),
+    FMINM_D     -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fminm, fWen = T, canRobCompress = T),
+    FMAXM_H     -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fmaxm, fWen = T, canRobCompress = T),
+    FMAXM_S     -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fmaxm, fWen = T, canRobCompress = T),
+    FMAXM_D     -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fmaxm, fWen = T, canRobCompress = T),
+    FROUND_H    -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.fround,   fWen = T, canRobCompress = T),
+    FROUND_S    -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.fround,   fWen = T, canRobCompress = T),
+    FROUND_D    -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.fround,   fWen = T, canRobCompress = T),
+    FROUNDNX_H  -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.froundnx, fWen = T, canRobCompress = T),
+    FROUNDNX_S  -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.froundnx, fWen = T, canRobCompress = T),
+    FROUNDNX_D  -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.froundnx, fWen = T, canRobCompress = T),
+    FCVTMOD_W_D -> FDecode(SrcType.fp, SrcType.X,  SrcType.X, FuType.fcvt, VfcvtType.fcvtmod_w_d, xWen = T, canRobCompress = T),
+    FLEQ_H      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fleq, xWen = T, canRobCompress = T),
+    FLEQ_S      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fleq, xWen = T, canRobCompress = T),
+    FLEQ_D      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fleq, xWen = T, canRobCompress = T),
+    FLTQ_H      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fltq, xWen = T, canRobCompress = T),
+    FLTQ_S      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fltq, xWen = T, canRobCompress = T),
+    FLTQ_D      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.falu, VfaluType.fltq, xWen = T, canRobCompress = T),
+  )
+}
+
 /**
  * XiangShan Trap Decode constants
  */
@@ -759,7 +787,8 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     HypervisorDecode.table ++
     VecDecoder.table ++
     ZicondDecode.table ++
-    ZimopDecode.table
+    ZimopDecode.table ++
+    ZfaDecode.table
 
   require(decode_table.map(_._2.length == 15).reduce(_ && _), "Decode tables have different column size")
   // assertion for LUI: only LUI should be assigned `selImm === SelImm.IMM_U && fuType === FuType.alu`
@@ -932,12 +961,18 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     VFWCVT_XU_F_V, VFWCVT_X_F_V, VFWCVT_RTZ_XU_F_V, VFWCVT_RTZ_X_F_V, VFWCVT_F_XU_V, VFWCVT_F_X_V, VFWCVT_F_F_V,
     VFNCVT_XU_F_W, VFNCVT_X_F_W, VFNCVT_RTZ_XU_F_W, VFNCVT_RTZ_X_F_W, VFNCVT_F_XU_W, VFNCVT_F_X_W, VFNCVT_F_F_W,
     VFNCVT_ROD_F_F_W, VFRSQRT7_V, VFREC7_V,
+    // zfa
+    FLEQ_H, FLEQ_S, FLEQ_D, FLTQ_H, FLTQ_S, FLTQ_D,
+    FMINM_H, FMINM_S, FMINM_D, FMAXM_H, FMAXM_S, FMAXM_D,
+    FROUND_H, FROUND_S, FROUND_D, FROUNDNX_H, FROUNDNX_S, FROUNDNX_D,
+    FCVTMOD_W_D,
   )
 
   private val scalaNeedFrmInsts = Seq(
     FADD_S, FSUB_S, FADD_D, FSUB_D,
     FCVT_W_S, FCVT_WU_S, FCVT_L_S, FCVT_LU_S,
     FCVT_W_D, FCVT_WU_D, FCVT_L_D, FCVT_LU_D, FCVT_S_D, FCVT_D_S,
+    FROUND_H, FROUND_S, FROUND_D, FROUNDNX_H, FROUNDNX_S, FROUNDNX_D,
   )
 
   private val vectorNeedFrmInsts = Seq (
@@ -1022,6 +1057,9 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
   val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
 
+  // for fli.s|fli.d instruction
+  val isFLI = inst.FUNCT7 === BitPat("b11110??") && inst.RS2 === 1.U && inst.RM === 0.U && inst.OPCODE5Bit === OPCODE5Bit.OP_FP
+
   when (isCsrrVl) {
     // convert to vsetvl instruction
     decodedInst.srcType(0) := SrcType.no
@@ -1087,6 +1125,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   io.deq.decodedInst.fuOpType := MuxCase(decodedInst.fuOpType, Seq(
     isCsrrVl    -> VSETOpType.csrrvl,
     isCsrrVlenb -> ALUOpType.add,
+    isFLI       -> Cat(1.U, inst.FMT, inst.RS1),
   ))
 
   io.deq.decodedInst.blockBackward := MuxCase(decodedInst.blockBackward, Seq(
diff --git a/src/main/scala/xiangshan/backend/decode/FPDecoder.scala b/src/main/scala/xiangshan/backend/decode/FPDecoder.scala
index 1d8055ebe85..8bf87101ac7 100644
--- a/src/main/scala/xiangshan/backend/decode/FPDecoder.scala
+++ b/src/main/scala/xiangshan/backend/decode/FPDecoder.scala
@@ -48,13 +48,25 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
     FCVT_W_D, FCVT_WU_D, FCVT_L_D, FCVT_LU_D, FCVT_S_D, FCVT_D_S,
     FCVT_S_H, FCVT_H_S, FCVT_H_D, FCVT_D_H,
     FMV_X_W, FMV_X_D, FMV_X_H,
+    // zfa inst
+    FLEQ_H, FLEQ_S, FLEQ_D, FLTQ_H, FLTQ_S, FLTQ_D, FMINM_H, FMINM_S, FMINM_D, FMAXM_H, FMAXM_S, FMAXM_D,
+    FROUND_H, FROUND_S, FROUND_D, FROUNDNX_H, FROUNDNX_S, FROUNDNX_D, FCVTMOD_W_D,
   )
   val isFpToVecInst = fpToVecInsts.map(io.instr === _).reduce(_ || _)
+  val isFP16Instrs = Seq(
+    // zfa inst
+    FLEQ_H, FLTQ_H, FMINM_H, FMAXM_H,
+    FROUND_H, FROUNDNX_H,
+  )
+  val isFP16Instr = isFP16Instrs.map(io.instr === _).reduce(_ || _)
   val isFP32Instrs = Seq(
     FADD_S, FSUB_S, FEQ_S, FLT_S, FLE_S, FMIN_S, FMAX_S,
     FMUL_S, FDIV_S, FSQRT_S,
     FMADD_S, FMSUB_S, FNMADD_S, FNMSUB_S,
     FCLASS_S, FSGNJ_S, FSGNJX_S, FSGNJN_S,
+    // zfa inst
+    FLEQ_S, FLTQ_S, FMINM_S, FMAXM_S,
+    FROUND_S, FROUNDNX_S,
   )
   val isFP32Instr = isFP32Instrs.map(io.instr === _).reduce(_ || _)
   val isFP64Instrs = Seq(
@@ -69,6 +81,8 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
     FCVT_W_S, FCVT_WU_S, FCVT_L_S, FCVT_LU_S,
     FCVT_W_D, FCVT_WU_D, FCVT_S_D, FCVT_D_S,
     FMV_X_W,
+    // zfa inst
+    FCVTMOD_W_D,
   )
   /*
   The optype for FCVT_D_H and FCVT_H_D is the same,
@@ -95,6 +109,8 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
     FDIV_S, FDIV_D, FSQRT_S, FSQRT_D,
     FMADD_S, FMSUB_S, FNMADD_S, FNMSUB_S, FMADD_D, FMSUB_D, FNMADD_D, FNMSUB_D,
     FCLASS_S, FCLASS_D, FSGNJ_S, FSGNJ_D, FSGNJX_S, FSGNJX_D, FSGNJN_S, FSGNJN_D,
+    // zfa inst
+    FLEQ_H, FLEQ_S, FLEQ_D, FLTQ_H, FLTQ_S, FLTQ_D, FMINM_H, FMINM_S, FMINM_D, FMAXM_H, FMAXM_S, FMAXM_D,
   )
   val needReverseInst = needReverseInsts.map(_ === inst.ALL).reduce(_ || _)
   io.vpuCtrl := 0.U.asTypeOf(io.vpuCtrl)
@@ -104,7 +120,7 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
   io.vpuCtrl.vill  := false.B
   io.vpuCtrl.vma   := true.B
   io.vpuCtrl.vta   := true.B
-  io.vpuCtrl.vsew  := Mux(isFP32Instr || isSew2Cvt32, VSew.e32, Mux(isSew2Cvt16, VSew.e16, VSew.e64))
+  io.vpuCtrl.vsew  := Mux(isFP32Instr || isSew2Cvt32, VSew.e32, Mux(isFP16Instr || isSew2Cvt16, VSew.e16, VSew.e64))
   io.vpuCtrl.vlmul := Mux(isFP32Instr || isLmulMf4Cvt, VLmul.mf4, VLmul.mf2)
   io.vpuCtrl.vm    := inst.VM
   io.vpuCtrl.nf    := inst.NF
diff --git a/src/main/scala/xiangshan/backend/fu/FuConfig.scala b/src/main/scala/xiangshan/backend/fu/FuConfig.scala
index 25d69eddd60..45b018b50fc 100644
--- a/src/main/scala/xiangshan/backend/fu/FuConfig.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuConfig.scala
@@ -274,6 +274,7 @@ object FuConfig {
       Seq(FpData()),
     ),
     piped = true,
+    writeFpRf = true,
     writeVecRf = true,
     writeV0Rf = true,
     latency = CertainLatency(0),
diff --git a/src/main/scala/xiangshan/backend/fu/fpu/FliTable.scala b/src/main/scala/xiangshan/backend/fu/fpu/FliTable.scala
new file mode 100644
index 00000000000..644ffe1e94f
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/fu/fpu/FliTable.scala
@@ -0,0 +1,129 @@
+package xiangshan.backend.fu.fpu
+
+import chisel3._
+import chisel3.util._
+import chisel3.util.experimental.decode._
+
+class FliTable(table: Seq[Int]) extends Module {
+  val src = IO(Input(UInt(5.W)))
+  val out = IO(Output(UInt(16.W)))
+
+  out := chisel3.util.experimental.decode.decoder(src,
+    TruthTable(table.zipWithIndex.map { case(data, in) =>
+      (BitPat(in.U(5.W)), BitPat(data.U(16.W)))
+    },
+      BitPat.N(16)
+    )
+  )
+}
+
+class FliHTable extends FliTable(
+  Seq(
+    0xBC00, // -1.0
+    0x0400, // minimum positive normal
+    0x0100, // 1.0 * 2^-16
+    0x0200, // 1.0 * 2^-15
+    0x1C00, // 1.0 * 2^-8
+    0x2000, // 1.0 * 2^-7
+    0x2C00, // 1.0 * 2^-4
+    0x3000, // 1.0 * 2^-3
+    0x3400, // 0.25
+    0x3500, // 0.3125
+    0x3600, // 0.375
+    0x3700, // 0.4375
+    0x3800, // 0.5
+    0x3900, // 0.625
+    0x3A00, // 0.75
+    0x3B00, // 0.875
+    0x3C00, // 1.0
+    0x3D00, // 1.25
+    0x3E00, // 1.5
+    0x3F00, // 1.75
+    0x4000, // 2.0
+    0x4100, // 2.5
+    0x4200, // 3
+    0x4400, // 4
+    0x4800, // 8
+    0x4C00, // 16
+    0x5800, // 2^7
+    0x5C00, // 2^8
+    0x7800, // 2^15
+    0x7C00, // +inf(2^16 is not expressible)
+    0x7C00, // +inf
+    0x7E00  // CNaN
+  )
+)
+
+class FliSTable extends FliTable(
+  Seq(
+    0xBF80, // -1.0
+    0x0080, // minimum positive normal
+    0x3780, // 1.0 * 2^-16
+    0x3800, // 1.0 * 2^-15
+    0x3B80, // 1.0 * 2^-8
+    0x3C00, // 1.0 * 2^-7
+    0x3D80, // 1.0 * 2^-4
+    0x3E00, // 1.0 * 2^-3
+    0x3E80, // 0.25
+    0x3EA0, // 0.3125
+    0x3EC0, // 0.375
+    0x3EE0, // 0.4375
+    0x3F00, // 0.5
+    0x3F20, // 0.625
+    0x3F40, // 0.75
+    0x3F60, // 0.875
+    0x3F80, // 1.0
+    0x3FA0, // 1.25
+    0x3FC0, // 1.5
+    0x3FE0, // 1.75
+    0x4000, // 2.0
+    0x4020, // 2.5
+    0x4040, // 3
+    0x4080, // 4
+    0x4100, // 8
+    0x4180, // 16
+    0x4300, // 2^7
+    0x4380, // 2^8
+    0x4700, // 2^15
+    0x4780, // 2^16
+    0x7F80, // +inf
+    0x7FC0  // CNaN
+  )
+)
+
+class FliDTable extends FliTable(
+  Seq(
+    0xBFF0, // -1.0
+    0x0010, // minimum positive normal
+    0x3EF0, // 1.0 * 2^-16
+    0x3F00, // 1.0 * 2^-15
+    0x3F70, // 1.0 * 2^-8
+    0x3F80, // 1.0 * 2^-7
+    0x3FB0, // 1.0 * 2^-4
+    0x3FC0, // 1.0 * 2^-3
+    0x3FD0, // 0.25
+    0x3FD4, // 0.3125
+    0x3FD8, // 0.375
+    0x3FDC, // 0.4375
+    0x3FE0, // 0.5
+    0x3FE4, // 0.625
+    0x3FE8, // 0.75
+    0x3FEC, // 0.875
+    0x3FF0, // 1.0
+    0x3FF4, // 1.25
+    0x3FF8, // 1.5
+    0x3FFC, // 1.75
+    0x4000, // 2.0
+    0x4004, // 2.5
+    0x4008, // 3
+    0x4010, // 4
+    0x4020, // 8
+    0x4030, // 16
+    0x4060, // 2^7
+    0x4070, // 2^8
+    0x40E0, // 2^15
+    0x40F0, // 2^16
+    0x7FF0, // +inf
+    0x7FF8  // CNaN
+  )
+)
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/backend/fu/fpu/IntFPToVec.scala b/src/main/scala/xiangshan/backend/fu/fpu/IntFPToVec.scala
index 27df80a07c5..0f3f736f134 100644
--- a/src/main/scala/xiangshan/backend/fu/fpu/IntFPToVec.scala
+++ b/src/main/scala/xiangshan/backend/fu/fpu/IntFPToVec.scala
@@ -31,17 +31,44 @@ class IntFPToVec(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cf
   protected val in = io.in.bits
   protected val out = io.out.bits
 
+  private val isFliH = in.ctrl.fuOpType(7) &&  in.ctrl.fuOpType(6) && !in.ctrl.fuOpType(5)
+  private val isFliS = in.ctrl.fuOpType(7) && !in.ctrl.fuOpType(6) && !in.ctrl.fuOpType(5)
+  private val isFliD = in.ctrl.fuOpType(7) && !in.ctrl.fuOpType(6) &&  in.ctrl.fuOpType(5)
+  private val isFli = isFliH || isFliS || isFliD
+
+  private val FliData = Wire(UInt(XLEN.W))
+
+  private val FliHTable = Module(new FliHTable)
+  private val FliSTable = Module(new FliSTable)
+  private val FliDTable = Module(new FliDTable)
+
+  FliHTable.src := in.ctrl.fuOpType(4, 0)
+  FliSTable.src := in.ctrl.fuOpType(4, 0)
+  FliDTable.src := in.ctrl.fuOpType(4, 0)
+
+  FliData := Mux1H(
+    Seq(
+      isFliH,
+      isFliS,
+      isFliD
+    ),
+    Seq(
+      Cat(~0.U(48.W), FliHTable.out),
+      Cat(~0.U(32.W), FliSTable.out, 0.U(16.W)),
+      Cat(FliDTable.out, 0.U(48.W)))
+  )
+
   // vsew is the lowest 2 bits of fuOpType
-  private val isImm = IF2VectorType.isImm(in.ctrl.fuOpType(4, 2))
+  private val isImm = Mux(isFli, 0.U, IF2VectorType.isImm(in.ctrl.fuOpType(4, 2))).asBool
   // when needDup is true, the scalar data is duplicated in vector register
-  private val needDup = IF2VectorType.needDup(in.ctrl.fuOpType(4, 2))
+  private val needDup = Mux(isFli, 0.U, IF2VectorType.needDup(in.ctrl.fuOpType(4, 2))).asBool
   // when isFmv is true, the high bits of the scalar data is 1
-  private val isFmv = IF2VectorType.isFmv(in.ctrl.fuOpType(4, 2))
+  private val isFmv = Mux(isFli, 0.U, IF2VectorType.isFmv(in.ctrl.fuOpType(4, 2))).asBool
 
-  private val isFp = IF2VectorType.isFp(in.ctrl.fuOpType(4, 2))
+  private val isFp = Mux(isFli, 0.U, IF2VectorType.isFp(in.ctrl.fuOpType(4, 2))).asBool
 
   // imm use src(1), scalar use src(0)
-  private val scalaData = Mux(isImm, in.data.src(1), in.data.src(0))
+  private val scalaData = Mux(isFli, FliData, Mux(isImm, in.data.src(1), in.data.src(0)))
   // vsew is the lowest 2 bits of fuOpType
   private val vsew = in.ctrl.fuOpType(1, 0)
   private val dataWidth = cfg.destDataBits
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala b/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala
index 9027aa71a33..3029d72ed3f 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala
@@ -8,7 +8,8 @@ import utility.XSError
 import xiangshan.backend.fu.FuConfig
 import xiangshan.backend.fu.fpu.FpPipedFuncUnit
 import xiangshan.backend.fu.vector.Bundles.VSew
-import yunsuan.VfpuType
+import xiangshan.FuOpType
+import yunsuan.{VfcvtType, VfpuType}
 import yunsuan.scalar.FPCVT
 import yunsuan.util._
 
@@ -21,7 +22,11 @@ class FCVT(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
   private val src0 = inData.src(0)
   private val sew = fp_fmt
 
-  private val isRtz = opcode(2) & opcode(1)
+  private val isFround  = opcode === VfcvtType.fround
+  private val isFoundnx = opcode === VfcvtType.froundnx
+  private val isFcvtmod = opcode === VfcvtType.fcvtmod_w_d
+
+  private val isRtz = opcode(2) & opcode(1) | isFcvtmod
   private val isRod = opcode(2) & !opcode(1) & opcode(0)
   private val isFrm = !isRtz && !isRod
   private val vfcvtRm = Mux1H(
@@ -67,7 +72,7 @@ class FCVT(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
   val outIs16bits = RegNext(RegNext(outputWidth1H(1)))
   val outIs32bits = RegNext(RegNext(outputWidth1H(2)))
   val outIsInt = !outCtrl.fuOpType(6)
-  val outIsMvInst = outCtrl.fuOpType(8)
+  val outIsMvInst = outCtrl.fuOpType === FuOpType.FMVXF
 
   // modules
   val fcvt = Module(new FPCVT(XLEN))
@@ -77,6 +82,8 @@ class FCVT(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
   fcvt.io.sew := sew
   fcvt.io.rm := vfcvtRm
   fcvt.io.isFpToVecInst := true.B
+  fcvt.io.isFround := Cat(isFoundnx, isFround)
+  fcvt.io.isFcvtmod := isFcvtmod
 
 
   //cycle2
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala
index a154dabb29f..86d95ef23cc 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VCVT.scala
@@ -8,6 +8,7 @@ import utility.XSError
 import xiangshan.backend.fu.FuConfig
 import xiangshan.backend.fu.vector.{Mgu, VecPipedFuncUnit}
 import xiangshan.ExceptionNO
+import xiangshan.FuOpType
 import yunsuan.VfpuType
 import yunsuan.vector.VectorConvert.VectorCvt
 import yunsuan.util._
@@ -69,7 +70,7 @@ class VCVT(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg)
   val outputWidth1H = output1H
   val outIs32bits = RegNext(RegNext(outputWidth1H(2)))
   val outIsInt = !outCtrl.fuOpType(6)
-  val outIsMvInst = outCtrl.fuOpType(8)
+  val outIsMvInst = outCtrl.fuOpType === FuOpType.FMVXF
 
   val outEew = RegEnable(RegEnable(Mux1H(output1H, Seq(0,1,2,3).map(i => i.U)), fire), fireReg)
   private val needNoMask = outVecCtrl.fpu.isFpToVecInst
@@ -219,6 +220,8 @@ class VectorCvtTop(vlen: Int, xlen: Int) extends Module{
   vectorCvt0.sew := sew
   vectorCvt0.rm := rm
   vectorCvt0.isFpToVecInst := isFpToVecInst
+  vectorCvt0.isFround := 0.U
+  vectorCvt0.isFcvtmod := false.B
 
   val vectorCvt1 = Module(new VectorCvt(xlen))
   vectorCvt1.fire := fire
@@ -227,6 +230,8 @@ class VectorCvtTop(vlen: Int, xlen: Int) extends Module{
   vectorCvt1.sew := sew
   vectorCvt1.rm := rm
   vectorCvt1.isFpToVecInst := isFpToVecInst
+  vectorCvt1.isFround := 0.U
+  vectorCvt1.isFcvtmod := false.B
 
   val isNarrowCycle2 = RegEnable(RegEnable(isNarrow, fire), fireReg)
   val outputWidth1HCycle2 = RegEnable(RegEnable(outputWidth1H, fire), fireReg)
diff --git a/yunsuan b/yunsuan
index 2a514e02a87..f882cb4c2f1 160000
--- a/yunsuan
+++ b/yunsuan
@@ -1 +1 @@
-Subproject commit 2a514e02a8787ad14ac0c924d3f72e62036ac414
+Subproject commit f882cb4c2f1b1d07c665b2ef5e44b4d49ef70b22
```
