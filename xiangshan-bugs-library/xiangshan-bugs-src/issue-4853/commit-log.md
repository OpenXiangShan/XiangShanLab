# Commit Log
- Issue: #4853
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4853
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4853
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4853
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index c530b4897f4..aa9767de6b2 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -564,7 +564,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
   /**
    * flush sbuffer IO Assign
    */
-  io.flush_sbuffer.valid           := !sbufferEmpty && (state === s_flush_sbuffer_req)
+  io.flush_sbuffer.valid           := !sbufferEmpty && (state === s_flush_sbuffer_req || state === s_wait_flush_sbuffer_resp)
 
   /**
   * update curPtr
```
