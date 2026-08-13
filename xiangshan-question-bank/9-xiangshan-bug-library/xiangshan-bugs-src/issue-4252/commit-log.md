# Commit Log
- Issue: #4252
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4252
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4252
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4252
- Changed files: 1
- Additions: 7
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 74d2fbc25f2..711d2097668 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -328,9 +328,15 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     // 1. s1 is napot(64KB) and s2 is superpage(greater than or equal to 2MB)
     // 2. s2 is napot(64KB) and s1 is superpage(greater than or equal to 2MB)
     // 3. s1 is napot(64KB) and s2 is also napot(64KB)
-    this.n := (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s2.entry.level.getOrElse(0.U) =/= 0.U) ||
+    val allStage_n = (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s2.entry.level.getOrElse(0.U) =/= 0.U) ||
       (item.s2.entry.n.getOrElse(0.U) =/= 0.U && item.s1.entry.level.getOrElse(0.U) =/= 0.U) ||
       (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s1.entry.n.getOrElse(0.U) =/= 0.U)
+    this.n := MuxLookup(item.s2xlate, 2.U)(Seq(
+      onlyStage1 -> item.s1.entry.n.getOrElse(0.U),
+      onlyStage2 -> item.s2.entry.n.getOrElse(0.U),
+      allStage -> allStage_n,
+      noS2xlate -> item.s1.entry.n.getOrElse(0.U)
+    ))
     this.vmid := item.s1.entry.vmid.getOrElse(0.U)
     this.g_pbmt := item.s2.entry.pbmt
     this.g_perm.applyS2(item.s2)
```
