# Commit Log
- Issue: #5272
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5272
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5272
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5272
- Changed files: 5
- Additions: 194
- Deletions: 254

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`
- `src/main/scala/xiangshan/mem/prefetch/FDP.scala`
- `src/main/scala/xiangshan/mem/prefetch/PrefetcherMonitor.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index d8b65a7df2f..30ab22c1cdc 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -951,7 +951,6 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   val accessArray = Module(new L1FlagMetaArray(readPorts = AccessArrayReadPort, writePorts = LoadPipelineWidth + 1))
   val tagArray = Module(new DuplicatedTagArray(readPorts = TagReadPort))
   val prefetcherMonitor = Module(new PrefetcherMonitor)
-  val fdpMonitor =  Module(new FDPrefetcherMonitor)
   val bloomFilter =  Module(new BloomFilter(BLOOM_FILTER_ENTRY_NUM, true))
   val counterFilter = Module(new CounterFilter)
   bankedDataArray.dump()
@@ -1145,9 +1144,11 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     val extra_flag_prefetch = Mux1H(extra_flag_way_en, prefetchArray.io.resp.last)
     val extra_flag_access = Mux1H(extra_flag_way_en, accessArray.io.resp.last)
 
-    prefetcherMonitor.io.validity.valid := extra_flag_valid
-    prefetcherMonitor.io.validity.bits.access := extra_flag_access
-    prefetcherMonitor.io.validity.bits.pf_source := extra_flag_prefetch
+    prefetcherMonitor.io.maininfo.pf_useless := extra_flag_valid && !extra_flag_access && isFromL1Prefetch(extra_flag_prefetch)
+    prefetcherMonitor.io.maininfo.pf_source_useless := extra_flag_prefetch
+
+    prefetcherMonitor.io.maininfo.hit_pf_in_cache := extra_flag_valid && extra_flag_access && isFromL1Prefetch(extra_flag_prefetch)
+    prefetcherMonitor.io.maininfo.hit_pf_source_in_cache := extra_flag_prefetch
   }
 
   // write extra meta
@@ -1172,10 +1173,6 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     latency_flag_write_ports.foreach { case p => p.ready := true.B }
   }
 
-  // FIXME: add hybrid unit?
-  val same_cycle_update_pf_flag = ldu(0).io.prefetch_flag_write.valid && ldu(1).io.prefetch_flag_write.valid && (ldu(0).io.prefetch_flag_write.bits.idx === ldu(1).io.prefetch_flag_write.bits.idx) && (ldu(0).io.prefetch_flag_write.bits.way_en === ldu(1).io.prefetch_flag_write.bits.way_en)
-  XSPerfAccumulate("same_cycle_update_pf_flag", same_cycle_update_pf_flag)
-
   val access_flag_write_ports = ldu.map(_.io.access_flag_write) ++ Seq(
     mainPipe.io.access_flag_write
     // refillPipe.io.access_flag_write
@@ -1349,21 +1346,24 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     ldu(w).io.disable_ld_fast_wakeup :=
       bankedDataArray.io.disable_ld_fast_wakeup(w) // load pipe fast wake up should be disabled when bank conflict
   }
+  
+  val clear_flag = Wire(Vec(LoadPipelineWidth, Bool()))
+  clear_flag(0) := false.B
+  for (i <- 1 until LoadPipelineWidth) {
+    val conflictWithEarlier = (0 until i).map { j =>
+      (ldu(i).io.prefetch_flag_write.bits.idx === ldu(j).io.prefetch_flag_write.bits.idx) &&
+      (ldu(i).io.prefetch_flag_write.bits.way_en === ldu(j).io.prefetch_flag_write.bits.way_en)
+    }.reduce(_ || _)
+    clear_flag(i) := conflictWithEarlier
+  }
 
   for (w <- 0 until LoadPipelineWidth) {
-    prefetcherMonitor.io.timely(w).total_prefetch := ldu(w).io.prefetch_info.naive.total_prefetch
-    prefetcherMonitor.io.timely(w).late_hit_prefetch := ldu(w).io.prefetch_info.naive.late_hit_prefetch
-    prefetcherMonitor.io.timely(w).pf_source := ldu(w).io.prefetch_info.naive.pf_source
-    prefetcherMonitor.io.timely(w).prefetch_hit := ldu(w).io.prefetch_info.naive.prefetch_hit
-    prefetcherMonitor.io.timely(w).hit_source := ldu(w).io.prefetch_info.naive.hit_source
-    prefetcherMonitor.io.timely(w).late_miss_prefetch := missQueue.io.prefetch_info.naive.late_miss_prefetch
-    prefetcherMonitor.io.timely(w).miss_source := missQueue.io.prefetch_info.naive.pf_source
+    prefetcherMonitor.io.loadinfo(w) := ldu(w).io.prefetch_stat
   }
+  prefetcherMonitor.io.missinfo := missQueue.io.prefetch_stat
+  prefetcherMonitor.io.debugRolling := io.debugRolling
+  prefetcherMonitor.io.clear_flag := clear_flag
   io.pf_ctrl <> prefetcherMonitor.io.pf_ctrl
-  XSPerfAccumulate("useless_prefetch", ldu.map(_.io.prefetch_info.naive.total_prefetch).reduce(_ || _) && !(ldu.map(_.io.prefetch_info.naive.useful_prefetch).reduce(_ || _)))
-  XSPerfAccumulate("useful_prefetch", ldu.map(_.io.prefetch_info.naive.useful_prefetch).reduce(_ || _))
-  XSPerfAccumulate("late_prefetch_hit", ldu.map(_.io.prefetch_info.naive.late_prefetch_hit).reduce(_ || _))
-  XSPerfAccumulate("late_load_hit", ldu.map(_.io.prefetch_info.naive.late_load_hit).reduce(_ || _))
 
   /** LoadMissDB: record load miss state */
   val hartId = p(XSCoreParamsKey).HartId
