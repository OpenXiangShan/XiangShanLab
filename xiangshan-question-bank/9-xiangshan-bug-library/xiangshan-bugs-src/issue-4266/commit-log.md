# Commit Log
- Issue: #4266
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4266
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4266
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4266
- Changed files: 1
- Additions: 6
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 46fce8ef770..bf1f0792a65 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -386,7 +386,11 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val hasS2xlate = s2xlate =/= noS2xlate
     val onlyS1 = s2xlate === onlyStage1
     val onlyS2 = s2xlate === onlyStage2
-    val af = perm.af || (hasS2xlate && g_perm.af)
+    val allS2xlate = s2xlate === allStage
+    // noS2xlate || onlyS1 -> perm.af
+    // onlyS2 -> g_perm.af
+    // allS2xlate -> perm.af || g_perm.af
+    val af = (!onlyS2 && perm.af) || ((onlyS2 || allS2xlate) && g_perm.af)
 
     // Stage 1 perm check
     val pf = perm.pf
@@ -418,7 +422,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val ldGpf = (g_ldPermFail || gpf) && isLd
     val stGpf = (g_stPermFail || gpf) && isSt
     val instrGpf = (g_instrPermFail || gpf) && isInst
-    val s2_valid = portTranslateEnable(idx) && hasS2xlate && !onlyS1
+    val s2_valid = portTranslateEnable(idx) && (onlyS2 || allS2xlate)
 
     val fault_valid = s1_valid || s2_valid
```
