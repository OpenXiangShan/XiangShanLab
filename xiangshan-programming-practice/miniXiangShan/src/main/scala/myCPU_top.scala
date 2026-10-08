package minixiangshan
 
import chisel3._
import chisel3.util._
import chisel3.dontTouch
import minixiangshan.config.NSModule
import minixiangshan.config.NSRawModule
import minixiangshan.config.NSBundle
import minixiangshan.config.Parameters
 
import minixiangshan.axi._
import minixiangshan.frontend._
import minixiangshan.mmu._
import minixiangshan.csr._
import minixiangshan.difftest._
import minixiangshan.backend.Backend
import minixiangshan.mem._
import minixiangshan.mem.L2cache.L2Cache
import minixiangshan.mem.L2cache.L2ReadArbiter
 
class core_top(implicit p: Parameters) extends NSRawModule {
  // ========== 时钟与复位 ==========
  val aclk    = IO(Input(Clock()))
  val aresetn = IO(Input(Bool()))
 
  // ========== 中断输入 ==========
  val intrpt = IO(Input(UInt(8.W)))
 
  // ========== AXI3 AR通道 ==========
  val arid    = IO(Output(UInt(4.W)))
  val araddr  = IO(Output(UInt(32.W)))
  val arlen   = IO(Output(UInt(8.W)))
  val arsize  = IO(Output(UInt(3.W)))
  val arburst = IO(Output(UInt(2.W)))
  val arlock  = IO(Output(UInt(2.W)))
  val arcache = IO(Output(UInt(4.W)))
  val arprot  = IO(Output(UInt(3.W)))
  val arvalid = IO(Output(Bool()))
  val arready = IO(Input(Bool()))
 
  // ========== AXI3 R通道 ==========
  val rid    = IO(Input(UInt(4.W)))
  val rdata  = IO(Input(UInt(32.W)))
  val rresp  = IO(Input(UInt(2.W)))
  val rlast  = IO(Input(Bool()))
  val rvalid = IO(Input(Bool()))
  val rready = IO(Output(Bool()))
 
  // ========== AXI3 AW通道 ==========
  val awid    = IO(Output(UInt(4.W)))
  val awaddr  = IO(Output(UInt(32.W)))
  val awlen   = IO(Output(UInt(8.W)))
  val awsize  = IO(Output(UInt(3.W)))
  val awburst = IO(Output(UInt(2.W)))
  val awlock  = IO(Output(UInt(2.W)))
  val awcache = IO(Output(UInt(4.W)))
  val awprot  = IO(Output(UInt(3.W)))
  val awvalid = IO(Output(Bool()))
  val awready = IO(Input(Bool()))
 
  // ========== AXI3 W通道 ==========
  val wid    = IO(Output(UInt(4.W)))
  val wdata  = IO(Output(UInt(32.W)))
  val wstrb  = IO(Output(UInt(4.W)))
  val wlast  = IO(Output(Bool()))
  val wvalid = IO(Output(Bool()))
  val wready = IO(Input(Bool()))
 
  // ========== AXI3 B通道 ==========
  val bid    = IO(Input(UInt(4.W)))
  val bresp  = IO(Input(UInt(2.W)))
  val bvalid = IO(Input(Bool()))
  val bready = IO(Output(Bool()))
 
  // ========== 调试接口 ==========
  val break_point       = IO(Input(Bool()))
  val infor_flag        = IO(Input(Bool()))
  val reg_num           = IO(Input(UInt(5.W)))
  val ws_valid          = IO(Output(Bool()))
  val rf_rdata          = IO(Output(UInt(32.W)))
  val debug0_wb_pc      = IO(Output(UInt(32.W)))
  val debug0_wb_rf_wen  = IO(Output(Bool()))
  val debug0_wb_rf_wnum = IO(Output(UInt(5.W)))
  val debug0_wb_rf_wdata= IO(Output(UInt(32.W)))
  val debug0_wb_inst    = IO(Output(UInt(32.W)))
 
