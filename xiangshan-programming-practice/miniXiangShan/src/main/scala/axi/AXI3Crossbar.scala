package minixiangshan.axi
 
import chisel3._
import chisel3.util._
import minixiangshan.config.NSModule
import minixiangshan.config.Parameters
 
/**
  * AXI3 2-to-1 Crossbar（锁定仲裁方式，AXI3 协议合规）
  *
  * 将 icache 和 dcache 两个 Master 的 AXI3 请求汇聚到 1 个 Slave 输出。
  *
  * ID 编码规则（由 Parameters 定义）：
  *   - dcache: ID ∈ [0, nMshrEntries)                        例: 0, 1, 2, 3
  *   - icache: ID = icacheAxiMissId, icacheAxiNucacheId       例: 4, 5
  *
  * 路由策略：
  *   - 请求通道 (AR / AW / W)：锁定仲裁
  *     一旦某个 Master 获得通道权，锁定直到握手完成或该 Master 撤下 valid 才释放。
  *     AR/AW：单拍握手完成或 valid 撤下即释放；W：整个 burst（wlast 握手）
  *     完成或 valid 撤下才释放。
  *     这确保 VALID 信号不会被仲裁切换中途撤走，符合 AXI3 规范；
  *     同时当 Master 因冲刷等原因主动撤下 valid 时，不会死锁占用通道。
  *   - 响应通道 (R / B)：依据 rid / bid 路由到对应 Master
  *
  * W 通道与 AW 通道独立仲裁，通过 WID 标识事务归属，天然支持乱序写。
  * W burst 期间锁定，防止不同 Master 的 W 数据交错。
  */
