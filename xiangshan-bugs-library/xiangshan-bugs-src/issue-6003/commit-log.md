# Commit Log
- Issue: #6003
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6003
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6003
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6003
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index 75b13f10c9f..401b9a07edf 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -525,6 +525,7 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       val s2MultiMatch       = RegEnable(s1MultiMatch, s1Valid)
       val s2LoadPaddr        = RegEnable(s1QueryPaddr, s1Valid)
       val s2LoadStart        = RegEnable(s1LoadStart, s1Valid)
+      val s2LoadEnd          = RegEnable(s1LoadEnd, s1Valid)
       val s2ForwardValid     = RegEnable(s1SelectOH.orR, s1Valid) // indicate whether forward is valid.
       val s2Valid            = RegNext(s1Valid)
       // debug
@@ -577,7 +578,7 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       )
       val s2OutMask            = ParallelLookUp(s2ByteSelectOffset, s2SelectMask) & s2LoadMaskEnd
 
-      val s2FullOverlap        = (s2SelectDataEntry.byteMask & s2LoadMaskEnd) === s2LoadMaskEnd
+      val s2FullOverlap        = s2SelectDataEntry.byteStart <= s2LoadStart && s2SelectDataEntry.byteEnd >= s2LoadEnd
       // First condition: access extends beyond the lower log2Ceil(VLEN/8) bits.
       // Second condition: higher bits of the virtual address within the page offset are non-zero, indicating a potential cross-page access.
       val s2Cross4KPage        = s2SelectDataEntry.byteEnd(VWordOffset) && s2SelectDataEntry.vaddr(pageOffset - 1, VWordOffset).andR && s2ForwardValid
```
