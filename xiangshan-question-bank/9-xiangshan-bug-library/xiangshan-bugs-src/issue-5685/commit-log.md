# Commit Log
- Issue: #5685
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5685
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5685
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5685
- Changed files: 4
- Additions: 60
- Deletions: 27

## Files
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`
- `src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala`
- `src/main/scala/xiangshan/backend/rename/freelist/StdFreeList.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index cebc56f58cb..a7ce6fbc39b 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -273,6 +273,7 @@ object Bundles {
     val numWB = UInt(log2Up(MaxUopSize).W) // rob need this
     // rename
     val psrc = Vec(numSrc, UInt(PhyRegIdxWidth.W))
+    val psrcIntForMove = UInt(PhyRegIdxWidth.W)
     val psrcVl = UInt(VlPhyRegIdxWidth.W)
     val pdest = UInt(PhyRegIdxWidth.W)
     val pdestVl = UInt(VlPhyRegIdxWidth.W)
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 51c2f914dd3..3c47d687c38 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -221,7 +221,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   vec_old_pdest := rat.io.vec_old_pdest
   v0_old_pdest := rat.io.v0_old_pdest
   vl_old_pdest := rat.io.vl_old_pdest
-  
+
   debug_int_rat.foreach(_ := rat.io.debug_int_rat.get)
   debug_fp_rat.foreach (_ := rat.io.debug_fp_rat.get)
   debug_vec_rat.foreach(_ := rat.io.debug_vec_rat.get)
@@ -525,6 +525,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     uops(i).psrc(2) := Mux1H(uops(i).srcType(2)(2, 1), Seq(fpReadPortsData(i)(2), vecReadPortsData(i)(2)))
     uops(i).psrc(3) := v0ReadPortsData(i)(0)
     uops(i).psrcVl := vlReadPortsData(i).head
+    uops(i).psrcIntForMove := intReadPortsData(i).head
 
     // int psrc2 should be bypassed from next instruction if it is fused
     if (i < RenameWidth - 1) {
@@ -554,6 +555,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     // dirty code
     if (i == 0) {
       io.out(i).bits.psrc(0) := Mux(io.out(i).bits.isLUI, 0.U, uops(i).psrc(0))
+      io.out(i).bits.psrcIntForMove := Mux(io.out(i).bits.isLUI, 0.U, uops(i).psrcIntForMove)
     }
     // Todo: move these shit in decode stage
     // dirty code for fence. The lsrc is passed by imm.
@@ -637,7 +639,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
       }
     }.elsewhen(needRobFlags(i)) {
       uops(i).traceBlockInPipe.ilastsize := Mux(lastIsRVC, Ilastsize.HalfWord, Ilastsize.Word)
-      
+
       // CSR systemop instruction excluding ebreak & ecall
       val csrAddr = Imm_Z().getCSRAddr(uops(i).imm(Imm_Z().len - 1, 0))
       val isXret = FuType.isCsr(uops(i).fuType) && CSROpType.isSystemOp(uops(i).fuOpType) && (csrAddr(11, 1).orR)
@@ -711,6 +713,9 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     io.out(i).bits.psrc(0) := Mux(io.out(i).bits.isLUI, 0.U, io.out.take(i).map(_.bits.pdest).zip(bypassCond(0)(i-1).asBools).foldLeft(uops(i).psrc(0)) {
       (z, next) => Mux(next._2, next._1, z)
     })
+    io.out(i).bits.psrcIntForMove := Mux(io.out(i).bits.isLUI, 0.U, io.out.take(i).map(_.bits.pdest).zip(bypassCond(0)(i-1).asBools).foldLeft(uops(i).psrcIntForMove) {
+      (z, next) => Mux(next._2, next._1, z)
+    })
     io.out(i).bits.psrc(1) := io.out.take(i).map(_.bits.pdest).zip(bypassCond(1)(i-1).asBools).foldLeft(uops(i).psrc(1)) {
       (z, next) => Mux(next._2, next._1, z)
     }
@@ -724,7 +729,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
       uops(i).psrcVl,
       (bypassCondVl(i-1).asBools zip io.out.take(i).map(_.bits.pdest)).reverse
     )
