### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. When executing the fault-only-first instruction `vle32ff.v v18, (t1)` at PC `0x800010ac`, NEMU correctly triggers a load access fault and updates the architectural state, whereas XiangShan fails to properly update the exception CSRs.

The base address register `t1` contains `0xffffffff9c3e9268`, which exceeds the configured PMP range, meaning the access violation occurs immediately on the first element. According to the RISC-V Vector Extension Specification regarding unit-stride fault-only-first loads:

> "These instructions execute as a regular load except that they will only take a trap caused by a synchronous exception on element 0. If element 0 raises an exception, `vl` is not modified, and the trap is taken. If an element > 0 raises an exception, the corresponding trap is not taken, and the vector length `vl` is reduced to the index of the element that would have raised an exception."

Since the PMP violation occurs on element 0, XiangShan is required to take the trap and behave exactly like a standard scalar load.

Although XiangShan's PMP checker successfully detects the violation (printing `isa pmp check failed` in the emulator log), the processor fails to update `mcause`, `mepc`, `mtval`, `mstatus`, and `mode`. Instead, it retains stale values from a previous exception handling flow (`cause=2`). This missing state update subsequently leads to the following DiffTest mismatch:


```
   mode different at pc = 0xffffa81ef9122bd8, right = 0x3, wrong = 0x0
mstatus different at pc = 0xffffa81ef9122bd8, right = 0x8000040a00506680, wrong = 0x8000000a00506688
   mepc different at pc = 0xffffa81ef9122bd8, right = 0x800010ac, wrong = 0x80001090
  mtval different at pc = 0xffffa81ef9122bd8, right = 0xffffffff9c3e9268, wrong = 0x0422b373
 mcause different at pc = 0xffffa81ef9122bd8, right = 0x5, wrong = 0x2
Core 0: ABORT at pc = 0xffffa81ef9122bd8
```
The DiffTest report is as follows：

[emulator.zip](https://github.com/user-attachments/files/28334420/emulator.zip)

### Expected behavior

XiangShan should correctly raise a precise load access-fault exception (`mcause=5`) at PC `0x800010ac` due to the element 0 PMP violation, and properly update the exception CSRs (`mepc`, `mtval`, `mstatus`, `mode`) to match NEMU's behavior.

### Environment

- Repo
  - XiangShan commit id: `f464649442`
  - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
  - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
  - Run command: `/***/dut/XiangShan-v2/build-v2/emu   -b 0 -e 0   -i /***/seeds_170_.elf   --diff /***/dut/XiangShan-v2/ready-to-run/riscv64-nemu-interpreter-so`
  - Config: `DefaultConfig`

### To Reproduce

[seed.zip](https://github.com/user-attachments/files/28334492/seed.zip)

### Additional context

_No response_
