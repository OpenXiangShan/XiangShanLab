---
name: analyze-xiangshan-wavekit
description: Use this skill when analyzing XiangShan/香山 processor simulation waveforms with the wavekit open-source repository. It tracks one instruction by PC through frontend FTQ/branch prediction/redirect, decode, rename, dispatch, ROB, issue, execute, writeback, commit, relevant module FSM states, architectural/difftest state including GPR/FPR/CSR/privilege/exception values, and for memory instructions through LSQ, MemBlock, StoreBuffer, AtomicsUnit, and DCache. It also quantifies redirect effects and pipeline bubbles/stalls from waveform evidence. Use when given XiangShan Chisel source, a VCD/FST/FSDB waveform, a disassembly, and a target PC.
---

# XiangShan Waveform Instruction Trace

This skill produces a source-annotated, cycle-accurate analysis of one XiangShan instruction from waveform data using the wavekit open-source repository in this workspace. The required depth is comparable to a hand-written microarchitecture article: every pipeline stage must include handshake evidence, instruction metadata, relevant internal control signals, involved module FSM states, architectural/difftest state, data/control provenance, and Chisel code references.

## Inputs

Require or infer these artifacts:

- XiangShan Chisel source root, supplied by the user each time this skill is used
  (fallback default: `/nfs/home/yanyusong/xs-env/XiangShan`).
- Waveform path: `.fst`, `.vcd`, or `.fsdb`.
- Disassembly path or pasted disassembly.
- Target instruction PC, in hex or decimal.
- Optional: expected top/core scope and clock. Default clock to `TOP.clock`, then verify.

If an input is missing, search local paths first. Do not invent signal names; resolve them from the waveform hierarchy.

## First Read

Read these references as needed:

- `references/workflow.md`: the required analysis workflow and wavekit recipes.
- `references/xiangshan-signal-map.md`: Chisel file map and common waveform signal patterns.

## Hard Requirements

For the target PC:

1. Track frontend fetch visibility: PC, instruction bits if present, FTQ index, and fetch-to-IBuffer/decode handshakes.
2. Track every pipeline boundary with `valid`, `ready`, and `fire = valid & ready`; if a stage stalls, name the blocking signal and explain why.
3. For each stage, report input signals, output signals, and important internal signals. For each important signal explain: where it comes from, where it goes, its value for this instruction, and its role.
4. After rename, never rely only on PC. Carry the instruction by ROB index; also track physical source/destination registers when present.
5. For memory instructions, carry load queue index, store queue index, address, data, mask, exception, replay, redirect, flush, hit/miss, and cache request/response signals through LSQ, MemBlock, and cache.
6. For every involved module on the instruction path, identify FSM/state-register signals when present, report the state value/name during the instruction's residence in that module, and explain how that state affects handshakes, stalls, requests, responses, redirects, or flushes.
7. Cite Chisel source locations for every non-obvious interpretation. Use `rg -n` and file/line references.
8. Whenever you trace any waveform signal, find that signal's producer and/or field definition in the corresponding XiangShan Chisel source tree supplied for this run. Read the Chisel code before interpreting the signal, then explain the relevant source code in detail: bundle/IO definition, assignment logic, gating conditions, registers/FSMs, producer module, consumer module, and how the code explains the waveform value.
9. If the exact dumped signal name is generated or renamed, trace it back through nearby IO/bundle fields and module hierarchy until the Chisel source origin is identified. If no exact source origin can be proven, state the searched files/patterns and the closest justified source evidence.
10. Whenever XiangShan source code is mentioned in the generated Markdown, first create a Markdown link to the corresponding source file/line using an absolute filesystem path, never `~` or a relative path. Also copy the relevant referenced Chisel code snippet into the Markdown report, near the explanation or in a dedicated source-excerpts section. Keep snippets focused but sufficient to justify the interpretation.
11. Include absolute waveform cycles and simulation times. State sampling edge and clock signal.
12. Compare the waveform with the disassembly and call out any inconsistency in PC, instruction bits, opcode, timing, width, or display radix.
13. Track architectural state at retire/difftest boundaries. Record every available state value used for difftest comparison, including committed PC/instruction, destination register writes, GPR/FPR/vector writeback values when dumped, privilege/debug/virtualization state, key CSR values, exception/trap/interrupt cause and target values, and memory store/load difftest records. If a state family is not dumped, explicitly state that it was searched for and absent.
14. Analyze frontend branch prediction and redirect behavior with waveform evidence. For branch/jump/return, memory exception, load/store violation, replay, trap, fence, CSR, or any instruction that may redirect or flush younger work, load the relevant prediction metadata and redirect/flush signals with wavekit. If the target instruction causes a misprediction or exception redirect, trace the exact redirect path from producer (execute unit, LoadUnit/LSQ, ROB/CSR/trap, or branch unit) through CtrlBlock/ROB/FTQ/frontend, including valid/ready/fire where present, target PC, FTQ index/offset, ROB/LQ/SQ identity, redirect cause, flush scope, and affected younger instructions. If no redirect occurs, prove it by reporting the checked waveform signals and their values around the instruction's execute/writeback/commit window. Do not infer redirect behavior from Chisel source alone.
15. Analyze pipeline bubbles and performance impact with waveform evidence. Around the instruction's residence window, use wavekit to quantify cycles where relevant boundaries have `valid && !ready`, `ready && !valid`, or no `fire`; include queue full/empty, ROB/LSQ/IQ backpressure, TLB/cache miss/replay/nack, redirect flush, and frontend/IBuffer/FTQ bubbles when dumped. Attribute bubbles to named blocking signals only when the waveform proves the attribution; otherwise state the unresolved evidence. Add a short performance-optimization discussion grounded in the observed waveforms, not only in code reading.
16. In the final analysis text, explicitly state that the analysis used the wavekit open-source repository/library to parse and query the waveform.

