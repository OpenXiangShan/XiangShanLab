# Commit Log
- Issue: #5345
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5345
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5345
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5345
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
index 1eae25c2f87..d7691cd2382 100644
--- a/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala
@@ -305,7 +305,7 @@ class Tage(implicit p: Parameters) extends BasePredictor with HasTageParameters
 //  }
 
   private val t1_cfiPcVec =
-    VecInit(t1_branches.map(branch => getCfiPcFromPosition(s2_startPc, branch.bits.cfiPosition)))
+    VecInit(t1_branches.map(branch => getCfiPcFromPosition(t1_startPc, branch.bits.cfiPosition)))
   private val t1_cfiUseAltIdxVec = VecInit(t1_cfiPcVec.map(getUseAltIndex))
 
   /* --------------------------------------------------------------------------------------------------------------
```
