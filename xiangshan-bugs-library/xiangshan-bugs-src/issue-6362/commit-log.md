# Commit Log
- Issue: #6362
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6362
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4050
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4050
- Changed files: 4
- Additions: 0
- Deletions: 44

## Files
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`
- `src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala`
- `src/main/scala/xiangshan/cache/dcache/meta/TagArray.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index 3b2c6f1ee70..f8d8fde82df 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -361,7 +361,6 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   //val delayedDcacheRefill = RegNext(dcache.io.lsu.lsq)
 
   val csrCtrl = DelayN(io.ooo_to_mem.csrCtrl, 2)
-  dcache.io.csr.distribute_csr <> csrCtrl.distribute_csr
   dcache.io.l2_pf_store_only := RegNext(io.ooo_to_mem.csrCtrl.l2_pf_store_only, false.B)
   io.error <> DelayNWithValid(dcache.io.error, 2)
   when(!csrCtrl.cache_error_enable){
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
index 8eaac17bcbe..50b86ca0c87 100644
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -795,7 +795,6 @@ class DCacheIO(implicit p: Parameters) extends DCacheBundle {
   val hartId = Input(UInt(hartIdLen.W))
   val l2_pf_store_only = Input(Bool())
   val lsu = new DCacheToLsuIO
-  val csr = new L1CacheToCsrIO
   val error = ValidIO(new L1CacheErrorInfo)
   val mshrFull = Output(Bool())
   val memSetPattenDetected = Output(Bool())
@@ -1671,32 +1670,6 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
     sink.bits    := source.bits
   }
 
-
-  //----------------------------------------
-  // Customized csr cache op support
-  val cacheOpDecoder = Module(new CSRCacheOpDecoder("dcache", CacheInstrucion.COP_ID_DCACHE))
-  cacheOpDecoder.io.csr <> io.csr
-  bankedDataArray.io.cacheOp.req := cacheOpDecoder.io.cache.req
-  // dup cacheOp_req_valid
-  bankedDataArray.io.cacheOp_req_dup.zipWithIndex.map{ case(dup, i) => dup := cacheOpDecoder.io.cache_req_dup(i) }
-  // dup cacheOp_req_bits_opCode
-  bankedDataArray.io.cacheOp_req_bits_opCode_dup.zipWithIndex.map{ case (dup, i) => dup := cacheOpDecoder.io.cacheOp_req_bits_opCode_dup(i) }
-
-  tagArray.io.cacheOp.req := cacheOpDecoder.io.cache.req
-  // dup cacheOp_req_valid
-  tagArray.io.cacheOp_req_dup.zipWithIndex.map{ case(dup, i) => dup := cacheOpDecoder.io.cache_req_dup(i) }
-  // dup cacheOp_req_bits_opCode
-  tagArray.io.cacheOp_req_bits_opCode_dup.zipWithIndex.map{ case (dup, i) => dup := cacheOpDecoder.io.cacheOp_req_bits_opCode_dup(i) }
-
-  cacheOpDecoder.io.cache.resp.valid := bankedDataArray.io.cacheOp.resp.valid ||
-    tagArray.io.cacheOp.resp.valid
-  cacheOpDecoder.io.cache.resp.bits := Mux1H(List(
-    bankedDataArray.io.cacheOp.resp.valid -> bankedDataArray.io.cacheOp.resp.bits,
-    tagArray.io.cacheOp.resp.valid -> tagArray.io.cacheOp.resp.bits,
-  ))
-  cacheOpDecoder.io.error := io.error
-  assert(!((bankedDataArray.io.cacheOp.resp.valid +& tagArray.io.cacheOp.resp.valid) > 1.U))
-
   //----------------------------------------
   // performance counters
   val num_loads = PopCount(ldu.map(e => e.io.lsu.req.fire))
diff --git a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
index a6d07a0983b..0dd809a59ec 100644
--- a/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/data/BankedDataArray.scala
@@ -262,10 +262,6 @@ abstract class AbstractBankedDataArray(implicit p: Parameters) extends DCacheMod
     // when bank_conflict, read (1) port should be ignored
     val bank_conflict_slow = Output(Vec(LoadPipelineWidth, Bool()))
     val disable_ld_fast_wakeup = Output(Vec(LoadPipelineWidth, Bool()))
-    // customized cache op port
-    val cacheOp = Flipped(new L1CacheInnerOpIO)
-    val cacheOp_req_dup = Vec(DCacheDupNum, Flipped(Valid(new CacheCtrlReqInfo)))
-    val cacheOp_req_bits_opCode_dup = Input(Vec(DCacheDupNum, UInt(XLEN.W)))
     val pseudo_error = Flipped(DecoupledIO(Vec(DCacheBanks, new CtrlUnitSignalingBundle)))
   })
 
@@ -603,9 +599,6 @@ class SramedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
     }
   }
 
-  io.cacheOp.resp.valid := false.B
-  io.cacheOp.resp.bits  := DontCare
-
   val tableName =  "BankConflict" + p(XSCoreParamsKey).HartId.toString
   val siteName = "BankedDataArray" + p(XSCoreParamsKey).HartId.toString
   val bankConflictTable = ChiselDB.createTable(tableName, new BankConflictDB)
@@ -893,9 +886,6 @@ class BankedDataArray(implicit p: Parameters) extends AbstractBankedDataArray {
     }
   }
 
-  io.cacheOp.resp.valid := false.B
-  io.cacheOp.resp.bits  := DontCare
-
   val tableName = "BankConflict" + p(XSCoreParamsKey).HartId.toString
   val siteName = "BankedDataArray" + p(XSCoreParamsKey).HartId.toString
   val bankConflictTable = ChiselDB.createTable(tableName, new BankConflictDB)
diff --git a/src/main/scala/xiangshan/cache/dcache/meta/TagArray.scala b/src/main/scala/xiangshan/cache/dcache/meta/TagArray.scala
index b02ab147b28..0130bd09701 100644
--- a/src/main/scala/xiangshan/cache/dcache/meta/TagArray.scala
+++ b/src/main/scala/xiangshan/cache/dcache/meta/TagArray.scala
@@ -97,10 +97,6 @@ class DuplicatedTagArray(readPorts: Int)(implicit p: Parameters) extends Abstrac
     val read = Vec(readPorts, Flipped(DecoupledIO(new TagReadReq)))
     val resp = Output(Vec(readPorts, Vec(nWays, UInt(encTagBits.W))))
     val write = Flipped(DecoupledIO(new TagWriteReq))
-    // customized cache op port
-    val cacheOp = Flipped(new L1CacheInnerOpIO)
-    val cacheOp_req_dup = Vec(DCacheDupNum, Flipped(Valid(new CacheCtrlReqInfo)))
-    val cacheOp_req_bits_opCode_dup = Input(Vec(DCacheDupNum, UInt(XLEN.W)))
   })
 
   val array = Seq.fill(readPorts) { Module(new TagArray) }
@@ -130,8 +126,6 @@ class DuplicatedTagArray(readPorts: Int)(implicit p: Parameters) extends Abstrac
     io.resp(i) := array(i).io.resp
     tag_read_oh(i) := PopCount(array(i).io.read.fire)
   }
-  io.cacheOp.resp.valid := false.B
-  io.cacheOp.resp.bits  := DontCare
 
   XSPerfAccumulate("tag_read_counter", tag_read_oh.reduce(_ + _))
 }
```
