### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. When executing the instruction `vsoxei32.v v14,(a0),v16` at PC `0x80001210`, NEMU correctly commits a store, whereas XiangShan commits no store from this instruction. Instead, XiangShan commits a store from a later instruction `fsw ft5,160(t6)` at PC `0x80001244`.

```
==============  Store Commit Event (Core 0)  ==============
Mismatch for store commits 
  REF commits addr 0x00000000fffffff8, data 0xc400000000000000, mask 0x0080, pc 0x0000000080001210
  DUT commits addr 0x00000000801010a0, data 0x00000000ffff668e, mask 0x000f, pc 0x0000000080001244, robidx 0x45
Core 0: ABORT at pc = 0x8000010c
Core-0 instrCnt = 447, cycleCnt = 6,606, IPC = 0.067666
```

`vsoxei32.v` is a vector indexed-ordered store that writes 32-bit elements to `base + vs2[i] * 4`. With `vl=4` set by a preceding `vsetivli`, this instruction should generate up to 4 element stores. NEMU produces at least one committed store. XiangShan produces none -- its next committed store is from `fsw`, located 13 instructions later at a different PC.

According to the RISC-V Vector Specification:

> Except for vector indexed-ordered loads and stores, element operations are unordered within the instruction. Vector indexed-ordered loads and stores read and write elements from/to memory in element order respectively.

As a vector indexed-ordered store, `vsoxei32.v` must commit its element stores in order. XiangShan fails to commit any store from this instruction entirely.

The DiffTest report is as follows：

[emulator.zip](https://github.com/user-attachments/files/28825070/emulator.zip)

### Expected behavior

XiangShan should commit stores from `vsoxei32.v` at PC `0x80001210`, matching NEMU's store-commit order. After `vsoxei32.v`, XiangShan should proceed to PC `0x80001214` and continue execution in lockstep with NEMU.

### Environment

- Repo
    - XiangShan commit id: `f09207872d`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seeds_170_.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds.zip](https://github.com/user-attachments/files/28825089/seeds.zip)

### Additional context

_No response_
