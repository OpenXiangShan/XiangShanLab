package minixiangshan.backend.issue
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.backend.decode._
import minixiangshan.backend.dispatch._
import minixiangshan.backend.rename._
import minixiangshan.backend.execute._
import minixiangshan.config.IQParams
 
/**
 * ═══════════════════════════════════════════════════════════════
 *  发射队列（IssueQueue）—— 含快速唤醒 + dataSource 机制
 *
 *  数据结构：
 *    - 空闲位图管理出入队
 *    - 年龄矩阵择优发射（最老就绪优先）
 *    - 写回唤醒广播（pdst 匹配，同拍生效，dataSource = regFile）
 *    - 快速唤醒广播（IQ fire 时发出，dataSource = exeUnit）
 *
 *  dataSource 变换规则：
 *    - wakeup 生效拍：dataSource = exeUnit（ExeUnit Phase2 旁路）
 *    - 1 拍后未 fire：exeUnit → regFile（ExeUnit 旁路已过期，数据在 PRF）
 *    - 新 wakeup 覆盖旧 dataSource（"最后唤醒获胜"）
 *
 *  不包含：
 *    - 投机 Load 唤醒
 *    - blocked 位
 * ═══════════════════════════════════════════════════════════════
 */
class IssueQueue(val iqParams: IQParams)(implicit p: Parameters) extends NSModule with HasCoreParameters {
  val io = IO(new Bundle {
    // ── 从分发阶段入队 ──
    val enq           = Flipped(ValidIO(new DispatchedInst))
    // ── 发射到 RegisterRead（携带 dataSource / exeSource） ──
    val issue         = Decoupled(new RegReadIssue)
    // ── 写回唤醒广播（原有，dataSource=regFile） ──
    val wakeupPorts   = Input(Vec(iqParams.numWakeupPorts, Valid(new IssueWakeup)))
    // ── 快速唤醒广播（IQ fire 时发出，dataSource=exeUnit） ──
    val fastWakeup    = Input(Vec(IQNum - 2, new WakeupSignal))
    // ── 执行阶段唤醒（仅前三个执行单元，更新 ready 不参与 pEff 关键路径） ──
    val execWakeup    = Input(Vec(3, new WakeupSignal))
    // ── 本 IQ 发出的快速唤醒信号 ──
    val wakeupOut     = Output(new WakeupSignal)
    // ── 重定向 / 冲刷 ──
    val redirectInfo  = Flipped(ValidIO(new redirectInfoToModule))
    val flushPipeline = Input(Bool())
    // ── 反馈给分发阶段 ──
    val freeEntries   = Output(UInt(log2Ceil(iqParams.numEntries + 1).W))
  })
 
  val N = iqParams.numEntries
  val freeEntriesWidth = log2Ceil(N + 1)

 
  // ══════════════════════════════════════════════════════════════
  //  表项存储
  // ══════════════════════════════════════════════════════════════
  val entryValid       = RegInit(VecInit(Seq.fill(N)(false.B)))
  val entryUops        = RegInit(VecInit(Seq.fill(N)(0.U.asTypeOf(new DispatchedInst))))
  val entryP1Ready     = RegInit(VecInit(Seq.fill(N)(false.B)))
  val entryP2Ready     = RegInit(VecInit(Seq.fill(N)(false.B)))
  val entrySrc1DS      = RegInit(VecInit(Seq.fill(N)(DataSource.regFile)))  // ★ dataSource
  val entrySrc2DS      = RegInit(VecInit(Seq.fill(N)(DataSource.regFile)))
  val entrySrc1ExeSrc  = RegInit(VecInit(Seq.fill(N)(0.U(log2Ceil(IQNum).W)))) // ★ exeSource
  val entrySrc2ExeSrc  = RegInit(VecInit(Seq.fill(N)(0.U(log2Ceil(IQNum).W))))
 
  // ══════════════════════════════════════════════════════════════
  //  年龄矩阵 age[i][j]=1 表示 entry[i] 比 entry[j] 更老
  // ══════════════════════════════════════════════════════════════
  val age = RegInit(VecInit(Seq.fill(N)(VecInit(Seq.fill(N)(false.B)))))
 
