# Commit Log
- Issue: #3683
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3683
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3683
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3683
- Changed files: 3
- Additions: 61
- Deletions: 61

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index eba54274f74..0a2d6e1b705 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -39,7 +39,7 @@ import org.chipsalliance.cde.config.Parameters
 import chisel3.util.BitPat.bitPatToUInt
 import chisel3.util.experimental.decode.EspressoMinimizer
 import xiangshan.backend.CtrlToFtqIO
-import xiangshan.backend.fu.NewCSR.{Mcontrol, Tdata1Bundle, Tdata2Bundle}
+import xiangshan.backend.fu.NewCSR.{Mcontrol6, Tdata1Bundle, Tdata2Bundle}
 import xiangshan.backend.fu.PMPEntry
 import xiangshan.frontend.Ftq_Redirect_SRAMEntry
 import xiangshan.frontend.AllFoldedHistories
@@ -753,16 +753,16 @@ class MatchTriggerIO(implicit p: Parameters) extends XSBundle {
   val tdata2    = Output(UInt(64.W))
 
   def GenTdataDistribute(tdata1: Tdata1Bundle, tdata2: Tdata2Bundle): MatchTriggerIO = {
-    val mcontrol = Wire(new Mcontrol)
-    mcontrol := tdata1.DATA.asUInt
-    this.matchType := mcontrol.MATCH.asUInt
-    this.select    := mcontrol.SELECT.asBool
-    this.timing    := mcontrol.TIMING.asBool
-    this.action    := mcontrol.ACTION.asUInt
-    this.chain     := mcontrol.CHAIN.asBool
-    this.execute   := mcontrol.EXECUTE.asBool
-    this.load      := mcontrol.LOAD.asBool
-    this.store     := mcontrol.STORE.asBool
+    val mcontrol6 = Wire(new Mcontrol6)
+    mcontrol6 := tdata1.DATA.asUInt
+    this.matchType := mcontrol6.MATCH.asUInt
+    this.select    := mcontrol6.SELECT.asBool
+    this.timing    := false.B
+    this.action    := mcontrol6.ACTION.asUInt
+    this.chain     := mcontrol6.CHAIN.asBool
+    this.execute   := mcontrol6.EXECUTE.asBool
+    this.load      := mcontrol6.LOAD.asBool
+    this.store     := mcontrol6.STORE.asBool
     this.tdata2    := tdata2.asUInt
     this
   }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
index 1f3434fcc5a..29bafeee133 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
@@ -57,10 +57,10 @@ class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
   val hasDebugEbreakException = isEbreak && ebreakEnterDebugMode
 
   // debug_exception_trigger
-  val mcontrolWireVec = tdata1Vec.map{ mod => {
-    val mcontrolWire = Wire(new Mcontrol)
-    mcontrolWire := mod.DATA.asUInt
-    mcontrolWire
+  val mcontrol6WireVec = tdata1Vec.map{ mod => {
+    val mcontrol6Wire = Wire(new Mcontrol6)
+    mcontrol6Wire := mod.DATA.asUInt
+    mcontrol6Wire
   }}
 
   val triggerCanRaiseBpExp = Mux(privState.isModeM, tcontrol.MTE.asBool, true.B)
@@ -73,38 +73,40 @@ class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
   val hasDebugTrap = hasDebugException || hasDebugIntr
 
   val tselect1H = UIntToOH(tselect.asUInt, TriggerNum).asBools
-  val chainVec = mcontrolWireVec.map(_.CHAIN.asBool)
+  val chainVec = mcontrol6WireVec.map(_.CHAIN.asBool)
   val newTriggerChainVec = tselect1H.zip(chainVec).map{case(a, b) => a | b}
   val newTriggerChainIsLegal = TriggerUtil.TriggerCheckChainLegal(newTriggerChainVec, TriggerChainMaxLength)
 
   val triggerUpdate = tdata1Update || tdata2Update
 
-  val mcontrolWdata = Wire(new Mcontrol)
-  mcontrolWdata := tdata1Wdata.DATA.asUInt
+  val mcontrol6Wdata = Wire(new Mcontrol6)
+  mcontrol6Wdata := tdata1Wdata.DATA.asUInt
   val tdata1TypeWdata = tdata1Wdata.TYPE
 
-  val mcontrolSelected = Wire(new Mcontrol)
-  mcontrolSelected := tdata1Selected.DATA.asUInt
+  val mcontrol6Selected = Wire(new Mcontrol6)
+  mcontrol6Selected := tdata1Selected.DATA.asUInt
 
   val frontendTriggerUpdate =
-    tdata1Update && tdata1TypeWdata.isLegal && mcontrolWdata.isFetchTrigger ||
-      mcontrolSelected.isFetchTrigger && triggerUpdate
+    tdata1Update && tdata1TypeWdata.isLegal && mcontrol6Wdata.isFetchTrigger ||
+      mcontrol6Selected.isFetchTrigger && triggerUpdate
 
   val memTriggerUpdate =
-    tdata1Update && tdata1TypeWdata.isLegal && mcontrolWdata.isMemAccTrigger ||
-      mcontrolSelected.isMemAccTrigger && triggerUpdate
+    tdata1Update && tdata1TypeWdata.isLegal && mcontrol6Wdata.isMemAccTrigger ||
+      mcontrol6Selected.isMemAccTrigger && triggerUpdate
 
-  val triggerEnableVec = tdata1Vec.zip(mcontrolWireVec).map { case(tdata1, mcontrol) =>
+  val triggerEnableVec = tdata1Vec.zip(mcontrol6WireVec).map { case(tdata1, mcontrol6) =>
     tdata1.TYPE.isLegal && (
-      mcontrol.M && privState.isModeM  ||
-        mcontrol.S && privState.isModeHS ||
-        mcontrol.U && privState.isModeHU)
+      mcontrol6.M && privState.isModeM  ||
+        mcontrol6.S && privState.isModeHS ||
+        mcontrol6.U && privState.isModeHU ||
+        mcontrol6.VS && privState.isModeVS ||
+        mcontrol6.VU && privState.isModeVU)
   }
 
-  val fetchTriggerEnableVec = triggerEnableVec.zip(mcontrolWireVec).map {
+  val fetchTriggerEnableVec = triggerEnableVec.zip(mcontrol6WireVec).map {
     case (tEnable, mod) => tEnable && mod.isFetchTrigger
   }
-  val memAccTriggerEnableVec = triggerEnableVec.zip(mcontrolWireVec).map {
+  val memAccTriggerEnableVec = triggerEnableVec.zip(mcontrol6WireVec).map {
     case (tEnable, mod) => tEnable && mod.isMemAccTrigger
   }
   
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index 3eac6af530e..d6fb5743f94 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -126,7 +126,7 @@ class Tdata1Bundle extends CSRBundle{
   val DATA    = RW(58, 0).withReset(0.U)
 
   def getTriggerAction: CSREnumType = {
-    val res = Wire(new Mcontrol)
+    val res = Wire(new Mcontrol6)
     res := this.asUInt
     res.ACTION
   }
@@ -138,9 +138,9 @@ class Tdata1Bundle extends CSRBundle{
     res.TYPE := this.TYPE.legalize.asUInt
     res.DMODE := dmode
     when(this.TYPE.isLegal) {
-      val mcontrolRes = Wire(new Mcontrol)
-      mcontrolRes := this.DATA.asUInt
-      res.DATA := mcontrolRes.writeData(dmode, chainable).asUInt
+      val mcontrol6Res = Wire(new Mcontrol6)
+      mcontrol6Res := this.DATA.asUInt
+      res.DATA := mcontrol6Res.writeData(dmode, chainable).asUInt
     }.otherwise{
       res.DATA := 0.U
     }
@@ -148,34 +148,32 @@ class Tdata1Bundle extends CSRBundle{
   }
 }
 
-class Mcontrol extends CSRBundle{
+class Mcontrol6 extends CSRBundle{
   override val len: Int = 59
   // xiangshan don't support match = NAPOT
-  val MASKMAX = RO(58, 53).withReset(0.U)
-  val SIZEHI  = RW(22, 21).withReset(0.U)
-  val HIT     = RW(20).withReset(0.U)
-  val SELECT  = RW(19).withReset(0.U)
-  val TIMING  = RW(18).withReset(0.U)
-  val SIZELO  = RW(17, 16).withReset(0.U)
-  val ACTION  = TrigAction(15, 12, wNoFilter).withReset(TrigAction.BreakpointExp)
-  val CHAIN   = RW(11).withReset(0.U)
-  val MATCH   = TrigMatch(10, 7, wNoFilter).withReset(TrigMatch.EQ)
-  val M       = RW(6).withReset(0.U)
-  val S       = RW(4).withReset(0.U)
-  val U       = RW(3).withReset(0.U)
-  val EXECUTE = RW(2).withReset(0.U)
-  val STORE   = RW(1).withReset(0.U)
-  val LOAD    = RW(0).withReset(0.U)
-
-  def writeData(dmode: Bool, chainable: Bool): Mcontrol = {
-    val res = Wire(new Mcontrol)
+  val UNCERTAIN   = RO(26).withReset(0.U)
+  val HIT1        = RO(25).withReset(0.U)
+  val VS          = RW(24).withReset(0.U)
+  val VU          = RW(23).withReset(0.U)
+  val HIT0        = RO(22).withReset(0.U)
+  val SELECT      = RW(21).withReset(0.U)
+  val SIZE        = RW(18, 16).withReset(0.U)
+  val ACTION      = TrigAction(15, 12, wNoFilter).withReset(TrigAction.BreakpointExp)
+  val CHAIN       = RW(11).withReset(0.U)
+  val MATCH       = TrigMatch(10, 7, wNoFilter).withReset(TrigMatch.EQ)
+  val M           = RW(6).withReset(0.U)
+  val UNCERTAINEN = RO(5).withReset(0.U)
+  val S           = RW(4).withReset(0.U)
+  val U           = RW(3).withReset(0.U)
+  val EXECUTE     = RW(2).withReset(0.U)
+  val STORE       = RW(1).withReset(0.U)
+  val LOAD        = RW(0).withReset(0.U)
+
+  def writeData(dmode: Bool, chainable: Bool): Mcontrol6 = {
+    val res = Wire(new Mcontrol6)
     res := this.asUInt
-    res.MASKMAX     := 0.U
-    res.SIZEHI      := 0.U
-    res.HIT         := false.B
+    res.SIZE        := 0.U
     res.SELECT      := this.EXECUTE.asBool && this.SELECT.asBool
-    res.TIMING      := false.B
-    res.SIZELO      := 0.U
     res.ACTION      := this.ACTION.legalize(dmode).asUInt
     res.CHAIN       := this.CHAIN.asBool && chainable
     res.MATCH       := this.MATCH.legalize.asUInt
@@ -197,7 +195,7 @@ object Tdata1Type extends CSREnum with WARLApply {
   val Tmexttrigger = Value(7.U)
   val Disabled     = Value(15.U)
 
-  override def isLegal(enumeration: CSREnumType): Bool = enumeration.isOneOf(Mcontrol)
+  override def isLegal(enumeration: CSREnumType): Bool = enumeration.isOneOf(Mcontrol6)
 
   override def legalize(enumeration: CSREnumType): CSREnumType = {
     val res = WireInit(enumeration)
@@ -260,8 +258,8 @@ class Tdata2Bundle extends OneFieldBundle
 class TinfoBundle extends CSRBundle{
   // Version isn't in version 0.13
   val VERSION     = RO(31, 24).withReset(0.U)
-  // only support mcontrol
-  val MCONTROLEN  = RO(2).withReset(1.U)
+  // only support mcontrol6
+  val MCONTROL6EN = RO(6).withReset(1.U)
 }
 
 class TcontrolBundle extends CSRBundle{
```
