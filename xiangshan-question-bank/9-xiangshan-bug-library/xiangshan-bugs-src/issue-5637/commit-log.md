# Commit Log
- Issue: #5637
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5637
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5637
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5637
- Changed files: 2
- Additions: 160
- Deletions: 118

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 9d87501eb32..0a15cb908bc 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -98,7 +98,6 @@ class CtrlBlockImp(
   val gpaMem = wrapper.gpaMem.module
   val decode = Module(new DecodeStage)
   val fusionDecoder = Module(new FusionDecoder)
-  val rat = Module(new RenameTableWrapper)
   val rename = Module(new Rename)
   val redirectGen = Module(new RedirectGenerator)
   private def hasRen: Boolean = true
@@ -525,11 +524,11 @@ class CtrlBlockImp(
   /** no valid instr in decode buffer && no valid instr from frontend --> can accept new instr from frontend */
   io.frontend.canAccept := !decodeBufValid(0) || !decodeFromFrontend(0).valid
   decode.io.csrCtrl := RegNext(io.csrCtrl)
-  decode.io.intRat <> rat.io.intReadPorts
-  decode.io.fpRat <> rat.io.fpReadPorts
-  decode.io.vecRat <> rat.io.vecReadPorts
-  decode.io.v0Rat <> rat.io.v0ReadPorts
-  decode.io.vlRat <> rat.io.vlReadPorts
+  decode.io.intRat <> rename.io.intReadPorts
+  decode.io.fpRat  <> rename.io.fpReadPorts
+  decode.io.vecRat <> rename.io.vecReadPorts
+  decode.io.v0Rat  <> rename.io.v0ReadPorts
+  decode.io.vlRat  <> rename.io.vlReadPorts
   decode.io.fusion := 0.U.asTypeOf(decode.io.fusion) // Todo
   decode.io.stallReason.in <> io.frontend.stallReason
 
@@ -576,11 +575,11 @@ class CtrlBlockImp(
   rob.io.snpt.useSnpt := useSnpt
   rob.io.snpt.snptSelect := snptSelect
   rob.io.snpt.flushVec := flushVecNext
-  rat.io.snpt.snptEnq := genSnapshot
-  rat.io.snpt.snptDeq := snpt.io.deq
-  rat.io.snpt.useSnpt := useSnpt
-  rat.io.snpt.snptSelect := snptSelect
-  rat.io.snpt.flushVec := flushVec
+  rename.io.ratSnpt.snptEnq := genSnapshot
+  rename.io.ratSnpt.snptDeq := snpt.io.deq
+  rename.io.ratSnpt.useSnpt := useSnpt
+  rename.io.ratSnpt.snptSelect := snptSelect
+  rename.io.ratSnpt.flushVec := flushVec
 
   val decodeHasException = decode.io.out.map(x => x.bits.exceptionVec.asUInt.orR || (!TriggerAction.isNone(x.bits.trigger)))
   // fusion decoder
@@ -647,17 +646,9 @@ class CtrlBlockImp(
   memCtrl.io.mdpFlodPcVec := mdpFlodPcVec
   memCtrl.io.dispatchLFSTio <> dispatch.io.lfst
 
-  rat.io.hartId := io.fromTop.hartId
-  rat.io.redirect := s1_s3_redirect.valid
-  rat.io.rabCommits := rob.io.rabCommits
-  rat.io.vlCommits := rob.io.vlCommits
-  rat.io.diffCommits.foreach(_ := rob.io.diffCommits.get)
-  rat.io.diffVlCommits.foreach(_ := rob.io.diffVlCommits.get)
-  rat.io.intRenamePorts := rename.io.intRenamePorts
-  rat.io.fpRenamePorts := rename.io.fpRenamePorts
-  rat.io.vecRenamePorts := rename.io.vecRenamePorts
-  rat.io.v0RenamePorts := rename.io.v0RenamePorts
-  rat.io.vlRenamePorts := rename.io.vlRenamePorts
+  rename.io.hartId := io.fromTop.hartId
+  rename.io.ratDiffCommits.foreach(_ := rob.io.diffCommits.get)
+  rename.io.ratDiffVlCommits.foreach(_ := rob.io.diffVlCommits.get)
 
   rename.io.redirect := s1_s3_redirect
   rename.io.rabCommits := rob.io.rabCommits
@@ -671,22 +662,6 @@ class CtrlBlockImp(
   // dispatch.io.lfst.resp := 0.U.asTypeOf(dispatch.io.lfst.resp)
   // rename.io.waittable := 0.U.asTypeOf(rename.io.waittable)
   // rename.io.ssit := 0.U.asTypeOf(rename.io.ssit)
-  rename.io.intReadPorts := VecInit(rat.io.intReadPorts.map(x => VecInit(x.map(_.data))))
-  rename.io.fpReadPorts := VecInit(rat.io.fpReadPorts.map(x => VecInit(x.map(_.data))))
-  rename.io.vecReadPorts := VecInit(rat.io.vecReadPorts.map(x => VecInit(x.map(_.data))))
-  rename.io.v0ReadPorts := VecInit(rat.io.v0ReadPorts.map(x => VecInit(x.data)))
-  rename.io.vlReadPorts := VecInit(rat.io.vlReadPorts.map(x => VecInit(x.data)))
-  rename.io.int_need_free := rat.io.int_need_free
-  rename.io.int_old_pdest := rat.io.int_old_pdest
-  rename.io.fp_old_pdest := rat.io.fp_old_pdest
-  rename.io.vec_old_pdest := rat.io.vec_old_pdest
-  rename.io.v0_old_pdest := rat.io.v0_old_pdest
-  rename.io.vl_old_pdest := rat.io.vl_old_pdest
-  rename.io.debug_int_rat.foreach(_ := rat.io.debug_int_rat.get)
-  rename.io.debug_fp_rat.foreach(_ := rat.io.debug_fp_rat.get)
-  rename.io.debug_vec_rat.foreach(_ := rat.io.debug_vec_rat.get)
-  rename.io.debug_v0_rat.foreach(_ := rat.io.debug_v0_rat.get)
-  rename.io.debug_vl_rat.foreach(_ := rat.io.debug_vl_rat.get)
   rename.io.stallReason.in <> decode.io.stallReason.out
   rename.io.snpt.snptEnq := DontCare
   rename.io.snpt.snptDeq := snpt.io.deq
@@ -811,7 +786,7 @@ class CtrlBlockImp(
   // rob to mem block
   io.robio.lsq <> rob.io.lsq
 
-  io.diff_vl_rat .foreach(_ := rat.io.diff_vl_rat.get)
+  io.diff_vl_rat .foreach(_ := rename.io.diff_vl_rat.get)
 
   rob.io.debug_ls := io.robio.debug_ls
   rob.io.debugHeadLsIssue := io.robio.robHeadLsIssue
@@ -837,29 +812,7 @@ class CtrlBlockImp(
 
   io.toVecExcpMod.logicPhyRegMap := rob.io.toVecExcpMod.logicPhyRegMap
   io.toVecExcpMod.excpInfo       := rob.io.toVecExcpMod.excpInfo
-  // T  : rat receive rabCommit
-  // T+1: rat return oldPdest
-  io.toVecExcpMod.ratOldPest match {
-    case fromRat =>
-      (0 until RabCommitWidth).foreach { idx =>
-        val v0Valid = RegNext(
-          rat.io.rabCommits.isCommit &&
-          rat.io.rabCommits.isWalk &&
-          rat.io.rabCommits.commitValid(idx) &&
-          rat.io.rabCommits.info(idx).v0Wen
-        )
-        fromRat.v0OldVdPdest(idx).valid := RegNext(v0Valid)
-        fromRat.v0OldVdPdest(idx).bits := RegEnable(rat.io.v0_old_pdest(idx), v0Valid)
-        val vecValid = RegNext(
-          rat.io.rabCommits.isCommit &&
-          rat.io.rabCommits.isWalk &&
-          rat.io.rabCommits.commitValid(idx) &&
-          rat.io.rabCommits.info(idx).vecWen
-        )
-        fromRat.vecOldVdPdest(idx).valid := RegNext(vecValid)
-        fromRat.vecOldVdPdest(idx).bits := RegEnable(rat.io.vec_old_pdest(idx), vecValid)
-      }
-  }
+  io.toVecExcpMod.ratOldPest := rename.io.ratOldPdest
 
   io.debugTopDown.fromRob := rob.io.debugTopDown.toCore
   dispatch.io.debugTopDown.fromRob := rob.io.debugTopDown.toDispatch
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 479eaa73a91..51c2f914dd3 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -37,15 +37,23 @@ import xiangshan.backend.trace._
 import xiangshan.backend.decode.isa.bitfield.{OPCODE5Bit, XSInstBitFields}
 import xiangshan.backend.fu.NewCSR.CSROoORead
 import yunsuan.{VfaluType, VipuType, VmoveType}
+import xiangshan.backend.RatToVecExcpMod
 
 class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper with HasPerfEvents {
 
   // params alias
   private val numRegSrc = backendParams.numRegSrc
+  private val numIntRegSrc = backendParams.numIntRegSrc
+  private val numFpRegSrc  = backendParams.numFpRegSrc
   private val numVecRegSrc = backendParams.numVecRegSrc
+  private val numIntRatPorts = numIntRegSrc
+  private val numFpRatPorts  = numFpRegSrc
   private val numVecRatPorts = numVecRegSrc
 
   println(s"[Rename] numRegSrc: $numRegSrc")
+  println(s"[Rename] numIntRegSrc: $numIntRegSrc")
+  println(s"[Rename] numFpRegSrc: $numFpRegSrc")
+  println(s"[Rename] numVecRegSrc: $numVecRegSrc")
 
   val io = IO(new Bundle() {
     val redirect = Flipped(ValidIO(new Redirect))
@@ -64,36 +72,24 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     val ssit = Flipped(Vec(RenameWidth, Output(new SSITEntry)))
     // waittable read result
     val waittable = Flipped(Vec(RenameWidth, Output(Bool())))
-    // to rename table
-    val intReadPorts = Vec(RenameWidth, Vec(2, Input(UInt(PhyRegIdxWidth.W))))
-    val fpReadPorts = Vec(RenameWidth, Vec(3, Input(UInt(PhyRegIdxWidth.W))))
-    val vecReadPorts = Vec(RenameWidth, Vec(numVecRatPorts, Input(UInt(PhyRegIdxWidth.W))))
-    val v0ReadPorts = Vec(RenameWidth, Vec(1, Input(UInt(PhyRegIdxWidth.W))))
-    val vlReadPorts = Vec(RenameWidth, Vec(1, Input(UInt(PhyRegIdxWidth.W))))
-    val intRenamePorts = Vec(RenameWidth, Output(new RatWritePort(log2Ceil(IntLogicRegs))))
-    val fpRenamePorts = Vec(RenameWidth, Output(new RatWritePort(log2Ceil(FpLogicRegs))))
-    val vecRenamePorts = Vec(RenameWidth, Output(new RatWritePort(log2Ceil(VecLogicRegs))))
-    val v0RenamePorts = Vec(RenameWidth, Output(new RatWritePort(log2Ceil(V0LogicRegs))))
-    val vlRenamePorts = Vec(RenameWidth, Output(new RatWritePort(log2Ceil(VlLogicRegs))))
-    // from rename table
-    val int_old_pdest = Vec(RabCommitWidth, Input(UInt(PhyRegIdxWidth.W)))
-    val fp_old_pdest = Vec(RabCommitWidth, Input(UInt(PhyRegIdxWidth.W)))
-    val vec_old_pdest = Vec(RabCommitWidth, Input(UInt(PhyRegIdxWidth.W)))
-    val v0_old_pdest = Vec(RabCommitWidth, Input(UInt(PhyRegIdxWidth.W)))
-    val vl_old_pdest = Vec(RabCommitWidth, Input(UInt(PhyRegIdxWidth.W)))
-    val int_need_free = Vec(RabCommitWidth, Input(Bool()))
+    val intReadPorts = Vec(RenameWidth, Vec(numIntRatPorts, new RatReadPort(log2Ceil(IntLogicRegs))))
+    val fpReadPorts  = Vec(RenameWidth, Vec(numFpRatPorts,  new RatReadPort(log2Ceil(FpLogicRegs))))
+    val vecReadPorts = Vec(RenameWidth, Vec(numVecRatPorts, new RatReadPort(log2Ceil(VecLogicRegs))))
+    val v0ReadPorts  = Vec(RenameWidth, new RatReadPort(log2Ceil(V0LogicRegs)))
+    val vlReadPorts  = Vec(RenameWidth, new RatReadPort(log2Ceil(VlLogicRegs)))
     // to dispatch1
     val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
     // for snapshots
     val snpt = Input(new SnapshotPort)
     val snptLastEnq = Flipped(ValidIO(new RobPtr))
     val snptIsFull= Input(Bool())
-    // debug arch ports
-    val debug_int_rat = if (backendParams.debugEn) Some(Vec(32, Input(UInt(PhyRegIdxWidth.W)))) else None
-    val debug_fp_rat  = if (backendParams.debugEn) Some(Vec(32, Input(UInt(PhyRegIdxWidth.W)))) else None
-    val debug_vec_rat = if (backendParams.debugEn) Some(Vec(31, Input(UInt(PhyRegIdxWidth.W)))) else None
-    val debug_v0_rat  = if (backendParams.debugEn) Some(Vec(1, Input(UInt(PhyRegIdxWidth.W)))) else None
-    val debug_vl_rat  = if (backendParams.debugEn) Some(Vec(1, Input(UInt(PhyRegIdxWidth.W)))) else None
+    // for rat
+    val hartId        = Input(UInt(8.W))
+    val ratOldPdest      = Output(new RatToVecExcpMod)
+    val ratDiffCommits   = Option.when(backendParams.basicDebugEn)(Input(new DiffCommitIO))
+    val ratDiffVlCommits = Option.when(backendParams.basicDebugEn)(Input(new DiffVlCommitBundle(CommitWidth)))
+    val diff_vl_rat      = Option.when(backendParams.basicDebugEn)(Output(Vec(1, UInt(PhyRegIdxWidth.W))))
+    val ratSnpt = Input(new SnapshotPort)
     // perf only
     val stallReason = new Bundle {
       val in = Flipped(new StallReasonIO(RenameWidth))
@@ -118,6 +114,27 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   val v0FreeList = Module(new StdFreeList(V0PhyRegs - V0LogicRegs, V0LogicRegs, Reg_V0, RabCommitWidth, 1))
   val vlFreeList = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, RabCommitWidth, 1))
 
+  val rat = Module(new RenameTableWrapper)
+
+  val intRenamePorts = Wire(Vec(RenameWidth, new RatWritePort(log2Ceil(IntLogicRegs))))
+  val fpRenamePorts  = Wire(Vec(RenameWidth, new RatWritePort(log2Ceil(FpLogicRegs))))
+  val vecRenamePorts = Wire(Vec(RenameWidth, new RatWritePort(log2Ceil(VecLogicRegs))))
+  val v0RenamePorts  = Wire(Vec(RenameWidth, new RatWritePort(log2Ceil(V0LogicRegs))))
+  val vlRenamePorts  = Wire(Vec(RenameWidth, new RatWritePort(log2Ceil(VlLogicRegs))))
+
+  val int_need_free = Wire(Vec(RabCommitWidth, Bool()))
+  val int_old_pdest = Wire(Vec(RabCommitWidth, UInt(PhyRegIdxWidth.W)))
+  val fp_old_pdest  = Wire(Vec(RabCommitWidth, UInt(PhyRegIdxWidth.W)))
+  val vec_old_pdest = Wire(Vec(RabCommitWidth, UInt(PhyRegIdxWidth.W)))
+  val v0_old_pdest  = Wire(Vec(RabCommitWidth, UInt(PhyRegIdxWidth.W)))
+  val vl_old_pdest  = Wire(Vec(RabCommitWidth, UInt(PhyRegIdxWidth.W)))
+
+  // debug arch ports
+  val debug_int_rat = Option.when(backendParams.debugEn)(Wire(Vec(32, UInt(PhyRegIdxWidth.W))))
+  val debug_fp_rat  = Option.when(backendParams.debugEn)(Wire(Vec(32, UInt(PhyRegIdxWidth.W))))
+  val debug_vec_rat = Option.when(backendParams.debugEn)(Wire(Vec(31, UInt(PhyRegIdxWidth.W))))
+  val debug_v0_rat  = Option.when(backendParams.debugEn)(Wire(Vec(1,  UInt(PhyRegIdxWidth.W))))
+  val debug_vl_rat  = Option.when(backendParams.debugEn)(Wire(Vec(1,  UInt(PhyRegIdxWidth.W))))
 
   intFreeList.io.commit match {
     case commit =>
@@ -126,7 +143,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
         case (valid, info) => valid && info.rfWen && !info.isMove
       }
   }
-  intFreeList.io.debug_rat.foreach(_ := io.debug_int_rat.get)
+  intFreeList.io.debug_rat.foreach(_ := debug_int_rat.get)
 
   fpFreeList.io.commit match {
     case commit =>
@@ -136,7 +153,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
           valid && info.fpWen
       }
   }
-  fpFreeList.io.debug_rat.foreach(_ := io.debug_fp_rat.get)
+  fpFreeList.io.debug_rat.foreach(_ := debug_fp_rat.get)
 
   vecFreeList.io.commit match {
     case commit =>
@@ -146,7 +163,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
           valid && info.vecWen
       }
   }
-  vecFreeList.io.debug_rat.foreach(_ := io.debug_vec_rat.get)
+  vecFreeList.io.debug_rat.foreach(_ := debug_vec_rat.get)
 
   v0FreeList.io.commit match {
     case commit =>
@@ -156,14 +173,86 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
           valid && info.v0Wen
       }
   }
-  v0FreeList.io.debug_rat.foreach(_ := io.debug_v0_rat.get)
+  v0FreeList.io.debug_rat.foreach(_ := debug_v0_rat.get)
 
   vlFreeList.io.commit match {
     case commit =>
       commit.doCommit := io.vlCommits.isCommit
       commit.archAlloc := io.vlCommits.commitValid
   }
-  vlFreeList.io.debug_rat.foreach(_ := io.debug_vl_rat.get)
+  vlFreeList.io.debug_rat.foreach(_ := debug_vl_rat.get)
+
+  val intReadPorts = rat.io.intReadPorts
+  val fpReadPorts  = rat.io.fpReadPorts
+  val vecReadPorts = rat.io.vecReadPorts
+  val v0ReadPorts  = rat.io.v0ReadPorts
+  val vlReadPorts  = rat.io.vlReadPorts
+
+  val intReadPortsData = VecInit(intReadPorts.map(x => VecInit(x.map(_.data))))
+  val fpReadPortsData  = VecInit(fpReadPorts.map(x => VecInit(x.map(_.data))))
+  val vecReadPortsData = VecInit(vecReadPorts.map(x => VecInit(x.map(_.data))))
+  val v0ReadPortsData  = VecInit(v0ReadPorts.map(x => VecInit(x.data)))
+  val vlReadPortsData  = VecInit(vlReadPorts.map(x => VecInit(x.data)))
+
+  io.intReadPorts <> intReadPorts
+  io.fpReadPorts  <> fpReadPorts
+  io.vecReadPorts <> vecReadPorts
+  io.v0ReadPorts  <> v0ReadPorts
+  io.vlReadPorts  <> vlReadPorts
+
+  rat.io.snpt <> io.ratSnpt
+
+  rat.io.intRenamePorts := intRenamePorts
+  rat.io.fpRenamePorts  := fpRenamePorts
+  rat.io.vecRenamePorts := vecRenamePorts
+  rat.io.v0RenamePorts  := v0RenamePorts
+  rat.io.vlRenamePorts  := vlRenamePorts
+
+  rat.io.hartId := io.hartId
+  rat.io.redirect := io.redirect.valid
+  rat.io.rabCommits := io.rabCommits
+  rat.io.vlCommits := io.vlCommits
+  rat.io.diffCommits.foreach(_ := io.ratDiffCommits.get)
+  rat.io.diffVlCommits.foreach(_ := io.ratDiffVlCommits.get)
+
+  int_need_free := rat.io.int_need_free
+  int_old_pdest := rat.io.int_old_pdest
+  fp_old_pdest := rat.io.fp_old_pdest
+  vec_old_pdest := rat.io.vec_old_pdest
+  v0_old_pdest := rat.io.v0_old_pdest
+  vl_old_pdest := rat.io.vl_old_pdest
+  
+  debug_int_rat.foreach(_ := rat.io.debug_int_rat.get)
+  debug_fp_rat.foreach (_ := rat.io.debug_fp_rat.get)
+  debug_vec_rat.foreach(_ := rat.io.debug_vec_rat.get)
+  debug_v0_rat.foreach (_ := rat.io.debug_v0_rat.get)
+  debug_vl_rat.foreach (_ := rat.io.debug_vl_rat.get)
+
+  io.diff_vl_rat.foreach(_ := rat.io.diff_vl_rat.get)
+
+  // T  : rat receive rabCommit
+  // T+1: rat return oldPdest
+  io.ratOldPdest match {
+    case fromRat =>
+      (0 until RabCommitWidth).foreach { idx =>
+        val v0Valid = RegNext(
+          rat.io.rabCommits.isCommit &&
+          rat.io.rabCommits.isWalk &&
+          rat.io.rabCommits.commitValid(idx) &&
+          rat.io.rabCommits.info(idx).v0Wen
+        )
+        fromRat.v0OldVdPdest(idx).valid := RegNext(v0Valid)
+        fromRat.v0OldVdPdest(idx).bits  := RegEnable(rat.io.v0_old_pdest(idx), v0Valid)
+        val vecValid = RegNext(
+          rat.io.rabCommits.isCommit &&
+          rat.io.rabCommits.isWalk &&
+          rat.io.rabCommits.commitValid(idx) &&
+          rat.io.rabCommits.info(idx).vecWen
+        )
+        fromRat.vecOldVdPdest(idx).valid := RegNext(vecValid)
+        fromRat.vecOldVdPdest(idx).bits  := RegEnable(rat.io.vec_old_pdest(idx), vecValid)
+      }
+  }
 
   // decide if given instruction needs allocating a new physical register (CfCtrl: from decode; RobCommitInfo: from rob)
   def needDestReg[T <: DecodeOutUop](reg_t: RegType, x: T): Bool = reg_t match {
@@ -431,16 +520,16 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     ).orR
     uops(i).debug.foreach(_.debug_sim_trig := (compressMasksVec(i) & Cat(io.in.map(_.bits.instr === XSDebugDecode.SIM_TRIG).reverse)).orR)
     // psrc0,psrc1,psrc2 don't require v0ReadPorts because their srcType can distinguish whether they are V0 or not
-    uops(i).psrc(0) := Mux1H(uops(i).srcType(0)(2, 0), Seq(io.intReadPorts(i)(0), io.fpReadPorts(i)(0), io.vecReadPorts(i)(0)))
-    uops(i).psrc(1) := Mux1H(uops(i).srcType(1)(2, 0), Seq(io.intReadPorts(i)(1), io.fpReadPorts(i)(1), io.vecReadPorts(i)(1)))
-    uops(i).psrc(2) := Mux1H(uops(i).srcType(2)(2, 1), Seq(io.fpReadPorts(i)(2), io.vecReadPorts(i)(2)))
-    uops(i).psrc(3) := io.v0ReadPorts(i)(0)
-    uops(i).psrcVl := io.vlReadPorts(i).head
+    uops(i).psrc(0) := Mux1H(uops(i).srcType(0)(2, 0), Seq(intReadPortsData(i)(0), fpReadPortsData(i)(0), vecReadPortsData(i)(0)))
+    uops(i).psrc(1) := Mux1H(uops(i).srcType(1)(2, 0), Seq(intReadPortsData(i)(1), fpReadPortsData(i)(1), vecReadPortsData(i)(1)))
+    uops(i).psrc(2) := Mux1H(uops(i).srcType(2)(2, 1), Seq(fpReadPortsData(i)(2), vecReadPortsData(i)(2)))
+    uops(i).psrc(3) := v0ReadPortsData(i)(0)
+    uops(i).psrcVl := vlReadPortsData(i).head
 
     // int psrc2 should be bypassed from next instruction if it is fused
     if (i < RenameWidth - 1) {
       when (io.fusionInfo(i).rs2FromRs2 || io.fusionInfo(i).rs2FromRs1) {
-        uops(i).psrc(1) := Mux(io.fusionInfo(i).rs2FromRs2, io.intReadPorts(i + 1)(1), io.intReadPorts(i + 1)(0))
+        uops(i).psrc(1) := Mux(io.fusionInfo(i).rs2FromRs2, intReadPortsData(i + 1)(1), intReadPortsData(i + 1)(0))
       }.elsewhen(io.fusionInfo(i).rs2FromZero) {
         uops(i).psrc(1) := 0.U
       }
@@ -692,37 +781,37 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
 
     // I. RAT Update
     // When redirect happens (mis-prediction), don't update the rename table
-    io.intRenamePorts(i).wen  := intSpecWen(i)
-    io.intRenamePorts(i).addr := inVec(i).ldest(log2Ceil(IntLogicRegs) - 1, 0)
-    io.intRenamePorts(i).data := io.out(i).bits.pdest
+    intRenamePorts(i).wen  := intSpecWen(i)
+    intRenamePorts(i).addr := inVec(i).ldest(log2Ceil(IntLogicRegs) - 1, 0)
+    intRenamePorts(i).data := io.out(i).bits.pdest
 
-    io.fpRenamePorts(i).wen  := fpSpecWen(i)
-    io.fpRenamePorts(i).addr := inVec(i).ldest(log2Ceil(FpLogicRegs) - 1, 0)
-    io.fpRenamePorts(i).data := fpFreeList.io.allocatePhyReg(i)
+    fpRenamePorts(i).wen  := fpSpecWen(i)
+    fpRenamePorts(i).addr := inVec(i).ldest(log2Ceil(FpLogicRegs) - 1, 0)
+    fpRenamePorts(i).data := fpFreeList.io.allocatePhyReg(i)
 
-    io.vecRenamePorts(i).wen := vecSpecWen(i)
-    io.vecRenamePorts(i).addr := inVec(i).ldest(log2Ceil(VecLogicRegs) - 1, 0)
-    io.vecRenamePorts(i).data := vecFreeList.io.allocatePhyReg(i)
+    vecRenamePorts(i).wen := vecSpecWen(i)
+    vecRenamePorts(i).addr := inVec(i).ldest(log2Ceil(VecLogicRegs) - 1, 0)
+    vecRenamePorts(i).data := vecFreeList.io.allocatePhyReg(i)
 
-    io.v0RenamePorts(i).wen := v0SpecWen(i)
-    io.v0RenamePorts(i).addr := inVec(i).ldest(log2Ceil(V0LogicRegs) - 1, 0)
-    io.v0RenamePorts(i).data := v0FreeList.io.allocatePhyReg(i)
+    v0RenamePorts(i).wen := v0SpecWen(i)
+    v0RenamePorts(i).addr := inVec(i).ldest(log2Ceil(V0LogicRegs) - 1, 0)
+    v0RenamePorts(i).data := v0FreeList.io.allocatePhyReg(i)
 
-    io.vlRenamePorts(i).wen := vlSpecWen(i)
-    io.vlRenamePorts(i).addr := 0.U // only one vl reg
-    io.vlRenamePorts(i).data := vlFreeList.io.allocatePhyReg(i)
+    vlRenamePorts(i).wen := vlSpecWen(i)
+    vlRenamePorts(i).addr := 0.U // only one vl reg
+    vlRenamePorts(i).data := vlFreeList.io.allocatePhyReg(i)
 
     // II. Free List Update
-    intFreeList.io.freeReq(i) := io.int_need_free(i)
-    intFreeList.io.freePhyReg(i) := RegNext(io.int_old_pdest(i))
+    intFreeList.io.freeReq(i) := int_need_free(i)
+    intFreeList.io.freePhyReg(i) := RegNext(int_old_pdest(i))
     fpFreeList.io.freeReq(i)  := GatedValidRegNext(commitValid && needDestRegCommit(Reg_F, io.rabCommits.info(i)))
-    fpFreeList.io.freePhyReg(i) := io.fp_old_pdest(i)
+    fpFreeList.io.freePhyReg(i) := fp_old_pdest(i)
     vecFreeList.io.freeReq(i)  := GatedValidRegNext(commitValid && needDestRegCommit(Reg_V, io.rabCommits.info(i)))
-    vecFreeList.io.freePhyReg(i) := io.vec_old_pdest(i)
+    vecFreeList.io.freePhyReg(i) := vec_old_pdest(i)
     v0FreeList.io.freeReq(i) := GatedValidRegNext(commitValid && needDestRegCommit(Reg_V0, io.rabCommits.info(i)))
-    v0FreeList.io.freePhyReg(i) := io.v0_old_pdest(i)
+    v0FreeList.io.freePhyReg(i) := v0_old_pdest(i)
     vlFreeList.io.freeReq(i) := GatedValidRegNext(io.vlCommits.isCommit && io.vlCommits.commitValid(i))
-    vlFreeList.io.freePhyReg(i) := io.vl_old_pdest(i)
+    vlFreeList.io.freePhyReg(i) := vl_old_pdest(i)
   }
 
   /*
```
