# Commit Log
- Issue: #3948
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3948
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3948
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3948
- Changed files: 3
- Additions: 10
- Deletions: 8

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala`
- `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index c410385a44a..4eae3651bc8 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -1039,19 +1039,22 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     val isNarrow = narrowInsts.map(_ === inst.ALL).reduce(_ || _)
     val isDstMask = maskDstInsts.map(_ === inst.ALL).reduce(_ || _)
     val isOpMask = maskOpInsts.map(_ === inst.ALL).reduce(_ || _)
-    val isVlx = decodedInst.fuOpType === VlduType.vloxe || decodedInst.fuOpType === VlduType.vluxe
-    val isVle = decodedInst.fuOpType === VlduType.vle || decodedInst.fuOpType === VlduType.vleff || decodedInst.fuOpType === VlduType.vlse
-    val isVlm = decodedInst.fuOpType === VlduType.vlm
+    val isVload = FuType.isVLoad(decodedInst.fuType)
+    val isVlx = isVload && (decodedInst.fuOpType === VlduType.vloxe || decodedInst.fuOpType === VlduType.vluxe)
+    val isVle = isVload && (decodedInst.fuOpType === VlduType.vle || decodedInst.fuOpType === VlduType.vleff || decodedInst.fuOpType === VlduType.vlse)
+    val isVlm = isVload && (decodedInst.fuOpType === VlduType.vlm)
+    val isFof = isVload && (decodedInst.fuOpType === VlduType.vleff)
     val isWritePartVd = decodedInst.uopSplitType === UopSplitType.VEC_VRED || decodedInst.uopSplitType === UopSplitType.VEC_0XV || decodedInst.uopSplitType === UopSplitType.VEC_VWW
     val isVma = vmaInsts.map(_ === inst.ALL).reduce(_ || _)
     val emulIsFrac = Cat(~decodedInst.vpu.vlmul(2), decodedInst.vpu.vlmul(1, 0)) +& decodedInst.vpu.veew < 4.U +& decodedInst.vpu.vsew
+    val vstartIsNotZero = io.enq.vstart =/= 0.U
     decodedInst.vpu.isNarrow := isNarrow
     decodedInst.vpu.isDstMask := isDstMask
     decodedInst.vpu.isOpMask := isOpMask
-    decodedInst.vpu.isDependOldVd := isVppu || isVecOPF || isVStore || (isDstMask && !isOpMask) || isNarrow || isVlx || isVma
+    decodedInst.vpu.isDependOldVd := isVppu || isVecOPF || isVStore || (isDstMask && !isOpMask) || isNarrow || isVlx || isVma || isFof || vstartIsNotZero
     decodedInst.vpu.isWritePartVd := isWritePartVd || isVlm || isVle && emulIsFrac
     decodedInst.vpu.vstart := io.enq.vstart
-    decodedInst.vpu.isVleff := decodedInst.fuOpType === VlduType.vleff && inst.NF === 0.U
+    decodedInst.vpu.isVleff := isFof && inst.NF === 0.U
   }
   decodedInst.vpu.specVill := io.enq.vtype.illegal
   decodedInst.vpu.specVma := io.enq.vtype.vma
diff --git a/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala b/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala
index 40f711aa2f5..5c22f0fb4fd 100644
--- a/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala
+++ b/src/main/scala/xiangshan/backend/issue/Dispatch2Iq.scala
@@ -232,12 +232,11 @@ abstract class Dispatch2IqImp(override val wrapper: Dispatch2Iq)(implicit p: Par
       val vlIsNonZero = !vlSrcIsZeroVec.get(i)
       val ignoreTail = vlIsVlmax && (vm =/= 0.U || vma) && !isWritePartVd
       val ignoreWhole = (vm =/= 0.U || vma) && vta
-      val isFof = VlduType.isFof(io.in(i).bits.fuOpType)
       for (j <- 0 until numRegSrcVf) {
         val ignoreOldVd = Wire(Bool())
         if (j == numRegSrcVf - 1) {
           // check whether can ignore the old vd dependency
-          ignoreOldVd := SrcState.isReady(vlSrcStateVec.get(i)) && !isFof && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
+          ignoreOldVd := SrcState.isReady(vlSrcStateVec.get(i)) && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
         } else {
           // check whether can ignore the src
           ignoreOldVd := false.B
diff --git a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
index 39c2212bd1c..5eb2fbd3e5e 100644
--- a/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
+++ b/src/main/scala/xiangshan/backend/issue/EntryBundles.scala
@@ -321,7 +321,7 @@ object EntryBundles extends HasCircularQueuePtrHelper {
           * 2. when vl = 0, we cannot set the srctype to imm because the vd keep the old value
           * 3. when vl = vlmax, we can set srctype to imm when vta is not set
           */
-        ignoreOldVd := !VlduType.isFof(entryReg.payload.fuOpType) && srcIsVec && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
+        ignoreOldVd := srcIsVec && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
       } else {
         ignoreOldVd := false.B
       }
```
