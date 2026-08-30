# Commit Log
- Issue: #4134
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4134
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4134
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4134
- Changed files: 6
- Additions: 15
- Deletions: 5

## Files
- `rocket-chip`
- `src/main/scala/device/RocketDebugWrapper.scala`
- `src/main/scala/device/standalone/StandAloneDebugModule.scala`
- `src/main/scala/top/Top.scala`
- `src/main/scala/top/XSNoCTop.scala`
- `src/main/scala/xiangshan/XSTileWrap.scala`

## Diff
```diff
diff --git a/rocket-chip b/rocket-chip
index bb4baf85c5b..dadbaa77163 160000
--- a/rocket-chip
+++ b/rocket-chip
@@ -1 +1 @@
-Subproject commit bb4baf85c5bd4b55ffdcda12a75648fef212ab69
+Subproject commit dadbaa7716321e30f2f75c6fd551ceefa863fd85
diff --git a/src/main/scala/device/RocketDebugWrapper.scala b/src/main/scala/device/RocketDebugWrapper.scala
index 8aea56602c3..fc27323aeaa 100644
--- a/src/main/scala/device/RocketDebugWrapper.scala
+++ b/src/main/scala/device/RocketDebugWrapper.scala
@@ -108,6 +108,7 @@ object XSDebugModuleParams {
       baseAddress = BigInt(0x38020000),
       nScratch = 2,
       crossingHasSafeReset = false,
+      hasHartResets = true,
     )
   }
 }
diff --git a/src/main/scala/device/standalone/StandAloneDebugModule.scala b/src/main/scala/device/standalone/StandAloneDebugModule.scala
index 1f3cdafc30f..15b62931fac 100644
--- a/src/main/scala/device/standalone/StandAloneDebugModule.scala
+++ b/src/main/scala/device/standalone/StandAloneDebugModule.scala
@@ -65,7 +65,7 @@ class StandAloneDebugModule (
     withClockAndReset(io.clock.asClock, io.reset.asAsyncReset) {
       outer.debugModule.module.io.resetCtrl.hartIsInReset := AsyncResetSynchronizerShiftReg(io.resetCtrl.hartIsInReset, 3, 0)
       io.resetCtrl.hartResetReq.foreach(req =>
-        req := RegNext(outer.debugModule.module.io.resetCtrl.hartResetReq.get, 0.U.asTypeOf(req)))
+        req := RegNext(outer.debugModule.module.io.resetCtrl.hartResetReq.getOrElse(0.U.asTypeOf(req)), 0.U.asTypeOf(req)))
     }
   }
 
diff --git a/src/main/scala/top/Top.scala b/src/main/scala/top/Top.scala
index 81d7983bc33..19b64c588fe 100644
--- a/src/main/scala/top/Top.scala
+++ b/src/main/scala/top/Top.scala
@@ -394,7 +394,7 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc() with HasSoCParameter
       }
     }
 
-    misc.module.debug_module_io.resetCtrl.hartIsInReset := core_with_l2.map(_.module.reset.asBool)
+    misc.module.debug_module_io.resetCtrl.hartIsInReset := core_with_l2.map(_.module.io.hartIsInReset)
     misc.module.debug_module_io.clock := io.clock
     misc.module.debug_module_io.reset := reset_sync
 
@@ -414,8 +414,14 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc() with HasSoCParameter
     withClockAndReset(io.clock.asClock, reset_sync) {
       // Modules are reset one by one
       // reset ----> SYNC --> {SoCMisc, L3 Cache, Cores}
-      val resetChain = Seq(Seq(misc.module) ++ l3cacheOpt.map(_.module) ++ core_with_l2.map(_.module))
+      val resetChain = Seq(Seq(misc.module) ++ l3cacheOpt.map(_.module))
       ResetGen(resetChain, reset_sync, !debugOpts.ResetGen)
+      // Ensure that cores could be reset when DM disable `hartReset` or l3cacheOpt.isEmpty.
+      val dmResetReqVec = misc.module.debug_module_io.resetCtrl.hartResetReq.getOrElse(0.U.asTypeOf(Vec(core_with_l2.map(_.module).length, Bool())))
+      val syncResetCores = if(l3cacheOpt.nonEmpty) l3cacheOpt.map(_.module).get.reset.asBool else misc.module.reset.asBool
+      (core_with_l2.map(_.module)).zip(dmResetReqVec).map { case(core, dmResetReq) =>
+        ResetGen(Seq(Seq(core)), (syncResetCores || dmResetReq).asAsyncReset, !debugOpts.ResetGen)
+      }
     }
 
   }
diff --git a/src/main/scala/top/XSNoCTop.scala b/src/main/scala/top/XSNoCTop.scala
index 34189ced588..7a25523a05a 100644
--- a/src/main/scala/top/XSNoCTop.scala
+++ b/src/main/scala/top/XSNoCTop.scala
@@ -111,6 +111,7 @@ class XSNoCTop()(implicit p: Parameters) extends BaseXSSoc with HasSoCParameter
       val hartId = Input(UInt(p(MaxHartIdBits).W))
       val riscv_halt = Output(Bool())
       val riscv_critical_error = Output(Bool())
+      val hartResetReq = Input(Bool())
       val hartIsInReset = Output(Bool())
       val riscv_rst_vec = Input(UInt(soc.PAddrBits.W))
       val chi = new PortIO
@@ -163,6 +164,7 @@ class XSNoCTop()(implicit p: Parameters) extends BaseXSSoc with HasSoCParameter
     core_with_l2.module.io.nodeID.get := io.nodeID
     io.riscv_halt := core_with_l2.module.io.cpu_halt
     io.riscv_critical_error := core_with_l2.module.io.cpu_crtical_error
+    core_with_l2.module.io.hartResetReq := io.hartResetReq
     io.hartIsInReset := core_with_l2.module.io.hartIsInReset
     core_with_l2.module.io.reset_vector := io.riscv_rst_vec
     // trace Interface
diff --git a/src/main/scala/xiangshan/XSTileWrap.scala b/src/main/scala/xiangshan/XSTileWrap.scala
index 3ce65b1ad9a..ad78a089b68 100644
--- a/src/main/scala/xiangshan/XSTileWrap.scala
+++ b/src/main/scala/xiangshan/XSTileWrap.scala
@@ -61,6 +61,7 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
       val reset_vector = Input(UInt(PAddrBits.W))
       val cpu_halt = Output(Bool())
       val cpu_crtical_error = Output(Bool())
+      val hartResetReq = Input(Bool())
       val hartIsInReset = Output(Bool())
       val traceCoreInterface = new TraceCoreInterface
       val debugTopDown = new Bundle {
@@ -78,7 +79,7 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
       }
     })
 
-    val reset_sync = withClockAndReset(clock, reset)(ResetGen())
+    val reset_sync = withClockAndReset(clock, (reset.asBool || io.hartResetReq).asAsyncReset)(ResetGen())
     val noc_reset_sync = EnableCHIAsyncBridge.map(_ => withClockAndReset(clock, noc_reset.get)(ResetGen()))
     val soc_reset_sync = withClockAndReset(clock, soc_reset)(ResetGen())
```
