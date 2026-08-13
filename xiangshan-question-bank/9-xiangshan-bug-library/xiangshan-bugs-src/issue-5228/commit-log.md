# Commit Log
- Issue: #5228
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5228
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5228
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5228
- Changed files: 1
- Additions: 5
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 8403cdc8d88..3816a4e210d 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -709,6 +709,7 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
                 req_paddr(log2Up(refillBytes)) === last
     val forward_D = RegInit(false.B)
     val forwardData = RegInit(VecInit(List.fill(VLEN/8)(0.U(8.W))))
+    val forwardCorrupt = RegInit(false.B)
 
     val block_idx = req_paddr(log2Up(refillBytes) - 1, 3)
     val block_data = Wire(Vec(l1BusDataWidth / 64, UInt(64.W)))
@@ -724,8 +725,11 @@ class DcacheToLduForwardIO(implicit p: Parameters) extends DCacheBundle {
         forwardData(i) := selected_data(8 * i + 7, 8 * i)
       }
     }
+    when (all_match) {
+      forwardCorrupt := corrupt
+    }
 
-    (forward_D, forwardData, corrupt)
+    (forward_D, forwardData, forwardCorrupt)
   }
 }
```
