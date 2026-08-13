# Commit Log
- Issue: #6086
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6086
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6086
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6086
- Changed files: 1
- Additions: 5
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 6467a1083a6..623e0ef34e0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -425,7 +425,7 @@ class InterruptFilter extends Module {
   iidC3C4 := Mux(C4IsZero, Mux(C4HighVSEI, iidOnlyC4, iidOnlyC1), iidOnlyC4)
   iprioC3C4 := iprioC4Tmp
   // C3,C5 enable
-  iidC3C5 := Mux(C2C5IsZero, Mux(hvictlReg.DPR.asBool, iidOnlyC5, iidOnlyC1), iidOnlyC5)
+  iidC3C5 := Mux(C2C5IsZero, Mux(hvictlReg.DPR.asBool, iidOnlyC1, iidOnlyC5), iidOnlyC5)
   iprioC3C5 := iprioC3C5Tmp
 
   // update vstopi
@@ -535,8 +535,10 @@ class InterruptFilter extends Module {
 
   // virtual interrupt with hvictl injection
   val vsIRModeCond = privState.isModeVS && vsstatusSIE || privState < PrivState.ModeVS
-  val SelectCandidate5 = onlyC5EnableReg || C3C5EnableReg ||
-                         C1C5EnableReg && (iprioC1 === iprioC2C5 && !hvictlReg.DPR.asBool || iprioC1 > iprioC2C5)
+  val SelectCandidate5 = onlyC5EnableReg ||
+                         C1C5EnableReg && ((!C2C5IsZero && (iprioC1 > iprioC2C5 || (iprioC1 === iprioC2C5) && !hvictlReg.DPR.asBool)) ||
+                                           (C2C5IsZero && !hvictlReg.DPR.asBool)) ||
+                         C3C5EnableReg && (!C2C5IsZero || !hvictlReg.DPR.asBool)
   // delay at least 6 cycles to maintain the atomic of sret/mret
   // 65bit indict current interrupt is NMI
   val intrVecReg = RegInit(0.U(8.W))
```
