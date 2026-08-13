# Commit Log
- Issue: #6275
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6275
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6275
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6275
- Changed files: 2
- Additions: 4
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala`
- `src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
index 1917b5397ab..8e043992173 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala
@@ -343,6 +343,7 @@ class LoadQueueReplay(implicit p: Parameters) extends XSModule
     // store address execute
     (0 until StorePipelineWidth).map(w => {
       storeAddrWakeupVec(i)(w) := io.storeAddrWakeup(w).valid &&
+        io.storeAddrWakeup(w).bits.sqIdx.withInPhysicalQueue(io.sqDeqPtr) &&
         blockSqIdx(i) === io.storeAddrWakeup(w).bits.sqIdx
     })
     storeAddrInSameCycleVec(i) := storeAddrWakeupVec(i).asUInt.orR // for better timing
diff --git a/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala b/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
index bd0a6d8e1f5..6aabb971b5b 100644
--- a/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
+++ b/src/main/scala/xiangshan/mem/prefetch/L1PrefetchComponent.scala
@@ -611,7 +611,9 @@ class MutiLevelPrefetchFilter(implicit p: Parameters) extends XSModule with HasL
   val s3_tlb_resp_valid = RegNext(s2_tlb_resp_valid)
   val s3_tlb_resp = RegEnable(s2_tlb_resp, s2_tlb_resp_valid)
   val s3_tlb_update_index = RegEnable(s2_tlb_update_index, s2_tlb_resp_valid)
-  val s3_tlb_evict = RegNext(s2_tlb_evict)
+  val s3_l1_tlb_evict = s1_l1_alloc && (s1_l1_index === s3_tlb_update_index)
+  val s3_l2_tlb_evict = s1_l2_alloc && ((s1_l2_index + MLP_L1_SIZE.U) === s3_tlb_update_index)
+  val s3_tlb_evict = RegNext(s2_tlb_evict) || s3_l1_tlb_evict || s3_l2_tlb_evict
   val s3_pmp_resp = io.pmp_resp
   val s3_update_valid = s3_tlb_resp_valid && !s3_tlb_evict && !s3_tlb_resp.miss
   val s3_drop = s3_update_valid && (
```
