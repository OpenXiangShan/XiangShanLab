# Commit Log
- Issue: #5959
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5959
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5959
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5959
- Changed files: 4
- Additions: 52
- Deletions: 52

## Files
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`
- `src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala`
- `src/main/scala/xiangshan/frontend/instruncache/Bundles.scala`
- `src/main/scala/xiangshan/frontend/instruncache/InstrUncacheEntry.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index e99df64a441..736f4b8042d 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -511,8 +511,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val s3_reqIsUncache    = RegEnable(s2_reqIsUncache, s2_fire)
   private val s3_useUncacheFetch = RegEnable(s2_useUncacheFetch, s2_fire)
   private val s3_uncacheCanGo =
-    (uncacheUnit.io.resp.valid && !uncacheUnit.io.resp.bits.crossPage) || !s3_useUncacheFetch
-  private val s3_uncacheCrossPageMask = s3_valid && uncacheUnit.io.resp.valid && uncacheUnit.io.resp.bits.crossPage
+    (uncacheUnit.io.resp.valid && !uncacheUnit.io.resp.bits.needResend) || !s3_useUncacheFetch
+  private val s3_uncacheCrossPageMask = s3_valid && uncacheUnit.io.resp.valid && uncacheUnit.io.resp.bits.needResend
   private val s3_toIBufferValid =
     s3_valid && (!s3_reqIsUncache || (s3_uncacheCanGo && s3_reqIsUncache)) && !s3_flush
   private val s3_shiftNum = s3_prevIBufEnqPtr.value(1, 0)
@@ -521,24 +521,24 @@ class Ifu(implicit p: Parameters) extends IfuModule
   /* ** unache state handle ** */
   private val uncacheBusy = RegInit(false.B)
   // Uncache cross-page across two fetch blocks, store the prev block’s cross-page flag and data.
-  private val prevUncacheCrossPage = RegInit(false.B)
-  private val prevUncacheData      = RegInit(0.U(16.W))
+  private val prevUncacheNeedResend = RegInit(false.B)
+  private val prevUncacheData       = RegInit(0.U(16.W))
   // For uncache cross-page instr, the real PC is in the prev fetch block.
   private val uncachePc = RegInit(0.U.asTypeOf(PrunedAddr(VAddrBits)))
   // Uncache cross-page may hit seq fetch or mispred, check required.
-  private val uncacheCrossPageCheck = RegInit(false.B)
+  private val uncacheResendCheck = RegInit(false.B)
   when(s3_flush) {
-    uncacheBusy           := false.B
-    uncachePc             := 0.U.asTypeOf(PrunedAddr(VAddrBits))
-    uncacheCrossPageCheck := false.B
+    uncacheBusy        := false.B
+    uncachePc          := 0.U.asTypeOf(PrunedAddr(VAddrBits))
+    uncacheResendCheck := false.B
   }.elsewhen(uncacheUnit.io.req.fire) {
-    uncacheBusy           := true.B
-    uncachePc             := Mux(prevUncacheCrossPage, uncachePc, s3_alignPc(s3_shiftNum))
-    uncacheCrossPageCheck := (s3_alignFetchBlock(0).startVAddr + 2.U) === s3_alignFetchBlock(0).target
+    uncacheBusy        := true.B
+    uncachePc          := Mux(prevUncacheNeedResend, uncachePc, s3_alignPc(s3_shiftNum))
+    uncacheResendCheck := (s3_alignFetchBlock(0).startVAddr + 2.U) === s3_alignFetchBlock(0).target
   }.elsewhen(uncacheUnit.io.resp.valid) {
     uncacheBusy := false.B
     // uncachePc := uncachePc
-    uncacheCrossPageCheck := false.B
+    uncacheResendCheck := false.B
   }
 
   uncacheUnit.io.req.valid       := s3_valid && s3_useUncacheFetch && !uncacheBusy
@@ -555,15 +555,15 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
   private val uncacheData       = uncacheUnit.io.resp.bits.uncacheData
   private val uncacheException  = uncacheUnit.io.resp.bits.exception
-  private val uncacheCrossPage  = uncacheUnit.io.resp.bits.crossPage
-  private val uncacheCheckFault = uncacheCrossPage && !uncacheCrossPageCheck && uncacheUnit.io.resp.valid
+  private val uncacheNeedResend = uncacheUnit.io.resp.bits.needResend // not RVC, no exception, crossing page boundary
+  private val uncacheCheckFault = uncacheNeedResend && !uncacheResendCheck && uncacheUnit.io.resp.valid
 
   when(uncacheUnit.io.resp.valid) {
-    prevUncacheCrossPage := uncacheCrossPage
-    prevUncacheData      := uncacheData
+    prevUncacheNeedResend := uncacheNeedResend
+    prevUncacheData       := uncacheData
   }
 
