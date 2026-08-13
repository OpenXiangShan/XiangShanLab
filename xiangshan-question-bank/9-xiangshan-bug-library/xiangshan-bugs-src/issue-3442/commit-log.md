# Commit Log
- Issue: #3442
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3442
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3442
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3442
- Changed files: 3
- Additions: 50
- Deletions: 12

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 0a54aba097c..ca128f05af8 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -1091,6 +1091,14 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
     )
   }
 
+  def isLeaf() = {
+    (entry.perm.get.r || entry.perm.get.x || entry.perm.get.w) && entry.v
+  }
+
+  def isFakePte() = {
+    !pf && !entry.v
+  }
+
   def hit(vpn: UInt, asid: UInt, vmid: UInt, allType: Boolean = false, ignoreAsid: Boolean = false, s2xlate: Bool): Bool = {
     require(vpn.getWidth == vpnLen)
     //    require(this.asid.getWidth <= asid.getWidth)
@@ -1148,7 +1156,7 @@ class PtwMergeResp(implicit p: Parameters) extends PtwBundle {
     ptw_resp.tag := vpn(vpnLen - 1, sectortlbwidth)
     ptw_resp.pf := pf
     ptw_resp.af := af
-    ptw_resp.v := !pf
+    ptw_resp.v := resp_pte.perm.v
     ptw_resp.prefetch := DontCare
     ptw_resp.asid := asid
     ptw_resp.vmid.map(_ := vmid)
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 424f6032303..dc6e5da6373 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -182,7 +182,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
 
   val pte_valid = RegInit(false.B)  // avoid l1tlb pf from stage1 when gpf happens in the first s2xlate in PTW
   val fake_pte = 0.U.asTypeOf(new PteBundle())
-  fake_pte.perm.v := true.B
+  fake_pte.perm.v := false.B // tell L1TLB this is fake pte
   fake_pte.perm.r := true.B
   fake_pte.perm.w := true.B
   fake_pte.perm.x := true.B
@@ -255,20 +255,20 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
       when (mode === Sv48) {
         level := Mux(req.l2Hit, 1.U, Mux(req.l3Hit.get, 2.U, 3.U))
         af_level := Mux(req.l2Hit, 1.U, Mux(req.l3Hit.get, 2.U, 3.U))
-        gpf_level := Mux(req.l2Hit, 2.U, Mux(req.l3Hit.get, 3.U, 3.U))
+        gpf_level := Mux(req.l2Hit, 2.U, Mux(req.l3Hit.get, 3.U, 0.U))
         ppn := Mux(req.l2Hit || req.l3Hit.get, io.req.bits.ppn, satp.ppn)
         l3Hit := req.l3Hit.get
       } .otherwise {
         level := Mux(req.l2Hit, 1.U, 2.U)
         af_level := Mux(req.l2Hit, 1.U, 2.U)
-        gpf_level := 2.U
+        gpf_level := 0.U
         ppn := Mux(req.l2Hit, io.req.bits.ppn, satp.ppn)
         l3Hit := false.B
       }
     } else {
       level := Mux(req.l2Hit, 1.U, 2.U)
       af_level := Mux(req.l2Hit, 1.U, 2.U)
-      gpf_level := 2.U
+      gpf_level := 0.U
       ppn := Mux(req.l2Hit, io.req.bits.ppn, satp.ppn)
       l3Hit := false.B
     }
@@ -362,7 +362,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     af_level := af_level - 1.U
     s_llptw_req := false.B
     mem_addr_update := true.B
-    gpf_level := Mux(!pte_valid && !(l3Hit || l2Hit), gpf_level, gpf_level - 1.U)
+    gpf_level := Mux(mode === Sv39 && !pte_valid && !(l3Hit || l2Hit), gpf_level - 2.U, gpf_level - 1.U)
     pte_valid := true.B
   }
 
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index d641613ef42..8b841cc44ed 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -96,8 +96,11 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   val need_gpa = RegInit(false.B)
   val need_gpa_robidx = Reg(new RobPtr)
   val need_gpa_vpn = Reg(UInt(vpnLen.W))
-  val need_gpa_gvpn = Reg(UInt(ptePPNLen.W))
+  val resp_gpa_gvpn = Reg(UInt(ptePPNLen.W))
   val resp_gpa_refill = RegInit(false.B)
+  val resp_s1_level = RegInit(0.U(log2Up(Level + 1).W))
+  val resp_s1_isLeaf = RegInit(false.B)
+  val resp_s1_isFakePte = RegInit(false.B)
   val hasGpf = Wire(Vec(Width, Bool()))
 
   val Sv39Enable = satp.mode === 8.U
