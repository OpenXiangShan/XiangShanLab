### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A `vsetivli` that requests **AVL = 0** sets `vtype.vill` when the immediately preceding vector configuration used **LMUL = 8**. The requested `vtype` (`e8, m1, ta, ma`) is supported by the implementation — the same `vsetivli` is accepted when the preceding configuration used LMUL = 1, and the register form `vsetvli` with an AVL of 0 is accepted after the very same LMUL = 8 configuration.

```asm
  li      t3, -1
  vsetvli  x0, t3, e8, m8, ta, ma    # (1) any LMUL = 8 configuration
  vsetivli x0, 0,  e8, m1, ta, ma    # (2) AVL = 0 as an immediate, LMUL back to 1
  csrr    t1, vtype
```

difftest, 11 instructions in (left = XiangShan, right = NEMU):

```
  vtype different at pc = 0xffff08c10accd869,
        right = 0x00000000000000c0, wrong = 0x80000000000000c0
```

`0x80000000000000c0` is the requested `vtype` with **bit 63 (`vill`) set**.

### Expected behavior


`AVL = 0` is an ordinary request. The specification reserves `vtype.vill` for a `vtype` the implementation does not support; `e8, m1` is supported here, as rows (b)–(e) show. The instruction should set `vl = 0` and leave `vill` clear, which is what NEMU and Spike both do.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv-none-elf-gcc (xPack) 15.2.0 (reproducer build)
  - java version: openjdk 17.0.20.1
  - mill version: 0.12.17
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `c8d7b3a5c1abf3f42c954e61abba20dd27e02a21` (kunminghu-v3, **the latest default-branch commit**; also reproduced on `37ce1b50b5`)
  - NEMU commit id: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `45638f5145aa9c7eee85cd9b08eb8962ee46b586`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig`
  - Run command: `./build/emu -i f1_vsetivli_vill.bin --diff <nemu-so> -I 60`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i f1_vsetivli_vill.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 60
   ```
3. Observe the `vtype` mismatch at `instrCnt = 11`.

The reproducer is 11 instructions and also self-checking: it reads `vtype` back, and writes `1` to `tohost` when `vill` is clear and `vl` is 0, or `3` otherwise.

[f1_vsetivli_vill.elf.zip](https://github.com/user-attachments/files/32386025/f1_vsetivli_vill.elf.zip)

### Additional context

_No response_
