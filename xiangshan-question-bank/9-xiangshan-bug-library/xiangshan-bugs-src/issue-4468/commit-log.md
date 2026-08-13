# Commit Log
- Issue: #4468
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4468
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4468
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4468
- Changed files: 1
- Additions: 4
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index f1b16ae8ea0..5834b6ae947 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -38,6 +38,7 @@ import huancun.PrefetchField
 import org.chipsalliance.cde.config.Parameters
 import utility._
 import utility.mbist.MbistPipeline
+import utility.sram.SplittedSRAMTemplate
 import utility.sram.SRAMReadBus
 import utility.sram.SRAMTemplate
 import utility.sram.SRAMWriteBus
@@ -268,10 +269,12 @@ class ICacheMetaArray(implicit p: Parameters) extends ICacheArray with HasICache
   )
 
   private val tagArrays = (0 until PortNumber) map { bank =>
-    val tagArray = Module(new SRAMTemplate(
+    val tagArray = Module(new SplittedSRAMTemplate(
       new ICacheMetaEntry(),
       set = nSets / PortNumber,
       way = nWays,
+      waySplit = 2,
+      dataSplit = 1,
       shouldReset = true,
       holdRead = true,
       singlePort = true,
```
