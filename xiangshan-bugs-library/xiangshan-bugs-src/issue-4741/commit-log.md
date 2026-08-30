# Commit Log
- Issue: #4741
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4741
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4741
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4741
- Changed files: 3
- Additions: 59
- Deletions: 22

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 4d556437a07..7c09f223cfa 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -1026,7 +1026,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   missQueue.io.occupy_set.zip(ldu.map(_.io.occupy_set)).foreach { case (l, r) => l <> r }
   missQueue.io.occupy_fail.zip(ldu.map(_.io.occupy_fail)).foreach { case (l, r) => l <> r }
   mainPipe.io.refill_info := missQueue.io.refill_info
-  mainPipe.io.replace_block := missQueue.io.replace_block
+  mainPipe.io.replace <> missQueue.io.replace
   mainPipe.io.sms_agt_evict_req <> io.sms_agt_evict_req
   io.memSetPattenDetected := missQueue.io.memSetPattenDetected
   io.wfi <> missQueue.io.wfi
@@ -1518,10 +1518,22 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   // tilelink stuff
   bus.a <> missQueue.io.mem_acquire
   bus.e <> missQueue.io.mem_finish
-  missQueue.io.probe_addr := bus.b.bits.address
-  missQueue.io.replace_addr := mainPipe.io.replace_addr
   missQueue.io.evict_set := mainPipe.io.evict_set
   missQueue.io.btot_ways_for_set <> mainPipe.io.btot_ways_for_set
+  missQueue.io.replace <> mainPipe.io.replace
+  missQueue.io.probe.req.valid := bus.b.valid
+  missQueue.io.probe.req.bits.addr := bus.b.bits.address
+  if(DCacheAboveIndexOffset > DCacheTagOffset) {
+    // have alias problem, extra alias bits needed for index
+    val alias_addr_frag = bus.b.bits.data(2, 1)
+    missQueue.io.probe.req.bits.vaddr := Cat(
+      bus.b.bits.address(PAddrBits - 1, DCacheAboveIndexOffset), // dontcare
+      alias_addr_frag(DCacheAboveIndexOffset - DCacheTagOffset - 1, 0), // index
+      bus.b.bits.address(DCacheTagOffset - 1, 0)                 // index & others
+    )
+  } else { // no alias problem
+    missQueue.io.probe.req.bits.vaddr := bus.b.bits.address
+  }
 
   missQueue.io.main_pipe_resp.valid := RegNext(mainPipe.io.atomic_resp.valid)
   missQueue.io.main_pipe_resp.bits := RegEnable(mainPipe.io.atomic_resp.bits, mainPipe.io.atomic_resp.valid)
@@ -1529,7 +1541,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   //----------------------------------------
   // probe
   // probeQueue.io.mem_probe <> bus.b
-  block_decoupled(bus.b, probeQueue.io.mem_probe, missQueue.io.probe_block)
+  block_decoupled(bus.b, probeQueue.io.mem_probe, missQueue.io.probe.block)
   probeQueue.io.lrsc_locked_block <> mainPipe.io.lrsc_locked_block
   probeQueue.io.update_resv_set <> mainPipe.io.update_resv_set
 
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 30de764a2cc..95eadd57824 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -183,8 +183,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     val btot_ways_for_set = Input(UInt(nWays.W))
 
     // writeback addr to be replaced
-    val replace_addr = ValidIO(UInt(PAddrBits.W))
-    val replace_block = Input(Bool())
+    val replace = new MissQueueBlockIO
 
     // sms prefetch
     val sms_agt_evict_req = DecoupledIO(new AGTEvictReq)
@@ -468,7 +467,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   )
 
   // For a store req, it either hits and goes to s3, or miss and enter miss queue immediately
