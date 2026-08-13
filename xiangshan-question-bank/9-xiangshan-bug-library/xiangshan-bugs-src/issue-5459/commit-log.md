# Commit Log
- Issue: #5459
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5459
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5459
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5459
- Changed files: 3
- Additions: 22
- Deletions: 17

## Files
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`
- `src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 4485a364b09..d27db6eb6be 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -382,8 +382,9 @@ class Ftq(implicit p: Parameters) extends FtqModule
   // MMIO fetch
   // --------------------------------------------------------------------------------
   private val mmioPtr           = io.fromIfu.mmioCommitRead.mmioFtqPtr
+  private val mmioValid         = io.fromIfu.mmioCommitRead.valid
   private val lastMmioCommitted = commitPtr > mmioPtr || commitPtr === mmioPtr && commit
-  io.fromIfu.mmioCommitRead.mmioLastCommit := RegNext(lastMmioCommitted)
+  io.fromIfu.mmioCommitRead.mmioLastCommit := RegNext(lastMmioCommitted && mmioValid)
 
   // --------------------------------------------------------------------------------
   // Performance monitoring
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index 4ee2670d441..6cd97845e94 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -388,7 +388,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
     s2_prevIBufEnqPtr := wbRedirect.prevIBufEnqPtr + wbRedirect.instrCount
   }.elsewhen(uncacheRedirect.valid) {
     s2_prevIBufEnqPtr := uncacheRedirect.prevIBufEnqPtr + uncacheRedirect.instrCount
-  }.elsewhen(s2_fire) {
+  }.elsewhen(s2_fire && !s2_icacheMeta(0).isUncache) {
     s2_prevIBufEnqPtr := s2_prevIBufEnqPtr + s2_instrCount
   }
 
@@ -400,9 +400,10 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
   private val s2_alignPd         = preDecoderOut.pd
   private val s2_alignJumpOffset = preDecoderOut.jumpOffset
-
-  private val s2_reqIsUncache = s2_valid && s2_icacheMeta(0).isUncache &&
-    s2_icacheMeta(0).exception.isNone
+  // reqIsUncache is used to limit the number of fetch requests and enable special pre-decode configurations.
+  private val s2_reqIsUncache = s2_valid && s2_icacheMeta(0).isUncache
+  // useUncacheFetch controls whether the instruction fetch operation follows the uncache control logic.
+  private val s2_useUncacheFetch = s2_valid && s2_icacheMeta(0).isUncache && s2_icacheMeta(0).exception.isNone
   private val s2_alignFetchBlock = Wire(Vec(FetchPorts, new FetchBlockInfo))
   s2_alignFetchBlock                      := s2_fetchBlock
   s2_alignFetchBlock(0).predTakenIdx.bits := s2_fetchBlock(0).predTakenIdx.bits + s2_prevIBufEnqPtr.value(1, 0)
@@ -493,12 +494,15 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s3_uncacheLowerPc     = RegEnable(s2_alignCompactInfo.instrPcLower(s2_alignShiftNum), s2_fire)
   private val s3_alignBlockStartPos = RegEnable(s2_alignBlockStartPos, s2_fire)
   private val s3_uncachePc = catPC(s3_uncacheLowerPc, s3_alignFetchBlock(0).pcHigh, s3_alignFetchBlock(0).pcHighPlus1)
-  private val s3_reqIsUncache         = RegEnable(s2_reqIsUncache, s2_fire)
-  private val s3_uncacheCanGo         = uncacheUnit.io.resp.valid && !uncacheUnit.io.resp.bits.crossPage
+  private val s3_reqIsUncache    = RegEnable(s2_reqIsUncache, s2_fire)
+  private val s3_useUncacheFetch = RegEnable(s2_useUncacheFetch, s2_fire)
+  private val s3_uncacheCanGo =
+    (uncacheUnit.io.resp.valid && !uncacheUnit.io.resp.bits.crossPage) || !s3_useUncacheFetch
   private val s3_uncacheCrossPageMask = s3_valid && uncacheUnit.io.resp.valid && uncacheUnit.io.resp.bits.crossPage
