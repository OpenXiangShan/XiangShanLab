# Commit Log
- Issue: #3588
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3588
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3588
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3588
- Changed files: 2
- Additions: 57
- Deletions: 129

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index e635c44a5b6..9931088d3ec 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -762,6 +762,30 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
   def getPPN() = {
     Cat(ppn_high, ppn)
   }
+
+  def canRefill(levelUInt: UInt, s2xlate: UInt, pbmte: Bool, mode: UInt) = {
+    val canRefill = WireInit(false.B)
+    switch (s2xlate) {
+      is (allStage) {
+        canRefill := !isStage1Gpf(mode) && !isPf(levelUInt, pbmte)
+      }
+      is (onlyStage1) {
+        canRefill := !isAf() && !isPf(levelUInt, pbmte)
+      }
+      is (onlyStage2) {
+        canRefill := !isAf() && !isGpf(levelUInt, pbmte)
+      }
+      is (noS2xlate) {
+        canRefill := !isAf() && !isPf(levelUInt, pbmte)
+      }
+    }
+    canRefill
+  }
+
+  def onlyPf(levelUInt: UInt, s2xlate: UInt, pbmte: Bool) = {
+    s2xlate === noS2xlate && isPf(levelUInt, pbmte) && !isAf()
+  }
+
   override def toPrintable: Printable = {
     p"ppn:0x${Hexadecimal(ppn)} perm:b${Binary(perm.asUInt)}"
   }
@@ -888,8 +912,10 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, ReservedBi
   val vmid = Some(UInt(vmidLen.W))
   val pbmts = Vec(num, UInt(ptePbmtLen.W))
   val ppns = Vec(num, UInt(gvpnLen.W))
+  // valid or not, vs = 0 will not hit
   val vs   = Vec(num, Bool())
-  val af   = Vec(num, Bool())
+  // only pf or not, onlypf = 1 means only trigger pf when nox2late
+  val onlypf = Vec(num, Bool())
   val perms = if (hasPerm) Some(Vec(num, new PtePermBundle)) else None
   val prefetch = Bool()
   val reservedBits = if(ReservedBits > 0) Some(UInt(ReservedBits.W)) else None
@@ -915,10 +941,10 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, ReservedBi
     val asid_value = Mux(s2xlate, vasid, asid)
     val asid_hit = if (ignoreAsid) true.B else (this.asid === asid_value)
     val vmid_hit = Mux(s2xlate, this.vmid.getOrElse(0.U) === vmid, true.B)
-    asid_hit && vmid_hit && tag === tagClip(vpn) && (if (hasPerm) true.B else vs(sectorIdxClip(vpn, level)))
+    asid_hit && vmid_hit && tag === tagClip(vpn) && vs(sectorIdxClip(vpn, level))
   }
 
