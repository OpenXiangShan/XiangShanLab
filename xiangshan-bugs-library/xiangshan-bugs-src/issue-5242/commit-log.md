# Commit Log
- Issue: #5242
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5242
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5242
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5242
- Changed files: 4
- Additions: 38
- Deletions: 35

## Files
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 92dd55c7427..6c84586874e 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -134,7 +134,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
   // pmp
   val PortNumber = ICacheParameters().PortNumber
   val pmp        = Module(new PMP())
-  val pmp_check  = VecInit(Seq.fill(coreParams.ipmpPortNum)(Module(new PMPChecker(3, sameCycle = true)).io))
+  val pmp_check  = VecInit(Seq.fill(coreParams.ipmpPortNum)(Module(new PMPChecker(3)).io))
   pmp.io.distribute_csr := csrCtrl.distribute_csr
   val pmp_req_vec = Wire(Vec(coreParams.ipmpPortNum, Valid(new PMPReqBundle())))
   (0 until 2 * PortNumber).foreach(i => pmp_req_vec(i) <> icache.io.pmp(i).req)
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 3b3b3835679..fffdc1e053d 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -654,8 +654,8 @@ class NewIFU(implicit p: Parameters) extends XSModule
   // last instuction finish
   val is_first_instr = RegInit(true.B)
 
-  val m_idle :: m_waitLastCmt :: m_sendReq :: m_waitResp :: m_sendTLB :: m_tlbResp :: m_sendPMP :: m_resendReq :: m_waitResendResp :: m_waitCommit :: m_commited :: Nil =
-    Enum(11)
+  val m_idle :: m_waitLastCmt :: m_sendReq :: m_waitResp :: m_sendTLB :: m_tlbResp :: m_sendPMP :: m_pmpResp :: m_resendReq :: m_waitResendResp :: m_waitCommit :: m_commited :: Nil =
+    Enum(12)
   val mmio_state = RegInit(m_idle)
 
   // do mmio fetch only when pmp/pbmt shows it is a uncacheable address and no exception occurs
