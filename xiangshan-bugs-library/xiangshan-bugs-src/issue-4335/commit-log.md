# Commit Log
- Issue: #4335
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4335
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4335
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4335
- Changed files: 3
- Additions: 8
- Deletions: 2

## Files
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index a300d3bc8f3..74780b7dcdc 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -103,6 +103,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
   val debug_int_node = IntIdentityNode()
   val plic_int_node = IntIdentityNode()
   val nmi_int_node = IntIdentityNode()
+  val beu_local_int_source = IntSourceNode(IntSourcePortSimple())
 
   println(s"enableCHI: ${enableCHI}")
   val l2cache = if (enableL2) {
@@ -228,6 +229,9 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
 
     val resetDelayN = Module(new DelayN(UInt(PAddrBits.W), 5))
 
+    val (beu_int_out, _) = beu_local_int_source.out(0)
+    beu_int_out(0) := beu.module.io.interrupt
+
     beu.module.io.errors.icache := io.beu_errors.icache
     beu.module.io.errors.dcache := io.beu_errors.dcache
     resetDelayN.io.in := io.reset_vector.fromTile
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index 9aa0df7d4e6..02b4cf382b9 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -27,7 +27,7 @@ import freechips.rocketchip.amba.axi4._
 import device.MsiInfoBundle
 import system.HasSoCParameter
 import top.{ArgParser, BusPerfMonitor, Generator}
-import utility.{ChiselDB, Constantin, DFTResetSignals, DelayN, FileRegisters, ResetGen, TLClientsMerger, TLEdgeBuffer, TLLogger}
+import utility.{ChiselDB, Constantin, DFTResetSignals, DelayN, FileRegisters, IntBuffer, ResetGen, TLClientsMerger, TLEdgeBuffer, TLLogger}
 import coupledL2.EnableCHI
 import coupledL2.tl2chi.PortIO
 import utility.sram.SramBroadcastBundle
@@ -58,6 +58,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
   memBlock.plic_int_sink :*= plic_int_node
   memBlock.debug_int_sink := debug_int_node
   memBlock.nmi_int_sink := nmi_int_node
+  memBlock.beu_local_int_sink := IntBuffer() := l2top.inner.beu_local_int_source
 
   // =========== Components' Connection ============
   // L1 to l1_xbar
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 7a086daae90..44b8c633ffa 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -274,6 +274,7 @@ class MemBlockInlined()(implicit p: Parameters) extends LazyModule
   val debug_int_sink = IntSinkNode(IntSinkPortSimple(1, 1))
   val plic_int_sink = IntSinkNode(IntSinkPortSimple(2, 1))
   val nmi_int_sink = IntSinkNode(IntSinkPortSimple(1, (new NonmaskableInterruptIO).elements.size))
+  val beu_local_int_sink = IntSinkNode(IntSinkPortSimple(1, 1))
 
   if (!coreParams.softPTW) {
     ptw_to_l2_buffer.node := ptw.node
@@ -1915,7 +1916,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     x.externalInterrupt.meip  := outer.plic_int_sink.in.head._1(0)
     x.externalInterrupt.seip  := outer.plic_int_sink.in.last._1(0)
     x.externalInterrupt.debug := outer.debug_int_sink.in.head._1(0)
-    x.externalInterrupt.nmi.nmi_31 := outer.nmi_int_sink.in.head._1(0)
+    x.externalInterrupt.nmi.nmi_31 := outer.nmi_int_sink.in.head._1(0) | outer.beu_local_int_sink.in.head._1(0)
     x.externalInterrupt.nmi.nmi_43 := outer.nmi_int_sink.in.head._1(1)
     x.msiInfo           := DelayNWithValid(io.fromTopToBackend.msiInfo, 1)
     x.clintTime         := DelayNWithValid(io.fromTopToBackend.clintTime, 1)
```
