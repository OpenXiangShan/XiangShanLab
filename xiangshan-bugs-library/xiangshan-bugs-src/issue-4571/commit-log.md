# Commit Log
- Issue: #4571
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4571
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4571
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4571
- Changed files: 2
- Additions: 163
- Deletions: 74

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index 2b60fc5edf5..f3e37b787d9 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 2b60fc5edf50f519a4310dc41620e9a204b4aacb
+Subproject commit f3e37b787d94e0cab5defc4e82fb31a4d4fb2262
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index bc7d625994b..b4abbaa087f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -53,9 +53,6 @@ class InterruptFilter extends Module {
   val mtopiIsNotZero: Bool = (mip & mie & (~mideleg).asUInt) =/= 0.U
   val stopiIsNotZero: Bool = (hsip & hsie & (~hideleg).asUInt) =/= 0.U
 
-  val mIpriosIsZero : Bool = miprios  === 0.U
-  val hsIpriosIsZero: Bool = hsiprios === 0.U
-
   val NoSEIMask = (~(BigInt(1) << InterruptNO.SEI).U(64.W)).asUInt
   val mtopigather = mip & mie & (~mideleg).asUInt
   val hstopigather = hsip & hsie & (~hideleg).asUInt
@@ -261,33 +258,23 @@ class InterruptFilter extends Module {
 
   // update mtopi
   io.out.mtopi.IID := Mux(mtopiIsNotZero, mIidNum, 0.U)
-  io.out.mtopi.IPRIO := Mux(
-    mtopiIsNotZero,
-    Mux(
-      mIpriosIsZero,
-      1.U,
-      Mux1H(Seq(
-        (!mipriosRegTmp.isZero && !mipriosRegTmp.greaterThan255) -> mipriosRegTmp.prioNum,
-        (mipriosRegTmp.greaterThan255 || mipriosRegTmp.isZero && mIidDefaultPrioLowMEI) -> 255.U,
-        (mipriosRegTmp.isZero && mIidDefaultPrioHighMEI) -> 0.U,
-      ))
-    ),
+  io.out.mtopi.IPRIO := Mux(mtopiIsNotZero,
+    Mux1H(Seq(
+      (!mipriosRegTmp.isZero && !mipriosRegTmp.greaterThan255) -> mipriosRegTmp.prioNum,
+      (mipriosRegTmp.greaterThan255 || mipriosRegTmp.isZero && mIidDefaultPrioLowMEI) -> 255.U,
+      (mipriosRegTmp.isZero && mIidDefaultPrioHighMEI) -> 0.U,
+    )),
     0.U
   )
 
   // upadte stopi
   io.out.stopi.IID := Mux(stopiIsNotZero, hsIidNum, 0.U)
-  io.out.stopi.IPRIO := Mux(
-    stopiIsNotZero,
-    Mux(
-      hsIpriosIsZero,
-      1.U,
-      Mux1H(Seq(
-        (!hsipriosRegTmp.isZero && !hsipriosRegTmp.greaterThan255) -> hsipriosRegTmp.prioNum,
-        (hsipriosRegTmp.greaterThan255 || hsipriosRegTmp.isZero && hsIidDefaultPrioLowSEI) -> 255.U,
-        (hsipriosRegTmp.isZero && hsIidDefaultPrioHighSEI) -> 0.U,
-      ))
-    ),
+  io.out.stopi.IPRIO := Mux(stopiIsNotZero,
+    Mux1H(Seq(
+      (!hsipriosRegTmp.isZero && !hsipriosRegTmp.greaterThan255) -> hsipriosRegTmp.prioNum,
+      (hsipriosRegTmp.greaterThan255 || hsipriosRegTmp.isZero && hsIidDefaultPrioLowSEI) -> 255.U,
+      (hsipriosRegTmp.isZero && hsIidDefaultPrioHighSEI) -> 0.U,
+    )),
     0.U
   )
 
@@ -301,59 +288,160 @@ class InterruptFilter extends Module {
 
   assert(PopCount(Cat(Candidate1, Candidate2, Candidate3)) < 2.U, "Only one Candidate could be select from Candidate1/2/3 in VS-level!")
   assert(PopCount(Cat(Candidate4, Candidate5)) < 2.U, "Only one Candidate could be select from Candidate4/5 in VS-level!")
-
-  val iidCandidate123   = Wire(UInt(12.W))
-  val iidCandidate45    = Wire(UInt(12.W))
-  val iprioCandidate123 = Wire(UInt(11.W))
-  val iprioCandidate45  = Wire(UInt(11.W))
-  iidCandidate123 := InterruptNO.SEI.U
-  iprioCandidate123 := Mux1H(Seq(
-    Candidate1 -> vstopei.IPRIO.asUInt,
-    Candidate2 -> hvictl.IPRIO.asUInt,
-    Candidate3 -> 256.U,
-  ))
-  iidCandidate45 := Mux1H(Seq(
-    Candidate4 -> vsIidNum,
-    Candidate5 -> hvictl.IID.asUInt,
-  ))
-  iprioCandidate45 := Mux1H(Seq(
-    Candidate4 -> hvipriosRegTmp.prioNum,
-    Candidate5 -> hvictl.IPRIO.asUInt,
-  ))
+  assert(PopCount(Cat(Candidate2, Candidate5)) < 2.U, "Candidate2 and candidate5 cannot be true at the same time. ")
 
   val Candidate123 = Candidate1 || Candidate2 || Candidate3
   val Candidate45 = Candidate4 || Candidate5
 
-  val Candidate123HighCandidate45 = Mux1H(Seq(
-    (Candidate123 && Candidate4)   -> ((iprioCandidate123 < iprioCandidate45) || ((iprioCandidate123 === iprioCandidate45) && (findIndex(iidCandidate123) <= findIndex(iidCandidate45)))),
-    (Candidate123 && Candidate5)   -> ((iprioCandidate123 < iprioCandidate45) || ((iprioCandidate123 === iprioCandidate45) && hvictl.DPR.asBool)),
-    (Candidate123 && !Candidate45) -> true.B,
-  ))
-  val Candidate123LowCandidate45 = Mux1H(Seq(
-    (Candidate123 && Candidate4)   -> ((iprioCandidate123 > iprioCandidate45) || ((iprioCandidate123 === iprioCandidate45) && (findIndex(iidCandidate123) > findIndex(iidCandidate45)))),
-    (Candidate123 && Candidate5)   -> ((iprioCandidate123 > iprioCandidate45) || ((iprioCandidate123 === iprioCandidate45) && !hvictl.DPR.asBool)),
-    (!Candidate123 && Candidate45) -> true.B,
-  ))
-
-  val iidCandidate = Wire(UInt(12.W))
-  val iprioCandidate = Wire(UInt(11.W))
-  iidCandidate := Mux1H(Seq(
-    Candidate123HighCandidate45 -> iidCandidate123,
-    Candidate123LowCandidate45 -> iidCandidate45,
-  ))
-  iprioCandidate := Mux1H(Seq(
-    Candidate123HighCandidate45 -> iprioCandidate123,
-    Candidate123LowCandidate45 -> iprioCandidate45,
-  ))
+  // Candidate2,Candidate5 不可能同时成立
+  val onlyC1Enable = Candidate1 & !Candidate45
+  val onlyC2Enable = Candidate2 & !Candidate45
+  val onlyC3Enable = Candidate3 & !Candidate123
+  val onlyC4Enable = Candidate4 & !Candidate123
+  val onlyC5Enable = Candidate5 & !Candidate123
+  val C1C4Enable   = Candidate1 & Candidate4
+  val C1C5Enable   = Candidate1 & Candidate5
+  val C2C4Enable   = Candidate2 & Candidate4
+  val C3C4Enable   = Candidate3 & Candidate4
+  val C3C5Enable   = Candidate3 & Candidate5
+
+  val iidOnlyC1 = Wire(UInt(12.W))
+  val iidOnlyC4 = Wire(UInt(12.W))
+  val iidOnlyC5 = Wire(UInt(12.W))
+  val iprioOnlyC1 = Wire(UInt(8.W))
+  val iprioOnlyC2 = Wire(UInt(8.W))
+  val iprioOnlyC3 = Wire(UInt(8.W))
+  val iprioOnlyC4 = Wire(UInt(8.W))
+  val iprioOnlyC5 = Wire(UInt(8.W))
+  val iidC1C4 = Wire(UInt(12.W))
+  val iidC1C5 = Wire(UInt(12.W))
+  val iidC2C4 = Wire(UInt(12.W))
+  val iidC3C4 = Wire(UInt(12.W))
+  val iidC3C5 = Wire(UInt(12.W))
+  val iprioC1C4 = Wire(UInt(8.W))
+  val iprioC1C5 = Wire(UInt(8.W))
+  val iprioC2C4 = Wire(UInt(8.W))
+  val iprioC3C4 = Wire(UInt(8.W))
+  val iprioC3C5 = Wire(UInt(8.W))
+
+  iidOnlyC1 := InterruptNO.SEI.U
+  iidOnlyC4 := vsIidNum
+  iidOnlyC5 := hvictl.IID.asUInt
+
+  val iidC4Idx = Wire(UInt(12.W))
+  iidC4Idx := vsIidIdxReg
+
+  val iprioC1   = Wire(UInt(11.W))
+  val iprioC2C5 = Wire(UInt(11.W))
+  val iprioC4   = Wire(UInt(11.W))
+  val iprioC1Tmp = Wire(UInt(8.W))
+  val iprioC4Tmp = Wire(UInt(8.W))
+  val iprioC3C5Tmp = Wire(UInt(8.W))
+
+  val hvictlDPR = Mux(hvictl.DPR.asBool, 255.U, 0.U)
+  val C1GreaterThan255 = vstopei.IPRIO.asUInt(10, 8).orR
+  val C4IsZero = !hvipriosRegTmp.prioNum.orR
+  val C2C5IsZero = !hvictl.IPRIO.asUInt.orR
+  val C4HighVSEI = iidC4Idx < findIndex(InterruptNO.VSEI.U)
+  val SEIHighC4 = findIndex(InterruptNO.SEI.U) < iidC4Idx
+  val iprioC1GreaterThan255 = Mux(C1GreaterThan255, 255.U, iprioC1Tmp)
+
+  iprioC1 := vstopei.IPRIO.asUInt
+  iprioC2C5 := hvictl.IPRIO.asUInt
+  iprioC4 := hvipriosRegTmp.prioNum
+
+  iprioC1Tmp := iprioC1(7, 0)
+  iprioC4Tmp := Mux(C4IsZero, Mux(C4HighVSEI, 0.U, 255.U), iprioC4)
+  iprioC3C5Tmp := Mux(C2C5IsZero, hvictlDPR, iprioC2C5)
+
+  iprioOnlyC1 := iprioC1GreaterThan255
+  iprioOnlyC2 := iprioC2C5
+  iprioOnlyC3 := 255.U
+  iprioOnlyC4 := iprioC4Tmp
+  iprioOnlyC5 := iprioC3C5Tmp
+
+  // C1,C4 enable
+  when(C4IsZero) {
+    iidC1C4 := Mux(C4HighVSEI, iidOnlyC4, iidOnlyC1)
+    iprioC1C4 := Mux(C4HighVSEI, 0.U, iprioC1GreaterThan255)
+  }.elsewhen(iprioC1 < iprioC4) {
+    iidC1C4 := iidOnlyC1
+    iprioC1C4 := iprioC1Tmp
+  }.elsewhen(iprioC1 === iprioC4) {
+    iidC1C4 := Mux(SEIHighC4, iidOnlyC1, iidOnlyC4)
+    iprioC1C4 := Mux(SEIHighC4, iprioC1Tmp, iprioC4)
+  }.otherwise {
+    iidC1C4 := iidOnlyC4
+    iprioC1C4 := iprioC4
+  }
+
+  // C1,C5 enable
+  iidC1C5 := Mux(hvictl.DPR.asBool, iidOnlyC1, iidOnlyC5)
+  when(C2C5IsZero) {
+    iprioC1C5 := Mux(hvictl.DPR.asBool, iprioC1GreaterThan255, 0.U)
+  }.elsewhen(iprioC1 < iprioC2C5) {
+    iidC1C5 := iidOnlyC1
+    iprioC1C5 := iprioC1Tmp
+  }.elsewhen(iprioC1 === iprioC2C5) {
+    iprioC1C5 := Mux(hvictl.DPR.asBool, iprioC1Tmp, iprioC2C5)
+  }.otherwise {
+    iidC1C5 := iidOnlyC5
+    iprioC1C5 := iprioC3C5Tmp
+  }
+  
+  // C2,C4 enable
+  when(C4IsZero) {
+    iidC2C4 := Mux(C4HighVSEI, iidOnlyC4, iidOnlyC1)
+    iprioC2C4 := Mux(C4HighVSEI, 0.U, iprioC2C5)
+  }.elsewhen(iprioC2C5 < iprioC4) {
+    iidC2C4 := iidOnlyC1
+    iprioC2C4 := iprioC2C5
+  }.elsewhen(iprioC2C5 === iprioC4) {
+    iidC2C4 := Mux(SEIHighC4, iidOnlyC1, iidOnlyC4)
+    iprioC2C4 := Mux(SEIHighC4, iprioC2C5, iprioC4)
+  }.otherwise {
+    iidC2C4 := iidOnlyC4
+    iprioC2C4 := iprioC4
+  }
+
+  // C3,C4 enable
+  iidC3C4 := Mux(C4IsZero, Mux(C4HighVSEI, iidOnlyC4, iidOnlyC1), iidOnlyC4)
+  iprioC3C4 := iprioC4Tmp
+  // C3,C5 enable
+  iidC3C5 := Mux(C2C5IsZero, Mux(hvictl.DPR.asBool, iidOnlyC5, iidOnlyC1), iidOnlyC5)
+  iprioC3C5 := iprioC3C5Tmp
 
   // update vstopi
-  io.out.vstopi.IID := Mux(CandidateNoValid, 0.U, iidCandidate)
-  io.out.vstopi.IPRIO := Mux1H(Seq(
-    CandidateNoValid -> 0.U,
-    (iprioCandidate > 255.U) -> 255.U,
-    (Candidate123LowCandidate45 && Candidate5 && !hvictl.IPRIOM.asBool) -> 1.U,
-    ((Candidate123HighCandidate45 && iprioCandidate <= 255.U) || (Candidate123LowCandidate45 && Candidate4) || (Candidate123LowCandidate45 && Candidate5 && hvictl.IPRIOM.asBool)) -> iprioCandidate(7, 0),
-  ))
+  io.out.vstopi.IID := Mux(CandidateNoValid,
+    0.U,
+    Mux1H(Seq(
+      (Candidate123 & !Candidate45) -> iidOnlyC1,
+      onlyC4Enable -> iidOnlyC4,
+      onlyC5Enable -> iidOnlyC5,
+      C1C4Enable -> iidC1C4,
+      C1C5Enable -> iidC1C5,
+      C2C4Enable -> iidC2C4,
+      C3C4Enable -> iidC3C4,
+      C3C5Enable -> iidC3C5,
+    ))
+  )
+  io.out.vstopi.IPRIO := Mux(CandidateNoValid,
+    0.U,
+    Mux(!hvictl.IPRIOM.asBool, 1.U,
+      Mux1H(Seq(
+        onlyC1Enable -> iprioOnlyC1,
+        onlyC2Enable -> iprioOnlyC2,
+        onlyC3Enable -> iprioOnlyC3,
+        onlyC4Enable -> iprioOnlyC4,
+        onlyC5Enable -> iprioOnlyC5,
+        C1C4Enable -> iprioC1C4,
+        C1C5Enable -> iprioC1C5,
+        C2C4Enable -> iprioC2C4,
+        C3C4Enable -> iprioC3C4,
+        C3C5Enable -> iprioC3C5,
+      ))
+    )
+  )
 
   val mIRVecTmp = Mux(
     privState.isModeM && mstatusMIE || privState < PrivState.ModeM,
@@ -430,7 +518,8 @@ class InterruptFilter extends Module {
 
   // virtual interrupt with hvictl injection
   val vsIRModeCond = privState.isModeVS && vsstatusSIE || privState < PrivState.ModeVS
-  val SelectCandidate5 = Candidate123LowCandidate45 && Candidate5
+  val SelectCandidate5 = onlyC5Enable || C3C5Enable ||
+                         C1C5Enable && (iprioC1 === iprioC2C5 && !hvictl.DPR.asBool || iprioC1 > iprioC2C5)
   // delay at least 6 cycles to maintain the atomic of sret/mret
   // 65bit indict current interrupt is NMI
   val intrVecReg = RegInit(0.U(8.W))
```
