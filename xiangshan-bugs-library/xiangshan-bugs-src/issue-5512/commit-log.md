# Commit Log
- Issue: #5512
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5512
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5512
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5512
- Changed files: 2
- Additions: 2
- Deletions: 0

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index 7f7b6cc13a2..c35d207a1b7 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -274,6 +274,7 @@ class LoadQueue(implicit p: Parameters) extends XSModule
     exceptionBuffer.io.req(LoadPipelineWidth + i).bits.gpaddr           := io.vecFeedback(i).bits.gpaddr
     exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.uopIdx       := io.vecFeedback(i).bits.uopidx
     exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.robIdx       := io.vecFeedback(i).bits.robidx
+    exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.lqIdx        := io.vecFeedback(i).bits.lqIdx
     exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.vpu.vstart   := io.vecFeedback(i).bits.vstart
     exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.vpu.vl       := io.vecFeedback(i).bits.vl
     exceptionBuffer.io.req(LoadPipelineWidth + i).bits.uop.exceptionVec := io.vecFeedback(i).bits.exceptionVec
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 985116977d8..1910325f80d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -235,6 +235,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
     exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.gpaddr         := io.vecFeedback(i).bits.gpaddr
     exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.uopIdx     := io.vecFeedback(i).bits.uopidx
     exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.robIdx     := io.vecFeedback(i).bits.robidx
+    exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.sqIdx      := io.vecFeedback(i).bits.sqIdx
     exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.vpu.vstart := io.vecFeedback(i).bits.vstart
     exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.uop.vpu.vl     := io.vecFeedback(i).bits.vl
     exceptionBuffer.io.storeAddrIn(StorePipelineWidth * 2 + i).bits.isForVSnonLeafPTE := io.vecFeedback(i).bits.isForVSnonLeafPTE
```
