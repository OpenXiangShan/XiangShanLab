# Commit Log
- Issue: #4349
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4349
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4349
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4349
- Changed files: 1
- Additions: 38
- Deletions: 22

## Files
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 14830fa5db4..a3ad7c6d9ea 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -1827,25 +1827,41 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     (misalignBufExceptionOverwrite && misalignBufExceptionIsHyper ||
       (!vSegmentException && lsq.io.exceptionAddr.isHyper && !misalignBufExceptionOverwrite))
 
-  def GenExceptionVa(mode: UInt, isVirt: Bool, vaNeedExt: Bool,
-                     satp: TlbSatpBundle, vsatp: TlbSatpBundle, hgatp: TlbHgatpBundle,
-                     vaddr: UInt) = {
+  def GenExceptionVa(
+    mode: UInt, isVirt: Bool, vaNeedExt: Bool,
+    satp: TlbSatpBundle, vsatp: TlbSatpBundle, hgatp: TlbHgatpBundle,
+    vaddr: UInt
+  ) = {
     require(VAddrBits >= 50)
 
-    val Sv39 = satp.mode === 8.U
-    val Sv48 = satp.mode === 9.U
-    val Sv39x4 = vsatp.mode === 8.U || hgatp.mode === 8.U
-    val Sv48x4 = vsatp.mode === 9.U || hgatp.mode === 9.U
-    val vmEnable = !isVirt && (Sv39 || Sv48) && (mode < CSRConst.ModeM)
-    val s2xlateEnable = isVirt && (Sv39x4 || Sv48x4) && (mode < CSRConst.ModeM)
-
-    val s2xlate = MuxCase(noS2xlate, Seq(
-      !isVirt                                    -> noS2xlate,
-      (vsatp.mode =/= 0.U && hgatp.mode =/= 0.U) -> allStage,
-      (vsatp.mode === 0.U)                       -> onlyStage2,
-      (hgatp.mode === 0.U)                       -> onlyStage1
-    ))
-    val onlyS2 = s2xlate === onlyStage2
+    val satpNone = satp.mode === 0.U
+    val satpSv39 = satp.mode === 8.U
+    val satpSv48 = satp.mode === 9.U
+
+    val vsatpNone = vsatp.mode === 0.U
+    val vsatpSv39 = vsatp.mode === 8.U
+    val vsatpSv48 = vsatp.mode === 9.U
+
+    val hgatpNone = hgatp.mode === 0.U
+    val hgatpSv39x4 = hgatp.mode === 8.U
+    val hgatpSv48x4 = hgatp.mode === 9.U
+
+    // For !isVirt, mode check is necessary, as we don't want virtual memory in M-mode.
+    // For isVirt, mode check is unnecessary, as virt won't be 1 in M-mode.
+    // Also, isVirt includes Hyper Insts, which don't care mode either.
+
+    val useBareAddr = 
+      (isVirt && vsatpNone && hgatpNone) ||
+      (!isVirt && (mode === CSRConst.ModeM)) || 
+      (!isVirt && (mode =/= CSRConst.ModeM) && satpNone)
+    val useSv39Addr =
+      (isVirt && vsatpSv39) ||
+      (!isVirt && (mode =/= CSRConst.ModeM) && satpSv39)
+    val useSv48Addr =
+      (isVirt && vsatpSv48) ||
+      (!isVirt && (mode =/= CSRConst.ModeM) && satpSv48)
+    val useSv39x4Addr = isVirt && vsatpNone && hgatpSv39x4
+    val useSv48x4Addr = isVirt && vsatpNone && hgatpSv48x4
 
     val bareAddr   = ZeroExt(vaddr(PAddrBits - 1, 0), XLEN)
     val sv39Addr   = SignExt(vaddr.take(39), XLEN)
@@ -1856,11 +1872,11 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     val ExceptionVa = Wire(UInt(XLEN.W))
     when (vaNeedExt) {
       ExceptionVa := Mux1H(Seq(
-        (!(vmEnable || s2xlateEnable)) -> bareAddr,
-        (!onlyS2 && (Sv39 || Sv39x4))  -> sv39Addr,
-        (!onlyS2 && (Sv48 || Sv48x4))  -> sv48Addr,
-        ( onlyS2 && (Sv39 || Sv39x4))  -> sv39x4Addr,
-        ( onlyS2 && (Sv48 || Sv48x4))  -> sv48x4Addr,
+        (useBareAddr)   -> bareAddr,
+        (useSv39Addr)   -> sv39Addr,
+        (useSv48Addr)   -> sv48Addr,
+        (useSv39x4Addr) -> sv39x4Addr,
+        (useSv48x4Addr) -> sv48x4Addr,
       ))
     } .otherwise {
       ExceptionVa := vaddr
```
