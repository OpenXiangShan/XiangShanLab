### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

On `kunminghu-v3` with `DefaultConfig`, an RNMI (Smrnmi) taken on an M-mode `ecall` writes the `ecall`'s cause to `mncause`: `0x000000000000000b`, bit 63 clear and exception code 11. The same NMI input taken before the `ecall`, or inside the trap handler the `ecall` entered, writes `0x800000000000002b`. With DiffTest against NEMU the run stops at the first RNMI taken on the `ecall`, because the core reports that trap to DiffTest with the same cause, exception 11 (see "Additional context"). With bit 63 clear, `0x000000000000000b` is what Smdbltrp writes for a double trap caused by an `ecall` in M-mode, although `mstatus.MDT` was clear when this RNMI was taken (see "Expected behavior"), so there is no double trap. In the source below the RNMI entry receives the same cause as every other trap entry, which for an exception is that exception's.

#### Where it comes from

The RNMI entry is taken for any trap while an NMI is pending, and the M-mode entry is held off in that case:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L845-L846
  trapEntryMNEvent.valid  := ((hasTrap && nmi) || dbltrpToMN) && !entryDebugMode && !debugMode && mnstatus.regOut.NMIE
  trapEntryMEvent .valid  := hasTrap && entryPrivState.isModeM && !dbltrpToMN && !entryDebugMode && !debugMode && !nmi && mnstatus.regOut.NMIE
```

`nmi` comes from the NMI-pending register through the interrupt module, while the trap and whether it is an interrupt come from the ROB:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L417
  intrMod.io.in.nmi := nmip.asUInt.orR
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L432
  val nmi = intrMod.io.out.nmi
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L276-L280
  val hasTrap = io.fromRob.trap.valid
  val trapVec = io.fromRob.trap.bits.trapVec
  val trapPC = io.fromRob.trap.bits.pc
  val trapPCGPA = io.fromRob.trap.bits.pcGPA
  val trapIsInterrupt = io.fromRob.trap.bits.isInterrupt
```

