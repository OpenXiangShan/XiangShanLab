# Commit Log
- Issue: #4088
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4088
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4088
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4088
- Changed files: 3
- Additions: 93
- Deletions: 8

## Files
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`
- `src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala`
- `src/main/scala/xiangshan/backend/regfile/Regfile.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index ee386869806..7196d5de98a 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -237,7 +237,7 @@ class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params
   private val fpRfWaddr = Wire(Vec(io.fromFpWb.length, UInt(fpSchdParams.pregIdxWidth.W)))
   private val fpRfWdata = Wire(Vec(io.fromFpWb.length, UInt(fpSchdParams.rfDataWidth.W)))
 
-  private val vfRfSplitNum = VLEN / XLEN
+  private val vfRfSplitNum = 4
   private val vfRfRaddr = Wire(Vec(params.numPregRd(VecData()), UInt(vfSchdParams.pregIdxWidth.W)))
   private val vfRfRdata = Wire(Vec(params.numPregRd(VecData()), UInt(vfSchdParams.rfDataWidth.W)))
   private val vfRfWen = Wire(Vec(vfRfSplitNum, Vec(io.fromVfWb.length, Bool())))
@@ -311,7 +311,7 @@ class DataPathImp(override val wrapper: DataPath)(implicit p: Parameters, params
     debugReadAddr = intDiffRead.map(_._1),
     debugReadData = intDiffRead.map(_._2)
   )
-  FpRegFile("FpRegFile", fpSchdParams.numPregs, fpRfRaddr, fpRfRdata, fpRfWen, fpRfWaddr, fpRfWdata,
+  FpRegFileSplit("FpRegFile", fpSchdParams.numPregs, splitNum = 4, fpRfRaddr, fpRfRdata, fpRfWen, fpRfWaddr, fpRfWdata,
     bankNum = 1,
     debugReadAddr = fpDiffRead.map(_._1),
     debugReadData = fpDiffRead.map(_._2)
diff --git a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
index 3868542ec10..069c3f8551d 100644
--- a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
@@ -575,6 +575,52 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     )
   })
 
+  private val conserveFlowsIs16 = VecInit(isVlsType.zipWithIndex.map { case (isVlsTyepItem, index) =>
+    isVlsTyepItem && !isUnitStride(index)
+  })
+  private val conserveFlowsIs2 = VecInit(isVlsType.zipWithIndex.map { case (isVlsTyepItem, index) =>
+    isVlsTyepItem && isUnitStride(index)
+  })
+  private val conserveFlowsIs1 = VecInit(isLSType.zipWithIndex.map { case (isLSTyepItem, index) =>
+    isLSTyepItem
+  })
+  private val flowTotalWidth = (VecMemLSQEnqIteratorNumberSeq.max * RenameWidth).U.getWidth
+  private val conserveFlowTotalDispatch = Wire(Vec(RenameWidth, UInt(flowTotalWidth.W)))
+  private val lowCountMaxWidth = (2 * RenameWidth).U.getWidth
+  conserveFlowTotalDispatch.zipWithIndex.map{ case (flowTotal, idx) =>
+    val highCount = PopCount(conserveFlowsIs16.take(idx + 1))
+    val conserveFlowsIs2Or1 = VecInit(conserveFlowsIs2.zip(conserveFlowsIs1).map(x => Cat(x._1, x._2)))
+    val lowCount = conserveFlowsIs2Or1.take(idx + 1).reduce(_ +& _).asTypeOf(0.U(lowCountMaxWidth.W))
+    flowTotal := (if (RenameWidth == 6) Cat(highCount, lowCount) else ((highCount << 4).asUInt + lowCount))
+  }
+  // renameIn
+  private val isVlsTypeRename = io.renameIn.map(x => x.valid && FuType.isVls(x.bits.fuType))
+  private val isLSTypeRename = io.renameIn.map(x => x.valid && (FuType.isLoad(x.bits.fuType)) || FuType.isStore(x.bits.fuType))
+  private val isUnitStrideRename = io.renameIn.map(x => LSUOpType.isAllUS(x.bits.fuOpType))
+  private val conserveFlowsIs16Rename = VecInit(isVlsTypeRename.zipWithIndex.map { case (isVlsTyepItem, index) =>
+    isVlsTyepItem && !isUnitStrideRename(index)
+  })
+  private val conserveFlowsIs2Rename = VecInit(isVlsTypeRename.zipWithIndex.map { case (isVlsTyepItem, index) =>
+    isVlsTyepItem && isUnitStrideRename(index)
+  })
+  private val conserveFlowsIs1Rename = VecInit(isLSTypeRename.zipWithIndex.map { case (isLSTyepItem, index) =>
+    isLSTyepItem
+  })
+  private val conserveFlowTotalRename = Wire(Vec(RenameWidth, UInt(flowTotalWidth.W)))
+  conserveFlowTotalRename.zipWithIndex.map { case (flowTotal, idx) =>
+    val highCount = PopCount(conserveFlowsIs16Rename.take(idx + 1))
+    val conserveFlowsIs2Or1 = VecInit(conserveFlowsIs2Rename.zip(conserveFlowsIs1Rename).map(x => Cat(x._1, x._2)))
+    val lowCount = conserveFlowsIs2Or1.take(idx + 1).reduce(_ +& _).asTypeOf(0.U(lowCountMaxWidth.W))
+    flowTotal := (if (RenameWidth == 6) Cat(highCount, lowCount) else ((highCount << 4).asUInt + lowCount))
+  }
+
+
+  private val conserveFlowTotal = Reg(Vec(RenameWidth, UInt(flowTotalWidth.W)))
+  when(io.toRenameAllFire){
+    conserveFlowTotal := conserveFlowTotalRename
+  }.otherwise(
+    conserveFlowTotal := conserveFlowTotalDispatch
+  )
   // A conservative allocation strategy is adopted here.
   // Vector 'unit-stride' instructions and scalar instructions can be issued from all six ports,
   // while other vector instructions can only be issued from the first port