@@ -1604,22 +1604,6 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     assert (!bus.d.fire)
   }
 
-  //----------------------------------------
-  // Feedback Direct Prefetch Monitor
-  fdpMonitor.io.refill := missQueue.io.prefetch_info.fdp.prefetch_monitor_cnt
-  fdpMonitor.io.timely.late_prefetch := missQueue.io.prefetch_info.fdp.late_miss_prefetch
-  fdpMonitor.io.accuracy.total_prefetch := missQueue.io.prefetch_info.fdp.total_prefetch
-  for (w <- 0 until LoadPipelineWidth)  {
-    if(w == 0) {
-      fdpMonitor.io.accuracy.useful_prefetch(w) := ldu(w).io.prefetch_info.fdp.useful_prefetch
-    }else {
-      fdpMonitor.io.accuracy.useful_prefetch(w) := Mux(same_cycle_update_pf_flag, false.B, ldu(w).io.prefetch_info.fdp.useful_prefetch)
-    }
-  }
-  for (w <- 0 until LoadPipelineWidth)  { fdpMonitor.io.pollution.cache_pollution(w) :=  ldu(w).io.prefetch_info.fdp.pollution }
-  for (w <- 0 until LoadPipelineWidth)  { fdpMonitor.io.pollution.demand_miss(w) :=  ldu(w).io.prefetch_info.fdp.demand_miss }
-  fdpMonitor.io.debugRolling := io.debugRolling
-
   //----------------------------------------
   // Bloom Filter
   // bloomFilter.io.set <> missQueue.io.bloom_filter_query.set
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index 8d0dd808c2f..b5c73f46210 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -87,25 +87,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
     val pseudo_tag_error_inj_done = Output(Bool())
     val pseudo_data_error_inj_done = Output(Bool())
 
-    val prefetch_info = new Bundle {
-      val naive = new Bundle {
-        val total_prefetch = Output(Bool())
-        val late_hit_prefetch = Output(Bool())
-        val late_prefetch_hit = Output(Bool())
-        val late_load_hit = Output(Bool())
-        val useless_prefetch = Output(Bool())
-        val useful_prefetch = Output(Bool())
-        val pf_source = Output(UInt(L1PfSourceBits.W))
-        val prefetch_hit = Output(Bool())
-        val hit_source = Output(UInt(L1PfSourceBits.W))
-      }
-
-      val fdp = new Bundle {
-        val useful_prefetch = Output(Bool())
-        val demand_miss = Output(Bool())
-        val pollution = Output(Bool())
-      }
-    }
+    val prefetch_stat = Output(new LoadPrefetchStatBundle)
 
     val bloom_filter_query = new Bundle {
       val query = ValidIO(new BloomQueryBundle(BLOOM_FILTER_ENTRY_NUM))
@@ -505,27 +487,20 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
 
   // if ldu0 and ldu1 hit the same, count for 1
   val total_prefetch = s2_valid && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
-  val late_hit_prefetch = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
-  val late_load_hit = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && !isFromL1Prefetch(s2_hit_prefetch)
-  val late_prefetch_hit = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && isFromL1Prefetch(s2_hit_prefetch)
-  val useless_prefetch = s2_miss_req_fire && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
-  val useful_prefetch = s2_valid && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && resp.bits.handled && !io.miss_resp.merged
-
-  val prefetch_hit = Wire(Bool()) // assigned in s3 for filtering
+  val pf_late_in_cache = s2_valid && s2_hit && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
+  val hit_pf_in_cache = Wire(Bool()) // assigned in s3 for filtering
   val hit_source = Wire(UInt(L1PfSourceBits.W))
+  
+  io.prefetch_stat.total_prefetch := total_prefetch
+  io.prefetch_stat.pf_late_in_cache := pf_late_in_cache
+  io.prefetch_stat.nack_prefetch := s2_valid && s2_nack && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
+  io.prefetch_stat.pf_source := s2_pf_source
 
-  io.prefetch_info.naive.total_prefetch := total_prefetch
-  io.prefetch_info.naive.late_hit_prefetch := late_hit_prefetch
-  io.prefetch_info.naive.late_load_hit := late_load_hit
-  io.prefetch_info.naive.late_prefetch_hit := late_prefetch_hit
-  io.prefetch_info.naive.useless_prefetch := useless_prefetch
-  io.prefetch_info.naive.useful_prefetch := useful_prefetch
-  io.prefetch_info.naive.pf_source := s2_pf_source
-  io.prefetch_info.naive.prefetch_hit := prefetch_hit
-  io.prefetch_info.naive.hit_source := hit_source
+  io.prefetch_stat.hit_pf_in_cache := hit_pf_in_cache
+  io.prefetch_stat.hit_source := hit_source
 
-  io.prefetch_info.fdp.demand_miss := s2_valid && (s2_req.instrtype =/= DCACHE_PREFETCH_SOURCE.U) && !s2_hit && s2_req.isFirstIssue
-  io.prefetch_info.fdp.pollution := io.prefetch_info.fdp.demand_miss && io.bloom_filter_query.resp.valid && io.bloom_filter_query.resp.bits.res
+  io.prefetch_stat.demand_miss := s2_valid && (s2_req.instrtype =/= DCACHE_PREFETCH_SOURCE.U) && !s2_hit && s2_req.isFirstIssue
+  io.prefetch_stat.pollution := io.prefetch_stat.demand_miss && io.bloom_filter_query.resp.valid && io.bloom_filter_query.resp.bits.res
 
   io.lsu.resp.valid := resp.valid
   io.lsu.resp.bits := resp.bits
@@ -610,8 +585,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.counter_filter_enq.bits.idx := get_idx(s3_vaddr)
   io.counter_filter_enq.bits.way := OHToUInt(s3_tag_match_way)
 
-  io.prefetch_info.fdp.useful_prefetch := s3_clear_pf_flag_en && !io.counter_filter_query.resp
-  prefetch_hit := s3_clear_pf_flag_en && !io.counter_filter_query.resp
+  hit_pf_in_cache := s3_clear_pf_flag_en && !io.counter_filter_query.resp
   hit_source := s3_hit_prefetch
 
   XSPerfAccumulate("s3_pf_hit", s3_clear_pf_flag_en)
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 4ce566ecdb6..1ed3cc10dea 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -448,7 +448,8 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     }
 
     val prefetch_info = new DCacheBundle {
-      val late_prefetch = Output(Bool())
+      val hit_prefetch = Output(Bool())
+      val hit_pf_source = UInt(L1PfSourceBits.W)
     }
     val nMaxPrefetchEntry = Input(UInt(64.W))
     val matched = Output(Bool())
