package minixiangshan.backend.rename
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
 
class RenameTable(implicit p: Parameters) extends NSModule {
 
  val nReadPorts = CtrlBlockWidth * 3
 
  val io = IO(new Bundle {
    val redirect       = Input(Bool())
    val doRecover      = Input(Bool())
    val recoverId      = Input(UInt(log2Ceil(SnapshotNum).W))
    val readPorts      = Vec(nReadPorts, new RatReadPort)
    val specWritePorts = Vec(CtrlBlockWidth, Input(new RatWritePort))
    val archWritePorts = Vec(CommitWidth, Input(new RatWritePort))
    val archReadPorts  = Vec(CommitWidth, new Bundle {
      val laddr = Input(UInt(log2Ceil(IntLogicRegs).W))
      val pdata = Output(UInt(PhyRegIdxWidth.W))
    })
    val snptSave       = Input(Vec(CtrlBlockWidth, Valid(UInt(log2Ceil(SnapshotNum).W))))
    val snptInvalidate = Input(Vec(SnapshotNum, Bool()))
    val writeFire      = Input(Bool()) 
  })
  val difftest = if (EnableDifftest) Some(IO(Output(Vec(IntLogicRegs, UInt(PhyRegIdxWidth.W))))) else None
    //  1. 双表初始化：逻辑寄存器 i 初始映射到物理寄存器 i

  val tableInit = VecInit.tabulate(IntLogicRegs)(_.U(PhyRegIdxWidth.W))
 
  val specTable     = RegInit(tableInit)
  val archTable     = RegInit(tableInit)
  val archTableNext = WireInit(archTable)
  if (EnableDifftest) {
    difftest.get := archTable
  }
    //  2. 快照存储
 
  val snapshots = RegInit(VecInit(Seq.fill(SnapshotNum)(VecInit(Seq.fill(IntLogicRegs)(0.U(PhyRegIdxWidth.W))))))
  val snptValids = RegInit(VecInit.fill(SnapshotNum)(false.B))
 
  val gatedSpecWritePorts = Wire(Vec(CtrlBlockWidth, new RatWritePort))
  for(i <- 0 until CtrlBlockWidth) {
    gatedSpecWritePorts(i)      := io.specWritePorts(i)
    gatedSpecWritePorts(i).wen  := io.specWritePorts(i).wen && io.writeFire
  }
     //  3. T0→T1 打拍（读管线不变）
    // 重定向时 t1WSpec 清零：阻止错误路径的写入被提交

  val t1WSpec = RegNext(
    Mux(io.redirect, 0.U.asTypeOf(gatedSpecWritePorts), gatedSpecWritePorts)
  )
  val t1Raddr = io.readPorts.map(p => RegEnable(p.addr, !p.hold))
  val t1RdataByT1Raddr = VecInit(t1Raddr.map(addr => specTable(addr)))
   //  4. 计算基础状态 baseState = specTable + t1WSpec 写入效果
  val t1WSpecAddrOH = t1WSpec.map(w =>
    Mux(w.wen, UIntToOH(w.addr, IntLogicRegs), 0.U)
  )
 
  val baseState = Wire(Vec(IntLogicRegs, UInt(PhyRegIdxWidth.W)))
  for (i <- 0 until IntLogicRegs) {
    val matchVec  = t1WSpecAddrOH.map(oh => oh(i))
    val matchData = PriorityMux(matchVec.reverse, t1WSpec.map(_.data).reverse)
    val anyMatch  = VecInit(matchVec).asUInt.orR
    baseState(i) := Mux(anyMatch, matchData, specTable(i))
  }
   //  5. scanLeft 计算中间状态
  val remapStates = io.specWritePorts.scanLeft(baseState) { case (table, wPort) =>
    VecInit(table.zipWithIndex.map { case (preg, lreg) =>
      if (lreg == 0) 0.U(PhyRegIdxWidth.W)
      else Mux(wPort.wen && wPort.addr === lreg.U, wPort.data, preg)
    })
  }
    //  6. 快照保存

