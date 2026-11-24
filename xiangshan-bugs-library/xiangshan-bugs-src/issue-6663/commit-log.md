# Commit Log
- Issue: #6663
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6663
- Issue state: closed
- Tested RTL commit: 09453ff8f44e9d2b526e6149c60e1dc3f08c7192
- Related PR: #5915
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5915
- Changed files: 21
- Additions: 2263
- Deletions: 131

## Files
- `rocket-chip`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/Fence.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/util/CSRConst.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUConst.scala`
- `src/main/scala/xiangshan/cache/mmu/MptChecker.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`
- `src/main/scala/xiangshan/cache/mmu/Repeater.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/rocket-chip b/rocket-chip
index 18f902dea0c..c4ba35a8075 160000
--- a/rocket-chip
+++ b/rocket-chip
@@ -1 +1 @@
-Subproject commit 18f902dea0cc94f7a0ec623fce946ec87ded4344
+Subproject commit c4ba35a80758881da59399b693a6b51448eea1e4
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 52feab03277..b42d6c51d3f 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -518,11 +518,23 @@ class TlbMbmcBundle(implicit p: Parameters) extends MbmcStruct {
   }
 }
 
+class MmptStruct(implicit p: Parameters) extends XSBundle { // add new mpt csr 
+    val mode = UInt(4.W)
+    val sdid = UInt(6.W)
+    val optOutInNode = UInt(1.W) // skip intermediate node MPT check
+    val ppn  = UInt(44.W)
+}
+
+class TlbMmptBundle(implicit p: Parameters) extends MmptStruct {
+  val changed = Bool()
+}
+
 class TlbCsrBundle(implicit p: Parameters) extends XSBundle {
   val satp = new TlbSatpBundle()
   val vsatp = new TlbSatpBundle()
   val hgatp = new TlbHgatpBundle()
   val mbmc = new TlbMbmcBundle()
+  val mmpt = new TlbMmptBundle() // mpt csr
   val priv = new Bundle {
     val mxr = Bool()
     val sum = Bool()
@@ -556,10 +568,11 @@ class SfenceBundle(implicit p: Parameters) extends XSBundle {
     val rs1 = Bool()
     val rs2 = Bool()
     val addr = UInt(VAddrBits.W)
-    val id = UInt((AsidLength).W) // asid or vmid
+    val id = UInt((AsidLength).W) // asid or vmid or SDID
     val flushPipe = Bool()
     val hv = Bool()
     val hg = Bool()
+    val mfence = Option.when(HasMptCheck) (Bool())
   }
 
   override def toPrintable: Printable = {
@@ -567,6 +580,7 @@ class SfenceBundle(implicit p: Parameters) extends XSBundle {
   }
 }
 
+
 // Bundle for load violation predictor updating
 class MemPredUpdateReq(implicit p: Parameters) extends XSBundle  {
   val valid = Bool()
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 66b611acb09..b20cfd68272 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -53,6 +53,10 @@ case class XSCoreParameters
   VLEN: Int = 128,
   ELEN: Int = 64,
   HSXLEN: Int = 64,
+  HasMptCheck: Boolean = false, //enable mpt
+  HasMptCheckDefault: Boolean = false, // hardwired testing code: fake 2M MPT table
+  HasMptCheckDefault4k: Boolean = false, // hardwired testing code: fake 4k MPT table
+  HasMptInodeOpt: Boolean = false, // hardwired testing code: skip mpt check for non-leaf ptw nodes
   HasBitmapCheck: Boolean = true,
   HasBitmapCheckDefault: Boolean = false,
   HasMExtension: Boolean = true,
@@ -70,6 +74,7 @@ case class XSCoreParameters
   HasVPU: Boolean = true,
   HasCustomCSRCacheOp: Boolean = true,
   AsidLength: Int = 16,
+  SdidLength: Int = 6,
   VmidLength: Int = 14,
   EnbaleTlbDebug: Boolean = false,
   EnableClockGate: Boolean = true,
@@ -567,7 +572,11 @@ trait HasXSParameter {
   val fLen = 64
   def hartIdLen = p(MaxHartIdBits)
   val xLen = XLEN
-
+  assert(!(HasMptCheck == true && HasBitmapCheck == true), "Conflicts: MPT and Bitmap can't be used together")
+  def HasMptCheck = coreParams.HasMptCheck && !coreParams.HasBitmapCheck
+  def HasMptCheckDefault = coreParams.HasMptCheckDefault
+  def HasMptCheckDefault4k = coreParams.HasMptCheckDefault4k
+  def HasMptInodeOpt = coreParams.HasMptInodeOpt
   def HasBitmapCheck = coreParams.HasBitmapCheck
   def HasBitmapCheckDefault = coreParams.HasBitmapCheckDefault
 
@@ -617,7 +626,7 @@ trait HasXSParameter {
       coreParams.VAddrBitsSv39 max coreParams.GPAddrBitsSv39x4
     }
   }
-
+  def SdidLength = coreParams.SdidLength
   def AsidLength = coreParams.AsidLength
   def VmidLength = coreParams.VmidLength
   def ReSelectLen = coreParams.ReSelectLen
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index de2a24d67b3..6a21b04fe85 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -513,6 +513,11 @@ object HypervisorDecode extends DecodeConstants {
   )
 }
 
+object MptFenceDecode extends DecodeConstants {
+  override val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
+    MFENCE -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.mfence, SelImm.X, noSpec = T, blockBack = T, flushPipe = T),
+  )
+}
 object ZicondDecode extends DecodeConstants {
   override val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
     CZERO_EQZ   -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.alu, ALUOpType.czero_eqz, SelImm.X, xWen = T, canRobCompress = T),
@@ -794,7 +799,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   val ctrl_flow = io.enq.decodeInUop // input with RVC Expanded
 
   private val inst: XSInstBitFields = io.enq.decodeInUop.instr.asTypeOf(new XSInstBitFields)
-
   val decode_table: Array[(BitPat, List[BitPat])] = XDecode.table ++
     FpDecode.table ++
 //    FDivSqrtDecode.table ++
@@ -807,7 +811,8 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     VecDecoder.table ++
     ZicondDecode.table ++
     ZimopDecode.table ++
-    ZfaDecode.table
+    ZfaDecode.table ++
+    (if (HasMptCheck) MptFenceDecode.table else Array.empty[(BitPat, List[BitPat])])
   require(decode_table.map(_._2.length == 14).reduce(_ && _), "Decode tables have different column size")
   // assertion for LUI: only LUI should be assigned `selImm === SelImm.IMM_U && fuType === FuType.alu`
   val luiMatch = (t: Seq[BitPat]) => t(3).value == FuType.alu.ohid && t.reverse.head.value == SelImm.IMM_U.litValue
@@ -876,6 +881,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
 
   private val exceptionII =
     decodedInst.selImm === SelImm.INVALID_INSTR ||
+    (if (HasMptCheck) (io.fromCSR.illegalInst.mfence.get && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.mfence) else false.B) ||
     io.fromCSR.illegalInst.sfenceVMA  && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.sfence  ||
     io.fromCSR.illegalInst.sfencePart && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.nofence ||
     io.fromCSR.illegalInst.hfenceGVMA && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.hfence_g ||
