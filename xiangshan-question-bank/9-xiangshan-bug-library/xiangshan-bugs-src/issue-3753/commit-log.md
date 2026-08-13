# Commit Log
- Issue: #3753
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3753
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3753
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3753
- Changed files: 8
- Additions: 149
- Deletions: 207

## Files
- `difftest`
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index 14117b14a78..7d3d2fb71f4 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit 14117b14a78a14e3a79a228891f765fac7718435
+Subproject commit 7d3d2fb71f410c320f62643864d8d06732170280
diff --git a/ready-to-run b/ready-to-run
index 82756052a01..1b2a652c0a6 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 82756052a014fa97c0ef22e37f6d7f5320e4ffc5
+Subproject commit 1b2a652c0a6ff1a41892443207172e759768b558
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
index 1af01a4970b..a2640480140 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
@@ -4,7 +4,7 @@ import chisel3._
 import chisel3.util._
 import freechips.rocketchip.rocket.CSRs
 import CSRConfig._
-import xiangshan.backend.fu.NewCSR.CSRBundles.PrivState
+import xiangshan.backend.fu.NewCSR.CSRBundles._
 import xiangshan.backend.fu.NewCSR.CSRConfig._
 import xiangshan.backend.fu.NewCSR.CSRDefines.{CSRROField => RO, CSRRWField => RW, _}
 
@@ -93,54 +93,22 @@ trait CSRAIA { self: NewCSR with HypervisorLevel =>
   val miprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle))
     .setAddr(0x30)
 
-  val miprio2 = Module(new CSRModule(s"Iprio2", new Iprio2Bundle))
-    .setAddr(0x32)
-
-  val miprio4 = Module(new CSRModule(s"Iprio4", new IprioBundle))
-    .setAddr(0x34)
-
-  val miprio6 = Module(new CSRModule(s"Iprio6", new IprioBundle))
-    .setAddr(0x36)
-
-  val miprio8 = Module(new CSRModule(s"Iprio8", new Iprio8Bundle))
-    .setAddr(0x38)
-
-  val miprio10 = Module(new CSRModule(s"Iprio10", new Iprio10Bundle))
-    .setAddr(0x3A)
-
-  val miprio12 = Module(new CSRModule(s"Iprio12", new IprioBundle))
-    .setAddr(0x3C)
-
-  val miprio14 = Module(new CSRModule(s"Iprio14", new IprioBundle))
-    .setAddr(0x3E)
+  val miprios: Seq[CSRModule[_]] = (2 to (0xF, 2)).map(num =>
+    Module(new CSRModule(s"Iprio$num", new IprioBundle))
+      .setAddr(0x30 + num)
+  )
 
   val siprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle))
     .setAddr(0x30)
 
-  val siprio2 = Module(new CSRModule(s"Iprio2", new Iprio2Bundle))
-    .setAddr(0x32)
-
-  val siprio4 = Module(new CSRModule(s"Iprio4", new IprioBundle))
-    .setAddr(0x34)
-
-  val siprio6 = Module(new CSRModule(s"Iprio6", new IprioBundle))
-    .setAddr(0x36)
-
-  val siprio8 = Module(new CSRModule(s"Iprio8", new Iprio8Bundle))
-    .setAddr(0x38)
-
-  val siprio10 = Module(new CSRModule(s"Iprio10", new Iprio10Bundle))
-    .setAddr(0x3A)
-
-  val siprio12 = Module(new CSRModule(s"Iprio12", new IprioBundle))
-    .setAddr(0x3C)
-
-  val siprio14 = Module(new CSRModule(s"Iprio14", new IprioBundle))
-    .setAddr(0x3E)
+  val siprios: Seq[CSRModule[_]] = (2 to (0xF, 2)).map(num =>
+    Module(new CSRModule(s"Iprio$num", new IprioBundle))
+    .setAddr(0x30 + num)
+  )
 
-  val miregiprios: Seq[CSRModule[_]] = Seq(miprio0, miprio2, miprio4, miprio6, miprio8, miprio10, miprio12, miprio14)
+  val miregiprios: Seq[CSRModule[_]] = Seq(miprio0) ++: miprios
 
