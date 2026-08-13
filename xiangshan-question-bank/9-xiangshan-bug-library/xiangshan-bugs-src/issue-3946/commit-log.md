# Commit Log
- Issue: #3946
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3946
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3946
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3946
- Changed files: 4
- Additions: 154
- Deletions: 58

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 1a94f43352a..57c630af114 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -2,7 +2,7 @@ package xiangshan.backend.fu.NewCSR
 
 import chisel3._
 import chisel3.util._
-import utility.DelayN
+import utility.{DelayN, GatedValidRegNext}
 import utils._
 import xiangshan.ExceptionNO
 import xiangshan.backend.fu.NewCSR.CSRBundles.{CauseBundle, PrivState, XtvecBundle}
@@ -59,13 +59,16 @@ class InterruptFilter extends Module {
   val mtopigather = mip & mie & (~mideleg).asUInt
   val hstopigather = hsip & hsie & (~hideleg).asUInt
   val vstopigather = vsip & vsie
-  val mipriosSort: Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
+  val mipriosSort:  Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
   val hsipriosSort: Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
   val hvipriosSort: Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
+  val indexSort   : Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(6.W)))
+
   InterruptNO.interruptDefaultPrio.zipWithIndex.foreach { case (value, index) =>
     mipriosSort(index)  := Mux(mtopigather(value), Cat(1.U, miprios(7 + 8 * value, 8 * value)), 0.U)
     hsipriosSort(index) := Mux(hstopigather(value), Cat(1.U, hsiprios(7 + 8 * value, 8 * value)), 0.U)
     hvipriosSort(index) := Mux(vstopigather(value), Cat(1.U, 0.U(8.W)), 0.U)
+    indexSort(index) := index.U
   }
   hvipriosSort(findIndex(1.U)) := Mux(vstopigather(1).asBool, Cat(1.U, hviprio1.PrioSSI.asUInt), 0.U)
   hvipriosSort(findIndex(5.U)) := Mux(vstopigather(5).asBool, Cat(1.U, hviprio1.PrioSTI.asUInt), 0.U)
@@ -154,29 +157,85 @@ class InterruptFilter extends Module {
     }
   }
 
-  def highIprio(iprios: Vec[UInt], xei: UInt = 0.U): (UInt, UInt) = {
-    val index = WireInit(VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(6.W))))
-    InterruptNO.interruptDefaultPrio.zipWithIndex.foreach { case (prio, i) =>
-      index(i) := i.U
-    }
+  def highIprio(index: Vec[UInt], iprios: Vec[UInt], xei: UInt = 0.U): (UInt, UInt, UInt) = {
     val result = minSelect(index, iprios, xei)
-    (result._1(0), result._2(0)(7, 0))
+    (result._1(0), result._2(0)(8), result._2(0)(7, 0))
+  }
+
+  private val indexTmp = VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(6.W)))))
+  (0 until 8).foreach { i =>
+    val end = math.min(8*(i+1), InterruptNO.interruptDefaultPrio.size)
+    val slice = indexSort.slice(8*i, end).map(_.asUInt)
+    val paddingSlice = slice ++ Seq.fill(8 - slice.length)(0.U(6.W))
+    indexTmp(i) := VecInit(paddingSlice)
+  }
+
+  private val mipriosSortTmp = VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(9.W)))))
+  (0 until 8).foreach { i =>
+    val end = math.min(8*(i+1), InterruptNO.interruptDefaultPrio.size)
+    val slice = mipriosSort.slice(8*i, end).map(_.asUInt)
+    val paddingSlice = slice ++ Seq.fill(8 - slice.length)(0.U(9.W))
+    mipriosSortTmp(i) := VecInit(paddingSlice)
+  }
+
+  private val hsipriosSortTmp = VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(9.W)))))
+  (0 until 8).foreach { i =>
+    val end = math.min(8*(i+1), InterruptNO.interruptDefaultPrio.size)
+    val slice = hsipriosSort.slice(8 * i, end).map(_.asUInt)
+    val paddingSlice = slice ++ Seq.fill(8 - slice.length)(0.U(9.W))
+    hsipriosSortTmp(i) := VecInit(paddingSlice)
+  }
+
+  private val mIidIdx   = VecInit(Seq.fill(8)(0.U(6.W)))
+  private val hsIidIdx  = VecInit(Seq.fill(8)(0.U(6.W)))
+  private val mEnable   = VecInit(Seq.fill(8)(0.U(1.W)))
+  private val hsEnable  = VecInit(Seq.fill(8)(0.U(1.W)))
+  private val mPrioNum  = VecInit(Seq.fill(8)(0.U(8.W)))
+  private val hsPrioNum = VecInit(Seq.fill(8)(0.U(8.W)))
+
+  indexTmp.zip(mipriosSortTmp).zipWithIndex.foreach { case ((index, iprios), i) =>
+    val (iidTmp, enableTmp, prioTmp) = highIprio(index, iprios, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U)
+    mIidIdx(i) := iidTmp
+    mEnable(i) := enableTmp
+    mPrioNum(i) := prioTmp
   }
 
