# Commit Log
- Issue: #4788
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4788
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4788
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4788
- Changed files: 1
- Additions: 6
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 4a09fadc6a7..bcf2864f27c 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -738,11 +738,15 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     mem_arb.io.in(i).valid := is_mems(i) && !io.mem.req_mask(i)
   }
 
+  // when an entry dup with another entry whose mem_arb.io.out.fire,
+  // should block hptw req and change state(i) to state_mem_waiting
+  val block_hptw_req = WireInit(VecInit(Seq.fill(l2tlbParams.llptwsize)(false.B)))
+
   // process hptw requests in serial
   val hyper_arb1 = Module(new RRArbiterInit(new LLPTWEntry(), l2tlbParams.llptwsize))
   for (i <- 0 until l2tlbParams.llptwsize) {
     hyper_arb1.io.in(i).bits := entries(i)
-    hyper_arb1.io.in(i).valid := is_hptw_req(i) && !(Cat(is_hptw_resp).orR) && !(Cat(is_last_hptw_resp).orR)
+    hyper_arb1.io.in(i).valid := is_hptw_req(i) && !(Cat(is_hptw_resp).orR) && !(Cat(is_last_hptw_resp).orR) && !block_hptw_req(i)
   }
   val hyper_arb2 = Module(new RRArbiterInit(new LLPTWEntry(), l2tlbParams.llptwsize))
   for(i <- 0 until l2tlbParams.llptwsize) {
@@ -894,6 +898,7 @@ class LLPTW(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         state(i) := state_mem_waiting
         entries(i).hptw_resp := entries(mem_arb.io.chosen).hptw_resp
         entries(i).wait_id := mem_arb.io.chosen
+        block_hptw_req(i) := true.B
       }
     }
   }
```
