### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

On `kunminghu-v3` with `DefaultConfig`, an `nmi_43` input that is raised and released again before the trap it requested reaches the CSR file is taken as an ordinary M-mode interrupt instead of an RNMI: `mcause` reads `0x800000000000002b` (interrupt 43) and `mstatus[15:0]` reads `0x1800` (MPP = M, MPIE = 0, MIE = 0). An M-mode trap entry copies MIE into MPIE, so MPIE = 0 shows that `mstatus.MIE` was 0 when the trap was taken: the program clears `mstatus.MIE` and `mie` before its first cell and runs in M-mode with `mnstatus.NMIE` = 1. The CSR file latches the NMI's interrupt number while the input is high, but routes the trap by the NMI flag in the cycle the trap arrives. In the run below, pulses of 21, 23 and 31 to 33 cycles took this path; pulses of 1 to 20 cycles took no trap, and pulses of 34 to 48 cycles, like an input held until the handler clears it, took an RNMI; from 21 to 33 cycles the three outcomes are mixed, as the table below shows.

#### Where it comes from

`nmip` takes the two NMI inputs every cycle, and the interrupt filter is told an NMI is pending only while `nmip` is set:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L388-L390
  val nmip = RegInit(new NonMaskableIRPendingBundle, (new NonMaskableIRPendingBundle).init)
  nmip.NMI_43 := nonMaskableIRP.NMI_43
  nmip.NMI_31 := nonMaskableIRP.NMI_31
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L417-L418
  intrMod.io.in.nmi := nmip.asUInt.orR
  intrMod.io.in.nmiVec := nmip.asUInt
```

The filter's `nmi` output is that flag registered once, and the filter's interrupt number is the NMI's while it is set:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L537-L541
  val nmiReg = RegNext(io.in.nmi, false.B)
  val nmiVecReg = RegNext(nmiVec, 0.U.asTypeOf(nmiVec))

  val normalIntrVecReg = mIRVec | hsIRVec | vsMapHostIRVec
  val intrVecReg = Mux(disableAllIntrReg, 0.U, Mux(nmiReg, nmiVecReg, normalIntrVecReg))
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L551-L554
  io.out.interruptVec.valid := intrVecReg.orR || debugIntrReg
  io.out.interruptVec.bits := intrVecReg
  io.out.debug := debugIntrReg
  io.out.nmi := nmiReg
```

The CSR file latches the interrupt number, `debug`, `irToHS` and `irToVS` whenever the filter presents a valid interrupt and keeps them until the next one, but reads `nmi` live; it requests the interrupt while the filter presents it:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L430-L435
  val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
  val debug = RegEnable(intrMod.io.out.debug, false.B, intrMod.io.out.interruptVec.valid)
  val nmi = intrMod.io.out.nmi
  val virtualInterruptIsHvictlInject = intrMod.io.out.virtualInterruptIsHvictlInject
  val irToHS = RegEnable(intrMod.io.out.irToHS, false.B, intrMod.io.out.interruptVec.valid)
  val irToVS = RegEnable(intrMod.io.out.irToVS, false.B, intrMod.io.out.interruptVec.valid)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1188
  io.status.interrupt := intrMod.io.out.interruptVec.valid
```

The ROB takes that request only when the instruction at its head can be interrupted, and the trap reaches the CSR file three cycles after the ROB takes it.

<details>
<summary>The ROB's lines: when it takes the interrupt, and the three cycles to the CSR file</summary>

The ROB registers the request and takes it only when the instruction at its head can be interrupted:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/rob/Rob.scala#L767-L768
  val intrBitSetReg = RegNext(io.csr.intrBitSet)
  val intrEnable = intrBitSetReg && !hasWaitForward && deqPtrEntry.interruptSafe && !deqHasFlushed
```

It raises its exception output one cycle after it takes the interrupt, and the CSR file receives the trap two cycles after that:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/rob/Rob.scala#L907
  val exceptionHappen = (state === s_idle) && deqPtrEntryValid && (intrEnable || deqHasException && (!deqIsVlsException || deqVlsCanCommit)) && !lastCycleFlush
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/rob/Rob.scala#L911
  io.exception.valid := RegNext(exceptionHappen)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/rob/Rob.scala#L932
  io.exception.bits.isInterrupt := RegEnable(intrEnable, exceptionHappen)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/CtrlBlock.scala#L449-L455
  // Be careful here:
  // T0: rob.io.flushOut, s0_robFlushRedirect
  // T1: s1_robFlushRedirect, rob.io.exception.valid
  // T2: csr.redirect.valid
  // T3: csr.exception.valid
  // T4: get csr.trapTarget from csr
  // T5: io.frontend.toFtq
```

</details>

The RNMI entry is taken only while `nmi` is set, and the M-mode entry only while it is clear:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L845-L846
  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
```

So the CSR file requests the interrupt (`io.status.interrupt`) while the filter presents the NMI, and the ROB takes it at an instruction it may interrupt. The trap reaches the CSR file three cycles after that and is routed by `nmi` in that cycle. If the input has dropped by then, `nmi` is clear: the RNMI entry is not taken, the M-mode entry is, and the cause is the latched `intrVec`, 43.

`nmi` in the trap's cycle is the input two cycles earlier (`nmip` registers the input and the filter registers `nmip`), and the trap arrives three cycles after the ROB takes the interrupt. So the RNMI entry is taken only if the input is still high in the cycle after the ROB takes the interrupt; in the trace below it fell one cycle before the ROB took the interrupt.

