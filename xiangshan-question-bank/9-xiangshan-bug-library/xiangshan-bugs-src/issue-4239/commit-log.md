# Commit Log
- Issue: #4239
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4239
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4239
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4239
- Changed files: 1
- Additions: 4
- Deletions: 10

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 17057016e1f..af1140bfef6 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -23,14 +23,14 @@ import utils._
 import utility._
 import xiangshan.ExceptionNO._
 import xiangshan._
-import xiangshan.backend.Bundles.{MemExuInput, MemExuOutput}
+import xiangshan.backend.Bundles.{MemExuInput, MemExuOutput, connectSamePort}
 import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.backend.fu.FuConfig._
 import xiangshan.backend.fu.FuType._
 import xiangshan.backend.ctrlblock.DebugLsInfoBundle
 import xiangshan.backend.fu.NewCSR._
-import xiangshan.cache.mmu.{TlbCmd, TlbReq, TlbRequestIO, TlbResp, Pbmt}
-import xiangshan.cache.{DcacheStoreRequestIO, DCacheStoreIO, MemoryOpConstants, HasDCacheParameters, StorePrefetchReq}
+import xiangshan.cache.mmu.{Pbmt, TlbCmd, TlbReq, TlbRequestIO, TlbResp}
+import xiangshan.cache.{DCacheStoreIO, DcacheStoreRequestIO, HasDCacheParameters, MemoryOpConstants, StorePrefetchReq}
 
 class StoreUnit(implicit p: Parameters) extends XSModule
   with HasDCacheParameters
@@ -486,14 +486,8 @@ class StoreUnit(implicit p: Parameters) extends XSModule
 
   val s2_misalign_stout = WireInit(0.U.asTypeOf(io.misalign_stout))
   s2_misalign_stout.valid := s2_valid && s2_can_go && s2_frm_mabuf
-  s2_misalign_stout.bits.mmio := s2_out.mmio
-  s2_misalign_stout.bits.vaddr := s2_out.vaddr
-  s2_misalign_stout.bits.isHyper := s2_out.isHyper
-  s2_misalign_stout.bits.paddr := s2_out.paddr
-  s2_misalign_stout.bits.gpaddr := s2_out.gpaddr
-  s2_misalign_stout.bits.isForVSnonLeafPTE := s2_out.isForVSnonLeafPTE
+  connectSamePort(s2_misalign_stout.bits, s2_out)
   s2_misalign_stout.bits.need_rep := RegEnable(s1_tlb_miss, s1_fire)
-  s2_misalign_stout.bits.uop.exceptionVec := s2_out.uop.exceptionVec
   io.misalign_stout := s2_misalign_stout
 
   // mmio and exception
```
