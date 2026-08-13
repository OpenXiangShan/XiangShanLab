# Commit Log
- Issue: #3453
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3453
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3453
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3453
- Changed files: 4
- Additions: 140
- Deletions: 47

## Files
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index 2036e6e5cc3..df405a50d05 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -506,7 +506,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       llptw_out.bits.first_s2xlate_fault, llptw_stage1(llptw_out.bits.id),
       contiguous_pte_to_merge_ptwResp(
         resp_pte_sector(llptw_out.bits.id).asUInt, llptw_out.bits.req_info.vpn, llptw_out.bits.af, 
-        true, s2xlate = llptw_out.bits.req_info.s2xlate, mPBMTE, hPBMTE
+        true, s2xlate = llptw_out.bits.req_info.s2xlate, mPBMTE = mPBMTE, hPBMTE = hPBMTE, gpf = llptw_out.bits.h_resp.gpf
       )
     )
     mergeArb(i).in(outArbMqPort).bits.s2 := llptw_out.bits.h_resp
@@ -553,7 +553,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
 
   // not_super means that this is a normal page
   // valididx(i) will be all true when super page to be convenient for l1 tlb matching
-  def contiguous_pte_to_merge_ptwResp(pte: UInt, vpn: UInt, af: Bool, af_first: Boolean, s2xlate: UInt, mPBMTE: Bool, hPBMTE: Bool, not_super: Boolean = true) : PtwMergeResp = {
+  def contiguous_pte_to_merge_ptwResp(pte: UInt, vpn: UInt, af: Bool, af_first: Boolean, s2xlate: UInt, mPBMTE: Bool, hPBMTE: Bool, not_super: Boolean = true, gpf: Bool) : PtwMergeResp = {
     assert(tlbcontiguous == 8, "Only support tlbcontiguous = 8!")
     val ptw_merge_resp = Wire(new PtwMergeResp())
     val hasS2xlate = s2xlate =/= noS2xlate
@@ -568,15 +568,16 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       ptw_resp.perm.map(_ := pte_in.getPerm())
       ptw_resp.tag := vpn(vpnLen - 1, sectortlbwidth)
       ptw_resp.pf := (if (af_first) !af else true.B) && (pte_in.isPf(0.U, pbmte) || !pte_in.isLeaf())
-      ptw_resp.af := (if (!af_first) pte_in.isPf(0.U, pbmte) else true.B) && (af || Mux(s2xlate === allStage, false.B, pte_in.isAf()))
+      ptw_resp.af := (if (!af_first) pte_in.isPf(0.U, pbmte) else true.B) && (af || (Mux(s2xlate === allStage, false.B, pte_in.isAf()) && !(hasS2xlate && gpf)))
       ptw_resp.v := !ptw_resp.pf
       ptw_resp.prefetch := DontCare
       ptw_resp.asid := Mux(hasS2xlate, vsatp.asid, satp.asid)
-      ptw_resp.vmid.map(_ := hgatp.vmid) 
+      ptw_resp.vmid.map(_ := hgatp.vmid)
       ptw_merge_resp.entry(i) := ptw_resp
     }
     ptw_merge_resp.pteidx := UIntToOH(vpn(sectortlbwidth - 1, 0)).asBools
     ptw_merge_resp.not_super := not_super.B
+    ptw_merge_resp.not_merge := hasS2xlate
     ptw_merge_resp
   }
 
@@ -603,7 +604,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       val v_equal = pte.entry(i).v === pte.entry(OHToUInt(pte.pteidx)).v
       val af_equal = pte.entry(i).af === pte.entry(OHToUInt(pte.pteidx)).af
       val pf_equal = pte.entry(i).pf === pte.entry(OHToUInt(pte.pteidx)).pf
-      ptw_sector_resp.valididx(i) := (ppn_equal && pbmt_equal && perm_equal && v_equal && af_equal && pf_equal) || !pte.not_super
+      ptw_sector_resp.valididx(i) := ((ppn_equal && pbmt_equal && perm_equal && v_equal && af_equal && pf_equal) || !pte.not_super) && !pte.not_merge
       ptw_sector_resp.ppn_low(i) := pte.entry(i).ppn_low
     }
     ptw_sector_resp.valididx(OHToUInt(pte.pteidx)) := true.B
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index ddc23295943..018f3cbc229 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -890,12 +890,12 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, ReservedBi
   val reservedBits = if(ReservedBits > 0) Some(UInt(ReservedBits.W)) else None
   // println(s"PtwEntries: tag:1*${tagLen} ppns:${num}*${ppnLen} vs:${num}*1")
   // NOTE: vs is used for different usage:
