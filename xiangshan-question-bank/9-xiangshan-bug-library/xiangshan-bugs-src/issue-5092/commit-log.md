# Commit Log
- Issue: #5092
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5092
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5092
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5092
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
index 78d0b2a08e3..92596406d3f 100644
--- a/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
@@ -70,8 +70,8 @@ class ResolveQueue(implicit p: Parameters) extends FtqModule with HalfAlignHelpe
       mem(enqIndex(i)).bits.ftqIdx     := branch.bits.ftqIdx
       mem(enqIndex(i)).bits.startVAddr := branch.bits.pc
 
-      val lastValid  = mem(enqIndex(i)).bits.branches.lastIndexWhere(_.valid)
-      val branchSlot = mem(enqIndex(i)).bits.branches(lastValid + PopCount(hitPrevious(i)) + 1.U)
+      val firstEmpty = mem(enqIndex(i)).bits.branches.indexWhere(!_.valid)
+      val branchSlot = mem(enqIndex(i)).bits.branches(firstEmpty + PopCount(hitPrevious(i)))
       branchSlot.valid            := true.B
       branchSlot.bits.target      := branch.bits.target
       branchSlot.bits.taken       := branch.bits.taken
```
