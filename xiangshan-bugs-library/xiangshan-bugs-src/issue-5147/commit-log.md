# Commit Log
- Issue: #5147
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5147
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5147
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5147
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala b/src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala
index d8f629a40ab..2fae2f0ecf4 100644
--- a/src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala
@@ -351,7 +351,9 @@ class IBuffer(implicit p: Parameters) extends IBufferModule with HasCircularQueu
     bypassHasExceptionExcludingRVCII && !useBypass
 
   // When exceptions are registered in IBuffer, set firstHasExceptionExcludingRVCII.
-  when(receiveExceptionFire && nextFirstHasExceptionExcludingRVCII) {
+  // We require numEnq to be non-zero to avoid the case when io.in.fire and numEnq is zero,
+  // i.e. current last instruction is half RVI
+  when(receiveExceptionFire && nextFirstHasExceptionExcludingRVCII && numEnq =/= 0.U) {
     firstHasExceptionExcludingRVCII := true.B
   }
```
