# Commit Log
- Issue: #5230
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5230
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5230
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5230
- Changed files: 1
- Additions: 10
- Deletions: 3

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 8eb8e92f478..0f0031777df 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -106,6 +106,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   )))
   val need_gpa = RegInit(false.B)
   val need_gpa_wire = WireInit(false.B)
+  val maybe_need_gpa_not_allow_refill = WireInit(false.B)
+  val need_clear_need_gpa = RegInit(false.B)
   val need_gpa_robidx = Reg(new RobPtr)
   val need_gpa_vpn = Reg(UInt(vpnLen.W))
   val resp_gpa_gvpn = Reg(UInt(ptePPNLen.W))
@@ -219,7 +221,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     }
   }
 
-  val refill = ptw.resp.fire && !(ptw.resp.bits.getGpa) && !need_gpa && !need_gpa_wire && !flush_mmu
+  val refill = ptw.resp.fire && !(ptw.resp.bits.getGpa) && !need_gpa && !maybe_need_gpa_not_allow_refill && !flush_mmu
   // prevent ptw refill when: 1) it's a getGpa request; 2) l1tlb is in need_gpa state; 3) mmu is being flushed.
 
   refill_to_mem := DontCare
@@ -278,16 +280,21 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val currentRedirect = req_out(i).debug.robIdx.needFlush(redirect)
     val lastCycleRedirect = req_out(i).debug.robIdx.needFlush(RegNext(redirect))
 
-    when (!isitlb && need_gpa_robidx.needFlush(redirect) || isitlb && flush_pipe(i)){
+    need_clear_need_gpa := false.B
+    when (!isitlb && need_gpa_robidx.needFlush(redirect) || isitlb && flush_pipe(i) || need_clear_need_gpa){
       need_gpa := false.B
       resp_gpa_refill := false.B
       need_gpa_vpn := 0.U
-    }.elsewhen (req_out_v(i) && !p_hit_fast && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate && hasGpf(i) && need_gpa === false.B && !io.requestor(i).req_kill && !isPrefetch && !currentRedirect && !lastCycleRedirect) {
+    }.elsewhen (req_out_v(i) && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate && hasGpf(i) && need_gpa === false.B && !io.requestor(i).req_kill && !isPrefetch && !currentRedirect && !lastCycleRedirect) {
+      maybe_need_gpa_not_allow_refill := true.B
       need_gpa_wire := true.B
       need_gpa := true.B
       need_gpa_vpn := get_pn(req_out(i).vaddr)
       resp_gpa_refill := false.B
       need_gpa_robidx := req_out(i).debug.robIdx
+      when (p_hit_fast) {
+        need_clear_need_gpa := true.B
+      }
     }.elsewhen (ptw.resp.fire && need_gpa && need_gpa_vpn === ptw.resp.bits.getVpn(need_gpa_vpn)) {
       resp_gpa_gvpn := Mux(ptw.resp.bits.s2xlate === onlyStage2, ptw.resp.bits.s2.entry.tag, ptw.resp.bits.s1.genGVPN(need_gpa_vpn))
       resp_s1_level := ptw.resp.bits.s1.entry.level.get
```
