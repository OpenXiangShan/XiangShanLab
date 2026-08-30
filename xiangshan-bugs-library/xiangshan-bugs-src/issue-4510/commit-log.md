# Commit Log
- Issue: #4510
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4510
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4510
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4510
- Changed files: 1
- Additions: 2
- Deletions: 3

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 8d3e603c69a..92f7f602bec 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -696,9 +696,6 @@ class LLPTWEntry(implicit p: Parameters) extends XSBundle with HasPtwConst {
 
 class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPerfEvents {
   val io = IO(new LLPTWIO())
-  val enableS2xlate = io.in.bits.req_info.s2xlate =/= noS2xlate
-  val satp = Mux(enableS2xlate, io.csr.vsatp, io.csr.satp)
-  val s1Pbmte = Mux(enableS2xlate, io.csr.hPBMTE, io.csr.mPBMTE)
 
   // mbmc:bitmap csr
   val mbmc = io.csr.mbmc
@@ -884,6 +881,8 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         val req_paddr = MakeAddr(entries(i).ppn, getVpnn(entries(i).req_info.vpn, 0))
         val req_hpaddr = MakeAddr(entries(i).hptw_resp.genPPNS2(get_pn(req_paddr)), getVpnn(entries(i).req_info.vpn, 0))
         val index =  Mux(entries(i).req_info.s2xlate === allStage, req_hpaddr, req_paddr)(log2Up(l2tlbParams.blockBytes)-1, log2Up(XLEN/8))
+        val enableS2xlate = entries(i).req_info.s2xlate =/= noS2xlate
+        val s1Pbmte = Mux(enableS2xlate, io.csr.hPBMTE, io.csr.mPBMTE)
         val vsStagePf = ptes(index).isPf(0.U, s1Pbmte) || !ptes(index).isLeaf() // Pagefault in vs-Stage
         // Pagefault in g-Stage; when vsStagePf valid, should not check gStagepf
         val gStagePf = ptes(index).isStage1Gpf(io.csr.hgatp.mode) && !vsStagePf
```
