# Commit Log
- Issue: #4485
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4485
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4485
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4485
- Changed files: 2
- Additions: 6
- Deletions: 8

## Files
- `src/main/scala/xiangshan/frontend/FTB.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/FTB.scala b/src/main/scala/xiangshan/frontend/FTB.scala
index 5abf6662447..c47033c4211 100644
--- a/src/main/scala/xiangshan/frontend/FTB.scala
+++ b/src/main/scala/xiangshan/frontend/FTB.scala
@@ -18,12 +18,11 @@ package xiangshan.frontend
 
 import chisel3._
 import chisel3.util._
-import coupledL2.utils.SplittedSRAM
 import org.chipsalliance.cde.config.Parameters
 import scala.{Tuple2 => &}
 import utility._
 import utility.mbist.MbistPipeline
-import utility.sram.SRAMTemplate
+import utility.sram.SplittedSRAMTemplate
 import xiangshan._
 
 trait FTBParams extends HasXSParameter with HasBPUConst {
@@ -494,7 +493,7 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
     })
 
     // Extract holdRead logic to fix bug that update read override predict read result
-    val ftb = Module(new SplittedSRAM(
+    val ftb = Module(new SplittedSRAMTemplate(
       new FTBEntryWithTag,
       set = numSets,
       way = numWays,
@@ -502,7 +501,7 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
       shouldReset = true,
       holdRead = false,
       singlePort = true,
-      clockGated = true,
+      withClockGate = true,
       hasMbist = hasMbist
     ))
     private val mbistPl = MbistPipeline.PlaceMbistPipeline(1, "MbistPipeFtb", hasMbist)
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index e61e7f32016..614b5b35aa3 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -26,12 +26,11 @@ package xiangshan.frontend
 
 import chisel3._
 import chisel3.util._
-import coupledL2.utils.SplittedSRAM
 import org.chipsalliance.cde.config.Parameters
 import utility._
 import utility.ChiselDB
 import utility.mbist.MbistPipeline
-import utility.sram.SRAMTemplate
+import utility.sram.SplittedSRAMTemplate
 import utils._
 import xiangshan._
 import xiangshan.backend.CtrlToFtqIO
@@ -78,13 +77,13 @@ class FtqNRSRAM[T <: Data](gen: T, numRead: Int)(implicit p: Parameters) extends
   })
 
   for (i <- 0 until numRead) {
-    val sram = Module(new SplittedSRAM(
+    val sram = Module(new SplittedSRAMTemplate(
       gen,
       set = FtqSize,
       way = 1,
       dataSplit = 2,
       singlePort = false,
-      clockGated = true,
+      withClockGate = true,
       hasMbist = hasMbist
     ))
     sram.io.r.req.valid       := io.ren(i)
```