@@ -946,9 +947,10 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.forwardInfo.corrupt := error
 
   io.matched := req_valid && (get_block(req.addr) === get_block(io.req.bits.addr)) && !prefetch
-  io.prefetch_info.late_prefetch := io.req.valid && !(io.req.bits.isFromPrefetch) && req_valid && (get_block(req.addr) === get_block(io.req.bits.addr)) && prefetch
+  io.prefetch_info.hit_prefetch := io.req.valid && !(io.req.bits.isFromPrefetch) && req_valid && (get_block(req.addr) === get_block(io.req.bits.addr)) && prefetch
+  io.prefetch_info.hit_pf_source := req.pf_source
 
-  when(io.prefetch_info.late_prefetch) {
+  when(io.prefetch_info.hit_prefetch) {
     prefetch := false.B
   }
 
@@ -1047,18 +1049,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     val memSetPattenDetected = Output(Bool())
     val lqEmpty = Input(Bool())
 
-    val prefetch_info = new Bundle {
-      val naive = new Bundle {
-        val late_miss_prefetch = Output(Bool())
-        val pf_source = Output(UInt(L1PfSourceBits.W))
-      }
-
-      val fdp = new Bundle {
-        val late_miss_prefetch = Output(Bool())
-        val prefetch_monitor_cnt = Output(Bool())
-        val total_prefetch = Output(Bool())
-      }
-    }
+    val prefetch_stat = Output(new MissPrefetchStatBundle)
 
     val wfi = Flipped(new WfiReqBundle)
 
@@ -1298,12 +1289,17 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.full := ~Cat(entries.map(_.io.primary_ready)).andR
 
   // prefetch related
-  io.prefetch_info.naive.late_miss_prefetch := io.req.valid && io.req.bits.isPrefetchRead && (miss_req_pipe_reg.matched(io.req.bits) || Cat(entries.map(_.io.matched)).orR)
-  io.prefetch_info.naive.pf_source := io.req.bits.pf_source
-
-  io.prefetch_info.fdp.late_miss_prefetch := (miss_req_pipe_reg.prefetch_late_en(io.req.bits.toMissReqWoStoreData(), io.req.valid) || Cat(entries.map(_.io.prefetch_info.late_prefetch)).orR)
-  io.prefetch_info.fdp.prefetch_monitor_cnt := io.main_pipe_req.fire
-  io.prefetch_info.fdp.total_prefetch := alloc && io.req.valid && !io.req.bits.cancel && isFromL1Prefetch(io.req.bits.pf_source)
+  val hit_pf_reg = miss_req_pipe_reg.prefetch_late_en(io.req.bits.toMissReqWoStoreData(), io.req.valid)
+  io.prefetch_stat.pf_late_in_mshr := io.req.valid && io.req.bits.isFromPrefetch && (miss_req_pipe_reg.matched(io.req.bits) || Cat(entries.map(_.io.matched)).orR)
+  io.prefetch_stat.prefetch_miss := accept && io.req.fire && !io.req.bits.cancel && io.req.bits.isFromPrefetch
+  io.prefetch_stat.pf_source := io.req.bits.pf_source
+  io.prefetch_stat.load_miss := accept && io.req.fire && !io.req.bits.cancel && io.req.bits.isFromLoad
+  io.prefetch_stat.hit_pf_in_mshr := hit_pf_reg || Cat(entries.map(_.io.prefetch_info.hit_prefetch)).orR
+  io.prefetch_stat.hit_pf_source_in_mshr := ParallelMux(
+    Seq(hit_pf_reg) ++ entries.map(_.io.prefetch_info.hit_prefetch)
+    zip
+    Seq(miss_req_pipe_reg.req.pf_source) ++ entries.map(_.io.prefetch_info.hit_pf_source)
+  )
 
   // L1MissTrace Chisel DB
   val debug_miss_trace = Wire(new L1MissTrace)
diff --git a/src/main/scala/xiangshan/mem/prefetch/FDP.scala b/src/main/scala/xiangshan/mem/prefetch/FDP.scala
index c97b5fad949..ae3a4258a20 100644
--- a/src/main/scala/xiangshan/mem/prefetch/FDP.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/FDP.scala
@@ -200,101 +200,4 @@ class BloomFilter(n: Int, bypass: Boolean = true)(implicit p: Parameters) extend
   assert(PopCount(data ^ data_next.asUInt) <= 2.U)
 
   XSPerfHistogram("valid_nums", PopCount(data), true.B, 0, n + 1, 20)
-}
-
-class FDPrefetcherMonitorBundle()(implicit p: Parameters) extends XSBundle {
-  val refill = Input(Bool()) // from refill pipe, fire
-  val accuracy = new XSBundle {
-    val total_prefetch = Input(Bool()) // from mshr enq, fire, alloc, prefetch
-    val useful_prefetch = Vec(LoadPipelineWidth, Input(Bool())) // from load pipeline, prefetch hit
-  }
-
-  val timely = new XSBundle {
-    val late_prefetch = Input(Bool()) // from mshr enq, a load matches a mshr caused by prefetch
-  }
-
-  val pollution = new XSBundle {
-    val demand_miss = Vec(LoadPipelineWidth, Input(Bool())) // from load pipeline, first miss
-    val cache_pollution = Vec(LoadPipelineWidth, Input(Bool())) // from load pipeline, first miss and pollution caused
-  }
-
-  val pf_ctrl = Output(new PrefetchControlBundle)
-  val debugRolling = Flipped(new RobDebugRollingIO)
-}
-
-class FDPrefetcherMonitor()(implicit p: Parameters) extends XSModule {
-  val io = IO(new FDPrefetcherMonitorBundle)
-
-  val INTERVAL = 8192
-  val CNTWIDTH = log2Up(INTERVAL) + 1
-
-  io.pf_ctrl := DontCare
-
-  val refill_cnt = RegInit(0.U(CNTWIDTH.W))
-
-  val total_prefetch_prev_cnt = RegInit(0.U(CNTWIDTH.W))
-  val useful_prefetch_prev_cnt = RegInit(0.U(CNTWIDTH.W))
-  val late_prefetch_prev_cnt = RegInit(0.U(CNTWIDTH.W))
-  val demand_miss_prev_cnt = RegInit(0.U(CNTWIDTH.W))
-  val pollution_prev_cnt = RegInit(0.U(CNTWIDTH.W))
-  val prev_cnts = Seq(total_prefetch_prev_cnt, useful_prefetch_prev_cnt, late_prefetch_prev_cnt, demand_miss_prev_cnt, pollution_prev_cnt)
-
-  val total_prefetch_interval_cnt = RegInit(0.U(CNTWIDTH.W))
-  val useful_prefetch_interval_cnt = RegInit(0.U(CNTWIDTH.W))
-  val late_prefetch_interval_cnt = RegInit(0.U(CNTWIDTH.W))
-  val demand_miss_interval_cnt = RegInit(0.U(CNTWIDTH.W))
-  val pollution_interval_cnt = RegInit(0.U(CNTWIDTH.W))
-  val interval_cnts = Seq(total_prefetch_interval_cnt, useful_prefetch_interval_cnt, late_prefetch_interval_cnt, demand_miss_interval_cnt, pollution_interval_cnt)
-
-  val interval_trigger = refill_cnt === INTERVAL.U
-
-  val io_ens = Seq(io.accuracy.total_prefetch, io.accuracy.useful_prefetch, io.timely.late_prefetch, io.pollution.demand_miss, io.pollution.cache_pollution)
-
-  for((interval, en) <- interval_cnts.zip(io_ens)) {
-    interval := interval + PopCount(en.asUInt)
-  }
-
-  when(io.refill) {
-    refill_cnt := refill_cnt + 1.U
-  }
-
-  when(interval_trigger) {
-    refill_cnt := 0.U
-    for((prev, interval) <- prev_cnts.zip(interval_cnts)) {
-      prev := Cat(0.U(1.W), prev(prev.getWidth - 1, 1)) + Cat(0.U(1.W), interval(interval.getWidth - 1, 1))
-      interval := 0.U
-    }
-  }
-
-  // rolling by instr
-  XSPerfRolling(
-    "L1PrefetchAccuracyIns",
-    PopCount(io.accuracy.useful_prefetch), PopCount(io.accuracy.total_prefetch),
-    1000, io.debugRolling.robTrueCommit, clock, reset
-  )
-
-  XSPerfRolling(
-    "L1PrefetchLatenessIns",
-    PopCount(io.timely.late_prefetch), PopCount(io.accuracy.total_prefetch),
-    1000, io.debugRolling.robTrueCommit, clock, reset
-  )
-
-  XSPerfRolling(
-    "L1PrefetchPollutionIns",
-    PopCount(io.pollution.cache_pollution), PopCount(io.pollution.demand_miss),
-    1000, io.debugRolling.robTrueCommit, clock, reset
-  )
-
-  XSPerfRolling(
-    "IPCIns",
-    io.debugRolling.robTrueCommit, 1.U,
-    1000, io.debugRolling.robTrueCommit, clock, reset
-  )
-
-  XSPerfAccumulate("io_refill", io.refill)
-  XSPerfAccumulate("total_prefetch_new_data_en", io.accuracy.total_prefetch)
-  XSPerfAccumulate("useful_prefetch_en", PopCount(io.accuracy.useful_prefetch) + io.timely.late_prefetch)
-  XSPerfAccumulate("late_prefetch_en", io.timely.late_prefetch)
-  XSPerfAccumulate("demand_miss_en", PopCount(io.pollution.demand_miss))
-  XSPerfAccumulate("cache_pollution_en", PopCount(io.pollution.cache_pollution))
 }