-    io.out(i).bits.pdest := Mux(isMove(i), io.out(i).bits.psrc(0), uops(i).pdest)
+    io.out(i).bits.pdest := Mux(isMove(i), io.out(i).bits.psrcIntForMove, uops(i).pdest)
 
     // Todo: better implementation for fields reuse
     // For fused-lui-load, load.src(0) is replaced by the imm.
diff --git a/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala b/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala
index 9438b365f1b..4394f04046b 100644
--- a/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala
+++ b/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala
@@ -36,10 +36,21 @@ class MEFreeList(size: Int, commitWidth: Int)(implicit p: Parameters) extends Ba
   val doRename = doWalkRename || doNormalRename
   val doCommit = io.commit.doCommit
 
+  val freeListVec = Wire(Vec(size, Vec(RenameWidth, UInt(PhyRegIdxWidth.W))))
+  for (i <- 0 until size) {
+    for (j <- 0 until RenameWidth) {
+      if (i + j > (size - 1)) {
+        freeListVec(i)(j) := freeList(i + j - size)
+      } else {
+        freeListVec(i)(j) := freeList(i + j)
+      }
+    }
+  }
+
   /**
     * Allocation: from freelist (same as StdFreelist)
     */
-  val phyRegCandidates = VecInit(headPtrOHVec.map(sel => Mux1H(sel, freeList)))
+  val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
   for (i <- 0 until RenameWidth) {
     // enqueue instr, is move elimination
     io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
@@ -64,10 +75,14 @@ class MEFreeList(size: Int, commitWidth: Int)(implicit p: Parameters) extends Ba
   /**
     * Deallocation: when refCounter becomes zero, the register can be released to freelist
     */
-  for (i <- 0 until commitWidth) {
-    when (io.freeReq(i)) {
-      val freePtr = tailPtr + PopCount(io.freeReq.take(i))
-      freeList(freePtr.value) := io.freePhyReg(i)
+  val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
+  for (i <- 0 until size) {
+    val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
+      w && freePtr(idx).value === i.U
+    })
+    val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
+    when(freeReqOH.asUInt.orR) {
+      freeList(i) := freePhyReg
     }
   }
 
diff --git a/src/main/scala/xiangshan/backend/rename/freelist/StdFreeList.scala b/src/main/scala/xiangshan/backend/rename/freelist/StdFreeList.scala
index a15977a2dc5..e7686e6a4be 100644
--- a/src/main/scala/xiangshan/backend/rename/freelist/StdFreeList.scala
+++ b/src/main/scala/xiangshan/backend/rename/freelist/StdFreeList.scala
@@ -34,29 +34,31 @@ class StdFreeList(
 )(implicit p: Parameters) extends BaseFreeList(freeListSize, commitWidth, realNumLogicRegs) with HasPerfEvents {
 
   val freeList = RegInit(VecInit(Seq.tabulate(freeListSize)( i => (i + numLogicRegs).U(PhyRegIdxWidth.W) )))
-  val lastTailPtr = RegInit(FreeListPtr(true, 0)) // tailPtr in the last cycle (need to add freeReqReg)
-  val tailPtr = Wire(new FreeListPtr) // this is the real tailPtr
-  val tailPtrOHReg = RegInit(0.U(freeListSize.W))
+  val tailPtr = RegInit(FreeListPtr(true, 0)) // tailPtr in the last cycle (need to add freeReqReg)
+  val tailPtrNext = Wire(new FreeListPtr) // this is the real tailPtr
 
   //
   // free committed instructions' `old_pdest` reg
   //
-  val freeReqReg = io.freeReq
+  val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
+  for (i <- 0 until freeListSize) {
+    val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
+      w && freePtr(idx).value === i.U
+    })
+    val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
+    when(freeReqOH.asUInt.orR) {
+      freeList(i) := freePhyReg
+    }
+  }
   for (i <- 0 until commitWidth) {
-    val offset = if (i == 0) 0.U else PopCount(freeReqReg.take(i))
-    val enqPtr = lastTailPtr + offset
-
     // Why RegNext (from RAT and Rename): for better timing
     // Why we can RegNext: these free registers won't be used in the next cycle,
     // since we set canAllocate only when the current free regs > RenameWidth.
-    when (freeReqReg(i)) {
-      freeList(enqPtr.value) := io.freePhyReg(i)
-    }
     XSDebug(io.freeReq(i), p"req#$i free physical reg: ${io.freePhyReg(i)}\n")
   }
 
