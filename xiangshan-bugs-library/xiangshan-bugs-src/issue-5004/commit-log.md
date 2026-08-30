# Commit Log
- Issue: #5004
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5004
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5004
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5004
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
index 8de6d6059eb..6ecfcf153da 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
@@ -158,7 +158,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
 
   // init a new entry
   private def initEntryIfNotUseful(notUseful: Bool): Unit =
-    when(notUseful && t1_actualTaken) { // only train taken branches to ubtb
+    when(notUseful) {
       t1_updatedEntry.valid := true.B
       t1_updatedEntry.tag   := t1_tag
       t1_updatedEntry.usefulCnt.resetPositive() // usefulCnt inits at strong positive, in/decrease by policy
@@ -241,7 +241,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   // select the entry: if hit, use the hit entry, otherwise use the victim from replacer (first not useful, or Plru)
   t1_updateIdx := Mux(t1_hit, t1_hitIdx, replacer.io.victim)
   // and write back the updated entry
-  when(t1_valid) {
+  when(t1_valid && (t1_hit || t1_actualTaken)) { // update entry if hit, or alloc entry only for taken branches
     entries(t1_updateIdx) := t1_updatedEntry
   }
```
