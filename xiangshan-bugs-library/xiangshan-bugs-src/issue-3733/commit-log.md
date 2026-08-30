# Commit Log
- Issue: #3733
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3733
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3733
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3733
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index 24cd5509e7c..95ef21a2a22 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -272,7 +272,7 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
         entry.isForVSnonLeafPTE := selPort(0).isForVSnonLeafPTE
       }.otherwise{
         entry.uop.vpu.vta  := VType.tu
-        entry.vl           := vstart
+        entry.vl           := Mux(entry.vl < vstart, entry.vl, vstart)
       }
     }
   }
```
