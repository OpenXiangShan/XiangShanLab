# Commit Log
- Issue: #5962
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5962
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5962
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5962
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
index 3a713d2ae1e..86b35464c20 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICachePrefetchPipe.scala
@@ -181,7 +181,7 @@ class ICachePrefetchPipe(implicit p: Parameters) extends ICacheModule
 
   private val s1_itlbExceptionRaw =
     DataHoldBypass(ExceptionType.fromTlbResp(fromItlb.bits), ExceptionType.None, tlbValidPulse)
-  private val s1_itlbPbmt = DataHoldBypass(fromItlb.bits.pbmt.head, Pbmt.pma, tlbValidPulse)
+  private val s1_itlbPbmt = DataHoldBypass(fromItlb.bits.pbmt.head, 0.U(Pbmt.width.W), tlbValidPulse)
 
   // Guest page fault related: save tlb raw response, select later
   // NOTE: we don't use GPAddrBits or XLEN here, refer to ICacheMainPipe.scala L43-48 and PR#3795
```
