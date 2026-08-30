# Commit Log
- Issue: #4072
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4072
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4072
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4072
- Changed files: 1
- Additions: 4
- Deletions: 3

## Files
- `src/main/scala/xiangshan/frontend/NewFtq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index 19465ce2b1d..2869b19c87e 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -1362,9 +1362,10 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   val bpu_ftb_update_stall    = RegInit(0.U(2.W)) // 2-cycle stall, so we need 3 states
   may_have_stall_from_bpu := bpu_ftb_update_stall =/= 0.U
 
-  val validInstructions       = commitStateQueueReg(commPtr.value).map(s => s === c_toCommit || s === c_committed)
-  val lastInstructionStatus   = PriorityMux(validInstructions.reverse.zip(commitStateQueueReg(commPtr.value).reverse))
-  val firstInstructionFlushed = commitStateQueueReg(commPtr.value)(0) === c_flushed
+  val validInstructions     = commitStateQueueReg(commPtr.value).map(s => s === c_toCommit || s === c_committed)
+  val lastInstructionStatus = PriorityMux(validInstructions.reverse.zip(commitStateQueueReg(commPtr.value).reverse))
+  val firstInstructionFlushed = commitStateQueueReg(commPtr.value)(0) === c_flushed ||
+    commitStateQueueReg(commPtr.value)(0) === c_empty && commitStateQueueReg(commPtr.value)(1) === c_flushed
   canCommit := commPtr =/= ifuWbPtr && !may_have_stall_from_bpu &&
     (isAfter(robCommPtr, commPtr) ||
       validInstructions.reduce(_ || _) && lastInstructionStatus === c_committed)
```