We rebuilt the same commit with tracing on and ran the program under "To Reproduce" again; it printed the same output as the run below, cell for cell. In its trace of the 31-cycle pulse, counted from the cycle `nonMaskableIRP.NMI_43` rose, the CSR file requested the interrupt from cycle 2, the input fell at cycle 31, the ROB took the interrupt at cycle 32, and the trap reached the CSR file at cycle 35, after `nmi` had fallen (cycle 33), so the M-mode entry was taken with `intrVec` = 43. `intrVec` is 43 in every row, so the table leaves it out: it already held 43 before the pulse, and it is loaded again in every cycle `io.status.interrupt` (the filter's `interruptVec.valid`) is 1, cycles 2 to 32, in all of which `nmi` is 1 and the filter presents the NMI's number. Rows are the cycles at which a column changes; "The signals as traced" below says which signal each column is.

| cycle | `nonMaskableIRP.NMI_43` | `nmip` | `nmi` | `io.status.interrupt` | ROB flush | ROB exception | trap at CSR | M entry | RNMI entry |
|---|---|---|---|---|---|---|---|---|---|
| -1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 31 | 0 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 32 | 0 | 0 | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| 33 | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 0 | 0 |
| 34 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 35 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 0 |
| 36 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

<details>
<summary>An earlier pulse in the traced window took an RNMI: the ROB took it at cycle 23 and its trap reached the CSR file at cycle 26, while <code>nmi</code> was still 1</summary>

| cycle | `nonMaskableIRP.NMI_43` | `nmip` | `nmi` | `io.status.interrupt` | ROB flush | ROB exception | trap at CSR | M entry | RNMI entry |
|---|---|---|---|---|---|---|---|---|---|
| -1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| 2 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 23 | 1 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 |
| 24 | 1 | 1 | 1 | 1 | 0 | 1 | 0 | 0 | 0 |
| 25 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 26 | 1 | 1 | 1 | 1 | 0 | 0 | 1 | 0 | 1 |
| 27 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| 28 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 |

At cycle 28 the request falls while `nmi` stays 1: the RNMI entry cleared `mnstatus.NMIE`, and the filter drops every interrupt while NMIE is clear, but not `nmi`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L536
  val disableAllIntrReg = RegNext(disableDebugIntr || !io.in.mnstatusNMIE, false.B)
```

</details>

<details>
<summary>The signals as traced</summary>

ROB flush is `rob.io.flushOut.valid`, ROB exception is `rob.io.exception.valid` as `CtrlBlock` passes it on, trap at CSR is `NewCSR`'s `io.fromRob.trap.valid`, and M entry and RNMI entry are the two trap events' `valid`. Each column's signal as the trace names it:

| column | signal in the trace |
|---|---|
| `nonMaskableIRP.NMI_43` | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.nonMaskableIRP_NMI_43` |
| `nmip` | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.nmip_NMI_43` |
| `nmi` | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.intrMod.io_out_nmi` |
| `io.status.interrupt` | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.io_status_interrupt` |
| ROB flush | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.rob_io_flushOut_valid_probe` |
| ROB exception | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.io_robio_exception_valid` |
| trap at CSR | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.io_fromRob_trap_valid` |
| M entry | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.trapEntryMEvent.valid` |
| RNMI entry | `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFenceLink.csr.csrMod.trapEntryMNEvent.valid` |

</details>

<details>
<summary>How the trace was made: the same commit, patch and build command with <code>EMU_TRACE=vcd</code> and this file, without its comment lines, appended to <code>SIM_VFLAGS</code>, run with <code>--dump-wave -b 66000 -e 72000</code></summary>

```text
`verilator_config
tracing_off -scope "*"
tracing_on -scope "*.l_simMMIO.intrGen" -levels 1
tracing_on -scope "*.csr.csrMod" -levels 1
tracing_on -scope "*.csr.csrMod.intrMod" -levels 1
tracing_on -scope "*.csr.csrMod.trapEntryMEvent" -levels 1
tracing_on -scope "*.csr.csrMod.trapEntryMNEvent" -levels 1
tracing_on -scope "*.inner_ctrlBlock" -levels 1
```

</details>

#### The run

The program under "To Reproduce" runs in M-mode with `mie` = 0, `mstatus.MIE` = 0 and `mnstatus.NMIE` = 1. In each cell it writes a pulse width to the NMI source, which raises `nmi_43` for that many cycles, and spins. The M-mode trap handler records `mcause` and `mstatus` and returns; the RNMI handler records `mncause` and `mnstatus`, clears the source, sets `mnstatus.NMIE` again and jumps to the next cell instead of returning with `mnret`, so the RNMI return path is not exercised. A control cell raises no NMI, and a second holds `nmi_43` until the RNMI handler clears it.

XiangShan's RNMI entry also jumps to `mtvec`, which the program sets in direct mode, so the program's one trap entry tells the two entries apart by `mnstatus.NMIE`, which an RNMI entry clears:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L57
  // nmi handle in MMode only and default handler is mtvec
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L92-L95
  private val xtvec = MuxCase(io.in.mtvec, Seq(
    traptoVS -> io.in.vstvec,
    trapToHS -> io.in.stvec
  ))
```

The program runs the first control, then widths 1 to 48 cycles, then the second control:

| `nmi_43` pulse (cycles) | the hart took | recorded |
|---|---|---|
| 1 to 20, 22, 24, 26, 27, 29 | no trap | nothing |
| 21, 23, 31 to 33 | an ordinary M-mode interrupt trap | `mcause` = `0x800000000000002b`, `mstatus[15:0]` = `0x1800` |
| 25, 28, 30, 34 to 48 | an RNMI | `mncause` = `0x800000000000002b`, `mnstatus[15:0]` = `0x1800` |
| control: no NMI | no trap | nothing |
| control: held until the RNMI handler clears it | an RNMI | `mncause` = `0x800000000000002b`, `mnstatus[15:0]` = `0x1800` |

In the five cells that took the ordinary trap, `mstatus.MPP` is M and MPIE is clear, and no cell took both entries or an exception. Every RNMI, the held control's included, recorded `mncause` = `0x800000000000002b`.

<details>
<summary>The program's output</summary>

```text
# nmi_sweep_uartlite.elf
P4G_BEGIN 5034464f4c390001
N 00 0 0 0
N 01 0 0 0
N 02 0 0 0
N 03 0 0 0
N 04 0 0 0
N 05 0 0 0
N 06 0 0 0
N 07 0 0 0
N 08 0 0 0
N 09 0 0 0
N 0a 0 0 0
N 0b 0 0 0
N 0c 0 0 0
N 0d 0 0 0
N 0e 0 0 0
N 0f 0 0 0
N 10 0 0 0
N 11 0 0 0
N 12 0 0 0
N 13 0 0 0
N 14 0 0 0
N 15 1 0 0 800000000000002b 1800
N 16 0 0 0
N 17 1 0 0 800000000000002b 1800
N 18 0 0 0
N 19 0 1 0 800000000000002b 1800
N 1a 0 0 0
N 1b 0 0 0
N 1c 0 1 0 800000000000002b 1800
N 1d 0 0 0
N 1e 0 1 0 800000000000002b 1800
N 1f 1 0 0 800000000000002b 1800
N 20 1 0 0 800000000000002b 1800
N 21 1 0 0 800000000000002b 1800
N 22 0 1 0 800000000000002b 1800
N 23 0 1 0 800000000000002b 1800
N 24 0 1 0 800000000000002b 1800
N 25 0 1 0 800000000000002b 1800
N 26 0 1 0 800000000000002b 1800
N 27 0 1 0 800000000000002b 1800
N 28 0 1 0 800000000000002b 1800
N 29 0 1 0 800000000000002b 1800
N 2a 0 1 0 800000000000002b 1800
N 2b 0 1 0 800000000000002b 1800
N 2c 0 1 0 800000000000002b 1800
N 2d 0 1 0 800000000000002b 1800
N 2e 0 1 0 800000000000002b 1800
N 2f 0 1 0 800000000000002b 1800
N 30 0 1 0 800000000000002b 1800
N 31 0 1 0 800000000000002b 1800
P4G_END 00
Core 0: HIT GOOD TRAP at pc = 0x8000159c
```

</details>

Which widths take the ordinary trap depends on when the pulse starts, not on the width alone. We ran the same program with each pulse starting 0 to 15 cycles after the write to the NMI source (bits 31:16 of the value written, as the patch under "To Reproduce" reads them) at widths 21, 25 and 31. Widths 21 and 25 each took all three outcomes as the delay moved, and width 31 took an RNMI at every delay, unlike in the run above (M: the ordinary trap, R: an RNMI, -: no trap):

| width \ delay | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 21 | M | M | - | M | R | - | R | M | R | M | R | R | R | R | R | R |
| 25 | R | - | M | M | M | M | R | R | R | R | R | R | R | R | R | R |
| 31 | R | R | R | R | R | R | R | R | R | R | R | R | R | R | R | R |

<details>
<summary>The delay sweep's program: its change to the program under "To Reproduce", and why width 31 differs</summary>

```diff
--- nmi_sweep_uartlite.S
+++ nmi_delay_uartlite.S
@@ -50,3 +50,3 @@
     jal ra, putchar
-    li a0, 0x5034464f4c390001
+    li a0, 0x5034464f4c3a0001
     li a1, 16
@@ -77,4 +77,4 @@
     jal ra, print_N
-    li s4, 1
-    li s5, 49
+    la s4, nd_table
+    la s5, nd_table + 384
     li s6, 1
@@ -85,4 +85,3 @@
     csrc mstatus, t0
-    li t2, 0x2000
-    or t1, s4, t2
+    ld t1, 0(s4)
     li t0, 0x4007001c
@@ -104,3 +103,3 @@
     jal ra, print_N
-    addi s4, s4, 1
+    addi s4, s4, 8
     addi s6, s6, 1
@@ -400 +399,51 @@
 .dword 0
+.balign 64
+nd_table:
+.dword 0x2015
+.dword 0x12015
+.dword 0x22015
+.dword 0x32015
+.dword 0x42015
+.dword 0x52015
+.dword 0x62015
+.dword 0x72015
+.dword 0x82015
+.dword 0x92015
+.dword 0xa2015
+.dword 0xb2015
+.dword 0xc2015
+.dword 0xd2015
+.dword 0xe2015
+.dword 0xf2015
+.dword 0x2019
+.dword 0x12019
+.dword 0x22019
+.dword 0x32019
+.dword 0x42019
+.dword 0x52019
+.dword 0x62019
+.dword 0x72019
+.dword 0x82019
+.dword 0x92019
+.dword 0xa2019
+.dword 0xb2019
+.dword 0xc2019
+.dword 0xd2019
+.dword 0xe2019
+.dword 0xf2019
+.dword 0x201f
+.dword 0x1201f
+.dword 0x2201f
+.dword 0x3201f
+.dword 0x4201f
+.dword 0x5201f
+.dword 0x6201f
+.dword 0x7201f
+.dword 0x8201f
+.dword 0x9201f
+.dword 0xa201f
+.dword 0xb201f
+.dword 0xc201f
+.dword 0xd201f
+.dword 0xe201f
+.dword 0xf201f
```

Built and run as under "To Reproduce", with `nmi_delay_uartlite` in place of `nmi_sweep_uartlite`; the ELF we ran has sha256 `366e4793181dfbf83720745b935de0274540db9f4898d8d9a060fd56e3f8aef8`.

Width 31 took the ordinary trap in the run above and an RNMI at all 16 delays here. Its cells here follow width 25's rather than width 30's, and the loop differs: it loads each value from a table instead of forming it, which also moves the loop's code (its spin loop starts 4 bytes later). At the same width, the outcome followed where the pulse fell in the program's execution.

</details>

<details>
<summary>The delay sweep's output</summary>

Cell `00` is the first control, cells `01` to `10` are width 21, `11` to `20` are width 25 and `21` to `30` are width 31, each at delays 0 to 15 in order, and cell `31` is the held control.

```text
# nmi_delay_uartlite.elf
P4G_BEGIN 5034464f4c3a0001
N 00 0 0 0
N 01 1 0 0 800000000000002b 1800
N 02 1 0 0 800000000000002b 1800
N 03 0 0 0
N 04 1 0 0 800000000000002b 1800
N 05 0 1 0 800000000000002b 1800
N 06 0 0 0
N 07 0 1 0 800000000000002b 1800
N 08 1 0 0 800000000000002b 1800
N 09 0 1 0 800000000000002b 1800
N 0a 1 0 0 800000000000002b 1800
N 0b 0 1 0 800000000000002b 1800
N 0c 0 1 0 800000000000002b 1800
N 0d 0 1 0 800000000000002b 1800
N 0e 0 1 0 800000000000002b 1800
N 0f 0 1 0 800000000000002b 1800
N 10 0 1 0 800000000000002b 1800
N 11 0 1 0 800000000000002b 1800
N 12 0 0 0
N 13 1 0 0 800000000000002b 1800
N 14 1 0 0 800000000000002b 1800
N 15 1 0 0 800000000000002b 1800
N 16 1 0 0 800000000000002b 1800
N 17 0 1 0 800000000000002b 1800
N 18 0 1 0 800000000000002b 1800
N 19 0 1 0 800000000000002b 1800
N 1a 0 1 0 800000000000002b 1800
N 1b 0 1 0 800000000000002b 1800
N 1c 0 1 0 800000000000002b 1800
N 1d 0 1 0 800000000000002b 1800
N 1e 0 1 0 800000000000002b 1800
N 1f 0 1 0 800000000000002b 1800
N 20 0 1 0 800000000000002b 1800
N 21 0 1 0 800000000000002b 1800
N 22 0 1 0 800000000000002b 1800
N 23 0 1 0 800000000000002b 1800
N 24 0 1 0 800000000000002b 1800
N 25 0 1 0 800000000000002b 1800
N 26 0 1 0 800000000000002b 1800
N 27 0 1 0 800000000000002b 1800
N 28 0 1 0 800000000000002b 1800
N 29 0 1 0 800000000000002b 1800
N 2a 0 1 0 800000000000002b 1800
N 2b 0 1 0 800000000000002b 1800
N 2c 0 1 0 800000000000002b 1800
N 2d 0 1 0 800000000000002b 1800
N 2e 0 1 0 800000000000002b 1800
N 2f 0 1 0 800000000000002b 1800
N 30 0 1 0 800000000000002b 1800
N 31 0 1 0 800000000000002b 1800
P4G_END 00
Core 0: HIT GOOD TRAP at pc = 0x800015a0
```

</details>

### Expected behavior

The NMI inputs are not maskable, and `mstatus.MIE` does not disable them:

> The `rnmi` interrupt signals are inputs to the hart. These interrupts have higher priority than any other interrupt or exception on the hart and cannot be disabled by software. Specifically, they are not disabled by clearing the `mstatus`.MIE register.
>
> — [smrnmi.adoc, lines 18-21](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/smrnmi.adoc#L18-L21)

In M-mode with `mstatus.MIE` clear, interrupts are globally disabled:

> When a hart is executing in privilege mode *x*, interrupts are globally enabled when *x*IE=1 and globally disabled when *x*IE=0.
>
> — [machine.adoc, lines 363-364](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/machine.adoc#L363-L364)

and an interrupt traps to M-mode only when its bit is set in both `mip` and `mie`:

> An interrupt *i* will trap to M-mode (causing the privilege mode to change to M-mode) if all of the following are true: (a) either the current privilege mode is M and the MIE bit in the `mstatus` register is set, or the current privilege mode has less privilege than M-mode; (b) bit *i* is set in both `mip` and `mie`; and (c) if register `mideleg` exists, bit *i* is not set in `mideleg`.
>
> — [machine.adoc, lines 1343-1348](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/machine.adoc#L1343-L1348)

A trap into M-mode saves `mstatus.MIE` in MPIE, which is how the recorded `mstatus` shows that MIE was 0 when the trap was taken:

> When a trap is taken from privilege mode *y* into privilege mode *x*, *x*PIE is set to the value of *x*IE; *x*IE is set to 0; and *x*PP is set to *y*.
>
> — [machine.adoc, lines 391-393](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/machine.adoc#L391-L393)

This report does not say that every `rnmi` pulse must be taken; shorter pulses in the run are not taken at all. When one is taken, it may not be taken as an ordinary M-mode interrupt: in M-mode with `mstatus.MIE` = 0, condition (a) above fails for every interrupt, and with `mie` = 0 so does (b). The RNMI entry is the only way an `rnmi` input reaches a handler, and it writes `mnepc`, `mncause` and `mnstatus`, not `mepc`, `mcause` and `mstatus`.

### Environment

- Hardware
  - Memory (GB): 256, with 16 cores, for the build.
  - Memory (GB): 32, with 6 cores, for the run, a later job on the same emulator binary.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container the emulator ran in.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17.
- Repo
  - XiangShan commit id: [`6705b63685d301f8c0960f7cbcc8b964660dc58e`](https://github.com/OpenXiangShan/XiangShan/commit/6705b63685d301f8c0960f7cbcc8b964660dc58e), with the patch under "To Reproduce" applied; the emulator prints `Core  0's Commit SHA is: 6705b63685, dirty: 1`. The `difftest` submodule is at [`99f77ff455766d18cd05927b144641bd9744b19a`](https://github.com/OpenXiangShan/difftest/commit/99f77ff455766d18cd05927b144641bd9744b19a).
  - NEMU commit id (if difftest failed with NEMU): not used; the run shown is without DiffTest.
- Build & Run
  - Build command: see "To Reproduce". The emulator was built with `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
  - Run command (if applicable): see "To Reproduce".

### To Reproduce

The bug needs an NMI input that is raised and released before the trap it requests is taken; in this run that happened at five pulse widths, 21, 23 and 31 to 33 cycles. The simulation top ties the NMI inputs low, so the first step gives the simulation interrupt generator a small NMI source. Only the simulation's `SimMMIO` instantiates that generator, so the core is unchanged.

1. Apply this patch to the commit under "Environment":

<details>
<summary><code>AXI4IntrGenerator.scala</code>, <code>SimMMIO.scala</code> and <code>SimTop.scala</code>: an NMI source for the simulation top</summary>

```diff
diff --git a/src/main/scala/device/AXI4IntrGenerator.scala b/src/main/scala/device/AXI4IntrGenerator.scala
--- a/src/main/scala/device/AXI4IntrGenerator.scala
+++ b/src/main/scala/device/AXI4IntrGenerator.scala
@@ -26,6 +26,8 @@
 // we support 256 interrupt bits by default
 class IntrGenIO extends Bundle {
   val intrVec = Output(UInt(64.W))
+  // simulation-only NMI source (offset 0x1c); SimTop drives hart 0's NMI inputs from it
+  val nmi = Output(UInt(2.W))
 }
 
 class AXI4IntrGenerator
@@ -90,5 +92,38 @@
     }
 
     in.r.bits.data := intrReg(raddr)
