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
import chisel3.experimental._
import scala.annotation.varargs
import javax.xml.transform.OutputKeys


// Register Configuration Interface Mux
class RegCfgMuxCompactK extends Module {
  val io = IO(new Bundle {
    val regcfg = RegCfgIO()
    val regcfg_regmap = Flipped(RegCfgIO())
    val regcfg_entry  = Flipped(RegCfgIO())
  })

  // Check if address falls within valid range for each register table
  val addrhit_info = (io.regcfg.addr >= IopmpParams.info_base_addr.U) && (io.regcfg.addr < IopmpParams.info_max_addr.U)
  val addrhit_entry = (io.regcfg.addr >= IopmpParams.entry_base_addr.U) && (io.regcfg.addr < IopmpParams.entry_max_addr.U)

  val regcfgs = Seq(
    (io.regcfg_regmap, addrhit_info),
    (io.regcfg_entry, addrhit_entry)
  )

  regcfgs.foreach { case (regcfg, hit) =>
    regcfg.rw   := Mux(hit, io.regcfg.rw, false.B)
    regcfg.addr := Mux(hit, io.regcfg.addr, 0.U)
    regcfg.din  := Mux(hit, io.regcfg.din, 0.U)
    regcfg.v    := Mux(hit, io.regcfg.v, false.B)
  }

  io.regcfg.dout := MuxCase(0.U, 
    regcfgs.map { case (regcfg, hit) => hit -> regcfg.dout }
  )
}

class CtrlCompactK extends Module {
  val io = IO(new Bundle {
    // check req
    val req = Flipped(new ReqIO)
    // check rsp
    val resp = new RspIO
    //entry
    val entry = Flipped(new EntryTableIORapidK)
    // regcfg
    val reg = Flipped(new RegMapIO)
    // stall & int & flush & enable
    val stall = Input(Bool())
    val int = Output(Bool())
    val flush = Output(Bool())
    val rs = Output(Bool())
    val enable = Output(Bool())
  })

  // Set default values
  io.reg.i_errinfo_v := 0.U
  io.reg.i_errinfo_ttype := 0.U
  io.reg.i_errinfo_etype := 0.U
  io.reg.i_erreqaddr := 0.U
  io.reg.i_erreqaddrh := 0.U
  io.reg.i_erreqid_rrid := 0.U
  io.reg.i_erreqid_eid := 0.U

  // fsm
  object State extends ChiselEnum {
    val sIdle, sMatch, sCheck, sErr, sDone = Value
  }

  val state = RegInit(State.sIdle)

  // reg the fired req
  val req = RegInit(0.U.asTypeOf(new ReqBundle))

  // error
  val ttype = RegInit(0.U(2.W)) // transcation type
  val etype = RegInit(0.U(4.W)) // error type
  val eid = RegInit(0.U(16.W)) // error entry id

  // entryTable
  val j_indx = RegInit(0.U(IopmpParams.entry_addr_width.W))
  val j_en = RegInit(0.U(1.W))
  val entry_attribute = RegInit(0.U.asTypeOf(new EntryAttribute))

  // matching
  val pri_hit_vec = Wire(Vec(IopmpParams.k, Bool()))
  val pri_part_hit_vec = Wire(Vec(IopmpParams.k, Bool()))
  val pri_hit = pri_hit_vec.reduce(_ || _)
  val pri_part_hit = pri_part_hit_vec.reduce(_ || _)
  val pri_hit_idx = PriorityEncoder(pri_hit_vec)
  val cf_r = RegInit(0.U(1.W)) // check fail read
  val cf_w = RegInit(0.U(1.W)) // check fail write

// state machine
  switch(state) {
    //0
    is(State.sIdle) {
      when(io.req.fire) {
        when(io.reg.o_reg_hwcfg0_enable === 1.U) {
          when(io.req.bits.rrid < io.reg.o_reg_hwcfg1_rrid_num){ //legal rrid fired
            state:= State.sMatch
          }.otherwise{ //illegal rrid fired
            etype := 0x6.U // 0x6: unknown RRID
            state:= State.sErr
          }
        }otherwise {
          state:= State.sDone
        }
      }
    }
    //1
    is(State.sMatch) {
      when(pri_hit) {
        state := State.sCheck // hit
      }.elsewhen(pri_part_hit) {
        etype := 0x4.U // 0x4 : partial hit on a priority rule
        state := State.sErr
      }.otherwise {
        etype := 0x5.U // 0x5 : not hit any rule
        state := State.sErr
      }
    }
    //2
    is(State.sCheck) {
      when(req.rw && !entry_attribute.w || io.reg.o_reg_hwcfg0_no_w.asBool) { //write error
        etype := 0x2.U // 0x2 : illegal write access/AMO
        state := State.sErr
      }.elsewhen(!req.rw && !entry_attribute.r) { //read error
        etype := 0x1.U // 0x1 : illegal read access
        state := State.sErr
      }.otherwise { // if legal access
        state := State.sDone
      }
    }
    //3
    is(State.sErr) {
      io.reg.i_errinfo_v        := !io.reg.o_reg_errinfo_v // capture a illegal action, only log once
      io.reg.i_errinfo_ttype    := ttype // transaction type of the first captured violation
      io.reg.i_errinfo_etype    := etype // type of violation
      io.reg.i_erreqaddr        := req.pa(33, 2) // Indicate the errored address[33:2]
      io.reg.i_erreqaddrh       := Cat(0.U((IopmpParams.soc_addr_width - 30).W), req.pa(IopmpParams.soc_addr_width - 1, 34)) // Indicate the errored address[65:34], high bits are zero
      io.reg.i_erreqid_rrid     := req.rrid // Indicate the errored RRID
      io.reg.i_erreqid_eid      := eid // Indicate the errored entry index

      state := State.sDone
    }
    //4
    is(State.sDone) {
      io.reg.i_errinfo_v        := 0.U
      when(io.resp.fire) {
        state := State.sIdle
      }
    }
  }

