### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

On `kunminghu-v3` with `DefaultConfig`, a 64-bit store of `0x0123456700000000` to `mtime` does not change the timer, at either of the two addresses the SoC maps it at: after a store to TIMER's `mtime` (`0x3800bff8`) the `time` CSR read `0x000000000000013e`, and after one to SYSCNT's (`0x3804bff8`) it read `0x0000000000000242`, both counts from reset, below the stored value. Neither store trapped. Only the same store to SYSCNT's `mtime` followed by a write of 1 to SYSCNT's `timeswReq` (`0x3804c010`) set the timer: `time` then read `0x01234567000000c4`.

#### Where it comes from

The SoC places two timer devices, TIMER and SYSCNT, and feeds TIMER's counter from SYSCNT's:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/system/SoC.scala#L75-L76
  TIMERRange: AddressSet = AddressSet(0x38000000L, TIMERConsts.size - 1),
  SYSCNTRange: AddressSet = AddressSet(0x38040000L, SYSCNTConsts.size - 1),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/system/SoC.scala#L568
    timer.module.io.time <> syscnt.module.io.time
```

TIMER copies the counter it is given and maps `mtime` read-only:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/TIMER.scala#L86-L87
    val time = RegInit(0.U(timeWidth.W))
    when(io.time.valid)(time := io.time.bits)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/TIMER.scala#L137
      timeOffset -> Seq(RegField.r(64, time, RegFieldDesc("mtime", "", volatile = true)))
```