  // ══════════════════════════════════════════════════════════════
  //  唤醒逻辑（组合逻辑，同拍生效）
  //
  //  两类唤醒：
  //    1. wakeupPorts（写回广播）：pdst 匹配 → operand ready，dataSource = regFile
  //    2. fastWakeup（IQ fire 快速唤醒）：pdst 匹配 → operand ready，
  //       dataSource = exeUnit，exeSource = 指定 ExeUnit 端口
  //
  //  Chisel when 优先级：后写的覆盖前写的 → fastWakeup 在 wakeupPorts 之后处理，
  //  保证"最后唤醒获胜"。
  // ══════════════════════════════════════════════════════════════
  val p1WakeupWB  = Wire(Vec(N, Bool()))  // 写回唤醒 p1
  val p2WakeupWB  = Wire(Vec(N, Bool()))  // 写回唤醒 p2
  val p1WakeupLoadWB = Wire(Vec(N, Bool())) // LoadQ 写回口同周期唤醒 p1
  val p2WakeupLoadWB = Wire(Vec(N, Bool())) // LoadQ 写回口同周期唤醒 p2
  val p1WakeupFast = Wire(Vec(N, Bool())) // 快速唤醒 p1
  val p2WakeupFast = Wire(Vec(N, Bool())) // 快速唤醒 p2
  val p1WakeupExec = Wire(Vec(N, Bool())) // 执行阶段唤醒 p1
  val p2WakeupExec = Wire(Vec(N, Bool())) // 执行阶段唤醒 p2
  val p1FastExeSrc = Wire(Vec(N, UInt(log2Ceil(IQNum - 2).W))) // 快速唤醒 p1 的 exeSource
  val p2FastExeSrc = Wire(Vec(N, UInt(log2Ceil(IQNum - 2).W))) // 快速唤醒 p2 的 exeSource
  dontTouch(p1FastExeSrc)
  dontTouch(p2FastExeSrc)
 
  for (i <- 0 until N) {
    // ── 写回唤醒 ──
    val wbP1Matches = Wire(Vec(iqParams.numWakeupPorts, Bool()))
    val wbP2Matches = Wire(Vec(iqParams.numWakeupPorts, Bool()))
    for (w <- 0 until iqParams.numWakeupPorts) {
      val pdst   = io.wakeupPorts(w).bits.pdst
      val wValid = io.wakeupPorts(w).valid && entryValid(i)
      wbP1Matches(w) := wValid && entryUops(i).rs1Valid && entryUops(i).prs1 === pdst && pdst =/= 0.U
      wbP2Matches(w) := wValid && entryUops(i).rs2Valid && entryUops(i).prs2 === pdst && pdst =/= 0.U
    }
    p1WakeupWB(i) := wbP1Matches.asUInt.orR
    p2WakeupWB(i) := wbP2Matches.asUInt.orR
    p1WakeupLoadWB(i) := wbP1Matches(3)
    p2WakeupLoadWB(i) := wbP2Matches(3)
 
    // ── 快速唤醒 ──
    val fastP1Matches = Wire(Vec(IQNum - 2, Bool()))
    val fastP2Matches = Wire(Vec(IQNum - 2, Bool()))
    for (w <- 0 until IQNum - 2) {
      val fw      = io.fastWakeup(w)
      val fwValid = fw.valid && entryValid(i)
      val pdst    = fw.pdst
      fastP1Matches(w) := fwValid && entryUops(i).rs1Valid && entryUops(i).prs1 === pdst && pdst =/= 0.U && entryUops(i).ctrl.fuType =/= FuType.div
      fastP2Matches(w) := fwValid && entryUops(i).rs2Valid && entryUops(i).prs2 === pdst && pdst =/= 0.U && entryUops(i).ctrl.fuType =/= FuType.div
    }
    p1WakeupFast(i) := fastP1Matches.asUInt.orR
    p2WakeupFast(i) := fastP2Matches.asUInt.orR

    // ── 执行阶段唤醒 ──
    val execP1Matches = Wire(Vec(3, Bool()))
    val execP2Matches = Wire(Vec(3, Bool()))
    for (w <- 0 until 3) {
      val ew      = io.execWakeup(w)
      val ewValid = ew.valid && entryValid(i)
      val pdst    = ew.pdst
      execP1Matches(w) := ewValid && entryUops(i).rs1Valid && entryUops(i).prs1 === pdst && pdst =/= 0.U
      execP2Matches(w) := ewValid && entryUops(i).rs2Valid && entryUops(i).prs2 === pdst && pdst =/= 0.U
    }
    p1WakeupExec(i) := execP1Matches.asUInt.orR
    p2WakeupExec(i) := execP2Matches.asUInt.orR

    // exeSource：PriorityMux 从匹配端口选 exeSource
    // 同拍同一操作数最多 1 个 pdst 匹配（rename 保证唯一），PriorityMux 结果唯一确定
    p1FastExeSrc(i) := Mux(p1WakeupFast(i),
      PriorityMux(fastP1Matches.zipWithIndex.map { case (m, w) => m -> io.fastWakeup(w).exeSource }),
      0.U(log2Ceil(IQNum - 2).W))
    p2FastExeSrc(i) := Mux(p2WakeupFast(i),
      PriorityMux(fastP2Matches.zipWithIndex.map { case (m, w) => m -> io.fastWakeup(w).exeSource }),
      0.U(log2Ceil(IQNum - 2).W))
  }
 
