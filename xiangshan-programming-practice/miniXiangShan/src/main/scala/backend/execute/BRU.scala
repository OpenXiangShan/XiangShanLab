package minixiangshan.backend.execute
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.rename._
import minixiangshan.backend.dispatch.DispatchedInst
import minixiangshan.backend.rename.RedirectInfo
import minixiangshan.frontend.BpuUpdateReq
 
// ═══════════════════════════════════════════════════════════════
//  分支执行单元
//
//  支持操作：jal, jalr, beq, bne, blt, bge, bltu, bgeu
//  单拍组合逻辑完成
//
//  职责：
//    1. 计算分支实际 taken 与目标地址
//    2. 与 BPU 预测值比对，仅在误预测时发起重定向
//    3. 无论预测正确与否，为每条有效分支指令生成 BPU 更新数据
//  注意：JAL 的重定向与 BPU 更新已由前端预译码处理，此处跳过
// ═══════════════════════════════════════════════════════════════
 
class redirectInfoFromBru(implicit p: Parameters) extends NSBundle {
  val doRedirect = Bool()
  val snptId     = UInt(log2Ceil(SnapshotNum).W)
  val robIdx     = new RobPtr(RobSize)
  val target     = UInt(XLEN.W)
}
 
class redirectInfoToModule(implicit p: Parameters) extends NSBundle {
  val doRedirect = Bool()
  val flushSelf  = Bool()
  val invalidIcache = Bool()
 
  // 如果是来自于BRU的重定向:
  val fromBru = Bool()
  val snptId  = UInt(log2Ceil(SnapshotNum).W)
  val robIdx  = new RobPtr(RobSize)
 
  // 如果是来自于Rob的重定向:
  val fromRob = Bool()
 
  // 重定向目标
  val target = UInt(XLEN.W)
}

class BRU(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val valid     = Input(Bool())
    val uop       = Input(new DispatchedInst)
    val rs1       = Input(UInt(XLEN.W))
    val rs2       = Input(UInt(XLEN.W))
    val result    = Output(UInt(XLEN.W))                        // 写回目标寄存器的值（如 BL 的 PC+4）
    val bruInfo   = ValidIO(new redirectInfoFromBru)             // 误预测重定向
    val isBranch  = Output(Bool())                               // 是否为分支指令
    val taken     = Output(Bool())                               // 分支是否 taken
    val bpuUpdate = Output(new BpuUpdateReq)                    // BPU 更新数据（始终发出）
  })
  diffDontTouch(io.uop)

  val op   = io.uop.ctrl.bruOp
  val src1 = io.rs1
  val src2 = io.rs2
  val pc   = io.uop.pc
 
  // ── 条件判断 ──
  val eq  = src1 === src2
  val ne  = !eq
  val lt  = src1.asSInt < src2.asSInt
  val ge  = !lt
  val ltu = src1 < src2
  val geu = !ltu

  // ── 分支是否 taken（实际值） ──
  val customUnit = if (customInstrEnable) {
    val unit = Module(new CustomBruUnit)
    unit.io.valid := io.valid && op === BruOp.custom
    unit.io.op := op
    unit.io.inst := io.uop.inst
    unit.io.rs1 := src1
    unit.io.rs2 := src2
    Some(unit)
  } else {
    None
  }

  val branchTaken = MuxCase(false.B, Seq(
    (op === BruOp.jal)  -> true.B,
    (op === BruOp.jalr) -> true.B,
    (op === BruOp.beq)  -> eq,
    (op === BruOp.bne)  -> ne,
    (op === BruOp.blt)  -> lt,
    (op === BruOp.bge)  -> ge,
    (op === BruOp.bltu) -> ltu,
    (op === BruOp.bgeu) -> geu
  ) ++ customUnit.toSeq.map(unit => (op === BruOp.custom) -> unit.io.taken))
 
  // ── 目标地址计算（实际值） ──
  val imm          = io.uop.imm
  // JALR：目标地址最低位清零（RISC-V 规定）
  val jalrSum      = (src1 + imm)(XLEN - 1, 0)
  val jalrTarget   = Cat(jalrSum(XLEN - 1, 1), 0.U(1.W))
  val branchTarget = (pc + imm)(XLEN - 1, 0)
  val target       = Mux(op === BruOp.jalr, jalrTarget, branchTarget)
 
  // ── 写回值 ──
  // jal / jalr 把 PC+4 写入 rd
  val linkResult = pc + 4.U
  io.result := Mux(io.uop.ctrl.rfWen, linkResult, 0.U)
 
  // ── 指令类型标记 ──
  io.isBranch := op =/= BruOp.none
  io.taken    := branchTaken
 
  // ══════════════════════════════════════════════════════════════
  //  误预测判定
  //
  //  BPU 预测值: bpuTaken / bpuTarget
  //  实际执行值: branchTaken / target
  //
  //  三种误预测情况：
  //    mispredTaken    : 预测不跳转，实际跳转       → 重定向到实际目标
  //    mispredNotTaken : 预测跳转，实际不跳转       → 重定向到 PC+4
  //    mispredTarget   : 预测跳转且实际跳转，目标错误 → 重定向到实际目标
  // ══════════════════════════════════════════════════════════════
 
  val bpuTaken  = io.uop.bpuInfo.taken
  val bpuTarget = io.uop.bpuInfo.target
 
  val mispredTaken    = !bpuTaken && branchTaken
  val mispredNotTaken = bpuTaken && !branchTaken
  val mispredTarget   = bpuTaken && branchTaken && (bpuTarget =/= target)
  val mispredict      = mispredTaken || mispredNotTaken || mispredTarget
 
  // JAL 的误预测已由前端预译码处理，BRU 不再发起重定向
  val isBranch = io.valid && io.isBranch && !io.uop.pdInfo.isJal
  val needRedirect = io.valid && io.isBranch && mispredict && !io.uop.pdInfo.isJal
 
  // 重定向目标：实际跳转 → target，实际不跳转 → PC+4
  val redirectTarget = Mux(branchTaken, target, pc + 4.U)
