# Commit Log
- Issue: #3859
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3859
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3859
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3859
- Changed files: 6
- Additions: 20
- Deletions: 9

## Files
- `rocket-chip`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/PreDecode.scala`

## Diff
```diff
diff --git a/rocket-chip b/rocket-chip
index ab7cf0bbfa4..d24ca81a617 160000
--- a/rocket-chip
+++ b/rocket-chip
@@ -1 +1 @@
-Subproject commit ab7cf0bbfa40f8395ba01f1beccf321074c4a22c
+Subproject commit d24ca81a61727157ae8b7768b24b9cdbb1ddc8dd
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index d88924125e9..fa473424a2a 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -614,6 +614,8 @@ class CustomCSRCtrlIO(implicit p: Parameters) extends XSBundle {
   val mem_trigger = new MemTdataDistributeIO()
   // Virtualization Mode
   val virtMode = Output(Bool())
+  // xstatus.fs field is off
+  val fsIsOff = Output(Bool())
 }
 
 class DistributedCSRIO(implicit p: Parameters) extends XSBundle {
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 8ecac7460e4..17d2def8e60 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -353,6 +353,8 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       custom.mem_trigger      := csrMod.io.status.memTrigger
       // virtual mode
       custom.virtMode := csrMod.io.status.privState.V.asBool
+      // xstatus.fs field is off
+      custom.fsIsOff := csrMod.io.toDecode.illegalInst.fsIsOff
   }
 
   csrOut.instrAddrTransType := csrMod.io.status.instrAddrTransType
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index c91839cf9f3..46ba2333a3a 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -105,11 +105,14 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
   // trigger
   ifu.io.frontendTrigger := csrCtrl.frontend_trigger
 
+  // RVCDecoder fsIsOff
+  ifu.io.csr_fsIsOff := csrCtrl.fsIsOff
+
   // bpu ctrl
   bpu.io.ctrl         := csrCtrl.bp_ctrl
   bpu.io.reset_vector := io.reset_vector
 
-// pmp
+  // pmp
   val PortNumber = ICacheParameters().PortNumber
   val pmp        = Module(new PMP())
   val pmp_check  = VecInit(Seq.fill(coreParams.ipmpPortNum)(Module(new PMPChecker(3, sameCycle = true)).io))
@@ -151,8 +154,8 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
   ftq.io.fromBpu <> bpu.io.bpu_to_ftq
 
   ftq.io.mmioCommitRead <> ifu.io.mmioCommitRead
-  // IFU-ICache
 
+  // IFU-ICache
   icache.io.fetch.req <> ftq.io.toICache.req
   ftq.io.toICache.req.ready := ifu.io.ftqInter.fromFtq.req.ready && icache.io.fetch.req.ready
 
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index b50a70c8ae1..effc360a8e6 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -80,6 +80,7 @@ class NewIFUIO(implicit p: Parameters) extends XSBundle {
   val iTLBInter       = new TlbRequestIO
   val pmp             = new ICachePMPBundle
   val mmioCommitRead  = new mmioCommitRead
+  val csr_fsIsOff     = Input(Bool())
 }
 
 // record the situation in which fallThruAddr falls into
@@ -523,7 +524,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
   val f3_instr = RegEnable(f2_instr, f2_fire)
 
   expanders.zipWithIndex.foreach { case (expander, i) =>
-    expander.io.in := f3_instr(i)
+    expander.io.in      := f3_instr(i)
+    expander.io.fsIsOff := io.csr_fsIsOff
   }
   // Use expanded instruction only when input is legal.
   // Otherwise use origin illegal RVC instruction.
@@ -919,7 +921,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
   mmioFlushWb.bits.instrRange := f3_mmio_range
 
   val mmioRVCExpander = Module(new RVCExpander)
-  mmioRVCExpander.io.in := Mux(f3_req_is_mmio, Cat(f3_mmio_data(1), f3_mmio_data(0)), 0.U)
+  mmioRVCExpander.io.in      := Mux(f3_req_is_mmio, Cat(f3_mmio_data(1), f3_mmio_data(0)), 0.U)
+  mmioRVCExpander.io.fsIsOff := io.csr_fsIsOff
 
   /** external predecode for MMIO instruction */
   when(f3_req_is_mmio) {
diff --git a/src/main/scala/xiangshan/frontend/PreDecode.scala b/src/main/scala/xiangshan/frontend/PreDecode.scala
index c645db22ac0..4601cd922ce 100644
--- a/src/main/scala/xiangshan/frontend/PreDecode.scala
+++ b/src/main/scala/xiangshan/frontend/PreDecode.scala
@@ -281,12 +281,13 @@ class F3Predecoder(implicit p: Parameters) extends XSModule with HasPdConst {
 
 class RVCExpander(implicit p: Parameters) extends XSModule {
   val io = IO(new Bundle {
-    val in  = Input(UInt(32.W))
-    val out = Output(new ExpandedInstruction)
-    val ill = Output(Bool())
+    val in      = Input(UInt(32.W))
+    val fsIsOff = Input(Bool())
+    val out     = Output(new ExpandedInstruction)
+    val ill     = Output(Bool())
   })
 
-  val decoder = new RVCDecoder(io.in, XLEN, fLen, useAddiForMv = true)
+  val decoder = new RVCDecoder(io.in, io.fsIsOff, XLEN, fLen, useAddiForMv = true)
 
   if (HasCExtension) {
     io.out := decoder.decode
```
