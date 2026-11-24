# Commit Log
- Issue: #6528
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6528
- Issue state: closed
- Tested RTL commit: e43c569f8
- Related PR: #6532
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6532
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/FMA.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/FMA.scala b/src/main/scala/xiangshan/backend/fu/wrapper/FMA.scala
index 62ef331cacb..eaf158b262b 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/FMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/FMA.scala
@@ -26,7 +26,7 @@ class FMA(cfg: FuConfig)(implicit p: Parameters) extends FpPipedFuncUnit(cfg) {
                               fp_fmt === VSew.e16 && !src0.head(48).andR
   val fp_bIsFpCanonicalNAN  = fp_fmt === VSew.e32 && !src1.head(32).andR ||
                               fp_fmt === VSew.e16 && !src1.head(48).andR
-  val fp_cIsFpCanonicalNAN  = !FMacOpcode.useMUL(fuOpType) && (fp_fmt === VSew.e32 && !src2.head(32).andR ||
+  val fp_cIsFpCanonicalNAN  = !FMacOpcode.isFmul(fuOpType) && (fp_fmt === VSew.e32 && !src2.head(32).andR ||
                               fp_fmt === VSew.e16 && !src2.head(48).andR)
 
   fma.io.fire         := io.in.valid
```
