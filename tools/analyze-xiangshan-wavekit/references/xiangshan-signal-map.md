# XiangShan Signal And Code Map

Paths vary with generated instance names. Always resolve with `get_matched_signals` or `rg` over dumped signal names. The paths below are common for Kunminghu-style waveforms.

For every module listed below, also search for FSM/state registers under that module prefix. Start with `*state*`, `*State*`, `*fsm*`, `*FSM*`, and module-specific state names found in the corresponding Chisel file. Map numeric values back to Chisel enum names when the source exposes them; otherwise report numeric states and infer behavior from transitions plus valid/ready/request/response signals.

## Source roots

Default source root:

`/nfs/home/yanyusong/xs-env/XiangShan`

Important Chisel files:

| Area | Files |
|---|---|
| Parameters and common bundles | `src/main/scala/xiangshan/Parameters.scala`, `src/main/scala/xiangshan/Bundle.scala`, `src/main/scala/xiangshan/backend/Bundles.scala`, `src/main/scala/xiangshan/package.scala` |
| Frontend and FTQ | `src/main/scala/xiangshan/frontend/Frontend.scala`, `src/main/scala/xiangshan/frontend/FrontendBundle.scala`, `src/main/scala/xiangshan/frontend/NewFtq.scala`, `src/main/scala/xiangshan/frontend/IBuffer.scala`, `src/main/scala/xiangshan/frontend/IFU.scala` |
| Decode | `src/main/scala/xiangshan/backend/decode/DecodeStage.scala`, `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`, `src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala` |
| Backend control block | `src/main/scala/xiangshan/backend/CtrlBlock.scala` |
| Rename | `src/main/scala/xiangshan/backend/rename/Rename.scala`, `src/main/scala/xiangshan/backend/rename/RenameTable.scala`, free-list definitions under `backend/rename/` |
| Dispatch | `src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala` |
| ROB | `src/main/scala/xiangshan/backend/rob/Rob.scala`, `src/main/scala/xiangshan/backend/rob/RobBundles.scala`, `src/main/scala/xiangshan/backend/rob/RobEnqPtrWrapper.scala`, `src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala` |
| Issue/Scheduler | `src/main/scala/xiangshan/backend/issue/Scheduler.scala`, `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`, `src/main/scala/xiangshan/backend/issue/EntryBundles.scala`, `src/main/scala/xiangshan/backend/issue/IssueBlockParams.scala` |
| Data path and writeback | `src/main/scala/xiangshan/backend/datapath/DataPath.scala`, `src/main/scala/xiangshan/backend/datapath/WbArbiter.scala`, `src/main/scala/xiangshan/backend/datapath/WbConfig.scala`, `src/main/scala/xiangshan/backend/datapath/WbFuBusyTable.scala` |
| Architectural state / Difftest / CSR | `src/main/scala/top/Top.scala`, `src/main/scala/top/XSNoCTop.scala`, `src/main/scala/xiangshan/backend/rob/Rob.scala`, `src/main/scala/xiangshan/backend/rob/ExceptionGen.scala`, `src/main/scala/xiangshan/backend/fu/CSR.scala`, `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`, `src/main/scala/xiangshan/backend/fu/NewCSR/*.scala` |
| MemBlock and LSU bundles | `src/main/scala/xiangshan/mem/MemBlock.scala`, `src/main/scala/xiangshan/mem/Bundles.scala` |
| LSQ | `src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala`, `VirtualLoadQueue.scala`, `LoadQueueRAW.scala`, `LoadQueueRAR.scala`, `LoadQueueReplay.scala`, `StoreQueue.scala`, `StoreQueueData.scala`, `LoadQueueData.scala`, `LSQWrapper.scala` |
| Load/store/atomic pipeline | `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`, `StoreUnit.scala`, `AtomicsUnit.scala` |
| Store buffer | `src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala`, `FakeSbuffer.scala` |
| DCache | `src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala`, `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`, `src/main/scala/xiangshan/cache/dcache/mainpipe/AtomicsReplayUnit.scala` |
| Functional-unit encodings | `src/main/scala/xiangshan/backend/fu/FuType.scala`, `src/main/scala/xiangshan/package.scala` (`SrcType`, `LSUOpType`) |

## Common waveform prefixes

Core:

`TOP.SimTop.l_soc.core_with_l2.core`

Backend:

`TOP.SimTop.l_soc.core_with_l2.core.backend`

Control block:

`TOP.SimTop.l_soc.core_with_l2.core.backend.inner_ctrlBlock`