  // ── 打一拍写快照的逻辑 ──
  // 寄存上一拍的快照保存请求
  val delayedSnptSaveValid = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(false.B)))
  val delayedSnptSaveBits  = RegInit(VecInit(Seq.fill(CtrlBlockWidth)(0.U(log2Ceil(SnapshotNum).W))))
  val delayedSnptState     = Reg(Vec(CtrlBlockWidth, Vec(IntLogicRegs, UInt(PhyRegIdxWidth.W))))

  for (i <- 0 until CtrlBlockWidth) {
    // 如果当拍没有发生重定向冲刷，则将快照保存请求记录下来
    // 如果发生了 io.redirect，则冲刷掉打拍的写入请求，保证异常分支产生的脏状态不被保留
    delayedSnptSaveValid(i) := io.snptSave(i).valid && !io.redirect
    
    when(io.snptSave(i).valid) {
      delayedSnptSaveBits(i) := io.snptSave(i).bits
      delayedSnptState(i)    := remapStates(i + 1)
    }
  }

  // 写入快照表（打一拍后的执行）
  for (i <- 0 until CtrlBlockWidth) {
    // 如果在写入的这拍又发生了重定向（flush），则取消写入，因为该分支以及被分配的槽位必定已经被作废
    // 如果在 stall 期间，依然可以正常写入上一拍寄存在这的快照（不受 stall 妨碍）
    // 如果同时遇到释放快照的操作，由于释放是对不同槽位的 Valid 处理，与此处落库写新槽位互不冲突
    when(delayedSnptSaveValid(i) && !io.redirect) {
      snapshots(delayedSnptSaveBits(i)) := delayedSnptState(i)
    }
  }

  //  7. 快照有效位更新
  for (i <- 0 until SnapshotNum) {
    val nextValid = WireInit(snptValids(i))
 
    for (j <- 0 until CtrlBlockWidth) {
      when(io.snptSave(j).valid && io.snptSave(j).bits === i.U) {
        nextValid := true.B
      }
    }
 
    when(io.snptInvalidate(i)) {
      nextValid := false.B
    }
 
    snptValids(i) := nextValid
  }
 
  when(io.redirect) {
    when(io.doRecover && snptValids(io.recoverId)) {
      specTable := snapshots(io.recoverId)
    }.otherwise {
      specTable := archTable
    }
  }.otherwise {
    specTable := baseState
  }
 
  val archWriteAddrOH = io.archWritePorts.map(w =>
    Mux(w.wen, UIntToOH(w.addr, IntLogicRegs), 0.U)
  )
 
  for ((next, i) <- archTableNext.zipWithIndex) {
    val matchVec  = archWriteAddrOH.map(oh => oh(i))
    val matchData = PriorityMux(matchVec.reverse, io.archWritePorts.map(_.data).reverse)
    val anyMatch  = VecInit(matchVec).asUInt.orR
    next := Mux(anyMatch, matchData, archTable(i))
  }
  archTable := archTableNext
 
  for ((port, rIdx) <- io.archReadPorts.zipWithIndex) {
    val earlierWritePorts = io.archWritePorts.zipWithIndex.filter { case (_, wIdx) => wIdx < rIdx }
    if (earlierWritePorts.nonEmpty) {
      val archBypassHits  = earlierWritePorts.map { case (w, _) => w.wen && (w.addr === port.laddr) }
      val archBypassDatas = earlierWritePorts.map { case (w, _) => w.data }
      val archBypassData  = PriorityMux(archBypassHits.reverse, archBypassDatas.reverse)
      val anyArchHit      = VecInit(archBypassHits).asUInt.orR
      port.pdata := Mux(anyArchHit, archBypassData, archTable(port.laddr))
    } else {
      port.pdata := archTable(port.laddr)
    }
  }
 
  for ((r, i) <- io.readPorts.zipWithIndex) {
    val t0Bypass = gatedSpecWritePorts.map(w => w.wen && (w.addr === r.addr))
    val t1Bypass = RegNext(
      Mux(io.redirect, 0.U.asTypeOf(VecInit(t0Bypass)), VecInit(t0Bypass))
    )
    val bypassData = PriorityMux(t1Bypass.reverse, t1WSpec.map(_.data).reverse)
    r.data := Mux(t1Bypass.asUInt.orR, bypassData, t1RdataByT1Raddr(i))
  }
}