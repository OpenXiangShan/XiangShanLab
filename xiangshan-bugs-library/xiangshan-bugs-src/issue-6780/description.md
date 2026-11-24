### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

# Description

On `kunminghu-v3`, the vector segmented load instruction `vlseg2e8.v v31, (a0)` retires normally with `SEW=8` and `LMUL=1`, without raising an illegal-instruction exception.

Here, `nf=1` means `NFIELDS=2`.
Because `EEW=SEW=8`, each field has `EMUL=1`, so the destination register group should consist of `v31` and `v32`.
RISC-V Vector 1.0 specifies that a segment load/store encoding is reserved if the vector register numbers accessed by the instruction would increment past 31.
Therefore, this test case should raise an illegal-instruction exception.

### Expected behavior

The core instruction sequence is:
```asm
li      t0, (3 << 9)
csrs    mstatus, t0
vsetivli zero, 0, e8, m1, ta, ma
la      a0, test_data
faulting_instruction:
  vlseg2e8.v v31, (a0)
```

`vlseg2e8.v v31, (a0)` should raise an illegal-instruction exception and produce:
```text
mcause = 2
mepc   = 0x8000002c
mtval  = 0x22050f87
```

### Environment

- Hardware
  - CPU: AMD x86
  - Memory (GB): 12GB
  - Storage (GB): 1024GB
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
  - XiangShan commit id: `b716aade5550b6c7ba21c199bc917e8275140bba`
  - NEMU commit id (if difftest failed with NEMU): `513786c4c597dd628c6126144937372d6f7fc92e`
  - SPIKE commit id (if difftest failed with SPIKE): `20feb9c2bf2a7deab964d8190b0cbd4b4131bec3`


### To Reproduce


# Test case

test.s
```asm
  .equ CAUSE_ILLEGAL_INSTRUCTION, 2

  /* XiangShan testharness custom trap: rs1 carries the result code. */
  .macro XSTRAP result_reg
    .insn r 0x6b, 0, 0, x0, \result_reg, x0
  .endm

  .option norvc
  .section .text.init,"ax",@progbits
  .align 3
  .globl _start

_start:
  la      t0, trap_handler
  csrw    mtvec, t0
  csrw    medeleg, zero
  csrw    mideleg, zero
  csrw    mie, zero

  /* Enable vector architectural state. */
  li      t0, (3 << 9)
  csrs    mstatus, t0

  /* vl=0 prevents memory accesses. Instruction legality is independent of vl. */
  vsetivli zero, 0, e8, m1, ta, ma
  la      a0, test_data

  .globl faulting_instruction
faulting_instruction:
  /* nf=1 selects two fields: v31 and nonexistent v32. This is reserved. */
  vlseg2e8.v v31, (a0)

  /* Failure 1: the processor accepted the reserved register group. */
  li      a0, 1
  j       finish

trap_handler:
  csrr    t0, mcause
  li      t1, CAUSE_ILLEGAL_INSTRUCTION
  bne     t0, t1, wrong_cause

  csrr    t0, mepc
  la      t1, faulting_instruction
  bne     t0, t1, wrong_epc

  li      a0, 0
  j       finish

wrong_cause:
  li      a0, 2
  j       finish

wrong_epc:
  li      a0, 3

finish:
  /* riscv-tests result protocol for Spike, followed by XiangShan's trap. */
  slli    t0, a0, 1
  ori     t0, t0, 1
  la      t1, tohost
  sd      t0, 0(t1)
  fence   rw, rw
  bnez    a0, test_failed
  XSTRAP  a0
1:
  j       1b
test_failed:
  j       test_failed

  .section .data,"aw",@progbits
  .align 6
test_data:
  .zero 64

  .section .tohost,"aw",@progbits
  .align 6
  .globl tohost
tohost:
  .dword 0
  .align 6
  .globl fromhost
fromhost:
  .dword 0
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

`test.s` intentionally sets `vl` to 0 to prevent any actual memory access, so the result depends only on instruction legality checking.

`test.s` installs an M-mode trap handler and checks both `mcause=2` and `mepc=faulting_instruction`.

The target instruction disassembles as:
```text
8000002c: 22050f87  vlseg2e8.v v31,(x10)
```

# Reproduction steps

Build the XiangShan simulation model:

```bash
cd /path/to/XiangShan
export NOOP_HOME="$PWD"
make emu CONFIG=MinimalConfig EMU_THREADS=2 -j10
```

For DefaultConfig:
```bash
make emu CONFIG=DefaultConfig EMU_THREADS=2 -j10
```

Build the test case:
```bash
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64d -mcmodel=medany -nostdlib -nostartfiles -static -Wl,--build-id=none -T ./link.ld ./test.s -o test.elf

