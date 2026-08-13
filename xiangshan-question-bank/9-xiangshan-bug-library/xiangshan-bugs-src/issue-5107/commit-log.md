# Commit Log
- Issue: #5107
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5107
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5107
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5107
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
index 08e49f72e38..1108bbda285 100644
--- a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
@@ -86,7 +86,9 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
   }
 
   when(io.backendRedirect) {
-    mem.foreach(entry => entry.bits.flushed := entry.bits.flushed || entry.bits.ftqIdx > io.backendRedirectPtr)
+    mem.foreach(entry =>
+      when(entry.valid)(entry.bits.flushed := entry.bits.flushed || entry.bits.ftqIdx > io.backendRedirectPtr)
+    )
   }
 
   private val deqValid = mem(deqPtr.value).valid && !io.backendResolve.map(branch =>
```
