# Commit Log
- Issue: #4435
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4435
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4435
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4435
- Changed files: 1
- Additions: 5
- Deletions: 5

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
index ef087b1f58c..41368bfa4ce 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
@@ -213,7 +213,7 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
     is(UopSplitType.AMO_CAS_W) {
       csBundle(0).uopIdx := 0.U
       csBundle(0).fuOpType := Cat(1.U(3.W), LSUOpType.amocas_w)
-      csBundle(0).lsrc(0) := src1
+      csBundle(0).lsrc(0) := 0.U
       csBundle(0).lsrc(1) := src2
       csBundle(0).rfWen := false.B
       csBundle(0).waitForward := true.B
@@ -229,7 +229,7 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
     is(UopSplitType.AMO_CAS_D) {
       csBundle(0).uopIdx := 0.U
       csBundle(0).fuOpType := Cat(1.U(3.W), LSUOpType.amocas_d)
-      csBundle(0).lsrc(0) := src1
+      csBundle(0).lsrc(0) := 0.U
       csBundle(0).lsrc(1) := src2
       csBundle(0).rfWen := false.B
       csBundle(0).waitForward := true.B
@@ -245,7 +245,7 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
     is(UopSplitType.AMO_CAS_Q) {
       csBundle(0).uopIdx := 0.U
       csBundle(0).fuOpType := Cat(1.U(3.W), LSUOpType.amocas_q)
-      csBundle(0).lsrc(0) := src1
+      csBundle(0).lsrc(0) := 0.U
       csBundle(0).lsrc(1) := src2
       csBundle(0).rfWen := false.B
       csBundle(0).waitForward := true.B
@@ -260,7 +260,7 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
 
       csBundle(2).uopIdx := 2.U
       csBundle(2).fuOpType := Cat(3.U(3.W), LSUOpType.amocas_q)
-      csBundle(2).lsrc(0) := src1
+      csBundle(2).lsrc(0) := 0.U
       csBundle(2).lsrc(1) := Mux(src2 === 0.U, 0.U, src2 + 1.U)
       csBundle(2).rfWen := false.B
       csBundle(2).waitForward := false.B
@@ -268,7 +268,7 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
 
       csBundle(3).uopIdx := 3.U
       csBundle(3).fuOpType := Cat(2.U(3.W), LSUOpType.amocas_q)
-      csBundle(3).lsrc(0) := src1
+      csBundle(3).lsrc(0) := 0.U
       csBundle(3).lsrc(1) := Mux(dest === 0.U, 0.U, dest + 1.U)
       csBundle(3).ldest := Mux(dest === 0.U, 0.U, dest + 1.U)
       csBundle(3).waitForward := false.B
```