+
+    // NMI source at offset 0x1c, simulation only. A 32-bit write of a nonzero value arms one
+    // pulse: bits 11:0 the width in cycles (0 holds the lines until a zero write), bits 13:12 the lines
+    // (bit 12 nmi_31, bit 13 nmi_43), bits 31:16 the delay in cycles before the pulse's first cycle. A zero
+    // write clears the source at once. The 1000-cycle delay above does not apply to this register.
+    val nmiWrite  = in.w.fire && waddr(4, 2) === 7.U
+    val nmiArmed  = RegInit(false.B)
+    val nmiActive = RegInit(false.B)
+    val nmiHold   = RegInit(false.B)
+    val nmiDelay  = RegInit(0.U(16.W))
+    val nmiWidth  = RegInit(0.U(12.W))
+    val nmiLines  = RegInit(0.U(2.W))
+    when (nmiWrite) {
+      nmiArmed  := w_data =/= 0.U
+      nmiActive := false.B
+      nmiHold   := w_data(11, 0) === 0.U
+      nmiDelay  := w_data(31, 16)
+      nmiWidth  := w_data(11, 0)
+      nmiLines  := w_data(13, 12)
+    } .elsewhen (nmiArmed) {
+      when (nmiDelay === 0.U) {
+        nmiArmed  := false.B
+        nmiActive := true.B
+      } .otherwise {
+        nmiDelay := nmiDelay - 1.U
+      }
+    } .elsewhen (nmiActive && !nmiHold) {
+      when (nmiWidth === 1.U) {
+        nmiActive := false.B
+      }
+      nmiWidth := nmiWidth - 1.U
+    }
+    io.extra.get.nmi := Mux(nmiActive, nmiLines, 0.U)
   }
 }
