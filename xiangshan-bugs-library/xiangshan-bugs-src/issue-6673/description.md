### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


"All vector instructions, including `vset{i}vl{i}`, reset the `vstart` CSR to
zero" (V extension, `vstart`). On kunminghu-v3, when `vstart` holds a nonzero
value, a `vsetvli` resets it, but the vector arithmetic instruction that
follows still takes an illegal-instruction exception, as if `vstart` were
still nonzero:

```asm
  li      t0, 0x600
  csrs    mstatus, t0             # mstatus.VS = dirty
  li      t0, 4
  csrw    vstart, t0
  li      a1, 0
  vsetvli a1, a1, e8, m1, tu, mu  # resets vstart to 0
  li      t0, 0xAA
  vmv.v.x v8, t0                  # must execute (vstart = 0)
```

difftest (`right` = NEMU, `wrong` = XiangShan):

```
[27] commit pc 0000000080002018 inst 00829073 wen 0 dst 00 data 0000000000000004 idx 02d
[28] commit pc 000000008000201c inst 00000593 wen 1 dst 11 data 0000000000000000 idx 02e
[29] commit pc 000000008000201e inst 0005f5d7 wen 1 dst 11 data 0000000000000000 idx 02f
[30] commit pc 0000000080002022 inst 0aa00293 wen 1 dst 05 data 00000000000000aa idx 030
[31] exception pc 0000000080002026 inst 5e02c457 cause 0000000000000002 <--
mstatus different at pc = ..., right = 0x8000000a00000600, wrong = 0x8000040a00001e00
   mepc different at pc = ..., right = 0x0000000000000000, wrong = 0x0000000080002026
  mtval different at pc = ..., right = 0x0000000000000000, wrong = 0x000000005e02c457
 mcause different at pc = ..., right = 0x0000000000000000, wrong = 0x0000000000000002
```

`vstart` itself is not in the list: after the `vsetvli` it is 0 on both sides
(NEMU's dump: `vstart: 0x0`, `vl: 0x0`, `vtype: 0x0`). So the `vsetvli` did
reset `vstart`, but the `vmv.v.x` was checked against the value from before.
It does not need difftest: in a 32-iteration loop of the same sequence
instructions, run without a reference model, every iteration traps (the
emulator retires 432 instructions; 304 for the same image with `vstart`
written with 0; each trap replaces the `vmv.v.x` by a 5-instruction handler,
32 x 4 = 128). The exception depends on timing: one single-shot variant whose
code after the `vmv.v.x` differs did not trap without difftest.

### Expected behavior

The `vmv.v.x` executes (with `vl` = 0 here it changes nothing), as on NEMU,
Spike and kunminghu-v2, and the program reaches `test_exit`.


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
  - Run command: `./build/emu -i xs_vstart_vsetvli.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 300`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_vstart_vsetvli.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 300
   ```
3. Observe the illegal-instruction exception at the `vmv.v.x` and the mismatch at
   `instrCnt = 49` (the instructions before `main` are the boot and start-up code).

[xs_vstart_vsetvli.bin.zip](https://github.com/user-attachments/files/33040325/xs_vstart_vsetvli.bin.zip)

### Additional context

- On kunminghu-v2 (`e7bab53e66`) and on kunminghu-v3 `4cf8b8d4ae` (2026-07-24)
  the image runs to its end under difftest; it raises the exception on
  `e43c569f85` (2026-09-09) and on the latest commit. The new decoder came in
  with `29f48d8a6` ("feat(vector): new vector initial commit", 2026-08-06), in
  between.
- #6613 ("fix(CSR): flush pipeline on nonzero vstart write", kunminghu-v2 only)
  is about the write of `vstart` itself; here the `csrw vstart` is followed by a
  `vsetvli` that resets it.


### Possible cause

The new vector decoder raises the exception from the `vstart` value it sees at
decode time (`src/main/scala/xiangshan/backend/vector/Decoder/VecExceptionGen.scala`):

```scala
    // 3.3 vstart not zero
    val vstartNotZero = Ctx.vstart =/= 0.U && Dec.isVstartForceZero
```

`vstart` is reset only when the `vsetvli` commits (`Rob.scala`, `resetVstart`),
and the `vsetvli` does not flush the younger instructions:
`backend/vector/Decoder/DecodeChannel/VsetDecoder.scala` sets

```scala
  out.bits.flushPipe := isVSETVL
```

so only the register form `vsetvl` flushes. The old decoder (still on
kunminghu-v2, `DecodeUnitComp.scala`) also flushed after `vsetvli` / `vsetivli`,
and after any other vector instruction, when `vstart` was nonzero:

```scala
  // when vstart is not zero, the last uop will modify vstart to zero
  // therefore, blockback and flush pipe
  csBundle(numOfUop - 1.U).blockBackward := vstartReg =/= 0.U
  csBundle(0.U).flushPipe := vstartReg =/= 0.U
  ...
        csBundle(0).flushPipe := Mux(VSETOpType.isVsetvl(latchedInst.fuOpType), true.B, vstartReg =/= 0.U)
```

This is our reading of the source; we have not verified it by changing the RTL.
