# Commit Log
- Issue: #4174
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4174
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4174
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4174
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/Repeater.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/Repeater.scala b/src/main/scala/xiangshan/cache/mmu/Repeater.scala
index c2c37caddd4..ea14c0db9b6 100644
--- a/src/main/scala/xiangshan/cache/mmu/Repeater.scala
+++ b/src/main/scala/xiangshan/cache/mmu/Repeater.scala
@@ -545,9 +545,9 @@ class PTWFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameters)
   io.tlb.resp.bits.data.s2xlate := ptwResp.s2xlate
   io.tlb.resp.bits.data.s1 := ptwResp.s1
   io.tlb.resp.bits.data.s2 := ptwResp.s2
-  io.tlb.resp.bits.data.memidx := memidx(OHToUInt(ptwResp_OldMatchVec))
+  io.tlb.resp.bits.data.memidx := RegNext(PriorityMux(ptwResp_OldMatchVec, memidx))
   io.tlb.resp.bits.vector := resp_vector
-  io.tlb.resp.bits.data.getGpa := RegNext(getGpa(OHToUInt(ptwResp_OldMatchVec)))
+  io.tlb.resp.bits.data.getGpa := RegNext(PriorityMux(ptwResp_OldMatchVec, getGpa))
   io.tlb.resp.bits.getGpa := DontCare
 
   val issue_valid = v(issPtr) && !isEmptyIss && !inflight_full
```
