/***************************************************************************************
* Copyright (c) 2026 Beijing Institute of Open Source Chip (BOSC)
*
* XiangShan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

package xiangshan.cache.mmu

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import top.{ArgParser, Generator}
import utility.sram.SramHelper
import xiangshan._
import xiangshan.backend.fu.{PMPReqBundle, PMPRespBundle}

class SmmptL2TlbMptIO(implicit p: Parameters) extends XSBundle with HasPtwConst with MPTCacheParam {
  val csr = Input(new TlbCsrBundle)
  val sfence = Input(new SfenceBundle)
  val wfiReq = Input(Bool())

  val mptReq = new Bundle {
    val ptw = Flipped(DecoupledIO(new MptReqBundle()))
    val hptw = Flipped(DecoupledIO(new MptReqBundle()))
    val llptw = Flipped(DecoupledIO(new MptReqBundle()))
    val merge = Vec(PtwWidth, Flipped(DecoupledIO(new MptReqBundle())))
    val l1MptOnly = Vec(PtwWidth, Flipped(DecoupledIO(UInt(ppnLen.W))))
  }

  val mem = new Bundle {
    val req = DecoupledIO(new L2TlbMemReqBundle())
    val resp = Flipped(ValidIO(UInt(XLEN.W)))
  }

  val pmp = new Bundle {
    val req = ValidIO(new PMPReqBundle())
    val resp = Flipped(new PMPRespBundle())
  }

  val upstreamAccessFault = Input(Bool())
  val resp = ValidIO(new MptRespBundle())
  val routedResp = new Bundle {
    val ptw = ValidIO(new MptRespBundle())
    val hptw = ValidIO(new MptRespBundle())
    val llptw = ValidIO(new MptRespBundle())
    val merge = Vec(PtwWidth, ValidIO(new MptRespBundle()))
    val l1MptOnly = Vec(PtwWidth, ValidIO(new MptRespBundle()))
  }

  val mptStatus = new Bundle {
    val mptEn = Output(Bool())
    val mptAf = Output(Bool())
    val accessFaultMpt = Output(Bool())
    val memReqBlockedByMptAf = Output(Bool())
    val routedMptAf = new Bundle {
      val ptw = Output(Bool())
      val hptw = Output(Bool())
      val llptw = Output(Bool())
      val merge = Output(Vec(PtwWidth, Bool()))
      val l1MptOnly = Output(Vec(PtwWidth, Bool()))
    }
  }

  val memBusy = Output(Bool())
  val flushLatched = Output(Bool())
}

class SmmptL2TlbMptHarness(implicit p: Parameters) extends XSModule with HasPtwConst with MPTCacheParam {
  val io = IO(new SmmptL2TlbMptIO)

  val mptChecker = Module(new MptChecker)
  val mptReqArb = Module(new Arbiter(new MptReqBundle, 4 + PtwWidth))
  val l1MptOnlyArb = Module(new Arbiter(UInt(ppnLen.W), PtwWidth))

  private val ptwMptPort = 0
  private val hptwMptPort = 1
  private val llptwMptPort = 2
  private val lastMptPort = 3
  private val mptOnlyPort = lastMptPort + PtwWidth

  mptReqArb.io.in(ptwMptPort) <> io.mptReq.ptw
  mptReqArb.io.in(hptwMptPort) <> io.mptReq.hptw
  mptReqArb.io.in(llptwMptPort) <> io.mptReq.llptw

  for (i <- 0 until PtwWidth) {
    mptReqArb.io.in(lastMptPort + i) <> io.mptReq.merge(i)
    l1MptOnlyArb.io.in(i) <> io.mptReq.l1MptOnly(i)
  }

  mptReqArb.io.in(mptOnlyPort).valid := l1MptOnlyArb.io.out.valid
  mptReqArb.io.in(mptOnlyPort).bits.reqPA := l1MptOnlyArb.io.out.bits
  mptReqArb.io.in(mptOnlyPort).bits.id := l1MptOnlyArb.io.chosen
  mptReqArb.io.in(mptOnlyPort).bits.mptOnly := true.B
  l1MptOnlyArb.io.out.ready := mptReqArb.io.in(mptOnlyPort).ready

  mptChecker.io.csr := io.csr
  mptChecker.io.sfence := io.sfence
  mptChecker.io.req.valid := mptReqArb.io.out.valid
  mptChecker.io.req.bits := mptReqArb.io.out.bits
  mptReqArb.io.out.ready := mptChecker.io.req.ready

  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed ||
    io.csr.priv.virt_changed || io.csr.mmpt.changed
  val waitingResp = RegInit(false.B)
  val flushLatch = RegInit(false.B)
  val mptEn = io.csr.mmpt.mode =/= 0.U

  mptChecker.io.mem.mask := waitingResp
  mptChecker.io.mem.req.ready := io.mem.req.ready && !flush && !io.wfiReq
  io.mem.req.valid := mptChecker.io.mem.req.valid && !flush && !io.wfiReq
  io.mem.req.bits := mptChecker.io.mem.req.bits

  when(mptChecker.io.mem.req.fire) {
    waitingResp := true.B
  }
  when(flush && waitingResp) {
    flushLatch := true.B
  }
  when(io.mem.resp.valid && waitingResp) {
    waitingResp := false.B
    flushLatch := false.B
  }

  mptChecker.io.mem.resp.valid := io.mem.resp.valid && waitingResp && !flushLatch
  mptChecker.io.mem.resp.bits := io.mem.resp.bits

  io.pmp.req <> mptChecker.io.pmp.req
  mptChecker.io.pmp.resp <> io.pmp.resp

  val respMptAf = !mptChecker.io.resp.bits.mptPerm(0) || mptChecker.io.resp.bits.accessFault
  val mptAf = RegEnable(respMptAf, false.B, mptChecker.io.resp.valid)
  when(flush) {
    mptAf := false.B
  }

  io.resp := mptChecker.io.resp
  io.routedResp.ptw.valid := mptChecker.io.resp.valid && mptChecker.io.resp.bits.id === ptwMptPort.U
  io.routedResp.ptw.bits := mptChecker.io.resp.bits
  io.routedResp.hptw.valid := mptChecker.io.resp.valid && mptChecker.io.resp.bits.id === hptwMptPort.U
  io.routedResp.hptw.bits := mptChecker.io.resp.bits
  io.routedResp.llptw.valid := mptChecker.io.resp.valid && mptChecker.io.resp.bits.id === llptwMptPort.U
  io.routedResp.llptw.bits := mptChecker.io.resp.bits
  for (i <- 0 until PtwWidth) {
    io.routedResp.merge(i).valid := mptChecker.io.resp.valid &&
      !mptChecker.io.resp.bits.mptOnly && mptChecker.io.resp.bits.id === (8 + i).U
    io.routedResp.merge(i).bits := mptChecker.io.resp.bits
    io.routedResp.l1MptOnly(i).valid := mptChecker.io.resp.valid &&
      mptChecker.io.resp.bits.mptOnly && mptChecker.io.resp.bits.id === i.U
    io.routedResp.l1MptOnly(i).bits := mptChecker.io.resp.bits
  }

  io.mptStatus.mptEn := mptEn
  io.mptStatus.mptAf := mptAf
  io.mptStatus.accessFaultMpt := io.upstreamAccessFault || mptAf
  io.mptStatus.memReqBlockedByMptAf := mptAf
  io.mptStatus.routedMptAf.ptw := io.routedResp.ptw.valid && respMptAf
  io.mptStatus.routedMptAf.hptw := io.routedResp.hptw.valid && respMptAf
  io.mptStatus.routedMptAf.llptw := io.routedResp.llptw.valid && respMptAf
  for (i <- 0 until PtwWidth) {
    io.mptStatus.routedMptAf.merge(i) := io.routedResp.merge(i).valid && respMptAf
    io.mptStatus.routedMptAf.l1MptOnly(i) := io.routedResp.l1MptOnly(i).valid && respMptAf
  }

  io.memBusy := waitingResp
  io.flushLatched := flushLatch
}

class smmpt(implicit p: Parameters) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))
  val io = IO(new Bundle {
    val l2tlbMpt = new SmmptL2TlbMptIO
  })

  withClockAndReset(clock, reset) {
    val l2tlbMpt = Module(new SmmptL2TlbMptHarness)
    io.l2tlbMpt <> l2tlbMpt.io

    val sramBroadcast = SramHelper.genBroadCastBundleTop()
    sramBroadcast.ram_hold := false.B
    sramBroadcast.ram_bypass := false.B
    sramBroadcast.ram_bp_clken := false.B
    sramBroadcast.ram_aux_clk := false.B
    sramBroadcast.ram_aux_ckbp := false.B
    sramBroadcast.ram_mcp_hold := false.B
    sramBroadcast.ram_ctl := 0.U
    sramBroadcast.cgen := false.B
  }
}

object SmmptMain extends App {
  val (config, firrtlOpts, firtoolOpts) = ArgParser.parse(
    args :+ "--disable-always-basic-diff" :+ "--dump-fir" :+ "--target" :+ "verilog"
  )

  val mptConfig = config.alterPartial({
    case XSTileKey => config(XSTileKey).map(_.copy(
      HasMptCheck = true,
      HasBitmapCheck = false
    ))
    case XSCoreParamsKey => config(XSTileKey).head.copy(
      HasMptCheck = true,
      HasBitmapCheck = false
    )
  })

  Generator.execute(
    firrtlOpts :+ "--full-stacktrace" :+ "--target-dir" :+ "build/smmpt",
    new smmpt()(mptConfig),
    firtoolOpts
  )

  println("smmpt done")
}
