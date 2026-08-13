# Commit Log
- Issue: #5814
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5814
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5814
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5814
- Changed files: 1
- Additions: 34
- Deletions: 29

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index e86f1be2b3e..f2f263790e0 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -415,32 +415,39 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
 
       val s1Req = io.query(i).s1Req
       val s1QueryPaddr = s1Req.paddr(PAddrBits - 1, VWordOffset)
+      val byteRangeWidth = VWordOffset + 2
       // prevent X-state
+      val s1Same16BMatchVec = WireInit(VecInit(io.dataEntriesIn.map(_.vaddr(VAddrBits - 1, VWordOffset) === s1LoadVaddr)))
+      val s1Next16BMatchVec = WireInit(VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).map { case (dataEntry, ctrlEntry) =>
+        ctrlEntry.cross16Byte && (dataEntry.vaddr(VAddrBits - 1, VWordOffset) + 1.U) === s1LoadVaddr
+      }))
+      val s1SameLineMatchVec = WireInit(VecInit(io.dataEntriesIn.map(dataEntry =>
+        dataEntry.vaddr(VAddrBits - 1, DCacheLineOffset) ===
+          s1LoadVaddr(s1LoadVaddr.getWidth - 1, DCacheLineOffset - VWordOffset)
+      )))
       // Virtual address match (high bits only, ignore byte offset)
-      val s1VaddrMatchVec  = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).map { case (dataEntry, ctrlEntry) =>
+      val s1VaddrMatchVec  = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).zipWithIndex.map { case ((dataEntry, ctrlEntry), j) =>
         val storeIsCboZero = ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
-        val isCross16B     = ctrlEntry.cross16Byte
-        // vaddr two part match:
-        // [1]: not cross 16B: vaddr[VaddrBits - 1, log2Ceil(CacheLineSize / 8)] addr(maxLen -> cacheline) or
-        //      cross 16B: vaddr[VaddrBits - 1, log2Ceil(CacheLineSize / 8)] + 1.U addr(maxLen -> cacheline) [next cacheline]
-        // [2]: not cross 16B: vaddr[log2Ceil(CacheLineSize / 8) - 1, log2Ceil(VLENB)] or
-        //      cross 16B: vaddr[log2Ceil(CacheLineSize / 8) - 1, log2Ceil(VLENB)] + 1.U [next 16B] or
-        //      The bits within cacheline, if store is cboZero, it can be ignored.
-        //
-        ((dataEntry.vaddr(DCacheLineOffset - 1, VWordOffset) === s1LoadVaddr(DCacheLineOffset - VWordOffset - 1, 0) ||
-          isCross16B && (dataEntry.vaddr(DCacheLineOffset - 1, VWordOffset) + 1.U) === s1LoadVaddr(DCacheLineOffset - VWordOffset - 1, 0) ||
-          storeIsCboZero) &&
-        (dataEntry.vaddr(VAddrBits - 1, DCacheLineOffset) === s1LoadVaddr(s1LoadVaddr.getWidth - 1, DCacheLineOffset - VWordOffset) ||
-          isCross16B && (dataEntry.vaddr(VAddrBits - 1, DCacheLineOffset) + 1.U) === s1LoadVaddr(s1LoadVaddr.getWidth - 1, DCacheLineOffset - VWordOffset))) &&
-        ctrlEntry.addrValid
+
+        (s1Same16BMatchVec(j) || s1Next16BMatchVec(j) || (storeIsCboZero && s1SameLineMatchVec(j))) && ctrlEntry.addrValid
       }).asUInt
 
-      // Byte overlap check: store covers any part of load's range
-      //   Example: store [2,5] and load [3,3] -> overlap (2<=3 && 5>=3)
-      val s1OverlapMask  = VecInit((0 until StoreQueueSize).map(j =>
-        io.dataEntriesIn(j).byteStart <= s1LoadEnd && io.dataEntriesIn(j).byteEnd >= s1LoadStart ||
-        io.ctrlEntriesIn(j).cross16Byte && io.dataEntriesIn(j).byteEnd(VWordOffset - 1, 0) <= s1LoadEnd // next 16B, store start always 0.
-      )).asUInt
+      // Byte overlap check in the store-relative 16B coordinate space.
+      val s1OverlapMask  = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).zipWithIndex.map { case ((dataEntry, ctrlEntry), j) =>
+        val loadInNext16B   = s1Next16BMatchVec(j)
+        val loadRangeStart  = Mux(loadInNext16B,
+          s1LoadStart.pad(byteRangeWidth) + VLENB.U(byteRangeWidth.W), // mapping load to next 16B segment if cross 16B
+          s1LoadStart.pad(byteRangeWidth)
+        )
+        val loadRangeEnd    = Mux(loadInNext16B,
+          s1LoadEnd.pad(byteRangeWidth) + VLENB.U(byteRangeWidth.W), // mapping load to next 16B segment if cross 16B
+          s1LoadEnd.pad(byteRangeWidth)
+        )
+        val storeRangeStart = dataEntry.byteStart.pad(byteRangeWidth)
+        val storeRangeEnd   = dataEntry.byteEnd.pad(byteRangeWidth)
+
+        storeRangeStart <= loadRangeEnd && storeRangeEnd >= loadRangeStart
+      }).asUInt
 
       XSError((s1LoadEnd < s1LoadStart) && s1Valid, "ByteStart > ByteEnd!\n")
 
@@ -544,14 +551,12 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       // !Paddrmatch
       val s2PaddrMatchVec       = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).map { case (dataEntry, ctrlEntry) =>
         val storeIsCboZero      = ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
-        val isCross16B          = ctrlEntry.cross16Byte
-
-        (dataEntry.paddr(DCacheLineOffset - 1, VWordOffset) === s2LoadPaddr(DCacheLineOffset - VWordOffset - 1, 0) ||
-          isCross16B && (dataEntry.paddr(DCacheLineOffset - 1, VWordOffset) + 1.U) === s2LoadPaddr(DCacheLineOffset - VWordOffset - 1, 0) || // next 16B
-          storeIsCboZero) &&
-        (dataEntry.paddr(pageOffset - 1, DCacheLineOffset) === s2LoadPaddr(pageOffset - VWordOffset - 1, DCacheLineOffset - VWordOffset) ||
-          isCross16B && (dataEntry.paddr(pageOffset - 1, DCacheLineOffset) + 1.U) === s2LoadPaddr(pageOffset - VWordOffset - 1, DCacheLineOffset - VWordOffset)) && // next Cacheline
-        dataEntry.paddr(PAddrBits - 1, pageOffset) === s2LoadPaddr(s2LoadPaddr.getWidth - 1, pageOffset - VWordOffset)
+        val same16BMatch        = dataEntry.paddr(PAddrBits - 1, VWordOffset) === s2LoadPaddr
+        val next16BMatch        = ctrlEntry.cross16Byte && (dataEntry.paddr(PAddrBits - 1, VWordOffset) + 1.U) === s2LoadPaddr
+        val sameLineMatch       = dataEntry.paddr(PAddrBits - 1, DCacheLineOffset) ===
+          s2LoadPaddr(s2LoadPaddr.getWidth - 1, DCacheLineOffset - VWordOffset)
+
+        same16BMatch || next16BMatch || (storeIsCboZero && sameLineMatch)
       }).asUInt
 
       // two situation need to trigger paddr not match :
```
