# Commit Log
- Issue: #5364
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5364
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5364
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5364
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/Parameters.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 368bd04ea7c..e55d0e1a6f6 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -169,7 +169,7 @@ case class XSCoreParameters
   LoadQueueRAWSize: Int = 32, // NOTE: make sure that LoadQueueRAWSize is power of 2.
   RollbackGroupSize: Int = 8,
   LoadQueueReplaySize: Int = 72,
-  LoadUncacheBufferSize: Int = 4,
+  LoadUncacheBufferSize: Int = 16,
   LoadQueueNWriteBanks: Int = 8, // NOTE: make sure that LoadQueueRARSize/LoadQueueRAWSize is divided by LoadQueueNWriteBanks
   StoreQueueSize: Int = 56,
   StoreQueueNWriteBanks: Int = 8, // NOTE: make sure that StoreQueueSize is divided by StoreQueueNWriteBanks
@@ -233,7 +233,7 @@ case class XSCoreParameters
   VSUopWritebackWidth: Int = 1,
   VSegmentBufferSize: Int = 8,
   // ==============================
-  UncacheBufferSize: Int = 4,
+  UncacheBufferSize: Int = 16,
   EnableLoadToLoadForward: Boolean = false,
   EnableFastForward: Boolean = true,
   EnableLdVioCheckAfterReset: Boolean = true,
```
