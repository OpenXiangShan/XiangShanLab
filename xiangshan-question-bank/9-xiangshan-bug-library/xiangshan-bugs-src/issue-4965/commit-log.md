# Commit Log
- Issue: #4965
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4965
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4965
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4965
- Changed files: 1
- Additions: 0
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 6d1e3df8088..3339ab2c606 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -479,7 +479,6 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     out.elemIdx       := src.elemIdx
     out.elemIdxInsideVd := src.elemIdxInsideVd
     out.alignedType   := src.alignedType
-    out.isnc          := src.nc
     out.data          := src.data
     out
   }
```
