# Commit Log
- Issue: #3427
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3427
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3427
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3427
- Changed files: 4
- Additions: 16
- Deletions: 12

## Files
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 135eaae1e06..5dd167819cb 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -584,6 +584,8 @@ trait HasXSParameter {
   def HasIcache = coreParams.HasICache
   def HasDcache = coreParams.HasDCache
   def AddrBits = coreParams.AddrBits // AddrBits is used in some cases
+  def GPAddrBitsSv39x4 = coreParams.GPAddrBitsSv39x4
+  def GPAddrBitsSv48x4 = coreParams.GPAddrBitsSv48x4
   def GPAddrBits = {
     if (EnableSv48)
       coreParams.GPAddrBitsSv48x4
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 643ccd89a20..cc9874506f1 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -734,8 +734,10 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
     af
   }
 
-  def isStage1Gpf() = {
-    !((Cat(ppn_high, ppn) >> gvpnLen) === 0.U)
+  def isStage1Gpf(mode: UInt) = {
+    val sv39_high = Cat(ppn_high, ppn) >> (GPAddrBitsSv39x4 - offLen)
+    val sv48_high = Cat(ppn_high, ppn) >> (GPAddrBitsSv48x4 - offLen)
+    !(Mux(mode === Sv39, sv39_high, Mux(mode === Sv48, sv48_high, 0.U)) === 0.U)
   }
 
   def getPerm() = {
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 49a7354519e..44d46bc7525 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -676,7 +676,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   // TODO: handle sfenceLatch outsize
   if (EnableSv48) {
     when (!flush_dup(2) && refill.levelOH.l3.get && !memPte(2).isLeaf() && !memPte(2).isPf(refill.level_dup(2)) 
-    && Mux(refill.req_info_dup(2).s2xlate === allStage, !memPte(2).isStage1Gpf(), Mux(refill.req_info_dup(2).s2xlate === onlyStage1, !(memPte(2).isAf() || memPte(2).isStage1Gpf()), Mux(refill.req_info_dup(2).s2xlate === onlyStage2, !memPte(2).isGpf(refill.level_dup(2)), !memPte(2).isAf())))) {
+    && Mux(refill.req_info_dup(2).s2xlate === allStage, !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode), Mux(refill.req_info_dup(2).s2xlate === onlyStage1, !(memPte(2).isAf() || memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)), Mux(refill.req_info_dup(2).s2xlate === onlyStage2, !memPte(2).isGpf(refill.level_dup(2)), !memPte(2).isAf())))) {
       val refillIdx = replaceWrapper(l3v.get, ptwl3replace.get.way)
       refillIdx.suggestName(s"Ptwl3RefillIdx")
       val rfOH = UIntToOH(refillIdx)
@@ -706,7 +706,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
 
   when (!flush_dup(2) && refill.levelOH.l2 && !memPte(2).isLeaf() && !memPte(2).isPf(refill.level_dup(2)) 
-    && Mux(refill.req_info_dup(2).s2xlate === allStage, !memPte(2).isStage1Gpf(), Mux(refill.req_info_dup(2).s2xlate === onlyStage1, !(memPte(2).isAf() || memPte(2).isStage1Gpf()), Mux(refill.req_info_dup(2).s2xlate === onlyStage2, !memPte(2).isGpf(refill.level_dup(2)), !memPte(2).isAf())))) {
+    && Mux(refill.req_info_dup(2).s2xlate === allStage, !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode), Mux(refill.req_info_dup(2).s2xlate === onlyStage1, !(memPte(2).isAf() || memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)), Mux(refill.req_info_dup(2).s2xlate === onlyStage2, !memPte(2).isGpf(refill.level_dup(2)), !memPte(2).isAf())))) {
     val refillIdx = replaceWrapper(l2v, ptwl2replace.way)
     refillIdx.suggestName(s"Ptwl2RefillIdx")
     val rfOH = UIntToOH(refillIdx)
@@ -735,7 +735,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
 
   when (!flush_dup(1) && refill.levelOH.l1 && !memPte(1).isLeaf() && !memPte(1).isPf(refill.level_dup(1)) 
-  && Mux(refill.req_info_dup(1).s2xlate === allStage, !memPte(1).isStage1Gpf(), Mux(refill.req_info_dup(1).s2xlate === onlyStage1, !(memPte(1).isAf() || memPte(1).isStage1Gpf()), Mux(refill.req_info_dup(1).s2xlate === onlyStage2, !memPte(1).isGpf(refill.level_dup(1)), !memPte(1).isAf())))) {
+  && Mux(refill.req_info_dup(1).s2xlate === allStage, !memPte(1).isStage1Gpf(io.csr_dup(1).vsatp.mode), Mux(refill.req_info_dup(1).s2xlate === onlyStage1, !(memPte(1).isAf() || memPte(1).isStage1Gpf(io.csr_dup(1).vsatp.mode)), Mux(refill.req_info_dup(1).s2xlate === onlyStage2, !memPte(1).isGpf(refill.level_dup(1)), !memPte(1).isAf())))) {
     val refillIdx = genPtwL1SetIdx(refill.req_info_dup(1).vpn)
     val victimWay = replaceWrapper(getl1vSet(refill.req_info_dup(1).vpn), ptwl1replace.way(refillIdx))
     val victimWayOH = UIntToOH(victimWay)
@@ -777,7 +777,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
 
   when (!flush_dup(0) && refill.levelOH.l0
-  && Mux(refill.req_info_dup(0).s2xlate === allStage, !memPte(0).isStage1Gpf(), Mux(refill.req_info_dup(0).s2xlate === onlyStage1, !(memPte(0).isAf() || memPte(0).isStage1Gpf()), Mux(refill.req_info_dup(0).s2xlate === onlyStage2, !memPte(0).isGpf(refill.level_dup(0)), !memPte(0).isAf())))) {
+  && Mux(refill.req_info_dup(0).s2xlate === allStage, !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode), Mux(refill.req_info_dup(0).s2xlate === onlyStage1, !(memPte(0).isAf() || memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)), Mux(refill.req_info_dup(0).s2xlate === onlyStage2, !memPte(0).isGpf(refill.level_dup(0)), !memPte(0).isAf())))) {
     val refillIdx = genPtwL0SetIdx(refill.req_info_dup(0).vpn)
     val victimWay = replaceWrapper(getl0vSet(refill.req_info_dup(0).vpn), ptwl0replace.way(refillIdx))
     val victimWayOH = UIntToOH(victimWay)
@@ -821,7 +821,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
   // misc entries: super & invalid
   when (!flush_dup(0) && refill.levelOH.sp && (memPte(0).isLeaf() || memPte(0).isPf(refill.level_dup(0))) 
-  && Mux(refill.req_info_dup(0).s2xlate === allStage, !memPte(0).isStage1Gpf(), Mux(refill.req_info_dup(0).s2xlate === onlyStage1, !(memPte(0).isAf() || memPte(0).isStage1Gpf()), Mux(refill.req_info_dup(0).s2xlate === onlyStage2, !memPte(0).isGpf(refill.level_dup(0)), !memPte(0).isAf())))) {
+  && Mux(refill.req_info_dup(0).s2xlate === allStage, !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode), Mux(refill.req_info_dup(0).s2xlate === onlyStage1, !(memPte(0).isAf() || memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)), Mux(refill.req_info_dup(0).s2xlate === onlyStage2, !memPte(0).isGpf(refill.level_dup(0)), !memPte(0).isAf())))) {
     val refillIdx = spreplace.way// LFSR64()(log2Up(l2tlbParams.spSize)-1,0) // TODO: may be LRU
     val rfOH = UIntToOH(refillIdx)
     sp(refillIdx).refill(
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index bd6674ef229..6283efd384a 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -140,7 +140,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val stage1 = RegEnable(io.req.bits.stage1, io.req.fire)
   val hptw_resp_stage2 = Reg(Bool())
 
-  val ppn_af = Mux(enableS2xlate, Mux(onlyS1xlate, pte.isAf() && !pte.isStage1Gpf(), false.B), pte.isAf()) // In two-stage address translation, stage 1 ppn is a vpn for host, so don't need to check ppn_high
+  val ppn_af = Mux(enableS2xlate, Mux(onlyS1xlate, pte.isAf() && !pte.isStage1Gpf(io.csr.vsatp.mode), false.B), pte.isAf()) // In two-stage address translation, stage 1 ppn is a vpn for host, so don't need to check ppn_high
   val find_pte = pte.isLeaf() || ppn_af || pageFault
   val to_find_pte = level === 1.U && find_pte === false.B
   val source = RegEnable(io.req.bits.req_info.source, io.req.fire)
@@ -174,7 +174,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     ))),
     0.U(offLen.W))
   ))
