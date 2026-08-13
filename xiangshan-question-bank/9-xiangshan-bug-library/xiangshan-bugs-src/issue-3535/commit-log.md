# Commit Log
- Issue: #3535
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3535
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3535
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3535
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/decode/VecExceptionGen.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/VecExceptionGen.scala b/src/main/scala/xiangshan/backend/decode/VecExceptionGen.scala
index d290aab201c..4cb9d1d99bb 100644
--- a/src/main/scala/xiangshan/backend/decode/VecExceptionGen.scala
+++ b/src/main/scala/xiangshan/backend/decode/VecExceptionGen.scala
@@ -185,7 +185,7 @@ class VecExceptionGen(implicit p: Parameters) extends XSModule{
   private val ZvfhminInst = Seq(VFWCVT_F_F_V, VFNCVT_F_F_W).map(_ === inst.ALL).reduce(_ || _)
   // funct3 of OPFVV is 001, funct3 of OPFVF is 101
   private val isFp = (inst.FUNCT3 === BitPat("b?01")) && (inst.OPCODE7Bit === OPCODE7Bit.VECTOR_ARITH)
-  private val fpEewIllegal = isFp && (((!doubleFpInst || !ZvfhminInst) && (SEW === 1.U)) || SEW === 0.U)
+  private val fpEewIllegal = isFp && (((!doubleFpInst && !ZvfhminInst) && (SEW === 1.U)) || SEW === 0.U)
 
   private val intExtEewIllegal = intExt2 && SEW === 0.U ||
                                  intExt4 && SEW <= 1.U ||
```
