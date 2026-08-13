# Commit Log
- Issue: #4275
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4275
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4275
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4275
- Changed files: 3
- Additions: 4
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/Bundles.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/Bundles.scala b/src/main/scala/xiangshan/mem/Bundles.scala
index 31ce94f031d..aa01d68426a 100644
--- a/src/main/scala/xiangshan/mem/Bundles.scala
+++ b/src/main/scala/xiangshan/mem/Bundles.scala
@@ -219,6 +219,7 @@ object Bundles {
   class LqWriteBundle(implicit p: Parameters) extends LsPipelineBundle {
     // load inst replay informations
     val rep_info = new LoadToLsqReplayIO
+    val nc_with_data = Bool() // nc access with data
     // queue entry data, except flag bits, will be updated if writeQueue is true,
     // valid bit in LqWriteBundle will be ignored
     val data_wen_dup = Vec(6, Bool()) // dirty reg dup
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
index f76317e7a14..d4fd0323e8e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala
@@ -295,7 +295,7 @@ class LoadQueue(implicit p: Parameters) extends XSModule
   for ((buff, w) <- uncacheBuffer.io.req.zipWithIndex) {
     // from load_s3
     val ldinBits = io.ldu.ldin(w).bits
-    buff.valid := io.ldu.ldin(w).valid && (ldinBits.nc || ldinBits.mmio) && !ldinBits.rep_info.need_rep
+    buff.valid := io.ldu.ldin(w).valid && (ldinBits.nc || ldinBits.mmio) && !ldinBits.rep_info.need_rep && !ldinBits.nc_with_data
     buff.bits := ldinBits
   }
 
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index f146b096e3f..f743ebad8c4 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1515,7 +1515,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // forwrad last beat
   val s3_fast_rep_canceled = io.replay.valid && io.replay.bits.forward_tlDchannel || io.misalign_ldin.valid || !io.dcache.req.ready
 
-  val s3_can_enter_lsq_valid = s3_valid && (!s3_fast_rep || s3_fast_rep_canceled) && !s3_in.feedbacked && !s3_nc_with_data && !s3_in.misalignNeedWakeUp
+  val s3_can_enter_lsq_valid = s3_valid && (!s3_fast_rep || s3_fast_rep_canceled) && !s3_in.feedbacked && !s3_in.misalignNeedWakeUp
   io.lsq.ldin.valid := s3_can_enter_lsq_valid
   // TODO: check this --by hx
   // io.lsq.ldin.valid := s3_valid && (!s3_fast_rep || !io.fast_rep_out.ready) && !s3_in.feedbacked && !s3_in.lateKill
@@ -1528,6 +1528,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.misalign_buf.bits  := s3_in
 
   /* <------- DANGEROUS: Don't change sequence here ! -------> */
+  io.lsq.ldin.bits.nc_with_data := s3_nc_with_data
   io.lsq.ldin.bits.data_wen_dup := s3_ld_valid_dup.asBools
   io.lsq.ldin.bits.replacementUpdated := io.dcache.resp.bits.replacementUpdated
   io.lsq.ldin.bits.missDbUpdated := GatedValidRegNext(s2_fire && s2_in.hasROBEntry && !s2_in.tlbMiss && !s2_in.missDbUpdated)
```
