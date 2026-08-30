# Commit Log
- Issue: #5255
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5255
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5255
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5255
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
index bc0200fb44e..2bf597126b9 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
@@ -114,9 +114,10 @@ class MainBtbInternalBank(
   }
 
   // io -> writeBuffer
+  private val conflict = w.req.valid && w.req.bits.setIdx === flush.req.bits.setIdx && w.req.bits.entry.tag === 0.U
   writeBuffer.io.write.zipWithIndex.foreach { case (bufWrite, i) =>
     val writeValid = w.req.valid && w.req.bits.wayMask(i)
-    val flushValid = flush.req.valid && flush.req.bits.wayMask(i)
+    val flushValid = flush.req.valid && flush.req.bits.wayMask(i) && !conflict
     bufWrite.valid := writeValid || flushValid
     bufWrite.bits.setIdx := Mux(
       writeValid,
```