  // reg the req info
  when(io.req.fire && io.reg.o_reg_hwcfg0_enable.asBool) { 
    req := io.req.bits 
    when (io.req.bits.rw) {
      ttype := 0x2.U // write access
    }.otherwise {
      ttype := 0x1.U // read access
    }
  }

  // req ready and stall
  io.req.ready := (state === State.sIdle) && !io.stall

  // reg j_indx
  j_indx := io.req.bits.rrid
  j_en := io.req.fire

  // read entry
  io.entry.j_indx := j_indx
  io.entry.j_en := j_en

  // parallel check k entry
  val matcher = for (i <- 0 until IopmpParams.k) yield {
    val m = Module(new Matcher)
    m.io.req_pa := req.pa
    m.io.req_len := req.len
    m.io.entry_addr := io.entry.addr(i)
    m.io.entry_attribute := io.entry.attribute(i)
    m.io.j_indx := j_indx
    m.io.reg_prio_entry := io.reg.o_reg_hwcfg2_prio_entry
    pri_hit_vec(i) := m.io.pri_hit
    pri_part_hit_vec(i) := m.io.pri_part_hit
  }

  // get hit entry
  when(pri_hit){
    entry_attribute := io.entry.attribute(pri_hit_idx)
  }

  // get cf
  when(!io.reg.o_reg_hwcfg0_enable) {
    cf_w := 0.U
    cf_r := 0.U    
  }.elsewhen(state === State.sCheck){ // get cf from entry[j]
    cf_w := !entry_attribute.w
    cf_r := !entry_attribute.r
  }.elsewhen(state === State.sErr) { // cause error, clear cf
    when(etype === 0x1.U || etype === 0x2.U || etype === 0x3.U){
      cf_w := !entry_attribute.w
      cf_r := !entry_attribute.r
    }.otherwise {
      cf_w := 1.U
      cf_r := 1.U
    }
  }

  // eid record
  when(pri_hit | pri_part_hit){
    eid := j_indx
  }

  // check done and have a rsp
  when(state === State.sDone) {
    io.resp.bits.cf_w := cf_w
    io.resp.bits.cf_r := cf_r
    io.resp.valid := true.B
  }.otherwise {
    io.resp.bits.cf_w := 0.U
    io.resp.bits.cf_r := 0.U
    io.resp.valid := false.B
  }

  // interrupt
  when(io.reg.o_reg_errinfo_v.asBool && io.reg.o_reg_errcfg_ie.asBool) { // have a unclear error flag
    when(io.reg.o_reg_errinfo_ttype === 1.U && !entry_attribute.sire) { //read error
      io.int := true.B // assert int
    }.elsewhen(io.reg.o_reg_errinfo_ttype === 2.U && !entry_attribute.siwe) { // write error
      io.int := true.B // assert int
    }.otherwise {
      io.int := false.B // clear int
    }
  }.otherwise {
    io.int := false.B // clear int
  }

  // flush
  io.flush := EdgeDetect.falling(io.stall)

  // rs
  io.rs := io.reg.o_reg_errcfg_rs.asBool

  //enable
  io.enable := io.reg.o_reg_hwcfg0_enable.asBool
}

class IopmpCheckerCompactK extends Module with IopmpCheckerBase{

  // Declare modules
  val regcfg_mux = Module(new RegCfgMuxCompactK)
  val entry_table = Module(new EntryTableRapidK)
  val reg_map = Module(new RegMap)
  val ctrl = Module(new CtrlCompactK)

  // Inter-module connections
  val stall = io.regcfg.v && io.regcfg.rw
  // registers
  regcfg_mux.io.regcfg <> io.regcfg
  entry_table.io.regcfg <> regcfg_mux.io.regcfg_entry
  reg_map.io.regcfg <> regcfg_mux.io.regcfg_regmap
  // ctrl
  ctrl.io.req <> io.req // check req
  ctrl.io.resp <> io.resp // check rsp
  ctrl.io.entry <> entry_table.io.bits // entryTable
  ctrl.io.reg <> reg_map.io.bits // regMap
  ctrl.io.stall := stall // stall
  ctrl.io.int <> io.int // interrupt
  ctrl.io.flush <> io.flush // flush
  ctrl.io.rs <> io.rs // response suppression
  ctrl.io.enable <> io.enable // IOPMP enable
}