-  private val (mIidIdx,  mPrioNum)  = highIprio(mipriosSort, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U)
-  private val (hsIidIdx, hsPrioNum) = highIprio(hsipriosSort, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U)
 
-  private val mIidNum  = findNum(mIidIdx)
-  private val hsIidNum = findNum(hsIidIdx)
+  indexTmp.zip(hsipriosSortTmp).zipWithIndex.foreach { case ((index, iprios), i) =>
+    val (iidTmp, enableTmp, prioTmp) = highIprio(index, iprios, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U)
+    hsIidIdx(i) := iidTmp
+    hsEnable(i) := enableTmp
+    hsPrioNum(i) := prioTmp
+  }
+
+  private val mIndexReg = RegInit(VecInit(Seq.fill(8)(0.U(6.W))))
+  (0 until 8).foreach(i => mIndexReg(i) := mIidIdx(i))
+
+  private val hsIndexReg = RegInit(VecInit(Seq.fill(8)(0.U(6.W))))
+  (0 until 8).foreach(i => hsIndexReg(i) := hsIidIdx(i))
+
+  private val mipriosSortReg = RegInit(VecInit(Seq.fill(8)(0.U(9.W))))
+  (0 until 8).foreach(i => mipriosSortReg(i) := Cat(mEnable(i), mPrioNum(i)))
 
-  private val mIidDefaultPrioHighMEI: Bool = mIidIdx < InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
-  private val mIidDefaultPrioLowMEI : Bool = mIidIdx > InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
+  private val hsipriosSortReg = RegInit(VecInit(Seq.fill(8)(0.U(9.W))))
+  (0 until 8).foreach(i => hsipriosSortReg(i) := Cat(hsEnable(i), hsPrioNum(i)))
 
-  private val hsIidDefaultPrioHighSEI: Bool = hsIidIdx < InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
-  private val hsIidDefaultPrioLowSEI : Bool = hsIidIdx > InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
 
-  val mtopiPrioNumReal = mPrioNum
-  val stopiPrioNumReal = hsPrioNum
+  private val (mIidIdxReg, mEnableReg, mPrioNumReg)  = highIprio(mIndexReg, mipriosSortReg, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U)
+  private val (hsIidIdxReg, hsEnableReg, hsPrioNumReg) = highIprio(hsIndexReg, hsipriosSortReg, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U)
+
+
+  private val mIidNum  = findNum(mIidIdxReg)
+  private val hsIidNum = findNum(hsIidIdxReg)
+
+  private val mIidDefaultPrioHighMEI: Bool = mIidIdxReg < InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
+  private val mIidDefaultPrioLowMEI : Bool = mIidIdxReg > InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
+
+  private val hsIidDefaultPrioHighSEI: Bool = hsIidIdxReg < InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
+  private val hsIidDefaultPrioLowSEI : Bool = hsIidIdxReg > InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
+
+  val mtopiPrioNumReal = mPrioNumReg
+  val stopiPrioNumReal = hsPrioNumReg
 
   // update mtopi
   io.out.mtopi.IID := Mux(mtopiIsNotZero, mIidNum, 0.U)
