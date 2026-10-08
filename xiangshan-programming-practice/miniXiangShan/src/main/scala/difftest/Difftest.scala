package minixiangshan.difftest

import chisel3._
import chisel3.util._
import chisel3.experimental._
import minixiangshan.config._

/* ============================================================================
 *  Difftest 黑盒接口（DPI-C）
 *
 *  注意：这些黑盒由仿真测试平台提供实现（chiplab/testbench）。
 *  切换到 RISC-V 后，C 侧需要换成 RISC-V 的 NEMU 参考模型
 *  （riscv32-nemu-interpreter-so），接口信号已按 RISC-V 语义重新命名。
 * ==========================================================================*/
class DifftestInstrCommit extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val index = Input(UInt(8.W))
    val valid = Input(Bool())
    val pc = Input(UInt(64.W))
    val instr = Input(UInt(32.W))
    val skip = Input(Bool())
    val wen = Input(Bool())
    val wdest = Input(UInt(8.W))
    val wdata = Input(UInt(64.W))
  })
}

class DifftestExcpEvent extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val excp_valid = Input(Bool())
    val intr = Input(Bool())
    val cause = Input(UInt(6.W))
    val exceptionPC = Input(UInt(64.W))
    val exceptionInst = Input(UInt(32.W))
  })
}

class DifftestTrapEvent extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val valid = Input(Bool())
    val code = Input(UInt(8.W))
    val pc = Input(UInt(64.W))
    val cycleCnt = Input(UInt(64.W))
    val instrCnt = Input(UInt(64.W))
  })
}

class DifftestStoreEvent extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val index = Input(UInt(8.W))
    val valid = Input(Bool())
    val storePAddr = Input(UInt(64.W))
    val storeVAddr = Input(UInt(64.W))
    val storeData = Input(UInt(64.W))
  })
}

class DifftestLoadEvent extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val index = Input(UInt(8.W))
    val valid = Input(Bool())
    val paddr = Input(UInt(64.W))
    val vaddr = Input(UInt(64.W))
  })
}

/** RISC-V CSR 状态对比接口 */
class DifftestCSRRegState extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val mstatus = Input(UInt(32.W))
    val misa = Input(UInt(32.W))
    val medeleg = Input(UInt(32.W))
    val mideleg = Input(UInt(32.W))
    val mie = Input(UInt(32.W))
    val mtvec = Input(UInt(32.W))
    val mscratch = Input(UInt(32.W))
    val mepc = Input(UInt(32.W))
    val mcause = Input(UInt(32.W))
    val mtval = Input(UInt(32.W))
    val mip = Input(UInt(32.W))
    val sstatus = Input(UInt(32.W))
    val stvec = Input(UInt(32.W))
    val sscratch = Input(UInt(32.W))
    val sepc = Input(UInt(32.W))
    val scause = Input(UInt(32.W))
    val stval = Input(UInt(32.W))
    val satp = Input(UInt(32.W))
    val priv = Input(UInt(2.W))
    val mcycle = Input(UInt(64.W))
    val minstret = Input(UInt(64.W))
  })
}

class DifftestGRegState extends BlackBox with HasBlackBoxResource {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val coreid = Input(UInt(8.W))
    val gpr_0 = Input(UInt(64.W))
    val gpr_1 = Input(UInt(64.W))
    val gpr_2 = Input(UInt(64.W))
    val gpr_3 = Input(UInt(64.W))
    val gpr_4 = Input(UInt(64.W))
    val gpr_5 = Input(UInt(64.W))
    val gpr_6 = Input(UInt(64.W))
    val gpr_7 = Input(UInt(64.W))
    val gpr_8 = Input(UInt(64.W))
    val gpr_9 = Input(UInt(64.W))
    val gpr_10 = Input(UInt(64.W))
    val gpr_11 = Input(UInt(64.W))
    val gpr_12 = Input(UInt(64.W))
    val gpr_13 = Input(UInt(64.W))
    val gpr_14 = Input(UInt(64.W))
    val gpr_15 = Input(UInt(64.W))
    val gpr_16 = Input(UInt(64.W))
    val gpr_17 = Input(UInt(64.W))
    val gpr_18 = Input(UInt(64.W))
    val gpr_19 = Input(UInt(64.W))
    val gpr_20 = Input(UInt(64.W))
    val gpr_21 = Input(UInt(64.W))
    val gpr_22 = Input(UInt(64.W))
    val gpr_23 = Input(UInt(64.W))
    val gpr_24 = Input(UInt(64.W))
    val gpr_25 = Input(UInt(64.W))
    val gpr_26 = Input(UInt(64.W))
    val gpr_27 = Input(UInt(64.W))
    val gpr_28 = Input(UInt(64.W))
    val gpr_29 = Input(UInt(64.W))
    val gpr_30 = Input(UInt(64.W))
    val gpr_31 = Input(UInt(64.W))
  })
}

