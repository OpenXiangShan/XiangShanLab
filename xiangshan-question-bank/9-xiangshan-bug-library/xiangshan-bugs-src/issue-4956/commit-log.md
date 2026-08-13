# Commit Log
- Issue: #4956
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4956
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4956
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4956
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index fa8ed9dd2ec..970688bd4dd 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -916,7 +916,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
     writebackOut.mask.get               := instMicroOp.mask
     writebackOut.data                   := data(deqPtr.value)
     writebackOut.vdIdx.get              := vdIdxInField
-    writebackOut.uop.vpu.vl             := Mux(instMicroOp.exceptionVl.valid, instMicroOp.exceptionVl.bits, instMicroOp.vl)
+    writebackOut.uop.vpu.vl             := instMicroOp.vl
     writebackOut.uop.vpu.vstart         := Mux(instMicroOp.uop.exceptionVec.asUInt.orR || TriggerAction.isDmode(instMicroOp.uop.trigger), instMicroOp.exceptionVstart, instMicroOp.vstart)
     writebackOut.uop.vpu.vmask          := maskUsed
     writebackOut.uop.vpu.vuopIdx        := uopq(deqPtr.value).uop.vpu.vuopIdx
```
