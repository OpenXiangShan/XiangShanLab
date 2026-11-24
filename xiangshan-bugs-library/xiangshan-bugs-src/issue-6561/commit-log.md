# Commit Log
- Issue: #6561
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6561
- Issue state: closed
- Tested RTL commit: b36a1a2a57
- Related PR: #6570
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6570
- Changed files: 1
- Additions: 12
- Deletions: 6

## Files
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index a4d21aa85d5..cef1a760286 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -367,18 +367,24 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     uops(i).wfflags := (compressMasksVec(i) & Cat(io.in.map(_.bits.wfflags).reverse)).orR
     uops(i).dirtyFs := (compressMasksVec(i) & Cat(io.in.map(_.bits.fpWen).reverse)).orR
     uops(i).dirtyVs := (
-      compressMasksVec(i) & Cat(io.in.map(in =>
-        // vector instructions' uopSplitType cannot be UopSplitType.SCA_SIM
-        in.bits.uopSplitType =/= UopSplitType.SCA_SIM &&
-        !UopSplitType.isAMOCAS(in.bits.uopSplitType) &&
+      compressMasksVec(i) & Cat(io.in.map { in =>
+        val isVStoreUop = FuType.isVStore(in.bits.fuType)
+        val isVStoreInst = in.bits.vlsInstr && CommitType.isStore(in.bits.commitType)
+        val isVStore = isVStoreUop || isVStoreInst
         // vfmv.f.s, vcpop.m, vfirst.m and vmv.x.s don't change vector state
-        !Seq(
+        val vectorStateReadOnlyInst = Seq(
           (FuType.vfalu, VfaluType.vfmv_f_s), // vfmv.f.s
           (FuType.vipu, VipuType.vcpop_m),    // vcpop.m
           (FuType.vipu, VipuType.vfirst_m),   // vfirst.m
           (FuType.vipu, VipuType.vmv_x_s)     // vmv.x.s
         ).map(x => FuTypeOrR(in.bits.fuType, x._1) && in.bits.fuOpType === x._2).reduce(_ || _)
-      ).reverse)
+        // vector instructions' uopSplitType cannot be UopSplitType.SCA_SIM
+        val vectorStateChange = in.bits.uopSplitType =/= UopSplitType.SCA_SIM &&
+          !UopSplitType.isAMOCAS(in.bits.uopSplitType) &&
+          !vectorStateReadOnlyInst
+        val vStoreNeedVstartReset = isVStore && in.bits.vpu.vstart =/= 0.U
+        vectorStateChange && (!isVStore || vStoreNeedVstartReset)
+      }.reverse)
     ).orR
     uops(i).debug_sim_trig.foreach(_ := (compressMasksVec(i) & Cat(io.in.map(_.bits.instr === XSDebugDecode.SIM_TRIG).reverse)).orR)
     // psrc0,psrc1,psrc2 don't require v0ReadPorts because their srcType can distinguish whether they are V0 or not
```
