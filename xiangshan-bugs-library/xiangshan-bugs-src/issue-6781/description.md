### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


On `kunminghu-v3`, XiangShan incorrectly treats the following legal instruction sequence as an illegal instruction:
```asm
vsetivli zero, 0, e8, m1, ta, ma
vluxseg2ei64.v v8, (a0), v16
```

Here, `SEW=8`, `LMUL=1`, the index EEW is 64, and NFIELDS is 2.

According to the indexed segment rules in RISC-V Vector 1.0:
```text
data EMUL = LMUL = 1
data group size = data EMUL * NFIELDS = 2
index EMUL = (index EEW / SEW) * LMUL = 8
```

The data group is `v8-v9`, and the index group is `v16-v23`. Both groups are properly aligned, remain in bounds, and do not overlap.

The specification explicitly states that the `EMUL × NFIELDS <= 8` constraint applies to the data register group, while there is only one index register group. Therefore, this encoding is legal.
Specification: <https://docs.riscv.org/reference/isa/unpriv/v-st-ext.html#vector-indexed-segment-loads-and-stores>

I searched the upstream issues to avoid filing a duplicate.
Issue #6486 concerns incorrect segment-store data, so both its root cause and observed behavior are different.

This report consistently reproduces a narrowly scoped decode-table legality-constraint issue and identifies the specific calculation involved.
Perhaps this report can also serve as a directed regression test for subsequent indexed-segment development.

### Expected behavior

The instruction should not raise an illegal-instruction exception.

Because `vl=0`, it should perform no memory access and should proceed normally to the following instruction.

### Environment

- Hardware
  - CPU: AMD x86
  - Memory (GB): 16 GB
  - Storage (GB): 1024 GB
- Software
  - Operating system: Ubuntu 24.04
  - docker simulation environment: ghcr.io/openxiangshan/xsdev:kunminghu-v3 (c5dffb1e5d7c)
  - gcc version
```
$ riscv64-unknown-elf-gcc --version

riscv64-unknown-elf-gcc (g04696df09) 14.2.0
Copyright (C) 2024 Free Software Foundation, Inc.
This is free software; see the source for copying conditions.  There is NO
warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
```
- Repo
  - XiangShan commit id: b716aade5550b6c7ba21c199bc917e8275140bba
  - NEMU commit id (if difftest failed with NEMU): 513786c4c597dd628c6126144937372d6f7fc92e
  - SPIKE commit id (if difftest failed with SPIKE): 20feb9c2bf2a7deab964d8190b0cbd4b4131bec3


### To Reproduce

Test case

```asm
  .option norvc
  .section .text.init,"ax",@progbits
  .align 3
  .globl _start

  /* XiangShan testharness custom trap: rs1 carries the result code. */
  .macro XSTRAP result_reg
    .insn r 0x6b, 0, 0, x0, \result_reg, x0
  .endm

_start:
  la      t0, trap_handler
  csrw    mtvec, t0
  csrw    medeleg, zero
  csrw    mideleg, zero
  csrw    mie, zero

  /* Enable vector state in mstatus.VS. */
  li      t0, (3 << 9)
  csrs    mstatus, t0

  /* Data: SEW=8, LMUL=1. Index: EEW=64, hence index EMUL=8. */
  vsetivli zero, 0, e8, m1, ta, ma
  la      a0, test_data

  .globl tested_instruction
tested_instruction:
  /* Legal: index group v16-v23; two data fields v8-v9. */
  vluxseg2ei64.v v8, (a0), v16

  /* The legal instruction completed without an exception. */
  li      a0, 0
  j       finish

trap_handler:
  /* Any exception means that the legal instruction was rejected. */
  li      a0, 1

finish:
  /* riscv-tests/Spike tohost convention: odd value, status in upper bits. */
  slli    t0, a0, 1
  ori     t0, t0, 1
  la      t1, tohost
  sd      t0, 0(t1)
  fence   rw, rw

  /* Only a successful self-check may terminate as a good trap. */
  bnez    a0, test_failed
  XSTRAP  a0
test_failed:
1:
  j       1b

  .section .data,"aw",@progbits
  .align 6
test_data:
  .zero 64

  .section .tohost,"aw",@progbits
  .align 6
  .globl tohost
tohost: .dword 0
  .align 6
  .globl fromhost
fromhost: .dword 0
```

The test intentionally sets `vl=0`, preventing the legal instruction from accessing memory so that the difference depends only on the instruction-legality check. The target instruction disassembles as:

```text
8000002c: 27057407  vluxseg2ei64.v v8,(x10),v16
```

