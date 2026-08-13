# Commit Log
- Issue: #3803
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3803
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3803
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3803
- Changed files: 1
- Additions: 5
- Deletions: 3

## Files
- `src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
index 13d0e7765be..32ada0a31f4 100644
--- a/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala
@@ -184,6 +184,8 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
   val actionVec = VecInit(tdata.map(_.action))
   val triggerAction = Wire(TriggerAction())
   TriggerUtil.triggerActionGen(triggerAction, backendTriggerCanFireVec, actionVec, triggerCanRaiseBpExp)
+  val triggerDebugMode = TriggerAction.isDmode(triggerAction)
+  val triggerBreakpoint = TriggerAction.isExp(triggerAction)
   
   // tlb translation, manipulating signals && deal with exception
   // at the same time, flush sbuffer
@@ -229,13 +231,13 @@ class AtomicsUnit(implicit p: Parameters) extends XSModule
       exceptionVec(storeGuestPageFault) := io.dtlb.resp.bits.excp(0).gpf.st
       exceptionVec(loadGuestPageFault)  := io.dtlb.resp.bits.excp(0).gpf.ld
       
-      exceptionVec(breakPoint) := TriggerAction.isExp(triggerAction)
+      exceptionVec(breakPoint) := triggerBreakpoint
       trigger                  := triggerAction
 
       when (!io.dtlb.resp.bits.miss) {
         io.out.bits.uop.debugInfo.tlbRespTime := GTimer()
-        when (!addrAligned) {
-          // NOTE: when addrAligned, do not need to wait tlb actually
+        when (!addrAligned || triggerDebugMode || triggerBreakpoint) {
+          // NOTE: when addrAligned or trigger fire, do not need to wait tlb actually
           // check for miss aligned exceptions, tlb exception are checked next cycle for timing
           // if there are exceptions, no need to execute it
           state := s_finish
```
