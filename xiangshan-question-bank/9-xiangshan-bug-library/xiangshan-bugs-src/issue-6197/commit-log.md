# Commit Log
- Issue: #6197
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6197
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6197
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6197
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 991ade99ffa..0fda2dcd2e8 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -884,7 +884,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     io.primary_ready := !req_valid && !GatedValidRegNext(primary_fire)
   }.otherwise {
     // cannot accept prefetch req except when a memset patten is detected
-    io.primary_ready := !req_valid && (!io.queryME.map(_.req.bits.isFromPrefetch).reduce(_&&_) || io.memSetPattenDetected) && !GatedValidRegNext(primary_fire)
+    // prefetch only in mainpipe, now
+    io.primary_ready := !req_valid && (!(io.queryME(0).req.valid && io.queryME(0).req.bits.isFromPrefetch) || io.memSetPattenDetected) && !GatedValidRegNext(primary_fire)
   }
 
   // Generate vectorized secondary_ready and secondary_reject for parallel enqueue
```
