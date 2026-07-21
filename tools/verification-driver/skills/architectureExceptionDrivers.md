# XiangShan Architecture Exception Drivers

Use this file for architecture-side verification of instruction exceptions, exception priority, memory-access exception priority, and exception/interrupt nesting. Every architecture claim must be verified through the `riscv-spec` skill, UDB, or an explicitly cited upstream RISC-V spec source when UDB lacks coverage.

This driver is architecture-focused. Microarchitecture drivers must still verify how the code carries, buffers, replays, redirects, and commits the exception metadata. Exception tests that depend on instruction operands, floating-point operands, integer operands, addresses, masks, immediates, CSR fields, or protocol fields must also use `skills/operandBoundaryDrivers.md`. Exception tests involving translation, privilege, PMP, PMA, IOPMP, guest faults, or two-stage translation must also use `skills/virtualizationProtectionDrivers.md`. Debug event tests involving `ebreak`, trigger match, single-step, halt/resume, `dret`, debug CSRs, debug-mode restrictions, or debug priority must also use `skills/debugEventDrivers.md`. Guest/host cross-trigger and save/handle/restore phase tests must also use `skills/systemVirtualizationPermissionDrivers.md`.

## Per-Instruction Exception Driver Shape

```markdown
## Per-Instruction Exception Verification
| Instruction | Exception class | Trigger construction | Expected architectural result | Spec source | Checkers |
| --- | --- | --- | --- | --- | --- |
```

For every implemented instruction or instruction class, generate all architecturally reachable exception cases:

- Illegal instruction: unsupported extension, disabled FP/vector state, reserved encoding, privilege-illegal instruction, CSR access violation, invalid system instruction, or mode/config mismatch.
- Breakpoint/debug: `ebreak`, trigger match, debug entry, single-step, halt request, resume/`dret`, and debug CSR legality when relevant. Each debug event must cite code-derived producer, priority, and CSR update evidence from the XiangShan analyzer.
- Environment call: `ecall` from U/S/M/VS/VU/HS modes when implemented.
- Instruction address exceptions: misaligned fetch target when applicable, instruction access fault, instruction page fault, guest instruction page fault.
- Load exceptions: address misaligned, load access fault, load page fault, guest load page fault.
- Store/AMO exceptions: address misaligned, store/AMO access fault, store/AMO page fault, guest store/AMO page fault.
- CSR exceptions: privilege violation, read-only field write, illegal CSR address, virtual-instruction exception, state-enable violations when implemented.
- FP/vector exceptions: illegal disabled state, floating exception flags, vector state legality, `vstart`/fault-only-first behavior when applicable.
- Cache-management/fence exceptions: illegal mode, unsupported op, page/access fault for address-bearing operations when applicable.

## Instruction Coverage Matrix

Every architecture driver must include an instruction matrix:

| Instruction/class | Legal modes | Required extensions/config | Possible exceptions | Priority notes | Required tests |
| --- | --- | --- | --- | --- | --- |

Minimum instruction classes:

- Integer ALU and control flow.
- Branch and jump, including target alignment and page/access fault on fetch target.
- Load/store: integer, FP, vector, misaligned, uncache/MMIO.
- AMO, LR, SC.
- Fence, fence.i, CBO/CMO/cache instructions.
- CSR/system: `ecall`, `ebreak`, `mret`, `sret`, `dret`, `wfi`, CSRRS/CSRRC/CSRRW variants.
- FP and vector instructions when enabled by configuration.
- Privileged, hypervisor, debug, AIA/IMSIC/APLIC-related CSR/MMIO accesses when present.

## Memory Instruction Multi-Exception Priority

For each memory-related instruction, construct cases where one instruction can encounter multiple exception candidates during execution. The expected result is the architecturally highest-priority exception according to spec and implementation-defined priority where allowed.

```markdown
## Memory Exception Priority Verification
| Instruction | Candidate exceptions in same execution | Construction | Expected winning exception | Lower-priority handling | Checkers |
| --- | --- | --- | --- | --- | --- |
```

Required memory priority combinations:

| Priority ID | Instruction class | Candidate exceptions | Construction goal | Expected check |
| --- | --- | --- | --- | --- |
| `E_MEM_MISALIGN_PAGE` | Load/store/AMO | Misaligned address plus page fault | Address crosses alignment boundary and points to unmapped/protected page | Winning exception matches spec/code priority |
| `E_MEM_MISALIGN_ACCESS` | Load/store/AMO | Misaligned address plus access fault/PMP/PMA/IOPMP deny | Misaligned target also denied by protection | Winning exception matches spec/code priority |
| `E_MEM_PAGE_ACCESS` | Load/store/AMO/fetch | Page fault plus access fault | PTE/guest translation and PMA/PMP/IOPMP can both fault | Winning exception and tval/metadata correct |
| `E_MEM_GUEST_PAGE_HOST_PAGE` | VS/VU memory | Guest-page fault plus host-stage fault candidate | Nested translation triggers VS/G-stage conflicts | Guest/host fault priority and metadata correct |
| `E_MEM_TLB_REFILL_INTERRUPT` | Load/store/fetch | Translation miss/refill plus interrupt pending | Interrupt arrives during translation path | Precise exception/interrupt priority at commit/trap correct |
| `E_MEM_REPLAY_EXCEPTION` | Load/store/AMO | Replay condition plus exception metadata | Cache miss/replay and fault metadata overlap | Exception is not lost or double-reported |
| `E_MEM_LRSC_FAULT_RESERVATION` | LR/SC | Reservation update plus page/access/misalign fault | Faulting LR/SC candidate | Reservation state and exception result correct |
| `E_MEM_VECTOR_ELEMENT_FAULT` | Vector load/store | Element fault plus later element candidate fault | Multiple element addresses fault differently | First-fault/vstart behavior and priority correct |
| `E_MEM_CBO_FAULT` | CBO/cache op | Illegal mode plus address translation/protection fault | Cache op with illegal privilege and bad address | Legal priority between illegal instruction and memory fault verified |
| `E_MEM_MMIO_FAULT_ORDER` | Uncache/MMIO | MMIO error plus page/access/order condition | MMIO response error with pending ordering constraint | Reported fault and ordering behavior correct |

