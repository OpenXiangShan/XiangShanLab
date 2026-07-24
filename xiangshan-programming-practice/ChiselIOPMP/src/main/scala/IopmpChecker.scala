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

// debug
object DebugUtils {
  def debug(signal: Data, name: String): Unit = {
    if (IopmpParams.debugMode) {
      val w = Wire(chiselTypeOf(signal))
      w := signal
      w.suggestName(name)
      dontTouch(w)
    }
  }
}

// True dual-port SRAM module (both A/B ports can independently read/write, supports configurable latency, bit width, and depth)
class TrueDualPortSRAM(addrWidth: Int, dataWidth: Int, readLatency: Int = 1, depth: Int = 1024) extends Module {
  val io = IO(new Bundle {
    // port A
    val a_en    = Input(Bool())
    val a_we    = Input(Bool())
    val a_addr  = Input(UInt(addrWidth.W))
    val a_wdata = Input(UInt(dataWidth.W))
    val a_rdata = Output(UInt(dataWidth.W))
    // port B
    val b_en    = Input(Bool())
    val b_we    = Input(Bool())
    val b_addr  = Input(UInt(addrWidth.W))
    val b_wdata = Input(UInt(dataWidth.W))
    val b_rdata = Output(UInt(dataWidth.W))
  })

  val mem = SyncReadMem(depth, UInt(dataWidth.W))

  // Write conflict detection and priority arbitration (Port A has priority)
  val write_conflict = io.a_en && io.a_we && io.b_en && io.b_we && (io.a_addr === io.b_addr)
  when(io.a_en && io.a_we) {
    mem.write(io.a_addr, io.a_wdata)
  }
  // Port B writes are only allowed when there is no conflict.
  when(io.b_en && io.b_we && !(io.a_en && io.a_we && (io.a_addr === io.b_addr))) {
    mem.write(io.b_addr, io.b_wdata)
  }

  // Assertion during simulation: Simultaneous writes to the same address from both Port A and Port B are prohibited.
  assert(!(write_conflict), "Error: TrueDualPortSRAM A/B ports write to the same address at the same time!")

  // Assertion during simulation: readLatency must be greater than or equal to 1.
  assert(readLatency >= 1, "Error: TrueDualPortSRAM readLatency must be greater than or equal to 1!")

  // port A read
  val a_rdata_wire = WireDefault(0.U(dataWidth.W))

  a_rdata_wire := mem.read(io.a_addr, io.a_en)

  if (readLatency == 1) {
    io.a_rdata := a_rdata_wire
  } else {
    val a_readPipe = RegInit(VecInit(Seq.fill(readLatency-1)(0.U(dataWidth.W))))
    a_readPipe(0) := a_rdata_wire
    for (i <- 1 until readLatency-1) {
      a_readPipe(i) := a_readPipe(i-1)
    }
    io.a_rdata := a_readPipe(readLatency-2)
  }

  // port B read
  val b_rdata_wire = WireDefault(0.U(dataWidth.W))

  b_rdata_wire := mem.read(io.b_addr, io.b_en)

  if (readLatency == 1) {
    io.b_rdata := b_rdata_wire
  } else {
    val b_readPipe = RegInit(VecInit(Seq.fill(readLatency-1)(0.U(dataWidth.W))))
    b_readPipe(0) := b_rdata_wire
    for (i <- 1 until readLatency-1) {
      b_readPipe(i) := b_readPipe(i-1)
    }
    io.b_rdata := b_readPipe(readLatency-2)
  }
}

// Register Configuration Interface Declaration
object RegCfgIO {
  def apply(): RegCfgIO = new RegCfgIO(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits)
  def apply(addrWidth: Int, dataWidth: Int): RegCfgIO = new RegCfgIO(addrWidth, dataWidth)
}

class RegCfgIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val v    = Input(Bool())
  val rw   = Input(Bool())  // rw flag, 1 is w, 0 is r
  val addr = Input(UInt(addrWidth.W))
  val din  = Input(UInt(dataWidth.W))
  val dout = Output(UInt(dataWidth.W))
}

// Register Configuration Interface Mux
class RegCfgMux extends Module {
  val io = IO(new Bundle {
    val regcfg = RegCfgIO()
    val regcfg_regmap = Flipped(RegCfgIO())
    val regcfg_mdcfg  = Flipped(RegCfgIO())
    val regcfg_srcmd  = Flipped(RegCfgIO())
    val regcfg_entry  = Flipped(RegCfgIO())
  })

  // Check if address falls within valid range for each register table
  val addrhit_info = (io.regcfg.addr >= IopmpParams.info_base_addr.U) && (io.regcfg.addr < IopmpParams.info_max_addr.U)
  val addrhit_mdcfg = (io.regcfg.addr >= IopmpParams.mdcfg_base_addr.U) && (io.regcfg.addr < IopmpParams.mdcfg_max_addr.U)
  val addrhit_srcmd = (io.regcfg.addr >= IopmpParams.srcmd_base_addr.U) && (io.regcfg.addr < IopmpParams.srcmd_max_addr.U)
  val addrhit_entry = (io.regcfg.addr >= IopmpParams.entry_base_addr.U) && (io.regcfg.addr < IopmpParams.entry_max_addr.U)