-  // for l3, which store the leaf(leaves), vs is page fault or not.
-  // for l2, which shoule not store leaf, vs is valid or not, that will anticipate in hit check
-  // Because, l2 should not store leaf(no perm), it doesn't store perm.
-  // If l2 hit a leaf, the perm is still unavailble. Should still page walk. Complex but nothing helpful.
+  // for l0, which store the leaf(leaves), vs is page fault or not.
+  // for l1, which shoule not store leaf, vs is valid or not, that will anticipate in hit check
+  // Because, l1 should not store leaf(no perm), it doesn't store perm.
+  // If l1 hit a leaf, the perm is still unavailble. Should still page walk. Complex but nothing helpful.
   // TODO: divide vs into validVec and pfVec
-  // for l2: may valid but pf, so no need for page walk, return random pte with pf.
+  // for l1: may valid but pf, so no need for page walk, return random pte with pf.
 
   def tagClip(vpn: UInt) = {
     require(vpn.getWidth == vpnLen)
@@ -926,7 +926,7 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, ReservedBi
       val pte = data((i+1)*XLEN-1, i*XLEN).asTypeOf(new PteBundle)
       ps.pbmts(i) := pte.pbmt
       ps.ppns(i) := pte.ppn
-      ps.vs(i)   := !pte.isPf(levelUInt, pbmte) && (if (hasPerm) pte.isLeaf() else !pte.isLeaf())
+      ps.vs(i)   := Mux(s2xlate === onlyStage2, !pte.isGpf(levelUInt, pbmte), !pte.isPf(levelUInt, pbmte)) && (if (hasPerm) pte.isLeaf() else !pte.isLeaf())
       ps.af(i)   := Mux(s2xlate === allStage, false.B, pte.isAf()) // if allstage, this refill is from ptw or llptw, so the af is invalid
       ps.perms.map(_(i) := pte.perm)
     }
