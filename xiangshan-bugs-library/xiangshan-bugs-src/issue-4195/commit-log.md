# Commit Log
- Issue: #4195
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4195
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4195
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4195
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 2187dfbfd7c..6a18b5a72e8 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -831,7 +831,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   // misc entries: super & invalid
   val spRefill =
     !flush_dup(0) &&
-    (refill.levelOH.sp || memPte(0).isNapot(refill.level_dup(0))) &&
+    (refill.levelOH.sp || (refill.levelOH.l0 && memPte(0).isNapot(refill.level_dup(0)))) &&
     ((memPte(0).isLeaf() && memPte(0).canRefill(refill.level_dup(0), refill.req_info_dup(0).s2xlate, pbmte, io.csr_dup(0).vsatp.mode)) ||
     memPte(0).onlyPf(refill.level_dup(0), refill.req_info_dup(0).s2xlate, pbmte))
   val spRefillIdx = spreplace.way.suggestName(s"sp_refillIdx") // LFSR64()(log2Up(l2tlbParams.spSize)-1,0) // TODO: may be LRU
```
