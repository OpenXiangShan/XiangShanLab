# Commit Log
- Issue: #4086
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4086
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4086
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4086
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/Parameters.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 8514359e3ad..5003b059521 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -164,7 +164,7 @@ case class XSCoreParameters
   Vl_IDX: Int = 0,
   NRPhyRegs: Int = 192,
   VirtualLoadQueueSize: Int = 72,
-  LoadQueueRARSize: Int = 64,
+  LoadQueueRARSize: Int = 72,
   LoadQueueRAWSize: Int = 32, // NOTE: make sure that LoadQueueRAWSize is power of 2.
   RollbackGroupSize: Int = 8,
   LoadQueueReplaySize: Int = 72,
```
