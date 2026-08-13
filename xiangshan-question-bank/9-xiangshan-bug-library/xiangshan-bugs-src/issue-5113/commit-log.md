# Commit Log
- Issue: #5113
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5113
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5113
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5113
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
index c322deec887..27484bd2428 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
@@ -170,7 +170,7 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
     case (((hit, entry), isCrossPage), i) =>
       hit && !isCrossPage && (
         (i / NumWay).U =/= s2_alignBankIdx ||
-          entry.position > getAlignedInstOffset(s2_startVAddr)
+          entry.position >= getAlignedInstOffset(s2_startVAddr)
       )
   }
   private val s2_targets =
```
