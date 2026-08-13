### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. At PC `0x80001198`, the instruction `94006013` is a `prefetch.i` (Zicbop). NEMU correctly handles it as a hint and advances, while XiangShan stalls indefinitely.

Instruction Encoding：
```
94006013  (opcode=OP-IMM, funct3=ORI, rd=x0, rs1=x0, imm[4:0]=0x0)
→ Zicbop: prefetch.i
→ effective address = x0 + signext(0x940) = 0xFFFFFFFFFFFFF940
```
`0xFFFFFFFFFFFFF940` lies outside all valid PMA regions (the first usable entry begins at `0x4000000`). No memory or peripheral backs this address.

Per the RISC-V Zicbop Extension:

> A cache-block prefetch instruction is permitted to access the specified cache block whenever a load instruction, store instruction, or instruction fetch is permitted to access the corresponding physical addresses. If access to the cache block is not permitted, a cache-block prefetch instruction does not raise any exceptions and shall not access any caches or memory. During address translation, the instruction does not check the accessed and dirty bits and neither raises an exception nor sets the bits.

Since `0xFFFFFFFFFFFFF940` is not a permitted address, the `prefetch.i` **shall not access memory or raise any exception**.

The DiffTest report is as follows：

[emulator.zip](https://github.com/user-attachments/files/28756044/emulator.zip)

### Expected behavior

`prefetch.i` shall be handled as a non-blocking hint. For an invalid address it must be treated as a NOP: no memory access, no pipeline stall, no exception. After `prefetch.i` at PC `0x80001198`, XiangShan should advance to PC `0x8000119c` to match NEMU.

### Environment

- Repo
    - XiangShan commit id: `f09207872d`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seeds_.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seed.zip](https://github.com/user-attachments/files/28756050/seed.zip)

### Additional context

_No response_
