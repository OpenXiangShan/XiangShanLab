# Commit Log
- Issue: #6167
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6167
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6167
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6167
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
index 7742fd627e1..e1d92379376 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
@@ -284,7 +284,7 @@ class Ittage(implicit p: Parameters) extends BasePredictor with HasIttageParamet
   ittageMeta.provider.bits     := s3_provider
   ittageMeta.altProvider.valid := s3_altProvided
   ittageMeta.altProvider.bits  := s3_altProvider
-  ittageMeta.altDiffers        := s3_providerTarget =/= s3_altProviderTarget
+  ittageMeta.altDiffers        := s3_altProvided && s3_providerTarget =/= s3_altProviderTarget
   ittageMeta.providerUsefulCnt := s3_providerUsefulCnt
   ittageMeta.providerCnt       := s3_providerCnt
   ittageMeta.altProviderCnt    := s3_altProviderCnt
```
