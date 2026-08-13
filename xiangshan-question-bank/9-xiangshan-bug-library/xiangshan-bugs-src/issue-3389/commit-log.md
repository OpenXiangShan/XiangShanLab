# Commit Log
- Issue: #3389
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3389
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3389
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3389
- Changed files: 1
- Additions: 15
- Deletions: 23

## Files
- `src/main/scala/xiangshan/frontend/newRAS.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/newRAS.scala b/src/main/scala/xiangshan/frontend/newRAS.scala
index 5234f8aa0fd..99d1a131831 100644
--- a/src/main/scala/xiangshan/frontend/newRAS.scala
+++ b/src/main/scala/xiangshan/frontend/newRAS.scala
@@ -140,6 +140,8 @@ class RAS(implicit p: Parameters) extends BasePredictor {
       val NOS = Output(new RASPtr)
       val BOS = Output(new RASPtr)
 
+      val spec_near_overflow = Output(Bool())
+
       val debug = new RASDebug
     })
 
@@ -155,7 +157,7 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     val TOSW = RegInit(RASPtr(false.B, 0.U))
     val BOS = RegInit(RASPtr(false.B, 0.U))
 
-    val spec_overflowed = RegInit(false.B)
+    val spec_near_overflowed = RegInit(false.B)
 
     val writeBypassEntry = Reg(new RASEntry)
     val writeBypassNos = Reg(new RASPtr)
@@ -220,11 +222,6 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     def specPtrInc(ptr: RASPtr) = ptr + 1.U
     def specPtrDec(ptr: RASPtr) = ptr - 1.U
 
-
-
-
-
-
     when (io.redirect_valid && io.redirect_isCall) {
       writeBypassValidWire := true.B
       writeBypassValid := true.B
@@ -242,8 +239,6 @@ class RAS(implicit p: Parameters) extends BasePredictor {
       writeBypassValidWire := writeBypassValid
     }
 
-
-
     val topEntry = getTop(ssp, sctr, TOSR, TOSW, true)
     val topNos = getTopNos(TOSR, true)
     val redirectTopEntry = getTop(io.redirect_meta_ssp, io.redirect_meta_sctr, io.redirect_meta_TOSR, io.redirect_meta_TOSW, false)
@@ -380,8 +375,6 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     s3_missPushAddr := io.s3_meta.TOSW
     s3_missPushNos := io.s3_meta.TOSR
 
-
-
     realWriteEntry := Mux(io.redirect_isCall, realWriteEntry_next,
       Mux(io.s3_missed_push, s3_missPushEntry,
       realWriteEntry_next))
@@ -412,13 +405,7 @@ class RAS(implicit p: Parameters) extends BasePredictor {
         ssp := ptrInc(currentSsp)
         sctr := 0.U
       }
-      // if we are draining the capacity of spec queue, force move BOS forward
-      when (specPtrInc(currentTOSW) === BOS) {
-        BOS := specPtrInc(BOS)
-        spec_overflowed := true.B;
-      }
     }
-    XSPerfAccumulate("spec_overflowed", TOSW.value === BOS.value)
 
     when (io.spec_push_valid) {
       specPush(io.spec_push_addr, ssp, sctr, TOSR, TOSW, topEntry)
@@ -498,8 +485,6 @@ class RAS(implicit p: Parameters) extends BasePredictor {
 
     val commit_push_addr = spec_queue(io.commit_meta_TOSW.value).retAddr
 
-
-
     when (io.commit_push_valid) {
       val nsp_update = Wire(UInt(log2Up(rasSize).W))
       when (io.commit_meta_ssp =/= nsp) {
@@ -517,11 +502,8 @@ class RAS(implicit p: Parameters) extends BasePredictor {
         commit_stack(ptrInc(nsp_update)).retAddr := commit_push_addr
         commit_stack(ptrInc(nsp_update)).ctr := 0.U
       }
-      // when overflow, BOS may be forced move forward, do not revert those changes
-      when (!spec_overflowed || isAfter(io.commit_meta_TOSW, BOS)) {
-        BOS := io.commit_meta_TOSW
-        spec_overflowed := false.B
-      }
+
+      BOS := io.commit_meta_TOSW
 
       // XSError(io.commit_meta_ssp =/= nsp, "nsp mismatch with expected ssp")
       // XSError(io.commit_push_addr =/= commit_push_addr, "addr from commit mismatch with addr from spec")
@@ -541,6 +523,14 @@ class RAS(implicit p: Parameters) extends BasePredictor {
       }
     }
 
+    when(distanceBetween(TOSW,BOS) > (rasSpecSize - 4).U){
+      spec_near_overflowed  := true.B
+    }.otherwise{
+      spec_near_overflowed  := false.B
+    }
+
+    io.spec_near_overflow   := spec_near_overflowed
+    XSPerfAccumulate("spec_near_overflow", spec_near_overflowed)
     io.debug.commit_stack.zipWithIndex.foreach{case (a, i) => a := commit_stack(i)}
     io.debug.spec_nos.zipWithIndex.foreach{case (a, i) => a := spec_nos(i)}
     io.debug.spec_queue.zipWithIndex.foreach{ case (a, i) => a := spec_queue(i)}
@@ -627,6 +617,8 @@ class RAS(implicit p: Parameters) extends BasePredictor {
   last_stage_meta.ssp := s3_meta.ssp
   last_stage_meta.TOSW := s3_meta.TOSW
 
+  io.s1_ready   := !stack.spec_near_overflow
+
   io.out.last_stage_spec_info.sctr  := s3_meta.sctr
   io.out.last_stage_spec_info.ssp := s3_meta.ssp
   io.out.last_stage_spec_info.TOSW := s3_meta.TOSW
```
