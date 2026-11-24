# [kunminghu-v3] ICacheMissUnit: a clean refill inherits corrupt/denied from the preceding back-to-back refill

## Before start

- [x] I have searched previous issues and PRs and did not find a report covering this specific refill-to-refill state leak.
- Reproduced on `182b2277c`, which was the default-branch HEAD when tested. Submission-time HEAD is `511ca530d11f90d411264c6a074a65cdb3b3c887`; the two subsequent commits modify backend code, not ICache or its dependencies. No claim of a new simulation on `511ca530` is made.

## Branch

`kunminghu-v3`

## Environment

- XiangShan commit `182b2277c0e4115e39ae396f7d705bc4e20e0def`
- `DefaultConfig`
- Chisel 7.13.0, firtool 1.149.0, OpenJDK 21, Linux x86_64
- Verilator server build reports `rev UNKNOWN.REV`
- The latest test rebuilt the XiangShan main Scala sources and generated fresh original/fixed RTL. Unchanged pinned dependency artifacts were reused.

## Describe the bug

When an erroneous refill A is immediately followed by a clean refill B, `ICacheMissUnit` can report A's `corrupt`/`denied` flags on B.

The error state is intended to be sticky across all beats of one refill, but it must be isolated from the next refill. The current implementation does not preserve that transaction boundary when the next refill starts in the cycle in which the previous response is being retired.

[ICacheMissUnit.scala:180–196](https://github.com/OpenXiangShan/XiangShan/blob/182b2277c0e4115e39ae396f7d705bc4e20e0def/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala#L180-L196):

```scala
when(io.memGrant.fire && edge.hasData(io.memGrant.bits)) {
  corruptReg := corruptReg || io.memGrant.bits.corrupt
  deniedReg  := deniedReg || io.memGrant.bits.denied
}.elsewhen(lastFireNext) {
  corruptReg := false.B
  deniedReg  := false.B
}
```

If B's first beat arrives in the `lastFireNext` cycle for A, the first branch takes priority over clearing the flags. A's error state is therefore retained and ORed into B, even though every beat of B is clean. Back-to-back responses are legal; `io.memGrant.ready` is always asserted.

Cycle-level sequence:

1. A's final data beat fires. A has `corrupt=1` and `denied=1`, so the registers hold those values.
2. In the next cycle, `lastFireNext=1` indicates that A's response is being completed.
3. B's first data beat also fires in that cycle, with both error bits clear.
4. The data-beat `when` branch wins over `elsewhen(lastFireNext)`, so the registers evaluate as `1 || 0` rather than being cleared for B.
5. B's response is consequently emitted with stale error flags.

This is an error-attribution violation even when the downstream system ultimately recovers or drops the affected response. The report does not claim that every occurrence produces an architectural trap.

## Expected behavior

Each refill's error flags should reflect only its own beats, regardless of the gap between consecutive refills.

## Reproduction

The reproducer elaborates the real `ICacheMissUnit` from the commit above using `DefaultConfig`. Its TileLink edge has a 32-byte data beat, so each 64-byte cache-line refill consists of two data beats. The testbench allocates two real fetch MSHRs through the module's request interface and supplies TileLink D-channel responses through `io.memGrant`; it does not replace the MissUnit with a handwritten model.

The test builds two variants:

- **ORIG:** the unmodified `ICacheMissUnit.scala` from the tested commit;
- **FIX:** the same module with the suggested two-line transaction-boundary change.

The testbench checks the error flags on both returned responses and includes positive and negative controls.

| Response sequence | Original | With fix |
|---|---|---|
| A corrupt+denied, B clean, one idle cycle | B clean | B clean |
| **A corrupt+denied, B clean, no idle cycle** | **B corrupt+denied** | B clean |
| A clean, B corrupt only, no idle cycle | B corrupt only | B corrupt only |
| Both clean, no idle cycle | Both clean | Both clean |

Original: one failed check. Fixed: all four checks pass. The clean-B/zero-gap case is the only failing original case, while the controls preserve the intended sticky behavior within a refill and correctly report an error that belongs to B.

The latest-source run was performed with freshly generated RTL. The output contains the following essential result:

```text
TEST: zero-gap (B beat0 at A lastFireNext)
  original: B corrupt=1 denied=1 (expected 0 0) [FAIL]
  fixed:    B corrupt=0 denied=0 (expected 0 0) [ok]
original errors=1
fixed errors=0
```

The supplied test program may return process exit code 0 even when its internal checks fail; the `errors` count and per-case result are the actual oracle.

## Suggested fix

Discard the previous refill's accumulated flags when accepting a beat in its `lastFireNext` cycle, while retaining the incoming beat's flags:

```diff
- corruptReg := corruptReg || io.memGrant.bits.corrupt
- deniedReg  := deniedReg || io.memGrant.bits.denied
+ corruptReg := (corruptReg && !lastFireNext) || io.memGrant.bits.corrupt
+ deniedReg  := (deniedReg && !lastFireNext) || io.memGrant.bits.denied
```

The change keeps the flags sticky for additional beats of A, starts B with a clean state when B's first beat overlaps A's completion cycle, and still preserves error flags arriving on B itself.

## Related work

#3413 and #4112 address receiving all beats and clearing state after flushed refills. #6404 concerns exception aggregation across fetch requests in MainPipe. This report concerns error-state leakage between consecutive refills inside MissUnit. The current accumulation logic is related to #4770.

The specific distinction is:

- #3413: a corrupt response must still deliver all of its beats;
- #4112: a flushed MSHR must not leave `corrupt` state permanently set;
- this report: a new, clean refill must not inherit error state from the immediately preceding refill when their D-channel beats are back-to-back.

## Scope and limitations

- The defect is reproduced on the real MissUnit module with a real TileLink edge and real MSHR allocation.
- The test injects a legal `corrupt`/`denied` response at the MissUnit boundary; it does not claim that a spontaneous L2 ECC or poison event was observed in a complete SoC.
- This report confirms the incorrect response flags at MissUnit output. It does not claim a software-visible instruction access fault, a specific `mepc`, data corruption, or a measured performance impact.
- The full ICache/IFU/core path was not used as the primary oracle because the module-level response mismatch isolates the state-leak root cause directly.
- The executed runner uses precompiled classes plus scalac (mode B). Its alternative checkout/mill path (mode A) was provided but not executed.

## Reproducer files

Source, build instructions, patch, and complete original/fixed logs: https://gist.github.com/ttkxclr/3a547915b37e43ebba4427842a428f71

The prepared reproducer contains:

- `MissUnitRepro.scala` — real MissUnit and TileLink edge;
- `AuditCfg.scala` — DefaultConfig audit parameters;
- `tb.cpp` — two-refill stimulus and controls;
- `fix.diff` — suggested fix;
- `split.py` and `run.sh` — RTL generation and Verilator build/run steps.

Tests and report prepared with AI assistance (OpenCode / GPT-6-Astra).
