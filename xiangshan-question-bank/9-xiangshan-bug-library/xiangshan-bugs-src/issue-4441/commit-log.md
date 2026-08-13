# Commit Log
- Issue: #4441
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4441
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4441
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4441
- Changed files: 1
- Additions: 11
- Deletions: 4

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index aca0de52ce1..0e6c29f518b 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -445,8 +445,15 @@ class StoreUnit(implicit p: Parameters) extends XSModule
 
   val s2_mmio = (s2_in.mmio || (Pbmt.isPMA(s2_pbmt) && s2_pmp.mmio)) && RegNext(s1_feedback.bits.hit)
   val s2_memBackTypeMM = !s2_pmp.mmio
-  val s2_actually_uncache = (Pbmt.isPMA(s2_pbmt) && s2_pmp.mmio || s2_in.nc || s2_in.mmio) && RegNext(s1_feedback.bits.hit)
-  val s2_uncache = !s2_exception && !s2_in.tlbMiss && s2_actually_uncache
+  // The response signal of `pmp/pma` is credible only after the physical address is actually generated.
+  // Therefore, the response signals of pmp/pma generated after an address translation has produced an `access fault` or a `page fault` are completely unreliable.
+  val s2_un_access_exception =  s2_vecActive && (
+    s2_in.uop.exceptionVec(storeAccessFault) ||
+    s2_in.uop.exceptionVec(storePageFault)   ||
+    s2_in.uop.exceptionVec(storeGuestPageFault)
+  )
+  // This real physical address is located in uncache space.
+  val s2_actually_uncache = !s2_in.tlbMiss && !s2_un_access_exception && (Pbmt.isPMA(s2_pbmt) && s2_pmp.mmio || s2_in.nc || s2_in.mmio) && RegNext(s1_feedback.bits.hit)
   val s2_isCbo  = RegEnable(s1_isCbo, s1_fire) // all cbo instr
   val s2_isCbo_noZero = LSUOpType.isCbo(s2_in.uop.fuOpType)
 
@@ -461,11 +468,11 @@ class StoreUnit(implicit p: Parameters) extends XSModule
                                                 s2_pmp.st ||
                                                 ((s2_in.isvec || s2_frm_mabuf || s2_isCbo) && s2_actually_uncache && RegNext(s1_feedback.bits.hit))
                                                 ) && s2_vecActive
-  s2_out.uop.exceptionVec(storeAddrMisaligned) := s2_mmio && s2_in.isMisalign && !s2_un_misalign_exception
+  s2_out.uop.exceptionVec(storeAddrMisaligned) := s2_actually_uncache && s2_in.isMisalign && !s2_un_misalign_exception
   s2_out.uop.vpu.vstart     := s2_in.vecVaddrOffset >> s2_in.uop.vpu.veew
 
   // kill dcache write intent request when mmio or exception
-  io.dcache.s2_kill := (s2_uncache || s2_exception || s2_in.uop.robIdx.needFlush(io.redirect))
+  io.dcache.s2_kill := (s2_actually_uncache || s2_exception || s2_in.uop.robIdx.needFlush(io.redirect))
   io.dcache.s2_pc   := s2_out.uop.pc
   // TODO: dcache resp
   io.dcache.resp.ready := true.B
```
