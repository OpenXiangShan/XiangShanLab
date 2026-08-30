# Commit Log
- Issue: #4594
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4594
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4594
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4594
- Changed files: 1
- Additions: 8
- Deletions: 7

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
index a5ee3f3319b..d1ca171cec6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
@@ -204,11 +204,11 @@ class ISelectField(final val maxValue: Int, reserved: Seq[Range]) extends CSREnu
 }
 
 object VSISelectField extends ISelectField(
-  0x1FF,
+  0xFFF,
   reserved = Seq(
     Range.inclusive(0x000, 0x02F),
     Range.inclusive(0x040, 0x06F),
-    Range.inclusive(0x100, 0x1FF),
+    Range.inclusive(0x100, 0xFFF),
   ),
 )
 
@@ -221,15 +221,16 @@ object MISelectField extends ISelectField(
 )
 
 object SISelectField extends ISelectField(
-  maxValue = 0xFF,
+  maxValue = 0xFFF,
   reserved = Seq(
-    Range.inclusive(0x00, 0x2F),
-    Range.inclusive(0x40, 0x6F),
+    Range.inclusive(0x000, 0x02F),
+    Range.inclusive(0x040, 0x06F),
+    Range.inclusive(0x100, 0xFFF),
   ),
 )
 
 class VSISelectBundle extends CSRBundle {
-  val ALL = VSISelectField(log2Up(0x1FF), 0, null).withReset(0.U)
+  val ALL = VSISelectField(log2Up(0xFFF), 0, null).withReset(0.U)
 }
 
 class MISelectBundle extends CSRBundle {
@@ -237,7 +238,7 @@ class MISelectBundle extends CSRBundle {
 }
 
 class SISelectBundle extends CSRBundle {
-  val ALL = SISelectField(log2Up(0xFF), 0, null).withReset(0.U)
+  val ALL = SISelectField(log2Up(0xFFF), 0, null).withReset(0.U)
 }
 
 class TopIBundle extends CSRBundle {
```
