# Commit Log
- Issue: #4454
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4454
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4454
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4454
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index cbaf24a99ab..b7e360062ca 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -330,7 +330,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     // 3. s1 is napot(64KB) and s2 is also napot(64KB)
     val allStage_n = (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s2.entry.level.getOrElse(0.U) =/= 0.U) ||
       (item.s2.entry.n.getOrElse(0.U) =/= 0.U && item.s1.entry.level.getOrElse(0.U) =/= 0.U) ||
-      (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s1.entry.n.getOrElse(0.U) =/= 0.U)
+      (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s2.entry.n.getOrElse(0.U) =/= 0.U)
     this.n := MuxLookup(item.s2xlate, 2.U)(Seq(
       onlyStage1 -> item.s1.entry.n.getOrElse(0.U),
       onlyStage2 -> item.s2.entry.n.getOrElse(0.U),
```
