# Commit Log
- Issue: #3559
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3559
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3559
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3559
- Changed files: 5
- Additions: 116
- Deletions: 21

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 97603803913..d635fdd60fe 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -851,6 +851,11 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   vecException.io.vtype := decodedInst.vpu.vtype
   vecException.io.vstart := decodedInst.vpu.vstart
 
+  private val isCboClean = CBO_CLEAN === io.enq.ctrlFlow.instr
+  private val isCboFlush = CBO_FLUSH === io.enq.ctrlFlow.instr
+  private val isCboInval = CBO_INVAL === io.enq.ctrlFlow.instr
+  private val isCboZero  = CBO_ZERO  === io.enq.ctrlFlow.instr
+
   private val exceptionII =
     decodedInst.selImm === SelImm.INVALID_INSTR ||
     vecException.io.illegalInst ||
@@ -867,7 +872,10 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     io.fromCSR.illegalInst.vsIsOff    && FuType.FuTypeOrR(decodedInst.fuType, FuType.vecAll) ||
     io.fromCSR.illegalInst.wfi        && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWfi(decodedInst.fuOpType) ||
     (decodedInst.needFrm.scalaNeedFrm || FuType.isScalaNeedFrm(decodedInst.fuType)) && (((decodedInst.fpu.rm === 5.U) || (decodedInst.fpu.rm === 6.U)) || ((decodedInst.fpu.rm === 7.U) && io.fromCSR.illegalInst.frm)) ||
-    (decodedInst.needFrm.vectorNeedFrm || FuType.isVectorNeedFrm(decodedInst.fuType)) && io.fromCSR.illegalInst.frm
+    (decodedInst.needFrm.vectorNeedFrm || FuType.isVectorNeedFrm(decodedInst.fuType)) && io.fromCSR.illegalInst.frm ||
+    io.fromCSR.illegalInst.cboZ       && isCboZero ||
+    io.fromCSR.illegalInst.cboCF      && (isCboClean || isCboFlush) ||
+    io.fromCSR.illegalInst.cboI       && isCboInval
 
   private val exceptionVI =
     io.fromCSR.virtualInst.sfenceVMA  && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.sfence ||
@@ -875,7 +883,11 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     io.fromCSR.virtualInst.hfence     && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && (decodedInst.fuOpType === FenceOpType.hfence_g || decodedInst.fuOpType === FenceOpType.hfence_v) ||
     io.fromCSR.virtualInst.hlsv       && FuType.FuTypeOrR(decodedInst.fuType, FuType.ldu)   && (LSUOpType.isHlv(decodedInst.fuOpType) || LSUOpType.isHlvx(decodedInst.fuOpType)) ||
     io.fromCSR.virtualInst.hlsv       && FuType.FuTypeOrR(decodedInst.fuType, FuType.stu)   && LSUOpType.isHsv(decodedInst.fuOpType) ||
-    io.fromCSR.virtualInst.wfi        && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWfi(decodedInst.fuOpType)
+    io.fromCSR.virtualInst.wfi        && FuType.FuTypeOrR(decodedInst.fuType, FuType.csr)   && CSROpType.isWfi(decodedInst.fuOpType) ||
+    io.fromCSR.virtualInst.cboZ       && isCboZero ||
+    io.fromCSR.virtualInst.cboCF      && (isCboClean || isCboFlush) ||
+    io.fromCSR.virtualInst.cboI       && isCboInval
+
 
   decodedInst.exceptionVec(illegalInstr) := exceptionII || io.enq.ctrlFlow.exceptionVec(EX_II)
   decodedInst.exceptionVec(virtualInstr) := exceptionVI
@@ -1089,11 +1101,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     decodedInst.selImm := SelImm.IMM_S
     decodedInst.fuType := FuType.ldu.U
     decodedInst.canRobCompress := false.B
