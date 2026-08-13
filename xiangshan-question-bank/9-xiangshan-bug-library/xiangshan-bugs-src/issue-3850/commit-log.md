# Commit Log
- Issue: #3850
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3850
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3850
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3850
- Changed files: 1
- Additions: 0
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/BPU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/BPU.scala b/src/main/scala/xiangshan/frontend/BPU.scala
index cfea2612403..6d9236959ab 100644
--- a/src/main/scala/xiangshan/frontend/BPU.scala
+++ b/src/main/scala/xiangshan/frontend/BPU.scala
@@ -412,8 +412,6 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   }
   predictors.io.s1_fire := s1_fire_dup
 
-  s2_fire_dup := s2_valid_dup
-
   for (
     ((((s1_fire, s2_flush), s2_fire), s2_valid), s1_flush) <-
       s1_fire_dup zip s2_flush_dup zip s2_fire_dup zip s2_valid_dup zip s1_flush_dup
```
