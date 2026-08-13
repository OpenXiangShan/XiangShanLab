# Commit Log
- Issue: #3418
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3418
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3418
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3418
- Changed files: 6
- Additions: 248
- Deletions: 1

## Files
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`
- `src/main/scala/xiangshan/backend/trace/Interface.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index 9d1059fceba..135eaae1e06 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -533,6 +533,10 @@ case class XSCoreParameters
     ),
     iqWakeUpParams,
   )
+
+  // Parameters for trace extension.
+  // Trace parameters is useful for XSTOP.
+  val TraceGroupNum          = 3 // Width to Encoder
 }
 
 case object DebugOptionsKey extends Field[DebugOptions]
@@ -854,4 +858,7 @@ trait HasXSParameter {
   // Parameters for Sdtrig extension
   protected def TriggerNum = 4
   protected def TriggerChainMaxLength = 2
+
+  // Parameters for Trace extension
+  def TraceGroupNum          = coreParams.TraceGroupNum
 }
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index c8655403539..d4ba6c4622c 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -22,6 +22,7 @@ import xiangshan.backend.rob.RobPtr
 import xiangshan.frontend._
 import xiangshan.mem.{LqPtr, SqPtr}
 import yunsuan.vector.VIFuParam
+import xiangshan.backend.trace._
 
 object Bundles {
   /**
@@ -195,6 +196,7 @@ object Bundles {
     val instrSize       = UInt(log2Ceil(RenameWidth + 1).W)
     val dirtyFs         = Bool()
     val dirtyVs         = Bool()
+    val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))
 
     val eliminatedMove  = Bool()
     // Take snapshot at this CFI inst
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 858c4cfe1e3..94b355b715f 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -32,6 +32,7 @@ import xiangshan.ExceptionNO._
 import xiangshan.backend.fu.FuType._
 import xiangshan.mem.{EewLog2, GenUSWholeEmul}
 import xiangshan.mem.GenRealFlowNum
+import xiangshan.backend.trace._
 
 class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHelper with HasPerfEvents {
 
@@ -192,6 +193,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     uop.hasException  :=  DontCare
     uop.useRegCache   := DontCare
     uop.regCacheIdx   := DontCare
+    uop.traceBlockInPipe := DontCare
   })
   private val fuType       = uops.map(_.fuType)
   private val fuOpType     = uops.map(_.fuOpType)
@@ -402,6 +404,46 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     }
   }
 
+  /**
+   * trace begin
+   */
+  val inVec = io.in.map(_.bits)
+  val canRobCompressVec = inVec.map(_.canRobCompress)
+  val isRVCVec = inVec.map(_.preDecodeInfo.isRVC)
+  val halfWordNumVec = (0 until RenameWidth).map{
+    i => compressMasksVec(i).asBools.zip(isRVCVec).map{
+      case (mask, isRVC) => Mux(mask, Mux(isRVC, 1.U, 2.U), 0.U)
+    }
+  }
+
+  for (i <- 0 until RenameWidth) {
+    // iretire
+    uops(i).traceBlockInPipe.iretire := Mux(canRobCompressVec(i),
+      halfWordNumVec(i).reduce(_ +& _),
+      Mux(isRVCVec(i), 1.U, 2.U)
+    )
+
+    // ilastsize
+    val j = i
+    val lastIsRVC = WireInit(false.B)
+    (j until RenameWidth).map { j =>
+      when(compressMasksVec(i)(j)) {
+        lastIsRVC := io.in(j).bits.preDecodeInfo.isRVC
+      }
+    }
+
+    uops(i).traceBlockInPipe.ilastsize := Mux(canRobCompressVec(i),
+      Mux(lastIsRVC, Ilastsize.HalfWord, Ilastsize.Word),
+      Mux(isRVCVec(i), Ilastsize.HalfWord, Ilastsize.Word)
+    )
+
+    // itype
+    uops(i).traceBlockInPipe.itype := Itype.jumpTypeGen(inVec(i).preDecodeInfo.brType, inVec(i).ldest.asTypeOf(new OpRegType), inVec(i).lsrc(0).asTypeOf((new OpRegType)))
+  }
+  /**
+   * trace end
+   */
+
   /**
     * How to set psrc:
     * - bypass the pdest to psrc if previous instructions write to the same ldest as lsrc
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 05cdced67ac..59c8652da28 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -35,6 +35,7 @@ import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.rename.SnapshotGenerator
 import yunsuan.VfaluType
 import xiangshan.backend.rob.RobBundles._
+import xiangshan.backend.trace._
 
 class Rob(params: BackendParams)(implicit p: Parameters) extends LazyModule with HasXSParameter {
   override def shouldBeInlined: Boolean = false
@@ -106,6 +107,8 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val exceptionWBs = io.writeback.filter(x => x.bits.exceptionVec.nonEmpty).toSeq
   val redirectWBs = io.writeback.filter(x => x.bits.redirect.nonEmpty).toSeq
   val vxsatWBs = io.exuWriteback.filter(x => x.bits.vxsat.nonEmpty).toSeq
+  val branchWBs = io.exuWriteback.filter(_.bits.params.hasBrhFu).toSeq
+  val csrWBs = io.exuWriteback.filter(x => x.bits.params.hasCSR).toSeq
 
   val numExuWbPorts = exuWBs.length
   val numStdWbPorts = stdWBs.length
@@ -197,6 +200,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     robBanksRaddrNextLine := robBanksRaddrThisLine
   )
   val robDeqGroup = Reg(Vec(bankNum, new RobCommitEntryBundle))
+  val rawInfo = VecInit((0 until CommitWidth).map(i => robDeqGroup(deqPtrVec(i).value(bankAddrWidth-1, 0)))).toSeq
   val commitInfo = VecInit((0 until CommitWidth).map(i => robDeqGroup(deqPtrVec(i).value(bankAddrWidth-1,0)))).toSeq
   val walkInfo = VecInit((0 until CommitWidth).map(i => robDeqGroup(walkPtrVec(i).value(bankAddrWidth-1, 0)))).toSeq
   for (i <- 0 until CommitWidth) {
@@ -205,6 +209,16 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       connectCommitEntry(robDeqGroup(i), robBanksRdataNextLineUpdate(i))
     }
   }
+  
+  // In each robentry, the ftqIdx and ftqOffset belong to the first instruction that was compressed, 
+  // that is Necessary when exceptions happen.
+  // Update the ftqIdx and ftqOffset to correctly notify the frontend which instructions have been committed.
+  for (i <- 0 until CommitWidth) {
+    val lastOffset = (rawInfo(i).traceBlockInPipe.iretire - (1.U << rawInfo(i).traceBlockInPipe.ilastsize.asUInt)) +& rawInfo(i).ftqOffset
+    commitInfo(i).ftqIdx := rawInfo(i).ftqIdx + lastOffset.head(1)
+    commitInfo(i).ftqOffset := lastOffset.tail(1)
+  }
+
   // data for debug
   // Warn: debug_* prefix should not exist in generated verilog.
   val debug_microOp = DebugMem(RobSize, new DynInst)
@@ -951,6 +965,17 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
     val vxsatCanWbSeq = vxsat_wb.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U)
     val vxsatRes = vxsatCanWbSeq.zip(vxsat_wb).map { case (canWb, wb) => Mux(canWb, wb.bits.vxsat.get, 0.U) }.fold(false.B)(_ | _)
     robEntries(i).vxsat := Mux(!robEntries(i).valid && instCanEnqFlag, 0.U, robEntries(i).vxsat | vxsatRes)
+
+    // trace
+    val taken = branchWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U && writeback.bits.redirect.get.bits.cfiUpdate.taken).reduce(_ || _)
+    val xret = csrWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U && io.csr.isXRet).reduce(_ || _)
+
+    when(xret){
+      robEntries(i).traceBlockInPipe.itype := Itype.ExpIntReturn
+    }.elsewhen(Itype.isBranchType(robEntries(i).traceBlockInPipe.itype)){
+      // BranchType code(itype = 5) must be correctly replaced!
+      robEntries(i).traceBlockInPipe.itype := Mux(taken, Itype.Taken, Itype.NonTaken)
+    }
   }
 
   // begin update robBanksRdata
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 1b2ed858f49..4933fdf3eea 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -34,6 +34,7 @@ import xiangshan.backend.Bundles.{DynInst, ExceptionInfo, ExuOutput}
 import xiangshan.backend.ctrlblock.{DebugLSIO, DebugLsInfo, LsTopdownInfo}
 import xiangshan.backend.fu.vector.Bundles.VType
 import xiangshan.backend.rename.SnapshotGenerator
+import xiangshan.backend.trace._
 
 import scala.collection.immutable.Nil
 
@@ -62,7 +63,9 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val loadWaitBit = Bool()    // for perfEvents
     val eliminatedMove = Bool() // for perfEvents
     // data end
-
+    
+    // trace
+    val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))
     // status begin
     val valid = Bool()
     val fflags = UInt(5.W)
@@ -110,6 +113,8 @@ object RobBundles extends HasCircularQueuePtrHelper {
     val loadWaitBit = Bool() // for perfEvents
     val isMove = Bool()      // for perfEvents
     val needFlush = Bool()
+    // trace
+    val traceBlockInPipe = new TracePipe(log2Up(RenameWidth * 2))
     // debug_begin
     val debug_pc = OptionWrapper(backendParams.debugEn, UInt(VAddrBits.W))
     val debug_instr = OptionWrapper(backendParams.debugEn, UInt(32.W))
@@ -137,6 +142,8 @@ object RobBundles extends HasCircularQueuePtrHelper {
     robEntry.eliminatedMove := robEnq.eliminatedMove
     // flushPipe needFlush but not exception
     robEntry.needFlush := robEnq.hasException || robEnq.flushPipe
+    // trace
+    robEntry.traceBlockInPipe := robEnq.traceBlockInPipe
     robEntry.debug_pc.foreach(_ := robEnq.pc)
     robEntry.debug_instr.foreach(_ := robEnq.instr)
     robEntry.debug_ldest.foreach(_ := robEnq.ldest)
@@ -167,6 +174,7 @@ object RobBundles extends HasCircularQueuePtrHelper {
     robCommitEntry.dirtyFs := robEntry.fpWen || robEntry.wflags
     robCommitEntry.dirtyVs := robEntry.dirtyVs
     robCommitEntry.needFlush := robEntry.needFlush
+    robCommitEntry.traceBlockInPipe := robEntry.traceBlockInPipe
     robCommitEntry.debug_pc.foreach(_ := robEntry.debug_pc.get)
     robCommitEntry.debug_instr.foreach(_ := robEntry.debug_instr.get)
     robCommitEntry.debug_ldest.foreach(_ := robEntry.debug_ldest.get)
diff --git a/src/main/scala/xiangshan/backend/trace/Interface.scala b/src/main/scala/xiangshan/backend/trace/Interface.scala
new file mode 100644
index 00000000000..a765ed4d79a
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/trace/Interface.scala
@@ -0,0 +1,163 @@
+package xiangshan.backend.trace
+
+import chisel3._
+import chisel3.util._
+import org.chipsalliance.cde.config.Parameters
+import utils.NamedUInt
+import xiangshan.HasXSParameter
+import xiangshan.frontend.{BrType, FtqPtr, PreDecodeInfo}
+
+class TraceTrap(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  val cause = UInt(XLEN.W)
+  val tval  = UInt(XLEN.W)
+  val priv  = Priv()
+}
+
+class TracePipe(iretireWidth: Int)(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  val itype     = Itype()
+  val iretire   = UInt(iretireWidth.W)
+  val ilastsize = Ilastsize()
+}
+
+class TraceBlock(hasIaddr: Boolean, iretireWidth: Int)(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  val iaddr     = if (hasIaddr)   Some(UInt(XLEN.W))                      else None
+  val ftqIdx    = if (!hasIaddr)  Some(new FtqPtr)                        else None
+  val ftqOffset = if (!hasIaddr)  Some( UInt(log2Up(PredictWidth).W))     else None
+  val tracePipe = new TracePipe(iretireWidth)
+}
+
+class TraceBundle(hasIaddr: Boolean, blockSize: Int, iretireWidth: Int)(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  val trap = Output(new TraceTrap)
+  val blocks = Vec(blockSize, ValidIO(new TraceBlock(hasIaddr, iretireWidth)))
+}
+
+class FromEncoder extends Bundle {
+  val enable = Bool()
+  val stall  = Bool()
+}
+
+class TraceCoreInterface(implicit val p: Parameters) extends Bundle with HasXSParameter {
+  // parameter
+  val CauseWidth             = XLEN
+  val TvalWidth              = XLEN
+  val PrivWidth              = 3
+  val IaddrWidth             = XLEN
+  val ItypeWidth             = 4
+  val IretireWidthInPipe     = log2Up(RenameWidth * 2)
+  val IretireWidthCompressed = log2Up(RenameWidth * CommitWidth * 2)
+  val IlastsizeWidth         = 1
+  val GroupNum               = TraceGroupNum
+  
+  val fromEncoder = Input(new Bundle {
+    val enable = Bool()
+    val stall  = Bool()
+  })
+  val toEncoder   = Output(new Bundle {
+    val cause     = UInt(CauseWidth.W)
+    val tval      = UInt(TvalWidth.W)
+    val priv      = UInt(PrivWidth.W)
+    val iaddr     = UInt((GroupNum * IaddrWidth).W)
+    val itype     = UInt((GroupNum * ItypeWidth).W)
+    val iretire   = UInt((GroupNum * IretireWidthCompressed).W)
+    val ilastsize = UInt((GroupNum * IlastsizeWidth).W)
+  })
+}
+
+object Itype extends NamedUInt(4) {
+  def None                 = 0.U
+  def Exception            = 1.U    //rob
+  def Interrupt            = 2.U    //rob
+  def ExpIntReturn         = 3.U    //rename
+  def NonTaken             = 4.U    //commit
+  def Taken                = 5.U    //commit
+  def UninferableJump      = 6.U    //It's reserved when width of itype is 4.
+  def reserved             = 7.U    //reserved
+  def UninferableCall      = 8.U    //rename
+  def InferableCall        = 9.U    //rename
+  def UninferableTailCall  = 10.U   //rename
+  def InferableTailCall    = 11.U   //rename
+  def CoRoutineSwap        = 12.U   //rename
+  def FunctionReturn       = 13.U   //rename
+  def OtherUninferableJump = 14.U   //rename
+  def OtherInferableJump   = 15.U   //rename
+
+  // Assuming the branchType is taken here, it will be correctly modified after writeBack.
+  def Branch = 5.U
+
+  def jumpTypeGen(brType: UInt, rd: OpRegType, rs: OpRegType): UInt = {
+
+    val isEqualRdRs = rd === rs
+    val isJal       = brType === BrType.jal
+    val isJalr      = brType === BrType.jalr
+    val isBranch    = brType === BrType.branch
+
+    // push to RAS when rd is link, pop from RAS when rs is link
+    def isUninferableCall      = isJalr && rd.isLink && (!rs.isLink || rs.isLink && isEqualRdRs)  //8   push
+    def isInferableCall        = isJal && rd.isLink                                               //9   push
+    def isUninferableTailCall  = isJalr && rd.isX0 && !rs.isLink                                  //10  no op
+    def isInferableTailCall    = isJal && rd.isX0                                                 //11  no op
+    def isCoRoutineSwap        = isJalr && rd.isLink && rs.isLink && !isEqualRdRs                 //12  pop then push
+    def isFunctionReturn       = isJalr && !rd.isLink && rs.isLink                                //13  pop
+    def isOtherUninferableJump = isJalr && !rd.isLink && !rd.isX0 && !rs.isLink                   //14  no op
+    def isOtherInferableJump   = isJal && !rd.isLink && !rd.isX0                                  //15  no op
+
+    val jumpType = Mux1H(
+      Seq(
+        isBranch,
+        isUninferableCall,
+        isInferableCall,
+        isUninferableTailCall,
+        isInferableTailCall,
+        isCoRoutineSwap,
+        isFunctionReturn,
+        isOtherUninferableJump,
+        isOtherInferableJump,
+      ),
+      Seq(
+        Branch,
+        UninferableCall,
+        InferableCall,
+        UninferableTailCall,
+        InferableTailCall,
+        CoRoutineSwap,
+        FunctionReturn,
+        OtherUninferableJump,
+        OtherInferableJump,
+      )
+    )
+
+    Mux(isBranch || isJal || isJalr, jumpType, 0.U)
+  }
+
+  def isTrap(itype: UInt) = Seq(Exception, Interrupt).map(_ === itype).reduce(_ || _)
+
+  def isNotNone(itype: UInt) = itype =/= None
+
+  def isBranchType(itype: UInt) = itype === Branch
+
+  // supportSijump
+  def isUninferable(itype: UInt) = Seq(UninferableCall, UninferableTailCall, CoRoutineSwap,
+    UninferableTailCall, OtherUninferableJump).map(_ === itype).reduce(_ || _)
+}
+
+object Ilastsize extends NamedUInt(1) {
+  def HalfWord = 0.U
+  def Word     = 1.U
+}
+
+object Priv extends NamedUInt(3) {
+  def HU = 0.U
+  def HS = 1.U
+  def M  = 3.U
+  def D  = 4.U
+  def VU = 5.U
+  def VS = 6.U
+}
+
+class OpRegType extends Bundle {
+  val value = UInt(3.W)
+  def isX0   = this.value === 0.U
+  def isX1   = this.value === 1.U
+  def isX5   = this.value === 5.U
+  def isLink = Seq(isX1, isX5).map(_ === this.value).reduce(_ || _)
+}
```
