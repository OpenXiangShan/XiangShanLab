# Commit Log
- Issue: #4944
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4944
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4944
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4944
- Changed files: 5
- Additions: 19
- Deletions: 36

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index dc8976a65f8..6dc30b90eaa 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -142,15 +142,15 @@ class CtrlBlockImp(
       (if (x.bits.trigger.nonEmpty) TriggerAction.isDmode(x.bits.trigger.get) else false.B)
   }
 
-  val wbDataNoStd = io.fromWB.wbData.filter(!_.bits.params.hasStdFu)
+  val wbData = io.fromWB.wbData
   val intScheWbData = io.fromWB.wbData.filter(_.bits.params.schdType.isInstanceOf[IntScheduler])
   val fpScheWbData = io.fromWB.wbData.filter(_.bits.params.schdType.isInstanceOf[FpScheduler])
   val vfScheWbData = io.fromWB.wbData.filter(_.bits.params.schdType.isInstanceOf[VfScheduler])
-  val staScheWbData = io.fromWB.wbData.filter(_.bits.params.hasStoreAddrFu)
+  val storeWbData = io.fromWB.wbData.filter(_.bits.params.hasStoreFu)
   val i2vWbData = intScheWbData.filter(_.bits.params.writeVecRf)
   val f2vWbData = fpScheWbData.filter(_.bits.params.writeVecRf)
   val memVloadWbData = io.fromWB.wbData.filter(x => x.bits.params.schdType.isInstanceOf[MemScheduler] && x.bits.params.hasVLoadFu)
-  private val delayedNotFlushedWriteBackNums = wbDataNoStd.map(x => {
+  private val delayedNotFlushedWriteBackNums = wbData.map(x => {
     val valid = x.valid
     val killedByOlder = x.bits.robIdx.needFlush(Seq(s1_s3_redirect, s2_s4_redirect, s3_s5_redirect))
     val delayed = Wire(Valid(UInt(io.fromWB.wbData.size.U.getWidth.W)))
@@ -161,7 +161,7 @@ class CtrlBlockImp(
     val isMemVload = memVloadWbData.contains(x)
     val isi2v = i2vWbData.contains(x)
     val isf2v = f2vWbData.contains(x)
-    val isStaSche = staScheWbData.contains(x)
+    val isStore = storeWbData.contains(x)
     val canSameRobidxWbData = if(isVfSche) {
       i2vWbData ++ f2vWbData ++ vfScheWbData
     } else if(isi2v) {
@@ -172,8 +172,8 @@ class CtrlBlockImp(
       intScheWbData ++ fpScheWbData
     } else if (isFpSche) {
       intScheWbData ++ fpScheWbData
-//    } else if (isStaSche) {
-//      intScheWbData ++ fpScheWbData ++ staScheWbData
+    } else if (isStore) {
+      storeWbData
     } else if (isMemVload) {
       memVloadWbData
     } else {
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index c96e8b45f91..5927aa360ea 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -844,7 +844,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   decodedInst.uopIdx := 0.U
   decodedInst.firstUop := true.B
   decodedInst.lastUop := true.B
-  decodedInst.numWB   := 1.U
+  decodedInst.numWB   := Mux(FuType.isStore(decodedInst.fuType), 2.U, 1.U)
 
   val isZimop = (BitPat("b1?00??0111??_?????_100_?????_1110011") === ctrl_flow.instr) ||
                 (BitPat("b1?00??1?????_?????_100_?????_1110011") === ctrl_flow.instr)
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
index a4e3bf2c4fc..db733b4a757 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
@@ -302,6 +302,8 @@ case class ExeUnitParams(
 
   def hasStdFu = fuConfigs.map(_.name == "std").reduce(_ || _)
 
+  def hasStoreFu = hasStoreAddrFu || hasStdFu
+
   def hasMemAddrFu = hasLoadFu || hasStoreAddrFu || hasVLoadFu || hasHyldaFu || hasHystaFu || hasVLoadFu || hasVStoreFu
 
   def hasHyldaFu = fuConfigs.map(_.name == "hylda").reduce(_ || _)
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index d4673445c74..326a6638706 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -71,7 +71,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     // exu + brq
     val writeback: MixedVec[ValidIO[ExuOutput]] = Flipped(params.genWrite2CtrlBundles)
     val exuWriteback: MixedVec[ValidIO[ExuOutput]] = Flipped(params.genWrite2CtrlBundles)
-    val writebackNums = Flipped(Vec(writeback.size - params.StdCnt, ValidIO(UInt(writeback.size.U.getWidth.W))))
+    val writebackNums = Flipped(Vec(writeback.size, ValidIO(UInt(writeback.size.U.getWidth.W))))
     val writebackNeedFlush = Input(Vec(params.allExuParams.filter(_.needExceptionGen).length, Bool()))
     val commits = Output(new RobCommitIO)
     val trace = new Bundle {
@@ -137,8 +137,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     })
   })
 
-  val exuWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback.filter(!_.bits.params.hasStdFu).toSeq
-  val stdWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback.filter(_.bits.params.hasStdFu).toSeq
+  val exuWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback
   val vldWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback.filter(_.bits.params.hasVLoadFu).toSeq
   val fflagsWBs = io.exuWriteback.filter(x => x.bits.fflags.nonEmpty).toSeq
   val exceptionWBs = io.writeback.filter(x => x.bits.exceptionVec.nonEmpty).toSeq
@@ -155,10 +154,9 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   }
 
   val numExuWbPorts = exuWBs.length
-  val numStdWbPorts = stdWBs.length
   val bankAddrWidth = log2Up(CommitWidth)
 
-  println(s"Rob: size $RobSize, numExuWbPorts: $numExuWbPorts, numStdWbPorts: $numStdWbPorts, commitwidth: $CommitWidth")
+  println(s"Rob: size $RobSize, numExuWbPorts: $numExuWbPorts, commitwidth: $CommitWidth")
 
   val rab = Module(new RenameBuffer(RabSize))
   val vtypeBuffer = Module(new VTypeBuffer(VTypeBufferSize))
@@ -1031,7 +1029,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val enqWriteStd = PriorityMux(instCanEnqSeq, enqWriteStdVec)
 
     val canWbSeq = exuWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U)
-    val canStdWbSeq = VecInit(stdWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U))
     val wbCnt = Mux1H(canWbSeq, io.writebackNums.map(_.bits))
 
     val canWbExceptionSeq = exceptionWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U)
@@ -1045,18 +1042,13 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     when(robEntries(i).valid && (needFlush || needFlushWriteBack)) {
       // exception flush
       robEntries(i).uopNum := robEntries(i).uopNum - wbCnt
-      robEntries(i).stdWritebacked := true.B
     }.elsewhen(!robEntries(i).valid && instCanEnqFlag) {
       // enq set num of uops
       robEntries(i).uopNum := enqWBNum
-      robEntries(i).stdWritebacked := Mux(enqWriteStd, false.B, true.B)
     }.elsewhen(robEntries(i).valid) {
       // update by writing back
       robEntries(i).uopNum := robEntries(i).uopNum - wbCnt
       assert(!(robEntries(i).uopNum - wbCnt > robEntries(i).uopNum), s"robEntries $i uopNum is overflow!")
-      when(canStdWbSeq.asUInt.orR) {
-        robEntries(i).stdWritebacked := true.B
-      }
     }
 
     val fflagsCanWbSeq = fflags_wb.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U && writeback.bits.wflags.getOrElse(false.B))
