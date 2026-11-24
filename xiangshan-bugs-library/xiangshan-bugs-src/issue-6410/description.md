### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。（Related: #5772 covers `vmv4r.v` only; this issue shows the missing check is class-wide via a test matrix.）
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史与提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支上的 commit `7bf51a8` 复现了不正确的行为。

### Branch

master (commit `7bf51a8`, MinimalConfig emu, VLEN=128)

### Describe the bug

Not just `vmv4r.v` (#5772): **the whole `FuType.vmove` class misses the vector-register-group alignment illegal-instruction check.** We confirmed this with a directed test matrix: for every tested instruction with a misaligned `vd`/`vs` register group (not a multiple of EMUL), the DUT executes and commits the instruction, while NEMU correctly raises illegal instruction (mcause=2, mtval=instruction encoding).

Test matrix (all difftest runs against `riscv64-nemu-interpreter-so`, deterministic, seed=0, 6/6 reruns):

| Instruction | Config | Misaligned operand | DUT behavior | Extra observation |
|---|---|---|---|---|
| `vmv.v.v v3, v0` | e32m2 | vd=v3 | executes, no trap | — |
| `vmv.v.x v3, t0` | e32m2 | vd=v3 | executes, no trap | — |
| `vmv.v.i v3, imm` | e32m2 | vd=v3 | executes, no trap | — |
| `vmerge.vvm` | e16m2 | vd misaligned | executes, no trap | vs2 source pointer lags: reads (v2,v3) instead of (v3,v4) |
| `vmerge.vvm` | e16m4 | vd misaligned | executes, no trap | corruption spreads to v6/v7 — reads uninitialized physical registers |
| `vmerge.vvm` | e64m2 | vd misaligned | executes, no trap | data architecturally correct, only the trap is missed |
| aligned control (same instructions, vd aligned) | e32m2 | — | GOOD TRAP | check passes when aligned |

Example trigger (`vmv.v.v v3, v0` at e32m2, encoding `0x5e0001d7`):

```assembly
li      a1, 32
vsetvli a0, a1, e32, m2, ta, ma
vmv.v.v v3, v0        # vd = 3 is not a multiple of EMUL=2 -> reserved encoding
```

### Expected behavior

Per the RISC-V V Extension v1.0 Specification (Vector Instruction Formats / Whole Vector Register Move, same clause cited in #5772):

> "The source and destination vector register numbers must be aligned appropriately for the vector register group size, and encodings with other vector register numbers are reserved."

Reserved encodings must trap as illegal instructions. NEMU does (mcause=2); XiangShan executes and writes back.

### Root cause (our analysis)

`VecExceptionGen.scala:262` gates the alignment check on `regNumIllegal = isVArithMem && (...)`, where `isVArithMem` comes from `FuType.scala:119-121`:

```scala
val vecArithOrMem = vecArith ++ vecMem   // vecMove = Seq(vmove) is NOT included
```

so every `FuType.vmove` instruction (vmv.v.v/x/i, vmerge.*, vmv1r-8r.v, vmv.x.s, vfmv/vfmerge) bypasses the check at decode time, and `VMove.scala` performs no second alignment check at execute time. Fix direction: include `vecMove` in the alignment-check coverage.

Additionally, beyond the missed trap, the e16m2/e16m4 `vmerge.vvm` runs show an execute-side source-pointer corruption (vs2 read window off by one group / spreading to uninitialized physical registers) — possibly a separate issue in the uop-split wiring; we have not yet localized it to a signal.

### mismatch details

```
emu (MinimalConfig, VLEN=128, Verilator), REF = riscv64-nemu-interpreter-so
vmv.v.v v3, v0 @ e32m2:
   mcause different at pc = 0x008000006c, right = 0x2, wrong = 0x0
    mtval different at pc = 0x008000006c, right = 0x5e0001d7, wrong = 0x0
     mepc different at pc = 0x008000006c, right = 0x80000030, wrong = 0x0
  mstatus different at pc = 0x008000006c, right = 0x8000040a00001e00, wrong = 0x8000000a00000600
Core 0: ABORT at pc = 0x8000006c

vmerge.vvm @ e16m4 (execute-side corruption, DUT vector regs after the op):
  v6 : <uninitialized garbage>, v7 : <uninitialized garbage>   (REF keeps sentinel 0x70707070...)
```

Reproducer ELFs and full difftest logs are available; each case is a bare-metal single-instruction harness (mtvec + mstatus.VS enable + sentinel fill of v0..v7 + readback via `vse32.v`). Happy to attach them or a minimal .img if useful.

Supersedes/extends #5772 from a single instruction (`vmv4r.v`) to the whole vmove class confirmed by a test matrix.