//
//  // Branch statistics
//  val fetchBlockBitsValue = log2Ceil(fetchWidth) + 2
//  val branchCnt  = RegInit(0.U(64.W))
//  val redirectCnt = RegInit(0.U(64.W))
//  val btbReadIdx = io.uop.pc(btbIndexBits + fetchBlockBitsValue - 1, fetchBlockBitsValue)
//  val branchEvent = io.valid && io.isBranch
//  val redirectEvent = needRedirect
//  val nextBranchCnt = branchCnt + branchEvent.asUInt
//  val nextRedirectCnt = redirectCnt + redirectEvent.asUInt
// 
  // ── 重定向输出 ──
  io.bruInfo.valid           := isBranch
  io.bruInfo.bits.doRedirect := needRedirect
  io.bruInfo.bits.robIdx     := io.uop.robIdxFull
  io.bruInfo.bits.target     := redirectTarget
  io.bruInfo.bits.snptId     := io.uop.snptId.bits
 
  // ══════════════════════════════════════════════════════════════
  //  BPU 更新
  //
  //  无论预测正确与否，只要执行了有效的分支指令就发出更新数据。
  //  B/BL 的更新由预译码负责，此处跳过（避免重复写入）。
  // ══════════════════════════════════════════════════════════════
 
  val needBpuUpdate = isBranch
 
  io.bpuUpdate.valid              := needBpuUpdate
  io.bpuUpdate.validEntry         := true.B
  io.bpuUpdate.pc            := pc
  io.bpuUpdate.taken         := branchTaken
  io.bpuUpdate.target        := target                        // 实际跳转目标（始终填入，供 BTB 记录）
  io.bpuUpdate.oldPhtCounter :=  io.uop.bpuInfo.meta.phtCounter //Mux(io.uop.bpuInfo.meta.valid, io.uop.bpuInfo.meta.phtCounter, 2.U)
  io.bpuUpdate.isJalr        := io.uop.pdInfo.isJalr
  io.bpuUpdate.isJal         := io.uop.pdInfo.isJal
  io.bpuUpdate.isCall        := false.B                       // 已剔除
  io.bpuUpdate.isRet         := false.B                       // 已剔除
  io.bpuUpdate.offset        := pc(fetchOffsetBits + 1, 2)    // 指令在取指块内的偏移
  io.bpuUpdate.rasTop        := io.uop.bpuInfo.meta.rasTop
//
//  when(branchEvent) {
//    branchCnt := nextBranchCnt
//    when(redirectEvent) {
//      redirectCnt := nextRedirectCnt
//    }
//    DebugPrint.debugPrint(true.B, "[BRU] pc=0x%x btbIdx=0x%x branches=%d redirects=%d\n",
//      pc, btbReadIdx, nextBranchCnt, nextRedirectCnt)
//  }
}