MemBlock:

`TOP.SimTop.l_soc.core_with_l2.core.memBlock`

## Frontend and FTQ

Goal: prove the PC entered frontend/FTQ, record `ftqIdx`, and show fetch/IBuffer handshakes.

Search patterns:

- `*ftq*pc*`, `*ftq*ptr*`, `*ftq*idx*`, `*ftqIdx*`
- `*frontend*io*to*Ctrl*`, `*frontend*io*to*Backend*`
- `*IBuffer*io_in_valid`, `*IBuffer*io_in_ready`, `*IBuffer*io_out_*`
- `*cf_pc*`, `*instr*`, `*ftqOffset*`, `*ftqIdx*`
- `*frontend*state*`, `*ftq*state*`, `*IBuffer*state*`

Interpretation:

- FTQ records fetch-block metadata. `ftqIdx` is the frontend identity that later appears in control-flow/uop metadata.
- IBuffer converts fetch packets into control-flow entries for decode. Track `valid/ready` on both input and output.

## Frontend prediction and redirect

Goal: prove whether the target instruction did or did not cause redirect, and trace the recovery path with waveform evidence.

Search roots:

- Frontend/FTQ: `{core}.frontend`, `{core}.frontend.ftq`, frontend child scopes containing `FTQ`, `Ftq`, `ftqInter`, `IBuffer`, `IFU`.
- Backend control: `{ctrl}`, `{ctrl}.rob`, backend writeback/control-flow update paths.
- Execution producers: branch/jump units, CSR/trap unit, LoadUnit/StoreUnit/LSQ, memory violation paths.

Signal name patterns:

- Prediction metadata: `*pred*`, `*Pred*`, `*target*`, `*taken*`, `*br*`, `*jal*`, `*jalr*`, `*ras*`, `*return*`, `*call*`, `*cfVec*`, `*cfiUpdate*`, `*preDecode*`.
- FTQ identity and update: `*ftqIdx*`, `*FtqIdx*`, `*ftqPtr*`, `*FtqPtr*`, `*ftqOffset*`, `*toFtq*`, `*fromFtq*`, `*pdWb*`, `*pc_mem*`.
- Redirect/flush: `*redirect*`, `*Redirect*`, `*flush*`, `*Flush*`, `*flushOut*`, `*needFlush*`, `*isMisPred*`, `*stFtqIdx*`, `*cfiUpdate_pc*`, `*cfiUpdate_target*`, `*cfiUpdate_taken*`, `*memoryViolation*`.
- Trap/exception redirect: `*trap*`, `*Trap*`, `*exception*`, `*Exception*`, `*cause*`, `*tval*`, `*epc*`, `*interrupt*`.
- Memory redirect/replay: `*replay*`, `*nuke*`, `*violation*`, `*ldCancel*`, `*stld*`, `*ldld*`, `*rollback*`.

Interpretation:

- For branch/jump instructions, compare predicted target/taken metadata with backend-resolved redirect payload when dumped.
- For memory/CSR/exception instructions, identify redirect producers through ROB/CSR/LSQ/LoadUnit/StoreUnit valid signals and carry ROB/LQ/SQ identity.
- If no redirect occurred, explicitly report inactive redirect/flush/trap/memory-violation signals in the target's execute/writeback/commit window.

## Bubble and performance signals

Goal: quantify target-related bubbles and stalls with waveform evidence.

Search patterns:

- Generic handshakes: `*valid`, `*ready`, `*fire`, `*canAccept`, `*allow*`, `*blocked*`, `*stall*`, `*bubble*`, `*empty*`, `*full*`.
- Frontend: `*IBuffer*valid`, `*IBuffer*ready`, `*toIbuffer*`, `*fetch_bubble*`, `*ifu2ibuffer*`, `*FtqFullStall*`, `*FtqUpdateBubble*`, `*frontend*stall*`, `*frontend*bubble*`.
- Decode/rename/dispatch: `*decode*valid`, `*decode*ready`, `*rename*valid`, `*rename*ready`, `*dispatch*ready`, `*uopBlockByIQ*`, `*allowDispatch*`, `*thisCanActualOut*`, `*lsqCanAccept*`, `*enqRob*canAccept*`.
- Issue/scheduler: `*IssueQueue*`, `*Scheduler*`, `*select*`, `*grant*`, `*srcReady*`, `*wakeup*`, `*busy*`, `*feedback*`.
- Memory: `*lqCanAccept*`, `*sqCanAccept*`, `*LoadQueue*full*`, `*StoreQueue*full*`, `*dcache*miss*`, `*nack*`, `*bank_conflict*`, `*mshr*`, `*tlb*miss*`, `*replay*`.
- Commit: `*commitValid*`, `*commit_v*`, `*commit_w*`, `*commit_block*`, `*allowOnlyOneCommit*`, `*intr*`, `*flush*`.