-  val s2_replace_block = io.replace_block && io.replace_addr.valid
+  val s2_replace_block = io.replace.block && io.replace.req.valid
   val s2_req_miss_without_data = Mux(s2_valid, s2_req.miss && !io.refill_info.valid, false.B)
   val s2_can_go_to_mq_no_data = (s2_req_miss_without_data && RegEnable(s2_req_miss_without_data && !io.mainpipe_info.s2_replay_to_mq, false.B, s2_valid)) // miss_req in s2 but refill data is invalid, can block 1 cycle
   val s2_can_go_to_mq_evict_fail = s2_replace_block // dcache and miss queue both occupy the same set, (BtoT scheme)
@@ -938,8 +937,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   XSPerfAccumulate("fake_tag_write_intend", io.tag_write_intend && !io.tag_write.valid)
   XSPerfAccumulate("mainpipe_tag_write", io.tag_write.valid)
 
-  io.replace_addr.valid := s2_valid && s2_need_eviction && !s2_refill_tag_eq_way
-  io.replace_addr.bits  := get_block_addr(Cat(s2_tag, get_untag(s2_req.vaddr)))
+  io.replace.req.valid := s2_valid && s2_need_eviction && !s2_refill_tag_eq_way
+  io.replace.req.bits.addr := get_block_addr(Cat(s2_tag, get_untag(s2_req.vaddr)))
+  io.replace.req.bits.vaddr := s2_req.vaddr
 
   io.evict_set := addr_to_dcache_set(s2_req.vaddr) // only use set index
 
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 8b8ecb14e20..27d8a228d80 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -142,6 +142,15 @@ class MissResp(implicit p: Parameters) extends DCacheBundle {
   val merged = Bool()
 }
 
