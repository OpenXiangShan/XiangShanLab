# Commit Log
- Issue: #3343
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3343
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3343
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3343
- Changed files: 1
- Additions: 5
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index b88a8b40fef..8d8dc075b14 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -228,7 +228,10 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   llptw.io.in.bits.ppn := cache.io.resp.bits.toFsm.ppn
   llptw.io.sfence := sfence_dup(1)
   llptw.io.csr := csr_dup(1)
-  val llptw_stage1 = RegEnable(cache.io.resp.bits.stage1, llptw.io.in.fire)
+  val llptw_stage1 = Reg(Vec(l2tlbParams.llptwsize, new PtwMergeResp()))
+  when(llptw.io.in.fire){
+    llptw_stage1(llptw.io.mem.enq_ptr) := cache.io.resp.bits.stage1
+  }
 
   cache.io.req.valid := arb2.io.out.valid
   cache.io.req.bits.req_info := arb2.io.out.bits.req_info
@@ -488,7 +491,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     mergeArb(i).in(outArbFsmPort).bits.s2 := ptw.io.resp.bits.h_resp
     mergeArb(i).in(outArbMqPort).valid := llptw_out.valid && llptw_out.bits.req_info.source===i.U
     mergeArb(i).in(outArbMqPort).bits.s2xlate := llptw_out.bits.req_info.s2xlate
-    mergeArb(i).in(outArbMqPort).bits.s1 := Mux(llptw_out.bits.first_s2xlate_fault, llptw_stage1, contiguous_pte_to_merge_ptwResp(resp_pte_sector(llptw_out.bits.id).asUInt, llptw_out.bits.req_info.vpn, llptw_out.bits.af, true, s2xlate = llptw_out.bits.req_info.s2xlate))
+    mergeArb(i).in(outArbMqPort).bits.s1 := Mux(llptw_out.bits.first_s2xlate_fault, llptw_stage1(llptw_out.bits.id), contiguous_pte_to_merge_ptwResp(resp_pte_sector(llptw_out.bits.id).asUInt, llptw_out.bits.req_info.vpn, llptw_out.bits.af, true, s2xlate = llptw_out.bits.req_info.s2xlate))
     mergeArb(i).in(outArbMqPort).bits.s2 := llptw_out.bits.h_resp
     mergeArb(i).out.ready := outArb(i).in(0).ready
   }
```