-  val siregiprios: Seq[CSRModule[_]] = Seq(siprio0, siprio2, siprio4, siprio6, siprio8, siprio10, siprio12, siprio14)
+  val siregiprios: Seq[CSRModule[_]] = Seq(siprio0) ++: siprios
 
   val aiaCSRMods = Seq(
     miselect,
@@ -157,15 +125,6 @@ trait CSRAIA { self: NewCSR with HypervisorLevel =>
     vstopei,
   )
 
-  val aiaSkipCSRs = Seq(
-    mtopei,
-    mtopi,
-    stopei,
-    stopi,
-    vstopi,
-    vstopei,
-  )
-
   val aiaCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_], UInt)] = SeqMap.from(
     aiaCSRMods.map(csr => (csr.addr -> (csr.w -> csr.rdata))).iterator
   )
@@ -244,9 +203,7 @@ class TopEIBundle extends CSRBundle {
   val IPRIO = RW(10, 0)
 }
 
-class IprioBundle extends CSRBundle {
-  val ALL = RO(63, 0).withReset(0.U)
-}
+class IprioBundle extends FieldInitBundle
 
 class Iprio0Bundle extends CSRBundle {
   val PrioSSI  = RW(15,  8).withReset(0.U)
@@ -257,22 +214,6 @@ class Iprio0Bundle extends CSRBundle {
   val PrioMTI  = RW(63, 56).withReset(0.U)
 }
 
-class Iprio2Bundle extends CSRBundle {
-  val PrioSEI  = RW(15,  8).withReset(0.U)
-  val PrioVSEI = RW(23, 16).withReset(0.U)
-  val PrioMEI  = RW(31, 24).withReset(0.U)
-  val PrioSGEI = RW(39, 32).withReset(0.U)
-  val PrioCOI  = RW(47, 40).withReset(0.U)
-}
-
-class Iprio8Bundle extends CSRBundle {
-  val PrioLPRASEI = RW(31, 24).withReset(0.U)
-}
-
-class Iprio10Bundle extends CSRBundle {
-  val PrioHPRASEI = RW(31, 24).withReset(0.U)
-}
-
 class CSRToAIABundle extends Bundle {
   private final val AddrWidth = 12
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index edd669b206c..8564b9563b6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -314,20 +314,11 @@ class Hviprio1Bundle extends CSRBundle {
   val PrioSSI = RW(15,  8).withReset(0.U)
   val PrioSTI = RW(31, 24).withReset(0.U)
   val PrioCOI = RW(47, 40).withReset(0.U)
-  val Prio14  = RO(55, 48).withReset(0.U)
-  val Prio15  = RO(63, 56).withReset(0.U)
+  val Prio14  = RW(55, 48).withReset(0.U)
+  val Prio15  = RW(63, 56).withReset(0.U)
 }
 
-class Hviprio2Bundle extends CSRBundle {
-  val Prio16 = RO(7, 0).withReset(0.U)
-  val Prio17 = RO(15, 8).withReset(0.U)
-  val Prio18 = RO(23, 16).withReset(0.U)
-  val Prio19 = RO(31, 24).withReset(0.U)
-  val Prio20 = RO(39, 32).withReset(0.U)
-  val Prio21 = RO(47, 40).withReset(0.U)
-  val Prio22 = RO(55, 48).withReset(0.U)
-  val Prio23 = RO(63, 56).withReset(0.U)
-}
+class Hviprio2Bundle extends FieldInitBundle
 
 class HgatpBundle extends CSRBundle {
   val MODE = HgatpMode(63, 60, wNoFilter).withReset(HgatpMode.Bare)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
index 71f6b8ac964..757187186ba 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
@@ -328,12 +328,12 @@ object InterruptNO {
   final val LPRASEI = 35
   final val HPRASEI = 43
 
-  val interruptDefaultPrio = Seq(
+  val privArchGroup = Seq(
     MEI, MSI, MTI,
     SEI, SSI, STI,
     SGEI,
     VSEI, VSSI, VSTI,
-    COI,
+    COI, 14, 15,
   )
 
   val localHighGroup = Seq(
@@ -370,6 +370,14 @@ object InterruptNO {
     49, 24, 48,
   )
 
+  val interruptDefaultPrio = customHighestGroup ++
+                            localHighGroup ++
+                            customMiddleHighGroup ++
+                            privArchGroup ++
+                            customMiddleLowGroup ++
+                            localLowGroup ++
+                            customLowestGroup
+
   def getPrioIdxInGroup(group: this.type => Seq[Int])(f: this.type => Int): Int = {
     val idx = group(this).indexOf(f(this))
     assert(idx != -1)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 1d5ab11ca4b..1a94f43352a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -51,24 +51,28 @@ class InterruptFilter extends Module {
   private val hsie = hie.asUInt | sie.asUInt
 
   val mtopiIsNotZero: Bool = (mip & mie & (~mideleg).asUInt) =/= 0.U
-  val stopiIsNotZero: Bool = privState.isModeHS && ((hsip & hsie & (~hideleg).asUInt) =/= 0.U)
+  val stopiIsNotZero: Bool = (hsip & hsie & (~hideleg).asUInt) =/= 0.U
 
   val mIpriosIsZero : Bool = miprios  === 0.U
   val hsIpriosIsZero: Bool = hsiprios === 0.U
 
-  val mtopigather = mip & mie
-  val hstopigather = hsip & hsie
+  val mtopigather = mip & mie & (~mideleg).asUInt
+  val hstopigather = hsip & hsie & (~hideleg).asUInt
   val vstopigather = vsip & vsie
   val mipriosSort: Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
   val hsipriosSort: Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
-  val hvipriosSort: Vec[UInt] = VecInit(Seq.fill(3)(0.U(9.W)))
+  val hvipriosSort: Vec[UInt] = VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(9.W)))
   InterruptNO.interruptDefaultPrio.zipWithIndex.foreach { case (value, index) =>
-    mipriosSort(index) := Mux(mtopigather(value), Cat(1.U, miprios(7 + 8 * value, 8 * value)), 0.U)
+    mipriosSort(index)  := Mux(mtopigather(value), Cat(1.U, miprios(7 + 8 * value, 8 * value)), 0.U)
     hsipriosSort(index) := Mux(hstopigather(value), Cat(1.U, hsiprios(7 + 8 * value, 8 * value)), 0.U)
+    hvipriosSort(index) := Mux(vstopigather(value), Cat(1.U, 0.U(8.W)), 0.U)
+  }
+  hvipriosSort(findIndex(1.U)) := Mux(vstopigather(1).asBool, Cat(1.U, hviprio1.PrioSSI.asUInt), 0.U)
+  hvipriosSort(findIndex(5.U)) := Mux(vstopigather(5).asBool, Cat(1.U, hviprio1.PrioSTI.asUInt), 0.U)
+
+  for (i <- 0 to 10) {
+    hvipriosSort(findIndex((i+13).U)) := Mux(vstopigather(i+13).asBool, Cat(1.U, hviprios(7 + 8 * (i+5), 8 * (i+5))), 0.U)
   }
-  hvipriosSort(0) := Mux(vstopigather(1).asBool, Cat(1.U, hviprios(15, 8)), 0.U)
-  hvipriosSort(1) := Mux(vstopigather(5).asBool, Cat(1.U, hviprios(31, 24)), 0.U)
-  hvipriosSort(2) := Mux(vstopigather(13).asBool, Cat(1.U, hviprios(47, 40)), 0.U)
 
   def findNum(input: UInt): UInt = {
     val select = Mux1H(UIntToOH(input), InterruptNO.interruptDefaultPrio.map(_.U))
@@ -86,49 +90,81 @@ class InterruptFilter extends Module {
   }
 
   // value lower, priority higher
-  def minSelect(index: Vec[UInt], value: Vec[UInt]): (Vec[UInt], Vec[UInt]) = {
+  def minSelect(index: Vec[UInt], value: Vec[UInt], xei: UInt): (Vec[UInt], Vec[UInt]) = {
     value.size match {
       case 1 =>
         (index, value)
       case 2 =>
+        /**
+         * default: index(0) priority > index(1) priority
+         *
+         * AIA Spec table 5.3/5.5
+         *
+         * xei is InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U for M
+         * xei is InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U for S
+         *
+         * if index(0) enable, index(1) disable:
+         *    select index(0)
+         * else if index(0) disable, index(1) enable:
+         *    select index(1)
+         * else if index(0), index(1) all enable:
+         *    if index(0), index(1) priority number all 0s:
+         *      select index(0)
+         *    else if index(0) priority number is 0, index(1) priority number is not 0:
+         *      if index(0) <= xei, index(1) > xei:
+         *        select index(0)
+         *      else:
+         *        select index(1)
+         *    else if index(0) priority number is not 0, index(1) priority number is 0:
+         *      if index(1) <= xei, index(0) > xei:
+         *        select index(1)
+         *      else:
+         *        select index(0)
+         *    else if index(0) priority number is not 0, index(1) priority number is not 0:
+         *      if value(0) <= value(1):
+         *        select index(0)
+         *      else:
+         *        select index(1)
+         */
         val minIndex = Mux1H(Seq(
-          (value(0)(8).asBool && (value(1)(8).asBool && (value(0)(7, 0) < value(1)(7 ,0)) || !value(1)(8).asBool)) -> index(0),
-          (value(1)(8).asBool && (value(0)(8).asBool && (value(0)(7, 0) > value(1)(7, 0)) || !value(0)(8).asBool)) -> index(1),
-          (value(0)(8).asBool && value(1)(8).asBool && (value(0)(7, 0) === value(1)(7, 0))) -> Mux(index(0) < index(1), index(0), index(1)),
+          ( value(0)(8).asBool && !value(1)(8).asBool) -> index(0),
+          (!value(0)(8).asBool &&  value(1)(8).asBool) -> index(1),
+          ( value(0)(8).asBool &&  value(1)(8).asBool) -> Mux1H(Seq(
+            (!value(0)(7, 0).orR && !value(1)(7, 0).orR) -> index(0),
+            (!value(0)(7, 0).orR &&  value(1)(7, 0).orR) -> Mux(index(0) <= xei && index(1) > xei, index(0), index(1)),
+            ( value(0)(7, 0).orR && !value(1)(7, 0).orR) -> Mux(index(1) <= xei && index(0) > xei, index(1), index(0)),
+            ( value(0)(7, 0).orR &&  value(1)(7, 0).orR) -> Mux(value(0)(7, 0) <= value(1)(7, 0), index(0), index(1)),
+          ))
         ))
         val minValue = Mux1H(Seq(
-          (value(0)(8).asBool && (value(1)(8).asBool && (value(0)(7, 0) < value(1)(7, 0)) || !value(1)(8).asBool)) -> value(0),
-          (value(1)(8).asBool && (value(0)(8).asBool && (value(0)(7, 0) > value(1)(7, 0)) || !value(0)(8).asBool)) -> value(1),
-          (value(0)(8).asBool && value(1)(8).asBool && (value(0)(7, 0) === value(1)(7, 0))) -> Mux(index(0) < index(1), value(0), value(1)),
+          ( value(0)(8).asBool && !value(1)(8).asBool) -> value(0),
+          (!value(0)(8).asBool &&  value(1)(8).asBool) -> value(1),
+          ( value(0)(8).asBool &&  value(1)(8).asBool) -> Mux1H(Seq(
+            (!value(0)(7, 0).orR && !value(1)(7, 0).orR) -> value(0),
+            (!value(0)(7, 0).orR &&  value(1)(7, 0).orR) -> Mux(index(0) <= xei && index(1) > xei, value(0), value(1)),
+            ( value(0)(7, 0).orR && !value(1)(7, 0).orR) -> Mux(index(1) <= xei && index(0) > xei, value(1), value(0)),
+            ( value(0)(7, 0).orR &&  value(1)(7, 0).orR) -> Mux(value(0)(7, 0) <= value(1)(7, 0), value(0), value(1)),
+          ))
         ))
         (VecInit(minIndex), VecInit(minValue))
       case _ =>
-        val (leftIndex,  leftValue)  = minSelect(VecInit(index.take((value.size + 1)/2)), VecInit(value.take((value.size + 1)/2)))
-        val (rightIndex, rightValue) = minSelect(VecInit(index.drop((value.size + 1)/2)), VecInit(value.drop((value.size + 1)/2)))
-        minSelect(VecInit(leftIndex ++ rightIndex), VecInit(leftValue ++ rightValue))
+        val (leftIndex,  leftValue)  = minSelect(VecInit(index.take((value.size + 1)/2)), VecInit(value.take((value.size + 1)/2)), xei)
+        val (rightIndex, rightValue) = minSelect(VecInit(index.drop((value.size + 1)/2)), VecInit(value.drop((value.size + 1)/2)), xei)
+        minSelect(VecInit(leftIndex ++ rightIndex), VecInit(leftValue ++ rightValue), xei)
     }
   }
 
-  def highIprio(iprios: Vec[UInt], vsMode: Boolean = false): (UInt, UInt) = {
-    if (vsMode) {
-      val index = WireInit(VecInit(Seq.fill(3)(0.U(6.W))))
-      for (i <- 0 until 3) {
-        index(i) := i.U
-      }
-      val result = minSelect(index, iprios)
-      (result._1(0), result._2(0)(7, 0))
-    } else {
-      val index = WireInit(VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(6.W))))
-      InterruptNO.interruptDefaultPrio.zipWithIndex.foreach { case (prio, i) =>
-        index(i) := i.U
-      }
-      val result = minSelect(index, iprios)
-      (result._1(0), result._2(0)(7, 0))
+  def highIprio(iprios: Vec[UInt], xei: UInt = 0.U): (UInt, UInt) = {
+    val index = WireInit(VecInit(Seq.fill(InterruptNO.interruptDefaultPrio.size)(0.U(6.W))))
+    InterruptNO.interruptDefaultPrio.zipWithIndex.foreach { case (prio, i) =>
+      index(i) := i.U
     }
+    val result = minSelect(index, iprios, xei)
+    (result._1(0), result._2(0)(7, 0))
   }
 
-  private val (mIidIdx,  mPrioNum)  = highIprio(mipriosSort)
-  private val (hsIidIdx, hsPrioNum) = highIprio(hsipriosSort)
+  private val (mIidIdx,  mPrioNum)  = highIprio(mipriosSort, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U)
+  private val (hsIidIdx, hsPrioNum) = highIprio(hsipriosSort, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U)
 
   private val mIidNum  = findNum(mIidIdx)
   private val hsIidNum = findNum(hsIidIdx)
@@ -185,17 +221,9 @@ class InterruptFilter extends Module {
   assert(PopCount(Cat(Candidate1, Candidate2, Candidate3)) < 2.U, "Only one Candidate could be select from Candidate1/2/3 in VS-level!")
   assert(PopCount(Cat(Candidate4, Candidate5)) < 2.U, "Only one Candidate could be select from Candidate4/5 in VS-level!")
 
-  val VSIidNumTmp = Wire(UInt(6.W))
-  val VSIidNum = Wire(UInt(6.W))
-  val VSPrioNum = Wire(UInt(8.W))
-  VSIidNumTmp := highIprio(hvipriosSort, vsMode = true)._1
-  VSPrioNum := highIprio(hvipriosSort, vsMode = true)._2
+  private val (vsIidIdx, vsPrioNum) = highIprio(hvipriosSort, InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.VSEI).U)
 
-  VSIidNum := Mux1H(Seq(
-    (VSIidNumTmp === 0.U) -> 1.U,
-    (VSIidNumTmp === 1.U) -> 5.U,
-    ((VSIidNumTmp =/= 0.U) && (VSIidNumTmp =/= 1.U)) -> (VSIidNumTmp + 11.U),
-  ))
+  private val vsIidNum = findNum(vsIidIdx)
 
   val iidCandidate123   = Wire(UInt(12.W))
   val iidCandidate45    = Wire(UInt(12.W))
@@ -208,11 +236,11 @@ class InterruptFilter extends Module {
     Candidate3 -> 256.U,
   ))
   iidCandidate45 := Mux1H(Seq(
-    Candidate4 -> VSIidNum,
+    Candidate4 -> vsIidNum,
     Candidate5 -> hvictl.IID.asUInt,
   ))
   iprioCandidate45 := Mux1H(Seq(
-    Candidate4 -> VSPrioNum,
+    Candidate4 -> vsPrioNum,
     Candidate5 -> hvictl.IPRIO.asUInt,
   ))
 
@@ -252,35 +280,43 @@ class InterruptFilter extends Module {
 
   val mIRVec = Mux(
     privState.isModeM && mstatusMIE || privState < PrivState.ModeM,
-    mip.asUInt & mie.asUInt & (~(mideleg.asUInt)).asUInt,
+    io.out.mtopi.IID.asUInt,
     0.U
   )
 
   val hsIRVec = Mux(
     privState.isModeHS && sstatusSIE || privState < PrivState.ModeHS,
-    hsip & hsie & (~(hideleg.asUInt)).asUInt,
+    io.out.stopi.IID.asUInt,
     0.U
   )
 
   val vsIRVec = Mux(
     privState.isModeVS && vsstatusSIE || privState < PrivState.ModeVS,
-    vsip.asUInt & vsie.asUInt,
+    io.out.vstopi.IID.asUInt,
     0.U
   )
 
-  val vsMapHostIRVec = Cat((0 until vsIRVec.getWidth).map { num =>
+  val mIRNotZero  = mIRVec.orR
+  val hsIRNotZero = hsIRVec.orR
+  val vsIRNotZero = vsIRVec.orR
+
+  val mIRVecOH  = Mux(mIRNotZero,  UIntToOH(mIRVec,  64), 0.U)
+  val hsIRVecOH = Mux(hsIRNotZero, UIntToOH(hsIRVec, 64), 0.U)
+  val vsIRVecOH = Mux(vsIRNotZero, UIntToOH(vsIRVec, 64), 0.U)
+
+  val vsMapHostIRVec = Cat((0 until vsIRVecOH.getWidth).map { num =>
     // 2,6,10
     if (InterruptNO.getVS.contains(num)) {
       // 1,5,9
       val sNum = num - 1
-      vsIRVec(sNum)
+      vsIRVecOH(sNum)
     }
     // 1,5,9
     else if(InterruptNO.getHS.contains(num)) {
       0.U(1.W)
     }
     else {
-      vsIRVec(num)
+      vsIRVecOH(num)
     }
   }.reverse)
 
@@ -292,9 +328,9 @@ class InterruptFilter extends Module {
   val disableAllIntr = disableDebugIntr || !io.in.mnstatusNMIE
   val debugInterupt = ((io.in.debugIntr && !disableDebugIntr)  << CSRConst.IRQ_DEBUG).asUInt
 
-  val normalIntrVec = Mux(mIRVec.orR, mIRVec,
-                        Mux(hsIRVec.orR, hsIRVec,
-                          Mux(vsMapHostIRVec.orR, vsMapHostIRVec, 0.U)))
+  val normalIntrVec = Mux(mIRNotZero, mIRVecOH,
+                        Mux(hsIRNotZero, hsIRVecOH,
+                          Mux(vsIRNotZero, vsMapHostIRVec, 0.U)))
   val intrVec = VecInit(Mux(io.in.nmi, io.in.nmiVec, normalIntrVec).asBools.map(IR => IR && !disableAllIntr)).asUInt | debugInterupt
 
   // virtual interrupt with hvictl injection
@@ -305,17 +341,25 @@ class InterruptFilter extends Module {
   val intrVecReg = RegInit(0.U(64.W))
   val nmiReg = RegInit(false.B)
   val viIsHvictlInjectReg = RegInit(false.B)
+  val irToHSReg = RegInit(false.B)
+  val irToVSReg = RegInit(false.B)
   intrVecReg := intrVec
   nmiReg := io.in.nmi
   viIsHvictlInjectReg := vsIRModeCond && SelectCandidate5
+  irToHSReg := !mIRNotZero && hsIRNotZero
+  irToVSReg := !mIRNotZero && !hsIRNotZero && vsIRNotZero
   val delayedIntrVec = DelayN(intrVecReg, 5)
   val delayedNMI = DelayN(nmiReg, 5)
   val delayedVIIsHvictlInjectReg = DelayN(viIsHvictlInjectReg, 5)
+  val delayedIRToHS = DelayN(irToHSReg, 5)
+  val delayedIRToVS = DelayN(irToVSReg, 5)
 
   io.out.interruptVec.valid := delayedIntrVec.orR || delayedVIIsHvictlInjectReg
   io.out.interruptVec.bits := delayedIntrVec
   io.out.nmi := delayedNMI
   io.out.virtualInterruptIsHvictlInject := delayedVIIsHvictlInjectReg & !delayedNMI
+  io.out.irToHS := delayedIRToHS & !delayedNMI
+  io.out.irToVS := delayedIRToVS & !delayedNMI
 
   dontTouch(hsip)
   dontTouch(hsie)
@@ -366,5 +410,7 @@ class InterruptFilterIO extends Bundle {
     val stopi  = new TopIBundle
     val vstopi = new TopIBundle
     val virtualInterruptIsHvictlInject = Bool()
+    val irToHS = Bool()
+    val irToVS = Bool()
   })
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index a15f31324c3..feeb3d5ab30 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -372,6 +372,8 @@ class NewCSR(implicit val p: Parameters) extends Module
   val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
   val nmi = RegEnable(intrMod.io.out.nmi, false.B, intrMod.io.out.interruptVec.valid)
   val virtualInterruptIsHvictlInject = RegEnable(intrMod.io.out.virtualInterruptIsHvictlInject, false.B, intrMod.io.out.interruptVec.valid)
+  val irToHS = RegEnable(intrMod.io.out.irToHS, false.B, intrMod.io.out.interruptVec.valid)
+  val irToVS = RegEnable(intrMod.io.out.irToVS, false.B, intrMod.io.out.interruptVec.valid)
 
   val trapHandleMod = Module(new TrapHandleModule)
 
@@ -380,6 +382,8 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapHandleMod.io.in.trapInfo.bits.nmi := nmi
   trapHandleMod.io.in.trapInfo.bits.intrVec := intrVec
   trapHandleMod.io.in.trapInfo.bits.isInterrupt := trapIsInterrupt
+  trapHandleMod.io.in.trapInfo.bits.irToHS := irToHS
+  trapHandleMod.io.in.trapInfo.bits.irToVS := irToVS
   trapHandleMod.io.in.privState := privState
   trapHandleMod.io.in.mstatus  := mstatus.regOut
   trapHandleMod.io.in.vsstatus := vsstatus.regOut
@@ -843,8 +847,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   // perf
   val addrInPerfCnt = (wenLegal || ren) && (
     (addr >= CSRs.mcycle.U) && (addr <= CSRs.mhpmcounter31.U) ||
-    (addr >= CSRs.cycle.U) && (addr <= CSRs.hpmcounter31.U) ||
-    Cat(aiaSkipCSRs.map(_.addr.U === addr)).orR
+    (addr >= CSRs.cycle.U) && (addr <= CSRs.hpmcounter31.U)
   )
 
   // flush
@@ -1486,6 +1489,12 @@ class NewCSR(implicit val p: Parameters) extends Module
     }).orR
     diffMhpmeventOverflowEvent.mhpmeventOverflow := VecInit(mhpmevents.map(_.regOut.asInstanceOf[MhpmeventBundle].OF.asBool)).asUInt
 
+    val diffAIAXtopeiEvent = DifftestModule(new DiffAIAXtopeiEvent)
+    diffAIAXtopeiEvent.coreid := hartId
+    diffAIAXtopeiEvent.valid := fromAIA.rdata.valid
+    diffAIAXtopeiEvent.mtopei := mtopei.rdata
+    diffAIAXtopeiEvent.stopei := stopei.rdata
+    diffAIAXtopeiEvent.vstopei := vstopei.rdata
   }
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index 3c2eee627c6..f682af85b66 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -34,32 +34,9 @@ class TrapHandleModule extends Module {
   private val hasEXVec = Mux(hasEX, exceptionVec, 0.U)
   private val hasIRVec = Mux(hasIR, intrVec, 0.U)
 
-  private val interruptGroups: Seq[(Seq[Int], String)] = Seq(
-    InterruptNO.customHighestGroup    -> "customHighest",
-    InterruptNO.localHighGroup        -> "localHigh",
-    InterruptNO.customMiddleHighGroup -> "customMiddleHigh",
-    InterruptNO.interruptDefaultPrio  -> "privArch",
-    InterruptNO.customMiddleLowGroup  -> "customMiddleLow",
-    InterruptNO.localLowGroup         -> "localLow",
-    InterruptNO.customLowestGroup     -> "customLowest",
-  )
-
-  private val filteredIRQs: Seq[UInt] = interruptGroups.map {
-    case (irqGroup, name) => (getMaskFromIRQGroup(irqGroup) & hasIRVec).suggestName(s"filteredIRQs_$name")
-  }
-  private val hasIRQinGroup: Seq[Bool] = interruptGroups.map {
-    case (irqGroup, name) => dontTouch(Cat(filterIRQs(irqGroup, hasIRVec)).orR.suggestName(s"hasIRQinGroup_$name"))
-  }
-
-  private val highestIRQinGroup: Seq[Vec[Bool]] = interruptGroups zip filteredIRQs map {
-    case ((irqGroup: Seq[Int], name), filteredIRQ: UInt) =>
-      produceHighIRInGroup(irqGroup, filteredIRQ).suggestName(s"highestIRQinGroup_$name")
-  }
+  private val irToHS = io.in.trapInfo.bits.irToHS
+  private val irToVS = io.in.trapInfo.bits.irToVS
 
-  private val highestPrioIRVec: Vec[Bool] = MuxCase(
-    0.U.asTypeOf(Vec(64, Bool())),
-    hasIRQinGroup zip highestIRQinGroup map{ case (hasIRQ: Bool, highestIRQ: Vec[Bool]) => hasIRQ -> highestIRQ }
-  )
   private val highestPrioNMIVec = Wire(Vec(64, Bool()))
   highestPrioNMIVec.zipWithIndex.foreach { case (irq, i) =>
     if (NonMaskableIRNO.interruptDefaultPrio.contains(i)) {
@@ -85,23 +62,18 @@ class TrapHandleModule extends Module {
       excp := false.B
   }
 
-  private val highestPrioIR  = highestPrioIRVec.asUInt
+  private val highestPrioIR  = hasIRVec.asUInt
   private val highestPrioNMI = highestPrioNMIVec.asUInt
   private val highestPrioEX  = highestPrioEXVec.asUInt
 
-
-  private val mIRVec  = dontTouch(WireInit(highestPrioIR))
-  private val hsIRVec = (mIRVec  & mideleg) | (mIRVec  & mvien & ~mideleg)
-  private val vsIRVec = (hsIRVec & hideleg) | (hsIRVec & hvien & ~hideleg)
-
   private val mEXVec  = highestPrioEX
   private val hsEXVec = highestPrioEX & medeleg
   private val vsEXVec = highestPrioEX & medeleg & hedeleg
 
   // nmi handle in MMode only and default handler is mtvec
-  private val  mHasIR =  mIRVec.orR
-  private val hsHasIR = hsIRVec.orR & !hasNMI
-  private val vsHasIR = (vsIRVec.orR || hasIR && virtualInterruptIsHvictlInject) & !hasNMI
+  private val  mHasIR = hasIR
+  private val hsHasIR = hasIR && irToHS & !hasNMI
+  private val vsHasIR = hasIR && irToVS & !hasNMI
 
   private val  mHasEX =  mEXVec.orR
   private val hsHasEX = hsEXVec.orR
@@ -150,34 +122,6 @@ class TrapHandleModule extends Module {
   io.out.hasDTExcp := hasDTExcp
   io.out.dbltrpToMN := dbltrpToMN
 
-  def filterIRQs(group: Seq[Int], originIRQ: UInt): Seq[Bool] = {
-    group.map(irqNum => originIRQ(irqNum))
-  }
-
-  def getIRQHigherThanInGroup(group: Seq[Int])(irq: Int): Seq[Int] = {
-    val idx = group.indexOf(irq, 0)
-    require(idx != -1, s"The irq($irq) does not exists in IntPriority Seq")
-    group.slice(0, idx)
-  }
-
-  def getMaskFromIRQGroup(group: Seq[Int]): UInt = {
-    group.map(irq => BigInt(1) << irq).reduce(_ | _).U
-  }
-
-  def produceHighIRInGroup(irqGroup: Seq[Int], filteredIRVec: UInt): Vec[Bool] = {
-    val irVec = Wire(Vec(64, Bool()))
-    irVec.zipWithIndex.foreach { case (irq, i) =>
-      if (irqGroup.contains(i)) {
-        val higherIRSeq: Seq[Int] = getIRQHigherThanInGroup(irqGroup)(i)
-        irq := (
-          higherIRSeq.nonEmpty.B && Cat(higherIRSeq.map(num => !filteredIRVec(num))).andR ||
-            higherIRSeq.isEmpty.B
-          ) && filteredIRVec(i)
-      } else
-        irq := false.B
-    }
-    irVec
-  }
 }
 
 class TrapHandleIO extends Bundle {
@@ -188,6 +132,9 @@ class TrapHandleIO extends Bundle {
       val intrVec = UInt(64.W)
       val isInterrupt = Bool()
       val singleStep = Bool()
+      // trap to x mode
+      val irToHS = Bool()
+      val irToVS = Bool()
     })
     val privState = new PrivState
     val mstatus = new MstatusBundle
```