diff --git a/src/test/scala/top/SimMMIO.scala b/src/test/scala/top/SimMMIO.scala
--- a/src/test/scala/top/SimMMIO.scala
+++ b/src/test/scala/top/SimMMIO.scala
@@ -191,6 +191,7 @@
     val uart16550Int = uartIntSink.makeIOs()
     private val uart16550IntNum = 0xa;
     io.interrupt.intrVec := intrGen.module.io.extra.get.intrVec | uart16550Int.elts.head.head << (uart16550IntNum - 1)
+    io.interrupt.nmi := intrGen.module.io.extra.get.nmi
 
     // TODO: rx not supported yet
     uart16550.module.io.tx.ready := true.B
diff --git a/src/test/scala/top/SimTop.scala b/src/test/scala/top/SimTop.scala
--- a/src/test/scala/top/SimTop.scala
+++ b/src/test/scala/top/SimTop.scala
@@ -141,7 +141,14 @@
   soc.io.pll0_lock := true.B
   soc.io.cacheable_check := DontCare
   soc.io.riscv_rst_vec.foreach(_ := 0x10000000L.U)
-  l_soc.nmi.foreach(_.foreach(intr => { intr := false.B; dontTouch(intr) }))
+  // NMI drive: hart 0's inputs (0 = nmi_31, 1 = nmi_43) follow the interrupt generator's NMI
+  // source; every other hart's inputs stay low.
+  l_soc.nmi.zipWithIndex.foreach { case (port, hart) =>
+    port.zipWithIndex.foreach { case (intr, line) =>
+      intr := (if (hart == 0) simMMIO.io.interrupt.nmi(line) else false.B)
+      dontTouch(intr)
+    }
+  }
   soc.io.traceCoreInterface.foreach(_.fromEncoder.enable := false.B)
   soc.io.traceCoreInterface.foreach(_.fromEncoder.stall  := false.B)
 
