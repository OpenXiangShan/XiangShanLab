# Commit Log
- Issue: #3710
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3710
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3710
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3710
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index f7581d0ed3f..da103180b8d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1391,7 +1391,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     diffNonRegInterruptPendingEvent.platformIRPStip  := sstcIRGen.o.STIP
     diffNonRegInterruptPendingEvent.platformIRPVseip := platformIRP.VSEIP || hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt)
     diffNonRegInterruptPendingEvent.platformIRPVstip := sstcIRGen.o.VSTIP
-    diffNonRegInterruptPendingEvent.localCounterOverflowInterruptReq  := lcofiReq
+    diffNonRegInterruptPendingEvent.localCounterOverflowInterruptReq  := mip.regOut.LCOFIP.asBool
 
   }
 }
```
