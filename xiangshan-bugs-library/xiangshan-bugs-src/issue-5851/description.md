### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A cross-page misaligned `sd` at offset 4092 from a page-aligned buffer, followed immediately by an alias `lbu` from the same address, reads stale data instead of the stored value.

The store data is eventually written correctly (a `fence rw, rw` followed by reload returns the correct value), so this is a transient store-to-load visibility bug.

Reproduced on commit `7a3e976da3c3`.

### Expected behavior

The alias load should either forward the stored byte or replay until the store becomes visible. It should not commit a stale value.

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
  - Run command: `./build/emu -i crosspage_sd_alias.elf --max-cycles=3500 --no-diff`

### To Reproduce

Run the attached binary:

```bash
./build/emu -i crosspage_sd_alias.elf --max-cycles=3500 --no-diff
```

The core instruction sequence is:

```assembly
  # s2 = pagebuf + 4080
  li    t1, 0x8877665544332211
  sd    t1, 12(s2)        # cross-page store at offset 4092 (bytes 4092..4099)
  lbu   a0, 12(s2)        # alias load from same address
  li    a1, 0x11
  bne   a0, a1, fail      # expects 0x11, gets stale data
```

The test enters the fail loop, ending at `pc = 0x80000114` (inside `fail:`).

As a control, the same cross-page `sd` followed by `fence rw, rw` and then `lbu` returns the correct value and reaches `pass:`.

[crosspage_sd_alias.zip](https://github.com/user-attachments/files/26994585/crosspage_sd_alias.zip)

### Additional context

_No response_
