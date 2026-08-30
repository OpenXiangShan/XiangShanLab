# Commit Log
- Issue: #5939
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5939
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5939
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5939
- Changed files: 2
- Additions: 13
- Deletions: 3

## Files
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index ed08b2156b9..80b99e48043 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -464,6 +464,10 @@ class SramedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
       (if (i == 0) 0.B else (0 until i).map(rr_bank_conflict(_)(i)).reduce(_ || _))
   })
   XSPerfAccumulate("data_array_multi_read", perf_multi_read)
+  val rr_bank_conflict_count = PopCount((1 until LoadPipelineWidth).flatMap(y =>
+    (0 until y).map(x => rr_bank_conflict(x)(y))
+  ))
+  XSPerfAccumulate("data_array_multi_rr_bank_conflict", rr_bank_conflict_count >= 2.U)
   (1 until LoadPipelineWidth).foreach(y => (0 until y).foreach(x =>
     XSPerfAccumulate(s"data_array_rr_bank_conflict_${x}_${y}", rr_bank_conflict(x)(y))
   ))
@@ -778,6 +782,10 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
       (if (i == 0) 0.B else (0 until i).map(rr_bank_conflict(_)(i)).reduce(_ || _))
   })
   XSPerfAccumulate("data_array_multi_read", perf_multi_read)
+  val rr_bank_conflict_count = PopCount((1 until LoadPipelineWidth).flatMap(y =>
+    (0 until y).map(x => rr_bank_conflict(x)(y))
+  ))
+  XSPerfAccumulate("data_array_multi_rr_bank_conflict", rr_bank_conflict_count >= 2.U)
   (1 until LoadPipelineWidth).foreach(y => (0 until y).foreach(x =>
     XSPerfAccumulate(s"data_array_rr_bank_conflict_${x}_${y}", rr_bank_conflict(x)(y))
   ))
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index 243a9f5db5e..4eb6bb06df2 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -1641,12 +1641,14 @@ class LoadUnitS3(param: ExeUnitParams)(
 
   // source occupy others but fail perf counter
   val executeFail = lqWriteValid && lqWriteCause.asUInt.orR || pipeIn.valid && shouldFastReplay
+  // fastReplay's entrance maybe not one-hot.
+  val perfEntrance = Mux((pipeIn.bits.entrance & LoadEntrance.fastReplay.U).orR, LoadEntrance.fastReplay.U, pipeIn.bits.entrance)
   for (i <- 0 until LoadEntrance.num) {
     val highPrioNume = LoadEntrance.findNameById(i)
     for (j <- i + 1 until LoadEntrance.num) {
       val lowPrioNume = LoadEntrance.findNameById(j)
       println(s"[${param.name}] Add S0 Occupy PerfEvents of ${highPrioNume} oocupy ${lowPrioNume}, index: ${i} and ${j}")
-      val enable = pipeIn.bits.occupySource(j.U) && pipeIn.bits.entrance(i.U)
+      val enable = pipeIn.bits.occupySource(j.U) && perfEntrance(i.U)
       XSPerfAccumulate(s"${highPrioNume}_occupy_${lowPrioNume}", executeFail && enable)
     }
   }
@@ -1655,7 +1657,7 @@ class LoadUnitS3(param: ExeUnitParams)(
   for (i <- 0 until LoadEntrance.num) {
     val sourceNum = LoadEntrance.findNameById(i)
     println(s"[${param.name}] Add execute successed PerfEvents of ${sourceNum}, index: ${i}")
-    val enable = pipeIn.bits.entrance(i.U) && ldoutValid // success writeback
+    val enable = perfEntrance(i.U) && ldoutValid // success writeback
     XSPerfAccumulate(s"${sourceNum}_execute_success", enable)
   }
 
@@ -1663,7 +1665,7 @@ class LoadUnitS3(param: ExeUnitParams)(
   for (i <- 0 until LoadEntrance.num) {
     val sourceNum = LoadEntrance.findNameById(i)
     println(s"[${param.name}] Add execute failed PerfEvents of ${sourceNum}, index: ${i}")
-    val enable = pipeIn.bits.entrance(i.U) && executeFail
+    val enable = perfEntrance(i.U) && executeFail
     XSPerfAccumulate(s"${sourceNum}_execute_fail", enable)
   }
```