diff --git a/src/main/scala/xiangshan/backend/fu/Fence.scala b/src/main/scala/xiangshan/backend/fu/Fence.scala
index b0641e4061b..1d4a4fd603a 100644
--- a/src/main/scala/xiangshan/backend/fu/Fence.scala
+++ b/src/main/scala/xiangshan/backend/fu/Fence.scala
@@ -63,7 +63,7 @@ class Fence(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   // NOTE: icache & tlb & sbuffer must receive flush signal at any time
   sbuffer      := state === s_wait
   fencei       := state === s_icache
-  sfence.valid := state === s_tlb && (func === FenceOpType.sfence || func === FenceOpType.hfence_v || func === FenceOpType.hfence_g)
+  sfence.valid := state === s_tlb && (func === FenceOpType.sfence || func === FenceOpType.hfence_v || func === FenceOpType.hfence_g || (if (HasMptCheck) (func === FenceOpType.mfence) else false.B))
   sfence.bits.rs1  := uop.data.imm(4, 0) === 0.U
   sfence.bits.rs2  := uop.data.imm(9, 5) === 0.U
   sfence.bits.flushPipe := uop.ctrl.flushPipe.get
@@ -71,10 +71,14 @@ class Fence(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   sfence.bits.hg := func === FenceOpType.hfence_g
   sfence.bits.addr := RegEnable(io.in.bits.data.src(0), io.in.fire)
   sfence.bits.id   := RegEnable(io.in.bits.data.src(1), io.in.fire)
+  
+  if (HasMptCheck) {
+    sfence.bits.mfence.get := func === FenceOpType.mfence
+  } // TODO:implement remaining mfence functionality, not yet finished!!!
 
   when (state === s_idle && io.in.valid) { state := s_wait }
   when (state === s_wait && func === FenceOpType.fencei && sbEmpty) { state := s_icache }
-  when (state === s_wait && ((func === FenceOpType.sfence || func === FenceOpType.hfence_g || func === FenceOpType.hfence_v) && sbEmpty)) { state := s_tlb }
+  when (state === s_wait && ((func === FenceOpType.sfence || func === FenceOpType.hfence_g || func === FenceOpType.hfence_v || (if (HasMptCheck) (func === FenceOpType.mfence) else false.B)) && sbEmpty)) { state := s_tlb }
   when (state === s_wait && func === FenceOpType.fence  && sbEmpty) { state := s_fence }
   when (state === s_wait && func === FenceOpType.nofence  && sbEmpty) { state := s_nofence }
   when (state =/= s_idle && state =/= s_wait) { state := s_idle }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala
index c2171e4c43f..7a3073a0f9e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala
@@ -183,6 +183,15 @@ object CSRDefines {
     override protected def legalValues: Seq[EnumType] = Seq(Bare, Sv39x4, Sv48x4)
   }
 
+  object MmptMode extends CSREnum with WARLApply {
+    val Bare   = Value(0.U)
+    val Smmpt43 = Value(1.U)
+    val Smmpt52 = Value(2.U)
+    val smmpt64 = Value(3.U)
+    // XiangShan only support Sv39 & Sv48 Page
+    override def isLegal(enumeration: CSREnumType): Bool = enumeration.isOneOf(Bare, Smmpt43, Smmpt52)
+  }
+
   object EnvCBIE extends CSREnum with WARLApply {
     val Off   = Value("b00".U)
     val Flush = Value("b01".U)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index c2612a9e6c2..adc6b27b6a0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -147,6 +147,7 @@ class TrapEntryEventInput(implicit val p: Parameters) extends Bundle with HasXSP
   val vsatp = Input(new SatpBundle)
   val hgatp = Input(new HgatpBundle)
   val mbmc = Input(new MbmcBundle)
+  val mmpt = Option.when(HasMptCheck) (Input(new MmptBundle)) //HasMptCheck
   // from mem
   val memExceptionVAddr = Input(UInt(XLEN.W))
   val memExceptionGPAddr = Input(UInt(XLEN.W))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index e4af5dbb39b..55480f22ee4 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -17,7 +17,7 @@ import xiangshan.backend.fu.NewCSR.CSRFunc._
 import xiangshan.backend.fu.util.CSRConst._
 import xiangshan.backend.decode.isa.CSRs
 import system.HasSoCParameter
-
+import utility.ZeroExt
 import scala.collection.immutable.SeqMap
 
 trait MachineLevel { self: NewCSR =>
@@ -38,6 +38,35 @@ trait MachineLevel { self: NewCSR =>
   })
     .setAddr(Mbmc))  else  None
 
+  val mmpt = if (HasMptCheck) Some(Module(new CSRModule("Mmpt", new MmptBundle) {
+    val ppnMask = ZeroExt(Fill(PPNLengthMpt, 1.U(1.W)).take(PAddrBits - PageOffsetWidth), PPNLengthMpt)
+    if (!HasMptCheckDefault) {
+      when(wen) {
+        reg.SDID := wdata.SDID
+        reg.PPN := wdata.PPN & ppnMask
+        reg.optOutInNode := wdata.optOutInNode
+        when(wdata.MODE.isLegal) {
+          reg.MODE := wdata.MODE
+        }.otherwise {
+          reg.MODE := reg.MODE
+        }
+      }.otherwise {
+        reg := reg
+      }
+    } else {
+      reg.SDID := 0.U.asTypeOf(reg.SDID)
+      reg.PPN := "h00000080000".U(PPNLengthMpt.W) & ppnMask //for testing, will be removed later
+      if (HasMptInodeOpt) {
+        reg.optOutInNode := 1.U.asTypeOf(reg.optOutInNode)
+      } else {
+        reg.optOutInNode := 0.U.asTypeOf(reg.optOutInNode)
+      }
+      reg.MODE := 2.U.asTypeOf(reg.MODE)
+    }
+
+  })
+    .setAddr(Mmpt)) else None
+
   val mstatus = Module(new MstatusModule)
     .setAddr(CSRs.mstatus)
 
@@ -513,8 +542,7 @@ trait MachineLevel { self: NewCSR =>
     mcyclecfg,
     minstretcfg,
     mcontext,
-  ) ++ mhpmevents ++ mhpmcounters ++ (if (HasBitmapCheck) Seq(mbmc.get) else Seq())
-
+  ) ++ mhpmevents ++ mhpmcounters ++ (if (HasBitmapCheck) Seq(mbmc.get) else if (HasMptCheck) Seq(mmpt.get) else Seq())
 
   val machineLevelCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap.from(
     machineLevelCSRMods.map(csr => (csr.addr -> (csr.w -> csr.rdata))).iterator
@@ -534,6 +562,15 @@ class MbmcBundle extends  CSRBundle {
   val CMODE   = RW(0).withReset(0.U).withDescription("Bitmap checking mode selector.")
 }
 
+class MmptBundle extends CSRBundle { //HasMptCheck
+  val MODE = MmptMode(63, 60, null).withReset(MmptMode.Bare) //wNoFilter
+  // WARL in privileged spec.
+  // RW, since we support max width of VMID
+  val optOutInNode = RW(59).withReset(0.U).withDescription("Skip non-leaf node check")
+  val SDID = RW(52 - 1 + SDIDLEN, 52).withReset(0.U).withDescription("ID for security domain")
+  val PPN = RW(PPNLengthMpt-1, 0).withReset(0.U).withDescription("Mpt level3 talble adress.")
+}
+
 class MstatusBundle extends CSRBundle {
 
   val SIE  = CSRRWField     (1).withReset(0.U).withDescription("Global interrupt enable for S-mode.")
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 2e499735727..6cfce0b3016 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -33,6 +33,8 @@ object CSRConfig {
 
   final val VMIDLEN = 14 // the length of VMID of XS implementation
 
+  final val SDIDLEN = 6
+
   final val VMIDMAX = 14 // the max value of VMIDLEN defined by spec
 
   final val VaddrMaxWidth = 48 + 2 // support Sv39/Sv48/Sv39x4/Sv48x4
@@ -66,6 +68,7 @@ object CSRConfig {
   final val EXT_DBLTRP = true
 
   final val PPNLength = 44
+  final val PPNLengthMpt = 44
   // TODO: as current test not support clean mdt , we set mstatus->mdt = 0 to allow exception in m-mode
   final val mdtInit = 0
 
@@ -201,10 +204,12 @@ class NewCSR(implicit val p: Parameters) extends Module
       val satpASIDChanged = Bool()
       val vsatpASIDChanged = Bool()
       val hgatpVMIDChanged = Bool()
+      val mmptSDIDChanged = Bool()
       val satp = new SatpBundle
       val vsatp = new SatpBundle
       val hgatp = new HgatpBundle
       val mbmc = new MbmcBundle
+      val mmpt = Option.when(HasMptCheck) (new MmptBundle)
       val mxr = Bool()
       val sum = Bool()
       val vmxr = Bool()
@@ -867,6 +872,7 @@ class NewCSR(implicit val p: Parameters) extends Module
         } else {
           in.mbmc := DontCare
         }
+        in.mmpt.foreach(_ := mmpt.get.regOut)
 
         in.memExceptionVAddr := io.fromMem.excpVA
         in.memExceptionGPAddr := io.fromMem.excpGPA
@@ -976,6 +982,8 @@ class NewCSR(implicit val p: Parameters) extends Module
   // flush
   if (HasBitmapCheck) {
     resetSatp := Cat(Seq(satp, vsatp, hgatp, mbmc.get).map(_.addr.U === addr)).orR && wenLegalReg // write to satp will cause the pipeline be flushed
+  } else if (HasMptCheck) {
+     resetSatp := Cat(Seq(satp, vsatp, hgatp, mmpt.get).map(_.addr.U === addr)).orR && wenLegalReg
   } else {
     resetSatp := Cat(Seq(satp, vsatp, hgatp).map(_.addr.U === addr)).orR && wenLegalReg // write to satp will cause the pipeline be flushed
   }
@@ -1490,6 +1498,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.tlb.satpASIDChanged  := GatedValidRegNext(satp.w.wen  && satp .regOut.ASID =/=  satp.w.wdataFields.ASID)
   io.tlb.vsatpASIDChanged := GatedValidRegNext(vsatp.w.wen && vsatp.regOut.ASID =/= vsatp.w.wdataFields.ASID)
   io.tlb.hgatpVMIDChanged := GatedValidRegNext(hgatp.w.wen && hgatp.regOut.VMID =/= hgatp.w.wdataFields.VMID)
+  io.tlb.mmptSDIDChanged := (if (HasMptCheck) GatedValidRegNext(mmpt.get.w.wen && mmpt.get.regOut.SDID =/= mmpt.get.w.wdataFields.SDID) else DontCare)
   io.tlb.satp := satp.rdata
   io.tlb.vsatp := vsatp.rdata
   io.tlb.hgatp := hgatp.rdata
@@ -1498,6 +1507,8 @@ class NewCSR(implicit val p: Parameters) extends Module
   } else {
     io.tlb.mbmc := DontCare
   }
+  io.tlb.mmpt.foreach(_ := mmpt.get.rdata)
+
   io.tlb.mxr  :=  mstatus.regOut.MXR.asBool
   io.tlb.sum  :=  mstatus.regOut.SUM.asBool
   io.tlb.vmxr := vsstatus.regOut.MXR.asBool
@@ -1524,6 +1535,8 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.tlb.pmm.hstatus := RegNext(hstatus.regOut.HUPMM.asUInt)
   io.tlb.pmm.senvcfg := RegNext(senvcfg.regOut.PMM.asUInt)
 
+  io.toDecode.illegalInst.mfence.foreach(_ := !isModeM)
+
   io.toDecode.illegalInst.sfenceVMA  := isModeHS && mstatus.regOut.TVM  || isModeHU
   io.toDecode.virtualInst.sfenceVMA  := isModeVS && hstatus.regOut.VTVM || isModeVU
   io.toDecode.illegalInst.sfencePart := isModeHU
diff --git a/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala b/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
index e2e36fff57c..74ce234bdc3 100644
--- a/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
+++ b/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
@@ -35,6 +35,7 @@ trait HasCSRConst {
 
   // Machine level Bitmap Check(Custom Read/Write)
   val Mbmc = 0xBC2
+  val Mmpt = 0x382
 
   def privEcall  = 0x000.U
   def privEbreak = 0x001.U
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 03ecc0b24c5..c75e042176c 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -273,7 +273,15 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   tlb.mbmc.CMODE    := csrMod.io.tlb.mbmc.CMODE.asUInt
   tlb.mbmc.BCLEAR   := csrMod.io.tlb.mbmc.BCLEAR.asUInt
   tlb.mbmc.BMA      := csrMod.io.tlb.mbmc.BMA.asUInt
-
+  if (HasMptCheck) {
+    tlb.mmpt.mode := csrMod.io.tlb.mmpt.get.MODE.asUInt
+    tlb.mmpt.sdid := csrMod.io.tlb.mmpt.get.SDID.asUInt
+    tlb.mmpt.optOutInNode := csrMod.io.tlb.mmpt.get.optOutInNode.asUInt
+    tlb.mmpt.ppn := csrMod.io.tlb.mmpt.get.PPN.asUInt
+    tlb.mmpt.changed := csrMod.io.tlb.mmptSDIDChanged
+  } else {
+    tlb.mmpt := DontCare
+  }
   // expose several csr bits for tlb
   tlb.priv.mxr := csrMod.io.tlb.mxr
   tlb.priv.sum := csrMod.io.tlb.sum
@@ -420,6 +428,8 @@ class CSRInput(implicit p: Parameters) extends XSBundle with HasSoCParameter {
 
 class CSRToDecode(implicit p: Parameters) extends XSBundle {
   val illegalInst = new Bundle {
+    
+    val mfence = Option.when(HasMptCheck) (Bool())
     /**
      * illegal sfence.vma, sinval.vma
      * raise EX_II when isModeHS && mstatus.TVM=1 || isModeHU
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index 357c27c525c..3afc8fde0a4 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -80,18 +80,24 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
 
   val sfence_tmp = DelayN(io.sfence, 1)
   val csr_tmp    = DelayN(io.csr.tlb, 1)
-  val sfence_dup = Seq.fill(if (HasBitmapCheck) 11 else 9)(RegNext(sfence_tmp))
-  val csr_dup = Seq.fill(if (HasBitmapCheck) 10 else 8)(RegNext(csr_tmp)) // TODO: add csr_modified?
+  val sfence_num: Int = if (HasBitmapCheck) 11 else if (HasMptCheck) (9+PtwWidth) else 9
+  val csr_num: Int = if (HasBitmapCheck) 10 else if (HasMptCheck) (8+PtwWidth) else 8
+  val sfence_dup = Seq.fill(sfence_num) (RegNext(sfence_tmp))
+  val csr_dup = Seq.fill(csr_num) (RegNext(csr_tmp)) // TODO: add csr_modified?
   val satp   = csr_dup(0).satp
   val vsatp  = csr_dup(0).vsatp
   val hgatp  = csr_dup(0).hgatp
   val priv   = csr_dup(0).priv
   val mPBMTE = csr_dup(0).mPBMTE
   val hPBMTE = csr_dup(0).hPBMTE
-  val flush  = sfence_dup(0).valid || satp.changed || vsatp.changed || hgatp.changed || priv.virt_changed
+  val mmpt = csr_dup(0).mmpt
+
+  val flush  = sfence_dup(0).valid || satp.changed || vsatp.changed || hgatp.changed || priv.virt_changed || (if (HasMptCheck) (csr_dup(0).mmpt.changed) else false.B)
+
+  val mptEn = Option.when(HasMptCheck) (mmpt.mode =/= 0.U)
 
   val pmp = Module(new PMP())
-  val pmp_check = VecInit(Seq.fill(if (HasBitmapCheck) 5 else 4)(Module(new PMPChecker(lgMaxSize = 3, sameCycle = true)).io))
+  val pmp_check = VecInit(Seq.fill(if (HasBitmapCheck || HasMptCheck) 5 else 4) (Module(new PMPChecker(lgMaxSize = 3, sameCycle = true)).io))
   pmp.io.distribute_csr := io.csr.distribute_csr
   if (HasBitmapCheck) {
     if (KeyIDBits > 0) {
@@ -122,6 +128,13 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     }
   }
 
+  val mptc = Option.when(HasMptCheck) (Module(new MptChecker))
+  if (HasMptCheck) {
+    mptc.get.io.csr := csr_dup(3)
+    pmp_check(4).req <> mptc.get.io.pmp.req
+    mptc.get.io.pmp.resp <> pmp_check(4).resp
+    mptc.get.io.sfence <> sfence_dup(3)
+  }
   val missQueue = Module(new L2TlbMissQueue)
   val cache = Module(new PtwCache)
   val ptw = Module(new PTW)
@@ -129,7 +142,24 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   val llptw = Module(new LLPTW)
   val blockmq = Module(new BlockHelper(3))
   val arb1 = Module(new Arbiter(new PtwReq, PtwWidth))
-  val arb2 = Module(new Arbiter(new L2TlbWithHptwIdBundle, ((if (l2tlbParams.enablePrefetch) 4 else 3) + (if(HasHExtension) 1 else 0))))
+  val arb2 = Module(new Arbiter(new L2TlbWithHptwIdBundle, ((if (l2tlbParams.enablePrefetch) 4 else 3) + (if (HasHExtension) 1 else 0))))
+
+  val mpt_arb = Option.when (HasMptCheck) (Module(new Arbiter(new MptReqBundle, 4+PtwWidth)))
+  val mptOnlyPort = 5
+  // req directly from l1tlb. in this situation, adress translation is disabled and the reqVPN is the actuall PA waiting to be mpt checked.
+  val lastMptPort2 = 4
+  val lastMptPort = 3 // 3 -> 3+ptw width-1, last mpt check before send to l1ltb
+  val llptwMptPort = 2
+  val hptwMptPort = 1
+  val ptwMptPort = 0
+  if (HasMptCheck) {
+    mptc.get.io.req.valid := mpt_arb.get.io.out.valid
+    mpt_arb.get.io.out.ready := mptc.get.io.req.ready
+    mptc.get.io.req.bits.reqPA := mpt_arb.get.io.out.bits.reqPA
+    mptc.get.io.req.bits.id := mpt_arb.get.io.out.bits.id
+    mptc.get.io.req.bits.mptOnly :=  mpt_arb.get.io.out.bits.mptOnly && mpt_arb.get.io.out.valid
+  }
+
   val hptw_req_arb = Module(new Arbiter(new Bundle {
     val id = UInt(log2Up(l2tlbParams.llptwsize).W)
     val source = UInt(bSourceWidth.W)
@@ -143,6 +173,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     val s2xlate = UInt(2.W)
     val s1 = new PtwSectorResp ()
     val s2 = new HptwResp()
+    val mpt = Option.when (HasMptCheck) (new MptTlbRespBundle())
   }, 1)).io)
   val mergeArb = (0 until PtwWidth).map(i => Module(new Arbiter(new Bundle {
     val s2xlate = UInt(2.W)
@@ -202,6 +233,14 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   }
   XSError(!(tlbCounter >= 0.U && tlbCounter <= MissQueueSize.U), s"l2tlb full!")
 
+  if (HasMptCheck) { // assign arb1 out to mptc for mptOnly mode check
+    mpt_arb.get.io.in(mptOnlyPort).bits.id := arb1.io.chosen // b101 + bx //tlb id to mptc id
+    mpt_arb.get.io.in(mptOnlyPort).bits.mptOnly := true.B // used for l2tlb return control logic
+    mpt_arb.get.io.in(mptOnlyPort).bits.reqPA := arb1.io.out.bits.vpn // for mpt only vpn is ppn
+    mpt_arb.get.io.in(mptOnlyPort).valid := arb1.io.out.fire && (arb1.io.out.bits.mptOnly.get) // valid when mptOnly and
+    // s2xlate is dont care, it has nothing to do with mpt
+  }
+
   arb2.io.in(InArbPTWPort).valid := ptw.io.llptw.valid
   arb2.io.in(InArbPTWPort).bits.req_info := ptw.io.llptw.bits.req_info
   arb2.io.in(InArbPTWPort).bits.isHptwReq := false.B
@@ -210,7 +249,8 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   ptw.io.llptw.ready := arb2.io.in(InArbPTWPort).ready
   block_decoupled(missQueue.io.out, arb2.io.in(InArbMissQueuePort), Mux(missQueue.io.out.bits.isLLptw, !llptw.io.in.ready, !ptw.io.req.ready))
 
-  arb2.io.in(InArbTlbPort).valid := arb1.io.out.fire
+  arb2.io.in(InArbTlbPort).valid := arb1.io.out.fire && (if (HasMptCheck) !(arb1.io.out.bits.mptOnly.get) else true.B)
+  // not mpt only req will go to l2tlb,mptOnly goes to mptc directly
   arb2.io.in(InArbTlbPort).bits.req_info.vpn := arb1.io.out.bits.vpn
   arb2.io.in(InArbTlbPort).bits.req_info.s2xlate := arb1.io.out.bits.s2xlate
   arb2.io.in(InArbTlbPort).bits.req_info.source := arb1.io.chosen
@@ -220,8 +260,9 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   // 1. arb1 and arb2 are both comb logic, so ready can work just the same cycle
   // 2. arb1 can send one req at most in a cycle, so do not need to write
   //    "tlbCounter <= (MissQueueSize - 2).U"
-  arb1.io.out.ready := arb2.io.in(InArbTlbPort).ready && tlbCounter < MissQueueSize.U
-
+  arb1.io.out.ready := arb2.io.in(InArbTlbPort).ready && tlbCounter < MissQueueSize.U &&
+    (if (HasMptCheck) (mpt_arb.get.io.in(mptOnlyPort).ready) else true.B)
+  //arb1 out is ready when arb2 and mptc are both ready
   arb2.io.in(InArbHPTWPort).valid := hptw_req_arb.io.out.valid
   arb2.io.in(InArbHPTWPort).bits.req_info.vpn := hptw_req_arb.io.out.bits.gvpn
   arb2.io.in(InArbHPTWPort).bits.req_info.s2xlate := onlyStage2
@@ -249,6 +290,12 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     L2TlbPrefetchDB.vpn := prefetch.io.out.bits.req_info.vpn
     L2TlbPrefetchTable.log(L2TlbPrefetchDB, isWriteL2TlbPrefetchTable.orR && prefetch.io.out.fire, "L2TlbPrefetch", clock, reset)
   }
+
+  if (HasMptCheck) {
+    for (i <- 0 until ((if (l2tlbParams.enablePrefetch) 4 else 3) + (if (HasHExtension) 1 else 0))) {
+      arb2.io.in(i).bits.req_info.mptOnly.get := DontCare // assign dont care to mpt port, it is not used. but arb2 uses L1TLB req as type, so...
+    }
+  }
   arb2.io.out.ready := cache.io.req.ready
 
   // Instructs jmp_bitmap_check requests from cache need go to LLPTW/PTW for processing
@@ -276,6 +323,10 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   missQueue.io.in <> mq_arb.io.out
   missQueue.io.sfence  := sfence_dup(6)
   missQueue.io.csr := csr_dup(5)
+  if (HasMptCheck) {
+    mq_arb.io.in(0).bits.req_info.mptOnly.get := DontCare
+    mq_arb.io.in(1).bits.req_info.mptOnly.get := DontCare
+  }
 
   blockmq.io.start := missQueue.io.out.fire
   blockmq.io.enable := ptw.io.req.fire
@@ -286,6 +337,9 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     !cache.io.resp.bits.bypassed &&
     !cache.io.resp.bits.isHptwReq
   llptw.io.in.bits.req_info := cache.io.resp.bits.req_info
+  if (HasMptCheck) {
+    llptw.io.in.bits.req_info.mptOnly.get := DontCare
+  }
   llptw.io.in.bits.ppn := cache.io.resp.bits.toFsm.ppn
   if (HasBitmapCheck) {
     llptw.io.in.bits.bitmapCheck.get.jmp_bitmap_check := cache.io.resp.bits.toFsm.bitmapCheck.get.jmp_bitmap_check
@@ -296,12 +350,26 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   llptw.io.sfence := sfence_dup(1)
   llptw.io.csr := csr_dup(1)
   val llptw_stage1 = Reg(Vec(l2tlbParams.llptwsize, new PtwMergeResp()))
-  when(llptw.io.in.fire){
+  when(llptw.io.in.fire) {
     llptw_stage1(llptw.io.mem.enq_ptr) := cache.io.resp.bits.stage1
   }
 
+  if (HasMptCheck) {
+    mpt_arb.get.io.in(llptwMptPort).bits.mptOnly := false.B // llptw is never mpt only mode
+    mpt_arb.get.io.in(llptwMptPort).bits.id := llptw.io.mptCheck.get.req.bits.id
+    mpt_arb.get.io.in(llptwMptPort).bits.reqPA := llptw.io.mptCheck.get.req.bits.reqPA
+    mpt_arb.get.io.in(llptwMptPort).valid := llptw.io.mptCheck.get.req.valid
+    llptw.io.mptCheck.get.req.ready := mpt_arb.get.io.in(llptwMptPort).ready
+
+    llptw.io.mptCheck.get.resp.bits <> mptc.get.io.resp.bits
+    llptw.io.mptCheck.get.resp.valid := mptc.get.io.resp.valid && from_llptw(mptc.get.io.resp.bits.id) && (!mptc.get.io.resp.bits.mptOnly)
+  }
+
   cache.io.req.valid := arb2.io.out.fire
   cache.io.req.bits.req_info := arb2.io.out.bits.req_info
+  if (HasMptCheck) {
+    cache.io.req.bits.req_info.mptOnly.get := DontCare // mptonly is neverused in cache,actually, its never used anywhere in l2tlb except the output sel
+  }
   cache.io.req.bits.isFirst := (arb2.io.chosen =/= InArbMissQueuePort.U && !arb2.io.out.bits.isHptwReq)
   cache.io.req.bits.isHptwReq := arb2.io.out.bits.isHptwReq
   cache.io.req.bits.hptwId := arb2.io.out.bits.hptwId
@@ -324,6 +392,10 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     !cache.io.resp.bits.isFirst &&
     !cache.io.resp.bits.isHptwReq
   ptw.io.req.bits.req_info := cache.io.resp.bits.req_info
+
+  if (HasMptCheck) {
+    ptw.io.req.bits.req_info.mptOnly.get := DontCare
+  }
   if (EnableSv48) {
     ptw.io.req.bits.l3Hit.get := cache.io.resp.bits.toFsm.l3Hit.get
   }
@@ -341,6 +413,17 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   ptw.io.csr := csr_dup(6)
   ptw.io.resp.ready := outReady(ptw.io.resp.bits.source, outArbFsmPort)
 
+  if (HasMptCheck) {
+    mpt_arb.get.io.in(ptwMptPort).bits.mptOnly := false.B // llptw is never mpt only mode
+    mpt_arb.get.io.in(ptwMptPort).bits.id := ptw.io.mptCheck.get.req.bits.id
+    mpt_arb.get.io.in(ptwMptPort).bits.reqPA := ptw.io.mptCheck.get.req.bits.reqPA
+    mpt_arb.get.io.in(ptwMptPort).valid := ptw.io.mptCheck.get.req.valid
+    ptw.io.mptCheck.get.req.ready := mpt_arb.get.io.in(ptwMptPort).ready
+
+    ptw.io.mptCheck.get.resp.bits <> mptc.get.io.resp.bits
+    ptw.io.mptCheck.get.resp.valid := mptc.get.io.resp.valid && from_ptw(mptc.get.io.resp.bits.id)
+  }
+
   hptw.io.req.valid := cache.io.resp.valid && !cache.io.resp.bits.hit && cache.io.resp.bits.isHptwReq
   hptw.io.req.bits.gvpn := cache.io.resp.bits.req_info.vpn
   hptw.io.req.bits.id := cache.io.resp.bits.toHptw.id
@@ -357,6 +440,17 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   }
   hptw.io.sfence := sfence_dup(8)
   hptw.io.csr := csr_dup(7)
+
+  if (HasMptCheck) {
+    mpt_arb.get.io.in(hptwMptPort).bits.mptOnly := false.B
+    mpt_arb.get.io.in(hptwMptPort).bits.id := hptw.io.mptCheck.get.req.bits.id
+    mpt_arb.get.io.in(hptwMptPort).bits.reqPA := hptw.io.mptCheck.get.req.bits.reqPA
+    mpt_arb.get.io.in(hptwMptPort).valid := hptw.io.mptCheck.get.req.valid
+    hptw.io.mptCheck.get.req.ready := mpt_arb.get.io.in(hptwMptPort).ready
+
+    hptw.io.mptCheck.get.resp.bits <> mptc.get.io.resp.bits
+    hptw.io.mptCheck.get.resp.valid := mptc.get.io.resp.valid && from_hptw(mptc.get.io.resp.bits.id)
+  }
   // mem req
   def blockBytes_align(addr: UInt) = {
     Cat(addr(PAddrBits - 1, log2Up(l2tlbParams.blockBytes)), 0.U(log2Up(l2tlbParams.blockBytes).W))
@@ -379,6 +473,10 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   def from_bitmap(id: UInt) = {
     (id > l2tlbParams.llptwsize.U + 1.U) && (id < MemReqWidth.U)
   }
+
+  def from_mptc (id: UInt) = { // HasMptCheck
+    id > l2tlbParams.llptwsize.U + 1.U
+  }
   val waiting_resp = RegInit(VecInit(Seq.fill(MemReqWidth)(false.B)))
   val flush_latch = RegInit(VecInit(Seq.fill(MemReqWidth)(false.B)))
   val hptw_bypassed = RegInit(false.B)
@@ -400,13 +498,17 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   if (HasBitmapCheck) {
     bitmap.get.io.mem.req_mask := waiting_resp.slice(MemReqWidth - (l2tlbParams.llptwsize + 2), MemReqWidth)
   }
-  val mem_arb = Module(new Arbiter(new L2TlbMemReqBundle(), if (HasBitmapCheck) 4 else 3))
+
+  if (HasMptCheck) { mptc.get.io.mem.mask := waiting_resp.apply(l2tlbParams.llptwsize + 2) }
+  val mem_arb = Module(new Arbiter(new L2TlbMemReqBundle(), if (HasBitmapCheck || HasMptCheck) 4 else 3))
   mem_arb.io.in(0) <> ptw.io.mem.req
   mem_arb.io.in(1) <> llptw_mem.req
   mem_arb.io.in(2) <> hptw.io.mem.req
   if (HasBitmapCheck) {
     mem_arb.io.in(3) <> bitmap.get.io.mem.req
   }
+
+  if (HasMptCheck) {mem_arb.io.in(3) <> mptc.get.io.mem.req}
   mem_arb.io.out.ready := mem.a.ready && !flush && !wfiReq
 
   // // assert, should not send mem access at same addr for twice.
@@ -435,7 +537,8 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   when (mem_arb.io.out.fire) {
     req_addr_low(mem_arb.io.out.bits.id) := addr_low_from_paddr(mem_arb.io.out.bits.addr)
     waiting_resp(mem_arb.io.out.bits.id) := true.B
-    hptw_bypassed := from_hptw(mem_arb.io.out.bits.id) && mem_arb.io.out.bits.hptw_bypassed
+    hptw_bypassed := from_hptw(mem_arb.io.out.bits.id) && mem_arb.io.out.bits.hptw_bypassed ||
+      (if (HasMptCheck) from_mptc(mem_arb.io.out.bits.id) else false.B)
   }
   // mem read
   val memRead =  edge.Get(
@@ -456,6 +559,9 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   val mem_resp_from_ptw = from_ptw(mem.d.bits.source)
   val mem_resp_from_hptw = from_hptw(mem.d.bits.source)
   val mem_resp_from_bitmap = from_bitmap(mem.d.bits.source)
+
+  val mem_resp_from_mptc = from_mptc(mem.d.bits.source)
+
   when (mem.d.valid) {
     assert(mem.d.bits.source < MemReqWidth.U)
     refill_data(refill_helper._4) := mem.d.bits.data
@@ -467,15 +573,16 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   // save only one pte for each id
   // (miss queue may can't resp to tlb with low latency, it should have highest priority, but diffcult to design cache)
   val resp_pte = VecInit((0 until (if (HasBitmapCheck) MemReqWidth / 2 else MemReqWidth)).map(i =>
-    if (i == l2tlbParams.llptwsize + 1) {RegEnable(get_part(refill_data_tmp, req_addr_low(i)), 0.U.asTypeOf(get_part(refill_data_tmp, req_addr_low(i))), mem_resp_done && mem_resp_from_hptw) }
-    else if (i == l2tlbParams.llptwsize) {RegEnable(get_part(refill_data_tmp, req_addr_low(i)), 0.U.asTypeOf(get_part(refill_data_tmp, req_addr_low(i))), mem_resp_done && mem_resp_from_ptw) }
+    if (i == l2tlbParams.llptwsize + 2) { RegEnable(get_part(refill_data_tmp, req_addr_low(i)), 0.U.asTypeOf(get_part(refill_data_tmp, req_addr_low(i))), mem_resp_done && mem_resp_from_mptc) } // HasMptCheck
+    else if (i == l2tlbParams.llptwsize + 1) { RegEnable(get_part(refill_data_tmp, req_addr_low(i)), 0.U.asTypeOf(get_part(refill_data_tmp, req_addr_low(i))), mem_resp_done && mem_resp_from_hptw) }
+    else if (i == l2tlbParams.llptwsize) { RegEnable(get_part(refill_data_tmp, req_addr_low(i)), 0.U.asTypeOf(get_part(refill_data_tmp, req_addr_low(i))), mem_resp_done && mem_resp_from_ptw) }
     else { Mux(llptw_mem.buffer_it(i), get_part(refill_data, req_addr_low(i)), RegEnable(get_part(refill_data, req_addr_low(i)), 0.U.asTypeOf(get_part(refill_data, req_addr_low(i))), llptw_mem.buffer_it(i))) }
     // llptw could not use refill_data_tmp, because enq bypass's result works at next cycle
   ))
 
   // save eight ptes for each id when sector tlb
   // (miss queue may can't resp to tlb with low latency, it should have highest priority, but diffcult to design cache)
-  val resp_pte_sector = VecInit((0 until (if (HasBitmapCheck) MemReqWidth / 2 else MemReqWidth)).map(i =>
+  val resp_pte_sector = VecInit((0 until (if (HasBitmapCheck) MemReqWidth / 2 else if (HasMptCheck) MemReqWidth - 1 else MemReqWidth)).map(i =>
     if (i == l2tlbParams.llptwsize + 1) {RegEnable(refill_data_tmp, 0.U.asTypeOf(refill_data_tmp), mem_resp_done && mem_resp_from_hptw) }
     else if (i == l2tlbParams.llptwsize) {RegEnable(refill_data_tmp, 0.U.asTypeOf(refill_data_tmp), mem_resp_done && mem_resp_from_ptw) }
     else { Mux(llptw_mem.buffer_it(i), refill_data, RegEnable(refill_data, 0.U.asTypeOf(refill_data), llptw_mem.buffer_it(i))) }
@@ -531,12 +638,20 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   // mem -> hptw
   hptw.io.mem.resp.valid := mem_resp_done && mem_resp_from_hptw
   hptw.io.mem.resp.bits := resp_pte.apply(l2tlbParams.llptwsize + 1)
+
+  if (HasMptCheck) {
+    mptc.get.io.mem.resp.valid := RegNext(mem_resp_done && mem_resp_from_mptc) // fix later extra delay reg which is unnecessary
+    mptc.get.io.mem.resp.bits := resp_pte.apply(l2tlbParams.llptwsize + 2)
+  }
   // mem -> cache
   val refill_from_llptw = mem_resp_from_llptw
   val refill_from_ptw = mem_resp_from_ptw
   val refill_from_hptw = mem_resp_from_hptw
   val refill_level = Mux(refill_from_llptw, 0.U, Mux(refill_from_ptw, RegEnable(ptw.io.refill.level, 0.U, ptw.io.mem.req.fire), RegEnable(hptw.io.refill.level, 0.U, hptw.io.mem.req.fire)))
-  val refill_valid = mem_resp_done && (if (HasBitmapCheck) !mem_resp_from_bitmap else true.B) && !flush && !flush_latch(mem.d.bits.source) && !(from_hptw(mem.d.bits.source) && hptw_bypassed)
+  val refill_valid = mem_resp_done && (if (HasBitmapCheck) !mem_resp_from_bitmap else true.B) &&
+    (if (HasMptCheck) !mem_resp_from_mptc else true.B) &&
+    !flush && !flush_latch(mem.d.bits.source) &&
+    !(from_hptw(mem.d.bits.source) && hptw_bypassed)
 
   cache.io.refill.valid := GatedValidRegNext(refill_valid, false.B)
   cache.io.refill.bits.ptes := refill_data.asUInt
@@ -625,6 +740,14 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
 
   val cfsValue = Option.when(HasBitmapCheck)(llptw_out.bits.bitmapCheck.get.cfs)
 
+  val MptSwitchBox = Option.when(HasMptCheck) (Seq.fill(PtwWidth)(Module(new MptOutputSwitchBox))) // Put all conversion logic into one module.
+  if (HasMptCheck) {
+    for (i <- 0 until PtwWidth) {
+      MptSwitchBox.get(i).io.csr <> csr_dup(8+i)
+      MptSwitchBox.get(i).io.sfence := sfence_dup(9+i)
+    }
+  }
+
   // Timing: Maybe need to do some optimization or even add one more cycle
   for (i <- 0 until PtwWidth) {
     mergeArb(i).in(outArbCachePort).valid := cache.io.resp.valid && cache.io.resp.bits.hit && cache.io.resp.bits.req_info.source===i.U && !cache.io.resp.bits.isHptwReq
@@ -649,6 +772,32 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     )
     mergeArb(i).in(outArbMqPort).bits.s2 := llptw_out.bits.h_resp
     mergeArb(i).out.ready := outArb(i).in(0).ready
+    // mptfin and outarb.ready,outArb(i).in(0).fire，ready only high for 1 clk
+  }
+
+  // connect to the switch box
+   if (HasMptCheck) {
+    for (i <- 0 until PtwWidth) {
+      // connect to the switch box
+      MptSwitchBox.get(i).io.mergeArb.valid := mergeArb(i).out.valid
+      mergeArb(i).out.ready := MptSwitchBox.get(i).io.mergeArb.ready
+      MptSwitchBox.get(i).io.mergeArb.bits.fault := merge_ptwResp_to_sector_ptwResp(mergeArb(i).out.bits.s1).af || merge_ptwResp_to_sector_ptwResp(mergeArb(i).out.bits.s1).pf ||
+        mergeArb(i).out.bits.s2.gpf || mergeArb(i).out.bits.s2.gaf
+
+      mpt_arb.get.io.in(lastMptPort+i).valid := MptSwitchBox.get(i).io.mptIn.valid
+      mpt_arb.get.io.in(lastMptPort+i).bits.reqPA := Mux(mergeArb(i).out.bits.s2xlate === onlyStage1 || mergeArb(i).out.bits.s2xlate === noS2xlate,
+          mergeArb(i).out.bits.s1.genPPN(),//gpa
+          mergeArb(i).out.bits.s2.genPPNS2(mergeArb(i).out.bits.s2.entry.tag)
+      ) // hpa tag or gpa? for better timing, use tag
+      mpt_arb.get.io.in(lastMptPort+i).bits.mptOnly := false.B
+      mpt_arb.get.io.in(lastMptPort+i).bits.id := (8+i).U
+      MptSwitchBox.get(i).io.mptIn.ready := mpt_arb.get.io.in(lastMptPort+i).ready
+
+      MptSwitchBox.get(i).io.mptOut.bits := mptc.get.io.resp.bits
+      MptSwitchBox.get(i).io.mptOut.valid := (mptc.get.io.resp.valid &&
+        (mptc.get.io.resp.bits.id === (8+i).U || (mptc.get.io.resp.bits.mptOnly && (mptc.get.io.resp.bits.id === i.U))))
+      // switch box also receives mpt only request
+    }
   }
 
   for (i <- 0 until PtwWidth) {
@@ -677,6 +826,25 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       outArb(i).in(0).bits.s1 := merge_ptwResp_to_sector_ptwResp(mergeArb(i).out.bits.s1)
       outArb(i).in(0).bits.s2 := mergeArb(i).out.bits.s2
     }
+
+    if (HasMptCheck) {
+      MptSwitchBox.get(i).io.l1TLB.ready := outArb(i).in(0).ready
+      outArb(i).in(0).valid := MptSwitchBox.get(i).io.l1TLB.valid
+      outArb(i).in(0).bits.mpt.get := MptSwitchBox.get(i).io.l1TLB.bits.outData
+      when(MptSwitchBox.get(i).io.l1TLB.valid && MptSwitchBox.get(i).io.l1TLB.bits.outMptOnly) {
+        // maybe redundent,need to change L1TLB structure later
+        outArb(i).in(0).bits.s2xlate := noS2xlate // actually it is don't care,but i want to assign it to 0
+        // fake output s1. s2 will not be used in mponly mode , its value  is dont care. in future release. niether will s1 be used(hopefully)
+
+        val fakeMptS1 = Wire(new PtwSectorResp())
+        fakeMptS1.FakeMptOnlyResp(MptSwitchBox.get(i).io.l1TLB.bits.reqPA, satp.asid, hgatp.vmid)
+        outArb(i).in(0).bits.s1 := fakeMptS1
+      }
+      when(!mptEn.get) {
+        outArb(i).in(0).bits.mpt.get.genFakeResp() // if mpt is disable as baremode, generate a fake one for the L1TLB
+      }
+    }
+
   }
 
   // io.tlb.map(_.resp) <> outArb.map(_.out)
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala b/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
index 08608866488..38a5199fc0e 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
@@ -56,6 +56,9 @@ class L2TlbPrefetch(implicit p: Parameters) extends XSModule with HasPtwConst {
   io.out.bits.req_info.vpn := next_req
   io.out.bits.req_info.s2xlate := s2xlate
   io.out.bits.req_info.source := prefetchID.U
+  if (HasMptCheck) {
+    io.out.bits.req_info.mptOnly.get := false.B
+  }
   io.out.bits.isHptwReq := false.B
   io.out.bits.isLLptw := false.B
   io.out.bits.hptwId := DontCare
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 503984760e5..6f91aab08ed 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -199,6 +199,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
   val pteidx = Vec(tlbcontiguous, Bool())
   val ppn_low = Vec(tlbcontiguous, UInt(sectortlbwidth.W))
 
+  val mptperm = Option.when(HasMptCheck) (new MptPermBundle(isTlbPort = false))
   val g_perm = new TlbPermBundle
   val vmid = UInt(vmidLen.W)
   val s2xlate = UInt(2.W)
@@ -328,23 +329,39 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
       Mux(s1_exception || (s2_exception && !s1_leaf), item.s1.entry.level.getOrElse(0.U),
         Mux(s2_exception && !s1_valid, 3.U, merge_level))
     val inner_level = Mux(item.s2xlate =/= allStage, merge_level, allStage_level)
-    this.level.map(_ := inner_level)
+
+    val mpt_level =Option.when(HasMptCheck) (inner_level min item.mpt.get.mptLevel)
+
+    this.level.map(_ := (if (HasMptCheck) (mpt_level.get) else (inner_level)))
+
     this.perm.apply(item.s1)
     this.pbmt := item.s1.entry.pbmt
+    
+    if (HasMptCheck) {this.mptperm.get.resp_apply(item.mpt.get)}
 
     val s1tag = item.s1.entry.tag
     val s2tag = item.s2.entry.tag(gvpnLen - 1, sectortlbwidth)
     this.tag := Mux(item.s2xlate === onlyStage2, s2tag, s1tag)
     // if stage2 page is larger than stage1 page, need to merge s2tag and s2ppn to get a new s2ppn.
-    val s1ppn = item.s1.entry.ppn(sectorppnLen - 1, 0)
-    val s1ppn_low = item.s1.ppn_low
+    val s1ppn = if (!HasMptCheck) (item.s1.entry.ppn(sectorppnLen - 1, 0))
+      else(MuxLookup(item.s1.entry.level.getOrElse(0.U),item.s1.entry.ppn(sectorppnLen - 1, 0)) (Seq(
+        3.U -> Cat(item.s1.entry.ppn(sectorppnLen - 1, vpnnLen * 3 - sectortlbwidth), item.s1.entry.tag(vpnnLen * 3 - 1- sectortlbwidth, 0)),
+        2.U -> Cat(item.s1.entry.ppn(sectorppnLen - 1, vpnnLen * 2 - sectortlbwidth), item.s1.entry.tag(vpnnLen * 2 - 1 -sectortlbwidth, 0)),
+        1.U -> Cat(item.s1.entry.ppn(sectorppnLen - 1, vpnnLen - sectortlbwidth), item.s1.entry.tag(vpnnLen - 1 - sectortlbwidth, 0)),
+        0.U -> Mux(item.s1.entry.n.getOrElse(0.U) === 0.U, item.s1.entry.ppn(sectorppnLen - 1, 0), 
+          Cat(item.s1.entry.ppn(sectorppnLen - 1, pteNapotBits- sectortlbwidth), item.s1.entry.tag(pteNapotBits - 1 - sectortlbwidth, 0)))
+        ))
+      )
+    val s1ppn_low = if (!HasMptCheck) (item.s1.ppn_low) else (Mux((item.s1.entry.level.getOrElse(0.U) > 0.U) 
+      || item.s1.entry.n.getOrElse(0.U) > 0.U, VecInit(Seq.fill(tlbcontiguous)(item.s1.addr_low)), item.s1.ppn_low))
+
     val s2ppn = MuxLookup(item.s2.entry.level.getOrElse(0.U), item.s2.entry.ppn(ppnLen - 1, sectortlbwidth))(Seq(
       3.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen * 3), item.s2.entry.tag(vpnnLen * 3 - 1, sectortlbwidth)),
       2.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen * 2), item.s2.entry.tag(vpnnLen * 2 - 1, sectortlbwidth)),
       1.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen), item.s2.entry.tag(vpnnLen - 1, sectortlbwidth)),
       0.U -> Mux(item.s2.entry.n.getOrElse(0.U) === 0.U, item.s2.entry.ppn(ppnLen - 1, sectortlbwidth), Cat(item.s2.entry.ppn(ppnLen - 1, pteNapotBits), item.s2.entry.tag(pteNapotBits - 1, sectortlbwidth)))
     ))
-    val s2ppn_tmp = MuxLookup(item.s2.entry.level.getOrElse(0.U), item.s2.entry.ppn(ppnLen - 1, 0))(Seq(
+    val s2ppn_tmp = MuxLookup(item.s2.entry.level.getOrElse(0.U), item.s2.entry.ppn(ppnLen - 1, 0)) (Seq(
       3.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen * 3), item.s2.entry.tag(vpnnLen * 3 - 1, 0)),
       2.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen * 2), item.s2.entry.tag(vpnnLen * 2 - 1, 0)),
       1.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen), item.s2.entry.tag(vpnnLen - 1, 0)),
@@ -367,10 +384,15 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
       allStage -> allStage_n,
       noS2xlate -> item.s1.entry.n.getOrElse(0.U)
     ))
-    this.n := inner_n
-    val isSuperPage = inner_level =/= 0.U || inner_n =/= 0.U
+    
+    val inner_n_mpt = Option.when(HasMptCheck)(mpt_merge_n(inner_level,inner_n,item.mpt.get.mptLevel,item.mpt.get.permIsNAPOT)) 
+ 
+    if (HasMptCheck) {this.n :=  inner_n_mpt.get} else {this.n :=  inner_n}
+
+    val isSuperPage = if (HasMptCheck) (inner_n_mpt.get =/= 0.U || mpt_level.get =/= 0.U) else (inner_level =/= 0.U || inner_n =/= 0.U)
     this.valididx := Mux(isSuperPage, VecInit(Seq.fill(tlbcontiguous)(true.B)),
-      Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), item.s1.valididx))
+      Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), Mux((if (HasMptCheck) item.mpt.get.contigousPerm else true.B),
+      Mux(if (HasMptCheck) (inner_level === 0.U) else true.B, item.s1.valididx,VecInit(Seq.fill(tlbcontiguous)(true.B))),item.s1.pteidx)))
     this.pteidx := Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), item.s1.pteidx)
     this.vmid := Mux(item.s2xlate === onlyStage2, item.s2.entry.vmid.getOrElse(0.U), item.s1.entry.vmid.getOrElse(0.U))
     this.g_pbmt := item.s2.entry.pbmt
@@ -378,7 +400,6 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     this.s2xlate := item.s2xlate
     this
   }
-
   // 4KB is normal entry, 2MB/1GB is considered as super entry
   def is_normalentry(): Bool = {
     if (!pageSuper) { true.B }
@@ -458,6 +479,7 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
       val perm = Vec(nDups, Output(new TlbSectorPermBundle()))
       val g_perm = Vec(nDups, Output(new TlbPermBundle()))
       val s2xlate = Vec(nDups, Output(UInt(2.W)))
+      val mptperm = Option.when(HasMptCheck)(Vec(nDups, Output(new MptPermBundle(isTlbPort = true)))) // add mptperm
     }))
   }
   val w = Flipped(ValidIO(new Bundle {
@@ -485,6 +507,21 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
 
 }
 
+class MptPermBundle(isTlbPort: Boolean = true) (implicit p: Parameters) extends TlbBundle {
+  val af = Option.when(isTlbPort) (Bool()) // NOTE: if this is true, just raise af
+  val x = Bool()
+  val w = Bool()
+  val r = Bool()
+
+  def resp_apply(mptResp:MptTlbRespBundle) = {
+    this.x := mptResp.mptPerm(2) === 1.U
+    this.w := mptResp.mptPerm(1) === 1.U
+    this.r := mptResp.mptPerm(0) === 1.U
+    if (isTlbPort) (
+      this.af.get := mptResp.accessFault
+    )
+  }
+}
 class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit p: Parameters) extends MMUIOBaseBundle {
   val r = new Bundle {
     val req = Vec(ports, Flipped(DecoupledIO(new Bundle {
@@ -498,6 +535,8 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
       val g_pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
       val perm = Vec(nDups, Output(new TlbPermBundle()))
       val g_perm = Vec(nDups, Output(new TlbPermBundle()))
+      val mptperm = Option.when(HasMptCheck)(Vec(nDups, Output(new MptPermBundle(isTlbPort = true))))
+      // HasMptCheck,add mptperm
       val s2xlate = Vec(nDups, Output(UInt(2.W)))
     }))
   }
@@ -512,8 +551,9 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
     this.r.req(i).bits.s2xlate := s2xlate
   }
 
-  def r_resp_apply(i: Int) = {
-    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm, this.r.resp(i).bits.s2xlate, this.r.resp(i).bits.pbmt, this.r.resp(i).bits.g_pbmt)
+  def r_resp_apply(i: Int) = { // HasMptCheck,add mptperm
+    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm, 
+      this.r.resp(i).bits.s2xlate, this.r.resp(i).bits.pbmt, this.r.resp(i).bits.g_pbmt,this.r.resp(i).bits.mptperm)
   }
 
   def w_apply(valid: Bool, data: PtwRespS2): Unit = {
@@ -1122,6 +1162,7 @@ class PTWEntriesWithEcc(eccCode: Code, num: Int, tagLen: Int, level: Int, hasPer
 
 class PtwReq(implicit p: Parameters) extends PtwBundle {
   val vpn = UInt(vpnLen.W) //vpn or gvpn
+  val mptOnly = Option.when(HasMptCheck){Bool()}
   val s2xlate = UInt(2.W)
   def hasS2xlate(): Bool = {
     this.s2xlate =/= noS2xlate
@@ -1192,10 +1233,17 @@ class HptwResp(implicit p: Parameters) extends PtwBundle {
     ))
   }
 
-  def hit(gvpn: UInt, vmid: UInt): Bool = {
+
+
+  def hit(gvpn: UInt, vmid: UInt, mergeMptLevel: Boolean = false, mptTlbResp:MptTlbRespBundle = 0.U.asTypeOf(new MptTlbRespBundle)): Bool = {
+
     val vmid_hit = this.entry.vmid.getOrElse(0.U) === vmid
+
+    val mptNapot = Option.when(mergeMptLevel) (mpt_merge_n(this.entry.level.get, this.entry.n.getOrElse(0.U), mptTlbResp.mptLevel, mptTlbResp.permIsNAPOT))
+    val mpt_level = Option.when(mergeMptLevel) (mptTlbResp.mptLevel min this.entry.level.get)
+
     val tag_match = Wire(Vec(Level + 1, Bool()))
-    tag_match(0) := Mux(entry.n.getOrElse(0.U) === 0.U,
+    tag_match(0) := Mux((if (mergeMptLevel) mptNapot.get else entry.n.getOrElse(0.U)) === 0.U,
       entry.tag(vpnnLen - 1, 0) === gvpn(vpnnLen - 1, 0),
       entry.tag(vpnnLen - 1, pteNapotBits) === gvpn(vpnnLen - 1, pteNapotBits))
     for (i <- 1 until Level) {
@@ -1203,7 +1251,7 @@ class HptwResp(implicit p: Parameters) extends PtwBundle {
     }
     tag_match(Level) := entry.tag(gvpnLen - 1, vpnnLen * Level) === gvpn(gvpnLen - 1, vpnnLen * Level)
 
-    val level_match = MuxLookup(entry.level.getOrElse(0.U), false.B)(Seq(
+    val level_match = MuxLookup(if(mergeMptLevel) (mpt_level.get) else (entry.level.getOrElse(0.U)), false.B) (Seq(
       3.U -> tag_match(3),
       2.U -> (tag_match(3) && tag_match(2)),
       1.U -> (tag_match(3) && tag_match(2) && tag_match(1)),
@@ -1223,6 +1271,33 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
   val pf = Bool()
   val af = Bool()
 
+  def FakeMptOnlyResp(ppn: UInt, asid: UInt, vmid: UInt ) = {
+    this.entry.tag := 0.U
+    this.entry.tag :=  ppn(ppnLen-1, sectortlbwidth)
+    this.entry.ppn := ppn(ppnLen-1, sectortlbwidth) 
+    this.entry.vmid.map(_ := vmid)
+    this.entry.asid := asid
+    this.entry.level.get := 3.U
+    this.entry.v := true.B
+    this.entry.pbmt := 0.U
+    this.entry.n.get := false.B
+    this.entry.perm.get.d := true.B
+    this.entry.perm.get.a := true.B
+    this.entry.perm.get.g := false.B
+    this.entry.perm.get.u := true.B
+    this.entry.perm.get.x := true.B
+    this.entry.perm.get.w := true.B
+    this.entry.perm.get.r := true.B
+    this.addr_low :=ppn(sectortlbwidth-1,0)
+    for (j <- 0 until tlbcontiguous) {
+      this.ppn_low(j) := j.U
+      this.valididx(j) := true.B
+    }
+    this.pteidx := UIntToOH(ppn(sectortlbwidth - 1, 0)).asBools
+    this.entry.prefetch := DontCare
+    this.pf := false.B
+    this.af := false.B
+  }
 
   def genPPN(vpn: UInt): UInt = {
     MuxLookup(entry.level.get, 0.U)(Seq(
@@ -1246,14 +1321,19 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
     !pf && !entry.v && !af
   }
 
-  def hit(vpn: UInt, asid: UInt, vmid: UInt, allType: Boolean = false, ignoreAsid: Boolean = false): Bool = {
+  def hit(vpn: UInt, asid: UInt, vmid: UInt, allType: Boolean = false, ignoreAsid: Boolean = false, mptIgnoreAsid: Bool = false.B,
+    mergeMptLevel: Boolean = false, mptTlbResp:MptTlbRespBundle = 0.U.asTypeOf(new MptTlbRespBundle)): Bool = {
     require(vpn.getWidth == vpnLen)
     require(allType)
     // require(this.asid.getWidth <= asid.getWidth)
-    val asid_hit = if (ignoreAsid) true.B else (this.entry.asid === asid || this.entry.perm.get.g)
+    val asid_hit = if (HasMptCheck) (Mux(mptIgnoreAsid, true.B, if (ignoreAsid) true.B else (this.entry.asid === asid || this.entry.perm.get.g))) else 
+      (if (ignoreAsid) true.B else (this.entry.asid === asid || this.entry.perm.get.g))
+
+    val mptNapot = Option.when(mergeMptLevel) (mpt_merge_n(this.entry.level.get, this.entry.n.getOrElse(0.U), mptTlbResp.mptLevel, mptTlbResp.permIsNAPOT))
+    val mpt_level = Option.when(mergeMptLevel) (mptTlbResp.mptLevel min this.entry.level.get)
 
     val tag_match = Wire(Vec(Level + 1, Bool()))
-    tag_match(0) := Mux(entry.n.getOrElse(0.U) === 0.U,
+    tag_match(0) := Mux((if(mergeMptLevel) mptNapot.get else entry.n.getOrElse(0.U)) === 0.U,
       entry.tag(vpnnLen - sectortlbwidth - 1, 0) === vpn(vpnnLen - 1, sectortlbwidth),
       entry.tag(vpnnLen - sectortlbwidth - 1, pteNapotBits - sectortlbwidth) === vpn(vpnnLen - 1, pteNapotBits))
     for (i <- 1 until Level) {
@@ -1261,14 +1341,14 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
     }
     tag_match(Level) := entry.tag(sectorvpnLen - 1, vpnnLen * Level - sectortlbwidth) === vpn(vpnLen - 1, vpnnLen * Level)
 
-    val level_match = MuxLookup(entry.level.getOrElse(0.U), false.B)(Seq(
+    val level_match = MuxLookup(if(mergeMptLevel) (mpt_level.get) else (entry.level.getOrElse(0.U)), false.B) (Seq(
       3.U -> tag_match(3),
       2.U -> (tag_match(3) && tag_match(2)),
       1.U -> (tag_match(3) && tag_match(2) && tag_match(1)),
       0.U -> (tag_match(3) && tag_match(2) && tag_match(1) && tag_match(0)))
     )
 
-    val isSuperPage = entry.level.getOrElse(0.U) =/= 0.U || entry.n.getOrElse(0.U) =/= 0.U
+    val isSuperPage = if (mergeMptLevel) (mpt_level.get=/= 0.U || mptNapot.get=/= 0.U) else (entry.level.getOrElse(0.U) =/= 0.U || entry.n.getOrElse(0.U) =/= 0.U)
     val addr_low_hit = Mux(isSuperPage, true.B, valididx(vpn(sectortlbwidth - 1, 0)))
 
     asid_hit && level_match && addr_low_hit
@@ -1326,6 +1406,7 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
   val s2xlate = UInt(2.W)
   val s1 = new PtwSectorResp()
   val s2 = new HptwResp()
+  val mpt = Option.when(HasMptCheck) (new MptTlbRespBundle())
 
   def hasS2xlate: Bool = {
     this.s2xlate =/= noS2xlate
@@ -1369,8 +1450,9 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
   }
 
   def hit(vpn: UInt, asid: UInt, vasid: UInt, vmid: UInt, allType: Boolean = false, ignoreAsid: Boolean = false): Bool = {
-    val noS2_hit = s1.hit(vpn, asid, vmid, allType, ignoreAsid)
-    val onlyS2_hit = s2.hit(vpn, vmid)
+    val noS2_hit = s1.hit(vpn, asid, vmid, allType, ignoreAsid, 
+      (if (HasMptCheck) (this.mpt.get.mptOnly) else false.B), HasMptCheck, this.mpt.getOrElse(0.U.asTypeOf(new MptTlbRespBundle)))
+    val onlyS2_hit = s2.hit(vpn, vmid, HasMptCheck, this.mpt.getOrElse(0.U.asTypeOf(new MptTlbRespBundle)))
     // allstage and onlys1 hit
     val s1vpn = Cat(s1.entry.tag, s1.addr_low)
     val level = Mux(this.s2xlate === onlyStage1,
@@ -1386,7 +1468,14 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
     val allStage_n = (s1.entry.n.getOrElse(0.U) =/= 0.U && s2.entry.level.getOrElse(0.U) =/= 0.U) ||
       (s2.entry.n.getOrElse(0.U) =/= 0.U && s1.entry.level.getOrElse(0.U) =/= 0.U) ||
       (s1.entry.n.getOrElse(0.U) =/= 0.U && s2.entry.n.getOrElse(0.U) =/= 0.U)
-    val n = Mux(this.s2xlate === onlyStage1, s1.entry.n.getOrElse(0.U), allStage_n)
+    
+    val mptNapot = Option.when(HasMptCheck) (mpt_merge_n(level, allStage_n, mpt.get.mptLevel, mpt.get.permIsNAPOT))
+    val mptNapot_onlys1 = Option.when(HasMptCheck) (
+      mpt_merge_n(s1.entry.level.getOrElse(0.U), s1.entry.n.getOrElse(0.U), mpt.get.mptLevel, mpt.get.permIsNAPOT)
+    )
+    val mpt_level = Option.when(HasMptCheck) (level min mpt.get.mptLevel)  
+    val n = if (HasMptCheck) (Mux(this.s2xlate === onlyStage1, mptNapot_onlys1.get,mptNapot.get)) else
+      (Mux(this.s2xlate === onlyStage1, s1.entry.n.getOrElse(0.U), allStage_n))
 
     val tag_match = Wire(Vec(Level + 1, Bool()))
     tag_match(0) := Mux(n === 0.U,
@@ -1397,7 +1486,7 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
     }
     tag_match(Level) := vpn(vpnLen - 1, vpnnLen * Level) === s1vpn(vpnLen - 1, vpnnLen * Level)
 
-    val level_match = MuxLookup(level, false.B)(Seq(
+    val level_match = MuxLookup(if (HasMptCheck) mpt_level.getOrElse(0.U) else level, false.B) (Seq(
       3.U -> tag_match(3),
       2.U -> (tag_match(3) && tag_match(2)),
       1.U -> (tag_match(3) && tag_match(2) && tag_match(1)),
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
index ddd7f6f3754..a135044e089 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
@@ -92,9 +92,52 @@ case class L2TLBParameters
   enablePTWECC: Boolean = false
 )
 
+trait MPTCacheParam extends HasTlbConst {
+  val perms16Len = 48 // length of the perms
+  val mptSourceWidth = 4 // id len of the source
+  val mptLevelLenOH = 4 // level number 4 length in OH
+  val mptLevelLenUInt = 2 // level number 4 length in int
+  val mptOff = 16 // mpt offset of PA
+  // l3 8T
+  val l3Size  = 1
+  val mptL3TagLen = 5
+  val l3Associative = "fa"
+  val l3Replacer = "plru"
+  // l2 4G
+  val mptL2TagLen = mptL3TagLen + 9
+  val l2Size = 1
+  val l2Associative = "fa"
+  val l2Replacer = "plru"
+  // l1 32M
+  val l1Size = 4
+  val mptL1TagLen = mptL3TagLen + 9 * 2
+  val l1Associative = "fa"
+  val l1Replacer = "plru"
+  // l0 64k
+  val l0nSets = 32 // 5bits
+  val l0nWays = 4
+  val mptL0TagLen = mptL3TagLen + 9 * 3 - log2Up(l0nSets) // 32-5=27
+  val l0Replacer = "setplru"
+  // sp
+  val spSize = 8
+  val mptspTagLen = mptL3TagLen + 9 * 2
+  val spAssociative = "fa"
+  val spReplacer = "plru"
+
+
+  // get set num from pa def; just some wires, 0 delay
+
+  def geL0Set(ppn: UInt): UInt = {
+    require(log2Up(l0nWays) == log2Down(l0nWays), "ways must be scala.math.power of 2")
+    ppn(ppnLen - 1 - mptL0TagLen, ppnLen - mptL0TagLen - log2Up(l0nSets)) // (36-1-27,36-27-5)(8,4)
+  }
+
+  def switch0(switch: Bool, data: UInt, len: Int) = (Fill(len, switch) & data)
+}
 trait HasTlbConst extends HasXSParameter {
   val Level = if (EnableSv48) 3 else 2
 
+  val mptLevelLEN = 4 // HasMptCheck param
   val offLen  = 12
   val ppnLen  = PAddrBits - offLen
   val vpnnLen = 9
@@ -154,6 +197,13 @@ trait HasTlbConst extends HasXSParameter {
   def PMLEN16 = "b11".U
   def MaxMaskedWidth = 16
 
+  def mpt_merge_n(in1_level: UInt, in1_n: UInt, in2_level: UInt, in2_n: Bool): UInt = {
+    val in1_level_0 = (in1_level === 0.U)
+    val in2_level_0 = (in2_level === 0.U)
+    val merge = (in1_n.asBool && !in2_level_0) || (in2_n && !in1_level_0) || (in2_n && in1_n.asBool)
+    merge.asUInt
+  }
+
   def get_pn(addr: UInt) = {
     require(addr.getWidth > offLen)
     addr(addr.getWidth-1, offLen)
@@ -269,7 +319,10 @@ trait HasPtwConst extends HasTlbConst with MemoryOpConstants{
 
   // miss queue
   val MissQueueSize = l2tlbParams.ifilterSize + l2tlbParams.dfilterSize
-  val MemReqWidth = if (HasBitmapCheck) 2 *(l2tlbParams.llptwsize + 1 + 1) else (l2tlbParams.llptwsize + 1 + 1)
+  val MemReqWidth = if (HasBitmapCheck) 2 * (l2tlbParams.llptwsize + 1 + 1) else if (HasMptCheck) ((l2tlbParams.llptwsize + 3))
+  else (l2tlbParams.llptwsize + 1 + 1)
+
+  val mptcMemReqID = l2tlbParams.llptwsize + 2
   val HptwReqId = l2tlbParams.llptwsize + 1
   val FsmReqID = l2tlbParams.llptwsize
   val bMemID = log2Up(MemReqWidth)
diff --git a/src/main/scala/xiangshan/cache/mmu/MptChecker.scala b/src/main/scala/xiangshan/cache/mmu/MptChecker.scala
new file mode 100644
index 00000000000..e02ff9e6907
--- /dev/null
+++ b/src/main/scala/xiangshan/cache/mmu/MptChecker.scala
@@ -0,0 +1,1338 @@
+/***************************************************************************************
+* Copyright (c) 2021-2026 Beijing Institute of Open Source Chip (BOSC)
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http: // license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
+package xiangshan.cache.mmu
+
+import org.chipsalliance.cde.config.Parameters
+import chisel3._
+import chisel3.util._
+import xiangshan._
+import xiangshan.cache.{HasDCacheParameters, MemoryOpConstants}
+import utils._
+import utility._
+import xscache.coupledL2.utils.SplittedSRAM
+import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
+import freechips.rocketchip.tilelink._
+import utility.mbist.MbistPipeline
+import xiangshan.backend.fu.{PMPReqBundle, PMPRespBundle}
+
+class MptReqBundle(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  // mpt io interface req and resp, id is not used in ptw
+  val reqPA   = UInt(ppnLen.W)
+  val id      = UInt(mptSourceWidth.W)
+  val mptOnly = Bool() // 1bit control logic for L2TLB
+}
+
+class MptTlbRespBundle(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  // L2TB return info to L1
+  val mptOnly       = Bool()                  // 1bit control logic for L2TLB
+  val accessFault   = Bool()
+  val mptPerm       = UInt(3.W)
+  val mptLevel      = UInt(mptLevelLenUInt.W) // UInt level
+  val contigousPerm = Bool()                  // only work for non H l0 pte
+  // indicate continous 8 permission. can not compress as l0pte(8bit valididx)
+  val permIsNAPOT = Bool()
+  def genFakeResp(): Unit = {
+    this.accessFault   := false.B
+    this.mptPerm       := Fill(3, 1.U(1.W))
+    this.contigousPerm := true.B
+    this.mptLevel      := 3.U
+    this.permIsNAPOT   := true.B
+  }
+  def applyMptc2TlbResp(childBundle: MptRespBundle): Unit = {
+    this.accessFault   := childBundle.accessFault
+    this.mptPerm       := childBundle.mptPerm
+    this.contigousPerm := childBundle.contigousPerm
+    this.mptLevel      := childBundle.mptLevel
+    this.permIsNAPOT   := childBundle.permIsNAPOT
+    this.mptOnly       := childBundle.mptOnly
+  }
+}
+class MptRespBundle(implicit p: Parameters) extends MptTlbRespBundle with MPTCacheParam { // mptc
+  val id    = UInt(mptSourceWidth.W)
+  val reqPA = UInt(ppnLen.W) // in req to out req
+}
+
+class MptOutputSwitchBoxIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCacheParam {
+  val mergeArb = Flipped(DecoupledIO(new Bundle() {
+    val fault = Input(Bool())
+  }))
+  val mptIn = DecoupledIO()
+  val mptOut = Flipped(ValidIO(new MptRespBundle()))
+  val l1TLB = DecoupledIO(new Bundle() {
+    val outData    = new MptTlbRespBundle()
+    val outMptOnly = Bool()
+    val reqPA      = UInt(ppnLen.W)
+  })
+}
+class MptOutputSwitchBox(implicit p: Parameters) extends XSModule with MPTCacheParam {
+  // All L2TLB address translations must be combined with the MPT response before returning.
+  // Therefore, a small state machine is required to demultiplex control signals.
+  // Additionally, the MPT output data needs to be stored in a register.
+
+  val io = IO(new MptOutputSwitchBoxIO())
+
+  val mptOutDataWire = Wire(new MptTlbRespBundle())
+  mptOutDataWire.applyMptc2TlbResp(io.mptOut.bits)
+
+  val mptOutDataReg = Reg(new MptTlbRespBundle())
+  val mptOutMptOnly = RegInit(false.B)
+  val mptOutReqPA   = Reg(UInt(ppnLen.W))
+
+  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed ||
+    io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B)
+
+  when(io.mptOut.valid) {
+    mptOutDataReg := mptOutDataWire
+    mptOutMptOnly := io.mptOut.bits.mptOnly
+    mptOutReqPA   := io.mptOut.bits.reqPA
+  }
+  when(io.mergeArb.bits.fault && io.mergeArb.valid) {
+    mptOutMptOnly := false.B
+  }
+
+  io.l1TLB.bits.outData := Mux(io.mptOut.valid, mptOutDataWire, mptOutDataReg)
+  io.l1TLB.bits.outMptOnly := Mux(io.mptOut.valid, io.mptOut.bits.mptOnly, mptOutMptOnly) &&
+    !(io.mergeArb.valid && io.mergeArb.bits.fault)
+  io.l1TLB.bits.reqPA := Mux(io.mptOut.valid, io.mptOut.bits.reqPA, mptOutReqPA)
+
+  object MptSwitchState extends ChiselEnum {
+    val s_idle, s_send_mpt, s_send_l1_tlb = Value
+  }
+  import MptSwitchState._
+  val curState  = RegInit(s_idle)
+  val nextState = WireDefault(s_idle)
+  when(flush) {
+    curState := s_idle
+  }.otherwise {
+    curState := nextState // 2 proc FSM
+  }
+  // fsm start
+  io.mergeArb.ready := false.B
+  io.mptIn.valid    := false.B
+  io.l1TLB.valid    := false.B
+  nextState         := curState
+  switch(curState) {
+    is(s_idle) {
+      when(io.mptOut.valid && io.mptOut.bits.mptOnly) { //mptonly mode
+        // mpt valid without merge arb first implies that it is mpt only request.try change later
+        io.l1TLB.valid := true.B
+        when(io.l1TLB.ready) {
+          io.mergeArb.ready := true.B
+          nextState         := s_idle
+        }.otherwise {
+          nextState := s_send_l1_tlb
+        }
+      }.elsewhen(io.mergeArb.valid) { // normal mode, two modes are mutually exclusive
+        when(io.mergeArb.bits.fault) {
+          io.l1TLB.valid := true.B
+          when(io.l1TLB.ready) {
+            io.mergeArb.ready := true.B
+            nextState         := s_idle
+          }.otherwise {
+            nextState := s_send_l1_tlb
+          }
+        }.otherwise {
+          io.mptIn.valid := true.B
+          when(io.mptIn.ready) {
+            nextState := s_send_mpt
+          }
+        }
+      }
+    }
+
+    is(s_send_mpt) { // delay+1+mptclk
+      io.mptIn.valid := false.B
+      when(io.mptOut.valid) {
+        io.l1TLB.valid := true.B
+        when(io.l1TLB.ready) {
+          io.mergeArb.ready := true.B
+          nextState         := s_idle
+        }.otherwise {
+          nextState := s_send_l1_tlb
+        }
+      }
+    }
+
+    is(s_send_l1_tlb) {
+      io.l1TLB.valid := true.B
+      when(io.l1TLB.ready) {
+        io.mergeArb.ready := true.B
+        nextState         := s_idle
+      }
+    }
+  }
+  // fsm end
+}
+
+class PLRUOH(logWays: Int, isTop: Boolean = true) extends Module {
+  val wayNum = 1 << logWays
+  val io = IO(new Bundle {
+    val access   = Flipped(ValidIO(UInt(wayNum.W)))
+    val replace  = Output(UInt(wayNum.W))
+    val upperCom = Option.when(!isTop)(Output(Bool()))
+  })
+  if (logWays == 0) {
+    io.replace := 1.U // OH 1 is b0
+
+  } else if (logWays == 1) { // delay 1 gate
+    val changed = io.access.bits(1) || io.access.bits(0)
+    // 01 will let state points to right 10 to left entry, 00 will disable state input,if input freezes,
+    // i.e. same access value with valid for more than 1 clk, the state will not change, great
+    if (!isTop) { io.upperCom.get := changed }
+    val state = RegEnable(io.access.bits(0), false.B, io.access.valid && changed)
+    // OH last bit indicates the next state 01 state 1, 10 state 0
+    io.replace := Cat(state, ~state) // replace state 1 : 10, state 0 01 opposite of the direction iof input
+  } else {
+    val top      = wayNum
+    val mid      = 1 << (logWays - 1)
+    val plruleft = Module(new PLRUOH(logWays - 1, false)) // gen left and right entry
+    plruleft.io.access.bits  := io.access.bits(top - 1, mid)
+    plruleft.io.access.valid := io.access.valid
+
+    val plruright = Module(new PLRUOH(logWays - 1, false))
+    plruright.io.access.bits  := io.access.bits(mid - 1, 0)
+    plruright.io.access.valid := io.access.valid
+
+    val changed = plruleft.io.upperCom.get || plruright.io.upperCom.get
+    val state =
+      RegEnable(
+        plruright.io.upperCom.get,
+        false.B,
+        io.access.valid && changed
+      ) // OH last bit indicates the next state 01 state 1, 10 state 0
+    if (!isTop) { io.upperCom.get := changed }
+    val leftreplace  = Fill(plruleft.wayNum, state) & plruleft.io.replace
+    val rightreplace = Fill(plruleft.wayNum, !state) & plruright.io.replace // replace state 1 : 10, state 0 01
+    io.replace := Cat(leftreplace, rightreplace)
+  }
+}
+class PLRUOHSet(setsLog2: Int, logWays: Int) extends Module {
+  val wayNum = 1 << logWays
+  val setNum = 1 << setsLog2
+  val io = IO(new Bundle {
+    val access  = Flipped(ValidIO(UInt(wayNum.W)))
+    val replace = Output(UInt(wayNum.W))
+    val idx     = Input(UInt(setsLog2.W))
+  })
+
+  val plruSet     = Array.fill(setNum)(Module(new PLRUOH(logWays)).io)
+  val outputArray = Wire(Vec(setNum, UInt(wayNum.W)))
+  val hitArray    = Wire(Vec(setNum, Bool()))
+
+  for (i <- 0 until setNum) {
+    val Idxhit = i.U === io.idx
+    hitArray(i)             := Idxhit
+    plruSet(i).access.bits  := io.access.bits
+    plruSet(i).access.valid := io.access.valid & Idxhit
+    outputArray(i)          := plruSet(i).replace
+  }
+  io.replace := Mux1H(hitArray, outputArray) // better readablity, select replace based on hit idx
+}
+
+class MptData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val data = UInt(perms16Len.W)
+  def apply(data: UInt): Unit = this.data := data // zero extended
+  def getPPN: UInt = this.data(ppnLen - 1, 0)
+  def getAddr(offset: UInt): UInt = Cat(this.getPPN, Cat(offset, 0.U(3.W))) // 2|3 byte = 64bit
+  def extractPerm(select: UInt): (UInt) = (this.data >> (select * 3.U))(2, 0) // extract XWR using 4bit offset
+  // cal start end and extract
+}
+
+class MptEntry(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val N    = Bool()
+  val data = new MptData()
+  val L    = Bool()
+  val V    = Bool()
+  def apply(sMEMResp: UInt): Unit = {
+    this.V := (sMEMResp(0) === 1.U)
+    this.L := (sMEMResp(1) === 1.U)
+    this.N := (sMEMResp(63) === 1.U)
+    this.data.apply(sMEMResp(57, 10)) // xiangshan only support 48bit PA, so PPN only needs 36
+  }
+  def isValid: Bool =
+    this.V
+
+  def isLeaf: Bool =
+    this.L
+
+  def getAddr(offset: UInt): UInt =
+    this.data.getAddr(offset)
+
+  def genFake(level: UInt): Unit = {
+    this.N         := false.B
+    this.data.data := "h802F4".U
+    this.L         := false.B
+    this.V         := true.B
+    switch(level) {
+      is("b1000".U) {
+        this.N         := false.B
+        this.data.data := "h802F4".U
+        this.L         := false.B
+        this.V         := true.B
+      }
+      is("b0100".U) {
+        this.N         := false.B
+        this.data.data := "h802F5".U
+        this.L         := false.B
+        this.V         := true.B
+      }
+      is("b0010".U) {
+        if (HasMptCheckDefault4k) {
+          this.N         := false.B
+          this.data.data := "h802F6".U
+          this.L         := false.B
+          this.V         := true.B
+        } else {
+          this.N         := false.B
+          this.data.data := "hFFFFFFFFFFFF".U
+          this.L         := true.B
+          this.V         := true.B
+        }
+      }
+      is("b0001".U) {
+        this.N         := false.B
+        this.data.data := "hFFFFFFFFFFFF".U
+        this.L         := true.B
+        this.V         := true.B
+      }
+    }
+  }
+}
+
+class MptCacheTag(tagLen: Int, isSp: Boolean = false)(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val tag   = UInt(tagLen.W)
+  val level = Option.when(isSp)(UInt((mptLevelLenOH - 1).W)) // sp can not be l0
+  def hit(ppn: UInt): Bool =
+    tag === ppn(ppnLen - 1, ppnLen - tagLen) // tag = 5, (47,43)
+  def hitSp(ppn: UInt): Bool = {
+    val hitL3 = this.tag(tagLen - 1, tagLen - mptL3TagLen) === ppn(ppnLen - 1, ppnLen - mptL3TagLen) // tag = 5, (47,43)
+    val hitL2 = this.tag(tagLen - 1, tagLen - mptL2TagLen) === ppn(ppnLen - 1, ppnLen - mptL2TagLen)
+    val hitL1 = this.tag === ppn(ppnLen - 1, ppnLen - tagLen)
+    val hotVal = Mux1H(Seq(
+      this.level.get(2) -> hitL3,
+      this.level.get(1) -> hitL2,
+      this.level.get(0) -> hitL1
+    )) // it is a tuple scala> 1 -> 2 res0: (Int, Int) = (1,2)
+    hotVal
+  }
+}
+class MptCacheData(isPerms: Boolean = false)(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val data = if (isPerms) UInt(perms16Len.W) else UInt(ppnLen.W) // 36.W
+  def extractPerm(select: UInt): (UInt) = { // extract XWR using 4bit offset
+    // cal start end and extract
+    require(isPerms, "extractPerm is only valid when isPerms is true")
+    (this.data >> (select * 3.U))(
+      2,
+      0
+    ) // not quite sure what kind of crap will be synthesized. I meant it to be a binary mux
+  }
+}
+
+class MptCacheL0(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val cacheData = new MptCacheData(isPerms = true)
+  val tag       = new MptCacheTag(tagLen = mptL0TagLen) // , isL0 = true )
+}
+
+class MptCacheReq(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val mptOnly = Bool()
+  val reqPA   = UInt(ppnLen.W)
+  val source  = UInt(mptSourceWidth.W)
+}
+
+// create pipe with reset
+class MPTPipe(implicit p: Parameters) extends MptCacheReq {
+  val dataValid  = Bool()
+  val flushCache = Bool()
+
+  def applySplitData(MPTPipeControl: MPTPipeControl, MPTPipeData: MPTPipeData): Unit = {
+    this.dataValid  := MPTPipeControl.dataValid
+    this.flushCache := MPTPipeControl.flushCache
+    this.mptOnly    := MPTPipeControl.mptOnly
+    this.reqPA      := MPTPipeData.reqPA
+    this.source     := MPTPipeData.source
+  }
+
+  def createSplitData(): (MPTPipeControl, MPTPipeData) = {
+    val mptPipeControl = Wire(new MPTPipeControl) // evil defination of hardware type :-(
+    val mptPipeData    = Wire(new MPTPipeData)
+    mptPipeData.reqPA  := this.reqPA
+    mptPipeData.source := this.source
+
+    mptPipeControl.dataValid  := this.dataValid
+    mptPipeControl.flushCache := this.flushCache
+    mptPipeControl.mptOnly    := this.mptOnly
+    (mptPipeControl, mptPipeData)
+  }
+}
+
+class MPTPipeControl(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val dataValid  = Bool()
+  val flushCache = Bool()
+  val mptOnly    = Bool()
+  def applyPipeData(mptPipe: MPTPipe): Unit = {
+    this.dataValid  := mptPipe.dataValid
+    this.flushCache := mptPipe.flushCache
+    this.mptOnly    := mptPipe.mptOnly
+  }
+}
+
+class MPTPipeData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val reqPA  = UInt(ppnLen.W)
+  val source = UInt(mptSourceWidth.W)
+  def applyPipeData(MPTPipe: MPTPipe): Unit = {
+    this.reqPA  := MPTPipe.reqPA
+    this.source := MPTPipe.source
+  }
+}
+
+object MPTPipeWithReset {
+  def apply(enqValid: Bool, enqBits: MPTPipe, reset: Bool, latency: Int): Valid[MPTPipe] = {
+    require(latency >= 0, "Pipe latency must be greater than or equal to zero!")
+
+    if (latency == 0) {
+      val out = Wire(Valid(chiselTypeOf(enqBits)))
+      out.valid := enqValid
+      out.bits  := enqBits
+      out
+    } else {
+      val v = RegNext(enqValid, false.B) // valid has reset
+      val (mptPipeControlIn, mptPipeDataIn) = enqBits.createSplitData() // split input as data and control
+      val mptPipeData = RegEnable(mptPipeDataIn, (mptPipeControlIn.dataValid || mptPipeControlIn.flushCache) && enqValid)
+      // data has no reset
+      val mptPipeControl = RegEnable(mptPipeControlIn, 0.U.asTypeOf(mptPipeControlIn), enqValid) // control has reset
+      val b              = Wire(chiselTypeOf(enqBits))
+      when(reset) {
+        mptPipeControl := 0.U.asTypeOf(mptPipeControlIn)
+        v := false.B
+      }
+      b.applySplitData(mptPipeControl, mptPipeData) // merge pipe control and data signal
+      apply(v, b, reset, latency - 1)
+    }
+  }
+}
+
+class RefillBundle(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val level      = UInt(mptLevelLenOH.W)
+  val pa         = UInt((PAddrBits - mptOff).W)
+  val refillData = new MptData()
+  val isAf       = Bool()
+  val isLeafMpte = Bool() // is leaf? decide what cache is refilled
+}
+class MPTCacheIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCacheParam {
+  val req = Flipped(DecoupledIO(new MptCacheReq()))
+
+  val respHit = ValidIO(new Bundle { // source is waiting for cache to resp
+    val accessFault      = Bool()
+    val perm             = UInt(3.W)
+    val tlbContigousPerm = Bool()
+    val permIsNAPOT      = Bool()
+    val source           = UInt(mptSourceWidth.W)
+    val mptLevel         = UInt(log2Up(mptLevelLenOH).W)
+    val mptOnly          = Bool()
+    val reqPA            = UInt(ppnLen.W)
+  })
+
+  val respMiss = DecoupledIO(new Bundle {
+    val hitLevel = UInt(mptLevelLenOH.W)
+    val ppn      = UInt(ppnLen.W) // minsize is 4k,dont need 12bits offset
+    val source   = UInt(mptSourceWidth.W)
+    val pa       = UInt(ppnLen.W)
+    val mptOnly  = Bool()
+  })
+
+  val refill = Flipped(ValidIO(new RefillBundle()))
+
+}
+
+class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
+  val io = IO(new MPTCacheIO)
+  // // mfence signal
+  val mfenceActive = WireInit(false.B)
+  val fencePA      = WireInit(false.B)
+  val mfencevalid  = io.sfence.valid && io.sfence.bits.mfence.get
+  // This MPT design supports partial cache flushing by PA. When flushing, in addition to leaf nodes, intermediate nodes are also invalidated
+  switch(Cat(io.sfence.bits.rs2, io.sfence.bits.rs1).asUInt) {
+    is("b11".U) {
+      fencePA := (io.sfence.bits.id === io.csr.mmpt.sdid) && mfencevalid // delay of about 10 gates
+    }
+    is("b01".U) {
+      fencePA := mfencevalid
+    }
+    is("b10".U) {
+      mfenceActive := (io.sfence.bits.id === io.csr.mmpt.sdid) && mfencevalid
+    }
+    is("b00".U) {
+      mfenceActive := mfencevalid
+    }
+  }
+  val flushAll      = mfenceActive || io.csr.mmpt.changed
+
+  val pipeFlowEn = Wire(Bool())
+  val refilling     = Wire(Bool())
+  val refillCounter = RegInit(0.U(4.W))
+  when(io.refill.valid) { // && ! io.refill.bits.isAf) { // refill valid and not access fault
+    refillCounter := "b1000".U
+  }.elsewhen(io.sfence.valid) {
+    refillCounter := 0.U
+  }.otherwise {
+    refillCounter := refillCounter >> 1.U
+  }
+
+  refilling := refillCounter > 0.U // is refiill state when counter ! = 0 for 4 rounds
+  val respHitRegTmp = Wire(Bool()) // resphitreg is defined later
+  pipeFlowEn := (io.respMiss.ready || respHitRegTmp) || refilling || fencePA
+  // Without respHitReg, if the final stage hits and stalls,
+  // the hit control signal will get stuck at a high level, causing the entire MMU to fail.
+  // Switch the pipeline input based on whether it is an mfence-PA operation or a refill operation.
+  val paFenceInputs = Wire(new MPTPipe)
+  paFenceInputs.reqPA      := io.sfence.bits.addr(47, 12)
+  paFenceInputs.source     := io.req.bits.source
+  paFenceInputs.dataValid  := false.B
+  paFenceInputs.flushCache := true.B
+  paFenceInputs.mptOnly    := false.B
+
+  val ioInputs = Wire(new MPTPipe)
+  ioInputs.reqPA      := io.req.bits.reqPA
+  ioInputs.source     := io.req.bits.source
+  ioInputs.dataValid  := io.req.fire
+  ioInputs.flushCache := false.B
+  ioInputs.mptOnly    := io.req.bits.mptOnly
+
+  val pipeInputs = Wire(new MPTPipe)
+
+  val stageReq     = MPTPipeWithReset(pipeFlowEn, pipeInputs, flushAll, 1)
+  val stageDelayin = stageReq.bits
+  val stageDelay   = MPTPipeWithReset(pipeFlowEn, stageDelayin, flushAll, 1)
+  val stageCheckin = stageDelay.bits
+  val stageCheck   = MPTPipeWithReset(pipeFlowEn, stageCheckin, flushAll, 1)
+  val stageRespin  = stageCheck.bits
+  val stageResp    = MPTPipeWithReset(pipeFlowEn, stageRespin, flushAll, 1)
+
+  // priority
+  // 1. fence PA
+  // 2. refill and last stage valid
+  // 3. normal request
+
+  pipeInputs := Mux(fencePA, paFenceInputs, Mux(refilling && stageResp.bits.dataValid, stageResp.bits, ioInputs))
+  when(io.sfence.valid) {
+
+    pipeInputs.dataValid := false.B
+    pipeInputs.mptOnly := false.B
+
+    stageDelayin.dataValid := false.B
+    stageDelayin.mptOnly := false.B
+
+    stageCheckin.dataValid := false.B
+    stageCheckin.mptOnly := false.B
+
+    stageRespin.dataValid := false.B
+    stageRespin.mptOnly := false.B
+  }
+  io.req.ready := ((io.respMiss.ready && !refilling) || (refilling && !stageResp.bits.dataValid)) && !fencePA
+  // init cache tag
+  val l3Tag   = Reg(Vec(l3Size, new MptCacheTag(tagLen = mptL3TagLen)))
+  val l3Valid = RegInit(Vec(l3Size, Bool()), 0.U.asTypeOf(Vec(l3Size, Bool())))
+
+  val l2Tag   = Reg(Vec(l2Size, new MptCacheTag(tagLen = mptL2TagLen)))
+  val l2Valid = RegInit(Vec(l2Size, Bool()), 0.U.asTypeOf(Vec(l2Size, Bool())))
+
+  val l1Tag   = Reg(Vec(l1Size, new MptCacheTag(tagLen = mptL1TagLen)))
+  val l1Valid = RegInit(Vec(l1Size, Bool()), 0.U.asTypeOf(Vec(l1Size, Bool())))
+
+  val spTag   = Reg(Vec(spSize, new MptCacheTag(tagLen = mptspTagLen, isSp = true)))
+  val spValid = RegInit(Vec(spSize, Bool()), 0.U.asTypeOf(Vec(spSize, Bool())))
+
+  val l3Data = Reg(Vec(l3Size, new MptCacheData()))
+  val l2Data = Reg(Vec(l2Size, new MptCacheData()))
+  val l1Data = Reg(Vec(l1Size, new MptCacheData()))
+  val spData = Reg(Vec(spSize, new MptCacheData(isPerms = true)))
+  val l0Data = Module(new SplittedSRAM(
+    new MptCacheL0(),
+    set = l0nSets,
+    way = l0nWays,
+    setSplit = 1,
+    waySplit = 2,
+    dataSplit = 1,
+    singlePort = sramSinglePort,
+    readMCP2 = false,
+    hasMbist = hasMbist,
+    hasSramCtl = hasSramCtl
+  )) // 1clk delay from req to resp
+  val l0Valid = RegInit(Vec(l0nSets, Vec(l0nWays, Bool())), 0.U.asTypeOf(Vec(l0nSets, Vec(l0nWays, Bool()))))
+
+  val mptCacheL3Replace = Module(new PLRUOH(logWays = log2Up(l3Size))).io
+  val mptCacheL2Replace = Module(new PLRUOH(logWays = log2Up(l2Size))).io
+  val mptCacheL1Replace = Module(new PLRUOH(logWays = log2Up(l1Size))).io
+  val MptCacheL0Replace = Module(new PLRUOHSet(setsLog2 = log2Up(l0nSets), logWays = log2Up(l0nWays))).io
+  val mptCacheSpReplace = Module(new PLRUOH(logWays = log2Up(spSize))).io
+  // alloc replacement policy,use PLRU with Onehot in/out
+
+  val (l3Hit, l3hitPPN) = {
+    val hitVecTemp = l3Tag.zip(l3Valid).map { case (x, v) =>
+      x.hit(stageReq.bits.reqPA) && v
+    } // hit when valid and tag equal stagereq
+    when(stageReq.bits.flushCache) { // clean fence valid if hit tag
+      hitVecTemp.zip(l3Valid).map { case (x, v) =>
+        when(x)(v := false.B)
+      }
+    }
+    val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid)
+    // ready at stage check, use datavalid instead of stageReq.valid
+    val hitData = RegEnable(Mux1H(hitVecTemp, l3Data), stageReq.bits.dataValid)
+    // we can use onehot mux, should be faster.
+    val hit =RegEnable(hitVecTemp.reduce(_ || _), stageReq.bits.dataValid)
+    // 1 bit hit, avaliable at stage delay after 2 or gates
+    mptCacheL3Replace.access.bits := hitVec.asUInt
+    // assign hitVec to plru to update plru state, miss(hitVec = h0) will not change the plru state
+    mptCacheL3Replace.access.valid := stageDelay.bits.dataValid // ready at stage check
+    (hit, hitData)
+  }
+
+  val (l2Hit, l2hitPPN) = {
+    val hitVecTemp = l2Tag.zip(l2Valid).map { case (x, v) =>
+      x.hit(stageReq.bits.reqPA) && v
+    }                                // hit when valid and tag equal stagereq
+    when(stageReq.bits.flushCache) { // clear fence valid
+      hitVecTemp.zip(l2Valid).map { case (x, v) =>
+        when(x)(v := false.B)
+      }
+    }
+    val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid) // ready at stage check
+    // val hitData = ParallelPriorityMux(hitVec zip l3Data)
+    val hitData = RegEnable(Mux1H(hitVecTemp, l2Data), stageReq.bits.dataValid)
+    // we can use onehot mux, should be faster.
+    val hit = RegEnable(hitVecTemp.reduce(_ || _), stageReq.bits.dataValid)
+    // 1 bit hit, avaliable at stage delay after 2 or gates
+    mptCacheL2Replace.access.bits := hitVec.asUInt
+    // assign hitVec to plru to update plru state, miss(hitVec = h0) will not change the plru state
+    mptCacheL2Replace.access.valid := stageDelay.bits.dataValid // ready at stage check
+    (hit, hitData)
+  }
+
+  val (l1Hit, l1hitPPN) = {
+    val hitVecTemp = l1Tag.zip(l1Valid).map { case (x, v) =>
+      x.hit(stageReq.bits.reqPA) && v
+    }                                // hit when valid and tag equal stagereq
+    when(stageReq.bits.flushCache) { // clear fence valid
+      hitVecTemp.zip(l1Valid).map { case (x, v) =>
+        when(x)(v := false.B)
+      }
+    }
+    val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid) // ready at stage check
+    // val hitData = ParallelPriorityMux(hitVec zip l3Data)
+    val hitData = RegEnable(Mux1H(hitVecTemp, l1Data), stageReq.bits.dataValid)
+    // we can use onehot mux, should be faster.
+    val hit = RegEnable(hitVecTemp.reduce(_ || _), stageReq.bits.dataValid)
+    // 1 bit hit, avaliable at stage delay after 2 or gates
+
+    mptCacheL1Replace.access.bits := hitVec.asUInt
+    // assign hitVec to plru to update plru state, miss(hitVec = h0) will not change the plru state
+    mptCacheL1Replace.access.valid := stageDelay.bits.dataValid // ready at stage check
+    (hit, hitData)
+
+  }
+  // // // // // // gen addr hit(l3-l1) at stage check // // // // // //
+  val missLevel = Mux(io.csr.mmpt.mode === 2.U, "b1000".U, "b0100".U)
+    // enablesmmpt52 = true, 0 delay since io.csr.mmpt.mode will not change during cache read
+  val hitAddrLevelTemp = Mux(l1Hit, "b0001".U, Mux(l2Hit, "b0010".U, Mux(l3Hit, "b0100".U, missLevel)))
+  val hitAddrDataTemp = Mux(l1Hit, l1hitPPN.data, Mux(l2Hit, l2hitPPN.data,
+    Mux(l3Hit, l3hitPPN.data, io.csr.mmpt.ppn(ppnLen - 1, 0))))
+  val hitAddrData  = RegEnable(hitAddrDataTemp, stageDelay.bits.dataValid)
+  val hitAddrLevel = RegEnable(hitAddrLevelTemp, stageDelay.bits.dataValid)
+
+  // // // // // // // // // // // // // /
+
+  val (l0Hit, l0HitPerms, l0PermTlbCompress, l0PermIs64kNAPOT) = {
+    val idx = geL0Set(pipeInputs.reqPA) //
+
+    l0Data.io.r.req.bits.apply(setIdx = idx) // .. 0 delay stagereq reg get valid at the same time
+    l0Data.io.r.req.valid := pipeInputs.dataValid || pipeInputs.flushCache // read and write at the same time will not cause error, but read is invalid
+
+    val l0validReg = RegEnable(
+      l0Valid(geL0Set(stageReq.bits.reqPA)),
+      0.U.asTypeOf(Vec(l0nWays, Bool())),
+      stageReq.bits.dataValid || stageReq.bits.flushCache
+    )
+    when(flushAll) {
+      l0validReg := 0.U.asTypeOf(Vec(l0nWays, Bool()))
+    }
+    val dataResp = RegEnable(l0Data.io.r.resp.data, stageReq.bits.dataValid || stageReq.bits.flushCache)
+    // data avaliable at stage delay
+    val setTag  = dataResp.map(_.tag)
+    val setData = dataResp.map(_.cacheData) // 4 entry+tag
+    // some wire
+    val hitVecTemp = setTag.zip(l0validReg).map { case (x, v) =>
+      x.hit(stageDelay.bits.reqPA) && v
+    } // hit when valid and tag equal
+
+    // delay (29 bit === ):(1xnor + 5*and), (&& valid):(1 and) total 7
+    // MfencePA
+    when(stageDelay.bits.flushCache) { // clear fence valid
+      hitVecTemp.zipWithIndex.map { case (x, i) =>
+        when(x)(l0Valid(geL0Set(stageDelay.bits.reqPA))(i) := false.B)
+      }
+    }
+    //
+    val hitVec = RegEnable(VecInit(hitVecTemp), stageDelay.bits.dataValid) // valid at stage check
+    val hitData = Mux1H(hitVecTemp, setData)
+    // we use onehot mux, should be faster than ParallelPriorityMux. delay:log2(4)*2 = 4
+    val hitDataReg = RegEnable(hitData, stageDelay.bits.dataValid) // valid at stage check, total delay 11 gates
+    val hit = RegEnable(hitVecTemp.reduce(_ || _), stageDelay.bits.dataValid) // 4-> 1 bit hit
+
+    val hitPermsTemp = hitDataReg.extractPerm(stageCheck.bits.reqPA(3, 0)) // always 15:12 delay log2(16)*3 = 12 gates
+
+    MptCacheL0Replace.access.bits := hitVec.asUInt // assign hitVec to plru to update plru state, miss(hitVec = h0) will not change the plru state
+    MptCacheL0Replace.access.valid := stageCheck.bits.dataValid // processing at stage check, ready at stage resp
+    MptCacheL0Replace.idx          := geL0Set(stageCheck.bits.reqPA)
+
+    val permsAsVec = Wire(Vec(16, UInt(3.W))) // perm xwr bits, total 16 xwrs in one mpte
+    for (i <- 0 until 16) {
+      permsAsVec(i) := hitDataReg.data(2 + i * 3, i * 3)
+    }
+    val permsEqual = Wire(Vec(16 - 1, Bool()))
+    for (i <- 0 until 16 - 1) {
+      permsEqual(i) := permsAsVec(i + 1) === permsAsVec(i)
+    } // delay 1XNOR + 2 and gates = 3
+    val low8PermsAllEqual  = permsEqual.slice(0, 7).reduce(_ && _)                    // = permsEqual(6,0), delay 3
+    val high8PermsAllEqual = permsEqual.slice(8, 15).reduce(_ && _)                   // = permsEqual(14,8)
+    val allEqual           = low8PermsAllEqual && high8PermsAllEqual && permsEqual(7) // Delay 2
+    val tlbCompress = Mux(stageCheck.bits.reqPA(3, 0) < 8.U, low8PermsAllEqual, high8PermsAllEqual) // delay 3
+
+    (
+      hit,
+      RegEnable(hitPermsTemp, stageCheck.bits.dataValid),
+      RegEnable(tlbCompress, stageCheck.bits.dataValid),
+      RegEnable(allEqual, stageCheck.bits.dataValid)
+    ) // ready at stage resp,hit reaady at stage check
+  }
+
+  // val (sphit,spHitPerms,spPermIsNAPOT,splevel) = {
+  val (sphit, spHitPerms, splevel) = {
+    val hitVecTemp = spTag.zip(spValid).map { case (x, v) =>
+      x.hitSp(stageReq.bits.reqPA) && v
+    }                                // hit when valid and tag equal delay 7 + mux1h delay 4 gates
+    when(stageReq.bits.flushCache) { // clear fence valid
+      hitVecTemp.zip(spValid).map { case (x, v) =>
+        when(x)(v := false.B)
+      }
+    }
+
+    val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid) // ready at stage delay
+    // // // //
+    val levelVec  = spTag.map(x => x.level.get)
+    val level     = Mux1H(hitVec, levelVec)       // ready at stage delay, require cache to be consistent
+    val levelReg  = RegEnable(level, stageDelay.bits.dataValid)
+    val levelUInt = Wire(UInt(mptLevelLenUInt.W)) // 4levels len 2
+    levelUInt := OHToUInt(Cat(levelReg, 0.U(1.W)))
+
+    val extractOffset = Mux1H(Seq(
+      level(2) -> stageDelay.bits.reqPA(3 + 9 * 3, 9 * 3),
+      level(1) -> stageDelay.bits.reqPA(3 + 9 * 2, 9 * 2),
+      level(0) -> stageDelay.bits.reqPA(3 + 9, 9)
+    ))
+
+    val extractOffsetReg = RegEnable(extractOffset, stageDelay.bits.dataValid) // ready at stage check
+
+    // val hitData = ParallelPriorityMux(hitVec zip l3Data)
+    val hitData      = Mux1H(hitVec, spData)                         // we can use onehot mux, should be faster.
+    val hitDataReg   = RegEnable(hitData, stageDelay.bits.dataValid) // valid at stage check, total delay 11 gates
+    val hitPermsTemp = hitDataReg.extractPerm(extractOffsetReg)      // always 15:12 delay log2(16)*3 = 12 gates
+    val hit = RegEnable(hitVec.reduce(_ || _), stageDelay.bits.dataValid) // 1 bit hit
+
+    mptCacheSpReplace.access.bits := hitVec.asUInt // assign hitVec to plru to update plru state, miss(hitVec = h0) will not change the plru state
+    mptCacheSpReplace.access.valid := stageDelay.bits.dataValid // ready at stage check
+
+    (
+      hit,
+      RegEnable(hitPermsTemp, stageCheck.bits.dataValid),
+      RegEnable(levelUInt, stageCheck.bits.dataValid)
+    ) // ready at stage resp,hit reaady at stage check
+
+  }
+  // gen perms hit l0 sp at stage check
+
+  val hitPerms = sphit || l0Hit
+  val respHitReg = RegEnable(hitPerms & stageCheck.bits.dataValid, false.B, pipeFlowEn)
+  // Data is latched regardless of dataValid. If dataValid is low, the hit signal is also considered invalid.
+  // In this case, hitPerms actually holds the result from the previous pipeline stage.
+
+  respHitRegTmp    := respHitReg
+  io.respHit.valid := respHitReg && !refilling && !fencePA
+  val (sphitReg, l0hitReg) = (RegEnable(sphit, pipeFlowEn), RegEnable(l0Hit, pipeFlowEn))
+  io.respHit.bits.perm := Mux1H(
+    Seq(sphitReg, l0hitReg),
+    Seq(spHitPerms, l0HitPerms)
+  ) // 1 mux at output, 2 gates, should be fine
+  io.respHit.bits.source           := stageResp.bits.source
+  io.respHit.bits.mptOnly          := stageResp.bits.mptOnly
+  io.respHit.bits.reqPA            := stageResp.bits.reqPA
+  io.respHit.bits.tlbContigousPerm := l0hitReg && l0PermTlbCompress
+  io.respHit.bits.permIsNAPOT := l0hitReg && l0PermIs64kNAPOT
+  io.respHit.bits.accessFault := (!io.respHit.bits.perm(0)) && io.respHit.bits.perm(
+    1
+  ) // not read but write // false.B // entry in mpt cache is always valid
+  io.respHit.bits.mptLevel := Mux1H(
+    Seq(sphitReg, l0hitReg),
+    Seq(splevel, 0.U(mptLevelLenUInt.W))
+  ) // splevel is converted to binary for l1/l2tlb level compare with s1pte and s2pte
+
+  val respMissReg = RegEnable(!hitPerms & stageCheck.bits.dataValid, false.B, pipeFlowEn)
+  // read regardless of dataValid
+  io.respMiss.valid := respMissReg && !refilling && !fencePA
+  io.respMiss.bits.hitLevel := RegEnable(hitAddrLevel, pipeFlowEn)
+  io.respMiss.bits.ppn     := RegEnable(hitAddrData, pipeFlowEn)
+  io.respMiss.bits.source  := stageResp.bits.source
+  io.respMiss.bits.mptOnly := stageResp.bits.mptOnly
+  io.respMiss.bits.pa      := stageResp.bits.reqPA
+  // read logic end
+  // refill write logic start
+  /* If a circular pipe is used to resolve refill conflicts, the cache will be accessed twice with the same tag during the loop.
+  For TLRU, this is not an issue as the LRU queue remains unchanged. But what about PLRU? It should probably be fine as well.
+  If TLRU is used, when the cache is empty, the LRU pointer should point to an empty entry because empty entries have never been accessed.
+  Therefore, we can normally use the entry pointed to by the LRU for refilling.
+  If PLRU is used, when the cache is empty, accessing neighbors of an empty entry may cause the LRU to stop pointing to that empty entry.
+  For example, with a 4-way PLRU sequence ABACAD, B would be replaced by D, while the empty entry next to A would not be written to.
+  We could fill the cache from top to bottom regardless of the PLRU state when it is not full.
+  However, due to tight timing constraints, let's try using only PLRU first to see how much waste/inefficiency it causes.*/
+
+  val l3RefillEn =
+    io.refill.bits.level(3).asBool & (!io.refill.bits.isLeafMpte) & (io.refill.valid && !io.refill.bits.isAf)
+  val rfl3Tag     = io.refill.bits.pa(PAddrBits - mptOff - 1, PAddrBits - mptOff - mptL3TagLen)
+  val l3VictimWay = mptCacheL3Replace.replace // ready after idx is set, OH encoding
+
+  val l2RefillEn =
+    io.refill.bits.level(2).asBool & (!io.refill.bits.isLeafMpte) & (io.refill.valid && !io.refill.bits.isAf)
+  val rfl2Tag     = io.refill.bits.pa(PAddrBits - mptOff - 1, PAddrBits - mptOff - mptL2TagLen)
+  val l2VictimWay = mptCacheL2Replace.replace // ready after idx is set, OH encoding
+
+  val l1RefillEn =
+    io.refill.bits.level(1).asBool & (!io.refill.bits.isLeafMpte) & (io.refill.valid && !io.refill.bits.isAf)
+  val rfl1Tag     = io.refill.bits.pa(PAddrBits - mptOff - 1, PAddrBits - mptOff - mptL1TagLen)
+  val l1VictimWay = mptCacheL1Replace.replace // ready after idx is set, OH encoding
+
+  val l0RefillEn =
+    io.refill.bits.level(0).asBool & (io.refill.bits.isLeafMpte) & (io.refill.valid && !io.refill.bits.isAf)
+  val rfl0Tag     = io.refill.bits.pa(PAddrBits - mptOff - 1, PAddrBits - mptOff - mptL0TagLen)
+  val rfl0SetIdx  = io.refill.bits.pa(PAddrBits - mptOff - mptL0TagLen - 1, 0)
+  val l0VictimWay = MptCacheL0Replace.replace // ready after idx is set, OH encoding
+
+  val spRefillEn =
+    (!io.refill.bits.level(0).asBool) & io.refill.bits.isLeafMpte & (io.refill.valid && !io.refill.bits.isAf)
+  val rfspTag     = io.refill.bits.pa(PAddrBits - mptOff - 1, PAddrBits - mptOff - mptspTagLen)
+  val spVictimWay = mptCacheSpReplace.replace //
+
+  // /write data into cache 1 cycle
+  val l0Wdata = Wire(new MptCacheL0()) // 0 delay, wire signal assignment
+  l0Wdata.tag.tag        := rfl0Tag
+  l0Wdata.cacheData.data := io.refill.bits.refillData.data
+  l0Data.io.w.req <> DontCare // default val for write channel is invalid
+  l0Data.io.w.req.valid := false.B
+  when(l0RefillEn) {
+    l0Data.io.w.apply(
+      valid = true.B, // valid when refill
+      setIdx = rfl0SetIdx,
+      data = l0Wdata,
+      waymask = l0VictimWay
+    )
+    for (j <- 0 until l0nSets) {
+      for (i <- 0 until l0nWays) {
+        when(l0VictimWay(i) === 1.U && rfl0SetIdx === j.U) {
+          l0Valid(j)(i) := true.B
+        }
+      }
+    }
+
+    MptCacheL0Replace.idx := rfl0SetIdx
+    /* During refill, switch the PLRU input to the refill data and update immediately.
+    Timing breakdown: 4-way update delay (2 gates), 32-set index switching (3 gates),
+    l0 refillEn (1 gate), mux for switching refillEn input (3 gates).*/
+    MptCacheL0Replace.access.bits  := l0VictimWay
+    MptCacheL0Replace.access.valid := true.B
+  }
+
+  when(l3RefillEn) {
+    for (i <- 0 until l3Size) {
+      when(l3VictimWay(i) === 1.U) {
+        l3Tag(i).tag   := rfl3Tag
+        l3Valid(i)     := true.B
+        l3Data(i).data := io.refill.bits.refillData.getPPN
+      }
+    }
+    mptCacheL3Replace.access.bits  := l3VictimWay
+    mptCacheL3Replace.access.valid := true.B
+  }
+
+  when(l2RefillEn) {
+    for (i <- 0 until l2Size) {
+      when(l2VictimWay(i) === 1.U) {
+        l2Tag(i).tag   := rfl2Tag
+        l2Valid(i)     := true.B
+        l2Data(i).data := io.refill.bits.refillData.getPPN
+      }
+    }
+    mptCacheL2Replace.access.bits  := l2VictimWay
+    mptCacheL2Replace.access.valid := true.B
+  }
+  when(l1RefillEn) {
+    for (i <- 0 until l1Size) {
+      when(l1VictimWay(i) === 1.U) {
+        l1Tag(i).tag   := rfl1Tag
+        l1Valid(i)     := true.B
+        l1Data(i).data := io.refill.bits.refillData.getPPN
+      }
+    }
+    mptCacheL1Replace.access.bits  := l1VictimWay
+    mptCacheL1Replace.access.valid := true.B
+  }
+  when(spRefillEn) {
+    /*spVictimWay.zipWithIndex.map{case(en,i) => // update cache content
+              when(en) {*/
+    for (i <- 0 until spSize) {
+      when(spVictimWay(i) === 1.U) {
+        spTag(i).tag       := rfspTag
+        spValid(i)         := true.B
+        spTag(i).level.get := io.refill.bits.level(3, 1)
+        spData(i).data     := io.refill.bits.refillData.data
+      }
+    }
+    mptCacheSpReplace.access.bits  := spVictimWay
+    mptCacheSpReplace.access.valid := true.B
+  }
+
+  when(flushAll) {
+    refillCounter := 0.U
+    l3Valid := 0.U.asTypeOf(Vec(l3Size, Bool()))
+    l2Valid := 0.U.asTypeOf(Vec(l2Size, Bool()))
+    l1Valid := 0.U.asTypeOf(Vec(l1Size, Bool()))
+    spValid := 0.U.asTypeOf(Vec(spSize, Bool()))
+    l0Valid := 0.U.asTypeOf(Vec(l0nSets, Vec(l0nWays, Bool())))
+    respHitReg := false.B
+    respMissReg := false.B
+  }
+}
+// // // MptMissQueue START // // //
+
+class MptMissQueueToTWReqBundle(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val hitAddr  = UInt(ppnLen.W)
+  val reqPA    = UInt((PAddrBits - mptOff).W)
+  val hitLevel = UInt(mptLevelLenOH.W)
+}
+
+class MissCacheBundle(implicit p: Parameters) extends XSBundle with MPTCacheParam {
+  val hitLevel = UInt(mptLevelLenOH.W)
+  val hitAddr  = UInt(ppnLen.W) // hit addr
+  val source   = UInt(mptSourceWidth.W)
+  val pa       = UInt(ppnLen.W) // req pa minsize is 4k,dont need 12bits offset
+  val mptOnly  = Bool()         // 1bit control signal for tlb, does basically nothing in mptc
+}
+
+class MptMissQueueIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCacheParam {
+  val missCache = Flipped(DecoupledIO(new MissCacheBundle()))
+
+  val twReq = DecoupledIO(new MptMissQueueToTWReqBundle())
+
+  val refill = Flipped(ValidIO(new RefillBundle()))
+  val resp = ValidIO(new Bundle { // source is waiting for cache to resp
+    val accessFault     = Bool()
+    val mptLevel        = UInt(mptLevelLenOH.W)
+    val perm            = UInt(3.W)
+    val PermTlbCompress = Bool()
+    val permIsNAPOT     = Bool()
+    val mptOnly         = Bool()
+    val reqPA           = UInt(ppnLen.W)
+    val source          = UInt(mptSourceWidth.W)
+  })
+}
+
+class MptMissQueue(implicit p: Parameters) extends XSModule with MPTCacheParam {
+  val io = IO(new MptMissQueueIO)
+
+  val flush = io.sfence.valid || io.csr.mmpt.changed
+  val reqFIFO = Module(new Queue(new MissCacheBundle(), entries = 4, pipe = true, hasFlush = true)).io
+  // FIFO queue,record offset
+  val fifoNotEmpty = reqFIFO.deq.valid
+  val fifoNotFull  = reqFIFO.enq.ready
+  val reqAltInput = Wire(new MissCacheBundle()) // alternative input for refill
+  val refillReg = RegEnable(io.refill.bits, 0.U.asTypeOf(new RefillBundle()), io.refill.valid)
+  // refill input, valid when refill is valid
+
+  val refilling     = Wire(Bool())
+  val refillCounter = RegInit(0.U(4.W))
+  when(io.refill.valid) { // refill valid and not access fault
+    refillCounter := "b1000".U
+  }.otherwise {
+    refillCounter := refillCounter >> 1.U
+  }
+
+  refilling := refillCounter > 0.U // is refiill state when counter ! = 0 4 clk
+
+  val l3Hit = reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - mptL3TagLen) === refillReg.pa(
+    PAddrBits - mptOff - 1,
+    PAddrBits - mptOff - mptL3TagLen
+  ) // 35:31 31:27
+  val l2Hit = reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - mptL2TagLen) === refillReg.pa(
+    PAddrBits - mptOff - 1,
+    PAddrBits - mptOff - mptL2TagLen
+  ) // 35:22 31:18
+  val l1Hit = reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - mptL1TagLen) === refillReg.pa(
+    PAddrBits - mptOff - 1,
+    PAddrBits - mptOff - mptL1TagLen
+  ) // 35:13 31:9
+  val l0Hit = reqFIFO.deq.bits.pa(ppnLen - 1, mptOff - offLen) === refillReg.pa // 35:4 31:0
+  val hitFIFO = Mux1H(Seq(
+    refillReg.level(3) -> l3Hit,
+    refillReg.level(2) -> l2Hit,
+    refillReg.level(1) -> l1Hit,
+    refillReg.level(0) -> l0Hit // 4k addr len can not repeat
+  )) //
+
+  io.missCache.ready := fifoNotFull && !refilling
+
+  reqFIFO.enq.valid := (fifoNotFull && io.missCache.valid && !refilling) ||
+    (refilling && fifoNotEmpty && !refillReg.isLeafMpte && !refillReg.isAf) ||
+    (refilling && fifoNotEmpty && !hitFIFO && (refillReg.isLeafMpte || refillReg.isAf))
+  // Entries are enqueued under three conditions: 1. Normal miss cache request; 2. Refill of a non-leaf MPTE;
+  // 3. Refill of a leaf MPTE or access fault, provided it is not a hit.
+  reqFIFO.enq.bits := Mux(refilling, reqAltInput, io.missCache.bits)
+
+  reqFIFO.deq.ready := (fifoNotEmpty && refilling)
+
+  reqAltInput := reqFIFO.deq.bits
+
+  when(hitFIFO && refilling) {
+    reqAltInput.hitLevel := refillReg.level
+    reqAltInput.hitAddr  := refillReg.refillData.getPPN
+  }
+
+  val retPermOffset = Mux1H(Seq( // different level use different offset vpnnLen
+    refillReg.level(3) -> reqFIFO.deq.bits.pa(mptOff - offLen - 1 + vpnnLen * 3, 0 + vpnnLen * 3), // 30:27
+    refillReg.level(2) -> reqFIFO.deq.bits.pa(mptOff - offLen - 1 + vpnnLen * 2, 0 + vpnnLen * 2), // 21:18
+    refillReg.level(1) -> reqFIFO.deq.bits.pa(mptOff - offLen - 1 + vpnnLen, 0 + vpnnLen),         // 12:9
+    refillReg.level(0) -> reqFIFO.deq.bits.pa(mptOff - offLen - 1, 0)
+  ) // 3:0
+  )
+  io.resp.bits.perm := refillReg.refillData.extractPerm(retPermOffset)
+  // extractPerm is a function in MptData, extract perm from refillData, offset is the offset of the perm in refillData.data
+  io.resp.valid := fifoNotEmpty && hitFIFO && refilling && (refillReg.isAf || refillReg.isLeafMpte)
+  // refillCounter.orR means we are refilling, FIFO not empty means we have a request to respond
+  io.resp.bits.accessFault := refillReg.isAf || ((!io.resp.bits.perm(0)) && io.resp.bits.perm(1))
+  // not read but write is af // access fault, no need to refill, just return the fault
+  io.resp.bits.mptLevel := OHToUInt(refillReg.level) // mptLevelLenOH.W
+  io.resp.bits.reqPA := reqFIFO.deq.bits.pa // reqPA is the PA of the request, used to generate the refill address
+  io.resp.bits.source  := reqFIFO.deq.bits.source // source is the source
+  io.resp.bits.mptOnly := reqFIFO.deq.bits.mptOnly && io.resp.valid
+
+  val permsAsVec = Wire(Vec(16, UInt(3.W))) // perm xwr bits, total 16 xwrs in one mpte
+  for (i <- 0 until 16) { permsAsVec(i) := refillReg.refillData.data(2 + i * 3, i * 3) }
+  val permsEqual = Wire(Vec(16 - 1, Bool()))
+  for (i <- 0 until 16 - 1) { permsEqual(i) := permsAsVec(i + 1) === permsAsVec(i) } // delay 1XNOR + 2 and gates = 3
+  val leftPermsAllEqual  = permsEqual.slice(0, 7).reduce(_ && _)  // = permsEqual(6,0), delay 3
+  val rightPermsAllEqual = permsEqual.slice(8, 15).reduce(_ && _) // = permsEqual(14,8)
+  // OH (refillReg.level === 1.U(4.W)) is (refillReg.level(0))
+  io.resp.bits.permIsNAPOT := (refillReg.level(0)) && leftPermsAllEqual && rightPermsAllEqual && permsEqual(
+    7
+  ) // Delay 2
+  io.resp.bits.PermTlbCompress := (refillReg.level(0)) && Mux(
+    reqFIFO.deq.bits.pa(mptOff - offLen - 1, 0) < 8.U,
+    leftPermsAllEqual,
+    rightPermsAllEqual
+  ) // delay 3
+
+  io.twReq.bits.hitAddr  := reqFIFO.deq.bits.hitAddr
+  io.twReq.bits.reqPA    := reqFIFO.deq.bits.pa(ppnLen - 1, mptOff - offLen)
+  io.twReq.bits.hitLevel := reqFIFO.deq.bits.hitLevel
+  io.twReq.valid         := fifoNotEmpty && !refilling && io.twReq.ready
+
+  reqFIFO.flush.get := flush
+  when(flush) {
+    refillReg := 0.U.asTypeOf(new RefillBundle())
+    refillCounter := 0.U(4.W)
+  }
+}
+
+class MptTableWalkerIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCacheParam {
+  val req = Flipped(DecoupledIO(new MptMissQueueToTWReqBundle()))
+
+  // val resp = DecoupledIO(new TWtoMptMissQueueRespBundle())
+
+  val mem = new Bundle {
+    val req  = DecoupledIO(new Bundle { val addr = UInt(PAddrBits.W) })
+    val resp = Flipped(ValidIO(UInt(XLEN.W)))
+    // val mask = Input(Bool()) dont need？
+  }
+
+  val pmp = new Bundle {
+    val req  = ValidIO(new PMPReqBundle())
+    val resp = Flipped(new PMPRespBundle())
+  }
+  val refill = ValidIO(new RefillBundle())
+}
+
+class MPTTableWalker(implicit p: Parameters) extends XSModule with MPTCacheParam {
+  val io  = IO(new MptTableWalkerIO)
+  val mem = io.mem
+
+  io.pmp.req.bits.size := 3.U
+  io.pmp.req.bits.cmd  := TlbCmd.read
+
+  val flush = io.sfence.valid || io.csr.mmpt.changed
+
+  val pa = RegEnable(io.req.bits.reqPA, 0.U, io.req.fire)
+  // Store the received request PA in a register, used to generate the PN1/2/3 offsets for synthesizing the table walk address.
+  io.refill.bits.pa := pa
+  // define level reg
+  val setLevel          = Wire(Bool())
+  val setPmpCheckLevel  = Wire(Bool())
+  val nextLevel         = Wire(UInt(mptLevelLenOH.W))
+  val nextPmpCheckLevel = Wire(UInt(mptLevelLenOH.W))
+
+  val level         = RegEnable(nextLevel, "b1000".U(mptLevelLenOH.W), setLevel)
+  val pmpCheckLevel = RegEnable(nextPmpCheckLevel, "b1000".U(mptLevelLenOH.W), setPmpCheckLevel)
+
+  val mpteResp = Wire(new MptEntry())
+  if (HasMptCheckDefault) {
+    mpteResp.genFake(level)
+  } else {
+    mpteResp.apply(mem.resp.bits) // mem mpte mpteData
+  }
+
+  val mpteData = Reg(new MptData())
+  // Stores the returned permissions/lower-level address, or the incoming request address entry;
+  // used to return permissions or synthesize the lower-level page table address with PA.
+  io.refill.bits.refillData := mpteData // output alloc
+  val isLeafMpte = RegEnable(mpteResp.isLeaf, false.B, io.mem.resp.valid)
+  io.refill.bits.isLeafMpte := isLeafMpte // tell cache if the current refill is leaf node
+  val mpteInvalid = RegEnable(!mpteResp.isValid, false.B, io.mem.resp.valid) // 1 level not on top of mem.resp
+  val rsvZeroError0 = RegEnable(mem.resp.bits(9, 2).orR, false.B, io.mem.resp.valid)
+  // max 3 level or gate on top of mem.resp，NON ZERO error of mtpe
+  val rsvZeroError1 = RegEnable(mem.resp.bits(62, 58).orR, false.B, io.mem.resp.valid)
+  val rsvZeroError2 = false.B
+  when(io.req.fire) {
+    mpteData.apply(io.req.bits.hitAddr)
+  }.elsewhen(io.mem.resp.valid) {
+    mpteData := mpteResp.data
+  }
+
+  val pn = Wire(UInt(9.W))
+  pn := Mux1H(Seq(
+    level(3) -> Cat(0.U(4.W), pa(47 - mptOff, 43 - mptOff)),
+    level(2) -> pa(42 - mptOff, 34 - mptOff),
+    level(1) -> pa(33 - mptOff, 25 - mptOff),
+    level(0) -> pa(24 - mptOff, 16 - mptOff)
+  ))
+
+  // 3 layers of gate logic select the coresponding PN[i] based on cur level, just a onehot mux
+  val memAddr = mpteData.getAddr(pn) // gen addr 0 delay
+  io.mem.req.bits.addr := memAddr
+  io.pmp.req.valid := DontCare
+  io.pmp.req.bits.addr := Mux(io.mem.resp.valid, mpteResp.getAddr(pn), memAddr)
+  // should be safer than just := memAddr
+
+  // accessFault logic
+  val pmpFail = if (HasMptCheckDefault) false.B else (!isLeafMpte) && (io.pmp.resp.ld || io.pmp.resp.mmio)
+  // PMP delay unknown
+  val entryError = if (HasMptCheckDefault) false.B else
+    mpteInvalid || rsvZeroError0 || rsvZeroError1 || rsvZeroError2 || ((!isLeafMpte) && level === 1.U)
+  // level == 0 non leaf, zero = / = 0,pmp fail,invalid casue accessFault
+  val accessFault = entryError || pmpFail // pmp fail also cause accessFault
+  io.refill.bits.level := Mux(pmpFail, pmpCheckLevel, level) // pmpFail return next level,else cur level
+  io.refill.bits.isAf  := accessFault
+  // pmp fail will not be recorded(if the root addr+ pn[i] cause pmp fail does not necessarily mean that
+  // the root addr + other offset will cause pmp fail, so entry will be refilled as a normal intermidiate node)
+
+  // // // FSM // // //
+  object mystate extends ChiselEnum {
+    val s_idle, s_mem_req, s_mem_resp, s_addr_proc = Value
+  }
+  import mystate._
+  val curState  = RegInit(s_idle)
+  val nextState = WireDefault(s_idle)
+  curState := nextState // 2 proc FSM, no need to specify the flush, it is global
+  // fsm start
+  mem.req.valid     := false.B
+  io.req.ready      := false.B
+  nextState         := curState
+  setLevel          := false.B
+  setPmpCheckLevel  := false.B
+  nextLevel         := level >> 1.U // onehotcounter-1 no aflevel
+  nextPmpCheckLevel := pmpCheckLevel >> 1.U
+  io.refill.valid   := false.B
+  // default val
+  switch(curState) {
+    is(s_idle) {
+      io.req.ready := true.B
+      when(io.req.fire) {
+        setPmpCheckLevel  := true.B
+        nextLevel         := io.req.bits.hitLevel
+        nextPmpCheckLevel := io.req.bits.hitLevel
+        nextState         := s_mem_req // to mem req if fire
+        setLevel          := true.B
+      }
+    }
+    is(s_mem_req) {
+      mem.req.valid := true.B // req valid when not fire
+
+      when(io.mem.req.fire) { // just waiting, timing safe
+        nextState := s_mem_resp // to wait resp
+      }.otherwise {}
+    }
+    is(s_mem_resp) {             // unknown in delay,timing?
+      when(io.mem.resp.valid) { // do nothing,delay one cycle OPTPOINT*
+        nextState        := s_addr_proc
+        setPmpCheckLevel := true.B
+      }
+    }
+    is(s_addr_proc) { // known delay
+      // process return
+      when(accessFault || isLeafMpte) { // out delay unknown
+        // when(isLeafMpte) {io.refill.valid := true.B}
+        io.refill.valid := true.B
+        nextState       := s_idle // OPTPOINT*
+      }.otherwise {
+        io.refill.valid := true.B   // start refill
+        setLevel        := true.B
+        nextState       := s_mem_req // OPTPOINT*
+      }
+    }
+
+  }
+  when(flush){
+    pa := 0.U
+    level := "b1000".U(mptLevelLenOH.W)
+    pmpCheckLevel := "b1000".U(mptLevelLenOH.W)
+    isLeafMpte := false.B
+    mpteInvalid := false.B
+    rsvZeroError0 := false.B
+    rsvZeroError1 := false.B
+    curState := s_idle
+  }
+  // fsm end
+}
+
+class MptCheckerIO(implicit p: Parameters) extends MMUIOBaseBundle with HasPtwConst {
+  val mem = new Bundle {
+    val req  = DecoupledIO(new L2TlbMemReqBundle())
+    val resp = Flipped(ValidIO(UInt(XLEN.W)))
+    val mask = Input(Bool()) // mask bit
+  }
+  val req  = Flipped(DecoupledIO(new MptReqBundle()))
+  val resp = ValidIO(new MptRespBundle())
+
+  val pmp = new Bundle {
+    val req  = ValidIO(new PMPReqBundle())
+    val resp = Flipped(new PMPRespBundle())
+  }
+}
+
+class MptChecker(implicit p: Parameters) extends XSModule with HasPtwConst {
+  val io = IO(new MptCheckerIO)
+  io.mem.req.bits.hptw_bypassed := true.B // never refill to page cache
+  io.mem.req.bits.id            := mptcMemReqID.U(bMemID.W)
+  val mptCacheInst     = Module(new MPTCache()).io
+  val mptTWInst        = Module(new MPTTableWalker()).io
+  val mptMissQueueInst = Module(new MptMissQueue()).io
+
+  mptCacheInst.csr <> io.csr
+  mptCacheInst.sfence <> io.sfence
+
+  mptTWInst.csr <> io.csr
+  mptTWInst.sfence <> io.sfence
+
+  mptMissQueueInst.csr <> io.csr
+  mptMissQueueInst.sfence <> io.sfence
+
+  mptCacheInst.req.bits.mptOnly := io.req.bits.mptOnly // need some fix
+  mptCacheInst.req.bits.reqPA   := io.req.bits.reqPA
+  mptCacheInst.req.bits.source  := io.req.bits.id
+  mptCacheInst.req.valid        := io.req.valid
+  io.req.ready                  := mptCacheInst.req.ready
+
+  val mptReturn = Wire(new MptRespBundle())
+  mptReturn.mptPerm := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.perm, mptCacheInst.respHit.bits.perm)
+  mptReturn.contigousPerm := Mux(
+    mptMissQueueInst.resp.valid,
+    mptMissQueueInst.resp.bits.PermTlbCompress,
+    mptCacheInst.respHit.bits.tlbContigousPerm
+  )
+  mptReturn.id := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.source, mptCacheInst.respHit.bits.source)
+  mptReturn.mptLevel := Mux(
+    mptMissQueueInst.resp.valid,
+    mptMissQueueInst.resp.bits.mptLevel,
+    mptCacheInst.respHit.bits.mptLevel
+  )
+  mptReturn.mptOnly := Mux(
+    mptMissQueueInst.resp.valid,
+    mptMissQueueInst.resp.bits.mptOnly,
+    mptCacheInst.respHit.bits.mptOnly
+  )
+  mptReturn.reqPA := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.reqPA, mptCacheInst.respHit.bits.reqPA)
+  mptReturn.accessFault := Mux(
+    mptMissQueueInst.resp.valid,
+    mptMissQueueInst.resp.bits.accessFault,
+    mptCacheInst.respHit.bits.accessFault
+  )
+  mptReturn.permIsNAPOT := Mux(
+    mptMissQueueInst.resp.valid,
+    mptMissQueueInst.resp.bits.permIsNAPOT,
+    mptCacheInst.respHit.bits.permIsNAPOT
+  )
+
+  io.resp.valid := mptMissQueueInst.resp.valid || mptCacheInst.respHit.valid
+  io.resp.bits <> mptReturn
+
+  mptMissQueueInst.refill <> mptTWInst.refill
+  // cache miss send MptMissQueue
+  mptMissQueueInst.missCache.bits.mptOnly  := mptCacheInst.respMiss.bits.mptOnly
+  mptMissQueueInst.missCache.bits.hitLevel := mptCacheInst.respMiss.bits.hitLevel
+  mptMissQueueInst.missCache.bits.hitAddr  := mptCacheInst.respMiss.bits.ppn
+  mptMissQueueInst.missCache.bits.source   := mptCacheInst.respMiss.bits.source
+  mptMissQueueInst.missCache.bits.pa       := mptCacheInst.respMiss.bits.pa
+  mptMissQueueInst.missCache.valid         := mptCacheInst.respMiss.valid
+  mptCacheInst.respMiss.ready              := mptMissQueueInst.missCache.ready
+  // cache refill io
+  mptCacheInst.refill <> mptTWInst.refill
+  // MptMissQueue-twio
+  mptTWInst.req <> mptMissQueueInst.twReq
+  // tw-MptMissQueueio
+  // table walk read ram
+  io.mem.req.bits.addr    := mptTWInst.mem.req.bits.addr
+  io.mem.req.valid        := mptTWInst.mem.req.valid
+  mptTWInst.mem.req.ready := io.mem.req.ready
+
+  mptTWInst.mem.resp.bits  := io.mem.resp.bits
+  mptTWInst.mem.resp.valid := io.mem.resp.valid
+  // mptTWInst.mem.resp.ready := true.B
+  // PMP IO
+  mptTWInst.pmp.resp <> io.pmp.resp
+  io.pmp.req <> mptTWInst.pmp.req
+
+  val mptDiabledFakedRespValid = RegInit(false.B)
+  val mptDiabledFakedMptOnly   = RegInit(false.B)
+  when(io.csr.mmpt.mode === 0.U) {
+    mptCacheInst.req.valid := false.B // mptmode  return 111
+
+    io.req.ready := true.B
+    when(io.req.fire) {
+      mptDiabledFakedRespValid := true.B
+      mptDiabledFakedMptOnly   := io.req.bits.mptOnly
+    }
+    io.resp.valid        := mptDiabledFakedRespValid
+    io.resp.bits.mptOnly := mptDiabledFakedMptOnly
+    when(io.resp.fire) {
+      mptDiabledFakedRespValid := false.B
+      mptDiabledFakedMptOnly   := false.B
+    }
+  }
+}
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 997ed646f25..31245fb40e1 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -94,6 +94,11 @@ class PTWIO()(implicit p: Parameters) extends MMUIOBaseBundle with HasPtwConst {
       val req = DecoupledIO(new bitmapReqBundle())
       val resp = Flipped(DecoupledIO(new bitmapRespBundle()))
   })