@@ -162,7 +165,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   /************************  main body above | method/log/perf below ****************************/
   def TLBRead(i: Int) = {
     val (e_hit, e_ppn, e_perm, e_g_perm, e_s2xlate, e_pbmt, e_g_pbmt) = entries.io.r_resp_apply(i)
-    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
+    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
     val enable = portTranslateEnable(i)
     val isOnlys2xlate = req_out_s2xlate(i) === onlyStage2
     val need_gpa_vpn_hit = need_gpa_vpn === get_pn(req_out(i).vaddr)
@@ -178,7 +181,10 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       resp_gpa_refill := false.B
       need_gpa_robidx := req_out(i).debug.robIdx
     }.elsewhen (ptw.resp.fire && need_gpa && need_gpa_vpn === ptw.resp.bits.getVpn(need_gpa_vpn)) {
-      need_gpa_gvpn := ptw.resp.bits.s1.genPPN(need_gpa_vpn)
+      resp_gpa_gvpn := ptw.resp.bits.s1.genPPN(need_gpa_vpn)
+      resp_s1_level := ptw.resp.bits.s1.entry.level.get
+      resp_s1_isLeaf := ptw.resp.bits.s1.isLeaf()
+      resp_s1_isFakePte := ptw.resp.bits.s1.isFakePte()
       resp_gpa_refill := true.B
     }
 
@@ -202,6 +208,9 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
     val perm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new TlbPermBundle))))
     val gvpn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(vpnLen.W))))
+    val level = WireInit(VecInit(Seq.fill(nRespDups)(0.U(log2Up(Level + 1).W))))
+    val isLeaf = WireInit(VecInit(Seq.fill(nRespDups)(false.B)))
+    val isFakePte = WireInit(VecInit(Seq.fill(nRespDups)(false.B)))
     val g_pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
     val g_perm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new TlbPermBundle))))
     val r_s2xlate = WireInit(VecInit(Seq.fill(nRespDups)(0.U(2.W))))
@@ -209,12 +218,21 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       ppn(d) := Mux(p_hit, p_ppn, e_ppn(d))
       pbmt(d) := Mux(p_hit, p_pbmt, e_pbmt(d))
       perm(d) := Mux(p_hit, p_perm, e_perm(d))
-      gvpn(d) :=  Mux(hasGpf(i), Mux(p_hit, p_gvpn, need_gpa_gvpn), 0.U)
+      gvpn(d) :=  Mux(p_hit, p_gvpn, resp_gpa_gvpn)
+      level(d) := Mux(p_hit, p_s1_level, resp_s1_level)
+      isLeaf(d) := Mux(p_hit, p_s1_isLeaf, resp_s1_isLeaf)
+      isFakePte(d) := Mux(p_hit, p_s1_isFakePte, resp_s1_isFakePte)
       g_pbmt(d) := Mux(p_hit, p_g_pbmt, e_g_pbmt(d))
       g_perm(d) := Mux(p_hit, p_g_perm, e_g_perm(d))
       r_s2xlate(d) := Mux(p_hit, p_s2xlate, e_s2xlate(d))
       val paddr = Cat(ppn(d), get_off(req_out(i).vaddr))
-      val gpaddr = Cat(gvpn(d), get_off(req_out(i).vaddr))
+      val vpn_idx = Mux1H(Seq(
+        (isFakePte(d) && vsatp.mode === Sv39) -> 2.U,
+        (isFakePte(d) && vsatp.mode === Sv48) -> 3.U,
+        (!isFakePte(d)) -> (level(d) - 1.U),
+      ))
+      val gpaddr_offset = Mux(isLeaf(d), get_off(req_out(i).vaddr), Cat(getVpnn(get_pn(req_out(i).vaddr), vpn_idx),  0.U(log2Up(XLEN/8).W)))
+      val gpaddr = Cat(gvpn(d), gpaddr_offset)
       resp(i).bits.paddr(d) := Mux(enable, paddr, vaddr)
       resp(i).bits.gpaddr(d) := Mux(r_s2xlate(d) === onlyStage2, vaddr, gpaddr)
     }
@@ -226,6 +244,15 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     (hit, miss, pmp_paddr, perm, g_perm, pbmt, g_pbmt)
   }
 
+  def getVpnn(vpn: UInt, idx: UInt): UInt = {
+    MuxLookup(idx, 0.U)(Seq(
+      0.U -> vpn(vpnnLen - 1, 0),
+      1.U -> vpn(vpnnLen * 2 - 1, vpnnLen),
+      2.U -> vpn(vpnnLen * 3 - 1, vpnnLen * 2),
+      3.U -> vpn(vpnnLen * 4 - 1, vpnnLen * 3))
+    )
+  }
+
   def pmp_check(addr: UInt, size: UInt, cmd: UInt, idx: Int): Unit = {
     pmp(idx).valid := resp(idx).valid
     pmp(idx).bits.addr := addr
@@ -429,7 +456,10 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val p_g_pbmt = RegEnable(ptw.resp.bits.s2.entry.pbmt,io.ptw.resp.fire)
     val p_g_perm = RegEnable(hptwresp_to_tlbperm(ptw.resp.bits.s2), io.ptw.resp.fire)
     val p_s2xlate = RegEnable(ptw.resp.bits.s2xlate, io.ptw.resp.fire)
-    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate)
+    val p_s1_level = RegEnable(ptw.resp.bits.s1.entry.level.get, io.ptw.resp.fire)
+    val p_s1_isLeaf = RegEnable(ptw.resp.bits.s1.isLeaf(), io.ptw.resp.fire)
+    val p_s1_isFakePte = RegEnable(ptw.resp.bits.s1.isFakePte(), io.ptw.resp.fire)
+    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate, p_s1_level, p_s1_isLeaf, p_s1_isFakePte)
   }
 
   // assert
```
