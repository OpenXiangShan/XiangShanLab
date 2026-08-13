# Commit Log
- Issue: #6095
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6095
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6095
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6095
- Changed files: 10
- Additions: 57
- Deletions: 33

## Files
- `src/main/scala/device/TLPMA/TLPMA.scala`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/PMParameters.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/PMA.scala`
- `src/main/scala/xiangshan/backend/fu/PMP.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/device/TLPMA/TLPMA.scala b/src/main/scala/device/TLPMA/TLPMA.scala
index c039820d942..cf36ceb97e8 100644
--- a/src/main/scala/device/TLPMA/TLPMA.scala
+++ b/src/main/scala/device/TLPMA/TLPMA.scala
@@ -49,7 +49,7 @@ class TLPMA(implicit p: Parameters) extends LazyModule with PMAConst with MMPMAM
         mmpma.sameCycle/* pmaParam.sameCycle*/,
         false)).io
     ))
-    pma_check.map(_.check_env.apply(mmpma.lgMaxSize.U, pma/*placeHolder*/, pma))
+    pma_check.map(_.check_env.apply(mmpma.lgMaxSize.U, false.B, pma/*placeHolder*/, pma))
     for (i <- 0 until mmpma.num) {
       pma_check(i).req_apply(req(i).valid, req(i).bits.addr)
       resp(i) := pma_check(i).resp
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index d6b78b4e633..1acea42c035 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -576,6 +576,7 @@ class TlbCsrBundle(implicit p: Parameters) extends XSBundle {
     val spvp = UInt(1.W)
     val imode = UInt(2.W)
     val dmode = UInt(2.W)
+    val debug = Bool()
   }
   val mPBMTE = Bool()
   val hPBMTE = Bool()
diff --git a/src/main/scala/xiangshan/PMParameters.scala b/src/main/scala/xiangshan/PMParameters.scala
index 6ec43d11b09..64c83c65daa 100644
--- a/src/main/scala/xiangshan/PMParameters.scala
+++ b/src/main/scala/xiangshan/PMParameters.scala
@@ -19,6 +19,7 @@ package xiangshan
 import chisel3.util.log2Ceil
 import org.chipsalliance.cde.config.{Field, Parameters}
 import freechips.rocketchip.tile.XLen
+import freechips.rocketchip.devices.debug.DebugModuleKey
 import system.{CVMParamsKey, SoCParamsKey}
 import xiangshan.backend.fu.{MMPMAConfig, MMPMAMethod}
 
@@ -57,4 +58,7 @@ trait HasPMParameters {
 
   def PlatformGrain = pmParams.PlatformGrain
   def mmpma = pmParams.mmpma
+
+  def debugStart = p(DebugModuleKey).get.baseAddress
+  def debugEnd = debugStart + 0xFFF
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 29b4efd9ebb..3b0f6bc95e5 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -216,6 +216,7 @@ class NewCSR(implicit val p: Parameters) extends Module
       val spvp = Bool()
       val imode = UInt(2.W)
       val dmode = UInt(2.W)
+      val debug = Bool()
       val dvirt = Bool()
       val mPBMTE = Bool()
       val hPBMTE = Bool()
@@ -1467,6 +1468,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     mstatus.regOut.MPV.asUInt,
     V.asUInt
   )
+  io.tlb.debug := debugMode
   io.tlb.mPBMTE := RegNext(menvcfg.regOut.PBMTE.asBool)
   io.tlb.hPBMTE := RegNext(henvcfg.regOut.PBMTE.asBool)
   io.tlb.pmm.mseccfg := RegNext(mseccfg.regOut.PMM.asUInt)
diff --git a/src/main/scala/xiangshan/backend/fu/PMA.scala b/src/main/scala/xiangshan/backend/fu/PMA.scala
index ae7d37575eb..be9610a76bc 100644
--- a/src/main/scala/xiangshan/backend/fu/PMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMA.scala
@@ -223,6 +223,7 @@ trait PMACheckMethod extends PMPConst {
     size: UInt,
     pmaEntries: Vec[PMPEntry],
     mode: UInt,
+    debug: Bool,
     lgMaxSize: Int
   ) = {
     val num = pmaEntries.size
@@ -237,7 +238,8 @@ trait PMACheckMethod extends PMPConst {
     val cfg_vec = Wire(Vec(num+1, new PMPEntry()))
 
     pmaEntries.zip(pmaDefault +: pmaEntries.take(num-1)).zipWithIndex.foreach{ case ((pma, last_pma), i) =>
-      val is_match = pma.is_match(addr, size, lgMaxSize, last_pma)
+      val is_match = pma.is_match(addr, size, lgMaxSize, last_pma) &&
+                     Mux(addr >= debugStart.U && addr <= debugEnd.U, debug, true.B)
       val aligned = pma.aligned(addr, size, lgMaxSize, last_pma)
 
       val cur = WireInit(pma)
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index 224c26e9f6a..7f655c23e8d 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -417,6 +417,7 @@ trait PMPCheckMethod extends PMPConst {
     size: UInt,
     pmpEntries: Vec[PMPEntry],
     mode: UInt,
+    debug: Bool,
     lgMaxSize: Int
   ) = {
     val num = pmpEntries.size
@@ -432,7 +433,8 @@ trait PMPCheckMethod extends PMPConst {
     val cfg_vec = Wire(Vec(num+1, new PMPEntry()))
 
     pmpEntries.zip(pmpDefault +: pmpEntries.take(num-1)).zipWithIndex.foreach{ case ((pmp, last_pmp), i) =>
-      val is_match = pmp.is_match(addr, size, lgMaxSize, last_pmp)
+      val is_match = pmp.is_match(addr, size, lgMaxSize, last_pmp) &&
+                     Mux(addr >= debugStart.U && addr <= debugEnd.U, debug, true.B)
       val ignore = passThrough && !pmp.cfg.l
       val aligned = pmp.aligned(addr, size, lgMaxSize, last_pmp)
 
@@ -462,29 +464,33 @@ class PMPCheckerEnv(implicit p: Parameters) extends PMPBundle {
   val keyIDen = Bool()
   val cmode = Bool()
   val mode = UInt(2.W)
+  val debug = Bool()
   val pmp = Vec(NumPMPReal, new PMPEntry())
   val pma = Vec(NumPMAReal, new PMPEntry())
 
-  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
+  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
     this.keyIDen := keyIDen
     this.cmode := cmode
     this.mode := mode
+    this.debug := debug
     this.pmp := pmp
     this.pma := pma
   }
 
-  def apply(cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
+  def apply(cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
     this.keyIDen := false.B
     this.cmode := cmode
     this.mode := mode
+    this.debug := debug
     this.pmp := pmp
     this.pma := pma
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry]): Unit = {
     this.keyIDen := false.B
     this.cmode := true.B
     this.mode := mode
+    this.debug := debug
     this.pmp := pmp
     this.pma := pma
   }
@@ -495,20 +501,20 @@ class PMPCheckIO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
   val req = Flipped(Valid(new PMPReqBundle(lgMaxSize))) // usage: assign the valid to fire signal
   val resp = new PMPRespBundle()
 
-  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(keyIDen, cmode, mode, pmp, pma)
+  def apply(keyIDen: Bool, cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(keyIDen, cmode, mode, debug, pmp, pma)
     this.req := req
     resp
   }
 
-  def apply(cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(cmode, mode, pmp, pma)
+  def apply(cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(cmode, mode, debug, pmp, pma)
     this.req := req
     resp
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(mode, debug, pmp, pma)
     this.req := req
     resp
   }
@@ -518,8 +524,8 @@ class PMPCheckIO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
     this.req.bits.apply(addr)
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
+    check_env.apply(mode, debug, pmp, pma)
     req_apply(valid, addr)
     resp
   }
@@ -530,14 +536,14 @@ class PMPCheckv2IO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
   val req = Flipped(Valid(new PMPReqBundle(lgMaxSize))) // usage: assign the valid to fire signal
   val resp = Output(new PMPConfig())
 
-  def apply(cmode: Bool, mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
-    check_env.apply(cmode, mode, pmp, pma)
+  def apply(cmode: Bool, mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
+    check_env.apply(cmode, mode, debug, pmp, pma)
     req_apply(valid, addr)
     resp
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], req: Valid[PMPReqBundle]) = {
+    check_env.apply(mode, debug, pmp, pma)
     this.req := req
     resp
   }
@@ -547,8 +553,8 @@ class PMPCheckv2IO(lgMaxSize: Int)(implicit p: Parameters) extends PMPBundle {
     this.req.bits.apply(addr)
   }
 
-  def apply(mode: UInt, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
-    check_env.apply(mode, pmp, pma)
+  def apply(mode: UInt, debug: Bool, pmp: Vec[PMPEntry], pma: Vec[PMPEntry], valid: Bool, addr: UInt) = {
+    check_env.apply(mode, debug, pmp, pma)
     req_apply(valid, addr)
     resp
   }
@@ -584,13 +590,13 @@ class PMPChecker
    */
 
   val check_addr = Mux(io.check_env.keyIDen, req.addr(PMPAddrBits-PMPKeyIDBits-1, 0), req.addr)
-  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pmp, io.check_env.mode, lgMaxSize)
-  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pma, io.check_env.mode, lgMaxSize)
+  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pmp, io.check_env.mode, io.check_env.debug, lgMaxSize)
+  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(check_addr, req.size, io.check_env.pma, io.check_env.mode, io.check_env.debug, lgMaxSize)
 
   val cmd = if(leaveHitMux) RegEnable(req.cmd, io.req.valid) else req.cmd
   val resp_pmp = pmp_check(cmd, res_pmp.cfg)
   val resp_pma = pma_check(cmd, res_pma.cfg)
-  
+
   def keyid_check(leaveHitMux: Boolean = false, valid: Bool = true.B, addr: UInt) = {
     val resp = Wire(new PMPRespBundle)
     val keyid_nz = if (PMPKeyIDBits > 0) addr(PMPAddrBits-1, PMPAddrBits-PMPKeyIDBits) =/= 0.U else false.B
@@ -632,8 +638,8 @@ class PMPCheckerv2
 
   val req = io.req.bits
 
-  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pmp, io.check_env.mode, lgMaxSize)
-  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pma, io.check_env.mode, lgMaxSize)
+  val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pmp, io.check_env.mode, io.check_env.debug, lgMaxSize)
+  val res_pma = pma_match_res(leaveHitMux, io.req.valid)(req.addr, req.size, io.check_env.pma, io.check_env.mode, io.check_env.debug, lgMaxSize)
 
   val resp = and(res_pmp, res_pma)
 
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index c1866e0c1b6..cb7a4375796 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -296,6 +296,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   tlb.priv.virt_changed := DataChanged(tlb.priv.virt)
   tlb.priv.imode := csrMod.io.tlb.imode
   tlb.priv.dmode := csrMod.io.tlb.dmode
+  tlb.priv.debug := csrMod.io.tlb.debug
 
   // Svpbmt extension enable
   tlb.mPBMTE := csrMod.io.tlb.mPBMTE
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index 357c27c525c..0a24def8fd3 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -95,12 +95,12 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
   pmp.io.distribute_csr := io.csr.distribute_csr
   if (HasBitmapCheck) {
     if (KeyIDBits > 0) {
-      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.KEYIDEN.asBool, csr_dup(0).mbmc.CMODE.asBool, ModeS, pmp.io.pmp, pmp.io.pma))
+      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.KEYIDEN.asBool, csr_dup(0).mbmc.CMODE.asBool, ModeS, csr_dup(0).priv.debug, pmp.io.pmp, pmp.io.pma))
     } else {
-      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.CMODE.asBool, ModeS, pmp.io.pmp, pmp.io.pma))
+      pmp_check.foreach(_.check_env.apply(csr_dup(0).mbmc.CMODE.asBool, ModeS, csr_dup(0).priv.debug, pmp.io.pmp, pmp.io.pma))
     }
   } else {
-    pmp_check.foreach(_.check_env.apply(ModeS, pmp.io.pmp, pmp.io.pma))
+    pmp_check.foreach(_.check_env.apply(ModeS, csr_dup(0).priv.debug, pmp.io.pmp, pmp.io.pma))
   }
 
   // add bitmapcheck
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 6b746831aa4..9da6a29f234 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -147,15 +147,23 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
           tlbCsr.mbmc.KEYIDEN.asBool,
           tlbCsr.mbmc.CMODE.asBool,
           tlbCsr.priv.imode,
