# Commit Log
- Issue: #4063
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4063
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4063
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4063
- Changed files: 8
- Additions: 40
- Deletions: 9

## Files
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnit.scala`
- `src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala`
- `src/main/scala/xiangshan/backend/exu/ExuBlock.scala`
- `src/main/scala/xiangshan/backend/fu/FuConfig.scala`
- `src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala`
- `src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index d60ae05603a..eae7563c729 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -593,10 +593,11 @@ object Bundles {
   }
 
   // DataPath --[ExuInput]--> Exu
-  class ExuInput(val params: ExeUnitParams, copyWakeupOut:Boolean = false, copyNum:Int = 0)(implicit p: Parameters) extends XSBundle {
+  class ExuInput(val params: ExeUnitParams, copyWakeupOut:Boolean = false, copyNum:Int = 0, hasCopySrc: Boolean = false)(implicit p: Parameters) extends XSBundle {
     val fuType        = FuType()
     val fuOpType      = FuOpType()
     val src           = Vec(params.numRegSrc, UInt(params.srcDataBitsMax.W))
+    val copySrc       = if(hasCopySrc) Some(Vec(params.numCopySrc, Vec(if(params.numRegSrc < 2) 1 else 2, UInt(params.srcDataBitsMax.W)))) else None
     val imm           = UInt(64.W)
     val nextPcOffset  = OptionWrapper(params.hasBrhFu, UInt((log2Up(PredictWidth) + 1).W))
     val robIdx        = new RobPtr
diff --git a/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala b/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
index 9e157c23371..b156b6124e3 100644
--- a/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
+++ b/src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala
@@ -11,6 +11,7 @@ import xiangshan.backend.issue.{FpScheduler, ImmExtractor, IntScheduler, MemSche
 import xiangshan.backend.datapath.DataConfig.RegDataMaxWidth
 import xiangshan.backend.decode.ImmUnion
 import xiangshan.backend.regcache._
+import xiangshan.backend.Bundles._
 import xiangshan.backend.fu.FuType
 
 class BypassNetworkIO()(implicit p: Parameters, params: BackendParams) extends XSBundle {
@@ -38,10 +39,10 @@ class BypassNetworkIO()(implicit p: Parameters, params: BackendParams) extends X
   }
 
   class ToExus extends Bundle {
-    val int: MixedVec[MixedVec[DecoupledIO[ExuInput]]] = intSchdParams.genExuInputBundle
-    val fp : MixedVec[MixedVec[DecoupledIO[ExuInput]]] = fpSchdParams.genExuInputBundle
-    val vf : MixedVec[MixedVec[DecoupledIO[ExuInput]]] = vfSchdParams.genExuInputBundle
-    val mem: MixedVec[MixedVec[DecoupledIO[ExuInput]]] = memSchdParams.genExuInputBundle
+    val int: MixedVec[MixedVec[DecoupledIO[ExuInput]]] = intSchdParams.genExuInputCopySrcBundle
+    val fp : MixedVec[MixedVec[DecoupledIO[ExuInput]]] = fpSchdParams.genExuInputCopySrcBundle
+    val vf : MixedVec[MixedVec[DecoupledIO[ExuInput]]] = vfSchdParams.genExuInputCopySrcBundle
+    val mem: MixedVec[MixedVec[DecoupledIO[ExuInput]]] = memSchdParams.genExuInputCopySrcBundle
   }
 
   class FromExus extends Bundle {
@@ -130,7 +131,9 @@ class BypassNetwork()(implicit p: Parameters, params: BackendParams) extends XSM
   println(s"[BypassNetwork] HasBypass2SinkExu: ${fromDPsHasBypass2Sink}")
 
   toExus.zip(fromDPs).foreach { case (sink, source) =>
-    sink <> source
+    connectSamePort(sink.bits, source.bits)
+    sink.valid := source.valid
+    source.ready := sink.ready
   }
 
   toExus.zipWithIndex.foreach { case (exuInput, exuIdx) =>
@@ -184,6 +187,9 @@ class BypassNetwork()(implicit p: Parameters, params: BackendParams) extends XSM
       exuInput.bits.imm := immBJU
       exuInput.bits.nextPcOffset.get := nextPcOffset
     }
+    exuInput.bits.copySrc.get.map( copysrc =>
+      copysrc.zip(exuInput.bits.src).foreach{ case(copy, src) => copy := src}
+    )
   }
 
   // to reg cache
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
index 6e7b54ea9f6..dabe2e3bf48 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnit.scala
@@ -33,7 +33,7 @@ import xiangshan.backend.fu.wrapper.{CSRInput, CSRToDecode}
 
 class ExeUnitIO(params: ExeUnitParams)(implicit p: Parameters) extends XSBundle {
   val flush = Flipped(ValidIO(new Redirect()))
-  val in = Flipped(DecoupledIO(new ExuInput(params)))
+  val in = Flipped(DecoupledIO(new ExuInput(params, hasCopySrc = true)))
   val out = DecoupledIO(new ExuOutput(params))
   val csrin = Option.when(params.hasCSR)(new CSRInput)
   val csrio = Option.when(params.hasCSR)(new CSRFileIO)
@@ -245,7 +245,6 @@ class ExeUnitImp(
       sink.valid := source.valid
       source.ready := sink.ready
 
-      sink.bits.data.src.zip(source.bits.src).foreach { case(fuSrc, exuSrc) => fuSrc := exuSrc }
       sink.bits.data.pc          .foreach(x => x := source.bits.pc.get)
       sink.bits.data.nextPcOffset.foreach(x => x := source.bits.nextPcOffset.get)
       sink.bits.data.imm         := source.bits.imm
@@ -302,6 +301,13 @@ class ExeUnitImp(
     }
   }
 
+  funcUnits.zip(exuParams.idxCopySrc).map{ case(fu, idx) =>
+    (fu.io.in.bits.data.src).zip(io.in.bits.src).foreach { case(fuSrc, exuSrc) => fuSrc := exuSrc }
+    if(fu.cfg.srcNeedCopy) {
+      (fu.io.in.bits.data.src).zip(io.in.bits.copySrc.get(idx)).foreach { case(fuSrc, copySrc) => fuSrc := copySrc }
+    }
+  }
+
   private val OutresVecs = funcUnits.map { fu =>
     def latDiff :Int = fu.cfg.latency.extraLatencyVal.getOrElse(0)
     val OutresVec = fu.io.out.bits.res +: Seq.fill(latDiff)(Reg(chiselTypeOf(fu.io.out.bits.res)))
diff --git a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
index 62877662b2b..dad2bf93bba 100644
--- a/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExeUnitParams.scala
@@ -90,6 +90,11 @@ case class ExeUnitParams(
   def needReadRegCache: Boolean = isIntExeUnit || isMemExeUnit && readIntRf
   def needWriteRegCache: Boolean = isIntExeUnit && isIQWakeUpSource || isMemExeUnit && isIQWakeUpSource && readIntRf
 
+  def numCopySrc: Int = fuConfigs.map(x => if(x.srcNeedCopy) 1 else 0).reduce(_ + _)
+  def idxCopySrc: Seq[Int] = (0 until fuConfigs.length).map { idx =>
+    fuConfigs.take(idx + 1).map(x => if(x.srcNeedCopy) 1 else 0).reduce(_ + _) - 1
+  }
+
   // exu writeback: 0 normalout; 1 intout; 2 fpout; 3 vecout
   val wbNeedIntWen : Boolean = writeIntRf && !isMemExeUnit
   val wbNeedFpWen  : Boolean = writeFpRf  && !isMemExeUnit
@@ -427,6 +432,10 @@ case class ExeUnitParams(
     new ExuInput(this)
   }
 
+  def genExuInputCopySrcBundle(implicit p: Parameters): ExuInput = {
+    new ExuInput(this, hasCopySrc = true)
+  }
+
   def genExuOutputBundle(implicit p: Parameters): ExuOutput = {
     new ExuOutput(this)
   }
diff --git a/src/main/scala/xiangshan/backend/exu/ExuBlock.scala b/src/main/scala/xiangshan/backend/exu/ExuBlock.scala
index beea16a1c02..d7dfb764001 100644
--- a/src/main/scala/xiangshan/backend/exu/ExuBlock.scala
+++ b/src/main/scala/xiangshan/backend/exu/ExuBlock.scala
@@ -71,7 +71,7 @@ class ExuBlockImp(
 class ExuBlockIO(implicit p: Parameters, params: SchdBlockParams) extends XSBundle {
   val flush = Flipped(ValidIO(new Redirect))
   // in(i)(j): issueblock(i), exu(j)
-  val in: MixedVec[MixedVec[DecoupledIO[ExuInput]]] = Flipped(params.genExuInputBundle)
+  val in: MixedVec[MixedVec[DecoupledIO[ExuInput]]] = Flipped(params.genExuInputCopySrcBundle)
   // out(i)(j): issueblock(i), exu(j).
   val out: MixedVec[MixedVec[DecoupledIO[ExuOutput]]] = params.genExuOutputDecoupledBundle
 
diff --git a/src/main/scala/xiangshan/backend/fu/FuConfig.scala b/src/main/scala/xiangshan/backend/fu/FuConfig.scala
index 52fc0414f73..3aac56371fe 100644
--- a/src/main/scala/xiangshan/backend/fu/FuConfig.scala
+++ b/src/main/scala/xiangshan/backend/fu/FuConfig.scala
@@ -60,6 +60,7 @@ case class FuConfig (
   writeVxsat    : Boolean = false,
   destDataBits  : Int = 64,
   srcDataBits   : Option[Int] = None,
+  srcNeedCopy   : Boolean = false,
   latency       : HasFuLatency = CertainLatency(0),// two field (base latency, extra latency(option))
   hasInputBuffer: (Boolean, Int, Boolean) = (false, 0, false),
   exceptionOut  : Seq[Int] = Seq(),
diff --git a/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala b/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
index ff87e9ab70f..cc8f4d8217d 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala
@@ -333,6 +333,10 @@ case class IssueBlockParams(
     MixedVec(this.exuBlockParams.map(x => DecoupledIO(x.genExuInputBundle)))
   }
 
+  def genExuInputDecoupledCopySrcBundle(implicit p: Parameters): MixedVec[DecoupledIO[ExuInput]] = {
+    MixedVec(this.exuBlockParams.map(x => DecoupledIO(x.genExuInputCopySrcBundle)))
+  }
+
   def genExuOutputDecoupledBundle(implicit p: Parameters): MixedVec[DecoupledIO[ExuOutput]] = {
     MixedVec(this.exuParams.map(x => DecoupledIO(x.genExuOutputBundle)))
   }
diff --git a/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala b/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala
index 4934d55d316..d3299827245 100644
--- a/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala
+++ b/src/main/scala/xiangshan/backend/issue/SchdBlockParams.scala
@@ -133,6 +133,10 @@ case class SchdBlockParams(
     MixedVec(this.issueBlockParams.map(_.genExuInputDecoupledBundle))
   }
 
+  def genExuInputCopySrcBundle(implicit p: Parameters): MixedVec[MixedVec[DecoupledIO[ExuInput]]] = {
+    MixedVec(this.issueBlockParams.map(_.genExuInputDecoupledCopySrcBundle))
+  }
+
   def genExuOutputDecoupledBundle(implicit p: Parameters): MixedVec[MixedVec[DecoupledIO[ExuOutput]]] = {
     MixedVec(this.issueBlockParams.map(_.genExuOutputDecoupledBundle))
   }
```