  // ── 合后的唤醒信号（写回 OR 快速） ──
  val p1Wakeup = Wire(Vec(N, Bool()))
  val p2Wakeup = Wire(Vec(N, Bool()))
  for (i <- 0 until N) {
    p1Wakeup(i) := p1WakeupWB(i) || p1WakeupFast(i) || p1WakeupExec(i)
    p2Wakeup(i) := p2WakeupWB(i) || p2WakeupFast(i) || p2WakeupExec(i)
  }
 
  // ── 有效就绪位 = 寄存器值 ∨ 本拍唤醒 ──
  val p1Eff = Wire(Vec(N, Bool()))
  val p2Eff = Wire(Vec(N, Bool()))
  for (i <- 0 until N) {
    p1Eff(i) := entryP1Ready(i) || p1WakeupLoadWB(i)
    p2Eff(i) := entryP2Ready(i) || p2WakeupLoadWB(i)
  }
 
  // ══════════════════════════════════════════════════════════════
  //  重定向 Kill 逻辑
  // ══════════════════════════════════════════════════════════════
  val killed = Wire(Vec(N, Bool()))
  val redirectRobIdx = io.redirectInfo.bits.robIdx
  for (i <- 0 until N) {
    val isNewer = entryUops(i).robIdxFull.isAfter(redirectRobIdx)
    killed(i) := entryValid(i) && io.redirectInfo.valid && io.redirectInfo.bits.doRedirect && isNewer
  }
 
  // ══════════════════════════════════════════════════════════════
  //  请求 & 年龄仲裁
  // ══════════════════════════════════════════════════════════════
  val request = Wire(Vec(N, Bool()))
  for (i <- 0 until N) {
    request(i) := entryValid(i) && p1Eff(i) && p2Eff(i) && !killed(i)
  }
 
  val oldest = Wire(Vec(N, Bool()))
  for (i <- 0 until N) {
    var hasOlder = false.B
    for (j <- 0 until N if j != i) {
      hasOlder = hasOlder || (request(j) && !age(i)(j))
    }
    oldest(i) := request(i) && !hasOlder
  }
 
  val grant = oldest
 
  // ══════════════════════════════════════════════════════════════
  //  发射输出（Decoupled 握手）
  //  输出 RegReadIssue = DispatchedInst + dataSource + exeSource
  // ══════════════════════════════════════════════════════════════
  val grantUop    = Mux1H(grant, entryUops)
  val grantSrc1DS = Mux1H(grant, entrySrc1DS)
  val grantSrc2DS = Mux1H(grant, entrySrc2DS)
  val grantSrc1ES = Mux1H(grant, entrySrc1ExeSrc)
  val grantSrc2ES = Mux1H(grant, entrySrc2ExeSrc)
 
