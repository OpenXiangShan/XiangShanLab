# Commit Log
- Issue: #3705
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3705
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3705
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3705
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeStage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
index 08d4205c5f7..77195f60244 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeStage.scala
@@ -163,7 +163,7 @@ class DecodeStage(implicit p: Parameters) extends XSModule
     in.ready := !io.redirect && (
       simplePrefixVec(i) && (i.U +& complexNum) < readyCounter ||
       firstComplexOH(i) && (i.U +& complexNum) <= readyCounter && decoderComp.io.in.ready
-    ) && !(hasVectorInst && io.fromRob.isResumeVType)
+    ) && !io.fromRob.isResumeVType
   }
 
   val finalDecodedInst = Wire(Vec(DecodeWidth, new DecodedInst))
@@ -175,7 +175,7 @@ class DecodeStage(implicit p: Parameters) extends XSModule
   }
 
   io.out.zipWithIndex.foreach { case (inst, i) =>
-    inst.valid := finalDecodedInstValid(i) && !(hasVectorInst && io.fromRob.isResumeVType)
+    inst.valid := finalDecodedInstValid(i) && !io.fromRob.isResumeVType
     inst.bits := finalDecodedInst(i)
     inst.bits.lsrc(0) := Mux(finalDecodedInst(i).vpu.isReverse, finalDecodedInst(i).lsrc(1), finalDecodedInst(i).lsrc(0))
     inst.bits.lsrc(1) := Mux(finalDecodedInst(i).vpu.isReverse, finalDecodedInst(i).lsrc(0), finalDecodedInst(i).lsrc(1))
```
