# Commit Log
- Issue: #5079
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5079
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5079
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5079
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index d26c5b13bbd..c379deb461d 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -619,7 +619,7 @@ class CtrlBlockImp(
     when (fusionDecoder.io.out(i).valid) {
       fusionDecoder.io.out(i).bits.update(rename.io.in(i).bits)
       fusionDecoder.io.out(i).bits.update(dispatch.io.renameIn(i).bits)
-      val cross2Ftq = decodePipeRename(i).bits.isLastInFtqEntry && decodePipeRename(i + 1).bits.isLastInFtqEntry
+      val cross2Ftq = decodePipeRename(i).bits.isLastInFtqEntry && decodePipeRename(i + 1).bits.isLastInFtqEntry && backendParams.robCompressEn.B
       val cross1Ftq = decodePipeRename(i).bits.isLastInFtqEntry || decodePipeRename(i + 1).bits.isLastInFtqEntry
       rename.io.in(i + 1).bits.isLastInFtqEntry := cross1Ftq
       rename.io.in(i + 1).bits.canRobCompress := !cross2Ftq
```