  io.issue.valid            := grant.reduce(_ || _)
  io.issue.bits.uop         := grantUop
  io.issue.bits.src1DataSource := grantSrc1DS
  io.issue.bits.src2DataSource := grantSrc2DS
  io.issue.bits.src1ExeSource  := grantSrc1ES
  io.issue.bits.src2ExeSource  := grantSrc2ES
 
  val issueFire = io.issue.valid && io.issue.ready
 
  // ══════════════════════════════════════════════════════════════
  //  本 IQ 发出的快速唤醒信号
  //  仅 ALU / BRU / CSR（确定延迟指令）在 fire 时发出
  // ══════════════════════════════════════════════════════════════
  val grantFuType = grantUop.ctrl.fuType
  val isFastWakeup = issueFire && 
    (grantFuType === FuType.alu || grantFuType === FuType.bru || grantFuType === FuType.csr)
 
  io.wakeupOut.valid     := isFastWakeup
  io.wakeupOut.exeSource := iqParams.exeSource.U   // ★ IQ 编号 = ExeUnit 端口编号
  diffDontTouch(io.wakeupOut.exeSource)
  io.wakeupOut.pdst      := grantUop.pdst
 
  // ══════════════════════════════════════════════════════════════
  //  入队逻辑（空闲位图 + 优先编码器）
  // ══════════════════════════════════════════════════════════════
  val freeMask = VecInit((0 until N).map(i => !entryValid(i)))
  val enqIdx   = PriorityEncoder(freeMask)
  val hasFree  = freeMask.asUInt.orR
  val enqFire  = io.enq.valid && hasFree
 
  val validAfterKillGrant = Wire(Vec(N, Bool()))
  for (i <- 0 until N) {
    validAfterKillGrant(i) := entryValid(i) && !killed(i) && !(grant(i) && issueFire)
  }
 
