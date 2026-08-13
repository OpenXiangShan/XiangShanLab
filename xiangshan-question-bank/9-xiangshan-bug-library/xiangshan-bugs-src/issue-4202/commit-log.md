# Commit Log
- Issue: #4202
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4202
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4202
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4202
- Changed files: 1
- Additions: 5
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index d24a0c032e2..38af7a99047 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -161,7 +161,11 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
     }
   }
 
-  tlbCounter := tlbCounter + PopCount(reqVec) - PopCount(respVec)
+  when (flush) {
+    tlbCounter := 0.U
+  } .otherwise {
+    tlbCounter := tlbCounter + PopCount(reqVec) - PopCount(respVec)
+  }
   XSError(!(tlbCounter >= 0.U && tlbCounter <= MissQueueSize.U), s"l2tlb full!")
 
   arb2.io.in(InArbPTWPort).valid := ptw.io.llptw.valid
```
