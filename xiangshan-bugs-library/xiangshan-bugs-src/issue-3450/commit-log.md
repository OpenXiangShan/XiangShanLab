# Commit Log
- Issue: #3450
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3450
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3450
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3450
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 5aab24670e2..8072d9a8ffc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1090,7 +1090,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.toDecode.illegalInst.hfenceGVMA := isModeHS && mstatus.regOut.TVM || isModeHU
   io.toDecode.illegalInst.hfenceVVMA := isModeHU
   io.toDecode.virtualInst.hfence     := isModeVS || isModeVU
-  io.toDecode.illegalInst.hlsv       := isModeHU && hstatus.regOut.HU
+  io.toDecode.illegalInst.hlsv       := isModeHU && !hstatus.regOut.HU
   io.toDecode.virtualInst.hlsv       := isModeVS || isModeVU
   io.toDecode.illegalInst.fsIsOff    := mstatus.regOut.FS === ContextStatus.Off || (isModeVS || isModeVU) && vsstatus.regOut.FS === ContextStatus.Off
   io.toDecode.illegalInst.vsIsOff    := mstatus.regOut.VS === ContextStatus.Off || (isModeVS || isModeVU) && vsstatus.regOut.VS === ContextStatus.Off
```
