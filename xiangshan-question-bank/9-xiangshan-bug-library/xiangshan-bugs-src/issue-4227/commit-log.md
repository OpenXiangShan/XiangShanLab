# Commit Log
- Issue: #4227
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4227
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4227
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4227
- Changed files: 1
- Additions: 18
- Deletions: 15

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 56c7626f849..809896b9e97 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -273,21 +273,24 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
           isCrossPage := false.B
           needFlushPipe := false.B
         }
-      }
-      when (io.writeBack.fire && (!isCrossPage || globalMMIO || globalException)) {
-        bufferState := s_idle
-        req_valid := false.B
-        curPtr := 0.U
-        unSentStores := 0.U
-        unWriteStores := 0.U
-        globalException := false.B
-        globalMMIO := false.B
-        isCrossPage := false.B
-        needFlushPipe := false.B
-      } .elsewhen(io.writeBack.fire && isCrossPage) {
-        bufferState := s_block
-      } .otherwise {
-        bufferState := s_wb
+
+      }.otherwise {
+        when (io.writeBack.fire && (!isCrossPage || globalMMIO || globalException)) {
+          bufferState := s_idle
+          req_valid := false.B
+          curPtr := 0.U
+          unSentStores := 0.U
+          unWriteStores := 0.U
+          globalException := false.B
+          globalMMIO := false.B
+          isCrossPage := false.B
+          needFlushPipe := false.B
+        } .elsewhen(io.writeBack.fire && isCrossPage) {
+          bufferState := s_block
+        } .otherwise {
+          bufferState := s_wb
+        }
+
       }
     }
```