@@ -1104,7 +1096,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val enqWriteStd = PriorityMux(instCanEnqSeq, enqWriteStdVec)
 
     val canWbSeq = exuWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === needUpdateRobIdx(i))
-    val canStdWbSeq = VecInit(stdWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === needUpdateRobIdx(i)))
     val wbCnt = Mux1H(canWbSeq, io.writebackNums.map(_.bits))
 
     val canWbExceptionSeq = exceptionWBs.map(writeback => writeback.valid && (writeback.bits.robIdx.value === needUpdateRobIdx(i)))
@@ -1118,17 +1109,12 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     when(needUpdate(i).valid && (needFlush || needFlushWriteBack)) {
       // exception flush
       needUpdate(i).uopNum := robBanksRdata(i).uopNum - wbCnt
-      needUpdate(i).stdWritebacked := true.B
     }.elsewhen(!needUpdate(i).valid && instCanEnqFlag) {
       // enq set num of uops
       needUpdate(i).uopNum := enqWBNum
-      needUpdate(i).stdWritebacked := Mux(enqWriteStd, false.B, true.B)
     }.elsewhen(needUpdate(i).valid) {
       // update by writing back
       needUpdate(i).uopNum := robBanksRdata(i).uopNum - wbCnt
-      when(canStdWbSeq.asUInt.orR) {
-        needUpdate(i).stdWritebacked := true.B
-      }
     }
 
     val fflagsCanWbSeq = fflags_wb.map(writeback => writeback.valid && writeback.bits.robIdx.value === needUpdateRobIdx(i) && writeback.bits.wflags.getOrElse(false.B))
@@ -1349,7 +1335,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   XSPerfHistogram("walkTotalCycleHist", walkCycle, state === s_walk && state_next === s_idle, 0, 32)
 
   private val deqNotWritebacked = robEntries(deqPtr.value).valid && !robEntries(deqPtr.value).isWritebacked
-  private val deqStdNotWritebacked = robEntries(deqPtr.value).valid && !robEntries(deqPtr.value).stdWritebacked
   private val deqUopNotWritebacked = robEntries(deqPtr.value).valid && !robEntries(deqPtr.value).isUopWritebacked
   private val deqHeadInfo = debug_microOp(deqPtr.value)
   val deqUopCommitType = debug_microOp(deqPtr.value).commitType
