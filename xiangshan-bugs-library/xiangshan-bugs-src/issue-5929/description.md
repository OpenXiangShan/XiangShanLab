### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

I found an RVV indexed segment load testcase where XiangShan Kunminghu v2 misses a load access fault and commits destination vector register side effects. This bug is originally found on v3 branch, but I can reproduce it on v2 as well.

At the mismatch point, the testcase executes:

```asm
vsetvli x8, x0, e32, mf2
li x15, 2415919032
vmv.v.i v19, 7
vloxseg5ei64.v v20, (x15), v19
```

In the disassembly, `x15` is printed as ABI register `a5`:

```asm
800002da: 01707457    vsetvli s0,zero,e32,mf2,tu,mu
800002de: 0090079b    addiw   a5,zero,9
800002e2: 07f2        slli    a5,a5,0x1c
800002e4: fb878793    addi    a5,a5,-72
800002e8: 5e03b9d7    vmv.v.i v19,7
800002ec: 8f37fa07    vloxseg5ei64.v v20,(a5),v19
```

The reference model reports a load access fault at the indexed segment load:

```text
pc:     0x0000000080000320
mcause: 0x0000000000000005
mepc:   0x00000000800002ec
mtval:  0x000000078fffffbf
```

But XiangShan v2 does not expose the same architectural exception state. Difftest reports:

```text
mstatus different at pc = 0x0000000000, right = 0x8000040a00007e00, wrong = 0x8000000a00006600
   mepc different at pc = 0x0000000000, right = 0x00000000800002ec, wrong = 0x0000000000000000
  mtval different at pc = 0x0000000000, right = 0x000000078fffffbf, wrong = 0x0000000000000000
 mcause different at pc = 0x0000000000, right = 0x0000000000000005, wrong = 0x0000000000000000
v20_low different at pc = 0x0000000000, right = 0x3a5df9a517eb24b9, wrong = 0x0000800000000000
v21_low different at pc = 0x0000000000, right = 0xe456d0e1e625c56a, wrong = 0x0000000000000000
v22_low different at pc = 0x0000000000, right = 0xffffffffffffffff, wrong = 0xc558268a00000000
v23_low different at pc = 0x0000000000, right = 0xffffffffffffffff, wrong = 0x2bedf3b600000000
v24_low different at pc = 0x0000000000, right = 0xbecfecf6cd7f0202, wrong = 0xce97988900000000
Core 0: ABORT at pc = 0x0
```

This looks like the fault from `vloxseg5ei64.v` is missed or mishandled, and partial/incorrect destination vector register writes become visible.

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
    li x8, 0x4117a3144560f91a
    li x9, 0x2ae7c8bf78cd7009
    li x10, 0x4eeea7c6ce70406a
    li x11, 0x80
    li x12, 0xbd486c2b6b72ead9
    li x13, 0xffffffffffffff7f
    li x14, 0xffffffffffffff7f
    li x15, 0x8cb9d90fcee7476e
    li x8, 0xb0aa511753502dba
    fmv.d.x f16, x8
    li x8, 0x7ffaf76b9aa1e809
    fmv.d.x f17, x8
    li x8, 0x7ffb9eccfcc45ea5
    fmv.d.x f18, x8
    li x8, 0xf3434b49bf9cb424
    fmv.d.x f19, x8
    li x8, 0x76fab73a4ad8d4d9
    fmv.d.x f20, x8
    li x8, 0x196ddc9a0035e68f
    fmv.d.x f21, x8
    li x8, 0x8512c7158eef9134
    fmv.d.x f22, x8
    li x8, 0xbff0000000000000
    fmv.d.x f23, x8
    vsetvli x9, x0, e64, m1
    li x8, 0x5555555555555555
    vmv.v.x v16, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v17, x8
    li x8, 0xaea08d71eb5e145a
    vmv.v.x v18, x8
    li x8, 0x0
    vmv.v.x v19, x8
    li x8, 0x3a5df9a517eb24b9
    vmv.v.x v20, x8
    li x8, 0xe456d0e1e625c56a
    vmv.v.x v21, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v22, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v23, x8
    li x9, 0x8fffffb8
    li x10, 0x8000
    sd x10, 0(x9)
    li x10, 0x2bedf3b6c558268a
    sd x10, 8(x9)
    li x10, 0xe6de5bfbce979889
    sd x10, 16(x9)
    li x10, 0xa73e1a3bce6e7fd6
    sd x10, 24(x9)
    li x10, 0x6b8d0eef4eeb52e0
    sd x10, 32(x9)
    li x10, 0x778c4b7f9bdecbc5
    sd x10, 40(x9)
    li x10, 0x80000000
    sd x10, 48(x9)
    li x10, 0x1000
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

    vsetvli x8, x0, e32, mf2
    li x15, 2415919032
    vmv.v.i v19, 7
    vloxseg5ei64.v v20, (x15), v19
    vmv.s.x v18, x13
    vfcvt.xu.f.v v20, v19
    vfirst.m x10, v18
    li x12, 2415919035
    vmv.v.i v22, -9
    vsuxseg3ei32.v v21, (x12), v22, v0.t

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
pc: 0x0000000080000320 mstatus: 0x8000040a00007e00 mcause: 0x0000000000000005 mepc: 0x00000000800002ec
mtval: 0x000000078fffffbf
mepc different at pc = 0x0000000000, right = 0x00000000800002ec, wrong = 0x0000000000000000
mtval different at pc = 0x0000000000, right = 0x000000078fffffbf, wrong = 0x0000000000000000
mcause different at pc = 0x0000000000, right = 0x0000000000000005, wrong = 0x0000000000000000
v20_low different at pc = 0x0000000000, right = 0x3a5df9a517eb24b9, wrong = 0x0000800000000000
Core 0: ABORT at pc = 0x0
```

Diff log and testcase:

[program.zip](https://github.com/user-attachments/files/27561480/program.zip)

### Expected behavior

`vloxseg5ei64.v` should raise the same load access fault as the reference model for this address/index combination.

In this testcase:

- the faulting instruction is at `0x800002ec`
- expected `mcause = 5`
- expected `mepc = 0x800002ec`
- expected `mtval = 0x000000078fffffbf`

The implementation should not commit destination vector register side effects for the faulting indexed segment load.

### Environment

XiangShan Kunminghu v2:

```text
Core 0's Commit SHA is: 5fedf66dd6, dirty: 1
emu compiled at Apr 28 2026, 12:47:13
```

### To Reproduce

Run XiangShan Kunminghu v2 with difftest:

```bash
./build/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so -I 500 -C 200000
```

Observe the mismatch at the indexed segment load:

```text
mcause different at pc = 0x0000000000, right = 0x0000000000000005, wrong = 0x0000000000000000
v20_low different at pc = 0x0000000000, right = 0x3a5df9a517eb24b9, wrong = 0x0000800000000000
Core 0: ABORT at pc = 0x0
```

### Additional context

_No response_