\ No newline at end of file
diff --git a/src/main/scala/xiangshan/mem/prefetch/PrefetcherMonitor.scala b/src/main/scala/xiangshan/mem/prefetch/PrefetcherMonitor.scala
index 7be87f472d8..8555102cf32 100644
--- a/src/main/scala/xiangshan/mem/prefetch/PrefetcherMonitor.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/PrefetcherMonitor.scala
@@ -9,6 +9,7 @@ import xiangshan._
 import xiangshan.mem.L1PrefetchReq
 import xiangshan.mem.Bundles.LsPrefetchTrainBundle
 import xiangshan.mem.HasL1PrefetchSourceParameter
+import xiangshan.backend.rob.RobDebugRollingIO
 
 class PrefetchControlBundle()(implicit p: Parameters) extends XSBundle with HasStreamPrefetchHelper {
   val dynamic_depth = UInt(DEPTH_BITS.W)
@@ -17,62 +18,138 @@ class PrefetchControlBundle()(implicit p: Parameters) extends XSBundle with HasS
   val confidence = UInt(1.W)
 }
 
-class PrefetchValidityBundle()(implicit p: Parameters) extends XSBundle with HasL1PrefetchSourceParameter {
-  val access = Bool()
+class LoadPrefetchStatBundle()(implicit p: Parameters) extends XSBundle with HasL1PrefetchSourceParameter {
+  val total_prefetch = Bool() // from loadpipe s2, pf req sent
+  val pf_late_in_cache = Bool() // from loadpipe s2, pf req sent but hit
+  val nack_prefetch = Bool() // from loadpipe s2, pf req miss but nack
   val pf_source = UInt(L1PfSourceBits.W)
+
+  val hit_pf_in_cache = Bool() // from loadpipe s3, pf block hit by demand, clear pf flag
+  val hit_source = UInt(L1PfSourceBits.W)
+
+  val demand_miss = Bool() // from loadpipe s2, demand req miss
+  val pollution = Bool() // from loadpipe s2, bloom filter speculate pollution
+}
+
+class MainPrefetchStatBundle()(implicit p: Parameters) extends XSBundle with HasL1PrefetchSourceParameter {
+  val pf_useless = Bool() // from mainpipe replace, prefetch block but not accessed
+  val pf_source_useless = UInt(L1PfSourceBits.W)
+
+  val hit_pf_in_cache = Bool() // from mainpipe, refill accessed pf block | store req hit pf block
+  val hit_pf_source_in_cache = UInt(L1PfSourceBits.W)
 }
 
-class PrefetchTimelyBundle()(implicit p: Parameters) extends XSBundle with HasL1PrefetchSourceParameter {
-  val total_prefetch = Bool()
-  val late_hit_prefetch = Bool()
+class MissPrefetchStatBundle()(implicit p: Parameters) extends XSBundle with HasL1PrefetchSourceParameter {
+  val pf_late_in_mshr = Bool() // from missqueue, pf req match a existing mshr
+  val prefetch_miss = Bool() // from missqueue, pf req allocate a new mshr
   val pf_source = UInt(L1PfSourceBits.W)
-  // prefetch_hit is s3
-  val prefetch_hit = Bool()
-  val hit_source = UInt(L1PfSourceBits.W)
-  // late_miss_prefetch is s2
-  val late_miss_prefetch = Bool()
-  val miss_source = UInt(L1PfSourceBits.W)
+
+  val hit_pf_in_mshr = Bool() // from missqueue, demand miss match a existing pf mshr, then clear pf flag
+  val hit_pf_source_in_mshr = UInt(L1PfSourceBits.W) // from missqueue, the pf source of demand miss matched
+  val load_miss = Bool() // from missqueue, load demand miss allocate a new mshr
 }
 
 class PrefetcherMonitorBundle()(implicit p: Parameters) extends XSBundle with HasL1PrefetchSourceParameter {
-  val timely = Input(Vec(LoadPipelineWidth, new PrefetchTimelyBundle))
+  val loadinfo = Input(Vec(LoadPipelineWidth, new LoadPrefetchStatBundle))
+  val missinfo = Input(new MissPrefetchStatBundle)
+  val maininfo = Input(new MainPrefetchStatBundle)
 
-  val validity = Flipped(ValidIO(new PrefetchValidityBundle))
+  val clear_flag = Input(Vec(LoadPipelineWidth, Bool()))
 
   val pf_ctrl = Output(Vec(L1PrefetcherNum, new PrefetchControlBundle))
+
+  val debugRolling = Flipped(new RobDebugRollingIO)
 }
 
 class PrefetcherMonitor()(implicit p: Parameters) extends XSModule with HasStreamPrefetchHelper {
   val io = IO(new PrefetcherMonitorBundle)
 
+  val prefetch_info = Wire(new L1PrefetchStatisticBundle)
+  prefetch_info.loadinfo := io.loadinfo 
+  prefetch_info.missinfo := io.missinfo
+  prefetch_info.maininfo := io.maininfo
+
+  for (i <- 0 until LoadPipelineWidth) {
+    when(io.clear_flag(i)) {
+      prefetch_info.loadinfo(i).hit_pf_in_cache := false.B
+    }
+  }
+
   val StreamMonitor = Module(new L1PrefetchMonitor(PrefetcherMonitorParam.fromString("stream")))
   val StrideMonitor = Module(new L1PrefetchMonitor(PrefetcherMonitorParam.fromString("stride")))
 
-  StreamMonitor.io.timely := io.timely
-  StrideMonitor.io.timely := io.timely
-  StreamMonitor.io.validity := io.validity
-  StrideMonitor.io.validity := io.validity
+  StreamMonitor.io.prefetch_info:= prefetch_info
+  StrideMonitor.io.prefetch_info := prefetch_info
+  
   // stream 0, stride 1
   io.pf_ctrl(0) := StreamMonitor.io.pf_ctrl
   io.pf_ctrl(1) := StrideMonitor.io.pf_ctrl
 
-  val total_prefetch = io.timely.map(t => t.total_prefetch).reduce(_ || _)
-  val late_hit_prefetch = io.timely.map(t => t.late_hit_prefetch).reduce(_ || _)
-  val late_miss_prefetch = io.timely.map(t => t.late_miss_prefetch).reduce(_ || _)
-  val good_prefetch = io.timely.map(t => t.prefetch_hit ).reduce(_ || _)
-  val bad_prefetch = io.validity.valid && !io.validity.bits.access
+  // ldu 0, 1, 2 can only have one prefetch request at a time
+  val total_prefetch = io.loadinfo.map(t => t.total_prefetch).reduce(_ || _)
+  val nack_prefetch = io.loadinfo.map(t => t.nack_prefetch).reduce(_ || _)
+  val pf_late_in_cache = io.loadinfo.map(t => t.pf_late_in_cache).reduce(_ || _)
+  val pf_late_in_mshr = io.missinfo.pf_late_in_mshr
+  val pf_late = pf_late_in_cache.asUInt + pf_late_in_mshr.asUInt
+  // demand accesses from different ldu may hit different prefetch blocks
+  val hit_pf_in_cache = PopCount(prefetch_info.loadinfo.map(t => t.hit_pf_in_cache) ++ Seq(prefetch_info.maininfo.hit_pf_in_cache))
+  val hit_pf_in_mshr = io.missinfo.hit_pf_in_mshr
+  val hit_pf = hit_pf_in_cache + hit_pf_in_mshr.asUInt
+  val pf_useless = io.maininfo.pf_useless
+  val prefetch_miss = io.missinfo.prefetch_miss
+  val load_miss_to_mshr = io.missinfo.load_miss
+  // ldu 0, 1, 2 can have multiple demand accesses at a time
+  val demand_miss_in_ldu = PopCount(io.loadinfo.map(t => t.demand_miss))
+  val pollution = PopCount(io.loadinfo.map(t => t.pollution))
   
-  XSPerfAccumulate("total_prefetch", total_prefetch)
-  XSPerfAccumulate("late_hit_prefetch", late_hit_prefetch)
-  XSPerfAccumulate("late_miss_prefetch", late_miss_prefetch)
-  XSPerfAccumulate("good_prefetch", good_prefetch)
-  XSPerfAccumulate("bad_prefetch", bad_prefetch)
+  XSPerfAccumulate("l1DemandMiss", demand_miss_in_ldu)
+  XSPerfAccumulate("l1prefetchSent", total_prefetch)
+  XSPerfAccumulate("l1prefetchHit", hit_pf)
+  XSPerfAccumulate("l1prefetchHitInCache", hit_pf_in_cache)
+  XSPerfAccumulate("l1prefetchHitInMSHR", hit_pf_in_mshr)
+  XSPerfAccumulate("l1prefetchLate", pf_late)
+  XSPerfAccumulate("l1prefetchLateInCache", pf_late_in_cache)
+  XSPerfAccumulate("l1prefetchLateInMSHR", pf_late_in_mshr)
+  XSPerfAccumulate("l1prefetchUseless", pf_useless)
+  XSPerfAccumulate("l1prefetchDropByNack", nack_prefetch)
+  XSPerfAccumulate("mshr_count_Prefetch", prefetch_miss)
+  XSPerfAccumulate("mshr_count_CPU", load_miss_to_mshr)
+  XSPerfAccumulate("cache_pollution", pollution)
+
+  // rolling by instr
+  XSPerfRolling(
+    "L1PrefetchAccuracyIns",
+    hit_pf_in_cache, total_prefetch,
+    1000, io.debugRolling.robTrueCommit, clock, reset
+  )
+  
+  XSPerfRolling(
+    "L1PrefetchLatenessIns",
+    hit_pf_in_mshr, prefetch_miss,
+    1000, io.debugRolling.robTrueCommit, clock, reset
+  )
+
+  XSPerfRolling(
+    "L1PrefetchPollutionIns",
+    pollution, demand_miss_in_ldu,
+    1000, io.debugRolling.robTrueCommit, clock, reset
+  )
+
+  XSPerfRolling(
+    "IPCIns",
+    io.debugRolling.robTrueCommit, 1.U,
+    1000, io.debugRolling.robTrueCommit, clock, reset
+  )
 }
 
-class L1PrefetchMonitorBundle()(implicit p: Parameters) extends XSBundle {
-  val timely = Input(Vec(LoadPipelineWidth, new PrefetchTimelyBundle))
+class L1PrefetchStatisticBundle()(implicit p: Parameters) extends XSBundle {
+  val loadinfo = Vec(LoadPipelineWidth, new LoadPrefetchStatBundle)
+  val missinfo = new MissPrefetchStatBundle
+  val maininfo = new MainPrefetchStatBundle
+}
 
-  val validity = Flipped(ValidIO(new PrefetchValidityBundle))
+class L1PrefetchMonitorBundle()(implicit p: Parameters) extends XSBundle {
+  val prefetch_info = Input(new L1PrefetchStatisticBundle)
 
   val pf_ctrl = Output(new PrefetchControlBundle)
 }
@@ -98,48 +175,50 @@ class L1PrefetchMonitor(param : PrefetcherMonitorParam)(implicit p: Parameters)
   depth_const := Constantin.createRecord(s"${param.name}_depth${p(XSCoreParamsKey).HartId}", initValue = 32)
 
   val total_prefetch_cnt = RegInit(0.U((log2Up(param.TIMELY_CHECK_INTERVAL) + 1).W))
-  val late_hit_prefetch_cnt = RegInit(0.U((log2Up(param.TIMELY_CHECK_INTERVAL) + 1).W))
-  val late_miss_prefetch_cnt = RegInit(0.U((log2Up(param.TIMELY_CHECK_INTERVAL) + 1).W))
-  // val prefetch_hit_cnt = RegInit(0.U(32.W))
+  val pf_late_in_cache_cnt = RegInit(0.U((log2Up(param.TIMELY_CHECK_INTERVAL) + 1).W))
+  val pf_late_in_mshr_cnt = RegInit(0.U((log2Up(param.TIMELY_CHECK_INTERVAL) + 1).W))
 
-  val good_prefetch_cnt = RegInit(0.U((log2Up(param.VALIDITY_CHECK_INTERVAL) + 1).W))
-  val bad_prefetch_cnt = RegInit(0.U((log2Up(param.VALIDITY_CHECK_INTERVAL) + 1).W))
+  val hit_pf_in_cache_cnt = RegInit(0.U((log2Up(param.VALIDITY_CHECK_INTERVAL) + 1).W))
+  val pf_useless_cnt = RegInit(0.U((log2Up(param.VALIDITY_CHECK_INTERVAL) + 1).W))
 
   val back_off_cnt = RegInit(0.U((log2Up(param.BACK_OFF_INTERVAL) + 1).W))
   val low_conf_cnt = RegInit(0.U((log2Up(param.LOW_CONF_INTERVAL) + 1).W))
 
-  val timely_reset = (total_prefetch_cnt === param.TIMELY_CHECK_INTERVAL.U) || (late_hit_prefetch_cnt >= param.TIMELY_CHECK_INTERVAL.U)
-  val validity_reset = (good_prefetch_cnt + bad_prefetch_cnt) === param.VALIDITY_CHECK_INTERVAL.U
+  val timely_reset = (total_prefetch_cnt === param.TIMELY_CHECK_INTERVAL.U) || (pf_late_in_cache_cnt >= param.TIMELY_CHECK_INTERVAL.U)
+  val validity_reset = (hit_pf_in_cache_cnt + pf_useless_cnt) === param.VALIDITY_CHECK_INTERVAL.U
   val back_off_reset = back_off_cnt === param.BACK_OFF_INTERVAL.U
   val conf_reset = low_conf_cnt === param.LOW_CONF_INTERVAL.U
 
-  val total_prefetch = io.timely.map(t => t.total_prefetch && param.isMyType(t.pf_source)).reduce(_ || _)
-  val late_hit_prefetch = io.timely.map(t => t.late_hit_prefetch && param.isMyType(t.hit_source)).reduce(_ || _)
-  val late_miss_prefetch = io.timely.map(t => t.late_miss_prefetch && param.isMyType(t.miss_source)).reduce(_ || _)
-  // val prefetch_hit = io.timely.map(t => t.prefetch_hit && (param.isMyType(t.hit_source) || param.isMyClearType(t.hit_source))).reduce(_ || _)
-  total_prefetch_cnt := Mux(timely_reset, 0.U, total_prefetch_cnt + total_prefetch)
-  late_hit_prefetch_cnt := Mux(timely_reset, 0.U, late_hit_prefetch_cnt + late_hit_prefetch)
-  late_miss_prefetch_cnt := Mux(timely_reset, 0.U, late_miss_prefetch_cnt + late_miss_prefetch)
-  // prefetch_hit_cnt := Mux(timely_reset, 0.U, prefetch_hit_cnt + prefetch_hit)
+  val total_prefetch = io.prefetch_info.loadinfo.map(t => t.total_prefetch && param.isMyType(t.pf_source)).reduce(_ || _)
+  val pf_late_in_cache = io.prefetch_info.loadinfo.map(t => t.pf_late_in_cache && param.isMyType(t.pf_source)).reduce(_ || _)
+  val nack_prefetch = io.prefetch_info.loadinfo.map(t => t.nack_prefetch && param.isMyType(t.pf_source)).reduce(_ || _)
+  val pf_late_in_mshr = io.prefetch_info.missinfo.pf_late_in_mshr && param.isMyType(io.prefetch_info.missinfo.pf_source)
+  val hit_pf_in_cache = PopCount(io.prefetch_info.loadinfo.map(t => t.hit_pf_in_cache && param.isMyType(t.hit_source)) ++ Seq(io.prefetch_info.maininfo.hit_pf_in_cache && param.isMyType(io.prefetch_info.maininfo.hit_pf_source_in_cache)))
+  val pf_useless = io.prefetch_info.maininfo.pf_useless && param.isMyType(io.prefetch_info.maininfo.pf_source_useless)
+  val prefetch_miss = io.prefetch_info.missinfo.prefetch_miss && param.isMyType(io.prefetch_info.missinfo.pf_source)
+  val hit_pf_in_mshr = io.prefetch_info.missinfo.hit_pf_in_mshr && param.isMyType(io.prefetch_info.missinfo.hit_pf_source_in_mshr)
+  val hit_pf = hit_pf_in_cache + hit_pf_in_mshr.asUInt
+  val pf_late = pf_late_in_cache.asUInt + pf_late_in_mshr.asUInt
 
-  val good_prefetch = io.timely.map(t => t.prefetch_hit && param.isMyType(t.hit_source)).reduce(_ || _)
-  val bad_prefetch = io.validity.valid && !io.validity.bits.access && param.isMyType(io.validity.bits.pf_source)
-  good_prefetch_cnt := Mux(validity_reset, 0.U, good_prefetch_cnt + good_prefetch)
-  bad_prefetch_cnt := Mux(validity_reset, 0.U, bad_prefetch_cnt + bad_prefetch)
+  total_prefetch_cnt := Mux(timely_reset, 0.U, total_prefetch_cnt + total_prefetch)
+  pf_late_in_cache_cnt := Mux(timely_reset, 0.U, pf_late_in_cache_cnt + pf_late_in_cache)
+  pf_late_in_mshr_cnt := Mux(timely_reset, 0.U, pf_late_in_mshr_cnt + pf_late_in_mshr)
+  hit_pf_in_cache_cnt := Mux(validity_reset, 0.U, hit_pf_in_cache_cnt + hit_pf_in_cache)
+  pf_useless_cnt := Mux(validity_reset, 0.U, pf_useless_cnt + pf_useless)
 
   back_off_cnt := Mux(back_off_reset, 0.U, back_off_cnt + !enable)
   low_conf_cnt := Mux(conf_reset, 0.U, low_conf_cnt + !confidence.asBool)
 
-  val trigger_late_hit = timely_reset && (late_hit_prefetch_cnt >= param.LATE_HIT_THRESHOLD.U)
-  val trigger_late_miss = timely_reset && (late_miss_prefetch_cnt >= param.LATE_MISS_THRESHOLD.U)
-  val trigger_bad_prefetch = validity_reset && (bad_prefetch_cnt >= param.BAD_THRESHOLD.U)
-  val trigger_disable = validity_reset && (bad_prefetch_cnt >= param.DISABLE_THRESHOLD.U)
+  val trigger_late_hit = timely_reset && (pf_late_in_cache_cnt >= param.LATE_HIT_THRESHOLD.U)
+  val trigger_late_miss = timely_reset && (pf_late_in_mshr_cnt >= param.LATE_MISS_THRESHOLD.U)
+  val trigger_pf_useless = validity_reset && (pf_useless_cnt >= param.BAD_THRESHOLD.U)
+  val trigger_disable = validity_reset && (pf_useless_cnt >= param.DISABLE_THRESHOLD.U)
 
   flush := Mux(flush, false.B, flush)
   enable := Mux(back_off_reset, true.B, enable)
   confidence := Mux(conf_reset, 1.U(1.W), confidence)
 
-  when(trigger_bad_prefetch) {
+  when(trigger_pf_useless) {
     depth := Mux(depth === 1.U, depth, depth >> 1)
   }
   when(trigger_disable) {
@@ -157,7 +236,7 @@ class L1PrefetchMonitor(param : PrefetcherMonitorParam)(implicit p: Parameters)
   }
 
   val enableDynamicPrefetcher_const = Constantin.createRecord(s"${param.name}_enableDynamicPrefetcher${p(XSCoreParamsKey).HartId}", initValue = 1)
-  val enableDynamicPrefetcher = enableDynamicPrefetcher_const === 1.U
+  val enableDynamicPrefetcher = (enableDynamicPrefetcher_const === 1.U)
 
   when(!enableDynamicPrefetcher) {
     depth := depth_const
@@ -174,11 +253,16 @@ class L1PrefetchMonitor(param : PrefetcherMonitorParam)(implicit p: Parameters)
     depth := depth_const
   }
 
-  XSPerfAccumulate(s"${param.name}_total_prefetch", total_prefetch)
-  XSPerfAccumulate(s"${param.name}_late_hit_prefetch", late_hit_prefetch)
-  XSPerfAccumulate(s"${param.name}_late_miss_prefetch", late_miss_prefetch)
-  XSPerfAccumulate(s"${param.name}_good_prefetch", good_prefetch)
-  XSPerfAccumulate(s"${param.name}_bad_prefetch", bad_prefetch)
+  XSPerfAccumulate(s"l1prefetchSent${param.name}", total_prefetch)
+  XSPerfAccumulate(s"l1prefetchHit${param.name}", hit_pf)
+  XSPerfAccumulate(s"l1prefetchHitInCache${param.name}", hit_pf_in_cache)
+  XSPerfAccumulate(s"l1prefetchHitInMSHR${param.name}", hit_pf_in_mshr)
+  XSPerfAccumulate(s"l1prefetchLate${param.name}", pf_late)
+  XSPerfAccumulate(s"l1prefetchLateInCache${param.name}", pf_late_in_cache)
+  XSPerfAccumulate(s"l1prefetchLateInMSHR${param.name}", pf_late_in_mshr)
+  XSPerfAccumulate(s"l1prefetchUseless${param.name}", pf_useless)
+  XSPerfAccumulate(s"l1prefetchDropByNack${param.name}", nack_prefetch)
+  XSPerfAccumulate(s"mshr_count_Prefetch${param.name}", prefetch_miss)
   for(i <- (0 until DEPTH_BITS)) {
     val t = (1 << i)
     XSPerfAccumulate(s"${param.name}_depth${t}", depth === t.U)
@@ -186,8 +270,7 @@ class L1PrefetchMonitor(param : PrefetcherMonitorParam)(implicit p: Parameters)
   XSPerfAccumulate(s"${param.name}_trigger_disable", trigger_disable)
   XSPerfAccumulate(s"${param.name}_trigger_late_hit", trigger_late_hit)
   XSPerfAccumulate(s"${param.name}_trigger_late_miss", trigger_late_miss)
-  XSPerfAccumulate(s"${param.name}_trigger_bad_prefetch", trigger_bad_prefetch)
-  // XSPerfAccumulate(s"${param.name}_prefetch_hit", prefetch_hit)
+  XSPerfAccumulate(s"${param.name}_trigger_pf_useless", trigger_pf_useless)
   XSPerfAccumulate(s"${param.name}_disable_time", !enable)
 
   assert(depth =/= 0.U, s"${param.name}_depth should not be zero")
```