-    decodedInst.fuOpType := Mux1H(Seq(
-      isPreW -> LSUOpType.prefetch_w,
-      isPreR -> LSUOpType.prefetch_r,
-      isPreI -> LSUOpType.prefetch_i,
-    ))
   }.elsewhen (isZimop) {
     // set srcType for zimop
     decodedInst.srcType(0) := SrcType.reg
@@ -1128,6 +1135,12 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     isCsrrVl    -> VSETOpType.csrrvl,
     isCsrrVlenb -> ALUOpType.add,
     isFLI       -> Cat(1.U, inst.FMT, inst.RS1),
+    (isPreW || isPreR || isPreI) -> Mux1H(Seq(
+      isPreW -> LSUOpType.prefetch_w,
+      isPreR -> LSUOpType.prefetch_r,
+      isPreI -> LSUOpType.prefetch_i,
+    )),
+    (isCboInval && io.fromCSR.special.cboI2F) -> LSUOpType.cbo_flush,
   ))
 
   //-------------------------------------------------------------
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
index de9b215e848..83816dd2b3e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
@@ -43,17 +43,17 @@ object CSRBundles {
   class XtinstBundle extends FieldInitBundle
 
   abstract class EnvCfg extends CSRBundle {
-    // Set all fields as RO in base class
-    val STCE  = RO(    63).withReset(0.U) // Sstc Enable
-    val PBMTE = RO(    62).withReset(0.U) // Svpbmt Enable
-    val ADUE  = RO(    61).withReset(0.U) // Svadu extension Enable
-    val PMM   = RO(33, 32).withReset(0.U) // Smnpm extension
-    val CBZE  = RO(     7).withReset(0.U) // Zicboz extension
-    val CBCFE = RO(     6).withReset(0.U) // Zicbom extension
-    val CBIE  = RO( 5,  4).withReset(0.U) // Zicbom extension
-    val SSE   = RO(     3).withReset(0.U) // Zicfiss extension Enable in S mode
-    val LPE   = RO(     2).withReset(0.U) // Zicfilp extension
-    val FIOM  = RO(     0).withReset(0.U) // Fence of I/O implies Memory
+    // Set all fields not supported as RO in base class
+    val STCE  =      RO(    63)           .withReset(0.U) // Sstc Enable
+    val PBMTE =      RO(    62)           .withReset(0.U) // Svpbmt Enable
+    val ADUE  =      RO(    61)           .withReset(0.U) // Svadu extension Enable
+    val PMM   =      RO(33, 32)           .withReset(0.U) // Smnpm extension
+    val CBZE  =      RW(     7)           .withReset(1.U) // Zicboz extension
+    val CBCFE =      RW(     6)           .withReset(1.U) // Zicbom extension
+    val CBIE  = EnvCBIE( 5,  4, wNoEffect).withReset(EnvCBIE.Inval) // Zicbom extension
+    val SSE   =      RO(     3)           .withReset(0.U) // Zicfiss extension Enable in S mode
+    val LPE   =      RO(     2)           .withReset(0.U) // Zicfilp extension
+    val FIOM  =      RO(     0)           .withReset(0.U) // Fence of I/O implies Memory
   }
 
   class PrivState extends Bundle { self =>
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala
index 816dd7ca445..97c898e71d7 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala
@@ -178,6 +178,14 @@ object CSRDefines {
     override def isLegal(enumeration: CSREnumType): Bool = enumeration.isOneOf(Bare, Sv39x4, Sv48x4)
   }
 
+  object EnvCBIE extends CSREnum with WARLApply {
+    val Off   = Value("b00".U)
+    val Flush = Value("b01".U)
+    val Inval = Value("b11".U)
+
+    override def isLegal(enumeration: CSREnumType): Bool = enumeration.isOneOf(Off, Flush, Inval)
+  }
+
   object ReflectHelper {
     val mirror: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 9f5a7beaf0d..a0f13cdc188 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -9,7 +9,7 @@ import top.{ArgParser, Generator}
 import utility.{DataHoldBypass, DelayN, GatedValidRegNext, RegNextWithEnable, SignExt, ZeroExt}
 import utils.{HPerfMonitor, OptionWrapper, PerfEvent}
 import xiangshan.backend.fu.NewCSR.CSRBundles.{CSRCustomState, PrivState, RobCommitCSR}
-import xiangshan.backend.fu.NewCSR.CSRDefines.{ContextStatus, HgatpMode, PrivMode, SatpMode, VirtMode}
+import xiangshan.backend.fu.NewCSR.CSRDefines._
 import xiangshan.backend.fu.NewCSR.CSREnumTypeImplicitCast._
 import xiangshan.backend.fu.NewCSR.CSREvents.{CSREvents, DretEventSinkBundle, EventUpdatePrivStateOutput, MNretEventSinkBundle, MretEventSinkBundle, SretEventSinkBundle, TargetPCBundle, TrapEntryDEventSinkBundle, TrapEntryEventInput, TrapEntryHSEventSinkBundle, TrapEntryMEventSinkBundle, TrapEntryMNEventSinkBundle, TrapEntryVSEventSinkBundle}
 import xiangshan.backend.fu.fpu.Bundles.Frm
@@ -315,7 +315,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   when(nonMaskableIRP.NMI) {
     nmip.NMI := true.B
   }
-  
+
   intrMod.io.in.nmi := nmip.asUInt.orR
   intrMod.io.in.nmiVec := nmip.asUInt
 
@@ -634,7 +634,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     println(s"${mod.modName}: ")
     println(mod.dumpFields)
   }
-  
+
   trapEntryMEvent.valid  := hasTrap && entryPrivState.isModeM && !entryDebugMode  && !debugMode && !nmi
   trapEntryMNEvent.valid := hasTrap && nmi && !debugMode
   trapEntryHSEvent.valid := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode
@@ -1177,6 +1177,29 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.toDecode.illegalInst.wfi        := isModeHU || !isModeM && mstatus.regOut.TW
   io.toDecode.virtualInst.wfi        := isModeVS && !mstatus.regOut.TW && hstatus.regOut.VTW || isModeVU && !mstatus.regOut.TW
   io.toDecode.illegalInst.frm        := frmIsReserved
+  // Ref: The RISC-V Instruction Set Manual Volume I - 20.5. Control and Status Register State
+  io.toDecode.illegalInst.cboZ       := !isModeM && !menvcfg.regOut.CBZE || isModeHU && !senvcfg.regOut.CBZE
+  io.toDecode.virtualInst.cboZ       := menvcfg.regOut.CBZE && (
+    isModeVS && !henvcfg.regOut.CBZE ||
+    isModeVU && !(henvcfg.regOut.CBZE && senvcfg.regOut.CBZE)
+  )
+  io.toDecode.illegalInst.cboCF      := !isModeM && !menvcfg.regOut.CBCFE || isModeHU && !senvcfg.regOut.CBCFE
+  io.toDecode.virtualInst.cboCF      := menvcfg.regOut.CBCFE && (
+    isModeVS && !henvcfg.regOut.CBCFE ||
+    isModeVU && !(henvcfg.regOut.CBCFE && senvcfg.regOut.CBCFE)
+  )
+  io.toDecode.illegalInst.cboI       :=
+    !isModeM && menvcfg.regOut.CBIE === EnvCBIE.Off ||
+    isModeHU && senvcfg.regOut.CBIE === EnvCBIE.Off
+  io.toDecode.virtualInst.cboI       := menvcfg.regOut.CBIE =/= EnvCBIE.Off && (
+    isModeVS && henvcfg.regOut.CBIE === EnvCBIE.Off ||
+    isModeVU &&(henvcfg.regOut.CBIE === EnvCBIE.Off || senvcfg.regOut.CBIE === EnvCBIE.Off)
+  )
+  io.toDecode.special.cboI2F := !io.toDecode.illegalInst.cboI && !io.toDecode.virtualInst.cboI && (
+    menvcfg.regOut.CBIE === EnvCBIE.Flush && !isModeM ||
+    senvcfg.regOut.CBIE === EnvCBIE.Flush && (isModeHU || isModeVU) ||
+    henvcfg.regOut.CBIE === EnvCBIE.Flush && (isModeVS || isModeVU)
+  )
 
   // Always instantiate basic difftest modules.
   if (env.AlwaysBasicDiff || env.EnableDifftest) {
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 99bdd2876f1..bd4e3c964e4 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -409,7 +409,26 @@ class CSRToDecode(implicit p: Parameters) extends XSBundle {
      * raise EX_II when frm.data > 4
      */
     val frm = Bool()
+
+    /**
+     * illegal CBO.ZERO
+     * raise [[EX_II]] when !isModeM && !MEnvCfg.CBZE || isModeHU && !SEnvCfg.CBZE
+     */
+    val cboZ = Bool()
+
+    /**
+     * illegal CBO.CLEAN/FLUSH
+     * raise [[EX_II]] when !isModeM && !MEnvCfg.CBCFE || isModeHU && !SEnvCfg.CBCFE
+     */
+    val cboCF = Bool()
+
+    /**
+     * illegal CBO.INVAL
+     * raise [[EX_II]] when !isModeM && MEnvCfg.CBIE = EnvCBIE.Off || isModeHU && SEnvCfg.CBIE = EnvCBIE.Off
+     */
+    val cboI = Bool()
   }
+
   val virtualInst = new Bundle {
     /**
      * illegal sfence.vma, svinval.vma
@@ -440,5 +459,37 @@ class CSRToDecode(implicit p: Parameters) extends XSBundle {
      * raise EX_VI when isModeVU && mstatus.TW=0 || isModeVS && mstatus.TW=0 && hstatus.VTW=1
      */
     val wfi = Bool()
+
+    /**
+     * illegal CBO.ZERO
+     * raise [[EX_VI]] when MEnvCfg.CBZE && (isModeVS && !HEnvCfg.CBZE || isModeVU && (!HEnvCfg.CBZE || !SEnvCfg.CBZE))
+     */
+    val cboZ = Bool()
+
+    /**
+     * illegal CBO.CLEAN/FLUSH
+     * raise [[EX_VI]] when MEnvCfg.CBZE && (isModeVS && !HEnvCfg.CBCFE || isModeVU && (!HEnvCfg.CBCFE || !SEnvCfg.CBCFE))
+     */
+    val cboCF = Bool()
+
+    /**
+     * illegal CBO.INVAL <br/>
+     * raise [[EX_VI]] when MEnvCfg.CBIE =/= EnvCBIE.Off && ( <br/>
+     *   isModeVS && HEnvCfg.CBIE === EnvCBIE.Off || <br/>
+     *   isModeVU && (HEnvCfg.CBIE === EnvCBIE.Off || SEnvCfg.CBIE === EnvCBIE.Off) <br/>
+     * ) <br/>
+     */
+    val cboI = Bool()
+  }
+
+  val special = new Bundle {
+    /**
+     * execute CBO.INVAL and perform flush operation when <br/>
+     * isModeHS && MEnvCfg.CBIE === EnvCBIE.Flush || <br/>
+     * isModeHU && (MEnvCfg.CBIE === EnvCBIE.Flush || SEnvCfg.CBIE === EnvCBIE.Flush) <br/>
+     * isModeVS && (MEnvCfg.CBIE === EnvCBIE.Flush || HEnvCfg.CBIE === EnvCBIE.Flush) <br/>
+     * isModeVU && (MEnvCfg.CBIE === EnvCBIE.Flush || HEnvCfg.CBIE === EnvCBIE.Flush || SEnvCfg.CBIE === EnvCBIE.Flush) <br/>
+     */
+    val cboI2F = Bool()
   }
 }
\ No newline at end of file
```
