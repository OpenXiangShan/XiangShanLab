# Exception, Interrupt, Debug, and Privilege Analysis

Use this file whenever a module may generate, transform, prioritize, carry, or consume exception, interrupt, debug, trap, CSR, privilege, PMP/PMA, IOPMP, AIA, or page-fault metadata. This is mandatory for backend control/ROB/CSR, frontend redirect/fetch exception, mem/cache/MMU/TLB, and any FU with exception outputs.

## Required Coverage

For each relevant module, identify:

| Class | Producer | Metadata/signals | Priority rule | Consumer | Architectural effect |
| --- | --- | --- | --- | --- | --- |

Classes to check:
- Synchronous exceptions: illegal instruction, breakpoint, ecall, load/store/inst address misaligned, page fault, access fault, virtual instruction, guest page fault, floating/vector exceptions when represented architecturally.
- Interrupts: machine/supervisor/virtual/timer/software/external/local interrupt signals as implemented in CSR/trap logic, including chiselAIA APLIC/IMSIC/MSI paths when present.
- Debug: debug mode entry, trigger match, breakpoint, single-step, debug CSR paths, DRET/MRET/SRET interactions where relevant.
- Privilege: current privilege mode, virtualization mode, satp/hgatp/vsatp effects, mstatus/sstatus/hstatus fields, PMP/PMA/IOPMP permission checks, TLB permission checks, CSR/AIA register privilege checks.
- Redirect/trap: trap target generation, frontend redirect, backend flush, ROB walk/recovery, CSR state update.

## Search Terms

Use these terms in code search:

- Exception/trap: `exception`, `exceptionVec`, `ExceptionNO`, `trap`, `tval`, `cause`, `Trap`, `Raise`, `fault`, `misalign`, `access`, `illegal`, `breakpoint`, `ecall`
- Interrupt: `interrupt`, `intr`, `mip`, `mie`, `sip`, `sie`, `localInterrupt`, `timer`, `external`, `software`
- Debug: `debug`, `Debug`, `dmode`, `Trigger`, `tdata`, `Dret`, `dret`, `singleStep`, `breakpoint`
- Privilege: `priv`, `Privilege`, `mode`, `mstatus`, `sstatus`, `hstatus`, `satp`, `vsatp`, `hgatp`, `PMP`, `PMA`, `permission`, `pf`, `af`, `pageFault`, `accessFault`
- CSR/AIA: `CSR`, `NewCSR`, `CSRBundles`, `CSRModule`, `TrapHandleModule`, `InterruptFilter`, `MachineLevel`, `SupervisorLevel`, `HypervisorLevel`, `DebugLevel`, `APLIC`, `IMSIC`, `mstateen`, `sstateen`, `hstateen`, `sireg`, `siselect`, `vsireg`, `vsiselect`
- IOPMP: `IOPMP`, `iopmp`, `permission`, `deny`, `accessFault`, `APB`, `bypass`, `entry`, `lock`

## Analysis Procedure

1. Locate where metadata is created. Example sources: decoder exception bits, TLB page-fault/access-fault, PMP/PMA, FU exception output, trigger/debug match, CSR interrupt filtering.
2. Trace how metadata is carried in bundles. Note fields such as `exceptionVec`, `trigger`, `debug`, `intr`, `priv`, `vaddr`, `gpaddr`, `tval`, `cause`, `robIdx`, `ftqIdx`.
3. Identify prioritization. Look for priority encoders, `PriorityMux`, ordered `when/.elsewhen`, ROB oldest selection, trap priority modules, interrupt filters.
4. Identify action. Does it cause replay, cancel, flush, redirect, trap entry, CSR write, debug entry, commit block, or writeback metadata only?
5. Explain privilege mode. State which mode/check controls legality or permission and where the result is consumed.
6. Explain recovery. Trace to frontend redirect, backend flush, ROB commit/trap, rename recovery, LSQ/SQ cleanup, or cache/TLB flush.

## Memory/MMU Specifics

For memory/cache analysis, always cover:
- Load/store/AMO address misalignment.
- TLB page faults and guest page faults.
- Access faults from PMP/PMA or cache/MMIO checks.
- Uncache/MMIO non-data errors.
- Exception buffering in load/store queues.
- Store exception commit timing versus speculative detection.
- Whether exception is replayed, delayed until commit, or immediately redirects.

## Output Requirements

Add a section titled `Exception / Interrupt / Debug / Privilege` when relevant.

Include:
- A table of metadata/signals.
- The priority rule.
- The propagation path from producer to architectural action.
- The exact code artifacts and parameters involved.
- Any uncertainty if the selected module only carries metadata but does not consume it.
