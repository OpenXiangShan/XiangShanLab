# Commit Log
- Issue: #4977
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4977
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4977
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4977
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
index f87f3a65029..8de6d6059eb 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
@@ -158,7 +158,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
 
   // init a new entry
   private def initEntryIfNotUseful(notUseful: Bool): Unit =
-    when(notUseful) {
+    when(notUseful && t1_actualTaken) { // only train taken branches to ubtb
       t1_updatedEntry.valid := true.B
       t1_updatedEntry.tag   := t1_tag
       t1_updatedEntry.usefulCnt.resetPositive() // usefulCnt inits at strong positive, in/decrease by policy
```
