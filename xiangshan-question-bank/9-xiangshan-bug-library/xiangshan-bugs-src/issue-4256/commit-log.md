# Commit Log
- Issue: #4256
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4256
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4256
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4256
- Changed files: 2
- Additions: 19
- Deletions: 13

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index 2c7cb6c0565..0b2d95b9600 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -43,9 +43,9 @@ trait DebugLevel { self: NewCSR =>
     .setAddr(CSRs.tdata2)
 
   val tdata1RegVec: Seq[CSRModule[_]] = Range(0, TriggerNum).map(i =>
-    Module(new CSRModule(s"Trigger$i" + s"_Tdata1", new Tdata1Bundle) with HasdebugModeBundle {
+    Module(new CSRModule(s"Trigger$i" + s"_Tdata1", new Tdata1Bundle) with HasTriggerBundle {
       when(wen){
-        reg := wdata.writeTdata1(debugMode, chainable).asUInt
+        reg := wdata.writeTdata1(canWriteDmode, chainable).asUInt
       }
     })
   )
@@ -131,10 +131,10 @@ class Tdata1Bundle extends CSRBundle{
     res.ACTION
   }
 
-  def writeTdata1(debugMode: Bool, chainable: Bool): Tdata1Bundle = {
+  def writeTdata1(canWriteDmode: Bool, chainable: Bool): Tdata1Bundle = {
     val res = Wire(new Tdata1Bundle)
     res := this.asUInt
-    val dmode = this.DMODE.asBool && debugMode
+    val dmode = this.DMODE.asBool && canWriteDmode
     res.TYPE := this.TYPE.legalize.asUInt
     res.DMODE := dmode
     when(this.TYPE.isLegal) {
@@ -272,7 +272,7 @@ class DscratchBundle extends OneFieldBundle
 
 class DcsrBundle extends CSRBundle {
   override val len: Int = 32
-  val DEBUGVER  = DcsrDebugVer(31, 28).withReset(DcsrDebugVer.Spec) // Debug implementation as it described in 0.13 draft // todo
+  val DEBUGVER  = DcsrDebugVer(31, 28).withReset(DcsrDebugVer.Spec) // Debug implementation as it described in 0.13 draft
   val EXTCAUSE  =           RO(26, 24).withReset(0.U)
   val CETRIG    =           RW(    19).withReset(0.U)
   // All ebreak Privileges are RW, instead of WARL, since XiangShan support U/S/VU/VS.
@@ -290,7 +290,6 @@ class DcsrBundle extends CSRBundle {
   // MPRVEN is RW, instead of WARL, since XiangShan support use mstatus.mprv in debug mode
   // Whether use mstatus.mprv
   val MPRVEN    =           RW(     4).withReset(0.U)
-  // TODO: support non-maskable interrupt
   val NMIP      =           RO(     3).withReset(0.U)
   // MPRVEN is RW, instead of WARL, since XiangShan support use mstatus.mprv in debug mode
   val STEP      =           RW(     2).withReset(0.U)
@@ -316,12 +315,12 @@ object DcsrCause extends CSREnum with ROApply {
 
 trait HasTdataSink { self: CSRModule[_] =>
   val tdataRead = IO(Input(new Bundle {
-    val tdata1 = UInt(XLEN.W) // Todo: check if use ireg bundle, and shrink the width
+    val tdata1 = UInt(XLEN.W)
     val tdata2 = UInt(XLEN.W)
   }))
 }
-trait HasdebugModeBundle { self: CSRModule[_] =>
-  val debugMode = IO(Input(Bool()))
+trait HasTriggerBundle { self: CSRModule[_] =>
+  val canWriteDmode = IO(Input(Bool()))
   val chainable = IO(Input(Bool()))
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 4b049fb6669..e953fa5e7fc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1134,14 +1134,21 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapEntryDEvent.in.breakPoint                   := debugMod.io.out.breakPoint
   trapEntryDEvent.in.criticalErrorStateEnterDebug := debugMod.io.out.criticalErrorStateEnterDebug
 
-  tdata1RegVec.foreach { mod =>
-    mod match {
-      case m: HasdebugModeBundle =>
-        m.debugMode := debugMode
+  for(idx <- 0 until TriggerNum) {
+    val tdata1Pre = Wire(new Tdata1Bundle)
+    val mcontrol6Pre = Wire(new Mcontrol6)
+    tdata1Pre := (if (idx > 0) tdata1RegVec(idx - 1) else tdata1RegVec(idx)).rdata.asUInt
+    mcontrol6Pre := tdata1Pre.DATA.asUInt
+    val canWriteDmode = WireInit(false.B)
+    canWriteDmode := (if(idx > 0) (Mux(mcontrol6Pre.CHAIN.asBool, tdata1Pre.DMODE.asBool && tdata1Pre.TYPE.isLegal, true.B)) && debugMode else debugMode).asBool
+    tdata1RegVec(idx) match {
+      case m: HasTriggerBundle =>
+        m.canWriteDmode := canWriteDmode
         m.chainable := debugMod.io.out.newTriggerChainIsLegal
       case _ =>
     }
   }
+
   tdata1RegVec.zip(tdata2RegVec).zipWithIndex.map { case ((mod1, mod2), idx) => {
     mod1.w.wen    := tdata1Update && (tselect.rdata === idx.U)
     mod1.w.wdata  := wdata
```
