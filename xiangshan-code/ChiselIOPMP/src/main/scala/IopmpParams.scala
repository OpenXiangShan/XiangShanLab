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

/* 
In full mode:
  srcmd_fmt = 0, mdcfg_fmt = 0

  srcmd table num(rrid_num) <= 32
  mdcfg table num(md_num  ) <= 31
  entry table num(entry_num) has no limit

In rapid-k mode:
  srcmd_fmt = 0, mdcfg_fmt = 1

  srcmd table num(rrid_num) <= 32
  entry table num(entry_num) = k * (md_num), and k = md_entry_num + 1

In compact-k mode:
  srcmd_fmt = 1, mdcfg_fmt = 1

  srcmd table num(rrid_num) <= 32
  entry table num(entry_num) = k * (md_num = rrid_num), and k = md_entry_num + 1

 */

sealed trait IopmpMode
object IopmpMode {
  case object Full     extends IopmpMode
  case object RapidK   extends IopmpMode
  case object CompactK extends IopmpMode
}

object IopmpParams {
  // mode select
  val mode: IopmpMode = IopmpMode.RapidK

  // global settings for the IOPMP checker
  val vendor             = 0    // [R   ]
  val specver            = 8    // [R   ]
  val impid              = 1    // [R   ]
  val mdcfg_fmt          = if (mode == IopmpMode.Full) 0 else 1
                                // [R   ] mdcfgTable format
  val srcmd_fmt          = if (mode == IopmpMode.CompactK) 1 else 0
                                // [R   ] srcmdTable format
  val tor_en             = 0    // [R   ] TOR enabled
  val sps_en             = 0    // [R   ] secondary permission settings disabled
  val user_cfg_en        = 0    // [R   ] user customized attributes disabled
  val prient_prog        = 1    // [W1CS] if prio_entry programmable, reset to 1
  val rrid_transl_en     = 0    // [R   ] RRID translation enabled
  val rrid_transl_prog   = 0    // [W1CS] if rrid translation programmable, reset to (1 & rrid_transl_en)
  val chk_x              = 0    // [R   ] unsupport instruction fetch check
  val no_x               = 0    // [R   ] unsupport instruction fetch check
  val no_w               = 0    // [R   ] always fails write accesses considered as no rule matched
  val stall_en           = 0    // [R   ] stall feature is not imp
  val peis               = 1    // [R   ] PEIS enabled
  val pees               = 0    // [R   ] PEES enabled
  val mfr_en             = 0    // [R   ] MFR disabled
  val md_entry_num_base  = 7    // [WARL] MD entry number, 0 for mdcfg_fmt 0 
  val md_num_base        = 31   // [R   ] mdcfgTable number m，max=64
  val addrh_en           = 1    // [R   ] enable addrh
  val enable             = 0    // [W1SS] disable iopmpchecker default
  val rrid_num           = 32   // [R   ] Indicate the number of RRIDs
  val entry_num_base     = 512  // [R   ] Indicate the number of entries // in k mode, entry_num = k * md_num
  val prio_entry         = 0    // [WARL] Indicate the number of entries matched with priority
  val rrid_transl        = 0    // [WARL] The RRID tagged to outgoing transactions
  val msi_en             = 0    // [WARL] Indicates whether the IOPMP triggers interrupt by MSI or wired interrupt
  val stall_violation_en = 0    // [WARL] Indicates whether the IOPMP faults stalled transactions
  val msidata            = 0    // [WARL] The data to trigger MSI
  val msi_werr           = 0    // [R/W1C] It’s asserted when the write access to trigger an IOPMP-originated MSI has failed

  // srcmdTable parameters
	val srcmd_s = rrid_num // srcmdTable number s，max=65536
  val srcmd_addr_width = log2Ceil(srcmd_s) // srcmdTable address width
  // mdcfgTable parameters
  val md_num = if (srcmd_fmt == 1) rrid_num else md_num_base // srcmd fmt = 1, md_num = rrid_num
	val mdcfg_m = md_num // mdcfgTable number m，max=64
  val mdcfg_addr_width = log2Ceil(mdcfg_m) // mdcfgTable address width
  val md_entry_num  = if (mdcfg_fmt == 0) 0 else md_entry_num_base  //in k mode, k = md_entry_num + 1
  val k = md_entry_num + 1

