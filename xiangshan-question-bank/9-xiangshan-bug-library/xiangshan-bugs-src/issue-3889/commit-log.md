# Commit Log
- Issue: #3889
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3889
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3889
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3889
- Changed files: 7
- Additions: 31
- Deletions: 20

## Files
- `src/main/scala/xiangshan/frontend/FTB.scala`
- `src/main/scala/xiangshan/frontend/ITTAGE.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`
- `src/main/scala/xiangshan/frontend/SC.scala`
- `src/main/scala/xiangshan/frontend/Tage.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `utility`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/FTB.scala b/src/main/scala/xiangshan/frontend/FTB.scala
index 30f13a90ced..536590f6ab8 100644
--- a/src/main/scala/xiangshan/frontend/FTB.scala
+++ b/src/main/scala/xiangshan/frontend/FTB.scala
@@ -497,7 +497,8 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
       way = numWays,
       shouldReset = true,
       holdRead = false,
-      singlePort = true
+      singlePort = true,
+      withClockGate = true
     ))
     val ftb_r_entries = ftb.io.r.resp.data.map(_.entry)
 
diff --git a/src/main/scala/xiangshan/frontend/ITTAGE.scala b/src/main/scala/xiangshan/frontend/ITTAGE.scala
index 8010c97901d..df0fa69e73b 100644
--- a/src/main/scala/xiangshan/frontend/ITTAGE.scala
+++ b/src/main/scala/xiangshan/frontend/ITTAGE.scala
@@ -201,7 +201,8 @@ class ITTageTable(
     shouldReset = true,
     holdRead = true,
     singlePort = true,
-    useBitmask = true
+    useBitmask = true,
+    withClockGate = true
   ))
 
   table.io.r.req.valid       := io.req.fire
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index 088ca017255..be5e9d5ca4b 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -68,7 +68,7 @@ class FtqNRSRAM[T <: Data](gen: T, numRead: Int)(implicit p: Parameters) extends
   })
 
   for (i <- 0 until numRead) {
-    val sram = Module(new SRAMTemplate(gen, FtqSize))
+    val sram = Module(new SRAMTemplate(gen, FtqSize, withClockGate = true))
     sram.io.r.req.valid       := io.ren(i)
     sram.io.r.req.bits.setIdx := io.raddr(i)
     io.rdata(i)               := sram.io.r.resp.data(0)
diff --git a/src/main/scala/xiangshan/frontend/SC.scala b/src/main/scala/xiangshan/frontend/SC.scala
index 1ba2a109767..401225c0caa 100644
--- a/src/main/scala/xiangshan/frontend/SC.scala
+++ b/src/main/scala/xiangshan/frontend/SC.scala
@@ -68,7 +68,8 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
     shouldReset = true,
     holdRead = true,
     singlePort = false,
-    bypassWrite = true
+    bypassWrite = true,
+    withClockGate = true
   ))
 
   // def getIdx(hist: UInt, pc: UInt) = {
diff --git a/src/main/scala/xiangshan/frontend/Tage.scala b/src/main/scala/xiangshan/frontend/Tage.scala
index 4d6f8a146bb..cb489014a7b 100644
--- a/src/main/scala/xiangshan/frontend/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/Tage.scala
@@ -150,7 +150,8 @@ class TageBTable(implicit p: Parameters) extends XSModule with TBTParams {
       way = numBr,
       shouldReset = false,
       holdRead = true,
-      bypassWrite = true
+      bypassWrite = true,
+      withClockGate = true
     )
   )
 
@@ -333,7 +334,8 @@ class TageTable(
     shouldReset = true,
     extraReset = true,
     holdRead = true,
-    singlePort = true
+    singlePort = true,
+    withClockGate = true
   ))
   us.extra_reset.get := io.update.reset_u.reduce(_ || _) && io.update.mask.reduce(_ || _)
 
@@ -345,7 +347,8 @@ class TageTable(
       way = numBr,
       shouldReset = true,
       holdRead = true,
-      singlePort = true
+      singlePort = true,
+      withClockGate = true
     ))
   )
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index 603d53e384c..8a537c4ff55 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -246,7 +246,8 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray {
       way = nWays,
       shouldReset = true,
       holdRead = true,
-      singlePort = true
+      singlePort = true,
+      withClockGate = true
     ))
 
     // meta connection
@@ -385,7 +386,8 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray {
         width = ICacheDataSRAMWidth,
         shouldReset = true,
         holdRead = true,
-        singlePort = true
+        singlePort = true,
+        withClockGate = true
       ))
 
       // read
@@ -690,7 +692,8 @@ class ICachePartWayArray[T <: Data](gen: T, pWay: Int)(implicit p: Parameters) e
       way = pWay,
       shouldReset = true,
       holdRead = true,
-      singlePort = true
+      singlePort = true,
+      withClockGate = true
     ))
 
     sramBank.io.r.req.valid := io.read.req(bank).valid
@@ -716,14 +719,15 @@ class ICachePartWayArray[T <: Data](gen: T, pWay: Int)(implicit p: Parameters) e
 // Automatically partition the SRAM based on the width of the data and the desired width.
 // final SRAM width = width * way
 class SRAMTemplateWithFixedWidth[T <: Data](
-    gen:         T,
-    set:         Int,
-    width:       Int,
-    way:         Int = 1,
-    shouldReset: Boolean = false,
-    holdRead:    Boolean = false,
-    singlePort:  Boolean = false,
-    bypassWrite: Boolean = false
+    gen:           T,
+    set:           Int,
+    width:         Int,
+    way:           Int = 1,
+    shouldReset:   Boolean = false,
+    holdRead:      Boolean = false,
+    singlePort:    Boolean = false,
+    bypassWrite:   Boolean = false,
+    withClockGate: Boolean = false
 ) extends Module {
 
   val dataBits  = gen.getWidth
@@ -750,7 +754,8 @@ class SRAMTemplateWithFixedWidth[T <: Data](
       shouldReset = shouldReset,
       holdRead = holdRead,
       singlePort = singlePort,
-      bypassWrite = bypassWrite
+      bypassWrite = bypassWrite,
+      withClockGate = withClockGate
     ))
     // read req
     sramBank.io.r.req.valid       := io.r.req.valid
diff --git a/utility b/utility
index dca69bda5ca..880e574d9fd 160000
--- a/utility
+++ b/utility
@@ -1 +1 @@
-Subproject commit dca69bda5caf0e21d576bcb2caf977adc4805ca1
+Subproject commit 880e574d9fdc628d42651bc609962a0a30fe68bb
```
