# Commit Log
- Issue: #4874
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4874
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4874
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4874
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index b454b841e3d..a4d21aa85d5 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -397,6 +397,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
       }
     }
     uops(i).eliminatedMove := isMove(i)
+    uops(i).isMove := isMove(i)
 
     // update pdest
     uops(i).pdest := MuxCase(0.U, Seq(
```
