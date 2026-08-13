# Commit Log
- Issue: #5630
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5630
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5630
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5630
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/dcache/Uncache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/Uncache.scala b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
index f7ecf99570b..5fffb703099 100644
--- a/src/main/scala/xiangshan/cache/dcache/Uncache.scala
+++ b/src/main/scala/xiangshan/cache/dcache/Uncache.scala
@@ -155,7 +155,7 @@ class UncacheEntryState(implicit p: Parameters) extends DCacheBundle {
   def can2Lsq(): Bool = valid && waitReturn
   def canMerge(): Bool = valid && !inflight
   def isFwdOld(): Bool = valid && (inflight || waitReturn)
-  def isFwdNew(): Bool = valid && !inflight && !waitReturn
+  def isFwdNew(): Bool = valid && !inflight && !waitReturn && waitSame
 
   def setValid(x: Bool): Unit = { valid := x}
   def setInflight(x: Bool): Unit = { inflight := x}
@@ -298,7 +298,7 @@ class UncacheImp(outer: Uncache)extends LazyModuleImp(outer)
 
   def canMergeSecondary(eid: UInt): Bool = {
     // old entry is not inflight and senting
-    states(eid).canMerge() && !(q0_canSent && q0_canSentIdx === eid)
+    states(eid).canMerge() && !(mem_acquire.fire && q0_canSentIdx === eid)
   }
 
   /******************************************************************
```
