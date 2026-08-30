# Commit Log
- Issue: #3301
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3301
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3301
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3301
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 83068ac9449..7533d5af918 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -525,6 +525,7 @@ class MidelegBundle extends InterruptBundle {
 
 class MieBundle extends InterruptEnableBundle {
   this.getNonLocal.foreach(_.setRW().withReset(0.U))
+  this.LCOFIE.setRW().withReset(0.U)
 }
 
 class MipBundle extends InterruptPendingBundle {
```
