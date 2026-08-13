# Commit Log
- Issue: #6131
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6131
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6131
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6131
- Changed files: 1
- Additions: 4
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 623e0ef34e0..6603ccff745 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -208,7 +208,7 @@ class InterruptFilter extends Module {
 
   private val meiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
   private val seiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
-  private val vseiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.VSEI).U
+  private val vseiPrioIdx = InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
 
   private val mipriosTmp = Wire(Vec(8, new IpriosSort))
   mipriosSortTmp.zipWithIndex.foreach { case (iprios, i) =>
@@ -359,8 +359,9 @@ class InterruptFilter extends Module {
   val C1GreaterThan255 = vstopeiReg.IPRIO.asUInt(10, 8).orR
   val C4IsZero = !hvipriosRegTmp.prioNum.orR
   val C2C5IsZero = !hvictlReg.IPRIO.asUInt.orR
-  val C4HighVSEI = iidC4Idx < findIndex(InterruptNO.VSEI.U)
-  val SEIHighC4 = findIndex(InterruptNO.SEI.U) < iidC4Idx
+  val SEIIdx = findIndex(InterruptNO.SEI.U)
+  val C4HighVSEI = iidC4Idx < SEIIdx
+  val SEIHighC4 = SEIIdx < iidC4Idx
   val iprioC1GreaterThan255 = Mux(C1GreaterThan255, 255.U, iprioC1Tmp)
 
   iprioC1 := vstopeiReg.IPRIO.asUInt
```
