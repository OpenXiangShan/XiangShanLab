# Commit Log
- Issue: #5149
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5149
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5149
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5149
- Changed files: 2
- Additions: 22
- Deletions: 4

## Files
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`
- `src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 4b46b5c8b50..54ff4429933 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -292,8 +292,8 @@ class Ftq(implicit p: Parameters) extends FtqModule
   io.toBpu.redirect.bits.speculationMeta := speculationQueue(redirect.bits.ftqIdx.value)
   io.toBpu.redirectFromIFU               := ifuRedirect.valid
 
-  resolveQueue.io.backendRedirect    := DelayN(backendRedirect.valid, 2)
-  resolveQueue.io.backendRedirectPtr := DelayN(backendRedirect.bits.ftqIdx, 2)
+  resolveQueue.io.backendRedirect    := backendRedirect.valid
+  resolveQueue.io.backendRedirectPtr := backendRedirect.bits.ftqIdx
 
   // --------------------------------------------------------------------------------
   // Resolve and train BPU
diff --git a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
index 816094cbf8e..da8b37587ca 100644
--- a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
@@ -89,9 +89,27 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
     }
   }
 
-  when(io.backendRedirect) {
+  // Branches that have been issued to functional units cannot be flushed by redirects. Therefore, these branches will
+  // be resolved. However, the meta of these branches may already be overwritten by new branches, which means they
+  // cannot be updated by BPU. To handle this case, backend redirect will be propagated for several cycles to make sure
+  // newly resolved branches can be flushed correctly.
+  // 3 cycles may be enough, we'll see in practice.
+  private def RedirectDelay   = 3
+  private val backendRedirect = WireDefault(VecInit.fill(RedirectDelay)(false.B))
+  backendRedirect := VecInit((0 until RedirectDelay).map(i =>
+    if (i == 0) io.backendRedirect else RegNext(backendRedirect(i - 1))
+  ))
+  private val backendRedirectPtr = RegEnable(io.backendRedirectPtr, FtqPtr(false.B, 0.U), io.backendRedirect)
+  XSError(
+    RegNext(backendRedirect(RedirectDelay - 1)) && !backendRedirect.reduce(_ || _) && io.backendResolve.map(branch =>
+      branch.valid && branch.bits.ftqIdx > backendRedirectPtr
+    ).reduce(_ || _),
+    "Backend resolves branches that should have been flushed\n"
+  )
+
+  when(backendRedirect.reduce(_ || _)) {
     mem.foreach(entry =>
-      when(entry.valid)(entry.bits.flushed := entry.bits.flushed || entry.bits.ftqIdx > io.backendRedirectPtr)
+      when(entry.valid)(entry.bits.flushed := entry.bits.flushed || entry.bits.ftqIdx > backendRedirectPtr)
     )
   }
```
