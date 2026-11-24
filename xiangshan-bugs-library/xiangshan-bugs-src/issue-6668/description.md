### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A read of `vl` or `vlenb` whose destination is `x0` (`csrrs x0, vl, x0`, i.e.
`csrr zero, vl`) is a legal instruction that reads the CSR and writes nothing.
On kunminghu-v3 it is decoded with an integer register write to `x0`: the rename
stage asserts, and the rename table reports that `x0` was given a new physical
register.

```asm
main:
  li      t0, 0x200          # mstatus.VS = Initial
  csrs    mstatus, t0
  li      a0, 1
  csrr    zero, vl           # 0xc2002073, csrrs x0, vl, x0
  li      a0, 2
  j       test_exit
```

With difftest, and equally without it (`--no-diff`), the simulation stops
before the `csrr` commits (instrCnt = 38; the start-up code clears x1-x31 first):

```
Assertion failed at .../build/rtl/RenameTableWrapper.sv:930.
Assertion failed at .../build/rtl/Rename.sv:5458.
The simulation stopped. There might be some assertion failed.
Core 0: ABORT at pc = 0x185e2bbf3c2bc
Core-0 instrCnt = 38, cycleCnt = 8,321, IPC = 0.004567
Assertion failed at LogUtils.scala:133
Assertion failed: rfWen cannot be 1 when Int regfile ldest is 0
[ERROR][time=                8325] SimTop.cpu.l_soc.core_with_l2.core.backend.inner.ctrlBlock.rename.rat: pdest for $0 should be 0
```

`csrr zero, vlenb` (`xs_csrr_x0_vlenb`) stops in the same way. `csrr zero, vtype`
(`xs_csrr_x0_vtype`) does not, and neither do `csrrs x0, c, x0` / `csrrc x0, c, t6`
on fflags, frm, fcsr, vstart, vxsat, vxrm and vcsr (`xs_csr_x0_seq`). In a build
without assertions the second message suggests that `x0` would afterwards read as
the value of `vl`.

### Expected behavior

The instruction reads `vl` (or `vlenb`) and discards the value; no register is
written and the program goes on, as on kunminghu-v2, NEMU and Spike.

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
  - Run command: `./build/emu -i xs_csrr_x0_vl.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 1000`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image, with or without the reference model:
   ```
   ./build/emu -i xs_csrr_x0_vl.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 1000
   ./build/emu -i xs_csrr_x0_vl.bin --no-diff -I 1000
   ```
3. Observe the two assertions and the `pdest for $0 should be 0` error at `instrCnt = 38`.

[xs_csrr_x0_vl.elf.zip](https://github.com/user-attachments/files/33040057/xs_csrr_x0_vl.elf.zip)

### Additional context

### Possible cause

`csrr rd, vl` and `csrr rd, vlenb` are decoded by the pseudo-instruction channel
(`PseudoDecodeChannel.scala`, patterns `CSRRVL` / `CSRRVLENB`, which match any
`rd`), and that channel takes the GP write enable straight from its table:

```scala
// src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala:92-93
  out.bits.ldest := instBitFields.RD
  out.bits.gpWen := bundle(gpWenField)
```

The other decode channels clear it when `rd` is `x0`:

```scala
// SimpleDecodeChannel.scala:149
out.uop(i).bits.renameInfo.gpWen := uopInfos(i).bits.gpWen && instFields.RD =/= 0.U
// VsetDecoder.scala:116
out.bits.renameInfo.bits.gpWen := uop.gpWen && instFields.RD =/= 0.U
// VectorDecodeChannel.scala:248
out.uop(i).bits.renameInfo.gpWen := uopInfos(i).bits.gpWen && srcSelectModule.out.src(i).dest =/= 0.U
```
