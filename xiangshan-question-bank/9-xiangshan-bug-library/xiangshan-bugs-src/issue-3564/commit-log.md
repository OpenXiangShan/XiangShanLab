# Commit Log
- Issue: #3564
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3564
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3564
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3564
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/ITTAGE.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ITTAGE.scala b/src/main/scala/xiangshan/frontend/ITTAGE.scala
index 6340178f70f..8852f629e7e 100644
--- a/src/main/scala/xiangshan/frontend/ITTAGE.scala
+++ b/src/main/scala/xiangshan/frontend/ITTAGE.scala
@@ -541,7 +541,8 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
       updateMask(provider)   := true.B
       updateUMask(provider)  := true.B
 
-      updateU(provider) := Mux(!updateMeta.altDiffers, updateMeta.providerU, !updateMisPred)
+      updateU(provider) := Mux(!updateMeta.altDiffers, updateMeta.providerU,
+                                updateMeta.providerTarget === updateRealTarget)
       updateCorrect(provider)  := updateMeta.providerTarget === updateRealTarget
       updateTarget(provider) := updateRealTarget
       updateOldTarget(provider) := updateMeta.providerTarget
```
