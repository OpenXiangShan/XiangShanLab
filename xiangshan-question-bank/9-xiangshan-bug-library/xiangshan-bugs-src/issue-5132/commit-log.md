# Commit Log
- Issue: #5132
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5132
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5132
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5132
- Changed files: 5
- Additions: 25
- Deletions: 25

## Files
- `src/main/scala/top/Configs.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Parameters.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/RasStack.scala`

## Diff
```diff
diff --git a/src/main/scala/top/Configs.scala b/src/main/scala/top/Configs.scala
index 9a758e85b27..369ff9a48cd 100644
--- a/src/main/scala/top/Configs.scala
+++ b/src/main/scala/top/Configs.scala
@@ -131,8 +131,8 @@ class MinimalConfig(n: Int = 1) extends Config(
               TagWidth = 7
             ),
             rasParameters = RasParameters(
-              StackSize = 8,
-              SpecSize = 16
+              CommitStackSize = 8,
+              SpecQueueSize = 16
             ),
           ),
           ftqParameters = FtqParameters(
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
index 8cbee34b8c4..d5602fc2de0 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
@@ -39,7 +39,7 @@ object RasEntry {
 }
 
 class RasPtr(implicit p: Parameters) extends CircularQueuePtr[RasPtr](p =>
-      p(XSCoreParamsKey).frontendParameters.bpuParameters.rasParameters.StackSize
+      p(XSCoreParamsKey).frontendParameters.bpuParameters.rasParameters.SpecQueueSize
     ) {}
 
 object RasPtr {
@@ -54,7 +54,7 @@ object RasPtr {
 }
 
 class RasInternalMeta(implicit p: Parameters) extends RasBundle {
-  val ssp:  UInt   = UInt(log2Up(StackSize).W)
+  val ssp:  UInt   = UInt(log2Up(CommitStackSize).W)
   val sctr: UInt   = UInt(StackCounterWidth.W)
   val tosw: RasPtr = new RasPtr
   val tosr: RasPtr = new RasPtr
@@ -74,7 +74,7 @@ object RasInternalMeta {
 }
 
 class RasMeta(implicit p: Parameters) extends RasBundle {
-  val ssp:  UInt   = UInt(log2Up(StackSize).W)
+  val ssp:  UInt   = UInt(log2Up(CommitStackSize).W)
   val tosw: RasPtr = new RasPtr
 }
 
@@ -90,7 +90,7 @@ object RasMeta {
 class RasDebug(implicit p: Parameters) extends RasBundle {
   val specQueue:   Vec[RasEntry] = Output(Vec(SpecQueueSize, new RasEntry))
   val specNos:     Vec[RasPtr]   = Output(Vec(SpecQueueSize, new RasPtr))
-  val commitStack: Vec[RasEntry] = Output(Vec(StackSize, new RasEntry))
+  val commitStack: Vec[RasEntry] = Output(Vec(CommitStackSize, new RasEntry))
   val bos:         RasPtr        = Output(new RasPtr)
 }
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Parameters.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Parameters.scala
index c6ff1d143b9..b2021eb20c2 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Parameters.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Parameters.scala
@@ -19,18 +19,18 @@ import chisel3.util._
 import xiangshan.frontend.bpu.HasBpuParameters
 
 case class RasParameters(
-    StackSize:         Int = 16, // Size of the RAS stack
-    SpecSize:          Int = 32, // Size of the RAS speculative queue
+    CommitStackSize:   Int = 16, // Size of the RAS stack
+    SpecQueueSize:     Int = 32, // Size of the RAS speculative queue
     StackCounterWidth: Int = 3   // Width of the RAS counter (log2 of number of same calls merged in single stack entry)
 ) {
-  require(isPow2(SpecSize), "SpecSize must be a power of 2")
+  require(isPow2(SpecQueueSize), "SpecSize must be a power of 2")
 }
 
 trait HasRasParameters extends HasBpuParameters {
   def rasParameters: RasParameters = bpuParameters.rasParameters
 
-  def StackSize:         Int = rasParameters.StackSize
-  def SpecQueueSize:     Int = rasParameters.SpecSize
+  def CommitStackSize:   Int = rasParameters.CommitStackSize
+  def SpecQueueSize:     Int = rasParameters.SpecQueueSize
   def StackCounterWidth: Int = rasParameters.StackCounterWidth
   def StackCounterMax:   Int = (1 << StackCounterWidth) - 1
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
index 4034e458740..3f849a3b0eb 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
@@ -53,7 +53,7 @@ class Ras(implicit p: Parameters) extends BasePredictor with HasRasParameters wi
 
   def alignMask: UInt = ((~0.U(VAddrBits.W)) << FetchBlockAlignWidth).asUInt
 
-  private val stack = Module(new RasStack(StackSize, SpecQueueSize)).io
+  private val stack = Module(new RasStack).io
   // Here is an assertion that the same piece of valid data lasts for only one cycle.
   // io.specIn.valid = s3_fire
   private val stackNearOverflow = stack.specNearOverflow
@@ -124,7 +124,7 @@ class Ras(implicit p: Parameters) extends BasePredictor with HasRasParameters wi
     XSDebug(specFire, "\n")
   }
   XSDebug(specFire, "  index       addr           ctr   (committed part)\n")
-  for (i <- 0 until StackSize) {
+  for (i <- 0 until CommitStackSize) {
     XSDebug(
       specFire,
       "  (%d)   0x%x      %d",
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/RasStack.scala b/src/main/scala/xiangshan/frontend/bpu/ras/RasStack.scala
index 822f4f0bca2..2e4576e10f9 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/RasStack.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/RasStack.scala
@@ -24,7 +24,7 @@ import utility.XSPerfAccumulate
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.PrunedAddrInit
 
-class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends RasModule
+class RasStack(implicit p: Parameters) extends RasModule
     with HasCircularQueuePtrHelper
     with Helpers {
   class RasStackIO extends Bundle {
@@ -43,7 +43,7 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
       val pushAddr:  PrunedAddr = Input(PrunedAddr(VAddrBits))
       val metaTosw:  RasPtr     = Input(new RasPtr)
       // for debug purpose only
-      val metaSsp: UInt = Input(UInt(log2Up(StackSize).W))
+      val metaSsp: UInt = Input(UInt(log2Up(CommitStackSize).W))
     }
 
     class RasRedirectIO extends Bundle {
@@ -64,12 +64,12 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
   }
   val io: RasStackIO = IO(new RasStackIO)
 
-  private val commitStack = RegInit(VecInit(Seq.fill(StackSize)(RasEntry(PrunedAddrInit(0.U(VAddrBits.W)), 0.U))))
-  private val specQueue   = RegInit(VecInit(Seq.fill(rasSpecSize)(RasEntry(PrunedAddrInit(0.U(VAddrBits.W)), 0.U))))
-  private val specNos     = RegInit(VecInit(Seq.fill(rasSpecSize)(RasPtr(false.B, 0.U))))
+  private val commitStack = RegInit(VecInit(Seq.fill(CommitStackSize)(RasEntry(PrunedAddrInit(0.U(VAddrBits.W)), 0.U))))
+  private val specQueue   = RegInit(VecInit(Seq.fill(SpecQueueSize)(RasEntry(PrunedAddrInit(0.U(VAddrBits.W)), 0.U))))
+  private val specNos     = RegInit(VecInit(Seq.fill(SpecQueueSize)(RasPtr(false.B, 0.U))))
 
-  private val nsp = RegInit(0.U(log2Up(rasSize).W))
-  private val ssp = RegInit(0.U(log2Up(rasSize).W))
+  private val nsp = RegInit(0.U(log2Up(CommitStackSize).W))
+  private val ssp = RegInit(0.U(log2Up(CommitStackSize).W))
 
   private val sctr = RegInit(0.U(StackCounterWidth.W))
   private val tosr = RegInit(RasPtr(true.B, (SpecQueueSize - 1).U))
@@ -230,7 +230,7 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
     }
   }.elsewhen(io.redirect.valid && io.redirect.isRet) {
     // getTop using redirect Nos as tosr
-    val popRedSsp  = Wire(UInt(log2Up(rasSize).W))
+    val popRedSsp  = Wire(UInt(log2Up(CommitStackSize).W))
     val popRedSctr = Wire(UInt(StackCounterWidth.W))
     val popRedTosr = io.redirect.meta.nos
     val popRedTosw = io.redirect.meta.tosw
@@ -257,7 +257,7 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
     timingTop := getTop(popSsp, popSctr, popTosr, popTosw, allowBypass = false)
   }.elsewhen(io.spec.popValid) {
     // getTop using current Nos as tosr
-    val popSsp  = Wire(UInt(log2Up(rasSize).W))
+    val popSsp  = Wire(UInt(log2Up(CommitStackSize).W))
     val popSctr = Wire(UInt(StackCounterWidth.W))
     val popTosr = topNos
     val popTosw = tosw
@@ -331,7 +331,7 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
   private val commitTop = commitStack(nsp)
 
   when(io.commit.popValid) {
-    val nspUpdate = Wire(UInt(log2Up(rasSize).W))
+    val nspUpdate = Wire(UInt(log2Up(CommitStackSize).W))
     when(io.commit.metaSsp =/= nsp) {
       // force set nsp to commit ssp to avoid permanent errors
       nspUpdate := io.commit.metaSsp
@@ -352,7 +352,7 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
   private val commitPushAddr = specQueue(io.commit.metaTosw.value).retAddr
 
   when(io.commit.pushValid) {
-    val nspUpdate = Wire(UInt(log2Up(rasSize).W))
+    val nspUpdate = Wire(UInt(log2Up(CommitStackSize).W))
     when(io.commit.metaSsp =/= nsp) {
       // force set nsp to commit ssp to avoid permanent errors
       nspUpdate := io.commit.metaSsp
@@ -411,7 +411,7 @@ class RasStack(rasSize: Int, rasSpecSize: Int)(implicit p: Parameters) extends R
     }
   }
 
-  when(distanceBetween(tosw, bos) > (rasSpecSize - 2).U) {
+  when(distanceBetween(tosw, bos) > (SpecQueueSize - 2).U) {
     specNearOverflowed := true.B
   }.otherwise {
     specNearOverflowed := false.B
```
