# Commit Log
- Issue: #4090
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4090
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4090
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4090
- Changed files: 1
- Additions: 28
- Deletions: 7

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 766dd4a0fd1..b3a07dd9679 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -164,7 +164,12 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   mem_addr := Mux(af_level === 3.U, l3addr, Mux(af_level === 2.U, l2addr, l1addr))
 
   val hptw_resp = Reg(new HptwResp)
-  val full_gvpn = Reg(UInt(ptePPNLen.W))
+
+  val update_full_gvpn_mem_resp = RegInit(false.B)
+  val full_gvpn_reg = Reg(UInt(ptePPNLen.W))
+  val full_gvpn_wire = pte.getPPN()
+  val full_gvpn = Mux(update_full_gvpn_mem_resp, full_gvpn_wire, full_gvpn_reg)
+
   val gpaddr = MuxCase(mem_addr, Seq(
     (stage1Hit || onlyS2xlate) -> Cat(full_gvpn, 0.U(offLen.W)),
     !s_last_hptw_req -> Cat(MuxLookup(level, pte.getPPN())(Seq(
@@ -174,7 +179,18 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     ))),
     0.U(offLen.W))
   ))
-  val gvpn_gpf = !(hptw_pageFault || hptw_accessFault ) && Mux(s2xlate && io.csr.hgatp.mode === Sv39x4, full_gvpn(ptePPNLen - 1, GPAddrBitsSv39x4 - offLen) =/= 0.U, Mux(s2xlate && io.csr.hgatp.mode === Sv48x4, full_gvpn(ptePPNLen - 1, GPAddrBitsSv48x4 - offLen) =/= 0.U, false.B))
+  val gvpn_gpf =
+    !(hptw_pageFault || hptw_accessFault ) &&
+    Mux(
+      s2xlate && io.csr.hgatp.mode === Sv39x4,
+      full_gvpn(ptePPNLen - 1, GPAddrBitsSv39x4 - offLen) =/= 0.U,
+      Mux(
+        s2xlate && io.csr.hgatp.mode === Sv48x4,
+        full_gvpn(ptePPNLen - 1, GPAddrBitsSv48x4 - offLen) =/= 0.U,
+        false.B
+      )
+    )
+
   val guestFault = hptw_pageFault || hptw_accessFault || gvpn_gpf
   val hpaddr = Cat(hptw_resp.genPPNS2(get_pn(gpaddr)), get_off(gpaddr))
   val fake_h_resp = WireInit(0.U.asTypeOf(new HptwResp))
@@ -234,7 +250,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     need_last_s2xlate := false.B
     hptw_pageFault := false.B
     hptw_accessFault := false.B
-    full_gvpn := io.req.bits.stage1.genPPN()
+    full_gvpn_reg := io.req.bits.stage1.genPPN()
   }
 
   when (io.resp.fire && stage1Hit){
@@ -277,7 +293,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     pte_valid := false.B
     req_s2xlate := io.req.bits.req_info.s2xlate
     when(io.req.bits.req_info.s2xlate === onlyStage2){
-      full_gvpn := io.req.bits.req_info.vpn
+      full_gvpn_reg := io.req.bits.req_info.vpn
       val onlys2_gpaddr = Cat(io.req.bits.req_info.vpn, 0.U(offLen.W)) // is 50 bits, don't need to check high bits when sv48x4 is enabled
       val check_gpa_high_fail = Mux(io.req.bits.req_info.s2xlate === onlyStage2 && io.csr.hgatp.mode === Sv39x4, onlys2_gpaddr(onlys2_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, false.B)
       need_last_s2xlate := false.B
@@ -287,7 +303,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         s_last_hptw_req := false.B
       }
     }.elsewhen(io.req.bits.req_info.s2xlate === allStage){
-      full_gvpn := 0.U
+      full_gvpn_reg := 0.U
       val allstage_gpaddr = Cat(gvpn_wire, 0.U(offLen.W))
       val check_gpa_high_fail = Mux(io.csr.hgatp.mode === Sv39x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(io.csr.hgatp.mode === Sv48x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
       when(check_gpa_high_fail){
@@ -297,7 +313,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         s_hptw_req := false.B
       }
     }.otherwise {
-      full_gvpn := 0.U
+      full_gvpn_reg := 0.U
       need_last_s2xlate := false.B
       s_pmp_check := false.B
     }
@@ -385,7 +401,12 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     mem_addr_update := true.B
     gpf_level := Mux(mode === Sv39 && !pte_valid && !(l3Hit || l2Hit), gpf_level - 2.U, gpf_level - 1.U)
     pte_valid := true.B
-    full_gvpn := pte.getPPN()
+    update_full_gvpn_mem_resp := true.B
+  }
+
+  when(update_full_gvpn_mem_resp) {
+    update_full_gvpn_mem_resp := false.B
+    full_gvpn_reg := pte.getPPN()
   }
 
   when(mem_addr_update){
```
