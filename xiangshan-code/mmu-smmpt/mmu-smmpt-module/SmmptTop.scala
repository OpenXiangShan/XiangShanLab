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
import org.chipsalliance.cde.config.Parameters
import top.{ArgParser, Generator}
import utility.sram.SramHelper
import xiangshan._

class smmpt(implicit p: Parameters) extends RawModule {
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))
  val io = IO(new MptCheckerIO)

  withClockAndReset(clock, reset) {
    val dut = Module(new MptChecker)
    io <> dut.io

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
