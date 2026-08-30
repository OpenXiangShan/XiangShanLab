# Commit Log
- Issue: #4619
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4619
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4619
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4619
- Changed files: 4
- Additions: 7
- Deletions: 6

## Files
- `difftest`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index 826fe99144a..1953acff267 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit 826fe99144a2abe97905177e86a20b5c445955ad
+Subproject commit 1953acff26721e990086613e3b86cf4339122ca0
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index 24f7135607e..64ecf248a35 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -214,7 +214,7 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.ncOut.bits := DontCare
     io.ncOut.bits.uop := selUop
     io.ncOut.bits.uop.lqIdx := req.uop.lqIdx
-    io.ncOut.bits.uop.exceptionVec(loadAccessFault) := nderr
+    io.ncOut.bits.uop.exceptionVec(hardwareError) := nderr
     io.ncOut.bits.data := rdataPartialLoad
     io.ncOut.bits.paddr := req.paddr
     io.ncOut.bits.vaddr := req.vaddr
@@ -229,7 +229,7 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
     io.mmioOut.bits := DontCare
     io.mmioOut.bits.uop := selUop
     io.mmioOut.bits.uop.lqIdx := req.uop.lqIdx
-    io.mmioOut.bits.uop.exceptionVec(loadAccessFault) := nderr
+    io.mmioOut.bits.uop.exceptionVec(hardwareError) := nderr
     io.mmioOut.bits.data := rdataPartialLoad
     io.mmioOut.bits.debug.isMMIO := true.B
     io.mmioOut.bits.debug.isNC := false.B
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index f5934a7f7df..6f5b382e155 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -831,13 +831,13 @@ class StoreQueue(implicit p: Parameters) extends XSModule
         mmioState := s_wb
 
         when (io.uncache.resp.bits.nderr || io.cmoOpResp.bits.nderr) {
-          uncacheUop.exceptionVec(storeAccessFault) := true.B
+          uncacheUop.exceptionVec(hardwareError) := true.B
         }
       }
     }
     is(s_wb) {
       when (io.mmioStout.fire || io.vecmmioStout.fire) {
-        when (uncacheUop.exceptionVec(storeAccessFault)) {
+        when (uncacheUop.exceptionVec(hardwareError)) {
           mmioState := s_idle
         }.otherwise {
           mmioState := s_wait
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 6382235eb81..d464b593e3e 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1028,8 +1028,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
     s1_out.uop.exceptionVec(loadPageFault)      := false.B
     s1_out.uop.exceptionVec(loadGuestPageFault) := false.B
     s1_out.uop.exceptionVec(loadAddrMisaligned) := false.B
+    s1_out.uop.exceptionVec(loadAccessFault)    := false.B
+    s1_out.uop.exceptionVec(hardwareError)      := s1_dly_err && s1_vecActive
     s1_out.isMisalign := false.B
-    s1_out.uop.exceptionVec(loadAccessFault)    := s1_dly_err && s1_vecActive
   }
 
   // pointer chasing
```