SYSCNT maps `mtime` as a register that a store writes into `time_sw`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/SYSCNT.scala#L178-L188
      timeOffset -> RegFieldGroup(
        "mtime",
        Some("Timer Register"),
        Seq(RWNotify(
          64,
          time_rpt_bus,
          time_sw,
          time_rpt_rd,
          time_sw_vld,
          Some(RegFieldDesc("timeswReq", "", reset = Some(0), volatile = true))
        ))
```

and copies `time_sw` into its counter only on a rising edge of `timeswReq`, a separate register at `0xc010` in its region (the `RegFieldDesc` of the `mtime` field above carries the same name):

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/SYSCNT.scala#L40
  def timeswReq   = timefreq + 0x0010
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/SYSCNT.scala#L144-L146
    when(time_req_rtc_ris) {
      time := time_sw
    }.elsewhen(stop_sync) {
```

So a store to TIMER's `mtime` is ignored, because `RegField.r` maps TIMER's `time` read-only, and a store to SYSCNT's `mtime` stops in `time_sw` until software writes `timeswReq` as well.

#### The run

The program under "To Reproduce" runs in M-mode. Each of its three cells reads `time`, stores `0x0123456700000000` with one `sd`, waits 20000 loop iterations, reads `time` again and loads the `mtime` address it stored to:

| cell | `time` before | `time` after the wait | load of the `mtime` address |
|---|---|---|---|
| `sd` to TIMER's `mtime` (`0x3800bff8`) | `0x0000000000000075` | `0x000000000000013e` | `0x0000000000000140` |
| `sd` to SYSCNT's `mtime` (`0x3804bff8`) | `0x0000000000000179` | `0x0000000000000242` | `0x0000000000000001` |
| `sd` to SYSCNT's `mtime`, then `1` to `timeswReq` (`0x3804c010`) | `0x000000000000027d` | `0x01234567000000c4` | `0x0000000000000001` |

The loads of SYSCNT's `mtime` read `0x0000000000000001` in both SYSCNT cells, in the third after `time` had read `0x01234567000000c4`; this report rests on the `time` CSR, not on that value. A load there returns `time_rpt_bus`, the read value in the register map above, which is SYSCNT's counter after `TimeAsync` carries it to the bus clock, the crossing [#6372](https://github.com/OpenXiangShan/XiangShan/issues/6372) is about:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/SYSCNT.scala#L157
    val time_rpt_bus = timeasync.io.o_time.bits
```

<details>
<summary>The program's output</summary>

```text
# mtime2_U_uartlite.elf
W2_BEGIN 4c4f574d54320000
W2_STEP 0000000000000000
W2_VAL 0000000000000075 000000000000013e 0000000000000140
W2_STEP 0000000000000001
W2_VAL 0000000000000179 0000000000000242 0000000000000001
W2_STEP 0000000000000002
W2_VAL 000000000000027d 01234567000000c4 0000000000000001
W2_END 0000000000000000
Core 0: HIT GOOD TRAP at pc = 0x8000165c
```

</details>

### Expected behavior

The privileged specification defines `mtime` as a read-write register:

> Platforms provide a real-time counter, exposed as a memory-mapped machine-mode read-write register, `mtime`.
>
> — [machine.adoc, lines 2511-2512](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/machine.adoc#L2511-L2512)

and `time` as its shadow, which reflects a change of `mtime`:

> The `time` CSR is a read-only shadow of the memory-mapped `mtime` register. When XLEN=32, the `timeh` CSR is a read-only shadow of the upper 32 bits of the memory-mapped `mtime` register, while `time` shadows only the lower 32 bits of `mtime`. When `mtime` changes, it is guaranteed to be reflected in `time` and `timeh` eventually, but not necessarily immediately.
>
> — [machine.adoc, lines 2594-2599](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/machine.adoc#L2594-L2599)

A store to a read-write register writes it, so after a store of a value to `mtime` the counter should continue from that value, and `time` should show it once the change has reached the CSR. The third cell shows that the wait is long enough for a value to reach `time`.

The two devices have different roles. TIMER holds `mtimecmp` and compares it with its copy of SYSCNT's counter, and its `mtime` returns that copy and ignores a store. SYSCNT holds the counter, and a store to its `mtime` stays in `time_sw` until software writes `timeswReq`. The question is which address XiangShan means as the platform's `mtime`: if it is SYSCNT's, a single store reaches only `time_sw`, not the counter, and software has to know to write `timeswReq` as well; if it is TIMER's, it is read-only.

### Environment

- Hardware
  - Memory (GB): 256, with 16 cores, for the build and the runs, in one job.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container the emulator ran in.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17.
- Repo
  - XiangShan commit id: [`e516245046234ba958fd69cf95241e77278a1c43`](https://github.com/OpenXiangShan/XiangShan/commit/e516245046234ba958fd69cf95241e77278a1c43); the emulator prints `Core  0's Commit SHA is: e516245046, dirty: 0`. The `difftest` submodule is at [`99f77ff455766d18cd05927b144641bd9744b19a`](https://github.com/OpenXiangShan/difftest/commit/99f77ff455766d18cd05927b144641bd9744b19a), the commit [`e516245046`](https://github.com/OpenXiangShan/XiangShan/commit/e516245046234ba958fd69cf95241e77278a1c43) pins.
  - NEMU commit id (if difftest failed with NEMU): not used; the run passes `--no-diff`.
- Build & Run
  - Build command: see "To Reproduce". The emulator was built with `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
  - Run command (if applicable): `./build/emu -i mtime2_U_uartlite.elf --no-diff --seed 1 --max-cycles 1000000 --ram-size 256MB`.

### To Reproduce

1. Build the emulator at the commit under "Environment". This is the command we ran, with the cluster's absolute paths shortened:

```sh
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" FIRTOOL=/path/to/firtool -j16
```

firtool was version 1.149.0, the version the head's Chisel declares; the command passes it through the Makefile's `FIRTOOL` variable. The command also set `CXX`, `LINK` and `AR` to the GCC 15.2.0 toolchain named under "Environment", and `PGO_CFLAGS` and `PGO_LDFLAGS` to header and library directories for the native dependencies; those five are left out above. `OPT_FAST` is set because difftest's default at this commit, `-O3 -fno-slp-vectorize`, is a clang option that GCC rejects; `-fno-tree-slp-vectorize` is GCC's name for the same pass. `SIM_VFLAGS` splits Verilator's output more finely than difftest's default.

2. Assemble and link the program and linker script below, then run it:

```sh
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -c -o mtime2_U_uartlite.o mtime2_U_uartlite.S
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T mtime2.ld -o mtime2_U_uartlite.elf mtime2_U_uartlite.o
```

The ELF we ran was built with `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0`; its sha256 is `02b8057f0d4e1e10b4948e22d2da4609b9fe733137f3307667dc0fa656dfd3f1`.

```sh
./build/emu -i mtime2_U_uartlite.elf --no-diff --seed 1 --max-cycles 1000000 --ram-size 256MB
```

The program prints `W2_STEP <cell>` and then `W2_VAL <time before> <time after the wait> <load of the address>` for each cell, in the table's order, and ends on its last instruction with `HIT GOOD TRAP`, which means only that it finished.

<details>
<summary><code>mtime2_U_uartlite.S</code></summary>

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
    la t0, m_trap
    csrw mtvec, t0
    csrw medeleg, zero
    csrw mideleg, zero
    csrw mie, zero
    csrsi 0x744, 8
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    li t0, -1
    srli t0, t0, 10
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    la s3, main_end
    li a0, 87
    jal ra, putchar
    li a0, 50
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
    li a0, 0x4c4f574d54320000
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, main_end
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li s6, 0x5e5e5e5e5e5e5e5e
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 83
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, cell_0_back
    csrr s4, time
    li t0, 0x3800bff8
    li t1, 0x123456700000000
    sd t1, 0(t0)
    fence iorw, iorw
    li t3, 20000
mt2_tmr_wait:
    addi t3, t3, -1
    bnez t3, mt2_tmr_wait
    csrr s5, time
    ld s6, 0(t0)
cell_0_back:
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 86
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 76
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s6
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, main_end
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li s6, 0x5e5e5e5e5e5e5e5e
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 83
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 1
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, cell_1_back
    csrr s4, time
    li t0, 0x3804bff8
    li t1, 0x123456700000000
    sd t1, 0(t0)
    fence iorw, iorw
    li t3, 20000
mt2_sys_wait:
    addi t3, t3, -1
    bnez t3, mt2_sys_wait
    csrr s5, time
    ld s6, 0(t0)
cell_1_back:
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 86
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 76
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s6
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, main_end
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li s6, 0x5e5e5e5e5e5e5e5e
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 83
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 2
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, cell_2_back
    csrr s4, time
    li t0, 0x3804bff8
    li t1, 0x123456700000000
    sd t1, 0(t0)
    li t2, 0x3804c010
    li t1, 1
    sd t1, 0(t2)
    fence iorw, iorw
    li t3, 20000
mt2_sw_wait:
    addi t3, t3, -1
    bnez t3, mt2_sw_wait
    csrr s5, time
    ld s6, 0(t0)
cell_2_back:
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 86
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 76
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s6
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
main_end:
    li a0, 87
    jal ra, putchar
    li a0, 50
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
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    li a0, 0
    jal ra, finish
halt:
    j halt

.balign 4

.balign 64
m_trap:
    csrr s10, mcause
    csrr s11, mepc
    csrr s9, mtval
    csrr s8, mtval2
    csrr s7, mtinst
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 82
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s10
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s11
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s9
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s8
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s7
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    li t0, 0x1800
    csrs mstatus, t0
    li t0, 1
    slli t0, t0, 39
    csrc mstatus, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    csrw mepc, s3
    mret

puthex64:
    mv s1, ra
    mv t4, a0
    li t5, 60
1:  srl a0, t4, t5
    andi a0, a0, 15
    li t6, 10
    bltu a0, t6, 2f
    addi a0, a0, 87
    j 3f
2:  addi a0, a0, 48
3:  jal ra, putchar
    addi t5, t5, -4
    bgez t5, 1b
    mv ra, s1
    ret

putchar:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret
finish:
    .word 0x0000006b
    j finish

.section .data,"aw",@progbits
.balign 64
scratch:
    .dword 0
```

</details>

<details>
<summary><code>mtime2.ld</code></summary>

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

**When it changed.** [#4991](https://github.com/OpenXiangShan/XiangShan/pull/4991) (`feat(CLINT): add new clint to match 1:1 ration between CLINT and HART`), which reached `kunminghu-v3` as commit [`a37e46f128`](https://github.com/OpenXiangShan/XiangShan/commit/a37e46f1288df11f7319545428aebc4cb6c32110) through [#5250](https://github.com/OpenXiangShan/XiangShan/pull/5250), replaced rocket-chip's CLINT with TIMER and SYSCNT. We built that commit and its parent with `DefaultConfig` and ran this program: at the parent a single store of the same value to `0x3800bff8` set the timer, and at the commit neither store did.

Related, and not this bug:

- [#4991](https://github.com/OpenXiangShan/XiangShan/pull/4991) introduced TIMER and SYSCNT; its description lists the two devices and says nothing about writing `mtime`.
- [#6372](https://github.com/OpenXiangShan/XiangShan/issues/6372) is about the clock-domain crossing of SYSCNT's counter for reads.
- [#6451](https://github.com/OpenXiangShan/XiangShan/issues/6451) reports that with a large `MaxHartIdBits` the last `mtimecmp` overlaps TIMER's `mtime` at `0xbff8`.

None of them reports a store to `mtime` that does not change the counter.

**Scope.** The program ran once on `DefaultConfig` at the commit under "Environment" with no local change, in M-mode, with 64-bit stores and loads, in an emulator that carries DiffTest's trap event, with its comparison against a reference model off (`--no-diff`). The `mtimecmp` registers and the machine timer interrupt were not exercised.

**AI assistance.** This report was prepared with Claude Opus 5.5 (Anthropic) in Claude Code.
