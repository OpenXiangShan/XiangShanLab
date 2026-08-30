# Commit Log
- Issue: #4588
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4588
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4588
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4588
- Changed files: 1
- Additions: 4
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index dc3b0e1a51d..feb34f8db90 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -572,8 +572,10 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       val s2xlate = io.ptw.resp.bits.s2xlate
       resp(idx).valid := true.B
       resp(idx).bits.miss := false.B
-      val s1_paddr = Cat(stage1.genPPN(get_pn(req_out(idx).vaddr)), get_off(req_out(idx).vaddr))
-      val s2_paddr = Cat(stage2.genPPNS2(get_pn(req_out(idx).vaddr)), get_off(req_out(idx).vaddr))
+      val s1_ppn = stage1.genPPN(get_pn(req_out(idx).vaddr))(ppnLen - 1, 0)
+      val s2_ppn = stage2.genPPNS2(get_pn(req_out(idx).vaddr))(ppnLen - 1, 0)
+      val s1_paddr = Cat(s1_ppn, get_off(req_out(idx).vaddr))
+      val s2_paddr = Cat(s2_ppn, get_off(req_out(idx).vaddr))
       for (d <- 0 until nRespDups) {
         resp(idx).bits.paddr(d) := Mux(s2xlate =/= noS2xlate, s2_paddr, s1_paddr)
         resp(idx).bits.gpaddr(d) := s1_paddr
```