+class MissQueueBlockReqBundle(implicit p: Parameters) extends XSBundle {
+  val addr = UInt(PAddrBits.W)
+  val vaddr = UInt(VAddrBits.W)
+}
+
+class MissQueueBlockIO(implicit p: Parameters) extends XSBundle {
+  val req = ValidIO(new MissQueueBlockReqBundle)
+  val block = Input(Bool())
+}
 
 /**
   * miss queue enq logic: enq is now splited into 2 cycles
@@ -282,8 +291,8 @@ class MissReqPipeRegBundle(edge: TLEdgeOut)(implicit p: Parameters) extends DCac
     acquire
   }
 
-  def block_match(release_addr: UInt): Bool = {
-    reg_valid() && get_block(req.addr) === get_block(release_addr)
+  def block_and_alias_match(releaseReq: MissQueueBlockReqBundle): Bool = {
+    reg_valid() && get_block(req.addr) === get_block(releaseReq.addr) && is_alias_match(req.vaddr, releaseReq.vaddr)
   }
 
   def evict_set_match(evict_set: UInt): Bool = {
@@ -403,9 +412,14 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     // for main pipe s2
     val refill_info = ValidIO(new MissQueueRefillInfo)
 
-    val block_addr = ValidIO(UInt(PAddrBits.W))
     val occupy_way = Output(UInt(nWays.W))
 
+    // block probe
+    val probe = Flipped(new MissQueueBlockIO)
+
+    // block replace when release an addr valid in mshr
+    val replace = Flipped(new MissQueueBlockIO)
+
     val req_addr = ValidIO(UInt(PAddrBits.W))
     val req_vaddr = ValidIO(UInt(VAddrBits.W))
     val req_isBtoT = Output(Bool())
@@ -876,8 +890,13 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.main_pipe_req.bits.miss_fail_cause_evict_btot := evict_BtoT_way
   io.main_pipe_req.bits.miss_tag_error := req.tag_error
 
-  io.block_addr.valid := req_valid && w_grantlast
-  io.block_addr.bits := req.addr
+  io.probe.block := req_valid && w_grantlast &&
+    get_block_addr(req.addr) === get_block_addr(io.probe.req.bits.addr) &&
+    is_alias_match(req.vaddr, io.probe.req.bits.vaddr)
+
+  io.replace.block := req_valid &&
+    get_block_addr(req.addr) === get_block_addr(io.replace.req.bits.addr) &&
+    is_alias_match(req.vaddr, io.replace.req.bits.vaddr)
 
   io.req_addr.valid := req_valid
   io.req_addr.bits:= req.addr
@@ -984,12 +1003,10 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val refill_info = ValidIO(new MissQueueRefillInfo)
 
     // block probe
-    val probe_addr = Input(UInt(PAddrBits.W))
-    val probe_block = Output(Bool())
+    val probe = Flipped(new MissQueueBlockIO)
 
     // block replace when release an addr valid in mshr
-    val replace_addr = Flipped(ValidIO(UInt(PAddrBits.W)))
-    val replace_block = Output(Bool())
+    val replace = Flipped(new MissQueueBlockIO)
 
     // block all way for set to BtoT
     val evict_set = Input(UInt())
@@ -1040,7 +1057,11 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   val primary_ready_vec = entries.map(_.io.primary_ready)
   val secondary_ready_vec = entries.map(_.io.secondary_ready)
   val secondary_reject_vec = entries.map(_.io.secondary_reject)
-  val probe_block_vec = entries.map { case e => e.io.block_addr.valid && e.io.block_addr.bits === io.probe_addr }
+  val probe_block_vec = entries.map {
+    case e =>
+      e.io.probe.req <> io.probe.req
+      e.io.probe.block
+  }
 
   val merge = ParallelORR(Cat(secondary_ready_vec ++ Seq(miss_req_pipe_reg.merge_req(io.req.bits))))
   val reject = ParallelORR(Cat(secondary_reject_vec ++ Seq(miss_req_pipe_reg.reject_req(io.req.bits))))
@@ -1226,9 +1247,13 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   // amo's main pipe req out
   fastArbiter(entries.map(_.io.main_pipe_req), io.main_pipe_req, Some("main_pipe_req"))
 
-  io.probe_block := Cat(probe_block_vec).orR
+  io.probe.block := Cat(probe_block_vec).orR
+  io.replace.block := Cat(entries.map {
+    case e =>
+      e.io.replace.req <> io.replace.req
+      e.io.replace.block
+  } :+ miss_req_pipe_reg.block_and_alias_match(io.replace.req.bits)).orR
 
-  io.replace_block := Cat(entries.map(e => e.io.req_addr.valid && e.io.req_addr.bits === io.replace_addr.bits) ++ Seq(miss_req_pipe_reg.block_match(io.replace_addr.bits))).orR
   val btot_evict_set_hit = entries.map(e => e.io.req_isBtoT && e.io.req_vaddr.valid && addr_to_dcache_set(e.io.req_vaddr.bits) === io.evict_set) ++
     Seq(miss_req_pipe_reg.evict_set_match(io.evict_set))
   val btot_occupy_ways = entries.map(e => e.io.occupy_way) ++ Seq(miss_req_pipe_reg.req.occupy_way)
@@ -1287,7 +1312,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   XSPerfAccumulate("miss_req_prefetch_allocate", io.req.fire && !io.req.bits.cancel && alloc && io.req.bits.isFromPrefetch)
   XSPerfAccumulate("miss_req_merge_load", io.req.fire && !io.req.bits.cancel && merge && io.req.bits.isFromLoad)
   XSPerfAccumulate("miss_req_reject_load", io.req.valid && !io.req.bits.cancel && reject && io.req.bits.isFromLoad)
-  XSPerfAccumulate("probe_blocked_by_miss", io.probe_block)
+  XSPerfAccumulate("probe_blocked_by_miss", io.probe.block)
   XSPerfAccumulate("prefetch_primary_fire", io.req.fire && !io.req.bits.cancel && alloc && io.req.bits.isFromPrefetch)
   XSPerfAccumulate("prefetch_secondary_fire", io.req.fire && !io.req.bits.cancel && merge && io.req.bits.isFromPrefetch)
   XSPerfAccumulate("memSetPattenDetected", memSetPattenDetected)
```
