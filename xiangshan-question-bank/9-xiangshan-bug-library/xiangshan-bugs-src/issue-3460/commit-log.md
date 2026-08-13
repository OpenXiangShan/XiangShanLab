# Commit Log
- Issue: #3460
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3460
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3460
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3460
- Changed files: 3
- Additions: 5
- Deletions: 5

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 86e6317323a..e9ce216d120 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -285,7 +285,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   val scommit = GatedRegNext(io.rob.scommit)
 
   // RegNext misalign control for better timing
-  val doMisalignSt = GatedValidRegNext((rdataPtrExt(0).value === deqPtr) && (cmtPtr === deqPtr) && allocated(deqPtr) && datavalid(deqPtr) && unaligned(deqPtr))
+  val doMisalignSt = GatedValidRegNext((rdataPtrExt(0).value === deqPtr) && (cmtPtr === deqPtr) && allocated(deqPtr) && datavalid(deqPtr) && unaligned(deqPtr) && !isVec(deqPtr))
   val finishMisalignSt = GatedValidRegNext(doMisalignSt && io.maControl.control.removeSq && !io.maControl.control.hasException)
   val misalignBlock = doMisalignSt && !finishMisalignSt
   
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 72173417784..763b8ac8862 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1355,7 +1355,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.lsq.ldin.bits.miss := s3_in.miss && !s3_fwd_frm_d_chan_valid
 
   // connect to misalignBuffer
-  io.misalign_buf.valid := io.lsq.ldin.valid && io.csrCtrl.hd_misalign_ld_enable
+  io.misalign_buf.valid := io.lsq.ldin.valid && io.csrCtrl.hd_misalign_ld_enable && !io.lsq.ldin.bits.isvec
   io.misalign_buf.bits  := s3_in
 
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
@@ -1386,7 +1386,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s3_sel_rep_cause = PriorityEncoderOH(s3_rep_info.cause.asUInt)
 
   val s3_exception = ExceptionNO.selectByFu(s3_in.uop.exceptionVec, LduCfg).asUInt.orR && s3_vecActive
-  val s3_mis_align = s3_valid && s3_in.uop.exceptionVec(loadAddrMisaligned) && io.csrCtrl.hd_misalign_ld_enable
+  val s3_mis_align = s3_valid && s3_in.uop.exceptionVec(loadAddrMisaligned) && io.csrCtrl.hd_misalign_ld_enable && !s3_in.isvec
   when (s3_exception || s3_dly_ld_err || s3_rep_frm_fetch) {
     io.lsq.ldin.bits.rep_info.cause := 0.U.asTypeOf(s3_rep_info.cause.cloneType)
   } .otherwise {
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 765ff4af74e..4abf1232d0f 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -322,7 +322,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   io.lsq.bits.miss := s1_tlb_miss
 
   // goto misalignBuffer
-  io.misalign_buf.valid := s1_valid && !s1_in.isHWPrefetch && io.csrCtrl.hd_misalign_st_enable
+  io.misalign_buf.valid := s1_valid && !s1_in.isHWPrefetch && io.csrCtrl.hd_misalign_st_enable && !s1_in.isvec
   io.misalign_buf.bits  := io.lsq.bits
 
   // kill dcache write intent request when tlb miss or exception
@@ -348,7 +348,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s2_can_go = s3_ready
   val s2_fire   = s2_valid && !s2_kill && s2_can_go
   val s2_vecActive    = RegEnable(s1_out.vecActive, true.B, s1_fire)
-  val s2_mis_align    = s2_in.uop.exceptionVec(storeAddrMisaligned) && io.csrCtrl.hd_misalign_st_enable
+  val s2_mis_align    = s2_in.uop.exceptionVec(storeAddrMisaligned) && io.csrCtrl.hd_misalign_st_enable && !s2_in.isvec
   val s2_frm_mabuf    = s2_in.isFrmMisAlignBuf
   val s2_pbmt   = RegEnable(s1_pbmt, s1_fire)
```
