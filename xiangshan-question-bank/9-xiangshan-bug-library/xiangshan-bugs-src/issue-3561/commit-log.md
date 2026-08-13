# Commit Log
- Issue: #3561
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3561
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3561
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3561
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 43db4b34831..d95a0d8412a 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -301,7 +301,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
 
   when(io.hptw.resp.fire && w_hptw_resp === false.B) {
     w_hptw_resp := true.B
-    val g_perm_fail = !io.hptw.resp.bits.h_resp.entry.perm.get.r && !(io.csr.priv.mxr && io.hptw.resp.bits.h_resp.entry.perm.get.x)
+    val g_perm_fail = !io.hptw.resp.bits.h_resp.gaf && (!io.hptw.resp.bits.h_resp.entry.perm.get.r && !(io.csr.priv.mxr && io.hptw.resp.bits.h_resp.entry.perm.get.x))
     hptw_pageFault := io.hptw.resp.bits.h_resp.gpf || g_perm_fail
     hptw_accessFault := io.hptw.resp.bits.h_resp.gaf
     hptw_resp := io.hptw.resp.bits.h_resp
@@ -685,7 +685,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   when (io.hptw.resp.fire) {
     for (i <- state.indices) {
       when (state(i) === state_hptw_resp && io.hptw.resp.bits.id === entries(i).wait_id && io.hptw.resp.bits.h_resp.entry.tag === entries(i).ppn) {
-        val check_g_perm_fail = !io.hptw.resp.bits.h_resp.entry.perm.get.r && !(io.csr.priv.mxr && io.hptw.resp.bits.h_resp.entry.perm.get.x)
+        val check_g_perm_fail = !io.hptw.resp.bits.h_resp.gaf && (!io.hptw.resp.bits.h_resp.entry.perm.get.r && !(io.csr.priv.mxr && io.hptw.resp.bits.h_resp.entry.perm.get.x))
         when (check_g_perm_fail || io.hptw.resp.bits.h_resp.gaf || io.hptw.resp.bits.h_resp.gpf) {
           state(i) := state_mem_out
           entries(i).hptw_resp := io.hptw.resp.bits.h_resp
```