@@ -221,9 +280,35 @@ class InterruptFilter extends Module {
   assert(PopCount(Cat(Candidate1, Candidate2, Candidate3)) < 2.U, "Only one Candidate could be select from Candidate1/2/3 in VS-level!")
   assert(PopCount(Cat(Candidate4, Candidate5)) < 2.U, "Only one Candidate could be select from Candidate4/5 in VS-level!")
 
-  private val (vsIidIdx, vsPrioNum) = highIprio(hvipriosSort, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.VSEI).U)
+  private val hvipriosSortTmp = VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(9.W)))))
+  (0 until 8).foreach { i =>
+    val end = math.min(8*(i+1), InterruptNO.interruptDefaultPrio.size)
+    val slice = hvipriosSort.slice(8*i, end).map(_.asUInt)
+    val paddingSlice = slice ++ Seq.fill(8 - slice.length)(0.U(9.W))
+    hvipriosSortTmp(i) := VecInit(paddingSlice)
+  }
+
+  private val vsIidIdx  = VecInit(Seq.fill(8)(0.U(6.W)))
+  private val vsEnable  = VecInit(Seq.fill(8)(0.U(1.W)))
+  private val vsPrioNum = VecInit(Seq.fill(8)(0.U(8.W)))
+
+  indexTmp.zip(hvipriosSortTmp).zipWithIndex.foreach { case ((index, iprios), i) =>
+    val (iidTmp, enableTmp, prioTmp) = highIprio(index, iprios, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.VSEI).U)
+    vsIidIdx(i) := iidTmp
+    vsEnable(i) := enableTmp
+    vsPrioNum(i) := prioTmp
+  }
+
+  private val vsIndexReg = RegInit(VecInit(Seq.fill(8)(0.U(6.W))))
+  (0 until 8).foreach(i => vsIndexReg(i) := vsIidIdx(i))
+
+  private val hvipriosSortReg = RegInit(VecInit(Seq.fill(8)(0.U(9.W))))
+  (0 until 8).foreach(i => hvipriosSortReg(i) := Cat(vsEnable(i), vsPrioNum(i)))
+
+  private val (vsIidIdxReg, vsEnableReg, vsPrioNumReg) = highIprio(vsIndexReg, hvipriosSortReg, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.VSEI).U)
+
+  private val vsIidNum = findNum(vsIidIdxReg)
 
-  private val vsIidNum = findNum(vsIidIdx)
 
   val iidCandidate123   = Wire(UInt(12.W))
   val iidCandidate45    = Wire(UInt(12.W))
@@ -240,7 +325,7 @@ class InterruptFilter extends Module {
     Candidate5 -> hvictl.IID.asUInt,
   ))
   iprioCandidate45 := Mux1H(Seq(
-    Candidate4 -> vsPrioNum,
+    Candidate4 -> vsPrioNumReg,
     Candidate5 -> hvictl.IPRIO.asUInt,
   ))
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index feeb3d5ab30..fac24ee8825 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -80,6 +80,7 @@ class NewCSRInput(implicit p: Parameters) extends Bundle {
   val ren = Bool()
   val op = UInt(2.W)
   val addr = UInt(12.W)
+  val waddrReg = UInt(12.W)
   val src = UInt(64.W)
   val wdata = UInt(64.W)
   val mnret = Input(Bool())
@@ -232,11 +233,13 @@ class NewCSR(implicit val p: Parameters) extends Module
   /* Alias of input signals */
   val wen   = io.in.bits.wen && valid
   val addr  = io.in.bits.addr
-  val wdata = io.in.bits.wdata
 
   val ren   = io.in.bits.ren && valid
   val raddr = io.in.bits.addr
 
+  val waddrReg = io.in.bits.waddrReg
+  val wdataReg = io.in.bits.wdata
+
   val hasTrap = io.fromRob.trap.valid
   val trapVec = io.fromRob.trap.bits.trapVec
   val trapPC = io.fromRob.trap.bits.pc
@@ -292,6 +295,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   val legalMNret = permitMod.io.out.hasLegalMNret
   val legalDret  = permitMod.io.out.hasLegalDret
 
+  private val wenLegalReg = GatedValidRegNext(wenLegal)
+  private val isModeVSReg = GatedValidRegNext(isModeVS)
+
   var csrRwMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] =
     machineLevelCSRMap ++
     supervisorLevelCSRMap ++
@@ -410,22 +416,23 @@ class NewCSR(implicit val p: Parameters) extends Module
   pmpEntryMod.io.in.pmpCfg  := cfgs.map(_.regOut.asInstanceOf[PMPCfgBundle])
   pmpEntryMod.io.in.pmpAddr := pmpaddr.map(_.regOut.asInstanceOf[PMPAddrBundle])
   pmpEntryMod.io.in.ren   := ren
-  pmpEntryMod.io.in.wen   := wenLegal
+  pmpEntryMod.io.in.wen   := wenLegalReg
   pmpEntryMod.io.in.addr  := addr
