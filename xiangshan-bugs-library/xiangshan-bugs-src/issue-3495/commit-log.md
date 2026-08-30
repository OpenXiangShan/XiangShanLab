# Commit Log
- Issue: #3495
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3495
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3495
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3495
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 46b33b32db0..9e3e8ee243f 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -1005,7 +1005,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     val isVlx = decodedInst.fuOpType === VlduType.vloxe || decodedInst.fuOpType === VlduType.vluxe
     val isVle = decodedInst.fuOpType === VlduType.vle || decodedInst.fuOpType === VlduType.vleff || decodedInst.fuOpType === VlduType.vlse
     val isVlm = decodedInst.fuOpType === VlduType.vlm
-    val isWritePartVd = decodedInst.uopSplitType === UopSplitType.VEC_VRED || decodedInst.uopSplitType === UopSplitType.VEC_0XV
+    val isWritePartVd = decodedInst.uopSplitType === UopSplitType.VEC_VRED || decodedInst.uopSplitType === UopSplitType.VEC_0XV || decodedInst.uopSplitType === UopSplitType.VEC_VWW
     val isVma = vmaInsts.map(_ === inst.ALL).reduce(_ || _)
     val emulIsFrac = Cat(~decodedInst.vpu.vlmul(2), decodedInst.vpu.vlmul(1, 0)) +& decodedInst.vpu.veew < 4.U +& decodedInst.vpu.vsew
     decodedInst.vpu.isNarrow := isNarrow
```
