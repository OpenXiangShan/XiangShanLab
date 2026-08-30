### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. When executing the instruction `vlse64.v v16,(s5),a6` at `PC=0x80001348`, NEMU correctly triggers a load access fault, whereas XiangShan fails to do so.

The base address register `s5` contains `0x000000000000ffff`, which is an invalid/illegal address. According to the RISC-V Privileged Specification:

> Attempting to execute a load, load-reserved, or cache-block management instruction which accesses a physical address within a PMP region without read permissions raises a load access-fault exception.

XiangShan should raise a load access-fault exception. Instead, XiangShan misses this exception and retains an incorrect state, which subsequently leads to the following DiffTest mismatch later in the execution stream:

```
mstatus different at pc = 0x008000110c, right = 0x8000040a00407fa2, wrong = 0x8000000a004067aa
   mepc different at pc = 0x008000110c, right = 0x0000000080001348, wrong = 0x000000008000133c
  mtval different at pc = 0x008000110c, right = 0x000000000000ffff, wrong = 0x000000004923a657
 mcause different at pc = 0x008000110c, right = 0x0000000000000005, wrong = 0x0000000000000002
```

The DiffTest report  is as follows：

[emulator.zip](https://github.com/user-attachments/files/28228936/emulator.zip)

### Expected behavior

XiangShan should correctly raise a precise load access-fault exception (`mcause=5`) at `PC=0x80001348` to match NEMU's behavior.

### Environment

- Repo
  - XiangShan commit id: abd0f867a8（kunminghu-v2）
  - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
  - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
  - Run command : `/***/dut/XiangShan-v2/build-v2/emu   -b 0 -e 0   -i /***/seed_.elf   --diff /***/dut/XiangShan-v2/ready-to-run/riscv64-nemu-interpreter-so`
  - Config: `DefaultConfig`


### To Reproduce

[seed.zip](https://github.com/user-attachments/files/28229049/seed.zip)

### Additional context

_No response_
