# Commit Log
- Issue: #4593
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4593
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4593
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4593
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/vector/VecCommon.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VecCommon.scala b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
index 4e1e674a36a..a55499182e7 100644
--- a/src/main/scala/xiangshan/mem/vector/VecCommon.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecCommon.scala
@@ -610,7 +610,7 @@ object GenElemIdx extends VLSUConstants {
       eewUopFlowsLog2
     )
     LookupTree(uopFlowsLog2, List(
-      0.U -> uopIdx,
+      0.U -> uopIdx ## flowIdx(0), // for hardware misalign
       1.U -> uopIdx ## flowIdx(0),
       2.U -> uopIdx ## flowIdx(1, 0),
       3.U -> uopIdx ## flowIdx(2, 0),
```
