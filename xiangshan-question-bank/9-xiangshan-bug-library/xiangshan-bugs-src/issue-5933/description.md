### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found an RVV strided segment load testcase where XiangShan Kunminghu v3 loads incorrect active destination data from `vlsseg3e64.v`.

This report is based on a reduced standalone testcase that still reproduces the bug. At the mismatch point, the testcase executes:

```asm
li x8, 0x8fffffcd
vlsseg3e64.v v18, (x8), x19
```

In the disassembly, `x8` is printed as ABI register `s0` and `x19` as `s3`:

```asm
800000aa: 0090041b    addiw   s0,zero,9
800000ae: 0472        slli    s0,s0,0x1c
800000b0: fcd40413    addi    s0,s0,-51
800000b4: 4b347907    vlsseg3e64.v v18,(s0),s3
```

So the segment-load base address and stride are:

```text
s0 = 0x000000008fffffcd
s3 = 0x0000000000000000
```

The testcase initializes memory starting at `0x8fffffb8`. The relevant stores are:

```asm
li x10, 0x3803444b2551a2ae
sd x10, 16(x9)
li x10, 0x1000
sd x10, 24(x9)
```

Since `x9 = 0x8fffffb8`, the bytes at the strided load base are:

```text
0x8fffffcd = 0x44
0x8fffffce = 0x03
0x8fffffcf = 0x38
0x8fffffd0 = 0x00
0x8fffffd1 = 0x10
0x8fffffd2 = 0x00
0x8fffffd3 = 0x00
0x8fffffd4 = 0x00
```

These reconstruct to:

```text
0x0000001000380344
```

Spike/reference reports:

```text
v18_low  = 0x0000001000380344
v18_high = 0x0000001000380344
```

But XiangShan reports:

```text
v18_low  = 0x0000001000394444
v18_high = 0x0000001000394444
```

The bytes are corrupted from:

```text
44 03 38 00 10 00 00 00
```

to:

```text
44 44 39 00 10 00 00 00
```

The reduced testcase also contains one earlier trapped instruction:

```asm
8000008a: 4b2a16d7    vfncvt.f.f.w v13, v18
```

XiangShan logs:

```text
exception pc 000000008000008a inst 4b2a16d7 cause 0000000000000002
```

The trap handler advances `mepc` and execution resumes. The first architectural data mismatch still appears later at `vlsseg3e64.v`, so this is not just the earlier illegal-instruction trap itself.

Reduced standalone testcase:

```asm
.section .text.init
.globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    li      x13, 0xfe7bd4046a923944
    li      x18, 0

    vsetvli x9, x0, e64, m1
    li      x8, 0x75b034a797aff2dc
    vmv.v.x v17, x8
    li      x8, -1
    vmv.v.x v18, x8

    li      x9, 0x8fffffb8
    li      x10, 0x3803444b2551a2ae
    sd      x10, 16(x9)
    li      x10, 0x1000
    sd      x10, 24(x9)

    vfncvt.f.f.w v13, v18
    remuw   x19, x18, x13
    vmv.x.s x15, v17
    li      x12, 0x8fffffbe
    sw      x13, 0(x12)
    fence.i
    sltu    x19, x15, x19
    li      x8, 0x8fffffcd
    vlsseg3e64.v v18, (x8), x19

exit:
    li      t0, 1
    la      t1, tohost
    sd      t0, 0(t1)
1:
    j       1b

    .align  2
trap_handler:
    csrr    t0, mepc
    csrr    t1, mcause
    csrr    t4, mtval

    slli    t5, t1, 1
    srli    t1, t5, 1

    li      t2, 2
    li      t3, 2
    beq     t1, t3, use_mtval
    li      t3, 1
    beq     t1, t3, update_mepc
    li      t3, 12
    beq     t1, t3, update_mepc

    lhu     t4, 0(t0)
    j       decode_length

use_mtval:
    j       decode_length

decode_length:
    andi    t4, t4, 3
    li      t3, 3
    bne     t4, t3, compressed_len
    li      t2, 4
    j       update_mepc

compressed_len:
    li      t2, 2

update_mepc:
    add     t0, t0, t2
    csrw    mepc, t0
    csrw    mcause, x0
    csrw    mtval, x0
    mret

    .section .tohost,"aw",@progbits
    .align  6
    .globl  tohost
    .globl  fromhost
tohost:
    .dword  0
fromhost:
    .dword  0
```

Observed XiangShan diff log:

```text
exception pc 000000008000008a inst 4b2a16d7 cause 0000000000000002
v18_low different at pc = 0x00800000b4, right = 0x0000001000380344, wrong = 0x0000001000394444
v18_high different at pc = 0x00800000b4, right = 0x0000001000380344, wrong = 0x0000001000394444
Core 0: ABORT at pc = 0x422f9d80c39a
```

Diff log and testcase:

[program.zip](https://github.com/user-attachments/files/27566865/program.zip)

### Expected behavior


`vlsseg3e64.v` should load active destination data from the base/stride-derived addresses correctly. For this testcase, the first reconstructed bytes at the load base are:

```text
44 03 38 00 10 00 00 00
```

which should produce:

```text
0x0000001000380344
```

The implementation should not corrupt them to:

```text
0x0000001000394444
```

### Environment

XiangShan Kunminghu v3:

```text
Git commit: 1e64239649566a1217ea26efad20ace05771cb11
Core 0's Commit SHA is: 1e64239649, dirty: 1
emu compiled at May 8 2026, 16:58:47
```

### To Reproduce


Run XiangShan Kunminghu v3 with difftest:

```bash
./build/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so
```

Observe the strided segment-load data mismatch:

```text
v18_low different at pc = 0x00800000b4, right = 0x0000001000380344, wrong = 0x0000001000394444
v18_high different at pc = 0x00800000b4, right = 0x0000001000380344, wrong = 0x0000001000394444
```

### Additional context

NOTE: This bug was found on v3 and not reproduced on v2. Considering v3 is under active refactoring, the test cases can be used as regression tests to ensure the issue is fixed and does not regress in the future version.
