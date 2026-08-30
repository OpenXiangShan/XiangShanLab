# Commit Log
- Issue: #3407
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3407
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3407
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3407
- Changed files: 21
- Additions: 315
- Deletions: 53

## Files
- `ready-to-run`
- `src/main/scala/utils/NamedUInt.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeStage.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala`
- `src/main/scala/xiangshan/frontend/IBuffer.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/PreDecode.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index a1f2d1a1c78..70b5df622e8 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit a1f2d1a1c78952af7f969aee6b60500ec4c57efe
+Subproject commit 70b5df622e85eafac511718547d4b2acee6b4e27
diff --git a/src/main/scala/utils/NamedUInt.scala b/src/main/scala/utils/NamedUInt.scala
index 4ee237afe6b..f5326ddbe83 100644
--- a/src/main/scala/utils/NamedUInt.scala
+++ b/src/main/scala/utils/NamedUInt.scala
@@ -14,4 +14,11 @@ abstract class NamedUInt(int : Int) {
   def apply(): UInt = UInt(width.W)
 
   def width: Int = int
+
+  protected def checkInputWidth(uint: UInt): Unit = {
+    require(
+      uint.getWidth == this.width,
+      s"the input UInt width(${uint.getWidth}) should be ${this.width}"
+    )
+  }
 }
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index e721346af73..3821487d1e8 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -405,6 +405,7 @@ class BackendImp(override val wrapper: Backend)(implicit p: Parameters) extends
   csrin.msiInfo.bits := RegEnable(io.fromTop.msiInfo.bits, io.fromTop.msiInfo.valid)
   csrin.clintTime.valid := RegNext(io.fromTop.clintTime.valid)
   csrin.clintTime.bits := RegEnable(io.fromTop.clintTime.bits, io.fromTop.clintTime.valid)
+  csrin.trapInstInfo := ctrlBlock.io.toCSR.trapInstInfo
 
   private val csrio = intExuBlock.io.csrio.get
   csrio.hartId := io.fromTop.hartId
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index d4ba6c4622c..aaeb109ee9f 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -140,6 +140,24 @@ object Bundles {
     }
   }
 
+  class TrapInstInfo(implicit p: Parameters) extends XSBundle {
+    val instr = UInt(32.W)
+    val ftqPtr = new FtqPtr
+    val ftqOffset = UInt(log2Up(PredictWidth).W)
+
+    def needFlush(ftqPtr: FtqPtr, ftqOffset: UInt): Bool ={
+      val sameFlush = this.ftqPtr === ftqPtr && this.ftqOffset > ftqOffset
+      sameFlush || isAfter(this.ftqPtr, ftqPtr)
+    }
+
+    def fromDecodedInst(decodedInst: DecodedInst): this.type = {
+      this.instr     := decodedInst.instr
+      this.ftqPtr    := decodedInst.ftqPtr
+      this.ftqOffset := decodedInst.ftqOffset
+      this
+    }
+  }
+
   // DecodedInst --[Rename]--> DynInst
   class DynInst(implicit p: Parameters) extends XSBundle {
     def numSrc          = backendParams.numSrc
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 083d5afd28e..17b2cadd4ad 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -24,7 +24,7 @@ import utility._
 import utils._
 import xiangshan.ExceptionNO._
 import xiangshan._
-import xiangshan.backend.Bundles.{DecodedInst, DynInst, ExceptionInfo, ExuOutput, StaticInst}
+import xiangshan.backend.Bundles.{DecodedInst, DynInst, ExceptionInfo, ExuOutput, StaticInst, TrapInstInfo}
 import xiangshan.backend.ctrlblock.{DebugLSIO, DebugLsInfoBundle, LsTopdownInfo, MemCtrl, RedirectGenerator}
 import xiangshan.backend.datapath.DataConfig.VAddrData
 import xiangshan.backend.decode.{DecodeStage, FusionDecoder}
@@ -623,6 +623,8 @@ class CtrlBlockImp(
   // backend to rob
   rob.io.vstartIsZero := io.toDecode.vstart === 0.U
 
+  io.toCSR.trapInstInfo := decode.io.toCSR.trapInstInfo
+
   io.debugTopDown.fromRob := rob.io.debugTopDown.toCore
   dispatch.io.debugTopDown.fromRob := rob.io.debugTopDown.toDispatch
   dispatch.io.debugTopDown.fromCore := io.debugTopDown.fromCore
@@ -662,6 +664,9 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
   val toExuBlock = new Bundle {
     val flush = ValidIO(new Redirect)
   }
+  val toCSR = new Bundle {
+    val trapInstInfo = Output(ValidIO(new TrapInstInfo))
+  }
   val intIQValidNumVec = Input(MixedVec(params.genIntIQValidNumBundle))
   val fpIQValidNumVec = Input(MixedVec(params.genFpIQValidNumBundle))
   val fromWB = new Bundle {
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
index ba3bacad88c..e5dd0eb73c3 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
@@ -28,6 +28,8 @@ import xiangshan.backend.fu.vector.Bundles.{VType, Vl}
 import xiangshan.backend.fu.FuType
 import xiangshan.backend.fu.wrapper.CSRToDecode
 import yunsuan.VpermType
+import xiangshan.ExceptionNO.{illegalInstr, virtualInstr}
+import xiangshan.frontend.FtqPtr
 
 class DecodeStage(implicit p: Parameters) extends XSModule
   with HasPerfEvents
@@ -55,6 +57,7 @@ class DecodeStage(implicit p: Parameters) extends XSModule
     val csrCtrl = Input(new CustomCSRCtrlIO)
     val fromCSR = Input(new CSRToDecode)
     val fusion = Vec(DecodeWidth - 1, Input(Bool()))
+
     // vtype update
     val isResumeVType = Input(Bool())
     val commitVType = new Bundle {
@@ -68,6 +71,10 @@ class DecodeStage(implicit p: Parameters) extends XSModule
     }
     val vsetvlVType = Input(VType())
     val vstart = Input(Vl())
+
+    val toCSR = new Bundle {
+      val trapInstInfo = ValidIO(new TrapInstInfo)
+    }
   })
 
   // io alias
@@ -97,6 +104,12 @@ class DecodeStage(implicit p: Parameters) extends XSModule
   val isSimpleVec = VecInit(inValids.zip(decoders.map(_.io.deq.isComplex)).map { case (valid, isComplex) => valid && !isComplex })
   val simpleDecodedInst = VecInit(decoders.map(_.io.deq.decodedInst))
 
+  val isIllegalInstVec = VecInit(inValids.zip(decoders.map(_.io.deq.decodedInst.exceptionVec)).map{
+    case (valid, exceptionVec) => valid && (exceptionVec(ExceptionNO.EX_II) || exceptionVec(ExceptionNO.EX_VI))
+  })
+  val hasIllegalInst =  Cat(isIllegalInstVec).orR
+  val illegalInst = PriorityMuxDefault(isIllegalInstVec.zip(decoders.map(_.io.deq.decodedInst)),0.U.asTypeOf(new DecodedInst))
+
   val complexNum = Wire(UInt(3.W))
   // (0, 1, 2, 3, 4, 5) + complexNum
   val complexNumAddLocation: Vec[UInt] = VecInit((0 until DecodeWidth).map(x => (x.U +& complexNum)))
@@ -180,6 +193,7 @@ class DecodeStage(implicit p: Parameters) extends XSModule
         "DecodeOut: can't wirte two regfile in one uop/instruction")
     }
   )
+
   for (i <- 0 until DecodeWidth) {
 
     // We use the lsrc/ldest before fusion decoder to read RAT for better timing.
@@ -218,6 +232,9 @@ class DecodeStage(implicit p: Parameters) extends XSModule
                in)
   }
 
+  io.toCSR.trapInstInfo.valid := hasIllegalInst
+  io.toCSR.trapInstInfo.bits.fromDecodedInst(illegalInst)
+
   XSPerfAccumulate("in_valid_count", PopCount(io.in.map(_.valid)))
   XSPerfAccumulate("in_fire_count", PopCount(io.in.map(_.fire)))
   XSPerfAccumulate("in_valid_not_ready_count", PopCount(io.in.map(x => x.valid && !x.ready)))
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 17d0e72f176..59b71baf0a5 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -24,7 +24,7 @@ import freechips.rocketchip.rocket.Instructions._
 import freechips.rocketchip.util.uintToBitPat
 import utility._
 import utils._
-import xiangshan.ExceptionNO.{breakPoint, illegalInstr, virtualInstr}
+import xiangshan.ExceptionNO.{EX_II, breakPoint, illegalInstr, virtualInstr}
 import xiangshan._
 import xiangshan.backend.fu.FuType
 import xiangshan.backend.Bundles.{DecodedInst, DynInst, StaticInst}
@@ -206,9 +206,9 @@ object XDecode extends DecodeConstants {
 
     // System, the immediate12 holds the CSR register.
 
-    CSRRW   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.wrt , SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
-    CSRRS   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.set , SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
-    CSRRC   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.clr , SelImm.IMM_I, xWen = T, noSpec = T, blockBack = T),
+    CSRRW   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.wrt , SelImm.IMM_Z, xWen = T, noSpec = T, blockBack = T),
+    CSRRS   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.set , SelImm.IMM_Z, xWen = T, noSpec = T, blockBack = T),
+    CSRRC   -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.clr , SelImm.IMM_Z, xWen = T, noSpec = T, blockBack = T),
 
     CSRRWI  -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.wrti, SelImm.IMM_Z, xWen = T, noSpec = T, blockBack = T),
     CSRRSI  -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X, FuType.csr, CSROpType.seti, SelImm.IMM_Z, xWen = T, noSpec = T, blockBack = T),
@@ -545,11 +545,31 @@ case class Imm_J() extends Imm(20){
   }
 }
 
-case class Imm_Z() extends Imm(12 + 5){
+case class Imm_Z() extends Imm(12 + 5 + 5){
   override def do_toImm32(minBits: UInt): UInt = minBits
 
   override def minBitsFromInstr(instr: UInt): UInt = {
-    Cat(instr(19, 15), instr(31, 20))
+    Cat(instr(11, 7), instr(19, 15), instr(31, 20))
+  }
+
+  def getCSRAddr(imm: UInt): UInt = {
+    require(imm.getWidth == this.len)
+    imm(11, 0)
+  }
+
+  def getRS1(imm: UInt): UInt = {
+    require(imm.getWidth == this.len)
+    imm(16, 12)
+  }
+
+  def getRD(imm: UInt): UInt = {
+    require(imm.getWidth == this.len)
+    imm(21, 17)
+  }
+
+  def getImm5(imm: UInt): UInt = {
+    require(imm.getWidth == this.len)
+    imm(16, 12)
   }
 }
 
@@ -806,7 +826,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     io.fromCSR.virtualInst.hlsv       && FuType.FuTypeOrR(decodedInst.fuType, FuType.stu)   && LSUOpType.isHsv(decodedInst.fuOpType) ||
     io.fromCSR.virtualInst.wfi        && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWfi(decodedInst.fuOpType)
 
-  decodedInst.exceptionVec(illegalInstr) := exceptionII
+  decodedInst.exceptionVec(illegalInstr) := exceptionII || io.enq.ctrlFlow.exceptionVec(EX_II)
   decodedInst.exceptionVec(virtualInstr) := exceptionVI
 
   //update exceptionVec: from frontend trigger's breakpoint exception. To reduce 1 bit of overhead in ibuffer entry.
@@ -1038,7 +1058,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   io.deq.decodedInst.fuOpType := MuxCase(decodedInst.fuOpType, Seq(
     isCsrrVl    -> VSETOpType.csrrvl,
     isCsrrVlenb -> ALUOpType.add,
-    isCSRR      -> CSROpType.ro,
   ))
 
   io.deq.decodedInst.blockBackward := MuxCase(decodedInst.blockBackward, Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/CSR.scala b/src/main/scala/xiangshan/backend/fu/CSR.scala
index 50521113634..97bb6de3d1d 100644
--- a/src/main/scala/xiangshan/backend/fu/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/CSR.scala
@@ -28,7 +28,7 @@ import xiangshan.ExceptionNO._
 import xiangshan._
 import xiangshan.backend.fu.util._
 import xiangshan.cache._
-import xiangshan.backend.Bundles.ExceptionInfo
+import xiangshan.backend.Bundles.{ExceptionInfo, TrapInstInfo}
 import xiangshan.backend.fu.NewCSR.CSRNamedConstant.ContextStatus
 import utils.MathUtils.{BigIntGenMask, BigIntNot}
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index 4505f82664a..0b3bcebd81c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -105,6 +105,7 @@ class TrapEntryEventInput(implicit val p: Parameters) extends Bundle with HasXSP
   val causeNO = Input(new CauseBundle)
   val trapPc = Input(UInt(VaddrMaxWidth.W))
   val trapPcGPA = Input(UInt(GPAddrBits.W))
+  val trapInst = Input(ValidIO(UInt(InstWidth.W)))
   val isCrossPageIPF = Input(Bool())
   val isHls = Input(Bool())
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
index ee556139c34..e037f7d971d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryHSEvent.scala
@@ -71,6 +71,8 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
 
   private val trapMemGPA = SignExt(in.memExceptionGPAddr, XLEN)
 
+  private val trapInst = Mux(in.trapInst.valid, in.trapInst.bits, 0.U)
+
   private val fetchIsVirt = current.iMode.isVirtual
   private val memIsVirt   = current.dMode.isVirtual
 
@@ -79,12 +81,13 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
   private val isBpExcp       = isException && ExceptionNO.EX_BP.U === highPrioTrapNO
   private val isHlsExcp      = isException && in.isHls
   private val fetchCrossPage = in.isCrossPageIPF
+  private val isIllegalInst  = isException && (ExceptionNO.EX_II.U === highPrioTrapNO || ExceptionNO.EX_VI.U === highPrioTrapNO)
 
   private val isLSGuestExcp    = isException && ExceptionNO.getLSGuestPageFault.map(_.U === highPrioTrapNO).reduce(_ || _)
   private val isFetchGuestExcp = isException && ExceptionNO.EX_IGPF.U === highPrioTrapNO
   // Software breakpoint exceptions are permitted to write either 0 or the pc to xtval
   // We fill pc here
-  private val tvalFillPc       = (isFetchExcp || isFetchGuestExcp) && !fetchCrossPage || isBpExcp 
+  private val tvalFillPc       = (isFetchExcp || isFetchGuestExcp) && !fetchCrossPage || isBpExcp
   private val tvalFillPcPlus2  = (isFetchExcp || isFetchGuestExcp) && fetchCrossPage
   private val tvalFillMemVaddr = isMemExcp
   private val tvalFillGVA      =
@@ -92,6 +95,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
     isLSGuestExcp|| isFetchGuestExcp ||
     (isFetchExcp || isBpExcp) && fetchIsVirt ||
     isMemExcp && memIsVirt
+  private val tvalFillInst     = isIllegalInst
 
   private val tval = Mux1H(Seq(
     (tvalFillPc                     ) -> trapPC,
@@ -99,6 +103,7 @@ class TrapEntryHSEventModule(implicit val p: Parameters) extends Module with CSR
     (tvalFillMemVaddr && !memIsVirt ) -> trapMemVA,
     (tvalFillMemVaddr &&  memIsVirt ) -> trapMemVA,
     (isLSGuestExcp                  ) -> trapMemVA,
+    (tvalFillInst                   ) -> trapInst,
   ))
 
   private val tval2 = Mux1H(Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
index 18d6b384c99..9ccc994317f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala
@@ -69,6 +69,8 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
 
   private val trapMemGPA = SignExt(in.memExceptionGPAddr, XLEN)
 
+  private val trapInst = Mux(in.trapInst.valid, in.trapInst.bits, 0.U)
+
   private val fetchIsVirt = iMode.isVirtual
   private val memIsVirt   = dMode.isVirtual
 
@@ -77,12 +79,13 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
   private val isBpExcp       = isException && ExceptionNO.EX_BP.U === highPrioTrapNO
   private val isHlsExcp      = isException && in.isHls
   private val fetchCrossPage = in.isCrossPageIPF
+  private val isIllegalInst  = isException && (ExceptionNO.EX_II.U === highPrioTrapNO || ExceptionNO.EX_VI.U === highPrioTrapNO)
 
   private val isLSGuestExcp    = isException && ExceptionNO.getLSGuestPageFault.map(_.U === highPrioTrapNO).reduce(_ || _)
   private val isFetchGuestExcp = isException && ExceptionNO.EX_IGPF.U === highPrioTrapNO
   // Software breakpoint exceptions are permitted to write either 0 or the pc to xtval
   // We fill pc here
-  private val tvalFillPc       = (isFetchExcp || isFetchGuestExcp) && !fetchCrossPage || isBpExcp 
+  private val tvalFillPc       = (isFetchExcp || isFetchGuestExcp) && !fetchCrossPage || isBpExcp
   private val tvalFillPcPlus2  = (isFetchExcp || isFetchGuestExcp) && fetchCrossPage
   private val tvalFillMemVaddr = isMemExcp
   private val tvalFillGVA      =
@@ -90,6 +93,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
     isLSGuestExcp|| isFetchGuestExcp ||
     (isFetchExcp || isBpExcp) && fetchIsVirt ||
     isMemExcp && memIsVirt
+  private val tvalFillInst     = isIllegalInst
 
   private val tval = Mux1H(Seq(
     (tvalFillPc                     ) -> trapPC,
@@ -97,6 +101,7 @@ class TrapEntryMEventModule(implicit val p: Parameters) extends Module with CSRE
     (tvalFillMemVaddr && !memIsVirt ) -> trapMemVA,
     (tvalFillMemVaddr &&  memIsVirt ) -> trapMemVA,
     (isLSGuestExcp                  ) -> trapMemVA,
+    (tvalFillInst                   ) -> trapInst,
   ))
 
   private val tval2 = Mux1H(Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
index f691fa8dee1..7eefeef89a0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryVSEvent.scala
@@ -78,6 +78,8 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   )
   private val trapMemGPA = SignExt(in.memExceptionGPAddr, XLEN)
 
+  private val trapInst = Mux(in.trapInst.valid, in.trapInst.bits, 0.U)
+
   private val fetchIsVirt = current.iMode.isVirtual
   private val memIsVirt   = current.dMode.isVirtual
 
@@ -85,6 +87,7 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   private val isMemExcp      = isException && Seq(EX_LAM, EX_LAF, EX_SAM, EX_SAF, EX_LPF, EX_SPF).map(_.U === highPrioTrapNO).reduce(_ || _)
   private val isBpExcp       = isException && EX_BP.U === highPrioTrapNO
   private val fetchCrossPage = in.isCrossPageIPF
+  private val isIllegalInst  = isException && (EX_II.U === highPrioTrapNO || EX_VI.U === highPrioTrapNO)
 
   // Software breakpoint exceptions are permitted to write either 0 or the pc to xtval
   // We fill pc here
@@ -94,12 +97,14 @@ class TrapEntryVSEventModule(implicit val p: Parameters) extends Module with CSR
   private val tvalFillGVA      =
     (isFetchExcp || isBpExcp) && fetchIsVirt ||
     isMemExcp && memIsVirt
+  private val tvalFillInst     = isIllegalInst
 
   private val tval = Mux1H(Seq(
     (tvalFillPc                     ) -> trapPC,
     (tvalFillPcPlus2                ) -> (trapPC + 2.U),
     (tvalFillMemVaddr && !memIsVirt ) -> trapMemVA,
     (tvalFillMemVaddr &&  memIsVirt ) -> trapMemVA,
+    (tvalFillInst                   ) -> trapInst,
   ))
 
   out := DontCare
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 9090de21d3d..dc68498849a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -18,6 +18,7 @@ import xiangshan.backend.fu.vector.Bundles.{Vl, Vstart, Vxrm, Vxsat}
 import xiangshan.backend.fu.wrapper.CSRToDecode
 import xiangshan._
 import xiangshan.backend.fu.PerfCounterIO
+import xiangshan.ExceptionNO._
 
 import scala.collection.immutable.SeqMap
 
@@ -39,6 +40,8 @@ object CSRConfig {
 
   final val VaddrMaxWidth = 48 + 2 // support Sv39/Sv48/Sv39x4/Sv48x4
 
+  final val InstWidth = 32
+
   final val XLEN = 64 // Todo: use XSParams
 
   final val VLEN = 128
@@ -100,6 +103,7 @@ class NewCSR(implicit val p: Parameters) extends Module
       val sret = Input(Bool())
       val dret = Input(Bool())
     }))
+    val trapInst = Input(ValidIO(UInt(InstWidth.W)))
     val fromMem = Input(new Bundle {
       val excpVA  = UInt(VaddrMaxWidth.W)
       val excpGPA = UInt(VaddrMaxWidth.W) // Todo: use guest physical address width
@@ -108,7 +112,7 @@ class NewCSR(implicit val p: Parameters) extends Module
       val trap = ValidIO(new Bundle {
         val pc = UInt(VaddrMaxWidth.W)
         val pcGPA = UInt(VaddrMaxWidth.W)
-        val instr = UInt(32.W)
+        val instr = UInt(InstWidth.W)
         val trapVec = UInt(64.W)
         val singleStep = Bool()
         val trigger = TriggerAction()
@@ -600,6 +604,7 @@ class NewCSR(implicit val p: Parameters) extends Module
         in.causeNO := trapHandleMod.io.out.causeNO
         in.trapPc := trapPC
         in.trapPcGPA := trapPCGPA // only used by trapEntryMEvent & trapEntryHSEvent
+        in.trapInst := io.trapInst
         in.isCrossPageIPF := trapIsCrossPageIPF
         in.isHls := trapIsHls
 
@@ -694,10 +699,9 @@ class NewCSR(implicit val p: Parameters) extends Module
     (addr === CSRs.mip.U) || (addr === CSRs.sip.U) || (addr === CSRs.vsip.U) ||
     (addr === CSRs.hip.U) || (addr === CSRs.mvip.U) || (addr === CSRs.hvip.U) ||
     Cat(aiaSkipCSRs.map(_.addr.U === addr)).orR ||
-    (addr === CSRs.stimecmp.U) ||
-    (addr === CSRs.mcounteren.U) ||
-    (addr === CSRs.scounteren.U) ||
-    (addr === CSRs.menvcfg.U)
+    (addr === CSRs.menvcfg.U) ||
+    (addr === CSRs.henvcfg.U) ||
+    (addr === CSRs.stimecmp.U)
   )
 
   // flush
@@ -803,6 +807,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     state === s_waitIMSIC && stateNext === s_idle
   io.out.bits.EX_II := permitMod.io.out.EX_II || imsic_EX_II || noCSRIllegal
   io.out.bits.EX_VI := permitMod.io.out.EX_VI || imsic_EX_VI
+
   io.out.bits.flushPipe := flushPipe
 
   io.out.bits.rData := MuxCase(0.U, Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
new file mode 100644
index 00000000000..dd6a68387da
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapInstMod.scala
@@ -0,0 +1,78 @@
+package xiangshan.backend.fu.NewCSR
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utility.{HasCircularQueuePtrHelper, XSError}
+import xiangshan._
+import xiangshan.backend.Bundles.TrapInstInfo
+import xiangshan.backend.decode.Imm_Z
+import xiangshan.frontend.FtqPtr
+import xiangshan.backend.decode.isa.bitfield.OPCODE5Bit
+
+class FtqInfo(implicit p: Parameters) extends XSBundle {
+  val ftqPtr = new FtqPtr()
+  val ftqOffset = UInt(log2Up(PredictWidth).W)
+}
+
+class TrapInstMod(implicit p: Parameters) extends Module with HasCircularQueuePtrHelper {
+  val io = IO(new Bundle {
+    val fromDecode = Input(new Bundle {
+      val trapInstInfo = ValidIO(new TrapInstInfo)
+    })
+
+    val fromRob = Input(new Bundle {
+      val flush = ValidIO(new FtqInfo)
+    })
+
+    val faultCsrUop = Input(ValidIO(new Bundle {
+      val fuOpType = FuOpType()
+      val imm      = UInt(Imm_Z().len.W)
+      val ftqInfo  = new FtqInfo
+    }))
+
+    val readClear = Input(Bool())
+    val currentTrapInst = Output(ValidIO(UInt(32.W)))
+  })
+
+  // alias
+  val flush = io.fromRob.flush
+  val newTrapInstInfo = io.fromDecode.trapInstInfo
+
+  val valid = RegInit(false.B)
+  val trapInstInfo = Reg(new TrapInstInfo)
+
+  val csrAddr = Imm_Z().getCSRAddr(io.faultCsrUop.bits.imm)
+  val rs1 = Imm_Z().getRS1(io.faultCsrUop.bits.imm)
+  val rd = Imm_Z().getRD(io.faultCsrUop.bits.imm)
+  val func3 = CSROpType.getFunc3(io.faultCsrUop.bits.fuOpType)
+
+  val csrInst = Cat(csrAddr, rs1, func3, rd, OPCODE5Bit.SYSTEM, "b11".U)
+  require(csrInst.getWidth == 32)
+
+  val newCSRInstValid = io.faultCsrUop.valid
+  val newCSRInst = WireInit(0.U.asTypeOf(new TrapInstInfo))
+  newCSRInst.instr := csrInst
+  newCSRInst.ftqPtr := io.faultCsrUop.bits.ftqInfo.ftqPtr
+  newCSRInst.ftqOffset := io.faultCsrUop.bits.ftqInfo.ftqOffset
+
+  when (flush.valid && valid && trapInstInfo.needFlush(flush.bits.ftqPtr, flush.bits.ftqOffset)) {
+    valid := false.B
+  }.elsewhen(io.readClear) {
+    valid := false.B
+  }.elsewhen(newCSRInstValid) {
+    valid := true.B
+    trapInstInfo := newCSRInst
+  }.elsewhen(newTrapInstInfo.valid && !valid) {
+    valid := true.B
+    trapInstInfo := newTrapInstInfo.bits
+    trapInstInfo.instr := Mux(
+      newTrapInstInfo.bits.instr(1, 0) === "b11".U,
+      newTrapInstInfo.bits.instr,
+      newTrapInstInfo.bits.instr(15, 0)
+    )
+  }
+
+  io.currentTrapInst.valid := valid
+  io.currentTrapInst.bits := trapInstInfo.instr
+}
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 162c2198458..76a06e2444d 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -10,10 +10,15 @@ import xiangshan.backend.fu.util._
 import xiangshan.backend.fu.{FuConfig, FuncUnit}
 import device._
 import system.HasSoCParameter
+import xiangshan.ExceptionNO._
+import xiangshan.backend.Bundles.TrapInstInfo
+import xiangshan.backend.decode.Imm_Z
 import xiangshan.backend.fu.NewCSR.CSRBundles.PrivState
 import xiangshan.backend.fu.NewCSR.CSRDefines.PrivMode
+import xiangshan.frontend.FtqPtr
 
 class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
+  with HasCircularQueuePtrHelper
 {
   val csrIn = io.csrio.get
   val csrOut = io.csrio.get
@@ -31,16 +36,19 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   val flushPipe = Wire(Bool())
   val flush = io.flush.valid
 
-  val (valid, src1, src2, func) = (
+  val (valid, src1, imm, func) = (
     io.in.valid,
     io.in.bits.data.src(0),
-    io.in.bits.data.imm,
+    io.in.bits.data.imm(Imm_Z().len - 1, 0),
     io.in.bits.ctrl.fuOpType
   )
 
-  // split imm from IMM_Z
-  val addr = src2(11, 0)
-  val csri = ZeroExt(src2(16, 12), XLEN)
+  // split imm/src1/rd from IMM_Z: src1/rd for tval
+  val addr = Imm_Z().getCSRAddr(imm)
+  val rd   = Imm_Z().getRD(imm)
+  val rs1  = Imm_Z().getRS1(imm)
+  val imm5 = Imm_Z().getImm5(imm)
+  val csri = ZeroExt(imm5, XLEN)
 
   import CSRConst._
 
@@ -53,6 +61,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   private val isCSRAcc = CSROpType.isCsrAccess(func)
 
   val csrMod = Module(new NewCSR)
+  val trapInstMod = Module(new TrapInstMod)
 
   private val privState = csrMod.io.status.privState
   // The real reg value in CSR, with no read mask
@@ -68,13 +77,20 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   ))
 
   private val csrAccess = valid && CSROpType.isCsrAccess(func)
-  private val csrWen = valid && CSROpType.notReadOnly(func)
+  private val csrWen = valid && (
+    CSROpType.isCSRRW(func) ||
+    CSROpType.isCSRRSorRC(func) && rs1 =/= 0.U
+  )
+  private val csrRen = valid && (
+    CSROpType.isCSRRW(func) && rd =/= 0.U ||
+    CSROpType.isCSRRSorRC(func)
+  )
 
   csrMod.io.in match {
     case in =>
       in.valid := valid
       in.bits.wen := csrWen
-      in.bits.ren := csrAccess
+      in.bits.ren := csrRen
       in.bits.op  := CSROpType.getCSROp(func)
       in.bits.addr := addr
       in.bits.src := src
@@ -83,6 +99,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       in.bits.sret := isSret
       in.bits.dret := isDret
   }
+  csrMod.io.trapInst := trapInstMod.io.currentTrapInst
   csrMod.io.fromMem.excpVA  := csrIn.memExceptionVAddr
   csrMod.io.fromMem.excpGPA := csrIn.memExceptionGPAddr
 
@@ -133,6 +150,21 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   private val csrModOutValid = csrMod.io.out.valid
   private val csrModOut      = csrMod.io.out.bits
 
+  trapInstMod.io.fromDecode.trapInstInfo := io.csrin.get.trapInstInfo
+  trapInstMod.io.fromRob.flush.valid := io.flush.valid
+  trapInstMod.io.fromRob.flush.bits.ftqPtr := io.flush.bits.ftqIdx
+  trapInstMod.io.fromRob.flush.bits.ftqOffset := io.flush.bits.ftqOffset
+  trapInstMod.io.faultCsrUop.valid         := csrMod.io.out.valid && (csrMod.io.out.bits.EX_II || csrMod.io.out.bits.EX_VI)
+  trapInstMod.io.faultCsrUop.bits.fuOpType := DataHoldBypass(io.in.bits.ctrl.fuOpType, io.in.fire)
+  trapInstMod.io.faultCsrUop.bits.imm      := DataHoldBypass(io.in.bits.data.imm, io.in.fire)
+  trapInstMod.io.faultCsrUop.bits.ftqInfo.ftqPtr    := DataHoldBypass(io.in.bits.ctrl.ftqIdx.get, io.in.fire)
+  trapInstMod.io.faultCsrUop.bits.ftqInfo.ftqOffset := DataHoldBypass(io.in.bits.ctrl.ftqOffset.get, io.in.fire)
+  // Clear trap instruction when instruction fault trap(EX_II, EX_VI) occurs.
+  trapInstMod.io.readClear := (csrMod.io.fromRob.trap match {
+    case t =>
+      t.valid && !t.bits.isInterrupt && (t.bits.trapVec(EX_II) || t.bits.trapVec(EX_VI))
+  })
+
   private val imsic = Module(new IMSIC(NumVSIRFiles = 5, NumHart = 1, XLEN = 64, NumIRSrc = 256))
   imsic.i.hartId := io.csrin.get.hartId
   imsic.i.msiInfo := io.csrin.get.msiInfo
@@ -159,7 +191,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   csrMod.fromAIA.vstopei := imsic.o.vstopei
 
   private val exceptionVec = WireInit(0.U.asTypeOf(ExceptionVec())) // Todo:
-  import ExceptionNO._
+
   exceptionVec(EX_BP    ) := isEbreak
   exceptionVec(EX_MCALL ) := isEcall && privState.isModeM
   exceptionVec(EX_HSCALL) := isEcall && privState.isModeHS
@@ -295,6 +327,7 @@ class CSRInput(implicit p: Parameters) extends XSBundle with HasSoCParameter{
   val hartId = Input(UInt(8.W))
   val msiInfo = Input(ValidIO(new MsiInfoBundle))
   val clintTime = Input(ValidIO(UInt(64.W)))
+  val trapInstInfo = Input(ValidIO(new TrapInstInfo))
 }
 
 class CSRToDecode(implicit p: Parameters) extends XSBundle {
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index ac8891a7428..526beb3eec4 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -156,8 +156,8 @@ class FrontendImp (outer: Frontend) extends LazyModuleImp(outer)
   ifu.io.toIbuffer    <> ibuffer.io.in
 
   ftq.io.fromBackend <> io.backend.toFtq
-  io.backend.fromFtq <> ftq.io.toBackend
-  io.backend.fromIfu <> ifu.io.toBackend
+  io.backend.fromFtq := ftq.io.toBackend
+  io.backend.fromIfu := ifu.io.toBackend
   io.frontendInfo.bpuInfo <> ftq.io.bpuInfo
 
   val checkPcMem = Reg(Vec(FtqSize, new Ftq_RF_Components))
diff --git a/src/main/scala/xiangshan/frontend/FrontendBundle.scala b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
index 0492f8a43a3..711ec071d14 100644
--- a/src/main/scala/xiangshan/frontend/FrontendBundle.scala
+++ b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
@@ -234,6 +234,7 @@ class FetchToIBuffer(implicit p: Parameters) extends XSBundle {
   val ftqOffset    = Vec(PredictWidth, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W)))
   val exceptionType = Vec(PredictWidth, UInt(ExceptionType.width.W))
   val crossPageIPFFix = Vec(PredictWidth, Bool())
+  val illegalInstr = Vec(PredictWidth, Bool())
   val triggered    = Vec(PredictWidth, TriggerAction())
   val topdown_info = new FrontendTopDownBundle
 }
diff --git a/src/main/scala/xiangshan/frontend/IBuffer.scala b/src/main/scala/xiangshan/frontend/IBuffer.scala
index a283b35efcf..042ccb15c78 100644
--- a/src/main/scala/xiangshan/frontend/IBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/IBuffer.scala
@@ -63,8 +63,7 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
   val pred_taken = Bool()
   val ftqPtr = new FtqPtr
   val ftqOffset = UInt(log2Ceil(PredictWidth).W)
-  val exceptionType = UInt(ExceptionType.width.W)
-  val crossPageIPFFix = Bool()
+  val exceptionType = IBufferExceptionType()
   val triggered = TriggerAction()
 
   def fromFetch(fetch: FetchToIBuffer, i: Int): IBufEntry = {
@@ -75,8 +74,11 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
     pred_taken := fetch.ftqOffset(i).valid
     ftqPtr := fetch.ftqPtr
     ftqOffset := fetch.ftqOffset(i).bits
-    exceptionType := fetch.exceptionType(i)
-    crossPageIPFFix := fetch.crossPageIPFFix(i)
+    exceptionType := IBufferExceptionType.cvtFromFetchExcpAndCrossPageAndRVCII(
+      fetch.exceptionType(i),
+      fetch.crossPageIPFFix(i),
+      fetch.illegalInstr(i),
+    )
     triggered := fetch.triggered(i)
     this
   }
@@ -87,13 +89,14 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
     cf.pc := pc
     cf.foldpc := foldpc
     cf.exceptionVec := 0.U.asTypeOf(ExceptionVec())
-    cf.exceptionVec(instrPageFault) := exceptionType === ExceptionType.pf
-    cf.exceptionVec(instrGuestPageFault) := exceptionType === ExceptionType.gpf
-    cf.exceptionVec(instrAccessFault) := exceptionType === ExceptionType.af
+    cf.exceptionVec(instrPageFault)      := IBufferExceptionType.isPF (this.exceptionType)
+    cf.exceptionVec(instrGuestPageFault) := IBufferExceptionType.isGPF(this.exceptionType)
+    cf.exceptionVec(instrAccessFault)    := IBufferExceptionType.isAF (this.exceptionType)
+    cf.exceptionVec(EX_II)               := IBufferExceptionType.isRVCII(this.exceptionType)
     cf.trigger := triggered
     cf.pd := pd
     cf.pred_taken := pred_taken
-    cf.crossPageIPFFix := crossPageIPFFix
+    cf.crossPageIPFFix := IBufferExceptionType.isCrossPage(this.exceptionType)
     cf.storeSetHit := DontCare
     cf.waitForRobIdx := DontCare
     cf.loadWaitBit := DontCare
@@ -103,6 +106,45 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
     cf.ftqOffset := ftqOffset
     cf
   }
+
+  object IBufferExceptionType extends NamedUInt(3) {
+    def None         = "b000".U
+    def NonCrossPF   = "b001".U
+    def NonCrossGPF  = "b010".U
+    def NonCrossAF   = "b011".U
+    // illegal instruction
+    def rvcII        = "b100".U
+    def CrossPF      = "b101".U
+    def CrossGPF     = "b110".U
+    def CrossAF      = "b111".U
+
+    def cvtFromFetchExcpAndCrossPageAndRVCII(fetchExcp: UInt, crossPage: Bool, rvcIll: Bool): UInt = {
+      require(
+        fetchExcp.getWidth == ExceptionType.width,
+        s"The width(${fetchExcp.getWidth}) of fetchExcp should be equal to " +
+        s"the width(${ExceptionType.width}) of frontend.ExceptionType."
+      )
+      MuxCase(0.U, Seq(
+        crossPage     -> Cat(1.U(1.W), fetchExcp),
+        fetchExcp.orR -> fetchExcp,
+        rvcIll        -> this.rvcII,
+      ))
+    }
+
+    def isRVCII(uint: UInt): Bool = {
+      this.checkInputWidth(uint)
+      uint(2) && uint(1, 0) === 0.U
+    }
+
+    def isCrossPage(uint: UInt): Bool = {
+      this.checkInputWidth(uint)
+      uint(2) && uint(1, 0) =/= 0.U
+    }
+
+    def isPF (uint: UInt): Bool = uint(1, 0) === this.NonCrossPF (1, 0)
+    def isGPF(uint: UInt): Bool = uint(1, 0) === this.NonCrossGPF(1, 0)
+    def isAF (uint: UInt): Bool = uint(1, 0) === this.NonCrossAF (1, 0)
+  }
 }
 
 class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper with HasPerfEvents {
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 6b6a56274e1..c327d8d8a09 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -129,6 +129,7 @@ class IfuWbToFtqDB extends Bundle {
 
 class NewIFU(implicit p: Parameters) extends XSModule
   with HasICacheParameters
+  with HasXSParameter
   with HasIFUConst
   with HasPdConst
   with HasCircularQueuePtrHelper
@@ -488,6 +489,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
     ******************************************************************************
     */
 
+  val expanders = Seq.fill(PredictWidth)(Module(new RVCExpander))
+
   val f3_valid          = RegInit(false.B)
   val f3_ftq_req        = RegEnable(f2_ftq_req,    f2_fire)
   // val f3_situation      = RegEnable(f2_situation,  f2_fire)
@@ -499,13 +502,17 @@ class NewIFU(implicit p: Parameters) extends XSModule
   val f3_exception      = RegEnable(f2_exception,  f2_fire)
   val f3_mmio           = RegEnable(f2_mmio,       f2_fire)
 
-  //val f3_expd_instr     = RegEnable(f2_expd_instr,  f2_fire)
   val f3_instr          = RegEnable(f2_instr, f2_fire)
-  val f3_expd_instr     = VecInit((0 until PredictWidth).map{ i =>
-    val expander       = Module(new RVCExpander)
+
+  expanders.zipWithIndex.foreach { case (expander, i) =>
     expander.io.in := f3_instr(i)
-    expander.io.out.bits
+  }
+  // Use expanded instruction only when input is legal.
+  // Otherwise use origin illegal RVC instruction.
+  val f3_expd_instr     = VecInit(expanders.map { expander: RVCExpander =>
+    Mux(expander.io.ill, expander.io.in, expander.io.out.bits)
   })
+  val f3_ill            = VecInit(expanders.map(_.io.ill))
 
   val f3_pd_wire         = RegEnable(f2_pd,            f2_fire)
   val f3_pd              = WireInit(f3_pd_wire)
@@ -813,6 +820,7 @@ class NewIFU(implicit p: Parameters) extends XSModule
   io.toIbuffer.bits.foldpc      := f3_foldpc
   io.toIbuffer.bits.exceptionType := ExceptionType.merge(f3_exception_vec, f3_crossPage_exception_vec)
   io.toIbuffer.bits.crossPageIPFFix := f3_crossPage_exception_vec.map(_ =/= ExceptionType.none)
+  io.toIbuffer.bits.illegalInstr:= f3_ill
   io.toIbuffer.bits.triggered   := f3_triggered
 
   when(f3_lastHalf.valid){
@@ -855,6 +863,9 @@ class NewIFU(implicit p: Parameters) extends XSModule
   mmioFlushWb.bits.jalTarget  := DontCare
   mmioFlushWb.bits.instrRange := f3_mmio_range
 
+  val mmioRVCExpander = Module(new RVCExpander)
+  mmioRVCExpander.io.in := Mux(f3_req_is_mmio, Cat(f3_mmio_data(1), f3_mmio_data(0)), 0.U)
+
   /** external predecode for MMIO instruction */
   when(f3_req_is_mmio){
     val inst  = Cat(f3_mmio_data(1), f3_mmio_data(0))
@@ -864,8 +875,7 @@ class NewIFU(implicit p: Parameters) extends XSModule
     val jalOffset = jal_offset(inst, currentIsRVC)
     val brOffset  = br_offset(inst, currentIsRVC)
 
-    io.toIbuffer.bits.instrs(0) := new RVCDecoder(inst, XLEN, fLen, useAddiForMv = true).decode.bits
-
+    io.toIbuffer.bits.instrs(0) := Mux(mmioRVCExpander.io.ill, mmioRVCExpander.io.in, mmioRVCExpander.io.out.bits)
 
     io.toIbuffer.bits.pd(0).valid   := true.B
     io.toIbuffer.bits.pd(0).isRVC   := currentIsRVC
@@ -875,6 +885,7 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
     io.toIbuffer.bits.exceptionType(0)   := mmio_resend_exception
     io.toIbuffer.bits.crossPageIPFFix(0) := mmio_resend_exception =/= ExceptionType.none
+    io.toIbuffer.bits.illegalInstr(0)  := mmioRVCExpander.io.ill
 
     io.toIbuffer.bits.enqEnable   := f3_mmio_range.asUInt
 
diff --git a/src/main/scala/xiangshan/frontend/PreDecode.scala b/src/main/scala/xiangshan/frontend/PreDecode.scala
index b8ab16d809b..485321754ef 100644
--- a/src/main/scala/xiangshan/frontend/PreDecode.scala
+++ b/src/main/scala/xiangshan/frontend/PreDecode.scala
@@ -273,12 +273,17 @@ class RVCExpander(implicit p: Parameters) extends XSModule {
   val io = IO(new Bundle {
     val in = Input(UInt(32.W))
     val out = Output(new ExpandedInstruction)
+    val ill = Output(Bool())
   })
 
+  val decoder = new RVCDecoder(io.in, XLEN, fLen, useAddiForMv = true)
+
   if (HasCExtension) {
-    io.out := new RVCDecoder(io.in, XLEN, fLen, useAddiForMv = true).decode
+    io.out := decoder.decode
+    io.ill := decoder.ill
   } else {
-    io.out := new RVCDecoder(io.in, XLEN, fLen, useAddiForMv = true).passthrough
+    io.out := decoder.passthrough
+    io.ill := false.B
   }
 }
 
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index 19fec71beb7..36a13a23e45 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -208,24 +208,28 @@ package object xiangshan {
 
 
   object CSROpType {
-    def jmp  = "b010_000".U
-    def wfi  = "b100_000".U
-    def wrt  = "b001_001".U
-    def set  = "b001_010".U
-    def clr  = "b001_011".U
-    def wrti = "b001_101".U
-    def seti = "b001_110".U
-    def clri = "b001_111".U
-    def ro   = "b001_000".U
+    //               | func3|
+    def jmp   = "b010_000".U
+    def wfi   = "b100_000".U
+    def wrt   = "b001_001".U
+    def set   = "b001_010".U
+    def clr   = "b001_011".U
+    def wrti  = "b001_101".U
+    def seti  = "b001_110".U
+    def clri  = "b001_111".U
 
     def isSystemOp (op: UInt): Bool = op(4)
     def isWfi      (op: UInt): Bool = op(5)
     def isCsrAccess(op: UInt): Bool = op(3)
     def isReadOnly (op: UInt): Bool = op(3) && op(2, 0) === 0.U
     def notReadOnly(op: UInt): Bool = op(3) && op(2, 0) =/= 0.U
+    def isCSRRW    (op: UInt): Bool = op(3) && op(1, 0) === "b01".U
+    def isCSRRSorRC(op: UInt): Bool = op(3) && op(1)
 
     def getCSROp(op: UInt) = op(1, 0)
     def needImm(op: UInt) = op(2)
+
+    def getFunc3(op: UInt) = op(2, 0)
   }
 
   // jump
```
