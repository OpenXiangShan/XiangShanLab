# Commit Log
- Issue: #3358
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3358
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3358
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3358
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
index 56a0a8e7cbf..83ade04b234 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
@@ -78,7 +78,7 @@ class indexedLSUopTable(uopIdx:Int) extends Module {
 
 trait VectorConstants {
   val MAX_VLMUL = 8
-  val VECTOR_TMP_REG_LMUL = 33 // 33~47  ->  15
+  val VECTOR_TMP_REG_LMUL = 32 // 32~46  ->  15
   val VECTOR_COMPRESS = 1 // in v0 regfile
   val MAX_INDEXED_LS_UOPNUM = 64
 }
```
