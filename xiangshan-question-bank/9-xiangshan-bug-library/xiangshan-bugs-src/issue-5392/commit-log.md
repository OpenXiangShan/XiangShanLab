# Commit Log
- Issue: #5392
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5392
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5392
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5392
- Changed files: 1
- Additions: 7
- Deletions: 8

## Files
- `src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala b/src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala
index cd976e53e9e..bef3ada61ec 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala
@@ -137,13 +137,10 @@ class IttageTable(
 
   private val tableReadData = table.io.r.resp.data(0)
 
-  private val s1_reqReadHit = tableReadData.valid && tableReadData.tag === s1_tag
+  private val s1_reqReadHit = tableReadData.valid && (if (tagLen != 0) tableReadData.tag === s1_tag else true.B)
 
-  private val readWriteConflict    = io.update.valid && io.req.valid
-  private val s1_readWriteConflict = RegEnable(readWriteConflict, io.req.valid)
-
-  io.resp.valid    := (if (tagLen != 0) s1_reqReadHit && !s1_readWriteConflict else true.B) && s1_valid // && s1_mask(b)
-  io.resp.bits.cnt := tableReadData.confidenceCnt
+  io.resp.valid             := s1_reqReadHit && s1_valid // && s1_mask(b)
+  io.resp.bits.cnt          := tableReadData.confidenceCnt
   io.resp.bits.usefulCnt    := tableReadData.usefulCnt
   io.resp.bits.targetOffset := tableReadData.targetOffset
 
@@ -172,7 +169,7 @@ class IttageTable(
     Mux(io.update.valid, updateNoUsBitmask, Mux(usefulCanReset, updateUsBitmask, updateNoBitmask))
   )
 
-  /** 
+  /**
     Bypass write data from WriteBuffer to sram when read port is empty.
   */
   private val writeBuffer = Module(new WriteBuffer(
@@ -239,7 +236,9 @@ class IttageTable(
   XSPerfAccumulate("ittage_table_updates", io.update.valid)
   XSPerfAccumulate("ittage_table_hits", io.resp.valid)
   XSPerfAccumulate("ittage_us_tick_reset", io.update.resetUsefulCnt)
-  XSPerfAccumulate("ittage_table_read_write_conflict", readWriteConflict)
+  // read-write conflict: can provide data to sram write port, but sram read port is also requesting
+  XSPerfAccumulate("ittage_table_read_write_conflict", table.io.r.req.valid && writeBufferRPort.valid)
+  XSPerfAccumulate("ittage_table_update_drop", io.update.valid && !writeBufferWPort.ready)
 
   if (debug) {
     val u   = io.update
```