```

</details>

2. Build the emulator. This is the command we ran, with the cluster's absolute paths shortened:

```sh
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" FIRTOOL=/path/to/firtool -j16
```

firtool was version 1.149.0, the version the head's Chisel declares; the command passes it through the Makefile's `FIRTOOL` variable. The command also set `CXX`, `LINK` and `AR` to the GCC 15.2.0 toolchain named under "Environment", and `PGO_CFLAGS` and `PGO_LDFLAGS` to header and library directories for the native dependencies; those five are left out above. `OPT_FAST` is set because difftest's default at this commit, `-O3 -fno-slp-vectorize`, is a clang option that GCC rejects; `-fno-tree-slp-vectorize` is GCC's name for the same pass. `SIM_VFLAGS` splits Verilator's output more finely than difftest's default.

3. Assemble and link the program and linker script below, then run it:

```sh
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -c -o nmi_sweep_uartlite.o nmi_sweep_uartlite.S
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T nmi_sweep.ld -o nmi_sweep_uartlite.elf nmi_sweep_uartlite.o
```

The ELF we ran was built with `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0`; its sha256 is `ed44b76ea5a76a23618dbd2f60293d394497aed688d51ae98d9292047149ab90`.

```sh
./build/emu -i nmi_sweep_uartlite.elf --no-diff --seed 1 --max-cycles 4000000 --ram-size 256MB
```

The program prints one line per cell, `N <cell> <M-mode traps> <RNMIs> <exceptions>` followed by `mcause` and `mstatus[15:0]` when an M-mode trap was taken and by `mncause` and `mnstatus[15:0]` when an RNMI was, and ends on the program's last instruction with `HIT GOOD TRAP`, which means only that the program finished. Cell numbers are hexadecimal: cell `00` is the first control, cells `01` to `30` are widths 1 to 48, and cell `31` is the held control. Cells `15`, `17` and `1f` to `21` (widths 21, 23 and 31 to 33) print `1 0 0 800000000000002b 1800`. The whole output is in the collapsed block under "Describe the bug".

<details>
<summary><code>nmi_sweep_uartlite.S</code>, without its generator's header comment</summary>

```asm
.option norvc
.option norelax
.section .text.entry,"ax",@progbits
.globl _start
_start:
    j main
.section .text.main,"ax",@progbits
.globl main
main:
    la t0, trap_entry
    csrw mtvec, t0
    csrw medeleg, zero
    csrw mideleg, zero
    csrw mie, zero
    li t0, 8
    csrc mstatus, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    li t0, -1
    srli t0, t0, 10
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    csrsi 0x744, 8
    la gp, vars
    li a0, 80
    jal ra, putchar
    li a0, 52
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 66
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 73
    jal ra, putchar
    li a0, 78
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0x5034464f4c390001
    li a1, 16
    jal ra, puthex
    li a0, 10
    jal ra, putchar
    la s3, c0_end
    jal ra, reset_vars
    li t0, 0x1800
    csrc mstatus, t0
    li t0, 0x4007001c
    li t1, 0x8
    sw t1, 0(t0)
    li a2, 600
