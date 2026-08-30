# Commit Log
- Issue: #4173
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4173
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4173
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4173
- Changed files: 1
- Additions: 22
- Deletions: 17

## Files
- `src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
index a854aa1e140..efbc5a7f7ec 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
@@ -350,10 +350,10 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
     corrupt_r := true.B
   }.elsewhen(last_fire_r) {
     // Clear corrupt_r when response it sent to mainPipe
-    // This used to be io.fetch_resp.valid (last_fire_r && mshr_resp.valid) but when mshr is flushed by io.flush/fencei,
-    // mshr_resp.valid is false.B and corrupt_r will never be cleared, that's not correct
-    // so we remove mshr_resp.valid here, and the condition leftover is last_fire_r
-    // or, actually, io.fetch_resp.valid || (last_fire_r && !mshr_resp.valid)
+    // This used to be io.fetch_resp.valid (last_fire_r && mshr_valid) but when mshr is flushed by io.flush/fencei,
+    // mshr_valid is false.B and corrupt_r will never be cleared, that's not correct
+    // so we remove mshr_valid here, and the condition leftover is last_fire_r
+    // or, actually, io.fetch_resp.valid || (last_fire_r && !mshr_valid)
     corrupt_r := false.B
   }
 
@@ -371,32 +371,37 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
     */
   // get request information from MSHRs
   private val allMSHRs_resp = VecInit(allMSHRs.map(mshr => mshr.io.resp))
-  private val mshr_resp     = allMSHRs_resp(id_r)
+  // select MSHR response 1 cycle before sending response to mainPipe/prefetchPipe for better timing
+  private val mshr_resp =
+    RegEnable(allMSHRs_resp(io.mem_grant.bits.source).bits, 0.U.asTypeOf(allMSHRs_resp(0).bits), last_fire)
+  // we can latch mshr.io.resp.bits since they are set on req.fire or acquire.fire, and keeps unchanged during response
+  // however, we should not latch mshr.io.resp.valid, since io.flush/fencei may clear it at any time
+  private val mshr_valid = allMSHRs_resp(id_r).valid
 
   // get waymask from replacer when acquire fire
   io.victim.vSetIdx.valid := acquireArb.io.out.fire
   io.victim.vSetIdx.bits  := acquireArb.io.out.bits.vSetIdx
-  private val waymask = UIntToOH(mshr_resp.bits.way)
+  private val waymask = UIntToOH(mshr_resp.way)
   // NOTE: when flush/fencei, missUnit will still send response to mainPipe/prefetchPipe
   //       this is intentional to fix timing (io.flush -> mainPipe/prefetchPipe s2_miss -> s2_ready -> ftq ready)
   //       unnecessary response will be dropped by mainPipe/prefetchPipe/wayLookup since their sx_valid is set to false
-  private val fetch_resp_valid = mshr_resp.valid && last_fire_r
+  private val fetch_resp_valid = mshr_valid && last_fire_r
   // NOTE: but we should not write meta/dataArray when flush/fencei
   private val write_sram_valid = fetch_resp_valid && !corrupt_r && !io.flush && !io.fencei
 
   // write SRAM
   io.meta_write.bits.generate(
-    tag = getPhyTagFromBlk(mshr_resp.bits.blkPaddr),
-    idx = mshr_resp.bits.vSetIdx,
+    tag = getPhyTagFromBlk(mshr_resp.blkPaddr),
+    idx = mshr_resp.vSetIdx,
     waymask = waymask,
-    bankIdx = mshr_resp.bits.vSetIdx(0),
+    bankIdx = mshr_resp.vSetIdx(0),
     poison = false.B
   )
   io.data_write.bits.generate(
     data = respDataReg.asUInt,
-    idx = mshr_resp.bits.vSetIdx,
+    idx = mshr_resp.vSetIdx,
     waymask = waymask,
-    bankIdx = mshr_resp.bits.vSetIdx(0),
+    bankIdx = mshr_resp.vSetIdx(0),
     poison = false.B
   )
 
@@ -405,8 +410,8 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
 
   // response fetch
   io.fetch_resp.valid         := fetch_resp_valid
-  io.fetch_resp.bits.blkPaddr := mshr_resp.bits.blkPaddr
-  io.fetch_resp.bits.vSetIdx  := mshr_resp.bits.vSetIdx
+  io.fetch_resp.bits.blkPaddr := mshr_resp.blkPaddr
+  io.fetch_resp.bits.vSetIdx  := mshr_resp.vSetIdx
   io.fetch_resp.bits.waymask  := waymask
   io.fetch_resp.bits.data     := respDataReg.asUInt
   io.fetch_resp.bits.corrupt  := corrupt_r
@@ -437,8 +442,8 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
     ChiselDB.createTable("ICacheSRAMTable" + p(XSCoreParamsKey).HartId.toString, new ICacheSRAMDB)
 
   private val ICacheSRAMDBDumpData = Wire(new ICacheSRAMDB)
-  ICacheSRAMDBDumpData.blkPaddr := mshr_resp.bits.blkPaddr
-  ICacheSRAMDBDumpData.vSetIdx  := mshr_resp.bits.vSetIdx
+  ICacheSRAMDBDumpData.blkPaddr := mshr_resp.blkPaddr
+  ICacheSRAMDBDumpData.vSetIdx  := mshr_resp.vSetIdx
   ICacheSRAMDBDumpData.waymask  := OHToUInt(waymask)
   ICacheSRAMTable.log(
     data = ICacheSRAMDBDumpData,
@@ -457,7 +462,7 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
     difftest.coreid := io.hartId
     difftest.index  := 0.U
     difftest.valid  := write_sram_valid
-    difftest.addr   := Cat(mshr_resp.bits.blkPaddr, 0.U(blockOffBits.W))
+    difftest.addr   := Cat(mshr_resp.blkPaddr, 0.U(blockOffBits.W))
     difftest.data   := respDataReg.asTypeOf(difftest.data)
     difftest.idtfr  := DontCare
   }
```
