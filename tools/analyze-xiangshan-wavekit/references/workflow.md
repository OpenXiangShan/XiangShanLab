# Workflow

## 1. Normalize the target instruction

Use the disassembly to establish:

- PC, instruction bits, mnemonic, source/destination architectural registers.
- Whether it is ALU, branch, CSR, load, store, AMO, vector, or floating-point.
- Expected side effects: destination register, memory address/data, branch redirect, CSR write, exception potential.
- Expected architectural effects visible to difftest: committed PC/instruction, integer/floating/vector register writeback, CSR updates, privilege/virtualization changes, memory load/store events, and exception/trap/interrupt state.

For a memory instruction, write down the expected base register, offset/immediate, access width, signedness, AMO op, aq/rl bits, and destination semantics before opening waveforms.

For a CSR, trap-return, fence, system, or potentially excepting instruction, write down the expected CSR reads/writes, privilege transition, trap cause, `epc`, `tval`, redirect target, and whether the instruction should retire or be squashed.

## 2. Open the waveform with wavekit

Pick the reader by extension:

```python
from wavekit import FstReader, VcdReader, FsdbReader

def open_reader(path):
    if path.endswith(".fst"):
        return FstReader(path)
    if path.endswith(".vcd"):
        return VcdReader(path)
    if path.endswith(".fsdb"):
        return FsdbReader(path)
    raise ValueError(path)
```

Default XiangShan clock is `TOP.clock`. For generated FSTs, first try `sample_on_posedge=True`. If events are consistently one half-cycle off, test negedge and report which edge matches handshakes.

Use hierarchy discovery before loading many signals:

```python
with open_reader(wave_path) as r:
    # inspect top scopes and resolve patterns before loading
    print([s.full_name() for s in r.top_scope_list()])
    matches = r.get_matched_signals("TOP.SimTop.*")
```

Find the core prefix from waveform matches. Common prefixes are:

- `TOP.SimTop.l_soc.core_with_l2.core`
- `TOP.SimTop.l_soc.core_with_l2.core.backend`
- `TOP.SimTop.l_soc.core_with_l2.core.memBlock`

In the final report, include a short methods sentence such as:

`本分析使用 wavekit 开源仓库中的 FstReader/VcdReader/FsdbReader 解析波形，并用 clock-sampled Waveform 数组按 cycle 查询信号值。`

Before interpreting any traced signal, record the XiangShan source root for this
run. The user is expected to provide it whenever invoking this skill; use the
default only when the user omits it and the path exists. All source-code lookup,
line references, and explanations must come from that source tree.

## 3. Build a PC anchor

Search all likely PC signals for the target PC. Prefer stage input/output PC signals with handshake bits nearby.

Recommended candidates:

- Frontend and FTQ output PC vectors.
- IBuffer output `cf.pc`.
- Decode `io_enq_*pc`, `io_deq_*pc`.
- Rename `io_in_*_bits_cf_pc`, `io_out_*_bits_uop_cf_pc`.
- Dispatch `io_fromRename_*_bits_uop_cf_pc`.
- ROB enqueue/commit debug PC.

Use `load_matched_waveforms` for lane patterns:

```python
pcs = r.load_matched_waveforms(
    f"{core}.backend.inner_ctrlBlock.decode.decoders_{{0..5}}.io_deq_decodedInst_pc[49:0]",
    clock,
    sample_on_posedge=True,
)
```

For each match, report cycle/time/lane where `pc == target_pc`. This gives the first backend anchor. Then expand a window around it, usually `begin_cycle=anchor-100`, `end_cycle=anchor+300`.

## 4. Track handshakes at every boundary

For every Decoupled interface, load:

- `valid`
- `ready`
- `bits_*_pc` or `bits_uop_cf_pc`
- `bits_uop_robIdx`, after rename
- instruction bits, ftqIdx, lqIdx, sqIdx, pdest, psrc, fuType, fuOpType when present

Compute `fire = valid & ready`. If `valid=1` and `ready=0`, identify local ready-generation or block signals. Examples:

- Rename allocation: free-list `allocateReq`, `allocatePhyReg`, `canAllocate`, redirect/walk.
- Dispatch: ROB accept/empty, issue queue backpressure, LSQ allocation, `waitForward`, `blockBackward`.
- Issue queue: enqueue valid/ready, source ready state, select/grant/issued.
- MemBlock/cache: request valid/ready, replay, nack, miss, exception, redirect, flush.

Do not claim a transfer happened unless `valid & ready` is true on the sampled edge.

