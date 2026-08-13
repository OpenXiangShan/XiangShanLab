# Commit Log
- Issue: #4881
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4881
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4881
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4881
- Changed files: 3
- Additions: 9
- Deletions: 6

## Files
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/FrontendBundle.scala b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
index 2d4ea5a07ba..5e2c59b68a8 100644
--- a/src/main/scala/xiangshan/frontend/FrontendBundle.scala
+++ b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
@@ -103,6 +103,7 @@ class PredecodeWritebackBundle(implicit p: Parameters) extends XSBundle {
 }
 
 class mmioCommitRead(implicit p: Parameters) extends XSBundle {
+  val valid          = Output(Bool())
   val mmioFtqPtr     = Output(new FtqPtr)
   val mmioLastCommit = Input(Bool())
 }
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 885e154abcb..566d857db57 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -654,9 +654,6 @@ class NewIFU(implicit p: Parameters) extends XSModule
   // last instuction finish
   val is_first_instr = RegInit(true.B)
 
-  /*** Determine whether the MMIO instruction is executable based on the previous prediction block ***/
-  io.mmioCommitRead.mmioFtqPtr := RegNext(f3_ftq_req.ftqIdx - 1.U)
-
   val m_idle :: m_waitLastCmt :: m_sendReq :: m_waitResp :: m_sendTLB :: m_tlbResp :: m_sendPMP :: m_resendReq :: m_waitResendResp :: m_waitCommit :: m_commited :: Nil =
     Enum(11)
   val mmio_state = RegInit(m_idle)
@@ -676,6 +673,10 @@ class NewIFU(implicit p: Parameters) extends XSModule
   val f3_mmio_to_commit_next = RegNext(f3_mmio_to_commit)
   val f3_mmio_can_go         = f3_mmio_to_commit && !f3_mmio_to_commit_next
 
+  /*** Determine whether the MMIO instruction is executable based on the previous prediction block ***/
+  io.mmioCommitRead.valid      := RegNext(f3_req_is_mmio && f3_valid, false.B)
+  io.mmioCommitRead.mmioFtqPtr := RegNext(f3_ftq_req.ftqIdx - 1.U)
+
   val fromFtqRedirectReg = Wire(fromFtq.redirect.cloneType)
   fromFtqRedirectReg.bits := RegEnable(
     fromFtq.redirect.bits,
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index 91d4a3d7b78..54f9d2b66e5 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -1401,9 +1401,10 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     * MMIO instruction fetch is allowed only if MMIO is the oldest instruction.
     *************************************************************************************
     */
-  val mmioReadPtr = io.mmioCommitRead.mmioFtqPtr
-  val mmioLastCommit = isAfter(commPtr, mmioReadPtr) ||
-    commPtr === mmioReadPtr && validInstructions.reduce(_ || _) && lastInstructionStatus === c_committed
+  val mmioReadPtr   = io.mmioCommitRead.mmioFtqPtr
+  val mmioReadValid = io.mmioCommitRead.valid
+  val mmioLastCommit = mmioReadValid && (isAfter(commPtr, mmioReadPtr) ||
+    commPtr === mmioReadPtr && validInstructions.reduce(_ || _) && lastInstructionStatus === c_committed)
   io.mmioCommitRead.mmioLastCommit := RegNext(mmioLastCommit)
 
   // commit reads
```
