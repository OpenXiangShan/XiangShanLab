# Commit Log
- Issue: #5340
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5340
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5340
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5340
- Changed files: 6
- Additions: 107
- Deletions: 49

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/TopDownGen.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index 389d38dc9a7..7c2bda1bb1c 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -795,6 +795,12 @@ class TopDownFromL2Top(implicit p: Parameters) extends XSBundle {
   val l3Miss = Bool()
 }
 
+class UopTopDown(implicit p: Parameters) extends XSBundle {
+  val uopsIssued = Output(Bool())
+  val uopsIssuedCnt = Output(UInt((log2Up(p(XSCoreParamsKey).backendParams.allIssueParams.size)).W))
+  val noStoreIssued = Output(Bool())
+}
+
 class LowPowerIO(implicit p: Parameters) extends Bundle {
   /* i_*: SoC -> CPU   o_*: CPU -> SoC */
   val o_cpu_no_op = Output(Bool()) 
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 8805e215ca7..1198331de74 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -297,6 +297,11 @@ case class XSCoreParameters
 
   def vlWidth = log2Up(VLEN) + 1
 
+  /* 
+    Top-Down, ExecutionStall used
+  */
+  def fewUops = 4
+
   /**
    * the minimum element length of vector elements
    */
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index a5997b012cb..c26a20f7d29 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -181,6 +181,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   private val fpRegion = Module(new Region(params.fpSchdParams.get))
   private val vecRegion = Module(new Region(params.vecSchdParams.get))
   private val vecExcpMod = Module(new VecExcpDataMergeModule)
+  private val topDownMod = Module(new TopDownGen)
 
 
   private val vlFromIntIsZero = intRegion.io.vlWriteBackInfoOut.vlFromIntIsZero
@@ -507,7 +508,22 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
 
   io.debugRolling := ctrlBlock.io.debugRolling
 
-  io.topDownInfo.noUopsIssued := false.B
+  // Top-Down
+  topDownMod.io.intTopDown.uopsIssued    := intRegion.io.uopTopDown.uopsIssued
+  topDownMod.io.intTopDown.uopsIssuedCnt := intRegion.io.uopTopDown.uopsIssuedCnt
+  topDownMod.io.intTopDown.noStoreIssued := intRegion.io.uopTopDown.noStoreIssued
+  topDownMod.io.fpTopDown.uopsIssued     := fpRegion.io.uopTopDown.uopsIssued
+  topDownMod.io.fpTopDown.uopsIssuedCnt  := fpRegion.io.uopTopDown.uopsIssuedCnt
+  topDownMod.io.fpTopDown.noStoreIssued  := fpRegion.io.uopTopDown.noStoreIssued
+  topDownMod.io.vecTopDown.uopsIssued    := vecRegion.io.uopTopDown.uopsIssued
+  topDownMod.io.vecTopDown.uopsIssuedCnt := vecRegion.io.uopTopDown.uopsIssuedCnt
+  topDownMod.io.vecTopDown.noStoreIssued := vecRegion.io.uopTopDown.noStoreIssued
+  topDownMod.io.topDownInfo.lqEmpty := DelayN(io.topDownInfo.lqEmpty, 2)
+  topDownMod.io.topDownInfo.sqEmpty := DelayN(io.topDownInfo.sqEmpty, 2)
+  topDownMod.io.topDownInfo.l1Miss  := RegNext(io.topDownInfo.l1Miss)
+  topDownMod.io.topDownInfo.l2TopMiss.l2Miss := io.topDownInfo.l2TopMiss.l2Miss
+  topDownMod.io.topDownInfo.l2TopMiss.l3Miss := io.topDownInfo.l2TopMiss.l3Miss
+  io.topDownInfo.noUopsIssued := RegNext(topDownMod.io.topDownInfo.noUopsIssued)
 
   private val cg = ClockGate.genTeSrc
   dontTouch(cg)
@@ -521,6 +537,7 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
     val rightResetTree = ResetGenNode(Seq(
       ModuleNode(intRegion),
       ModuleNode(fpRegion),
+      ModuleNode(topDownMod)
     ))
     val leftResetTree = ResetGenNode(Seq(
       ModuleNode(vecRegion),
@@ -544,10 +561,12 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
   val csrevents = pfevent.io.hpmevent.slice(8,16)
 
   val ctrlBlockPerf    = ctrlBlock.getPerfEvents
+  
+  val topDownPerf = topDownMod.getPerfEvents
 
   val perfBackend  = Seq()
   // let index = 0 be no event
-  val allPerfEvents = Seq(("noEvent", 0.U)) ++ ctrlBlockPerf ++ perfBackend
+  val allPerfEvents = Seq(("noEvent", 0.U)) ++ ctrlBlockPerf ++ topDownPerf ++ perfBackend
 
 
   if (printEventCoding) {
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index b191b64e21c..9104e9df000 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -328,11 +328,6 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
   dataPath.io.fromBypassNetwork := 0.U.asTypeOf(dataPath.io.fromBypassNetwork)
   dataPath.io.fromPcTargetMem.toDataPathTargetPC := 0.U.asTypeOf(dataPath.io.fromPcTargetMem.toDataPathTargetPC)
   dataPath.io.fromPcTargetMem.toDataPathPC := 0.U.asTypeOf(dataPath.io.fromPcTargetMem.toDataPathPC)
-  dataPath.io.topDownInfo.lqEmpty := false.B
-  dataPath.io.topDownInfo.sqEmpty := false.B
-  dataPath.io.topDownInfo.l1Miss := false.B
-  dataPath.io.topDownInfo.l2TopMiss.l2Miss := false.B
-  dataPath.io.topDownInfo.l2TopMiss.l3Miss := false.B
 
   bypassNetwork.io.fromDataPath.int.foreach(x => x.foreach{ xx =>
       xx.valid := false.B
@@ -731,6 +726,10 @@ class Region(val params: SchdBlockParams)(implicit p: Parameters) extends XSModu
   io.fpRfRdataOut.foreach(_ := dataPath.io.fpRfRdataOut.get)
   dataPath.io.fpRfRdataIn.foreach(_ := io.fpRfRdataIn.get)
 
+  io.uopTopDown.uopsIssued := dataPath.io.uopTopDown.uopsIssued
+  io.uopTopDown.uopsIssuedCnt := dataPath.io.uopTopDown.uopsIssuedCnt
+  io.uopTopDown.noStoreIssued := dataPath.io.uopTopDown.noStoreIssued
+
   // perf counter
   if (params.isIntSchd) {
     val iqNum = issueQueues.size
@@ -879,5 +878,7 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
   val fpIQOut = Option.when(params.isFpSchd)(MixedVec(params.issueBlockParams.map(_.genIssueDecoupledBundle)))
   val fromFpIQ = Option.when(params.isIntSchd)(Flipped(MixedVec(fpSchdParam.issueBlockParams.map(_.genIssueDecoupledBundle))))
   val intToFpIQResp = Option.when(params.isIntSchd)(MixedVec(fpSchdParam.issueBlockParams.map(_.genOGRespBundle)))
+  // TopDown
+  val uopTopDown = new UopTopDown
 }
 
diff --git a/src/main/scala/xiangshan/backend/TopDownGen.scala b/src/main/scala/xiangshan/backend/TopDownGen.scala
new file mode 100644
index 00000000000..5f9a1cdac14
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/TopDownGen.scala
@@ -0,0 +1,60 @@
+package xiangshan.backend
+
+import org.chipsalliance.cde.config.Parameters
+import chisel3._
+import chisel3.util._
+import xiangshan._
+import utility._
+
+class TopDownGen(implicit p: Parameters) extends XSModule
+  with HasPerfEvents {
+  val io = IO(new TopDownGenIO)
+
+  val uopsIssued = io.intTopDown.uopsIssued || io.fpTopDown.uopsIssued || io.vecTopDown.uopsIssued
+  val uopsIssuedCnt = io.intTopDown.uopsIssuedCnt + io.vecTopDown.uopsIssuedCnt
+  val noStoreIssued = io.intTopDown.noStoreIssued
+
+  val fewUopsIssued = (0 until p(XSCoreParamsKey).fewUops).map(_.U === uopsIssuedCnt).reduce(_ || _)
+
+  val stallLoad = !uopsIssued
+  val stallStore = uopsIssued && noStoreIssued
+
+  val stallLoadDly2 = DelayN(stallLoad, 2)
+  val stallStoreDly2 = DelayN(stallStore, 2)
+
+  val lqEmpty = io.topDownInfo.lqEmpty
+  val sqEmpty = io.topDownInfo.sqEmpty
+  val l1Miss = io.topDownInfo.l1Miss
+  val l2Miss = io.topDownInfo.l2TopMiss.l2Miss
+  val l3Miss = io.topDownInfo.l2TopMiss.l3Miss
+
+  val memStallAnyLoad = stallLoadDly2 && !lqEmpty
+  val memStallStore = stallStoreDly2 && !sqEmpty
+  val memStallL1Miss = memStallAnyLoad && l1Miss
+  val memStallL2Miss = memStallL1Miss && l2Miss
+  val memStallL3Miss = memStallL2Miss && l3Miss
+
+  io.topDownInfo.noUopsIssued := stallLoad
+  
+  XSPerfAccumulate("exec_stall_cycle",   fewUopsIssued)
+  XSPerfAccumulate("mem_stall_store",    memStallStore)
+  XSPerfAccumulate("mem_stall_l1miss",   memStallL1Miss)
+  XSPerfAccumulate("mem_stall_l2miss",   memStallL2Miss)
+  XSPerfAccumulate("mem_stall_l3miss",   memStallL3Miss)
+
+  val perfEvents = Seq(
+    ("EXEC_STALL_CYCLE",  fewUopsIssued),
+    ("MEMSTALL_STORE",    memStallStore),
+    ("MEMSTALL_L1MISS",   memStallL1Miss),
+    ("MEMSTALL_L2MISS",   memStallL2Miss),
+    ("MEMSTALL_L3MISS",   memStallL3Miss),
+  )
+  generatePerfEvent()
+}
+
+class TopDownGenIO(implicit p: Parameters) extends XSBundle {
+  val intTopDown = Flipped(new UopTopDown)
+  val fpTopDown  = Flipped(new UopTopDown)
+  val vecTopDown = Flipped(new UopTopDown)
+  val topDownInfo = new TopDownInfo
+}
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 148b1ccd499..117f588c4dd 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -24,7 +24,7 @@ import xiangshan.backend.fu.FuType.isUncertain
 import xiangshan.mem.{LqPtr, SqPtr}
 
 class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockParams)
-  extends XSModule with HasXSParameter with HasPerfEvents {
+  extends XSModule with HasXSParameter {
 
   val io = IO(new DataPathIO())
 
@@ -805,50 +805,17 @@ class DataPath(implicit p: Parameters, params: BackendParams, param: SchdBlockPa
   })
 
   // Top-Down
-  def FewUops = 4
-
-  val lqEmpty = io.topDownInfo.lqEmpty
-  val sqEmpty = io.topDownInfo.sqEmpty
-  val l1Miss = io.topDownInfo.l1Miss
-  val l2Miss = io.topDownInfo.l2TopMiss.l2Miss
-  val l3Miss = io.topDownInfo.l2TopMiss.l3Miss
-
-  val uopsIssued = fromIQ.flatten.map(_.fire).reduce(_ || _)
-  val uopsIssuedCnt = PopCount(fromIQ.flatten.map(_.fire))
-  val fewUopsIssued = (0 until FewUops).map(_.U === uopsIssuedCnt).reduce(_ || _)
-
-  val stallLoad = !uopsIssued
+  val IQsFire = fromFlattenIQ.map(_.fire)
+  val uopsIssued = IQsFire.reduce(_ || _)  
+  val uopsIssuedCnt = PopCount(IQsFire)
 
   val noStoreIssued = !fromIntIQ.flatten.filter(memIq => memIq.bits.exuParams.fuConfigs.contains(FuConfig.StaCfg) ||
                                                          memIq.bits.exuParams.fuConfigs.contains(FuConfig.StdCfg)
   ).map(_.fire).reduce(_ || _)
-  val stallStore = uopsIssued && noStoreIssued
-
-  val stallLoadReg = DelayN(stallLoad, 2)
-  val stallStoreReg = DelayN(stallStore, 2)
-
-  val memStallAnyLoad = stallLoadReg && !lqEmpty
-  val memStallStore = stallStoreReg && !sqEmpty
-  val memStallL1Miss = memStallAnyLoad && l1Miss
-  val memStallL2Miss = memStallL1Miss && l2Miss
-  val memStallL3Miss = memStallL2Miss && l3Miss
-
-  io.topDownInfo.noUopsIssued := stallLoad
-
-  XSPerfAccumulate("exec_stall_cycle",   fewUopsIssued)
-  XSPerfAccumulate("mem_stall_store",    memStallStore)
-  XSPerfAccumulate("mem_stall_l1miss",   memStallL1Miss)
-  XSPerfAccumulate("mem_stall_l2miss",   memStallL2Miss)
-  XSPerfAccumulate("mem_stall_l3miss",   memStallL3Miss)
-
-  val perfEvents = Seq(
-    ("EXEC_STALL_CYCLE",  fewUopsIssued),
-    ("MEMSTALL_STORE",    memStallStore),
-    ("MEMSTALL_L1MISS",   memStallL1Miss),
-    ("MEMSTALL_L2MISS",   memStallL2Miss),
-    ("MEMSTALL_L3MISS",   memStallL3Miss),
-  )
-  generatePerfEvent()
+
+  io.uopTopDown.uopsIssued := uopsIssued
+  io.uopTopDown.uopsIssuedCnt := uopsIssuedCnt
+  io.uopTopDown.noStoreIssued := noStoreIssued
 }
 
 class DataPathIO()(implicit p: Parameters, params: BackendParams, param: SchdBlockParams) extends XSBundle {
@@ -932,5 +899,5 @@ class DataPathIO()(implicit p: Parameters, params: BackendParams, param: SchdBlo
   val diffVlRat  = if (params.basicDebugEn && param.isVecSchd) Some(Input(Vec(1, UInt(log2Up(VlPhyRegs).W)))) else None
   val diffVl     = if (params.basicDebugEn && param.isVecSchd) Some(Output(UInt(VlData().dataWidth.W))) else None
 
-  val topDownInfo = new TopDownInfo
+  val uopTopDown = new UopTopDown
 }
```
