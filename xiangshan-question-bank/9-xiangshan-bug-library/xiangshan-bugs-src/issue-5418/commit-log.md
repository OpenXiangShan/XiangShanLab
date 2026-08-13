# Commit Log
- Issue: #5418
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5418
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5418
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5418
- Changed files: 1
- Additions: 36
- Deletions: 25

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
index 60cd677138c..0205b78c465 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbInternalBank.scala
@@ -82,10 +82,10 @@ class MainBtbInternalBank(
   val io: MainBtbInternalBankIO = IO(new MainBtbInternalBankIO)
 
   // alias
-  private val r     = io.read
-  private val we    = io.writeEntry
-  private val wc    = io.writeCounter
-  private val flush = io.flush
+  private val read         = io.read
+  private val writeEntry   = io.writeEntry
+  private val writeCounter = io.writeCounter
+  private val flush        = io.flush
 
   private val entrySrams = Seq.tabulate(NumWay) { wayIdx =>
     Module(
@@ -141,12 +141,12 @@ class MainBtbInternalBank(
   /* *** sram -> io *** */
   // handle entry & counter together
   (entrySrams :+ counterSram).foreach { sram =>
-    sram.io.r.req.valid       := r.req.valid
-    sram.io.r.req.bits.setIdx := r.req.bits.setIdx
+    sram.io.r.req.valid       := read.req.valid
+    sram.io.r.req.bits.setIdx := read.req.bits.setIdx
   }
   // each entry sram template has 1 way, so here we only read data.head
-  r.resp.entries  := VecInit(entrySrams.map(_.io.r.resp.data.head))
-  r.resp.counters := counterSram.io.r.resp.data
+  read.resp.entries  := VecInit(entrySrams.map(_.io.r.resp.data.head))
+  read.resp.counters := counterSram.io.r.resp.data
 
   /* *** writeBuffer -> sram *** */
   // entry
@@ -165,32 +165,43 @@ class MainBtbInternalBank(
 
   /* *** io -> writeBuffer *** */
   // entry
-  private val conflict = we.req.valid && we.req.bits.setIdx === flush.req.bits.setIdx && we.req.bits.entry.tag === 0.U
+  private val conflict =
+    writeEntry.req.valid &&
+      writeEntry.req.bits.setIdx === flush.req.bits.setIdx &&
+      writeEntry.req.bits.entry.tag === 0.U
+
   entryWriteBuffer.io.write.zipWithIndex.foreach { case (bufWrite, i) =>
-    val writeValid = we.req.valid && we.req.bits.wayMask(i)
+    val writeValid = writeEntry.req.valid && writeEntry.req.bits.wayMask(i)
     val flushValid = flush.req.valid && flush.req.bits.wayMask(i) && !conflict
-    bufWrite.valid := writeValid || flushValid
-    bufWrite.bits.setIdx := Mux(
-      writeValid,
-      we.req.bits.setIdx,
-      flush.req.bits.setIdx
+    val valid      = writeValid || flushValid
+    bufWrite.valid := RegNext(valid, false.B)
+    bufWrite.bits.setIdx := RegEnable(
+      Mux(
+        writeValid,
+        writeEntry.req.bits.setIdx,
+        flush.req.bits.setIdx
+      ),
+      valid
     )
-    bufWrite.bits.entry := Mux(
-      writeValid,
-      we.req.bits.entry,
-      0.U.asTypeOf(new MainBtbEntry)
+    bufWrite.bits.entry := RegEnable(
+      Mux(
+        writeValid,
+        writeEntry.req.bits.entry,
+        0.U.asTypeOf(new MainBtbEntry)
+      ),
+      valid
     )
   }
   // counter, dont care flush (`hit` is controlled by entry)
-  counterWriteBuffer.io.enq.valid         := wc.req.valid
-  counterWriteBuffer.io.enq.bits.setIdx   := wc.req.bits.setIdx
-  counterWriteBuffer.io.enq.bits.wayMask  := wc.req.bits.wayMask
-  counterWriteBuffer.io.enq.bits.counters := wc.req.bits.counters
+  counterWriteBuffer.io.enq.valid         := writeCounter.req.valid
+  counterWriteBuffer.io.enq.bits.setIdx   := writeCounter.req.bits.setIdx
+  counterWriteBuffer.io.enq.bits.wayMask  := writeCounter.req.bits.wayMask
+  counterWriteBuffer.io.enq.bits.counters := writeCounter.req.bits.counters
 
   XSPerfAccumulate(
     "multihit_write_conflict",
-    we.req.valid && flush.req.valid && we.req.bits.setIdx === flush.req.bits.setIdx &&
-      (we.req.bits.wayMask & flush.req.bits.wayMask).orR
+    writeEntry.req.valid && flush.req.valid && writeEntry.req.bits.setIdx === flush.req.bits.setIdx &&
+      (writeEntry.req.bits.wayMask & flush.req.bits.wayMask).orR
   )
 
   XSPerfAccumulate(
```
