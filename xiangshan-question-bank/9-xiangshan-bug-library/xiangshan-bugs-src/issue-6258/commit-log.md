# Commit Log
- Issue: #6258
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6258
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6258
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6258
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 4afb4cd27b2..12d6ea4484e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -799,8 +799,8 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
   trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
-  trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
-  trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
+  trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
+  trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
 
   Seq(trapEntryMEvent, trapEntryMNEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
     eMod.in match {
```
