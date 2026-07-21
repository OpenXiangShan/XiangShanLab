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

// APB Slave Bundle  
class APBSlaveBundle(addrBits: Int, dataBits: Int) extends Bundle {
  val paddr   = Input(UInt(addrBits.W))
  val psel    = Input(Bool())
  val penable = Input(Bool())
  val pwrite  = Input(Bool())
  val pwdata  = Input(UInt(dataBits.W))
  val pready  = Output(Bool())
  val prdata  = Output(UInt(dataBits.W))
}

// APB2Reg translator
class APB2Reg extends Module {
  val io = IO(new Bundle {
    val apb = new APBSlaveBundle(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits)
    val reg = Flipped(RegCfgIO(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits))
  })

io.reg.v := (io.apb.psel && !io.apb.penable) //setup
io.reg.rw := io.apb.pwrite
io.reg.addr := io.apb.paddr
io.reg.din := io.apb.pwdata
io.apb.pready := (io.apb.psel && io.apb.penable) //access
io.apb.prdata := io.reg.dout
}

class IopmpBridgeLazy(
  address: AddressSet = AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1),
  beatBytes: Int = IopmpParams.axi_beatByte
)(implicit p: Parameters) extends LazyModule {
  
  // use RocketChip axi4 node
  val slaveNode = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = Seq(address),
      regionType    = RegionType.UNCACHED,
      executable    = false,
      supportsRead  = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0)
    )),
    beatBytes = beatBytes
  )))
  
  val masterNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "iopmp-bridge",
      id   = IdRange(0, IopmpParams.axi_idNum)
    ))
  )))

  lazy val module = new Imp
  class Imp extends LazyModuleImp(this) {
    val check_req = IO(new ReqIO)
    val check_resp = IO(Flipped(new RspIO))
    val rs = IO(Input(Bool()))
    val enable = IO(Input(Bool()))
    
    // get slave/master node
    val (slaveBundle, slaveEdge) = slaveNode.in.head
    val (masterBundle, masterEdge) = masterNode.out.head
    
//----------------------------------------------ar/aw channel---------------------------------------------//
//-----------------------------slave ar/aw->>-checker->--pass-->-master ar/aw-----------------------------//
//-----------------------------                  |                           -----------------------------//
//-----------------------------                  v                           -----------------------------//
//-----------------------------               not pass->>-- log err r/w id   -----------------------------//

    // slave port aw/ar FIFO
    val s_ar_fifo = Module(new Queue(slaveBundle.ar.bits.cloneType, IopmpParams.axi_outstanding))
    val s_aw_fifo = Module(new Queue(slaveBundle.aw.bits.cloneType, IopmpParams.axi_outstanding))
    // master port aw/ar FIFO
    val m_ar_fifo = Module(new Queue(slaveBundle.ar.bits.cloneType, IopmpParams.axi_outstanding))
    val m_aw_fifo = Module(new Queue(slaveBundle.aw.bits.cloneType, IopmpParams.axi_outstanding))
    
    when(enable){
      slaveBundle.ar <> s_ar_fifo.io.enq
      slaveBundle.aw <> s_aw_fifo.io.enq
      masterBundle.ar <> m_ar_fifo.io.deq
      masterBundle.aw <> m_aw_fifo.io.deq
    }.otherwise{
      masterBundle.ar.valid := slaveBundle.ar.valid
      masterBundle.ar.bits  := slaveBundle.ar.bits
      slaveBundle.ar.ready  := masterBundle.ar.ready

      masterBundle.aw.valid := slaveBundle.aw.valid
      masterBundle.aw.bits  := slaveBundle.aw.bits
      slaveBundle.aw.ready  := masterBundle.aw.ready

      // s port ar/aw and m port ar/aw fifo input wire 0，output wire null 
      s_ar_fifo.io.enq.valid := false.B
      s_ar_fifo.io.enq.bits := 0.U.asTypeOf(s_ar_fifo.io.enq.bits)
      s_aw_fifo.io.enq.valid := false.B
      s_aw_fifo.io.enq.bits := 0.U.asTypeOf(s_aw_fifo.io.enq.bits)
      m_ar_fifo.io.deq.ready := false.B
      m_aw_fifo.io.deq.ready := false.B
    }
    
    // Checker request arbitration logic - Fair polling strategy.
    val poll_sel = RegInit(false.B)  // false: ar; true: aw
    val ar_valid = s_ar_fifo.io.deq.valid
    val aw_valid = s_aw_fifo.io.deq.valid
    
    // Fair round-robin arbitration: when both are valid, select based on `poll_sel`; otherwise, choose the one that is valid.
    val use_ar = ar_valid && (!aw_valid || !poll_sel)
    val use_aw = aw_valid && (!ar_valid || poll_sel)
    
    val current_aux = RegInit(0.U.asTypeOf(slaveBundle.ar.bits.cloneType))
    val current_rw = RegInit(false.B)
    
    // transfer length
    val s_read_transfer_len = (s_ar_fifo.io.deq.bits.len +& 1.U) << s_ar_fifo.io.deq.bits.size
    val s_write_transfer_len = (s_aw_fifo.io.deq.bits.len +& 1.U) << s_aw_fifo.io.deq.bits.size
    
    // Checker request process
    check_req.valid := use_ar || use_aw
    check_req.bits.rrid := Mux(use_ar, s_ar_fifo.io.deq.bits.id, s_aw_fifo.io.deq.bits.id)
    check_req.bits.pa := Mux(use_ar, s_ar_fifo.io.deq.bits.addr, s_aw_fifo.io.deq.bits.addr)
    check_req.bits.rw := use_aw
    check_req.bits.len := Mux(use_ar, s_read_transfer_len, s_write_transfer_len)
    
    s_ar_fifo.io.deq.ready := check_req.ready && use_ar
    s_aw_fifo.io.deq.ready := check_req.ready && use_aw
    
    // log current request, and update poll_sel
    when(check_req.ready && check_req.valid) {
      current_aux := Mux(use_ar, s_ar_fifo.io.deq.bits, s_aw_fifo.io.deq.bits.asTypeOf(slaveBundle.ar.bits.cloneType))
      current_rw := use_aw
      poll_sel := ~poll_sel
    }
    
    // Checker response process
    val pass = ((!check_resp.bits.cf_r.asBool && !current_rw) || (!check_resp.bits.cf_w.asBool && current_rw)) // checker pass
    val err_handle_idle_w = Wire(Bool())
    val err_handle_idle_r = Wire(Bool())
    
    // if check_resp valid and access pass, put current_aux into corresponding master fifo, otherwise discard current_aux
    // and if the channel is in error handle state, stall the next check req. that is signal slave mode.
    m_ar_fifo.io.enq.valid := check_resp.valid && pass && !current_rw && err_handle_idle_r
    m_ar_fifo.io.enq.bits := current_aux
    m_aw_fifo.io.enq.valid := check_resp.valid && pass && current_rw && err_handle_idle_w
    m_aw_fifo.io.enq.bits := current_aux.asTypeOf(slaveBundle.aw.bits.cloneType)

    when(check_resp.valid){
      when(pass){ // check pass trans to master
        when(current_rw){
          check_resp.ready := m_aw_fifo.io.enq.ready && err_handle_idle_w// master write ready
        }.otherwise{
          check_resp.ready := m_ar_fifo.io.enq.ready && err_handle_idle_r// master read ready
        }
      }.otherwise{ // check not pass trans to error handle
        when(current_rw){
          check_resp.ready := err_handle_idle_w // error handle write ready when error handle not working
        }.otherwise{
          check_resp.ready := err_handle_idle_r // error handle read ready when error handle not working
        }
      }
    }.otherwise{
      check_resp.ready := false.B
    }

//---------------------------------------------r/w/b channel----------------------------------------------//
//--------------------------   slave w->>--->>----pass-->>--->>---master w   -----------------------------//
//--------------------------                       |                         -----------------------------//
//--------------------------                       v                         -----------------------------//
//--------------------------                    not pass                     -----------------------------//
//--------------------------------------------------------------------------------------------------------//
//-------------------------- slave r/b-<<---<<----pass--<<---<<---master r/b -----------------------------//
//--------------------------                ^                                -----------------------------//
//--------------------------                |                                -----------------------------//
//--------------------------             not pass--<<---<<- error handle r/b -----------------------------//



    //--------------------------------------------- r channel -----------------------------------------------//
    // fsm
    object State_r extends ChiselEnum {
      val sIdle, sSetup, sWait, sErr = Value
    }
    val state_r = RegInit(State_r.sIdle)

    val r_ch_id   = RegInit(0.U(IopmpParams.axi_idBits.W))
    val r_ch_len  = RegInit(0.U(slaveBundle.ar.bits.len.getWidth.W))
    val r_ch_err  = check_resp.fire && !pass && !current_rw
    val r_ch_done = slaveBundle.r.bits.last && slaveBundle.r.fire
    val r_ch_mon  = RegInit(false.B) // 0:idle 1:working, Used to determine channel status.
    val r_ch_idle = !slaveBundle.r.fire && !r_ch_mon

    when(r_ch_done){
      r_ch_mon := false.B
    }.elsewhen(slaveBundle.r.fire){
      r_ch_mon := true.B
    }

    // R channel counters
    // 1. When a check error occurs, correctly record the current number of outstanding R transmissions. 
    // 2. Stall the R channel until other transmissions are completed, and then return an error response (rsp).

    // The ar leads r counter, which counts all transmissions from the slave port.
    val arAheadRCntFull = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))
    val ar_ch_check_done = check_resp.fire && !current_rw

    when(enable){
      when(ar_ch_check_done){
        when(r_ch_done){
          arAheadRCntFull := arAheadRCntFull
        }.otherwise{
          arAheadRCntFull := arAheadRCntFull + 1.U // cnt ++
        }
      }.otherwise(
        when(r_ch_done){
          arAheadRCntFull := arAheadRCntFull - 1.U // cnt --
        }.otherwise(
          arAheadRCntFull := arAheadRCntFull
        )
      )
    }.otherwise{
      arAheadRCntFull := arAheadRCntFull
    }

    // Record the current number of transactions by which the erroneous ar channel is ahead of the r channel.
    val arAheadRCntWait = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))

    when(enable){
      when(state_r === State_r.sSetup){
        when(r_ch_done){
          arAheadRCntWait := arAheadRCntFull - 1.U
        }.otherwise{
          arAheadRCntWait := arAheadRCntFull
        }
      }.elsewhen(state_r === State_r.sWait){
        when(r_ch_done){
          arAheadRCntWait := arAheadRCntWait - 1.U
        }
      }
    }.otherwise{
      arAheadRCntWait := arAheadRCntWait
    }

    switch(state_r){
      is(State_r.sIdle ){when(r_ch_err              ){state_r := State_r.sSetup}}
      is(State_r.sSetup){                             state_r := State_r.sWait  }
      is(State_r.sWait ){when(r_ch_idle && 
                             arAheadRCntWait === 1.U){state_r := State_r.sErr  }}
      is(State_r.sErr  ){when(r_ch_done             ){state_r := State_r.sIdle }}
    }

    // log r id when error
    when(state_r === State_r.sIdle && r_ch_err){
      r_ch_id := current_aux.id
    }

    // log r len when error
    when(state_r === State_r.sIdle && r_ch_err){
      r_ch_len := current_aux.len
    }.elsewhen(state_r === State_r.sErr && slaveBundle.r.fire){
      r_ch_len := r_ch_len - 1.U
    }

    // r channel switch
    when(enable){
      when(state_r === State_r.sErr) { // error response
        slaveBundle.r.valid := true.B
        slaveBundle.r.bits.id := r_ch_id
        slaveBundle.r.bits.data := 0.U
        slaveBundle.r.bits.resp := Mux(rs,"b00".U,"b10".U) // SLVERR
        masterBundle.r.ready := false.B
        when(r_ch_len === 0.U){
          slaveBundle.r.bits.last := true.B
        }.otherwise{
          slaveBundle.r.bits.last := false.B
        }
      }.otherwise { // Passthrough master and slave
        slaveBundle.r.valid := masterBundle.r.valid
        slaveBundle.r.bits  := masterBundle.r.bits
        masterBundle.r.ready := slaveBundle.r.ready
      }
    }.otherwise{
        slaveBundle.r.valid := masterBundle.r.valid// bypass mode, passthrough master and slave
        slaveBundle.r.bits  := masterBundle.r.bits
        masterBundle.r.ready := slaveBundle.r.ready
    }

    err_handle_idle_r := (state_r === State_r.sIdle)

    //--------------------------------------------- w/b channel -----------------------------------------------//
    // fsm
    object State_w extends ChiselEnum {
      val sIdle, sSetupW, sWaitW, sErrW, sSetupB, sWaitB, sErrB = Value
    }
    val state_w = RegInit(State_w.sIdle)

    val w_ch_id   = RegInit(0.U(IopmpParams.axi_idBits.W))
    val w_ch_err  = check_resp.fire && !pass && current_rw
    val w_ch_done = slaveBundle.w.bits.last && slaveBundle.w.fire
    val w_ch_mon  = RegInit(false.B) // 0:idle 1:working, Used to determine channel status.
    val w_ch_idle = !slaveBundle.w.fire && !w_ch_mon
    val b_ch_done = slaveBundle.b.fire
    val b_ch_idle = !slaveBundle.b.fire

    when(w_ch_done){
      w_ch_mon := false.B
    }.elsewhen(slaveBundle.w.fire){
      w_ch_mon := true.B
    }

    // W channel counters
    // 1. When a check error occurs, correctly record the current number of outstanding W transmissions. 
    // 2. Stall the W channel until other transmissions are completed, and then return an error response (rsp).
    // 3. Prevent the write data channel (W) from advancing ahead of the corresponding write request (AW) before it is confirmed by the checker.

    // Record the number of AWs that have passed the check and been sent to the master, but whose corresponding W data has not yet been fully transmitted.
    val awAheadWCntPass = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))
    val aw_ch_check_done_pass = check_resp.fire && current_rw && pass
    val w_ch_done_mst = masterBundle.w.bits.last && masterBundle.w.fire

    when(enable){
      when(aw_ch_check_done_pass){
        when(w_ch_done_mst){
          awAheadWCntPass := awAheadWCntPass
        }.otherwise{
          awAheadWCntPass := awAheadWCntPass + 1.U // cnt ++
        }
      }.otherwise(
        when(w_ch_done_mst){
          awAheadWCntPass := awAheadWCntPass - 1.U // cnt --
        }.otherwise(
          awAheadWCntPass := awAheadWCntPass
        )
      )
    }.otherwise{
      awAheadWCntPass := awAheadWCntPass
    }

    // The aw leads w counter, which counts all transmissions from the slave port.
    val awAheadWCntFull = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))
    val aw_ch_check_done = check_resp.fire && current_rw

    when(enable){
      when(aw_ch_check_done){
        when(w_ch_done){
          awAheadWCntFull := awAheadWCntFull
        }.otherwise{
          awAheadWCntFull := awAheadWCntFull + 1.U // cnt ++
        }
      }.otherwise(
        when(w_ch_done){
          awAheadWCntFull := awAheadWCntFull - 1.U // cnt --
        }.otherwise(
          awAheadWCntFull := awAheadWCntFull
        )
      )
    }.otherwise{
      awAheadWCntFull := awAheadWCntFull
    }

    // Record the current number of transactions by which the erroneous aw channel is ahead of the w channel.
    val awAheadWCntWait = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))

    when(enable){
      when(state_w === State_w.sSetupW){
        when(w_ch_done){
          awAheadWCntWait := awAheadWCntFull - 1.U
        }.otherwise{
          awAheadWCntWait := awAheadWCntFull
        }  
      }.elsewhen(state_w === State_w.sWaitW){
        when(w_ch_done){
          awAheadWCntWait := awAheadWCntWait - 1.U
        }
      }
    }.otherwise{
      awAheadWCntWait := awAheadWCntWait
    }

    // The aw leads b counter, which counts all transmissions from the slave port.
    val awAheadBCntFull = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))

    when(enable){
      when(aw_ch_check_done){
        when(b_ch_done){
          awAheadBCntFull := awAheadBCntFull
        }.otherwise{
          awAheadBCntFull := awAheadBCntFull + 1.U // cnt ++
        }
      }.otherwise(
        when(b_ch_done){
          awAheadBCntFull := awAheadBCntFull - 1.U // cnt --
        }.otherwise(
          awAheadBCntFull := awAheadBCntFull
        )
      )
    }.otherwise{
      awAheadBCntFull := awAheadBCntFull
    }

    // Record the current number of transactions by which the erroneous aw channel is ahead of the b channel.
    val awAheadBCntWait = RegInit(0.U((1+log2Ceil(IopmpParams.axi_outstanding)).W))

    when(enable){
      when(state_w === State_w.sSetupB){
        when(b_ch_done){
          awAheadBCntWait := awAheadBCntFull - 1.U
        }.otherwise{
          awAheadBCntWait := awAheadBCntFull
        }
      }.elsewhen(state_w === State_w.sWaitB){
        when(b_ch_done){
          awAheadBCntWait := awAheadBCntWait - 1.U
        }
      }
    }.otherwise{
      awAheadBCntWait := awAheadBCntWait
    }

    switch(state_w){
      is(State_w.sIdle  ){when(w_ch_err              ){state_w := State_w.sSetupW}}
      is(State_w.sSetupW){                             state_w := State_w.sWaitW  }
      is(State_w.sWaitW ){when(w_ch_idle &&
                              awAheadWCntWait === 1.U){state_w := State_w.sErrW  }}
      is(State_w.sErrW  ){when(w_ch_done             ){state_w := State_w.sSetupB}}
      is(State_w.sSetupB){                            {state_w := State_w.sWaitB }}
      is(State_w.sWaitB ){when(b_ch_idle &&
                              awAheadBCntWait === 1.U){state_w := State_w.sErrB  }}
      is(State_w.sErrB  ){when(b_ch_done             ){state_w := State_w.sIdle  }}
    }

    when(state_w === State_w.sIdle && w_ch_err){
      w_ch_id := current_aux.id
    }

    // w channel switch
    when(enable){
      when(state_w === State_w.sErrW) { // error response
        slaveBundle.w.ready := true.B
        masterBundle.w.valid := false.B
        masterBundle.w.bits := 0.U.asTypeOf(masterBundle.w.bits)
      }.elsewhen(awAheadWCntPass > 0.U) { // Passthrough master and slave
        masterBundle.w.valid := slaveBundle.w.valid
        masterBundle.w.bits  := slaveBundle.w.bits
        slaveBundle.w.ready  := masterBundle.w.ready
      }.otherwise { // pending when aw not ahead w channel
        slaveBundle.w.ready := false.B
        masterBundle.w.valid := false.B
        masterBundle.w.bits := 0.U.asTypeOf(masterBundle.w.bits)
      }
    }.otherwise{ // bypass mode, passthrough master and slave
      masterBundle.w.valid := slaveBundle.w.valid
      masterBundle.w.bits  := slaveBundle.w.bits
      slaveBundle.w.ready  := masterBundle.w.ready
    }

    // b channel switch
    when(enable){
      when(state_w === State_w.sErrB) { // error response
        slaveBundle.b.valid := true.B
        slaveBundle.b.bits.id := w_ch_id
        slaveBundle.b.bits.resp := Mux(rs, "b00".U, "b10".U) // SLVERR
        masterBundle.b.ready := false.B
      }.otherwise { // Passthrough master and slave
        slaveBundle.b.valid := masterBundle.b.valid
        slaveBundle.b.bits  := masterBundle.b.bits
        masterBundle.b.ready := slaveBundle.b.ready
      }
    }.otherwise{ // bypass mode, passthrough master and slave
      slaveBundle.b.valid := masterBundle.b.valid
      slaveBundle.b.bits  := masterBundle.b.bits
      masterBundle.b.ready := slaveBundle.b.ready
    }

    err_handle_idle_w := (state_w === State_w.sIdle)
  }
}

