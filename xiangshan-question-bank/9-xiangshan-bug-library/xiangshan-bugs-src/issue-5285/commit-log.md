# Commit Log
- Issue: #5285
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5285
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5285
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5285
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/Parameters.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 9896f726efd..368bd04ea7c 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -239,7 +239,7 @@ case class XSCoreParameters
   EnableLdVioCheckAfterReset: Boolean = true,
   EnableSoftPrefetchAfterReset: Boolean = true,
   EnableCacheErrorAfterReset: Boolean = true,
-  EnableAccurateLoadError: Boolean = true,
+  EnableAccurateLoadError: Boolean = false,
   EnableUncacheWriteOutstanding: Boolean = false,
   EnableHardwareStoreMisalign: Boolean = true,
   EnableHardwareLoadMisalign: Boolean = true,
```
