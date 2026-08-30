### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found an RVV masked segment load testcase where XiangShan Kunminghu v3 corrupts an active destination element in `v14` during `vlseg2e16.v`. At the mismatch point, the testcase executes:

```asm
li x9, 0x8fffffca
vlseg2e16.v v13, (x9), v0.t
```

In the disassembly, `x9` is printed as ABI register `s1`:

```asm
8000008a: 0090049b    addiw   s1,zero,9
8000008e: 04f2        slli    s1,s1,0x1c
80000090: fca48493    addi    s1,s1,-54
80000094: 2004d687    vlseg2e16.v v13,(s1),v0.t
```

So the segment-load base address is:

```text
s1 = 0x000000008fffffca
```

Before the load, `v14` is initialized to all ones:

```asm
li x8, -1
vmv.v.x v14, x8
```

The vector policy is:

```asm
vsetvli x9, x0, e64, m1
```

which yields `tu,mu` in the disassembly, so an inactive or undisturbed 16-bit lane would remain `0xffff`.

The testcase initializes memory starting at `0x8fffffb8`. One relevant store is:

```asm
li x10, 0x9ff597b54a2e1009
sd x10, 16(x9)
```

Since `x9 = 0x8fffffb8`, this writes:

```text
0x8fffffc8 = 0x09
0x8fffffc9 = 0x10
0x8fffffca = 0x2e
0x8fffffcb = 0x4a
0x8fffffcc = 0xb5
0x8fffffcd = 0x97
0x8fffffce = 0xf5
0x8fffffcf = 0x9f
```

The reduced testcase then explicitly overwrites `0x8fffffce .. 0x8fffffd5` with zeros:

```asm
li x14, 0
li x2, 0x8fffffbe
sd x14, 16(x2)
```

So at the time of `vlseg2e16.v v13, (s1), v0.t`, the relevant bytes are:

```text
0x8fffffca = 0x2e
0x8fffffcb = 0x4a
0x8fffffcc = 0xb5
0x8fffffcd = 0x97
0x8fffffce = 0x00
0x8fffffcf = 0x00
0x8fffffd0 = 0x00
0x8fffffd1 = 0x00
```

Under the testcase's mask state, Spike/reference reports:

```text
v14_low = 0xffffffff000097b5
```

But XiangShan reports:

```text
v14_low = 0xffffffff3ac197b5
```

So the second loaded 16-bit field in `v14_low` is corrupted from:

```text
0x0000
```

to:

```text
0x3ac1
```


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

    li      x13, 0
    li      x16, 0x35b8c53e3930a213

    vsetvli x9, x0, e64, m1
    li      x8, -1
    vmv.v.x v14, x8

    li      x9, 0x8fffffb8
    li      x10, 0x9ff597b54a2e1009
    sd      x10, 16(x9)

    li      x14, 0
    li      x2, 0x8fffffbe
    sd      x14, 16(x2)
    sub     x20, x13, x16
    li      x2, 0x8fffffdc
    sd      x20, 0(x2)

    li      x9, 0x8fffffca
    vlseg2e16.v v13, (x9), v0.t

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
v14_low different at pc = 0x0080000094, right = 0xffffffff000097b5, wrong = 0xffffffff3ac197b5
Core 0: ABORT at pc = 0x92e40c326de9
```

Diff log and testcase:  [program.zip](https://github.com/user-attachments/files/27566985/program.zip)

### Expected behavior

`vlseg2e16.v` should load active destination elements from memory correctly under the testcase's mask state.

For this reduced testcase, Spike/reference reports:

```text
v14_low = 0xffffffff000097b5
```

So the implementation should not corrupt the second loaded 16-bit field to:

```text
0x3ac1
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

Observe the masked segment-load data mismatch:

```text
v14_low different at pc = 0x0080000094, right = 0xffffffff000097b5, wrong = 0xffffffff3ac197b5
```

### Additional context

NOTE: This bug was found on v3 and not reproduced on v2. Considering v3 is under active refactoring, the test cases can be used as regression tests to ensure the issue is fixed and does not regress in the future version.