## 5. Track FSM state for every involved module

For each module on the instruction path, search for state registers and finite-state-machine encodings before writing the stage analysis. Common waveform names include:

- `state`, `stateReg`, `r_state`, `fsm`, `fsmState`, `*_state`, `*_stateReg`
- generated Chisel enum names such as `s_idle`, `s_wait`, `s_req`, `s_resp`, `s_replay`
- module-specific names like `mainPipeState`, `missState`, `flushState`, `amoState`, `replayState`

Use `reader.get_matched_signals()` to resolve candidate state signals under the module prefix, then load candidates in the same cycle window as the target instruction. For each state signal:

- Report the numeric waveform value.
- Map the value to a Chisel enum name when the source code exposes `Enum(...)`, `ChiselEnum`, `object State`, or `val s_*`.
- If the name cannot be recovered, state that only the numeric encoding is available and explain behavior using transitions and neighboring control signals.
- Explain how the state affects the instruction: for example, why ready is low, why a cache request is held, why a replay is generated, why store buffer drain is required, or why a redirect/flush is asserted.

Do not overclaim that a state belongs to the target instruction unless it is correlated by PC, ROB index, LQ index, SQ index, request valid/fire, or response metadata. If a module FSM is shared across many transactions, describe it as the module state observed while this instruction's transaction is active.

## 6. Carry identifiers

Before rename, carry by PC and lane. At rename output, record:

- `robIdx`
- `ftqIdx`
- `pdest`
- `psrc*`
- `lqIdx` and `sqIdx` if allocated or later visible
- `fuType`, `fuOpType`, `waitForward`, `blockBackward`, exception flags

After rename, use `robIdx` as the primary identity. PC may disappear or be reused in debug-only paths. For memory instructions, carry `lqIdx` and/or `sqIdx` in parallel.

## 7. Analyze each stage causally

For each stage answer:

- What input accepted this instruction? Include `valid/ready/fire`, PC/ROB/lane, and time.
- What output did it produce? Include transformed fields.
- What internal signals explain behavior? For stalls, name the exact gate.
- What FSM/state-register value did the module have while this instruction was active, and what transition happened before/after the event?
- Where did each important signal come from, and where is it consumed next?
- Which Chisel code defines the bundle or logic?

Use `rg -n` on the Chisel tree for signal/field names, then cite file lines in the final answer.

For every waveform signal you decide to track, perform a Chisel source-origin
pass before writing the interpretation:

- Search the supplied XiangShan source root for the signal field name, nearby
  bundle field, IO name, module instance name, or generated prefix components.
- Identify the relevant bundle/IO definition, producer assignment, gating
  condition, register/FSM update, and downstream consumer when they are present.
- Read the surrounding source code, not just the matching line. Explain how the
  Chisel logic produces or consumes the observed waveform value and why that
  value matters for the target instruction.
- If the dumped signal is generated from a renamed temporary and has no exact
  Chisel spelling, trace through the hierarchy and neighboring fields to the
  closest source-level signal. State the searched patterns/files and whether the
  origin is exact or inferred.
- Do not present a signal as causally understood until its source-code origin and
  role have been checked, or until the report explicitly states that the source
  origin could not be proven from the provided tree.

## 8. Memory instruction extension

For loads, stores, AMOs, and LR/SC, add these tracks:

- Address generation: source operands, immediate, virtual address, physical address if present.
- LSQ indices: `lqIdx`, `sqIdx`, queue enqueue, allocated entry, readiness bits.
- Store data path: data source, mask, byte/word lane, store queue data readiness.
- Load path: DCache request, response data, forwarding, miss/replay, exception.
- Store path: store queue commit, sbuffer enqueue/drain, DCache write request.
- AMO path: store buffer flush/drain, atomics unit request, AMO data/mask/op, DCache response old value.
- Control hazards: replay, redirect, flush, cancel, violation, RAW/RAR, memory ordering.
- Memory-module FSMs: LSQ allocation/replay state, StoreQueue commit/drain state, SBuffer flush/drain state, AtomicsUnit state, DCache mainpipe/miss/replay state if dumped.

When cache request address is line-aligned but execute address is byte-addressed, report both and explain mask/offset selection.

## 9. Architectural and difftest state extension

Always inspect retire/difftest state for the target instruction, even when the microarchitectural trace already explains execution. The final report must include every dumped value that the difftest interface or commit trace uses to compare architectural state.

Search signal names under top/core/backend/ROB/CSR scopes with terms such as:

