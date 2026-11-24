### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


`aes64ks1i` with a reserved round number retires and writes its destination
instead of raising an illegal-instruction exception. The scalar-crypto chapter
says *"rnum must be in the range 0x0..0xA. The values 0xB..0xF are reserved"*.
This was reported in #3838 and fixed by #3845, which made the decoder raise
illegal-instruction for these encodings; kunminghu-v2 still does. On
kunminghu-v3 the check is gone again.

```asm
  la      t0, mh
  csrw    mtvec, t0
  li      t1, 0x0123456789abcdef
  .word   0x31b31393              # aes64ks1i t2, t1, 0xB  (reserved rnum)
  j       test_exit
mh:
  csrr    a0, mcause              # 2 expected
  csrr    a1, mtval
  j       test_exit
```

difftest, at the `aes64ks1i` (left = NEMU, right = XiangShan):

```
     t2 different at pc = ..., right = 0x0000000000000000, wrong = 0x857c266f857c266f
     a0 different at pc = ..., right = 0x0000000000000002, wrong = 0x0000000000000000
mstatus different at pc = ..., right = 0x0000040a00001800, wrong = 0x0000000a00000000
   mepc different at pc = ..., right = 0x0000000080002034, wrong = 0x0000000000000000
  mtval different at pc = ..., right = 0x0000000031b31393, wrong = 0x0000000000000000
 mcause different at pc = ..., right = 0x0000000000000002, wrong = 0x0000000000000000
```

NEMU (and Spike) take the illegal-instruction trap with `mtval` = the
encoding; XiangShan executes the instruction.

### Expected behavior

Raise an illegal-instruction exception, as after #3845 and as kunminghu-v2
does.

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
  - Run command: `./build/emu -i xs_aes64ks1i_reserved_rnum.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_aes64ks1i_reserved_rnum.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000
   ```
3. Observe the `t2` / `mcause` / `mepc` / `mtval` mismatch at `instrCnt = 53`
   (the first 32 instructions are start-up code that clears x1-x31).

[xs_aes64ks1i_reserved_rnum.elf.zip](https://github.com/user-attachments/files/32987745/xs_aes64ks1i_reserved_rnum.elf.zip)

### Additional context

### Possible cause

The check added by #3845 is still defined but no longer used. In
`src/main/scala/xiangshan/backend/decode/isa/bitfield/RiscvInst.scala`:

```scala
def isRnumIllegal = {
  this.RNUM > 0xA.U
}
```

kunminghu-v2 applies it in `DecodeUnit.scala` (`isAes64ks1iIllegal`), and so
did kunminghu-v3 up to `29f48d8a6~1`. From `29f48d8a6` ("feat(vector): new
vector initial commit", 2026-08-06) on, nothing in `src/main/scala` calls
`isRnumIllegal`, so the new decode path never raises the exception. This is our
reading of the source; we have not verified it by changing the RTL.
