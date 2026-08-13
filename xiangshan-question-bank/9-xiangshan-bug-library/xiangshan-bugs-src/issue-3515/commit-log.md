# Commit Log
- Issue: #3515
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3515
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3515
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3515
- Changed files: 2
- Additions: 7
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 8b37d809460..5614f25ac2f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -263,13 +263,14 @@ trait MachineLevel { self: NewCSR =>
     regOut.SGEIP := Cat(hgeip.asUInt & hgeie.asUInt).orR
 
     // bit 13 LCOFIP
-    reg.LCOFIP := lcofiReq
     when (fromSip.LCOFIP.valid || fromVSip.LCOFIP.valid || wen) {
       reg.LCOFIP := Mux1H(Seq(
         fromSip.LCOFIP.valid  -> fromSip.LCOFIP.bits,
         fromVSip.LCOFIP.valid -> fromVSip.LCOFIP.bits,
         wen -> wdata.LCOFIP,
       ))
+    }.elsewhen(lcofiReq) {
+      reg.LCOFIP := lcofiReq
     }
   }).setAddr(CSRs.mip)
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
index bf725c887f0..0dbe0da7ce2 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/VirtualSupervisorLevel.scala
@@ -155,11 +155,11 @@ trait VirtualSupervisorLevel { self: NewCSR with SupervisorLevel with Hypervisor
 
     wdata.getLocal lazyZip
       (toMip.getLocal lazyZip toMvip.getLocal lazyZip toHvip.getLocal) lazyZip
-      (mideleg.getLocal lazyZip hideleg.getLocal lazyZip mvien.getLocal) foreach {
-        case (wLCIP, (toMipLCIP, toMvipLCIP, toHvipLCIP), (midelegBit, hidelegBit, mvienBit)) =>
-          toMipLCIP .valid := wen &&  midelegBit &&  hidelegBit
-          toMvipLCIP.valid := wen && !midelegBit &&  hidelegBit &&  mvienBit
-          toHvipLCIP.valid := wen &&                !hidelegBit &&  mvienBit
+      (mideleg.getLocal lazyZip hideleg.getLocal lazyZip mvien.getLocal lazyZip hvien.getLocal) foreach {
+        case (wLCIP, (toMipLCIP, toMvipLCIP, toHvipLCIP), (midelegBit, hidelegBit, mvienBit, hvienBit)) =>
+          toMipLCIP .valid := wen &&  hidelegBit &&  midelegBit
+          toMvipLCIP.valid := wen &&  hidelegBit && !midelegBit &&  mvienBit
+          toHvipLCIP.valid := wen && !hidelegBit &&                 hvienBit
           toMipLCIP .bits := wLCIP
           toMvipLCIP.bits := wLCIP
           toHvipLCIP.bits := wLCIP
```
