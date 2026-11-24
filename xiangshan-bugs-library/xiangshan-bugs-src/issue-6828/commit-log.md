# Commit Log
- Issue: #6828
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6828
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6686
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6686
- Changed files: 18
- Additions: 648
- Deletions: 259

## Files
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/decode/isa/Extensions.scala`
- `src/main/scala/xiangshan/backend/decode/isa/Instructions.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/CanRobCompressField.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/PrivExceptionCauseField.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/InstPattern/InstPattern.scala`
- `src/main/scala/xiangshan/backend/vector/Decoder/Uop/ScalaUopTable.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TLBMissQueue.scala`
- `src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUConst.scala`
- `src/main/scala/xiangshan/cache/mmu/MptChecker.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`
- `src/main/scala/xiangshan/cache/mmu/Repeater.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 3d3f2d4f5b9..7a4a148b0bc 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -204,6 +204,7 @@ case class XSCoreParameters
   HasCMO: Boolean = true,
   MMUAsidLen: Int = 16, // max is 16, 0 is not supported now
   MMUVmidLen: Int = 14,
+  MMUSdidLen: Int = 6,
   ReSelectLen: Int = 7, // load replay queue replay select counter len
   iwpuParameters: WPUParameters = WPUParameters(
     enWPU = false,
@@ -863,6 +864,7 @@ trait HasXSParameter {
   def Enable3Load3Store = (LoadPipelineWidth == 3 && StorePipelineWidth == 3)
   def asidLen = coreParams.MMUAsidLen
   def vmidLen = coreParams.MMUVmidLen
+  def sdidLen = coreParams.MMUSdidLen
   def BTLBWidth = coreParams.LoadPipelineWidth + coreParams.StorePipelineWidth
   def refillBothTlb = coreParams.refillBothTlb
   def iwpuParam = coreParams.iwpuParameters
diff --git a/src/main/scala/xiangshan/backend/decode/isa/Extensions.scala b/src/main/scala/xiangshan/backend/decode/isa/Extensions.scala
index 5b22aecb6fc..42b9477055d 100644
--- a/src/main/scala/xiangshan/backend/decode/isa/Extensions.scala
+++ b/src/main/scala/xiangshan/backend/decode/isa/Extensions.scala
@@ -91,6 +91,8 @@ object Extensions {
 
   case object S extends PrivExt(Seq(SType), tableS)
 
+  case object Mpt extends PrivExt(Seq(MPTType), tableMpt)
+
   case object ZacasZabha extends CombExt[Zacas.type, Zabha.type] {
     override val types: Seq[InstType] = Seq(ZABHA_ZACASType)
     override val table: Map[BitPat, Seq[Opcode]] = tableZabhaZacas
@@ -161,7 +163,7 @@ object Extensions {
 
   def extensions(implicit p: Parameters): Seq[ExtBase] = Seq(
     I, M, A, F, D, Zicsr,
-    Sdext, Smrnmi, System, S, Svinval,
+    Sdext, Smrnmi, System, S, Svinval, Mpt,
     Za64rs, Zabha, Zacas, ZacasZabha, Zawrs,
     Zba, Zbb, Zbc, Zbs, Zbkb, Zbkc, Zbkx,
     V, H,
diff --git a/src/main/scala/xiangshan/backend/decode/isa/Instructions.scala b/src/main/scala/xiangshan/backend/decode/isa/Instructions.scala
index ac14d51de44..52477fa4625 100644
--- a/src/main/scala/xiangshan/backend/decode/isa/Instructions.scala
+++ b/src/main/scala/xiangshan/backend/decode/isa/Instructions.scala
@@ -236,6 +236,8 @@ object Instructions {
   def FDIV_Q             :BitPat = BitPat("b0001111??????????????????1010011")
   def FDIV_S             :BitPat = BitPat("b0001100??????????????????1010011")
   def FENCE              :BitPat = BitPat("b?????????????????000?????0001111")
+  def MFENCE_PA          :BitPat = BitPat("b1011111??????????000000001110011") // HasMptCheck
+  def MINVAL_PA          :BitPat = BitPat("b0111111??????????000000001110011") // HasMptCheck
   def FENCE_I            :BitPat = BitPat("b?????????????????001?????0001111")
   def FEQ_D              :BitPat = BitPat("b1010001??????????010?????1010011")
   def FEQ_H              :BitPat = BitPat("b1010010??????????010?????1010011")
@@ -1609,6 +1611,16 @@ object Instructions {
     )
   }
 
+  object MPTType extends InstType {
+    def MFENCE_PA          = outer.MFENCE_PA
+    def MINVAL_PA          = outer.MINVAL_PA
+
+    val allWithNames = withNameSeq(
+      MFENCE_PA,
+      MINVAL_PA,
+    )
+  }
+
   object MType extends InstType {
     def DIV                = outer.DIV
     def DIVU               = outer.DIVU
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/CanRobCompressField.scala b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/CanRobCompressField.scala
index 37cbebca8ad..6200a46431f 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/CanRobCompressField.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/CanRobCompressField.scala
@@ -33,7 +33,7 @@ object CanRobCompressField extends BoolDecodeField[InstPattern] {
     CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI,
     EBREAK, ECALL,
     SRET, MRET, MNRET, DRET, WFI,
-    SFENCE_VMA, FENCE_I, FENCE, MFENCE, PAUSE,
+    SFENCE_VMA, MFENCE_PA, MINVAL_PA, FENCE_I, FENCE, PAUSE,
     WRS_NTO, WRS_STO,
     AMOADD_D, AMOXOR_D, AMOSWAP_D, AMOAND_D, AMOOR_D, AMOMIN_D, AMOMAX_D, AMOMINU_D, AMOMAXU_D, AMOCAS_D,
     AMOADD_W, AMOXOR_W, AMOSWAP_W, AMOAND_W, AMOOR_W, AMOMIN_W, AMOMAX_W, AMOMINU_W, AMOMAXU_W, AMOCAS_W,
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/PrivExceptionCauseField.scala b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/PrivExceptionCauseField.scala
index b464ba89171..0a1c5ee7cce 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/PrivExceptionCauseField.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/SimpleDecodeChannel/PrivExceptionCauseField.scala
@@ -22,7 +22,7 @@ object PrivExceptionCauseField extends DecodeField[InstPattern, PrivExceptionCau
       case _: SfenceOtherInstPattern      => PrivExceptionCause.sfencePart
       case _: HfenceGVMAInstPattern       => PrivExceptionCause.hfenceGVMA
       case _: HfenceVVMAInstPattern       => PrivExceptionCause.hfenceVVMA
-      case _: MfenceInstPattern           => PrivExceptionCause.mfence
+      case _: MptFenceInstPattern         => PrivExceptionCause.mfence
       case _: WaitForInterruptInstPattern => PrivExceptionCause.wfi
       case _: ZawrsNtoPattern             => PrivExceptionCause.wrsNto
       case _: HyperLoadInstPattern        => PrivExceptionCause.hlsv
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/InstPattern/InstPattern.scala b/src/main/scala/xiangshan/backend/vector/Decoder/InstPattern/InstPattern.scala
index 711fe82e796..1c9e00c276a 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/InstPattern/InstPattern.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/InstPattern/InstPattern.scala
@@ -127,7 +127,7 @@ case class HfenceGVMAInstPattern()(implicit rawInst: BitPat) extends SystemInstP
 
 case class HfenceVVMAInstPattern()(implicit rawInst: BitPat) extends SystemInstPattern
 
-case class MfenceInstPattern()(implicit rawInst: BitPat) extends SystemInstPattern
+case class MptFenceInstPattern()(implicit rawInst: BitPat) extends SystemInstPattern
 
 case class WaitForInterruptInstPattern()(implicit rawInst: BitPat) extends SystemInstPattern
 
@@ -463,7 +463,7 @@ object InstPattern {
               case s if rawStringMatches(s, "0001001?????") || rawStringMatches(s, "0001011?????") => SfenceVMAInstPattern()
               case s if rawStringMatches(s, "0110001?????") || rawStringMatches(s, "0110011?????") => HfenceGVMAInstPattern()
               case s if rawStringMatches(s, "0010001?????") || rawStringMatches(s, "0010011?????") => HfenceVVMAInstPattern()
-              case s if rawStringMatches(s, "1011111?????") => MfenceInstPattern()
+              case s if rawStringMatches(s, "1011111?????") || rawStringMatches(s, "0111111?????") => MptFenceInstPattern()
               case _ => PrivInstPattern()
             }
           case "100" =>
diff --git a/src/main/scala/xiangshan/backend/vector/Decoder/Uop/ScalaUopTable.scala b/src/main/scala/xiangshan/backend/vector/Decoder/Uop/ScalaUopTable.scala
index 1902f214679..c0909d9db97 100644
--- a/src/main/scala/xiangshan/backend/vector/Decoder/Uop/ScalaUopTable.scala
+++ b/src/main/scala/xiangshan/backend/vector/Decoder/Uop/ScalaUopTable.scala
@@ -390,6 +390,16 @@ object ScalaUopTable {
     )
   }
 
+  val tableMpt = {
+    import xiangshan.backend.decode.isa.Instructions.MPTType
+    import xiangshan.backend.decode.opcode.OpcodeTraits.FlushPipe
+
+    MPTType.mapUopcode(
+      _.MFENCE_PA -> mfence,
+      _.MINVAL_PA -> mfence.copy().removeTraits(FlushPipe),
+    )
+  }
+
   val tableH = {
     import xiangshan.backend.decode.isa.Instructions.{H64Type, HType}
 
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index 504e8389660..498ec117889 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -1190,7 +1190,8 @@ class FakePTW()(implicit p: Parameters) extends XSModule with HasPtwConst {
     io.csr.tlb.satp.changed ||
     io.csr.tlb.vsatp.changed ||
     io.csr.tlb.hgatp.changed ||
-    io.csr.tlb.priv.virt_changed
+    io.csr.tlb.priv.virt_changed ||
+    (if (HasMptCheck) io.csr.tlb.mmpt.changed else false.B)
   flush(0) := DelayN(flushCond, itlbParams.fenceDelay)
   flush(1) := DelayN(flushCond, ldtlbParams.fenceDelay)
 
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLBMissQueue.scala b/src/main/scala/xiangshan/cache/mmu/L2TLBMissQueue.scala
index b01faf4a358..7f50e96dfcd 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLBMissQueue.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLBMissQueue.scala
@@ -41,5 +41,7 @@ class L2TlbMissQueue(implicit p: Parameters) extends XSModule with HasPtwConst {
   require(MissQueueSize >= (l2tlbParams.ifilterSize + l2tlbParams.dfilterSize))
   val io = IO(new L2TlbMQIO())
 
-  io.out <> Queue(io.in, MissQueueSize, flush = Some(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed))
+  io.out <> Queue(io.in, MissQueueSize, flush = Some(io.sfence.valid || io.csr.satp.changed ||
+    io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed ||
+    (if (HasMptCheck) io.csr.mmpt.changed else false.B)))
 }
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala b/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
index 38a5199fc0e..f21de3b1691 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
@@ -41,7 +41,8 @@ class L2TlbPrefetch(implicit p: Parameters) extends XSModule with HasPtwConst {
     Cat(old_reqs.zip(old_v).map{ case (o,v) => dup(o,vpn) && v}).orR
   }
 
-  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed
+  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed ||
+    io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B)
   val next_line = get_next_line(io.in.bits.vpn)
   val next_req = RegEnable(next_line, io.in.valid)
   val input_valid = io.in.valid && !flush && !already_have(next_line)
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 3398eeafbb6..be35730758f 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -229,11 +229,12 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
   val pteidx = Vec(tlbcontiguous, Bool())
   val ppn_low = Vec(tlbcontiguous, UInt(sectortlbwidth.W))
 
-  val mptperm = Option.when(HasMptCheck) (new MptPermBundle(isTlbPort = false))
+  // Keep MPT AF until a lookup consumes the faulting entry.
+  val mptperm = Option.when(HasMptCheck) (new MptPermBundle)
   val g_perm = new TlbPermBundle
   val vmid = UInt(vmidLen.W)
   val s2xlate = UInt(2.W)
-
+  val sdid = Option.when(HasMptCheck) (UInt(sdidLen.W))
 
   /** level usage:
    *  !PageSuper: page is only normal, level is None, match all the tag
@@ -245,13 +246,13 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
    *  bits1  0: need mid 9bits
    */
 
-  def hit(vpn: UInt, asid: UInt, nSets: Int = 1, ignoreAsid: Boolean = false, vmid: UInt, hasS2xlate: Bool, onlyS2: Bool = false.B, onlyS1: Bool = false.B): Bool = {
+  def hit(vpn: UInt, asid: UInt, nSets: Int = 1, ignoreAsid: Boolean = false, vmid: UInt, sdid: UInt, matchMpt: Bool, hasS2xlate: Bool, onlyS2: Bool = false.B, onlyS1: Bool = false.B): Bool = {
     val asid_hit = Mux(hasS2xlate && onlyS2, true.B, if (ignoreAsid) true.B else (this.asid === asid || perm.g))
     val addr_low_hit = valididx(vpn(2, 0))
     val vmid_hit = Mux(hasS2xlate, this.vmid === vmid, true.B)
     val isPageSuper = !(level.getOrElse(0.U) === 0.U)
     val pteidx_hit = Mux(hasS2xlate && !isPageSuper && !onlyS1 && n === 0.U, pteidx(vpn(2, 0)), true.B)
-
+    val sdid_hit = if(HasMptCheck) (Mux(matchMpt, this.sdid.get === sdid, true.B)) else true.B
     val tmp_level = level.get
     val tag_matchs = Wire(Vec(Level + 1, Bool()))
     tag_matchs(0) := Mux(n === 0.U, tag(vpnnLen - sectortlbwidth - 1, 0) === vpn(vpnnLen - 1, sectortlbwidth), tag(vpnnLen - sectortlbwidth - 1, pteNapotBits - sectortlbwidth) === vpn(vpnnLen - 1, pteNapotBits))
@@ -265,10 +266,10 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     }
     level_matchs(Level) := tag_matchs(Level)
 
-    asid_hit && level_matchs.asUInt.andR && addr_low_hit && vmid_hit && pteidx_hit
+    asid_hit && level_matchs.asUInt.andR && addr_low_hit && vmid_hit && pteidx_hit && sdid_hit
   }
 
-  def wbhit(data: PtwRespS2, asid: UInt, vmid: UInt, nSets: Int = 1, ignoreAsid: Boolean = false, s2xlate: UInt): Bool = {
+  def wbhit(data: PtwRespS2, asid: UInt, vmid: UInt, nSets: Int = 1, ignoreAsid: Boolean = false, s2xlate: UInt, sdid: UInt = 0.U, matchMpt: Bool = false.B): Bool = {
     val s1vpn = data.s1.entry.tag
     val s2vpn = data.s2.entry.tag(vpnLen - 1, sectortlbwidth)
     val wb_vpn = Mux(s2xlate === onlyStage2, s2vpn, s1vpn)
@@ -281,6 +282,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     val index_hit = Wire(Vec(tlbcontiguous, Bool()))
     val wb_valididx = Wire(Vec(tlbcontiguous, Bool()))
     val vmid_hit = Mux(hasS2xlate, this.vmid === vmid, true.B)
+    val sdid_hit = if (HasMptCheck) Mux(matchMpt, this.sdid.get === sdid, true.B) else true.B
     val pteidx_hit = MuxCase(true.B, Seq(
       onlyS2 -> (VecInit(UIntToOH(data.s2.entry.tag(sectortlbwidth - 1, 0))).asUInt === pteidx.asUInt),
       hasS2xlate -> (pteidx.asUInt === data.s1.pteidx.asUInt)
@@ -314,7 +316,7 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
     // Now 0x10 and 0x13 are both valid in page cache
     // However, when 0x13 refill to tlb, will trigger multi hit
     // So will only trigger multi-hit when PopCount(data.valididx) = 1
-    vpn_hit && index_hit.reduce(_ || _) && PopCount(wb_valididx) === 1.U && s2xlate_hit && pteidx_hit
+    vpn_hit && index_hit.reduce(_ || _) && PopCount(wb_valididx) === 1.U && s2xlate_hit && pteidx_hit && sdid_hit
   }
 
   def apply(item: PtwRespS2): TlbSectorEntry = {
@@ -360,14 +362,17 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
         Mux(s2_exception && !s1_valid, 3.U, merge_level))
     val inner_level = Mux(item.s2xlate =/= allStage, merge_level, allStage_level)
 
-    val mpt_level =Option.when(HasMptCheck) (inner_level min item.mpt.get.mptLevel)
+    val mpt_level = Option.when(HasMptCheck) (inner_level min item.mpt.get.mptLevel)
 
     this.level.map(_ := (if (HasMptCheck) (mpt_level.get) else (inner_level)))
 
     this.perm.apply(item.s1)
     this.pbmt := item.s1.entry.pbmt
-    
-    if (HasMptCheck) {this.mptperm.get.resp_apply(item.mpt.get)}
+
+    if (HasMptCheck) {
+      this.sdid.get := item.mpt.get.sdid
+      this.mptperm.get.resp_apply(item.mpt.get)
+    }
 
     val s1tag = item.s1.entry.tag
     val s2tag = item.s2.entry.tag(gvpnLen - 1, sectortlbwidth)
@@ -378,11 +383,11 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
         3.U -> Cat(item.s1.entry.ppn(sectorppnLen - 1, vpnnLen * 3 - sectortlbwidth), item.s1.entry.tag(vpnnLen * 3 - 1- sectortlbwidth, 0)),
         2.U -> Cat(item.s1.entry.ppn(sectorppnLen - 1, vpnnLen * 2 - sectortlbwidth), item.s1.entry.tag(vpnnLen * 2 - 1 -sectortlbwidth, 0)),
         1.U -> Cat(item.s1.entry.ppn(sectorppnLen - 1, vpnnLen - sectortlbwidth), item.s1.entry.tag(vpnnLen - 1 - sectortlbwidth, 0)),
-        0.U -> Mux(item.s1.entry.n.getOrElse(0.U) === 0.U, item.s1.entry.ppn(sectorppnLen - 1, 0), 
+        0.U -> Mux(item.s1.entry.n.getOrElse(0.U) === 0.U, item.s1.entry.ppn(sectorppnLen - 1, 0),
           Cat(item.s1.entry.ppn(sectorppnLen - 1, pteNapotBits- sectortlbwidth), item.s1.entry.tag(pteNapotBits - 1 - sectortlbwidth, 0)))
         ))
       )
-    val s1ppn_low = if (!HasMptCheck) (item.s1.ppn_low) else (Mux((item.s1.entry.level.getOrElse(0.U) > 0.U) 
+    val s1ppn_low = if (!HasMptCheck) (item.s1.ppn_low) else (Mux((item.s1.entry.level.getOrElse(0.U) > 0.U)
       || item.s1.entry.n.getOrElse(0.U) > 0.U, VecInit(Seq.fill(tlbcontiguous)(item.s1.addr_low)), item.s1.ppn_low))
 
     val s2ppn = MuxLookup(item.s2.entry.level.getOrElse(0.U), item.s2.entry.ppn(ppnLen - 1, sectortlbwidth))(Seq(
@@ -414,14 +419,14 @@ class TlbSectorEntry(pageNormal: Boolean, pageSuper: Boolean)(implicit p: Parame
       allStage -> allStage_n,
       noS2xlate -> item.s1.entry.n.getOrElse(0.U)
     ))
-    
-    val inner_n_mpt = Option.when(HasMptCheck)(mpt_merge_n(inner_level,inner_n,item.mpt.get.mptLevel,item.mpt.get.permIsNAPOT)) 
- 
+
+    val inner_n_mpt = Option.when(HasMptCheck)(mpt_merge_n(inner_level,inner_n,item.mpt.get.mptLevel,item.mpt.get.permIs64KContinuous))
+
     if (HasMptCheck) {this.n :=  inner_n_mpt.get} else {this.n :=  inner_n}
 
     val isSuperPage = if (HasMptCheck) (inner_n_mpt.get =/= 0.U || mpt_level.get =/= 0.U) else (inner_level =/= 0.U || inner_n =/= 0.U)
     this.valididx := Mux(isSuperPage, VecInit(Seq.fill(tlbcontiguous)(true.B)),
-      Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), Mux((if (HasMptCheck) item.mpt.get.contigousPerm else true.B),
+      Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), Mux((if (HasMptCheck) item.mpt.get.permIs32KContinuous else true.B),
       Mux(if (HasMptCheck) (inner_level === 0.U) else true.B, item.s1.valididx,VecInit(Seq.fill(tlbcontiguous)(true.B))),item.s1.pteidx)))
     this.pteidx := Mux(item.s2xlate === onlyStage2, VecInit(UIntToOH(item.s2.entry.tag(sectortlbwidth - 1, 0)).asBools), item.s1.pteidx)
     this.vmid := Mux(item.s2xlate === onlyStage2, item.s2.entry.vmid.getOrElse(0.U), item.s1.entry.vmid.getOrElse(0.U))
@@ -502,6 +507,7 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
     val req = Vec(ports, Flipped(DecoupledIO(new Bundle {
       val vpn = Output(UInt(vpnLen.W))
       val s2xlate = Output(UInt(2.W))
+      val matchMpt = Option.when(HasMptCheck) (Output(Bool()))
     })))
     val resp = Vec(ports, ValidIO(new Bundle{
       val hit = Output(Bool())
@@ -511,7 +517,7 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
       val perm = Vec(nDups, Output(new TlbSectorPermBundle()))
       val g_perm = Vec(nDups, Output(new TlbPermBundle()))
       val s2xlate = Vec(nDups, Output(UInt(2.W)))
-      val mptperm = Option.when(HasMptCheck)(Vec(nDups, Output(new MptPermBundle(isTlbPort = true)))) // add mptperm
+      val mptperm = Option.when(HasMptCheck)(Vec(nDups, Output(new MptPermBundle))) // add mptperm
     }))
   }
   val w = Flipped(ValidIO(new Bundle {
@@ -520,11 +526,13 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
   }))
   val access = Vec(ports, new ReplaceAccessBundle(nSets, nWays))
 
-  def r_req_apply(valid: Bool, vpn: UInt, i: Int, s2xlate:UInt): Unit = {
+  def r_req_apply(valid: Bool, vpn: UInt, i: Int, s2xlate: UInt, matchMpt: Bool = false.B): Unit = {
     this.r.req(i).valid := valid
     this.r.req(i).bits.vpn := vpn
     this.r.req(i).bits.s2xlate := s2xlate
-
+    if (HasMptCheck) {
+      this.r.req(i).bits.matchMpt.get := matchMpt
+    }
   }
 
   def r_resp_apply(i: Int) = {
@@ -539,8 +547,8 @@ class TlbStorageIO(nSets: Int, nWays: Int, ports: Int, nDups: Int = 1)(implicit
 
 }
 
-class MptPermBundle(isTlbPort: Boolean = true) (implicit p: Parameters) extends TlbBundle {
-  val af = Option.when(isTlbPort) (Bool()) // NOTE: if this is true, just raise af
+class MptPermBundle(implicit p: Parameters) extends TlbBundle {
+  val af = Bool() // NOTE: if this is true, just raise af
   val x = Bool()
   val w = Bool()
   val r = Bool()
@@ -549,9 +557,7 @@ class MptPermBundle(isTlbPort: Boolean = true) (implicit p: Parameters) extends
     this.x := mptResp.mptPerm(2) === 1.U
     this.w := mptResp.mptPerm(1) === 1.U
     this.r := mptResp.mptPerm(0) === 1.U
-    if (isTlbPort) (
-      this.af.get := mptResp.accessFault
-    )
+    this.af := mptResp.accessFault
   }
 }
 class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit p: Parameters) extends MMUIOBaseBundle {
@@ -559,6 +565,7 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
     val req = Vec(ports, Flipped(DecoupledIO(new Bundle {
       val vpn = Output(UInt(vpnLen.W))
       val s2xlate = Output(UInt(2.W))
+      val matchMpt = Option.when(HasMptCheck) (Output(Bool()))
     })))
     val resp = Vec(ports, ValidIO(new Bundle{
       val hit = Output(Bool())
@@ -567,7 +574,7 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
       val g_pbmt = Vec(nDups, Output(UInt(ptePbmtLen.W)))
       val perm = Vec(nDups, Output(new TlbPermBundle()))
       val g_perm = Vec(nDups, Output(new TlbPermBundle()))
-      val mptperm = Option.when(HasMptCheck)(Vec(nDups, Output(new MptPermBundle(isTlbPort = true))))
+      val mptperm = Option.when(HasMptCheck)(Vec(nDups, Output(new MptPermBundle)))
       // HasMptCheck,add mptperm
       val s2xlate = Vec(nDups, Output(UInt(2.W)))
     }))
@@ -577,14 +584,17 @@ class TlbStorageWrapperIO(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit
   }))
   val replace = if (q.outReplace) Flipped(new TlbReplaceIO(ports, q)) else null
 
-  def r_req_apply(valid: Bool, vpn: UInt, i: Int, s2xlate: UInt): Unit = {
+  def r_req_apply(valid: Bool, vpn: UInt, i: Int, s2xlate: UInt, matchMpt: Bool = false.B): Unit = {
     this.r.req(i).valid := valid
     this.r.req(i).bits.vpn := vpn
     this.r.req(i).bits.s2xlate := s2xlate
+    if (HasMptCheck) {
+      this.r.req(i).bits.matchMpt.get := matchMpt
+    }
   }
 
   def r_resp_apply(i: Int) = { // HasMptCheck,add mptperm
-    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm, 
+    (this.r.resp(i).bits.hit, this.r.resp(i).bits.ppn, this.r.resp(i).bits.perm, this.r.resp(i).bits.g_perm,
       this.r.resp(i).bits.s2xlate, this.r.resp(i).bits.pbmt, this.r.resp(i).bits.g_pbmt,this.r.resp(i).bits.mptperm)
   }
 
@@ -1279,7 +1289,7 @@ class HptwResp(implicit p: Parameters) extends PtwBundle {
 
     val vmid_hit = this.entry.vmid.getOrElse(0.U) === vmid
 
-    val mptNapot = Option.when(mergeMptLevel) (mpt_merge_n(this.entry.level.get, this.entry.n.getOrElse(0.U), mptTlbResp.mptLevel, mptTlbResp.permIsNAPOT))
+    val mptNapot = Option.when(mergeMptLevel) (mpt_merge_n(this.entry.level.get, this.entry.n.getOrElse(0.U), mptTlbResp.mptLevel, mptTlbResp.permIs64KContinuous))
     val mpt_level = Option.when(mergeMptLevel) (mptTlbResp.mptLevel min this.entry.level.get)
 
     val tag_match = Wire(Vec(Level + 1, Bool()))
@@ -1314,7 +1324,7 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
   def FakeMptOnlyResp(ppn: UInt, asid: UInt, vmid: UInt ) = {
     this.entry.tag := 0.U
     this.entry.tag :=  ppn(ppnLen-1, sectortlbwidth)
-    this.entry.ppn := ppn(ppnLen-1, sectortlbwidth) 
+    this.entry.ppn := ppn(ppnLen-1, sectortlbwidth)
     this.entry.vmid.map(_ := vmid)
     this.entry.asid := asid
     this.entry.level.get := 3.U
@@ -1366,10 +1376,10 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
     require(vpn.getWidth == vpnLen)
     require(allType)
     // require(this.asid.getWidth <= asid.getWidth)
-    val asid_hit = if (HasMptCheck) (Mux(mptIgnoreAsid, true.B, if (ignoreAsid) true.B else (this.entry.asid === asid || this.entry.perm.get.g))) else 
+    val asid_hit = if (HasMptCheck) (Mux(mptIgnoreAsid, true.B, if (ignoreAsid) true.B else (this.entry.asid === asid || this.entry.perm.get.g))) else
       (if (ignoreAsid) true.B else (this.entry.asid === asid || this.entry.perm.get.g))
 
-    val mptNapot = Option.when(mergeMptLevel) (mpt_merge_n(this.entry.level.get, this.entry.n.getOrElse(0.U), mptTlbResp.mptLevel, mptTlbResp.permIsNAPOT))
+    val mptNapot = Option.when(mergeMptLevel) (mpt_merge_n(this.entry.level.get, this.entry.n.getOrElse(0.U), mptTlbResp.mptLevel, mptTlbResp.permIs64KContinuous))
     val mpt_level = Option.when(mergeMptLevel) (mptTlbResp.mptLevel min this.entry.level.get)
 
     val tag_match = Wire(Vec(Level + 1, Bool()))
@@ -1489,8 +1499,18 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
     Mux(s2xlate === onlyStage2, s2_vpn, s1_vpn)
   }
 
-  def hit(vpn: UInt, asid: UInt, vasid: UInt, vmid: UInt, allType: Boolean = false, ignoreAsid: Boolean = false): Bool = {
-    val noS2_hit = s1.hit(vpn, asid, vmid, allType, ignoreAsid, 
+  def hit(
+    vpn: UInt,
+    asid: UInt,
+    vasid: UInt,
+    vmid: UInt,
+    allType: Boolean = false,
+    ignoreAsid: Boolean = false,
+    sdid: UInt = 0.U,
+    matchMpt: Bool = false.B
+  ): Bool = {
+    val sdid_hit = if (HasMptCheck) Mux(matchMpt, this.mpt.get.sdid === sdid, true.B) else true.B
+    val noS2_hit = s1.hit(vpn, asid, vmid, allType, ignoreAsid,
       (if (HasMptCheck) (this.mpt.get.mptOnly) else false.B), HasMptCheck, this.mpt.getOrElse(0.U.asTypeOf(new MptTlbRespBundle)))
     val onlyS2_hit = s2.hit(vpn, vmid, HasMptCheck, this.mpt.getOrElse(0.U.asTypeOf(new MptTlbRespBundle)))
     // allstage and onlys1 hit
@@ -1508,12 +1528,12 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
     val allStage_n = (s1.entry.n.getOrElse(0.U) =/= 0.U && s2.entry.level.getOrElse(0.U) =/= 0.U) ||
       (s2.entry.n.getOrElse(0.U) =/= 0.U && s1.entry.level.getOrElse(0.U) =/= 0.U) ||
       (s1.entry.n.getOrElse(0.U) =/= 0.U && s2.entry.n.getOrElse(0.U) =/= 0.U)
-    
-    val mptNapot = Option.when(HasMptCheck) (mpt_merge_n(level, allStage_n, mpt.get.mptLevel, mpt.get.permIsNAPOT))
+
+    val mptNapot = Option.when(HasMptCheck) (mpt_merge_n(level, allStage_n, mpt.get.mptLevel, mpt.get.permIs64KContinuous))
     val mptNapot_onlys1 = Option.when(HasMptCheck) (
-      mpt_merge_n(s1.entry.level.getOrElse(0.U), s1.entry.n.getOrElse(0.U), mpt.get.mptLevel, mpt.get.permIsNAPOT)
+      mpt_merge_n(s1.entry.level.getOrElse(0.U), s1.entry.n.getOrElse(0.U), mpt.get.mptLevel, mpt.get.permIs64KContinuous)
     )
-    val mpt_level = Option.when(HasMptCheck) (level min mpt.get.mptLevel)  
+    val mpt_level = Option.when(HasMptCheck) (level min mpt.get.mptLevel)
     val n = if (HasMptCheck) (Mux(this.s2xlate === onlyStage1, mptNapot_onlys1.get,mptNapot.get)) else
       (Mux(this.s2xlate === onlyStage1, s1.entry.n.getOrElse(0.U), allStage_n))
 
@@ -1538,7 +1558,7 @@ class PtwRespS2(implicit p: Parameters) extends PtwBundle {
     val vasid_hit = if (ignoreAsid) true.B else (s1.entry.asid === vasid || s1.entry.perm.get.g)
     val all_onlyS1_hit = vpn_hit && vmid_hit && vasid_hit
     Mux(this.s2xlate === noS2xlate, noS2_hit,
-      Mux(this.s2xlate === onlyStage2, onlyS2_hit, all_onlyS1_hit))
+      Mux(this.s2xlate === onlyStage2, onlyS2_hit, all_onlyS1_hit)) && sdid_hit
   }
 }
 
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
index b73076b3524..650377c3929 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
@@ -100,29 +100,33 @@ trait MPTCacheParam extends HasTlbConst {
   val mptOff = 16 // mpt offset of PA
   val smmpt43PAddrBits = 43
   val smmpt52PAddrBits = 52
-  // l3 8T
+  // l3 8 TiB
   val l3Size  = 1
   val mptL3TagLen = 5
   val l3Associative = "fa"
   val l3Replacer = "plru"
-  // l2 4G
+  // l2 16 GiB
   val mptL2TagLen = mptL3TagLen + 9
   val l2Size = 1
   val l2Associative = "fa"
   val l2Replacer = "plru"
-  // l1 32M
+  // l1 32 MiB
   val l1Size = 4
   val mptL1TagLen = mptL3TagLen + 9 * 2
   val l1Associative = "fa"
   val l1Replacer = "plru"
-  // l0 64k
-  val l0nSets = 32 // 5bits
-  val l0nWays = 4
+  // l0 64 KiB
+  val l0nSets = 16 // 5bits
+  val l0nWays = 8
   val mptL0TagLen = mptL3TagLen + 9 * 3 - log2Up(l0nSets) // 32-5=27
   val l0Replacer = "setplru"
   // sp
-  val spSize = 8
-  val mptspTagLen = mptL3TagLen + 9 * 2
+  val spSize = 16
+  // A G=4 MPT NAPOT entry covers 1/16 of the ordinary SP range at the
+  // corresponding effective level.  Keep four additional address bits so
+  // the SP tag can match that narrower range without a second cache format.
+  val mptNapotExtraTagLen = 4
+  val mptspTagLen = mptL1TagLen + mptNapotExtraTagLen
   val spAssociative = "fa"
   val spReplacer = "plru"
 
diff --git a/src/main/scala/xiangshan/cache/mmu/MptChecker.scala b/src/main/scala/xiangshan/cache/mmu/MptChecker.scala
index 84bfeca1d77..de6184e4c81 100644
--- a/src/main/scala/xiangshan/cache/mmu/MptChecker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MptChecker.scala
@@ -40,23 +40,27 @@ class MptTlbRespBundle(implicit p: Parameters) extends XSBundle with MPTCachePar
   val accessFault   = Bool()
   val mptPerm       = UInt(3.W)
   val mptLevel      = UInt(mptLevelLenUInt.W) // UInt level
-  val contigousPerm = Bool()                  // only work for non H l0 pte
-  // indicate continous 8 permission. can not compress as l0pte(8bit valididx)
-  val permIsNAPOT = Bool()
+  // Ordinary L0 MPTE permission compression; independent of MPTE.N.
+  val permIs32KContinuous = Bool()
+  val permIs64KContinuous = Bool()
+  val sdid = UInt(sdidLen.W)
   def genFakeResp(): Unit = {
     this.accessFault   := false.B
     this.mptPerm       := Fill(3, 1.U(1.W))
-    this.contigousPerm := true.B
+    this.permIs32KContinuous := true.B
     this.mptLevel      := 3.U
-    this.permIsNAPOT   := true.B
+    this.permIs64KContinuous := true.B
+    this.sdid          := 0.U
+    this.mptOnly       := false.B
   }
   def applyMptc2TlbResp(childBundle: MptRespBundle): Unit = {
     this.accessFault   := childBundle.accessFault
     this.mptPerm       := childBundle.mptPerm
-    this.contigousPerm := childBundle.contigousPerm
+    this.permIs32KContinuous := childBundle.permIs32KContinuous
     this.mptLevel      := childBundle.mptLevel
-    this.permIsNAPOT   := childBundle.permIsNAPOT
+    this.permIs64KContinuous := childBundle.permIs64KContinuous
     this.mptOnly       := childBundle.mptOnly
+    this.sdid          := childBundle.sdid
   }
 }
 class MptRespBundle(implicit p: Parameters) extends MptTlbRespBundle with MPTCacheParam { // mptc
@@ -85,7 +89,6 @@ class MptOutputSwitchBox(implicit p: Parameters) extends XSModule with MPTCacheP
 
   val mptOutDataWire = Wire(new MptTlbRespBundle())
   mptOutDataWire.applyMptc2TlbResp(io.mptOut.bits)
-
   val mptOutDataReg = Reg(new MptTlbRespBundle())
   val mptOutMptOnly = RegInit(false.B)
   val mptOutReqPA   = Reg(UInt(ppnLen.W))
@@ -245,7 +248,9 @@ class PLRUOHSet(setsLog2: Int, logWays: Int) extends Module {
 class MptData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val data = UInt(perms16Len.W)
   def apply(data: UInt): Unit = this.data := data // zero extended
-  def getPPN: UInt = this.data(ppnLen - 1, 0)
+  // MptData holds MPTE[55:8], so the implemented low ppnLen bits of
+  // MPTE.PPN[53:10] are data[ppnLen + 1:2].
+  def getPPN: UInt = this.data(ppnLen + 1, 2)
   def getAddr(offset: UInt): UInt = Cat(this.getPPN, Cat(offset, 0.U(3.W))) // 2|3 byte = 64bit
   def extractPerm(select: UInt): (UInt) = (this.data >> (select * 3.U))(2, 0) // extract XWR using 4bit offset
   // cal start end and extract
@@ -254,13 +259,15 @@ class MptData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
 class MptEntry(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val N    = Bool()
   val data = new MptData()
+  val g    = UInt(4.W)
   val L    = Bool()
   val V    = Bool()
   def apply(sMEMResp: UInt): Unit = {
-    this.V := (sMEMResp(0) === 1.U)
-    this.L := (sMEMResp(1) === 1.U)
-    this.N := (sMEMResp(63) === 1.U)
-    this.data.apply(sMEMResp(57, 10)) // xiangshan only support 48bit PA, so PPN only needs 36
+    this.V := sMEMResp(0)
+    this.L := sMEMResp(1)
+    this.N := sMEMResp(2)
+    this.g := sMEMResp(15, 12)
+    this.data.apply(sMEMResp(55, 8))
   }
   def isValid: Bool =
     this.V
@@ -274,19 +281,28 @@ class MptEntry(implicit p: Parameters) extends XSBundle with MPTCacheParam {
 
 class MptCacheTag(tagLen: Int, isSp: Boolean = false)(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val tag   = UInt(tagLen.W)
+  val sdid = UInt(sdidLen.W)
   val level = Option.when(isSp)(UInt((mptLevelLenOH - 1).W)) // sp can not be l0
+  val n = Option.when(isSp)(Bool())
   def hit(ppn: UInt): Bool =
     tag === ppn(ppnLen - 1, ppnLen - tagLen) // tag = 5, (47,43)
+  def hitSdid(sdid: UInt): Bool =
+    this.sdid === sdid
   def hitSp(ppn: UInt): Bool = {
-    val hitL3 = this.tag(tagLen - 1, tagLen - mptL3TagLen) === ppn(ppnLen - 1, ppnLen - mptL3TagLen) // tag = 5, (47,43)
-    val hitL2 = this.tag(tagLen - 1, tagLen - mptL2TagLen) === ppn(ppnLen - 1, ppnLen - mptL2TagLen)
-    val hitL1 = this.tag === ppn(ppnLen - 1, ppnLen - tagLen)
-    val hotVal = Mux1H(Seq(
-      this.level.get(2) -> hitL3,
-      this.level.get(1) -> hitL2,
-      this.level.get(0) -> hitL1
+    def prefixHit(width: Int): Bool =
+      this.tag(tagLen - 1, tagLen - width) === ppn(ppnLen - 1, ppnLen - width)
+
+    val normalHit = Mux1H(Seq(
+      this.level.get(2) -> prefixHit(mptL3TagLen),
+      this.level.get(1) -> prefixHit(mptL2TagLen),
+      this.level.get(0) -> prefixHit(mptL1TagLen)
+    ))
+    val napotHit = Mux1H(Seq(
+      this.level.get(2) -> prefixHit(mptL3TagLen + mptNapotExtraTagLen),
+      this.level.get(1) -> prefixHit(mptL2TagLen + mptNapotExtraTagLen),
+      this.level.get(0) -> prefixHit(mptL1TagLen + mptNapotExtraTagLen)
     )) // it is a tuple scala> 1 -> 2 res0: (Int, Int) = (1,2)
-    hotVal
+    Mux(this.n.get, napotHit, normalHit)
   }
 }
 class MptCacheData(isPerms: Boolean = false)(implicit p: Parameters) extends XSBundle with MPTCacheParam {
@@ -294,10 +310,8 @@ class MptCacheData(isPerms: Boolean = false)(implicit p: Parameters) extends XSB
   def extractPerm(select: UInt): (UInt) = { // extract XWR using 4bit offset
     // cal start end and extract
     require(isPerms, "extractPerm is only valid when isPerms is true")
-    (this.data >> (select * 3.U))(
-      2,
-      0
-    ) // not quite sure what kind of crap will be synthesized. I meant it to be a binary mux
+    (this.data >> (select * 3.U))(2,0)
+    // not quite sure what kind of crap will be synthesized. I meant it to be a binary mux
   }
 }
 
@@ -310,19 +324,23 @@ class MptCacheReq(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val mptOnly = Bool()
   val reqPA   = UInt(ppnLen.W)
   val source  = UInt(mptSourceWidth.W)
+  val sdid    = UInt(sdidLen.W)
 }
 
 // create pipe with reset
 class MPTPipe(implicit p: Parameters) extends MptCacheReq {
   val dataValid  = Bool()
   val flushCache = Bool()
+  val flushSdid  = Bool()
 
   def applySplitData(MPTPipeControl: MPTPipeControl, MPTPipeData: MPTPipeData): Unit = {
     this.dataValid  := MPTPipeControl.dataValid
     this.flushCache := MPTPipeControl.flushCache
+    this.flushSdid  := MPTPipeControl.flushSdid
     this.mptOnly    := MPTPipeControl.mptOnly
     this.reqPA      := MPTPipeData.reqPA
     this.source     := MPTPipeData.source
+    this.sdid       := MPTPipeData.sdid
   }
 
   def createSplitData(): (MPTPipeControl, MPTPipeData) = {
@@ -330,9 +348,11 @@ class MPTPipe(implicit p: Parameters) extends MptCacheReq {
     val mptPipeData    = Wire(new MPTPipeData)
     mptPipeData.reqPA  := this.reqPA
     mptPipeData.source := this.source
+    mptPipeData.sdid   := this.sdid
 
     mptPipeControl.dataValid  := this.dataValid
     mptPipeControl.flushCache := this.flushCache
+    mptPipeControl.flushSdid  := this.flushSdid
     mptPipeControl.mptOnly    := this.mptOnly
     (mptPipeControl, mptPipeData)
   }
@@ -341,10 +361,12 @@ class MPTPipe(implicit p: Parameters) extends MptCacheReq {
 class MPTPipeControl(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val dataValid  = Bool()
   val flushCache = Bool()
+  val flushSdid  = Bool()
   val mptOnly    = Bool()
   def applyPipeData(mptPipe: MPTPipe): Unit = {
     this.dataValid  := mptPipe.dataValid
     this.flushCache := mptPipe.flushCache
+    this.flushSdid  := mptPipe.flushSdid
     this.mptOnly    := mptPipe.mptOnly
   }
 }
@@ -352,9 +374,11 @@ class MPTPipeControl(implicit p: Parameters) extends XSBundle with MPTCacheParam
 class MPTPipeData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val reqPA  = UInt(ppnLen.W)
   val source = UInt(mptSourceWidth.W)
+  val sdid   = UInt(sdidLen.W)
   def applyPipeData(MPTPipe: MPTPipe): Unit = {
     this.reqPA  := MPTPipe.reqPA
     this.source := MPTPipe.source
+    this.sdid   := MPTPipe.sdid
   }
 }
 
@@ -376,12 +400,12 @@ object MPTPipeWithReset {
     } else {
       val v = RegNext(enqValid, false.B) // valid has reset
       val (mptPipeControlIn, mptPipeDataIn) = enqBits.createSplitData() // split input as data and control
-      val mptPipeData = RegEnable(mptPipeDataIn, (mptPipeControlIn.dataValid || mptPipeControlIn.flushCache) && enqValid)
+      val mptPipeData = RegEnable(mptPipeDataIn, (mptPipeControlIn.dataValid || mptPipeControlIn.flushCache || mptPipeControlIn.flushSdid) && enqValid)
       // data has no reset
       val mptPipeControl = RegEnable(mptPipeControlIn, 0.U.asTypeOf(mptPipeControlIn), enqValid) // control has reset
       val b              = Wire(chiselTypeOf(enqBits))
-      // kill ordinary in-flight requests on context-changing flush, but keep
-      // fence flushCache requests alive.
+      // Kill ordinary in-flight requests on context-changing flush, but keep
+      // fence cache invalidation requests alive.
       when(clearData) {
         mptPipeControl.dataValid := false.B
         mptPipeControl.mptOnly   := false.B
@@ -402,6 +426,9 @@ class RefillBundle(implicit p: Parameters) extends XSBundle with MPTCacheParam {
   val refillData = new MptData()
   val isAf       = Bool()
   val isLeafMpte = Bool() // is leaf? decide what cache is refilled
+  // Internal MPT-cache metadata.  The TLB only receives the effective level.
+  val mpteNapot  = Bool()
+  def useNapot: Bool = mpteNapot && !isAf
 }
 class MPTCacheIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCacheParam {
   val req = Flipped(DecoupledIO(new MptCacheReq()))
@@ -409,12 +436,13 @@ class MPTCacheIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCachePa
   val respHit = ValidIO(new Bundle { // source is waiting for cache to resp
     val accessFault      = Bool()
     val perm             = UInt(3.W)
-    val tlbContigousPerm = Bool()
-    val permIsNAPOT      = Bool()
+    val permIs32KContinuous = Bool()
+    val permIs64KContinuous = Bool()
     val source           = UInt(mptSourceWidth.W)
     val mptLevel         = UInt(log2Up(mptLevelLenOH).W)
     val mptOnly          = Bool()
     val reqPA            = UInt(ppnLen.W)
+    val sdid             = UInt(sdidLen.W)
   })
 
   val respMiss = DecoupledIO(new Bundle {
@@ -423,6 +451,7 @@ class MPTCacheIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCachePa
     val source   = UInt(mptSourceWidth.W)
     val pa       = UInt(ppnLen.W)
     val mptOnly  = Bool()
+    val sdid     = UInt(sdidLen.W)
   })
 
   val refill = Flipped(ValidIO(new RefillBundle()))
@@ -431,24 +460,17 @@ class MPTCacheIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCachePa
 
 class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   val io = IO(new MPTCacheIO)
-  // // mfence signal
-  val mfenceActive = WireInit(false.B)
-  val fencePA      = WireInit(false.B)
-  val mfencevalid  = io.sfence.valid && io.sfence.bits.mfence.get
-  val rs1IsX0      = io.sfence.bits.rs1
-  val rs2IsX0      = io.sfence.bits.rs2
-  val sdidMatches  = io.sfence.bits.id === io.csr.mmpt.sdid
-  // This MPT design supports partial cache flushing by PA. When flushing, in addition to leaf nodes, intermediate nodes are also invalidated
-  when(mfencevalid) {
-    when(rs1IsX0) {
-      mfenceActive := rs2IsX0 || sdidMatches
-    }.otherwise {
-      fencePA := rs2IsX0 || sdidMatches
-    }
-  }
-  val flushAll      = mfenceActive || io.csr.mmpt.changed
+  // mfence signal
+  val mfencevalid    = io.sfence.valid && io.sfence.bits.mfence.get
+  val rs1IsX0        = io.sfence.bits.rs1
+  val rs2IsX0        = io.sfence.bits.rs2
+  val mfenceHasPA    = mfencevalid && !rs1IsX0
+  val mfenceHasSDID  = mfencevalid && !rs2IsX0
+  val mfenceFenceAll = mfencevalid && rs1IsX0 && rs2IsX0
+  val fenceSDIDReg    = io.sfence.bits.id(sdidLen - 1, 0)
   val flushPipeOnly = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed ||
     io.csr.priv.virt_changed || io.csr.mmpt.changed
+  // This MPT design supports partial cache flushing by PA. When flushing, in addition to leaf nodes, intermediate nodes are also invalidated.
 
   val pipeFlowEn = Wire(Bool())
   val refilling     = Wire(Bool())
@@ -463,16 +485,19 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
 
   refilling := refillCounter > 0.U // is refiill state when counter ! = 0 for 4 rounds
   val respHitRegTmp = Wire(Bool()) // resphitreg is defined later
-  pipeFlowEn := (io.respMiss.ready || respHitRegTmp) || refilling || fencePA
+  pipeFlowEn := (io.respMiss.ready || respHitRegTmp) || refilling || io.sfence.valid
+  // during the sfence valid, the pipe is flushed. pipe is empty and can recieve the fence req(if there is one)
   // Without respHitReg, if the final stage hits and stalls,
   // the hit control signal will get stuck at a high level, causing the entire MMU to fail.
   // Switch the pipeline input based on whether it is an mfence-PA operation or a refill operation.
-  val paFenceInputs = Wire(new MPTPipe)
-  paFenceInputs.reqPA      := io.sfence.bits.addr(47, 12)
-  paFenceInputs.source     := io.req.bits.source
-  paFenceInputs.dataValid  := false.B
-  paFenceInputs.flushCache := true.B
-  paFenceInputs.mptOnly    := false.B
+  val fenceInputs = Wire(new MPTPipe)
+  fenceInputs.reqPA      := io.sfence.bits.addr(47, 12)
+  fenceInputs.source     := io.req.bits.source
+  fenceInputs.dataValid  := false.B
+  fenceInputs.flushCache := mfenceHasPA
+  fenceInputs.mptOnly    := false.B
+  fenceInputs.flushSdid  := mfenceHasSDID
+  fenceInputs.sdid       := fenceSDIDReg
 
   val ioInputs = Wire(new MPTPipe)
   ioInputs.reqPA      := io.req.bits.reqPA
@@ -480,38 +505,60 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   ioInputs.dataValid  := io.req.fire
   ioInputs.flushCache := false.B
   ioInputs.mptOnly    := io.req.bits.mptOnly
+  ioInputs.flushSdid  := false.B
+  ioInputs.sdid       := io.csr.mmpt.sdid
 
   val pipeInputs = Wire(new MPTPipe)
 
-  val stageReq     = MPTPipeWithReset(pipeFlowEn, pipeInputs, flushAll, 1, flushPipeOnly)
+  // mmpt.changed also flush the pipe, apart from sfence.valid.
+  // req stage will not reset during fence, as it needs to receive FenceInputs
+  val stageReq = MPTPipeWithReset(pipeFlowEn, pipeInputs, io.csr.mmpt.changed, 1, flushPipeOnly)
   val stageDelayin = stageReq.bits
-  val stageDelay   = MPTPipeWithReset(pipeFlowEn, stageDelayin, flushAll, 1, flushPipeOnly)
+  val stageDelay = MPTPipeWithReset(
+    pipeFlowEn,
+    stageDelayin,
+    io.sfence.valid || io.csr.mmpt.changed,
+    1,
+    flushPipeOnly
+  )
   val stageCheckin = stageDelay.bits
-  val stageCheck   = MPTPipeWithReset(pipeFlowEn, stageCheckin, flushAll, 1, flushPipeOnly)
-  val stageRespin  = stageCheck.bits
-  val stageResp    = MPTPipeWithReset(pipeFlowEn, stageRespin, flushAll, 1, flushPipeOnly)
+  val stageCheck = MPTPipeWithReset(
+    pipeFlowEn,
+    stageCheckin,
+    io.sfence.valid || io.csr.mmpt.changed,
+    1,
+    flushPipeOnly
+  )
+  val stageRespin = stageCheck.bits
+  val stageResp = MPTPipeWithReset(
+    pipeFlowEn,
+    stageRespin,
+    io.sfence.valid || io.csr.mmpt.changed,
+    1,
+    flushPipeOnly
+  )
 
   // priority
   // 1. fence PA
   // 2. refill and last stage valid
   // 3. normal request
 
-  pipeInputs := Mux(fencePA, paFenceInputs, Mux(refilling && stageResp.bits.dataValid, stageResp.bits, ioInputs))
-  when(io.sfence.valid) {
+  pipeInputs := Mux(io.sfence.valid, fenceInputs, Mux(refilling && stageResp.bits.dataValid, stageResp.bits, ioInputs))
+  // when(io.sfence.valid) {
 
-    pipeInputs.dataValid := false.B
-    pipeInputs.mptOnly := false.B
+  //   pipeInputs.dataValid := false.B
+  //   pipeInputs.mptOnly := false.B
 
-    stageDelayin.dataValid := false.B
-    stageDelayin.mptOnly := false.B
+  //   stageDelayin.dataValid := false.B
+  //   stageDelayin.mptOnly := false.B
 
-    stageCheckin.dataValid := false.B
-    stageCheckin.mptOnly := false.B
+  //   stageCheckin.dataValid := false.B
+  //   stageCheckin.mptOnly := false.B
 
-    stageRespin.dataValid := false.B
-    stageRespin.mptOnly := false.B
-  }
-  io.req.ready := ((io.respMiss.ready && !refilling) || (refilling && !stageResp.bits.dataValid)) && !fencePA
+  //   stageRespin.dataValid := false.B
+  //   stageRespin.mptOnly := false.B
+  // }
+  io.req.ready := ((io.respMiss.ready && !refilling) || (refilling && !stageResp.bits.dataValid)) && !mfencevalid
   // init cache tag
   val l3Tag   = Reg(Vec(l3Size, new MptCacheTag(tagLen = mptL3TagLen)))
   val l3Valid = RegInit(Vec(l3Size, Bool()), 0.U.asTypeOf(Vec(l3Size, Bool())))
@@ -542,6 +589,12 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     hasSramCtl = hasSramCtl
   )) // 1clk delay from req to resp
   val l0Valid = RegInit(Vec(l0nSets, Vec(l0nWays, Bool())), 0.U.asTypeOf(Vec(l0nSets, Vec(l0nWays, Bool()))))
+  // Keep a register shadow of the SRAM-resident SDID tags so an x0/SDID MFENCE
+  // can invalidate matching entries in every set without using a PA index.
+  val l0Sdid = RegInit(
+    Vec(l0nSets, Vec(l0nWays, UInt(sdidLen.W))),
+    0.U.asTypeOf(Vec(l0nSets, Vec(l0nWays, UInt(sdidLen.W))))
+  )
 
   val mptCacheL3Replace = Module(new PLRUOH(logWays = log2Up(l3Size))).io
   val mptCacheL2Replace = Module(new PLRUOH(logWays = log2Up(l2Size))).io
@@ -551,12 +604,36 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   // alloc replacement policy,use PLRU with Onehot in/out
 
   val (l3Hit, l3hitPPN) = {
-    val hitVecTemp = l3Tag.zip(l3Valid).map { case (x, v) =>
+    // val hitVecTemp = l3Tag.zip(l3Valid).map { case (x, v) =>
+    //   x.hitSdidPpn(stageReq.bits.reqPA, stageReq.bits.sdid) && v
+    // } // hit when valid and tag equal stagereq
+    val hitVecHitPA = l3Tag.zip(l3Valid).map { case (x, v) =>
       x.hit(stageReq.bits.reqPA) && v
-    } // hit when valid and tag equal stagereq
-    when(stageReq.bits.flushCache) { // clean fence valid if hit tag
-      hitVecTemp.zip(l3Valid).map { case (x, v) =>
-        when(x)(v := false.B)
+    }
+    val hitVecHitSDID = l3Tag.zip(l3Valid).map { case (x, v) =>
+      x.hitSdid(stageReq.bits.sdid) && v
+    }
+    val hitVecTemp = hitVecHitPA.zip(hitVecHitSDID).map {case (a, b) =>
+      a && b
+    }
+    switch(Cat(stageReq.bits.flushCache, stageReq.bits.flushSdid).asUInt) {
+      is("b11".U) {
+        hitVecTemp.zip(l3Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b01".U) {
+        hitVecHitSDID.zip(l3Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b10".U) {
+        hitVecHitPA.zip(l3Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b00".U) {
+        //NOP
       }
     }
     val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid)
@@ -572,16 +649,36 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   }
 
   val (l2Hit, l2hitPPN) = {
-    val hitVecTemp = l2Tag.zip(l2Valid).map { case (x, v) =>
+     val hitVecHitPA = l2Tag.zip(l2Valid).map { case (x, v) =>
       x.hit(stageReq.bits.reqPA) && v
-    }                                // hit when valid and tag equal stagereq
-    when(stageReq.bits.flushCache) { // clear fence valid
-      hitVecTemp.zip(l2Valid).map { case (x, v) =>
-        when(x)(v := false.B)
+    }
+    val hitVecHitSDID = l2Tag.zip(l2Valid).map { case (x, v) =>
+      x.hitSdid(stageReq.bits.sdid) && v
+    }
+    val hitVecTemp = hitVecHitPA.zip(hitVecHitSDID).map {case (a, b) =>
+      a && b
+    }
+    switch(Cat(stageReq.bits.flushCache, stageReq.bits.flushSdid).asUInt) {
+      is("b11".U) {
+        hitVecTemp.zip(l2Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b01".U) {
+        hitVecHitSDID.zip(l2Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b10".U) {
+        hitVecHitPA.zip(l2Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b00".U) {
+        //NOP
       }
     }
     val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid) // ready at stage check
-    // val hitData = ParallelPriorityMux(hitVec zip l3Data)
     val hitData = RegEnable(Mux1H(hitVecTemp, l2Data), stageReq.bits.dataValid)
     // we can use onehot mux, should be faster.
     val hit = RegEnable(hitVecTemp.reduce(_ || _), stageReq.bits.dataValid)
@@ -593,16 +690,36 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   }
 
   val (l1Hit, l1hitPPN) = {
-    val hitVecTemp = l1Tag.zip(l1Valid).map { case (x, v) =>
+     val hitVecHitPA = l1Tag.zip(l1Valid).map { case (x, v) =>
       x.hit(stageReq.bits.reqPA) && v
-    }                                // hit when valid and tag equal stagereq
-    when(stageReq.bits.flushCache) { // clear fence valid
-      hitVecTemp.zip(l1Valid).map { case (x, v) =>
-        when(x)(v := false.B)
+    }
+    val hitVecHitSDID = l1Tag.zip(l1Valid).map { case (x, v) =>
+      x.hitSdid(stageReq.bits.sdid) && v
+    }
+    val hitVecTemp = hitVecHitPA.zip(hitVecHitSDID).map {case (a, b) =>
+      a && b
+    }
+    switch(Cat(stageReq.bits.flushCache, stageReq.bits.flushSdid).asUInt) {
+      is("b11".U) {
+        hitVecTemp.zip(l1Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b01".U) {
+        hitVecHitSDID.zip(l1Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b10".U) {
+        hitVecHitPA.zip(l1Valid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b00".U) {
+        //NOP
       }
     }
     val hitVec = RegEnable(VecInit(hitVecTemp), stageReq.bits.dataValid) // ready at stage check
-    // val hitData = ParallelPriorityMux(hitVec zip l3Data)
     val hitData = RegEnable(Mux1H(hitVecTemp, l1Data), stageReq.bits.dataValid)
     // we can use onehot mux, should be faster.
     val hit = RegEnable(hitVecTemp.reduce(_ || _), stageReq.bits.dataValid)
@@ -625,7 +742,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
 
   // // // // // // // // // // // // // /
 
-  val (l0Hit, l0HitPerms, l0PermTlbCompress, l0PermIs64kNAPOT) = {
+  val (l0Hit, l0HitPerms, l0PermIs32KContinuous, l0PermIs64KContinuous) = {
     val idx = geL0Set(pipeInputs.reqPA) //
 
     l0Data.io.r.req.bits.apply(setIdx = idx) // .. 0 delay stagereq reg get valid at the same time
@@ -636,7 +753,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
       0.U.asTypeOf(Vec(l0nWays, Bool())),
       stageReq.bits.dataValid || stageReq.bits.flushCache
     )
-    when(flushAll) {
+    when(mfenceFenceAll) {
       l0validReg := 0.U.asTypeOf(Vec(l0nWays, Bool()))
     }
     val dataResp = RegEnable(l0Data.io.r.resp.data, stageReq.bits.dataValid || stageReq.bits.flushCache)
@@ -644,15 +761,40 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     val setTag  = dataResp.map(_.tag)
     val setData = dataResp.map(_.cacheData) // 4 entry+tag
     // some wire
-    val hitVecTemp = setTag.zip(l0validReg).map { case (x, v) =>
-      x.hit(stageDelay.bits.reqPA) && v
-    } // hit when valid and tag equal
 
-    // delay (29 bit === ):(1xnor + 5*and), (&& valid):(1 and) total 7
     // MfencePA
-    when(stageDelay.bits.flushCache) { // clear fence valid
-      hitVecTemp.zipWithIndex.map { case (x, i) =>
-        when(x)(l0Valid(geL0Set(stageDelay.bits.reqPA))(i) := false.B)
+    val hitVecHitPA = setTag.zip(l0validReg).map { case (x, v) =>
+      x.hit(stageDelay.bits.reqPA) && v
+    }
+    val hitVecHitSDID = setTag.zip(l0validReg).map { case (x, v) =>
+      x.hitSdid(stageDelay.bits.sdid) && v
+    }
+    val hitVecTemp = hitVecHitPA.zip(hitVecHitSDID).map {case (a, b) =>
+      a && b
+    }
+
+    switch(Cat(stageDelay.bits.flushCache, stageDelay.bits.flushSdid).asUInt) {
+      is("b11".U) {
+        hitVecTemp.zipWithIndex.map { case (x, i) =>
+          when(x)(l0Valid(geL0Set(stageDelay.bits.reqPA))(i) := false.B)
+        }
+      }
+      is("b01".U) {
+        for (set <- 0 until l0nSets) {
+          for (way <- 0 until l0nWays) {
+            when(l0Valid(set)(way) && l0Sdid(set)(way) === stageDelay.bits.sdid) {
+              l0Valid(set)(way) := false.B
+            }
+          }
+        }
+      }
+      is("b10".U) {
+        hitVecHitPA.zipWithIndex.map { case (x, i) =>
+          when(x)(l0Valid(geL0Set(stageDelay.bits.reqPA))(i) := false.B)
+        }
+      }
+      is("b00".U) {
+        //NOP
       }
     }
     //
@@ -691,12 +833,33 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
 
   // val (sphit,spHitPerms,spPermIsNAPOT,splevel) = {
   val (sphit, spHitPerms, splevel) = {
-    val hitVecTemp = spTag.zip(spValid).map { case (x, v) =>
+    val hitVecHitPA = spTag.zip(spValid).map { case (x, v) =>
       x.hitSp(stageReq.bits.reqPA) && v
-    }                                // hit when valid and tag equal delay 7 + mux1h delay 4 gates
-    when(stageReq.bits.flushCache) { // clear fence valid
-      hitVecTemp.zip(spValid).map { case (x, v) =>
-        when(x)(v := false.B)
+    }
+    val hitVecHitSDID = spTag.zip(spValid).map { case (x, v) =>
+      x.hitSdid(stageReq.bits.sdid) && v
+    }
+    val hitVecTemp = hitVecHitPA.zip(hitVecHitSDID).map {case (a, b) =>
+      a && b
+    }
+    switch(Cat(stageReq.bits.flushCache, stageReq.bits.flushSdid).asUInt) {
+      is("b11".U) {
+        hitVecTemp.zip(spValid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b01".U) {
+        hitVecHitSDID.zip(spValid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b10".U) {
+        hitVecHitPA.zip(spValid).map { case (x, v) =>
+          when(x)(v := false.B)
+        }
+      }
+      is("b00".U) {
+        //NOP
       }
     }
 
@@ -756,10 +919,9 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   val respHitReg = RegEnable(hitPerms & stageCheck.bits.dataValid, false.B, pipeFlowEn)
   // Data is latched regardless of dataValid. If dataValid is low, the hit signal is also considered invalid.
   // In this case, hitPerms actually holds the result from the previous pipeline stage.
-  
+
   respHitRegTmp    := respHitReg || overRangeFaultReg
-  io.respHit.valid := (respHitReg || overRangeFaultReg) && !refilling && !fencePA && !flushPipeOnly
-  
+  io.respHit.valid := (respHitReg || overRangeFaultReg) && !refilling && !flushPipeOnly
   val (sphitReg, l0hitReg) = (RegEnable(sphit, pipeFlowEn), RegEnable(l0Hit, pipeFlowEn))
   val cacheHitPerm = Mux1H(
     Seq(sphitReg, l0hitReg),
@@ -769,11 +931,11 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   io.respHit.bits.source           := stageResp.bits.source
   io.respHit.bits.mptOnly          := stageResp.bits.mptOnly
   io.respHit.bits.reqPA            := stageResp.bits.reqPA
-  io.respHit.bits.tlbContigousPerm := !overRangeFaultReg && l0hitReg && l0PermTlbCompress
-  io.respHit.bits.permIsNAPOT      := !overRangeFaultReg && l0hitReg && l0PermIs64kNAPOT
-  io.respHit.bits.accessFault := overRangeFaultReg || ((!cacheHitPerm(0)) && cacheHitPerm(
-    1
-  )) // not read but write // false.B // entry in mpt cache is always valid
+  io.respHit.bits.sdid             := stageResp.bits.sdid
+  io.respHit.bits.permIs32KContinuous := !overRangeFaultReg && l0hitReg && l0PermIs32KContinuous
+  io.respHit.bits.permIs64KContinuous := !overRangeFaultReg && l0hitReg && l0PermIs64KContinuous
+  io.respHit.bits.accessFault := overRangeFaultReg || ((!cacheHitPerm(0)) && cacheHitPerm(1))
+  // not read but write // false.B // entry in mpt cache is always valid
   val cacheHitLevel = Mux1H(
     Seq(sphitReg, l0hitReg),
     Seq(splevel, 0.U(mptLevelLenUInt.W))
@@ -786,12 +948,13 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     pipeFlowEn
   )
   // read regardless of dataValid
-  io.respMiss.valid := respMissReg && !refilling && !fencePA && !flushPipeOnly
+  io.respMiss.valid := respMissReg && !refilling && !flushPipeOnly
   io.respMiss.bits.hitLevel := RegEnable(hitAddrLevel, pipeFlowEn)
   io.respMiss.bits.ppn     := RegEnable(hitAddrData, pipeFlowEn)
   io.respMiss.bits.source  := stageResp.bits.source
   io.respMiss.bits.mptOnly := stageResp.bits.mptOnly
   io.respMiss.bits.pa      := stageResp.bits.reqPA
+  io.respMiss.bits.sdid    := stageResp.bits.sdid
   // read logic end
   // refill write logic start
   /* If a circular pipe is used to resolve refill conflicts, the cache will be accessed twice with the same tag during the loop.
@@ -832,6 +995,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
   // /write data into cache 1 cycle
   val l0Wdata = Wire(new MptCacheL0()) // 0 delay, wire signal assignment
   l0Wdata.tag.tag        := rfl0Tag
+  l0Wdata.tag.sdid       := io.csr.mmpt.sdid
   l0Wdata.cacheData.data := io.refill.bits.refillData.data
   l0Data.io.w.req <> DontCare // default val for write channel is invalid
   l0Data.io.w.req.valid := false.B
@@ -846,6 +1010,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
       for (i <- 0 until l0nWays) {
         when(l0VictimWay(i) === 1.U && rfl0SetIdx === j.U) {
           l0Valid(j)(i) := true.B
+          l0Sdid(j)(i) := io.csr.mmpt.sdid
         }
       }
     }
@@ -862,6 +1027,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     for (i <- 0 until l3Size) {
       when(l3VictimWay(i) === 1.U) {
         l3Tag(i).tag   := rfl3Tag
+        l3Tag(i).sdid  := io.csr.mmpt.sdid
         l3Valid(i)     := true.B
         l3Data(i).data := io.refill.bits.refillData.getPPN
       }
@@ -874,6 +1040,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     for (i <- 0 until l2Size) {
       when(l2VictimWay(i) === 1.U) {
         l2Tag(i).tag   := rfl2Tag
+        l2Tag(i).sdid  := io.csr.mmpt.sdid
         l2Valid(i)     := true.B
         l2Data(i).data := io.refill.bits.refillData.getPPN
       }
@@ -885,6 +1052,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     for (i <- 0 until l1Size) {
       when(l1VictimWay(i) === 1.U) {
         l1Tag(i).tag   := rfl1Tag
+        l1Tag(i).sdid  := io.csr.mmpt.sdid
         l1Valid(i)     := true.B
         l1Data(i).data := io.refill.bits.refillData.getPPN
       }
@@ -898,8 +1066,10 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     for (i <- 0 until spSize) {
       when(spVictimWay(i) === 1.U) {
         spTag(i).tag       := rfspTag
+        spTag(i).sdid      := io.csr.mmpt.sdid
         spValid(i)         := true.B
         spTag(i).level.get := io.refill.bits.level(3, 1)
+        spTag(i).n.get     := io.refill.bits.mpteNapot
         spData(i).data     := io.refill.bits.refillData.data
       }
     }
@@ -907,7 +1077,7 @@ class MPTCache(implicit p: Parameters) extends XSModule with MPTCacheParam {
     mptCacheSpReplace.access.valid := true.B
   }
 
-  when(flushAll) {
+  when(mfenceFenceAll) {
     refillCounter := 0.U
     l3Valid := 0.U.asTypeOf(Vec(l3Size, Bool()))
     l2Valid := 0.U.asTypeOf(Vec(l2Size, Bool()))
@@ -937,6 +1107,7 @@ class MissCacheBundle(implicit p: Parameters) extends XSBundle with MPTCachePara
   val source   = UInt(mptSourceWidth.W)
   val pa       = UInt(ppnLen.W) // req pa minsize is 4k,dont need 12bits offset
   val mptOnly  = Bool()         // 1bit control signal for tlb, does basically nothing in mptc
+  val sdid     = UInt(sdidLen.W)
 }
 
 class MptMissQueueIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCacheParam {
@@ -949,11 +1120,12 @@ class MptMissQueueIO(implicit p: Parameters) extends MMUIOBaseBundle with MPTCac
     val accessFault     = Bool()
     val mptLevel        = UInt(mptLevelLenOH.W)
     val perm            = UInt(3.W)
-    val PermTlbCompress = Bool()
-    val permIsNAPOT     = Bool()
+    val permIs32KContinuous = Bool()
+    val permIs64KContinuous = Bool()
     val mptOnly         = Bool()
     val reqPA           = UInt(ppnLen.W)
     val source          = UInt(mptSourceWidth.W)
+    val sdid            = UInt(sdidLen.W)
   })
 }
 
@@ -961,7 +1133,7 @@ class MptMissQueue(implicit p: Parameters) extends XSModule with MPTCacheParam {
   val io = IO(new MptMissQueueIO)
 
   val flush = io.csr.satp.changed || io.csr.vsatp.changed ||
-io.csr.hgatp.changed || io.csr.priv.virt_changed || io.sfence.valid || io.csr.mmpt.changed
+    io.csr.hgatp.changed || io.csr.priv.virt_changed || io.sfence.valid || io.csr.mmpt.changed
   val reqFIFO = Module(new Queue(new MissCacheBundle(), entries = 4, pipe = true, hasFlush = true)).io
   // FIFO queue,record offset
   val fifoNotEmpty = reqFIFO.deq.valid
@@ -980,18 +1152,25 @@ io.csr.hgatp.changed || io.csr.priv.virt_changed || io.sfence.valid || io.csr.mm
 
   refilling := refillCounter > 0.U // is refiill state when counter ! = 0 4 clk
 
-  val l3Hit = reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - mptL3TagLen) === refillReg.pa(
-    PAddrBits - mptOff - 1,
-    PAddrBits - mptOff - mptL3TagLen
-  ) // 35:31 31:27
-  val l2Hit = reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - mptL2TagLen) === refillReg.pa(
-    PAddrBits - mptOff - 1,
-    PAddrBits - mptOff - mptL2TagLen
-  ) // 35:22 31:18
-  val l1Hit = reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - mptL1TagLen) === refillReg.pa(
-    PAddrBits - mptOff - 1,
-    PAddrBits - mptOff - mptL1TagLen
-  ) // 35:13 31:9
+  def refillPrefixHit(width: Int): Bool =
+    reqFIFO.deq.bits.pa(ppnLen - 1, ppnLen - width) ===
+      refillReg.pa(PAddrBits - mptOff - 1, PAddrBits - mptOff - width)
+
+  val l3Hit = Mux(
+    refillReg.useNapot,
+    refillPrefixHit(mptL3TagLen + mptNapotExtraTagLen),
+    refillPrefixHit(mptL3TagLen)
+  )
+  val l2Hit = Mux(
+    refillReg.useNapot,
+    refillPrefixHit(mptL2TagLen + mptNapotExtraTagLen),
+    refillPrefixHit(mptL2TagLen)
+  )
+  val l1Hit = Mux(
+    refillReg.useNapot,
+    refillPrefixHit(mptL1TagLen + mptNapotExtraTagLen),
+    refillPrefixHit(mptL1TagLen)
+  )
   val l0Hit = reqFIFO.deq.bits.pa(ppnLen - 1, mptOff - offLen) === refillReg.pa // 35:4 31:0
   val hitFIFO = Mux1H(Seq(
     refillReg.level(3) -> l3Hit,
@@ -1013,8 +1192,10 @@ io.csr.hgatp.changed || io.csr.priv.virt_changed || io.sfence.valid || io.csr.mm
 
   reqAltInput := reqFIFO.deq.bits
 
+  // A non-leaf refill contains the child table's PPN. Pair that pointer
+  // with the next level to read, just as the cache miss path does.
   when(hitFIFO && refilling) {
-    reqAltInput.hitLevel := refillReg.level
+    reqAltInput.hitLevel := refillReg.level >> 1
     reqAltInput.hitAddr  := refillReg.refillData.getPPN
   }
 
@@ -1035,6 +1216,7 @@ io.csr.hgatp.changed || io.csr.priv.virt_changed || io.sfence.valid || io.csr.mm
   io.resp.bits.reqPA := reqFIFO.deq.bits.pa // reqPA is the PA of the request, used to generate the refill address
   io.resp.bits.source  := reqFIFO.deq.bits.source // source is the source
   io.resp.bits.mptOnly := reqFIFO.deq.bits.mptOnly && io.resp.valid
+  io.resp.bits.sdid := reqFIFO.deq.bits.sdid
 
   val permsAsVec = Wire(Vec(16, UInt(3.W))) // perm xwr bits, total 16 xwrs in one mpte
   for (i <- 0 until 16) { permsAsVec(i) := refillReg.refillData.data(2 + i * 3, i * 3) }
@@ -1043,10 +1225,10 @@ io.csr.hgatp.changed || io.csr.priv.virt_changed || io.sfence.valid || io.csr.mm
   val leftPermsAllEqual  = permsEqual.slice(0, 7).reduce(_ && _)  // = permsEqual(6,0), delay 3
   val rightPermsAllEqual = permsEqual.slice(8, 15).reduce(_ && _) // = permsEqual(14,8)
   // OH (refillReg.level === 1.U(4.W)) is (refillReg.level(0))
-  io.resp.bits.permIsNAPOT := (refillReg.level(0)) && leftPermsAllEqual && rightPermsAllEqual && permsEqual(
+  io.resp.bits.permIs64KContinuous := (refillReg.level(0)) && leftPermsAllEqual && rightPermsAllEqual && permsEqual(
     7
   ) // Delay 2
-  io.resp.bits.PermTlbCompress := (refillReg.level(0)) && Mux(
+  io.resp.bits.permIs32KContinuous := (refillReg.level(0)) && Mux(
     reqFIFO.deq.bits.pa(mptOff - offLen - 1, 0) < 8.U,
     leftPermsAllEqual,
     rightPermsAllEqual
@@ -1127,16 +1309,63 @@ class MPTTableWalker(implicit p: Parameters) extends XSModule with MPTCacheParam
   val isLeafMpte = RegEnable(mpteResp.isLeaf, false.B, memRespAccepted)
   io.refill.bits.isLeafMpte := isLeafMpte // tell cache if the current refill is leaf node
   val mpteInvalid = RegEnable(!mpteResp.isValid, false.B, memRespAccepted) // 1 level not on top of mem.resp
-  val rsvZeroError0 = RegEnable(mem.resp.bits(9, 2).orR, false.B, memRespAccepted)
-  // max 3 level or gate on top of mem.resp，NON ZERO error of mtpe
-  val rsvZeroError1 = RegEnable(mem.resp.bits(62, 58).orR, false.B, memRespAccepted)
-  val rsvZeroError2 = false.B
+  val mpteN = RegEnable(mpteResp.N, false.B, memRespAccepted)
+  val mpteG = RegEnable(mpteResp.g, 0.U(4.W), memRespAccepted)
+
+  // Register the reserved-bit reductions directly from the memory response,
+  // as in the original rsvZeroError path.  The state machine consumes them in
+  // s_addr_proc, so the remaining entry-type checks are after this register.
+  val nonLeafReserved = RegEnable(
+    mem.resp.bits(9, 3).orR || mem.resp.bits(63, 54).orR || mem.resp.bits(53, PAddrBits - 2).orR, // Xiangshan support PAlen 48
+    false.B,
+    memRespAccepted
+  )
+  val normalLeafReserved = RegEnable(
+    mem.resp.bits(7, 3).orR || mem.resp.bits(63, 56).orR,
+    false.B,
+    memRespAccepted
+  )
+  val napotLeafReserved = RegEnable(
+    mem.resp.bits(7, 3).orR || mem.resp.bits(11) || mem.resp.bits(63, 16).orR,
+    false.B,
+    memRespAccepted
+  )
+
+  val topLevel = MuxLookup(io.csr.mmpt.mode, true.B)(Seq(
+    1.U -> level(2), // Smmpt43: L2 NAPOT would describe an unsupported 512 GiB range.
+    2.U -> level(3)  // Smmpt52: L3 NAPOT would describe an unsupported 256 TiB range.
+  ))
+  // XWR legality is checked when the selected permission is consumed.  This
+  // stage only validates the MPTE form and its reserved fields.
+  val napotEncodingError = isLeafMpte && mpteN &&
+    (napotLeafReserved || mpteG =/= 4.U || topLevel)
+  val normalLeafEncodingError = isLeafMpte && !mpteN && normalLeafReserved
+  val nonLeafEncodingError = !isLeafMpte && (mpteN || nonLeafReserved)
+  val encodingError = napotEncodingError || normalLeafEncodingError || nonLeafEncodingError
+  io.refill.bits.mpteNapot := mpteN
   when(io.req.fire) {
-    mpteData.apply(io.req.bits.hitAddr)
+    // hitAddr is already a PPN.  Place it at the same data[ppnLen + 1:2]
+    // position used by raw MPTE[55:8] before calling getAddr.
+    mpteData.apply(io.req.bits.hitAddr << 2)
   }.elsewhen(memRespAccepted) {
-    mpteData := mpteResp.data
+    // Keep MptEntry as the raw MPTE[55:8] view.  Convert only the NAPOT leaf
+    // to the refill format expected by the cache and miss queue: all sixteen
+    // permission slots carry the single MPTE[10:8] XWR tuple.
+    mpteData.data := Mux(
+      mpteResp.isLeaf && mpteResp.N,
+      Fill(16, mpteResp.data.data(2, 0)),
+      mpteResp.data.data
+    )
   }
 
+  val rootAddr = Cat(io.csr.mmpt.ppn(ppnLen - 1, 0), 0.U(offLen.W))
+  val checkRootPMP = Wire(Bool())
+  val rootLevel = Mux(
+    io.csr.mmpt.mode === 2.U,
+    "b1000".U(mptLevelLenOH.W), // Smmpt52
+    "b0100".U(mptLevelLenOH.W)  // Smmpt43
+  )
+
   val pn = Wire(UInt(9.W))
   pn := Mux1H(Seq(
     level(3) -> Cat(0.U(4.W), pa(47 - mptOff, 43 - mptOff)),
@@ -1149,16 +1378,17 @@ class MPTTableWalker(implicit p: Parameters) extends XSModule with MPTCacheParam
   val memAddr = mpteData.getAddr(pn) // gen addr 0 delay
   io.mem.req.bits.addr := memAddr
   io.pmp.req.valid := DontCare
-  io.pmp.req.bits.addr := Mux(memRespAccepted, mpteResp.getAddr(pn), memAddr)
-  // should be safer than just := memAddr
+  io.pmp.req.bits.addr := Mux(checkRootPMP, rootAddr, mpteData.getAddr(0.U(9.W)))
 
   // accessFault logic
-  val pmpFail = (!isLeafMpte) && (io.pmp.resp.ld || io.pmp.resp.mmio)
+  val pmpFail = ((!isLeafMpte && level =/= 1.U) || checkRootPMP) && (io.pmp.resp.ld || io.pmp.resp.mmio)
   // PMP delay unknown
-  val entryError = mpteInvalid || rsvZeroError0 || rsvZeroError1 || rsvZeroError2 || ((!isLeafMpte) && level === 1.U)
+  val entryError = mpteInvalid || encodingError || ((!isLeafMpte) && level === 1.U)
   // level == 0 non leaf, zero = / = 0,pmp fail,invalid casue accessFault
   val accessFault = entryError || pmpFail // pmp fail also cause accessFault
-  io.refill.bits.level := Mux(pmpFail, pmpCheckLevel, level) // pmpFail return next level,else cur level
+  val effectiveLevel = Mux(mpteN, (level << 1)(mptLevelLenOH - 1, 0), level)
+  val faultLevel = Mux(pmpFail && !entryError, pmpCheckLevel, level)
+  io.refill.bits.level := Mux(accessFault, faultLevel, effectiveLevel)
   io.refill.bits.isAf  := accessFault
   // pmp fail will not be recorded(if the root addr+ pn[i] cause pmp fail does not necessarily mean that
   // the root addr + other offset will cause pmp fail, so entry will be refilled as a normal intermidiate node)
@@ -1180,16 +1410,28 @@ class MPTTableWalker(implicit p: Parameters) extends XSModule with MPTCacheParam
   nextLevel         := level >> 1.U // onehotcounter-1 no aflevel
   nextPmpCheckLevel := pmpCheckLevel >> 1.U
   io.refill.valid   := false.B
+  checkRootPMP      := false.B
   // default val
   switch(curState) {
     is(s_idle) {
+      checkRootPMP := true.B
       io.req.ready := true.B
       when(io.req.fire) {
-        setPmpCheckLevel  := true.B
-        nextLevel         := io.req.bits.hitLevel
-        nextPmpCheckLevel := io.req.bits.hitLevel
-        nextState         := s_mem_req // to mem req if fire
-        setLevel          := true.B
+        when(pmpFail) {
+          io.refill.valid           := true.B
+          io.refill.bits.isAf       := true.B
+          io.refill.bits.isLeafMpte := false.B
+          io.refill.bits.mpteNapot  := false.B
+          io.refill.bits.pa         := io.req.bits.reqPA
+          io.refill.bits.level      := rootLevel
+          nextState                 := s_idle
+        }.otherwise {
+          setPmpCheckLevel  := true.B
+          nextLevel         := io.req.bits.hitLevel
+          nextPmpCheckLevel := io.req.bits.hitLevel
+          nextState         := s_mem_req // to mem req if fire
+          setLevel          := true.B
+        }
       }
     }
     is(s_mem_req) {
@@ -1211,10 +1453,10 @@ class MPTTableWalker(implicit p: Parameters) extends XSModule with MPTCacheParam
       // process return
       when(accessFault || isLeafMpte) { // out delay unknown
         // when(isLeafMpte) {io.refill.valid := true.B}
-        io.refill.valid := true.B
+        io.refill.valid := !flush
         nextState       := s_idle // OPTPOINT*
       }.otherwise {
-        io.refill.valid := true.B   // start refill
+        io.refill.valid := !flush   // start refill
         setLevel        := true.B
         nextState       := s_mem_req // OPTPOINT*
       }
@@ -1235,8 +1477,11 @@ class MPTTableWalker(implicit p: Parameters) extends XSModule with MPTCacheParam
     pmpCheckLevel := "b1000".U(mptLevelLenOH.W)
     isLeafMpte := false.B
     mpteInvalid := false.B
-    rsvZeroError0 := false.B
-    rsvZeroError1 := false.B
+    mpteN := false.B
+    mpteG := 0.U
+    nonLeafReserved := false.B
+    normalLeafReserved := false.B
+    napotLeafReserved := false.B
     // Match PTW: invalidate the local transaction immediately, but leave the
     // L2TLB source occupied until its late response is drained.
     w_mem_resp := true.B
@@ -1282,15 +1527,16 @@ class MptChecker(implicit p: Parameters) extends XSModule with HasPtwConst {
   mptCacheInst.req.bits.mptOnly := io.req.bits.mptOnly // need some fix
   mptCacheInst.req.bits.reqPA   := io.req.bits.reqPA
   mptCacheInst.req.bits.source  := io.req.bits.id
+  mptCacheInst.req.bits.sdid    := io.csr.mmpt.sdid
   mptCacheInst.req.valid        := io.req.valid && mptEn
   io.req.ready                  := mptEn && mptCacheInst.req.ready
 
   val mptReturn = Wire(new MptRespBundle())
   mptReturn.mptPerm := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.perm, mptCacheInst.respHit.bits.perm)
-  mptReturn.contigousPerm := Mux(
+  mptReturn.permIs32KContinuous := Mux(
     mptMissQueueInst.resp.valid,
-    mptMissQueueInst.resp.bits.PermTlbCompress,
-    mptCacheInst.respHit.bits.tlbContigousPerm
+    mptMissQueueInst.resp.bits.permIs32KContinuous,
+    mptCacheInst.respHit.bits.permIs32KContinuous
   )
   mptReturn.id := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.source, mptCacheInst.respHit.bits.source)
   mptReturn.mptLevel := Mux(
@@ -1304,17 +1550,17 @@ class MptChecker(implicit p: Parameters) extends XSModule with HasPtwConst {
     mptCacheInst.respHit.bits.mptOnly
   )
   mptReturn.reqPA := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.reqPA, mptCacheInst.respHit.bits.reqPA)
+  mptReturn.sdid := Mux(mptMissQueueInst.resp.valid, mptMissQueueInst.resp.bits.sdid, mptCacheInst.respHit.bits.sdid)
   mptReturn.accessFault := Mux(
     mptMissQueueInst.resp.valid,
     mptMissQueueInst.resp.bits.accessFault,
     mptCacheInst.respHit.bits.accessFault
   )
-  mptReturn.permIsNAPOT := Mux(
+  mptReturn.permIs64KContinuous := Mux(
     mptMissQueueInst.resp.valid,
-    mptMissQueueInst.resp.bits.permIsNAPOT,
-    mptCacheInst.respHit.bits.permIsNAPOT
+    mptMissQueueInst.resp.bits.permIs64KContinuous,
+    mptCacheInst.respHit.bits.permIs64KContinuous
   )
-
   io.resp.valid := mptEn && (mptMissQueueInst.resp.valid || mptCacheInst.respHit.valid)
   io.resp.bits <> mptReturn
 
@@ -1325,6 +1571,7 @@ class MptChecker(implicit p: Parameters) extends XSModule with HasPtwConst {
   mptMissQueueInst.missCache.bits.hitAddr  := mptCacheInst.respMiss.bits.ppn
   mptMissQueueInst.missCache.bits.source   := mptCacheInst.respMiss.bits.source
   mptMissQueueInst.missCache.bits.pa       := mptCacheInst.respMiss.bits.pa
+  mptMissQueueInst.missCache.bits.sdid     := mptCacheInst.respMiss.bits.sdid
   mptMissQueueInst.missCache.valid         := mptCacheInst.respMiss.valid && mptEn
   mptCacheInst.respMiss.ready              := Mux(mptEn, mptMissQueueInst.missCache.ready, true.B)
   // cache refill io
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 814137bae51..358cdb92a19 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -218,7 +218,8 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
   val refill = io.refill.bits
   val refill_prefetch_dup = io.refill.bits.req_info_dup.map(a => from_pre(a.source))
   val refill_h = io.refill.bits.req_info_dup.map(a => Mux(a.s2xlate === allStage, onlyStage1, a.s2xlate))
-  val flush_dup = sfence_dup.zip(io.csr_dup).map(f => f._1.valid || f._2.satp.changed || f._2.vsatp.changed || f._2.hgatp.changed || f._2.priv.virt_changed)
+  val flush_dup = sfence_dup.zip(io.csr_dup).map(f => f._1.valid || f._2.satp.changed || f._2.vsatp.changed ||
+    f._2.hgatp.changed || f._2.priv.virt_changed || (if (HasMptCheck) f._2.mmpt.changed else false.B))
   val flush = flush_dup(0)
 
   // when refill, refuce to accept new req
diff --git a/src/main/scala/xiangshan/cache/mmu/Repeater.scala b/src/main/scala/xiangshan/cache/mmu/Repeater.scala
index abd3cec3dd4..c8b3293d67d 100644
--- a/src/main/scala/xiangshan/cache/mmu/Repeater.scala
+++ b/src/main/scala/xiangshan/cache/mmu/Repeater.scala
@@ -55,7 +55,9 @@ class PTWRepeater(Width: Int = 1, FenceDelay: Int)(implicit p: Parameters) exten
     arb.io.in <> io.tlb.req
     arb.io.out
   }
-  val (tlb, ptw, flush) = (io.tlb, io.ptw, DelayN(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed, FenceDelay))
+  val (tlb, ptw, flush) = (io.tlb, io.ptw, DelayN(io.sfence.valid || io.csr.satp.changed ||
+    io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed ||
+    (if (HasMptCheck) io.csr.mmpt.changed else false.B), FenceDelay))
   val req = RegEnable(req_in.bits, req_in.fire)
   val resp = RegEnable(ptw.resp.bits, ptw.resp.fire)
   val haveOne = BoolStopWatch(req_in.fire, tlb.resp.fire || flush)
@@ -97,7 +99,9 @@ class PTWRepeaterNB(Width: Int = 1, passReady: Boolean = false, FenceDelay: Int)
     arb.io.in <> io.tlb.req
     arb.io.out
   }
-  val (tlb, ptw, flush) = (io.tlb, io.ptw, DelayN(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed, FenceDelay))
+  val (tlb, ptw, flush) = (io.tlb, io.ptw, DelayN(io.sfence.valid || io.csr.satp.changed ||
+    io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed ||
+    (if (HasMptCheck) io.csr.mmpt.changed else false.B), FenceDelay))
   /* sent: tlb -> repeater -> ptw
    * recv: ptw -> repeater -> tlb
    * different from PTWRepeater
@@ -198,9 +202,21 @@ class PTWFilterEntry(Width: Int, Size: Int, hasHint: Boolean = false)(implicit p
 
   val entryIsMatchVec = WireInit(VecInit(Seq.fill(Width)(false.B)))
   val entryMatchIndexVec = WireInit(VecInit(Seq.fill(Width)(0.U(log2Up(Size).W))))
-  val ptwResp_EntryMatchVec = vpn.zip(v).zip(s2xlate).map{ case ((pi, vi), s2xlatei) => vi && s2xlatei === io.ptw.resp.bits.s2xlate && io.ptw.resp.bits.hit(pi, io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid, allType = true)}
+  val ptwResp_EntryMatchVec = vpn.zip(v).zip(s2xlate).map { case ((pi, vi), s2xlatei) =>
+    vi && s2xlatei === io.ptw.resp.bits.s2xlate && io.ptw.resp.bits.hit(
+      pi, io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid,
+      allType = true,
+      sdid = if (HasMptCheck) io.csr.mmpt.sdid else 0.U,
+      matchMpt = if (HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B
+    )
+  }
   val ptwResp_EntryMatchFirst = firstValidIndex(ptwResp_EntryMatchVec, true.B)
-  val ptwResp_ReqMatchVec = io.tlb.req.map(a => io.ptw.resp.valid && a.bits.s2xlate === io.ptw.resp.bits.s2xlate && io.ptw.resp.bits.hit(a.bits.vpn, io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid, allType = true))
+  val ptwResp_ReqMatchVec = io.tlb.req.map(a => io.ptw.resp.valid && a.bits.s2xlate === io.ptw.resp.bits.s2xlate && io.ptw.resp.bits.hit(
+    a.bits.vpn, io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid,
+    allType = true,
+    sdid = if (HasMptCheck) io.csr.mmpt.sdid else 0.U,
+    matchMpt = if (HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B
+  ))
 
   io.refill := Cat(ptwResp_EntryMatchVec).orR && io.ptw.resp.fire
   io.ptw.resp.ready := true.B
@@ -379,7 +395,8 @@ class PTWNewFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameter
   store_filter.map(_.tlb.req := io.tlb.req.drop(LdExuCnt + PfNumInDtlbLD).take(StaCnt))
   prefetch_filter.map(_.tlb.req := io.tlb.req.drop(LdExuCnt + PfNumInDtlbLD + StaCnt))
 
-  val flush = DelayN(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed, FenceDelay)
+  val flush = DelayN(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed ||
+    io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B), FenceDelay)
   val ptwResp = RegEnable(io.ptw.resp.bits, io.ptw.resp.fire)
   val ptwResp_valid = Cat(filter.map(_.refill)).orR
   filter.map(_.tlb.resp.ready := true.B)
@@ -480,7 +497,8 @@ class PTWFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameters)
   val mayFullDeq = RegInit(false.B)
   val mayFullIss = RegInit(false.B)
   val counter = RegInit(0.U(log2Up(Size+1).W))
-  val flush = DelayN(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed || io.csr.priv.virt_changed, FenceDelay)
+  val flush = DelayN(io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed ||
+    io.csr.priv.virt_changed || (if (HasMptCheck) io.csr.mmpt.changed else false.B), FenceDelay)
   val tlb_req = WireInit(io.tlb.req) // NOTE: tlb_req is not io.tlb.req, see below codes, just use cloneType
   tlb_req.suggestName("tlb_req")
 
@@ -488,7 +506,12 @@ class PTWFilter(Width: Int, Size: Int, FenceDelay: Int)(implicit p: Parameters)
   val inflight_full = inflight_counter === Size.U
 
   def ptwResp_hit(vpn: UInt, s2xlate: UInt, resp: PtwRespS2): Bool = {
-    s2xlate === resp.s2xlate && resp.hit(vpn, io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid, allType = true)
+    s2xlate === resp.s2xlate && resp.hit(
+      vpn, io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid,
+      allType = true,
+      sdid = if (HasMptCheck) io.csr.mmpt.sdid else 0.U,
+      matchMpt = if (HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B
+    )
   }
 
   when (io.ptw.req(0).fire =/= io.ptw.resp.fire) {
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index a49d680296e..5fc03db19a1 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -156,17 +156,14 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   val isRobMatch = (0 until Width).map(i => req_out(i).debug.robIdx === io.robPendingPtr)
   val canGetGpa =  (0 until Width).map(i => isRobMatch(i) || TlbCmd.isExec(req_out(i).cmd) || req_out(i).frm_mabuf)
 
-  val BareMode = Option.when(HasMptCheck) (
-    csr.hgatp.mode === 0.U && csr.vsatp.mode === 0.U && csr.satp.mode === 0.U
-  ) // will start mpt only check if not in M mode
   val useReqS1Paddr = (0 until Width).map(i => RegNext(req(i).bits.no_translate))
   val privNeedTranslate = (0 until Width).map(i => (vmEnable(i) || s2xlateEnable(i)))
   val portTranslateEnable = (0 until Width).map(i => privNeedTranslate(i) && !useReqS1Paddr(i))
-
+  val BareMode = Option.when(HasMptCheck) { privNeedTranslate.map(enable => !enable) }
   val mptEn = Option.when(HasMptCheck) (csr.mmpt.mode =/= 0.U)
-  val mptCheckOnly = Option.when(HasMptCheck) ((0 until Width).map( i =>
-    BareMode.get && RegEnable(!req(i).bits.no_translate, req(i).valid) && mptEn.get && (mode(i) < ModeM)
-  )) // need mptcheck even when ptw disable, but dont need it when req.no_trans
+  val mptCheckOnly = Option.when(HasMptCheck) { (0 until Width).map { i =>
+    BareMode.get(i) && !req_out(i).no_translate &&  mptEn.get && (mode(i) < ModeM)
+  }}
   (0 until Width).foreach{ i =>
     if (HasMptCheck) {
       io.ptw.req(i).bits.mptOnly.get := mptCheckOnly.get(i)
@@ -260,7 +257,10 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     }
   }
 
-  val refill = ptw.resp.fire && !(ptw.resp.bits.getGpa) && !need_gpa && !maybe_need_gpa_not_allow_refill && !flush_mmu || (if (HasMptCheck) (ptw.resp.fire && BareMode.get) else false.B)
+  val refill = ptw.resp.fire &&
+    (!(ptw.resp.bits.getGpa) && !need_gpa && !maybe_need_gpa_not_allow_refill ||
+      (if (HasMptCheck) ptw.resp.bits.mpt.get.mptOnly else false.B)) &&
+    !flush_mmu
   // mpt only mode also returns and save to l1tlb
   // prevent ptw refill when: 1) it's a getGpa request; 2) l1tlb is in need_gpa state; 3) mmu is being flushed.
 
@@ -269,7 +269,13 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   entries.io.base_connect(sfence, csr, csr.satp)
   if (q.outReplace) { io.replace <> entries.io.replace }
   for (i <- 0 until Width) {
-    entries.io.r_req_apply(io.requestor(i).req.valid, get_pn(req_in(i).bits.vaddr), i, req_in_s2xlate(i))
+    entries.io.r_req_apply(
+      io.requestor(i).req.valid,
+      get_pn(req_in(i).bits.vaddr),
+      i,
+      req_in_s2xlate(i),
+      if (HasMptCheck) mptEn.get else false.B
+    )
     entries.io.w_apply(refill, ptw.resp.bits)
     // TODO: RegNext enable:req.valid
     resp(i).bits.debug.isFirstIssue := RegEnable(req(i).bits.debug.isFirstIssue, req(i).valid)
@@ -297,7 +303,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     for (d <- 0 until nRespDups) {
       pbmt_check(i, d, pbmt(i)(d), g_pbmt(i)(d), req_out_s2xlate(i))
       val mptTemp = Option.when(HasMptCheck) (mptPerm.get(i)(d))
-      perm_check(mptTemp.getOrElse(0.U.asTypeOf(new MptPermBundle(isTlbPort = true))), mptEn.getOrElse(false.B), perm(i)(d),
+      perm_check(mptTemp.getOrElse(0.U.asTypeOf(new MptPermBundle)), mptEn.getOrElse(false.B), perm(i)(d),
         req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i), prepf(i), pregpf(i), preaf(i))
     }
     hasGpf(i) := hitVec(i) && (resp(i).bits.excp(0).gpf.ld || resp(i).bits.excp(0).gpf.st || resp(i).bits.excp(0).gpf.instr)
@@ -508,11 +514,14 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val s1_valid = portTranslateEnable(idx) && !onlyS2
 
     // mptcheck val
-    val mptValid = Option.when(HasMptCheck) (mptCheckOnly.get(idx))
+    // MPT permissions and AF apply to translated accesses as well as Bare-mode checks.
+    val mptValid = Option.when(HasMptCheck) (
+      mptEn && (mode(idx) < ModeM) && (portTranslateEnable(idx) || mptCheckOnly.get(idx))
+    )
     val mptInstf = Option.when(HasMptCheck) (!mptPerm.x && isInst)
     val mptWf = Option.when(HasMptCheck) (!(mptPerm.r && mptPerm.w) && isSt)
     val mptRf = Option.when(HasMptCheck) (!mptPerm.r && isLd)
-    val mptAf = Option.when(HasMptCheck) (mptPerm.af.get)
+    val mptAf = Option.when(HasMptCheck) (mptPerm.af)
 
     // Stage 2 perm check
     val gpf = g_perm.pf
@@ -601,10 +610,20 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       (csr.hgatp.mode === 0.U) -> onlyStage1
     ))
 
-    val ptw_just_back = ptw.resp.fire && req_s2xlate === ptw.resp.bits.s2xlate && ptw.resp.bits.hit(get_pn(req_out(idx).vaddr), csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid, allType = true)
+    val ptw_just_back = ptw.resp.fire && req_s2xlate === ptw.resp.bits.s2xlate && ptw.resp.bits.hit(
+      get_pn(req_out(idx).vaddr), csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid,
+      allType = true,
+      sdid = if (HasMptCheck) csr.mmpt.sdid else 0.U,
+      matchMpt = if (HasMptCheck) mptEn.get else false.B
+    )
     // TODO: RegNext enable: ptw.resp.valid ? req.valid
     val ptw_resp_bits_reg = RegEnable(ptw.resp.bits, ptw.resp.valid)
-    val ptw_already_back = GatedValidRegNext(ptw.resp.fire) && req_s2xlate === ptw_resp_bits_reg.s2xlate && ptw_resp_bits_reg.hit(get_pn(req_out(idx).vaddr), csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid, allType = true)
+    val ptw_already_back = GatedValidRegNext(ptw.resp.fire) && req_s2xlate === ptw_resp_bits_reg.s2xlate && ptw_resp_bits_reg.hit(
+      get_pn(req_out(idx).vaddr), csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid,
+      allType = true,
+      sdid = if (HasMptCheck) csr.mmpt.sdid else 0.U,
+      matchMpt = if (HasMptCheck) mptEn.get else false.B
+    )
     val ptw_getGpa = req_need_gpa && hitVec(idx)
     val need_gpa_req_match = need_gpa_vpn === get_pn(req_out(idx).vaddr) && need_gpa_s2xlate === req_s2xlate
 
@@ -651,7 +670,13 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       (csr.hgatp.mode === 0.U) -> onlyStage1
     ))
     val miss_req_s2xlate_reg = RegEnable(miss_req_s2xlate, io.ptw.req(idx).fire)
-    val hit = io.ptw.resp.bits.hit(miss_req_vpn, csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid, allType = true, ignoreAsid = false) && io.ptw.resp.valid && miss_req_s2xlate_reg === io.ptw.resp.bits.s2xlate
+    val hit = io.ptw.resp.bits.hit(
+      miss_req_vpn, csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid,
+      allType = true,
+      ignoreAsid = false,
+      sdid = if (HasMptCheck) csr.mmpt.sdid else 0.U,
+      matchMpt = if (HasMptCheck) mptEn.get else false.B
+    ) && io.ptw.resp.valid && miss_req_s2xlate_reg === io.ptw.resp.bits.s2xlate
 
     val new_coming_valid = WireInit(false.B)
     new_coming_valid := req_in(idx).fire && !req_in(idx).bits.kill && !flush_pipe(idx)
@@ -670,7 +695,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       val stage1 = io.ptw.resp.bits.s1
       val stage2 = io.ptw.resp.bits.s2
       val s2xlate = io.ptw.resp.bits.s2xlate
-      val mptResp = Option.when(HasMptCheck) (Wire (new MptPermBundle(isTlbPort = true)))
+      val mptResp = Option.when(HasMptCheck) (Wire (new MptPermBundle))
       if (HasMptCheck) {mptResp.get.resp_apply(io.ptw.resp.bits.mpt.get)}
       resp(idx).valid := true.B
       resp(idx).bits.miss := false.B
@@ -697,7 +722,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
         resp(idx).bits.paddr(d) := Mux(s2xlate === onlyStage2 || s2xlate === allStage, s2_paddr, s1_paddr)
         resp(idx).bits.gpaddr(d) := Mux(s2xlate === onlyStage2, req_out(idx).vaddr, s1_gpaddr)
         pbmt_check(idx, d, io.ptw.resp.bits.s1.entry.pbmt, io.ptw.resp.bits.s2.entry.pbmt, s2xlate)
-        perm_check(mptResp.getOrElse(0.U.asTypeOf(new MptPermBundle(isTlbPort = true))), mptEn.getOrElse(false.B),
+        perm_check(mptResp.getOrElse(0.U.asTypeOf(new MptPermBundle)), mptEn.getOrElse(false.B),
           stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
       }
       val pmpCmd = Mux(req_out(idx).hlvx, TlbCmd.read_exec, req_out(idx).cmd)
@@ -741,7 +766,12 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val onlyS2 = s2xlate === onlyStage2
     val onlyS1 = s2xlate === onlyStage1
     val s2xlate_hit = s2xlate === ptw.resp.bits.s2xlate
-    val resp_hit = ptw.resp.bits.hit(vpn, csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid, allType = true)
+    val resp_hit = ptw.resp.bits.hit(
+      vpn, csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid,
+      allType = true,
+      sdid = if (HasMptCheck) csr.mmpt.sdid else 0.U,
+      matchMpt = if (HasMptCheck) mptEn.get else false.B
+    )
     val p_hit_fast = resp_hit && io.ptw.resp.fire && s2xlate_hit    // valid in the same cycle as tlb_req and ptw_resp
     val p_hit = GatedValidRegNext(p_hit_fast)                       // valid in the next cycle after tlb_req and ptw_resp
     val ppn_s1 = ptw.resp.bits.s1.genPPN(vpn)(ppnLen - 1, 0)
@@ -758,7 +788,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val p_s1_isLeaf = RegEnable(ptw.resp.bits.s1.isLeaf(), io.ptw.resp.fire)
     val p_s1_isFakePte = RegEnable(ptw.resp.bits.s1.isFakePte(), io.ptw.resp.fire)
 
-    val p_mptPerm_temp = Option.when(HasMptCheck) (Wire(new MptPermBundle(isTlbPort = true)))
+    val p_mptPerm_temp = Option.when(HasMptCheck) (Wire(new MptPermBundle))
     if (HasMptCheck) {
       p_mptPerm_temp.get.resp_apply(ptw.resp.bits.mpt.get)
     }
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index c4161ab4587..3450eecbff8 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -101,7 +101,7 @@ class TLBFA(
   val entries = Reg(Vec(nWays, new TlbSectorEntry(normalPage, superPage)))
   val g = entries.map(_.perm.g)
 
-  for (i <- 0 until ports) {
+  val hitVecs = (0 until ports).map { i =>
     val req = io.r.req(i)
     val resp = io.r.resp(i)
     val access = io.access(i)
@@ -115,7 +115,10 @@ class TLBFA(
     val hitVec = VecInit((entries.zipWithIndex).zip(v zip refill_mask.asBools).map{
       case (e, m) => {
         val s2xlate_hit = e._1.s2xlate === req.bits.s2xlate
-        val hit = e._1.hit(vpn, Mux(hasS2xlate, io.csr.vsatp.asid, io.csr.satp.asid), vmid = io.csr.hgatp.vmid, hasS2xlate = hasS2xlate, onlyS2 = OnlyS2, onlyS1 = OnlyS1)
+        val hit = e._1.hit(vpn, Mux(hasS2xlate, io.csr.vsatp.asid, io.csr.satp.asid), vmid = io.csr.hgatp.vmid,
+          sdid = if (HasMptCheck) io.csr.mmpt.sdid else 0.U,
+          matchMpt = if (HasMptCheck) req.bits.matchMpt.get else false.B,
+          hasS2xlate = hasS2xlate, onlyS2 = OnlyS2, onlyS1 = OnlyS1)
         s2xlate_hit && hit && m._1 && !m._2
       }
     })
@@ -147,7 +150,7 @@ class TLBFA(
           resp.bits.mptperm.get(d).x := mptperm.get(0).x
           resp.bits.mptperm.get(d).w := mptperm.get(0).w
           resp.bits.mptperm.get(d).r := mptperm.get(0).r
-          resp.bits.mptperm.get(d).af.get := false.B
+          resp.bits.mptperm.get(d).af := hitVecReg(0) && mptperm.get(0).af
         }
       }
     } else {
@@ -160,12 +163,10 @@ class TLBFA(
         resp.bits.s2xlate(d) := Mux1H(hitVecReg zip s2xLate)
         if (HasMptCheck) {
           val mptpermtmp = Mux1H(hitVecReg zip mptperm.get)
-          if (HasMptCheck) {
-            resp.bits.mptperm.get(d).x := mptpermtmp.x
-            resp.bits.mptperm.get(d).w := mptpermtmp.w
-            resp.bits.mptperm.get(d).r := mptpermtmp.r
-            resp.bits.mptperm.get(d).af.get := false.B
-          }
+          resp.bits.mptperm.get(d).x := mptpermtmp.x
+          resp.bits.mptperm.get(d).w := mptpermtmp.w
+          resp.bits.mptperm.get(d).r := mptpermtmp.r
+          resp.bits.mptperm.get(d).af := mptpermtmp.af
         }
       }
     }
@@ -180,6 +181,7 @@ class TLBFA(
     resp.bits.g_pbmt.suggestName("g_pbmt")
     resp.bits.perm.suggestName("perm")
     resp.bits.g_perm.suggestName("g_perm")
+    hitVec
   }
 
   when (io.w.valid) {
@@ -187,7 +189,16 @@ class TLBFA(
     entries(io.w.bits.wayIdx).apply(io.w.bits.data)
   }
   // write assert, should not duplicate with the existing entries
-  val w_hit_vec = VecInit(entries.zip(v).map{case (e, vi) => e.wbhit(io.w.bits.data, Mux(io.w.bits.data.s2xlate =/= noS2xlate, io.csr.vsatp.asid, io.csr.satp.asid), io.csr.hgatp.vmid, s2xlate = io.w.bits.data.s2xlate) && vi })
+  val w_hit_vec = VecInit(entries.zip(v).map { case (e, vi) =>
+    e.wbhit(
+      io.w.bits.data,
+      Mux(io.w.bits.data.s2xlate =/= noS2xlate, io.csr.vsatp.asid, io.csr.satp.asid),
+      io.csr.hgatp.vmid,
+      s2xlate = io.w.bits.data.s2xlate,
+      sdid = if (HasMptCheck) io.w.bits.data.mpt.get.sdid else 0.U,
+      matchMpt = if (HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B
+    ) && vi
+  })
   XSError(io.w.valid && Cat(w_hit_vec).orR, s"${parentName} refill, duplicate with existing entries")
 
   val refill_vpn_reg = RegEnable(io.w.bits.data.s1.entry.tag, io.w.valid)
@@ -203,11 +214,14 @@ class TLBFA(
   val sfence = io.sfence
   val sfence_valid = sfence.valid && !sfence.bits.hg && !sfence.bits.hv && (if (HasMptCheck) !(sfence.bits.mfence.get) else true.B)
   val sfence_vpn = sfence.bits.addr(VAddrBits - 1, offLen)
-  val sfenceHit = entries.map(_.hit(sfence_vpn, sfence.bits.id, vmid = io.csr.hgatp.vmid, hasS2xlate = io.csr.priv.virt))
-  val sfenceHit_noasid = entries.map(_.hit(sfence_vpn, sfence.bits.id, ignoreAsid = true, vmid = io.csr.hgatp.vmid, hasS2xlate = io.csr.priv.virt))
+  // when mpt is enabled, sfence only clear entries with current sdid
+  val sfenceHit = entries.map(_.hit(sfence_vpn, sfence.bits.id, vmid = io.csr.hgatp.vmid, sdid = if(HasMptCheck) io.csr.mmpt.sdid else 0.U, matchMpt = if(HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B, hasS2xlate = io.csr.priv.virt))
+  val sfenceHit_noasid = entries.map(_.hit(sfence_vpn, sfence.bits.id, ignoreAsid = true, vmid = io.csr.hgatp.vmid, sdid = if(HasMptCheck) io.csr.mmpt.sdid else 0.U, matchMpt = if(HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B, hasS2xlate = io.csr.priv.virt))
+  val mptSfenceOverFence = if (HasMptCheck) io.csr.mmpt.mode =/= 0.U else false.B
   // Sfence will flush all sectors of an entry when hit
   when (sfence_valid) {
-    when (sfence.bits.rs1 || io.csr.priv.virt || (if (HasBitmapCheck) (io.csr.mbmc.BME === 1.U && io.csr.mbmc.CMODE === 0.U) else false.B)) { // virtual address *.rs1 <- (rs1===0.U)
+    when (sfence.bits.rs1 || io.csr.priv.virt || mptSfenceOverFence ||
+      (if (HasBitmapCheck) (io.csr.mbmc.BME === 1.U && io.csr.mbmc.CMODE === 0.U) else false.B)) { // virtual address *.rs1 <- (rs1===0.U)
       // Note: when virt=1, always flush all addr. See hfence.vvma comment.
       when (sfence.bits.rs2) { // asid, but i do not want to support asid, *.rs2 <- (rs2===0.U)
         // all addr and all asid
@@ -228,6 +242,22 @@ class TLBFA(
       }
     }
   }
+  if (HasMptCheck) {
+    val mfence_valid = sfence.valid && sfence.bits.mfence.get
+    val mfence_sdid = sfence.bits.id(sdidLen - 1, 0)
+    val mfenceRs2IsX0 = sfence.bits.rs2
+    when (mfence_valid) {
+      when (!mfenceRs2IsX0) {
+        // mfence with sdid target: flush all entries matching the sdid, regardless of address/pa form
+        v.zipWithIndex.map { case (a, i) =>
+          a := a && !(entries(i).sdid.get === mfence_sdid)
+        }
+      }.otherwise {
+        // mfence with x0 as the SDID operand: flush entries from all SDIDs
+        v.zipWithIndex.map { case (a, i) => a := false.B }
+      }
+    }
+  }
 
   val hfencev_valid = sfence.valid && sfence.bits.hv
   val hfenceg_valid = sfence.valid && sfence.bits.hg
@@ -290,14 +320,15 @@ class TLBFA(
       v.zipWithIndex.map { case (a, i) => a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === sfence.bits.id) }
     }
   }
+
   if (HasMptCheck) {
-    when(sfence.valid && sfence.bits.mfence.get) {
-      v.zipWithIndex.map { case (a, i) => a := false.B } // mfence reset all
-    }
-    val modechange = DataChanged(io.csr.satp.mode).asBool || DataChanged(io.csr.vsatp.mode).asBool ||
-      DataChanged(io.csr.hgatp.mode).asBool
-    when(modechange) {
-      v.zipWithIndex.map { case (a, i) => a := false.B } // mptonly to ptw reset all
+    for (way <- 0 until nWays) {
+      for (port <- 0 until ports) {
+        when (io.r.req(port).valid && hitVecs(port)(way) && entries(way).mptperm.get.af) {
+          // Keep the entry data for the current response selected by hitVecReg.
+          v(way) := false.B
+        }
+      }
     }
   }
 
@@ -439,7 +470,8 @@ class TlbStorageWrapper(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit p
       valid = io.r.req(i).valid,
       vpn = io.r.req(i).bits.vpn,
       i = i,
-      s2xlate = io.r.req(i).bits.s2xlate
+      s2xlate = io.r.req(i).bits.s2xlate,
+      matchMpt = if (HasMptCheck) io.r.req(i).bits.matchMpt.get else false.B
     )
   }
 
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index bb0e97284aa..f2513dc6527 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -713,7 +713,9 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   }
 
   val ptw_resp_next = RegEnable(ptwio.resp.bits, ptwio.resp.valid)
-  val ptw_resp_v = RegNext(ptwio.resp.valid && !(sfence.valid || tlbcsr.satp.changed || tlbcsr.vsatp.changed || tlbcsr.hgatp.changed || tlbcsr.priv.virt_changed), init = false.B)
+  val ptw_resp_v = RegNext(ptwio.resp.valid && !(sfence.valid || tlbcsr.satp.changed || tlbcsr.vsatp.changed ||
+    tlbcsr.hgatp.changed || tlbcsr.priv.virt_changed || (if (HasMptCheck) tlbcsr.mmpt.changed else false.B)),
+    init = false.B)
   ptwio.resp.ready := true.B
 
   val tlbreplay = WireInit(VecInit(Seq.fill(LdExuCnt)(false.B)))
```
