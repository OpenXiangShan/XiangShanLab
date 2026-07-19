# Instruction Latency and Throughput Reference

Read this file whenever the user asks about instruction latency, instruction throughput, performance timing, FU timing, pipeline timing, or instruction-flow analysis that reaches backend, mem, cache, XSCache, or writeback/commit.

## Definitions

- **Latency**: cycles from an explicitly named start event to an explicitly named end event. Common start events are decode input, rename output, dispatch fire, issue fire, FU input fire, memory request fire, or cache request fire. Common end events are FU response valid, bypass/forward data visible, regCache write/update, physical register-file write, writeback fire, ROB writeback observed, commit fire, or memory response accepted.
- **Throughput**: steady-state rate for independent operations under stated assumptions. Express as instructions per cycle, operations per cycle, or initiation interval. Do not use latency as throughput.
- **Best-case latency**: no stalls, no redirects, operands ready, resource available, cache hit or fixed FU path when applicable.
- **Variable latency**: load miss, TLB miss, cache miss/refill, replay, division iteration, vector element count, CSR/exception serialization, writeback contention, queue full, bank conflict, MSHR contention, or downstream backpressure.
- **Unclear**: use this when source lines do not prove cycle timing. State exactly which elaborated parameter, generated Verilog, waveform, or child module must be inspected next.

## Required Evidence

For every reported latency or throughput number, include source evidence for:

- Instruction classification: decode table, `FuType`, operation type, or memory/cache opcode marker.
- Routing path: decode/rename/dispatch/issue/exu/mem/cache/writeback/commit modules that the instruction class traverses.
- FU or pipeline timing: `FuConfig`, `FunctionUnit`, wrapper, pipeline registers, valid delay, busy/ready, or response valid logic.
- Bypass/regCache timing: bypass source valid/data timing, `readForward`/`readBypass`/`readBypass2` selection, regCache write-enable/data timing, and whether regCache is updated from bypass data or PRF data.
- Resource count: issue ports, execution units, FU instances, writeback ports, memory pipelines, cache banks, MSHRs, queue entries, arbiters, or commit width.
- Bottleneck arbitration: issue select, FU busy table, writeback arbiter, load/store queue, memory/cache request arbiter, bank/MSHR arbiter, or commit rule.
- Variable contributors: replay, redirect, exception, miss, refill, TLB/PTW, vector iteration, divider state, fence/order serialization, or MMIO/uncache path.

## Analysis Procedure

1. Build an instruction taxonomy for the requested scope. Prefer exact decode/FU markers over prose categories: integer ALU, branch/jump, CSR, multiply, divide, floating point, vector arithmetic, load, store, AMO/LR/SC, prefetch, fence, CBO/CMO, and exception-generating instructions when relevant.
2. For each instruction class, trace the effective path from decode to the chosen end event. Include all live pipeline registers, queues, ready/valid handshakes, wrappers, and storage updates that can add cycles or stalls.
3. Derive latency in layers:
   - Frontend/decode-to-dispatch latency if the request asks end-to-end instruction flow.
   - Dispatch/issue wait is variable unless the analysis assumes operands ready and issue slot available.
   - Execute/FU latency is fixed only if `FuConfig`, wrapper, or FU code proves a fixed pipeline delay.
   - Memory/cache latency must split hit, miss, replay, TLB/PTW, MMIO/uncache, refill/writeback, and commit-visible completion.
   - Bypass/regCache/writeback/ROB/commit latency must be split when these events differ. If the analyzed path can update regCache, report the write-to-regCache timing separately from bypass visibility and physical register-file write timing.
4. Derive throughput from the limiting initiation interval:
   - Count independent instruction accept/issue slots and FU instances for the class.
   - Check whether the FU is fully pipelined, has a busy state, or accepts a new request only after response.
   - Check writeback, bypass/wakeup, ROB, LSQ, cache bank, MSHR, and commit port bottlenecks.
   - For memory/cache, state separate throughput for L1 hits, bank-conflict cases, misses/refills, stores, AMO/LR/SC, fences, CBO, and uncache/MMIO when applicable.
5. Separate code-proven values from assumptions. If a value depends on parameters, report the parameter expression and the effective value only when the analyzed configuration proves it.
6. Add scenario rows for throughput degraders: simultaneous issue to same FU type, more completions than writeback ports, load bank conflict, queue full, MSHR full, replay, redirect flush, exception serialization, and commit blocked by an older instruction.

## Output Tables

Use a per-instruction or per-class timing table:

| Instruction/class | Decode/FU marker | Path | Latency start | Latency end | Best-case latency | issue -> bypass | issue -> regCache write | issue -> PRF write | Variable contributors | Throughput / initiation interval | Bottleneck resource | Source evidence | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Use a resource-throughput table:

| Resource | Applies to | Count/width | Accept condition | Completion condition | Arbitration/priority | Peak throughput | Degradation scenarios | Source evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Use a path timing table when one instruction needs a cycle-by-cycle explanation:

| Cycle/stage | Event | Valid/ready condition | Payload/state | Can stall/replay/flush? | Source evidence |
| --- | --- | --- | --- | --- | --- |

## Reporting Rules

- Always state the timing reference point. `ALU latency is 1 cycle` is insufficient; say whether this means FU-input-to-response, issue-to-bypass, issue-to-regCache-write, issue-to-PRF-write, FU-input-to-writeback, or dispatch-to-commit.
- When analyzing XiangShan backend latency, include regCache write/update timing when the producer has a regCache path. Treat regCache as a distinct endpoint: it is a bypass-backed cache for later source reads, not the PRF itself.
- Do not average unrelated paths into one number. Report separate best-case, hit, miss, replay, and serialized cases.
- Do not claim one-instruction-per-cycle throughput unless the FU can accept a new request every cycle and downstream writeback/commit resources can sustain it.
- For vector instructions, state whether latency/throughput is per instruction, per micro-op, per element group, or per lane operation.
- For stores, distinguish store address/data execution, store queue enqueue, commit authorization, and cache write/drain timing.
- For loads, distinguish execute/address-generation, TLB/cache hit response, replay, and final writeback timing.
- For AMO/LR/SC, include atomic ordering, cache/coherence interaction, retry/replay, and commit-visible result timing.
- For branch/jump, distinguish redirect-generation latency from architectural commit latency.
- For exceptions and CSR/fence instructions, include serialization, ordered commit, and flush/redirect effects.