- `*difftest*`, `*Difftest*`, `*diff*`, `*Diff*`
- `*commit*`, `*io_commits*`, `*debug_pc*`, `*debug_instr*`, `*rfwen*`, `*fpwen*`, `*vecwen*`, `*wdest*`, `*wdata*`
- `*csr*`, `*CSR*`, `*mstatus*`, `*sstatus*`, `*mepc*`, `*sepc*`, `*mcause*`, `*scause*`, `*mtval*`, `*stval*`, `*satp*`, `*mip*`, `*mie*`, `*medeleg*`, `*mideleg*`, `*priv*`, `*virt*`, `*vtype*`, `*vl*`, `*vstart*`
- `*exception*`, `*Exception*`, `*trap*`, `*Trap*`, `*interrupt*`, `*cause*`, `*tval*`, `*epc*`, `*redirect*`, `*flush*`
- For memory difftest: `*LoadEvent*`, `*StoreEvent*`, `*load*diff*`, `*store*diff*`, `*paddr*`, `*vaddr*`, `*mask*`, `*data*`

At the target commit cycle, record at least:

- ROB commit lane state: `isCommit`, `commitValid`, `commit_v`, `commit_w`, `debug_pc`, `debug_instr`, `debug_ldest`, `debug_pdest`, `rfWen`, `fpWen`, vector write enables, FTQ identity, commit type, exception/flush bits.
- Register architectural result: destination architectural register number, physical destination, final writeback value, write-enable, and source of that value. If full GPR/FPR/vector architectural arrays are dumped, record the before/after value for affected registers.
- CSR state: all dumped difftest CSR values around commit, including privilege/debug/virtualization state and standard machine/supervisor/vector CSRs. For CSR-writing instructions, show before/after; for non-CSR instructions, state that relevant CSR values were unchanged when the waveform proves it, or list the sampled values.
- Exception/trap state: exception vector bits, selected exception number/cause, `epc`, `tval`, guest physical address if present, interrupt valid/cause, trap redirect target, `needFlush`, `flushOut`, and whether the target instruction commits or is killed.
- Memory architectural events: load/store/AMO difftest event valid, paddr/vaddr, data, mask, commit type, MMIO flags, and old/new value semantics when present.

Use the retire cycle as the primary sampling point. Also sample one cycle before and after when CSR/trap/difftest signals are registered or delayed. If difftest signals are generated in a separate top-level wrapper, resolve their hierarchy from `top/Top.scala`, `top/XSNoCTop.scala`, or generated signal names rather than assuming a fixed path.

When reporting architectural state:

- Separate microarchitectural writeback from architectural retire. A value written back before commit is not architectural until the ROB commit valid for that instruction is true.
- For exceptions, distinguish the excepting instruction's ROB entry from younger instructions flushed by the trap.
- Do not claim CSR unchanged merely because no CSR instruction was decoded; verify dumped CSR/difftest state if available, or say the waveform lacks the relevant signal.
- If a difftest state value is X/Z, use `load_unknown_mask` and report the unknown bits.

## 10. Frontend prediction and redirect extension

This section is mandatory for every target instruction, even when no redirect occurs. The answer must be based on wavekit queries over the waveform; Chisel source may explain the signal semantics but must not be the sole evidence.

Use a window that covers at least:

- Frontend/FTQ/decode visibility for the target PC.
- Execute/writeback cycles where branch resolution, load/store exception, replay, or violation could be generated.
- Commit/trap cycles where ROB/CSR may flush or redirect.
- Several cycles after any redirect to show frontend recovery and younger instruction squashing.

Search and load waveform signals matching these roles:

- Prediction metadata: FTQ index/offset, predicted target, predicted taken, branch type, return/call flags, `cfVec`, predecode prediction fields, and frontend-to-backend FTQ entries when dumped.
- Backend redirect producers: branch/jump execution redirect, load/store violation redirect, LoadUnit/LSQ replay redirect, exception/trap redirect, ROB flush, CSR/trap redirect, fence/sfence redirects.
- Redirect path: `redirect.valid`, `redirect.bits.cfiUpdate.pc`, `target`, `taken`, `isMisPred`, `stFtqIdx`, `ftqIdx`, `ftqOffset`, `robIdx`, `level`, `flushOut`, `needFlush`, `exception`, `trap`, and frontend `toFtq/fromFtq` redirect signals.

For an instruction that causes a redirect, report:

