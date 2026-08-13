# Commit Log
- Issue: #3955
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3955
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3955
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3955
- Changed files: 2
- Additions: 9
- Deletions: 4

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index d226beaafc4..c1dc496545b 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit d226beaafc43388aa314659758185b2fa1e326f4
+Subproject commit c1dc496545b9d62bb10264cd4485cb6fe7c60798
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index ba3b430bbc1..53a547fa17d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -500,9 +500,13 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   }
   // when DTE is zero, SDT field is read-only zero(write any, read zero, side effect of write 1 is block)
   val writeSDT = Wire(Bool())
-  writeSDT := Mux(this.menvcfg.DTE.asBool, (w.wdataFields.SDT && w.wen) || (wAliasSstatus.wdataFields.SDT && wAliasSstatus.wen), 0.U)
-  when (!this.menvcfg.DTE) {
-    regOut.SDT := false.B
+  writeSDT := (w.wdataFields.SDT && w.wen) || (this.menvcfg.DTE.asBool && wAliasSstatus.wdataFields.SDT && wAliasSstatus.wen)
+  // menvcfg.DTE only control Smode dbltrp. Thus mstatus.sdt will not control by DTE.
+  // as sstatus is alias of mstatus, when menvcfg.DTE close write,
+  // sstatus.sdt cannot lead to shadow write of mstatus.sdt. \
+  // As a result, we add wmask of sdt, when write source is from alias write.
+  when (!this.menvcfg.DTE.asBool && wAliasSstatus.wdataFields.SDT && wAliasSstatus.wen ) {
+    reg.SDT := reg.SDT
   }
   // SDT and SIE is the same as MDT and MIE
   when (writeSDT) {
@@ -511,6 +515,7 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
   // read connection
   mstatus :|= regOut
   sstatus := mstatus
+  sstatus.SDT := regOut.SDT && menvcfg.DTE
   rdata := mstatus.asUInt
   sstatusRdata := sstatus.asUInt
 }
```
