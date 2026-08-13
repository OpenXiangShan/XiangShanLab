### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In **EX_BP_m/pc_match (EX_BP in M-mode, with the triggering type of PC-matching)** scenario, all test cases mismatch between DUT (Xiangshan) and the **latest REF** `Xiangshan/ready-to-run/riscv64-nemu-interpreter-so` . 
As regards all those cases, DUT (Xiangshan) always trigger BP at the target PC as expected, so does the previous ready-to-run/riscv64-nemu-interpreter-so `(commit version: 900eb58246ad08aeeff091be4de0d9814c751149)`;  However, the newest ready-to-run/riscv64-nemu-interpreter-so `(commit version: 955e6e2a5b5a51426d000597710cd632aeba2f52)` just ignores the pc-match and does not trigger BP at the target PC.

Take one case (with details in the attachment file) for example: 
Observed Behavior
- current ref:
  - HEAD: `955e6e2`
  - no breakpoint exception thrown at `pc = 0x80000302`
  - `t0` becomes `0x58`
  - final state shows `mcause = 0`, `mepc = 0`, `mtval = 0`
  - `pc` advances to `0x80000306`
- DUT:
  - raises `cause = 3` at `pc = 0x80000302`
  - `t0` stays `0x57`
  - log shows: `exception pc 0000000080000302 inst 00128293 cause 0000000000000003 addi    t0, t0, 1`
- previous ref:
  - HEAD: ` 900eb58`
  - log shows: `[M-mode] Exception handled: cause=3, epc=80000302`
  - reaches good trap
  - behaves exactly as DUT does.

Test Construction
- `bp_target` is a `.option norvc` label containing a 32-bit `addi t0, t0, 1`
- trigger setup:
  - `set_trigger(targetAddr, 0x6000000000000044ULL);`
  - `clear_trigger()` after the asm block
- the trigger target is exactly `0x80000302`
- this is a real execute-trigger pc match on a 32-bit instruction
- it is not a compressed-instruction halfword corner case
- the newest ref silently skips the breakpoint on this exact PC, while DUT and the previous ref both trap correctly


**This may not be a hardware-design bug of Xiangshan; rather, it indicates some tricky behavior of the latest `ready-to-run/riscv64-nemu-interpreter-so`.**


### Expected behavior

As for the REF (ready-to-run/riscv64-nemu-interpreter-so): 
- execute trigger should fire at `pc = bp_target`
- `mcause = 3`
- `mepc = 0x80000302`

### Environment

[bug-report3.tar.gz](https://github.com/user-attachments/files/29348648/bug-report3.tar.gz)


### To Reproduce

Please refer to the attachment: bug-report3.tar.gz for details.

### Additional context

_No response_