Interpretation:

- Use `valid && !ready` for backpressure, `ready && !valid` for an upstream bubble, and `valid && ready` for transfer.
- Correlate bubble attribution with the target's PC/ROB/LQ/SQ/FTQ identity. If a stall is caused by older instructions or shared resources, label it as concurrent pressure instead of target-caused.
- Prefer measured cycle counts over qualitative descriptions.

## Decode

Common prefix:

`{ctrl}.decode.decoders_{lane}`

Signals to load:

- `io_enq_*valid`, `io_enq_*ready`, `io_enq_*pc`, `io_enq_*instr` if present.
- `io_deq_*valid`, `io_deq_*ready`, `io_deq_decodedInst_pc`, `io_deq_decodedInst_instr`.
- Decoded fields: `srcType_*`, `lsrc_*`, `ldest`, `fuType`, `fuOpType`, `rfWen`, `fpWen`, `vecWen`, `waitForward`, `blockBackward`, `canRobCompress`, `uopSplitType`, exception flags.
- Decode-stage state/register-valid signals: `*state*`, `*validReg*`, pipeline register valid bits when no explicit FSM exists.

Code basis:

- `backend/decode/DecodeUnit.scala`: decode IO and instruction decode.
- `backend/decode/DecodeStage.scala`: decode stage plumbing.
- `backend/Bundles.scala`: `DecodedInst`.
- `package.scala` and `backend/fu/FuType.scala`: encodings.

## Rename

Common prefix:

`{ctrl}.rename`

Signals to load:

- Input handshake: `io_in_{lane}_valid`, `io_in_{lane}_ready`, `io_in_{lane}_bits_*pc`, `instr`, decoded fields.
- Output handshake: `io_out_{lane}_valid`, `io_out_{lane}_ready`, `io_out_{lane}_bits_uop_*`.
- Register mapping: `lsrc*`, `ldest`, `psrc*`, `pdest`, old destination/stale pdest if present.
- ROB assignment: `robIdx`.
- Free list: `intFreeList.io_allocateReq_{lane}`, `io_allocatePhyReg_{lane}`, `io_canAllocate`, `io_doAllocate`; use FP/vector free lists if the instruction writes those files.
- Rename table/RAT read/write signals if a mapping decision needs explanation.
- Rename/free-list FSM or pointer state: `*state*`, free-list head/tail/pointer signals, walk/redirect state if dumped.

Code basis:

- `backend/rename/Rename.scala`: physical register allocation, ROB allocation, output uop construction.
- `backend/rename/RenameTable.scala`: logical-to-physical mapping.

## Dispatch and ROB enqueue

Common dispatch prefix:

`{ctrl}.dispatch`

Signals to load:

- `io_fromRename_{lane}_valid`, `ready`, `bits_uop_cf_pc`, `bits_uop_robIdx`, `bits_uop_pdest`, `psrc*`, `fuType`, `fuOpType`, `lqIdx`, `sqIdx`.
- Dispatch gates: `allow`, `uopBlockByIQ`, `thisCanActualOut`, `blockedByWaitForward`, `blockedByBlockBackward`, `enqRob_empty`, `enqRob_canAccept`, LSQ allocation readiness.
- Dispatch state or gating state: `*state*`, wait-forward/block-backward aggregate state if dumped.

Common ROB prefix:

`{ctrl}.rob`

Signals to load:

- `io_enq_req_{lane}_valid`, `ready`, `bits_*pc`, `bits_*robIdx`.
- `robEntries_{idx}_valid`, writeback/commit flags, exception/replay flags if dumped.
- `hasWaitForward`, `hasBlockBackward`, head/tail/enq/deq pointers.
- Commit: `io_commit_*`, debug commit PC, commit valid mask, trap/flush/redirect.
- ROB state: per-entry valid/writeback/commit/exception state, head/tail pointers, redirect/walk/flush state, global `*state*` if present.

Code basis:

- `backend/dispatch/NewDispatch.scala`: dispatch gating and outgoing queues.
- `backend/rob/Rob.scala`: ROB enqueue, state, commit, redirect/trap interactions.
- `backend/rob/RobBundles.scala`: ROB pointer and bundle fields.

