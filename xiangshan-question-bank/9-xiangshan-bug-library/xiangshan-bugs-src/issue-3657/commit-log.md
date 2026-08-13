# Commit Log
- Issue: #3657
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3657
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3657
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3657
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 9a37d904ad6..9042320f49a 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -174,7 +174,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     ))),
     0.U(offLen.W))
   ))
-  val gvpn_gpf = Mux(s2xlate && io.csr.hgatp.mode === Sv39x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(s2xlate && io.csr.hgatp.mode === Sv48x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
+  val gvpn_gpf = !(hptw_pageFault || hptw_accessFault ) && Mux(s2xlate && io.csr.hgatp.mode === Sv39x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(s2xlate && io.csr.hgatp.mode === Sv48x4, gpaddr(gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
   val guestFault = hptw_pageFault || hptw_accessFault || gvpn_gpf
   val hpaddr = Cat(hptw_resp.genPPNS2(get_pn(gpaddr)), get_off(gpaddr))
   val fake_h_resp = 0.U.asTypeOf(new HptwResp)
```