-  private val s3_toIBufferValid = s3_valid && (!s3_reqIsUncache || (s3_uncacheCanGo && s3_reqIsUncache)) && !s3_flush
-  private val s3_shiftNum       = s3_prevIBufEnqPtr.value(1, 0)
-  private val s3_ignore         = s3_prevShiftSelect // possibly redundant, may remove later.
+  private val s3_toIBufferValid =
+    s3_valid && (!s3_reqIsUncache || (s3_uncacheCanGo && s3_reqIsUncache)) && !s3_flush
+  private val s3_shiftNum = s3_prevIBufEnqPtr.value(1, 0)
+  private val s3_ignore   = s3_prevShiftSelect // possibly redundant, may remove later.
 
   /* ** unache state handle ** */
   private val uncacheBusy = RegInit(false.B)
@@ -523,7 +527,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
     uncacheCrossPageCheck := false.B
   }
 
-  uncacheUnit.io.req.valid       := s3_valid && s3_reqIsUncache && !uncacheBusy
+  uncacheUnit.io.req.valid       := s3_valid && s3_useUncacheFetch && !uncacheBusy
   uncacheUnit.io.req.bits.ftqIdx := s3_alignFetchBlock(0).ftqIdx
   uncacheUnit.io.req.bits.pbmt   := s3_icacheMeta(0).itlbPbmt
   uncacheUnit.io.req.bits.isMmio := s3_icacheMeta(0).pmpMmio
@@ -684,7 +688,9 @@ class Ifu(implicit p: Parameters) extends IfuModule
       s3_alignFetchBlock(0).startVAddr + 2.U,
       s3_alignFetchBlock(0).startVAddr + 4.U
     )
-  uncacheFlushWb.valid          := s3_reqIsUncache && !backendRedirect && (s3_uncacheCanGo || uncacheCheckFault)
+  // Due to the presence of uncache requests, s3_valid && io.toIBuffer.ready is not equivalent to s3_fire.
+  uncacheFlushWb.valid :=
+    s3_valid && io.toIBuffer.ready && s3_reqIsUncache && !backendRedirect && (s3_uncacheCanGo || uncacheCheckFault)
   uncacheFlushWb.bits.ftqIdx    := s3_alignFetchBlock(0).ftqIdx
   uncacheFlushWb.bits.pc        := s3_alignFetchBlock(0).startVAddr.toUInt
   uncacheFlushWb.bits.taken     := false.B
@@ -708,7 +714,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
     io.toIBuffer.bits.isRvc(s3_shiftNum)                 := uncacheIsRvc
     io.toIBuffer.bits.instrEndOffset(s3_shiftNum).offset := Mux(prevUncacheCrossPage || uncacheIsRvc, 0.U, 1.U)
 
-    io.toIBuffer.bits.exceptionType := uncacheException || uncacheRvcException
+    io.toIBuffer.bits.exceptionType := s3_icacheMeta(0).exception || uncacheException || uncacheRvcException
     // execption can happen in next page only when cross page.
     io.toIBuffer.bits.exceptionCrossPage := prevUncacheCrossPage && uncacheException.hasException
     io.toIBuffer.bits.exceptionOffset    := 0.U
@@ -723,7 +729,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
     uncacheFlushWb.bits.attribute := brAttribute
   }
 
-  uncacheRedirect.valid          := s3_reqIsUncache && (s3_uncacheCanGo || uncacheCheckFault)
+  uncacheRedirect.valid := s3_valid && io.toIBuffer.ready && s3_reqIsUncache && (s3_uncacheCanGo || uncacheCheckFault)
   uncacheRedirect.instrCount     := Mux(uncacheCheckFault, 0.U, 1.U)
   uncacheRedirect.prevIBufEnqPtr := s3_prevIBufEnqPtr
   uncacheRedirect.isHalfInstr    := false.B
diff --git a/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala b/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
index 6bd6d902339..e84b667130a 100644
--- a/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
@@ -102,9 +102,7 @@ class IfuUncacheUnit(implicit p: Parameters) extends IfuModule with IfuHelper {
       when(isFirstInstr) {
         uncacheState := UncacheFsmState.SendReq
       }.otherwise {
-        // FIXME: MMIO blocking will be enabled once FTQ commit support is in place.
-        // uncacheState := Mux(io.mmioCommitRead.mmioLastCommit, UncacheFsmState.SendReq, UncacheFsmState.WaitLastCommit)
-        uncacheState := Mux(true.B, UncacheFsmState.SendReq, UncacheFsmState.WaitLastCommit)
+        uncacheState := Mux(io.mmioCommitRead.mmioLastCommit, UncacheFsmState.SendReq, UncacheFsmState.WaitLastCommit)
       }
     }
```