+
+  val mptCheck = Option.when(HasMptCheck)(new Bundle { //mpt interface
+      val req = DecoupledIO(new MptReqBundle())
+      val resp = Flipped(ValidIO(new MptRespBundle()))
+  })
 }
 
 class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPerfEvents {
@@ -114,10 +119,14 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val satp = Mux(enableS2xlate, io.csr.vsatp, io.csr.satp)
   val s1Pbmte = Mux(req_s2xlate =/= noS2xlate, io.csr.hPBMTE, io.csr.mPBMTE)
 
+  val mptEn = if (HasMptCheck) (io.csr.mmpt.mode =/= 0.U) else false.B // enable mpt when mpt mode is not bare
+  val checkIntermediateNode = if (HasMptCheck) (io.csr.mmpt.optOutInNode === 0.U) else false.B
+
   val current_mode = current_satp.mode
   val mode = satp.mode
   val hgatp = io.csr.hgatp
-  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed
+  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B)
+  // changes in mpt flush all
   val s2xlate = enableS2xlate && !onlyS1xlate
   val level = RegInit(3.U(log2Up(Level + 1).W))
   val af_level = RegInit(3.U(log2Up(Level + 1).W)) // access fault return this level
@@ -144,6 +153,14 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   // for updating "level"
   val mem_addr_update = RegInit(false.B)
 