- Producer module and exact cycle/time of redirect assertion.
- Target instruction identity: PC plus ROB/LQ/SQ/FTQ identity where available.
- Redirect cause: branch target/taken mismatch, exception/trap, memory ordering violation, replay, cache/TLB fault, fence, or CSR side effect.
- Redirect payload: old PC, predicted target if dumped, actual target, FTQ index/offset, ROB index, flush level/scope.
- Consumer path: CtrlBlock/ROB/FTQ/frontend valid signals in the next cycles and evidence that younger instructions are killed or refetched.

If no redirect occurs, do not omit the section. State the checked waveform signals and show the relevant zero/inactive values around execute/writeback/commit, for example `redirect.valid=0`, `flushOut.valid=0`, `needFlush=0`, trap valid=0, and memory violation valid=0.

## 11. Bubble and performance-impact extension

This section is mandatory for every target instruction. It must quantify bubbles/stalls with wavekit waveform data; do not infer stalls from source code alone.

Build a compact cycle table over the target instruction's residence window and nearby frontend recovery window. For each relevant interface, compute or list:

- `valid`, `ready`, `fire = valid & ready`.
- `valid && !ready`: backpressure / blocked transfer.
- `ready && !valid`: empty source / upstream bubble.
- `!valid && !ready`: idle or globally blocked state, explain only if supporting signals prove it.
- Queue/resource state when dumped: ROB canAccept, IQ ready, LSQ canAccept, LQ/SQ free counts, load/store queue full, MSHR/full/nack, TLB miss, cache miss, replay queue, frontend FTQ/IBuffer ready/valid, fetch bubble counters.

Attribute each bubble only when the waveform proves the cause. Examples:

- Decode/rename bubble: frontend/IBuffer output not valid, decode ready low, rename allocation blocked.
- Dispatch bubble: `fromRename.valid=1 && ready=0` with IQ/ROB/LSQ gating signal low.
- Issue bubble: entry valid but not selected because sources not ready, FU busy, scheduler ready low, or wakeup not observed.
- Load bubble: TLB miss, DCache miss/nack/bank conflict/replay, LSQ forward query blocked, store-load violation recovery.
- Redirect bubble: cycles lost between redirect assertion, frontend FTQ recovery, and first correct-path decode.

In the report, include:

- A table with cycle range, boundary/module, `valid/ready/fire`, blocking signal, observed duration, and whether it is attributable to the target instruction or merely concurrent pressure.
- A short performance discussion grounded in observed waveform evidence: which resource caused the largest delay, whether it is frontend, scheduler, LSQ/cache, ROB commit, redirect recovery, or unrelated older instructions, and what optimization would reduce the observed bubbles.
- If no measurable bubble is caused by the target instruction, state that explicitly and list the checked interfaces.

## 12. Wavekit patterns useful for reports

Use scalar tables instead of printing long arrays:

```python
import numpy as np

def rows(w, pred):
    idx = np.where(pred(w.value))[0]
    return [(int(w.clock[i]), int(w.time[i]), int(w.value[i])) for i in idx]

def at_cycle(w, cycle):
    i = np.searchsorted(w.clock, cycle)
    if i < len(w.clock) and int(w.clock[i]) == cycle:
        return int(w.value[i])
    return None
```

Use `load_unknown_mask` for suspicious X/Z control or data signals. If a control decision depends on unknown values, report it explicitly.

## 13. Validation checklist

Before finalizing, verify:

- The report explicitly says wavekit was used to parse/query the waveform.
- The disassembly instruction bits equal waveform instruction bits.
- The first PC anchor and all later ROB/LQ/SQ-linked events refer to the same instruction.
- Every stage-to-stage movement has `valid & ready` evidence.
- Every stall has a named blocking condition.
- The frontend prediction/redirect section is present and is backed by waveform values. If no redirect occurred, the report lists checked inactive redirect/flush/trap signals.
- The bubble/performance section is present and is backed by waveform values. It quantifies observed `valid/ready/fire` gaps or explicitly states that no target-attributable bubbles were observed after checking relevant interfaces.
- Every involved module with a dumped FSM/state signal has a reported state value/name, or the report states that no state signal was dumped/found.
- Every traced waveform signal has a Chisel source-origin note from the supplied
  XiangShan source tree, including line references and a detailed explanation of
  the relevant assignment/gating/consumer logic; unresolved origins are explicitly
  listed with searched patterns.
- The architectural/difftest section records all dumped comparison values for the target commit, including CSR and exception/trap state, or explicitly lists missing undumped state families.
- Writeback data and commit behavior match instruction semantics, or the mismatch is explained.
- All line references are from the provided XiangShan source tree, not memory.
