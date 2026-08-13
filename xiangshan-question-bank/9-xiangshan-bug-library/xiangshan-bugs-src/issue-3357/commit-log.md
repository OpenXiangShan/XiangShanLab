# Commit Log
- Issue: #3357
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3357
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3357
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3357
- Changed files: 1
- Additions: 8
- Deletions: 9

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index fec19ca4477..44e55402ef9 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -102,8 +102,13 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val vpn = Reg(UInt(vpnLen.W)) // vpn or gvpn(onlyS2xlate)
   val levelNext = level + 1.U
   val l1Hit = Reg(Bool())
-  val pte = mem.resp.bits.asTypeOf(new PteBundle().cloneType)
-
+  val pte_valid = RegInit(false.B) // avoid the x states
+  val fake_pte = 0.U.asTypeOf(new PteBundle())
+  fake_pte.perm.v := true.B
+  fake_pte.perm.r := true.B
+  fake_pte.perm.w := true.B
+  fake_pte.perm.x := true.B
+  val pte = Mux(pte_valid, mem.resp.bits.asTypeOf(new PteBundle()), fake_pte)
   // s/w register
   val s_pmp_check = RegInit(true.B)
   val s_mem_req = RegInit(true.B)
@@ -153,14 +158,8 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val hpaddr = Cat(hptw_resp.genPPNS2(get_pn(gpaddr)), get_off(gpaddr))
 
   io.req.ready := idle
-  val pte_valid = RegInit(false.B) // avoid the x states
-  val fake_pte = 0.U.asTypeOf(pte)
-  fake_pte.perm.v := true.B
-  fake_pte.perm.r := true.B
-  fake_pte.perm.w := true.B
-  fake_pte.perm.x := true.B
   val ptw_resp = Wire(new PtwMergeResp)
-  ptw_resp.apply(pageFault && !accessFault && !ppn_af, accessFault || ppn_af, Mux(accessFault, af_level,level), Mux(pte_valid, pte, fake_pte), vpn, satp.asid, hgatp.asid, vpn(sectortlbwidth - 1, 0), not_super = false)
+  ptw_resp.apply(pageFault && !accessFault && !ppn_af, accessFault || ppn_af, Mux(accessFault, af_level,level), pte, vpn, satp.asid, hgatp.asid, vpn(sectortlbwidth - 1, 0), not_super = false)
 
   val normal_resp = idle === false.B && mem_addr_update && !last_s2xlate && (guest_fault || (w_mem_resp && find_pte) || (s_pmp_check && accessFault) || onlyS2xlate )
   val stageHit_resp = idle === false.B && hptw_resp_stage2
```
