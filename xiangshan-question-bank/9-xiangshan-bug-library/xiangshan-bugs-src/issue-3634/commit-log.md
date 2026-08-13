# Commit Log
- Issue: #3634
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3634
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3634
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3634
- Changed files: 3
- Additions: 24
- Deletions: 27

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `utility`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 2d96209bc3e..2d545116279 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -893,12 +893,6 @@ class NewCSR(implicit val p: Parameters) extends Module
     }
   }
 
-  /** Data that have been read before,and should be stored because output not fired */
-  val rdataReg = RegInit(UInt(64.W), 0.U)
-
-  when(valid && !asyncRead) {
-    rdataReg := rdata
-  }
 
   // Todo: check IMSIC EX_II and EX_VI
   private val imsicIllegal = fromAIA.rdata.valid && fromAIA.rdata.bits.illegal
@@ -913,20 +907,23 @@ class NewCSR(implicit val p: Parameters) extends Module
    * When in IDLE state, when input_valid is high, we set it.
    * When in waitIMSIC state, and the next state is IDLE, we set it.
    **/
-  io.out.valid := (state === s_idle) && valid && !(asyncRead && fromAIA.rdata.valid) ||
-                  (state === s_waitIMSIC) && fromAIA.rdata.valid ||
-                  (state === s_finish)
-  io.out.bits.EX_II := permitMod.io.out.EX_II || imsic_EX_II || noCSRIllegal
-  io.out.bits.EX_VI := permitMod.io.out.EX_VI || imsic_EX_VI
 
-  io.out.bits.flushPipe := flushPipe
+  /** Data that have been read before,and should be stored because output not fired */
+  io.out.valid := state === s_idle && valid && !asyncRead ||
+                  state === s_waitIMSIC && fromAIA.rdata.valid ||
+                  state === s_finish
+  io.out.bits.EX_II := DataHoldBypass(permitMod.io.out.EX_II || noCSRIllegal, false.B, io.in.fire) ||
+                       DataHoldBypass(imsic_EX_II, false.B, fromAIA.rdata.valid)
+  io.out.bits.EX_VI := DataHoldBypass(permitMod.io.out.EX_VI, false.B, io.in.fire) ||
+                       DataHoldBypass(imsic_EX_VI, false.B, fromAIA.rdata.valid)
+  io.out.bits.flushPipe := DataHoldBypass(flushPipe, false.B, io.in.fire)
 
   /** Prepare read data for output */
-  io.out.bits.rData := MuxCase(0.U, Seq(
-    ((state === s_idle) && valid) -> rdata,
-    (state === s_waitIMSIC && fromAIA.rdata.valid) -> fromAIA.rdata.bits.data,
-    (state === s_finish) -> rdataReg,
-  ))
+  io.out.bits.rData := DataHoldBypass(
+    Mux1H(Seq(
+      io.in.fire -> rdata,
+      fromAIA.rdata.valid -> fromAIA.rdata.bits.data
+    )), 0.U(64.W), io.in.fire || fromAIA.rdata.valid)
   io.out.bits.regOut := regOut
   io.out.bits.targetPc := DataHoldBypass(
     Mux(trapEntryDEvent.out.targetPc.valid,
@@ -943,8 +940,8 @@ class NewCSR(implicit val p: Parameters) extends Module
       )
     ),
   needTargetUpdate)
-  io.out.bits.targetPcUpdate := needTargetUpdate
-  io.out.bits.isPerfCnt := addrInPerfCnt
+  io.out.bits.targetPcUpdate := DataHoldBypass(needTargetUpdate, false.B, io.in.fire)
+  io.out.bits.isPerfCnt := DataHoldBypass(addrInPerfCnt, false.B, io.in.fire)
 
   io.status.privState := privState
   io.status.fpState.frm := fcsr.frm
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index c1c37da1880..756c8181d6a 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -209,11 +209,11 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
 
   private val exceptionVec = WireInit(0.U.asTypeOf(ExceptionVec())) // Todo:
 
-  exceptionVec(EX_BP    ) := isEbreak
-  exceptionVec(EX_MCALL ) := isEcall && privState.isModeM
-  exceptionVec(EX_HSCALL) := isEcall && privState.isModeHS
-  exceptionVec(EX_VSCALL) := isEcall && privState.isModeVS
-  exceptionVec(EX_UCALL ) := isEcall && privState.isModeHUorVU
+  exceptionVec(EX_BP    ) := DataHoldBypass(isEbreak, false.B, io.in.fire)
+  exceptionVec(EX_MCALL ) := DataHoldBypass(isEcall && privState.isModeM, false.B, io.in.fire)
+  exceptionVec(EX_HSCALL) := DataHoldBypass(isEcall && privState.isModeHS, false.B, io.in.fire)
+  exceptionVec(EX_VSCALL) := DataHoldBypass(isEcall && privState.isModeVS, false.B, io.in.fire)
+  exceptionVec(EX_UCALL ) := DataHoldBypass(isEcall && privState.isModeHUorVU, false.B, io.in.fire)
   exceptionVec(EX_II    ) := csrMod.io.out.bits.EX_II
   exceptionVec(EX_VI    ) := csrMod.io.out.bits.EX_VI
 
@@ -267,7 +267,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   /** initialize NewCSR's io_out_ready from wrapper's io */
   csrMod.io.out.ready := io.out.ready
 
-  io.out.bits.res.redirect.get.valid := isXRet
+  io.out.bits.res.redirect.get.valid := io.out.valid && DataHoldBypass(isXRet, false.B, io.in.fire)
   val redirect = io.out.bits.res.redirect.get.bits
   redirect := 0.U.asTypeOf(redirect)
   redirect.level := RedirectLevel.flushAfter
@@ -286,7 +286,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   connectNonPipedCtrlSingalForCSR
 
   // Todo: summerize all difftest skip condition
-  csrOut.isPerfCnt  := csrMod.io.out.bits.isPerfCnt && csrModOutValid && func =/= CSROpType.jmp
+  csrOut.isPerfCnt  := io.out.valid && csrMod.io.out.bits.isPerfCnt && DataHoldBypass(func =/= CSROpType.jmp, false.B, io.in.fire)
   csrOut.fpu.frm    := csrMod.io.status.fpState.frm.asUInt
   csrOut.vpu.vstart := csrMod.io.status.vecState.vstart.asUInt
   csrOut.vpu.vxrm   := csrMod.io.status.vecState.vxrm.asUInt
diff --git a/utility b/utility
index 342e0ad98bf..051d07961ce 160000
--- a/utility
+++ b/utility
@@ -1 +1 @@
-Subproject commit 342e0ad98bf24bf550f4d44099bd64875145d07c
+Subproject commit 051d07961ce1679891b2ea7f1ea2f19c0a00a3fd
```
