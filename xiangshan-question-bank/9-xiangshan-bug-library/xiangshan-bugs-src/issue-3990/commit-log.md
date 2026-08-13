# Commit Log
- Issue: #3990
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3990
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3990
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3990
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 8555f27a7b9..c8f103b8e6b 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -689,7 +689,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
   val resetVstart = dirty_vs && !io.vstartIsZero
 
-  vecExcpInfo.valid := exceptionHappen && exceptionDataRead.bits.vstartEn && exceptionDataRead.bits.isVecLoad && !exceptionDataRead.bits.isEnqExcp
+  vecExcpInfo.valid := exceptionHappen && !intrEnable && exceptionDataRead.bits.vstartEn && exceptionDataRead.bits.isVecLoad && !exceptionDataRead.bits.isEnqExcp
   when (exceptionHappen) {
     vecExcpInfo.bits.nf := exceptionDataRead.bits.nf
     vecExcpInfo.bits.vsew := exceptionDataRead.bits.vsew
```
