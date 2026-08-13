# Commit Log
- Issue: #5383
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5383
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5383
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5383
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
index 27e4c277bf0..60cd677138c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
@@ -195,6 +195,6 @@ class MainBtbInternalBank(
 
   XSPerfAccumulate(
     "counter_writebuffer_drop_write",
-    counterWriteBuffer.io.enq.ready && counterWriteBuffer.io.enq.valid
+    !counterWriteBuffer.io.enq.ready && counterWriteBuffer.io.enq.valid
   )
 }
```
