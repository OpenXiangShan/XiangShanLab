# Commit Log
- Issue: #5698
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5698
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5698
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5698
- Changed files: 1
- Additions: 42
- Deletions: 26

## Files
- `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index fe3233214d0..4a259783a5a 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -244,12 +244,35 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
      *         -> lowHasOne: b11111100 => (result, multiHit): (b00000010, false.B)
      * */
     def findYoungest(in: UInt): (UInt, Bool) = {
-      val lowHasOne = VecInit(Seq.fill(in.getWidth)(false.B))
-      for (i <- 1 until in.getWidth) {
-        lowHasOne(i) := lowHasOne(i - 1) | in(i - 1)
+
+      def onehotWithMulti(x: UInt): (UInt, Bool) = {
+        val w = x.getWidth
+        if (w == 1) {
+          (x, false.B)
+        } else {
+          val highWidth = w / 2
+          val lowWidth  = w - highWidth
+          val high = x(w - 1, lowWidth)
+          val low  = x(lowWidth - 1, 0)
+
+          val (highOnehot, highMulti) = onehotWithMulti(high)
+          val (lowOnehot,  lowMulti)  = onehotWithMulti(low)
+
+          val highHasOne = high.orR
+          val lowHasOne  = low.orR
+
+          val multi = highMulti || lowMulti || (highHasOne && lowHasOne)
+
+          val onehot = Mux(lowHasOne,
+            Cat(0.U(highWidth.W), lowOnehot),
+            Cat(highOnehot, 0.U(lowWidth.W))
+          )
+
+          (onehot, multi)
+        }
       }
-      // (one-hot result, has multi match)
-      (in & (~lowHasOne.asUInt).asUInt, (in & lowHasOne.asUInt).orR)
+
+      onehotWithMulti(in)
     }
 
     /**
@@ -432,9 +455,6 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       val s1DataInvalid                  = !(s1SelectOH & dataValidVec.asUInt).orR
       val (_, s1MultiMatch)              = findYoungest(s1CanForwardLow | s1CanForwardHigh) // don't care
 
-      // select offset generate
-      val s1ByteSelectOffset   = s1LoadStart - s1SelectDataEntry.byteStart
-
       // MDP
       //                +-----------------------+
       //                | Search a SSID for the |
@@ -478,7 +498,6 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       s1AddrInvalidSqIdx.value := OHToUInt(s1AddrInvSelectOH)
       s1AddrInvalidSqIdx.flag  := Mux(s1AddrInvLowOH.orR, io.ctrlInfo.enqPtr.flag, io.ctrlInfo.deqPtr.flag)
 
-      val s2ByteSelectOffset = RegEnable(s1ByteSelectOffset, s1Valid)
       val s2SelectDataEntry  = RegEnable(s1SelectDataEntry, s1Valid)
       val s2SelectCtrlEntry  = RegEnable(s1SelectCtrlEntry, s1Valid)
       val s2DataInValid      = RegEnable(s1DataInvalid, s1Valid)
@@ -516,6 +535,7 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       //                                  ^^^^
       //                                  Load needs this byte (0x77)
 
+      val s2ByteSelectOffset    = s2LoadStart - s2SelectDataEntry.byteStart
       // !Paddrmatch
       val s2PaddrMatchVec       = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).map { case (dataEntry, ctrlEntry) =>
         val storeIsCboZero      = ctrlEntry.isCbo && isCboZero(dataEntry.cboType)
@@ -1497,7 +1517,7 @@ class NewStoreQueue(implicit p: Parameters) extends NewStoreQueueBase with HasPe
         enqLowBound(j).value <= i.U || i.U < enqUpBound(j).value,
         enqLowBound(j).value <= i.U && i.U < enqUpBound(j).value
       )
-      canEnqueue(j) && !enqCancel(j) && entryHitBound
+      canEnqueue(j) && !io.redirect.valid && entryHitBound
     }
 
     val entryCanEnq = entryCanEnqSeq.reduce(_ || _)
@@ -1753,10 +1773,8 @@ class NewStoreQueue(implicit p: Parameters) extends NewStoreQueueBase with HasPe
   /**
    * Update enqPtr
    * */
-  val enqCancelValid = canEnqueue.zip(io.enq.req).map{case (v , x) =>
-    v && x.bits.uop.robIdx.needFlush(io.redirect)
-  }
-  val enqCancelNum = enqCancelValid.zip(vStoreFlow).map{case (v, flow) =>
+
+  val enqCancelNum = canEnqueue.zip(vStoreFlow).map{case (v, flow) =>
     Mux(v, flow, 0.U)
   }
   val lastEnqCancel = RegEnable(enqCancelNum.reduce(_ + _), io.redirect.valid) // 1 cycle after redirect
@@ -1899,23 +1917,21 @@ class NewStoreQueue(implicit p: Parameters) extends NewStoreQueueBase with HasPe
     val ptr = cmtPtrExt(i).value
     val ctrlEntry = ctrlEntries(ptr)
     val dataEntry = dataEntries(ptr)
-    val ptrNoRotate = cmtPtrExt(i) >= deqPtrExt.head // prevent pointer rotation
     //TODO:
     /*
     * Currently three commit situation:
-    * [1]. normal Scalar Store Commit:      ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid --> move cmtPtr, set committed
-    * [2]. activate Vector Store Commit:    ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid && [vecMbcommit] --> move cmtPtr, set committed
-    * [3]. inactivate Vector Store Commit:  ptrNoRotate && allocated && noFlush && vecInactive  --> move cmtPtr, set committed
+    * [1]. normal Scalar Store Commit:      allocated && isRobHead && noException && allValid --> move cmtPtr, set committed
+    * [2]. activate Vector Store Commit:    allocated && isRobHead && noException && allValid && [vecMbcommit] --> move cmtPtr, set committed
+    * [3]. inactivate Vector Store Commit:  allocated && noFlush && vecInactive  --> move cmtPtr, set committed
     *
     * Future three commit situation:
-    * [1]. normal Scalar Store Commit:      ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid --> move cmtPtr, set committed
-    * [2]. activate Vector Store Commit:    ptrNoRotate && allocated && noFlush && isRobHead && noException && allValid --> move cmtPtr, set committed
-    * [3]. inactivate Vector Store Commit:  ptrNoRotate && allocated && noFlush && vecInactive  --> move cmtPtr, set committed
+    * [1]. normal Scalar Store Commit:      allocated && isRobHead && noException && allValid --> move cmtPtr, set committed
+    * [2]. activate Vector Store Commit:    allocated && isRobHead && noException && allValid --> move cmtPtr, set committed
+    * [3]. inactivate Vector Store Commit:  allocated && noFlush && vecInactive  --> move cmtPtr, set committed
     * */
-    when(ctrlEntries(ptr).allocated && !needCancel(ptr) &&
-      (isNotAfter(dataEntries(ptr).uop.robIdx, GatedRegNext(io.fromRob.pendingPtr)) &&
-      !ctrlEntries(ptr).hasException && !ctrlEntries(ptr).waitStoreS2 && (ctrlEntries(ptr).vecMbCommit || !ctrlEntries(ptr).isVec) &&
-      ctrlEntries(ptr).allValid || (ctrlEntries(ptr).vecMbCommit && !ctrlEntries(ptr).allValid || ctrlEntries(ptr).vecInactive))) { //TODO: vecMbCommit will be remove in the future
+    when(ctrlEntries(ptr).allocated && (isNotAfter(dataEntries(ptr).uop.robIdx, GatedRegNext(io.fromRob.pendingPtr)) &&
+      !ctrlEntries(ptr).hasException && !ctrlEntries(ptr).waitStoreS2 &&  (ctrlEntries(ptr).vecMbCommit || !ctrlEntries(ptr).isVec) &&
+      ctrlEntries(ptr).allValid || (ctrlEntries(ptr).vecMbCommit && !ctrlEntries(ptr).allValid || ctrlEntries(ptr).vecInactive) && !io.redirect.valid)) { //TODO: vecMbCommit will be remove in the future
       if(i == 0) {
         commitVec(i)               := true.B
       }
@@ -1924,7 +1940,7 @@ class NewStoreQueue(implicit p: Parameters) extends NewStoreQueueBase with HasPe
       }
     } // commitVec default is false.B
     //TODO: vecMbCommit will be remove in the future
-    ctrlEntries(ptr).committed   := Mux(ptrNoRotate, commitVec(i), ctrlEntries(ptr).committed)
+    ctrlEntries(ptr).committed     := ctrlEntries(ptr).committed || commitVec(i)
     XSError(!ctrlEntries(ptr).allocated && ctrlEntries(ptr).committed, "commit not allocated entry!\n")
     XSError(ctrlEntries(ptr).allocated && ctrlEntries(ptr).vecInactive && !ctrlEntries(ptr).isVec, "inactive entry must be vector!\n")
     XSError(ctrlEntries(ptr).allocated && ctrlEntries(ptr).vecMbCommit && !ctrlEntries(ptr).isVec, "vecMbCommit entry must be vector!\n")
```