For every memory priority test, check:

- `mcause`/`scause`/`vscause` or debug cause.
- `mtval`/`stval`/`vstval`, guest physical address metadata, and virtual instruction metadata when applicable.
- `mepc`/`sepc`/`vsepc` points to the faulting instruction.
- No younger instruction becomes architecturally visible.
- Store/AMO side effects do not occur on fault unless explicitly allowed.
- LR/SC reservation state is correct after fault.
- Vector `vstart` and partial completion behavior match spec/config.

## Exception Plus Interrupt Nesting

When testing exceptions, also construct interrupt-pending and nested-trap scenarios.

```markdown
## Exception Interrupt Nesting Verification
| Scenario | Base exception | Interrupt/nesting stimulus | Expected architectural result | Checkers |
| --- | --- | --- | --- | --- |
```

Required nesting scenarios:

| Nesting ID | Scenario | Stimulus construction | Expected check |
| --- | --- | --- | --- |
| `N_EXC_INT_SAME_CYCLE` | Exception and interrupt pending together | Faulting instruction reaches trap point while enabled interrupt is pending | Correct exception-vs-interrupt priority and cause |
| `N_EXC_INT_DISABLED` | Exception with disabled interrupt | Pending interrupt disabled by xIE/delegation/mask | Exception taken; interrupt remains pending if spec requires |
| `N_EXC_INT_DELEGATED` | Delegated interrupt during exception | Exception target and interrupt delegation target differ | Trap goes to architecturally selected mode |
| `N_TRAP_ENTRY_INT_ARRIVE` | Interrupt arrives during trap entry | Assert interrupt as trap CSRs are being written | CSR updates are precise; nested trap follows legal enable state |
| `N_HANDLER_NESTED_INT` | Interrupt inside exception handler | Handler enables interrupts, then another interrupt arrives | Nested trap saves new EPC/cause without corrupting outer trap state |
| `N_MRET_SRET_INT` | Return from trap with pending interrupt | Execute MRET/SRET/DRET while interrupt pending | Return state and immediately taken interrupt behavior match spec |
| `N_DEBUG_EXCEPTION_INT` | Debug plus exception/interrupt | Trigger debug entry while exception and interrupt are possible | Debug priority and dcsr/dpc state correct |
| `N_AIA_VIRTUAL_INT` | AIA/IMSIC virtual interrupt nesting | VS/VU guest interrupt pending during guest/host exception | Virtual interrupt routing and delegation correct |
| `N_NMI_OR_LOCAL_INT` | Local/NMI-like interrupt if implemented | Non-maskable/local interrupt overlaps exception | Implementation-specific priority checked against code/spec source |

For nesting tests, check:

- Trap mode and privilege stack fields.
- xIE/xPIE/xPP, virtualization fields, delegation fields, and debug fields.
- EPC/cause/tval for outer and inner traps.
- Pending bits are consumed, preserved, or masked according to spec.
- Redirect target for each trap entry/return.
- No double retirement of the faulting instruction.

## Architecture Exception Completion Checklist

Before an architecture exception driver is complete:

- Every implemented instruction or instruction class lists all architecturally reachable exceptions.
- Every exception trigger that depends on an operand sweeps the relevant operand boundary classes from `skills/operandBoundaryDrivers.md`.
- Every exception claim cites `riscv-spec`/UDB or an upstream spec source.
- Every memory instruction has multi-exception priority tests when multiple fault sources can be constructed.
- Memory exception priority tests include page, guest page, PMP, PMA, IOPMP, MMIO/uncache, and two-stage translation combinations when reachable.
- System exception tests cover guest page fault plus host trap/fault/interrupt and host page fault plus guest exception/trap/fault/interrupt across save/handle/restore phases when reachable.
- Every exception test has an interrupt-pending variant.
- Nested interrupt/trap scenarios cover trap entry, handler re-enable, trap return, delegation, debug, and virtualization when implemented.
- Debug exception and nesting scenarios select applicable rows from `skills/debugEventDrivers.md` and check `dcsr`, `dpc`, debug cause, privilege restoration, EPC/cause/tval non-corruption, pending bits, and no younger side effects.
- The checker validates cause, tval, epc, privilege stack, delegation, pending bits, redirect target, and no younger architectural side effects.

