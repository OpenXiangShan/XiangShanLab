# Commit Log
- Issue: #3827
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3827
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3827
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3827
- Changed files: 1
- Additions: 5
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index b82f9bc4e19..ba3b430bbc1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -228,6 +228,8 @@ trait MachineLevel { self: NewCSR =>
         wen -> wdata.STIP,
         fromMvip.STIP.valid -> fromMvip.STIP.bits,
       ))
+    }.otherwise {
+      reg.STIP := reg.STIP
     }
 
     // bit 6 VSTIP
@@ -248,7 +250,7 @@ trait MachineLevel { self: NewCSR =>
     // mip.SEIP is implemented as the alias of mvip.SEIP when mvien=0
     // the read valid of SEIP is ORed by mvip.SEIP and the other source from the interrupt controller.
 
-    toMvip.SEIP.valid := wen && !this.mvien.SSIE
+    toMvip.SEIP.valid := wen && !this.mvien.SEIE
     toMvip.SEIP.bits := wdata.SEIP
     // When mvien.SEIE = 0, mip.SEIP is alias of mvip.SEIP.
     // Otherwise, mip.SEIP is read only 0
@@ -274,6 +276,8 @@ trait MachineLevel { self: NewCSR =>
       ))
     }.elsewhen(lcofiReq) {
       reg.LCOFIP := lcofiReq
+    }.otherwise {
+      reg.LCOFIP := reg.LCOFIP
     }
   }).setAddr(CSRs.mip)
```