-  private val s3_uncacheData = Mux(prevUncacheCrossPage, Cat(uncacheData(15, 0), prevUncacheData), uncacheData)
+  private val s3_uncacheData = Mux(prevUncacheNeedResend, Cat(uncacheData(15, 0), prevUncacheData), uncacheData)
   private val uncacheIsRvc   = s3_uncacheData(1, 0) =/= "b11".U
   uncacheRvcExpander.io.in      := Mux(s3_reqIsUncache, s3_uncacheData, 0.U)
   uncacheRvcExpander.io.fsIsOff := io.csrFsIsOff
@@ -692,14 +692,14 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val uncachePd           = 0.U.asTypeOf(Vec(FetchBlockInstNum, new PreDecodeInfo))
   private val uncacheMisEndOffset = Wire(Valid(UInt(FetchBlockInstOffsetWidth.W)))
   uncacheMisEndOffset.valid := s3_reqIsUncache
-  uncacheMisEndOffset.bits  := Mux(prevUncacheCrossPage || uncacheIsRvc || uncacheCheckFault, 0.U, 1.U)
+  uncacheMisEndOffset.bits  := Mux(prevUncacheNeedResend || uncacheIsRvc || uncacheCheckFault, 0.U, 1.U)
 
   // Send mmioFlushWb back to FTQ 1 cycle after uncache fetch return
   // When backend redirect, mmioState reset after 1 cycle.
   // In this case, mask .valid to avoid overriding backend redirect
   private val uncacheTarget =
     Mux(
-      uncacheIsRvc || prevUncacheCrossPage || uncacheCheckFault,
+      uncacheIsRvc || prevUncacheNeedResend || uncacheCheckFault,
       s3_alignFetchBlock(0).startVAddr + 2.U,
       s3_alignFetchBlock(0).startVAddr + 4.U
     )
@@ -727,11 +727,11 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
     io.toIBuffer.bits.pc(s3_shiftNum)                    := uncachePc
     io.toIBuffer.bits.isRvc(s3_shiftNum)                 := uncacheIsRvc
-    io.toIBuffer.bits.instrEndOffset(s3_shiftNum).offset := Mux(prevUncacheCrossPage || uncacheIsRvc, 0.U, 1.U)
+    io.toIBuffer.bits.instrEndOffset(s3_shiftNum).offset := Mux(prevUncacheNeedResend || uncacheIsRvc, 0.U, 1.U)
 
     io.toIBuffer.bits.exceptionType := s3_icacheMeta(0).exception || uncacheException || uncacheRvcException
     // execption can happen in next page only when cross page.
-    io.toIBuffer.bits.exceptionCrossPage := prevUncacheCrossPage && uncacheException.hasException
+    io.toIBuffer.bits.exceptionCrossPage := prevUncacheNeedResend && uncacheException.hasException
     io.toIBuffer.bits.exceptionOffset    := 0.U
 
     // The s3_alignBlockStartPos vector marks the position of the first instruction.
