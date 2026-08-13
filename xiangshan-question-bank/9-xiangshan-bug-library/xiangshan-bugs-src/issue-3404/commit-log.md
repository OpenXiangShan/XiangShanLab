# Commit Log
- Issue: #3404
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3404
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3404
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3404
- Changed files: 16
- Additions: 174
- Deletions: 56

## Files
- `coupledL2`
- `difftest`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUConst.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`
- `src/main/scala/xiangshan/frontend/icache/WayLookup.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/coupledL2 b/coupledL2
index 2a48e6f200b..ba56bb8c601 160000
--- a/coupledL2
+++ b/coupledL2
@@ -1 +1 @@
-Subproject commit 2a48e6f200bcbf82884836f90f3beb9dc76a01e6
+Subproject commit ba56bb8c6013d715b784b31e152fb3206a6abb8b
diff --git a/difftest b/difftest
index e7946f87fa4..2d1ce405bda 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit e7946f87fa490cba1af7e4bcbada03ebfd974f0f
+Subproject commit 2d1ce405bda56eb3d508bd8c9dc423e567a69090
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index 2c9c982090b..5461975e16e 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -193,6 +193,7 @@ class L2Top()(implicit p: Parameters) extends LazyModule
       l2.io.l2_tlb_req.resp.valid := l2_tlb_req.resp.valid
       l2.io.l2_tlb_req.req.ready := l2_tlb_req.req.ready
       l2.io.l2_tlb_req.resp.bits.paddr.head := l2_tlb_req.resp.bits.paddr.head
+      l2.io.l2_tlb_req.resp.bits.pbmt := l2_tlb_req.resp.bits.pbmt.head
       l2.io.l2_tlb_req.resp.bits.miss := l2_tlb_req.resp.bits.miss
       l2.io.l2_tlb_req.resp.bits.excp.head <> l2_tlb_req.resp.bits.excp.head
       l2.io.l2_tlb_req.pmp_resp.ld := l2_pmp_resp.ld
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index 4998392f7a9..16202517173 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -1272,7 +1272,7 @@ class MemBlockImp(outer: MemBlock) extends LazyModuleImp(outer)
     })
   }
 
-  // Uncahce
+  // Uncache
   uncache.io.enableOutstanding := io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable
   uncache.io.hartId := io.hartId
   lsq.io.uncacheOutstanding := io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index cb615be6398..9e306d9ec06 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -438,6 +438,8 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       difftest.valid := io.tlb(i).resp.fire && !io.tlb(i).resp.bits.s1.af && !io.tlb(i).resp.bits.s2.gaf
       difftest.index := i.U
       difftest.vpn := Cat(io.tlb(i).resp.bits.s1.entry.tag, 0.U(sectortlbwidth.W))
+      difftest.pbmt := io.tlb(i).resp.bits.s1.entry.pbmt
+      difftest.g_pbmt := io.tlb(i).resp.bits.s2.entry.pbmt
       for (j <- 0 until tlbcontiguous) {
         difftest.ppn(j) := Cat(io.tlb(i).resp.bits.s1.entry.ppn, io.tlb(i).resp.bits.s1.ppn_low(j))
         difftest.valididx(j) := io.tlb(i).resp.bits.s1.valididx(j)
@@ -553,6 +555,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       ptw_resp.ppn := pte_in.getPPN()(ptePPNLen - 1, sectortlbwidth)
       ptw_resp.ppn_low := pte_in.getPPN()(sectortlbwidth - 1, 0)
       ptw_resp.level.map(_ := 0.U)
+      ptw_resp.pbmt := pte_in.pbmt
       ptw_resp.perm.map(_ := pte_in.getPerm())
       ptw_resp.tag := vpn(vpnLen - 1, sectortlbwidth)
       ptw_resp.pf := (if (af_first) !af else true.B) && (pte_in.isPf(0.U) || !pte_in.isLeaf())
@@ -575,6 +578,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     ptw_sector_resp.entry.asid := pte.entry(OHToUInt(pte.pteidx)).asid
     ptw_sector_resp.entry.vmid.map(_ := pte.entry(OHToUInt(pte.pteidx)).vmid.getOrElse(0.U))
     ptw_sector_resp.entry.ppn := pte.entry(OHToUInt(pte.pteidx)).ppn
+    ptw_sector_resp.entry.pbmt := pte.entry(OHToUInt(pte.pteidx)).pbmt
     ptw_sector_resp.entry.perm.map(_ := pte.entry(OHToUInt(pte.pteidx)).perm.getOrElse(0.U.asTypeOf(new PtePermBundle)))
     ptw_sector_resp.entry.level.map(_ := pte.entry(OHToUInt(pte.pteidx)).level.getOrElse(0.U(log2Up(Level + 1).W)))
     ptw_sector_resp.entry.prefetch := pte.entry(OHToUInt(pte.pteidx)).prefetch
@@ -585,11 +589,12 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     ptw_sector_resp.pteidx := pte.pteidx
     for (i <- 0 until tlbcontiguous) {
       val ppn_equal = pte.entry(i).ppn === pte.entry(OHToUInt(pte.pteidx)).ppn
+      val pbmt_equal = pte.entry(i).pbmt === pte.entry(OHToUInt(pte.pteidx)).pbmt
       val perm_equal = pte.entry(i).perm.getOrElse(0.U.asTypeOf(new PtePermBundle)).asUInt === pte.entry(OHToUInt(pte.pteidx)).perm.getOrElse(0.U.asTypeOf(new PtePermBundle)).asUInt
       val v_equal = pte.entry(i).v === pte.entry(OHToUInt(pte.pteidx)).v
       val af_equal = pte.entry(i).af === pte.entry(OHToUInt(pte.pteidx)).af
       val pf_equal = pte.entry(i).pf === pte.entry(OHToUInt(pte.pteidx)).pf
-      ptw_sector_resp.valididx(i) := (ppn_equal && perm_equal && v_equal && af_equal && pf_equal) || !pte.not_super
+      ptw_sector_resp.valididx(i) := (ppn_equal && pbmt_equal && perm_equal && v_equal && af_equal && pf_equal) || !pte.not_super
       ptw_sector_resp.ppn_low(i) := pte.entry(i).ppn_low
     }
     ptw_sector_resp.valididx(OHToUInt(pte.pteidx)) := true.B
@@ -794,6 +799,7 @@ class FakePTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     io.tlb(i).resp.valid := PTWDelayN(io.tlb(i).req(0).fire, coreParams.softPTWDelay, flush(i))
     assert(!io.tlb(i).resp.valid || io.tlb(i).resp.ready)
     io.tlb(i).resp.bits.s1.entry.tag := PTWDelayN(io.tlb(i).req(0).bits.vpn, coreParams.softPTWDelay, flush(i))
+    io.tlb(i).resp.bits.s1.entry.pbmt := pte.pbmt
     io.tlb(i).resp.bits.s1.entry.ppn := pte.ppn
     io.tlb(i).resp.bits.s1.entry.perm.map(_ := pte.getPerm())
     io.tlb(i).resp.bits.s1.entry.level.map(_ := level)
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 9365cf71c20..3a1e7bdfc8f 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -186,6 +186,8 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
   */
   val level = Some(UInt(2.W))
   val ppn = UInt(sectorppnLen.W)
+  val pbmt = UInt(ptePbmtLen.W)
+  val g_pbmt = UInt(ptePbmtLen.W)
   val perm = new TlbSectorPermBundle
   val valididx = Vec(tlbcontiguous, Bool())
   val pteidx = Vec(tlbcontiguous, Bool())
@@ -285,6 +287,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     ))
     this.level.map(_ := inner_level)
     this.perm.apply(item.s1)