@@ -1364,20 +1349,18 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   XSPerfAccumulate("waitBkuCycle", deqNotWritebacked && deqHeadInfo.fuType === FuType.bku.U)
   XSPerfAccumulate("waitLduCycle", deqNotWritebacked && deqHeadInfo.fuType === FuType.ldu.U)
   XSPerfAccumulate("waitStuCycle", deqNotWritebacked && deqHeadInfo.fuType === FuType.stu.U)
-  XSPerfAccumulate("waitStaCycle", deqUopNotWritebacked && deqHeadInfo.fuType === FuType.stu.U)
-  XSPerfAccumulate("waitStdCycle", deqStdNotWritebacked && deqHeadInfo.fuType === FuType.stu.U)
-  XSPerfAccumulate("waitAtmCycle", deqStdNotWritebacked && deqHeadInfo.fuType === FuType.mou.U)
+  XSPerfAccumulate("waitAtmCycle", deqNotWritebacked && deqHeadInfo.fuType === FuType.mou.U)
 
-  XSPerfAccumulate("waitVfaluCycle", deqStdNotWritebacked && deqHeadInfo.fuType === FuType.vfalu.U)
-  XSPerfAccumulate("waitVfmaCycle", deqStdNotWritebacked && deqHeadInfo.fuType === FuType.vfma.U)
-  XSPerfAccumulate("waitVfdivCycle", deqStdNotWritebacked && deqHeadInfo.fuType === FuType.vfdiv.U)
+  XSPerfAccumulate("waitVfaluCycle", deqNotWritebacked && deqHeadInfo.fuType === FuType.vfalu.U)
+  XSPerfAccumulate("waitVfmaCycle" , deqNotWritebacked && deqHeadInfo.fuType === FuType.vfma.U)
+  XSPerfAccumulate("waitVfdivCycle", deqNotWritebacked && deqHeadInfo.fuType === FuType.vfdiv.U)
 
   val vfalufuop = Seq(VfaluType.vfadd, VfaluType.vfwadd, VfaluType.vfwadd_w, VfaluType.vfsub, VfaluType.vfwsub, VfaluType.vfwsub_w, VfaluType.vfmin, VfaluType.vfmax,
     VfaluType.vfmerge, VfaluType.vfmv, VfaluType.vfsgnj, VfaluType.vfsgnjn, VfaluType.vfsgnjx, VfaluType.vfeq, VfaluType.vfne, VfaluType.vflt, VfaluType.vfle, VfaluType.vfgt,
     VfaluType.vfge, VfaluType.vfclass, VfaluType.vfmv_f_s, VfaluType.vfmv_s_f, VfaluType.vfredusum, VfaluType.vfredmax, VfaluType.vfredmin, VfaluType.vfredosum, VfaluType.vfwredosum)
 
   vfalufuop.zipWithIndex.map{
-    case(fuoptype,i) =>  XSPerfAccumulate(s"waitVfalu_${i}Cycle", deqStdNotWritebacked && deqHeadInfo.fuOpType === fuoptype && deqHeadInfo.fuType === FuType.vfalu.U)
+    case(fuoptype,i) =>  XSPerfAccumulate(s"waitVfalu_${i}Cycle", deqNotWritebacked && deqHeadInfo.fuOpType === fuoptype && deqHeadInfo.fuType === FuType.vfalu.U)
   }
 
 
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 0aa9581db1d..e66f7ba44b7 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -68,8 +68,6 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val valid = Bool()
     val fflags = UInt(5.W)
     val mmio = Bool()
-    // store will be commited if both sta & std have been writebacked
-    val stdWritebacked = Bool()
     val vxsat = Bool()
     val realDestSize = UInt(log2Up(MaxUopSize + 1).W)
     val uopNum = UInt(log2Up(MaxUopSize + 1).W)
@@ -86,7 +84,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val debug_fusionNum = OptionWrapper(backendParams.debugEn, UInt(2.W))
     // debug_end
 
-    def isWritebacked: Bool = !uopNum.orR && stdWritebacked
+    def isWritebacked: Bool = !uopNum.orR
     def isUopWritebacked: Bool = !uopNum.orR
 
   }
@@ -155,7 +153,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
   def connectCommitEntry(robCommitEntry: RobCommitEntryBundle, robEntry: RobEntryBundle): Unit = {
     robCommitEntry.walk_v := robEntry.valid
     robCommitEntry.commit_v := robEntry.valid
-    robCommitEntry.commit_w := (robEntry.uopNum === 0.U) && (robEntry.stdWritebacked === true.B)
+    robCommitEntry.commit_w := robEntry.uopNum === 0.U
     robCommitEntry.realDestSize := robEntry.realDestSize
     robCommitEntry.interrupt_safe := robEntry.interrupt_safe
     robCommitEntry.rfWen := robEntry.rfWen
```
