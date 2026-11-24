# Commit Log
- Issue: #6969
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6969
- Issue state: closed
- Tested RTL commit: -
- Related PR: #7004
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/7004
- Changed files: 1
- Additions: 6
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala b/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala
index ac246bc61bb..f029d83c995 100644
--- a/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala
+++ b/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala
@@ -449,7 +449,12 @@ class BitmapCache(implicit p: Parameters) extends XSModule with HasPtwConst {
   stageResp.ready := !stageResp.valid || io.resp.ready
 
   val te = ClockGate.genTeSink
-  val bc_masked_clock = ClockGate(te.cgen, stageReq.fire | (!flush && io.refill.valid) | mbistBC.map(_.mbist.req).getOrElse(false.B), clock)
+  // Translation flushes only clear the pipeline; refills must still update the SRAM along with bcv.
+  val bc_masked_clock = ClockGate(
+    te.cgen,
+    stageReq.fire | io.refill.valid | mbistBC.map(_.mbist.req).getOrElse(false.B),
+    clock
+  )
   bitmapcache.clock := bc_masked_clock
 
   val ppn_search = stageReq.bits.tag
```