## Issue, scheduler, datapath, writeback

Common scheduler prefixes:

- `{backend}.inner_intScheduler*`
- `{backend}.inner_memScheduler*`
- `{backend}.inner_fpScheduler*`
- Issue queues like `IssueQueueStaMou_*`, `IssueQueueStdMoud_*`, load/store/vector queues depending on the instruction.

Signals to load:

- Issue queue enqueue valid/ready and bits: PC, ROB, FU type/op, src states, psrc, pdest, lq/sq.
- Entry state for matching ROB: valid, issued, source-ready, wakeup, select/grant.
- Scheduler output valid/ready to datapath or execution unit.
- Datapath `io_to*Exu_*_valid/ready/bits`: operands, PC, ROB, pdest, lq/sq.
- Writeback `io_from*Exu_*_valid/ready/bits`: ROB, pdest, data, exception, replay, redirect.
- Register file/write port signals if final write data must be proven.
- Scheduler/issue FSM state: `*state*`, entry status fields, source-ready state, issued/granted state.
- Datapath/writeback FSM or arbitration state: `*state*`, arbiter select/grant state, busy-table state.

Code basis:

- `backend/issue/Scheduler.scala`, `IssueQueue.scala`, `EntryBundles.scala`.
- `backend/datapath/DataPath.scala`, `WbArbiter.scala`, `WbConfig.scala`.

## Architectural state, CSR, exceptions, and difftest

Goal: close the trace at the architectural comparison boundary, not only at microarchitectural writeback. For every target instruction, record the values that difftest or commit trace uses to compare architectural state.

Common search roots:

- Top/difftest wrapper: `TOP`, `TOP.SimTop`, `TOP.SimTop.l_soc`, generated `Difftest*` scopes.
- Core/backend: `{core}`, `{backend}`, `{ctrl}`, `{ctrl}.rob`.
- CSR block: search under `{backend}` for child scopes containing `CSR`, `csr`, `NewCSR`, `csrCtrl`, `csrFile`, `inner_csr`.

Signal name patterns:

- Difftest wrapper/events: `*difftest*`, `*Difftest*`, `*diff*`, `*Diff*`, `*TrapEvent*`, `*InstrCommit*`, `*ArchIntRegState*`, `*CSRState*`, `*StoreEvent*`, `*LoadEvent*`.
- Commit architectural info: `*io_commits*`, `*commitValid*`, `*commit_v*`, `*commit_w*`, `*debug_pc*`, `*debug_instr*`, `*debug_ldest*`, `*debug_pdest*`, `*rfWen*`, `*fpWen*`, `*vecWen*`, `*commitType*`, `*needFlush*`.
- GPR/FPR/vector result: `*wdata*`, `*wb*data*`, `*writeback*data*`, `*wdest*`, `*pdest*`, `*ldest*`, `*ArchIntRegState*`, `*ArchFpRegState*`, `*ArchVecRegState*`.
- CSR state: `*mstatus*`, `*sstatus*`, `*mepc*`, `*sepc*`, `*mcause*`, `*scause*`, `*mtval*`, `*stval*`, `*mtvec*`, `*stvec*`, `*satp*`, `*mip*`, `*mie*`, `*medeleg*`, `*mideleg*`, `*mcycle*`, `*minstret*`, `*fflags*`, `*frm*`, `*fcsr*`, `*vtype*`, `*vl*`, `*vstart*`, `*vxsat*`, `*vxrm*`.
- Privilege/debug/virtualization: `*priv*`, `*Privilege*`, `*dmode*`, `*debugMode*`, `*virt*`, `*v*priv*`, `*hstatus*`, `*vsstatus*`, `*vsepc*`, `*vscause*`, `*vstval*`, `*vsatp*`, `*hgatp*`.
- Exception/trap/interrupt: `*exception*`, `*Exception*`, `*exceptionVec*`, `*trap*`, `*Trap*`, `*interrupt*`, `*cause*`, `*tval*`, `*epc*`, `*redirect*`, `*flushOut*`, `*needFlush*`, `*xtval*`, `*gpa*`, `*gpaddr*`.
- Memory difftest events: `*load*diff*`, `*store*diff*`, `*LoadEvent*`, `*StoreEvent*`, `*paddr*`, `*vaddr*`, `*gpaddr*`, `*mask*`, `*data*`, `*mmio*`.

Fields to report when dumped:

