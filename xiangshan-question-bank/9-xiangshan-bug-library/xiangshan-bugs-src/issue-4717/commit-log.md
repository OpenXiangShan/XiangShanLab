# Commit Log
- Issue: #4717
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4717
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4717
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4717
- Changed files: 5
- Additions: 17
- Deletions: 5

## Files
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/cache/dcache/Uncache.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index 53b06e93656..8eb0d3bac05 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -44,12 +44,14 @@ class L1BusErrorUnitInfo(implicit val p: Parameters) extends Bundle with HasSoCP
 class XSL1BusErrors()(implicit val p: Parameters) extends BusErrors {
   val icache = new L1BusErrorUnitInfo
   val dcache = new L1BusErrorUnitInfo
+  val uncache = new L1BusErrorUnitInfo
   val l2 = new L1BusErrorUnitInfo
 
   override def toErrorList: List[Option[(ValidIO[UInt], String, String)]] =
     List(
       Some(icache.ecc_error, "I_ECC", "Icache ecc error"),
       Some(dcache.ecc_error, "D_ECC", "Dcache ecc error"),
+      Some(uncache.ecc_error, "U_ECC", "Uncache ecc error"),
       Some(l2.ecc_error, "L2_ECC", "L2Cache ecc error")
     )
 }
@@ -235,6 +237,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
 
     beu.module.io.errors.icache := io.beu_errors.icache
     beu.module.io.errors.dcache := io.beu_errors.dcache
+    beu.module.io.errors.uncache := io.beu_errors.uncache
     resetDelayN.io.in := io.reset_vector.fromTile
     io.reset_vector.toCore := resetDelayN.io.out
     io.hartId.toCore := io.hartId.fromTile
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 1c87a12dca7..e219978a267 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -264,7 +264,8 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   io.cpu_critical_error := memBlock.io.outer_cpu_critical_error
   io.msiAck := memBlock.io.outer_msi_ack
   io.beu_errors.icache <> memBlock.io.outer_beu_errors_icache
-  io.beu_errors.dcache <> memBlock.io.error.bits.toL1BusErrorUnitInfo(memBlock.io.error.valid)
+  io.beu_errors.dcache <> memBlock.io.dcacheError.bits.toL1BusErrorUnitInfo(memBlock.io.dcacheError.valid)
+  io.beu_errors.uncache <> memBlock.io.uncacheError
   io.beu_errors.l2 <> DontCare
   io.l2PfCtrl := backend.io.mem.csrCtrl.pf_ctrl.toL2PrefetchCtrl()
 
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index e9047f8b406..4ad5c33ac4a 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -150,6 +150,7 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
     l2top.module.io.beu_errors.icache <> core.module.io.beu_errors.icache
     l2top.module.io.beu_errors.dcache <> core.module.io.beu_errors.dcache
+    l2top.module.io.beu_errors.uncache <> core.module.io.beu_errors.uncache
 
     l2top.module.io.l2_flush_en.foreach { _ := core.module.io.l2_flush_en }
     io.l2_flush_en.foreach { _ := core.module.io.l2_flush_en }
diff --git a/src/main/scala/xiangshan/cache/dcache/Uncache.scala b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
index 9a5eb2ad2b3..6436dcb2d7b 100644
--- a/src/main/scala/xiangshan/cache/dcache/Uncache.scala
+++ b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
@@ -175,6 +175,7 @@ class UncacheIO(implicit p: Parameters) extends DCacheBundle {
   val lsq = Flipped(new UncacheWordIO)
   val forward = Vec(LoadPipelineWidth, Flipped(new LoadForwardQueryIO))
   val wfi = Flipped(new WfiReqBundle)
+  val busError = Output(new L1BusErrorUnitInfo())
 }
 
 // convert DCacheIO to TileLink
@@ -467,6 +468,9 @@ class UncacheImp(outer: Uncache)extends LazyModuleImp(outer)
       }
     )
   }
+  io.busError.ecc_error.valid := mem_grant.fire && isStore(entries(mem_grant.bits.source)) &&
+    (mem_grant.bits.denied || mem_grant.bits.corrupt)
+  io.busError.ecc_error.bits := entries(mem_grant.bits.source).addr >> blockOffBits << blockOffBits
 
   io.wfi.wfiSafe := GatedValidRegNext(noPending.asUInt.andR && io.wfi.wfiReq)
   /******************************************************************
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index aa9f7510577..9da26067368 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -312,7 +312,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     val ifetchPrefetch = Vec(LduCnt, ValidIO(new SoftIfetchPrefetchBundle))
 
     // misc
-    val error = ValidIO(new L1CacheErrorInfo)
+    val dcacheError = ValidIO(new L1CacheErrorInfo)
+    val uncacheError = Output(new L1BusErrorUnitInfo())
     val memInfo = new Bundle {
       val sqFull = Output(Bool())
       val lqFull = Output(Bool())
@@ -400,10 +401,12 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
 
   val csrCtrl = DelayN(io.ooo_to_mem.csrCtrl, 2)
   dcache.io.l2_pf_store_only := RegNext(io.ooo_to_mem.csrCtrl.pf_ctrl.l2_pf_store_only, false.B)
-  io.error <> DelayNWithValid(dcache.io.error, 2)
+  io.dcacheError <> DelayNWithValid(dcache.io.error, 2)
+  io.uncacheError.ecc_error <> DelayNWithValid(uncache.io.busError.ecc_error, 2)
   when(!csrCtrl.cache_error_enable){
-    io.error.bits.report_to_beu := false.B
-    io.error.valid := false.B
+    io.dcacheError.bits.report_to_beu := false.B
+    io.dcacheError.valid := false.B
+    io.uncacheError.ecc_error.valid := false.B
   }
 
   val loadUnits = Seq.fill(LduCnt)(Module(new LoadUnit))
```
