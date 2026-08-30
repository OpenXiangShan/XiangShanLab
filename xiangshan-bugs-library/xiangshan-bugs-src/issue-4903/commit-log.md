# Commit Log
- Issue: #4903
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4903
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4903
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4903
- Changed files: 3
- Additions: 13
- Deletions: 14

## Files
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/icache/InstrUncache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index fa43101cbf0..f28f3071230 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -421,7 +421,6 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
 
   instrUncache.io.req <> ifu.io.uncacheInter.toUncache
   ifu.io.uncacheInter.fromUncache <> instrUncache.io.resp
-  instrUncache.io.flush := false.B
   io.error <> RegNext(RegNext(icache.io.error))
 
   icache.io.hartId := io.hartId
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 566d857db57..d5ca41361c0 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -825,7 +825,7 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   // Exception or flush by older branch prediction
   // Condition is from RegNext(fromFtq.redirect), 1 cycle after backend rediect
-  when(f3_ftq_flush_self || f3_ftq_flush_by_older) {
+  when(f3_ftq_flush_self || f3_ftq_flush_by_older || mmioF3Flush && !f3_need_not_flush) {
     mmio_state                    := m_idle
     mmio_exception                := ExceptionType.none
     mmio_is_RVC                   := false.B
@@ -836,9 +836,10 @@ class NewIFU(implicit p: Parameters) extends XSModule
     f3_mmio_data.map(_ := 0.U)
   }
 
-  toUncache.valid     := ((mmio_state === m_sendReq) || (mmio_state === m_resendReq)) && f3_req_is_mmio
-  toUncache.bits.addr := Mux(mmio_state === m_resendReq, mmio_resend_addr, f3_paddrs(0))
-  fromUncache.ready   := true.B
+  toUncache.valid      := ((mmio_state === m_sendReq) || (mmio_state === m_resendReq)) && f3_req_is_mmio
+  toUncache.bits.addr  := Mux(mmio_state === m_resendReq, mmio_resend_addr, f3_paddrs(0))
+  toUncache.bits.flush := f3_ftq_flush_self || f3_ftq_flush_by_older || mmioF3Flush && !f3_need_not_flush
+  fromUncache.ready    := true.B
 
   // send itlb request in m_sendTLB state
   io.iTLBInter.req.valid                   := (mmio_state === m_sendTLB) && f3_req_is_mmio
diff --git a/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala b/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
index 4aa753a6959..71a55709b8c 100644
--- a/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
@@ -36,6 +36,8 @@ import xiangshan.frontend._
 
 class InsUncacheReq(implicit p: Parameters) extends ICacheBundle {
   val addr: UInt = UInt(PAddrBits.W)
+  // FIXME: this IO is re-organized in kunminghu-v3, this is a temp solution for v2
+  val flush: Bool = Bool()
 }
 
 class InsUncacheResp(implicit p: Parameters) extends ICacheBundle {
@@ -52,8 +54,7 @@ class InstrMMIOEntryIO(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheBu
   val mmio_acquire: DecoupledIO[TLBundleA] = DecoupledIO(new TLBundleA(edge.bundle))
   val mmio_grant:   DecoupledIO[TLBundleD] = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
 
-  val flush: Bool         = Input(Bool())
-  val wfi:   WfiReqBundle = Flipped(new WfiReqBundle)
+  val wfi: WfiReqBundle = Flipped(new WfiReqBundle)
 }
 
 // One miss entry deals with one mmio request
@@ -83,7 +84,7 @@ class InstrMMIOEntry(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
 
   private val needFlush = RegInit(false.B)
 
-  when(io.flush && (state =/= s_invalid) && (state =/= s_send_resp))(needFlush := true.B)
+  when(io.req.bits.flush && (state =/= s_invalid) && (state =/= s_send_resp))(needFlush := true.B)
     .elsewhen((state === s_send_resp) && needFlush)(needFlush := false.B)
 
   // --------------------------------------------
@@ -149,10 +150,9 @@ class InstrMMIOEntry(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
 }
 
 class InstrUncacheIO(implicit p: Parameters) extends ICacheBundle {
-  val req:   DecoupledIO[InsUncacheReq]  = Flipped(DecoupledIO(new InsUncacheReq))
-  val resp:  DecoupledIO[InsUncacheResp] = DecoupledIO(new InsUncacheResp)
-  val flush: Bool                        = Input(Bool())
-  val wfi:   WfiReqBundle                = Flipped(new WfiReqBundle)
+  val req:  DecoupledIO[InsUncacheReq]  = Flipped(DecoupledIO(new InsUncacheReq))
+  val resp: DecoupledIO[InsUncacheResp] = DecoupledIO(new InsUncacheResp)
+  val wfi:  WfiReqBundle                = Flipped(new WfiReqBundle)
 }
 
 class InstrUncache()(implicit p: Parameters) extends LazyModule with HasICacheParameters {
@@ -198,8 +198,7 @@ class InstrUncacheImp(outer: InstrUncache)
   private val entries = (0 until cacheParams.nMMIOs).map { i =>
     val entry = Module(new InstrMMIOEntry(edge))
 
-    entry.io.id    := i.U(log2Up(cacheParams.nMMIOs).W)
-    entry.io.flush := io.flush
+    entry.io.id := i.U(log2Up(cacheParams.nMMIOs).W)
 
     entry.io.wfi.wfiReq := io.wfi.wfiReq
```
