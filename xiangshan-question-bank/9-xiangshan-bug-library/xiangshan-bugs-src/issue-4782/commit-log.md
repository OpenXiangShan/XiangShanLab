# Commit Log
- Issue: #4782
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4782
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4782
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4782
- Changed files: 2
- Additions: 10
- Deletions: 12

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 30de764a2cc..df9e9123510 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -36,7 +36,6 @@ class MainPipeReq(implicit p: Parameters) extends DCacheBundle {
   val miss_dirty = Bool()
   val occupy_way = UInt(nWays.W)
   val miss_fail_cause_evict_btot = Bool()
-  val miss_tag_error = Bool()
 
   val probe = Bool()
   val probe_param = UInt(TLPermissions.bdWidth.W)
@@ -350,8 +349,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s1_tag_eq_way = wayMap((w: Int) => tag_resp(w) === get_tag(s1_req.addr)).asUInt
   val s1_tag_ecc_eq_way = wayMap((w: Int) => s1_tag_eq_way(w) && !s1_tag_errors(w)).asUInt
   val s1_tag_ecc_match_way = wayMap((w: Int) => s1_tag_ecc_eq_way(w) && s1_meta_valids(w)).asUInt
-  val s1_refill_with_tag_error = s1_req.miss && s1_req.miss_tag_error
-  val s1_tag_match = ParallelORR(s1_tag_ecc_match_way) && !s1_refill_with_tag_error
+  val s1_tag_match = ParallelORR(s1_tag_ecc_match_way)
   val s1_real_tag_eq_way = wayMap((w: Int) => io.tag_resp(w)(tagBits - 1, 0) === get_tag(s1_req.addr) && s1_meta_valids(w)).asUInt
   val s1_has_real_tag_eq_way = ParallelORR(s1_real_tag_eq_way)
   val s1_real_tag_match_way = PriorityEncoderOH(s1_real_tag_eq_way)
@@ -407,9 +405,8 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   // s2: select data, return resp if this is a store miss
   val s2_valid = RegInit(false.B)
   val s2_req = RegEnable(s1_req, s1_fire)
-  val s2_refill_with_tag_error = s2_req.miss && s2_req.miss_tag_error
   val s2_tag_errors = RegEnable(s1_tag_errors, s1_fire)
-  val s2_tag_match = RegEnable(s1_tag_match, s1_fire) && !s2_refill_with_tag_error
+  val s2_tag_match = RegEnable(s1_tag_match, s1_fire)
   val s2_has_real_tag_eq_way = RegEnable(s1_has_real_tag_eq_way, s1_fire)
   val s2_tag_ecc_match_way = RegEnable(s1_tag_ecc_match_way, s1_fire)
   val s2_hit_coh = RegEnable(s1_hit_coh, s1_fire)
@@ -442,7 +439,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   val s2_may_report_data_error = s2_need_data && s2_coh.state =/= ClientStates.Nothing
 
-  val s2_hit = (s2_tag_match || s2_refill_tag_eq_way) && s2_has_permission && !s2_refill_with_tag_error
+  val s2_hit = (s2_tag_match || s2_refill_tag_eq_way) && s2_has_permission
   val s2_sc = s2_req.cmd === M_XSC
   val s2_lr = s2_req.cmd === M_XLR
   val s2_amo_hit = s2_hit && !s2_req.probe && !s2_req.miss && s2_req.isAMO
@@ -474,7 +471,13 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s2_can_go_to_mq_evict_fail = s2_replace_block // dcache and miss queue both occupy the same set, (BtoT scheme)
   val s2_can_go_to_mq_replay = s2_can_go_to_mq_no_data || s2_can_go_to_mq_evict_fail
   val s2_can_go_to_mq = RegEnable(s1_pregen_can_go_to_mq, s1_fire)
-  val s2_can_go_to_s3 = (s2_sc || s2_req.replace || s2_req.probe || (s2_req.miss && io.refill_info.valid && !s2_replace_block) || (s2_req.isStore || s2_req.isAMO) && s2_hit) && s3_ready
+  val s2_can_go_to_s3 = (s2_sc || s2_req.replace || s2_req.probe ||
+    Mux(
+      s2_req.miss,
+      io.refill_info.valid && !s2_replace_block,
+      (s2_req.isStore || s2_req.isAMO) && s2_hit
+    )
+  ) && s3_ready
   assert(RegNext(!(s2_valid && s2_can_go_to_s3 && s2_can_go_to_mq && s2_can_go_to_mq_replay)))
   val s2_can_go = s2_can_go_to_s3 || s2_can_go_to_mq || s2_can_go_to_mq_replay
   val s2_fire = s2_valid && s2_can_go
@@ -838,7 +841,6 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   miss_req.full_overwrite := s2_req.isStore && s2_req.store_mask.andR
   miss_req.isBtoT := s2_grow_perm
   miss_req.occupy_way := s2_tag_ecc_match_way
-  miss_req.tag_error := s2_tag_error
 
   io.wbq_conflict_check.valid := s2_valid && s2_can_go_to_mq
   io.wbq_conflict_check.bits := s2_req.addr
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
index 8b8ecb14e20..f4a8be78baf 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -86,9 +86,6 @@ class MissReqWoStoreData(implicit p: Parameters) extends DCacheBundle {
   // 2. pmp check failed
   val cancel = Bool() // cancel is slow to generate, it will cancel missreq.valid
 
-  // MissReq caused by tag ecc error
-  val tag_error = Bool()
-
   // Req source decode
   // Note that req source is NOT cmd type
   // For instance, a req which isFromPrefetch may have R or W cmd
@@ -874,7 +871,6 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
   io.main_pipe_req.bits.access := access
   io.main_pipe_req.bits.occupy_way := req.occupy_way
   io.main_pipe_req.bits.miss_fail_cause_evict_btot := evict_BtoT_way
-  io.main_pipe_req.bits.miss_tag_error := req.tag_error
 
   io.block_addr.valid := req_valid && w_grantlast
   io.block_addr.bits := req.addr
```