c0_spin:
    addi a2, a2, -1
    bnez a2, c0_spin
c0_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    li a4, 0
    jal ra, print_N
    li s4, 1
    li s5, 49
    li s6, 1
cell_loop:
    la s3, cell_end
    jal ra, reset_vars
    li t0, 0x1800
    csrc mstatus, t0
    li t2, 0x2000
    or t1, s4, t2
    li t0, 0x4007001c
    sw t1, 0(t0)
    li a2, 600
cell_spin:
    addi a2, a2, -1
    bnez a2, cell_spin
cell_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    mv a4, s6
    jal ra, print_N
    addi s4, s4, 1
    addi s6, s6, 1
    blt s4, s5, cell_loop
    la s3, ch_end
    jal ra, reset_vars
    li t0, 0x1800
    csrc mstatus, t0
    li t0, 0x4007001c
    li t1, 0x2000
    sw t1, 0(t0)
    li a2, 600
ch_spin:
    addi a2, a2, -1
    bnez a2, ch_spin
ch_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    li a4, 49
    jal ra, print_N
    li a0, 80
    jal ra, putchar
    li a0, 52
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 78
    jal ra, putchar
    li a0, 68
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0
    li a1, 2
    jal ra, puthex
    li a0, 10
    jal ra, putchar
    li a0, 0
    jal ra, finish
halt:
    j halt
