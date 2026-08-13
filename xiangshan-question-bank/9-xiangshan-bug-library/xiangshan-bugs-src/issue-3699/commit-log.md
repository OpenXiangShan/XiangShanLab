# Commit Log
- Issue: #3699
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3699
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3699
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3699
- Changed files: 2
- Additions: 17
- Deletions: 8

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index 9bc6a3040c3..831b29c55ec 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -276,6 +276,7 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
       unSentLoads := 1.U
       curPtr := 0.U
       new128Load.vaddr := aligned16BytesAddr
+      new128Load.fullva := req.fullva
       // new128Load.mask  := (getMask(req.uop.fuOpType(1, 0)) << aligned16BytesSel).asUInt
       new128Load.mask  := 0xffff.U
       new128Load.uop   := req.uop
@@ -288,8 +289,10 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
       curPtr := 0.U
       lowAddrLoad.uop := req.uop
       lowAddrLoad.uop.exceptionVec(loadAddrMisaligned) := false.B
+      lowAddrLoad.fullva := req.fullva
       highAddrLoad.uop := req.uop
       highAddrLoad.uop.exceptionVec(loadAddrMisaligned) := false.B
+      highAddrLoad.fullva := req.fullva
 
       switch (req.uop.fuOpType(1, 0)) {
         is (LB) {
@@ -566,8 +569,11 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
 
   // NOTE: spectial case (unaligned load cross page, page fault happens in next page)
   // if exception happens in the higher page address part, overwrite the loadExceptionBuffer vaddr
-  val overwriteExpBuf = GatedValidRegNext(req_valid && cross16BytesBoundary && globalException && (curPtr === 1.U))
-  val overwriteVaddr = GatedRegNext(splitLoadResp(curPtr).vaddr)
+  val overwriteExpBuf = GatedValidRegNext(req_valid && globalException)
+  val overwriteVaddr = GatedRegNext(Mux(
+    cross16BytesBoundary && (curPtr === 1.U), 
+    splitLoadResp(curPtr).vaddr,
+    splitLoadResp(curPtr).fullva))
   val overwriteIsHyper = GatedRegNext(splitLoadResp(curPtr).isHyper)
   val overwriteGpaddr = GatedRegNext(splitLoadResp(curPtr).gpaddr)
   val overwriteIsForVSnonLeafPTE = GatedRegNext(splitLoadResp(curPtr).isForVSnonLeafPTE)
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 769cfe9535e..f151a67095f 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -640,12 +640,15 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   )
 
   // only first issue of int / vec load intructions need to check full vaddr
-  s0_tlb_fullva := Mux(s0_src_select_vec(vec_iss_idx),
-    io.vecldin.bits.vaddr,
-    Mux(
-      s0_src_select_vec(int_iss_idx),
-      io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), XLEN),
-      s0_dcache_vaddr
+  s0_tlb_fullva := Mux(s0_src_valid_vec(mab_idx),
+    io.misalign_ldin.bits.fullva,
+    Mux(s0_src_select_vec(vec_iss_idx),
+      io.vecldin.bits.vaddr,
+      Mux(
+        s0_src_select_vec(int_iss_idx),
+        io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), XLEN),
+        s0_dcache_vaddr
+      )
     )
   )
```