- Retire identity: commit lane, commit cycle/time, `isCommit`, `commitValid`, `commit_v`, `commit_w`, PC, instruction, FTQ index/offset, ROB index if exposed.
- Register architectural result: architectural destination, physical destination, write enable, data, and whether the write is integer, floating-point, vector, v0, or vl.
- CSR state: sampled values at commit for all dumped difftest CSR fields. For CSR instructions, show before/after and identify the write source; for non-CSR instructions, still include sampled CSR/privilege state when available.
- Exception/trap state: selected exception bit/cause, interrupt valid/cause, trap target, `epc`, `tval`, guest physical address, `needFlush`, `flushOut`, redirect, and whether the target instruction retires or is killed.
- Memory architectural events: load/store/AMO event valid, address, data, mask, MMIO/cache-error flags, and old/new value interpretation.

Code basis:

- `top/Top.scala` and `top/XSNoCTop.scala`: top-level difftest generation and wrapper wiring.
- `backend/rob/Rob.scala`: commit info, difftest valid generation, architectural retire metadata.
- `backend/rob/ExceptionGen.scala`: exception selection and trap metadata from ROB.
- `backend/fu/CSR.scala`, `backend/fu/wrapper/CSR.scala`, `backend/fu/NewCSR/*.scala`: CSR file, privilege state, trap handling, CSR write semantics.
- `mem/Bundles.scala`, `mem/MemBlock.scala`, `mem/sbuffer/Sbuffer.scala`, LSQ files: memory difftest load/store event information.

## Memory operations

For loads/stores/AMOs, start from dispatch or issue where `lqIdx` and `sqIdx` first become visible, then follow those indices.

MemBlock common prefix:

`{core}.memBlock`

Signals to search:

- `*lqIdx*`, `*sqIdx*`, `*robIdx*`, `*uop*cf*pc*`
- Load unit: `*LoadUnit*`, `*loadUnits*`, `*io_ldin*`, `*io_dcache_req*`, `*io_dcache_resp*`
- Store unit: `*StoreUnit*`, `*storeUnits*`, `*io_stin*`, `*io_stout*`
- LSQ: `*inner_lsq*`, `*LoadQueue*`, `*StoreQueue*`, `*allocated*`, `*addrReady*`, `*dataReady*`
- Store buffer: `*inner_sbuffer*`, `*io_flush_valid*`, `*io_flush_empty*`, `*io_enq*`, `*io_deq*`
- Atomics: `*inner_atomicsUnit*`, `*io_dcache_req*`, `*io_dcache_resp*`, `*amo_*`
- DCache: `*dcache*`, `*mainPipe*`, `*req*`, `*resp*`, `*miss*`, `*hit*`, `*replay*`, `*nack*`, `*error*`, `*redirect*`, `*flush*`
- Memory FSMs: `*LoadQueue*state*`, `*StoreQueue*state*`, `*sbuffer*state*`, `*atomics*state*`, `*dcache*state*`, `*mainPipe*state*`, `*miss*state*`, `*replay*state*`

Required fields:

- Virtual address, physical address if present, aligned cache-line address if different.
- Opcode/fuOp, memory command, mask, data, old data for AMO.
- Hit/miss/replay/exception/error.
- Redirect/flush/cancel and whether it kills this instruction by ROB/LQ/SQ.
- FSM/state values for LSQ, store buffer, atomics unit, DCache mainpipe/miss/replay logic while the memory transaction is active.

Code basis:

- `mem/MemBlock.scala`: top-level LSU wiring, atomics/store buffer/cache connections.
- `mem/Bundles.scala`: LSU bundles.
- `mem/lsqueue/*.scala`: load/store queue allocation, replay, RAW/RAR, data queues.
- `mem/pipeline/LoadUnit.scala`, `StoreUnit.scala`, `AtomicsUnit.scala`.
- `mem/sbuffer/Sbuffer.scala`.
- `cache/dcache/DCacheWrapper.scala`, `cache/dcache/mainpipe/MainPipe.scala`.

## Final report detail level

For each major stage include a paragraph like:

`A -> signal -> B`: value, cycle/time, why it matters, and source-code line.

Example:

`Rename -> dispatch.io_fromRename_2.bits.uop.robIdx -> ROB/issue queues`: value `47`, produced when rename allocates the ROB entry, then used after dispatch as the stable identity for issue, writeback, and commit.

If the waveform lacks a signal, state that it was not dumped and use the nearest available producer/consumer evidence instead.

For FSM reporting, include:

`Module/FSM -> state signal`: numeric value/name, cycle range, surrounding transition, why that state matters for the instruction.