link.ld
```
OUTPUT_ARCH("riscv")
ENTRY(_start)

SECTIONS
{
  . = 0x80000000;
  .text : { *(.text.init) *(.text .text.*) }

  . = ALIGN(0x1000);
  .tohost : { *(.tohost) }

  . = ALIGN(0x1000);
  .data : { *(.data .data.*) *(.rodata .rodata.*) }
  .bss : { *(.bss .bss.*) *(COMMON) }
}
```


### Build commands

The linker script places the entry point at `0x80000000` and provides `tohost/fromhost`. Assuming it is saved as `link.ld`:

```bash
TOOLCHAIN=/path/to/riscv64-unknown-elf

"${TOOLCHAIN}-gcc" -march=rv64gcv -mabi=lp64d -mcmodel=medany -nostdlib -nostartfiles -static -Wl,--build-id=none -T link.ld test.s -o test.elf
"${TOOLCHAIN}-objcopy" -O binary test.elf test.bin
"${TOOLCHAIN}-objdump" -d -M no-aliases,numeric test.elf > test.dump
```

### Spike result

```bash
spike --isa=rv64gcv_zicsr_zifencei test.elf
echo $?
```

Result:
```text
0
```

Spike accepts the target instruction, after which the self-check writes the success status. This provides an independent architectural reference result.

### Reproducing the XiangShan differential result

Build the `MinimalConfig` model:

```bash
cd /path/to/XiangShan
export NOOP_HOME="$PWD"
make emu CONFIG=MinimalConfig EMU_THREADS=2 -j10
```

Run the Verilated model with NEMU difftest:

```bash
ulimit -s 32768
./build/emu -b 0 -e 0 -C 50000 -i /path/to/test.bin --diff ./ready-to-run/riscv64-nemu-interpreter-so
```

### Actual result

XiangShan raises an illegal-instruction exception at the target instruction:
```text
[17] exception pc 000000008000002c inst 27057407 cause 0000000000000002
```

NEMU proceeds normally to the following instruction, while the DUT updates its exception CSRs.

Difftest reports:
```text
mstatus different at pc = 0xfffeea97c5d3ee86, right = 0x8000000a00000600, wrong = 0x8000040a00001e00
   mepc different at pc = 0xfffeea97c5d3ee86, right = 0x0000000000000000, wrong = 0x000000008000002c
  mtval different at pc = 0xfffeea97c5d3ee86, right = 0x0000000000000000, wrong = 0x0000000027057407
 mcause different at pc = 0xfffeea97c5d3ee86, right = 0x0000000000000000, wrong = 0x0000000000000002
Core 0: ABORT at pc = 0xfffeea97c5d3ee86
```

Here, `right` is the NEMU reference model and `wrong` is the XiangShan DUT.

Spike also independently accepts the same ELF, so both reference models agree on the difference from the DUT.

### Additional context


### Root-cause analysis

Based on an initial source-code audit, the issue first appears in the indexed-memory pattern enumeration in `src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/VectorDecodeChannel.scala`:
```scala
case _ if vmi.isInstanceOf[VecMemIndex] =>
  val eew = vmi.eewValue
  for {
    sewlmul <- SewLmulPattern.all
    nf <- NfPattern.all
    sew = sewlmul.p1.sewValue
    lmul = sewlmul.p2.lmulValue
    iEmul = lmul * eew / sew
    uopNum = ((iEmul max lmul) max 1.0).toInt * nf.segNum
    if iEmul >= 0.125 && uopNum <= 8
  } yield { vi ## sewlmul ## nf }
```

The same calculation also appears in `src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/VecDecodeChannel/UopInfoField.scala`:
```scala
val iEmul = emul
val dEmul = lmul
val uopNum = (emul max lmul max 1.0).toInt * seg
if (emul >= 0.125 && uopNum <= 8) {
  // generate indexed-memory uops
} else {
  Seq()
}
```

For this test case, the code calculates:
```text
max(iEmul=8, lmul=1, 1) * seg=2 = 16
```

The `uopNum <= 8` condition is therefore false, so the legal combination is omitted from the decode table and is ultimately treated as an illegal instruction.

The apparent root problem is that the internal indexed-memory uop-count calculation is also used as an architectural-legality filter, with the index EMUL multiplied by the number of fields.

Each field of an indexed segment operation requires one data register group, but all fields share a single index register group.
The specification's `EMUL * NFIELDS <= 8` constraint applies only to the data group.
This case should check separately that `iEmul=8` is legal and that `dEmul*NFIELDS=2` is legal, rather than checking `iEmul*NFIELDS=16`.
