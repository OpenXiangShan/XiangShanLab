# Commit Log
- Issue: #4754
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4754
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4754
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4754
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index f870dd94fd1..4a09fadc6a7 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -972,7 +972,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
           state(i) := state_mem_out
           entries(i).hptw_resp := io.hptw.resp.bits.h_resp
           entries(i).hptw_resp.gpf := io.hptw.resp.bits.h_resp.gpf || check_g_perm_fail
-          entries(i).first_s2xlate_fault := io.hptw.resp.bits.h_resp.gaf || io.hptw.resp.bits.h_resp.gpf
+          entries(i).first_s2xlate_fault := io.hptw.resp.bits.h_resp.gaf || io.hptw.resp.bits.h_resp.gpf || check_g_perm_fail
         }.otherwise{ // change the entry that is waiting hptw resp
           val need_to_waiting_vec = state.indices.map(i => state(i) === state_mem_waiting &&
             dup(entries(i).req_info.vpn, entries(io.hptw.resp.bits.id).req_info.vpn) &&
```
