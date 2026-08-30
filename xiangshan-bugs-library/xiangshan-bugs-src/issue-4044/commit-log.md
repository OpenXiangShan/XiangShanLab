# Commit Log
- Issue: #4044
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4044
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4044
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4044
- Changed files: 23
- Additions: 482
- Deletions: 128

## Files
- `src/main/scala/top/Configs.scala`
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/XSTile.scala`
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/backend/fu/CSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/PMA.scala`
- `src/main/scala/xiangshan/backend/fu/util/CSRConst.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`
- `src/main/scala/xiangshan/frontend/icache/InstrUncache.scala`
- `src/main/scala/xiangshan/frontend/icache/WayLookup.scala`

## Diff
```diff
diff --git a/src/main/scala/top/Configs.scala b/src/main/scala/top/Configs.scala
index c20f9d8c997..840c8ad8202 100644
--- a/src/main/scala/top/Configs.scala
+++ b/src/main/scala/top/Configs.scala
@@ -118,6 +118,7 @@ class MinimalConfig(n: Int = 1) extends Config(
           tagECC = Some("parity"),
           dataECC = Some("parity"),
           replacer = Some("setplru"),
+          cacheCtrlAddressOpt = Some(AddressSet(0x38022080, 0x7f)),
         ),
         dcacheParametersOpt = Some(DCacheParameters(
           nSets = 64, // 32KB DCache
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index a5875e8494e..665af521788 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -595,8 +595,6 @@ class CustomCSRCtrlIO(implicit p: Parameters) extends XSBundle {
   val l1D_pf_active_stride = Output(UInt(6.W))
   val l1D_pf_enable_stride = Output(Bool())
   val l2_pf_store_only = Output(Bool())
-  // ICache
-  val icache_parity_enable = Output(Bool())
   // Load violation predictor
   val lvpred_disable = Output(Bool())
   val no_spec_load = Output(Bool())
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index 160ea17d3ec..937580f4386 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -81,6 +81,7 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
   ))
 
   val i_mmio_port = TLTempNode()
+  val icachectrl_port_opt = if(icacheParameters.cacheCtrlAddressOpt.nonEmpty) Option(TLTempNode()) else None
   val d_mmio_port = TLTempNode()
 
   val misc_l2_pmu = BusPerfMonitor(name = "Misc_L2", enable = !debugOpts.FPGAPlatform) // l1D & l1I & PTW
@@ -137,16 +138,19 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
   mmio_xbar := TLBuffer.chainNode(2) := i_mmio_port
   mmio_xbar := TLBuffer.chainNode(2) := d_mmio_port
   beu.node := TLBuffer.chainNode(1) := mmio_xbar
-  if (dcacheParameters.cacheCtrlAddressOpt.nonEmpty) {
-    mmio_port :=
-      TLFilter(TLFilter.mSubtract(dcacheParameters.cacheCtrlAddressOpt.get)) :=
-      TLBuffer() :=
-      mmio_xbar
-  } else {
-    mmio_port :=
-      TLBuffer() :=
-      mmio_xbar
+  if (icacheParameters.cacheCtrlAddressOpt.nonEmpty) {
+    icachectrl_port_opt.get := TLBuffer.chainNode(1) := mmio_xbar
   }
+
+  // filter out in-core addresses before sent to mmio_port
+  // Option[AddressSet] ++ Option[AddressSet] => List[AddressSet]
+  private def mmioFilters: Seq[AddressSet] =
+    (icacheParameters.cacheCtrlAddressOpt ++ dcacheParameters.cacheCtrlAddressOpt).toSeq
+  mmio_port :=
+    TLFilter(TLFilter.mSubtract(mmioFilters)) :=
+    TLBuffer() :=
+    mmio_xbar
+
   class Imp(wrapper: LazyModule) extends LazyModuleImp(wrapper) {
     val io = IO(new Bundle {
       val beu_errors = Input(chiselTypeOf(beu.module.io.errors))
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 586fe9d52b0..3887cb48c77 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -337,6 +337,7 @@ case class XSCoreParameters
     tagECC = Some("parity"),
     dataECC = Some("parity"),
     replacer = Some("setplru"),
+    cacheCtrlAddressOpt = Some(AddressSet(0x38022080, 0x7f))
   ),
   dcacheParametersOpt: Option[DCacheParameters] = Some(DCacheParameters(
     tagECC = Some("secded"),
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index 11196085d05..ae349c3380e 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -66,6 +66,9 @@ abstract class XSCoreBase()(implicit p: config.Parameters) extends LazyModule
 
   memBlock.inner.frontendBridge.icache_node := frontend.inner.icache.clientNode
   memBlock.inner.frontendBridge.instr_uncache_node := frontend.inner.instrUncache.clientNode
+  if (icacheParameters.cacheCtrlAddressOpt.nonEmpty) {
+    frontend.inner.icache.ctrlUnitOpt.get.node := memBlock.inner.frontendBridge.icachectrl_node
+  }
 }
 
 class XSCore()(implicit p: config.Parameters) extends XSCoreBase
diff --git a/src/main/scala/xiangshan/XSTile.scala b/src/main/scala/xiangshan/XSTile.scala
index 8642ff984a2..e88be0db82f 100644
--- a/src/main/scala/xiangshan/XSTile.scala
+++ b/src/main/scala/xiangshan/XSTile.scala
@@ -91,6 +91,9 @@ class XSTile()(implicit p: Parameters) extends LazyModule
 
   // mmio
   l2top.inner.i_mmio_port := l2top.inner.i_mmio_buffer.node := memBlock.frontendBridge.instr_uncache_node
+  if (icacheParameters.cacheCtrlAddressOpt.nonEmpty) {
+    memBlock.frontendBridge.icachectrl_node := l2top.inner.icachectrl_port_opt.get
+  }
   l2top.inner.d_mmio_port := memBlock.uncache_port
 
   // =========== IO Connection ============
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index 3b2c6f1ee70..1ab956cf724 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -226,9 +226,22 @@ class ICacheBuffer()(implicit p: Parameters) extends LazyModule {
   }
 }
 
+class ICacheCtrlBuffer()(implicit p: Parameters) extends LazyModule {
+  val node = new TLBufferNode(BufferParams.default, BufferParams.default, BufferParams.default, BufferParams.default, BufferParams.default)
+  lazy val module = new ICacheCtrlBufferImpl
+
+  class ICacheCtrlBufferImpl extends LazyModuleImp(this) {
+    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
+      out.a <> BufferParams.default(BufferParams.default(in.a))
+      in.d <> BufferParams.default(BufferParams.default(out.d))
+    }
+  }
+}
+
 // Frontend bus goes through MemBlock
 class FrontendBridge()(implicit p: Parameters) extends LazyModule {
   val icache_node = LazyModule(new ICacheBuffer()).suggestName("icache").node// to keep IO port name
+  val icachectrl_node = LazyModule(new ICacheCtrlBuffer()).suggestName("icachectrl").node
   val instr_uncache_node = LazyModule(new InstrUncacheBuffer()).suggestName("instr_uncache").node
   lazy val module = new LazyModuleImp(this) {
   }
diff --git a/src/main/scala/xiangshan/backend/fu/CSR.scala b/src/main/scala/xiangshan/backend/fu/CSR.scala
index ac07c2755d9..8e600d061f3 100644
--- a/src/main/scala/xiangshan/backend/fu/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/CSR.scala
@@ -516,10 +516,6 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
   csrio.customCtrl.l1D_pf_enable_stride := spfctl(16)
   csrio.customCtrl.l2_pf_store_only := spfctl(17)
 
-  // sfetchctl Bit 0: L1I Cache Parity check enable
-  val sfetchctl = RegInit(UInt(XLEN.W), "b0".U)
-  csrio.customCtrl.icache_parity_enable := sfetchctl(0)
-
   // slvpredctl: load violation predict settings
   // Default reset period: 2^16
   // Why this number: reset more frequently while keeping the overhead low
@@ -810,7 +806,6 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
     //--- Supervisor Custom Read/Write Registers
     MaskedRegMap(Sbpctl, sbpctl),
     MaskedRegMap(Spfctl, spfctl),
-    MaskedRegMap(Sfetchctl, sfetchctl),
     MaskedRegMap(Slvpredctl, slvpredctl),
     MaskedRegMap(Smblockctl, smblockctl),
     MaskedRegMap(Srnctl, srnctl),
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
index 586b1ac3703..c5180b0e5b3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala
@@ -181,8 +181,6 @@ object CSRBundles {
     val l1D_pf_active_stride = Output(UInt(6.W))
     val l1D_pf_enable_stride = Output(Bool())
     val l2_pf_store_only = Output(Bool())
-    // ICache
-    val icache_parity_enable = Output(Bool())
     // Load violation predictor
     val lvpred_disable = Output(Bool())
     val no_spec_load = Output(Bool())
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
index f21d9b2d9a4..63f63239f75 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
@@ -31,16 +31,12 @@ trait CSRCustom { self: NewCSR =>
   val srnctl = Module(new CSRModule("Srnctl", new SrnctlBundle))
     .setAddr(0x5C4)
 
-  val sfetchctl = Module(new CSRModule("Sfetchctl", new SfetchctlBundle))
-    .setAddr(0x9E0)
-
   val customCSRMods = Seq(
     sbpctl,
     spfctl,
     slvpredctl,
     smblockctl,
     srnctl,
-    sfetchctl,
   )
 
   val customCSRMap: SeqMap[Int, (CSRAddrWriteBundle[_ <: CSRBundle], UInt)] = SeqMap.from(
@@ -99,10 +95,6 @@ class SrnctlBundle extends CSRBundle {
   val FUSION_ENABLE  = RW(0).withReset(true.B)
 }
 
-class SfetchctlBundle extends CSRBundle {
-  val ICACHE_PARITY_ENABLE = RW(0).withReset(false.B) // L1I Cache Parity check enable
-}
-
 object SbufferThreshold extends CSREnum with RWApply {
   val initValue = Value(7.U)
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 27cf5a67bee..06109840e62 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1219,8 +1219,6 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.status.custom.l1D_pf_enable_stride    := spfctl.regOut.L1D_PF_ENABLE_STRIDE.asBool
   io.status.custom.l2_pf_store_only        := spfctl.regOut.L2_PF_STORE_ONLY.asBool
 
-  io.status.custom.icache_parity_enable    := sfetchctl.regOut.ICACHE_PARITY_ENABLE.asBool
-
   io.status.custom.lvpred_disable          := slvpredctl.regOut.LVPRED_DISABLE.asBool
   io.status.custom.no_spec_load            := slvpredctl.regOut.NO_SPEC_LOAD.asBool
   io.status.custom.storeset_wait_store     := slvpredctl.regOut.STORESET_WAIT_STORE.asBool
diff --git a/src/main/scala/xiangshan/backend/fu/PMA.scala b/src/main/scala/xiangshan/backend/fu/PMA.scala
index 818aed9ecd7..4c69351e3a5 100644
--- a/src/main/scala/xiangshan/backend/fu/PMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMA.scala
@@ -111,7 +111,8 @@ trait PMAMethod extends PMAConst {
       MemMap("h00_3802_0000", "h00_3802_0FFF",   "h0", "DebugModule", "RWX"),
       MemMap("h00_3802_1000", "h00_3802_1FFF",   "h0", "MMPMA",       "RW"),
       MemMap("h00_3802_2000", "h00_3802_207F",   "h0", "L1DCacheCtrl", "RW"),
-      MemMap("h00_3802_2080", "h00_38FF_FFFF",   "h0", "Reserved",    ""),
+      MemMap("h00_3802_2080", "h00_3802_20FF",   "h0", "L1ICacheCtrl", "RW"),
+      MemMap("h00_3802_2100", "h00_38FF_FFFF",   "h0", "Reserved",    ""),
       MemMap("h00_3900_0000", "h00_3900_1FFF",   "h0", "L3CacheCtrl",  "RW"),
       MemMap("h00_3900_2000", "h00_39FF_FFFF",   "h0", "Reserved",    ""),
       MemMap("h00_3A00_0000", "h00_3FFF_FFFF",   "h0", "",            "RW),
diff --git a/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala b/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
index 25b8b2428e4..bfc15013bae 100644
--- a/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
+++ b/src/main/scala/xiangshan/backend/fu/util/CSRConst.scala
@@ -111,9 +111,6 @@ trait HasCSRConst {
   /** 0x5C5-0x5E5 for cache instruction register*/
   val Scachebase    = 0x5C5
 
-  // Supervisor Custom Read/Write
-  val Sfetchctl     = 0x9e0
-
   // Hypervisor Trap Setup
   val Hstatus       = 0x600
   val Hedeleg       = 0x602
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index 790e2758024..156d843270c 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -323,8 +323,6 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       custom.l1D_pf_active_stride     := csrMod.io.status.custom.l1D_pf_active_stride
       custom.l1D_pf_enable_stride     := csrMod.io.status.custom.l1D_pf_enable_stride
       custom.l2_pf_store_only         := csrMod.io.status.custom.l2_pf_store_only
-      // ICache
-      custom.icache_parity_enable     := csrMod.io.status.custom.icache_parity_enable
       // Load violation predictor
       custom.lvpred_disable           := csrMod.io.status.custom.lvpred_disable
       custom.no_spec_load             := csrMod.io.status.custom.no_spec_load
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index 85289d7a8ad..de130f4cbc4 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -181,8 +181,7 @@ class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
 
   ifu.io.icachePerfInfo := icache.io.perfInfo
 
-  icache.io.csr_pf_enable     := RegNext(csrCtrl.l1I_pf_enable)
-  icache.io.csr_parity_enable := RegNext(csrCtrl.icache_parity_enable)
+  icache.io.csr_pf_enable := RegNext(csrCtrl.l1I_pf_enable)
 
   icache.io.fencei := RegNext(io.fencei)
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index 781b8698e34..2153f1de05d 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -27,6 +27,7 @@ package xiangshan.frontend.icache
 
 import chisel3._
 import chisel3.util._
+import freechips.rocketchip.diplomacy.AddressSet
 import freechips.rocketchip.diplomacy.IdRange
 import freechips.rocketchip.diplomacy.LazyModule
 import freechips.rocketchip.diplomacy.LazyModuleImp
@@ -36,6 +37,7 @@ import huancun.AliasField
 import huancun.PrefetchField
 import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
 import xiangshan._
 import xiangshan.cache._
 import xiangshan.cache.mmu.TlbRequestIO
@@ -57,9 +59,10 @@ case class ICacheParameters(
     ICacheDataBanks:     Int = 8,
     ICacheDataSRAMWidth: Int = 66,
     // TODO: hard code, need delete
-    partWayNum: Int = 4,
-    nMMIOs:     Int = 1,
-    blockBytes: Int = 64
+    partWayNum:          Int = 4,
+    nMMIOs:              Int = 1,
+    blockBytes:          Int = 64,
+    cacheCtrlAddressOpt: Option[AddressSet] = None
 ) extends L1CacheParameters {
 
   val setBytes:     Int         = nSets * blockBytes
@@ -77,6 +80,14 @@ case class ICacheParameters(
 trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst with HasIFUConst {
   val cacheParams: ICacheParameters = icacheParameters
 
+  def ctrlUnitParamsOpt: Option[L1ICacheCtrlParams] = OptionWrapper(
+    cacheParams.cacheCtrlAddressOpt.nonEmpty,
+    L1ICacheCtrlParams(
+      address = cacheParams.cacheCtrlAddressOpt.get,
+      regWidth = XLEN
+    )
+  )
+
   def ICacheSets:          Int = cacheParams.nSets
   def ICacheWays:          Int = cacheParams.nWays
   def PortNumber:          Int = cacheParams.PortNumber
@@ -138,19 +149,6 @@ trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst wi
   def InitQueue[T <: Data](entry: T, size: Int): Vec[T] =
     RegInit(VecInit(Seq.fill(size)(0.U.asTypeOf(entry.cloneType))))
 
-  def encodeMetaECC(meta: UInt): UInt = {
-    require(meta.getWidth == ICacheMetaBits)
-    val code = cacheParams.tagCode.encode(meta) >> ICacheMetaBits
-    code.asTypeOf(UInt(ICacheMetaCodeBits.W))
-  }
-
-  def encodeDataECC(data: UInt): UInt = {
-    require(data.getWidth == ICacheDataBits)
-    val datas = data.asTypeOf(Vec(ICacheDataCodeSegs, UInt((ICacheDataBits / ICacheDataCodeSegs).W)))
-    val codes = VecInit(datas.map(cacheParams.dataCode.encode(_) >> (ICacheDataBits / ICacheDataCodeSegs)))
-    codes.asTypeOf(UInt(ICacheDataCodeBits.W))
-  }
-
   def getBankSel(blkOffset: UInt, valid: Bool = true.B): Vec[UInt] = {
     val bankIdxLow  = (Cat(0.U(1.W), blkOffset) >> log2Ceil(blockBytes / ICacheDataBanks)).asUInt
     val bankIdxHigh = ((Cat(0.U(1.W), blkOffset) + 32.U) >> log2Ceil(blockBytes / ICacheDataBanks)).asUInt
@@ -178,6 +176,21 @@ trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst wi
     VecInit((vaddrVec zip ptagVec).map { case (vaddr, ptag) => getPaddrFromPtag(vaddr, ptag) })
 }
 
+trait HasICacheECCHelper extends HasICacheParameters {
+  def encodeMetaECC(meta: UInt, poison: Bool = false.B): UInt = {
+    require(meta.getWidth == ICacheMetaBits)
+    val code = cacheParams.tagCode.encode(meta, poison) >> ICacheMetaBits
+    code.asTypeOf(UInt(ICacheMetaCodeBits.W))
+  }
+
+  def encodeDataECC(data: UInt, poison: Bool = false.B): UInt = {
+    require(data.getWidth == ICacheDataBits)
+    val datas = data.asTypeOf(Vec(ICacheDataCodeSegs, UInt((ICacheDataBits / ICacheDataCodeSegs).W)))
+    val codes = VecInit(datas.map(cacheParams.dataCode.encode(_, poison) >> (ICacheDataBits / ICacheDataCodeSegs)))
+    codes.asTypeOf(UInt(ICacheDataCodeBits.W))
+  }
+}
+
 abstract class ICacheBundle(implicit p: Parameters) extends XSBundle
     with HasICacheParameters
 
@@ -207,17 +220,17 @@ class ICacheMetaArrayIO(implicit p: Parameters) extends ICacheBundle {
   val flushAll: Bool                               = Input(Bool())
 }
 
-class ICacheMetaArray(implicit p: Parameters) extends ICacheArray {
+class ICacheMetaArray(implicit p: Parameters) extends ICacheArray with HasICacheECCHelper {
   class ICacheMetaEntry(implicit p: Parameters) extends ICacheBundle {
     val meta: ICacheMetadata = new ICacheMetadata
     val code: UInt           = UInt(ICacheMetaCodeBits.W)
   }
 
   private object ICacheMetaEntry {
-    def apply(meta: ICacheMetadata)(implicit p: Parameters): ICacheMetaEntry = {
+    def apply(meta: ICacheMetadata, poison: Bool)(implicit p: Parameters): ICacheMetaEntry = {
       val entry = Wire(new ICacheMetaEntry)
       entry.meta := meta
-      entry.code := encodeMetaECC(meta.asUInt)
+      entry.code := encodeMetaECC(meta.asUInt, poison)
       entry
     }
   }
@@ -243,10 +256,11 @@ class ICacheMetaArray(implicit p: Parameters) extends ICacheArray {
   private val write_bank_0 = io.write.valid && !io.write.bits.bankIdx
   private val write_bank_1 = io.write.valid && io.write.bits.bankIdx
 
-  private val write_meta_bits = ICacheMetaEntry(meta =
-    ICacheMetadata(
+  private val write_meta_bits = ICacheMetaEntry(
+    meta = ICacheMetadata(
       tag = io.write.bits.phyTag
-    )
+    ),
+    poison = io.write.bits.poison
   )
 
   private val tagArrays = (0 until PortNumber) map { bank =>
@@ -368,17 +382,17 @@ class ICacheDataArrayIO(implicit p: Parameters) extends ICacheBundle {
   val readResp: ICacheDataRespBundle               = Output(new ICacheDataRespBundle)
 }
 
-class ICacheDataArray(implicit p: Parameters) extends ICacheArray {
+class ICacheDataArray(implicit p: Parameters) extends ICacheArray with HasICacheECCHelper {
   class ICacheDataEntry(implicit p: Parameters) extends ICacheBundle {
     val data: UInt = UInt(ICacheDataBits.W)
     val code: UInt = UInt(ICacheDataCodeBits.W)
   }
 
   private object ICacheDataEntry {
-    def apply(data: UInt)(implicit p: Parameters): ICacheDataEntry = {
+    def apply(data: UInt, poison: Bool)(implicit p: Parameters): ICacheDataEntry = {
       val entry = Wire(new ICacheDataEntry)
       entry.data := data
-      entry.code := encodeDataECC(data)
+      entry.code := encodeDataECC(data, poison)
       entry
     }
   }
@@ -391,7 +405,7 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray {
     ******************************************************************************
     */
   private val writeDatas   = io.write.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt(ICacheDataBits.W)))
-  private val writeEntries = writeDatas.map(ICacheDataEntry(_).asUInt)
+  private val writeEntries = writeDatas.map(ICacheDataEntry(_, io.write.bits.poison).asUInt)
 
   // io.read() are copies to control fan-out, we can simply use .head here
   private val bankSel  = getBankSel(io.read.head.bits.blkOffset, io.read.head.valid)
@@ -529,8 +543,7 @@ class ICacheIO(implicit p: Parameters) extends ICacheBundle {
   // backend/BEU
   val error: Valid[L1CacheErrorInfo] = ValidIO(new L1CacheErrorInfo)
   // backend/CSR
-  val csr_pf_enable:     Bool = Input(Bool())
-  val csr_parity_enable: Bool = Input(Bool())
+  val csr_pf_enable: Bool = Input(Bool())
   // flush
   val fencei: Bool = Input(Bool())
   val flush:  Bool = Input(Bool())
@@ -553,6 +566,8 @@ class ICache()(implicit p: Parameters) extends LazyModule with HasICacheParamete
 
   val clientNode: TLClientNode = TLClientNode(Seq(clientParameters))
 
+  val ctrlUnitOpt: Option[ICacheCtrlUnit] = ctrlUnitParamsOpt.map(params => LazyModule(new ICacheCtrlUnit(params)))
+
   lazy val module: ICacheImp = new ICacheImp(this)
 }
 
@@ -582,21 +597,52 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   private val prefetcher = Module(new IPrefetchPipe)
   private val wayLookup  = Module(new WayLookup)
 
-  dataArray.io.write <> missUnit.io.data_write
+  private val ecc_enable = if (outer.ctrlUnitOpt.nonEmpty) outer.ctrlUnitOpt.get.module.io.ecc_enable else true.B
+
+  // dataArray io
+  if (outer.ctrlUnitOpt.nonEmpty) {
+    val ctrlUnit = outer.ctrlUnitOpt.get.module
+    when(ctrlUnit.io.injecting) {
+      dataArray.io.write <> ctrlUnit.io.dataWrite
+      missUnit.io.data_write.ready := false.B
+    }.otherwise {
+      ctrlUnit.io.dataWrite.ready := false.B
+      dataArray.io.write <> missUnit.io.data_write
+    }
+  } else {
+    dataArray.io.write <> missUnit.io.data_write
+  }
   dataArray.io.read <> mainPipe.io.dataArray.toIData
-  dataArray.io.readResp <> mainPipe.io.dataArray.fromIData
+  mainPipe.io.dataArray.fromIData := dataArray.io.readResp
 
+  // metaArray io
   metaArray.io.flushAll := io.fencei
   metaArray.io.flush <> mainPipe.io.metaArrayFlush
-  metaArray.io.write <> missUnit.io.meta_write
-  metaArray.io.read <> prefetcher.io.metaRead.toIMeta
-  metaArray.io.readResp <> prefetcher.io.metaRead.fromIMeta
-
-  prefetcher.io.flush             := io.flush
-  prefetcher.io.csr_pf_enable     := io.csr_pf_enable
-  prefetcher.io.csr_parity_enable := io.csr_parity_enable
-  prefetcher.io.MSHRResp          := missUnit.io.fetch_resp
-  prefetcher.io.flushFromBpu      := io.ftqPrefetch.flushFromBpu
+  if (outer.ctrlUnitOpt.nonEmpty) {
+    val ctrlUnit = outer.ctrlUnitOpt.get.module
+    when(ctrlUnit.io.injecting) {
+      metaArray.io.write <> ctrlUnit.io.metaWrite
+      metaArray.io.read <> ctrlUnit.io.metaRead
+      missUnit.io.meta_write.ready         := false.B
+      prefetcher.io.metaRead.toIMeta.ready := false.B
+    }.otherwise {
+      ctrlUnit.io.metaWrite.ready := false.B
+      ctrlUnit.io.metaRead.ready  := false.B
+      metaArray.io.write <> missUnit.io.meta_write
+      metaArray.io.read <> prefetcher.io.metaRead.toIMeta
+    }
+    ctrlUnit.io.metaReadResp := metaArray.io.readResp
+  } else {
+    metaArray.io.write <> missUnit.io.meta_write
+    metaArray.io.read <> prefetcher.io.metaRead.toIMeta
+  }
+  prefetcher.io.metaRead.fromIMeta := metaArray.io.readResp
+
+  prefetcher.io.flush         := io.flush
+  prefetcher.io.csr_pf_enable := io.csr_pf_enable
+  prefetcher.io.ecc_enable    := ecc_enable
+  prefetcher.io.MSHRResp      := missUnit.io.fetch_resp
+  prefetcher.io.flushFromBpu  := io.ftqPrefetch.flushFromBpu
   // cache softPrefetch
   private val softPrefetchValid = RegInit(false.B)
   private val softPrefetch      = RegInit(0.U.asTypeOf(new IPrefetchReq))
@@ -634,11 +680,11 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   missUnit.io.mem_grant.bits  := DontCare
   missUnit.io.mem_grant <> bus.d
 
-  mainPipe.io.flush             := io.flush
-  mainPipe.io.respStall         := io.stop
-  mainPipe.io.csr_parity_enable := io.csr_parity_enable
-  mainPipe.io.hartId            := io.hartId
-  mainPipe.io.mshr.resp         := missUnit.io.fetch_resp
+  mainPipe.io.flush      := io.flush
+  mainPipe.io.respStall  := io.stop
+  mainPipe.io.ecc_enable := ecc_enable
+  mainPipe.io.hartId     := io.hartId
+  mainPipe.io.mshr.resp  := missUnit.io.fetch_resp
   mainPipe.io.fetch.req <> io.fetch.req
   mainPipe.io.wayLookupRead <> wayLookup.io.read
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala b/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
index 61ab2ba08dd..ba17509fb61 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
@@ -33,12 +33,14 @@ class ICacheMetaWriteBundle(implicit p: Parameters) extends ICacheBundle {
   val phyTag:  UInt = UInt(tagBits.W)
   val waymask: UInt = UInt(nWays.W)
   val bankIdx: Bool = Bool()
+  val poison:  Bool = Bool()
 
-  def generate(tag: UInt, idx: UInt, waymask: UInt, bankIdx: Bool): Unit = {
+  def generate(tag: UInt, idx: UInt, waymask: UInt, bankIdx: Bool, poison: Bool): Unit = {
     this.virIdx  := idx
     this.phyTag  := tag
     this.waymask := waymask
     this.bankIdx := bankIdx
+    this.poison  := poison
   }
 }
 
@@ -52,12 +54,14 @@ class ICacheDataWriteBundle(implicit p: Parameters) extends ICacheBundle {
   val data:    UInt = UInt(blockBits.W)
   val waymask: UInt = UInt(nWays.W)
   val bankIdx: Bool = Bool()
+  val poison:  Bool = Bool()
 
-  def generate(data: UInt, idx: UInt, waymask: UInt, bankIdx: Bool): Unit = {
+  def generate(data: UInt, idx: UInt, waymask: UInt, bankIdx: Bool, poison: Bool): Unit = {
     this.virIdx  := idx
     this.data    := data
     this.waymask := waymask
     this.bankIdx := bankIdx
+    this.poison  := poison
   }
 }
 
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala
new file mode 100644
index 00000000000..98a8a69b53f
--- /dev/null
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheCtrlUnit.scala
@@ -0,0 +1,297 @@
+/***************************************************************************************
+* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2020-2021 Peng Cheng Laboratory
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http://license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
+
+package xiangshan.frontend.icache
+
+import annotation.unused
+import chisel3._
+import chisel3.util._
+import freechips.rocketchip.diplomacy._
+import freechips.rocketchip.regmapper._
+import freechips.rocketchip.tilelink._
+import org.chipsalliance.cde.config.Parameters
+import utils._
+
+case class L1ICacheCtrlParams(
+    address:   AddressSet,
+    regWidth:  Int,
+    beatBytes: Int = 8
+) {
+  def regBytes: Int = regWidth / 8
+
+  def eccctrlOffset:  Int = 0
+  def ecciaddrOffset: Int = eccctrlOffset + regBytes
+}
+
+class ICacheCtrlUnitIO(implicit p: Parameters) extends ICacheBundle {
+  // ecc control
+  val ecc_enable: Bool = Output(Bool())
+  // ecc inject
+  val injecting:    Bool                               = Output(Bool())
+  val metaRead:     DecoupledIO[ICacheReadBundle]      = DecoupledIO(new ICacheReadBundle)
+  val metaReadResp: ICacheMetaRespBundle               = Input(new ICacheMetaRespBundle)
+  val metaWrite:    DecoupledIO[ICacheMetaWriteBundle] = DecoupledIO(new ICacheMetaWriteBundle)
+  val dataWrite:    DecoupledIO[ICacheDataWriteBundle] = DecoupledIO(new ICacheDataWriteBundle)
+}
+
+// currently for ECC control only
+class ICacheCtrlUnit(params: L1ICacheCtrlParams)(implicit p: Parameters) extends LazyModule {
+  lazy val module = new ICacheCtrlUnitImp(this)
+
+  // register tilelink node
+  val device: SimpleDevice = new SimpleDevice("L1ICacheCtrl", Seq("xiangshan,l1icache_ctrl"))
+
+  val node: TLRegisterNode = TLRegisterNode(
+    address = Seq(params.address),
+    device = device,
+    beatBytes = params.beatBytes,
+    concurrency = 1
+  )
+
+  class ICacheCtrlUnitImp(wrapper: LazyModule) extends LazyModuleImp(wrapper) with HasICacheParameters {
+    val io: ICacheCtrlUnitIO = IO(new ICacheCtrlUnitIO)
+
+    // eccctrl.ierror: inject error code
+    private def nInjError: Int = 8
+    private object eccctrlInjError extends NamedUInt(log2Up(nInjError)) {
+      def notEnabled:    UInt = 0.U(width.W) // try to inject when ECC check is not enabled
+      def targetInvalid: UInt = 1.U(width.W) // try to inject to invalid(rsvd) eccctrl.itarget
+      def notFound:      UInt = 2.U(width.W) // try to inject to ecciaddr.paddr does not exist in ICache
+      @unused
+      def rsvd3: UInt = 3.U(width.W)
+      @unused
+      def rsvd4: UInt = 4.U(width.W)
+      @unused
+      def rsvd5: UInt = 5.U(width.W)
+      @unused
+      def rsvd6: UInt = 6.U(width.W)
+      @unused
+      def rsvd7: UInt = 7.U(width.W)
+    }
+    // eccctrl.istatus: inject status
+    private def nInjStatus: Int = 8
+    private object eccctrlInjStatus extends NamedUInt(log2Up(nInjStatus)) {
+      def idle:     UInt = 0.U(width.W)
+      def working:  UInt = 1.U(width.W)
+      def injected: UInt = 2.U(width.W)
+      def error:    UInt = 7.U(width.W)
+      @unused
+      def rsvd3: UInt = 3.U(width.W)
+      @unused
+      def rsvd4: UInt = 4.U(width.W)
+      @unused
+      def rsvd5: UInt = 5.U(width.W)
+      @unused
+      def rsvd6: UInt = 6.U(width.W)
+    }
+    // eccctrl.itarget: inject target
+    private def nInjTarget: Int = 4
+    private object eccctrlInjTarget extends NamedUInt(log2Up(nInjTarget)) {
+      def metaArray: UInt = 0.U(width.W)
+      def dataArray: UInt = 2.U(width.W)
+      @unused
+      def rsvd1: UInt = 1.U(width.W)
+      @unused
+      def rsvd3: UInt = 3.U(width.W)
+    }
+    private class eccctrlBundle extends Bundle {
+      val ierror:  UInt = eccctrlInjError()  // inject error code, read-only, valid only when istatus === error
+      val istatus: UInt = eccctrlInjStatus() // inject status, read-only
+      val itarget: UInt = eccctrlInjTarget() // inject target
+      val inject:  Bool = Bool()             // request to inject, write-only, read 0
+      val enable:  Bool = Bool()             // enable ECC
+    }
+    private object eccctrlBundle {
+      def default: eccctrlBundle = {
+        val x = Wire(new eccctrlBundle)
+        x.ierror  := eccctrlInjError.notEnabled
+        x.istatus := eccctrlInjStatus.idle
+        x.itarget := eccctrlInjTarget.metaArray
+        x.inject  := false.B
+        x.enable  := true.B
+        x
+      }
+    }
+
+    private class ecciaddrBundle extends Bundle {
+      val paddr: UInt = UInt(PAddrBits.W) // inject position physical address
+    }
+    private object ecciaddrBundle {
+      def default: ecciaddrBundle = {
+        val x = Wire(new ecciaddrBundle)
+        x.paddr := 0.U
+        x
+      }
+    }
+
+    private val eccctrl  = RegInit(eccctrlBundle.default)
+    private val ecciaddr = RegInit(ecciaddrBundle.default)
+
+    // sanity check
+    require(params.regWidth >= eccctrl.asUInt.getWidth)
+    require(params.regWidth >= ecciaddr.asUInt.getWidth)
+
+    // control signal
+    io.ecc_enable := eccctrl.enable
+    io.injecting  := eccctrl.istatus === eccctrlInjStatus.working
+
+    // inject position
+    private val ivirIdx  = get_idx(ecciaddr.paddr)
+    private val iphyTag  = get_tag(ecciaddr.paddr)
+    private val iwaymask = RegInit(0.U(nWays.W)) // read from metaArray, valid after istate === is_readMetaResp
+
+    // inject FSM
+    private val is_idle :: is_readMetaReq :: is_readMetaResp :: is_writeMeta :: is_writeData :: Nil =
+      Enum(5)
+    private val istate = RegInit(is_idle)
+
+    io.metaRead.valid             := istate === is_readMetaReq
+    io.metaRead.bits.isDoubleLine := false.B // we inject into first cacheline and ignore the rest port
+    io.metaRead.bits.vSetIdx      := VecInit(Seq.fill(PortNumber)(ivirIdx))
+    io.metaRead.bits.waymask   := VecInit(Seq.fill(PortNumber)(VecInit(Seq.fill(nWays)(false.B)))) // dontcare
+    io.metaRead.bits.blkOffset := 0.U(blockBits.W)                                                 // dontcare
+
+    io.metaWrite.valid := istate === is_writeMeta
+    io.metaWrite.bits.generate(
+      tag = iphyTag,
+      idx = ivirIdx,
+      waymask = iwaymask,
+      bankIdx = ivirIdx(0),
+      poison = true.B
+    )
+
+    io.dataWrite.valid := istate === is_writeData
+    io.dataWrite.bits.generate(
+      data = 0.U, // inject poisoned data, don't care actual data
+      idx = ivirIdx,
+      waymask = iwaymask,
+      bankIdx = ivirIdx(0),
+      poison = true.B
+    )
+
+    switch(istate) {
+      is(is_idle) {
+        when(eccctrl.istatus === eccctrlInjStatus.working) {
+          // we need to read meta first to get waymask, whether itarget is metaArray or dataArray
+          istate := is_readMetaReq
+        }
+      }
+      is(is_readMetaReq) {
+        when(io.metaRead.fire) {
+          istate := is_readMetaResp
+        }
+      }
+      is(is_readMetaResp) {
+        // metaArray ensures resp is valid one cycle after req
+        val waymask = VecInit((0 until nWays).map { w =>
+          io.metaReadResp.entryValid.head(w) && io.metaReadResp.tags.head(w) === iphyTag
+        }).asUInt
+        iwaymask := waymask
+        when(!waymask.orR) {
+          // not hit, refuse to inject
+          istate          := is_idle
+          eccctrl.istatus := eccctrlInjStatus.error
+          eccctrl.ierror  := eccctrlInjError.notFound
+        }.otherwise {
+          istate := Mux(eccctrl.itarget === eccctrlInjTarget.metaArray, is_writeMeta, is_writeData)
+        }
+      }
+      is(is_writeMeta) {
+        when(io.metaWrite.fire) {
+          istate          := is_idle
+          eccctrl.istatus := eccctrlInjStatus.injected
+        }
+      }
+      is(is_writeData) {
+        when(io.dataWrite.fire) {
+          istate          := is_idle
+          eccctrl.istatus := eccctrlInjStatus.injected
+        }
+      }
+    }
+
+    private def eccctrlRegDesc: RegFieldDesc =
+      RegFieldDesc(
+        name = s"ecc_control",
+        desc = s"ECC control",
+        group = Option(s"ecc_control"),
+        groupDesc = Option(s"ECC Control"),
+        reset = Option(0)
+      )
+
+    private def ecciaddrRegDesc: RegFieldDesc =
+      RegFieldDesc(
+        name = s"ecc_iaddr",
+        desc = s"ECC Inject Address",
+        group = Option(s"ecc_iaddr"),
+        groupDesc = Option(s"ECC Inject Address"),
+        reset = Option(0)
+      )
+
+    private def eccctrlRegField(x: eccctrlBundle): RegField =
+      RegField(
+        params.regWidth,
+        RegReadFn { ready =>
+          val res = WireInit(x)
+          res.inject := false.B // read always 0
+          when(ready) {
+            // if istatus is injected or error, clear it after read
+            when(x.istatus === eccctrlInjStatus.injected || x.istatus === eccctrlInjStatus.error) {
+              x.istatus := eccctrlInjStatus.idle
+              x.ierror  := eccctrlInjError.notEnabled
+            }
+          }
+          // always read valid
+          (true.B, res.asUInt)
+        },
+        RegWriteFn { (valid, data) =>
+          when(valid) {
+            val req = data.asTypeOf(new eccctrlBundle)
+            x.enable := req.enable
+            when(req.inject && x.istatus === eccctrlInjStatus.idle) {
+              // if istatus is not idle, ignore the inject request
+              when(req.enable === false.B) {
+                // check if enable is not valid
+                x.istatus := eccctrlInjStatus.error
+                x.ierror  := eccctrlInjError.notEnabled
+              }.elsewhen(req.itarget =/= eccctrlInjTarget.metaArray && req.itarget =/= eccctrlInjTarget.dataArray) {
+                // check if itarget is not valid
+                x.istatus := eccctrlInjStatus.error
+                x.ierror  := eccctrlInjError.targetInvalid
+              }.otherwise {
+                x.istatus := eccctrlInjStatus.working
+              }
+            }
+            x.itarget := req.itarget
+            // istatus is read-only, ignore req.istatus
+            // ierror is read-only, ignore req.ierror
+          }
+          // always ready to write
+          true.B
+        },
+        eccctrlRegDesc
+      )
+
+    private def ecciaddrRegField(x: ecciaddrBundle): RegField =
+      RegField(params.regWidth, x.asUInt, ecciaddrRegDesc)
+
+    node.regmap(
+      params.eccctrlOffset  -> Seq(eccctrlRegField(eccctrl)),
+      params.ecciaddrOffset -> Seq(ecciaddrRegField(ecciaddr))
+    )
+  }
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 64a7781257d..7f36f078176 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -1,5 +1,6 @@
 /***************************************************************************************
-* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
 * Copyright (c) 2020-2021 Peng Cheng Laboratory
 *
 * XiangShan is licensed under Mulan PSL v2.
@@ -97,6 +98,7 @@ class ICacheMainPipeInterface(implicit p: Parameters) extends ICacheBundle {
   val touch:          Vec[Valid[ReplacerTouch]]         = Vec(PortNumber, ValidIO(new ReplacerTouch))
   val wayLookupRead:  DecoupledIO[WayLookupInfo]        = Flipped(DecoupledIO(new WayLookupInfo))
   val mshr:           ICacheMSHRBundle                  = new ICacheMSHRBundle
+  val ecc_enable:     Bool                              = Input(Bool())
 
   /*** outside interface ***/
   // FTQ
@@ -108,8 +110,6 @@ class ICacheMainPipeInterface(implicit p: Parameters) extends ICacheBundle {
   val respStall: Bool = Input(Bool())
   // backend/BEU
   val errors: Vec[Valid[L1CacheErrorInfo]] = Output(Vec(PortNumber, ValidIO(new L1CacheErrorInfo)))
-  // backend/CSR
-  val csr_parity_enable: Bool = Input(Bool())
 
   /*** PERF ***/
   val perfInfo: ICachePerfInfo = Output(new ICachePerfInfo)
@@ -121,7 +121,7 @@ class ICacheMainPipeInterface(implicit p: Parameters) extends ICacheBundle {
 //  val hit:       Bool = Bool()
 //}
 
-class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
+class ICacheMainPipe(implicit p: Parameters) extends ICacheModule with HasICacheECCHelper {
   val io: ICacheMainPipeInterface = IO(new ICacheMainPipeInterface)
 
   /** Input/Output port */
@@ -131,8 +131,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   private val (toMSHR, fromMSHR) = (io.mshr.req, io.mshr.resp)
   private val (toPMP, fromPMP)   = (io.pmp.map(_.req), io.pmp.map(_.resp))
   private val fromWayLookup      = io.wayLookupRead
-  private val csr_parity_enable =
-    if (ICacheForceMetaECCError || ICacheForceDataECCError) true.B else io.csr_parity_enable
+  private val ecc_enable =
+    if (ICacheForceMetaECCError || ICacheForceDataECCError) true.B else io.ecc_enable
 
   // Statistics on the frequency distribution of FTQ fire interval
   private val cntFtqFireInterval      = RegInit(0.U(32.W))
@@ -263,7 +263,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
       hit_num > 1.U                                        // hit multi-way, must be an ECC failure
   })
   // force clear meta_corrupt when parity check is disabled
-  when(!csr_parity_enable) {
+  when(!ecc_enable) {
     s1_meta_corrupt := VecInit(Seq.fill(PortNumber)(false.B))
   }
 
@@ -383,7 +383,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
     }.reduce(_ || _) && s2_SRAMhits(port)
   })
   // force clear data_corrupt when parity check is disabled
-  when(!csr_parity_enable) {
+  when(!ecc_enable) {
     s2_data_corrupt := VecInit(Seq.fill(PortNumber)(false.B))
   }
   // meta error is checked in s1 stage
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
index 718c972c8e3..030d738018a 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
@@ -1,5 +1,6 @@
 /***************************************************************************************
-* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
 * Copyright (c) 2020-2021 Peng Cheng Laboratory
 *
 * XiangShan is licensed under Mulan PSL v2.
@@ -381,13 +382,15 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
     tag = getPhyTagFromBlk(mshr_resp.bits.blkPaddr),
     idx = mshr_resp.bits.vSetIdx,
     waymask = waymask,
-    bankIdx = mshr_resp.bits.vSetIdx(0)
+    bankIdx = mshr_resp.bits.vSetIdx(0),
+    poison = false.B
   )
   io.data_write.bits.generate(
     data = respDataReg.asUInt,
     idx = mshr_resp.bits.vSetIdx,
     waymask = waymask,
-    bankIdx = mshr_resp.bits.vSetIdx(0)
+    bankIdx = mshr_resp.bits.vSetIdx(0),
+    poison = false.B
   )
 
   io.meta_write.valid := write_sram_valid
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 42bc036248f..d0c3dd2144f 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -1,18 +1,19 @@
 /***************************************************************************************
-  * Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
-  * Copyright (c) 2020-2021 Peng Cheng Laboratory
-  *
-  * XiangShan is licensed under Mulan PSL v2.
-  * You can use this software according to the terms and conditions of the Mulan PSL v2.
-  * You may obtain a copy of Mulan PSL v2 at:
-  *          http://license.coscl.org.cn/MulanPSL2
-  *
-  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-  * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-  * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-  *
-  * See the Mulan PSL v2 for more details.
-  ***************************************************************************************/
+* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2020-2021 Peng Cheng Laboratory
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http://license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
 
 package xiangshan.frontend.icache
 
@@ -54,9 +55,9 @@ class IPrefetchReq(implicit p: Parameters) extends IPrefetchBundle {
 
 class IPrefetchIO(implicit p: Parameters) extends IPrefetchBundle {
   // control
-  val csr_pf_enable:     Bool = Input(Bool())
-  val csr_parity_enable: Bool = Input(Bool())
-  val flush:             Bool = Input(Bool())
+  val csr_pf_enable: Bool = Input(Bool())
+  val ecc_enable:    Bool = Input(Bool())
+  val flush:         Bool = Input(Bool())
 
   val req:            DecoupledIO[IPrefetchReq]  = Flipped(Decoupled(new IPrefetchReq))
   val flushFromBpu:   BpuFlushInfo               = Flipped(new BpuFlushInfo)
@@ -69,7 +70,7 @@ class IPrefetchIO(implicit p: Parameters) extends IPrefetchBundle {
   val wayLookupWrite: DecoupledIO[WayLookupInfo] = DecoupledIO(new WayLookupInfo)
 }
 
-class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
+class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICacheECCHelper {
   val io: IPrefetchIO = IO(new IPrefetchIO)
 
   private val (toITLB, fromITLB) = (io.itlb.map(_.req), io.itlb.map(_.resp))
@@ -505,7 +506,7 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
 //  })
 //
 //  // generate exception
-//  val s2_meta_exception = VecInit(s2_meta_corrupt.map(ExceptionType.fromECC(io.csr_parity_enable, _)))
+//  val s2_meta_exception = VecInit(s2_meta_corrupt.map(ExceptionType.fromECC(io.ecc_enable, _)))
 //
 //  // merge meta exception and itlb/pmp exception
 //  val s2_exception = ExceptionType.merge(s2_exception_in, s2_meta_exception)
diff --git a/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala b/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
index b0351a6f54f..2fc9bfadacb 100644
--- a/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
@@ -1,5 +1,6 @@
 /***************************************************************************************
-* Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
 * Copyright (c) 2020-2021 Peng Cheng Laboratory
 *
 * XiangShan is licensed under Mulan PSL v2.
diff --git a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
index 3f0bf7a15c5..cfd4439e0c0 100644
--- a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
@@ -1,18 +1,19 @@
 /***************************************************************************************
-  * Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
-  * Copyright (c) 2020-2021 Peng Cheng Laboratory
-  *
-  * XiangShan is licensed under Mulan PSL v2.
-  * You can use this software according to the terms and conditions of the Mulan PSL v2.
-  * You may obtain a copy of Mulan PSL v2 at:
-  *          http://license.coscl.org.cn/MulanPSL2
-  *
-  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
-  * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
-  * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
-  *
-  * See the Mulan PSL v2 for more details.
-  ***************************************************************************************/
+* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
+* Copyright (c) 2020-2024 Institute of Computing Technology, Chinese Academy of Sciences
+* Copyright (c) 2020-2021 Peng Cheng Laboratory
+*
+* XiangShan is licensed under Mulan PSL v2.
+* You can use this software according to the terms and conditions of the Mulan PSL v2.
+* You may obtain a copy of Mulan PSL v2 at:
+*          http://license.coscl.org.cn/MulanPSL2
+*
+* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+*
+* See the Mulan PSL v2 for more details.
+***************************************************************************************/
 
 package xiangshan.frontend.icache
 
@@ -66,7 +67,7 @@ class WayLookupInterface(implicit p: Parameters) extends ICacheBundle {
   val update: Valid[ICacheMissResp]      = Flipped(ValidIO(new ICacheMissResp))
 }
 
-class WayLookup(implicit p: Parameters) extends ICacheModule {
+class WayLookup(implicit p: Parameters) extends ICacheModule with HasICacheECCHelper {
   val io: WayLookupInterface = IO(new WayLookupInterface)
 
   class WayLookupPtr extends CircularQueuePtr[WayLookupPtr](nWayLookupSize)
```
