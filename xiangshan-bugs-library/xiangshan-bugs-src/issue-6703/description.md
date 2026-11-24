### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A misaligned load that crosses a 16-byte boundary can receive, for its lower bytes, data from an older in-flight
store that does not overlap it. The store is also misaligned across a 16-byte boundary, and its first byte is the
last byte of the 16-byte block that holds the upper part of the load.

In the program below (`buf` is 1 KiB aligned and zero), `sd a3, 591(s6)` writes bytes 591..598 and
`lwu a4, 574(s6)` reads bytes 574..577. The two accesses do not overlap, so `a4` must be 0, but after a few
iterations the load returns `0x33220000`: the store's bytes for addresses 592 and 593.

DiffTest output on `df993b2527` (excerpt):

```text
[28] commit pc 0000000080000036 inst 23eb6703 wen 1 dst 14 data 0000000033220000 idx 013 (03)
[29] commit pc 000000008000003a inst 00071763 wen 0 dst 14 data 0000000000000000 idx 014 <--
     a4 different at pc = 0xffffbffd7c47057c, right = 0x0000000000000000, wrong = 0x0000000033220000
Core 0: ABORT at pc = 0xffffbffd7c47057c
```

The pc in the last two lines is not a program address; the commit trace shows the load (`lwu a4, 574(s6)`) at
`0x80000036`.

Variants: a single store/load pair without the loop passes; a `fence rw, rw` in the loop makes it pass; an aligned
`lhu a4, 576(s6)` instead of the misaligned load passes; a store that does not cross 16 bytes passes.



### Expected behavior



`lwu a4, 574(s6)` reads bytes 574..577, which no instruction writes, so `a4` is 0 in every iteration and the
program ends at the `li a0, 0` trap, as on NEMU.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - java version: openjdk version "17.0.20.1" 2026-08-18
  - mill version: 0.12.17
  - verilator version: 5.052
  - RISC-V toolchain for the workload: riscv-none-elf-gcc (xPack GNU RISC-V Embedded GCC) 15.2.0
- Repo
  - XiangShan commit id: `df993b2527` (kunminghu-v3 HEAD); also reproduced on `09453ff8f`
  - NEMU commit id (if difftest failed with NEMU): prebuilt `riscv64-nemu-interpreter-so` from the `ready-to-run` submodule (`4cf9983`)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig NO_ZSTD_COMPRESSION=1 OPT_FAST=-O3 -j10`
  - Run command: `./build/emu -i fwd_wrap.bin --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 200000`
  - Workload: `fwd_wrap.S`, `fwd_wrap.bin` and `link.ld` attached (source also below).

### To Reproduce

1. Build the workload:
   `riscv64-unknown-elf-gcc -march=rv64gc_zicsr -mabi=lp64d -nostdlib -nostartfiles -static -T link.ld -o fwd_wrap.elf fwd_wrap.S`
   and `riscv64-unknown-elf-objcopy -O binary fwd_wrap.elf fwd_wrap.bin`.
2. Run it with DiffTest against NEMU (command above). DiffTest stops at the load with the mismatch shown above.
   Without DiffTest the program ends at the trap after label `fail` (a0 = 1).

[fwd_wrap.bin.zip](https://github.com/user-attachments/files/33300665/fwd_wrap.bin.zip)

### Additional context

_No response_