+  val s_mpt_check = RegInit(false.B)//new fsm state for mpt
+  val w_mpt_resp = RegInit(false.B)
+  val mpt_af = if (HasMptCheck)
+    RegEnable(!io.mptCheck.get.resp.bits.mptPerm(0) || io.mptCheck.get.resp.bits.accessFault,
+      false.B, io.mptCheck.get.resp.valid)
+  else
+    false.B
+
   val s_bitmap_check = RegInit(true.B)
   val w_bitmap_resp = RegInit(true.B)
   val whether_need_bitmap_check = RegInit(false.B)
@@ -180,6 +197,8 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val sent_to_pmp = idle === false.B && (s_pmp_check === false.B || mem_addr_update) && !finish && !vs_finish && !first_gvpn_check_fail && !(find_pte && pte_valid)
   val accessFault = RegEnable(io.pmp.resp.ld || io.pmp.resp.mmio, false.B, sent_to_pmp)
 
+  val accessFaultMpt = if (HasMptCheck) (accessFault || mpt_af) else accessFault
+
   val l3addr = Wire(UInt(ptePaddrLen.W))
   val l2addr = Wire(UInt(ptePaddrLen.W))
   val l1addr = Wire(UInt(ptePaddrLen.W))
@@ -245,14 +264,14 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   // pageFault is always valid when pte_valid
   val resp_pf = pte_valid && pageFault
   // when (pte_valid && (pageFault || guestFault), should not report accessFault or ppn_af
