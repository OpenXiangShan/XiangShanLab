# Commit Log
- Issue: #5082
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5082
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5082
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5082
- Changed files: 1
- Additions: 1
- Deletions: 12

## Files
- `src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
index 7ec8aaa8f4c..6a1a58281c9 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheWayLookup.scala
@@ -118,18 +118,7 @@ class ICacheWayLookup(implicit p: Parameters) extends ICacheModule
     }.reduce(_ || _)
   })
   // if the entry is being updated, we should not read it (i.e. read.valid should be false)
-  // NOTE: this is not necessary in current design:
-  //       when update is valid, DataArray is refilling at the same cycle, so DataArray.read.req.ready will be false.B
-  //       then, MainPipe will get s0_canGo (= toData.last.ready && fromWayLookup.valid && s1_ready) = false.B
-  //       then, WayLookup.read.ready (= s0_fire = s0_valid && s0_canGo && !s0_flush) will be false.B
-  //       so, even if read.valid is true, read.fire will still be false.B, and no read happens.
-//  private val updateStall = entryUpdate(readPtr.value)
-  //       currently, we use a simple assertion to avoid using extra logic
-  assert(
-    !(!empty && io.read.fire && entryUpdate(readPtr.value)),
-    "WayLookup read should not happen when entry is being updated"
-  )
-  private val updateStall = false.B
+  private val updateStall = entryUpdate(readPtr.value)
 
   /* *** read *** */
   // if the entry is empty, but there is a valid write, we can bypass it to read port (maybe timing critical)
```
