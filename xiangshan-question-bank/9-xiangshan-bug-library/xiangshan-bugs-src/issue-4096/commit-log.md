# Commit Log
- Issue: #4096
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4096
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4096
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4096
- Changed files: 2
- Additions: 5
- Deletions: 4

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `utility`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index 9eddb51e688..f33f748a8cc 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -324,11 +324,12 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
    *    ready: freelist can allocate
    ******************************************************************/
   
-  val s1_req = VecInit(io.req.map(_.bits))
-  val s1_valid = VecInit(io.req.map(_.valid))
+  val s1_sortedVec = HwSort(VecInit(io.req.map { case x => DataWithPtr(x.valid, x.bits, x.bits.uop.robIdx) }))
+  val s1_req = VecInit(s1_sortedVec.map(_.bits))
+  val s1_valid = VecInit(s1_sortedVec.map(_.valid))
   val s2_enqueue = Wire(Vec(LoadPipelineWidth, Bool()))
   io.req.zipWithIndex.foreach{ case (r, i) =>
-    r.ready := !s2_enqueue(i) || freeList.io.canAllocate(i)
+    r.ready := true.B
   }
 
   // s2: enqueue
diff --git a/utility b/utility
index b42008b7e9a..8de7c4243d7 160000
--- a/utility
+++ b/utility
@@ -1 +1 @@
-Subproject commit b42008b7e9ad0e908ee87e1fa13f94dffd7cfc77
+Subproject commit 8de7c4243d731d1baec9ca8e22bb40bd0543f2e0
```
