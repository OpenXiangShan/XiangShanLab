# Commit Log
- Issue: #5743
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5743
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5743
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5743
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 980ddd75e6b..83b5955f8e5 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -731,7 +731,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     }
     io.out(i).bits.psrcVl := MuxCase(
       uops(i).psrcVl,
-      (bypassCondVl(i-1).asBools zip io.out.take(i).map(_.bits.pdest)).reverse
+      (bypassCondVl(i-1).asBools zip io.out.take(i).map(_.bits.pdestVl)).reverse
     )
     io.out(i).bits.pdest := Mux(isMove(i), io.out(i).bits.psrcIntForMove, uops(i).pdest)
```
