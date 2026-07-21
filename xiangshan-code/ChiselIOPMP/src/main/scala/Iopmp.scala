/***************************************************************************************
* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
*
* ChiselIOPMP is licensed under Mulan PSL v2.
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

package iopmp

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import _root_.circt.stage.ChiselStage
import utility.{LogUtilsOptionsKey, LogUtilsOptions}  

// Arbiter
class ReqArb(n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new ReqIO))
    val out = new ReqIO
  })

  io.out.valid := false.B
  io.out.bits := 0.U.asTypeOf(new ReqBundle)
  for(i <- 0 until n){
    io.in(i).ready := false.B
  }

  val sel = RegInit(0.U(log2Ceil(n).W))

  when(io.out.valid && io.out.ready){
    sel := Mux(sel === (n-1).U, 0.U, sel + 1.U)
  }

  when(io.in(sel).valid){
    io.out.valid := true.B
    io.out.bits := io.in(sel).bits
    io.in(sel).ready := io.out.ready
  }
}

class RspArb(n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new RspIO)
    val out = Vec(n, new RspIO)
  })

  for (i <- 0 until n) {
    io.out(i).valid := false.B
    io.out(i).bits := 0.U.asTypeOf(new RspBundle)
  }
  io.in.ready := false.B

  val sel = RegInit(0.U(log2Ceil(n).W))

  when(io.out(sel).valid && io.out(sel).ready) {
    sel := Mux(sel === (n-1).U, 0.U, sel + 1.U)
  }

  when(io.in.valid) {
    io.out(sel).valid := true.B
    io.out(sel).bits := io.in.bits
    io.in.ready := io.out(sel).ready
  }
}

class IopmpLazy(numBridge: Int = 1)(implicit p: Parameters) extends LazyModule {
  val bridges = Seq.fill(numBridge)(LazyModule(new IopmpBridgeLazy()))
  
  // create IdentityNode for Passthrough，maintain Diplomacy connectivity.
  val slaveNodes = Seq.fill(numBridge)(AXI4IdentityNode())
  val masterNodes = Seq.fill(numBridge)(AXI4IdentityNode())

  for(i <- 0 until numBridge){
    bridges(i).slaveNode := slaveNodes(i)    // identityNode(source) -> bridge.slaveNode(sink)
    masterNodes(i) := bridges(i).masterNode  // bridge.masterNode(source) -> identityNode(sink)
  }

  lazy val module = new Imp
  class Imp extends LazyModuleImp(this) {
    val apb_s = IO(new APBSlaveBundle(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits))
    val int = IO(Output(Bool()))

    // APB2Reg and Checker
    val apb2reg = Module(new APB2Reg())
    val iopmp_checker: IopmpCheckerBase = if (IopmpParams.mode == IopmpMode.Full) {
      Module(new IopmpChecker())
    } else if (IopmpParams.mode == IopmpMode.RapidK) {
      Module(new IopmpCheckerRapidK())
    } else {
      Module(new IopmpCheckerCompactK())
    }

    if (numBridge > 1) {
      val reqArb = Module(new ReqArb(numBridge))
      val rspArb = Module(new RspArb(numBridge))

      for (i <- 0 until numBridge) {
        reqArb.io.in(i) <> bridges(i).module.check_req
      }

      iopmp_checker.io.req <> reqArb.io.out
      rspArb.io.in <> iopmp_checker.io.resp

      for (i <- 0 until numBridge) {
        bridges(i).module.check_resp <> rspArb.io.out(i)
      }
    } else { // only one bridge, connect directly
      iopmp_checker.io.req <> bridges(0).module.check_req
      iopmp_checker.io.resp <> bridges(0).module.check_resp
    }

    // APB
    apb2reg.io.apb <> apb_s
    iopmp_checker.io.regcfg <> apb2reg.io.reg

    // other signals connect checker with bridge
    for (i <- 0 until numBridge) {
      bridges(i).module.rs := iopmp_checker.io.rs
      bridges(i).module.enable := iopmp_checker.io.enable
    }

    // int
    iopmp_checker.io.int <> int
  }
}

class IopmpLazyWrapper(numBridge: Int = 1)(implicit p: Parameters) extends LazyModule {
  val iopmpLazy = LazyModule(new IopmpLazy(numBridge))

  val slaveNodes = Seq.fill(numBridge)(AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = Seq(AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1)),
      regionType    = RegionType.UNCACHED,
      executable    = false,
      supportsRead  = TransferSizes(1, IopmpParams.axi_beatByte),
      supportsWrite = TransferSizes(1, IopmpParams.axi_beatByte),
      interleavedId = Some(0)
    )),
    beatBytes = IopmpParams.axi_beatByte
  ))))

  val masterNodes = Seq.fill(numBridge)(AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "iopmp-bridge",
      id   = IdRange(0, IopmpParams.axi_idNum)
    ))
  ))))

  // connect nodes and make them visible for IO
  for(i <- 0 until numBridge){
    iopmpLazy.slaveNodes(i) := masterNodes(i)   // slaveNodes(source) -> iopmpLazy.slaveNodes(sink)
    slaveNodes(i) := iopmpLazy.masterNodes(i) // iopmpLazy.masterNodes(source) -> masterNodes(sink)
  }

  val axi4_s = masterNodes.zipWithIndex.map { case (node, i) => 
    InModuleBody(node.makeIOs()(ValName(s"axi4_s$i")))
  }
  val axi4_m = slaveNodes.zipWithIndex.map { case (node, i) => 
    InModuleBody(node.makeIOs()(ValName(s"axi4_m$i")))
  }

  lazy val module = new Imp
  class Imp extends LazyModuleImp(this) {
    val apb_s = IO(new APBSlaveBundle(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits))
    val int = IO(Output(Bool()))

    iopmpLazy.module.apb_s <> apb_s
    iopmpLazy.module.int <> int
  }
}

/**
 * Generate Verilog sources
 */
object IOPMP extends App {
  // implicit val p: Parameters = Parameters.empty
  implicit val p: Parameters = Parameters.empty.alterPartial {                             
    case LogUtilsOptionsKey => LogUtilsOptions(                                            
      enableDebug = false,                                                                 
      enablePerf = false,                                                                  
      fpgaPlatform = false                                                                 
    )                                                                                      
  }   
  // val top = LazyModule(new IopmpLazyWrapper(numBridge = 1)) // only one bridge test pass
  val top = LazyModule(new Demo)

  ChiselStage.emitSystemVerilog(
    top.module,
    args = Array("--dump-fir"),
    // more opts see: $CHISEL_FIRTOOL_PATH/firtool -h
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      // without this, firtool will exit with error: Unhandled annotation
      "--disable-annotation-unknown",
      "--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none",
      "--split-verilog", "-o=./build/rtl",
    )
  )
}
