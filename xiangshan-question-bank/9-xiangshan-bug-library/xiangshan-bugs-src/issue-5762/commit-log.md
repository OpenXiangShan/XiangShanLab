# Commit Log
- Issue: #5762
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5762
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5762
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5762
- Changed files: 4
- Additions: 9
- Deletions: 9

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 35cce35db0c..58df458b3cf 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -192,7 +192,7 @@ class CtrlBlockImp(
   private val exuRedirects: Seq[ValidIO[Redirect]] = io.fromWB.wbData.filter(_.bits.redirect.nonEmpty).map(x => {
     val hasCSR = x.bits.params.hasCSR
     val out = Wire(Valid(new Redirect()))
-    out.valid := x.valid && x.bits.redirect.get.valid && (x.bits.redirect.get.bits.cfiUpdate.isMisPred || x.bits.redirect.get.bits.cfiUpdate.hasBackendFault) && !x.bits.robIdx.needFlush(Seq(s1_s3_redirect, s2_s4_redirect))
+    out.valid := x.valid && x.bits.redirect.get.valid && !x.bits.robIdx.needFlush(Seq(s1_s3_redirect, s2_s4_redirect))
     out.bits := x.bits.redirect.get.bits
     out.bits.debugIsCtrl := true.B
     out.bits.debugIsMemVio := false.B
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
index 7614b57a97f..12702cc2312 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
@@ -50,7 +50,7 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   io.out.bits.res.data := 0.U
   io.out.bits.res.redirect.get match {
     case redirect =>
-      redirect.valid := io.out.valid && dataModule.io.mispredict
+      redirect.valid := io.out.valid && (dataModule.io.mispredict || redirect.bits.cfiUpdate.hasBackendFault)
       redirect.bits := 0.U.asTypeOf(io.out.bits.res.redirect.get.bits)
       redirect.bits.level := RedirectLevel.flushAfter
       redirect.bits.robIdx := io.in.bits.ctrl.robIdx
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 8940a2c9ac3..5dc3fae3108 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -317,7 +317,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   redirect.cfiUpdate.backendIAF := csrMod.io.out.bits.targetPc.raiseIAF
   redirect.cfiUpdate.backendIGPF := csrMod.io.out.bits.targetPc.raiseIGPF
   // Only mispred will send redirect to frontend
-  redirect.cfiUpdate.isMisPred := true.B
+  redirect.cfiUpdate.isMisPred := false.B
 
   connectNonPipedCtrlSingal
 
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index a42ffc5b4c5..02f9dcc7924 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -128,7 +128,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       val robHeadLqIdx = Valid(new LqPtr)
     }
     val debugRolling = new RobDebugRollingIO
-    val debugInstrAddrTransType = Input(new AddrTransType) 
+    val debugInstrAddrTransType = Input(new AddrTransType)
 
     // store event difftest information
     val storeDebugInfo = Vec(EnsbufferWidth, new Bundle {
@@ -1610,12 +1610,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     }
   }
 
-  val brhMispred = PopCount(branchWBs.map(wb => wb.valid & wb.bits.redirect.get.valid))
-  val jmpMispred = PopCount(jmpWBs.map(wb => wb.valid && wb.bits.redirect.get.valid))
-  val brhJump    = PopCount((branchWBs ++ jmpWBs).map(wb => wb.valid))
-  val misPred = brhMispred +& jmpMispred
+  val brhJump = PopCount((branchWBs ++ jmpWBs).map(wb => wb.valid))
+  val misPred = io.redirect.valid && io.redirect.bits.cfiUpdate.isMisPred
 
+  XSPerfAccumulate("brh_jump", brhJump)
   XSPerfAccumulate("br_mis_pred", misPred)
+  XSPerfAccumulate("total_flush", io.redirect.valid)
 
   val commitLoadVec = VecInit(commitLoadValid)
   val commitBranchVec = VecInit(commitBranchValid)
@@ -1639,7 +1639,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     ("rob_4_4_valid          ", numValidEntries > (RobSize * 3 / 4).U),
     ("BRANCH_JUMP            ", brhJump),
     ("BR_MIS_PRED            ", misPred),
-    ("TOTAL_FLUSH            ", io.flushOut.valid)
+    ("TOTAL_FLUSH            ", io.redirect.valid)
   )
   generatePerfEvent()
```
