# Commit Log
- Issue: #5329
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5329
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5329
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5329
- Changed files: 1
- Additions: 6
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
index d460ff32b98..59cbedf35b5 100644
--- a/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
+++ b/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala
@@ -410,7 +410,12 @@ class Sbuffer(implicit p: Parameters)
       io.store_prefetch(i).bits.vaddr := Mux(prefetcher.io.prefetch_req(i).valid, prefetcher.io.prefetch_req(i).bits.vaddr, io.in(i).bits.vaddr)
       prefetcher.io.prefetch_req(i).ready := io.store_prefetch(i).ready
     } else {
-      io.store_prefetch(i) <> prefetcher.io.prefetch_req(i)
+      if (EnableStorePrefetchSPB) {
+        io.store_prefetch(i) <> prefetcher.io.prefetch_req(i)
+      } else {
+        io.store_prefetch(i) <> DontCare
+        prefetcher.io.prefetch_req(i) <> DontCare
+      }
     }
     io.store_prefetch zip prefetcher.io.prefetch_req drop 2 foreach (x => x._1 <> x._2)
   }
```