// 顶层 Difftest 逻辑
class DifftestInCore(implicit p: Parameters) extends NSModule {
  val io = IO(Input(new CoreDifftestBundle))

  private def zeroExt64(x: UInt): UInt = {
    val width = x.getWidth
    if (width >= 64) x(63, 0) else Cat(0.U((64 - width).W), x)
  }

  val cmt = RegInit(0.U.asTypeOf(Vec(CommitWidth, new DifftestCommitInfo)))
  val cmtTimer64 = RegInit(0.U(64.W))
  cmt := io.commit
  cmtTimer64 := io.csr.timer64

  val cycleCnt = RegInit(0.U(64.W))
  val instrCnt = RegInit(0.U(64.W))
  cycleCnt := cycleCnt + 1.U
  instrCnt := instrCnt + PopCount(io.commit.map(_.valid))

  for (i <- 0 until CommitWidth) {
    val commit = cmt(i)

    val difftestInstrCommit = Module(new DifftestInstrCommit)
    difftestInstrCommit.io.clock := clock
    difftestInstrCommit.io.coreid := 0.U
    difftestInstrCommit.io.index := i.U
    difftestInstrCommit.io.valid := commit.valid && !commit.excpFlush
    difftestInstrCommit.io.pc := zeroExt64(commit.pc)
    difftestInstrCommit.io.instr := commit.instr
    difftestInstrCommit.io.skip := false.B
    difftestInstrCommit.io.wen := commit.valid && commit.rfWen
    difftestInstrCommit.io.wdest := Cat(0.U(3.W), commit.wdest)
    difftestInstrCommit.io.wdata := zeroExt64(commit.wdata)

    val difftestStoreEvent = Module(new DifftestStoreEvent)
    difftestStoreEvent.io.clock := clock
    difftestStoreEvent.io.coreid := 0.U
    difftestStoreEvent.io.index := i.U
    difftestStoreEvent.io.valid := commit.valid && commit.store.valid && !commit.excpFlush
    difftestStoreEvent.io.storePAddr := zeroExt64(commit.store.paddr)
    difftestStoreEvent.io.storeVAddr := zeroExt64(commit.store.vaddr)
    difftestStoreEvent.io.storeData := zeroExt64(commit.store.data)

    val difftestLoadEvent = Module(new DifftestLoadEvent)
    difftestLoadEvent.io.clock := clock
    difftestLoadEvent.io.coreid := 0.U
    difftestLoadEvent.io.index := i.U
    difftestLoadEvent.io.valid := commit.valid && commit.load.valid
    difftestLoadEvent.io.paddr := zeroExt64(commit.load.paddr)
    difftestLoadEvent.io.vaddr := zeroExt64(commit.load.vaddr)
  }

  val excpValids = VecInit(cmt.map(c => c.valid && (c.excpFlush || c.xretFlush)))
  val excpCommit = PriorityMux(excpValids, cmt)
  val difftestExcpEvent = Module(new DifftestExcpEvent)
  difftestExcpEvent.io.clock := clock
  difftestExcpEvent.io.coreid := 0.U
  difftestExcpEvent.io.excp_valid := excpValids.asUInt.orR && excpCommit.excpFlush
  difftestExcpEvent.io.intr := excpCommit.cause === 0.U && excpCommit.excpFlush
  difftestExcpEvent.io.cause := excpCommit.cause
  difftestExcpEvent.io.exceptionPC := zeroExt64(excpCommit.pc)
  difftestExcpEvent.io.exceptionInst := excpCommit.instr

  val trapValids = VecInit(cmt.map(c => c.valid && c.trap))
  val trapCommit = PriorityMux(trapValids, cmt)
  val difftestTrapEvent = Module(new DifftestTrapEvent)
  difftestTrapEvent.io.clock := clock
  difftestTrapEvent.io.coreid := 0.U
  difftestTrapEvent.io.valid := trapValids.asUInt.orR
  difftestTrapEvent.io.code := Mux(trapCommit.trapCode.orR, trapCommit.trapCode, io.regs(10)(7, 0))
  difftestTrapEvent.io.pc := zeroExt64(trapCommit.pc)
  difftestTrapEvent.io.cycleCnt := cycleCnt
  difftestTrapEvent.io.instrCnt := instrCnt

