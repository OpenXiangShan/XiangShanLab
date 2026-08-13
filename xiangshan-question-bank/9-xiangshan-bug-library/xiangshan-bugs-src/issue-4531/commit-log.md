# Commit Log
- Issue: #4531
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4531
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4531
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4531
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 2fc1718a9dc..984aac3fe5b 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -302,7 +302,7 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   val ncSlaveAck = Wire(Bool())
   val ncSlaveAckMid = Wire(UInt(uncacheIdxBits.W))
   val ncDoResp = Wire(Bool())
-  val ncReadNextTrigger = Mux(io.uncacheOutstanding, ncDoReq, ncDoResp)
+  val ncReadNextTrigger = Mux(io.uncacheOutstanding, ncSlaveAck, ncDoResp)
   val ncDeqTrigger = Mux(io.uncacheOutstanding, ncSlaveAck, ncDoResp)
   val ncPtr = Mux(io.uncacheOutstanding, ncSlaveAckMid, ncWaitRespPtrReg)
```
