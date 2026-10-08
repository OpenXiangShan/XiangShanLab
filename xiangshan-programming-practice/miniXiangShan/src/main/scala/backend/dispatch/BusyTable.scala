package minixiangshan.backend.dispatch
 
import chisel3._
import chisel3.util._
import minixiangshan.config._
 
/**
 * ═══════════════════════════════════════════════════════════════
 *  忙表（BusyTable）
 * ═══════════════════════════════════════════════════════════════
 *
 *  位图式：bit[i]=1 表示物理寄存器 pi 的值尚未写回（忙）
 *
 *  操作：
 *    · allocReq：重命名分配新 pdst 时置 1（忙）
 *    · wbReq：执行单元写回时清 0（就绪）
 *    · readReq/Resp：查询某物理寄存器是否忙
 *
 *  p0 永远为就绪（对应 r0 恒零）
 * ═══════════════════════════════════════════════════════════════
 */
class BusyTable(implicit p: Parameters) extends NSModule {
  val io = IO(new BusyTableIO)
 
  // ================================================================
  //  1. 核心位图：初始全 0（所有物理寄存器就绪）
  //  p0 对应的 bit0 永远为 0
  // ================================================================
  val table = RegInit(UInt(IntPhyRegs.W), 0.U)
 
  // ================================================================
  //  2. 写回清忙：wbReq 中有效的 pdst 对应位清零
  // ================================================================
  val wbClearMask = io.wbReq.map { w =>
    Mux(w.valid, UIntToOH(w.bits)(IntPhyRegs - 1, 0), 0.U(IntPhyRegs.W))
  }.reduce(_ | _)
 
  // ================================================================
  //  3. 分配置忙：allocReq 中有效的 pdst 对应位置 1
  // ================================================================
  val allocSetMask = io.allocReq.map { a =>
    Mux(a.valid, UIntToOH(a.bits)(IntPhyRegs - 1, 0), 0.U(IntPhyRegs.W))
  }.reduce(_ | _)
 
  // ================================================================
  //  4. 更新：先清忙再置忙（写回优先级高于分配）
  //  确保 p0 永远为 0（& ~1.U）
  // ================================================================
  table := ((table & (~wbClearMask).asUInt) | allocSetMask) & (~1.U(IntPhyRegs.W)).asUInt
 
  // ================================================================
  //  5. 读端口：查询物理寄存器是否忙
  //  含分配旁路：如果本周期刚分配的寄存器，也视为忙
  // ================================================================
  for (i <- 0 until CtrlBlockWidth * 2) {
    val allocBypass = io.allocReq.map(a => a.valid && a.bits === io.readReq(i)).reduce(_ || _)
    io.readResp(i) := table(io.readReq(i)) || allocBypass
  }
}