class AXI3Crossbar2to1(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    val in_icache = Flipped(new AXI3MasterIO)   // 从 icache 接收请求
    val in_dcache = Flipped(new AXI3MasterIO)   // 从 dcache 接收请求
    val out       = new AXI3MasterIO            // 向外发出请求
  })
 
  // ── ID 路由判断 ─────────────────────────────────────────────
  private def toIcache(id: UInt): Bool = id >= nMshrEntries.U
 
  // ══════════════════════════════════════════════════════════════
  //  锁定仲裁器：单拍握手通道（AR / AW）
  //
  //  行为：
  //    1. 未锁定时：按优先级选择有 valid 请求的 Master
  //    2. 选中的 Master 如果本拍未完成握手（valid && !ready），则锁定
  //    3. 锁定期间：始终输出锁定 Master 的信号，其他 Master 的请求被挂起
  //    4. 释放条件：
  //       a) 握手完成（valid && ready）
  //       b) 被锁定 Master 主动撤下 valid（冲刷等场景）
  //    5. 释放后下一拍可重新仲裁
  //
  //  优先级：dcache(1) > icache(0)，与原设计一致
  // ══════════════════════════════════════════════════════════════
 
  // ── AR 通道 ──
  val arLocked   = RegInit(false.B)
  val arWinner   = RegInit(false.B)   // false=icache(0), true=dcache(1)
 
  val icacheARValid = io.in_icache.ar.data.arvalid
  val dcacheARValid = io.in_dcache.ar.data.arvalid
 
  // 当前选择：锁定时用寄存器，未锁定时按优先级仲裁
  val arSel = Mux(arLocked, arWinner,
               Mux(dcacheARValid, true.B, false.B))  // dcache优先
 
  // 输出 valid：选中 Master 的 valid
  val arOutValid = Mux(arSel,
                    dcacheARValid,
                    icacheARValid)
 
  // 握手检测
  val arHandshake = arOutValid && io.out.ar.arready
 
  // 锁定管理
  when(!arLocked) {
    when((icacheARValid || dcacheARValid) && !arHandshake) {
      // 有请求但本拍未完成握手 → 锁定到胜者
      arLocked := true.B
      arWinner := arSel
    }
  }.otherwise {
    // 释放条件：握手完成 或 被锁定 Master 撤下 valid
    when(arHandshake ){ //|| !arOutValid) {
      arLocked := false.B
    }
  }
 
  // AR 通道输出路由
  io.out.ar.data := Mux(arSel, io.in_dcache.ar.data, io.in_icache.ar.data)
  io.out.ar.data.arvalid := arOutValid
 
  // arready 只传给胜者
  io.in_icache.ar.arready := io.out.ar.arready && !arSel
  io.in_dcache.ar.arready := io.out.ar.arready &&  arSel
 
  // ── AW 通道（逻辑同 AR） ──
  val awLocked   = RegInit(false.B)
  val awWinner   = RegInit(false.B)
 
  val icacheAWValid = io.in_icache.aw.data.awvalid
  val dcacheAWValid = io.in_dcache.aw.data.awvalid
 
  val awSel = Mux(awLocked, awWinner,
               Mux(dcacheAWValid, true.B, false.B))
 
  val awOutValid = Mux(awSel,
                    dcacheAWValid,
                    icacheAWValid)
 
  val awHandshake = awOutValid && io.out.aw.awready
 
  when(!awLocked) {
    when((icacheAWValid || dcacheAWValid) && !awHandshake) {
      awLocked := true.B
      awWinner := awSel
    }
  }.otherwise {
    when(awHandshake){// || !awOutValid) {
      awLocked := false.B
    }
  }
 
  io.out.aw.data := Mux(awSel, io.in_dcache.aw.data, io.in_icache.aw.data)
  io.out.aw.data.awvalid := awOutValid
 
  io.in_icache.aw.awready := io.out.aw.awready && !awSel
  io.in_dcache.aw.awready := io.out.aw.awready &&  awSel
 
  // ══════════════════════════════════════════════════════════════
  //  W 通道：Burst 级锁定仲裁
  //
  //  行为：
  //    1. 未锁定时：按优先级选择有 wvalid 请求的 Master
  //    2. 选中的 Master 如果本拍不是 wlast 握手完成，则锁定
  //    3. 锁定期间：始终输出锁定 Master 的 W 数据
  //    4. 释放条件：
  //       a) burst 最后 beat 握手完成（wlast && valid && ready）
  //       b) 被锁定 Master 撤下 wvalid（冲刷等场景，burst 中途终止）
  // ══════════════════════════════════════════════════════════════
  val wLocked   = RegInit(false.B)
  val wWinner   = RegInit(false.B)
 
  val icacheWValid = io.in_icache.w.data.wvalid
  val dcacheWValid = io.in_dcache.w.data.wvalid
 
  val wSel = Mux(wLocked, wWinner,
              Mux(dcacheWValid, true.B, false.B))
 
  val wOutValid = Mux(wSel,
                   dcacheWValid,
                   icacheWValid)
 
  val wHandshake     = wOutValid && io.out.w.wready
  val wLastHandshake = wHandshake && Mux(wSel,
                          io.in_dcache.w.data.wlast,
                          io.in_icache.w.data.wlast)
 
  when(!wLocked) {
    when((icacheWValid || dcacheWValid) && !wLastHandshake) {
      // 有 W 请求但 burst 本拍未结束 → 锁定
      wLocked := true.B
      wWinner := wSel
    }
  }.otherwise {
    // 释放条件：burst 完成 或 被锁定 Master 撤下 wvalid
    when(wLastHandshake ){ //|| !wOutValid) {
      wLocked := false.B
    }
  }
 
  io.out.w.data := Mux(wSel, io.in_dcache.w.data, io.in_icache.w.data)
  io.out.w.data.wvalid := wOutValid
 
  io.in_icache.w.wready := io.out.w.wready && !wSel
  io.in_dcache.w.wready := io.out.w.wready &&  wSel
 
  // ══════════════════════════════════════════════════════════════
  //  R 通道：基于 rid 路由响应（与原设计一致）
  // ══════════════════════════════════════════════════════════════
  val rToIcache = toIcache(io.out.r.data.rid)
 
  io.in_icache.r.data.rvalid := io.out.r.data.rvalid &&  rToIcache
  io.in_dcache.r.data.rvalid := io.out.r.data.rvalid && !rToIcache
 
  io.in_icache.r.data.rid   := io.out.r.data.rid
  io.in_icache.r.data.rdata := io.out.r.data.rdata
  io.in_icache.r.data.rresp := io.out.r.data.rresp
  io.in_icache.r.data.rlast := io.out.r.data.rlast
 
  io.in_dcache.r.data.rid   := io.out.r.data.rid
  io.in_dcache.r.data.rdata := io.out.r.data.rdata
  io.in_dcache.r.data.rresp := io.out.r.data.rresp
  io.in_dcache.r.data.rlast := io.out.r.data.rlast
 
  io.out.r.rready := io.in_icache.r.rready || io.in_dcache.r.rready //Mux(rToIcache, io.in_icache.r.rready, io.in_dcache.r.rready)
 
  // ══════════════════════════════════════════════════════════════
  //  B 通道：基于 bid 路由响应（与原设计一致）
  // ══════════════════════════════════════════════════════════════
  val bToIcache = toIcache(io.out.b.data.bid)
 
  io.in_icache.b.data.bvalid := io.out.b.data.bvalid &&  bToIcache
  io.in_dcache.b.data.bvalid := io.out.b.data.bvalid && !bToIcache
 
  io.in_icache.b.data.bid   := io.out.b.data.bid
  io.in_icache.b.data.bresp := io.out.b.data.bresp
 
  io.in_dcache.b.data.bid   := io.out.b.data.bid
  io.in_dcache.b.data.bresp := io.out.b.data.bresp
 
  io.out.b.bready := io.in_icache.b.bready || io.in_dcache.b.bready // Mux(bToIcache, io.in_icache.b.bready, io.in_dcache.b.bready)
}