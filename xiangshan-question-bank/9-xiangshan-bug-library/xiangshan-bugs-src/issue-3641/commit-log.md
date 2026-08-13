# Commit Log
- Issue: #3641
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3641
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3641
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3641
- Changed files: 2
- Additions: 20
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/FTB.scala`
- `src/main/scala/xiangshan/frontend/FauFTB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/FTB.scala b/src/main/scala/xiangshan/frontend/FTB.scala
index 701417b2a3d..60910248f52 100644
--- a/src/main/scala/xiangshan/frontend/FTB.scala
+++ b/src/main/scala/xiangshan/frontend/FTB.scala
@@ -525,6 +525,17 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
     val multi_way = PriorityMux(Seq.tabulate(numWays)(i => ((total_hits_reg(i)) -> i.asUInt(log2Ceil(numWays).W))))
     val multi_hit_selectEntry = PriorityMux(Seq.tabulate(numWays)(i => ((total_hits_reg(i)) -> read_entries_reg(i))))
 
+    //Check if the entry read by ftbBank is legal.
+    for (n <- 0 to numWays -1 ) {
+      val req_pc_reg = RegEnable(io.req_pc.bits, 0.U.asTypeOf(io.req_pc.bits), io.req_pc.valid)
+      val req_pc_reg_lower = Cat(0.U(1.W), req_pc_reg(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
+      val ftbEntryEndLowerwithCarry = Cat(read_entries(n).carry, read_entries(n).pftAddr)
+      val fallThroughErr = req_pc_reg_lower + (PredictWidth).U >= ftbEntryEndLowerwithCarry
+      when(read_entries(n).valid && total_hits(n) && io.s1_fire){
+        assert(fallThroughErr, s"FTB read sram entry in way${n} fallThrough address error!")
+      }
+    }
+
     val u_total_hits = VecInit((0 until numWays).map(b =>
         ftb.io.r.resp.data(b).tag === u_req_tag && ftb.io.r.resp.data(b).entry.valid && RegNext(io.update_access)))
     val u_hit = u_total_hits.reduce(_||_)
@@ -630,7 +641,7 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
   val s2_multi_hit = ftbBank.io.read_multi_hits.valid && io.s2_fire(0)
   val s2_multi_hit_way = ftbBank.io.read_multi_hits.bits
   val s2_multi_hit_entry = ftbBank.io.read_multi_entry
-  val s2_multi_hit_enable = s2_multi_hit && io.s2_redirect(0)
+  val s2_multi_hit_enable = s2_multi_hit && !s2_close_ftb_req
   XSPerfAccumulate("ftb_s2_multi_hit", s2_multi_hit)
   XSPerfAccumulate("ftb_s2_multi_hit_enable", s2_multi_hit_enable)
 
diff --git a/src/main/scala/xiangshan/frontend/FauFTB.scala b/src/main/scala/xiangshan/frontend/FauFTB.scala
index ea600647f52..2079aeb4fe4 100644
--- a/src/main/scala/xiangshan/frontend/FauFTB.scala
+++ b/src/main/scala/xiangshan/frontend/FauFTB.scala
@@ -121,6 +121,14 @@ class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
   io.fauftb_entry_out := s1_hit_fauftbentry
   io.fauftb_entry_hit_out := s1_hit && fauftb_enable
 
+  // Illegal check for FTB entry reading
+  val s1_pc_startLower             = Cat(0.U(1.W), s1_pc_dup(0)(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
+  val uftb_entry_endLowerwithCarry = Cat(s1_hit_fauftbentry.carry, s1_hit_fauftbentry.pftAddr)
+  val fallThroughErr = s1_pc_startLower + (PredictWidth).U >= uftb_entry_endLowerwithCarry
+  when(io.s1_fire(0) && s1_hit){
+    assert(fallThroughErr, s"FauFTB read entry fallThrough address error!")
+  }
+
   // assign metas
   io.out.last_stage_meta := resp_meta.asUInt
   resp_meta.hit := RegEnable(RegEnable(s1_hit, io.s1_fire(0)), io.s2_fire(0))
```
