package minixiangshan.backend.rename
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
 
class SnapshotManager(implicit p: Parameters) extends NSModule with HasCoreParameters {
 
  val io = IO(new Bundle {
    val doAllocReqs   = Input(Vec(CtrlBlockWidth, Bool()))   
    val allocReqs   = Input(Vec(CtrlBlockWidth, Bool()))   
    val allocOk     = Output(Bool())                       
    val allocIds    = Output(Vec(CtrlBlockWidth, Valid(UInt(log2Ceil(SnapshotNum).W))))
    val remaining   = Output(UInt(log2Ceil(SnapshotNum + 1).W))
 
    val resolve     = Flipped(Valid(new SnapshotResolveInfo))
    val resolveAllSs = Input(Bool())
 
    val doRecover       = Output(Bool())                     
    val recoverId       = Output(UInt(log2Ceil(SnapshotNum).W))
    val invalidateSlots = Output(Vec(SnapshotNum, Bool()))   
  })
 
  val valids = RegInit(VecInit.fill(SnapshotNum)(false.B))
 
  val younger = RegInit(VecInit(Seq.fill(SnapshotNum)(
    VecInit(Seq.fill(SnapshotNum)(false.B))
  )))
 
  val validCount = PopCount(valids)
  io.remaining := SnapshotNum.U - validCount
 
  val allocCount = PopCount(io.allocReqs)
  io.allocOk := (allocCount <= (SnapshotNum.U - validCount))
 
  val snapFreeMask = VecInit((0 until SnapshotNum).map(i => !valids(i))).asUInt
  diffDontTouch(snapFreeMask)
  val allocSlotId = Wire(Vec(CtrlBlockWidth, UInt(log2Ceil(SnapshotNum).W)))
  var search = snapFreeMask
  for (k <- 0 until CtrlBlockWidth) {
    val sel = PriorityEncoderOH(search)
    allocSlotId(k) := OHToUInt(sel)
    search = search & (~sel).asUInt
  }
 
  val allocPrefixSum = Wire(Vec(CtrlBlockWidth + 1, UInt(log2Ceil(CtrlBlockWidth + 1).W)))
  allocPrefixSum(0) := 0.U
  for (i <- 0 until CtrlBlockWidth) {
    allocPrefixSum(i + 1) := allocPrefixSum(i) + io.allocReqs(i).asUInt
  }
 
  for (i <- 0 until CtrlBlockWidth) {
    io.allocIds(i).valid := io.doAllocReqs(i) && io.allocOk
    io.allocIds(i).bits  := allocSlotId(allocPrefixSum(i))
  }
 
  val resolveValid = io.resolve.valid
  val resolveId    = io.resolve.bits.snptId
  val resolveMis   = io.resolve.bits.isMispredict
 
  val resolveSlotValid = valids(resolveId)
 
  val shouldInvalidate = Wire(Vec(SnapshotNum, Bool()))
  for (i <- 0 until SnapshotNum) {
    shouldInvalidate(i) :=  (resolveSlotValid && resolveValid &&  
    // 误预测：释放自己 + 所有更年轻的槽位
          ((resolveMis && (i.U === resolveId || younger(resolveId)(i))) ||
          // 正确预测：仅释放自己
           (!resolveMis && i.U === resolveId))) || io.resolveAllSs
  }
 
  io.invalidateSlots := shouldInvalidate
  io.doRecover       := resolveValid && resolveMis && resolveSlotValid //这是恢复物理寄存器的
  io.recoverId       := resolveId
 
  val validsAfterInv = Wire(Vec(SnapshotNum, Bool()))
  for (i <- 0 until SnapshotNum) {
    validsAfterInv(i) := valids(i) && !shouldInvalidate(i)
  }
 //释放后的年龄矩阵（清除被释放槽位的所有行和列）
  val youngerAfterInv = Wire(Vec(SnapshotNum, Vec(SnapshotNum, Bool())))
  for (i <- 0 until SnapshotNum) {
    for (j <- 0 until SnapshotNum) {
      youngerAfterInv(i)(j) := Mux(
        shouldInvalidate(i) || shouldInvalidate(j),
        false.B,
        younger(i)(j)
      )
    }
  }
 
  val finalValids  = WireInit(validsAfterInv)
  val finalYounger = WireInit(youngerAfterInv)
 
  for (i <- 0 until CtrlBlockWidth) {
    when(io.doAllocReqs(i) && io.allocOk) {
      val newSlot = allocSlotId(allocPrefixSum(i))
 
      finalValids(newSlot) := true.B
  // 新槽位比所有当前有效的旧槽位都年轻
      for (j <- 0 until SnapshotNum) {
        when(validsAfterInv(j) && j.U =/= newSlot) {
          finalYounger(j)(newSlot) := true.B
        }
      }
 
      for (k <- 0 until i) {
        when(io.doAllocReqs(k) && io.allocOk) {
          val prevSlot = allocSlotId(allocPrefixSum(k))
          finalYounger(prevSlot)(newSlot) := true.B
        }
      }
    }
  }
 
  valids  := finalValids
  younger := finalYounger
}