-  pmpEntryMod.io.in.wdata := wdata
+  pmpEntryMod.io.in.waddr := waddrReg
+  pmpEntryMod.io.in.wdata := wdataReg
 
   // Todo: all wen and wdata of CSRModule assigned in this for loop
   for ((id, (wBundle, _)) <- csrRwMap) {
     if (vsMapS.contains(id)) {
       // VS access CSR by S: privState.isModeVS && addrMappedToVS === sMapVS(id).U
-      wBundle.wen := wenLegal && ((isModeVS && addr === vsMapS(id).U) || (!isModeVS && addr === id.U))
-      wBundle.wdata := wdata
+      wBundle.wen := wenLegalReg && ((isModeVSReg && waddrReg === vsMapS(id).U) || (!isModeVSReg && waddrReg === id.U))
+      wBundle.wdata := wdataReg
     } else if (sMapVS.contains(id)) {
-      wBundle.wen := wenLegal && !isModeVS && addr === id.U
-      wBundle.wdata := wdata
+      wBundle.wen := wenLegalReg && !isModeVSReg && waddrReg === id.U
+      wBundle.wdata := wdataReg
     } else {
-      wBundle.wen := wenLegal && addr === id.U
-      wBundle.wdata := wdata
+      wBundle.wen := wenLegalReg && waddrReg === id.U
+      wBundle.wdata := wdataReg
     }
   }
 
@@ -486,23 +493,23 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   miregiprios.foreach { mod =>
     mod.w.wen := mireg.w.wen && (miselect.regOut.ALL.asUInt === mod.addr.U)
-    mod.w.wdata := wdata
+    mod.w.wdata := wdataReg
   }
 
   siregiprios.foreach { mod =>
     mod.w.wen := sireg.w.wen && (siselect.regOut.ALL.asUInt === mod.addr.U)
-    mod.w.wdata := wdata
+    mod.w.wdata := wdataReg
   }
 
   mhartid.hartid := this.io.fromTop.hartId
 
   cfgs.zipWithIndex.foreach { case (mod, i) =>
-    mod.w.wen := wenLegal && (addr === (0x3A0 + i / 8 * 2).U)
+    mod.w.wen := wenLegalReg && (waddrReg === (0x3A0 + i / 8 * 2).U)
     mod.w.wdata := pmpEntryMod.io.out.pmpCfgWData(8*((i%8)+1)-1,8*(i%8))
   }
 
   pmpaddr.zipWithIndex.foreach{ case(mod, i) =>
-    mod.w.wen := wenLegal && (addr === (0x3B0 + i).U)
+    mod.w.wen := wenLegalReg && (waddrReg === (0x3B0 + i).U)
     mod.w.wdata := pmpEntryMod.io.out.pmpAddrWData(i)
   }
 
@@ -851,7 +858,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   )
 
   // flush
