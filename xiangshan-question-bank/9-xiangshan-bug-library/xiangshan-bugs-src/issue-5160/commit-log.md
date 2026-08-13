# Commit Log
- Issue: #5160
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5160
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5160
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5160
- Changed files: 2
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index aefed20c31c..6bc145030b5 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -253,8 +253,8 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
     MuxCase(
       fallThrough.io.prediction,
       Seq(
-        ubtb.io.prediction.taken -> ubtb.io.prediction
-//      abtb.io.prediction.taken -> abtb.io.prediction
+        ubtb.io.prediction.taken -> ubtb.io.prediction,
+        abtb.io.prediction.taken -> abtb.io.prediction
       )
     )
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
index d82b170ab13..a884f1dd03b 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
@@ -257,7 +257,7 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers {
     case ((hit, position), attribute) =>
       hit && position === t1_trainPosition && attribute === t1_trainAttribute
   }.reduce(_ || _) && t1_trainTaken
-  private val t1_needWriteNewEntry = !t1_hitTakenBranch
+  private val t1_needWriteNewEntry = !t1_hitTakenBranch && t1_trainTaken
 
   // If the target of indirect branch is wrong, we need correct it.
   // Since the entry only stores the lower bits of the target, we only need to check the lower bits.
```
