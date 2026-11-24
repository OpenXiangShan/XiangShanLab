# Commit Log
- Issue: #6497
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6497
- Issue state: closed
- Tested RTL commit: 2e148b41d52307bb90d0e7812462dacd90bc8b33
- Related PR: #6522
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6522
- Changed files: 1
- Additions: 4
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 120c79b1e22..581476ff42b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -315,14 +315,15 @@ trait MachineLevel { self: NewCSR =>
     regOut.SGEIP := Cat(hgeip.asUInt & hgeie.asUInt).orR
 
     // bit 13 LCOFIP
-    when (fromSip.LCOFIP.valid || fromVSip.LCOFIP.valid || wen) {
+    val lcofiReqHold = lcofiReq || RegNext(lcofiReq, false.B)
+    when(lcofiReqHold) {
+      reg.LCOFIP := lcofiReqHold
+    }.elsewhen (fromSip.LCOFIP.valid || fromVSip.LCOFIP.valid || wen) {
       reg.LCOFIP := Mux1H(Seq(
         fromSip.LCOFIP.valid  -> fromSip.LCOFIP.bits,
         fromVSip.LCOFIP.valid -> fromVSip.LCOFIP.bits,
         wen -> wdata.LCOFIP,
       ))
-    }.elsewhen(lcofiReq) {
-      reg.LCOFIP := lcofiReq
     }.otherwise {
       reg.LCOFIP := reg.LCOFIP
     }
```
