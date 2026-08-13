# Commit Log
- Issue: #5330
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5330
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5330
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5330
- Changed files: 1
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index d62bf08d509..3e1ddf6bc94 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -210,7 +210,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   val issueVlMax                      = instMicroOp.uopFlowNum // max elementIdx in vd
   val issueMaxIdxInIndex              = GenVLMAX(Mux(issueEmul.asSInt > 0.S, 0.U, issueEmul), issueEew(1, 0)) // index element index in index register
   val issueMaxIdxInIndexMask          = GenVlMaxMask(issueMaxIdxInIndex, elemIdxBits)
-  val issueMaxIdxInIndexLog2          = GenVLMAXLog2(Mux(issueEmul.asSInt > 0.S, 0.U, issueEmul), issueEew(1, 0))
+  val issueMaxIdxInIndexLog2          = RegNext(GenVLMAXLog2(Mux(issueEmul.asSInt > 0.S, 0.U, issueEmul), issueEew(1, 0)))
   val issueIndexIdx                   = segmentIdx & issueMaxIdxInIndexMask
   val segmentActive                   = (mask & UIntToOH(segmentIdx)).orR
 
@@ -477,8 +477,8 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   segmentTrigger.io.fromLoadStore.mask                  := 0.U
 
   val triggerAction = segmentTrigger.io.toLoadStore.triggerAction
-  val triggerDebugMode = TriggerAction.isDmode(triggerAction)
-  val triggerBreakpoint = TriggerAction.isExp(triggerAction)
+  val triggerDebugMode = RegEnable(TriggerAction.isDmode(triggerAction), false.B, state === s_tlb_req)
+  val triggerBreakpoint = RegEnable(TriggerAction.isExp(triggerAction), false.B, state === s_tlb_req)
 
   // tlb resp
   when(io.dtlb.resp.fire && state === s_wait_tlb_resp){
```
