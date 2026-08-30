### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

Two cross-page misaligned `sw` stores at offset 4093 from a page-aligned buffer deterministically trigger an internal `StoreQueue` assertion:

```text
[ERROR][time= ...] ...storeQueue: double deq! index: 3
```

at `NewStoreQueue.scala:1781`.

The assertion fires at about 1125 guest cycles on commit `7a3e976da3c3`.

### Expected behavior

Cross-page misaligned stores should not cause internal `StoreQueue` assertion failures.

### Environment

- Hardware
  - CPU: Intel Xeon Platinum 8592+
  - Memory (GB): 1006
  - Storage (GB): 877
- Software
  - Operating system: Ubuntu 24.04
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
  - Run command: `./build/emu -i crosspage_double_deq.elf --max-cycles=50000 --no-diff`

### To Reproduce

Run the attached binary:

```bash
./build/emu -i crosspage_double_deq.elf --max-cycles=50000 --no-diff
```

The core instruction sequence is:

```assembly
  # s0 = pagebuf + 4080 (near page boundary)
  li t0, 160
  sb t0, 0(s0)              # initialize 1 byte

  li t1, 0x44332211
  sw t1, 13(s0)             # cross-page store at offset 4093
  li t2, 0x44332211
  sw t2, 13(s0)             # cross-page store again
  fence rw, rw
```

[crosspage_double_deq.zip](https://github.com/user-attachments/files/26993437/crosspage_double_deq.zip)

### Additional context

_No response_
