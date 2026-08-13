# Commit Log
- Issue: #4198
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4198
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4198
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4198
- Changed files: 3
- Additions: 46
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/Backend.scala`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Backend.scala b/src/main/scala/xiangshan/backend/Backend.scala
index 1798710f711..561afed80fb 100644
--- a/src/main/scala/xiangshan/backend/Backend.scala
+++ b/src/main/scala/xiangshan/backend/Backend.scala
@@ -297,6 +297,10 @@ class BackendInlinedImp(override val wrapper: BackendInlined)(implicit p: Parame
     x._1.valid := x._2.wen && x._2.vlWen
     x._1.bits := x._2.addr
   })
+  ctrlBlock.io.toDispatch.vlWriteBackInfo.vlFromIntIsZero := vlFromIntIsZero
+  ctrlBlock.io.toDispatch.vlWriteBackInfo.vlFromIntIsVlmax := vlFromIntIsVlmax
+  ctrlBlock.io.toDispatch.vlWriteBackInfo.vlFromVfIsZero := vlFromVfIsZero
+  ctrlBlock.io.toDispatch.vlWriteBackInfo.vlFromVfIsVlmax := vlFromVfIsVlmax
   ctrlBlock.io.csrCtrl <> intExuBlock.io.csrio.get.customCtrl
   ctrlBlock.io.robio.csr.intrBitSet := intExuBlock.io.csrio.get.interrupt
   ctrlBlock.io.robio.csr.trapTarget := intExuBlock.io.csrio.get.trapTarget
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 6d3aed5674c..87022aacd9d 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -726,6 +726,7 @@ class CtrlBlockImp(
   dispatch.io.wbPregsVec := io.toDispatch.wbPregsVec
   dispatch.io.wbPregsV0 := io.toDispatch.wbPregsV0
   dispatch.io.wbPregsVl := io.toDispatch.wbPregsVl
+  dispatch.io.vlWriteBackInfo := io.toDispatch.vlWriteBackInfo
   dispatch.io.robHeadNotReady := rob.io.headNotReady
   dispatch.io.robFull := rob.io.robFull
   dispatch.io.singleStep := GatedValidRegNext(io.csrCtrl.singlestep)
@@ -892,6 +893,12 @@ class CtrlBlockIO()(implicit p: Parameters, params: BackendParams) extends XSBun
     val wbPregsVec = Vec(backendParams.numPregWb(VecData()), Flipped(ValidIO(UInt(PhyRegIdxWidth.W))))
     val wbPregsV0 = Vec(backendParams.numPregWb(V0Data()), Flipped(ValidIO(UInt(PhyRegIdxWidth.W))))
     val wbPregsVl = Vec(backendParams.numPregWb(VlData()), Flipped(ValidIO(UInt(PhyRegIdxWidth.W))))
+    val vlWriteBackInfo = new Bundle {
+      val vlFromIntIsZero  = Input(Bool())
+      val vlFromIntIsVlmax = Input(Bool())
+      val vlFromVfIsZero   = Input(Bool())
+      val vlFromVfIsVlmax  = Input(Bool())
+    }
   }
   val toDataPath = new Bundle {
     val flush = ValidIO(new Redirect)
diff --git a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
index 4ed7fc9a98b..5ba5722421c 100644
--- a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
@@ -27,7 +27,7 @@ import xiangshan._
 import xiangshan.backend.rob.{RobDispatchTopDownIO, RobEnqIO}
 import xiangshan.backend.Bundles.{DecodedInst, DynInst, ExuVec, IssueQueueIQWakeUpBundle}
 import xiangshan.backend.fu.{FuConfig, FuType}
-import xiangshan.backend.rename.BusyTable
+import xiangshan.backend.rename.{BusyTable, VlBusyTable}
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.backend.rename.BusyTableReadIO
 import xiangshan.backend.datapath.DataConfig._
@@ -116,6 +116,13 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     }
     val og0Cancel = Input(ExuVec())
     val ldCancel = Vec(backendParams.LdExuCnt, Flipped(new LoadCancelIO))
+    // to vlbusytable
+    val vlWriteBackInfo = new Bundle {
+      val vlFromIntIsZero  = Input(Bool())
+      val vlFromIntIsVlmax = Input(Bool())
+      val vlFromVfIsZero   = Input(Bool())
+      val vlFromVfIsVlmax  = Input(Bool())
+    }
     // from MemBlock
     val fromMem = new Bundle {
       val lcommit = Input(UInt(log2Up(CommitWidth + 1).W))
@@ -221,7 +228,8 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
   val fpBusyTable = Module(new BusyTable(numRegSrcFp * renameWidth, backendParams.numPregWb(FpData()), FpPhyRegs, FpWB()))
   val vecBusyTable = Module(new BusyTable(numRegSrcVf * renameWidth, backendParams.numPregWb(VecData()), VfPhyRegs, VfWB()))
   val v0BusyTable = Module(new BusyTable(numRegSrcV0 * renameWidth, backendParams.numPregWb(V0Data()), V0PhyRegs, V0WB()))
-  val vlBusyTable = Module(new BusyTable(numRegSrcVl * renameWidth, backendParams.numPregWb(VlData()), VlPhyRegs, VlWB()))
+  val vlBusyTable = Module(new VlBusyTable(numRegSrcVl * renameWidth, backendParams.numPregWb(VlData()), VlPhyRegs, VlWB()))
+  vlBusyTable.io_vl_Wb.vlWriteBackInfo := io.vlWriteBackInfo
   val busyTables = Seq(intBusyTable, fpBusyTable, vecBusyTable, v0BusyTable, vlBusyTable)
   val wbPregs = Seq(io.wbPregsInt, io.wbPregsFp, io.wbPregsVec, io.wbPregsV0, io.wbPregsVl)
   val idxRegType = Seq(idxRegTypeInt, idxRegTypeFp, idxRegTypeVec, idxRegTypeV0, idxRegTypeVl)
@@ -296,6 +304,29 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     }
   }
 
+  // eliminate old vd
+  val ignoreOldVdVec = Wire(Vec(renameWidth, Bool()))
+  for (i <- 0 until renameWidth){
+    // numRegSrcVf - 1 is old vd
+    var j = numRegSrcVf - 1
+    // 2 is type of vec
+    var k = 2
+    val readidx = i * idxRegType(k).size + idxRegType(k).indexOf(j)
+    val readEn = SrcType.isVp(fromRename(i).bits.srcType(j))
+    val isDependOldVd = fromRename(i).bits.vpu.isDependOldVd
+    val isWritePartVd = fromRename(i).bits.vpu.isWritePartVd
+    val vta = fromRename(i).bits.vpu.vta
+    val vma = fromRename(i).bits.vpu.vma
+    val vm = fromRename(i).bits.vpu.vm
+    val vlIsVlmax = vlBusyTable.io_vl_read.vlReadInfo(i).is_vlmax
+    val vlIsNonZero = !vlBusyTable.io_vl_read.vlReadInfo(i).is_zero
+    val ignoreTail = vlIsVlmax && (vm =/= 0.U || vma) && !isWritePartVd
+    val ignoreWhole = (vm =/= 0.U || vma) && vta
+    val ignoreOldVd = vlBusyTable.io.read(i).resp && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
+    ignoreOldVdVec(i) := readEn && ignoreOldVd
+    allSrcState(i)(j)(k) := readEn && (busyTables(k).io.read(readidx).resp || ignoreOldVd) || SrcType.isImm(fromRename(i).bits.srcType(j))
+  }
+
   // Singlestep should only commit one machine instruction after dret, and then hart enter debugMode according to singlestep exception.
   val s_holdRobidx :: s_updateRobidx :: Nil = Enum(2)
   val singleStepState = RegInit(s_updateRobidx)
@@ -412,6 +443,8 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     fromRenameUpdate(i).valid := fromRename(i).valid && allowDispatch(i) && !uopBlockByIQ(i) && thisCanActualOut(i) &&
       lsqCanAccept && !fromRename(i).bits.eliminatedMove && !fromRename(i).bits.hasException && !fromRenameUpdate(i).bits.singleStep
     fromRename(i).ready := allowDispatch(i) && !uopBlockByIQ(i) && thisCanActualOut(i) && lsqCanAccept
+    // update src type if eliminate old vd
+    fromRenameUpdate(i).bits.srcType(numRegSrcVf - 1) := Mux(ignoreOldVdVec(i), SrcType.no, fromRename(i).bits.srcType(numRegSrcVf - 1))
   }
   for (i <- 0 until RenameWidth){
     // check is drop amocas sta
```
