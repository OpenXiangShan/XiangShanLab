# Commit Log
- Issue: #4658
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4658
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4658
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4658
- Changed files: 1
- Additions: 6
- Deletions: 4

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index c3adbe449b6..c68d7d6433a 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -572,10 +572,12 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       val s2xlate = io.ptw.resp.bits.s2xlate
       resp(idx).valid := true.B
       resp(idx).bits.miss := false.B
-      val s1_ppn = stage1.genPPN(get_pn(req_out(idx).vaddr))(ppnLen - 1, 0)
-      val s2_ppn = stage2.genPPNS2(get_pn(req_out(idx).vaddr))(ppnLen - 1, 0)
-      val s1_paddr = Cat(s1_ppn, get_off(req_out(idx).vaddr))
-      val s2_paddr = Cat(s2_ppn, get_off(req_out(idx).vaddr))
+      val vpn = get_pn(req_out(idx).vaddr)
+      val s1_ppn = stage1.genPPN(vpn)(ppnLen - 1, 0)
+      val s2_gvpn = Mux(s2xlate === onlyStage2, vpn, s1_ppn)
+      val s2_ppn = stage2.genPPNS2(s2_gvpn)(ppnLen - 1, 0)
+      val s1_paddr = Cat(s1_ppn, get_off(vpn))
+      val s2_paddr = Cat(s2_ppn, get_off(vpn))
       for (d <- 0 until nRespDups) {
         resp(idx).bits.paddr(d) := Mux(s2xlate === onlyStage2 || s2xlate === allStage, s2_paddr, s1_paddr)
         resp(idx).bits.gpaddr(d) := s1_paddr
```
