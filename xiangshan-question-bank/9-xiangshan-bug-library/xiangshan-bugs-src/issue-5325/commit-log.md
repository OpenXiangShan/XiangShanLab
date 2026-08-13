# Commit Log
- Issue: #5325
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5325
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5325
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5325
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
index 3c6c0be4b6c..d022fe1e286 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Bundles.scala
@@ -70,7 +70,7 @@ class TageMeta(implicit p: Parameters) extends TageBundle {
 }
 
 class TageFoldedHist(implicit p: Parameters, info: TageTableInfo) extends TageBundle {
-  val forIdx: UInt = UInt(NumSets.W)
+  val forIdx: UInt = UInt(SetIdxWidth.W)
   val forTag: UInt = UInt(TagWidth.W)
 }
```
