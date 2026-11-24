### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

`FEX0` bundles five functional units behind **one** Exu writeback port
(`BackendParams.scala:756`):

```scala
ExeUnitParams("FEX0", Seq(FaluCfg, FmulCfg, FcvtCfg, FcmpCfg, F2vCfg), ...)
```

with latencies FALU 1, FMUL 2, FCVT 3, FCMP 3. `FltIssueQueue` keeps two fixed-latency FUs
from completing in the same cycle using `WbFuBusyTable`: on issue it sets the bit for
`writebackSlot(latency, 3)`, and an entry may only issue if `writebackSlot(latency, 4)` is
clear (`FltIssueQueue.scala:710, 878, 896-897`).

**The reservation is gated on the register-write enable rather than on occupancy of the Exu
port** (`FltIssueQueue.scala:430-455`):

```scala
connectWbFuBusyTableIn(sink = ...intWbFuBusyTableIn, deqWen = out.deq.map(_.bits.gpWen), ...)
connectWbFuBusyTableIn(sink = ...fpWbFuBusyTableIn,  deqWen = out.deq.map(x => x.bits.fpWen || x.bits.gpWen), ...)
// and vpWen / v0Wen / vlWen for the three vector tables
```

A uop whose architectural destination is **`x0`** has `gpWen = 0` and `fpWen = 0`, so it
writes **none** of the five tables — yet its FU still executes and still drives FEX0's shared
`out.ex` valid at its latency. The slot therefore looks free to every later uop.

A subsequent FP op whose completion lands on that same cycle is not blocked, both FUs drive
the port together, and `FltExu.scala:127-141, 153-154` fires:

```
Assertion failed: FEX0 produced multiple Exu outputs in one cycle
```

This is **not only a debug check**. Immediately below the assertion:

```scala
out.uop.bits := Mux1H(outFuUopEx.map(x => x.valid -> x.bits))
```

`Mux1H` with two valid selects ORs its inputs, so the colliding cycle merges both uops'
payloads — data **and** control (`robIdx`, `pdest`). In our run the `fmul.d` is the op with a
live architectural destination, so its result is the one corrupted; the merged `robIdx` is
also the most plausible cause of the `robEntries 187 uopNum is overflow!` assertion that
fires immediately afterwards, after which the core stops committing.

## Evidence (waveform, `emu-fst` at `aa6b52033`)

`FEX0` = `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_fpRegion.issuePipes`.
Issues into FEX0 (`exu.in_uop_*`, FU from `exu.ex_0_bits_fuSel`):

| cycle | robIdx | latency | FU | gpWen | fpWen | pdest |
|---|---|---|---|---|---|---|
| 9372 | 172 | 3 | Fcvt | 1 | 0 | 69 |
| **9373** | **176** | **3** | **Fcvt** | **0** | **0** | **0** |
| **9374** | **171** | **2** | **Fmul** | 0 | 1 | 68 |
| 9375 | 187 | 3 | Fcvt | 0 | 1 | 70 |
| 9376 | 197 | 3 | Fcvt | 1 | 0 | 80 |

The only timestamp in the window with more than one FU output valid:

```
t = 9377   simultaneousOutCnt = 0b0010 (= 2)
           HIGH: Fcvt.out_ex_3 , Fmul.out_ex_2
```

`9373 + 3 = 9377` (the `x0`-destination `fcvt`, latency 3) and `9374 + 2 = 9377` (the `fmul`,
latency 2). `Falu.out_ex_*` stay low throughout, so the FMUL->FADD fusion path is not involved.

The relevant instructions in the attached reproducer:

```
80000600  fcvt.d.w  fa5, a1
8000060e  fsgnjn.s  ft2, ft2, fa1
80000618  fmul.d    ft9, fa5, fs1, rmm     <-- latency 2
8000061c  fcvt.l.s  a0,  fa1,  rne         <-- latency 3, writes a0
80000628  fcvt.wu.d zero, fs1, rup         <-- latency 3, writes x0: reserves nothing
```

Removing only the `fcvt.wu.d zero, ...` at `0x80000628` makes the run complete cleanly;
adding it back reproduces the assertion. That is exactly what the description above predicts.

### Expected behavior

Two functional units inside one `ExeUnit` never drive the shared Exu writeback port in the
same cycle, regardless of whether their results are architecturally discarded. An op targeting
`x0` still occupies the port for its latency and should reserve the corresponding slot.

### Environment

- Branch `kunminghu-v3`, commit `aa6b52033` (also reproduced on `20fd0d609`, cycle-identical).
- `SimTop`, RV64GCV, VLEN=128, stock `make sim-verilog DISABLE_PERF=1` + `make emu`
  (no modifications; RTL assertions enabled).
- Reference model: the tree's own `ready-to-run/riscv64-nemu-interpreter-so`.
- Verilator simulation, bare-metal M-mode test image loaded at `0x80000000`.

### To Reproduce

1. Build a stock difftest emu of `SimTop` at kunminghu-v3 `aa6b52033`.
2. Run the attached workload:
   ```
   ./build/emu -i repro_min.elf -C 15000 \
       --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe:
   ```
   Assertion failed at build/rtl/FEX0.sv:1479.
   Core 0: ABORT at pc = 0x80000614
   Core-0 instrCnt = 163, cycleCnt = 9,378
   Assertion failed: FEX0 produced multiple Exu outputs in one cycle
   ```
   Deterministic — byte-identical across repeated runs and across the two commits above.

[xs_v3_fex0_x0_dest_attachments.tar.gz](https://github.com/user-attachments/files/32719436/xs_v3_fex0_x0_dest_attachments.tar.gz)

### Additional context

_No response_
