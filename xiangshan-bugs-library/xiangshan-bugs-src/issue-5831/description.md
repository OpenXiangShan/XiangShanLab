### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A real data corruption in `vlse32.v` was found on XiangShan. This is not a tail-agnostic or mask-agnostic difference. The corrupted bits seem to be inside active loaded elements.

At the mismatch point, the testcase executes:
```asm
vsetvli x8, x0, e64, m8
li x9, 2415919032
li x13, 4
vlse32.v v24, (x9), x13
```

Architectural interpretation:
- current `SEW = 64`
- current `LMUL = 8`
- instruction `EEW = 32`
- therefore `EMUL = (EEW / SEW) * LMUL = 4`
- `vl = 16`, so the destination register group `v24..v27` is fully written

This means the loaded data should be packed as 32-bit elements into the destination group, and there is no tail freedom involved in the corrupted value.

For the memory layout used by this testcase:
- `e4 = 0x7b17d676`
- `e5 = 0x4792b1db`

So the expected low 64 bits of `v25` are:
```text
0x4792b1db7b17d676
```
But XiangShan reports:
```text
0xff92b1db7b17d676
```
Only the top byte of `e5` is corrupted (`0x47 -> 0xff`), which is a real data error.

Original Code:
```asm
.section .text.init
.globl _start

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
    li x8, 0x4a5424199d9d6371
    li x9, 0x8c857dc60589a695
    li x10, 0x9b4b1e46cefe93cd
    li x11, 0xffffffffffffff80
    li x12, 0xffffffffffffff80
    li x13, 0x7fff
    li x14, 0x835fd450261c7ce1
    li x15, 0xd60035e595d68dfc
    li x8, 0x127428cd59c6e2bc
    fmv.d.x f16, x8
    li x8, 0xfe23776d3dd30865
    fmv.d.x f17, x8
    li x8, 0x47803e2a4a466e4d
    fmv.d.x f18, x8
    li x8, 0xd586a4c4ccb70425
    fmv.d.x f19, x8
    li x8, 0x3ff0000000000000
    fmv.d.x f20, x8
    li x8, 0x1b61a52f248a1a17
    fmv.d.x f21, x8
    li x8, 0x36124241a6394d95
    fmv.d.x f22, x8
    li x8, 0x80a9a114fa3dfd41
    fmv.d.x f23, x8
    vsetvli x9, x0, e64, m1
    li x8, 0x5555555555555555
    vmv.v.x v16, x8
    li x8, 0x88c99f107473cef1
    vmv.v.x v17, x8
    li x8, 0x7d52ea60babd5063
    vmv.v.x v18, x8
    li x8, 0xf8feed0c9722535a
    vmv.v.x v19, x8
    li x8, 0x5555555555555555
    vmv.v.x v20, x8
    li x8, 0xffffffffffffffff
    vmv.v.x v21, x8
    li x8, 0xaaaaaaaaaaaaaaaa
    vmv.v.x v22, x8
    li x8, 0x45fd5d503ef54b77
    vmv.v.x v23, x8
    li x9, 0x8fffffb8
    li x10, 0x1000
    sd x10, 0(x9)
    li x10, 0x7f
    sd x10, 8(x9)
    li x10, 0x4792b1db7b17d676
    sd x10, 16(x9)
    li x10, 0x0
    sd x10, 24(x9)
    li x10, 0xed8adb74c7d1dae3
    sd x10, 32(x9)
    li x10, 0xb6c1377ee61df71f
    sd x10, 40(x9)
    li x10, 0x988a9d98c177c9df
    sd x10, 48(x9)
    li x10, 0xff
    sd x10, 56(x9)
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0
    li x2, 2415919039
    c.sdsp x13, 0(sp)
    vsetvli x8, x0, e64, m8
    li x9, 2415919032
    li x13, 4
    vlse32.v v24, (x9), x13
    c.lui x8, 0xfffe1
    li x12, 2415919032
    vs1r.v v4, (x12)
    li x10, 2415919032
    li x14, 1
    vssseg2e8.v v4, (x10), x14, v0.t
    li x9, 2415919032
    vlseg3e8.v v21, (x9), v0.t
    srliw x14, x14, 1
    vmsbc.vv v0, v8, v24

exit:
    li      gp, 1
    la      t1, tohost
    sd      gp, 0(t1)

    la      t1, skiptrap_store_buf
    sd      gp, 0(t1)

    li      t0, 0
    .insn   i 0x6b, 0, x0, t0, 0
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
    beq     t1, t3, fetch_inst
    li      t3, 12
    beq     t1, t3, fetch_inst

fetch_inst:
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

    .section .data
    .align  3
skiptrap_store_buf:
    .dword  0
```

Observed XiangShan diff log:
```text
v25_low different at pc = 0x0080000316, right = 0x4792b1db7b17d676, wrong = 0xff92b1db7b17d676
Core 0: ABORT at pc = 0xffff9a7c92cb4ea1
```
Xiangshan Diff Log:

[diff.log](https://github.com/user-attachments/files/26888239/diff.log)

### Expected behavior

`vlse32.v` should correctly pack the loaded 32-bit elements into the destination group `v24..v27` according to the instruction `EEW=32` and computed `EMUL=4`.

For this testcase, `v25_low` should be:
> 0x4792b1db7b17d676

The implementation should not corrupt the high byte of element `e5`.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26888216/bug-report.tar.gz)

### To Reproduce

Run XiangShan with diff:
> ./build/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so


### Additional context

_No response_
