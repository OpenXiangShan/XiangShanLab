### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In the current implementation of XiangShan, the vmv.x.s instruction performs a zero-extension instead of a sign-extension when the Selected Element Width (SEW) is smaller than the XLEN (64-bit). This violates the RISC-V Vector Specification.

Spec Reference:
According to RISC-V Vector Spec v1.0, Section 16.1:

    "If SEW < XLEN, the value is sign-extended to XLEN bits."

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
    vmv.v.i  v9, 0

    vsetivli x0, 1, e32, m1, tu, mu
    la       x10, data_word
    vle32.v  v9, (x10)
    vmv.x.s  x15, v9

    li       x16, 0xffffffffe01d7b92
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
    .word   0xe01d7b92

```
In the test, `vle32.v v9, (x10)` loads the 32-bit value 0xe01d7b92 (where the MSB is 1) into element 0 of v9. The next instruction is `vmv.x.s a5, v9`, which moves this element to the scalar register a5 (which corresponds to x15).
Spike sign-extends the value to 64 bits (0xffffffffe01d7b92) as required by the RVV specification.
On XiangShan with diff enabled, the architectural state diverges at vmv.x.s:
> a5 different at pc = 0x008000003c, right = 0xffffffffe01d7b92, wrong = 0xdcfffef3e01d7b92

Observed Behavior:
- Input element (SEW=32): 0xe01d7b92 (The MSB is 1)
- Expected Result (x15): 0xffffffffe01d7b92 (Sign-extended)
- Actual Result (x15): 0x00000000e01d7b92 (Zero-extended)

Xiangshan Diff Log: 

[diff.log](https://github.com/user-attachments/files/26884223/diff.log)

### Expected behavior

Observed Behavior:
- Input element (SEW=32): 0xe01d7b92 (The MSB is 1)
- Expected Result (x15): 0xffffffffe01d7b92 (Sign-extended)
- Actual Result (x15): 0x00000000e01d7b92 (Zero-extended)
- 
According to the RISC-V Vector Specification v1.0 (Section 16.1):

    "If SEW < XLEN, the value is sign-extended to XLEN bits."

For this testcase, the expected result is:
- `vmv.x.s` correctly sign-extends 0xe01d7b92 to 0xffffffffe01d7b92
- the program reaches the pass path smoothly without difftest errors.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26884242/bug-report.tar.gz)

### To Reproduce

Run XiangShan with diff:
> ./build/emu -i program.bin --diff ./ready-to-run/riscv64-spike-so

Observe the mismatch at pc = 0x008000003c:
> a5 is 0xffffffffe01d7b92 on Spike but incorrect on XiangShan.

### Additional context

_No response_