  // entryTable parameters
  val entry_num = if (mdcfg_fmt == 0) entry_num_base else k * md_num
	val entry_j = entry_num // entryTable number j，max= +oo
  val entry_addr_width = log2Ceil(entry_j) // entryTable address width

  // priority parameters

  // error&int parameters

  // register addr offset parameters
  val reg_version_addr_offset         = 0x0000.U(16.W) // version
  val reg_implementation_addr_offset  = 0x0004.U(16.W) // implementation
  val reg_hwcfg0_addr_offset          = 0x0008.U(16.W) // hwcfg0
  val reg_hwcfg1_addr_offset          = 0x000C.U(16.W) // hwcfg1
  val reg_hwcfg2_addr_offset          = 0x0010.U(16.W) // hwcfg2
  val reg_entryoffset_addr_offset     = 0x0014.U(16.W) // entryoffset
  val reg_hwcfguser_addr_offset       = 0x002C.U(16.W) // hwcfguser
  val reg_mdstall_addr_offset         = 0x0030.U(16.W) // mdstall
  val reg_mdstallh_addr_offset        = 0x0034.U(16.W) // mdstallh
  val reg_rridscp_addr_offset         = 0x0038.U(16.W) // rridscp
  val reg_mdclk_addr_offset           = 0x0040.U(16.W) // mdclk
  val reg_mdclkh_addr_offset          = 0x0044.U(16.W) // mdclkh
  val reg_mdcfglck_addr_offset        = 0x0048.U(16.W) // mdcfglck
  val reg_entrylck_addr_offset        = 0x004C.U(16.W) // entrylck
  val reg_errcfg_addr_offset          = 0x0060.U(16.W) // errcfg
  val reg_errinfo_addr_offset         = 0x0064.U(16.W) // errinfo
  val reg_erreqaddr_addr_offset       = 0x0068.U(16.W) // erreqaddr
  val reg_erreqaddrh_addr_offset      = 0x006C.U(16.W) // erreqaddrh
  val reg_erreqid_addr_offset         = 0x0070.U(16.W) // erreqid
  val reg_errmfr_addr_offset          = 0x0074.U(16.W) // errmfr
  val reg_errmsiaddr_addr_offset      = 0x0078.U(16.W) // errmsiaddr
  val reg_errmsiaddrh_addr_offset     = 0x007C.U(16.W) // errmsiaddrh
  val reg_erruser_addr_offset         = 0x0080.U(16.W) // erruser
 
  // register configuration interface parameters
  val regcfg_addrBits = 32
  val regcfg_dataBits = 32

  // base addresses for various register tables
  val reg_base_addr   = 0x4010_0000 // reg base address
  val info_base_addr  = reg_base_addr + 0x0000_0000 // info reg base address
  val mdcfg_base_addr = reg_base_addr + 0x0000_0800 // mdcfgTable base address
  val srcmd_base_addr = reg_base_addr + 0x0000_1000 // srcmdTable base address
  val entry_base_addr = reg_base_addr + 0x0000_2000 // entryTable base address

  // max addresses for various register tables
  val info_max_addr  = info_base_addr + 0x0000_0080 // reg max address
  val mdcfg_max_addr = mdcfg_base_addr + (mdcfg_m *  4) // mdcfgTable max address
  val srcmd_max_addr = srcmd_base_addr + (srcmd_s * 32) // srcmdTable max address
  val entry_max_addr = entry_base_addr + (entry_j * 16) // entryTable max address

  // SoC address width
  val soc_addr_width = 48

  // IO Bridge parameters
  val axi_addrBits = soc_addr_width
  val axi_dataBits = 256
  val axi_beatByte = axi_dataBits/8
  val axi_idBits   = 16
  val axi_idNum    = 1 << axi_idBits
  val axi_outstanding = 8

  // other global parameters...
  // debugMode
  val debugMode = true
}
