# Commit Log
- Issue: #3769
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3769
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3769
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3769
- Changed files: 3
- Additions: 8
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 2b85ca027dc..14c33b423a3 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -862,7 +862,7 @@ object Bundles {
     val instr = UInt(32.W)
     val commitType = CommitType()
     val exceptionVec = ExceptionVec()
-    val isFetchBkpt = Bool()
+    val isPcBkpt = Bool()
     val isFetchMalAddr = Bool()
     val gpaddr = UInt(XLEN.W)
     val singleStep = Bool()
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 86507a04e6a..70b7fdba7c1 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -116,7 +116,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   // Todo: shrink the width of trap vector.
   // We use 64bits trap vector in CSR, and 24 bits exceptionVec in exception bundle.
   csrMod.io.fromRob.trap.bits.trapVec := csrIn.exception.bits.exceptionVec.asUInt
-  csrMod.io.fromRob.trap.bits.isFetchBkpt := csrIn.exception.bits.isFetchBkpt
+  csrMod.io.fromRob.trap.bits.isFetchBkpt := csrIn.exception.bits.isPcBkpt
   csrMod.io.fromRob.trap.bits.singleStep := csrIn.exception.bits.singleStep
   csrMod.io.fromRob.trap.bits.crossPageIPFFix := csrIn.exception.bits.crossPageIPFFix
   csrMod.io.fromRob.trap.bits.isInterrupt := csrIn.exception.bits.isInterrupt
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 7b7ed9c65fb..04df1879142 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -605,8 +605,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   io.exception.bits.instr := RegEnable(debug_deqUop.instr, exceptionHappen)
   io.exception.bits.commitType := RegEnable(deqDispatchData.commitType, exceptionHappen)
   io.exception.bits.exceptionVec := RegEnable(exceptionDataRead.bits.exceptionVec, exceptionHappen)
-  io.exception.bits.isFetchBkpt := RegEnable(
-    exceptionDataRead.bits.isEnqExcp && exceptionDataRead.bits.exceptionVec(ExceptionNO.EX_BP),
+  // fetch trigger fire or execute ebreak
+  io.exception.bits.isPcBkpt := RegEnable(
+    exceptionDataRead.bits.exceptionVec(ExceptionNO.EX_BP) && (
+      exceptionDataRead.bits.isEnqExcp ||
+      exceptionDataRead.bits.trigger === TriggerAction.None
+    ),
     exceptionHappen,
   )
   io.exception.bits.isFetchMalAddr := RegEnable(exceptionDataRead.bits.isFetchMalAddr && deqHasException, exceptionHappen)
```
