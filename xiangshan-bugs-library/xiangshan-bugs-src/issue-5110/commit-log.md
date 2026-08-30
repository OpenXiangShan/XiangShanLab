# Commit Log
- Issue: #5110
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5110
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5110
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5110
- Changed files: 2
- Additions: 5
- Deletions: 2

## Files
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/XSTile.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index 35b1087b23d..d41e1339480 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -105,6 +105,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
   val plic_int_node = IntIdentityNode()
   val nmi_int_node = IntIdentityNode()
   val beu_local_int_source = IntSourceNode(IntSourcePortSimple())
+  val beu_local_int_source_buffer = IntBuffer()
 
   println(s"enableCHI: ${enableCHI}")
   val l2cache = if (enableL2) {
@@ -163,6 +164,8 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
     TLFilter(TLFilter.mSubtract(mmioFilters)) :=
     TLBuffer() :=
     mmio_xbar
+  
+  beu_local_int_source_buffer := beu_local_int_source
 
   class Imp(wrapper: LazyModule) extends LazyModuleImp(wrapper) {
     val io = IO(new Bundle {
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index 4ad5c33ac4a..347a3c3549d 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -26,7 +26,7 @@ import freechips.rocketchip.tilelink._
 import freechips.rocketchip.amba.axi4._
 import system.HasSoCParameter
 import top.{ArgParser, BusPerfMonitor, Generator}
-import utility.{ChiselDB, Constantin, DFTResetSignals, DelayN, FileRegisters, IntBuffer, ResetGen, TLClientsMerger, TLEdgeBuffer, TLLogger}
+import utility.{ChiselDB, Constantin, DFTResetSignals, DelayN, FileRegisters, ResetGen, TLClientsMerger, TLEdgeBuffer, TLLogger}
 import utility.sram.SramBroadcastBundle
 import coupledL2.EnableCHI
 import coupledL2.tl2chi.PortIO
@@ -57,7 +57,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
   memBlock.plic_int_sink :*= plic_int_node
   memBlock.debug_int_sink := debug_int_node
   memBlock.nmi_int_sink := nmi_int_node
-  memBlock.beu_local_int_sink := IntBuffer() := l2top.inner.beu_local_int_source
+  memBlock.beu_local_int_sink := l2top.inner.beu_local_int_source_buffer
 
   // =========== Components' Connection ============
   // L1 to l1_xbar
```
