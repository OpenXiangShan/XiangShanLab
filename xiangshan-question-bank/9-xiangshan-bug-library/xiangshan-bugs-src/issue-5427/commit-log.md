# Commit Log
- Issue: #5427
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5427
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5427
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5427
- Changed files: 2
- Additions: 17
- Deletions: 4

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index c4e0350c0f6..4c8714da574 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit c4e0350c0f686cfa206d5b47d80cfd730f39675a
+Subproject commit 4c8714da5741d67dbe927bc85bd930d25b864acf
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index f28c0402d0d..88cdae219bf 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -353,9 +353,9 @@ trait MachineLevel { self: NewCSR =>
     }).setAddr(CSRs.mhpmcounter3 - 3 + num)
   )
 
-  val mvendorid = Module(new CSRModule("Mvendorid", new CSRBundle {
-    val ALL = RO(63, 0)
-  }))
+  // JEDEC JEP106 Manufacturer ID: 
+  //   Bank 17 (16 continuations), Offset 0x6F (111)
+  val mvendorid = Module(new CSRModule("Mvendorid", new MvendoridBundle))
     .setAddr(CSRs.mvendorid)
 
   // architecture id for XiangShan is 25
@@ -703,6 +703,19 @@ object MarchidField extends CSREnum with ROApply {
   val XSArchid = Value(25.U)
 }
 
+class MvendoridBundle extends CSRBundle {
+  val Bank   = MvidBankField(31, 7).withReset(MvidBankField.BANK)
+  val Offset = MvidOffsetField(6, 0).withReset(MvidOffsetField.OFFSET)
+}
+
+object MvidBankField extends CSREnum with ROApply {
+  val BANK = Value(16.U)
+}
+
+object MvidOffsetField extends CSREnum with ROApply {
+  val OFFSET = Value(0x6F.U)
+}
+
 class MieToHie extends Bundle {
   val VSSIE = ValidIO(RW(0))
   val VSTIE = ValidIO(RW(0))
```