@@ -1156,8 +1156,9 @@ class PtwMergeResp(implicit p: Parameters) extends PtwBundle {
   val entry = Vec(tlbcontiguous, new PtwMergeEntry(tagLen = sectorvpnLen, hasPerm = true, hasLevel = true))
   val pteidx = Vec(tlbcontiguous, Bool())
   val not_super = Bool()
+  val not_merge = Bool()
 
-  def apply(pf: Bool, af: Bool, level: UInt, pte: PteBundle, vpn: UInt, asid: UInt, vmid:UInt, addr_low : UInt, not_super : Boolean = true) = {
+  def apply(pf: Bool, af: Bool, level: UInt, pte: PteBundle, vpn: UInt, asid: UInt, vmid:UInt, addr_low : UInt, not_super : Boolean = true, not_merge: Boolean = false) = {
     assert(tlbcontiguous == 8, "Only support tlbcontiguous = 8!")
     val resp_pte = pte
     val ptw_resp = Wire(new PtwMergeEntry(tagLen = sectorvpnLen, hasPerm = true, hasLevel = true))
@@ -1175,7 +1176,7 @@ class PtwMergeResp(implicit p: Parameters) extends PtwBundle {
     ptw_resp.vmid.map(_ := vmid)
     this.pteidx := UIntToOH(addr_low).asBools
     this.not_super := not_super.B
-
+    this.not_merge := not_merge.B
 
     for (i <- 0 until tlbcontiguous) {
       this.entry(i) := ptw_resp
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 8da0ce3713b..2800d50146c 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -653,6 +653,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
   io.resp.bits.stage1.pteidx := UIntToOH(idx).asBools
   io.resp.bits.stage1.not_super := Mux(resp_res.l0.hit, true.B, false.B)
+  io.resp.bits.stage1.not_merge := false.B
   io.resp.valid := stageResp.valid
   XSError(stageResp.valid && resp_res.l0.hit && resp_res.sp.hit, "normal page and super page both hit")
   XSError(stageResp.valid && io.resp.bits.hit && bypassed(0), "page cache, bypassed but hit")
@@ -685,12 +686,29 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
   // TODO: handle sfenceLatch outsize
   if (EnableSv48) {
+    // L3 refill
+    val l3GoodToRefill = WireInit(false.B)
+    switch (refill.req_info_dup(2).s2xlate) {
+      is (allStage) {
+        l3GoodToRefill := !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)
+      }
+      is (onlyStage1) {
+        l3GoodToRefill := !memPte(2).isAf()
+      }
+      is (onlyStage2) {
+        l3GoodToRefill := !memPte(2).isAf() && !memPte(2).isGpf(refill.level_dup(2), mPBMTE)
+      }
+      is (noS2xlate) {
+        l3GoodToRefill := !memPte(2).isAf()
+      }
+    }
+
     when (
-      !flush_dup(2) && refill.levelOH.l3.get && !memPte(2).isLeaf() && !memPte(2).isPf(refill.level_dup(2), pbmte) &&
-      Mux(refill.req_info_dup(2).s2xlate === allStage, !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode),
-      Mux(refill.req_info_dup(2).s2xlate === onlyStage1, !(memPte(2).isAf() || memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)),
-      Mux(refill.req_info_dup(2).s2xlate === onlyStage2, !memPte(2).isGpf(refill.level_dup(2), mPBMTE),
-      !memPte(2).isAf())))
+      !flush_dup(2) && 
+      refill.levelOH.l3.get && 
+      !memPte(2).isLeaf() && 
+      !memPte(2).isPf(refill.level_dup(2), pbmte) && 
+      l3GoodToRefill
     ) {
       val refillIdx = replaceWrapper(l3v.get, ptwl3replace.get.way)
       refillIdx.suggestName(s"Ptwl3RefillIdx")
@@ -720,12 +738,28 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     }
   }
 
+  // L2 refill
+  val l2GoodToRefill = WireInit(false.B)
+  switch (refill.req_info_dup(2).s2xlate) {
+    is (allStage) {
+      l2GoodToRefill := !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)
+    }
+    is (onlyStage1) {
+      l2GoodToRefill := !memPte(2).isAf()
+    }
+    is (onlyStage2) {
+      l2GoodToRefill := !memPte(2).isAf() && !memPte(2).isGpf(refill.level_dup(2), mPBMTE)
+    }
+    is (noS2xlate) {
+      l2GoodToRefill := !memPte(2).isAf()
+    }
+  }
   when (
-    !flush_dup(2) && refill.levelOH.l2 && !memPte(2).isLeaf() && !memPte(2).isPf(refill.level_dup(2), pbmte) &&
-    Mux(refill.req_info_dup(2).s2xlate === allStage, !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode),
-    Mux(refill.req_info_dup(2).s2xlate === onlyStage1, !(memPte(2).isAf() || memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)),
-    Mux(refill.req_info_dup(2).s2xlate === onlyStage2, !memPte(2).isGpf(refill.level_dup(2), mPBMTE),
-    !memPte(2).isAf())))
+    !flush_dup(2) && 
+    refill.levelOH.l2 && 
+    !memPte(2).isLeaf() && 
+    !memPte(2).isPf(refill.level_dup(2), pbmte) && 
+    l2GoodToRefill
   ) {
     val refillIdx = replaceWrapper(l2v, ptwl2replace.way)
     refillIdx.suggestName(s"Ptwl2RefillIdx")
@@ -754,12 +788,32 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     rfOH.suggestName(s"l2_rfOH")
   }
 
+  // L1 refill
+  val l1GoodToRefill = WireInit(false.B)
+  switch (refill.req_info_dup(1).s2xlate) {
+    is (allStage) {
+      // l1GoodToRefill := !memPte(1).isStage1Gpf(io.csr_dup(1).vsatp.mode)
+      l1GoodToRefill := !Cat(memPtes.map(_.isStage1Gpf(io.csr_dup(1).vsatp.mode))).orR
+    }
+    is (onlyStage1) {
+      // l1GoodToRefill := !memPte(1).isAf()
+      l1GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
+    }
+    is (onlyStage2) {
+      // l1GoodToRefill := !memPte(1).isGpf(refill.level_dup(1))
+      // l1GoodToRefill := !Cat(memPtes.map(_.isGpf(refill.level_dup(1)))).orR
+      l1GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
+    }
+    is (noS2xlate) {
+      // l1GoodToRefill := !memPte(1).isAf()
+      l1GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
+    }
+  }
   when (
-    !flush_dup(1) && refill.levelOH.l1 && !memPte(1).isLeaf() && !memPte(1).isPf(refill.level_dup(1), pbmte) &&
-    Mux(refill.req_info_dup(1).s2xlate === allStage, !memPte(1).isStage1Gpf(io.csr_dup(1).vsatp.mode),
-    Mux(refill.req_info_dup(1).s2xlate === onlyStage1, !(memPte(1).isAf() || memPte(1).isStage1Gpf(io.csr_dup(1).vsatp.mode)),
-    Mux(refill.req_info_dup(1).s2xlate === onlyStage2, !memPte(1).isGpf(refill.level_dup(1), mPBMTE),
-    !memPte(1).isAf())))
+    !flush_dup(1) && refill.levelOH.l1 && 
+    !memPte(1).isLeaf() && 
+    !memPte(1).isPf(refill.level_dup(1), pbmte) && 
+    l1GoodToRefill
   ) {
     val refillIdx = genPtwL1SetIdx(refill.req_info_dup(1).vpn)
     val victimWay = replaceWrapper(getl1vSet(refill.req_info_dup(1).vpn), ptwl1replace.way(refillIdx))
@@ -802,13 +856,29 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     rfvOH.suggestName(s"l1_rfvOH")
   }
 
