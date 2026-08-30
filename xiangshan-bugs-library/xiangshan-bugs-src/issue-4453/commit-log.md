# Commit Log
- Issue: #4453
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4453
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4453
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4453
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 3f01f4d384b..c5d2719954a 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -399,7 +399,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
 
   // l3
   val l3Hit = if(EnableSv48) Some(Wire(Bool())) else None
-  val l3HitPPN = if(EnableSv48) Some(Wire(UInt(ppnLen.W))) else None
+  val l3HitPPN = if(EnableSv48) Some(Wire(UInt(gvpnLen.W))) else None
   val l3HitPbmt = if(EnableSv48) Some(Wire(UInt(ptePbmtLen.W))) else None
   val l3Pre = if(EnableSv48) Some(Wire(Bool())) else None
   val ptwl3replace = if(EnableSv48) Some(ReplacementPolicy.fromString(l2tlbParams.l3Replacer, l2tlbParams.l3Size)) else None
```
