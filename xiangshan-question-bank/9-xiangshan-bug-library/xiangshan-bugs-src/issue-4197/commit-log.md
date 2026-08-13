# Commit Log
- Issue: #4197
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4197
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4197
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4197
- Changed files: 2
- Additions: 6
- Deletions: 5

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `yunsuan`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index f17fc49fb9f..6d3aed5674c 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -543,10 +543,11 @@ class CtrlBlockImp(
   }.reduceTree(_ || _)
   val snptSelect = MuxCase(
     0.U(log2Ceil(RenameSnapshotNum).W),
-    (1 to RenameSnapshotNum).map(i => (snpt.io.enqPtr - i.U).value).map(idx =>
-      (snpt.io.valids(idx) && (s1_s3_redirect.bits.robIdx > snpt.io.snapshots(idx).robIdx.head ||
-        !s1_s3_redirect.bits.flushItself() && s1_s3_redirect.bits.robIdx === snpt.io.snapshots(idx).robIdx.head), idx)
-    )
+    (1 to RenameSnapshotNum).map(i => (snpt.io.enqPtr - i.U).value).map{case idx =>
+      val thisSnapRobidx = snpt.io.snapshots(idx).robIdx.head
+      (snpt.io.valids(idx) && (redirectRobidx > thisSnapRobidx && (redirectRobidx.value =/= thisSnapRobidx.value) ||
+        !s1_s3_redirect.bits.flushItself() && redirectRobidx === thisSnapRobidx), idx)
+    }
   )
 
   rob.io.snpt.snptEnq := DontCare
diff --git a/yunsuan b/yunsuan
index 3cd12ca678d..cadd3c2f430 160000
--- a/yunsuan
+++ b/yunsuan
@@ -1 +1 @@
-Subproject commit 3cd12ca678d74da71d6e50864497828151da4499
+Subproject commit cadd3c2f43096e253d61296b33ac697be8354e29
```
