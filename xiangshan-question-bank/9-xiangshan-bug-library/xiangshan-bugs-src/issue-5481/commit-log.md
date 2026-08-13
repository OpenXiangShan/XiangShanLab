# Commit Log
- Issue: #5481
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5481
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5481
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5481
- Changed files: 1
- Additions: 1
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 4485a364b09..7d2026ea6fd 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -181,12 +181,11 @@ class Ftq(implicit p: Parameters) extends FtqModule
     entryQueue(predictionPtr.value).takenCfiOffset := prediction.bits.takenCfiOffset
   }
 
-  speculationQueue(io.fromBpu.s3FtqPtr.value) := io.fromBpu.speculationMeta.bits
-
   when(io.fromBpu.meta.valid) {
     val s3BpuPtr = io.fromBpu.s3FtqPtr.value
     metaQueueResolve(s3BpuPtr) := io.fromBpu.meta.bits
     metaQueueCommit(s3BpuPtr)  := io.fromBpu.meta.bits.ras
+    speculationQueue(s3BpuPtr) := io.fromBpu.speculationMeta.bits
 
     perfQueue(s3BpuPtr).bpuPerf := io.fromBpu.perfMeta
     perfQueue(s3BpuPtr).isCfi.foreach(_ := false.B)
```
