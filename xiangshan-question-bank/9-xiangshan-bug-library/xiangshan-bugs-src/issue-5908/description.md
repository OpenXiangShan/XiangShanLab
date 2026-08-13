### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A short loop of cross-16B `sd` stores with unique-line stride deterministically fires the assertion at `NewStoreQueue.scala:1414` inside the inner `UnalignQueue` module:

```scala
XSError(enqPtr < deqPtr && !full, s"Something wrong in UnalignQueue!")
```

```
[0] %Error: UnalignQueue.sv:139: Assertion failed in
    TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_lsq.storeQueue.unalignQueue:
    Assertion failed at LogUtils.scala:132
%Error: build/rtl/UnalignQueue.sv:139: Verilog $stop
Aborting...
```

The reproducer (200 iterations, two cross-16B `sd`s per iteration, 32-byte stride) fires the assertion deterministically within 5,000 cycles (5/5 reruns). Halving the iteration count, removing the stride, removing the second `sd`, or aligning the base address each makes the assertion not fire in the same cycle window.

### Expected behavior

`UnalignQueue` should maintain its `enqPtr` / `deqPtr` invariant under any legal sequence of cross-16B stores, regardless of sbuffer occupancy or loop-trip count.

### Environment

- Hardware
  - CPU: Intel Xeon Platinum 8592+
  - Memory (GB): 1006
  - Storage (GB): 2000
- Software
  - Operating system: Ubuntu 24.04.3 LTS
  - gcc version: 13.2.0
  - clang version: -
  - java version: openjdk 21.0.10
  - mill version: 0.12.15
- Repo
  - XiangShan commit id: `cc942e98c8fdd4b0ad3a8bddc9bfbd125b1f5d81`
  - difftest commit id: `f9d0bb858c18133984a5efd476f59989c69cb23f`
  - NEMU commit id: not used (`--no-diff`)
  - SPIKE commit id: not used
- Build & Run
  - Build command: `make sim-verilog && make -C ./difftest emu NUM_CORES=1 RTL_SUFFIX=sv`
  - Run command: `./build/verilator-compile/emu --no-diff -i unalignq_enqptr_deqptr.bin -C 5000`

### To Reproduce

Run the attached binary:

```
./build/verilator-compile/emu --no-diff -i unalignq_enqptr_deqptr.bin -C 5000
```

The assertion fires deterministically before 5,000 cycles (5/5 reruns).

The full reproducer is 9 instructions:

```assembly
_start:
  li   s2, 200                  # iteration count
  li   t0, 0x8010000F           # base; offset 15 -> cross-16B sd
  li   t3, 0xDEADBEEF
  li   t5, 32                   # unique-line stride
.Lloop:
  sd   t3, 0(t0)                # first  cross-16B sd
  sd   t3, 16(t0)               # second cross-16B sd
  add  t0, t0, t5
  addi s2, s2, -1
  bnez s2, .Lloop
```

To rebuild from the attached `.S`:

```bash
riscv64-unknown-elf-gcc -nostdlib -nostartfiles -static \
    -march=rv64g -mabi=lp64 -T link.ld \
    -o unalignq_enqptr_deqptr.elf unalignq_enqptr_deqptr.S
riscv64-unknown-elf-objcopy -O binary unalignq_enqptr_deqptr.elf unalignq_enqptr_deqptr.bin
```

`link.ld` is a one-line linker script placing `.text` at `0x80000000`.

[unalignq_enqptr_deqptr.zip](https://github.com/user-attachments/files/27475354/unalignq_enqptr_deqptr.zip) — contains the `.S`, the pre-built `.bin`, the linker script, and the failing run log.

### Additional context

_No response_