-  when (
-    !flush_dup(0) && refill.levelOH.l0 &&
-    Mux(refill.req_info_dup(0).s2xlate === allStage, !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode),
-    Mux(refill.req_info_dup(0).s2xlate === onlyStage1, !(memPte(0).isAf() || memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)),
-    Mux(refill.req_info_dup(0).s2xlate === onlyStage2, !memPte(0).isGpf(refill.level_dup(0), mPBMTE),
-    !memPte(0).isAf())))
-  ) {
+  // L0 refill
+  val l0GoodToRefill = WireInit(false.B)
+  switch (refill.req_info_dup(0).s2xlate) {
+    is (allStage) {
+      // l0GoodToRefill := !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)
+      l0GoodToRefill := !Cat(memPtes.map(_.isStage1Gpf(io.csr_dup(0).vsatp.mode))).orR
+    }
+    is (onlyStage1) {
+      // l0GoodToRefill := !memPte(0).isAf()
+      l0GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
+    }
+    is (onlyStage2) {
+      // l0GoodToRefill := !memPte(0).isGpf(refill.level_dup(0))
+      // l0GoodToRefill := !Cat(memPtes.map(_.isGpf(refill.level_dup(0)))).orR
+      l0GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
+    }
+    is (noS2xlate) {
+      // l0GoodToRefill := !memPte(0).isAf()
+      l0GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
+    }
+  }
+
+  when (!flush_dup(0) && refill.levelOH.l0 && l0GoodToRefill) {
     val refillIdx = genPtwL0SetIdx(refill.req_info_dup(0).vpn)
     val victimWay = replaceWrapper(getl0vSet(refill.req_info_dup(0).vpn), ptwl0replace.way(refillIdx))
     val victimWayOH = UIntToOH(victimWay)
@@ -852,12 +922,27 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
 
   // misc entries: super & invalid
+  val spGoodToRefill = WireInit(false.B)
+  switch (refill.req_info_dup(0).s2xlate) {
+    is (allStage) {
+      spGoodToRefill := !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)
+    }
+    is (onlyStage1) {
+      spGoodToRefill := !memPte(0).isAf()
+    }
+    is (onlyStage2) {
+      spGoodToRefill := !memPte(0).isGpf(refill.level_dup(0), mPBMTE)
+    }
+    is (noS2xlate) {
+      spGoodToRefill := !memPte(0).isAf()
+    }
+  }
+
   when (
-    !flush_dup(0) && refill.levelOH.sp && (memPte(0).isLeaf() || memPte(0).isPf(refill.level_dup(0), pbmte) &&
-    Mux(refill.req_info_dup(0).s2xlate === allStage, !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode),
-    Mux(refill.req_info_dup(0).s2xlate === onlyStage1, !(memPte(0).isAf() || memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)),
-    Mux(refill.req_info_dup(0).s2xlate === onlyStage2, !memPte(0).isGpf(refill.level_dup(0), mPBMTE),
-    !memPte(0).isAf()))))
+    !flush_dup(0) && 
+    refill.levelOH.sp && 
+    (memPte(0).isLeaf() || memPte(0).isPf(refill.level_dup(0), pbmte)) && 
+    spGoodToRefill
   ) {
     val refillIdx = spreplace.way// LFSR64()(log2Up(l2tlbParams.spSize)-1,0) // TODO: may be LRU
     val rfOH = UIntToOH(refillIdx)
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index c83a971c4da..bb6340d3925 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -140,7 +140,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val stage1 = RegEnable(io.req.bits.stage1, io.req.fire)
   val hptw_resp_stage2 = Reg(Bool())
 
-  val ppn_af = Mux(enableS2xlate, Mux(onlyS1xlate, pte.isAf() && !pte.isStage1Gpf(io.csr.vsatp.mode), false.B), pte.isAf()) // In two-stage address translation, stage 1 ppn is a vpn for host, so don't need to check ppn_high
+  val ppn_af = Mux(enableS2xlate, Mux(onlyS1xlate, pte.isAf(), false.B), pte.isAf()) // In two-stage address translation, stage 1 ppn is a vpn for host, so don't need to check ppn_high
   val find_pte = pte.isLeaf() || ppn_af || pageFault
   val to_find_pte = level === 1.U && find_pte === false.B
   val source = RegEnable(io.req.bits.req_info.source, io.req.fire)
@@ -174,7 +174,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     ))),
     0.U(offLen.W))
   ))
