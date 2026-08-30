# Commit Log
- Issue: #5926
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5926
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5926
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5926
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 0b9d24b71a1..b166c6b4825 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -407,7 +407,7 @@ class InterruptFilter extends Module {
     iidC1C5 := iidOnlyC5
     iprioC1C5 := iprioC3C5Tmp
   }
-  
+
   // C2,C4 enable
   when(C4IsZero) {
     iidC2C4 := Mux(C4HighVSEI, iidOnlyC4, iidOnlyC1)
@@ -560,7 +560,7 @@ class InterruptFilter extends Module {
   val delayedIRToHS = DelayN(irToHSReg, 5)
   val delayedIRToVS = DelayN(irToVSReg, 5)
 
-  io.out.interruptVec.valid := delayedIntrVec.orR || delayedDebugIntr || delayedVIIsHvictlInjectReg
+  io.out.interruptVec.valid := delayedIntrVec.orR || delayedDebugIntr
   io.out.interruptVec.bits := delayedIntrVec
   io.out.debug := delayedDebugIntr
   io.out.nmi := delayedNMI
```
