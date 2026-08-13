# Commit Log
- Issue: #5331
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5331
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5331
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5331
- Changed files: 2
- Additions: 8
- Deletions: 4

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 2041a444f07..63122c8d7c8 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -133,7 +133,10 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     (Sv39vsEnable || Sv48vsEnable || Sv39x4Enable || Sv48x4Enable) &&
     (mode(i) < ModeM)
   )
-  val portTranslateEnable = (0 until Width).map(i => (vmEnable(i) || s2xlateEnable(i)) && RegEnable(!req(i).bits.no_translate, req(i).valid))
+
+  val useReqS1Paddr = (0 until Width).map(i => RegNext(req(i).bits.no_translate))
+  val privNeedTranslate = (0 until Width).map(i => (vmEnable(i) || s2xlateEnable(i)))
+  val portTranslateEnable = (0 until Width).map(i => privNeedTranslate(i) && !useReqS1Paddr(i))
 
   // pre fault: check fault before real do translate
   val prepf = WireInit(VecInit(Seq.fill(Width)(false.B)))
@@ -315,6 +318,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     miss.suggestName(s"miss_read_${i}")
 
     val vaddr = SignExt(req_out(i).vaddr, PAddrBits)
+    val notTranslatePaddr = Mux(useReqS1Paddr(i), req_in(i).bits.pmp_addr, SignExt(req_out(i).vaddr, PAddrBits))
     resp(i).bits.miss := miss
     resp(i).bits.ptwBack := ptw.resp.fire
     resp(i).bits.memidx := RegEnable(req_in(i).bits.memidx, req_in(i).valid)
@@ -359,7 +363,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       val crossPageVaddr = Mux(isitlb || req_out(i).fullva(12) =/= vaddr(12), req_out(i).vaddr, req_out(i).fullva)
       val gpaddr_offset = Mux(isLeaf(d), get_off(crossPageVaddr), Cat(getVpnn(get_pn(crossPageVaddr), vpn_idx), 0.U(log2Up(XLEN/8).W)))
       val gpaddr = Cat(gvpn(d), gpaddr_offset)
-      resp(i).bits.paddr(d) := Mux(enable, paddr, vaddr)
+      resp(i).bits.paddr(d) := Mux(enable, paddr, notTranslatePaddr)
       resp(i).bits.gpaddr(d) := Mux(r_s2xlate(d) === onlyStage2, crossPageVaddr, gpaddr)
     }
 
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 8f444063835..224b4274c8f 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -936,8 +936,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   s1_vaddr_hi         := s1_in.vaddr(VAddrBits - 1, 6)
   s1_vaddr_lo         := s1_in.vaddr(5, 0)
   s1_vaddr            := Cat(s1_vaddr_hi, s1_vaddr_lo)
-  s1_paddr_dup_lsu    := Mux(s1_in.tlbNoQuery, s1_in.paddr, io.tlb.resp.bits.paddr(0))
-  s1_paddr_dup_dcache := Mux(s1_in.tlbNoQuery, s1_in.paddr, io.tlb.resp.bits.paddr(1))
+  s1_paddr_dup_lsu    := io.tlb.resp.bits.paddr(0)
+  s1_paddr_dup_dcache := io.tlb.resp.bits.paddr(1)
   s1_gpaddr_dup_lsu   := Mux(s1_in.isFastReplay, s1_in.paddr, io.tlb.resp.bits.gpaddr(0))
 
   when (s1_tlb_memidx.is_ld && io.tlb.resp.valid && !s1_tlb_miss && s1_tlb_memidx.idx === s1_in.uop.lqIdx.value) {
```