print_N:
    mv s2, ra
    li a0, 78
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, a4
    li a1, 2
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 0(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 32(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 88(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
    jal ra, puthex
    ld t3, 0(gp)
    beqz t3, print_N_no_m
    li a0, 32
    jal ra, putchar
    ld a0, 8(gp)
    li a1, 16
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 16(gp)
    li a1, 4
    jal ra, puthex
print_N_no_m:
    ld t3, 32(gp)
    beqz t3, print_N_no_n
    li a0, 32
    jal ra, putchar
    ld a0, 40(gp)
    li a1, 16
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 56(gp)
    li a1, 4
    jal ra, puthex
print_N_no_n:
    ld t3, 88(gp)
    beqz t3, print_N_no_u
    li a0, 32
    jal ra, putchar
    ld a0, 96(gp)
    li a1, 16
    jal ra, puthex
print_N_no_u:
    li a0, 10
    jal ra, putchar
    mv ra, s2
    ret
ecall_stream:
    li a3, 8
ecall_loop:
.globl ecall_site
ecall_site:
    ecall
    addi a3, a3, -1
    bnez a3, ecall_loop
    ret
reset_vars:
    sd zero, 0(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 8(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 16(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 24(gp)
    sd zero, 32(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 40(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 48(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 56(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 64(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 72(gp)
    sd zero, 80(gp)
    sd zero, 88(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 96(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 104(gp)
    ret
quiesce:
    li t0, 0x4007001c
    sw zero, 0(t0)
    fence iorw, iorw
    lw t1, 0(t0)
    li t1, 64
quiesce_spin:
    addi t1, t1, -1
    bnez t1, quiesce_spin
    ret
puthex:
    mv s1, ra
    mv t4, a0
    slli t5, a1, 2
    addi t5, t5, -4
puthex_loop:
    srl a0, t4, t5
    andi a0, a0, 15
    li t3, 10
    bltu a0, t3, puthex_digit
    addi a0, a0, 87
    j puthex_put
puthex_digit:
    addi a0, a0, 48
puthex_put:
    jal ra, putchar
    addi t5, t5, -4
    bgez t5, puthex_loop
    mv ra, s1
    ret
putchar:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret
finish:
    .word 0x0000006b
    j finish
.balign 64
.globl trap_entry
trap_entry:
    csrr t6, 0x744
    andi t6, t6, 8
    beqz t6, rnmi_path
    j m_path
.globl m_path
m_path:
    csrrw sp, mscratch, sp
    sd t0, 0(sp)
    sd t1, 8(sp)
    sd t2, 16(sp)
    la t1, vars
    csrr t0, mcause
    bltz t0, m_interrupt
    li t2, 11
    bne t0, t2, m_unexpected
    ld t2, 80(t1)
    addi t2, t2, 1
    sd t2, 80(t1)
    j m_skip
m_unexpected:
    ld t2, 88(t1)
    bnez t2, m_unexpected_counted
    sd t0, 96(t1)
    csrr t0, mepc
    sd t0, 104(t1)
m_unexpected_counted:
    ld t2, 88(t1)
    addi t2, t2, 1
    sd t2, 88(t1)
m_skip:
    csrr t0, mepc
    addi t0, t0, 4
    csrw mepc, t0
    j m_return
m_interrupt:
    ld t2, 0(t1)
    bnez t2, m_interrupt_counted
    sd t0, 8(t1)
    csrr t0, mstatus
    sd t0, 16(t1)
    csrr t0, mepc
    sd t0, 24(t1)
m_interrupt_counted:
    ld t2, 0(t1)
    addi t2, t2, 1
    sd t2, 0(t1)
m_return:
    ld t0, 0(sp)
    ld t1, 8(sp)
    ld t2, 16(sp)
    csrrw sp, mscratch, sp
    mret
.globl rnmi_path
rnmi_path:
    csrrw sp, 0x740, sp
    la t1, vars
    ld t2, 32(t1)
    bnez t2, rnmi_counted
    csrr t0, 0x742
    sd t0, 40(t1)
    csrr t0, 0x741
    sd t0, 48(t1)
    csrr t0, 0x744
    sd t0, 56(t1)
    csrr t0, mstatus
    sd t0, 64(t1)
    csrr t0, mcause
    sd t0, 72(t1)
rnmi_counted:
    ld t2, 32(t1)
    addi t2, t2, 1
    sd t2, 32(t1)
    li t0, 0x4007001c
    sw zero, 0(t0)
    fence iorw, iorw
    lw t2, 0(t0)
    li t2, 64
rnmi_spin:
    addi t2, t2, -1
    bnez t2, rnmi_spin
    csrrw sp, 0x740, sp
    csrsi 0x744, 8
    jr s3
.section .data,"aw",@progbits
.balign 64
m_save:
.space 64
n_save:
.space 64
.globl vars
vars:
.space 112
.balign 64
nmi_source:
.dword 0
scratch:
.dword 0
```

</details>

<details>
<summary><code>nmi_sweep.ld</code></summary>

```ld
OUTPUT_ARCH(riscv)
ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text.entry : { *(.text.entry) }
    . = 0x80001000;
    .text : { *(.text.main) }
    . = 0x80100000;
    .data : { *(.data*) }
    . = 0x80200000;
    .pt : { *(.pt) }
    . = 0x80300000;
    .guest0 : { *(.guest0) }
    . = 0x80301000;
    .guest1 : { *(.guest1) }
    . = 0x80400000;
    .pages : { *(.pages) }
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.riscv.attributes) }
}
```

</details>

### Additional context

**When it started.** [#5437](https://github.com/OpenXiangShan/XiangShan/pull/5437) (`fix(nmi): nmip should be always enable`, merged as commit [`81890ca4f4`](https://github.com/OpenXiangShan/XiangShan/commit/81890ca4f497cf68508900353fac851e8a68cf58)) replaced the two lines that set `nmip` when an NMI input was high and the clearing of `nmip` when the RNMI was taken with the two assignments quoted above. We ran the program under "To Reproduce" on builds of [`81890ca4f4`](https://github.com/OpenXiangShan/XiangShan/commit/81890ca4f497cf68508900353fac851e8a68cf58) and its parent, `DefaultConfig` with the same NMI source: at the parent every pulse, from 1 to 48 cycles, took an RNMI, and at [`81890ca4f4`](https://github.com/OpenXiangShan/XiangShan/commit/81890ca4f497cf68508900353fac851e8a68cf58) pulses of 13 to 16 cycles took the ordinary trap. [#5437](https://github.com/OpenXiangShan/XiangShan/pull/5437) has no description, so we have not proposed reverting it.

**`kunminghu-v2`.** `kunminghu-v2` carries the same change as [`ac7a46aa4a`](https://github.com/OpenXiangShan/XiangShan/commit/ac7a46aa4a447af7cfc951b72b608c535f89d93b) (`fix(nmi): nmip should be always enable`): `nmip` follows the inputs and the two entries are routed by `nmi`. Its interrupt filter delays the NMI flag and the interrupt number by five more cycles. We did not build or run it.

<details>
<summary><code>kunminghu-v2</code>'s lines</summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/fe2288cb09361b65afa26be3f403520c4b496dbf/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L371-L373
  val nmip = RegInit(new NonMaskableIRPendingBundle, (new NonMaskableIRPendingBundle).init)
  nmip.NMI_43 := nonMaskableIRP.NMI_43
  nmip.NMI_31 := nonMaskableIRP.NMI_31
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/fe2288cb09361b65afa26be3f403520c4b496dbf/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L557-L559
  val delayedIntrVec = DelayN(intrVecReg, 5)
  val delayedDebugIntr = DelayN(debugIntrReg, 5)
  val delayedNMI = DelayN(nmiReg, 5)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/fe2288cb09361b65afa26be3f403520c4b496dbf/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L801-L802
  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
```

</details>

**A direction for a fix.** Latching the filter's NMI flag together with `intrVec`, `debug`, `irToHS` and `irToVS`, and routing an interrupt trap by that latched flag as well as by `nmi` (the change below), removed the ordinary trap from the program under "To Reproduce" and from the delay sweep's, built and run as there. An exception is still routed by `nmi` alone. We ran it without DiffTest only. Cells by outcome, without and with the change:

| program | the change | ordinary M-mode trap | RNMI | no trap |
|---|---|---|---|---|
| the program under "To Reproduce" (48 widths) | without | 5 | 18 | 25 |
| the program under "To Reproduce" (48 widths) | with | 0 | 23 | 25 |
| the delay sweep (48 cells) | without | 9 | 36 | 3 |
| the delay sweep (48 cells) | with | 0 | 43 | 5 |

The latched NMI flag is loaded on the same condition as `intrVec`, so the two always describe the same interrupt; neither is tied to the interrupt the ROB took, and an interrupt the filter presents between the ROB taking one and its trap arriving would replace both. The programs raise no other interrupt, so that case was not run.

<details>
<summary>The routing change</summary>

```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -430,6 +430,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   val intrVec = RegEnable(intrMod.io.out.interruptVec.bits, 0.U, intrMod.io.out.interruptVec.valid)
   val debug = RegEnable(intrMod.io.out.debug, false.B, intrMod.io.out.interruptVec.valid)
   val nmi = intrMod.io.out.nmi
+  // Latched with the interrupt number, as debug, irToHS and irToVS are: whether the interrupt being taken is an NMI,
+  // even if the NMI input has dropped since it was requested.
+  val nmiLatched = RegEnable(intrMod.io.out.nmi, false.B, intrMod.io.out.interruptVec.valid)
   val virtualInterruptIsHvictlInject = intrMod.io.out.virtualInterruptIsHvictlInject
   val irToHS = RegEnable(intrMod.io.out.irToHS, false.B, intrMod.io.out.interruptVec.valid)
   val irToVS = RegEnable(intrMod.io.out.irToVS, false.B, intrMod.io.out.interruptVec.valid)
@@ -842,8 +845,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     }
   }
 
-  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
-  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
+  trapEntryMNEvent.valid  := ((hasTrap && (nmi || (trapIsInterrupt && nmiLatched))) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
+  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !(nmi || (trapIsInterrupt && nmiLatched)) && mnstatus.regOut.NMIE
   trapEntryHSEvent.valid  := hasTrap && entryPrivState.isModeHS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
   trapEntryVSEvent.valid  := hasTrap && entryPrivState.isModeVS && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
 
```

</details>

<details>
<summary>The program's output with the routing change</summary>

With the change, pulses of 21, 23, 25 and 29 to 48 cycles took an RNMI and the others none. The change matters only for an interrupt trap that arrives after `nmi` has fallen, so with it the program under "To Reproduce" printed the same line as in the run above for every cell before width 21's. There the RNMI handler ran instead of the M-mode one, and it ends the cell instead of returning into the spin loop, so every later cell started from a different state; that is why widths 28 and 29 came out differently (28: no trap instead of an RNMI; 29: an RNMI instead of no trap).

```text
# nmi_sweep_uartlite.elf
P4G_BEGIN 5034464f4c390001
N 00 0 0 0
N 01 0 0 0
N 02 0 0 0
N 03 0 0 0
N 04 0 0 0
N 05 0 0 0
N 06 0 0 0
N 07 0 0 0
N 08 0 0 0
N 09 0 0 0
N 0a 0 0 0
N 0b 0 0 0
N 0c 0 0 0
N 0d 0 0 0
N 0e 0 0 0
N 0f 0 0 0
N 10 0 0 0
N 11 0 0 0
N 12 0 0 0
N 13 0 0 0
N 14 0 0 0
N 15 0 1 0 800000000000002b 1800
N 16 0 0 0
N 17 0 1 0 800000000000002b 1800
N 18 0 0 0
N 19 0 1 0 800000000000002b 1800
N 1a 0 0 0
N 1b 0 0 0
N 1c 0 0 0
N 1d 0 1 0 800000000000002b 1800
N 1e 0 1 0 800000000000002b 1800
N 1f 0 1 0 800000000000002b 1800
N 20 0 1 0 800000000000002b 1800
N 21 0 1 0 800000000000002b 1800
N 22 0 1 0 800000000000002b 1800
N 23 0 1 0 800000000000002b 1800
N 24 0 1 0 800000000000002b 1800
N 25 0 1 0 800000000000002b 1800
N 26 0 1 0 800000000000002b 1800
N 27 0 1 0 800000000000002b 1800
N 28 0 1 0 800000000000002b 1800
N 29 0 1 0 800000000000002b 1800
N 2a 0 1 0 800000000000002b 1800
N 2b 0 1 0 800000000000002b 1800
N 2c 0 1 0 800000000000002b 1800
N 2d 0 1 0 800000000000002b 1800
N 2e 0 1 0 800000000000002b 1800
N 2f 0 1 0 800000000000002b 1800
N 30 0 1 0 800000000000002b 1800
N 31 0 1 0 800000000000002b 1800
P4G_END 00
Core 0: HIT GOOD TRAP at pc = 0x8000159c
```

</details>

<details>
<summary>The delay sweep's output with the routing change</summary>

Cell `00` is the first control, cells `01` to `10` are width 21, `11` to `20` are width 25 and `21` to `30` are width 31, each at delays 0 to 15 in order, and cell `31` is the held control.

```text
# nmi_delay_uartlite.elf
P4G_BEGIN 5034464f4c3a0001
N 00 0 0 0
N 01 0 1 0 800000000000002b 1800
N 02 0 0 0
N 03 0 0 0
N 04 0 0 0
N 05 0 1 0 800000000000002b 1800
N 06 0 0 0
N 07 0 1 0 800000000000002b 1800
N 08 0 1 0 800000000000002b 1800
N 09 0 1 0 800000000000002b 1800
N 0a 0 1 0 800000000000002b 1800
N 0b 0 1 0 800000000000002b 1800
N 0c 0 1 0 800000000000002b 1800
N 0d 0 1 0 800000000000002b 1800
N 0e 0 1 0 800000000000002b 1800
N 0f 0 1 0 800000000000002b 1800
N 10 0 1 0 800000000000002b 1800
N 11 0 1 0 800000000000002b 1800
N 12 0 0 0
N 13 0 1 0 800000000000002b 1800
N 14 0 1 0 800000000000002b 1800
N 15 0 1 0 800000000000002b 1800
N 16 0 1 0 800000000000002b 1800
N 17 0 1 0 800000000000002b 1800
N 18 0 1 0 800000000000002b 1800
N 19 0 1 0 800000000000002b 1800
N 1a 0 1 0 800000000000002b 1800
N 1b 0 1 0 800000000000002b 1800
N 1c 0 1 0 800000000000002b 1800
N 1d 0 1 0 800000000000002b 1800
N 1e 0 1 0 800000000000002b 1800
N 1f 0 1 0 800000000000002b 1800
N 20 0 1 0 800000000000002b 1800
N 21 0 1 0 800000000000002b 1800
N 22 0 1 0 800000000000002b 1800
N 23 0 1 0 800000000000002b 1800
N 24 0 1 0 800000000000002b 1800
N 25 0 1 0 800000000000002b 1800
N 26 0 1 0 800000000000002b 1800
N 27 0 1 0 800000000000002b 1800
N 28 0 1 0 800000000000002b 1800
N 29 0 1 0 800000000000002b 1800
N 2a 0 1 0 800000000000002b 1800
N 2b 0 1 0 800000000000002b 1800
N 2c 0 1 0 800000000000002b 1800
N 2d 0 1 0 800000000000002b 1800
N 2e 0 1 0 800000000000002b 1800
N 2f 0 1 0 800000000000002b 1800
N 30 0 1 0 800000000000002b 1800
N 31 0 1 0 800000000000002b 1800
P4G_END 00
Core 0: HIT GOOD TRAP at pc = 0x800015a0
```

</details>

Related, and not this bug:

- [#6182](https://github.com/OpenXiangShan/XiangShan/issues/6182) reports that the interrupt filter's delayed vector is not flushed when an enable bit clears, so a stale interrupt can be taken; it shows the NMI vector entering that latched path, but its trigger is an enable that clears, not an NMI input that drops.
- [#4825](https://github.com/OpenXiangShan/XiangShan/pull/4825) changes when the NMI pending state is cleared.

None of them reports an NMI pulse taken as an ordinary M-mode interrupt.

**Scope.** The program ran on `DefaultConfig` at the commit under "Environment" with the patch from "To Reproduce". Only `nmi_43` was driven, in M-mode with `mie` = 0 and `mstatus.MIE` = 0; `nmi_31`, other privilege modes and enabled interrupts were not run. The NMI source is the simulation's; whether a platform drops an NMI input this soon is the platform's to say. After its write the program spins in a two-instruction loop. Why the ROB took the interrupt 30 cycles after the CSR file requested it was not examined: the trace does not include the ROB's own state (`interruptSafe`, `hasWaitForward`, `deqHasFlushed`).

**AI assistance.** This report was prepared with Claude Opus 5.5 (Anthropic) in Claude Code.
