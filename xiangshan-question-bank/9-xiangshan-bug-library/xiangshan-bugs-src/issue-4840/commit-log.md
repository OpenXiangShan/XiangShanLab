# Commit Log
- Issue: #4840
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4840
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4840
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4840
- Changed files: 2
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index d39f46166df..f6c6af25b11 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -510,7 +510,7 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
   io.splitLoadReq.bits.uop.fuOpType := Mux(req.isvec, req.uop.fuOpType, Cat(reqIsHlv, reqIsHlvx, 0.U(1.W), splitLoadReqs(curPtr).uop.fuOpType(1, 0)))
   io.splitLoadReq.bits.alignedType  := Mux(req.isvec, splitLoadReqs(curPtr).uop.fuOpType(1, 0), req.alignedType)
 
-  when (io.splitLoadResp.valid) {
+  when (io.splitLoadResp.valid && bufferState === s_resp && req.uop.robIdx === io.splitLoadResp.bits.uop.robIdx) {
     val resp = io.splitLoadResp.bits
     splitLoadResp(curPtr) := io.splitLoadResp.bits
     when (isUncache) {
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 6ac438c2441..9e8a28ead64 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -558,7 +558,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   io.splitStoreReq.bits.alignedType  := Mux(req.isvec, splitStoreReqs(curPtr).uop.fuOpType(1, 0), req.alignedType)
   io.splitStoreReq.bits.isFinalSplit := curPtr(0)
 
-  when (io.splitStoreResp.valid) {
+  when (io.splitStoreResp.valid && bufferState === s_resp && req.uop.robIdx === io.splitStoreResp.bits.uop.robIdx) {
     val resp = io.splitStoreResp.bits
     splitStoreResp(curPtr) := io.splitStoreResp.bits
     when (isUncache) {
```
