# Commit Log
- Issue: #5238
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5238
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5238
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5238
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
index da8b37587ca..0b1e274b807 100644
--- a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
@@ -18,6 +18,7 @@ package xiangshan.frontend.ftq
 import chisel3._
 import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
+import utility.DataHoldBypass
 import utility.HasCircularQueuePtrHelper
 import utility.XSError
 import utility.XSPerfAccumulate
@@ -99,7 +100,7 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
   backendRedirect := VecInit((0 until RedirectDelay).map(i =>
     if (i == 0) io.backendRedirect else RegNext(backendRedirect(i - 1))
   ))
-  private val backendRedirectPtr = RegEnable(io.backendRedirectPtr, FtqPtr(false.B, 0.U), io.backendRedirect)
+  private val backendRedirectPtr = DataHoldBypass(io.backendRedirectPtr, FtqPtr(false.B, 0.U), io.backendRedirect)
   XSError(
     RegNext(backendRedirect(RedirectDelay - 1)) && !backendRedirect.reduce(_ || _) && io.backendResolve.map(branch =>
       branch.valid && branch.bits.ftqIdx > backendRedirectPtr
```
