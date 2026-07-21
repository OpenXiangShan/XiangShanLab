# XiangShan Virtualization and Protection Drivers

Use this file when a module touches virtualization state, address translation, PMP, PMA, page-table walks, guest translation, IOPMP, MMIO/uncache protection, privilege checks, or any metadata derived from `satp`, `vsatp`, `hgatp`, ASID, VMID, privilege mode, or supervisor/security domain configuration. System-level permission, guest/host trap interaction, and save/handle/restore phase tests must also use `skills/systemVirtualizationPermissionDrivers.md`.

Every architecture claim must be verified with `riscv-spec`/UDB or an upstream RISC-V spec source when UDB lacks coverage. Every microarchitecture claim must cite effective Chisel code from the code analyzer. Debug/protection overlap must also use `skills/debugEventDrivers.md`.

## Virtualization / Protection Driver Shape

```markdown
## Virtualization Protection Verification
| Protection ID | Scope | Stimulus construction | Expected architectural result | Expected microarchitectural result | Checkers |
| --- | --- | --- | --- | --- | --- |
```

For every selected scenario, include:

- Current mode: M/S/U, HS/VS/VU, debug when relevant.
- Translation state: bare, single-stage, VS-stage, G-stage, two-stage translation.
- Context fields: ASID, VMID, privilege, domain, `satp`, `vsatp`, `hgatp`, `mstatus`, `sstatus`, `hstatus`, `mstatus.MPRV`, SUM, MXR, MXR-like effective permissions if implemented.
- Protection source: PMP, PMA, page permission, guest page permission, IOPMP, cacheability/MMIO region, AIA/IMSIC privilege routing when relevant.
- Expected metadata: cause, tval/stval/vstval, guest physical address metadata, access-fault/page-fault/guest-page-fault selection, redirect target, and no illegal side effect.

## Virtualization State Drivers

| Protection ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `V_MODE_M_S_U` | Base privilege switch | Execute memory/CSR/fetch from U/S/M with different permission bits | Access legality and trap routing match spec | Privilege checker |
| `V_MODE_HS_VS_VU` | Hypervisor mode switch | Switch HS/VS/VU and execute fetch/load/store/CSR | VS/VU accesses use virtualized state and trap to correct mode | Hypervisor checker |
| `V_SATP_SWITCH` | Process switch | Change `satp`/ASID while TLB/cache/PTW/MSHR entries are live | Stale translation killed, tagged, or rechecked | Context isolation checker |
| `V_VSATP_SWITCH` | Guest address-space switch | Change `vsatp` while VS/VU translations are live | VS-stage stale translations cannot complete visibly | VM context checker |
| `V_HGATP_SWITCH` | VM switch | Change `hgatp`/VMID while guest transactions are outstanding | G-stage stale translations cannot update new VM | VM isolation checker |
| `V_MPRV_SUM_MXR` | Effective privilege | Vary MPRV, MPP, SUM, MXR across load/store/fetch | Effective permission and fault result match spec | Privilege/permission checker |
| `V_STATEEN` | State-enable legality | Access virtualized/extension state with state-enable bits disabled | Illegal/virtual instruction trap as spec requires | CSR legality checker |
| `V_DEBUG_TRANSLATION` | Debug overlap | Enter debug with translation/protection faults pending | Debug priority, `dcsr`/`dpc`, privilege state, and memory side effects match spec/code evidence | Debug checker |

## Page Translation Drivers

| Protection ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `P_PAGE_VALID_INVALID` | PTE valid boundary | Valid and invalid PTEs at each level | Page fault metadata correct | Page checker |
| `P_PAGE_PERM_RWX` | Permission matrix | Sweep R/W/X/U/A/D/G permissions for fetch/load/store/AMO | Correct allow/page fault for each access type | Permission checker |
| `P_PAGE_AD_BITS` | Accessed/dirty | Clear A/D for load/store/AMO and test update/fault policy | A/D update or fault follows spec/config | A/D checker |
| `P_SUPERPAGE_BOUNDARY` | Superpage | Map superpages with aligned and misaligned PPNs | Legal superpage works; misconfigured superpage faults | PTW checker |
| `P_TWO_STAGE_TRANSLATION` | VS + G translation | Valid VS-stage plus valid/invalid G-stage combinations | Final PA or guest-page/page fault priority correct | Two-stage checker |
| `P_GUEST_PAGE_FAULT` | Guest fault metadata | Trigger VS-stage and G-stage faults separately | Guest fault cause/tval/gpa metadata correct | Guest fault checker |
| `P_TLB_REFILL_INVALIDATE` | Refill/invalidate conflict | PTW refill races SFENCE/HFENCE/context switch | Stale refill cannot create valid hit | TLB conflict checker |
| `P_PAGE_BOUNDARY_ACCESS` | Boundary address | Access first/last byte of page and crossing-page load/store/vector | Split/fault/tval priority correct | Boundary checker |

## PMP Drivers