+          tlbCsr.priv.debug,
           pmp.io.pmp,
           pmp.io.pma,
           pmp_req_vec(i)
         )
       } else {
-        pmp_check(i).apply(tlbCsr.mbmc.CMODE.asBool, tlbCsr.priv.imode, pmp.io.pmp, pmp.io.pma, pmp_req_vec(i))
+        pmp_check(i).apply(
+          tlbCsr.mbmc.CMODE.asBool,
+          tlbCsr.priv.imode,
+          tlbCsr.priv.debug,
+          pmp.io.pmp,
+          pmp.io.pma,
+          pmp_req_vec(i)
+        )
       }
     } else {
-      pmp_check(i).apply(tlbCsr.priv.imode, pmp.io.pmp, pmp.io.pma, pmp_req_vec(i))
+      pmp_check(i).apply(tlbCsr.priv.imode, tlbCsr.priv.debug, pmp.io.pmp, pmp.io.pma, pmp_req_vec(i))
     }
   }
   (0 until 2 * PortNumber).foreach(i => icache.io.pmp(i).resp <> pmp_check(i).resp)
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 19a8bc19ebe..82cf8139223 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -792,12 +792,12 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   for ((p,d) <- pmp_check zip dtlb_pmps) {
     if (HasBitmapCheck) {
       if (KeyIDBits > 0) {
-        p.apply(tlbcsr.mbmc.KEYIDEN.asBool, tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, pmp.io.pmp, pmp.io.pma, d)
+        p.apply(tlbcsr.mbmc.KEYIDEN.asBool, tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
       } else {
-        p.apply(tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, pmp.io.pmp, pmp.io.pma, d)
+        p.apply(tlbcsr.mbmc.CMODE.asBool, tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
       }
     } else {
-      p.apply(tlbcsr.priv.dmode, pmp.io.pmp, pmp.io.pma, d)
+      p.apply(tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
     }
     require(p.req.bits.size.getWidth == d.bits.size.getWidth)
   }
```
