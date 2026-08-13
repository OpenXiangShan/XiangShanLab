### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. When the vector unit-stride load instruction `vle16.v v1, (s3)` at PC `0x800012a8` accesses an illegal address and triggers a Load Access Fault (mcause=5) on the very first element, XiangShan incorrectly sets `vstart = 4` (vl) instead of `vstart = 0` (the index of the faulting element).

The base address register `s3` contains `0x000000004ad00009`, which falls outside any valid PMA region, causing a precise Load Access Fault at element index 0.

According to the RISC-V Vector Extension Specification (Section 31.1.3.7. Vector Start Index Register):

> Normally, vstart is only written by hardware on a trap on a vector instruction, with the vstart value representing the element on which the trap was taken (either a synchronous exception or an asynchronous interrupt), and at which execution should resume after a resumable trap is handled.

Since the first element (index 0) caused the trap, `vstart` must be 0. NEMU correctly reports `vstart = 0`. XiangShan reports `vstart = 4`, which equals `vl`. The DiffTest per-instruction checking confirms this: the previous instruction `vmv.v.v` at [30] committed without mismatch, and the error only appears at the exception entry of `vle16.v` at [31].

```
[30] commit pc 0x800012a4 5e0f8357  vmv.v.v v6, v31
[31] exception pc 0x800012a8 0209d087  vle16.v v1, (s3) -> Load Access Fault (mcause=5)
```

```
 vstart different at pc = 0x008000012e
   right = 0x0000000000000000 (NEMU)
   wrong = 0x0000000000000004 (XiangShan = vl)
```  

Register state at exception entry (NEMU REF):
```
s3:     0x000000004ad00009
vl:     0x0000000000000004
vstart: 0x0000000000000000
mcause: 0x0000000000000005
mepc:   0x00000000800012a8
mtval:  0x000000004ad00009
```
The DiffTest report is as follows：

[emulator.zip](https://github.com/user-attachments/files/28379464/emulator.zip)

### Expected behavior

When `vle16.v` traps on element 0, XiangShan should set `vstart = 0` (the faulting element index), not `vstart = 4` (vl).

### Environment

- Repo
    - XiangShan commit id: `f464649442`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan-v2/build-v2/emu -b 0 -e 0 -i /***/seeds.elf --diff /***/dut/XiangShan-v2/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds.zip](https://github.com/user-attachments/files/28379490/seeds.zip)

### Additional context

_No response_
