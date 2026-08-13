# Commit Log
- Issue: #3558
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3558
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3558
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3558
- Changed files: 3
- Additions: 4
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index 497fbea6d04..3c7977579a2 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -338,8 +338,7 @@ class HEnvCfg extends EnvCfg {
   if (CSRConfig.EXT_SSTC) {
     this.STCE.setRW().withReset(1.U)
   }
-  // Always enable PBMT
-  this.PBMTE.setRO().withReset(1.U)
+  this.PBMTE.setRW().withReset(0.U)
 }
 
 trait HypervisorBundle { self: CSRModule[_] =>
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index b828695d06a..6b746a61b21 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -621,8 +621,7 @@ class MEnvCfg extends EnvCfg {
   if (CSRConfig.EXT_SSTC) {
     this.STCE.setRW().withReset(1.U)
   }
-  // Always enable PBMT
-  this.PBMTE.setRO().withReset(1.U)
+  this.PBMTE.setRW().withReset(0.U)
 }
 
 object MarchidField extends CSREnum with ROApply {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 9f5a7beaf0d..222b19af57e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1160,8 +1160,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     mstatus.regOut.MPV.asUInt,
     V.asUInt
   )
-  io.tlb.mPBMTE := menvcfg.regOut.PBMTE.asBool
-  io.tlb.hPBMTE := henvcfg.regOut.PBMTE.asBool
+  io.tlb.mPBMTE := RegNext(menvcfg.regOut.PBMTE.asBool)
+  io.tlb.hPBMTE := RegNext(henvcfg.regOut.PBMTE.asBool)
 
   io.toDecode.illegalInst.sfenceVMA  := isModeHS && mstatus.regOut.TVM  || isModeHU
   io.toDecode.virtualInst.sfenceVMA  := isModeVS && hstatus.regOut.VTVM || isModeVU
```
