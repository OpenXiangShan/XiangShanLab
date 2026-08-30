# Commit Log
- Issue: #4792
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4792
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4792
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4792
- Changed files: 3
- Additions: 4
- Deletions: 6

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 82d99d3a4a5..a7c43e4a2df 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1314,7 +1314,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // Here a judgement is made as to whether a misaligned exception needs to actually be generated.
   // We will generate misaligned exceptions at mmio.
   val s2_real_exceptionVec = WireInit(s2_exception_vec)
-  s2_real_exceptionVec(loadAddrMisaligned) := (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_uncache
+  s2_real_exceptionVec(loadAddrMisaligned) := (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_uncache && !s2_isvec
   s2_real_exceptionVec(loadAccessFault) := s2_exception_vec(loadAccessFault) ||
     s2_fwd_frm_d_chan && s2_d_corrupt ||
     s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_corrupt
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index dedc2bac2e0..8665d503380 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -477,7 +477,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
                                                 s2_pmp.ld && s2_isCbo_noZero || // cmo need read permission but produce store exception
                                                 ((s2_in.isvec || s2_isCbo) && s2_actually_uncache && RegNext(s1_feedback.bits.hit))
                                                 ) && s2_vecActive
-  s2_out.uop.exceptionVec(storeAddrMisaligned) := s2_actually_uncache && (s2_in.isMisalign || s2_in.isFrmMisAlignBuf) && !s2_un_misalign_exception
+  s2_out.uop.exceptionVec(storeAddrMisaligned) := s2_actually_uncache && !s2_in.isvec && (s2_in.isMisalign || s2_in.isFrmMisAlignBuf) && !s2_un_misalign_exception
   s2_out.uop.vpu.vstart     := s2_in.vecVaddrOffset >> s2_in.uop.vpu.veew
 
   // kill dcache write intent request when mmio or exception
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 6eac9441c56..91a55013e24 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -479,8 +479,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
       exceptionVec(loadPageFault)       := io.dtlb.resp.bits.excp(0).pf.ld
       exceptionVec(storeGuestPageFault) := io.dtlb.resp.bits.excp(0).gpf.st
       exceptionVec(loadGuestPageFault)  := io.dtlb.resp.bits.excp(0).gpf.ld
-      exceptionVec(storeAccessFault)    := io.dtlb.resp.bits.excp(0).af.st
-      exceptionVec(loadAccessFault)     := io.dtlb.resp.bits.excp(0).af.ld
+      exceptionVec(storeAccessFault)    := io.dtlb.resp.bits.excp(0).af.st || Pbmt.isUncache(io.dtlb.resp.bits.pbmt.head)
+      exceptionVec(loadAccessFault)     := io.dtlb.resp.bits.excp(0).af.ld || Pbmt.isUncache(io.dtlb.resp.bits.pbmt.head)
       when(!io.dtlb.resp.bits.miss){
         instMicroOp.paddr             := io.dtlb.resp.bits.paddr(0)
         instMicroOp.exceptionVaddr    := io.dtlb.resp.bits.fullva
@@ -515,8 +515,6 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
     notCross16ByteWire   := highAddress(4) === vaddr(4)
     isMisalignWire       := !addr_aligned && !isMisalignReg
     canHandleMisalign    := !pmp.mmio && !triggerBreakpoint && !triggerDebugMode
-    exceptionVec(loadAddrMisaligned)  := isMisalignWire && isVSegLoad  && canTriggerException && pmp.mmio
-    exceptionVec(storeAddrMisaligned) := isMisalignWire && isVSegStore && canTriggerException && pmp.mmio
 
     exception_va  := exceptionVec(storePageFault) || exceptionVec(loadPageFault) ||
                      exceptionVec(storeAccessFault) || exceptionVec(loadAccessFault) ||
```
