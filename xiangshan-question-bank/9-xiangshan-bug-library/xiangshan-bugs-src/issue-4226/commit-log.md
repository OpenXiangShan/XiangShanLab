# Commit Log
- Issue: #4226
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4226
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4226
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4226
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 086fed9a0ff..c60cd47e2a4 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1451,7 +1451,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     io.dcache.s1_pc := s1_out.uop.pc
     io.dcache.s2_pc := s2_out.uop.pc
   }
-  io.dcache.s2_kill := s2_pmp.ld || s2_actually_uncache || s2_kill
+  io.dcache.s2_kill := s2_pmp.ld || s2_pmp.st || s2_actually_uncache || s2_kill
 
   val s1_ld_left_fire = s1_valid && !s1_kill && s2_ready
   val s2_ld_valid_dup = RegInit(0.U(6.W))
```
