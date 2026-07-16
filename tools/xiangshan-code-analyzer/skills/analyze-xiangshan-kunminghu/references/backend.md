# Backend Reference

Read this file for backend module explanations under `src/main/scala/xiangshan/backend`.

## Pipeline Order

Use this order unless code proves a different local path:

1. Decode: `backend/decode`
2. Rename: `backend/rename`, `backend/rename/freelist`
3. Dispatch: `backend/dispatch/NewDispatch.scala`
4. Issue/schedule: `backend/issue`
5. Execute: `backend/exu`, `backend/fu`, `backend/fu/wrapper`, vector/FPU/CSR subtrees
6. Writeback and wakeup: `backend/datapath`, writeback arbiters, bypass, wakeup networks
7. Commit/recovery: `backend/rob`, `backend/ctrlblock`, redirect generation

## Decode

Start with `DecodeStage.scala`, `DecodeUnit.scala`, `DecodeUnitComp.scala`, `Instructions.scala`, `UopInfoGen.scala`, `FusionDecoder.scala`, and specialized decoders.

Focus on:
- Instruction bit pattern to micro-op control fields
- FU type, source/destination type, immediate, exception, vector/fp metadata
- Predecode/fusion interactions
- Redirect or flush behavior from upstream/frontend or backend recovery

Ask:
- Who classifies the instruction?
- Why is the uop field needed later?
- How does decode preserve enough semantic information for rename/dispatch/execute?
- From what instruction bundle and PC metadata?
- To what rename/dispatch bundle?

## Rename

Start with `Rename.scala`, `RenameTable.scala`, `BusyTable.scala`, `Snapshot.scala`, and `freelist`.

Focus on:
- Architectural to physical register mapping
- Free-list allocation and old physical destination tracking
- Busy table readiness and wakeup updates
- Snapshot/checkpoint recovery on redirects
- Vector/fp/int register namespace differences when present

Ask:
- Who updates rename tables and free lists?
- Why are old physical destinations retained until commit/recovery?
- How are speculative mappings restored?
- From what decoded uop and commit/redirect feedback?
- To what dispatch, issue, ROB, and regfile read paths?

## Dispatch

Start with `dispatch/NewDispatch.scala` and related backend bundles.

Focus on:
- Routing renamed uops into issue queues, ROB, LSQ, and execution clusters
- Backpressure from issue/ROB/LSQ resources
- Dispatch width and arbitration
- Flush/redirect cancellation

Ask:
- Who decides the target issue block?
- Why is dispatch a resource-allocation boundary?
- How are valid uops masked or stalled?
- From what rename outputs and resource availability?
- To what scheduler, ROB, memory block, and datapath?

## Issue

Start with `issue/Scheduler.scala`, `IssueQueue.scala`, `Entries.scala`, `EnqEntry.scala`, `WakeupQueue.scala`, `MultiWakeupQueue.scala`, `FuBusyTableRead.scala`, `FuBusyTableWrite.scala`, `DataArray.scala`, and age/dequeue policy files.

Focus on:
- Entry allocation, valid bits, operand readiness, immediate/data arrays
- Wakeup sources from writeback/bypass/execution
- Oldest-ready selection and FU availability
- Replay or cancellation behavior

Ask:
- Who marks operands ready?
- Why are wakeup and selection separated?
- How does the queue prevent issuing to busy FUs?
- From what dispatch and writeback signals?
- To what execution units and datapath read ports?

## Execute

Start with `exu/ExeUnit.scala`, `exu/ExuBlock.scala`, `fu/FunctionUnit.scala`, `fu/FuConfig.scala`, `fu/FuType.scala`, wrappers, ALU/branch/jump/mul/div/FPU/vector/CSR/Fence files.

Focus on:
- FU type matching and latency
- Per-instruction and per-instruction-class latency/throughput. Read `instruction-latency-throughput.md` before reporting timing numbers.
- Operand source selection and bypass
- Branch/jump redirect generation
- CSR/exception/interrupt interactions
- Memory-operation handoff to `mem` units when relevant

Ask:
- Who owns execution latency and valid timing?
- What are the timing reference points for each reported instruction latency: issue-to-response, issue-to-writeback, dispatch-to-writeback, or dispatch-to-commit?
- Which resource limits throughput for this instruction class: issue port, FU count, FU pipeline initiation interval, writeback port, ROB/commit width, LSQ/cache resource, or a busy/serialization state?
- Why is this FU wrapper needed instead of exposing the raw FU?
- How are exceptions, redirects, and writeback results produced?
- From what issue/datapath operands?
- To what writeback, ROB, redirect generator, or memory subsystem?

## Writeback

Start with `datapath/DataPath.scala`, `WbArbiter.scala`, `WbConfig.scala`, `BypassNetwork.scala`, `RFReadArbiter.scala`, `RFWBConflictChecker.scala`, `WbFuBusyTable.scala`, and regfile files.

Focus on:
- FU result arbitration to writeback ports
- Bypass and wakeup fanout
- Register file write timing and read/write conflicts
- Result metadata delivered to ROB/commit and issue wakeup

Ask:
- Who arbitrates multiple FU results?
- Why does wakeup need to happen before or beside regfile write?
- How are data hazards resolved?
- From what FU outputs?
- To what regfile, scheduler, ROB, and bypass consumers?

## Commit and Recovery

Start with `rob/Rob.scala`, ROB bundle files, `ctrlblock/RedirectGenerator.scala`, `ctrlblock/MemCtrl.scala`, `CtrlBlock.scala`, and exception files.

Focus on:
- ROB enqueue/dequeue pointers and commit width
- Commit eligibility and exception priority
- Redirect, flush, and recovery broadcast
- Freeing old physical registers and retiring stores
- Interaction with CSR, memory ordering, and frontend redirect

Ask:
- Who decides an instruction can commit?
- Why is retirement ordered when execution is out of order?
- How are exception/redirect causes prioritized?
- From what writeback, memory, CSR, and frontend metadata?
- To what rename/free-list, frontend, LSQ/store queue, CSR, and debug/trace outputs?

## Extra Backend Analysis Requirements

For backend modules, always split control path and data path. For decode/rename/dispatch/issue/execute/writeback/commit, identify the effective instantiated path and the parameter-generated widths. For algorithms, prioritize rename allocation/recovery, issue select, wakeup, bypass, reg-cache replacement, writeback arbitration, ROB commit, redirect priority, and exception selection. For FSMs, include queue entry status machines even when there is no explicit `Enum`.

For timing analysis, build a table per instruction class. Use decode markers and `FuType` to identify the class, `FuConfig`/`FunctionUnit`/wrapper code to prove execute latency, issue queue and FU busy logic to prove initiation interval, writeback arbitration to prove completion throughput, and ROB/commit logic to prove ordered retirement effects. Treat dispatch/issue wait as variable unless operands-ready and issue-resource-available assumptions are explicitly stated.
