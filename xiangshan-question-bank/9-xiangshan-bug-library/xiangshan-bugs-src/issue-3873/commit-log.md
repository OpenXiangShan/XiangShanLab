# Commit Log
- Issue: #3873
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3873
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3873
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3873
- Changed files: 1
- Additions: 45
- Deletions: 17

## Files
- `src/main/scala/xiangshan/frontend/IFU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index aefcb387cf2..62068c15315 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -386,21 +386,30 @@ class NewIFU(implicit p: Parameters) extends XSModule
     .elsewhen(f1_fire && !f1_flush)(f2_valid := true.B)
     .elsewhen(f2_fire)(f2_valid := false.B)
 
-  val f2_exception        = VecInit((0 until PortNumber).map(i => fromICache(i).bits.exception))
+  val f2_exception_in     = VecInit((0 until PortNumber).map(i => fromICache(i).bits.exception))
   val f2_backendException = fromICache(0).bits.backendException
   // paddr and gpaddr of [startAddr, nextLineAddr]
   val f2_paddrs            = VecInit((0 until PortNumber).map(i => fromICache(i).bits.paddr))
   val f2_gpaddr            = fromICache(0).bits.gpaddr
   val f2_isForVSnonLeafPTE = fromICache(0).bits.isForVSnonLeafPTE
 
-  // FIXME: what if port 0 is not mmio, but port 1 is?
-  // cancel mmio fetch if exception occurs
-  val f2_mmio = !ExceptionType.hasException(f2_exception(0)) && (
-    fromICache(0).bits.pmp_mmio ||
-      // currently, we do not distinguish between Pbmt.nc and Pbmt.io
-      // anyway, they are both non-cacheable, and should be handled with mmio fsm and sent to Uncache module
-      Pbmt.isUncache(fromICache(0).bits.itlb_pbmt)
-  )
+  // FIXME: raise af if one fetch block crosses the cacheable-noncacheable boundary, might not correct
+  val f2_mmio_mismatch_exception = VecInit(Seq.fill(2)(Mux(
+    // not double-line, skip check
+    !fromICache(1).valid ||
+      // is double-line, ask for consistent pmp_mmio and itlb_pbmt value
+      fromICache(0).bits.pmp_mmio === fromICache(1).bits.pmp_mmio &&
+      fromICache(0).bits.itlb_pbmt === fromICache(1).bits.itlb_pbmt,
+    ExceptionType.none,
+    ExceptionType.af
+  )))
+
+  // merge exceptions
+  val f2_exception = ExceptionType.merge(f2_exception_in, f2_mmio_mismatch_exception)
+
+  // we need only the first port, as the second is asked to be the same
+  val f2_pmp_mmio  = fromICache(0).bits.pmp_mmio
+  val f2_itlb_pbmt = fromICache(0).bits.itlb_pbmt
 
   /**
     * reduce the number of registers, origin code
@@ -518,7 +527,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
   val f3_cut_data = RegEnable(f2_cut_data, f2_fire)
 
   val f3_exception        = RegEnable(f2_exception, f2_fire)
-  val f3_mmio             = RegEnable(f2_mmio, f2_fire)
+  val f3_pmp_mmio         = RegEnable(f2_pmp_mmio, f2_fire)
+  val f3_itlb_pbmt        = RegEnable(f2_itlb_pbmt, f2_fire)
   val f3_backendException = RegEnable(f2_backendException, f2_fire)
 
   val f3_instr = RegEnable(f2_instr, f2_fire)
@@ -615,7 +625,12 @@ class NewIFU(implicit p: Parameters) extends XSModule
     Enum(11)
   val mmio_state = RegInit(m_idle)
 
-  val f3_req_is_mmio = f3_mmio && f3_valid
+  // do mmio fetch only when pmp/pbmt shows it is a uncacheable address and no exception occurs
+  /* FIXME: we do not distinguish pbmt is NC or IO now
+   *        but we actually can do speculative execution if pbmt is NC, maybe fix this later for performance
+   */
+  val f3_req_is_mmio =
+    f3_valid && (f3_pmp_mmio || Pbmt.isUncache(f3_itlb_pbmt)) && !ExceptionType.hasException(f3_exception)
   val mmio_commit = VecInit(io.rob_commits.map { commit =>
     commit.valid && commit.bits.ftqIdx === f3_ftq_req.ftqIdx && commit.bits.ftqOffset === 0.U
   }).asUInt.orR
@@ -705,23 +720,36 @@ class NewIFU(implicit p: Parameters) extends XSModule
         // we are using a blocked tlb, so resp.fire must have !resp.bits.miss
         assert(!io.iTLBInter.resp.bits.miss, "blocked mode iTLB miss when resp.fire")
         val tlb_exception = ExceptionType.fromTlbResp(io.iTLBInter.resp.bits)
+        // if itlb re-check respond pbmt mismatch with previous check, must be access fault
+        val pbmt_mismatch_exception = Mux(
+          io.iTLBInter.resp.bits.pbmt(0) =/= f3_itlb_pbmt,
+          ExceptionType.af,
+          ExceptionType.none
+        )
+        val exception = ExceptionType.merge(tlb_exception, pbmt_mismatch_exception)
         // if tlb has exception, abort checking pmp, just send instr & exception to ibuffer and wait for commit
-        mmio_state := Mux(ExceptionType.hasException(tlb_exception), m_waitCommit, m_sendPMP)
+        mmio_state := Mux(ExceptionType.hasException(exception), m_waitCommit, m_sendPMP)
         // also save itlb response
         mmio_resend_addr              := io.iTLBInter.resp.bits.paddr(0)
-        mmio_resend_exception         := tlb_exception
+        mmio_resend_exception         := exception
         mmio_resend_gpaddr            := io.iTLBInter.resp.bits.gpaddr(0)
         mmio_resend_isForVSnonLeafPTE := io.iTLBInter.resp.bits.isForVSnonLeafPTE(0)
       }
     }
 
     is(m_sendPMP) {
-      // if pmp re-check does not respond mmio, must be access fault
-      val pmp_exception = Mux(io.pmp.resp.mmio, ExceptionType.fromPMPResp(io.pmp.resp), ExceptionType.af)
+      val pmp_exception = ExceptionType.fromPMPResp(io.pmp.resp)
+      // if pmp re-check respond mismatch with previous check, must be access fault
+      val mmio_mismatch_exception = Mux(
+        io.pmp.resp.mmio =/= f3_pmp_mmio,
+        ExceptionType.af,
+        ExceptionType.none
+      )
+      val exception = ExceptionType.merge(pmp_exception, mmio_mismatch_exception)
       // if pmp has exception, abort sending request, just send instr & exception to ibuffer and wait for commit
-      mmio_state := Mux(ExceptionType.hasException(pmp_exception), m_waitCommit, m_resendReq)
+      mmio_state := Mux(ExceptionType.hasException(exception), m_waitCommit, m_resendReq)
       // also save pmp response
-      mmio_resend_exception := pmp_exception
+      mmio_resend_exception := exception
     }
 
     is(m_resendReq) {
```