| Protection ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMP_OFF_TOR_NA4_NAPOT` | Address match modes | Sweep OFF/TOR/NA4/NAPOT entries, first/last byte inside/outside | Match result and permission match spec/code | PMP checker |
| `PMP_PRIORITY` | Entry priority | Multiple PMP entries match same address | Lowest/highest priority per spec/code wins | PMP priority checker |
| `PMP_LOCKED` | Lock bit | Locked entry then attempt config change | Locked config and permissions stable | PMP lock checker |
| `PMP_RWX_MATRIX` | Permission matrix | Sweep R/W/X across fetch/load/store/AMO | Access fault or allow matches config | PMP permission checker |
| `PMP_MPRV` | Effective mode | M-mode load/store with MPRV/MPP selecting lower mode | PMP check uses effective privilege correctly | PMP privilege checker |
| `PMP_PAGE_INTERACTION` | PMP plus page fault | Address that can trigger page and PMP fault candidates | Priority and cause match spec/code | Fault priority checker |
| `PMP_CONTEXT_SWITCH` | Switch with live entries | Priv/process/VM/domain switch while PMP-checked request outstanding | Permission is tagged/rechecked or request killed | Context checker |

## PMA / MMIO / Cacheability Drivers

| Protection ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `PMA_CACHEABLE_UNCACHE` | Region type boundary | Access cacheable/uncache/MMIO boundary addresses | Correct cache path, bus path, ordering, and faults | PMA checker |
| `PMA_ATOMIC_SUPPORT` | Atomic legality | AMO/LR/SC to supported and unsupported PMA regions | Allow or access fault per PMA | Atomic PMA checker |
| `PMA_MISALIGN` | Misalignment support | Misaligned load/store/AMO in regions with different support | Misalign/access fault priority correct | PMA misalign checker |
| `PMA_EXECUTE` | Fetch permission | Fetch from executable and non-executable PMA regions | Instruction access fault/page fault priority correct | IFU/PMA checker |
| `PMA_SIDE_EFFECT` | Side-effect region | Speculative/prefetch access to MMIO/side-effect region | No illegal speculative side effect | Side-effect checker |
| `PMA_BUS_ERROR` | Bus error response | Inject bus error from PMA/MMIO target | Access fault metadata and replay/flush behavior correct | Bus/protection checker |

## IOPMP Drivers

| Protection ID | Scenario | Stimulus construction | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `IOPMP_MASTER_ID` | Master-specific permission | Sweep master IDs and address regions | Only authorized masters pass | IOPMP checker |
| `IOPMP_ENTRY_PRIORITY` | Multiple matches | Multiple IOPMP entries match same transaction | Priority/lock behavior follows code/spec | IOPMP priority checker |
| `IOPMP_CONFIG_LOOKUP_RACE` | Config race | Config write races permission lookup | Defined old/new/stall behavior | IOPMP conflict checker |
| `IOPMP_DENY_RESPONSE` | Deny path | Denied request on AXI/TL/MMIO path | Error/deny response and architectural fault correct | IOPMP response checker |
| `IOPMP_DOMAIN_SWITCH` | Domain switch | Change domain/security config while requests outstanding | Stale allow cannot leak across domain | Domain isolation checker |

## Combined Priority Drivers

| Protection ID | Scenario | Candidate events | Expected behavior | Checkers |
| --- | --- | --- | --- | --- |
| `VP_PAGE_PMP_PMA` | Page + PMP + PMA | Same access can trigger page fault, PMP deny, and PMA deny | Cause priority and metadata match spec/code | Fault priority checker |
| `VP_GPAGE_PAGE_ACCESS` | Guest page + host page/access | Two-stage fault candidates in same instruction | Guest/host fault priority correct | Two-stage priority checker |
| `VP_MISALIGN_PAGE_PMP` | Misalign + page + PMP | Misaligned access also crosses bad page/protected region | Winning fault and no side effect | Memory exception checker |
| `VP_INTERRUPT_FAULT` | Interrupt plus protection fault | Enabled interrupt pending when protection fault reaches trap | Exception/interrupt priority and nesting correct | Trap checker |
| `VP_REPLAY_FAULT` | Replay plus protection fault | TLB/cache replay condition and protection fault overlap | Fault not lost; replay does not double-commit | Replay/fault checker |
| `VP_DEBUG_FAULT` | Debug plus protection fault | Debug trigger overlaps page/PMP/PMA/IOPMP fault | Debug/fault priority, `dcsr`/`dpc`, EPC/tval non-corruption, and side effects match spec/code | Debug checker |

## Required Cross-Products

Use directed cross-products, not blind full explosion:

- Access type x protection source: fetch/load/store/AMO/LR/SC/vector/prefetch/CBO over page/PMP/PMA/IOPMP.
- Privilege x translation: U/S/M/HS/VS/VU over bare/single-stage/two-stage.
- Boundary address x permission: first/last byte inside/outside page/PMP/PMA/IOPMP/MMIO region.
- Context switch x outstanding request: `satp`, `vsatp`, `hgatp`, ASID, VMID, domain, privilege while TLB/PTW/cache/MSHR/bus request is live.
- Exception x interrupt/debug: protection fault with interrupt pending, trap entry, handler nested interrupt, xRET with pending interrupt, and debug entry. Debug combinations select applicable rows from `skills/debugEventDrivers.md`.

## Completion Checklist

Before a virtualization/protection driver is complete:

- Every virtualized mode and context field implemented by the branch is tested.
- Page translation covers valid/invalid PTE, R/W/X/U/A/D/G, superpage, two-stage translation, guest faults, and invalidation/refill races.
- PMP covers OFF/TOR/NA4/NAPOT, priority, lock, R/W/X, MPRV/effective privilege, and page/PMP priority.
- System permission tests traverse page leaf/non-leaf, guest page, PMP, PMA, and IOPMP read/write/execute matrices using `skills/systemVirtualizationPermissionDrivers.md`.
- PMA covers cacheable/uncache/MMIO, atomic support, misalignment, fetch permission, side-effect regions, and bus errors.
- IOPMP covers master ID, entry priority, config/lookup races, deny responses, and domain switch.
- Combined priority tests cover page/PMP/PMA/IOPMP, misalign, replay, interrupt, debug, and two-stage faults when reachable. Debug priority tests cite the exact XiangShan arbitration and CSR update code evidence.
- All checks validate cause, tval/stval/vstval, guest physical metadata, EPC, privilege stack, redirect target, side effects, and stale-context isolation.

