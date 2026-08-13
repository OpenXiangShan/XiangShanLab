# Commit Log
- Issue: #5985
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5985
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5985
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5985
- Changed files: 1
- Additions: 34
- Deletions: 42

## Files
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index bb2b00b0368..e86569b2180 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -219,7 +219,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   }.elsewhen(wbRedirect.valid) {
     s1_prevLastIsHalfRvi := wbRedirect.isHalfInstr
   }.elsewhen(uncacheRedirect.valid) {
-    s1_prevLastIsHalfRvi := false.B
+    s1_prevLastIsHalfRvi := uncacheRedirect.isHalfInstr
   }.elsewhen(s1_fire && !s1_icacheMeta(0).isUncache) {
     s1_prevLastIsHalfRvi := s1_fetchEndIsHalf
   }
@@ -391,8 +391,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
     s2_prevLastHalfData := wbRedirect.halfData
     s2_prevLastHalfPc   := wbRedirect.halfPc
   }.elsewhen(uncacheRedirect.valid) {
-    s2_prevLastHalfData := 0.U
-    s2_prevLastHalfPc   := 0.U.asTypeOf(PrunedAddr(VAddrBits))
+    s2_prevLastHalfData := uncacheRedirect.halfData
+    s2_prevLastHalfPc   := uncacheRedirect.halfPc
   }.elsewhen(s2_fire) {
     s2_prevLastHalfData := s2_alignInstrData(s2_instrCount + s2_alignShiftNum)(15, 0)
     s2_prevLastHalfPc   := s2_alignPc(s2_instrCount + s2_alignShiftNum)
@@ -506,12 +506,12 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
   private val s3_alignFoldPc        = RegEnable(s2_alignFoldPc, s2_fire)
   private val s3_rawInstrEndVec     = RegEnable(s2_rawInstrEndVec, s2_fire)
-  private val s3_prevLastIsHalfRvi  = RegEnable(s2_prevLastIsHalfRvi, s2_fire)
-  private val s3_uncacheLowerPc     = RegEnable(s2_alignCompactInfo.instrPcLower(s2_alignShiftNum), s2_fire)
+  private val s3_prevLastIsHalfRvi  = RegEnable(s2_prevLastIsHalfRvi, false.B, s2_fire)
+  private val s3_prevLastHalfData   = RegEnable(s2_prevLastHalfData, s2_fire)
+  private val s3_prevLastHalfPc     = RegEnable(s2_prevLastHalfPc, s2_fire)
   private val s3_alignBlockStartPos = RegEnable(s2_alignBlockStartPos, s2_fire)
-  private val s3_uncachePc = catPC(s3_uncacheLowerPc, s3_alignFetchBlock(0).pcHigh, s3_alignFetchBlock(0).pcHighPlus1)
-  private val s3_reqIsUncache    = RegEnable(s2_reqIsUncache, s2_fire)
-  private val s3_useUncacheFetch = RegEnable(s2_useUncacheFetch, s2_fire)
+  private val s3_reqIsUncache       = RegEnable(s2_reqIsUncache, false.B, s2_fire)
+  private val s3_useUncacheFetch    = RegEnable(s2_useUncacheFetch, s2_fire)
   private val s3_uncacheCanGo =
     (uncacheUnit.io.resp.valid && !uncacheUnit.io.resp.bits.needResend) || !s3_useUncacheFetch
   private val s3_uncacheCrossPageMask = s3_valid && uncacheUnit.io.resp.valid && uncacheUnit.io.resp.bits.needResend
@@ -522,25 +522,17 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
   /* ** unache state handle ** */
   private val uncacheBusy = RegInit(false.B)
-  // Uncache cross-page across two fetch blocks, store the prev block’s cross-page flag and data.
-  private val prevUncacheNeedResend = RegInit(false.B)
-  private val prevUncacheData       = RegInit(0.U(16.W))
   // For uncache cross-page instr, the real PC is in the prev fetch block.
   private val uncachePc = RegInit(0.U.asTypeOf(PrunedAddr(VAddrBits)))
-  // Uncache cross-page may hit seq fetch or mispred, check required.
-  private val uncacheResendCheck = RegInit(false.B)
   when(s3_flush) {
-    uncacheBusy        := false.B
-    uncachePc          := 0.U.asTypeOf(PrunedAddr(VAddrBits))
-    uncacheResendCheck := false.B
+    uncacheBusy := false.B
+    uncachePc   := 0.U.asTypeOf(PrunedAddr(VAddrBits))
   }.elsewhen(uncacheUnit.io.req.fire) {
-    uncacheBusy        := true.B
-    uncachePc          := Mux(prevUncacheNeedResend, uncachePc, s3_alignPc(s3_shiftNum))
-    uncacheResendCheck := (s3_alignFetchBlock(0).startVAddr + 2.U) === s3_alignFetchBlock(0).target
+    uncacheBusy := true.B
+    uncachePc   := Mux(s3_prevLastIsHalfRvi, s3_prevLastHalfPc, s3_alignPc(s3_shiftNum))
   }.elsewhen(uncacheUnit.io.resp.valid) {
     uncacheBusy := false.B
     // uncachePc := uncachePc
-    uncacheResendCheck := false.B
   }
 
   uncacheUnit.io.req.valid       := s3_valid && s3_useUncacheFetch && !uncacheBusy
@@ -554,17 +546,12 @@ class Ifu(implicit p: Parameters) extends IfuModule
   io.toUncache <> uncacheUnit.io.toUncache
   uncacheUnit.io.fromUncache <> io.fromUncache
 
-  private val uncacheData       = uncacheUnit.io.resp.bits.uncacheData
-  private val uncacheException  = uncacheUnit.io.resp.bits.exception
-  private val uncacheNeedResend = uncacheUnit.io.resp.bits.needResend // not RVC, no exception, crossing page boundary
-  private val uncacheCheckFault = uncacheNeedResend && !uncacheResendCheck && uncacheUnit.io.resp.valid
-
-  when(uncacheUnit.io.resp.valid) {
-    prevUncacheNeedResend := uncacheNeedResend
-    prevUncacheData       := uncacheData
-  }
+  private val uncacheData      = uncacheUnit.io.resp.bits.uncacheData
+  private val uncacheException = uncacheUnit.io.resp.bits.exception
+  // not RVC, no exception, crossing page boundary
+  private val uncacheNeedResend = uncacheUnit.io.resp.bits.needResend && uncacheUnit.io.resp.valid
 
-  private val s3_uncacheData = Mux(prevUncacheNeedResend, Cat(uncacheData(15, 0), prevUncacheData), uncacheData)
+  private val s3_uncacheData = Mux(s3_prevLastIsHalfRvi, Cat(uncacheData(15, 0), s3_prevLastHalfData), uncacheData)
   private val uncacheIsRvc   = s3_uncacheData(1, 0) =/= "b11".U
   uncacheRvcExpander.io.in      := Mux(s3_reqIsUncache, s3_uncacheData, 0.U)
   uncacheRvcExpander.io.fsIsOff := io.csrFsIsOff
@@ -658,7 +645,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
   // otherwise, we only have rvcException, so select its offset
   io.toIBuffer.bits.exceptionOffset := Mux(
     s3_icacheMeta(0).exception.hasException,
-    0.U,
+    s3_shiftNum.pad(log2Ceil(IBufferEnqueueWidth)),
     s3_rvcExceptionOffset
   )
 
@@ -693,20 +680,20 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val uncachePd           = 0.U.asTypeOf(Vec(FetchBlockInstNum, new PreDecodeInfo))
   private val uncacheMisEndOffset = Wire(Valid(UInt(FetchBlockInstOffsetWidth.W)))
   uncacheMisEndOffset.valid := s3_reqIsUncache
-  uncacheMisEndOffset.bits  := Mux(prevUncacheNeedResend || uncacheIsRvc || uncacheCheckFault, 0.U, 1.U)
+  uncacheMisEndOffset.bits  := Mux(uncacheIsRvc || uncacheNeedResend, 0.U, 1.U)
 
   // Send mmioFlushWb back to FTQ 1 cycle after uncache fetch return
   // When backend redirect, mmioState reset after 1 cycle.
   // In this case, mask .valid to avoid overriding backend redirect
   private val uncacheTarget =
     Mux(
-      uncacheIsRvc || prevUncacheNeedResend || uncacheCheckFault,
+      uncacheIsRvc || uncacheNeedResend,
       s3_alignFetchBlock(0).startVAddr + 2.U,
       s3_alignFetchBlock(0).startVAddr + 4.U
     )
   // Due to the presence of uncache requests, s3_valid && io.toIBuffer.ready is not equivalent to s3_fire.
   uncacheFlushWb.valid :=
-    s3_valid && io.toIBuffer.ready && s3_reqIsUncache && !backendRedirect && (s3_uncacheCanGo || uncacheCheckFault)
+    s3_valid && io.toIBuffer.ready && s3_reqIsUncache && !backendRedirect && (s3_uncacheCanGo || uncacheNeedResend)
   uncacheFlushWb.bits.ftqIdx    := s3_alignFetchBlock(0).ftqIdx
   uncacheFlushWb.bits.pc        := s3_alignFetchBlock(0).startVAddr.toUInt
   uncacheFlushWb.bits.taken     := false.B
@@ -728,12 +715,13 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
     io.toIBuffer.bits.pc(s3_shiftNum)                    := uncachePc
     io.toIBuffer.bits.isRvc(s3_shiftNum)                 := uncacheIsRvc
-    io.toIBuffer.bits.instrEndOffset(s3_shiftNum).offset := Mux(prevUncacheNeedResend || uncacheIsRvc, 0.U, 1.U)
+    io.toIBuffer.bits.instrEndOffset(s3_shiftNum).offset := Mux(uncacheIsRvc, 0.U, 1.U)
 
     io.toIBuffer.bits.exceptionType := s3_icacheMeta(0).exception || uncacheException || uncacheRvcException
     // execption can happen in next page only when cross page.
-    io.toIBuffer.bits.exceptionCrossPage := prevUncacheNeedResend && uncacheException.hasException
-    io.toIBuffer.bits.exceptionOffset    := 0.U
+    io.toIBuffer.bits.exceptionCrossPage :=
+      s3_prevLastIsHalfRvi && (s3_icacheMeta(0).exception.hasException || uncacheException.hasException)
+    io.toIBuffer.bits.exceptionOffset := s3_shiftNum.pad(log2Ceil(IBufferEnqueueWidth))
 
     // The s3_alignBlockStartPos vector marks the position of the first instruction.
     // In uncache scenarios, only a single instruction is allowed for execution,
@@ -745,12 +733,16 @@ class Ifu(implicit p: Parameters) extends IfuModule
     uncacheFlushWb.bits.attribute := brAttribute
   }
 
