# Commit Log
- Issue: #3871
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3871
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3871
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3871
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 47075844256..a375797fab8 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -275,7 +275,9 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       // When load 1 trigger a guest page fault, we should use offset of fullva when generate gpaddr
       // and when load 2 trigger a guest page fault, we should just use offset of vaddr(all zero).
       // Also, when onlyS2, if crosspage, gpaddr = vaddr(start address of a new page), else gpaddr = fullva(original vaddr)
-      val crossPageVaddr = Mux(req_out(i).fullva(12) =/= vaddr(12), vaddr, req_out(i).fullva)
+      // By the way, frontend handles the cross page instruction fetch by itself, so TLB doesn't need to do anything extra.
+      // Also, the fullva of iTLB is not used and always zero. crossPageVaddr should never use fullva in iTLB.
+      val crossPageVaddr = Mux(isitlb || req_out(i).fullva(12) =/= vaddr(12), vaddr, req_out(i).fullva)
       val gpaddr_offset = Mux(isLeaf(d), get_off(crossPageVaddr), Cat(getVpnn(get_pn(crossPageVaddr), vpn_idx), 0.U(log2Up(XLEN/8).W)))
       val gpaddr = Cat(gvpn(d), gpaddr_offset)
       resp(i).bits.paddr(d) := Mux(enable, paddr, vaddr)
```
