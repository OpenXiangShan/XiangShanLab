# Commit Log
- Issue: #5734
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5734
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5734
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5734
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 41f7fcdcc7e..11fd4ed3b94 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -697,9 +697,9 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
 
   XSPerfAccumulate(
     "train",
-    io.fromFtq.train.fire,
+    io.fromFtq.train.valid,
     Seq(
-      ("total", true.B),
+      ("total", io.fromFtq.train.ready),
       ("stall", !io.fromFtq.train.ready)
     )
   )
```
