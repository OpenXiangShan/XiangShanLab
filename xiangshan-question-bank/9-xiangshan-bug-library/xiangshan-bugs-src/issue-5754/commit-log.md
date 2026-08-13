# Commit Log
- Issue: #5754
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5754
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5754
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5754
- Changed files: 4
- Additions: 54
- Deletions: 25

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
index f46aecdb389..c6eb238c736 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryDEvent.scala
@@ -30,6 +30,7 @@ class TrapEntryDEventInput(implicit override val p: Parameters) extends TrapEntr
   val hasSingleStep                = Input(Bool())
   val breakPoint                   = Input(Bool())
   val criticalErrorStateEnterDebug = Input(Bool())
+  val holdDpc                      = Input(Bool())
 }
 
 class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSREventBase with DebugMMIO {
@@ -79,7 +80,7 @@ class TrapEntryDEventModule(implicit val p: Parameters) extends Module with CSRE
   out := DontCare
   // output
   out.dcsr.valid              := valid
-  out.dpc.valid               := valid
+  out.dpc.valid               := valid && !in.holdDpc
   // !debugMode trap || debugMode hasExp
   out.targetPc.valid          := valid || hasExceptionInDmode
   out.debugMode.valid         := valid
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
index 0197fdd31f6..8a43a77f741 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
@@ -249,13 +249,24 @@ abstract class BaseTrigger()(implicit val p: Parameters) extends Module with Has
 
   val tiggerVaddrHit = Mux(isCacheLine, cacheLineEq, Mux(isVectorStride, hitVecVectorStride, triggerHitVec))
   TriggerCheckCanFire(TriggerNum, triggerCanFireVec, tiggerVaddrHit, triggerTimingVec, triggerChainVec)
-  val triggerFireOH = PriorityEncoderOH(triggerCanFireVec)
-  val triggerVaddr  = PriorityMux(triggerFireOH, VecInit(tdataVec.map(_.tdata2))).asUInt
-  val triggerMask   = PriorityMux(triggerFireOH, VecInit(tdataVec.map(x => UIntToOH(x.tdata2(lowBitWidth-1, 0))))).asUInt
 
   val actionVec = VecInit(tdataVec.map(_.action))
   val triggerAction = Wire(TriggerAction())
-  TriggerUtil.triggerActionGen(triggerAction, triggerCanFireVec, actionVec, triggerCanRaiseBpExp)
+  val fireDebugModeVec = TriggerUtil.triggerActionMatchVec(triggerCanFireVec, actionVec, TriggerAction.DebugMode)
+  val fireBreakpointExpVec = TriggerUtil.triggerActionMatchVec(triggerCanFireVec, actionVec, TriggerAction.BreakpointExp)
+  val fireDebugMode = fireDebugModeVec.asUInt.orR
+  val breakPointExp = fireBreakpointExpVec.asUInt.orR && triggerCanRaiseBpExp
+  triggerAction := MuxCase(TriggerAction.None, Seq(
+    fireDebugMode -> TriggerAction.DebugMode,
+    breakPointExp -> TriggerAction.BreakpointExp,
+  ))
+  val triggerFireVec = MuxCase(VecInit(Seq.fill(TriggerNum)(false.B)), Seq(
+    fireDebugMode -> fireDebugModeVec,
+    breakPointExp -> fireBreakpointExpVec,
+  ))
+  val triggerFireOH = PriorityEncoderOH(triggerFireVec)
+  val triggerVaddr  = Mux(triggerFireVec.asUInt.orR, PriorityMux(triggerFireOH, VecInit(tdataVec.map(_.tdata2))).asUInt, 0.U(VAddrBits.W))
+  val triggerMask   = Mux(triggerFireVec.asUInt.orR, PriorityMux(triggerFireOH, VecInit(tdataVec.map(x => UIntToOH(x.tdata2(lowBitWidth-1, 0))))).asUInt, 0.U((VLEN/8).W))
 
   io.toLoadStore.triggerAction := triggerAction
   io.toLoadStore.triggerVaddr  := triggerVaddr
@@ -340,4 +351,4 @@ class VSegmentTrigger(override implicit val p: Parameters) extends BaseTrigger {
   def DcacheLineBitsEq(): (Bool, Vec[Bool]) = {
     (false.B, VecInit(Seq.fill(tdataVec.length)(false.B)))
   }
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index 0b2d95b9600..8c3b1fd18d6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -45,7 +45,7 @@ trait DebugLevel { self: NewCSR =>
   val tdata1RegVec: Seq[CSRModule[_]] = Range(0, TriggerNum).map(i =>
     Module(new CSRModule(s"Trigger$i" + s"_Tdata1", new Tdata1Bundle) with HasTriggerBundle {
       when(wen){
-        reg := wdata.writeTdata1(canWriteDmode, chainable).asUInt
+        reg := wdata.writeTdata1(canWriteDmode, chainable, dmodeNextTrigger).asUInt
       }
     })
   )
@@ -131,7 +131,7 @@ class Tdata1Bundle extends CSRBundle{
     res.ACTION
   }
 
-  def writeTdata1(canWriteDmode: Bool, chainable: Bool): Tdata1Bundle = {
+  def writeTdata1(canWriteDmode: Bool, chainable: Bool, dmodeNextTrigger: Bool): Tdata1Bundle = {
     val res = Wire(new Tdata1Bundle)
     res := this.asUInt
     val dmode = this.DMODE.asBool && canWriteDmode
@@ -140,7 +140,8 @@ class Tdata1Bundle extends CSRBundle{
     when(this.TYPE.isLegal) {
       val mcontrol6Res = Wire(new Mcontrol6)
       mcontrol6Res := this.DATA.asUInt
-      res.DATA := mcontrol6Res.writeData(dmode, chainable).asUInt
+      val chain = chainable && !(!dmode && dmodeNextTrigger)
+      res.DATA := mcontrol6Res.writeData(dmode, chain).asUInt
     }.otherwise{
       res.DATA := 0.U
     }
@@ -322,6 +323,7 @@ trait HasTdataSink { self: CSRModule[_] =>
 trait HasTriggerBundle { self: CSRModule[_] =>
   val canWriteDmode = IO(Input(Bool()))
   val chainable = IO(Input(Bool()))
+  val dmodeNextTrigger = IO(Input(Bool()))
 }
 
 trait HasNmipBundle { self: CSRModule[_] =>
@@ -362,6 +364,12 @@ object TriggerUtil {
     !ConsecutiveOnes(chainVec, chainLen)
   }
 
+  def triggerActionMatchVec(triggerCanFireVec: Vec[Bool], actionVec: Vec[UInt], targetAction: UInt): Vec[Bool] = {
+    VecInit(triggerCanFireVec.zip(actionVec).map {
+      case (canFire, action) => canFire && (action === targetAction)
+    })
+  }
+
   /**
    * Generate Trigger action
    * @return triggerAction return
@@ -370,19 +378,15 @@ object TriggerUtil {
    * @param  triggerCanRaiseBpExp from csr
    */
   def triggerActionGen(triggerAction: UInt, triggerCanFireVec: Vec[Bool], actionVec: Vec[UInt], triggerCanRaiseBpExp: Bool): Unit = {
-    // More than one triggers can hit at the same time, but only fire one.
-    // We select the first hit trigger to fire.
-    val hasTriggerFire    = triggerCanFireVec.asUInt.orR
-    val triggerFireOH     = PriorityEncoderOH(triggerCanFireVec)
-    val triggerFireAction = PriorityMux(triggerFireOH, actionVec).asUInt
-    val actionIsBPExp     = hasTriggerFire && (triggerFireAction === TrigAction.BreakpointExp.asUInt)
-    val actionIsDmode     = hasTriggerFire && (triggerFireAction === TrigAction.DebugMode.asUInt)
-    val breakPointExp     = actionIsBPExp && triggerCanRaiseBpExp
+    val fireDebugModeVec = triggerActionMatchVec(triggerCanFireVec, actionVec, TriggerAction.DebugMode)
+    val fireBreakpointExpVec = triggerActionMatchVec(triggerCanFireVec, actionVec, TriggerAction.BreakpointExp)
+    val fireDebugMode = fireDebugModeVec.asUInt.orR
+    val breakPointExp = fireBreakpointExpVec.asUInt.orR && triggerCanRaiseBpExp
 
     // todo: add more for trace
     triggerAction := MuxCase(TriggerAction.None, Seq(
+      fireDebugMode -> TriggerAction.DebugMode,
       breakPointExp -> TriggerAction.BreakpointExp,
-      actionIsDmode -> TriggerAction.DebugMode,
     ))
   }
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index b8f06b5f703..37b794a2a2f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -273,7 +273,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   val debugMode = RegInit(false.B)
   private val nextV = WireInit(VirtMode(0), VirtMode.Off)
   V := nextV
-  // dcsr stopcount 
+  // dcsr stopcount
   val debugModeStopCountNext = debugMode && dcsr.regOut.STOPCOUNT
   val debugModeStopTimeNext  = debugMode && dcsr.regOut.STOPTIME
   val debugModeStopCount = RegNext(debugModeStopCountNext)
@@ -281,6 +281,11 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   val criticalErrorStateInCSR = Wire(Bool())
   val criticalErrorState = RegEnable(true.B, false.B, io.fromTop.criticalErrorState || criticalErrorStateInCSR)
+  // When cetrig is 1, resuming from DebugMode following an entry due to a critical
+  // error will result in an immediate re-entry into Debug Mode due to the critical error.
+  // Ensure that dpc remains unchanged when criticalErrorState causes a re-entry into dmode,
+  // since the PC fetched from pcmem for updating dpc is random in this case.
+  val holdDpc = RegEnable(criticalErrorState && dcsr.regOut.CETRIG, false.B, dretEvent.valid)
 
   private val privState = Wire(new PrivState)
   privState.PRVM := PRVM
@@ -930,7 +935,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   val addrInPerfCnt = (wenLegal || ren) && (
     (addr >= CSRs.mcycle.U) && (addr <= CSRs.mhpmcounter31.U) ||
     (addr >= CSRs.cycle.U) && (addr <= CSRs.hpmcounter31.U)
-  ) || 
+  ) ||
   ren && (
     (addr === CSRs.vstopi.U) || (addr === CSRs.vstopei.U) ||
     (addr === CSRs.stopi.U) || (addr === CSRs.stopei.U) ||
@@ -1193,6 +1198,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapEntryDEvent.in.hasDebugEbreakException      := debugMod.io.out.hasDebugEbreakException
   trapEntryDEvent.in.breakPoint                   := debugMod.io.out.breakPoint
   trapEntryDEvent.in.criticalErrorStateEnterDebug := debugMod.io.out.criticalErrorStateEnterDebug
+  trapEntryDEvent.in.holdDpc                      := holdDpc
 
   for(idx <- 0 until TriggerNum) {
     val tdata1Pre = Wire(new Tdata1Bundle)
@@ -1200,11 +1206,18 @@ class NewCSR(implicit val p: Parameters) extends Module
     tdata1Pre := (if (idx > 0) tdata1RegVec(idx - 1) else tdata1RegVec(idx)).rdata.asUInt
     mcontrol6Pre := tdata1Pre.DATA.asUInt
     val canWriteDmode = WireInit(false.B)
-    canWriteDmode := (if(idx > 0) (Mux(mcontrol6Pre.CHAIN.asBool, tdata1Pre.DMODE.asBool && tdata1Pre.TYPE.isLegal, true.B)) && debugMode else debugMode).asBool
+    canWriteDmode := (if (idx > 0) (Mux(mcontrol6Pre.CHAIN.asBool, tdata1Pre.DMODE.asBool && tdata1Pre.TYPE.isLegal, true.B)) && debugMode else debugMode).asBool
+
+    val tdata1Next = Wire(new Tdata1Bundle)
+    tdata1Next := (if (idx < TriggerNum - 1) tdata1RegVec(idx + 1) else tdata1RegVec(idx)).rdata.asUInt
+    val dmodeNextTrigger = WireInit(false.B)
+    dmodeNextTrigger := (if (idx < TriggerNum - 1) tdata1Next.TYPE.isLegal && tdata1Next.DMODE.asBool else false.B)
+
     tdata1RegVec(idx) match {
       case m: HasTriggerBundle =>
         m.canWriteDmode := canWriteDmode
         m.chainable := debugMod.io.out.newTriggerChainIsLegal
+        m.dmodeNextTrigger := dmodeNextTrigger
       case _ =>
     }
   }
@@ -1250,7 +1263,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     Seq(mtval.rdata,       stval.rdata,        vstval.rdata)
   )
   io.status.traceCSR.mstatus  := mstatus.regOut.asUInt
-  
+
   /**
    * perf_begin
    * perf number: 29 (frontend 8, ctrlblock 8, memblock 8, huancun 5)
@@ -1271,7 +1284,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   val countingEn        = RegInit(0.U.asTypeOf(Vec(perfCntNum, Bool())))
   val ofFromPerfCntVec  = Wire(Vec(perfCntNum, Bool()))
   val lcofiReqVec       = Wire(Vec(perfCntNum, Bool()))
-  
+
   for(i <- 0 until perfCntNum) {
     mhpmcounters(i) match {
       case m: HasPerfCounterBundle =>
@@ -1286,7 +1299,7 @@ class NewCSR(implicit val p: Parameters) extends Module
         m.ofFromPerfCnt := ofFromPerfCntVec(i)
       case _ =>
     }
-    
+
     val mhpmevent = Wire(new MhpmeventBundle)
     mhpmevent := mhpmevents(i).rdata
     lcofiReqVec(i) := ofFromPerfCntVec(i) && !mhpmevent.OF.asBool
```
