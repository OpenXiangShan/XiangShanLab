# Commit Log
- Issue: #5244
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5244
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5244
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5244
- Changed files: 1
- Additions: 5
- Deletions: 5

## Files
- `src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
index adde8713483..d088b0b74f7 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
@@ -118,10 +118,10 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
   private val t1_meta = Wire(new IttageMeta)
   t1_train.meta.ittage := t1_meta
 
-  // The pc register has been moved outside of predictor
-  // pc field of t1_train bundle and other t1_train data are not in the same stage
-  // so io.t1_train.bits.pc is used directly here
-  private val updatePc = io.train.bits.startVAddr
+  private val t1_trainFoldedPhr = RegEnable(io.trainFoldedPhr, io.train.fire)
+
+  private val updatePc        = t1_train.startVAddr
+  private val updateFoldedPhr = t1_trainFoldedPhr
 
   // To improve Clock Gating Efficiency
   private val t0_meta = io.train.bits.meta.ittage
@@ -402,7 +402,7 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
     tables(i).io.update.usefulCntValid := RegEnable(updateUsefulCntMask(i), false.B, updateMask(i))
     tables(i).io.update.usefulCnt      := RegEnable(updateUsefulCnt(i), updateMask(i))
     tables(i).io.update.pc             := RegEnable(updatePc, updateMask(i))
-    tables(i).io.update.foldedHist     := RegEnable(io.trainFoldedPhr, updateMask(i))
+    tables(i).io.update.foldedHist     := RegEnable(updateFoldedPhr, updateMask(i))
   }
 
   // Debug and perf info
```
