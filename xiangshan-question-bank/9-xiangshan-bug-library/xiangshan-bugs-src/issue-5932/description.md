### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found an RVV indexed segment load testcase where XiangShan Kunminghu v3 corrupts active destination data produced by `vluxseg5ei32.v`.

This report is based on a reduced standalone testcase that still reproduces the bug. At the mismatch point, the testcase executes:

```asm
li x11, 0x8fffffde
vluxseg5ei32.v v18, (x11), v14
```

In the disassembly, `x11` is printed as ABI register `a1`:

```asm
800000c8: 0090059b    addiw   a1,zero,9
800000cc: 05f2        slli    a1,a1,0x1c
800000ce: fde58593    addi    a1,a1,-34
800000d2: 86e5e907    vluxseg5ei32.v v18,(a1),v14
```

So the indexed segment-load base address is:

```text
a1 = 0x000000008fffffde
```

Before the load, the reduced testcase initializes `v14` to zero:

```asm
li x8, 0
vmv.v.x v14, x8
```

so the first indexed addresses come directly from the base.

The testcase initializes memory starting at `0x8fffffb8`. The relevant stores are:

```asm
li x10, 0xe0672377e7f6c7df
sd x10, 32(x9)
li x10, 0x416d1e6234c1864f
sd x10, 40(x9)
```

Since `x9 = 0x8fffffb8`, these stores place the following bytes at the load base:

```text
0x8fffffde = 0x67
0x8fffffdf = 0xe0
0x8fffffe0 = 0x4f
0x8fffffe1 = 0x86
0x8fffffe2 = 0xc1
0x8fffffe3 = 0x34
0x8fffffe4 = 0x62
0x8fffffe5 = 0x1e
```

So the first loaded 64-bit value should be:

```text
0x1e6234c1864fe067
```

Spike/reference reports:

```text
v18_low  = 0x1e6234c1864fe067
v18_high = 0x1e6234c1864fe067
```

But XiangShan reports:

```text
v18_low  = 0x1e6234c1864f0008
v18_high = 0x1e6234c1864f0008
```

The low 16 bits are corrupted from:

```text
0xe067
```

to:

```text
0x0008
```

The reduced testcase also contains one earlier trapped instruction:

```asm
800000c0: c7181a57    vfwredusum.vs v20, v17, v16
```

XiangShan logs:

```text
exception pc 00000000800000c0 inst c7181a57 cause 0000000000000002
```

The trap handler advances `mepc` and execution resumes. The first architectural data mismatch still appears later at `vluxseg5ei32.v`, so this is not just the earlier illegal-instruction trap itself.

Original Code:

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

    vsetvli x9, x0, e64, m1
    li      x8, 0
    vmv.v.x v14, x8
    li      x8, 0x0f0f0f0f0f0f0f0f
    vmv.v.x v15, x8
    li      x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v16, x8

    li      x9, 0x8fffffb8
    li      x10, 0xe0672377e7f6c7df
    sd      x10, 32(x9)
    li      x10, 0x416d1e6234c1864f
    sd      x10, 40(x9)

    li      x13, 0x800
    vwadd.vx v17, v15, x13
    li      x2, 0x8fffffc5
    sw      x13, 8(x2)
    vfwredusum.vs v20, v17, v16
    fence.i

    li      x11, 0x8fffffde
    vluxseg5ei32.v v18, (x11), v14

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
exception pc 00000000800000c0 inst c7181a57 cause 0000000000000002
v18_low different at pc = 0x00800000d2, right = 0x1e6234c1864fe067, wrong = 0x1e6234c1864f0008
v18_high different at pc = 0x00800000d2, right = 0x1e6234c1864fe067, wrong = 0x1e6234c1864f0008
Core 0: ABORT at pc = 0x1abe274928cd6
```

Diff log and testcase: 

[program.zip](https://github.com/user-attachments/files/27566684/program.zip)

### Expected behavior

`vluxseg5ei32.v` should load active destination data from the indexed addresses correctly.

For this reduced testcase, the first loaded 64-bit value should be:

```text
0x1e6234c1864fe067
```

The implementation should not corrupt it to:

```text
0x1e6234c1864f0008
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

Observe the indexed segment-load data mismatch:

```text
v18_low different at pc = 0x00800000d2, right = 0x1e6234c1864fe067, wrong = 0x1e6234c1864f0008
v18_high different at pc = 0x00800000d2, right = 0x1e6234c1864fe067, wrong = 0x1e6234c1864f0008
```


### Additional context

NOTE: This bug was found on v3 and not reproduced on v2. Considering v3 is under active refactoring, the test cases can be used as regression tests to ensure the issue is fixed and does not regress in the future version.
