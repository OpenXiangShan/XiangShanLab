# Commit Log
- Issue: #4694
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4694
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4694
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4694
- Changed files: 1
- Additions: 6
- Deletions: 5

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 7d02a7cfd06..18fef418b8b 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -301,10 +301,6 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     val s1tag = item.s1.entry.tag
     val s2tag = item.s2.entry.tag(gvpnLen - 1, sectortlbwidth)
     this.tag := Mux(item.s2xlate === onlyStage2, s2tag, s1tag)
-    val s2page_pageSuper = item.s2.entry.level.getOrElse(0.U) =/= 0.U || item.s2.entry.n.getOrElse(0.U) =/= 0.U
-    this.pteidx := Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), item.s1.pteidx)
-    val s2_valid = Mux(s2page_pageSuper, VecInit(Seq.fill(tlbcontiguous)(true.B)), VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools))
-    this.valididx := Mux(item.s2xlate === onlyStage2, s2_valid, item.s1.valididx)
     // if stage2 page is larger than stage1 page, need to merge s2tag and s2ppn to get a new s2ppn.
     val s1ppn = item.s1.entry.ppn(sectorppnLen - 1, 0)
     val s1ppn_low = item.s1.ppn_low
@@ -331,12 +327,17 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     val allStage_n = (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s2.entry.level.getOrElse(0.U) =/= 0.U) ||
       (item.s2.entry.n.getOrElse(0.U) =/= 0.U && item.s1.entry.level.getOrElse(0.U) =/= 0.U) ||
       (item.s1.entry.n.getOrElse(0.U) =/= 0.U && item.s2.entry.n.getOrElse(0.U) =/= 0.U)
-    this.n := MuxLookup(item.s2xlate, 2.U)(Seq(
+    val inner_n = MuxLookup(item.s2xlate, 2.U)(Seq(
       onlyStage1 -> item.s1.entry.n.getOrElse(0.U),
       onlyStage2 -> item.s2.entry.n.getOrElse(0.U),
       allStage -> allStage_n,
       noS2xlate -> item.s1.entry.n.getOrElse(0.U)
     ))
+    this.n := inner_n
+    val isSuperPage = inner_level =/= 0.U || inner_n =/= 0.U
+    this.valididx := Mux(isSuperPage, VecInit(Seq.fill(tlbcontiguous)(true.B)),
+      Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), item.s1.valididx))
+    this.pteidx := Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), item.s1.pteidx)
     this.vmid := Mux(item.s2xlate === onlyStage2, item.s2.entry.vmid.getOrElse(0.U), item.s1.entry.vmid.getOrElse(0.U))
     this.g_pbmt := item.s2.entry.pbmt
     this.g_perm.applyS2(item.s2)
```
