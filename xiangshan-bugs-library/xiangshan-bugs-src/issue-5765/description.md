### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a reproducible difftest mismatch between XiangShan and Spike after executing vlm.v v0, (...).
```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  # Minimal deterministic vlm.v -> v0 test.
  # First zero the full v0 register with vl=16, then switch to vl=10 and
  # execute vlm.v. This avoids tail-undisturbed garbage in the old v0 state.
  li a0, 16
  vsetvli t0, a0, e8, m1, tu, mu
  vmv.v.i v0, 0

  li a0, 10
  vsetvli t0, a0, e8, m1, tu, mu

  la a1, mask_data
  vlm.v v0, (a1)

  la a2, out_data
  vsm.v v0, (a2)

  la t1, out_data
  la t2, expected_data
  li t3, 2
1:
  lbu t4, 0(t1)
  lbu t5, 0(t2)
  bne t4, t5, bug
  addi t1, t1, 1
  addi t2, t2, 1
  addi t3, t3, -1
  bnez t3, 1b

pass:
  la t0, result_code
  li t1, 1
  sw t1, 0(t0)
  j done

bug:
  la t0, result_code
  li t1, 2
  sw t1, 0(t0)
  j done

trap_handler:
  la t0, result_code
  li t1, 0xdead
  sw t1, 0(t0)
  csrr t2, mepc
  addi t2, t2, 4
  csrw mepc, t2
  mret

done:
  fence
  wfi

.section .data
.align 3
result_code:
  .word 0
mask_data:
  .byte 0x80, 0x03
expected_data:
  .byte 0x80, 0x03
out_data:
  .space 4

```
The code is very simple:
1. enable vector state
2. set vl=16
3. execute vmv.v.i v0, 0 to clear the full v0
4. set vl=10
5. execute: vlm.v v0, (a1)

The Xiangshan Difftest reports a difference:
```
v0_low different at pc = 0x008000002c, right = 0x0000000000000380, wrong = 0xffffffffffff0380
v0_high different at pc = 0x008000002c, right = 0x0000000000000000, wrong = 0xffffffffffffffff
```

At this point I am not fully sure whether this is:
- a real XiangShan implementation bug, or
- a difftest issue where the comparison on v0 is too strict for this instruction.

### Expected behavior

XiangShan and Spike should not trigger a difftest mismatch on this test.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26462176/bug-report.tar.gz)

### To Reproduce

```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  # Minimal deterministic vlm.v -> v0 test.
  # First zero the full v0 register with vl=16, then switch to vl=10 and
  # execute vlm.v. This avoids tail-undisturbed garbage in the old v0 state.
  li a0, 16
  vsetvli t0, a0, e8, m1, tu, mu
  vmv.v.i v0, 0

  li a0, 10
  vsetvli t0, a0, e8, m1, tu, mu

  la a1, mask_data
  vlm.v v0, (a1)

  la a2, out_data
  vsm.v v0, (a2)

  la t1, out_data
  la t2, expected_data
  li t3, 2
1:
  lbu t4, 0(t1)
  lbu t5, 0(t2)
  bne t4, t5, bug
  addi t1, t1, 1
  addi t2, t2, 1
  addi t3, t3, -1
  bnez t3, 1b

pass:
  la t0, result_code
  li t1, 1
  sw t1, 0(t0)
  j done

bug:
  la t0, result_code
  li t1, 2
  sw t1, 0(t0)
  j done

trap_handler:
  la t0, result_code
  li t1, 0xdead
  sw t1, 0(t0)
  csrr t2, mepc
  addi t2, t2, 4
  csrw mepc, t2
  mret

done:
  fence
  wfi

.section .data
.align 3
result_code:
  .word 0
mask_data:
  .byte 0x80, 0x03
expected_data:
  .byte 0x80, 0x03
out_data:
  .space 4

```

### Additional context

_No response_
