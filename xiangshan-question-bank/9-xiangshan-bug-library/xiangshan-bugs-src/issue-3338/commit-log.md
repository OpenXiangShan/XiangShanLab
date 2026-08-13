# Commit Log
- Issue: #3338
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3338
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3338
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3338
- Changed files: 3
- Additions: 94
- Deletions: 26

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
index e6e23b8d01d..2070a1e8447 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptBundle.scala
@@ -308,29 +308,53 @@ object InterruptNO {
   final val HPRASEI = 43
 
   val interruptDefaultPrio = Seq(
-    HPRASEI,
-
     MEI, MSI, MTI,
     SEI, SSI, STI,
     SGEI,
     VSEI, VSSI, VSTI,
     COI,
+  )
+
+  val localHighGroup = Seq(
+    47, 23, 46,
+    45, 22, 44,
+    HPRASEI, 21, 42,
+    41, 20, 40,
+  )
+
+  val localLowGroup = Seq(
+    39, 19, 38,
+    37, 18, 36,
+    LPRASEI, 17, 34,
+    33, 16, 32,
+  )
+
+  val customHighestGroup = Seq(
+    63, 31, 62,
+    61, 30, 60,
+  )
+
+  val customMiddleHighGroup = Seq(
+    59, 29, 58,
+    57, 28, 56,
+  )
 
-    LPRASEI
+  val customMiddleLowGroup = Seq(
+    55, 27, 54,
+    53, 26, 52,
   )
 
-  def getPrioIdx(f: this.type => Int): Int = {
-    val idx = this.interruptDefaultPrio.indexOf(f(this))
+  val customLowestGroup = Seq(
+    51, 25, 50,
+    49, 24, 48,
+  )
+
+  def getPrioIdxInGroup(group: this.type => Seq[Int])(f: this.type => Int): Int = {
+    val idx = group(this).indexOf(f(this))
     assert(idx != -1)
     idx
   }
 
-  def getIRQHigherThan(irq: Int): Seq[Int] = {
-    val idx = this.interruptDefaultPrio.indexOf(irq, 0)
-    require(idx != -1, s"The irq($irq) does not exists in IntPriority Seq")
-    this.interruptDefaultPrio.slice(0, idx)
-  }
-
   def getVS = Seq(VSSI, VSTI, VSEI)
 
   def getHS = Seq(SSI, STI, SEI)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 0fd20f48ad4..1dfc2d0ce3a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -140,11 +140,11 @@ class InterruptFilter extends Module {
   private val mIidNum  = findNum(mIidIdx)
   private val hsIidNum = findNum(hsIidIdx)
 
-  private val mIidDefaultPrioHighMEI: Bool = mIidIdx < InterruptNO.getPrioIdx(_.MEI).U
-  private val mIidDefaultPrioLowMEI : Bool = mIidIdx > InterruptNO.getPrioIdx(_.MEI).U
+  private val mIidDefaultPrioHighMEI: Bool = mIidIdx < InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
+  private val mIidDefaultPrioLowMEI : Bool = mIidIdx > InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.MEI).U
 
-  private val hsIidDefaultPrioHighSEI: Bool = hsIidIdx < InterruptNO.getPrioIdx(_.SEI).U
-  private val hsIidDefaultPrioLowSEI : Bool = hsIidIdx > InterruptNO.getPrioIdx(_.SEI).U
+  private val hsIidDefaultPrioHighSEI: Bool = hsIidIdx < InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
+  private val hsIidDefaultPrioLowSEI : Bool = hsIidIdx > InterruptNO.getPrioIdxInGroup(_.interruptDefaultPrio)(_.SEI).U
 
   val mtopiPrioNumReal = mPrioNum
   val stopiPrioNumReal = hsPrioNum
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index 718fd014179..45a7606ebe5 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -27,19 +27,34 @@ class TrapHandleModule extends Module {
   private val hasEXVec = Mux(hasEX, exceptionVec, 0.U)
   private val hasIRVec = Mux(hasIR, intrVec, 0.U)
 
-  private val highestPrioIRVec = Wire(Vec(64, Bool()))
-  highestPrioIRVec.zipWithIndex.foreach { case (irq, i) =>
-    if (InterruptNO.interruptDefaultPrio.contains(i)) {
-      val higherIRSeq = InterruptNO.getIRQHigherThan(i)
-      irq := (
-        higherIRSeq.nonEmpty.B && Cat(higherIRSeq.map(num => !hasIRVec(num))).andR ||
-        higherIRSeq.isEmpty.B
-      ) && hasIRVec(i)
-      dontTouch(irq)
-    } else
-      irq := false.B
+  private val interruptGroups: Seq[(Seq[Int], String)] = Seq(
+    InterruptNO.customHighestGroup    -> "customHighest",
+    InterruptNO.localHighGroup        -> "localHigh",
+    InterruptNO.customMiddleHighGroup -> "customMiddleHigh",
+    InterruptNO.interruptDefaultPrio  -> "privArch",
+    InterruptNO.customMiddleLowGroup  -> "customMiddleLow",
+    InterruptNO.localLowGroup         -> "localLow",
+    InterruptNO.customLowestGroup     -> "customLowest",
+  )
+
+  private val filteredIRQs: Seq[UInt] = interruptGroups.map {
+    case (irqGroup, name) => (getMaskFromIRQGroup(irqGroup) & hasIRVec).suggestName(s"filteredIRQs_$name")
+  }
+
+  private val hasIRQinGroup: Seq[Bool] = interruptGroups.map {
+    case (irqGroup, name) => dontTouch(Cat(filterIRQs(irqGroup, hasIRVec)).orR.suggestName(s"hasIRQinGroup_$name"))
   }
 
+  private val highestIRQinGroup: Seq[Vec[Bool]] = interruptGroups zip filteredIRQs map {
+    case ((irqGroup: Seq[Int], name), filteredIRQ: UInt) =>
+      produceHighIRInGroup(irqGroup, filteredIRQ).suggestName(s"highestIRQinGroup_$name")
+  }
+
+  private val highestPrioIRVec: Vec[Bool] = MuxCase(
+    0.U.asTypeOf(Vec(64, Bool())),
+    hasIRQinGroup zip highestIRQinGroup map{ case (hasIRQ: Bool, highestIRQ: Vec[Bool]) => hasIRQ -> highestIRQ }
+  )
+
   private val highestPrioEXVec = Wire(Vec(64, Bool()))
   highestPrioEXVec.zipWithIndex.foreach { case (excp, i) =>
     if (ExceptionNO.priorities.contains(i)) {
@@ -99,6 +114,35 @@ class TrapHandleModule extends Module {
   io.out.causeNO.Interrupt := hasIR
   io.out.causeNO.ExceptionCode := causeNO
   io.out.pcFromXtvec := pcFromXtvec
+
+  def filterIRQs(group: Seq[Int], originIRQ: UInt): Seq[Bool] = {
+    group.map(irqNum => originIRQ(irqNum))
+  }
+
+  def getIRQHigherThanInGroup(group: Seq[Int])(irq: Int): Seq[Int] = {
+    val idx = group.indexOf(irq, 0)
+    require(idx != -1, s"The irq($irq) does not exists in IntPriority Seq")
+    group.slice(0, idx)
+  }
+
+  def getMaskFromIRQGroup(group: Seq[Int]): UInt = {
+    group.map(irq => BigInt(1) << irq).reduce(_ | _).U
+  }
+
+  def produceHighIRInGroup(irqGroup: Seq[Int], filteredIRVec: UInt): Vec[Bool] = {
+    val irVec = Wire(Vec(64, Bool()))
+    irVec.zipWithIndex.foreach { case (irq, i) =>
+      if (irqGroup.contains(i)) {
+        val higherIRSeq: Seq[Int] = getIRQHigherThanInGroup(irqGroup)(i)
+        irq := (
+          higherIRSeq.nonEmpty.B && Cat(higherIRSeq.map(num => !filteredIRVec(num))).andR ||
+            higherIRSeq.isEmpty.B
+          ) && filteredIRVec(i)
+      } else
+        irq := false.B
+    }
+    irVec
+  }
 }
 
 class TrapHandleIO extends Bundle {
```
