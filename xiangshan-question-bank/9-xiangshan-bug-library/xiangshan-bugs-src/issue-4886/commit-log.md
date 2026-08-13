# Commit Log
- Issue: #4886
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4886
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4886
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4886
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/xiangshan/L2Top.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index 0c048ad7316..c21503db31e 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -306,6 +306,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
       io.l2_tlb_req.req.bits.cmd := l2.io.l2_tlb_req.req.bits.cmd
       io.l2_tlb_req.req.bits.size := l2.io.l2_tlb_req.req.bits.size
       io.l2_tlb_req.req.bits.kill := l2.io.l2_tlb_req.req.bits.kill
+      io.l2_tlb_req.req.bits.isPrefetch := l2.io.l2_tlb_req.req.bits.isPrefetch
       io.l2_tlb_req.req.bits.no_translate := l2.io.l2_tlb_req.req.bits.no_translate
       io.l2_tlb_req.req_kill := l2.io.l2_tlb_req.req_kill
       io.perfEvents := l2.io_perf
```
