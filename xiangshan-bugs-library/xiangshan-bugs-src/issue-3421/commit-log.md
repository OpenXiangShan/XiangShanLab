# Commit Log
- Issue: #3421
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3421
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3421
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3421
- Changed files: 9
- Additions: 91
- Deletions: 38

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/decode/FPDecoder.scala`
- `src/main/scala/xiangshan/backend/decode/VecDecoder.scala`
- `src/main/scala/xiangshan/backend/fu/fpu/FPU.scala`
- `src/main/scala/xiangshan/backend/fu/fpu/IntToFP.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/package.scala`
- `yunsuan`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 17d0e72f176..99cb90d8dc5 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -381,13 +381,16 @@ object ScalarCryptoDecode extends DecodeConstants {
  */
 object FpDecode extends DecodeConstants{
   val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
+    FLH     -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.ldu, LSUOpType.lh, selImm = SelImm.IMM_I, fWen = T),
     FLW     -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.ldu, LSUOpType.lw, selImm = SelImm.IMM_I, fWen = T),
     FLD     -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.ldu, LSUOpType.ld, selImm = SelImm.IMM_I, fWen = T),
+    FSH     -> FDecode(SrcType.reg, SrcType.fp,  SrcType.X, FuType.stu, LSUOpType.sh, selImm = SelImm.IMM_S          ),
     FSW     -> FDecode(SrcType.reg, SrcType.fp,  SrcType.X, FuType.stu, LSUOpType.sw, selImm = SelImm.IMM_S          ),
     FSD     -> FDecode(SrcType.reg, SrcType.fp,  SrcType.X, FuType.stu, LSUOpType.sd, selImm = SelImm.IMM_S          ),
 
     FMV_D_X -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.i2v, IF2VectorType.FMX_D_X, fWen = T, canRobCompress = T),
     FMV_W_X -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.i2v, IF2VectorType.FMX_W_X, fWen = T, canRobCompress = T),
+    FMV_H_X -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.i2v, IF2VectorType.FMX_H_X, fWen = T, canRobCompress = T),
 
     // Int to FP
     FCVT_S_W  -> FDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.i2f, FuOpType.X, fWen = T, canRobCompress = T),
@@ -887,6 +890,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     FCVT_W_S, FCVT_WU_S, FCVT_L_S, FCVT_LU_S,
     FCVT_D_W, FCVT_D_WU, FCVT_D_L, FCVT_D_LU,
     FCVT_W_D, FCVT_WU_D, FCVT_L_D, FCVT_LU_D, FCVT_S_D, FCVT_D_S,
+    FCVT_S_H, FCVT_H_S, FCVT_H_D, FCVT_D_H,
     VFCVT_XU_F_V, VFCVT_X_F_V, VFCVT_RTZ_XU_F_V, VFCVT_RTZ_X_F_V, VFCVT_F_XU_V, VFCVT_F_X_V,
     VFWCVT_XU_F_V, VFWCVT_X_F_V, VFWCVT_RTZ_XU_F_V, VFWCVT_RTZ_X_F_V, VFWCVT_F_XU_V, VFWCVT_F_X_V, VFWCVT_F_F_V,
     VFNCVT_XU_F_W, VFNCVT_X_F_W, VFNCVT_RTZ_XU_F_W, VFNCVT_RTZ_X_F_W, VFNCVT_F_XU_W, VFNCVT_F_X_W, VFNCVT_F_F_W,
diff --git a/src/main/scala/xiangshan/backend/decode/FPDecoder.scala b/src/main/scala/xiangshan/backend/decode/FPDecoder.scala
index bf1463b642b..1d8055ebe85 100644
--- a/src/main/scala/xiangshan/backend/decode/FPDecoder.scala
+++ b/src/main/scala/xiangshan/backend/decode/FPDecoder.scala
@@ -46,7 +46,8 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
     // scalar cvt inst
     FCVT_W_S, FCVT_WU_S, FCVT_L_S, FCVT_LU_S,
     FCVT_W_D, FCVT_WU_D, FCVT_L_D, FCVT_LU_D, FCVT_S_D, FCVT_D_S,
-    FMV_X_W, FMV_X_D,
+    FCVT_S_H, FCVT_H_S, FCVT_H_D, FCVT_D_H,
+    FMV_X_W, FMV_X_D, FMV_X_H,
   )
   val isFpToVecInst = fpToVecInsts.map(io.instr === _).reduce(_ || _)
   val isFP32Instrs = Seq(
@@ -69,7 +70,18 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
     FCVT_W_D, FCVT_WU_D, FCVT_S_D, FCVT_D_S,
     FMV_X_W,
   )
-  val isSew2Cvt = isSew2Cvts.map(io.instr === _).reduce(_ || _)
+  /*
+  The optype for FCVT_D_H and FCVT_H_D is the same,
+  so the two instructions are distinguished by sew.
+  FCVT_H_D:VSew.e64
+  FCVT_D_H:VSew.e16
+   */
+  val isSew2Cvth = Seq(
+    FCVT_S_H, FCVT_H_S, FCVT_D_H,
+    FMV_X_H,
+  )
+  val isSew2Cvt32 = isSew2Cvts.map(io.instr === _).reduce(_ || _)
+  val isSew2Cvt16 = isSew2Cvth.map(io.instr === _).reduce(_ || _)
   val isLmulMf4Cvts = Seq(
     FCVT_W_S, FCVT_WU_S,
     FMV_X_W,
@@ -92,7 +104,7 @@ class FPToVecDecoder(implicit p: Parameters) extends XSModule {
   io.vpuCtrl.vill  := false.B
   io.vpuCtrl.vma   := true.B
   io.vpuCtrl.vta   := true.B
-  io.vpuCtrl.vsew  := Mux(isFP32Instr || isSew2Cvt, VSew.e32, VSew.e64)
+  io.vpuCtrl.vsew  := Mux(isFP32Instr || isSew2Cvt32, VSew.e32, Mux(isSew2Cvt16, VSew.e16, VSew.e64))
   io.vpuCtrl.vlmul := Mux(isFP32Instr || isLmulMf4Cvt, VLmul.mf4, VLmul.mf2)
   io.vpuCtrl.vm    := inst.VM
   io.vpuCtrl.nf    := inst.NF
@@ -118,9 +130,9 @@ class FPDecoder(implicit p: Parameters) extends XSModule{
   def X = BitPat("b?")
   def N = BitPat("b0")
   def Y = BitPat("b1")
-  val s = BitPat(FPU.S)
-  val d = BitPat(FPU.D)
-  val i = BitPat(FPU.D)
+  val s = BitPat(FPU.S(0))
+  val d = BitPat(FPU.D(0))
+  val i = BitPat(FPU.D(0))
 
   val default = List(X,X,X,N,N,N,X,X,X)
 
diff --git a/src/main/scala/xiangshan/backend/decode/VecDecoder.scala b/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
index cc664e81aed..568c03f2c99 100644
--- a/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
+++ b/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
@@ -511,9 +511,15 @@ object VecDecoder extends DecodeConstants {
     FCVT_LU_D -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.vfcvt_xufv,  T, F, F, UopSplitType.SCA_SIM),
     FCVT_S_D  -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.vfncvt_ffw,  F, T, F, UopSplitType.SCA_SIM),
     FCVT_D_S  -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.vfwcvt_ffv,  F, T, F, UopSplitType.SCA_SIM),
+    //Scala Half-Precision Float Convert Inst.
+    FCVT_H_S  -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.fcvt_h_s,  F, T, F, UopSplitType.SCA_SIM),
+    FCVT_S_H  -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.fcvt_s_h,  F, T, F, UopSplitType.SCA_SIM),
+    FCVT_H_D  -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.fcvt_h_d,  F, T, F, UopSplitType.SCA_SIM),
+    FCVT_D_H  -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, VfcvtType.fcvt_d_h,  F, T, F, UopSplitType.SCA_SIM),
     // Scalar Float Point f2i MV Inst.
     FMV_X_D -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, FuOpType.FMVXF, T, F, F, UopSplitType.SCA_SIM),
     FMV_X_W -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, FuOpType.FMVXF, T, F, F, UopSplitType.SCA_SIM),
+    FMV_X_H -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.fcvt, FuOpType.FMVXF, T, F, F, UopSplitType.SCA_SIM),
     // donot wflags
     FCLASS_S -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.falu, VfaluType.vfclass, T, F, F, UopSplitType.SCA_SIM),
     FCLASS_D -> OPFFF(SrcType.fp, SrcType.X, SrcType.X, FuType.falu, VfaluType.vfclass, T, F, F, UopSplitType.SCA_SIM),
diff --git a/src/main/scala/xiangshan/backend/fu/fpu/FPU.scala b/src/main/scala/xiangshan/backend/fu/fpu/FPU.scala
index 78c41efd740..2256426061f 100644
--- a/src/main/scala/xiangshan/backend/fu/fpu/FPU.scala
+++ b/src/main/scala/xiangshan/backend/fu/fpu/FPU.scala
@@ -11,37 +11,44 @@ object FPU {
     val len = expWidth + precision
   }
 
+  val f16 = FType(5, 11)
   val f32 = FType(8, 24)
   val f64 = FType(11, 53)
 
-  val ftypes = List(f32, f64)
+  // Appending f16 instead of pushing it from head to avoid potential encoding conflicts.
+  // Todo: use fmt field encoding in riscv FP instructions instead of customized encoding.
+  val ftypes = List(f32, f64, f16)
+  val ftypeWidth = log2Up(ftypes.length)
 
-  val S = ftypes.indexOf(f32).U(log2Ceil(ftypes.length).W)
-  val D = ftypes.indexOf(f64).U(log2Ceil(ftypes.length).W)
+  val S = ftypes.indexOf(f32).U(ftypeWidth.W)
+  val D = ftypes.indexOf(f64).U(ftypeWidth.W)
+  val H = ftypes.indexOf(f16).U(ftypeWidth.W)
 
+  // Produce zero-extended FPXX data
   def unbox(x: UInt, typeTag: UInt): UInt = {
     require(x.getWidth == 64)
-    val isBoxed = x.head(32).andR
-    Mux(typeTag === D,
-      x,
-      Mux(isBoxed,
-        x.tail(32),
-        FloatPoint.defaultNaNUInt(f32.expWidth, f32.precision)
-      )
-    )
+    require(typeTag.getWidth == ftypeWidth)
+    Mux1H(Seq(
+      (typeTag === D) -> x,
+      (typeTag === S) -> Mux(x.head(32).andR, x(f32.len - 1, 0), FloatPoint.defaultNaNUInt(f32.expWidth, f32.precision)),
+      (typeTag === H) -> Mux(x.head(48).andR, x(f16.len - 1, 0), FloatPoint.defaultNaNUInt(f16.expWidth, f16.precision)),
+    ))
   }
 
   def box(x: UInt, typeTag: UInt): UInt = {
     require(x.getWidth == 64)
-    Mux(typeTag === D, x, Cat(~0.U(32.W), x(31, 0)))
+    Mux1H(Seq(
+      (typeTag === D) -> x,
+      (typeTag === S) -> Cat(Fill(32, 1.U(1.W)), x(f32.len - 1, 0)),
+      (typeTag === H) -> Cat(Fill(48, 1.U(1.W)), x(f16.len - 1, 0)),
+    ))
   }
 
   def box(x: UInt, t: FType): UInt = {
-    if(t == f32){
-      Cat(~0.U(32.W), x(31, 0))
-    } else if(t == f64){
-      x(63, 0)
-    } else {
+    if      (t == f64) x(63, 0)
+    else if (t == f32) Cat(Fill(32, 1.U(1.W)), x(31, 0))
+    else if (t == f16) Cat(Fill(48, 1.U(1.W)), x(15, 0))
+    else {
       assert(cond = false, "Unknown ftype!")
       0.U
     }
diff --git a/src/main/scala/xiangshan/backend/fu/fpu/IntToFP.scala b/src/main/scala/xiangshan/backend/fu/fpu/IntToFP.scala
index da35d377848..e4e383543c3 100644
--- a/src/main/scala/xiangshan/backend/fu/fpu/IntToFP.scala
+++ b/src/main/scala/xiangshan/backend/fu/fpu/IntToFP.scala
@@ -56,7 +56,7 @@ class IntToFPDataModule(latency: Int)(implicit p: Parameters) extends FPUDataMod
   mux.exc := 0.U
 
   when(s2_wflags){
-    val i2fResults = for(t <- FPU.ftypes) yield {
+    val i2fResults = for(t <- FPU.ftypes.take(2)) yield {
       val i2f = Module(new fudian.IntToFP(t.expWidth, t.precision))
       i2f.io.sign := ~s2_typ(0)
       i2f.io.long := s2_typ(1)
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala b/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala
index 22638626817..9027aa71a33 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/FCVT.scala
@@ -7,8 +7,9 @@ import chisel3.util.experimental.decode._
 import utility.XSError
 import xiangshan.backend.fu.FuConfig
 import xiangshan.backend.fu.fpu.FpPipedFuncUnit
+import xiangshan.backend.fu.vector.Bundles.VSew
 import yunsuan.VfpuType
-import yunsuan.vector.VectorConvert.VectorCvt
+import yunsuan.scalar.FPCVT
 import yunsuan.util._
 
 
@@ -52,6 +53,9 @@ class FCVT(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
         BitPat("b10_00") -> BitPat("b0001"), // 8
         BitPat("b10_01") -> BitPat("b0010"), // 16
         BitPat("b10_10") -> BitPat("b0100"), // 32
+
+        BitPat("b11_01") -> BitPat("b1000"), // f16->f64/i64/ui64
+        BitPat("b11_11") -> BitPat("b0010"), // f64->f16
       ),
       BitPat.N(4)
     )
@@ -66,7 +70,7 @@ class FCVT(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
   val outIsMvInst = outCtrl.fuOpType(8)
 
   // modules
-  val fcvt = Module(new VectorCvt(XLEN))
+  val fcvt = Module(new FPCVT(XLEN))
   fcvt.io.fire := fire
   fcvt.io.src := src0
   fcvt.io.opType := opcode(7, 0)
@@ -79,21 +83,20 @@ class FCVT(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
   val isNarrowCycle2 = RegEnable(RegEnable(isNarrowCvt, fire), fireReg)
   val outputWidth1HCycle2 = RegEnable(RegEnable(outputWidth1H, fire), fireReg)
 
-  val fcvtResult = Mux(isNarrowCycle2, fcvt.io.result.tail(32), fcvt.io.result)
+  val fcvtResult = fcvt.io.result
+  io.out.bits.res.fflags.get := Mux(outIsMvInst, 0.U, fcvt.io.fflags)
 
-  val fcvtFflags = Mux1H(outputWidth1HCycle2, Seq(
-    fcvt.io.fflags,
-    Mux(isNarrowCycle2, fcvt.io.fflags.tail(10), fcvt.io.fflags),
-    Mux(isNarrowCycle2, fcvt.io.fflags(4,0), fcvt.io.fflags.tail(10)),
-    fcvt.io.fflags(4,0)
+  //fmv box
+  val result_fmv = Mux1H(Seq(
+    (sew === VSew.e8) -> Fill(56, src0(7)) ## src0(7, 0),
+    (sew === VSew.e16) -> Fill(48, src0(15)) ## src0(15, 0),
+    (sew === VSew.e32) -> Fill(32, src0(31)) ## src0(31, 0),
+    (sew === VSew.e64) -> src0,
   ))
-
-  io.out.bits.res.fflags.get := Mux(outIsMvInst, 0.U, fcvtFflags)
-
   // for scalar f2i cvt inst
   val isFpToInt32 = outIs32bits && outIsInt
   // for f2i mv inst
-  val result = Mux(outIsMvInst, RegNext(RegNext(src0)),
+  val result = Mux(outIsMvInst, RegEnable(RegEnable(result_fmv, fire), fireReg),
     // for scalar fp32 fp16 result
     Mux(
       outIs32bits && !outIsInt,
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index e482cebd7b1..3e9d714fc63 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -82,8 +82,9 @@ trait HasLoadHelper { this: XSModule =>
     val fpWen    = uop.fpWen
     val result = Cat(
       (fuOpType === LSUOpType.lw && fpWen),
+      (fuOpType === LSUOpType.lh && fpWen),
       (fuOpType === LSUOpType.lw && !fpWen) || (fuOpType === LSUOpType.hlvw),
-      (fuOpType === LSUOpType.lh)           || (fuOpType === LSUOpType.hlvh),
+      (fuOpType === LSUOpType.lh && !fpWen) || (fuOpType === LSUOpType.hlvh),
       (fuOpType === LSUOpType.lb)           || (fuOpType === LSUOpType.hlvb),
       (fuOpType === LSUOpType.ld)           || (fuOpType === LSUOpType.hlvd),
       (fuOpType === LSUOpType.lwu)          || (fuOpType === LSUOpType.hlvwu) || (fuOpType === LSUOpType.hlvxwu),
@@ -103,6 +104,7 @@ trait HasLoadHelper { this: XSModule =>
       SignExt(rdata(7, 0) , XLEN),
       SignExt(rdata(15, 0) , XLEN),
       SignExt(rdata(31, 0) , XLEN),
+      FPU.box(rdata, FPU.H),
       FPU.box(rdata, FPU.S)
     )
     Mux1H(select, selData)
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index 19fec71beb7..a7e2e6cdcfc 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -71,6 +71,24 @@ package object xiangshan {
     def FMVXF = BitPat("b1_1000_0000") //for fmv_x_d & fmv_x_w
   }
 
+  object I2fType {
+    // move/cvt ## i64/i32(input) ## f64/f32/f16(output) ## hassign
+    def fcvt_h_wu = BitPat("b0_0_00_0")
+    def fcvt_h_w  = BitPat("b0_0_00_1")
+    def fcvt_h_lu = BitPat("b0_1_00_0")
+    def fcvt_h_l  = BitPat("b0_1_00_1")
+
+    def fcvt_s_wu = BitPat("b0_0_01_0")
+    def fcvt_s_w  = BitPat("b0_0_01_1")
+    def fcvt_s_lu = BitPat("b0_1_01_0")
+    def fcvt_s_l  = BitPat("b0_1_01_1")
+
+    def fcvt_d_wu = BitPat("b0_0_10_0")
+    def fcvt_d_w  = BitPat("b0_0_10_1")
+    def fcvt_d_lu = BitPat("b0_1_10_0")
+    def fcvt_d_l  = BitPat("b0_1_10_1")
+
+  }
   object VlduType {
     // bit encoding: | vector or scala (2bit) || mop (2bit) | lumop(5bit) |
     // only unit-stride use lumop
@@ -139,6 +157,7 @@ package object xiangshan {
     def isFmv(bits: UInt): Bool = bits(0) & !bits(2)
     def FMX_D_X    = "b0_01_11".U
     def FMX_W_X    = "b0_01_10".U
+    def FMX_H_X   =  "b0_01_01".U
   }
 
   object CommitType {
diff --git a/yunsuan b/yunsuan
index fdd7611512c..2a514e02a87 160000
--- a/yunsuan
+++ b/yunsuan
@@ -1 +1 @@
-Subproject commit fdd7611512c29e31796c608cc58024be2ed3234f
+Subproject commit 2a514e02a8787ad14ac0c924d3f72e62036ac414
```