-  val resetSatp = Cat(Seq(satp, vsatp, hgatp).map(_.addr.U === addr)).orR && wenLegal // write to satp will cause the pipeline be flushed
+  val resetSatp = Cat(Seq(satp, vsatp, hgatp).map(_.addr.U === waddrReg)).orR && wenLegalReg // write to satp will cause the pipeline be flushed
 
   val floatStatusOnOff = mstatus.w.wen && (
     mstatus.w.wdataFields.FS === ContextStatus.Off && mstatus.regOut.FS =/= ContextStatus.Off ||
@@ -941,7 +948,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     is(s_idle) {
       when(valid && asyncAccess) {
         stateNext := s_waitIMSIC
-      }.elsewhen(valid && !io.out.ready) {
+      }.elsewhen(valid) {
         stateNext := s_finish
       }
     }
@@ -980,9 +987,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   val normalCSRValid = state === s_idle && valid && !asyncAccess
   val waitIMSICValid = state === s_waitIMSIC && fromAIA.rdata.valid
 
-  io.out.valid := normalCSRValid ||
-                  waitIMSICValid ||
-                  state === s_finish
+  io.out.valid := waitIMSICValid || state === s_finish
   io.out.bits.EX_II := DataHoldBypass(Mux1H(Seq(
     normalCSRValid -> (permitMod.io.out.EX_II || noCSRIllegal),
     waitIMSICValid -> imsic_EX_II,
@@ -991,7 +996,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     normalCSRValid -> permitMod.io.out.EX_VI,
     waitIMSICValid -> imsic_EX_VI,
   )), false.B, normalCSRValid || waitIMSICValid)
-  io.out.bits.flushPipe := DataHoldBypass(flushPipe, false.B, io.in.fire)
+  io.out.bits.flushPipe := flushPipe
 
   /** Prepare read data for output */
   io.out.bits.rData := DataHoldBypass(
@@ -1070,7 +1075,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   debugMod.io.in.tdata2Selected            := tdata2.rdata
   debugMod.io.in.tdata1Update              := tdata1Update
   debugMod.io.in.tdata2Update              := tdata2Update
-  debugMod.io.in.tdata1Wdata               := wdata
+  debugMod.io.in.tdata1Wdata               := wdataReg
   debugMod.io.in.triggerCanRaiseBpExp      := triggerCanRaiseBpExp
 
   entryDebugMode := debugMod.io.out.hasDebugTrap && !debugMode
@@ -1095,9 +1100,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   }
   tdata1RegVec.zip(tdata2RegVec).zipWithIndex.map { case ((mod1, mod2), idx) => {
     mod1.w.wen    := tdata1Update && (tselect.rdata === idx.U)
-    mod1.w.wdata  := wdata
+    mod1.w.wdata  := wdataReg
     mod2.w.wen    := tdata2Update && (tselect.rdata === idx.U)
-    mod2.w.wdata  := wdata
+    mod2.w.wdata  := wdataReg
   }}
 
   triggerFrontendChange := debugMod.io.out.triggerFrontendChange
@@ -1247,8 +1252,8 @@ class NewCSR(implicit val p: Parameters) extends Module
   toAIA.addr.bits.v    := imsicAddrPrivState.V
 
   toAIA.wdata.valid := imsicWdataValid
-  toAIA.wdata.bits.op := io.in.bits.op
-  toAIA.wdata.bits.data := io.in.bits.src
+  toAIA.wdata.bits.op := RegNext(io.in.bits.op)
+  toAIA.wdata.bits.data := RegNext(io.in.bits.src)
   toAIA.vgein := hstatus.regOut.VGEIN.asUInt
   toAIA.mClaim  := mtopei.w.wen
   toAIA.sClaim  := stopei.w.wen
@@ -1320,7 +1325,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     henvcfg.regOut.CBIE === EnvCBIE.Flush && (isModeVS || isModeVU)
   )
 