## Output Shape

Write the final analysis in Chinese unless the user asks otherwise. Use this structure:

1. **方法与结论摘要**: state that wavekit was used, then list instruction, disassembly, core scope, clock, sampling edge, and whether waveform matches the program.
2. **全局时间线**: a table with cycle, time, stage/event, valid/ready/fire, ROB/LQ/SQ/FTQ indices, and key values.
3. **逐级分析**: Frontend/FTQ, IBuffer/Decode, Rename, Dispatch/ROB, Issue/Schedule, Execute/DataPath, Writeback, Commit; add MemBlock/DCache subsections for memory ops. Each subsection must include relevant FSM/state-register values when dumped.
4. **前端预测 / Redirect 路径**: waveform-backed analysis of prediction metadata, redirect/flush signals, redirect source, target, cause, FTQ/ROB identity, frontend recovery path, and proof of no redirect when none occurs.
5. **Bubble / 性能影响分析**: waveform-backed table of bubbles/stalls by cycle range, boundary, `valid/ready/fire`, blocking signal, affected pipeline region, and a brief optimization discussion grounded in observed evidence.
6. **架构态 / Difftest 状态**: table every dumped difftest comparison value for the target commit, including PC/instr, writeback registers/data, CSR/privilege state, exception cause/tval/epc/redirect target, load/store event state, and whether each value changed because of this instruction.
7. **信号来源与去向**: for each stage, explicitly connect producer -> signal -> consumer.
8. **Chisel 源码解析**: for every traced signal, give the XiangShan source origin, relevant line references, assignment/gating logic, producer/consumer relationship, and how the code explains the observed waveform behavior.
9. **FSM 状态汇总**: table of module, state signal, state value/name, cycle range, code reference, and effect on this instruction.
10. **源码链接与引用代码段**: Markdown links to every referenced XiangShan Chisel source file/line using absolute paths, followed by copied focused code snippets for the cited line ranges.
11. **代码依据**: concise list of Chisel files and line references used; each entry must be a Markdown link with an absolute filesystem path.
12. **异常/不一致**: waveform/document/code disagreements and likely explanations.

Do not stop at a shallow waveform dump. The value of the skill is the causal explanation of why the instruction moved, stalled, replayed, redirected, flushed, wrote back, or committed.
