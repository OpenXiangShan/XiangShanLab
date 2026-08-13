# Commit Log
- Issue: #3430
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3430
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3430
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3430
- Changed files: 1
- Additions: 6
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
index 39281a5ac9d..a0e196f7afa 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
@@ -1145,9 +1145,10 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
           for (i <- 0 until vlmax) {
             csBundle(i).lsrc(0) := (if (i == 0) src1 else VECTOR_TMP_REG_LMUL.U)
             csBundle(i).lsrc(1) := (if (i % 4 == 0) src2 + (i/4).U else VECTOR_TMP_REG_LMUL.U)
-            csBundle(i).lsrc(2) := (if (i % 4 == 0) src2 + (i/4).U else if (i == vlmax - 1) dest else VECTOR_TMP_REG_LMUL.U)
+            csBundle(i).lsrc(2) := (if (i % 4 == 0) src2 + (i/4).U else if (i == vlmax - 1) dest else if (i % 4 == 1) Mux(isWiden, src2 + (i/4).U, VECTOR_TMP_REG_LMUL.U) else VECTOR_TMP_REG_LMUL.U)
             csBundle(i).ldest := (if (i == vlmax - 1) dest else VECTOR_TMP_REG_LMUL.U)
-            csBundle(i).vpu.fpu.isFoldTo1_4 := (if (i % 4 == 0) false.B else true.B)
+            csBundle(i).vpu.fpu.isFoldTo1_2 := isWiden && (if (i % 4 == 0) false.B else true.B)
+            csBundle(i).vpu.fpu.isFoldTo1_4 := !isWiden && (if (i % 4 == 0) false.B else true.B)
             csBundle(i).uopIdx := i.U
           }
         }
@@ -1156,9 +1157,10 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
           for (i <- 0 until vlmax) {
             csBundle(i).lsrc(0) := (if (i == 0) src1 else VECTOR_TMP_REG_LMUL.U)
             csBundle(i).lsrc(1) := (if (i % 8 == 0) src2 + (i/8).U else VECTOR_TMP_REG_LMUL.U)
-            csBundle(i).lsrc(2) := (if (i % 8 == 0) src2 + (i/8).U else if (i == vlmax - 1) dest else VECTOR_TMP_REG_LMUL.U)
+            csBundle(i).lsrc(2) := (if (i % 8 == 0) src2 + (i/8).U else if (i == vlmax - 1) dest else if (i % 8 == 1) Mux(isWiden, src2 + (i/8).U, VECTOR_TMP_REG_LMUL.U) else VECTOR_TMP_REG_LMUL.U)
             csBundle(i).ldest := (if (i == vlmax - 1) dest else VECTOR_TMP_REG_LMUL.U)
-            csBundle(i).vpu.fpu.isFoldTo1_8 := (if (i % 8 == 0) false.B else true.B)
+            csBundle(i).vpu.fpu.isFoldTo1_4 := isWiden && (if (i % 8 == 0) false.B else true.B)
+            csBundle(i).vpu.fpu.isFoldTo1_8 := !isWiden && (if (i % 8 == 0) false.B else true.B)
             csBundle(i).uopIdx := i.U
           }
         }
```