  // ========== 输出信号初始值 ==========
  debug0_wb_pc       := 0.U
  debug0_wb_rf_wen   := false.B
  debug0_wb_rf_wnum  := 0.U
  debug0_wb_rf_wdata := 0.U
  debug0_wb_inst     := 0.U
  ws_valid           := false.B
  rf_rdata           := 0.U
  // ---- 复位同步：打一拍 ----
  val sync_reset = Wire(Bool())
  withClockAndReset(aclk, ~aresetn) {   // 这个寄存器本身用原始异步复位
    val rst_d1 = RegInit(true.B)        // 复位时保持1（复位有效）
    rst_d1 := false.B                    // 复位释放后，下一拍拉低
    sync_reset := rst_d1
  }

 
  withClockAndReset(aclk, sync_reset ) {

  val frontend = Module(new Frontend)
  val backend = Module(new Backend)
  val memory = Module(new MemoryBlock)
  val mmu = Module(new Mmu)
  val lrValid = Wire(Bool())
  val coreIdle = RegInit(false.B)

  memory.io.redirectInfo <> backend.io.redirectInfo
  backend.io.storeQueueEmpty := memory.io.storeQueueEmpty
  
  frontend.io.out <> backend.io.in
  frontend.io.redirectInfo <> backend.io.redirectInfo
  //invalidIcahe由上诉redirectInfo去做
  //frontend.io.invalidateICache := backend.io.commitToCsr.fenceI || backend.io.cacheOpICacheReq
  frontend.io.idle := coreIdle


  diffDontTouch(backend.io.lsEnq)
  backend.io.lsEnq <> memory.io.lsEnq

  diffDontTouch(backend.io.toMemResult(0)) //load+store的地址
  diffDontTouch(backend.io.toMemResult(1)) //store的数据
  // 1.后端传给Memory的数据信息OK
  backend.io.toMemResult(1) <> memory.io.fromExeResult
  // 2.后端传给memory的地址信息处理
  val memaddrtrans = Module(new MemAddrTrans) 
  
  memaddrtrans.io.lrValid := lrValid
  memaddrtrans.io.in <> backend.io.toMemResult(0)
  memory.io.fromExeMmuResult <> memaddrtrans.io.out
  memaddrtrans.io.mmuReq <> mmu.io.fromMem
  memaddrtrans.io.mmuResp <> mmu.io.toMem


  memory.io.toWbResult <> backend.io.fromMemResult
  memory.io.robHead <> backend.io.robHead

  
  for (i <- 0 until CommitWidth) {
    memory.io.robCommit(i).valid := backend.io.commitToSq.valid(i)
    memory.io.robCommit(i).sqIdx := backend.io.commitToSq.bits(i).sqIdx.value
  }


  memory.io.redirect.valid := false.B
  memory.io.redirect.bits.robIdx.value := 0.U
  memory.io.redirect.bits.robIdx.flag := true.B
  
  backend.io.flush := false.B


    // laji
  frontend.io.redirect.valid  := false.B
  frontend.io.redirect.target := 0.U
  frontend.io.redirect.rtype  := 0.U
  // 后端BPU更新

  frontend.io.bpuUpdateBr        <> backend.io.bpuUpdate

  diffDontTouch(frontend.io.out)

 
  // ---------- MMU / TLB ----------
  frontend.io.mmu.toMmu <> mmu.io.fromIcache
  frontend.io.mmu.fromMmu <> mmu.io.toIcache


 
  // ---------- CSR ----------
  val csr = Module(new CsrFile)
  lrValid := csr.io.lrValid
  csr.io.timerInfo <> backend.io.timerInfo
  csr.io.irqBus <> intrpt
  csr.io.rReq <> backend.io.csrReq
  csr.io.rResp <> backend.io.csrResp

  backend.io.extInt :=  csr.io.hasIrq
  backend.io.intrCode := csr.io.intrCode

  when(backend.io.idle) {
    coreIdle := true.B
  }.elsewhen(csr.io.hasIrq) {
    coreIdle := false.B
  }

  csr.io.wReq.wen := backend.io.commitToCsr.csrWen
  csr.io.wReq.addr := backend.io.commitToCsr.csrWaddr
  csr.io.wReq.data := backend.io.commitToCsr.csrWdata
  csr.io.lrValidSet := backend.io.commitToCsr.lrValidSet
  csr.io.lrValidClear := backend.io.commitToCsr.lrValidClear

  memory.io.fenceIReq := backend.io.fenceIReq
  backend.io.fenceIReady := memory.io.fenceIReady
  
  csr.io.trapReq := backend.io.trapReq
  csr.io.trapEnv <> backend.io.trapEnv
  csr.io.commitValid := backend.io.commitValid

  // ---------- TLB 冲刷（sfence.vma / satp 写） ----------
  val sfenceFlush = Wire(new TlbFlush)
  sfenceFlush.valid  := backend.io.tlbInstr.valid
  sfenceFlush.all    := backend.io.tlbInstr.bits.all
  sfenceFlush.noAsid := backend.io.tlbInstr.bits.noAsid
  sfenceFlush.vaddr  := backend.io.tlbInstr.bits.vaddr
  sfenceFlush.asid   := backend.io.tlbInstr.bits.asid

  val satpFlush = Wire(new TlbFlush)
  satpFlush.valid  := csr.io.flushTlb
  satpFlush.all    := true.B
  satpFlush.noAsid := true.B
  satpFlush.vaddr  := 0.U
  satpFlush.asid   := 0.U

  mmu.io.tlbFlush := Mux(csr.io.flushTlb, satpFlush, sfenceFlush)
  backend.io.tlbFillIdx := mmu.io.tlbFillIdx

  backend.io.currentPriv := csr.io.priv.curPriv

  mmu.io.fromCsr := csr.io.mmuCtrl

  mmu.io.fromIcacheFlush := backend.io.redirectInfo.valid && backend.io.redirectInfo.bits.doRedirect

  //Rob的重定向才去做两者的flush
  //实际上这里是根本不需要去做刷掉的，因为LSQ中自然会刷
  //但有很必要是因为，如果某次rob redirct改变了地址映射方式（恰好在mmu中的state 由sdie —> busy这个时期映射方式改变）
  //就可能会导致mmu的状态机堵住
  //于是mmu必然需要刷新机制回到idle
  //既然mmu需要刷新机制，那memaddrtrans也还是需要刷新，不然就阻塞了
  //而Rob发出的redirct信号一定是可以刷这里的
  mmu.io.fromMemFlush := backend.io.redirectInfo.valid && backend.io.redirectInfo.bits.doRedirect && backend.io.redirectInfo.bits.fromRob
  memaddrtrans.io.flush := backend.io.redirectInfo.valid && backend.io.redirectInfo.bits.doRedirect && backend.io.redirectInfo.bits.fromRob

  // I/D L1通过分组native端口接入统一L2，L2是唯一DDR master。
  val l2cache = Module(new L2Cache)
  // I-Cache 与页表遍历器共享 L2 只读端口
  val ptwArb = Module(new L2ReadArbiter)
  ptwArb.io.m(0) <> frontend.io.l2_read
  ptwArb.io.m(1) <> mmu.io.ptwRead
  l2cache.io.icache <> ptwArb.io.s
  l2cache.io.dcache <> memory.io.l2

  // Cache 维护通路保留接口，L2 维护端口仅作扩展预留。
  l2cache.io.maintenance.req.valid := false.B
  l2cache.io.maintenance.req.bits.op := 0.U
  l2cache.io.maintenance.req.bits.addr := 0.U
  l2cache.io.maintenance.done.ready := true.B
 
  // L2 → 顶层AXI3接口
  // AR通道
  arid    := l2cache.io.axi.ar.data.arid
  araddr  := l2cache.io.axi.ar.data.araddr
  arlen   := l2cache.io.axi.ar.data.arlen
  arsize  := l2cache.io.axi.ar.data.arsize
  arburst := l2cache.io.axi.ar.data.arburst
  arlock  := l2cache.io.axi.ar.data.arlock
  arcache := l2cache.io.axi.ar.data.arcache
  arprot  := l2cache.io.axi.ar.data.arprot
  arvalid := l2cache.io.axi.ar.data.arvalid
  l2cache.io.axi.ar.arready := arready
 
  // R通道
  val r_data = Wire(new AXI3RData)
  r_data.rid    := rid
  r_data.rdata  := rdata
  r_data.rresp  := rresp
  r_data.rlast  := rlast
  r_data.rvalid := rvalid
  l2cache.io.axi.r.data := r_data
  rready := l2cache.io.axi.r.rready
 
  // AW通道
  awid    := l2cache.io.axi.aw.data.awid
  awaddr  := l2cache.io.axi.aw.data.awaddr
  awlen   := l2cache.io.axi.aw.data.awlen
  awsize  := l2cache.io.axi.aw.data.awsize
  awburst := l2cache.io.axi.aw.data.awburst
  awlock  := l2cache.io.axi.aw.data.awlock
  awcache := l2cache.io.axi.aw.data.awcache
  awprot  := l2cache.io.axi.aw.data.awprot
  awvalid := l2cache.io.axi.aw.data.awvalid
  l2cache.io.axi.aw.awready := awready
 
  // W通道
  wid     := l2cache.io.axi.w.data.wid
  wdata   := l2cache.io.axi.w.data.wdata
  wstrb   := l2cache.io.axi.w.data.wstrb
  wlast   := l2cache.io.axi.w.data.wlast
  wvalid  := l2cache.io.axi.w.data.wvalid
  l2cache.io.axi.w.wready := wready
 
  // B通道
  val b_data = Wire(new AXI3BData)
  b_data.bid    := bid
  b_data.bresp  := bresp
  b_data.bvalid := bvalid
  l2cache.io.axi.b.data := b_data
  bready := l2cache.io.axi.b.bready
 
  // ================================================================
  // 调试信号
  // ================================================================
  // 从前端IBuffer取第一条有效指令作为调试输出
  //  val dbgFirstValid = frontend.io.out(0).fire
  //  when(dbgFirstValid) {
  //    debug0_wb_pc       := frontend.io.out(0).bits.pc
  //    debug0_wb_inst     := frontend.io.out(0).bits.instr(31, 0)
  //    debug0_wb_rf_wen   := false.B    // 后端实现后连接写回使能
  //    debug0_wb_rf_wnum  := 0.U
  //    debug0_wb_rf_wdata := 0.U
  //    ws_valid           := true.B
  //  }.otherwise {
  //    ws_valid := false.B
  //  }
 
  // ================================================================
  // Difftest 协同仿真
  // ================================================================
  if (EnableDifftest) {
    val difftest = Module(new DifftestInCore)
    val difftestInfo = Wire(new CoreDifftestBundle)

    difftestInfo.commit := backend.difftest.get.commit
    difftestInfo.regs   := backend.difftest.get.regs
    difftestInfo.csr    := csr.difftest.get
    difftest.io := difftestInfo

    val legacyCommit = backend.difftest.get.commit(0)
    ws_valid           := legacyCommit.valid
    debug0_wb_pc       := legacyCommit.pc(31, 0)
    debug0_wb_rf_wen   := legacyCommit.valid && legacyCommit.rfWen
    debug0_wb_rf_wnum  := legacyCommit.wdest
    debug0_wb_rf_wdata := legacyCommit.wdata(31, 0)
    debug0_wb_inst     := legacyCommit.instr
    rf_rdata           := backend.difftest.get.regs(reg_num)
  }

  
 
  // ========== 信号防优化 ==========
  dontTouch(break_point)
  dontTouch(infor_flag)
  dontTouch(reg_num)
  dontTouch(arid)
  dontTouch(araddr)
  dontTouch(rid)
  dontTouch(rdata)
  dontTouch(awid)
  dontTouch(awaddr)
  dontTouch(wid)
  dontTouch(wdata)
  dontTouch(bid)
  dontTouch(bresp)
 
  } // end withClockAndReset
}
