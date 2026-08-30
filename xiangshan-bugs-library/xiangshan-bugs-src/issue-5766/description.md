### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a minimal RVV fault-only-first reproducer shows that XiangShan exposes an incorrect vl value immediately after vle8ff.v.

```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  # Same PMP shape as the double-fixvl test, but only one vle8ff.
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
  vmv.v.i v8, 0

  la a1, deny_start
  addi a1, a1, -7

  vle8ff.v v8, (a1)
  csrr t4, vl

  # Record the observed vl so we can compare against the double-fixvl case.
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
  .byte 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17
deny_start:
  .byte 0xde, 0xad, 0xbe, 0xef, 0xfa, 0xce, 0xba, 0xbe
deny_end:

```
In the test, vle8ff.v v8, (a1) is executed under a PMP configuration that causes a later-element fault. The next instruction is csrr t4, vl.
Spike continues to the pass path, which implies t4 == 8 as expected.
On XiangShan with diff enabled, the architectural state diverges at the csrr vl:
> t4 different at pc = 0x0080000056, right = 0x0000000000000008, wrong = 0x0000000000000000

So the observed behavior is:
- Expected: `csrr vl` returns 8
- XiangShan: `csrr vl` returns 0
Additionally, running the same testcase on XiangShan with --no-diff still triggers an internal assertion / critical error, so this does not look like a diff-only issue.

Useful log excerpts:

Spike:

> 0x0000000080000052  vle8ff.v v8, (a1)
> 0x0000000080000056  csrr    t4, vl
> ...
> \>\>\>\>  pass

XiangShan --diff:
> t4 different at pc = 0x0080000056, right = 0x0000000000000008, wrong = 0x0000000000000000
> Core-0 instrCnt = 29, cycleCnt = 8445

XiangShan --no-diff:

> Assertion failed at /home/projects/projects/XiangShan/build/rtl/ExuBlock.sv:927.
> [ERROR] ... critical error: csr_dbltrp_inMN

Logs:

[vle8ff_single_fixvl_probe.diff.log](https://github.com/user-attachments/files/26487202/vle8ff_single_fixvl_probe.diff.log)
[vle8ff_single_fixvl_probe.nodiff.log](https://github.com/user-attachments/files/26487203/vle8ff_single_fixvl_probe.nodiff.log)
[vle8ff_single_fixvl_probe.spike.log](https://github.com/user-attachments/files/26487204/vle8ff_single_fixvl_probe.spike.log)

### Expected behavior

After a single vle8ff.v with a later-element fault, an immediately following csrr vl should observe the architecturally correct vl value.

For this testcase, the expected result is:

- `csrr t4, vl` returns 8
- the program reaches the pass path
- XiangShan should not trigger any internal assertion or critical error

### Environment

[bug-report.zip](https://github.com/user-attachments/files/26487217/bug-report.zip)

### To Reproduce
 
1. Run XiangShan with diff:> ./build/emu -i tests/vseg-fof-risk/vle8ff_single_fixvl_probe.bin \>  --diff ./ready-to-run/riscv64-spike-so \>  -I 80 -C 200000

2. Observe the mismatch at pc = 0x80000056:
> t4 is 8 on Spike but 0 on XiangShan.

3. Run XiangShan without diff:
> ./build/emu -i tests/vseg-fof-risk/vle8ff_single_fixvl_probe.bin \
>  --no-diff \
>  -I 80 -C 200000

4. Observe that XiangShan still aborts with an internal assertion / critical error.

Source Code:
```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  li t6, 0x600
  csrs mstatus, t6

  # Same PMP shape as the double-fixvl test, but only one vle8ff.
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
  vmv.v.i v8, 0

  la a1, deny_start
  addi a1, a1, -7

  vle8ff.v v8, (a1)
  csrr t4, vl

  # Record the observed vl so we can compare against the double-fixvl case.
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
  .byte 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17
deny_start:
  .byte 0xde, 0xad, 0xbe, 0xef, 0xfa, 0xce, 0xba, 0xbe
deny_end:

```

### Additional context

_No response_