@@ -787,6 +787,10 @@ class NewIFU(implicit p: Parameters) extends XSModule
     }
 
     is(m_sendPMP) {
+      mmio_state := m_pmpResp
+    }
+
+    is(m_pmpResp) {
       val pmp_exception = ExceptionType.fromPMPResp(io.pmp.resp)
       // if pmp re-check respond mismatch with previous check, must be access fault
       val mmio_mismatch_exception = Mux(
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 1b7c0a618c8..a72a5ac9b51 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -296,14 +296,6 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule with HasICache
     p.bits.size := 3.U
     p.bits.cmd  := TlbCmd.exec
   }
-  private val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
-  private val s1_pmp_mmio      = VecInit(fromPMP.map(_.mmio))
-
-  // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next
-  private val s1_exception_out = ExceptionType.merge(
-    s1_itlb_exception,
-    s1_pmp_exception
-  )
 
   /**
     ******************************************************************************
@@ -357,9 +349,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule with HasICache
   private val s2_req_isForVSnonLeafPTE =
     RegEnable(s1_req_isForVSnonLeafPTE, 0.U.asTypeOf(s1_req_isForVSnonLeafPTE), s1_fire)
   private val s2_doubleline       = RegEnable(s1_doubleline, 0.U.asTypeOf(s1_doubleline), s1_fire)
-  private val s2_exception        = RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_fire)
+  private val s2_itlb_exception   = RegEnable(s1_itlb_exception, 0.U.asTypeOf(s1_itlb_exception), s1_fire)
   private val s2_backendException = RegEnable(s1_backendException, false.B, s1_fire)
-  private val s2_pmp_mmio         = RegEnable(s1_pmp_mmio, 0.U.asTypeOf(s1_pmp_mmio), s1_fire)
   private val s2_itlb_pbmt        = RegEnable(s1_itlb_pbmt, 0.U.asTypeOf(s1_itlb_pbmt), s1_fire)
   private val s2_waymasks         = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_fire)
 
@@ -373,6 +364,16 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule with HasICache
   private val s2_datas             = RegInit(VecInit(Seq.fill(ICacheDataBanks)(0.U((blockBits / ICacheDataBanks).W))))
   private val s2_data_is_from_MSHR = RegInit(VecInit(Seq.fill(ICacheDataBanks)(false.B)))
 
+  // receive & hold pmp result
+  private val s2_pmp_exception = DataHoldBypass(VecInit(fromPMP.map(ExceptionType.fromPMPResp)), RegNext(s1_fire))
+  private val s2_pmp_mmio      = DataHoldBypass(VecInit(fromPMP.map(_.mmio)), RegNext(s1_fire))
+
+  // merge s2 itlb/pmp exceptions, itlb has the highest priority, pmp next
+  private val s2_exception = ExceptionType.merge(
+    s2_itlb_exception,
+    s2_pmp_exception
+  )
+
   /**
     ******************************************************************************
     * ECC check
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 71984b6ed8a..f652f2b7be1 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -219,7 +219,7 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
   private val s1_itlb_pbmt = VecInit((0 until PortNumber).map { i =>
     ResultHoldBypass(
       valid = tlb_valid_pulse(i),
-      init = 0.U.asTypeOf(fromITLB(i).bits.pbmt(0)),
+      init = 0.U(Pbmt.width.W),
       data = fromITLB(i).bits.pbmt(0)
     )
   })
@@ -407,20 +407,6 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
     p.bits.size := 3.U
     p.bits.cmd  := TlbCmd.exec
   }
-  private val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
-  private val s1_pmp_mmio      = VecInit(fromPMP.map(_.mmio))
-
-  // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next
-  // for timing consideration, meta_corrupt is not merged, and it will NOT cancel prefetch
-  private val s1_exception_out = ExceptionType.merge(
-    s1_itlb_exception, // includes backend exception
-    s1_pmp_exception
-  )
-
-  // merge pmp mmio and itlb pbmt
-  private val s1_mmio = VecInit((s1_pmp_mmio zip s1_itlb_pbmt).map { case (mmio, pbmt) =>
-    mmio || Pbmt.isUncache(pbmt)
-  })
 
   /**
     ******************************************************************************
@@ -498,16 +484,28 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
   private val s2_isSoftPrefetch = RegEnable(s1_isSoftPrefetch, 0.U.asTypeOf(s1_isSoftPrefetch), s1_real_fire)
   private val s2_doubleline     = RegEnable(s1_doubleline, 0.U.asTypeOf(s1_doubleline), s1_real_fire)
   private val s2_req_paddr      = RegEnable(s1_req_paddr, 0.U.asTypeOf(s1_req_paddr), s1_real_fire)
-  private val s2_exception =
-    RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_real_fire) // includes itlb/pmp exception
-  // disabled for timing consideration
-// private val s2_exception_in =
-//   RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_real_fire)
-  private val s2_mmio     = RegEnable(s1_mmio, 0.U.asTypeOf(s1_mmio), s1_real_fire)
-  private val s2_waymasks = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_real_fire)
+  private val s2_itlb_exception = RegEnable(s1_itlb_exception, 0.U.asTypeOf(s1_itlb_exception), s1_real_fire)
+  private val s2_itlb_pbmt      = RegEnable(s1_itlb_pbmt, 0.U.asTypeOf(s1_itlb_pbmt), s1_real_fire)
+  private val s2_waymasks       = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_real_fire)
   // disabled for timing consideration
 // private val s2_meta_codes   = RegEnable(s1_meta_codes, 0.U.asTypeOf(s1_meta_codes), s1_real_fire)
 
+  // receive pmp result
+  private val s2_pmp_exception = DataHoldBypass(VecInit(fromPMP.map(ExceptionType.fromPMPResp)), RegNext(s1_real_fire))
+  private val s2_pmp_mmio      = DataHoldBypass(VecInit(fromPMP.map(_.mmio)), RegNext(s1_real_fire))
+
+  // merge s2 itlb/pmp exceptions, itlb has the highest priority, pmp next
+  // for timing consideration, meta_corrupt is not merged, and it will NOT cancel prefetch
+  private val s2_exception = ExceptionType.merge(
+    s2_itlb_exception, // includes backend exception
+    s2_pmp_exception
+  )
+
+  // merge pmp mmio and itlb pbmt
+  private val s2_mmio = VecInit((s2_pmp_mmio zip s2_itlb_pbmt).map { case (mmio, pbmt) =>
+    mmio || Pbmt.isUncache(pbmt)
+  })
+
   private val s2_req_vSetIdx = s2_req_vaddr.map(get_idx)
   private val s2_req_ptags   = s2_req_paddr.map(get_phy_tag)
```
