### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a minimal RVV segment fault-only-first testcase triggers an internal XiangShan critical error when executing a single vlseg2e8ff.v under a PMP configuration that causes a later-element fault.
```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  la t0, deny_start
  srli t0, t0, 2
  csrw pmpaddr0, t0
  la t1, deny_end
  srli t1, t1, 2
  csrw pmpaddr1, t1
  li t2, 0x8800
  csrw pmpcfg0, t2

  li a0, 8
  vsetvli t3, a0, e8, m1, tu, mu
  vmv.v.i v16, 0
  vmv.v.i v17, 0

  # Same segment-ff access shape used in the mixed test.
  la a2, deny_start
  addi a2, a2, -12

  vlseg2e8ff.v v16, (a2)
  csrr t4, vl

  la t0, observed_vl
  sw t4, 0(t0)

pass:
  li gp, 1
  ebreak

trap_handler:
  csrr t0, mcause
  li t1, 3
  bne t0, t1, unexpected_trap

  la t2, result_code
  sw gp, 0(t2)
  la t3, done
  csrw mepc, t3
  mret

unexpected_trap:
  la t2, result_code
  li t3, 0xdead
  sw t3, 0(t2)
  la t4, done
  csrw mepc, t4
  mret

done:
  fence
1:
  j 1b

.section .data
.align 3
result_code:
  .word 0
observed_vl:
  .word 0
pre_deny:
  .byte 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
  .byte 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
deny_start:
  .byte 0xde, 0xad, 0xbe, 0xef, 0xfa, 0xce, 0xba, 0xbe
deny_end:

```
The problem can be reproduced with a single vlseg2e8ff.v.
Observed behavior on XiangShan:
- internal critical error: `csr_dbltrp_inMN`
- assertion failure: `ExuBlock.sv:927`

With --diff, the run reports:
> Core 0 dump: HIT CRITICAL ERROR: please check if software cause a double trap.
Core 0: HIT GOOD TRAP ...
Assertion failed at /home/projects/projects/XiangShan/build/rtl/ExuBlock.sv:927.



### Expected behavior

A single `vlseg2e8ff.v` with a later-element fault should not trigger an internal XiangShan critical error or assertion failure. The processor should handle the fault-only-first behavior architecturally correctly, including `vl` update / trap behavior, without entering a double-trap-like internal failure path.

### Environment

[bug-report.zip](https://github.com/user-attachments/files/26487409/bug-report.zip)

### To Reproduce

1. Run XiangShan with diff:
> ./build/emu -i tests/vseg-fof-risk/vlseg2e8ff_single_fixvl_probe.bin \
>  --diff ./ready-to-run/riscv64-spike-so \
> -I 80 -C 200000

2. Observe the internal error / assertion:
- HIT CRITICAL ERROR
- csr_dbltrp_inMN
- assertion at ExuBlock.sv:927

3. Run XiangShan without diff:
> ./build/emu -i tests/vseg-fof-risk/vlseg2e8ff_single_fixvl_probe.bin \
>  --no-diff \
>  -I 80 -C 200000

4. Observe that the same internal assertion / critical error still happens.

### Additional context

_No response_
