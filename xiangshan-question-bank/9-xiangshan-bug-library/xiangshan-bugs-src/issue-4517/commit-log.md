# Commit Log
- Issue: #4517
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4517
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4517
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4517
- Changed files: 1
- Additions: 6
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 3d3250aea8d..4c85d402e18 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1606,9 +1606,14 @@ class NewCSR(implicit val p: Parameters) extends Module
     }).orR
     diffMhpmeventOverflowEvent.mhpmeventOverflow := VecInit(mhpmevents.map(_.regOut.asInstanceOf[MhpmeventBundle].OF.asBool)).asUInt
 
+    val mtopeiChange = RegNext(fromAIA.mtopei.asUInt) =/= fromAIA.mtopei.asUInt
+    val stopeiChange = RegNext(fromAIA.stopei.asUInt) =/= fromAIA.stopei.asUInt
+    val vstopeiChange = RegNext(hstatus.regOut.VGEIN.asUInt) =/= hstatus.regOut.VGEIN.asUInt
+    val hgeipChange = RegNext(fromAIA.vseip) =/= fromAIA.vseip
+
     val diffSyncAIAEvent = DifftestModule(new DiffSyncAIAEvent)
     diffSyncAIAEvent.coreid := hartId
-    diffSyncAIAEvent.valid := fromAIA.rdata.valid
+    diffSyncAIAEvent.valid := mtopeiChange || stopeiChange || vstopeiChange || hgeipChange
     diffSyncAIAEvent.mtopei := mtopei.rdata
     diffSyncAIAEvent.stopei := stopei.rdata
     diffSyncAIAEvent.vstopei := vstopei.rdata
```
