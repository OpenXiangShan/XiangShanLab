# Commit Log
- Issue: #6404
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6404
- Issue state: closed
- Tested RTL commit: a7fc87df1e82211eb47f3c74a8c93cd9d425572b
- Related PR: #3215
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3215
- Changed files: 2
- Additions: 13
- Deletions: 6

## Files
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 9149cc18c91..d5dc008f20c 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -422,7 +422,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * response to IFU
     ******************************************************************************
     */
-  (0 until PortNumber).map{ i =>
+  (0 until PortNumber).foreach{ i =>
     if(i == 0) {
       toIFU(i).valid                        := s2_fire
       toIFU(i).bits.tlbExcp.pageFault       := s2_excp_tlb_pf(i)
@@ -431,6 +431,10 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
       toIFU(i).bits.tlbExcp.mmio            := s2_excp_pmp_mmio(0) && !s2_excp_tlb(0) && !s2_excp_pmp_af(0)
       toIFU(i).bits.data                    := s2_datas.asTypeOf(UInt(blockBits.W))
     } else {
+      /* Note: toIFU(1).bits.tlbExcp.xxx is already "&&ed" with doubleline before it goes into WayLookup (see IPrefetch.scala)
+       * so we actually don't need do "&&" again here,
+       * but as excp_pmp_xxx and corrupt does not, we keep all the "&&" logic here for clarity
+       */
       toIFU(i).valid                        := s2_fire && s2_doubleline
       toIFU(i).bits.tlbExcp.pageFault       := s2_excp_tlb_pf(i) && s2_doubleline
       toIFU(i).bits.tlbExcp.guestPageFault  := s2_excp_tlb_gpf(i) && s2_doubleline
@@ -440,7 +444,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     }
     toIFU(i).bits.vaddr                     := s2_req_vaddr(i)
     toIFU(i).bits.paddr                     := s2_req_paddr(i)
-    toIFU(i).bits.gpaddr                    := s2_req_gpaddr
+    toIFU(i).bits.gpaddr                    := s2_req_gpaddr  // Note: toIFU(1).bits.gpaddr is actually DontCare in current design
   }
 
   s2_flush := io.flush
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 4d744a09e7a..fcdd916b975 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -265,10 +265,13 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   toWayLookup.bits.waymask      := s1_waymasks
   toWayLookup.bits.ptag         := s1_req_ptags
   toWayLookup.bits.gpaddr       := s1_req_gpaddr
-  toWayLookup.bits.excp_tlb_af  := itlbExcpAF
-  toWayLookup.bits.excp_tlb_pf  := itlbExcpPF
-  toWayLookup.bits.excp_tlb_gpf := itlbExcpGPF
-  toWayLookup.bits.meta_errors  := s1_meta_errors
+  (0 until PortNumber).foreach { i =>
+    val excpValid = (if (i == 0) true.B else s1_doubleline)  // exception in first line is always valid, in second line is valid iff is doubleline request
+    toWayLookup.bits.excp_tlb_af(i)  := excpValid && itlbExcpAF(i)
+    toWayLookup.bits.excp_tlb_pf(i)  := excpValid && itlbExcpPF(i)
+    toWayLookup.bits.excp_tlb_gpf(i) := excpValid && itlbExcpGPF(i)
+    toWayLookup.bits.meta_errors(i)  := excpValid && s1_meta_errors(i)
+  }
 
   val s1_waymasks_vec = s1_waymasks.map(_.asTypeOf(Vec(nWays, Bool())))
   when(toWayLookup.fire) {
```