-  val gvpn_gpf = Mux(enableS2xlate && io.csr.hgatp.mode === Sv39x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(enableS2xlate && io.csr.hgatp.mode === Sv48x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
+  val gvpn_gpf = Mux(s2xlate && io.csr.hgatp.mode === Sv39x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(s2xlate && io.csr.hgatp.mode === Sv48x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
   val guestFault = hptw_pageFault || hptw_accessFault || gvpn_gpf
   val hpaddr = Cat(hptw_resp.genPPNS2(get_pn(gpaddr)), get_off(gpaddr))
   val fake_h_resp = 0.U.asTypeOf(new HptwResp)
@@ -195,7 +195,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
 
   io.req.ready := idle
   val ptw_resp = Wire(new PtwMergeResp)
-  ptw_resp.apply(Mux(pte_valid, pageFault && !accessFault && !ppn_af, false.B), accessFault || ppn_af, Mux(accessFault, af_level, Mux(guestFault, gpf_level, level)), Mux(pte_valid, pte, fake_pte), vpn, satp.asid, hgatp.vmid, vpn(sectortlbwidth - 1, 0), not_super = false)
+  ptw_resp.apply(Mux(pte_valid, pageFault && !accessFault, false.B), accessFault || (ppn_af && !(pte_valid && (pageFault || guestFault))), Mux(accessFault, af_level, Mux(guestFault, gpf_level, level)), Mux(pte_valid, pte, fake_pte), vpn, satp.asid, hgatp.vmid, vpn(sectortlbwidth - 1, 0), not_super = false, not_merge = false)
 
   val normal_resp = idle === false.B && mem_addr_update && !last_s2xlate && (guestFault || (w_mem_resp && find_pte) || (s_pmp_check && accessFault) || onlyS2xlate )
   val stageHit_resp = idle === false.B && hptw_resp_stage2
@@ -658,9 +658,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
                 , state_last_hptw_req, state_mem_out)
         mem_resp_hit(i) := true.B
         entries(i).ppn := ptes(index).getPPN() // for last stage 2 translation
-        // when onlystage1, gpf has higher priority
-        entries(i).af := Mux(entries(i).req_info.s2xlate === allStage, false.B, Mux(entries(i).req_info.s2xlate === onlyStage1, ptes(index).isAf() && !ptes(index).isStage1Gpf(io.csr.vsatp.mode), ptes(index).isAf()))
-        entries(i).hptw_resp.gpf := Mux(entries(i).req_info.s2xlate === allStage || entries(i).req_info.s2xlate === onlyStage1, ptes(index).isStage1Gpf(io.csr.vsatp.mode), false.B)
+        entries(i).hptw_resp.gpf := Mux(entries(i).req_info.s2xlate === allStage, ptes(index).isStage1Gpf(io.csr.vsatp.mode), false.B)
       }
     }
   }
@@ -900,7 +898,15 @@ class HPTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
 
   io.req.ready := idle
   val resp = Wire(new HptwResp())
-  resp.apply(pageFault && !accessFault && !ppn_af, accessFault || ppn_af, Mux(accessFault, af_level, level), pte, vpn, hgatp.vmid)
+  // accessFault > pageFault > ppn_af
+  resp.apply(
+    gpf = pageFault && !accessFault,
+    gaf = accessFault || (ppn_af && !pageFault),
+    level = Mux(accessFault, af_level, level),
+    pte = pte, 
+    vpn = vpn, 
+    vmid = hgatp.vmid
+  )
   io.resp.valid := resp_valid
   io.resp.bits.id := id
   io.resp.bits.resp := resp
```
