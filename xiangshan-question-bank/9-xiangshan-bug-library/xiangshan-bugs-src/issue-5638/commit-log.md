# Commit Log
- Issue: #5638
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5638
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5638
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5638
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
index 4ff54d81be0..9d1c1648d21 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
@@ -39,8 +39,8 @@ trait Helpers extends HasMainBtbParameters
     extraFields = Seq(
       ("replacerSetIdx", FetchBlockSizeWidth, SetIdxLen),
       ("targetLower", instOffsetBits, TargetWidth),
-      ("position", instOffsetBits, FetchBlockAlignWidth),
-      ("cfiPosition", instOffsetBits, FetchBlockSizeWidth)
+      ("position", instOffsetBits, CfiAlignedPositionWidth),
+      ("cfiPosition", instOffsetBits, CfiPositionWidth)
     )
   )
```
