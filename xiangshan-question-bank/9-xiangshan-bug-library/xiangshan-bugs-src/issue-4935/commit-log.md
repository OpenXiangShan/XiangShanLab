# Commit Log
- Issue: #4935
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4935
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4935
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4935
- Changed files: 1
- Additions: 4
- Deletions: 4

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index b5c9b43ba38..43c77424e3d 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -565,8 +565,8 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
       // cause llptw will trigger bitmapcheck
       // add a coniditonal logic
       // (s2x_info =/= allStage || ishptw)
-      hit := Mux(bitmapEnable && (s2x_info =/= allStage || ishptw), ParallelOR(hitVec) && l0bitmapreg(hitWay)(pte_index) === 1.U, ParallelOR(hitVec))
-      when (bitmapEnable && (s2x_info =/= allStage || ishptw) && ParallelOR(hitVec) && l0bitmapreg(hitWay)(pte_index) === 0.U) {
+      hit := Mux(bitmapEnable && (s2x_info =/= allStage || ishptw) && !hitWayData.onlypf(pte_index), ParallelOR(hitVec) && l0bitmapreg(hitWay)(pte_index) === 1.U, ParallelOR(hitVec))
+      when (bitmapEnable && (s2x_info =/= allStage || ishptw) && !hitWayData.onlypf(pte_index) && ParallelOR(hitVec) && l0bitmapreg(hitWay)(pte_index) === 0.U) {
         jmp_bitmap_check := true.B
       }
     } else {
@@ -651,8 +651,8 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     val jmp_bitmap_check  = WireInit(false.B)
     val hit = WireInit(false.B)
     if (HasBitmapCheck) {
-      hit := Mux(bitmapEnable && (s2x_info =/= allStage || ishptw), false.B, ParallelOR(hitVec))
-      when (bitmapEnable && (s2x_info =/= allStage || ishptw) && ParallelOR(hitVec)) {
+      hit := Mux(bitmapEnable && (s2x_info =/= allStage || ishptw) && hitData.v, false.B, ParallelOR(hitVec))
+      when (bitmapEnable && (s2x_info =/= allStage || ishptw) && hitData.v && ParallelOR(hitVec)) {
         jmp_bitmap_check := true.B
       }
     } else {
```
