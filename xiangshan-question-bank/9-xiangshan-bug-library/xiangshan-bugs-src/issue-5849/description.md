### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

Two cross-16B `sd` stores at offset 12, each followed by a non-overlap load from the next 16B block, deterministically trigger an internal `StoreQueue` assertion:

```text
[ERROR][time= 1345] ...storeQueue: double deq! index: 12
```

at `NewStoreQueue.scala:1781`.

The assertion fires at 1346 guest cycles on commit `7a3e976da3c3`.

### Expected behavior

Cross-16B stores interleaved with loads should not cause internal `StoreQueue` assertion failures.

### Environment

- Hardware
  - CPU: INTEL(R) XEON(R) PLATINUM 8592+
  - Memory (GB): 1006
  - Storage (GB): 877
- Software
  - Operating system: Ubuntu 24.04.3 LTS
  - gcc version: `gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`
  - clang version:
  - java version: `openjdk version "21.0.10" 2026-01-20`
  - mill version: `0.12.15`
- Repo
  - XiangShan commit id: `7a3e976da3c3a13fa612cea352b4a18750a50ce8`
  - NEMU commit id (if difftest failed with NEMU):
  - SPIKE commit id (if difftest failed with SPIKE):
- Build & Run
  - Build command: `make emu NO_DIFF=1 CONFIG=TLMinimalConfig EMU_THREADS=2 WITH_CHISELDB=0 -j32`
  - Run command: `./build/emu -i cross16b_double_deq.bin --max-cycles=1500 --no-diff`

### To Reproduce

Run the attached binary:

```bash
./build/emu -i cross16b_double_deq.bin --max-cycles=1500 --no-diff
```

The core instruction sequence in the reproducer is:

```assembly
  li t0, 0xDEADBEEFCAFEBABE
  sd t0, 12(s0)           # cross-16B store (bytes 12..19)
  lw t1, 20(s0)           # non-overlap load from next 16B block
  bnez t1, bug_hit

  li t0, 0x1122334455667788
  sd t0, 12(s0)           # cross-16B store again
  ld t1, 24(s0)           # non-overlap load from next 16B block
  bnez t1, bug_hit
```

[cross16b_double_deq.zip](https://github.com/user-attachments/files/26990826/cross16b_double_deq.zip)

### Additional context

_No response_
