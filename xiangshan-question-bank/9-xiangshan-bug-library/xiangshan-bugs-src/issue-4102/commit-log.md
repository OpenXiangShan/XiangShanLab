# Commit Log
- Issue: #4102
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4102
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4102
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4102
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 50edbab5a58..a7ea4e860cc 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1269,7 +1269,8 @@ class LoadUnit(implicit p: Parameters) extends XSModule
                            !s2_raw_nack &&
                            s2_nuke
 
-  val s2_fast_rep = !s2_mem_amb &&
+  val s2_fast_rep = !s2_in.isFastReplay &&
+                    !s2_mem_amb &&
                     !s2_tlb_miss &&
                     !s2_fwd_fail &&
                     (s2_dcache_fast_rep || s2_nuke_fast_rep) &&
```