  val difftestCSRRegState = Module(new DifftestCSRRegState)
  difftestCSRRegState.io.clock := clock
  difftestCSRRegState.io.coreid := 0.U
  difftestCSRRegState.io.mstatus  := io.csr.mstatus
  difftestCSRRegState.io.misa     := io.csr.misa
  difftestCSRRegState.io.medeleg  := io.csr.medeleg
  difftestCSRRegState.io.mideleg  := io.csr.mideleg
  difftestCSRRegState.io.mie      := io.csr.mie
  difftestCSRRegState.io.mtvec    := io.csr.mtvec
  difftestCSRRegState.io.mscratch := io.csr.mscratch
  difftestCSRRegState.io.mepc     := io.csr.mepc
  difftestCSRRegState.io.mcause   := io.csr.mcause
  difftestCSRRegState.io.mtval    := io.csr.mtval
  difftestCSRRegState.io.mip      := io.csr.mip
  difftestCSRRegState.io.sstatus  := io.csr.sstatus
  difftestCSRRegState.io.stvec    := io.csr.stvec
  difftestCSRRegState.io.sscratch := io.csr.sscratch
  difftestCSRRegState.io.sepc     := io.csr.sepc
  difftestCSRRegState.io.scause   := io.csr.scause
  difftestCSRRegState.io.stval    := io.csr.stval
  difftestCSRRegState.io.satp     := io.csr.satp
  difftestCSRRegState.io.priv     := io.csr.priv
  difftestCSRRegState.io.mcycle   := io.csr.mcycle
  difftestCSRRegState.io.minstret := io.csr.minstret

  val difftestGRegState = Module(new DifftestGRegState)
  difftestGRegState.io.clock := clock
  difftestGRegState.io.coreid := 0.U
  difftestGRegState.io.gpr_0 := 0.U
  difftestGRegState.io.gpr_1 := zeroExt64(io.regs(1))
  difftestGRegState.io.gpr_2 := zeroExt64(io.regs(2))
  difftestGRegState.io.gpr_3 := zeroExt64(io.regs(3))
  difftestGRegState.io.gpr_4 := zeroExt64(io.regs(4))
  difftestGRegState.io.gpr_5 := zeroExt64(io.regs(5))
  difftestGRegState.io.gpr_6 := zeroExt64(io.regs(6))
  difftestGRegState.io.gpr_7 := zeroExt64(io.regs(7))
  difftestGRegState.io.gpr_8 := zeroExt64(io.regs(8))
  difftestGRegState.io.gpr_9 := zeroExt64(io.regs(9))
  difftestGRegState.io.gpr_10 := zeroExt64(io.regs(10))
  difftestGRegState.io.gpr_11 := zeroExt64(io.regs(11))
  difftestGRegState.io.gpr_12 := zeroExt64(io.regs(12))
  difftestGRegState.io.gpr_13 := zeroExt64(io.regs(13))
  difftestGRegState.io.gpr_14 := zeroExt64(io.regs(14))
  difftestGRegState.io.gpr_15 := zeroExt64(io.regs(15))
  difftestGRegState.io.gpr_16 := zeroExt64(io.regs(16))
  difftestGRegState.io.gpr_17 := zeroExt64(io.regs(17))
  difftestGRegState.io.gpr_18 := zeroExt64(io.regs(18))
  difftestGRegState.io.gpr_19 := zeroExt64(io.regs(19))
  difftestGRegState.io.gpr_20 := zeroExt64(io.regs(20))
  difftestGRegState.io.gpr_21 := zeroExt64(io.regs(21))
  difftestGRegState.io.gpr_22 := zeroExt64(io.regs(22))
  difftestGRegState.io.gpr_23 := zeroExt64(io.regs(23))
  difftestGRegState.io.gpr_24 := zeroExt64(io.regs(24))
  difftestGRegState.io.gpr_25 := zeroExt64(io.regs(25))
  difftestGRegState.io.gpr_26 := zeroExt64(io.regs(26))
  difftestGRegState.io.gpr_27 := zeroExt64(io.regs(27))
  difftestGRegState.io.gpr_28 := zeroExt64(io.regs(28))
  difftestGRegState.io.gpr_29 := zeroExt64(io.regs(29))
  difftestGRegState.io.gpr_30 := zeroExt64(io.regs(30))
  difftestGRegState.io.gpr_31 := zeroExt64(io.regs(31))
}
