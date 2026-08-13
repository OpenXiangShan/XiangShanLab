# Commit Log
- Issue: #4472
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4472
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4472
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4472
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 7119bec3d2b..3e73a566652 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -215,7 +215,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     0.U(offLen.W))
   ))
   val gvpn_gpf =
-    !(hptw_pageFault || hptw_accessFault ) &&
+    !(hptw_pageFault || hptw_accessFault || ((pageFault || ppn_af) && pte_valid)) &&
     Mux(
       s2xlate && io.csr.hgatp.mode === Sv39x4,
       full_gvpn(ptePPNLen - 1, GPAddrBitsSv39x4 - offLen) =/= 0.U,
```