@@ -586,14 +632,12 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
 
 
   for (index <- allowDispatch.indices) {
-    val flowTotal = Wire(UInt(log2Up(VirtualLoadQueueMaxStoreQueueSize + 1).W))
-    flowTotal := conserveFlows.take(index + 1).reduce(_ +& _)
+    val flowTotal = conserveFlowTotal(index)
     val allowDispatchPrevious = if (index == 0) true.B else allowDispatch(index - 1)
-    val allowDispatchThisUop = true.B
     when(isStoreVec(index) || isVStoreVec(index)) {
-      allowDispatch(index) := (sqFreeCount > flowTotal) && allowDispatchThisUop && allowDispatchPrevious
+      allowDispatch(index) := (sqFreeCount > flowTotal) && allowDispatchPrevious
     }.elsewhen(isLoadVec(index) || isVLoadVec(index)) {
-      allowDispatch(index) := (lqFreeCount > flowTotal) && allowDispatchThisUop && allowDispatchPrevious
+      allowDispatch(index) := (lqFreeCount > flowTotal) && allowDispatchPrevious
     }.elsewhen(isAMOVec(index)) {
       allowDispatch(index) := allowDispatchPrevious
     }.otherwise {
diff --git a/src/main/scala/xiangshan/backend/regfile/Regfile.scala b/src/main/scala/xiangshan/backend/regfile/Regfile.scala
index 05a4c05d37e..df4132f536c 100644
--- a/src/main/scala/xiangshan/backend/regfile/Regfile.scala
+++ b/src/main/scala/xiangshan/backend/regfile/Regfile.scala
@@ -286,6 +286,47 @@ object FpRegFile {
   }
 }
 
+object FpRegFileSplit {
+  // non-return version
+  def apply(
+             name         : String,
+             numEntries   : Int,
+             splitNum     : Int,
+             raddr        : Seq[UInt],
+             rdata        : Vec[UInt],
+             wen          : Seq[Bool],
+             waddr        : Seq[UInt],
+             wdata        : Seq[UInt],
+             debugReadAddr: Option[Seq[UInt]],
+             debugReadData: Option[Vec[UInt]],
+             withReset    : Boolean = false,
+             bankNum      : Int,
+             isVlRegfile  : Boolean = false,
+           )(implicit p: Parameters): Unit = {
+    require(Seq(1, 2, 4, 8).contains(splitNum))
+    val rdataVec = Wire(Vec(splitNum, Vec(rdata.length, UInt((rdata.head.getWidth / splitNum).W))))
+    rdata.zipWithIndex.map{ case (r, i) =>
+      r := Cat((0 until splitNum).map(x => rdataVec(x)(i)).reverse)
+    }
+    val debugReadDataVec = OptionWrapper(debugReadData.nonEmpty, Wire(Vec(splitNum, Vec(debugReadData.get.length, UInt((debugReadData.get.head.getWidth / splitNum).W)))))
+    if (debugReadData.nonEmpty) {
+      debugReadData.get.zipWithIndex.map { case (r, i) =>
+        r := Cat((0 until splitNum).map(x => debugReadDataVec.get(x)(i)).reverse)
+      }
+    }
+    for (i <- 0 until splitNum){
+      val wdataThisPart = wdata.map { case x =>
+        val widthThisPart = x.getWidth / splitNum
+        x((i + 1) * widthThisPart - 1, i * widthThisPart)
+      }
+      val nameSuffix = if (splitNum > 1) s"Part${i}" else ""
+      Regfile(
+        name + nameSuffix, numEntries, raddr, rdataVec(i), wen, waddr, wdataThisPart,
+        hasZero = false, withReset, bankNum, debugReadAddr, OptionWrapper(debugReadData.nonEmpty, debugReadDataVec.get(i)), isVlRegfile)
+    }
+  }
+}
+
 object VfRegFile {
   // non-return version
   def apply(
@@ -308,7 +349,7 @@ object VfRegFile {
         name, numEntries, raddr, rdata, wen.head, waddr, wdata,
         hasZero = false, withReset, bankNum = 1, debugReadAddr, debugReadData)
     } else {
-      val dataWidth = 64
+      val dataWidth = wdata.head.getWidth / splitNum
       val numReadPorts = raddr.length
       require(splitNum > 1 && wdata.head.getWidth == dataWidth * splitNum)
       val wdataVec = Wire(Vec(splitNum, Vec(wdata.length, UInt(dataWidth.W))))
```
