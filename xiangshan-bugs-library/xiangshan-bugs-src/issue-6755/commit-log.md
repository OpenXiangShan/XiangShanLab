# Commit Log
- Issue: #6755
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6755
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4991
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4991
- Changed files: 13
- Additions: 650
- Deletions: 66

## Files
- `.github/workflows/emu.yml`
- `src/main/scala/device/SYSCNT.scala`
- `src/main/scala/device/TIMER.scala`
- `src/main/scala/device/TimeAsync.scala`
- `src/main/scala/device/standalone/StandAloneDevice.scala`
- `src/main/scala/device/standalone/StandAloneSYSCNT.scala`
- `src/main/scala/device/standalone/standalone_device.mk`
- `src/main/scala/system/SoC.scala`
- `src/main/scala/top/Top.scala`
- `src/main/scala/top/XSNoCTop.scala`
- `src/main/scala/xiangshan/L2Top.scala`
- `src/main/scala/xiangshan/XSTileWrap.scala`
- `src/test/scala/top/SimTop.scala`

## Diff
```diff
diff --git a/.github/workflows/emu.yml b/.github/workflows/emu.yml
index b09f9510dd1..0a3ae9f9914 100644
--- a/.github/workflows/emu.yml
+++ b/.github/workflows/emu.yml
@@ -51,13 +51,13 @@ jobs:
           bash .github/workflows/check-usage.sh "BoringUtils" $GITHUB_WORKSPACE
       - name: generate standalone devices for TL
         run: |
-          make StandAloneCLINT DEVICE_BASE_ADDR=0x38000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=1 DEVICE_PREFIX=CLINT_
+          make StandAloneSYSCNT DEVICE_BASE_ADDR=0x38040000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=1 DEVICE_PREFIX=CLINT_
           make StandAloneDebugModule DEVICE_BASE_ADDR=0x38020000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=1 DEVICE_PREFIX=DM_
           make StandAlonePLIC DEVICE_BASE_ADDR=0x3C000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=1 DEVICE_PREFIX=PLIC_
           make clean
       - name: generate standalone devices for AXI4
         run: |
-          make StandAloneCLINT DEVICE_BASE_ADDR=0x38000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
+          make StandAloneSYSCNT DEVICE_BASE_ADDR=0x38040000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
           make StandAloneDebugModule DEVICE_BASE_ADDR=0x38020000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=DM_
           make StandAlonePLIC DEVICE_BASE_ADDR=0x3C000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=PLIC_
           make clean
@@ -465,7 +465,7 @@ jobs:
         run: python3 $GITHUB_WORKSPACE/scripts/xiangshan.py --clean
       - name: generate standalone devices for AXI4
         run: |
-          make StandAloneCLINT DEVICE_BASE_ADDR=0x38000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
+          make StandAloneSYSCNT DEVICE_BASE_ADDR=0x38040000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
           make StandAloneDebugModule DEVICE_BASE_ADDR=0x38020000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=DM_
           make StandAlonePLIC DEVICE_BASE_ADDR=0x3C000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=PLIC_
       - name: generate CHI Issue B XSNoCTop verilog with difftest and filelist
@@ -482,7 +482,7 @@ jobs:
         run: python3 $GITHUB_WORKSPACE/scripts/xiangshan.py --clean
       - name: generate standalone devices for AXI4
         run: |
-          make StandAloneCLINT DEVICE_BASE_ADDR=0x38000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
+          make StandAloneSYSCNT DEVICE_BASE_ADDR=0x38040000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
           make StandAloneDebugModule DEVICE_BASE_ADDR=0x38020000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=DM_
           make StandAlonePLIC DEVICE_BASE_ADDR=0x3C000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=PLIC_
       - name: generate CHI Issue E.b XSNoCTop verilog with difftest and filelist
@@ -499,7 +499,7 @@ jobs:
         run: python3 $GITHUB_WORKSPACE/scripts/xiangshan.py --clean
       - name: generate standalone devices for AXI4
         run: |
-          make StandAloneCLINT DEVICE_BASE_ADDR=0x38000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
+          make StandAloneSYSCNT DEVICE_BASE_ADDR=0x38040000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=CLINT_
           make StandAloneDebugModule DEVICE_BASE_ADDR=0x38020000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=DM_
           make StandAlonePLIC DEVICE_BASE_ADDR=0x3C000000 DEVICE_ADDR_WIDTH=32 DEVICE_DATA_WIDTH=64 DEVICE_TL=0 DEVICE_PREFIX=PLIC_
       - name: generate CHI Issue E.b lowpower XSNoCTop verilog with difftest and filelist
diff --git a/src/main/scala/device/SYSCNT.scala b/src/main/scala/device/SYSCNT.scala
new file mode 100644
index 00000000000..1dfccf98fc2
--- /dev/null
+++ b/src/main/scala/device/SYSCNT.scala
@@ -0,0 +1,228 @@
+/***************************************************************************************
+ * Copyright (c) 2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2025 Institute of Computing Technology, Chinese Academy of Sciences
+ *
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          http://license.coscl.org.cn/MulanPSL2
+ *
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ *
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package device
+
+import chisel3._
+import chisel3.util._
+import chisel3.util.ShiftRegister
+import chisel3.util.ValidIO
+import freechips.rocketchip.devices.debug.RWNotify
+import freechips.rocketchip.diplomacy._
+import freechips.rocketchip.interrupts._
+import freechips.rocketchip.regmapper._
+import freechips.rocketchip.regmapper.RegField
+import freechips.rocketchip.subsystem._
+import freechips.rocketchip.tilelink._
+import freechips.rocketchip.util._
+import org.chipsalliance.cde.config.Field
+import org.chipsalliance.cde.config.Parameters
+import scala.collection.immutable.Seq
+
+object SYSCNTConsts {
+
+  def timeOffset  = 0xbff8 // 0xbff8 base addr is 0x8000
+  def timefreq    = 0xc000 // 0xC000:0x8000+0x4000
+  def timefreqReq = timefreq + 0x0008
+  def timeswReq   = timefreq + 0x0010
+  def size        = 0x10000
+  def timeWidth   = 64
+}
+
+case class SYSCNTParams(baseAddress: BigInt = 0x02000000, intStages: Int = 0) {
+  def address = AddressSet(baseAddress, SYSCNTConsts.size - 1)
+}
+
+case object SYSCNTKey extends Field[Option[SYSCNTParams]](None)
+
+case class SYSCNTAttachParams(
+    slaveWhere: TLBusWrapperLocation = CBUS
+)
+
+case object SYSCNTAttachKey extends Field(SYSCNTAttachParams())
+
+class SYSCNT(params: SYSCNTParams, beatBytes: Int)(implicit p: Parameters) extends LazyModule {
+  import SYSCNTConsts._
+
+  // clint0 => at most 4095 devices
+  val device = new SimpleDevice("clint", Seq("riscv,clint0")) {
+    override val alwaysExtended = true
+  }
+
+  val node: TLRegisterNode = TLRegisterNode(
+    address = Seq(params.address),
+    device = device,
+    beatBytes = beatBytes
+  )
+
+  lazy val module = new Impl
+  class Impl extends LazyModuleImp(this) {
+
+    val rtc_clock = IO(Input(Clock()))
+    val rtc_reset = IO(Input(AsyncReset()))
+    val bus_clock = IO(Input(Clock()))
+    val bus_reset = IO(Input(AsyncReset()))
+    val io = IO(new Bundle {
+      val update_en    = Input(Bool())
+      val update_value = Input(UInt(timeWidth.W))
+      val stop_en      = Input(Bool())
+      val time         = Output(ValidIO(UInt(timeWidth.W)))
+    })
+    dontTouch(io)
+    // increasing time define working on rtc_clock
+    val time    = withClockAndReset(rtc_clock, rtc_reset)(RegInit(0.U(timeWidth.W)))
+    val time_sw = withClockAndReset(bus_clock, bus_reset)(RegInit(0.U(timeWidth.W))) // software config time register
+    val time_sw_req = withClockAndReset(bus_clock, bus_reset)(RegInit(false.B))
+    // register define working on bus clock
+    // freqidx bit[1:0] is incr width 0: 1GHz,1:500MHz,2:250MHz,3:125MHz,4:62.5MHz,..
+    val freqidx     = withClockAndReset(bus_clock, bus_reset)(RegInit(0.U(3.W)))
+    val freqidx_req = withClockAndReset(bus_clock, bus_reset)(RegInit(false.B))
+    // async process about soc signal
+    val update_sync     = withClockAndReset(rtc_clock, rtc_reset)(AsyncResetSynchronizerShiftReg(io.update_en, 3, 0))
+    val stop_sync       = withClockAndReset(rtc_clock, rtc_reset)(AsyncResetSynchronizerShiftReg(io.stop_en, 3, 0))
+    val freqidx_req_rtc = withClockAndReset(rtc_clock, rtc_reset)(AsyncResetSynchronizerShiftReg(freqidx_req, 3, 0))
+    val time_sw_req_rtc = withClockAndReset(rtc_clock, rtc_reset)(AsyncResetSynchronizerShiftReg(time_sw_req, 3, 0))
+    // generate freqidx_req_rtc's rising edge
+    val freqidx_req_rtc_1f = withClockAndReset(rtc_clock, rtc_reset)(RegNext(freqidx_req_rtc, init = false.B))
+    val inccfg_vld         = freqidx_req_rtc & (!freqidx_req_rtc_1f)
+    val time_req_rtc_1f    = withClockAndReset(rtc_clock, rtc_reset)(RegNext(time_sw_req_rtc, init = false.B))
+    val time_req_rtc_ris   = time_sw_req_rtc & (!time_req_rtc_1f)
+    // async process from rtc clock to bus clock
+    val time_sw_update_bus =
+      withClockAndReset(bus_clock, bus_reset)(AsyncResetSynchronizerShiftReg(time_req_rtc_ris, 3, 0))
+    when(time_sw_update_bus)(time_sw_req := false.B)
+    // inc freq will be active after freqidx request is set from 0 to 1.
+    val incwidth = WireInit(0.U(3.W))
+    incwidth := withClockAndReset(rtc_clock, rtc_reset)(RegEnable(freqidx, inccfg_vld))
+    val inc_up_dis = withClockAndReset(rtc_clock, rtc_reset)(RegInit(false.B))
+    // generate the low bit: time_low= time[incwidth-1:0]
+    val time_low = Mux(incwidth === 0.U, 1.U, time & ((1.U << incwidth) - 1.U))
+
+    val inczero      = incwidth === 0.U
+    val timelow_zero = time_low === 0.U
+
+    val inc_update = inc_up_dis & (inczero | timelow_zero)
+    // count step will not update before count arrive at 2^n,n is increg
+    when(inccfg_vld) {
+      inc_up_dis := true.B
+    }.elsewhen(inc_update) {
+      inc_up_dis := false.B
+    }
+
+    // update the increasing step only when counter arrivals integer times of step config.
+    val incr_width_value = withClockAndReset(rtc_clock, rtc_reset)(RegInit(0.U(3.W)))
+    when(inc_update) {
+      incr_width_value := incwidth
+    }
+    // async process about inc_update
+    val inc_update_bus = withClockAndReset(bus_clock, bus_reset)(AsyncResetSynchronizerShiftReg(inc_update, 3, 0))
+    when(inc_update_bus) {
+      freqidx_req := false.B
+    } // freqidx update cfg is cleared auto by hardware.
+    val time_en      = withClockAndReset(rtc_clock, rtc_reset)(RegInit(false.B))
+    val incwidth_mux = Mux(inc_update, incwidth, incr_width_value)
+
+    when(stop_sync) {
+      time_en := false.B
+    }.otherwise {
+      time_en := true.B
+    }
+
+    when(time_req_rtc_ris) {
+      time := time_sw
+    }.elsewhen(stop_sync) {
+      time := time
+    }.elsewhen(update_sync) {
+      time := io.update_value
+    }.otherwise {
+      time := time + (1.U << incwidth_mux)
+    }
+    io.time.bits  := time
+    io.time.valid := time_en
+    // time working on rtc clock is to be synced with bus clock
+    val timeasync    = withClockAndReset(bus_clock, bus_reset)(Module(new TimeAsync()))
+    val time_rpt_bus = timeasync.io.o_time.bits
+
+    timeasync.io.i_time := io.time
+    /* 0000 msip hart 0
+     * 0004 msip hart 1
+     * 4000 mtimecmp hart 0 lo
+     * 4004 mtimecmp hart 0 hi
+     * 4008 mtimecmp hart 1 lo
+     * bffc mtime hi
+     */
+    // no use, only statement for syntax.
+    val time_rpt_rd    = WireInit(false.B)
+    val time_sw_vld    = WireInit(false.B)
+    val freqidx_rd     = WireInit(false.B)
+    val freqidx_wr     = WireInit(false.B)
+    val freqidx_req_rd = WireInit(false.B)
+    val freqidx_req_wr = WireInit(false.B)
+    val mtime_req_rd   = WireInit(false.B)
+    val mtime_req_wr   = WireInit(false.B)
+    //
+    node.regmap(
+      timeOffset -> RegFieldGroup(
+        "mtime",
+        Some("Timer Register"),
+        Seq(RWNotify(
+          64,
+          time_rpt_bus,
+          time_sw,
+          time_rpt_rd,
+          time_sw_vld,
+          Some(RegFieldDesc("timeswReq", "", reset = Some(0), volatile = true))
+        ))
+      ),
+      timefreq -> RegFieldGroup(
+        "freqidx",
+        Some("mtime frequency Register"),
+        Seq(RWNotify(
+          3,
+          freqidx,
+          freqidx,
+          freqidx_rd,
+          freqidx_wr,
+          Some(RegFieldDesc("freqidx", "", reset = Some(0), volatile = true))
+        ))
+      ),
+      timefreqReq -> RegFieldGroup(
+        "freqidxReq",
+        Some("mtime frequency update Request Register"),
+        Seq(RWNotify(
+          1,
+          freqidx_req,
+          freqidx_req,
+          freqidx_req_rd,
+          freqidx_req_wr,
+          Some(RegFieldDesc("freqidxReq", "", reset = Some(0), volatile = true))
+        ))
+      ),
+      timeswReq -> RegFieldGroup(
+        "mtimeReq",
+        Some("mtime software update Request Register"),
+        Seq(RWNotify(
+          1,
+          time_sw_req,
+          time_sw_req,
+          mtime_req_rd,
+          mtime_req_wr,
+          Some(RegFieldDesc("mtime_req", "", reset = Some(0), volatile = true))
+        ))
+      )
+    )
+  }
+}
diff --git a/src/main/scala/device/TIMER.scala b/src/main/scala/device/TIMER.scala
new file mode 100644
index 00000000000..ed9fad18090
--- /dev/null
+++ b/src/main/scala/device/TIMER.scala
@@ -0,0 +1,157 @@
+/***************************************************************************************
+ * Copyright (c) 2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2025 Institute of Computing Technology, Chinese Academy of Sciences
+ *
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          http://license.coscl.org.cn/MulanPSL2
+ *
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ *
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package device
+
+import chisel3._
+import chisel3.util._
+import chisel3.util.ShiftRegister
+import chisel3.util.ValidIO
+import freechips.rocketchip.diplomacy._
+import freechips.rocketchip.interrupts._
+import freechips.rocketchip.regmapper._
+import freechips.rocketchip.subsystem._
+import freechips.rocketchip.tile.MaxHartIdBits
+import freechips.rocketchip.tilelink._
+import freechips.rocketchip.util._
+import org.chipsalliance.cde.config.Field
+import org.chipsalliance.cde.config.Parameters
+
+object TIMERConsts {
+  def msipOffset(hart:    Int) = hart * msipBytes
+  def timecmpOffset(hart: Int) = 0x4000 + hart * timecmpBytes
+  def timeOffset   = 0xbff8
+  def msipBytes    = 4
+  def timecmpBytes = 8
+  def size         = 0x10000
+  def timeWidth    = 64
+  def ipiWidth     = 32
+  def ints         = 2
+}
+
+case class TIMERParams(IsSelfTest: Boolean = false, baseAddress: BigInt = 0x02000000, intStages: Int = 0) {
+  def address = AddressSet(baseAddress, TIMERConsts.size - 1)
+}
+
+case object CLINTKey extends Field[Option[TIMERParams]](None)
+
+case class CLINTAttachParams(
+    slaveWhere: TLBusWrapperLocation = CBUS
+)
+
+case object CLINTAttachKey extends Field(CLINTAttachParams())
+
+class TIMER(params: TIMERParams, beatBytes: Int)(implicit p: Parameters) extends LazyModule {
+  import TIMERConsts._
+
+  // clint0 => at most 4095 devices
+  val device = new SimpleDevice("clint", Seq("riscv,clint0")) {
+    override val alwaysExtended = true
+  }
+
+  val node: TLRegisterNode = TLRegisterNode(
+    address = Seq(params.address),
+    device = device,
+    beatBytes = beatBytes
+  )
+
+  val intnode: IntNexusNode = IntNexusNode(
+    sourceFn = { _ => IntSourcePortParameters(Seq(IntSourceParameters(ints, Seq(Resource(device, "int"))))) },
+    sinkFn = { _ => IntSinkPortParameters(Seq(IntSinkParameters())) },
+    outputRequiresInput = false
+  )
+
+  lazy val module = new Impl
+  class Impl extends LazyModuleImp(this) {
+    require(intnode.edges.in.size == 0, "CLINT only produces interrupts; it does not accept them")
+
+    val io = IO(new Bundle {
+      val time   = Input(ValidIO(UInt(timeWidth.W)))
+      val hartId = Input(UInt(p(MaxHartIdBits).W))
+    })
+
+    val time = RegInit(0.U(timeWidth.W))
+    when(io.time.valid)(time := io.time.bits)
+
+//    val nTiles = intnode.out.size
+    val NumsHart = 1 << p(MaxHartIdBits)
+    val nTiles   = NumsHart // def the max hart satisfing kmhv2 spec
+    val timecmp  = Seq.fill(nTiles)(RegInit((BigInt(2).pow(timeWidth) - 1).asUInt(timeWidth.W)))
+    val ipi      = Seq.fill(nTiles)(RegInit(0.U(1.W)))
+
+    // only get interrupt from the current hart
+    val (intnode_out, _) = intnode.out.unzip
+
+    intnode_out.zipWithIndex.foreach { case (int, i) =>
+      if (params.IsSelfTest) {
+        int(0) := ShiftRegister(ipi(i)(0), params.intStages)                        // msip
+        int(1) := ShiftRegister(time.asUInt >= timecmp(i).asUInt, params.intStages) // mtip
+      } else {
+        int(0) := ShiftRegister(ipi(io.hartId)(0), params.intStages)                        // msip
+        int(1) := ShiftRegister(time.asUInt >= timecmp(io.hartId).asUInt, params.intStages) // mtip
+      }
+    }
+
+    // io.time.valid := RegNext(io.rtcTick)
+    // io.time.bits := time
+
+    /* 0000 msip hart 0
+     * 0004 msip hart 1
+     * 4000 mtimecmp hart 0 lo
+     * 4004 mtimecmp hart 0 hi
+     * 4008 mtimecmp hart 1 lo
+     * 400c mtimecmp hart 1 hi
+     * bff8 mtime lo
+     * bffc mtime hi
+     */
+    node.regmap(
+      0 -> RegFieldGroup(
+        "msip",
+        Some("MSIP Bits"),
+        ipi.zipWithIndex.flatMap { case (r, i) =>
+          RegField(1, r, RegFieldDesc(s"msip_$i", s"MSIP bit for Hart $i", reset = Some(0))) :: RegField(
+            ipiWidth - 1
+          ) :: Nil
+        }
+      ),
+      timecmpOffset(0) -> timecmp.zipWithIndex.flatMap { case (t, i) =>
+        RegFieldGroup(
+          s"mtimecmp_$i",
+          Some(s"MTIMECMP for hart $i"),
+          RegField.bytes(t, Some(RegFieldDesc(s"mtimecmp_$i", "", reset = None)))
+        )
+      },
+      timeOffset -> Seq(RegField.r(64, time, RegFieldDesc("mtime", "", volatile = true)))
+    )
+  }
+}
+
+/** Trait that will connect a CLINT to a subsystem */
+trait CanHavePeripheryCLINT { this: BaseSubsystem =>
+  val clintOpt = p(CLINTKey).map { params =>
+    val tlbus = locateTLBusWrapper(p(CLINTAttachKey).slaveWhere)
+    val clint = LazyModule(new TIMER(params, cbus.beatBytes))
+    clint.node := tlbus.coupleTo("clint")(TLFragmenter(tlbus) := _)
+
+    // Override the implicit clock and reset -- could instead include a clockNode in the clint, and make it a RawModuleImp?
+    InModuleBody {
+      clint.module.clock := tlbus.module.clock
+      clint.module.reset := tlbus.module.reset
+    }
+    clint
+
+  }
+}
diff --git a/src/main/scala/device/TimeAsync.scala b/src/main/scala/device/TimeAsync.scala
new file mode 100644
index 00000000000..ef447779f77
--- /dev/null
+++ b/src/main/scala/device/TimeAsync.scala
@@ -0,0 +1,51 @@
+/***************************************************************************************
+ * Copyright (c) 2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2025 Institute of Computing Technology, Chinese Academy of Sciences
+ *
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          http://license.coscl.org.cn/MulanPSL2
+ *
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ *
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package device
+
+import chisel3._
+import chisel3.Bundle
+import chisel3.Input
+import chisel3.Module
+import chisel3.Output
+import chisel3.UInt
+import chisel3.util.RegEnable
+import chisel3.util.ValidIO
+import freechips.rocketchip.util.AsyncResetSynchronizerShiftReg
+
+class TimeAsync extends Module { // work with destination clock
+  val io = IO(new Bundle {
+    val i_time = Input(ValidIO(UInt(64.W)))
+    val o_time = Output(ValidIO(UInt(64.W)))
+  })
+  // async for i_time vld
+  val time_vld      = AsyncResetSynchronizerShiftReg(io.i_time.valid, 3, 0)
+  val time_vld_1dly = RegNext(time_vld, false.B)
+  val time_vld_xor  = time_vld ^ time_vld_1dly
+  val time_vld_o    = RegNext(time_vld_xor, false.B)
+  val time_o        = RegEnable(io.i_time.bits, 0.U(64.W), time_vld_xor)
+  io.o_time.valid := time_vld_o
+  io.o_time.bits  := time_o
+}
+// code about timer vld gen : Q<=~D
+class TimeVldGen extends Module { // work with reference clock,sync with syscnt
+  val io = IO(new Bundle {
+    val i_time = Input(UInt(64.W))
+    val o_time = Output(ValidIO(UInt(64.W)))
+  })
+  io.o_time.bits  := io.i_time
+  io.o_time.valid := io.i_time(0)
+}
diff --git a/src/main/scala/device/standalone/StandAloneDevice.scala b/src/main/scala/device/standalone/StandAloneDevice.scala
index 88e049a0a3b..a3d67a8844e 100644
--- a/src/main/scala/device/standalone/StandAloneDevice.scala
+++ b/src/main/scala/device/standalone/StandAloneDevice.scala
@@ -205,8 +205,8 @@ object ArgParser {
     require(addrWidth >= 0, "addrWidth not specified correctly")
     require(dataWidth >= 0, "dataWidth not specified correctly")
     val device: StandAloneDevice = module match {
-      case "StandAloneCLINT" =>
-        DisableMonitors(p => LazyModule(new StandAloneCLINT(
+      case "StandAloneSYSCNT" =>
+        DisableMonitors(p => LazyModule(new StandAloneSYSCNT(
           useTL, baseAddress, addrWidth, dataWidth, p(XSTileKey).size
         )(p)))(p)
       case "StandAlonePLIC" =>
diff --git a/src/main/scala/device/standalone/StandAloneSYSCNT.scala b/src/main/scala/device/standalone/StandAloneSYSCNT.scala
new file mode 100644
index 00000000000..d4e1ed9e88d
--- /dev/null
+++ b/src/main/scala/device/standalone/StandAloneSYSCNT.scala
@@ -0,0 +1,70 @@
+/***************************************************************************************
+ * Copyright (c) 2025 Beijing Institute of Open Source Chip (BOSC)
+ * Copyright (c) 2025 Institute of Computing Technology, Chinese Academy of Sciences
+ *
+ * XiangShan is licensed under Mulan PSL v2.
+ * You can use this software according to the terms and conditions of the Mulan PSL v2.
+ * You may obtain a copy of Mulan PSL v2 at:
+ *          http://license.coscl.org.cn/MulanPSL2
+ *
+ * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
+ * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
+ * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
+ *
+ * See the Mulan PSL v2 for more details.
+ ***************************************************************************************/
+
+package device.standalone
+
+import chisel3._
+import chisel3.util._
+import device.SYSCNT
+import device.SYSCNTParams
+import freechips.rocketchip.devices.tilelink._
+import freechips.rocketchip.diplomacy._
+import freechips.rocketchip.interrupts._
+import org.chipsalliance.cde.config.Parameters
+import utility.IntBuffer
+
+class StandAloneSYSCNT(
+    useTL:       Boolean = false,
+    baseAddress: BigInt,
+    addrWidth:   Int,
+    dataWidth:   Int = 64,
+    hartNum:     Int
+)(implicit p: Parameters) extends StandAloneDevice(
+      useTL,
+      baseAddress,
+      addrWidth,
+      dataWidth,
+      hartNum
+    ) {
+
+  private def clintParam = SYSCNTParams(baseAddress)
+  def addressSet: AddressSet = clintParam.address
+
+  private val clint = LazyModule(new SYSCNT(clintParam, dataWidth / 8))
+  clint.node := xbar
+
+  class StandAloneSYSCNTImp(outer: StandAloneSYSCNT)(implicit p: Parameters) extends StandAloneDeviceImp(outer) {
+    val rtc_clock = IO(Input(Clock()))
+    val rtc_reset = IO(Input(AsyncReset()))
+    val io = IO(new Bundle {
+      val update_en    = Input(Bool())
+      val update_value = Input(UInt(64.W))
+      val stop_en      = Input(Bool())
+      val time         = Output(ValidIO(UInt(64.W)))
+    })
+    outer.clint.module.rtc_clock       := rtc_clock
+    outer.clint.module.rtc_reset       := rtc_reset
+    outer.clint.module.bus_clock       := clock
+    outer.clint.module.bus_reset       := reset
+    outer.clint.module.io.stop_en      := io.stop_en
+    outer.clint.module.io.update_en    := io.update_en
+    outer.clint.module.io.update_value := io.update_value
+    io.time                            := outer.clint.module.io.time
+  }
+
+  override lazy val module = new StandAloneSYSCNTImp(this)
+
+}
diff --git a/src/main/scala/device/standalone/standalone_device.mk b/src/main/scala/device/standalone/standalone_device.mk
index 070ecdca4ae..e8dfee83566 100644
--- a/src/main/scala/device/standalone/standalone_device.mk
+++ b/src/main/scala/device/standalone/standalone_device.mk
@@ -4,7 +4,7 @@ DEBUG_ARGS += --xstop-prefix $(DEVICE_PREFIX)
 PLDM_ARGS += --xstop-prefix $(DEVICE_PREFIX)
 endif
 
-STANDALONE_DEVICES = StandAloneCLINT StandAlonePLIC StandAloneDebugModule
+STANDALONE_DEVICES = StandAlonePLIC StandAloneDebugModule StandAloneSYSCNT
 STANDALONE_DEVICES_PATH = $(shell for module in $(STANDALONE_DEVICES); do echo $(RTL_DIR)/$$module/$(DEVICE_PREFIX)$$module.$(RTL_SUFFIX); done)
 
 ifeq ($(DEVICE_TL),1)
diff --git a/src/main/scala/system/SoC.scala b/src/main/scala/system/SoC.scala
index 63f77b5b1d8..4591287e4cd 100644
--- a/src/main/scala/system/SoC.scala
+++ b/src/main/scala/system/SoC.scala
@@ -19,7 +19,14 @@ package system
 import org.chipsalliance.cde.config.{Field, Parameters}
 import chisel3._
 import chisel3.util._
-import device.{DebugModule, TLPMA, TLPMAIO, AXI4MemEncrypt}
+import device.{AXI4MemEncrypt, DebugModule, SYSCNT, SYSCNTConsts, SYSCNTParams, TIMER, TIMERConsts, TIMERParams, TLPMA, TLPMAIO}
+
+
+import huancun._
+import utility.{ReqSourceKey, TLClientsMerger, TLEdgeBuffer, TLLogger}
+import coupledL2.{EnableCHI, L2Param}
+import coupledL2.tl2chi.CHIIssue
+import openLLC.OpenLLCParam
 import freechips.rocketchip.amba.axi4._
 import freechips.rocketchip.devices.debug.DebugModuleKey
 import freechips.rocketchip.devices.tilelink._
@@ -27,15 +34,11 @@ import freechips.rocketchip.diplomacy.{AddressSet, IdRange, InModuleBody, LazyMo
 import freechips.rocketchip.interrupts.{IntSourceNode, IntSourcePortSimple}
 import freechips.rocketchip.regmapper.{RegField, RegFieldDesc, RegFieldGroup}
 import freechips.rocketchip.tilelink._
-import freechips.rocketchip.util.AsyncQueueParams
-import huancun._
+import freechips.rocketchip.util.{AsyncQueueParams}
 import top.BusPerfMonitor
-import utility.{ReqSourceKey, TLClientsMerger, TLEdgeBuffer, TLLogger}
 import xiangshan.backend.fu.{MemoryRange, PMAConfigEntry, PMAConst}
 import xiangshan.{DebugOptionsKey, PMParameKey, XSTileKey}
-import coupledL2.{EnableCHI, L2Param}
-import coupledL2.tl2chi.CHIIssue
-import openLLC.OpenLLCParam
+import device.SYSCNTConsts.timeWidth
 
 case object SoCParamsKey extends Field[SoCParameters]
 case object CVMParamsKey extends Field[CVMParameters]
@@ -70,7 +73,8 @@ case class SoCParameters
     PMAConfigEntry(0x10000000L, a = 1, w = true, r = true),
     PMAConfigEntry(0)
   ),
-  CLINTRange: AddressSet = AddressSet(0x38000000L, CLINTConsts.size - 1),
+  TIMERRange: AddressSet = AddressSet(0x38000000L, TIMERConsts.size - 1),
+  SYSCNTRange: AddressSet = AddressSet(0x38040000L, SYSCNTConsts.size - 1),
   BEURange: AddressSet = AddressSet(0x38010000L, 0xfff),
   PLICRange: AddressSet = AddressSet(0x3c000000L, PLICConsts.size(PLICConsts.maxMaxHarts) - 1),
   PLLRange: AddressSet = AddressSet(0x3a000000L, 0xfff),
@@ -112,7 +116,7 @@ case class SoCParameters
     HasTEEIMSIC = false
   ),
   EnableCHIAsyncBridge: Option[AsyncQueueParams] = Some(AsyncQueueParams(depth = 16, sync = 3, safe = false)),
-  EnableClintAsyncBridge: Option[AsyncQueueParams] = Some(AsyncQueueParams(depth = 1, sync = 3, safe = false)),
+  EnableClintAsyncBridge: Option[AsyncQueueParams] = Some(AsyncQueueParams(depth = 8, sync = 3, safe = false)),
   SeperateTLAsyncBridge: Option[AsyncQueueParams] = Some(AsyncQueueParams(depth = 1, sync = 3, safe = false)),
   WFIClockGate: Boolean = false,
   EnablePowerDown: Boolean = false
@@ -198,7 +202,8 @@ trait HasPeripheralRanges {
   private def mmpma = pmParams.mmpma
 
   def onChipPeripheralRanges: Map[String, AddressSet] = Map(
-    "CLINT" -> soc.CLINTRange,
+    "TIMER" -> soc.TIMERRange,
+    "SYSCNT" -> soc.SYSCNTRange,
     "BEU"   -> soc.BEURange,
     "PLIC"  -> soc.PLICRange,
     "PLL"   -> soc.PLLRange,
@@ -466,9 +471,15 @@ class MemMisc()(implicit p: Parameters) extends BaseSoC
     }
   }
 
-  val clint = LazyModule(new CLINT(CLINTParams(soc.CLINTRange.base), 8))
-  if (enableCHI) { clint.node := device_xbar.get }
-  else { clint.node := peripheralXbar.get }
+  // instant syscnt
+  val syscnt = (LazyModule(new SYSCNT(SYSCNTParams(soc.SYSCNTRange.base), 8)))
+  //  val clint = LazyModule(new CLINT(CLINTParams(soc.CLINTRange.base), 8))
+
+  if (enableCHI) { syscnt.node := device_xbar.get }
+  else { syscnt.node := peripheralXbar.get }
+
+//  if (enableCHI) { clint.node := device_xbar.get }
+//  else { clint.node := peripheralXbar.get }
 
   class IntSourceNodeToModule(val num: Int)(implicit p: Parameters) extends LazyModule {
     val sourceNode = IntSourceNode(IntSourcePortSimple(num, ports = 1, sources = 1))
@@ -495,26 +506,38 @@ class MemMisc()(implicit p: Parameters) extends BaseSoC
   if (enableCHI) { pll_node := device_xbar.get }
   else { pll_node := peripheralXbar.get }
 
+  // instance timer
+  val timer = LazyModule(new TIMER(TIMERParams(IsSelfTest = true, soc.TIMERRange.base), 8))
   val debugModule = LazyModule(new DebugModule(NumCores)(p))
-  val debugModuleXbarOpt = Option.when(SeperateDM)(TLXbar())
+  val SepTLXbarOpt = Option.when(SeperateTLBus)(TLXbar())
   if (enableCHI) {
     if (SeperateDM) {
-      debugModule.debug.node := debugModuleXbarOpt.get
+      debugModule.debug.node := SepTLXbarOpt.get
     } else {
       debugModule.debug.node := device_xbar.get
     }
     debugModule.debug.dmInner.dmInner.sb2tlOpt.foreach { sb2tl =>
       error_xbar.get := sb2tl.node
     }
+    if(SeperateTLBus){
+      timer.node := SepTLXbarOpt.get
+    } else{
+      timer.node := device_xbar.get
+    }
   } else {
     if (SeperateDM) {
-      debugModule.debug.node := debugModuleXbarOpt.get
+      debugModule.debug.node := SepTLXbarOpt.get
     } else {
       debugModule.debug.node := peripheralXbar.get
     }
     debugModule.debug.dmInner.dmInner.sb2tlOpt.foreach { sb2tl  =>
       l3_xbar.get := TLBuffer() := TLWidthWidget(1) := sb2tl.node
     }
+    if(SeperateTLBus){
+      timer.node := SepTLXbarOpt.get
+    } else{
+      timer.node := peripheralXbar.get
+    }
   }
 
   val pma = LazyModule(new TLPMA)
@@ -534,12 +557,19 @@ class MemMisc()(implicit p: Parameters) extends BaseSoC
 
     val debug_module_io = IO(new debugModule.DebugModuleIO)
     val ext_intrs = IO(Input(UInt(NrExtIntr.W)))
-    val rtc_clock = IO(Input(Bool()))
+    val rtc_clock = IO(Input(Clock()))
+    val rtc_reset = IO(Input(Reset()))
+    val bus_clock = IO(Input(Clock()))
+    val bus_reset = IO(Input(Reset()))
     val pll0_lock = IO(Input(Bool()))
     val pll0_ctrl = IO(Output(Vec(6, UInt(32.W))))
     val cacheable_check = IO(new TLPMAIO)
     val clintTime = IO(Output(ValidIO(UInt(64.W))))
-
+    val scntIO = IO(new Bundle {
+      val update_en = Input(Bool())
+      val update_value = Input(UInt(timeWidth.W))
+      val stop_en = Input(Bool())
+    })
     debugModule.module.io <> debug_module_io
 
     // sync external interrupts
@@ -558,14 +588,26 @@ class MemMisc()(implicit p: Parameters) extends BaseSoC
       axi4memencrpty.get.module.io.random_data := cnt(0).asBool
     }
     // positive edge sampling of the lower-speed rtc_clock
-    val rtcTick = RegInit(0.U(3.W))
-    rtcTick := Cat(rtcTick(1, 0), rtc_clock)
-    clint.module.io.rtcTick := rtcTick(1) && !rtcTick(2)
+//    val rtcTick = RegInit(0.U(3.W))
+//    rtcTick := Cat(rtcTick(1, 0), rtc_clock)
+//    clint.module.io.rtcTick := rtcTick(1) && !rtcTick(2)
 
     val pll_ctrl_regs = Seq.fill(6){ RegInit(0.U(32.W)) }
     val pll_lock = RegNext(next = pll0_lock, init = false.B)
 
-    clintTime := clint.module.io.time
+    // timer instance
+    clintTime :=   syscnt.module.io.time // syscnt ->timeasync
+    timer.module.io.time <> syscnt.module.io.time
+    timer.module.io.hartId := 0.U
+
+    // instance syscnt
+    syscnt.module.rtc_clock := rtc_clock
+    syscnt.module.rtc_reset := rtc_reset
+    syscnt.module.bus_clock := bus_clock
+    syscnt.module.bus_reset := bus_reset
+    syscnt.module.io.update_en := scntIO.update_en
+    syscnt.module.io.update_value := scntIO.update_value
+    syscnt.module.io.stop_en := scntIO.stop_en
 
     pll0_ctrl <> VecInit(pll_ctrl_regs)
 
diff --git a/src/main/scala/top/Top.scala b/src/main/scala/top/Top.scala
index 4faec91b7ae..0e9896e0cd7 100644
--- a/src/main/scala/top/Top.scala
+++ b/src/main/scala/top/Top.scala
@@ -162,7 +162,7 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc()
   val nmi = InModuleBody(nmiIntNode.makeIOs())
 
   for (i <- 0 until NumCores) {
-    core_with_l2(i).clint_int_node := misc.clint.intnode
+    core_with_l2(i).clint_int_node := misc.timer.intnode
     core_with_l2(i).plic_int_node :*= misc.plic.intnode
     core_with_l2(i).debug_int_node := misc.debugModule.debug.dmOuter.dmOuter.intnode
     core_with_l2(i).nmi_int_node := nmiIntNode
@@ -175,17 +175,17 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc()
       println(s"Connecting Core_${i}'s L1 pf source to L3!")
       recv := core_with_l2(i).core_l3_pf_port.get
     })
-    misc.debugModuleXbarOpt.foreach { debugModuleXbar =>
+    misc.SepTLXbarOpt.foreach { SepTLXbarOpt =>
       // SeperateTlBus can only be connected to DebugModule now in non-XSNoCTop environment
       println(s"SeparateDM: ${SeperateDM}")
-      println(s"misc.debugModuleXbarOpt: ${misc.debugModuleXbarOpt}")
+      println(s"misc.SepTLXbarOpt: ${misc.SepTLXbarOpt}")
       require(core_with_l2(i).sep_tl_opt.isDefined)
-      require(SeperateTLBusRanges.size == 1)
-      require(SeperateTLBusRanges.head == p(DebugModuleKey).get.address)
-      debugModuleXbar := core_with_l2(i).sep_tl_opt.get
+      require(SeperateTLBusRanges.size >= 1)
+      require(SeperateTLBusRanges.head.base <= p(DebugModuleKey).get.address.base)
+      require(SeperateTLBusRanges.head.base <= p(SoCParamsKey).TIMERRange.base)
+      SepTLXbarOpt := core_with_l2(i).sep_tl_opt.get
     }
   }
-
   l3cacheOpt.map(_.ctlnode.map(_ := misc.peripheralXbar.get))
   l3cacheOpt.map(_.intnode.map(int => {
     misc.plic.intnode := IntBuffer() := int
@@ -276,7 +276,7 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc()
         val version = Input(UInt(4.W))
       }
       val debug_reset = Output(Bool())
-      val rtc_clock = Input(Bool())
+      val rtc_clock = Input(Clock())
       val cacheable_check = new TLPMAIO()
       val riscv_halt = Output(Vec(NumCores, Bool()))
       val riscv_critical_error = Output(Vec(NumCores, Bool()))
@@ -322,13 +322,22 @@ class XSTop()(implicit p: Parameters) extends BaseXSSoc()
     dontTouch(io)
     dontTouch(memory)
     misc.module.ext_intrs := io.extIntrs
-    misc.module.rtc_clock := io.rtc_clock
     misc.module.pll0_lock := io.pll0_lock
     misc.module.cacheable_check <> io.cacheable_check
 
     io.pll0_ctrl <> misc.module.pll0_ctrl
 
     val msiInfo = WireInit(0.U.asTypeOf(ValidIO(UInt(soc.IMSICParams.MSI_INFO_WIDTH.W))))
+    // syscnt io input descrip
+    val ref_reset_sync = withClockAndReset(io.rtc_clock, io.reset) { ResetGen() }
+    misc.module.scntIO.update_en := false.B
+    misc.module.scntIO.update_value := 0.U
+    misc.module.scntIO.stop_en := false.B
+    misc.module.rtc_clock := io.rtc_clock // syscnt clock
+    misc.module.rtc_reset := ref_reset_sync.asAsyncReset
+    misc.module.bus_clock := io.clock
+    misc.module.bus_reset := io.reset
+
 
 
     for ((core, i) <- core_with_l2.zipWithIndex) {
diff --git a/src/main/scala/top/XSNoCTop.scala b/src/main/scala/top/XSNoCTop.scala
index 44dc3286a4b..0e6f37089dd 100644
--- a/src/main/scala/top/XSNoCTop.scala
+++ b/src/main/scala/top/XSNoCTop.scala
@@ -83,9 +83,12 @@ trait HasAsyncClockImp { this: BaseXSSocImp =>
   val noc_reset = socParams.EnableCHIAsyncBridge.map(_ => IO(Input(AsyncReset())))
   val soc_clock = IO(Input(Clock()))
   val soc_reset = IO(Input(AsyncReset()))
+  val clint_clock = IO(Input(Clock()))
+  val clint_reset = IO(Input(AsyncReset()))
 
   val noc_reset_sync = socParams.EnableCHIAsyncBridge.map(_ => withClockAndReset(noc_clock, noc_reset) { ResetGen(io.dft_reset) })
   val soc_reset_sync = withClockAndReset(soc_clock, soc_reset) { ResetGen(io.dft_reset) }
+  val clint_reset_sync = withClockAndReset(clint_clock, clint_reset) { ResetGen(io.dft_reset) }
 }
 
 trait HasCoreLowPowerImp[+L <: HasXSTile] { this: BaseXSSocImp with HasXSTileCHIImp[L] =>
@@ -133,11 +136,23 @@ trait HasCoreLowPowerImp[+L <: HasXSTile] { this: BaseXSSocImp with HasXSTileCHI
     val isNormal = lpState === sIDLE
     val wfiGateClock = withClockAndReset(clock, cpuReset_sync) {RegInit(false.B)}
     val flitpend = io_chi.rx.snp.flitpend | io_chi.rx.rsp.flitpend | io_chi.rx.dat.flitpend
+    val msip_mux = socParams.SeperateTLBus match {
+      case (true) =>
+        core_with_l2.timer.get.intnode.out.head._1(0)
+      case (false) =>
+        core_with_l2.clintIntNode.get.out.head._1(0)
+    }
+    val mtip_mux = socParams.SeperateTLBus match {
+      case (true) =>
+        core_with_l2.timer.get.intnode.out.head._1(1)
+      case (false) =>
+        core_with_l2.clintIntNode.get.out.head._1(1)
+    }
 
     if (socParams.WFIClockGate) {
       // Interrupt sources collect
-      val msip  = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(clint.head(0), 3, 0)}
-      val mtip  = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(clint.head(1), 3, 0)}
+      val msip  = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(msip_mux, 3, 0)}
+      val mtip  = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(mtip_mux, 3, 0)}
       val meip  = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(plic.head(0), 3, 0)}
       val seip  = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(plic.last(0), 3, 0)}
       val nmi_31 = withClockAndReset(clock, cpuReset_sync) {AsyncResetSynchronizerShiftReg(nmi.head(0), 3, 0)}
@@ -187,19 +202,18 @@ trait HasXSTile { this: BaseXSSoc =>
     case XSCoreParamsKey => tiles.head
     case PerfCounterOptionsKey => up(PerfCounterOptionsKey).copy(perfDBHartID = tiles.head.HartId)
   })))
-
   // interrupts
-  val clintIntNode = IntSourceNode(IntSourcePortSimple(1, 1, 2))
+  val clintIntNode = Option.when(!SeperateTLBus)(IntSourceNode(IntSourcePortSimple(1, 1, 2)))
   val debugIntNode = IntSourceNode(IntSourcePortSimple(1, 1, 1))
   val plicIntNode = IntSourceNode(IntSourcePortSimple(1, 2, 1))
   val nmiIntNode = IntSourceNode(IntSourcePortSimple(1, 1, (new NonmaskableInterruptIO).elements.size))
   val beuIntNode = IntSinkNode(IntSinkPortSimple(1, 1))
-  core_with_l2.clintIntNode := clintIntNode
+  core_with_l2.clintIntNode.map(_ := clintIntNode.get) //from soc
   core_with_l2.debugIntNode := debugIntNode
   core_with_l2.plicIntNode :*= plicIntNode
   core_with_l2.nmiIntNode := nmiIntNode
   beuIntNode := core_with_l2.beuIntNode
-  val clint = InModuleBody(clintIntNode.makeIOs())
+  val clint = InModuleBody(clintIntNode.map(_.makeIOs()))
   val debug = InModuleBody(debugIntNode.makeIOs())
   val plic = InModuleBody(plicIntNode.makeIOs())
   val nmi = InModuleBody(nmiIntNode.makeIOs())
@@ -261,9 +275,9 @@ trait HasXSTileCHIImp[+L <: HasXSTile] extends HasXSTileImp[L] {
   socParams.EnableCHIAsyncBridge match {
     case Some(param) =>
       withClockAndReset(noc_clock.get, noc_reset_sync.get) {
-        val sink = Module(new CHIAsyncBridgeSink(param))
-        sink.io.async <> core_with_l2.module.io.chi
-        io_chi <> sink.io.deq
+        val time_sink = Module(new CHIAsyncBridgeSink(param))
+        time_sink.io.async <> core_with_l2.module.io.chi
+        io_chi <> time_sink.io.deq
       }
     case None =>
       io_chi <> core_with_l2.module.io.chi
@@ -283,7 +297,7 @@ trait HasSeperatedTLBusOpt { this: BaseXSSoc with HasXSTile =>
   // The Manager Node is only used to make IO
   val tl = Option.when(SeperateTLBus)(TLManagerNode(Seq(
     TLSlavePortParameters.v1(
-      managers = SeperateTLBusRanges map { address =>
+      managers = SeperateTLBusRanges.filter(address => !address.overlaps(soc.TIMERRange)) map { address =>
         TLSlaveParameters.v1(
           address = Seq(address),
           regionType = RegionType.UNCACHED,
@@ -393,11 +407,11 @@ trait HasClintTimeImp[+L <: HasXSTile] { this: BaseXSSocImp with HasAsyncClockIm
 
     socParams.EnableClintAsyncBridge match {
       case Some(param) =>
-        withClockAndReset(soc_clock, soc_reset_sync) {
-          val source = Module(new AsyncQueueSource(UInt(64.W), param))
-          source.io.enq.valid := io_clintTime.valid
-          source.io.enq.bits := io_clintTime.bits
-          core_with_l2.module.io.clintTime <> source.io.async
+        withClockAndReset(clint_clock, clint_reset_sync) {
+          val time_source = Module(new AsyncQueueSource(UInt(64.W), param))
+          time_source.io.enq.valid := io_clintTime.valid
+          time_source.io.enq.bits := io_clintTime.bits
+          core_with_l2.module.io.clintTime <> time_source.io.async
         }
       case None =>
         core_with_l2.module.io.clintTime <> io_clintTime
diff --git a/src/main/scala/xiangshan/L2Top.scala b/src/main/scala/xiangshan/L2Top.scala
index c21503db31e..e8698cb153e 100644
--- a/src/main/scala/xiangshan/L2Top.scala
+++ b/src/main/scala/xiangshan/L2Top.scala
@@ -112,7 +112,8 @@ class L2TopInlined()(implicit p: Parameters) extends LazyModule
       case L2ParamKey => coreParams.L2CacheParamsOpt.get.copy(
         hartId = p(XSCoreParamsKey).HartId,
         FPGAPlatform = debugOpts.FPGAPlatform,
-        hasMbist = hasMbist
+        hasMbist = hasMbist,
+        EnablePrivateClint = SeperateTLBus
       )
       case EnableCHI => p(EnableCHI)
       case CHIIssue => p(CHIIssue)
diff --git a/src/main/scala/xiangshan/XSTileWrap.scala b/src/main/scala/xiangshan/XSTileWrap.scala
index ca7f96a55f0..47f9d9aa1d9 100644
--- a/src/main/scala/xiangshan/XSTileWrap.scala
+++ b/src/main/scala/xiangshan/XSTileWrap.scala
@@ -18,6 +18,7 @@ package xiangshan
 
 import chisel3._
 import chisel3.util._
+import device.{TIMER, TIMERParams, TimeAsync}
 import org.chipsalliance.cde.config._
 import freechips.rocketchip.diplomacy._
 import freechips.rocketchip.interrupts._
@@ -42,12 +43,15 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
   val tile = LazyModule(new XSTile())
 
   // interrupts sync
-  val clintIntNode = IntIdentityNode()
+  val SeperateTLsync = SeperateTLBus && (!EnableSeperateTLAsync)
+  val clintIntNode = Option.when(!SeperateTLBus)(IntIdentityNode()) // interrupt from CHI or (TL & async)
   val debugIntNode = IntIdentityNode()
   val plicIntNode = IntIdentityNode()
   val beuIntNode = IntIdentityNode()
   val nmiIntNode = IntIdentityNode()
-  tile.clint_int_node := IntBuffer(3, cdc = true) := clintIntNode
+  // instance clint timer
+  val timer = Option.when(SeperateTLBus)(LazyModule(new TIMER(TIMERParams(IsSelfTest=false,soc.TIMERRange.base), 8))) // TL & sync
+  tile.clint_int_node := IntBuffer(3, cdc = true) := clintIntNode.getOrElse(timer.get.intnode)
   tile.debug_int_node := IntBuffer(3, cdc = true) := debugIntNode
   tile.plic_int_node :*= IntBuffer(3, cdc = true) :*= plicIntNode
   tile.nmi_int_node := IntBuffer(3, cdc = true) := nmiIntNode
@@ -56,12 +60,15 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
   // seperate TL bus
   println(s"SeperateTLBus = $SeperateTLBus")
   println(s"EnableSeperateTLAsync = $EnableSeperateTLAsync")
+  val tlXbar = Option.when(SeperateTLBus)(TLXbar())
+  tlXbar.map(_ := tile.sep_tl_opt.get) // TLXbar node in connect with tile master
+  timer.map(_.node := tlXbar.get) // TLXbar node out connnect with timer mmio
   // asynchronous bridge source node
   val tlAsyncSourceOpt = Option.when(SeperateTLBus && EnableSeperateTLAsync)(LazyModule(new TLAsyncCrossingSource()))
-  tlAsyncSourceOpt.foreach(_.node := tile.sep_tl_opt.get)
+  tlAsyncSourceOpt.foreach(_.node := tlXbar.get)
   // synchronous source node
-  val tlSyncSourceOpt = Option.when(SeperateTLBus && !EnableSeperateTLAsync)(TLTempNode())
-  tlSyncSourceOpt.foreach(_ := tile.sep_tl_opt.get)
+  val tlSyncSourceOpt = Option.when(SeperateTLBus && SeperateTLsync)(TLTempNode())
+  tlSyncSourceOpt.foreach(_ := tlXbar.get)
 
   class XSTileWrapImp(wrapper: LazyModule) extends LazyRawModuleImp(wrapper) {
     val clock = IO(Input(Clock()))
@@ -109,6 +116,11 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
     childClock := clock
     childReset := reset_sync
 
+    timer.foreach { timerInst =>
+      timerInst.module.io.hartId := io.hartId
+      timerInst.module.io.time := tile.module.io.clintTime
+    }
+
     tile.module.io.hartId := io.hartId
     tile.module.io.msiInfo := io.msiInfo
     tile.module.io.reset_vector := io.reset_vector
@@ -134,11 +146,11 @@ class XSTileWrap()(implicit p: Parameters) extends LazyModule
     // CLINT Async Queue Sink
     EnableClintAsyncBridge match {
       case Some(param) =>
-        val sink = withClockAndReset(clock, soc_reset_sync)(Module(new AsyncQueueSink(UInt(64.W), param)))
-        sink.io.async <> io.clintTime
-        sink.io.deq.ready := true.B
-        tile.module.io.clintTime.valid := sink.io.deq.valid
-        tile.module.io.clintTime.bits := sink.io.deq.bits
+        val time_sink = withClockAndReset(clock, soc_reset_sync)(Module(new AsyncQueueSink(UInt(64.W), param)))
+        time_sink.io.async <> io.clintTime
+        time_sink.io.deq.ready := true.B
+        tile.module.io.clintTime.valid := time_sink.io.deq.valid
+        tile.module.io.clintTime.bits := time_sink.io.deq.bits
       case None =>
         tile.module.io.clintTime := io.clintTime
     }
diff --git a/src/test/scala/top/SimTop.scala b/src/test/scala/top/SimTop.scala
index 937eabdaa29..5ae049c98cd 100644
--- a/src/test/scala/top/SimTop.scala
+++ b/src/test/scala/top/SimTop.scala
@@ -78,7 +78,7 @@ class SimTop(implicit p: Parameters) extends Module {
   when (rtcCounter === 0.U) {
     rtcClock := ~rtcClock
   }
-  soc.io.rtc_clock := rtcClock
+  soc.io.rtc_clock := rtcClock.asClock
 
   val success = Wire(Bool())
   val jtag = Module(new SimJTAG(tickDelay = 3)(p))
```
