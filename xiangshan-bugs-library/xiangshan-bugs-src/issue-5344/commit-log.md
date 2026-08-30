# Commit Log
- Issue: #5344
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5344
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5344
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5344
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
index 53dc8736061..640c8fbe552 100644
--- a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
@@ -122,7 +122,7 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
   mem.foreach { entry =>
     when(entry.valid &&
       (backendRedirect.reduce(_ || _) && entry.bits.ftqIdx > backendRedirectPtr ||
-        io.bpuEnqueue && entry.bits.ftqIdx === io.bpuEnqueuePtr)) {
+        io.bpuEnqueue && entry.bits.ftqIdx.value === io.bpuEnqueuePtr.value)) {
       entry.bits.flushed := true.B
     }
   }
```
