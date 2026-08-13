# Commit Log
- Issue: #5005
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5005
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5005
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5005
- Changed files: 2
- Additions: 7
- Deletions: 6

## Files
- `src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala`
- `src/main/scala/xiangshan/mem/prefetch/FDP.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
index c922a7759ae..09c7634cd83 100644
--- a/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
@@ -503,7 +503,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   val useless_prefetch = s2_miss_req_fire && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U)
   val useful_prefetch = s2_valid && (s2_req.instrtype === DCACHE_PREFETCH_SOURCE.U) && resp.bits.handled && !io.miss_resp.merged
 
-  val prefetch_hit = s2_valid && (s2_req.instrtype =/= DCACHE_PREFETCH_SOURCE.U) && s2_hit && isFromL1Prefetch(s2_hit_prefetch) && s2_req.isFirstIssue
+  val prefetch_hit = Wire(Bool()) // assigned in s3 for filtering
 
   io.prefetch_info.naive.total_prefetch := total_prefetch
   io.prefetch_info.naive.late_hit_prefetch := late_hit_prefetch
@@ -592,6 +592,7 @@ class LoadPipe(id: Int)(implicit p: Parameters) extends DCacheModule with HasPer
   io.counter_filter_enq.bits.way := OHToUInt(s3_tag_match_way)
 
   io.prefetch_info.fdp.useful_prefetch := s3_clear_pf_flag_en && !io.counter_filter_query.resp
+  prefetch_hit := s3_clear_pf_flag_en && !io.counter_filter_query.resp
 
   XSPerfAccumulate("s3_pf_hit", s3_clear_pf_flag_en)
   XSPerfAccumulate("s3_pf_hit_filter", s3_clear_pf_flag_en && !io.counter_filter_query.resp)
diff --git a/src/main/scala/xiangshan/mem/prefetch/FDP.scala b/src/main/scala/xiangshan/mem/prefetch/FDP.scala
index 5cbabec1c9d..6cad0b035b2 100644
--- a/src/main/scala/xiangshan/mem/prefetch/FDP.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/FDP.scala
@@ -53,9 +53,9 @@ class CounterFilterQueryBundle(implicit p: Parameters) extends DCacheBundle {
 }
 
 // no Set Blocking in LoadPipe, so when counting useful prefetch counter, duplicate result occurs
-// s0    s1     s2     s3
-// r                   w
-// if 3 load insts is accessing the same cache line(set0, way0) in s0, s1, s2
+// s0    s1     s2     s3     s4(in PrefetchArray, write next cycle of wreq)
+// r                   wreq   w
+// if 3 load insts is accessing the same cache line(set0, way0) in s0, s1, s2, s3
 // they think they all prefetch hit, increment useful prefetch counter 3 times
 // so when load arrives at s3, save it's set&way to an FIFO, all loads will search this FIFO to avoid this case
 class CounterFilter()(implicit p: Parameters) extends DCacheModule {
@@ -120,7 +120,7 @@ class CounterFilter()(implicit p: Parameters) extends DCacheModule {
       x := x + allocNum
     }
   }
-  last3CycleAlloc := RegNext(RegNext(allocNum))
+  last3CycleAlloc := RegNext(RegNext(RegNext(allocNum)))
 
   // deq
   for(i <- (0 until deqLen)) {
@@ -295,7 +295,7 @@ class FDPrefetcherMonitor()(implicit p: Parameters) extends XSModule {
   )
 
   XSPerfAccumulate("io_refill", io.refill)
-  XSPerfAccumulate("total_prefetch_en", io.accuracy.total_prefetch)
+  XSPerfAccumulate("total_prefetch_new_data_en", io.accuracy.total_prefetch)
   XSPerfAccumulate("useful_prefetch_en", PopCount(io.accuracy.useful_prefetch) + io.timely.late_prefetch)
   XSPerfAccumulate("late_prefetch_en", io.timely.late_prefetch)
   XSPerfAccumulate("demand_miss_en", PopCount(io.pollution.demand_miss))
```
