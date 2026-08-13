# Commit Log
- Issue: #4825
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4825
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4825
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4825
- Changed files: 1
- Additions: 5
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 91ea1fa9929..c96a0482bf3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -392,10 +392,6 @@ class NewCSR(implicit val p: Parameters) extends Module
   intrMod.io.in.fromAIA.meip := fromAIA.meip
   intrMod.io.in.fromAIA.seip := fromAIA.seip
 
-  when(intrMod.io.out.nmi && intrMod.io.out.interruptVec.valid) {
-    nmip.NMI_31 := nmip.NMI_31 & !UIntToOH(intrMod.io.out.interruptVec.bits, 64)(NonMaskableIRNO.NMI_31)
-    nmip.NMI_43 := nmip.NMI_43 & !UIntToOH(intrMod.io.out.interruptVec.bits, 64)(NonMaskableIRNO.NMI_43)
-  }
   val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
   val debug = RegEnable(intrMod.io.out.debug, false.B, intrMod.io.out.interruptVec.valid)
   val nmi = RegEnable(intrMod.io.out.nmi, false.B, intrMod.io.out.interruptVec.valid)
@@ -403,6 +399,11 @@ class NewCSR(implicit val p: Parameters) extends Module
   val irToHS = RegEnable(intrMod.io.out.irToHS, false.B, intrMod.io.out.interruptVec.valid)
   val irToVS = RegEnable(intrMod.io.out.irToVS, false.B, intrMod.io.out.interruptVec.valid)
 
+  when(hasTrap && trapIsInterrupt && nmi) {
+    nmip.NMI_31 := nmip.NMI_31 & !UIntToOH(intrVec, 64)(NonMaskableIRNO.NMI_31)
+    nmip.NMI_43 := nmip.NMI_43 & !UIntToOH(intrVec, 64)(NonMaskableIRNO.NMI_43)
+  }
+
   val trapHandleMod = Module(new TrapHandleModule)
 
   trapHandleMod.io.in.trapInfo.valid := hasTrap
```
