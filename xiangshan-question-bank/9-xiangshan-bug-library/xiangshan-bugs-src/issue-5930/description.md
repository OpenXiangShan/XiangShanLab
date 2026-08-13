### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found an RVV `vle8ff.v` testcase where XiangShan Kunminghu v3 loads an incorrect active byte value. The mismatch is on active destination data produced by the `vle8ff.v` instruction itself. 

At the mismatch point, the testcase executes:

```asm
li x8, 2415919058
vle8ff.v v14, (x8), v0.t
```

In the disassembly, `x8` is printed as ABI register `s0`:

```asm
8000025a: 0090041b    addiw   s0,zero,9
8000025e: 0472        slli    s0,s0,0x1c
80000260: fd240413    addi    s0,s0,-46
80000264: 01040707    vle8ff.v v14,(s0),v0.t
```

So the load base address is:

```text
s0 = 0x000000008fffffd2
```

The testcase initializes memory starting at `0x8fffffb8`. The relevant store is:

```asm
li x10, 0x2c92056bca470fac
sd x10, 24(x9)
```

Since `x9 = 0x8fffffb8`, this writes the 64-bit word at `0x8fffffd0`. In little-endian byte order:

```text
0x8fffffd0 = 0xac
0x8fffffd1 = 0x0f
0x8fffffd2 = 0x47
0x8fffffd3 = 0xca
```

Therefore the active loaded bytes from `0x8fffffd2` should begin with:

```text
0x47, 0xca
```

Spike/reference reports:

```text
v14_low = 0xaaaaaaaaaaaaca47
```

But XiangShan reports:

```text
v14_low = 0xaaaaaaaaaaaacac7
```

The first active byte is corrupted from `0x47` to `0xc7`, i.e. bit 7 is incorrectly set.

Original Code:

```asm
    .section .text
    .globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    j       user_code

user_code:
    li x13, 0x80
    li x14, 0x1490534e200a47ba
    li x15, 0xfdf7618d2496ba28
    li x16, 0xc7be21a994d6aa8f
    li x17, 0xcffdb58628089f8a
    li x18, 0xfffffffffffffffe
    li x19, 0x2210dc3ced2682e5
    li x20, 0xd9288d531a6e8a27
    vsetvli x9, x0, e64, m1
    li x8, 0x0
    vmv.v.x v13, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v14, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v15, x8
    li x8, 0x0
    vmv.v.x v16, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v17, x8
    li x8, 0x5555555555555555
    vmv.v.x v18, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v19, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v20, x8
    li x9, 0x8fffffb8
    li x10, 0x7fff
    sd x10, 0(x9)
    li x10, 0x100
    sd x10, 8(x9)
    li x10, 0x1000
    sd x10, 16(x9)
    li x10, 0x2c92056bca470fac
    sd x10, 24(x9)
    li x10, 0x666adfe29621898f
    sd x10, 32(x9)
    li x10, 0x2b07c73b2b2eeb90
    sd x10, 40(x9)
    li x10, 0xbe60ebff7a3af395
    sd x10, 48(x9)
    li x10, 0x9d41418aadb35b20
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

    srai x14, x19, 10
    c.add x20, x19
    subw x15, x13, x15
    li x2, 2415919067
    c.sdsp x16, 0(sp)
    addw x17, x17, x16
    li x9, 2415919039
    vsoxseg6ei16.v v13, (x9), v18, v0.t
    fence.i
    mulh x14, x13, x17
    ori x18, x16, -1816
    fence.i
    li x8, 2415919058
    vle8ff.v v14, (x8), v0.t
    srlw x15, x18, x19
    c.andi x14, 31
    mulhsu x14, x16, x19

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
v14_low different at pc = 0x0080000264, right = 0xaaaaaaaaaaaaca47, wrong = 0xaaaaaaaaaaaacac7
Core 0: ABORT at pc = 0xfffea59863534eea
```

Tests & logs:

[program.zip](https://github.com/user-attachments/files/27561697/program.zip)

### Expected behavior

`vle8ff.v` should load active byte elements from memory correctly.

For this testcase, the first active byte loaded from `0x8fffffd2` should be:

```text
0x47
```

The implementation should not change it to:

```text
0xc7
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

Observe the mismatch at `vle8ff.v`:

```text
v14_low different at pc = 0x0080000264, right = 0xaaaaaaaaaaaaca47, wrong = 0xaaaaaaaaaaaacac7
Core 0: ABORT at pc = 0xfffea59863534eea
```

### Additional context

NOTE: This bug was found on v3 and not reproduced on v2. Considering v3 is under active refactoring, the test cases can be used as regression tests to ensure the issue is fixed and does not regress in the future version.
