# Commit Log
- Issue: #4393
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4393
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4393
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4393
- Changed files: 2
- Additions: 18
- Deletions: 7

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
index d373ae601d6..db725166f17 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
@@ -7,7 +7,7 @@ object CSROoORead {
    * "Read only" CSRs that can be fully pipelined when read in CSRR instruction.
    * Only read by csr instructions.
    */
-  val inOrderCsrReadList = List(
+  val waitForwardInOrderCsrReadList = List(
     CSRs.fflags,
     CSRs.fcsr,
     CSRs.vxsat,
@@ -20,5 +20,13 @@ object CSROoORead {
     CSRs.mnstatus,
     CSRs.dcsr,
     CSRs.vtype,
+    CSRs.mireg,
+    CSRs.sireg,
+    CSRs.vsireg,
+  )
+  val blockBackwardInOrderCsrReadList = List(
+    CSRs.mireg,
+    CSRs.sireg,
+    CSRs.vsireg,
   )
 }
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 225641e907b..653e565c812 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -202,7 +202,8 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   private val inst         = Wire(Vec(RenameWidth, new XSInstBitFields))
   private val isCsr        = Wire(Vec(RenameWidth, Bool()))
   private val isCsrr       = Wire(Vec(RenameWidth, Bool()))
-  private val isRoCsrr     = Wire(Vec(RenameWidth, Bool()))
+  private val isWaitForwardCsrr = Wire(Vec(RenameWidth, Bool()))
+  private val isBlockBackwardCsrr = Wire(Vec(RenameWidth, Bool()))
   private val fuType       = uops.map(_.fuType)
   private val fuOpType     = uops.map(_.fuOpType)
   private val vtype        = uops.map(_.vpu.vtype)
@@ -282,18 +283,20 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     inst(i) := uops(i).instr.asTypeOf(new XSInstBitFields)
     isCsr(i) := inst(i).OPCODE5Bit === OPCODE5Bit.SYSTEM && inst(i).FUNCT3(1, 0) =/= 0.U
     isCsrr(i) := isCsr(i) && inst(i).FUNCT3 === BitPat("b?1?") && inst(i).RS1 === 0.U
-    isRoCsrr(i) := isCsrr(i) && LookupTreeDefault(
-      inst(i).CSRIDX, true.B, CSROoORead.inOrderCsrReadList.map(_.U -> false.B))
+    isWaitForwardCsrr(i) := isCsrr(i) && LookupTreeDefault(
+      inst(i).CSRIDX, true.B, CSROoORead.waitForwardInOrderCsrReadList.map(_.U -> false.B))
+    isBlockBackwardCsrr(i) := isCsrr(i) && LookupTreeDefault(
+      inst(i).CSRIDX, true.B, CSROoORead.blockBackwardInOrderCsrReadList.map(_.U -> false.B))
 
     /*
      * For most CSRs, CSRR instructions do not need to wait forward instructions.
      *
-     * For All CSRs, CSRR instructions do not need to block backward instructions.
+     * For most CSRs, CSRR instructions do not need to block backward instructions.
      *
      * Signal "isCsrr" contains not only "CSRR", but also other CSR instructions that do not require writing to CSR.
      */
-    uops(i).waitForward := io.in(i).bits.waitForward && !isRoCsrr(i)
-    uops(i).blockBackward := io.in(i).bits.blockBackward && !isCsrr(i)
+    uops(i).waitForward := io.in(i).bits.waitForward && !isWaitForwardCsrr(i)
+    uops(i).blockBackward := io.in(i).bits.blockBackward && !isBlockBackwardCsrr(i)
 
     // update cf according to ssit result
     uops(i).storeSetHit := io.ssit(i).valid
```
