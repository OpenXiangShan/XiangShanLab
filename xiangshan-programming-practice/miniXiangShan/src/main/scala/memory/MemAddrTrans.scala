package minixiangshan.mem

import chisel3._
import chisel3.util._
import minixiangshan.config._
import minixiangshan.mmu._
import minixiangshan.backend.execute._
import minixiangshan.backend.decode.LsuOp

class MemAddrTrans(implicit p: Parameters) extends NSModule {
  val io = IO(new Bundle {
    // 接收执行级(Exe)传入的数据
    val in       = Flipped(Decoupled(new ExeResult))
    // 组合完毕后发往访存级(Mem)的数据包
    val out      = Decoupled(new ExeMmuResult)

    // 与 MMU 的请求与响应接口
    val mmuReq   = Decoupled(new SqToMmuReq)
    val mmuResp  = Flipped(Decoupled(new MmuToSqResp))

    // 全局冲刷信号（如遇异常或分支预测错误）
    val flush    = Input(Bool())
    val lrValid    = Input(Bool())
  })

  // ================================================================
  //  组合逻辑透传：直接向 MMU 发起地址翻译请求 (省去一拍延迟)
  // ================================================================

  // reservation 无效时的 SC.W 直接失败，不进行地址翻译。
  val in_sc_fail = io.in.bits.uop.ctrl.lsuOp === LsuOp.scw && !io.lrValid

  // MMU 能够接收（或无需接收）的条件
  val can_issue_mmu = in_sc_fail || io.mmuReq.ready

  // 预留单级流水线 (S1) 的准备好信号
  val s1_ready = Wire(Bool())

  // 握手成功条件
  val in_fire = io.in.valid && s1_ready && can_issue_mmu

  // 告知上一级是否可以接收新数据
  io.in.ready := s1_ready && can_issue_mmu

  // 对 MMU 发起请求：直接用 io.in 驱动，且必须在本级流水能够接收时才拉高 valid
  io.mmuReq.valid      := io.in.valid && s1_ready && !in_sc_fail
  io.mmuReq.bits.vaddr := io.in.bits.data      // 虚拟地址
  io.mmuReq.bits.lsuOp := MuxLookup(io.in.bits.uop.ctrl.lsuOp,
    io.in.bits.uop.ctrl.lsuOp)(Seq(
      LsuOp.lrw -> LsuOp.lw,
      LsuOp.scw -> LsuOp.sw
    ))

  // ================================================================
  //  Stage 1: 等待并接收 MMU 响应，打包发往 Memory 级
  // ================================================================
  val s1_valid    = RegInit(false.B)
  val s1_exe_data = RegInit(0.U.asTypeOf(new ExeResult))
  val s1_sc_fail  = RegInit(false.B)

  // [关键缓冲器]：应对 MMU 响应可能晚于请求的问题
  val s1_mmu_done = RegInit(false.B)
  val s1_mmu_resp = RegInit(0.U.asTypeOf(new MmuToSqResp))

  // 当前 S1 向外发送的条件：有元数据，且(MMU已缓冲完毕 OR MMU本拍刚好响应)
  val s1_result_valid = s1_sc_fail || s1_mmu_done || io.mmuResp.valid
  val out_fire = s1_valid && s1_result_valid && io.out.ready

  // S1 能够接收上一级新数据的条件
  s1_ready := !s1_valid || out_fire

  // 状态机与 Skid Buffer 捕获逻辑
  when(io.flush) {
    s1_valid    := false.B
    s1_mmu_done := false.B
    s1_sc_fail  := false.B
  }.otherwise {
    when(s1_ready) {
      // 成功接收新请求时，用新数据覆盖，并重置缓冲状态
      s1_valid    := in_fire
      s1_exe_data := io.in.bits
      s1_sc_fail  := in_sc_fail
      s1_mmu_done := false.B
    }.otherwise {
      // 防丢失捕获网：如果本周期由于下游阻塞导致 S1 停滞
      // 且 MMU 恰好给出了结果，必须把响应结果暂存起来
      when(!s1_sc_fail && !s1_mmu_done && io.mmuResp.valid) {
        s1_mmu_done := true.B
        s1_mmu_resp := io.mmuResp.bits
      }
    }
  }

  // 告知 MMU：本端口不反压，靠 S1 的 Skid Buffer 兜底
  io.mmuResp.ready := true.B

  // ================================================================
  //  打包输出给访存板块 (Memory)
  // ================================================================
  io.out.valid := s1_valid && s1_result_valid
  io.out.bits.exeRes := s1_exe_data

  // 如果之前因为阻塞把数据捕获了，就用寄存器里的(s1_mmu_resp)
  // 如果恰好这拍刚好到达，就直接用线上的数据(io.mmuResp.bits)
  io.out.bits.mmuRes := Mux(s1_sc_fail,
    0.U.asTypeOf(new MmuToSqResp),
    Mux(s1_mmu_done, s1_mmu_resp, io.mmuResp.bits))

  io.out.bits.scSuccess := s1_exe_data.uop.ctrl.lsuOp === LsuOp.scw && !s1_sc_fail
}