// IopmpBridge test wrapper, for test, need a diplomacy node
class IopmpBridgeWrapperTest(implicit p: Parameters) extends LazyModule {
  val bridge = LazyModule(new IopmpBridgeLazy())
  
  val testSource = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(name = "test-master", id = IdRange(0, IopmpParams.axi_idNum)))
  )))
  
  val testSink = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address       = Seq(AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1)),
      regionType    = RegionType.UNCACHED,
      executable    = false,
      supportsRead  = TransferSizes(1, IopmpParams.axi_beatByte),
      supportsWrite = TransferSizes(1, IopmpParams.axi_beatByte),
      interleavedId = Some(0)
    )),
    beatBytes = IopmpParams.axi_beatByte
  )))
  
  // Connection node
  bridge.slaveNode := testSource   // testSource -> bridge.slaveNode  
  testSink := bridge.masterNode    // bridge.masterNode -> testSink
  val io_tl_s = InModuleBody(testSink.makeIOs())
  val io_tl_m = InModuleBody(testSource.makeIOs())

  lazy val module = new LazyModuleImp(this) {
    // Expose non-Diplomacy signal IO interfaces.
    val io = IO(new Bundle {
      val check_req = new ReqIO
      val check_resp = Flipped(new RspIO)
      val rs = Input(Bool())
    })
    
    bridge.module.check_req <> io.check_req
    bridge.module.check_resp <> io.check_resp
    bridge.module.rs := io.rs
  }
}
