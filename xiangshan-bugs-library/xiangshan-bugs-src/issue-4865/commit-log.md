# Commit Log
- Issue: #4865
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4865
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4865
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4865
- Changed files: 1
- Additions: 4
- Deletions: 3

## Files
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index c07f89f3f9c..1bd773d28f4 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -273,8 +273,9 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     // When unaligned, the lowest bit of mask is 0.
     //  example: 16'b1111_1111_1111_0000
     val firstUnmask            = genVFirstUnmask(selPort(0).mask).asUInt
-    val vaddrOffset            = Mux(entryIsUS, firstUnmask, 0.U)
-    val vaddr                  = selVaddr + vaddrOffset
+    val addrOffset             = Mux(entryIsUS, firstUnmask, 0.U)
+    val vaddr                  = selVaddr + addrOffset
+    val gpaddr                 = selPort(0).gpaddr + addrOffset
     val vstart                 = Mux(entryIsUS, selPort(0).vstart, selElemInfield)
 
     // select oldest port to raise exception
@@ -287,7 +288,7 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
         entry.uop.trigger     := selPort(0).trigger
         entry.vaddr        := vaddr
         entry.vaNeedExt    := selPort(0).vaNeedExt
-        entry.gpaddr       := selPort(0).gpaddr
+        entry.gpaddr       := gpaddr
         entry.isForVSnonLeafPTE := selPort(0).isForVSnonLeafPTE
       }.otherwise{
         entry.vl           := Mux(entry.vl < vstart, entry.vl, vstart)
```
