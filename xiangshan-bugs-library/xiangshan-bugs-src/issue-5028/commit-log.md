# Commit Log
- Issue: #5028
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5028
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5028
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5028
- Changed files: 1
- Additions: 8
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
index 8538ced2ef1..90065292589 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
@@ -50,7 +50,13 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers with B
   }
   io.resetDone := resetDone
 
-  private val takenCounter = Reg(Vec(NumBanks, Vec(NumSets, Vec(NumWays, new SaturateCounter(TakenCounterWidth)))))
+  private val takenCounter = RegInit(
+    VecInit.fill(NumBanks)(
+      VecInit.fill(NumSets)(
+        VecInit.fill(NumWays)(0.U.asTypeOf(new SaturateCounter(TakenCounterWidth)))
+      )
+    )
+  )
 
   // TODO: write ctr bypass to read
   // TODO: train after execution
@@ -164,7 +170,7 @@ class AheadBtb(implicit p: Parameters) extends BasePredictor with Helpers with B
   // used for check abtb output
   io.debug_startVaddr := s2_startPc
 
-  private val meta = Wire(new AheadBtbMeta)
+  private val meta = WireInit(0.U.asTypeOf(new AheadBtbMeta))
   meta.valid           := s2_valid
   meta.hitMask         := s2_hitMask
   meta.taken           := s2_taken
```
