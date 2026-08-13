### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. When the whole-register load instruction `vl1re16.v v2, (a7)` at PC `0x80001070` triggers a Load Access Fault (mcause=5) on the very first element, NEMU correctly sets `vstart = 0`. XiangShan incorrectly reports `vstart = 62`.

- Base address: `a7 = 0x41dc3c7c`
- Fault address: `mtval = 0x41dc3c7c` (same as base, thus element 0)
- Fault instruction: `0x0288d107`, width field = `101` → EEW = 16-bit

```
vstart different:
  NEMU (right): 0x0000000000000000
  DUT  (wrong): 0x000000000000003e  (= 62)
```

XiangShan computed `vstart` as `mtval[6:0] / (EEW/8) = 0x7C / 2 = 62`, using only the lower 7 bits of the fault address as the offset instead of the full address difference.

Per the RISC-V Vector Extension Specification v1.0, Section 31.1.3.7：
> Normally, vstart is only written by hardware on a trap on a vector instruction, with the vstart value representing the element on which the trap was taken (either a synchronous exception or an asynchronous interrupt), and at which execution should resume after a resumable trap is handled.

Since the faulting address equals the base address, the correct `vstart` is `(0x41dc3c7c - 0x41dc3c7c) / 2 = 0`.

The DiffTest report is as follows：

[emulator.zip](https://github.com/user-attachments/files/28779568/emulator.zip)

### Expected behavior

Fix the LSU vstart update logic on vector load exceptions: use `(mtval - base_addr) / EEW_bytes`, not `mtval[6:0] / EEW_bytes`.

### Environment

- Repo
    - XiangShan commit id: `f09207872d`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seeds_170_.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds.zip](https://github.com/user-attachments/files/28779599/seeds.zip)

### Additional context

_No response_
