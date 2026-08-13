# Commit Log
- Issue: #5538
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5538
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5538
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5538
- Changed files: 5
- Additions: 10
- Deletions: 16

## Files
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index 3e001d02f85..3ee4a23a15a 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -716,16 +716,11 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
     }
   }
   io.wbDataPathToCtrlBlock.writeback := wbDataPath.io.toCtrlBlock.writeback
-  io.wbDataPathToCtrlBlock.writeback.filter(_.bits.redirect.nonEmpty).map{ x =>
-    x.bits.redirect.get := 0.U.asTypeOf(x.bits.redirect.get)
-  } 
   // oldestRedirect
   if (params.isIntSchd) {
     val exuRedirects: Seq[ValidIO[Redirect]] = wbDataPath.io.toCtrlBlock.writeback.filter(_.bits.redirect.nonEmpty).map(x => {
       val out = Wire(Valid(new Redirect()))
-      out.valid := x.valid && x.bits.redirect.get.valid &&
-        (x.bits.redirect.get.bits.isMisPred || x.bits.redirect.get.bits.hasBackendFault) && 
-        !x.bits.robIdx.needFlush(Seq(io.flush, flushCopyReg2))
+      out.valid := x.valid && x.bits.redirect.get.valid && !x.bits.robIdx.needFlush(Seq(io.flush, flushCopyReg2))
       out.bits := x.bits.redirect.get.bits
       out.bits.debugIsCtrl := true.B
       out.bits.debugIsMemVio := false.B
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
index 9f2aeb0eb81..386409e4395 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
@@ -57,7 +57,7 @@ class BranchUnit(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg) {
   io.out.bits.res.data := 0.U
   io.out.bits.res.redirect.get match {
     case redirect =>
-      redirect.valid := io.out.valid
+      redirect.valid := io.out.valid && (isMisPred || redirect.bits.hasBackendFault)
       redirect.bits := 0.U.asTypeOf(io.out.bits.res.redirect.get.bits)
       redirect.bits.level := RedirectLevel.flushAfter
       redirect.bits.robIdx := io.in.bits.ctrl.robIdx
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 9f2f7768b95..461912e9e14 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -307,7 +307,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   redirect.backendIAF := csrMod.io.xretTargetPc.bits.raiseIAF
   redirect.backendIGPF := csrMod.io.xretTargetPc.bits.raiseIGPF
   // Only mispred will send redirect to frontend
-  redirect.isMisPred := true.B
+  redirect.isMisPred := false.B
 
   val rfWenReg = RegEnable(io.in.bits.ctrl.rfWen.get, io.in.fire)
   val pdestReg = RegEnable(io.in.bits.ctrl.pdest, io.in.fire)
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
index 5ef18f3666e..f4d5771ddc6 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
@@ -45,7 +45,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
 
   val redirect = io.out.bits.res.redirect.get.bits
   val redirectValid = io.out.bits.res.redirect.get.valid
-  redirectValid := io.in.valid && !jumpDataModule.io.isAuipc
+  redirectValid := io.in.valid && !jumpDataModule.io.isAuipc && (needRedirect || redirect.hasBackendFault)
   redirect := 0.U.asTypeOf(redirect)
   redirect.level := RedirectLevel.flushAfter
   redirect.robIdx := io.in.bits.ctrl.robIdx
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index cc66b21a88b..c96cfa4d3ee 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -146,7 +146,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val redirectWBs = io.writeback.filter(x => x.bits.redirect.nonEmpty).toSeq
   val vxsatWBs = io.exuWriteback.filter(x => x.bits.vxsat.nonEmpty).toSeq
   val branchWBs = io.exuWriteback.filter(_.bits.params.hasBrhFu).toSeq
-  val jmpWBs = io.exuWriteback.filter(_.bits.params.hasJmpFu).toSeq
+  val isBrhOrJmpWBs = io.exuWriteback.filter(x => (x.bits.params.hasBrhFu || x.bits.params.hasJmpFu)).toSeq
   val csrWBs = io.exuWriteback.filter(x => x.bits.params.hasCSR).toSeq
 
   if (backendParams.debugEn){
@@ -1645,13 +1645,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       port.pc := debug_microOp(port.robidx.value).pc
     }
   }
-
-  val brhMispred = PopCount(branchWBs.map(wb => wb.valid & wb.bits.redirect.get.valid))
-  val jmpMispred = PopCount(jmpWBs.map(wb => wb.valid && wb.bits.redirect.get.valid))
-  val brhJump    = PopCount((branchWBs ++ jmpWBs).map(wb => wb.valid))
-  val misPred = brhMispred +& jmpMispred
+ 
+  val misPred = io.redirect.valid && io.redirect.bits.isMisPred
+  val brhJump = PopCount(isBrhOrJmpWBs.map(wb => wb.valid))
 
   XSPerfAccumulate("br_mis_pred", misPred)
+  XSPerfAccumulate("total_flush", io.redirect.valid)
 
   val commitLoadVec = VecInit(commitLoadValid)
   val commitBranchVec = VecInit(commitBranchValid)
@@ -1675,7 +1674,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     ("rob_4_4_valid          ", numValidEntries > (RobSize * 3 / 4).U),
     ("BRANCH_JUMP            ", brhJump),
     ("BR_MIS_PRED            ", misPred),
-    ("TOTAL_FLUSH            ", io.flushOut.valid)
+    ("TOTAL_FLUSH            ", io.redirect.valid)
   )
   generatePerfEvent()
```