riscv64-unknown-elf-objdump -d -M no-aliases,numeric test.elf > test.dump

riscv64-unknown-elf-objcopy -O binary test.elf test.bin
```

Check the architectural expectation with Spike:
```bash
./run_spike.sh
```
`test.elf` terminates normally, and the self-check passes with `tohost=1`.

Run the XiangShan Verilated model:
```bash
emu="${xiangshan_dir}/build/verilator-compile/emu"
nemu="${xiangshan_dir}/ready-to-run/riscv64-nemu-interpreter-so"

ulimit -s 32768
"${emu}" -b 0 -e 15000 -C 15000 -I 200 --dump-commit-trace -i test.bin --diff "${nemu}"
```

# XiangShan Verilated-model differential result

The current RTL incorrectly commits an instruction whose encoding should be reserved:
```text
Core  0's Commit SHA is: 6705b63685, dirty: 1
commit pc 000000008000002c inst 22050f87 wen 1 dst 31 data 0000000080002024
```

The NEMU reference model enters an M-mode illegal-instruction trap, while the DUT does not:
```text
mstatus different at pc = 0xfffe84ed8ad2581f, right = 0x8000040a00001e00, wrong = 0x8000000a00000600
   mepc different at pc = 0xfffe84ed8ad2581f, right = 0x000000008000002c, wrong = 0x0000000000000000
  mtval different at pc = 0xfffe84ed8ad2581f, right = 0x0000000022050f87, wrong = 0x0000000000000000
 mcause different at pc = 0xfffe84ed8ad2581f, right = 0x0000000000000002, wrong = 0x0000000000000000

Core 0: ABORT at pc = 0xfffe84ed8ad2581f
```

In the difftest output, `right` is NEMU and `wrong` is the XiangShan DUT.

The same test also reproduces on the `DefaultConfig` model.

### Additional context


# Root-cause analysis

Based on an initial source-code audit, the issue appears to be in `src/main/scala/xiangshan/backend/vector/Decoder/VecExceptionGen.scala`.

The ordinary destination/source register out-of-bounds checks use `vs3RegHi`:
```scala
val vs3RegHi = emulLoToHi(Reg.emulVs3, vs3RegLo)
val vs3OutOfBound = Reg.useVs3 && (vs3RegHi >= 32.U)
val vdOutOfBound  = Reg.useVd  && (vs3RegHi >= 32.U)
```

`vs3RegHi` describes only one field's EMUL-sized register group and does not include the additional fields of a segment instruction. The code subsequently computes the end register including `nf` correctly:
```scala
val vs3RegHiWithNf = emulNfLoToHi(Reg.emulVs3, Dec.nffield, vs3RegLo)
```

However, `vs3RegHiWithNf` is used only for `segmentOverlap`; it is not included in the register-group out-of-bounds conditions feeding `out.exception`. For `test.elf`:
vs3RegLo = 31
vs3RegHi = 31 -> the current vdOutOfBound is false
vs3RegHiWithNf = 32 -> the actual segment register group is already out of bounds

Therefore, the decoder misses the end-of-group bounds violation for the segment register group.

# Suggested fix

Add an out-of-bounds condition based on `vs3RegHiWithNf >= 32.U` for segment instructions and include it in `out.exception`.
