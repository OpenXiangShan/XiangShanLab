### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug


In the current implementation of XiangShan, `vfmv.f.s` does not NaN-box the result when `SEW=32` and the scalar floating-point register width is 64-bit. This violates the architectural floating-point register writeback rule for narrower-than-`FLEN` values.

Spec Reference:

- The RVV spec defines `vfmv.f.s` as copying element 0 from a vector register to a scalar floating-point register.
- The D extension requires operations that write a narrower result to an `f` register to write all `1`s into the upper `FLEN-n` bits, i.e. a valid NaN-boxed value.

Original Code:

```asm
.section .text
.globl _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600       # set FS+VS to Dirty so FP/RVV state is usable
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0

    j       user_code

user_code:
    vsetivli x0, 2, e64, m1, ta, ma
    vmv.v.i  v21, 0

    vsetivli x0, 1, e32, m1, tu, mu
    la       x10, data_word
    vle32.v  v21, (x10)
    vfmv.f.s f23, v21
    fmv.x.d  x15, f23

    li       x16, -1
    slli     x16, x16, 32
    li       x17, 0x55555555
    or       x16, x16, x17       # expected 0xffffffff55555555

    li       gp, 1
    beq      x15, x16, exit
    li       gp, 2

exit:
    la      t1, tohost
    sd      gp, 0(t1)

    la      t1, skiptrap_store_buf
    sd      gp, 0(t1)

    li      t0, 0                # DiffTest STATE_GOODTRAP
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

    .align  2
data_word:
    .word   0x55555555
```

In the test, `vle32.v v21, (x10)` loads the 32-bit value `0x55555555` into element 0 of `v21` under `SEW=32`. The next instruction is `vfmv.f.s f23, v21`, which copies that element into scalar floating-point register `f23` (alias `fs7`). Since `FLEN=64` and the transferred value is only 32 bits wide, the result must be NaN-boxed, i.e. `0xffffffff55555555`.

On XiangShan with diff enabled, the architectural state diverges at `vfmv.f.s`:
> fs7 different at pc = 0x008000003c, right = 0xffffffff55555555, wrong = 0x0000000055555555

Observed Behavior:
- Input element (`SEW=32`): `0x55555555`
- Expected result in `f23/fs7`: `0xffffffff55555555`
- Actual result in `f23/fs7`: `0x0000000055555555`

XiangShan Diff Log:

[diff.log](https://github.com/user-attachments/files/26885076/diff.log)

### Expected behavior

According to the RVV spec, `vfmv.f.s` copies element 0 to a scalar floating-point register. According to the D extension NaN-boxing rule, writing a 32-bit value into a 64-bit `f` register must set the upper 32 bits to `1`.

For this testcase, the expected result is:
- `vfmv.f.s` correctly produces `0xffffffff55555555` in `f23`
- the program reaches the pass path without difftest errors

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26885118/bug-report.tar.gz)

### To Reproduce

Run XiangShan with diff:

```bash
./build/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so
```

Observe the mismatch at pc = `0x008000003c`:

```text
fs7 different at pc = 0x008000003c, right = 0xffffffff55555555, wrong = 0x0000000055555555
```


### Additional context

_No response_