  // ══════════════════════════════════════════════════════════════
  //  状态更新
  //  优先级：flush > kill > grant > enq > dataSource变换 > wakeup
  //
  //  dataSource 变换规则：
  //    当 dataSource = exeUnit 且本拍未 fire → exeUnit → regFile
  //    （ExeUnit Phase2 旁路只在唤醒生效后 1 拍有效）
  //
  //  wakeup 写入在 dataSource 变换之后处理 → Chisel when 后写覆盖前写
  //  保证新 fastWakeup 的 exeUnit 覆盖变换后的 regFile（"最后唤醒获胜"）
  // ══════════════════════════════════════════════════════════════
  for (i <- 0 until N) {
 
    // ── valid ──
    when(io.flushPipeline) {
      entryValid(i) := false.B
    }.elsewhen(killed(i)) {
      entryValid(i) := false.B
    }.elsewhen(grant(i) && issueFire) {
      entryValid(i) := false.B
    }.elsewhen(enqFire && enqIdx === i.U) {
      entryValid(i) := true.B
    }
 
    // ── P1Ready / P2Ready ──
    when(io.flushPipeline || killed(i) || (grant(i) && issueFire)) {
      entryP1Ready(i) := false.B
      entryP2Ready(i) := false.B
    }.elsewhen(enqFire && enqIdx === i.U) {
      entryP1Ready(i) := !io.enq.bits.prs1Busy || !io.enq.bits.rs1Valid
      entryP2Ready(i) := !io.enq.bits.prs2Busy || !io.enq.bits.rs2Valid
    }.otherwise {
      entryP1Ready(i) := entryP1Ready(i) || p1Wakeup(i)
      entryP2Ready(i) := entryP2Ready(i) || p2Wakeup(i)
    }
 
    // ── dataSource 变换（★ 新增） ──
    //  规则：exeUnit → regFile，仅当表项存活且本拍未 fire
    //  变换发生在 wakeup 写入之前，后续 wakeup 会覆盖
    when(entryValid(i) && !killed(i) && !(grant(i) && issueFire)) {
      when(entrySrc1DS(i) === DataSource.exeUnit) {
        entrySrc1DS(i) := DataSource.regFile
      }
      when(entrySrc2DS(i) === DataSource.exeUnit) {
        entrySrc2DS(i) := DataSource.regFile
      }
    }
 
    // ── dataSource / exeSource wakeup 写入（★ 新增） ──
    //  写回唤醒：dataSource = regFile（数据已在 PRF）
    //  快速唤醒：dataSource = exeUnit，exeSource = 指定端口
    //  Chisel when 优先级：后写覆盖前写 → fastWakeup 在 wakeupPorts 之后
 
    // (a) 写回唤醒 → dataSource = regFile
    when(p1WakeupWB(i) && entryValid(i) && !killed(i) && !(grant(i) && issueFire)) {
      entrySrc1DS(i) := DataSource.regFile
    }
    when(p2WakeupWB(i) && entryValid(i) && !killed(i) && !(grant(i) && issueFire)) {
      entrySrc2DS(i) := DataSource.regFile
    }
 
    // (b) 快速唤醒 → dataSource = exeUnit, exeSource = 指定端口
    //     后写覆盖前写 → 覆盖上面的 regFile 和变换后的 regFile ✓
    when(p1WakeupFast(i) && entryValid(i) && !killed(i) && !(grant(i) && issueFire)) {
      entrySrc1DS(i)     := DataSource.exeUnit
      entrySrc1ExeSrc(i) := p1FastExeSrc(i)
    }
    when(p2WakeupFast(i) && entryValid(i) && !killed(i) && !(grant(i) && issueFire)) {
      entrySrc2DS(i)     := DataSource.exeUnit
      entrySrc2ExeSrc(i) := p2FastExeSrc(i)
    }
 
    // ── 入队时初始化 dataSource / exeSource ──
    when(enqFire && enqIdx === i.U) {
      entrySrc1DS(i)     := DataSource.regFile  // 入队时数据来自 PRF
      entrySrc2DS(i)     := DataSource.regFile
      entrySrc1ExeSrc(i) := 0.U
      entrySrc2ExeSrc(i) := 0.U
    }
 
    // ── flush / kill / grant 清除 dataSource ──
    when(io.flushPipeline || killed(i) || (grant(i) && issueFire)) {
      entrySrc1DS(i)     := DataSource.regFile
      entrySrc2DS(i)     := DataSource.regFile
      entrySrc1ExeSrc(i) := 0.U
      entrySrc2ExeSrc(i) := 0.U
    }
 
    // ── uop ──
    when(enqFire && enqIdx === i.U) {
      entryUops(i) := io.enq.bits
    }
 
    // ── 年龄矩阵 ──
    age(i)(i) := false.B
    for (j <- 0 until N if j != i) {
      when(io.flushPipeline) {
        age(i)(j) := false.B
      }.elsewhen(killed(i) || killed(j) || (grant(i) && issueFire) || (grant(j) && issueFire)) {
        age(i)(j) := false.B
      }.elsewhen(enqFire && enqIdx === j.U) {
        age(i)(j) := validAfterKillGrant(i)
      }.elsewhen(enqFire && enqIdx === i.U) {
        age(i)(j) := false.B
      }
    }
  }
 
  // ══════════════════════════════════════════════════════════════
  //  反馈信号
  // ══════════════════════════════════════════════════════════════
  //io.freeEntries := PopCount(freeMask)
  val freeEntriesReg = RegInit(N.U(freeEntriesWidth.W))
  val killedCount = PopCount(killed)
 
  when(io.flushPipeline) {
    freeEntriesReg := N.U(freeEntriesWidth.W)
  }.otherwise {
    freeEntriesReg := freeEntriesReg +& issueFire.asUInt  +& killedCount -& enqFire.asUInt
  }
 
  // ================================================================
  //  ★ 反馈信号 —— 寄存器输出，切断跨模块组合关键路径
  // ================================================================
  io.freeEntries := freeEntriesReg


}
