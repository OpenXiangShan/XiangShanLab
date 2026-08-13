# Commit Log
- Issue: #5054
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5054
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5054
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5054
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index a377cb29c6b..8d8846fbf99 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -215,8 +215,8 @@ class Ifu(implicit p: Parameters) extends IfuModule
   s4_flush        := backendRedirect || (wbRedirect.valid && !s4_wbNotFlush)
   s3_flush        := backendRedirect || mmioRedirect.valid || wbRedirect.valid
   s2_flush        := s3_flush
-  s1_flush        := s2_flush || s1_flushFromBpu.reduce(_ || _)
-  s0_flush        := s1_flush || s0_flushFromBpu.reduce(_ || _)
+  s1_flush        := s2_flush || s1_flushFromBpu(0)
+  s0_flush        := s1_flush || s0_flushFromBpu(0)
 
   fromFtq.req.ready := s1_ready && io.fromICache.fetchReady
```