-  io.distributedWenLegal := wenLegal
+  io.distributedWenLegal := wenLegalReg
   io.status.criticalErrorState := criticalErrorState && !dcsr.regOut.CETRIG.asBool
 
   val criticalErrors = Seq(
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
index 0137e476fca..7a5acd19efd 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
@@ -23,6 +23,7 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
   val ren   = io.in.ren
   val wen   = io.in.wen
   val addr  = io.in.addr
+  val waddr = io.in.waddr
   val wdata = io.in.wdata
 
   val pmpMask  = RegInit(VecInit(Seq.fill(p(PMParameKey).NumPMP)(0.U(PMPAddrBits.W))))
@@ -35,7 +36,7 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
   // write pmpCfg
   val cfgVec = WireInit(VecInit(Seq.fill(8)(0.U.asTypeOf(new PMPCfgBundle))))
   for (i <- 0 until (p(PMParameKey).NumPMP/8+1) by 2) {
-    when (wen && (addr === (0x3A0 + i).U)) {
+    when (wen && (waddr === (0x3A0 + i).U)) {
       for (j <- cfgVec.indices) {
         val cfgOldTmp = pmpEntry(8*i/2+j).cfg
         val cfgNewTmp = Wire(new PMPCfgBundle)
@@ -64,7 +65,7 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
     pmpAddrW(i) := pmpEntry(i).addr.ADDRESS.asUInt
     pmpAddrR(i) := pmpEntry(i).addr.ADDRESS.asUInt
     // write pmpAddr
-    when (wen && (addr === (0x3B0 + i).U)) {
+    when (wen && (waddr === (0x3B0 + i).U)) {
       if (i != (p(PMParameKey).NumPMP - 1)) {
         val addrNextLocked: Bool = PMPCfgLField.addrLocked(pmpEntry(i).cfg, pmpEntry(i + 1).cfg)
         pmpMask(i) := Mux(!addrNextLocked, pmpEntry(i).matchMask(wdata), pmpEntry(i).mask)
@@ -91,6 +92,7 @@ class PMPEntryHandleIOBundle(implicit p: Parameters) extends PMPBundle {
     val wen   = Bool()
     val ren   = Bool()
     val addr  = UInt(12.W)
+    val waddr = UInt(12.W)
     val wdata = UInt(64.W)
     val pmpCfg  = Vec(NumPMP, new PMPCfgBundle)
     val pmpAddr = Vec(NumPMP, new PMPAddrBundle)
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 88d59b0a9cb..de2069e57bb 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -89,6 +89,9 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
     CSROpType.isCSRRSorRC(func)
   )
 
+  private val waddrReg = RegEnable(addr, 0.U(12.W), io.in.fire)
+  private val wdataReg = RegEnable(wdata, 0.U(64.W), io.in.fire)
+
   csrMod.io.in match {
     case in =>
       in.valid := valid
@@ -96,8 +99,9 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       in.bits.ren := csrRen
       in.bits.op  := CSROpType.getCSROp(func)
       in.bits.addr := addr
+      in.bits.waddrReg := waddrReg
       in.bits.src := src
-      in.bits.wdata := wdata
+      in.bits.wdata := wdataReg
       in.bits.mret := isMret
       in.bits.mnret := isMNret
       in.bits.sret := isSret
@@ -272,13 +276,13 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   /** initialize NewCSR's io_out_ready from wrapper's io */
   csrMod.io.out.ready := io.out.ready
 
-  io.out.bits.res.redirect.get.valid := io.out.valid && DataHoldBypass(isXRet, false.B, io.in.fire)
+  io.out.bits.res.redirect.get.valid := io.out.valid && RegEnable(isXRet, false.B, io.in.fire)
   val redirect = io.out.bits.res.redirect.get.bits
   redirect := 0.U.asTypeOf(redirect)
   redirect.level := RedirectLevel.flushAfter
-  redirect.robIdx := io.in.bits.ctrl.robIdx
-  redirect.ftqIdx := io.in.bits.ctrl.ftqIdx.get
-  redirect.ftqOffset := io.in.bits.ctrl.ftqOffset.get
+  redirect.robIdx := RegEnable(io.in.bits.ctrl.robIdx, io.in.fire)
+  redirect.ftqIdx := RegEnable(io.in.bits.ctrl.ftqIdx.get, io.in.fire)
+  redirect.ftqOffset := RegEnable(io.in.bits.ctrl.ftqOffset.get, io.in.fire)
   redirect.cfiUpdate.predTaken := true.B
   redirect.cfiUpdate.taken := true.B
   redirect.cfiUpdate.target := csrMod.io.out.bits.targetPc.pc
@@ -288,18 +292,18 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   // Only mispred will send redirect to frontend
   redirect.cfiUpdate.isMisPred := true.B
 
-  connectNonPipedCtrlSingalForCSR
+  connectNonPipedCtrlSingal
 
   override val criticalErrors = csrMod.getCriticalErrors
   generateCriticalErrors()
 
   // Todo: summerize all difftest skip condition
-  csrOut.isPerfCnt  := io.out.valid && csrMod.io.out.bits.isPerfCnt && DataHoldBypass(func =/= CSROpType.jmp, false.B, io.in.fire)
+  csrOut.isPerfCnt  := io.out.valid && csrMod.io.out.bits.isPerfCnt && RegEnable(func =/= CSROpType.jmp, false.B, io.in.fire)
   csrOut.fpu.frm    := csrMod.io.status.fpState.frm.asUInt
   csrOut.vpu.vstart := csrMod.io.status.vecState.vstart.asUInt
   csrOut.vpu.vxrm   := csrMod.io.status.vecState.vxrm.asUInt
 
-  csrOut.isXRet := isXRetFlag
+  csrOut.isXRet := RegEnable(isXRetFlag, false.B, io.in.fire)
 
   csrOut.trapTarget := csrMod.io.out.bits.targetPc
   csrOut.interrupt := csrMod.io.status.interrupt
@@ -345,8 +349,8 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       // distribute csr write signal
       // write to frontend and memory
       custom.distribute_csr.w.valid := csrMod.io.distributedWenLegal
-      custom.distribute_csr.w.bits.addr := addr
-      custom.distribute_csr.w.bits.data := wdata
+      custom.distribute_csr.w.bits.addr := waddrReg
+      custom.distribute_csr.w.bits.data := wdataReg
       // rename single step
       custom.singlestep := csrMod.io.status.singleStepFlag
       // trigger
```
