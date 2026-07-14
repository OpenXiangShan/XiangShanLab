# Frontend Reference

Read this file for frontend fetch, prediction, and instruction delivery modules under `src/main/scala/xiangshan/frontend`. For predictor modules, also read `predictor-papers.md` and use `paper-search-agent-mcp` to search the relevant paper before explaining algorithm principles.

## Main Files

- Top/control: `Frontend.scala`, `IFU.scala`, `FrontendBundle.scala`, `Composer.scala`, `PreDecode.scala`
- Fetch target queue: `NewFtq.scala`
- Instruction buffer: `IBuffer.scala`
- Branch prediction: `BPU.scala`, `FTB.scala`, `FauFTB.scala`, `Tage.scala`, `ITTAGE.scala`, `SC.scala`, `Bim.scala`, `RAS.scala`, `newRAS.scala`, `WrBypass.scala`
- I-cache: `frontend/icache/ICache.scala`, `ICacheMainPipe.scala`, `ICacheMissUnit.scala`, `ICacheCtrlUnit.scala`, `ICacheBundle.scala`, `IPrefetch.scala`, `InstrUncache.scala`, `WayLookup.scala`
- TLB/MMU support: inspect frontend connections and `cache/mmu/TLB.scala`, `L2TLB.scala`, `PageTableWalker.scala`, and MMU bundles when the user asks about ITLB.

## Fetch Flow

Trace:

1. Prediction generates next fetch PC and branch metadata.
2. FTQ records fetch blocks, prediction state, PC, and later backend feedback.
3. I-cache/ITLB translate and fetch instruction bytes.
4. Predecode/composer split fetched bytes into instructions.
5. IBuffer decouples frontend fetch from backend decode.
6. Backend redirect/commit feedback trains predictors and flushes wrong-path state.

## ICache and ITLB

For `icache`:
- Identify request channel from IFU/fetch pipeline.
- Trace virtual address, translation request/response, way lookup, miss handling, refill, and uncache handling.
- Separate tag/meta/data structures, miss queue/unit, control unit, and prefetch path.
- Explain replay/refetch when miss, exception, or redirect occurs.

For ITLB:
- Search for TLB request and response bundles in frontend/icache files and `cache/mmu`.
- Explain whether the local module only consumes translation or owns a TLB instance.
- Trace exceptions/page faults/access faults back to frontend and backend metadata.

## FTQ

Focus on:
- Enqueue by prediction/fetch block
- Dequeue or commit-driven retirement
- Redirect cleanup
- Predictor update metadata storage
- PC, target, branch mask, and prediction state fields

Ask:
- Who allocates FTQ entries?
- Why does frontend need a queue instead of sending predictions directly?
- How are prediction records matched with backend-resolved branches?
- From what predictor/fetch response?
- To what backend decode/commit/training path?

## Instruction Buffer

Focus on:
- Queueing instruction packets between frontend and backend
- Backpressure from decode/backend
- Flush on redirect
- Handling partial fetch blocks, compressed instructions, and valid masks when present

Ask:
- Who writes instructions into the buffer?
- Why is decoupling needed between fetch bandwidth and decode availability?
- How are valid masks and PCs preserved?
- From what predecode/composer/fetch output?
- To what backend decode input?

## Branch Predictors

Analyze each predictor separately, then describe the ensemble:

- `Bim`: usually a simple bimodal direction predictor
- `FTB`/`FauFTB`: target and branch information storage
- `Tage`/`ITTAGE`: history-indexed direction/indirect prediction structures
- `SC`: statistical corrector path
- `RAS`/`newRAS`: call/return stack prediction
- `BPU`: integration, arbitration, update, redirect, and response composition

For each predictor:
- Paper-grounded principle: use `paper-search-agent-mcp` and summarize the relevant paper's algorithm before code mapping
- Storage structures: tables, tags, counters, history registers, folded histories, useful bits, target entries, stack entries
- Read path: index generation, tag match, provider/alternate selection, confidence/counter interpretation, prediction output
- Update path: who trains, when, with what resolved metadata, allocation/replacement, useful-bit or confidence update when present
- Recovery path: history repair, redirect flush, snapshot restore
- Scenario example: cold start, aliasing, long-history branch, indirect target change, return prediction, misprediction redirect, or correction override
- Consumer: next PC generation, FTQ metadata, backend redirect comparison

Always distinguish prediction-time signals from training/update signals.

## Extra Frontend Analysis Requirements

For frontend modules, explain predictor algorithms with paper-backed principle plus lookup/update/recovery separation. For FSMs, inspect fetch pipeline control, ICache miss/refill control, FTQ enqueue/dequeue/commit state, RAS/history recovery, and redirect handling. Keep prediction data path separate from training/update control path.
