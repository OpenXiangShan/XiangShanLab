# Commit Log
- Issue: #4419
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4419
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4419
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4419
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index de3d5c3ce57..d4213a63ea4 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1583,11 +1583,11 @@ class NewCSR(implicit val p: Parameters) extends Module
 
     val diffNonRegInterruptPendingEvent = DifftestModule(new DiffNonRegInterruptPendingEvent)
     diffNonRegInterruptPendingEvent.coreid           := hartId
-    diffNonRegInterruptPendingEvent.valid            := platformIRPMeipChange || platformIRPMtipChange || platformIRPMsipChange ||
+    diffNonRegInterruptPendingEvent.valid            := (platformIRPMeipChange || platformIRPMtipChange || platformIRPMsipChange ||
                                                         platformIRPSeipChange || platformIRPStipChange ||
                                                         platformIRPVseipChange || platformIRPVstipChange ||
                                                         fromAIAMeipChange || fromAIASeipChange ||
-                                                        lcofiReqChange
+                                                        lcofiReqChange) & !reset.asBool
     diffNonRegInterruptPendingEvent.platformIRPMeip  := platformIRP.MEIP
     diffNonRegInterruptPendingEvent.platformIRPMtip  := platformIRP.MTIP
     diffNonRegInterruptPendingEvent.platformIRPMsip  := platformIRP.MSIP
```
