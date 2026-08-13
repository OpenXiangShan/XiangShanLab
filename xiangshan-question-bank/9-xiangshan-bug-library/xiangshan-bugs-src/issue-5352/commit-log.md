# Commit Log
- Issue: #5352
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5352
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5352
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5352
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
index c460c2fb3b6..e586889dbf7 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
@@ -290,7 +290,7 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
 
   def detectRollback(i: Int) = {
     paddrModule.io.violationMdata(i) := genPartialPAddr(RegEnable(storeIn(i).bits.paddr, storeIn(i).valid))
-    paddrModule.io.violationCheckLine.get(i) := storeIn(i).bits.wlineflag
+    paddrModule.io.violationCheckLine.get(i) := RegEnable(storeIn(i).bits.wlineflag, storeIn(i).valid)
     maskModule.io.violationMdata(i) := RegEnable(storeIn(i).bits.mask, storeIn(i).valid)
 
     val addrMaskMatch = paddrModule.io.violationMmask(i).asUInt & maskModule.io.violationMmask(i).asUInt
```
