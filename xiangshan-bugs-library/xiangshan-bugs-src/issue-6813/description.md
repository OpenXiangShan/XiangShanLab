### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A misaligned store that crosses a 4 KiB page boundary, followed by a younger load that reads only bytes on the second page, can return the old memory value of those bytes. The store itself is written correctly: with a `fence rw, rw` between the two, or with a different timing, the load returns the new value.

In the attached workload (M mode, no translation), `lh a1, 0(a5)` at 0x80024000 returns `0x4000`, the value before the store. DiffTest reports `a1 different at pc ..., right = 0xffffffffffffabcd, wrong = 0x0000000000004000` at the 48th instruction.

### Expected behavior


The load returns the bytes written by the older store (`0xffffffffffffabcd`), as NEMU and Spike do.

### Environment


- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - clang version: not used
  - java version: openjdk version "17.0.20.1" 2026-08-18
  - mill version: 0.12.17
- Repo
  - XiangShan commit id: `a0c2a26cbe3c862175174bad03a1a23c406315ed`
  - NEMU commit id (if difftest failed with NEMU): `513786c4c597dd628c6126144937372d6f7fc92e`
  - SPIKE commit id (if difftest failed with SPIKE): not used
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig NO_ZSTD_COMPRESSION=1 OPT_FAST=-O3 -j20`
  - Run command (if applicable): `./build/emu -i crosspage_store_K6.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.

### To Reproduce


Workload in `crosspage_store.zip`:
- `crosspage_store_K6.elf`: fails (K = 6).
- `crosspage_store_K8.elf`: passes (K = 8).
- `crosspage_store_K6_fence.elf`: K = 6 with `fence rw, rw` after the store, passes.

```asm
  li   a4, 0x80024000      # page boundary; the line at a4 is cached
  li   a3, 0x80420000      # lines that were never touched
  ld   a1, 0(a4)
  ld   t3, 8(a3)           # cold miss: delays the store's commit and the load's address
  sd   s5, -1(a4)          # bytes 0x80023fff..0x80024006, crosses the page
  addi t3, t3, 1           # K times
  andi t3, t3, 0
  add  a5, a4, t3
  lh   a1, 0(a5)           # bytes 0x80024000..0x80024001 of the store
```

Run: `./build/emu -i crosspage_store_K6.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so`

[crosspage_store.zip](https://github.com/user-attachments/files/34232767/crosspage_store.zip)

### Additional context


In `src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala`, the second physical address of a cross-page store is kept only in the UnalignQueue, and a younger load of the second-page bytes is held back only while it matches that entry (`s1UnalignQueueMatch`, around line 486). The entry is released when the store head is handed to the Sbuffer write path (`io.fromUnalignQueue.ready := ... && writeSbufferWire.head.fire`, line 1299, and `allocated := false` on `deqCancel`, around line 1522), before the tail bytes can be forwarded from the Sbuffer. A load whose forward query falls in that gap matches neither and reads the old line from the DCache.

Keeping the entry (or at least its forward match) until the store has left the store queue should fix it.

Thanks!
