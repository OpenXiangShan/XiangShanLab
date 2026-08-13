# Commit Log
- Issue: #4449
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4449
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4449
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4449
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 9623f9b0145..79637b34cf7 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1590,7 +1590,7 @@ class NewCSR(implicit val p: Parameters) extends Module
                                                         platformIRPSeipChange || platformIRPStipChange ||
                                                         platformIRPVseipChange || platformIRPVstipChange ||
                                                         fromAIAMeipChange || fromAIASeipChange ||
-                                                        lcofiReqChange) & !reset.asBool
+                                                        lcofiReqChange || RegNext(reset.asBool)) & !reset.asBool
     diffNonRegInterruptPendingEvent.platformIRPMeip  := platformIRP.MEIP
     diffNonRegInterruptPendingEvent.platformIRPMtip  := platformIRP.MTIP
     diffNonRegInterruptPendingEvent.platformIRPMsip  := platformIRP.MSIP
```
