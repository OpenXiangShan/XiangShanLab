# Commit Log
- Issue: #4473
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4473
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4473
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4473
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 7119bec3d2b..333e21ff2fb 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -882,7 +882,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
                         state_last_hptw_req,
                         Mux(bitmap_enable, state_bitmap_check, state_mem_out))
         mem_resp_hit(i) := true.B
-        entries(i).ppn := ptes(index).getPPN() // for last stage 2 translation
+        entries(i).ppn := Mux(ptes(index).n === 0.U, ptes(index).getPPN(), Cat(ptes(index).getPPN()(ptePPNLen - 1, pteNapotBits), entries(i).req_info.vpn(pteNapotBits - 1, 0))) // for last stage 2 translation
         // af will be judged in L2 TLB `contiguous_pte_to_merge_ptwResp`
         entries(i).hptw_resp.gpf := Mux(entries(i).req_info.s2xlate === allStage, ptes(index).isStage1Gpf(io.csr.hgatp.mode), false.B)
       }
```
