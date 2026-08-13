# Commit Log
- Issue: #4800
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4800
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4800
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4800
- Changed files: 3
- Additions: 12
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VecBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index fa2c06563eb..cdc81df3a1c 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -2008,6 +2008,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
 
   // vector segmentUnit
   vSegmentUnit.io.in.bits <> io.ooo_to_mem.issueVldu.head.bits
+  vSegmentUnit.io.csrCtrl <> csrCtrl
   vSegmentUnit.io.in.valid := isSegment && io.ooo_to_mem.issueVldu.head.valid// is segment instruction
   vSegmentUnit.io.dtlb.resp.bits <> dtlb_reqs.take(LduCnt).head.resp.bits
   vSegmentUnit.io.dtlb.resp.valid <> dtlb_reqs.take(LduCnt).head.resp.valid
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 6eac9441c56..9d1cccd494f 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -311,8 +311,11 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
       when(io.rdcache.resp.bits.miss || io.rdcache.s2_bank_conflict) {
         stateNext := s_cache_req
       }.otherwise {
-
-        stateNext := Mux(isVSegLoad, Mux(isMisalignReg && !notCross16ByteReg, s_misalign_merge_data, s_latch_and_merge_data), s_send_data)
+        when(io.rdcache.resp.bits.error_delayed && GatedValidRegNext(io.csrCtrl.cache_error_enable)) {
+          stateNext := s_finish
+        }.otherwise {
+          stateNext := Mux(isVSegLoad, Mux(isMisalignReg && !notCross16ByteReg, s_misalign_merge_data, s_latch_and_merge_data), s_send_data)
+        }
       }
     }.otherwise{
       stateNext := s_cache_resp
@@ -585,6 +588,11 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   }
 
 
+  when(state === s_cache_resp && io.rdcache.resp.fire && !io.rdcache.resp.bits.miss && !io.rdcache.s2_bank_conflict) {
+    exceptionVec(hardwareError) := io.rdcache.resp.bits.error_delayed && GatedValidRegNext(io.csrCtrl.cache_error_enable)
+    exception_pa := exceptionVec(hardwareError)
+    instMicroOp.exception_pa := exception_pa
+  }
 
   /**
    * merge data for load
diff --git a/src/main/scala/xiangshan/mem/vector/VecBundle.scala b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
index 6115b07be8e..abfc4297e12 100644
--- a/src/main/scala/xiangshan/mem/vector/VecBundle.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
@@ -265,6 +265,7 @@ class VMergeBufferIO(isVStore : Boolean=false)(implicit p: Parameters) extends V
 class VSegmentUnitIO(implicit p: Parameters) extends VLSUBundle{
   val in                  = Flipped(Decoupled(new MemExuInput(isVector = true))) // from iq
   val uopwriteback        = DecoupledIO(new MemExuOutput(isVector = true)) // writeback data
+  val csrCtrl             = Flipped(new CustomCSRCtrlIO)
   val rdcache             = new DCacheLoadIO // read dcache port
   val sbuffer             = Decoupled(new DCacheWordReqWithVaddrAndPfFlag)
   val vecDifftestInfo     = Decoupled(new ToSbufferDifftestInfoBundle) // to sbuffer
```
