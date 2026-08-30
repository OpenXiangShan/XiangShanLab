# Commit Log
- Issue: #4064
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4064
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4064
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4064
- Changed files: 1
- Additions: 6
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index f682af85b66..c9a66900866 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -109,7 +109,12 @@ class TrapHandleModule extends Module {
     traptoVS -> io.in.vstvec,
     trapToHS -> io.in.stvec
   ))
-  private val pcFromXtvec = Cat(xtvec.addr.asUInt + Mux(xtvec.mode === XtvecMode.Vectored && hasIR, interruptNO(5, 0), 0.U), 0.U(2.W))
+  private val adjustinterruptNO = Mux(
+    InterruptNO.getVS.map(_.U === interruptNO).reduce(_ || _) && vsHasIR,
+    interruptNO - 1.U, // map VSSIP, VSTIP, VSEIP to SSIP, STIP, SEIP
+    interruptNO,
+  )
+  private val pcFromXtvec = Cat(xtvec.addr.asUInt + Mux(xtvec.mode === XtvecMode.Vectored && hasIR, adjustinterruptNO(5, 0), 0.U), 0.U(2.W))
 
   io.out.entryPrivState := MuxCase(default = PrivState.ModeM, mapping = Seq(
     traptoVS -> PrivState.ModeVS,
```
