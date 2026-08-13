# Commit Log
- Issue: #4793
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4793
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4793
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4793
- Changed files: 1
- Additions: 14
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 8c07899bde8..cc90252fd24 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -574,9 +574,22 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       val s2_ppn = stage2.genPPNS2(s2_gvpn)(ppnLen - 1, 0)
       val s1_paddr = Cat(s1_ppn, get_off(req_out(idx).vaddr))
       val s2_paddr = Cat(s2_ppn, get_off(req_out(idx).vaddr))
+
+      // This block of code is used to generate the gpaddr in allStage, for:
+      //   1. G-for-VS-Stage failed (isFakePte || isNonLeaf)
+      //   2. Normal G-Stage failed
+      // Similar to the code in TLBRead.
+      val vpn_idx = Mux1H(Seq(
+        (stage1.isFakePte() && csr.vsatp.mode === Sv39) -> 2.U,
+        (stage1.isFakePte() && csr.vsatp.mode === Sv48) -> 3.U,
+        (!stage1.isFakePte()) -> (stage1.entry.level.get - 1.U),
+      ))
+      val s1_gpaddr_offset = Mux(stage1.isLeaf(), get_off(req_out(idx).vaddr), Cat(getVpnn(get_pn(req_out(idx).vaddr), vpn_idx), 0.U(log2Up(XLEN/8).W)))
+      val s1_gpaddr = Cat(stage1.genGVPN(vpn), s1_gpaddr_offset)
+      
       for (d <- 0 until nRespDups) {
         resp(idx).bits.paddr(d) := Mux(s2xlate === onlyStage2 || s2xlate === allStage, s2_paddr, s1_paddr)
-        resp(idx).bits.gpaddr(d) := s1_paddr
+        resp(idx).bits.gpaddr(d) := Mux(s2xlate === onlyStage2, req_out(idx).vaddr, s1_gpaddr)
         pbmt_check(idx, d, io.ptw.resp.bits.s1.entry.pbmt, io.ptw.resp.bits.s2.entry.pbmt, s2xlate)
         perm_check(stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
       }
```