-  uncacheRedirect.valid := s3_valid && io.toIBuffer.ready && s3_reqIsUncache && (s3_uncacheCanGo || uncacheCheckFault)
-  uncacheRedirect.instrCount     := Mux(uncacheCheckFault, 0.U, 1.U)
+  // Core change: Route cross-page uncache data to S1 for unified management.
+  // S3 can now directly concatenate uncache instructions using s3_prevLastIsHalfRvi during fetch.
+  // This fixes the edge case where instructions spanning both cache and uncache channels fell through
+  // the cracks of the existing S1 (cache) and S3 (uncache) cross-page handling logic.
+  uncacheRedirect.valid := s3_valid && io.toIBuffer.ready && s3_reqIsUncache && (s3_uncacheCanGo || uncacheNeedResend)
+  uncacheRedirect.instrCount     := Mux(uncacheNeedResend, 0.U, 1.U)
   uncacheRedirect.prevIBufEnqPtr := s3_prevIBufEnqPtr
-  uncacheRedirect.isHalfInstr    := false.B
-  uncacheRedirect.halfPc         := 0.U.asTypeOf(PrunedAddr(VAddrBits))
-  uncacheRedirect.halfData       := 0.U
+  uncacheRedirect.isHalfInstr    := uncacheNeedResend
+  uncacheRedirect.halfPc         := uncachePc
+  uncacheRedirect.halfData       := uncacheData(15, 0)
   /* *****************************************************************************
    * IFU Write-back Stage
    * - write back preDecode information to Ftq to update
```
