# Commit Log
- Issue: #5035
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5035
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5035
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5035
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index dd3981dd84f..8c52a1bb011 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -117,8 +117,8 @@ class Ftq(implicit p: Parameters) extends FtqModule
   private val backendExceptionPtr = RegInit(FtqPtr(false.B, 0.U))
   when(backendRedirect.valid) {
     val exception = ExceptionType.fromBackend(backendRedirect.bits)
+    backendException := exception
     when(exception.hasException) {
-      backendException    := exception
       backendExceptionPtr := ifuWbPtr(0)
     }
   }.elsewhen(ifuWbPtr(0) =/= backendExceptionPtr) {
```
