# Commit Log
- Issue: #4916
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4916
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4916
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4916
- Changed files: 1
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index c2d3a0595d9..f56df6b6eea 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -254,9 +254,9 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val resp_pte = Mux(pte_valid, pte, fake_pte)
   ptw_resp.apply(resp_pf, resp_af, resp_level, resp_pte, vpn, satp.asid, hgatp.vmid, vpn(sectortlbwidth - 1, 0), not_super = false, not_merge = false, bitmap_checkfailed.asBool)
 
-  val normal_resp = idle === false.B && mem_addr_update && !need_last_s2xlate && (guestFault || (w_mem_resp && find_pte) || (s_pmp_check && accessFault) || onlyS2xlate )
-  val stageHit_resp = idle === false.B && hptw_resp_stage2
-  io.resp.valid := Mux(stage1Hit, stageHit_resp, normal_resp)
+  val normal_resp = mem_addr_update && !need_last_s2xlate && (guestFault || (w_mem_resp && find_pte) || (s_pmp_check && accessFault) || onlyS2xlate )
+  val stageHit_resp = hptw_resp_stage2
+  io.resp.valid := !idle && Mux(stage1Hit, stageHit_resp, normal_resp)
   io.resp.bits.source := source
   io.resp.bits.resp := Mux(stage1Hit || (l3Hit || l2Hit) && guestFault && !pte_valid, stage1, ptw_resp)
   io.resp.bits.h_resp := Mux(gvpn_gpf, fake_h_resp, hptw_resp)
```