  val regcfgs = Seq(
    (io.regcfg_regmap, addrhit_info),
    (io.regcfg_mdcfg, addrhit_mdcfg),
    (io.regcfg_srcmd, addrhit_srcmd),
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

class SrcmdTableIO extends Bundle {
  val s_indx = Input(UInt(IopmpParams.srcmd_addr_width.W))
  val s_en = Input(Bool())
  val m_indx = Output(UInt(31.W))
}

class SrcmdTable extends Module {
  val io = IO(new Bundle {
    val regcfg = RegCfgIO()
    val bits = new SrcmdTableIO()
  })

  // srcmd_en(s).md
  val srcmd_en = Module(new TrueDualPortSRAM(addrWidth = log2Ceil(IopmpParams.srcmd_s), dataWidth = 31, readLatency = 1, depth = IopmpParams.srcmd_s))

	// regcfg port addr translate, Address interval 32
	val srcmd_en_addr = ((io.regcfg.addr(15,0)) >> 5.U)(log2Ceil(IopmpParams.srcmd_s)-1,0)

  // srcmd_en hit when io.regcfg.addr(4,0) = 0
  val srcmd_en_hit = io.regcfg.addr(4, 0) === 0.U

  // srcmd_en(s).l
  val lck = RegInit(0.U(IopmpParams.srcmd_s.W))
	val lck_delayed = RegNext(lck(srcmd_en_addr))

  // srcmd_en vaild
  val srcmd_en_v = srcmd_en_hit && io.regcfg.v
  val srcmd_en_v_delayed = RegNext(srcmd_en_v)

	// srcmdTable port A reg cfg r/w and lck
	// when srcmd.En(s).lck is true, the target srcmd.En(s) can not be modified
  when(srcmd_en_v){ srcmd_en.io.a_en := true.B }.otherwise { srcmd_en.io.a_en := false.B }
  when(srcmd_en_v && io.regcfg.rw && !lck(srcmd_en_addr)){ srcmd_en.io.a_we := true.B }.otherwise { srcmd_en.io.a_we := false.B }
	when(srcmd_en_v && io.regcfg.rw && !lck(srcmd_en_addr) && io.regcfg.din(0)){ lck := lck.bitSet(srcmd_en_addr, true.B) }
	when(srcmd_en_v && io.regcfg.rw && !lck(srcmd_en_addr)){ srcmd_en.io.a_wdata := io.regcfg.din(31, 1) }.otherwise { srcmd_en.io.a_wdata := 0.U }
	srcmd_en.io.a_addr := srcmd_en_addr
  when(srcmd_en_v_delayed){ io.regcfg.dout := Cat(srcmd_en.io.a_rdata, lck_delayed) }.otherwise { io.regcfg.dout := 0.U }
	
	// srcmdTable port B read with s indx
	when(io.bits.s_en) { srcmd_en.io.b_en := true.B }.otherwise { srcmd_en.io.b_en := false.B }
	srcmd_en.io.b_addr := io.bits.s_indx
	io.bits.m_indx := srcmd_en.io.b_rdata
	srcmd_en.io.b_we := false.B
	srcmd_en.io.b_wdata := 0.U
}

class MdcfgTableIO extends Bundle {
  val m_indx = Input(UInt(IopmpParams.mdcfg_addr_width.W))
  val m_en = Input(Bool())
  val j_indx = Output(UInt(16.W))
}

class MdcfgTable extends Module {
	val io = IO(new Bundle {
		val regcfg = RegCfgIO()
    val bits = new MdcfgTableIO()
	})

  // mdcfg(m)
  val mdcfg = Module(new TrueDualPortSRAM(addrWidth = IopmpParams.mdcfg_addr_width, dataWidth = 16, readLatency = 1, depth = IopmpParams.mdcfg_m))

  // regcfg port addr translate, Address interval 4
  val mdcfg_addr = ((io.regcfg.addr(15,0)) >> 2.U)(IopmpParams.mdcfg_addr_width-1,0)

  // mdcfg vaild
  val regcfg_v_delayed = RegNext(io.regcfg.v)

  // mdcfgTable port A reg cfg r/w
  when(io.regcfg.v){ mdcfg.io.a_en := true.B }.otherwise { mdcfg.io.a_en := false.B }
  when(io.regcfg.v && io.regcfg.rw){ mdcfg.io.a_we := true.B }.otherwise { mdcfg.io.a_we := false.B }
  when(io.regcfg.v && io.regcfg.rw){ mdcfg.io.a_wdata := io.regcfg.din(15, 0) }.otherwise { mdcfg.io.a_wdata := 0.U }
  mdcfg.io.a_addr := mdcfg_addr
  when(regcfg_v_delayed){ io.regcfg.dout := Cat(0.U(16.W), mdcfg.io.a_rdata) }.otherwise { io.regcfg.dout := 0.U }

  // mdcfgTable port B read with m indx
  when(io.bits.m_en) { mdcfg.io.b_en := true.B }.otherwise { mdcfg.io.b_en := false.B }
  mdcfg.io.b_addr := io.bits.m_indx
  io.bits.j_indx := mdcfg.io.b_rdata
  mdcfg.io.b_we := false.B
  mdcfg.io.b_wdata := 0.U
}

class EntryAttribute extends Bundle{
  val r      = UInt(1.W)
  val w      = UInt(1.W)
  val x      = UInt(1.W)
  val a      = UInt(2.W)
  val sire   = UInt(1.W)
  val siwe   = UInt(1.W)
  val sixe   = UInt(1.W)
  val sere   = UInt(1.W)
  val sewe   = UInt(1.W)
  val sexe   = UInt(1.W)
}


class EntryTableIO extends Bundle {
  val j_indx = Input(UInt(IopmpParams.entry_addr_width.W))
  val j_en   = Input(Bool())
  val addr   = Output(UInt(64.W))
  val attribute = Output(new EntryAttribute)
}

class EntryTable extends Module {
  val io = IO(new Bundle {
    val regcfg = RegCfgIO()
    val bits = new EntryTableIO()
  })

  // entry(i)
  val entry_addr  = Module(new TrueDualPortSRAM(addrWidth = IopmpParams.entry_addr_width, dataWidth = 32, readLatency = 1, depth = IopmpParams.entry_j))
  val entry_addrh  = Module(new TrueDualPortSRAM(addrWidth = IopmpParams.entry_addr_width, dataWidth = 32, readLatency = 1, depth = IopmpParams.entry_j))
  val entry_cfg  = Module(new TrueDualPortSRAM(addrWidth = IopmpParams.entry_addr_width, dataWidth = 11, readLatency = 1, depth = IopmpParams.entry_j))
  val entrySeq = Seq(entry_addr, entry_addrh, entry_cfg)
  
  // regcfg port addr translate, Address interval 16
  val entryTable_addr = ((io.regcfg.addr(15,0)) >> 4.U)(IopmpParams.entry_addr_width-1,0)

  // subtable port addr hit
  val entry_hit_addr = io.regcfg.addr(3, 2) === 0.U(2.W) 
  val entry_hit_addrh = io.regcfg.addr(3, 2) === 1.U(2.W) 
  val entry_hit_cfg = io.regcfg.addr(3, 2) === 2.U(2.W) 
  val entry_hit_Seq = Seq(entry_hit_addr, entry_hit_addrh, entry_hit_cfg)

  // entry valid
  val entry_v_seq = entry_hit_Seq.map(_ && io.regcfg.v)
  val entry_v_delayed_seq = entry_v_seq.map(RegNext(_))

  // entry Table port A reg cfg r/w
  entrySeq.zip(entry_v_seq).zipWithIndex.foreach { case ((sram, v), idx) =>
    sram.io.a_en := v
    sram.io.a_we := v && io.regcfg.rw
    sram.io.a_addr := entryTable_addr
    sram.io.a_wdata := (idx match {
      case 2 => Mux(v && io.regcfg.rw, io.regcfg.din(10, 0), 0.U(11.W))
      case _ => Mux(v && io.regcfg.rw, io.regcfg.din, 0.U(32.W))
    })
  }

  // io.regcfg.dout
  io.regcfg.dout := MuxCase(0.U,
    entrySeq.zip(entry_v_delayed_seq).zipWithIndex.map { case ((sram, v_delayed), idx) =>
      idx match {
        case 2 => v_delayed -> Cat(0.U(21.W), sram.io.a_rdata)
        case _ => v_delayed -> sram.io.a_rdata
      }
    }
  )

  // entry_Table port B read with j indx
  val tmp_entry_addr  = Wire(UInt(32.W))
  val tmp_entry_addrh = Wire(UInt(32.W))
  val tmp_entry_cfg   = Wire(UInt(11.W))

  entrySeq.zipWithIndex.foreach { case (sram, idx) =>
    sram.io.b_en := io.bits.j_en
    sram.io.b_addr := io.bits.j_indx
    sram.io.b_we := false.B
    sram.io.b_wdata := 0.U
    idx match {
      case 0 => tmp_entry_addr  := sram.io.b_rdata
      case 1 => tmp_entry_addrh := sram.io.b_rdata
      case 2 => tmp_entry_cfg   := sram.io.b_rdata
    }
  }

  io.bits.addr             := Cat(tmp_entry_addrh, tmp_entry_addr)
  io.bits.attribute.r      := tmp_entry_cfg(0)
  io.bits.attribute.w      := tmp_entry_cfg(1)
  io.bits.attribute.x      := tmp_entry_cfg(2)
  io.bits.attribute.a      := tmp_entry_cfg(4, 3) // 2 bits for a
  io.bits.attribute.sire   := tmp_entry_cfg(5)
  io.bits.attribute.siwe   := tmp_entry_cfg(6)
  io.bits.attribute.sixe   := tmp_entry_cfg(7)
  io.bits.attribute.sere   := tmp_entry_cfg(8)
  io.bits.attribute.sewe   := tmp_entry_cfg(9)
  io.bits.attribute.sexe   := tmp_entry_cfg(10)
}

// registers
// info register
object RegVersion {
  def apply(): RegVersion = {
    val init_val = Wire(new RegVersion)
    init_val.vendor  := IopmpParams.vendor.U
    init_val.specver := IopmpParams.specver.U
    RegInit(init_val)
  }
}
class RegVersion extends Bundle {
  val specver           = UInt(8.W)  // [R   ] The specification version. (it will be defined in ratifiedversion).
  val vendor            = UInt(24.W) // [R   ] The JEDEC manufacturer ID.
  //to 32 bits
  def toUInt: UInt = Cat(specver, vendor)
}

object RegImplementation {
  def apply(): RegImplementation = {
    val init_val = Wire(new RegImplementation)
    init_val.impid := IopmpParams.impid.U
    RegInit(init_val)
  }
}
class RegImplementation extends Bundle {
  val impid             = UInt(32.W) // [R   ] The user-defined implementation ID.
  //to 32 bits
  def toUInt: UInt = Cat(impid)
}

// hwcfg0 register
object RegHwcfg0 {
  def apply(): RegHwcfg0 = {
    val init_val = Wire(new RegHwcfg0)
    init_val.mdcfg_fmt          := IopmpParams.mdcfg_fmt.U           // 0.U
    init_val.srcmd_fmt          := IopmpParams.srcmd_fmt.U           // 0.U
    init_val.tor_en             := IopmpParams.tor_en.U              // 1.U
    init_val.sps_en             := IopmpParams.sps_en.U              // 0.U
    init_val.user_cfg_en        := IopmpParams.user_cfg_en.U         // 0.U
    init_val.prient_prog        := IopmpParams.prient_prog.U         // 1.U
    init_val.rrid_transl_en     := IopmpParams.rrid_transl_en.U      // 1.U
    init_val.rrid_transl_prog   := IopmpParams.rrid_transl_prog.U    // 1.U
    init_val.chk_x              := IopmpParams.chk_x.U               // 0.U
    init_val.no_x               := IopmpParams.no_x.U                // 0.U
    init_val.no_w               := IopmpParams.no_w.U                // 1.U
    init_val.stall_en           := IopmpParams.stall_en.U            // 1.U
    init_val.peis               := IopmpParams.peis.U                // 1.U
    init_val.pees               := IopmpParams.pees.U                // 1.U
    init_val.mfr_en             := IopmpParams.mfr_en.U              // 0.U
    init_val.md_entry_num       := IopmpParams.md_entry_num.U        // 0.U
    init_val.md_num             := IopmpParams.md_num.U              // 31.U
    init_val.addrh_en           := IopmpParams.addrh_en.U            // 1.U
    init_val.enable             := IopmpParams.enable.U              // 0.U
    RegInit(init_val)
  }
}
class RegHwcfg0 extends Bundle {
  val enable            = UInt(1.W)  // [W1SS] Indicate if the IOPMP checks transactions by default
  val addrh_en          = UInt(1.W)  // [R   ] Indicate if ENTRY_ADDRH(i) and ERR_MSIADDRH (if ERR_CFG.msi_en = 1) are available
  val md_num            = UInt(6.W)  // [R   ] Indicate the supported number of MD in the instance
  val md_entry_num      = UInt(7.W)  // [WARL] When HWCFG0.mdcfg_fmt = 0x0: must be zero
  val mfr_en            = UInt(1.W)  // [R   ] Indicate if the IOPMP implements Multi Faults Record Extension
  val pees              = UInt(1.W)  // [R   ] Indicate if the IOPMP implements the error suppression per entry
  val peis              = UInt(1.W)  // [R   ] Indicate if the IOPMP implements interrupt suppression per entry
  val stall_en          = UInt(1.W)  // [R   ] Indicate if the IOPMP implements stall-related features
  val no_w              = UInt(1.W)  // [R   ] Indicate if the IOPMP always fails write accesses considered as as no rule matched
  val no_x              = UInt(1.W)  // [R   ] For chk_x=1, the IOPMP with no_x=1 always fails on an instruction fetch
  val chk_x             = UInt(1.W)  // [R   ] Indicate if the IOPMP implements the check of an instruction fetch.
  val rrid_transl_prog  = UInt(1.W)  // [W1CS] A write-1-clear bit is sticky to 0 and indicate if the field rrid_transl is programmable.
  val rrid_transl_en    = UInt(1.W)  // [R   ] Indicate the if tagging a new RRID on the initiator port is supported
  val prient_prog       = UInt(1.W)  // [W1CS] A write-1-clear bit is sticky to 0 and indicates if HWCFG2.prio_entry is programmable
  val user_cfg_en       = UInt(1.W)  // [R   ] Indicate if user customized attributes is supported
  val sps_en            = UInt(1.W)  // [R   ] Indicate secondary permission settings is supported
  val tor_en            = UInt(1.W)  // [R   ] Indicate if TOR is supported
  val srcmd_fmt         = UInt(2.W)  // [R   ] Indicate the SRCMD format
  val mdcfg_fmt         = UInt(2.W)  // [R   ] Indicate the MDCFG format 
  //to 32 bits
  def toUInt: UInt = Cat(enable, addrh_en, md_num, md_entry_num, mfr_en, pees, peis, stall_en, no_w, no_x, chk_x, rrid_transl_prog, rrid_transl_en, prient_prog, user_cfg_en, sps_en, tor_en, srcmd_fmt, mdcfg_fmt)
}

// hwcfg1 register
object RegHwcfg1 {
  def apply(): RegHwcfg1 = {
    val init_val = Wire(new RegHwcfg1)
    init_val.rrid_num  := IopmpParams.rrid_num.U
    init_val.entry_num := IopmpParams.entry_num.U
    RegInit(init_val)
  }
}
class RegHwcfg1 extends Bundle {
  val entry_num         = UInt(16.W) // [R   ] Indicate the number of entries
  val rrid_num          = UInt(16.W) // [R   ] Indicate the number of RRIDs
  //to 32 bits
  def toUInt: UInt = Cat(entry_num, rrid_num)
}

// hwcfg2 register
object RegHwcfg2 {
  def apply(): RegHwcfg2 = {
    val init_val = Wire(new RegHwcfg2)
    init_val.prio_entry  := IopmpParams.prio_entry.U
    init_val.rrid_transl := IopmpParams.rrid_transl.U
    RegInit(init_val)
  }
}
class RegHwcfg2 extends Bundle {
  val rrid_transl       = UInt(16.W) // [WARL] The RRID tagged to outgoing transactions
  val prio_entry        = UInt(16.W) // [WARL] Indicate the number of entries matched with priority
  //to 32 bits
  def toUInt: UInt = Cat(rrid_transl, prio_entry)
}

// entryoffset register
object RegEntryoffset {
  def apply(): RegEntryoffset = {
    val init_val = Wire(new RegEntryoffset)
    init_val.offset := IopmpParams.entry_base_addr.U
    RegInit(init_val)
  }
}
class RegEntryoffset extends Bundle {
  val offset            = UInt(32.W) // [R   ] Indicate the offset address of the entryTable array
  //to 32 bits
  def toUInt: UInt = Cat(offset)
}

/* not implemented begin
class RegHwcfguser extends Bundle {
  val offset            = UInt(32.W) // [R   ] (Optional) user-defined registers
}

// programming protection registers
class RegMdstall extends Bundle {
  val is_stalled        = UInt(1.W)  // [R   ] 1 indicates that all requested stalls take effect; otherwise, 0
  val md                = UInt(31.W) // [WARL] Writing md[m]=1 selects MD m; reading md[m] = 1 means MD m selected
  val exempt            = UInt(1.W)  // [W   ] Stall transactions with exempt selected MDs, or Stall selected MDs  
}

class RegMdstallh extends Bundle {
  val mdh               = UInt(32.W) // [WARL] Writing mdh[m]=1 selects MD (m+31); reading md[m] = 1 means MD (m+31) selected
}

class RegRridscp extends Bundle {
  val stat              = UInt(2.W)  // [R   ] --
  val op                = UInt(2.W)  // [W   ] --
  val rsv               = UInt(14.W) // [ZERO] Must be zero on write, reserved for future
  val rrid              = UInt(16.W) // [WARL] RRID to select
}

class RegRridscp extends Bundle {  
  val stat              = UInt(2.W)  // [R   ] --
  val op                = UInt(2.W)  // [W   ] --
  val rsv               = UInt(14.W) // [ZERO] Must be zero on write, reserved for future
  val rrid              = UInt(16.W) // [WARL] RRID to select
}

// configuration protection register
class RegMdlck extends Bundle {
  val md                = UInt(31.W) // [WARL] md[m] is sticky to 1 and indicates if SRCMD_EN(s).md[m], SRCMD_R(i).md[m] and SRCMD_W(s).md[m] are locked for all RRID s
  val l                 = UInt(1.W)  // [W1SS] Lock bit to MDLCK and MDLCKH register
}

class RegMdlckh extends Bundle {
  val mdh               = UInt(31.W) // [WARL] --
}

class RegMdcfglck extends Bundle {
  val rsv               = UInt(25.W) // [ZERO] Must be zero on write, reserved for future
  val f                 = UInt(6.W)  // [WARL] Indicate the number of locked MDCFG entries - MDCFG(m) is locked for m < f
  val l                 = UInt(1.W)  // [W1SS] Lock bit to MDCFGLCK register
}

class RegEntrylck extends Bundle {
  val rsv               = UInt(15.W) // [ZERO] Must be zero on write, reserved for future
  val f                 = UInt(16.W) // [WARL] Indicate the number of locked ENTRY entries - ENTRY(j) is locked for j < f
  val l                 = UInt(1.W)  // [W1SS] Lock bit to ENTRYLCK register 
}
not implemented end */

// error cfg register
object RegErrcfg {
  def apply(): RegErrcfg = {
    val init_val = Wire(new RegErrcfg)
    init_val.l                  := 0.U
    init_val.ie                 := 0.U
    init_val.rs                 := 0.U
    init_val.msi_en             := IopmpParams.msi_en.U // 1.U
    init_val.stall_violation_en := IopmpParams.stall_violation_en.U // 1.U
    init_val.rsv1               := 0.U
    init_val.msidata            := IopmpParams.msidata.U // 0x000.U
    init_val.rsv2               := 0.U
    RegInit(init_val)
  }
}
class RegErrcfg extends Bundle {
  val rsv2              = UInt(13.W) // [ZERO] Must be zero on write, reserved for future
  val msidata           = UInt(11.W) // [WARL] The data to trigger MSI
  val rsv1              = UInt(3.W)  // [ZERO] Must be zero on write, reserved for future
  val stall_violation_en= UInt(1.W)  // [WARL] Indicates whether the IOPMP faults stalled transactions
  val msi_en            = UInt(1.W)  // [WARL] Indicates whether the IOPMP triggers interrupt by MSI or wired interrupt
  val rs                = UInt(1.W)  // [WARL] To suppress an error response on an IOPMP rule violation
  val ie                = UInt(1.W)  // [RW  ] Enable the interrupt of the IOPMP rule violation
  val l                 = UInt(1.W)  // [W1SS] Lock fields to ERR_CFG register
  //to 32 bits
  def toUInt: UInt = Cat(rsv2, msidata, rsv1, stall_violation_en, msi_en, rs, ie, l)
}

// error info register
object RegErrinfo {
  def apply(): RegErrinfo = {
    val init_val = Wire(new RegErrinfo)
    init_val.v        := 0.U
    init_val.ttype    := 0.U
    init_val.msi_werr := IopmpParams.msi_werr.U // 0.U
    init_val.etype    := 0.U
    init_val.svc      := 0.U
    init_val.rsv      := 0.U
    RegInit(init_val)
  }
}
class RegErrinfo extends Bundle {
  val rsv               = UInt(23.W) // [ZERO] Must be zero on write, reserved for future
  val svc               = UInt(1.W)  // [R   ] Indicate there is a subsequent violation caught in ERR_MFR
  val etype             = UInt(4.W)  // [R   ] Indicated the type of violation
  val msi_werr          = UInt(1.W)  // [R/W1C] It’s asserted when the write access to trigger an IOPMP-originated MSI has failed.
  val ttype             = UInt(2.W)  // [R   ]  Indicated the transaction type of the first captured violation
  val v                 = UInt(1.W)  // [R/W1C] Indicate if the illegal capture recorder valid
  //to 32 bits
  def toUInt: UInt = Cat(rsv, svc, etype, msi_werr, ttype, v)
}

// error req addr register
object RegErreqaddr {
  def apply(): RegErreqaddr = {
    val init_val = Wire(new RegErreqaddr)
    init_val.addr := 0.U
    RegInit(init_val)
  }
}

class RegErreqaddr extends Bundle {
  val addr              = UInt(32.W) // [R   ] Indicate the errored address[33:2]
  // to 32 bits
  def toUInt: UInt = Cat(addr) 
}

// error req addrh register
object RegErreqaddrh {
  def apply(): RegErreqaddrh = {
    val init_val = Wire(new RegErreqaddrh)
    init_val.addrh := 0.U
    RegInit(init_val)
  }
}

class RegErreqaddrh extends Bundle {
  val addrh             = UInt(32.W) // [R   ] Indicate the errored address[65:34]
  // to 32 bits
  def toUInt: UInt = Cat(addrh)
}

// error req id register
object RegErreqid {
  def apply(): RegErreqid = {
    val init_val = Wire(new RegErreqid)
    init_val.rrid := 0.U
    init_val.eid  := 0.U
    RegInit(init_val)
  }
}
class RegErreqid extends Bundle {
  val eid               = UInt(16.W) // [R   ] Indicates the index pointing to the entry that catches the violation
  val rrid              = UInt(16.W) // [R   ] Indicate the errored RRID
  // to 32 bits
  def toUInt: UInt = Cat(eid, rrid)
}

/* not implemented begin
class RegErrmfr extends Bundle {
  val svs               = UInt(1.W)  // [R   ] The status of this window’s content
  val rsv               = UInt(3.W)  // [ZERO] Must be zero on write, reserved for future
  val svi               = UInt(12.W) // [WARL] Window’s index to search subsequent violations
  val svw               = UInt(16.W) // [R   ] Subsequent violations in the window
}

class RegErrmsiaddr extends Bundle {
  val msiaddr           = UInt(32.W) // [WARL] The address to trigger MSI
}

class RegErrmsiaddrh extends Bundle {
  val msiaddrh          = UInt(32.W) // [WARL] The address to trigger MSI
}

class RegErruser extends Bundle {
  val user              = UInt(32.W) // [WARL] (Optional) user-defined registers
}
not implemented end */

class RegMapIO extends Bundle {
  // logic in
  val i_errinfo_v = Input(UInt(1.W)) // caputure a illegal action
  val i_errinfo_ttype = Input(UInt(2.W)) // transaction type of the first captured violation
  val i_errinfo_etype = Input(UInt(4.W)) // type of violation
  val i_erreqaddr = Input(UInt(32.W)) // Indicate the errored address[33:2]
  val i_erreqaddrh = Input(UInt(32.W)) // Indicate the errored address[65:34]
  val i_erreqid_rrid = Input(UInt(16.W)) // Indicate the errored RRID
  val i_erreqid_eid = Input(UInt(16.W)) // Indicate the errored entry index
  //logic out
  val o_reg_hwcfg0_enable = Output(UInt(1.W)) // IOPMP checker enable
  val o_reg_hwcfg0_no_w = Output(UInt(1.W)) // IOPMP if always write access is not allowed
  val o_reg_hwcfg1_rrid_num = Output(UInt(16.W)) // IOPMP supported RRID number
  val o_reg_hwcfg2_prio_entry = Output(UInt(16.W)) // IOPMP priority entry number
  val o_reg_errcfg_ie = Output(UInt(1.W)) // interrupt enable
  val o_reg_errcfg_rs = Output(UInt(1.W)) // bus response suppression
  val o_reg_errcfg_msi_en = Output(UInt(1.W)) // MSI enable
  val o_reg_errcfg_msidata = Output(UInt(11.W)) // MSI data
  val o_reg_errinfo_v = Output(UInt(1.W)) // Indicate captured a illegal action, wire to int
  val o_reg_errinfo_ttype = Output(UInt(2.W)) // error info
}

class RegMap extends Module {
  val io = IO(new Bundle {
    val regcfg = RegCfgIO()
    val bits = new RegMapIO()
  })

  // Declare registers
  val reg_version        = RegVersion()
  val reg_implementation = RegImplementation()
  val reg_hwcfg0         = RegHwcfg0()
  val reg_hwcfg1         = RegHwcfg1()
  val reg_hwcfg2         = RegHwcfg2()
  val reg_entryoffset    = RegEntryoffset()
  val reg_errcfg         = RegErrcfg()
  val reg_errinfo        = RegErrinfo()
  val reg_erreqaddr      = RegErreqaddr()
  val reg_erreqaddrh     = RegErreqaddrh()
  val reg_erreqid        = RegErreqid()

  // addr offset
  val addr_offset = io.regcfg.addr(15, 0) 

  // addr hit
  // val addrhit_version        = addr_offset === IopmpParams.reg_version_addr_offset
  // val addrhit_implementation = addr_offset === IopmpParams.reg_implementation_addr_offset
  val addrhit_hwcfg0         = addr_offset === IopmpParams.reg_hwcfg0_addr_offset
  // val addrhit_hwcfg1         = addr_offset === IopmpParams.reg_hwcfg1_addr_offset
  val addrhit_hwcfg2         = addr_offset === IopmpParams.reg_hwcfg2_addr_offset
  // val addrhit_entryoffset    = addr_offset === IopmpParams.reg_entryoffset_addr_offset
  val addrhit_errcfg         = addr_offset === IopmpParams.reg_errcfg_addr_offset
  val addrhit_errinfo        = addr_offset === IopmpParams.reg_errinfo_addr_offset
  // val addrhit_erreqaddr      = addr_offset === IopmpParams.reg_erreqaddr_addr_offset
  // val addrhit_erreqaddrh     = addr_offset === IopmpParams.reg_erreqaddrh_addr_offset
  // val addrhit_erreqid        = addr_offset === IopmpParams.reg_erreqid_addr_offset

  // registers write
  // reg_hwcfg0
  when(io.regcfg.v && io.regcfg.rw && addrhit_hwcfg0) {
    val w = io.regcfg.din.asTypeOf(new RegHwcfg0)
    when(reg_hwcfg0.prient_prog === 1.U && w.prient_prog === 1.U) {  // W1CS
      reg_hwcfg0.prient_prog := 0.U
    }
    // unsupported rrid_transl_en and related features
    // when(reg_hwcfg0.rrid_transl_prog === 1.U && w.rrid_transl_prog === 1.U) {  // W1CS
    //   reg_hwcfg0.rrid_transl_prog := 0.U
    // }
    // reg_hwcfg0.md_entry_num := w.md_entry_num  // WARL-for mdcfg_fmt=0x0, must be zero
    reg_hwcfg0.enable := reg_hwcfg0.enable | w.enable  // W1SS
  }

  // reg_hwcfg2        
  when(io.regcfg.v && io.regcfg.rw && addrhit_hwcfg2) {
    val w = io.regcfg.din.asTypeOf(new RegHwcfg2)
    // val w = io.regcfg.din.asTypeOf(RegHwcfg2())
    when(reg_hwcfg0.prient_prog === 1.U) {
      reg_hwcfg2.prio_entry := w.prio_entry  // WARL
    }
    // unsupported rrid_transl_en and related features
    // when(reg_hwcfg0.rrid_transl_prog === 1.U) {
    //   reg_hwcfg2.rrid_transl := w.rrid_transl  // WARL
    // } 
  }

  // reg_errcfg        
  when(io.regcfg.v && io.regcfg.rw && addrhit_errcfg) {
    val w = io.regcfg.din.asTypeOf(new RegErrcfg)
    reg_errcfg.l := reg_errcfg.l | w.l  // W1SS
    when(reg_errcfg.l === 0.U) {
      reg_errcfg.ie := w.ie  // RW
      reg_errcfg.rs := w.rs  // WARL
      reg_errcfg.msi_en := w.msi_en  // WARL
      reg_errcfg.stall_violation_en := w.stall_violation_en  // WARL
      reg_errcfg.msidata := w.msidata  // WARL
    }
  }
  // reg_errinfo  reqaddr & reqaddrh & reqid
  when(io.bits.i_errinfo_v === 1.U && reg_errinfo.v =/= 1.U) {
    reg_errinfo.v := 1.U
    reg_errinfo.ttype := io.bits.i_errinfo_ttype // transaction type of the first captured violation
    reg_errinfo.etype := io.bits.i_errinfo_etype // type of violation
    reg_erreqaddr.addr := io.bits.i_erreqaddr // Indicate the errored address[33:2]
    reg_erreqaddrh.addrh := io.bits.i_erreqaddrh // Indicate the errored address[65:34]
    reg_erreqid.rrid := io.bits.i_erreqid_rrid // Indicate the errored RRID
    reg_erreqid.eid := io.bits.i_erreqid_eid // Indicate the errored entry index
  }.elsewhen(io.regcfg.v && io.regcfg.rw && addrhit_errinfo) {
    val w = io.regcfg.din.asTypeOf(new RegErrinfo)
    when(w.v === 1.U) {   // R/W1C
      reg_errinfo.v := 0.U
    }
    // when(w.msi_werr === 1.U) {   // R/W1C
    //   reg_errinfo.msi_werr := 0.U
    // }
  }

  // read registers
  val regcfg_dout = RegInit(0.U(32.W))
  regcfg_dout := MuxLookup(addr_offset, 0.U)(Seq(
    IopmpParams.reg_version_addr_offset        -> reg_version.toUInt,
    IopmpParams.reg_implementation_addr_offset -> reg_implementation.toUInt,
    IopmpParams.reg_hwcfg0_addr_offset         -> reg_hwcfg0.toUInt,
    IopmpParams.reg_hwcfg1_addr_offset         -> reg_hwcfg1.toUInt,
    IopmpParams.reg_hwcfg2_addr_offset         -> reg_hwcfg2.toUInt,
    IopmpParams.reg_entryoffset_addr_offset    -> reg_entryoffset.toUInt,
    IopmpParams.reg_errcfg_addr_offset         -> reg_errcfg.toUInt,
    IopmpParams.reg_errinfo_addr_offset        -> reg_errinfo.toUInt,
    IopmpParams.reg_erreqaddr_addr_offset      -> reg_erreqaddr.toUInt,
    IopmpParams.reg_erreqaddrh_addr_offset     -> reg_erreqaddrh.toUInt,
    IopmpParams.reg_erreqid_addr_offset        -> reg_erreqid.toUInt
  ))

  io.regcfg.dout := regcfg_dout

// output registers to other modules
  io.bits.o_reg_hwcfg0_enable      := reg_hwcfg0.enable
  io.bits.o_reg_hwcfg0_no_w        := reg_hwcfg0.no_w
  io.bits.o_reg_hwcfg1_rrid_num    := reg_hwcfg1.rrid_num
  io.bits.o_reg_hwcfg2_prio_entry  := reg_hwcfg2.prio_entry
  io.bits.o_reg_errcfg_ie          := reg_errcfg.ie
  io.bits.o_reg_errcfg_rs          := reg_errcfg.rs
  io.bits.o_reg_errcfg_msi_en      := reg_errcfg.msi_en
  io.bits.o_reg_errcfg_msidata     := reg_errcfg.msidata
  io.bits.o_reg_errinfo_v          := reg_errinfo.v
  io.bits.o_reg_errinfo_ttype      := reg_errinfo.ttype
}

/*
  OptTreePriorityEncoder: Optimized tree-based priority encoder
  - Recursive implementation, suitable for wide input bit-widths
  - Input: UInt, each bit represents a priority
  - Output: Index of the highest priority (lowest index) bit set to 1, returns 0 if none
  - For width <= 4, use Chisel's PriorityEncoder directly
  - For wider inputs, recursively split into upper/lower halves, prioritize lower half
  - If lower half has 1, return its result; otherwise, concatenate upper half result
  - Use Cat for concatenation to ensure correct width and index
*/
object OptTreePriorityEncoder {
  def apply(in: UInt): UInt = {
    val width = in.getWidth
    if (width <= 4) {
      PriorityEncoder(in)
    } else {
      val half = 1 << (log2Ceil(width) - 1)
      val lower = in(half-1, 0)
      val upper = in(width-1, half)
      val lower_has = lower.orR
      
      val lower_result = OptTreePriorityEncoder(lower)
      val upper_result = OptTreePriorityEncoder(upper)
      
      // 使用位拼接替代加法，完全消除算术运算
      val upper_offset_bits = log2Ceil(width) - log2Ceil(half)
      Mux(lower_has,
          lower_result,
          Cat(1.U(upper_offset_bits.W), upper_result))
    }
  }
}

/*NAPOT address range decoder
in : UInt(IopmpParams.soc_addr_width.W) // 66 bit addr，{entry_addrh, entry_addr, 2'b11}
etc: input address is：010_0011，mask = 011, output address range is: 010_0000 - 010_0111

Explain0：
  NA4: size = trailing_ones(all 0) + 2 = 2
  NAPOT4: size = trailing_ones(mayeby 0) + 3
  range = 2^size
  addr_start = base_addr
  addr_end = base_addr + range - 1

Explain1：
  entry_addr = padding 01 (NA4)/ padding 11(NAPOT4)
  size = trailing_ones + 1
  mask = (1 << size) - 1
  base = value & ~mask
  addr_start = entry_addr & (~mask)
  addr_end = addr_start + mask

example：
  input address：010_0000，mask = 0000，output address range: 010_0000 - 010_0001，2byte  = 2^(x+1) not support
  input address：010_0001，mask = 0001，output address range: 010_0000 - 010_0011，4byte  = 2^(x+1) NA4
  input address：010_0011，mask = 0011，output address range: 010_0000 - 010_0111，8byte  = 2^(x+1) NAPOT
  input address：010_0111，mask = 0111，output address range: 010_0000 - 010_1111，16byte = 2^(x+1) NAPOT
  input address：010_1111，mask = 1111，output address range: 010_0000 - 011_1111，32byte = 2^(x+1) NAPOT
  ...
  ...
 */
object NAPOTDecoder {
  def apply(entry_addr: UInt): (UInt, UInt) = {

    val addr_start = Wire(UInt(IopmpParams.soc_addr_width.W))
    val addr_end = Wire(UInt(IopmpParams.soc_addr_width.W))

    // find trailing ones
    val trailing_ones = Wire(UInt(log2Ceil(IopmpParams.soc_addr_width).W))
    trailing_ones := PriorityEncoder(~entry_addr)
    DebugUtils.debug(trailing_ones,"trailing_ones_debug")

    // get mask
    val size = trailing_ones + 1.U
    val mask = Wire(UInt(IopmpParams.soc_addr_width.W))
    mask := (1.U << size) - 1.U
    DebugUtils.debug(mask,"mask_debug")

    // address
    addr_start := entry_addr & (~mask).asUInt
    addr_end := addr_start + mask
    
    (addr_start, addr_end)
  }
}

// edge detection for signals
object EdgeDetect {
  def rising(signal: Bool): Bool = {
    val prev = RegNext(signal)
    !prev && signal
  }
  
  def falling(signal: Bool): Bool = {
    val prev = RegNext(signal)
    prev && !signal
  }
  
  def both(signal: Bool): Bool = {
    val prev = RegNext(signal)
    prev =/= signal
  }
}

class ReqBundle extends Bundle {
  val rrid = UInt(IopmpParams.srcmd_addr_width.W)
  val pa   = UInt(IopmpParams.soc_addr_width.W)
  val rw   = Bool()
  val len  = UInt(IopmpParams.soc_addr_width.W)
}

class ReqIO extends DecoupledIO(new ReqBundle)

class RspBundle extends Bundle{
  val cf_r = UInt(1.W)
  val cf_w = UInt(1.W)
}
class RspIO extends DecoupledIO(new RspBundle)

class Ctrl extends Module {
  val io = IO(new Bundle {
    // check req
    val req = Flipped(new ReqIO)
    // check rsp
    val resp = new RspIO
    //srcmd
    val srcmd = Flipped(new SrcmdTableIO)
    //mdcfg
    val mdcfg = Flipped(new MdcfgTableIO)
    //entry
    val entry = Flipped(new EntryTableIO)
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
    val sIdle, sSrcmd, sMdcfg, sMdcfgPre, sEntry, sPriority, sMatching, sErr, sDone = Value
  }

  val state = RegInit(State.sIdle)

  // reg the fired req
  val req = RegInit(0.U.asTypeOf(new ReqBundle))

  // error
  val ttype = RegInit(0.U(2.W)) // transcation type
  val etype = RegInit(0.U(4.W)) // error type
  val eid = RegInit(0.U(16.W)) // error entry id

  // srcmdTable ,io.srcmd.s_en delay 1
  val srcmd_s_en_d = RegInit(false.B)

  // mdcfgTable
  val md = RegInit(0.U(IopmpParams.md_num.W)) // reg md read from rscmdTable
  val md_hasTask = md =/= 0.U // check if there is task undone
  val md_taskIdx = PriorityEncoder(md) // find the lowest priority bit
  val md_taskIdx_d = RegNext(md_taskIdx) // md_taskIdx delay 1
  val md_taskMask = (1.U << md_taskIdx) // mask for md_taskIdx
  val md_t = RegInit(0.U(16.W)) // reg the md(t) from mdcfgTable
  val md_t_pre = RegInit(0.U(16.W)) // reg the md(t-1) from mdcfgTable
  val mdcfg_m_en_d = RegInit(false.B) // io.mdcfg.m_en delay 1

  // entryTable
  val j_indx = RegInit(0.U(16.W)) // reg the j_indx
  val j_indx_d = RegInit(0.U(16.W))
  val entry_j_en_d = RegInit(false.B)
  val entry_attribute = RegInit(0.U.asTypeOf(new EntryAttribute))
  
  // matching
  val pri_hit = WireDefault(false.B) // hit
  val pri_part_hit = WireDefault(false.B) // partial hit
  val cf_r = RegInit(0.U(1.W)) // check fail read
  val cf_w = RegInit(0.U(1.W)) // check fail write

// state machine
  switch(state) {
    //0
    is(State.sIdle) {
      when(io.req.fire) {
        when(io.reg.o_reg_hwcfg0_enable === 1.U) {
          when(io.req.bits.rrid < io.reg.o_reg_hwcfg1_rrid_num){ //legal rrid fired
            state:= State.sSrcmd
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
    is(State.sSrcmd) {
      when(io.stall === 0.U) { 
        state := State.sMdcfg
      }
    }
    //2
    is(State.sMdcfg) {
      // polling mdcfgTable
      when(srcmd_s_en_d === true.B) {
        md := io.srcmd.m_indx
        state := State.sMdcfg
      }.elsewhen(md_hasTask) {
        md := md & (~md_taskMask).asUInt // clear the md has been checked
        state := State.sMdcfgPre
      }.otherwise { // no md hit
        etype := 0x5.U // 0x5 : not hit any rule
        state := State.sErr
      }
    }
    //3
    is(State.sMdcfgPre) { //read back md_t_pre
      state := State.sEntry
    }
    //4
    is(State.sEntry) {  // initial j=md_t_pre
      when(!mdcfg_m_en_d){
        state := State.sPriority
      }
    }
    //5
    is(State.sPriority) {// polling check md(m-1).t <= j < md(m).t
      when(pri_hit) {
        state := State.sMatching // hit
      }.elsewhen(pri_part_hit) {
        etype := 0x4.U // 0x4 : partial hit on a priority rule
        state := State.sErr
      }.elsewhen(j_indx === md_t){
        state := State.sMdcfg // if j=md(m).t, go to mdcfg
      }
    }
    //6
    is(State.sMatching) {
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
    //7
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
    //8
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

  // req ready
  io.req.ready := state === State.sIdle

  // read srcmd
  when(state === State.sSrcmd) {
    when(true.B) { // may add stall logic
      io.srcmd.s_indx := req.rrid
      io.srcmd.s_en := true.B
    }.otherwise {
      io.srcmd.s_indx := 0.U
      io.srcmd.s_en := false.B
    }
  }.otherwise {
    io.srcmd.s_indx := 0.U
    io.srcmd.s_en := false.B
  }
  srcmd_s_en_d := io.srcmd.s_en

  // read mdcfg
  when(state === State.sMdcfg && md_hasTask) { // if have undone md task, read md.t
      md_taskIdx_d := md_taskIdx
      io.mdcfg.m_indx := md_taskIdx
      io.mdcfg.m_en := true.B
  }.elsewhen(state === State.sMdcfgPre && md_taskIdx_d =/= 0.U) { // read pre md.t
      io.mdcfg.m_indx := md_taskIdx_d - 1.U
      io.mdcfg.m_en := true.B
  }.otherwise {
      io.mdcfg.m_indx := 0.U
      io.mdcfg.m_en := false.B
  }
  mdcfg_m_en_d := io.mdcfg.m_en
  // get md_t and md_t_pre
  when(state === State.sMdcfgPre){
    md_t := io.mdcfg.j_indx 
    when(md_taskIdx_d === 0.U) { // if md_t is 1st md, then md_t_pre is 0
      md_t_pre := 0.U
    }
  }.elsewhen(state === State.sEntry && mdcfg_m_en_d){
    md_t_pre := io.mdcfg.j_indx // get pre md.t
  }

  // read entry
  //initialize j_indx
  when(state === State.sEntry && !mdcfg_m_en_d) {
    j_indx := md_t_pre // j_indx = md.t_pre
  }.elsewhen(state === State.sPriority && io.entry.j_en === true.B) {
    j_indx := j_indx + 1.U // j_indx ++
  }
  when(state === State.sPriority && j_indx =/= md_t) {
    io.entry.j_indx := j_indx
    io.entry.j_en := true.B
  }.otherwise {
    io.entry.j_indx := 0.U
    io.entry.j_en := false.B
  }
  entry_j_en_d := io.entry.j_en
  j_indx_d := j_indx

  // priority hit
  when(state === State.sPriority && entry_j_en_d) {
    val entry_addr = Wire(UInt(IopmpParams.soc_addr_width.W))
    when(io.entry.attribute.a === 0x2.U) {
      entry_addr := Cat(io.entry.addr, 1.U(2.W)) // NA4 mode
    }.elsewhen(io.entry.attribute.a === 0x3.U) {
      entry_addr := Cat(io.entry.addr, 3.U(2.W)) // NAPOT mode
    }.otherwise {
      entry_addr := 0.U //unsupported other modes
    }
    val (addr_start, addr_end) = NAPOTDecoder(entry_addr) // NAPOT decoder
    when(io.entry.attribute.a === 0x2.U || io.entry.attribute.a === 0x3.U) { // NA4 or NAPOT mode
      // Compute the end address of the transaction
      val pa_end = req.pa + req.len - 1.U // address end
      when(pa_end < addr_start || req.pa > addr_end){ // Not Match Any
        pri_hit := false.B
        pri_part_hit := false.B
      }.elsewhen(req.pa >= addr_start && pa_end <= addr_end) { // Full Match
        pri_hit := true.B
        pri_part_hit := false.B
      }.otherwise { // Partial Match
        pri_hit := false.B
        when(j_indx_d < io.reg.o_reg_hwcfg2_prio_entry) { 
          pri_part_hit := true.B
        }.otherwise {
          pri_part_hit := false.B
        }
      }      
    }.otherwise {
      pri_hit := false.B // unsupported other modes
      pri_part_hit := false.B
    }
  }.otherwise {
    pri_hit := false.B
    pri_part_hit := false.B
  }

  //hitting entry match
  when(pri_hit){
    entry_attribute := io.entry.attribute
  }
  when(!io.reg.o_reg_hwcfg0_enable) {
    cf_w := 0.U
    cf_r := 0.U    
  }.elsewhen(state === State.sMatching){ // get cf from entry[j]
    cf_w := !entry_attribute.w
    cf_r := !entry_attribute.r
  }.elsewhen(state === State.sErr) { // cause error, clear cf
    // warning: Possible logical redundancy
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
    eid := j_indx_d
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

/* 
  main module for IOPMP checker
*/
class IopmpCheckerIO extends Bundle {
    val regcfg = RegCfgIO()
    // check req
    val req = Flipped(new ReqIO)
    // check rsp
    val resp = new RspIO
    val int = Output(Bool()) // interrupt
    val flush = Output(Bool()) // flush (if have a fast table out of checker, need flush)
    val rs = Output(Bool()) // response suppression
    val enable = Output(Bool()) // IOPMP enable
}

trait IopmpCheckerBase extends Module {
  val io = IO(new IopmpCheckerIO())
}

class IopmpChecker extends Module with IopmpCheckerBase{

  // Declare modules
  val regcfg_mux = Module(new RegCfgMux)
  val srcmd_table = Module(new SrcmdTable)
  val mdcfg_table = Module(new MdcfgTable)
  val entry_table = Module(new EntryTable)
  val reg_map = Module(new RegMap)
  val ctrl = Module(new Ctrl)

  // Inter-module connections
  val stall = io.regcfg.v && io.regcfg.rw
  // registers
  regcfg_mux.io.regcfg <> io.regcfg
  srcmd_table.io.regcfg <> regcfg_mux.io.regcfg_srcmd
  mdcfg_table.io.regcfg <> regcfg_mux.io.regcfg_mdcfg
  entry_table.io.regcfg <> regcfg_mux.io.regcfg_entry
  reg_map.io.regcfg <> regcfg_mux.io.regcfg_regmap
  // ctrl
  ctrl.io.req <> io.req // check req
  ctrl.io.resp <> io.resp // check rsp
  ctrl.io.srcmd <> srcmd_table.io.bits // srcmdTable
  ctrl.io.mdcfg <> mdcfg_table.io.bits // mdcfgTable
  ctrl.io.entry <> entry_table.io.bits // entryTable
  ctrl.io.reg <> reg_map.io.bits // regMap
  ctrl.io.stall := stall // stall
  ctrl.io.int <> io.int // interrupt
  ctrl.io.flush <> io.flush // flush
  ctrl.io.rs <> io.rs // response suppression
  ctrl.io.enable <> io.enable // IOPMP enable
}