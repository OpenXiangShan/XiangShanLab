# Commit Log
- Issue: #3719
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3719
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3719
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3719
- Changed files: 1
- Additions: 21
- Deletions: 7

## Files
- `src/main/scala/xiangshan/frontend/icache/WayLookup.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
index 99b301d7003..3664e981526 100644
--- a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
@@ -101,6 +101,7 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
 
   private val gpf_entry = RegInit(0.U.asTypeOf(Valid(new WayLookupGPFEntry)))
   private val gpfPtr    = RegInit(WayLookupPtr(false.B, 0.U))
+  private val gpf_hit   = gpfPtr === readPtr && gpf_entry.valid
 
   when(io.flush) {
     // we don't need to reset gpfPtr, since the valid is actually gpf_entries.excp_tlb_gpf
@@ -143,12 +144,22 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
     * read
     ******************************************************************************
     */
+  // if the entry is empty, but there is a valid write, we can bypass it to read port (maybe timing critical)
+  private val can_bypass = empty && io.write.valid
   io.read.valid := !empty || io.write.valid
-  when (empty && io.write.valid) {  // bypass
+  when (can_bypass) {
     io.read.bits := io.write.bits
-  }.otherwise {
+  }.otherwise {  // can't bypass
     io.read.bits.entry := entries(readPtr.value)
-    io.read.bits.gpf   := Mux(readPtr === gpfPtr && gpf_entry.valid, gpf_entry.bits, 0.U.asTypeOf(new WayLookupGPFEntry))
+    when(gpf_hit) {  // ptr match && entry valid
+      io.read.bits.gpf := gpf_entry.bits
+      // also clear gpf_entry.valid when it's read, note this will be override by write (L175)
+      when (io.read.fire) {
+        gpf_entry.valid := false.B
+      }
+    }.otherwise {  // gpf not hit
+      io.read.bits.gpf := 0.U.asTypeOf(new WayLookupGPFEntry)
+    }
   }
 
   /**
@@ -156,12 +167,15 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
     * write
     ******************************************************************************
     */
-  io.write.ready := !full
+  // if there is a valid gpf to be read, we should stall the write
+  private val gpf_stall = gpf_entry.valid && !(io.read.fire && gpf_hit)
+  io.write.ready := !full && !gpf_stall
   when(io.write.fire) {
     entries(writePtr.value) := io.write.bits.entry
-    // save gpf iff no gpf is already saved
-    when(!gpf_entry.valid && io.write.bits.itlb_exception.map(_ === ExceptionType.gpf).reduce(_||_)) {
-      gpf_entry.valid := true.B
+    when(io.write.bits.itlb_exception.map(_ === ExceptionType.gpf).reduce(_||_)) {
+      // if gpf_entry is bypassed, we don't need to save it
+      // note this will override the read (L156)
+      gpf_entry.valid := !(can_bypass && io.read.fire)
       gpf_entry.bits  := io.write.bits.gpf
       gpfPtr := writePtr
     }
```
