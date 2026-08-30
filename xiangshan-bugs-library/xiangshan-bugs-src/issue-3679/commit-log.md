# Commit Log
- Issue: #3679
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3679
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3679
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3679
- Changed files: 3
- Additions: 11
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/GPAMem.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/GPAMem.scala b/src/main/scala/xiangshan/backend/GPAMem.scala
index 9b866e1081b..0cf5d4de6b5 100644
--- a/src/main/scala/xiangshan/backend/GPAMem.scala
+++ b/src/main/scala/xiangshan/backend/GPAMem.scala
@@ -41,7 +41,7 @@ class GPAMemImp(override val wrapper: GPAMem)(implicit p: Parameters) extends La
 }
 
 class GPAMemEntry(implicit val p: Parameters) extends Bundle with HasXSParameter {
-  val gpaddr = UInt(GPAddrBits.W)
+  val gpaddr = UInt(XLEN.W)
   val isForVSnonLeafPTE = Bool()
 }
 
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index fd01ec7dc33..04741e576a5 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -164,17 +164,17 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   mem_addr := Mux(af_level === 3.U, l3addr, Mux(af_level === 2.U, l2addr, l1addr))
 
   val hptw_resp = Reg(new HptwResp)
+  val full_gvpn = Reg(UInt(ptePPNLen.W))
   val gpaddr = MuxCase(mem_addr, Seq(
-    stage1Hit -> Cat(stage1.genPPN(), 0.U(offLen.W)),
-    onlyS2xlate -> Cat(vpn, 0.U(offLen.W)),
-    !need_last_s2xlate -> Cat(MuxLookup(level, pte.getPPN())(Seq(
+    (stage1Hit || onlyS2xlate) -> Cat(full_gvpn, 0.U(offLen.W)),
+    !s_last_hptw_req -> Cat(MuxLookup(level, pte.getPPN())(Seq(
       3.U -> Cat(pte.getPPN()(ptePPNLen - 1, vpnnLen * 3), vpn(vpnnLen * 3 - 1, 0)),
       2.U -> Cat(pte.getPPN()(ptePPNLen - 1, vpnnLen * 2), vpn(vpnnLen * 2 - 1, 0)),
       1.U -> Cat(pte.getPPN()(ptePPNLen - 1, vpnnLen), vpn(vpnnLen - 1, 0)
     ))),
     0.U(offLen.W))
   ))
-  val gvpn_gpf = !(hptw_pageFault || hptw_accessFault ) && Mux(s2xlate && io.csr.hgatp.mode === Sv39x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(s2xlate && io.csr.hgatp.mode === Sv48x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
+  val gvpn_gpf = !(hptw_pageFault || hptw_accessFault ) && Mux(s2xlate && io.csr.hgatp.mode === Sv39x4, full_gvpn(ptePPNLen - 1, GPAddrBitsSv39x4 - offLen) =/= 0.U, Mux(s2xlate && io.csr.hgatp.mode === Sv48x4, full_gvpn(ptePPNLen - 1, GPAddrBitsSv48x4 - offLen) =/= 0.U, false.B))
   val guestFault = hptw_pageFault || hptw_accessFault || gvpn_gpf
   val hpaddr = Cat(hptw_resp.genPPNS2(get_pn(gpaddr)), get_off(gpaddr))
   val fake_h_resp = 0.U.asTypeOf(new HptwResp)
@@ -234,6 +234,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     need_last_s2xlate := false.B
     hptw_pageFault := false.B
     hptw_accessFault := false.B
+    full_gvpn := io.req.bits.stage1.genPPN()
   }
 
   when (io.resp.fire && stage1Hit){
@@ -276,6 +277,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     pte_valid := false.B
     req_s2xlate := io.req.bits.req_info.s2xlate
     when(io.req.bits.req_info.s2xlate === onlyStage2){
+      full_gvpn := io.req.bits.req_info.vpn
       val onlys2_gpaddr = Cat(io.req.bits.req_info.vpn, 0.U(offLen.W)) // is 50 bits, don't need to check high bits when sv48x4 is enabled
       val check_gpa_high_fail = Mux(io.req.bits.req_info.s2xlate === onlyStage2 && io.csr.hgatp.mode === Sv39x4, onlys2_gpaddr(onlys2_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, false.B)
       need_last_s2xlate := false.B
@@ -285,6 +287,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         s_last_hptw_req := false.B
       }
     }.elsewhen(io.req.bits.req_info.s2xlate === allStage){
+      full_gvpn := 0.U
       val allstage_gpaddr = Cat(gvpn_wire, 0.U(offLen.W))
       val check_gpa_high_fail = Mux(io.csr.hgatp.mode === Sv39x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(io.csr.hgatp.mode === Sv48x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
       when(check_gpa_high_fail){
@@ -294,6 +297,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         s_hptw_req := false.B
       }
     }.otherwise {
+      full_gvpn := 0.U
       need_last_s2xlate := false.B
       s_pmp_check := false.B
     }
@@ -381,6 +385,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     mem_addr_update := true.B
     gpf_level := Mux(mode === Sv39 && !pte_valid && !(l3Hit || l2Hit), gpf_level - 2.U, gpf_level - 1.U)
     pte_valid := true.B
+    full_gvpn := pte.getPPN()
   }
 
   when(mem_addr_update){
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 2ac53778cd1..9dd85e8cc9a 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -244,7 +244,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val ppn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ppnLen.W))))
     val pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
     val perm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new TlbPermBundle))))
-    val gvpn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(vpnLen.W))))
+    val gvpn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePPNLen.W))))
     val level = WireInit(VecInit(Seq.fill(nRespDups)(0.U(log2Up(Level + 1).W))))
     val isLeaf = WireInit(VecInit(Seq.fill(nRespDups)(false.B)))
     val isFakePte = WireInit(VecInit(Seq.fill(nRespDups)(false.B)))
```