-  val resp_af = (accessFault || ppn_af) && !((pte_valid && pageFault) || guestFault)
+  val resp_af = (accessFaultMpt || ppn_af) && !((pte_valid && pageFault) || guestFault)
   // should use af_level when accessFault && !((pte_valid && pageFault) || guestFault)
-  val resp_level = Mux(accessFault && resp_af, af_level, Mux(guestFault, gpf_level, level))
+  val resp_level = Mux(accessFaultMpt && resp_af, af_level, Mux(guestFault, gpf_level, level))
   // when ptw do not really send a memory request, should use fake_pte
   val resp_pte = Mux(pte_valid, pte, fake_pte)
   ptw_resp.apply(resp_pf, resp_af, resp_level, resp_pte, vpn, satp.asid, hgatp.vmid, vpn(sectortlbwidth - 1, 0), not_super = false, not_merge = false, bitmap_checkfailed.asBool)
 
-  val normal_resp = mem_addr_update && !need_last_s2xlate && (guestFault || (w_mem_resp && find_pte) || (s_pmp_check && accessFault) || onlyS2xlate )
+  val normal_resp = mem_addr_update && !need_last_s2xlate && (guestFault || (w_mem_resp && find_pte) || (s_pmp_check && accessFault) || onlyS2xlate || (if (HasMptCheck) (mpt_af) else false.B))
   val stageHit_resp = hptw_resp_stage2
   io.resp.valid := !idle && Mux(stage1Hit, stageHit_resp, normal_resp)
   io.resp.bits.source := source
