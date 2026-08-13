# Commit Log
- Issue: #4983
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4983
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4983
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4983
- Changed files: 1
- Additions: 5
- Deletions: 4

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 3b89b54dfbb..e91acea3142 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -270,7 +270,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   /************************  main body above | method/log/perf below ****************************/
   def TLBRead(i: Int) = {
     val (e_hit, e_ppn, e_perm, e_g_perm, e_s2xlate, e_pbmt, e_g_pbmt) = entries.io.r_resp_apply(i)
-    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
+    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte, p_hit_fast) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
     val enable = portTranslateEnable(i)
     val isOnlys2xlate = req_out_s2xlate(i) === onlyStage2
     val need_gpa_vpn_hit = need_gpa_vpn === get_pn(req_out(i).vaddr)
@@ -283,7 +283,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       need_gpa := false.B
       resp_gpa_refill := false.B
       need_gpa_vpn := 0.U
-    }.elsewhen (req_out_v(i) && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate && hasGpf(i) && need_gpa === false.B && !io.requestor(i).req_kill && !isPrefetch && !currentRedirect && !lastCycleRedirect) {
+    }.elsewhen (req_out_v(i) && !p_hit_fast && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate && hasGpf(i) && need_gpa === false.B && !io.requestor(i).req_kill && !isPrefetch && !currentRedirect && !lastCycleRedirect) {
       need_gpa_wire := true.B
       need_gpa := true.B
       need_gpa_vpn := get_pn(req_out(i).vaddr)
@@ -634,7 +634,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val onlyS1 = s2xlate === onlyStage1
     val s2xlate_hit = s2xlate === ptw.resp.bits.s2xlate
     val resp_hit = ptw.resp.bits.hit(vpn, csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid, allType = true)
-    val p_hit = GatedValidRegNext(resp_hit && io.ptw.resp.fire && s2xlate_hit)
+    val p_hit_fast = resp_hit && io.ptw.resp.fire && s2xlate_hit    // valid in the same cycle as tlb_req and ptw_resp
+    val p_hit = GatedValidRegNext(p_hit_fast)                       // valid in the next cycle after tlb_req and ptw_resp
     val ppn_s1 = ptw.resp.bits.s1.genPPN(vpn)(ppnLen - 1, 0)
     val gvpn = Mux(onlyS2, vpn, ppn_s1)
     val ppn_s2 = ptw.resp.bits.s2.genPPNS2(gvpn)(ppnLen - 1, 0)
@@ -648,7 +649,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val p_s1_level = RegEnable(ptw.resp.bits.s1.entry.level.get, io.ptw.resp.fire)
     val p_s1_isLeaf = RegEnable(ptw.resp.bits.s1.isLeaf(), io.ptw.resp.fire)
     val p_s1_isFakePte = RegEnable(ptw.resp.bits.s1.isFakePte(), io.ptw.resp.fire)
-    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte)
+    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte, p_hit_fast)
   }
 
   // perf event
```
