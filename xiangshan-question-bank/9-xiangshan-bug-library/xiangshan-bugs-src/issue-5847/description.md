### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A single cross-16B misaligned `sd`, combined with sbuffer pressure, deterministically triggers the StoreQueue pointer invariant assertion:

```scala
XSError(deqPtrExt(0) > rdataPtrExt(0), "Why deqPtr > rdataPtr? something error!")
```

at `NewStoreQueue.scala:1918`.

On commit `7a3e976da3c3`, the offset-44 reproducer fired at 1524 guest cycles, the offset-60 variant fired at 1689 guest cycles, and a within-16B control at offset 56 completed without assertion.

This suggests that the trigger is the SQ cross-16B handling, and that a 64-byte cache-line crossing is not required.

### Expected behavior

Cross-16B misaligned stores should be dequeued correctly without violating StoreQueue pointer invariants, regardless of sbuffer occupancy.

### Environment

- Hardware
  - CPU: Intel Xeon
  - Memory (GB): 1024
  - Storage (GB): 2000
- Software
  - Operating system: Ubuntu 24.04
  - gcc version: 13.3.0
  - clang version:
  - java version: openjdk 21.0.6
  - mill version: 0.12.6
- Repo
  - XiangShan commit id: `7a3e976da3c3`
  - NEMU commit id (if difftest failed with NEMU):
  - SPIKE commit id (if difftest failed with SPIKE):
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=2 -j$(nproc)`
  - Run command: `./build/emu -i cross16b_off44.bin --max-cycles=50000 --no-diff`

### To Reproduce

Run the attached binary:

```
./build/emu -i cross16b_off44.bin --max-cycles=50000 --no-diff
```

The assertion fires deterministically at about 1524 guest cycles.

The core loop in the reproducer is:

```assembly
trigger_loop:
  addi  a1, a1, 1

  li    t0, 0xAAAABBBBCCCCDDDD
  sd    t0, 44(s1)            # bytes 44..51, crosses 16B boundary at 48

  li    t2, 0xEEEE
  sd    t2, 192(s1)
  sd    t2, 256(s1)
  sd    t2, 320(s1)
  sd    t2, 384(s1)
  sd    t2, 448(s1)
  sd    t2, 512(s1)
  sd    t2, 576(s1)
  sd    t2, 640(s1)
  sd    t2, 704(s1)
  sd    t2, 768(s1)
  sd    t2, 832(s1)
  sd    t2, 896(s1)
  sd    t2, 960(s1)
  sd    t2, 1024(s1)
  sd    t2, 1088(s1)
  sd    t2, 1152(s1)

  blt   a1, a0, trigger_loop
```

Replacing `44(s1)` with `60(s1)` (which additionally crosses a 64B cache-line boundary) also fires the same assertion (`cross16b_off60.bin` attached). Replacing with `56(s1)` (within 16B) does not.

[cross16b_deqptr_rdataptr.zip](https://github.com/user-attachments/files/26976513/cross16b_deqptr_rdataptr.zip)

### Additional context

_No response_
