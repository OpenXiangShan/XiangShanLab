### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Scalar floating-point **conversion** instructions fail to set the **inexact (`NX`,
`fflags[0]`)** accrued exception flag when the conversion result is inexact (i.e. the
value had to be rounded). The reference model (NEMU) sets `NX`; XiangShan leaves it 0.

Minimal observed case (from the attached stock `0cdabfc7c` run):

```
800002c0: fcvt.s.l  ft7, s1, rne        ; convert int64 -> float32, round-to-nearest-even
...
fcsr different at pc = 0x800002ea:  right (NEMU) 0x1 (NX set)   wrong (XS) 0x0 (NX clear)
```

At the `fcvt.s.l`, `s1 = 0xad334a4fe1f2bed4` = **−5,966,343,374,382,121,260**, whose
magnitude has **63 significant bits**. A single-precision float has only a 24-bit
mantissa, so this integer **cannot** be represented exactly — the conversion **must
round**, and per the RISC-V spec the **inexact (`NX`) flag must be raised**. NEMU raises
it (`fcsr.NX=1`); XiangShan does not (`fcsr.NX=0`).

Deterministic: reproduces byte-identically across repeated runs on a stock (non-
instrumented) `make emu` build with an unmodified difftest submodule.

## This is systematic, and localizes to the FP conversion unit

The same `NX`-not-raised behavior appears across **many different conversion instructions
and PCs**, not a single encoding corner. Both conversion directions are affected, e.g.:
- `fcvt.s.l` (int64 → float32) — the case above
- `fcvt.lu.s` (float32 → uint64) — also rounds when the source has a fractional part, and
  XS likewise does not set `NX`

So the defect is in the **FP conversion (CVT) path's fflags generation**: the inexact
condition is computed for the rounded result but not accrued into `fflags`.

### Expected behavior

For a floating-point conversion whose result is inexact (the exact value is not
representable in the destination format and is rounded), XiangShan must set the inexact
(`NX`) accrued exception flag in `fcsr.fflags`, matching the RISC-V spec and the reference
model. For `fcvt.s.l ft7, s1` with `s1` = `0xad334a4fe1f2bed4`, `fcsr.NX` must become 1.

### Environment

- Hardware
  - CPU: x86_64 server
  - Memory (GB): 512
- Software
  - Operating system: Linux
  - gcc version: gcc (conda-forge gcc 16.1.0-1) 16.1.0   (emu build compiler)
  - java version: openjdk 20.0.2-internal (mill)
  - mill version: 0.12.17
  - firtool version: 1.144.0
  - verilator version: Verilator 5.028 2024-08-21
- Repo
  - XiangShan commit id: `0cdabfc7c` (kunminghu-v3; **reproduced on this, the latest
    default-branch commit**, with a STOCK / non-instrumented emu and an unmodified difftest)
  - NEMU commit id (difftest reference): shipped `ready-to-run` `4cf9983`
  - difftest submodule: `45638f51`
- Build & Run
  - Build command: `make emu` for `SimTop`, rv64gcv, VLEN=128
  - Run command: `./build/emu -i id_1_1516.elf -C 20000 --diff <nemu-so>`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v3 `0cdabfc7c` (rv64gcv, VLEN=128) — a
   stock `make emu` build reproduces it (no instrumentation).
2. Run the attached workload:
   ```
   ./build/emu -i id_1_1516.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe (around `pc = 0x800002ea`): `fcsr different ... right = 0x1, wrong = 0x0` — NEMU
   set the inexact flag from the earlier `fcvt.s.l ft7,s1` at `0x800002c0` (`s1` has 63
   significant bits, so the int64→float32 conversion rounds), XiangShan did not.
   Deterministic.

Attachments (tarball `xs_v3_fcvt_inexact_nx_attachments.tar.gz`):
- `id_1_1516.elf` — reproducer binary
- `id_1_1516.disasm` — disassembly (see `fcvt.s.l` at `0x800002c0`)
- `stock_replay.log` — the stock-emu run showing the `fcsr` `NX` divergence

The workload is a bare-metal RV64 test; not hand-minimized. The trigger is the
`fcvt.s.l ft7, s1` at `0x800002c0` with a 63-significant-bit `s1`.

[xs_v3_fcvt_inexact_nx_attachments.tar.gz](https://github.com/user-attachments/files/31936046/xs_v3_fcvt_inexact_nx_attachments.tar.gz)

### Additional context

_No response_
