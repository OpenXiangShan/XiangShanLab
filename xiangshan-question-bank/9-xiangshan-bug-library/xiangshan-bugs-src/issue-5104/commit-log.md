# Commit Log
- Issue: #5104
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5104
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5104
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5104
- Changed files: 1
- Additions: 7
- Deletions: 3

## Files
- `src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
index 92596406d3f..08e49f72e38 100644
--- a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
@@ -39,10 +39,14 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
   private val deqPtr = RegInit(ResolveQueuePtr(false.B, 0.U))
 
   private val hit = io.backendResolve.map { branch =>
-    mem.map(entry => branch.valid && entry.valid && entry.bits.ftqIdx === branch.bits.ftqIdx).reduce(_ || _)
+    mem.map(entry =>
+      branch.valid && entry.valid && !entry.bits.flushed && entry.bits.ftqIdx === branch.bits.ftqIdx
+    ).reduce(_ || _)
   }
   private val hitIndex = io.backendResolve.map { branch =>
-    mem.indexWhere(entry => branch.valid && entry.valid && entry.bits.ftqIdx === branch.bits.ftqIdx)
+    mem.indexWhere(entry =>
+      branch.valid && entry.valid && !entry.bits.flushed && entry.bits.ftqIdx === branch.bits.ftqIdx
+    )
   }
   private val hitPrevious = io.backendResolve.zipWithIndex.map { case (branch, i) =>
     io.backendResolve.take(i).map(previousBranch =>
@@ -82,7 +86,7 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
   }
 
   when(io.backendRedirect) {
-    mem.foreach(entry => entry.bits.flushed := entry.bits.ftqIdx > io.backendRedirectPtr)
+    mem.foreach(entry => entry.bits.flushed := entry.bits.flushed || entry.bits.ftqIdx > io.backendRedirectPtr)
   }
 
   private val deqValid = mem(deqPtr.value).valid && !io.backendResolve.map(branch =>
```
