# Commit Log
- Issue: #3370
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3370
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3370
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3370
- Changed files: 29
- Additions: 5254
- Deletions: 4547

## Files
- `.github/workflows/format.yml`
- `.scalafmt.conf`
- `Makefile`
- `build.sc`
- `src/main/scala/xiangshan/frontend/BPU.scala`
- `src/main/scala/xiangshan/frontend/Bim.scala`
- `src/main/scala/xiangshan/frontend/Composer.scala`
- `src/main/scala/xiangshan/frontend/FTB.scala`
- `src/main/scala/xiangshan/frontend/FauFTB.scala`
- `src/main/scala/xiangshan/frontend/Frontend.scala`
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala`
- `src/main/scala/xiangshan/frontend/IBuffer.scala`
- `src/main/scala/xiangshan/frontend/IFU.scala`
- `src/main/scala/xiangshan/frontend/ITTAGE.scala`
- `src/main/scala/xiangshan/frontend/NewFtq.scala`
- `src/main/scala/xiangshan/frontend/PreDecode.scala`
- `src/main/scala/xiangshan/frontend/RAS.scala`
- `src/main/scala/xiangshan/frontend/SC.scala`
- `src/main/scala/xiangshan/frontend/Tage.scala`
- `src/main/scala/xiangshan/frontend/WrBypass.scala`
- `src/main/scala/xiangshan/frontend/icache/FIFO.scala`
- `src/main/scala/xiangshan/frontend/icache/ICache.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala`
- `src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala`
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`
- `src/main/scala/xiangshan/frontend/icache/InstrUncache.scala`
- `src/main/scala/xiangshan/frontend/icache/WayLookup.scala`
- `src/main/scala/xiangshan/frontend/newRAS.scala`

## Diff
```diff
diff --git a/.github/workflows/format.yml b/.github/workflows/format.yml
new file mode 100644
index 00000000000..52bd3716bca
--- /dev/null
+++ b/.github/workflows/format.yml
@@ -0,0 +1,23 @@
+name: Format Checking
+
+on: [push]
+
+jobs:
+  scalafmt:
+    runs-on: ubuntu-latest
+    continue-on-error: true
+    timeout-minutes: 900
+    name: Check Format
+    steps:
+      - uses: actions/checkout@v4
+        with:
+          submodules: 'recursive'
+      - uses: actions/setup-java@v4
+        with:
+          distribution: 'temurin'
+          java-version: '11'
+      - run: |
+          mkdir ~/.local/bin
+          sh -c "curl -L https://github.com/com-lihaoyi/mill/releases/download/0.11.7/0.11.7 > ~/.local/bin/mill && chmod +x ~/.local/bin/mill"
+          export PATH=~/.local/bin:$PATH
+      - run: make check-format
diff --git a/.scalafmt.conf b/.scalafmt.conf
new file mode 100644
index 00000000000..356da6a958c
--- /dev/null
+++ b/.scalafmt.conf
@@ -0,0 +1,26 @@
+version = 3.8.1
+runner.dialect = scala213
+maxColumn = 120
+preset = defaultWithAlign
+
+align.tokens."+" = [{
+  code = ":"
+}]
+
+newlines.source = keep
+newlines.afterCurlyLambdaParams = squash
+
+rewrite.rules = [
+  RedundantBraces,
+  RedundantParens,
+  SortModifiers,
+  Imports
+]
+rewrite.redundantBraces.generalExpressions = false
+rewrite.imports.expand = true
+rewrite.imports.sort = scalastyle
+rewrite.trailingCommas.style = never
+
+docstrings.style = keep
+
+project.includePaths = ["glob:**/src/main/scala/xiangshan/frontend/**.scala"]
diff --git a/Makefile b/Makefile
index 2a3dc704fbc..4bb99ed4ba8 100644
--- a/Makefile
+++ b/Makefile
@@ -209,6 +209,12 @@ bsp:
 idea:
 	mill -i mill.idea.GenIdea/idea
 
+check-format:
+	mill xiangshan.checkFormat
+
+reformat:
+	mill xiangshan.reformat
+
 # verilator simulation
 emu: sim-verilog
 	$(MAKE) -C ./difftest emu SIM_TOP=SimTop DESIGN_DIR=$(NOOP_HOME) NUM_CORES=$(NUM_CORES) RTL_SUFFIX=$(RTL_SUFFIX)
diff --git a/build.sc b/build.sc
index 1fc7727123b..89af9ccd3fe 100644
--- a/build.sc
+++ b/build.sc
@@ -17,6 +17,7 @@
 
 import mill._
 import scalalib._
+import scalafmt._
 import $file.`rocket-chip`.common
 import $file.`rocket-chip`.cde.common
 import $file.`rocket-chip`.hardfloat.build
@@ -229,7 +230,7 @@ trait XiangShanModule extends ScalaModule {
   override def forkEnv = Map("PATH" -> envPATH)
 }
 
-object xiangshan extends XiangShanModule with HasChisel {
+object xiangshan extends XiangShanModule with HasChisel with ScalafmtModule {
 
   override def millSourcePath = os.pwd
 
diff --git a/src/main/scala/xiangshan/frontend/BPU.scala b/src/main/scala/xiangshan/frontend/BPU.scala
index c10e73ef377..f7874457b58 100644
--- a/src/main/scala/xiangshan/frontend/BPU.scala
+++ b/src/main/scala/xiangshan/frontend/BPU.scala
@@ -16,25 +16,24 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
+import org.chipsalliance.cde.config.Parameters
 import scala.math.min
+import utility._
+import utils._
+import xiangshan._
 import xiangshan.backend.decode.ImmUnion
 
 trait HasBPUConst extends HasXSParameter {
-  val MaxMetaBaseLength =  if (!env.FPGAPlatform) 512 else 256 // TODO: Reduce meta length
-  val MaxMetaLength = if (HasHExtension) MaxMetaBaseLength + 4 else MaxMetaBaseLength
+  val MaxMetaBaseLength = if (!env.FPGAPlatform) 512 else 256 // TODO: Reduce meta length
+  val MaxMetaLength     = if (HasHExtension) MaxMetaBaseLength + 4 else MaxMetaBaseLength
   val MaxBasicBlockSize = 32
-  val LHistoryLength = 32
+  val LHistoryLength    = 32
   // val numBr = 2
-  val useBPD = true
-  val useLHist = true
-  val numBrSlot = numBr-1
+  val useBPD    = true
+  val useLHist  = true
+  val numBrSlot = numBr - 1
   val totalSlot = numBrSlot + 1
 
   val numDup = 4
@@ -43,14 +42,14 @@ trait HasBPUConst extends HasXSParameter {
   val pcSegments = Seq(VAddrBits - 24, 12, 12)
 
   def BP_STAGES = (0 until 3).map(_.U(2.W))
-  def BP_S1 = BP_STAGES(0)
-  def BP_S2 = BP_STAGES(1)
-  def BP_S3 = BP_STAGES(2)
+  def BP_S1     = BP_STAGES(0)
+  def BP_S2     = BP_STAGES(1)
+  def BP_S3     = BP_STAGES(2)
 
-  def dup_seq[T](src: T, num: Int = numDup) = Seq.tabulate(num)(n => src)
-  def dup[T <: Data](src: T, num: Int = numDup) = VecInit(Seq.tabulate(num)(n => src))
+  def dup_seq[T](src:          T, num: Int = numDup) = Seq.tabulate(num)(n => src)
+  def dup[T <: Data](src:      T, num: Int = numDup) = VecInit(Seq.tabulate(num)(n => src))
   def dup_wire[T <: Data](src: T, num: Int = numDup) = Wire(Vec(num, src.cloneType))
-  def dup_idx = Seq.tabulate(numDup)(n => n.toString())
+  def dup_idx     = Seq.tabulate(numDup)(n => n.toString())
   val numBpStages = BP_STAGES.length
 
   val debug = true
@@ -58,11 +57,11 @@ trait HasBPUConst extends HasXSParameter {
 }
 
 trait HasBPUParameter extends HasXSParameter with HasBPUConst {
-  val BPUDebug = true && !env.FPGAPlatform && env.EnablePerfDebug
-  val EnableCFICommitLog = true
-  val EnbaleCFIPredLog = true
+  val BPUDebug            = true && !env.FPGAPlatform && env.EnablePerfDebug
+  val EnableCFICommitLog  = true
+  val EnbaleCFIPredLog    = true
   val EnableBPUTimeRecord = (EnableCFICommitLog || EnbaleCFIPredLog) && !env.FPGAPlatform
-  val EnableCommit = false
+  val EnableCommit        = false
 }
 
 class BPUCtrl(implicit p: Parameters) extends XSBundle {
@@ -78,60 +77,58 @@ class BPUCtrl(implicit p: Parameters) extends XSBundle {
 trait BPUUtils extends HasXSParameter {
   // circular shifting
   def circularShiftLeft(source: UInt, len: Int, shamt: UInt): UInt = {
-    val res = Wire(UInt(len.W))
+    val res    = Wire(UInt(len.W))
     val higher = source << shamt
-    val lower = source >> (len.U - shamt)
+    val lower  = source >> (len.U - shamt)
     res := higher | lower
     res
   }
 
   def circularShiftRight(source: UInt, len: Int, shamt: UInt): UInt = {
-    val res = Wire(UInt(len.W))
+    val res    = Wire(UInt(len.W))
     val higher = source << (len.U - shamt)
-    val lower = source >> shamt
+    val lower  = source >> shamt
     res := higher | lower
     res
   }
 
   // To be verified
   def satUpdate(old: UInt, len: Int, taken: Bool): UInt = {
-    val oldSatTaken = old === ((1 << len)-1).U
+    val oldSatTaken    = old === ((1 << len) - 1).U
     val oldSatNotTaken = old === 0.U
-    Mux(oldSatTaken && taken, ((1 << len)-1).U,
-      Mux(oldSatNotTaken && !taken, 0.U,
-        Mux(taken, old + 1.U, old - 1.U)))
+    Mux(oldSatTaken && taken, ((1 << len) - 1).U, Mux(oldSatNotTaken && !taken, 0.U, Mux(taken, old + 1.U, old - 1.U)))
   }
 
   def signedSatUpdate(old: SInt, len: Int, taken: Bool): SInt = {
-    val oldSatTaken = old === ((1 << (len-1))-1).S
-    val oldSatNotTaken = old === (-(1 << (len-1))).S
-    Mux(oldSatTaken && taken, ((1 << (len-1))-1).S,
-      Mux(oldSatNotTaken && !taken, (-(1 << (len-1))).S,
-        Mux(taken, old + 1.S, old - 1.S)))
+    val oldSatTaken    = old === ((1 << (len - 1)) - 1).S
+    val oldSatNotTaken = old === (-(1 << (len - 1))).S
+    Mux(
+      oldSatTaken && taken,
+      ((1 << (len - 1)) - 1).S,
+      Mux(oldSatNotTaken && !taken, (-(1 << (len - 1))).S, Mux(taken, old + 1.S, old - 1.S))
+    )
   }
 
   def getFallThroughAddr(start: UInt, carry: Bool, pft: UInt) = {
-    val higher = start.head(VAddrBits-log2Ceil(PredictWidth)-instOffsetBits)
-    Cat(Mux(carry, higher+1.U, higher), pft, 0.U(instOffsetBits.W))
+    val higher = start.head(VAddrBits - log2Ceil(PredictWidth) - instOffsetBits)
+    Cat(Mux(carry, higher + 1.U, higher), pft, 0.U(instOffsetBits.W))
   }
 
   def foldTag(tag: UInt, l: Int): UInt = {
     val nChunks = (tag.getWidth + l - 1) / l
-    val chunks = (0 until nChunks).map { i =>
-      tag(min((i+1)*l, tag.getWidth)-1, i*l)
-    }
+    val chunks  = (0 until nChunks).map(i => tag(min((i + 1) * l, tag.getWidth) - 1, i * l))
     ParallelXOR(chunks)
   }
 }
 
-class BasePredictorInput (implicit p: Parameters) extends XSBundle with HasBPUConst {
+class BasePredictorInput(implicit p: Parameters) extends XSBundle with HasBPUConst {
   def nInputs = 1
 
   val s0_pc = Vec(numDup, UInt(VAddrBits.W))
 
-  val folded_hist = Vec(numDup, new AllFoldedHistories(foldedGHistInfos))
+  val folded_hist    = Vec(numDup, new AllFoldedHistories(foldedGHistInfos))
   val s1_folded_hist = Vec(numDup, new AllFoldedHistories(foldedGHistInfos))
-  val ghist = UInt(HistoryLength.W)
+  val ghist          = UInt(HistoryLength.W)
 
   val resp_in = Vec(nInputs, new BranchPredictionResp)
 
@@ -141,18 +138,18 @@ class BasePredictorInput (implicit p: Parameters) extends XSBundle with HasBPUCo
   // val s0_all_ready = Bool()
 }
 
-class BasePredictorOutput (implicit p: Parameters) extends BranchPredictionResp {}
+class BasePredictorOutput(implicit p: Parameters) extends BranchPredictionResp {}
 
-class BasePredictorIO (implicit p: Parameters) extends XSBundle with HasBPUConst {
+class BasePredictorIO(implicit p: Parameters) extends XSBundle with HasBPUConst {
   val reset_vector = Input(UInt(PAddrBits.W))
-  val in  = Flipped(DecoupledIO(new BasePredictorInput)) // TODO: Remove DecoupledIO
+  val in           = Flipped(DecoupledIO(new BasePredictorInput)) // TODO: Remove DecoupledIO
   // val out = DecoupledIO(new BasePredictorOutput)
   val out = Output(new BasePredictorOutput)
   // val flush_out = Valid(UInt(VAddrBits.W))
 
-  val fauftb_entry_in = Input(new FTBEntry)
-  val fauftb_entry_hit_in = Input(Bool())
-  val fauftb_entry_out = Output(new FTBEntry)
+  val fauftb_entry_in      = Input(new FTBEntry)
+  val fauftb_entry_hit_in  = Input(Bool())
+  val fauftb_entry_out     = Output(new FTBEntry)
   val fauftb_entry_hit_out = Output(Bool())
 
   val ctrl = Input(new BPUCtrl)
@@ -169,21 +166,21 @@ class BasePredictorIO (implicit p: Parameters) extends XSBundle with HasBPUConst
   val s2_ready = Output(Bool())
   val s3_ready = Output(Bool())
 
-  val update = Flipped(Valid(new BranchPredictionUpdate))
-  val redirect = Flipped(Valid(new BranchPredictionRedirect))
+  val update          = Flipped(Valid(new BranchPredictionUpdate))
+  val redirect        = Flipped(Valid(new BranchPredictionRedirect))
   val redirectFromIFU = Input(Bool())
 }
 
 abstract class BasePredictor(implicit p: Parameters) extends XSModule
-  with HasBPUConst with BPUUtils with HasPerfEvents {
-  val meta_size = 0
+    with HasBPUConst with BPUUtils with HasPerfEvents {
+  val meta_size      = 0
   val spec_meta_size = 0
-  val is_fast_pred = false
-  val io = IO(new BasePredictorIO())
+  val is_fast_pred   = false
+  val io             = IO(new BasePredictorIO())
 
   io.out := io.in.bits.resp_in(0)
 
-  io.fauftb_entry_out := io.fauftb_entry_in
+  io.fauftb_entry_out     := io.fauftb_entry_in
   io.fauftb_entry_hit_out := io.fauftb_entry_hit_in
 
   io.out.last_stage_meta := 0.U
@@ -194,13 +191,17 @@ abstract class BasePredictor(implicit p: Parameters) extends XSModule
   io.s2_ready := true.B
   io.s3_ready := true.B
 
-  val s0_pc_dup   = WireInit(io.in.bits.s0_pc) // fetchIdx(io.f0_pc)
-  val s1_pc_dup   = s0_pc_dup.zip(io.s0_fire).map {case (s0_pc, s0_fire) => RegEnable(s0_pc, s0_fire)}
-  val s2_pc_dup   = s1_pc_dup.zip(io.s1_fire).map {case (s1_pc, s1_fire) => SegmentedAddrNext(s1_pc, pcSegments, s1_fire, Some("s2_pc"))}
-  val s3_pc_dup   = s2_pc_dup.zip(io.s2_fire).map {case (s2_pc, s2_fire) => SegmentedAddrNext(s2_pc, s2_fire, Some("s3_pc"))}
+  val s0_pc_dup = WireInit(io.in.bits.s0_pc) // fetchIdx(io.f0_pc)
+  val s1_pc_dup = s0_pc_dup.zip(io.s0_fire).map { case (s0_pc, s0_fire) => RegEnable(s0_pc, s0_fire) }
+  val s2_pc_dup = s1_pc_dup.zip(io.s1_fire).map { case (s1_pc, s1_fire) =>
+    SegmentedAddrNext(s1_pc, pcSegments, s1_fire, Some("s2_pc"))
+  }
+  val s3_pc_dup = s2_pc_dup.zip(io.s2_fire).map { case (s2_pc, s2_fire) =>
+    SegmentedAddrNext(s2_pc, s2_fire, Some("s3_pc"))
+  }
 
-  when (RegNext(RegNext(reset.asBool) && !reset.asBool)) {
-    s1_pc_dup.map{case s1_pc => s1_pc := io.reset_vector}
+  when(RegNext(RegNext(reset.asBool) && !reset.asBool)) {
+    s1_pc_dup.map { case s1_pc => s1_pc := io.reset_vector }
   }
 
   io.out.s1.pc := s1_pc_dup
@@ -209,14 +210,13 @@ abstract class BasePredictor(implicit p: Parameters) extends XSModule
 
   val perfEvents: Seq[(String, UInt)] = Seq()
 
-
   def getFoldedHistoryInfo: Option[Set[FoldedHistoryInfo]] = None
 }
 
 class FakePredictor(implicit p: Parameters) extends BasePredictor {
-  io.in.ready                 := true.B
-  io.out.last_stage_meta      := 0.U
-  io.out := io.in.bits.resp_in(0)
+  io.in.ready            := true.B
+  io.out.last_stage_meta := 0.U
+  io.out                 := io.in.bits.resp_in(0)
 }
 
 class BpuToFtqIO(implicit p: Parameters) extends XSBundle {
@@ -224,16 +224,17 @@ class BpuToFtqIO(implicit p: Parameters) extends XSBundle {
 }
 
 class PredictorIO(implicit p: Parameters) extends XSBundle {
-  val bpu_to_ftq = new BpuToFtqIO()
-  val ftq_to_bpu = Flipped(new FtqToBpuIO)
-  val ctrl = Input(new BPUCtrl)
+  val bpu_to_ftq   = new BpuToFtqIO()
+  val ftq_to_bpu   = Flipped(new FtqToBpuIO)
+  val ctrl         = Input(new BPUCtrl)
   val reset_vector = Input(UInt(PAddrBits.W))
 }
 
-class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with HasPerfEvents with HasCircularQueuePtrHelper {
+class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with HasPerfEvents
+    with HasCircularQueuePtrHelper {
   val io = IO(new PredictorIO)
 
-  val ctrl = DelayN(io.ctrl, 1)
+  val ctrl       = DelayN(io.ctrl, 1)
   val predictors = Module(if (useBPD) new Composer else new FakePredictor)
 
   def numOfStage = 3
@@ -242,24 +243,24 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
 
   // following can only happen on s1
   val controlRedirectBubble = Wire(Bool())
-  val ControlBTBMissBubble = Wire(Bool())
-  val TAGEMissBubble = Wire(Bool())
-  val SCMissBubble = Wire(Bool())
-  val ITTAGEMissBubble = Wire(Bool())
-  val RASMissBubble = Wire(Bool())
+  val ControlBTBMissBubble  = Wire(Bool())
+  val TAGEMissBubble        = Wire(Bool())
+  val SCMissBubble          = Wire(Bool())
+  val ITTAGEMissBubble      = Wire(Bool())
+  val RASMissBubble         = Wire(Bool())
 
   val memVioRedirectBubble = Wire(Bool())
-  val otherRedirectBubble = Wire(Bool())
-  val btbMissBubble = Wire(Bool())
-  otherRedirectBubble := false.B
+  val otherRedirectBubble  = Wire(Bool())
+  val btbMissBubble        = Wire(Bool())
+  otherRedirectBubble  := false.B
   memVioRedirectBubble := false.B
 
   // override can happen between s1-s2 and s2-s3
   val overrideBubble = Wire(Vec(numOfStage - 1, Bool()))
-  def overrideStage = 1
+  def overrideStage  = 1
   // ftq update block can happen on s1, s2 and s3
   val ftqUpdateBubble = Wire(Vec(numOfStage, Bool()))
-  def ftqUpdateStage = 0
+  def ftqUpdateStage  = 0
   // ftq full stall only happens on s3 (last stage)
   val ftqFullStall = Wire(Bool())
 
@@ -270,37 +271,35 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
     topdown_stages(i + 1) := topdown_stages(i)
   }
 
-
-
   // ctrl signal
-  predictors.io.ctrl := ctrl
+  predictors.io.ctrl         := ctrl
   predictors.io.reset_vector := io.reset_vector
 
   val s0_stall_dup = dup_wire(Bool()) // For some reason s0 stalled, usually FTQ Full
-  val s0_fire_dup, s1_fire_dup, s2_fire_dup, s3_fire_dup = dup_wire(Bool())
-  val s1_valid_dup, s2_valid_dup, s3_valid_dup = dup_seq(RegInit(false.B))
-  val s1_ready_dup, s2_ready_dup, s3_ready_dup = dup_wire(Bool())
+  val s0_fire_dup, s1_fire_dup, s2_fire_dup, s3_fire_dup                        = dup_wire(Bool())
+  val s1_valid_dup, s2_valid_dup, s3_valid_dup                                  = dup_seq(RegInit(false.B))
+  val s1_ready_dup, s2_ready_dup, s3_ready_dup                                  = dup_wire(Bool())
   val s1_components_ready_dup, s2_components_ready_dup, s3_components_ready_dup = dup_wire(Bool())
 
-  val s0_pc_dup = dup(WireInit(0.U.asTypeOf(UInt(VAddrBits.W))))
-  val s0_pc_reg_dup = s0_pc_dup.zip(s0_stall_dup).map{ case (s0_pc, s0_stall) => RegEnable(s0_pc, !s0_stall) }
-  when (RegNext(RegNext(reset.asBool) && !reset.asBool)) {
-    s0_pc_reg_dup.map{case s0_pc => s0_pc := io.reset_vector}
+  val s0_pc_dup     = dup(WireInit(0.U.asTypeOf(UInt(VAddrBits.W))))
+  val s0_pc_reg_dup = s0_pc_dup.zip(s0_stall_dup).map { case (s0_pc, s0_stall) => RegEnable(s0_pc, !s0_stall) }
+  when(RegNext(RegNext(reset.asBool) && !reset.asBool)) {
+    s0_pc_reg_dup.map { case s0_pc => s0_pc := io.reset_vector }
   }
   val s1_pc = RegEnable(s0_pc_dup(0), s0_fire_dup(0))
   val s2_pc = RegEnable(s1_pc, s1_fire_dup(0))
   val s3_pc = RegEnable(s2_pc, s2_fire_dup(0))
 
   val s0_folded_gh_dup = dup_wire(new AllFoldedHistories(foldedGHistInfos))
-  val s0_folded_gh_reg_dup = s0_folded_gh_dup.zip(s0_stall_dup).map{
+  val s0_folded_gh_reg_dup = s0_folded_gh_dup.zip(s0_stall_dup).map {
     case (x, s0_stall) => RegEnable(x, 0.U.asTypeOf(s0_folded_gh_dup(0)), !s0_stall)
   }
   val s1_folded_gh_dup = RegEnable(s0_folded_gh_dup, 0.U.asTypeOf(s0_folded_gh_dup), s0_fire_dup(1))
   val s2_folded_gh_dup = RegEnable(s1_folded_gh_dup, 0.U.asTypeOf(s0_folded_gh_dup), s1_fire_dup(1))
   val s3_folded_gh_dup = RegEnable(s2_folded_gh_dup, 0.U.asTypeOf(s0_folded_gh_dup), s2_fire_dup(1))
 
-  val s0_last_br_num_oh_dup = dup_wire(UInt((numBr+1).W))
-  val s0_last_br_num_oh_reg_dup = s0_last_br_num_oh_dup.zip(s0_stall_dup).map{
+  val s0_last_br_num_oh_dup = dup_wire(UInt((numBr + 1).W))
+  val s0_last_br_num_oh_reg_dup = s0_last_br_num_oh_dup.zip(s0_stall_dup).map {
     case (x, s0_stall) => RegEnable(x, 0.U, !s0_stall)
   }
   val s1_last_br_num_oh_dup = RegEnable(s0_last_br_num_oh_dup, 0.U.asTypeOf(s0_last_br_num_oh_dup), s0_fire_dup(1))
@@ -308,12 +307,15 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   val s3_last_br_num_oh_dup = RegEnable(s2_last_br_num_oh_dup, 0.U.asTypeOf(s0_last_br_num_oh_dup), s2_fire_dup(1))
 
   val s0_ahead_fh_oldest_bits_dup = dup_wire(new AllAheadFoldedHistoryOldestBits(foldedGHistInfos))
-  val s0_ahead_fh_oldest_bits_reg_dup = s0_ahead_fh_oldest_bits_dup.zip(s0_stall_dup).map{
+  val s0_ahead_fh_oldest_bits_reg_dup = s0_ahead_fh_oldest_bits_dup.zip(s0_stall_dup).map {
     case (x, s0_stall) => RegEnable(x, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup(0)), !s0_stall)
   }
-  val s1_ahead_fh_oldest_bits_dup = RegEnable(s0_ahead_fh_oldest_bits_dup, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup), s0_fire_dup(1))
-  val s2_ahead_fh_oldest_bits_dup = RegEnable(s1_ahead_fh_oldest_bits_dup, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup), s1_fire_dup(1))
-  val s3_ahead_fh_oldest_bits_dup = RegEnable(s2_ahead_fh_oldest_bits_dup, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup), s2_fire_dup(1))
+  val s1_ahead_fh_oldest_bits_dup =
+    RegEnable(s0_ahead_fh_oldest_bits_dup, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup), s0_fire_dup(1))
+  val s2_ahead_fh_oldest_bits_dup =
+    RegEnable(s1_ahead_fh_oldest_bits_dup, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup), s1_fire_dup(1))
+  val s3_ahead_fh_oldest_bits_dup =
+    RegEnable(s2_ahead_fh_oldest_bits_dup, 0.U.asTypeOf(s0_ahead_fh_oldest_bits_dup), s2_fire_dup(1))
 
   val npcGen_dup         = Seq.tabulate(numDup)(n => new PhyPriorityMuxGenerator[UInt])
   val foldedGhGen_dup    = Seq.tabulate(numDup)(n => new PhyPriorityMuxGenerator[AllFoldedHistories])
@@ -324,51 +326,49 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   val ghvBitWriteGens = Seq.tabulate(HistoryLength)(n => new PhyPriorityMuxGenerator[Bool])
   // val ghistGen = new PhyPriorityMuxGenerator[UInt]
 
-  val ghv = RegInit(0.U.asTypeOf(Vec(HistoryLength, Bool())))
+  val ghv      = RegInit(0.U.asTypeOf(Vec(HistoryLength, Bool())))
   val ghv_wire = WireInit(ghv)
 
   val s0_ghist = WireInit(0.U.asTypeOf(UInt(HistoryLength.W)))
 
-
   println(f"history buffer length ${HistoryLength}")
   val ghv_write_datas = Wire(Vec(HistoryLength, Bool()))
-  val ghv_wens = Wire(Vec(HistoryLength, Bool()))
+  val ghv_wens        = Wire(Vec(HistoryLength, Bool()))
 
   val s0_ghist_ptr_dup = dup_wire(new CGHPtr)
-  val s0_ghist_ptr_reg_dup = s0_ghist_ptr_dup.zip(s0_stall_dup).map{
+  val s0_ghist_ptr_reg_dup = s0_ghist_ptr_dup.zip(s0_stall_dup).map {
     case (x, s0_stall) => RegEnable(x, 0.U.asTypeOf(new CGHPtr), !s0_stall)
   }
   val s1_ghist_ptr_dup = RegEnable(s0_ghist_ptr_dup, 0.U.asTypeOf(s0_ghist_ptr_dup), s0_fire_dup(1))
   val s2_ghist_ptr_dup = RegEnable(s1_ghist_ptr_dup, 0.U.asTypeOf(s0_ghist_ptr_dup), s1_fire_dup(1))
   val s3_ghist_ptr_dup = RegEnable(s2_ghist_ptr_dup, 0.U.asTypeOf(s0_ghist_ptr_dup), s2_fire_dup(1))
 
-  def getHist(ptr: CGHPtr): UInt = (Cat(ghv_wire.asUInt, ghv_wire.asUInt) >> (ptr.value+1.U))(HistoryLength-1, 0)
+  def getHist(ptr: CGHPtr): UInt = (Cat(ghv_wire.asUInt, ghv_wire.asUInt) >> (ptr.value + 1.U))(HistoryLength - 1, 0)
   s0_ghist := getHist(s0_ghist_ptr_dup(0))
 
   val resp = predictors.io.out
 
-
   val toFtq_fire = io.bpu_to_ftq.resp.valid && io.bpu_to_ftq.resp.ready
 
   val s1_flush_dup, s2_flush_dup, s3_flush_dup = dup_wire(Bool())
-  val s2_redirect_dup, s3_redirect_dup = dup_wire(Bool())
+  val s2_redirect_dup, s3_redirect_dup         = dup_wire(Bool())
 
   // predictors.io := DontCare
-  predictors.io.in.valid := s0_fire_dup(0)
-  predictors.io.in.bits.s0_pc := s0_pc_dup
-  predictors.io.in.bits.ghist := s0_ghist
-  predictors.io.in.bits.folded_hist := s0_folded_gh_dup
+  predictors.io.in.valid               := s0_fire_dup(0)
+  predictors.io.in.bits.s0_pc          := s0_pc_dup
+  predictors.io.in.bits.ghist          := s0_ghist
+  predictors.io.in.bits.folded_hist    := s0_folded_gh_dup
   predictors.io.in.bits.s1_folded_hist := s1_folded_gh_dup
-  predictors.io.in.bits.resp_in(0) := (0.U).asTypeOf(new BranchPredictionResp)
-  predictors.io.fauftb_entry_in := (0.U).asTypeOf(new FTBEntry)
-  predictors.io.fauftb_entry_hit_in := false.B
-  predictors.io.redirectFromIFU := RegNext(io.ftq_to_bpu.redirctFromIFU, init=false.B)
+  predictors.io.in.bits.resp_in(0)     := 0.U.asTypeOf(new BranchPredictionResp)
+  predictors.io.fauftb_entry_in        := 0.U.asTypeOf(new FTBEntry)
+  predictors.io.fauftb_entry_hit_in    := false.B
+  predictors.io.redirectFromIFU        := RegNext(io.ftq_to_bpu.redirctFromIFU, init = false.B)
   // predictors.io.in.bits.resp_in(0).s1.pc := s0_pc
   // predictors.io.in.bits.toFtq_fire := toFtq_fire
 
   // predictors.io.out.ready := io.bpu_to_ftq.resp.ready
 
-  val redirect_req = io.ftq_to_bpu.redirect
+  val redirect_req    = io.ftq_to_bpu.redirect
   val do_redirect_dup = dup_seq(RegNextWithEnable(redirect_req))
 
   // Pipeline logic
@@ -381,83 +381,101 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   for (((s1_flush, s2_flush), s2_redirect) <- s1_flush_dup zip s2_flush_dup zip s2_redirect_dup)
     s1_flush := s2_flush || s2_redirect
 
-
   s1_components_ready_dup.map(_ := predictors.io.s1_ready)
   for (((s1_ready, s1_fire), s1_valid) <- s1_ready_dup zip s1_fire_dup zip s1_valid_dup)
     s1_ready := s1_fire || !s1_valid
   for (((s0_fire, s1_components_ready), s1_ready) <- s0_fire_dup zip s1_components_ready_dup zip s1_ready_dup)
-    s0_fire := s1_components_ready && s1_ready
+    s0_fire             := s1_components_ready && s1_ready
   predictors.io.s0_fire := s0_fire_dup
 
   s2_components_ready_dup.map(_ := predictors.io.s2_ready)
   for (((s2_ready, s2_fire), s2_valid) <- s2_ready_dup zip s2_fire_dup zip s2_valid_dup)
     s2_ready := s2_fire || !s2_valid
-  for ((((s1_fire, s2_components_ready), s2_ready), s1_valid) <- s1_fire_dup zip s2_components_ready_dup zip s2_ready_dup zip s1_valid_dup)
+  for (
+    (((s1_fire, s2_components_ready), s2_ready), s1_valid) <-
+      s1_fire_dup zip s2_components_ready_dup zip s2_ready_dup zip s1_valid_dup
+  )
     s1_fire := s1_valid && s2_components_ready && s2_ready && io.bpu_to_ftq.resp.ready
 
   s3_components_ready_dup.map(_ := predictors.io.s3_ready)
   for (((s3_ready, s3_fire), s3_valid) <- s3_ready_dup zip s3_fire_dup zip s3_valid_dup)
     s3_ready := s3_fire || !s3_valid
-  for ((((s2_fire, s3_components_ready), s3_ready), s2_valid) <- s2_fire_dup zip s3_components_ready_dup zip s3_ready_dup zip s2_valid_dup)
+  for (
+    (((s2_fire, s3_components_ready), s3_ready), s2_valid) <-
+      s2_fire_dup zip s3_components_ready_dup zip s3_ready_dup zip s2_valid_dup
+  )
     s2_fire := s2_valid && s3_components_ready && s3_ready
 
   for ((((s0_fire, s1_flush), s1_fire), s1_valid) <- s0_fire_dup zip s1_flush_dup zip s1_fire_dup zip s1_valid_dup) {
-    when (redirect_req.valid) { s1_valid := false.B }
-      .elsewhen(s0_fire)      { s1_valid := true.B  }
-      .elsewhen(s1_flush)     { s1_valid := false.B }
-      .elsewhen(s1_fire)      { s1_valid := false.B }
+    when(redirect_req.valid)(s1_valid := false.B)
+      .elsewhen(s0_fire)(s1_valid := true.B)
+      .elsewhen(s1_flush)(s1_valid := false.B)
+      .elsewhen(s1_fire)(s1_valid := false.B)
   }
   predictors.io.s1_fire := s1_fire_dup
 
   s2_fire_dup := s2_valid_dup
 
-  for (((((s1_fire, s2_flush), s2_fire), s2_valid), s1_flush) <-
-    s1_fire_dup zip s2_flush_dup zip s2_fire_dup zip s2_valid_dup zip s1_flush_dup) {
+  for (
+    ((((s1_fire, s2_flush), s2_fire), s2_valid), s1_flush) <-
+      s1_fire_dup zip s2_flush_dup zip s2_fire_dup zip s2_valid_dup zip s1_flush_dup
+  ) {
 
-    when (s2_flush)      { s2_valid := false.B   }
-      .elsewhen(s1_fire) { s2_valid := !s1_flush }
-      .elsewhen(s2_fire) { s2_valid := false.B   }
+    when(s2_flush)(s2_valid := false.B)
+      .elsewhen(s1_fire)(s2_valid := !s1_flush)
+      .elsewhen(s2_fire)(s2_valid := false.B)
   }
 
-  predictors.io.s2_fire := s2_fire_dup
+  predictors.io.s2_fire     := s2_fire_dup
   predictors.io.s2_redirect := s2_redirect_dup
 
   s3_fire_dup := s3_valid_dup
 
-  for (((((s2_fire, s3_flush), s3_fire), s3_valid), s2_flush) <-
-    s2_fire_dup zip s3_flush_dup zip s3_fire_dup zip s3_valid_dup zip s2_flush_dup) {
+  for (
+    ((((s2_fire, s3_flush), s3_fire), s3_valid), s2_flush) <-
+      s2_fire_dup zip s3_flush_dup zip s3_fire_dup zip s3_valid_dup zip s2_flush_dup
+  ) {
 
-    when (s3_flush)      { s3_valid := false.B   }
-      .elsewhen(s2_fire) { s3_valid := !s2_flush }
-      .elsewhen(s3_fire) { s3_valid := false.B   }
+    when(s3_flush)(s3_valid := false.B)
+      .elsewhen(s2_fire)(s3_valid := !s2_flush)
+      .elsewhen(s3_fire)(s3_valid := false.B)
   }
 
-  predictors.io.s3_fire := s3_fire_dup
+  predictors.io.s3_fire     := s3_fire_dup
   predictors.io.s3_redirect := s3_redirect_dup
 
-
   io.bpu_to_ftq.resp.valid :=
     s1_valid_dup(2) && s2_components_ready_dup(2) && s2_ready_dup(2) ||
-    s2_fire_dup(2) && s2_redirect_dup(2) ||
-    s3_fire_dup(2) && s3_redirect_dup(2)
-  io.bpu_to_ftq.resp.bits  := predictors.io.out
-  io.bpu_to_ftq.resp.bits.last_stage_spec_info.histPtr     := s3_ghist_ptr_dup(2)
+      s2_fire_dup(2) && s2_redirect_dup(2) ||
+      s3_fire_dup(2) && s3_redirect_dup(2)
+  io.bpu_to_ftq.resp.bits                              := predictors.io.out
+  io.bpu_to_ftq.resp.bits.last_stage_spec_info.histPtr := s3_ghist_ptr_dup(2)
 
-  val full_pred_diff = WireInit(false.B)
-  val full_pred_diff_stage = WireInit(0.U)
+  val full_pred_diff        = WireInit(false.B)
+  val full_pred_diff_stage  = WireInit(0.U)
   val full_pred_diff_offset = WireInit(0.U)
   for (i <- 0 until numDup - 1) {
-    when (io.bpu_to_ftq.resp.valid &&
-      ((io.bpu_to_ftq.resp.bits.s1.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s1.full_pred(i+1).asTypeOf(UInt()) && io.bpu_to_ftq.resp.bits.s1.full_pred(i).hit) ||
-          (io.bpu_to_ftq.resp.bits.s2.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s2.full_pred(i+1).asTypeOf(UInt()) && io.bpu_to_ftq.resp.bits.s2.full_pred(i).hit) ||
-          (io.bpu_to_ftq.resp.bits.s3.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s3.full_pred(i+1).asTypeOf(UInt()) && io.bpu_to_ftq.resp.bits.s3.full_pred(i).hit))) {
-      full_pred_diff := true.B
+    when(io.bpu_to_ftq.resp.valid &&
+      ((io.bpu_to_ftq.resp.bits.s1.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s1.full_pred(
+        i + 1
+      ).asTypeOf(UInt()) && io.bpu_to_ftq.resp.bits.s1.full_pred(i).hit) ||
+        (io.bpu_to_ftq.resp.bits.s2.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s2.full_pred(
+          i + 1
+        ).asTypeOf(UInt()) && io.bpu_to_ftq.resp.bits.s2.full_pred(i).hit) ||
+        (io.bpu_to_ftq.resp.bits.s3.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s3.full_pred(
+          i + 1
+        ).asTypeOf(UInt()) && io.bpu_to_ftq.resp.bits.s3.full_pred(i).hit))) {
+      full_pred_diff        := true.B
       full_pred_diff_offset := i.U
-      when (io.bpu_to_ftq.resp.bits.s1.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s1.full_pred(i+1).asTypeOf(UInt())) {
+      when(io.bpu_to_ftq.resp.bits.s1.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s1.full_pred(
+        i + 1
+      ).asTypeOf(UInt())) {
         full_pred_diff_stage := 1.U
-      } .elsewhen (io.bpu_to_ftq.resp.bits.s2.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s2.full_pred(i+1).asTypeOf(UInt())) {
+      }.elsewhen(io.bpu_to_ftq.resp.bits.s2.full_pred(i).asTypeOf(UInt()) =/= io.bpu_to_ftq.resp.bits.s2.full_pred(
+        i + 1
+      ).asTypeOf(UInt())) {
         full_pred_diff_stage := 2.U
-      } .otherwise {
+      }.otherwise {
         full_pred_diff_stage := 3.U
       }
     }
@@ -476,86 +494,109 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
     // When BPU pipeline first time fire, we consider power-on reset is done
     powerOnResetState := false.B
   }
-  XSError(!powerOnResetState && s0_stall_dup(0) && s0_pc_dup(0) =/= s0_pc_reg_dup(0), "s0_stall but s0_pc is differenct from s0_pc_reg")
-
-  npcGen_dup.zip(s0_pc_reg_dup).map{ case (gen, reg) =>
-    gen.register(true.B, reg, Some("stallPC"), 0)}
-  foldedGhGen_dup.zip(s0_folded_gh_reg_dup).map{ case (gen, reg) =>
-    gen.register(true.B, reg, Some("stallFGH"), 0)}
-  ghistPtrGen_dup.zip(s0_ghist_ptr_reg_dup).map{ case (gen, reg) =>
-    gen.register(true.B, reg, Some("stallGHPtr"), 0)}
-  lastBrNumOHGen_dup.zip(s0_last_br_num_oh_reg_dup).map{ case (gen, reg) =>
-    gen.register(true.B, reg, Some("stallBrNumOH"), 0)}
-  aheadFhObGen_dup.zip(s0_ahead_fh_oldest_bits_reg_dup).map{ case (gen, reg) =>
-    gen.register(true.B, reg, Some("stallAFHOB"), 0)}
+  XSError(
+    !powerOnResetState && s0_stall_dup(0) && s0_pc_dup(0) =/= s0_pc_reg_dup(0),
+    "s0_stall but s0_pc is differenct from s0_pc_reg"
+  )
+
+  npcGen_dup.zip(s0_pc_reg_dup).map { case (gen, reg) =>
+    gen.register(true.B, reg, Some("stallPC"), 0)
+  }
+  foldedGhGen_dup.zip(s0_folded_gh_reg_dup).map { case (gen, reg) =>
+    gen.register(true.B, reg, Some("stallFGH"), 0)
+  }
+  ghistPtrGen_dup.zip(s0_ghist_ptr_reg_dup).map { case (gen, reg) =>
+    gen.register(true.B, reg, Some("stallGHPtr"), 0)
+  }
+  lastBrNumOHGen_dup.zip(s0_last_br_num_oh_reg_dup).map { case (gen, reg) =>
+    gen.register(true.B, reg, Some("stallBrNumOH"), 0)
+  }
+  aheadFhObGen_dup.zip(s0_ahead_fh_oldest_bits_reg_dup).map { case (gen, reg) =>
+    gen.register(true.B, reg, Some("stallAFHOB"), 0)
+  }
 
   // assign pred cycle for profiling
   io.bpu_to_ftq.resp.bits.s1.full_pred.map(_.predCycle.map(_ := GTimer()))
   io.bpu_to_ftq.resp.bits.s2.full_pred.map(_.predCycle.map(_ := GTimer()))
   io.bpu_to_ftq.resp.bits.s3.full_pred.map(_.predCycle.map(_ := GTimer()))
 
-
-
   // History manage
   // s1
   val s1_possible_predicted_ghist_ptrs_dup = s1_ghist_ptr_dup.map(ptr => (0 to numBr).map(ptr - _.U))
-  val s1_predicted_ghist_ptr_dup = s1_possible_predicted_ghist_ptrs_dup.zip(resp.s1.lastBrPosOH).map{ case (ptr, oh) => Mux1H(oh, ptr)}
+  val s1_predicted_ghist_ptr_dup = s1_possible_predicted_ghist_ptrs_dup.zip(resp.s1.lastBrPosOH).map { case (ptr, oh) =>
+    Mux1H(oh, ptr)
+  }
   val s1_possible_predicted_fhs_dup =
-    for (((((fgh, afh), br_num_oh), t), br_pos_oh) <-
-      s1_folded_gh_dup zip s1_ahead_fh_oldest_bits_dup zip s1_last_br_num_oh_dup zip resp.s1.brTaken zip resp.s1.lastBrPosOH)
+    for (
+      ((((fgh, afh), br_num_oh), t), br_pos_oh) <-
+        s1_folded_gh_dup zip s1_ahead_fh_oldest_bits_dup zip s1_last_br_num_oh_dup zip resp.s1.brTaken zip resp.s1.lastBrPosOH
+    )
       yield (0 to numBr).map(i =>
         fgh.update(afh, br_num_oh, i, t & br_pos_oh(i))
       )
-  val s1_predicted_fh_dup = resp.s1.lastBrPosOH.zip(s1_possible_predicted_fhs_dup).map{ case (oh, fh) => Mux1H(oh, fh)}
+  val s1_predicted_fh_dup = resp.s1.lastBrPosOH.zip(s1_possible_predicted_fhs_dup).map { case (oh, fh) =>
+    Mux1H(oh, fh)
+  }
 
   val s1_ahead_fh_ob_src_dup = dup_wire(new AllAheadFoldedHistoryOldestBits(foldedGHistInfos))
-  s1_ahead_fh_ob_src_dup.zip(s1_ghist_ptr_dup).map{ case (src, ptr) => src.read(ghv, ptr)}
+  s1_ahead_fh_ob_src_dup.zip(s1_ghist_ptr_dup).map { case (src, ptr) => src.read(ghv, ptr) }
 
   if (EnableGHistDiff) {
     val s1_predicted_ghist = WireInit(getHist(s1_predicted_ghist_ptr_dup(0)).asTypeOf(Vec(HistoryLength, Bool())))
     for (i <- 0 until numBr) {
-      when (resp.s1.shouldShiftVec(0)(i)) {
-        s1_predicted_ghist(i) := resp.s1.brTaken(0) && (i==0).B
+      when(resp.s1.shouldShiftVec(0)(i)) {
+        s1_predicted_ghist(i) := resp.s1.brTaken(0) && (i == 0).B
       }
     }
-    when (s1_valid_dup(0)) {
+    when(s1_valid_dup(0)) {
       s0_ghist := s1_predicted_ghist.asUInt
     }
   }
 
   val s1_ghv_wens = (0 until HistoryLength).map(n =>
-    (0 until numBr).map(b => (s1_ghist_ptr_dup(0)).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s1.shouldShiftVec(0)(b) && s1_valid_dup(0)))
+    (0 until numBr).map(b =>
+      s1_ghist_ptr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s1.shouldShiftVec(0)(b) && s1_valid_dup(
+        0
+      )
+    )
+  )
   val s1_ghv_wdatas = (0 until HistoryLength).map(n =>
     Mux1H(
-      (0 until numBr).map(b => (
-        (s1_ghist_ptr_dup(0)).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s1.shouldShiftVec(0)(b),
-        resp.s1.brTaken(0) && resp.s1.lastBrPosOH(0)(b+1)
-      ))
+      (0 until numBr).map(b =>
+        (
+          s1_ghist_ptr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s1.shouldShiftVec(0)(b),
+          resp.s1.brTaken(0) && resp.s1.lastBrPosOH(0)(b + 1)
+        )
+      )
     )
   )
 
-
   for (((npcGen, s1_valid), s1_target) <- npcGen_dup zip s1_valid_dup zip resp.s1.getTarget)
     npcGen.register(s1_valid, s1_target, Some("s1_target"), 4)
   for (((foldedGhGen, s1_valid), s1_predicted_fh) <- foldedGhGen_dup zip s1_valid_dup zip s1_predicted_fh_dup)
     foldedGhGen.register(s1_valid, s1_predicted_fh, Some("s1_FGH"), 4)
-  for (((ghistPtrGen, s1_valid), s1_predicted_ghist_ptr) <- ghistPtrGen_dup zip s1_valid_dup zip s1_predicted_ghist_ptr_dup)
+  for (
+    ((ghistPtrGen, s1_valid), s1_predicted_ghist_ptr) <- ghistPtrGen_dup zip s1_valid_dup zip s1_predicted_ghist_ptr_dup
+  )
     ghistPtrGen.register(s1_valid, s1_predicted_ghist_ptr, Some("s1_GHPtr"), 4)
-  for (((lastBrNumOHGen, s1_valid), s1_brPosOH) <- lastBrNumOHGen_dup zip s1_valid_dup zip resp.s1.lastBrPosOH.map(_.asUInt))
+  for (
+    ((lastBrNumOHGen, s1_valid), s1_brPosOH) <-
+      lastBrNumOHGen_dup zip s1_valid_dup zip resp.s1.lastBrPosOH.map(_.asUInt)
+  )
     lastBrNumOHGen.register(s1_valid, s1_brPosOH, Some("s1_BrNumOH"), 4)
   for (((aheadFhObGen, s1_valid), s1_ahead_fh_ob_src) <- aheadFhObGen_dup zip s1_valid_dup zip s1_ahead_fh_ob_src_dup)
     aheadFhObGen.register(s1_valid, s1_ahead_fh_ob_src, Some("s1_AFHOB"), 4)
-  ghvBitWriteGens.zip(s1_ghv_wens).zipWithIndex.map{case ((b, w), i) =>
-    b.register(w.reduce(_||_), s1_ghv_wdatas(i), Some(s"s1_new_bit_$i"), 4)
+  ghvBitWriteGens.zip(s1_ghv_wens).zipWithIndex.map { case ((b, w), i) =>
+    b.register(w.reduce(_ || _), s1_ghv_wdatas(i), Some(s"s1_new_bit_$i"), 4)
   }
 
   class PreviousPredInfo extends Bundle {
-    val hit = Vec(numDup, Bool())
-    val target = Vec(numDup, UInt(VAddrBits.W))
-    val lastBrPosOH = Vec(numDup, Vec(numBr+1, Bool()))
-    val taken = Vec(numDup, Bool())
-    val takenMask = Vec(numDup, Vec(numBr, Bool()))
-    val cfiIndex = Vec(numDup, UInt(log2Ceil(PredictWidth).W))
+    val hit         = Vec(numDup, Bool())
+    val target      = Vec(numDup, UInt(VAddrBits.W))
+    val lastBrPosOH = Vec(numDup, Vec(numBr + 1, Bool()))
+    val taken       = Vec(numDup, Bool())
+    val takenMask   = Vec(numDup, Vec(numBr, Bool()))
+    val cfiIndex    = Vec(numDup, UInt(log2Ceil(PredictWidth).W))
   }
 
   def preds_needs_redirect_vec_dup(x: PreviousPredInfo, y: BranchPredictionBundle) = {
@@ -567,18 +608,24 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
       x.target.zip(y.getAllTargets).map {
         case (xTarget, yAllTarget) => VecInit(yAllTarget.map(_ =/= xTarget))
       } // [numDup][all Target comparison]
-    val targetDiff   : IndexedSeq[Bool]      =
+    val targetDiff: IndexedSeq[Bool] =
       targetDiffVec.zip(x.hit).zip(x.takenMask).map {
         case ((diff, hit), takenMask) => selectByTaken(takenMask, hit, diff)
       } // [numDup]
 
-    val lastBrPosOHDiff: IndexedSeq[Bool]      = x.lastBrPosOH.zip(y.lastBrPosOH).map { case (oh1, oh2) => oh1.asUInt =/= oh2.asUInt }
-    val takenDiff      : IndexedSeq[Bool]      = x.taken.zip(y.taken).map { case (t1, t2) => t1 =/= t2 }
-    val takenOffsetDiff: IndexedSeq[Bool]      = x.cfiIndex.zip(y.cfiIndex).zip(x.taken).zip(y.taken).map { case (((i1, i2), xt), yt) => xt && yt && i1 =/= i2.bits }
+    val lastBrPosOHDiff: IndexedSeq[Bool] = x.lastBrPosOH.zip(y.lastBrPosOH).map { case (oh1, oh2) =>
+      oh1.asUInt =/= oh2.asUInt
+    }
+    val takenDiff: IndexedSeq[Bool] = x.taken.zip(y.taken).map { case (t1, t2) => t1 =/= t2 }
+    val takenOffsetDiff: IndexedSeq[Bool] = x.cfiIndex.zip(y.cfiIndex).zip(x.taken).zip(y.taken).map {
+      case (((i1, i2), xt), yt) => xt && yt && i1 =/= i2.bits
+    }
     VecInit(
-      for ((((tgtd, lbpohd), tkd), tod) <-
-             targetDiff zip lastBrPosOHDiff zip takenDiff zip takenOffsetDiff)
-      yield VecInit(tgtd, lbpohd, tkd, tod)
+      for (
+        (((tgtd, lbpohd), tkd), tod) <-
+          targetDiff zip lastBrPosOHDiff zip takenDiff zip takenOffsetDiff
+      )
+        yield VecInit(tgtd, lbpohd, tkd, tod)
       // x.shouldShiftVec.asUInt =/= y.shouldShiftVec.asUInt,
       // x.brTaken =/= y.brTaken
     )
@@ -586,24 +633,30 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
 
   // s2
   val s2_possible_predicted_ghist_ptrs_dup = s2_ghist_ptr_dup.map(ptr => (0 to numBr).map(ptr - _.U))
-  val s2_predicted_ghist_ptr_dup = s2_possible_predicted_ghist_ptrs_dup.zip(resp.s2.lastBrPosOH).map{ case (ptr, oh) => Mux1H(oh, ptr)}
+  val s2_predicted_ghist_ptr_dup = s2_possible_predicted_ghist_ptrs_dup.zip(resp.s2.lastBrPosOH).map { case (ptr, oh) =>
+    Mux1H(oh, ptr)
+  }
 
   val s2_possible_predicted_fhs_dup =
-    for ((((fgh, afh), br_num_oh), full_pred) <-
-      s2_folded_gh_dup zip s2_ahead_fh_oldest_bits_dup zip s2_last_br_num_oh_dup zip resp.s2.full_pred)
+    for (
+      (((fgh, afh), br_num_oh), full_pred) <-
+        s2_folded_gh_dup zip s2_ahead_fh_oldest_bits_dup zip s2_last_br_num_oh_dup zip resp.s2.full_pred
+    )
       yield (0 to numBr).map(i =>
-        fgh.update(afh, br_num_oh, i, if (i > 0) full_pred.br_taken_mask(i-1) else false.B)
+        fgh.update(afh, br_num_oh, i, if (i > 0) full_pred.br_taken_mask(i - 1) else false.B)
       )
-  val s2_predicted_fh_dup = resp.s2.lastBrPosOH.zip(s2_possible_predicted_fhs_dup).map{ case (oh, fh) => Mux1H(oh, fh)}
+  val s2_predicted_fh_dup = resp.s2.lastBrPosOH.zip(s2_possible_predicted_fhs_dup).map { case (oh, fh) =>
+    Mux1H(oh, fh)
+  }
 
   val s2_ahead_fh_ob_src_dup = dup_wire(new AllAheadFoldedHistoryOldestBits(foldedGHistInfos))
-  s2_ahead_fh_ob_src_dup.zip(s2_ghist_ptr_dup).map{ case (src, ptr) => src.read(ghv, ptr)}
+  s2_ahead_fh_ob_src_dup.zip(s2_ghist_ptr_dup).map { case (src, ptr) => src.read(ghv, ptr) }
 
   if (EnableGHistDiff) {
     val s2_predicted_ghist = WireInit(getHist(s2_predicted_ghist_ptr_dup(0)).asTypeOf(Vec(HistoryLength, Bool())))
     for (i <- 0 until numBr) {
-      when (resp.s2.shouldShiftVec(0)(i)) {
-        s2_predicted_ghist(i) := resp.s2.brTaken(0) && (i==0).B
+      when(resp.s2.shouldShiftVec(0)(i)) {
+        s2_predicted_ghist(i) := resp.s2.brTaken(0) && (i == 0).B
       }
     }
     when(s2_redirect_dup(0)) {
@@ -612,44 +665,61 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   }
 
   val s2_ghv_wens = (0 until HistoryLength).map(n =>
-    (0 until numBr).map(b => (s2_ghist_ptr_dup(0)).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s2.shouldShiftVec(0)(b) && s2_redirect_dup(0)))
+    (0 until numBr).map(b =>
+      s2_ghist_ptr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s2.shouldShiftVec(0)(
+        b
+      ) && s2_redirect_dup(0)
+    )
+  )
   val s2_ghv_wdatas = (0 until HistoryLength).map(n =>
     Mux1H(
-      (0 until numBr).map(b => (
-        (s2_ghist_ptr_dup(0)).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s2.shouldShiftVec(0)(b),
-        resp.s2.full_pred(0).real_br_taken_mask()(b)
-      ))
+      (0 until numBr).map(b =>
+        (
+          s2_ghist_ptr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s2.shouldShiftVec(0)(b),
+          resp.s2.full_pred(0).real_br_taken_mask()(b)
+        )
+      )
     )
   )
 
   val s1_pred_info = Wire(new PreviousPredInfo)
-  s1_pred_info.hit := resp.s1.full_pred.map(_.hit)
-  s1_pred_info.target := resp.s1.getTarget
+  s1_pred_info.hit         := resp.s1.full_pred.map(_.hit)
+  s1_pred_info.target      := resp.s1.getTarget
   s1_pred_info.lastBrPosOH := resp.s1.lastBrPosOH
-  s1_pred_info.taken := resp.s1.taken
-  s1_pred_info.takenMask := resp.s1.full_pred.map(_.taken_mask_on_slot)
-  s1_pred_info.cfiIndex := resp.s1.cfiIndex.map { case x => x.bits }
+  s1_pred_info.taken       := resp.s1.taken
+  s1_pred_info.takenMask   := resp.s1.full_pred.map(_.taken_mask_on_slot)
+  s1_pred_info.cfiIndex    := resp.s1.cfiIndex.map { case x => x.bits }
 
   val previous_s1_pred_info = RegEnable(s1_pred_info, 0.U.asTypeOf(new PreviousPredInfo), s1_fire_dup(0))
 
   val s2_redirect_s1_last_pred_vec_dup = preds_needs_redirect_vec_dup(previous_s1_pred_info, resp.s2)
 
-  for (((s2_redirect, s2_fire), s2_redirect_s1_last_pred_vec) <- s2_redirect_dup zip s2_fire_dup zip s2_redirect_s1_last_pred_vec_dup)
-    s2_redirect := s2_fire && s2_redirect_s1_last_pred_vec.reduce(_||_)
-
+  for (
+    ((s2_redirect, s2_fire), s2_redirect_s1_last_pred_vec) <-
+      s2_redirect_dup zip s2_fire_dup zip s2_redirect_s1_last_pred_vec_dup
+  )
+    s2_redirect := s2_fire && s2_redirect_s1_last_pred_vec.reduce(_ || _)
 
   for (((npcGen, s2_redirect), s2_target) <- npcGen_dup zip s2_redirect_dup zip resp.s2.getTarget)
     npcGen.register(s2_redirect, s2_target, Some("s2_target"), 5)
   for (((foldedGhGen, s2_redirect), s2_predicted_fh) <- foldedGhGen_dup zip s2_redirect_dup zip s2_predicted_fh_dup)
     foldedGhGen.register(s2_redirect, s2_predicted_fh, Some("s2_FGH"), 5)
-  for (((ghistPtrGen, s2_redirect), s2_predicted_ghist_ptr) <- ghistPtrGen_dup zip s2_redirect_dup zip s2_predicted_ghist_ptr_dup)
+  for (
+    ((ghistPtrGen, s2_redirect), s2_predicted_ghist_ptr) <-
+      ghistPtrGen_dup zip s2_redirect_dup zip s2_predicted_ghist_ptr_dup
+  )
     ghistPtrGen.register(s2_redirect, s2_predicted_ghist_ptr, Some("s2_GHPtr"), 5)
-  for (((lastBrNumOHGen, s2_redirect), s2_brPosOH) <- lastBrNumOHGen_dup zip s2_redirect_dup zip resp.s2.lastBrPosOH.map(_.asUInt))
+  for (
+    ((lastBrNumOHGen, s2_redirect), s2_brPosOH) <-
+      lastBrNumOHGen_dup zip s2_redirect_dup zip resp.s2.lastBrPosOH.map(_.asUInt)
+  )
     lastBrNumOHGen.register(s2_redirect, s2_brPosOH, Some("s2_BrNumOH"), 5)
-  for (((aheadFhObGen, s2_redirect), s2_ahead_fh_ob_src) <- aheadFhObGen_dup zip s2_redirect_dup zip s2_ahead_fh_ob_src_dup)
+  for (
+    ((aheadFhObGen, s2_redirect), s2_ahead_fh_ob_src) <- aheadFhObGen_dup zip s2_redirect_dup zip s2_ahead_fh_ob_src_dup
+  )
     aheadFhObGen.register(s2_redirect, s2_ahead_fh_ob_src, Some("s2_AFHOB"), 5)
-  ghvBitWriteGens.zip(s2_ghv_wens).zipWithIndex.map{case ((b, w), i) =>
-    b.register(w.reduce(_||_), s2_ghv_wdatas(i), Some(s"s2_new_bit_$i"), 5)
+  ghvBitWriteGens.zip(s2_ghv_wens).zipWithIndex.map { case ((b, w), i) =>
+    b.register(w.reduce(_ || _), s2_ghv_wdatas(i), Some(s"s2_new_bit_$i"), 5)
   }
 
   XSPerfAccumulate("s2_redirect_because_target_diff", s2_fire_dup(0) && s2_redirect_s1_last_pred_vec_dup(0)(0))
@@ -664,27 +734,32 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   XSPerfAccumulate("s2_redirect_when_not_taken", s2_redirect_dup(0) && !resp.s2.taken(0) && resp.s2.full_pred(0).hit)
   XSPerfAccumulate("s2_redirect_when_not_hit", s2_redirect_dup(0) && !resp.s2.full_pred(0).hit)
 
-
   // s3
   val s3_possible_predicted_ghist_ptrs_dup = s3_ghist_ptr_dup.map(ptr => (0 to numBr).map(ptr - _.U))
-  val s3_predicted_ghist_ptr_dup = s3_possible_predicted_ghist_ptrs_dup.zip(resp.s3.lastBrPosOH).map{ case (ptr, oh) => Mux1H(oh, ptr)}
+  val s3_predicted_ghist_ptr_dup = s3_possible_predicted_ghist_ptrs_dup.zip(resp.s3.lastBrPosOH).map { case (ptr, oh) =>
+    Mux1H(oh, ptr)
+  }
 
   val s3_possible_predicted_fhs_dup =
-    for ((((fgh, afh), br_num_oh), full_pred) <-
-      s3_folded_gh_dup zip s3_ahead_fh_oldest_bits_dup zip s3_last_br_num_oh_dup zip resp.s3.full_pred)
+    for (
+      (((fgh, afh), br_num_oh), full_pred) <-
+        s3_folded_gh_dup zip s3_ahead_fh_oldest_bits_dup zip s3_last_br_num_oh_dup zip resp.s3.full_pred
+    )
       yield (0 to numBr).map(i =>
-        fgh.update(afh, br_num_oh, i, if (i > 0) full_pred.br_taken_mask(i-1) else false.B)
+        fgh.update(afh, br_num_oh, i, if (i > 0) full_pred.br_taken_mask(i - 1) else false.B)
       )
-  val s3_predicted_fh_dup = resp.s3.lastBrPosOH.zip(s3_possible_predicted_fhs_dup).map{ case (oh, fh) => Mux1H(oh, fh)}
+  val s3_predicted_fh_dup = resp.s3.lastBrPosOH.zip(s3_possible_predicted_fhs_dup).map { case (oh, fh) =>
+    Mux1H(oh, fh)
+  }
 
   val s3_ahead_fh_ob_src_dup = dup_wire(new AllAheadFoldedHistoryOldestBits(foldedGHistInfos))
-  s3_ahead_fh_ob_src_dup.zip(s3_ghist_ptr_dup).map{ case (src, ptr) => src.read(ghv, ptr)}
+  s3_ahead_fh_ob_src_dup.zip(s3_ghist_ptr_dup).map { case (src, ptr) => src.read(ghv, ptr) }
 
   if (EnableGHistDiff) {
     val s3_predicted_ghist = WireInit(getHist(s3_predicted_ghist_ptr_dup(0)).asTypeOf(Vec(HistoryLength, Bool())))
     for (i <- 0 until numBr) {
-      when (resp.s3.shouldShiftVec(0)(i)) {
-        s3_predicted_ghist(i) := resp.s3.brTaken(0) && (i==0).B
+      when(resp.s3.shouldShiftVec(0)(i)) {
+        s3_predicted_ghist(i) := resp.s3.brTaken(0) && (i == 0).B
       }
     }
     when(s3_redirect_dup(0)) {
@@ -693,27 +768,48 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   }
 
   val s3_ghv_wens = (0 until HistoryLength).map(n =>
-    (0 until numBr).map(b => (s3_ghist_ptr_dup(0)).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s3.shouldShiftVec(0)(b) && s3_redirect_dup(0)))
+    (0 until numBr).map(b =>
+      s3_ghist_ptr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s3.shouldShiftVec(0)(
+        b
+      ) && s3_redirect_dup(0)
+    )
+  )
   val s3_ghv_wdatas = (0 until HistoryLength).map(n =>
     Mux1H(
-      (0 until numBr).map(b => (
-        (s3_ghist_ptr_dup(0)).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s3.shouldShiftVec(0)(b),
-        resp.s3.full_pred(0).real_br_taken_mask()(b)
-      ))
+      (0 until numBr).map(b =>
+        (
+          s3_ghist_ptr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && resp.s3.shouldShiftVec(0)(b),
+          resp.s3.full_pred(0).real_br_taken_mask()(b)
+        )
+      )
     )
   )
 
   val previous_s2_pred = RegEnable(resp.s2, 0.U.asTypeOf(resp.s2), s2_fire_dup(0))
 
-  val s3_redirect_on_br_taken_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map {case (fp1, fp2) => fp1.real_br_taken_mask().asUInt =/= fp2.real_br_taken_mask().asUInt}
-  val s3_both_first_taken_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map {case (fp1, fp2) => fp1.real_br_taken_mask()(0) && fp2.real_br_taken_mask()(0)}
-  val s3_redirect_on_target_dup = resp.s3.getTarget.zip(previous_s2_pred.getTarget).map {case (t1, t2) => t1 =/= t2}
-  val s3_redirect_on_jalr_target_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map {case (fp1, fp2) => fp1.hit_taken_on_jalr && fp1.jalr_target =/= fp2.jalr_target}
+  val s3_redirect_on_br_taken_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map { case (fp1, fp2) =>
+    fp1.real_br_taken_mask().asUInt =/= fp2.real_br_taken_mask().asUInt
+  }
+  val s3_both_first_taken_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map { case (fp1, fp2) =>
+    fp1.real_br_taken_mask()(0) && fp2.real_br_taken_mask()(0)
+  }
+  val s3_redirect_on_target_dup = resp.s3.getTarget.zip(previous_s2_pred.getTarget).map { case (t1, t2) => t1 =/= t2 }
+  val s3_redirect_on_jalr_target_dup = resp.s3.full_pred.zip(previous_s2_pred.full_pred).map { case (fp1, fp2) =>
+    fp1.hit_taken_on_jalr && fp1.jalr_target =/= fp2.jalr_target
+  }
   val s3_redirect_on_fall_thru_error_dup = resp.s3.fallThruError
-  val s3_redirect_on_ftb_multi_hit_dup = resp.s3.ftbMultiHit
-
-  for (((((((s3_redirect, s3_fire), s3_redirect_on_br_taken), s3_redirect_on_target), s3_redirect_on_fall_thru_error), s3_redirect_on_ftb_multi_hit), s3_both_first_taken) <-
-    s3_redirect_dup zip s3_fire_dup zip s3_redirect_on_br_taken_dup zip s3_redirect_on_target_dup zip s3_redirect_on_fall_thru_error_dup zip s3_redirect_on_ftb_multi_hit_dup zip s3_both_first_taken_dup) {
+  val s3_redirect_on_ftb_multi_hit_dup   = resp.s3.ftbMultiHit
+
+  for (
+    (
+      (
+        ((((s3_redirect, s3_fire), s3_redirect_on_br_taken), s3_redirect_on_target), s3_redirect_on_fall_thru_error),
+        s3_redirect_on_ftb_multi_hit
+      ),
+      s3_both_first_taken
+    ) <-
+      s3_redirect_dup zip s3_fire_dup zip s3_redirect_on_br_taken_dup zip s3_redirect_on_target_dup zip s3_redirect_on_fall_thru_error_dup zip s3_redirect_on_ftb_multi_hit_dup zip s3_both_first_taken_dup
+  ) {
 
     s3_redirect := s3_fire && (
       (s3_redirect_on_br_taken && !s3_both_first_taken) || s3_redirect_on_target || s3_redirect_on_fall_thru_error || s3_redirect_on_ftb_multi_hit
@@ -722,20 +818,31 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
 
   XSPerfAccumulate(f"s3_redirect_on_br_taken", s3_fire_dup(0) && s3_redirect_on_br_taken_dup(0))
   XSPerfAccumulate(f"s3_redirect_on_jalr_target", s3_fire_dup(0) && s3_redirect_on_jalr_target_dup(0))
-  XSPerfAccumulate(f"s3_redirect_on_others", s3_redirect_dup(0) && !(s3_redirect_on_br_taken_dup(0) || s3_redirect_on_jalr_target_dup(0)))
+  XSPerfAccumulate(
+    f"s3_redirect_on_others",
+    s3_redirect_dup(0) && !(s3_redirect_on_br_taken_dup(0) || s3_redirect_on_jalr_target_dup(0))
+  )
 
   for (((npcGen, s3_redirect), s3_target) <- npcGen_dup zip s3_redirect_dup zip resp.s3.getTarget)
     npcGen.register(s3_redirect, s3_target, Some("s3_target"), 3)
   for (((foldedGhGen, s3_redirect), s3_predicted_fh) <- foldedGhGen_dup zip s3_redirect_dup zip s3_predicted_fh_dup)
     foldedGhGen.register(s3_redirect, s3_predicted_fh, Some("s3_FGH"), 3)
-  for (((ghistPtrGen, s3_redirect), s3_predicted_ghist_ptr) <- ghistPtrGen_dup zip s3_redirect_dup zip s3_predicted_ghist_ptr_dup)
+  for (
+    ((ghistPtrGen, s3_redirect), s3_predicted_ghist_ptr) <-
+      ghistPtrGen_dup zip s3_redirect_dup zip s3_predicted_ghist_ptr_dup
+  )
     ghistPtrGen.register(s3_redirect, s3_predicted_ghist_ptr, Some("s3_GHPtr"), 3)
-  for (((lastBrNumOHGen, s3_redirect), s3_brPosOH) <- lastBrNumOHGen_dup zip s3_redirect_dup zip resp.s3.lastBrPosOH.map(_.asUInt))
+  for (
+    ((lastBrNumOHGen, s3_redirect), s3_brPosOH) <-
+      lastBrNumOHGen_dup zip s3_redirect_dup zip resp.s3.lastBrPosOH.map(_.asUInt)
+  )
     lastBrNumOHGen.register(s3_redirect, s3_brPosOH, Some("s3_BrNumOH"), 3)
-  for (((aheadFhObGen, s3_redirect), s3_ahead_fh_ob_src) <- aheadFhObGen_dup zip s3_redirect_dup zip s3_ahead_fh_ob_src_dup)
+  for (
+    ((aheadFhObGen, s3_redirect), s3_ahead_fh_ob_src) <- aheadFhObGen_dup zip s3_redirect_dup zip s3_ahead_fh_ob_src_dup
+  )
     aheadFhObGen.register(s3_redirect, s3_ahead_fh_ob_src, Some("s3_AFHOB"), 3)
-  ghvBitWriteGens.zip(s3_ghv_wens).zipWithIndex.map{case ((b, w), i) =>
-    b.register(w.reduce(_||_), s3_ghv_wdatas(i), Some(s"s3_new_bit_$i"), 3)
+  ghvBitWriteGens.zip(s3_ghv_wens).zipWithIndex.map { case ((b, w), i) =>
+    b.register(w.reduce(_ || _), s3_ghv_wdatas(i), Some(s"s3_new_bit_$i"), 3)
   }
 
   // Send signal tell Ftq override
@@ -750,67 +857,75 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   for (((to_ftq_s2_valid, s2_fire), s2_flush) <- io.bpu_to_ftq.resp.bits.s2.valid zip s2_fire_dup zip s2_flush_dup) {
     to_ftq_s2_valid := s2_fire && !s2_flush
   }
-  io.bpu_to_ftq.resp.bits.s2.hasRedirect.zip(s2_redirect_dup).map {case (hr, r) => hr := r}
+  io.bpu_to_ftq.resp.bits.s2.hasRedirect.zip(s2_redirect_dup).map { case (hr, r) => hr := r }
   io.bpu_to_ftq.resp.bits.s2.ftq_idx := s2_ftq_idx
   for (((to_ftq_s3_valid, s3_fire), s3_flush) <- io.bpu_to_ftq.resp.bits.s3.valid zip s3_fire_dup zip s3_flush_dup) {
     to_ftq_s3_valid := s3_fire && !s3_flush
   }
-  io.bpu_to_ftq.resp.bits.s3.hasRedirect.zip(s3_redirect_dup).map {case (hr, r) => hr := r}
+  io.bpu_to_ftq.resp.bits.s3.hasRedirect.zip(s3_redirect_dup).map { case (hr, r) => hr := r }
   io.bpu_to_ftq.resp.bits.s3.ftq_idx := s3_ftq_idx
 
   predictors.io.update.valid := RegNext(io.ftq_to_bpu.update.valid, init = false.B)
-  predictors.io.update.bits := RegEnable(io.ftq_to_bpu.update.bits, io.ftq_to_bpu.update.valid)
+  predictors.io.update.bits  := RegEnable(io.ftq_to_bpu.update.bits, io.ftq_to_bpu.update.valid)
   predictors.io.update.bits.ghist := RegEnable(
-    getHist(io.ftq_to_bpu.update.bits.spec_info.histPtr), io.ftq_to_bpu.update.valid)
+    getHist(io.ftq_to_bpu.update.bits.spec_info.histPtr),
+    io.ftq_to_bpu.update.valid
+  )
 
   val redirect_dup = do_redirect_dup.map(_.bits)
   predictors.io.redirect := do_redirect_dup(0)
 
   // Redirect logic
-  val shift_dup = redirect_dup.map(_.cfiUpdate.shift)
+  val shift_dup       = redirect_dup.map(_.cfiUpdate.shift)
   val addIntoHist_dup = redirect_dup.map(_.cfiUpdate.addIntoHist)
   // TODO: remove these below
-  val shouldShiftVec_dup = shift_dup.map(shift => Mux(shift === 0.U, VecInit(0.U((1 << (log2Ceil(numBr) + 1)).W).asBools), VecInit((LowerMask(1.U << (shift-1.U))).asBools)))
+  val shouldShiftVec_dup = shift_dup.map(shift =>
+    Mux(
+      shift === 0.U,
+      VecInit(0.U((1 << (log2Ceil(numBr) + 1)).W).asBools),
+      VecInit(LowerMask(1.U << (shift - 1.U)).asBools)
+    )
+  )
   // TODO end
-  val afhob_dup = redirect_dup.map(_.cfiUpdate.afhob)
+  val afhob_dup       = redirect_dup.map(_.cfiUpdate.afhob)
   val lastBrNumOH_dup = redirect_dup.map(_.cfiUpdate.lastBrNumOH)
 
-
-  val isBr_dup = redirect_dup.map(_.cfiUpdate.pd.isBr)
+  val isBr_dup  = redirect_dup.map(_.cfiUpdate.pd.isBr)
   val taken_dup = redirect_dup.map(_.cfiUpdate.taken)
   val real_br_taken_mask_dup =
     for (((shift, taken), addIntoHist) <- shift_dup zip taken_dup zip addIntoHist_dup)
-      yield (0 until numBr).map(i => shift === (i+1).U && taken && addIntoHist )
+      yield (0 until numBr).map(i => shift === (i + 1).U && taken && addIntoHist)
 
-  val oldPtr_dup = redirect_dup.map(_.cfiUpdate.histPtr)
-  val updated_ptr_dup = oldPtr_dup.zip(shift_dup).map {case (oldPtr, shift) => oldPtr - shift}
-  def computeFoldedHist(hist: UInt, compLen: Int)(histLen: Int): UInt = {
+  val oldPtr_dup      = redirect_dup.map(_.cfiUpdate.histPtr)
+  val updated_ptr_dup = oldPtr_dup.zip(shift_dup).map { case (oldPtr, shift) => oldPtr - shift }
+  def computeFoldedHist(hist: UInt, compLen: Int)(histLen: Int): UInt =
     if (histLen > 0) {
       val nChunks     = (histLen + compLen - 1) / compLen
-      val hist_chunks = (0 until nChunks) map { i =>
-        hist(min((i + 1) * compLen, histLen) - 1, i * compLen)
-      }
+      val hist_chunks = (0 until nChunks) map { i => hist(min((i + 1) * compLen, histLen) - 1, i * compLen) }
       ParallelXOR(hist_chunks)
-    }
-    else 0.U
-  }
+    } else 0.U
 
   val oldFh_dup = dup_seq(WireInit(0.U.asTypeOf(new AllFoldedHistories(foldedGHistInfos))))
   oldFh_dup.zip(oldPtr_dup).map { case (oldFh, oldPtr) =>
-      foldedGHistInfos.foreach { case (histLen, compLen) => 
-        oldFh.getHistWithInfo((histLen, compLen)).folded_hist := computeFoldedHist(getHist(oldPtr), compLen)(histLen)
-      }
+    foldedGHistInfos.foreach { case (histLen, compLen) =>
+      oldFh.getHistWithInfo((histLen, compLen)).folded_hist := computeFoldedHist(getHist(oldPtr), compLen)(histLen)
+    }
   }
 
   val updated_fh_dup =
-    for (((((oldFh, oldPtr), taken), addIntoHist), shift) <-
-      oldFh_dup zip oldPtr_dup zip taken_dup zip addIntoHist_dup zip shift_dup)
-    yield VecInit((0 to numBr).map(i => oldFh.update(ghv, oldPtr, i, taken && addIntoHist)))(shift)
-  val thisBrNumOH_dup = shift_dup.map(shift => UIntToOH(shift, numBr+1))
+    for (
+      ((((oldFh, oldPtr), taken), addIntoHist), shift) <-
+        oldFh_dup zip oldPtr_dup zip taken_dup zip addIntoHist_dup zip shift_dup
+    )
+      yield VecInit((0 to numBr).map(i => oldFh.update(ghv, oldPtr, i, taken && addIntoHist)))(shift)
+  val thisBrNumOH_dup   = shift_dup.map(shift => UIntToOH(shift, numBr + 1))
   val thisAheadFhOb_dup = dup_wire(new AllAheadFoldedHistoryOldestBits(foldedGHistInfos))
-  thisAheadFhOb_dup.zip(oldPtr_dup).map {case (afhob, oldPtr) => afhob.read(ghv, oldPtr)}
+  thisAheadFhOb_dup.zip(oldPtr_dup).map { case (afhob, oldPtr) => afhob.read(ghv, oldPtr) }
   val redirect_ghv_wens = (0 until HistoryLength).map(n =>
-    (0 until numBr).map(b => oldPtr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && shouldShiftVec_dup(0)(b) && do_redirect_dup(0).valid))
+    (0 until numBr).map(b =>
+      oldPtr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && shouldShiftVec_dup(0)(b) && do_redirect_dup(0).valid
+    )
+  )
   val redirect_ghv_wdatas = (0 until HistoryLength).map(n =>
     Mux1H(
       (0 until numBr).map(b => oldPtr_dup(0).value === (CGHPtr(false.B, n.U) + b.U).value && shouldShiftVec_dup(0)(b)),
@@ -821,8 +936,8 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   if (EnableGHistDiff) {
     val updated_ghist = WireInit(getHist(updated_ptr_dup(0)).asTypeOf(Vec(HistoryLength, Bool())))
     for (i <- 0 until numBr) {
-      when (shift_dup(0) >= (i+1).U) {
-        updated_ghist(i) := taken_dup(0) && addIntoHist_dup(0) && (i==0).B
+      when(shift_dup(0) >= (i + 1).U) {
+        updated_ghist(i) := taken_dup(0) && addIntoHist_dup(0) && (i == 0).B
       }
     }
     when(do_redirect_dup(0).valid) {
@@ -832,28 +947,24 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
 
   // Commit time history checker
   if (EnableCommitGHistDiff) {
-    val commitGHist = RegInit(0.U.asTypeOf(Vec(HistoryLength, Bool())))
+    val commitGHist    = RegInit(0.U.asTypeOf(Vec(HistoryLength, Bool())))
     val commitGHistPtr = RegInit(0.U.asTypeOf(new CGHPtr))
     def getCommitHist(ptr: CGHPtr): UInt =
-      (Cat(commitGHist.asUInt, commitGHist.asUInt) >> (ptr.value+1.U))(HistoryLength-1, 0)
+      (Cat(commitGHist.asUInt, commitGHist.asUInt) >> (ptr.value + 1.U))(HistoryLength - 1, 0)
 
-    val updateValid        : Bool      = io.ftq_to_bpu.update.valid
-    val branchValidMask    : UInt      = io.ftq_to_bpu.update.bits.ftb_entry.brValids.asUInt
+    val updateValid:         Bool      = io.ftq_to_bpu.update.valid
+    val branchValidMask:     UInt      = io.ftq_to_bpu.update.bits.ftb_entry.brValids.asUInt
     val branchCommittedMask: Vec[Bool] = io.ftq_to_bpu.update.bits.br_committed
-    val misPredictMask     : UInt      = io.ftq_to_bpu.update.bits.mispred_mask.asUInt
-    val takenMask          : UInt      =
+    val misPredictMask:      UInt      = io.ftq_to_bpu.update.bits.mispred_mask.asUInt
+    val takenMask: UInt =
       io.ftq_to_bpu.update.bits.br_taken_mask.asUInt |
         io.ftq_to_bpu.update.bits.ftb_entry.always_taken.asUInt // Always taken branch is recorded in history
-    val takenIdx       : UInt = (PriorityEncoder(takenMask) + 1.U((log2Ceil(numBr)+1).W)).asUInt
-    val misPredictIdx  : UInt = (PriorityEncoder(misPredictMask) + 1.U((log2Ceil(numBr)+1).W)).asUInt
-    val shouldShiftMask: UInt = Mux(takenMask.orR,
-        LowerMask(takenIdx).asUInt,
-        ((1 << numBr) - 1).asUInt) &
-      Mux(misPredictMask.orR,
-        LowerMask(misPredictIdx).asUInt,
-        ((1 << numBr) - 1).asUInt) &
+    val takenIdx:      UInt = (PriorityEncoder(takenMask) + 1.U((log2Ceil(numBr) + 1).W)).asUInt
+    val misPredictIdx: UInt = (PriorityEncoder(misPredictMask) + 1.U((log2Ceil(numBr) + 1).W)).asUInt
+    val shouldShiftMask: UInt = Mux(takenMask.orR, LowerMask(takenIdx).asUInt, ((1 << numBr) - 1).asUInt) &
+      Mux(misPredictMask.orR, LowerMask(misPredictIdx).asUInt, ((1 << numBr) - 1).asUInt) &
       branchCommittedMask.asUInt
-    val updateShift    : UInt   =
+    val updateShift: UInt =
       Mux(updateValid && branchValidMask.orR, PopCount(branchValidMask & shouldShiftMask), 0.U)
 
     // Maintain the commitGHist
@@ -871,17 +982,18 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
     // Do differential
     TageTableInfos.map {
       case (nRows, histLen, _) => {
-        val nRowsPerBr = nRows / numBr
+        val nRowsPerBr      = nRows / numBr
         val predictGHistPtr = io.ftq_to_bpu.update.bits.spec_info.histPtr
         val commitTrueHist: UInt = computeFoldedHist(getCommitHist(commitGHistPtr), log2Ceil(nRowsPerBr))(histLen)
-        val predictFHist  : UInt = computeFoldedHist(getHist(predictGHistPtr), log2Ceil(nRowsPerBr))(histLen)
-        XSWarn(updateValid && predictFHist =/= commitTrueHist,
-          p"predict time ghist: ${predictFHist} is different from commit time: ${commitTrueHist}\n")
+        val predictFHist:   UInt = computeFoldedHist(getHist(predictGHistPtr), log2Ceil(nRowsPerBr))(histLen)
+        XSWarn(
+          updateValid && predictFHist =/= commitTrueHist,
+          p"predict time ghist: ${predictFHist} is different from commit time: ${commitTrueHist}\n"
+        )
       }
     }
   }
 
-
   // val updatedGh = oldGh.update(shift, taken && addIntoHist)
   for ((npcGen, do_redirect) <- npcGen_dup zip do_redirect_dup)
     npcGen.register(do_redirect.valid, do_redirect.bits.cfiUpdate.target, Some("redirect_target"), 2)
@@ -893,8 +1005,8 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
     lastBrNumOHGen.register(do_redirect.valid, thisBrNumOH, Some("redirect_BrNumOH"), 2)
   for (((aheadFhObGen, do_redirect), thisAheadFhOb) <- aheadFhObGen_dup zip do_redirect_dup zip thisAheadFhOb_dup)
     aheadFhObGen.register(do_redirect.valid, thisAheadFhOb, Some("redirect_AFHOB"), 2)
-  ghvBitWriteGens.zip(redirect_ghv_wens).zipWithIndex.map{case ((b, w), i) =>
-    b.register(w.reduce(_||_), redirect_ghv_wdatas(i), Some(s"redirect_new_bit_$i"), 2)
+  ghvBitWriteGens.zip(redirect_ghv_wens).zipWithIndex.map { case ((b, w), i) =>
+    b.register(w.reduce(_ || _), redirect_ghv_wdatas(i), Some(s"redirect_new_bit_$i"), 2)
   }
   // no need to assign s0_last_pred
 
@@ -905,120 +1017,151 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   // foldedGhGen.register(need_reset, 0.U.asTypeOf(s0_folded_gh), Some("reset_FGH"), 1)
   // ghistPtrGen.register(need_reset, 0.U.asTypeOf(new CGHPtr), Some("reset_GHPtr"), 1)
 
-  s0_pc_dup.zip(npcGen_dup).map {case (s0_pc, npcGen) => s0_pc := npcGen()}
-  s0_folded_gh_dup.zip(foldedGhGen_dup).map {case (s0_folded_gh, foldedGhGen) => s0_folded_gh := foldedGhGen()}
-  s0_ghist_ptr_dup.zip(ghistPtrGen_dup).map {case (s0_ghist_ptr, ghistPtrGen) => s0_ghist_ptr := ghistPtrGen()}
-  s0_ahead_fh_oldest_bits_dup.zip(aheadFhObGen_dup).map {case (s0_ahead_fh_oldest_bits, aheadFhObGen) =>
-    s0_ahead_fh_oldest_bits := aheadFhObGen()}
-  s0_last_br_num_oh_dup.zip(lastBrNumOHGen_dup).map {case (s0_last_br_num_oh, lastBrNumOHGen) =>
-    s0_last_br_num_oh := lastBrNumOHGen()}
-  (ghv_write_datas zip ghvBitWriteGens).map{case (wd, d) => wd := d()}
+  s0_pc_dup.zip(npcGen_dup).map { case (s0_pc, npcGen) => s0_pc := npcGen() }
+  s0_folded_gh_dup.zip(foldedGhGen_dup).map { case (s0_folded_gh, foldedGhGen) => s0_folded_gh := foldedGhGen() }
+  s0_ghist_ptr_dup.zip(ghistPtrGen_dup).map { case (s0_ghist_ptr, ghistPtrGen) => s0_ghist_ptr := ghistPtrGen() }
+  s0_ahead_fh_oldest_bits_dup.zip(aheadFhObGen_dup).map { case (s0_ahead_fh_oldest_bits, aheadFhObGen) =>
+    s0_ahead_fh_oldest_bits := aheadFhObGen()
+  }
+  s0_last_br_num_oh_dup.zip(lastBrNumOHGen_dup).map { case (s0_last_br_num_oh, lastBrNumOHGen) =>
+    s0_last_br_num_oh := lastBrNumOHGen()
+  }
+  (ghv_write_datas zip ghvBitWriteGens).map { case (wd, d) => wd := d() }
   for (i <- 0 until HistoryLength) {
-    ghv_wens(i) := Seq(s1_ghv_wens, s2_ghv_wens, s3_ghv_wens, redirect_ghv_wens).map(_(i).reduce(_||_)).reduce(_||_)
-    when (ghv_wens(i)) {
+    ghv_wens(i) := Seq(s1_ghv_wens, s2_ghv_wens, s3_ghv_wens, redirect_ghv_wens).map(_(i).reduce(_ || _)).reduce(_ || _)
+    when(ghv_wens(i)) {
       ghv(i) := ghv_write_datas(i)
     }
   }
 
   // TODO: signals for memVio and other Redirects
   controlRedirectBubble := do_redirect_dup(0).valid && do_redirect_dup(0).bits.ControlRedirectBubble
-  ControlBTBMissBubble := do_redirect_dup(0).bits.ControlBTBMissBubble
-  TAGEMissBubble := do_redirect_dup(0).bits.TAGEMissBubble
-  SCMissBubble := do_redirect_dup(0).bits.SCMissBubble
-  ITTAGEMissBubble := do_redirect_dup(0).bits.ITTAGEMissBubble
-  RASMissBubble := do_redirect_dup(0).bits.RASMissBubble
-
-  memVioRedirectBubble := do_redirect_dup(0).valid && do_redirect_dup(0).bits.MemVioRedirectBubble
-  otherRedirectBubble := do_redirect_dup(0).valid && do_redirect_dup(0).bits.OtherRedirectBubble
-  btbMissBubble := do_redirect_dup(0).valid && do_redirect_dup(0).bits.BTBMissBubble
-  overrideBubble(0) := s2_redirect_dup(0)
-  overrideBubble(1) := s3_redirect_dup(0)
-  ftqUpdateBubble(0) := !s1_components_ready_dup(0)
-  ftqUpdateBubble(1) := !s2_components_ready_dup(0)
-  ftqUpdateBubble(2) := !s3_components_ready_dup(0)
-  ftqFullStall := !io.bpu_to_ftq.resp.ready
+  ControlBTBMissBubble  := do_redirect_dup(0).bits.ControlBTBMissBubble
+  TAGEMissBubble        := do_redirect_dup(0).bits.TAGEMissBubble
+  SCMissBubble          := do_redirect_dup(0).bits.SCMissBubble
+  ITTAGEMissBubble      := do_redirect_dup(0).bits.ITTAGEMissBubble
+  RASMissBubble         := do_redirect_dup(0).bits.RASMissBubble
+
+  memVioRedirectBubble                 := do_redirect_dup(0).valid && do_redirect_dup(0).bits.MemVioRedirectBubble
+  otherRedirectBubble                  := do_redirect_dup(0).valid && do_redirect_dup(0).bits.OtherRedirectBubble
+  btbMissBubble                        := do_redirect_dup(0).valid && do_redirect_dup(0).bits.BTBMissBubble
+  overrideBubble(0)                    := s2_redirect_dup(0)
+  overrideBubble(1)                    := s3_redirect_dup(0)
+  ftqUpdateBubble(0)                   := !s1_components_ready_dup(0)
+  ftqUpdateBubble(1)                   := !s2_components_ready_dup(0)
+  ftqUpdateBubble(2)                   := !s3_components_ready_dup(0)
+  ftqFullStall                         := !io.bpu_to_ftq.resp.ready
   io.bpu_to_ftq.resp.bits.topdown_info := topdown_stages(numOfStage - 1)
 
   // topdown handling logic here
-  when (controlRedirectBubble) {
+  when(controlRedirectBubble) {
     /*
     for (i <- 0 until numOfStage)
       topdown_stages(i).reasons(TopDownCounters.ControlRedirectBubble.id) := true.B
     io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.ControlRedirectBubble.id) := true.B
-    */
-    when (ControlBTBMissBubble) {
+     */
+    when(ControlBTBMissBubble) {
       for (i <- 0 until numOfStage)
-        topdown_stages(i).reasons(TopDownCounters.BTBMissBubble.id) := true.B
+        topdown_stages(i).reasons(TopDownCounters.BTBMissBubble.id)                  := true.B
       io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.BTBMissBubble.id) := true.B
-    } .elsewhen (TAGEMissBubble) {
+    }.elsewhen(TAGEMissBubble) {
       for (i <- 0 until numOfStage)
-        topdown_stages(i).reasons(TopDownCounters.TAGEMissBubble.id) := true.B
+        topdown_stages(i).reasons(TopDownCounters.TAGEMissBubble.id)                  := true.B
       io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.TAGEMissBubble.id) := true.B
-    } .elsewhen (SCMissBubble) {
+    }.elsewhen(SCMissBubble) {
       for (i <- 0 until numOfStage)
-        topdown_stages(i).reasons(TopDownCounters.SCMissBubble.id) := true.B
+        topdown_stages(i).reasons(TopDownCounters.SCMissBubble.id)                  := true.B
       io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.SCMissBubble.id) := true.B
-    } .elsewhen (ITTAGEMissBubble) {
+    }.elsewhen(ITTAGEMissBubble) {
       for (i <- 0 until numOfStage)
-        topdown_stages(i).reasons(TopDownCounters.ITTAGEMissBubble.id) := true.B
+        topdown_stages(i).reasons(TopDownCounters.ITTAGEMissBubble.id)                  := true.B
       io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.ITTAGEMissBubble.id) := true.B
-    } .elsewhen (RASMissBubble) {
+    }.elsewhen(RASMissBubble) {
       for (i <- 0 until numOfStage)
-        topdown_stages(i).reasons(TopDownCounters.RASMissBubble.id) := true.B
+        topdown_stages(i).reasons(TopDownCounters.RASMissBubble.id)                  := true.B
       io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.RASMissBubble.id) := true.B
     }
   }
-  when (memVioRedirectBubble) {
+  when(memVioRedirectBubble) {
     for (i <- 0 until numOfStage)
-      topdown_stages(i).reasons(TopDownCounters.MemVioRedirectBubble.id) := true.B
+      topdown_stages(i).reasons(TopDownCounters.MemVioRedirectBubble.id)                  := true.B
     io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.MemVioRedirectBubble.id) := true.B
   }
-  when (otherRedirectBubble) {
+  when(otherRedirectBubble) {
     for (i <- 0 until numOfStage)
-      topdown_stages(i).reasons(TopDownCounters.OtherRedirectBubble.id) := true.B
+      topdown_stages(i).reasons(TopDownCounters.OtherRedirectBubble.id)                  := true.B
     io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.OtherRedirectBubble.id) := true.B
   }
-  when (btbMissBubble) {
+  when(btbMissBubble) {
     for (i <- 0 until numOfStage)
-      topdown_stages(i).reasons(TopDownCounters.BTBMissBubble.id) := true.B
+      topdown_stages(i).reasons(TopDownCounters.BTBMissBubble.id)                  := true.B
     io.bpu_to_ftq.resp.bits.topdown_info.reasons(TopDownCounters.BTBMissBubble.id) := true.B
   }
 
   for (i <- 0 until numOfStage) {
     if (i < numOfStage - overrideStage) {
-      when (overrideBubble(i)) {
+      when(overrideBubble(i)) {
         for (j <- 0 to i)
           topdown_stages(j).reasons(TopDownCounters.OverrideBubble.id) := true.B
       }
     }
     if (i < numOfStage - ftqUpdateStage) {
-      when (ftqUpdateBubble(i)) {
+      when(ftqUpdateBubble(i)) {
         topdown_stages(i).reasons(TopDownCounters.FtqUpdateBubble.id) := true.B
       }
     }
   }
-  when (ftqFullStall) {
+  when(ftqFullStall) {
     topdown_stages(0).reasons(TopDownCounters.FtqFullStall.id) := true.B
   }
 
-  XSError(isBefore(redirect_dup(0).cfiUpdate.histPtr, s3_ghist_ptr_dup(0)) && do_redirect_dup(0).valid,
-    p"s3_ghist_ptr ${s3_ghist_ptr_dup(0)} exceeds redirect histPtr ${redirect_dup(0).cfiUpdate.histPtr}\n")
-  XSError(isBefore(redirect_dup(0).cfiUpdate.histPtr, s2_ghist_ptr_dup(0)) && do_redirect_dup(0).valid,
-    p"s2_ghist_ptr ${s2_ghist_ptr_dup(0)} exceeds redirect histPtr ${redirect_dup(0).cfiUpdate.histPtr}\n")
-  XSError(isBefore(redirect_dup(0).cfiUpdate.histPtr, s1_ghist_ptr_dup(0)) && do_redirect_dup(0).valid,
-    p"s1_ghist_ptr ${s1_ghist_ptr_dup(0)} exceeds redirect histPtr ${redirect_dup(0).cfiUpdate.histPtr}\n")
+  XSError(
+    isBefore(redirect_dup(0).cfiUpdate.histPtr, s3_ghist_ptr_dup(0)) && do_redirect_dup(0).valid,
+    p"s3_ghist_ptr ${s3_ghist_ptr_dup(0)} exceeds redirect histPtr ${redirect_dup(0).cfiUpdate.histPtr}\n"
+  )
+  XSError(
+    isBefore(redirect_dup(0).cfiUpdate.histPtr, s2_ghist_ptr_dup(0)) && do_redirect_dup(0).valid,
+    p"s2_ghist_ptr ${s2_ghist_ptr_dup(0)} exceeds redirect histPtr ${redirect_dup(0).cfiUpdate.histPtr}\n"
+  )
+  XSError(
+    isBefore(redirect_dup(0).cfiUpdate.histPtr, s1_ghist_ptr_dup(0)) && do_redirect_dup(0).valid,
+    p"s1_ghist_ptr ${s1_ghist_ptr_dup(0)} exceeds redirect histPtr ${redirect_dup(0).cfiUpdate.histPtr}\n"
+  )
 
   XSDebug(RegNext(reset.asBool) && !reset.asBool, "Reseting...\n")
   XSDebug(io.ftq_to_bpu.update.valid, p"Update from ftq\n")
   XSDebug(io.ftq_to_bpu.redirect.valid, p"Redirect from ftq\n")
 
   XSDebug("[BP0]                 fire=%d                      pc=%x\n", s0_fire_dup(0), s0_pc_dup(0))
-  XSDebug("[BP1] v=%d r=%d cr=%d fire=%d             flush=%d pc=%x\n",
-    s1_valid_dup(0), s1_ready_dup(0), s1_components_ready_dup(0), s1_fire_dup(0), s1_flush_dup(0), s1_pc)
-  XSDebug("[BP2] v=%d r=%d cr=%d fire=%d redirect=%d flush=%d pc=%x\n",
-    s2_valid_dup(0), s2_ready_dup(0), s2_components_ready_dup(0), s2_fire_dup(0), s2_redirect_dup(0), s2_flush_dup(0), s2_pc)
-  XSDebug("[BP3] v=%d r=%d cr=%d fire=%d redirect=%d flush=%d pc=%x\n",
-    s3_valid_dup(0), s3_ready_dup(0), s3_components_ready_dup(0), s3_fire_dup(0), s3_redirect_dup(0), s3_flush_dup(0), s3_pc)
+  XSDebug(
+    "[BP1] v=%d r=%d cr=%d fire=%d             flush=%d pc=%x\n",
+    s1_valid_dup(0),
+    s1_ready_dup(0),
+    s1_components_ready_dup(0),
+    s1_fire_dup(0),
+    s1_flush_dup(0),
+    s1_pc
+  )
+  XSDebug(
+    "[BP2] v=%d r=%d cr=%d fire=%d redirect=%d flush=%d pc=%x\n",
+    s2_valid_dup(0),
+    s2_ready_dup(0),
+    s2_components_ready_dup(0),
+    s2_fire_dup(0),
+    s2_redirect_dup(0),
+    s2_flush_dup(0),
+    s2_pc
+  )
+  XSDebug(
+    "[BP3] v=%d r=%d cr=%d fire=%d redirect=%d flush=%d pc=%x\n",
+    s3_valid_dup(0),
+    s3_ready_dup(0),
+    s3_components_ready_dup(0),
+    s3_fire_dup(0),
+    s3_redirect_dup(0),
+    s3_flush_dup(0),
+    s3_pc
+  )
   XSDebug("[FTQ] ready=%d\n", io.bpu_to_ftq.resp.ready)
   XSDebug("resp.s1.target=%x\n", resp.s1.getTarget(0))
   XSDebug("resp.s2.target=%x\n", resp.s2.getTarget(0))
@@ -1034,7 +1177,6 @@ class Predictor(implicit p: Parameters) extends XSModule with HasBPUConst with H
   io.ftq_to_bpu.update.bits.display(io.ftq_to_bpu.update.valid)
   io.ftq_to_bpu.redirect.bits.display(io.ftq_to_bpu.redirect.valid)
 
-
   XSPerfAccumulate("s2_redirect", s2_redirect_dup(0))
   XSPerfAccumulate("s3_redirect", s3_redirect_dup(0))
   XSPerfAccumulate("s1_not_valid", !s1_valid_dup(0))
diff --git a/src/main/scala/xiangshan/frontend/Bim.scala b/src/main/scala/xiangshan/frontend/Bim.scala
index 78f229e9ce0..6fd43b2ce20 100644
--- a/src/main/scala/xiangshan/frontend/Bim.scala
+++ b/src/main/scala/xiangshan/frontend/Bim.scala
@@ -121,4 +121,4 @@ class BIM(implicit p: Parameters) extends BasePredictor with BimParams with BPUU
   }
 
 }
-*/
\ No newline at end of file
+ */
diff --git a/src/main/scala/xiangshan/frontend/Composer.scala b/src/main/scala/xiangshan/frontend/Composer.scala
index f6db5daba62..8a854798464 100644
--- a/src/main/scala/xiangshan/frontend/Composer.scala
+++ b/src/main/scala/xiangshan/frontend/Composer.scala
@@ -16,12 +16,12 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
+import xiangshan._
 
 class Composer(implicit p: Parameters) extends BasePredictor with HasBPUConst with HasPerfEvents {
   val (components, resp) = getBPDComponents(io.in.bits.resp_in(0), p)
@@ -35,15 +35,15 @@ class Composer(implicit p: Parameters) extends BasePredictor with HasBPUConst wi
     io.out.s1 := fast_pred.io.out.s1
   }
 
-  var metas = 0.U(1.W)
+  var metas   = 0.U(1.W)
   var meta_sz = 0
   for (c <- components) {
-    c.io.reset_vector        := io.reset_vector
-    c.io.in.valid            := io.in.valid
-    c.io.in.bits.s0_pc       := io.in.bits.s0_pc
-    c.io.in.bits.folded_hist := io.in.bits.folded_hist
+    c.io.reset_vector           := io.reset_vector
+    c.io.in.valid               := io.in.valid
+    c.io.in.bits.s0_pc          := io.in.bits.s0_pc
+    c.io.in.bits.folded_hist    := io.in.bits.folded_hist
     c.io.in.bits.s1_folded_hist := io.in.bits.s1_folded_hist
-    c.io.in.bits.ghist       := io.in.bits.ghist
+    c.io.in.bits.ghist          := io.in.bits.ghist
 
     c.io.s0_fire := io.s0_fire
     c.io.s1_fire := io.s1_fire
@@ -53,18 +53,17 @@ class Composer(implicit p: Parameters) extends BasePredictor with HasBPUConst wi
     c.io.s2_redirect := io.s2_redirect
     c.io.s3_redirect := io.s3_redirect
 
-    c.io.redirect := io.redirect
-    c.io.ctrl := DelayN(io.ctrl, 1)
+    c.io.redirect        := io.redirect
+    c.io.ctrl            := DelayN(io.ctrl, 1)
     c.io.redirectFromIFU := io.redirectFromIFU
 
     if (c.meta_size > 0) {
-      metas = (metas << c.meta_size) | c.io.out.last_stage_meta(c.meta_size-1,0)
+      metas = (metas << c.meta_size) | c.io.out.last_stage_meta(c.meta_size - 1, 0)
     }
     meta_sz = meta_sz + c.meta_size
   }
   println(s"total meta size: $meta_sz\n\n")
 
-
   io.in.ready := components.map(_.io.s1_ready).reduce(_ && _)
 
   io.s1_ready := components.map(_.io.s1_ready).reduce(_ && _)
@@ -75,7 +74,7 @@ class Composer(implicit p: Parameters) extends BasePredictor with HasBPUConst wi
 
   var update_meta = io.update.bits.meta
   for (c <- components.reverse) {
-    c.io.update := io.update
+    c.io.update           := io.update
     c.io.update.bits.meta := update_meta
     update_meta = update_meta >> c.meta_size
   }
@@ -90,8 +89,8 @@ class Composer(implicit p: Parameters) extends BasePredictor with HasBPUConst wi
     metas(idx)
   }
 
-  override def getFoldedHistoryInfo = Some(components.map(_.getFoldedHistoryInfo.getOrElse(Set())).reduce(_++_))
+  override def getFoldedHistoryInfo = Some(components.map(_.getFoldedHistoryInfo.getOrElse(Set())).reduce(_ ++ _))
 
-  override val perfEvents = components.map(_.getPerfEvents).reduce(_++_)
+  override val perfEvents = components.map(_.getPerfEvents).reduce(_ ++ _)
   generatePerfEvent()
 }
diff --git a/src/main/scala/xiangshan/frontend/FTB.scala b/src/main/scala/xiangshan/frontend/FTB.scala
index f28de10f88c..c882de60d9e 100644
--- a/src/main/scala/xiangshan/frontend/FTB.scala
+++ b/src/main/scala/xiangshan/frontend/FTB.scala
@@ -16,36 +16,32 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
-import scala.math.min
-import scala.{Tuple2 => &}
+import org.chipsalliance.cde.config.Parameters
 import os.copy
-
+import scala.{Tuple2 => &}
+import scala.math.min
+import utility._
+import utils._
+import xiangshan._
 
 trait FTBParams extends HasXSParameter with HasBPUConst {
   val numEntries = FtbSize
   val numWays    = FtbWays
-  val numSets    = numEntries/numWays // 512
+  val numSets    = numEntries / numWays // 512
   val tagSize    = 20
 
-
-
   val TAR_STAT_SZ = 2
-  def TAR_FIT = 0.U(TAR_STAT_SZ.W)
-  def TAR_OVF = 1.U(TAR_STAT_SZ.W)
-  def TAR_UDF = 2.U(TAR_STAT_SZ.W)
+  def TAR_FIT     = 0.U(TAR_STAT_SZ.W)
+  def TAR_OVF     = 1.U(TAR_STAT_SZ.W)
+  def TAR_UDF     = 2.U(TAR_STAT_SZ.W)
 
-  def BR_OFFSET_LEN = 12
+  def BR_OFFSET_LEN  = 12
   def JMP_OFFSET_LEN = 20
 
   def FTBCLOSE_THRESHOLD_SZ = log2Ceil(500)
-  def FTBCLOSE_THRESHOLD = 500.U(FTBCLOSE_THRESHOLD_SZ.W) //can be modified
+  def FTBCLOSE_THRESHOLD    = 500.U(FTBCLOSE_THRESHOLD_SZ.W) // can be modified
 }
 
 class FtbSlot_FtqMem(implicit p: Parameters) extends XSBundle with FTBParams {
@@ -54,7 +50,8 @@ class FtbSlot_FtqMem(implicit p: Parameters) extends XSBundle with FTBParams {
   val valid   = Bool()
 }
 
-class FtbSlot(val offsetLen: Int, val subOffsetLen: Option[Int] = None)(implicit p: Parameters) extends FtbSlot_FtqMem with FTBParams {
+class FtbSlot(val offsetLen: Int, val subOffsetLen: Option[Int] = None)(implicit p: Parameters) extends FtbSlot_FtqMem
+    with FTBParams {
   if (subOffsetLen.isDefined) {
     require(subOffsetLen.get <= offsetLen)
   }
@@ -63,39 +60,37 @@ class FtbSlot(val offsetLen: Int, val subOffsetLen: Option[Int] = None)(implicit
 
   def setLowerStatByTarget(pc: UInt, target: UInt, isShare: Boolean) = {
     def getTargetStatByHigher(pc_higher: UInt, target_higher: UInt) =
-      Mux(target_higher > pc_higher, TAR_OVF,
-        Mux(target_higher < pc_higher, TAR_UDF, TAR_FIT))
+      Mux(target_higher > pc_higher, TAR_OVF, Mux(target_higher < pc_higher, TAR_UDF, TAR_FIT))
     def getLowerByTarget(target: UInt, offsetLen: Int) = target(offsetLen, 1)
-    val offLen = if (isShare) this.subOffsetLen.get else this.offsetLen
-    val pc_higher = pc(VAddrBits-1, offLen+1)
-    val target_higher = target(VAddrBits-1, offLen+1)
-    val stat = getTargetStatByHigher(pc_higher, target_higher)
-    val lower = ZeroExt(getLowerByTarget(target, offLen), this.offsetLen)
-    this.lower := lower
+    val offLen        = if (isShare) this.subOffsetLen.get else this.offsetLen
+    val pc_higher     = pc(VAddrBits - 1, offLen + 1)
+    val target_higher = target(VAddrBits - 1, offLen + 1)
+    val stat          = getTargetStatByHigher(pc_higher, target_higher)
+    val lower         = ZeroExt(getLowerByTarget(target, offLen), this.offsetLen)
+    this.lower   := lower
     this.tarStat := stat
     this.sharing := isShare.B
   }
 
   def getTarget(pc: UInt, last_stage: Option[Tuple2[UInt, Bool]] = None) = {
-    def getTarget(offLen: Int)(pc: UInt, lower: UInt, stat: UInt,
-      last_stage: Option[Tuple2[UInt, Bool]] = None) = {
+    def getTarget(offLen: Int)(pc: UInt, lower: UInt, stat: UInt, last_stage: Option[Tuple2[UInt, Bool]] = None) = {
       val h                = pc(VAddrBits - 1, offLen + 1)
       val higher           = Wire(UInt((VAddrBits - offLen - 1).W))
       val higher_plus_one  = Wire(UInt((VAddrBits - offLen - 1).W))
-      val higher_minus_one = Wire(UInt((VAddrBits-offLen-1).W))
+      val higher_minus_one = Wire(UInt((VAddrBits - offLen - 1).W))
 
       // Switch between previous stage pc and current stage pc
       // Give flexibility for timing
       if (last_stage.isDefined) {
-        val last_stage_pc = last_stage.get._1
-        val last_stage_pc_h = last_stage_pc(VAddrBits-1, offLen+1)
-        val stage_en = last_stage.get._2
-        higher := RegEnable(last_stage_pc_h, stage_en)
-        higher_plus_one := RegEnable(last_stage_pc_h+1.U, stage_en)
-        higher_minus_one := RegEnable(last_stage_pc_h-1.U, stage_en)
+        val last_stage_pc   = last_stage.get._1
+        val last_stage_pc_h = last_stage_pc(VAddrBits - 1, offLen + 1)
+        val stage_en        = last_stage.get._2
+        higher           := RegEnable(last_stage_pc_h, stage_en)
+        higher_plus_one  := RegEnable(last_stage_pc_h + 1.U, stage_en)
+        higher_minus_one := RegEnable(last_stage_pc_h - 1.U, stage_en)
       } else {
-        higher := h
-        higher_plus_one := h + 1.U
+        higher           := h
+        higher_plus_one  := h + 1.U
         higher_minus_one := h - 1.U
       }
       val target =
@@ -103,16 +98,18 @@ class FtbSlot(val offsetLen: Int, val subOffsetLen: Option[Int] = None)(implicit
           Mux1H(Seq(
             (stat === TAR_OVF, higher_plus_one),
             (stat === TAR_UDF, higher_minus_one),
-            (stat === TAR_FIT, higher),
+            (stat === TAR_FIT, higher)
           )),
-          lower(offLen-1, 0), 0.U(1.W)
+          lower(offLen - 1, 0),
+          0.U(1.W)
         )
       require(target.getWidth == VAddrBits)
       require(offLen != 0)
       target
     }
     if (subOffsetLen.isDefined)
-      Mux(sharing,
+      Mux(
+        sharing,
         getTarget(subOffsetLen.get)(pc, lower, tarStat, last_stage),
         getTarget(offsetLen)(pc, lower, tarStat, last_stage)
       )
@@ -122,58 +119,56 @@ class FtbSlot(val offsetLen: Int, val subOffsetLen: Option[Int] = None)(implicit
   def fromAnotherSlot(that: FtbSlot) = {
     require(
       this.offsetLen > that.offsetLen && this.subOffsetLen.map(_ == that.offsetLen).getOrElse(true) ||
-      this.offsetLen == that.offsetLen
+        this.offsetLen == that.offsetLen
     )
-    this.offset := that.offset
+    this.offset  := that.offset
     this.tarStat := that.tarStat
     this.sharing := (this.offsetLen > that.offsetLen && that.offsetLen == this.subOffsetLen.get).B
-    this.valid := that.valid
-    this.lower := ZeroExt(that.lower, this.offsetLen)
+    this.valid   := that.valid
+    this.lower   := ZeroExt(that.lower, this.offsetLen)
   }
 
-  def slotConsistent(that: FtbSlot) = {
+  def slotConsistent(that: FtbSlot) =
     VecInit(
-      this.offset  === that.offset,
-      this.lower   === that.lower,
+      this.offset === that.offset,
+      this.lower === that.lower,
       this.tarStat === that.tarStat,
       this.sharing === that.sharing,
-      this.valid   === that.valid
-    ).reduce(_&&_)
-  }
+      this.valid === that.valid
+    ).reduce(_ && _)
 
 }
 
-
 class FTBEntry_part(implicit p: Parameters) extends XSBundle with FTBParams with BPUUtils {
-  val isCall      = Bool()
-  val isRet       = Bool()
-  val isJalr      = Bool()
+  val isCall = Bool()
+  val isRet  = Bool()
+  val isJalr = Bool()
 
   def isJal = !isJalr
 }
 
 class FTBEntry_FtqMem(implicit p: Parameters) extends FTBEntry_part with FTBParams with BPUUtils {
 
-  val brSlots = Vec(numBrSlot, new FtbSlot_FtqMem)
+  val brSlots  = Vec(numBrSlot, new FtbSlot_FtqMem)
   val tailSlot = new FtbSlot_FtqMem
 
-  def jmpValid = {
+  def jmpValid =
     tailSlot.valid && !tailSlot.sharing
-  }
 
-  def getBrRecordedVec(offset: UInt) = {
+  def getBrRecordedVec(offset: UInt) =
     VecInit(
       brSlots.map(s => s.valid && s.offset === offset) :+
-      (tailSlot.valid && tailSlot.offset === offset && tailSlot.sharing)
+        (tailSlot.valid && tailSlot.offset === offset && tailSlot.sharing)
     )
-  }
 
-  def brIsSaved(offset: UInt) = getBrRecordedVec(offset).reduce(_||_)
+  def brIsSaved(offset: UInt) = getBrRecordedVec(offset).reduce(_ || _)
 
   def getBrMaskByOffset(offset: UInt) =
-    brSlots.map{ s => s.valid && s.offset <= offset } :+
-    (tailSlot.valid && tailSlot.offset <= offset && tailSlot.sharing)
-  
+    brSlots.map { s =>
+      s.valid && s.offset <= offset
+    } :+
+      (tailSlot.valid && tailSlot.offset <= offset && tailSlot.sharing)
+
   def newBrCanNotInsert(offset: UInt) = {
     val lastSlotForBr = tailSlot
     lastSlotForBr.valid && lastSlotForBr.offset < offset
@@ -183,38 +178,35 @@ class FTBEntry_FtqMem(implicit p: Parameters) extends FTBEntry_part with FTBPara
 
 class FTBEntry(implicit p: Parameters) extends FTBEntry_part with FTBParams with BPUUtils {
 
-
-  val valid       = Bool()
+  val valid = Bool()
 
   val brSlots = Vec(numBrSlot, new FtbSlot(BR_OFFSET_LEN))
 
   val tailSlot = new FtbSlot(JMP_OFFSET_LEN, Some(BR_OFFSET_LEN))
 
   // Partial Fall-Through Address
-  val pftAddr     = UInt(log2Up(PredictWidth).W)
-  val carry       = Bool()
+  val pftAddr = UInt(log2Up(PredictWidth).W)
+  val carry   = Bool()
 
   val last_may_be_rvi_call = Bool()
 
   val always_taken = Vec(numBr, Bool())
 
   def getSlotForBr(idx: Int): FtbSlot = {
-    require(idx <= numBr-1)
+    require(idx <= numBr - 1)
     (idx, numBr) match {
-      case (i, n) if i == n-1 => this.tailSlot
-      case _ => this.brSlots(idx)
+      case (i, n) if i == n - 1 => this.tailSlot
+      case _                    => this.brSlots(idx)
     }
   }
-  def allSlotsForBr = {
+  def allSlotsForBr =
     (0 until numBr).map(getSlotForBr(_))
-  }
   def setByBrTarget(brIdx: Int, pc: UInt, target: UInt) = {
     val slot = getSlotForBr(brIdx)
-    slot.setLowerStatByTarget(pc, target, brIdx == numBr-1)
+    slot.setLowerStatByTarget(pc, target, brIdx == numBr - 1)
   }
-  def setByJmpTarget(pc: UInt, target: UInt) = {
+  def setByJmpTarget(pc: UInt, target: UInt) =
     this.tailSlot.setLowerStatByTarget(pc, target, false)
-  }
 
   def getTargetVec(pc: UInt, last_stage: Option[Tuple2[UInt, Bool]] = None) = {
     /*
@@ -224,85 +216,94 @@ class FTBEntry(implicit p: Parameters) extends FTBEntry_part with FTBParams with
     calculate the common high bits last_stage_pc_higher of brtarget and jmptarget,
     and the high bits last_stage_pc_middle that need to be added and subtracted from each other,
     and then concatenate them according to the carry situation to obtain brtarget and jmptarget
-    */
-    val h_br                = pc(VAddrBits - 1,  BR_OFFSET_LEN + 1)
-    val higher_br           = Wire(UInt((VAddrBits - BR_OFFSET_LEN - 1).W))
-    val higher_plus_one_br  = Wire(UInt((VAddrBits - BR_OFFSET_LEN - 1).W))
-    val higher_minus_one_br = Wire(UInt((VAddrBits - BR_OFFSET_LEN - 1).W))
-    val h_tail                = pc(VAddrBits - 1,  JMP_OFFSET_LEN + 1)
+     */
+    val h_br                  = pc(VAddrBits - 1, BR_OFFSET_LEN + 1)
+    val higher_br             = Wire(UInt((VAddrBits - BR_OFFSET_LEN - 1).W))
+    val higher_plus_one_br    = Wire(UInt((VAddrBits - BR_OFFSET_LEN - 1).W))
+    val higher_minus_one_br   = Wire(UInt((VAddrBits - BR_OFFSET_LEN - 1).W))
+    val h_tail                = pc(VAddrBits - 1, JMP_OFFSET_LEN + 1)
     val higher_tail           = Wire(UInt((VAddrBits - JMP_OFFSET_LEN - 1).W))
     val higher_plus_one_tail  = Wire(UInt((VAddrBits - JMP_OFFSET_LEN - 1).W))
     val higher_minus_one_tail = Wire(UInt((VAddrBits - JMP_OFFSET_LEN - 1).W))
     if (last_stage.isDefined) {
-      val last_stage_pc = last_stage.get._1
-      val stage_en = last_stage.get._2
-      val last_stage_pc_higher = RegEnable(last_stage_pc(VAddrBits - 1, JMP_OFFSET_LEN + 1), stage_en)
-      val last_stage_pc_middle = RegEnable(last_stage_pc(JMP_OFFSET_LEN, BR_OFFSET_LEN + 1), stage_en)
+      val last_stage_pc                  = last_stage.get._1
+      val stage_en                       = last_stage.get._2
+      val last_stage_pc_higher           = RegEnable(last_stage_pc(VAddrBits - 1, JMP_OFFSET_LEN + 1), stage_en)
+      val last_stage_pc_middle           = RegEnable(last_stage_pc(JMP_OFFSET_LEN, BR_OFFSET_LEN + 1), stage_en)
       val last_stage_pc_higher_plus_one  = RegEnable(last_stage_pc(VAddrBits - 1, JMP_OFFSET_LEN + 1) + 1.U, stage_en)
       val last_stage_pc_higher_minus_one = RegEnable(last_stage_pc(VAddrBits - 1, JMP_OFFSET_LEN + 1) - 1.U, stage_en)
-      val last_stage_pc_middle_plus_one  = RegEnable(Cat(0.U(1.W), last_stage_pc(JMP_OFFSET_LEN, BR_OFFSET_LEN + 1)) + 1.U, stage_en)
-      val last_stage_pc_middle_minus_one = RegEnable(Cat(0.U(1.W), last_stage_pc(JMP_OFFSET_LEN, BR_OFFSET_LEN + 1)) - 1.U, stage_en)
+      val last_stage_pc_middle_plus_one =
+        RegEnable(Cat(0.U(1.W), last_stage_pc(JMP_OFFSET_LEN, BR_OFFSET_LEN + 1)) + 1.U, stage_en)
+      val last_stage_pc_middle_minus_one =
+        RegEnable(Cat(0.U(1.W), last_stage_pc(JMP_OFFSET_LEN, BR_OFFSET_LEN + 1)) - 1.U, stage_en)
 
       higher_br := Cat(last_stage_pc_higher, last_stage_pc_middle)
       higher_plus_one_br := Mux(
-          last_stage_pc_middle_plus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN),
-          Cat(last_stage_pc_higher_plus_one, last_stage_pc_middle_plus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN-1, 0)),
-          Cat(last_stage_pc_higher, last_stage_pc_middle_plus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN-1, 0)))
+        last_stage_pc_middle_plus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN),
+        Cat(last_stage_pc_higher_plus_one, last_stage_pc_middle_plus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN - 1, 0)),
+        Cat(last_stage_pc_higher, last_stage_pc_middle_plus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN - 1, 0))
+      )
       higher_minus_one_br := Mux(
-          last_stage_pc_middle_minus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN),
-          Cat(last_stage_pc_higher_minus_one, last_stage_pc_middle_minus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN-1, 0)),
-          Cat(last_stage_pc_higher, last_stage_pc_middle_minus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN-1, 0)))
+        last_stage_pc_middle_minus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN),
+        Cat(last_stage_pc_higher_minus_one, last_stage_pc_middle_minus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN - 1, 0)),
+        Cat(last_stage_pc_higher, last_stage_pc_middle_minus_one(JMP_OFFSET_LEN - BR_OFFSET_LEN - 1, 0))
+      )
 
-      higher_tail := last_stage_pc_higher
-      higher_plus_one_tail := last_stage_pc_higher_plus_one
+      higher_tail           := last_stage_pc_higher
+      higher_plus_one_tail  := last_stage_pc_higher_plus_one
       higher_minus_one_tail := last_stage_pc_higher_minus_one
-    }else{
-      higher_br := h_br
-      higher_plus_one_br := h_br + 1.U
-      higher_minus_one_br := h_br - 1.U
-      higher_tail := h_tail
-      higher_plus_one_tail := h_tail + 1.U
+    } else {
+      higher_br             := h_br
+      higher_plus_one_br    := h_br + 1.U
+      higher_minus_one_br   := h_br - 1.U
+      higher_tail           := h_tail
+      higher_plus_one_tail  := h_tail + 1.U
       higher_minus_one_tail := h_tail - 1.U
     }
     val br_slots_targets = VecInit(brSlots.map(s =>
       Cat(
-          Mux1H(Seq(
-            (s.tarStat === TAR_OVF, higher_plus_one_br),
-            (s.tarStat === TAR_UDF, higher_minus_one_br),
-            (s.tarStat === TAR_FIT, higher_br),
-          )),
-          s.lower(s.offsetLen-1, 0), 0.U(1.W)
-        )
+        Mux1H(Seq(
+          (s.tarStat === TAR_OVF, higher_plus_one_br),
+          (s.tarStat === TAR_UDF, higher_minus_one_br),
+          (s.tarStat === TAR_FIT, higher_br)
+        )),
+        s.lower(s.offsetLen - 1, 0),
+        0.U(1.W)
+      )
     ))
     val tail_target = Wire(UInt(VAddrBits.W))
-    if(tailSlot.subOffsetLen.isDefined){
-      tail_target := Mux(tailSlot.sharing,
+    if (tailSlot.subOffsetLen.isDefined) {
+      tail_target := Mux(
+        tailSlot.sharing,
         Cat(
           Mux1H(Seq(
             (tailSlot.tarStat === TAR_OVF, higher_plus_one_br),
             (tailSlot.tarStat === TAR_UDF, higher_minus_one_br),
-            (tailSlot.tarStat === TAR_FIT, higher_br),
+            (tailSlot.tarStat === TAR_FIT, higher_br)
           )),
-          tailSlot.lower(tailSlot.subOffsetLen.get-1, 0), 0.U(1.W)
+          tailSlot.lower(tailSlot.subOffsetLen.get - 1, 0),
+          0.U(1.W)
         ),
         Cat(
           Mux1H(Seq(
             (tailSlot.tarStat === TAR_OVF, higher_plus_one_tail),
             (tailSlot.tarStat === TAR_UDF, higher_minus_one_tail),
-            (tailSlot.tarStat === TAR_FIT, higher_tail),
+            (tailSlot.tarStat === TAR_FIT, higher_tail)
           )),
-          tailSlot.lower(tailSlot.offsetLen-1, 0), 0.U(1.W)
+          tailSlot.lower(tailSlot.offsetLen - 1, 0),
+          0.U(1.W)
         )
       )
-    }else{
+    } else {
       tail_target := Cat(
-          Mux1H(Seq(
-            (tailSlot.tarStat === TAR_OVF, higher_plus_one_tail),
-            (tailSlot.tarStat === TAR_UDF, higher_minus_one_tail),
-            (tailSlot.tarStat === TAR_FIT, higher_tail),
-          )),
-          tailSlot.lower(tailSlot.offsetLen-1, 0), 0.U(1.W)
-        )
+        Mux1H(Seq(
+          (tailSlot.tarStat === TAR_OVF, higher_plus_one_tail),
+          (tailSlot.tarStat === TAR_UDF, higher_minus_one_tail),
+          (tailSlot.tarStat === TAR_FIT, higher_tail)
+        )),
+        tailSlot.lower(tailSlot.offsetLen - 1, 0),
+        0.U(1.W)
+      )
     }
 
     br_slots_targets.map(t => require(t.getWidth == VAddrBits))
@@ -312,75 +313,71 @@ class FTBEntry(implicit p: Parameters) extends FTBEntry_part with FTBParams with
   }
 
   def getOffsetVec = VecInit(brSlots.map(_.offset) :+ tailSlot.offset)
-  def getFallThrough(pc: UInt, last_stage_entry: Option[Tuple2[FTBEntry, Bool]] = None) = {
+  def getFallThrough(pc: UInt, last_stage_entry: Option[Tuple2[FTBEntry, Bool]] = None) =
     if (last_stage_entry.isDefined) {
       var stashed_carry = RegEnable(last_stage_entry.get._1.carry, last_stage_entry.get._2)
       getFallThroughAddr(pc, stashed_carry, pftAddr)
     } else {
       getFallThroughAddr(pc, carry, pftAddr)
     }
-  }
 
   def hasBr(offset: UInt) =
-    brSlots.map{ s => s.valid && s.offset <= offset}.reduce(_||_) ||
-    (tailSlot.valid && tailSlot.offset <= offset && tailSlot.sharing)
+    brSlots.map(s => s.valid && s.offset <= offset).reduce(_ || _) ||
+      (tailSlot.valid && tailSlot.offset <= offset && tailSlot.sharing)
 
   def getBrMaskByOffset(offset: UInt) =
-    brSlots.map{ s => s.valid && s.offset <= offset } :+
-    (tailSlot.valid && tailSlot.offset <= offset && tailSlot.sharing)
+    brSlots.map { s =>
+      s.valid && s.offset <= offset
+    } :+
+      (tailSlot.valid && tailSlot.offset <= offset && tailSlot.sharing)
 
-  def getBrRecordedVec(offset: UInt) = {
+  def getBrRecordedVec(offset: UInt) =
     VecInit(
       brSlots.map(s => s.valid && s.offset === offset) :+
-      (tailSlot.valid && tailSlot.offset === offset && tailSlot.sharing)
+        (tailSlot.valid && tailSlot.offset === offset && tailSlot.sharing)
     )
-  }
 
-  def brIsSaved(offset: UInt) = getBrRecordedVec(offset).reduce(_||_)
+  def brIsSaved(offset: UInt) = getBrRecordedVec(offset).reduce(_ || _)
 
-  def brValids = {
+  def brValids =
     VecInit(
       brSlots.map(_.valid) :+ (tailSlot.valid && tailSlot.sharing)
     )
-  }
 
-  def noEmptySlotForNewBr = {
-    VecInit(brSlots.map(_.valid) :+ tailSlot.valid).reduce(_&&_)
-  }
+  def noEmptySlotForNewBr =
+    VecInit(brSlots.map(_.valid) :+ tailSlot.valid).reduce(_ && _)
 
   def newBrCanNotInsert(offset: UInt) = {
     val lastSlotForBr = tailSlot
     lastSlotForBr.valid && lastSlotForBr.offset < offset
   }
 
-  def jmpValid = {
+  def jmpValid =
     tailSlot.valid && !tailSlot.sharing
-  }
 
-  def brOffset = {
+  def brOffset =
     VecInit(brSlots.map(_.offset) :+ tailSlot.offset)
-  }
 
   def entryConsistent(that: FTBEntry) = {
-    val validDiff     = this.valid === that.valid
-    val brSlotsDiffSeq  : IndexedSeq[Bool] =
-      this.brSlots.zip(that.brSlots).map{
-        case(x, y) => x.slotConsistent(y)
+    val validDiff = this.valid === that.valid
+    val brSlotsDiffSeq: IndexedSeq[Bool] =
+      this.brSlots.zip(that.brSlots).map {
+        case (x, y) => x.slotConsistent(y)
       }
-    val tailSlotDiff  = this.tailSlot.slotConsistent(that.tailSlot)
-    val pftAddrDiff   = this.pftAddr === that.pftAddr
-    val carryDiff     = this.carry   === that.carry
-    val isCallDiff    = this.isCall  === that.isCall
-    val isRetDiff     = this.isRet   === that.isRet
-    val isJalrDiff    = this.isJalr  === that.isJalr
+    val tailSlotDiff         = this.tailSlot.slotConsistent(that.tailSlot)
+    val pftAddrDiff          = this.pftAddr === that.pftAddr
+    val carryDiff            = this.carry === that.carry
+    val isCallDiff           = this.isCall === that.isCall
+    val isRetDiff            = this.isRet === that.isRet
+    val isJalrDiff           = this.isJalr === that.isJalr
     val lastMayBeRviCallDiff = this.last_may_be_rvi_call === that.last_may_be_rvi_call
-    val alwaysTakenDiff : IndexedSeq[Bool] =
-      this.always_taken.zip(that.always_taken).map{
-        case(x, y) => x === y
+    val alwaysTakenDiff: IndexedSeq[Bool] =
+      this.always_taken.zip(that.always_taken).map {
+        case (x, y) => x === y
       }
     VecInit(
       validDiff,
-      brSlotsDiffSeq.reduce(_&&_),
+      brSlotsDiffSeq.reduce(_ && _),
       tailSlotDiff,
       pftAddrDiff,
       carryDiff,
@@ -388,19 +385,25 @@ class FTBEntry(implicit p: Parameters) extends FTBEntry_part with FTBParams with
       isRetDiff,
       isJalrDiff,
       lastMayBeRviCallDiff,
-      alwaysTakenDiff.reduce(_&&_)
-    ).reduce(_&&_)
+      alwaysTakenDiff.reduce(_ && _)
+    ).reduce(_ && _)
   }
 
   def display(cond: Bool): Unit = {
     XSDebug(cond, p"-----------FTB entry----------- \n")
     XSDebug(cond, p"v=${valid}\n")
-    for(i <- 0 until numBr) {
-      XSDebug(cond, p"[br$i]: v=${allSlotsForBr(i).valid}, offset=${allSlotsForBr(i).offset}," +
-        p"lower=${Hexadecimal(allSlotsForBr(i).lower)}\n")
+    for (i <- 0 until numBr) {
+      XSDebug(
+        cond,
+        p"[br$i]: v=${allSlotsForBr(i).valid}, offset=${allSlotsForBr(i).offset}," +
+          p"lower=${Hexadecimal(allSlotsForBr(i).lower)}\n"
+      )
     }
-    XSDebug(cond, p"[tailSlot]: v=${tailSlot.valid}, offset=${tailSlot.offset}," +
-      p"lower=${Hexadecimal(tailSlot.lower)}, sharing=${tailSlot.sharing}}\n")
+    XSDebug(
+      cond,
+      p"[tailSlot]: v=${tailSlot.valid}, offset=${tailSlot.offset}," +
+        p"lower=${Hexadecimal(tailSlot.lower)}, sharing=${tailSlot.sharing}}\n"
+    )
     XSDebug(cond, p"pftAddr=${Hexadecimal(pftAddr)}, carry=$carry\n")
     XSDebug(cond, p"isCall=$isCall, isRet=$isRet, isjalr=$isJalr\n")
     XSDebug(cond, p"last_may_be_rvi_call=$last_may_be_rvi_call\n")
@@ -411,7 +414,7 @@ class FTBEntry(implicit p: Parameters) extends FTBEntry_part with FTBParams with
 
 class FTBEntryWithTag(implicit p: Parameters) extends XSBundle with FTBParams with BPUUtils {
   val entry = new FTBEntry
-  val tag = UInt(tagSize.W)
+  val tag   = UInt(tagSize.W)
   def display(cond: Bool): Unit = {
     entry.display(cond)
     XSDebug(cond, p"tag is ${Hexadecimal(tag)}\n------------------------------- \n")
@@ -419,8 +422,8 @@ class FTBEntryWithTag(implicit p: Parameters) extends XSBundle with FTBParams wi
 }
 
 class FTBMeta(implicit p: Parameters) extends XSBundle with FTBParams {
-  val writeWay = UInt(log2Ceil(numWays).W)
-  val hit = Bool()
+  val writeWay   = UInt(log2Ceil(numWays).W)
+  val hit        = Bool()
   val pred_cycle = if (!env.FPGAPlatform) Some(UInt(64.W)) else None
 }
 
@@ -428,7 +431,7 @@ object FTBMeta {
   def apply(writeWay: UInt, hit: Bool, pred_cycle: UInt)(implicit p: Parameters): FTBMeta = {
     val e = Wire(new FTBMeta)
     e.writeWay := writeWay
-    e.hit := hit
+    e.hit      := hit
     e.pred_cycle.map(_ := pred_cycle)
     e
   }
@@ -452,7 +455,6 @@ object FTBMeta {
 //   }
 // }
 
-
 class FTBTableAddr(val idxBits: Int, val banks: Int, val skewedBits: Int)(implicit p: Parameters) extends XSBundle {
   val addr = new TableAddr(idxBits, banks)
   def getIdx(x: UInt) = addr.getIdx(x) ^ Cat(addr.getTag(x), addr.getIdx(x))(idxBits + skewedBits - 1, skewedBits)
@@ -460,7 +462,7 @@ class FTBTableAddr(val idxBits: Int, val banks: Int, val skewedBits: Int)(implic
 }
 
 class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUUtils
-  with HasCircularQueuePtrHelper with HasPerfEvents {
+    with HasCircularQueuePtrHelper with HasPerfEvents {
   override val meta_size = WireInit(0.U.asTypeOf(new FTBMeta)).getWidth
 
   val ftbAddr = new FTBTableAddr(log2Up(numSets), 1, 3)
@@ -472,80 +474,94 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
       // when ftb hit, read_hits.valid is true, and read_hits.bits is OH of hit way
       // when ftb not hit, read_hits.valid is false, and read_hits is OH of allocWay
       // val read_hits = Valid(Vec(numWays, Bool()))
-      val req_pc = Flipped(DecoupledIO(UInt(VAddrBits.W)))
+      val req_pc    = Flipped(DecoupledIO(UInt(VAddrBits.W)))
       val read_resp = Output(new FTBEntry)
       val read_hits = Valid(UInt(log2Ceil(numWays).W))
 
       val read_multi_entry = Output(new FTBEntry)
-      val read_multi_hits = Valid(UInt(log2Ceil(numWays).W))
+      val read_multi_hits  = Valid(UInt(log2Ceil(numWays).W))
 
-      val u_req_pc = Flipped(DecoupledIO(UInt(VAddrBits.W)))
-      val update_hits = Valid(UInt(log2Ceil(numWays).W))
+      val u_req_pc      = Flipped(DecoupledIO(UInt(VAddrBits.W)))
+      val update_hits   = Valid(UInt(log2Ceil(numWays).W))
       val update_access = Input(Bool())
 
-      val update_pc = Input(UInt(VAddrBits.W))
-      val update_write_data = Flipped(Valid(new FTBEntryWithTag))
-      val update_write_way = Input(UInt(log2Ceil(numWays).W))
+      val update_pc          = Input(UInt(VAddrBits.W))
+      val update_write_data  = Flipped(Valid(new FTBEntryWithTag))
+      val update_write_way   = Input(UInt(log2Ceil(numWays).W))
       val update_write_alloc = Input(Bool())
     })
 
     // Extract holdRead logic to fix bug that update read override predict read result
-    val ftb = Module(new SRAMTemplate(new FTBEntryWithTag, set = numSets, way = numWays, shouldReset = true, holdRead = false, singlePort = true))
+    val ftb = Module(new SRAMTemplate(
+      new FTBEntryWithTag,
+      set = numSets,
+      way = numWays,
+      shouldReset = true,
+      holdRead = false,
+      singlePort = true
+    ))
     val ftb_r_entries = ftb.io.r.resp.data.map(_.entry)
 
-    val pred_rdata   = HoldUnless(ftb.io.r.resp.data, RegNext(io.req_pc.valid && !io.update_access))
+    val pred_rdata = HoldUnless(ftb.io.r.resp.data, RegNext(io.req_pc.valid && !io.update_access))
     ftb.io.r.req.valid := io.req_pc.valid || io.u_req_pc.valid // io.s0_fire
-    ftb.io.r.req.bits.setIdx := Mux(io.u_req_pc.valid, ftbAddr.getIdx(io.u_req_pc.bits), ftbAddr.getIdx(io.req_pc.bits)) // s0_idx
+    ftb.io.r.req.bits.setIdx := Mux(
+      io.u_req_pc.valid,
+      ftbAddr.getIdx(io.u_req_pc.bits),
+      ftbAddr.getIdx(io.req_pc.bits)
+    ) // s0_idx
 
     assert(!(io.req_pc.valid && io.u_req_pc.valid))
 
-    io.req_pc.ready := ftb.io.r.req.ready
+    io.req_pc.ready   := ftb.io.r.req.ready
     io.u_req_pc.ready := ftb.io.r.req.ready
 
-    val req_tag = RegEnable(ftbAddr.getTag(io.req_pc.bits)(tagSize-1, 0), io.req_pc.valid)
+    val req_tag = RegEnable(ftbAddr.getTag(io.req_pc.bits)(tagSize - 1, 0), io.req_pc.valid)
     val req_idx = RegEnable(ftbAddr.getIdx(io.req_pc.bits), io.req_pc.valid)
 
-    val u_req_tag = RegEnable(ftbAddr.getTag(io.u_req_pc.bits)(tagSize-1, 0), io.u_req_pc.valid)
+    val u_req_tag = RegEnable(ftbAddr.getTag(io.u_req_pc.bits)(tagSize - 1, 0), io.u_req_pc.valid)
 
     val read_entries = pred_rdata.map(_.entry)
     val read_tags    = pred_rdata.map(_.tag)
 
-    val total_hits = VecInit((0 until numWays).map(b => read_tags(b) === req_tag && read_entries(b).valid && io.s1_fire))
-    val hit = total_hits.reduce(_||_)
+    val total_hits =
+      VecInit((0 until numWays).map(b => read_tags(b) === req_tag && read_entries(b).valid && io.s1_fire))
+    val hit = total_hits.reduce(_ || _)
     // val hit_way_1h = VecInit(PriorityEncoderOH(total_hits))
     val hit_way = OHToUInt(total_hits)
 
-    //There may be two hits in the four paths of the ftbBank, and the OHToUInt will fail.
-    //If there is a redirect in s2 at this time, the wrong FTBEntry will be used to calculate the target,
-    //resulting in an address error and affecting performance.
-    //The solution is to select a hit entry during multi hit as the entry for s2.
-    //Considering timing, use this entry in s3 and trigger s3-redirect.
-    val total_hits_reg = RegEnable(total_hits, io.s1_fire)
+    // There may be two hits in the four paths of the ftbBank, and the OHToUInt will fail.
+    // If there is a redirect in s2 at this time, the wrong FTBEntry will be used to calculate the target,
+    // resulting in an address error and affecting performance.
+    // The solution is to select a hit entry during multi hit as the entry for s2.
+    // Considering timing, use this entry in s3 and trigger s3-redirect.
+    val total_hits_reg   = RegEnable(total_hits, io.s1_fire)
     val read_entries_reg = read_entries.map(w => RegEnable(w, io.s1_fire))
 
-    val multi_hit = VecInit((0 until numWays).map{
-      i => (0 until numWays).map(j => {
-        if(i < j) total_hits_reg(i) && total_hits_reg(j)
-        else false.B
-      }).reduce(_||_)
-    }).reduce(_||_)
-    val multi_way = PriorityMux(Seq.tabulate(numWays)(i => ((total_hits_reg(i)) -> i.asUInt(log2Ceil(numWays).W))))
-    val multi_hit_selectEntry = PriorityMux(Seq.tabulate(numWays)(i => ((total_hits_reg(i)) -> read_entries_reg(i))))
-
-    //Check if the entry read by ftbBank is legal.
-    for (n <- 0 to numWays -1 ) {
-      val req_pc_reg = RegEnable(io.req_pc.bits, 0.U.asTypeOf(io.req_pc.bits), io.req_pc.valid)
+    val multi_hit = VecInit((0 until numWays).map {
+      i =>
+        (0 until numWays).map { j =>
+          if (i < j) total_hits_reg(i) && total_hits_reg(j)
+          else false.B
+        }.reduce(_ || _)
+    }).reduce(_ || _)
+    val multi_way = PriorityMux(Seq.tabulate(numWays)(i => (total_hits_reg(i)) -> i.asUInt(log2Ceil(numWays).W)))
+    val multi_hit_selectEntry = PriorityMux(Seq.tabulate(numWays)(i => (total_hits_reg(i)) -> read_entries_reg(i)))
+
+    // Check if the entry read by ftbBank is legal.
+    for (n <- 0 to numWays - 1) {
+      val req_pc_reg       = RegEnable(io.req_pc.bits, 0.U.asTypeOf(io.req_pc.bits), io.req_pc.valid)
       val req_pc_reg_lower = Cat(0.U(1.W), req_pc_reg(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
       val ftbEntryEndLowerwithCarry = Cat(read_entries(n).carry, read_entries(n).pftAddr)
-      val fallThroughErr = req_pc_reg_lower + (PredictWidth).U >= ftbEntryEndLowerwithCarry
-      when(read_entries(n).valid && total_hits(n) && io.s1_fire){
+      val fallThroughErr            = req_pc_reg_lower + PredictWidth.U >= ftbEntryEndLowerwithCarry
+      when(read_entries(n).valid && total_hits(n) && io.s1_fire) {
         assert(fallThroughErr, s"FTB read sram entry in way${n} fallThrough address error!")
       }
     }
 
     val u_total_hits = VecInit((0 until numWays).map(b =>
-        ftb.io.r.resp.data(b).tag === u_req_tag && ftb.io.r.resp.data(b).entry.valid && RegNext(io.update_access)))
-    val u_hit = u_total_hits.reduce(_||_)
+      ftb.io.r.resp.data(b).tag === u_req_tag && ftb.io.r.resp.data(b).entry.valid && RegNext(io.update_access)
+    ))
+    val u_hit = u_total_hits.reduce(_ || _)
     // val hit_way_1h = VecInit(PriorityEncoderOH(total_hits))
     val u_hit_way = OHToUInt(u_total_hits)
 
@@ -568,15 +584,15 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
     val read_set = Wire(UInt(log2Ceil(numSets).W))
     val read_way = Wire(Valid(UInt(log2Ceil(numWays).W)))
 
-    read_set := req_idx
+    read_set       := req_idx
     read_way.valid := hit
     read_way.bits  := hit_way
 
     // Read replacer access is postponed for 1 cycle
     // this helps timing
-    touch_set(0) := Mux(write_way.valid, write_set, RegNext(read_set))
+    touch_set(0)       := Mux(write_way.valid, write_set, RegNext(read_set))
     touch_way(0).valid := write_way.valid || RegNext(read_way.valid)
-    touch_way(0).bits := Mux(write_way.valid, write_way.bits, RegNext(read_way.bits))
+    touch_way(0).bits  := Mux(write_way.valid, write_way.bits, RegNext(read_way.bits))
 
     replacer.access(touch_set, touch_way)
 
@@ -584,9 +600,9 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
     // Selection logic:
     //    1. if any entries within the same index is not valid, select it
     //    2. if all entries is valid, use replacer
-    def allocWay(valids: UInt, idx: UInt): UInt = {
+    def allocWay(valids: UInt, idx: UInt): UInt =
       if (numWays > 1) {
-        val w = Wire(UInt(log2Up(numWays).W))
+        val w     = Wire(UInt(log2Up(numWays).W))
         val valid = WireInit(valids.andR)
         w := Mux(valid, replacer.way(idx), PriorityEncoder(~valids))
         w
@@ -594,224 +610,256 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
         val w = WireInit(0.U(log2Up(numWays).W))
         w
       }
-    }
 
-    io.read_resp := Mux1H(total_hits, read_entries) // Mux1H
+    io.read_resp       := Mux1H(total_hits, read_entries) // Mux1H
     io.read_hits.valid := hit
-    io.read_hits.bits := hit_way
+    io.read_hits.bits  := hit_way
 
-    io.read_multi_entry := multi_hit_selectEntry
+    io.read_multi_entry      := multi_hit_selectEntry
     io.read_multi_hits.valid := multi_hit
-    io.read_multi_hits.bits := multi_way
+    io.read_multi_hits.bits  := multi_way
 
     io.update_hits.valid := u_hit
-    io.update_hits.bits := u_hit_way
+    io.update_hits.bits  := u_hit_way
 
     // Update logic
-    val u_valid = io.update_write_data.valid
-    val u_data = io.update_write_data.bits
-    val u_idx = ftbAddr.getIdx(io.update_pc)
+    val u_valid       = io.update_write_data.valid
+    val u_data        = io.update_write_data.bits
+    val u_idx         = ftbAddr.getIdx(io.update_pc)
     val allocWriteWay = allocWay(RegNext(VecInit(ftb_r_entries.map(_.valid))).asUInt, u_idx)
-    val u_way = Mux(io.update_write_alloc, allocWriteWay, io.update_write_way)
-    val u_mask = UIntToOH(u_way)
+    val u_way         = Mux(io.update_write_alloc, allocWriteWay, io.update_write_way)
+    val u_mask        = UIntToOH(u_way)
 
     for (i <- 0 until numWays) {
       XSPerfAccumulate(f"ftb_replace_way$i", u_valid && io.update_write_alloc && u_way === i.U)
-      XSPerfAccumulate(f"ftb_replace_way${i}_has_empty", u_valid && io.update_write_alloc && !ftb_r_entries.map(_.valid).reduce(_&&_) && u_way === i.U)
+      XSPerfAccumulate(
+        f"ftb_replace_way${i}_has_empty",
+        u_valid && io.update_write_alloc && !ftb_r_entries.map(_.valid).reduce(_ && _) && u_way === i.U
+      )
       XSPerfAccumulate(f"ftb_hit_way$i", hit && !io.update_access && hit_way === i.U)
     }
 
     ftb.io.w.apply(u_valid, u_data, u_idx, u_mask)
 
     // for replacer
-    write_set := u_idx
+    write_set       := u_idx
     write_way.valid := u_valid
-    write_way.bits := Mux(io.update_write_alloc, allocWriteWay, io.update_write_way)
+    write_way.bits  := Mux(io.update_write_alloc, allocWriteWay, io.update_write_way)
 
     // print hit entry info
     Mux1H(total_hits, ftb.io.r.resp.data).display(true.B)
   } // FTBBank
 
-  //FTB switch register & temporary storage of fauftb prediction results
-  val s0_close_ftb_req = RegInit(false.B)
-  val s1_close_ftb_req = RegEnable(s0_close_ftb_req, false.B, io.s0_fire(0))
-  val s2_close_ftb_req = RegEnable(s1_close_ftb_req, false.B, io.s1_fire(0))
-  val s2_fauftb_ftb_entry_dup = io.s1_fire.map(f => RegEnable(io.fauftb_entry_in, f))
+  // FTB switch register & temporary storage of fauftb prediction results
+  val s0_close_ftb_req            = RegInit(false.B)
+  val s1_close_ftb_req            = RegEnable(s0_close_ftb_req, false.B, io.s0_fire(0))
+  val s2_close_ftb_req            = RegEnable(s1_close_ftb_req, false.B, io.s1_fire(0))
+  val s2_fauftb_ftb_entry_dup     = io.s1_fire.map(f => RegEnable(io.fauftb_entry_in, f))
   val s2_fauftb_ftb_entry_hit_dup = io.s1_fire.map(f => RegEnable(io.fauftb_entry_hit_in, f))
 
   val ftbBank = Module(new FTBBank(numSets, numWays))
 
-  //for close ftb read_req
+  // for close ftb read_req
   ftbBank.io.req_pc.valid := io.s0_fire(0) && !s0_close_ftb_req
-  ftbBank.io.req_pc.bits := s0_pc_dup(0)
+  ftbBank.io.req_pc.bits  := s0_pc_dup(0)
 
-  val s2_multi_hit = ftbBank.io.read_multi_hits.valid && io.s2_fire(0)
-  val s2_multi_hit_way = ftbBank.io.read_multi_hits.bits
-  val s2_multi_hit_entry = ftbBank.io.read_multi_entry
+  val s2_multi_hit        = ftbBank.io.read_multi_hits.valid && io.s2_fire(0)
+  val s2_multi_hit_way    = ftbBank.io.read_multi_hits.bits
+  val s2_multi_hit_entry  = ftbBank.io.read_multi_entry
   val s2_multi_hit_enable = s2_multi_hit && !s2_close_ftb_req
   XSPerfAccumulate("ftb_s2_multi_hit", s2_multi_hit)
   XSPerfAccumulate("ftb_s2_multi_hit_enable", s2_multi_hit_enable)
 
-  //After closing ftb, the entry output from s2 is the entry of FauFTB cached in s1
-  val btb_enable_dup = dup(RegNext(io.ctrl.btb_enable))
-  val s1_read_resp = Mux(s1_close_ftb_req, io.fauftb_entry_in, ftbBank.io.read_resp)
-  val s2_ftbBank_dup = io.s1_fire.map(f => RegEnable(ftbBank.io.read_resp, f))
+  // After closing ftb, the entry output from s2 is the entry of FauFTB cached in s1
+  val btb_enable_dup   = dup(RegNext(io.ctrl.btb_enable))
+  val s1_read_resp     = Mux(s1_close_ftb_req, io.fauftb_entry_in, ftbBank.io.read_resp)
+  val s2_ftbBank_dup   = io.s1_fire.map(f => RegEnable(ftbBank.io.read_resp, f))
   val s2_ftb_entry_dup = dup(0.U.asTypeOf(new FTBEntry))
-  for(((s2_fauftb_entry, s2_ftbBank_entry), s2_ftb_entry) <-
-    s2_fauftb_ftb_entry_dup zip s2_ftbBank_dup zip s2_ftb_entry_dup){
-      s2_ftb_entry := Mux(s2_close_ftb_req, s2_fauftb_entry, s2_ftbBank_entry)
-  }
-  val s3_ftb_entry_dup  = io.s2_fire.zip(s2_ftb_entry_dup).map {case (f, e) => RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit_entry, e), f)}
-  val real_s2_ftb_entry = Mux(s2_multi_hit_enable, s2_multi_hit_entry, s2_ftb_entry_dup(0))
-  val real_s2_pc        = s2_pc_dup(0).getAddr()
-  val real_s2_startLower          = Cat(0.U(1.W),    real_s2_pc(instOffsetBits+log2Ceil(PredictWidth)-1, instOffsetBits))
-  val real_s2_endLowerwithCarry   = Cat(real_s2_ftb_entry.carry, real_s2_ftb_entry.pftAddr)
-  val real_s2_fallThroughErr      = real_s2_startLower >= real_s2_endLowerwithCarry || real_s2_endLowerwithCarry > (real_s2_startLower + (PredictWidth).U)
-  val real_s3_fallThroughErr_dup  = io.s2_fire.map {f => RegEnable(real_s2_fallThroughErr, f)}
-  
-  //After closing ftb, the hit output from s2 is the hit of FauFTB cached in s1.
-  //s1_hit is the ftbBank hit.
-  val s1_hit = Mux(s1_close_ftb_req, false.B, ftbBank.io.read_hits.valid && io.ctrl.btb_enable)
+  for (
+    ((s2_fauftb_entry, s2_ftbBank_entry), s2_ftb_entry) <-
+      s2_fauftb_ftb_entry_dup zip s2_ftbBank_dup zip s2_ftb_entry_dup
+  ) {
+    s2_ftb_entry := Mux(s2_close_ftb_req, s2_fauftb_entry, s2_ftbBank_entry)
+  }
+  val s3_ftb_entry_dup = io.s2_fire.zip(s2_ftb_entry_dup).map { case (f, e) =>
+    RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit_entry, e), f)
+  }
+  val real_s2_ftb_entry         = Mux(s2_multi_hit_enable, s2_multi_hit_entry, s2_ftb_entry_dup(0))
+  val real_s2_pc                = s2_pc_dup(0).getAddr()
+  val real_s2_startLower        = Cat(0.U(1.W), real_s2_pc(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
+  val real_s2_endLowerwithCarry = Cat(real_s2_ftb_entry.carry, real_s2_ftb_entry.pftAddr)
+  val real_s2_fallThroughErr =
+    real_s2_startLower >= real_s2_endLowerwithCarry || real_s2_endLowerwithCarry > (real_s2_startLower + PredictWidth.U)
+  val real_s3_fallThroughErr_dup = io.s2_fire.map(f => RegEnable(real_s2_fallThroughErr, f))
+
+  // After closing ftb, the hit output from s2 is the hit of FauFTB cached in s1.
+  // s1_hit is the ftbBank hit.
+  val s1_hit         = Mux(s1_close_ftb_req, false.B, ftbBank.io.read_hits.valid && io.ctrl.btb_enable)
   val s2_ftb_hit_dup = io.s1_fire.map(f => RegEnable(s1_hit, 0.B, f))
-  val s2_hit_dup = dup(0.U.asTypeOf(Bool()))
-  for(((s2_fauftb_hit, s2_ftb_hit), s2_hit) <-
-    s2_fauftb_ftb_entry_hit_dup zip s2_ftb_hit_dup zip s2_hit_dup){
-      s2_hit := Mux(s2_close_ftb_req, s2_fauftb_hit, s2_ftb_hit)
-  }
-  val s3_hit_dup = io.s2_fire.zip(s2_hit_dup).map {case (f, h) => RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit, h), 0.B, f)}
-  val s3_multi_hit_dup = io.s2_fire.map(f => RegEnable(s2_multi_hit_enable,f))
-  val writeWay = Mux(s1_close_ftb_req, 0.U, ftbBank.io.read_hits.bits)
-  val s2_ftb_meta = RegEnable(FTBMeta(writeWay.asUInt, s1_hit, GTimer()).asUInt, io.s1_fire(0))
+  val s2_hit_dup     = dup(0.U.asTypeOf(Bool()))
+  for (
+    ((s2_fauftb_hit, s2_ftb_hit), s2_hit) <-
+      s2_fauftb_ftb_entry_hit_dup zip s2_ftb_hit_dup zip s2_hit_dup
+  ) {
+    s2_hit := Mux(s2_close_ftb_req, s2_fauftb_hit, s2_ftb_hit)
+  }
+  val s3_hit_dup = io.s2_fire.zip(s2_hit_dup).map { case (f, h) =>
+    RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit, h), 0.B, f)
+  }
+  val s3_multi_hit_dup  = io.s2_fire.map(f => RegEnable(s2_multi_hit_enable, f))
+  val writeWay          = Mux(s1_close_ftb_req, 0.U, ftbBank.io.read_hits.bits)
+  val s2_ftb_meta       = RegEnable(FTBMeta(writeWay.asUInt, s1_hit, GTimer()).asUInt, io.s1_fire(0))
   val s2_multi_hit_meta = FTBMeta(s2_multi_hit_way.asUInt, s2_multi_hit, GTimer()).asUInt
 
-  //Consistent count of entries for fauftb and ftb
+  // Consistent count of entries for fauftb and ftb
   val fauftb_ftb_entry_consistent_counter = RegInit(0.U(FTBCLOSE_THRESHOLD_SZ.W))
-  val fauftb_ftb_entry_consistent = s2_fauftb_ftb_entry_dup(0).entryConsistent(s2_ftbBank_dup(0))
-
-  //if close ftb_req, the counter need keep
-  when(io.s2_fire(0) && s2_fauftb_ftb_entry_hit_dup(0) && s2_ftb_hit_dup(0) ){
-    fauftb_ftb_entry_consistent_counter := Mux(fauftb_ftb_entry_consistent, fauftb_ftb_entry_consistent_counter + 1.U, 0.U)
-  } .elsewhen(io.s2_fire(0) && !s2_fauftb_ftb_entry_hit_dup(0) && s2_ftb_hit_dup(0) ){
+  val fauftb_ftb_entry_consistent         = s2_fauftb_ftb_entry_dup(0).entryConsistent(s2_ftbBank_dup(0))
+
+  // if close ftb_req, the counter need keep
+  when(io.s2_fire(0) && s2_fauftb_ftb_entry_hit_dup(0) && s2_ftb_hit_dup(0)) {
+    fauftb_ftb_entry_consistent_counter := Mux(
+      fauftb_ftb_entry_consistent,
+      fauftb_ftb_entry_consistent_counter + 1.U,
+      0.U
+    )
+  }.elsewhen(io.s2_fire(0) && !s2_fauftb_ftb_entry_hit_dup(0) && s2_ftb_hit_dup(0)) {
     fauftb_ftb_entry_consistent_counter := 0.U
   }
 
-  when((fauftb_ftb_entry_consistent_counter >= FTBCLOSE_THRESHOLD) && io.s0_fire(0)){
+  when((fauftb_ftb_entry_consistent_counter >= FTBCLOSE_THRESHOLD) && io.s0_fire(0)) {
     s0_close_ftb_req := true.B
   }
 
-  //Clear counter during false_hit or ifuRedirect
+  // Clear counter during false_hit or ifuRedirect
   val ftb_false_hit = WireInit(false.B)
-  val needReopen = s0_close_ftb_req && (ftb_false_hit || io.redirectFromIFU)
+  val needReopen    = s0_close_ftb_req && (ftb_false_hit || io.redirectFromIFU)
   ftb_false_hit := io.update.valid && io.update.bits.false_hit
-  when(needReopen){
+  when(needReopen) {
     fauftb_ftb_entry_consistent_counter := 0.U
-    s0_close_ftb_req := false.B
+    s0_close_ftb_req                    := false.B
   }
 
-  val s2_close_consistent = s2_fauftb_ftb_entry_dup(0).entryConsistent(s2_ftb_entry_dup(0))
+  val s2_close_consistent     = s2_fauftb_ftb_entry_dup(0).entryConsistent(s2_ftb_entry_dup(0))
   val s2_not_close_consistent = s2_ftbBank_dup(0).entryConsistent(s2_ftb_entry_dup(0))
 
-  when(s2_close_ftb_req && io.s2_fire(0)){
+  when(s2_close_ftb_req && io.s2_fire(0)) {
     assert(s2_close_consistent, s"Entry inconsistency after ftb req is closed!")
-  }.elsewhen(!s2_close_ftb_req &&  io.s2_fire(0)){
+  }.elsewhen(!s2_close_ftb_req && io.s2_fire(0)) {
     assert(s2_not_close_consistent, s"Entry inconsistency after ftb req is not closed!")
   }
 
-  val  reopenCounter = !s1_close_ftb_req && s2_close_ftb_req &&  io.s2_fire(0)
-  val  falseHitReopenCounter = ftb_false_hit && s1_close_ftb_req
+  val reopenCounter         = !s1_close_ftb_req && s2_close_ftb_req && io.s2_fire(0)
+  val falseHitReopenCounter = ftb_false_hit && s1_close_ftb_req
   XSPerfAccumulate("ftb_req_reopen_counter", reopenCounter)
   XSPerfAccumulate("false_hit_reopen_Counter", falseHitReopenCounter)
-  XSPerfAccumulate("ifuRedirec_needReopen",s1_close_ftb_req && io.redirectFromIFU)
-  XSPerfAccumulate("this_cycle_is_close",s2_close_ftb_req && io.s2_fire(0))
-  XSPerfAccumulate("this_cycle_is_open",!s2_close_ftb_req && io.s2_fire(0))
+  XSPerfAccumulate("ifuRedirec_needReopen", s1_close_ftb_req && io.redirectFromIFU)
+  XSPerfAccumulate("this_cycle_is_close", s2_close_ftb_req && io.s2_fire(0))
+  XSPerfAccumulate("this_cycle_is_open", !s2_close_ftb_req && io.s2_fire(0))
 
   // io.out.bits.resp := RegEnable(io.in.bits.resp_in(0), 0.U.asTypeOf(new BranchPredictionResp), io.s1_fire)
   io.out := io.in.bits.resp_in(0)
 
-  io.out.s2.full_pred.map {case fp => fp.multiHit := false.B}
-
-  io.out.s2.full_pred.zip(s2_hit_dup).map {case (fp, h) => fp.hit := h}
-  for (full_pred & s2_ftb_entry & s2_pc & s1_pc & s1_fire <-
-    io.out.s2.full_pred zip s2_ftb_entry_dup zip s2_pc_dup zip s1_pc_dup zip io.s1_fire) {
-      full_pred.fromFtbEntry(s2_ftb_entry,
-        s2_pc.getAddr(),
-        // Previous stage meta for better timing
-        Some(s1_pc, s1_fire),
-        Some(s1_read_resp, s1_fire)
-      )
+  io.out.s2.full_pred.map { case fp => fp.multiHit := false.B }
+
+  io.out.s2.full_pred.zip(s2_hit_dup).map { case (fp, h) => fp.hit := h }
+  for (
+    full_pred & s2_ftb_entry & s2_pc & s1_pc & s1_fire <-
+      io.out.s2.full_pred zip s2_ftb_entry_dup zip s2_pc_dup zip s1_pc_dup zip io.s1_fire
+  ) {
+    full_pred.fromFtbEntry(
+      s2_ftb_entry,
+      s2_pc.getAddr(),
+      // Previous stage meta for better timing
+      Some(s1_pc, s1_fire),
+      Some(s1_read_resp, s1_fire)
+    )
   }
 
-  io.out.s3.full_pred.zip(s3_hit_dup).map {case (fp, h) => fp.hit := h}
-  io.out.s3.full_pred.zip(s3_multi_hit_dup).map {case (fp, m) => fp.multiHit := m}
-  for (full_pred & s3_ftb_entry & s3_pc & s2_pc & s2_fire <-
-    io.out.s3.full_pred zip s3_ftb_entry_dup zip s3_pc_dup zip s2_pc_dup zip io.s2_fire)
-      full_pred.fromFtbEntry(s3_ftb_entry, s3_pc.getAddr(), Some((s2_pc.getAddr(), s2_fire)))
+  io.out.s3.full_pred.zip(s3_hit_dup).map { case (fp, h) => fp.hit := h }
+  io.out.s3.full_pred.zip(s3_multi_hit_dup).map { case (fp, m) => fp.multiHit := m }
+  for (
+    full_pred & s3_ftb_entry & s3_pc & s2_pc & s2_fire <-
+      io.out.s3.full_pred zip s3_ftb_entry_dup zip s3_pc_dup zip s2_pc_dup zip io.s2_fire
+  )
+    full_pred.fromFtbEntry(s3_ftb_entry, s3_pc.getAddr(), Some((s2_pc.getAddr(), s2_fire)))
 
   // Overwrite the fallThroughErr value
-  io.out.s3.full_pred.zipWithIndex.map {case(fp, i) => fp.fallThroughErr := real_s3_fallThroughErr_dup(i)}
+  io.out.s3.full_pred.zipWithIndex.map { case (fp, i) => fp.fallThroughErr := real_s3_fallThroughErr_dup(i) }
 
   io.out.last_stage_ftb_entry := s3_ftb_entry_dup(0)
-  io.out.last_stage_meta := RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit_meta, s2_ftb_meta), io.s2_fire(0))
-  io.out.s1_ftbCloseReq := s1_close_ftb_req
-  io.out.s1_uftbHit := io.fauftb_entry_hit_in
+  io.out.last_stage_meta      := RegEnable(Mux(s2_multi_hit_enable, s2_multi_hit_meta, s2_ftb_meta), io.s2_fire(0))
+  io.out.s1_ftbCloseReq       := s1_close_ftb_req
+  io.out.s1_uftbHit           := io.fauftb_entry_hit_in
   val s1_uftbHasIndirect = io.fauftb_entry_in.jmpValid &&
     io.fauftb_entry_in.isJalr && !io.fauftb_entry_in.isRet // uFTB determines that it's real JALR, RET and JAL are excluded
   io.out.s1_uftbHasIndirect := s1_uftbHasIndirect
 
   // always taken logic
   for (i <- 0 until numBr) {
-    for (out_fp & in_fp & s2_hit & s2_ftb_entry <-
-      io.out.s2.full_pred zip io.in.bits.resp_in(0).s2.full_pred zip s2_hit_dup zip s2_ftb_entry_dup)
+    for (
+      out_fp & in_fp & s2_hit & s2_ftb_entry <-
+        io.out.s2.full_pred zip io.in.bits.resp_in(0).s2.full_pred zip s2_hit_dup zip s2_ftb_entry_dup
+    )
       out_fp.br_taken_mask(i) := in_fp.br_taken_mask(i) || s2_hit && s2_ftb_entry.always_taken(i)
-    for (out_fp & in_fp & s3_hit & s3_ftb_entry <-
-      io.out.s3.full_pred zip io.in.bits.resp_in(0).s3.full_pred zip s3_hit_dup zip s3_ftb_entry_dup)
+    for (
+      out_fp & in_fp & s3_hit & s3_ftb_entry <-
+        io.out.s3.full_pred zip io.in.bits.resp_in(0).s3.full_pred zip s3_hit_dup zip s3_ftb_entry_dup
+    )
       out_fp.br_taken_mask(i) := in_fp.br_taken_mask(i) || s3_hit && s3_ftb_entry.always_taken(i)
   }
 
   // Update logic
   val update = io.update.bits
 
-  val u_meta = update.meta.asTypeOf(new FTBMeta)
+  val u_meta  = update.meta.asTypeOf(new FTBMeta)
   val u_valid = io.update.valid && !io.update.bits.old_entry
 
-  val (_, delay2_pc) = DelayNWithValid(update.pc, u_valid, 2)
+  val (_, delay2_pc)    = DelayNWithValid(update.pc, u_valid, 2)
   val (_, delay2_entry) = DelayNWithValid(update.ftb_entry, u_valid, 2)
 
-
-  val update_now = u_valid && u_meta.hit
+  val update_now       = u_valid && u_meta.hit
   val update_need_read = u_valid && !u_meta.hit
   // stall one more cycle because we use a whole cycle to do update read tag hit
-  io.s1_ready := ftbBank.io.req_pc.ready && !(update_need_read) && !RegNext(update_need_read)
+  io.s1_ready := ftbBank.io.req_pc.ready && !update_need_read && !RegNext(update_need_read)
 
   ftbBank.io.u_req_pc.valid := update_need_read
-  ftbBank.io.u_req_pc.bits := update.pc
-
-
+  ftbBank.io.u_req_pc.bits  := update.pc
 
   val ftb_write = Wire(new FTBEntryWithTag)
   ftb_write.entry := Mux(update_now, update.ftb_entry, delay2_entry)
-  ftb_write.tag   := ftbAddr.getTag(Mux(update_now, update.pc, delay2_pc))(tagSize-1, 0)
+  ftb_write.tag   := ftbAddr.getTag(Mux(update_now, update.pc, delay2_pc))(tagSize - 1, 0)
 
   val write_valid = update_now || DelayN(u_valid && !u_meta.hit, 2)
   val write_pc    = Mux(update_now, update.pc, delay2_pc)
 
   ftbBank.io.update_write_data.valid := write_valid
-  ftbBank.io.update_write_data.bits := ftb_write
-  ftbBank.io.update_pc          := write_pc
-  ftbBank.io.update_write_way   := Mux(update_now, u_meta.writeWay, RegNext(ftbBank.io.update_hits.bits)) // use it one cycle later
-  ftbBank.io.update_write_alloc := Mux(update_now, false.B, RegNext(!ftbBank.io.update_hits.valid)) // use it one cycle later
+  ftbBank.io.update_write_data.bits  := ftb_write
+  ftbBank.io.update_pc               := write_pc
+  ftbBank.io.update_write_way := Mux(
+    update_now,
+    u_meta.writeWay,
+    RegNext(ftbBank.io.update_hits.bits)
+  ) // use it one cycle later
+  ftbBank.io.update_write_alloc := Mux(
+    update_now,
+    false.B,
+    RegNext(!ftbBank.io.update_hits.valid)
+  ) // use it one cycle later
   ftbBank.io.update_access := u_valid && !u_meta.hit
-  ftbBank.io.s1_fire := io.s1_fire(0)
+  ftbBank.io.s1_fire       := io.s1_fire(0)
 
   val ftb_write_fallThrough = ftb_write.entry.getFallThrough(write_pc)
-  when(write_valid){
+  when(write_valid) {
     assert(write_pc + (FetchWidth * 4).U >= ftb_write_fallThrough, s"FTB write_entry fallThrough address error!")
   }
 
   XSDebug("req_v=%b, req_pc=%x, ready=%b (resp at next cycle)\n", io.s0_fire(0), s0_pc_dup(0), ftbBank.io.req_pc.ready)
   XSDebug("s2_hit=%b, hit_way=%b\n", s2_hit_dup(0), writeWay.asUInt)
-  XSDebug("s2_br_taken_mask=%b, s2_real_taken_mask=%b\n",
-    io.in.bits.resp_in(0).s2.full_pred(0).br_taken_mask.asUInt, io.out.s2.full_pred(0).real_slot_taken_mask().asUInt)
+  XSDebug(
+    "s2_br_taken_mask=%b, s2_real_taken_mask=%b\n",
+    io.in.bits.resp_in(0).s2.full_pred(0).br_taken_mask.asUInt,
+    io.out.s2.full_pred(0).real_slot_taken_mask().asUInt
+  )
   XSDebug("s2_target=%x\n", io.out.s2.getTarget(0))
 
   s2_ftb_entry_dup(0).display(true.B)
@@ -827,8 +875,8 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
   XSPerfAccumulate("ftb_updated", u_valid)
 
   override val perfEvents = Seq(
-    ("ftb_commit_hits            ", io.update.valid  &&  u_meta.hit),
-    ("ftb_commit_misses          ", io.update.valid  && !u_meta.hit),
+    ("ftb_commit_hits            ", io.update.valid && u_meta.hit),
+    ("ftb_commit_misses          ", io.update.valid && !u_meta.hit)
   )
   generatePerfEvent()
 }
diff --git a/src/main/scala/xiangshan/frontend/FauFTB.scala b/src/main/scala/xiangshan/frontend/FauFTB.scala
index 1ed10443110..6ca3dd6e8f8 100644
--- a/src/main/scala/xiangshan/frontend/FauFTB.scala
+++ b/src/main/scala/xiangshan/frontend/FauFTB.scala
@@ -16,123 +16,121 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import utils._
+import org.chipsalliance.cde.config.Parameters
+import scala.{Tuple2 => &}
 import utility._
+import utils._
 import xiangshan._
-import scala.{Tuple2 => &}
 
 trait FauFTBParams extends HasXSParameter with HasBPUConst {
   val numWays = 32
   val tagSize = 16
 
   val TAR_STAT_SZ = 2
-  def TAR_FIT = 0.U(TAR_STAT_SZ.W)
-  def TAR_OVF = 1.U(TAR_STAT_SZ.W)
-  def TAR_UDF = 2.U(TAR_STAT_SZ.W)
+  def TAR_FIT     = 0.U(TAR_STAT_SZ.W)
+  def TAR_OVF     = 1.U(TAR_STAT_SZ.W)
+  def TAR_UDF     = 2.U(TAR_STAT_SZ.W)
 
-  def BR_OFFSET_LEN = 12
+  def BR_OFFSET_LEN  = 12
   def JMP_OFFSET_LEN = 20
 
-  def getTag(pc: UInt) = pc(tagSize+instOffsetBits-1, instOffsetBits)
+  def getTag(pc: UInt) = pc(tagSize + instOffsetBits - 1, instOffsetBits)
 }
 
 class FauFTBEntry(implicit p: Parameters) extends FTBEntry()(p) {}
 
 class FauFTBWay(implicit p: Parameters) extends XSModule with FauFTBParams {
-  val io = IO(new Bundle{
-    val req_tag = Input(UInt(tagSize.W))
-    val resp = Output(new FauFTBEntry)
-    val resp_hit = Output(Bool())
+  val io = IO(new Bundle {
+    val req_tag        = Input(UInt(tagSize.W))
+    val resp           = Output(new FauFTBEntry)
+    val resp_hit       = Output(Bool())
     val update_req_tag = Input(UInt(tagSize.W))
-    val update_hit = Output(Bool())
-    val write_valid = Input(Bool())
-    val write_entry = Input(new FauFTBEntry)
-    val write_tag = Input(UInt(tagSize.W))
-    val tag_read = Output(UInt(tagSize.W))
+    val update_hit     = Output(Bool())
+    val write_valid    = Input(Bool())
+    val write_entry    = Input(new FauFTBEntry)
+    val write_tag      = Input(UInt(tagSize.W))
+    val tag_read       = Output(UInt(tagSize.W))
   })
 
-  val data = Reg(new FauFTBEntry)
-  val tag = Reg(UInt(tagSize.W))
+  val data  = Reg(new FauFTBEntry)
+  val tag   = Reg(UInt(tagSize.W))
   val valid = RegInit(false.B)
 
-  io.resp := data
+  io.resp     := data
   io.resp_hit := tag === io.req_tag && valid
   // write bypass to avoid multiple hit
   io.update_hit := ((tag === io.update_req_tag) && valid) ||
-                   ((io.write_tag === io.update_req_tag) && io.write_valid)
+    ((io.write_tag === io.update_req_tag) && io.write_valid)
   io.tag_read := tag
 
-  when (io.write_valid) {
-    when (!valid) {
+  when(io.write_valid) {
+    when(!valid) {
       valid := true.B
     }
-    tag   := io.write_tag
-    data  := io.write_entry
+    tag  := io.write_tag
+    data := io.write_entry
   }
 }
 
-
 class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
 
   class FauFTBMeta(implicit p: Parameters) extends XSBundle with FauFTBParams {
     val pred_way = if (!env.FPGAPlatform) Some(UInt(log2Ceil(numWays).W)) else None
-    val hit = Bool()
+    val hit      = Bool()
   }
-  val resp_meta = Wire(new FauFTBMeta)
-  override val meta_size = resp_meta.getWidth
+  val resp_meta             = Wire(new FauFTBMeta)
+  override val meta_size    = resp_meta.getWidth
   override val is_fast_pred = true
 
-
-
   val ways = Seq.tabulate(numWays)(w => Module(new FauFTBWay))
   // numWays * numBr
-  val ctrs = Seq.tabulate(numWays)(w => Seq.tabulate(numBr)(b => RegInit(2.U(2.W))))
-  val replacer = ReplacementPolicy.fromString("plru", numWays)
+  val ctrs                = Seq.tabulate(numWays)(w => Seq.tabulate(numBr)(b => RegInit(2.U(2.W))))
+  val replacer            = ReplacementPolicy.fromString("plru", numWays)
   val replacer_touch_ways = Wire(Vec(2, Valid(UInt(log2Ceil(numWays).W))))
 
-
   // pred req
   ways.foreach(_.io.req_tag := getTag(s1_pc_dup(0)))
 
   // pred resp
-  val s1_hit_oh = VecInit(ways.map(_.io.resp_hit)).asUInt
-  val s1_hit = s1_hit_oh.orR
-  val s1_hit_way = OHToUInt(s1_hit_oh)
+  val s1_hit_oh              = VecInit(ways.map(_.io.resp_hit)).asUInt
+  val s1_hit                 = s1_hit_oh.orR
+  val s1_hit_way             = OHToUInt(s1_hit_oh)
   val s1_possible_full_preds = Wire(Vec(numWays, new FullBranchPrediction(isNotS3 = true)))
 
   val s1_all_entries = VecInit(ways.map(_.io.resp))
   for (c & fp & e <- ctrs zip s1_possible_full_preds zip s1_all_entries) {
-    fp.hit := DontCare
+    fp.hit      := DontCare
     fp.multiHit := false.B
     fp.fromFtbEntry(e, s1_pc_dup(0))
     for (i <- 0 until numBr) {
       fp.br_taken_mask(i) := c(i)(1) || e.always_taken(i)
     }
   }
-  val s1_hit_full_pred = Mux1H(s1_hit_oh, s1_possible_full_preds)
-  val s1_hit_fauftbentry  = Mux1H(s1_hit_oh, s1_all_entries)
+  val s1_hit_full_pred   = Mux1H(s1_hit_oh, s1_possible_full_preds)
+  val s1_hit_fauftbentry = Mux1H(s1_hit_oh, s1_all_entries)
   XSError(PopCount(s1_hit_oh) > 1.U, "fauftb has multiple hits!\n")
   val fauftb_enable = RegNext(io.ctrl.ubtb_enable)
   io.out.s1.full_pred.map(_ := s1_hit_full_pred)
-  io.out.s1.full_pred.map(_ .hit := s1_hit && fauftb_enable)
-  io.fauftb_entry_out := s1_hit_fauftbentry
+  io.out.s1.full_pred.map(_.hit := s1_hit && fauftb_enable)
+  io.fauftb_entry_out     := s1_hit_fauftbentry
   io.fauftb_entry_hit_out := s1_hit && fauftb_enable
 
   // Illegal check for FTB entry reading
-  val s1_pc_startLower             = Cat(0.U(1.W), s1_pc_dup(0)(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
+  val s1_pc_startLower = Cat(0.U(1.W), s1_pc_dup(0)(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
   val uftb_entry_endLowerwithCarry = Cat(s1_hit_fauftbentry.carry, s1_hit_fauftbentry.pftAddr)
-  val fallThroughErr = s1_pc_startLower + (PredictWidth).U >= uftb_entry_endLowerwithCarry
-  when(io.s1_fire(0) && s1_hit){
+  val fallThroughErr               = s1_pc_startLower + PredictWidth.U >= uftb_entry_endLowerwithCarry
+  when(io.s1_fire(0) && s1_hit) {
     assert(fallThroughErr, s"FauFTB read entry fallThrough address error!")
   }
 
   // assign metas
   io.out.last_stage_meta := resp_meta.asUInt
-  resp_meta.hit := RegEnable(RegEnable(s1_hit, io.s1_fire(0)), io.s2_fire(0))
-  if(resp_meta.pred_way.isDefined) {resp_meta.pred_way.get := RegEnable(RegEnable(s1_hit_way, io.s1_fire(0)), io.s2_fire(0))}
+  resp_meta.hit          := RegEnable(RegEnable(s1_hit, io.s1_fire(0)), io.s2_fire(0))
+  if (resp_meta.pred_way.isDefined) {
+    resp_meta.pred_way.get := RegEnable(RegEnable(s1_hit_way, io.s1_fire(0)), io.s2_fire(0))
+  }
 
   // pred update replacer state
   val s1_fire = io.s1_fire(0)
@@ -144,25 +142,26 @@ class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
   // s1: alloc_way and write
 
   // s0
-  val u = io.update
-  val u_meta = u.bits.meta.asTypeOf(new FauFTBMeta)
+  val u        = io.update
+  val u_meta   = u.bits.meta.asTypeOf(new FauFTBMeta)
   val u_s0_tag = getTag(u.bits.pc)
   ways.foreach(_.io.update_req_tag := u_s0_tag)
   val u_s0_hit_oh = VecInit(ways.map(_.io.update_hit)).asUInt
-  val u_s0_hit = u_s0_hit_oh.orR
+  val u_s0_hit    = u_s0_hit_oh.orR
   val u_s0_br_update_valids =
     VecInit((0 until numBr).map(w =>
       u.bits.ftb_entry.brValids(w) && u.valid && !u.bits.ftb_entry.always_taken(w) &&
-      !(PriorityEncoder(u.bits.br_taken_mask) < w.U)))
+        !(PriorityEncoder(u.bits.br_taken_mask) < w.U)
+    ))
 
   // s1
-  val u_s1_valid = RegNext(u.valid)
-  val u_s1_tag       = RegEnable(u_s0_tag, u.valid)
-  val u_s1_hit_oh    = RegEnable(u_s0_hit_oh, u.valid)
-  val u_s1_hit       = RegEnable(u_s0_hit, u.valid)
-  val u_s1_alloc_way = replacer.way
-  val u_s1_write_way_oh = Mux(u_s1_hit, u_s1_hit_oh, UIntToOH(u_s1_alloc_way))
-  val u_s1_ftb_entry = RegEnable(u.bits.ftb_entry, u.valid)
+  val u_s1_valid            = RegNext(u.valid)
+  val u_s1_tag              = RegEnable(u_s0_tag, u.valid)
+  val u_s1_hit_oh           = RegEnable(u_s0_hit_oh, u.valid)
+  val u_s1_hit              = RegEnable(u_s0_hit, u.valid)
+  val u_s1_alloc_way        = replacer.way
+  val u_s1_write_way_oh     = Mux(u_s1_hit, u_s1_hit_oh, UIntToOH(u_s1_alloc_way))
+  val u_s1_ftb_entry        = RegEnable(u.bits.ftb_entry, u.valid)
   val u_s1_ways_write_valid = VecInit((0 until numWays).map(w => u_s1_write_way_oh(w).asBool && u_s1_valid))
   for (w <- 0 until numWays) {
     ways(w).io.write_valid := u_s1_ways_write_valid(w)
@@ -171,19 +170,22 @@ class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
   }
 
   // Illegal check for FTB entry writing
-  val uftb_write_pc = RegEnable(u.bits.pc, u.valid)
+  val uftb_write_pc          = RegEnable(u.bits.pc, u.valid)
   val uftb_write_fallThrough = u_s1_ftb_entry.getFallThrough(uftb_write_pc)
-  when(u_s1_valid && u_s1_hit){
-    assert(uftb_write_pc + (FetchWidth * 4).U >= uftb_write_fallThrough, s"FauFTB write entry fallThrough address error!")
+  when(u_s1_valid && u_s1_hit) {
+    assert(
+      uftb_write_pc + (FetchWidth * 4).U >= uftb_write_fallThrough,
+      s"FauFTB write entry fallThrough address error!"
+    )
   }
 
   // update saturating counters
   val u_s1_br_update_valids = RegEnable(u_s0_br_update_valids, u.valid)
-  val u_s1_br_takens        = RegEnable(u.bits.br_taken_mask,  u.valid)
+  val u_s1_br_takens        = RegEnable(u.bits.br_taken_mask, u.valid)
   for (w <- 0 until numWays) {
-    when (u_s1_ways_write_valid(w)) {
+    when(u_s1_ways_write_valid(w)) {
       for (br <- 0 until numBr) {
-        when (u_s1_br_update_valids(br)) {
+        when(u_s1_br_update_valids(br)) {
           ctrs(w)(br) := satUpdate(ctrs(w)(br), 2, u_s1_br_takens(br))
         }
       }
@@ -197,21 +199,20 @@ class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
   /******** update replacer *********/
   replacer.access(replacer_touch_ways)
 
-
   /********************** perf counters **********************/
   val s0_fire_next_cycle = RegNext(io.s0_fire(0))
-  val u_pred_hit_way_map   = (0 until numWays).map(w => s0_fire_next_cycle && s1_hit && s1_hit_way === w.U)
-  XSPerfAccumulate("uftb_read_hits",   s0_fire_next_cycle &&  s1_hit)
+  val u_pred_hit_way_map = (0 until numWays).map(w => s0_fire_next_cycle && s1_hit && s1_hit_way === w.U)
+  XSPerfAccumulate("uftb_read_hits", s0_fire_next_cycle && s1_hit)
   XSPerfAccumulate("uftb_read_misses", s0_fire_next_cycle && !s1_hit)
-  XSPerfAccumulate("uftb_commit_hits",   u.valid &&  u_meta.hit)
+  XSPerfAccumulate("uftb_commit_hits", u.valid && u_meta.hit)
   XSPerfAccumulate("uftb_commit_misses", u.valid && !u_meta.hit)
   XSPerfAccumulate("uftb_commit_read_hit_pred_miss", u.valid && !u_meta.hit && u_s0_hit_oh.orR)
   for (w <- 0 until numWays) {
-    XSPerfAccumulate(f"uftb_pred_hit_way_${w}",   u_pred_hit_way_map(w))
+    XSPerfAccumulate(f"uftb_pred_hit_way_${w}", u_pred_hit_way_map(w))
     XSPerfAccumulate(f"uftb_replace_way_${w}", !u_s1_hit && u_s1_alloc_way === w.U)
   }
 
-  if(u_meta.pred_way.isDefined) {
+  if (u_meta.pred_way.isDefined) {
     val u_commit_hit_way_map = (0 until numWays).map(w => u.valid && u_meta.hit && u_meta.pred_way.get === w.U)
     for (w <- 0 until numWays) {
       XSPerfAccumulate(f"uftb_commit_hit_way_${w}", u_commit_hit_way_map(w))
@@ -219,9 +220,9 @@ class FauFTB(implicit p: Parameters) extends BasePredictor with FauFTBParams {
   }
 
   override val perfEvents = Seq(
-    ("fauftb_commit_hit       ", u.valid &&  u_meta.hit),
-    ("fauftb_commit_miss      ", u.valid && !u_meta.hit),
+    ("fauftb_commit_hit       ", u.valid && u_meta.hit),
+    ("fauftb_commit_miss      ", u.valid && !u_meta.hit)
   )
   generatePerfEvent()
 
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/Frontend.scala b/src/main/scala/xiangshan/frontend/Frontend.scala
index fde3f94dee5..941e442283b 100644
--- a/src/main/scala/xiangshan/frontend/Frontend.scala
+++ b/src/main/scala/xiangshan/frontend/Frontend.scala
@@ -15,25 +15,29 @@
 ***************************************************************************************/
 
 package xiangshan.frontend
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
-import utils._
+import freechips.rocketchip.diplomacy.LazyModule
+import freechips.rocketchip.diplomacy.LazyModuleImp
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
 import xiangshan._
-import xiangshan.backend.fu.{PFEvent, PMP, PMPChecker, PMPReqBundle}
+import xiangshan.backend.fu.PFEvent
+import xiangshan.backend.fu.PMP
+import xiangshan.backend.fu.PMPChecker
+import xiangshan.backend.fu.PMPReqBundle
 import xiangshan.cache.mmu._
 import xiangshan.frontend.icache._
 
 class Frontend()(implicit p: Parameters) extends LazyModule with HasXSParameter {
   override def shouldBeInlined: Boolean = false
-  val inner = LazyModule(new FrontendInlined)
+  val inner       = LazyModule(new FrontendInlined)
   lazy val module = new FrontendImp(this)
 }
 
 class FrontendImp(wrapper: Frontend)(implicit p: Parameters) extends LazyModuleImp(wrapper) {
-  val io = IO(wrapper.inner.module.io.cloneType)
+  val io      = IO(wrapper.inner.module.io.cloneType)
   val io_perf = IO(wrapper.inner.module.io_perf.cloneType)
   io <> wrapper.inner.module.io
   io_perf <> wrapper.inner.module.io_perf
@@ -45,29 +49,28 @@ class FrontendImp(wrapper: Frontend)(implicit p: Parameters) extends LazyModuleI
 class FrontendInlined()(implicit p: Parameters) extends LazyModule with HasXSParameter {
   override def shouldBeInlined: Boolean = true
 
-  val instrUncache  = LazyModule(new InstrUncache())
-  val icache        = LazyModule(new ICache())
+  val instrUncache = LazyModule(new InstrUncache())
+  val icache       = LazyModule(new ICache())
 
   lazy val module = new FrontendInlinedImp(this)
 }
 
-class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
-  with HasXSParameter
-  with HasPerfEvents
-{
+class FrontendInlinedImp(outer: FrontendInlined) extends LazyModuleImp(outer)
+    with HasXSParameter
+    with HasPerfEvents {
   val io = IO(new Bundle() {
-    val hartId = Input(UInt(hartIdLen.W))
+    val hartId       = Input(UInt(hartIdLen.W))
     val reset_vector = Input(UInt(PAddrBits.W))
-    val fencei = Input(Bool())
-    val ptw = new TlbPtwIO()
-    val backend = new FrontendToCtrlIO
+    val fencei       = Input(Bool())
+    val ptw          = new TlbPtwIO()
+    val backend      = new FrontendToCtrlIO
     val softPrefetch = Vec(backendParams.LduCnt, Flipped(Valid(new SoftIfetchPrefetchBundle)))
-    val sfence = Input(new SfenceBundle)
-    val tlbCsr = Input(new TlbCsrBundle)
-    val csrCtrl = Input(new CustomCSRCtrlIO)
-    val error  = ValidIO(new L1CacheErrorInfo)
+    val sfence       = Input(new SfenceBundle)
+    val tlbCsr       = Input(new TlbCsrBundle)
+    val csrCtrl      = Input(new CustomCSRCtrlIO)
+    val error        = ValidIO(new L1CacheErrorInfo)
     val frontendInfo = new Bundle {
-      val ibufFull  = Output(Bool())
+      val ibufFull = Output(Bool())
       val bpuInfo = new Bundle {
         val bpRight = Output(UInt(XLEN.W))
         val bpWrong = Output(UInt(XLEN.W))
@@ -79,40 +82,40 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
     }
   })
 
-  //decouped-frontend modules
+  // decouped-frontend modules
   val instrUncache = outer.instrUncache.module
   val icache       = outer.icache.module
-  val bpu     = Module(new Predictor)
-  val ifu     = Module(new NewIFU)
-  val ibuffer =  Module(new IBuffer)
-  val ftq = Module(new Ftq)
+  val bpu          = Module(new Predictor)
+  val ifu          = Module(new NewIFU)
+  val ibuffer      = Module(new IBuffer)
+  val ftq          = Module(new Ftq)
 
-  val needFlush = RegNext(io.backend.toFtq.redirect.valid)
+  val needFlush            = RegNext(io.backend.toFtq.redirect.valid)
   val FlushControlRedirect = RegNext(io.backend.toFtq.redirect.bits.debugIsCtrl)
-  val FlushMemVioRedirect = RegNext(io.backend.toFtq.redirect.bits.debugIsMemVio)
-  val FlushControlBTBMiss = Wire(Bool())
-  val FlushTAGEMiss = Wire(Bool())
-  val FlushSCMiss = Wire(Bool())
-  val FlushITTAGEMiss = Wire(Bool())
-  val FlushRASMiss = Wire(Bool())
-
-  val tlbCsr = DelayN(io.tlbCsr, 2)
+  val FlushMemVioRedirect  = RegNext(io.backend.toFtq.redirect.bits.debugIsMemVio)
+  val FlushControlBTBMiss  = Wire(Bool())
+  val FlushTAGEMiss        = Wire(Bool())
+  val FlushSCMiss          = Wire(Bool())
+  val FlushITTAGEMiss      = Wire(Bool())
+  val FlushRASMiss         = Wire(Bool())
+
+  val tlbCsr  = DelayN(io.tlbCsr, 2)
   val csrCtrl = DelayN(io.csrCtrl, 2)
-  val sfence = RegNext(RegNext(io.sfence))
+  val sfence  = RegNext(RegNext(io.sfence))
 
   // trigger
   ifu.io.frontendTrigger := csrCtrl.frontend_trigger
 
   // bpu ctrl
-  bpu.io.ctrl := csrCtrl.bp_ctrl
+  bpu.io.ctrl         := csrCtrl.bp_ctrl
   bpu.io.reset_vector := io.reset_vector
 
 // pmp
   val PortNumber = ICacheParameters().PortNumber
-  val pmp = Module(new PMP())
-  val pmp_check = VecInit(Seq.fill(coreParams.ipmpPortNum)(Module(new PMPChecker(3, sameCycle = true)).io))
+  val pmp        = Module(new PMP())
+  val pmp_check  = VecInit(Seq.fill(coreParams.ipmpPortNum)(Module(new PMPChecker(3, sameCycle = true)).io))
   pmp.io.distribute_csr := csrCtrl.distribute_csr
-  val pmp_req_vec     = Wire(Vec(coreParams.ipmpPortNum, Valid(new PMPReqBundle())))
+  val pmp_req_vec = Wire(Vec(coreParams.ipmpPortNum, Valid(new PMPReqBundle())))
   (0 until 2 * PortNumber).foreach(i => pmp_req_vec(i) <> icache.io.pmp(i).req)
   pmp_req_vec.last <> ifu.io.pmp.req
 
@@ -122,9 +125,9 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
   (0 until 2 * PortNumber).foreach(i => icache.io.pmp(i).resp <> pmp_check(i).resp)
   ifu.io.pmp.resp <> pmp_check.last.resp
 
-  val itlb = Module(new TLB(coreParams.itlbPortNum, nRespDups = 1,
-    Seq.fill(PortNumber)(false) ++ Seq(true), itlbParams))
-  itlb.io.requestor.take(PortNumber) zip icache.io.itlb foreach {case (a,b) => a <> b}
+  val itlb =
+    Module(new TLB(coreParams.itlbPortNum, nRespDups = 1, Seq.fill(PortNumber)(false) ++ Seq(true), itlbParams))
+  itlb.io.requestor.take(PortNumber) zip icache.io.itlb foreach { case (a, b) => a <> b }
   itlb.io.requestor.last <> ifu.io.iTLBInter // mmio may need re-tlb, blocked
   itlb.io.hartId := io.hartId
   itlb.io.base_connect(sfence, tlbCsr)
@@ -134,31 +137,32 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
   val itlb_ptw = Wire(new VectorTlbPtwIO(coreParams.itlbPortNum))
   itlb_ptw.connect(itlb.io.ptw)
   val itlbRepeater1 = PTWFilter(itlbParams.fenceDelay, itlb_ptw, sfence, tlbCsr, l2tlbParams.ifilterSize)
-  val itlbRepeater2 = PTWRepeaterNB(passReady = false, itlbParams.fenceDelay, itlbRepeater1.io.ptw, io.ptw, sfence, tlbCsr)
+  val itlbRepeater2 =
+    PTWRepeaterNB(passReady = false, itlbParams.fenceDelay, itlbRepeater1.io.ptw, io.ptw, sfence, tlbCsr)
 
   icache.io.ftqPrefetch <> ftq.io.toPrefetch
   icache.io.softPrefetch <> io.softPrefetch
 
-  //IFU-Ftq
+  // IFU-Ftq
   ifu.io.ftqInter.fromFtq <> ftq.io.toIfu
-  ftq.io.toIfu.req.ready :=  ifu.io.ftqInter.fromFtq.req.ready && icache.io.fetch.req.ready
+  ftq.io.toIfu.req.ready := ifu.io.ftqInter.fromFtq.req.ready && icache.io.fetch.req.ready
 
-  ftq.io.fromIfu          <> ifu.io.ftqInter.toFtq
-  bpu.io.ftq_to_bpu       <> ftq.io.toBpu
-  ftq.io.fromBpu          <> bpu.io.bpu_to_ftq
+  ftq.io.fromIfu <> ifu.io.ftqInter.toFtq
+  bpu.io.ftq_to_bpu <> ftq.io.toBpu
+  ftq.io.fromBpu <> bpu.io.bpu_to_ftq
 
-  ftq.io.mmioCommitRead   <> ifu.io.mmioCommitRead
-  //IFU-ICache
+  ftq.io.mmioCommitRead <> ifu.io.mmioCommitRead
+  // IFU-ICache
 
   icache.io.fetch.req <> ftq.io.toICache.req
-  ftq.io.toICache.req.ready :=  ifu.io.ftqInter.fromFtq.req.ready && icache.io.fetch.req.ready
+  ftq.io.toICache.req.ready := ifu.io.ftqInter.fromFtq.req.ready && icache.io.fetch.req.ready
 
-  ifu.io.icacheInter.resp <>    icache.io.fetch.resp
-  ifu.io.icacheInter.icacheReady :=  icache.io.toIFU
+  ifu.io.icacheInter.resp <> icache.io.fetch.resp
+  ifu.io.icacheInter.icacheReady       := icache.io.toIFU
   ifu.io.icacheInter.topdownIcacheMiss := icache.io.fetch.topdownIcacheMiss
-  ifu.io.icacheInter.topdownItlbMiss := icache.io.fetch.topdownItlbMiss
-  icache.io.stop := ifu.io.icacheStop
-  icache.io.flush := ftq.io.icacheFlush
+  ifu.io.icacheInter.topdownItlbMiss   := icache.io.fetch.topdownItlbMiss
+  icache.io.stop                       := ifu.io.icacheStop
+  icache.io.flush                      := ftq.io.icacheFlush
 
   ifu.io.icachePerfInfo := icache.io.perfInfo
 
@@ -167,8 +171,8 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
 
   icache.io.fencei := RegNext(io.fencei)
 
-  //IFU-Ibuffer
-  ifu.io.toIbuffer    <> ibuffer.io.in
+  // IFU-Ibuffer
+  ifu.io.toIbuffer <> ibuffer.io.in
 
   ftq.io.fromBackend <> io.backend.toFtq
   io.backend.fromFtq := ftq.io.toBackend
@@ -176,169 +180,185 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
   io.frontendInfo.bpuInfo <> ftq.io.bpuInfo
 
   val checkPcMem = Reg(Vec(FtqSize, new Ftq_RF_Components))
-  when (ftq.io.toBackend.pc_mem_wen) {
+  when(ftq.io.toBackend.pc_mem_wen) {
     checkPcMem(ftq.io.toBackend.pc_mem_waddr) := ftq.io.toBackend.pc_mem_wdata
   }
 
   val checkTargetIdx = Wire(Vec(DecodeWidth, UInt(log2Up(FtqSize).W)))
-  val checkTarget = Wire(Vec(DecodeWidth, UInt(VAddrBits.W)))
+  val checkTarget    = Wire(Vec(DecodeWidth, UInt(VAddrBits.W)))
 
   for (i <- 0 until DecodeWidth) {
     checkTargetIdx(i) := ibuffer.io.out(i).bits.ftqPtr.value
-    checkTarget(i) := Mux(ftq.io.toBackend.newest_entry_ptr.value === checkTargetIdx(i),
-                        ftq.io.toBackend.newest_entry_target,
-                        checkPcMem(checkTargetIdx(i) + 1.U).startAddr)
+    checkTarget(i) := Mux(
+      ftq.io.toBackend.newest_entry_ptr.value === checkTargetIdx(i),
+      ftq.io.toBackend.newest_entry_target,
+      checkPcMem(checkTargetIdx(i) + 1.U).startAddr
+    )
   }
 
   // commented out for this br could be the last instruction in the fetch block
   def checkNotTakenConsecutive = {
-    val prevNotTakenValid = RegInit(0.B)
+    val prevNotTakenValid  = RegInit(0.B)
     val prevNotTakenFtqIdx = Reg(UInt(log2Up(FtqSize).W))
     for (i <- 0 until DecodeWidth - 1) {
       // for instrs that is not the last, if a not-taken br, the next instr should have the same ftqPtr
       // for instrs that is the last, record and check next request
-      when (ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr) {
-        when (ibuffer.io.out(i+1).fire) {
+      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr) {
+        when(ibuffer.io.out(i + 1).fire) {
           // not last br, check now
-          XSError(checkTargetIdx(i) =/= checkTargetIdx(i+1), "not-taken br should have same ftqPtr\n")
-        } .otherwise {
+          XSError(checkTargetIdx(i) =/= checkTargetIdx(i + 1), "not-taken br should have same ftqPtr\n")
+        }.otherwise {
           // last br, record its info
-          prevNotTakenValid := true.B
+          prevNotTakenValid  := true.B
           prevNotTakenFtqIdx := checkTargetIdx(i)
         }
       }
     }
-    when (ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr) {
+    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr) {
       // last instr is a br, record its info
-      prevNotTakenValid := true.B
+      prevNotTakenValid  := true.B
       prevNotTakenFtqIdx := checkTargetIdx(DecodeWidth - 1)
     }
-    when (prevNotTakenValid && ibuffer.io.out(0).fire) {
+    when(prevNotTakenValid && ibuffer.io.out(0).fire) {
       XSError(prevNotTakenFtqIdx =/= checkTargetIdx(0), "not-taken br should have same ftqPtr\n")
       prevNotTakenValid := false.B
     }
-    when (needFlush) {
+    when(needFlush) {
       prevNotTakenValid := false.B
     }
   }
 
   def checkTakenNotConsecutive = {
-    val prevTakenValid = RegInit(0.B)
+    val prevTakenValid  = RegInit(0.B)
     val prevTakenFtqIdx = Reg(UInt(log2Up(FtqSize).W))
     for (i <- 0 until DecodeWidth - 1) {
       // for instrs that is not the last, if a taken br, the next instr should not have the same ftqPtr
       // for instrs that is the last, record and check next request
-      when (ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.pred_taken) {
-        when (ibuffer.io.out(i+1).fire) {
+      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && ibuffer.io.out(i).bits.pred_taken) {
+        when(ibuffer.io.out(i + 1).fire) {
           // not last br, check now
-          XSError(checkTargetIdx(i) + 1.U =/= checkTargetIdx(i+1), "taken br should have consecutive ftqPtr\n")
-        } .otherwise {
+          XSError(checkTargetIdx(i) + 1.U =/= checkTargetIdx(i + 1), "taken br should have consecutive ftqPtr\n")
+        }.otherwise {
           // last br, record its info
-          prevTakenValid := true.B
+          prevTakenValid  := true.B
           prevTakenFtqIdx := checkTargetIdx(i)
         }
       }
     }
-    when (ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr && ibuffer.io.out(DecodeWidth - 1).bits.pred_taken) {
+    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr && ibuffer.io.out(
+      DecodeWidth - 1
+    ).bits.pred_taken) {
       // last instr is a br, record its info
-      prevTakenValid := true.B
+      prevTakenValid  := true.B
       prevTakenFtqIdx := checkTargetIdx(DecodeWidth - 1)
     }
-    when (prevTakenValid && ibuffer.io.out(0).fire) {
+    when(prevTakenValid && ibuffer.io.out(0).fire) {
       XSError(prevTakenFtqIdx + 1.U =/= checkTargetIdx(0), "taken br should have consecutive ftqPtr\n")
       prevTakenValid := false.B
     }
-    when (needFlush) {
+    when(needFlush) {
       prevTakenValid := false.B
     }
   }
 
   def checkNotTakenPC = {
-    val prevNotTakenPC = Reg(UInt(VAddrBits.W))
-    val prevIsRVC = Reg(Bool())
+    val prevNotTakenPC    = Reg(UInt(VAddrBits.W))
+    val prevIsRVC         = Reg(Bool())
     val prevNotTakenValid = RegInit(0.B)
 
     for (i <- 0 until DecodeWidth - 1) {
-      when (ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && !ibuffer.io.out(i).bits.pred_taken) {
-        when (ibuffer.io.out(i+1).fire) {
-          XSError(ibuffer.io.out(i).bits.pc + Mux(ibuffer.io.out(i).bits.pd.isRVC, 2.U, 4.U) =/= ibuffer.io.out(i+1).bits.pc, "not-taken br should have consecutive pc\n")
-        } .otherwise {
+      when(ibuffer.io.out(i).fire && ibuffer.io.out(i).bits.pd.isBr && !ibuffer.io.out(i).bits.pred_taken) {
+        when(ibuffer.io.out(i + 1).fire) {
+          XSError(
+            ibuffer.io.out(i).bits.pc + Mux(ibuffer.io.out(i).bits.pd.isRVC, 2.U, 4.U) =/= ibuffer.io.out(
+              i + 1
+            ).bits.pc,
+            "not-taken br should have consecutive pc\n"
+          )
+        }.otherwise {
           prevNotTakenValid := true.B
-          prevIsRVC := ibuffer.io.out(i).bits.pd.isRVC
-          prevNotTakenPC := ibuffer.io.out(i).bits.pc
+          prevIsRVC         := ibuffer.io.out(i).bits.pd.isRVC
+          prevNotTakenPC    := ibuffer.io.out(i).bits.pc
         }
       }
     }
-    when (ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr && !ibuffer.io.out(DecodeWidth - 1).bits.pred_taken) {
+    when(ibuffer.io.out(DecodeWidth - 1).fire && ibuffer.io.out(DecodeWidth - 1).bits.pd.isBr && !ibuffer.io.out(
+      DecodeWidth - 1
+    ).bits.pred_taken) {
       prevNotTakenValid := true.B
-      prevIsRVC := ibuffer.io.out(DecodeWidth - 1).bits.pd.isRVC
-      prevNotTakenPC := ibuffer.io.out(DecodeWidth - 1).bits.pc
+      prevIsRVC         := ibuffer.io.out(DecodeWidth - 1).bits.pd.isRVC
+      prevNotTakenPC    := ibuffer.io.out(DecodeWidth - 1).bits.pc
     }
-    when (prevNotTakenValid && ibuffer.io.out(0).fire) {
-      XSError(prevNotTakenPC + Mux(prevIsRVC, 2.U, 4.U) =/= ibuffer.io.out(0).bits.pc, "not-taken br should have same pc\n")
+    when(prevNotTakenValid && ibuffer.io.out(0).fire) {
+      XSError(
+        prevNotTakenPC + Mux(prevIsRVC, 2.U, 4.U) =/= ibuffer.io.out(0).bits.pc,
+        "not-taken br should have same pc\n"
+      )
       prevNotTakenValid := false.B
     }
-    when (needFlush) {
+    when(needFlush) {
       prevNotTakenValid := false.B
     }
   }
 
   def checkTakenPC = {
     val prevTakenFtqIdx = Reg(UInt(log2Up(FtqSize).W))
-    val prevTakenValid = RegInit(0.B)
+    val prevTakenValid  = RegInit(0.B)
     val prevTakenTarget = Wire(UInt(VAddrBits.W))
     prevTakenTarget := checkPcMem(prevTakenFtqIdx + 1.U).startAddr
 
     for (i <- 0 until DecodeWidth - 1) {
-      when (ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.pred_taken) {
-        when (ibuffer.io.out(i+1).fire) {
-          XSError(checkTarget(i) =/= ibuffer.io.out(i+1).bits.pc, "taken instr should follow target pc\n")
-        } .otherwise {
-          prevTakenValid := true.B
+      when(ibuffer.io.out(i).fire && !ibuffer.io.out(i).bits.pd.notCFI && ibuffer.io.out(i).bits.pred_taken) {
+        when(ibuffer.io.out(i + 1).fire) {
+          XSError(checkTarget(i) =/= ibuffer.io.out(i + 1).bits.pc, "taken instr should follow target pc\n")
+        }.otherwise {
+          prevTakenValid  := true.B
           prevTakenFtqIdx := checkTargetIdx(i)
         }
       }
     }
-    when (ibuffer.io.out(DecodeWidth - 1).fire && !ibuffer.io.out(DecodeWidth - 1).bits.pd.notCFI && ibuffer.io.out(DecodeWidth - 1).bits.pred_taken) {
-      prevTakenValid := true.B
+    when(ibuffer.io.out(DecodeWidth - 1).fire && !ibuffer.io.out(DecodeWidth - 1).bits.pd.notCFI && ibuffer.io.out(
+      DecodeWidth - 1
+    ).bits.pred_taken) {
+      prevTakenValid  := true.B
       prevTakenFtqIdx := checkTargetIdx(DecodeWidth - 1)
     }
-    when (prevTakenValid && ibuffer.io.out(0).fire) {
+    when(prevTakenValid && ibuffer.io.out(0).fire) {
       XSError(prevTakenTarget =/= ibuffer.io.out(0).bits.pc, "taken instr should follow target pc\n")
       prevTakenValid := false.B
     }
-    when (needFlush) {
+    when(needFlush) {
       prevTakenValid := false.B
     }
   }
 
-  //checkNotTakenConsecutive
+  // checkNotTakenConsecutive
   checkTakenNotConsecutive
   checkTakenPC
   checkNotTakenPC
 
   ifu.io.rob_commits <> io.backend.toFtq.rob_commits
 
-  ibuffer.io.flush := needFlush
-  ibuffer.io.ControlRedirect := FlushControlRedirect
-  ibuffer.io.MemVioRedirect := FlushMemVioRedirect
+  ibuffer.io.flush                := needFlush
+  ibuffer.io.ControlRedirect      := FlushControlRedirect
+  ibuffer.io.MemVioRedirect       := FlushMemVioRedirect
   ibuffer.io.ControlBTBMissBubble := FlushControlBTBMiss
-  ibuffer.io.TAGEMissBubble := FlushTAGEMiss
-  ibuffer.io.SCMissBubble := FlushSCMiss
-  ibuffer.io.ITTAGEMissBubble := FlushITTAGEMiss
-  ibuffer.io.RASMissBubble := FlushRASMiss
-  ibuffer.io.decodeCanAccept := io.backend.canAccept
+  ibuffer.io.TAGEMissBubble       := FlushTAGEMiss
+  ibuffer.io.SCMissBubble         := FlushSCMiss
+  ibuffer.io.ITTAGEMissBubble     := FlushITTAGEMiss
+  ibuffer.io.RASMissBubble        := FlushRASMiss
+  ibuffer.io.decodeCanAccept      := io.backend.canAccept
 
   FlushControlBTBMiss := ftq.io.ControlBTBMissBubble
-  FlushTAGEMiss := ftq.io.TAGEMissBubble
-  FlushSCMiss := ftq.io.SCMissBubble
-  FlushITTAGEMiss := ftq.io.ITTAGEMissBubble
-  FlushRASMiss := ftq.io.RASMissBubble
+  FlushTAGEMiss       := ftq.io.TAGEMissBubble
+  FlushSCMiss         := ftq.io.SCMissBubble
+  FlushITTAGEMiss     := ftq.io.ITTAGEMissBubble
+  FlushRASMiss        := ftq.io.RASMissBubble
 
   io.backend.cfVec <> ibuffer.io.out
   io.backend.stallReason <> ibuffer.io.stallReason
 
-  instrUncache.io.req   <> ifu.io.uncacheInter.toUncache
+  instrUncache.io.req <> ifu.io.uncacheInter.toUncache
   ifu.io.uncacheInter.fromUncache <> instrUncache.io.resp
   instrUncache.io.flush := false.B
   io.error <> RegNext(RegNext(icache.io.error))
@@ -350,7 +370,7 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
   val frontendBubble = Mux(io.backend.canAccept, DecodeWidth.U - PopCount(ibuffer.io.out.map(_.valid)), 0.U)
   XSPerfAccumulate("FrontendBubble", frontendBubble)
   io.frontendInfo.ibufFull := RegNext(ibuffer.io.full)
-  io.resetInFrontend := reset.asBool
+  io.resetInFrontend       := reset.asBool
 
   // PFEvent
   val pfevent = Module(new PFEvent)
@@ -369,7 +389,7 @@ class FrontendInlinedImp (outer: FrontendInlined) extends LazyModuleImp(outer)
     }
   }
 
-  val allPerfInc = allPerfEvents.map(_._2.asTypeOf(new PerfEvent))
+  val allPerfInc          = allPerfEvents.map(_._2.asTypeOf(new PerfEvent))
   override val perfEvents = HPerfMonitor(csrevents, allPerfInc).getPerfEvents
   generatePerfEvent()
 }
diff --git a/src/main/scala/xiangshan/frontend/FrontendBundle.scala b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
index a2ab5af6b08..5c4231aeb43 100644
--- a/src/main/scala/xiangshan/frontend/FrontendBundle.scala
+++ b/src/main/scala/xiangshan/frontend/FrontendBundle.scala
@@ -16,40 +16,39 @@
 ***************************************************************************************/
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import xiangshan.frontend.icache._
-import utils._
+import java.util.ResourceBundle.Control
+import org.chipsalliance.cde.config.Parameters
+import scala.math._
 import utility._
-import xiangshan.cache.mmu.TlbResp
+import utils._
+import xiangshan._
 import xiangshan.backend.fu.PMPRespBundle
-
-import scala.math._
-import java.util.ResourceBundle.Control
+import xiangshan.cache.mmu.TlbResp
+import xiangshan.frontend.icache._
 
 class FrontendTopDownBundle(implicit p: Parameters) extends XSBundle {
-  val reasons = Vec(TopDownCounters.NumStallReasons.id, Bool())
+  val reasons    = Vec(TopDownCounters.NumStallReasons.id, Bool())
   val stallWidth = UInt(log2Ceil(PredictWidth).W)
 }
 
 class FetchRequestBundle(implicit p: Parameters) extends XSBundle with HasICacheParameters {
 
-  //fast path: Timing critical
-  val startAddr       = UInt(VAddrBits.W)
-  val nextlineStart   = UInt(VAddrBits.W)
-  val nextStartAddr   = UInt(VAddrBits.W)
-  //slow path
-  val ftqIdx          = new FtqPtr
-  val ftqOffset       = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
+  // fast path: Timing critical
+  val startAddr     = UInt(VAddrBits.W)
+  val nextlineStart = UInt(VAddrBits.W)
+  val nextStartAddr = UInt(VAddrBits.W)
+  // slow path
+  val ftqIdx    = new FtqPtr
+  val ftqOffset = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
 
-  val topdown_info    = new FrontendTopDownBundle
+  val topdown_info = new FrontendTopDownBundle
 
-  def crossCacheline =  startAddr(blockOffBits - 1) === 1.U
+  def crossCacheline = startAddr(blockOffBits - 1) === 1.U
 
   def fromFtqPcBundle(b: Ftq_RF_Components) = {
-    this.startAddr := b.startAddr
+    this.startAddr     := b.startAddr
     this.nextlineStart := b.nextLineAddr
     // when (b.fallThruError) {
     //   val nextBlockHigherTemp = Mux(startAddr(log2Ceil(PredictWidth)+instOffsetBits), b.nextLineAddr, b.startAddr)
@@ -63,87 +62,89 @@ class FetchRequestBundle(implicit p: Parameters) extends XSBundle with HasICache
     // }
     this
   }
-  override def toPrintable: Printable = {
+  override def toPrintable: Printable =
     p"[start] ${Hexadecimal(startAddr)} [next] ${Hexadecimal(nextlineStart)}" +
       p"[tgt] ${Hexadecimal(nextStartAddr)} [ftqIdx] $ftqIdx [jmp] v:${ftqOffset.valid}" +
       p" offset: ${ftqOffset.bits}\n"
-  }
 }
 
-class FtqICacheInfo(implicit p: Parameters)extends XSBundle with HasICacheParameters{
-  val startAddr           = UInt(VAddrBits.W)
-  val nextlineStart       = UInt(VAddrBits.W)
-  val ftqIdx              = new FtqPtr
-  def crossCacheline =  startAddr(blockOffBits - 1) === 1.U
+class FtqICacheInfo(implicit p: Parameters) extends XSBundle with HasICacheParameters {
+  val startAddr      = UInt(VAddrBits.W)
+  val nextlineStart  = UInt(VAddrBits.W)
+  val ftqIdx         = new FtqPtr
+  def crossCacheline = startAddr(blockOffBits - 1) === 1.U
   def fromFtqPcBundle(b: Ftq_RF_Components) = {
-    this.startAddr := b.startAddr
+    this.startAddr     := b.startAddr
     this.nextlineStart := b.nextLineAddr
     this
   }
 }
 
-class IFUICacheIO(implicit p: Parameters)extends XSBundle with HasICacheParameters{
+class IFUICacheIO(implicit p: Parameters) extends XSBundle with HasICacheParameters {
   val icacheReady       = Output(Bool())
   val resp              = Vec(PortNumber, ValidIO(new ICacheMainPipeResp))
   val topdownIcacheMiss = Output(Bool())
-  val topdownItlbMiss = Output(Bool())
+  val topdownItlbMiss   = Output(Bool())
 }
 
-class FtqToICacheRequestBundle(implicit p: Parameters)extends XSBundle with HasICacheParameters{
-  val pcMemRead           = Vec(5, new FtqICacheInfo)
-  val readValid           = Vec(5, Bool())
-  val backendIpf          = Bool()
-  val backendIgpf         = Bool()
-  val backendIaf          = Bool()
+class FtqToICacheRequestBundle(implicit p: Parameters) extends XSBundle with HasICacheParameters {
+  val pcMemRead   = Vec(5, new FtqICacheInfo)
+  val readValid   = Vec(5, Bool())
+  val backendIpf  = Bool()
+  val backendIgpf = Bool()
+  val backendIaf  = Bool()
 }
 
-
-class PredecodeWritebackBundle(implicit p:Parameters) extends XSBundle {
-  val pc           = Vec(PredictWidth, UInt(VAddrBits.W))
-  val pd           = Vec(PredictWidth, new PreDecodeInfo) // TODO: redefine Predecode
-  val ftqIdx       = new FtqPtr
-  val ftqOffset    = UInt(log2Ceil(PredictWidth).W)
-  val misOffset    = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
-  val cfiOffset    = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
-  val target       = UInt(VAddrBits.W)
-  val jalTarget    = UInt(VAddrBits.W)
-  val instrRange   = Vec(PredictWidth, Bool())
+class PredecodeWritebackBundle(implicit p: Parameters) extends XSBundle {
+  val pc         = Vec(PredictWidth, UInt(VAddrBits.W))
+  val pd         = Vec(PredictWidth, new PreDecodeInfo) // TODO: redefine Predecode
+  val ftqIdx     = new FtqPtr
+  val ftqOffset  = UInt(log2Ceil(PredictWidth).W)
+  val misOffset  = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
+  val cfiOffset  = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
+  val target     = UInt(VAddrBits.W)
+  val jalTarget  = UInt(VAddrBits.W)
+  val instrRange = Vec(PredictWidth, Bool())
 }
 
 class mmioCommitRead(implicit p: Parameters) extends XSBundle {
-  val mmioFtqPtr = Output(new FtqPtr)
+  val mmioFtqPtr     = Output(new FtqPtr)
   val mmioLastCommit = Input(Bool())
 }
 
 object ExceptionType {
-  def none  : UInt = "b00".U
-  def pf    : UInt = "b01".U // instruction page fault
-  def gpf   : UInt = "b10".U // instruction guest page fault
-  def af    : UInt = "b11".U // instruction access fault
-  def width : Int  = 2
+  def none:  UInt = "b00".U
+  def pf:    UInt = "b01".U // instruction page fault
+  def gpf:   UInt = "b10".U // instruction guest page fault
+  def af:    UInt = "b11".U // instruction access fault
+  def width: Int  = 2
 
   def fromOH(has_pf: Bool, has_gpf: Bool, has_af: Bool): UInt = {
     assert(
       PopCount(VecInit(has_pf, has_gpf, has_af)) <= 1.U,
       "ExceptionType.fromOH receives input that is not one-hot: pf=%d, gpf=%d, af=%d",
-      has_pf, has_gpf, has_af
+      has_pf,
+      has_gpf,
+      has_af
     )
     // input is at-most-one-hot encoded, so we don't worry about priority here.
-    MuxCase(none, Seq(
-      has_pf  -> pf,
-      has_gpf -> gpf,
-      has_af  -> af
-    ))
+    MuxCase(
+      none,
+      Seq(
+        has_pf  -> pf,
+        has_gpf -> gpf,
+        has_af  -> af
+      )
+    )
   }
 
   // raise pf/gpf/af according to ftq(backend) request
-  def fromFtq(req: FtqToICacheRequestBundle): UInt = {
+  def fromFtq(req: FtqToICacheRequestBundle): UInt =
     fromOH(
       req.backendIpf,
       req.backendIgpf,
       req.backendIaf
     )
-  }
 
   // raise pf/gpf/af according to itlb response
   def fromTlbResp(resp: TlbResp, useDup: Int = 0): UInt = {
@@ -157,9 +158,8 @@ object ExceptionType {
   }
 
   // raise af if pmp check failed
-  def fromPMPResp(resp: PMPRespBundle): UInt = {
+  def fromPMPResp(resp: PMPRespBundle): UInt =
     Mux(resp.instr, af, none)
-  }
 
   // raise af if meta/data array ecc check failed or l2 cache respond with tilelink corrupt
   /* FIXME: RISC-V Machine ISA v1.13 (draft) introduced a "hardware error" exception, described as:
@@ -175,9 +175,8 @@ object ExceptionType {
    * Maybe it's better to raise hardware error instead of access fault when ECC check failed.
    * But it's draft and XiangShan backend does not implement this exception code yet, so we still raise af here.
    */
-  def fromECC(enable: Bool, corrupt: Bool): UInt = {
+  def fromECC(enable: Bool, corrupt: Bool): UInt =
     Mux(enable && corrupt, af, none)
-  }
 
   /**Generates exception mux tree
    *
@@ -238,27 +237,25 @@ object ExceptionType {
     // merge port-by-port
     val length = exceptionVecs.head.length
     exceptionVecs.tail.foreach(vec => require(vec.length == length))
-    VecInit((0 until length).map{ i =>
-      merge(exceptionVecs.map(_(i)): _*)
-    })
+    VecInit((0 until length).map(i => merge(exceptionVecs.map(_(i)): _*)))
   }
 }
 
 class FetchToIBuffer(implicit p: Parameters) extends XSBundle {
-  val instrs    = Vec(PredictWidth, UInt(32.W))
-  val valid     = UInt(PredictWidth.W)
-  val enqEnable = UInt(PredictWidth.W)
-  val pd        = Vec(PredictWidth, new PreDecodeInfo)
-  val foldpc    = Vec(PredictWidth, UInt(MemPredPCWidth.W))
-  val ftqOffset    = Vec(PredictWidth, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W)))
+  val instrs               = Vec(PredictWidth, UInt(32.W))
+  val valid                = UInt(PredictWidth.W)
+  val enqEnable            = UInt(PredictWidth.W)
+  val pd                   = Vec(PredictWidth, new PreDecodeInfo)
+  val foldpc               = Vec(PredictWidth, UInt(MemPredPCWidth.W))
+  val ftqOffset            = Vec(PredictWidth, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W)))
   val exceptionFromBackend = Vec(PredictWidth, Bool())
-  val exceptionType = Vec(PredictWidth, UInt(ExceptionType.width.W))
-  val crossPageIPFFix = Vec(PredictWidth, Bool())
-  val illegalInstr = Vec(PredictWidth, Bool())
-  val triggered    = Vec(PredictWidth, TriggerAction())
-  val isLastInFtqEntry = Vec(PredictWidth, Bool())
+  val exceptionType        = Vec(PredictWidth, UInt(ExceptionType.width.W))
+  val crossPageIPFFix      = Vec(PredictWidth, Bool())
+  val illegalInstr         = Vec(PredictWidth, Bool())
+  val triggered            = Vec(PredictWidth, TriggerAction())
+  val isLastInFtqEntry     = Vec(PredictWidth, Bool())
 
-  val pc        = Vec(PredictWidth, UInt(VAddrBits.W))
+  val pc           = Vec(PredictWidth, UInt(VAddrBits.W))
   val ftqPtr       = new FtqPtr
   val topdown_info = new FrontendTopDownBundle
 }
@@ -287,78 +284,68 @@ class ShiftingGlobalHistory(implicit p: Parameters) extends GlobalHistory {
     require(real_taken_mask.length == numBr)
     val last_valid_idx = PriorityMux(
       br_valids.reverse :+ true.B,
-      (numBr to 0 by -1).map(_.U(log2Ceil(numBr+1).W))
+      (numBr to 0 by -1).map(_.U(log2Ceil(numBr + 1).W))
     )
     val first_taken_idx = PriorityEncoder(false.B +: real_taken_mask)
-    val smaller = Mux(last_valid_idx < first_taken_idx,
-      last_valid_idx,
-      first_taken_idx
-    )
-    val shift = smaller
-    val taken = real_taken_mask.reduce(_||_)
+    val smaller         = Mux(last_valid_idx < first_taken_idx, last_valid_idx, first_taken_idx)
+    val shift           = smaller
+    val taken           = real_taken_mask.reduce(_ || _)
     update(shift, taken, this.predHist)
   }
 
   // static read
   def read(n: Int): Bool = predHist.asBools(n)
 
-  final def === (that: ShiftingGlobalHistory): Bool = {
+  final def ===(that: ShiftingGlobalHistory): Bool =
     predHist === that.predHist
-  }
 
-  final def =/= (that: ShiftingGlobalHistory): Bool = !(this === that)
+  final def =/=(that: ShiftingGlobalHistory): Bool = !(this === that)
 }
 
 // circular global history pointer
-class CGHPtr(implicit p: Parameters) extends CircularQueuePtr[CGHPtr](
-  p => p(XSCoreParamsKey).HistoryLength
-){
-}
+class CGHPtr(implicit p: Parameters) extends CircularQueuePtr[CGHPtr](p => p(XSCoreParamsKey).HistoryLength) {}
 
 object CGHPtr {
   def apply(f: Bool, v: UInt)(implicit p: Parameters): CGHPtr = {
     val ptr = Wire(new CGHPtr)
-    ptr.flag := f
+    ptr.flag  := f
     ptr.value := v
     ptr
   }
-  def inverse(ptr: CGHPtr)(implicit p: Parameters): CGHPtr = {
+  def inverse(ptr: CGHPtr)(implicit p: Parameters): CGHPtr =
     apply(!ptr.flag, ptr.value)
-  }
 }
 
 class CircularGlobalHistory(implicit p: Parameters) extends GlobalHistory {
   val buffer = Vec(HistoryLength, Bool())
   type HistPtr = UInt
-  def update(br_valids: Vec[Bool], real_taken_mask: Vec[Bool]): CircularGlobalHistory = {
+  def update(br_valids: Vec[Bool], real_taken_mask: Vec[Bool]): CircularGlobalHistory =
     this
-  }
 }
 
 class FoldedHistory(val len: Int, val compLen: Int, val max_update_num: Int)(implicit p: Parameters)
-  extends XSBundle with HasBPUConst {
+    extends XSBundle with HasBPUConst {
   require(compLen >= 1)
   require(len > 0)
   // require(folded_len <= len)
   require(compLen >= max_update_num)
   val folded_hist = UInt(compLen.W)
 
-  def need_oldest_bits = len > compLen
-  def info = (len, compLen)
+  def need_oldest_bits           = len > compLen
+  def info                       = (len, compLen)
   def oldest_bit_to_get_from_ghr = (0 until max_update_num).map(len - _ - 1)
-  def oldest_bit_pos_in_folded = oldest_bit_to_get_from_ghr map (_ % compLen)
-  def oldest_bit_wrap_around = oldest_bit_to_get_from_ghr map (_ / compLen > 0)
-  def oldest_bit_start = oldest_bit_pos_in_folded.head
+  def oldest_bit_pos_in_folded   = oldest_bit_to_get_from_ghr map (_ % compLen)
+  def oldest_bit_wrap_around     = oldest_bit_to_get_from_ghr map (_ / compLen > 0)
+  def oldest_bit_start           = oldest_bit_pos_in_folded.head
 
-  def get_oldest_bits_from_ghr(ghr: Vec[Bool], histPtr: CGHPtr) = {
+  def get_oldest_bits_from_ghr(ghr: Vec[Bool], histPtr: CGHPtr) =
     // TODO: wrap inc for histPtr value
-    oldest_bit_to_get_from_ghr.map(i => ghr((histPtr + (i+1).U).value))
-  }
+    oldest_bit_to_get_from_ghr.map(i => ghr((histPtr + (i + 1).U).value))
 
   def circular_shift_left(src: UInt, shamt: Int) = {
-    val srcLen = src.getWidth
+    val srcLen      = src.getWidth
     val src_doubled = Cat(src, src)
-    val shifted = src_doubled(srcLen*2-1-shamt, srcLen-shamt)
+    val shifted     = src_doubled(srcLen * 2 - 1 - shamt, srcLen - shamt)
     shifted
   }
 
@@ -368,7 +355,6 @@ class FoldedHistory(val len: Int, val compLen: Int, val max_update_num: Int)(imp
     update(oldest_bits, num, taken)
   }
 
-
   // fast path, use pre-read oldest bits
   def update(ob: Vec[Bool], num: Int, taken: Bool): FoldedHistory = {
     // do xors for several bitsets at specified bits
@@ -389,7 +375,7 @@ class FoldedHistory(val len: Int, val compLen: Int, val max_update_num: Int)(imp
         if (resArr(i).length == 0) {
           println(f"[error] bits $i is not assigned in folded hist update logic! histlen:${this.len}, compLen:$compLen")
         }
-        res(i) := resArr(i).foldLeft(false.B)(_^_)
+        res(i) := resArr(i).foldLeft(false.B)(_ ^ _)
       }
       res.asUInt
     }
@@ -398,23 +384,25 @@ class FoldedHistory(val len: Int, val compLen: Int, val max_update_num: Int)(imp
       val oldest_bits = ob
       require(oldest_bits.length == max_update_num)
       // mask off bits that do not update
-      val oldest_bits_masked = oldest_bits.zipWithIndex.map{
+      val oldest_bits_masked = oldest_bits.zipWithIndex.map {
         case (ob, i) => ob && (i < num).B
       }
       // if a bit does not wrap around, it should not be xored when it exits
-      val oldest_bits_set = (0 until max_update_num).filter(oldest_bit_wrap_around).map(i => (oldest_bit_pos_in_folded(i), oldest_bits_masked(i)))
+      val oldest_bits_set = (0 until max_update_num).filter(oldest_bit_wrap_around).map(i =>
+        (oldest_bit_pos_in_folded(i), oldest_bits_masked(i))
+      )
 
       // println(f"old bits pos ${oldest_bits_set.map(_._1)}")
 
       // only the last bit could be 1, as we have at most one taken branch at a time
-      val newest_bits_masked = VecInit((0 until max_update_num).map(i => taken && ((i+1) == num).B)).asUInt
+      val newest_bits_masked = VecInit((0 until max_update_num).map(i => taken && ((i + 1) == num).B)).asUInt
       // if a bit does not wrap around, newest bits should not be xored onto it either
-      val newest_bits_set = (0 until max_update_num).map(i => (compLen-1-i, newest_bits_masked(i)))
+      val newest_bits_set = (0 until max_update_num).map(i => (compLen - 1 - i, newest_bits_masked(i)))
 
       // println(f"new bits set ${newest_bits_set.map(_._1)}")
       //
-      val original_bits_masked = VecInit(folded_hist.asBools.zipWithIndex.map{
-        case (fb, i) => fb && !(num >= (len-i)).B
+      val original_bits_masked = VecInit(folded_hist.asBools.zipWithIndex.map {
+        case (fb, i) => fb && !(num >= (len - i)).B
       })
       val original_bits_set = (0 until compLen).map(i => (i, original_bits_masked(i)))
 
@@ -423,7 +411,7 @@ class FoldedHistory(val len: Int, val compLen: Int, val max_update_num: Int)(imp
       circular_shift_left(xored, num)
     } else {
       // histLen too short to wrap around
-      ((folded_hist << num) | taken)(compLen-1,0)
+      ((folded_hist << num) | taken)(compLen - 1, 0)
     }
 
     val fh = WireInit(this)
@@ -433,19 +421,20 @@ class FoldedHistory(val len: Int, val compLen: Int, val max_update_num: Int)(imp
 }
 
 class AheadFoldedHistoryOldestBits(val len: Int, val max_update_num: Int)(implicit p: Parameters) extends XSBundle {
-  val bits = Vec(max_update_num*2, Bool())
+  val bits = Vec(max_update_num * 2, Bool())
   // def info = (len, compLen)
   def getRealOb(brNumOH: UInt): Vec[Bool] = {
     val ob = Wire(Vec(max_update_num, Bool()))
     for (i <- 0 until max_update_num) {
-      ob(i) := Mux1H(brNumOH, bits.drop(i).take(numBr+1))
+      ob(i) := Mux1H(brNumOH, bits.drop(i).take(numBr + 1))
     }
     ob
   }
 }
 
-class AllAheadFoldedHistoryOldestBits(val gen: Seq[Tuple2[Int, Int]])(implicit p: Parameters) extends XSBundle with HasBPUConst {
-  val afhob = MixedVec(gen.filter(t => t._1 > t._2).map{_._1}
+class AllAheadFoldedHistoryOldestBits(val gen: Seq[Tuple2[Int, Int]])(implicit p: Parameters) extends XSBundle
+    with HasBPUConst {
+  val afhob = MixedVec(gen.filter(t => t._1 > t._2).map(_._1)
     .toSet.toList.map(l => new AheadFoldedHistoryOldestBits(l, numBr))) // remove duplicates
   require(gen.toSet.toList.equals(gen))
   def getObWithInfo(info: Tuple2[Int, Int]) = {
@@ -454,12 +443,12 @@ class AllAheadFoldedHistoryOldestBits(val gen: Seq[Tuple2[Int, Int]])(implicit p
     selected(0)
   }
   def read(ghv: Vec[Bool], ptr: CGHPtr) = {
-    val hisLens = afhob.map(_.len)
-    val bitsToRead = hisLens.flatMap(l => (0 until numBr*2).map(i => l-i-1)).toSet // remove duplicates
-    val bitsWithInfo = bitsToRead.map(pos => (pos, ghv((ptr+(pos+1).U).value)))
+    val hisLens      = afhob.map(_.len)
+    val bitsToRead   = hisLens.flatMap(l => (0 until numBr * 2).map(i => l - i - 1)).toSet // remove duplicates
+    val bitsWithInfo = bitsToRead.map(pos => (pos, ghv((ptr + (pos + 1).U).value)))
     for (ob <- afhob) {
-      for (i <- 0 until numBr*2) {
-        val pos = ob.len - i - 1
+      for (i <- 0 until numBr * 2) {
+        val pos       = ob.len - i - 1
         val bit_found = bitsWithInfo.filter(_._1 == pos).toList
         require(bit_found.length == 1)
         ob.bits(i) := bit_found(0)._2
@@ -469,7 +458,7 @@ class AllAheadFoldedHistoryOldestBits(val gen: Seq[Tuple2[Int, Int]])(implicit p
 }
 
 class AllFoldedHistories(val gen: Seq[Tuple2[Int, Int]])(implicit p: Parameters) extends XSBundle with HasBPUConst {
-  val hist = MixedVec(gen.map{case (l, cl) => new FoldedHistory(l, cl, numBr)})
+  val hist = MixedVec(gen.map { case (l, cl) => new FoldedHistory(l, cl, numBr) })
   // println(gen.mkString)
   require(gen.toSet.toList.equals(gen))
   def getHistWithInfo(info: Tuple2[Int, Int]) = {
@@ -495,47 +484,46 @@ class AllFoldedHistories(val gen: Seq[Tuple2[Int, Int]])(implicit p: Parameters)
     for (i <- 0 until this.hist.length) {
       val fh = this.hist(i)
       if (fh.need_oldest_bits) {
-        val info = fh.info
+        val info          = fh.info
         val selectedAfhob = afhob.getObWithInfo(info)
-        val ob = selectedAfhob.getRealOb(lastBrNumOH)
+        val ob            = selectedAfhob.getRealOb(lastBrNumOH)
         res.hist(i) := this.hist(i).update(ob, shift, taken)
       } else {
         val dumb = Wire(Vec(numBr, Bool())) // not needed
-        dumb := DontCare
+        dumb        := DontCare
         res.hist(i) := this.hist(i).update(dumb, shift, taken)
       }
     }
     res
   }
 
-  def display(cond: Bool) = {
+  def display(cond: Bool) =
     for (h <- hist) {
       XSDebug(cond, p"hist len ${h.len}, folded len ${h.compLen}, value ${Binary(h.folded_hist)}\n")
     }
-  }
 }
 
-class TableAddr(val idxBits: Int, val banks: Int)(implicit p: Parameters) extends XSBundle{
+class TableAddr(val idxBits: Int, val banks: Int)(implicit p: Parameters) extends XSBundle {
   def tagBits = VAddrBits - idxBits - instOffsetBits
 
-  val tag = UInt(tagBits.W)
-  val idx = UInt(idxBits.W)
+  val tag    = UInt(tagBits.W)
+  val idx    = UInt(idxBits.W)
   val offset = UInt(instOffsetBits.W)
 
-  def fromUInt(x: UInt) = x.asTypeOf(UInt(VAddrBits.W)).asTypeOf(this)
-  def getTag(x: UInt) = fromUInt(x).tag
-  def getIdx(x: UInt) = fromUInt(x).idx
-  def getBank(x: UInt) = if (banks > 1) getIdx(x)(log2Up(banks) - 1, 0) else 0.U
+  def fromUInt(x:   UInt) = x.asTypeOf(UInt(VAddrBits.W)).asTypeOf(this)
+  def getTag(x:     UInt) = fromUInt(x).tag
+  def getIdx(x:     UInt) = fromUInt(x).idx
+  def getBank(x:    UInt) = if (banks > 1) getIdx(x)(log2Up(banks) - 1, 0) else 0.U
   def getBankIdx(x: UInt) = if (banks > 1) getIdx(x)(idxBits - 1, log2Up(banks)) else getIdx(x)
 }
 
 trait BasicPrediction extends HasXSParameter {
   def cfiIndex: ValidUndirectioned[UInt]
   def target(pc: UInt): UInt
-  def lastBrPosOH: Vec[Bool]
-  def brTaken: Bool
+  def lastBrPosOH:    Vec[Bool]
+  def brTaken:        Bool
   def shouldShiftVec: Vec[Bool]
-  def fallThruError: Bool
+  def fallThruError:  Bool
 }
 
 // selectByTaken selects some data according to takenMask
@@ -543,105 +531,104 @@ trait BasicPrediction extends HasXSParameter {
 object selectByTaken {
   def apply[T <: Data](takenMask: Vec[Bool], hit: Bool, allTargets: Vec[T]): T = {
     val selVecOH =
-      takenMask.zipWithIndex.map { case (t, i) => !takenMask.take(i).fold(false.B)(_ || _) && t && hit } :+
+      takenMask.zipWithIndex.map { case (t, i) =>
+        !takenMask.take(i).fold(false.B)(_ || _) && t && hit
+      } :+
         (!takenMask.asUInt.orR && hit) :+ !hit
     Mux1H(selVecOH, allTargets)
   }
 }
 
-class FullBranchPrediction(val isNotS3: Boolean)(implicit p: Parameters) extends XSBundle with HasBPUConst with BasicPrediction {
+class FullBranchPrediction(val isNotS3: Boolean)(implicit p: Parameters) extends XSBundle with HasBPUConst
+    with BasicPrediction {
   val br_taken_mask = Vec(numBr, Bool())
 
   val slot_valids = Vec(totalSlot, Bool())
 
-  val targets = Vec(totalSlot, UInt(VAddrBits.W))
-  val jalr_target = UInt(VAddrBits.W) // special path for indirect predictors
-  val offsets = Vec(totalSlot, UInt(log2Ceil(PredictWidth).W))
+  val targets         = Vec(totalSlot, UInt(VAddrBits.W))
+  val jalr_target     = UInt(VAddrBits.W) // special path for indirect predictors
+  val offsets         = Vec(totalSlot, UInt(log2Ceil(PredictWidth).W))
   val fallThroughAddr = UInt(VAddrBits.W)
-  val fallThroughErr = Bool()
-  val multiHit = Bool()
+  val fallThroughErr  = Bool()
+  val multiHit        = Bool()
 
-  val is_jal = Bool()
-  val is_jalr = Bool()
-  val is_call = Bool()
-  val is_ret = Bool()
+  val is_jal               = Bool()
+  val is_jalr              = Bool()
+  val is_call              = Bool()
+  val is_ret               = Bool()
   val last_may_be_rvi_call = Bool()
-  val is_br_sharing = Bool()
+  val is_br_sharing        = Bool()
 
   // val call_is_rvc = Bool()
   val hit = Bool()
 
   val predCycle = if (!env.FPGAPlatform) Some(UInt(64.W)) else None
 
-  def br_slot_valids = slot_valids.init
+  def br_slot_valids  = slot_valids.init
   def tail_slot_valid = slot_valids.last
 
-  def br_valids = {
+  def br_valids =
     VecInit(br_slot_valids :+ (tail_slot_valid && is_br_sharing))
-  }
 
-  def taken_mask_on_slot = {
+  def taken_mask_on_slot =
     VecInit(
-      (br_slot_valids zip br_taken_mask.init).map{ case (t, v) => t && v } :+ (
+      (br_slot_valids zip br_taken_mask.init).map { case (t, v) => t && v } :+ (
         tail_slot_valid && (
           is_br_sharing && br_taken_mask.last || !is_br_sharing
         )
       )
     )
-  }
 
-  def real_slot_taken_mask(): Vec[Bool] = {
+  def real_slot_taken_mask(): Vec[Bool] =
     VecInit(taken_mask_on_slot.map(_ && hit))
-  }
 
   // len numBr
-  def real_br_taken_mask(): Vec[Bool] = {
+  def real_br_taken_mask(): Vec[Bool] =
     VecInit(
       taken_mask_on_slot.map(_ && hit).init :+
-      (br_taken_mask.last && tail_slot_valid && is_br_sharing && hit)
+        (br_taken_mask.last && tail_slot_valid && is_br_sharing && hit)
     )
-  }
 
   // the vec indicating if ghr should shift on each branch
   def shouldShiftVec =
-    VecInit(br_valids.zipWithIndex.map{ case (v, i) =>
-      v && hit && !real_br_taken_mask().take(i).reduceOption(_||_).getOrElse(false.B)})
+    VecInit(br_valids.zipWithIndex.map { case (v, i) =>
+      v && hit && !real_br_taken_mask().take(i).reduceOption(_ || _).getOrElse(false.B)
+    })
 
   def lastBrPosOH =
-    VecInit((!hit || !br_valids.reduce(_||_)) +: // not hit or no brs in entry
+    VecInit((!hit || !br_valids.reduce(_ || _)) +: // not hit or no brs in entry
       (0 until numBr).map(i =>
         br_valids(i) &&
-        !real_br_taken_mask().take(i).reduceOption(_||_).getOrElse(false.B) && // no brs taken in front it
-        (real_br_taken_mask()(i) || !br_valids.drop(i+1).reduceOption(_||_).getOrElse(false.B)) && // no brs behind it
-        hit
-      )
-    )
+          !real_br_taken_mask().take(i).reduceOption(_ || _).getOrElse(false.B) && // no brs taken in front it
+          (real_br_taken_mask()(i) || !br_valids.drop(i + 1).reduceOption(_ || _).getOrElse(
+            false.B
+          )) && // no brs behind it
+          hit
+      ))
 
-  def brTaken = (br_valids zip br_taken_mask).map{ case (a, b) => a && b && hit}.reduce(_||_)
+  def brTaken = (br_valids zip br_taken_mask).map { case (a, b) => a && b && hit }.reduce(_ || _)
 
-  def target(pc: UInt): UInt = {
-    if (isNotS3){
+  def target(pc: UInt): UInt =
+    if (isNotS3) {
       selectByTaken(taken_mask_on_slot, hit, allTarget(pc))
-    }else {
+    } else {
       selectByTaken(taken_mask_on_slot, hit && !fallThroughErr, allTarget(pc))
     }
-  }
 
   // allTarget return a Vec of all possible target of a BP stage
   // in the following order: [taken_target0, taken_target1, ..., fallThroughAddr, not hit (plus fetch width)]
   //
   // This exposes internal targets for timing optimization,
   // since usually targets are generated quicker than taken
-  def allTarget(pc: UInt): Vec[UInt] = {
+  def allTarget(pc: UInt): Vec[UInt] =
     VecInit(targets :+ fallThroughAddr :+ (pc + (FetchWidth * 4).U))
-  }
 
   def fallThruError: Bool = hit && fallThroughErr
-  def ftbMultiHit: Bool = hit && multiHit
+  def ftbMultiHit:   Bool = hit && multiHit
 
   def hit_taken_on_jmp =
-    !real_slot_taken_mask().init.reduce(_||_) &&
-    real_slot_taken_mask().last && !is_br_sharing
+    !real_slot_taken_mask().init.reduce(_ || _) &&
+      real_slot_taken_mask().last && !is_br_sharing
   def hit_taken_on_call = hit_taken_on_jmp && is_call
   def hit_taken_on_ret  = hit_taken_on_jmp && is_ret
   def hit_taken_on_jalr = hit_taken_on_jmp && is_jalr
@@ -652,75 +639,73 @@ class FullBranchPrediction(val isNotS3: Boolean)(implicit p: Parameters) extends
     // when no takens, set cfiIndex to PredictWidth-1
     cfiIndex.bits :=
       ParallelPriorityMux(real_slot_taken_mask(), offsets) |
-      Fill(log2Ceil(PredictWidth), (!real_slot_taken_mask().asUInt.orR).asUInt)
+        Fill(log2Ceil(PredictWidth), (!real_slot_taken_mask().asUInt.orR).asUInt)
     cfiIndex
   }
 
-  def taken = br_taken_mask.reduce(_||_) || slot_valids.last // || (is_jal || is_jalr)
+  def taken = br_taken_mask.reduce(_ || _) || slot_valids.last // || (is_jal || is_jalr)
 
   def fromFtbEntry(
-                    entry: FTBEntry,
-                    pc: UInt,
-                    last_stage_pc: Option[Tuple2[UInt, Bool]] = None,
-                    last_stage_entry: Option[Tuple2[FTBEntry, Bool]] = None
-                  ) = {
-    slot_valids := entry.brSlots.map(_.valid) :+ entry.tailSlot.valid
-    targets := entry.getTargetVec(pc, last_stage_pc) // Use previous stage pc for better timing
-    jalr_target := targets.last
-    offsets := entry.getOffsetVec
-    is_jal := entry.tailSlot.valid && entry.isJal
-    is_jalr := entry.tailSlot.valid && entry.isJalr
-    is_call := entry.tailSlot.valid && entry.isCall
-    is_ret := entry.tailSlot.valid && entry.isRet
+      entry:            FTBEntry,
+      pc:               UInt,
+      last_stage_pc:    Option[Tuple2[UInt, Bool]] = None,
+      last_stage_entry: Option[Tuple2[FTBEntry, Bool]] = None
+  ) = {
+    slot_valids          := entry.brSlots.map(_.valid) :+ entry.tailSlot.valid
+    targets              := entry.getTargetVec(pc, last_stage_pc) // Use previous stage pc for better timing
+    jalr_target          := targets.last
+    offsets              := entry.getOffsetVec
+    is_jal               := entry.tailSlot.valid && entry.isJal
+    is_jalr              := entry.tailSlot.valid && entry.isJalr
+    is_call              := entry.tailSlot.valid && entry.isCall
+    is_ret               := entry.tailSlot.valid && entry.isRet
     last_may_be_rvi_call := entry.last_may_be_rvi_call
-    is_br_sharing := entry.tailSlot.valid && entry.tailSlot.sharing
+    is_br_sharing        := entry.tailSlot.valid && entry.tailSlot.sharing
     predCycle.map(_ := GTimer())
 
-    val startLower        = Cat(0.U(1.W),    pc(instOffsetBits+log2Ceil(PredictWidth)-1, instOffsetBits))
+    val startLower        = Cat(0.U(1.W), pc(instOffsetBits + log2Ceil(PredictWidth) - 1, instOffsetBits))
     val endLowerwithCarry = Cat(entry.carry, entry.pftAddr)
-    fallThroughErr := startLower >= endLowerwithCarry || endLowerwithCarry > (startLower + (PredictWidth).U)
+    fallThroughErr  := startLower >= endLowerwithCarry || endLowerwithCarry > (startLower + PredictWidth.U)
     fallThroughAddr := Mux(fallThroughErr, pc + (FetchWidth * 4).U, entry.getFallThrough(pc, last_stage_entry))
   }
 
-  def display(cond: Bool): Unit = {
+  def display(cond: Bool): Unit =
     XSDebug(cond, p"[taken_mask] ${Binary(br_taken_mask.asUInt)} [hit] $hit\n")
-  }
 }
 
 class SpeculativeInfo(implicit p: Parameters) extends XSBundle
-  with HasBPUConst with BPUUtils {
+    with HasBPUConst with BPUUtils {
   val histPtr = new CGHPtr
-  val ssp = UInt(log2Up(RasSize).W)
-  val sctr = UInt(RasCtrSize.W)
-  val TOSW = new RASPtr
-  val TOSR = new RASPtr
-  val NOS = new RASPtr
+  val ssp     = UInt(log2Up(RasSize).W)
+  val sctr    = UInt(RasCtrSize.W)
+  val TOSW    = new RASPtr
+  val TOSR    = new RASPtr
+  val NOS     = new RASPtr
   val topAddr = UInt(VAddrBits.W)
 }
 
-// 
+//
 class BranchPredictionBundle(val isNotS3: Boolean)(implicit p: Parameters) extends XSBundle
-  with HasBPUConst with BPUUtils {
-  val pc    = Vec(numDup, UInt(VAddrBits.W))
-  val valid = Vec(numDup, Bool())
-  val hasRedirect  = Vec(numDup, Bool())
-  val ftq_idx = new FtqPtr
-  val full_pred    = Vec(numDup, new FullBranchPrediction(isNotS3))
-
-
-  def target(pc: UInt) = VecInit(full_pred.map(_.target(pc)))
-  def targets(pc: Vec[UInt]) = VecInit(pc.zipWithIndex.map{case (pc, idx) => full_pred(idx).target(pc)})
-  def allTargets(pc: Vec[UInt]) = VecInit(pc.zipWithIndex.map{case (pc, idx) => full_pred(idx).allTarget(pc)})
-  def cfiIndex         = VecInit(full_pred.map(_.cfiIndex))
-  def lastBrPosOH      = VecInit(full_pred.map(_.lastBrPosOH))
-  def brTaken          = VecInit(full_pred.map(_.brTaken))
-  def shouldShiftVec   = VecInit(full_pred.map(_.shouldShiftVec))
-  def fallThruError    = VecInit(full_pred.map(_.fallThruError))
-  def ftbMultiHit      = VecInit(full_pred.map(_.ftbMultiHit))
+    with HasBPUConst with BPUUtils {
+  val pc          = Vec(numDup, UInt(VAddrBits.W))
+  val valid       = Vec(numDup, Bool())
+  val hasRedirect = Vec(numDup, Bool())
+  val ftq_idx     = new FtqPtr
+  val full_pred   = Vec(numDup, new FullBranchPrediction(isNotS3))
+
+  def target(pc:     UInt)      = VecInit(full_pred.map(_.target(pc)))
+  def targets(pc:    Vec[UInt]) = VecInit(pc.zipWithIndex.map { case (pc, idx) => full_pred(idx).target(pc) })
+  def allTargets(pc: Vec[UInt]) = VecInit(pc.zipWithIndex.map { case (pc, idx) => full_pred(idx).allTarget(pc) })
+  def cfiIndex       = VecInit(full_pred.map(_.cfiIndex))
+  def lastBrPosOH    = VecInit(full_pred.map(_.lastBrPosOH))
+  def brTaken        = VecInit(full_pred.map(_.brTaken))
+  def shouldShiftVec = VecInit(full_pred.map(_.shouldShiftVec))
+  def fallThruError  = VecInit(full_pred.map(_.fallThruError))
+  def ftbMultiHit    = VecInit(full_pred.map(_.ftbMultiHit))
 
   def taken = VecInit(cfiIndex.map(_.valid))
 
-  def getTarget = targets(pc)
+  def getTarget     = targets(pc)
   def getAllTargets = allTargets(pc)
 
   def display(cond: Bool): Unit = {
@@ -734,30 +719,30 @@ class BranchPredictionResp(implicit p: Parameters) extends XSBundle with HasBPUC
   val s2 = new BranchPredictionBundle(isNotS3 = true)
   val s3 = new BranchPredictionBundle(isNotS3 = false)
 
-  val s1_uftbHit = Bool()
+  val s1_uftbHit         = Bool()
   val s1_uftbHasIndirect = Bool()
-  val s1_ftbCloseReq = Bool()
+  val s1_ftbCloseReq     = Bool()
 
-  val last_stage_meta = UInt(MaxMetaLength.W)
+  val last_stage_meta      = UInt(MaxMetaLength.W)
   val last_stage_spec_info = new Ftq_Redirect_SRAMEntry
   val last_stage_ftb_entry = new FTBEntry
 
   val topdown_info = new FrontendTopDownBundle
 
-  def selectedResp ={
+  def selectedResp = {
     val res =
       PriorityMux(Seq(
-        ((s3.valid(3) && s3.hasRedirect(3)) -> s3),
-        ((s2.valid(3) && s2.hasRedirect(3)) -> s2),
-        (s1.valid(3) -> s1)
+        (s3.valid(3) && s3.hasRedirect(3)) -> s3,
+        (s2.valid(3) && s2.hasRedirect(3)) -> s2,
+        s1.valid(3)                        -> s1
       ))
     res
   }
   def selectedRespIdxForFtq =
     PriorityMux(Seq(
-      ((s3.valid(3) && s3.hasRedirect(3)) -> BP_S3),
-      ((s2.valid(3) && s2.hasRedirect(3)) -> BP_S2),
-      (s1.valid(3) -> BP_S1)
+      (s3.valid(3) && s3.hasRedirect(3)) -> BP_S3,
+      (s2.valid(3) && s2.hasRedirect(3)) -> BP_S2,
+      s1.valid(3)                        -> BP_S1
     ))
   def lastStage = s3
 }
@@ -765,31 +750,31 @@ class BranchPredictionResp(implicit p: Parameters) extends XSBundle with HasBPUC
 class BpuToFtqBundle(implicit p: Parameters) extends BranchPredictionResp {}
 
 class BranchPredictionUpdate(implicit p: Parameters) extends XSBundle with HasBPUConst {
-  val pc = UInt(VAddrBits.W)
+  val pc        = UInt(VAddrBits.W)
   val spec_info = new SpeculativeInfo
   val ftb_entry = new FTBEntry()
 
-  val cfi_idx = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
-  val br_taken_mask = Vec(numBr, Bool())
-  val br_committed = Vec(numBr, Bool()) // High only when br valid && br committed
-  val jmp_taken = Bool()
-  val mispred_mask = Vec(numBr+1, Bool())
-  val pred_hit = Bool()
-  val false_hit = Bool()
+  val cfi_idx           = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
+  val br_taken_mask     = Vec(numBr, Bool())
+  val br_committed      = Vec(numBr, Bool()) // High only when br valid && br committed
+  val jmp_taken         = Bool()
+  val mispred_mask      = Vec(numBr + 1, Bool())
+  val pred_hit          = Bool()
+  val false_hit         = Bool()
   val new_br_insert_pos = Vec(numBr, Bool())
-  val old_entry = Bool()
-  val meta = UInt(MaxMetaLength.W)
-  val full_target = UInt(VAddrBits.W)
-  val from_stage = UInt(2.W)
-  val ghist = UInt(HistoryLength.W)
+  val old_entry         = Bool()
+  val meta              = UInt(MaxMetaLength.W)
+  val full_target       = UInt(VAddrBits.W)
+  val from_stage        = UInt(2.W)
+  val ghist             = UInt(HistoryLength.W)
 
-  def is_jal = ftb_entry.tailSlot.valid && ftb_entry.isJal
+  def is_jal  = ftb_entry.tailSlot.valid && ftb_entry.isJal
   def is_jalr = ftb_entry.tailSlot.valid && ftb_entry.isJalr
   def is_call = ftb_entry.tailSlot.valid && ftb_entry.isCall
-  def is_ret = ftb_entry.tailSlot.valid && ftb_entry.isRet
+  def is_ret  = ftb_entry.tailSlot.valid && ftb_entry.isRet
 
   def is_call_taken = is_call && jmp_taken && cfi_idx.valid && cfi_idx.bits === ftb_entry.tailSlot.offset
-  def is_ret_taken = is_ret && jmp_taken && cfi_idx.valid && cfi_idx.bits === ftb_entry.tailSlot.offset
+  def is_ret_taken  = is_ret && jmp_taken && cfi_idx.valid && cfi_idx.bits === ftb_entry.tailSlot.offset
 
   def display(cond: Bool) = {
     XSDebug(cond, p"-----------BranchPredictionUpdate-----------\n")
@@ -820,24 +805,23 @@ class BranchPredictionRedirect(implicit p: Parameters) extends Redirect with Has
   // TODO: backend should pass topdown signals here
   // must not change its parent since BPU has used asTypeOf(this type) from its parent class
   require(isInstanceOf[Redirect])
-  val BTBMissBubble = Bool()
+  val BTBMissBubble         = Bool()
   def ControlRedirectBubble = debugIsCtrl
   // if mispred br not in ftb, count as BTB miss
   def ControlBTBMissBubble = ControlRedirectBubble && !cfiUpdate.br_hit && !cfiUpdate.jr_hit
-  def TAGEMissBubble = ControlRedirectBubble && cfiUpdate.br_hit && !cfiUpdate.sc_hit
-  def SCMissBubble = ControlRedirectBubble && cfiUpdate.br_hit && cfiUpdate.sc_hit
-  def ITTAGEMissBubble = ControlRedirectBubble && cfiUpdate.jr_hit && !cfiUpdate.pd.isRet
-  def RASMissBubble = ControlRedirectBubble && cfiUpdate.jr_hit && cfiUpdate.pd.isRet
+  def TAGEMissBubble       = ControlRedirectBubble && cfiUpdate.br_hit && !cfiUpdate.sc_hit
+  def SCMissBubble         = ControlRedirectBubble && cfiUpdate.br_hit && cfiUpdate.sc_hit
+  def ITTAGEMissBubble     = ControlRedirectBubble && cfiUpdate.jr_hit && !cfiUpdate.pd.isRet
+  def RASMissBubble        = ControlRedirectBubble && cfiUpdate.jr_hit && cfiUpdate.pd.isRet
   def MemVioRedirectBubble = debugIsMemVio
-  def OtherRedirectBubble = !debugIsCtrl && !debugIsMemVio
+  def OtherRedirectBubble  = !debugIsCtrl && !debugIsMemVio
 
-  def connectRedirect(source: Redirect): Unit = {
+  def connectRedirect(source: Redirect): Unit =
     for ((name, data) <- this.elements) {
       if (source.elements.contains(name)) {
         data := source.elements(name)
       }
     }
-  }
 
   def display(cond: Bool): Unit = {
     XSDebug(cond, p"-----------BranchPredictionRedirect----------- \n")
@@ -845,7 +829,10 @@ class BranchPredictionRedirect(implicit p: Parameters) extends Redirect with Has
     XSDebug(cond, p"[pc] ${Hexadecimal(cfiUpdate.pc)}\n")
     // XSDebug(cond, p"[hist] ${Binary(cfiUpdate.hist.predHist)}\n")
     XSDebug(cond, p"[br_hit] ${cfiUpdate.br_hit} [isMisPred] ${cfiUpdate.isMisPred}\n")
-    XSDebug(cond, p"[pred_taken] ${cfiUpdate.predTaken} [taken] ${cfiUpdate.taken} [isMisPred] ${cfiUpdate.isMisPred}\n")
+    XSDebug(
+      cond,
+      p"[pred_taken] ${cfiUpdate.predTaken} [taken] ${cfiUpdate.taken} [isMisPred] ${cfiUpdate.isMisPred}\n"
+    )
     XSDebug(cond, p"[target] ${Hexadecimal(cfiUpdate.target)} \n")
     XSDebug(cond, p"[shift] ${cfiUpdate.shift}\n")
     XSDebug(cond, p"------------------------------- \n")
diff --git a/src/main/scala/xiangshan/frontend/IBuffer.scala b/src/main/scala/xiangshan/frontend/IBuffer.scala
index 5f127a235ee..29acb23e107 100644
--- a/src/main/scala/xiangshan/frontend/IBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/IBuffer.scala
@@ -16,125 +16,121 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
+import xiangshan._
 import xiangshan.ExceptionNO._
 
-class IBufPtr(implicit p: Parameters) extends CircularQueuePtr[IBufPtr](
-  p => p(XSCoreParamsKey).IBufSize
-) {
-}
+class IBufPtr(implicit p: Parameters) extends CircularQueuePtr[IBufPtr](p => p(XSCoreParamsKey).IBufSize) {}
 
-class IBufInBankPtr(implicit p: Parameters) extends CircularQueuePtr[IBufInBankPtr](
-  p => p(XSCoreParamsKey).IBufSize / p(XSCoreParamsKey).IBufNBank
-) {
-}
+class IBufInBankPtr(implicit p: Parameters) extends CircularQueuePtr[IBufInBankPtr](p =>
+      p(XSCoreParamsKey).IBufSize / p(XSCoreParamsKey).IBufNBank
+    ) {}
 
-class IBufBankPtr(implicit p: Parameters) extends CircularQueuePtr[IBufBankPtr](
-  p => p(XSCoreParamsKey).IBufNBank
-) {
-}
+class IBufBankPtr(implicit p: Parameters) extends CircularQueuePtr[IBufBankPtr](p => p(XSCoreParamsKey).IBufNBank) {}
 
 class IBufferIO(implicit p: Parameters) extends XSBundle {
-  val flush = Input(Bool())
-  val ControlRedirect = Input(Bool())
+  val flush                = Input(Bool())
+  val ControlRedirect      = Input(Bool())
   val ControlBTBMissBubble = Input(Bool())
-  val TAGEMissBubble = Input(Bool())
-  val SCMissBubble = Input(Bool())
-  val ITTAGEMissBubble = Input(Bool())
-  val RASMissBubble = Input(Bool())
-  val MemVioRedirect = Input(Bool())
-  val in = Flipped(DecoupledIO(new FetchToIBuffer))
-  val out = Vec(DecodeWidth, DecoupledIO(new CtrlFlow))
-  val full = Output(Bool())
-  val decodeCanAccept = Input(Bool())
-  val stallReason = new StallReasonIO(DecodeWidth)
+  val TAGEMissBubble       = Input(Bool())
+  val SCMissBubble         = Input(Bool())
+  val ITTAGEMissBubble     = Input(Bool())
+  val RASMissBubble        = Input(Bool())
+  val MemVioRedirect       = Input(Bool())
+  val in                   = Flipped(DecoupledIO(new FetchToIBuffer))
+  val out                  = Vec(DecodeWidth, DecoupledIO(new CtrlFlow))
+  val full                 = Output(Bool())
+  val decodeCanAccept      = Input(Bool())
+  val stallReason          = new StallReasonIO(DecodeWidth)
 }
 
 class IBufEntry(implicit p: Parameters) extends XSBundle {
-  val inst = UInt(32.W)
-  val pc = UInt(VAddrBits.W)
-  val foldpc = UInt(MemPredPCWidth.W)
-  val pd = new PreDecodeInfo
-  val pred_taken = Bool()
-  val ftqPtr = new FtqPtr
-  val ftqOffset = UInt(log2Ceil(PredictWidth).W)
-  val exceptionType = IBufferExceptionType()
+  val inst                 = UInt(32.W)
+  val pc                   = UInt(VAddrBits.W)
+  val foldpc               = UInt(MemPredPCWidth.W)
+  val pd                   = new PreDecodeInfo
+  val pred_taken           = Bool()
+  val ftqPtr               = new FtqPtr
+  val ftqOffset            = UInt(log2Ceil(PredictWidth).W)
+  val exceptionType        = IBufferExceptionType()
   val exceptionFromBackend = Bool()
-  val triggered = TriggerAction()
-  val isLastInFtqEntry = Bool()
+  val triggered            = TriggerAction()
+  val isLastInFtqEntry     = Bool()
 
   def fromFetch(fetch: FetchToIBuffer, i: Int): IBufEntry = {
-    inst   := fetch.instrs(i)
-    pc     := fetch.pc(i)
-    foldpc := fetch.foldpc(i)
-    pd     := fetch.pd(i)
+    inst       := fetch.instrs(i)
+    pc         := fetch.pc(i)
+    foldpc     := fetch.foldpc(i)
+    pd         := fetch.pd(i)
     pred_taken := fetch.ftqOffset(i).valid
-    ftqPtr := fetch.ftqPtr
-    ftqOffset := fetch.ftqOffset(i).bits
+    ftqPtr     := fetch.ftqPtr
+    ftqOffset  := fetch.ftqOffset(i).bits
     exceptionType := IBufferExceptionType.cvtFromFetchExcpAndCrossPageAndRVCII(
       fetch.exceptionType(i),
       fetch.crossPageIPFFix(i),
-      fetch.illegalInstr(i),
+      fetch.illegalInstr(i)
     )
     exceptionFromBackend := fetch.exceptionFromBackend(i)
-    triggered := fetch.triggered(i)
-    isLastInFtqEntry := fetch.isLastInFtqEntry(i)
+    triggered            := fetch.triggered(i)
+    isLastInFtqEntry     := fetch.isLastInFtqEntry(i)
     this
   }
 
   def toCtrlFlow: CtrlFlow = {
     val cf = Wire(new CtrlFlow)
-    cf.instr := inst
-    cf.pc := pc
-    cf.foldpc := foldpc
-    cf.exceptionVec := 0.U.asTypeOf(ExceptionVec())
-    cf.exceptionVec(instrPageFault)      := IBufferExceptionType.isPF (this.exceptionType)
+    cf.instr                             := inst
+    cf.pc                                := pc
+    cf.foldpc                            := foldpc
+    cf.exceptionVec                      := 0.U.asTypeOf(ExceptionVec())
+    cf.exceptionVec(instrPageFault)      := IBufferExceptionType.isPF(this.exceptionType)
     cf.exceptionVec(instrGuestPageFault) := IBufferExceptionType.isGPF(this.exceptionType)
-    cf.exceptionVec(instrAccessFault)    := IBufferExceptionType.isAF (this.exceptionType)
+    cf.exceptionVec(instrAccessFault)    := IBufferExceptionType.isAF(this.exceptionType)
     cf.exceptionVec(EX_II)               := IBufferExceptionType.isRVCII(this.exceptionType)
-    cf.exceptionFromBackend := exceptionFromBackend
-    cf.trigger := triggered
-    cf.pd := pd
-    cf.pred_taken := pred_taken
-    cf.crossPageIPFFix := IBufferExceptionType.isCrossPage(this.exceptionType)
-    cf.storeSetHit := DontCare
-    cf.waitForRobIdx := DontCare
-    cf.loadWaitBit := DontCare
-    cf.loadWaitStrict := DontCare
-    cf.ssid := DontCare
-    cf.ftqPtr := ftqPtr
-    cf.ftqOffset := ftqOffset
-    cf.isLastInFtqEntry := isLastInFtqEntry
+    cf.exceptionFromBackend              := exceptionFromBackend
+    cf.trigger                           := triggered
+    cf.pd                                := pd
+    cf.pred_taken                        := pred_taken
+    cf.crossPageIPFFix                   := IBufferExceptionType.isCrossPage(this.exceptionType)
+    cf.storeSetHit                       := DontCare
+    cf.waitForRobIdx                     := DontCare
+    cf.loadWaitBit                       := DontCare
+    cf.loadWaitStrict                    := DontCare
+    cf.ssid                              := DontCare
+    cf.ftqPtr                            := ftqPtr
+    cf.ftqOffset                         := ftqOffset
+    cf.isLastInFtqEntry                  := isLastInFtqEntry
     cf
   }
 
   object IBufferExceptionType extends NamedUInt(3) {
-    def None         = "b000".U
-    def NonCrossPF   = "b001".U
-    def NonCrossGPF  = "b010".U
-    def NonCrossAF   = "b011".U
+    def None        = "b000".U
+    def NonCrossPF  = "b001".U
+    def NonCrossGPF = "b010".U
+    def NonCrossAF  = "b011".U
     // illegal instruction
-    def rvcII        = "b100".U
-    def CrossPF      = "b101".U
-    def CrossGPF     = "b110".U
-    def CrossAF      = "b111".U
+    def rvcII    = "b100".U
+    def CrossPF  = "b101".U
+    def CrossGPF = "b110".U
+    def CrossAF  = "b111".U
 
     def cvtFromFetchExcpAndCrossPageAndRVCII(fetchExcp: UInt, crossPage: Bool, rvcIll: Bool): UInt = {
       require(
         fetchExcp.getWidth == ExceptionType.width,
         s"The width(${fetchExcp.getWidth}) of fetchExcp should be equal to " +
-        s"the width(${ExceptionType.width}) of frontend.ExceptionType."
+          s"the width(${ExceptionType.width}) of frontend.ExceptionType."
+      )
+      MuxCase(
+        0.U,
+        Seq(
+          crossPage     -> Cat(1.U(1.W), fetchExcp),
+          fetchExcp.orR -> fetchExcp,
+          rvcIll        -> this.rvcII
+        )
       )
-      MuxCase(0.U, Seq(
-        crossPage     -> Cat(1.U(1.W), fetchExcp),
-        fetchExcp.orR -> fetchExcp,
-        rvcIll        -> this.rvcII,
-      ))
     }
 
     def isRVCII(uint: UInt): Bool = {
@@ -147,9 +143,9 @@ class IBufEntry(implicit p: Parameters) extends XSBundle {
       uint(2) && uint(1, 0) =/= 0.U
     }
 
-    def isPF (uint: UInt): Bool = uint(1, 0) === this.NonCrossPF (1, 0)
+    def isPF(uint:  UInt): Bool = uint(1, 0) === this.NonCrossPF(1, 0)
     def isGPF(uint: UInt): Bool = uint(1, 0) === this.NonCrossGPF(1, 0)
-    def isAF (uint: UInt): Bool = uint(1, 0) === this.NonCrossAF (1, 0)
+    def isAF(uint:  UInt): Bool = uint(1, 0) === this.NonCrossAF(1, 0)
   }
 }
 
@@ -162,8 +158,10 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
   // Parameter Check
   private val bankSize = IBufSize / IBufNBank
   require(IBufSize % IBufNBank == 0, s"IBufNBank should divide IBufSize, IBufNBank: $IBufNBank, IBufSize: $IBufSize")
-  require(IBufNBank >= DecodeWidth,
-    s"IBufNBank should be equal or larger than DecodeWidth, IBufNBank: $IBufNBank, DecodeWidth: $DecodeWidth")
+  require(
+    IBufNBank >= DecodeWidth,
+    s"IBufNBank should be equal or larger than DecodeWidth, IBufNBank: $IBufNBank, DecodeWidth: $DecodeWidth"
+  )
 
   // IBuffer is organized as raw registers
   // This is due to IBuffer is a huge queue, read & write port logic should be precisely controlled
@@ -177,37 +175,35 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
   // Enqueue writes cannot benefit from this characteristic unless use a SRAM
   // For detail see Enqueue and Dequeue below
   private val ibuf: Vec[IBufEntry] = RegInit(VecInit.fill(IBufSize)(0.U.asTypeOf(new IBufEntry)))
-  private val bankedIBufView: Vec[Vec[IBufEntry]] = VecInit.tabulate(IBufNBank)(
-    bankID => VecInit.tabulate(bankSize)(
-      inBankOffset => ibuf(bankID + inBankOffset * IBufNBank)
-    )
+  private val bankedIBufView: Vec[Vec[IBufEntry]] = VecInit.tabulate(IBufNBank)(bankID =>
+    VecInit.tabulate(bankSize)(inBankOffset => ibuf(bankID + inBankOffset * IBufNBank))
   )
 
-
   // Bypass wire
   private val bypassEntries = WireDefault(VecInit.fill(DecodeWidth)(0.U.asTypeOf(Valid(new IBufEntry))))
   // Normal read wire
   private val deqEntries = WireDefault(VecInit.fill(DecodeWidth)(0.U.asTypeOf(Valid(new IBufEntry))))
   // Output register
   private val outputEntries = RegInit(VecInit.fill(DecodeWidth)(0.U.asTypeOf(Valid(new IBufEntry))))
-  private val outputEntriesValidNum = PriorityMuxDefault(outputEntries.map(_.valid).zip(Seq.range(1, DecodeWidth).map(_.U)).reverse.toSeq, 0.U)
+  private val outputEntriesValidNum =
+    PriorityMuxDefault(outputEntries.map(_.valid).zip(Seq.range(1, DecodeWidth).map(_.U)).reverse.toSeq, 0.U)
 
   // Between Bank
   private val deqBankPtrVec: Vec[IBufBankPtr] = RegInit(VecInit.tabulate(DecodeWidth)(_.U.asTypeOf(new IBufBankPtr)))
-  private val deqBankPtr: IBufBankPtr = deqBankPtrVec(0)
+  private val deqBankPtr:    IBufBankPtr      = deqBankPtrVec(0)
   private val deqBankPtrVecNext = Wire(deqBankPtrVec.cloneType)
   // Inside Bank
   private val deqInBankPtr: Vec[IBufInBankPtr] = RegInit(VecInit.fill(IBufNBank)(0.U.asTypeOf(new IBufInBankPtr)))
   private val deqInBankPtrNext = Wire(deqInBankPtr.cloneType)
 
-  val deqPtr = RegInit(0.U.asTypeOf(new IBufPtr))
+  val deqPtr     = RegInit(0.U.asTypeOf(new IBufPtr))
   val deqPtrNext = Wire(deqPtr.cloneType)
 
   val enqPtrVec = RegInit(VecInit.tabulate(PredictWidth)(_.U.asTypeOf(new IBufPtr)))
-  val enqPtr = enqPtrVec(0)
+  val enqPtr    = enqPtrVec(0)
 
   val numTryEnq = WireDefault(0.U)
-  val numEnq = Mux(io.in.fire, numTryEnq, 0.U)
+  val numEnq    = Mux(io.in.fire, numTryEnq, 0.U)
 
   // empty and decode can accept insts
   val useBypass = enqPtr === deqPtr && decodeCanAccept
@@ -218,19 +214,19 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
   private val numDeq = numOut
 
   // counter current number of valid
-  val numValid = distanceBetween(enqPtr, deqPtr)
+  val numValid         = distanceBetween(enqPtr, deqPtr)
   val numValidAfterDeq = numValid - numDeq
   // counter next number of valid
   val numValidNext = numValid + numEnq - numDeq
-  val allowEnq = RegInit(true.B)
+  val allowEnq     = RegInit(true.B)
   val numFromFetch = Mux(io.in.valid, PopCount(io.in.bits.enqEnable), 0.U)
 
   allowEnq := (IBufSize - PredictWidth).U >= numValidNext // Disable when almost full
 
   val enqOffset = VecInit.tabulate(PredictWidth)(i => PopCount(io.in.bits.valid.asBools.take(i)))
-  val enqData = VecInit.tabulate(PredictWidth)(i => Wire(new IBufEntry).fromFetch(io.in.bits, i))
+  val enqData   = VecInit.tabulate(PredictWidth)(i => Wire(new IBufEntry).fromFetch(io.in.bits, i))
 
-  val outputEntriesIsNotFull = !outputEntries(DecodeWidth-1).valid
+  val outputEntriesIsNotFull = !outputEntries(DecodeWidth - 1).valid
   when(decodeCanAccept) {
     numOut := Mux(numValid >= DecodeWidth.U, DecodeWidth.U, numValid)
   }.elsewhen(outputEntriesIsNotFull) {
@@ -244,11 +240,11 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
     when(numFromFetch >= DecodeWidth.U) {
       numTryEnq := numFromFetch - DecodeWidth.U
       numBypass := DecodeWidth.U
-    } .otherwise {
+    }.otherwise {
       numTryEnq := 0.U
       numBypass := numFromFetch
     }
-  } .otherwise {
+  }.otherwise {
     numTryEnq := numFromFetch
     numBypass := 0.U
   }
@@ -262,11 +258,11 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
       val validOH = Range(0, PredictWidth).map {
         i =>
           io.in.bits.valid(i) &&
-            io.in.bits.enqEnable(i) &&
-            enqOffset(i) === idx.asUInt
+          io.in.bits.enqEnable(i) &&
+          enqOffset(i) === idx.asUInt
       } // Should be OneHot
       entry.valid := validOH.reduce(_ || _) && io.in.fire && !io.flush
-      entry.bits := Mux1H(validOH, enqData)
+      entry.bits  := Mux1H(validOH, enqData)
 
       // Debug Assertion
       XSError(io.in.valid && PopCount(validOH) > 1.asUInt, "validOH is not OneHot")
@@ -277,7 +273,7 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
   io.out zip outputEntries foreach {
     case (io, reg) =>
       io.valid := reg.valid
-      io.bits := reg.bits.toCtrlFlow
+      io.bits  := reg.bits.toCtrlFlow
   }
   (outputEntries zip bypassEntries).zipWithIndex.foreach {
     case ((out, bypass), i) =>
@@ -287,9 +283,13 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
         }.otherwise {
           out := deqEntries(i)
         }
-      }.elsewhen(outputEntriesIsNotFull){
+      }.elsewhen(outputEntriesIsNotFull) {
         out.valid := deqEntries(i).valid
-        out.bits := Mux(i.U < outputEntriesValidNum, out.bits, VecInit(deqEntries.take(i + 1).map(_.bits))(i.U - outputEntriesValidNum))
+        out.bits := Mux(
+          i.U < outputEntriesValidNum,
+          out.bits,
+          VecInit(deqEntries.take(i + 1).map(_.bits))(i.U - outputEntriesValidNum)
+        )
       }
   }
 
@@ -322,7 +322,7 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
     }
   }
   // Pointer maintenance
-  when (io.in.fire && !io.flush) {
+  when(io.in.fire && !io.flush) {
     enqPtrVec := VecInit(enqPtrVec.map(_ + numTryEnq))
   }
 
@@ -343,40 +343,40 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
   // Read port
   // 2-stage, IBufNBank * (bankSize -> 1) + IBufNBank -> 1
   // Should be better than IBufSize -> 1 in area, with no significant latency increase
-  private val readStage1: Vec[IBufEntry] = VecInit.tabulate(IBufNBank)(
-    bankID => Mux1H(UIntToOH(deqInBankPtr(bankID).value), bankedIBufView(bankID))
-  )
+  private val readStage1: Vec[IBufEntry] =
+    VecInit.tabulate(IBufNBank)(bankID => Mux1H(UIntToOH(deqInBankPtr(bankID).value), bankedIBufView(bankID)))
   for (i <- 0 until DecodeWidth) {
     deqEntries(i).valid := validVec(i)
-    deqEntries(i).bits := Mux1H(UIntToOH(deqBankPtrVec(i).value), readStage1)
+    deqEntries(i).bits  := Mux1H(UIntToOH(deqBankPtrVec(i).value), readStage1)
   }
   // Pointer maintenance
   deqBankPtrVecNext := VecInit(deqBankPtrVec.map(_ + numDeq))
-  deqPtrNext := deqPtr + numDeq
+  deqPtrNext        := deqPtr + numDeq
   deqInBankPtrNext.zip(deqInBankPtr).zipWithIndex.foreach {
     case ((ptrNext, ptr), idx) => {
       // validVec[k] == bankValid[deqBankPtr + k]
       // So bankValid[n] == validVec[n - deqBankPtr]
-      val validIdx = Mux(idx.asUInt >= deqBankPtr.value,
+      val validIdx = Mux(
+        idx.asUInt >= deqBankPtr.value,
         idx.asUInt - deqBankPtr.value,
         ((idx + IBufNBank).asUInt - deqBankPtr.value)(log2Ceil(IBufNBank) - 1, 0)
       )(log2Ceil(DecodeWidth) - 1, 0)
       val bankAdvance = numOut > validIdx
-      ptrNext := Mux(bankAdvance , ptr + 1.U, ptr)
+      ptrNext := Mux(bankAdvance, ptr + 1.U, ptr)
     }
   }
 
   // Flush
-  when (io.flush) {
-    allowEnq := true.B
-    enqPtrVec := enqPtrVec.indices.map(_.U.asTypeOf(new IBufPtr))
+  when(io.flush) {
+    allowEnq      := true.B
+    enqPtrVec     := enqPtrVec.indices.map(_.U.asTypeOf(new IBufPtr))
     deqBankPtrVec := deqBankPtrVec.indices.map(_.U.asTypeOf(new IBufBankPtr))
-    deqInBankPtr := VecInit.fill(IBufNBank)(0.U.asTypeOf(new IBufInBankPtr))
-    deqPtr := 0.U.asTypeOf(new IBufPtr())
+    deqInBankPtr  := VecInit.fill(IBufNBank)(0.U.asTypeOf(new IBufInBankPtr))
+    deqPtr        := 0.U.asTypeOf(new IBufPtr())
     outputEntries.foreach(_.valid := false.B)
   }.otherwise {
-    deqPtr := deqPtrNext
-    deqInBankPtr := deqInBankPtrNext
+    deqPtr        := deqPtrNext
+    deqInBankPtr  := deqInBankPtrNext
     deqBankPtrVec := deqBankPtrVecNext
   }
   io.full := !allowEnq
@@ -406,8 +406,7 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
     }
   }
 
-
-  val matchBubble = Wire(UInt(log2Up(TopDownCounters.NumStallReasons.id).W))
+  val matchBubble   = Wire(UInt(log2Up(TopDownCounters.NumStallReasons.id).W))
   val deqValidCount = PopCount(validVec.asBools)
   val deqWasteCount = DecodeWidth.U - deqValidCount
   matchBubble := (TopDownCounters.NumStallReasons.id - 1).U - PriorityEncoder(topdown_stage.reasons.reverse)
@@ -445,28 +444,30 @@ class IBuffer(implicit p: Parameters) extends XSModule with HasCircularQueuePtrH
   when(io.in.fire) {
     XSDebug("Enque:\n")
     XSDebug(p"MASK=${Binary(io.in.bits.valid)}\n")
-    for(i <- 0 until PredictWidth){
+    for (i <- 0 until PredictWidth) {
       XSDebug(p"PC=${Hexadecimal(io.in.bits.pc(i))} ${Hexadecimal(io.in.bits.instrs(i))}\n")
     }
   }
 
   for (i <- 0 until DecodeWidth) {
-    XSDebug(io.out(i).fire,
+    XSDebug(
+      io.out(i).fire,
       p"deq: ${Hexadecimal(io.out(i).bits.instr)} PC=${Hexadecimal(io.out(i).bits.pc)}" +
-      p"v=${io.out(i).valid} r=${io.out(i).ready} " +
-      p"excpVec=${Binary(io.out(i).bits.exceptionVec.asUInt)} crossPageIPF=${io.out(i).bits.crossPageIPFFix}\n")
+        p"v=${io.out(i).valid} r=${io.out(i).ready} " +
+        p"excpVec=${Binary(io.out(i).bits.exceptionVec.asUInt)} crossPageIPF=${io.out(i).bits.crossPageIPFFix}\n"
+    )
   }
 
   XSDebug(p"numValid: ${numValid}\n")
   XSDebug(p"EnqNum: ${numEnq}\n")
   XSDebug(p"DeqNum: ${numDeq}\n")
 
-  val afterInit = RegInit(false.B)
+  val afterInit  = RegInit(false.B)
   val headBubble = RegInit(false.B)
-  when (io.in.fire) { afterInit := true.B }
-  when (io.flush) {
+  when(io.in.fire)(afterInit := true.B)
+  when(io.flush) {
     headBubble := true.B
-  } .elsewhen(numValid =/= 0.U) {
+  }.elsewhen(numValid =/= 0.U) {
     headBubble := false.B
   }
   val instrHungry = afterInit && (numValid === 0.U) && !headBubble
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index dea588dfcdb..ea81c57a855 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -17,42 +17,44 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
 import freechips.rocketchip.rocket.RVCDecoder
+import org.chipsalliance.cde.config.Parameters
+import utility._
+import utility.ChiselDB
+import utils._
 import xiangshan._
+import xiangshan.backend.GPAMemEntry
+import xiangshan.backend.fu.PMPReqBundle
+import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.cache.mmu._
 import xiangshan.frontend.icache._
-import utils._
-import utility._
-import xiangshan.backend.fu.{PMPReqBundle, PMPRespBundle}
-import xiangshan.backend.GPAMemEntry
-import utility.ChiselDB
 
-trait HasInstrMMIOConst extends HasXSParameter with HasIFUConst{
+trait HasInstrMMIOConst extends HasXSParameter with HasIFUConst {
   def mmioBusWidth = 64
   def mmioBusBytes = mmioBusWidth / 8
-  def maxInstrLen = 32
+  def maxInstrLen  = 32
 }
 
-trait HasIFUConst extends HasXSParameter{
-  def addrAlign(addr: UInt, bytes: Int, highest: Int): UInt = Cat(addr(highest-1, log2Ceil(bytes)), 0.U(log2Ceil(bytes).W))
+trait HasIFUConst extends HasXSParameter {
+  def addrAlign(addr: UInt, bytes: Int, highest: Int): UInt =
+    Cat(addr(highest - 1, log2Ceil(bytes)), 0.U(log2Ceil(bytes).W))
   def fetchQueueSize = 2
 
-  def getBasicBlockIdx( pc: UInt, start:  UInt ): UInt = {
+  def getBasicBlockIdx(pc: UInt, start: UInt): UInt = {
     val byteOffset = pc - start
-    (byteOffset - instBytes.U)(log2Ceil(PredictWidth),instOffsetBits)
+    (byteOffset - instBytes.U)(log2Ceil(PredictWidth), instOffsetBits)
   }
 }
 
-class IfuToFtqIO(implicit p:Parameters) extends XSBundle {
+class IfuToFtqIO(implicit p: Parameters) extends XSBundle {
   val pdWb = Valid(new PredecodeWritebackBundle)
 }
 
-class IfuToBackendIO(implicit p:Parameters) extends XSBundle {
+class IfuToBackendIO(implicit p: Parameters) extends XSBundle {
   // write to backend gpaddr mem
-  val gpaddrMem_wen = Output(Bool())
+  val gpaddrMem_wen   = Output(Bool())
   val gpaddrMem_waddr = Output(UInt(log2Ceil(FtqSize).W)) // Ftq Ptr
   // 2 gpaddrs, correspond to startAddr & nextLineAddr in bundle FtqICacheInfo
   // TODO: avoid cross page entry in Ftq
@@ -66,154 +68,151 @@ class FtqInterface(implicit p: Parameters) extends XSBundle {
 
 class UncacheInterface(implicit p: Parameters) extends XSBundle {
   val fromUncache = Flipped(DecoupledIO(new InsUncacheResp))
-  val toUncache   = DecoupledIO( new InsUncacheReq )
+  val toUncache   = DecoupledIO(new InsUncacheReq)
 }
 
 class NewIFUIO(implicit p: Parameters) extends XSBundle {
-  val ftqInter         = new FtqInterface
-  val icacheInter      = Flipped(new IFUICacheIO)
-  val icacheStop       = Output(Bool())
-  val icachePerfInfo   = Input(new ICachePerfInfo)
-  val toIbuffer        = Decoupled(new FetchToIBuffer)
-  val toBackend        = new IfuToBackendIO
-  val uncacheInter     = new UncacheInterface
-  val frontendTrigger  = Flipped(new FrontendTdataDistributeIO)
-  val rob_commits      = Flipped(Vec(CommitWidth, Valid(new RobCommitInfo)))
-  val iTLBInter        = new TlbRequestIO
-  val pmp              = new ICachePMPBundle
-  val mmioCommitRead   = new mmioCommitRead
+  val ftqInter        = new FtqInterface
+  val icacheInter     = Flipped(new IFUICacheIO)
+  val icacheStop      = Output(Bool())
+  val icachePerfInfo  = Input(new ICachePerfInfo)
+  val toIbuffer       = Decoupled(new FetchToIBuffer)
+  val toBackend       = new IfuToBackendIO
+  val uncacheInter    = new UncacheInterface
+  val frontendTrigger = Flipped(new FrontendTdataDistributeIO)
+  val rob_commits     = Flipped(Vec(CommitWidth, Valid(new RobCommitInfo)))
+  val iTLBInter       = new TlbRequestIO
+  val pmp             = new ICachePMPBundle
+  val mmioCommitRead  = new mmioCommitRead
 }
 
 // record the situation in which fallThruAddr falls into
 // the middle of an RVI inst
 class LastHalfInfo(implicit p: Parameters) extends XSBundle {
-  val valid = Bool()
+  val valid    = Bool()
   val middlePC = UInt(VAddrBits.W)
   def matchThisBlock(startAddr: UInt) = valid && middlePC === startAddr
 }
 
 class IfuToPreDecode(implicit p: Parameters) extends XSBundle {
-  val data                =  if(HasCExtension) Vec(PredictWidth + 1, UInt(16.W)) else Vec(PredictWidth, UInt(32.W))
-  val frontendTrigger     = new FrontendTdataDistributeIO
-  val pc                  = Vec(PredictWidth, UInt(VAddrBits.W))
+  val data            = if (HasCExtension) Vec(PredictWidth + 1, UInt(16.W)) else Vec(PredictWidth, UInt(32.W))
+  val frontendTrigger = new FrontendTdataDistributeIO
+  val pc              = Vec(PredictWidth, UInt(VAddrBits.W))
 }
 
-
 class IfuToPredChecker(implicit p: Parameters) extends XSBundle {
-  val ftqOffset     = Valid(UInt(log2Ceil(PredictWidth).W))
-  val jumpOffset    = Vec(PredictWidth, UInt(XLEN.W))
-  val target        = UInt(VAddrBits.W)
-  val instrRange    = Vec(PredictWidth, Bool())
-  val instrValid    = Vec(PredictWidth, Bool())
-  val pds           = Vec(PredictWidth, new PreDecodeInfo)
-  val pc            = Vec(PredictWidth, UInt(VAddrBits.W))
-  val fire_in       = Bool()
+  val ftqOffset  = Valid(UInt(log2Ceil(PredictWidth).W))
+  val jumpOffset = Vec(PredictWidth, UInt(XLEN.W))
+  val target     = UInt(VAddrBits.W)
+  val instrRange = Vec(PredictWidth, Bool())
+  val instrValid = Vec(PredictWidth, Bool())
+  val pds        = Vec(PredictWidth, new PreDecodeInfo)
+  val pc         = Vec(PredictWidth, UInt(VAddrBits.W))
+  val fire_in    = Bool()
 }
 
 class FetchToIBufferDB extends Bundle {
-  val start_addr = UInt(39.W)
-  val instr_count = UInt(32.W)
-  val exception = Bool()
+  val start_addr   = UInt(39.W)
+  val instr_count  = UInt(32.W)
+  val exception    = Bool()
   val is_cache_hit = Bool()
 }
 
 class IfuWbToFtqDB extends Bundle {
-  val start_addr = UInt(39.W)
-  val is_miss_pred = Bool()
-  val miss_pred_offset = UInt(32.W)
-  val checkJalFault = Bool()
-  val checkRetFault = Bool()
-  val checkTargetFault = Bool()
-  val checkNotCFIFault = Bool()
+  val start_addr        = UInt(39.W)
+  val is_miss_pred      = Bool()
+  val miss_pred_offset  = UInt(32.W)
+  val checkJalFault     = Bool()
+  val checkRetFault     = Bool()
+  val checkTargetFault  = Bool()
+  val checkNotCFIFault  = Bool()
   val checkInvalidTaken = Bool()
 }
 
 class NewIFU(implicit p: Parameters) extends XSModule
-  with HasICacheParameters
-  with HasXSParameter
-  with HasIFUConst
-  with HasPdConst
-  with HasCircularQueuePtrHelper
-  with HasPerfEvents
-  with HasTlbConst
-{
-  val io = IO(new NewIFUIO)
-  val (toFtq, fromFtq)    = (io.ftqInter.toFtq, io.ftqInter.fromFtq)
-  val fromICache = io.icacheInter.resp
-  val (toUncache, fromUncache) = (io.uncacheInter.toUncache , io.uncacheInter.fromUncache)
+    with HasICacheParameters
+    with HasXSParameter
+    with HasIFUConst
+    with HasPdConst
+    with HasCircularQueuePtrHelper
+    with HasPerfEvents
+    with HasTlbConst {
+  val io                       = IO(new NewIFUIO)
+  val (toFtq, fromFtq)         = (io.ftqInter.toFtq, io.ftqInter.fromFtq)
+  val fromICache               = io.icacheInter.resp
+  val (toUncache, fromUncache) = (io.uncacheInter.toUncache, io.uncacheInter.fromUncache)
 
   def isCrossLineReq(start: UInt, end: UInt): Bool = start(blockOffBits) ^ end(blockOffBits)
 
   def numOfStage = 3
   // equal lower_result overflow bit
-  def PcCutPoint = (VAddrBits/4) - 1
-  def CatPC(low: UInt, high: UInt, high1: UInt): UInt = {
+  def PcCutPoint = (VAddrBits / 4) - 1
+  def CatPC(low: UInt, high: UInt, high1: UInt): UInt =
     Mux(
       low(PcCutPoint),
-      Cat(high1, low(PcCutPoint-1, 0)),
-      Cat(high, low(PcCutPoint-1, 0))
+      Cat(high1, low(PcCutPoint - 1, 0)),
+      Cat(high, low(PcCutPoint - 1, 0))
     )
-  }
   def CatPC(lowVec: Vec[UInt], high: UInt, high1: UInt): Vec[UInt] = VecInit(lowVec.map(CatPC(_, high, high1)))
   require(numOfStage > 1, "BPU numOfStage must be greater than 1")
   val topdown_stages = RegInit(VecInit(Seq.fill(numOfStage)(0.U.asTypeOf(new FrontendTopDownBundle))))
   // bubble events in IFU, only happen in stage 1
   val icacheMissBubble = Wire(Bool())
-  val itlbMissBubble =Wire(Bool())
+  val itlbMissBubble   = Wire(Bool())
 
   // only driven by clock, not valid-ready
   topdown_stages(0) := fromFtq.req.bits.topdown_info
   for (i <- 1 until numOfStage) {
     topdown_stages(i) := topdown_stages(i - 1)
   }
-  when (icacheMissBubble) {
+  when(icacheMissBubble) {
     topdown_stages(1).reasons(TopDownCounters.ICacheMissBubble.id) := true.B
   }
-  when (itlbMissBubble) {
+  when(itlbMissBubble) {
     topdown_stages(1).reasons(TopDownCounters.ITLBMissBubble.id) := true.B
   }
   io.toIbuffer.bits.topdown_info := topdown_stages(numOfStage - 1)
-  when (fromFtq.topdown_redirect.valid) {
+  when(fromFtq.topdown_redirect.valid) {
     // only redirect from backend, IFU redirect itself is handled elsewhere
-    when (fromFtq.topdown_redirect.bits.debugIsCtrl) {
+    when(fromFtq.topdown_redirect.bits.debugIsCtrl) {
       /*
       for (i <- 0 until numOfStage) {
         topdown_stages(i).reasons(TopDownCounters.ControlRedirectBubble.id) := true.B
       }
       io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.ControlRedirectBubble.id) := true.B
-      */
-      when (fromFtq.topdown_redirect.bits.ControlBTBMissBubble) {
+       */
+      when(fromFtq.topdown_redirect.bits.ControlBTBMissBubble) {
         for (i <- 0 until numOfStage) {
           topdown_stages(i).reasons(TopDownCounters.BTBMissBubble.id) := true.B
         }
         io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.BTBMissBubble.id) := true.B
-      } .elsewhen (fromFtq.topdown_redirect.bits.TAGEMissBubble) {
+      }.elsewhen(fromFtq.topdown_redirect.bits.TAGEMissBubble) {
         for (i <- 0 until numOfStage) {
           topdown_stages(i).reasons(TopDownCounters.TAGEMissBubble.id) := true.B
         }
         io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.TAGEMissBubble.id) := true.B
-      } .elsewhen (fromFtq.topdown_redirect.bits.SCMissBubble) {
+      }.elsewhen(fromFtq.topdown_redirect.bits.SCMissBubble) {
         for (i <- 0 until numOfStage) {
           topdown_stages(i).reasons(TopDownCounters.SCMissBubble.id) := true.B
         }
         io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.SCMissBubble.id) := true.B
-      } .elsewhen (fromFtq.topdown_redirect.bits.ITTAGEMissBubble) {
+      }.elsewhen(fromFtq.topdown_redirect.bits.ITTAGEMissBubble) {
         for (i <- 0 until numOfStage) {
           topdown_stages(i).reasons(TopDownCounters.ITTAGEMissBubble.id) := true.B
         }
         io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.ITTAGEMissBubble.id) := true.B
-      } .elsewhen (fromFtq.topdown_redirect.bits.RASMissBubble) {
+      }.elsewhen(fromFtq.topdown_redirect.bits.RASMissBubble) {
         for (i <- 0 until numOfStage) {
           topdown_stages(i).reasons(TopDownCounters.RASMissBubble.id) := true.B
         }
         io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.RASMissBubble.id) := true.B
       }
-    } .elsewhen (fromFtq.topdown_redirect.bits.debugIsMemVio) {
+    }.elsewhen(fromFtq.topdown_redirect.bits.debugIsMemVio) {
       for (i <- 0 until numOfStage) {
         topdown_stages(i).reasons(TopDownCounters.MemVioRedirectBubble.id) := true.B
       }
       io.toIbuffer.bits.topdown_info.reasons(TopDownCounters.MemVioRedirectBubble.id) := true.B
-    } .otherwise {
+    }.otherwise {
       for (i <- 0 until numOfStage) {
         topdown_stages(i).reasons(TopDownCounters.OtherRedirectBubble.id) := true.B
       }
@@ -221,17 +220,18 @@ class NewIFU(implicit p: Parameters) extends XSModule
     }
   }
 
-  class TlbExept(implicit p: Parameters) extends XSBundle{
-    val pageFault = Bool()
+  class TlbExept(implicit p: Parameters) extends XSBundle {
+    val pageFault   = Bool()
     val accessFault = Bool()
-    val mmio = Bool()
+    val mmio        = Bool()
   }
 
-  val preDecoder       = Module(new PreDecode)
+  val preDecoder = Module(new PreDecode)
 
   val predChecker     = Module(new PredChecker)
   val frontendTrigger = Module(new FrontendTrigger)
-  val (checkerIn, checkerOutStage1, checkerOutStage2)         = (predChecker.io.in, predChecker.io.out.stage1Out,predChecker.io.out.stage2Out)
+  val (checkerIn, checkerOutStage1, checkerOutStage2) =
+    (predChecker.io.in, predChecker.io.out.stage1Out, predChecker.io.out.stage2Out)
 
   /**
     ******************************************************************************
@@ -240,34 +240,33 @@ class NewIFU(implicit p: Parameters) extends XSModule
     ******************************************************************************
     */
 
-  val f0_valid                             = fromFtq.req.valid
-  val f0_ftq_req                           = fromFtq.req.bits
-  val f0_doubleLine                        = fromFtq.req.bits.crossCacheline
-  val f0_vSetIdx                           = VecInit(get_idx((f0_ftq_req.startAddr)), get_idx(f0_ftq_req.nextlineStart))
-  val f0_fire                              = fromFtq.req.fire
+  val f0_valid      = fromFtq.req.valid
+  val f0_ftq_req    = fromFtq.req.bits
+  val f0_doubleLine = fromFtq.req.bits.crossCacheline
+  val f0_vSetIdx    = VecInit(get_idx(f0_ftq_req.startAddr), get_idx(f0_ftq_req.nextlineStart))
+  val f0_fire       = fromFtq.req.fire
 
-  val f0_flush, f1_flush, f2_flush, f3_flush = WireInit(false.B)
+  val f0_flush, f1_flush, f2_flush, f3_flush                                     = WireInit(false.B)
   val from_bpu_f0_flush, from_bpu_f1_flush, from_bpu_f2_flush, from_bpu_f3_flush = WireInit(false.B)
 
   from_bpu_f0_flush := fromFtq.flushFromBpu.shouldFlushByStage2(f0_ftq_req.ftqIdx) ||
-                       fromFtq.flushFromBpu.shouldFlushByStage3(f0_ftq_req.ftqIdx)
+    fromFtq.flushFromBpu.shouldFlushByStage3(f0_ftq_req.ftqIdx)
 
-  val wb_redirect , mmio_redirect,  backend_redirect= WireInit(false.B)
-  val f3_wb_not_flush = WireInit(false.B)
+  val wb_redirect, mmio_redirect, backend_redirect = WireInit(false.B)
+  val f3_wb_not_flush                              = WireInit(false.B)
 
   backend_redirect := fromFtq.redirect.valid
-  f3_flush := backend_redirect || (wb_redirect && !f3_wb_not_flush)
-  f2_flush := backend_redirect || mmio_redirect || wb_redirect
-  f1_flush := f2_flush || from_bpu_f1_flush
-  f0_flush := f1_flush || from_bpu_f0_flush
+  f3_flush         := backend_redirect || (wb_redirect && !f3_wb_not_flush)
+  f2_flush         := backend_redirect || mmio_redirect || wb_redirect
+  f1_flush         := f2_flush || from_bpu_f1_flush
+  f0_flush         := f1_flush || from_bpu_f0_flush
 
-  val f1_ready, f2_ready, f3_ready         = WireInit(false.B)
+  val f1_ready, f2_ready, f3_ready = WireInit(false.B)
 
   fromFtq.req.ready := f1_ready && io.icacheInter.icacheReady
 
-
-  when (wb_redirect) {
-    when (f3_wb_not_flush) {
+  when(wb_redirect) {
+    when(f3_wb_not_flush) {
       topdown_stages(2).reasons(TopDownCounters.BTBMissBubble.id) := true.B
     }
     for (i <- 0 until numOfStage - 1) {
@@ -277,15 +276,14 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   /** <PERF> f0 fetch bubble */
 
-  XSPerfAccumulate("fetch_bubble_ftq_not_valid",   !fromFtq.req.valid && fromFtq.req.ready  )
+  XSPerfAccumulate("fetch_bubble_ftq_not_valid", !fromFtq.req.valid && fromFtq.req.ready)
   // XSPerfAccumulate("fetch_bubble_pipe_stall",    f0_valid && toICache(0).ready && toICache(1).ready && !f1_ready )
   // XSPerfAccumulate("fetch_bubble_icache_0_busy",   f0_valid && !toICache(0).ready  )
   // XSPerfAccumulate("fetch_bubble_icache_1_busy",   f0_valid && !toICache(1).ready  )
-  XSPerfAccumulate("fetch_flush_backend_redirect",   backend_redirect  )
-  XSPerfAccumulate("fetch_flush_wb_redirect",    wb_redirect  )
-  XSPerfAccumulate("fetch_flush_bpu_f1_flush",   from_bpu_f1_flush  )
-  XSPerfAccumulate("fetch_flush_bpu_f0_flush",   from_bpu_f0_flush  )
-
+  XSPerfAccumulate("fetch_flush_backend_redirect", backend_redirect)
+  XSPerfAccumulate("fetch_flush_wb_redirect", wb_redirect)
+  XSPerfAccumulate("fetch_flush_bpu_f1_flush", from_bpu_f1_flush)
+  XSPerfAccumulate("fetch_flush_bpu_f0_flush", from_bpu_f0_flush)
 
   /**
     ******************************************************************************
@@ -294,11 +292,11 @@ class NewIFU(implicit p: Parameters) extends XSModule
     ******************************************************************************
     */
 
-  val f1_valid      = RegInit(false.B)
-  val f1_ftq_req    = RegEnable(f0_ftq_req,    f0_fire)
+  val f1_valid   = RegInit(false.B)
+  val f1_ftq_req = RegEnable(f0_ftq_req, f0_fire)
   // val f1_situation  = RegEnable(f0_situation,  f0_fire)
   val f1_doubleLine = RegEnable(f0_doubleLine, f0_fire)
-  val f1_vSetIdx    = RegEnable(f0_vSetIdx,    f0_fire)
+  val f1_vSetIdx    = RegEnable(f0_vSetIdx, f0_fire)
   val f1_fire       = f1_valid && f2_ready
 
   f1_ready := f1_fire || !f1_valid
@@ -306,12 +304,12 @@ class NewIFU(implicit p: Parameters) extends XSModule
   from_bpu_f1_flush := fromFtq.flushFromBpu.shouldFlushByStage3(f1_ftq_req.ftqIdx) && f1_valid
   // from_bpu_f1_flush := false.B
 
-  when(f1_flush)                  {f1_valid  := false.B}
-  .elsewhen(f0_fire && !f0_flush) {f1_valid  := true.B}
-  .elsewhen(f1_fire)              {f1_valid  := false.B}
+  when(f1_flush)(f1_valid := false.B)
+    .elsewhen(f0_fire && !f0_flush)(f1_valid := true.B)
+    .elsewhen(f1_fire)(f1_valid := false.B)
 
-  val f1_pc_high            = f1_ftq_req.startAddr(VAddrBits-1, PcCutPoint)
-  val f1_pc_high_plus1      = f1_pc_high + 1.U
+  val f1_pc_high       = f1_ftq_req.startAddr(VAddrBits - 1, PcCutPoint)
+  val f1_pc_high_plus1 = f1_pc_high + 1.U
 
   /**
    * In order to reduce power consumption, avoid calculating the full PC value in the first level.
@@ -319,23 +317,34 @@ class NewIFU(implicit p: Parameters) extends XSModule
    * val f1_pc                 = VecInit(f1_pc_lower_result.map{ i =>
    *  Mux(i(f1_pc_adder_cut_point), Cat(f1_pc_high_plus1,i(f1_pc_adder_cut_point-1,0)), Cat(f1_pc_high,i(f1_pc_adder_cut_point-1,0)))})
    */
-  val f1_pc_lower_result    = VecInit((0 until PredictWidth).map(i => Cat(0.U(1.W), f1_ftq_req.startAddr(PcCutPoint-1, 0)) + (i * 2).U)) // cat with overflow bit
+  val f1_pc_lower_result = VecInit((0 until PredictWidth).map(i =>
+    Cat(0.U(1.W), f1_ftq_req.startAddr(PcCutPoint - 1, 0)) + (i * 2).U
+  )) // cat with overflow bit
 
-  val f1_pc                 = CatPC(f1_pc_lower_result, f1_pc_high, f1_pc_high_plus1)
+  val f1_pc = CatPC(f1_pc_lower_result, f1_pc_high, f1_pc_high_plus1)
 
-  val f1_half_snpc_lower_result = VecInit((0 until PredictWidth).map(i => Cat(0.U(1.W), f1_ftq_req.startAddr(PcCutPoint-1, 0)) + ((i+2) * 2).U)) // cat with overflow bit
-  val f1_half_snpc            = CatPC(f1_half_snpc_lower_result, f1_pc_high, f1_pc_high_plus1)
+  val f1_half_snpc_lower_result = VecInit((0 until PredictWidth).map(i =>
+    Cat(0.U(1.W), f1_ftq_req.startAddr(PcCutPoint - 1, 0)) + ((i + 2) * 2).U
+  )) // cat with overflow bit
+  val f1_half_snpc = CatPC(f1_half_snpc_lower_result, f1_pc_high, f1_pc_high_plus1)
 
-  if (env.FPGAPlatform){
-    val f1_pc_diff          = VecInit((0 until PredictWidth).map(i => f1_ftq_req.startAddr + (i * 2).U))
-    val f1_half_snpc_diff   = VecInit((0 until PredictWidth).map(i => f1_ftq_req.startAddr + ((i+2) * 2).U))
+  if (env.FPGAPlatform) {
+    val f1_pc_diff        = VecInit((0 until PredictWidth).map(i => f1_ftq_req.startAddr + (i * 2).U))
+    val f1_half_snpc_diff = VecInit((0 until PredictWidth).map(i => f1_ftq_req.startAddr + ((i + 2) * 2).U))
 
-    XSError(f1_pc.zip(f1_pc_diff).map{ case (a,b) => a.asUInt =/= b.asUInt }.reduce(_||_), "f1_half_snpc adder cut fail")
-    XSError(f1_half_snpc.zip(f1_half_snpc_diff).map{ case (a,b) => a.asUInt =/= b.asUInt }.reduce(_||_),  "f1_half_snpc adder cut fail")
+    XSError(
+      f1_pc.zip(f1_pc_diff).map { case (a, b) => a.asUInt =/= b.asUInt }.reduce(_ || _),
+      "f1_half_snpc adder cut fail"
+    )
+    XSError(
+      f1_half_snpc.zip(f1_half_snpc_diff).map { case (a, b) => a.asUInt =/= b.asUInt }.reduce(_ || _),
+      "f1_half_snpc adder cut fail"
+    )
   }
 
-  val f1_cut_ptr            = if(HasCExtension)  VecInit((0 until PredictWidth + 1).map(i =>  Cat(0.U(2.W), f1_ftq_req.startAddr(blockOffBits-1, 1)) + i.U ))
-                                  else           VecInit((0 until PredictWidth).map(i =>     Cat(0.U(2.W), f1_ftq_req.startAddr(blockOffBits-1, 2)) + i.U ))
+  val f1_cut_ptr = if (HasCExtension)
+    VecInit((0 until PredictWidth + 1).map(i => Cat(0.U(2.W), f1_ftq_req.startAddr(blockOffBits - 1, 1)) + i.U))
+  else VecInit((0 until PredictWidth).map(i => Cat(0.U(2.W), f1_ftq_req.startAddr(blockOffBits - 1, 2)) + i.U))
 
   /**
     ******************************************************************************
@@ -350,17 +359,20 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   val icacheRespAllValid = WireInit(false.B)
 
-  val f2_valid      = RegInit(false.B)
-  val f2_ftq_req    = RegEnable(f1_ftq_req,    f1_fire)
+  val f2_valid   = RegInit(false.B)
+  val f2_ftq_req = RegEnable(f1_ftq_req, f1_fire)
   // val f2_situation  = RegEnable(f1_situation,  f1_fire)
   val f2_doubleLine = RegEnable(f1_doubleLine, f1_fire)
-  val f2_vSetIdx    = RegEnable(f1_vSetIdx,    f1_fire)
+  val f2_vSetIdx    = RegEnable(f1_vSetIdx, f1_fire)
   val f2_fire       = f2_valid && f3_ready && icacheRespAllValid
 
   f2_ready := f2_fire || !f2_valid
-  //TODO: addr compare may be timing critical
-  val f2_icache_all_resp_wire       =  fromICache(0).valid && (fromICache(0).bits.vaddr ===  f2_ftq_req.startAddr) && ((fromICache(1).valid && (fromICache(1).bits.vaddr ===  f2_ftq_req.nextlineStart)) || !f2_doubleLine)
-  val f2_icache_all_resp_reg        = RegInit(false.B)
+  // TODO: addr compare may be timing critical
+  val f2_icache_all_resp_wire =
+    fromICache(0).valid && (fromICache(0).bits.vaddr === f2_ftq_req.startAddr) && ((fromICache(1).valid && (fromICache(
+      1
+    ).bits.vaddr === f2_ftq_req.nextlineStart)) || !f2_doubleLine)
+  val f2_icache_all_resp_reg = RegInit(false.B)
 
   icacheRespAllValid := f2_icache_all_resp_reg || f2_icache_all_resp_wire
 
@@ -369,70 +381,76 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   io.icacheStop := !f3_ready
 
-  when(f2_flush)                                              {f2_icache_all_resp_reg := false.B}
-  .elsewhen(f2_valid && f2_icache_all_resp_wire && !f3_ready) {f2_icache_all_resp_reg := true.B}
-  .elsewhen(f2_fire && f2_icache_all_resp_reg)                {f2_icache_all_resp_reg := false.B}
+  when(f2_flush)(f2_icache_all_resp_reg := false.B)
+    .elsewhen(f2_valid && f2_icache_all_resp_wire && !f3_ready)(f2_icache_all_resp_reg := true.B)
+    .elsewhen(f2_fire && f2_icache_all_resp_reg)(f2_icache_all_resp_reg := false.B)
 
-  when(f2_flush)                  {f2_valid := false.B}
-  .elsewhen(f1_fire && !f1_flush) {f2_valid := true.B }
-  .elsewhen(f2_fire)              {f2_valid := false.B}
+  when(f2_flush)(f2_valid := false.B)
+    .elsewhen(f1_fire && !f1_flush)(f2_valid := true.B)
+    .elsewhen(f2_fire)(f2_valid := false.B)
 
-  val f2_exception    = VecInit((0 until PortNumber).map(i => fromICache(i).bits.exception))
+  val f2_exception          = VecInit((0 until PortNumber).map(i => fromICache(i).bits.exception))
   val f2_except_fromBackend = fromICache(0).bits.exceptionFromBackend
   // paddr and gpaddr of [startAddr, nextLineAddr]
-  val f2_paddrs       = VecInit((0 until PortNumber).map(i => fromICache(i).bits.paddr))
-  val f2_gpaddr       = fromICache(0).bits.gpaddr
-  val f2_isForVSnonLeafPTE      = fromICache(0).bits.isForVSnonLeafPTE
+  val f2_paddrs            = VecInit((0 until PortNumber).map(i => fromICache(i).bits.paddr))
+  val f2_gpaddr            = fromICache(0).bits.gpaddr
+  val f2_isForVSnonLeafPTE = fromICache(0).bits.isForVSnonLeafPTE
 
   // FIXME: what if port 0 is not mmio, but port 1 is?
   // cancel mmio fetch if exception occurs
-  val f2_mmio         = f2_exception(0) === ExceptionType.none && (
+  val f2_mmio = f2_exception(0) === ExceptionType.none && (
     fromICache(0).bits.pmp_mmio ||
       // currently, we do not distinguish between Pbmt.nc and Pbmt.io
       // anyway, they are both non-cacheable, and should be handled with mmio fsm and sent to Uncache module
       Pbmt.isUncache(fromICache(0).bits.itlb_pbmt)
   )
 
-
   /**
     * reduce the number of registers, origin code
     * f2_pc = RegEnable(f1_pc, f1_fire)
     */
-  val f2_pc_lower_result        = RegEnable(f1_pc_lower_result, f1_fire)
-  val f2_pc_high                = RegEnable(f1_pc_high, f1_fire)
-  val f2_pc_high_plus1          = RegEnable(f1_pc_high_plus1, f1_fire)
-  val f2_pc                     = CatPC(f2_pc_lower_result, f2_pc_high, f2_pc_high_plus1)
+  val f2_pc_lower_result = RegEnable(f1_pc_lower_result, f1_fire)
+  val f2_pc_high         = RegEnable(f1_pc_high, f1_fire)
+  val f2_pc_high_plus1   = RegEnable(f1_pc_high_plus1, f1_fire)
+  val f2_pc              = CatPC(f2_pc_lower_result, f2_pc_high, f2_pc_high_plus1)
 
-  val f2_cut_ptr                = RegEnable(f1_cut_ptr, f1_fire)
-  val f2_resend_vaddr           = RegEnable(f1_ftq_req.startAddr + 2.U, f1_fire)
+  val f2_cut_ptr      = RegEnable(f1_cut_ptr, f1_fire)
+  val f2_resend_vaddr = RegEnable(f1_ftq_req.startAddr + 2.U, f1_fire)
 
-  def isNextLine(pc: UInt, startAddr: UInt) = {
+  def isNextLine(pc: UInt, startAddr: UInt) =
     startAddr(blockOffBits) ^ pc(blockOffBits)
-  }
 
-  def isLastInLine(pc: UInt) = {
+  def isLastInLine(pc: UInt) =
     pc(blockOffBits - 1, 0) === "b111110".U
-  }
 
-  val f2_foldpc = VecInit(f2_pc.map(i => XORFold(i(VAddrBits-1,1), MemPredPCWidth)))
-  val f2_jump_range = Fill(PredictWidth, !f2_ftq_req.ftqOffset.valid) | Fill(PredictWidth, 1.U(1.W)) >> ~f2_ftq_req.ftqOffset.bits
-  val f2_ftr_range  = Fill(PredictWidth,  f2_ftq_req.ftqOffset.valid) | Fill(PredictWidth, 1.U(1.W)) >> ~getBasicBlockIdx(f2_ftq_req.nextStartAddr, f2_ftq_req.startAddr)
+  val f2_foldpc = VecInit(f2_pc.map(i => XORFold(i(VAddrBits - 1, 1), MemPredPCWidth)))
+  val f2_jump_range =
+    Fill(PredictWidth, !f2_ftq_req.ftqOffset.valid) | Fill(PredictWidth, 1.U(1.W)) >> ~f2_ftq_req.ftqOffset.bits
+  val f2_ftr_range = Fill(PredictWidth, f2_ftq_req.ftqOffset.valid) | Fill(PredictWidth, 1.U(1.W)) >> ~getBasicBlockIdx(
+    f2_ftq_req.nextStartAddr,
+    f2_ftq_req.startAddr
+  )
   val f2_instr_range = f2_jump_range & f2_ftr_range
-  val f2_exception_vec = VecInit((0 until PredictWidth).map( i => MuxCase(ExceptionType.none, Seq(
-      !isNextLine(f2_pc(i), f2_ftq_req.startAddr)                   -> f2_exception(0),
-      (isNextLine(f2_pc(i), f2_ftq_req.startAddr) && f2_doubleLine) -> f2_exception(1)
-  ))))
-  val f2_perf_info    = io.icachePerfInfo
+  val f2_exception_vec = VecInit((0 until PredictWidth).map(i =>
+    MuxCase(
+      ExceptionType.none,
+      Seq(
+        !isNextLine(f2_pc(i), f2_ftq_req.startAddr)                   -> f2_exception(0),
+        (isNextLine(f2_pc(i), f2_ftq_req.startAddr) && f2_doubleLine) -> f2_exception(1)
+      )
+    )
+  ))
+  val f2_perf_info = io.icachePerfInfo
 
-  def cut(cacheline: UInt, cutPtr: Vec[UInt]) : Vec[UInt] ={
+  def cut(cacheline: UInt, cutPtr: Vec[UInt]): Vec[UInt] = {
     require(HasCExtension)
     // if(HasCExtension){
-      val result   = Wire(Vec(PredictWidth + 1, UInt(16.W)))
-      val dataVec  = cacheline.asTypeOf(Vec(blockBytes, UInt(16.W))) //32 16-bit data vector
-      (0 until PredictWidth + 1).foreach( i =>
-        result(i) := dataVec(cutPtr(i)) //the max ptr is 3*blockBytes/4-1
-      )
-      result
+    val result  = Wire(Vec(PredictWidth + 1, UInt(16.W)))
+    val dataVec = cacheline.asTypeOf(Vec(blockBytes, UInt(16.W))) // 32 16-bit data vector
+    (0 until PredictWidth + 1).foreach(i =>
+      result(i) := dataVec(cutPtr(i)) // the max ptr is 3*blockBytes/4-1
+    )
+    result
     // } else {
     //   val result   = Wire(Vec(PredictWidth, UInt(32.W)) )
     //   val dataVec  = cacheline.asTypeOf(Vec(blockBytes * 2/ 4, UInt(32.W)))
@@ -444,9 +462,9 @@ class NewIFU(implicit p: Parameters) extends XSModule
   }
 
   val f2_cache_response_data = fromICache.map(_.bits.data)
-  val f2_data_2_cacheline = Cat(f2_cache_response_data(0), f2_cache_response_data(0))
+  val f2_data_2_cacheline    = Cat(f2_cache_response_data(0), f2_cache_response_data(0))
 
-  val f2_cut_data   = cut(f2_data_2_cacheline, f2_cut_ptr)
+  val f2_cut_data = cut(f2_data_2_cacheline, f2_cut_ptr)
 
   /** predecode (include RVC expander) */
   // preDecoderRegIn.data := f2_reg_cut_data
@@ -454,28 +472,29 @@ class NewIFU(implicit p: Parameters) extends XSModule
   // preDecoderRegInIn.csrTriggerEnable := io.csrTriggerEnable
   // preDecoderRegIn.pc  := f2_pc
 
-  val preDecoderIn  = preDecoder.io.in
-  preDecoderIn.valid := f2_valid
-  preDecoderIn.bits.data := f2_cut_data
+  val preDecoderIn = preDecoder.io.in
+  preDecoderIn.valid                := f2_valid
+  preDecoderIn.bits.data            := f2_cut_data
   preDecoderIn.bits.frontendTrigger := io.frontendTrigger
-  preDecoderIn.bits.pc  := f2_pc
+  preDecoderIn.bits.pc              := f2_pc
   val preDecoderOut = preDecoder.io.out
 
-  //val f2_expd_instr     = preDecoderOut.expInstr
-  val f2_instr          = preDecoderOut.instr
-  val f2_pd             = preDecoderOut.pd
-  val f2_jump_offset    = preDecoderOut.jumpOffset
-  val f2_hasHalfValid   =  preDecoderOut.hasHalfValid
+  // val f2_expd_instr     = preDecoderOut.expInstr
+  val f2_instr        = preDecoderOut.instr
+  val f2_pd           = preDecoderOut.pd
+  val f2_jump_offset  = preDecoderOut.jumpOffset
+  val f2_hasHalfValid = preDecoderOut.hasHalfValid
   /* if there is a cross-page RVI instruction, and the former page has no exception,
    * whether it has exception is actually depends on the latter page
    */
-  val f2_crossPage_exception_vec = VecInit((0 until PredictWidth).map { i => Mux(
-    isLastInLine(f2_pc(i)) && !f2_pd(i).isRVC && f2_doubleLine && f2_exception(0) === ExceptionType.none,
-    f2_exception(1),
-    ExceptionType.none
-  )})
-  XSPerfAccumulate("fetch_bubble_icache_not_resp",   f2_valid && !icacheRespAllValid )
-
+  val f2_crossPage_exception_vec = VecInit((0 until PredictWidth).map { i =>
+    Mux(
+      isLastInLine(f2_pc(i)) && !f2_pd(i).isRVC && f2_doubleLine && f2_exception(0) === ExceptionType.none,
+      f2_exception(1),
+      ExceptionType.none
+    )
+  })
+  XSPerfAccumulate("fetch_bubble_icache_not_resp", f2_valid && !icacheRespAllValid)
 
   /**
     ******************************************************************************
@@ -493,34 +512,34 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   val expanders = Seq.fill(PredictWidth)(Module(new RVCExpander))
 
-  val f3_valid          = RegInit(false.B)
-  val f3_ftq_req        = RegEnable(f2_ftq_req,    f2_fire)
+  val f3_valid   = RegInit(false.B)
+  val f3_ftq_req = RegEnable(f2_ftq_req, f2_fire)
   // val f3_situation      = RegEnable(f2_situation,  f2_fire)
-  val f3_doubleLine     = RegEnable(f2_doubleLine, f2_fire)
-  val f3_fire           = io.toIbuffer.fire
+  val f3_doubleLine = RegEnable(f2_doubleLine, f2_fire)
+  val f3_fire       = io.toIbuffer.fire
 
-  val f3_cut_data       = RegEnable(f2_cut_data,   f2_fire)
+  val f3_cut_data = RegEnable(f2_cut_data, f2_fire)
 
-  val f3_exception      = RegEnable(f2_exception,  f2_fire)
-  val f3_mmio           = RegEnable(f2_mmio,       f2_fire)
+  val f3_exception          = RegEnable(f2_exception, f2_fire)
+  val f3_mmio               = RegEnable(f2_mmio, f2_fire)
   val f3_except_fromBackend = RegEnable(f2_except_fromBackend, f2_fire)
 
-  val f3_instr          = RegEnable(f2_instr, f2_fire)
+  val f3_instr = RegEnable(f2_instr, f2_fire)
 
   expanders.zipWithIndex.foreach { case (expander, i) =>
     expander.io.in := f3_instr(i)
   }
   // Use expanded instruction only when input is legal.
   // Otherwise use origin illegal RVC instruction.
-  val f3_expd_instr     = VecInit(expanders.map { expander: RVCExpander =>
+  val f3_expd_instr = VecInit(expanders.map { expander: RVCExpander =>
     Mux(expander.io.ill, expander.io.in, expander.io.out.bits)
   })
-  val f3_ill            = VecInit(expanders.map(_.io.ill))
+  val f3_ill = VecInit(expanders.map(_.io.ill))
 
-  val f3_pd_wire         = RegEnable(f2_pd,            f2_fire)
-  val f3_pd              = WireInit(f3_pd_wire)
-  val f3_jump_offset     = RegEnable(f2_jump_offset,   f2_fire)
-  val f3_exception_vec   = RegEnable(f2_exception_vec, f2_fire)
+  val f3_pd_wire                 = RegEnable(f2_pd, f2_fire)
+  val f3_pd                      = WireInit(f3_pd_wire)
+  val f3_jump_offset             = RegEnable(f2_jump_offset, f2_fire)
+  val f3_exception_vec           = RegEnable(f2_exception_vec, f2_fire)
   val f3_crossPage_exception_vec = RegEnable(f2_crossPage_exception_vec, f2_fire)
 
   val f3_pc_lower_result = RegEnable(f2_pc_lower_result, f2_fire)
@@ -530,81 +549,92 @@ class NewIFU(implicit p: Parameters) extends XSModule
 
   val f3_pc_last_lower_result_plus2 = RegEnable(f2_pc_lower_result(PredictWidth - 1) + 2.U, f2_fire)
   val f3_pc_last_lower_result_plus4 = RegEnable(f2_pc_lower_result(PredictWidth - 1) + 4.U, f2_fire)
-  //val f3_half_snpc      = RegEnable(f2_half_snpc,   f2_fire)
+  // val f3_half_snpc      = RegEnable(f2_half_snpc,   f2_fire)
 
   /**
     ***********************************************************************
     * Half snpc(i) is larger than pc(i) by 4. Using pc to calculate half snpc may be a good choice.
     ***********************************************************************
     */
-  val f3_half_snpc      = Wire(Vec(PredictWidth,UInt(VAddrBits.W)))
-  for(i <- 0 until PredictWidth){
-    if(i == (PredictWidth - 2)){
-      f3_half_snpc(i)   := CatPC(f3_pc_last_lower_result_plus2, f3_pc_high, f3_pc_high_plus1)
-    } else if (i == (PredictWidth - 1)){
-      f3_half_snpc(i)   := CatPC(f3_pc_last_lower_result_plus4, f3_pc_high, f3_pc_high_plus1)
+  val f3_half_snpc = Wire(Vec(PredictWidth, UInt(VAddrBits.W)))
+  for (i <- 0 until PredictWidth) {
+    if (i == (PredictWidth - 2)) {
+      f3_half_snpc(i) := CatPC(f3_pc_last_lower_result_plus2, f3_pc_high, f3_pc_high_plus1)
+    } else if (i == (PredictWidth - 1)) {
+      f3_half_snpc(i) := CatPC(f3_pc_last_lower_result_plus4, f3_pc_high, f3_pc_high_plus1)
     } else {
-      f3_half_snpc(i)   := f3_pc(i+2)
+      f3_half_snpc(i) := f3_pc(i + 2)
     }
   }
 
-  val f3_instr_range    = RegEnable(f2_instr_range, f2_fire)
-  val f3_foldpc         = RegEnable(f2_foldpc,      f2_fire)
-  val f3_hasHalfValid   = RegEnable(f2_hasHalfValid,             f2_fire)
-  val f3_paddrs         = RegEnable(f2_paddrs,  f2_fire)
-  val f3_gpaddr         = RegEnable(f2_gpaddr,  f2_fire)
-  val f3_isForVSnonLeafPTE        = RegEnable(f2_isForVSnonLeafPTE, f2_fire)
-  val f3_resend_vaddr   = RegEnable(f2_resend_vaddr,             f2_fire)
+  val f3_instr_range       = RegEnable(f2_instr_range, f2_fire)
+  val f3_foldpc            = RegEnable(f2_foldpc, f2_fire)
+  val f3_hasHalfValid      = RegEnable(f2_hasHalfValid, f2_fire)
+  val f3_paddrs            = RegEnable(f2_paddrs, f2_fire)
+  val f3_gpaddr            = RegEnable(f2_gpaddr, f2_fire)
+  val f3_isForVSnonLeafPTE = RegEnable(f2_isForVSnonLeafPTE, f2_fire)
+  val f3_resend_vaddr      = RegEnable(f2_resend_vaddr, f2_fire)
 
   // Expand 1 bit to prevent overflow when assert
-  val f3_ftq_req_startAddr      = Cat(0.U(1.W), f3_ftq_req.startAddr)
-  val f3_ftq_req_nextStartAddr  = Cat(0.U(1.W), f3_ftq_req.nextStartAddr)
+  val f3_ftq_req_startAddr     = Cat(0.U(1.W), f3_ftq_req.startAddr)
+  val f3_ftq_req_nextStartAddr = Cat(0.U(1.W), f3_ftq_req.nextStartAddr)
   // brType, isCall and isRet generation is delayed to f3 stage
   val f3Predecoder = Module(new F3Predecoder)
 
   f3Predecoder.io.in.instr := f3_instr
 
-  f3_pd.zipWithIndex.map{ case (pd,i) =>
+  f3_pd.zipWithIndex.map { case (pd, i) =>
     pd.brType := f3Predecoder.io.out.pd(i).brType
     pd.isCall := f3Predecoder.io.out.pd(i).isCall
     pd.isRet  := f3Predecoder.io.out.pd(i).isRet
   }
 
-  val f3PdDiff = f3_pd_wire.zip(f3_pd).map{ case (a,b) => a.asUInt =/= b.asUInt }.reduce(_||_)
+  val f3PdDiff = f3_pd_wire.zip(f3_pd).map { case (a, b) => a.asUInt =/= b.asUInt }.reduce(_ || _)
   XSError(f3_valid && f3PdDiff, "f3 pd diff")
 
-  when(f3_valid && !f3_ftq_req.ftqOffset.valid){
-    assert(f3_ftq_req_startAddr + (2*PredictWidth).U >= f3_ftq_req_nextStartAddr, s"More tha ${2*PredictWidth} Bytes fetch is not allowed!")
+  when(f3_valid && !f3_ftq_req.ftqOffset.valid) {
+    assert(
+      f3_ftq_req_startAddr + (2 * PredictWidth).U >= f3_ftq_req_nextStartAddr,
+      s"More tha ${2 * PredictWidth} Bytes fetch is not allowed!"
+    )
   }
 
   /*** MMIO State Machine***/
-  val f3_mmio_data          = Reg(Vec(2, UInt(16.W)))
-  val mmio_is_RVC           = RegInit(false.B)
-  val mmio_resend_addr      = RegInit(0.U(PAddrBits.W))
-  val mmio_resend_exception = RegInit(0.U(ExceptionType.width.W))
-  val mmio_resend_gpaddr    = RegInit(0.U(GPAddrBits.W))
-  val mmio_resend_isForVSnonLeafPTE    = RegInit(false.B)
-
-  //last instuction finish
+  val f3_mmio_data                  = Reg(Vec(2, UInt(16.W)))
+  val mmio_is_RVC                   = RegInit(false.B)
+  val mmio_resend_addr              = RegInit(0.U(PAddrBits.W))
+  val mmio_resend_exception         = RegInit(0.U(ExceptionType.width.W))
+  val mmio_resend_gpaddr            = RegInit(0.U(GPAddrBits.W))
+  val mmio_resend_isForVSnonLeafPTE = RegInit(false.B)
+
+  // last instuction finish
   val is_first_instr = RegInit(true.B)
+
   /*** Determine whether the MMIO instruction is executable based on the previous prediction block ***/
   io.mmioCommitRead.mmioFtqPtr := RegNext(f3_ftq_req.ftqIdx - 1.U)
 
-  val m_idle :: m_waitLastCmt:: m_sendReq :: m_waitResp :: m_sendTLB :: m_tlbResp :: m_sendPMP :: m_resendReq :: m_waitResendResp :: m_waitCommit :: m_commited :: Nil = Enum(11)
+  val m_idle :: m_waitLastCmt :: m_sendReq :: m_waitResp :: m_sendTLB :: m_tlbResp :: m_sendPMP :: m_resendReq :: m_waitResendResp :: m_waitCommit :: m_commited :: Nil =
+    Enum(11)
   val mmio_state = RegInit(m_idle)
 
-  val f3_req_is_mmio     = f3_mmio && f3_valid
-  val mmio_commit = VecInit(io.rob_commits.map{commit => commit.valid && commit.bits.ftqIdx === f3_ftq_req.ftqIdx &&  commit.bits.ftqOffset === 0.U}).asUInt.orR
+  val f3_req_is_mmio = f3_mmio && f3_valid
+  val mmio_commit = VecInit(io.rob_commits.map { commit =>
+    commit.valid && commit.bits.ftqIdx === f3_ftq_req.ftqIdx && commit.bits.ftqOffset === 0.U
+  }).asUInt.orR
   val f3_mmio_req_commit = f3_req_is_mmio && mmio_state === m_commited
 
-  val f3_mmio_to_commit =  f3_req_is_mmio && mmio_state === m_waitCommit
+  val f3_mmio_to_commit      = f3_req_is_mmio && mmio_state === m_waitCommit
   val f3_mmio_to_commit_next = RegNext(f3_mmio_to_commit)
-  val f3_mmio_can_go      = f3_mmio_to_commit && !f3_mmio_to_commit_next
+  val f3_mmio_can_go         = f3_mmio_to_commit && !f3_mmio_to_commit_next
 
   val fromFtqRedirectReg = Wire(fromFtq.redirect.cloneType)
-  fromFtqRedirectReg.bits := RegEnable(fromFtq.redirect.bits, 0.U.asTypeOf(fromFtq.redirect.bits), fromFtq.redirect.valid)
+  fromFtqRedirectReg.bits := RegEnable(
+    fromFtq.redirect.bits,
+    0.U.asTypeOf(fromFtq.redirect.bits),
+    fromFtq.redirect.valid
+  )
   fromFtqRedirectReg.valid := RegNext(fromFtq.redirect.valid, init = false.B)
-  val mmioF3Flush           = RegNext(f3_flush,init = false.B)
+  val mmioF3Flush           = RegNext(f3_flush, init = false.B)
   val f3_ftq_flush_self     = fromFtqRedirectReg.valid && RedirectLevel.flushItself(fromFtqRedirectReg.bits.level)
   val f3_ftq_flush_by_older = fromFtqRedirectReg.valid && isBefore(fromFtqRedirectReg.bits.ftqIdx, f3_ftq_req.ftqIdx)
 
@@ -616,62 +646,63 @@ class NewIFU(implicit p: Parameters) extends XSModule
     * This is the exception when the first instruction is an MMIO instruction.
     **********************************************************************************
     */
-  when(is_first_instr && f3_fire){
+  when(is_first_instr && f3_fire) {
     is_first_instr := false.B
   }
 
-  when(f3_flush && !f3_req_is_mmio)                                                 {f3_valid := false.B}
-  .elsewhen(mmioF3Flush && f3_req_is_mmio && !f3_need_not_flush)                    {f3_valid := false.B}
-  .elsewhen(f2_fire && !f2_flush )                                                  {f3_valid := true.B }
-  .elsewhen(io.toIbuffer.fire && !f3_req_is_mmio)                                   {f3_valid := false.B}
-  .elsewhen{f3_req_is_mmio && f3_mmio_req_commit}                                   {f3_valid := false.B}
+  when(f3_flush && !f3_req_is_mmio)(f3_valid := false.B)
+    .elsewhen(mmioF3Flush && f3_req_is_mmio && !f3_need_not_flush)(f3_valid := false.B)
+    .elsewhen(f2_fire && !f2_flush)(f3_valid := true.B)
+    .elsewhen(io.toIbuffer.fire && !f3_req_is_mmio)(f3_valid := false.B)
+    .elsewhen(f3_req_is_mmio && f3_mmio_req_commit)(f3_valid := false.B)
 
   val f3_mmio_use_seq_pc = RegInit(false.B)
 
-  val (redirect_ftqIdx, redirect_ftqOffset)  = (fromFtqRedirectReg.bits.ftqIdx,fromFtqRedirectReg.bits.ftqOffset)
-  val redirect_mmio_req = fromFtqRedirectReg.valid && redirect_ftqIdx === f3_ftq_req.ftqIdx && redirect_ftqOffset === 0.U
+  val (redirect_ftqIdx, redirect_ftqOffset) = (fromFtqRedirectReg.bits.ftqIdx, fromFtqRedirectReg.bits.ftqOffset)
+  val redirect_mmio_req =
+    fromFtqRedirectReg.valid && redirect_ftqIdx === f3_ftq_req.ftqIdx && redirect_ftqOffset === 0.U
 
-  when(RegNext(f2_fire && !f2_flush) && f3_req_is_mmio)        { f3_mmio_use_seq_pc := true.B  }
-  .elsewhen(redirect_mmio_req)                                 { f3_mmio_use_seq_pc := false.B }
+  when(RegNext(f2_fire && !f2_flush) && f3_req_is_mmio)(f3_mmio_use_seq_pc := true.B)
+    .elsewhen(redirect_mmio_req)(f3_mmio_use_seq_pc := false.B)
 
   f3_ready := (io.toIbuffer.ready && (f3_mmio_req_commit || !f3_req_is_mmio)) || !f3_valid
 
   // mmio state machine
-  switch(mmio_state){
-    is(m_idle){
-      when(f3_req_is_mmio){
+  switch(mmio_state) {
+    is(m_idle) {
+      when(f3_req_is_mmio) {
         mmio_state := m_waitLastCmt
       }
     }
 
-    is(m_waitLastCmt){
-      when(is_first_instr){
+    is(m_waitLastCmt) {
+      when(is_first_instr) {
         mmio_state := m_sendReq
-      }.otherwise{
+      }.otherwise {
         mmio_state := Mux(io.mmioCommitRead.mmioLastCommit, m_sendReq, m_waitLastCmt)
       }
     }
 
-    is(m_sendReq){
+    is(m_sendReq) {
       mmio_state := Mux(toUncache.fire, m_waitResp, m_sendReq)
     }
 
-    is(m_waitResp){
-      when(fromUncache.fire){
-          val isRVC = fromUncache.bits.data(1,0) =/= 3.U
-          val needResend = !isRVC && f3_paddrs(0)(2,1) === 3.U
-          mmio_state      := Mux(needResend, m_sendTLB, m_waitCommit)
-          mmio_is_RVC     := isRVC
-          f3_mmio_data(0) := fromUncache.bits.data(15,0)
-          f3_mmio_data(1) := fromUncache.bits.data(31,16)
+    is(m_waitResp) {
+      when(fromUncache.fire) {
+        val isRVC      = fromUncache.bits.data(1, 0) =/= 3.U
+        val needResend = !isRVC && f3_paddrs(0)(2, 1) === 3.U
+        mmio_state      := Mux(needResend, m_sendTLB, m_waitCommit)
+        mmio_is_RVC     := isRVC
+        f3_mmio_data(0) := fromUncache.bits.data(15, 0)
+        f3_mmio_data(1) := fromUncache.bits.data(31, 16)
       }
     }
 
-    is(m_sendTLB){
+    is(m_sendTLB) {
       mmio_state := Mux(io.iTLBInter.req.fire, m_tlbResp, m_sendTLB)
     }
 
-    is(m_tlbResp){
+    is(m_tlbResp) {
       when(io.iTLBInter.resp.fire) {
         // we are using a blocked tlb, so resp.fire must have !resp.bits.miss
         assert(!io.iTLBInter.resp.bits.miss, "blocked mode iTLB miss when resp.fire")
@@ -679,14 +710,14 @@ class NewIFU(implicit p: Parameters) extends XSModule
         // if tlb has exception, abort checking pmp, just send instr & exception to ibuffer and wait for commit
         mmio_state := Mux(tlb_exception === ExceptionType.none, m_sendPMP, m_waitCommit)
         // also save itlb response
-        mmio_resend_addr      := io.iTLBInter.resp.bits.paddr(0)
-        mmio_resend_exception := tlb_exception
-        mmio_resend_gpaddr    := io.iTLBInter.resp.bits.gpaddr(0)
-        mmio_resend_isForVSnonLeafPTE   := io.iTLBInter.resp.bits.isForVSnonLeafPTE(0)
+        mmio_resend_addr              := io.iTLBInter.resp.bits.paddr(0)
+        mmio_resend_exception         := tlb_exception
+        mmio_resend_gpaddr            := io.iTLBInter.resp.bits.gpaddr(0)
+        mmio_resend_isForVSnonLeafPTE := io.iTLBInter.resp.bits.isForVSnonLeafPTE(0)
       }
     }
 
-    is(m_sendPMP){
+    is(m_sendPMP) {
       // if pmp re-check does not respond mmio, must be access fault
       val pmp_exception = Mux(io.pmp.resp.mmio, ExceptionType.fromPMPResp(io.pmp.resp), ExceptionType.af)
       // if pmp has exception, abort sending request, just send instr & exception to ibuffer and wait for commit
@@ -695,14 +726,14 @@ class NewIFU(implicit p: Parameters) extends XSModule
       mmio_resend_exception := pmp_exception
     }
 
-    is(m_resendReq){
+    is(m_resendReq) {
       mmio_state := Mux(toUncache.fire, m_waitResendResp, m_resendReq)
     }
 
     is(m_waitResendResp) {
       when(fromUncache.fire) {
         mmio_state      := m_waitCommit
-        f3_mmio_data(1) := fromUncache.bits.data(15,0)
+        f3_mmio_data(1) := fromUncache.bits.data(15, 0)
       }
     }
 
@@ -710,31 +741,31 @@ class NewIFU(implicit p: Parameters) extends XSModule
       mmio_state := Mux(mmio_commit, m_commited, m_waitCommit)
     }
 
-    //normal mmio instruction
+    // normal mmio instruction
     is(m_commited) {
-      mmio_state            := m_idle
-      mmio_is_RVC           := false.B
-      mmio_resend_addr      := 0.U
-      mmio_resend_exception := ExceptionType.none
-      mmio_resend_gpaddr    := 0.U
-      mmio_resend_isForVSnonLeafPTE   := false.B
+      mmio_state                    := m_idle
+      mmio_is_RVC                   := false.B
+      mmio_resend_addr              := 0.U
+      mmio_resend_exception         := ExceptionType.none
+      mmio_resend_gpaddr            := 0.U
+      mmio_resend_isForVSnonLeafPTE := false.B
     }
   }
 
   // Exception or flush by older branch prediction
   // Condition is from RegNext(fromFtq.redirect), 1 cycle after backend rediect
   when(f3_ftq_flush_self || f3_ftq_flush_by_older) {
-    mmio_state            := m_idle
-    mmio_is_RVC           := false.B
-    mmio_resend_addr      := 0.U
-    mmio_resend_exception := ExceptionType.none
-    mmio_resend_gpaddr    := 0.U
-    mmio_resend_isForVSnonLeafPTE   := false.B
+    mmio_state                    := m_idle
+    mmio_is_RVC                   := false.B
+    mmio_resend_addr              := 0.U
+    mmio_resend_exception         := ExceptionType.none
+    mmio_resend_gpaddr            := 0.U
+    mmio_resend_isForVSnonLeafPTE := false.B
     f3_mmio_data.map(_ := 0.U)
   }
 
   toUncache.valid     := ((mmio_state === m_sendReq) || (mmio_state === m_resendReq)) && f3_req_is_mmio
-  toUncache.bits.addr := Mux((mmio_state === m_resendReq), mmio_resend_addr, f3_paddrs(0))
+  toUncache.bits.addr := Mux(mmio_state === m_resendReq, mmio_resend_addr, f3_paddrs(0))
   fromUncache.ready   := true.B
 
   // send itlb request in m_sendTLB state
@@ -759,78 +790,81 @@ class NewIFU(implicit p: Parameters) extends XSModule
   // wait for itlb response in m_tlbResp state
   io.iTLBInter.resp.ready := (mmio_state === m_tlbResp) && f3_req_is_mmio
 
-  io.pmp.req.valid := (mmio_state === m_sendPMP) && f3_req_is_mmio
-  io.pmp.req.bits.addr  := mmio_resend_addr
-  io.pmp.req.bits.size  := 3.U
-  io.pmp.req.bits.cmd   := TlbCmd.exec
+  io.pmp.req.valid     := (mmio_state === m_sendPMP) && f3_req_is_mmio
+  io.pmp.req.bits.addr := mmio_resend_addr
+  io.pmp.req.bits.size := 3.U
+  io.pmp.req.bits.cmd  := TlbCmd.exec
 
-  val f3_lastHalf       = RegInit(0.U.asTypeOf(new LastHalfInfo))
+  val f3_lastHalf = RegInit(0.U.asTypeOf(new LastHalfInfo))
 
   val f3_predecode_range = VecInit(preDecoderOut.pd.map(inst => inst.valid)).asUInt
-  val f3_mmio_range      = VecInit((0 until PredictWidth).map(i => if(i ==0) true.B else false.B))
+  val f3_mmio_range      = VecInit((0 until PredictWidth).map(i => if (i == 0) true.B else false.B))
   val f3_instr_valid     = Wire(Vec(PredictWidth, Bool()))
 
   /*** prediction result check   ***/
-  checkerIn.ftqOffset   := f3_ftq_req.ftqOffset
-  checkerIn.jumpOffset  := f3_jump_offset
-  checkerIn.target      := f3_ftq_req.nextStartAddr
-  checkerIn.instrRange  := f3_instr_range.asTypeOf(Vec(PredictWidth, Bool()))
-  checkerIn.instrValid  := f3_instr_valid.asTypeOf(Vec(PredictWidth, Bool()))
-  checkerIn.pds         := f3_pd
-  checkerIn.pc          := f3_pc
-  checkerIn.fire_in     := RegNext(f2_fire, init = false.B)
+  checkerIn.ftqOffset  := f3_ftq_req.ftqOffset
+  checkerIn.jumpOffset := f3_jump_offset
+  checkerIn.target     := f3_ftq_req.nextStartAddr
+  checkerIn.instrRange := f3_instr_range.asTypeOf(Vec(PredictWidth, Bool()))
+  checkerIn.instrValid := f3_instr_valid.asTypeOf(Vec(PredictWidth, Bool()))
+  checkerIn.pds        := f3_pd
+  checkerIn.pc         := f3_pc
+  checkerIn.fire_in    := RegNext(f2_fire, init = false.B)
 
   /*** handle half RVI in the last 2 Bytes  ***/
 
-  def hasLastHalf(idx: UInt) = {
-    //!f3_pd(idx).isRVC && checkerOutStage1.fixedRange(idx) && f3_instr_valid(idx) && !checkerOutStage1.fixedTaken(idx) && !checkerOutStage2.fixedMissPred(idx) && ! f3_req_is_mmio
-    !f3_pd(idx).isRVC && checkerOutStage1.fixedRange(idx) && f3_instr_valid(idx) && !checkerOutStage1.fixedTaken(idx) && ! f3_req_is_mmio
-  }
+  def hasLastHalf(idx: UInt) =
+    // !f3_pd(idx).isRVC && checkerOutStage1.fixedRange(idx) && f3_instr_valid(idx) && !checkerOutStage1.fixedTaken(idx) && !checkerOutStage2.fixedMissPred(idx) && ! f3_req_is_mmio
+    !f3_pd(idx).isRVC && checkerOutStage1.fixedRange(idx) && f3_instr_valid(idx) && !checkerOutStage1.fixedTaken(
+      idx
+    ) && !f3_req_is_mmio
 
-  val f3_last_validIdx       = ParallelPosteriorityEncoder(checkerOutStage1.fixedRange)
+  val f3_last_validIdx = ParallelPosteriorityEncoder(checkerOutStage1.fixedRange)
 
-  val f3_hasLastHalf         = hasLastHalf((PredictWidth - 1).U)
-  val f3_false_lastHalf      = hasLastHalf(f3_last_validIdx)
-  val f3_false_snpc          = f3_half_snpc(f3_last_validIdx)
+  val f3_hasLastHalf    = hasLastHalf((PredictWidth - 1).U)
+  val f3_false_lastHalf = hasLastHalf(f3_last_validIdx)
+  val f3_false_snpc     = f3_half_snpc(f3_last_validIdx)
 
-  val f3_lastHalf_mask    = VecInit((0 until PredictWidth).map( i => if(i ==0) false.B else true.B )).asUInt
+  val f3_lastHalf_mask    = VecInit((0 until PredictWidth).map(i => if (i == 0) false.B else true.B)).asUInt
   val f3_lastHalf_disable = RegInit(false.B)
 
-  when(f3_flush || (f3_fire && f3_lastHalf_disable)){
+  when(f3_flush || (f3_fire && f3_lastHalf_disable)) {
     f3_lastHalf_disable := false.B
   }
 
-  when (f3_flush) {
+  when(f3_flush) {
     f3_lastHalf.valid := false.B
-  }.elsewhen (f3_fire) {
-    f3_lastHalf.valid := f3_hasLastHalf && !f3_lastHalf_disable
+  }.elsewhen(f3_fire) {
+    f3_lastHalf.valid    := f3_hasLastHalf && !f3_lastHalf_disable
     f3_lastHalf.middlePC := f3_ftq_req.nextStartAddr
   }
 
-  f3_instr_valid := Mux(f3_lastHalf.valid,f3_hasHalfValid ,VecInit(f3_pd.map(inst => inst.valid)))
+  f3_instr_valid := Mux(f3_lastHalf.valid, f3_hasHalfValid, VecInit(f3_pd.map(inst => inst.valid)))
 
   /*** frontend Trigger  ***/
   frontendTrigger.io.pds  := f3_pd
   frontendTrigger.io.pc   := f3_pc
-  frontendTrigger.io.data   := f3_cut_data
+  frontendTrigger.io.data := f3_cut_data
 
-  frontendTrigger.io.frontendTrigger  := io.frontendTrigger
+  frontendTrigger.io.frontendTrigger := io.frontendTrigger
 
-  val f3_triggered = frontendTrigger.io.triggered
+  val f3_triggered       = frontendTrigger.io.triggered
   val f3_toIbuffer_valid = f3_valid && (!f3_req_is_mmio || f3_mmio_can_go) && !f3_flush
 
   /*** send to Ibuffer  ***/
-  io.toIbuffer.valid            := f3_toIbuffer_valid
-  io.toIbuffer.bits.instrs      := f3_expd_instr
-  io.toIbuffer.bits.valid       := f3_instr_valid.asUInt
-  io.toIbuffer.bits.enqEnable   := checkerOutStage1.fixedRange.asUInt & f3_instr_valid.asUInt
-  io.toIbuffer.bits.pd          := f3_pd
-  io.toIbuffer.bits.ftqPtr      := f3_ftq_req.ftqIdx
-  io.toIbuffer.bits.pc          := f3_pc
+  io.toIbuffer.valid          := f3_toIbuffer_valid
+  io.toIbuffer.bits.instrs    := f3_expd_instr
+  io.toIbuffer.bits.valid     := f3_instr_valid.asUInt
+  io.toIbuffer.bits.enqEnable := checkerOutStage1.fixedRange.asUInt & f3_instr_valid.asUInt
+  io.toIbuffer.bits.pd        := f3_pd
+  io.toIbuffer.bits.ftqPtr    := f3_ftq_req.ftqIdx
+  io.toIbuffer.bits.pc        := f3_pc
   // Find last using PriorityMux
   io.toIbuffer.bits.isLastInFtqEntry := Reverse(PriorityEncoderOH(Reverse(io.toIbuffer.bits.enqEnable))).asBools
-  io.toIbuffer.bits.ftqOffset.zipWithIndex.map{case(a, i) => a.bits := i.U; a.valid := checkerOutStage1.fixedTaken(i) && !f3_req_is_mmio}
-  io.toIbuffer.bits.foldpc      := f3_foldpc
+  io.toIbuffer.bits.ftqOffset.zipWithIndex.map { case (a, i) =>
+    a.bits := i.U; a.valid := checkerOutStage1.fixedTaken(i) && !f3_req_is_mmio
+  }
+  io.toIbuffer.bits.foldpc        := f3_foldpc
   io.toIbuffer.bits.exceptionType := ExceptionType.merge(f3_exception_vec, f3_crossPage_exception_vec)
   // exceptionFromBackend only needs to be set for the first instruction.
   // Other instructions in the same block may have pf or af set,
@@ -840,30 +874,34 @@ class NewIFU(implicit p: Parameters) extends XSModule
     case _ => false.B
   }
   io.toIbuffer.bits.crossPageIPFFix := f3_crossPage_exception_vec.map(_ =/= ExceptionType.none)
-  io.toIbuffer.bits.illegalInstr:= f3_ill
-  io.toIbuffer.bits.triggered   := f3_triggered
+  io.toIbuffer.bits.illegalInstr    := f3_ill
+  io.toIbuffer.bits.triggered       := f3_triggered
 
-  when(f3_lastHalf.valid){
+  when(f3_lastHalf.valid) {
     io.toIbuffer.bits.enqEnable := checkerOutStage1.fixedRange.asUInt & f3_instr_valid.asUInt & f3_lastHalf_mask
     io.toIbuffer.bits.valid     := f3_lastHalf_mask & f3_instr_valid.asUInt
   }
 
   /** to backend */
   // f3_gpaddr is valid iff gpf is detected
-  io.toBackend.gpaddrMem_wen   := f3_toIbuffer_valid && Mux(
+  io.toBackend.gpaddrMem_wen := f3_toIbuffer_valid && Mux(
     f3_req_is_mmio,
     mmio_resend_exception === ExceptionType.gpf,
-    f3_exception.map(_ === ExceptionType.gpf).reduce(_||_)
+    f3_exception.map(_ === ExceptionType.gpf).reduce(_ || _)
+  )
+  io.toBackend.gpaddrMem_waddr        := f3_ftq_req.ftqIdx.value
+  io.toBackend.gpaddrMem_wdata.gpaddr := Mux(f3_req_is_mmio, mmio_resend_gpaddr, f3_gpaddr)
+  io.toBackend.gpaddrMem_wdata.isForVSnonLeafPTE := Mux(
+    f3_req_is_mmio,
+    mmio_resend_isForVSnonLeafPTE,
+    f3_isForVSnonLeafPTE
   )
-  io.toBackend.gpaddrMem_waddr := f3_ftq_req.ftqIdx.value
-  io.toBackend.gpaddrMem_wdata.gpaddr  := Mux(f3_req_is_mmio, mmio_resend_gpaddr, f3_gpaddr)
-  io.toBackend.gpaddrMem_wdata.isForVSnonLeafPTE := Mux(f3_req_is_mmio, mmio_resend_isForVSnonLeafPTE, f3_isForVSnonLeafPTE)
 
-  //Write back to Ftq
-  val f3_cache_fetch = f3_valid && !(f2_fire && !f2_flush)
+  // Write back to Ftq
+  val f3_cache_fetch     = f3_valid && !(f2_fire && !f2_flush)
   val finishFetchMaskReg = RegNext(f3_cache_fetch)
 
-  val mmioFlushWb = Wire(Valid(new PredecodeWritebackBundle))
+  val mmioFlushWb        = Wire(Valid(new PredecodeWritebackBundle))
   val f3_mmio_missOffset = Wire(ValidUndirectioned(UInt(log2Ceil(PredictWidth).W)))
   f3_mmio_missOffset.valid := f3_req_is_mmio
   f3_mmio_missOffset.bits  := 0.U
@@ -871,16 +909,16 @@ class NewIFU(implicit p: Parameters) extends XSModule
   // Send mmioFlushWb back to FTQ 1 cycle after uncache fetch return
   // When backend redirect, mmio_state reset after 1 cycle.
   // In this case, mask .valid to avoid overriding backend redirect
-  mmioFlushWb.valid           := (f3_req_is_mmio && mmio_state === m_waitCommit && RegNext(fromUncache.fire) &&
+  mmioFlushWb.valid := (f3_req_is_mmio && mmio_state === m_waitCommit && RegNext(fromUncache.fire) &&
     f3_mmio_use_seq_pc && !f3_ftq_flush_self && !f3_ftq_flush_by_older)
-  mmioFlushWb.bits.pc         := f3_pc
-  mmioFlushWb.bits.pd         := f3_pd
-  mmioFlushWb.bits.pd.zipWithIndex.map{case(instr,i) => instr.valid :=  f3_mmio_range(i)}
+  mmioFlushWb.bits.pc := f3_pc
+  mmioFlushWb.bits.pd := f3_pd
+  mmioFlushWb.bits.pd.zipWithIndex.map { case (instr, i) => instr.valid := f3_mmio_range(i) }
   mmioFlushWb.bits.ftqIdx     := f3_ftq_req.ftqIdx
   mmioFlushWb.bits.ftqOffset  := f3_ftq_req.ftqOffset.bits
   mmioFlushWb.bits.misOffset  := f3_mmio_missOffset
   mmioFlushWb.bits.cfiOffset  := DontCare
-  mmioFlushWb.bits.target     := Mux(mmio_is_RVC, f3_ftq_req.startAddr + 2.U , f3_ftq_req.startAddr + 4.U)
+  mmioFlushWb.bits.target     := Mux(mmio_is_RVC, f3_ftq_req.startAddr + 2.U, f3_ftq_req.startAddr + 4.U)
   mmioFlushWb.bits.jalTarget  := DontCare
   mmioFlushWb.bits.instrRange := f3_mmio_range
 
@@ -888,39 +926,38 @@ class NewIFU(implicit p: Parameters) extends XSModule
   mmioRVCExpander.io.in := Mux(f3_req_is_mmio, Cat(f3_mmio_data(1), f3_mmio_data(0)), 0.U)
 
   /** external predecode for MMIO instruction */
-  when(f3_req_is_mmio){
-    val inst  = Cat(f3_mmio_data(1), f3_mmio_data(0))
-    val currentIsRVC   = isRVC(inst)
+  when(f3_req_is_mmio) {
+    val inst         = Cat(f3_mmio_data(1), f3_mmio_data(0))
+    val currentIsRVC = isRVC(inst)
 
-    val brType::isCall::isRet::Nil = brInfo(inst)
-    val jalOffset = jal_offset(inst, currentIsRVC)
-    val brOffset  = br_offset(inst, currentIsRVC)
+    val brType :: isCall :: isRet :: Nil = brInfo(inst)
+    val jalOffset                        = jal_offset(inst, currentIsRVC)
+    val brOffset                         = br_offset(inst, currentIsRVC)
 
     io.toIbuffer.bits.instrs(0) := Mux(mmioRVCExpander.io.ill, mmioRVCExpander.io.in, mmioRVCExpander.io.out.bits)
 
-    io.toIbuffer.bits.pd(0).valid   := true.B
-    io.toIbuffer.bits.pd(0).isRVC   := currentIsRVC
-    io.toIbuffer.bits.pd(0).brType  := brType
-    io.toIbuffer.bits.pd(0).isCall  := isCall
-    io.toIbuffer.bits.pd(0).isRet   := isRet
+    io.toIbuffer.bits.pd(0).valid  := true.B
+    io.toIbuffer.bits.pd(0).isRVC  := currentIsRVC
+    io.toIbuffer.bits.pd(0).brType := brType
+    io.toIbuffer.bits.pd(0).isCall := isCall
+    io.toIbuffer.bits.pd(0).isRet  := isRet
 
     io.toIbuffer.bits.exceptionType(0)   := mmio_resend_exception
     io.toIbuffer.bits.crossPageIPFFix(0) := mmio_resend_exception =/= ExceptionType.none
-    io.toIbuffer.bits.illegalInstr(0)  := mmioRVCExpander.io.ill
+    io.toIbuffer.bits.illegalInstr(0)    := mmioRVCExpander.io.ill
 
-    io.toIbuffer.bits.enqEnable   := f3_mmio_range.asUInt
+    io.toIbuffer.bits.enqEnable := f3_mmio_range.asUInt
 
-    mmioFlushWb.bits.pd(0).valid   := true.B
-    mmioFlushWb.bits.pd(0).isRVC   := currentIsRVC
-    mmioFlushWb.bits.pd(0).brType  := brType
-    mmioFlushWb.bits.pd(0).isCall  := isCall
-    mmioFlushWb.bits.pd(0).isRet   := isRet
+    mmioFlushWb.bits.pd(0).valid  := true.B
+    mmioFlushWb.bits.pd(0).isRVC  := currentIsRVC
+    mmioFlushWb.bits.pd(0).brType := brType
+    mmioFlushWb.bits.pd(0).isCall := isCall
+    mmioFlushWb.bits.pd(0).isRet  := isRet
   }
 
-  mmio_redirect := (f3_req_is_mmio && mmio_state === m_waitCommit && RegNext(fromUncache.fire)  && f3_mmio_use_seq_pc)
-
-  XSPerfAccumulate("fetch_bubble_ibuffer_not_ready",   io.toIbuffer.valid && !io.toIbuffer.ready )
+  mmio_redirect := (f3_req_is_mmio && mmio_state === m_waitCommit && RegNext(fromUncache.fire) && f3_mmio_use_seq_pc)
 
+  XSPerfAccumulate("fetch_bubble_ibuffer_not_ready", io.toIbuffer.valid && !io.toIbuffer.ready)
 
   /**
     ******************************************************************************
@@ -930,34 +967,34 @@ class NewIFU(implicit p: Parameters) extends XSModule
     * - redirect if has false hit last half (last PC is not start + 32 Bytes, but in the midle of an notCFI RVI instruction)
     ******************************************************************************
     */
-  val wb_enable         = RegNext(f2_fire && !f2_flush) && !f3_req_is_mmio && !f3_flush
-  val wb_valid          = RegNext(wb_enable, init = false.B)
-  val wb_ftq_req        = RegEnable(f3_ftq_req, wb_enable)
+  val wb_enable  = RegNext(f2_fire && !f2_flush) && !f3_req_is_mmio && !f3_flush
+  val wb_valid   = RegNext(wb_enable, init = false.B)
+  val wb_ftq_req = RegEnable(f3_ftq_req, wb_enable)
 
-  val wb_check_result_stage1   = RegEnable(checkerOutStage1, wb_enable)
-  val wb_check_result_stage2   = checkerOutStage2
-  val wb_instr_range    = RegEnable(io.toIbuffer.bits.enqEnable, wb_enable)
+  val wb_check_result_stage1 = RegEnable(checkerOutStage1, wb_enable)
+  val wb_check_result_stage2 = checkerOutStage2
+  val wb_instr_range         = RegEnable(io.toIbuffer.bits.enqEnable, wb_enable)
 
-  val wb_pc_lower_result        = RegEnable(f3_pc_lower_result, wb_enable)
-  val wb_pc_high                = RegEnable(f3_pc_high, wb_enable)
-  val wb_pc_high_plus1          = RegEnable(f3_pc_high_plus1, wb_enable)
-  val wb_pc                     = CatPC(wb_pc_lower_result, wb_pc_high, wb_pc_high_plus1)
+  val wb_pc_lower_result = RegEnable(f3_pc_lower_result, wb_enable)
+  val wb_pc_high         = RegEnable(f3_pc_high, wb_enable)
+  val wb_pc_high_plus1   = RegEnable(f3_pc_high_plus1, wb_enable)
+  val wb_pc              = CatPC(wb_pc_lower_result, wb_pc_high, wb_pc_high_plus1)
 
-  //val wb_pc             = RegEnable(f3_pc, wb_enable)
-  val wb_pd             = RegEnable(f3_pd, wb_enable)
-  val wb_instr_valid    = RegEnable(f3_instr_valid, wb_enable)
+  // val wb_pc             = RegEnable(f3_pc, wb_enable)
+  val wb_pd          = RegEnable(f3_pd, wb_enable)
+  val wb_instr_valid = RegEnable(f3_instr_valid, wb_enable)
 
   /* false hit lastHalf */
   val wb_lastIdx        = RegEnable(f3_last_validIdx, wb_enable)
   val wb_false_lastHalf = RegEnable(f3_false_lastHalf, wb_enable) && wb_lastIdx =/= (PredictWidth - 1).U
   val wb_false_target   = RegEnable(f3_false_snpc, wb_enable)
 
-  val wb_half_flush = wb_false_lastHalf
+  val wb_half_flush  = wb_false_lastHalf
   val wb_half_target = wb_false_target
 
   /* false oversize */
-  val lastIsRVC = wb_instr_range.asTypeOf(Vec(PredictWidth,Bool())).last  && wb_pd.last.isRVC
-  val lastIsRVI = wb_instr_range.asTypeOf(Vec(PredictWidth,Bool()))(PredictWidth - 2) && !wb_pd(PredictWidth - 2).isRVC
+  val lastIsRVC = wb_instr_range.asTypeOf(Vec(PredictWidth, Bool())).last && wb_pd.last.isRVC
+  val lastIsRVI = wb_instr_range.asTypeOf(Vec(PredictWidth, Bool()))(PredictWidth - 2) && !wb_pd(PredictWidth - 2).isRVC
   val lastTaken = wb_check_result_stage1.fixedTaken.last
 
   f3_wb_not_flush := wb_ftq_req.ftqIdx === f3_ftq_req.ftqIdx && f3_valid && wb_valid
@@ -965,124 +1002,145 @@ class NewIFU(implicit p: Parameters) extends XSModule
   /** if a req with a last half but miss predicted enters in wb stage, and this cycle f3 stalls,
     * we set a flag to notify f3 that the last half flag need not to be set.
     */
-  //f3_fire is after wb_valid
-  when(wb_valid && RegNext(f3_hasLastHalf,init = false.B)
-        && wb_check_result_stage2.fixedMissPred(PredictWidth - 1) && !f3_fire  && !RegNext(f3_fire,init = false.B) && !f3_flush
-      ){
+  // f3_fire is after wb_valid
+  when(wb_valid && RegNext(f3_hasLastHalf, init = false.B)
+    && wb_check_result_stage2.fixedMissPred(PredictWidth - 1) && !f3_fire && !RegNext(
+      f3_fire,
+      init = false.B
+    ) && !f3_flush) {
     f3_lastHalf_disable := true.B
   }
 
-  //wb_valid and f3_fire are in same cycle
-  when(wb_valid && RegNext(f3_hasLastHalf,init = false.B)
-        && wb_check_result_stage2.fixedMissPred(PredictWidth - 1) && f3_fire
-      ){
+  // wb_valid and f3_fire are in same cycle
+  when(wb_valid && RegNext(f3_hasLastHalf, init = false.B)
+    && wb_check_result_stage2.fixedMissPred(PredictWidth - 1) && f3_fire) {
     f3_lastHalf.valid := false.B
   }
 
   val checkFlushWb = Wire(Valid(new PredecodeWritebackBundle))
-  val checkFlushWbjalTargetIdx = ParallelPriorityEncoder(VecInit(wb_pd.zip(wb_instr_valid).map{case (pd, v) => v && pd.isJal }))
+  val checkFlushWbjalTargetIdx = ParallelPriorityEncoder(VecInit(wb_pd.zip(wb_instr_valid).map { case (pd, v) =>
+    v && pd.isJal
+  }))
   val checkFlushWbTargetIdx = ParallelPriorityEncoder(wb_check_result_stage2.fixedMissPred)
-  checkFlushWb.valid                  := wb_valid
-  checkFlushWb.bits.pc                := wb_pc
-  checkFlushWb.bits.pd                := wb_pd
-  checkFlushWb.bits.pd.zipWithIndex.map{case(instr,i) => instr.valid := wb_instr_valid(i)}
-  checkFlushWb.bits.ftqIdx            := wb_ftq_req.ftqIdx
-  checkFlushWb.bits.ftqOffset         := wb_ftq_req.ftqOffset.bits
-  checkFlushWb.bits.misOffset.valid   := ParallelOR(wb_check_result_stage2.fixedMissPred) || wb_half_flush
-  checkFlushWb.bits.misOffset.bits    := Mux(wb_half_flush, wb_lastIdx, ParallelPriorityEncoder(wb_check_result_stage2.fixedMissPred))
-  checkFlushWb.bits.cfiOffset.valid   := ParallelOR(wb_check_result_stage1.fixedTaken)
-  checkFlushWb.bits.cfiOffset.bits    := ParallelPriorityEncoder(wb_check_result_stage1.fixedTaken)
-  checkFlushWb.bits.target            := Mux(wb_half_flush, wb_half_target, wb_check_result_stage2.fixedTarget(checkFlushWbTargetIdx))
-  checkFlushWb.bits.jalTarget         := wb_check_result_stage2.jalTarget(checkFlushWbjalTargetIdx)
-  checkFlushWb.bits.instrRange        := wb_instr_range.asTypeOf(Vec(PredictWidth, Bool()))
-
-  toFtq.pdWb := Mux(wb_valid, checkFlushWb,  mmioFlushWb)
+  checkFlushWb.valid   := wb_valid
+  checkFlushWb.bits.pc := wb_pc
+  checkFlushWb.bits.pd := wb_pd
+  checkFlushWb.bits.pd.zipWithIndex.map { case (instr, i) => instr.valid := wb_instr_valid(i) }
+  checkFlushWb.bits.ftqIdx          := wb_ftq_req.ftqIdx
+  checkFlushWb.bits.ftqOffset       := wb_ftq_req.ftqOffset.bits
+  checkFlushWb.bits.misOffset.valid := ParallelOR(wb_check_result_stage2.fixedMissPred) || wb_half_flush
+  checkFlushWb.bits.misOffset.bits := Mux(
+    wb_half_flush,
+    wb_lastIdx,
+    ParallelPriorityEncoder(wb_check_result_stage2.fixedMissPred)
+  )
+  checkFlushWb.bits.cfiOffset.valid := ParallelOR(wb_check_result_stage1.fixedTaken)
+  checkFlushWb.bits.cfiOffset.bits  := ParallelPriorityEncoder(wb_check_result_stage1.fixedTaken)
+  checkFlushWb.bits.target := Mux(
+    wb_half_flush,
+    wb_half_target,
+    wb_check_result_stage2.fixedTarget(checkFlushWbTargetIdx)
+  )
+  checkFlushWb.bits.jalTarget  := wb_check_result_stage2.jalTarget(checkFlushWbjalTargetIdx)
+  checkFlushWb.bits.instrRange := wb_instr_range.asTypeOf(Vec(PredictWidth, Bool()))
+
+  toFtq.pdWb := Mux(wb_valid, checkFlushWb, mmioFlushWb)
 
   wb_redirect := checkFlushWb.bits.misOffset.valid && wb_valid
 
   /*write back flush type*/
-  val checkFaultType = wb_check_result_stage2.faultType
-  val checkJalFault =  wb_valid && checkFaultType.map(_.isjalFault).reduce(_||_)
-  val checkRetFault =  wb_valid && checkFaultType.map(_.isRetFault).reduce(_||_)
-  val checkTargetFault =  wb_valid && checkFaultType.map(_.istargetFault).reduce(_||_)
-  val checkNotCFIFault =  wb_valid && checkFaultType.map(_.notCFIFault).reduce(_||_)
-  val checkInvalidTaken =  wb_valid && checkFaultType.map(_.invalidTakenFault).reduce(_||_)
-
-
-  XSPerfAccumulate("predecode_flush_jalFault",   checkJalFault )
-  XSPerfAccumulate("predecode_flush_retFault",   checkRetFault )
-  XSPerfAccumulate("predecode_flush_targetFault",   checkTargetFault )
-  XSPerfAccumulate("predecode_flush_notCFIFault",   checkNotCFIFault )
-  XSPerfAccumulate("predecode_flush_incalidTakenFault",   checkInvalidTaken )
-
-  when(checkRetFault){
-    XSDebug("startAddr:%x  nextstartAddr:%x  taken:%d    takenIdx:%d\n",
-        wb_ftq_req.startAddr, wb_ftq_req.nextStartAddr, wb_ftq_req.ftqOffset.valid, wb_ftq_req.ftqOffset.bits)
+  val checkFaultType    = wb_check_result_stage2.faultType
+  val checkJalFault     = wb_valid && checkFaultType.map(_.isjalFault).reduce(_ || _)
+  val checkRetFault     = wb_valid && checkFaultType.map(_.isRetFault).reduce(_ || _)
+  val checkTargetFault  = wb_valid && checkFaultType.map(_.istargetFault).reduce(_ || _)
+  val checkNotCFIFault  = wb_valid && checkFaultType.map(_.notCFIFault).reduce(_ || _)
+  val checkInvalidTaken = wb_valid && checkFaultType.map(_.invalidTakenFault).reduce(_ || _)
+
+  XSPerfAccumulate("predecode_flush_jalFault", checkJalFault)
+  XSPerfAccumulate("predecode_flush_retFault", checkRetFault)
+  XSPerfAccumulate("predecode_flush_targetFault", checkTargetFault)
+  XSPerfAccumulate("predecode_flush_notCFIFault", checkNotCFIFault)
+  XSPerfAccumulate("predecode_flush_incalidTakenFault", checkInvalidTaken)
+
+  when(checkRetFault) {
+    XSDebug(
+      "startAddr:%x  nextstartAddr:%x  taken:%d    takenIdx:%d\n",
+      wb_ftq_req.startAddr,
+      wb_ftq_req.nextStartAddr,
+      wb_ftq_req.ftqOffset.valid,
+      wb_ftq_req.ftqOffset.bits
+    )
   }
 
-
   /** performance counter */
-  val f3_perf_info     = RegEnable(f2_perf_info,  f2_fire)
-  val f3_req_0    = io.toIbuffer.fire
-  val f3_req_1    = io.toIbuffer.fire && f3_doubleLine
-  val f3_hit_0    = io.toIbuffer.fire && f3_perf_info.bank_hit(0)
-  val f3_hit_1    = io.toIbuffer.fire && f3_doubleLine & f3_perf_info.bank_hit(1)
-  val f3_hit      = f3_perf_info.hit
+  val f3_perf_info = RegEnable(f2_perf_info, f2_fire)
+  val f3_req_0     = io.toIbuffer.fire
+  val f3_req_1     = io.toIbuffer.fire && f3_doubleLine
+  val f3_hit_0     = io.toIbuffer.fire && f3_perf_info.bank_hit(0)
+  val f3_hit_1     = io.toIbuffer.fire && f3_doubleLine & f3_perf_info.bank_hit(1)
+  val f3_hit       = f3_perf_info.hit
   val perfEvents = Seq(
-    ("frontendFlush                ", wb_redirect                                ),
-    ("ifu_req                      ", io.toIbuffer.fire                        ),
-    ("ifu_miss                     ", io.toIbuffer.fire && !f3_perf_info.hit   ),
-    ("ifu_req_cacheline_0          ", f3_req_0                                   ),
-    ("ifu_req_cacheline_1          ", f3_req_1                                   ),
-    ("ifu_req_cacheline_0_hit      ", f3_hit_1                                   ),
-    ("ifu_req_cacheline_1_hit      ", f3_hit_1                                   ),
-    ("only_0_hit                   ", f3_perf_info.only_0_hit       && io.toIbuffer.fire ),
-    ("only_0_miss                  ", f3_perf_info.only_0_miss      && io.toIbuffer.fire ),
-    ("hit_0_hit_1                  ", f3_perf_info.hit_0_hit_1      && io.toIbuffer.fire ),
-    ("hit_0_miss_1                 ", f3_perf_info.hit_0_miss_1     && io.toIbuffer.fire ),
-    ("miss_0_hit_1                 ", f3_perf_info.miss_0_hit_1     && io.toIbuffer.fire ),
-    ("miss_0_miss_1                ", f3_perf_info.miss_0_miss_1    && io.toIbuffer.fire ),
+    ("frontendFlush                ", wb_redirect),
+    ("ifu_req                      ", io.toIbuffer.fire),
+    ("ifu_miss                     ", io.toIbuffer.fire && !f3_perf_info.hit),
+    ("ifu_req_cacheline_0          ", f3_req_0),
+    ("ifu_req_cacheline_1          ", f3_req_1),
+    ("ifu_req_cacheline_0_hit      ", f3_hit_1),
+    ("ifu_req_cacheline_1_hit      ", f3_hit_1),
+    ("only_0_hit                   ", f3_perf_info.only_0_hit && io.toIbuffer.fire),
+    ("only_0_miss                  ", f3_perf_info.only_0_miss && io.toIbuffer.fire),
+    ("hit_0_hit_1                  ", f3_perf_info.hit_0_hit_1 && io.toIbuffer.fire),
+    ("hit_0_miss_1                 ", f3_perf_info.hit_0_miss_1 && io.toIbuffer.fire),
+    ("miss_0_hit_1                 ", f3_perf_info.miss_0_hit_1 && io.toIbuffer.fire),
+    ("miss_0_miss_1                ", f3_perf_info.miss_0_miss_1 && io.toIbuffer.fire)
   )
   generatePerfEvent()
 
-  XSPerfAccumulate("ifu_req",   io.toIbuffer.fire )
-  XSPerfAccumulate("ifu_miss",  io.toIbuffer.fire && !f3_hit )
-  XSPerfAccumulate("ifu_req_cacheline_0", f3_req_0  )
-  XSPerfAccumulate("ifu_req_cacheline_1", f3_req_1  )
-  XSPerfAccumulate("ifu_req_cacheline_0_hit",   f3_hit_0 )
-  XSPerfAccumulate("ifu_req_cacheline_1_hit",   f3_hit_1 )
-  XSPerfAccumulate("frontendFlush",  wb_redirect )
-  XSPerfAccumulate("only_0_hit",      f3_perf_info.only_0_hit   && io.toIbuffer.fire  )
-  XSPerfAccumulate("only_0_miss",     f3_perf_info.only_0_miss  && io.toIbuffer.fire  )
-  XSPerfAccumulate("hit_0_hit_1",     f3_perf_info.hit_0_hit_1  && io.toIbuffer.fire  )
-  XSPerfAccumulate("hit_0_miss_1",    f3_perf_info.hit_0_miss_1  && io.toIbuffer.fire  )
-  XSPerfAccumulate("miss_0_hit_1",    f3_perf_info.miss_0_hit_1   && io.toIbuffer.fire )
-  XSPerfAccumulate("miss_0_miss_1",   f3_perf_info.miss_0_miss_1 && io.toIbuffer.fire )
-  XSPerfAccumulate("hit_0_except_1",   f3_perf_info.hit_0_except_1 && io.toIbuffer.fire )
-  XSPerfAccumulate("miss_0_except_1",   f3_perf_info.miss_0_except_1 && io.toIbuffer.fire )
-  XSPerfAccumulate("except_0",   f3_perf_info.except_0 && io.toIbuffer.fire )
-  XSPerfHistogram("ifu2ibuffer_validCnt", PopCount(io.toIbuffer.bits.valid & io.toIbuffer.bits.enqEnable), io.toIbuffer.fire, 0, PredictWidth + 1, 1)
-
-  val hartId = p(XSCoreParamsKey).HartId
+  XSPerfAccumulate("ifu_req", io.toIbuffer.fire)
+  XSPerfAccumulate("ifu_miss", io.toIbuffer.fire && !f3_hit)
+  XSPerfAccumulate("ifu_req_cacheline_0", f3_req_0)
+  XSPerfAccumulate("ifu_req_cacheline_1", f3_req_1)
+  XSPerfAccumulate("ifu_req_cacheline_0_hit", f3_hit_0)
+  XSPerfAccumulate("ifu_req_cacheline_1_hit", f3_hit_1)
+  XSPerfAccumulate("frontendFlush", wb_redirect)
+  XSPerfAccumulate("only_0_hit", f3_perf_info.only_0_hit && io.toIbuffer.fire)
+  XSPerfAccumulate("only_0_miss", f3_perf_info.only_0_miss && io.toIbuffer.fire)
+  XSPerfAccumulate("hit_0_hit_1", f3_perf_info.hit_0_hit_1 && io.toIbuffer.fire)
+  XSPerfAccumulate("hit_0_miss_1", f3_perf_info.hit_0_miss_1 && io.toIbuffer.fire)
+  XSPerfAccumulate("miss_0_hit_1", f3_perf_info.miss_0_hit_1 && io.toIbuffer.fire)
+  XSPerfAccumulate("miss_0_miss_1", f3_perf_info.miss_0_miss_1 && io.toIbuffer.fire)
+  XSPerfAccumulate("hit_0_except_1", f3_perf_info.hit_0_except_1 && io.toIbuffer.fire)
+  XSPerfAccumulate("miss_0_except_1", f3_perf_info.miss_0_except_1 && io.toIbuffer.fire)
+  XSPerfAccumulate("except_0", f3_perf_info.except_0 && io.toIbuffer.fire)
+  XSPerfHistogram(
+    "ifu2ibuffer_validCnt",
+    PopCount(io.toIbuffer.bits.valid & io.toIbuffer.bits.enqEnable),
+    io.toIbuffer.fire,
+    0,
+    PredictWidth + 1,
+    1
+  )
+
+  val hartId                     = p(XSCoreParamsKey).HartId
   val isWriteFetchToIBufferTable = Constantin.createRecord(s"isWriteFetchToIBufferTable$hartId")
-  val isWriteIfuWbToFtqTable = Constantin.createRecord(s"isWriteIfuWbToFtqTable$hartId")
-  val fetchToIBufferTable = ChiselDB.createTable(s"FetchToIBuffer$hartId", new FetchToIBufferDB)
-  val ifuWbToFtqTable = ChiselDB.createTable(s"IfuWbToFtq$hartId", new IfuWbToFtqDB)
+  val isWriteIfuWbToFtqTable     = Constantin.createRecord(s"isWriteIfuWbToFtqTable$hartId")
+  val fetchToIBufferTable        = ChiselDB.createTable(s"FetchToIBuffer$hartId", new FetchToIBufferDB)
+  val ifuWbToFtqTable            = ChiselDB.createTable(s"IfuWbToFtq$hartId", new IfuWbToFtqDB)
 
   val fetchIBufferDumpData = Wire(new FetchToIBufferDB)
-  fetchIBufferDumpData.start_addr := f3_ftq_req.startAddr
+  fetchIBufferDumpData.start_addr  := f3_ftq_req.startAddr
   fetchIBufferDumpData.instr_count := PopCount(io.toIbuffer.bits.enqEnable)
   fetchIBufferDumpData.exception := (f3_perf_info.except_0 && io.toIbuffer.fire) || (f3_perf_info.hit_0_except_1 && io.toIbuffer.fire) || (f3_perf_info.miss_0_except_1 && io.toIbuffer.fire)
   fetchIBufferDumpData.is_cache_hit := f3_hit
 
   val ifuWbToFtqDumpData = Wire(new IfuWbToFtqDB)
-  ifuWbToFtqDumpData.start_addr := wb_ftq_req.startAddr
-  ifuWbToFtqDumpData.is_miss_pred := checkFlushWb.bits.misOffset.valid
-  ifuWbToFtqDumpData.miss_pred_offset := checkFlushWb.bits.misOffset.bits
-  ifuWbToFtqDumpData.checkJalFault := checkJalFault
-  ifuWbToFtqDumpData.checkRetFault := checkRetFault
-  ifuWbToFtqDumpData.checkTargetFault := checkTargetFault
-  ifuWbToFtqDumpData.checkNotCFIFault := checkNotCFIFault
+  ifuWbToFtqDumpData.start_addr        := wb_ftq_req.startAddr
+  ifuWbToFtqDumpData.is_miss_pred      := checkFlushWb.bits.misOffset.valid
+  ifuWbToFtqDumpData.miss_pred_offset  := checkFlushWb.bits.misOffset.bits
+  ifuWbToFtqDumpData.checkJalFault     := checkJalFault
+  ifuWbToFtqDumpData.checkRetFault     := checkRetFault
+  ifuWbToFtqDumpData.checkTargetFault  := checkTargetFault
+  ifuWbToFtqDumpData.checkNotCFIFault  := checkNotCFIFault
   ifuWbToFtqDumpData.checkInvalidTaken := checkInvalidTaken
 
   fetchToIBufferTable.log(
diff --git a/src/main/scala/xiangshan/frontend/ITTAGE.scala b/src/main/scala/xiangshan/frontend/ITTAGE.scala
index 8852f629e7e..611c1da29c7 100644
--- a/src/main/scala/xiangshan/frontend/ITTAGE.scala
+++ b/src/main/scala/xiangshan/frontend/ITTAGE.scala
@@ -16,138 +16,128 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
+import org.chipsalliance.cde.config.Parameters
+import scala.{Tuple2 => &}
 import scala.math.min
 import scala.util.matching.Regex
-import scala.{Tuple2 => &}
+import utility._
+import utils._
+import xiangshan._
 
 trait ITTageParams extends HasXSParameter with HasBPUParameter {
 
   val ITTageNTables = ITTageTableInfos.size // Number of tage tables
-  val UBitPeriod = 2048
+  val UBitPeriod    = 2048
   val ITTageCtrBits = 2
-  val uFoldedWidth = 16
-  val TickWidth = 8
-  val ITTageUsBits = 1
-  def ctr_null(ctr: UInt, ctrBits: Int = ITTageCtrBits) = {
+  val uFoldedWidth  = 16
+  val TickWidth     = 8
+  val ITTageUsBits  = 1
+  def ctr_null(ctr: UInt, ctrBits: Int = ITTageCtrBits) =
     ctr === 0.U
-  }
-  def ctr_unconf(ctr: UInt, ctrBits: Int = ITTageCtrBits) = {
-    ctr < (1 << (ctrBits-1)).U
-  }
+  def ctr_unconf(ctr: UInt, ctrBits: Int = ITTageCtrBits) =
+    ctr < (1 << (ctrBits - 1)).U
   val UAONA_bits = 4
 
   val TotalBits = ITTageTableInfos.map {
     case (s, h, t) => {
-      s * (1+t+ITTageCtrBits+ITTageUsBits+VAddrBits)
+      s * (1 + t + ITTageCtrBits + ITTageUsBits + VAddrBits)
     }
-  }.reduce(_+_)
+  }.reduce(_ + _)
 }
 // reuse TAGE implementation
 
 trait ITTageHasFoldedHistory {
   val histLen: Int
-  def compute_folded_hist(hist: UInt, l: Int) = {
+  def compute_folded_hist(hist: UInt, l: Int) =
     if (histLen > 0) {
-      val nChunks = (histLen + l - 1) / l
-      val hist_chunks = (0 until nChunks) map {i =>
-        hist(min((i+1)*l, histLen)-1, i*l)
-      }
+      val nChunks     = (histLen + l - 1) / l
+      val hist_chunks = (0 until nChunks) map { i => hist(min((i + 1) * l, histLen) - 1, i * l) }
       ParallelXOR(hist_chunks)
-    }
-    else 0.U
-  }
+    } else 0.U
 }
 
-
-
 abstract class ITTageBundle(implicit p: Parameters)
-  extends XSBundle with ITTageParams with BPUUtils
+    extends XSBundle with ITTageParams with BPUUtils
 
 abstract class ITTageModule(implicit p: Parameters)
-  extends XSModule with ITTageParams with BPUUtils
-{}
-
+    extends XSModule with ITTageParams with BPUUtils {}
 
 class ITTageReq(implicit p: Parameters) extends ITTageBundle {
-  val pc = UInt(VAddrBits.W)
+  val pc          = UInt(VAddrBits.W)
   val folded_hist = new AllFoldedHistories(foldedGHistInfos)
 }
 
 class ITTageResp(implicit p: Parameters) extends ITTageBundle {
-  val ctr = UInt(ITTageCtrBits.W)
-  val u = UInt(2.W)
+  val ctr    = UInt(ITTageCtrBits.W)
+  val u      = UInt(2.W)
   val target = UInt(VAddrBits.W)
 }
 
 class ITTageUpdate(implicit p: Parameters) extends ITTageBundle {
-  val pc = UInt(VAddrBits.W)
+  val pc    = UInt(VAddrBits.W)
   val ghist = UInt(HistoryLength.W)
   // update tag and ctr
-  val valid = Bool()
+  val valid   = Bool()
   val correct = Bool()
-  val alloc = Bool()
-  val oldCtr = UInt(ITTageCtrBits.W)
+  val alloc   = Bool()
+  val oldCtr  = UInt(ITTageCtrBits.W)
   // update u
-  val uValid = Bool()
-  val u = Bool()
+  val uValid  = Bool()
+  val u       = Bool()
   val reset_u = Bool()
   // target
-  val target = UInt(VAddrBits.W)
+  val target     = UInt(VAddrBits.W)
   val old_target = UInt(VAddrBits.W)
 }
 
 // reuse TAGE Implementation
 
-class ITTageMeta(implicit p: Parameters) extends XSBundle with ITTageParams{
-  val provider = ValidUndirectioned(UInt(log2Ceil(ITTageNTables).W))
-  val altProvider = ValidUndirectioned(UInt(log2Ceil(ITTageNTables).W))
-  val altDiffers = Bool()
-  val providerU = Bool()
-  val providerCtr = UInt(ITTageCtrBits.W)
-  val altProviderCtr = UInt(ITTageCtrBits.W)
-  val allocate = ValidUndirectioned(UInt(log2Ceil(ITTageNTables).W))
-  val providerTarget = UInt(VAddrBits.W)
+class ITTageMeta(implicit p: Parameters) extends XSBundle with ITTageParams {
+  val provider          = ValidUndirectioned(UInt(log2Ceil(ITTageNTables).W))
+  val altProvider       = ValidUndirectioned(UInt(log2Ceil(ITTageNTables).W))
+  val altDiffers        = Bool()
+  val providerU         = Bool()
+  val providerCtr       = UInt(ITTageCtrBits.W)
+  val altProviderCtr    = UInt(ITTageCtrBits.W)
+  val allocate          = ValidUndirectioned(UInt(log2Ceil(ITTageNTables).W))
+  val providerTarget    = UInt(VAddrBits.W)
   val altProviderTarget = UInt(VAddrBits.W)
   // val scMeta = new SCMeta(EnableSC)
   // TODO: check if we need target info here
   val pred_cycle = if (!env.FPGAPlatform) Some(UInt(64.W)) else None
 
-  override def toPrintable = {
+  override def toPrintable =
     p"pvdr(v:${provider.valid} num:${provider.bits} ctr:$providerCtr u:$providerU tar:${Hexadecimal(providerTarget)}), " +
-    p"altpvdr(v:${altProvider.valid} num:${altProvider.bits}, ctr:$altProviderCtr, tar:${Hexadecimal(altProviderTarget)})"
-  }
+      p"altpvdr(v:${altProvider.valid} num:${altProvider.bits}, ctr:$altProviderCtr, tar:${Hexadecimal(altProviderTarget)})"
 }
 
-
 class FakeITTageTable()(implicit p: Parameters) extends ITTageModule {
   val io = IO(new Bundle() {
-    val req = Input(Valid(new ITTageReq))
-    val resp = Output(Valid(new ITTageResp))
+    val req    = Input(Valid(new ITTageReq))
+    val resp   = Output(Valid(new ITTageResp))
     val update = Input(new ITTageUpdate)
   })
   io.resp := DontCare
 
 }
 
-class ITTageTable
-(
-  val nRows: Int, val histLen: Int, val tagLen: Int, val uBitPeriod: Int, val tableIdx: Int
+class ITTageTable(
+    val nRows:      Int,
+    val histLen:    Int,
+    val tagLen:     Int,
+    val uBitPeriod: Int,
+    val tableIdx:   Int
 )(implicit p: Parameters)
-  extends ITTageModule with HasFoldedHistory {
+    extends ITTageModule with HasFoldedHistory {
   val io = IO(new Bundle() {
-    val req = Flipped(DecoupledIO(new ITTageReq))
-    val resp = Output(Valid(new ITTageResp))
+    val req    = Flipped(DecoupledIO(new ITTageReq))
+    val resp   = Output(Valid(new ITTageResp))
     val update = Input(new ITTageUpdate)
   })
 
-  val SRAM_SIZE=128
+  val SRAM_SIZE = 128
 
   val foldedWidth = if (nRows >= SRAM_SIZE) nRows / SRAM_SIZE else 1
 
@@ -160,35 +150,33 @@ class ITTageTable
   val wrBypassEntries = 4
 
   require(histLen == 0 && tagLen == 0 || histLen != 0 && tagLen != 0)
-  val idxFhInfo = (histLen, min(log2Ceil(nRows), histLen))
-  val tagFhInfo = (histLen, min(histLen, tagLen))
-  val altTagFhInfo = (histLen, min(histLen, tagLen-1))
-  val allFhInfos = Seq(idxFhInfo, tagFhInfo, altTagFhInfo)
+  val idxFhInfo    = (histLen, min(log2Ceil(nRows), histLen))
+  val tagFhInfo    = (histLen, min(histLen, tagLen))
+  val altTagFhInfo = (histLen, min(histLen, tagLen - 1))
+  val allFhInfos   = Seq(idxFhInfo, tagFhInfo, altTagFhInfo)
 
-  def getFoldedHistoryInfo = allFhInfos.filter(_._1 >0).toSet
+  def getFoldedHistoryInfo = allFhInfos.filter(_._1 > 0).toSet
 
-  def compute_tag_and_hash(unhashed_idx: UInt, allFh: AllFoldedHistories) = {
+  def compute_tag_and_hash(unhashed_idx: UInt, allFh: AllFoldedHistories) =
     if (histLen > 0) {
-      val idx_fh = allFh.getHistWithInfo(idxFhInfo).folded_hist
-      val tag_fh = allFh.getHistWithInfo(tagFhInfo).folded_hist
+      val idx_fh     = allFh.getHistWithInfo(idxFhInfo).folded_hist
+      val tag_fh     = allFh.getHistWithInfo(tagFhInfo).folded_hist
       val alt_tag_fh = allFh.getHistWithInfo(altTagFhInfo).folded_hist
       // require(idx_fh.getWidth == log2Ceil(nRows))
-      val idx = (unhashed_idx ^ idx_fh)(log2Ceil(nRows)-1, 0)
-      val tag = ((unhashed_idx >> log2Ceil(nRows)) ^ tag_fh ^ (alt_tag_fh << 1)) (tagLen - 1, 0)
+      val idx = (unhashed_idx ^ idx_fh)(log2Ceil(nRows) - 1, 0)
+      val tag = ((unhashed_idx >> log2Ceil(nRows)) ^ tag_fh ^ (alt_tag_fh << 1))(tagLen - 1, 0)
       (idx, tag)
-    }
-    else {
+    } else {
       require(tagLen == 0)
-      (unhashed_idx(log2Ceil(nRows)-1, 0), 0.U)
+      (unhashed_idx(log2Ceil(nRows) - 1, 0), 0.U)
     }
-  }
 
   def inc_ctr(ctr: UInt, taken: Bool): UInt = satUpdate(ctr, ITTageCtrBits, taken)
 
   class ITTageEntry() extends ITTageBundle {
-    val valid = Bool()
-    val tag = UInt(tagLen.W)
-    val ctr = UInt(ITTageCtrBits.W)
+    val valid  = Bool()
+    val tag    = UInt(tagLen.W)
+    val ctr    = UInt(ITTageCtrBits.W)
     val target = UInt(VAddrBits.W)
     val useful = Bool()
   }
@@ -200,67 +188,74 @@ class ITTageTable
   // def getUnhashedIdx(pc: UInt) = pc >> (instOffsetBits+log2Ceil(TageBanks))
   def getUnhashedIdx(pc: UInt): UInt = pc >> instOffsetBits
 
-  val s0_valid = io.req.valid
-  val s0_pc = io.req.bits.pc
+  val s0_valid        = io.req.valid
+  val s0_pc           = io.req.bits.pc
   val s0_unhashed_idx = getUnhashedIdx(io.req.bits.pc)
 
   val (s0_idx, s0_tag) = compute_tag_and_hash(s0_unhashed_idx, io.req.bits.folded_hist)
   val (s1_idx, s1_tag) = (RegEnable(s0_idx, io.req.fire), RegEnable(s0_tag, io.req.fire))
-  val s1_valid = RegNext(s0_valid)
+  val s1_valid         = RegNext(s0_valid)
 
   val table = Module(new FoldedSRAMTemplate(
-    new ITTageEntry, set=nRows, width=foldedWidth, shouldReset=true, holdRead=true, singlePort=true, useBitmask=true))
+    new ITTageEntry,
+    set = nRows,
+    width = foldedWidth,
+    shouldReset = true,
+    holdRead = true,
+    singlePort = true,
+    useBitmask = true
+  ))
 
-  table.io.r.req.valid := io.req.fire
+  table.io.r.req.valid       := io.req.fire
   table.io.r.req.bits.setIdx := s0_idx
 
   val table_read_data = table.io.r.resp.data(0)
 
   val s1_req_rhit = table_read_data.valid && table_read_data.tag === s1_tag
 
-  val read_write_conflict = io.update.valid && io.req.valid
+  val read_write_conflict    = io.update.valid && io.req.valid
   val s1_read_write_conflict = RegEnable(read_write_conflict, io.req.valid)
 
-  io.resp.valid := (if (tagLen != 0) s1_req_rhit && !s1_read_write_conflict else true.B) && s1_valid // && s1_mask(b)
+  io.resp.valid    := (if (tagLen != 0) s1_req_rhit && !s1_read_write_conflict else true.B) && s1_valid // && s1_mask(b)
   io.resp.bits.ctr := table_read_data.ctr
-  io.resp.bits.u := table_read_data.useful
+  io.resp.bits.u   := table_read_data.useful
   io.resp.bits.target := table_read_data.target
 
   // Use fetchpc to compute hash
   val update_folded_hist = WireInit(0.U.asTypeOf(new AllFoldedHistories(foldedGHistInfos)))
 
-  update_folded_hist.getHistWithInfo(idxFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, log2Ceil(nRows))
-  update_folded_hist.getHistWithInfo(tagFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, tagLen)
-  update_folded_hist.getHistWithInfo(altTagFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, tagLen-1)
+  update_folded_hist.getHistWithInfo(idxFhInfo).folded_hist    := compute_folded_ghist(io.update.ghist, log2Ceil(nRows))
+  update_folded_hist.getHistWithInfo(tagFhInfo).folded_hist    := compute_folded_ghist(io.update.ghist, tagLen)
+  update_folded_hist.getHistWithInfo(altTagFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, tagLen - 1)
   dontTouch(update_folded_hist)
   val (update_idx, update_tag) = compute_tag_and_hash(getUnhashedIdx(io.update.pc), update_folded_hist)
-  val update_target = io.update.target
-  val update_wdata = Wire(new ITTageEntry)
+  val update_target            = io.update.target
+  val update_wdata             = Wire(new ITTageEntry)
 
+  val updateAllBitmask = VecInit.fill(ittageEntrySz)(1.U).asUInt // update all entry
+  val updateNoBitmask  = VecInit.fill(ittageEntrySz)(0.U).asUInt // update no
+  val updateNoUsBitmask =
+    VecInit.tabulate(ittageEntrySz)(_.U >= ITTageUsBits.U).asUInt // update others besides useful bit
+  val updateUsBitmask = VecInit.tabulate(ittageEntrySz)(_.U < ITTageUsBits.U).asUInt // update useful bit
 
-  val updateAllBitmask = VecInit.fill(ittageEntrySz)(1.U).asUInt                        //update all entry
-  val updateNoBitmask = VecInit.fill(ittageEntrySz)(0.U).asUInt                         //update no
-  val updateNoUsBitmask = VecInit.tabulate(ittageEntrySz)(_.U >= ITTageUsBits.U).asUInt //update others besides useful bit
-  val updateUsBitmask = VecInit.tabulate(ittageEntrySz)(_.U < ITTageUsBits.U).asUInt    //update useful bit
-
-  val needReset = RegInit(false.B)
-  val useful_can_reset = !(io.req.fire || io.update.valid) && needReset
+  val needReset               = RegInit(false.B)
+  val useful_can_reset        = !(io.req.fire || io.update.valid) && needReset
   val (resetSet, resetFinish) = Counter(useful_can_reset, nRows)
-  when (io.update.reset_u) {
+  when(io.update.reset_u) {
     needReset := true.B
-  }.elsewhen (resetFinish) {
+  }.elsewhen(resetFinish) {
     needReset := false.B
   }
-  val update_bitmask =  Mux(io.update.uValid && io.update.valid,
-                          updateAllBitmask,
-                          Mux(io.update.valid, updateNoUsBitmask,
-                            Mux(useful_can_reset, updateUsBitmask, updateNoBitmask)
-                          ))
+  val update_bitmask = Mux(
+    io.update.uValid && io.update.valid,
+    updateAllBitmask,
+    Mux(io.update.valid, updateNoUsBitmask, Mux(useful_can_reset, updateUsBitmask, updateNoBitmask))
+  )
 
   table.io.w.apply(
-    valid   = io.update.valid || useful_can_reset,
-    data    = update_wdata,
-    setIdx  = Mux(useful_can_reset, resetSet, update_idx),
+    valid = io.update.valid || useful_can_reset,
+    data = update_wdata,
+    setIdx = Mux(useful_can_reset, resetSet, update_idx),
     waymask = true.B,
     bitmask = update_bitmask
   )
@@ -279,48 +274,54 @@ class ITTageTable
 
   val wrbypass = Module(new WrBypass(UInt(ITTageCtrBits.W), wrBypassEntries, log2Ceil(nRows)))
 
-  wrbypass.io.wen := io.update.valid
+  wrbypass.io.wen       := io.update.valid
   wrbypass.io.write_idx := update_idx
   wrbypass.io.write_data.map(_ := update_wdata.ctr)
 
   val old_ctr = Mux(wrbypass.io.hit, wrbypass.io.hit_data(0).bits, io.update.oldCtr)
-  update_wdata.valid := true.B
-  update_wdata.ctr   := Mux(io.update.alloc, 2.U, inc_ctr(old_ctr, io.update.correct))
-  update_wdata.tag   := update_tag
-  update_wdata.useful:= Mux(useful_can_reset, false.B, io.update.u)
+  update_wdata.valid  := true.B
+  update_wdata.ctr    := Mux(io.update.alloc, 2.U, inc_ctr(old_ctr, io.update.correct))
+  update_wdata.tag    := update_tag
+  update_wdata.useful := Mux(useful_can_reset, false.B, io.update.u)
   // only when ctr is null
   update_wdata.target := Mux(io.update.alloc || ctr_null(old_ctr), update_target, io.update.old_target)
 
-
   XSPerfAccumulate("ittage_table_updates", io.update.valid)
   XSPerfAccumulate("ittage_table_hits", io.resp.valid)
   XSPerfAccumulate("ittage_us_tick_reset", io.update.reset_u)
   XSPerfAccumulate("ittage_table_read_write_conflict", read_write_conflict)
 
   if (BPUDebug && debug) {
-    val u = io.update
+    val u   = io.update
     val idx = s0_idx
     val tag = s0_tag
-    XSDebug(io.req.fire,
+    XSDebug(
+      io.req.fire,
       p"ITTageTableReq: pc=0x${Hexadecimal(io.req.bits.pc)}, " +
-      p"idx=$idx, tag=$tag\n")
-    XSDebug(RegNext(io.req.fire) && s1_req_rhit,
+        p"idx=$idx, tag=$tag\n"
+    )
+    XSDebug(
+      RegNext(io.req.fire) && s1_req_rhit,
       p"ITTageTableResp: idx=$s1_idx, hit:${s1_req_rhit}, " +
-      p"ctr:${io.resp.bits.ctr}, u:${io.resp.bits.u}, tar:${Hexadecimal(io.resp.bits.target)}\n")
-    XSDebug(io.update.valid,
+        p"ctr:${io.resp.bits.ctr}, u:${io.resp.bits.u}, tar:${Hexadecimal(io.resp.bits.target)}\n"
+    )
+    XSDebug(
+      io.update.valid,
       p"update ITTAGE Table: pc:${Hexadecimal(u.pc)}}, " +
-      p"correct:${u.correct}, alloc:${u.alloc}, oldCtr:${u.oldCtr}, " +
-      p"target:${Hexadecimal(u.target)}, old_target:${Hexadecimal(u.old_target)}\n")
-    XSDebug(io.update.valid,
+        p"correct:${u.correct}, alloc:${u.alloc}, oldCtr:${u.oldCtr}, " +
+        p"target:${Hexadecimal(u.target)}, old_target:${Hexadecimal(u.old_target)}\n"
+    )
+    XSDebug(
+      io.update.valid,
       p"update ITTAGE Table: writing tag:${update_tag}, " +
-      p"ctr: ${update_wdata.ctr}, target:${Hexadecimal(update_wdata.target)}" +
-      p" in idx $update_idx\n")
+        p"ctr: ${update_wdata.ctr}, target:${Hexadecimal(update_wdata.target)}" +
+        p" in idx $update_idx\n"
+    )
     XSDebug(RegNext(io.req.fire) && !s1_req_rhit, "TageTableResp: no hits!\n")
 
-
     // ------------------------------Debug-------------------------------------
     val valids = RegInit(0.U.asTypeOf(Vec(nRows, Bool())))
-    when (io.update.valid) { valids(update_idx) := true.B }
+    when(io.update.valid)(valids(update_idx) := true.B)
     XSDebug("ITTAGE Table usage:------------------------\n")
     XSDebug("%d out of %d rows are valid\n", PopCount(valids), nRows.U)
   }
@@ -365,16 +366,15 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
       val t = Module(new ITTageTable(nRows, histLen, tagLen, UBitPeriod, i))
       t
   }
-  override def getFoldedHistoryInfo = Some(tables.map(_.getFoldedHistoryInfo).reduce(_++_))
+  override def getFoldedHistoryInfo = Some(tables.map(_.getFoldedHistoryInfo).reduce(_ ++ _))
 
-
-  val useAltOnNa = RegInit((1 << (UAONA_bits-1)).U(UAONA_bits.W))
-  val tickCtr = RegInit(0.U(TickWidth.W))
+  val useAltOnNa = RegInit((1 << (UAONA_bits - 1)).U(UAONA_bits.W))
+  val tickCtr    = RegInit(0.U(TickWidth.W))
 
   // uftb miss or hasIndirect
-  val s1_uftbHit = io.in.bits.resp_in(0).s1_uftbHit
+  val s1_uftbHit         = io.in.bits.resp_in(0).s1_uftbHit
   val s1_uftbHasIndirect = io.in.bits.resp_in(0).s1_uftbHasIndirect
-  val s1_isIndirect = (!s1_uftbHit && !io.in.bits.resp_in(0).s1_ftbCloseReq) || s1_uftbHasIndirect
+  val s1_isIndirect      = (!s1_uftbHit && !io.in.bits.resp_in(0).s1_ftbCloseReq) || s1_uftbHasIndirect
 
   // Keep the table responses to process in s2
 
@@ -413,10 +413,10 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
 
   // Update logic
   val u_valid = io.update.valid
-  val update = io.update.bits
+  val update  = io.update.bits
   val updateValid =
     update.is_jalr && !update.is_ret && u_valid && update.ftb_entry.jmpValid &&
-    update.jmp_taken && update.cfi_idx.valid && update.cfi_idx.bits === update.ftb_entry.tailSlot.offset
+      update.jmp_taken && update.cfi_idx.valid && update.cfi_idx.bits === update.ftb_entry.tailSlot.offset
 
   // meta is splited by composer
   val updateMeta = update.meta.asTypeOf(new ITTageMeta)
@@ -431,44 +431,44 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
   val updateOldCtr    = Wire(Vec(ITTageNTables, UInt(ITTageCtrBits.W)))
   val updateU         = Wire(Vec(ITTageNTables, Bool()))
   updateCorrect   := DontCare
-  updateTarget  := DontCare
-  updateOldTarget  := DontCare
-  updateAlloc   := DontCare
-  updateOldCtr  := DontCare
-  updateU       := DontCare
+  updateTarget    := DontCare
+  updateOldTarget := DontCare
+  updateAlloc     := DontCare
+  updateOldCtr    := DontCare
+  updateU         := DontCare
 
   // val updateTageMisPreds = VecInit((0 until numBr).map(i => updateMetas(i).taken =/= u.takens(i)))
   val updateMisPred = update.mispred_mask(numBr) // the last one indicates jmp results
 
-
   // Predict
-  tables.map { t => {
-      t.io.req.valid := io.s1_fire(3) && s1_isIndirect
-      t.io.req.bits.pc := s1_pc_dup(3)
-      t.io.req.bits.folded_hist := io.in.bits.s1_folded_hist(3)
-    }
+  tables.map { t =>
+    t.io.req.valid            := io.s1_fire(3) && s1_isIndirect
+    t.io.req.bits.pc          := s1_pc_dup(3)
+    t.io.req.bits.folded_hist := io.in.bits.s1_folded_hist(3)
   }
 
   // access tag tables and output meta info
   class ITTageTableInfo(implicit p: Parameters) extends ITTageResp {
     val tableIdx = UInt(log2Ceil(ITTageNTables).W)
   }
-  val inputRes = VecInit(s2_resps.zipWithIndex.map{case (r, i) => {
-    val tableInfo = Wire(new ITTageTableInfo)
-    tableInfo.u := r.bits.u
-    tableInfo.ctr := r.bits.ctr
-    tableInfo.target := r.bits.target
-    tableInfo.tableIdx := i.U(log2Ceil(ITTageNTables).W)
-    SelectTwoInterRes(r.valid, tableInfo)
-  }})
+  val inputRes = VecInit(s2_resps.zipWithIndex.map {
+    case (r, i) => {
+      val tableInfo = Wire(new ITTageTableInfo)
+      tableInfo.u        := r.bits.u
+      tableInfo.ctr      := r.bits.ctr
+      tableInfo.target   := r.bits.target
+      tableInfo.tableIdx := i.U(log2Ceil(ITTageNTables).W)
+      SelectTwoInterRes(r.valid, tableInfo)
+    }
+  })
 
   val selectedInfo = ParallelSelectTwo(inputRes.reverse)
-  val provided = selectedInfo.hasOne
-  val altProvided = selectedInfo.hasTwo
+  val provided     = selectedInfo.hasOne
+  val altProvided  = selectedInfo.hasTwo
 
-  val providerInfo = selectedInfo.first
+  val providerInfo    = selectedInfo.first
   val altProviderInfo = selectedInfo.second
-  val providerNull = providerInfo.ctr === 0.U
+  val providerNull    = providerInfo.ctr === 0.U
 
   val baseTarget = io.in.bits.resp_in(0).s2.full_pred(3).jalr_target // use ftb pred as base target
 
@@ -477,22 +477,23 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
     (altProvided && providerNull, altProviderInfo.target),
     (!provided, baseTarget)
   ))
-  s2_provided       := provided
-  s2_provider       := providerInfo.tableIdx
-  s2_altProvided    := altProvided
-  s2_altProvider    := altProviderInfo.tableIdx
-  s2_providerU      := providerInfo.u
-  s2_providerCtr    := providerInfo.ctr
-  s2_altProviderCtr := altProviderInfo.ctr
-  s2_providerTarget := providerInfo.target
+  s2_provided          := provided
+  s2_provider          := providerInfo.tableIdx
+  s2_altProvided       := altProvided
+  s2_altProvider       := altProviderInfo.tableIdx
+  s2_providerU         := providerInfo.u
+  s2_providerCtr       := providerInfo.ctr
+  s2_altProviderCtr    := altProviderInfo.ctr
+  s2_providerTarget    := providerInfo.target
   s2_altProviderTarget := altProviderInfo.target
 
   XSDebug(io.s2_fire(3), p"hit_taken_jalr:")
 
-  for (fp & s3_tageTarget <-
-    io.out.s3.full_pred zip s3_tageTarget_dup)
-    yield
-    fp.jalr_target := s3_tageTarget
+  for (
+    fp & s3_tageTarget <-
+      io.out.s3.full_pred zip s3_tageTarget_dup
+  )
+    yield fp.jalr_target := s3_tageTarget
 
   resp_meta.provider.valid    := s3_provided
   resp_meta.provider.bits     := s3_provider
@@ -504,7 +505,7 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
   resp_meta.altProviderCtr    := s3_altProviderCtr
   resp_meta.providerTarget    := s3_providerTarget
   resp_meta.altProviderTarget := s3_altProviderTarget
-  resp_meta.pred_cycle.map(_:= GTimer())
+  resp_meta.pred_cycle.map(_ := GTimer())
   // TODO: adjust for ITTAGE
   // Create a mask fo tables which did not hit our query, and also contain useless entries
   // and also uses a longer history than the provider
@@ -519,121 +520,127 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
 
   // Update in loop
   val updateRealTarget = update.full_target
-  when (updateValid) {
-    when (updateMeta.provider.valid) {
+  when(updateValid) {
+    when(updateMeta.provider.valid) {
       val provider = updateMeta.provider.bits
       XSDebug(true.B, p"update provider $provider, pred cycle ${updateMeta.pred_cycle.getOrElse(0.U)}\n")
       val altProvider = updateMeta.altProvider.bits
       val usedAltpred = updateMeta.altProvider.valid && updateMeta.providerCtr === 0.U
-      when (usedAltpred && updateMisPred) { // update altpred if used as pred
+      when(usedAltpred && updateMisPred) { // update altpred if used as pred
         XSDebug(true.B, p"update altprovider $altProvider, pred cycle ${updateMeta.pred_cycle.getOrElse(0.U)}\n")
 
-        updateMask(altProvider)    := true.B
-        updateUMask(altProvider)   := false.B
-        updateCorrect(altProvider) := false.B
-        updateOldCtr(altProvider)  := updateMeta.altProviderCtr
-        updateAlloc(altProvider)   := false.B
-        updateTarget(altProvider)  := updateRealTarget
+        updateMask(altProvider)      := true.B
+        updateUMask(altProvider)     := false.B
+        updateCorrect(altProvider)   := false.B
+        updateOldCtr(altProvider)    := updateMeta.altProviderCtr
+        updateAlloc(altProvider)     := false.B
+        updateTarget(altProvider)    := updateRealTarget
         updateOldTarget(altProvider) := updateMeta.altProviderTarget
       }
 
+      updateMask(provider)  := true.B
+      updateUMask(provider) := true.B
 
-      updateMask(provider)   := true.B
-      updateUMask(provider)  := true.B
-
-      updateU(provider) := Mux(!updateMeta.altDiffers, updateMeta.providerU,
-                                updateMeta.providerTarget === updateRealTarget)
-      updateCorrect(provider)  := updateMeta.providerTarget === updateRealTarget
-      updateTarget(provider) := updateRealTarget
+      updateU(provider) := Mux(
+        !updateMeta.altDiffers,
+        updateMeta.providerU,
+        updateMeta.providerTarget === updateRealTarget
+      )
+      updateCorrect(provider)   := updateMeta.providerTarget === updateRealTarget
+      updateTarget(provider)    := updateRealTarget
       updateOldTarget(provider) := updateMeta.providerTarget
-      updateOldCtr(provider) := updateMeta.providerCtr
-      updateAlloc(provider)  := false.B
+      updateOldCtr(provider)    := updateMeta.providerCtr
+      updateAlloc(provider)     := false.B
     }
   }
 
   // if mispredicted and not the case that
   // provider offered correct target but used altpred due to unconfident
   val providerCorrect = updateMeta.provider.valid && updateMeta.providerTarget === updateRealTarget
-  val providerUnconf = updateMeta.providerCtr === 0.U
-  when (updateValid && updateMisPred && !(providerCorrect && providerUnconf)) {
+  val providerUnconf  = updateMeta.providerCtr === 0.U
+  when(updateValid && updateMisPred && !(providerCorrect && providerUnconf)) {
     val allocate = updateMeta.allocate
     tickCtr := satUpdate(tickCtr, TickWidth, !allocate.valid)
-    when (allocate.valid) {
+    when(allocate.valid) {
       XSDebug(true.B, p"allocate new table entry, pred cycle ${updateMeta.pred_cycle.getOrElse(0.U)}\n")
-      updateMask(allocate.bits)  := true.B
+      updateMask(allocate.bits)    := true.B
       updateCorrect(allocate.bits) := true.B // useless for alloc
-      updateTarget(allocate.bits) := updateRealTarget
-      updateAlloc(allocate.bits) := true.B
-      updateUMask(allocate.bits) := true.B
-      updateU(allocate.bits) := false.B
+      updateTarget(allocate.bits)  := updateRealTarget
+      updateAlloc(allocate.bits)   := true.B
+      updateUMask(allocate.bits)   := true.B
+      updateU(allocate.bits)       := false.B
     }
   }
 
-  when (tickCtr === ((1 << TickWidth) - 1).U) {
-    tickCtr := 0.U
+  when(tickCtr === ((1 << TickWidth) - 1).U) {
+    tickCtr      := 0.U
     updateResetU := true.B
   }
 
   for (i <- 0 until ITTageNTables) {
-    tables(i).io.update.valid := RegNext(updateMask(i), init = false.B)
-    tables(i).io.update.reset_u := RegNext(updateResetU, init = false.B)
-    tables(i).io.update.correct := RegEnable(updateCorrect(i), updateMask(i))
-    tables(i).io.update.target := RegEnable(updateTarget(i), updateMask(i))
+    tables(i).io.update.valid      := RegNext(updateMask(i), init = false.B)
+    tables(i).io.update.reset_u    := RegNext(updateResetU, init = false.B)
+    tables(i).io.update.correct    := RegEnable(updateCorrect(i), updateMask(i))
+    tables(i).io.update.target     := RegEnable(updateTarget(i), updateMask(i))
     tables(i).io.update.old_target := RegEnable(updateOldTarget(i), updateMask(i))
-    tables(i).io.update.alloc := RegEnable(updateAlloc(i), updateMask(i))
-    tables(i).io.update.oldCtr := RegEnable(updateOldCtr(i), updateMask(i))
+    tables(i).io.update.alloc      := RegEnable(updateAlloc(i), updateMask(i))
+    tables(i).io.update.oldCtr     := RegEnable(updateOldCtr(i), updateMask(i))
 
     tables(i).io.update.uValid := RegEnable(updateUMask(i), false.B, updateMask(i))
-    tables(i).io.update.u := RegEnable(updateU(i), updateMask(i))
-    tables(i).io.update.pc := RegEnable(update.pc, updateMask(i))
+    tables(i).io.update.u      := RegEnable(updateU(i), updateMask(i))
+    tables(i).io.update.pc     := RegEnable(update.pc, updateMask(i))
     // use fetch pc instead of instruction pc
     tables(i).io.update.ghist := RegEnable(update.ghist, updateMask(i))
   }
 
   // all should be ready for req
-  io.s1_ready := tables.map(_.io.req.ready).reduce(_&&_)
+  io.s1_ready := tables.map(_.io.req.ready).reduce(_ && _)
 
   // Debug and perf info
   XSPerfAccumulate("ittage_reset_u", updateResetU)
   XSPerfAccumulate("ittage_used", io.s1_fire(0) && s1_isIndirect)
   XSPerfAccumulate("ittage_closed_due_to_uftb_info", io.s1_fire(0) && !s1_isIndirect)
 
-  def pred_perf(name: String, cond: Bool)   = XSPerfAccumulate(s"${name}_at_pred", cond && io.s2_fire(3))
+  def pred_perf(name:   String, cond: Bool) = XSPerfAccumulate(s"${name}_at_pred", cond && io.s2_fire(3))
   def commit_perf(name: String, cond: Bool) = XSPerfAccumulate(s"${name}_at_commit", cond && updateValid)
   def ittage_perf(name: String, pred_cond: Bool, commit_cond: Bool) = {
     pred_perf(s"ittage_${name}", pred_cond)
     commit_perf(s"ittage_${name}", commit_cond)
   }
-  val pred_use_provider = s2_provided && !ctr_null(s2_providerCtr)
-  val pred_use_altpred = s2_provided && ctr_null(s2_providerCtr)
-  val pred_use_ht_as_altpred = pred_use_altpred && s2_altProvided
+  val pred_use_provider       = s2_provided && !ctr_null(s2_providerCtr)
+  val pred_use_altpred        = s2_provided && ctr_null(s2_providerCtr)
+  val pred_use_ht_as_altpred  = pred_use_altpred && s2_altProvided
   val pred_use_bim_as_altpred = pred_use_altpred && !s2_altProvided
-  val pred_use_bim_as_pred = !s2_provided
+  val pred_use_bim_as_pred    = !s2_provided
 
-  val commit_use_provider = updateMeta.provider.valid && !ctr_null(updateMeta.providerCtr)
-  val commit_use_altpred = updateMeta.provider.valid && ctr_null(updateMeta.providerCtr)
-  val commit_use_ht_as_altpred = commit_use_altpred && updateMeta.altProvider.valid
+  val commit_use_provider       = updateMeta.provider.valid && !ctr_null(updateMeta.providerCtr)
+  val commit_use_altpred        = updateMeta.provider.valid && ctr_null(updateMeta.providerCtr)
+  val commit_use_ht_as_altpred  = commit_use_altpred && updateMeta.altProvider.valid
   val commit_use_bim_as_altpred = commit_use_altpred && !updateMeta.altProvider.valid
-  val commit_use_bim_as_pred = !updateMeta.provider.valid
+  val commit_use_bim_as_pred    = !updateMeta.provider.valid
 
   for (i <- 0 until ITTageNTables) {
-    val pred_this_is_provider = s2_provider === i.U
-    val pred_this_is_altpred  = s2_altProvider === i.U
+    val pred_this_is_provider   = s2_provider === i.U
+    val pred_this_is_altpred    = s2_altProvider === i.U
     val commit_this_is_provider = updateMeta.provider.bits === i.U
     val commit_this_is_altpred  = updateMeta.altProvider.bits === i.U
-    ittage_perf(s"table_${i}_final_provided",
+    ittage_perf(
+      s"table_${i}_final_provided",
       pred_use_provider && pred_this_is_provider,
       commit_use_provider && commit_this_is_provider
     )
-    ittage_perf(s"table_${i}_provided_not_used",
+    ittage_perf(
+      s"table_${i}_provided_not_used",
       pred_use_altpred && pred_this_is_provider,
       commit_use_altpred && commit_this_is_provider
     )
-    ittage_perf(s"table_${i}_alt_provider_as_final_pred",
+    ittage_perf(
+      s"table_${i}_alt_provider_as_final_pred",
       pred_use_ht_as_altpred && pred_this_is_altpred,
       commit_use_ht_as_altpred && commit_this_is_altpred
     )
-    ittage_perf(s"table_${i}_alt_provider_not_used",
+    ittage_perf(
+      s"table_${i}_alt_provider_not_used",
       pred_use_provider && pred_this_is_altpred,
       commit_use_provider && commit_this_is_altpred
     )
@@ -652,16 +659,20 @@ class ITTage(implicit p: Parameters) extends BaseITTage {
     val s2_resps_regs = RegEnable(s2_resps, io.s2_fire(3))
     XSDebug("req: v=%d, pc=0x%x\n", io.s0_fire(3), s0_pc_dup(3))
     XSDebug("s1_fire:%d, resp: pc=%x\n", io.s1_fire(3), debug_pc_s1)
-    XSDebug("s2_fireOnLastCycle: resp: pc=%x, target=%x, hit=%b\n",
-      debug_pc_s2, io.out.s2.getTarget(3), s2_provided)
+    XSDebug("s2_fireOnLastCycle: resp: pc=%x, target=%x, hit=%b\n", debug_pc_s2, io.out.s2.getTarget(3), s2_provided)
     for (i <- 0 until ITTageNTables) {
-      XSDebug("TageTable(%d): valids:%b, resp_ctrs:%b, resp_us:%b, target:%x\n",
-        i.U, VecInit(s2_resps_regs(i).valid).asUInt, s2_resps_regs(i).bits.ctr,
-        s2_resps_regs(i).bits.u, s2_resps_regs(i).bits.target)
+      XSDebug(
+        "TageTable(%d): valids:%b, resp_ctrs:%b, resp_us:%b, target:%x\n",
+        i.U,
+        VecInit(s2_resps_regs(i).valid).asUInt,
+        s2_resps_regs(i).bits.ctr,
+        s2_resps_regs(i).bits.u,
+        s2_resps_regs(i).bits.target
+      )
     }
   }
   XSDebug(updateValid, p"pc: ${Hexadecimal(update.pc)}, target: ${Hexadecimal(update.full_target)}\n")
-  XSDebug(updateValid, updateMeta.toPrintable+p"\n")
+  XSDebug(updateValid, updateMeta.toPrintable + p"\n")
   XSDebug(updateValid, p"correct(${!updateMisPred})\n")
 
   generatePerfEvent()
diff --git a/src/main/scala/xiangshan/frontend/NewFtq.scala b/src/main/scala/xiangshan/frontend/NewFtq.scala
index c4696fc58e9..c41c6cc5ad0 100644
--- a/src/main/scala/xiangshan/frontend/NewFtq.scala
+++ b/src/main/scala/xiangshan/frontend/NewFtq.scala
@@ -16,85 +16,87 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import utils._
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utility.ChiselDB
+import utils._
 import xiangshan._
-import xiangshan.frontend.icache._
 import xiangshan.backend.CtrlToFtqIO
 import xiangshan.backend.decode.ImmUnion
-import utility.ChiselDB
+import xiangshan.frontend.icache._
 
 class FtqDebugBundle extends Bundle {
-  val pc = UInt(39.W)
-  val target = UInt(39.W)
-  val isBr = Bool()
-  val isJmp = Bool()
-  val isCall = Bool()
-  val isRet = Bool()
-  val misPred = Bool()
-  val isTaken = Bool()
+  val pc        = UInt(39.W)
+  val target    = UInt(39.W)
+  val isBr      = Bool()
+  val isJmp     = Bool()
+  val isCall    = Bool()
+  val isRet     = Bool()
+  val misPred   = Bool()
+  val isTaken   = Bool()
   val predStage = UInt(2.W)
 }
 
 class FtqPtr(entries: Int) extends CircularQueuePtr[FtqPtr](
-  entries
-){
+      entries
+    ) {
   def this()(implicit p: Parameters) = this(p(XSCoreParamsKey).FtqSize)
 }
 
 object FtqPtr {
   def apply(f: Bool, v: UInt)(implicit p: Parameters): FtqPtr = {
     val ptr = Wire(new FtqPtr)
-    ptr.flag := f
+    ptr.flag  := f
     ptr.value := v
     ptr
   }
-  def inverse(ptr: FtqPtr)(implicit p: Parameters): FtqPtr = {
+  def inverse(ptr: FtqPtr)(implicit p: Parameters): FtqPtr =
     apply(!ptr.flag, ptr.value)
-  }
 }
 
 class FtqNRSRAM[T <: Data](gen: T, numRead: Int)(implicit p: Parameters) extends XSModule {
 
   val io = IO(new Bundle() {
     val raddr = Input(Vec(numRead, UInt(log2Up(FtqSize).W)))
-    val ren = Input(Vec(numRead, Bool()))
+    val ren   = Input(Vec(numRead, Bool()))
     val rdata = Output(Vec(numRead, gen))
     val waddr = Input(UInt(log2Up(FtqSize).W))
-    val wen = Input(Bool())
+    val wen   = Input(Bool())
     val wdata = Input(gen)
   })
 
-  for(i <- 0 until numRead){
+  for (i <- 0 until numRead) {
     val sram = Module(new SRAMTemplate(gen, FtqSize))
-    sram.io.r.req.valid := io.ren(i)
+    sram.io.r.req.valid       := io.ren(i)
     sram.io.r.req.bits.setIdx := io.raddr(i)
-    io.rdata(i) := sram.io.r.resp.data(0)
-    sram.io.w.req.valid := io.wen
+    io.rdata(i)               := sram.io.r.resp.data(0)
+    sram.io.w.req.valid       := io.wen
     sram.io.w.req.bits.setIdx := io.waddr
-    sram.io.w.req.bits.data := VecInit(io.wdata)
+    sram.io.w.req.bits.data   := VecInit(io.wdata)
   }
 
 }
 
 class Ftq_RF_Components(implicit p: Parameters) extends XSBundle with BPUUtils {
-  val startAddr = UInt(VAddrBits.W)
-  val nextLineAddr = UInt(VAddrBits.W)
-  val isNextMask = Vec(PredictWidth, Bool())
+  val startAddr     = UInt(VAddrBits.W)
+  val nextLineAddr  = UInt(VAddrBits.W)
+  val isNextMask    = Vec(PredictWidth, Bool())
   val fallThruError = Bool()
   // val carry = Bool()
   def getPc(offset: UInt) = {
-    def getHigher(pc: UInt) = pc(VAddrBits-1, log2Ceil(PredictWidth)+instOffsetBits+1)
-    def getOffset(pc: UInt) = pc(log2Ceil(PredictWidth)+instOffsetBits, instOffsetBits)
-    Cat(getHigher(Mux(isNextMask(offset) && startAddr(log2Ceil(PredictWidth)+instOffsetBits), nextLineAddr, startAddr)),
-        getOffset(startAddr)+offset, 0.U(instOffsetBits.W))
+    def getHigher(pc: UInt) = pc(VAddrBits - 1, log2Ceil(PredictWidth) + instOffsetBits + 1)
+    def getOffset(pc: UInt) = pc(log2Ceil(PredictWidth) + instOffsetBits, instOffsetBits)
+    Cat(
+      getHigher(Mux(isNextMask(offset) && startAddr(log2Ceil(PredictWidth) + instOffsetBits), nextLineAddr, startAddr)),
+      getOffset(startAddr) + offset,
+      0.U(instOffsetBits.W)
+    )
   }
   def fromBranchPrediction(resp: BranchPredictionBundle) = {
-    def carryPos(addr: UInt) = addr(instOffsetBits+log2Ceil(PredictWidth)+1)
-    this.startAddr := resp.pc(3)
+    def carryPos(addr: UInt) = addr(instOffsetBits + log2Ceil(PredictWidth) + 1)
+    this.startAddr    := resp.pc(3)
     this.nextLineAddr := resp.pc(3) + (FetchWidth * 4 * 2).U // may be broken on other configs
     this.isNextMask := VecInit((0 until PredictWidth).map(i =>
       (resp.pc(3)(log2Ceil(PredictWidth), 1) +& i.U)(log2Ceil(PredictWidth)).asBool
@@ -102,30 +104,31 @@ class Ftq_RF_Components(implicit p: Parameters) extends XSBundle with BPUUtils {
     this.fallThruError := resp.fallThruError(3)
     this
   }
-  override def toPrintable: Printable = {
+  override def toPrintable: Printable =
     p"startAddr:${Hexadecimal(startAddr)}"
-  }
 }
 
 class Ftq_pd_Entry(implicit p: Parameters) extends XSBundle {
-  val brMask = Vec(PredictWidth, Bool())
-  val jmpInfo = ValidUndirectioned(Vec(3, Bool()))
+  val brMask    = Vec(PredictWidth, Bool())
+  val jmpInfo   = ValidUndirectioned(Vec(3, Bool()))
   val jmpOffset = UInt(log2Ceil(PredictWidth).W)
   val jalTarget = UInt(VAddrBits.W)
-  val rvcMask = Vec(PredictWidth, Bool())
-  def hasJal  = jmpInfo.valid && !jmpInfo.bits(0)
-  def hasJalr = jmpInfo.valid && jmpInfo.bits(0)
-  def hasCall = jmpInfo.valid && jmpInfo.bits(1)
-  def hasRet  = jmpInfo.valid && jmpInfo.bits(2)
+  val rvcMask   = Vec(PredictWidth, Bool())
+  def hasJal    = jmpInfo.valid && !jmpInfo.bits(0)
+  def hasJalr   = jmpInfo.valid && jmpInfo.bits(0)
+  def hasCall   = jmpInfo.valid && jmpInfo.bits(1)
+  def hasRet    = jmpInfo.valid && jmpInfo.bits(2)
 
   def fromPdWb(pdWb: PredecodeWritebackBundle) = {
     val pds = pdWb.pd
-    this.brMask := VecInit(pds.map(pd => pd.isBr && pd.valid))
+    this.brMask        := VecInit(pds.map(pd => pd.isBr && pd.valid))
     this.jmpInfo.valid := VecInit(pds.map(pd => (pd.isJal || pd.isJalr) && pd.valid)).asUInt.orR
-    this.jmpInfo.bits := ParallelPriorityMux(pds.map(pd => (pd.isJal || pd.isJalr) && pd.valid),
-                                             pds.map(pd => VecInit(pd.isJalr, pd.isCall, pd.isRet)))
+    this.jmpInfo.bits := ParallelPriorityMux(
+      pds.map(pd => (pd.isJal || pd.isJalr) && pd.valid),
+      pds.map(pd => VecInit(pd.isJalr, pd.isCall, pd.isRet))
+    )
     this.jmpOffset := ParallelPriorityEncoder(pds.map(pd => (pd.isJal || pd.isJalr) && pd.valid))
-    this.rvcMask := VecInit(pds.map(pd => pd.isRVC))
+    this.rvcMask   := VecInit(pds.map(pd => pd.isRVC))
     this.jalTarget := pdWb.jalTarget
   }
 
@@ -134,7 +137,7 @@ class Ftq_pd_Entry(implicit p: Parameters) extends XSBundle {
     val pd = Wire(new PreDecodeInfo)
     pd.valid := true.B
     pd.isRVC := rvcMask(offset)
-    val isBr = brMask(offset)
+    val isBr   = brMask(offset)
     val isJalr = offset === jmpOffset && jmpInfo.valid && jmpInfo.bits(0)
     pd.brType := Cat(offset === jmpOffset && jmpInfo.valid, isJalr || isBr)
     pd.isCall := offset === jmpOffset && jmpInfo.valid && jmpInfo.bits(1)
@@ -144,8 +147,8 @@ class Ftq_pd_Entry(implicit p: Parameters) extends XSBundle {
 }
 
 class PrefetchPtrDB(implicit p: Parameters) extends Bundle {
-  val fromFtqPtr  = UInt(log2Up(p(XSCoreParamsKey).FtqSize).W)
-  val fromIfuPtr  = UInt(log2Up(p(XSCoreParamsKey).FtqSize).W)
+  val fromFtqPtr = UInt(log2Up(p(XSCoreParamsKey).FtqSize).W)
+  val fromIfuPtr = UInt(log2Up(p(XSCoreParamsKey).FtqSize).W)
 }
 
 class Ftq_Redirect_SRAMEntry(implicit p: Parameters) extends SpeculativeInfo {
@@ -153,34 +156,32 @@ class Ftq_Redirect_SRAMEntry(implicit p: Parameters) extends SpeculativeInfo {
 }
 
 class Ftq_1R_SRAMEntry(implicit p: Parameters) extends XSBundle with HasBPUConst {
-  val meta = UInt(MaxMetaLength.W)
+  val meta      = UInt(MaxMetaLength.W)
   val ftb_entry = new FTBEntry
 }
 
 class Ftq_Pred_Info(implicit p: Parameters) extends XSBundle {
-  val target = UInt(VAddrBits.W)
+  val target   = UInt(VAddrBits.W)
   val cfiIndex = ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))
 }
 
-
 class FtqRead[T <: Data](private val gen: T)(implicit p: Parameters) extends XSBundle {
-  val valid = Output(Bool())
-  val ptr = Output(new FtqPtr)
+  val valid  = Output(Bool())
+  val ptr    = Output(new FtqPtr)
   val offset = Output(UInt(log2Ceil(PredictWidth).W))
-  val data = Input(gen)
+  val data   = Input(gen)
   def apply(valid: Bool, ptr: FtqPtr, offset: UInt) = {
-    this.valid := valid
-    this.ptr := ptr
+    this.valid  := valid
+    this.ptr    := ptr
     this.offset := offset
     this.data
   }
 }
 
-
 class FtqToBpuIO(implicit p: Parameters) extends XSBundle {
-  val redirect = Valid(new BranchPredictionRedirect)
-  val update = Valid(new BranchPredictionUpdate)
-  val enq_ptr = Output(new FtqPtr)
+  val redirect       = Valid(new BranchPredictionRedirect)
+  val update         = Valid(new BranchPredictionUpdate)
+  val enq_ptr        = Output(new FtqPtr)
   val redirctFromIFU = Output(Bool())
 }
 
@@ -189,28 +190,27 @@ class BpuFlushInfo(implicit p: Parameters) extends XSBundle with HasCircularQueu
   // a packet from bpu s3 can reach f1 at most
   val s2 = Valid(new FtqPtr)
   val s3 = Valid(new FtqPtr)
-  def shouldFlushBy(src: Valid[FtqPtr], idx_to_flush: FtqPtr) = {
+  def shouldFlushBy(src: Valid[FtqPtr], idx_to_flush: FtqPtr) =
     src.valid && !isAfter(src.bits, idx_to_flush)
-  }
   def shouldFlushByStage2(idx: FtqPtr) = shouldFlushBy(s2, idx)
   def shouldFlushByStage3(idx: FtqPtr) = shouldFlushBy(s3, idx)
 }
 
 class FtqToIfuIO(implicit p: Parameters) extends XSBundle {
-  val req = Decoupled(new FetchRequestBundle)
-  val redirect = Valid(new BranchPredictionRedirect)
+  val req              = Decoupled(new FetchRequestBundle)
+  val redirect         = Valid(new BranchPredictionRedirect)
   val topdown_redirect = Valid(new BranchPredictionRedirect)
-  val flushFromBpu = new BpuFlushInfo
+  val flushFromBpu     = new BpuFlushInfo
 }
 
 class FtqToICacheIO(implicit p: Parameters) extends XSBundle {
-  //NOTE: req.bits must be prepare in T cycle
+  // NOTE: req.bits must be prepare in T cycle
   // while req.valid is set true in T + 1 cycle
   val req = Decoupled(new FtqToICacheRequestBundle)
 }
 
 class FtqToPrefetchIO(implicit p: Parameters) extends XSBundle {
-  val req = Decoupled(new FtqICacheInfo)
+  val req          = Decoupled(new FtqICacheInfo)
   val flushFromBpu = new BpuFlushInfo
 }
 
@@ -220,125 +220,125 @@ trait HasBackendRedirectInfo extends HasXSParameter {
 
 class FtqToCtrlIO(implicit p: Parameters) extends XSBundle with HasBackendRedirectInfo {
   // write to backend pc mem
-  val pc_mem_wen = Output(Bool())
+  val pc_mem_wen   = Output(Bool())
   val pc_mem_waddr = Output(UInt(log2Ceil(FtqSize).W))
   val pc_mem_wdata = Output(new Ftq_RF_Components)
   // newest target
-  val newest_entry_en = Output(Bool())
+  val newest_entry_en     = Output(Bool())
   val newest_entry_target = Output(UInt(VAddrBits.W))
-  val newest_entry_ptr = Output(new FtqPtr)
+  val newest_entry_ptr    = Output(new FtqPtr)
 }
 
 class FTBEntryGen(implicit p: Parameters) extends XSModule with HasBackendRedirectInfo with HasBPUParameter {
   val io = IO(new Bundle {
-    val start_addr = Input(UInt(VAddrBits.W))
-    val old_entry = Input(new FTBEntry)
-    val pd = Input(new Ftq_pd_Entry)
-    val cfiIndex = Flipped(Valid(UInt(log2Ceil(PredictWidth).W)))
-    val target = Input(UInt(VAddrBits.W))
-    val hit = Input(Bool())
+    val start_addr     = Input(UInt(VAddrBits.W))
+    val old_entry      = Input(new FTBEntry)
+    val pd             = Input(new Ftq_pd_Entry)
+    val cfiIndex       = Flipped(Valid(UInt(log2Ceil(PredictWidth).W)))
+    val target         = Input(UInt(VAddrBits.W))
+    val hit            = Input(Bool())
     val mispredict_vec = Input(Vec(PredictWidth, Bool()))
 
-    val new_entry = Output(new FTBEntry)
+    val new_entry         = Output(new FTBEntry)
     val new_br_insert_pos = Output(Vec(numBr, Bool()))
-    val taken_mask = Output(Vec(numBr, Bool()))
-    val jmp_taken = Output(Bool())
-    val mispred_mask = Output(Vec(numBr+1, Bool()))
+    val taken_mask        = Output(Vec(numBr, Bool()))
+    val jmp_taken         = Output(Bool())
+    val mispred_mask      = Output(Vec(numBr + 1, Bool()))
 
     // for perf counters
-    val is_init_entry = Output(Bool())
-    val is_old_entry = Output(Bool())
-    val is_new_br = Output(Bool())
-    val is_jalr_target_modified = Output(Bool())
+    val is_init_entry            = Output(Bool())
+    val is_old_entry             = Output(Bool())
+    val is_new_br                = Output(Bool())
+    val is_jalr_target_modified  = Output(Bool())
     val is_always_taken_modified = Output(Bool())
-    val is_br_full = Output(Bool())
+    val is_br_full               = Output(Bool())
   })
 
   // no mispredictions detected at predecode
   val hit = io.hit
-  val pd = io.pd
+  val pd  = io.pd
 
   val init_entry = WireInit(0.U.asTypeOf(new FTBEntry))
 
-
-  val cfi_is_br = pd.brMask(io.cfiIndex.bits) && io.cfiIndex.valid
-  val entry_has_jmp = pd.jmpInfo.valid
+  val cfi_is_br       = pd.brMask(io.cfiIndex.bits) && io.cfiIndex.valid
+  val entry_has_jmp   = pd.jmpInfo.valid
   val new_jmp_is_jal  = entry_has_jmp && !pd.jmpInfo.bits(0) && io.cfiIndex.valid
-  val new_jmp_is_jalr = entry_has_jmp &&  pd.jmpInfo.bits(0) && io.cfiIndex.valid
-  val new_jmp_is_call = entry_has_jmp &&  pd.jmpInfo.bits(1) && io.cfiIndex.valid
-  val new_jmp_is_ret  = entry_has_jmp &&  pd.jmpInfo.bits(2) && io.cfiIndex.valid
-  val last_jmp_rvi = entry_has_jmp && pd.jmpOffset === (PredictWidth-1).U && !pd.rvcMask.last
+  val new_jmp_is_jalr = entry_has_jmp && pd.jmpInfo.bits(0) && io.cfiIndex.valid
+  val new_jmp_is_call = entry_has_jmp && pd.jmpInfo.bits(1) && io.cfiIndex.valid
+  val new_jmp_is_ret  = entry_has_jmp && pd.jmpInfo.bits(2) && io.cfiIndex.valid
+  val last_jmp_rvi    = entry_has_jmp && pd.jmpOffset === (PredictWidth - 1).U && !pd.rvcMask.last
   // val last_br_rvi = cfi_is_br && io.cfiIndex.bits === (PredictWidth-1).U && !pd.rvcMask.last
 
-  val cfi_is_jal = io.cfiIndex.bits === pd.jmpOffset && new_jmp_is_jal
+  val cfi_is_jal  = io.cfiIndex.bits === pd.jmpOffset && new_jmp_is_jal
   val cfi_is_jalr = io.cfiIndex.bits === pd.jmpOffset && new_jmp_is_jalr
 
-  def carryPos = log2Ceil(PredictWidth)+instOffsetBits
-  def getLower(pc: UInt) = pc(carryPos-1, instOffsetBits)
+  def carryPos = log2Ceil(PredictWidth) + instOffsetBits
+  def getLower(pc: UInt) = pc(carryPos - 1, instOffsetBits)
   // if not hit, establish a new entry
   init_entry.valid := true.B
   // tag is left for ftb to assign
 
   // case br
   val init_br_slot = init_entry.getSlotForBr(0)
-  when (cfi_is_br) {
-    init_br_slot.valid := true.B
+  when(cfi_is_br) {
+    init_br_slot.valid  := true.B
     init_br_slot.offset := io.cfiIndex.bits
     init_br_slot.setLowerStatByTarget(io.start_addr, io.target, numBr == 1)
     init_entry.always_taken(0) := true.B // set to always taken on init
   }
 
   // case jmp
-  when (entry_has_jmp) {
+  when(entry_has_jmp) {
     init_entry.tailSlot.offset := pd.jmpOffset
-    init_entry.tailSlot.valid := new_jmp_is_jal || new_jmp_is_jalr
-    init_entry.tailSlot.setLowerStatByTarget(io.start_addr, Mux(cfi_is_jalr, io.target, pd.jalTarget), isShare=false)
+    init_entry.tailSlot.valid  := new_jmp_is_jal || new_jmp_is_jalr
+    init_entry.tailSlot.setLowerStatByTarget(io.start_addr, Mux(cfi_is_jalr, io.target, pd.jalTarget), isShare = false)
   }
 
   val jmpPft = getLower(io.start_addr) +& pd.jmpOffset +& Mux(pd.rvcMask(pd.jmpOffset), 1.U, 2.U)
   init_entry.pftAddr := Mux(entry_has_jmp && !last_jmp_rvi, jmpPft, getLower(io.start_addr))
-  init_entry.carry   := Mux(entry_has_jmp && !last_jmp_rvi, jmpPft(carryPos-instOffsetBits), true.B)
-  init_entry.isJalr := new_jmp_is_jalr
-  init_entry.isCall := new_jmp_is_call
-  init_entry.isRet  := new_jmp_is_ret
+  init_entry.carry   := Mux(entry_has_jmp && !last_jmp_rvi, jmpPft(carryPos - instOffsetBits), true.B)
+  init_entry.isJalr  := new_jmp_is_jalr
+  init_entry.isCall  := new_jmp_is_call
+  init_entry.isRet   := new_jmp_is_ret
   // that means fall thru points to the middle of an inst
-  init_entry.last_may_be_rvi_call := pd.jmpOffset === (PredictWidth-1).U && !pd.rvcMask(pd.jmpOffset)
+  init_entry.last_may_be_rvi_call := pd.jmpOffset === (PredictWidth - 1).U && !pd.rvcMask(pd.jmpOffset)
 
   // if hit, check whether a new cfi(only br is possible) is detected
-  val oe = io.old_entry
+  val oe              = io.old_entry
   val br_recorded_vec = oe.getBrRecordedVec(io.cfiIndex.bits)
-  val br_recorded = br_recorded_vec.asUInt.orR
-  val is_new_br = cfi_is_br && !br_recorded
-  val new_br_offset = io.cfiIndex.bits
+  val br_recorded     = br_recorded_vec.asUInt.orR
+  val is_new_br       = cfi_is_br && !br_recorded
+  val new_br_offset   = io.cfiIndex.bits
   // vec(i) means new br will be inserted BEFORE old br(i)
   val allBrSlotsVec = oe.allSlotsForBr
-  val new_br_insert_onehot = VecInit((0 until numBr).map{
-    i => i match {
-      case 0 =>
-        !allBrSlotsVec(0).valid || new_br_offset < allBrSlotsVec(0).offset
-      case idx =>
-        allBrSlotsVec(idx-1).valid && new_br_offset > allBrSlotsVec(idx-1).offset &&
-        (!allBrSlotsVec(idx).valid || new_br_offset < allBrSlotsVec(idx).offset)
-    }
+  val new_br_insert_onehot = VecInit((0 until numBr).map {
+    i =>
+      i match {
+        case 0 =>
+          !allBrSlotsVec(0).valid || new_br_offset < allBrSlotsVec(0).offset
+        case idx =>
+          allBrSlotsVec(idx - 1).valid && new_br_offset > allBrSlotsVec(idx - 1).offset &&
+          (!allBrSlotsVec(idx).valid || new_br_offset < allBrSlotsVec(idx).offset)
+      }
   })
 
   val old_entry_modified = WireInit(io.old_entry)
   for (i <- 0 until numBr) {
     val slot = old_entry_modified.allSlotsForBr(i)
-    when (new_br_insert_onehot(i)) {
-      slot.valid := true.B
+    when(new_br_insert_onehot(i)) {
+      slot.valid  := true.B
       slot.offset := new_br_offset
-      slot.setLowerStatByTarget(io.start_addr, io.target, i == numBr-1)
+      slot.setLowerStatByTarget(io.start_addr, io.target, i == numBr - 1)
       old_entry_modified.always_taken(i) := true.B
-    }.elsewhen (new_br_offset > oe.allSlotsForBr(i).offset) {
+    }.elsewhen(new_br_offset > oe.allSlotsForBr(i).offset) {
       old_entry_modified.always_taken(i) := false.B
       // all other fields remain unchanged
     }.otherwise {
       // case i == 0, remain unchanged
       if (i != 0) {
-        val noNeedToMoveFromFormerSlot = (i == numBr-1).B && !oe.brSlots.last.valid
-        when (!noNeedToMoveFromFormerSlot) {
-          slot.fromAnotherSlot(oe.allSlotsForBr(i-1))
+        val noNeedToMoveFromFormerSlot = (i == numBr - 1).B && !oe.brSlots.last.valid
+        when(!noNeedToMoveFromFormerSlot) {
+          slot.fromAnotherSlot(oe.allSlotsForBr(i - 1))
           old_entry_modified.always_taken(i) := oe.always_taken(i)
         }
       }
@@ -350,51 +350,46 @@ class FTBEntryGen(implicit p: Parameters) extends XSModule with HasBackendRedire
   // 2. oe: | br | br |, new br could be anywhere between, thus new pft is the addr of either
   //        the previous last br or the new br
   val may_have_to_replace = oe.noEmptySlotForNewBr
-  val pft_need_to_change = is_new_br && may_have_to_replace
+  val pft_need_to_change  = is_new_br && may_have_to_replace
   // it should either be the given last br or the new br
-  when (pft_need_to_change) {
+  when(pft_need_to_change) {
     val new_pft_offset =
-      Mux(!new_br_insert_onehot.asUInt.orR,
-        new_br_offset, oe.allSlotsForBr.last.offset)
+      Mux(!new_br_insert_onehot.asUInt.orR, new_br_offset, oe.allSlotsForBr.last.offset)
 
     // set jmp to invalid
-    old_entry_modified.pftAddr := getLower(io.start_addr) + new_pft_offset
-    old_entry_modified.carry := (getLower(io.start_addr) +& new_pft_offset).head(1).asBool
+    old_entry_modified.pftAddr              := getLower(io.start_addr) + new_pft_offset
+    old_entry_modified.carry                := (getLower(io.start_addr) +& new_pft_offset).head(1).asBool
     old_entry_modified.last_may_be_rvi_call := false.B
-    old_entry_modified.isCall := false.B
-    old_entry_modified.isRet := false.B
-    old_entry_modified.isJalr := false.B
+    old_entry_modified.isCall               := false.B
+    old_entry_modified.isRet                := false.B
+    old_entry_modified.isJalr               := false.B
   }
 
   val old_entry_jmp_target_modified = WireInit(oe)
-  val old_target = oe.tailSlot.getTarget(io.start_addr) // may be wrong because we store only 20 lowest bits
+  val old_target      = oe.tailSlot.getTarget(io.start_addr) // may be wrong because we store only 20 lowest bits
   val old_tail_is_jmp = !oe.tailSlot.sharing
   val jalr_target_modified = cfi_is_jalr && (old_target =/= io.target) && old_tail_is_jmp // TODO: pass full jalr target
-  when (jalr_target_modified) {
+  when(jalr_target_modified) {
     old_entry_jmp_target_modified.setByJmpTarget(io.start_addr, io.target)
     old_entry_jmp_target_modified.always_taken := 0.U.asTypeOf(Vec(numBr, Bool()))
   }
 
-  val old_entry_always_taken = WireInit(oe)
+  val old_entry_always_taken    = WireInit(oe)
   val always_taken_modified_vec = Wire(Vec(numBr, Bool())) // whether modified or not
   for (i <- 0 until numBr) {
     old_entry_always_taken.always_taken(i) :=
       oe.always_taken(i) && io.cfiIndex.valid && oe.brValids(i) && io.cfiIndex.bits === oe.brOffset(i)
     always_taken_modified_vec(i) := oe.always_taken(i) && !old_entry_always_taken.always_taken(i)
   }
-  val always_taken_modified = always_taken_modified_vec.reduce(_||_)
-
-
+  val always_taken_modified = always_taken_modified_vec.reduce(_ || _)
 
   val derived_from_old_entry =
-    Mux(is_new_br, old_entry_modified,
-      Mux(jalr_target_modified, old_entry_jmp_target_modified, old_entry_always_taken))
-
+    Mux(is_new_br, old_entry_modified, Mux(jalr_target_modified, old_entry_jmp_target_modified, old_entry_always_taken))
 
   io.new_entry := Mux(!hit, init_entry, derived_from_old_entry)
 
   io.new_br_insert_pos := new_br_insert_onehot
-  io.taken_mask := VecInit((io.new_entry.brOffset zip io.new_entry.brValids).map{
+  io.taken_mask := VecInit((io.new_entry.brOffset zip io.new_entry.brValids).map {
     case (off, v) => io.cfiIndex.bits === off && io.cfiIndex.valid && v
   })
   io.jmp_taken := io.new_entry.jmpValid && io.new_entry.tailSlot.offset === io.cfiIndex.bits
@@ -404,23 +399,23 @@ class FTBEntryGen(implicit p: Parameters) extends XSModule with HasBackendRedire
   io.mispred_mask.last := io.new_entry.jmpValid && io.mispredict_vec(pd.jmpOffset)
 
   // for perf counters
-  io.is_init_entry := !hit
-  io.is_old_entry := hit && !is_new_br && !jalr_target_modified && !always_taken_modified
-  io.is_new_br := hit && is_new_br
-  io.is_jalr_target_modified := hit && jalr_target_modified
+  io.is_init_entry            := !hit
+  io.is_old_entry             := hit && !is_new_br && !jalr_target_modified && !always_taken_modified
+  io.is_new_br                := hit && is_new_br
+  io.is_jalr_target_modified  := hit && jalr_target_modified
   io.is_always_taken_modified := hit && always_taken_modified
-  io.is_br_full := hit && is_new_br && may_have_to_replace
+  io.is_br_full               := hit && is_new_br && may_have_to_replace
 }
 
 class FtqPcMemWrapper(numOtherReads: Int)(implicit p: Parameters) extends XSModule with HasBackendRedirectInfo {
   val io = IO(new Bundle {
-    val ifuPtr_w       = Input(new FtqPtr)
-    val ifuPtrPlus1_w  = Input(new FtqPtr)
-    val ifuPtrPlus2_w  = Input(new FtqPtr)
-    val pfPtr_w        = Input(new FtqPtr)
-    val pfPtrPlus1_w   = Input(new FtqPtr)
-    val commPtr_w      = Input(new FtqPtr)
-    val commPtrPlus1_w = Input(new FtqPtr)
+    val ifuPtr_w           = Input(new FtqPtr)
+    val ifuPtrPlus1_w      = Input(new FtqPtr)
+    val ifuPtrPlus2_w      = Input(new FtqPtr)
+    val pfPtr_w            = Input(new FtqPtr)
+    val pfPtrPlus1_w       = Input(new FtqPtr)
+    val commPtr_w          = Input(new FtqPtr)
+    val commPtrPlus1_w     = Input(new FtqPtr)
     val ifuPtr_rdata       = Output(new Ftq_RF_Components)
     val ifuPtrPlus1_rdata  = Output(new Ftq_RF_Components)
     val ifuPtrPlus2_rdata  = Output(new Ftq_RF_Components)
@@ -429,22 +424,27 @@ class FtqPcMemWrapper(numOtherReads: Int)(implicit p: Parameters) extends XSModu
     val commPtr_rdata      = Output(new Ftq_RF_Components)
     val commPtrPlus1_rdata = Output(new Ftq_RF_Components)
 
-    val wen = Input(Bool())
+    val wen   = Input(Bool())
     val waddr = Input(UInt(log2Ceil(FtqSize).W))
     val wdata = Input(new Ftq_RF_Components)
   })
 
   val num_pc_read = numOtherReads + 5
-  val mem = Module(new SyncDataModuleTemplate(new Ftq_RF_Components, FtqSize,
-    num_pc_read, 1, "FtqPC"))
+  val mem         = Module(new SyncDataModuleTemplate(new Ftq_RF_Components, FtqSize, num_pc_read, 1, "FtqPC"))
   mem.io.wen(0)   := io.wen
   mem.io.waddr(0) := io.waddr
   mem.io.wdata(0) := io.wdata
 
   // read one cycle ahead for ftq local reads
-  val raddr_vec = VecInit(Seq(io.ifuPtr_w.value, io.ifuPtrPlus1_w.value, io.ifuPtrPlus2_w.value,
-                              io.pfPtr_w.value, io.pfPtrPlus1_w.value,
-                              io.commPtrPlus1_w.value, io.commPtr_w.value))
+  val raddr_vec = VecInit(Seq(
+    io.ifuPtr_w.value,
+    io.ifuPtrPlus1_w.value,
+    io.ifuPtrPlus2_w.value,
+    io.pfPtr_w.value,
+    io.pfPtrPlus1_w.value,
+    io.commPtrPlus1_w.value,
+    io.commPtr_w.value
+  ))
 
   mem.io.raddr := raddr_vec
 
@@ -458,18 +458,18 @@ class FtqPcMemWrapper(numOtherReads: Int)(implicit p: Parameters) extends XSModu
 }
 
 class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper
-  with HasBackendRedirectInfo with BPUUtils with HasBPUConst with HasPerfEvents
-  with HasICacheParameters{
+    with HasBackendRedirectInfo with BPUUtils with HasBPUConst with HasPerfEvents
+    with HasICacheParameters {
   val io = IO(new Bundle {
-    val fromBpu = Flipped(new BpuToFtqIO)
-    val fromIfu = Flipped(new IfuToFtqIO)
+    val fromBpu     = Flipped(new BpuToFtqIO)
+    val fromIfu     = Flipped(new IfuToFtqIO)
     val fromBackend = Flipped(new CtrlToFtqIO)
 
-    val toBpu = new FtqToBpuIO
-    val toIfu = new FtqToIfuIO
-    val toICache = new FtqToICacheIO
-    val toBackend = new FtqToCtrlIO
-    val toPrefetch = new FtqToPrefetchIO
+    val toBpu       = new FtqToBpuIO
+    val toIfu       = new FtqToIfuIO
+    val toICache    = new FtqToICacheIO
+    val toBackend   = new FtqToCtrlIO
+    val toPrefetch  = new FtqToPrefetchIO
     val icacheFlush = Output(Bool())
 
     val bpuInfo = new Bundle {
@@ -481,53 +481,52 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
 
     // for perf
     val ControlBTBMissBubble = Output(Bool())
-    val TAGEMissBubble = Output(Bool())
-    val SCMissBubble = Output(Bool())
-    val ITTAGEMissBubble = Output(Bool())
-    val RASMissBubble = Output(Bool())
+    val TAGEMissBubble       = Output(Bool())
+    val SCMissBubble         = Output(Bool())
+    val ITTAGEMissBubble     = Output(Bool())
+    val RASMissBubble        = Output(Bool())
   })
   io.bpuInfo := DontCare
 
   val topdown_stage = RegInit(0.U.asTypeOf(new FrontendTopDownBundle))
   // only driven by clock, not valid-ready
-  topdown_stage := io.fromBpu.resp.bits.topdown_info
+  topdown_stage                  := io.fromBpu.resp.bits.topdown_info
   io.toIfu.req.bits.topdown_info := topdown_stage
 
   val ifuRedirected = RegInit(VecInit(Seq.fill(FtqSize)(false.B)))
 
-  
   // io.fromBackend.ftqIdxAhead: bju(BjuCnt) + ldReplay + exception
   val ftqIdxAhead = VecInit(Seq.tabulate(FtqRedirectAheadNum)(i => io.fromBackend.ftqIdxAhead(i))) // only bju
   val ftqIdxSelOH = io.fromBackend.ftqIdxSelOH.bits(FtqRedirectAheadNum - 1, 0)
 
-  val aheadValid   = ftqIdxAhead.map(_.valid).reduce(_|_) && !io.fromBackend.redirect.valid
-  val realAhdValid = io.fromBackend.redirect.valid && (ftqIdxSelOH > 0.U) && RegNext(aheadValid)
-  val backendRedirect = Wire(Valid(new BranchPredictionRedirect))
+  val aheadValid         = ftqIdxAhead.map(_.valid).reduce(_ | _) && !io.fromBackend.redirect.valid
+  val realAhdValid       = io.fromBackend.redirect.valid && (ftqIdxSelOH > 0.U) && RegNext(aheadValid)
+  val backendRedirect    = Wire(Valid(new BranchPredictionRedirect))
   val backendRedirectReg = Wire(Valid(new BranchPredictionRedirect))
   backendRedirectReg.valid := RegNext(Mux(realAhdValid, false.B, backendRedirect.valid))
-  backendRedirectReg.bits := RegEnable(backendRedirect.bits, backendRedirect.valid)
+  backendRedirectReg.bits  := RegEnable(backendRedirect.bits, backendRedirect.valid)
   val fromBackendRedirect = Wire(Valid(new BranchPredictionRedirect))
   fromBackendRedirect := Mux(realAhdValid, backendRedirect, backendRedirectReg)
 
-  val stage2Flush = backendRedirect.valid
+  val stage2Flush  = backendRedirect.valid
   val backendFlush = stage2Flush || RegNext(stage2Flush)
-  val ifuFlush = Wire(Bool())
+  val ifuFlush     = Wire(Bool())
 
   val flush = stage2Flush || RegNext(stage2Flush)
 
   val allowBpuIn, allowToIfu = WireInit(false.B)
-  val flushToIfu = !allowToIfu
+  val flushToIfu             = !allowToIfu
   allowBpuIn := !ifuFlush && !backendRedirect.valid && !backendRedirectReg.valid
   allowToIfu := !ifuFlush && !backendRedirect.valid && !backendRedirectReg.valid
 
-  def copyNum = 5
+  def copyNum                                              = 5
   val bpuPtr, ifuPtr, pfPtr, ifuWbPtr, commPtr, robCommPtr = RegInit(FtqPtr(false.B, 0.U))
-  val ifuPtrPlus1 = RegInit(FtqPtr(false.B, 1.U))
-  val ifuPtrPlus2 = RegInit(FtqPtr(false.B, 2.U))
-  val pfPtrPlus1  = RegInit(FtqPtr(false.B, 1.U))
-  val commPtrPlus1 = RegInit(FtqPtr(false.B, 1.U))
-  val copied_ifu_ptr = Seq.fill(copyNum)(RegInit(FtqPtr(false.B, 0.U)))
-  val copied_bpu_ptr = Seq.fill(copyNum)(RegInit(FtqPtr(false.B, 0.U)))
+  val ifuPtrPlus1                                          = RegInit(FtqPtr(false.B, 1.U))
+  val ifuPtrPlus2                                          = RegInit(FtqPtr(false.B, 2.U))
+  val pfPtrPlus1                                           = RegInit(FtqPtr(false.B, 1.U))
+  val commPtrPlus1                                         = RegInit(FtqPtr(false.B, 1.U))
+  val copied_ifu_ptr                                       = Seq.fill(copyNum)(RegInit(FtqPtr(false.B, 0.U)))
+  val copied_bpu_ptr                                       = Seq.fill(copyNum)(RegInit(FtqPtr(false.B, 0.U)))
   require(FtqSize >= 4)
   val ifuPtr_write       = WireInit(ifuPtr)
   val ifuPtrPlus1_write  = WireInit(ifuPtrPlus1)
@@ -546,34 +545,36 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   ifuWbPtr     := ifuWbPtr_write
   commPtr      := commPtr_write
   commPtrPlus1 := commPtrPlus1_write
-  copied_ifu_ptr.map{ptr =>
+  copied_ifu_ptr.map { ptr =>
     ptr := ifuPtr_write
     dontTouch(ptr)
   }
-  robCommPtr   := robCommPtr_write
+  robCommPtr := robCommPtr_write
   val validEntries = distanceBetween(bpuPtr, commPtr)
-  val canCommit = Wire(Bool())
+  val canCommit    = Wire(Bool())
 
   // Instruction page fault and instruction access fault are sent from backend with redirect requests.
   // When IPF and IAF are sent, backendPcFaultIfuPtr points to the FTQ entry whose first instruction
   // raises IPF or IAF, which is ifuWbPtr_write or IfuPtr_write.
   // Only when IFU has written back that FTQ entry can backendIpf and backendIaf be false because this
   // makes sure that IAF and IPF are correctly raised instead of being flushed by redirect requests.
-  val backendIpf = RegInit(false.B)
-  val backendIgpf = RegInit(false.B)
-  val backendIaf = RegInit(false.B)
+  val backendIpf        = RegInit(false.B)
+  val backendIgpf       = RegInit(false.B)
+  val backendIaf        = RegInit(false.B)
   val backendPcFaultPtr = RegInit(FtqPtr(false.B, 0.U))
-  when (fromBackendRedirect.valid) {
-    backendIpf := fromBackendRedirect.bits.cfiUpdate.backendIPF
+  when(fromBackendRedirect.valid) {
+    backendIpf  := fromBackendRedirect.bits.cfiUpdate.backendIPF
     backendIgpf := fromBackendRedirect.bits.cfiUpdate.backendIGPF
-    backendIaf := fromBackendRedirect.bits.cfiUpdate.backendIAF
-    when (fromBackendRedirect.bits.cfiUpdate.backendIPF || fromBackendRedirect.bits.cfiUpdate.backendIGPF || fromBackendRedirect.bits.cfiUpdate.backendIAF) {
+    backendIaf  := fromBackendRedirect.bits.cfiUpdate.backendIAF
+    when(
+      fromBackendRedirect.bits.cfiUpdate.backendIPF || fromBackendRedirect.bits.cfiUpdate.backendIGPF || fromBackendRedirect.bits.cfiUpdate.backendIAF
+    ) {
       backendPcFaultPtr := ifuWbPtr_write
     }
-  } .elsewhen (ifuWbPtr =/= backendPcFaultPtr) {
-    backendIpf := false.B
+  }.elsewhen(ifuWbPtr =/= backendPcFaultPtr) {
+    backendIpf  := false.B
     backendIgpf := false.B
-    backendIaf := false.B
+    backendIaf  := false.B
   }
 
   // **********************************************************************
@@ -582,67 +583,76 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   val new_entry_ready = validEntries < FtqSize.U || canCommit
   io.fromBpu.resp.ready := new_entry_ready
 
-  val bpu_s2_resp = io.fromBpu.resp.bits.s2
-  val bpu_s3_resp = io.fromBpu.resp.bits.s3
+  val bpu_s2_resp     = io.fromBpu.resp.bits.s2
+  val bpu_s3_resp     = io.fromBpu.resp.bits.s3
   val bpu_s2_redirect = bpu_s2_resp.valid(3) && bpu_s2_resp.hasRedirect(3)
   val bpu_s3_redirect = bpu_s3_resp.valid(3) && bpu_s3_resp.hasRedirect(3)
 
   io.toBpu.enq_ptr := bpuPtr
-  val enq_fire = io.fromBpu.resp.fire && allowBpuIn // from bpu s1
+  val enq_fire    = io.fromBpu.resp.fire && allowBpuIn // from bpu s1
   val bpu_in_fire = (io.fromBpu.resp.fire || bpu_s2_redirect || bpu_s3_redirect) && allowBpuIn
 
-  val bpu_in_resp = io.fromBpu.resp.bits.selectedResp
-  val bpu_in_stage = io.fromBpu.resp.bits.selectedRespIdxForFtq
+  val bpu_in_resp     = io.fromBpu.resp.bits.selectedResp
+  val bpu_in_stage    = io.fromBpu.resp.bits.selectedRespIdxForFtq
   val bpu_in_resp_ptr = Mux(bpu_in_stage === BP_S1, bpuPtr, bpu_in_resp.ftq_idx)
   val bpu_in_resp_idx = bpu_in_resp_ptr.value
 
   // read ports:      pfReq1 + pfReq2 ++  ifuReq1 + ifuReq2 + ifuReq3 + commitUpdate2 + commitUpdate
   val ftq_pc_mem = Module(new FtqPcMemWrapper(2))
   // resp from uBTB
-  ftq_pc_mem.io.wen := bpu_in_fire
+  ftq_pc_mem.io.wen   := bpu_in_fire
   ftq_pc_mem.io.waddr := bpu_in_resp_idx
   ftq_pc_mem.io.wdata.fromBranchPrediction(bpu_in_resp)
 
   //                                                            ifuRedirect + backendRedirect + commit
-  val ftq_redirect_mem = Module(new SyncDataModuleTemplate(new Ftq_Redirect_SRAMEntry,
-    FtqSize, IfuRedirectNum+FtqRedirectAheadNum+1, 1, hasRen = true))
+  val ftq_redirect_mem = Module(new SyncDataModuleTemplate(
+    new Ftq_Redirect_SRAMEntry,
+    FtqSize,
+    IfuRedirectNum + FtqRedirectAheadNum + 1,
+    1,
+    hasRen = true
+  ))
   // these info is intended to enq at the last stage of bpu
-  ftq_redirect_mem.io.wen(0) := io.fromBpu.resp.bits.lastStage.valid(3)
+  ftq_redirect_mem.io.wen(0)   := io.fromBpu.resp.bits.lastStage.valid(3)
   ftq_redirect_mem.io.waddr(0) := io.fromBpu.resp.bits.lastStage.ftq_idx.value
   ftq_redirect_mem.io.wdata(0) := io.fromBpu.resp.bits.last_stage_spec_info
   println(f"ftq redirect MEM: entry ${ftq_redirect_mem.io.wdata(0).getWidth} * ${FtqSize} * 3")
 
   val ftq_meta_1r_sram = Module(new FtqNRSRAM(new Ftq_1R_SRAMEntry, 1))
   // these info is intended to enq at the last stage of bpu
-  ftq_meta_1r_sram.io.wen := io.fromBpu.resp.bits.lastStage.valid(3)
-  ftq_meta_1r_sram.io.waddr := io.fromBpu.resp.bits.lastStage.ftq_idx.value
-  ftq_meta_1r_sram.io.wdata.meta := io.fromBpu.resp.bits.last_stage_meta
+  ftq_meta_1r_sram.io.wen             := io.fromBpu.resp.bits.lastStage.valid(3)
+  ftq_meta_1r_sram.io.waddr           := io.fromBpu.resp.bits.lastStage.ftq_idx.value
+  ftq_meta_1r_sram.io.wdata.meta      := io.fromBpu.resp.bits.last_stage_meta
   ftq_meta_1r_sram.io.wdata.ftb_entry := io.fromBpu.resp.bits.last_stage_ftb_entry
   //                                                            ifuRedirect + backendRedirect (commit moved to ftq_meta_1r_sram)
-  val ftb_entry_mem = Module(new SyncDataModuleTemplate(new FTBEntry_FtqMem,
-    FtqSize, IfuRedirectNum+FtqRedirectAheadNum, 1, hasRen = true))
-  ftb_entry_mem.io.wen(0) := io.fromBpu.resp.bits.lastStage.valid(3)
+  val ftb_entry_mem = Module(new SyncDataModuleTemplate(
+    new FTBEntry_FtqMem,
+    FtqSize,
+    IfuRedirectNum + FtqRedirectAheadNum,
+    1,
+    hasRen = true
+  ))
+  ftb_entry_mem.io.wen(0)   := io.fromBpu.resp.bits.lastStage.valid(3)
   ftb_entry_mem.io.waddr(0) := io.fromBpu.resp.bits.lastStage.ftq_idx.value
   ftb_entry_mem.io.wdata(0) := io.fromBpu.resp.bits.last_stage_ftb_entry
 
-
   // multi-write
   val update_target = Reg(Vec(FtqSize, UInt(VAddrBits.W))) // could be taken target or fallThrough //TODO: remove this
-  val newest_entry_target = Reg(UInt(VAddrBits.W))
+  val newest_entry_target          = Reg(UInt(VAddrBits.W))
   val newest_entry_target_modified = RegInit(false.B)
-  val newest_entry_ptr = Reg(new FtqPtr)
-  val newest_entry_ptr_modified = RegInit(false.B)
-  val cfiIndex_vec = Reg(Vec(FtqSize, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))))
-  val mispredict_vec = Reg(Vec(FtqSize, Vec(PredictWidth, Bool())))
-  val pred_stage = Reg(Vec(FtqSize, UInt(2.W)))
-  val pred_s1_cycle = if (!env.FPGAPlatform) Some(Reg(Vec(FtqSize, UInt(64.W)))) else None
+  val newest_entry_ptr             = Reg(new FtqPtr)
+  val newest_entry_ptr_modified    = RegInit(false.B)
+  val cfiIndex_vec                 = Reg(Vec(FtqSize, ValidUndirectioned(UInt(log2Ceil(PredictWidth).W))))
+  val mispredict_vec               = Reg(Vec(FtqSize, Vec(PredictWidth, Bool())))
+  val pred_stage                   = Reg(Vec(FtqSize, UInt(2.W)))
+  val pred_s1_cycle                = if (!env.FPGAPlatform) Some(Reg(Vec(FtqSize, UInt(64.W)))) else None
 
   val c_empty :: c_toCommit :: c_committed :: c_flushed :: Nil = Enum(4)
   val commitStateQueueReg = RegInit(VecInit(Seq.fill(FtqSize) {
     VecInit(Seq.fill(PredictWidth)(c_empty))
   }))
   val commitStateQueueEnable = WireInit(VecInit(Seq.fill(FtqSize)(false.B)))
-  val commitStateQueueNext = WireInit(commitStateQueueReg)
+  val commitStateQueueNext   = WireInit(commitStateQueueReg)
 
   for (f <- 0 until FtqSize) {
     when(commitStateQueueEnable(f)) {
@@ -651,17 +661,17 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   }
 
   val f_to_send :: f_sent :: Nil = Enum(2)
-  val entry_fetch_status = RegInit(VecInit(Seq.fill(FtqSize)(f_sent)))
+  val entry_fetch_status         = RegInit(VecInit(Seq.fill(FtqSize)(f_sent)))
 
   val h_not_hit :: h_false_hit :: h_hit :: Nil = Enum(3)
-  val entry_hit_status = RegInit(VecInit(Seq.fill(FtqSize)(h_not_hit)))
+  val entry_hit_status                         = RegInit(VecInit(Seq.fill(FtqSize)(h_not_hit)))
 
   // modify registers one cycle later to cut critical path
-  val last_cycle_bpu_in = RegNext(bpu_in_fire)
-  val last_cycle_bpu_in_ptr = RegEnable(bpu_in_resp_ptr, bpu_in_fire)
-  val last_cycle_bpu_in_idx = last_cycle_bpu_in_ptr.value
-  val last_cycle_bpu_target = RegEnable(bpu_in_resp.getTarget(3), bpu_in_fire)
-  val last_cycle_cfiIndex = RegEnable(bpu_in_resp.cfiIndex(3), bpu_in_fire)
+  val last_cycle_bpu_in       = RegNext(bpu_in_fire)
+  val last_cycle_bpu_in_ptr   = RegEnable(bpu_in_resp_ptr, bpu_in_fire)
+  val last_cycle_bpu_in_idx   = last_cycle_bpu_in_ptr.value
+  val last_cycle_bpu_target   = RegEnable(bpu_in_resp.getTarget(3), bpu_in_fire)
+  val last_cycle_cfiIndex     = RegEnable(bpu_in_resp.cfiIndex(3), bpu_in_fire)
   val last_cycle_bpu_in_stage = RegEnable(bpu_in_stage, bpu_in_fire)
 
   def extra_copyNum_for_commitStateQueue = 2
@@ -671,41 +681,41 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     VecInit(Seq.fill(extra_copyNum_for_commitStateQueue)(RegEnable(bpu_in_resp_ptr, bpu_in_fire)))
 
   newest_entry_target_modified := false.B
-  newest_entry_ptr_modified := false.B
-  when (last_cycle_bpu_in) {
+  newest_entry_ptr_modified    := false.B
+  when(last_cycle_bpu_in) {
     entry_fetch_status(last_cycle_bpu_in_idx) := f_to_send
-    cfiIndex_vec(last_cycle_bpu_in_idx) := last_cycle_cfiIndex
-    pred_stage(last_cycle_bpu_in_idx) := last_cycle_bpu_in_stage
+    cfiIndex_vec(last_cycle_bpu_in_idx)       := last_cycle_cfiIndex
+    pred_stage(last_cycle_bpu_in_idx)         := last_cycle_bpu_in_stage
 
     update_target(last_cycle_bpu_in_idx) := last_cycle_bpu_target // TODO: remove this
-    newest_entry_target_modified := true.B
-    newest_entry_target := last_cycle_bpu_target
-    newest_entry_ptr_modified := true.B
-    newest_entry_ptr := last_cycle_bpu_in_ptr
+    newest_entry_target_modified         := true.B
+    newest_entry_target                  := last_cycle_bpu_target
+    newest_entry_ptr_modified            := true.B
+    newest_entry_ptr                     := last_cycle_bpu_in_ptr
   }
 
   // reduce fanout by delay write for a cycle
-  when (RegNext(last_cycle_bpu_in)) {
+  when(RegNext(last_cycle_bpu_in)) {
     mispredict_vec(RegEnable(last_cycle_bpu_in_idx, last_cycle_bpu_in)) :=
       WireInit(VecInit(Seq.fill(PredictWidth)(false.B)))
   }
 
   // record s1 pred cycles
-  pred_s1_cycle.map(vec => {
-    when (bpu_in_fire && (bpu_in_stage === BP_S1)) {
+  pred_s1_cycle.map { vec =>
+    when(bpu_in_fire && (bpu_in_stage === BP_S1)) {
       vec(bpu_in_resp_ptr.value) := bpu_in_resp.full_pred(0).predCycle.getOrElse(0.U)
     }
-  })
+  }
 
   // reduce fanout using copied last_cycle_bpu_in and copied last_cycle_bpu_in_ptr
   val copied_last_cycle_bpu_in_for_ftq = copied_last_cycle_bpu_in.takeRight(extra_copyNum_for_commitStateQueue)
   copied_last_cycle_bpu_in_for_ftq.zip(copied_last_cycle_bpu_in_ptr_for_ftq).zipWithIndex.map {
     case ((in, ptr), i) =>
-      when (in) {
+      when(in) {
         val perSetEntries = FtqSize / extra_copyNum_for_commitStateQueue // 32
         require(FtqSize % extra_copyNum_for_commitStateQueue == 0)
         for (j <- 0 until perSetEntries) {
-          when (ptr.value === (i * perSetEntries + j).U) {
+          when(ptr.value === (i * perSetEntries + j).U) {
             commitStateQueueNext(i * perSetEntries + j) := VecInit(Seq.fill(PredictWidth)(c_empty))
             // Clock gating optimization, use 1 gate cell to control a row
             commitStateQueueEnable(i * perSetEntries + j) := true.B
@@ -716,56 +726,55 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
 
   bpuPtr := bpuPtr + enq_fire
   copied_bpu_ptr.map(_ := bpuPtr + enq_fire)
-  when (io.toIfu.req.fire && allowToIfu) {
-    ifuPtr_write := ifuPtrPlus1
+  when(io.toIfu.req.fire && allowToIfu) {
+    ifuPtr_write      := ifuPtrPlus1
     ifuPtrPlus1_write := ifuPtrPlus2
     ifuPtrPlus2_write := ifuPtrPlus2 + 1.U
   }
-  when (io.toPrefetch.req.fire && allowToIfu) {
-    pfPtr_write := pfPtrPlus1
+  when(io.toPrefetch.req.fire && allowToIfu) {
+    pfPtr_write      := pfPtrPlus1
     pfPtrPlus1_write := pfPtrPlus1 + 1.U
   }
 
   // only use ftb result to assign hit status
-  when (bpu_s2_resp.valid(3)) {
+  when(bpu_s2_resp.valid(3)) {
     entry_hit_status(bpu_s2_resp.ftq_idx.value) := Mux(bpu_s2_resp.full_pred(3).hit, h_hit, h_not_hit)
   }
 
-
-  io.toIfu.flushFromBpu.s2.valid := bpu_s2_redirect
-  io.toIfu.flushFromBpu.s2.bits := bpu_s2_resp.ftq_idx
+  io.toIfu.flushFromBpu.s2.valid      := bpu_s2_redirect
+  io.toIfu.flushFromBpu.s2.bits       := bpu_s2_resp.ftq_idx
   io.toPrefetch.flushFromBpu.s2.valid := bpu_s2_redirect
-  io.toPrefetch.flushFromBpu.s2.bits := bpu_s2_resp.ftq_idx
-  when (bpu_s2_redirect) {
+  io.toPrefetch.flushFromBpu.s2.bits  := bpu_s2_resp.ftq_idx
+  when(bpu_s2_redirect) {
     bpuPtr := bpu_s2_resp.ftq_idx + 1.U
     copied_bpu_ptr.map(_ := bpu_s2_resp.ftq_idx + 1.U)
     // only when ifuPtr runs ahead of bpu s2 resp should we recover it
-    when (!isBefore(ifuPtr, bpu_s2_resp.ftq_idx)) {
-      ifuPtr_write := bpu_s2_resp.ftq_idx
+    when(!isBefore(ifuPtr, bpu_s2_resp.ftq_idx)) {
+      ifuPtr_write      := bpu_s2_resp.ftq_idx
       ifuPtrPlus1_write := bpu_s2_resp.ftq_idx + 1.U
       ifuPtrPlus2_write := bpu_s2_resp.ftq_idx + 2.U
     }
-    when (!isBefore(pfPtr, bpu_s2_resp.ftq_idx)) {
-      pfPtr_write := bpu_s2_resp.ftq_idx
+    when(!isBefore(pfPtr, bpu_s2_resp.ftq_idx)) {
+      pfPtr_write      := bpu_s2_resp.ftq_idx
       pfPtrPlus1_write := bpu_s2_resp.ftq_idx + 1.U
     }
   }
 
-  io.toIfu.flushFromBpu.s3.valid := bpu_s3_redirect
-  io.toIfu.flushFromBpu.s3.bits := bpu_s3_resp.ftq_idx
+  io.toIfu.flushFromBpu.s3.valid      := bpu_s3_redirect
+  io.toIfu.flushFromBpu.s3.bits       := bpu_s3_resp.ftq_idx
   io.toPrefetch.flushFromBpu.s3.valid := bpu_s3_redirect
-  io.toPrefetch.flushFromBpu.s3.bits := bpu_s3_resp.ftq_idx
-  when (bpu_s3_redirect) {
+  io.toPrefetch.flushFromBpu.s3.bits  := bpu_s3_resp.ftq_idx
+  when(bpu_s3_redirect) {
     bpuPtr := bpu_s3_resp.ftq_idx + 1.U
     copied_bpu_ptr.map(_ := bpu_s3_resp.ftq_idx + 1.U)
     // only when ifuPtr runs ahead of bpu s2 resp should we recover it
-    when (!isBefore(ifuPtr, bpu_s3_resp.ftq_idx)) {
-      ifuPtr_write := bpu_s3_resp.ftq_idx
+    when(!isBefore(ifuPtr, bpu_s3_resp.ftq_idx)) {
+      ifuPtr_write      := bpu_s3_resp.ftq_idx
       ifuPtrPlus1_write := bpu_s3_resp.ftq_idx + 1.U
       ifuPtrPlus2_write := bpu_s3_resp.ftq_idx + 2.U
     }
-    when (!isBefore(pfPtr, bpu_s3_resp.ftq_idx)) {
-      pfPtr_write := bpu_s3_resp.ftq_idx
+    when(!isBefore(pfPtr, bpu_s3_resp.ftq_idx)) {
+      pfPtr_write      := bpu_s3_resp.ftq_idx
       pfPtrPlus1_write := bpu_s3_resp.ftq_idx + 1.U
     }
   }
@@ -774,22 +783,20 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   XSError(isBefore(bpuPtr, pfPtr) && !isFull(bpuPtr, pfPtr), "\npfPtr is before bpuPtr!\n")
   XSError(isBefore(ifuWbPtr, commPtr) && !isFull(ifuWbPtr, commPtr), "\ncommPtr is before ifuWbPtr!\n")
 
-  (0 until copyNum).map{i =>
-    XSError(copied_bpu_ptr(i) =/= bpuPtr, "\ncopiedBpuPtr is different from bpuPtr!\n")
-  }
+  (0 until copyNum).map(i => XSError(copied_bpu_ptr(i) =/= bpuPtr, "\ncopiedBpuPtr is different from bpuPtr!\n"))
 
   // ****************************************************************
   // **************************** to ifu ****************************
   // ****************************************************************
   // 0  for ifu, and 1-4 for ICache
-  val bpu_in_bypass_buf = RegEnable(ftq_pc_mem.io.wdata, bpu_in_fire)
-  val copied_bpu_in_bypass_buf = VecInit(Seq.fill(copyNum)(RegEnable(ftq_pc_mem.io.wdata, bpu_in_fire)))
+  val bpu_in_bypass_buf         = RegEnable(ftq_pc_mem.io.wdata, bpu_in_fire)
+  val copied_bpu_in_bypass_buf  = VecInit(Seq.fill(copyNum)(RegEnable(ftq_pc_mem.io.wdata, bpu_in_fire)))
   val bpu_in_bypass_buf_for_ifu = bpu_in_bypass_buf
-  val bpu_in_bypass_ptr = RegEnable(bpu_in_resp_ptr, bpu_in_fire)
-  val last_cycle_to_ifu_fire = RegNext(io.toIfu.req.fire)
-  val last_cycle_to_pf_fire = RegNext(io.toPrefetch.req.fire)
+  val bpu_in_bypass_ptr         = RegEnable(bpu_in_resp_ptr, bpu_in_fire)
+  val last_cycle_to_ifu_fire    = RegNext(io.toIfu.req.fire)
+  val last_cycle_to_pf_fire     = RegNext(io.toPrefetch.req.fire)
 
-  val copied_bpu_in_bypass_ptr = VecInit(Seq.fill(copyNum)(RegEnable(bpu_in_resp_ptr, bpu_in_fire)))
+  val copied_bpu_in_bypass_ptr      = VecInit(Seq.fill(copyNum)(RegEnable(bpu_in_resp_ptr, bpu_in_fire)))
   val copied_last_cycle_to_ifu_fire = VecInit(Seq.fill(copyNum)(RegNext(io.toIfu.req.fire)))
 
   // read pc and target
@@ -801,37 +808,40 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   ftq_pc_mem.io.commPtr_w      := commPtr_write
   ftq_pc_mem.io.commPtrPlus1_w := commPtrPlus1_write
 
-
   io.toIfu.req.bits.ftqIdx := ifuPtr
 
-  val toICachePcBundle = Wire(Vec(copyNum,new Ftq_RF_Components))
-  val toICacheEntryToSend = Wire(Vec(copyNum,Bool()))
-  val nextCycleToPrefetchPcBundle = Wire(new Ftq_RF_Components)
+  val toICachePcBundle               = Wire(Vec(copyNum, new Ftq_RF_Components))
+  val toICacheEntryToSend            = Wire(Vec(copyNum, Bool()))
+  val nextCycleToPrefetchPcBundle    = Wire(new Ftq_RF_Components)
   val nextCycleToPrefetchEntryToSend = Wire(Bool())
-  val toPrefetchPcBundle = RegNext(nextCycleToPrefetchPcBundle)
-  val toPrefetchEntryToSend = RegNext(nextCycleToPrefetchEntryToSend)
-  val toIfuPcBundle = Wire(new Ftq_RF_Components)
-  val entry_is_to_send = WireInit(entry_fetch_status(ifuPtr.value) === f_to_send)
-  val entry_ftq_offset = WireInit(cfiIndex_vec(ifuPtr.value))
-  val entry_next_addr  = Wire(UInt(VAddrBits.W))
+  val toPrefetchPcBundle             = RegNext(nextCycleToPrefetchPcBundle)
+  val toPrefetchEntryToSend          = RegNext(nextCycleToPrefetchEntryToSend)
+  val toIfuPcBundle                  = Wire(new Ftq_RF_Components)
+  val entry_is_to_send               = WireInit(entry_fetch_status(ifuPtr.value) === f_to_send)
+  val entry_ftq_offset               = WireInit(cfiIndex_vec(ifuPtr.value))
+  val entry_next_addr                = Wire(UInt(VAddrBits.W))
 
   val pc_mem_ifu_ptr_rdata   = VecInit(Seq.fill(copyNum)(RegNext(ftq_pc_mem.io.ifuPtr_rdata)))
   val pc_mem_ifu_plus1_rdata = VecInit(Seq.fill(copyNum)(RegNext(ftq_pc_mem.io.ifuPtrPlus1_rdata)))
-  val diff_entry_next_addr = WireInit(update_target(ifuPtr.value)) //TODO: remove this
-
-  val copied_ifu_plus1_to_send = VecInit(Seq.fill(copyNum)(RegNext(entry_fetch_status(ifuPtrPlus1.value) === f_to_send) || RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === (ifuPtrPlus1))))
-  val copied_ifu_ptr_to_send   = VecInit(Seq.fill(copyNum)(RegNext(entry_fetch_status(ifuPtr.value) === f_to_send) || RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr)))
-
-  for(i <- 0 until copyNum){
-    when(copied_last_cycle_bpu_in(i) && copied_bpu_in_bypass_ptr(i) === copied_ifu_ptr(i)){
-      toICachePcBundle(i) := copied_bpu_in_bypass_buf(i)
-      toICacheEntryToSend(i)   := true.B
-    }.elsewhen(copied_last_cycle_to_ifu_fire(i)){
-      toICachePcBundle(i) := pc_mem_ifu_plus1_rdata(i)
-      toICacheEntryToSend(i)   := copied_ifu_plus1_to_send(i)
-    }.otherwise{
-      toICachePcBundle(i) := pc_mem_ifu_ptr_rdata(i)
-      toICacheEntryToSend(i)   := copied_ifu_ptr_to_send(i)
+  val diff_entry_next_addr   = WireInit(update_target(ifuPtr.value)) // TODO: remove this
+
+  val copied_ifu_plus1_to_send = VecInit(Seq.fill(copyNum)(RegNext(
+    entry_fetch_status(ifuPtrPlus1.value) === f_to_send
+  ) || RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtrPlus1)))
+  val copied_ifu_ptr_to_send = VecInit(Seq.fill(copyNum)(RegNext(
+    entry_fetch_status(ifuPtr.value) === f_to_send
+  ) || RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr)))
+
+  for (i <- 0 until copyNum) {
+    when(copied_last_cycle_bpu_in(i) && copied_bpu_in_bypass_ptr(i) === copied_ifu_ptr(i)) {
+      toICachePcBundle(i)    := copied_bpu_in_bypass_buf(i)
+      toICacheEntryToSend(i) := true.B
+    }.elsewhen(copied_last_cycle_to_ifu_fire(i)) {
+      toICachePcBundle(i)    := pc_mem_ifu_plus1_rdata(i)
+      toICacheEntryToSend(i) := copied_ifu_plus1_to_send(i)
+    }.otherwise {
+      toICachePcBundle(i)    := pc_mem_ifu_ptr_rdata(i)
+      toICacheEntryToSend(i) := copied_ifu_ptr_to_send(i)
     }
   }
 
@@ -840,56 +850,58 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     nextCycleToPrefetchPcBundle    := ftq_pc_mem.io.wdata
     nextCycleToPrefetchEntryToSend := true.B
   }.elsewhen(io.toPrefetch.req.fire) {
-    nextCycleToPrefetchPcBundle    := ftq_pc_mem.io.pfPtrPlus1_rdata
+    nextCycleToPrefetchPcBundle := ftq_pc_mem.io.pfPtrPlus1_rdata
     nextCycleToPrefetchEntryToSend := entry_fetch_status(pfPtrPlus1.value) === f_to_send ||
       last_cycle_bpu_in && bpu_in_bypass_ptr === pfPtrPlus1
   }.otherwise {
-    nextCycleToPrefetchPcBundle    := ftq_pc_mem.io.pfPtr_rdata
+    nextCycleToPrefetchPcBundle := ftq_pc_mem.io.pfPtr_rdata
     nextCycleToPrefetchEntryToSend := entry_fetch_status(pfPtr.value) === f_to_send ||
       last_cycle_bpu_in && bpu_in_bypass_ptr === pfPtr // reduce potential bubbles
   }
 
   // TODO: reconsider target address bypass logic
-  when (last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr) {
-    toIfuPcBundle := bpu_in_bypass_buf_for_ifu
-    entry_is_to_send := true.B
-    entry_next_addr := last_cycle_bpu_target
-    entry_ftq_offset := last_cycle_cfiIndex
+  when(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr) {
+    toIfuPcBundle        := bpu_in_bypass_buf_for_ifu
+    entry_is_to_send     := true.B
+    entry_next_addr      := last_cycle_bpu_target
+    entry_ftq_offset     := last_cycle_cfiIndex
     diff_entry_next_addr := last_cycle_bpu_target // TODO: remove this
-  }.elsewhen (last_cycle_to_ifu_fire) {
+  }.elsewhen(last_cycle_to_ifu_fire) {
     toIfuPcBundle := RegNext(ftq_pc_mem.io.ifuPtrPlus1_rdata)
     entry_is_to_send := RegNext(entry_fetch_status(ifuPtrPlus1.value) === f_to_send) ||
-                        RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === (ifuPtrPlus1)) // reduce potential bubbles
-    entry_next_addr := Mux(last_cycle_bpu_in && bpu_in_bypass_ptr === (ifuPtrPlus1),
-                          bpu_in_bypass_buf_for_ifu.startAddr,
-                          Mux(ifuPtr === newest_entry_ptr,
-                            newest_entry_target,
-                            RegNext(ftq_pc_mem.io.ifuPtrPlus2_rdata.startAddr))) // ifuPtr+2
+      RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtrPlus1) // reduce potential bubbles
+    entry_next_addr := Mux(
+      last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtrPlus1,
+      bpu_in_bypass_buf_for_ifu.startAddr,
+      Mux(ifuPtr === newest_entry_ptr, newest_entry_target, RegNext(ftq_pc_mem.io.ifuPtrPlus2_rdata.startAddr))
+    ) // ifuPtr+2
   }.otherwise {
     toIfuPcBundle := RegNext(ftq_pc_mem.io.ifuPtr_rdata)
     entry_is_to_send := RegNext(entry_fetch_status(ifuPtr.value) === f_to_send) ||
-                        RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr) // reduce potential bubbles
-    entry_next_addr := Mux(last_cycle_bpu_in && bpu_in_bypass_ptr === (ifuPtrPlus1),
-                          bpu_in_bypass_buf_for_ifu.startAddr,
-                          Mux(ifuPtr === newest_entry_ptr,
-                            newest_entry_target,
-                            RegNext(ftq_pc_mem.io.ifuPtrPlus1_rdata.startAddr))) // ifuPtr+1
+      RegNext(last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtr) // reduce potential bubbles
+    entry_next_addr := Mux(
+      last_cycle_bpu_in && bpu_in_bypass_ptr === ifuPtrPlus1,
+      bpu_in_bypass_buf_for_ifu.startAddr,
+      Mux(ifuPtr === newest_entry_ptr, newest_entry_target, RegNext(ftq_pc_mem.io.ifuPtrPlus1_rdata.startAddr))
+    ) // ifuPtr+1
   }
 
-  io.toIfu.req.valid := entry_is_to_send && ifuPtr =/= bpuPtr
+  io.toIfu.req.valid              := entry_is_to_send && ifuPtr =/= bpuPtr
   io.toIfu.req.bits.nextStartAddr := entry_next_addr
-  io.toIfu.req.bits.ftqOffset := entry_ftq_offset
+  io.toIfu.req.bits.ftqOffset     := entry_ftq_offset
   io.toIfu.req.bits.fromFtqPcBundle(toIfuPcBundle)
 
   io.toICache.req.valid := entry_is_to_send && ifuPtr =/= bpuPtr
-  io.toICache.req.bits.readValid.zipWithIndex.map{case(copy, i) => copy := toICacheEntryToSend(i) && copied_ifu_ptr(i) =/= copied_bpu_ptr(i)}
-  io.toICache.req.bits.pcMemRead.zipWithIndex.foreach{case(copy,i) =>
+  io.toICache.req.bits.readValid.zipWithIndex.map { case (copy, i) =>
+    copy := toICacheEntryToSend(i) && copied_ifu_ptr(i) =/= copied_bpu_ptr(i)
+  }
+  io.toICache.req.bits.pcMemRead.zipWithIndex.foreach { case (copy, i) =>
     copy.fromFtqPcBundle(toICachePcBundle(i))
     copy.ftqIdx := ifuPtr
   }
-  io.toICache.req.bits.backendIpf := backendIpf && backendPcFaultPtr === ifuPtr
+  io.toICache.req.bits.backendIpf  := backendIpf && backendPcFaultPtr === ifuPtr
   io.toICache.req.bits.backendIgpf := backendIgpf && backendPcFaultPtr === ifuPtr
-  io.toICache.req.bits.backendIaf := backendIaf && backendPcFaultPtr === ifuPtr
+  io.toICache.req.bits.backendIaf  := backendIaf && backendPcFaultPtr === ifuPtr
 
   io.toPrefetch.req.valid := toPrefetchEntryToSend && pfPtr =/= bpuPtr
   io.toPrefetch.req.bits.fromFtqPcBundle(toPrefetchPcBundle)
@@ -901,96 +913,105 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // }
 
   // TODO: remove this
-  XSError(io.toIfu.req.valid && diff_entry_next_addr =/= entry_next_addr,
-          p"\nifu_req_target wrong! ifuPtr: ${ifuPtr}, entry_next_addr: ${Hexadecimal(entry_next_addr)} diff_entry_next_addr: ${Hexadecimal(diff_entry_next_addr)}\n")
+  XSError(
+    io.toIfu.req.valid && diff_entry_next_addr =/= entry_next_addr,
+    p"\nifu_req_target wrong! ifuPtr: ${ifuPtr}, entry_next_addr: ${Hexadecimal(entry_next_addr)} diff_entry_next_addr: ${Hexadecimal(diff_entry_next_addr)}\n"
+  )
 
   // when fall through is smaller in value than start address, there must be a false hit
-  when (toIfuPcBundle.fallThruError && entry_hit_status(ifuPtr.value) === h_hit) {
-    when (io.toIfu.req.fire &&
+  when(toIfuPcBundle.fallThruError && entry_hit_status(ifuPtr.value) === h_hit) {
+    when(io.toIfu.req.fire &&
       !(bpu_s2_redirect && bpu_s2_resp.ftq_idx === ifuPtr) &&
-      !(bpu_s3_redirect && bpu_s3_resp.ftq_idx === ifuPtr)
-    ) {
+      !(bpu_s3_redirect && bpu_s3_resp.ftq_idx === ifuPtr)) {
       entry_hit_status(ifuPtr.value) := h_false_hit
       // XSError(true.B, "FTB false hit by fallThroughError, startAddr: %x, fallTHru: %x\n", io.toIfu.req.bits.startAddr, io.toIfu.req.bits.nextStartAddr)
     }
-    XSDebug(true.B, "fallThruError! start:%x, fallThru:%x\n", io.toIfu.req.bits.startAddr, io.toIfu.req.bits.nextStartAddr)
+    XSDebug(
+      true.B,
+      "fallThruError! start:%x, fallThru:%x\n",
+      io.toIfu.req.bits.startAddr,
+      io.toIfu.req.bits.nextStartAddr
+    )
   }
 
-  XSPerfAccumulate(f"fall_through_error_to_ifu", toIfuPcBundle.fallThruError && entry_hit_status(ifuPtr.value) === h_hit &&
-    io.toIfu.req.fire && !(bpu_s2_redirect && bpu_s2_resp.ftq_idx === ifuPtr) && !(bpu_s3_redirect && bpu_s3_resp.ftq_idx === ifuPtr))
+  XSPerfAccumulate(
+    f"fall_through_error_to_ifu",
+    toIfuPcBundle.fallThruError && entry_hit_status(ifuPtr.value) === h_hit &&
+      io.toIfu.req.fire && !(bpu_s2_redirect && bpu_s2_resp.ftq_idx === ifuPtr) && !(bpu_s3_redirect && bpu_s3_resp.ftq_idx === ifuPtr)
+  )
 
   val ifu_req_should_be_flushed =
     io.toIfu.flushFromBpu.shouldFlushByStage2(io.toIfu.req.bits.ftqIdx) ||
-    io.toIfu.flushFromBpu.shouldFlushByStage3(io.toIfu.req.bits.ftqIdx)
+      io.toIfu.flushFromBpu.shouldFlushByStage3(io.toIfu.req.bits.ftqIdx)
 
-    when (io.toIfu.req.fire && !ifu_req_should_be_flushed) {
-      entry_fetch_status(ifuPtr.value) := f_sent
-    }
+  when(io.toIfu.req.fire && !ifu_req_should_be_flushed) {
+    entry_fetch_status(ifuPtr.value) := f_sent
+  }
 
   // *********************************************************************
   // **************************** wb from ifu ****************************
   // *********************************************************************
-  val pdWb = io.fromIfu.pdWb
-  val pds = pdWb.bits.pd
+  val pdWb         = io.fromIfu.pdWb
+  val pds          = pdWb.bits.pd
   val ifu_wb_valid = pdWb.valid
-  val ifu_wb_idx = pdWb.bits.ftqIdx.value
+  val ifu_wb_idx   = pdWb.bits.ftqIdx.value
   // read ports:                                                         commit update
-  val ftq_pd_mem = Module(new SyncDataModuleTemplate(new Ftq_pd_Entry, FtqSize, FtqRedirectAheadNum+1, 1, hasRen = true))
-  ftq_pd_mem.io.wen(0) := ifu_wb_valid
+  val ftq_pd_mem =
+    Module(new SyncDataModuleTemplate(new Ftq_pd_Entry, FtqSize, FtqRedirectAheadNum + 1, 1, hasRen = true))
+  ftq_pd_mem.io.wen(0)   := ifu_wb_valid
   ftq_pd_mem.io.waddr(0) := pdWb.bits.ftqIdx.value
   ftq_pd_mem.io.wdata(0).fromPdWb(pdWb.bits)
 
-  val hit_pd_valid = entry_hit_status(ifu_wb_idx) === h_hit && ifu_wb_valid
-  val hit_pd_mispred = hit_pd_valid && pdWb.bits.misOffset.valid
-  val hit_pd_mispred_reg = RegNext(hit_pd_mispred, init=false.B)
-  val pd_reg       = RegEnable(pds,             pdWb.valid)
-  val start_pc_reg = RegEnable(pdWb.bits.pc(0), pdWb.valid)
-  val wb_idx_reg   = RegEnable(ifu_wb_idx,      pdWb.valid)
+  val hit_pd_valid       = entry_hit_status(ifu_wb_idx) === h_hit && ifu_wb_valid
+  val hit_pd_mispred     = hit_pd_valid && pdWb.bits.misOffset.valid
+  val hit_pd_mispred_reg = RegNext(hit_pd_mispred, init = false.B)
+  val pd_reg             = RegEnable(pds, pdWb.valid)
+  val start_pc_reg       = RegEnable(pdWb.bits.pc(0), pdWb.valid)
+  val wb_idx_reg         = RegEnable(ifu_wb_idx, pdWb.valid)
 
-  when (ifu_wb_valid) {
-    val comm_stq_wen = VecInit(pds.map(_.valid).zip(pdWb.bits.instrRange).map{
+  when(ifu_wb_valid) {
+    val comm_stq_wen = VecInit(pds.map(_.valid).zip(pdWb.bits.instrRange).map {
       case (v, inRange) => v && inRange
     })
     commitStateQueueEnable(ifu_wb_idx) := true.B
     (commitStateQueueNext(ifu_wb_idx) zip comm_stq_wen).map {
       case (qe, v) => when(v) {
-        qe := c_toCommit
-      }
+          qe := c_toCommit
+        }
     }
   }
 
-  when (ifu_wb_valid) {
+  when(ifu_wb_valid) {
     ifuWbPtr_write := ifuWbPtr + 1.U
   }
 
   XSError(ifu_wb_valid && isAfter(pdWb.bits.ftqIdx, ifuPtr), "IFU returned a predecode before its req, check IFU")
 
   ftb_entry_mem.io.ren.get.head := ifu_wb_valid
-  ftb_entry_mem.io.raddr.head := ifu_wb_idx
+  ftb_entry_mem.io.raddr.head   := ifu_wb_idx
   val has_false_hit = WireInit(false.B)
-  when (RegNext(hit_pd_valid)) {
+  when(RegNext(hit_pd_valid)) {
     // check for false hit
     val pred_ftb_entry = ftb_entry_mem.io.rdata.head
-    val brSlots = pred_ftb_entry.brSlots
-    val tailSlot = pred_ftb_entry.tailSlot
+    val brSlots        = pred_ftb_entry.brSlots
+    val tailSlot       = pred_ftb_entry.tailSlot
     // we check cfis that bpu predicted
 
     // bpu predicted branches but denied by predecode
     val br_false_hit =
-      brSlots.map{
+      brSlots.map {
         s => s.valid && !(pd_reg(s.offset).valid && pd_reg(s.offset).isBr)
-      }.reduce(_||_) ||
-      (tailSlot.valid && pred_ftb_entry.tailSlot.sharing &&
-        !(pd_reg(tailSlot.offset).valid && pd_reg(tailSlot.offset).isBr))
+      }.reduce(_ || _) ||
+        (tailSlot.valid && pred_ftb_entry.tailSlot.sharing &&
+          !(pd_reg(tailSlot.offset).valid && pd_reg(tailSlot.offset).isBr))
 
     val jmpOffset = tailSlot.offset
-    val jmp_pd = pd_reg(jmpOffset)
+    val jmp_pd    = pd_reg(jmpOffset)
     val jal_false_hit = pred_ftb_entry.jmpValid &&
-      ((pred_ftb_entry.isJal  && !(jmp_pd.valid && jmp_pd.isJal)) ||
-       (pred_ftb_entry.isJalr && !(jmp_pd.valid && jmp_pd.isJalr)) ||
-       (pred_ftb_entry.isCall && !(jmp_pd.valid && jmp_pd.isCall)) ||
-       (pred_ftb_entry.isRet  && !(jmp_pd.valid && jmp_pd.isRet))
-      )
+      ((pred_ftb_entry.isJal && !(jmp_pd.valid && jmp_pd.isJal)) ||
+        (pred_ftb_entry.isJalr && !(jmp_pd.valid && jmp_pd.isJalr)) ||
+        (pred_ftb_entry.isCall && !(jmp_pd.valid && jmp_pd.isCall)) ||
+        (pred_ftb_entry.isRet && !(jmp_pd.valid && jmp_pd.isRet)))
 
     has_false_hit := br_false_hit || jal_false_hit || hit_pd_mispred_reg
     XSDebug(has_false_hit, "FTB false hit by br or jal or hit_pd, startAddr: %x\n", pdWb.bits.pc(0))
@@ -998,7 +1019,7 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     // assert(!has_false_hit)
   }
 
-  when (has_false_hit) {
+  when(has_false_hit) {
     entry_hit_status(wb_idx_reg) := h_false_hit
   }
 
@@ -1014,20 +1035,28 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   val ftq_pd_rdata = Wire(Vec(FtqRedirectAheadNum, new Ftq_pd_Entry))
   for (i <- 1 until FtqRedirectAheadNum) {
     ftq_redirect_mem.io.ren.get(i + IfuRedirectNum) := ftqIdxAhead(i).valid
-    ftq_redirect_mem.io.raddr(i + IfuRedirectNum) := ftqIdxAhead(i).bits.value
-    ftb_entry_mem.io.ren.get(i + IfuRedirectNum) := ftqIdxAhead(i).valid
-    ftb_entry_mem.io.raddr(i + IfuRedirectNum) := ftqIdxAhead(i).bits.value
+    ftq_redirect_mem.io.raddr(i + IfuRedirectNum)   := ftqIdxAhead(i).bits.value
+    ftb_entry_mem.io.ren.get(i + IfuRedirectNum)    := ftqIdxAhead(i).valid
+    ftb_entry_mem.io.raddr(i + IfuRedirectNum)      := ftqIdxAhead(i).bits.value
 
-    ftq_pd_mem.io.ren.get(i)  := ftqIdxAhead(i).valid
-    ftq_pd_mem.io.raddr(i)    := ftqIdxAhead(i).bits.value 
+    ftq_pd_mem.io.ren.get(i) := ftqIdxAhead(i).valid
+    ftq_pd_mem.io.raddr(i)   := ftqIdxAhead(i).bits.value
   }
   ftq_redirect_mem.io.ren.get(IfuRedirectNum) := Mux(aheadValid, ftqIdxAhead(0).valid, backendRedirect.valid)
-  ftq_redirect_mem.io.raddr(IfuRedirectNum) := Mux(aheadValid, ftqIdxAhead(0).bits.value, backendRedirect.bits.ftqIdx.value)
+  ftq_redirect_mem.io.raddr(IfuRedirectNum) := Mux(
+    aheadValid,
+    ftqIdxAhead(0).bits.value,
+    backendRedirect.bits.ftqIdx.value
+  )
   ftb_entry_mem.io.ren.get(IfuRedirectNum) := Mux(aheadValid, ftqIdxAhead(0).valid, backendRedirect.valid)
-  ftb_entry_mem.io.raddr(IfuRedirectNum) := Mux(aheadValid, ftqIdxAhead(0).bits.value, backendRedirect.bits.ftqIdx.value)
+  ftb_entry_mem.io.raddr(IfuRedirectNum) := Mux(
+    aheadValid,
+    ftqIdxAhead(0).bits.value,
+    backendRedirect.bits.ftqIdx.value
+  )
 
-  ftq_pd_mem.io.ren.get(0)  := Mux(aheadValid, ftqIdxAhead(0).valid, backendRedirect.valid)
-  ftq_pd_mem.io.raddr(0)    := Mux(aheadValid, ftqIdxAhead(0).bits.value, backendRedirect.bits.ftqIdx.value)
+  ftq_pd_mem.io.ren.get(0) := Mux(aheadValid, ftqIdxAhead(0).valid, backendRedirect.valid)
+  ftq_pd_mem.io.raddr(0)   := Mux(aheadValid, ftqIdxAhead(0).bits.value, backendRedirect.bits.ftqIdx.value)
 
   for (i <- 0 until FtqRedirectAheadNum) {
     ftq_redirect_rdata(i) := ftq_redirect_mem.io.rdata(i + IfuRedirectNum)
@@ -1035,13 +1064,13 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
 
     ftq_pd_rdata(i) := ftq_pd_mem.io.rdata(i)
   }
-  val stage3CfiInfo = Mux(realAhdValid, Mux1H(ftqIdxSelOH, ftq_redirect_rdata), ftq_redirect_mem.io.rdata(IfuRedirectNum))
-  val stage3PdInfo  = Mux(realAhdValid, Mux1H(ftqIdxSelOH, ftq_pd_rdata), ftq_pd_mem.io.rdata(0))
+  val stage3CfiInfo =
+    Mux(realAhdValid, Mux1H(ftqIdxSelOH, ftq_redirect_rdata), ftq_redirect_mem.io.rdata(IfuRedirectNum))
+  val stage3PdInfo       = Mux(realAhdValid, Mux1H(ftqIdxSelOH, ftq_pd_rdata), ftq_pd_mem.io.rdata(0))
   val backendRedirectCfi = fromBackendRedirect.bits.cfiUpdate
   backendRedirectCfi.fromFtqRedirectSram(stage3CfiInfo)
   backendRedirectCfi.pd := stage3PdInfo.toPd(fromBackendRedirect.bits.ftqOffset)
 
-
   val r_ftb_entry = Mux(realAhdValid, Mux1H(ftqIdxSelOH, ftb_redirect_rdata), ftb_entry_mem.io.rdata(IfuRedirectNum))
   val r_ftqOffset = fromBackendRedirect.bits.ftqOffset
 
@@ -1049,58 +1078,60 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   backendRedirectCfi.jr_hit := r_ftb_entry.isJalr && r_ftb_entry.tailSlot.offset === r_ftqOffset
   // FIXME: not portable
   val sc_disagree = stage3CfiInfo.sc_disagree.getOrElse(VecInit(Seq.fill(numBr)(false.B)))
-  backendRedirectCfi.sc_hit := backendRedirectCfi.br_hit && Mux(r_ftb_entry.brSlots(0).offset === r_ftqOffset,
-    sc_disagree(0), sc_disagree(1))
+  backendRedirectCfi.sc_hit := backendRedirectCfi.br_hit && Mux(
+    r_ftb_entry.brSlots(0).offset === r_ftqOffset,
+    sc_disagree(0),
+    sc_disagree(1)
+  )
 
-  when (entry_hit_status(fromBackendRedirect.bits.ftqIdx.value) === h_hit) {
+  when(entry_hit_status(fromBackendRedirect.bits.ftqIdx.value) === h_hit) {
     backendRedirectCfi.shift := PopCount(r_ftb_entry.getBrMaskByOffset(r_ftqOffset)) +&
       (backendRedirectCfi.pd.isBr && !r_ftb_entry.brIsSaved(r_ftqOffset) &&
-      !r_ftb_entry.newBrCanNotInsert(r_ftqOffset))
+        !r_ftb_entry.newBrCanNotInsert(r_ftqOffset))
 
     backendRedirectCfi.addIntoHist := backendRedirectCfi.pd.isBr && (r_ftb_entry.brIsSaved(r_ftqOffset) ||
-        !r_ftb_entry.newBrCanNotInsert(r_ftqOffset))
+      !r_ftb_entry.newBrCanNotInsert(r_ftqOffset))
   }.otherwise {
-    backendRedirectCfi.shift := (backendRedirectCfi.pd.isBr && backendRedirectCfi.taken).asUInt
+    backendRedirectCfi.shift       := (backendRedirectCfi.pd.isBr && backendRedirectCfi.taken).asUInt
     backendRedirectCfi.addIntoHist := backendRedirectCfi.pd.isBr.asUInt
   }
 
-
   // ***************************************************************************
   // **************************** redirect from ifu ****************************
   // ***************************************************************************
   val fromIfuRedirect = WireInit(0.U.asTypeOf(Valid(new BranchPredictionRedirect)))
-  fromIfuRedirect.valid := pdWb.valid && pdWb.bits.misOffset.valid && !backendFlush
-  fromIfuRedirect.bits.ftqIdx := pdWb.bits.ftqIdx
-  fromIfuRedirect.bits.ftqOffset := pdWb.bits.misOffset.bits
-  fromIfuRedirect.bits.level := RedirectLevel.flushAfter
+  fromIfuRedirect.valid              := pdWb.valid && pdWb.bits.misOffset.valid && !backendFlush
+  fromIfuRedirect.bits.ftqIdx        := pdWb.bits.ftqIdx
+  fromIfuRedirect.bits.ftqOffset     := pdWb.bits.misOffset.bits
+  fromIfuRedirect.bits.level         := RedirectLevel.flushAfter
   fromIfuRedirect.bits.BTBMissBubble := true.B
   fromIfuRedirect.bits.debugIsMemVio := false.B
-  fromIfuRedirect.bits.debugIsCtrl := false.B
+  fromIfuRedirect.bits.debugIsCtrl   := false.B
 
   val ifuRedirectCfiUpdate = fromIfuRedirect.bits.cfiUpdate
-  ifuRedirectCfiUpdate.pc := pdWb.bits.pc(pdWb.bits.misOffset.bits)
-  ifuRedirectCfiUpdate.pd := pdWb.bits.pd(pdWb.bits.misOffset.bits)
+  ifuRedirectCfiUpdate.pc        := pdWb.bits.pc(pdWb.bits.misOffset.bits)
+  ifuRedirectCfiUpdate.pd        := pdWb.bits.pd(pdWb.bits.misOffset.bits)
   ifuRedirectCfiUpdate.predTaken := cfiIndex_vec(pdWb.bits.ftqIdx.value).valid
-  ifuRedirectCfiUpdate.target := pdWb.bits.target
-  ifuRedirectCfiUpdate.taken := pdWb.bits.cfiOffset.valid
+  ifuRedirectCfiUpdate.target    := pdWb.bits.target
+  ifuRedirectCfiUpdate.taken     := pdWb.bits.cfiOffset.valid
   ifuRedirectCfiUpdate.isMisPred := pdWb.bits.misOffset.valid
 
-  val ifuRedirectReg = RegNextWithEnable(fromIfuRedirect, hasInit = true)
+  val ifuRedirectReg   = RegNextWithEnable(fromIfuRedirect, hasInit = true)
   val ifuRedirectToBpu = WireInit(ifuRedirectReg)
   ifuFlush := fromIfuRedirect.valid || ifuRedirectToBpu.valid
 
   ftq_redirect_mem.io.ren.get.head := fromIfuRedirect.valid
-  ftq_redirect_mem.io.raddr.head := fromIfuRedirect.bits.ftqIdx.value
+  ftq_redirect_mem.io.raddr.head   := fromIfuRedirect.bits.ftqIdx.value
 
   val toBpuCfi = ifuRedirectToBpu.bits.cfiUpdate
   toBpuCfi.fromFtqRedirectSram(ftq_redirect_mem.io.rdata.head)
-  when (ifuRedirectReg.bits.cfiUpdate.pd.isRet && ifuRedirectReg.bits.cfiUpdate.pd.valid) {
+  when(ifuRedirectReg.bits.cfiUpdate.pd.isRet && ifuRedirectReg.bits.cfiUpdate.pd.valid) {
     toBpuCfi.target := toBpuCfi.topAddr
   }
 
-  when (ifuRedirectReg.valid) {
+  when(ifuRedirectReg.valid) {
     ifuRedirected(ifuRedirectReg.bits.ftqIdx.value) := true.B
-  } .elsewhen(RegNext(pdWb.valid)) {
+  }.elsewhen(RegNext(pdWb.valid)) {
     // if pdWb and no redirect, set to false
     ifuRedirected(last_cycle_bpu_in_ptr.value) := false.B
   }
@@ -1109,14 +1140,14 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // ***************************** to backend *****************************
   // **********************************************************************
   // to backend pc mem / target
-  io.toBackend.pc_mem_wen := RegNext(last_cycle_bpu_in)
+  io.toBackend.pc_mem_wen   := RegNext(last_cycle_bpu_in)
   io.toBackend.pc_mem_waddr := RegEnable(last_cycle_bpu_in_idx, last_cycle_bpu_in)
   io.toBackend.pc_mem_wdata := RegEnable(bpu_in_bypass_buf_for_ifu, last_cycle_bpu_in)
 
   // num cycle is fixed
   val newest_entry_en: Bool = RegNext(last_cycle_bpu_in || backendRedirect.valid || ifuRedirectToBpu.valid)
-  io.toBackend.newest_entry_en := RegNext(newest_entry_en)
-  io.toBackend.newest_entry_ptr := RegEnable(newest_entry_ptr, newest_entry_en)
+  io.toBackend.newest_entry_en     := RegNext(newest_entry_en)
+  io.toBackend.newest_entry_ptr    := RegEnable(newest_entry_ptr, newest_entry_en)
   io.toBackend.newest_entry_target := RegEnable(newest_entry_target, newest_entry_en)
 
   // *********************************************************************
@@ -1127,37 +1158,37 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   backendRedirect.bits.connectRedirect(io.fromBackend.redirect.bits)
   backendRedirect.bits.BTBMissBubble := false.B
 
-
   def extractRedirectInfo(wb: Valid[Redirect]) = {
-    val ftqPtr = wb.bits.ftqIdx
+    val ftqPtr    = wb.bits.ftqIdx
     val ftqOffset = wb.bits.ftqOffset
-    val taken = wb.bits.cfiUpdate.taken
-    val mispred = wb.bits.cfiUpdate.isMisPred
+    val taken     = wb.bits.cfiUpdate.taken
+    val mispred   = wb.bits.cfiUpdate.isMisPred
     (wb.valid, ftqPtr, ftqOffset, taken, mispred)
   }
 
   // fix mispredict entry
   val lastIsMispredict = RegNext(
-    backendRedirect.valid && backendRedirect.bits.level === RedirectLevel.flushAfter, init = false.B
+    backendRedirect.valid && backendRedirect.bits.level === RedirectLevel.flushAfter,
+    init = false.B
   )
 
   def updateCfiInfo(redirect: Valid[Redirect], isBackend: Boolean = true) = {
     val (r_valid, r_ptr, r_offset, r_taken, r_mispred) = extractRedirectInfo(redirect)
-    val r_idx = r_ptr.value
-    val cfiIndex_bits_wen = r_valid && r_taken && r_offset < cfiIndex_vec(r_idx).bits
-    val cfiIndex_valid_wen = r_valid && r_offset === cfiIndex_vec(r_idx).bits
-    when (cfiIndex_bits_wen || cfiIndex_valid_wen) {
+    val r_idx                                          = r_ptr.value
+    val cfiIndex_bits_wen                              = r_valid && r_taken && r_offset < cfiIndex_vec(r_idx).bits
+    val cfiIndex_valid_wen                             = r_valid && r_offset === cfiIndex_vec(r_idx).bits
+    when(cfiIndex_bits_wen || cfiIndex_valid_wen) {
       cfiIndex_vec(r_idx).valid := cfiIndex_bits_wen || cfiIndex_valid_wen && r_taken
-    } .elsewhen (r_valid && !r_taken && r_offset =/= cfiIndex_vec(r_idx).bits) {
-      cfiIndex_vec(r_idx).valid :=false.B
+    }.elsewhen(r_valid && !r_taken && r_offset =/= cfiIndex_vec(r_idx).bits) {
+      cfiIndex_vec(r_idx).valid := false.B
     }
-    when (cfiIndex_bits_wen) {
+    when(cfiIndex_bits_wen) {
       cfiIndex_vec(r_idx).bits := r_offset
     }
     newest_entry_target_modified := true.B
-    newest_entry_target := redirect.bits.cfiUpdate.target
-    newest_entry_ptr_modified := true.B
-    newest_entry_ptr := r_ptr
+    newest_entry_target          := redirect.bits.cfiUpdate.target
+    newest_entry_ptr_modified    := true.B
+    newest_entry_ptr             := r_ptr
 
     update_target(r_idx) := redirect.bits.cfiUpdate.target // TODO: remove this
     if (isBackend) {
@@ -1167,47 +1198,46 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
 
   when(fromBackendRedirect.valid) {
     updateCfiInfo(fromBackendRedirect)
-  }.elsewhen (ifuRedirectToBpu.valid) {
-    updateCfiInfo(ifuRedirectToBpu, isBackend=false)
+  }.elsewhen(ifuRedirectToBpu.valid) {
+    updateCfiInfo(ifuRedirectToBpu, isBackend = false)
   }
 
-  when (fromBackendRedirect.valid) {
-    when (fromBackendRedirect.bits.ControlRedirectBubble) {
-      when (fromBackendRedirect.bits.ControlBTBMissBubble) {
-        topdown_stage.reasons(TopDownCounters.BTBMissBubble.id) := true.B
+  when(fromBackendRedirect.valid) {
+    when(fromBackendRedirect.bits.ControlRedirectBubble) {
+      when(fromBackendRedirect.bits.ControlBTBMissBubble) {
+        topdown_stage.reasons(TopDownCounters.BTBMissBubble.id)                  := true.B
         io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.BTBMissBubble.id) := true.B
-      } .elsewhen (fromBackendRedirect.bits.TAGEMissBubble) {
-        topdown_stage.reasons(TopDownCounters.TAGEMissBubble.id) := true.B
+      }.elsewhen(fromBackendRedirect.bits.TAGEMissBubble) {
+        topdown_stage.reasons(TopDownCounters.TAGEMissBubble.id)                  := true.B
         io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.TAGEMissBubble.id) := true.B
-      } .elsewhen (fromBackendRedirect.bits.SCMissBubble) {
-        topdown_stage.reasons(TopDownCounters.SCMissBubble.id) := true.B
+      }.elsewhen(fromBackendRedirect.bits.SCMissBubble) {
+        topdown_stage.reasons(TopDownCounters.SCMissBubble.id)                  := true.B
         io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.SCMissBubble.id) := true.B
-      } .elsewhen (fromBackendRedirect.bits.ITTAGEMissBubble) {
-        topdown_stage.reasons(TopDownCounters.ITTAGEMissBubble.id) := true.B
+      }.elsewhen(fromBackendRedirect.bits.ITTAGEMissBubble) {
+        topdown_stage.reasons(TopDownCounters.ITTAGEMissBubble.id)                  := true.B
         io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.ITTAGEMissBubble.id) := true.B
-      } .elsewhen (fromBackendRedirect.bits.RASMissBubble) {
-        topdown_stage.reasons(TopDownCounters.RASMissBubble.id) := true.B
+      }.elsewhen(fromBackendRedirect.bits.RASMissBubble) {
+        topdown_stage.reasons(TopDownCounters.RASMissBubble.id)                  := true.B
         io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.RASMissBubble.id) := true.B
       }
 
-
-    } .elsewhen (backendRedirect.bits.MemVioRedirectBubble) {
-      topdown_stage.reasons(TopDownCounters.MemVioRedirectBubble.id) := true.B
+    }.elsewhen(backendRedirect.bits.MemVioRedirectBubble) {
+      topdown_stage.reasons(TopDownCounters.MemVioRedirectBubble.id)                  := true.B
       io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.MemVioRedirectBubble.id) := true.B
-    } .otherwise {
-      topdown_stage.reasons(TopDownCounters.OtherRedirectBubble.id) := true.B
+    }.otherwise {
+      topdown_stage.reasons(TopDownCounters.OtherRedirectBubble.id)                  := true.B
       io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.OtherRedirectBubble.id) := true.B
     }
-  } .elsewhen (ifuRedirectReg.valid) {
-    topdown_stage.reasons(TopDownCounters.BTBMissBubble.id) := true.B
+  }.elsewhen(ifuRedirectReg.valid) {
+    topdown_stage.reasons(TopDownCounters.BTBMissBubble.id)                  := true.B
     io.toIfu.req.bits.topdown_info.reasons(TopDownCounters.BTBMissBubble.id) := true.B
   }
 
   io.ControlBTBMissBubble := fromBackendRedirect.bits.ControlBTBMissBubble
-  io.TAGEMissBubble := fromBackendRedirect.bits.TAGEMissBubble
-  io.SCMissBubble := fromBackendRedirect.bits.SCMissBubble
-  io.ITTAGEMissBubble := fromBackendRedirect.bits.ITTAGEMissBubble
-  io.RASMissBubble := fromBackendRedirect.bits.RASMissBubble
+  io.TAGEMissBubble       := fromBackendRedirect.bits.TAGEMissBubble
+  io.SCMissBubble         := fromBackendRedirect.bits.SCMissBubble
+  io.ITTAGEMissBubble     := fromBackendRedirect.bits.ITTAGEMissBubble
+  io.RASMissBubble        := fromBackendRedirect.bits.RASMissBubble
 
   // ***********************************************************************************
   // **************************** flush ptr and state queue ****************************
@@ -1216,41 +1246,40 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   val redirectVec = VecInit(backendRedirect, fromIfuRedirect)
 
   // when redirect, we should reset ptrs and status queues
-  io.icacheFlush := redirectVec.map(r => r.valid).reduce(_||_)
+  io.icacheFlush := redirectVec.map(r => r.valid).reduce(_ || _)
   XSPerfAccumulate("icacheFlushFromBackend", backendRedirect.valid)
   XSPerfAccumulate("icacheFlushFromIFU", fromIfuRedirect.valid)
-  when(redirectVec.map(r => r.valid).reduce(_||_)){
-    val r = PriorityMux(redirectVec.map(r => (r.valid -> r.bits)))
-    val notIfu = redirectVec.dropRight(1).map(r => r.valid).reduce(_||_)
+  when(redirectVec.map(r => r.valid).reduce(_ || _)) {
+    val r                          = PriorityMux(redirectVec.map(r => r.valid -> r.bits))
+    val notIfu                     = redirectVec.dropRight(1).map(r => r.valid).reduce(_ || _)
     val (idx, offset, flushItSelf) = (r.ftqIdx, r.ftqOffset, RedirectLevel.flushItself(r.level))
-    val next = idx + 1.U
+    val next                       = idx + 1.U
     bpuPtr := next
     copied_bpu_ptr.map(_ := next)
-    ifuPtr_write := next
-    ifuWbPtr_write := next
+    ifuPtr_write      := next
+    ifuWbPtr_write    := next
     ifuPtrPlus1_write := idx + 2.U
     ifuPtrPlus2_write := idx + 3.U
-    pfPtr_write := next
-    pfPtrPlus1_write := idx + 2.U
+    pfPtr_write       := next
+    pfPtrPlus1_write  := idx + 2.U
   }
-  when(RegNext(redirectVec.map(r => r.valid).reduce(_||_))){
-    val r = PriorityMux(redirectVec.map(r => (r.valid -> r.bits)))
-    val notIfu = redirectVec.dropRight(1).map(r => r.valid).reduce(_||_)
+  when(RegNext(redirectVec.map(r => r.valid).reduce(_ || _))) {
+    val r                          = PriorityMux(redirectVec.map(r => r.valid -> r.bits))
+    val notIfu                     = redirectVec.dropRight(1).map(r => r.valid).reduce(_ || _)
     val (idx, offset, flushItSelf) = (r.ftqIdx, r.ftqOffset, RedirectLevel.flushItself(r.level))
-    when (RegNext(notIfu)) {
+    when(RegNext(notIfu)) {
       commitStateQueueEnable(RegNext(idx.value)) := true.B
-      commitStateQueueNext(RegNext(idx.value)).zipWithIndex.foreach({ case (s, i) =>
+      commitStateQueueNext(RegNext(idx.value)).zipWithIndex.foreach { case (s, i) =>
         when(i.U > RegNext(offset)) {
           s := c_empty
         }
-        when (i.U === RegNext(offset) && RegNext(flushItSelf)) {
+        when(i.U === RegNext(offset) && RegNext(flushItSelf)) {
           s := c_flushed
         }
-      })
+      }
     }
   }
 
-
   // only the valid bit is actually needed
   io.toIfu.redirect.bits    := backendRedirect.bits
   io.toIfu.redirect.valid   := stage2Flush
@@ -1259,21 +1288,21 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // commit
   for (c <- io.fromBackend.rob_commits) {
     when(c.valid) {
-      commitStateQueueEnable(c.bits.ftqIdx.value) := true.B
+      commitStateQueueEnable(c.bits.ftqIdx.value)                 := true.B
       commitStateQueueNext(c.bits.ftqIdx.value)(c.bits.ftqOffset) := c_committed
       // TODO: remove this
       // For instruction fusions, we also update the next instruction
-      when (c.bits.commitType === 4.U) {
+      when(c.bits.commitType === 4.U) {
         commitStateQueueNext(c.bits.ftqIdx.value)(c.bits.ftqOffset + 1.U) := c_committed
       }.elsewhen(c.bits.commitType === 5.U) {
         commitStateQueueNext(c.bits.ftqIdx.value)(c.bits.ftqOffset + 2.U) := c_committed
       }.elsewhen(c.bits.commitType === 6.U) {
         val index = (c.bits.ftqIdx + 1.U).value
-        commitStateQueueEnable(index) := true.B
+        commitStateQueueEnable(index)  := true.B
         commitStateQueueNext(index)(0) := c_committed
       }.elsewhen(c.bits.commitType === 7.U) {
         val index = (c.bits.ftqIdx + 1.U).value
-        commitStateQueueEnable(index) := true.B
+        commitStateQueueEnable(index)  := true.B
         commitStateQueueNext(index)(1) := c_committed
       }
     }
@@ -1284,20 +1313,31 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // ****************************************************************
 
   io.toBpu.redirctFromIFU := ifuRedirectToBpu.valid
-  io.toBpu.redirect := Mux(fromBackendRedirect.valid, fromBackendRedirect, ifuRedirectToBpu)
-  val dummy_s1_pred_cycle_vec = VecInit(List.tabulate(FtqSize)(_=>0.U(64.W)))
-  val redirect_latency = GTimer() - pred_s1_cycle.getOrElse(dummy_s1_pred_cycle_vec)(io.toBpu.redirect.bits.ftqIdx.value) + 1.U
+  io.toBpu.redirect       := Mux(fromBackendRedirect.valid, fromBackendRedirect, ifuRedirectToBpu)
+  val dummy_s1_pred_cycle_vec = VecInit(List.tabulate(FtqSize)(_ => 0.U(64.W)))
+  val redirect_latency =
+    GTimer() - pred_s1_cycle.getOrElse(dummy_s1_pred_cycle_vec)(io.toBpu.redirect.bits.ftqIdx.value) + 1.U
   XSPerfHistogram("backend_redirect_latency", redirect_latency, fromBackendRedirect.valid, 0, 60, 1)
-  XSPerfHistogram("ifu_redirect_latency", redirect_latency, !fromBackendRedirect.valid && ifuRedirectToBpu.valid, 0, 60, 1)
+  XSPerfHistogram(
+    "ifu_redirect_latency",
+    redirect_latency,
+    !fromBackendRedirect.valid && ifuRedirectToBpu.valid,
+    0,
+    60,
+    1
+  )
 
-  XSError(io.toBpu.redirect.valid && isBefore(io.toBpu.redirect.bits.ftqIdx, commPtr), "Ftq received a redirect after its commit, check backend or replay")
+  XSError(
+    io.toBpu.redirect.valid && isBefore(io.toBpu.redirect.bits.ftqIdx, commPtr),
+    "Ftq received a redirect after its commit, check backend or replay"
+  )
 
   val may_have_stall_from_bpu = Wire(Bool())
-  val bpu_ftb_update_stall = RegInit(0.U(2.W)) // 2-cycle stall, so we need 3 states
+  val bpu_ftb_update_stall    = RegInit(0.U(2.W)) // 2-cycle stall, so we need 3 states
   may_have_stall_from_bpu := bpu_ftb_update_stall =/= 0.U
 
-  val validInstructions = commitStateQueueReg(commPtr.value).map(s => s === c_toCommit || s === c_committed)
-  val lastInstructionStatus = PriorityMux(validInstructions.reverse.zip(commitStateQueueReg(commPtr.value).reverse))
+  val validInstructions       = commitStateQueueReg(commPtr.value).map(s => s === c_toCommit || s === c_committed)
+  val lastInstructionStatus   = PriorityMux(validInstructions.reverse.zip(commitStateQueueReg(commPtr.value).reverse))
   val firstInstructionFlushed = commitStateQueueReg(commPtr.value)(0) === c_flushed
   canCommit := commPtr =/= ifuWbPtr && !may_have_stall_from_bpu &&
     (isAfter(robCommPtr, commPtr) ||
@@ -1307,11 +1347,14 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
       validInstructions.reduce(_ || _) && lastInstructionStatus === c_committed ||
       firstInstructionFlushed)
 
-  when (io.fromBackend.rob_commits.map(_.valid).reduce(_ | _)) {
-    robCommPtr_write := ParallelPriorityMux(io.fromBackend.rob_commits.map(_.valid).reverse, io.fromBackend.rob_commits.map(_.bits.ftqIdx).reverse)
-  } .elsewhen (isAfter(commPtr, robCommPtr)) {
+  when(io.fromBackend.rob_commits.map(_.valid).reduce(_ | _)) {
+    robCommPtr_write := ParallelPriorityMux(
+      io.fromBackend.rob_commits.map(_.valid).reverse,
+      io.fromBackend.rob_commits.map(_.bits.ftqIdx).reverse
+    )
+  }.elsewhen(isAfter(commPtr, robCommPtr)) {
     robCommPtr_write := commPtr
-  } .otherwise {
+  }.otherwise {
     robCommPtr_write := robCommPtr
   }
 
@@ -1328,61 +1371,64 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // commit reads
   val commit_pc_bundle = RegNext(ftq_pc_mem.io.commPtr_rdata)
   val commit_target =
-    Mux(RegNext(commPtr === newest_entry_ptr),
+    Mux(
+      RegNext(commPtr === newest_entry_ptr),
       RegEnable(newest_entry_target, newest_entry_target_modified),
-      RegNext(ftq_pc_mem.io.commPtrPlus1_rdata.startAddr))
+      RegNext(ftq_pc_mem.io.commPtrPlus1_rdata.startAddr)
+    )
   ftq_pd_mem.io.ren.get.last := canCommit
-  ftq_pd_mem.io.raddr.last := commPtr.value
+  ftq_pd_mem.io.raddr.last   := commPtr.value
   val commit_pd = ftq_pd_mem.io.rdata.last
   ftq_redirect_mem.io.ren.get.last := canCommit
-  ftq_redirect_mem.io.raddr.last := commPtr.value
+  ftq_redirect_mem.io.raddr.last   := commPtr.value
   val commit_spec_meta = ftq_redirect_mem.io.rdata.last
-  ftq_meta_1r_sram.io.ren(0) := canCommit
+  ftq_meta_1r_sram.io.ren(0)   := canCommit
   ftq_meta_1r_sram.io.raddr(0) := commPtr.value
-  val commit_meta = ftq_meta_1r_sram.io.rdata(0).meta
+  val commit_meta      = ftq_meta_1r_sram.io.rdata(0).meta
   val commit_ftb_entry = ftq_meta_1r_sram.io.rdata(0).ftb_entry
 
   // need one cycle to read mem and srams
   val do_commit_ptr = RegEnable(commPtr, canCommit)
-  val do_commit = RegNext(canCommit, init=false.B)
-  when (canMoveCommPtr) {
-    commPtr_write := commPtrPlus1
+  val do_commit     = RegNext(canCommit, init = false.B)
+  when(canMoveCommPtr) {
+    commPtr_write      := commPtrPlus1
     commPtrPlus1_write := commPtrPlus1 + 1.U
   }
-  val commit_state = RegEnable(commitStateQueueReg(commPtr.value), canCommit)
+  val commit_state   = RegEnable(commitStateQueueReg(commPtr.value), canCommit)
   val can_commit_cfi = WireInit(cfiIndex_vec(commPtr.value))
-  val do_commit_cfi = WireInit(cfiIndex_vec(do_commit_ptr.value))
+  val do_commit_cfi  = WireInit(cfiIndex_vec(do_commit_ptr.value))
   //
-  //when (commitStateQueue(commPtr.value)(can_commit_cfi.bits) =/= c_commited) {
+  // when (commitStateQueue(commPtr.value)(can_commit_cfi.bits) =/= c_commited) {
   //  can_commit_cfi.valid := false.B
-  //}
+  // }
   val commit_cfi = RegEnable(can_commit_cfi, canCommit)
-  val debug_cfi = commitStateQueueReg(do_commit_ptr.value)(do_commit_cfi.bits) =/= c_committed && do_commit_cfi.valid
+  val debug_cfi  = commitStateQueueReg(do_commit_ptr.value)(do_commit_cfi.bits) =/= c_committed && do_commit_cfi.valid
 
-  val commit_mispredict  : Vec[Bool] = VecInit((RegEnable(mispredict_vec(commPtr.value), canCommit) zip commit_state).map {
-    case (mis, state) => mis && state === c_committed
-  })
+  val commit_mispredict: Vec[Bool] =
+    VecInit((RegEnable(mispredict_vec(commPtr.value), canCommit) zip commit_state).map {
+      case (mis, state) => mis && state === c_committed
+    })
   val commit_instCommited: Vec[Bool] = VecInit(commit_state.map(_ === c_committed)) // [PredictWidth]
-  val can_commit_hit                 = entry_hit_status(commPtr.value)
-  val commit_hit                     = RegEnable(can_commit_hit, canCommit)
-  val diff_commit_target             = RegEnable(update_target(commPtr.value), canCommit) // TODO: remove this
-  val commit_stage                   = RegEnable(pred_stage(commPtr.value), canCommit)
-  val commit_valid                   = commit_hit === h_hit || commit_cfi.valid // hit or taken
+  val can_commit_hit     = entry_hit_status(commPtr.value)
+  val commit_hit         = RegEnable(can_commit_hit, canCommit)
+  val diff_commit_target = RegEnable(update_target(commPtr.value), canCommit) // TODO: remove this
+  val commit_stage       = RegEnable(pred_stage(commPtr.value), canCommit)
+  val commit_valid       = commit_hit === h_hit || commit_cfi.valid           // hit or taken
 
   val to_bpu_hit = can_commit_hit === h_hit || can_commit_hit === h_false_hit
-  switch (bpu_ftb_update_stall) {
-    is (0.U) {
-      when (can_commit_cfi.valid && !to_bpu_hit && canCommit) {
+  switch(bpu_ftb_update_stall) {
+    is(0.U) {
+      when(can_commit_cfi.valid && !to_bpu_hit && canCommit) {
         bpu_ftb_update_stall := 2.U // 2-cycle stall
       }
     }
-    is (2.U) {
+    is(2.U) {
       bpu_ftb_update_stall := 1.U
     }
-    is (1.U) {
+    is(1.U) {
       bpu_ftb_update_stall := 0.U
     }
-    is (3.U) {
+    is(3.U) {
       XSError(true.B, "bpu_ftb_update_stall should be 0, 1 or 2")
     }
   }
@@ -1394,7 +1440,7 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   val update_latency = GTimer() - pred_s1_cycle.getOrElse(dummy_s1_pred_cycle_vec)(do_commit_ptr.value) + 1.U
   XSPerfHistogram("bpu_update_latency", update_latency, io.toBpu.update.valid, 0, 64, 2)
 
-  io.toBpu.update := DontCare
+  io.toBpu.update       := DontCare
   io.toBpu.update.valid := commit_valid && do_commit
   val update = io.toBpu.update.bits
   update.false_hit   := commit_hit === h_false_hit
@@ -1406,7 +1452,7 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   update.spec_info   := commit_spec_meta
   XSError(commit_valid && do_commit && debug_cfi, "\ncommit cfi can be non c_commited\n")
 
-  val commit_real_hit = commit_hit === h_hit
+  val commit_real_hit  = commit_hit === h_hit
   val update_ftb_entry = update.ftb_entry
 
   val ftbEntryGen = Module(new FTBEntryGen).io
@@ -1424,10 +1470,10 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   update.old_entry         := ftbEntryGen.is_old_entry
   update.pred_hit          := commit_hit === h_hit || commit_hit === h_false_hit
   update.br_taken_mask     := ftbEntryGen.taken_mask
-  update.br_committed      := (ftbEntryGen.new_entry.brValids zip ftbEntryGen.new_entry.brOffset) map {
+  update.br_committed := (ftbEntryGen.new_entry.brValids zip ftbEntryGen.new_entry.brOffset) map {
     case (valid, offset) => valid && commit_instCommited(offset)
   }
-  update.jmp_taken         := ftbEntryGen.jmp_taken
+  update.jmp_taken := ftbEntryGen.jmp_taken
 
   // update.full_pred.fromFtbEntry(ftbEntryGen.new_entry, update.pc)
   // update.full_pred.jalr_target := commit_target
@@ -1440,13 +1486,13 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // **************************** commit perf counters ****************************
   // ******************************************************************************
 
-  val commit_inst_mask    = VecInit(commit_state.map(c => c === c_committed && do_commit)).asUInt
-  val commit_mispred_mask = commit_mispredict.asUInt
+  val commit_inst_mask        = VecInit(commit_state.map(c => c === c_committed && do_commit)).asUInt
+  val commit_mispred_mask     = commit_mispredict.asUInt
   val commit_not_mispred_mask = ~commit_mispred_mask
 
-  val commit_br_mask = commit_pd.brMask.asUInt
+  val commit_br_mask  = commit_pd.brMask.asUInt
   val commit_jmp_mask = UIntToOH(commit_pd.jmpOffset) & Fill(PredictWidth, commit_pd.jmpInfo.valid.asTypeOf(UInt(1.W)))
-  val commit_cfi_mask = (commit_br_mask | commit_jmp_mask)
+  val commit_cfi_mask = commit_br_mask | commit_jmp_mask
 
   val mbpInstrs = commit_inst_mask & commit_cfi_mask
 
@@ -1456,40 +1502,48 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   io.bpuInfo.bpRight := PopCount(mbpRights)
   io.bpuInfo.bpWrong := PopCount(mbpWrongs)
 
-  val hartId = p(XSCoreParamsKey).HartId
-  val isWriteFTQTable = Constantin.createRecord(s"isWriteFTQTable$hartId")
+  val hartId           = p(XSCoreParamsKey).HartId
+  val isWriteFTQTable  = Constantin.createRecord(s"isWriteFTQTable$hartId")
   val ftqBranchTraceDB = ChiselDB.createTable(s"FTQTable$hartId", new FtqDebugBundle)
   // Cfi Info
   for (i <- 0 until PredictWidth) {
-    val pc = commit_pc_bundle.startAddr + (i * instBytes).U
-    val v = commit_state(i) === c_committed
-    val isBr = commit_pd.brMask(i)
-    val isJmp = commit_pd.jmpInfo.valid && commit_pd.jmpOffset === i.U
-    val isCfi = isBr || isJmp
+    val pc      = commit_pc_bundle.startAddr + (i * instBytes).U
+    val v       = commit_state(i) === c_committed
+    val isBr    = commit_pd.brMask(i)
+    val isJmp   = commit_pd.jmpInfo.valid && commit_pd.jmpOffset === i.U
+    val isCfi   = isBr || isJmp
     val isTaken = commit_cfi.valid && commit_cfi.bits === i.U
     val misPred = commit_mispredict(i)
     // val ghist = commit_spec_meta.ghist.predHist
-    val histPtr = commit_spec_meta.histPtr
+    val histPtr   = commit_spec_meta.histPtr
     val predCycle = commit_meta(63, 0)
-    val target = commit_target
-
-    val brIdx = OHToUInt(Reverse(Cat(update_ftb_entry.brValids.zip(update_ftb_entry.brOffset).map{case(v, offset) => v && offset === i.U})))
-    val inFtbEntry = update_ftb_entry.brValids.zip(update_ftb_entry.brOffset).map{case(v, offset) => v && offset === i.U}.reduce(_||_)
-    val addIntoHist = ((commit_hit === h_hit) && inFtbEntry) || ((!(commit_hit === h_hit) && i.U === commit_cfi.bits && isBr && commit_cfi.valid))
-    XSDebug(v && do_commit && isCfi, p"cfi_update: isBr(${isBr}) pc(${Hexadecimal(pc)}) " +
-    p"taken(${isTaken}) mispred(${misPred}) cycle($predCycle) hist(${histPtr.value}) " +
-    p"startAddr(${Hexadecimal(commit_pc_bundle.startAddr)}) AddIntoHist(${addIntoHist}) " +
-    p"brInEntry(${inFtbEntry}) brIdx(${brIdx}) target(${Hexadecimal(target)})\n")
+    val target    = commit_target
+
+    val brIdx = OHToUInt(Reverse(Cat(update_ftb_entry.brValids.zip(update_ftb_entry.brOffset).map { case (v, offset) =>
+      v && offset === i.U
+    })))
+    val inFtbEntry = update_ftb_entry.brValids.zip(update_ftb_entry.brOffset).map { case (v, offset) =>
+      v && offset === i.U
+    }.reduce(_ || _)
+    val addIntoHist =
+      ((commit_hit === h_hit) && inFtbEntry) || (!(commit_hit === h_hit) && i.U === commit_cfi.bits && isBr && commit_cfi.valid)
+    XSDebug(
+      v && do_commit && isCfi,
+      p"cfi_update: isBr(${isBr}) pc(${Hexadecimal(pc)}) " +
+        p"taken(${isTaken}) mispred(${misPred}) cycle($predCycle) hist(${histPtr.value}) " +
+        p"startAddr(${Hexadecimal(commit_pc_bundle.startAddr)}) AddIntoHist(${addIntoHist}) " +
+        p"brInEntry(${inFtbEntry}) brIdx(${brIdx}) target(${Hexadecimal(target)})\n"
+    )
 
     val logbundle = Wire(new FtqDebugBundle)
-    logbundle.pc := pc
-    logbundle.target := target
-    logbundle.isBr := isBr
-    logbundle.isJmp := isJmp
-    logbundle.isCall := isJmp && commit_pd.hasCall
-    logbundle.isRet := isJmp && commit_pd.hasRet
-    logbundle.misPred := misPred
-    logbundle.isTaken := isTaken
+    logbundle.pc        := pc
+    logbundle.target    := target
+    logbundle.isBr      := isBr
+    logbundle.isJmp     := isJmp
+    logbundle.isCall    := isJmp && commit_pd.hasCall
+    logbundle.isRet     := isJmp && commit_pd.hasRet
+    logbundle.misPred   := misPred
+    logbundle.isTaken   := isTaken
     logbundle.predStage := commit_stage
 
     ftqBranchTraceDB.log(
@@ -1501,7 +1555,7 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
     )
   }
 
-  val enq = io.fromBpu.resp
+  val enq           = io.fromBpu.resp
   val perf_redirect = backendRedirect
 
   XSPerfAccumulate("entry", validEntries)
@@ -1515,27 +1569,25 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   XSPerfAccumulate("to_ifu_stall", io.toIfu.req.valid && !io.toIfu.req.ready)
   XSPerfAccumulate("from_bpu_real_bubble", !enq.valid && enq.ready && allowBpuIn)
   XSPerfAccumulate("bpu_to_ifu_bubble", bpuPtr === ifuPtr)
-  XSPerfAccumulate("bpu_to_ifu_bubble_when_ftq_full", (bpuPtr === ifuPtr) && isFull(bpuPtr, commPtr) && io.toIfu.req.ready)
+  XSPerfAccumulate(
+    "bpu_to_ifu_bubble_when_ftq_full",
+    (bpuPtr === ifuPtr) && isFull(bpuPtr, commPtr) && io.toIfu.req.ready
+  )
 
-  XSPerfAccumulate("redirectAhead_ValidNum", ftqIdxAhead.map(_.valid).reduce(_|_))
+  XSPerfAccumulate("redirectAhead_ValidNum", ftqIdxAhead.map(_.valid).reduce(_ | _))
   XSPerfAccumulate("fromBackendRedirect_ValidNum", io.fromBackend.redirect.valid)
   XSPerfAccumulate("toBpuRedirect_ValidNum", io.toBpu.redirect.valid)
 
   val from_bpu = io.fromBpu.resp.bits
-  val to_ifu = io.toIfu.req.bits
-
-
-  XSPerfHistogram("commit_num_inst", PopCount(commit_inst_mask), do_commit, 0, PredictWidth+1, 1)
-
-
+  val to_ifu   = io.toIfu.req.bits
 
+  XSPerfHistogram("commit_num_inst", PopCount(commit_inst_mask), do_commit, 0, PredictWidth + 1, 1)
 
   val commit_jal_mask  = UIntToOH(commit_pd.jmpOffset) & Fill(PredictWidth, commit_pd.hasJal.asTypeOf(UInt(1.W)))
   val commit_jalr_mask = UIntToOH(commit_pd.jmpOffset) & Fill(PredictWidth, commit_pd.hasJalr.asTypeOf(UInt(1.W)))
   val commit_call_mask = UIntToOH(commit_pd.jmpOffset) & Fill(PredictWidth, commit_pd.hasCall.asTypeOf(UInt(1.W)))
   val commit_ret_mask  = UIntToOH(commit_pd.jmpOffset) & Fill(PredictWidth, commit_pd.hasRet.asTypeOf(UInt(1.W)))
 
-
   val mbpBRights = mbpRights & commit_br_mask
   val mbpJRights = mbpRights & commit_jal_mask
   val mbpIRights = mbpRights & commit_jalr_mask
@@ -1550,16 +1602,15 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
 
   val commit_pred_stage = RegNext(pred_stage(commPtr.value))
 
-  def pred_stage_map(src: UInt, name: String) = {
+  def pred_stage_map(src: UInt, name: String) =
     (0 until numBpStages).map(i =>
-      f"${name}_stage_${i+1}" -> PopCount(src.asBools.map(_ && commit_pred_stage === BP_STAGES(i)))
-    ).foldLeft(Map[String, UInt]())(_+_)
-  }
+      f"${name}_stage_${i + 1}" -> PopCount(src.asBools.map(_ && commit_pred_stage === BP_STAGES(i)))
+    ).foldLeft(Map[String, UInt]())(_ + _)
 
-  val mispred_stage_map      = pred_stage_map(mbpWrongs,  "mispredict")
+  val mispred_stage_map      = pred_stage_map(mbpWrongs, "mispredict")
   val br_mispred_stage_map   = pred_stage_map(mbpBWrongs, "br_mispredict")
   val jalr_mispred_stage_map = pred_stage_map(mbpIWrongs, "jalr_mispredict")
-  val correct_stage_map      = pred_stage_map(mbpRights,  "correct")
+  val correct_stage_map      = pred_stage_map(mbpRights, "correct")
   val br_correct_stage_map   = pred_stage_map(mbpBRights, "br_correct")
   val jalr_correct_stage_map = pred_stage_map(mbpIRights, "jalr_correct")
 
@@ -1569,61 +1620,61 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   // assert(!ftb_false_hit)
   val ftb_hit = u(commit_hit === h_hit)
 
-  val ftb_new_entry = u(ftbEntryGen.is_init_entry)
-  val ftb_new_entry_only_br = ftb_new_entry && !update_ftb_entry.jmpValid
-  val ftb_new_entry_only_jmp = ftb_new_entry && !update_ftb_entry.brValids(0)
+  val ftb_new_entry                = u(ftbEntryGen.is_init_entry)
+  val ftb_new_entry_only_br        = ftb_new_entry && !update_ftb_entry.jmpValid
+  val ftb_new_entry_only_jmp       = ftb_new_entry && !update_ftb_entry.brValids(0)
   val ftb_new_entry_has_br_and_jmp = ftb_new_entry && update_ftb_entry.brValids(0) && update_ftb_entry.jmpValid
 
   val ftb_old_entry = u(ftbEntryGen.is_old_entry)
 
-  val ftb_modified_entry = u(ftbEntryGen.is_new_br || ftbEntryGen.is_jalr_target_modified || ftbEntryGen.is_always_taken_modified)
-  val ftb_modified_entry_new_br = u(ftbEntryGen.is_new_br)
-  val ftb_modified_entry_ifu_redirected = u(ifuRedirected(do_commit_ptr.value))
+  val ftb_modified_entry =
+    u(ftbEntryGen.is_new_br || ftbEntryGen.is_jalr_target_modified || ftbEntryGen.is_always_taken_modified)
+  val ftb_modified_entry_new_br               = u(ftbEntryGen.is_new_br)
+  val ftb_modified_entry_ifu_redirected       = u(ifuRedirected(do_commit_ptr.value))
   val ftb_modified_entry_jalr_target_modified = u(ftbEntryGen.is_jalr_target_modified)
-  val ftb_modified_entry_br_full = ftb_modified_entry && ftbEntryGen.is_br_full
-  val ftb_modified_entry_always_taken = ftb_modified_entry && ftbEntryGen.is_always_taken_modified
+  val ftb_modified_entry_br_full              = ftb_modified_entry && ftbEntryGen.is_br_full
+  val ftb_modified_entry_always_taken         = ftb_modified_entry && ftbEntryGen.is_always_taken_modified
 
   def getFtbEntryLen(pc: UInt, entry: FTBEntry) = (entry.getFallThrough(pc) - pc) >> instOffsetBits
   val gen_ftb_entry_len = getFtbEntryLen(update.pc, ftbEntryGen.new_entry)
-  XSPerfHistogram("ftb_init_entry_len", gen_ftb_entry_len, ftb_new_entry, 0, PredictWidth+1, 1)
-  XSPerfHistogram("ftb_modified_entry_len", gen_ftb_entry_len, ftb_modified_entry, 0, PredictWidth+1, 1)
+  XSPerfHistogram("ftb_init_entry_len", gen_ftb_entry_len, ftb_new_entry, 0, PredictWidth + 1, 1)
+  XSPerfHistogram("ftb_modified_entry_len", gen_ftb_entry_len, ftb_modified_entry, 0, PredictWidth + 1, 1)
   val s3_ftb_entry_len = getFtbEntryLen(from_bpu.s3.pc(0), from_bpu.last_stage_ftb_entry)
-  XSPerfHistogram("s3_ftb_entry_len", s3_ftb_entry_len, from_bpu.s3.valid(0), 0, PredictWidth+1, 1)
+  XSPerfHistogram("s3_ftb_entry_len", s3_ftb_entry_len, from_bpu.s3.valid(0), 0, PredictWidth + 1, 1)
 
-  XSPerfHistogram("ftq_has_entry", validEntries, true.B, 0, FtqSize+1, 1)
+  XSPerfHistogram("ftq_has_entry", validEntries, true.B, 0, FtqSize + 1, 1)
 
   val perfCountsMap = Map(
-    "BpInstr" -> PopCount(mbpInstrs),
-    "BpBInstr" -> PopCount(mbpBRights | mbpBWrongs),
-    "BpRight"  -> PopCount(mbpRights),
-    "BpWrong"  -> PopCount(mbpWrongs),
-    "BpBRight" -> PopCount(mbpBRights),
-    "BpBWrong" -> PopCount(mbpBWrongs),
-    "BpJRight" -> PopCount(mbpJRights),
-    "BpJWrong" -> PopCount(mbpJWrongs),
-    "BpIRight" -> PopCount(mbpIRights),
-    "BpIWrong" -> PopCount(mbpIWrongs),
-    "BpCRight" -> PopCount(mbpCRights),
-    "BpCWrong" -> PopCount(mbpCWrongs),
-    "BpRRight" -> PopCount(mbpRRights),
-    "BpRWrong" -> PopCount(mbpRWrongs),
-
-    "ftb_false_hit"                -> PopCount(ftb_false_hit),
-    "ftb_hit"                      -> PopCount(ftb_hit),
-    "ftb_new_entry"                -> PopCount(ftb_new_entry),
-    "ftb_new_entry_only_br"        -> PopCount(ftb_new_entry_only_br),
-    "ftb_new_entry_only_jmp"       -> PopCount(ftb_new_entry_only_jmp),
-    "ftb_new_entry_has_br_and_jmp" -> PopCount(ftb_new_entry_has_br_and_jmp),
-    "ftb_old_entry"                -> PopCount(ftb_old_entry),
-    "ftb_modified_entry"           -> PopCount(ftb_modified_entry),
-    "ftb_modified_entry_new_br"    -> PopCount(ftb_modified_entry_new_br),
-    "ftb_jalr_target_modified"     -> PopCount(ftb_modified_entry_jalr_target_modified),
-    "ftb_modified_entry_br_full"   -> PopCount(ftb_modified_entry_br_full),
+    "BpInstr"                         -> PopCount(mbpInstrs),
+    "BpBInstr"                        -> PopCount(mbpBRights | mbpBWrongs),
+    "BpRight"                         -> PopCount(mbpRights),
+    "BpWrong"                         -> PopCount(mbpWrongs),
+    "BpBRight"                        -> PopCount(mbpBRights),
+    "BpBWrong"                        -> PopCount(mbpBWrongs),
+    "BpJRight"                        -> PopCount(mbpJRights),
+    "BpJWrong"                        -> PopCount(mbpJWrongs),
+    "BpIRight"                        -> PopCount(mbpIRights),
+    "BpIWrong"                        -> PopCount(mbpIWrongs),
+    "BpCRight"                        -> PopCount(mbpCRights),
+    "BpCWrong"                        -> PopCount(mbpCWrongs),
+    "BpRRight"                        -> PopCount(mbpRRights),
+    "BpRWrong"                        -> PopCount(mbpRWrongs),
+    "ftb_false_hit"                   -> PopCount(ftb_false_hit),
+    "ftb_hit"                         -> PopCount(ftb_hit),
+    "ftb_new_entry"                   -> PopCount(ftb_new_entry),
+    "ftb_new_entry_only_br"           -> PopCount(ftb_new_entry_only_br),
+    "ftb_new_entry_only_jmp"          -> PopCount(ftb_new_entry_only_jmp),
+    "ftb_new_entry_has_br_and_jmp"    -> PopCount(ftb_new_entry_has_br_and_jmp),
+    "ftb_old_entry"                   -> PopCount(ftb_old_entry),
+    "ftb_modified_entry"              -> PopCount(ftb_modified_entry),
+    "ftb_modified_entry_new_br"       -> PopCount(ftb_modified_entry_new_br),
+    "ftb_jalr_target_modified"        -> PopCount(ftb_modified_entry_jalr_target_modified),
+    "ftb_modified_entry_br_full"      -> PopCount(ftb_modified_entry_br_full),
     "ftb_modified_entry_always_taken" -> PopCount(ftb_modified_entry_always_taken)
   ) ++ mispred_stage_map ++ br_mispred_stage_map ++ jalr_mispred_stage_map ++
-       correct_stage_map ++ br_correct_stage_map ++ jalr_correct_stage_map
+    correct_stage_map ++ br_correct_stage_map ++ jalr_correct_stage_map
 
-  for((key, value) <- perfCountsMap) {
+  for ((key, value) <- perfCountsMap) {
     XSPerfAccumulate(key, value)
   }
 
@@ -1632,8 +1683,11 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   XSDebug(io.toIfu.req.fire, p"fire to ifu " + io.toIfu.req.bits.toPrintable)
   XSDebug(do_commit, p"deq! [ptr] $do_commit_ptr\n")
   XSDebug(true.B, p"[bpuPtr] $bpuPtr, [ifuPtr] $ifuPtr, [ifuWbPtr] $ifuWbPtr [commPtr] $commPtr\n")
-  XSDebug(true.B, p"[in] v:${io.fromBpu.resp.valid} r:${io.fromBpu.resp.ready} " +
-    p"[out] v:${io.toIfu.req.valid} r:${io.toIfu.req.ready}\n")
+  XSDebug(
+    true.B,
+    p"[in] v:${io.fromBpu.resp.valid} r:${io.fromBpu.resp.ready} " +
+      p"[out] v:${io.toIfu.req.valid} r:${io.toIfu.req.ready}\n"
+  )
   XSDebug(do_commit, p"[deq info] cfiIndex: $commit_cfi, $commit_pc_bundle, target: ${Hexadecimal(commit_target)}\n")
 
   //   def ubtbCheck(commit: FtqEntry, predAns: Seq[PredictorAnswer], isWrong: Bool) = {
@@ -1702,30 +1756,30 @@ class Ftq(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelpe
   //   val rasWrongs = rasCheck(commitEntry, commitEntry.metas.map(_.rasAns), true.B)
 
   val perfEvents = Seq(
-    ("bpu_s2_redirect        ", bpu_s2_redirect                                                             ),
-    ("bpu_s3_redirect        ", bpu_s3_redirect                                                             ),
-    ("bpu_to_ftq_stall       ", enq.valid && ~enq.ready                                                     ),
+    ("bpu_s2_redirect        ", bpu_s2_redirect),
+    ("bpu_s3_redirect        ", bpu_s3_redirect),
+    ("bpu_to_ftq_stall       ", enq.valid && ~enq.ready),
     ("mispredictRedirect     ", perf_redirect.valid && RedirectLevel.flushAfter === perf_redirect.bits.level),
-    ("replayRedirect         ", perf_redirect.valid && RedirectLevel.flushItself(perf_redirect.bits.level)  ),
-    ("predecodeRedirect      ", fromIfuRedirect.valid                                                       ),
-    ("to_ifu_bubble          ", io.toIfu.req.ready && !io.toIfu.req.valid                                   ),
-    ("from_bpu_real_bubble   ", !enq.valid && enq.ready && allowBpuIn                                       ),
-    ("BpInstr                ", PopCount(mbpInstrs)                                                         ),
-    ("BpBInstr               ", PopCount(mbpBRights | mbpBWrongs)                                           ),
-    ("BpRight                ", PopCount(mbpRights)                                                         ),
-    ("BpWrong                ", PopCount(mbpWrongs)                                                         ),
-    ("BpBRight               ", PopCount(mbpBRights)                                                        ),
-    ("BpBWrong               ", PopCount(mbpBWrongs)                                                        ),
-    ("BpJRight               ", PopCount(mbpJRights)                                                        ),
-    ("BpJWrong               ", PopCount(mbpJWrongs)                                                        ),
-    ("BpIRight               ", PopCount(mbpIRights)                                                        ),
-    ("BpIWrong               ", PopCount(mbpIWrongs)                                                        ),
-    ("BpCRight               ", PopCount(mbpCRights)                                                        ),
-    ("BpCWrong               ", PopCount(mbpCWrongs)                                                        ),
-    ("BpRRight               ", PopCount(mbpRRights)                                                        ),
-    ("BpRWrong               ", PopCount(mbpRWrongs)                                                        ),
-    ("ftb_false_hit          ", PopCount(ftb_false_hit)                                                     ),
-    ("ftb_hit                ", PopCount(ftb_hit)                                                           ),
+    ("replayRedirect         ", perf_redirect.valid && RedirectLevel.flushItself(perf_redirect.bits.level)),
+    ("predecodeRedirect      ", fromIfuRedirect.valid),
+    ("to_ifu_bubble          ", io.toIfu.req.ready && !io.toIfu.req.valid),
+    ("from_bpu_real_bubble   ", !enq.valid && enq.ready && allowBpuIn),
+    ("BpInstr                ", PopCount(mbpInstrs)),
+    ("BpBInstr               ", PopCount(mbpBRights | mbpBWrongs)),
+    ("BpRight                ", PopCount(mbpRights)),
+    ("BpWrong                ", PopCount(mbpWrongs)),
+    ("BpBRight               ", PopCount(mbpBRights)),
+    ("BpBWrong               ", PopCount(mbpBWrongs)),
+    ("BpJRight               ", PopCount(mbpJRights)),
+    ("BpJWrong               ", PopCount(mbpJWrongs)),
+    ("BpIRight               ", PopCount(mbpIRights)),
+    ("BpIWrong               ", PopCount(mbpIWrongs)),
+    ("BpCRight               ", PopCount(mbpCRights)),
+    ("BpCWrong               ", PopCount(mbpCWrongs)),
+    ("BpRRight               ", PopCount(mbpRRights)),
+    ("BpRWrong               ", PopCount(mbpRWrongs)),
+    ("ftb_false_hit          ", PopCount(ftb_false_hit)),
+    ("ftb_hit                ", PopCount(ftb_hit))
   )
   generatePerfEvent()
 }
diff --git a/src/main/scala/xiangshan/frontend/PreDecode.scala b/src/main/scala/xiangshan/frontend/PreDecode.scala
index 485321754ef..2600a25d648 100644
--- a/src/main/scala/xiangshan/frontend/PreDecode.scala
+++ b/src/main/scala/xiangshan/frontend/PreDecode.scala
@@ -16,40 +16,42 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
-import freechips.rocketchip.rocket.{RVCDecoder, ExpandedInstruction}
-import chisel3.{util, _}
+import chisel3._
+import chisel3.util
 import chisel3.util._
-import utils._
+import freechips.rocketchip.rocket.ExpandedInstruction
+import freechips.rocketchip.rocket.RVCDecoder
+import java.lang.reflect.Parameter
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
 import xiangshan._
-import xiangshan.frontend.icache._
 import xiangshan.backend.decode.isa.predecode.PreDecodeInst
 import xiangshan.backend.fu.NewCSR.TriggerUtil
-import java.lang.reflect.Parameter
 import xiangshan.backend.fu.util.SdtrigExt
+import xiangshan.frontend.icache._
 
-trait HasPdConst extends HasXSParameter with HasICacheParameters with HasIFUConst{
-  def isRVC(inst: UInt) = (inst(1,0) =/= 3.U)
-  def isLink(reg:UInt) = reg === 1.U || reg === 5.U
+trait HasPdConst extends HasXSParameter with HasICacheParameters with HasIFUConst {
+  def isRVC(inst: UInt) = inst(1, 0) =/= 3.U
+  def isLink(reg: UInt) = reg === 1.U || reg === 5.U
   def brInfo(instr: UInt) = {
-    val brType::Nil = ListLookup(instr, List(BrType.notCFI), PreDecodeInst.brTable)
-    val rd = Mux(isRVC(instr), instr(12), instr(11,7))
-    val rs = Mux(isRVC(instr), Mux(brType === BrType.jal, 0.U, instr(11, 7)), instr(19, 15))
+    val brType :: Nil = ListLookup(instr, List(BrType.notCFI), PreDecodeInst.brTable)
+    val rd            = Mux(isRVC(instr), instr(12), instr(11, 7))
+    val rs            = Mux(isRVC(instr), Mux(brType === BrType.jal, 0.U, instr(11, 7)), instr(19, 15))
     val isCall = (brType === BrType.jal && !isRVC(instr) || brType === BrType.jalr) && isLink(rd) // Only for RV64
-    val isRet = brType === BrType.jalr && isLink(rs) && !isCall
+    val isRet  = brType === BrType.jalr && isLink(rs) && !isCall
     List(brType, isCall, isRet)
   }
   def jal_offset(inst: UInt, rvc: Bool): UInt = {
     val rvc_offset = Cat(inst(12), inst(8), inst(10, 9), inst(6), inst(7), inst(2), inst(11), inst(5, 3), 0.U(1.W))
     val rvi_offset = Cat(inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))
-    val max_width = rvi_offset.getWidth
+    val max_width  = rvi_offset.getWidth
     SignExt(Mux(rvc, SignExt(rvc_offset, max_width), SignExt(rvi_offset, max_width)), XLEN)
   }
   def br_offset(inst: UInt, rvc: Bool): UInt = {
     val rvc_offset = Cat(inst(12), inst(6, 5), inst(2), inst(11, 10), inst(4, 3), 0.U(1.W))
     val rvi_offset = Cat(inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
-    val max_width = rvi_offset.getWidth
+    val max_width  = rvi_offset.getWidth
     SignExt(Mux(rvc, SignExt(rvc_offset, max_width), SignExt(rvi_offset, max_width)), XLEN)
   }
 
@@ -57,59 +59,59 @@ trait HasPdConst extends HasXSParameter with HasICacheParameters with HasIFUCons
 }
 
 object BrType {
-  def notCFI   = "b00".U
+  def notCFI  = "b00".U
   def branch  = "b01".U
   def jal     = "b10".U
   def jalr    = "b11".U
   def apply() = UInt(2.W)
 }
 
-object ExcType {  //TODO:add exctype
-  def notExc = "b000".U
+object ExcType { // TODO:add exctype
+  def notExc  = "b000".U
   def apply() = UInt(3.W)
 }
 
-class PreDecodeInfo extends Bundle {  // 8 bit
-  val valid   = Bool()
-  val isRVC   = Bool()
-  val brType  = UInt(2.W)
-  val isCall  = Bool()
-  val isRet   = Bool()
-  //val excType = UInt(3.W)
-  def isBr    = brType === BrType.branch
-  def isJal   = brType === BrType.jal
-  def isJalr  = brType === BrType.jalr
-  def notCFI  = brType === BrType.notCFI
+class PreDecodeInfo extends Bundle { // 8 bit
+  val valid  = Bool()
+  val isRVC  = Bool()
+  val brType = UInt(2.W)
+  val isCall = Bool()
+  val isRet  = Bool()
+  // val excType = UInt(3.W)
+  def isBr   = brType === BrType.branch
+  def isJal  = brType === BrType.jal
+  def isJalr = brType === BrType.jalr
+  def notCFI = brType === BrType.notCFI
 }
 
 class PreDecodeResp(implicit p: Parameters) extends XSBundle with HasPdConst {
-  val pd = Vec(PredictWidth, new PreDecodeInfo)
+  val pd           = Vec(PredictWidth, new PreDecodeInfo)
   val hasHalfValid = Vec(PredictWidth, Bool())
-  //val expInstr = Vec(PredictWidth, UInt(32.W))
+  // val expInstr = Vec(PredictWidth, UInt(32.W))
   val instr      = Vec(PredictWidth, UInt(32.W))
   val jumpOffset = Vec(PredictWidth, UInt(XLEN.W))
 //  val hasLastHalf = Bool()
-  val triggered    = Vec(PredictWidth, TriggerAction())
+  val triggered = Vec(PredictWidth, TriggerAction())
 }
 
-class PreDecode(implicit p: Parameters) extends XSModule with HasPdConst{
+class PreDecode(implicit p: Parameters) extends XSModule with HasPdConst {
   val io = IO(new Bundle() {
-    val in = Input(ValidIO(new IfuToPreDecode))
+    val in  = Input(ValidIO(new IfuToPreDecode))
     val out = Output(new PreDecodeResp)
   })
 
-  val data          = io.in.bits.data
+  val data = io.in.bits.data
 //  val lastHalfMatch = io.in.lastHalfMatch
-  val validStart, validEnd = Wire(Vec(PredictWidth, Bool()))
+  val validStart, validEnd     = Wire(Vec(PredictWidth, Bool()))
   val h_validStart, h_validEnd = Wire(Vec(PredictWidth, Bool()))
 
-  val validStart_half, validEnd_half = Wire(Vec(PredictWidth, Bool()))
+  val validStart_half, validEnd_half     = Wire(Vec(PredictWidth, Bool()))
   val h_validStart_half, h_validEnd_half = Wire(Vec(PredictWidth, Bool()))
 
-  val validStart_halfPlus1, validEnd_halfPlus1 = Wire(Vec(PredictWidth, Bool()))
+  val validStart_halfPlus1, validEnd_halfPlus1     = Wire(Vec(PredictWidth, Bool()))
   val h_validStart_halfPlus1, h_validEnd_halfPlus1 = Wire(Vec(PredictWidth, Bool()))
 
-  val validStart_diff, validEnd_diff = Wire(Vec(PredictWidth, Bool()))
+  val validStart_diff, validEnd_diff     = Wire(Vec(PredictWidth, Bool()))
   val h_validStart_diff, h_validEnd_diff = Wire(Vec(PredictWidth, Bool()))
 
   val currentIsRVC = Wire(Vec(PredictWidth, Bool()))
@@ -124,108 +126,117 @@ class PreDecode(implicit p: Parameters) extends XSModule with HasPdConst{
   h_validStart_halfPlus1.map(_ := false.B)
   h_validEnd_halfPlus1.map(_ := false.B)
 
-  val rawInsts = if (HasCExtension) VecInit((0 until PredictWidth).map(i => Cat(data(i+1), data(i))))
-  else         VecInit((0 until PredictWidth).map(i => data(i)))
+  val rawInsts = if (HasCExtension) VecInit((0 until PredictWidth).map(i => Cat(data(i + 1), data(i))))
+  else VecInit((0 until PredictWidth).map(i => data(i)))
 
   for (i <- 0 until PredictWidth) {
-    val inst           = WireInit(rawInsts(i))
-    //val expander       = Module(new RVCExpander)
-    currentIsRVC(i)   := isRVC(inst)
-    val currentPC      = io.in.bits.pc(i)
-    //expander.io.in             := inst
-
-    val brType::isCall::isRet::Nil = brInfo(inst)
-    val jalOffset = jal_offset(inst, currentIsRVC(i))
-    val brOffset  = br_offset(inst, currentIsRVC(i))
+    val inst = WireInit(rawInsts(i))
+    // val expander       = Module(new RVCExpander)
+    currentIsRVC(i) := isRVC(inst)
+    val currentPC = io.in.bits.pc(i)
+    // expander.io.in             := inst
 
-    io.out.hasHalfValid(i)        := h_validStart(i)
+    val brType :: isCall :: isRet :: Nil = brInfo(inst)
+    val jalOffset                        = jal_offset(inst, currentIsRVC(i))
+    val brOffset                         = br_offset(inst, currentIsRVC(i))
 
-    io.out.triggered(i)   := DontCare//VecInit(Seq.fill(10)(false.B))
+    io.out.hasHalfValid(i) := h_validStart(i)
 
+    io.out.triggered(i) := DontCare // VecInit(Seq.fill(10)(false.B))
 
-    io.out.pd(i).valid         := validStart(i)
-    io.out.pd(i).isRVC         := currentIsRVC(i)
+    io.out.pd(i).valid := validStart(i)
+    io.out.pd(i).isRVC := currentIsRVC(i)
 
     // for diff purpose only
-    io.out.pd(i).brType        := brType
-    io.out.pd(i).isCall        := isCall
-    io.out.pd(i).isRet         := isRet
+    io.out.pd(i).brType := brType
+    io.out.pd(i).isCall := isCall
+    io.out.pd(i).isRet  := isRet
 
-    //io.out.expInstr(i)         := expander.io.out.bits
-    io.out.instr(i)              :=inst
-    io.out.jumpOffset(i)       := Mux(io.out.pd(i).isBr, brOffset, jalOffset)
+    // io.out.expInstr(i)         := expander.io.out.bits
+    io.out.instr(i)      := inst
+    io.out.jumpOffset(i) := Mux(io.out.pd(i).isBr, brOffset, jalOffset)
   }
 
   // the first half is always reliable
   for (i <- 0 until PredictWidth / 2) {
-    val lastIsValidEnd =   if (i == 0) { true.B } else { validEnd(i-1) || !HasCExtension.B }
-    validStart(i)   := (lastIsValidEnd || !HasCExtension.B)
-    validEnd(i)     := validStart(i) && currentIsRVC(i) || !validStart(i) || !HasCExtension.B
-
-    //prepared for last half match
-    val h_lastIsValidEnd = if (i == 0) { false.B } else { h_validEnd(i-1) || !HasCExtension.B }
-    h_validStart(i)   := (h_lastIsValidEnd || !HasCExtension.B)
-    h_validEnd(i)     := h_validStart(i) && currentIsRVC(i) || !h_validStart(i) || !HasCExtension.B
+    val lastIsValidEnd = if (i == 0) { true.B }
+    else { validEnd(i - 1) || !HasCExtension.B }
+    validStart(i) := (lastIsValidEnd || !HasCExtension.B)
+    validEnd(i)   := validStart(i) && currentIsRVC(i) || !validStart(i) || !HasCExtension.B
+
+    // prepared for last half match
+    val h_lastIsValidEnd = if (i == 0) { false.B }
+    else { h_validEnd(i - 1) || !HasCExtension.B }
+    h_validStart(i) := (h_lastIsValidEnd || !HasCExtension.B)
+    h_validEnd(i)   := h_validStart(i) && currentIsRVC(i) || !h_validStart(i) || !HasCExtension.B
   }
 
   for (i <- 0 until PredictWidth) {
-    val lastIsValidEnd =   if (i == 0) { true.B } else { validEnd_diff(i-1) || !HasCExtension.B }
-    validStart_diff(i)   := (lastIsValidEnd || !HasCExtension.B)
-    validEnd_diff(i)     := validStart_diff(i) && currentIsRVC(i) || !validStart_diff(i) || !HasCExtension.B
-
-    //prepared for last half match
-    val h_lastIsValidEnd = if (i == 0) { false.B } else { h_validEnd_diff(i-1) || !HasCExtension.B }
-    h_validStart_diff(i)   := (h_lastIsValidEnd || !HasCExtension.B)
-    h_validEnd_diff(i)     := h_validStart_diff(i) && currentIsRVC(i) || !h_validStart_diff(i) || !HasCExtension.B
+    val lastIsValidEnd = if (i == 0) { true.B }
+    else { validEnd_diff(i - 1) || !HasCExtension.B }
+    validStart_diff(i) := (lastIsValidEnd || !HasCExtension.B)
+    validEnd_diff(i)   := validStart_diff(i) && currentIsRVC(i) || !validStart_diff(i) || !HasCExtension.B
+
+    // prepared for last half match
+    val h_lastIsValidEnd = if (i == 0) { false.B }
+    else { h_validEnd_diff(i - 1) || !HasCExtension.B }
+    h_validStart_diff(i) := (h_lastIsValidEnd || !HasCExtension.B)
+    h_validEnd_diff(i)   := h_validStart_diff(i) && currentIsRVC(i) || !h_validStart_diff(i) || !HasCExtension.B
   }
 
   // assume PredictWidth / 2 is a valid start
   for (i <- PredictWidth / 2 until PredictWidth) {
-    val lastIsValidEnd =   if (i == PredictWidth / 2) { true.B } else { validEnd_half(i-1) || !HasCExtension.B }
-    validStart_half(i)   := (lastIsValidEnd || !HasCExtension.B)
-    validEnd_half(i)     := validStart_half(i) && currentIsRVC(i) || !validStart_half(i) || !HasCExtension.B
-
-    //prepared for last half match
-    val h_lastIsValidEnd = if (i == PredictWidth / 2) { true.B } else { h_validEnd_half(i-1) || !HasCExtension.B }
-    h_validStart_half(i)   := (h_lastIsValidEnd || !HasCExtension.B)
-    h_validEnd_half(i)     := h_validStart_half(i) && currentIsRVC(i) || !h_validStart_half(i) || !HasCExtension.B
+    val lastIsValidEnd = if (i == PredictWidth / 2) { true.B }
+    else { validEnd_half(i - 1) || !HasCExtension.B }
+    validStart_half(i) := (lastIsValidEnd || !HasCExtension.B)
+    validEnd_half(i)   := validStart_half(i) && currentIsRVC(i) || !validStart_half(i) || !HasCExtension.B
+
+    // prepared for last half match
+    val h_lastIsValidEnd = if (i == PredictWidth / 2) { true.B }
+    else { h_validEnd_half(i - 1) || !HasCExtension.B }
+    h_validStart_half(i) := (h_lastIsValidEnd || !HasCExtension.B)
+    h_validEnd_half(i)   := h_validStart_half(i) && currentIsRVC(i) || !h_validStart_half(i) || !HasCExtension.B
   }
 
   // assume PredictWidth / 2 + 1 is a valid start (and PredictWidth / 2 is last half of RVI)
   for (i <- PredictWidth / 2 + 1 until PredictWidth) {
-    val lastIsValidEnd =   if (i == PredictWidth / 2 + 1) { true.B } else { validEnd_halfPlus1(i-1) || !HasCExtension.B }
-    validStart_halfPlus1(i)   := (lastIsValidEnd || !HasCExtension.B)
-    validEnd_halfPlus1(i)     := validStart_halfPlus1(i) && currentIsRVC(i) || !validStart_halfPlus1(i) || !HasCExtension.B
-
-    //prepared for last half match
-    val h_lastIsValidEnd = if (i == PredictWidth / 2 + 1) { true.B } else { h_validEnd_halfPlus1(i-1) || !HasCExtension.B }
-    h_validStart_halfPlus1(i)   := (h_lastIsValidEnd || !HasCExtension.B)
-    h_validEnd_halfPlus1(i)     := h_validStart_halfPlus1(i) && currentIsRVC(i) || !h_validStart_halfPlus1(i) || !HasCExtension.B
+    val lastIsValidEnd = if (i == PredictWidth / 2 + 1) { true.B }
+    else { validEnd_halfPlus1(i - 1) || !HasCExtension.B }
+    validStart_halfPlus1(i) := (lastIsValidEnd || !HasCExtension.B)
+    validEnd_halfPlus1(i) := validStart_halfPlus1(i) && currentIsRVC(i) || !validStart_halfPlus1(i) || !HasCExtension.B
+
+    // prepared for last half match
+    val h_lastIsValidEnd = if (i == PredictWidth / 2 + 1) { true.B }
+    else { h_validEnd_halfPlus1(i - 1) || !HasCExtension.B }
+    h_validStart_halfPlus1(i) := (h_lastIsValidEnd || !HasCExtension.B)
+    h_validEnd_halfPlus1(i) := h_validStart_halfPlus1(i) && currentIsRVC(i) || !h_validStart_halfPlus1(
+      i
+    ) || !HasCExtension.B
   }
   validStart_halfPlus1(PredictWidth / 2) := false.B // could be true but when true we select half, not halfPlus1
-  validEnd_halfPlus1(PredictWidth / 2) := true.B
+  validEnd_halfPlus1(PredictWidth / 2)   := true.B
 
   // assume h_PredictWidth / 2 is an end
   h_validStart_halfPlus1(PredictWidth / 2) := false.B // could be true but when true we select half, not halfPlus1
-  h_validEnd_halfPlus1(PredictWidth / 2) := true.B
+  h_validEnd_halfPlus1(PredictWidth / 2)   := true.B
 
   // if PredictWidth / 2 - 1 is a valid end, PredictWidth / 2 is a valid start
   for (i <- PredictWidth / 2 until PredictWidth) {
-    validStart(i) := Mux(validEnd(PredictWidth / 2 - 1), validStart_half(i), validStart_halfPlus1(i))
-    validEnd(i) := Mux(validEnd(PredictWidth / 2 - 1), validEnd_half(i), validEnd_halfPlus1(i))
+    validStart(i)   := Mux(validEnd(PredictWidth / 2 - 1), validStart_half(i), validStart_halfPlus1(i))
+    validEnd(i)     := Mux(validEnd(PredictWidth / 2 - 1), validEnd_half(i), validEnd_halfPlus1(i))
     h_validStart(i) := Mux(h_validEnd(PredictWidth / 2 - 1), h_validStart_half(i), h_validStart_halfPlus1(i))
-    h_validEnd(i) := Mux(h_validEnd(PredictWidth / 2 - 1), h_validEnd_half(i), h_validEnd_halfPlus1(i))
+    h_validEnd(i)   := Mux(h_validEnd(PredictWidth / 2 - 1), h_validEnd_half(i), h_validEnd_halfPlus1(i))
   }
 
-  val validStartMismatch = Wire(Bool())
-  val validEndMismatch = Wire(Bool())
+  val validStartMismatch        = Wire(Bool())
+  val validEndMismatch          = Wire(Bool())
   val validH_ValidStartMismatch = Wire(Bool())
-  val validH_ValidEndMismatch = Wire(Bool())
+  val validH_ValidEndMismatch   = Wire(Bool())
 
-  validStartMismatch := validStart.zip(validStart_diff).map{case(a,b) => a =/= b}.reduce(_||_)
-  validEndMismatch := validEnd.zip(validEnd_diff).map{case(a,b) => a =/= b}.reduce(_||_)
-  validH_ValidStartMismatch := h_validStart.zip(h_validStart_diff).map{case(a,b) => a =/= b}.reduce(_||_)
-  validH_ValidEndMismatch := h_validEnd.zip(h_validEnd_diff).map{case(a,b) => a =/= b}.reduce(_||_)
+  validStartMismatch        := validStart.zip(validStart_diff).map { case (a, b) => a =/= b }.reduce(_ || _)
+  validEndMismatch          := validEnd.zip(validEnd_diff).map { case (a, b) => a =/= b }.reduce(_ || _)
+  validH_ValidStartMismatch := h_validStart.zip(h_validStart_diff).map { case (a, b) => a =/= b }.reduce(_ || _)
+  validH_ValidEndMismatch   := h_validEnd.zip(h_validEnd_diff).map { case (a, b) => a =/= b }.reduce(_ || _)
 
   XSError(io.in.valid && validStartMismatch, p"validStart mismatch\n")
   XSError(io.in.valid && validEndMismatch, p"validEnd mismatch\n")
@@ -235,7 +246,8 @@ class PreDecode(implicit p: Parameters) extends XSModule with HasPdConst{
 //  io.out.hasLastHalf := !io.out.pd(PredictWidth - 1).isRVC && io.out.pd(PredictWidth - 1).valid
 
   for (i <- 0 until PredictWidth) {
-    XSDebug(true.B,
+    XSDebug(
+      true.B,
       p"instr ${Hexadecimal(io.out.instr(i))}, " +
         p"validStart ${Binary(validStart(i))}, " +
         p"validEnd ${Binary(validEnd(i))}, " +
@@ -248,7 +260,7 @@ class PreDecode(implicit p: Parameters) extends XSModule with HasPdConst{
 }
 
 class IfuToF3PreDecode(implicit p: Parameters) extends XSBundle with HasPdConst {
-  val instr      = Vec(PredictWidth, UInt(32.W))
+  val instr = Vec(PredictWidth, UInt(32.W))
 }
 
 class F3PreDecodeResp(implicit p: Parameters) extends XSBundle with HasPdConst {
@@ -256,22 +268,22 @@ class F3PreDecodeResp(implicit p: Parameters) extends XSBundle with HasPdConst {
 }
 class F3Predecoder(implicit p: Parameters) extends XSModule with HasPdConst {
   val io = IO(new Bundle() {
-    val in = Input(new IfuToF3PreDecode)
+    val in  = Input(new IfuToF3PreDecode)
     val out = Output(new F3PreDecodeResp)
   })
-  io.out.pd.zipWithIndex.map{ case (pd,i) =>
-    pd.valid := DontCare
-    pd.isRVC := DontCare
+  io.out.pd.zipWithIndex.map { case (pd, i) =>
+    pd.valid  := DontCare
+    pd.isRVC  := DontCare
     pd.brType := brInfo(io.in.instr(i))(0)
     pd.isCall := brInfo(io.in.instr(i))(1)
-    pd.isRet := brInfo(io.in.instr(i))(2)
+    pd.isRet  := brInfo(io.in.instr(i))(2)
   }
 
 }
 
 class RVCExpander(implicit p: Parameters) extends XSModule {
   val io = IO(new Bundle {
-    val in = Input(UInt(32.W))
+    val in  = Input(UInt(32.W))
     val out = Output(new ExpandedInstruction)
     val ill = Output(Bool())
   })
@@ -294,50 +306,49 @@ class RVCExpander(implicit p: Parameters) extends XSModule {
  */
 
 object FaultType {
-  def noFault         = "b000".U
-  def jalFault        = "b001".U    //not CFI taken or invalid instruction taken
-  def retFault        = "b010".U    //not CFI taken or invalid instruction taken
-  def targetFault     = "b011".U
-  def notCFIFault    = "b100".U    //not CFI taken or invalid instruction taken
-  def invalidTaken    = "b101".U
-  def apply() = UInt(3.W)
+  def noFault      = "b000".U
+  def jalFault     = "b001".U // not CFI taken or invalid instruction taken
+  def retFault     = "b010".U // not CFI taken or invalid instruction taken
+  def targetFault  = "b011".U
+  def notCFIFault  = "b100".U // not CFI taken or invalid instruction taken
+  def invalidTaken = "b101".U
+  def apply()      = UInt(3.W)
 }
 
-class CheckInfo extends Bundle {  // 8 bit
-  val value  = UInt(3.W)
-  def isjalFault      = value === FaultType.jalFault
-  def isRetFault      = value === FaultType.retFault
-  def istargetFault   = value === FaultType.targetFault
-  def invalidTakenFault    = value === FaultType.invalidTaken
-  def notCFIFault          = value === FaultType.notCFIFault
+class CheckInfo extends Bundle { // 8 bit
+  val value             = UInt(3.W)
+  def isjalFault        = value === FaultType.jalFault
+  def isRetFault        = value === FaultType.retFault
+  def istargetFault     = value === FaultType.targetFault
+  def invalidTakenFault = value === FaultType.invalidTaken
+  def notCFIFault       = value === FaultType.notCFIFault
 }
 
 class PredCheckerResp(implicit p: Parameters) extends XSBundle with HasPdConst {
-  //to Ibuffer write port  (stage 1)
-  val stage1Out = new Bundle{
-    val fixedRange  = Vec(PredictWidth, Bool())
-    val fixedTaken  = Vec(PredictWidth, Bool())
+  // to Ibuffer write port  (stage 1)
+  val stage1Out = new Bundle {
+    val fixedRange = Vec(PredictWidth, Bool())
+    val fixedTaken = Vec(PredictWidth, Bool())
   }
-  //to Ftq write back port (stage 2)
-  val stage2Out = new Bundle{
-    val fixedTarget = Vec(PredictWidth, UInt(VAddrBits.W))
-    val jalTarget = Vec(PredictWidth, UInt(VAddrBits.W))
-    val fixedMissPred = Vec(PredictWidth,  Bool())
-    val faultType   = Vec(PredictWidth, new CheckInfo)
+  // to Ftq write back port (stage 2)
+  val stage2Out = new Bundle {
+    val fixedTarget   = Vec(PredictWidth, UInt(VAddrBits.W))
+    val jalTarget     = Vec(PredictWidth, UInt(VAddrBits.W))
+    val fixedMissPred = Vec(PredictWidth, Bool())
+    val faultType     = Vec(PredictWidth, new CheckInfo)
   }
 }
 
-
 class PredChecker(implicit p: Parameters) extends XSModule with HasPdConst {
-  val io = IO( new Bundle{
-    val in = Input(new IfuToPredChecker)
+  val io = IO(new Bundle {
+    val in  = Input(new IfuToPredChecker)
     val out = Output(new PredCheckerResp)
   })
 
-  val (takenIdx, predTaken)     = (io.in.ftqOffset.bits, io.in.ftqOffset.valid)
-  val predTarget                = (io.in.target)
-  val (instrRange, instrValid)  = (io.in.instrRange, io.in.instrValid)
-  val (pds, pc, jumpOffset)     = (io.in.pds, io.in.pc, io.in.jumpOffset)
+  val (takenIdx, predTaken)    = (io.in.ftqOffset.bits, io.in.ftqOffset.valid)
+  val predTarget               = io.in.target
+  val (instrRange, instrValid) = (io.in.instrRange, io.in.instrValid)
+  val (pds, pc, jumpOffset)    = (io.in.pds, io.in.pc, io.in.jumpOffset)
 
   val jalFaultVec, retFaultVec, targetFault, notCFITaken, invalidTaken = Wire(Vec(PredictWidth, Bool()))
 
@@ -346,71 +357,104 @@ class PredChecker(implicit p: Parameters) extends XSModule with HasPdConst {
     * we first detecct remask fault and then use fixedRange to do second check
     **/
 
-  //Stage 1: detect remask fault
+  // Stage 1: detect remask fault
   /** first check: remask Fault */
-  jalFaultVec         := VecInit(pds.zipWithIndex.map{case(pd, i) => pd.isJal && instrRange(i) && instrValid(i) && (takenIdx > i.U && predTaken || !predTaken) })
-  retFaultVec         := VecInit(pds.zipWithIndex.map{case(pd, i) => pd.isRet && instrRange(i) && instrValid(i) && (takenIdx > i.U && predTaken || !predTaken) })
-  val remaskFault      = VecInit((0 until PredictWidth).map(i => jalFaultVec(i) || retFaultVec(i)))
-  val remaskIdx        = ParallelPriorityEncoder(remaskFault.asUInt)
-  val needRemask       = ParallelOR(remaskFault)
-  val fixedRange       = instrRange.asUInt & (Fill(PredictWidth, !needRemask) | Fill(PredictWidth, 1.U(1.W)) >> ~remaskIdx)
+  jalFaultVec := VecInit(pds.zipWithIndex.map { case (pd, i) =>
+    pd.isJal && instrRange(i) && instrValid(i) && (takenIdx > i.U && predTaken || !predTaken)
+  })
+  retFaultVec := VecInit(pds.zipWithIndex.map { case (pd, i) =>
+    pd.isRet && instrRange(i) && instrValid(i) && (takenIdx > i.U && predTaken || !predTaken)
+  })
+  val remaskFault = VecInit((0 until PredictWidth).map(i => jalFaultVec(i) || retFaultVec(i)))
+  val remaskIdx   = ParallelPriorityEncoder(remaskFault.asUInt)
+  val needRemask  = ParallelOR(remaskFault)
+  val fixedRange  = instrRange.asUInt & (Fill(PredictWidth, !needRemask) | Fill(PredictWidth, 1.U(1.W)) >> ~remaskIdx)
 
-  io.out.stage1Out.fixedRange := fixedRange.asTypeOf((Vec(PredictWidth, Bool())))
+  io.out.stage1Out.fixedRange := fixedRange.asTypeOf(Vec(PredictWidth, Bool()))
 
-  io.out.stage1Out.fixedTaken := VecInit(pds.zipWithIndex.map{case(pd, i) => instrValid (i) && fixedRange(i) && (pd.isRet || pd.isJal || takenIdx === i.U && predTaken && !pd.notCFI)  })
+  io.out.stage1Out.fixedTaken := VecInit(pds.zipWithIndex.map { case (pd, i) =>
+    instrValid(i) && fixedRange(i) && (pd.isRet || pd.isJal || takenIdx === i.U && predTaken && !pd.notCFI)
+  })
 
   /** second check: faulse prediction fault and target fault */
-  notCFITaken  := VecInit(pds.zipWithIndex.map{case(pd, i) => fixedRange(i) && instrValid(i) && i.U === takenIdx && pd.notCFI && predTaken })
-  invalidTaken := VecInit(pds.zipWithIndex.map{case(pd, i) => fixedRange(i) && !instrValid(i)  && i.U === takenIdx  && predTaken })
+  notCFITaken := VecInit(pds.zipWithIndex.map { case (pd, i) =>
+    fixedRange(i) && instrValid(i) && i.U === takenIdx && pd.notCFI && predTaken
+  })
+  invalidTaken := VecInit(pds.zipWithIndex.map { case (pd, i) =>
+    fixedRange(i) && !instrValid(i) && i.U === takenIdx && predTaken
+  })
 
-  val jumpTargets          = VecInit(pds.zipWithIndex.map{case(pd,i) => (pc(i) + jumpOffset(i)).asTypeOf(UInt(VAddrBits.W))})
-  val seqTargets = VecInit((0 until PredictWidth).map(i => pc(i) + Mux(pds(i).isRVC || !instrValid(i), 2.U, 4.U ) ))
+  val jumpTargets = VecInit(pds.zipWithIndex.map { case (pd, i) =>
+    (pc(i) + jumpOffset(i)).asTypeOf(UInt(VAddrBits.W))
+  })
+  val seqTargets = VecInit((0 until PredictWidth).map(i => pc(i) + Mux(pds(i).isRVC || !instrValid(i), 2.U, 4.U)))
 
-  //Stage 2: detect target fault
+  // Stage 2: detect target fault
   /** target calculation: in the next stage  */
-  val fixedRangeNext = RegEnable(fixedRange, io.in.fire_in)
-  val instrValidNext = RegEnable(instrValid, io.in.fire_in)
-  val takenIdxNext   = RegEnable(takenIdx, io.in.fire_in)
-  val predTakenNext  = RegEnable(predTaken, io.in.fire_in)
-  val predTargetNext = RegEnable(predTarget, io.in.fire_in)
-  val jumpTargetsNext = RegEnable(jumpTargets, io.in.fire_in)
-  val seqTargetsNext = RegEnable(seqTargets, io.in.fire_in)
-  val pdsNext = RegEnable(pds, io.in.fire_in)
-  val jalFaultVecNext = RegEnable(jalFaultVec, io.in.fire_in)
-  val retFaultVecNext = RegEnable(retFaultVec, io.in.fire_in)
-  val notCFITakenNext = RegEnable(notCFITaken, io.in.fire_in)
+  val fixedRangeNext   = RegEnable(fixedRange, io.in.fire_in)
+  val instrValidNext   = RegEnable(instrValid, io.in.fire_in)
+  val takenIdxNext     = RegEnable(takenIdx, io.in.fire_in)
+  val predTakenNext    = RegEnable(predTaken, io.in.fire_in)
+  val predTargetNext   = RegEnable(predTarget, io.in.fire_in)
+  val jumpTargetsNext  = RegEnable(jumpTargets, io.in.fire_in)
+  val seqTargetsNext   = RegEnable(seqTargets, io.in.fire_in)
+  val pdsNext          = RegEnable(pds, io.in.fire_in)
+  val jalFaultVecNext  = RegEnable(jalFaultVec, io.in.fire_in)
+  val retFaultVecNext  = RegEnable(retFaultVec, io.in.fire_in)
+  val notCFITakenNext  = RegEnable(notCFITaken, io.in.fire_in)
   val invalidTakenNext = RegEnable(invalidTaken, io.in.fire_in)
 
-  targetFault      := VecInit(pdsNext.zipWithIndex.map{case(pd,i) => fixedRangeNext(i) && instrValidNext(i) && (pd.isJal || pd.isBr) && takenIdxNext === i.U && predTakenNext  && (predTargetNext =/= jumpTargetsNext(i))})
-
+  targetFault := VecInit(pdsNext.zipWithIndex.map { case (pd, i) =>
+    fixedRangeNext(i) && instrValidNext(
+      i
+    ) && (pd.isJal || pd.isBr) && takenIdxNext === i.U && predTakenNext && (predTargetNext =/= jumpTargetsNext(i))
+  })
 
-  io.out.stage2Out.faultType.zipWithIndex.foreach{case(faultType, i) => faultType.value := Mux(jalFaultVecNext(i) , FaultType.jalFault ,
-                                                                             Mux(retFaultVecNext(i), FaultType.retFault ,
-                                                                             Mux(targetFault(i), FaultType.targetFault ,
-                                                                             Mux(notCFITakenNext(i) , FaultType.notCFIFault,
-                                                                             Mux(invalidTakenNext(i), FaultType.invalidTaken,  FaultType.noFault)))))}
+  io.out.stage2Out.faultType.zipWithIndex.foreach { case (faultType, i) =>
+    faultType.value := Mux(
+      jalFaultVecNext(i),
+      FaultType.jalFault,
+      Mux(
+        retFaultVecNext(i),
+        FaultType.retFault,
+        Mux(
+          targetFault(i),
+          FaultType.targetFault,
+          Mux(
+            notCFITakenNext(i),
+            FaultType.notCFIFault,
+            Mux(invalidTakenNext(i), FaultType.invalidTaken, FaultType.noFault)
+          )
+        )
+      )
+    )
+  }
 
-  io.out.stage2Out.fixedMissPred.zipWithIndex.foreach{case(missPred, i ) => missPred := jalFaultVecNext(i) || retFaultVecNext(i) || notCFITakenNext(i) || invalidTakenNext(i) || targetFault(i)}
-  io.out.stage2Out.fixedTarget.zipWithIndex.foreach{case(target, i) => target := Mux(jalFaultVecNext(i) || targetFault(i), jumpTargetsNext(i),  seqTargetsNext(i) )}
-  io.out.stage2Out.jalTarget.zipWithIndex.foreach{case(target, i) => target := jumpTargetsNext(i) }
+  io.out.stage2Out.fixedMissPred.zipWithIndex.foreach { case (missPred, i) =>
+    missPred := jalFaultVecNext(i) || retFaultVecNext(i) || notCFITakenNext(i) || invalidTakenNext(i) || targetFault(i)
+  }
+  io.out.stage2Out.fixedTarget.zipWithIndex.foreach { case (target, i) =>
+    target := Mux(jalFaultVecNext(i) || targetFault(i), jumpTargetsNext(i), seqTargetsNext(i))
+  }
+  io.out.stage2Out.jalTarget.zipWithIndex.foreach { case (target, i) => target := jumpTargetsNext(i) }
 
 }
 
 class FrontendTrigger(implicit p: Parameters) extends XSModule with SdtrigExt {
-  val io = IO(new Bundle(){
+  val io = IO(new Bundle() {
     val frontendTrigger = Input(new FrontendTdataDistributeIO)
-    val triggered     = Output(Vec(PredictWidth, TriggerAction()))
+    val triggered       = Output(Vec(PredictWidth, TriggerAction()))
 
-    val pds           = Input(Vec(PredictWidth, new PreDecodeInfo))
-    val pc            = Input(Vec(PredictWidth, UInt(VAddrBits.W)))
-    val data          = if(HasCExtension) Input(Vec(PredictWidth + 1, UInt(16.W)))
-                        else Input(Vec(PredictWidth, UInt(32.W)))
+    val pds = Input(Vec(PredictWidth, new PreDecodeInfo))
+    val pc  = Input(Vec(PredictWidth, UInt(VAddrBits.W)))
+    val data = if (HasCExtension) Input(Vec(PredictWidth + 1, UInt(16.W)))
+    else Input(Vec(PredictWidth, UInt(32.W)))
   })
 
-  val data          = io.data
+  val data = io.data
 
-  val rawInsts = if (HasCExtension) VecInit((0 until PredictWidth).map(i => Cat(data(i+1), data(i))))
-  else         VecInit((0 until PredictWidth).map(i => data(i)))
+  val rawInsts = if (HasCExtension) VecInit((0 until PredictWidth).map(i => Cat(data(i + 1), data(i))))
+  else VecInit((0 until PredictWidth).map(i => data(i)))
 
   val tdataVec = RegInit(VecInit(Seq.fill(TriggerNum)(0.U.asTypeOf(new MatchTriggerIO))))
   when(io.frontendTrigger.tUpdate.valid) {
@@ -421,28 +465,32 @@ class FrontendTrigger(implicit p: Parameters) extends XSModule with SdtrigExt {
   XSDebug(triggerEnableVec.asUInt.orR, "Debug Mode: At least one frontend trigger is enabled\n")
 
   val triggerTimingVec = VecInit(tdataVec.map(_.timing))
-  val triggerChainVec = VecInit(tdataVec.map(_.chain))
+  val triggerChainVec  = VecInit(tdataVec.map(_.chain))
 
   for (i <- 0 until TriggerNum) { PrintTriggerInfo(triggerEnableVec(i), tdataVec(i)) }
 
-  val debugMode = io.frontendTrigger.debugMode
+  val debugMode            = io.frontendTrigger.debugMode
   val triggerCanRaiseBpExp = io.frontendTrigger.triggerCanRaiseBpExp
-  //val triggerHitVec = Wire(Vec(PredictWidth, Vec(TriggerNum, Bool())))
+  // val triggerHitVec = Wire(Vec(PredictWidth, Vec(TriggerNum, Bool())))
   val triggerHitVec = (0 until TriggerNum).map(j =>
-      TriggerCmpConsecutive(io.pc, tdataVec(j).tdata2, tdataVec(j).matchType, triggerEnableVec(j)).map(
-        hit => hit && !tdataVec(j).select && !debugMode)
+    TriggerCmpConsecutive(io.pc, tdataVec(j).tdata2, tdataVec(j).matchType, triggerEnableVec(j)).map(hit =>
+      hit && !tdataVec(j).select && !debugMode
+    )
   ).transpose
 
   for (i <- 0 until PredictWidth) {
     val triggerCanFireVec = Wire(Vec(TriggerNum, Bool()))
     TriggerCheckCanFire(TriggerNum, triggerCanFireVec, VecInit(triggerHitVec(i)), triggerTimingVec, triggerChainVec)
 
-    val actionVec = VecInit(tdataVec.map(_.action))
+    val actionVec     = VecInit(tdataVec.map(_.action))
     val triggerAction = Wire(TriggerAction())
     TriggerUtil.triggerActionGen(triggerAction, triggerCanFireVec, actionVec, triggerCanRaiseBpExp)
 
     // Priority may select last when no trigger fire.
     io.triggered(i) := triggerAction
-    XSDebug(triggerCanFireVec.asUInt.orR, p"Debug Mode: Predecode Inst No. ${i} has trigger action vec ${triggerCanFireVec.asUInt.orR}\n")
+    XSDebug(
+      triggerCanFireVec.asUInt.orR,
+      p"Debug Mode: Predecode Inst No. ${i} has trigger action vec ${triggerCanFireVec.asUInt.orR}\n"
+    )
   }
 }
diff --git a/src/main/scala/xiangshan/frontend/RAS.scala b/src/main/scala/xiangshan/frontend/RAS.scala
index 229cddc6679..56ed0ec39f8 100644
--- a/src/main/scala/xiangshan/frontend/RAS.scala
+++ b/src/main/scala/xiangshan/frontend/RAS.scala
@@ -306,4 +306,4 @@ class RAS(implicit p: Parameters) extends BasePredictor {
 
   generatePerfEvent()
 }
-*/
\ No newline at end of file
+ */
diff --git a/src/main/scala/xiangshan/frontend/SC.scala b/src/main/scala/xiangshan/frontend/SC.scala
index f615967c589..ef5cff3d967 100644
--- a/src/main/scala/xiangshan/frontend/SC.scala
+++ b/src/main/scala/xiangshan/frontend/SC.scala
@@ -16,102 +16,102 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
-import scala.math.min
+import org.chipsalliance.cde.config.Parameters
 import scala.{Tuple2 => &}
+import scala.math.min
+import utility._
+import utils._
+import xiangshan._
 
-trait HasSCParameter extends TageParams {
-}
+trait HasSCParameter extends TageParams {}
 
 class SCReq(implicit p: Parameters) extends TageReq
 
 abstract class SCBundle(implicit p: Parameters) extends TageBundle with HasSCParameter {}
 abstract class SCModule(implicit p: Parameters) extends TageModule with HasSCParameter {}
 
-
 class SCMeta(val ntables: Int)(implicit p: Parameters) extends XSBundle with HasSCParameter {
   val scPreds = Vec(numBr, Bool())
   // Suppose ctrbits of all tables are identical
   val ctrs = Vec(numBr, Vec(ntables, SInt(SCCtrBits.W)))
 }
 
-
 class SCResp(val ctrBits: Int = 6)(implicit p: Parameters) extends SCBundle {
   val ctrs = Vec(numBr, Vec(2, SInt(ctrBits.W)))
 }
 
 class SCUpdate(val ctrBits: Int = 6)(implicit p: Parameters) extends SCBundle {
-  val pc = UInt(VAddrBits.W)
-  val ghist = UInt(HistoryLength.W)
-  val mask = Vec(numBr, Bool())
-  val oldCtrs = Vec(numBr, SInt(ctrBits.W))
+  val pc        = UInt(VAddrBits.W)
+  val ghist     = UInt(HistoryLength.W)
+  val mask      = Vec(numBr, Bool())
+  val oldCtrs   = Vec(numBr, SInt(ctrBits.W))
   val tagePreds = Vec(numBr, Bool())
-  val takens = Vec(numBr, Bool())
+  val takens    = Vec(numBr, Bool())
 }
 
 class SCTableIO(val ctrBits: Int = 6)(implicit p: Parameters) extends SCBundle {
-  val req = Input(Valid(new SCReq))
-  val resp = Output(new SCResp(ctrBits))
+  val req    = Input(Valid(new SCReq))
+  val resp   = Output(new SCResp(ctrBits))
   val update = Input(new SCUpdate(ctrBits))
 }
 
 class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Parameters)
-  extends SCModule with HasFoldedHistory {
+    extends SCModule with HasFoldedHistory {
   val io = IO(new SCTableIO(ctrBits))
 
   // val table = Module(new SRAMTemplate(SInt(ctrBits.W), set=nRows, way=2*TageBanks, shouldReset=true, holdRead=true, singlePort=false))
-  val table = Module(new SRAMTemplate(SInt(ctrBits.W), set=nRows, way=2*TageBanks, shouldReset=true, holdRead=true, singlePort=false, bypassWrite=true))
+  val table = Module(new SRAMTemplate(
+    SInt(ctrBits.W),
+    set = nRows,
+    way = 2 * TageBanks,
+    shouldReset = true,
+    holdRead = true,
+    singlePort = false,
+    bypassWrite = true
+  ))
 
   // def getIdx(hist: UInt, pc: UInt) = {
   //   (compute_folded_ghist(hist, log2Ceil(nRows)) ^ (pc >> instOffsetBits))(log2Ceil(nRows)-1,0)
   // }
 
-
   val idxFhInfo = (histLen, min(log2Ceil(nRows), histLen))
 
   def getFoldedHistoryInfo = Set(idxFhInfo).filter(_._1 > 0)
 
-  def getIdx(pc: UInt, allFh: AllFoldedHistories) = {
+  def getIdx(pc: UInt, allFh: AllFoldedHistories) =
     if (histLen > 0) {
       val idx_fh = allFh.getHistWithInfo(idxFhInfo).folded_hist
       // require(idx_fh.getWidth == log2Ceil(nRows))
-      ((pc >> instOffsetBits) ^ idx_fh)(log2Ceil(nRows)-1,0)
+      ((pc >> instOffsetBits) ^ idx_fh)(log2Ceil(nRows) - 1, 0)
+    } else {
+      (pc >> instOffsetBits)(log2Ceil(nRows) - 1, 0)
     }
-    else {
-      (pc >> instOffsetBits)(log2Ceil(nRows)-1,0)
-    }
-  }
-
 
   def ctrUpdate(ctr: SInt, cond: Bool): SInt = signedSatUpdate(ctr, ctrBits, cond)
 
   val s0_idx = getIdx(io.req.bits.pc, io.req.bits.folded_hist)
   val s1_idx = RegEnable(s0_idx, io.req.valid)
 
-  val s1_pc = RegEnable(io.req.bits.pc, io.req.fire)
+  val s1_pc           = RegEnable(io.req.bits.pc, io.req.fire)
   val s1_unhashed_idx = s1_pc >> instOffsetBits
 
-  table.io.r.req.valid := io.req.valid
+  table.io.r.req.valid       := io.req.valid
   table.io.r.req.bits.setIdx := s0_idx
 
-  val update_wdata = Wire(Vec(numBr, SInt(ctrBits.W))) // correspond to physical bridx
-  val update_wdata_packed = VecInit(update_wdata.map(Seq.fill(2)(_)).reduce(_++_))
-  val updateWayMask = Wire(Vec(2*numBr, Bool())) // correspond to physical bridx
+  val update_wdata        = Wire(Vec(numBr, SInt(ctrBits.W))) // correspond to physical bridx
+  val update_wdata_packed = VecInit(update_wdata.map(Seq.fill(2)(_)).reduce(_ ++ _))
+  val updateWayMask       = Wire(Vec(2 * numBr, Bool()))      // correspond to physical bridx
 
   val update_unhashed_idx = io.update.pc >> instOffsetBits
   for (pi <- 0 until numBr) {
-    updateWayMask(2*pi)   := Seq.tabulate(numBr)(li =>
+    updateWayMask(2 * pi) := Seq.tabulate(numBr)(li =>
       io.update.mask(li) && get_phy_br_idx(update_unhashed_idx, li) === pi.U && !io.update.tagePreds(li)
-    ).reduce(_||_)
-    updateWayMask(2*pi+1) := Seq.tabulate(numBr)(li =>
-      io.update.mask(li) && get_phy_br_idx(update_unhashed_idx, li) === pi.U &&  io.update.tagePreds(li)
-    ).reduce(_||_)
+    ).reduce(_ || _)
+    updateWayMask(2 * pi + 1) := Seq.tabulate(numBr)(li =>
+      io.update.mask(li) && get_phy_br_idx(update_unhashed_idx, li) === pi.U && io.update.tagePreds(li)
+    ).reduce(_ || _)
   }
 
   val update_folded_hist = WireInit(0.U.asTypeOf(new AllFoldedHistories(foldedGHistInfos)))
@@ -120,43 +120,49 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
   }
   val update_idx = getIdx(io.update.pc, update_folded_hist)
 
-  //SCTable dual port SRAM reads and writes to the same address processing
+  // SCTable dual port SRAM reads and writes to the same address processing
   val conflict_buffer_valid   = RegInit(false.B)
   val conflict_buffer_data    = RegInit(0.U.asTypeOf(update_wdata_packed))
   val conflict_buffer_idx     = RegInit(0.U.asTypeOf(update_idx))
   val conflict_buffer_waymask = RegInit(0.U.asTypeOf(updateWayMask))
 
-  val write_conflict = update_idx === s0_idx && io.update.mask.reduce(_||_) && io.req.valid
-  val can_write = (conflict_buffer_idx =/= s0_idx || !io.req.valid) && conflict_buffer_valid
+  val write_conflict = update_idx === s0_idx && io.update.mask.reduce(_ || _) && io.req.valid
+  val can_write      = (conflict_buffer_idx =/= s0_idx || !io.req.valid) && conflict_buffer_valid
 
-  when(write_conflict){
+  when(write_conflict) {
     conflict_buffer_valid   := true.B
     conflict_buffer_data    := update_wdata_packed
     conflict_buffer_idx     := update_idx
     conflict_buffer_waymask := updateWayMask
   }
-  when(can_write){
-    conflict_buffer_valid   := false.B
+  when(can_write) {
+    conflict_buffer_valid := false.B
   }
 
-  //Using buffer data for prediction
+  // Using buffer data for prediction
   val use_conflict_data = conflict_buffer_valid && conflict_buffer_idx === s1_idx
-  val conflict_data_bypass = conflict_buffer_data.zip(conflict_buffer_waymask).map {case (data, mask) => Mux(mask, data, 0.U.asTypeOf(data))}
-  val conflict_prediction_data = conflict_data_bypass.sliding(2,2).toSeq.map(VecInit(_))
-  val per_br_ctrs_unshuffled = table.io.r.resp.data.sliding(2,2).toSeq.map(VecInit(_))
-  val per_br_ctrs = VecInit((0 until numBr).map(i => Mux1H(
-    UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
-    per_br_ctrs_unshuffled
-  )))
-  val conflict_br_ctrs = VecInit((0 until numBr).map(i => Mux1H(
-    UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
-    conflict_prediction_data
-  )))
+  val conflict_data_bypass = conflict_buffer_data.zip(conflict_buffer_waymask).map { case (data, mask) =>
+    Mux(mask, data, 0.U.asTypeOf(data))
+  }
+  val conflict_prediction_data = conflict_data_bypass.sliding(2, 2).toSeq.map(VecInit(_))
+  val per_br_ctrs_unshuffled   = table.io.r.resp.data.sliding(2, 2).toSeq.map(VecInit(_))
+  val per_br_ctrs = VecInit((0 until numBr).map(i =>
+    Mux1H(
+      UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
+      per_br_ctrs_unshuffled
+    )
+  ))
+  val conflict_br_ctrs = VecInit((0 until numBr).map(i =>
+    Mux1H(
+      UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
+      conflict_prediction_data
+    )
+  ))
 
   io.resp.ctrs := Mux(use_conflict_data, conflict_br_ctrs, per_br_ctrs)
 
   table.io.w.apply(
-    valid = (io.update.mask.reduce(_||_) && !write_conflict) || can_write,
+    valid = (io.update.mask.reduce(_ || _) && !write_conflict) || can_write,
     data = Mux(can_write, conflict_buffer_data, update_wdata_packed),
     setIdx = Mux(can_write, conflict_buffer_idx, update_idx),
     waymask = Mux(can_write, conflict_buffer_waymask.asUInt, updateWayMask.asUInt)
@@ -165,44 +171,49 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
   val wrBypassEntries = 16
 
   // let it corresponds to logical brIdx
-  val wrbypasses = Seq.fill(numBr)(Module(new WrBypass(SInt(ctrBits.W), wrBypassEntries, log2Ceil(nRows), numWays=2)))
+  val wrbypasses = Seq.fill(numBr)(Module(new WrBypass(SInt(ctrBits.W), wrBypassEntries, log2Ceil(nRows), numWays = 2)))
 
   for (pi <- 0 until numBr) {
     val br_lidx = get_lgc_br_idx(update_unhashed_idx, pi.U(log2Ceil(numBr).W))
 
     val wrbypass_io = Mux1H(UIntToOH(br_lidx, numBr), wrbypasses.map(_.io))
 
-    val ctrPos = Mux1H(UIntToOH(br_lidx, numBr), io.update.tagePreds)
-    val bypass_ctr = wrbypass_io.hit_data(ctrPos)
-    val previous_ctr = Mux1H(UIntToOH(br_lidx, numBr), io.update.oldCtrs)
+    val ctrPos        = Mux1H(UIntToOH(br_lidx, numBr), io.update.tagePreds)
+    val bypass_ctr    = wrbypass_io.hit_data(ctrPos)
+    val previous_ctr  = Mux1H(UIntToOH(br_lidx, numBr), io.update.oldCtrs)
     val hit_and_valid = wrbypass_io.hit && bypass_ctr.valid
-    val oldCtr = Mux(hit_and_valid, bypass_ctr.bits, previous_ctr)
-    val taken = Mux1H(UIntToOH(br_lidx, numBr), io.update.takens)
+    val oldCtr        = Mux(hit_and_valid, bypass_ctr.bits, previous_ctr)
+    val taken         = Mux1H(UIntToOH(br_lidx, numBr), io.update.takens)
     update_wdata(pi) := ctrUpdate(oldCtr, taken)
   }
 
-  val per_br_update_wdata_packed = update_wdata_packed.sliding(2,2).map(VecInit(_)).toSeq
-  val per_br_update_way_mask = updateWayMask.sliding(2,2).map(VecInit(_)).toSeq
+  val per_br_update_wdata_packed = update_wdata_packed.sliding(2, 2).map(VecInit(_)).toSeq
+  val per_br_update_way_mask     = updateWayMask.sliding(2, 2).map(VecInit(_)).toSeq
   for (li <- 0 until numBr) {
     val wrbypass = wrbypasses(li)
-    val br_pidx = get_phy_br_idx(update_unhashed_idx, li)
-    wrbypass.io.wen := io.update.mask(li)
-    wrbypass.io.write_idx := update_idx
+    val br_pidx  = get_phy_br_idx(update_unhashed_idx, li)
+    wrbypass.io.wen        := io.update.mask(li)
+    wrbypass.io.write_idx  := update_idx
     wrbypass.io.write_data := Mux1H(UIntToOH(br_pidx, numBr), per_br_update_wdata_packed)
     wrbypass.io.write_way_mask.map(_ := Mux1H(UIntToOH(br_pidx, numBr), per_br_update_way_mask))
   }
 
-
   val u = io.update
-  XSDebug(io.req.valid,
+  XSDebug(
+    io.req.valid,
     p"scTableReq: pc=0x${Hexadecimal(io.req.bits.pc)}, " +
-    p"s0_idx=${s0_idx}\n")
-  XSDebug(RegNext(io.req.valid),
+      p"s0_idx=${s0_idx}\n"
+  )
+  XSDebug(
+    RegNext(io.req.valid),
     p"scTableResp: s1_idx=${s1_idx}," +
-    p"ctr:${io.resp.ctrs}\n")
-  XSDebug(io.update.mask.reduce(_||_),
+      p"ctr:${io.resp.ctrs}\n"
+  )
+  XSDebug(
+    io.update.mask.reduce(_ || _),
     p"update Table: pc:${Hexadecimal(u.pc)}, " +
-    p"tageTakens:${u.tagePreds}, taken:${u.takens}, oldCtr:${u.oldCtrs}\n")
+      p"tageTakens:${u.tagePreds}, taken:${u.takens}, oldCtr:${u.oldCtrs}\n"
+  )
 }
 
 class SCThreshold(val ctrBits: Int = 6)(implicit p: Parameters) extends SCBundle {
@@ -210,18 +221,20 @@ class SCThreshold(val ctrBits: Int = 6)(implicit p: Parameters) extends SCBundle
   def satPos(ctr: UInt = this.ctr) = ctr === ((1.U << ctrBits) - 1.U)
   def satNeg(ctr: UInt = this.ctr) = ctr === 0.U
   def neutralVal = (1 << (ctrBits - 1)).U
-  val thres = UInt(8.W)
-  def initVal = 6.U
-  def minThres = 6.U
-  def maxThres = 31.U
+  val thres      = UInt(8.W)
+  def initVal    = 6.U
+  def minThres   = 6.U
+  def maxThres   = 31.U
   def update(cause: Bool): SCThreshold = {
-    val res = Wire(new SCThreshold(this.ctrBits))
+    val res    = Wire(new SCThreshold(this.ctrBits))
     val newCtr = satUpdate(this.ctr, this.ctrBits, cause)
-    val newThres = Mux(res.satPos(newCtr) && this.thres <= maxThres, this.thres + 2.U,
-                      Mux(res.satNeg(newCtr) && this.thres >= minThres, this.thres - 2.U,
-                      this.thres))
+    val newThres = Mux(
+      res.satPos(newCtr) && this.thres <= maxThres,
+      this.thres + 2.U,
+      Mux(res.satNeg(newCtr) && this.thres >= minThres, this.thres - 2.U, this.thres)
+    )
     res.thres := newThres
-    res.ctr := Mux(res.satPos(newCtr) || res.satNeg(newCtr), res.neutralVal, newCtr)
+    res.ctr   := Mux(res.satPos(newCtr) || res.satNeg(newCtr), res.neutralVal, newCtr)
     // XSDebug(true.B, p"scThres Update: cause${cause} newCtr ${newCtr} newThres ${newThres}\n")
     res
   }
@@ -229,56 +242,55 @@ class SCThreshold(val ctrBits: Int = 6)(implicit p: Parameters) extends SCBundle
 
 object SCThreshold {
   def apply(bits: Int)(implicit p: Parameters) = {
-    val t = Wire(new SCThreshold(ctrBits=bits))
-    t.ctr := t.neutralVal
+    val t = Wire(new SCThreshold(ctrBits = bits))
+    t.ctr   := t.neutralVal
     t.thres := t.initVal
     t
   }
 }
 
-
 trait HasSC extends HasSCParameter with HasPerfEvents { this: Tage =>
   val update_on_mispred, update_on_unconf = WireInit(0.U.asTypeOf(Vec(TageBanks, Bool())))
-  var sc_fh_info = Set[FoldedHistoryInfo]()
+  var sc_fh_info                          = Set[FoldedHistoryInfo]()
   if (EnableSC) {
     val scTables = SCTableInfos.map {
       case (nRows, ctrBits, histLen) => {
-        val t = Module(new SCTable(nRows/TageBanks, ctrBits, histLen))
+        val t   = Module(new SCTable(nRows / TageBanks, ctrBits, histLen))
         val req = t.io.req
-        req.valid := io.s0_fire(3)
-        req.bits.pc := s0_pc_dup(3)
+        req.valid            := io.s0_fire(3)
+        req.bits.pc          := s0_pc_dup(3)
         req.bits.folded_hist := io.in.bits.folded_hist(3)
-        req.bits.ghist := DontCare
-        if (!EnableSC) {t.io.update := DontCare}
+        req.bits.ghist       := DontCare
+        if (!EnableSC) { t.io.update := DontCare }
         t
       }
     }
-    sc_fh_info = scTables.map(_.getFoldedHistoryInfo).reduce(_++_).toSet
+    sc_fh_info = scTables.map(_.getFoldedHistoryInfo).reduce(_ ++ _).toSet
 
-    val scThresholds = List.fill(TageBanks)(RegInit(SCThreshold(5)))
+    val scThresholds  = List.fill(TageBanks)(RegInit(SCThreshold(5)))
     val useThresholds = VecInit(scThresholds map (_.thres))
 
-    def sign(x: SInt) = x(x.getWidth-1)
-    def pos(x: SInt) = !sign(x)
-    def neg(x: SInt) = sign(x)
+    def sign(x: SInt) = x(x.getWidth - 1)
+    def pos(x:  SInt) = !sign(x)
+    def neg(x:  SInt) = sign(x)
 
     def aboveThreshold(scSum: SInt, tagePvdr: SInt, threshold: UInt): Bool = {
       val signedThres = threshold.zext
-      val totalSum = scSum +& tagePvdr
-      (scSum >  signedThres - tagePvdr) && pos(totalSum) ||
+      val totalSum    = scSum +& tagePvdr
+      (scSum > signedThres - tagePvdr) && pos(totalSum) ||
       (scSum < -signedThres - tagePvdr) && neg(totalSum)
     }
     val updateThresholds = VecInit(useThresholds map (t => (t << 3) +& 21.U))
 
     val s1_scResps = VecInit(scTables.map(t => t.io.resp))
 
-    val scUpdateMask = WireInit(0.U.asTypeOf(Vec(numBr, Vec(SCNTables, Bool()))))
+    val scUpdateMask      = WireInit(0.U.asTypeOf(Vec(numBr, Vec(SCNTables, Bool()))))
     val scUpdateTagePreds = Wire(Vec(TageBanks, Bool()))
-    val scUpdateTakens = Wire(Vec(TageBanks, Bool()))
-    val scUpdateOldCtrs = Wire(Vec(numBr, Vec(SCNTables, SInt(SCCtrBits.W))))
+    val scUpdateTakens    = Wire(Vec(TageBanks, Bool()))
+    val scUpdateOldCtrs   = Wire(Vec(numBr, Vec(SCNTables, SInt(SCCtrBits.W))))
     scUpdateTagePreds := DontCare
-    scUpdateTakens := DontCare
-    scUpdateOldCtrs := DontCare
+    scUpdateTakens    := DontCare
+    scUpdateOldCtrs   := DontCare
 
     val updateSCMeta = updateMeta.scMeta.get
 
@@ -292,7 +304,7 @@ trait HasSC extends HasSCParameter with HasPerfEvents { this: Tage =>
     // for sc ctrs
     def getCentered(ctr: SInt): SInt = Cat(ctr, 1.U(1.W)).asSInt
     // for tage ctrs, (2*(ctr-4)+1)*8
-    def getPvdrCentered(ctr: UInt): SInt = Cat(ctr ^ (1 << (TageCtrBits-1)).U, 1.U(1.W), 0.U(3.W)).asSInt
+    def getPvdrCentered(ctr: UInt): SInt = Cat(ctr ^ (1 << (TageCtrBits - 1)).U, 1.U(1.W), 0.U(3.W)).asSInt
 
     val scMeta = resp_meta.scMeta.get
     scMeta := DontCare
@@ -303,38 +315,36 @@ trait HasSC extends HasSCParameter with HasPerfEvents { this: Tage =>
           ParallelSingedExpandingAdd(s1_scResps map (r => getCentered(r.ctrs(w)(i)))) // TODO: rewrite with wallace tree
         }
       )
-      val s2_scTableSums = RegEnable(s1_scTableSums, io.s1_fire(3))
+      val s2_scTableSums         = RegEnable(s1_scTableSums, io.s1_fire(3))
       val s2_tagePrvdCtrCentered = getPvdrCentered(RegEnable(s1_providerResps(w).ctr, io.s1_fire(3)))
-      val s2_totalSums = s2_scTableSums.map(_ +& s2_tagePrvdCtrCentered)
-      val s2_sumAboveThresholds = VecInit((0 to 1).map(i => aboveThreshold(s2_scTableSums(i), s2_tagePrvdCtrCentered, useThresholds(w))))
+      val s2_totalSums           = s2_scTableSums.map(_ +& s2_tagePrvdCtrCentered)
+      val s2_sumAboveThresholds =
+        VecInit((0 to 1).map(i => aboveThreshold(s2_scTableSums(i), s2_tagePrvdCtrCentered, useThresholds(w))))
       val s2_scPreds = VecInit(s2_totalSums.map(_ >= 0.S))
 
-      val s2_scResps = VecInit(RegEnable(s1_scResps, io.s1_fire(3)).map(_.ctrs(w)))
-      val s2_scCtrs = VecInit(s2_scResps.map(_(s2_tageTakens_dup(3)(w).asUInt)))
+      val s2_scResps   = VecInit(RegEnable(s1_scResps, io.s1_fire(3)).map(_.ctrs(w)))
+      val s2_scCtrs    = VecInit(s2_scResps.map(_(s2_tageTakens_dup(3)(w).asUInt)))
       val s2_chooseBit = s2_tageTakens_dup(3)(w)
 
       val s2_pred =
-        Mux(s2_provideds(w) && s2_sumAboveThresholds(s2_chooseBit),
-          s2_scPreds(s2_chooseBit),
-          s2_tageTakens_dup(3)(w)
-        )
+        Mux(s2_provideds(w) && s2_sumAboveThresholds(s2_chooseBit), s2_scPreds(s2_chooseBit), s2_tageTakens_dup(3)(w))
 
       val s3_disagree = RegEnable(s2_disagree, io.s2_fire(3))
       io.out.last_stage_spec_info.sc_disagree.map(_ := s3_disagree)
 
-      scMeta.scPreds(w)    := RegEnable(s2_scPreds(s2_chooseBit), io.s2_fire(3))
-      scMeta.ctrs(w)       := RegEnable(s2_scCtrs, io.s2_fire(3))
+      scMeta.scPreds(w) := RegEnable(s2_scPreds(s2_chooseBit), io.s2_fire(3))
+      scMeta.ctrs(w)    := RegEnable(s2_scCtrs, io.s2_fire(3))
 
-      when (s2_provideds(w)) {
+      when(s2_provideds(w)) {
         s2_sc_used(w) := true.B
-        s2_unconf(w) := !s2_sumAboveThresholds(s2_chooseBit)
-        s2_conf(w) := s2_sumAboveThresholds(s2_chooseBit)
+        s2_unconf(w)  := !s2_sumAboveThresholds(s2_chooseBit)
+        s2_conf(w)    := s2_sumAboveThresholds(s2_chooseBit)
         // Use prediction from Statistical Corrector
         XSDebug(p"---------tage_bank_${w} provided so that sc used---------\n")
-        when (s2_sumAboveThresholds(s2_chooseBit)) {
-          val pred = s2_scPreds(s2_chooseBit)
+        when(s2_sumAboveThresholds(s2_chooseBit)) {
+          val pred     = s2_scPreds(s2_chooseBit)
           val debug_pc = Cat(debug_pc_s2, w.U, 0.U(instOffsetBits.W))
-          s2_agree(w) := s2_tageTakens_dup(3)(w) === pred
+          s2_agree(w)    := s2_tageTakens_dup(3)(w) === pred
           s2_disagree(w) := s2_tageTakens_dup(3)(w) =/= pred
           // fit to always-taken condition
           // io.out.s2.full_pred.br_taken_mask(w) := pred
@@ -342,73 +352,76 @@ trait HasSC extends HasSCParameter with HasPerfEvents { this: Tage =>
         }
       }
 
-      val s3_pred_dup = io.s2_fire.map(f => RegEnable(s2_pred, f))
+      val s3_pred_dup   = io.s2_fire.map(f => RegEnable(s2_pred, f))
       val sc_enable_dup = dup(RegNext(io.ctrl.sc_enable))
-      for (sc_enable & fp & s3_pred <-
-        sc_enable_dup zip io.out.s3.full_pred zip s3_pred_dup) {
-          when (sc_enable) {
-            fp.br_taken_mask(w) := s3_pred
-          }
+      for (
+        sc_enable & fp & s3_pred <-
+          sc_enable_dup zip io.out.s3.full_pred zip s3_pred_dup
+      ) {
+        when(sc_enable) {
+          fp.br_taken_mask(w) := s3_pred
+        }
       }
 
       val updateTageMeta = updateMeta
-      when (updateValids(w) && updateTageMeta.providers(w).valid) {
-        val scPred = updateSCMeta.scPreds(w)
-        val tagePred = updateTageMeta.takens(w)
-        val taken = update.br_taken_mask(w)
-        val scOldCtrs = updateSCMeta.ctrs(w)
-        val pvdrCtr = updateTageMeta.providerResps(w).ctr
-        val tableSum = ParallelSingedExpandingAdd(scOldCtrs.map(getCentered))
-        val totalSumAbs = (tableSum +& getPvdrCentered(pvdrCtr)).abs.asUInt
-        val updateThres = updateThresholds(w)
+      when(updateValids(w) && updateTageMeta.providers(w).valid) {
+        val scPred            = updateSCMeta.scPreds(w)
+        val tagePred          = updateTageMeta.takens(w)
+        val taken             = update.br_taken_mask(w)
+        val scOldCtrs         = updateSCMeta.ctrs(w)
+        val pvdrCtr           = updateTageMeta.providerResps(w).ctr
+        val tableSum          = ParallelSingedExpandingAdd(scOldCtrs.map(getCentered))
+        val totalSumAbs       = (tableSum +& getPvdrCentered(pvdrCtr)).abs.asUInt
+        val updateThres       = updateThresholds(w)
         val sumAboveThreshold = aboveThreshold(tableSum, getPvdrCentered(pvdrCtr), updateThres)
         scUpdateTagePreds(w) := tagePred
-        scUpdateTakens(w) := taken
-        (scUpdateOldCtrs(w) zip scOldCtrs).foreach{case (t, c) => t := c}
-
-        update_sc_used(w) := true.B
-        update_unconf(w) := !sumAboveThreshold
-        update_conf(w) := sumAboveThreshold
-        update_agree(w) := scPred === tagePred
-        update_disagree(w) := scPred =/= tagePred
+        scUpdateTakens(w)    := taken
+        (scUpdateOldCtrs(w) zip scOldCtrs).foreach { case (t, c) => t := c }
+
+        update_sc_used(w)    := true.B
+        update_unconf(w)     := !sumAboveThreshold
+        update_conf(w)       := sumAboveThreshold
+        update_agree(w)      := scPred === tagePred
+        update_disagree(w)   := scPred =/= tagePred
         sc_corr_tage_misp(w) := scPred === taken && tagePred =/= taken && update_conf(w)
         sc_misp_tage_corr(w) := scPred =/= taken && tagePred === taken && update_conf(w)
 
         val thres = useThresholds(w)
-        when (scPred =/= tagePred && totalSumAbs >= thres - 4.U && totalSumAbs <= thres - 2.U) {
+        when(scPred =/= tagePred && totalSumAbs >= thres - 4.U && totalSumAbs <= thres - 2.U) {
           val newThres = scThresholds(w).update(scPred =/= taken)
           scThresholds(w) := newThres
           XSDebug(p"scThres $w update: old ${useThresholds(w)} --> new ${newThres.thres}\n")
         }
 
-        when (scPred =/= taken || !sumAboveThreshold) {
+        when(scPred =/= taken || !sumAboveThreshold) {
           scUpdateMask(w).foreach(_ := true.B)
-          XSDebug(tableSum < 0.S,
+          XSDebug(
+            tableSum < 0.S,
             p"scUpdate: bank(${w}), scPred(${scPred}), tagePred(${tagePred}), " +
-            p"scSum(-${tableSum.abs}), mispred: sc(${scPred =/= taken}), tage(${updateMisPreds(w)})\n"
+              p"scSum(-${tableSum.abs}), mispred: sc(${scPred =/= taken}), tage(${updateMisPreds(w)})\n"
           )
-          XSDebug(tableSum >= 0.S,
+          XSDebug(
+            tableSum >= 0.S,
             p"scUpdate: bank(${w}), scPred(${scPred}), tagePred(${tagePred}), " +
-            p"scSum(+${tableSum.abs}), mispred: sc(${scPred =/= taken}), tage(${updateMisPreds(w)})\n"
+              p"scSum(+${tableSum.abs}), mispred: sc(${scPred =/= taken}), tage(${updateMisPreds(w)})\n"
           )
           XSDebug(p"bank(${w}), update: sc: ${updateSCMeta}\n")
           update_on_mispred(w) := scPred =/= taken
-          update_on_unconf(w) := scPred === taken
+          update_on_unconf(w)  := scPred === taken
         }
       }
     }
 
-
     val realWens = scUpdateMask.transpose.map(v => v.reduce(_ | _))
     for (b <- 0 until TageBanks) {
       for (i <- 0 until SCNTables) {
         val realWen = realWens(i)
-        scTables(i).io.update.mask(b) := RegNext(scUpdateMask(b)(i))
+        scTables(i).io.update.mask(b)      := RegNext(scUpdateMask(b)(i))
         scTables(i).io.update.tagePreds(b) := RegEnable(scUpdateTagePreds(b), realWen)
-        scTables(i).io.update.takens(b) := RegEnable(scUpdateTakens(b), realWen)
-        scTables(i).io.update.oldCtrs(b) := RegEnable(scUpdateOldCtrs(b)(i), realWen)
-        scTables(i).io.update.pc := RegEnable(update.pc, realWen)
-        scTables(i).io.update.ghist := RegEnable(io.update.bits.ghist, realWen)
+        scTables(i).io.update.takens(b)    := RegEnable(scUpdateTakens(b), realWen)
+        scTables(i).io.update.oldCtrs(b)   := RegEnable(scUpdateOldCtrs(b)(i), realWen)
+        scTables(i).io.update.pc           := RegEnable(update.pc, realWen)
+        scTables(i).io.update.ghist        := RegEnable(io.update.bits.ghist, realWen)
       }
     }
 
@@ -428,8 +441,8 @@ trait HasSC extends HasSCParameter with HasPerfEvents { this: Tage =>
 
   override val perfEvents = Seq(
     ("tage_tht_hit                  ", PopCount(updateMeta.providers.map(_.valid))),
-    ("sc_update_on_mispred          ", PopCount(update_on_mispred) ),
-    ("sc_update_on_unconf           ", PopCount(update_on_unconf)  ),
+    ("sc_update_on_mispred          ", PopCount(update_on_mispred)),
+    ("sc_update_on_unconf           ", PopCount(update_on_unconf))
   )
   generatePerfEvent()
 }
diff --git a/src/main/scala/xiangshan/frontend/Tage.scala b/src/main/scala/xiangshan/frontend/Tage.scala
index db6d943d1c3..5397f5a7ef2 100644
--- a/src/main/scala/xiangshan/frontend/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/Tage.scala
@@ -16,17 +16,16 @@
 
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
-import utility._
-
+import org.chipsalliance.cde.config.Parameters
+import os.followLink
+import scala.{Tuple2 => &}
 import scala.math.min
 import scala.util.matching.Regex
-import scala.{Tuple2 => &}
-import os.followLink
+import utility._
+import utils._
+import xiangshan._
 
 trait TageParams extends HasBPUConst with HasXSParameter {
   // println(BankTageTableInfos)
@@ -34,17 +33,17 @@ trait TageParams extends HasBPUConst with HasXSParameter {
   // val BankTageNTables = BankTageTableInfos.map(_.size) // Number of tage tables
   // val UBitPeriod = 256
   val TageCtrBits = 3
-  val TickWidth = 7
+  val TickWidth   = 7
 
   val USE_ALT_ON_NA_WIDTH = 4
-  val NUM_USE_ALT_ON_NA = 128
-  def use_alt_idx(pc: UInt) = (pc >> instOffsetBits)(log2Ceil(NUM_USE_ALT_ON_NA)-1, 0)
+  val NUM_USE_ALT_ON_NA   = 128
+  def use_alt_idx(pc: UInt) = (pc >> instOffsetBits)(log2Ceil(NUM_USE_ALT_ON_NA) - 1, 0)
 
   val TotalBits = TageTableInfos.map {
     case (s, h, t) => {
-      s * (1+t+TageCtrBits+1)
+      s * (1 + t + TageCtrBits + 1)
     }
-  }.reduce(_+_)
+  }.reduce(_ + _)
 
   def posUnconf(ctr: UInt) = ctr === (1 << (ctr.getWidth - 1)).U
   def negUnconf(ctr: UInt) = ctr === ((1 << (ctr.getWidth - 1)) - 1).U
@@ -52,46 +51,39 @@ trait TageParams extends HasBPUConst with HasXSParameter {
   def unconf(ctr: UInt) = posUnconf(ctr) || negUnconf(ctr)
 
   val unshuffleBitWidth = log2Ceil(numBr)
-  def get_unshuffle_bits(idx: UInt) = idx(unshuffleBitWidth-1, 0)
+  def get_unshuffle_bits(idx: UInt) = idx(unshuffleBitWidth - 1, 0)
   // xor hashes are reversable
-  def get_phy_br_idx(unhashed_idx: UInt, br_lidx: Int)  = get_unshuffle_bits(unhashed_idx) ^ br_lidx.U(log2Ceil(numBr).W)
+  def get_phy_br_idx(unhashed_idx: UInt, br_lidx: Int) = get_unshuffle_bits(unhashed_idx) ^ br_lidx.U(log2Ceil(numBr).W)
   def get_lgc_br_idx(unhashed_idx: UInt, br_pidx: UInt) = get_unshuffle_bits(unhashed_idx) ^ br_pidx
 
 }
 
 trait HasFoldedHistory {
   val histLen: Int
-  def compute_folded_hist(hist: UInt, l: Int)(histLen: Int) = {
+  def compute_folded_hist(hist: UInt, l: Int)(histLen: Int) =
     if (histLen > 0) {
-      val nChunks = (histLen + l - 1) / l
-      val hist_chunks = (0 until nChunks) map {i =>
-        hist(min((i+1)*l, histLen)-1, i*l)
-      }
+      val nChunks     = (histLen + l - 1) / l
+      val hist_chunks = (0 until nChunks) map { i => hist(min((i + 1) * l, histLen) - 1, i * l) }
       ParallelXOR(hist_chunks)
-    }
-    else 0.U
-  }
+    } else 0.U
   val compute_folded_ghist = compute_folded_hist(_: UInt, _: Int)(histLen)
 }
 
 abstract class TageBundle(implicit p: Parameters)
-  extends XSBundle with TageParams with BPUUtils
+    extends XSBundle with TageParams with BPUUtils
 
 abstract class TageModule(implicit p: Parameters)
-  extends XSModule with TageParams with BPUUtils
-  {}
-
-
+    extends XSModule with TageParams with BPUUtils {}
 
 class TageReq(implicit p: Parameters) extends TageBundle {
-  val pc = UInt(VAddrBits.W)
-  val ghist = UInt(HistoryLength.W)
+  val pc          = UInt(VAddrBits.W)
+  val ghist       = UInt(HistoryLength.W)
   val folded_hist = new AllFoldedHistories(foldedGHistInfos)
 }
 
 class TageResp_meta(implicit p: Parameters) extends TageBundle with TageParams {
   val ctr = UInt(TageCtrBits.W)
-  val u = Bool()
+  val u   = Bool()
 }
 
 class TageResp(implicit p: Parameters) extends TageResp_meta {
@@ -99,59 +91,58 @@ class TageResp(implicit p: Parameters) extends TageResp_meta {
 }
 
 class TageUpdate(implicit p: Parameters) extends TageBundle {
-  val pc = UInt(VAddrBits.W)
+  val pc    = UInt(VAddrBits.W)
   val ghist = UInt(HistoryLength.W)
   // update tag and ctr
-  val mask = Vec(numBr, Bool())
-  val takens = Vec(numBr, Bool())
-  val alloc = Vec(numBr, Bool())
+  val mask    = Vec(numBr, Bool())
+  val takens  = Vec(numBr, Bool())
+  val alloc   = Vec(numBr, Bool())
   val oldCtrs = Vec(numBr, UInt(TageCtrBits.W))
   // update u
-  val uMask = Vec(numBr, Bool())
-  val us = Vec(numBr, Bool())
+  val uMask   = Vec(numBr, Bool())
+  val us      = Vec(numBr, Bool())
   val reset_u = Vec(numBr, Bool())
 }
 
 class TageMeta(implicit p: Parameters)
-  extends TageBundle with HasSCParameter
-{
-  val providers = Vec(numBr, ValidUndirectioned(UInt(log2Ceil(TageNTables).W)))
+    extends TageBundle with HasSCParameter {
+  val providers     = Vec(numBr, ValidUndirectioned(UInt(log2Ceil(TageNTables).W)))
   val providerResps = Vec(numBr, new TageResp_meta)
   // val altProviders = Vec(numBr, ValidUndirectioned(UInt(log2Ceil(TageNTables).W)))
   // val altProviderResps = Vec(numBr, new TageResp)
-  val altUsed = Vec(numBr, Bool())
-  val basecnts = Vec(numBr, UInt(2.W))
-  val allocates = Vec(numBr, UInt(TageNTables.W))
-  val scMeta = if (EnableSC) Some(new SCMeta(SCNTables)) else None
-  val pred_cycle = if (!env.FPGAPlatform) Some(UInt(64.W)) else None
+  val altUsed       = Vec(numBr, Bool())
+  val basecnts      = Vec(numBr, UInt(2.W))
+  val allocates     = Vec(numBr, UInt(TageNTables.W))
+  val scMeta        = if (EnableSC) Some(new SCMeta(SCNTables)) else None
+  val pred_cycle    = if (!env.FPGAPlatform) Some(UInt(64.W)) else None
   val use_alt_on_na = if (!env.FPGAPlatform) Some(Vec(numBr, Bool())) else None
 
-  def altPreds = basecnts.map(_(1))
+  def altPreds      = basecnts.map(_(1))
   def allocateValid = allocates.map(_.orR)
   def altDiffers(i: Int) = basecnts(i)(1) =/= providerResps(i).ctr(TageCtrBits - 1)
-  def takens(i: Int) = Mux(altUsed(i), basecnts(i)(1), providerResps(i).ctr(TageCtrBits-1))
+  def takens(i:     Int) = Mux(altUsed(i), basecnts(i)(1), providerResps(i).ctr(TageCtrBits - 1))
 }
 
 trait TBTParams extends HasXSParameter with TageParams {
-  val BtSize = 2048
+  val BtSize        = 2048
   val bypassEntries = 8
 }
 
-class TageBTable(implicit p: Parameters) extends XSModule with TBTParams{
+class TageBTable(implicit p: Parameters) extends XSModule with TBTParams {
   val io = IO(new Bundle {
-    val req = Flipped(DecoupledIO(UInt(VAddrBits.W))) // s0_pc
-    val s1_cnt     = Output(Vec(numBr,UInt(2.W)))
-    val update_mask = Input(Vec(TageBanks, Bool()))
-    val update_pc = Input(UInt(VAddrBits.W))
-    val update_cnt  = Input(Vec(numBr,UInt(2.W)))
+    val req           = Flipped(DecoupledIO(UInt(VAddrBits.W))) // s0_pc
+    val s1_cnt        = Output(Vec(numBr, UInt(2.W)))
+    val update_mask   = Input(Vec(TageBanks, Bool()))
+    val update_pc     = Input(UInt(VAddrBits.W))
+    val update_cnt    = Input(Vec(numBr, UInt(2.W)))
     val update_takens = Input(Vec(TageBanks, Bool()))
-   // val update  = Input(new TageUpdate)
+    // val update  = Input(new TageUpdate)
   })
 
   val bimAddr = new TableAddr(log2Up(BtSize), instOffsetBits)
 
   // Physical SRAM Size
-  val SRAMSize = 512
+  val SRAMSize  = 512
   val foldWidth = BtSize / SRAMSize
 
   val bt = Module(
@@ -163,31 +154,41 @@ class TageBTable(implicit p: Parameters) extends XSModule with TBTParams{
       shouldReset = false,
       holdRead = true,
       bypassWrite = true
-    ))
+    )
+  )
 
-  val wrbypass = Module(new WrBypass(UInt(2.W), bypassEntries, log2Up(BtSize), numWays = numBr, extraPort = Some(true))) // logical bridx
+  val wrbypass =
+    Module(new WrBypass(
+      UInt(2.W),
+      bypassEntries,
+      log2Up(BtSize),
+      numWays = numBr,
+      extraPort = Some(true)
+    )) // logical bridx
 
   // Power-on reset to weak taken
   val doing_reset = RegInit(true.B)
-  val resetRow = RegInit(0.U(log2Ceil(BtSize).W))
+  val resetRow    = RegInit(0.U(log2Ceil(BtSize).W))
   resetRow := resetRow + doing_reset
-  when (resetRow === (BtSize-1).U) { doing_reset := false.B }
+  when(resetRow === (BtSize - 1).U) {
+    doing_reset := false.B
+  }
 
   // Require power-on reset done before handling any request
   io.req.ready := !doing_reset
 
-  val s0_pc = io.req.bits
+  val s0_pc   = io.req.bits
   val s0_fire = io.req.valid
-  val s0_idx = bimAddr.getIdx(s0_pc)
-  bt.io.r.req.valid := s0_fire
+  val s0_idx  = bimAddr.getIdx(s0_pc)
+  bt.io.r.req.valid       := s0_fire
   bt.io.r.req.bits.setIdx := s0_idx
 
   val s1_idx = RegEnable(s0_idx, s0_fire)
-  //The cached data in wrbypass can participate in prediction
-  val use_wrbypass_data = wrbypass.io.has_conflict.getOrElse(false.B) && wrbypass.io.update_idx.getOrElse(0.U) === s1_idx
+  // The cached data in wrbypass can participate in prediction
+  val use_wrbypass_data =
+    wrbypass.io.has_conflict.getOrElse(false.B) && wrbypass.io.update_idx.getOrElse(0.U) === s1_idx
   val s1_read = Mux(use_wrbypass_data, wrbypass.io.update_data.get, bt.io.r.resp.data)
 
-
   val per_br_ctr = VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_idx, i), numBr), s1_read)))
   io.s1_cnt := per_br_ctr
 
@@ -196,7 +197,7 @@ class TageBTable(implicit p: Parameters) extends XSModule with TBTParams{
 
   val newCtrs = Wire(Vec(numBr, UInt(2.W))) // physical bridx
 
-  wrbypass.io.wen := io.update_mask.reduce(_||_)
+  wrbypass.io.wen       := io.update_mask.reduce(_ || _)
   wrbypass.io.write_idx := u_idx
   wrbypass.io.write_way_mask.map(_ := io.update_mask)
   for (li <- 0 until numBr) {
@@ -204,109 +205,108 @@ class TageBTable(implicit p: Parameters) extends XSModule with TBTParams{
     wrbypass.io.write_data(li) := newCtrs(br_pidx)
   }
 
-
   val oldCtrs =
-    VecInit((0 until numBr).map(pi => {
+    VecInit((0 until numBr).map { pi =>
       val br_lidx = get_lgc_br_idx(u_idx, pi.U(log2Ceil(numBr).W))
-      Mux(wrbypass.io.hit && wrbypass.io.hit_data(br_lidx).valid,
+      Mux(
+        wrbypass.io.hit && wrbypass.io.hit_data(br_lidx).valid,
         wrbypass.io.hit_data(br_lidx).bits,
-        io.update_cnt(br_lidx))
-    }))
+        io.update_cnt(br_lidx)
+      )
+    })
 
   def satUpdate(old: UInt, len: Int, taken: Bool): UInt = {
-    val oldSatTaken = old === ((1 << len)-1).U
+    val oldSatTaken    = old === ((1 << len) - 1).U
     val oldSatNotTaken = old === 0.U
-    Mux(oldSatTaken && taken, ((1 << len)-1).U,
-      Mux(oldSatNotTaken && !taken, 0.U,
-        Mux(taken, old + 1.U, old - 1.U)))
+    Mux(oldSatTaken && taken, ((1 << len) - 1).U, Mux(oldSatNotTaken && !taken, 0.U, Mux(taken, old + 1.U, old - 1.U)))
   }
 
   val newTakens = io.update_takens
-  newCtrs := VecInit((0 until numBr).map(pi => {
+  newCtrs := VecInit((0 until numBr).map { pi =>
     val br_lidx = get_lgc_br_idx(u_idx, pi.U(log2Ceil(numBr).W))
     satUpdate(oldCtrs(pi), 2, newTakens(br_lidx))
-  }))
+  })
 
   val updateWayMask = VecInit((0 until numBr).map(pi =>
     (0 until numBr).map(li =>
       io.update_mask(li) && get_phy_br_idx(u_idx, li) === pi.U
-    ).reduce(_||_)
+    ).reduce(_ || _)
   )).asUInt
 
-  //Using WrBypass to store wdata dual ports for reading and writing to the same address.
-  val write_conflict = u_idx === s0_idx && io.update_mask.reduce(_||_) && s0_fire
-  val can_write = (wrbypass.io.update_idx.get =/= s0_idx || !s0_fire) && wrbypass.io.has_conflict.get
+  // Using WrBypass to store wdata dual ports for reading and writing to the same address.
+  val write_conflict = u_idx === s0_idx && io.update_mask.reduce(_ || _) && s0_fire
+  val can_write      = (wrbypass.io.update_idx.get =/= s0_idx || !s0_fire) && wrbypass.io.has_conflict.get
 
-  wrbypass.io.conflict_valid.get := write_conflict || (can_write && (io.update_mask.reduce(_||_) || doing_reset))
+  wrbypass.io.conflict_valid.get      := write_conflict || (can_write && (io.update_mask.reduce(_ || _) || doing_reset))
   wrbypass.io.conflict_write_data.get := Mux(doing_reset, VecInit(Seq.fill(numBr)(2.U(2.W))), newCtrs)
-  wrbypass.io.conflict_way_mask.get := Mux(doing_reset, Fill(numBr, 1.U(1.W)).asUInt, updateWayMask.asUInt)
+  wrbypass.io.conflict_way_mask.get   := Mux(doing_reset, Fill(numBr, 1.U(1.W)).asUInt, updateWayMask.asUInt)
 
-  val wrbrpass_idx = wrbypass.io.update_idx.get
-  val wrbypass_write_data = wrbypass.io.update_data.get
+  val wrbrpass_idx           = wrbypass.io.update_idx.get
+  val wrbypass_write_data    = wrbypass.io.update_data.get
   val wrbypass_write_waymask = wrbypass.io.update_way_mask.get
   wrbypass.io.conflict_clean.get := can_write
 
   bt.io.w.apply(
-    valid = ((io.update_mask.reduce(_||_) || doing_reset) && !write_conflict) || can_write,
-    data = Mux(can_write, wrbypass_write_data, Mux(doing_reset, VecInit(Seq.fill(numBr)(2.U(2.W))), newCtrs)), // Weak taken
+    valid = ((io.update_mask.reduce(_ || _) || doing_reset) && !write_conflict) || can_write,
+    data =
+      Mux(can_write, wrbypass_write_data, Mux(doing_reset, VecInit(Seq.fill(numBr)(2.U(2.W))), newCtrs)), // Weak taken
     setIdx = Mux(can_write, wrbrpass_idx, Mux(doing_reset, resetRow, u_idx)),
-    waymask = Mux(can_write, wrbypass_write_waymask , Mux(doing_reset, Fill(numBr, 1.U(1.W)).asUInt, updateWayMask))
+    waymask = Mux(can_write, wrbypass_write_waymask, Mux(doing_reset, Fill(numBr, 1.U(1.W)).asUInt, updateWayMask))
   )
 }
 
-
-class TageTable
-(
-  val nRows: Int, val histLen: Int, val tagLen: Int, val tableIdx: Int
+class TageTable(
+    val nRows:    Int,
+    val histLen:  Int,
+    val tagLen:   Int,
+    val tableIdx: Int
 )(implicit p: Parameters)
-  extends TageModule with HasFoldedHistory {
+    extends TageModule with HasFoldedHistory {
   val io = IO(new Bundle() {
-    val req = Flipped(DecoupledIO(new TageReq))
-    val resps = Output(Vec(numBr, Valid(new TageResp)))
+    val req    = Flipped(DecoupledIO(new TageReq))
+    val resps  = Output(Vec(numBr, Valid(new TageResp)))
     val update = Input(new TageUpdate)
   })
 
   class TageEntry() extends TageBundle {
     val valid = Bool()
-    val tag = UInt(tagLen.W)
-    val ctr = UInt(TageCtrBits.W)
+    val tag   = UInt(tagLen.W)
+    val ctr   = UInt(TageCtrBits.W)
   }
 
-
   // Physical SRAM size
   val bankSRAMSize = 512
-  val uSRAMSize = 256
+  val uSRAMSize    = 256
   require(nRows % bankSRAMSize == 0)
   require(isPow2(numBr))
-  val nRowsPerBr = nRows / numBr
-  val nBanks = 4 // Tage banks
-  val bankSize = nRowsPerBr / nBanks
+  val nRowsPerBr    = nRows / numBr
+  val nBanks        = 4 // Tage banks
+  val bankSize      = nRowsPerBr / nBanks
   val bankFoldWidth = if (bankSize >= bankSRAMSize) bankSize / bankSRAMSize else 1
-  val uFoldedWidth = nRowsPerBr / uSRAMSize
+  val uFoldedWidth  = nRowsPerBr / uSRAMSize
   if (bankSize < bankSRAMSize) {
     println(f"warning: tage table $tableIdx has small sram depth of $bankSize")
   }
   val bankIdxWidth = log2Ceil(nBanks)
-  def get_bank_mask(idx: UInt) = VecInit((0 until nBanks).map(idx(bankIdxWidth-1, 0) === _.U))
-  def get_bank_idx(idx: UInt) = idx >> bankIdxWidth
-
+  def get_bank_mask(idx: UInt) = VecInit((0 until nBanks).map(idx(bankIdxWidth - 1, 0) === _.U))
+  def get_bank_idx(idx:  UInt) = idx >> bankIdxWidth
 
   // bypass entries for tage update
   val perBankWrbypassEntries = 8
 
-  val idxFhInfo = (histLen, min(log2Ceil(nRowsPerBr), histLen))
-  val tagFhInfo = (histLen, min(histLen, tagLen))
-  val altTagFhInfo = (histLen, min(histLen, tagLen-1))
-  val allFhInfos = Seq(idxFhInfo, tagFhInfo, altTagFhInfo)
+  val idxFhInfo    = (histLen, min(log2Ceil(nRowsPerBr), histLen))
+  val tagFhInfo    = (histLen, min(histLen, tagLen))
+  val altTagFhInfo = (histLen, min(histLen, tagLen - 1))
+  val allFhInfos   = Seq(idxFhInfo, tagFhInfo, altTagFhInfo)
 
-  def getFoldedHistoryInfo = allFhInfos.filter(_._1 >0).toSet
+  def getFoldedHistoryInfo = allFhInfos.filter(_._1 > 0).toSet
   def compute_tag_and_hash(unhashed_idx: UInt, allFh: AllFoldedHistories) = {
-    val idx_fh = allFh.getHistWithInfo(idxFhInfo).folded_hist
-    val tag_fh = allFh.getHistWithInfo(tagFhInfo).folded_hist
+    val idx_fh     = allFh.getHistWithInfo(idxFhInfo).folded_hist
+    val tag_fh     = allFh.getHistWithInfo(tagFhInfo).folded_hist
     val alt_tag_fh = allFh.getHistWithInfo(altTagFhInfo).folded_hist
     // require(idx_fh.getWidth == log2Ceil(nRows))
-    val idx = (unhashed_idx ^ idx_fh)(log2Ceil(nRowsPerBr)-1, 0)
-    val tag = (unhashed_idx ^ tag_fh ^ (alt_tag_fh << 1)) (tagLen - 1, 0)
+    val idx = (unhashed_idx ^ idx_fh)(log2Ceil(nRowsPerBr) - 1, 0)
+    val tag = (unhashed_idx ^ tag_fh ^ (alt_tag_fh << 1))(tagLen - 1, 0)
     (idx, tag)
   }
 
@@ -314,9 +314,12 @@ class TageTable
 
   if (EnableGHistDiff) {
     val idx_history = compute_folded_ghist(io.req.bits.ghist, log2Ceil(nRowsPerBr))
-    val idx_fh = io.req.bits.folded_hist.getHistWithInfo(idxFhInfo)
-    XSError(idx_history =/= idx_fh.folded_hist, p"tage table $tableIdx has different fh," +
-      p" ghist: ${Binary(idx_history)}, fh: ${Binary(idx_fh.folded_hist)}\n")
+    val idx_fh      = io.req.bits.folded_hist.getHistWithInfo(idxFhInfo)
+    XSError(
+      idx_history =/= idx_fh.folded_hist,
+      p"tage table $tableIdx has different fh," +
+        p" ghist: ${Binary(idx_history)}, fh: ${Binary(idx_fh.folded_hist)}\n"
+    )
   }
   // pc is start address of basic block, most 2 branch inst in block
   // def getUnhashedIdx(pc: UInt) = pc >> (instOffsetBits+log2Ceil(TageBanks))
@@ -325,88 +328,113 @@ class TageTable
   // val s1_pc = io.req.bits.pc
   val req_unhashed_idx = getUnhashedIdx(io.req.bits.pc)
 
-  val us = Module(new FoldedSRAMTemplate(Bool(), set=nRowsPerBr, width=uFoldedWidth, way=numBr, shouldReset=true, extraReset=true, holdRead=true, singlePort=true))
-  us.extra_reset.get := io.update.reset_u.reduce(_||_) && io.update.mask.reduce(_||_)
-
+  val us = Module(new FoldedSRAMTemplate(
+    Bool(),
+    set = nRowsPerBr,
+    width = uFoldedWidth,
+    way = numBr,
+    shouldReset = true,
+    extraReset = true,
+    holdRead = true,
+    singlePort = true
+  ))
+  us.extra_reset.get := io.update.reset_u.reduce(_ || _) && io.update.mask.reduce(_ || _)
 
   val table_banks = Seq.fill(nBanks)(
-    Module(new FoldedSRAMTemplate(new TageEntry, set=bankSize, width=bankFoldWidth, way=numBr, shouldReset=true, holdRead=true, singlePort=true)))
-
+    Module(new FoldedSRAMTemplate(
+      new TageEntry,
+      set = bankSize,
+      width = bankFoldWidth,
+      way = numBr,
+      shouldReset = true,
+      holdRead = true,
+      singlePort = true
+    ))
+  )
 
   val (s0_idx, s0_tag) = compute_tag_and_hash(req_unhashed_idx, io.req.bits.folded_hist)
-  val s0_bank_req_1h = get_bank_mask(s0_idx)
+  val s0_bank_req_1h   = get_bank_mask(s0_idx)
 
-    for (b <- 0 until nBanks) {
-      table_banks(b).io.r.req.valid := io.req.fire && s0_bank_req_1h(b)
-      table_banks(b).io.r.req.bits.setIdx := get_bank_idx(s0_idx)
-    }
+  for (b <- 0 until nBanks) {
+    table_banks(b).io.r.req.valid       := io.req.fire && s0_bank_req_1h(b)
+    table_banks(b).io.r.req.bits.setIdx := get_bank_idx(s0_idx)
+  }
 
-  us.io.r.req.valid := io.req.fire
+  us.io.r.req.valid       := io.req.fire
   us.io.r.req.bits.setIdx := s0_idx
 
-
-  val s1_unhashed_idx = RegEnable(req_unhashed_idx, io.req.fire)
-  val s1_idx = RegEnable(s0_idx, io.req.fire)
-  val s1_tag = RegEnable(s0_tag, io.req.fire)
-  val s1_pc  = RegEnable(io.req.bits.pc, io.req.fire)
-  val s1_bank_req_1h = RegEnable(s0_bank_req_1h, io.req.fire)
+  val s1_unhashed_idx               = RegEnable(req_unhashed_idx, io.req.fire)
+  val s1_idx                        = RegEnable(s0_idx, io.req.fire)
+  val s1_tag                        = RegEnable(s0_tag, io.req.fire)
+  val s1_pc                         = RegEnable(io.req.bits.pc, io.req.fire)
+  val s1_bank_req_1h                = RegEnable(s0_bank_req_1h, io.req.fire)
   val s1_bank_has_write_on_this_req = RegEnable(VecInit(table_banks.map(_.io.w.req.valid)), io.req.valid)
 
   val resp_invalid_by_write = Wire(Bool())
 
-  val tables_r = table_banks.map(_.io.r.resp.data) // s1
-  val unconfs = tables_r.map(r => VecInit(r.map(e => WireInit(unconf(e.ctr))))) // do unconf cal in parallel
-  val hits = tables_r.map(r => VecInit(r.map(e => e.tag === s1_tag && e.valid && !resp_invalid_by_write))) // do tag compare in parallel
+  val tables_r = table_banks.map(_.io.r.resp.data)                               // s1
+  val unconfs  = tables_r.map(r => VecInit(r.map(e => WireInit(unconf(e.ctr))))) // do unconf cal in parallel
+  val hits =
+    tables_r.map(r =>
+      VecInit(r.map(e => e.tag === s1_tag && e.valid && !resp_invalid_by_write))
+    ) // do tag compare in parallel
 
-  val resp_selected = Mux1H(s1_bank_req_1h, tables_r)
+  val resp_selected   = Mux1H(s1_bank_req_1h, tables_r)
   val unconf_selected = Mux1H(s1_bank_req_1h, unconfs)
-  val hit_selected = Mux1H(s1_bank_req_1h, hits)
+  val hit_selected    = Mux1H(s1_bank_req_1h, hits)
   resp_invalid_by_write := Mux1H(s1_bank_req_1h, s1_bank_has_write_on_this_req)
 
-
-  val per_br_resp = VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), resp_selected)))
-  val per_br_unconf = VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), unconf_selected)))
-  val per_br_hit = VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), hit_selected)))
-  val per_br_u    = VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), us.io.r.resp.data)))
+  val per_br_resp =
+    VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), resp_selected)))
+  val per_br_unconf =
+    VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), unconf_selected)))
+  val per_br_hit =
+    VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), hit_selected)))
+  val per_br_u =
+    VecInit((0 until numBr).map(i => Mux1H(UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr), us.io.r.resp.data)))
 
   for (i <- 0 until numBr) {
-    io.resps(i).valid := per_br_hit(i)
-    io.resps(i).bits.ctr := per_br_resp(i).ctr
-    io.resps(i).bits.u := per_br_u(i)
+    io.resps(i).valid       := per_br_hit(i)
+    io.resps(i).bits.ctr    := per_br_resp(i).ctr
+    io.resps(i).bits.u      := per_br_u(i)
     io.resps(i).bits.unconf := per_br_unconf(i)
   }
 
   // Use fetchpc to compute hash
   val update_folded_hist = WireInit(0.U.asTypeOf(new AllFoldedHistories(foldedGHistInfos)))
-  update_folded_hist.getHistWithInfo(idxFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, log2Ceil(nRowsPerBr))
-  update_folded_hist.getHistWithInfo(tagFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, tagLen)
-  update_folded_hist.getHistWithInfo(altTagFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, tagLen-1)
+  update_folded_hist.getHistWithInfo(idxFhInfo).folded_hist := compute_folded_ghist(
+    io.update.ghist,
+    log2Ceil(nRowsPerBr)
+  )
+  update_folded_hist.getHistWithInfo(tagFhInfo).folded_hist    := compute_folded_ghist(io.update.ghist, tagLen)
+  update_folded_hist.getHistWithInfo(altTagFhInfo).folded_hist := compute_folded_ghist(io.update.ghist, tagLen - 1)
 
   val per_bank_update_wdata = Wire(Vec(nBanks, Vec(numBr, new TageEntry))) // corresponds to physical branches
 
-  val update_unhashed_idx = getUnhashedIdx(io.update.pc)
+  val update_unhashed_idx      = getUnhashedIdx(io.update.pc)
   val (update_idx, update_tag) = compute_tag_and_hash(update_unhashed_idx, update_folded_hist)
-  val update_req_bank_1h = get_bank_mask(update_idx)
-  val update_idx_in_bank = get_bank_idx(update_idx)
+  val update_req_bank_1h       = get_bank_mask(update_idx)
+  val update_idx_in_bank       = get_bank_idx(update_idx)
 
   val per_bank_not_silent_update = Wire(Vec(nBanks, Vec(numBr, Bool()))) // corresponds to physical branches
   val per_bank_update_way_mask =
     VecInit((0 until nBanks).map(b =>
-      VecInit((0 until numBr).map(pi => {
+      VecInit((0 until numBr).map { pi =>
         // whether any of the logical branches updates on each slot
         Seq.tabulate(numBr)(li =>
           get_phy_br_idx(update_unhashed_idx, li) === pi.U &&
-          io.update.mask(li)).reduce(_||_) && per_bank_not_silent_update(b)(pi)
-      })).asUInt
+            io.update.mask(li)
+        ).reduce(_ || _) && per_bank_not_silent_update(b)(pi)
+      }).asUInt
     ))
 
   // val silent_update_from_wrbypass = Wire(Bool())
 
   for (b <- 0 until nBanks) {
     table_banks(b).io.w.apply(
-      valid   = per_bank_update_way_mask(b).orR && update_req_bank_1h(b),
-      data    = per_bank_update_wdata(b),
-      setIdx  = update_idx_in_bank,
+      valid = per_bank_update_way_mask(b).orR && update_req_bank_1h(b),
+      data = per_bank_update_wdata(b),
+      setIdx = update_idx_in_bank,
       waymask = per_bank_update_way_mask(b)
     )
   }
@@ -422,16 +450,16 @@ class TageTable
   // We do not want write request block the whole BPU pipeline
   io.req.ready := !powerOnResetState
 
-  val bank_conflict = (0 until nBanks).map(b => table_banks(b).io.w.req.valid && s0_bank_req_1h(b)).reduce(_||_)
+  val bank_conflict = (0 until nBanks).map(b => table_banks(b).io.w.req.valid && s0_bank_req_1h(b)).reduce(_ || _)
   XSPerfAccumulate(f"tage_table_bank_conflict", bank_conflict)
 
   val update_u_idx = update_idx
-  val update_u_way_mask = VecInit((0 until numBr).map(pi => {
+  val update_u_way_mask = VecInit((0 until numBr).map { pi =>
     Seq.tabulate(numBr)(li =>
       get_phy_br_idx(update_unhashed_idx, li) === pi.U &&
-      io.update.uMask(li)
-    ).reduce(_||_)
-  })).asUInt
+        io.update.uMask(li)
+    ).reduce(_ || _)
+  }).asUInt
 
   val update_u_wdata = VecInit((0 until numBr).map(pi =>
     Mux1H(Seq.tabulate(numBr)(li =>
@@ -439,12 +467,16 @@ class TageTable
     ))
   ))
 
-  us.io.w.apply(io.update.mask.reduce(_||_) && io.update.uMask.reduce(_||_), update_u_wdata, update_u_idx, update_u_way_mask)
+  us.io.w.apply(
+    io.update.mask.reduce(_ || _) && io.update.uMask.reduce(_ || _),
+    update_u_wdata,
+    update_u_idx,
+    update_u_way_mask
+  )
 
   // remove silent updates
-  def silentUpdate(ctr: UInt, taken: Bool) = {
+  def silentUpdate(ctr: UInt, taken: Bool) =
     ctr.andR && taken || !ctr.orR && !taken
-  }
 
   val bank_wrbypasses = Seq.fill(nBanks)(Seq.fill(numBr)(
     Module(new WrBypass(UInt(TageCtrBits.W), perBankWrbypassEntries, log2Ceil(bankSize)))
@@ -454,25 +486,29 @@ class TageTable
     val not_silent_update = per_bank_not_silent_update(b)
     for (pi <- 0 until numBr) { // physical brIdx
       val update_wdata = per_bank_update_wdata(b)(pi)
-      val br_lidx = get_lgc_br_idx(update_unhashed_idx, pi.U(log2Ceil(numBr).W))
+      val br_lidx      = get_lgc_br_idx(update_unhashed_idx, pi.U(log2Ceil(numBr).W))
       // this
-      val wrbypass_io = Mux1H(UIntToOH(br_lidx, numBr), bank_wrbypasses(b).map(_.io))
-      val wrbypass_hit = wrbypass_io.hit
-      val wrbypass_ctr = wrbypass_io.hit_data(0).bits
+      val wrbypass_io         = Mux1H(UIntToOH(br_lidx, numBr), bank_wrbypasses(b).map(_.io))
+      val wrbypass_hit        = wrbypass_io.hit
+      val wrbypass_ctr        = wrbypass_io.hit_data(0).bits
       val wrbypass_data_valid = wrbypass_hit && wrbypass_io.hit_data(0).valid
       update_wdata.ctr :=
-        Mux(io.update.alloc(br_lidx),
+        Mux(
+          io.update.alloc(br_lidx),
           Mux(io.update.takens(br_lidx), 4.U, 3.U),
-          Mux(wrbypass_data_valid,
-            inc_ctr(wrbypass_ctr,               io.update.takens(br_lidx)),
+          Mux(
+            wrbypass_data_valid,
+            inc_ctr(wrbypass_ctr, io.update.takens(br_lidx)),
             inc_ctr(io.update.oldCtrs(br_lidx), io.update.takens(br_lidx))
           )
         )
       not_silent_update(pi) :=
-        Mux(wrbypass_data_valid,
-          !silentUpdate(wrbypass_ctr,               io.update.takens(br_lidx)),
-          !silentUpdate(io.update.oldCtrs(br_lidx), io.update.takens(br_lidx))) ||
-        io.update.alloc(br_lidx)
+        Mux(
+          wrbypass_data_valid,
+          !silentUpdate(wrbypass_ctr, io.update.takens(br_lidx)),
+          !silentUpdate(io.update.oldCtrs(br_lidx), io.update.takens(br_lidx))
+        ) ||
+          io.update.alloc(br_lidx)
 
       update_wdata.valid := true.B
       update_wdata.tag   := update_tag
@@ -480,9 +516,9 @@ class TageTable
 
     for (li <- 0 until numBr) {
       val wrbypass = bank_wrbypasses(b)(li)
-      val br_pidx = get_phy_br_idx(update_unhashed_idx, li)
-      wrbypass.io.wen := io.update.mask(li) && update_req_bank_1h(b)
-      wrbypass.io.write_idx := get_bank_idx(update_idx)
+      val br_pidx  = get_phy_br_idx(update_unhashed_idx, li)
+      wrbypass.io.wen           := io.update.mask(li) && update_req_bank_1h(b)
+      wrbypass.io.write_idx     := get_bank_idx(update_idx)
       wrbypass.io.write_data(0) := Mux1H(UIntToOH(br_pidx, numBr), per_bank_update_wdata(b)).ctr
     }
   }
@@ -490,63 +526,86 @@ class TageTable
   for (i <- 0 until numBr) {
     for (b <- 0 until nBanks) {
       val wrbypass = bank_wrbypasses(b)(i)
-      XSPerfAccumulate(f"tage_table_bank_${b}_wrbypass_enq_$i", io.update.mask(i) && update_req_bank_1h(b) && !wrbypass.io.hit)
-      XSPerfAccumulate(f"tage_table_bank_${b}_wrbypass_hit_$i", io.update.mask(i) && update_req_bank_1h(b) &&  wrbypass.io.hit)
+      XSPerfAccumulate(
+        f"tage_table_bank_${b}_wrbypass_enq_$i",
+        io.update.mask(i) && update_req_bank_1h(b) && !wrbypass.io.hit
+      )
+      XSPerfAccumulate(
+        f"tage_table_bank_${b}_wrbypass_hit_$i",
+        io.update.mask(i) && update_req_bank_1h(b) && wrbypass.io.hit
+      )
     }
   }
 
   for (b <- 0 until nBanks) {
     val not_silent_update = per_bank_not_silent_update(b)
-    XSPerfAccumulate(f"tage_table_bank_${b}_real_updates",
-      io.update.mask.reduce(_||_) && update_req_bank_1h(b) && not_silent_update.reduce(_||_))
-    XSPerfAccumulate(f"tage_table_bank_${b}_silent_updates_eliminated",
-      io.update.mask.reduce(_||_) && update_req_bank_1h(b) && !not_silent_update.reduce(_||_))
+    XSPerfAccumulate(
+      f"tage_table_bank_${b}_real_updates",
+      io.update.mask.reduce(_ || _) && update_req_bank_1h(b) && not_silent_update.reduce(_ || _)
+    )
+    XSPerfAccumulate(
+      f"tage_table_bank_${b}_silent_updates_eliminated",
+      io.update.mask.reduce(_ || _) && update_req_bank_1h(b) && !not_silent_update.reduce(_ || _)
+    )
   }
 
   XSPerfAccumulate("tage_table_hits", PopCount(io.resps.map(_.valid)))
 
   for (b <- 0 until nBanks) {
-    XSPerfAccumulate(f"tage_table_bank_${b}_update_req", io.update.mask.reduce(_||_) && update_req_bank_1h(b))
+    XSPerfAccumulate(f"tage_table_bank_${b}_update_req", io.update.mask.reduce(_ || _) && update_req_bank_1h(b))
     for (i <- 0 until numBr) {
-      val li = i
+      val li   = i
       val pidx = get_phy_br_idx(update_unhashed_idx, li)
-      XSPerfAccumulate(f"tage_table_bank_${b}_br_li_${li}_updated", table_banks(b).io.w.req.valid && table_banks(b).io.w.req.bits.waymask.get(pidx))
+      XSPerfAccumulate(
+        f"tage_table_bank_${b}_br_li_${li}_updated",
+        table_banks(b).io.w.req.valid && table_banks(b).io.w.req.bits.waymask.get(pidx)
+      )
       val pi = i
-      XSPerfAccumulate(f"tage_table_bank_${b}_br_pi_${pi}_updated", table_banks(b).io.w.req.valid && table_banks(b).io.w.req.bits.waymask.get(pi))
+      XSPerfAccumulate(
+        f"tage_table_bank_${b}_br_pi_${pi}_updated",
+        table_banks(b).io.w.req.valid && table_banks(b).io.w.req.bits.waymask.get(pi)
+      )
     }
   }
 
-  val u = io.update
-  val b = PriorityEncoder(u.mask)
+  val u  = io.update
+  val b  = PriorityEncoder(u.mask)
   val ub = PriorityEncoder(u.uMask)
-  XSDebug(io.req.fire,
+  XSDebug(
+    io.req.fire,
     p"tableReq: pc=0x${Hexadecimal(io.req.bits.pc)}, " +
-    p"idx=$s0_idx, tag=$s0_tag\n")
+      p"idx=$s0_idx, tag=$s0_tag\n"
+  )
   for (i <- 0 until numBr) {
-    XSDebug(RegNext(io.req.fire) && per_br_hit(i),
+    XSDebug(
+      RegNext(io.req.fire) && per_br_hit(i),
       p"TageTableResp_br_$i: idx=$s1_idx, hit:${per_br_hit(i)}, " +
-      p"ctr:${io.resps(i).bits.ctr}, u:${io.resps(i).bits.u}\n")
-    XSDebug(io.update.mask(i),
+        p"ctr:${io.resps(i).bits.ctr}, u:${io.resps(i).bits.u}\n"
+    )
+    XSDebug(
+      io.update.mask(i),
       p"update Table_br_$i: pc:${Hexadecimal(u.pc)}}, " +
-      p"taken:${u.takens(i)}, alloc:${u.alloc(i)}, oldCtrs:${u.oldCtrs(i)}\n")
+        p"taken:${u.takens(i)}, alloc:${u.alloc(i)}, oldCtrs:${u.oldCtrs(i)}\n"
+    )
     val bank = OHToUInt(update_req_bank_1h.asUInt, nBanks)
-    val pi = get_phy_br_idx(update_unhashed_idx, i)
-    XSDebug(io.update.mask(i),
+    val pi   = get_phy_br_idx(update_unhashed_idx, i)
+    XSDebug(
+      io.update.mask(i),
       p"update Table_$i: writing tag:$update_tag, " +
-      p"ctr: ${per_bank_update_wdata(bank)(pi).ctr} in idx ${update_idx}\n")
+        p"ctr: ${per_bank_update_wdata(bank)(pi).ctr} in idx ${update_idx}\n"
+    )
     XSDebug(RegNext(io.req.fire) && !per_br_hit(i), p"TageTableResp_$i: not hit!\n")
   }
 
   // ------------------------------Debug-------------------------------------
   val valids = RegInit(VecInit(Seq.fill(nRowsPerBr)(false.B)))
-  when (io.update.mask.reduce(_||_)) { valids(update_idx) := true.B }
+  when(io.update.mask.reduce(_ || _))(valids(update_idx) := true.B)
   XSDebug("Table usage:------------------------\n")
   XSDebug("%d out of %d rows are valid\n", PopCount(valids), nRowsPerBr.U)
 
 }
 
-abstract class BaseTage(implicit p: Parameters) extends BasePredictor with TageParams with BPUUtils {
-}
+abstract class BaseTage(implicit p: Parameters) extends BasePredictor with TageParams with BPUUtils {}
 
 class FakeTage(implicit p: Parameters) extends BaseTage {
   io.out <> 0.U.asTypeOf(DecoupledIO(new BasePredictorOutput))
@@ -558,75 +617,76 @@ class FakeTage(implicit p: Parameters) extends BaseTage {
 
 class Tage(implicit p: Parameters) extends BaseTage {
 
-  val resp_meta = Wire(new TageMeta)
+  val resp_meta          = Wire(new TageMeta)
   override val meta_size = resp_meta.getWidth
   val tables = TageTableInfos.zipWithIndex.map {
     case ((nRows, histLen, tagLen), i) => {
       val t = Module(new TageTable(nRows, histLen, tagLen, i))
-      t.io.req.valid := io.s0_fire(1)
-      t.io.req.bits.pc := s0_pc_dup(1)
+      t.io.req.valid            := io.s0_fire(1)
+      t.io.req.bits.pc          := s0_pc_dup(1)
       t.io.req.bits.folded_hist := io.in.bits.folded_hist(1)
-      t.io.req.bits.ghist := io.in.bits.ghist
+      t.io.req.bits.ghist       := io.in.bits.ghist
       t
     }
   }
-  val bt = Module (new TageBTable)
+  val bt = Module(new TageBTable)
   bt.io.req.valid := io.s0_fire(1)
-  bt.io.req.bits := s0_pc_dup(1)
+  bt.io.req.bits  := s0_pc_dup(1)
 
   val bankTickCtrDistanceToTops = Seq.fill(numBr)(RegInit(((1 << TickWidth) - 1).U(TickWidth.W)))
-  val bankTickCtrs = Seq.fill(numBr)(RegInit(0.U(TickWidth.W)))
+  val bankTickCtrs              = Seq.fill(numBr)(RegInit(0.U(TickWidth.W)))
   val useAltOnNaCtrs = RegInit(
     VecInit(Seq.fill(numBr)(
-      VecInit(Seq.fill(NUM_USE_ALT_ON_NA)((1 << (USE_ALT_ON_NA_WIDTH-1)).U(USE_ALT_ON_NA_WIDTH.W)))
+      VecInit(Seq.fill(NUM_USE_ALT_ON_NA)((1 << (USE_ALT_ON_NA_WIDTH - 1)).U(USE_ALT_ON_NA_WIDTH.W)))
     ))
   )
 
-  val tage_fh_info = tables.map(_.getFoldedHistoryInfo).reduce(_++_).toSet
+  val tage_fh_info                  = tables.map(_.getFoldedHistoryInfo).reduce(_ ++ _).toSet
   override def getFoldedHistoryInfo = Some(tage_fh_info)
 
   val s1_resps = VecInit(tables.map(_.io.resps))
 
-  //val s1_bim = io.in.bits.resp_in(0).s1.full_pred
+  // val s1_bim = io.in.bits.resp_in(0).s1.full_pred
   // val s2_bim = RegEnable(s1_bim, io.s1_fire)
 
   val debug_pc_s0 = s0_pc_dup(1)
   val debug_pc_s1 = RegEnable(s0_pc_dup(1), io.s0_fire(1))
   val debug_pc_s2 = RegEnable(debug_pc_s1, io.s1_fire(1))
 
-  val s1_provideds        = Wire(Vec(numBr, Bool()))
-  val s1_providers        = Wire(Vec(numBr, UInt(log2Ceil(TageNTables).W)))
-  val s1_providerResps    = Wire(Vec(numBr, new TageResp))
+  val s1_provideds     = Wire(Vec(numBr, Bool()))
+  val s1_providers     = Wire(Vec(numBr, UInt(log2Ceil(TageNTables).W)))
+  val s1_providerResps = Wire(Vec(numBr, new TageResp))
   // val s1_altProvideds     = Wire(Vec(numBr, Bool()))
   // val s1_altProviders     = Wire(Vec(numBr, UInt(log2Ceil(TageNTables).W)))
   // val s1_altProviderResps = Wire(Vec(numBr, new TageResp))
-  val s1_altUsed          = Wire(Vec(numBr, Bool()))
-  val s1_tageTakens       = Wire(Vec(numBr, Bool()))
-  val s1_basecnts         = Wire(Vec(numBr, UInt(2.W)))
-  val s1_useAltOnNa       = Wire(Vec(numBr, Bool()))
-
-  val s2_provideds        = RegEnable(s1_provideds, io.s1_fire(1))
-  val s2_providers        = RegEnable(s1_providers, io.s1_fire(1))
-  val s2_providerResps    = RegEnable(s1_providerResps, io.s1_fire(1))
+  val s1_altUsed    = Wire(Vec(numBr, Bool()))
+  val s1_tageTakens = Wire(Vec(numBr, Bool()))
+  val s1_basecnts   = Wire(Vec(numBr, UInt(2.W)))
+  val s1_useAltOnNa = Wire(Vec(numBr, Bool()))
+
+  val s2_provideds     = RegEnable(s1_provideds, io.s1_fire(1))
+  val s2_providers     = RegEnable(s1_providers, io.s1_fire(1))
+  val s2_providerResps = RegEnable(s1_providerResps, io.s1_fire(1))
   // val s2_altProvideds     = RegEnable(s1_altProvideds, io.s1_fire)
   // val s2_altProviders     = RegEnable(s1_altProviders, io.s1_fire)
   // val s2_altProviderResps = RegEnable(s1_altProviderResps, io.s1_fire)
-  val s2_altUsed          = RegEnable(s1_altUsed, io.s1_fire(1))
-  val s2_tageTakens_dup   = io.s1_fire.map(f => RegEnable(s1_tageTakens, f))
-  val s2_basecnts         = RegEnable(s1_basecnts, io.s1_fire(1))
-  val s2_useAltOnNa       = RegEnable(s1_useAltOnNa, io.s1_fire(1))
+  val s2_altUsed        = RegEnable(s1_altUsed, io.s1_fire(1))
+  val s2_tageTakens_dup = io.s1_fire.map(f => RegEnable(s1_tageTakens, f))
+  val s2_basecnts       = RegEnable(s1_basecnts, io.s1_fire(1))
+  val s2_useAltOnNa     = RegEnable(s1_useAltOnNa, io.s1_fire(1))
 
-  io.out := io.in.bits.resp_in(0)
+  io.out                 := io.in.bits.resp_in(0)
   io.out.last_stage_meta := resp_meta.asUInt
 
   val resp_s2 = io.out.s2
 
   // Update logic
   val u_valid = io.update.valid
-  val update = io.update.bits
+  val update  = io.update.bits
   val updateValids = VecInit((0 until TageBanks).map(w =>
-      update.ftb_entry.brValids(w) && u_valid && !update.ftb_entry.always_taken(w) &&
-      !(PriorityEncoder(update.br_taken_mask) < w.U)))
+    update.ftb_entry.brValids(w) && u_valid && !update.ftb_entry.always_taken(w) &&
+      !(PriorityEncoder(update.br_taken_mask) < w.U)
+  ))
 
   val updateMeta = update.meta.asTypeOf(new TageMeta)
 
@@ -635,51 +695,53 @@ class Tage(implicit p: Parameters) extends BaseTage {
   val updateResetU  = WireInit(0.U.asTypeOf(Vec(numBr, Bool()))) // per predictor
   val updateTakens  = Wire(Vec(numBr, Vec(TageNTables, Bool())))
   val updateAlloc   = WireInit(0.U.asTypeOf(Vec(numBr, Vec(TageNTables, Bool()))))
-  val updateOldCtrs  = Wire(Vec(numBr, Vec(TageNTables, UInt(TageCtrBits.W))))
+  val updateOldCtrs = Wire(Vec(numBr, Vec(TageNTables, UInt(TageCtrBits.W))))
   val updateU       = Wire(Vec(numBr, Vec(TageNTables, Bool())))
   val updatebcnt    = Wire(Vec(TageBanks, UInt(2.W)))
   val baseupdate    = WireInit(0.U.asTypeOf(Vec(TageBanks, Bool())))
   val bUpdateTakens = Wire(Vec(TageBanks, Bool()))
   updateTakens  := DontCare
-  updateOldCtrs  := DontCare
+  updateOldCtrs := DontCare
   updateU       := DontCare
 
   val updateMisPreds = update.mispred_mask
 
   class TageTableInfo(implicit p: Parameters) extends XSBundle {
-    val resp = new TageResp
-    val tableIdx = UInt(log2Ceil(TageNTables).W)
+    val resp              = new TageResp
+    val tableIdx          = UInt(log2Ceil(TageNTables).W)
     val use_alt_on_unconf = Bool()
   }
   // access tag tables and output meta info
 
   for (i <- 0 until numBr) {
-    val useAltCtr = Mux1H(UIntToOH(use_alt_idx(s1_pc_dup(0)), NUM_USE_ALT_ON_NA), useAltOnNaCtrs(i))
-    val useAltOnNa = useAltCtr(USE_ALT_ON_NA_WIDTH-1) // highest bit
+    val useAltCtr  = Mux1H(UIntToOH(use_alt_idx(s1_pc_dup(0)), NUM_USE_ALT_ON_NA), useAltOnNaCtrs(i))
+    val useAltOnNa = useAltCtr(USE_ALT_ON_NA_WIDTH - 1) // highest bit
 
     val s1_per_br_resp = VecInit(s1_resps.map(_(i)))
-    val inputRes = s1_per_br_resp.zipWithIndex.map{case (r, idx) => {
-      val tableInfo = Wire(new TageTableInfo)
-      tableInfo.resp := r.bits
-      tableInfo.use_alt_on_unconf := r.bits.unconf && useAltOnNa
-      tableInfo.tableIdx := idx.U(log2Ceil(TageNTables).W)
-      (r.valid, tableInfo)
-    }}
+    val inputRes = s1_per_br_resp.zipWithIndex.map {
+      case (r, idx) => {
+        val tableInfo = Wire(new TageTableInfo)
+        tableInfo.resp              := r.bits
+        tableInfo.use_alt_on_unconf := r.bits.unconf && useAltOnNa
+        tableInfo.tableIdx          := idx.U(log2Ceil(TageNTables).W)
+        (r.valid, tableInfo)
+      }
+    }
     val providerInfo = ParallelPriorityMux(inputRes.reverse)
-    val provided = inputRes.map(_._1).reduce(_||_)
+    val provided     = inputRes.map(_._1).reduce(_ || _)
     // val altProvided = selectedInfo.hasTwo
     // val providerInfo = selectedInfo
     // val altProviderInfo = selectedInfo.second
-    s1_provideds(i)      := provided
-    s1_providers(i)      := providerInfo.tableIdx
-    s1_providerResps(i)  := providerInfo.resp
+    s1_provideds(i)     := provided
+    s1_providers(i)     := providerInfo.tableIdx
+    s1_providerResps(i) := providerInfo.resp
     // s1_altProvideds(i)   := altProvided
     // s1_altProviders(i)   := altProviderInfo.tableIdx
     // s1_altProviderResps(i) := altProviderInfo.resp
 
-    resp_meta.providers(i).valid    := RegEnable(s2_provideds(i), io.s2_fire(1))
-    resp_meta.providers(i).bits     := RegEnable(s2_providers(i), io.s2_fire(1))
-    resp_meta.providerResps(i)      := RegEnable(s2_providerResps(i), io.s2_fire(1))
+    resp_meta.providers(i).valid := RegEnable(s2_provideds(i), io.s2_fire(1))
+    resp_meta.providers(i).bits  := RegEnable(s2_providers(i), io.s2_fire(1))
+    resp_meta.providerResps(i)   := RegEnable(s2_providerResps(i), io.s2_fire(1))
     // resp_meta.altProviders(i).valid := RegEnable(s2_altProvideds(i), io.s2_fire)
     // resp_meta.altProviders(i).bits  := RegEnable(s2_altProviders(i), io.s2_fire)
     // resp_meta.altProviderResps(i)   := RegEnable(s2_altProviderResps(i), io.s2_fire)
@@ -699,48 +761,44 @@ class Tage(implicit p: Parameters) extends BaseTage {
     resp_meta.allocates(i) := RegEnable(allocatableSlots, io.s2_fire(1))
 
     val s1_bimCtr = bt.io.s1_cnt(i)
-    s1_altUsed(i)       := !provided || providerInfo.use_alt_on_unconf
+    s1_altUsed(i) := !provided || providerInfo.use_alt_on_unconf
     s1_tageTakens(i) :=
-      Mux(s1_altUsed(i),
-        s1_bimCtr(1),
-        providerInfo.resp.ctr(TageCtrBits-1)
-      )
-    s1_basecnts(i)      := s1_bimCtr
-    s1_useAltOnNa(i)    := providerInfo.use_alt_on_unconf
+      Mux(s1_altUsed(i), s1_bimCtr(1), providerInfo.resp.ctr(TageCtrBits - 1))
+    s1_basecnts(i)   := s1_bimCtr
+    s1_useAltOnNa(i) := providerInfo.use_alt_on_unconf
 
-    resp_meta.altUsed(i)    := RegEnable(s2_altUsed(i), io.s2_fire(1))
-    resp_meta.basecnts(i)   := RegEnable(s2_basecnts(i), io.s2_fire(1))
+    resp_meta.altUsed(i)  := RegEnable(s2_altUsed(i), io.s2_fire(1))
+    resp_meta.basecnts(i) := RegEnable(s2_basecnts(i), io.s2_fire(1))
 
     val tage_enable_dup = dup(RegNext(io.ctrl.tage_enable))
     for (tage_enable & fp & s2_tageTakens <- tage_enable_dup zip resp_s2.full_pred zip s2_tageTakens_dup) {
-      when (tage_enable) {
+      when(tage_enable) {
         fp.br_taken_mask(i) := s2_tageTakens(i)
       }
     }
 
-    //---------------- update logics below ------------------//
-    val hasUpdate = updateValids(i)
+    // ---------------- update logics below ------------------//
+    val hasUpdate     = updateValids(i)
     val updateMispred = updateMisPreds(i)
-    val updateTaken = hasUpdate && update.br_taken_mask(i)
-
-    val updateProvided     = updateMeta.providers(i).valid
-    val updateProvider     = updateMeta.providers(i).bits
-    val updateProviderResp = updateMeta.providerResps(i)
-    val updateProviderCorrect = updateProviderResp.ctr(TageCtrBits-1) === updateTaken
-    val updateUseAlt = updateMeta.altUsed(i)
-    val updateAltDiffers = updateMeta.altDiffers(i)
-    val updateAltIdx = use_alt_idx(update.pc)
-    val updateUseAltCtr = Mux1H(UIntToOH(updateAltIdx, NUM_USE_ALT_ON_NA), useAltOnNaCtrs(i))
-    val updateAltPred = updateMeta.altPreds(i)
-    val updateAltCorrect = updateAltPred === updateTaken
-
-
-    val updateProviderWeakTaken = posUnconf(updateProviderResp.ctr)
+    val updateTaken   = hasUpdate && update.br_taken_mask(i)
+
+    val updateProvided        = updateMeta.providers(i).valid
+    val updateProvider        = updateMeta.providers(i).bits
+    val updateProviderResp    = updateMeta.providerResps(i)
+    val updateProviderCorrect = updateProviderResp.ctr(TageCtrBits - 1) === updateTaken
+    val updateUseAlt          = updateMeta.altUsed(i)
+    val updateAltDiffers      = updateMeta.altDiffers(i)
+    val updateAltIdx          = use_alt_idx(update.pc)
+    val updateUseAltCtr       = Mux1H(UIntToOH(updateAltIdx, NUM_USE_ALT_ON_NA), useAltOnNaCtrs(i))
+    val updateAltPred         = updateMeta.altPreds(i)
+    val updateAltCorrect      = updateAltPred === updateTaken
+
+    val updateProviderWeakTaken    = posUnconf(updateProviderResp.ctr)
     val updateProviderWeaknotTaken = negUnconf(updateProviderResp.ctr)
-    val updateProviderWeak = unconf(updateProviderResp.ctr)
+    val updateProviderWeak         = unconf(updateProviderResp.ctr)
 
-    when (hasUpdate) {
-      when (updateProvided && updateProviderWeak && updateAltDiffers) {
+    when(hasUpdate) {
+      when(updateProvided && updateProviderWeak && updateAltDiffers) {
         val newCtr = satUpdate(updateUseAltCtr, USE_ALT_ON_NA_WIDTH, updateAltCorrect)
         useAltOnNaCtrs(i)(updateAltIdx) := newCtr
       }
@@ -750,86 +808,101 @@ class Tage(implicit p: Parameters) extends BaseTage {
     XSPerfAccumulate(f"tage_bank_${i}_alt_correct", hasUpdate && updateUseAlt && updateAltCorrect)
     XSPerfAccumulate(f"tage_bank_${i}_alt_wrong", hasUpdate && updateUseAlt && !updateAltCorrect)
     XSPerfAccumulate(f"tage_bank_${i}_alt_differs", hasUpdate && updateAltDiffers)
-    XSPerfAccumulate(f"tage_bank_${i}_use_alt_on_na_ctr_updated", hasUpdate && updateAltDiffers && updateProvided && updateProviderWeak)
-    XSPerfAccumulate(f"tage_bank_${i}_use_alt_on_na_ctr_inc", hasUpdate && updateAltDiffers && updateProvided && updateProviderWeak &&  updateAltCorrect)
-    XSPerfAccumulate(f"tage_bank_${i}_use_alt_on_na_ctr_dec", hasUpdate && updateAltDiffers && updateProvided && updateProviderWeak && !updateAltCorrect)
+    XSPerfAccumulate(
+      f"tage_bank_${i}_use_alt_on_na_ctr_updated",
+      hasUpdate && updateAltDiffers && updateProvided && updateProviderWeak
+    )
+    XSPerfAccumulate(
+      f"tage_bank_${i}_use_alt_on_na_ctr_inc",
+      hasUpdate && updateAltDiffers && updateProvided && updateProviderWeak && updateAltCorrect
+    )
+    XSPerfAccumulate(
+      f"tage_bank_${i}_use_alt_on_na_ctr_dec",
+      hasUpdate && updateAltDiffers && updateProvided && updateProviderWeak && !updateAltCorrect
+    )
 
     XSPerfAccumulate(f"tage_bank_${i}_na", hasUpdate && updateProvided && updateProviderWeak)
-    XSPerfAccumulate(f"tage_bank_${i}_use_na_correct", hasUpdate && updateProvided && updateProviderWeak && !updateUseAlt && !updateMispred)
-    XSPerfAccumulate(f"tage_bank_${i}_use_na_wrong",   hasUpdate && updateProvided && updateProviderWeak && !updateUseAlt &&  updateMispred)
+    XSPerfAccumulate(
+      f"tage_bank_${i}_use_na_correct",
+      hasUpdate && updateProvided && updateProviderWeak && !updateUseAlt && !updateMispred
+    )
+    XSPerfAccumulate(
+      f"tage_bank_${i}_use_na_wrong",
+      hasUpdate && updateProvided && updateProviderWeak && !updateUseAlt && updateMispred
+    )
 
     updateMeta.use_alt_on_na.map(uaon => XSPerfAccumulate(f"tage_bank_${i}_use_alt_on_na", hasUpdate && uaon(i)))
 
-    when (hasUpdate) {
-      when (updateProvided) {
-        updateMask(i)(updateProvider) := true.B
-        updateUMask(i)(updateProvider) := updateAltDiffers
-        updateU(i)(updateProvider) := updateProviderCorrect
-        updateTakens(i)(updateProvider) := updateTaken
+    when(hasUpdate) {
+      when(updateProvided) {
+        updateMask(i)(updateProvider)    := true.B
+        updateUMask(i)(updateProvider)   := updateAltDiffers
+        updateU(i)(updateProvider)       := updateProviderCorrect
+        updateTakens(i)(updateProvider)  := updateTaken
         updateOldCtrs(i)(updateProvider) := updateProviderResp.ctr
-        updateAlloc(i)(updateProvider) := false.B
+        updateAlloc(i)(updateProvider)   := false.B
       }
     }
 
     // update base table if used base table to predict
-    baseupdate(i) := hasUpdate && updateUseAlt
-    updatebcnt(i) := updateMeta.basecnts(i)
+    baseupdate(i)    := hasUpdate && updateUseAlt
+    updatebcnt(i)    := updateMeta.basecnts(i)
     bUpdateTakens(i) := updateTaken
 
-    val needToAllocate = hasUpdate && updateMispred && !(updateUseAlt && updateProviderCorrect && updateProvided)
+    val needToAllocate  = hasUpdate && updateMispred && !(updateUseAlt && updateProviderCorrect && updateProvided)
     val allocatableMask = updateMeta.allocates(i)
-    val canAllocate = updateMeta.allocateValid(i)
+    val canAllocate     = updateMeta.allocateValid(i)
 
     val allocLFSR = random.LFSR(width = 15)(TageNTables - 1, 0)
-    val longerHistoryTableMask = ~(LowerMask(UIntToOH(updateProvider), TageNTables) & Fill(TageNTables, updateProvided.asUInt))
-    val canAllocMask = allocatableMask & longerHistoryTableMask
+    val longerHistoryTableMask =
+      ~(LowerMask(UIntToOH(updateProvider), TageNTables) & Fill(TageNTables, updateProvided.asUInt))
+    val canAllocMask     = allocatableMask & longerHistoryTableMask
     val allocFailureMask = ~allocatableMask & longerHistoryTableMask
-    val tickInc = PopCount(allocFailureMask) > PopCount(canAllocMask)
-    val tickDec = PopCount(canAllocMask) > PopCount(allocFailureMask)
-    val tickIncVal = PopCount(allocFailureMask) - PopCount(canAllocMask)
-    val tickDecVal = PopCount(canAllocMask) - PopCount(allocFailureMask)
-    val tickToPosSat = tickIncVal >= bankTickCtrDistanceToTops(i) && tickInc
-    val tickToNegSat = tickDecVal >= bankTickCtrs(i) && tickDec
-
-    val firstEntry = PriorityEncoder(canAllocMask)
+    val tickInc          = PopCount(allocFailureMask) > PopCount(canAllocMask)
+    val tickDec          = PopCount(canAllocMask) > PopCount(allocFailureMask)
+    val tickIncVal       = PopCount(allocFailureMask) - PopCount(canAllocMask)
+    val tickDecVal       = PopCount(canAllocMask) - PopCount(allocFailureMask)
+    val tickToPosSat     = tickIncVal >= bankTickCtrDistanceToTops(i) && tickInc
+    val tickToNegSat     = tickDecVal >= bankTickCtrs(i) && tickDec
+
+    val firstEntry  = PriorityEncoder(canAllocMask)
     val maskedEntry = PriorityEncoder(canAllocMask & allocLFSR)
-    val allocate = Mux(canAllocMask(maskedEntry), maskedEntry, firstEntry)
+    val allocate    = Mux(canAllocMask(maskedEntry), maskedEntry, firstEntry)
 
-
-    when (needToAllocate) {
+    when(needToAllocate) {
       // val allocate = updateMeta.allocates(i).bits
-      when (tickInc) {
-        when (tickToPosSat) {
-          bankTickCtrs(i) := ((1 << TickWidth) - 1).U
+      when(tickInc) {
+        when(tickToPosSat) {
+          bankTickCtrs(i)              := ((1 << TickWidth) - 1).U
           bankTickCtrDistanceToTops(i) := 0.U
         }.otherwise {
-          bankTickCtrs(i) := bankTickCtrs(i) + tickIncVal
+          bankTickCtrs(i)              := bankTickCtrs(i) + tickIncVal
           bankTickCtrDistanceToTops(i) := bankTickCtrDistanceToTops(i) - tickIncVal
         }
-      }.elsewhen (tickDec) {
-        when (tickToNegSat) {
-          bankTickCtrs(i) := 0.U
+      }.elsewhen(tickDec) {
+        when(tickToNegSat) {
+          bankTickCtrs(i)              := 0.U
           bankTickCtrDistanceToTops(i) := ((1 << TickWidth) - 1).U
         }.otherwise {
-          bankTickCtrs(i) := bankTickCtrs(i) - tickDecVal
+          bankTickCtrs(i)              := bankTickCtrs(i) - tickDecVal
           bankTickCtrDistanceToTops(i) := bankTickCtrDistanceToTops(i) + tickDecVal
         }
       }
-      when (canAllocate) {
-        updateMask(i)(allocate) := true.B
+      when(canAllocate) {
+        updateMask(i)(allocate)   := true.B
         updateTakens(i)(allocate) := updateTaken
-        updateAlloc(i)(allocate) := true.B
-        updateUMask(i)(allocate) := true.B
-        updateU(i)(allocate) := false.B
+        updateAlloc(i)(allocate)  := true.B
+        updateUMask(i)(allocate)  := true.B
+        updateU(i)(allocate)      := false.B
       }
-      when (bankTickCtrs(i) === ((1 << TickWidth) - 1).U) {
-        bankTickCtrs(i) := 0.U
+      when(bankTickCtrs(i) === ((1 << TickWidth) - 1).U) {
+        bankTickCtrs(i)              := 0.U
         bankTickCtrDistanceToTops(i) := ((1 << TickWidth) - 1).U
-        updateResetU(i) := true.B
+        updateResetU(i)              := true.B
       }
     }
     XSPerfAccumulate(f"tage_bank_${i}_update_allocate_failure", needToAllocate && !canAllocate)
-    XSPerfAccumulate(f"tage_bank_${i}_update_allocate_success", needToAllocate &&  canAllocate)
+    XSPerfAccumulate(f"tage_bank_${i}_update_allocate_success", needToAllocate && canAllocate)
     XSPerfAccumulate(s"tage_bank_${i}_mispred", hasUpdate && updateMispred)
     XSPerfAccumulate(s"tage_bank_${i}_reset_u", updateResetU(i))
     for (t <- 0 to TageNTables) {
@@ -844,27 +917,27 @@ class Tage(implicit p: Parameters) extends BaseTage {
       val realWen = realWens(i)
       tables(i).io.update.reset_u(w) := RegNext(updateResetU(w))
       tables(i).io.update.mask(w)    := RegNext(updateMask(w)(i))
-      tables(i).io.update.takens(w) := RegEnable(updateTakens(w)(i), realWen)
-      tables(i).io.update.alloc(w) := RegEnable(updateAlloc(w)(i), realWen)
+      tables(i).io.update.takens(w)  := RegEnable(updateTakens(w)(i), realWen)
+      tables(i).io.update.alloc(w)   := RegEnable(updateAlloc(w)(i), realWen)
       tables(i).io.update.oldCtrs(w) := RegEnable(updateOldCtrs(w)(i), realWen)
 
       tables(i).io.update.uMask(w) := RegEnable(updateUMask(w)(i), realWen)
-      tables(i).io.update.us(w) := RegEnable(updateU(w)(i), realWen)
+      tables(i).io.update.us(w)    := RegEnable(updateU(w)(i), realWen)
       // use fetch pc instead of instruction pc
-      tables(i).io.update.pc := RegEnable(update.pc, realWen)
+      tables(i).io.update.pc    := RegEnable(update.pc, realWen)
       tables(i).io.update.ghist := RegEnable(io.update.bits.ghist, realWen)
     }
   }
-  bt.io.update_mask := RegNext(baseupdate)
-  bt.io.update_cnt := RegEnable(updatebcnt, baseupdate.reduce(_ | _))
-  bt.io.update_pc := RegEnable(update.pc, baseupdate.reduce(_ | _))
+  bt.io.update_mask   := RegNext(baseupdate)
+  bt.io.update_cnt    := RegEnable(updatebcnt, baseupdate.reduce(_ | _))
+  bt.io.update_pc     := RegEnable(update.pc, baseupdate.reduce(_ | _))
   bt.io.update_takens := RegEnable(bUpdateTakens, baseupdate.reduce(_ | _))
 
   // all should be ready for req
   io.s1_ready := tables.map(_.io.req.ready).reduce(_ && _) && bt.io.req.ready
   XSPerfAccumulate(f"tage_write_blocks_read", !io.s1_ready)
 
-  def pred_perf(name: String, cnt: UInt)   = XSPerfAccumulate(s"${name}_at_pred", cnt)
+  def pred_perf(name:   String, cnt: UInt) = XSPerfAccumulate(s"${name}_at_pred", cnt)
   def commit_perf(name: String, cnt: UInt) = XSPerfAccumulate(s"${name}_at_commit", cnt)
   def tage_perf(name: String, pred_cnt: UInt, commit_cnt: UInt) = {
     pred_perf(name, pred_cnt)
@@ -908,27 +981,48 @@ class Tage(implicit p: Parameters) extends BaseTage {
   for (b <- 0 until TageBanks) {
     val m = updateMeta
     // val bri = u.metas(b)
-    XSDebug(updateValids(b), "update(%d): pc=%x, cycle=%d, taken:%b, misPred:%d, bimctr:%d, pvdr(%d):%d, altDiff:%d, pvdrU:%d, pvdrCtr:%d, alloc:%b\n",
-      b.U, update.pc, 0.U, update.br_taken_mask(b), update.mispred_mask(b),
-      0.U, m.providers(b).valid, m.providers(b).bits, m.altDiffers(b), m.providerResps(b).u,
-      m.providerResps(b).ctr, m.allocates(b)
+    XSDebug(
+      updateValids(b),
+      "update(%d): pc=%x, cycle=%d, taken:%b, misPred:%d, bimctr:%d, pvdr(%d):%d, altDiff:%d, pvdrU:%d, pvdrCtr:%d, alloc:%b\n",
+      b.U,
+      update.pc,
+      0.U,
+      update.br_taken_mask(b),
+      update.mispred_mask(b),
+      0.U,
+      m.providers(b).valid,
+      m.providers(b).bits,
+      m.altDiffers(b),
+      m.providerResps(b).u,
+      m.providerResps(b).ctr,
+      m.allocates(b)
     )
   }
   val s2_resps = RegEnable(s1_resps, io.s1_fire(1))
   XSDebug("req: v=%d, pc=0x%x\n", io.s0_fire(1), s0_pc_dup(1))
   XSDebug("s1_fire:%d, resp: pc=%x\n", io.s1_fire(1), debug_pc_s1)
-  XSDebug("s2_fireOnLastCycle: resp: pc=%x, target=%x, hits=%b, takens=%b\n",
-    debug_pc_s2, io.out.s2.getTarget(1), s2_provideds.asUInt, s2_tageTakens_dup(0).asUInt)
+  XSDebug(
+    "s2_fireOnLastCycle: resp: pc=%x, target=%x, hits=%b, takens=%b\n",
+    debug_pc_s2,
+    io.out.s2.getTarget(1),
+    s2_provideds.asUInt,
+    s2_tageTakens_dup(0).asUInt
+  )
 
   for (b <- 0 until TageBanks) {
     for (i <- 0 until TageNTables) {
-      XSDebug("bank(%d)_tage_table(%d): valid:%b, resp_ctr:%d, resp_us:%d\n",
-        b.U, i.U, s2_resps(i)(b).valid, s2_resps(i)(b).bits.ctr, s2_resps(i)(b).bits.u)
+      XSDebug(
+        "bank(%d)_tage_table(%d): valid:%b, resp_ctr:%d, resp_us:%d\n",
+        b.U,
+        i.U,
+        s2_resps(i)(b).valid,
+        s2_resps(i)(b).bits.ctr,
+        s2_resps(i)(b).bits.u
+      )
     }
   }
-    // XSDebug(io.update.valid && updateIsBr, p"update: sc: ${updateSCMeta}\n")
-    // XSDebug(true.B, p"scThres: use(${useThreshold}), update(${updateThreshold})\n")
+  // XSDebug(io.update.valid && updateIsBr, p"update: sc: ${updateSCMeta}\n")
+  // XSDebug(true.B, p"scThres: use(${useThreshold}), update(${updateThreshold})\n")
 }
 
-
 class Tage_SC(implicit p: Parameters) extends Tage with HasSC {}
diff --git a/src/main/scala/xiangshan/frontend/WrBypass.scala b/src/main/scala/xiangshan/frontend/WrBypass.scala
index 949ba24238c..b4d3b916901 100644
--- a/src/main/scala/xiangshan/frontend/WrBypass.scala
+++ b/src/main/scala/xiangshan/frontend/WrBypass.scala
@@ -15,41 +15,47 @@
 ***************************************************************************************/
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import xiangshan._
-import utils._
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
+import xiangshan._
 import xiangshan.cache.mmu.CAMTemplate
 
-class WrBypass[T <: Data](gen: T, val numEntries: Int, val idxWidth: Int,
-  val numWays: Int = 1, val tagWidth: Int = 0, val extraPort: Option[Boolean] = None)(implicit p: Parameters) extends XSModule {
+class WrBypass[T <: Data](
+    gen:            T,
+    val numEntries: Int,
+    val idxWidth:   Int,
+    val numWays:    Int = 1,
+    val tagWidth:   Int = 0,
+    val extraPort:  Option[Boolean] = None
+)(implicit p: Parameters) extends XSModule {
   require(numEntries >= 0)
   require(idxWidth > 0)
   require(numWays >= 1)
   require(tagWidth >= 0)
-  def hasTag = tagWidth > 0
+  def hasTag       = tagWidth > 0
   def multipleWays = numWays > 1
   val io = IO(new Bundle {
-    val wen = Input(Bool())
-    val write_idx = Input(UInt(idxWidth.W))
-    val write_tag = if (hasTag) Some(Input(UInt(tagWidth.W))) else None
-    val write_data = Input(Vec(numWays, gen))
+    val wen            = Input(Bool())
+    val write_idx      = Input(UInt(idxWidth.W))
+    val write_tag      = if (hasTag) Some(Input(UInt(tagWidth.W))) else None
+    val write_data     = Input(Vec(numWays, gen))
     val write_way_mask = if (multipleWays) Some(Input(Vec(numWays, Bool()))) else None
 
-    val conflict_valid = if(extraPort.isDefined) Some(Input(Bool())) else None
-    val conflict_write_data = if(extraPort.isDefined) Some(Input(Vec(numWays, gen))) else None
-    val conflict_way_mask = if(extraPort.isDefined) Some(Input(UInt(numBr.W))) else None
+    val conflict_valid      = if (extraPort.isDefined) Some(Input(Bool())) else None
+    val conflict_write_data = if (extraPort.isDefined) Some(Input(Vec(numWays, gen))) else None
+    val conflict_way_mask   = if (extraPort.isDefined) Some(Input(UInt(numBr.W))) else None
 
-    val hit = Output(Bool())
-    val hit_data = Vec(numWays, Valid(gen))
-    val has_conflict = if(extraPort.isDefined) Some(Output(Bool())) else None
-    val update_idx = if(extraPort.isDefined) Some(Output(UInt(idxWidth.W))) else None
-    val update_data = if(extraPort.isDefined) Some(Output(Vec(numWays, gen))) else None
-    val update_way_mask = if(extraPort.isDefined) Some(Output(UInt(numBr.W))) else None
+    val hit             = Output(Bool())
+    val hit_data        = Vec(numWays, Valid(gen))
+    val has_conflict    = if (extraPort.isDefined) Some(Output(Bool())) else None
+    val update_idx      = if (extraPort.isDefined) Some(Output(UInt(idxWidth.W))) else None
+    val update_data     = if (extraPort.isDefined) Some(Output(Vec(numWays, gen))) else None
+    val update_way_mask = if (extraPort.isDefined) Some(Output(UInt(numBr.W))) else None
 
-    val conflict_clean = if(extraPort.isDefined) Some(Input(Bool())) else None
+    val conflict_clean = if (extraPort.isDefined) Some(Input(Bool())) else None
   })
 
   class Idx_Tag extends Bundle {
@@ -62,16 +68,15 @@ class WrBypass[T <: Data](gen: T, val numEntries: Int, val idxWidth: Int,
   }
 
   val idx_tag_cam = Module(new IndexableCAMTemplate(new Idx_Tag, numEntries, 1, isIndexable = extraPort.isDefined))
-  val data_mem = Mem(numEntries, Vec(numWays, gen))
+  val data_mem    = Mem(numEntries, Vec(numWays, gen))
 
-  val valids = RegInit(0.U.asTypeOf(Vec(numEntries, Vec(numWays, Bool()))))
+  val valids       = RegInit(0.U.asTypeOf(Vec(numEntries, Vec(numWays, Bool()))))
   val ever_written = RegInit(0.U.asTypeOf(Vec(numEntries, Bool())))
 
-
   idx_tag_cam.io.r.req(0)(io.write_idx, io.write_tag.getOrElse(0.U))
-  val hits_oh = idx_tag_cam.io.r.resp(0).zip(ever_written).map {case (h, ew) => h && ew}
+  val hits_oh = idx_tag_cam.io.r.resp(0).zip(ever_written).map { case (h, ew) => h && ew }
   val hit_idx = OHToUInt(hits_oh)
-  val hit = hits_oh.reduce(_||_)
+  val hit     = hits_oh.reduce(_ || _)
 
   io.hit := hit
   for (i <- 0 until numWays) {
@@ -83,32 +88,32 @@ class WrBypass[T <: Data](gen: T, val numEntries: Int, val idxWidth: Int,
   // Because data_mem can only write to one index
   // Implementing a per-way replacer is meaningless
   // So here use one replacer for all ways
-  val replacer = ReplacementPolicy.fromString("plru", numEntries) // numEntries in total
+  val replacer            = ReplacementPolicy.fromString("plru", numEntries)  // numEntries in total
   val replacer_touch_ways = Wire(Vec(1, Valid(UInt(log2Ceil(numEntries).W)))) // One index at a time
-  val enq_idx = replacer.way
-  val full_mask = Fill(numWays, 1.U(1.W)).asTypeOf(Vec(numWays, Bool()))
-  val update_way_mask = io.write_way_mask.getOrElse(full_mask)
+  val enq_idx             = replacer.way
+  val full_mask           = Fill(numWays, 1.U(1.W)).asTypeOf(Vec(numWays, Bool()))
+  val update_way_mask     = io.write_way_mask.getOrElse(full_mask)
 
   // write data on every request
-  when (io.wen) {
+  when(io.wen) {
     val data_write_idx = Mux(hit, hit_idx, enq_idx)
     data_mem.write(data_write_idx, io.write_data, update_way_mask)
   }
   replacer_touch_ways(0).valid := io.wen
-  replacer_touch_ways(0).bits := Mux(hit, hit_idx, enq_idx)
+  replacer_touch_ways(0).bits  := Mux(hit, hit_idx, enq_idx)
   replacer.access(replacer_touch_ways)
 
   // update valids
   for (i <- 0 until numWays) {
-    when (io.wen) {
-      when (hit) {
-        when (update_way_mask(i)) {
+    when(io.wen) {
+      when(hit) {
+        when(update_way_mask(i)) {
           valids(hit_idx)(i) := true.B
         }
       }.otherwise {
         ever_written(enq_idx) := true.B
-        valids(enq_idx)(i) := false.B
-        when (update_way_mask(i)) {
+        valids(enq_idx)(i)    := false.B
+        when(update_way_mask(i)) {
           valids(enq_idx)(i) := true.B
         }
       }
@@ -116,39 +121,45 @@ class WrBypass[T <: Data](gen: T, val numEntries: Int, val idxWidth: Int,
   }
 
   val enq_en = io.wen && !hit
-  idx_tag_cam.io.w.valid := enq_en
+  idx_tag_cam.io.w.valid      := enq_en
   idx_tag_cam.io.w.bits.index := enq_idx
   idx_tag_cam.io.w.bits.data(io.write_idx, io.write_tag.getOrElse(0.U))
 
-  //Extra ports are used to handle dual port read/write conflicts
+  // Extra ports are used to handle dual port read/write conflicts
   if (extraPort.isDefined) {
-    val conflict_flags = RegInit(0.U.asTypeOf(Vec(numEntries, Bool())))
+    val conflict_flags    = RegInit(0.U.asTypeOf(Vec(numEntries, Bool())))
     val conflict_way_mask = RegInit(0.U.asTypeOf(io.conflict_way_mask.get))
-    val conflict_data = RegInit(VecInit(Seq.tabulate(numWays)( i => 0.U.asTypeOf(gen))))
-    val conflict_idx = OHToUInt(conflict_flags)
+    val conflict_data     = RegInit(VecInit(Seq.tabulate(numWays)(i => 0.U.asTypeOf(gen))))
+    val conflict_idx      = OHToUInt(conflict_flags)
 
     idx_tag_cam.io.ridx.get := conflict_idx
 
-    when (io.wen && io.conflict_valid.getOrElse(false.B)) {
+    when(io.wen && io.conflict_valid.getOrElse(false.B)) {
       conflict_flags(Mux(hit, hit_idx, enq_idx)) := true.B
-      conflict_way_mask := io.conflict_way_mask.get
-      conflict_data := io.conflict_write_data.get
+      conflict_way_mask                          := io.conflict_way_mask.get
+      conflict_data                              := io.conflict_write_data.get
     }
-    when (io.conflict_clean.getOrElse(false.B)) {
+    when(io.conflict_clean.getOrElse(false.B)) {
       conflict_flags(conflict_idx) := false.B
     }
     // for update the cached data
-    io.has_conflict.get := conflict_flags.reduce(_||_)
-    io.update_idx.get := idx_tag_cam.io.rdata.get.idx
+    io.has_conflict.get    := conflict_flags.reduce(_ || _)
+    io.update_idx.get      := idx_tag_cam.io.rdata.get.idx
     io.update_way_mask.get := conflict_way_mask
     io.update_data.foreach(_ := conflict_data)
   } else None
 
-  XSPerfAccumulate("wrbypass_hit",  io.wen &&  hit)
+  XSPerfAccumulate("wrbypass_hit", io.wen && hit)
   XSPerfAccumulate("wrbypass_miss", io.wen && !hit)
 
-  XSDebug(io.wen && hit,  p"wrbypass hit entry #${hit_idx}, idx ${io.write_idx}" +
-    p"tag ${io.write_tag.getOrElse(0.U)}data ${io.write_data}\n")
-  XSDebug(io.wen && !hit, p"wrbypass enq entry #${enq_idx}, idx ${io.write_idx}" +
-    p"tag ${io.write_tag.getOrElse(0.U)}data ${io.write_data}\n")
+  XSDebug(
+    io.wen && hit,
+    p"wrbypass hit entry #${hit_idx}, idx ${io.write_idx}" +
+      p"tag ${io.write_tag.getOrElse(0.U)}data ${io.write_data}\n"
+  )
+  XSDebug(
+    io.wen && !hit,
+    p"wrbypass enq entry #${enq_idx}, idx ${io.write_idx}" +
+      p"tag ${io.write_tag.getOrElse(0.U)}data ${io.write_data}\n"
+  )
 }
diff --git a/src/main/scala/xiangshan/frontend/icache/FIFO.scala b/src/main/scala/xiangshan/frontend/icache/FIFO.scala
index c67a6e7b3be..7c4ec06ce39 100644
--- a/src/main/scala/xiangshan/frontend/icache/FIFO.scala
+++ b/src/main/scala/xiangshan/frontend/icache/FIFO.scala
@@ -15,14 +15,17 @@
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/
 
-package  xiangshan.frontend.icache
+package xiangshan.frontend.icache
 
 import chisel3._
 import chisel3.util._
-import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
+import freechips.rocketchip.diplomacy.IdRange
+import freechips.rocketchip.diplomacy.LazyModule
+import freechips.rocketchip.diplomacy.LazyModuleImp
 import freechips.rocketchip.tilelink._
 import freechips.rocketchip.util.BundleFieldBase
-import huancun.{AliasField, PrefetchField}
+import huancun.AliasField
+import huancun.PrefetchField
 import org.chipsalliance.cde.config.Parameters
 import utility._
 import utils._
@@ -31,19 +34,18 @@ import xiangshan.cache._
 import xiangshan.cache.mmu.TlbRequestIO
 import xiangshan.frontend._
 
-
 class FIFOReg[T <: Data](
-  val gen:            T,
-  val entries:        Int,
-  val pipe:           Boolean = false,
-  val hasFlush:       Boolean = false
+    val gen:      T,
+    val entries:  Int,
+    val pipe:     Boolean = false,
+    val hasFlush: Boolean = false
 ) extends Module() {
   require(entries > 0, "Queue must have non-negative number of entries")
 
   val io = IO(new Bundle {
-    val enq     = Flipped(DecoupledIO(gen))
-    val deq     = DecoupledIO(gen)
-    val flush   = if (hasFlush) Some(Input(Bool())) else None
+    val enq   = Flipped(DecoupledIO(gen))
+    val deq   = DecoupledIO(gen)
+    val flush = if (hasFlush) Some(Input(Bool())) else None
   })
   val flush = io.flush.getOrElse(false.B)
 
@@ -52,7 +54,7 @@ class FIFOReg[T <: Data](
   object FIFOPtr {
     def apply(f: Bool, v: UInt): FIFOPtr = {
       val ptr = Wire(new FIFOPtr)
-      ptr.flag := f
+      ptr.flag  := f
       ptr.value := v
       ptr
     }
@@ -86,6 +88,6 @@ class FIFOReg[T <: Data](
   io.deq.valid := !empty
   io.enq.ready := !full
   if (pipe) {
-    when(io.deq.ready) { io.enq.ready := true.B }
+    when(io.deq.ready)(io.enq.ready := true.B)
   }
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICache.scala b/src/main/scala/xiangshan/frontend/icache/ICache.scala
index f8704fc9d9c..957cb5777fb 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICache.scala
@@ -15,14 +15,17 @@
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/
 
-package  xiangshan.frontend.icache
+package xiangshan.frontend.icache
 
 import chisel3._
 import chisel3.util._
-import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp}
+import freechips.rocketchip.diplomacy.IdRange
+import freechips.rocketchip.diplomacy.LazyModule
+import freechips.rocketchip.diplomacy.LazyModuleImp
 import freechips.rocketchip.tilelink._
 import freechips.rocketchip.util.BundleFieldBase
-import huancun.{AliasField, PrefetchField}
+import huancun.AliasField
+import huancun.PrefetchField
 import org.chipsalliance.cde.config.Parameters
 import utility._
 import utils._
@@ -32,64 +35,64 @@ import xiangshan.cache.mmu.TlbRequestIO
 import xiangshan.frontend._
 
 case class ICacheParameters(
-    nSets: Int = 256,
-    nWays: Int = 4,
-    rowBits: Int = 64,
-    nTLBEntries: Int = 32,
-    tagECC: Option[String] = None,
-    dataECC: Option[String] = None,
-    replacer: Option[String] = Some("random"),
-
-    PortNumber: Int = 2,
-    nFetchMshr: Int = 4,
-    nPrefetchMshr: Int = 10,
-    nWayLookupSize: Int = 32,
-    DataCodeUnit: Int = 64,
-    ICacheDataBanks: Int = 8,
+    nSets:               Int = 256,
+    nWays:               Int = 4,
+    rowBits:             Int = 64,
+    nTLBEntries:         Int = 32,
+    tagECC:              Option[String] = None,
+    dataECC:             Option[String] = None,
+    replacer:            Option[String] = Some("random"),
+    PortNumber:          Int = 2,
+    nFetchMshr:          Int = 4,
+    nPrefetchMshr:       Int = 10,
+    nWayLookupSize:      Int = 32,
+    DataCodeUnit:        Int = 64,
+    ICacheDataBanks:     Int = 8,
     ICacheDataSRAMWidth: Int = 66,
     // TODO: hard code, need delete
     partWayNum: Int = 4,
-
-    nMMIOs: Int = 1,
+    nMMIOs:     Int = 1,
     blockBytes: Int = 64
-)extends L1CacheParameters {
+) extends L1CacheParameters {
 
   val setBytes = nSets * blockBytes
-  val aliasBitsOpt = DCacheParameters().aliasBitsOpt //if(setBytes > pageSize) Some(log2Ceil(setBytes / pageSize)) else None
+  val aliasBitsOpt =
+    DCacheParameters().aliasBitsOpt // if(setBytes > pageSize) Some(log2Ceil(setBytes / pageSize)) else None
   val reqFields: Seq[BundleFieldBase] = Seq(
     PrefetchField(),
     ReqSourceField()
   ) ++ aliasBitsOpt.map(AliasField)
   val echoFields: Seq[BundleFieldBase] = Nil
-  def tagCode: Code = Code.fromString(tagECC)
-  def dataCode: Code = Code.fromString(dataECC)
-  def replacement = ReplacementPolicy.fromString(replacer,nWays,nSets)
+  def tagCode:    Code                 = Code.fromString(tagECC)
+  def dataCode:   Code                 = Code.fromString(dataECC)
+  def replacement = ReplacementPolicy.fromString(replacer, nWays, nSets)
 }
 
-trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst with HasIFUConst{
+trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst with HasIFUConst {
   val cacheParams = icacheParameters
 
-  def ICacheSets            = cacheParams.nSets
-  def ICacheWays            = cacheParams.nWays
-  def PortNumber            = cacheParams.PortNumber
-  def nFetchMshr            = cacheParams.nFetchMshr
-  def nPrefetchMshr         = cacheParams.nPrefetchMshr
-  def nWayLookupSize        = cacheParams.nWayLookupSize
-  def DataCodeUnit          = cacheParams.DataCodeUnit
-  def ICacheDataBanks       = cacheParams.ICacheDataBanks
-  def ICacheDataSRAMWidth   = cacheParams.ICacheDataSRAMWidth
-  def partWayNum            = cacheParams.partWayNum
-
-  def ICacheMetaBits        = tagBits  // FIXME: unportable: maybe use somemethod to get width
-  def ICacheMetaCodeBits    = 1  // FIXME: unportable: maybe use cacheParams.tagCode.somemethod to get width
-  def ICacheMetaEntryBits   = ICacheMetaBits + ICacheMetaCodeBits
-
-  def ICacheDataBits        = blockBits / ICacheDataBanks
-  def ICacheDataCodeSegs    = math.ceil(ICacheDataBits / DataCodeUnit).toInt  // split data to segments for ECC checking
-  def ICacheDataCodeBits    = ICacheDataCodeSegs * 1  // FIXME: unportable: maybe use cacheParams.dataCode.somemethod to get width
-  def ICacheDataEntryBits   = ICacheDataBits + ICacheDataCodeBits
-  def ICacheBankVisitNum    = 32 * 8 / ICacheDataBits + 1
-  def highestIdxBit         = log2Ceil(nSets) - 1
+  def ICacheSets          = cacheParams.nSets
+  def ICacheWays          = cacheParams.nWays
+  def PortNumber          = cacheParams.PortNumber
+  def nFetchMshr          = cacheParams.nFetchMshr
+  def nPrefetchMshr       = cacheParams.nPrefetchMshr
+  def nWayLookupSize      = cacheParams.nWayLookupSize
+  def DataCodeUnit        = cacheParams.DataCodeUnit
+  def ICacheDataBanks     = cacheParams.ICacheDataBanks
+  def ICacheDataSRAMWidth = cacheParams.ICacheDataSRAMWidth
+  def partWayNum          = cacheParams.partWayNum
+
+  def ICacheMetaBits      = tagBits // FIXME: unportable: maybe use somemethod to get width
+  def ICacheMetaCodeBits  = 1       // FIXME: unportable: maybe use cacheParams.tagCode.somemethod to get width
+  def ICacheMetaEntryBits = ICacheMetaBits + ICacheMetaCodeBits
+
+  def ICacheDataBits     = blockBits / ICacheDataBanks
+  def ICacheDataCodeSegs = math.ceil(ICacheDataBits / DataCodeUnit).toInt // split data to segments for ECC checking
+  def ICacheDataCodeBits =
+    ICacheDataCodeSegs * 1 // FIXME: unportable: maybe use cacheParams.dataCode.somemethod to get width
+  def ICacheDataEntryBits = ICacheDataBits + ICacheDataCodeBits
+  def ICacheBankVisitNum  = 32 * 8 / ICacheDataBits + 1
+  def highestIdxBit       = log2Ceil(nSets) - 1
 
   require((ICacheDataBanks >= 2) && isPow2(ICacheDataBanks))
   require(ICacheDataSRAMWidth >= ICacheDataEntryBits)
@@ -99,39 +102,36 @@ trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst wi
   def getBits(num: Int) = log2Ceil(num).W
 
   def generatePipeControl(lastFire: Bool, thisFire: Bool, thisFlush: Bool, lastFlush: Bool): Bool = {
-    val valid  = RegInit(false.B)
-    when(thisFlush)                    {valid  := false.B}
-      .elsewhen(lastFire && !lastFlush)  {valid  := true.B}
-      .elsewhen(thisFire)                 {valid  := false.B}
+    val valid = RegInit(false.B)
+    when(thisFlush)(valid := false.B)
+      .elsewhen(lastFire && !lastFlush)(valid := true.B)
+      .elsewhen(thisFire)(valid := false.B)
     valid
   }
 
-  def ResultHoldBypass[T<:Data](data: T, valid: Bool): T = {
+  def ResultHoldBypass[T <: Data](data: T, valid: Bool): T =
     Mux(valid, data, RegEnable(data, valid))
-  }
 
-  def ResultHoldBypass[T <: Data](data: T, init: T, valid: Bool): T = {
+  def ResultHoldBypass[T <: Data](data: T, init: T, valid: Bool): T =
     Mux(valid, data, RegEnable(data, init, valid))
-  }
 
-  def holdReleaseLatch(valid: Bool, release: Bool, flush: Bool): Bool ={
+  def holdReleaseLatch(valid: Bool, release: Bool, flush: Bool): Bool = {
     val bit = RegInit(false.B)
-    when(flush)                   { bit := false.B  }
-      .elsewhen(valid && !release)  { bit := true.B   }
-      .elsewhen(release)            { bit := false.B  }
+    when(flush)(bit := false.B)
+      .elsewhen(valid && !release)(bit := true.B)
+      .elsewhen(release)(bit := false.B)
     bit || valid
   }
 
   def blockCounter(block: Bool, flush: Bool, threshold: Int): Bool = {
     val counter = RegInit(0.U(log2Up(threshold + 1).W))
-    when (block) { counter := counter + 1.U }
-    when (flush) { counter := 0.U}
+    when(block)(counter := counter + 1.U)
+    when(flush)(counter := 0.U)
     counter > threshold.U
   }
 
-  def InitQueue[T <: Data](entry: T, size: Int): Vec[T] ={
+  def InitQueue[T <: Data](entry: T, size: Int): Vec[T] =
     return RegInit(VecInit(Seq.fill(size)(0.U.asTypeOf(entry.cloneType))))
-  }
 
   def encodeMetaECC(meta: UInt): UInt = {
     require(meta.getWidth == ICacheMetaBits)
@@ -147,33 +147,38 @@ trait HasICacheParameters extends HasL1CacheParameters with HasInstrMMIOConst wi
   }
 
   def getBankSel(blkOffset: UInt, valid: Bool = true.B): Vec[UInt] = {
-    val bankIdxLow  = Cat(0.U(1.W), blkOffset) >> log2Ceil(blockBytes/ICacheDataBanks)
-    val bankIdxHigh = (Cat(0.U(1.W), blkOffset) + 32.U) >> log2Ceil(blockBytes/ICacheDataBanks)
-    val bankSel = VecInit((0 until ICacheDataBanks * 2).map(i => (i.U >= bankIdxLow) && (i.U <= bankIdxHigh)))
-    assert(!valid || PopCount(bankSel) === ICacheBankVisitNum.U, "The number of bank visits must be %d, but bankSel=0x%x", ICacheBankVisitNum.U, bankSel.asUInt)
+    val bankIdxLow  = Cat(0.U(1.W), blkOffset) >> log2Ceil(blockBytes / ICacheDataBanks)
+    val bankIdxHigh = (Cat(0.U(1.W), blkOffset) + 32.U) >> log2Ceil(blockBytes / ICacheDataBanks)
+    val bankSel     = VecInit((0 until ICacheDataBanks * 2).map(i => (i.U >= bankIdxLow) && (i.U <= bankIdxHigh)))
+    assert(
+      !valid || PopCount(bankSel) === ICacheBankVisitNum.U,
+      "The number of bank visits must be %d, but bankSel=0x%x",
+      ICacheBankVisitNum.U,
+      bankSel.asUInt
+    )
     bankSel.asTypeOf(UInt((ICacheDataBanks * 2).W)).asTypeOf(Vec(2, UInt(ICacheDataBanks.W)))
   }
 
   def getLineSel(blkOffset: UInt)(implicit p: Parameters): Vec[Bool] = {
-    val bankIdxLow  = blkOffset >> log2Ceil(blockBytes/ICacheDataBanks)
-    val lineSel = VecInit((0 until ICacheDataBanks).map(i => i.U < bankIdxLow))
+    val bankIdxLow = blkOffset >> log2Ceil(blockBytes / ICacheDataBanks)
+    val lineSel    = VecInit((0 until ICacheDataBanks).map(i => i.U < bankIdxLow))
     lineSel
   }
 
-  def getBlkAddr(addr: UInt) = addr >> blockOffBits
-  def getPhyTagFromBlk(addr: UInt): UInt = addr >> (pgUntagBits - blockOffBits)
-  def getIdxFromBlk(addr: UInt) = addr(idxBits - 1, 0)
+  def getBlkAddr(addr:           UInt) = addr >> blockOffBits
+  def getPhyTagFromBlk(addr:     UInt): UInt = addr >> (pgUntagBits - blockOffBits)
+  def getIdxFromBlk(addr:        UInt) = addr(idxBits - 1, 0)
   def get_paddr_from_ptag(vaddr: UInt, ptag: UInt) = Cat(ptag, vaddr(pgUntagBits - 1, 0))
 }
 
 abstract class ICacheBundle(implicit p: Parameters) extends XSBundle
-  with HasICacheParameters
+    with HasICacheParameters
 
 abstract class ICacheModule(implicit p: Parameters) extends XSModule
-  with HasICacheParameters
+    with HasICacheParameters
 
 abstract class ICacheArray(implicit p: Parameters) extends XSModule
-  with HasICacheParameters
+    with HasICacheParameters
 
 class ICacheMetadata(implicit p: Parameters) extends ICacheBundle {
   val tag = UInt(tagBits.W)
@@ -187,9 +192,7 @@ object ICacheMetadata {
   }
 }
 
-
-class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray
-{
+class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray {
   class ICacheMetaEntry(implicit p: Parameters) extends ICacheBundle {
     val meta: ICacheMetadata = new ICacheMetadata
     val code: UInt           = UInt(ICacheMetaCodeBits.W)
@@ -214,10 +217,10 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray
     val fencei   = Input(Bool())
   })
 
-  val port_0_read_0 = io.read.valid  && !io.read.bits.vSetIdx(0)(0)
-  val port_0_read_1 = io.read.valid  &&  io.read.bits.vSetIdx(0)(0)
-  val port_1_read_1  = io.read.valid &&  io.read.bits.vSetIdx(1)(0) && io.read.bits.isDoubleLine
-  val port_1_read_0  = io.read.valid && !io.read.bits.vSetIdx(1)(0) && io.read.bits.isDoubleLine
+  val port_0_read_0 = io.read.valid && !io.read.bits.vSetIdx(0)(0)
+  val port_0_read_1 = io.read.valid && io.read.bits.vSetIdx(0)(0)
+  val port_1_read_1 = io.read.valid && io.read.bits.vSetIdx(1)(0) && io.read.bits.isDoubleLine
+  val port_1_read_0 = io.read.valid && !io.read.bits.vSetIdx(1)(0) && io.read.bits.isDoubleLine
 
   val port_0_read_0_reg = RegEnable(port_0_read_0, 0.U.asTypeOf(port_0_read_0), io.read.fire)
   val port_0_read_1_reg = RegEnable(port_0_read_1, 0.U.asTypeOf(port_0_read_1), io.read.fire)
@@ -229,54 +232,64 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray
   val bank_idx   = Seq(bank_0_idx, bank_1_idx)
 
   val write_bank_0 = io.write.valid && !io.write.bits.bankIdx
-  val write_bank_1 = io.write.valid &&  io.write.bits.bankIdx
+  val write_bank_1 = io.write.valid && io.write.bits.bankIdx
 
-  val write_meta_bits = ICacheMetaEntry(meta = ICacheMetadata(
-    tag = io.write.bits.phyTag
-  ))
+  val write_meta_bits = ICacheMetaEntry(meta =
+    ICacheMetadata(
+      tag = io.write.bits.phyTag
+    )
+  )
 
   val tagArrays = (0 until 2) map { bank =>
     val tagArray = Module(new SRAMTemplate(
       new ICacheMetaEntry(),
-      set=nSets/2,
-      way=nWays,
+      set = nSets / 2,
+      way = nWays,
       shouldReset = true,
       holdRead = true,
       singlePort = true
     ))
 
-    //meta connection
-    if(bank == 0) {
+    // meta connection
+    if (bank == 0) {
       tagArray.io.r.req.valid := port_0_read_0 || port_1_read_0
-      tagArray.io.r.req.bits.apply(setIdx=bank_0_idx(highestIdxBit,1))
+      tagArray.io.r.req.bits.apply(setIdx = bank_0_idx(highestIdxBit, 1))
       tagArray.io.w.req.valid := write_bank_0
-      tagArray.io.w.req.bits.apply(data=write_meta_bits, setIdx=io.write.bits.virIdx(highestIdxBit,1), waymask=io.write.bits.waymask)
-    }
-    else {
+      tagArray.io.w.req.bits.apply(
+        data = write_meta_bits,
+        setIdx = io.write.bits.virIdx(highestIdxBit, 1),
+        waymask = io.write.bits.waymask
+      )
+    } else {
       tagArray.io.r.req.valid := port_0_read_1 || port_1_read_1
-      tagArray.io.r.req.bits.apply(setIdx=bank_1_idx(highestIdxBit,1))
+      tagArray.io.r.req.bits.apply(setIdx = bank_1_idx(highestIdxBit, 1))
       tagArray.io.w.req.valid := write_bank_1
-      tagArray.io.w.req.bits.apply(data=write_meta_bits, setIdx=io.write.bits.virIdx(highestIdxBit,1), waymask=io.write.bits.waymask)
+      tagArray.io.w.req.bits.apply(
+        data = write_meta_bits,
+        setIdx = io.write.bits.virIdx(highestIdxBit, 1),
+        waymask = io.write.bits.waymask
+      )
     }
 
     tagArray
   }
 
   val read_set_idx_next = RegEnable(io.read.bits.vSetIdx, 0.U.asTypeOf(io.read.bits.vSetIdx), io.read.fire)
-  val valid_array = RegInit(VecInit(Seq.fill(nWays)(0.U(nSets.W))))
-  val valid_metas = Wire(Vec(PortNumber, Vec(nWays, Bool())))
+  val valid_array       = RegInit(VecInit(Seq.fill(nWays)(0.U(nSets.W))))
+  val valid_metas       = Wire(Vec(PortNumber, Vec(nWays, Bool())))
   // valid read
-  (0 until PortNumber).foreach( i =>
-    (0 until nWays).foreach( way =>
+  (0 until PortNumber).foreach(i =>
+    (0 until nWays).foreach(way =>
       valid_metas(i)(way) := valid_array(way)(read_set_idx_next(i))
-    ))
+    )
+  )
   io.readResp.entryValid := valid_metas
 
-  io.read.ready := !io.write.valid && !io.fencei && tagArrays.map(_.io.r.req.ready).reduce(_&&_)
+  io.read.ready := !io.write.valid && !io.fencei && tagArrays.map(_.io.r.req.ready).reduce(_ && _)
 
   // valid write
   val way_num = OHToUInt(io.write.bits.waymask)
-  when (io.write.valid) {
+  when(io.write.valid) {
     valid_array(way_num) := valid_array(way_num).bitSet(io.write.bits.virIdx, true.B)
   }
 
@@ -284,29 +297,27 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray
 
   io.readResp.metas <> DontCare
   io.readResp.codes <> DontCare
-  val readMetaEntries = tagArrays.map{ port =>
-    port.io.r.resp.asTypeOf(Vec(nWays, new ICacheMetaEntry()))
-  }
-  val readMetas = readMetaEntries.map(_.map(_.meta))
-  val readCodes = readMetaEntries.map(_.map(_.code))
+  val readMetaEntries = tagArrays.map(port => port.io.r.resp.asTypeOf(Vec(nWays, new ICacheMetaEntry())))
+  val readMetas       = readMetaEntries.map(_.map(_.meta))
+  val readCodes       = readMetaEntries.map(_.map(_.code))
 
   // TEST: force ECC to fail by setting readCodes to 0
   if (ICacheForceMetaECCError) {
     readCodes.foreach(_.foreach(_ := 0.U))
   }
 
-  when(port_0_read_0_reg){
+  when(port_0_read_0_reg) {
     io.readResp.metas(0) := readMetas(0)
     io.readResp.codes(0) := readCodes(0)
-  }.elsewhen(port_0_read_1_reg){
+  }.elsewhen(port_0_read_1_reg) {
     io.readResp.metas(0) := readMetas(1)
     io.readResp.codes(0) := readCodes(1)
   }
 
-  when(port_1_read_0_reg){
+  when(port_1_read_0_reg) {
     io.readResp.metas(1) := readMetas(0)
     io.readResp.codes(1) := readCodes(0)
-  }.elsewhen(port_1_read_1_reg){
+  }.elsewhen(port_1_read_1_reg) {
     io.readResp.metas(1) := readMetas(1)
     io.readResp.codes(1) := readCodes(1)
   }
@@ -314,15 +325,14 @@ class ICacheMetaArray()(implicit p: Parameters) extends ICacheArray
   io.write.ready := true.B // TODO : has bug ? should be !io.cacheOp.req.valid
 
   // fencei logic : reset valid_array
-  when (io.fencei) {
-    (0 until nWays).foreach( way =>
+  when(io.fencei) {
+    (0 until nWays).foreach(way =>
       valid_array(way) := 0.U
     )
   }
 }
 
-class ICacheDataArray(implicit p: Parameters) extends ICacheArray
-{
+class ICacheDataArray(implicit p: Parameters) extends ICacheArray {
   class ICacheDataEntry(implicit p: Parameters) extends ICacheBundle {
     val data = UInt(ICacheDataBits.W)
     val code = UInt(ICacheDataCodeBits.W)
@@ -337,12 +347,14 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray
     }
   }
 
-  val io=IO{new Bundle{
-    val write    = Flipped(DecoupledIO(new ICacheDataWriteBundle))
-    // TODO: fix hard code
-    val read     = Flipped(Vec(4, DecoupledIO(new ICacheReadBundle)))
-    val readResp = Output(new ICacheDataRespBundle)
-  }}
+  val io = IO {
+    new Bundle {
+      val write = Flipped(DecoupledIO(new ICacheDataWriteBundle))
+      // TODO: fix hard code
+      val read     = Flipped(Vec(4, DecoupledIO(new ICacheReadBundle)))
+      val readResp = Output(new ICacheDataRespBundle)
+    }
+  }
 
   /**
     ******************************************************************************
@@ -352,23 +364,26 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray
   val writeDatas   = io.write.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt(ICacheDataBits.W)))
   val writeEntries = writeDatas.map(ICacheDataEntry(_).asUInt)
 
-  val bankSel = getBankSel(io.read(0).bits.blkOffset, io.read(0).valid)
-  val lineSel = getLineSel(io.read(0).bits.blkOffset)
+  val bankSel  = getBankSel(io.read(0).bits.blkOffset, io.read(0).valid)
+  val lineSel  = getLineSel(io.read(0).bits.blkOffset)
   val waymasks = io.read(0).bits.wayMask
-  val masks = Wire(Vec(nWays, Vec(ICacheDataBanks, Bool())))
-  (0 until nWays).foreach{way =>
-    (0 until ICacheDataBanks).foreach{bank =>
-      masks(way)(bank) := Mux(lineSel(bank), waymasks(1)(way) && bankSel(1)(bank).asBool,
-                                             waymasks(0)(way) && bankSel(0)(bank).asBool)
+  val masks    = Wire(Vec(nWays, Vec(ICacheDataBanks, Bool())))
+  (0 until nWays).foreach { way =>
+    (0 until ICacheDataBanks).foreach { bank =>
+      masks(way)(bank) := Mux(
+        lineSel(bank),
+        waymasks(1)(way) && bankSel(1)(bank).asBool,
+        waymasks(0)(way) && bankSel(0)(bank).asBool
+      )
     }
   }
 
-  val dataArrays = (0 until nWays).map{ way =>
+  val dataArrays = (0 until nWays).map { way =>
     (0 until ICacheDataBanks).map { bank =>
       val sramBank = Module(new SRAMTemplateWithFixedWidth(
         UInt(ICacheDataEntryBits.W),
-        set=nSets,
-        width=ICacheDataSRAMWidth,
+        set = nSets,
+        width = ICacheDataSRAMWidth,
         shouldReset = true,
         holdRead = true,
         singlePort = true
@@ -376,14 +391,14 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray
 
       // read
       sramBank.io.r.req.valid := io.read(bank % 4).valid && masks(way)(bank)
-      sramBank.io.r.req.bits.apply(setIdx=Mux(lineSel(bank),
-                                              io.read(bank % 4).bits.vSetIdx(1),
-                                              io.read(bank % 4).bits.vSetIdx(0)))
+      sramBank.io.r.req.bits.apply(setIdx =
+        Mux(lineSel(bank), io.read(bank % 4).bits.vSetIdx(1), io.read(bank % 4).bits.vSetIdx(0))
+      )
       // write
       sramBank.io.w.req.valid := io.write.valid && io.write.bits.waymask(way).asBool
       sramBank.io.w.req.bits.apply(
-        data    = writeEntries(bank),
-        setIdx  = io.write.bits.virIdx,
+        data = writeEntries(bank),
+        setIdx = io.write.bits.virIdx,
         // waymask is invalid when way of SRAMTemplate <= 1
         waymask = 0.U
       )
@@ -396,13 +411,13 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray
     * read logic
     ******************************************************************************
     */
-  val masksReg          = RegEnable(masks, 0.U.asTypeOf(masks), io.read(0).valid)
-  val readDataWithCode  = (0 until ICacheDataBanks).map(bank =>
-                            Mux1H(VecInit(masksReg.map(_(bank))).asTypeOf(UInt(nWays.W)),
-                                  dataArrays.map(_(bank).io.r.resp.asUInt)))
-  val readEntries       = readDataWithCode.map(_.asTypeOf(new ICacheDataEntry()))
-  val readDatas         = VecInit(readEntries.map(_.data))
-  val readCodes         = VecInit(readEntries.map(_.code))
+  val masksReg = RegEnable(masks, 0.U.asTypeOf(masks), io.read(0).valid)
+  val readDataWithCode = (0 until ICacheDataBanks).map(bank =>
+    Mux1H(VecInit(masksReg.map(_(bank))).asTypeOf(UInt(nWays.W)), dataArrays.map(_(bank).io.r.resp.asUInt))
+  )
+  val readEntries = readDataWithCode.map(_.asTypeOf(new ICacheDataEntry()))
+  val readDatas   = VecInit(readEntries.map(_.data))
+  val readCodes   = VecInit(readEntries.map(_.code))
 
   // TEST: force ECC to fail by setting readCodes to 0
   if (ICacheForceDataECCError) {
@@ -414,64 +429,69 @@ class ICacheDataArray(implicit p: Parameters) extends ICacheArray
     * IO
     ******************************************************************************
     */
-  io.readResp.datas   := readDatas
-  io.readResp.codes   := readCodes
-  io.write.ready      := true.B
-  io.read.foreach( _.ready := !io.write.valid)
+  io.readResp.datas := readDatas
+  io.readResp.codes := readCodes
+  io.write.ready    := true.B
+  io.read.foreach(_.ready := !io.write.valid)
 }
 
-
 class ICacheReplacer(implicit p: Parameters) extends ICacheModule {
   val io = IO(new Bundle {
-    val touch   = Vec(PortNumber, Flipped(ValidIO(new ReplacerTouch)))
-    val victim  = Flipped(new ReplacerVictim)
+    val touch  = Vec(PortNumber, Flipped(ValidIO(new ReplacerTouch)))
+    val victim = Flipped(new ReplacerVictim)
   })
 
-  val replacers = Seq.fill(PortNumber)(ReplacementPolicy.fromString(cacheParams.replacer,nWays,nSets/PortNumber))
+  val replacers = Seq.fill(PortNumber)(ReplacementPolicy.fromString(cacheParams.replacer, nWays, nSets / PortNumber))
 
   // touch
-  val touch_sets = Seq.fill(PortNumber)(Wire(Vec(2, UInt(log2Ceil(nSets/2).W))))
+  val touch_sets = Seq.fill(PortNumber)(Wire(Vec(2, UInt(log2Ceil(nSets / 2).W))))
   val touch_ways = Seq.fill(PortNumber)(Wire(Vec(2, Valid(UInt(log2Ceil(nWays).W)))))
-  (0 until PortNumber).foreach {i =>
-    touch_sets(i)(0)        := Mux(io.touch(i).bits.vSetIdx(0), io.touch(1).bits.vSetIdx(highestIdxBit, 1), io.touch(0).bits.vSetIdx(highestIdxBit, 1))
-    touch_ways(i)(0).bits   := Mux(io.touch(i).bits.vSetIdx(0), io.touch(1).bits.way, io.touch(0).bits.way)
-    touch_ways(i)(0).valid  := Mux(io.touch(i).bits.vSetIdx(0), io.touch(1).valid, io.touch(0).valid)
+  (0 until PortNumber).foreach { i =>
+    touch_sets(i)(0) := Mux(
+      io.touch(i).bits.vSetIdx(0),
+      io.touch(1).bits.vSetIdx(highestIdxBit, 1),
+      io.touch(0).bits.vSetIdx(highestIdxBit, 1)
+    )
+    touch_ways(i)(0).bits  := Mux(io.touch(i).bits.vSetIdx(0), io.touch(1).bits.way, io.touch(0).bits.way)
+    touch_ways(i)(0).valid := Mux(io.touch(i).bits.vSetIdx(0), io.touch(1).valid, io.touch(0).valid)
   }
 
   // victim
-  io.victim.way := Mux(io.victim.vSetIdx.bits(0),
-                       replacers(1).way(io.victim.vSetIdx.bits(highestIdxBit, 1)),
-                       replacers(0).way(io.victim.vSetIdx.bits(highestIdxBit, 1)))
+  io.victim.way := Mux(
+    io.victim.vSetIdx.bits(0),
+    replacers(1).way(io.victim.vSetIdx.bits(highestIdxBit, 1)),
+    replacers(0).way(io.victim.vSetIdx.bits(highestIdxBit, 1))
+  )
 
   // touch the victim in next cycle
-  val victim_vSetIdx_reg = RegEnable(io.victim.vSetIdx.bits, 0.U.asTypeOf(io.victim.vSetIdx.bits), io.victim.vSetIdx.valid)
-  val victim_way_reg     = RegEnable(io.victim.way,          0.U.asTypeOf(io.victim.way),          io.victim.vSetIdx.valid)
-  (0 until PortNumber).foreach {i =>
-    touch_sets(i)(1)        := victim_vSetIdx_reg(highestIdxBit, 1)
-    touch_ways(i)(1).bits   := victim_way_reg
-    touch_ways(i)(1).valid  := RegNext(io.victim.vSetIdx.valid) && (victim_vSetIdx_reg(0) === i.U)
+  val victim_vSetIdx_reg =
+    RegEnable(io.victim.vSetIdx.bits, 0.U.asTypeOf(io.victim.vSetIdx.bits), io.victim.vSetIdx.valid)
+  val victim_way_reg = RegEnable(io.victim.way, 0.U.asTypeOf(io.victim.way), io.victim.vSetIdx.valid)
+  (0 until PortNumber).foreach { i =>
+    touch_sets(i)(1)       := victim_vSetIdx_reg(highestIdxBit, 1)
+    touch_ways(i)(1).bits  := victim_way_reg
+    touch_ways(i)(1).valid := RegNext(io.victim.vSetIdx.valid) && (victim_vSetIdx_reg(0) === i.U)
   }
 
-  ((replacers zip touch_sets) zip touch_ways).map{case ((r, s),w) => r.access(s,w)}
+  ((replacers zip touch_sets) zip touch_ways).map { case ((r, s), w) => r.access(s, w) }
 }
 
-class ICacheIO(implicit p: Parameters) extends ICacheBundle
-{
-  val hartId      = Input(UInt(hartIdLen.W))
+class ICacheIO(implicit p: Parameters) extends ICacheBundle {
+  val hartId       = Input(UInt(hartIdLen.W))
   val ftqPrefetch  = Flipped(new FtqToPrefetchIO)
   val softPrefetch = Vec(backendParams.LduCnt, Flipped(Valid(new SoftIfetchPrefetchBundle)))
-  val stop        = Input(Bool())
-  val fetch       = new ICacheMainPipeBundle
-  val toIFU       = Output(Bool())
-  val pmp         = Vec(2 * PortNumber, new ICachePMPBundle)
-  val itlb        = Vec(PortNumber, new TlbRequestIO)
-  val perfInfo    = Output(new ICachePerfInfo)
-  val error       = ValidIO(new L1CacheErrorInfo)
+  val stop         = Input(Bool())
+  val fetch        = new ICacheMainPipeBundle
+  val toIFU        = Output(Bool())
+  val pmp          = Vec(2 * PortNumber, new ICachePMPBundle)
+  val itlb         = Vec(PortNumber, new TlbRequestIO)
+  val perfInfo     = Output(new ICachePerfInfo)
+  val error        = ValidIO(new L1CacheErrorInfo)
   /* CSR control signal */
-  val csr_pf_enable = Input(Bool())
+  val csr_pf_enable     = Input(Bool())
   val csr_parity_enable = Input(Bool())
-  val fencei      = Input(Bool())
-  val flush       = Input(Bool())
+  val fencei            = Input(Bool())
+  val flush             = Input(Bool())
 }
 
 class ICache()(implicit p: Parameters) extends LazyModule with HasICacheParameters {
@@ -480,7 +500,7 @@ class ICache()(implicit p: Parameters) extends LazyModule with HasICacheParamete
   val clientParameters = TLMasterPortParameters.v1(
     Seq(TLMasterParameters.v1(
       name = "icache",
-      sourceId = IdRange(0, cacheParams.nFetchMshr + cacheParams.nPrefetchMshr + 1),
+      sourceId = IdRange(0, cacheParams.nFetchMshr + cacheParams.nPrefetchMshr + 1)
     )),
     requestFields = cacheParams.reqFields,
     echoFields = cacheParams.echoFields
@@ -495,35 +515,35 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   val io = IO(new ICacheIO)
 
   println("ICache:")
-  println("  TagECC: "                + cacheParams.tagECC)
-  println("  DataECC: "               + cacheParams.dataECC)
-  println("  ICacheSets: "            + cacheParams.nSets)
-  println("  ICacheWays: "            + cacheParams.nWays)
-  println("  PortNumber: "            + cacheParams.PortNumber)
-  println("  nFetchMshr: "            + cacheParams.nFetchMshr)
-  println("  nPrefetchMshr: "         + cacheParams.nPrefetchMshr)
-  println("  nWayLookupSize: "        + cacheParams.nWayLookupSize)
-  println("  DataCodeUnit: "          + cacheParams.DataCodeUnit)
-  println("  ICacheDataBanks: "       + cacheParams.ICacheDataBanks)
-  println("  ICacheDataSRAMWidth: "   + cacheParams.ICacheDataSRAMWidth)
+  println("  TagECC: " + cacheParams.tagECC)
+  println("  DataECC: " + cacheParams.dataECC)
+  println("  ICacheSets: " + cacheParams.nSets)
+  println("  ICacheWays: " + cacheParams.nWays)
+  println("  PortNumber: " + cacheParams.PortNumber)
+  println("  nFetchMshr: " + cacheParams.nFetchMshr)
+  println("  nPrefetchMshr: " + cacheParams.nPrefetchMshr)
+  println("  nWayLookupSize: " + cacheParams.nWayLookupSize)
+  println("  DataCodeUnit: " + cacheParams.DataCodeUnit)
+  println("  ICacheDataBanks: " + cacheParams.ICacheDataBanks)
+  println("  ICacheDataSRAMWidth: " + cacheParams.ICacheDataSRAMWidth)
 
   val (bus, edge) = outer.clientNode.out.head
 
-  val metaArray         = Module(new ICacheMetaArray)
-  val dataArray         = Module(new ICacheDataArray)
-  val mainPipe          = Module(new ICacheMainPipe)
-  val missUnit          = Module(new ICacheMissUnit(edge))
-  val replacer          = Module(new ICacheReplacer)
-  val prefetcher        = Module(new IPrefetchPipe)
-  val wayLookup         = Module(new WayLookup)
+  val metaArray  = Module(new ICacheMetaArray)
+  val dataArray  = Module(new ICacheDataArray)
+  val mainPipe   = Module(new ICacheMainPipe)
+  val missUnit   = Module(new ICacheMissUnit(edge))
+  val replacer   = Module(new ICacheReplacer)
+  val prefetcher = Module(new IPrefetchPipe)
+  val wayLookup  = Module(new WayLookup)
 
-  dataArray.io.write    <> missUnit.io.data_write
-  dataArray.io.read     <> mainPipe.io.dataArray.toIData
+  dataArray.io.write <> missUnit.io.data_write
+  dataArray.io.read <> mainPipe.io.dataArray.toIData
   dataArray.io.readResp <> mainPipe.io.dataArray.fromIData
 
-  metaArray.io.fencei   := io.fencei
-  metaArray.io.write    <> missUnit.io.meta_write
-  metaArray.io.read     <> prefetcher.io.metaRead.toIMeta
+  metaArray.io.fencei := io.fencei
+  metaArray.io.write <> missUnit.io.meta_write
+  metaArray.io.read <> prefetcher.io.metaRead.toIMeta
   metaArray.io.readResp <> prefetcher.io.metaRead.fromIMeta
 
   prefetcher.io.flush             := io.flush
@@ -533,7 +553,7 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   prefetcher.io.flushFromBpu      := io.ftqPrefetch.flushFromBpu
   // cache softPrefetch
   private val softPrefetchValid = RegInit(false.B)
-  private val softPrefetch = RegInit(0.U.asTypeOf(new IPrefetchReq))
+  private val softPrefetch      = RegInit(0.U.asTypeOf(new IPrefetchReq))
   /* FIXME:
    * If there is already a pending softPrefetch request, it will be overwritten.
    * Also, if there are multiple softPrefetch requests in the same cycle, only the first one will be accepted.
@@ -541,46 +561,46 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
    * However, the impact on performance still needs to be assessed.
    * Considering that the frequency of prefetch.i may not be high, let's start with a temporary dummy solution.
    */
-  when (io.softPrefetch.map(_.valid).reduce(_||_)) {
+  when(io.softPrefetch.map(_.valid).reduce(_ || _)) {
     softPrefetchValid := true.B
     softPrefetch.fromSoftPrefetch(MuxCase(
       0.U.asTypeOf(new SoftIfetchPrefetchBundle),
-      io.softPrefetch.map(req => (req.valid -> req.bits))
+      io.softPrefetch.map(req => req.valid -> req.bits)
     ))
-  }.elsewhen (prefetcher.io.req.fire) {
+  }.elsewhen(prefetcher.io.req.fire) {
     softPrefetchValid := false.B
   }
   // pass ftqPrefetch
   private val ftqPrefetch = WireInit(0.U.asTypeOf(new IPrefetchReq))
   ftqPrefetch.fromFtqICacheInfo(io.ftqPrefetch.req.bits)
   // software prefetch has higher priority
-  prefetcher.io.req.valid := softPrefetchValid || io.ftqPrefetch.req.valid
-  prefetcher.io.req.bits  := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
+  prefetcher.io.req.valid  := softPrefetchValid || io.ftqPrefetch.req.valid
+  prefetcher.io.req.bits   := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
   io.ftqPrefetch.req.ready := prefetcher.io.req.ready && !softPrefetchValid
 
-  missUnit.io.hartId            := io.hartId
-  missUnit.io.fencei            := io.fencei
-  missUnit.io.flush             := io.flush
-  missUnit.io.fetch_req         <> mainPipe.io.mshr.req
-  missUnit.io.prefetch_req      <> prefetcher.io.MSHRReq
-  missUnit.io.mem_grant.valid   := false.B
-  missUnit.io.mem_grant.bits    := DontCare
-  missUnit.io.mem_grant         <> bus.d
+  missUnit.io.hartId := io.hartId
+  missUnit.io.fencei := io.fencei
+  missUnit.io.flush  := io.flush
+  missUnit.io.fetch_req <> mainPipe.io.mshr.req
+  missUnit.io.prefetch_req <> prefetcher.io.MSHRReq
+  missUnit.io.mem_grant.valid := false.B
+  missUnit.io.mem_grant.bits  := DontCare
+  missUnit.io.mem_grant <> bus.d
 
   mainPipe.io.flush             := io.flush
   mainPipe.io.respStall         := io.stop
   mainPipe.io.csr_parity_enable := io.csr_parity_enable
   mainPipe.io.hartId            := io.hartId
   mainPipe.io.mshr.resp         := missUnit.io.fetch_resp
-  mainPipe.io.fetch.req         <> io.fetch.req
-  mainPipe.io.wayLookupRead     <> wayLookup.io.read
+  mainPipe.io.fetch.req <> io.fetch.req
+  mainPipe.io.wayLookupRead <> wayLookup.io.read
 
-  wayLookup.io.flush            := io.flush
-  wayLookup.io.write            <> prefetcher.io.wayLookupWrite
-  wayLookup.io.update           := missUnit.io.fetch_resp
+  wayLookup.io.flush := io.flush
+  wayLookup.io.write <> prefetcher.io.wayLookupWrite
+  wayLookup.io.update := missUnit.io.fetch_resp
 
-  replacer.io.touch   <> mainPipe.io.touch
-  replacer.io.victim  <> missUnit.io.victim
+  replacer.io.touch <> mainPipe.io.touch
+  replacer.io.victim <> missUnit.io.victim
 
   io.pmp(0) <> mainPipe.io.pmp(0)
   io.pmp(1) <> mainPipe.io.pmp(1)
@@ -590,11 +610,11 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
   io.itlb(0) <> prefetcher.io.itlb(0)
   io.itlb(1) <> prefetcher.io.itlb(1)
 
-  //notify IFU that Icache pipeline is available
-  io.toIFU := mainPipe.io.fetch.req.ready
+  // notify IFU that Icache pipeline is available
+  io.toIFU    := mainPipe.io.fetch.req.ready
   io.perfInfo := mainPipe.io.perfInfo
 
-  io.fetch.resp              <> mainPipe.io.fetch.resp
+  io.fetch.resp <> mainPipe.io.fetch.resp
   io.fetch.topdownIcacheMiss := mainPipe.io.fetch.topdownIcacheMiss
   io.fetch.topdownItlbMiss   := mainPipe.io.fetch.topdownItlbMiss
 
@@ -606,91 +626,104 @@ class ICacheImp(outer: ICache) extends LazyModuleImp(outer) with HasICacheParame
 
   bus.a <> missUnit.io.mem_acquire
 
-  //Parity error port
-  val errors = mainPipe.io.errors
+  // Parity error port
+  val errors       = mainPipe.io.errors
   val errors_valid = errors.map(e => e.valid).reduce(_ | _)
   io.error.bits <> RegEnable(Mux1H(errors.map(e => e.valid -> e.bits)), 0.U.asTypeOf(errors(0).bits), errors_valid)
   io.error.valid := RegNext(errors_valid, false.B)
 
-  XSPerfAccumulate("softPrefetch_drop_not_ready", io.softPrefetch.map(_.valid).reduce(_||_) && softPrefetchValid && !prefetcher.io.req.fire)
+  XSPerfAccumulate(
+    "softPrefetch_drop_not_ready",
+    io.softPrefetch.map(_.valid).reduce(_ || _) && softPrefetchValid && !prefetcher.io.req.fire
+  )
   XSPerfAccumulate("softPrefetch_drop_multi_req", PopCount(io.softPrefetch.map(_.valid)) > 1.U)
   XSPerfAccumulate("softPrefetch_block_ftq", softPrefetchValid && io.ftqPrefetch.req.valid)
 
   val perfEvents = Seq(
     ("icache_miss_cnt  ", false.B),
-    ("icache_miss_penalty", BoolStopWatch(start = false.B, stop = false.B || false.B, startHighPriority = true)),
+    ("icache_miss_penalty", BoolStopWatch(start = false.B, stop = false.B || false.B, startHighPriority = true))
   )
   generatePerfEvent()
 }
 
 class ICachePartWayReadBundle[T <: Data](gen: T, pWay: Int)(implicit p: Parameters)
-  extends ICacheBundle
-{
-  val req = Flipped(Vec(PortNumber, Decoupled(new Bundle{
-    val ridx = UInt((log2Ceil(nSets) - 1).W)
-  })))
-  val resp = Output(new Bundle{
-    val rdata  = Vec(PortNumber,Vec(pWay, gen))
+    extends ICacheBundle {
+  val req = Flipped(Vec(
+    PortNumber,
+    Decoupled(new Bundle {
+      val ridx = UInt((log2Ceil(nSets) - 1).W)
+    })
+  ))
+  val resp = Output(new Bundle {
+    val rdata = Vec(PortNumber, Vec(pWay, gen))
   })
 }
 
 class ICacheWriteBundle[T <: Data](gen: T, pWay: Int)(implicit p: Parameters)
-  extends ICacheBundle
-{
-  val wdata = gen
-  val widx = UInt((log2Ceil(nSets) - 1).W)
+    extends ICacheBundle {
+  val wdata    = gen
+  val widx     = UInt((log2Ceil(nSets) - 1).W)
   val wbankidx = Bool()
-  val wmask = Vec(pWay, Bool())
+  val wmask    = Vec(pWay, Bool())
 }
 
-class ICachePartWayArray[T <: Data](gen: T, pWay: Int)(implicit p: Parameters) extends ICacheArray
-{
+class ICachePartWayArray[T <: Data](gen: T, pWay: Int)(implicit p: Parameters) extends ICacheArray {
 
-  //including part way data
-  val io = IO{new Bundle {
-    val read      = new  ICachePartWayReadBundle(gen,pWay)
-    val write     = Flipped(ValidIO(new ICacheWriteBundle(gen, pWay)))
-  }}
+  // including part way data
+  val io = IO {
+    new Bundle {
+      val read  = new ICachePartWayReadBundle(gen, pWay)
+      val write = Flipped(ValidIO(new ICacheWriteBundle(gen, pWay)))
+    }
+  }
 
   io.read.req.map(_.ready := !io.write.valid)
 
   val srams = (0 until PortNumber) map { bank =>
     val sramBank = Module(new SRAMTemplate(
       gen,
-      set=nSets/2,
-      way=pWay,
+      set = nSets / 2,
+      way = pWay,
       shouldReset = true,
       holdRead = true,
       singlePort = true
     ))
 
     sramBank.io.r.req.valid := io.read.req(bank).valid
-    sramBank.io.r.req.bits.apply(setIdx= io.read.req(bank).bits.ridx)
-
-    if(bank == 0) sramBank.io.w.req.valid := io.write.valid && !io.write.bits.wbankidx
-    else sramBank.io.w.req.valid := io.write.valid && io.write.bits.wbankidx
-    sramBank.io.w.req.bits.apply(data=io.write.bits.wdata, setIdx=io.write.bits.widx, waymask=io.write.bits.wmask.asUInt)
+    sramBank.io.r.req.bits.apply(setIdx = io.read.req(bank).bits.ridx)
+
+    if (bank == 0) sramBank.io.w.req.valid := io.write.valid && !io.write.bits.wbankidx
+    else sramBank.io.w.req.valid           := io.write.valid && io.write.bits.wbankidx
+    sramBank.io.w.req.bits.apply(
+      data = io.write.bits.wdata,
+      setIdx = io.write.bits.widx,
+      waymask = io.write.bits.wmask.asUInt
+    )
 
     sramBank
   }
 
-  io.read.req.map(_.ready := !io.write.valid && srams.map(_.io.r.req.ready).reduce(_&&_))
+  io.read.req.map(_.ready := !io.write.valid && srams.map(_.io.r.req.ready).reduce(_ && _))
 
-  io.read.resp.rdata := VecInit(srams.map(bank => bank.io.r.resp.asTypeOf(Vec(pWay,gen))))
+  io.read.resp.rdata := VecInit(srams.map(bank => bank.io.r.resp.asTypeOf(Vec(pWay, gen))))
 
 }
 
 // Automatically partition the SRAM based on the width of the data and the desired width.
 // final SRAM width = width * way
-class SRAMTemplateWithFixedWidth[T <: Data]
-(
-  gen: T, set: Int, width: Int, way: Int = 1,
-  shouldReset: Boolean = false, holdRead: Boolean = false,
-  singlePort: Boolean = false, bypassWrite: Boolean = false
+class SRAMTemplateWithFixedWidth[T <: Data](
+    gen:         T,
+    set:         Int,
+    width:       Int,
+    way:         Int = 1,
+    shouldReset: Boolean = false,
+    holdRead:    Boolean = false,
+    singlePort:  Boolean = false,
+    bypassWrite: Boolean = false
 ) extends Module {
 
   val dataBits  = gen.getWidth
-  val bankNum = math.ceil(dataBits.toDouble / width.toDouble).toInt
+  val bankNum   = math.ceil(dataBits.toDouble / width.toDouble).toInt
   val totalBits = bankNum * width
 
   val io = IO(new Bundle {
@@ -698,7 +731,7 @@ class SRAMTemplateWithFixedWidth[T <: Data]
     val w = Flipped(new SRAMWriteBus(gen, set, way))
   })
 
-  val wordType   = UInt(width.W)
+  val wordType = UInt(width.W)
   val writeDatas = (0 until bankNum).map(bank =>
     VecInit((0 until way).map(i =>
       io.w.req.bits.data(i).asTypeOf(UInt(totalBits.W)).asTypeOf(Vec(bankNum, wordType))(bank)
@@ -708,12 +741,12 @@ class SRAMTemplateWithFixedWidth[T <: Data]
   val srams = (0 until bankNum) map { bank =>
     val sramBank = Module(new SRAMTemplate(
       wordType,
-      set=set,
-      way=way,
+      set = set,
+      way = way,
       shouldReset = shouldReset,
       holdRead = holdRead,
       singlePort = singlePort,
-      bypassWrite = bypassWrite,
+      bypassWrite = bypassWrite
     ))
     // read req
     sramBank.io.r.req.valid       := io.r.req.valid
@@ -729,12 +762,12 @@ class SRAMTemplateWithFixedWidth[T <: Data]
   }
 
   io.r.req.ready := !io.w.req.valid
-  (0 until way).foreach{i =>
+  (0 until way).foreach { i =>
     io.r.resp.data(i) := VecInit((0 until bankNum).map(bank =>
-                           srams(bank).io.r.resp.data(i)
-                         )).asTypeOf(UInt(totalBits.W))(dataBits-1, 0).asTypeOf(gen.cloneType)
+      srams(bank).io.r.resp.data(i)
+    )).asTypeOf(UInt(totalBits.W))(dataBits - 1, 0).asTypeOf(gen.cloneType)
   }
 
   io.r.req.ready := srams.head.io.r.req.ready
   io.w.req.ready := srams.head.io.w.req.ready
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala b/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
index dbeb7a16809..7f4969e3ece 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheBundle.scala
@@ -17,46 +17,44 @@
 
 package xiangshan.frontend.icache
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import freechips.rocketchip.tilelink.{ClientMetadata, TLPermissions}
-import xiangshan._
-import utils._
+import freechips.rocketchip.tilelink.ClientMetadata
+import freechips.rocketchip.tilelink.TLPermissions
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
+import xiangshan._
 
-class ICacheReadBundle(implicit p: Parameters) extends ICacheBundle
-{
-  val vSetIdx       = Vec(2,UInt(log2Ceil(nSets).W))
-  val wayMask       = Vec(2,Vec(nWays, Bool()))
-  val blkOffset     = UInt(log2Ceil(blockBytes).W)
-  val isDoubleLine  = Bool()
+class ICacheReadBundle(implicit p: Parameters) extends ICacheBundle {
+  val vSetIdx      = Vec(2, UInt(log2Ceil(nSets).W))
+  val wayMask      = Vec(2, Vec(nWays, Bool()))
+  val blkOffset    = UInt(log2Ceil(blockBytes).W)
+  val isDoubleLine = Bool()
 }
 
-class ICacheMetaWriteBundle(implicit p: Parameters) extends ICacheBundle
-{
+class ICacheMetaWriteBundle(implicit p: Parameters) extends ICacheBundle {
   val virIdx  = UInt(idxBits.W)
   val phyTag  = UInt(tagBits.W)
   val waymask = UInt(nWays.W)
   val bankIdx = Bool()
 
-  def generate(tag:UInt, idx:UInt, waymask:UInt, bankIdx: Bool): Unit = {
+  def generate(tag: UInt, idx: UInt, waymask: UInt, bankIdx: Bool): Unit = {
     this.virIdx  := idx
     this.phyTag  := tag
     this.waymask := waymask
-    this.bankIdx   := bankIdx
+    this.bankIdx := bankIdx
   }
 
 }
 
-class ICacheDataWriteBundle(implicit p: Parameters) extends ICacheBundle
-{
+class ICacheDataWriteBundle(implicit p: Parameters) extends ICacheBundle {
   val virIdx  = UInt(idxBits.W)
   val data    = UInt(blockBits.W)
   val waymask = UInt(nWays.W)
   val bankIdx = Bool()
 
-  def generate(data:UInt, idx:UInt, waymask:UInt, bankIdx: Bool): Unit = {
+  def generate(data: UInt, idx: UInt, waymask: UInt, bankIdx: Bool): Unit = {
     this.virIdx  := idx
     this.data    := data
     this.waymask := waymask
@@ -65,26 +63,23 @@ class ICacheDataWriteBundle(implicit p: Parameters) extends ICacheBundle
 
 }
 
-class ICacheMetaRespBundle(implicit p: Parameters) extends ICacheBundle
-{
+class ICacheMetaRespBundle(implicit p: Parameters) extends ICacheBundle {
   val metas      = Vec(PortNumber, Vec(nWays, new ICacheMetadata))
   val codes      = Vec(PortNumber, Vec(nWays, UInt(ICacheMetaCodeBits.W)))
   val entryValid = Vec(PortNumber, Vec(nWays, Bool()))
 
   // for compatibility
-  def tags = VecInit(metas.map(port => VecInit(port.map( way => way.tag ))))
+  def tags = VecInit(metas.map(port => VecInit(port.map(way => way.tag))))
 }
 
-class ICacheDataRespBundle(implicit p: Parameters) extends ICacheBundle
-{
-  val datas   = Vec(ICacheDataBanks, UInt(ICacheDataBits.W))
-  val codes   = Vec(ICacheDataBanks, UInt(ICacheDataCodeBits.W))
+class ICacheDataRespBundle(implicit p: Parameters) extends ICacheBundle {
+  val datas = Vec(ICacheDataBanks, UInt(ICacheDataBits.W))
+  val codes = Vec(ICacheDataBanks, UInt(ICacheDataCodeBits.W))
 }
 
-class ICacheMetaReadBundle(implicit p: Parameters) extends ICacheBundle
-{
-    val req     = Flipped(DecoupledIO(new ICacheReadBundle))
-    val resp = Output(new ICacheMetaRespBundle)
+class ICacheMetaReadBundle(implicit p: Parameters) extends ICacheBundle {
+  val req  = Flipped(DecoupledIO(new ICacheReadBundle))
+  val resp = Output(new ICacheMetaRespBundle)
 }
 
 class ReplacerTouch(implicit p: Parameters) extends ICacheBundle {
@@ -95,4 +90,4 @@ class ReplacerTouch(implicit p: Parameters) extends ICacheBundle {
 class ReplacerVictim(implicit p: Parameters) extends ICacheBundle {
   val vSetIdx = ValidIO(UInt(log2Ceil(nSets).W))
   val way     = Input(UInt(log2Ceil(nWays).W))
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
index 43ff716ed78..43e33ddaf7a 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -16,132 +16,132 @@
 
 package xiangshan.frontend.icache
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
 import difftest._
 import freechips.rocketchip.tilelink.ClientStates
+import org.chipsalliance.cde.config.Parameters
+import utility._
+import utils._
 import xiangshan._
+import xiangshan.backend.fu.PMPReqBundle
+import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.cache.mmu._
-import utils._
-import utility._
-import xiangshan.backend.fu.{PMPReqBundle, PMPRespBundle}
-import xiangshan.frontend.{FtqICacheInfo, FtqToICacheRequestBundle, ExceptionType}
+import xiangshan.frontend.ExceptionType
+import xiangshan.frontend.FtqICacheInfo
+import xiangshan.frontend.FtqToICacheRequestBundle
 
-class ICacheMainPipeReq(implicit p: Parameters) extends ICacheBundle
-{
-  val vaddr  = UInt(VAddrBits.W)
+class ICacheMainPipeReq(implicit p: Parameters) extends ICacheBundle {
+  val vaddr   = UInt(VAddrBits.W)
   def vSetIdx = get_idx(vaddr)
 }
 
-class ICacheMainPipeResp(implicit p: Parameters) extends ICacheBundle
-{
-  val vaddr    = UInt(VAddrBits.W)
-  val data     = UInt((blockBits).W)
-  val paddr    = UInt(PAddrBits.W)
-  val gpaddr    = UInt(GPAddrBits.W)
-  val isForVSnonLeafPTE   = Bool()
-  val exception = UInt(ExceptionType.width.W)
-  val pmp_mmio  = Bool()
-  val itlb_pbmt = UInt(Pbmt.width.W)
+class ICacheMainPipeResp(implicit p: Parameters) extends ICacheBundle {
+  val vaddr                = UInt(VAddrBits.W)
+  val data                 = UInt(blockBits.W)
+  val paddr                = UInt(PAddrBits.W)
+  val gpaddr               = UInt(GPAddrBits.W)
+  val isForVSnonLeafPTE    = Bool()
+  val exception            = UInt(ExceptionType.width.W)
+  val pmp_mmio             = Bool()
+  val itlb_pbmt            = UInt(Pbmt.width.W)
   val exceptionFromBackend = Bool()
 }
 
-class ICacheMainPipeBundle(implicit p: Parameters) extends ICacheBundle
-{
-  val req  = Flipped(Decoupled(new FtqToICacheRequestBundle))
-  val resp = Vec(PortNumber, ValidIO(new ICacheMainPipeResp))
+class ICacheMainPipeBundle(implicit p: Parameters) extends ICacheBundle {
+  val req               = Flipped(Decoupled(new FtqToICacheRequestBundle))
+  val resp              = Vec(PortNumber, ValidIO(new ICacheMainPipeResp))
   val topdownIcacheMiss = Output(Bool())
-  val topdownItlbMiss = Output(Bool())
+  val topdownItlbMiss   = Output(Bool())
 }
 
-class ICacheMetaReqBundle(implicit p: Parameters) extends ICacheBundle{
-  val toIMeta       = DecoupledIO(new ICacheReadBundle)
-  val fromIMeta     = Input(new ICacheMetaRespBundle)
+class ICacheMetaReqBundle(implicit p: Parameters) extends ICacheBundle {
+  val toIMeta   = DecoupledIO(new ICacheReadBundle)
+  val fromIMeta = Input(new ICacheMetaRespBundle)
 }
 
-class ICacheDataReqBundle(implicit p: Parameters) extends ICacheBundle{
-  val toIData       = Vec(partWayNum, DecoupledIO(new ICacheReadBundle))
-  val fromIData     = Input(new ICacheDataRespBundle)
+class ICacheDataReqBundle(implicit p: Parameters) extends ICacheBundle {
+  val toIData   = Vec(partWayNum, DecoupledIO(new ICacheReadBundle))
+  val fromIData = Input(new ICacheDataRespBundle)
 }
 
-class ICacheMSHRBundle(implicit p: Parameters) extends ICacheBundle{
-  val req   = Decoupled(new ICacheMissReq)
-  val resp  = Flipped(ValidIO(new ICacheMissResp))
+class ICacheMSHRBundle(implicit p: Parameters) extends ICacheBundle {
+  val req  = Decoupled(new ICacheMissReq)
+  val resp = Flipped(ValidIO(new ICacheMissResp))
 }
 
-class ICachePMPBundle(implicit p: Parameters) extends ICacheBundle{
+class ICachePMPBundle(implicit p: Parameters) extends ICacheBundle {
   val req  = Valid(new PMPReqBundle())
   val resp = Input(new PMPRespBundle())
 }
 
-class ICachePerfInfo(implicit p: Parameters) extends ICacheBundle{
-  val only_0_hit     = Bool()
-  val only_0_miss    = Bool()
-  val hit_0_hit_1    = Bool()
-  val hit_0_miss_1   = Bool()
-  val miss_0_hit_1   = Bool()
-  val miss_0_miss_1  = Bool()
-  val hit_0_except_1 = Bool()
+class ICachePerfInfo(implicit p: Parameters) extends ICacheBundle {
+  val only_0_hit      = Bool()
+  val only_0_miss     = Bool()
+  val hit_0_hit_1     = Bool()
+  val hit_0_miss_1    = Bool()
+  val miss_0_hit_1    = Bool()
+  val miss_0_miss_1   = Bool()
+  val hit_0_except_1  = Bool()
   val miss_0_except_1 = Bool()
-  val except_0       = Bool()
-  val bank_hit       = Vec(2,Bool())
-  val hit            = Bool()
+  val except_0        = Bool()
+  val bank_hit        = Vec(2, Bool())
+  val hit             = Bool()
 }
 
 class ICacheMainPipeInterface(implicit p: Parameters) extends ICacheBundle {
   val hartId = Input(UInt(hartIdLen.W))
+
   /*** internal interface ***/
-  val dataArray     = new ICacheDataReqBundle
+  val dataArray = new ICacheDataReqBundle
+
   /** prefetch io */
-  val touch = Vec(PortNumber,ValidIO(new ReplacerTouch))
+  val touch         = Vec(PortNumber, ValidIO(new ReplacerTouch))
   val wayLookupRead = Flipped(DecoupledIO(new WayLookupInfo))
 
-  val mshr          = new ICacheMSHRBundle
-  val errors        = Output(Vec(PortNumber, ValidIO(new L1CacheErrorInfo)))
+  val mshr   = new ICacheMSHRBundle
+  val errors = Output(Vec(PortNumber, ValidIO(new L1CacheErrorInfo)))
+
   /*** outside interface ***/
-  //val fetch       = Vec(PortNumber, new ICacheMainPipeBundle)
+  // val fetch       = Vec(PortNumber, new ICacheMainPipeBundle)
   /* when ftq.valid is high in T + 1 cycle
    * the ftq component must be valid in T cycle
    */
-  val fetch       = new ICacheMainPipeBundle
-  val pmp         = Vec(PortNumber, new ICachePMPBundle)
-  val respStall   = Input(Bool())
+  val fetch     = new ICacheMainPipeBundle
+  val pmp       = Vec(PortNumber, new ICachePMPBundle)
+  val respStall = Input(Bool())
 
   val csr_parity_enable = Input(Bool())
-  val flush = Input(Bool())
+  val flush             = Input(Bool())
 
   val perfInfo = Output(new ICachePerfInfo)
 }
 
 class ICacheDB(implicit p: Parameters) extends ICacheBundle {
-  val blk_vaddr   = UInt((VAddrBits - blockOffBits).W)
-  val blk_paddr   = UInt((PAddrBits - blockOffBits).W)
-  val hit         = Bool()
+  val blk_vaddr = UInt((VAddrBits - blockOffBits).W)
+  val blk_paddr = UInt((PAddrBits - blockOffBits).W)
+  val hit       = Bool()
 }
 
-class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
-{
+class ICacheMainPipe(implicit p: Parameters) extends ICacheModule {
   val io = IO(new ICacheMainPipeInterface)
 
   /** Input/Output port */
-  val (fromFtq, toIFU)    = (io.fetch.req,          io.fetch.resp)
-  val (toData,  fromData) = (io.dataArray.toIData,  io.dataArray.fromIData)
-  val (toMSHR,  fromMSHR) = (io.mshr.req,           io.mshr.resp)
-  val (toPMP,   fromPMP)  = (io.pmp.map(_.req),     io.pmp.map(_.resp))
-  val fromWayLookup = io.wayLookupRead
+  val (fromFtq, toIFU)   = (io.fetch.req, io.fetch.resp)
+  val (toData, fromData) = (io.dataArray.toIData, io.dataArray.fromIData)
+  val (toMSHR, fromMSHR) = (io.mshr.req, io.mshr.resp)
+  val (toPMP, fromPMP)   = (io.pmp.map(_.req), io.pmp.map(_.resp))
+  val fromWayLookup      = io.wayLookupRead
 
   // Statistics on the frequency distribution of FTQ fire interval
   val cntFtqFireInterval = RegInit(0.U(32.W))
   cntFtqFireInterval := Mux(fromFtq.fire, 1.U, cntFtqFireInterval + 1.U)
-  XSPerfHistogram("ftq2icache_fire",
-                  cntFtqFireInterval, fromFtq.fire,
-                  1, 300, 1, right_strict = true)
+  XSPerfHistogram("ftq2icache_fire", cntFtqFireInterval, fromFtq.fire, 1, 300, 1, right_strict = true)
 
   /** pipeline control signal */
-  val s1_ready, s2_ready = Wire(Bool())
-  val s0_fire,  s1_fire , s2_fire  = Wire(Bool())
-  val s0_flush,  s1_flush , s2_flush  = Wire(Bool())
+  val s1_ready, s2_ready           = Wire(Bool())
+  val s0_fire, s1_fire, s2_fire    = Wire(Bool())
+  val s0_flush, s1_flush, s2_flush = Wire(Bool())
 
   /**
     ******************************************************************************
@@ -154,19 +154,20 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   /** s0 control */
   // 0,1,2,3 -> dataArray(data); 4 -> mainPipe
   // Ftq RegNext Register
-  val fromFtqReq          = fromFtq.bits.pcMemRead
-  val s0_valid            = fromFtq.valid
-  val s0_req_valid_all    = (0 until partWayNum + 1).map(i => fromFtq.bits.readValid(i))
-  val s0_req_vaddr_all    = (0 until partWayNum + 1).map(i => VecInit(Seq(fromFtqReq(i).startAddr, fromFtqReq(i).nextlineStart)))
-  val s0_req_vSetIdx_all  = (0 until partWayNum + 1).map(i => VecInit(s0_req_vaddr_all(i).map(get_idx)))
-  val s0_req_offset_all   = (0 until partWayNum + 1).map(i => s0_req_vaddr_all(i)(0)(log2Ceil(blockBytes)-1, 0))
-  val s0_doubleline_all   = (0 until partWayNum + 1).map(i => fromFtq.bits.readValid(i) && fromFtqReq(i).crossCacheline)
-
-  val s0_req_vaddr        = s0_req_vaddr_all.last
-  val s0_req_vSetIdx      = s0_req_vSetIdx_all.last
-  val s0_doubleline       = s0_doubleline_all.last
-
-  val s0_ftq_exception = VecInit((0 until PortNumber).map(i => ExceptionType.fromFtq(fromFtq.bits)))
+  val fromFtqReq       = fromFtq.bits.pcMemRead
+  val s0_valid         = fromFtq.valid
+  val s0_req_valid_all = (0 until partWayNum + 1).map(i => fromFtq.bits.readValid(i))
+  val s0_req_vaddr_all =
+    (0 until partWayNum + 1).map(i => VecInit(Seq(fromFtqReq(i).startAddr, fromFtqReq(i).nextlineStart)))
+  val s0_req_vSetIdx_all = (0 until partWayNum + 1).map(i => VecInit(s0_req_vaddr_all(i).map(get_idx)))
+  val s0_req_offset_all  = (0 until partWayNum + 1).map(i => s0_req_vaddr_all(i)(0)(log2Ceil(blockBytes) - 1, 0))
+  val s0_doubleline_all  = (0 until partWayNum + 1).map(i => fromFtq.bits.readValid(i) && fromFtqReq(i).crossCacheline)
+
+  val s0_req_vaddr   = s0_req_vaddr_all.last
+  val s0_req_vSetIdx = s0_req_vSetIdx_all.last
+  val s0_doubleline  = s0_doubleline_all.last
+
+  val s0_ftq_exception    = VecInit((0 until PortNumber).map(i => ExceptionType.fromFtq(fromFtq.bits)))
   val s0_excp_fromBackend = fromFtq.bits.backendIaf || fromFtq.bits.backendIpf || fromFtq.bits.backendIgpf
 
   /**
@@ -175,23 +176,29 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     ******************************************************************************
     */
   fromWayLookup.ready := s0_fire
-  val s0_waymasks       = VecInit(fromWayLookup.bits.waymask.map(_.asTypeOf(Vec(nWays, Bool()))))
-  val s0_req_ptags      = fromWayLookup.bits.ptag
-  val s0_req_gpaddr     = fromWayLookup.bits.gpaddr
-  val s0_req_isForVSnonLeafPTE    = fromWayLookup.bits.isForVSnonLeafPTE
-  val s0_itlb_exception = fromWayLookup.bits.itlb_exception
-  val s0_itlb_pbmt      = fromWayLookup.bits.itlb_pbmt
-  val s0_meta_codes     = fromWayLookup.bits.meta_codes
-  val s0_hits           = VecInit(fromWayLookup.bits.waymask.map(_.orR))
-
-  when(s0_fire){
-    assert((0 until PortNumber).map(i => s0_req_vSetIdx(i) === fromWayLookup.bits.vSetIdx(i)).reduce(_&&_),
-           "vSetIdxs from ftq and wayLookup are different! vaddr0=0x%x ftq: vidx0=0x%x vidx1=0x%x wayLookup: vidx0=0x%x vidx1=0x%x",
-           s0_req_vaddr(0), s0_req_vSetIdx(0), s0_req_vSetIdx(1), fromWayLookup.bits.vSetIdx(0), fromWayLookup.bits.vSetIdx(1))
+  val s0_waymasks              = VecInit(fromWayLookup.bits.waymask.map(_.asTypeOf(Vec(nWays, Bool()))))
+  val s0_req_ptags             = fromWayLookup.bits.ptag
+  val s0_req_gpaddr            = fromWayLookup.bits.gpaddr
+  val s0_req_isForVSnonLeafPTE = fromWayLookup.bits.isForVSnonLeafPTE
+  val s0_itlb_exception        = fromWayLookup.bits.itlb_exception
+  val s0_itlb_pbmt             = fromWayLookup.bits.itlb_pbmt
+  val s0_meta_codes            = fromWayLookup.bits.meta_codes
+  val s0_hits                  = VecInit(fromWayLookup.bits.waymask.map(_.orR))
+
+  when(s0_fire) {
+    assert(
+      (0 until PortNumber).map(i => s0_req_vSetIdx(i) === fromWayLookup.bits.vSetIdx(i)).reduce(_ && _),
+      "vSetIdxs from ftq and wayLookup are different! vaddr0=0x%x ftq: vidx0=0x%x vidx1=0x%x wayLookup: vidx0=0x%x vidx1=0x%x",
+      s0_req_vaddr(0),
+      s0_req_vSetIdx(0),
+      s0_req_vSetIdx(1),
+      fromWayLookup.bits.vSetIdx(0),
+      fromWayLookup.bits.vSetIdx(1)
+    )
   }
 
   val s0_exception_out = ExceptionType.merge(
-    s0_ftq_exception,  // backend-requested exception has the highest priority
+    s0_ftq_exception, // backend-requested exception has the highest priority
     s0_itlb_exception
   )
 
@@ -200,7 +207,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * data SRAM request
     ******************************************************************************
     */
-  for(i <- 0 until partWayNum) {
+  for (i <- 0 until partWayNum) {
     toData(i).valid             := s0_req_valid_all(i)
     toData(i).bits.isDoubleLine := s0_doubleline_all(i)
     toData(i).bits.vSetIdx      := s0_req_vSetIdx_all(i)
@@ -209,8 +216,8 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   }
 
   val s0_can_go = toData.last.ready && fromWayLookup.valid && s1_ready
-  s0_flush  := io.flush
-  s0_fire   := s0_valid && s0_can_go && !s0_flush
+  s0_flush := io.flush
+  s0_fire  := s0_valid && s0_can_go && !s0_flush
 
   fromFtq.ready := s0_can_go
 
@@ -224,28 +231,28 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     */
   val s1_valid = generatePipeControl(lastFire = s0_fire, thisFire = s1_fire, thisFlush = s1_flush, lastFlush = false.B)
 
-  val s1_req_vaddr        = RegEnable(s0_req_vaddr,        0.U.asTypeOf(s0_req_vaddr),     s0_fire)
-  val s1_req_ptags        = RegEnable(s0_req_ptags,        0.U.asTypeOf(s0_req_ptags),     s0_fire)
-  val s1_req_gpaddr       = RegEnable(s0_req_gpaddr,       0.U.asTypeOf(s0_req_gpaddr),    s0_fire)
-  val s1_req_isForVSnonLeafPTE      = RegEnable(s0_req_isForVSnonLeafPTE,      0.U.asTypeOf(s0_req_isForVSnonLeafPTE),   s0_fire)
-  val s1_doubleline       = RegEnable(s0_doubleline,       0.U.asTypeOf(s0_doubleline),    s0_fire)
-  val s1_SRAMhits         = RegEnable(s0_hits,             0.U.asTypeOf(s0_hits),          s0_fire)
-  val s1_itlb_exception   = RegEnable(s0_exception_out,    0.U.asTypeOf(s0_exception_out), s0_fire)
-  val s1_excp_fromBackend = RegEnable(s0_excp_fromBackend, false.B,                        s0_fire)
-  val s1_itlb_pbmt        = RegEnable(s0_itlb_pbmt,        0.U.asTypeOf(s0_itlb_pbmt),     s0_fire)
-  val s1_waymasks         = RegEnable(s0_waymasks,         0.U.asTypeOf(s0_waymasks),      s0_fire)
-  val s1_meta_codes       = RegEnable(s0_meta_codes,       0.U.asTypeOf(s0_meta_codes),    s0_fire)
-
-  val s1_req_vSetIdx  = s1_req_vaddr.map(get_idx)
-  val s1_req_paddr    = s1_req_vaddr.zip(s1_req_ptags).map{case(vaddr, ptag) => get_paddr_from_ptag(vaddr, ptag)}
-  val s1_req_offset   = s1_req_vaddr(0)(log2Ceil(blockBytes)-1, 0)
+  val s1_req_vaddr             = RegEnable(s0_req_vaddr, 0.U.asTypeOf(s0_req_vaddr), s0_fire)
+  val s1_req_ptags             = RegEnable(s0_req_ptags, 0.U.asTypeOf(s0_req_ptags), s0_fire)
+  val s1_req_gpaddr            = RegEnable(s0_req_gpaddr, 0.U.asTypeOf(s0_req_gpaddr), s0_fire)
+  val s1_req_isForVSnonLeafPTE = RegEnable(s0_req_isForVSnonLeafPTE, 0.U.asTypeOf(s0_req_isForVSnonLeafPTE), s0_fire)
+  val s1_doubleline            = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
+  val s1_SRAMhits              = RegEnable(s0_hits, 0.U.asTypeOf(s0_hits), s0_fire)
+  val s1_itlb_exception        = RegEnable(s0_exception_out, 0.U.asTypeOf(s0_exception_out), s0_fire)
+  val s1_excp_fromBackend      = RegEnable(s0_excp_fromBackend, false.B, s0_fire)
+  val s1_itlb_pbmt             = RegEnable(s0_itlb_pbmt, 0.U.asTypeOf(s0_itlb_pbmt), s0_fire)
+  val s1_waymasks              = RegEnable(s0_waymasks, 0.U.asTypeOf(s0_waymasks), s0_fire)
+  val s1_meta_codes            = RegEnable(s0_meta_codes, 0.U.asTypeOf(s0_meta_codes), s0_fire)
+
+  val s1_req_vSetIdx = s1_req_vaddr.map(get_idx)
+  val s1_req_paddr   = s1_req_vaddr.zip(s1_req_ptags).map { case (vaddr, ptag) => get_paddr_from_ptag(vaddr, ptag) }
+  val s1_req_offset  = s1_req_vaddr(0)(log2Ceil(blockBytes) - 1, 0)
 
   // do metaArray ECC check
-  val s1_meta_corrupt = VecInit((s1_req_ptags zip s1_meta_codes zip s1_waymasks).map{ case ((meta, code), waymask) =>
+  val s1_meta_corrupt = VecInit((s1_req_ptags zip s1_meta_codes zip s1_waymasks).map { case ((meta, code), waymask) =>
     val hit_num = PopCount(waymask)
     // NOTE: if not hit, encodeMetaECC(meta) =/= code can also be true, but we don't care about it
-    (encodeMetaECC(meta) =/= code && hit_num === 1.U) ||  // hit one way, but parity code does not match, ECC failure
-      hit_num > 1.U                                       // hit multi way, must be a ECC failure
+    (encodeMetaECC(meta) =/= code && hit_num === 1.U) || // hit one way, but parity code does not match, ECC failure
+    hit_num > 1.U                                        // hit multi way, must be a ECC failure
   })
 
   /**
@@ -253,9 +260,9 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * update replacement status register
     ******************************************************************************
     */
-  (0 until PortNumber).foreach{ i =>
-    io.touch(i).bits.vSetIdx  := s1_req_vSetIdx(i)
-    io.touch(i).bits.way      := OHToUInt(s1_waymasks(i))
+  (0 until PortNumber).foreach { i =>
+    io.touch(i).bits.vSetIdx := s1_req_vSetIdx(i)
+    io.touch(i).bits.way     := OHToUInt(s1_waymasks(i))
   }
   io.touch(0).valid := RegNext(s0_fire) && s1_SRAMhits(0)
   io.touch(1).valid := RegNext(s0_fire) && s1_SRAMhits(1) && s1_doubleline
@@ -269,7 +276,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     // if itlb has exception, paddr can be invalid, therefore pmp check can be skipped
     p.valid     := s1_valid // && s1_itlb_exception === ExceptionType.none
     p.bits.addr := s1_req_paddr(i)
-    p.bits.size := 3.U // TODO
+    p.bits.size := 3.U      // TODO
     p.bits.cmd  := TlbCmd.exec
   }
   val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
@@ -292,21 +299,27 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * select data from MSHR, SRAM
     ******************************************************************************
     */
-  val s1_MSHR_match = VecInit((0 until PortNumber).map(i => (s1_req_vSetIdx(i) === fromMSHR.bits.vSetIdx) &&
-                                                            (s1_req_ptags(i) === getPhyTagFromBlk(fromMSHR.bits.blkPaddr)) &&
-                                                            fromMSHR.valid && !fromMSHR.bits.corrupt))
-  val s1_MSHR_hits  = Seq(s1_valid && s1_MSHR_match(0),
-                          s1_valid && (s1_MSHR_match(1) && s1_doubleline))
-  val s1_MSHR_datas = fromMSHR.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt((blockBits/ICacheDataBanks).W)))
-
-  val s1_hits = (0 until PortNumber).map(i => ValidHoldBypass(s1_MSHR_hits(i) || (RegNext(s0_fire) && s1_SRAMhits(i)), s1_fire || s1_flush))
-
-  val s1_bankIdxLow  = s1_req_offset >> log2Ceil(blockBytes/ICacheDataBanks)
-  val s1_bankMSHRHit = VecInit((0 until ICacheDataBanks).map(i => (i.U >= s1_bankIdxLow) && s1_MSHR_hits(0) ||
-                                                      (i.U < s1_bankIdxLow) && s1_MSHR_hits(1)))
-  val s1_datas       = VecInit((0 until ICacheDataBanks).map(i => DataHoldBypass(Mux(s1_bankMSHRHit(i), s1_MSHR_datas(i), fromData.datas(i)),
-                                                          s1_bankMSHRHit(i) || RegNext(s0_fire))))
-  val s1_codes       = DataHoldBypass(fromData.codes, RegNext(s0_fire))
+  val s1_MSHR_match = VecInit((0 until PortNumber).map(i =>
+    (s1_req_vSetIdx(i) === fromMSHR.bits.vSetIdx) &&
+      (s1_req_ptags(i) === getPhyTagFromBlk(fromMSHR.bits.blkPaddr)) &&
+      fromMSHR.valid && !fromMSHR.bits.corrupt
+  ))
+  val s1_MSHR_hits  = Seq(s1_valid && s1_MSHR_match(0), s1_valid && (s1_MSHR_match(1) && s1_doubleline))
+  val s1_MSHR_datas = fromMSHR.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt((blockBits / ICacheDataBanks).W)))
+
+  val s1_hits = (0 until PortNumber).map(i =>
+    ValidHoldBypass(s1_MSHR_hits(i) || (RegNext(s0_fire) && s1_SRAMhits(i)), s1_fire || s1_flush)
+  )
+
+  val s1_bankIdxLow = s1_req_offset >> log2Ceil(blockBytes / ICacheDataBanks)
+  val s1_bankMSHRHit = VecInit((0 until ICacheDataBanks).map(i =>
+    (i.U >= s1_bankIdxLow) && s1_MSHR_hits(0) ||
+      (i.U < s1_bankIdxLow) && s1_MSHR_hits(1)
+  ))
+  val s1_datas = VecInit((0 until ICacheDataBanks).map(i =>
+    DataHoldBypass(Mux(s1_bankMSHRHit(i), s1_MSHR_datas(i), fromData.datas(i)), s1_bankMSHRHit(i) || RegNext(s0_fire))
+  ))
+  val s1_codes = DataHoldBypass(fromData.codes, RegNext(s0_fire))
 
   s1_flush := io.flush
   s1_ready := s2_ready || !s1_valid
@@ -323,24 +336,25 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
 
   val s2_valid = generatePipeControl(lastFire = s1_fire, thisFire = s2_fire, thisFlush = s2_flush, lastFlush = false.B)
 
-  val s2_req_vaddr        = RegEnable(s1_req_vaddr,        0.U.asTypeOf(s1_req_vaddr),     s1_fire)
-  val s2_req_ptags        = RegEnable(s1_req_ptags,        0.U.asTypeOf(s1_req_ptags),     s1_fire)
-  val s2_req_gpaddr       = RegEnable(s1_req_gpaddr,       0.U.asTypeOf(s1_req_gpaddr),    s1_fire)
-  val s2_req_isForVSnonLeafPTE      = RegEnable(s1_req_isForVSnonLeafPTE,      0.U.asTypeOf(s1_req_isForVSnonLeafPTE),   s1_fire)
-  val s2_doubleline       = RegEnable(s1_doubleline,       0.U.asTypeOf(s1_doubleline),    s1_fire)
-  val s2_exception        = RegEnable(s1_exception_out,    0.U.asTypeOf(s1_exception_out), s1_fire)  // includes itlb/pmp/meta exception
-  val s2_excp_fromBackend = RegEnable(s1_excp_fromBackend, false.B,                        s1_fire)
-  val s2_pmp_mmio         = RegEnable(s1_pmp_mmio,         0.U.asTypeOf(s1_pmp_mmio),      s1_fire)
-  val s2_itlb_pbmt        = RegEnable(s1_itlb_pbmt,        0.U.asTypeOf(s1_itlb_pbmt),     s1_fire)
-
-  val s2_req_vSetIdx  = s2_req_vaddr.map(get_idx)
-  val s2_req_offset   = s2_req_vaddr(0)(log2Ceil(blockBytes)-1, 0)
-  val s2_req_paddr    = s2_req_vaddr.zip(s2_req_ptags).map{case(vaddr, ptag) => get_paddr_from_ptag(vaddr, ptag)}
-
-  val s2_SRAMhits     = RegEnable(s1_SRAMhits, 0.U.asTypeOf(s1_SRAMhits), s1_fire)
-  val s2_codes        = RegEnable(s1_codes, 0.U.asTypeOf(s1_codes), s1_fire)
-  val s2_hits         = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
-  val s2_datas        = RegInit(VecInit(Seq.fill(ICacheDataBanks)(0.U((blockBits/ICacheDataBanks).W))))
+  val s2_req_vaddr             = RegEnable(s1_req_vaddr, 0.U.asTypeOf(s1_req_vaddr), s1_fire)
+  val s2_req_ptags             = RegEnable(s1_req_ptags, 0.U.asTypeOf(s1_req_ptags), s1_fire)
+  val s2_req_gpaddr            = RegEnable(s1_req_gpaddr, 0.U.asTypeOf(s1_req_gpaddr), s1_fire)
+  val s2_req_isForVSnonLeafPTE = RegEnable(s1_req_isForVSnonLeafPTE, 0.U.asTypeOf(s1_req_isForVSnonLeafPTE), s1_fire)
+  val s2_doubleline            = RegEnable(s1_doubleline, 0.U.asTypeOf(s1_doubleline), s1_fire)
+  val s2_exception =
+    RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_fire) // includes itlb/pmp/meta exception
+  val s2_excp_fromBackend = RegEnable(s1_excp_fromBackend, false.B, s1_fire)
+  val s2_pmp_mmio         = RegEnable(s1_pmp_mmio, 0.U.asTypeOf(s1_pmp_mmio), s1_fire)
+  val s2_itlb_pbmt        = RegEnable(s1_itlb_pbmt, 0.U.asTypeOf(s1_itlb_pbmt), s1_fire)
+
+  val s2_req_vSetIdx = s2_req_vaddr.map(get_idx)
+  val s2_req_offset  = s2_req_vaddr(0)(log2Ceil(blockBytes) - 1, 0)
+  val s2_req_paddr   = s2_req_vaddr.zip(s2_req_ptags).map { case (vaddr, ptag) => get_paddr_from_ptag(vaddr, ptag) }
+
+  val s2_SRAMhits = RegEnable(s1_SRAMhits, 0.U.asTypeOf(s1_SRAMhits), s1_fire)
+  val s2_codes    = RegEnable(s1_codes, 0.U.asTypeOf(s1_codes), s1_fire)
+  val s2_hits     = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
+  val s2_datas    = RegInit(VecInit(Seq.fill(ICacheDataBanks)(0.U((blockBits / ICacheDataBanks).W))))
 
   /**
     ******************************************************************************
@@ -348,23 +362,28 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     ******************************************************************************
     */
   // check data error
-  val s2_bankSel     = getBankSel(s2_req_offset, s2_valid)
-  val s2_bank_corrupt = (0 until ICacheDataBanks).map(i => (encodeDataECC(s2_datas(i)) =/= s2_codes(i)))
-  val s2_data_corrupt = (0 until PortNumber).map(port => (0 until ICacheDataBanks).map(bank =>
-                         s2_bank_corrupt(bank) && s2_bankSel(port)(bank).asBool).reduce(_||_) && s2_SRAMhits(port))
+  val s2_bankSel      = getBankSel(s2_req_offset, s2_valid)
+  val s2_bank_corrupt = (0 until ICacheDataBanks).map(i => encodeDataECC(s2_datas(i)) =/= s2_codes(i))
+  val s2_data_corrupt = (0 until PortNumber).map(port =>
+    (0 until ICacheDataBanks).map(bank =>
+      s2_bank_corrupt(bank) && s2_bankSel(port)(bank).asBool
+    ).reduce(_ || _) && s2_SRAMhits(port)
+  )
   // meta error is checked in prefetch pipeline
   val s2_meta_corrupt = RegEnable(s1_meta_corrupt, 0.U.asTypeOf(s1_meta_corrupt), s1_fire)
   // send errors to top
-  (0 until PortNumber).map{ i =>
-    io.errors(i).valid              := io.csr_parity_enable && RegNext(s1_fire) && (s2_meta_corrupt(i) || s2_data_corrupt(i))
-    io.errors(i).bits.report_to_beu := io.csr_parity_enable && RegNext(s1_fire) && (s2_meta_corrupt(i) || s2_data_corrupt(i))
-    io.errors(i).bits.paddr         := s2_req_paddr(i)
-    io.errors(i).bits.source        := DontCare
-    io.errors(i).bits.source.tag    := s2_meta_corrupt(i)
-    io.errors(i).bits.source.data   := s2_data_corrupt(i)
-    io.errors(i).bits.source.l2     := false.B
-    io.errors(i).bits.opType        := DontCare
-    io.errors(i).bits.opType.fetch  := true.B
+  (0 until PortNumber).map { i =>
+    io.errors(i).valid := io.csr_parity_enable && RegNext(s1_fire) && (s2_meta_corrupt(i) || s2_data_corrupt(i))
+    io.errors(i).bits.report_to_beu := io.csr_parity_enable && RegNext(s1_fire) && (s2_meta_corrupt(
+      i
+    ) || s2_data_corrupt(i))
+    io.errors(i).bits.paddr        := s2_req_paddr(i)
+    io.errors(i).bits.source       := DontCare
+    io.errors(i).bits.source.tag   := s2_meta_corrupt(i)
+    io.errors(i).bits.source.data  := s2_data_corrupt(i)
+    io.errors(i).bits.source.l2    := false.B
+    io.errors(i).bits.opType       := DontCare
+    io.errors(i).bits.opType.fetch := true.B
   }
 
   /**
@@ -372,21 +391,20 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * monitor missUint response port
     ******************************************************************************
     */
-  val s2_MSHR_match = VecInit((0 until PortNumber).map( i =>
+  val s2_MSHR_match = VecInit((0 until PortNumber).map(i =>
     (s2_req_vSetIdx(i) === fromMSHR.bits.vSetIdx) &&
-    (s2_req_ptags(i) === getPhyTagFromBlk(fromMSHR.bits.blkPaddr)) &&
-    fromMSHR.valid  // we don't care about whether it's corrupt here
+      (s2_req_ptags(i) === getPhyTagFromBlk(fromMSHR.bits.blkPaddr)) &&
+      fromMSHR.valid // we don't care about whether it's corrupt here
   ))
-  val s2_MSHR_hits  = Seq(s2_valid && s2_MSHR_match(0),
-                          s2_valid && s2_MSHR_match(1) && s2_doubleline)
-  val s2_MSHR_datas = fromMSHR.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt((blockBits/ICacheDataBanks).W)))
+  val s2_MSHR_hits  = Seq(s2_valid && s2_MSHR_match(0), s2_valid && s2_MSHR_match(1) && s2_doubleline)
+  val s2_MSHR_datas = fromMSHR.bits.data.asTypeOf(Vec(ICacheDataBanks, UInt((blockBits / ICacheDataBanks).W)))
 
-  val s2_bankIdxLow  = s2_req_offset >> log2Ceil(blockBytes/ICacheDataBanks)
-  val s2_bankMSHRHit = VecInit((0 until ICacheDataBanks).map( i =>
+  val s2_bankIdxLow = s2_req_offset >> log2Ceil(blockBytes / ICacheDataBanks)
+  val s2_bankMSHRHit = VecInit((0 until ICacheDataBanks).map(i =>
     ((i.U >= s2_bankIdxLow) && s2_MSHR_hits(0)) || ((i.U < s2_bankIdxLow) && s2_MSHR_hits(1))
   ))
 
-  (0 until ICacheDataBanks).foreach{ i =>
+  (0 until ICacheDataBanks).foreach { i =>
     when(s1_fire) {
       s2_datas := s1_datas
     }.elsewhen(s2_bankMSHRHit(i) && !fromMSHR.bits.corrupt) {
@@ -395,7 +413,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     }
   }
 
-  (0 until PortNumber).foreach{ i =>
+  (0 until PortNumber).foreach { i =>
     when(s1_fire) {
       s2_hits := s1_hits
     }.elsewhen(s2_MSHR_hits(i)) {
@@ -405,7 +423,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
   }
 
   val s2_l2_corrupt = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
-  (0 until PortNumber).foreach{ i =>
+  (0 until PortNumber).foreach { i =>
     when(s1_fire) {
       s2_l2_corrupt(i) := false.B
     }.elsewhen(s2_MSHR_hits(i)) {
@@ -420,7 +438,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     */
 
   // merge pmp mmio and itlb pbmt
-  val s2_mmio = VecInit((s2_pmp_mmio zip s2_itlb_pbmt).map{ case (mmio, pbmt) =>
+  val s2_mmio = VecInit((s2_pmp_mmio zip s2_itlb_pbmt).map { case (mmio, pbmt) =>
     mmio || Pbmt.isUncache(pbmt)
   })
 
@@ -429,16 +447,16 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
    * also, if previous has exception, latter port should also not be fetched
    */
   val s2_miss = VecInit((0 until PortNumber).map { i =>
-    !s2_hits(i) && (if (i==0) true.B else s2_doubleline) &&
-      s2_exception.take(i+1).map(_ === ExceptionType.none).reduce(_&&_) &&
-      s2_mmio.take(i+1).map(!_).reduce(_&&_)
+    !s2_hits(i) && (if (i == 0) true.B else s2_doubleline) &&
+    s2_exception.take(i + 1).map(_ === ExceptionType.none).reduce(_ && _) &&
+    s2_mmio.take(i + 1).map(!_).reduce(_ && _)
   })
 
   val toMSHRArbiter = Module(new Arbiter(new ICacheMissReq, PortNumber))
 
   // To avoid sending duplicate requests.
   val has_send = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
-  (0 until PortNumber).foreach{ i =>
+  (0 until PortNumber).foreach { i =>
     when(s1_fire) {
       has_send(i) := false.B
     }.elsewhen(toMSHRArbiter.io.in(i).fire) {
@@ -446,16 +464,16 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     }
   }
 
-  (0 until PortNumber).map{ i =>
-    toMSHRArbiter.io.in(i).valid          := s2_valid && s2_miss(i) && !has_send(i) && !s2_flush
-    toMSHRArbiter.io.in(i).bits.blkPaddr  := getBlkAddr(s2_req_paddr(i))
-    toMSHRArbiter.io.in(i).bits.vSetIdx   := s2_req_vSetIdx(i)
+  (0 until PortNumber).map { i =>
+    toMSHRArbiter.io.in(i).valid         := s2_valid && s2_miss(i) && !has_send(i) && !s2_flush
+    toMSHRArbiter.io.in(i).bits.blkPaddr := getBlkAddr(s2_req_paddr(i))
+    toMSHRArbiter.io.in(i).bits.vSetIdx  := s2_req_vSetIdx(i)
   }
   toMSHR <> toMSHRArbiter.io.out
 
-  XSPerfAccumulate("to_missUnit_stall",  toMSHR.valid && !toMSHR.ready)
+  XSPerfAccumulate("to_missUnit_stall", toMSHR.valid && !toMSHR.ready)
 
-  val s2_fetch_finish = !s2_miss.reduce(_||_)
+  val s2_fetch_finish = !s2_miss.reduce(_ || _)
 
   // also raise af if data/l2 corrupt is detected
   val s2_data_exception = VecInit(s2_data_corrupt.map(ExceptionType.fromECC(io.csr_parity_enable, _)))
@@ -463,7 +481,7 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
 
   // merge s2 exceptions, itlb has the highest priority, meta next, meta/data/l2 lowest (and we dont care about prioritizing between this three)
   val s2_exception_out = ExceptionType.merge(
-    s2_exception,  // includes itlb/pmp/meta exception
+    s2_exception, // includes itlb/pmp/meta exception
     s2_data_exception,
     s2_l2_exception
   )
@@ -473,11 +491,11 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * response to IFU
     ******************************************************************************
     */
-  (0 until PortNumber).foreach{ i =>
-    if(i == 0) {
+  (0 until PortNumber).foreach { i =>
+    if (i == 0) {
       toIFU(i).valid          := s2_fire
       toIFU(i).bits.exception := s2_exception_out(i)
-      toIFU(i).bits.pmp_mmio  := s2_pmp_mmio(i)   // pass pmp_mmio instead of merged mmio to IFU
+      toIFU(i).bits.pmp_mmio  := s2_pmp_mmio(i) // pass pmp_mmio instead of merged mmio to IFU
       toIFU(i).bits.itlb_pbmt := s2_itlb_pbmt(i)
       toIFU(i).bits.data      := s2_datas.asTypeOf(UInt(blockBits.W))
     } else {
@@ -488,10 +506,10 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
       toIFU(i).bits.data      := DontCare
     }
     toIFU(i).bits.exceptionFromBackend := s2_excp_fromBackend
-    toIFU(i).bits.vaddr       := s2_req_vaddr(i)
-    toIFU(i).bits.paddr       := s2_req_paddr(i)
-    toIFU(i).bits.gpaddr      := s2_req_gpaddr  // Note: toIFU(1).bits.gpaddr is actually DontCare in current design
-    toIFU(i).bits.isForVSnonLeafPTE     := s2_req_isForVSnonLeafPTE
+    toIFU(i).bits.vaddr                := s2_req_vaddr(i)
+    toIFU(i).bits.paddr                := s2_req_paddr(i)
+    toIFU(i).bits.gpaddr := s2_req_gpaddr // Note: toIFU(1).bits.gpaddr is actually DontCare in current design
+    toIFU(i).bits.isForVSnonLeafPTE := s2_req_isForVSnonLeafPTE
   }
 
   s2_flush := io.flush
@@ -503,14 +521,14 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * report Tilelink corrupt error
     ******************************************************************************
     */
-  (0 until PortNumber).map{ i =>
-    when(RegNext(s2_fire && s2_l2_corrupt(i))){
-      io.errors(i).valid                 := true.B
-      io.errors(i).bits.report_to_beu    := false.B // l2 should have report that to bus error unit, no need to do it again
-      io.errors(i).bits.paddr            := RegNext(s2_req_paddr(i))
-      io.errors(i).bits.source.tag       := false.B
-      io.errors(i).bits.source.data      := false.B
-      io.errors(i).bits.source.l2        := true.B
+  (0 until PortNumber).map { i =>
+    when(RegNext(s2_fire && s2_l2_corrupt(i))) {
+      io.errors(i).valid              := true.B
+      io.errors(i).bits.report_to_beu := false.B // l2 should have report that to bus error unit, no need to do it again
+      io.errors(i).bits.paddr         := RegNext(s2_req_paddr(i))
+      io.errors(i).bits.source.tag    := false.B
+      io.errors(i).bits.source.data   := false.B
+      io.errors(i).bits.source.l2     := true.B
     }
   }
 
@@ -519,25 +537,25 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     * performance info. TODO: need to simplify the logic
     ***********************************************************s*******************
     */
-  io.perfInfo.only_0_hit      :=  s2_hits(0) && !s2_doubleline
+  io.perfInfo.only_0_hit      := s2_hits(0) && !s2_doubleline
   io.perfInfo.only_0_miss     := !s2_hits(0) && !s2_doubleline
-  io.perfInfo.hit_0_hit_1     :=  s2_hits(0) &&  s2_hits(1) && s2_doubleline
-  io.perfInfo.hit_0_miss_1    :=  s2_hits(0) && !s2_hits(1) && s2_doubleline
-  io.perfInfo.miss_0_hit_1    := !s2_hits(0) &&  s2_hits(1) && s2_doubleline
+  io.perfInfo.hit_0_hit_1     := s2_hits(0) && s2_hits(1) && s2_doubleline
+  io.perfInfo.hit_0_miss_1    := s2_hits(0) && !s2_hits(1) && s2_doubleline
+  io.perfInfo.miss_0_hit_1    := !s2_hits(0) && s2_hits(1) && s2_doubleline
   io.perfInfo.miss_0_miss_1   := !s2_hits(0) && !s2_hits(1) && s2_doubleline
-  io.perfInfo.hit_0_except_1  :=  s2_hits(0) && (s2_exception(1) =/= ExceptionType.none) && s2_doubleline
+  io.perfInfo.hit_0_except_1  := s2_hits(0) && (s2_exception(1) =/= ExceptionType.none) && s2_doubleline
   io.perfInfo.miss_0_except_1 := !s2_hits(0) && (s2_exception(1) =/= ExceptionType.none) && s2_doubleline
-  io.perfInfo.bank_hit(0)     :=  s2_hits(0)
-  io.perfInfo.bank_hit(1)     :=  s2_hits(1) && s2_doubleline
-  io.perfInfo.except_0        :=  s2_exception(0) =/= ExceptionType.none
-  io.perfInfo.hit             :=  s2_hits(0) && (!s2_doubleline || s2_hits(1))
+  io.perfInfo.bank_hit(0)     := s2_hits(0)
+  io.perfInfo.bank_hit(1)     := s2_hits(1) && s2_doubleline
+  io.perfInfo.except_0        := s2_exception(0) =/= ExceptionType.none
+  io.perfInfo.hit             := s2_hits(0) && (!s2_doubleline || s2_hits(1))
 
   /** <PERF> fetch bubble generated by icache miss */
-  XSPerfAccumulate("icache_bubble_s2_miss", s2_valid && !s2_fetch_finish )
+  XSPerfAccumulate("icache_bubble_s2_miss", s2_valid && !s2_fetch_finish)
   XSPerfAccumulate("icache_bubble_s0_wayLookup", s0_valid && !fromWayLookup.ready)
 
   io.fetch.topdownIcacheMiss := !s2_fetch_finish
-  io.fetch.topdownItlbMiss := s0_valid && !fromWayLookup.ready
+  io.fetch.topdownItlbMiss   := s0_valid && !fromWayLookup.ready
 
   // class ICacheTouchDB(implicit p: Parameters) extends ICacheBundle{
   //   val blkPaddr  = UInt((PAddrBits - blockOffBits).W)
@@ -577,17 +595,20 @@ class ICacheMainPipe(implicit p: Parameters) extends ICacheModule
     (0 until ICacheDataBanks).map { i =>
       val diffMainPipeOut = DifftestModule(new DiffRefillEvent, dontCare = true)
       diffMainPipeOut.coreid := io.hartId
-      diffMainPipeOut.index := (3 + i).U
+      diffMainPipeOut.index  := (3 + i).U
 
-      val bankSel = getBankSel(s2_req_offset, s2_valid).reduce(_|_)
+      val bankSel = getBankSel(s2_req_offset, s2_valid).reduce(_ | _)
       val lineSel = getLineSel(s2_req_offset)
 
       diffMainPipeOut.valid := s2_fire && bankSel(i).asBool && Mux(lineSel(i), !discards(1), !discards(0))
-      diffMainPipeOut.addr  := Mux(lineSel(i), blkPaddrAll(1) + (i.U << (log2Ceil(blockBytes/ICacheDataBanks))),
-                                               blkPaddrAll(0) + (i.U << (log2Ceil(blockBytes/ICacheDataBanks))))
+      diffMainPipeOut.addr := Mux(
+        lineSel(i),
+        blkPaddrAll(1) + (i.U << (log2Ceil(blockBytes / ICacheDataBanks))),
+        blkPaddrAll(0) + (i.U << (log2Ceil(blockBytes / ICacheDataBanks)))
+      )
 
-      diffMainPipeOut.data :=  s2_datas(i).asTypeOf(diffMainPipeOut.data)
+      diffMainPipeOut.data  := s2_datas(i).asTypeOf(diffMainPipeOut.data)
       diffMainPipeOut.idtfr := DontCare
     }
   }
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
index 16be82864c8..56ff6e70256 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
@@ -16,61 +16,57 @@
 
 package xiangshan.frontend.icache
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
+import difftest._
 import freechips.rocketchip.diplomacy.IdRange
+import freechips.rocketchip.tilelink._
 import freechips.rocketchip.tilelink.ClientStates._
 import freechips.rocketchip.tilelink.TLPermissions._
-import freechips.rocketchip.tilelink._
+import org.chipsalliance.cde.config.Parameters
+import utility._
+import utils._
 import xiangshan._
 import xiangshan.cache._
-import utils._
-import utility._
-import difftest._
-
 
 abstract class ICacheMissUnitModule(implicit p: Parameters) extends XSModule
-  with HasICacheParameters
+    with HasICacheParameters
 
 abstract class ICacheMissUnitBundle(implicit p: Parameters) extends XSBundle
-  with HasICacheParameters
+    with HasICacheParameters
 
+class Demultiplexer[T <: Data](val gen: T, val n: Int) extends Module {
 
-class Demultiplexer[T <: Data](val gen: T, val n: Int) extends Module
-{
   /** Hardware module that is used to sequence 1 producers into n consumer.
     * Priority is given to lower producer.
     */
   require(n >= 2)
   val io = IO(new Bundle {
-    val in      = Flipped(DecoupledIO(gen))
-    val out     = Vec(n, DecoupledIO(gen))
-    val chosen  = Output(UInt(log2Ceil(n).W))
+    val in     = Flipped(DecoupledIO(gen))
+    val out    = Vec(n, DecoupledIO(gen))
+    val chosen = Output(UInt(log2Ceil(n).W))
   })
 
-  val grant = false.B +: (1 until n).map(i=> (0 until i).map(io.out(_).ready).reduce(_||_))
+  val grant = false.B +: (1 until n).map(i => (0 until i).map(io.out(_).ready).reduce(_ || _))
   for (i <- 0 until n) {
-    io.out(i).bits := io.in.bits
+    io.out(i).bits  := io.in.bits
     io.out(i).valid := !grant(i) && io.in.valid
   }
 
   io.in.ready := grant.last || io.out.last.ready
-  io.chosen := PriorityEncoder(VecInit(io.out.map(_.ready)))
+  io.chosen   := PriorityEncoder(VecInit(io.out.map(_.ready)))
 }
 
-
-class MuxBundle[T <: Data](val gen: T, val n: Int) extends Module
-{
+class MuxBundle[T <: Data](val gen: T, val n: Int) extends Module {
   require(n >= 2)
   val io = IO(new Bundle {
-    val sel     = Input(UInt(log2Ceil(n).W))
-    val in      = Flipped(Vec(n, DecoupledIO(gen)))
-    val out     = DecoupledIO(gen)
+    val sel = Input(UInt(log2Ceil(n).W))
+    val in  = Flipped(Vec(n, DecoupledIO(gen)))
+    val out = DecoupledIO(gen)
   })
 
-  io.in   <> DontCare
-  io.out  <> DontCare
+  io.in <> DontCare
+  io.out <> DontCare
   for (i <- 0 until n) {
     when(io.sel === i.U) {
       io.out <> io.in(i)
@@ -79,35 +75,30 @@ class MuxBundle[T <: Data](val gen: T, val n: Int) extends Module
   }
 }
 
-
 class ICacheMissReq(implicit p: Parameters) extends ICacheBundle {
-  val blkPaddr  = UInt((PAddrBits - blockOffBits).W)
-  val vSetIdx   = UInt(idxBits.W)
+  val blkPaddr = UInt((PAddrBits - blockOffBits).W)
+  val vSetIdx  = UInt(idxBits.W)
 }
 
-
 class ICacheMissResp(implicit p: Parameters) extends ICacheBundle {
-  val blkPaddr  = UInt((PAddrBits - blockOffBits).W)
-  val vSetIdx   = UInt(idxBits.W)
-  val waymask   = UInt(nWays.W)
-  val data      = UInt(blockBits.W)
-  val corrupt   = Bool()
+  val blkPaddr = UInt((PAddrBits - blockOffBits).W)
+  val vSetIdx  = UInt(idxBits.W)
+  val waymask  = UInt(nWays.W)
+  val data     = UInt(blockBits.W)
+  val corrupt  = Bool()
 }
 
-
 class LookUpMSHR(implicit p: Parameters) extends ICacheBundle {
-  val info  = ValidIO(new ICacheMissReq)
-  val hit   = Input(Bool())
+  val info = ValidIO(new ICacheMissReq)
+  val hit  = Input(Bool())
 }
 
-
 class MSHRResp(implicit p: Parameters) extends ICacheBundle {
-  val blkPaddr  = UInt((PAddrBits - blockOffBits).W)
-  val vSetIdx   = UInt(idxBits.W)
-  val waymask   = UInt(log2Ceil(nWays).W)
+  val blkPaddr = UInt((PAddrBits - blockOffBits).W)
+  val vSetIdx  = UInt(idxBits.W)
+  val waymask  = UInt(log2Ceil(nWays).W)
 }
 
-
 class MSHRAcquire(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheBundle {
   val acquire = new TLBundleA(edge.bundle)
   val vSetIdx = UInt(idxBits.W)
@@ -115,34 +106,34 @@ class MSHRAcquire(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheBundle
 
 class ICacheMSHR(edge: TLEdgeOut, isFetch: Boolean, ID: Int)(implicit p: Parameters) extends ICacheMissUnitModule {
   val io = IO(new Bundle {
-    val fencei      = Input(Bool())
-    val flush       = Input(Bool())
-    val invalid     = Input(Bool())
-    val req         = Flipped(DecoupledIO(new ICacheMissReq))
-    val acquire     = DecoupledIO(new MSHRAcquire(edge))
-    val lookUps     = Flipped(Vec(2, new LookUpMSHR))
-    val resp        = ValidIO(new MSHRResp)
-    val victimWay   = Input(UInt(log2Ceil(nWays).W))
+    val fencei    = Input(Bool())
+    val flush     = Input(Bool())
+    val invalid   = Input(Bool())
+    val req       = Flipped(DecoupledIO(new ICacheMissReq))
+    val acquire   = DecoupledIO(new MSHRAcquire(edge))
+    val lookUps   = Flipped(Vec(2, new LookUpMSHR))
+    val resp      = ValidIO(new MSHRResp)
+    val victimWay = Input(UInt(log2Ceil(nWays).W))
   })
 
-  val valid     = RegInit(Bool(), false.B)
+  val valid = RegInit(Bool(), false.B)
   // this MSHR doesn't respones to fetch and sram
-  val flush     = RegInit(Bool(), false.B)
-  val fencei    = RegInit(Bool(), false.B)
+  val flush  = RegInit(Bool(), false.B)
+  val fencei = RegInit(Bool(), false.B)
   // this MSHR has been issued
-  val issue     = RegInit(Bool(), false.B)
+  val issue = RegInit(Bool(), false.B)
 
-  val blkPaddr  = RegInit(UInt((PAddrBits - blockOffBits).W), 0.U)
-  val vSetIdx   = RegInit(UInt(idxBits.W), 0.U)
-  val waymask   = RegInit(UInt(log2Ceil(nWays).W), 0.U)
+  val blkPaddr = RegInit(UInt((PAddrBits - blockOffBits).W), 0.U)
+  val vSetIdx  = RegInit(UInt(idxBits.W), 0.U)
+  val waymask  = RegInit(UInt(log2Ceil(nWays).W), 0.U)
 
   // look up and return result at the same cycle
-  val hits = io.lookUps.map(lookup => valid && !fencei && !flush && (lookup.info.bits.vSetIdx === vSetIdx) && 
-                                      (lookup.info.bits.blkPaddr === blkPaddr))
+  val hits = io.lookUps.map(lookup =>
+    valid && !fencei && !flush && (lookup.info.bits.vSetIdx === vSetIdx) &&
+      (lookup.info.bits.blkPaddr === blkPaddr)
+  )
   // Decoupling valid and bits
-  (0 until 2).foreach { i =>
-    io.lookUps(i).hit := hits(i)
-  }
+  (0 until 2).foreach(i => io.lookUps(i).hit := hits(i))
 
   // disable wake up when hit MSHR (fencei is low)
   // when(hit) {
@@ -151,8 +142,8 @@ class ICacheMSHR(edge: TLEdgeOut, isFetch: Boolean, ID: Int)(implicit p: Paramet
 
   // invalid when the req hasn't been issued
   when(io.fencei || io.flush) {
-    fencei  := true.B
-    flush   := true.B
+    fencei := true.B
+    flush  := true.B
     when(!issue) {
       valid := false.B
     }
@@ -161,20 +152,20 @@ class ICacheMSHR(edge: TLEdgeOut, isFetch: Boolean, ID: Int)(implicit p: Paramet
   // receive request and register
   io.req.ready := !valid && !io.flush && !io.fencei
   when(io.req.fire) {
-    valid     := true.B
-    flush     := false.B
-    issue     := false.B
-    fencei    := false.B
-    blkPaddr  := io.req.bits.blkPaddr
-    vSetIdx   := io.req.bits.vSetIdx
+    valid    := true.B
+    flush    := false.B
+    issue    := false.B
+    fencei   := false.B
+    blkPaddr := io.req.bits.blkPaddr
+    vSetIdx  := io.req.bits.vSetIdx
   }
 
   // send request to L2
   io.acquire.valid := valid && !issue && !io.flush && !io.fencei
-  val getBlock = edge.Get( 
-    fromSource  = ID.U,
-    toAddress   = Cat(blkPaddr, 0.U(blockOffBits.W)),
-    lgSize      = (log2Up(cacheParams.blockBytes)).U
+  val getBlock = edge.Get(
+    fromSource = ID.U,
+    toAddress = Cat(blkPaddr, 0.U(blockOffBits.W)),
+    lgSize = log2Up(cacheParams.blockBytes).U
   )._2
   io.acquire.bits.acquire := getBlock
   io.acquire.bits.acquire.user.lift(ReqSourceKey).foreach(_ := MemReqSource.CPUInst.id.U)
@@ -198,32 +189,30 @@ class ICacheMSHR(edge: TLEdgeOut, isFetch: Boolean, ID: Int)(implicit p: Paramet
   io.resp.bits.waymask  := waymask
 }
 
-class ICacheMissBundle(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheBundle{
+class ICacheMissBundle(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheBundle {
   // difftest
-  val hartId      = Input(Bool())
+  val hartId = Input(Bool())
   // control
-  val fencei      = Input(Bool())
-  val flush       = Input(Bool())
+  val fencei = Input(Bool())
+  val flush  = Input(Bool())
   // fetch
-  val fetch_req     = Flipped(DecoupledIO(new ICacheMissReq))
-  val fetch_resp    = ValidIO(new ICacheMissResp)
+  val fetch_req  = Flipped(DecoupledIO(new ICacheMissReq))
+  val fetch_resp = ValidIO(new ICacheMissResp)
   // prefetch
-  val prefetch_req      = Flipped(DecoupledIO(new ICacheMissReq))
+  val prefetch_req = Flipped(DecoupledIO(new ICacheMissReq))
   // SRAM Write Req
-  val meta_write    = DecoupledIO(new ICacheMetaWriteBundle)
-  val data_write    = DecoupledIO(new ICacheDataWriteBundle)
+  val meta_write = DecoupledIO(new ICacheMetaWriteBundle)
+  val data_write = DecoupledIO(new ICacheDataWriteBundle)
   // get victim from replacer
-  val victim        = new ReplacerVictim
+  val victim = new ReplacerVictim
   // Tilelink
   val mem_acquire = DecoupledIO(new TLBundleA(edge.bundle))
   val mem_grant   = Flipped(DecoupledIO(new TLBundleD(edge.bundle)))
 }
 
-
-class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMissUnitModule
-{
+class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMissUnitModule {
   val io = IO(new ICacheMissBundle(edge))
-                                     
+
   /**
     ******************************************************************************
     * fetch have higher priority
@@ -249,26 +238,26 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
   val prefetchDemux = Module(new Demultiplexer(new ICacheMissReq, nPrefetchMshr))
   val prefetchArb   = Module(new MuxBundle(new MSHRAcquire(edge), nPrefetchMshr))
   val acquireArb    = Module(new Arbiter(new MSHRAcquire(edge), nFetchMshr + 1))
-  
+
   // To avoid duplicate request reception.
-  val fetchHit, prefetchHit   = Wire(Bool())
-  fetchDemux.io.in            <> io.fetch_req
-  fetchDemux.io.in.valid      := io.fetch_req.valid && !fetchHit
-  io.fetch_req.ready          := fetchDemux.io.in.ready || fetchHit
-  prefetchDemux.io.in         <> io.prefetch_req
-  prefetchDemux.io.in.valid   := io.prefetch_req.valid && !prefetchHit
-  io.prefetch_req.ready       := prefetchDemux.io.in.ready || prefetchHit
-  acquireArb.io.in.last       <> prefetchArb.io.out
+  val fetchHit, prefetchHit = Wire(Bool())
+  fetchDemux.io.in <> io.fetch_req
+  fetchDemux.io.in.valid := io.fetch_req.valid && !fetchHit
+  io.fetch_req.ready     := fetchDemux.io.in.ready || fetchHit
+  prefetchDemux.io.in <> io.prefetch_req
+  prefetchDemux.io.in.valid := io.prefetch_req.valid && !prefetchHit
+  io.prefetch_req.ready     := prefetchDemux.io.in.ready || prefetchHit
+  acquireArb.io.in.last <> prefetchArb.io.out
 
   // mem_acquire connect
-  io.mem_acquire.valid        := acquireArb.io.out.valid
-  io.mem_acquire.bits         := acquireArb.io.out.bits.acquire
-  acquireArb.io.out.ready     := io.mem_acquire.ready
+  io.mem_acquire.valid    := acquireArb.io.out.valid
+  io.mem_acquire.bits     := acquireArb.io.out.bits.acquire
+  acquireArb.io.out.ready := io.mem_acquire.ready
 
   val fetchMSHRs = (0 until nFetchMshr).map { i =>
     val mshr = Module(new ICacheMSHR(edge, true, i))
-    mshr.io.flush   := false.B
-    mshr.io.fencei  := io.fencei
+    mshr.io.flush  := false.B
+    mshr.io.fencei := io.fencei
     mshr.io.req <> fetchDemux.io.out(i)
     mshr.io.lookUps(0).info.valid := io.fetch_req.valid
     mshr.io.lookUps(0).info.bits  := io.fetch_req.bits
@@ -281,8 +270,8 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
 
   val prefetchMSHRs = (0 until nPrefetchMshr).map { i =>
     val mshr = Module(new ICacheMSHR(edge, false, nFetchMshr + i))
-    mshr.io.flush := io.flush
-    mshr.io.fencei  := io.fencei
+    mshr.io.flush  := io.flush
+    mshr.io.fencei := io.fencei
     mshr.io.req <> prefetchDemux.io.out(i)
     mshr.io.lookUps(0).info.valid := io.fetch_req.valid
     mshr.io.lookUps(0).info.bits  := io.fetch_req.bits
@@ -299,12 +288,12 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
     * - look up all mshr
     ******************************************************************************
     */
-  val allMSHRs = (fetchMSHRs ++ prefetchMSHRs)
+  val allMSHRs = fetchMSHRs ++ prefetchMSHRs
   val prefetchHitFetchReq = (io.prefetch_req.bits.blkPaddr === io.fetch_req.bits.blkPaddr) &&
-                            (io.prefetch_req.bits.vSetIdx === io.fetch_req.bits.vSetIdx) &&
-                            io.fetch_req.valid
-  fetchHit    := allMSHRs.map(mshr => mshr.io.lookUps(0).hit).reduce(_||_)
-  prefetchHit := allMSHRs.map(mshr => mshr.io.lookUps(1).hit).reduce(_||_) || prefetchHitFetchReq
+    (io.prefetch_req.bits.vSetIdx === io.fetch_req.bits.vSetIdx) &&
+    io.fetch_req.valid
+  fetchHit    := allMSHRs.map(mshr => mshr.io.lookUps(0).hit).reduce(_ || _)
+  prefetchHit := allMSHRs.map(mshr => mshr.io.lookUps(1).hit).reduce(_ || _) || prefetchHitFetchReq
 
   /**
     ******************************************************************************
@@ -317,28 +306,34 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
   // When the FIFO is full, enqueue and dequeue operations do not occur at the same cycle.
   // So the depth of the FIFO is set to match the number of MSHRs.
   // val priorityFIFO = Module(new Queue(UInt(log2Ceil(nPrefetchMshr).W), nPrefetchMshr, hasFlush=true))
-  val priorityFIFO = Module(new FIFOReg(UInt(log2Ceil(nPrefetchMshr).W), nPrefetchMshr, hasFlush=true))
+  val priorityFIFO = Module(new FIFOReg(UInt(log2Ceil(nPrefetchMshr).W), nPrefetchMshr, hasFlush = true))
   priorityFIFO.io.flush.get := io.flush || io.fencei
   priorityFIFO.io.enq.valid := prefetchDemux.io.in.fire
   priorityFIFO.io.enq.bits  := prefetchDemux.io.chosen
   priorityFIFO.io.deq.ready := prefetchArb.io.out.fire
   prefetchArb.io.sel        := priorityFIFO.io.deq.bits
-  assert(!(priorityFIFO.io.enq.fire ^ prefetchDemux.io.in.fire), "priorityFIFO.io.enq and io.prefetch_req must fire at the same cycle")
-  assert(!(priorityFIFO.io.deq.fire ^ prefetchArb.io.out.fire), "priorityFIFO.io.deq and prefetchArb.io.out must fire at the same cycle")
+  assert(
+    !(priorityFIFO.io.enq.fire ^ prefetchDemux.io.in.fire),
+    "priorityFIFO.io.enq and io.prefetch_req must fire at the same cycle"
+  )
+  assert(
+    !(priorityFIFO.io.deq.fire ^ prefetchArb.io.out.fire),
+    "priorityFIFO.io.deq and prefetchArb.io.out must fire at the same cycle"
+  )
 
   /**
     ******************************************************************************
     * Tilelink D channel (grant)
     ******************************************************************************
     */
-  //cacheline register
+  // cacheline register
   val readBeatCnt = RegInit(UInt(log2Up(refillCycles).W), 0.U)
   val respDataReg = RegInit(VecInit(Seq.fill(refillCycles)(0.U(beatBits.W))))
 
   val wait_last = readBeatCnt === (refillCycles - 1).U
   when(io.mem_grant.fire && edge.hasData(io.mem_grant.bits)) {
     respDataReg(readBeatCnt) := io.mem_grant.bits.data
-    readBeatCnt := Mux(wait_last, 0.U, readBeatCnt + 1.U)
+    readBeatCnt              := Mux(wait_last, 0.U, readBeatCnt + 1.U)
   }
 
   // last transition finsh or corrupt
@@ -352,8 +347,8 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
   val id_r        = RegNext(io.mem_grant.bits.source)
 
   // if any beat is corrupt, the whole response (to mainPipe/metaArray/dataArray) is corrupt
-  val corrupt_r   = RegInit(false.B)
-  when (io.mem_grant.fire && edge.hasData(io.mem_grant.bits) && io.mem_grant.bits.corrupt) {
+  val corrupt_r = RegInit(false.B)
+  when(io.mem_grant.fire && edge.hasData(io.mem_grant.bits) && io.mem_grant.bits.corrupt) {
     corrupt_r := true.B
   }.elsewhen(io.fetch_resp.fire) {
     corrupt_r := false.B
@@ -364,9 +359,7 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
     * invalid mshr when finish transition
     ******************************************************************************
     */
-  (0 until (nFetchMshr + nPrefetchMshr)).foreach{ i =>
-    allMSHRs(i).io.invalid := last_fire_r && (id_r === i.U)
-  }
+  (0 until (nFetchMshr + nPrefetchMshr)).foreach(i => allMSHRs(i).io.invalid := last_fire_r && (id_r === i.U))
 
   /**
     ******************************************************************************
@@ -375,7 +368,7 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
     */
   // get request information from MSHRs
   val allMSHRs_resp = VecInit(allMSHRs.map(mshr => mshr.io.resp))
-  val mshr_resp = allMSHRs_resp(id_r)
+  val mshr_resp     = allMSHRs_resp(id_r)
 
   // get waymask from replacer when acquire fire
   io.victim.vSetIdx.valid := acquireArb.io.out.fire
@@ -389,14 +382,18 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
   val write_sram_valid = fetch_resp_valid && !corrupt_r && !io.flush && !io.fencei
 
   // write SRAM
-  io.meta_write.bits.generate(tag     = getPhyTagFromBlk(mshr_resp.bits.blkPaddr),
-                              idx     = mshr_resp.bits.vSetIdx,
-                              waymask = waymask,
-                              bankIdx = mshr_resp.bits.vSetIdx(0))
-  io.data_write.bits.generate(data    = respDataReg.asUInt,
-                              idx     = mshr_resp.bits.vSetIdx,
-                              waymask = waymask,
-                              bankIdx = mshr_resp.bits.vSetIdx(0))
+  io.meta_write.bits.generate(
+    tag = getPhyTagFromBlk(mshr_resp.bits.blkPaddr),
+    idx = mshr_resp.bits.vSetIdx,
+    waymask = waymask,
+    bankIdx = mshr_resp.bits.vSetIdx(0)
+  )
+  io.data_write.bits.generate(
+    data = respDataReg.asUInt,
+    idx = mshr_resp.bits.vSetIdx,
+    waymask = waymask,
+    bankIdx = mshr_resp.bits.vSetIdx(0)
+  )
 
   io.meta_write.valid := write_sram_valid
   io.data_write.valid := write_sram_valid
@@ -415,30 +412,31 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
     ******************************************************************************
     */
   // Duplicate requests will be excluded.
-  XSPerfAccumulate("enq_fetch_req",     fetchDemux.io.in.fire)
-  XSPerfAccumulate("enq_prefetch_req",  prefetchDemux.io.in.fire)
+  XSPerfAccumulate("enq_fetch_req", fetchDemux.io.in.fire)
+  XSPerfAccumulate("enq_prefetch_req", prefetchDemux.io.in.fire)
 
   /**
     ******************************************************************************
     * ChiselDB: record ICache SRAM write log
     ******************************************************************************
     */
-  class ICacheSRAMDB(implicit p: Parameters) extends ICacheBundle{
-    val blkPaddr  = UInt((PAddrBits - blockOffBits).W)
-    val vSetIdx   = UInt(idxBits.W)
-    val waymask   = UInt(log2Ceil(nWays).W)
+  class ICacheSRAMDB(implicit p: Parameters) extends ICacheBundle {
+    val blkPaddr = UInt((PAddrBits - blockOffBits).W)
+    val vSetIdx  = UInt(idxBits.W)
+    val waymask  = UInt(log2Ceil(nWays).W)
   }
 
-  val isWriteICacheSRAMTable = WireInit(Constantin.createRecord("isWriteICacheSRAMTable" + p(XSCoreParamsKey).HartId.toString))
+  val isWriteICacheSRAMTable =
+    WireInit(Constantin.createRecord("isWriteICacheSRAMTable" + p(XSCoreParamsKey).HartId.toString))
   val ICacheSRAMTable = ChiselDB.createTable("ICacheSRAMTable" + p(XSCoreParamsKey).HartId.toString, new ICacheSRAMDB)
 
   val ICacheSRAMDBDumpData = Wire(new ICacheSRAMDB)
-  ICacheSRAMDBDumpData.blkPaddr  := mshr_resp.bits.blkPaddr
-  ICacheSRAMDBDumpData.vSetIdx   := mshr_resp.bits.vSetIdx
-  ICacheSRAMDBDumpData.waymask   := OHToUInt(waymask)
+  ICacheSRAMDBDumpData.blkPaddr := mshr_resp.bits.blkPaddr
+  ICacheSRAMDBDumpData.vSetIdx  := mshr_resp.bits.vSetIdx
+  ICacheSRAMDBDumpData.waymask  := OHToUInt(waymask)
   ICacheSRAMTable.log(
-    data  = ICacheSRAMDBDumpData,
-    en    = write_sram_valid,
+    data = ICacheSRAMDBDumpData,
+    en = write_sram_valid,
     clock = clock,
     reset = reset
   )
@@ -457,4 +455,4 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheMiss
     difftest.data   := respDataReg.asTypeOf(difftest.data)
     difftest.idtfr  := DontCare
   }
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 75c1a25a663..2048cf23cb7 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -16,42 +16,43 @@
 
 package xiangshan.frontend.icache
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
 import difftest._
 import freechips.rocketchip.tilelink._
+import huancun.PreferCacheKey
+import org.chipsalliance.cde.config.Parameters
+import utility._
 import utils._
+import xiangshan.SoftIfetchPrefetchBundle
+import xiangshan.XSCoreParamsKey
+import xiangshan.backend.fu.PMPReqBundle
+import xiangshan.backend.fu.PMPRespBundle
 import xiangshan.cache.mmu._
 import xiangshan.frontend._
-import xiangshan.backend.fu.{PMPReqBundle, PMPRespBundle}
-import huancun.PreferCacheKey
-import xiangshan.XSCoreParamsKey
-import xiangshan.SoftIfetchPrefetchBundle
-import utility._
 
 abstract class IPrefetchBundle(implicit p: Parameters) extends ICacheBundle
 abstract class IPrefetchModule(implicit p: Parameters) extends ICacheModule
 
 class IPrefetchReq(implicit p: Parameters) extends IPrefetchBundle {
-  val startAddr     : UInt   = UInt(VAddrBits.W)
-  val nextlineStart : UInt   = UInt(VAddrBits.W)
-  val ftqIdx        : FtqPtr = new FtqPtr
+  val startAddr:      UInt   = UInt(VAddrBits.W)
+  val nextlineStart:  UInt   = UInt(VAddrBits.W)
+  val ftqIdx:         FtqPtr = new FtqPtr
   val isSoftPrefetch: Bool   = Bool()
   def crossCacheline: Bool   = startAddr(blockOffBits - 1) === 1.U
 
   def fromFtqICacheInfo(info: FtqICacheInfo): IPrefetchReq = {
-    this.startAddr := info.startAddr
-    this.nextlineStart := info.nextlineStart
-    this.ftqIdx := info.ftqIdx
+    this.startAddr      := info.startAddr
+    this.nextlineStart  := info.nextlineStart
+    this.ftqIdx         := info.ftqIdx
     this.isSoftPrefetch := false.B
     this
   }
 
   def fromSoftPrefetch(req: SoftIfetchPrefetchBundle): IPrefetchReq = {
-    this.startAddr := req.vaddr
-    this.nextlineStart := req.vaddr + (1 << blockOffBits).U
-    this.ftqIdx := DontCare
+    this.startAddr      := req.vaddr
+    this.nextlineStart  := req.vaddr + (1 << blockOffBits).U
+    this.ftqIdx         := DontCare
     this.isSoftPrefetch := true.B
     this
   }
@@ -63,31 +64,30 @@ class IPrefetchIO(implicit p: Parameters) extends IPrefetchBundle {
   val csr_parity_enable = Input(Bool())
   val flush             = Input(Bool())
 
-  val req               = Flipped(Decoupled(new IPrefetchReq))
-  val flushFromBpu      = Flipped(new BpuFlushInfo)
-  val itlb              = Vec(PortNumber, new TlbRequestIO)
-  val pmp               = Vec(PortNumber, new ICachePMPBundle)
-  val metaRead          = new ICacheMetaReqBundle
-  val MSHRReq           = DecoupledIO(new ICacheMissReq)
-  val MSHRResp          = Flipped(ValidIO(new ICacheMissResp))
-  val wayLookupWrite    = DecoupledIO(new WayLookupInfo)
+  val req            = Flipped(Decoupled(new IPrefetchReq))
+  val flushFromBpu   = Flipped(new BpuFlushInfo)
+  val itlb           = Vec(PortNumber, new TlbRequestIO)
+  val pmp            = Vec(PortNumber, new ICachePMPBundle)
+  val metaRead       = new ICacheMetaReqBundle
+  val MSHRReq        = DecoupledIO(new ICacheMissReq)
+  val MSHRResp       = Flipped(ValidIO(new ICacheMissResp))
+  val wayLookupWrite = DecoupledIO(new WayLookupInfo)
 }
 
-class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
-{
+class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule {
   val io: IPrefetchIO = IO(new IPrefetchIO)
 
-  val (toITLB,  fromITLB) = (io.itlb.map(_.req), io.itlb.map(_.resp))
-  val (toPMP,  fromPMP)   = (io.pmp.map(_.req), io.pmp.map(_.resp))
-  val (toMeta,  fromMeta) = (io.metaRead.toIMeta,  io.metaRead.fromIMeta)
-  val (toMSHR, fromMSHR)  = (io.MSHRReq, io.MSHRResp)
-  val toWayLookup = io.wayLookupWrite
+  val (toITLB, fromITLB) = (io.itlb.map(_.req), io.itlb.map(_.resp))
+  val (toPMP, fromPMP)   = (io.pmp.map(_.req), io.pmp.map(_.resp))
+  val (toMeta, fromMeta) = (io.metaRead.toIMeta, io.metaRead.fromIMeta)
+  val (toMSHR, fromMSHR) = (io.MSHRReq, io.MSHRResp)
+  val toWayLookup        = io.wayLookupWrite
 
-  val s0_fire, s1_fire, s2_fire             = WireInit(false.B)
-  val s0_discard, s2_discard                = WireInit(false.B)
-  val s0_ready, s1_ready, s2_ready          = WireInit(false.B)
-  val s0_flush, s1_flush, s2_flush          = WireInit(false.B)
-  val from_bpu_s0_flush, from_bpu_s1_flush  = WireInit(false.B)
+  val s0_fire, s1_fire, s2_fire            = WireInit(false.B)
+  val s0_discard, s2_discard               = WireInit(false.B)
+  val s0_ready, s1_ready, s2_ready         = WireInit(false.B)
+  val s0_flush, s1_flush, s2_flush         = WireInit(false.B)
+  val from_bpu_s0_flush, from_bpu_s1_flush = WireInit(false.B)
 
   /**
     ******************************************************************************
@@ -97,21 +97,21 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     * - 3. send req to Meta SRAM
     ******************************************************************************
     */
-  val s0_valid  = io.req.valid
+  val s0_valid = io.req.valid
 
   /**
     ******************************************************************************
     * receive ftq req
     ******************************************************************************
     */
-  val s0_req_vaddr    = VecInit(Seq(io.req.bits.startAddr, io.req.bits.nextlineStart))
-  val s0_req_ftqIdx   = io.req.bits.ftqIdx
+  val s0_req_vaddr      = VecInit(Seq(io.req.bits.startAddr, io.req.bits.nextlineStart))
+  val s0_req_ftqIdx     = io.req.bits.ftqIdx
   val s0_isSoftPrefetch = io.req.bits.isSoftPrefetch
-  val s0_doubleline   = io.req.bits.crossCacheline
-  val s0_req_vSetIdx  = s0_req_vaddr.map(get_idx)
+  val s0_doubleline     = io.req.bits.crossCacheline
+  val s0_req_vSetIdx    = s0_req_vaddr.map(get_idx)
 
   from_bpu_s0_flush := !s0_isSoftPrefetch && (io.flushFromBpu.shouldFlushByStage2(s0_req_ftqIdx) ||
-                                              io.flushFromBpu.shouldFlushByStage3(s0_req_ftqIdx))
+    io.flushFromBpu.shouldFlushByStage3(s0_req_ftqIdx))
   s0_flush := io.flush || from_bpu_s0_flush || s1_flush
 
   val s0_can_go = s1_ready && toITLB(0).ready && toITLB(1).ready && toMeta.ready
@@ -130,16 +130,16 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     */
   val s1_valid = generatePipeControl(lastFire = s0_fire, thisFire = s1_fire, thisFlush = s1_flush, lastFlush = false.B)
 
-  val s1_req_vaddr    = RegEnable(s0_req_vaddr, 0.U.asTypeOf(s0_req_vaddr), s0_fire)
+  val s1_req_vaddr      = RegEnable(s0_req_vaddr, 0.U.asTypeOf(s0_req_vaddr), s0_fire)
   val s1_isSoftPrefetch = RegEnable(s0_isSoftPrefetch, 0.U.asTypeOf(s0_isSoftPrefetch), s0_fire)
-  val s1_doubleline   = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
-  val s1_req_ftqIdx   = RegEnable(s0_req_ftqIdx, 0.U.asTypeOf(s0_req_ftqIdx), s0_fire)
-  val s1_req_vSetIdx  = VecInit(s1_req_vaddr.map(get_idx))
+  val s1_doubleline     = RegEnable(s0_doubleline, 0.U.asTypeOf(s0_doubleline), s0_fire)
+  val s1_req_ftqIdx     = RegEnable(s0_req_ftqIdx, 0.U.asTypeOf(s0_req_ftqIdx), s0_fire)
+  val s1_req_vSetIdx    = VecInit(s1_req_vaddr.map(get_idx))
 
   val m_idle :: m_itlbResend :: m_metaResend :: m_enqWay :: m_enterS2 :: Nil = Enum(5)
-  val state = RegInit(m_idle)
-  val next_state = WireDefault(state)
-  val s0_fire_r = RegNext(s0_fire)
+  val state                                                                  = RegInit(m_idle)
+  val next_state                                                             = WireDefault(state)
+  val s0_fire_r                                                              = RegNext(s0_fire)
   dontTouch(state)
   dontTouch(next_state)
   state := next_state
@@ -149,7 +149,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     * resend itlb req if miss
     ******************************************************************************
     */
-  val s1_wait_itlb  = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
+  val s1_wait_itlb = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
   (0 until PortNumber).foreach { i =>
     when(s1_flush) {
       s1_wait_itlb(i) := false.B
@@ -159,15 +159,20 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
       s1_wait_itlb(i) := false.B
     }
   }
-  val s1_need_itlb    = VecInit(Seq((RegNext(s0_fire) || s1_wait_itlb(0)) && fromITLB(0).bits.miss,
-                                    (RegNext(s0_fire) || s1_wait_itlb(1)) && fromITLB(1).bits.miss && s1_doubleline))
-  val tlb_valid_pulse = VecInit(Seq((RegNext(s0_fire) || s1_wait_itlb(0)) && !fromITLB(0).bits.miss,
-                                    (RegNext(s0_fire) || s1_wait_itlb(1)) && !fromITLB(1).bits.miss && s1_doubleline))
-  val tlb_valid_latch = VecInit((0 until PortNumber).map(i => ValidHoldBypass(tlb_valid_pulse(i), s1_fire, flush=s1_flush)))
-  val itlb_finish     = tlb_valid_latch(0) && (!s1_doubleline || tlb_valid_latch(1))
+  val s1_need_itlb = VecInit(Seq(
+    (RegNext(s0_fire) || s1_wait_itlb(0)) && fromITLB(0).bits.miss,
+    (RegNext(s0_fire) || s1_wait_itlb(1)) && fromITLB(1).bits.miss && s1_doubleline
+  ))
+  val tlb_valid_pulse = VecInit(Seq(
+    (RegNext(s0_fire) || s1_wait_itlb(0)) && !fromITLB(0).bits.miss,
+    (RegNext(s0_fire) || s1_wait_itlb(1)) && !fromITLB(1).bits.miss && s1_doubleline
+  ))
+  val tlb_valid_latch =
+    VecInit((0 until PortNumber).map(i => ValidHoldBypass(tlb_valid_pulse(i), s1_fire, flush = s1_flush)))
+  val itlb_finish = tlb_valid_latch(0) && (!s1_doubleline || tlb_valid_latch(1))
 
   for (i <- 0 until PortNumber) {
-    toITLB(i).valid             := s1_need_itlb(i) || (s0_valid && (if(i == 0) true.B else s0_doubleline))
+    toITLB(i).valid             := s1_need_itlb(i) || (s0_valid && (if (i == 0) true.B else s0_doubleline))
     toITLB(i).bits              := DontCare
     toITLB(i).bits.size         := 3.U
     toITLB(i).bits.vaddr        := Mux(s1_need_itlb(i), s1_req_vaddr(i), s0_req_vaddr(i))
@@ -183,24 +188,40 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     * Receive resp from ITLB
     ******************************************************************************
     */
-  val s1_req_paddr_wire     = VecInit(fromITLB.map(_.bits.paddr(0)))
-  val s1_req_paddr_reg      = VecInit((0 until PortNumber).map( i =>
+  val s1_req_paddr_wire = VecInit(fromITLB.map(_.bits.paddr(0)))
+  val s1_req_paddr_reg = VecInit((0 until PortNumber).map(i =>
     RegEnable(s1_req_paddr_wire(i), 0.U(PAddrBits.W), tlb_valid_pulse(i))
   ))
-  val s1_req_paddr          = VecInit((0 until PortNumber).map( i =>
+  val s1_req_paddr = VecInit((0 until PortNumber).map(i =>
     Mux(tlb_valid_pulse(i), s1_req_paddr_wire(i), s1_req_paddr_reg(i))
   ))
-  val s1_req_gpaddr_tmp     = VecInit((0 until PortNumber).map( i =>
-    ResultHoldBypass(valid = tlb_valid_pulse(i), init = 0.U.asTypeOf(fromITLB(i).bits.gpaddr(0)), data = fromITLB(i).bits.gpaddr(0))
+  val s1_req_gpaddr_tmp = VecInit((0 until PortNumber).map(i =>
+    ResultHoldBypass(
+      valid = tlb_valid_pulse(i),
+      init = 0.U.asTypeOf(fromITLB(i).bits.gpaddr(0)),
+      data = fromITLB(i).bits.gpaddr(0)
+    )
   ))
-  val s1_req_isForVSnonLeafPTE_tmp    = VecInit((0 until PortNumber).map( i =>
-    ResultHoldBypass(valid = tlb_valid_pulse(i), init = 0.U.asTypeOf(fromITLB(i).bits.isForVSnonLeafPTE), data = fromITLB(i).bits.isForVSnonLeafPTE)
+  val s1_req_isForVSnonLeafPTE_tmp = VecInit((0 until PortNumber).map(i =>
+    ResultHoldBypass(
+      valid = tlb_valid_pulse(i),
+      init = 0.U.asTypeOf(fromITLB(i).bits.isForVSnonLeafPTE),
+      data = fromITLB(i).bits.isForVSnonLeafPTE
+    )
   ))
-  val s1_itlb_exception     = VecInit((0 until PortNumber).map( i =>
-    ResultHoldBypass(valid = tlb_valid_pulse(i), init = 0.U(ExceptionType.width.W), data = ExceptionType.fromTlbResp(fromITLB(i).bits))
+  val s1_itlb_exception = VecInit((0 until PortNumber).map(i =>
+    ResultHoldBypass(
+      valid = tlb_valid_pulse(i),
+      init = 0.U(ExceptionType.width.W),
+      data = ExceptionType.fromTlbResp(fromITLB(i).bits)
+    )
   ))
-  val s1_itlb_pbmt          = VecInit((0 until PortNumber).map( i =>
-    ResultHoldBypass(valid = tlb_valid_pulse(i), init = 0.U.asTypeOf(fromITLB(i).bits.pbmt(0)), data = fromITLB(i).bits.pbmt(0))
+  val s1_itlb_pbmt = VecInit((0 until PortNumber).map(i =>
+    ResultHoldBypass(
+      valid = tlb_valid_pulse(i),
+      init = 0.U.asTypeOf(fromITLB(i).bits.pbmt(0)),
+      data = fromITLB(i).bits.pbmt(0)
+    )
   ))
   val s1_itlb_exception_gpf = VecInit(s1_itlb_exception.map(_ === ExceptionType.gpf))
 
@@ -226,12 +247,12 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     ******************************************************************************
     */
   val s1_need_meta = ((state === m_itlbResend) && itlb_finish) || (state === m_metaResend)
-  toMeta.valid              := s1_need_meta || s0_valid
-  toMeta.bits               := DontCare
-  toMeta.bits.isDoubleLine  := Mux(s1_need_meta, s1_doubleline, s0_doubleline)
+  toMeta.valid             := s1_need_meta || s0_valid
+  toMeta.bits              := DontCare
+  toMeta.bits.isDoubleLine := Mux(s1_need_meta, s1_doubleline, s0_doubleline)
 
   for (i <- 0 until PortNumber) {
-    toMeta.bits.vSetIdx(i)  := Mux(s1_need_meta, s1_req_vSetIdx(i), s0_req_vSetIdx(i))
+    toMeta.bits.vSetIdx(i) := Mux(s1_need_meta, s1_req_vSetIdx(i), s0_req_vSetIdx(i))
   }
 
   /**
@@ -239,16 +260,19 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     * Receive resp from IMeta and check
     ******************************************************************************
     */
-  val s1_req_ptags    = VecInit(s1_req_paddr.map(get_phy_tag))
+  val s1_req_ptags = VecInit(s1_req_paddr.map(get_phy_tag))
 
-  val s1_meta_ptags   = fromMeta.tags
-  val s1_meta_valids  = fromMeta.entryValid
+  val s1_meta_ptags  = fromMeta.tags
+  val s1_meta_valids = fromMeta.entryValid
 
   def get_waymask(paddrs: Vec[UInt]): Vec[UInt] = {
-    val ptags         = paddrs.map(get_phy_tag)
-    val tag_eq_vec    = VecInit((0 until PortNumber).map( p => VecInit((0 until nWays).map( w => s1_meta_ptags(p)(w) === ptags(p)))))
-    val tag_match_vec = VecInit((0 until PortNumber).map( k => VecInit(tag_eq_vec(k).zipWithIndex.map{ case(way_tag_eq, w) => way_tag_eq && s1_meta_valids(k)(w)})))
-    val waymasks      = VecInit(tag_match_vec.map(_.asUInt))
+    val ptags = paddrs.map(get_phy_tag)
+    val tag_eq_vec =
+      VecInit((0 until PortNumber).map(p => VecInit((0 until nWays).map(w => s1_meta_ptags(p)(w) === ptags(p)))))
+    val tag_match_vec = VecInit((0 until PortNumber).map(k =>
+      VecInit(tag_eq_vec(k).zipWithIndex.map { case (way_tag_eq, w) => way_tag_eq && s1_meta_valids(k)(w) })
+    ))
+    val waymasks = VecInit(tag_match_vec.map(_.asUInt))
     waymasks
   }
 
@@ -282,7 +306,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     require(mask.getWidth == nWays)
     val new_mask  = WireInit(mask)
     val new_code  = WireInit(code)
-    val valid = fromMSHR.valid && !fromMSHR.bits.corrupt
+    val valid     = fromMSHR.valid && !fromMSHR.bits.corrupt
     val vset_same = fromMSHR.bits.vSetIdx === vSetIdx
     val ptag_same = getPhyTagFromBlk(fromMSHR.bits.blkPaddr) === ptag
     val way_same  = fromMSHR.bits.waymask === mask
@@ -300,18 +324,18 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     (new_mask, new_code)
   }
 
-  val s1_SRAM_valid = s0_fire_r || RegNext(s1_need_meta && toMeta.ready)
-  val s1_MSHR_valid = fromMSHR.valid && !fromMSHR.bits.corrupt
-  val s1_waymasks   = WireInit(VecInit(Seq.fill(PortNumber)(0.U(nWays.W))))
-  val s1_waymasks_r = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_SRAM_valid || s1_MSHR_valid)
+  val s1_SRAM_valid   = s0_fire_r || RegNext(s1_need_meta && toMeta.ready)
+  val s1_MSHR_valid   = fromMSHR.valid && !fromMSHR.bits.corrupt
+  val s1_waymasks     = WireInit(VecInit(Seq.fill(PortNumber)(0.U(nWays.W))))
+  val s1_waymasks_r   = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_SRAM_valid || s1_MSHR_valid)
   val s1_meta_codes   = WireInit(VecInit(Seq.fill(PortNumber)(0.U(ICacheMetaCodeBits.W))))
   val s1_meta_codes_r = RegEnable(s1_meta_codes, 0.U.asTypeOf(s1_meta_codes), s1_SRAM_valid || s1_MSHR_valid)
 
   // update waymasks and meta_codes
-  (0 until PortNumber).foreach{i =>
-    val old_waymask    = Mux(s1_SRAM_valid, s1_SRAM_waymasks(i),   s1_waymasks_r(i))
+  (0 until PortNumber).foreach { i =>
+    val old_waymask    = Mux(s1_SRAM_valid, s1_SRAM_waymasks(i), s1_waymasks_r(i))
     val old_meta_codes = Mux(s1_SRAM_valid, s1_SRAM_meta_codes(i), s1_meta_codes_r(i))
-    val new_info = update_meta_info(old_waymask, s1_req_vSetIdx(i), s1_req_ptags(i), old_meta_codes)
+    val new_info       = update_meta_info(old_waymask, s1_req_vSetIdx(i), s1_req_ptags(i), old_meta_codes)
     s1_waymasks(i)   := new_info._1
     s1_meta_codes(i) := new_info._2
   }
@@ -322,16 +346,17 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     ******** **********************************************************************
     */
   // Disallow enqueuing wayLookup when SRAM write occurs.
-  toWayLookup.valid             := ((state === m_enqWay) || ((state === m_idle) && itlb_finish)) &&
-    !s1_flush && !fromMSHR.valid && !s1_isSoftPrefetch  // do not enqueue soft prefetch
-  toWayLookup.bits.vSetIdx      := s1_req_vSetIdx
-  toWayLookup.bits.waymask      := s1_waymasks
-  toWayLookup.bits.ptag         := s1_req_ptags
-  toWayLookup.bits.gpaddr       := s1_req_gpaddr
-  toWayLookup.bits.isForVSnonLeafPTE      := s1_req_isForVSnonLeafPTE
-  toWayLookup.bits.meta_codes   := s1_meta_codes
+  toWayLookup.valid := ((state === m_enqWay) || ((state === m_idle) && itlb_finish)) &&
+    !s1_flush && !fromMSHR.valid && !s1_isSoftPrefetch // do not enqueue soft prefetch
+  toWayLookup.bits.vSetIdx           := s1_req_vSetIdx
+  toWayLookup.bits.waymask           := s1_waymasks
+  toWayLookup.bits.ptag              := s1_req_ptags
+  toWayLookup.bits.gpaddr            := s1_req_gpaddr
+  toWayLookup.bits.isForVSnonLeafPTE := s1_req_isForVSnonLeafPTE
+  toWayLookup.bits.meta_codes        := s1_meta_codes
   (0 until PortNumber).foreach { i =>
-    val excpValid = (if (i == 0) true.B else s1_doubleline)  // exception in first line is always valid, in second line is valid iff is doubleline request
+    val excpValid = if (i == 0) true.B
+    else s1_doubleline // exception in first line is always valid, in second line is valid iff is doubleline request
     // Send s1_itlb_exception to WayLookup (instead of s1_exception_out) for better timing. Will check pmp again in mainPipe
     toWayLookup.bits.itlb_exception(i) := Mux(excpValid, s1_itlb_exception(i), ExceptionType.none)
     toWayLookup.bits.itlb_pbmt(i)      := Mux(excpValid, s1_itlb_pbmt(i), Pbmt.pma)
@@ -339,10 +364,18 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
 
   val s1_waymasks_vec = s1_waymasks.map(_.asTypeOf(Vec(nWays, Bool())))
   when(toWayLookup.fire) {
-    assert(PopCount(s1_waymasks_vec(0)) <= 1.U && (PopCount(s1_waymasks_vec(1)) <= 1.U || !s1_doubleline),
+    assert(
+      PopCount(s1_waymasks_vec(0)) <= 1.U && (PopCount(s1_waymasks_vec(1)) <= 1.U || !s1_doubleline),
       "Multiple hit in main pipe, port0:is=%d,ptag=0x%x,vidx=0x%x,vaddr=0x%x port1:is=%d,ptag=0x%x,vidx=0x%x,vaddr=0x%x ",
-      PopCount(s1_waymasks_vec(0)) > 1.U, s1_req_ptags(0), get_idx(s1_req_vaddr(0)), s1_req_vaddr(0),
-      PopCount(s1_waymasks_vec(1)) > 1.U && s1_doubleline, s1_req_ptags(1), get_idx(s1_req_vaddr(1)), s1_req_vaddr(1))
+      PopCount(s1_waymasks_vec(0)) > 1.U,
+      s1_req_ptags(0),
+      get_idx(s1_req_vaddr(0)),
+      s1_req_vaddr(0),
+      PopCount(s1_waymasks_vec(1)) > 1.U && s1_doubleline,
+      s1_req_ptags(1),
+      get_idx(s1_req_vaddr(1)),
+      s1_req_vaddr(1)
+    )
   }
 
   /**
@@ -354,7 +387,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     // if itlb has exception, paddr can be invalid, therefore pmp check can be skipped
     p.valid     := s1_valid // && s1_itlb_exception === ExceptionType.none
     p.bits.addr := s1_req_paddr(i)
-    p.bits.size := 3.U // TODO
+    p.bits.size := 3.U      // TODO
     p.bits.cmd  := TlbCmd.exec
   }
   val s1_pmp_exception = VecInit(fromPMP.map(ExceptionType.fromPMPResp))
@@ -368,7 +401,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   )
 
   // merge pmp mmio and itlb pbmt
-  val s1_mmio = VecInit((s1_pmp_mmio zip s1_itlb_pbmt).map{ case (mmio, pbmt) =>
+  val s1_mmio = VecInit((s1_pmp_mmio zip s1_itlb_pbmt).map { case (mmio, pbmt) =>
     mmio || Pbmt.isUncache(pbmt)
   })
 
@@ -383,12 +416,12 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
       when(s1_valid) {
         when(!itlb_finish) {
           next_state := m_itlbResend
-        }.elsewhen(!toWayLookup.fire) {  // itlb_finish
+        }.elsewhen(!toWayLookup.fire) { // itlb_finish
           next_state := m_enqWay
-        }.elsewhen(!s2_ready) {  // itlb_finish && toWayLookup.fire
+        }.elsewhen(!s2_ready) { // itlb_finish && toWayLookup.fire
           next_state := m_enterS2
         } // .otherwise { next_state := m_idle }
-      } // .otherwise { next_state := m_idle }  // !s1_valid
+      }   // .otherwise { next_state := m_idle }  // !s1_valid
     }
     is(m_itlbResend) {
       when(itlb_finish) {
@@ -406,9 +439,9 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     }
     is(m_enqWay) {
       when(toWayLookup.fire || s1_isSoftPrefetch) {
-        when (!s2_ready) {
+        when(!s2_ready) {
           next_state := m_enterS2
-        }.otherwise {  // s2_ready
+        }.otherwise { // s2_ready
           next_state := m_idle
         }
       } // .otherwise { next_state := m_enqWay }
@@ -426,11 +459,11 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
 
   /** Stage 1 control */
   from_bpu_s1_flush := s1_valid && !s1_isSoftPrefetch && io.flushFromBpu.shouldFlushByStage3(s1_req_ftqIdx)
-  s1_flush := io.flush || from_bpu_s1_flush
+  s1_flush          := io.flush || from_bpu_s1_flush
 
-  s1_ready      := next_state === m_idle
-  s1_fire       := (next_state === m_idle) && s1_valid && !s1_flush  // used to clear s1_valid & itlb_valid_latch
-  val s1_real_fire = s1_fire && io.csr_pf_enable                     // real "s1 fire" that s1 enters s2
+  s1_ready := next_state === m_idle
+  s1_fire  := (next_state === m_idle) && s1_valid && !s1_flush // used to clear s1_valid & itlb_valid_latch
+  val s1_real_fire = s1_fire && io.csr_pf_enable // real "s1 fire" that s1 enters s2
 
   /**
     ******************************************************************************
@@ -439,20 +472,22 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     * - 2. send req to missUnit
     ******************************************************************************
     */
-  val s2_valid  = generatePipeControl(lastFire = s1_real_fire, thisFire = s2_fire, thisFlush = s2_flush, lastFlush = false.B)
+  val s2_valid =
+    generatePipeControl(lastFire = s1_real_fire, thisFire = s2_fire, thisFlush = s2_flush, lastFlush = false.B)
 
-  val s2_req_vaddr    = RegEnable(s1_req_vaddr,     0.U.asTypeOf(s1_req_vaddr),     s1_real_fire)
+  val s2_req_vaddr      = RegEnable(s1_req_vaddr, 0.U.asTypeOf(s1_req_vaddr), s1_real_fire)
   val s2_isSoftPrefetch = RegEnable(s1_isSoftPrefetch, 0.U.asTypeOf(s1_isSoftPrefetch), s1_real_fire)
-  val s2_doubleline   = RegEnable(s1_doubleline,    0.U.asTypeOf(s1_doubleline),    s1_real_fire)
-  val s2_req_paddr    = RegEnable(s1_req_paddr,     0.U.asTypeOf(s1_req_paddr),     s1_real_fire)
-  val s2_exception    = RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_real_fire)  // includes itlb/pmp exception
+  val s2_doubleline     = RegEnable(s1_doubleline, 0.U.asTypeOf(s1_doubleline), s1_real_fire)
+  val s2_req_paddr      = RegEnable(s1_req_paddr, 0.U.asTypeOf(s1_req_paddr), s1_real_fire)
+  val s2_exception =
+    RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_real_fire) // includes itlb/pmp exception
 //  val s2_exception_in = RegEnable(s1_exception_out, 0.U.asTypeOf(s1_exception_out), s1_real_fire)  // disabled for timing consideration
-  val s2_mmio         = RegEnable(s1_mmio,          0.U.asTypeOf(s1_mmio),          s1_real_fire)
-  val s2_waymasks     = RegEnable(s1_waymasks,      0.U.asTypeOf(s1_waymasks),      s1_real_fire)
+  val s2_mmio     = RegEnable(s1_mmio, 0.U.asTypeOf(s1_mmio), s1_real_fire)
+  val s2_waymasks = RegEnable(s1_waymasks, 0.U.asTypeOf(s1_waymasks), s1_real_fire)
 //  val s2_meta_codes   = RegEnable(s1_meta_codes,    0.U.asTypeOf(s1_meta_codes),    s1_real_fire)  // disabled for timing consideration
 
-  val s2_req_vSetIdx  = s2_req_vaddr.map(get_idx)
-  val s2_req_ptags    = s2_req_paddr.map(get_phy_tag)
+  val s2_req_vSetIdx = s2_req_vaddr.map(get_idx)
+  val s2_req_ptags   = s2_req_paddr.map(get_phy_tag)
 
   // disabled for timing consideration
 //  // do metaArray ECC check
@@ -481,22 +516,22 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
    */
   val s2_MSHR_match = VecInit((0 until PortNumber).map(i =>
     (s2_req_vSetIdx(i) === fromMSHR.bits.vSetIdx) &&
-    (s2_req_ptags(i) === getPhyTagFromBlk(fromMSHR.bits.blkPaddr)) &&
-    s2_valid && fromMSHR.valid && !fromMSHR.bits.corrupt
+      (s2_req_ptags(i) === getPhyTagFromBlk(fromMSHR.bits.blkPaddr)) &&
+      s2_valid && fromMSHR.valid && !fromMSHR.bits.corrupt
   ))
   val s2_MSHR_hits = (0 until PortNumber).map(i => ValidHoldBypass(s2_MSHR_match(i), s2_fire || s2_flush))
 
   val s2_SRAM_hits = s2_waymasks.map(_.orR)
-  val s2_hits = VecInit((0 until PortNumber).map(i => s2_MSHR_hits(i) || s2_SRAM_hits(i)))
+  val s2_hits      = VecInit((0 until PortNumber).map(i => s2_MSHR_hits(i) || s2_SRAM_hits(i)))
 
   /* s2_exception includes itlb pf/gpf/af, pmp af and meta corruption (af), neither of which should be prefetched
    * mmio should not be prefetched
    * also, if previous has exception, latter port should also not be prefetched
    */
   val s2_miss = VecInit((0 until PortNumber).map { i =>
-    !s2_hits(i) && (if (i==0) true.B else s2_doubleline) &&
-      s2_exception.take(i+1).map(_ === ExceptionType.none).reduce(_&&_) &&
-      s2_mmio.take(i+1).map(!_).reduce(_&&_)
+    !s2_hits(i) && (if (i == 0) true.B else s2_doubleline) &&
+    s2_exception.take(i + 1).map(_ === ExceptionType.none).reduce(_ && _) &&
+    s2_mmio.take(i + 1).map(!_).reduce(_ && _)
   })
 
   /**
@@ -508,7 +543,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
 
   // To avoid sending duplicate requests.
   val has_send = RegInit(VecInit(Seq.fill(PortNumber)(false.B)))
-  (0 until PortNumber).foreach{ i =>
+  (0 until PortNumber).foreach { i =>
     when(s1_real_fire) {
       has_send(i) := false.B
     }.elsewhen(toMSHRArbiter.io.in(i).fire) {
@@ -516,10 +551,10 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
     }
   }
 
-  (0 until PortNumber).map{ i =>
-    toMSHRArbiter.io.in(i).valid          := s2_valid && s2_miss(i) && !has_send(i)
-    toMSHRArbiter.io.in(i).bits.blkPaddr  := getBlkAddr(s2_req_paddr(i))
-    toMSHRArbiter.io.in(i).bits.vSetIdx   := s2_req_vSetIdx(i)
+  (0 until PortNumber).map { i =>
+    toMSHRArbiter.io.in(i).valid         := s2_valid && s2_miss(i) && !has_send(i)
+    toMSHRArbiter.io.in(i).bits.blkPaddr := getBlkAddr(s2_req_paddr(i))
+    toMSHRArbiter.io.in(i).bits.vSetIdx  := s2_req_vSetIdx(i)
   }
 
   toMSHR <> toMSHRArbiter.io.out
@@ -528,9 +563,9 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
 
   // toMSHRArbiter.io.in(i).fire is not used here for timing consideration
   // val s2_finish  = (0 until PortNumber).map(i => has_send(i) || !s2_miss(i) || toMSHRArbiter.io.in(i).fire).reduce(_&&_)
-  val s2_finish  = (0 until PortNumber).map(i => has_send(i) || !s2_miss(i)).reduce(_&&_)
-  s2_ready      := s2_finish || !s2_valid
-  s2_fire       := s2_valid && s2_finish && !s2_flush
+  val s2_finish = (0 until PortNumber).map(i => has_send(i) || !s2_miss(i)).reduce(_ && _)
+  s2_ready := s2_finish || !s2_valid
+  s2_fire  := s2_valid && s2_finish && !s2_flush
 
   /** PerfAccumulate */
   // the number of bpu flush
@@ -545,6 +580,7 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   XSPerfAccumulate("prefetch_req_send_hw", toMSHR.fire && !s2_isSoftPrefetch)
   XSPerfAccumulate("prefetch_req_send_sw", toMSHR.fire && s2_isSoftPrefetch)
   XSPerfAccumulate("to_missUnit_stall", toMSHR.valid && !toMSHR.ready)
+
   /**
     * Count the number of requests that are filtered for various reasons.
     * The number of prefetch discard in Performance Accumulator may be
@@ -561,4 +597,4 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
   // XSPerfAccumulate("fdip_prefetch_discard_by_pmp",         p2_discard && p2_pmp_except)
   // // discard prefetch request by hit mainPipe info
   // // XSPerfAccumulate("fdip_prefetch_discard_by_mainPipe",    p2_discard && p2_mainPipe_hit)
-}
\ No newline at end of file
+}
diff --git a/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala b/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
index 0aca87c1038..0ff95786c4a 100644
--- a/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
+++ b/src/main/scala/xiangshan/frontend/icache/InstrUncache.scala
@@ -18,31 +18,38 @@ package xiangshan.frontend.icache
 
 import chisel3._
 import chisel3.util._
-import utils._
-import utility._
+import freechips.rocketchip.diplomacy.IdRange
+import freechips.rocketchip.diplomacy.LazyModule
+import freechips.rocketchip.diplomacy.LazyModuleImp
+import freechips.rocketchip.diplomacy.TransferSizes
+import freechips.rocketchip.tilelink.TLArbiter
+import freechips.rocketchip.tilelink.TLBundleA
+import freechips.rocketchip.tilelink.TLBundleD
+import freechips.rocketchip.tilelink.TLClientNode
+import freechips.rocketchip.tilelink.TLEdgeOut
+import freechips.rocketchip.tilelink.TLMasterParameters
+import freechips.rocketchip.tilelink.TLMasterPortParameters
 import org.chipsalliance.cde.config.Parameters
-import freechips.rocketchip.diplomacy.{IdRange, LazyModule, LazyModuleImp, TransferSizes}
-import freechips.rocketchip.tilelink.{TLArbiter, TLBundleA, TLBundleD, TLClientNode, TLEdgeOut, TLMasterParameters, TLMasterPortParameters}
+import utility._
+import utils._
 import xiangshan._
 import xiangshan.frontend._
 
-class InsUncacheReq(implicit p: Parameters) extends ICacheBundle
-{
-    val addr = UInt(PAddrBits.W)
+class InsUncacheReq(implicit p: Parameters) extends ICacheBundle {
+  val addr = UInt(PAddrBits.W)
 }
 
-class InsUncacheResp(implicit p: Parameters) extends ICacheBundle
-{
+class InsUncacheResp(implicit p: Parameters) extends ICacheBundle {
   val data = UInt(maxInstrLen.W)
 }
 
 // One miss entry deals with one mmio request
-class InstrMMIOEntry(edge: TLEdgeOut)(implicit p: Parameters) extends XSModule with HasICacheParameters with HasIFUConst
-{
+class InstrMMIOEntry(edge: TLEdgeOut)(implicit p: Parameters) extends XSModule with HasICacheParameters
+    with HasIFUConst {
   val io = IO(new Bundle {
     val id = Input(UInt(log2Up(cacheParams.nMMIOs).W))
     // client requests
-    val req = Flipped(DecoupledIO(new InsUncacheReq))
+    val req  = Flipped(DecoupledIO(new InsUncacheReq))
     val resp = DecoupledIO(new InsUncacheResp)
 
     val mmio_acquire = DecoupledIO(new TLBundleA(edge.bundle))
@@ -51,92 +58,92 @@ class InstrMMIOEntry(edge: TLEdgeOut)(implicit p: Parameters) extends XSModule w
     val flush = Input(Bool())
   })
 
-
   val s_invalid :: s_refill_req :: s_refill_resp :: s_send_resp :: Nil = Enum(4)
 
   val state = RegInit(s_invalid)
 
-  val req       = Reg(new InsUncacheReq )
+  val req         = Reg(new InsUncacheReq)
   val respDataReg = Reg(UInt(mmioBusWidth.W))
 
   // assign default values to output signals
-  io.req.ready           := false.B
-  io.resp.valid          := false.B
-  io.resp.bits           := DontCare
+  io.req.ready  := false.B
+  io.resp.valid := false.B
+  io.resp.bits  := DontCare
 
-  io.mmio_acquire.valid   := false.B
-  io.mmio_acquire.bits    := DontCare
+  io.mmio_acquire.valid := false.B
+  io.mmio_acquire.bits  := DontCare
 
-  io.mmio_grant.ready     := false.B
+  io.mmio_grant.ready := false.B
 
   val needFlush = RegInit(false.B)
 
-  when(io.flush && (state =/= s_invalid) && (state =/= s_send_resp)){ needFlush := true.B }
-  .elsewhen((state=== s_send_resp) && needFlush){ needFlush := false.B }
+  when(io.flush && (state =/= s_invalid) && (state =/= s_send_resp))(needFlush := true.B)
+    .elsewhen((state === s_send_resp) && needFlush)(needFlush := false.B)
 
   // --------------------------------------------
   // s_invalid: receive requests
-  when (state === s_invalid) {
+  when(state === s_invalid) {
     io.req.ready := true.B
 
-    when (io.req.fire) {
+    when(io.req.fire) {
       req   := io.req.bits
       state := s_refill_req
     }
   }
 
-
-  when (state === s_refill_req) {
+  when(state === s_refill_req) {
     val address_aligned = req.addr(req.addr.getWidth - 1, log2Ceil(mmioBusBytes))
     io.mmio_acquire.valid := true.B
-    io.mmio_acquire.bits  := edge.Get(
-          fromSource      = io.id,
-          toAddress       = Cat(address_aligned, 0.U(log2Ceil(mmioBusBytes).W)),
-          lgSize          = log2Ceil(mmioBusBytes).U
-        )._2
+    io.mmio_acquire.bits := edge.Get(
+      fromSource = io.id,
+      toAddress = Cat(address_aligned, 0.U(log2Ceil(mmioBusBytes).W)),
+      lgSize = log2Ceil(mmioBusBytes).U
+    )._2
 
-    when (io.mmio_acquire.fire) {
+    when(io.mmio_acquire.fire) {
       state := s_refill_resp
     }
   }
 
   val (_, _, refill_done, _) = edge.addr_inc(io.mmio_grant)
 
-  when (state === s_refill_resp) {
+  when(state === s_refill_resp) {
     io.mmio_grant.ready := true.B
 
-    when (io.mmio_grant.fire) {
+    when(io.mmio_grant.fire) {
       respDataReg := io.mmio_grant.bits.data
-      state := s_send_resp
+      state       := s_send_resp
     }
   }
 
   def getDataFromBus(pc: UInt) = {
     val respData = Wire(UInt(maxInstrLen.W))
-    respData := Mux(pc(2,1) === "b00".U, respDataReg(31,0),
-        Mux(pc(2,1) === "b01".U, respDataReg(47,16),
-          Mux(pc(2,1) === "b10".U, respDataReg(63,32),
-            Cat(0.U, respDataReg(63,48))
-          )
-        )
+    respData := Mux(
+      pc(2, 1) === "b00".U,
+      respDataReg(31, 0),
+      Mux(
+        pc(2, 1) === "b01".U,
+        respDataReg(47, 16),
+        Mux(pc(2, 1) === "b10".U, respDataReg(63, 32), Cat(0.U, respDataReg(63, 48)))
       )
+    )
     respData
   }
 
-  when (state === s_send_resp) {
+  when(state === s_send_resp) {
     io.resp.valid     := !needFlush
-    io.resp.bits.data :=  getDataFromBus(req.addr)
+    io.resp.bits.data := getDataFromBus(req.addr)
     // meta data should go with the response
-    when (io.resp.fire || needFlush) {
+    when(io.resp.fire || needFlush) {
       state := s_invalid
     }
   }
 }
 
 class InstrUncacheIO(implicit p: Parameters) extends ICacheBundle {
-    val req = Flipped(DecoupledIO(new InsUncacheReq ))
-    val resp = DecoupledIO(new InsUncacheResp)
-    val flush = Input(Bool())
+  val req   = Flipped(DecoupledIO(new InsUncacheReq))
+  val resp  = DecoupledIO(new InsUncacheResp)
+  val flush = Input(Bool())
 }
 
 class InstrUncache()(implicit p: Parameters) extends LazyModule with HasICacheParameters {
@@ -155,23 +162,22 @@ class InstrUncache()(implicit p: Parameters) extends LazyModule with HasICachePa
 }
 
 class InstrUncacheImp(outer: InstrUncache)
-  extends LazyModuleImp(outer)
+    extends LazyModuleImp(outer)
     with HasICacheParameters
-    with HasTLDump
-{
+    with HasTLDump {
   val io = IO(new InstrUncacheIO)
 
   val (bus, edge) = outer.clientNode.out.head
 
   val resp_arb = Module(new Arbiter(new InsUncacheResp, cacheParams.nMMIOs))
 
-  val req  = io.req
-  val resp = io.resp
+  val req          = io.req
+  val resp         = io.resp
   val mmio_acquire = bus.a
   val mmio_grant   = bus.d
 
   val entry_alloc_idx = Wire(UInt())
-  val req_ready = WireInit(false.B)
+  val req_ready       = WireInit(false.B)
 
   // assign default values to output signals
   bus.b.ready := false.B
@@ -184,13 +190,13 @@ class InstrUncacheImp(outer: InstrUncache)
   val entries = (0 until cacheParams.nMMIOs) map { i =>
     val entry = Module(new InstrMMIOEntry(edge))
 
-    entry.io.id := i.U(log2Up(cacheParams.nMMIOs).W)
+    entry.io.id    := i.U(log2Up(cacheParams.nMMIOs).W)
     entry.io.flush := io.flush
 
     // entry req
     entry.io.req.valid := (i.U === entry_alloc_idx) && req.valid
     entry.io.req.bits  := req.bits
-    when (i.U === entry_alloc_idx) {
+    when(i.U === entry_alloc_idx) {
       req_ready := entry.io.req.ready
     }
 
@@ -199,16 +205,16 @@ class InstrUncacheImp(outer: InstrUncache)
 
     entry.io.mmio_grant.valid := false.B
     entry.io.mmio_grant.bits  := DontCare
-    when (mmio_grant.bits.source === i.U) {
+    when(mmio_grant.bits.source === i.U) {
       entry.io.mmio_grant <> mmio_grant
     }
     entry
   }
 
-  entry_alloc_idx    := PriorityEncoder(entries.map(m=>m.io.req.ready))
+  entry_alloc_idx := PriorityEncoder(entries.map(m => m.io.req.ready))
 
-  req.ready  := req_ready
-  resp          <> resp_arb.io.out
+  req.ready := req_ready
+  resp <> resp_arb.io.out
   TLArbiter.lowestFromSeq(edge, mmio_acquire, entries.map(_.io.mmio_acquire))
 
 }
diff --git a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
index 3664e981526..94b7b050872 100644
--- a/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
+++ b/src/main/scala/xiangshan/frontend/icache/WayLookup.scala
@@ -16,12 +16,12 @@
 
 package xiangshan.frontend.icache
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
 import utility._
-import xiangshan.frontend.ExceptionType
 import xiangshan.cache.mmu.Pbmt
+import xiangshan.frontend.ExceptionType
 
 /* WayLookupEntry is for internal storage, while WayLookupInfo is for interface
  * Notes:
@@ -30,17 +30,17 @@ import xiangshan.cache.mmu.Pbmt
  *      to save area, we separate those signals from WayLookupEntry and store only once.
  */
 class WayLookupEntry(implicit p: Parameters) extends ICacheBundle {
-  val vSetIdx        : Vec[UInt] = Vec(PortNumber, UInt(idxBits.W))
-  val waymask        : Vec[UInt] = Vec(PortNumber, UInt(nWays.W))
-  val ptag           : Vec[UInt] = Vec(PortNumber, UInt(tagBits.W))
-  val itlb_exception : Vec[UInt] = Vec(PortNumber, UInt(ExceptionType.width.W))
-  val itlb_pbmt      : Vec[UInt] = Vec(PortNumber, UInt(Pbmt.width.W))
-  val meta_codes     : Vec[UInt] = Vec(PortNumber, UInt(ICacheMetaCodeBits.W))
+  val vSetIdx:        Vec[UInt] = Vec(PortNumber, UInt(idxBits.W))
+  val waymask:        Vec[UInt] = Vec(PortNumber, UInt(nWays.W))
+  val ptag:           Vec[UInt] = Vec(PortNumber, UInt(tagBits.W))
+  val itlb_exception: Vec[UInt] = Vec(PortNumber, UInt(ExceptionType.width.W))
+  val itlb_pbmt:      Vec[UInt] = Vec(PortNumber, UInt(Pbmt.width.W))
+  val meta_codes:     Vec[UInt] = Vec(PortNumber, UInt(ICacheMetaCodeBits.W))
 }
 
 class WayLookupGPFEntry(implicit p: Parameters) extends ICacheBundle {
-  val gpaddr         : UInt      = UInt(GPAddrBits.W)
-  val isForVSnonLeafPTE        : Bool      = Bool()
+  val gpaddr:            UInt = UInt(GPAddrBits.W)
+  val isForVSnonLeafPTE: Bool = Bool()
 }
 
 class WayLookupInfo(implicit p: Parameters) extends ICacheBundle {
@@ -48,21 +48,21 @@ class WayLookupInfo(implicit p: Parameters) extends ICacheBundle {
   val gpf   = new WayLookupGPFEntry
 
   // for compatibility
-  def vSetIdx        : Vec[UInt] = entry.vSetIdx
-  def waymask        : Vec[UInt] = entry.waymask
-  def ptag           : Vec[UInt] = entry.ptag
-  def itlb_exception : Vec[UInt] = entry.itlb_exception
-  def itlb_pbmt      : Vec[UInt] = entry.itlb_pbmt
-  def meta_codes     : Vec[UInt] = entry.meta_codes
-  def gpaddr         : UInt      = gpf.gpaddr
-  def isForVSnonLeafPTE        : Bool      = gpf.isForVSnonLeafPTE
+  def vSetIdx:           Vec[UInt] = entry.vSetIdx
+  def waymask:           Vec[UInt] = entry.waymask
+  def ptag:              Vec[UInt] = entry.ptag
+  def itlb_exception:    Vec[UInt] = entry.itlb_exception
+  def itlb_pbmt:         Vec[UInt] = entry.itlb_pbmt
+  def meta_codes:        Vec[UInt] = entry.meta_codes
+  def gpaddr:            UInt      = gpf.gpaddr
+  def isForVSnonLeafPTE: Bool      = gpf.isForVSnonLeafPTE
 }
 
 class WayLookupInterface(implicit p: Parameters) extends ICacheBundle {
-  val flush   = Input(Bool())
-  val read    = DecoupledIO(new WayLookupInfo)
-  val write   = Flipped(DecoupledIO(new WayLookupInfo))
-  val update  = Flipped(ValidIO(new ICacheMissResp))
+  val flush  = Input(Bool())
+  val read   = DecoupledIO(new WayLookupInfo)
+  val write  = Flipped(DecoupledIO(new WayLookupInfo))
+  val update = Flipped(ValidIO(new ICacheMissResp))
 }
 
 class WayLookup(implicit p: Parameters) extends ICacheModule {
@@ -72,7 +72,7 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
   private object WayLookupPtr {
     def apply(f: Bool, v: UInt)(implicit p: Parameters): WayLookupPtr = {
       val ptr = Wire(new WayLookupPtr)
-      ptr.flag := f
+      ptr.flag  := f
       ptr.value := v
       ptr
     }
@@ -115,12 +115,12 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
     ******************************************************************************
     */
   private val hits = Wire(Vec(nWayLookupSize, Bool()))
-  entries.zip(hits).foreach{ case(entry, hit) =>
+  entries.zip(hits).foreach { case (entry, hit) =>
     val hit_vec = Wire(Vec(PortNumber, Bool()))
     (0 until PortNumber).foreach { i =>
       val vset_same = (io.update.bits.vSetIdx === entry.vSetIdx(i)) && !io.update.bits.corrupt && io.update.valid
       val ptag_same = getPhyTagFromBlk(io.update.bits.blkPaddr) === entry.ptag(i)
-      val way_same = io.update.bits.waymask === entry.waymask(i)
+      val way_same  = io.update.bits.waymask === entry.waymask(i)
       when(vset_same) {
         when(ptag_same) {
           // miss -> hit
@@ -136,7 +136,7 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
       }
       hit_vec(i) := vset_same && (ptag_same || way_same)
     }
-    hit := hit_vec.reduce(_||_)
+    hit := hit_vec.reduce(_ || _)
   }
 
   /**
@@ -147,17 +147,17 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
   // if the entry is empty, but there is a valid write, we can bypass it to read port (maybe timing critical)
   private val can_bypass = empty && io.write.valid
   io.read.valid := !empty || io.write.valid
-  when (can_bypass) {
+  when(can_bypass) {
     io.read.bits := io.write.bits
-  }.otherwise {  // can't bypass
+  }.otherwise { // can't bypass
     io.read.bits.entry := entries(readPtr.value)
-    when(gpf_hit) {  // ptr match && entry valid
+    when(gpf_hit) { // ptr match && entry valid
       io.read.bits.gpf := gpf_entry.bits
       // also clear gpf_entry.valid when it's read, note this will be override by write (L175)
-      when (io.read.fire) {
+      when(io.read.fire) {
         gpf_entry.valid := false.B
       }
-    }.otherwise {  // gpf not hit
+    }.otherwise { // gpf not hit
       io.read.bits.gpf := 0.U.asTypeOf(new WayLookupGPFEntry)
     }
   }
@@ -172,12 +172,12 @@ class WayLookup(implicit p: Parameters) extends ICacheModule {
   io.write.ready := !full && !gpf_stall
   when(io.write.fire) {
     entries(writePtr.value) := io.write.bits.entry
-    when(io.write.bits.itlb_exception.map(_ === ExceptionType.gpf).reduce(_||_)) {
+    when(io.write.bits.itlb_exception.map(_ === ExceptionType.gpf).reduce(_ || _)) {
       // if gpf_entry is bypassed, we don't need to save it
       // note this will override the read (L156)
       gpf_entry.valid := !(can_bypass && io.read.fire)
       gpf_entry.bits  := io.write.bits.gpf
-      gpfPtr := writePtr
+      gpfPtr          := writePtr
     }
   }
 }
diff --git a/src/main/scala/xiangshan/frontend/newRAS.scala b/src/main/scala/xiangshan/frontend/newRAS.scala
index 3348cbd797a..66fa5eb2723 100644
--- a/src/main/scala/xiangshan/frontend/newRAS.scala
+++ b/src/main/scala/xiangshan/frontend/newRAS.scala
@@ -16,73 +16,69 @@
 ***************************************************************************************/
 package xiangshan.frontend
 
-import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import utils._
+import org.chipsalliance.cde.config.Parameters
 import utility._
+import utils._
 import xiangshan._
 import xiangshan.frontend._
 
 class RASEntry()(implicit p: Parameters) extends XSBundle {
-    val retAddr = UInt(VAddrBits.W)
-    val ctr = UInt(RasCtrSize.W) // layer of nested call functions
-    def =/=(that: RASEntry) = this.retAddr =/= that.retAddr || this.ctr =/= that.ctr
+  val retAddr = UInt(VAddrBits.W)
+  val ctr     = UInt(RasCtrSize.W) // layer of nested call functions
+  def =/=(that: RASEntry) = this.retAddr =/= that.retAddr || this.ctr =/= that.ctr
 }
 
-class RASPtr(implicit p: Parameters) extends CircularQueuePtr[RASPtr](
-  p => p(XSCoreParamsKey).RasSpecSize
-){
-}
+class RASPtr(implicit p: Parameters) extends CircularQueuePtr[RASPtr](p => p(XSCoreParamsKey).RasSpecSize) {}
 
 object RASPtr {
   def apply(f: Bool, v: UInt)(implicit p: Parameters): RASPtr = {
     val ptr = Wire(new RASPtr)
-    ptr.flag := f
+    ptr.flag  := f
     ptr.value := v
     ptr
   }
-  def inverse(ptr: RASPtr)(implicit p: Parameters): RASPtr = {
+  def inverse(ptr: RASPtr)(implicit p: Parameters): RASPtr =
     apply(!ptr.flag, ptr.value)
-  }
 }
 
 class RASInternalMeta(implicit p: Parameters) extends XSBundle {
-  val ssp = UInt(log2Up(RasSize).W)
+  val ssp  = UInt(log2Up(RasSize).W)
   val sctr = UInt(RasCtrSize.W)
   val TOSW = new RASPtr
   val TOSR = new RASPtr
-  val NOS = new RASPtr
+  val NOS  = new RASPtr
 }
 
 object RASInternalMeta {
-  def apply(ssp: UInt, sctr: UInt, TOSW: RASPtr, TOSR: RASPtr, NOS: RASPtr)(implicit p: Parameters):RASInternalMeta = {
+  def apply(ssp: UInt, sctr: UInt, TOSW: RASPtr, TOSR: RASPtr, NOS: RASPtr)(implicit p: Parameters): RASInternalMeta = {
     val e = Wire(new RASInternalMeta)
-    e.ssp := ssp
+    e.ssp  := ssp
     e.TOSW := TOSW
     e.TOSR := TOSR
-    e.NOS := NOS
+    e.NOS  := NOS
     e
   }
 }
 
 class RASMeta(implicit p: Parameters) extends XSBundle {
-  val ssp = UInt(log2Up(RasSize).W)
+  val ssp  = UInt(log2Up(RasSize).W)
   val TOSW = new RASPtr
 }
 
 object RASMeta {
-  def apply(ssp: UInt, sctr: UInt, TOSW: RASPtr, TOSR: RASPtr, NOS: RASPtr)(implicit p: Parameters):RASMeta = {
+  def apply(ssp: UInt, sctr: UInt, TOSW: RASPtr, TOSR: RASPtr, NOS: RASPtr)(implicit p: Parameters): RASMeta = {
     val e = Wire(new RASMeta)
-    e.ssp := ssp
+    e.ssp  := ssp
     e.TOSW := TOSW
     e
   }
 }
 
 class RASDebug(implicit p: Parameters) extends XSBundle {
-  val spec_queue = Output(Vec(RasSpecSize, new RASEntry))
-  val spec_nos = Output(Vec(RasSpecSize, new RASPtr))
+  val spec_queue   = Output(Vec(RasSpecSize, new RASEntry))
+  val spec_nos     = Output(Vec(RasSpecSize, new RASPtr))
   val commit_stack = Output(Vec(RasSize, new RASEntry))
 }
 
@@ -93,53 +89,52 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     def apply(retAddr: UInt, ctr: UInt): RASEntry = {
       val e = Wire(new RASEntry)
       e.retAddr := retAddr
-      e.ctr := ctr
+      e.ctr     := ctr
       e
     }
   }
 
-
   class RASStack(rasSize: Int, rasSpecSize: Int) extends XSModule with HasCircularQueuePtrHelper {
     val io = IO(new Bundle {
       val spec_push_valid = Input(Bool())
-      val spec_pop_valid = Input(Bool())
-      val spec_push_addr = Input(UInt(VAddrBits.W))
+      val spec_pop_valid  = Input(Bool())
+      val spec_push_addr  = Input(UInt(VAddrBits.W))
       // for write bypass between s2 and s3
 
-      val s2_fire = Input(Bool())
-      val s3_fire = Input(Bool())
-      val s3_cancel = Input(Bool())
-      val s3_meta = Input(new RASInternalMeta)
-      val s3_missed_pop = Input(Bool())
+      val s2_fire        = Input(Bool())
+      val s3_fire        = Input(Bool())
+      val s3_cancel      = Input(Bool())
+      val s3_meta        = Input(new RASInternalMeta)
+      val s3_missed_pop  = Input(Bool())
       val s3_missed_push = Input(Bool())
-      val s3_pushAddr = Input(UInt(VAddrBits.W))
-      val spec_pop_addr = Output(UInt(VAddrBits.W))
+      val s3_pushAddr    = Input(UInt(VAddrBits.W))
+      val spec_pop_addr  = Output(UInt(VAddrBits.W))
 
       val commit_valid      = Input(Bool())
       val commit_push_valid = Input(Bool())
-      val commit_pop_valid = Input(Bool())
-      val commit_push_addr = Input(UInt(VAddrBits.W))
-      val commit_meta_TOSW = Input(new RASPtr)
+      val commit_pop_valid  = Input(Bool())
+      val commit_push_addr  = Input(UInt(VAddrBits.W))
+      val commit_meta_TOSW  = Input(new RASPtr)
       // for debug purpose only
       val commit_meta_ssp = Input(UInt(log2Up(RasSize).W))
 
-      val redirect_valid = Input(Bool())
-      val redirect_isCall = Input(Bool())
-      val redirect_isRet = Input(Bool())
-      val redirect_meta_ssp = Input(UInt(log2Up(RasSize).W))
+      val redirect_valid     = Input(Bool())
+      val redirect_isCall    = Input(Bool())
+      val redirect_isRet     = Input(Bool())
+      val redirect_meta_ssp  = Input(UInt(log2Up(RasSize).W))
       val redirect_meta_sctr = Input(UInt(RasCtrSize.W))
       val redirect_meta_TOSW = Input(new RASPtr)
       val redirect_meta_TOSR = Input(new RASPtr)
-      val redirect_meta_NOS = Input(new RASPtr)
-      val redirect_callAddr = Input(UInt(VAddrBits.W))
+      val redirect_meta_NOS  = Input(new RASPtr)
+      val redirect_callAddr  = Input(UInt(VAddrBits.W))
 
-      val ssp = Output(UInt(log2Up(RasSize).W))
+      val ssp  = Output(UInt(log2Up(RasSize).W))
       val sctr = Output(UInt(RasCtrSize.W))
-      val nsp = Output(UInt(log2Up(RasSize).W))
+      val nsp  = Output(UInt(log2Up(RasSize).W))
       val TOSR = Output(new RASPtr)
       val TOSW = Output(new RASPtr)
-      val NOS = Output(new RASPtr)
-      val BOS = Output(new RASPtr)
+      val NOS  = Output(new RASPtr)
+      val BOS  = Output(new RASPtr)
 
       val spec_near_overflow = Output(Bool())
 
@@ -147,8 +142,8 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     })
 
     val commit_stack = RegInit(VecInit(Seq.fill(RasSize)(RASEntry(0.U, 0.U))))
-    val spec_queue = RegInit(VecInit(Seq.fill(rasSpecSize)(RASEntry(0.U, 0.U))))
-    val spec_nos = RegInit(VecInit(Seq.fill(rasSpecSize)(RASPtr(false.B, 0.U))))
+    val spec_queue   = RegInit(VecInit(Seq.fill(rasSpecSize)(RASEntry(0.U, 0.U))))
+    val spec_nos     = RegInit(VecInit(Seq.fill(rasSpecSize)(RASPtr(false.B, 0.U))))
 
     val nsp = RegInit(0.U(log2Up(rasSize).W))
     val ssp = RegInit(0.U(log2Up(rasSize).W))
@@ -156,35 +151,34 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     val sctr = RegInit(0.U(RasCtrSize.W))
     val TOSR = RegInit(RASPtr(true.B, (RasSpecSize - 1).U))
     val TOSW = RegInit(RASPtr(false.B, 0.U))
-    val BOS = RegInit(RASPtr(false.B, 0.U))
+    val BOS  = RegInit(RASPtr(false.B, 0.U))
 
     val spec_near_overflowed = RegInit(false.B)
 
     val writeBypassEntry = Reg(new RASEntry)
-    val writeBypassNos = Reg(new RASPtr)
+    val writeBypassNos   = Reg(new RASPtr)
 
-    val writeBypassValid = RegInit(0.B)
+    val writeBypassValid     = RegInit(0.B)
     val writeBypassValidWire = Wire(Bool())
 
     def TOSRinRange(currentTOSR: RASPtr, currentTOSW: RASPtr) = {
       val inflightValid = WireInit(false.B)
       // if in range, TOSR should be no younger than BOS and strictly younger than TOSW
-      when (!isBefore(currentTOSR, BOS) && isBefore(currentTOSR, currentTOSW)) {
+      when(!isBefore(currentTOSR, BOS) && isBefore(currentTOSR, currentTOSW)) {
         inflightValid := true.B
       }
       inflightValid
     }
 
-    def getCommitTop(currentSsp: UInt) = {
+    def getCommitTop(currentSsp: UInt) =
       commit_stack(currentSsp)
-    }
 
-    def getTopNos(currentTOSR: RASPtr, allowBypass: Boolean):RASPtr = {
+    def getTopNos(currentTOSR: RASPtr, allowBypass: Boolean): RASPtr = {
       val ret = Wire(new RASPtr)
-      if (allowBypass){
-        when (writeBypassValid) {
+      if (allowBypass) {
+        when(writeBypassValid) {
           ret := writeBypassNos
-        } .otherwise {
+        }.otherwise {
           ret := spec_nos(TOSR.value)
         }
       } else {
@@ -193,20 +187,26 @@ class RAS(implicit p: Parameters) extends BasePredictor {
       ret
     }
 
-    def getTop(currentSsp: UInt, currentSctr: UInt, currentTOSR: RASPtr, currentTOSW: RASPtr, allowBypass: Boolean):RASEntry = {
+    def getTop(
+        currentSsp:  UInt,
+        currentSctr: UInt,
+        currentTOSR: RASPtr,
+        currentTOSW: RASPtr,
+        allowBypass: Boolean
+    ): RASEntry = {
       val ret = Wire(new RASEntry)
       if (allowBypass) {
-        when (writeBypassValid) {
+        when(writeBypassValid) {
           ret := writeBypassEntry
-        } .elsewhen (TOSRinRange(currentTOSR, currentTOSW)) {
+        }.elsewhen(TOSRinRange(currentTOSR, currentTOSW)) {
           ret := spec_queue(currentTOSR.value)
-        } .otherwise {
+        }.otherwise {
           ret := getCommitTop(currentSsp)
         }
       } else {
-        when (TOSRinRange(currentTOSR, currentTOSW)) {
+        when(TOSRinRange(currentTOSR, currentTOSW)) {
           ret := spec_queue(currentTOSR.value)
-        } .otherwise {
+        }.otherwise {
           ret := getCommitTop(currentSsp)
         }
       }
@@ -223,139 +223,149 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     def specPtrInc(ptr: RASPtr) = ptr + 1.U
     def specPtrDec(ptr: RASPtr) = ptr - 1.U
 
-    when (io.redirect_valid && io.redirect_isCall) {
+    when(io.redirect_valid && io.redirect_isCall) {
       writeBypassValidWire := true.B
-      writeBypassValid := true.B
-    } .elsewhen (io.redirect_valid) {
+      writeBypassValid     := true.B
+    }.elsewhen(io.redirect_valid) {
       // clear current top writeBypass if doing redirect
       writeBypassValidWire := false.B
-      writeBypassValid := false.B
-    } .elsewhen (io.s2_fire) {
+      writeBypassValid     := false.B
+    }.elsewhen(io.s2_fire) {
       writeBypassValidWire := io.spec_push_valid
-      writeBypassValid := io.spec_push_valid
-    } .elsewhen (io.s3_fire) {
+      writeBypassValid     := io.spec_push_valid
+    }.elsewhen(io.s3_fire) {
       writeBypassValidWire := false.B
-      writeBypassValid := false.B
-    } .otherwise {
+      writeBypassValid     := false.B
+    }.otherwise {
       writeBypassValidWire := writeBypassValid
     }
 
     val topEntry = getTop(ssp, sctr, TOSR, TOSW, true)
-    val topNos = getTopNos(TOSR, true)
-    val redirectTopEntry = getTop(io.redirect_meta_ssp, io.redirect_meta_sctr, io.redirect_meta_TOSR, io.redirect_meta_TOSW, false)
+    val topNos   = getTopNos(TOSR, true)
+    val redirectTopEntry =
+      getTop(io.redirect_meta_ssp, io.redirect_meta_sctr, io.redirect_meta_TOSR, io.redirect_meta_TOSW, false)
     val redirectTopNos = io.redirect_meta_NOS
-    val s3TopEntry = getTop(io.s3_meta.ssp, io.s3_meta.sctr, io.s3_meta.TOSR, io.s3_meta.TOSW, false)
-    val s3TopNos = io.s3_meta.NOS
+    val s3TopEntry     = getTop(io.s3_meta.ssp, io.s3_meta.sctr, io.s3_meta.TOSR, io.s3_meta.TOSW, false)
+    val s3TopNos       = io.s3_meta.NOS
 
     val writeEntry = Wire(new RASEntry)
-    val writeNos = Wire(new RASPtr)
-    writeEntry.retAddr := Mux(io.redirect_valid && io.redirect_isCall,  io.redirect_callAddr, io.spec_push_addr)
-    writeEntry.ctr := Mux(io.redirect_valid && io.redirect_isCall,
-      Mux(redirectTopEntry.retAddr === io.redirect_callAddr && redirectTopEntry.ctr < ctrMax, io.redirect_meta_sctr + 1.U, 0.U),
-      Mux(topEntry.retAddr === io.spec_push_addr && topEntry.ctr < ctrMax, sctr + 1.U, 0.U))
-
-    writeNos := Mux(io.redirect_valid && io.redirect_isCall,
-      io.redirect_meta_TOSR, TOSR)
-
-    when (io.spec_push_valid || (io.redirect_valid && io.redirect_isCall)) {
+    val writeNos   = Wire(new RASPtr)
+    writeEntry.retAddr := Mux(io.redirect_valid && io.redirect_isCall, io.redirect_callAddr, io.spec_push_addr)
+    writeEntry.ctr := Mux(
+      io.redirect_valid && io.redirect_isCall,
+      Mux(
+        redirectTopEntry.retAddr === io.redirect_callAddr && redirectTopEntry.ctr < ctrMax,
+        io.redirect_meta_sctr + 1.U,
+        0.U
+      ),
+      Mux(topEntry.retAddr === io.spec_push_addr && topEntry.ctr < ctrMax, sctr + 1.U, 0.U)
+    )
+
+    writeNos := Mux(io.redirect_valid && io.redirect_isCall, io.redirect_meta_TOSR, TOSR)
+
+    when(io.spec_push_valid || (io.redirect_valid && io.redirect_isCall)) {
       writeBypassEntry := writeEntry
-      writeBypassNos := writeNos
+      writeBypassNos   := writeNos
     }
 
-    val realPush = Wire(Bool())
+    val realPush       = Wire(Bool())
     val realWriteEntry = Wire(new RASEntry)
-    val timingTop = RegInit(0.U.asTypeOf(new RASEntry))
-    val timingNos = RegInit(0.U.asTypeOf(new RASPtr))
+    val timingTop      = RegInit(0.U.asTypeOf(new RASEntry))
+    val timingNos      = RegInit(0.U.asTypeOf(new RASPtr))
 
-    when (writeBypassValidWire) {
-      when ((io.redirect_valid && io.redirect_isCall) || io.spec_push_valid) {
+    when(writeBypassValidWire) {
+      when((io.redirect_valid && io.redirect_isCall) || io.spec_push_valid) {
         timingTop := writeEntry
         timingNos := writeNos
-      } .otherwise {
+      }.otherwise {
         timingTop := writeBypassEntry
         timingNos := writeBypassNos
       }
 
-    } .elsewhen (io.redirect_valid && io.redirect_isRet) {
+    }.elsewhen(io.redirect_valid && io.redirect_isRet) {
       // getTop using redirect Nos as TOSR
-      val popRedSsp = Wire(UInt(log2Up(rasSize).W))
+      val popRedSsp  = Wire(UInt(log2Up(rasSize).W))
       val popRedSctr = Wire(UInt(RasCtrSize.W))
       val popRedTOSR = io.redirect_meta_NOS
       val popRedTOSW = io.redirect_meta_TOSW
 
-      when (io.redirect_meta_sctr > 0.U) {
+      when(io.redirect_meta_sctr > 0.U) {
         popRedSctr := io.redirect_meta_sctr - 1.U
-        popRedSsp := io.redirect_meta_ssp
-      } .elsewhen (TOSRinRange(popRedTOSR, TOSW)) {
-        popRedSsp := ptrDec(io.redirect_meta_ssp)
+        popRedSsp  := io.redirect_meta_ssp
+      }.elsewhen(TOSRinRange(popRedTOSR, TOSW)) {
+        popRedSsp  := ptrDec(io.redirect_meta_ssp)
         popRedSctr := spec_queue(popRedTOSR.value).ctr
-      } .otherwise {
-        popRedSsp := ptrDec(io.redirect_meta_ssp)
+      }.otherwise {
+        popRedSsp  := ptrDec(io.redirect_meta_ssp)
         popRedSctr := getCommitTop(ptrDec(io.redirect_meta_ssp)).ctr
       }
       // We are deciding top for the next cycle, no need to use bypass here
       timingTop := getTop(popRedSsp, popRedSctr, popRedTOSR, popRedTOSW, false)
-    } .elsewhen (io.redirect_valid) {
+    }.elsewhen(io.redirect_valid) {
       // Neither call nor ret
-      val popSsp = io.redirect_meta_ssp
+      val popSsp  = io.redirect_meta_ssp
       val popSctr = io.redirect_meta_sctr
       val popTOSR = io.redirect_meta_TOSR
       val popTOSW = io.redirect_meta_TOSW
 
       timingTop := getTop(popSsp, popSctr, popTOSR, popTOSW, false)
 
-    } .elsewhen (io.spec_pop_valid) {
+    }.elsewhen(io.spec_pop_valid) {
       // getTop using current Nos as TOSR
-      val popSsp = Wire(UInt(log2Up(rasSize).W))
+      val popSsp  = Wire(UInt(log2Up(rasSize).W))
       val popSctr = Wire(UInt(RasCtrSize.W))
       val popTOSR = topNos
       val popTOSW = TOSW
 
-      when (sctr > 0.U) {
+      when(sctr > 0.U) {
         popSctr := sctr - 1.U
-        popSsp := ssp
-      } .elsewhen (TOSRinRange(popTOSR, TOSW)) {
-        popSsp := ptrDec(ssp)
+        popSsp  := ssp
+      }.elsewhen(TOSRinRange(popTOSR, TOSW)) {
+        popSsp  := ptrDec(ssp)
         popSctr := spec_queue(popTOSR.value).ctr
-      } .otherwise {
-        popSsp := ptrDec(ssp)
+      }.otherwise {
+        popSsp  := ptrDec(ssp)
         popSctr := getCommitTop(ptrDec(ssp)).ctr
       }
       // We are deciding top for the next cycle, no need to use bypass here
       timingTop := getTop(popSsp, popSctr, popTOSR, popTOSW, false)
-    } .elsewhen (realPush) {
+    }.elsewhen(realPush) {
       // just updating spec queue, cannot read from there
       timingTop := realWriteEntry
-    } .elsewhen (io.s3_cancel) {
+    }.elsewhen(io.s3_cancel) {
       // s3 is different with s2
       timingTop := getTop(io.s3_meta.ssp, io.s3_meta.sctr, io.s3_meta.TOSR, io.s3_meta.TOSW, false)
-      when (io.s3_missed_push) {
+      when(io.s3_missed_push) {
         val writeEntry_s3 = Wire(new RASEntry)
-        timingTop := writeEntry_s3
+        timingTop             := writeEntry_s3
         writeEntry_s3.retAddr := io.s3_pushAddr
-        writeEntry_s3.ctr := Mux(timingTop.retAddr === io.s3_pushAddr && io.s3_meta.sctr < ctrMax, io.s3_meta.sctr + 1.U, 0.U)
-      } .elsewhen (io.s3_missed_pop) {
-        val popRedSsp_s3 = Wire(UInt(log2Up(rasSize).W))
+        writeEntry_s3.ctr := Mux(
+          timingTop.retAddr === io.s3_pushAddr && io.s3_meta.sctr < ctrMax,
+          io.s3_meta.sctr + 1.U,
+          0.U
+        )
+      }.elsewhen(io.s3_missed_pop) {
+        val popRedSsp_s3  = Wire(UInt(log2Up(rasSize).W))
         val popRedSctr_s3 = Wire(UInt(RasCtrSize.W))
         val popRedTOSR_s3 = io.s3_meta.NOS
         val popRedTOSW_s3 = io.s3_meta.TOSW
 
-        when (io.s3_meta.sctr > 0.U) {
+        when(io.s3_meta.sctr > 0.U) {
           popRedSctr_s3 := io.s3_meta.sctr - 1.U
-          popRedSsp_s3 := io.s3_meta.ssp
-        } .elsewhen (TOSRinRange(popRedTOSR_s3, popRedTOSW_s3)) {
-          popRedSsp_s3 := ptrDec(io.s3_meta.ssp)
+          popRedSsp_s3  := io.s3_meta.ssp
+        }.elsewhen(TOSRinRange(popRedTOSR_s3, popRedTOSW_s3)) {
+          popRedSsp_s3  := ptrDec(io.s3_meta.ssp)
           popRedSctr_s3 := spec_queue(popRedTOSR_s3.value).ctr
-        } .otherwise {
-          popRedSsp_s3 := ptrDec(io.s3_meta.ssp)
+        }.otherwise {
+          popRedSsp_s3  := ptrDec(io.s3_meta.ssp)
           popRedSctr_s3 := getCommitTop(ptrDec(io.s3_meta.ssp)).ctr
         }
         // We are deciding top for the next cycle, no need to use bypass here
         timingTop := getTop(popRedSsp_s3, popRedSctr_s3, popRedTOSR_s3, popRedTOSW_s3, false)
       }
-    } .otherwise {
+    }.otherwise {
       // easy case
-      val popSsp = ssp
+      val popSsp  = ssp
       val popSctr = sctr
       val popTOSR = TOSR
       val popTOSW = TOSW
@@ -367,96 +377,121 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     // could diff when more pop than push and a commit stack is updated with inflight info
 
     val realWriteEntry_next = RegEnable(writeEntry, io.s2_fire || io.redirect_isCall)
-    val s3_missPushEntry = Wire(new RASEntry)
-    val s3_missPushAddr = Wire(new RASPtr)
-    val s3_missPushNos = Wire(new RASPtr)
+    val s3_missPushEntry    = Wire(new RASEntry)
+    val s3_missPushAddr     = Wire(new RASPtr)
+    val s3_missPushNos      = Wire(new RASPtr)
 
     s3_missPushEntry.retAddr := io.s3_pushAddr
-    s3_missPushEntry.ctr := Mux(s3TopEntry.retAddr === io.s3_pushAddr && s3TopEntry.ctr < ctrMax, io.s3_meta.sctr + 1.U, 0.U)
+    s3_missPushEntry.ctr := Mux(
+      s3TopEntry.retAddr === io.s3_pushAddr && s3TopEntry.ctr < ctrMax,
+      io.s3_meta.sctr + 1.U,
+      0.U
+    )
     s3_missPushAddr := io.s3_meta.TOSW
-    s3_missPushNos := io.s3_meta.TOSR
-
-    realWriteEntry := Mux(io.redirect_isCall, realWriteEntry_next,
-      Mux(io.s3_missed_push, s3_missPushEntry,
-      realWriteEntry_next))
-
-    val realWriteAddr_next = RegEnable(Mux(io.redirect_valid && io.redirect_isCall, io.redirect_meta_TOSW, TOSW), io.s2_fire || (io.redirect_valid && io.redirect_isCall))
-    val realWriteAddr = Mux(io.redirect_isCall, realWriteAddr_next,
-      Mux(io.s3_missed_push, s3_missPushAddr,
-      realWriteAddr_next))
-    val realNos_next = RegEnable(Mux(io.redirect_valid && io.redirect_isCall, io.redirect_meta_TOSR, TOSR), io.s2_fire || (io.redirect_valid && io.redirect_isCall))
-    val realNos = Mux(io.redirect_isCall, realNos_next,
-      Mux(io.s3_missed_push, s3_missPushNos,
-      realNos_next))
-
-    realPush := (io.s3_fire && (!io.s3_cancel && RegEnable(io.spec_push_valid, io.s2_fire) || io.s3_missed_push)) || RegNext(io.redirect_valid && io.redirect_isCall)
-
-    when (realPush) {
+    s3_missPushNos  := io.s3_meta.TOSR
+
+    realWriteEntry := Mux(
+      io.redirect_isCall,
+      realWriteEntry_next,
+      Mux(io.s3_missed_push, s3_missPushEntry, realWriteEntry_next)
+    )
+
+    val realWriteAddr_next = RegEnable(
+      Mux(io.redirect_valid && io.redirect_isCall, io.redirect_meta_TOSW, TOSW),
+      io.s2_fire || (io.redirect_valid && io.redirect_isCall)
+    )
+    val realWriteAddr =
+      Mux(io.redirect_isCall, realWriteAddr_next, Mux(io.s3_missed_push, s3_missPushAddr, realWriteAddr_next))
+    val realNos_next = RegEnable(
+      Mux(io.redirect_valid && io.redirect_isCall, io.redirect_meta_TOSR, TOSR),
+      io.s2_fire || (io.redirect_valid && io.redirect_isCall)
+    )
+    val realNos = Mux(io.redirect_isCall, realNos_next, Mux(io.s3_missed_push, s3_missPushNos, realNos_next))
+
+    realPush := (io.s3_fire && (!io.s3_cancel && RegEnable(
+      io.spec_push_valid,
+      io.s2_fire
+    ) || io.s3_missed_push)) || RegNext(io.redirect_valid && io.redirect_isCall)
+
+    when(realPush) {
       spec_queue(realWriteAddr.value) := realWriteEntry
-      spec_nos(realWriteAddr.value) := realNos
+      spec_nos(realWriteAddr.value)   := realNos
     }
 
-    def specPush(retAddr: UInt, currentSsp: UInt, currentSctr: UInt, currentTOSR: RASPtr, currentTOSW: RASPtr, topEntry: RASEntry) = {
+    def specPush(
+        retAddr:     UInt,
+        currentSsp:  UInt,
+        currentSctr: UInt,
+        currentTOSR: RASPtr,
+        currentTOSW: RASPtr,
+        topEntry:    RASEntry
+    ) = {
       TOSR := currentTOSW
       TOSW := specPtrInc(currentTOSW)
       // spec sp and ctr should always be maintained
-      when (topEntry.retAddr === retAddr && currentSctr < ctrMax) {
+      when(topEntry.retAddr === retAddr && currentSctr < ctrMax) {
         sctr := currentSctr + 1.U
-      } .otherwise {
-        ssp := ptrInc(currentSsp)
+      }.otherwise {
+        ssp  := ptrInc(currentSsp)
         sctr := 0.U
       }
     }
 
-    when (io.spec_push_valid) {
+    when(io.spec_push_valid) {
       specPush(io.spec_push_addr, ssp, sctr, TOSR, TOSW, topEntry)
     }
-    def specPop(currentSsp: UInt, currentSctr: UInt, currentTOSR: RASPtr, currentTOSW: RASPtr, currentTopNos: RASPtr) = {
+    def specPop(
+        currentSsp:    UInt,
+        currentSctr:   UInt,
+        currentTOSR:   RASPtr,
+        currentTOSW:   RASPtr,
+        currentTopNos: RASPtr
+    ) = {
       // TOSR is only maintained when spec queue is not empty
-      when (TOSRinRange(currentTOSR, currentTOSW)) {
+      when(TOSRinRange(currentTOSR, currentTOSW)) {
         TOSR := currentTopNos
       }
       // spec sp and ctr should always be maintained
-      when (currentSctr > 0.U) {
+      when(currentSctr > 0.U) {
         sctr := currentSctr - 1.U
-      } .elsewhen (TOSRinRange(currentTopNos, currentTOSW)) {
+      }.elsewhen(TOSRinRange(currentTopNos, currentTOSW)) {
         // in range, use inflight data
-        ssp := ptrDec(currentSsp)
+        ssp  := ptrDec(currentSsp)
         sctr := spec_queue(currentTopNos.value).ctr
-      } .otherwise {
+      }.otherwise {
         // NOS not in range, use commit data
-        ssp := ptrDec(currentSsp)
+        ssp  := ptrDec(currentSsp)
         sctr := getCommitTop(ptrDec(currentSsp)).ctr
         // in overflow state, we cannot determine the next sctr, sctr here is not accurate
       }
     }
-    when (io.spec_pop_valid) {
+    when(io.spec_pop_valid) {
       specPop(ssp, sctr, TOSR, TOSW, topNos)
     }
 
     // io.spec_pop_addr := Mux(writeBypassValid, writeBypassEntry.retAddr, topEntry.retAddr)
 
     io.spec_pop_addr := timingTop.retAddr
-    io.BOS := BOS
-    io.TOSW := TOSW
-    io.TOSR := TOSR
-    io.NOS := topNos
-    io.ssp := ssp
-    io.sctr := sctr
-    io.nsp := nsp
-
-    when (io.s3_cancel) {
+    io.BOS           := BOS
+    io.TOSW          := TOSW
+    io.TOSR          := TOSR
+    io.NOS           := topNos
+    io.ssp           := ssp
+    io.sctr          := sctr
+    io.nsp           := nsp
+
+    when(io.s3_cancel) {
       // recovery of all related pointers
       TOSR := io.s3_meta.TOSR
       TOSW := io.s3_meta.TOSW
-      ssp := io.s3_meta.ssp
+      ssp  := io.s3_meta.ssp
       sctr := io.s3_meta.sctr
 
       // for missing pop, we also need to do a pop here
-      when (io.s3_missed_pop) {
+      when(io.s3_missed_pop) {
         specPop(io.s3_meta.ssp, io.s3_meta.sctr, io.s3_meta.TOSR, io.s3_meta.TOSW, io.s3_meta.NOS)
       }
-      when (io.s3_missed_push) {
+      when(io.s3_missed_push) {
         // do not use any bypass from f2
         specPush(io.s3_pushAddr, io.s3_meta.ssp, io.s3_meta.sctr, io.s3_meta.TOSR, io.s3_meta.TOSW, s3TopEntry)
       }
@@ -464,21 +499,21 @@ class RAS(implicit p: Parameters) extends BasePredictor {
 
     val commitTop = commit_stack(nsp)
 
-    when (io.commit_pop_valid) {
+    when(io.commit_pop_valid) {
 
       val nsp_update = Wire(UInt(log2Up(rasSize).W))
-      when (io.commit_meta_ssp =/= nsp) {
+      when(io.commit_meta_ssp =/= nsp) {
         // force set nsp to commit ssp to avoid permanent errors
         nsp_update := io.commit_meta_ssp
-      } .otherwise {
+      }.otherwise {
         nsp_update := nsp
       }
 
       // if ctr > 0, --ctr in stack, otherwise --nsp
-      when (commitTop.ctr > 0.U) {
+      when(commitTop.ctr > 0.U) {
         commit_stack(nsp_update).ctr := commitTop.ctr - 1.U
-        nsp := nsp_update
-      } .otherwise {
+        nsp                          := nsp_update
+      }.otherwise {
         nsp := ptrDec(nsp_update);
       }
       // XSError(io.commit_meta_ssp =/= nsp, "nsp mismatch with expected ssp")
@@ -486,106 +521,124 @@ class RAS(implicit p: Parameters) extends BasePredictor {
 
     val commit_push_addr = spec_queue(io.commit_meta_TOSW.value).retAddr
 
-    when (io.commit_push_valid) {
+    when(io.commit_push_valid) {
       val nsp_update = Wire(UInt(log2Up(rasSize).W))
-      when (io.commit_meta_ssp =/= nsp) {
+      when(io.commit_meta_ssp =/= nsp) {
         // force set nsp to commit ssp to avoid permanent errors
         nsp_update := io.commit_meta_ssp
-      } .otherwise {
+      }.otherwise {
         nsp_update := nsp
       }
       // if ctr < max && topAddr == push addr, ++ctr, otherwise ++nsp
-      when (commitTop.ctr < ctrMax && commitTop.retAddr === commit_push_addr) {
+      when(commitTop.ctr < ctrMax && commitTop.retAddr === commit_push_addr) {
         commit_stack(nsp_update).ctr := commitTop.ctr + 1.U
-        nsp := nsp_update
-      } .otherwise {
-        nsp := ptrInc(nsp_update)
+        nsp                          := nsp_update
+      }.otherwise {
+        nsp                                      := ptrInc(nsp_update)
         commit_stack(ptrInc(nsp_update)).retAddr := commit_push_addr
-        commit_stack(ptrInc(nsp_update)).ctr := 0.U
+        commit_stack(ptrInc(nsp_update)).ctr     := 0.U
       }
 
       // XSError(io.commit_meta_ssp =/= nsp, "nsp mismatch with expected ssp")
       // XSError(io.commit_push_addr =/= commit_push_addr, "addr from commit mismatch with addr from spec")
     }
 
-    when (io.commit_push_valid) {
+    when(io.commit_push_valid) {
       BOS := io.commit_meta_TOSW
-    } .elsewhen(io.commit_valid && (distanceBetween(io.commit_meta_TOSW,BOS) > 2.U)) {
+    }.elsewhen(io.commit_valid && (distanceBetween(io.commit_meta_TOSW, BOS) > 2.U)) {
       BOS := specPtrDec(io.commit_meta_TOSW)
     }
 
-    when (io.redirect_valid) {
+    when(io.redirect_valid) {
       TOSR := io.redirect_meta_TOSR
       TOSW := io.redirect_meta_TOSW
-      ssp := io.redirect_meta_ssp
+      ssp  := io.redirect_meta_ssp
       sctr := io.redirect_meta_sctr
 
-      when (io.redirect_isCall) {
-        specPush(io.redirect_callAddr, io.redirect_meta_ssp, io.redirect_meta_sctr, io.redirect_meta_TOSR, io.redirect_meta_TOSW, redirectTopEntry)
+      when(io.redirect_isCall) {
+        specPush(
+          io.redirect_callAddr,
+          io.redirect_meta_ssp,
+          io.redirect_meta_sctr,
+          io.redirect_meta_TOSR,
+          io.redirect_meta_TOSW,
+          redirectTopEntry
+        )
       }
-      when (io.redirect_isRet) {
-        specPop(io.redirect_meta_ssp, io.redirect_meta_sctr, io.redirect_meta_TOSR, io.redirect_meta_TOSW, redirectTopNos)
+      when(io.redirect_isRet) {
+        specPop(
+          io.redirect_meta_ssp,
+          io.redirect_meta_sctr,
+          io.redirect_meta_TOSR,
+          io.redirect_meta_TOSW,
+          redirectTopNos
+        )
       }
     }
 
-    when(distanceBetween(TOSW,BOS) > (rasSpecSize - 4).U){
-      spec_near_overflowed  := true.B
-    }.otherwise{
-      spec_near_overflowed  := false.B
+    when(distanceBetween(TOSW, BOS) > (rasSpecSize - 4).U) {
+      spec_near_overflowed := true.B
+    }.otherwise {
+      spec_near_overflowed := false.B
     }
 
-    io.spec_near_overflow   := spec_near_overflowed
+    io.spec_near_overflow := spec_near_overflowed
     XSPerfAccumulate("spec_near_overflow", spec_near_overflowed)
-    io.debug.commit_stack.zipWithIndex.foreach{case (a, i) => a := commit_stack(i)}
-    io.debug.spec_nos.zipWithIndex.foreach{case (a, i) => a := spec_nos(i)}
-    io.debug.spec_queue.zipWithIndex.foreach{ case (a, i) => a := spec_queue(i)}
+    io.debug.commit_stack.zipWithIndex.foreach { case (a, i) => a := commit_stack(i) }
+    io.debug.spec_nos.zipWithIndex.foreach { case (a, i) => a := spec_nos(i) }
+    io.debug.spec_queue.zipWithIndex.foreach { case (a, i) => a := spec_queue(i) }
   }
 
   val stack = Module(new RASStack(RasSize, RasSpecSize)).io
 
   val s2_spec_push = WireInit(false.B)
-  val s2_spec_pop = WireInit(false.B)
+  val s2_spec_pop  = WireInit(false.B)
   val s2_full_pred = io.in.bits.resp_in(0).s2.full_pred(2)
   // when last inst is an rvi call, fall through address would be set to the middle of it, so an addition is needed
   val s2_spec_new_addr = s2_full_pred.fallThroughAddr + Mux(s2_full_pred.last_may_be_rvi_call, 2.U, 0.U)
   stack.spec_push_valid := s2_spec_push
   stack.spec_pop_valid  := s2_spec_pop
-  stack.spec_push_addr := s2_spec_new_addr
+  stack.spec_push_addr  := s2_spec_new_addr
 
   // confirm that the call/ret is the taken cfi
   s2_spec_push := io.s2_fire(2) && s2_full_pred.hit_taken_on_call && !io.s3_redirect(2)
-  s2_spec_pop  := io.s2_fire(2) && s2_full_pred.hit_taken_on_ret  && !io.s3_redirect(2)
+  s2_spec_pop  := io.s2_fire(2) && s2_full_pred.hit_taken_on_ret && !io.s3_redirect(2)
 
-  //val s2_jalr_target = io.out.s2.full_pred.jalr_target
-  //val s2_last_target_in = s2_full_pred.targets.last
+  // val s2_jalr_target = io.out.s2.full_pred.jalr_target
+  // val s2_last_target_in = s2_full_pred.targets.last
   // val s2_last_target_out = io.out.s2.full_pred(2).targets.last
   val s2_is_jalr = s2_full_pred.is_jalr
-  val s2_is_ret = s2_full_pred.is_ret
-  val s2_top = stack.spec_pop_addr
+  val s2_is_ret  = s2_full_pred.is_ret
+  val s2_top     = stack.spec_pop_addr
   // assert(is_jalr && is_ret || !is_ret)
   when(s2_is_ret && io.ctrl.ras_enable) {
     io.out.s2.full_pred.map(_.jalr_target).foreach(_ := s2_top)
     // FIXME: should use s1 globally
   }
-  //s2_last_target_out := Mux(s2_is_jalr, s2_jalr_target, s2_last_target_in)
-  io.out.s2.full_pred.zipWithIndex.foreach{ case (a, i) =>
-    a.targets.last := Mux(s2_is_jalr, io.out.s2.full_pred(i).jalr_target, io.in.bits.resp_in(0).s2.full_pred(i).targets.last)
+  // s2_last_target_out := Mux(s2_is_jalr, s2_jalr_target, s2_last_target_in)
+  io.out.s2.full_pred.zipWithIndex.foreach { case (a, i) =>
+    a.targets.last := Mux(
+      s2_is_jalr,
+      io.out.s2.full_pred(i).jalr_target,
+      io.in.bits.resp_in(0).s2.full_pred(i).targets.last
+    )
   }
 
   val s2_meta = Wire(new RASInternalMeta)
-  s2_meta.ssp := stack.ssp
+  s2_meta.ssp  := stack.ssp
   s2_meta.sctr := stack.sctr
   s2_meta.TOSR := stack.TOSR
   s2_meta.TOSW := stack.TOSW
-  s2_meta.NOS := stack.NOS
+  s2_meta.NOS  := stack.NOS
 
-  val s3_top = RegEnable(stack.spec_pop_addr, io.s2_fire(2))
+  val s3_top           = RegEnable(stack.spec_pop_addr, io.s2_fire(2))
   val s3_spec_new_addr = RegEnable(s2_spec_new_addr, io.s2_fire(2))
 
   // val s3_jalr_target = io.out.s3.full_pred.jalr_target
   // val s3_last_target_in = io.in.bits.resp_in(0).s3.full_pred(2).targets.last
   // val s3_last_target_out = io.out.s3.full_pred(2).targets.last
-  val s3_is_jalr = io.in.bits.resp_in(0).s3.full_pred(2).is_jalr && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
+  val s3_is_jalr =
+    io.in.bits.resp_in(0).s3.full_pred(2).is_jalr && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
   val s3_is_ret = io.in.bits.resp_in(0).s3.full_pred(2).is_ret && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
   // assert(is_jalr && is_ret || !is_ret)
   when(s3_is_ret && io.ctrl.ras_enable) {
@@ -593,14 +646,20 @@ class RAS(implicit p: Parameters) extends BasePredictor {
     // FIXME: should use s1 globally
   }
   // s3_last_target_out := Mux(s3_is_jalr, s3_jalr_target, s3_last_target_in)
-  io.out.s3.full_pred.zipWithIndex.foreach{ case (a, i) =>
-    a.targets.last := Mux(s3_is_jalr, io.out.s3.full_pred(i).jalr_target, io.in.bits.resp_in(0).s3.full_pred(i).targets.last)
+  io.out.s3.full_pred.zipWithIndex.foreach { case (a, i) =>
+    a.targets.last := Mux(
+      s3_is_jalr,
+      io.out.s3.full_pred(i).jalr_target,
+      io.in.bits.resp_in(0).s3.full_pred(i).targets.last
+    )
   }
 
   val s3_pushed_in_s2 = RegEnable(s2_spec_push, io.s2_fire(2))
-  val s3_popped_in_s2 = RegEnable(s2_spec_pop,  io.s2_fire(2))
-  val s3_push = io.in.bits.resp_in(0).s3.full_pred(2).hit_taken_on_call && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
-  val s3_pop  = io.in.bits.resp_in(0).s3.full_pred(2).hit_taken_on_ret && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
+  val s3_popped_in_s2 = RegEnable(s2_spec_pop, io.s2_fire(2))
+  val s3_push =
+    io.in.bits.resp_in(0).s3.full_pred(2).hit_taken_on_call && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
+  val s3_pop =
+    io.in.bits.resp_in(0).s3.full_pred(2).hit_taken_on_ret && !io.in.bits.resp_in(0).s3.full_pred(2).fallThroughErr
 
   val s3_cancel = io.s3_fire(2) && (s3_pushed_in_s2 =/= s3_push || s3_popped_in_s2 =/= s3_pop)
   stack.s2_fire := io.s2_fire(2)
@@ -610,81 +669,95 @@ class RAS(implicit p: Parameters) extends BasePredictor {
 
   val s3_meta = RegEnable(s2_meta, io.s2_fire(2))
 
-  stack.s3_meta := s3_meta
-  stack.s3_missed_pop := s3_pop && !s3_popped_in_s2
+  stack.s3_meta        := s3_meta
+  stack.s3_missed_pop  := s3_pop && !s3_popped_in_s2
   stack.s3_missed_push := s3_push && !s3_pushed_in_s2
-  stack.s3_pushAddr := s3_spec_new_addr
+  stack.s3_pushAddr    := s3_spec_new_addr
 
   // no longer need the top Entry, but TOSR, TOSW, ssp sctr
   // TODO: remove related signals
 
   val last_stage_meta = Wire(new RASMeta)
-  last_stage_meta.ssp := s3_meta.ssp
+  last_stage_meta.ssp  := s3_meta.ssp
   last_stage_meta.TOSW := s3_meta.TOSW
 
-  io.s1_ready   := !stack.spec_near_overflow
+  io.s1_ready := !stack.spec_near_overflow
 
-  io.out.last_stage_spec_info.sctr  := s3_meta.sctr
-  io.out.last_stage_spec_info.ssp := s3_meta.ssp
-  io.out.last_stage_spec_info.TOSW := s3_meta.TOSW
-  io.out.last_stage_spec_info.TOSR := s3_meta.TOSR
-  io.out.last_stage_spec_info.NOS := s3_meta.NOS
+  io.out.last_stage_spec_info.sctr    := s3_meta.sctr
+  io.out.last_stage_spec_info.ssp     := s3_meta.ssp
+  io.out.last_stage_spec_info.TOSW    := s3_meta.TOSW
+  io.out.last_stage_spec_info.TOSR    := s3_meta.TOSR
+  io.out.last_stage_spec_info.NOS     := s3_meta.NOS
   io.out.last_stage_spec_info.topAddr := s3_top
-  io.out.last_stage_meta := last_stage_meta.asUInt
-
+  io.out.last_stage_meta              := last_stage_meta.asUInt
 
-  val redirect = RegNextWithEnable(io.redirect)
-  val do_recover = redirect.valid
+  val redirect    = RegNextWithEnable(io.redirect)
+  val do_recover  = redirect.valid
   val recover_cfi = redirect.bits.cfiUpdate
 
   val retMissPred  = do_recover && redirect.bits.level === 0.U && recover_cfi.pd.isRet
   val callMissPred = do_recover && redirect.bits.level === 0.U && recover_cfi.pd.isCall
   // when we mispredict a call, we must redo a push operation
   // similarly, when we mispredict a return, we should redo a pop
-  stack.redirect_valid := do_recover
-  stack.redirect_isCall := callMissPred
-  stack.redirect_isRet := retMissPred
-  stack.redirect_meta_ssp := recover_cfi.ssp
+  stack.redirect_valid     := do_recover
+  stack.redirect_isCall    := callMissPred
+  stack.redirect_isRet     := retMissPred
+  stack.redirect_meta_ssp  := recover_cfi.ssp
   stack.redirect_meta_sctr := recover_cfi.sctr
   stack.redirect_meta_TOSW := recover_cfi.TOSW
   stack.redirect_meta_TOSR := recover_cfi.TOSR
-  stack.redirect_meta_NOS := recover_cfi.NOS
-  stack.redirect_callAddr := recover_cfi.pc + Mux(recover_cfi.pd.isRVC, 2.U, 4.U)
+  stack.redirect_meta_NOS  := recover_cfi.NOS
+  stack.redirect_callAddr  := recover_cfi.pc + Mux(recover_cfi.pd.isRVC, 2.U, 4.U)
 
-  val update = io.update.bits
-  val updateMeta = io.update.bits.meta.asTypeOf(new RASMeta)
+  val update      = io.update.bits
+  val updateMeta  = io.update.bits.meta.asTypeOf(new RASMeta)
   val updateValid = io.update.valid
 
-  stack.commit_valid      := updateValid  
+  stack.commit_valid      := updateValid
   stack.commit_push_valid := updateValid && update.is_call_taken
-  stack.commit_pop_valid := updateValid && update.is_ret_taken
-  stack.commit_push_addr := update.ftb_entry.getFallThrough(update.pc) + Mux(update.ftb_entry.last_may_be_rvi_call, 2.U, 0.U)
+  stack.commit_pop_valid  := updateValid && update.is_ret_taken
+  stack.commit_push_addr := update.ftb_entry.getFallThrough(update.pc) + Mux(
+    update.ftb_entry.last_may_be_rvi_call,
+    2.U,
+    0.U
+  )
   stack.commit_meta_TOSW := updateMeta.TOSW
-  stack.commit_meta_ssp := updateMeta.ssp
-
+  stack.commit_meta_ssp  := updateMeta.ssp
 
   XSPerfAccumulate("ras_s3_cancel", s3_cancel)
   XSPerfAccumulate("ras_redirect_recover", redirect.valid)
   XSPerfAccumulate("ras_s3_and_redirect_recover_at_the_same_time", s3_cancel && redirect.valid)
 
-
   val spec_debug = stack.debug
   XSDebug(io.s2_fire(2), "----------------RAS----------------\n")
-  XSDebug(io.s2_fire(2), " TopRegister: 0x%x\n",stack.spec_pop_addr)
+  XSDebug(io.s2_fire(2), " TopRegister: 0x%x\n", stack.spec_pop_addr)
   XSDebug(io.s2_fire(2), "  index       addr           ctr           nos (spec part)\n")
-  for(i <- 0 until RasSpecSize){
-      XSDebug(io.s2_fire(2), "  (%d)   0x%x      %d       %d",i.U,spec_debug.spec_queue(i).retAddr,spec_debug.spec_queue(i).ctr, spec_debug.spec_nos(i).value)
-      when(i.U === stack.TOSW.value){XSDebug(io.s2_fire(2), "   <----TOSW")}
-      when(i.U === stack.TOSR.value){XSDebug(io.s2_fire(2), "   <----TOSR")}
-      when(i.U === stack.BOS.value){XSDebug(io.s2_fire(2), "   <----BOS")}
-      XSDebug(io.s2_fire(2), "\n")
+  for (i <- 0 until RasSpecSize) {
+    XSDebug(
+      io.s2_fire(2),
+      "  (%d)   0x%x      %d       %d",
+      i.U,
+      spec_debug.spec_queue(i).retAddr,
+      spec_debug.spec_queue(i).ctr,
+      spec_debug.spec_nos(i).value
+    )
+    when(i.U === stack.TOSW.value)(XSDebug(io.s2_fire(2), "   <----TOSW"))
+    when(i.U === stack.TOSR.value)(XSDebug(io.s2_fire(2), "   <----TOSR"))
+    when(i.U === stack.BOS.value)(XSDebug(io.s2_fire(2), "   <----BOS"))
+    XSDebug(io.s2_fire(2), "\n")
   }
   XSDebug(io.s2_fire(2), "  index       addr           ctr   (committed part)\n")
-  for(i <- 0 until RasSize){
-      XSDebug(io.s2_fire(2), "  (%d)   0x%x      %d",i.U,spec_debug.commit_stack(i).retAddr,spec_debug.commit_stack(i).ctr)
-      when(i.U === stack.ssp){XSDebug(io.s2_fire(2), "   <----ssp")}
-      when(i.U === stack.nsp){XSDebug(io.s2_fire(2), "   <----nsp")}
-      XSDebug(io.s2_fire(2), "\n")
+  for (i <- 0 until RasSize) {
+    XSDebug(
+      io.s2_fire(2),
+      "  (%d)   0x%x      %d",
+      i.U,
+      spec_debug.commit_stack(i).retAddr,
+      spec_debug.commit_stack(i).ctr
+    )
+    when(i.U === stack.ssp)(XSDebug(io.s2_fire(2), "   <----ssp"))
+    when(i.U === stack.nsp)(XSDebug(io.s2_fire(2), "   <----nsp"))
+    XSDebug(io.s2_fire(2), "\n")
   }
   /*
   XSDebug(s2_spec_push, "s2_spec_push  inAddr: 0x%x  inCtr: %d |  allocNewEntry:%d |   sp:%d \n",
@@ -699,7 +772,7 @@ class RAS(implicit p: Parameters) extends BasePredictor {
   XSDebug(do_recover && retMissPred, "redirect_recover_pop\n")
   XSDebug(do_recover, "redirect_recover(SP:%d retAddr:%x ctr:%d) \n",
       redirectUpdate.rasSp,redirectUpdate.rasEntry.retAddr,redirectUpdate.rasEntry.ctr)
-  */
+   */
 
   generatePerfEvent()
 }
```