Every trap entry, the RNMI entry included, receives the cause the trap-handling module computes:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L850-L853
  Seq(trapEntryMEvent, trapEntryMNEvent, trapEntryHSEvent, trapEntryVSEvent, trapEntryDEvent).foreach { eMod =>
    eMod.in match {
      case in: TrapEntryEventInput =>
        in.causeNO := trapHandleMod.io.out.causeNO
```

For an exception, that cause has the interrupt bit clear and the exception's code:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L79
  private val causeNO = Mux(hasIR, interruptNO, exceptionNO)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L108-L109
  io.out.causeNO.Interrupt := hasIR
  io.out.causeNO.ExceptionCode := causeNO
```

and the RNMI entry copies both fields into `mncause`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala#L30-L31
  private val highPrioTrapNO = in.causeNO.ExceptionCode.asUInt
  private val isInterrupt = in.causeNO.Interrupt.asBool
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMNEvent.scala#L57-L58
  out.mncause.bits.Interrupt     := isInterrupt
  out.mncause.bits.ExceptionCode := highPrioTrapNO
```

So the RNMI entry is selected by the pending flag, and its cause by the kind of trap the ROB delivers. When the RNMI is taken on an instruction that raises no exception, the ROB delivers an interrupt, and the same path yields the interrupt bit and the NMI's number (43, for the `nmi_43` input we drove). When the ROB delivers an instruction's exception while `nmi` is set, the RNMI entry is taken with that exception's cause.

#### The runs

The program under "To Reproduce" runs in M-mode. In each cell the program writes a delay to the NMI source, which raises `nmi_43` that many cycles later and holds it until the RNMI handler clears it, and then calls a routine that executes `li a3, 8` and then `ecall`. The M-mode trap handler counts an `ecall` and returns past it; the RNMI handler records `mncause` and `mnepc`, clears the source and moves to the next cell. The program clears `mstatus.MDT` at the start and again at the end of every cell. A control cell raises the NMI with no `ecall` to follow. The program runs the control first and then delays 0 to 63 cycles:

| NMI delay (cycles) | RNMI taken at | `mncause` |
|---|---|---|
| 0 to 20 | the `jal` that calls the `ecall` routine | `0x800000000000002b` |
| 21 to 37 | the `ecall` (`mnepc` is its address) | `0x000000000000000b` |
| 38 to 63 | the M-mode trap handler's second instruction, `andi t6,t6,8` after `csrr t6,0x744` (`0x744` is `mnstatus`), at every delay in this range | `0x800000000000002b` |
| control | `li a2, 300`, with no `ecall` to follow | `0x800000000000002b` |

Every cell took exactly one RNMI, the M-mode handler counted no `ecall`, and no M-mode interrupt or other trap was counted. The 17 RNMIs taken on the `ecall` read the `ecall`'s cause; the other 48, the control included, read `0x800000000000002b`.

<details>
<summary>The program's output</summary>

Each line is `R <cell> <ecalls counted by the M-mode handler> <M-mode interrupts> <RNMI entries> <other M-mode traps>[ <mcause>][ <mncause> <mnepc[31:0]>][ <mcause of the other trap>]`, and cell `00` is the control.

```text
# rnmi_sweep_uartlite.elf
P4G_BEGIN 5034464f4c350001
R 00 0 0 1 0 800000000000002b 80001100
R 01 0 0 1 0 800000000000002b 8000116c
R 02 0 0 1 0 800000000000002b 8000116c
R 03 0 0 1 0 800000000000002b 8000116c
R 04 0 0 1 0 800000000000002b 8000116c
R 05 0 0 1 0 800000000000002b 8000116c
R 06 0 0 1 0 800000000000002b 8000116c
R 07 0 0 1 0 800000000000002b 8000116c
R 08 0 0 1 0 800000000000002b 8000116c
R 09 0 0 1 0 800000000000002b 8000116c
R 0a 0 0 1 0 800000000000002b 8000116c
R 0b 0 0 1 0 800000000000002b 8000116c
R 0c 0 0 1 0 800000000000002b 8000116c
R 0d 0 0 1 0 800000000000002b 8000116c
R 0e 0 0 1 0 800000000000002b 8000116c
R 0f 0 0 1 0 800000000000002b 8000116c
R 10 0 0 1 0 800000000000002b 8000116c
R 11 0 0 1 0 800000000000002b 8000116c
R 12 0 0 1 0 800000000000002b 8000116c
R 13 0 0 1 0 800000000000002b 8000116c
R 14 0 0 1 0 800000000000002b 8000116c
R 15 0 0 1 0 800000000000002b 8000116c
R 16 0 0 1 0 000000000000000b 80001334
R 17 0 0 1 0 000000000000000b 80001334
R 18 0 0 1 0 000000000000000b 80001334
R 19 0 0 1 0 000000000000000b 80001334
R 1a 0 0 1 0 000000000000000b 80001334
R 1b 0 0 1 0 000000000000000b 80001334
R 1c 0 0 1 0 000000000000000b 80001334
R 1d 0 0 1 0 000000000000000b 80001334
R 1e 0 0 1 0 000000000000000b 80001334
R 1f 0 0 1 0 000000000000000b 80001334
R 20 0 0 1 0 000000000000000b 80001334
R 21 0 0 1 0 000000000000000b 80001334
R 22 0 0 1 0 000000000000000b 80001334
R 23 0 0 1 0 000000000000000b 80001334
R 24 0 0 1 0 000000000000000b 80001334
R 25 0 0 1 0 000000000000000b 80001334
R 26 0 0 1 0 000000000000000b 80001334
R 27 0 0 1 0 800000000000002b 80001544
R 28 0 0 1 0 800000000000002b 80001544
R 29 0 0 1 0 800000000000002b 80001544
R 2a 0 0 1 0 800000000000002b 80001544
R 2b 0 0 1 0 800000000000002b 80001544
R 2c 0 0 1 0 800000000000002b 80001544
R 2d 0 0 1 0 800000000000002b 80001544
R 2e 0 0 1 0 800000000000002b 80001544
R 2f 0 0 1 0 800000000000002b 80001544
R 30 0 0 1 0 800000000000002b 80001544
R 31 0 0 1 0 800000000000002b 80001544
R 32 0 0 1 0 800000000000002b 80001544
R 33 0 0 1 0 800000000000002b 80001544
R 34 0 0 1 0 800000000000002b 80001544
R 35 0 0 1 0 800000000000002b 80001544
R 36 0 0 1 0 800000000000002b 80001544
R 37 0 0 1 0 800000000000002b 80001544
R 38 0 0 1 0 800000000000002b 80001544
R 39 0 0 1 0 800000000000002b 80001544
R 3a 0 0 1 0 800000000000002b 80001544
R 3b 0 0 1 0 800000000000002b 80001544
R 3c 0 0 1 0 800000000000002b 80001544
R 3d 0 0 1 0 800000000000002b 80001544
R 3e 0 0 1 0 800000000000002b 80001544
R 3f 0 0 1 0 800000000000002b 80001544
R 40 0 0 1 0 800000000000002b 80001544
P4G_END 00
Core 0: HIT GOOD TRAP at pc = 0x80001534
```

</details>

### Expected behavior

Smrnmi gives an RNMI priority over every exception, so taking the RNMI on the `ecall` and writing the `ecall`'s address to `mnepc`, as the runs show, is what the extension asks for:

> The `rnmi` interrupt signals are inputs to the hart. These interrupts have higher priority than any other interrupt or exception on the hart and cannot be disabled by software. Specifically, they are not disabled by clearing the `mstatus`.MIE register.
>
> — [smrnmi.adoc, lines 18-21](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/smrnmi.adoc#L18-L21)

What `mncause` holds depends on the reason for the RNMI:

> The `mncause` CSR holds the reason for the RNMI. If the reason is an interrupt, bit MXLEN-1 is set to 1, and the RNMI cause is encoded in the least-significant bits. If the reason is an interrupt and RNMI causes are not supported, bit MXLEN-1 is set to 1, and zero is written to the least-significant bits. If the reason is an exception within M-mode that results in a double trap as specified in the Smdbltrp extension, bit MXLEN-1 is set to 0 and the least-significant bits are set to the cause code corresponding to the exception that precipitated the double trap.
>
> — [smrnmi.adoc, lines 72-80](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/smrnmi.adoc#L72-L80)

> When an RNMI interrupt is detected, the interrupted PC is written to the `mnepc` CSR, the type of RNMI to the `mncause` CSR, and the privilege mode of the interrupted context to the `mnstatus` CSR. The `mnstatus`.NMIE bit is cleared, masking all interrupts.
>
> — [smrnmi.adoc, lines 152-155](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/smrnmi.adoc#L152-L155)

The double-trap encoding is the one Smdbltrp writes when an unexpected trap is delivered to the RNMI handler:

> When the Smrnmi extension is implemented and `mnstatus`.NMIE is 1, the hart traps to the RNMI interrupt trap handler. To deliver this trap, the `mnepc` and `mncause` registers are written with the values that the _unexpected trap_ would have written to the `mepc` and `mcause` registers respectively. The privilege mode information fields in the `mnstatus` register are written to indicate M-mode and its `nmie` field is set to 0.
>
> — [machine.adoc, lines 480-485](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/machine.adoc#L480-L485)

In the 17 cells that read `0x000000000000000b`, `mnepc` holds the `ecall`'s own address, so the RNMI was taken in place of the `ecall`, and the M-mode entry is held off while `nmi` is set (quoted above). Under DiffTest, at the first of them the core's `mepc` and `mcause` were still 0 and its `mstatus`, `0x0000000a00000000`, had MDT clear, while the reference, told of an exception, wrote the `ecall`'s address and 11 and set MDT (`0x0000040a00001800`). The reason for the RNMI is therefore the interrupt, and with `mstatus.MDT` clear there is no double trap, so `mncause` should read `0x800000000000002b`, as it does for the same input on every other instruction in the runs. In the control and at delays 0 to 20, NEMU took each NMI the core reported at the same instruction, with no difference in the registers and CSRs DiffTest compares. `mncause` is not among those CSRs, but DiffTest does not skip the RNMI handler's `csrr` of it, and the program prints the value it read through registers DiffTest compares, so that value was checked in those cells as well. With the change under "Fix direction" the core reports these RNMIs to DiffTest as the NMI, and NEMU, told of that NMI at the same `ecall`s, took the RNMI there and held `0x800000000000002b` in `mncause` as well: in all 17 cells the values the RNMI handler read passed through registers DiffTest compares, with no difference. DiffTest hands the reference the interrupt number the core reports, so this shows the reference takes the NMI at that instruction with that cause, not that it derives the number itself.

### Environment

- Hardware
  - Memory (GB): 256, with 16 cores, for the build and the runs, in one job.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container the emulator ran in.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17.
- Repo
  - XiangShan commit id: [`18657f51ce1ed4acaffdceb38bb79ebae68b6af4`](https://github.com/OpenXiangShan/XiangShan/commit/18657f51ce1ed4acaffdceb38bb79ebae68b6af4), with the patch under "To Reproduce" applied; the emulator prints `Core  0's Commit SHA is: 18657f51ce, dirty: 1`. The `difftest` submodule is at [`99f77ff455766d18cd05927b144641bd9744b19a`](https://github.com/OpenXiangShan/difftest/commit/99f77ff455766d18cd05927b144641bd9744b19a).
  - NEMU commit id (if difftest failed with NEMU): [`513786c4c597dd628c6126144937372d6f7fc92e`](https://github.com/OpenXiangShan/NEMU/commit/513786c4c597dd628c6126144937372d6f7fc92e), as recorded by the `ready-to-run` commit [`4cf9983569432035d41956c65e6a58724b5954be`](https://github.com/OpenXiangShan/ready-to-run/commit/4cf9983569432035d41956c65e6a58724b5954be) that the head's gitlink names; its `riscv64-nemu-interpreter-so` was the reference of the DiffTest run under "Additional context".
- Build & Run
  - Build command: see "To Reproduce". The emulator we ran was built by the build steps there, with `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
  - Run command (if applicable): `./build/emu -i rnmi_sweep_uartlite.elf --no-diff --seed 1 --max-cycles 2500000 --ram-size 256MB`.

### To Reproduce

The bug needs the NMI to be pending when the ROB delivers an instruction's exception to the CSR file; in these runs that happened at 17 consecutive NMI delays. The simulation top ties the NMI inputs low, so the first step gives the simulation interrupt generator a small NMI source. Only the simulation's `SimMMIO` instantiates that generator, so the core is unchanged.

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

2. Build the emulator. Suggested build command, with the GCC under "Environment"; we did not run it as written:

```sh
make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"
```

<details>
<summary>Build steps we ran, after the patch</summary>

```sh
mill -i -Djvm-xmx=40G -Djvm-xss=256m xiangshan.test.runMain top.XiangShanSim --target-dir build/chirrtl --config DefaultConfig --issue E.b --num-cores 1 --target chirrtl --enable-difftest --full-stacktrace
firtool build/chirrtl/SimTop.fir -o build/rtl --split-verilog -O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none --default-layer-specialization=enable
# then the Makefile's RTL rule for this configuration (Makefile lines 341-342, 350 and 353); our build made the same three edits:
{ git log -n 1; git diff; } | sed 's/^/\/\// ' > ./build/rtl/.__diff__
cat ./build/rtl/.__diff__ ./build/rtl/SimTop.sv > ./build/rtl/.__out__ && mv ./build/rtl/.__out__ ./build/rtl/SimTop.sv
sed -i -e 's/$fatal/xs_assert_v2(`__FILE__, `__LINE__)/g' ./build/rtl/*.sv
sed -i -e "s/\$error(/\$fwrite(32\'h80000002, /g" ./build/rtl/*.sv
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" -j16
```

firtool was version 1.149.0. Those steps used this cluster's absolute paths, shortened here, and the `make` step also set `CXX`, `LINK` and `AR` to the GCC 15.2.0 toolchain named under "Environment", and `PGO_CFLAGS` and `PGO_LDFLAGS` to header and library directories for the native dependencies; those five are left out.

`OPT_FAST` is set in these steps and in the suggested command because difftest's default at this commit, `-O3 -fno-slp-vectorize`, is a clang option that GCC rejects; `-fno-tree-slp-vectorize` is GCC's name for the same pass. `SIM_VFLAGS` splits Verilator's output more finely than difftest's `--output-split 30000`, and is set in the environment because the difftest Makefile appends `+define+DIFFTEST` to it.

</details>

3. Assemble and link the program and linker script below, then run it without DiffTest; with DiffTest the run stops at the first RNMI taken on the `ecall` (see "Additional context"):

```sh
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -c -o rnmi_sweep_uartlite.o rnmi_sweep_uartlite.S
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T rnmi_sweep.ld -o rnmi_sweep_uartlite.elf rnmi_sweep_uartlite.o
```

The ELF we ran was built with `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0`; its sha256 is `210efd03b4f579acf44955eee701448ed0e9a0db9c2b6b182aea031c3ec1c18b`.

```sh
./build/emu -i rnmi_sweep_uartlite.elf --no-diff --seed 1 --max-cycles 2500000 --ram-size 256MB
```

Each run prints one line per cell and ends on the program's last instruction with `HIT GOOD TRAP`, which means only that the program finished: it does not check `mncause`, and the values are in the `R` lines. In this program cell `00` is the control and cells `01` to `40` are delays 0 to 63; cells `16` to `26` (delays 21 to 37) print `mncause` = `0x000000000000000b` with `mnepc` = `0x80001334`, the `ecall`'s address. The whole output is in the collapsed block under "Describe the bug".

<details>
<summary><code>rnmi_sweep_uartlite.S</code>, without its generator's header comment</summary>

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
    li a0, 0x5034464f4c350001
    li a1, 16
    jal ra, puthex
    li a0, 10
    jal ra, putchar
    la s3, rc_end
    jal ra, reset_vars
    li t0, 0x4007001c
    li t1, 0x2000
    sw t1, 0(t0)
    li a2, 300
rc_spin:
    addi a2, a2, -1
    bnez a2, rc_spin
rc_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    li a4, 0
    jal ra, print_R
    li s4, 0
    li s5, 64
    li s6, 1
cell_loop:
    la s3, cell_end
    jal ra, reset_vars
    slli t1, s4, 16
    li t2, 0x2000
    or t1, t1, t2
    li t0, 0x4007001c
    sw t1, 0(t0)
    jal ra, ecall_stream
    li a2, 300
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
    jal ra, print_R
    addi s4, s4, 1
    addi s6, s6, 1
    blt s4, s5, cell_loop
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
print_R:
    mv s2, ra
    li a0, 82
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, a4
    li a1, 2
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 80(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
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
    beqz t3, print_R_no_m
    li a0, 32
    jal ra, putchar
    ld a0, 8(gp)
    li a1, 16
    jal ra, puthex
print_R_no_m:
    ld t3, 32(gp)
    beqz t3, print_R_no_n
    li a0, 32
    jal ra, putchar
    ld a0, 40(gp)
    li a1, 16
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 48(gp)
    li a1, 8
    jal ra, puthex
print_R_no_n:
    ld t3, 88(gp)
    beqz t3, print_R_no_u
    li a0, 32
    jal ra, putchar
    ld a0, 96(gp)
    li a1, 16
    jal ra, puthex
print_R_no_u:
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
<summary><code>rnmi_sweep.ld</code></summary>

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

Related, and not this bug:

- On `kunminghu-v2`, [#6258](https://github.com/OpenXiangShan/XiangShan/pull/6258) keeps the HS and VS trap entries from firing while an NMI is pending, for an NMI and an exception that occur together. It does not change the RNMI entry or `mncause`.
- [#6502](https://github.com/OpenXiangShan/XiangShan/issues/6502) quotes the RNMI entry's valid, `(hasTrap && nmi)`, while reporting how a delegated exception is handled with `mnstatus.NMIE` clear.
- [#6203](https://github.com/OpenXiangShan/XiangShan/pull/6203), an open pull request against `kunminghu-v2`, proposes changing that valid for interrupts taken at an xRET or CSR-write boundary and keeps the pending flag for an exception.

None of them reports the value written to `mncause`.

**DiffTest.** With DiffTest against NEMU (`./build/emu -i rnmi_sweep_uartlite.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 2500000 --ram-size 256MB`), the same program ran in step with the reference through the control and delays 0 to 20, each with its RNMI, and stopped at delay 21, the first RNMI taken on the `ecall`. The core reports every trap to DiffTest from the cause the trap-handling module computes, the same cause the RNMI entry copies into `mncause`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1673-L1677
    val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool
    val trapNO = Mux(virtualInterruptIsHvictlInject && interrupt, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt)
    val hasNMI = nmi && hasTrap
    val interruptNO = Mux(interrupt, trapNO, 0.U)
    val exceptionNO = Mux(!interrupt, trapNO, 0.U)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1699-L1702
    diffArchEvent.interrupt := RegEnable(interruptNO, hasTrap)
    diffArchEvent.exception := RegEnable(exceptionNO, hasTrap)
    diffArchEvent.exceptionPC := RegEnable(exceptionPC, hasTrap)
    diffArchEvent.hasNMI := RegEnable(hasNMI, hasTrap)
```

So the core reported the RNMI on the `ecall` as exception 11 at the `ecall`'s address, the reference took the environment-call exception (`mcause` 11, `mepc` at the `ecall`) where the core took the RNMI, and the two disagree on `mstatus`, `mepc` and `mcause`:

<details>
<summary>The last commits DiffTest traced, and the differences it reported</summary>

```text
[27] commit pc 0000000080001154 inst 010a1313 wen 1 dst 06 data 0000000000150000 idx 025
[28] commit pc 0000000080001168 inst 0062a023 wen 0 dst 00 data 0000000002f2f2f3 idx 025 (3d) (S)
[29] commit pc 000000008000116c inst 1c4000ef wen 1 dst 01 data 0000000080001170 idx 026
[30] commit pc 0000000080001330 inst 00800693 wen 1 dst 13 data 0000000000000008 idx 027
[31] exception pc 0000000080001334 inst 00000073 cause 000000000000000b <--
...
mstatus different at pc = 0x0080001500, right = 0x0000040a00001800, wrong = 0x0000000a00000000
mepc different at pc = 0x0080001500, right = 0x0000000080001334, wrong = 0x0000000000000000
mcause different at pc = 0x0080001500, right = 0x000000000000000b, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0x80001500
```

</details>

**Fix direction.** Both readers of the cause need the change: the RNMI entry and the event the core reports to DiffTest. When the RNMI entry is taken with `dbltrpToMN` clear (`trapEntryMNEvent.valid && !dbltrpToMN`), giving both readers the interrupt bit and the NMI's number from the interrupt module's output in the same cycle (`intrMod.io.out.interruptVec.bits`, which `intrVec` registers a cycle later) gave these results on this program, in builds of the same commit:

| Change to the core | `mncause` without DiffTest | With DiffTest |
|---|---|---|
| none | `0x000000000000000b` in the 17 cells at the `ecall`, `0x800000000000002b` in the other 48 | stops at delay 21 |
| the RNMI entry's cause | `0x800000000000002b` in all 65 | stops at delay 21 |
| the RNMI entry's cause and the DiffTest event | `0x800000000000002b` in all 65 | runs every delay to the end, no difference reported |

In the last row's run with DiffTest, the `mncause` value the RNMI handler read in each of the 65 cells passed through registers DiffTest compares, with no difference reported.

Because the condition is the RNMI entry's own valid, it carries that entry's `mnstatus.NMIE` and debug-mode terms, and it also covers an RNMI taken on an ordinary interrupt the ROB delivers, which this program does not reach. `intrMod.io.out.interruptVec.bits` is read combinationally from the interrupt filter in the trap cycle instead of through the `intrVec` register; whether that suits the design's timing is the maintainers' call.

The `!dbltrpToMN` term keeps the change off the double-trap path. For a trap handled in M-mode, `dbltrpToMN` is set by `mstatus.MDT` and `mnstatus.NMIE` whether or not `nmi` is set:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L82
  private val m_EX_DT  = handleTrapUnderM  && mstatus.MDT.asBool  && hasTrap
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L86
  private val dbltrpToMN = m_EX_DT && mnstatus.NMIE.asBool // NMI not allow double trap
```

So with MDT set, an exception the ROB delivers while an NMI is pending keeps its own cause under this change. By the argument under "Expected behavior" the RNMI is then taken in place of the excepting instruction and there is no double trap yet, which would make the condition `trapEntryMNEvent.valid && nmi`; that case was not run, and which of the two conditions the design wants is open.

<details>
<summary><code>NewCSR.scala</code>: the change we built and ran</summary>

```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 1f4fe77..459cccc 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -903,6 +903,13 @@ class NewCSR(implicit val p: Parameters) extends Module
     }
   }
 
+  // The RNMI entry taken with dbltrpToMN clear records the NMI as its cause.
+  val rnmiForNmi = trapEntryMNEvent.valid && !dbltrpToMN
+  when (rnmiForNmi) {
+    trapEntryMNEvent.in.causeNO.Interrupt := true.B
+    trapEntryMNEvent.in.causeNO.ExceptionCode := intrMod.io.out.interruptVec.bits
+  }
+
   mnretEvent.valid := legalMNret
   mnretEvent.in match {
     case in =>
@@ -1670,8 +1677,10 @@ class NewCSR(implicit val p: Parameters) extends Module
     io.diffArchEvent.get.bits := pendingLatter
 
     val hartId = io.fromTop.hartId
-    val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool
-    val trapNO = Mux(virtualInterruptIsHvictlInject && interrupt, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt)
+    // DiffTest is told about the trap the core takes: an RNMI taken for an NMI is reported as the NMI.
+    val interrupt = trapHandleMod.io.out.causeNO.Interrupt.asBool || rnmiForNmi
+    val trapNO = Mux(rnmiForNmi, intrMod.io.out.interruptVec.bits,
+      Mux(virtualInterruptIsHvictlInject && interrupt, hvictl.regOut.IID.asUInt, trapHandleMod.io.out.causeNO.ExceptionCode.asUInt))
     val hasNMI = nmi && hasTrap
     val interruptNO = Mux(interrupt, trapNO, 0.U)
     val exceptionNO = Mux(!interrupt, trapNO, 0.U)
```

</details>

**Scope.** The program ran on three builds of `DefaultConfig` at the commit under "Environment", each with the patch from "To Reproduce": unchanged, with the RNMI entry's cause changed alone, and with the change under "Fix direction"; on each build once without DiffTest and once with it. The excepting instruction was an M-mode `ecall` and the NMI was `nmi_43`; other exceptions, privilege modes and `nmi_31` were not run. M-mode interrupts were disabled (`mie` = 0), so an NMI arriving while the ROB delivers an ordinary interrupt was not run. The case of `mstatus.MDT` = 1 with an NMI pending while the ROB delivers an exception was not run.

**AI assistance.** This report was prepared with Claude Opus 5.5 (Anthropic) in Claude Code.