-  def genEntries(vpn: UInt, asid: UInt, vmid: UInt, data: UInt, levelUInt: UInt, prefetch: Bool, s2xlate: UInt, pbmte: Bool) = {
+  def genEntries(vpn: UInt, asid: UInt, vmid: UInt, data: UInt, levelUInt: UInt, prefetch: Bool, s2xlate: UInt, pbmte: Bool, mode: UInt) = {
     require((data.getWidth / XLEN) == num,
       s"input data length must be multiple of pte length: data.length:${data.getWidth} num:${num}")
 
@@ -931,8 +957,8 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, ReservedBi
       val pte = data((i+1)*XLEN-1, i*XLEN).asTypeOf(new PteBundle)
       ps.pbmts(i) := pte.pbmt
       ps.ppns(i) := pte.ppn
-      ps.vs(i)   := Mux(s2xlate === onlyStage2, !pte.isGpf(levelUInt, pbmte), !pte.isPf(levelUInt, pbmte)) && (if (hasPerm) pte.isLeaf() else !pte.isLeaf())
-      ps.af(i)   := Mux(s2xlate === allStage, false.B, pte.isAf()) // if allstage, this refill is from ptw or llptw, so the af is invalid
+      ps.vs(i)   := (pte.canRefill(levelUInt, s2xlate, pbmte, mode) || (if (hasPerm) pte.onlyPf(levelUInt, s2xlate, pbmte) else false.B)) && (if (hasPerm) pte.isLeaf() else !pte.isLeaf())
+      ps.onlypf(i) := pte.onlyPf(levelUInt, s2xlate, pbmte)
       ps.perms.map(_(i) := pte.perm)
     }
     ps.reservedBits.map(_ := true.B)
@@ -994,8 +1020,8 @@ class PTWEntriesWithEcc(eccCode: Code, num: Int, tagLen: Int, level: Int, hasPer
     Cat(res).orR
   }
 
-  def gen(vpn: UInt, asid: UInt, vmid: UInt, data: UInt, levelUInt: UInt, prefetch: Bool, s2xlate: UInt, pbmte: Bool) = {
-    this.entries := entries.genEntries(vpn, asid, vmid, data, levelUInt, prefetch, s2xlate, pbmte)
+  def gen(vpn: UInt, asid: UInt, vmid: UInt, data: UInt, levelUInt: UInt, prefetch: Bool, s2xlate: UInt, pbmte: Bool, mode: UInt) = {
+    this.entries := entries.genEntries(vpn, asid, vmid, data, levelUInt, prefetch, s2xlate, pbmte, mode)
     this.encode()
   }
 }
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 2800d50146c..9b3ffb6f226 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -68,12 +68,10 @@ class PageCacheMergePespBundle(implicit p: Parameters) extends PtwBundle {
   val ecc = Bool()
   val level = UInt(2.W)
   val v = Vec(tlbcontiguous, Bool())
-  val af = Vec(tlbcontiguous, Bool())
 
   def apply(hit: Bool, pre: Bool, ppn: Vec[UInt], pbmt: Vec[UInt] = Vec(tlbcontiguous, 0.U),
             perm: Vec[PtePermBundle] = Vec(tlbcontiguous, 0.U.asTypeOf(new PtePermBundle())),
-            ecc: Bool = false.B, level: UInt = 0.U, valid: Vec[Bool] = Vec(tlbcontiguous, true.B),
-            accessFault: Vec[Bool] = Vec(tlbcontiguous, true.B)): Unit = {
+            ecc: Bool = false.B, level: UInt = 0.U, valid: Vec[Bool] = Vec(tlbcontiguous, true.B)): Unit = {
     this.hit := hit && !ecc
     this.pre := pre
     this.ppn := ppn
@@ -82,7 +80,6 @@ class PageCacheMergePespBundle(implicit p: Parameters) extends PtwBundle {
     this.ecc := ecc && hit
     this.level := level
     this.v := valid
-    this.af := accessFault
   }
 }
 
@@ -499,8 +496,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   val l0HitPPN = l0HitData.ppns
   val l0HitPbmt = l0HitData.pbmts
   val l0HitPerm = l0HitData.perms.getOrElse(0.U.asTypeOf(Vec(PtwL0SectorSize, new PtePermBundle)))
-  val l0HitValid = l0HitData.vs
-  val l0HitAf = l0HitData.af
+  val l0HitValid = VecInit(l0HitData.onlypf.map(!_))
 
   // super page
   val spreplace = ReplacementPolicy.fromString(l2tlbParams.spReplacer, l2tlbParams.spSize)
@@ -533,7 +529,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   check_res.l3.map(_.apply(l3Hit.get, l3Pre.get, l3HitPPN.get))
   check_res.l2.apply(l2Hit, l2Pre, l2HitPPN, l2HitPbmt)
   check_res.l1.apply(l1Hit, l1Pre, l1HitPPN, l1HitPbmt, ecc = l1eccError)
-  check_res.l0.apply(l0Hit, l0Pre, l0HitPPN, l0HitPbmt, l0HitPerm, l0eccError, valid = l0HitValid, accessFault = l0HitAf)
+  check_res.l0.apply(l0Hit, l0Pre, l0HitPPN, l0HitPbmt, l0HitPerm, l0eccError, valid = l0HitValid)
   check_res.sp.apply(spHit, spPre, spHitData.ppn, spHitData.pbmt, spHitPerm, false.B, spHitLevel, spValid)
 
   val resp_res = Reg(new PageCacheRespBundle)
@@ -596,7 +592,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   io.resp.bits.toHptw.resp.entry.perm.map(_ := Mux(resp_res.l0.hit, resp_res.l0.perm(idx), resp_res.sp.perm))
   io.resp.bits.toHptw.resp.entry.v := Mux(resp_res.l0.hit, resp_res.l0.v(idx), resp_res.sp.v)
   io.resp.bits.toHptw.resp.gpf := !io.resp.bits.toHptw.resp.entry.v
-  io.resp.bits.toHptw.resp.gaf := Mux(resp_res.l0.hit, resp_res.l0.af(idx), false.B)
+  io.resp.bits.toHptw.resp.gaf := false.B
 
   io.resp.bits.stage1.entry.map(_.tag := stageResp.bits.req_info.vpn(vpnLen - 1, 3))
   io.resp.bits.stage1.entry.map(_.asid := Mux(stageResp.bits.req_info.hasS2xlate(), io.csr_dup(0).vsatp.asid, io.csr_dup(0).satp.asid)) // DontCare
@@ -649,7 +645,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
           resp_res.l2.pbmt)))
     io.resp.bits.stage1.entry(i).perm.map(_ := Mux(resp_res.l0.hit, resp_res.l0.perm(i),  Mux(resp_res.sp.hit, resp_res.sp.perm, 0.U.asTypeOf(new PtePermBundle))))
     io.resp.bits.stage1.entry(i).pf := !io.resp.bits.stage1.entry(i).v
