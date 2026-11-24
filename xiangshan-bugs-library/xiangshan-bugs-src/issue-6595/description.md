### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A cache-block operation issued in **M mode** against a block covered by a **locked** PMP entry that grants no permission retires silently. No trap is taken and no trap CSR is written, so execution continues with the next
instruction.

A PMP entry with `L = 1` applies to M mode as well, so the access must be refused. NEMU raises a store/AMO access fault (`mcause = 7`) with `mtval` = the block address.

```asm
  # PMP entry 0: NAPOT, one 4 KiB block at 0x80600000, locked, no R/W/X
  li    t0, 0x201801ff          # (0x80600000 >> 2) | 0x1ff
  csrw  pmpaddr0, t0
  li    t0, 0x98                # L | NAPOT | no permissions
  csrw  pmpcfg0, t0

  li    t1, 0x80600000
  cbo.clean (t1)                # must raise a store/AMO access fault
```

difftest, 12 instructions in (left = XiangShan, right = NEMU):

```
  mstatus different at pc = ..., right = 0x0000040a00001800, wrong = 0x0000000a00000000
     mepc different at pc = ..., right = 0x0000000080000026, wrong = 0x0000000000000000
    mtval different at pc = ..., right = 0x0000000080600000, wrong = 0x0000000000000000
   mcause different at pc = ..., right = 0x0000000000000007, wrong = 0x0000000000000000
```

All four trap CSRs are zero on XiangShan: the core never took the trap.

The same holds for `cbo.clean`, `cbo.flush`, `cbo.inval` and `cbo.zero`.


### Expected behavior

The cache-block operation should raise a store/AMO access fault, as the reference model does, since the locked PMP entry denies the access in M mode.

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
  - Run command: `./build/emu -i f2_cbo_locked_pmp.bin --diff <nemu-so> -I 60`


### To Reproduce


1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i f2_cbo_locked_pmp.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 60
   ```
3. Observe the `mcause` / `mepc` / `mtval` / `mstatus` mismatch at `instrCnt = 12`: the reference has taken the fault, the DUT has not.

[f2_cbo_locked_pmp.elf.zip](https://github.com/user-attachments/files/32386614/f2_cbo_locked_pmp.elf.zip)

### Additional context

Related but not the same: #6062 (*S-mode `cbo.clean` to a PMP-denied block aborts StoreQueue*) is closed as fixed. That one was an S-mode access hitting a StoreQueue assertion; this one is an **M-mode** access against a **locked** entry, and the symptom is the opposite — no trap at all rather than an assertion. It may be a part the earlier fix did not cover.
