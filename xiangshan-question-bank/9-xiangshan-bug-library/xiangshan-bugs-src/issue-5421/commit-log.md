# Commit Log
- Issue: #5421
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5421
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5421
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5421
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 723c6e033ee..aaaf1b3f07c 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -385,7 +385,7 @@ class CtrlBlockImp(
     val vld = rob.io.commits.isCommit && rob.io.commits.commitValid(i)
     crc.valid := GatedValidRegNext(vld)
     crc.bits.ftqPtr := RegEnable(blk.bits.ftqIdx.get, vld)
-    crc.bits.rasAction := RegEnable(Itype.isPop(blk.bits.tracePipe.itype) ## Itype.isPush(blk.bits.tracePipe.itype), vld)
+    crc.bits.rasAction := RegEnable(Itype.isPush(blk.bits.tracePipe.itype) ## Itype.isPop(blk.bits.tracePipe.itype), vld)
   }
   // Be careful here:
   // T0: rob.io.flushOut, s0_robFlushRedirect
```
