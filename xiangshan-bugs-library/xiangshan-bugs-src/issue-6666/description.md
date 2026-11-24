### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


Below M mode, when the governing `xenvcfg.CBIE` field is `01`, `cbo.inval` must
be executed as a flush (`cbo.flush`): the block's contents do not change. On
XiangShan the instruction **writes zeros to the whole cache block**, as
`cbo.zero` would.

```asm
  la      s0, buf                 # a 64-byte-aligned block
  li      t1, 0x1122334455667788
  sd      t1, 0(s0)
  sd      t1, 56(s0)
  li      t0, 0x30                # menvcfg: CBIE = 11
  csrw    menvcfg, t0
  li      t0, 0x10                # senvcfg: CBIE = 01 -> in U mode cbo.inval performs a flush
  csrw    senvcfg, t0
  ...                             # mret to U mode
  cbo.inval (s0)                  # must not change memory
  ld      a0, 0(s0)               # must read 0x1122334455667788
```

difftest, at the `cbo.inval` (pc `0x8000207c`):

```
NEMU does not commit any store instruction.

==============  Store Commit Event (Core 0)  ==============
Mismatch for store commits
  DUT commits addr 0x0000000080003000, data 0x0000000000000000, mask 0xff, pc 0x000000008000207c, robidx 0x43
  DUT origin: addr 0x0000000080003000, high:0x0000000000000000, low 0x0000000000000000, mask:0xffff
```

The design commits a store of zeros to the block at the `cbo.inval`; the
reference commits none. The same happens in S mode with `menvcfg.CBIE` = 01
(`xs_cbo_inval_as_flush_s`, pc `0x80002084`).

A self-checking image (`xs_cbo_inval_as_flush_u_selfcheck`, run without
difftest so that no reference model is involved) reads the block back after the
`cbo.inval`: the pattern written before is gone, and the program ends at its
`changed` label (`0x800020c0`).

In M mode the four `cbo.*` instructions behave correctly (`xs_cbo_inval_m`, no
mismatch).

### Expected behavior

With `CBIE` = 01 the `cbo.inval` should perform a flush, leaving the block's
contents unchanged, as NEMU and Spike do.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv-none-elf-gcc (xPack) 15.2.0 (reproducer build)
  - java version: openjdk 17.0.20.1
  - mill version: 0.12.17
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192` (kunminghu-v3, **the latest default-branch commit**)
  - NEMU commit id: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `4c64fe86b17e0564232b52bb3800d9b15c45a3a0`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"` (g++ does not accept difftest's default `-fno-slp-vectorize`)
  - Run command: `./build/emu -i xs_cbo_inval_as_flush_u.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_cbo_inval_as_flush_u.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000
   ```
3. Observe the store-commit mismatch at `instrCnt = 70`.
4. Optionally run the self-checking image without a reference:
   ```
   ./build/emu -i xs_cbo_inval_as_flush_u_selfcheck.bin --no-diff -I 3000
   ```
   It spins at `same` when the block still holds the pattern and at `changed`
   (`0x800020c0`) when it does not; XiangShan stops at `changed`.

[cbo_inval.zip](https://github.com/user-attachments/files/32987495/cbo_inval.zip)

### Additional context

### Possible cause

The decoder appears to OR two opcodes together. In
`src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/SimpleDecodeChannel.scala`:

```scala
val cboOpcode = resultInstCboI2f(CboOpcodeField)        // cbo_flush when cboI2F is set and the instruction is cbo.inval
...
out.uop(i).bits.opcode := opcodes(i) | cboOpcode        // line 151
```

`opcodes(i)` is still `cbo_inval` for this instruction, and the CBO sub-opcodes
in `Opcode.scala` are `clean = 000`, `flush = 001`, `inval = 010`, `zero = 011`,
so `cbo_inval | cbo_flush` has the sub-opcode `010 | 001 = 011`, which is
`cbo_zero`. This is our reading of the source and matches the observed store of
zeros; we have not verified it by changing the RTL. The line came in with
`dfcb5748e` ("feat(decode,mem): add ZICBO extension and unify cbo opcode via
LSUOpType", 2026-08-26).