@@ -260,10 +279,13 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   io.resp.bits.h_resp := Mux(gvpn_gpf, fake_h_resp, hptw_resp)
   io.resp.bits.s2xlate := req_s2xlate
 
-  io.llptw.valid := s_llptw_req === false.B && to_find_pte && !accessFault && !guestFault
+  io.llptw.valid := s_llptw_req === false.B && to_find_pte && !accessFaultMpt && !guestFault
   io.llptw.bits.req_info.source := source
   io.llptw.bits.req_info.vpn := vpn
   io.llptw.bits.req_info.s2xlate := req_s2xlate
+
+  if (HasMptCheck) {io.llptw.bits.req_info.mptOnly.get := DontCare}
+
   io.llptw.bits.ppn := DontCare
   if (HasBitmapCheck) {
     io.llptw.bits.bitmapCheck.get.jmp_bitmap_check := DontCare
@@ -272,6 +294,13 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     io.llptw.bits.bitmapCheck.get.hitway := DontCare
   }
 
+  if (HasMptCheck) {
+    io.mptCheck.get.req.valid := s_mpt_check && !accessFault && s_pmp_check
+    io.mptCheck.get.req.bits.mptOnly := DontCare // mpt during tw is never mpt only
+    io.mptCheck.get.req.bits.reqPA := Mux(s2xlate, hpaddr(PAddrBits-1, offLen), mem_addr(PAddrBits-1, offLen))
+    io.mptCheck.get.req.bits.id := FsmReqID.U(bMemID.W)
+  }
+
   io.pmp.req.valid := DontCare // samecycle, do not use valid
   io.pmp.req.bits.addr := Mux(s2xlate, hpaddr, mem_addr)
   io.pmp.req.bits.size := 3.U // TODO: fix it
@@ -290,7 +319,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     io.bitmap.get.req.bits.s2xlate := req_s2xlate
     io.bitmap.get.resp.ready := !w_bitmap_resp
   }
-  mem.req.valid := s_mem_req === false.B && !mem.mask && !accessFault && s_pmp_check
+  mem.req.valid := s_mem_req === false.B && !mem.mask && !accessFaultMpt && s_pmp_check
   mem.req.bits.addr := Mux(s2xlate, hpaddr, mem_addr)
   mem.req.bits.id := FsmReqID.U(bMemID.W)
   mem.req.bits.hptw_bypassed := false.B
@@ -299,6 +328,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   io.refill.req_info.vpn := vpn
   io.refill.level := level
   io.refill.req_info.source := source
+  if (HasMptCheck) { io.refill.req_info.mptOnly.get := DontCare }
 
   io.hptw.req.valid := !s_hptw_req || !s_last_hptw_req
   io.hptw.req.bits.id := FsmReqID.U(bMemID.W)
@@ -331,7 +361,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     full_gvpn_reg := io.req.bits.stage1.genPPN()
   }
 
