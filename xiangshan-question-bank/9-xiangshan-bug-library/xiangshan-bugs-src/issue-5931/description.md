### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found an RVV fault-only-first segment load testcase where XiangShan Kunminghu v3 loads incorrect active destination data. The mismatch is on active field data produced by the `vlseg2e32ff.v` instruction itself.

At the mismatch point, the testcase executes:

```asm
li x12, 2415919058
vlseg2e32ff.v v20, (x12)
```

In the disassembly, `x12` is printed as ABI register `a2`:

```asm
80000280: 0090061b    addiw   a2,zero,9
80000284: 0672        slli    a2,a2,0x1c
80000286: fd260613    addi    a2,a2,-46
8000028a: 23066a07    vlseg2e32ff.v v20,(a2)
```

So the load base address is:

```text
a2 = 0x000000008fffffd2
```

The testcase initializes memory starting at `0x8fffffb8`. One relevant store is:

```asm
li x10, 0xc0c7072d21a3ba47
sd x10, 24(x9)
```

Since `x9 = 0x8fffffb8`, this writes the 64-bit word at `0x8fffffd0`. In little-endian byte order:

```text
0x8fffffd0 = 0x47
0x8fffffd1 = 0xba
0x8fffffd2 = 0xa3
0x8fffffd3 = 0x21
0x8fffffd4 = 0x2d
0x8fffffd5 = 0x07
0x8fffffd6 = 0xc7
0x8fffffd7 = 0xc0
```

Therefore field0 element0 of `vlseg2e32ff.v v20, (a2)` should load the 32-bit little-endian value from `0x8fffffd2`:

```text
0x8fffffd2..0x8fffffd5 = a3 21 2d 07
expected field0 element0 = 0x072d21a3
```

Spike/reference reports this value in the low 32 bits of `v20_low`:

```text
v20_low = 0xee011041072d21a3
```

But XiangShan reports:

```text
v20_low = 0xee01104160dd92a3
```

The low 32-bit field0 element is corrupted from:

```text
0x072d21a3
```

to:

```text
0x60dd92a3
```

The wrong value is not random. It matches bytes from a nearby later store:

```asm
li x13, 0x60dd92a3ecf5bcd7
li x8, 2415919059
sd x13, 11(x8)
```

Here `x8 = 0x8fffffd3`, so this store writes `0x60dd92a3` into the word ending at nearby higher addresses. This suggests that `vlseg2e32ff.v` is taking field0 element0 from the wrong byte lane or offset.

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
    li x13, 0x60dd92a3ecf5bcd7
    li x14, 0x6a81406fbfd26f3e
    li x15, 0x5d4c83e7a011bab6
    li x16, 0xfffffffffffff800
    li x17, 0x5d512aea8841af0
    li x18, 0xfbe0dbb053f81b37
    li x19, 0xffffffffffffff7f
    li x20, 0xffffffffffffff80
    vsetvli x9, x0, e64, m1
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v13, x8
    li x8, 0xe8f875b260d620f1
    vmv.v.x v14, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v15, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v16, x8
    li x8, 0x43a795d7e6d6f1bf
    vmv.v.x v17, x8
    li x8, 0x41ff7079b17b6979
    vmv.v.x v18, x8
    li x8, 0x38a982ab6018bf71
    vmv.v.x v19, x8
    li x8, 0xf0f0f0f0f0f0f0f
    vmv.v.x v20, x8
    li x9, 0x8fffffb8
    li x10, 0xdea0cf615541b173
    sd x10, 0(x9)
    li x10, 0xb89d35a5ad6c0955
    sd x10, 8(x9)
    li x10, 0xc35ee50de2d66c47
    sd x10, 16(x9)
    li x10, 0xc0c7072d21a3ba47
    sd x10, 24(x9)
    li x10, 0x279bee011041d6e8
    sd x10, 32(x9)
    li x10, 0x5a722c65a16d457c
    sd x10, 40(x9)
    li x10, 0x10000
    sd x10, 48(x9)
    li x10, 0x1
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

    li x8, 2415919059
    sd x13, 11(x8)
    mulw x15, x15, x15
    divu x18, x19, x13
    mulhu x14, x20, x17
    li x12, 2415919058
    vlseg2e32ff.v v20, (x12)
    li x11, 2415919058
    sh x14, 15(x11)
    vmaxu.vx v20, v15, x17, v0.t
    li x17, -7265179862056305209
    csrrs x13, mscratch, x17
    fence.i
    vwmacc.vx v14, x13, v19
    li x13, 2276710301682653949
    csrrs x18, mscratch, x13
    srl x16, x17, x18
    fence.i
    vslideup.vx v15, v19, x17, v0.t
    c.mv x16, x18
    li x11, 2415919061
    lh x13, 3(x11)
    li x10, 2415919045
    vssseg8e16.v v16, (x10), x19, v0.t
    c.mv x15, x17
    sub x16, x14, x14
    vasubu.vx v20, v20, x17, v0.t
    li x9, 2415919051
    vsuxseg2ei16.v v20, (x9), v19
    divw x18, x19, x16
    vwsub.vv v13, v13, v13
    fence.i
    fence.i
    vfncvt.xu.f.w v16, v20, v0.t
    vfsqrt.v v16, v20, v0.t
    lui x14, 4096

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
pc: 0x000000008000028e mstatus: 0x8000000a00006600 mcause: 0x0000000000000000 mepc: 0x0000000000000000
mtval: 0x0000000000000000
v20_low different at pc = 0x008000028a, right = 0xee011041072d21a3, wrong = 0xee01104160dd92a3
Core 0: ABORT at pc = 0x36217e856c81
```

Diff log and testcase: 

[program.zip](https://github.com/user-attachments/files/27561835/program.zip)

### Expected behavior

`vlseg2e32ff.v` should load active field elements from memory correctly.

For this testcase, field0 element0 is loaded from base address `0x8fffffd2`, so it should be:

```text
0x072d21a3
```

The implementation should not change it to:

```text
0x60dd92a3
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

Observe the mismatch at `vlseg2e32ff.v`:

```text
v20_low different at pc = 0x008000028a, right = 0xee011041072d21a3, wrong = 0xee01104160dd92a3
Core 0: ABORT at pc = 0x36217e856c81
```

### Additional context

NOTE: This bug was found on v3 and not reproduced on v2. Considering v3 is under active refactoring, the test cases can be used as regression tests to ensure the issue is fixed and does not regress in the future version.
