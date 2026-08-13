# Commit Log
- Issue: #4361
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4361
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4361
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4361
- Changed files: 5
- Additions: 37
- Deletions: 6

## Files
- `difftest`
- `ready-to-run`
- `src/main/scala/xiangshan/backend/datapath/WbArbiter.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index 07fb4300947..62e4540c03c 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit 07fb4300947d1685305cb6a597145c724d9ae29a
+Subproject commit 62e4540c03c03aa947bdb67f4b6bf66f1a29c5d0
diff --git a/ready-to-run b/ready-to-run
index d6e86e64cc0..8c943ff751d 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit d6e86e64cc0658ab93678c40e3eac1add7cdaf9e
+Subproject commit 8c943ff751d2aa2ea2c708170264698788d6546d
diff --git a/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala b/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala
index e6f0394e294..156bb8c9005 100644
--- a/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala
+++ b/src/main/scala/xiangshan/backend/datapath/WbArbiter.scala
@@ -3,7 +3,7 @@ package xiangshan.backend.datapath
 import org.chipsalliance.cde.config.Parameters
 import chisel3._
 import chisel3.util._
-import difftest.{DiffFpWriteback, DiffIntWriteback, DiffVecWriteback, DifftestModule}
+import difftest.{DiffFpWriteback, DiffIntWriteback, DiffVecV0Writeback, DiffVecWriteback, DifftestModule}
 import utility.XSError
 import xiangshan.backend.BackendParams
 import xiangshan.backend.Bundles.{ExuOutput, WriteBackBundle}
@@ -415,7 +415,19 @@ class WbDataPath(params: BackendParams)(implicit p: Parameters) extends XSModule
       difftest.coreid := io.fromTop.hartId
       difftest.valid := out.fire
       difftest.address := out.bits.pdest
-      difftest.data := out.bits.data
+      difftest.data(0) := out.bits.data(63, 0)
+      difftest.data(1) := out.bits.data(127, 64)
+    })
+  }
+
+  if (env.EnableDifftest || env.AlwaysBasicDiff) {
+    v0WbArbiterOut.foreach(out => {
+      val difftest = DifftestModule(new DiffVecV0Writeback(V0PhyRegs))
+      difftest.coreid := io.fromTop.hartId
+      difftest.valid := out.fire
+      difftest.address := out.bits.pdest
+      difftest.data(0) := out.bits.data(63, 0)
+      difftest.data(1) := out.bits.data(127, 64)
     })
   }
 }
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index e0dd42bdb0b..d86ce8d4e0e 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -36,6 +36,7 @@ import xiangshan._
 import xiangshan.backend.GPAMemEntry
 import xiangshan.backend.{BackendParams, RatToVecExcpMod, RegWriteFromRab, VecExcpInfo}
 import xiangshan.backend.Bundles.{DynInst, ExceptionInfo, ExuOutput}
+import xiangshan.backend.decode.isa.bitfield.XSInstBitFields
 import xiangshan.backend.fu.{FuConfig, FuType}
 import xiangshan.frontend.FtqPtr
 import xiangshan.mem.{LqPtr, LsqEnqIO, SqPtr}
@@ -132,6 +133,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
   val exuWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback.filter(!_.bits.params.hasStdFu).toSeq
   val stdWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback.filter(_.bits.params.hasStdFu).toSeq
+  val vldWBs: Seq[ValidIO[ExuOutput]] = io.exuWriteback.filter(_.bits.params.hasVLoadFu).toSeq
   val fflagsWBs = io.exuWriteback.filter(x => x.bits.fflags.nonEmpty).toSeq
   val exceptionWBs = io.writeback.filter(x => x.bits.exceptionVec.nonEmpty).toSeq
   val redirectWBs = io.writeback.filter(x => x.bits.redirect.nonEmpty).toSeq
@@ -1451,6 +1453,16 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     }
   }
 
+  val debug_VecOtherPdest = RegInit(VecInit.fill(RobSize)(VecInit.fill(8)(0.U(PhyRegIdxWidth.W))))
+
+  vldWBs.map{ vldWb =>
+    val vldWbPdest  = vldWb.bits.pdest
+    val vldWbRobIdx = vldWb.bits.robIdx.value
+    val vldWbvdIdx  = vldWb.bits.vls.get.vdIdx
+    when (vldWb.fire && robEntries(vldWbRobIdx).valid && (vldWb.bits.vecWen.get || vldWb.bits.v0Wen.get)) {
+      debug_VecOtherPdest(vldWbRobIdx)(vldWbvdIdx) := vldWbPdest
+    }
+  }
 
   //difftest signals
   val firstValidCommit = (deqPtr + PriorityMux(io.commits.commitValid, VecInit(List.tabulate(CommitWidth)(_.U(log2Up(CommitWidth).W))))).value
@@ -1489,6 +1501,8 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       val exuOut = dt_exuDebug(ptr)
       val eliminatedMove = dt_eliminatedMove(ptr)
       val isRVC = dt_isRVC(ptr)
+      val instr = uop.instr.asTypeOf(new XSInstBitFields)
+      val isVLoad = instr.isVecLoad
 
       val difftest = DifftestModule(new DiffInstrCommit(MaxPhyRegs), delay = 3, dontCare = true)
       val dt_skip = Mux(eliminatedMove, false.B, exuOut.isSkipDiff)
@@ -1499,8 +1513,11 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       difftest.isRVC := isRVC
       difftest.rfwen := io.commits.commitValid(i) && commitInfo.rfWen && commitInfo.debug_ldest.get =/= 0.U
       difftest.fpwen := io.commits.commitValid(i) && uop.fpWen
+      difftest.vecwen := io.commits.commitValid(i) && uop.vecWen
+      difftest.v0wen := io.commits.commitValid(i) && (uop.v0Wen || isVLoad && instr.VD === 0.U)
       difftest.wpdest := commitInfo.debug_pdest.get
-      difftest.wdest := commitInfo.debug_ldest.get
+      difftest.wdest := Mux(isVLoad, instr.VD, commitInfo.debug_ldest.get)
+      difftest.otherwpdest := debug_VecOtherPdest(ptr)
       difftest.nFused := CommitType.isFused(commitInfo.commitType).asUInt + commitInfo.instrSize - 1.U
       when(difftest.valid) {
         assert(CommitType.isFused(commitInfo.commitType).asUInt + commitInfo.instrSize >= 1.U)
@@ -1518,12 +1535,13 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
         val difftestLoadEvent = DifftestModule(new DiffLoadEvent, delay = 3)
         difftestLoadEvent.coreid := io.hartId
         difftestLoadEvent.index := i.U
-        val loadCheck = (FuType.isAMO(uop.fuType) || FuType.isLoad(uop.fuType)) && !dt_skip
+        val loadCheck = (FuType.isAMO(uop.fuType) || FuType.isLoad(uop.fuType) || isVLoad) && !dt_skip
         difftestLoadEvent.valid    := io.commits.commitValid(i) && io.commits.isCommit && loadCheck
         difftestLoadEvent.paddr    := exuOut.paddr
         difftestLoadEvent.opType   := uop.fuOpType
         difftestLoadEvent.isAtomic := FuType.isAMO(uop.fuType)
         difftestLoadEvent.isLoad   := FuType.isLoad(uop.fuType)
+        difftestLoadEvent.isVLoad  := isVLoad
       }
     }
   }
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 769e54b49b9..33dc6666c83 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -119,6 +119,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val debug_instr = OptionWrapper(backendParams.debugEn, UInt(32.W))
     val debug_ldest = OptionWrapper(backendParams.basicDebugEn, UInt(LogicRegsWidth.W))
     val debug_pdest = OptionWrapper(backendParams.basicDebugEn, UInt(PhyRegIdxWidth.W))
+    val debug_otherPdest = OptionWrapper(backendParams.basicDebugEn, Vec(7, UInt(PhyRegIdxWidth.W)))
     val debug_fuType = OptionWrapper(backendParams.debugEn, FuType())
     // debug_end
     val dirtyFs = Bool()
```