-  when (io.resp.fire && stage1Hit){
+  when (io.resp.fire && stage1Hit) {
     idle := true.B
   }
 
@@ -365,27 +395,28 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     vpn := io.req.bits.req_info.vpn
     l2Hit := req.l2Hit
     accessFault := false.B
+    if (HasMptCheck) { mpt_af := false.B }
     idle := false.B
     hptw_pageFault := false.B
     hptw_accessFault := false.B
     pte_valid := false.B
     req_s2xlate := io.req.bits.req_info.s2xlate
-    when(io.req.bits.req_info.s2xlate === onlyStage2){
+    when(io.req.bits.req_info.s2xlate === onlyStage2) {
       full_gvpn_reg := io.req.bits.req_info.vpn
       val onlys2_gpaddr = Cat(io.req.bits.req_info.vpn, 0.U(offLen.W)) // is 50 bits, don't need to check high bits when sv48x4 is enabled
       val check_gpa_high_fail = Mux(io.req.bits.req_info.s2xlate === onlyStage2 && io.csr.hgatp.mode === Sv39x4, onlys2_gpaddr(onlys2_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, false.B)
       need_last_s2xlate := false.B
-      when(check_gpa_high_fail){
+      when(check_gpa_high_fail) {
         mem_addr_update := true.B
         first_gvpn_check_fail := true.B
       }.otherwise{
         s_last_hptw_req := false.B
       }
-    }.elsewhen(io.req.bits.req_info.s2xlate === allStage){
+    }.elsewhen(io.req.bits.req_info.s2xlate === allStage) {
       full_gvpn_reg := 0.U
       val allstage_gpaddr = Cat(gvpn_wire, 0.U(offLen.W))
       val check_gpa_high_fail = Mux(io.csr.hgatp.mode === Sv39x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(io.csr.hgatp.mode === Sv48x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
-      when(check_gpa_high_fail){
+      when(check_gpa_high_fail) {
         mem_addr_update := true.B
         first_gvpn_check_fail := true.B
       }.otherwise{
@@ -399,7 +430,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     }
   }
 
-  when(io.hptw.req.fire && s_hptw_req === false.B){
+  when(io.hptw.req.fire && s_hptw_req === false.B) {
     s_hptw_req := true.B
     w_hptw_resp := false.B
   }
@@ -424,13 +455,13 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     s_last_hptw_req := true.B
   }
 
-  when (io.hptw.resp.fire && w_last_hptw_resp === false.B && stage1Hit){
+  when (io.hptw.resp.fire && w_last_hptw_resp === false.B && stage1Hit) {
     w_last_hptw_resp := true.B
     hptw_resp_stage2 := true.B
     hptw_resp := io.hptw.resp.bits.h_resp
   }
 
-  when(io.hptw.resp.fire && w_last_hptw_resp === false.B && !stage1Hit){
+  when(io.hptw.resp.fire && w_last_hptw_resp === false.B && !stage1Hit) {
     hptw_pageFault := io.hptw.resp.bits.h_resp.gpf
     hptw_accessFault := io.hptw.resp.bits.h_resp.gaf
     hptw_resp := io.hptw.resp.bits.h_resp
@@ -438,12 +469,36 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     mem_addr_update := true.B
   }
 
-  when(sent_to_pmp && mem_addr_update === false.B){
-    s_mem_req := false.B
-    s_pmp_check := true.B
+  when(sent_to_pmp && mem_addr_update === false.B) {
+    if (HasMptCheck) {
+      when(mptEn && checkIntermediateNode) {
+        s_mpt_check := true.B
+      }.otherwise {
+        s_mem_req := false.B
+      }
+    } else {
+      s_mem_req := false.B
+    } // send to mpt /send to mem,  when pmp pass)
+      s_pmp_check := true.B
+  }
+
+  //add mpt
+  if (HasMptCheck) {
+    when(s_mpt_check && !accessFault) {
+      when(io.mptCheck.get.req.fire) {
+        s_mpt_check := false.B
+        w_mpt_resp := true.B
+      }
+    }
+    when(w_mpt_resp) {
+      when(io.mptCheck.get.resp.valid) {
+        w_mpt_resp := false.B
+        s_mem_req := false.B // send to mem when pmp && mpt pass
+      }
+    }
   }
 
-  when(accessFault && idle === false.B){
+  when(accessFaultMpt && idle === false.B) {
     s_pmp_check := true.B
     s_mem_req := true.B
     w_mem_resp := true.B
@@ -454,6 +509,12 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     w_last_hptw_resp := true.B
     mem_addr_update := true.B
     need_last_s2xlate := false.B
+
+    if (HasMptCheck) { //reset s_mpt_check
+      s_mpt_check := false.B
+      w_mpt_resp := false.B
+    }
+
     if (HasBitmapCheck) {
       s_bitmap_check := true.B
       w_bitmap_resp := true.B
@@ -462,7 +523,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     }
   }
 
-  when(guestFault && idle === false.B){
+  when(guestFault && idle === false.B) {
     s_pmp_check := true.B
     s_mem_req := true.B
     w_mem_resp := true.B
@@ -473,6 +534,12 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     w_last_hptw_resp := true.B
     mem_addr_update := true.B
     need_last_s2xlate := false.B
+
+    if (HasMptCheck) { //reset s_mpt_check
+      s_mpt_check := false.B
+      w_mpt_resp := false.B
+    }
+
     if (HasBitmapCheck) {
       s_bitmap_check := true.B
       w_bitmap_resp := true.B
@@ -481,12 +548,12 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     }
   }
 
-  when (mem.req.fire){
+  when (mem.req.fire) {
     s_mem_req := true.B
     w_mem_resp := false.B
   }
 
-  when(mem.resp.fire && w_mem_resp === false.B){
+  when(mem.resp.fire && w_mem_resp === false.B) {
     w_mem_resp := true.B
     af_level := af_level - 1.U
     gpf_level := Mux(mode === Sv39 && !pte_valid && !l2Hit, gpf_level - 2.U, gpf_level - 1.U)
@@ -534,18 +601,22 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     }
   }
 
-  when(mem_addr_update){
-    when(level >= 2.U && !onlyS2xlate && !(guestFault || find_pte || accessFault)) {
+  when(mem_addr_update) {
+    when(level >= 2.U && !onlyS2xlate && !(guestFault || find_pte || accessFaultMpt)) {
       level := levelNext
-      when(s2xlate){
+      when(s2xlate) {
         s_hptw_req := false.B
         vs_finish := true.B
       }.otherwise{
-        s_mem_req := false.B
+        if (HasMptCheck) {
+          s_mpt_check := true.B // mem_addr_update checks pmp, next is mpt or mem access state
+        }else{
+          s_mem_req := false.B
+        } // that is not intended for mpt
       }
       s_llptw_req := true.B
       mem_addr_update := false.B
-    }.elsewhen(io.llptw.valid){
+    }.elsewhen(io.llptw.valid) {
       when(io.llptw.fire) {
         idle := true.B
         s_llptw_req := true.B
@@ -555,11 +626,11 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
       finish := true.B
     }.elsewhen(s2xlate && need_last_s2xlate === true.B) {
       need_last_s2xlate := false.B
-      when(!(guestFault || accessFault || pageFault || ppn_af)){
+      when(!(guestFault || accessFaultMpt || pageFault || ppn_af)) {
         s_last_hptw_req := false.B
         mem_addr_update := false.B
       }
-    }.elsewhen(io.resp.valid){
+    }.elsewhen(io.resp.valid) {
       when(io.resp.fire) {
         idle := true.B
         s_llptw_req := true.B
@@ -569,6 +640,9 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         if (HasBitmapCheck) {
           bitmap_checkfailed := false.B
         }
+        if (HasMptCheck) {
+          mpt_af := false.B
+        }
       }
       finish := true.B
     }
@@ -588,6 +662,11 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     w_hptw_resp := true.B
     s_last_hptw_req := true.B
     w_last_hptw_resp := true.B
+    if (HasMptCheck) {
+      mpt_af := false.B
+      s_mpt_check := false.B
+      w_mpt_resp := false.B
+    }
     if (HasBitmapCheck) {
       s_bitmap_check := true.B
       w_bitmap_resp := true.B
@@ -689,6 +768,11 @@ class LLPTWIO(implicit p: Parameters) extends MMUIOBaseBundle with HasPtwConst {
   })
 
   val l0_way_info = Option.when(HasBitmapCheck)(Input(UInt(l2tlbParams.l0nWays.W)))
+
+  val mptCheck = Option.when(HasMptCheck)(new Bundle {
+      val req = DecoupledIO(new MptReqBundle())
+      val resp = Flipped(ValidIO(new MptRespBundle()))
+  })
 }
 
 class LLPTWEntry(implicit p: Parameters) extends XSBundle with HasPtwConst {
@@ -715,11 +799,13 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val mbmc = io.csr.mbmc
   val bitmap_enable = (if (HasBitmapCheck) true.B else false.B) && mbmc.BME === 1.U && mbmc.CMODE === 0.U
 
-  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed
+  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B)
   val entries = RegInit(VecInit(Seq.fill(l2tlbParams.llptwsize)(0.U.asTypeOf(new LLPTWEntry()))))
-  val state_idle :: state_hptw_req :: state_hptw_resp :: state_addr_check :: state_mem_req :: state_mem_waiting :: state_mem_out :: state_last_hptw_req :: state_last_hptw_resp :: state_cache :: state_bitmap_check :: state_bitmap_resp :: Nil = Enum(12)
+  val state_idle :: state_hptw_req :: state_hptw_resp :: state_addr_check :: state_mem_req :: state_mem_waiting :: state_mem_out :: state_last_hptw_req :: state_last_hptw_resp :: state_cache :: state_bitmap_check :: state_bitmap_resp :: stage_mpt_req ::stage_mpt_resp :: Nil = Enum(14)
   val state = RegInit(VecInit(Seq.fill(l2tlbParams.llptwsize)(state_idle)))
 
+  val mptEn = if (HasMptCheck) (io.csr.mmpt.mode =/= 0.U) else false.B
+  val checkIntermediateNode = if (HasMptCheck) (io.csr.mmpt.optOutInNode === 0.U) else false.B
   val is_emptys = state.map(_ === state_idle)
   val is_mems = state.map(_ === state_mem_req)
   val is_waiting = state.map(_ === state_mem_waiting)
@@ -732,16 +818,33 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val is_bitmap_req = state.map(_ === state_bitmap_check)
   val is_bitmap_resp = state.map(_ === state_bitmap_resp)
 
+  val is_stage_mpt_req  = state.map(_ === stage_mpt_req)
+  val is_stage_mpt_resp = state.map(_ === stage_mpt_resp)
+
   val full = !ParallelOR(is_emptys).asBool
   val enq_ptr = ParallelPriorityEncoder(is_emptys)
 
-  val mem_ptr = ParallelPriorityEncoder(is_having) // TODO: optimize timing, bad: entries -> ptr -> entry
+  val mem_ptr = if (HasMptCheck) {
+    RegInit(0.U.asTypeOf(ParallelPriorityEncoder(is_having)))
+  }else{
+    ParallelPriorityEncoder(is_having)
+  }// TODO: optimize timing, bad: entries -> ptr -> entry
+
   val mem_arb = Module(new RRArbiterInit(new LLPTWEntry(), l2tlbParams.llptwsize))
   for (i <- 0 until l2tlbParams.llptwsize) {
     mem_arb.io.in(i).bits := entries(i)
     mem_arb.io.in(i).valid := is_mems(i) && !io.mem.req_mask(i)
   }
 
+  val mpt_arb = Option.when(HasMptCheck) (Module(new Arbiter(new LLPTWEntry(), l2tlbParams.llptwsize)))//switch mpt input,arb
+
+  if (HasMptCheck) {
+    for (i <- 0 until l2tlbParams.llptwsize) { //arb 6
+      mpt_arb.get.io.in(i).bits := entries(i)
+      mpt_arb.get.io.in(i).valid := is_stage_mpt_req(i)
+    }
+  }
+
   // process hptw requests in serial
   val hyper_arb1 = Module(new RRArbiterInit(new LLPTWEntry(), l2tlbParams.llptwsize))
   for (i <- 0 until l2tlbParams.llptwsize) {
@@ -784,10 +887,23 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val dup_vec_having = dup_vec.zipWithIndex.map{case (d, i) => d && is_having(i)} // dup with the "mem_out" entry recv the data just now
   val dup_vec_bitmap = dup_vec.zipWithIndex.map{case (d, i) => d && (is_bitmap_req(i) || is_bitmap_resp(i))}
   val dup_vec_last_hptw = dup_vec.zipWithIndex.map{case (d, i) => d && (is_last_hptw_req(i) || is_last_hptw_resp(i))}
+
+  val dup_vec_mpt = Option.when(HasMptCheck) (dup_vec.zipWithIndex.map{case(d, i) => d && (is_stage_mpt_req(i) || is_stage_mpt_resp(i))})
+  // input is dup of mpt entry (is_stage_mpt_req(i) || is_stage_mpt_resp(i))
+  val dup_mpt_resp = if (HasMptCheck) (io.mptCheck.get.resp.valid && VecInit(dup_vec)(io.mptCheck.get.resp.bits.id)) else false.B
+  val dup_mpt_mem_req = Option.when(HasMptCheck) (dup_vec.zipWithIndex.map{case (d, i) => d && is_mems(i)})
+
+  val accessFaultMpt = if (HasMptCheck) (!(io.mptCheck.get.resp.bits.mptPerm(0)) || io.mptCheck.get.resp.bits.accessFault) else false.B
+
   val wait_id = Mux(dup_req_fire, mem_arb.io.chosen, ParallelMux(dup_vec_wait zip entries.map(_.wait_id)))
+  val wait_mpt_id = if (HasMptCheck) ParallelMux(dup_vec_mpt.get zip entries.map(_.wait_id)) else 0.U
+  val wait_mpt_mem_id = if (HasMptCheck) ParallelMux(dup_mpt_mem_req.get zip entries.map(_.wait_id)) else 0.U
   val dup_wait_resp = io.mem.resp.fire && VecInit(dup_vec_wait)(io.mem.resp.bits.id) && !io.mem.flush_latch(io.mem.resp.bits.id) // dup with the entry that data coming next cycle
   val to_wait = Cat(dup_vec_wait).orR || dup_req_fire
 
+  val to_mpt = if (HasMptCheck) Cat(dup_vec_mpt.get).orR else false.B
+  val to_mpt_mem_req = if (HasMptCheck) (Cat(dup_mpt_mem_req.get).orR) && !dup_req_fire else false.B
+
   val last_hptw_req_id = io.mem.resp.bits.id
   val req_paddr = MakeAddr(io.in.bits.ppn(ppnLen-1, 0), getVpnn(io.in.bits.req_info.vpn, 0))
   val req_hpaddr = MakeAddr(entries(last_hptw_req_id).hptw_resp.genPPNS2(get_pn(req_paddr)), getVpnn(io.in.bits.req_info.vpn, 0))
@@ -819,14 +935,29 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
 
   XSError(io.in.fire && ((to_mem_out && to_cache) || (to_wait && to_cache)), "llptw enq, to cache conflict with to mem")
   val mem_resp_hit = RegInit(VecInit(Seq.fill(l2tlbParams.llptwsize)(false.B)))
-  val enq_state_normal = MuxCase(state_addr_check, Seq(
-    to_mem_out -> state_mem_out, // same to the blew, but the mem resp now
-    to_bitmap_req -> state_bitmap_check,
-    to_last_hptw_req -> state_last_hptw_req,
-    to_wait -> state_mem_waiting,
-    to_cache -> state_cache,
-    to_hptw_req -> state_hptw_req
-  ))
+  val enq_state_normal = if (HasMptCheck) (
+    MuxCase(state_addr_check, Seq(
+      to_mem_out -> state_mem_out, // same to the blew, but the mem resp now
+      to_bitmap_req -> state_bitmap_check,
+      to_last_hptw_req -> state_last_hptw_req,
+      to_wait -> state_mem_waiting,
+      to_cache -> state_cache,
+      to_hptw_req -> state_hptw_req,
+
+      dup_mpt_resp -> Mux(accessFaultMpt, state_mem_out, state_mem_req),
+      to_mpt -> stage_mpt_resp,// wait at stage mpt resp, if dup mpt
+      to_mpt_mem_req -> state_mem_req
+    ))
+  )else(
+    MuxCase(state_addr_check, Seq(
+      to_mem_out -> state_mem_out, // same to the blew, but the mem resp now
+      to_bitmap_req -> state_bitmap_check,
+      to_last_hptw_req -> state_last_hptw_req,
+      to_wait -> state_mem_waiting,
+      to_cache -> state_cache,
+      to_hptw_req -> state_hptw_req
+    ))
+  )
   val enq_state = Mux(from_pre(io.in.bits.req_info.source) && enq_state_normal =/= state_addr_check, state_idle, enq_state_normal)
   when (io.in.fire && (if (HasBitmapCheck) !io.in.bits.bitmapCheck.get.jmp_bitmap_check else true.B)) {
     // if prefetch req does not need mem access, just give it up.
@@ -836,7 +967,15 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     entries(enq_ptr).req_info := io.in.bits.req_info
     entries(enq_ptr).ppn := Mux(to_bitmap_req || to_last_hptw_req || last_hptw_excp, last_hptw_req_ppn, io.in.bits.ppn)
     entries(enq_ptr).wait_id := Mux(to_wait, wait_id, enq_ptr)
+    if (HasMptCheck) {
+      entries(enq_ptr).wait_id := Mux(to_mpt_mem_req , wait_mpt_mem_id,Mux(to_mpt,wait_mpt_id, Mux(to_wait, wait_id, enq_ptr)))
+    }
     entries(enq_ptr).af := false.B
+    if (HasMptCheck) {
+      when(dup_mpt_resp) {
+        entries(enq_ptr).af := accessFaultMpt
+      }
+    }
     if (HasBitmapCheck) {
       entries(enq_ptr).cf := false.B
       entries(enq_ptr).from_l0 := false.B
@@ -903,7 +1042,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     val ptr = enq_ptr_reg
     val accessFault = io.pmp(0).resp.ld || io.pmp(0).resp.mmio
     entries(ptr).af := accessFault
-    state(ptr) := Mux(accessFault, state_mem_out, state_mem_req)
+    state(ptr) := Mux(accessFault, state_mem_out, (if (HasMptCheck) Mux(mptEn && checkIntermediateNode, stage_mpt_req, state_mem_req) else state_mem_req))
   }
 
   io.pmp(1).req.valid := hptw_need_addr_check
@@ -914,7 +1053,31 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     val ptr = hptw_resp_ptr_reg
     val accessFault = io.pmp(1).resp.ld || io.pmp(1).resp.mmio
     entries(ptr).af := accessFault
-    state(ptr) := Mux(accessFault, state_mem_out, state_mem_req)
+    state(ptr) := Mux(accessFault, state_mem_out, (if (HasMptCheck) Mux(mptEn && checkIntermediateNode, stage_mpt_req, state_mem_req) else state_mem_req))
+    //switch to mpt req state when mpten, else same as before
+  }
+
+  if (HasMptCheck) {
+    when (mpt_arb.get.io.out.fire) {
+      for (i <- state.indices) {
+        when (state(i) =/= state_idle && state(i) =/= state_mem_out && state(i) =/= state_last_hptw_req && state(i) =/= state_last_hptw_resp &&
+          entries(i).req_info.s2xlate === mpt_arb.get.io.out.bits.req_info.s2xlate &&
+          dup(entries(i).req_info.vpn, mpt_arb.get.io.out.bits.req_info.vpn)) {
+          // NOTE: "dup enq set state to mem_wait" -> "sending req set other dup entries to mem_wait"
+          state(i) := stage_mpt_resp//set all match fsm to state mptResp
+          entries(i).wait_id := mpt_arb.get.io.chosen
+        }
+      }
+    }
+
+    when (io.mptCheck.get.resp.valid) {
+      state.indices.map{i =>
+        when (state(i) === stage_mpt_resp && io.mptCheck.get.resp.bits.id === entries(i).wait_id) {
+          entries(i).af := accessFaultMpt
+          state(i) := Mux(accessFaultMpt, state_mem_out, state_mem_req)//af just return
+        }
+      }
+    }
   }
 
   when (mem_arb.io.out.fire) {
@@ -1022,11 +1185,27 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     }
   }
 
+  val mem_state= RegInit(false.B)
+  if (HasMptCheck) {
+    when(mem_state === false.B && (VecInit(is_having).asUInt =/= 0.U)) {
+      mem_ptr := ParallelPriorityEncoder(is_having) // if mem_ptr is 0, then we should find the first entry that is having
+      mem_state := true.B
+    }
+  }
   when (io.out.fire) {
     assert(state(mem_ptr) === state_mem_out)
     state(mem_ptr) := state_idle
+  if (HasMptCheck) {
+      mem_state := false.B // reset mem_state when out fire
+    }
+  }
+  mem_resp_hit.map(a => when (a) { a := false.B })
+
+  if (HasMptCheck) {
+    when(flush) {
+      mem_state := false.B
+    }
   }
-  mem_resp_hit.map(a => when (a) { a := false.B } )
 
   when (io.cache.fire) {
     state(cache_ptr) := state_idle
@@ -1039,7 +1218,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
 
   io.in.ready := !full
 
-  io.out.valid := ParallelOR(is_having).asBool
+  io.out.valid := ParallelOR(is_having).asBool && (if (HasMptCheck) mem_state else true.B)
   io.out.bits.req_info := entries(mem_ptr).req_info
   io.out.bits.id := mem_ptr
   if (HasBitmapCheck) {
@@ -1090,6 +1269,17 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   io.mem.buffer_it := mem_resp_hit
   io.mem.enq_ptr := enq_ptr
 
+
+  if (HasMptCheck) { //mpt output assign send to mptarb
+    val mpt_paddr = MakeAddr(mpt_arb.get.io.out.bits.ppn, getVpnn(mpt_arb.get.io.out.bits.req_info.vpn, 0))
+    val mpt_hpaddr = MakeAddr(mpt_arb.get.io.out.bits.hptw_resp.genPPNS2(get_pn(mpt_paddr)), getVpnn(mpt_arb.get.io.out.bits.req_info.vpn, 0))
+    io.mptCheck.get.req.bits.reqPA := Mux(mpt_arb.get.io.out.bits.req_info.s2xlate === allStage, mpt_hpaddr(PAddrBits-1, offLen), mpt_paddr(PAddrBits-1, offLen))
+    io.mptCheck.get.req.bits.id := mpt_arb.get.io.chosen
+    io.mptCheck.get.req.valid := mpt_arb.get.io.out.valid && !flush
+    io.mptCheck.get.req.bits.mptOnly := DontCare //mpt during tw is never mpt only
+    mpt_arb.get.io.out.ready := io.mptCheck.get.req.ready
+  }
+
   io.cache.valid := Cat(is_cache).orR
   io.cache.bits := ParallelMux(is_cache, entries.map(_.req_info))
 
@@ -1182,6 +1372,11 @@ class HPTWIO()(implicit p: Parameters) extends MMUIOBaseBundle with HasPtwConst
   })
 
   val l0_way_info = Option.when(HasBitmapCheck)(Input(UInt(l2tlbParams.l0nWays.W)))
+
+  val mptCheck = Option.when(HasMptCheck)(new Bundle {
+    val req = DecoupledIO(new MptReqBundle())
+    val resp = Flipped(ValidIO(new MptRespBundle()))
+  })
 }
 
 class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
@@ -1189,9 +1384,11 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   val hgatp = io.csr.hgatp
   val mpbmte = io.csr.mPBMTE
   val sfence = io.sfence
-  val flush = sfence.valid || hgatp.changed || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.priv.virt_changed
+  val flush = sfence.valid || hgatp.changed || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B)
   val mode = hgatp.mode
 
+  val mptEn = if (HasMptCheck) (io.csr.mmpt.mode =/= 0.U) else false.B // mpt bare mode will generate the circuit but not using it
+  val checkIntermediateNode = if (HasMptCheck) (io.csr.mmpt.optOutInNode === 0.U) else false.B
   // mbmc:bitmap csr
   val mbmc = io.csr.mbmc
   val bitmap_enable = (if (HasBitmapCheck) true.B else false.B) && mbmc.BME === 1.U && mbmc.CMODE === 0.U
@@ -1210,6 +1407,8 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   val jmp_bitmap_check = if (HasBitmapCheck) RegEnable(io.req.bits.bitmapCheck.get.jmp_bitmap_check, io.req.fire) else false.B
   val fromSP = if (HasBitmapCheck) RegEnable(io.req.bits.bitmapCheck.get.fromSP, io.req.fire) else false.B
   val cache_pte = Option.when(HasBitmapCheck)(RegEnable(Mux(io.req.bits.bitmapCheck.get.fromSP, io.req.bits.bitmapCheck.get.pte.asTypeOf(new PteBundle().cloneType), io.req.bits.bitmapCheck.get.ptes(io.req.bits.gvpn(sectortlbwidth - 1, 0)).asTypeOf(new PteBundle().cloneType)), io.req.fire))
+
+
   val pte = if (HasBitmapCheck) Mux(jmp_bitmap_check, cache_pte.get, io.mem.resp.bits.asTypeOf(new PteBundle().cloneType)) else io.mem.resp.bits.asTypeOf(new PteBundle().cloneType)
   val ppn_l3 = Mux(l3Hit, req_ppn, pte.ppn)
   val ppn_l2 = Mux(l2Hit, req_ppn, pte.ppn)
@@ -1241,6 +1440,10 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   val idle = RegInit(true.B)
   val mem_addr_update = RegInit(false.B)
   val finish = WireInit(false.B)
+
+  val s_mpt_check = RegInit(false.B)//new mpt state
+  val w_mpt_resp = RegInit(false.B)
+
   val s_bitmap_check = RegInit(true.B)
   val w_bitmap_resp = RegInit(true.B)
   val whether_need_bitmap_check = RegInit(false.B)
@@ -1249,6 +1452,13 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   val sent_to_pmp = !idle && (!s_pmp_check || mem_addr_update) && !finish
   val pageFault = pte.isGpf(level, mpbmte) || (!pte.isLeaf() && level === 0.U)
   val accessFault = RegEnable(io.pmp.resp.ld || io.pmp.resp.mmio, sent_to_pmp)
+  // use access fault when mpt check failed
+  val mpt_af = if (HasMptCheck)
+    RegEnable(!io.mptCheck.get.resp.bits.mptPerm(0) || io.mptCheck.get.resp.bits.accessFault, io.mptCheck.get.resp.valid)
+  else
+    false.B
+  val accessFaultMpt = if (HasMptCheck) (accessFault || mpt_af) else accessFault
+  //R=0, af or not readable=>af , mpt af will be reged
 
   // use access fault when bitmap check failed
   val ppn_af = if (HasBitmapCheck) {
@@ -1258,7 +1468,8 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   }
   val find_pte = pte.isLeaf() || ppn_af || pageFault
 
-  val resp_valid = !idle && mem_addr_update && ((w_mem_resp && find_pte) || (s_pmp_check && accessFault))
+  val resp_valid = !idle && mem_addr_update && ((w_mem_resp && find_pte) || (s_pmp_check && accessFault) ||
+    (if (HasMptCheck) (mpt_af) else false.B))
   val id = Reg(UInt(log2Up(l2tlbParams.llptwsize).W))
   val source = RegEnable(io.req.bits.source, io.req.fire)
 
@@ -1266,9 +1477,9 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   val resp = Wire(new HptwResp())
   // accessFault > pageFault > ppn_af
   resp.apply(
-    gpf = pageFault && !accessFault,
-    gaf = accessFault || (ppn_af && !pageFault),
-    level = Mux(accessFault, af_level, level),
+    gpf = pageFault && !accessFault && (if (HasMptCheck) !mpt_af else true.B), // mptAf is also gaf
+    gaf = accessFaultMpt || (ppn_af && !pageFault),
+    level = Mux(accessFaultMpt, af_level, level),
     pte = pte,
     vpn = vpn,
     vmid = hgatp.vmid
@@ -1283,6 +1494,15 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   io.pmp.req.bits.size := 3.U
   io.pmp.req.bits.cmd := TlbCmd.read
 
+
+  if (HasMptCheck) {//send to mpt req
+    io.mptCheck.get.req.valid := s_mpt_check && !accessFault && s_pmp_check
+    io.mptCheck.get.req.bits.mptOnly := DontCare//mpt during tw is never mpt only
+    io.mptCheck.get.req.bits.reqPA := mem_addr(PAddrBits-1 ,offLen)//same as pmp but no offset
+    io.mptCheck.get.req.bits.id := HptwReqId.U(bMemID.W)
+  }
+
+
   if (HasBitmapCheck) {
     val way_info = DataHoldBypass(io.l0_way_info.get, RegNext(io.mem.resp.fire, init=false.B))
     val cache_hitway = RegEnable(io.req.bits.bitmapCheck.get.hitway, io.req.fire)
@@ -1299,7 +1519,7 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     io.bitmap.get.resp.ready := !w_bitmap_resp
   }
 
-  io.mem.req.valid := !s_mem_req && !io.mem.mask && !accessFault && s_pmp_check
+  io.mem.req.valid := !s_mem_req && !io.mem.mask && !accessFault && s_pmp_check && (if (HasMptCheck) !mpt_af else true.B) // mptAf stop req mem
   io.mem.req.bits.addr := mem_addr
   io.mem.req.bits.id := HptwReqId.U(bMemID.W)
   io.mem.req.bits.hptw_bypassed := bypassed
@@ -1309,7 +1529,9 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
   io.refill.req_info.source := source
   io.refill.req_info.s2xlate := onlyStage2
 
-  when (idle){
+  if (HasMptCheck) {io.refill.req_info.mptOnly.get := DontCare}
+
+  when (idle) {
     if (HasBitmapCheck) {
       when (io.req.bits.bitmapCheck.get.jmp_bitmap_check && io.req.fire) {
         idle := false.B
@@ -1324,6 +1546,9 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
       idle := false.B
       gpaddr := Cat(io.req.bits.gvpn, 0.U(offLen.W))
       accessFault := false.B
+      if (HasMptCheck) {
+        mpt_af := false.B
+      }//reset mptAf
       s_pmp_check := false.B
       id := io.req.bits.id
       req_ppn := io.req.bits.ppn
@@ -1347,16 +1572,30 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     }
   }
 
-  when(sent_to_pmp && !mem_addr_update){
-    s_mem_req := false.B
+  when(sent_to_pmp && !mem_addr_update) {
+    if (HasMptCheck) {
+      when(mptEn && checkIntermediateNode) {
+        s_mpt_check := true.B
+      }.otherwise {
+        s_mem_req := false.B
+      } //to mptcheck state，send valid
+    } else {
+      s_mem_req := false.B
+    } // send to mpt /send to mem,  when pmp pass)
     s_pmp_check := true.B
   }
 
-  when(accessFault && !idle){
+  when(!idle && accessFaultMpt) { // pmp return af
     s_pmp_check := true.B
     s_mem_req := true.B
     w_mem_resp := true.B
     mem_addr_update := true.B
+
+    if (HasMptCheck) { // reset s_mpt_check
+      s_mpt_check := false.B
+      w_mpt_resp := false.B
+    }
+
     if (HasBitmapCheck) {
       s_bitmap_check := true.B
       w_bitmap_resp := true.B
@@ -1365,12 +1604,28 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     }
   }
 
-  when(io.mem.req.fire){
+  //mpt fsm
+  if (HasMptCheck) {
+    when(s_mpt_check && !accessFault) {
+      when(io.mptCheck.get.req.fire) {
+        s_mpt_check := false.B
+        w_mpt_resp := true.B
+      }
+    }
+    when(w_mpt_resp) {
+      when(io.mptCheck.get.resp.valid) {
+        w_mpt_resp := false.B
+        s_mem_req := false.B // send to mem when mpt pass
+      }
+    }
+  }
+
+  when(io.mem.req.fire) {
     s_mem_req := true.B
     w_mem_resp := false.B
   }
 
-  when(io.mem.resp.fire && !w_mem_resp){
+  when(io.mem.resp.fire && !w_mem_resp) {
     w_mem_resp := true.B
     af_level := af_level - 1.U
     if (HasBitmapCheck) {
@@ -1407,19 +1662,26 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     }
   }
 
-  when(mem_addr_update){
-    when(!(find_pte || accessFault)){
+  when(mem_addr_update) {
+    when(!(find_pte || accessFaultMpt)) {
       level := levelNext
-      s_mem_req := false.B
+      if (HasMptCheck) {
+        s_mpt_check := true.B // mem_addr_update checks pmp, next is mpt or mem access state
+      } else {
+        s_mem_req := false.B
+      }
       mem_addr_update := false.B
-    }.elsewhen(resp_valid){
-      when(io.resp.fire){
+    }.elsewhen(resp_valid) {
+      when(io.resp.fire) {
         idle := true.B
         mem_addr_update := false.B
         accessFault := false.B
         if (HasBitmapCheck) {
           bitmap_checkfailed := false.B
         }
+        if (HasMptCheck) {
+          mpt_af := false.B
+        }
       }
       finish := true.B
     }
@@ -1431,6 +1693,11 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     w_mem_resp := true.B
     accessFault := false.B
     mem_addr_update := false.B
+    if (HasMptCheck) {
+      mpt_af := false.B
+      s_mpt_check := false.B
+      w_mpt_resp := false.B
+    }
     if (HasBitmapCheck) {
       s_bitmap_check := true.B
       w_bitmap_resp := true.B
diff --git a/src/main/scala/xiangshan/cache/mmu/Repeater.scala b/src/main/scala/xiangshan/cache/mmu/Repeater.scala
index ecace6b9762..b36dfa62456 100644
--- a/src/main/scala/xiangshan/cache/mmu/Repeater.scala
+++ b/src/main/scala/xiangshan/cache/mmu/Repeater.scala
@@ -179,6 +179,7 @@ class PTWFilterEntry(Width: Int, Size: Int, hasHint: Boolean = false)(implicit p
   val v = RegInit(VecInit(Seq.fill(Size)(false.B)))
   val sent = RegInit(VecInit(Seq.fill(Size)(false.B)))
   val vpn = Reg(Vec(Size, UInt(vpnLen.W)))
+  val mptOnly = Option.when(HasMptCheck) (RegInit(Vec(Size, Bool()), 0.U.asTypeOf(Vec(Size, Bool()))))
   val s2xlate = Reg(Vec(Size, UInt(2.W)))
   val getGpa = Reg(Vec(Size, Bool()))
   val memidx = Reg(Vec(Size, new MemBlockidxBundle))
@@ -241,6 +242,9 @@ class PTWFilterEntry(Width: Int, Size: Int, hasHint: Boolean = false)(implicit p
       v(enqidx(i)) := true.B
       sent(enqidx(i)) := false.B
       vpn(enqidx(i)) := io.tlb.req(i).bits.vpn
+      if (HasMptCheck) {
+        mptOnly.get(enqidx(i)) := io.tlb.req(i).bits.mptOnly.get
+      }
       s2xlate(enqidx(i)) := io.tlb.req(i).bits.s2xlate
       getGpa(enqidx(i)) := io.tlb.req(i).bits.getGpa
       memidx(enqidx(i)) := io.tlb.req(i).bits.memidx
@@ -254,6 +258,9 @@ class PTWFilterEntry(Width: Int, Size: Int, hasHint: Boolean = false)(implicit p
     io.ptw.req(0).valid := canissue
     io.ptw.req(0).bits.vpn := vpn(issueindex)
     io.ptw.req(0).bits.s2xlate := s2xlate(issueindex)
+    if (HasMptCheck) {
+      io.ptw.req(0).bits.mptOnly.get := mptOnly.get(issueindex)
+    }
   }
   when (io.ptw.req(0).fire) {
     sent(issueindex) := true.B
@@ -367,6 +374,9 @@ class PTWNewFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameter
   io.tlb.resp.bits.data.getGpa := DontCare // not used
   io.tlb.resp.bits.data.s1 := ptwResp.s1
   io.tlb.resp.bits.data.s2 := ptwResp.s2
+  if (HasMptCheck) {
+    io.tlb.resp.bits.data.mpt.get := ptwResp.mpt.get
+  }
   io.tlb.resp.bits.data.memidx := 0.U.asTypeOf(new MemBlockidxBundle)
   // vector used to represent different requestors of DTLB
   // (e.g. the store DTLB has StuCnt requestors)
@@ -408,12 +418,18 @@ class PTWNewFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameter
     ptw_arb.io.in(i).valid := filter(i).ptw.req(0).valid
     ptw_arb.io.in(i).bits.vpn := filter(i).ptw.req(0).bits.vpn
     ptw_arb.io.in(i).bits.s2xlate := filter(i).ptw.req(0).bits.s2xlate
+    if (HasMptCheck) {
+      ptw_arb.io.in(i).bits.mptOnly.get := filter(i).ptw.req(0).bits.mptOnly.get
+    }
     filter(i).ptw.req(0).ready := ptw_arb.io.in(i).ready
   }
   ptw_arb.io.out.ready := io.ptw.req(0).ready
   io.ptw.req(0).valid := ptw_arb.io.out.valid
   io.ptw.req(0).bits.vpn := ptw_arb.io.out.bits.vpn
   io.ptw.req(0).bits.s2xlate := ptw_arb.io.out.bits.s2xlate
+  if (HasMptCheck) {
+    io.ptw.req(0).bits.mptOnly.get := ptw_arb.io.out.bits.mptOnly.get
+  }
   io.ptw.resp.ready := true.B
 
   io.rob_head_miss_in_tlb := Cat(filter.map(_.rob_head_miss_in_tlb)).orR
@@ -427,6 +443,7 @@ class PTWFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameters)
   val v = RegInit(VecInit(Seq.fill(Size)(false.B)))
   val ports = Reg(Vec(Size, Vec(Width, Bool()))) // record which port(s) the entry come from, may not able to cover all the ports
   val vpn = Reg(Vec(Size, UInt(vpnLen.W)))
+  val mptOnly = Option.when(HasMptCheck)(Reg(Vec(Size,Bool())))
   val s2xlate = Reg(Vec(Size, UInt(2.W)))
   val getGpa = Reg(Vec(Size, Bool()))
   val memidx = Reg(Vec(Size, new MemBlockidxBundle))
@@ -526,6 +543,9 @@ class PTWFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameters)
   io.tlb.resp.bits.data.s2xlate := ptwResp.s2xlate
   io.tlb.resp.bits.data.s1 := ptwResp.s1
   io.tlb.resp.bits.data.s2 := ptwResp.s2
+  if (HasMptCheck) {
+    io.tlb.resp.bits.data.mpt.get := ptwResp.mpt.get
+  }
   io.tlb.resp.bits.data.memidx := RegNext(PriorityMux(ptwResp_OldMatchVec, memidx))
   io.tlb.resp.bits.vector := resp_vector
   io.tlb.resp.bits.data.getGpa := RegNext(PriorityMux(ptwResp_OldMatchVec, getGpa))
@@ -538,12 +558,17 @@ class PTWFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameters)
   io.ptw.req(0).bits.vpn := vpn(issPtr)
   io.ptw.req(0).bits.s2xlate := s2xlate(issPtr)
   io.ptw.resp.ready := true.B
-
+  if (HasMptCheck) {
+    io.ptw.req(0).bits.mptOnly.get := mptOnly.get(issPtr)
+  }
   reqs.zipWithIndex.map{
     case (req, i) =>
       when (req.valid && canEnqueue) {
         v(enqPtrVec(i)) := !tlb_req_flushed(i)
         vpn(enqPtrVec(i)) := req.bits.vpn
+        if (HasMptCheck) {
+          mptOnly.get(enqPtrVec(i)) := req.bits.mptOnly.get
+        }
         s2xlate(enqPtrVec(i)) := req.bits.s2xlate
         getGpa(enqPtrVec(i)) := req.bits.getGpa
         memidx(enqPtrVec(i)) := req.bits.memidx
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 63122c8d7c8..c5ffe57b3b9 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -66,7 +66,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   val sfence = DelayN(io.sfence, q.fenceDelay)
   val csr = DelayN(io.csr, q.fenceDelay)
 
-  val flush_mmu = sfence.valid || csr.satp.changed || csr.vsatp.changed || csr.hgatp.changed || csr.priv.virt_changed
+  val flush_mmu = sfence.valid || csr.satp.changed || csr.vsatp.changed || csr.hgatp.changed || csr.priv.virt_changed ||
+  (if (HasMptCheck) csr.mmpt.changed else false.B)
   val mmu_flush_pipe = sfence.valid && sfence.bits.flushPipe // for svinval, won't flush pipe
   val flush_pipe = io.flushPipe
   val redirect = io.redirect
@@ -134,10 +135,22 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     (mode(i) < ModeM)
   )
 
+  val BareMode = Option.when(HasMptCheck) (
+    csr.hgatp.mode === 0.U && csr.vsatp.mode === 0.U && csr.satp.mode === 0.U
+  ) // will start mpt only check if not in M mode
   val useReqS1Paddr = (0 until Width).map(i => RegNext(req(i).bits.no_translate))
   val privNeedTranslate = (0 until Width).map(i => (vmEnable(i) || s2xlateEnable(i)))
   val portTranslateEnable = (0 until Width).map(i => privNeedTranslate(i) && !useReqS1Paddr(i))
 
+  val mptEn = Option.when(HasMptCheck) (csr.mmpt.mode =/= 0.U)
+  val mptCheckOnly = Option.when(HasMptCheck) ((0 until Width).map( i =>
+    BareMode.get && RegEnable(!req(i).bits.no_translate, req(i).valid) && mptEn.get && (mode(i) < ModeM)
+  )) // need mptcheck even when ptw disable, but dont need it when req.no_trans
+  (0 until Width).foreach{ i =>
+    if (HasMptCheck) {
+      io.ptw.req(i).bits.mptOnly.get := mptCheckOnly.get(i)
+    } // assign mptCheckonly to io req
+  }
   // pre fault: check fault before real do translate
   val prepf = WireInit(VecInit(Seq.fill(Width)(false.B)))
   val pregpf = WireInit(VecInit(Seq.fill(Width)(false.B)))
@@ -226,7 +239,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     }
   }
 
-  val refill = ptw.resp.fire && !(ptw.resp.bits.getGpa) && !need_gpa && !maybe_need_gpa_not_allow_refill && !flush_mmu
+  val refill = ptw.resp.fire && !(ptw.resp.bits.getGpa) && !need_gpa && !maybe_need_gpa_not_allow_refill && !flush_mmu || (if (HasMptCheck) (ptw.resp.fire && BareMode.get) else false.B)
+  // mpt only mode also returns and save to l1tlb
   // prevent ptw refill when: 1) it's a getGpa request; 2) l1tlb is in need_gpa state; 3) mmu is being flushed.
 
   refill_to_mem := DontCare
@@ -250,6 +264,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   val g_perm = readResult.map(_._5)
   val pbmt = readResult.map(_._6)
   val g_pbmt = readResult.map(_._7)
+  val mptPerm = Option.when(HasMptCheck) (readResult.map(_._8))
   // check pmp use paddr (for timing optization, use pmp_addr here)
   // check permisson
   (0 until Width).foreach{i =>
@@ -258,7 +273,9 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     pmp_check(addr, req_out(i).size, req_out(i).cmd, noTranslateReg, i)
     for (d <- 0 until nRespDups) {
       pbmt_check(i, d, pbmt(i)(d), g_pbmt(i)(d), req_out_s2xlate(i))
-      perm_check(perm(i)(d), req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i), prepf(i), pregpf(i), preaf(i))
+      val mptTemp = Option.when(HasMptCheck) (mptPerm.get(i)(d))
+      perm_check(mptTemp.getOrElse(0.U.asTypeOf(new MptPermBundle(isTlbPort = true))), mptEn.getOrElse(false.B), perm(i)(d),
+        req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i), prepf(i), pregpf(i), preaf(i))
     }
     hasGpf(i) := hitVec(i) && (resp(i).bits.excp(0).gpf.ld || resp(i).bits.excp(0).gpf.st || resp(i).bits.excp(0).gpf.instr)
   }
@@ -275,9 +292,14 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
 
   /************************  main body above | method/log/perf below ****************************/
   def TLBRead(i: Int) = {
-    val (e_hit, e_ppn, e_perm, e_g_perm, e_s2xlate, e_pbmt, e_g_pbmt) = entries.io.r_resp_apply(i)
-    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte, p_hit_fast) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
+    val (e_hit, e_ppn, e_perm, e_g_perm, e_s2xlate, e_pbmt, e_g_pbmt, e_mptperm) = entries.io.r_resp_apply(i)
+    // HasMptCheck, add mptperm
+    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate,
+      p_s1_level, p_s1_isLeaf, p_s1_isFakePte, p_hit_fast, p_mptPerm) =
+      ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
     val enable = portTranslateEnable(i)
+    val mptOnlyEnable = Option.when(HasMptCheck) (mptCheckOnly.get(i))
+    // when in check only mode, assign miss to true to start query mpt in l2tlb
     val isOnlys2xlate = req_out_s2xlate(i) === onlyStage2
     val need_gpa_vpn_hit = need_gpa_vpn === get_pn(req_out(i).vaddr)
     val isitlb = TlbCmd.isExec(req_out(i).cmd)
@@ -313,7 +335,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     }
 
     val hit = e_hit || p_hit
-    val miss = (!hit && enable) || hasGpf(i) && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate && !isPrefetch && !lastCycleRedirect
+    val miss = (!hit && enable) || hasGpf(i) && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate && !isPrefetch && !lastCycleRedirect ||
+      (if (HasMptCheck) mptOnlyEnable.get && !hit else false.B) // for mpt only either hit or miss
     hit.suggestName(s"hit_read_${i}")
     miss.suggestName(s"miss_read_${i}")
 
@@ -334,6 +357,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val g_pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
     val g_perm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new TlbPermBundle))))
     val r_s2xlate = WireInit(VecInit(Seq.fill(nRespDups)(0.U(2.W))))
+
+    val mptPerm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new MptPermBundle)))) // HasMptCheck
     for (d <- 0 until nRespDups) {
       ppn(d) := Mux(p_hit, p_ppn, e_ppn(d))
       pbmt(d) := Mux(p_hit, p_pbmt, e_pbmt(d))
@@ -345,6 +370,9 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       g_pbmt(d) := Mux(p_hit, p_g_pbmt, e_g_pbmt(d))
       g_perm(d) := Mux(p_hit, p_g_perm, e_g_perm(d))
       r_s2xlate(d) := Mux(p_hit, p_s2xlate, e_s2xlate(d))
+      if (HasMptCheck) {
+        mptPerm(d) := Mux(p_hit, p_mptPerm.get, e_mptperm.get(d))
+      }
       val paddr = Cat(ppn(d), get_off(req_out(i).vaddr))
       val vpn_idx = Mux1H(Seq(
         (isFakePte(d) && csr.vsatp.mode === Sv39) -> 2.U,
@@ -371,7 +399,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
 
     val pmp_paddr = resp(i).bits.paddr(0)
 
-    (hit, miss, pmp_paddr, perm, g_perm, pbmt, g_pbmt)
+    (hit, miss, pmp_paddr, perm, g_perm, pbmt, g_pbmt, mptPerm)
   }
 
   def getVpnn(vpn: UInt, idx: UInt): UInt = {
@@ -404,7 +432,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   }
 
   // for timing optimization, pmp check is divided into dynamic and static
-  def perm_check(perm: TlbPermBundle, cmd: UInt, idx: Int, nDups: Int, g_perm: TlbPermBundle, hlvx: Bool, s2xlate: UInt, prepf: Bool = false.B, pregpf: Bool = false.B, preaf: Bool = false.B) = {
+  def perm_check(mptPerm: MptPermBundle, mptEn: Bool, perm: TlbPermBundle, cmd: UInt, idx: Int, nDups: Int,
+    g_perm: TlbPermBundle, hlvx: Bool, s2xlate: UInt, prepf: Bool = false.B, pregpf: Bool = false.B, preaf: Bool = false.B) = {
     // dynamic: superpage (or full-connected reg entries) -> check pmp when translation done
     // static: 4K pages (or sram entries) -> check pmp with pre-checked results
     val hasS2xlate = s2xlate =/= noS2xlate
@@ -435,6 +464,13 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val isNonLeaf = !(perm.r || perm.w || perm.x) && perm.v && !perm.pf && !perm.af
     val s1_valid = portTranslateEnable(idx) && !onlyS2
 
+    // mptcheck val
+    val mptValid = Option.when(HasMptCheck) (mptCheckOnly.get(idx))
+    val mptInstf = Option.when(HasMptCheck) (!mptPerm.x && isInst)
+    val mptWf = Option.when(HasMptCheck) (!(mptPerm.r && mptPerm.w) && isSt)
+    val mptRf = Option.when(HasMptCheck) (!mptPerm.r && isLd)
+    val mptAf = Option.when(HasMptCheck) (mptPerm.af.get)
+
     // Stage 2 perm check
     val gpf = g_perm.pf
     val g_ldUpdate = !g_perm.a && isLd
@@ -488,9 +524,14 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       resp(idx).bits.excp(nDups).gpf.st := (stGpf || g_stUpdate) && s2_valid && !af && !hasPf
       resp(idx).bits.excp(nDups).gpf.instr := (instrGpf || g_instrUpdate) && s2_valid && !af && !hasPf
 
-      resp(idx).bits.excp(nDups).af.ld    := af && TlbCmd.isRead(cmd) && fault_valid
-      resp(idx).bits.excp(nDups).af.st    := af && TlbCmd.isWrite(cmd) && fault_valid
-      resp(idx).bits.excp(nDups).af.instr := af && TlbCmd.isExec(cmd) && fault_valid
+      resp(idx).bits.excp(nDups).af.ld := (af && TlbCmd.isRead(cmd) && fault_valid) ||
+        (if (HasMptCheck) ((mptRf.get || (mptAf.get && TlbCmd.isRead(cmd))) && mptValid.get) else false.B)
+      // Mpt always on and valid, even if s1 s2 in bare mode
+      resp(idx).bits.excp(nDups).af.st := (af && TlbCmd.isWrite(cmd) && fault_valid) ||
+        (if (HasMptCheck) ((mptWf.get || (mptAf.get && TlbCmd.isWrite(cmd))) && mptValid.get) else false.B)
+      resp(idx).bits.excp(nDups).af.instr := (af && TlbCmd.isExec(cmd) && fault_valid) ||
+        (if (HasMptCheck) ((mptInstf.get || (mptAf.get && TlbCmd.isExec(cmd))) && mptValid.get) else false.B)
+      // mptAf is af
 
       resp(idx).bits.excp(nDups).vaNeedExt := true.B
     }
@@ -573,11 +614,16 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       io.ptw.req(idx).fire || resp(idx).fire, flush_pipe(idx))
 
     // when ptw resp, check if hit, reset miss_v, resp to lsu/ifu
-    resp(idx).valid := req_out_v(idx) && !(miss_v && portTranslateEnable(idx))
-    when (io.ptw.resp.fire && hit && req_out_v(idx) && portTranslateEnable(idx)) {
+    resp(idx).valid := req_out_v(idx) && (!(miss_v && portTranslateEnable(idx)) &&
+      (if (HasMptCheck) !(miss_v && mptCheckOnly.get(idx)) else true.B))
+    when (io.ptw.resp.fire && hit && req_out_v(idx) && (portTranslateEnable(idx) ||
+      (if (HasMptCheck) mptCheckOnly.get(idx) else false.B))) {
+      // add condition, equivalently make it = true.B, always return
       val stage1 = io.ptw.resp.bits.s1
       val stage2 = io.ptw.resp.bits.s2
       val s2xlate = io.ptw.resp.bits.s2xlate
+      val mptResp = Option.when(HasMptCheck) (Wire (new MptPermBundle(isTlbPort = true)))
+      if (HasMptCheck) {mptResp.get.resp_apply(io.ptw.resp.bits.mpt.get)}
       resp(idx).valid := true.B
       resp(idx).bits.miss := false.B
       val vpn = get_pn(req_out(idx).vaddr)
@@ -603,7 +649,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
         resp(idx).bits.paddr(d) := Mux(s2xlate === onlyStage2 || s2xlate === allStage, s2_paddr, s1_paddr)
         resp(idx).bits.gpaddr(d) := Mux(s2xlate === onlyStage2, req_out(idx).vaddr, s1_gpaddr)
         pbmt_check(idx, d, io.ptw.resp.bits.s1.entry.pbmt, io.ptw.resp.bits.s2.entry.pbmt, s2xlate)
-        perm_check(stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
+        perm_check(mptResp.getOrElse(0.U.asTypeOf(new MptPermBundle(isTlbPort = true))), mptEn.getOrElse(false.B),
+          stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
       }
       pmp_check(resp(idx).bits.paddr(0), req_out(idx).size, req_out(idx).cmd, false.B, idx)
 
@@ -661,7 +708,15 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val p_s1_level = RegEnable(ptw.resp.bits.s1.entry.level.get, io.ptw.resp.fire)
     val p_s1_isLeaf = RegEnable(ptw.resp.bits.s1.isLeaf(), io.ptw.resp.fire)
     val p_s1_isFakePte = RegEnable(ptw.resp.bits.s1.isFakePte(), io.ptw.resp.fire)
-    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte, p_hit_fast)
+
+    val p_mptPerm_temp = Option.when(HasMptCheck) (Wire(new MptPermBundle(isTlbPort = true)))
+    if (HasMptCheck) {
+      p_mptPerm_temp.get.resp_apply(ptw.resp.bits.mpt.get)
+    }
+    val p_mptPerm = Option.when(HasMptCheck) (RegEnable(p_mptPerm_temp.get, io.ptw.resp.fire)) // HasMptCheck,add mptperm
+
+    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate,
+      p_s1_level, p_s1_isLeaf, p_s1_isFakePte, p_hit_fast, p_mptPerm)
   }
 
   // perf event
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index 1b4232d3514..a231693d3cd 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -134,6 +134,7 @@ class TLBFA(
     val perm     = entries.map(_.perm)
     val gPerm    = entries.map(_.g_perm)
     val s2xLate  = entries.map(_.s2xlate)
+    val mptperm = Option.when(HasMptCheck) (entries.map(_.mptperm.get)) // hasmptcheck
     if (nWays == 1) {
       for (d <- 0 until nDups) {
         resp.bits.ppn(d) := entries(0).genPPN(saveLevel, resp.valid)(reqVpn)
@@ -142,6 +143,12 @@ class TLBFA(
         resp.bits.perm(d) := perm(0)
         resp.bits.g_perm(d) := gPerm(0)
         resp.bits.s2xlate(d) := s2xLate(0)
+        if (HasMptCheck) {
+          resp.bits.mptperm.get(d).x := mptperm.get(0).x
+          resp.bits.mptperm.get(d).w := mptperm.get(0).w
+          resp.bits.mptperm.get(d).r := mptperm.get(0).r
+          resp.bits.mptperm.get(d).af.get := false.B
+        }
       }
     } else {
       for (d <- 0 until nDups) {
@@ -151,6 +158,15 @@ class TLBFA(
         resp.bits.perm(d) := Mux1H(hitVecReg zip perm)
         resp.bits.g_perm(d) := Mux1H(hitVecReg zip gPerm)
         resp.bits.s2xlate(d) := Mux1H(hitVecReg zip s2xLate)
+        if (HasMptCheck) {
+          val mptpermtmp = Mux1H(hitVecReg zip mptperm.get)
+          if (HasMptCheck) {
+            resp.bits.mptperm.get(d).x := mptpermtmp.x
+            resp.bits.mptperm.get(d).w := mptpermtmp.w
+            resp.bits.mptperm.get(d).r := mptpermtmp.r
+            resp.bits.mptperm.get(d).af.get := false.B
+          }
+        }
       }
     }
 
@@ -185,7 +201,7 @@ class TLBFA(
   }
 
   val sfence = io.sfence
-  val sfence_valid = sfence.valid && !sfence.bits.hg && !sfence.bits.hv
+  val sfence_valid = sfence.valid && !sfence.bits.hg && !sfence.bits.hv && (if (HasMptCheck) !(sfence.bits.mfence.get) else true.B)
   val sfence_vpn = sfence.bits.addr(VAddrBits - 1, offLen)
   val sfenceHit = entries.map(_.hit(sfence_vpn, sfence.bits.id, vmid = io.csr.hgatp.vmid, hasS2xlate = io.csr.priv.virt))
   val sfenceHit_noasid = entries.map(_.hit(sfence_vpn, sfence.bits.id, ignoreAsid = true, vmid = io.csr.hgatp.vmid, hasS2xlate = io.csr.priv.virt))
@@ -245,11 +261,11 @@ class TLBFA(
      *  sfence addr ---> |        |
      *  try to flush     |        |
      *                   +--------+
-     * 
-     * In this case, the VS-stage is a large page, while the G-stage is a small page. L1TLB stores them as a small page. 
-     * When hfence.vvma comes with an address in that VS large page but outside the small page, it should flush the VS page. 
+     *
+     * In this case, the VS-stage is a large page, while the G-stage is a small page. L1TLB stores them as a small page.
+     * When hfence.vvma comes with an address in that VS large page but outside the small page, it should flush the VS page.
      * However, since L1TLB always treats this entry as a small page, it cannot match this address, thus cannot flush this entry.
-     * 
+     *
     ***/
     // when (hfencev.bits.rs1) {
     //   // all addr
@@ -274,6 +290,16 @@ class TLBFA(
       v.zipWithIndex.map { case (a, i) => a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === sfence.bits.id) }
     }
   }
+  if (HasMptCheck) {
+    when(sfence.valid && sfence.bits.mfence.get) {
+      v.zipWithIndex.map { case (a, i) => a := false.B } // mfence reset all
+    }
+    val modechange = DataChanged(io.csr.satp.mode).asBool || DataChanged(io.csr.vsatp.mode).asBool ||
+      DataChanged(io.csr.hgatp.mode).asBool
+    when(modechange) {
+      v.zipWithIndex.map { case (a, i) => a := false.B } // mptonly to ptw reset all
+    }
+  }
 
   XSPerfAccumulate(s"access", io.r.resp.map(_.valid.asUInt).fold(0.U)(_ + _))
   XSPerfAccumulate(s"hit", io.r.resp.map(a => a.valid && a.bits.hit).fold(0.U)(_.asUInt + _.asUInt))
@@ -425,6 +451,9 @@ class TlbStorageWrapper(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit p
       rp.bits.g_perm(d) := p.bits.g_perm(d)
       rp.bits.pbmt(d) := p.bits.pbmt(d)
       rp.bits.g_pbmt(d) := p.bits.g_pbmt(d)
+      if (HasMptCheck) {
+        rp.bits.mptperm.get(d) := p.bits.mptperm.get(d)
+      }
     }
   }
 
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index 78fd3402000..017b384fbda 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -459,6 +459,7 @@ package object xiangshan {
     def hfence_v = "b10011".U
     def hfence_g = "b10100".U
     def nofence= "b00000".U
+    def mfence = "b10111".U // HasMptCheck self defined instruction
   }
 
   object ALUOpType {
```
