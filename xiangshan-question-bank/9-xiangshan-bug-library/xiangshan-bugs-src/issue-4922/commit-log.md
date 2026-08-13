# Commit Log
- Issue: #4922
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4922
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4922
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4922
- Changed files: 1
- Additions: 14
- Deletions: 6

## Files
- `src/main/scala/xiangshan/frontend/IFU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 32c39c1f078..d6d112cd145 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -669,9 +669,9 @@ class NewIFU(implicit p: Parameters) extends XSModule
   }).asUInt.orR
   val f3_mmio_req_commit = f3_req_is_mmio && mmio_state === m_commited
 
-  val f3_mmio_to_commit      = f3_req_is_mmio && mmio_state === m_waitCommit
-  val f3_mmio_to_commit_next = RegNext(f3_mmio_to_commit)
-  val f3_mmio_can_go         = f3_mmio_to_commit && !f3_mmio_to_commit_next
+  val f3_mmio_to_commit           = f3_req_is_mmio && mmio_state === m_waitCommit
+  val f3_mmio_sent_to_ibuffer     = RegInit(false.B)
+  val f3_mmio_can_sent_to_ibuffer = f3_mmio_to_commit && !f3_mmio_sent_to_ibuffer
 
   /*** Determine whether the MMIO instruction is executable based on the previous prediction block ***/
   io.mmioCommitRead.valid      := RegNext(f3_req_is_mmio && f3_valid, false.B)
@@ -690,6 +690,14 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   val f3_need_not_flush = f3_req_is_mmio && fromFtqRedirectReg.valid && !f3_ftq_flush_self && !f3_ftq_flush_by_older
 
+  when(!f3_req_is_mmio) {
+    f3_mmio_sent_to_ibuffer := false.B
+  }.elsewhen(f3_mmio_req_commit || (mmioF3Flush && !f3_need_not_flush) || f3_ftq_flush_self || f3_ftq_flush_by_older) {
+    f3_mmio_sent_to_ibuffer := false.B
+  }.elsewhen(io.toIbuffer.fire) {
+    f3_mmio_sent_to_ibuffer := true.B
+  }
+
   /**
     **********************************************************************************
     * We want to defer instruction fetching when encountering MMIO instructions to ensure that the MMIO region is not negatively impacted.
@@ -807,8 +815,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
     is(m_waitCommit) {
       // in idempotent spaces, we can skip waiting for commit (i.e. can do speculative fetch)
-      // but we do not skip m_waitCommit state, as other signals (e.g. f3_mmio_can_go relies on this)
-      mmio_state := Mux(mmio_commit || f3_itlb_pbmt === Pbmt.nc, m_commited, m_waitCommit)
+      // but we do not skip m_waitCommit state, as other signals (e.g. f3_mmio_can_sent_to_ibuffer relies on this)
+      mmio_state := Mux(mmio_commit || ((f3_itlb_pbmt === Pbmt.nc) && io.toIbuffer.ready), m_commited, m_waitCommit)
     }
 
     // normal mmio instruction
@@ -927,7 +935,7 @@ class NewIFU(implicit p: Parameters) extends XSModule
   frontendTrigger.io.frontendTrigger := io.frontendTrigger
 
   val f3_triggered       = frontendTrigger.io.triggered
-  val f3_toIbuffer_valid = f3_valid && (!f3_req_is_mmio || f3_mmio_can_go) && !f3_flush
+  val f3_toIbuffer_valid = f3_valid && (!f3_req_is_mmio || f3_mmio_can_sent_to_ibuffer) && !f3_flush
 
   /*** send to Ibuffer  ***/
   io.toIbuffer.valid          := f3_toIbuffer_valid
```
