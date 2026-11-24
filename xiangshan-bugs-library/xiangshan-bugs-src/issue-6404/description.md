### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

On the second box: [#3215](https://github.com/OpenXiangShan/XiangShan/pull/3215), [#4814](https://github.com/OpenXiangShan/XiangShan/pull/4814), [#5733](https://github.com/OpenXiangShan/XiangShan/pull/5733), [#6207](https://github.com/OpenXiangShan/XiangShan/pull/6207) and the `FIXME` at `Ifu.scala:224` are related and are discussed under Additional context; none of them reports this case, and #5733 states the assumption it rests on.

### Branch

kunminghu-v3

### Describe the bug

`ICacheMainPipe` reduces the TileLink `corrupt` and `denied` results of **all four cache lines across both fetch requests** into a single `ExceptionType`, and then assigns that one value to every fetch request. A failing L2 response on a cache line belonging to fetch request 1 therefore raises an exception on fetch request 0, whose own two cache lines returned good data. Per `ExceptionType.fromTileLink` that is an instruction access fault when the response is `corrupt` and `denied`, and a hardware error when it is `corrupt` without `denied`.

The flatten is where the two requests stop being distinguishable:

```scala
// ICacheMainPipe.scala:378
  private val s1_portValid = VecInit(s1_lineValid.flatten)
```

The per-line mask below is correct: a line that is not part of a live request contributes nothing. What is not per-request is the `reduce` on the last line, which merges the four masked results into one value:

```scala
// ICacheMainPipe.scala:380-388
  // also raise af if l2 corrupt is detected
  private val s1_tlException =
    (s1_tlCorrupt.flatten zip s1_tlDenied.flatten).zipWithIndex.map { case ((corrupt, denied), i) =>
      val portValid   = s1_portValid(i)
      val realCorrupt = corrupt && portValid
      val realDenied  = denied && portValid
      val canAssert   = s1_valid && portValid
      ExceptionType.fromTileLink(realCorrupt, realDenied, canAssert)
    }.reduce(_ || _)
```

`s1_tlCorrupt` and `s1_tlDenied` are both indexed by fetch request first, so `.flatten` maps `[request][port]` to a flat index in which entries 0 and 1 are request 0's two cache lines and entries 2 and 3 are request 1's. `ExceptionType.||` is `Mux(this.hasException, this, that)`, so the reduce returns the lowest flat index that carries any exception.

That single value is then merged and fanned out:

```scala
// ICacheMainPipe.scala:390-391
  // merge all exceptions, itlb/pmp has the highest priority, then l2/ecc
  private val s1_exceptionOut = s1_exception || s1_tlException
```

```scala
// ICacheMainPipe.scala:394-406
  io.toIfu.req.bits.zipWithIndex.foreach { case (req, i) =>
    req.valid                := s1_req(i).valid
    req.startVAddr           := s1_req(i).startVAddr
    req.ftqIdx               := s1_req(i).ftqIdx
    req.takenCfiOffset.valid := s1_req(i).taken
    req.takenCfiOffset.bits  := s1_takenCfiOffset(i)
    req.range                := Fill(FetchBlockInstNum, 1.U(1.W)) >> (~s1_takenCfiOffset(i)).asUInt
    req.size                 := s1_takenCfiOffset(i) +& 1.U
    req.data                 := s1_data(i)
    req.maybeRvcMap          := s1_maybeRvcMap(i)
    req.perf_isCrossLine     := s1_isCrossLine(i)

    req.icacheMeta.exception          := s1_exceptionOut
```

The assignment is inside the loop over `i` and reads no `i`, so both requests receive the same `ExceptionType`.

The IFU then attributes it to the first enqueued instruction, which belongs to request 0:

```scala
// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:536-543
  // if icache respond with exception, it's marked on entire cacheline,
  // so the first enqueued instr should be marked with exception
  // otherwise, we only have rvcException, so select its offset
  io.toIBuffer.bits.exceptionMask := Mux(
    s2_icacheMeta(0).exception.hasException,
    VecInit.tabulate(IBufferEnqueueWidth)(i => if (i < IfuAlignWidth) i.U === s2_alignShiftNum else false.B),
    VecInit.tabulate(IBufferEnqueueWidth)(i => enq(i) & s2_rvcIll(i))
  )
```

The comment above it describes the granularity for the case it was written for: one fetch block, whose exception is marked on its entire cache line. The mark now lands on a pair of independently addressed fetch blocks.

**This is not the only request-0-only path in s1, and some of it is already marked.** Reporting the TileLink dimension without saying so would misrepresent the state of the code:

```scala
// ICacheMainPipe.scala:335-336
  // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next, note this `||` is overloaded
  private val s1_exception = s1_exceptionInfo(0).itlbException || s1_pmpException
```

The iTLB exception comes from entry 0 alone. The PMP request is built from `s1_req(0).startVAddr` and `s1_pTag`, and `s1_pTag` is `s1_wayLookupEntry(0).pTag` (`ICacheMainPipe.scala:208`, `:329`). That same tag is what `:412` uses to form request 1's own `icacheMeta.pAddr`. On the IFU side the ECC merge carries the project's own note:

```scala
// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:222-227
  s1_icacheMeta.zipWithIndex.foreach { case (meta, i) =>
    meta.exception := s1_icacheMetaIn(i).exception || ExceptionType.fromEcc(
      io.fromICache.corrupt(i).reduce(_ || _), // FIXME: consider which cacheline is corrupted, and modify exceptionMask
      s1_valid
    )
  }
```

The per-request meta is also not fully consumed. `src/main/scala/xiangshan/frontend/ifu/Ifu.scala` carries ten reads of the form `<meta>(idx).exception`. Nine take a literal index 0: `:171`, `:231`, `:329`, `:528`, `:535`, `:540`, `:565`, `:614`, `:617`. The tenth is `:223` above, inside the loop that rebuilds the pair. So `icacheMeta(1).exception` is written and never consumed.

This report therefore covers one dimension of that separation, TileLink `corrupt` and `denied`, and does not claim the rest is correct. One case looks worse than the one reported here: `s1_exceptionInfo(1).itlbException` is dropped rather than misattributed, so request 1's own translation fault raises nothing at all. It is outside this report only because it was not measured.

#### What was measured

**Elaborated RTL, exhaustively.** The attribution cone was elaborated and its deciding input swept completely: four line states over four cache lines (not requested, clean, `corrupt` without `denied` which yields a hardware error, and `corrupt` with `denied` which yields an access fault), times both values of the stage-valid bit. That is 512 inputs, the whole distinguishable input space of the cone. It was run at [a7fc87df1](https://github.com/OpenXiangShan/XiangShan/commit/a7fc87df1e82211eb47f3c74a8c93cd9d425572b) and again at [91ea1a167](https://github.com/OpenXiangShan/XiangShan/commit/91ea1a167a2a8a598d514c6cc840d7c0804c2e6d), which was the tip of `kunminghu-v3` when this report was written, with the same result on every input. `ICacheMainPipe.scala` and `frontend/Bundles.scala` are byte-identical between the two commits, so every line number quoted above holds on either checkout. `Ifu.scala` is not byte-identical, but its first difference is at line 670, past every line this report names in it; both regions quoted from it re-read unchanged.

- On all 96 inputs whose only failing line belongs to request 1, request 0 receives the exception. The 96 mirror inputs behave the same way in reverse.
- On all 512 inputs both requests receive the same value, so the cone never distinguishes the two fetch blocks.

**Full core, with a causal intervention.** Three arms on the whole core under one workload, run at [a7fc87df1](https://github.com/OpenXiangShan/XiangShan/commit/a7fc87df1e82211eb47f3c74a8c93cd9d425572b) and again at [91ea1a167](https://github.com/OpenXiangShan/XiangShan/commit/91ea1a167a2a8a598d514c6cc840d7c0804c2e6d), with the same three results.

- Control: reaches a real FTQ, way-lookup and request-1 miss and refill chain, injects nothing, takes no architectural trap.
- Injection: same chain, exactly one `denied` response after the naturally reached request-1 refill. Traps once with `mcause=1` and `mepc = mtval = 0x80010020`, the address of fetch request 0.
- Causal: changes only the IFU's request-selection paths, same workload and same injection predicate. Traps once at `mepc = mtval = 0x80010040`, the address of request 1.

The architectural attribution moves with that one source change and with nothing else. Between the two runs only the source tree differs: 57 files changed between the two commits, `ICacheMainPipe.scala` is not one of them, and the injected and patched forms of that file are byte-identical in both runs.


### Expected behavior

Each fetch request's `icacheMeta.exception` should reflect only that request's own cache lines. When the only failing line belongs to request 1, request 0's instructions should execute normally and the trap should land on the first instruction of request 1, with `mepc` and `mtval` naming that instruction. Request 0, whose lines returned good data, should not take an instruction access fault at all.

Software sees the current behaviour as an instruction access fault at a PC whose own cache line is fine, so the fault reads as unexplained.


### Environment

These fields describe the full-core arms, which are the part that needs an environment to reproduce. They ran on a Slurm cluster node.

- Hardware
  - CPU: AMD EPYC 9534 64-Core Processor, 8 cores allocated to the job (`SLURM_CPUS_PER_TASK=8`)
  - Memory (GB): 128 requested
  - Storage (GB): cluster scratch
- Software
  - Operating system: Rocky Linux 9.8 (Blue Onyx), kernel 5.14.0-687.29.1.el9_8.x86_64
  - gcc version: `g++ (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`, inside the container below
  - clang version: not used
  - java version: 17.0.19, Eclipse Adoptium
  - mill version: 0.12.17
- Build & Run
  - Build command: `make -C difftest verilator-emu SIM_TOP=SimTop NUM_CORES=1 RTL_SUFFIX=sv WITH_CHISELDB=0 WITH_CONSTANTIN=0 -j 8`, run inside an Apptainer image.
  - Run command: one workload per arm, `MAX_CYCLES=1000000`, `CONFIG=MinimalConfig`; one failing TileLink response is injected on a naturally reached request-1 refill in two of the three arms, setting `corrupt` and `denied` together so it is an access fault. This is not `DefaultConfig`; Additional context says why that does not reach the dimension this report is about.
  - Simulator: `Verilator 5.048 2026-04-26 rev v5.048`, on Ubuntu 24.04.4 LTS, in an Apptainer image built from `docker://verilator/verilator:v5.048`.
  - firtool: 1.149.0.
- Repo
  - XiangShan commit id: `a7fc87df1e82211eb47f3c74a8c93cd9d425572b` for the analysis and the full-core arms, `91ea1a167a2a8a598d514c6cc840d7c0804c2e6d` for the head re-run of both the cone sweep and the full-core arms. The commit under analysis is `d97789de12ea371909ebc1bf89a4fbaf136bb994`, `feat(Frontend): implement 2-fetch`, merged by [#5996](https://github.com/OpenXiangShan/XiangShan/pull/5996).
  - NEMU commit id (if difftest failed with NEMU): not applicable, this was not found by difftest. The full-core arms read `mepc` and `mtval` directly.
  - SPIKE commit id (if difftest failed with SPIKE): not applicable, same reason.

The 512-input cone sweep is a standalone Verilator harness on the elaborated cone, built with Chisel 7.13.0 and firtool 1.149.0 and run under `Verilator 5.049 devel rev v5.048-230-gde0236be2 (mod)`. It ran on a separate workstation. Its fields are not listed because what a reader needs in order to rebuild it is the elaborator and the simulator named above; the sweep produced the same 512 rows at `a7fc87df1e82211eb47f3c74a8c93cd9d425572b` and at the head.

This report was prepared with AI assistance. The model is Claude Opus 5.

### To Reproduce

**By reading.** The three quotations above, `ICacheMainPipe.scala:378`, `:381-388` and `:406`, are the whole argument: the flatten spans both requests, the reduce runs over the flat sequence, and the assignment sits inside the per-request loop without reading its index.

**By running.**

1. Elaborate the cone and sweep its 512 distinguishable inputs, recording which request the exception is attributed to. Every input whose only failing line belongs to request 1 attributes it to request 0. Run at both commits above.
2. On the full core, reach a natural request-1 miss and refill, inject one failing response on that refill with `corrupt` and `denied` set together, and read `mepc`. It is request 0's address. Change only the IFU's request-selection paths and it becomes request 1's. Run at both commits above.

### Additional context

**Related public history.** Pull requests [#3215](https://github.com/OpenXiangShan/XiangShan/pull/3215) and [#4814](https://github.com/OpenXiangShan/XiangShan/pull/4814), both merged, are the same broad class: a per-index ICache exception used for an index that was not real, on the previous generation's structure and in the opposite direction. Neither describes this case: they are about an index that is not part of the request, and this is about a line that is real and valid but belongs to the other request.

**The assumption this report is about is already written down, by its author.** Pull request [#5733](https://github.com/OpenXiangShan/XiangShan/pull/5733), merged 2026-07-14 and therefore inside the analysed pin, says of the IFU-side mask:

> The current `exceptionMask` assumes that the exceptions for the two fetchBlocks are the same. This is correct for page faults, guest page faults, and access faults, but not for hardware errors caused by parity checks, which can **only** occur in the second cache line of the first fetchBlock, the first cache line of the second fetchBlock, or the second cache line of the second fetchBlock.

The principle behind that split is sound, and this report is one entry on the wrong side of the list rather than a disagreement with the principle.

- A translation fault is a property of a page, which both fetch blocks share for as long as a 2-fetch stays inside one page. A parity error is a property of one cache line, which they do not share. That is the right axis to split on.
- A TileLink `corrupt` or `denied` is a property of one line, exactly like a parity error. The comment directly above the reduce says what it is turned into: `// also raise af if l2 corrupt is detected`.
- So it is an access fault by type and a per-line condition by origin. It is named on the shared side of that list and behaves like the unshared side, which is the whole of this report.

The three positions in that sentence are the cases where a per-line condition breaks the assumption, and the one position it leaves out is the first cache line of the first fetch block, which is where the mask already points. A TileLink `corrupt` or `denied` is per-line, so it breaks the assumption at the same three positions and at no others. One half of that set already has an owner in this repository and the other has none:

- The second cache line of the **first** fetch block is a question of which line inside one block failed, and it is `Ifu.scala:224`'s own `FIXME: consider which cacheline is corrupted, and modify exceptionMask`. This report does not touch it.
- Both cache lines of the **second** fetch block are a question of which fetch block failed. That is this report, and nothing in the tree distinguishes them, because the producer has already merged the two requests before the IFU sees either.

**What is already in flight, and what it does not reach.** Pull request [#6207](https://github.com/OpenXiangShan/XiangShan/pull/6207), open, is the follow-up #5733 asks for: it modifies the IFU-to-IBuffer `exceptionMask` so an exception can be marked on the first instruction of either fetch block. It does not reach the expression above — `ICacheMainPipe.scala:380-391` and `:406` are byte-identical between the analysed pin and its head `170961f4c` — and that is deliberate on its part, because the two halves are different halves. It builds the consumer; the defect reported here is in the producer.

**Merging #6207 alone would not change this behaviour.** Every per-block arm it adds is selected by entry 0 first, and entry 0 reports an exception whenever entry 1 does, because `:406` assigns the same `s1_exceptionOut` to both. At its head `170961f4c` the exception type is:

```scala
// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:543-550 @170961f4cdc59034c52f34263a144447a6907e31
  io.toIBuffer.bits.exceptionType := MuxCase(
    s2_rvcException,
    Seq(
      s2_icacheMeta(0).exception.hasException -> s2_icacheMeta(0).exception,
      s2_firstHasRvcIll                       -> s2_rvcException,
      s2_icacheMeta(1).exception.hasException -> s2_icacheMeta(1).exception
    )
  )
```

The mask is the same shape at `:564-571`, `exceptionCrossPage` at `:562` requires `!s2_icacheMeta(0).exception.hasException` for its entry-1 arm, and the index that decides which instruction the mask lands on branches on entry 0 as well:

```scala
// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:273-277 @170961f4cdc59034c52f34263a144447a6907e31
  private val s1_icacheExceptionIdx = Mux(
    s1_icacheMeta(0).exception.hasException,
    s1_alignShiftNum,
    s1_alignShiftNum +& s1_firstInstrCount
  )
```

For a TileLink `corrupt` or `denied` on request 1's line, `s2_icacheMeta(0).exception.hasException` is true, so the first arm wins in both `MuxCase`s, the `Mux` above picks `s1_alignShiftNum`, and the trap still lands on the first instruction of fetch block 0. The entry-1 arms #6207 adds are unreachable for this signal for as long as the producer hands both entries the same value. The two changes are halves of one repair: with both, those arms are the ones that fire.

That distinction matters for the fix. The branch already gates this expression by per-line validity, which repairs the not-part-of-the-request case and works correctly at the pin. What the 2-fetch change did not do is carry that principle into the request dimension it created.

**Suggested change, ICache side only. NOT safe to apply on its own.** The patch below keeps the per-line mask, moves the reduce inside one fetch request, and makes the fan-out per request. `s1_portValid` is removed because the expression above is its only user. `s1_exceptionOut` has a third user, `accessTrace.exception` at `:569`, which the patch subscripts to entry 0 to match the rest of that trace.

Applying it alone would make things worse. Today request 1's TileLink error reaches the IFU through `icacheMeta(0).exception`, misattributed but trapping. After this patch it reaches only `icacheMeta(1).exception`, which no consumer reads, so request 1's corrupt or denied data would enter the IBuffer with no trap at all. A silent wrong instruction is worse than a trap at the wrong PC.

That request 1's metadata reaches nobody is visible in the generated RTL and not only in the source. `Ifu.scala:134` maps over both requests, but every later use indexes entry 0, so the emitted `ICacheMainPipe.sv` names `io_toIfu_req_bits_0_icacheMeta` on 16 lines and `io_toIfu_req_bits_1_icacheMeta` on none. That holds in both arms above: the port is dead in the tree this report was measured on, and the patch would route request 1's exception into it. #6207 is what gives that port a consumer, in source; the elaborations above are of the pin and of the pin plus this patch, and neither of them includes #6207.

The patch therefore needs an IFU-side change with it: `exceptionMask` has to be able to land on the first instruction of request 1, and the exception the IFU consumes has to be formed from both entries. That is what #6207 already does, so the ordering decides what else is needed.

- **If #6207 lands first, this patch is the whole of the remaining change.** Its entry-1 arms stop being unreachable the moment the producer stops handing both entries the same value, and nothing further is required.
- **If this patch lands first, it needs a stopgap or it makes things worse.** The smallest one is to have the IFU consume `icacheMeta(0).exception || icacheMeta(1).exception` instead of entry 0 alone. That is behaviour-neutral: the trap still happens and is still charged to request 0, exactly as today. It removes the silent loss and does not fix the attribution, and it becomes unnecessary once #6207 is in.

```diff
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
@@ -375,20 +375,23 @@
 
   private val s1_fetchFinish = !s1_shouldFetch.reduce(_ || _)
 
-  private val s1_portValid = VecInit(s1_lineValid.flatten)
-
   // also raise af if l2 corrupt is detected
-  private val s1_tlException =
-    (s1_tlCorrupt.flatten zip s1_tlDenied.flatten).zipWithIndex.map { case ((corrupt, denied), i) =>
-      val portValid   = s1_portValid(i)
-      val realCorrupt = corrupt && portValid
-      val realDenied  = denied && portValid
-      val canAssert   = s1_valid && portValid
-      ExceptionType.fromTileLink(realCorrupt, realDenied, canAssert)
+  // NOTE: reduce WITHIN one fetch request only. The two requests address
+  // independently chosen fetch blocks, so a TileLink error on one request's
+  // cache line must not raise an exception on the other request.
+  private val s1_tlException = VecInit((0 until MaxFetchReqNum).map { reqIdx =>
+    (s1_tlCorrupt(reqIdx) zip s1_tlDenied(reqIdx)).zipWithIndex.map {
+      case ((corrupt, denied), portIdx) =>
+        val portValid   = s1_lineValid(reqIdx)(portIdx)
+        val realCorrupt = corrupt && portValid
+        val realDenied  = denied && portValid
+        val canAssert   = s1_valid && portValid
+        ExceptionType.fromTileLink(realCorrupt, realDenied, canAssert)
     }.reduce(_ || _)
+  })
 
   // merge all exceptions, itlb/pmp has the highest priority, then l2/ecc
-  private val s1_exceptionOut = s1_exception || s1_tlException
+  private val s1_exceptionOut = VecInit(s1_tlException.map(s1_exception || _))
 
   io.toIfu.req.valid := s1_valid && s1_fetchFinish && !s1_flush
   io.toIfu.req.bits.zipWithIndex.foreach { case (req, i) =>
@@ -403,7 +406,7 @@
     req.maybeRvcMap          := s1_maybeRvcMap(i)
     req.perf_isCrossLine     := s1_isCrossLine(i)
 
-    req.icacheMeta.exception          := s1_exceptionOut
+    req.icacheMeta.exception          := s1_exceptionOut(i)
     req.icacheMeta.pmpMmio            := s1_pmpMmio
     req.icacheMeta.isBackendException := s1_req(i).hasBackendException
     req.icacheMeta.hasSatpFlush       := s1_req(i).hasSatpFlush
@@ -566,7 +569,7 @@
   accessTrace.wayMask    := s1_wayMask(0)
   accessTrace.crossLine  := s1_isCrossLine(0)
   accessTrace.waitRefill := perf_waitRefill
-  accessTrace.exception  := s1_exceptionOut
+  accessTrace.exception  := s1_exceptionOut(0)
   accessTrace.pmpMmio    := s1_pmpMmio
   accessTrace.itlbPbmt   := s1_wayLookupEntry(0).itlbPbmt
   accessTrace.rawHits    := s1_rawHits
```

The patch applies cleanly to `a7fc87df1` and it elaborates. The whole SoC was built from the pinned tree twice under `MinimalConfig` with the same `top.TopMain` invocation, once unpatched and once with this patch; both arms emit the same module set with no new elaboration warnings, and ignoring firtool's source-locator comments and the `gitDirty` stamp, the only module that differs is `ICacheMainPipe.sv`.

The third hunk is not optional. Without it the build stops at `ICacheMainPipe.scala:572` with `Sink (ExceptionType) and Source (ExceptionType[2]) have different types.`, because `s1_exceptionOut` becomes a `Vec` there and `accessTrace.exception` is a scalar.

Nothing above is simulated. The patch is offered as a description of where the merge boundary should be, not as a change to land.

**On `MinimalConfig`.** The full-core arms were built with `CONFIG=MinimalConfig`. It sizes the ICache, the FTQ, the IBuffer and the BPU tables below `DefaultConfig`, and it sets `FetchBlockSize` to 32 bytes rather than the default 64. None of that reaches the expression above. Both dimensions of the vector it flattens are `def`s in source that no configuration sets:

```scala
// src/main/scala/xiangshan/frontend/FrontendParameters.scala:92-94
  def MaxPrefetchReqNum: Int = 2
  def MaxFetchReqNum:    Int = 2
  def MaxAccessLineNum:  Int = 2
```

```scala
// src/main/scala/xiangshan/frontend/icache/Parameters.scala:68-70
  // this is used to prevent magic number in the code, DO NOT CHANGE, it won't work other than 2
  // explanation: we allow concurrent access to consecutive cachelines
  def PortNumber: Int = 2
```

So `s1_tlCorrupt.flatten` is four entries and the `reduce` runs across both fetch requests in every configuration. What the smaller fetch block does change is how far apart the two requests are (one fetch block, 32 bytes here and 64 under `DefaultConfig`), which is the separation between the two trap addresses reported above. The exhaustive sweep is on the elaborated cone rather than on a configuration.

**What this report does not claim.** The `denied` response in the full-core arms is injected on a naturally reached refill. No spontaneous fabric failure was observed, and how often a real workload meets a `denied` or `corrupt` response on an instruction fetch is a question about the memory system rather than about this expression.