-    io.resp.bits.stage1.entry(i).af := Mux(resp_res.l0.hit, resp_res.l0.af(i), false.B)
+    io.resp.bits.stage1.entry(i).af := false.B
   }
   io.resp.bits.stage1.pteidx := UIntToOH(idx).asBools
   io.resp.bits.stage1.not_super := Mux(resp_res.l0.hit, true.B, false.B)
@@ -686,29 +682,11 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
   // TODO: handle sfenceLatch outsize
   if (EnableSv48) {
-    // L3 refill
-    val l3GoodToRefill = WireInit(false.B)
-    switch (refill.req_info_dup(2).s2xlate) {
-      is (allStage) {
-        l3GoodToRefill := !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)
-      }
-      is (onlyStage1) {
-        l3GoodToRefill := !memPte(2).isAf()
-      }
-      is (onlyStage2) {
-        l3GoodToRefill := !memPte(2).isAf() && !memPte(2).isGpf(refill.level_dup(2), mPBMTE)
-      }
-      is (noS2xlate) {
-        l3GoodToRefill := !memPte(2).isAf()
-      }
-    }
-
     when (
-      !flush_dup(2) && 
-      refill.levelOH.l3.get && 
-      !memPte(2).isLeaf() && 
-      !memPte(2).isPf(refill.level_dup(2), pbmte) && 
-      l3GoodToRefill
+      !flush_dup(2) &&
+      refill.levelOH.l3.get &&
+      !memPte(2).isLeaf() &&
+      memPte(2).canRefill(refill.level_dup(2), refill.req_info_dup(2).s2xlate, pbmte, io.csr_dup(2).vsatp.mode)
     ) {
       val refillIdx = replaceWrapper(l3v.get, ptwl3replace.get.way)
       refillIdx.suggestName(s"Ptwl3RefillIdx")
@@ -739,27 +717,11 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
 
   // L2 refill
-  val l2GoodToRefill = WireInit(false.B)
-  switch (refill.req_info_dup(2).s2xlate) {
-    is (allStage) {
-      l2GoodToRefill := !memPte(2).isStage1Gpf(io.csr_dup(2).vsatp.mode)
-    }
-    is (onlyStage1) {
-      l2GoodToRefill := !memPte(2).isAf()
-    }
-    is (onlyStage2) {
-      l2GoodToRefill := !memPte(2).isAf() && !memPte(2).isGpf(refill.level_dup(2), mPBMTE)
-    }
-    is (noS2xlate) {
-      l2GoodToRefill := !memPte(2).isAf()
-    }
-  }
   when (
-    !flush_dup(2) && 
-    refill.levelOH.l2 && 
-    !memPte(2).isLeaf() && 
-    !memPte(2).isPf(refill.level_dup(2), pbmte) && 
-    l2GoodToRefill
+    !flush_dup(2) &&
+    refill.levelOH.l2 &&
+    !memPte(2).isLeaf() &&
+    memPte(2).canRefill(refill.level_dup(2), refill.req_info_dup(2).s2xlate, pbmte, io.csr_dup(2).vsatp.mode)
   ) {
     val refillIdx = replaceWrapper(l2v, ptwl2replace.way)
     refillIdx.suggestName(s"Ptwl2RefillIdx")
@@ -789,32 +751,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
 
   // L1 refill
-  val l1GoodToRefill = WireInit(false.B)
-  switch (refill.req_info_dup(1).s2xlate) {
-    is (allStage) {
-      // l1GoodToRefill := !memPte(1).isStage1Gpf(io.csr_dup(1).vsatp.mode)
-      l1GoodToRefill := !Cat(memPtes.map(_.isStage1Gpf(io.csr_dup(1).vsatp.mode))).orR
-    }
-    is (onlyStage1) {
-      // l1GoodToRefill := !memPte(1).isAf()
-      l1GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
-    }
-    is (onlyStage2) {
-      // l1GoodToRefill := !memPte(1).isGpf(refill.level_dup(1))
-      // l1GoodToRefill := !Cat(memPtes.map(_.isGpf(refill.level_dup(1)))).orR
-      l1GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
-    }
-    is (noS2xlate) {
-      // l1GoodToRefill := !memPte(1).isAf()
-      l1GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
-    }
-  }
-  when (
-    !flush_dup(1) && refill.levelOH.l1 && 
-    !memPte(1).isLeaf() && 
-    !memPte(1).isPf(refill.level_dup(1), pbmte) && 
-    l1GoodToRefill
-  ) {
+  when (!flush_dup(1) && refill.levelOH.l1) {
     val refillIdx = genPtwL1SetIdx(refill.req_info_dup(1).vpn)
     val victimWay = replaceWrapper(getl1vSet(refill.req_info_dup(1).vpn), ptwl1replace.way(refillIdx))
     val victimWayOH = UIntToOH(victimWay)
@@ -828,7 +765,8 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
       levelUInt = 1.U,
       refill_prefetch_dup(1),
       refill.req_info_dup(1).s2xlate,
-      pbmte
+      pbmte,
+      io.csr_dup(1).vsatp.mode
     )
     l1.io.w.apply(
       valid = true.B,
@@ -857,28 +795,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   }
 
   // L0 refill
-  val l0GoodToRefill = WireInit(false.B)
-  switch (refill.req_info_dup(0).s2xlate) {
-    is (allStage) {
-      // l0GoodToRefill := !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)
-      l0GoodToRefill := !Cat(memPtes.map(_.isStage1Gpf(io.csr_dup(0).vsatp.mode))).orR
-    }
-    is (onlyStage1) {
-      // l0GoodToRefill := !memPte(0).isAf()
-      l0GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
-    }
-    is (onlyStage2) {
-      // l0GoodToRefill := !memPte(0).isGpf(refill.level_dup(0))
-      // l0GoodToRefill := !Cat(memPtes.map(_.isGpf(refill.level_dup(0)))).orR
-      l0GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
-    }
-    is (noS2xlate) {
-      // l0GoodToRefill := !memPte(0).isAf()
-      l0GoodToRefill := !Cat(memPtes.map(_.isAf())).orR
-    }
-  }
-
-  when (!flush_dup(0) && refill.levelOH.l0 && l0GoodToRefill) {
+  when (!flush_dup(0) && refill.levelOH.l0) {
     val refillIdx = genPtwL0SetIdx(refill.req_info_dup(0).vpn)
     val victimWay = replaceWrapper(getl0vSet(refill.req_info_dup(0).vpn), ptwl0replace.way(refillIdx))
     val victimWayOH = UIntToOH(victimWay)
@@ -892,7 +809,8 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
       levelUInt = 0.U,
       refill_prefetch_dup(0),
       refill.req_info_dup(0).s2xlate,
-      pbmte
+      pbmte,
+      io.csr_dup(0).vsatp.mode
     )
     l0.io.w.apply(
       valid = true.B,
@@ -922,27 +840,11 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
 
   // misc entries: super & invalid
-  val spGoodToRefill = WireInit(false.B)
-  switch (refill.req_info_dup(0).s2xlate) {
-    is (allStage) {
-      spGoodToRefill := !memPte(0).isStage1Gpf(io.csr_dup(0).vsatp.mode)
-    }
-    is (onlyStage1) {
-      spGoodToRefill := !memPte(0).isAf()
-    }
-    is (onlyStage2) {
-      spGoodToRefill := !memPte(0).isGpf(refill.level_dup(0), mPBMTE)
-    }
-    is (noS2xlate) {
-      spGoodToRefill := !memPte(0).isAf()
-    }
-  }
-
   when (
-    !flush_dup(0) && 
-    refill.levelOH.sp && 
-    (memPte(0).isLeaf() || memPte(0).isPf(refill.level_dup(0), pbmte)) && 
-    spGoodToRefill
+    !flush_dup(0) &&
+    refill.levelOH.sp &&
+    ((memPte(0).isLeaf() && memPte(0).canRefill(refill.level_dup(0), refill.req_info_dup(0).s2xlate, pbmte, io.csr_dup(0).vsatp.mode)) ||
+    memPte(0).onlyPf(refill.level_dup(0), refill.req_info_dup(0).s2xlate, pbmte))
   ) {
     val refillIdx = spreplace.way// LFSR64()(log2Up(l2tlbParams.spSize)-1,0) // TODO: may be LRU
     val rfOH = UIntToOH(refillIdx)
@@ -953,7 +855,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
       memSelData(0),
       refill.level_dup(0),
       refill_prefetch_dup(0),
-      !memPte(0).isPf(refill.level_dup(0), pbmte),
+      !memPte(0).onlyPf(refill.level_dup(0), refill.req_info_dup(0).s2xlate, pbmte)
     )
     spreplace.access(refillIdx)
     spv := spv | rfOH
```
