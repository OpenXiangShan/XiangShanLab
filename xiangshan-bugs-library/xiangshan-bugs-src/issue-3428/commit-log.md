# Commit Log
- Issue: #3428
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3428
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3428
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3428
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 607ff067410..71c07db7c40 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -676,7 +676,7 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
     }.elsewhen (!perm.v || (!perm.r && perm.w)) {
       pf := true.B
     }.otherwise{
-      unaligned(level)
+      pf := unaligned(level)
     }
     pf
   }
@@ -690,7 +690,7 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
     }.elsewhen (!perm.u) {
       gpf := true.B
     }.otherwise{
-      unaligned(level)
+      gpf := unaligned(level)
     }
     gpf
   }
```