-  val gvpn_gpf = Mux(enableS2xlate, gpaddr(gpaddr.getWidth - 1, GPAddrBits) =/= 0.U, false.B)
+  val gvpn_gpf = Mux(enableS2xlate && io.csr.vsatp.mode === Sv39, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(enableS2xlate && io.csr.vsatp.mode === Sv48, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
   val guestFault = hptw_pageFault || hptw_accessFault || gvpn_gpf
   val hpaddr = Cat(hptw_resp.genPPNS2(get_pn(gpaddr)), get_off(gpaddr))
   val fake_h_resp = 0.U.asTypeOf(new HptwResp)
@@ -639,13 +639,13 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         val req_paddr = MakeAddr(entries(i).ppn, getVpnn(entries(i).req_info.vpn, 0))
         val req_hpaddr = MakeAddr(entries(i).hptw_resp.genPPNS2(get_pn(req_paddr)), getVpnn(entries(i).req_info.vpn, 0))
         val index =  Mux(entries(i).req_info.s2xlate === allStage, req_hpaddr, req_paddr)(log2Up(l2tlbParams.blockBytes)-1, log2Up(XLEN/8))
-        state(i) := Mux(entries(i).req_info.s2xlate === allStage && !(ptes(index).isPf(0.U) || !ptes(index).isLeaf() || ptes(index).isAf() || ptes(index).isStage1Gpf()) 
+        state(i) := Mux(entries(i).req_info.s2xlate === allStage && !(ptes(index).isPf(0.U) || !ptes(index).isLeaf() || ptes(index).isAf() || ptes(index).isStage1Gpf(io.csr.vsatp.mode)) 
                 , state_last_hptw_req, state_mem_out)
         mem_resp_hit(i) := true.B
         entries(i).ppn := ptes(index).getPPN() // for last stage 2 translation
         // when onlystage1, gpf has higher priority
-        entries(i).af := Mux(entries(i).req_info.s2xlate === allStage, false.B, Mux(entries(i).req_info.s2xlate === onlyStage1, ptes(index).isAf() && !ptes(index).isStage1Gpf(), ptes(index).isAf()))
-        entries(i).hptw_resp.gpf := Mux(entries(i).req_info.s2xlate === allStage || entries(i).req_info.s2xlate === onlyStage1, ptes(index).isStage1Gpf(), false.B)
+        entries(i).af := Mux(entries(i).req_info.s2xlate === allStage, false.B, Mux(entries(i).req_info.s2xlate === onlyStage1, ptes(index).isAf() && !ptes(index).isStage1Gpf(io.csr.vsatp.mode), ptes(index).isAf()))
+        entries(i).hptw_resp.gpf := Mux(entries(i).req_info.s2xlate === allStage || entries(i).req_info.s2xlate === onlyStage1, ptes(index).isStage1Gpf(io.csr.vsatp.mode), false.B)
       }
     }
   }
```
