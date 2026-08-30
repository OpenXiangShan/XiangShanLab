# Commit Log
- Issue: #4455
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4455
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4455
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4455
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index cbaf24a99ab..157f33702e7 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -306,7 +306,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     val s2_valid = Mux(s2page_pageSuper, VecInit(Seq.fill(tlbcontiguous)(true.B)), VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools))
     this.valididx := Mux(item.s2xlate === onlyStage2, s2_valid, item.s1.valididx)
     // if stage2 page is larger than stage1 page, need to merge s2tag and s2ppn to get a new s2ppn.
-    val s1ppn = item.s1.entry.ppn
+    val s1ppn = item.s1.entry.ppn(sectorppnLen - 1, 0)
     val s1ppn_low = item.s1.ppn_low
     val s2ppn = MuxLookup(item.s2.entry.level.getOrElse(0.U), item.s2.entry.ppn(ppnLen - 1, sectortlbwidth))(Seq(
       3.U -> Cat(item.s2.entry.ppn(ppnLen - 1, vpnnLen * 3), item.s2.entry.tag(vpnnLen * 3 - 1, sectortlbwidth)),
```