-  tailPtr := lastTailPtr + PopCount(freeReqReg)
-  lastTailPtr := tailPtr
+  tailPtrNext := tailPtr + PopCount(io.freeReq)
+  tailPtr := tailPtrNext
 
   //
   // allocate new physical registers for instructions at rename stage
@@ -65,7 +67,18 @@ class StdFreeList(
   io.canAllocate := GatedValidRegNext(freeRegCnt >= RenameWidth.U) // use RegNext for better timing
   XSDebug(p"freeRegCnt: $freeRegCnt\n")
 
-  val phyRegCandidates = VecInit(headPtrOHVec.map(sel => Mux1H(sel, freeList)))
+  val freeListVec = Wire(Vec(freeListSize, Vec(RenameWidth, UInt(PhyRegIdxWidth.W))))
+  for (i <- 0 until freeListSize) {
+    for (j <- 0 until RenameWidth) {
+      if (i + j > (freeListSize - 1)) {
+        freeListVec(i)(j) := freeList(i + j - freeListSize)
+      } else {
+        freeListVec(i)(j) := freeList(i + j)
+      }
+    }
+  }
+
+  val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
 
   for(i <- 0 until RenameWidth) {
     io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
@@ -84,19 +97,18 @@ class StdFreeList(
   val numAllocate = Mux(io.walk, PopCount(io.walkReq), PopCount(io.allocateReq))
   val headPtrAllocate = Mux(lastCycleRedirect, redirectedHeadPtr, headPtr + numAllocate)
   val headPtrOHAllocate = Mux(lastCycleRedirect, redirectedHeadPtrOH, headPtrOHVec(numAllocate))
-  val headPtrNext = Mux(isAllocate, headPtrAllocate, headPtr)
-  freeRegCnt := Mux(isWalkAlloc && !lastCycleRedirect, distanceBetween(tailPtr, headPtr) - PopCount(io.walkReq),
-                Mux(isNormalAlloc,                     distanceBetween(tailPtr, headPtr) - PopCount(io.allocateReq),
-                                                       distanceBetween(tailPtr, headPtr)))
+  freeRegCnt := Mux(isWalkAlloc && !lastCycleRedirect, distanceBetween(tailPtrNext, headPtr) - PopCount(io.walkReq),
+                Mux(isNormalAlloc,                     distanceBetween(tailPtrNext, headPtr) - PopCount(io.allocateReq),
+                                                       distanceBetween(tailPtrNext, headPtr)))
 
   // priority: (1) exception and flushPipe; (2) walking; (3) mis-prediction; (4) normal dequeue
   val realDoAllocate = !io.redirect && isAllocate
   headPtr := Mux(realDoAllocate, headPtrAllocate, headPtr)
   headPtrOH := Mux(realDoAllocate, headPtrOHAllocate, headPtrOH)
 
-  XSDebug(p"head:$headPtr tail:$tailPtr\n")
+  XSDebug(p"head:$headPtr tail:$tailPtrNext\n")
 
-  XSError(!isFull(tailPtr, archHeadPtr), s"${regType}ArchFreeList should always be full\n")
+  XSError(!isFull(tailPtrNext, archHeadPtr), s"${regType}ArchFreeList should always be full\n")
 
   val enableFreeListCheck = false
   if (enableFreeListCheck) {
```
