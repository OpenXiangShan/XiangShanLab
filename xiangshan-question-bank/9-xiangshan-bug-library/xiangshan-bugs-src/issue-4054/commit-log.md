# Commit Log
- Issue: #4054
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4054
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4054
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4054
- Changed files: 2
- Additions: 8
- Deletions: 4

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index f694193ef94..020cc92d206 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit f694193ef941d81b836bb9c533149cb3a78560a4
+Subproject commit 020cc92d206954ceb181dc43faeed175a135ceb5
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 9ea3d640758..bf7c716d958 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -500,13 +500,17 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
     reg.MIE := false.B
   }
   // when DTE is zero, SDT field is read-only zero(write any, read zero, side effect of write 1 is block)
-  val writeSDT = Wire(Bool())
-  writeSDT := (w.wdataFields.SDT && w.wen) || (this.menvcfg.DTE.asBool && wAliasSstatus.wdataFields.SDT && wAliasSstatus.wen)
+  val writeSstatusSDT = Wire(Bool())
+  val writeMstatusSDT = Wire(Bool())
+  val writeSDT        = Wire(Bool())
+  writeMstatusSDT := w.wdataFields.SDT.asBool
+  writeSstatusSDT := Mux(this.menvcfg.DTE.asBool, wAliasSstatus.wdataFields.SDT.asBool, reg.SDT.asBool)
+  writeSDT        := Mux(w.wen, writeMstatusSDT, wAliasSstatus.wen && writeSstatusSDT)
   // menvcfg.DTE only control Smode dbltrp. Thus mstatus.sdt will not control by DTE.
   // as sstatus is alias of mstatus, when menvcfg.DTE close write,
   // sstatus.sdt cannot lead to shadow write of mstatus.sdt. \
   // As a result, we add wmask of sdt, when write source is from alias write.
-  when (!this.menvcfg.DTE.asBool && wAliasSstatus.wdataFields.SDT && wAliasSstatus.wen ) {
+  when (!this.menvcfg.DTE.asBool && wAliasSstatus.wen) {
     reg.SDT := reg.SDT
   }
   // SDT and SIE is the same as MDT and MIE
```
