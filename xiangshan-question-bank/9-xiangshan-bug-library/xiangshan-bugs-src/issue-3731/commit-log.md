# Commit Log
- Issue: #3731
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3731
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3731
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3731
- Changed files: 9
- Additions: 11
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index e9d90e80cd8..91c9c914cf8 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -419,7 +419,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   )
   bypassNetwork.io.fromExus.mem.flatten.zip(io.mem.writeBack).foreach { case (sink, source) =>
     sink.valid := source.valid
-    sink.bits.intWen := source.bits.uop.rfWen && FuType.isLoad(source.bits.uop.fuType)
+    sink.bits.intWen := source.bits.uop.rfWen && source.bits.isFromLoadUnit
     sink.bits.pdest := source.bits.uop.pdest
     sink.bits.data := source.bits.data
   }
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 066bcb13773..fc12998ee1d 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -928,6 +928,7 @@ object Bundles {
     val mask = if (isVector) Some(UInt(VLEN.W)) else None
     val vdIdx = if (isVector) Some(UInt(3.W)) else None // TODO: parameterize width
     val vdIdxInField = if (isVector) Some(UInt(3.W)) else None
+    val isFromLoadUnit = Bool()
     val debug = new DebugBundle
 
     def isVls = FuType.isVls(uop.fuType)
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index af9e9755943..7cfde3f556f 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -443,6 +443,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   // misalignBuffer will overwrite the source from ldu if it is about to writeback
   ldaOut.valid := atomicsUnit.io.out.valid || loadUnits.head.io.ldout.valid || loadMisalignBuffer.io.writeBack.valid
   ldaOut.bits  := ldaWritebackOverride
+  ldaOut.bits.isFromLoadUnit := !(atomicsUnit.io.out.valid || loadMisalignBuffer.io.writeBack.valid)
   atomicsUnit.io.out.ready := ldaOut.ready
   loadUnits.head.io.ldout.ready := ldaOut.ready
   loadMisalignBuffer.io.writeBack.ready := ldaOut.ready
@@ -1183,7 +1184,7 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
         stData(i).ready := true.B
     }
     lsq.io.std.storeDataIn.map(_.bits.debug := 0.U.asTypeOf(new DebugBundle))
-
+    lsq.io.std.storeDataIn.foreach(_.bits.isFromLoadUnit := DontCare)
 
 
     // store prefetch train
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index 831b29c55ec..4df81c0f9cd 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -551,6 +551,7 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
   io.writeBack.bits.uop.flushPipe := Mux(globalMMIO || globalException, false.B, true.B)
   io.writeBack.bits.uop.replayInst := false.B
   io.writeBack.bits.data := combinedData
+  io.writeBack.bits.isFromLoadUnit := DontCare
   io.writeBack.bits.debug.isMMIO := globalMMIO
   io.writeBack.bits.debug.isPerfCnt := false.B
   io.writeBack.bits.debug.paddr := req.paddr
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 62e2434c9ca..53742fbcaff 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -571,6 +571,7 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
   io.writeBack.bits.uop.flushPipe := Mux(globalMMIO || globalException, false.B, true.B)
   io.writeBack.bits.uop.replayInst := false.B
   io.writeBack.bits.data := unalignedStoreData
+  io.writeBack.bits.isFromLoadUnit := DontCare
   io.writeBack.bits.debug.isMMIO := globalMMIO
   io.writeBack.bits.debug.isPerfCnt := false.B
   io.writeBack.bits.debug.paddr := req.paddr
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index cd475832cf9..efd733ea6c2 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -890,6 +890,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   io.mmioStout.bits.uop.sqIdx := deqPtrExt(0)
   io.mmioStout.bits.uop.flushPipe := deqCanDoCbo // flush Pipeline to keep order in CMO
   io.mmioStout.bits.data := shiftDataToLow(paddrModule.io.rdata(0), dataModule.io.rdata(0).data) // dataModule.io.rdata.read(deqPtr)
+  io.mmioStout.bits.isFromLoadUnit := DontCare
   io.mmioStout.bits.debug.isMMIO := true.B
   io.mmioStout.bits.debug.paddr := DontCare
   io.mmioStout.bits.debug.isPerfCnt := false.B
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 9d64dc50bcf..277589d4af5 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1411,6 +1411,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s3_out.bits.uop.flushPipe   := false.B
   s3_out.bits.uop.replayInst  := s3_rep_frm_fetch || s3_flushPipe
   s3_out.bits.data            := s3_in.data
+  s3_out.bits.isFromLoadUnit  := true.B
   s3_out.bits.debug.isMMIO    := s3_in.mmio
   s3_out.bits.debug.isPerfCnt := false.B
   s3_out.bits.debug.paddr     := s3_in.paddr
@@ -1565,6 +1566,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.ldout.valid       := (s3_mmio.valid ||
                           (s3_out.valid && !s3_vecout.isvec && !s3_mis_align && !s3_frm_mabuf))
   io.ldout.bits.uop.exceptionVec := ExceptionNO.selectByFu(s3_ld_wb_meta.uop.exceptionVec, LduCfg)
+  io.ldout.bits.isFromLoadUnit := true.B
 
   // TODO: check this --hx
   // io.ldout.valid       := s3_out.valid && !s3_out.bits.uop.robIdx.needFlush(io.redirect) && !s3_vecout.isvec ||
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index 24cd5509e7c..f95472dd6b8 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -461,6 +461,7 @@ class VSMergeBufferImp(implicit p: Parameters) extends BaseVMergeBuffer(isVStore
     sink.debug            := 0.U.asTypeOf(new DebugBundle)
     sink.vdIdxInField.get := DontCare
     sink.vdIdx.get        := DontCare
+    sink.isFromLoadUnit   := DontCare
     sink.uop.vpu.vstart   := source.vstart
     sink
   }
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index acc611af1d4..5722fe2c23b 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -456,6 +456,7 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
   vstd.bits.debug := DontCare
   vstd.bits.vdIdx.get := DontCare
   vstd.bits.vdIdxInField.get := DontCare
+  vstd.bits.isFromLoadUnit   := DontCare
   vstd.bits.mask.get := Mux(!issuePreIsSplit, usSplitMask, mask)
 
 }
```
