# Commit Log
- Issue: #4439
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4439
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4439
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4439
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 637cab3a1cc..de195a3923d 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -558,7 +558,7 @@ class CtrlBlockImp(
   rat.io.snpt.snptSelect := snptSelect
   rat.io.snpt.flushVec := flushVec
 
-  val decodeHasException = decode.io.out.map(x => x.bits.exceptionVec(instrPageFault) || x.bits.exceptionVec(instrAccessFault))
+  val decodeHasException = decode.io.out.map(x => x.bits.exceptionVec.asUInt.orR || (!TriggerAction.isNone(x.bits.trigger)))
   // fusion decoder
   for (i <- 0 until DecodeWidth) {
     fusionDecoder.io.in(i).valid := decode.io.out(i).valid && !(decodeHasException(i) || disableFusion)
```