+    this.pbmt := item.s1.entry.pbmt
 
     val s1tag = item.s1.entry.tag
     val s2tag = item.s2.entry.tag(gvpnLen - 1, sectortlbwidth)
@@ -319,6 +322,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     this.ppn := Mux(item.s2xlate === noS2xlate || item.s2xlate === onlyStage1, s1ppn, s2ppn)
     this.ppn_low := Mux(item.s2xlate === noS2xlate || item.s2xlate === onlyStage1, s1ppn_low, s2ppn_low)
     this.vmid := item.s1.entry.vmid.getOrElse(0.U)
+    this.g_pbmt := item.s2.entry.pbmt
     this.g_perm.applyS2(item.s2)
     this.s2xlate := item.s2xlate
     this
@@ -373,6 +377,18 @@ object TlbCmd {
   def isAmo(a: UInt) = a===atom_write // NOTE: sc mixed
 }
 
+// Svpbmt extension
+object Pbmt {
+  def pma:  UInt = "b00".U  // None
+  def nc:   UInt = "b01".U  // Non-cacheable, idempotent, weakly-ordered (RVWMO), main memory
+  def io:   UInt = "b10".U  // Non-cacheable, non-idempotent, strongly-ordered (I/O ordering), I/O
+  def rsvd: UInt = "b11".U  // Reserved for future standard use
+  def width: Int = 2
+  
+  def apply() = UInt(2.W)
+  def isUncache(a: UInt) = a===nc || a===io
+}
+
 class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit p: Parameters) extends MMUIOBaseBundle {
   val r = new Bundle {
     val req = Vec(ports, Flipped(DecoupledIO(new Bundle {
@@ -382,6 +398,8 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
     val resp = Vec(ports, ValidIO(new Bundle{
       val hit = Output(Bool())
       val ppn = Vec(nDups, Output(UInt(ppnLen.W)))
+      val pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
+      val g_pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
       val perm = Vec(nDups, Output(new TlbSectorPermBundle()))
       val g_perm = Vec(nDups, Output(new TlbPermBundle()))
       val s2xlate = Vec(nDups, Output(UInt(2.W)))
@@ -401,7 +419,7 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
   }
 
   def r_resp_apply(i: Int) = {
-    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm)
+    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm, this.r.resp(i).bits.pbmt, this.r.resp(i).bits.g_pbmt)
   }
 
   def w_apply(valid: Bool, wayIdx: UInt, data: PtwRespS2): Unit = {
@@ -421,6 +439,8 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
     val resp = Vec(ports, ValidIO(new Bundle{
       val hit = Output(Bool())
       val ppn = Vec(nDups, Output(UInt(ppnLen.W)))
+      val pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
+      val g_pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
       val perm = Vec(nDups, Output(new TlbPermBundle()))
       val g_perm = Vec(nDups, Output(new TlbPermBundle()))
       val s2xlate = Vec(nDups, Output(UInt(2.W)))
@@ -438,7 +458,7 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
   }
 
   def r_resp_apply(i: Int) = {
-    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm, this.r.resp(i).bits.s2xlate)
+    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm, this.r.resp(i).bits.s2xlate, this.r.resp(i).bits.pbmt, this.r.resp(i).bits.g_pbmt)
   }
 
   def w_apply(valid: Bool, data: PtwRespS2): Unit = {
@@ -515,6 +535,7 @@ class TlbExceptionBundle(implicit p: Parameters) extends TlbBundle {
 class TlbResp(nDups: Int = 1)(implicit p: Parameters) extends TlbBundle {
   val paddr = Vec(nDups, Output(UInt(PAddrBits.W)))
   val gpaddr = Vec(nDups, Output(UInt(GPAddrBits.W)))
+  val pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
   val miss = Output(Bool())
   val excp = Vec(nDups, new Bundle {
     val gpf = new TlbExceptionBundle()
@@ -638,10 +659,12 @@ abstract class PtwModule(outer: L2TLB) extends LazyModuleImp(outer)
   with HasXSParameter with HasPtwConst
 
 class PteBundle(implicit p: Parameters) extends PtwBundle{
+  val n = UInt(pteNLen.W)
+  val pbmt = UInt(ptePbmtLen.W)
   val reserved  = UInt(pteResLen.W)
   val ppn_high = UInt(ppnHignLen.W)
   val ppn  = UInt(ppnLen.W)
-  val rsw  = UInt(2.W)
+  val rsw  = UInt(pteRswLen.W)
   val perm = new Bundle {
     val d    = Bool()
     val a    = Bool()
@@ -712,6 +735,7 @@ class PtwEntry(tagLen: Int, hasPerm: Boolean = false, hasLevel: Boolean = false)
   val tag = UInt(tagLen.W)
   val asid = UInt(asidLen.W)
   val vmid = if (HasHExtension) Some(UInt(vmidLen.W)) else None
+  val pbmt = UInt(ptePbmtLen.W)
   val ppn = UInt(gvpnLen.W)
   val perm = if (hasPerm) Some(new PtePermBundle) else None
   val level = if (hasLevel) Some(UInt(log2Up(Level + 1).W)) else None
@@ -782,6 +806,7 @@ class PtwEntry(tagLen: Int, hasPerm: Boolean = false, hasLevel: Boolean = false)
     require(this.asid.getWidth <= asid.getWidth) // maybe equal is better, but ugly outside
 
     tag := vpn(vpnLen - 1, vpnLen - tagLen)
+    pbmt := pte.asTypeOf(new PteBundle().cloneType).pbmt
     ppn := pte.asTypeOf(new PteBundle().cloneType).ppn
     perm.map(_ := pte.asTypeOf(new PteBundle().cloneType).perm)
     this.asid := asid
@@ -801,7 +826,7 @@ class PtwEntry(tagLen: Int, hasPerm: Boolean = false, hasLevel: Boolean = false)
 
   override def toPrintable: Printable = {
     // p"tag:0x${Hexadecimal(tag)} ppn:0x${Hexadecimal(ppn)} perm:${perm}"
-    p"tag:0x${Hexadecimal(tag)} ppn:0x${Hexadecimal(ppn)} " +
+    p"tag:0x${Hexadecimal(tag)} pbmt: ${pbmt} ppn:0x${Hexadecimal(ppn)} " +
       (if (hasPerm) p"perm:${perm.getOrElse(0.U.asTypeOf(new PtePermBundle))} " else p"") +
       (if (hasLevel) p"level:${level.getOrElse(0.U)}" else p"") +
       p"prefetch:${prefetch}"
@@ -825,6 +850,7 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, hasReserve
   val tag  = UInt(tagLen.W)
   val asid = UInt(asidLen.W)
   val vmid = Some(UInt(vmidLen.W))
+  val pbmts = Vec(num, UInt(ptePbmtLen.W))
   val ppns = Vec(num, UInt(gvpnLen.W))
   val vs   = Vec(num, Bool())
   val af   = Vec(num, Bool())
@@ -867,6 +893,7 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, hasReserve
     ps.prefetch := prefetch
     for (i <- 0 until num) {
       val pte = data((i+1)*XLEN-1, i*XLEN).asTypeOf(new PteBundle)
+      ps.pbmts(i) := pte.pbmt
       ps.ppns(i) := pte.ppn
       ps.vs(i)   := !pte.isPf(levelUInt) && (if (hasPerm) pte.isLeaf() else !pte.isLeaf())
       ps.af(i)   := Mux(s2xlate === allStage, false.B, pte.isAf()) // if allstage, this refill is from ptw or llptw, so the af is invalid
@@ -880,7 +907,7 @@ class PtwEntries(num: Int, tagLen: Int, level: Int, hasPerm: Boolean, hasReserve
     // require(num == 4, "if num is not 4, please comment this toPrintable")
     // NOTE: if num is not 4, please comment this toPrintable
     val permsInner = perms.getOrElse(0.U.asTypeOf(Vec(num, new PtePermBundle)))
-    p"asid: ${Hexadecimal(asid)} tag:0x${Hexadecimal(tag)} ppns:${printVec(ppns)} vs:${Binary(vs.asUInt)} " +
+    p"asid: ${Hexadecimal(asid)} tag:0x${Hexadecimal(tag)} pbmt:${printVec(pbmts)} ppns:${printVec(ppns)} vs:${Binary(vs.asUInt)} " +
       (if (hasPerm) p"perms:${printVec(permsInner)}" else p"")
   }
 }
@@ -966,6 +993,7 @@ class PtwResp(implicit p: Parameters) extends PtwBundle {
     this.entry.tag := vpn
     this.entry.perm.map(_ := pte.getPerm())
     this.entry.ppn := pte.ppn
+    this.entry.pbmt := pte.pbmt
     this.entry.prefetch := DontCare
     this.entry.asid := asid
     this.entry.v := !pf
@@ -989,6 +1017,7 @@ class HptwResp(implicit p: Parameters) extends PtwBundle {
     this.entry.tag := vpn
     this.entry.perm.map(_ := resp_pte.getPerm())
     this.entry.ppn := resp_pte.ppn
+    this.entry.pbmt := resp_pte.pbmt
     this.entry.prefetch := DontCare
     this.entry.asid := DontCare
     this.entry.vmid.map(_ := vmid)
@@ -1095,6 +1124,7 @@ class PtwMergeResp(implicit p: Parameters) extends PtwBundle {
     val ptw_resp = Wire(new PtwMergeEntry(tagLen = sectorvpnLen, hasPerm = true, hasLevel = true))
     ptw_resp.ppn := resp_pte.getPPN()(ptePPNLen - 1, sectortlbwidth)
     ptw_resp.ppn_low := resp_pte.getPPN()(sectortlbwidth - 1, 0)
+    ptw_resp.pbmt := resp_pte.pbmt
     ptw_resp.level.map(_ := level)
     ptw_resp.perm.map(_ := resp_pte.getPerm())
     ptw_resp.tag := vpn(vpnLen - 1, sectortlbwidth)
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
index 15e9bce1baf..a1b6262b2cb 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
@@ -93,9 +93,20 @@ trait HasTlbConst extends HasXSParameter {
   val vpnnLen = 9
   val extendVpnnBits = if (HasHExtension) 2 else 0
   val vpnLen  = VAddrBits - offLen // when opening H extention, vpnlen broaden two bits
-  val flagLen = 8
+  /*
+    Sv39 page table entry
+    +--+------+--------+----------------------+-----+--------+
+    |63|62  61|60    54|53                  10|9   8|7      0|
+    +--+------+--------+----------------------+-----+--------+
+    |N | PBMT |Reserved|        PPNs          | RSW |  FALG  |
+    +--+------+--------+----------------------+-----+--------+
+  */
+  val pteFlagLen = 8
+  val pteRswLen = 2
   val ptePPNLen = 44
-  val pteResLen = XLEN - ptePPNLen - 2 - flagLen
+  val pteResLen = 7
+  val ptePbmtLen = 2
+  val pteNLen = 1
   val ppnHignLen = ptePPNLen - ppnLen
   val gvpnLen = GPAddrBits - offLen
 
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index a9220c20b72..a1cceb070c6 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -37,16 +37,19 @@ class PageCachePerPespBundle(implicit p: Parameters) extends PtwBundle {
   val hit = Bool()
   val pre = Bool()
   val ppn = UInt(gvpnLen.W)
+  val pbmt = UInt(ptePbmtLen.W)
   val perm = new PtePermBundle()
   val ecc = Bool()
   val level = UInt(2.W)
   val v = Bool()
 
-  def apply(hit: Bool, pre: Bool, ppn: UInt, perm: PtePermBundle = 0.U.asTypeOf(new PtePermBundle()),
+  def apply(hit: Bool, pre: Bool, ppn: UInt, pbmt: UInt = 0.U,
+            perm: PtePermBundle = 0.U.asTypeOf(new PtePermBundle()),
             ecc: Bool = false.B, level: UInt = 0.U, valid: Bool = true.B): Unit = {
     this.hit := hit && !ecc
     this.pre := pre
     this.ppn := ppn
+    this.pbmt := pbmt
     this.perm := perm
     this.ecc := ecc && hit
     this.level := level
@@ -59,17 +62,21 @@ class PageCacheMergePespBundle(implicit p: Parameters) extends PtwBundle {
   val hit = Bool()
   val pre = Bool()
   val ppn = Vec(tlbcontiguous, UInt(gvpnLen.W))
+  val pbmt = Vec(tlbcontiguous, UInt(ptePbmtLen.W))
   val perm = Vec(tlbcontiguous, new PtePermBundle())
   val ecc = Bool()
   val level = UInt(2.W)
   val v = Vec(tlbcontiguous, Bool())
   val af = Vec(tlbcontiguous, Bool())
 
-  def apply(hit: Bool, pre: Bool, ppn: Vec[UInt], perm: Vec[PtePermBundle] = Vec(tlbcontiguous, 0.U.asTypeOf(new PtePermBundle())),
-            ecc: Bool = false.B, level: UInt = 0.U, valid: Vec[Bool] = Vec(tlbcontiguous, true.B), accessFault: Vec[Bool] = Vec(tlbcontiguous, true.B)): Unit = {
+  def apply(hit: Bool, pre: Bool, ppn: Vec[UInt], pbmt: Vec[UInt] = Vec(tlbcontiguous, 0.U),
+            perm: Vec[PtePermBundle] = Vec(tlbcontiguous, 0.U.asTypeOf(new PtePermBundle())),
+            ecc: Bool = false.B, level: UInt = 0.U, valid: Vec[Bool] = Vec(tlbcontiguous, true.B),
+            accessFault: Vec[Bool] = Vec(tlbcontiguous, true.B)): Unit = {
     this.hit := hit && !ecc
     this.pre := pre
     this.ppn := ppn
+    this.pbmt := pbmt
     this.perm := perm
     this.ecc := ecc && hit
     this.level := level
@@ -300,6 +307,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   // l3
   val l3Hit = if(EnableSv48) Some(Wire(Bool())) else None
   val l3HitPPN = if(EnableSv48) Some(Wire(UInt(ppnLen.W))) else None
+  val l3HitPbmt = if(EnableSv48) Some(Wire(UInt(ptePbmtLen.W))) else None
   val l3Pre = if(EnableSv48) Some(Wire(Bool())) else None
   val ptwl3replace = if(EnableSv48) Some(ReplacementPolicy.fromString(l2tlbParams.l3Replacer, l2tlbParams.l3Size)) else None
   if (EnableSv48) {
@@ -311,6 +319,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
     // stageDelay, but check for l3
     val hitPPN = DataHoldBypass(ParallelPriorityMux(hitVec zip l3.get.map(_.ppn)), stageDelay_valid_1cycle)
+    val hitPbmt = DataHoldBypass(ParallelPriorityMux(hitVec zip l3.get.map(_.pbmt)), stageDelay_valid_1cycle)
     val hitPre = DataHoldBypass(ParallelPriorityMux(hitVec zip l3.get.map(_.prefetch)), stageDelay_valid_1cycle)
     val hit = DataHoldBypass(ParallelOR(hitVec), stageDelay_valid_1cycle)
 
@@ -329,12 +338,13 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     // synchronize with other entries with RegEnable
     l3Hit.map(_ := RegEnable(hit, stageDelay(1).fire))
     l3HitPPN.map(_ := RegEnable(hitPPN, stageDelay(1).fire))
+    l3HitPbmt.map(_ := RegEnable(hitPbmt, stageDelay(1).fire))
     l3Pre.map(_ := RegEnable(hitPre, stageDelay(1).fire))
   }
 
   // l2
   val ptwl2replace = ReplacementPolicy.fromString(l2tlbParams.l2Replacer, l2tlbParams.l2Size)
-  val (l2Hit, l2HitPPN, l2Pre) = {
+  val (l2Hit, l2HitPPN, l2HitPbmt, l2Pre) = {
     val hitVecT = l2.zipWithIndex.map {
       case (e, i) => (e.hit(vpn_search, io.csr_dup(2).satp.asid, io.csr_dup(2).vsatp.asid, io.csr_dup(2).hgatp.vmid, s2xlate = h_search =/= noS2xlate)
         && l2v(i) && h_search === l2h(i))
@@ -343,6 +353,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
     // stageDelay, but check for l2
     val hitPPN = DataHoldBypass(ParallelPriorityMux(hitVec zip l2.map(_.ppn)), stageDelay_valid_1cycle)
+    val hitPbmt = DataHoldBypass(ParallelPriorityMux(hitVec zip l2.map(_.pbmt)), stageDelay_valid_1cycle)
     val hitPre = DataHoldBypass(ParallelPriorityMux(hitVec zip l2.map(_.prefetch)), stageDelay_valid_1cycle)
     val hit = DataHoldBypass(ParallelOR(hitVec), stageDelay_valid_1cycle)
 
@@ -361,12 +372,13 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     // synchronize with other entries with RegEnable
     (RegEnable(hit, stageDelay(1).fire),
      RegEnable(hitPPN, stageDelay(1).fire),
+     RegEnable(hitPbmt, stageDelay(1).fire),
      RegEnable(hitPre, stageDelay(1).fire))
   }
 
   // l1
   val ptwl1replace = ReplacementPolicy.fromString(l2tlbParams.l1Replacer,l2tlbParams.l1nWays,l2tlbParams.l1nSets)
-  val (l1Hit, l1HitPPN, l1Pre, l1eccError) = {
+  val (l1Hit, l1HitPPN, l1HitPbmt, l1Pre, l1eccError) = {
     val ridx = genPtwL1SetIdx(vpn_search)
     l1.io.r.req.valid := stageReq.fire
     l1.io.r.req.bits.apply(setIdx = ridx)
@@ -418,7 +430,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     }
     XSDebug(stageCheck_valid_1cycle, p"[l1] l1Hit:${hit} l1HitPPN:0x${Hexadecimal(hitWayData.ppns(genPtwL1SectorIdx(check_vpn)))} hitVec:${Binary(hitVec.asUInt)} hitWay:${hitWay} vidx:${vVec}\n")
 
-    (hit, hitWayData.ppns(genPtwL1SectorIdx(check_vpn)), hitWayData.prefetch, eccError)
+    (hit, hitWayData.ppns(genPtwL1SectorIdx(check_vpn)), hitWayData.pbmts(genPtwL1SectorIdx(check_vpn)), hitWayData.prefetch, eccError)
   }
 
   // l0
@@ -478,6 +490,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     (hit, hitWayData, hitWayData.prefetch, eccError)
   }
   val l0HitPPN = l0HitData.ppns
+  val l0HitPbmt = l0HitData.pbmts
   val l0HitPerm = l0HitData.perms.getOrElse(0.U.asTypeOf(Vec(PtwL0SectorSize, new PtePermBundle)))
   val l0HitValid = l0HitData.vs
   val l0HitAf = l0HitData.af
@@ -511,10 +524,10 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
   val check_res = Wire(new PageCacheRespBundle)
   check_res.l3.map(_.apply(l3Hit.get, l3Pre.get, l3HitPPN.get))
-  check_res.l2.apply(l2Hit, l2Pre, l2HitPPN)
-  check_res.l1.apply(l1Hit, l1Pre, l1HitPPN, ecc = l1eccError)
-  check_res.l0.apply(l0Hit, l0Pre, l0HitPPN, l0HitPerm, l0eccError, valid = l0HitValid, accessFault = l0HitAf)
-  check_res.sp.apply(spHit, spPre, spHitData.ppn, spHitPerm, false.B, spHitLevel, spValid)
+  check_res.l2.apply(l2Hit, l2Pre, l2HitPPN, l2HitPbmt)
+  check_res.l1.apply(l1Hit, l1Pre, l1HitPPN, l1HitPbmt, ecc = l1eccError)
+  check_res.l0.apply(l0Hit, l0Pre, l0HitPPN, l0HitPbmt, l0HitPerm, l0eccError, valid = l0HitValid, accessFault = l0HitAf)
+  check_res.sp.apply(spHit, spPre, spHitData.ppn, spHitData.pbmt, spHitPerm, false.B, spHitLevel, spValid)
 
   val resp_res = Reg(new PageCacheRespBundle)
   when (stageCheck(1).fire) { resp_res := check_res }
@@ -572,6 +585,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   io.resp.bits.toHptw.resp.entry.level.map(_ := Mux(resp_res.l0.hit, 0.U, resp_res.sp.level))
   io.resp.bits.toHptw.resp.entry.prefetch := from_pre(stageResp.bits.req_info.source)
   io.resp.bits.toHptw.resp.entry.ppn := Mux(resp_res.l0.hit, resp_res.l0.ppn(idx), resp_res.sp.ppn)(ppnLen - 1, 0)
+  io.resp.bits.toHptw.resp.entry.pbmt := Mux(resp_res.l0.hit, resp_res.l0.pbmt(idx), resp_res.sp.pbmt)
   io.resp.bits.toHptw.resp.entry.perm.map(_ := Mux(resp_res.l0.hit, resp_res.l0.perm(idx), resp_res.sp.perm))
   io.resp.bits.toHptw.resp.entry.v := Mux(resp_res.l0.hit, resp_res.l0.v(idx), resp_res.sp.v)
   io.resp.bits.toHptw.resp.gpf := !io.resp.bits.toHptw.resp.entry.v
@@ -622,6 +636,10 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
           Mux(resp_res.l1.hit, resp_res.l1.v,
             resp_res.l2.v)))
     }
+    io.resp.bits.stage1.entry(i).pbmt := Mux(resp_res.l0.hit, resp_res.l0.pbmt(i),
+      Mux(resp_res.sp.hit, resp_res.sp.pbmt,
+        Mux(resp_res.l1.hit, resp_res.l1.pbmt,
+          resp_res.l2.pbmt)))
     io.resp.bits.stage1.entry(i).perm.map(_ := Mux(resp_res.l0.hit, resp_res.l0.perm(i),  Mux(resp_res.sp.hit, resp_res.sp.perm, 0.U.asTypeOf(new PtePermBundle))))
     io.resp.bits.stage1.entry(i).pf := !io.resp.bits.stage1.entry(i).v
     io.resp.bits.stage1.entry(i).af := Mux(resp_res.l0.hit, resp_res.l0.af(i), false.B)
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 5ae37646e3d..d641613ef42 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -132,6 +132,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   val pmp_addr = readResult.map(_._3)
   val perm = readResult.map(_._4)
   val g_perm = readResult.map(_._5)
+  val pbmt = readResult.map(_._6)
+  val g_pbmt = readResult.map(_._7)
   // check pmp use paddr (for timing optization, use pmp_addr here)
   // check permisson
   (0 until Width).foreach{i =>
@@ -141,6 +143,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       pmp_check(pmp_addr(i), req_out(i).size, req_out(i).cmd, i)
     }
     for (d <- 0 until nRespDups) {
+      pbmt_check(i, d, pbmt(i)(d), g_pbmt(i)(d), req_out_s2xlate(i))
       perm_check(perm(i)(d), req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i))
     }
     hasGpf(i) := resp(i).bits.excp(0).gpf.ld || resp(i).bits.excp(0).gpf.st || resp(i).bits.excp(0).gpf.instr
@@ -158,8 +161,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
 
   /************************  main body above | method/log/perf below ****************************/
   def TLBRead(i: Int) = {
-    val (e_hit, e_ppn, e_perm, e_g_perm, e_s2xlate) = entries.io.r_resp_apply(i)
-    val (p_hit, p_ppn, p_perm, p_gvpn, p_g_perm, p_s2xlate) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
+    val (e_hit, e_ppn, e_perm, e_g_perm, e_s2xlate, e_pbmt, e_g_pbmt) = entries.io.r_resp_apply(i)
+    val (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate) = ptw_resp_bypass(get_pn(req_in(i).bits.vaddr), req_in_s2xlate(i))
     val enable = portTranslateEnable(i)
     val isOnlys2xlate = req_out_s2xlate(i) === onlyStage2
     val need_gpa_vpn_hit = need_gpa_vpn === get_pn(req_out(i).vaddr)
@@ -183,7 +186,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       need_gpa := false.B
     }
 
-    TimeOutAssert(need_gpa && !resp_gpa_refill, timeOutThreshold, s"port{i} need gpa long time not refill.")
+    TimeOutAssert(need_gpa && !resp_gpa_refill, timeOutThreshold, s"port${i} need gpa long time not refill.")
 
     val hit = e_hit || p_hit
     val miss = (!hit && enable) || hasGpf(i) && !p_hit && !(resp_gpa_refill && need_gpa_vpn_hit) && !isOnlys2xlate
@@ -196,14 +199,18 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     resp(i).bits.memidx := RegEnable(req_in(i).bits.memidx, req_in(i).valid)
 
     val ppn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ppnLen.W))))
+    val pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
     val perm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new TlbPermBundle))))
     val gvpn = WireInit(VecInit(Seq.fill(nRespDups)(0.U(vpnLen.W))))
+    val g_pbmt = WireInit(VecInit(Seq.fill(nRespDups)(0.U(ptePbmtLen.W))))
     val g_perm = WireInit(VecInit(Seq.fill(nRespDups)(0.U.asTypeOf(new TlbPermBundle))))
     val r_s2xlate = WireInit(VecInit(Seq.fill(nRespDups)(0.U(2.W))))
     for (d <- 0 until nRespDups) {
       ppn(d) := Mux(p_hit, p_ppn, e_ppn(d))
+      pbmt(d) := Mux(p_hit, p_pbmt, e_pbmt(d))
       perm(d) := Mux(p_hit, p_perm, e_perm(d))
       gvpn(d) :=  Mux(hasGpf(i), Mux(p_hit, p_gvpn, need_gpa_gvpn), 0.U)
+      g_pbmt(d) := Mux(p_hit, p_g_pbmt, e_g_pbmt(d))
       g_perm(d) := Mux(p_hit, p_g_perm, e_g_perm(d))
       r_s2xlate(d) := Mux(p_hit, p_s2xlate, e_s2xlate(d))
       val paddr = Cat(ppn(d), get_off(req_out(i).vaddr))
@@ -216,7 +223,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
 
     val pmp_paddr = resp(i).bits.paddr(0)
 
-    (hit, miss, pmp_paddr, perm, g_perm)
+    (hit, miss, pmp_paddr, perm, g_perm, pbmt, g_pbmt)
   }
 
   def pmp_check(addr: UInt, size: UInt, cmd: UInt, idx: Int): Unit = {
@@ -226,8 +233,17 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     pmp(idx).bits.cmd := cmd
   }
 
+  def pbmt_check(idx: Int, d: Int, pbmt: UInt, g_pbmt: UInt, s2xlate: UInt):Unit = {
+    val onlyS1 = s2xlate === onlyStage1 || s2xlate === noS2xlate
+    resp(idx).bits.pbmt(d) := Mux(
+      portTranslateEnable(idx),
+      Mux(onlyS1, pbmt, g_pbmt),
+      0.U
+    )
+  }
+
+  // for timing optimization, pmp check is divided into dynamic and static
   def perm_check(perm: TlbPermBundle, cmd: UInt, idx: Int, nDups: Int, g_perm: TlbPermBundle, hlvx: Bool, s2xlate: UInt) = {
-    // for timing optimization, pmp check is divided into dynamic and static
     // dynamic: superpage (or full-connected reg entries) -> check pmp when translation done
     // static: 4K pages (or sram entries) -> check pmp with pre-checked results
     val hasS2xlate = s2xlate =/= noS2xlate
@@ -358,6 +374,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       for (d <- 0 until nRespDups) {
         resp(idx).bits.paddr(d) := Mux(s2xlate =/= noS2xlate, s2_paddr, s1_paddr)
         resp(idx).bits.gpaddr(d) := s1_paddr
+        pbmt_check(idx, d, io.ptw.resp.bits.s1.entry.pbmt, io.ptw.resp.bits.s2.entry.pbmt, s2xlate)
         perm_check(stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
       }
       pmp_check(resp(idx).bits.paddr(0), req_out(idx).size, req_out(idx).cmd, idx)
@@ -383,6 +400,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       when (req_out_v(idx) && flush_pipe(idx) && portTranslateEnable(idx)) {
         resp(idx).valid := true.B
         for (d <- 0 until nRespDups) {
+          resp(idx).bits.pbmt(d) := 0.U
           resp(idx).bits.excp(d).pf.ld := true.B // sfence happened, pf for not to use this addr
           resp(idx).bits.excp(d).pf.st := true.B
           resp(idx).bits.excp(d).pf.instr := true.B
@@ -405,11 +423,13 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val gvpn = Mux(onlyS2, vpn, ppn_s1)
     val ppn_s2 = ptw.resp.bits.s2.genPPNS2(gvpn)
     val p_ppn = RegEnable(Mux(hasS2xlate, ppn_s2, ppn_s1), io.ptw.resp.fire)
+    val p_pbmt = RegEnable(ptw.resp.bits.s1.entry.pbmt,io.ptw.resp.fire)
     val p_perm = RegEnable(ptwresp_to_tlbperm(ptw.resp.bits.s1), io.ptw.resp.fire)
     val p_gvpn = RegEnable(Mux(onlyS2, ptw.resp.bits.s2.entry.tag, ppn_s1), io.ptw.resp.fire)
+    val p_g_pbmt = RegEnable(ptw.resp.bits.s2.entry.pbmt,io.ptw.resp.fire)
     val p_g_perm = RegEnable(hptwresp_to_tlbperm(ptw.resp.bits.s2), io.ptw.resp.fire)
     val p_s2xlate = RegEnable(ptw.resp.bits.s2xlate, io.ptw.resp.fire)
-    (p_hit, p_ppn, p_perm, p_gvpn, p_g_perm, p_s2xlate)
+    (p_hit, p_ppn, p_pbmt, p_perm, p_gvpn, p_g_pbmt, p_g_perm, p_s2xlate)
   }
 
   // assert
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index 9e023737b74..5ffe8688396 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -129,12 +129,16 @@ class TLBFA(
     resp.valid := GatedValidRegNext(req.valid)
     resp.bits.hit := Cat(hitVecReg).orR
     val ppnReg   = RegEnable(VecInit(entries.map(_.genPPN(saveLevel, req.valid)(vpn))), req.fire)
+    val pbmtReg  = RegEnable(VecInit(entries.map(_.pbmt)), req.fire)
+    val gpbmtReg  = RegEnable(VecInit(entries.map(_.g_pbmt)), req.fire)
     val permReg  = RegEnable(VecInit(entries.map(_.perm)), req.fire)
     val gPermReg = RegEnable(VecInit(entries.map(_.g_perm)), req.fire)
     val s2xLate  = RegEnable(VecInit(entries.map(_.s2xlate)), req.fire)
     if (nWays == 1) {
       for (d <- 0 until nDups) {
         resp.bits.ppn(d) := ppnReg(0)
+        resp.bits.pbmt(d) := pbmtReg(0)
+        resp.bits.g_pbmt(d) := gpbmtReg(0)
         resp.bits.perm(d) := permReg(0)
         resp.bits.g_perm(d) := gPermReg(0)
         resp.bits.s2xlate(d) := s2xLate(0)
@@ -142,6 +146,8 @@ class TLBFA(
     } else {
       for (d <- 0 until nDups) {
         resp.bits.ppn(d) := Mux1H(hitVecReg zip ppnReg)
+        resp.bits.pbmt(d) := Mux1H(hitVecReg zip pbmtReg)
+        resp.bits.g_pbmt(d) := Mux1H(hitVecReg zip gpbmtReg)
         resp.bits.perm(d) := Mux1H(hitVecReg zip permReg)
         resp.bits.g_perm(d) := Mux1H(hitVecReg zip gPermReg)
         resp.bits.s2xlate(d) := Mux1H(hitVecReg zip s2xLate)
@@ -154,6 +160,8 @@ class TLBFA(
 
     resp.bits.hit.suggestName("hit")
     resp.bits.ppn.suggestName("ppn")
+    resp.bits.pbmt.suggestName("pbmt")
+    resp.bits.g_pbmt.suggestName("g_pbmt")
     resp.bits.perm.suggestName("perm")
     resp.bits.g_perm.suggestName("g_perm")
   }
@@ -300,7 +308,7 @@ class TLBFakeFA(
       resp.bits.perm(d).x := pte.perm.x
       resp.bits.perm(d).w := pte.perm.w
       resp.bits.perm(d).r := pte.perm.r
-
+      resp.bits.pbmt(d) := pte.pbmt
       resp.bits.ppn(d) := MuxLookup(level, 0.U)(Seq(
         0.U -> Cat(ppn(ppn.getWidth-1, vpnnLen*2), vpn_reg(vpnnLen*2-1, 0)),
         1.U -> Cat(ppn(ppn.getWidth-1, vpnnLen), vpn_reg(vpnnLen-1, 0)),
@@ -385,6 +393,8 @@ class TlbStorageWrapper(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit p
       rp.bits.perm(d).r := p.bits.perm(d).r
       rp.bits.s2xlate(d) := p.bits.s2xlate(d)
       rp.bits.g_perm(d) := p.bits.g_perm(d)
+      rp.bits.pbmt(d) := p.bits.pbmt(d)
+      rp.bits.g_pbmt(d) := p.bits.g_pbmt(d)
     }
   }
 
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index d4e621b8d9b..6b6a56274e1 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -380,8 +380,16 @@ class NewIFU(implicit p: Parameters) extends XSModule
   // paddr and gpaddr of [startAddr, nextLineAddr]
   val f2_paddrs       = VecInit((0 until PortNumber).map(i => fromICache(i).bits.paddr))
   val f2_gpaddr       = fromICache(0).bits.gpaddr
+
+  // FIXME: what if port 0 is not mmio, but port 1 is?
   // cancel mmio fetch if exception occurs
-  val f2_mmio         = fromICache(0).bits.mmio && f2_exception(0) === ExceptionType.none
+  val f2_mmio         = f2_exception(0) === ExceptionType.none && (
+    fromICache(0).bits.pmp_mmio ||
+      // currently, we do not distinguish between Pbmt.nc and Pbmt.io
+      // anyway, they are both non-cacheable, and should be handled with mmio fsm and sent to Uncache module
+      Pbmt.isUncache(fromICache(0).bits.itlb_pbmt)
+  )
+
 
   /**
     * reduce the number of registers, origin code
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 20eade514e4..22506df5cb8 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -41,7 +41,8 @@ class ICacheMainPipeResp(implicit p: Parameters) extends ICacheBundle
   val paddr    = UInt(PAddrBits.W)
   val gpaddr    = UInt(GPAddrBits.W)
   val exception = UInt(ExceptionType.width.W)
-  val mmio      = Bool()
+  val pmp_mmio  = Bool()
+  val itlb_pbmt = UInt(Pbmt.width.W)
 }
 
 class ICacheMainPipeBundle(implicit p: Parameters) extends ICacheBundle
@@ -173,6 +174,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   val s0_req_ptags      = fromWayLookup.bits.ptag
   val s0_req_gpaddr     = fromWayLookup.bits.gpaddr
   val s0_itlb_exception = fromWayLookup.bits.itlb_exception
+  val s0_itlb_pbmt      = fromWayLookup.bits.itlb_pbmt
   val s0_meta_corrupt   = fromWayLookup.bits.meta_corrupt
   val s0_hits           = VecInit(fromWayLookup.bits.waymask.map(_.orR))
 
@@ -217,6 +219,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   val s1_doubleline     = RegEnable(s0_doubleline,     0.U.asTypeOf(s0_doubleline),     s0_fire)
   val s1_SRAMhits       = RegEnable(s0_hits,           0.U.asTypeOf(s0_hits),           s0_fire)
   val s1_itlb_exception = RegEnable(s0_itlb_exception, 0.U.asTypeOf(s0_itlb_exception), s0_fire)
+  val s1_itlb_pbmt      = RegEnable(s0_itlb_pbmt,      0.U.asTypeOf(s0_itlb_pbmt),      s0_fire)
   val s1_waymasks       = RegEnable(s0_waymasks,       0.U.asTypeOf(s0_waymasks),       s0_fire)
   val s1_meta_corrupt   = RegEnable(s0_meta_corrupt,   0.U.asTypeOf(s0_meta_corrupt),   s0_fire)
 
@@ -249,7 +252,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     p.bits.cmd  := TlbCmd.exec
   }
   val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
-  val s1_mmio          = VecInit(fromPMP.map(_.mmio))
+  val s1_pmp_mmio      = VecInit(fromPMP.map(_.mmio))
 
   // also raise af when meta array corrupt is detected, to cancel fetch
   val s1_meta_exception = VecInit(s1_meta_corrupt.map(ExceptionType.fromECC(io.csr_parity_enable, _)))
@@ -261,6 +264,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     s1_meta_exception
   )
 
+  // DO NOT merge pmp mmio and itlb pbmt here, we need them to be passed to IFU separately
+
   /**
     ******************************************************************************
     * select data from MSHR, SRAM
@@ -302,7 +307,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   val s2_req_gpaddr   = RegEnable(s1_req_gpaddr,    0.U.asTypeOf(s1_req_gpaddr),    s1_fire)
   val s2_doubleline   = RegEnable(s1_doubleline,    0.U.asTypeOf(s1_doubleline),    s1_fire)
   val s2_exception    = RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_fire)  // includes itlb/pmp/meta exception
-  val s2_mmio         = RegEnable(s1_mmio,          0.U.asTypeOf(s1_mmio),          s1_fire)
+  val s2_pmp_mmio     = RegEnable(s1_pmp_mmio,      0.U.asTypeOf(s1_pmp_mmio),      s1_fire)
+  val s2_itlb_pbmt    = RegEnable(s1_itlb_pbmt,     0.U.asTypeOf(s1_itlb_pbmt),     s1_fire)
 
   val s2_req_vSetIdx  = s2_req_vaddr.map(get_idx)
   val s2_req_offset   = s2_req_vaddr(0)(log2Ceil(blockBytes)-1, 0)
@@ -389,6 +395,12 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * send request to MSHR if ICache miss
     ******************************************************************************
     */
+
+  // merge pmp mmio and itlb pbmt
+  val s2_mmio = VecInit((s2_pmp_mmio zip s2_itlb_pbmt).map{ case (mmio, pbmt) =>
+    mmio || Pbmt.isUncache(pbmt)
+  })
+
   /* s2_exception includes itlb pf/gpf/af, pmp af and meta corruption (af), neither of which should be fetched
    * mmio should not be fetched, it will be fetched by IFU mmio fsm
    * also, if previous has exception, latter port should also not be fetched
@@ -442,12 +454,14 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     if(i == 0) {
       toIFU(i).valid          := s2_fire
       toIFU(i).bits.exception := s2_exception_out(i)
-      toIFU(i).bits.mmio      := s2_mmio(i)
+      toIFU(i).bits.pmp_mmio  := s2_pmp_mmio(i)   // pass pmp_mmio instead of merged mmio to IFU
+      toIFU(i).bits.itlb_pbmt := s2_itlb_pbmt(i)
       toIFU(i).bits.data      := s2_datas.asTypeOf(UInt(blockBits.W))
     } else {
       toIFU(i).valid          := s2_fire && s2_doubleline
       toIFU(i).bits.exception := Mux(s2_doubleline, s2_exception_out(i), ExceptionType.none)
-      toIFU(i).bits.mmio      := s2_mmio(i) && s2_doubleline
+      toIFU(i).bits.pmp_mmio  := s2_pmp_mmio(i) && s2_doubleline
+      toIFU(i).bits.itlb_pbmt := Mux(s2_doubleline, s2_itlb_pbmt(i), Pbmt.pma)
       toIFU(i).bits.data      := DontCare
     }
     toIFU(i).bits.vaddr       := s2_req_vaddr(i)
@@ -530,7 +544,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     */
   if (env.EnableDifftest) {
     val discards = (0 until PortNumber).map { i =>
-      val discard = toIFU(i).bits.exception =/= ExceptionType.none || toIFU(i).bits.mmio
+      val discard = toIFU(i).bits.exception =/= ExceptionType.none || toIFU(i).bits.pmp_mmio ||
+        Pbmt.isUncache(toIFU(i).bits.itlb_pbmt)
       discard
     }
     val blkPaddrAll = s2_req_paddr.map(addr => addr(PAddrBits - 1, blockOffBits) << blockOffBits)
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 5497a396f72..23cdab103bd 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -196,6 +196,9 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   val s1_itlb_exception     = VecInit((0 until PortNumber).map( i =>
     ResultHoldBypass(valid = tlb_valid_pulse(i), init = 0.U(ExceptionType.width.W), data = ExceptionType.fromTlbResp(fromITLB(i).bits))
   ))
+  val s1_itlb_pbmt          = VecInit((0 until PortNumber).map( i =>
+    ResultHoldBypass(valid = tlb_valid_pulse(i), init = 0.U.asTypeOf(fromITLB(i).bits.pbmt(0)), data = fromITLB(i).bits.pbmt(0))
+  ))
   val s1_itlb_exception_gpf = VecInit(s1_itlb_exception.map(_ === ExceptionType.gpf))
 
   /* Select gpaddr with the first gpf
@@ -293,6 +296,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     val excpValid = (if (i == 0) true.B else s1_doubleline)  // exception in first line is always valid, in second line is valid iff is doubleline request
     // Send s1_itlb_exception to WayLookup (instead of s1_exception_out) for better timing. Will check pmp again in mainPipe
     toWayLookup.bits.itlb_exception(i) := Mux(excpValid, s1_itlb_exception(i), ExceptionType.none)
+    toWayLookup.bits.itlb_pbmt(i)      := Mux(excpValid, s1_itlb_pbmt(i), Pbmt.pma)
     toWayLookup.bits.meta_corrupt(i)   := excpValid && s1_meta_corrupt(i)
   }
 
@@ -317,7 +321,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     p.bits.cmd  := TlbCmd.exec
   }
   val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
-  val s1_mmio          = VecInit(fromPMP.map(_.mmio))
+  val s1_pmp_mmio      = VecInit(fromPMP.map(_.mmio))
 
   // also raise af when meta array corrupt is detected, to cancel prefetch
   val s1_meta_exception = VecInit(s1_meta_corrupt.map(ExceptionType.fromECC(io.csr_parity_enable, _)))
@@ -329,6 +333,11 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     s1_meta_exception
   )
 
+  // merge pmp mmio and itlb pbmt
+  val s1_mmio = VecInit((s1_pmp_mmio zip s1_itlb_pbmt).map{ case (mmio, pbmt) =>
+    mmio || Pbmt.isUncache(pbmt)
+  })
+
   /**
     ******************************************************************************
     * state machine
diff --git a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
index 5b08bde8129..7b29be57c19 100644
--- a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
@@ -21,6 +21,7 @@ import chisel3._
 import chisel3.util._
 import utility._
 import xiangshan.frontend.ExceptionType
+import xiangshan.cache.mmu.Pbmt
 
 /* WayLookupEntry is for internal storage, while WayLookupInfo is for interface
  * Notes:
@@ -33,6 +34,7 @@ class WayLookupEntry(implicit p: Parameters) extends ICacheBundle {
   val waymask        : Vec[UInt] = Vec(PortNumber, UInt(nWays.W))
   val ptag           : Vec[UInt] = Vec(PortNumber, UInt(tagBits.W))
   val itlb_exception : Vec[UInt] = Vec(PortNumber, UInt(ExceptionType.width.W))
+  val itlb_pbmt      : Vec[UInt] = Vec(PortNumber, UInt(Pbmt.width.W))
   val meta_corrupt   : Vec[Bool] = Vec(PortNumber, Bool())
 }
 
@@ -49,27 +51,11 @@ class WayLookupInfo(implicit p: Parameters) extends ICacheBundle {
   def waymask        : Vec[UInt] = entry.waymask
   def ptag           : Vec[UInt] = entry.ptag
   def itlb_exception : Vec[UInt] = entry.itlb_exception
+  def itlb_pbmt      : Vec[UInt] = entry.itlb_pbmt
   def meta_corrupt   : Vec[Bool] = entry.meta_corrupt
   def gpaddr         : UInt      = gpf.gpaddr
 }
 
-
-// class WayLookupRead(implicit p: Parameters) extends ICacheBundle {
-//   val vSetIdx     = Vec(PortNumber, UInt(idxBits.W))
-//   val waymask     = Vec(PortNumber, UInt(nWays.W))
-//   val ptag        = Vec(PortNumber, UInt(tagBits.W))
-//   val excp_tlb_af = Vec(PortNumber, Bool())
-//   val excp_tlb_pf = Vec(PortNumber, Bool())
-// }
-
-// class WayLookupWrite(implicit p: Parameters) extends ICacheBundle {
-//   val vSetIdx       = Vec(PortNumber, UInt(idxBits.W))
-//   val ptag          = Vec(PortNumber, UInt(tagBits.W))
-//   val waymask       = Vec(PortNumber, UInt(nWays.W))
-//   val excp_tlb_af   = Vec(PortNumber, Bool())
-//   val excp_tlb_pf   = Vec(PortNumber, Bool())
-// }
-
 class WayLookupInterface(implicit p: Parameters) extends ICacheBundle {
   val flush   = Input(Bool())
   val read    = DecoupledIO(new WayLookupInfo)
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 7ce579ec226..854a773c9d1 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -865,6 +865,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s1_paddr_dup_dcache = Wire(UInt())
   val s1_exception        = ExceptionNO.selectByFu(s1_out.uop.exceptionVec, LduCfg).asUInt.orR   // af & pf exception were modified below.
   val s1_tlb_miss         = io.tlb.resp.bits.miss && io.tlb.resp.valid && s1_valid
+  val s1_pbmt             = Mux(io.tlb.resp.valid, io.tlb.resp.bits.pbmt(0), 0.U(2.W))
   val s1_prf              = s1_in.isPrefetch
   val s1_hw_prf           = s1_in.isHWPrefetch
   val s1_sw_prf           = s1_prf && !s1_hw_prf
@@ -1040,6 +1041,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s2_data_select  = genRdataOH(s2_out.uop)
   val s2_data_select_by_offset = genDataSelectByOffset(s2_out.paddr(3, 0))
   val s2_frm_mabuf = s2_in.isFrmMisAlignBuf
+  val s2_pbmt = RegEnable(s1_pbmt, s1_fire)
 
   s2_kill := s2_in.uop.robIdx.needFlush(io.redirect)
   s2_ready := !s2_valid || s2_kill || s3_ready
@@ -1079,7 +1081,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   // writeback access fault caused by ecc error / bus error
   // * ecc data error is slow to generate, so we will not use it until load stage 3
   // * in load stage 3, an extra signal io.load_error will be used to
-  val s2_actually_mmio = s2_pmp.mmio
+  val s2_actually_mmio = s2_pmp.mmio || Pbmt.isUncache(s2_pbmt)
   val s2_mmio          = !s2_prf &&
                           s2_actually_mmio &&
                          !s2_exception &&
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index f9148b65172..7ce6772979a 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -29,7 +29,7 @@ import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.fu.FuType._
 import xiangshan.backend.ctrlblock.DebugLsInfoBundle
 import xiangshan.backend.fu.NewCSR._
-import xiangshan.cache.mmu.{TlbCmd, TlbReq, TlbRequestIO, TlbResp}
+import xiangshan.cache.mmu.{TlbCmd, TlbReq, TlbRequestIO, TlbResp, Pbmt}
 import xiangshan.cache.{DcacheStoreRequestIO, DCacheStoreIO, MemoryOpConstants, HasDCacheParameters, StorePrefetchReq}
 
 class StoreUnit(implicit p: Parameters) extends XSModule
@@ -247,6 +247,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s1_gpaddr    = io.tlb.resp.bits.gpaddr(0)
   val s1_tlb_miss  = io.tlb.resp.bits.miss
   val s1_mmio      = s1_mmio_cbo
+  val s1_pbmt      = io.tlb.resp.bits.pbmt(0)
   val s1_exception = ExceptionNO.selectByFu(s1_out.uop.exceptionVec, StaCfg).asUInt.orR
   val s1_isvec     = RegEnable(s0_out.isvec, false.B, s0_fire)
   // val s1_isLastElem = RegEnable(s0_isLastElem, false.B, s0_fire)
@@ -349,6 +350,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s2_vecActive    = RegEnable(s1_out.vecActive, true.B, s1_fire)
   val s2_mis_align    = s2_in.uop.exceptionVec(storeAddrMisaligned) && io.csrCtrl.hd_misalign_st_enable
   val s2_frm_mabuf    = s2_in.isFrmMisAlignBuf
+  val s2_pbmt   = RegEnable(s1_pbmt, s1_fire)
 
   s2_ready := !s2_valid || s2_kill || s3_ready
   when (s1_fire) { s2_valid := true.B }
@@ -358,7 +360,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
   val s2_pmp = WireInit(io.pmp)
 
   val s2_exception = (ExceptionNO.selectByFu(s2_out.uop.exceptionVec, StaCfg).asUInt.orR) && RegNext(s1_feedback.bits.hit)
-  val s2_mmio = (s2_in.mmio || s2_pmp.mmio) && RegNext(s1_feedback.bits.hit)
+  val s2_mmio = (s2_in.mmio || s2_pmp.mmio || Pbmt.isUncache(s2_pbmt)) && RegNext(s1_feedback.bits.hit)
   s2_kill := ((s2_mmio && !s2_exception) && !s2_in.isvec) || s2_in.uop.robIdx.needFlush(io.redirect)
 
   s2_out        := s2_in
```
