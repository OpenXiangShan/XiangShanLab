# Commit Log
- Issue: #4527
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4527
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4527
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4527
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index c0f6fe94e86..79fd7fe580f 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -1270,7 +1270,9 @@ class PtwMergeResp(implicit p: Parameters) extends PtwBundle {
       3.U -> Cat(entry(idx).ppn(entry(idx).ppn.getWidth - 1, vpnnLen * 3 - sectortlbwidth), tag(vpnnLen * 3 - 1, 0)),
       2.U -> Cat(entry(idx).ppn(entry(idx).ppn.getWidth - 1, vpnnLen * 2 - sectortlbwidth), tag(vpnnLen * 2 - 1, 0)),
       1.U -> Cat(entry(idx).ppn(entry(idx).ppn.getWidth - 1, vpnnLen - sectortlbwidth), tag(vpnnLen - 1, 0)),
-      0.U -> Cat(entry(idx).ppn(entry(idx).ppn.getWidth - 1, 0), entry(idx).ppn_low))
+      0.U -> Mux(entry(idx).n.getOrElse(0.U) === 0.U,
+        Cat(entry(idx).ppn(entry(idx).ppn.getWidth - 1, 0), entry(idx).ppn_low),
+        Cat(entry(idx).ppn(entry(idx).ppn.getWidth - 1, pteNapotBits - sectortlbwidth), tag(pteNapotBits - 1, 0))))
     )
   }
 }
```