diff --git a/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala b/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
index 27a1961c0a5..ddef37eaf74 100644
--- a/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
@@ -38,7 +38,7 @@ class IfuUncacheUnit(implicit p: Parameters) extends IfuModule with IfuHelper {
     class IfuUncacheResp(implicit p: Parameters) extends IfuBundle {
       val uncacheData: UInt          = UInt(32.W)
       val exception:   ExceptionType = new ExceptionType
-      val crossPage:   Bool          = Bool()
+      val needResend:  Bool          = Bool() // not RVC, no exception, crossing page boundary, see InstrUncacheResp
     }
     val req            = Flipped(DecoupledIO(new IfuUncacheReq))
     val resp           = Output(ValidIO(new IfuUncacheResp))
@@ -68,21 +68,21 @@ class IfuUncacheUnit(implicit p: Parameters) extends IfuModule with IfuHelper {
   def uncacheReady: Bool = uncacheState === UncacheFsmState.Idle
   def uncacheValid: Bool = uncacheState =/= UncacheFsmState.Idle
 
-  private val uncacheData      = RegInit(0.U(32.W))
-  private val uncacheException = RegInit(ExceptionType.None)
-  private val uncacheCrossPage = RegInit(false.B)
-  private val uncacheFinish    = RegInit(false.B)
-  private val uncachePAddr     = RegInit(PrunedAddrInit(0.U(PAddrBits.W)))
-  private val isMmio           = RegInit(false.B)
-  private val itlbPbmt         = RegInit(0.U(Pbmt.width.W))
+  private val uncacheData       = RegInit(0.U(32.W))
+  private val uncacheException  = RegInit(ExceptionType.None)
+  private val uncacheNeedResend = RegInit(false.B)
+  private val uncacheFinish     = RegInit(false.B)
+  private val uncachePAddr      = RegInit(PrunedAddrInit(0.U(PAddrBits.W)))
+  private val isMmio            = RegInit(false.B)
+  private val itlbPbmt          = RegInit(0.U(Pbmt.width.W))
 
   private def uncacheReset(): Unit = {
-    uncacheState     := UncacheFsmState.Idle
-    uncacheData      := 0.U
-    uncacheException := ExceptionType.None
-    uncacheCrossPage := false.B
-    uncachePAddr     := PrunedAddrInit(0.U(PAddrBits.W))
-    uncacheFinish    := false.B
+    uncacheState      := UncacheFsmState.Idle
+    uncacheData       := 0.U
+    uncacheException  := ExceptionType.None
+    uncacheNeedResend := false.B
+    uncachePAddr      := PrunedAddrInit(0.U(PAddrBits.W))
+    uncacheFinish     := false.B
   }
 
   // last instruction finish
@@ -113,13 +113,10 @@ class IfuUncacheUnit(implicit p: Parameters) extends IfuModule with IfuHelper {
     is(UncacheFsmState.WaitResp) {
       when(fromUncache.fire) {
         val exception = ExceptionType.fromTileLink(fromUncache.bits.corrupt, fromUncache.bits.denied)
-        val crossPage = fromUncache.bits.incomplete
-        uncacheState     := UncacheFsmState.Idle
-        uncacheException := exception
-        // Cross-page exception: only when triggered at the exact page crossing;
-        // exceptions before crossing are normal exceptions
-        uncacheCrossPage := crossPage && !exception.hasException
-        uncacheData      := fromUncache.bits.data
+        uncacheState      := UncacheFsmState.Idle
+        uncacheException  := exception
+        uncacheNeedResend := fromUncache.bits.needResend
+        uncacheData       := fromUncache.bits.data
       }
     }
   }
@@ -143,7 +140,7 @@ class IfuUncacheUnit(implicit p: Parameters) extends IfuModule with IfuHelper {
   io.resp.valid            := uncacheFinish
   io.resp.bits.exception   := uncacheException
   io.resp.bits.uncacheData := uncacheData
-  io.resp.bits.crossPage   := uncacheCrossPage
+  io.resp.bits.needResend  := uncacheNeedResend
 
   // When a single MMIO instruction spans pages,
   // should the second send for confirming the oldest instruction be blocked?
diff --git a/src/main/scala/xiangshan/frontend/instruncache/Bundles.scala b/src/main/scala/xiangshan/frontend/instruncache/Bundles.scala
index a1d17341ae7..c82fc5a9246 100644
--- a/src/main/scala/xiangshan/frontend/instruncache/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/instruncache/Bundles.scala
@@ -30,5 +30,5 @@ class InstrUncacheResp(implicit p: Parameters) extends InstrUncacheBundle {
   val data:       UInt = UInt(32.W)
   val corrupt:    Bool = Bool()
   val denied:     Bool = Bool()
-  val incomplete: Bool = Bool() // whether this.data is incomplete (e.g. crossing a page boundary)
+  val needResend: Bool = Bool() // not RVC, no exception, crossing page boundary
 }
diff --git a/src/main/scala/xiangshan/frontend/instruncache/InstrUncacheEntry.scala b/src/main/scala/xiangshan/frontend/instruncache/InstrUncacheEntry.scala
index a27ace2ac1e..25feb806376 100644
--- a/src/main/scala/xiangshan/frontend/instruncache/InstrUncacheEntry.scala
+++ b/src/main/scala/xiangshan/frontend/instruncache/InstrUncacheEntry.scala
@@ -97,11 +97,13 @@ class InstrUncacheEntry(edge: TLEdgeOut)(implicit p: Parameters) extends InstrUn
   private val respDeniedReg  = RegInit(false.B)
 
   // send response to InstrUncache
-  io.resp.valid           := state === State.SendResp && !needFlush
-  io.resp.bits.data       := respDataReg.asUInt
-  io.resp.bits.corrupt    := respCorruptReg
-  io.resp.bits.denied     := respDeniedReg
-  io.resp.bits.incomplete := crossPageBoundary
+  io.resp.valid        := state === State.SendResp && !needFlush
+  io.resp.bits.data    := respDataReg.asUInt
+  io.resp.bits.corrupt := respCorruptReg
+  io.resp.bits.denied  := respDeniedReg
+  // if crossing page boundary, we need Ifu sending a frontend redirect to Ftq to re-check itlb and pmp
+  // NOTE: tile-link asked that if denied=true, corrupt must be true too, so here we don't need check respDeniedReg
+  io.resp.bits.needResend := crossPageBoundary && !respCorruptReg && !isRVC(respDataReg.asUInt)
 
   // state transfer
   switch(state) {
@@ -134,11 +136,12 @@ class InstrUncacheEntry(edge: TLEdgeOut)(implicit p: Parameters) extends InstrUn
           )
         )
 
-        // if is corrupted, we need to raise an exception anyway, so no need to resend request
+        // if crossing bus boundary, but not page boundary, we can automatically re-send request, except:
+        // 1. if has exception, we need to raise an exception anyway, so no need to resend request
         val respCorrupt = io.mmioGrant.bits.corrupt
-        // if response is rvc, we need only 2B, so no need to resend request
+        // 2. if response is rvc, we need only 2B, so no need to resend request
         val respIsRvc = isRVC(shiftedBusData)
-        // also, if we are already resending, we should not resend again
+        // 3. also, if we are already resending, we should not resend again
         val needResend = crossBusBoundary && !crossPageBoundary && !respCorrupt && !respIsRvc && !resending
 
         state     := Mux(needResend, State.RefillReq, State.SendResp)
```
