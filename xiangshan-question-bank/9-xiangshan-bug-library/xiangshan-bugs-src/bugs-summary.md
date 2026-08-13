# Bugs Summary

- Repository: `OpenXiangShan/XiangShan`
- Issue state filter: `all`
- Generated at: `2026-08-13T06:55:04.961974+00:00`
- Bug issues: **1053** (open: **102**, closed: **951**)
- Bug PRs: **874** (open: **0**, closed: **874**)

## Category Counts

| Category | Issues | Open issues | Closed issues | PRs | Open PRs | Closed PRs |
|---|---:|---:|---:|---:|---:|---:|
| Frontend | 0 | 0 | 0 | 0 | 0 | 0 |
| Backend | 0 | 0 | 0 | 0 | 0 | 0 |
| Mem/Cache | 1 | 0 | 1 | 0 | 0 | 0 |
| Uncategorized | 1052 | 102 | 950 | 874 | 0 | 874 |

## Frontend Bugs

### Issues

| Issue | Title | Author | Submitted at | Branch | RTL commit | Reproducer files |
|---:|---|---|---|---|---|---:|
| - | - | - | - | - | - | 0 |

### Pull Requests

| PR | Title | Author | State | Commits | Merged at |
|---:|---|---|---|---:|---|
| - | - | - | - | 0 | - |

## Backend Bugs

### Issues

| Issue | Title | Author | Submitted at | Branch | RTL commit | Reproducer files |
|---:|---|---|---|---|---|---:|
| - | - | - | - | - | - | 0 |

### Pull Requests

| PR | Title | Author | State | Commits | Merged at |
|---:|---|---|---|---:|---|
| - | - | - | - | 0 | - |

## Mem/Cache Bugs

### Issues

| Issue | Title | Author | Submitted at | Branch | RTL commit | Reproducer files |
|---:|---|---|---|---|---|---:|
| [#4800](https://github.com/OpenXiangShan/XiangShan/pull/4800) | fix(vsegment): vec segment should also respond to bus error | Anzooooo | 2025-06-10 | - | `-` | 0 |

### Pull Requests

| PR | Title | Author | State | Commits | Merged at |
|---:|---|---|---|---:|---|
| - | - | - | - | 0 | - |

## Uncategorized Bugs

### Issues

| Issue | Title | Author | Submitted at | Branch | RTL commit | Reproducer files |
|---:|---|---|---|---|---|---:|
| [#6318](https://github.com/OpenXiangShan/XiangShan/issues/6318) | Cross-page misaligned store can commit an untranslated tail address after a DTLB miss | DFPMTS | 2026-08-01 | - | `-` | 2 |
| [#6303](https://github.com/OpenXiangShan/XiangShan/issues/6303) | Nested exception in M-mode causes subsequent exception to deadlock | lhb-sec | 2026-07-29 | - | `-` | 6 |
| [#6302](https://github.com/OpenXiangShan/XiangShan/issues/6302) | XiangShan does not trap on vector instruction with reserved vsew encoding | lhb-sec | 2026-07-28 | - | `-` | 7 |
| [#6298](https://github.com/OpenXiangShan/XiangShan/issues/6298) | XiangShan misses illegal-instruction trap for out-of-range vstart | lhb-sec | 2026-07-28 | - | `-` | 4 |
| [#6296](https://github.com/OpenXiangShan/XiangShan/issues/6296) | Vector store reports wrong `mcause` in M-mode | lhb-sec | 2026-07-28 | - | `-` | 3 |
| [#6295](https://github.com/OpenXiangShan/XiangShan/issues/6295) | `vle32ff.v` corrupts destination vector register on Load Access Fault | lhb-sec | 2026-07-28 | - | `-` | 4 |
| [#6294](https://github.com/OpenXiangShan/XiangShan/issues/6294) | A store-page-faulting AMO clobbers its destination register (rd) | wndmll643 | 2026-07-28 | - | `-` | 3 |
| [#6293](https://github.com/OpenXiangShan/XiangShan/issues/6293) | `vle64ff.v` misses Load Access Fault on misaligned access to I/O PMA region | lhb-sec | 2026-07-28 | - | `-` | 3 |
| [#6292](https://github.com/OpenXiangShan/XiangShan/issues/6292) | vstart set to wrong element on a page-faulting vector load (for debugging assistance) | wndmll643 | 2026-07-28 | - | `-` | 3 |
| [#6289](https://github.com/OpenXiangShan/XiangShan/issues/6289) | AMO deadlocks after `vse32.v` store access fault | lhb-sec | 2026-07-27 | - | `-` | 3 |
| [#6288](https://github.com/OpenXiangShan/XiangShan/issues/6288) | `sw` instruction reports wrong `mcause` in M-mode | lhb-sec | 2026-07-27 | - | `-` | 4 |
| [#6286](https://github.com/OpenXiangShan/XiangShan/pull/6286) | perf(mdp): refine strict StoreSet prediction | weidingliu | 2026-07-27 | - | `-` | 0 |
| [#6276](https://github.com/OpenXiangShan/XiangShan/issues/6276) | IMSIC: VS-mode claim (`vstopei` EOI) is broadcast to all guest interrupt files instead of the `vgein`-selected one, causing silent MSI loss | LeeHaofeng | 2026-07-23 | - | `-` | 5 |
| [#6275](https://github.com/OpenXiangShan/XiangShan/pull/6275) | fix(L1PF, LSQ): reject stale TLB updates and nonphysical store wakeups | fuhuakai | 2026-07-23 | - | `-` | 0 |
| [#6268](https://github.com/OpenXiangShan/XiangShan/issues/6268) | L1 DCache assertion: "failed too many SCs in a row, resv set addr always match" | lhb-sec | 2026-07-22 | - | `-` | 4 |
| [#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267) | `fsw fa1,716(t6)` to PMA-missing address `0x2cc` causes StoreUnit deadlock | lhb-sec | 2026-07-21 | - | `-` | 3 |
| [#6265](https://github.com/OpenXiangShan/XiangShan/issues/6265) | Behavior mismatch on `ld s8,0xf0(t6)` to misaligned address `0xf1` | lhb-sec | 2026-07-21 | - | `-` | 3 |
| [#6264](https://github.com/OpenXiangShan/XiangShan/issues/6264) | Sequential fetch fall-through across Sv39 canonical boundary does not raise instruction page fault | ZhongYic00 | 2026-07-21 | - | `-` | 7 |
| [#6262](https://github.com/OpenXiangShan/XiangShan/issues/6262) | XS bug-report — vlseg to unmapped page deadlocks (VSegmentUnit) | wndmll643 | 2026-07-21 | - | `-` | 3 |
| [#6259](https://github.com/OpenXiangShan/XiangShan/issues/6259) | Variant of HLV.WU ignores SPVP=VU effective privilege for final PMP checks found on NEMU | ZhongYic00 | 2026-07-21 | - | `-` | 3 |
| [#6258](https://github.com/OpenXiangShan/XiangShan/pull/6258) | fix(nmi): fix trap to hs/vs event when both NMI and excp occur | sinceforYy | 2026-07-21 | - | `-` | 0 |
| [#6256](https://github.com/OpenXiangShan/XiangShan/pull/6256) | fix(csr,dbltrp): fix s_EX_DT should be controlled by sstatus.SDT | sinceforYy | 2026-07-20 | - | `-` | 0 |
| [#6243](https://github.com/OpenXiangShan/XiangShan/pull/6243) | chore: cherry-pick v2 fixes to v3 (260714) | wissygh | 2026-07-16 | - | `-` | 0 |
| [#6242](https://github.com/OpenXiangShan/XiangShan/pull/6242) | feat(loadUnit): add support of arbiter of RRBankConflict | weidingliu | 2026-07-15 | - | `-` | 0 |
| [#6241](https://github.com/OpenXiangShan/XiangShan/pull/6241) | fix(missQueue): remove incorrect XSError | jlong299 | 2026-07-15 | - | `-` | 0 |
| [#6235](https://github.com/OpenXiangShan/XiangShan/pull/6235) | fix(Ftq): fix backendExceptionPtr | ngc7331 | 2026-07-13 | - | `-` | 0 |
| [#6229](https://github.com/OpenXiangShan/XiangShan/issues/6229) | NewLoadUnit: misaligned load crossing a 16B (VWord) boundary misses store-to-load forwarding on the lower half and commits stale data | wndmll643 | 2026-07-13 | - | `-` | 5 |
| [#6228](https://github.com/OpenXiangShan/XiangShan/pull/6228) | fix(storeQueue): fix write zero to sbuffer of cbo.zero | weidingliu | 2026-07-13 | - | `-` | 0 |
| [#6227](https://github.com/OpenXiangShan/XiangShan/issues/6227) | Bare-mode trap zero-extends (truncates) trap-address CSRs (mepc/mtval/sepc/stval) in genTrapVA on kunminghu-v3 (refiled) | wndmll643 | 2026-07-12 | - | `-` | 5 |
| [#6223](https://github.com/OpenXiangShan/XiangShan/pull/6223) | Fix nmie 0710 | wissygh | 2026-07-10 | - | `-` | 0 |
| [#6215](https://github.com/OpenXiangShan/XiangShan/issues/6215) | Pointer masking (PMLEN=16) not applied to G-stage translation input under vsatp=Bare (onlyStage2) + hgatp=Sv48×4 | ruc-jty | 2026-07-09 | - | `-` | 1 |
| [#6214](https://github.com/OpenXiangShan/XiangShan/issues/6214) | Pointer masking not applied to debug address-trigger comparison (MemTrigger compares raw vaddr) | ruc-jty | 2026-07-09 | - | `-` | 2 |
| [#6213](https://github.com/OpenXiangShan/XiangShan/pull/6213) | fix(Ifu): fix uncache with prevHalfRvi | ngc7331 | 2026-07-09 | - | `-` | 0 |
| [#6212](https://github.com/OpenXiangShan/XiangShan/issues/6212) | VS-Stage PTW Guest-Page-Fault mtval2 Is Not Precise | jf-cc727 | 2026-07-09 | - | `-` | 3 |
| [#6211](https://github.com/OpenXiangShan/XiangShan/issues/6211) | Cross-Page Instruction Page Fault Reports Wrong mepc and mtval | jf-cc727 | 2026-07-09 | - | `-` | 3 |
| [#6210](https://github.com/OpenXiangShan/XiangShan/issues/6210) | Sv39 Non-Canonical Instruction Fetch Reaches the Low Alias | jf-cc727 | 2026-07-09 | - | `-` | 3 |
| [#6209](https://github.com/OpenXiangShan/XiangShan/issues/6209) | Split Load Tail Page Fault Does Not Retire | jf-cc727 | 2026-07-09 | - | `-` | 3 |
| [#6199](https://github.com/OpenXiangShan/XiangShan/issues/6199) | [Bug][PMP][2-core] S-mode store to L=1 locked PMP region does not raise store access fault | Security-HC | 2026-07-06 | - | `-` | 2 |
| [#6197](https://github.com/OpenXiangShan/XiangShan/pull/6197) | fix(MissQueue): fix nMaxPrefetchEntry logic | Ruomio | 2026-07-06 | - | `-` | 0 |
| [#6185](https://github.com/OpenXiangShan/XiangShan/pull/6185) | fix(LoadPipe): suppress S3 hit metadata updates on s2 kill | jlong299 | 2026-07-02 | - | `-` | 0 |
| [#6183](https://github.com/OpenXiangShan/XiangShan/pull/6183) | fix(Sbuffer): split CMO sbuffer drain empty checks | jlong299 | 2026-07-02 | - | `-` | 0 |
| [#6182](https://github.com/OpenXiangShan/XiangShan/issues/6182) | Stale interrupt taken after `mstatus.MIE`/`sstatus.SIE`/`vsstatus.SIE` is cleared | LeeHaofeng | 2026-07-02 | - | `-` | 0 |
| [#6168](https://github.com/OpenXiangShan/XiangShan/issues/6168) | [Bug] debug watchpoint trigger lost on FOF non-first element | ruc-jty | 2026-06-29 | - | `-` | 1 |
| [#6167](https://github.com/OpenXiangShan/XiangShan/pull/6167) | fix(Ittage): fix altDiffer condition | ngc7331 | 2026-06-29 | - | `-` | 0 |
| [#6165](https://github.com/OpenXiangShan/XiangShan/issues/6165) | `TAGE` can expose a new entry with a stale useful counter to FTQ/train metadata | YzhDDDing | 2026-06-28 | - | `-` | 12 |
| [#6162](https://github.com/OpenXiangShan/XiangShan/issues/6162) | TOR partial match admits an aligned 8-byte store and commits a side effect | Jacob-yen | 2026-06-28 | - | `-` | 9 |
| [#6161](https://github.com/OpenXiangShan/XiangShan/issues/6161) | PTW does not fault when a TOR entry covers only half of an 8-byte PTE | Jacob-yen | 2026-06-28 | - | `-` | 10 |
| [#6160](https://github.com/OpenXiangShan/XiangShan/issues/6160) | ITTAGE `altDiffers` asserts with no alternate provider and mistrains useful counter | ScottaMcdonald | 2026-06-27 | - | `-` | 17 |
| [#6159](https://github.com/OpenXiangShan/XiangShan/issues/6159) | `uTage` keeps issuing SRAM reads after `SBPCTL.ABTB_ENABLE` is cleared and can block table writes | ScottaMcdonald | 2026-06-27 | - | `-` | 17 |
| [#6158](https://github.com/OpenXiangShan/XiangShan/issues/6158) | `prefetch.w` loses write intent and is admitted as a read prefetch on a read-only page | ScottaMcdonald | 2026-06-27 | - | `-` | 6 |
| [#6157](https://github.com/OpenXiangShan/XiangShan/issues/6157) | `FTQ` accepts delayed BPU metadata for redirect-flushed entries | ScottaMcdonald | 2026-06-27 | - | `-` | 8 |
| [#6156](https://github.com/OpenXiangShan/XiangShan/issues/6156) | `satp` first-fetch page fault writes sign-extended mepc/mtval on kunminghu-v3 | ScottaMcdonald | 2026-06-27 | - | `-` | 12 |
| [#6155](https://github.com/OpenXiangShan/XiangShan/issues/6155) | `uTage` SRAM banks continue read-clock activity after `ABTB_ENABLE` is cleared | ScottaMcdonald | 2026-06-27 | - | `-` | 13 |
| [#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154) | `NewLoadUnit` scalar loads can write back after redirect kill and expose wrong-path data on the RF write bus | YzhDDDing | 2026-06-26 | - | `-` | 20 |
| [#6153](https://github.com/OpenXiangShan/XiangShan/issues/6153) | `Load` breakpoint trigger does not kill S1 DCache lookup and can leave post-trap cache state | YzhDDDing | 2026-06-26 | - | `-` | 15 |
| [#6152](https://github.com/OpenXiangShan/XiangShan/issues/6152) | [RVV] vzext.vf4 / vsext.vf4 writes zero to high 64-bit (elements 2..3) | Security-HC | 2026-06-26 | - | `-` | 0 |
| [#6151](https://github.com/OpenXiangShan/XiangShan/issues/6151) | [Bug] VSegmentUnit: segment instructions with vl=0 stall the memory unit hundreds of cycles instead of retiring immediately | ruc-jty | 2026-06-26 | - | `-` | 3 |
| [#6150](https://github.com/OpenXiangShan/XiangShan/issues/6150) | Illegal `DRET` outside Debug Mode emits stale xRET redirect and creates a secret-dependent ICache fetch oracle | YzhDDDing | 2026-06-26 | - | `-` | 14 |
| [#6149](https://github.com/OpenXiangShan/XiangShan/issues/6149) | `sbpctl.RAS_ENABLE` does not disable RAS/URAS and allows secret-dependent wrong-path fetches | YzhDDDing | 2026-06-26 | - | `-` | 16 |
| [#6148](https://github.com/OpenXiangShan/XiangShan/issues/6148) | `ITTAGE` is accessed by direct-only fetch blocks, creating a software-visible timing channel | YzhDDDing | 2026-06-26 | - | `-` | 14 |
| [#6147](https://github.com/OpenXiangShan/XiangShan/pull/6147) | fix(ftq): fix train cache flush condition | TheKiteRunner24 | 2026-06-26 | - | `-` | 0 |
| [#6144](https://github.com/OpenXiangShan/XiangShan/pull/6144) | fix(Ifu): Pbmt.IO should wait for last commit | ngc7331 | 2026-06-26 | - | `-` | 0 |
| [#6143](https://github.com/OpenXiangShan/XiangShan/issues/6143) | Newest REF (ready-to-run/riscv64-nemu-interpreter-so) misses an execute breakpoint (EX_BP) on pc-match, while Xiangshan and the previous REF trap correctly | Jason-Young123 | 2026-06-25 | - | `-` | 1 |
| [#6141](https://github.com/OpenXiangShan/XiangShan/issues/6141) | Xiangshan and NEMU diverge between EX_LAM and EX_LAF on pure load cross-page MMIO access when only the second PMP region loses R permission | Jason-Young123 | 2026-06-25 | - | `-` | 1 |
| [#6139](https://github.com/OpenXiangShan/XiangShan/issues/6139) | Xiangshan and NEMU diverge in mtval/tval on pure store cross-page MMIO access when only the second PMP region loses W permission | Jason-Young123 | 2026-06-25 | - | `-` | 1 |
| [#6138](https://github.com/OpenXiangShan/XiangShan/issues/6138) | Same-FTQ wrong-path branch can train BPU and leak one bit through timing | YzhDDDing | 2026-06-25 | - | `-` | 13 |
| [#6137](https://github.com/OpenXiangShan/XiangShan/issues/6137) | `ABTB` can emit stale predictions after `sbpctl` disables ABTB and drive transient I/D-cache accesses | YzhDDDing | 2026-06-25 | - | `-` | 20 |
| [#6135](https://github.com/OpenXiangShan/XiangShan/issues/6135) | RAS disable control is ignored for return prediction and leaves a secret-dependent timing side channel | YzhDDDing | 2026-06-25 | - | `-` | 7 |
| [#6134](https://github.com/OpenXiangShan/XiangShan/issues/6134) | `PBMT-IO` instruction fetch skips `IFU` uncache last-commit serialization | YzhDDDing | 2026-06-25 | - | `-` | 14 |
| [#6133](https://github.com/OpenXiangShan/XiangShan/issues/6133) | `LoadQueueUncache` full-buffer rollback can carry accepted MMIO load metadata | YzhDDDing | 2026-06-25 | - | `-` | 10 |
| [#6131](https://github.com/OpenXiangShan/XiangShan/pull/6131) | fix(vstopi): fix the mapping of vsei index | sinceforYy | 2026-06-24 | - | `-` | 0 |
| [#6130](https://github.com/OpenXiangShan/XiangShan/issues/6130) | In ```MSHR```, ```denied``` / ```corrupt``` / ```w_replResp``` error flags can be silently dropped when RXDAT and RXRSP fire in the same cycle | LeeHaofeng | 2026-06-24 | - | `-` | 0 |
| [#6127](https://github.com/OpenXiangShan/XiangShan/issues/6127) | ```HPerfCounter``` out-of-bounds dynamic index: CSR-writable event selector beyond range counts the wrong event | LeeHaofeng | 2026-06-24 | - | `-` | 0 |
| [#6126](https://github.com/OpenXiangShan/XiangShan/issues/6126) | HLV.WU ignores SPVP=VU effective privilege for final PMP checks | Jacob-yen | 2026-06-24 | - | `-` | 5 |
| [#6121](https://github.com/OpenXiangShan/XiangShan/pull/6121) | fix(perf): fixed perf-event `frontend_stall_cycle` | wissygh | 2026-06-23 | - | `-` | 0 |
| [#6120](https://github.com/OpenXiangShan/XiangShan/pull/6120) | timing(loadQueueRAW): use age matrix for RAW oldest select | zzQGyy | 2026-06-23 | - | `-` | 0 |
| [#6117](https://github.com/OpenXiangShan/XiangShan/pull/6117) | perf(Sbuffer): set in.req.ready to true if req can be merged | jlong299 | 2026-06-22 | - | `-` | 0 |
| [#6116](https://github.com/OpenXiangShan/XiangShan/issues/6116) | Commit group trace skips 10 bytes of instructions | ZhongYic00 | 2026-06-21 | - | `-` | 7 |
| [#6113](https://github.com/OpenXiangShan/XiangShan/issues/6113) | [Bug] VS-mode wrongly raises EX_II instead of EX_VI when mstateen0.AIA=0 and vsiselect ∈ 0x30-0x3F | ruc-jty | 2026-06-18 | - | `-` | 3 |
| [#6104](https://github.com/OpenXiangShan/XiangShan/pull/6104) | fix(debug, csr): fix csr to support debug spec 1.0 | wissygh | 2026-06-17 | - | `-` | 0 |
| [#6102](https://github.com/OpenXiangShan/XiangShan/pull/6102) | timing(L1PrefetchComponent): defer sent_vec updates to pf fire | zzQGyy | 2026-06-17 | - | `-` | 0 |
| [#6101](https://github.com/OpenXiangShan/XiangShan/pull/6101) | fix(pmu): update resolve-to-perfQueue and commit pmu logic | Erlkonigal | 2026-06-16 | - | `-` | 0 |
| [#6100](https://github.com/OpenXiangShan/XiangShan/pull/6100) | fix(CSR): fix reset value of mstatus.mdt & mnstatus.nmie | wissygh | 2026-06-16 | - | `-` | 0 |
| [#6096](https://github.com/OpenXiangShan/XiangShan/pull/6096) | fix(Fence): fix fence opcodes | sinceforYy | 2026-06-15 | - | `-` | 0 |
| [#6095](https://github.com/OpenXiangShan/XiangShan/pull/6095) | fix(PMPChecker, PMAChecker): non-dmode can't access memory of debug | wissygh | 2026-06-15 | - | `-` | 0 |
| [#6093](https://github.com/OpenXiangShan/XiangShan/issues/6093) | ```VectorFloatAdder.scala``` overflow rounding uses wrong normal-path GRS bits | LeeHaofeng | 2026-06-13 | - | `-` | 1 |
| [#6086](https://github.com/OpenXiangShan/XiangShan/pull/6086) | fix(vstopi): fix iid when Candidate3 and Candidate5 enable | sinceforYy | 2026-06-11 | - | `-` | 0 |
| [#6085](https://github.com/OpenXiangShan/XiangShan/issues/6085) | `vsoxei32.v` fails to commit store | lhb-sec | 2026-06-11 | - | `-` | 3 |
| [#6084](https://github.com/OpenXiangShan/XiangShan/pull/6084) | fix(TrapInst): fix temporarily stored trapInstInfo generation | sinceforYy | 2026-06-11 | - | `-` | 0 |
| [#6081](https://github.com/OpenXiangShan/XiangShan/pull/6081) | fix(StoreQueue): fix cbo handle earlier than instruction commit | weidingliu | 2026-06-10 | - | `-` | 0 |
| [#6080](https://github.com/OpenXiangShan/XiangShan/issues/6080) | ```Cat(0.U, ...)``` width inference bug in ```VectorFloatAdder``` | LeeHaofeng | 2026-06-10 | - | `-` | 3 |
| [#6079](https://github.com/OpenXiangShan/XiangShan/issues/6079) | `vl1re16.v` incorrectly computes `vstart` on Load Access Fault | lhb-sec | 2026-06-10 | - | `-` | 3 |
| [#6078](https://github.com/OpenXiangShan/XiangShan/issues/6078) | `prefetch.i` hangs on invalid address | lhb-sec | 2026-06-09 | - | `-` | 3 |
| [#6075](https://github.com/OpenXiangShan/XiangShan/pull/6075) | fix(mnret): fix MNret error and clear mnstatus.mnpv/mnpp | sinceforYy | 2026-06-09 | - | `-` | 0 |
| [#6074](https://github.com/OpenXiangShan/XiangShan/pull/6074) | fix(mret): fix vsstatus Valid Path in MretEvent | sinceforYy | 2026-06-09 | - | `-` | 0 |
| [#6071](https://github.com/OpenXiangShan/XiangShan/pull/6071) | fix(csr, xstatus): mark HLV/HLVX/HSV memory traps as virtual | wissygh | 2026-06-08 | - | `-` | 0 |
| [#6070](https://github.com/OpenXiangShan/XiangShan/pull/6070) | fix(rob): fix the X-state propagation for commit_w | xiaofeibao-xjtu | 2026-06-08 | - | `-` | 0 |
| [#6068](https://github.com/OpenXiangShan/XiangShan/issues/6068) | WFI does not resume execution when some interrupts become pending | ruc-jty | 2026-06-08 | - | `-` | 1 |
| [#6067](https://github.com/OpenXiangShan/XiangShan/pull/6067) | fix(CSR, vscause): gate VS hvictl interrupt cause by interrupt type | wissygh | 2026-06-08 | - | `-` | 0 |
| [#6066](https://github.com/OpenXiangShan/XiangShan/issues/6066) | VMask.scala ```shift``` truncation bug for ```SEW=8``` and large ```uopIdx``` | LeeHaofeng | 2026-06-07 | - | `-` | 0 |
| [#6065](https://github.com/OpenXiangShan/XiangShan/issues/6065) | ```genVUopOffset``` uses wrong ```nf``` semantics | LeeHaofeng | 2026-06-07 | - | `-` | 0 |
| [#6064](https://github.com/OpenXiangShan/XiangShan/issues/6064) | Copy-Paste Typo in Mux1H Default Condition — ```isvrgatherei16``` Repeated Instead of ```isvcompress``` in   VPermSrcTypeModule | LeeHaofeng | 2026-06-07 | - | `-` | 1 |
| [#6063](https://github.com/OpenXiangShan/XiangShan/issues/6063) | ```CVT16.scala``` fflags ```NV``` lost for ```vfrec7 -inf``` | LeeHaofeng | 2026-06-07 | - | `-` | 1 |
| [#6062](https://github.com/OpenXiangShan/XiangShan/issues/6062) | S-mode cbo.clean to a PMP-denied block aborts StoreQueue instead of raising access fault | jimmymtest | 2026-06-07 | - | `-` | 7 |
| [#6061](https://github.com/OpenXiangShan/XiangShan/issues/6061) | [Bug] MRET to VU does not clear `vsstatus.SDT` | ruc-jty | 2026-06-05 | - | `-` | 8 |
| [#6060](https://github.com/OpenXiangShan/XiangShan/issues/6060) | [bug] XiangShan wrongly raises store access fault for cbo instruction on PBMT=NC memory | ruc-jty | 2026-06-05 | - | `-` | 7 |
| [#6059](https://github.com/OpenXiangShan/XiangShan/issues/6059) | PTWFilterEntry: `inflight_counter` bit-width too small (use log2Up(Size+1) not log2Up(Size)) | LeeHaofeng | 2026-06-05 | - | `-` | 3 |
| [#6058](https://github.com/OpenXiangShan/XiangShan/pull/6058) | fix(mtval2): fix the incorrect generation of mtval2 during IGPF | sinceforYy | 2026-06-05 | - | `-` | 0 |
| [#6057](https://github.com/OpenXiangShan/XiangShan/issues/6057) | Illegal-instruction `mtval` uses a younger instruction encoding instead of the faulting instruction | ra4ing | 2026-06-05 | - | `-` | 13 |
| [#6051](https://github.com/OpenXiangShan/XiangShan/pull/6051) | fix(PMA,PMP): fix RMW base value for CSRRS/CSRRC in PMP and PMA | sinceforYy | 2026-06-03 | - | `-` | 0 |
| [#6048](https://github.com/OpenXiangShan/XiangShan/issues/6048) | `debug_s1UseUbtbUtage` is a copy of `debug_s1UseUbtb` | LeeHaofeng | 2026-06-01 | - | `-` | 1 |
| [#6042](https://github.com/OpenXiangShan/XiangShan/issues/6042) | `vle16.v` fault handling incorrectly sets `vstart` to `vl` instead of the faulting element index | lhb-sec | 2026-05-29 | - | `-` | 5 |
| [#6039](https://github.com/OpenXiangShan/XiangShan/issues/6039) | `vsetvl` with `rd=zero` causes core hang | lhb-sec | 2026-05-28 | - | `-` | 3 |
| [#6038](https://github.com/OpenXiangShan/XiangShan/issues/6038) | `NewCSR` Double-trap exception redirected to M-mode uses vectored interrupt offset instead of exception offset (`mtvec + 0`) | LeeHaofeng | 2026-05-28 | - | `-` | 1 |
| [#6037](https://github.com/OpenXiangShan/XiangShan/issues/6037) | [Bug] vmvnr_unaligned_regs | nyh1 | 2026-05-28 | - | `-` | 5 |
| [#6036](https://github.com/OpenXiangShan/XiangShan/issues/6036) | [Bug] vmv1r_vstart_ge_evl | nyh1 | 2026-05-28 | - | `-` | 6 |
| [#6035](https://github.com/OpenXiangShan/XiangShan/issues/6035) | `vle32ff.v` fails to update exception CSRs upon Load Access Fault | lhb-sec | 2026-05-28 | - | `-` | 3 |
| [#6034](https://github.com/OpenXiangShan/XiangShan/issues/6034) | 【BUG】 `vfsgnj.vv` incorrectly dirties `mstatus.FS` | nyh1 | 2026-05-28 | - | `-` | 7 |
| [#6032](https://github.com/OpenXiangShan/XiangShan/issues/6032) | [Bug] WFI does not resume execution when some interrupts become pending | ruc-jty | 2026-05-27 | - | `-` | 2 |
| [#6031](https://github.com/OpenXiangShan/XiangShan/pull/6031) | fix(vstopi): fix vstopi Candidate3 enable conditation | sinceforYy | 2026-05-27 | - | `-` | 0 |
| [#6030](https://github.com/OpenXiangShan/XiangShan/pull/6030) | fix(vstopi): fix vstopi Candidate3 enable conditation | sinceforYy | 2026-05-27 | - | `-` | 0 |
| [#6027](https://github.com/OpenXiangShan/XiangShan/issues/6027) | `onlyC3Enable` is always false | LeeHaofeng | 2026-05-26 | - | `-` | 0 |
| [#6023](https://github.com/OpenXiangShan/XiangShan/issues/6023) | mtinst/htinst pseudo instruction missing bit 5 (store indication) for guest store page faults | ZhongYic00 | 2026-05-26 | - | `-` | 8 |
| [#6022](https://github.com/OpenXiangShan/XiangShan/issues/6022) | `vlse64.v` fails to trigger Load Access Fault upon illegal address access | lhb-sec | 2026-05-25 | - | `-` | 3 |
| [#6018](https://github.com/OpenXiangShan/XiangShan/issues/6018) | `triggerTag` truncated when `trainOnVaddr` is enabled (`vtag` 40 bit → `triggerTag` 32 bit) | LeeHaofeng | 2026-05-25 | - | `-` | 3 |
| [#6015](https://github.com/OpenXiangShan/XiangShan/issues/6015) | Pipeline hangs on `vluxei32.v` when accessing unmapped physical addresses | lhb-sec | 2026-05-25 | - | `-` | 2 |
| [#6012](https://github.com/OpenXiangShan/XiangShan/issues/6012) | Reserved Hint (`ori rd=x0`) incorrectly triggers Load Access Fault (Cause 5) | lhb-sec | 2026-05-22 | - | `-` | 2 |
| [#6010](https://github.com/OpenXiangShan/XiangShan/pull/6010) | fix(Intr): fix priority number of SEI when SEI is injected from M-level | sinceforYy | 2026-05-22 | - | `-` | 0 |
| [#6009](https://github.com/OpenXiangShan/XiangShan/pull/6009) | fix(ubtb): check if s0 hits t1 victim | ngc7331 | 2026-05-22 | - | `-` | 0 |
| [#6003](https://github.com/OpenXiangShan/XiangShan/pull/6003) | fix(StoreQueue): fix bug of fullOverlap when store is cross16B | weidingliu | 2026-05-22 | - | `-` | 0 |
| [#6002](https://github.com/OpenXiangShan/XiangShan/issues/6002) | Same-page cross-16B store-load OctaWord nuke mask is not shifted for upper VWord | YzhDDDing | 2026-05-22 | - | `-` | 22 |
| [#6001](https://github.com/OpenXiangShan/XiangShan/issues/6001) | [BUG] SEI injected from M‑mode is encoded as priority 0 instead of 256 | ruc-jty | 2026-05-21 | - | `-` | 3 |
| [#6000](https://github.com/OpenXiangShan/XiangShan/issues/6000) | JumpUnit compressed c.jr backend redirect clears isRVC | Wowblk | 2026-05-21 | - | `-` | 11 |
| [#5998](https://github.com/OpenXiangShan/XiangShan/issues/5998) | StoreQueue cross-16B multi-match partial forward is treated as safe full overlap | YzhDDDing | 2026-05-21 | - | `-` | 16 |
| [#5995](https://github.com/OpenXiangShan/XiangShan/issues/5995) | [BUG]kunminghu-v2 HLVX does not check final PMA/PMP execute permission | biquanha | 2026-05-20 | - | `-` | 8 |
| [#5994](https://github.com/OpenXiangShan/XiangShan/issues/5994) | Backend redirect for compressed `c.bnez` reports `isRVC=0`, causing wrong redirect CFI PC | ScottaMcdonald | 2026-05-19 | - | `-` | 17 |
| [#5993](https://github.com/OpenXiangShan/XiangShan/pull/5993) | feat(DCacheWrapper): add perfcnt for l2 hint accuracy | Frankslu | 2026-05-19 | - | `-` | 0 |
| [#5989](https://github.com/OpenXiangShan/XiangShan/pull/5989) | perf(l1pf): add prefetch request handshake to l2 | Maxpicca-Li | 2026-05-19 | - | `-` | 0 |
| [#5988](https://github.com/OpenXiangShan/XiangShan/issues/5988) | `MainBTB` replacement state aliases across different physical sets | YzhDDDing | 2026-05-19 | - | `-` | 25 |
| [#5985](https://github.com/OpenXiangShan/XiangShan/pull/5985) | fix(ifu): fix instruction concatenation error during cross-channel fetch | my-mayfly | 2026-05-18 | - | `-` | 0 |
| [#5963](https://github.com/OpenXiangShan/XiangShan/pull/5963) | timing(MemBlock): optimize LSQ and L1 prefetch critical paths | zzQGyy | 2026-05-14 | - | `-` | 0 |
| [#5962](https://github.com/OpenXiangShan/XiangShan/pull/5962) | fix(ICache): explicitly set `s1_itlbPbmt`'s init width | ngc7331 | 2026-05-14 | - | `-` | 0 |
| [#5960](https://github.com/OpenXiangShan/XiangShan/issues/5960) | Zicbop prefetch.r / prefetch.i hint can cause no-progress hang in TLMinimalConfig | wang02020119 | 2026-05-14 | - | `-` | 1 |
| [#5959](https://github.com/OpenXiangShan/XiangShan/pull/5959) | fix(Ifu,InstrUncache): do not mark incomplete if is RVC or has exception | ngc7331 | 2026-05-14 | - | `-` | 0 |
| [#5958](https://github.com/OpenXiangShan/XiangShan/issues/5958) | Kunminghu-v2: LoadQueueReplay assertion on translated cross-page vector byte load/store | 0x1B05 | 2026-05-14 | - | `-` | 11 |
| [#5952](https://github.com/OpenXiangShan/XiangShan/pull/5952) | fix(debug, csr): fix csr to support debug spec 1.0 | wissygh | 2026-05-12 | - | `-` | 0 |
| [#5943](https://github.com/OpenXiangShan/XiangShan/issues/5943) | Incorrect `mtval` for faulting `vsse16.v` on kunminghu-v2 | youzi27 | 2026-05-11 | - | `-` | 9 |
| [#5939](https://github.com/OpenXiangShan/XiangShan/pull/5939) | fix(LoadUnit): fix perfCounter of LoadUnit | weidingliu | 2026-05-11 | - | `-` | 0 |
| [#5934](https://github.com/OpenXiangShan/XiangShan/issues/5934) | `vlseg2e16.v` corrupts an active destination lane during masked segment load | KnightGOKU | 2026-05-10 | - | `-` | 21 |
| [#5933](https://github.com/OpenXiangShan/XiangShan/issues/5933) | `vlsseg3e64.v` loads wrong active destination data for zero-stride segment load | KnightGOKU | 2026-05-10 | - | `-` | 21 |
| [#5932](https://github.com/OpenXiangShan/XiangShan/issues/5932) | `vluxseg5ei32.v` corrupts active destination data for indexed segment load | KnightGOKU | 2026-05-10 | - | `-` | 21 |
| [#5931](https://github.com/OpenXiangShan/XiangShan/issues/5931) | `vlseg2e32ff.v` loads wrong field0 data for misaligned fault-only-first segment load | KnightGOKU | 2026-05-10 | - | `-` | 19 |
| [#5930](https://github.com/OpenXiangShan/XiangShan/issues/5930) | `vle8ff.v` corrupts active loaded byte value | KnightGOKU | 2026-05-10 | - | `-` | 16 |
| [#5929](https://github.com/OpenXiangShan/XiangShan/issues/5929) | `vloxseg5ei64.v` misses load access fault and commits destination vector registers | KnightGOKU | 2026-05-10 | - | `-` | 10 |
| [#5928](https://github.com/OpenXiangShan/XiangShan/issues/5928) | Incorrect source element selection in `vsext` | youzi27 | 2026-05-09 | - | `-` | 10 |
| [#5926](https://github.com/OpenXiangShan/XiangShan/pull/5926) | fix(Interrupt): `stepie` should control hvictl inject interrupt | wissygh | 2026-05-09 | - | `-` | 0 |
| [#5921](https://github.com/OpenXiangShan/XiangShan/issues/5921) | Incorrect `vstart` update after faulting vector indexed store | youzi27 | 2026-05-09 | - | `-` | 2 |
| [#5919](https://github.com/OpenXiangShan/XiangShan/issues/5919) | Reserved `vmv<nr>r.v` encodings are executed instead of being rejected | youzi27 | 2026-05-08 | - | `-` | 1 |
| [#5916](https://github.com/OpenXiangShan/XiangShan/issues/5916) | Unexpected exception behavior for `prefetch.r` | youzi27 | 2026-05-08 | - | `-` | 2 |
| [#5913](https://github.com/OpenXiangShan/XiangShan/pull/5913) | fix(StoreQueue): fix entry of invalid in unalignQueue | weidingliu | 2026-05-08 | - | `-` | 0 |
| [#5910](https://github.com/OpenXiangShan/XiangShan/issues/5910) | Diff-test fails when EX_IAF occurs across 2 physical pages in 0x1000_0000 ~ 0x1fff_ffff, related to #5872 | Jason-Young123 | 2026-05-07 | - | `-` | 2 |
| [#5908](https://github.com/OpenXiangShan/XiangShan/issues/5908) | [BUG] UnalignQueue "enqPtr < deqPtr" assertion triggered by cross-16B sd under sbuffer-stride pressure | mmxsrup | 2026-05-07 | - | `-` | 6 |
| [#5887](https://github.com/OpenXiangShan/XiangShan/pull/5887) | refactor(MissQueue): Parallel enqueue in MissQueue | Ruomio | 2026-04-30 | - | `-` | 0 |
| [#5874](https://github.com/OpenXiangShan/XiangShan/pull/5874) | fix(ifu): do not defer exception signal until instruction reassembly is complete | my-mayfly | 2026-04-29 | - | `-` | 0 |
| [#5872](https://github.com/OpenXiangShan/XiangShan/issues/5872) | Diff-test fails when EX_IAF (Exception: InstrAccessFault) occurs across two physical pages (unexpected mtval and mcause) | Jason-Young123 | 2026-04-28 | - | `-` | 2 |
| [#5867](https://github.com/OpenXiangShan/XiangShan/pull/5867) | fix(jump, perf): fix redirect valid | sinceforYy | 2026-04-27 | - | `-` | 0 |
| [#5865](https://github.com/OpenXiangShan/XiangShan/issues/5865) | XiangShan misses illegal-instruction trap for reserved masked `vmerge.vvm` with vd = v0 | KnightGOKU | 2026-04-27 | - | `-` | 6 |
| [#5862](https://github.com/OpenXiangShan/XiangShan/pull/5862) | fix(CSR, mtvec): add reset value for mtvec | wissygh | 2026-04-26 | - | `-` | 0 |
| [#5861](https://github.com/OpenXiangShan/XiangShan/issues/5861) | NewLoadUnit: `staNukeQueryReq` can miss second-half overlap of a cross16B non-cross-page store | DFPMTS | 2026-04-25 | - | `-` | 1 |
| [#5860](https://github.com/OpenXiangShan/XiangShan/pull/5860) | fix(csr, satp): fix the update logic of xepc and xtval | sinceforYy | 2026-04-25 | - | `-` | 0 |
| [#5858](https://github.com/OpenXiangShan/XiangShan/pull/5858) | fix(Bitmap): cfs indexed with wrong truncated PPN in L2TLB | yxtx1994 | 2026-04-24 | - | `-` | 0 |
| [#5855](https://github.com/OpenXiangShan/XiangShan/pull/5855) | fix(StoreQueue): fix `cross16B` handle of `storeQueue`  | weidingliu | 2026-04-23 | - | `-` | 0 |
| [#5854](https://github.com/OpenXiangShan/XiangShan/issues/5854) | [BUG] L2TLB: cfs indexed with wrong truncated PPN, defeats bitmap isolation | Hoshi44 | 2026-04-23 | - | `-` | 7 |
| [#5851](https://github.com/OpenXiangShan/XiangShan/issues/5851) | [BUG] Cross-page misaligned sd followed by alias lbu reads stale data | mmxsrup | 2026-04-23 | - | `-` | 3 |
| [#5850](https://github.com/OpenXiangShan/XiangShan/issues/5850) | [BUG] StoreQueue "double deq!" assertion triggered by two cross-page misaligned stores | mmxsrup | 2026-04-23 | - | `-` | 4 |
| [#5849](https://github.com/OpenXiangShan/XiangShan/issues/5849) | [BUG] StoreQueue "double deq!" assertion triggered by cross-16B stores interleaved with loads | mmxsrup | 2026-04-23 | - | `-` | 4 |
| [#5847](https://github.com/OpenXiangShan/XiangShan/issues/5847) | [BUG] StoreQueue "deqPtr > rdataPtr" assertion triggered by cross-16B misaligned store under sbuffer pressure | mmxsrup | 2026-04-22 | - | `-` | 4 |
| [#5846](https://github.com/OpenXiangShan/XiangShan/issues/5846) | NewStoreQueue:  `cross16BDeqReg` can clear between the two sbuffer writes of one cross16B store and over-advance `deqPtr` | DFPMTS | 2026-04-22 | - | `-` | 1 |
| [#5845](https://github.com/OpenXiangShan/XiangShan/issues/5845) | [Assertion Failure] XiangShan crashes in `LoadUnitS0` on a reduced `vmsbf.m -> vl1re64.v -> vlseg4e8.v` sequence | jimmymtest | 2026-04-22 | - | `-` | 6 |
| [#5843](https://github.com/OpenXiangShan/XiangShan/pull/5843) | timing(sc): move the computation of totalSum and sumAboveThre to s1 | sleep-zzz | 2026-04-22 | - | `-` | 0 |
| [#5840](https://github.com/OpenXiangShan/XiangShan/issues/5840) | [BUG] `vzext.vf8` can compute the wrong zero-extended result for an active destination element. | jimmymtest | 2026-04-22 | - | `-` | 8 |
| [#5835](https://github.com/OpenXiangShan/XiangShan/pull/5835) | timing(ftq): add some additional stages in FTQ | Yan-Muzi | 2026-04-20 | - | `-` | 0 |
| [#5833](https://github.com/OpenXiangShan/XiangShan/pull/5833) | fix(csr): fix indirect csr RegOut | sinceforYy | 2026-04-20 | - | `-` | 0 |
| [#5832](https://github.com/OpenXiangShan/XiangShan/issues/5832) | [BUG] `vl8re64.v` truncates upper 64-bit data in whole-register loads | jimmymtest | 2026-04-20 | - | `-` | 10 |
| [#5831](https://github.com/OpenXiangShan/XiangShan/issues/5831) | [BUG] `vlse32.v` corrupts packed 32-bit element data under SEW=64, LMUL=8 mixed-EEW execution | jimmymtest | 2026-04-20 | - | `-` | 7 |
| [#5830](https://github.com/OpenXiangShan/XiangShan/issues/5830) | [BUG] `vfmv.f.s` fails to NaN-box 32-bit values when writing to a 64-bit floating-point registe | jimmymtest | 2026-04-20 | - | `-` | 5 |
| [#5829](https://github.com/OpenXiangShan/XiangShan/issues/5829) | [Bug] vmv.x.s instruction fails to sign-extend when SEW < XLEN | jimmymtest | 2026-04-20 | - | `-` | 3 |
| [#5823](https://github.com/OpenXiangShan/XiangShan/pull/5823) | fix(csr): fix indirect csr RegOut | sinceforYy | 2026-04-17 | - | `-` | 0 |
| [#5814](https://github.com/OpenXiangShan/XiangShan/pull/5814) | fix(StoreQueue): fix OverlapMask for cross16B forward | weidingliu | 2026-04-14 | - | `-` | 0 |
| [#5809](https://github.com/OpenXiangShan/XiangShan/issues/5809) | `vmv.x.s` executes instead of raising illegal-instruction when `vsetvli` leaves `vtype.vill=1` | KnightGOKU | 2026-04-14 | - | `-` | 7 |
| [#5808](https://github.com/OpenXiangShan/XiangShan/issues/5808) | `vstart` mismatch after a minimal `vsse16.v` RVV testcase | KnightGOKU | 2026-04-14 | - | `-` | 5 |
| [#5807](https://github.com/OpenXiangShan/XiangShan/issues/5807) | [difftest] CSR mcause Exception Code Mismatch Between XiangShan RTL and NEMU When Exception Occurs | Lruos | 2026-04-14 | - | `-` | 5 |
| [#5803](https://github.com/OpenXiangShan/XiangShan/pull/5803) | feat(topdown): Resolve the false positive issue caused by insufficient main pipeline resources. | lewislzh | 2026-04-13 | - | `-` | 0 |
| [#5797](https://github.com/OpenXiangShan/XiangShan/pull/5797) | timing(bpu): fix bpu s3 timing | TheKiteRunner24 | 2026-04-10 | - | `-` | 0 |
| [#5795](https://github.com/OpenXiangShan/XiangShan/pull/5795) | feat(VirtualLoadQueue): add pointer exceed assert for debug | weidingliu | 2026-04-10 | - | `-` | 0 |
| [#5794](https://github.com/OpenXiangShan/XiangShan/issues/5794) | difftest store-commit mismatch triggered by two scalar stores to the same high-address region | KnightGOKU | 2026-04-09 | - | `-` | 2 |
| [#5792](https://github.com/OpenXiangShan/XiangShan/issues/5792) | NewLoadUnit: matchInvalid/vp_match_fail path incorrectly preserves replay cause and creates a replay entry   alongside rollback | maxiaoran24 | 2026-04-09 | - | `-` | 1 |
| [#5790](https://github.com/OpenXiangShan/XiangShan/issues/5790) | Mismatch mcause and mtval when executing vssseg3e16.v | zhangkanqi | 2026-04-08 | - | `-` | 4 |
| [#5787](https://github.com/OpenXiangShan/XiangShan/pull/5787) | fix(backend, ctrlblock): export empty state to ftq when backend drains | wissygh | 2026-04-08 | - | `-` | 0 |
| [#5783](https://github.com/OpenXiangShan/XiangShan/pull/5783) | fix(LoadUnit): fix unalignedHead replay stuck | weidingliu | 2026-04-08 | - | `-` | 0 |
| [#5780](https://github.com/OpenXiangShan/XiangShan/issues/5780) | `amominu.w` instruction behavior mismatch between Xiangshan and  NEMU | zhangkanqi | 2026-04-07 | - | `-` | 3 |
| [#5779](https://github.com/OpenXiangShan/XiangShan/issues/5779) | HFENCE.GVMA fails to ignore upper bits of rs2 (VMID) | Hoshi44 | 2026-04-07 | - | `-` | 4 |
| [#5777](https://github.com/OpenXiangShan/XiangShan/issues/5777) | No instructions have been submitted for a long time. Could this be a case of deadlock? | zhangkanqi | 2026-04-07 | - | `-` | 3 |
| [#5773](https://github.com/OpenXiangShan/XiangShan/issues/5773) | Incorrect exception type raised when flh accessing addr=0x1 | zhangkanqi | 2026-04-06 | - | `-` | 7 |
| [#5772](https://github.com/OpenXiangShan/XiangShan/issues/5772) | vmv4r.v with misaligned registers dosen't raise illegal instruction exception | zhangkanqi | 2026-04-06 | - | `-` | 4 |
| [#5770](https://github.com/OpenXiangShan/XiangShan/issues/5770) | Vector whole register load(vl2re32.v) partially updates destination on exception | zhangkanqi | 2026-04-06 | - | `-` | 5 |
| [#5769](https://github.com/OpenXiangShan/XiangShan/issues/5769) | Vector indexed segment store (vsuxseg*ei*) reports only base address in mtval on exception | zhangkanqi | 2026-04-06 | - | `-` | 6 |
| [#5768](https://github.com/OpenXiangShan/XiangShan/issues/5768) | Vector FP move/merge instructions missing `frm` reserved value check | Hoshi44 | 2026-04-05 | - | `-` | 1 |
| [#5767](https://github.com/OpenXiangShan/XiangShan/issues/5767) | `vlseg2e8ff.v` with later-element fault triggers XiangShan internal critical error | KnightGOKU | 2026-04-05 | - | `-` | 2 |
| [#5766](https://github.com/OpenXiangShan/XiangShan/issues/5766) | `vle8ff` fault-only-first followed by immediate `csrr vl` returns 0 on XiangShan, while Spike returns the expected `vl` | KnightGOKU | 2026-04-05 | - | `-` | 6 |
| [#5765](https://github.com/OpenXiangShan/XiangShan/issues/5765) | Difftest mismatch on tail bits of v0 after vlm.v | KnightGOKU | 2026-04-03 | - | `-` | 4 |
| [#5762](https://github.com/OpenXiangShan/XiangShan/pull/5762) | fix(TopDown): fix mis_pred and total_flush | sinceforYy | 2026-04-03 | - | `-` | 0 |
| [#5756](https://github.com/OpenXiangShan/XiangShan/pull/5756) | feat(sc): open global table and refactor Sc parameter | sleep-zzz | 2026-04-02 | - | `-` | 0 |
| [#5754](https://github.com/OpenXiangShan/XiangShan/pull/5754) | Fix debug 260401 | wissygh | 2026-04-01 | - | `-` | 0 |
| [#5751](https://github.com/OpenXiangShan/XiangShan/pull/5751) | fix(storeQueue): fix bug of `pbmt` & `hsv_*` access device region | weidingliu | 2026-03-31 | - | `-` | 0 |
| [#5748](https://github.com/OpenXiangShan/XiangShan/pull/5748) | fix(storeQueue): fix deqPtr move early | weidingliu | 2026-03-31 | - | `-` | 0 |
| [#5743](https://github.com/OpenXiangShan/XiangShan/pull/5743) | fix(Rename): fix psrcVl bypass to use pdestVl | sinceforYy | 2026-03-30 | - | `-` | 0 |
| [#5740](https://github.com/OpenXiangShan/XiangShan/pull/5740) | fix(frontend,perf): bump utility & use XSPerfSeqAccumulate | ngc7331 | 2026-03-30 | - | `-` | 0 |
| [#5739](https://github.com/OpenXiangShan/XiangShan/issues/5739) | `csrr vl` reads stale zero immediately after `vsetvli` | KnightGOKU | 2026-03-29 | - | `-` | 8 |
| [#5734](https://github.com/OpenXiangShan/XiangShan/pull/5734) | fix(bpu): fix the statistical error in counter train_stall | sleep-zzz | 2026-03-27 | - | `-` | 0 |
| [#5730](https://github.com/OpenXiangShan/XiangShan/pull/5730) | fix(debug): Hold dpc on critical-error debug reentry | wissygh | 2026-03-27 | - | `-` | 0 |
| [#5725](https://github.com/OpenXiangShan/XiangShan/issues/5725) | Behavior mismatch on RVV testcase (`vsetvl x0, x0, rs2` path) | KnightGOKU | 2026-03-25 | - | `-` | 9 |
| [#5724](https://github.com/OpenXiangShan/XiangShan/issues/5724) | 【Bug Report】Some CSRs is inaccessible incorrectly in Debug Mode | oChunCai | 2026-03-25 | - | `-` | 7 |
| [#5722](https://github.com/OpenXiangShan/XiangShan/pull/5722) | fix(CSR, Mcontrol6): fix chain of Mcontrol6/Tdata1 | wissygh | 2026-03-25 | - | `-` | 0 |
| [#5721](https://github.com/OpenXiangShan/XiangShan/issues/5721) | [Bug Report](Mcontrol6): Missing forward dmode check when writing mcontrol6.chain | oChunCai | 2026-03-24 | - | `-` | 4 |
| [#5720](https://github.com/OpenXiangShan/XiangShan/pull/5720) | fix(L1Prefetcher): use a separate control signal to RegEnable PC | good-circle | 2026-03-24 | - | `-` | 0 |
| [#5714](https://github.com/OpenXiangShan/XiangShan/issues/5714) | [bug report](Trigger): triggerActionGen uses index-based priority instead of action-type priority when multiple triggers fire simultaneously | oChunCai | 2026-03-23 | - | `-` | 0 |
| [#5713](https://github.com/OpenXiangShan/XiangShan/issues/5713) | [Bug Report](Mcontrol6): Missing forward dmode check when writing mcontrol6.chain | oChunCai | 2026-03-23 | - | `-` | 2 |
| [#5705](https://github.com/OpenXiangShan/XiangShan/pull/5705) | perf(rob): fix `commitInstrBranch` & add `branch_jump` perfCounter | wissygh | 2026-03-20 | - | `-` | 0 |
| [#5704](https://github.com/OpenXiangShan/XiangShan/pull/5704) | fix(IQ, entryBundle): remove datasources from `commonOutBundle` | wissygh | 2026-03-19 | - | `-` | 0 |
| [#5702](https://github.com/OpenXiangShan/XiangShan/issues/5702) | Vector memory access | biquanha | 2026-03-19 | - | `-` | 0 |
| [#5700](https://github.com/OpenXiangShan/XiangShan/pull/5700) | fix(LoadUnit): raise af for unalign access on MMIO region | linjuanZ | 2026-03-18 | - | `-` | 0 |
| [#5698](https://github.com/OpenXiangShan/XiangShan/pull/5698) | timing(StoreQueue): optimize timing path of StoreQueue | weidingliu | 2026-03-18 | - | `-` | 1 |
| [#5697](https://github.com/OpenXiangShan/XiangShan/pull/5697) | timing(MemBlock): optimize timing | linjuanZ | 2026-03-18 | - | `-` | 0 |
| [#5695](https://github.com/OpenXiangShan/XiangShan/issues/5695) | Misaligned accesses to non-idempotent regions raise AddrMisaligned instead of AccessFault | oChunCai | 2026-03-17 | - | `-` | 4 |
| [#5689](https://github.com/OpenXiangShan/XiangShan/issues/5689) | Illegal address access | YAM2020er | 2026-03-14 | - | `-` | 0 |
| [#5687](https://github.com/OpenXiangShan/XiangShan/pull/5687) | fix(IFU): do fetch if only the second cacheline has exception | ngc7331 | 2026-03-13 | - | `-` | 1 |
| [#5685](https://github.com/OpenXiangShan/XiangShan/pull/5685) | timing(rename): fix rename timing | sinceforYy | 2026-03-12 | - | `-` | 0 |
| [#5680](https://github.com/OpenXiangShan/XiangShan/pull/5680) | fix(uras): correct S1-level RAS stack top address during override | my-mayfly | 2026-03-11 | - | `-` | 0 |
| [#5677](https://github.com/OpenXiangShan/XiangShan/pull/5677) | fix(tage): fix tage select allocate way logic | TheKiteRunner24 | 2026-03-11 | - | `-` | 0 |
| [#5675](https://github.com/OpenXiangShan/XiangShan/pull/5675) | fix(vldMergeUnit): fix the data output of v0 in vldMergeUnit | sinceforYy | 2026-03-11 | - | `-` | 0 |
| [#5674](https://github.com/OpenXiangShan/XiangShan/pull/5674) | fix(StoreUnit): fix the revoke logic of misalignbuffer | Anzooooo | 2026-03-10 | - | `-` | 0 |
| [#5654](https://github.com/OpenXiangShan/XiangShan/issues/5654) | `ifuWbPtr` pointer not updated in FTQ | ambulGruidae | 2026-03-06 | - | `-` | 0 |
| [#5652](https://github.com/OpenXiangShan/XiangShan/pull/5652) | chore(backend): improve code quality | xiaofeibao-xjtu | 2026-03-05 | - | `-` | 0 |
| [#5648](https://github.com/OpenXiangShan/XiangShan/pull/5648) | timing(sc): fix the timing of sc in train  | sleep-zzz | 2026-03-04 | - | `-` | 0 |
| [#5644](https://github.com/OpenXiangShan/XiangShan/pull/5644) | timing(MMU): avoid adder when generate gpaddr | cebarobot | 2026-03-04 | - | `-` | 1 |
| [#5640](https://github.com/OpenXiangShan/XiangShan/pull/5640) | fix(mmio): store mmio will also mark the rob | Anzooooo | 2026-03-04 | - | `-` | 0 |
| [#5638](https://github.com/OpenXiangShan/XiangShan/pull/5638) | fix(mbtb): fix addrField (cfi)Position width | ngc7331 | 2026-03-03 | - | `-` | 1 |
| [#5637](https://github.com/OpenXiangShan/XiangShan/pull/5637) | chore(Rat): move RatWrapper to Rename to check Rename timing | sinceforYy | 2026-03-03 | - | `-` | 0 |
| [#5636](https://github.com/OpenXiangShan/XiangShan/pull/5636) | timing(intRegion): reduce bju IssueQueue's size, fix IssueQueue's ready timing, fix timing of interrupt selection | xiaofeibao-xjtu | 2026-03-03 | - | `-` | 0 |
| [#5630](https://github.com/OpenXiangShan/XiangShan/pull/5630) | fix(uncache): fix forwarding order hazard when `mem_acquire` is not fired | Maxpicca-Li | 2026-02-28 | - | `-` | 0 |
| [#5628](https://github.com/OpenXiangShan/XiangShan/issues/5628) | VU direct access to VS CSR reports EX_VI, expected EX_II (illegal-instruction) | oChunCai | 2026-02-27 | - | `-` | 0 |
| [#5625](https://github.com/OpenXiangShan/XiangShan/pull/5625) | fix(commonHR): commonHR restored using queue implementation in s3Override | sleep-zzz | 2026-02-26 | - | `-` | 0 |
| [#5614](https://github.com/OpenXiangShan/XiangShan/pull/5614) | timing(bpu): fix bpu s2 timing | TheKiteRunner24 | 2026-02-11 | - | `-` | 0 |
| [#5611](https://github.com/OpenXiangShan/XiangShan/pull/5611) | fix(bpu): fix s1 selection logic | TheKiteRunner24 | 2026-02-09 | - | `-` | 0 |
| [#5608](https://github.com/OpenXiangShan/XiangShan/issues/5608) | Compilation failed on the nanhu branch | quange51 | 2026-02-06 | - | `-` | 1 |
| [#5603](https://github.com/OpenXiangShan/XiangShan/pull/5603) | timing(bpu): move position comparation to s2 | Erlkonigal | 2026-02-05 | - | `-` | 0 |
| [#5602](https://github.com/OpenXiangShan/XiangShan/pull/5602) | fix(mbtb): train counter in all align banks | Erlkonigal | 2026-02-05 | - | `-` | 0 |
| [#5601](https://github.com/OpenXiangShan/XiangShan/pull/5601) | fix(tage,sc): add mbtb hit cond to avoid sc train error | out-of-order55 | 2026-02-05 | - | `-` | 0 |
| [#5600](https://github.com/OpenXiangShan/XiangShan/issues/5600) | Too long Sbuffer's EvictCycles leading to poor spinlock performance | cyyself | 2026-02-04 | - | `-` | 3 |
| [#5583](https://github.com/OpenXiangShan/XiangShan/pull/5583) | fix(redirect): flushpipe shouldn't assert `redirect.interrupt` | wissygh | 2026-01-29 | - | `-` | 0 |
| [#5576](https://github.com/OpenXiangShan/XiangShan/pull/5576) | feat(MDP): support MDP of StoreSet | weidingliu | 2026-01-28 | - | `-` | 0 |
| [#5568](https://github.com/OpenXiangShan/XiangShan/pull/5568) | feat(pmu): enhance misprediction analysis with position comparison | my-mayfly | 2026-01-24 | - | `-` | 0 |
| [#5565](https://github.com/OpenXiangShan/XiangShan/issues/5565) | [Bug]IntRegCache perf history write index can exceed `Vec(48)` bounds (pointer wraps at 64) | oChunCai | 2026-01-23 | - | `-` | 1 |
| [#5551](https://github.com/OpenXiangShan/XiangShan/pull/5551) | fix(SaturateCounter): fix signed isWeakPositive | ngc7331 | 2026-01-21 | - | `-` | 0 |
| [#5548](https://github.com/OpenXiangShan/XiangShan/pull/5548) | refactor: new LoadUnit and new StoreQueue | linjuanZ | 2026-01-20 | - | `-` | 0 |
| [#5545](https://github.com/OpenXiangShan/XiangShan/pull/5545) | fix(SaturateCounter): disallow signed step | ngc7331 | 2026-01-20 | - | `-` | 2 |
| [#5544](https://github.com/OpenXiangShan/XiangShan/pull/5544) | perf(pf): optimize l1 prefetcher training and for training | Maxpicca-Li | 2026-01-20 | - | `-` | 0 |
| [#5543](https://github.com/OpenXiangShan/XiangShan/pull/5543) | refactor(mbtb): eliminate mbtb trace warning | out-of-order55 | 2026-01-20 | - | `-` | 1 |
| [#5540](https://github.com/OpenXiangShan/XiangShan/pull/5540) | fix(mbtb): fix typo in trainTouchWay | ngc7331 | 2026-01-15 | - | `-` | 0 |
| [#5538](https://github.com/OpenXiangShan/XiangShan/pull/5538) | fix(Redirect): fix redirect and Topdown | sinceforYy | 2026-01-15 | - | `-` | 0 |
| [#5536](https://github.com/OpenXiangShan/XiangShan/pull/5536) | fix(pmu): adjust condition logic for specific performance counters | my-mayfly | 2026-01-15 | - | `-` | 0 |
| [#5526](https://github.com/OpenXiangShan/XiangShan/pull/5526) | fix(WriteBuffer): fix victim selection logic when setIdx matches | sleep-zzz | 2026-01-13 | - | `-` | 0 |
| [#5517](https://github.com/OpenXiangShan/XiangShan/pull/5517) | timing(utage): train and predict using the history from the previous cycle | my-mayfly | 2026-01-12 | - | `-` | 0 |
| [#5512](https://github.com/OpenXiangShan/XiangShan/pull/5512) | fix(LSQ): connect exception buffer enq.{sq/lq}Idx | Anzooooo | 2026-01-11 | - | `-` | 0 |
| [#5502](https://github.com/OpenXiangShan/XiangShan/issues/5502) | Error when run command such as "make verilog CONFIG=XSNoCTopConfig", "make verilog CONFIG=KunminghuV2Config" | WHR-oss | 2026-01-08 | - | `-` | 0 |
| [#5490](https://github.com/OpenXiangShan/XiangShan/issues/5490) | Error when run "make verilog CONFIG=KunminghuV2MinimalConfig" | WHR-oss | 2026-01-06 | - | `-` | 1 |
| [#5481](https://github.com/OpenXiangShan/XiangShan/pull/5481) | fix(ras): prevent incorrect RAS pointer updates under FTQ backpressure | my-mayfly | 2026-01-05 | - | `-` | 0 |
| [#5475](https://github.com/OpenXiangShan/XiangShan/pull/5475) | timing(CSR, Redirect): split targetPc into trap and xret paths | wissygh | 2026-01-04 | - | `-` | 0 |
| [#5469](https://github.com/OpenXiangShan/XiangShan/pull/5469) | fix(ghr): fix ghr losing updates when !s0_fire | sleep-zzz | 2025-12-31 | - | `-` | 0 |
| [#5465](https://github.com/OpenXiangShan/XiangShan/pull/5465) | fix(Backend, Region): use `basicDebugEn` for `diffVl` debug IO | wissygh | 2025-12-30 | - | `-` | 0 |
| [#5462](https://github.com/OpenXiangShan/XiangShan/pull/5462) | timing(CtrlBlock, Redirect): Move selection of oldestExuRedirect from ctrlblock to intRegion | wissygh | 2025-12-30 | - | `-` | 0 |
| [#5461](https://github.com/OpenXiangShan/XiangShan/pull/5461) | fix(tage): fix tage use meta condition | TheKiteRunner24 | 2025-12-30 | - | `-` | 0 |
| [#5459](https://github.com/OpenXiangShan/XiangShan/pull/5459) | fix(ifu): enable MMIO blocking and fix uncache exception assert | my-mayfly | 2025-12-30 | - | `-` | 0 |
| [#5450](https://github.com/OpenXiangShan/XiangShan/issues/5450) | Error when run “make verilog CONFIG=FpgaDefaultConfig” | WHR-oss | 2025-12-27 | - | `-` | 0 |
| [#5449](https://github.com/OpenXiangShan/XiangShan/issues/5449) | VectorFloatFMA module operation alignment logic error | I3eg1nner | 2025-12-26 | - | `-` | 1 |
| [#5441](https://github.com/OpenXiangShan/XiangShan/pull/5441) | chore(NewCSR): fix RegNext error usage | sinceforYy | 2025-12-26 | - | `-` | 0 |
| [#5440](https://github.com/OpenXiangShan/XiangShan/pull/5440) | fix(CSR): add CSRs.scala to keep track of CSR addresses | wissygh | 2025-12-26 | - | `-` | 0 |
| [#5434](https://github.com/OpenXiangShan/XiangShan/pull/5434) | submodule(ready-to-run): bump nemu to fix vfredusum | lewislzh | 2025-12-25 | - | `-` | 0 |
| [#5433](https://github.com/OpenXiangShan/XiangShan/pull/5433) | timing(tage): delay write one cycle for better timing | TheKiteRunner24 | 2025-12-25 | - | `-` | 0 |
| [#5427](https://github.com/OpenXiangShan/XiangShan/pull/5427) | Cherry-pick mvendorid from master | wissygh | 2025-12-25 | - | `-` | 0 |
| [#5426](https://github.com/OpenXiangShan/XiangShan/issues/5426) | v31_low different on vfredusum.vs | cesarus777 | 2025-12-24 | - | `-` | 3 |
| [#5425](https://github.com/OpenXiangShan/XiangShan/issues/5425) | Assertion failed at DCache on vector indexed segment store instructions | cesarus777 | 2025-12-24 | - | `-` | 5 |
| [#5422](https://github.com/OpenXiangShan/XiangShan/pull/5422) | fix(decode): fix priority for CSR read vl/vlenb causing EX_II | sinceforYy | 2025-12-24 | - | `-` | 0 |
| [#5421](https://github.com/OpenXiangShan/XiangShan/pull/5421) | fix(CtrlBlock): fix `rasAction` when commit | wissygh | 2025-12-24 | - | `-` | 0 |
| [#5420](https://github.com/OpenXiangShan/XiangShan/pull/5420) | fix(decode): fix priority for CSR read vl/vlenb causing EX_II | sinceforYy | 2025-12-24 | - | `-` | 0 |
| [#5418](https://github.com/OpenXiangShan/XiangShan/pull/5418) | timing(mbtb): latch write req before it goes into writebuffer | ngc7331 | 2025-12-23 | - | `-` | 0 |
| [#5417](https://github.com/OpenXiangShan/XiangShan/pull/5417) | timing(abtb): change abtb sram to 32x112 for better timing | out-of-order55 | 2025-12-23 | - | `-` | 0 |
| [#5415](https://github.com/OpenXiangShan/XiangShan/pull/5415) | fix(StoreUnit): fix multi-writeback when storeMisalignBuffer full | weidingliu | 2025-12-22 | - | `-` | 0 |
| [#5413](https://github.com/OpenXiangShan/XiangShan/pull/5413) | chore(backend): remove some connection with 0.U | xiaofeibao-xjtu | 2025-12-22 | - | `-` | 0 |
| [#5405](https://github.com/OpenXiangShan/XiangShan/pull/5405) | chore(backend): remove dead code | huxuan0307 | 2025-12-20 | - | `-` | 0 |
| [#5399](https://github.com/OpenXiangShan/XiangShan/pull/5399) | fix(bpu): fix decoupled train | ngc7331 | 2025-12-19 | - | `-` | 0 |
| [#5398](https://github.com/OpenXiangShan/XiangShan/pull/5398) | fix(perfCount): fix dispatch stall cycle | xiaofeibao-xjtu | 2025-12-19 | - | `-` | 0 |
| [#5395](https://github.com/OpenXiangShan/XiangShan/pull/5395) | refactor(Frontend): follow new style guide & fix IDE warnings | ngc7331 | 2025-12-19 | - | `-` | 0 |
| [#5392](https://github.com/OpenXiangShan/XiangShan/pull/5392) | fix(ittage): fix condition of IttageTable readWriteConflict | rich-cake | 2025-12-18 | - | `-` | 0 |
| [#5383](https://github.com/OpenXiangShan/XiangShan/pull/5383) | fix(mbtb,tage): fix basetable drop write counter typo | ngc7331 | 2025-12-17 | - | `-` | 0 |
| [#5378](https://github.com/OpenXiangShan/XiangShan/pull/5378) | fix(CtrlBlock, Redirect): reduce 1 cycle for redirect | wissygh | 2025-12-16 | - | `-` | 0 |
| [#5372](https://github.com/OpenXiangShan/XiangShan/pull/5372) | feat(pmu): add correct-path branch mispredict statistics. | eastonman | 2025-12-15 | - | `-` | 0 |
| [#5370](https://github.com/OpenXiangShan/XiangShan/pull/5370) | fix(pmu): fix s3Override_takenMismatch & ITTAGEMissBubble | rich-cake | 2025-12-15 | - | `-` | 0 |
| [#5368](https://github.com/OpenXiangShan/XiangShan/pull/5368) | refactor(backend): separate vl src config in every params class of Backend | huxuan0307 | 2025-12-15 | - | `-` | 0 |
| [#5367](https://github.com/OpenXiangShan/XiangShan/pull/5367) | fix(CSR, Mvendorid): Modify the value of `mvendorid` | wissygh | 2025-12-15 | - | `-` | 0 |
| [#5365](https://github.com/OpenXiangShan/XiangShan/pull/5365) | fix(L1StreamPrefetcher): change L1 and L2 depth | happy-lx | 2025-12-15 | - | `-` | 0 |
| [#5353](https://github.com/OpenXiangShan/XiangShan/pull/5353) | fix(pmu): enable ras pmu in bpu | out-of-order55 | 2025-12-11 | - | `-` | 0 |
| [#5352](https://github.com/OpenXiangShan/XiangShan/pull/5352) | fix(LoadQueueRAW): `storeIn.wlineflag` needs a one-cycle delay | Anzooooo | 2025-12-11 | - | `-` | 0 |
| [#5347](https://github.com/OpenXiangShan/XiangShan/pull/5347) | feat(perf): add perfQueue and collect perf statistics when commit | Yan-Muzi | 2025-12-10 | - | `-` | 0 |
| [#5345](https://github.com/OpenXiangShan/XiangShan/pull/5345) | fix(tage): fix cfiPc typo | TheKiteRunner24 | 2025-12-10 | - | `-` | 0 |
| [#5344](https://github.com/OpenXiangShan/XiangShan/pull/5344) | fix(resolve): bpu enqueue flush should not consider flag | Yan-Muzi | 2025-12-10 | - | `-` | 0 |
| [#5342](https://github.com/OpenXiangShan/XiangShan/pull/5342) | misc: update backend code-owners | lewislzh | 2025-12-10 | - | `-` | 0 |
| [#5340](https://github.com/OpenXiangShan/XiangShan/pull/5340) | fix(pmu): fix connection of TopDown interface in backend | sinceforYy | 2025-12-09 | - | `-` | 0 |
| [#5339](https://github.com/OpenXiangShan/XiangShan/pull/5339) | fix(pmu): fix topdown counters wiring | eastonman | 2025-12-09 | - | `-` | 0 |
| [#5332](https://github.com/OpenXiangShan/XiangShan/pull/5332) | fix(pmu): update bpu s1 prediction source | Erlkonigal | 2025-12-09 | - | `-` | 0 |
| [#5327](https://github.com/OpenXiangShan/XiangShan/pull/5327) | fix(LoadQueueReplay): fix dcache miss block | weidingliu | 2025-12-08 | - | `-` | 0 |
| [#5326](https://github.com/OpenXiangShan/XiangShan/pull/5326) | fix(ubtb): fix ubtb t0_hitT1Update condition | out-of-order55 | 2025-12-07 | - | `-` | 0 |
| [#5325](https://github.com/OpenXiangShan/XiangShan/pull/5325) | fix(tage): fix width typo for TageFoldedHist.forIdx | ngc7331 | 2025-12-07 | - | `-` | 0 |
| [#5324](https://github.com/OpenXiangShan/XiangShan/pull/5324) | chore(backend): remove some dead code | Squareless-XD | 2025-12-07 | - | `-` | 0 |
| [#5321](https://github.com/OpenXiangShan/XiangShan/pull/5321) | fix(ras): correct speculative pushAddr and enable return stack | my-mayfly | 2025-12-05 | - | `-` | 0 |
| [#5320](https://github.com/OpenXiangShan/XiangShan/pull/5320) | feat(phr): add the diff cnt btw predFoldedHist and trainFoldedHist | sleep-zzz | 2025-12-05 | - | `-` | 0 |
| [#5319](https://github.com/OpenXiangShan/XiangShan/pull/5319) | feat(mbtb): add write reason counter | ngc7331 | 2025-12-05 | - | `-` | 0 |
| [#5317](https://github.com/OpenXiangShan/XiangShan/pull/5317) | fix(frontend): bpu redirect need cfiPc instead of startPc | ngc7331 | 2025-12-05 | - | `-` | 0 |
| [#5306](https://github.com/OpenXiangShan/XiangShan/pull/5306) | feat(tage): use AddrFields & allow different NumWay for tables | ngc7331 | 2025-12-03 | - | `-` | 1 |
| [#5302](https://github.com/OpenXiangShan/XiangShan/pull/5302) | fix(mbtb): encode train mask to one-hot & pass it to replacer | ngc7331 | 2025-12-03 | - | `-` | 0 |
| [#5295](https://github.com/OpenXiangShan/XiangShan/pull/5295) | feat(AddrField): add extract methods | ngc7331 | 2025-12-02 | - | `-` | 2 |
| [#5291](https://github.com/OpenXiangShan/XiangShan/pull/5291) | timing(BypassNetwork): remove clock gate of bypass2DataVec | xiaofeibao-xjtu | 2025-12-01 | - | `-` | 0 |
| [#5288](https://github.com/OpenXiangShan/XiangShan/issues/5288) | Unexpected interaction between vs1r.v and fence.i instructions | youzi27 | 2025-12-01 | - | `-` | 4 |
| [#5282](https://github.com/OpenXiangShan/XiangShan/issues/5282) | Incorrect mtval in both reference models for specific illegal-instruction sequences | youzi27 | 2025-11-30 | - | `-` | 4 |
| [#5279](https://github.com/OpenXiangShan/XiangShan/issues/5279) | Mismatch in vector store commit behavior under misaligned base address | youzi27 | 2025-11-29 | - | `-` | 5 |
| [#5276](https://github.com/OpenXiangShan/XiangShan/pull/5276) | fix(frontend): add `suffix` param to SRAMTemplate to prevent warning | ngc7331 | 2025-11-28 | - | `-` | 0 |
| [#5274](https://github.com/OpenXiangShan/XiangShan/pull/5274) | feat(log): add AddrField util to print address fields | ngc7331 | 2025-11-28 | - | `-` | 1 |
| [#5273](https://github.com/OpenXiangShan/XiangShan/pull/5273) | fix(resolve): condition of bpu enq overrides that of backend redirect | Yan-Muzi | 2025-11-28 | - | `-` | 0 |
| [#5272](https://github.com/OpenXiangShan/XiangShan/pull/5272) | refactor(prefetchmonitor):remove fdpmonitor and fix some statistical bugs | ywlcode | 2025-11-28 | - | `-` | 0 |
| [#5271](https://github.com/OpenXiangShan/XiangShan/pull/5271) | fix(bpu,ftq): s3_s1PredictionSource use s2_fire instead of s1_fire | Erlkonigal | 2025-11-28 | - | `-` | 0 |
| [#5270](https://github.com/OpenXiangShan/XiangShan/pull/5270) | fix(MemBlock): adjust the priority of misalign exception | Anzooooo | 2025-11-27 | - | `-` | 0 |
| [#5269](https://github.com/OpenXiangShan/XiangShan/pull/5269) | fix(MemBlock): adjust the logic where tilelink error generate exception | Anzooooo | 2025-11-27 | - | `-` | 0 |
| [#5266](https://github.com/OpenXiangShan/XiangShan/pull/5266) | fix(abtb): fix taken ctr update logic | TheKiteRunner24 | 2025-11-26 | - | `-` | 0 |
| [#5265](https://github.com/OpenXiangShan/XiangShan/pull/5265) | feat(tage): add more perf counters | eastonman | 2025-11-26 | - | `-` | 0 |
| [#5259](https://github.com/OpenXiangShan/XiangShan/pull/5259) | fix(wakeup): fix bug of csr wakeup | xiaofeibao-xjtu | 2025-11-26 | - | `-` | 0 |
| [#5257](https://github.com/OpenXiangShan/XiangShan/issues/5257) | Incorrect Instruction Page Fault When Executing From a Valid Sv39-Mapped Supervisor Page | BoA5li | 2025-11-25 | - | `-` | 1 |
| [#5255](https://github.com/OpenXiangShan/XiangShan/pull/5255) | fix(mbtb): fix internalBank flush and write conflict | sleep-zzz | 2025-11-25 | - | `-` | 0 |
| [#5254](https://github.com/OpenXiangShan/XiangShan/pull/5254) | fix(tage): fix tage allocate logic | TheKiteRunner24 | 2025-11-25 | - | `-` | 0 |
| [#5252](https://github.com/OpenXiangShan/XiangShan/pull/5252) | fix(tage): fix new entry taken ctr init value | TheKiteRunner24 | 2025-11-25 | - | `-` | 0 |
| [#5251](https://github.com/OpenXiangShan/XiangShan/pull/5251) | fix(tage): correct wrong parameter usage in condTrace signal | my-mayfly | 2025-11-25 | - | `-` | 0 |
| [#5248](https://github.com/OpenXiangShan/XiangShan/issues/5248) | CSR exception behavior differs | canxin121 | 2025-11-24 | - | `-` | 1 |
| [#5244](https://github.com/OpenXiangShan/XiangShan/pull/5244) | fix(ittage): delay startVAddr & phr from io.train for update | rich-cake | 2025-11-21 | - | `-` | 0 |
| [#5242](https://github.com/OpenXiangShan/XiangShan/pull/5242) | timing(ICache,Ifu): remove sameCycle=true in pmp | ngc7331 | 2025-11-21 | - | `-` | 0 |
| [#5238](https://github.com/OpenXiangShan/XiangShan/pull/5238) | fix(resolve): flush resolve queue with wrong backend pointer | Yan-Muzi | 2025-11-20 | - | `-` | 0 |
| [#5233](https://github.com/OpenXiangShan/XiangShan/pull/5233) | fix(Unalign, Store): fix corner case of unalign store | weidingliu | 2025-11-19 | - | `-` | 0 |
| [#5232](https://github.com/OpenXiangShan/XiangShan/pull/5232) | refactor(ICache): refactoring to prepare for cleaner 2-fetch | ngc7331 | 2025-11-19 | - | `-` | 0 |
| [#5228](https://github.com/OpenXiangShan/XiangShan/pull/5228) | fix(DCache): fix timing mismatch of corrupt in DCache forwarding | linjuanZ | 2025-11-18 | - | `-` | 0 |
| [#5225](https://github.com/OpenXiangShan/XiangShan/pull/5225) | fix(resolve): drop entries that has been overwritten by BP | Yan-Muzi | 2025-11-18 | - | `-` | 0 |
| [#5215](https://github.com/OpenXiangShan/XiangShan/pull/5215) | fix(CSR, NMI): fix the logic for gating `nmi` | sinceforYy | 2025-11-13 | - | `-` | 0 |
| [#5201](https://github.com/OpenXiangShan/XiangShan/pull/5201) | fix(resolve): width of `bpTrainStallCnt` | Yan-Muzi | 2025-11-12 | - | `-` | 0 |
| [#5197](https://github.com/OpenXiangShan/XiangShan/pull/5197) | fix(abtb): hold abtb output when bpu s1 not fire | TheKiteRunner24 | 2025-11-10 | - | `-` | 0 |
| [#5189](https://github.com/OpenXiangShan/XiangShan/pull/5189) | fix(TLB): gpaddr should be same to vaddr when onlyS2 | cebarobot | 2025-11-07 | - | `-` | 0 |
| [#5185](https://github.com/OpenXiangShan/XiangShan/pull/5185) | timing(LRQ, DCache): optimize timing | jin120811 | 2025-11-07 | - | `-` | 0 |
| [#5184](https://github.com/OpenXiangShan/XiangShan/pull/5184) | fix(ittage): select lowest-index branch when multiple-train to avoid assertion | rich-cake | 2025-11-06 | - | `-` | 0 |
| [#5181](https://github.com/OpenXiangShan/XiangShan/pull/5181) | fix(mbtb): fix the error of multi-hit flush more than 1 | sleep-zzz | 2025-11-06 | - | `-` | 0 |
| [#5170](https://github.com/OpenXiangShan/XiangShan/pull/5170) | PTW timing (Should be rebase & merge) | good-circle | 2025-11-04 | - | `-` | 0 |
| [#5168](https://github.com/OpenXiangShan/XiangShan/issues/5168) | NEMU and Xiangshan generate unmatched MIE value. | chenjie35335 | 2025-11-03 | - | `-` | 1 |
| [#5167](https://github.com/OpenXiangShan/XiangShan/pull/5167) | refactor(MemBlock): adjust the interface for issue and writeback | linjuanZ | 2025-11-03 | - | `-` | 0 |
| [#5164](https://github.com/OpenXiangShan/XiangShan/pull/5164) | fix(VsegmentUnit): fix latch of paddr when element is unalign | weidingliu | 2025-11-03 | - | `-` | 0 |
| [#5162](https://github.com/OpenXiangShan/XiangShan/pull/5162) | refactor(tage): add BaseTableAlignBank wrapper | ngc7331 | 2025-10-31 | - | `-` | 0 |
| [#5160](https://github.com/OpenXiangShan/XiangShan/pull/5160) | fix(abtb): fix condition of writing new entry to abtb | rich-cake | 2025-10-31 | - | `-` | 0 |
| [#5159](https://github.com/OpenXiangShan/XiangShan/pull/5159) | refactor(mbtb): re-structure code | ngc7331 | 2025-10-30 | - | `-` | 0 |
| [#5156](https://github.com/OpenXiangShan/XiangShan/pull/5156) | fix(tage): fix tage performance | TheKiteRunner24 | 2025-10-30 | - | `-` | 0 |
| [#5155](https://github.com/OpenXiangShan/XiangShan/pull/5155) | fix(Tage,mbtb): fix next set idx logic | TheKiteRunner24 | 2025-10-30 | - | `-` | 0 |
| [#5153](https://github.com/OpenXiangShan/XiangShan/pull/5153) | fix(abtb): hold sram read to prevent x-state | ngc7331 | 2025-10-29 | - | `-` | 0 |
| [#5149](https://github.com/OpenXiangShan/XiangShan/pull/5149) | fix(resolve): flush branches that are not flushed by backend redirect | Yan-Muzi | 2025-10-29 | - | `-` | 0 |
| [#5147](https://github.com/OpenXiangShan/XiangShan/pull/5147) | fix(ibuffer): do not store first encountered exception when currentLastHalfRvi | Erlkonigal | 2025-10-28 | - | `-` | 0 |
| [#5143](https://github.com/OpenXiangShan/XiangShan/pull/5143) | fix(writeBuffer): fix writePortValid not assigned correctly error | sleep-zzz | 2025-10-27 | - | `-` | 0 |
| [#5139](https://github.com/OpenXiangShan/XiangShan/pull/5139) | fix(phr): fix phrPtr meta error | sleep-zzz | 2025-10-24 | - | `-` | 0 |
| [#5137](https://github.com/OpenXiangShan/XiangShan/issues/5137) | BUG when load word from illegal address | E1thannn | 2025-10-22 | - | `-` | 3 |
| [#5135](https://github.com/OpenXiangShan/XiangShan/pull/5135) | chore(backend): improve code quality | xiaofeibao-xjtu | 2025-10-22 | - | `-` | 0 |
| [#5134](https://github.com/OpenXiangShan/XiangShan/pull/5134) | fix(WriteBuffer): fix writebuffer writeTouchVec idx usage error | sleep-zzz | 2025-10-22 | - | `-` | 0 |
| [#5132](https://github.com/OpenXiangShan/XiangShan/pull/5132) | fix(ras): fix rasPtr width based on wrong stack size | my-mayfly | 2025-10-21 | - | `-` | 0 |
| [#5131](https://github.com/OpenXiangShan/XiangShan/pull/5131) | fix(csr): fix csr out-of-order read xip registers | sinceforYy | 2025-10-20 | - | `-` | 0 |
| [#5129](https://github.com/OpenXiangShan/XiangShan/issues/5129) | Difference between NEMU, XiangshanCore and SPIKE | MrCookieeeee | 2025-10-20 | - | `-` | 4 |
| [#5122](https://github.com/OpenXiangShan/XiangShan/pull/5122) | fix(ifu): fix fetch size assertion not accounting for flush scenario. | my-mayfly | 2025-10-16 | - | `-` | 0 |
| [#5118](https://github.com/OpenXiangShan/XiangShan/pull/5118) | fix(sc): fix sc update logic error | sleep-zzz | 2025-10-15 | - | `-` | 0 |
| [#5114](https://github.com/OpenXiangShan/XiangShan/pull/5114) | fix(L1TLB): ignore addr when hfence.vvma or sfence.vma when v=1 | cebarobot | 2025-10-14 | - | `-` | 1 |
| [#5113](https://github.com/OpenXiangShan/XiangShan/pull/5113) | fix(mbtb): do not filter branches with position equal to start | ngc7331 | 2025-10-14 | - | `-` | 2 |
| [#5109](https://github.com/OpenXiangShan/XiangShan/issues/5109) | The NEMU and Xiangshan reports different exception when executing a illegal instruction | chenjie35335 | 2025-10-14 | - | `-` | 1 |
| [#5107](https://github.com/OpenXiangShan/XiangShan/pull/5107) | fix(resolve queue): only valid entries can be flushed | Yan-Muzi | 2025-10-13 | - | `-` | 0 |
| [#5104](https://github.com/OpenXiangShan/XiangShan/pull/5104) | fix(resolve queue): flushed entries should remain flushed | Yan-Muzi | 2025-10-11 | - | `-` | 0 |
| [#5103](https://github.com/OpenXiangShan/XiangShan/pull/5103) | feat(ifu): distinguish between fixedTaken and predTaken for BPU training | my-mayfly | 2025-10-11 | - | `-` | 0 |
| [#5102](https://github.com/OpenXiangShan/XiangShan/issues/5102) | [BUG] bug in local interrupt behaviour | bantierr | 2025-10-10 | - | `-` | 2 |
| [#5096](https://github.com/OpenXiangShan/XiangShan/pull/5096) | fix(mbtb): fix typos and fix hitMask position comparison | TheKiteRunner24 | 2025-10-09 | - | `-` | 0 |
| [#5092](https://github.com/OpenXiangShan/XiangShan/pull/5092) | fix(resolve): enqueue branch slot index | Yan-Muzi | 2025-10-02 | - | `-` | 0 |
| [#5090](https://github.com/OpenXiangShan/XiangShan/pull/5090) | fix(tage): fix typos, BaseTable use Queue and store meta to Ftq | TheKiteRunner24 | 2025-09-30 | - | `-` | 0 |
| [#5087](https://github.com/OpenXiangShan/XiangShan/pull/5087) | fix(TLB): fix incorrect TLB level refill when has exception | good-circle | 2025-09-29 | - | `-` | 0 |
| [#5086](https://github.com/OpenXiangShan/XiangShan/pull/5086) | fix(tage): fix providerIdxOH | TheKiteRunner24 | 2025-09-29 | - | `-` | 0 |
| [#5085](https://github.com/OpenXiangShan/XiangShan/pull/5085) | fix(resolve): flush entries that have been redirected by backend | Yan-Muzi | 2025-09-29 | - | `-` | 0 |
| [#5082](https://github.com/OpenXiangShan/XiangShan/pull/5082) | fix(ICache): stall read when updating | ngc7331 | 2025-09-28 | - | `-` | 0 |
| [#5079](https://github.com/OpenXiangShan/XiangShan/pull/5079) | fix(Closecompress): when rob compress close, fusion which cross two ftq should be cancompressed | lewislzh | 2025-09-28 | - | `-` | 0 |
| [#5074](https://github.com/OpenXiangShan/XiangShan/pull/5074) | fix(Closecompress): when rob compress close, the brh instruction compress bit cannot be true | lewislzh | 2025-09-26 | - | `-` | 0 |
| [#5073](https://github.com/OpenXiangShan/XiangShan/pull/5073) | fix(Bitmap): fix bitmap check result wakeup `l0BitmapReg` logic | yxtx1994 | 2025-09-26 | - | `-` | 0 |
| [#5072](https://github.com/OpenXiangShan/XiangShan/pull/5072) | fix(ICache,Ifu): do not bpuFlush if not valid | ngc7331 | 2025-09-25 | - | `-` | 0 |
| [#5067](https://github.com/OpenXiangShan/XiangShan/pull/5067) | fix(CSR, NMI): fix the logic for gating `nmi` | sinceforYy | 2025-09-25 | - | `-` | 0 |
| [#5060](https://github.com/OpenXiangShan/XiangShan/pull/5060) | fix(mbtb): filter out cross-page branches | TheKiteRunner24 | 2025-09-23 | - | `-` | 0 |
| [#5059](https://github.com/OpenXiangShan/XiangShan/pull/5059) | fix(loadTrigger): the prefetch instructions don’t match trigger at all | wissygh | 2025-09-23 | - | `-` | 0 |
| [#5058](https://github.com/OpenXiangShan/XiangShan/pull/5058) | fix(fallthrough): fix cfipostion when cross page | ngc7331 | 2025-09-23 | - | `-` | 0 |
| [#5055](https://github.com/OpenXiangShan/XiangShan/pull/5055) | fix(ICache): bpu s3 flush waylookup & mainPipe s1 | ngc7331 | 2025-09-22 | - | `-` | 0 |
| [#5054](https://github.com/OpenXiangShan/XiangShan/pull/5054) | fix(ifu): fix s1 flush condition | TheKiteRunner24 | 2025-09-22 | - | `-` | 0 |
| [#5043](https://github.com/OpenXiangShan/XiangShan/pull/5043) | fix(tage): fix x-state issue | TheKiteRunner24 | 2025-09-19 | - | `-` | 0 |
| [#5040](https://github.com/OpenXiangShan/XiangShan/pull/5040) | feat(ifu): connect IFU redirect with the return stack | my-mayfly | 2025-09-18 | - | `-` | 0 |
| [#5035](https://github.com/OpenXiangShan/XiangShan/pull/5035) | fix(ftq): relax the gating of backendException. | my-mayfly | 2025-09-17 | - | `-` | 0 |
| [#5030](https://github.com/OpenXiangShan/XiangShan/pull/5030) | fix(prefetch): size of counter filter needs to add 1 | Maxpicca-Li | 2025-09-16 | - | `-` | 0 |
| [#5028](https://github.com/OpenXiangShan/XiangShan/pull/5028) | fix(abtb): fix abtb meta signal X-Propagation | sleep-zzz | 2025-09-16 | - | `-` | 0 |
| [#5027](https://github.com/OpenXiangShan/XiangShan/pull/5027) | fix(branchUnit): check target when predict and real are all taken | xiaofeibao-xjtu | 2025-09-16 | - | `-` | 0 |
| [#5019](https://github.com/OpenXiangShan/XiangShan/pull/5019) | fix(ibuffer): receive identifiedCfi and pass it to backend | Yan-Muzi | 2025-09-11 | - | `-` | 0 |
| [#5018](https://github.com/OpenXiangShan/XiangShan/pull/5018) | fix(Bitmap): fix not need bitmap check logic in LLPTW | yxtx1994 | 2025-09-11 | - | `-` | 0 |
| [#5016](https://github.com/OpenXiangShan/XiangShan/pull/5016) | fix(Ftq): add ExceptionType.fromBackend & fix write condition in Ftq | ngc7331 | 2025-09-09 | - | `-` | 0 |
| [#5012](https://github.com/OpenXiangShan/XiangShan/pull/5012) | fix(ifu): rewrite instruction boundary calculation and offset | Yan-Muzi | 2025-09-08 | - | `-` | 0 |
| [#5008](https://github.com/OpenXiangShan/XiangShan/pull/5008) | fix(ubtb): fix hit detection to resolve multi-hit | ngc7331 | 2025-09-05 | - | `-` | 0 |
| [#5006](https://github.com/OpenXiangShan/XiangShan/pull/5006) | fix(Vsegment): fix address generation of misaligned split | weidingliu | 2025-09-05 | - | `-` | 0 |
| [#5005](https://github.com/OpenXiangShan/XiangShan/pull/5005) | fix(prefetch): the statistic of prefetch hit | Maxpicca-Li | 2025-09-04 | - | `-` | 0 |
| [#5004](https://github.com/OpenXiangShan/XiangShan/pull/5004) | fix(ubtb): alloc new entry for taken branches only | ngc7331 | 2025-09-04 | - | `-` | 0 |
| [#5003](https://github.com/OpenXiangShan/XiangShan/pull/5003) | fix(RobCompress): fix isRVC transfer logic for new ftqoffset | lewislzh | 2025-09-04 | - | `-` | 0 |
| [#4997](https://github.com/OpenXiangShan/XiangShan/pull/4997) | fix(MMU): PMM is disabled if MXR is effective | cebarobot | 2025-09-01 | - | `-` | 0 |
| [#4983](https://github.com/OpenXiangShan/XiangShan/pull/4983) | fix(MMU): TLB freeze when ptw resp in particular cycle | cebarobot | 2025-08-27 | - | `-` | 0 |
| [#4982](https://github.com/OpenXiangShan/XiangShan/issues/4982) | [BOT] REFs report different exception causes at 0x80000000 | poemonsense | 2025-08-27 | - | `-` | 4 |
| [#4981](https://github.com/OpenXiangShan/XiangShan/issues/4981) | [BOT] DUT and REFs disagree on s10, mcause, mtval values. | poemonsense | 2025-08-27 | - | `-` | 4 |
| [#4980](https://github.com/OpenXiangShan/XiangShan/issues/4980) | [BOT] mstatus/sstatus high bits set unexpectedly at exception entry | poemonsense | 2025-08-27 | - | `-` | 4 |
| [#4979](https://github.com/OpenXiangShan/XiangShan/pull/4979) | fix(CSR): fix dpc for trapping to dmode | wissygh | 2025-08-27 | - | `-` | 0 |
| [#4977](https://github.com/OpenXiangShan/XiangShan/pull/4977) | fix(ubtb): alloc entry for taken branch only | ngc7331 | 2025-08-26 | - | `-` | 0 |
| [#4973](https://github.com/OpenXiangShan/XiangShan/issues/4973) | Mismatch between Xiangshan and NEMU in a random generated program | timegoer | 2025-08-25 | - | `-` | 3 |
| [#4971](https://github.com/OpenXiangShan/XiangShan/pull/4971) | fix(FTB): X state in FTB | Yan-Muzi | 2025-08-25 | - | `-` | 0 |
| [#4968](https://github.com/OpenXiangShan/XiangShan/pull/4968) | fix(Bpu): wait Sram reset to avoid x-state | ngc7331 | 2025-08-22 | - | `-` | 1 |
| [#4965](https://github.com/OpenXiangShan/XiangShan/pull/4965) | fix(LoadUnit): reaccess the data even if it is a fast replay | Maxpicca-Li | 2025-08-21 | - | `-` | 2 |
| [#4962](https://github.com/OpenXiangShan/XiangShan/pull/4962) | feat(bp): train BPU with information provided by backend when branches are resolved | Yan-Muzi | 2025-08-20 | - | `-` | 0 |
| [#4958](https://github.com/OpenXiangShan/XiangShan/issues/4958) | Mismatch between Xiangshan and NEMU | timegoer | 2025-08-19 | - | `-` | 3 |
| [#4956](https://github.com/OpenXiangShan/XiangShan/pull/4956) | fix(VSegmentUnit): fof instruction writeback origin vl | weidingliu | 2025-08-18 | - | `-` | 0 |
| [#4952](https://github.com/OpenXiangShan/XiangShan/issues/4952) | Mismatch between Xiangshan and NEMU | timegoer | 2025-08-15 | - | `-` | 3 |
| [#4951](https://github.com/OpenXiangShan/XiangShan/issues/4951) | Mismatch when access  hcontext(scontext, mcontext) between Xiangshan and NEMU | timegoer | 2025-08-14 | - | `-` | 0 |
| [#4950](https://github.com/OpenXiangShan/XiangShan/issues/4950) | Mismatch at access hcontext(scontext, mcontext) between Xiangshan and NEMU | timegoer | 2025-08-14 | - | `-` | 1 |
| [#4949](https://github.com/OpenXiangShan/XiangShan/issues/4949) | Mismatch at pc = 0x0080000c3c between Xiangshan and NEMU | timegoer | 2025-08-14 | - | `-` | 1 |
| [#4948](https://github.com/OpenXiangShan/XiangShan/issues/4948) | Mismatch at pc = 0x0080000344 between Xiangshan and NEMU | timegoer | 2025-08-14 | - | `-` | 2 |
| [#4947](https://github.com/OpenXiangShan/XiangShan/issues/4947) | Mismatch at  csrrwi between Xiangshan and NEMU | timegoer | 2025-08-14 | - | `-` | 1 |
| [#4944](https://github.com/OpenXiangShan/XiangShan/pull/4944) | feat(rob): std uop use numWB instead of stdWritebacked | xiaofeibao-xjtu | 2025-08-13 | - | `-` | 0 |
| [#4941](https://github.com/OpenXiangShan/XiangShan/pull/4941) | fix(vlbusytable): remove wakeUpInt to avoid load fast wakes up vsetvli | Ziyue-Zhang | 2025-08-13 | - | `-` | 0 |
| [#4935](https://github.com/OpenXiangShan/XiangShan/pull/4935) | fix(Bitmap): fix jmp_bitmap_check logic in PtwCache | yxtx1994 | 2025-08-12 | - | `-` | 0 |
| [#4929](https://github.com/OpenXiangShan/XiangShan/pull/4929) | fix(pma): fix pma RegOut | sinceforYy | 2025-08-05 | - | `-` | 0 |
| [#4923](https://github.com/OpenXiangShan/XiangShan/pull/4923) | fix(excp): add SWC to exception priorities | sinceforYy | 2025-07-31 | - | `-` | 0 |
| [#4922](https://github.com/OpenXiangShan/XiangShan/pull/4922) | fix(ifu): fix IBuffer enqueue check for nc instructions. | my-mayfly | 2025-07-31 | - | `-` | 2 |
| [#4916](https://github.com/OpenXiangShan/XiangShan/pull/4916) | fix(PTW): Fix X-prop caused by using un-initialized stage1Hit in Mux() | forever043 | 2025-07-30 | - | `-` | 2 |
| [#4915](https://github.com/OpenXiangShan/XiangShan/pull/4915) | fix(CSR): initialize [m\|h\|s]context to 0 | wissygh | 2025-07-28 | - | `-` | 0 |
| [#4914](https://github.com/OpenXiangShan/XiangShan/pull/4914) | fix(misalign): fixed a hang issue caused by vector misalign | Anzooooo | 2025-07-28 | - | `-` | 0 |
| [#4913](https://github.com/OpenXiangShan/XiangShan/pull/4913) | fix(TLB): vaddr should be extended to PAddrBitsMax | good-circle | 2025-07-25 | - | `-` | 0 |
| [#4911](https://github.com/OpenXiangShan/XiangShan/pull/4911) | fix(TLB): fix GPA matching bug in Napot cases | good-circle | 2025-07-25 | - | `-` | 0 |
| [#4907](https://github.com/OpenXiangShan/XiangShan/pull/4907) | fix(DCache): there also needs memBackTypeMM setting | Maxpicca-Li | 2025-07-24 | - | `-` | 0 |
| [#4904](https://github.com/OpenXiangShan/XiangShan/issues/4904) | A typo in README.md / README.md中的拼写错误 | skyhgzsh | 2025-07-23 | - | `-` | 0 |
| [#4903](https://github.com/OpenXiangShan/XiangShan/pull/4903) | fix(Ifu,InstrUncache): flush mmio fsm | ngc7331 | 2025-07-23 | - | `-` | 0 |
| [#4900](https://github.com/OpenXiangShan/XiangShan/pull/4900) | fix(L2TLB): fix check condition for Napot pages | good-circle | 2025-07-23 | - | `-` | 0 |
| [#4899](https://github.com/OpenXiangShan/XiangShan/pull/4899) | fix(MainPipe): fix `s3_data_error_beu` generate logic avoid x state | cz4e | 2025-07-23 | - | `-` | 0 |
| [#4898](https://github.com/OpenXiangShan/XiangShan/pull/4898) | fix(ICache,Ifu): set memBackTypeMM and memPageTypeNC correctly | ngc7331 | 2025-07-22 | - | `-` | 0 |
| [#4896](https://github.com/OpenXiangShan/XiangShan/pull/4896) | feat(Bpu): v3 bpu part2 | ngc7331 | 2025-07-21 | - | `-` | 0 |
| [#4892](https://github.com/OpenXiangShan/XiangShan/pull/4892) | fix(VSegmentUnit): adjust the fullva bit width of the tlb req | Anzooooo | 2025-07-17 | - | `-` | 0 |
| [#4889](https://github.com/OpenXiangShan/XiangShan/pull/4889) | fix(VSplit): fix judgement of unaligned index vector load/store | weidingliu | 2025-07-16 | - | `-` | 0 |
| [#4886](https://github.com/OpenXiangShan/XiangShan/pull/4886) | fix(TLB): vbop should drive high the isPrefetch signal | good-circle | 2025-07-14 | - | `-` | 0 |
| [#4881](https://github.com/OpenXiangShan/XiangShan/pull/4881) | fix(ifu): fix speculative instruction fetch in MMIO region. | my-mayfly | 2025-07-10 | - | `-` | 0 |
| [#4877](https://github.com/OpenXiangShan/XiangShan/pull/4877) | fix(MisalignBuffer): fix vector misalign request writeback ready | weidingliu | 2025-07-10 | - | `-` | 0 |
| [#4876](https://github.com/OpenXiangShan/XiangShan/pull/4876) | fix(StoreQueue): fix not set vecExceptionFlagCancel | weidingliu | 2025-07-09 | - | `-` | 0 |
| [#4874](https://github.com/OpenXiangShan/XiangShan/pull/4874) | fix(rab): correct ismove sent to rab for instraction page fault caused by move elimination | xiaofeibao-xjtu | 2025-07-09 | - | `-` | 0 |
| [#4869](https://github.com/OpenXiangShan/XiangShan/pull/4869) | fix(Vsplit): fix Vec Store split stuck when misaligned | weidingliu | 2025-07-07 | - | `-` | 0 |
| [#4868](https://github.com/OpenXiangShan/XiangShan/issues/4868) | riscv-rootfs: fatal error: libfdt.h: No such file or directory | han-jianing | 2025-07-07 | - | `-` | 0 |
| [#4865](https://github.com/OpenXiangShan/XiangShan/pull/4865) | fix(VMergeBuffer): fix gpaddr calculation when Unit-Stride triggers an exception | Anzooooo | 2025-07-05 | - | `-` | 0 |
| [#4864](https://github.com/OpenXiangShan/XiangShan/issues/4864) | `mcause` error when load data | ha0lyu | 2025-07-04 | - | `-` | 4 |
| [#4856](https://github.com/OpenXiangShan/XiangShan/pull/4856) | fix(MainPipe): fix mainpipe x state when miss request after miss request | cz4e | 2025-07-02 | - | `-` | 0 |
| [#4854](https://github.com/OpenXiangShan/XiangShan/pull/4854) | fix(VMergeBuffer): adjust elemidx generation logic when exception | Anzooooo | 2025-07-02 | - | `-` | 0 |
| [#4853](https://github.com/OpenXiangShan/XiangShan/pull/4853) | fix(VSegmentUnit): flush sbuffer until sbuffer is empty | Anzooooo | 2025-07-02 | - | `-` | 0 |
| [#4851](https://github.com/OpenXiangShan/XiangShan/pull/4851) | feat(Bpu): v3 bpu part1 | ngc7331 | 2025-07-01 | - | `-` | 0 |
| [#4850](https://github.com/OpenXiangShan/XiangShan/pull/4850) | fix(VTypeBuffer): fix bug of commitCount, walkCount and spclWalkCount's width | xiaofeibao-xjtu | 2025-07-01 | - | `-` | 0 |
| [#4849](https://github.com/OpenXiangShan/XiangShan/pull/4849) | feat(ifu): pre-decoding delayed by one cycle; modify pre-decoder and checker submodule.  | my-mayfly | 2025-07-01 | - | `-` | 0 |
| [#4845](https://github.com/OpenXiangShan/XiangShan/pull/4845) | Backend-v3 | xiaofeibao-xjtu | 2025-06-30 | - | `-` | 0 |
| [#4842](https://github.com/OpenXiangShan/XiangShan/pull/4842) | fix(MainPipe): fix `extra_meta_resp` overrided | cz4e | 2025-06-27 | - | `-` | 0 |
| [#4840](https://github.com/OpenXiangShan/XiangShan/pull/4840) | fix(LSU): fix misalignbuffer response acceptance process | Anzooooo | 2025-06-26 | - | `-` | 0 |
| [#4836](https://github.com/OpenXiangShan/XiangShan/pull/4836) | fix(intr): fix xtopi genertation for second cycle | sinceforYy | 2025-06-24 | - | `-` | 0 |
| [#4831](https://github.com/OpenXiangShan/XiangShan/pull/4831) | fix(VSegmentUnit): `dcache.error_delayed` will be one beat latet than `dcache.resp` | Anzooooo | 2025-06-23 | - | `-` | 0 |
| [#4829](https://github.com/OpenXiangShan/XiangShan/pull/4829) | fix(LLPTW): fix bug in handling both virtualized & non-virtualized cases | good-circle | 2025-06-23 | - | `-` | 0 |
| [#4828](https://github.com/OpenXiangShan/XiangShan/pull/4828) | fix(MMU): fix the condition for identifying napot pages | good-circle | 2025-06-21 | - | `-` | 0 |
| [#4825](https://github.com/OpenXiangShan/XiangShan/pull/4825) | fix(CSR, NMI): fix the logic for clearing `nmip` | wissygh | 2025-06-20 | - | `-` | 0 |
| [#4820](https://github.com/OpenXiangShan/XiangShan/pull/4820) | fix(vleff): fix vleff writeback `vl` and `vta` | weidingliu | 2025-06-19 | - | `-` | 0 |
| [#4817](https://github.com/OpenXiangShan/XiangShan/pull/4817) | fix(csr): set xstatus.VS dirty when a vector memory access instr has exception | sinceforYy | 2025-06-17 | - | `-` | 0 |
| [#4814](https://github.com/OpenXiangShan/XiangShan/pull/4814) | fix(ICache): do not check meta(1) parity if !s1_doubleline | ngc7331 | 2025-06-16 | - | `-` | 0 |
| [#4811](https://github.com/OpenXiangShan/XiangShan/pull/4811) | fix(MMU): global entries should always hit even asid switch | good-circle | 2025-06-13 | - | `-` | 0 |
| [#4810](https://github.com/OpenXiangShan/XiangShan/pull/4810) | perf(MemBlock): optimize L1DCache index | jin120811 | 2025-06-13 | - | `-` | 0 |
| [#4807](https://github.com/OpenXiangShan/XiangShan/pull/4807) | fix(StoreUnit): vec misalignBufferNack need to mergebuffer | Anzooooo | 2025-06-12 | - | `-` | 0 |
| [#4802](https://github.com/OpenXiangShan/XiangShan/pull/4802) | fix(csr): fix CSR rData when CSR claim IMSIC | sinceforYy | 2025-06-11 | - | `-` | 0 |
| [#4801](https://github.com/OpenXiangShan/XiangShan/pull/4801) | submodule(rocket-chip): bump rocket-chip to fix dm_extTrigger | wissygh | 2025-06-10 | - | `-` | 0 |
| [#4799](https://github.com/OpenXiangShan/XiangShan/pull/4799) | fix(LSU): enable hardwareError exception for all memory access types | Anzooooo | 2025-06-10 | - | `-` | 0 |
| [#4798](https://github.com/OpenXiangShan/XiangShan/issues/4798) | XiangShan performance counter shows an abnormal proportion of ITLBMissBubble | BaoBao-zhu | 2025-06-10 | - | `-` | 2 |
| [#4795](https://github.com/OpenXiangShan/XiangShan/pull/4795) | fix(MetaArray): add bypass to read when write s0 | Anzooooo | 2025-06-09 | - | `-` | 0 |
| [#4793](https://github.com/OpenXiangShan/XiangShan/pull/4793) | fix(TLB): correct gpaddr generating for handle_block | cebarobot | 2025-06-08 | - | `-` | 0 |
| [#4792](https://github.com/OpenXiangShan/XiangShan/pull/4792) | fix(VLSU): vector ld/st does not generate misalign exception when mmio | Anzooooo | 2025-06-08 | - | `-` | 0 |
| [#4790](https://github.com/OpenXiangShan/XiangShan/pull/4790) | refactor(prefetch): add a wrapper and parameterization | Maxpicca-Li | 2025-06-08 | - | `-` | 0 |
| [#4788](https://github.com/OpenXiangShan/XiangShan/pull/4788) | fix(LLPTW): should block hptw req when dup with mem_out | good-circle | 2025-06-07 | - | `-` | 0 |
| [#4782](https://github.com/OpenXiangShan/XiangShan/pull/4782) | fix(MainPipe): adjust `s2_can_go_to_s3` select priority for refill ecc inject lead to tag miss | cz4e | 2025-06-05 | - | `-` | 0 |
| [#4780](https://github.com/OpenXiangShan/XiangShan/pull/4780) | fix(TLB): use the same hit logic for block as for bypass | cebarobot | 2025-06-05 | - | `-` | 0 |
| [#4779](https://github.com/OpenXiangShan/XiangShan/pull/4779) | fix(DiffStoreEvent): unify the difftest time of various store | Maxpicca-Li | 2025-06-04 | - | `-` | 0 |
| [#4776](https://github.com/OpenXiangShan/XiangShan/pull/4776) | feat: use custom HINT for simulation debug trigger | Tang-Haojin | 2025-06-04 | - | `-` | 0 |
| [#4774](https://github.com/OpenXiangShan/XiangShan/pull/4774) | fix(LoadQueueUncache): fix missing hardware error | Maxpicca-Li | 2025-06-03 | - | `-` | 0 |
| [#4769](https://github.com/OpenXiangShan/XiangShan/pull/4769) | fix(PMP): take a beat for `cmd` | Anzooooo | 2025-06-03 | - | `-` | 0 |
| [#4755](https://github.com/OpenXiangShan/XiangShan/pull/4755) | fix(MainPipe): add reg enable for mainpipe `ecc_delayed` | cz4e | 2025-05-30 | - | `-` | 0 |
| [#4754](https://github.com/OpenXiangShan/XiangShan/pull/4754) | fix(LLPTW): first_s2xlate_fault should be true when check_g_perm_fail | good-circle | 2025-05-29 | - | `-` | 0 |
| [#4753](https://github.com/OpenXiangShan/XiangShan/pull/4753) | fix(MainPipe): fix report error for probe/atomic request | cz4e | 2025-05-29 | - | `-` | 0 |
| [#4751](https://github.com/OpenXiangShan/XiangShan/pull/4751) | fix(LoadUnit): misaligned exception addr should use split addr | Anzooooo | 2025-05-29 | - | `-` | 0 |
| [#4748](https://github.com/OpenXiangShan/XiangShan/pull/4748) | fix(TLB): offset of paddr is vaddr[11:0] | good-circle | 2025-05-28 | - | `-` | 0 |
| [#4742](https://github.com/OpenXiangShan/XiangShan/pull/4742) | fix(csr, perf): skip CSR read xtopi | sinceforYy | 2025-05-27 | - | `-` | 0 |
| [#4741](https://github.com/OpenXiangShan/XiangShan/pull/4741) | fix(MainPipe): fix probe/replace stall for alias scheme | cz4e | 2025-05-27 | - | `-` | 0 |
| [#4736](https://github.com/OpenXiangShan/XiangShan/pull/4736) | fix(trace): move checking xret from commit to rename | wissygh | 2025-05-26 | - | `-` | 0 |
| [#4731](https://github.com/OpenXiangShan/XiangShan/pull/4731) | fix(StoreQueue): fix vecExceptionFlag when flow is misaligned | weidingliu | 2025-05-26 | - | `-` | 0 |
| [#4730](https://github.com/OpenXiangShan/XiangShan/pull/4730) | fix(StoreUnit):  mask for the cmo instr is 0xFFFF | Anzooooo | 2025-05-25 | - | `-` | 0 |
| [#4728](https://github.com/OpenXiangShan/XiangShan/pull/4728) | fix(jump, branch): fix wrong pc sext when sv48x4 | Tang-Haojin | 2025-05-23 | - | `-` | 0 |
| [#4725](https://github.com/OpenXiangShan/XiangShan/pull/4725) | fix(ICache,Ifu,Perf): fix perfcounters | ngc7331 | 2025-05-23 | - | `-` | 0 |
| [#4724](https://github.com/OpenXiangShan/XiangShan/pull/4724) | fix(PMA): sc / amo should report af when !atomic | good-circle | 2025-05-23 | - | `-` | 0 |
| [#4723](https://github.com/OpenXiangShan/XiangShan/pull/4723) | fix(sstateenx): generate sstateen[1\|2\|3]Module to verilog | wissygh | 2025-05-23 | - | `-` | 0 |
| [#4718](https://github.com/OpenXiangShan/XiangShan/pull/4718) | fix(MainPipe): fix pseudo ecc inject report address | cz4e | 2025-05-21 | - | `-` | 0 |
| [#4717](https://github.com/OpenXiangShan/XiangShan/pull/4717) | fix(Uncache): add bus error handle for uncache store | Maxpicca-Li | 2025-05-21 | - | `-` | 0 |
| [#4713](https://github.com/OpenXiangShan/XiangShan/issues/4713) | Mstatus.MIE not set properly | bantierr | 2025-05-20 | - | `-` | 3 |
| [#4711](https://github.com/OpenXiangShan/XiangShan/pull/4711) | fix(StoreQueue): redirect logic for vector exception | Anzooooo | 2025-05-20 | - | `-` | 0 |
| [#4702](https://github.com/OpenXiangShan/XiangShan/pull/4702) | fix(StoreUnit): cbo requires read permission | Anzooooo | 2025-05-17 | - | `-` | 0 |
| [#4698](https://github.com/OpenXiangShan/XiangShan/pull/4698) | fix(csr): CSRR instruction read xtopi/xtopei inOrder | sinceforYy | 2025-05-16 | - | `-` | 0 |
| [#4696](https://github.com/OpenXiangShan/XiangShan/pull/4696) | fix(csr): add [m\|h\|s]context for sdtrig extension | wissygh | 2025-05-16 | - | `-` | 0 |
| [#4694](https://github.com/OpenXiangShan/XiangShan/pull/4694) | fix(TLB): valididx(i) should all be true when isSuperPage | good-circle | 2025-05-15 | - | `-` | 0 |
| [#4689](https://github.com/OpenXiangShan/XiangShan/issues/4689) | XiangShan didn’t exit simulation in my test case | LuLuji04 | 2025-05-14 | - | `-` | 1 |
| [#4687](https://github.com/OpenXiangShan/XiangShan/issues/4687) | `tdata2` CSR read returns unexpected value | LuLuji04 | 2025-05-13 | - | `-` | 1 |
| [#4682](https://github.com/OpenXiangShan/XiangShan/issues/4682) | Fails to raise Instruction Access Fault on invalid PC | LuLuji04 | 2025-05-11 | - | `-` | 1 |
| [#4676](https://github.com/OpenXiangShan/XiangShan/pull/4676) | fix(smstateen): add [m\|h\|s]stateen[1\|2\|3] CSRs | wissygh | 2025-05-09 | - | `-` | 0 |
| [#4674](https://github.com/OpenXiangShan/XiangShan/pull/4674) | fix(LoadUnit): preventing raw jams caused by misalignment | Anzooooo | 2025-05-09 | - | `-` | 0 |
| [#4673](https://github.com/OpenXiangShan/XiangShan/pull/4673) | fix(LoadUnit): misaligned exception addr should use split addr | Anzooooo | 2025-05-09 | - | `-` | 0 |
| [#4672](https://github.com/OpenXiangShan/XiangShan/pull/4672) | fix(Smcsrind, Smstateen, Smaia): add missing permit check | NewPaulWalker | 2025-05-08 | - | `-` | 0 |
| [#4671](https://github.com/OpenXiangShan/XiangShan/pull/4671) | fix(CSR): fix trapInst update logic | lewislzh | 2025-05-08 | - | `-` | 0 |
| [#4668](https://github.com/OpenXiangShan/XiangShan/issues/4668) | `mtval` Should Contain the `Illegal Instruction Encoding` on Illegal Instruction Exception | LuLuji04 | 2025-05-07 | - | `-` | 1 |
| [#4667](https://github.com/OpenXiangShan/XiangShan/issues/4667) | Mismatch in `mcause` for Unaligned `amomin.d` Access Between XiangShan and Spike | LuLuji04 | 2025-05-07 | - | `-` | 1 |
| [#4666](https://github.com/OpenXiangShan/XiangShan/issues/4666) | Mismatch in `mstatus.SIE` and `sstatus.SIE` After `sret` Instruction | LuLuji04 | 2025-05-07 | - | `-` | 1 |
| [#4665](https://github.com/OpenXiangShan/XiangShan/issues/4665) | Spike Triggers Instruction Access Fault, But XiangShan Does Not | LuLuji04 | 2025-05-07 | - | `-` | 1 |
| [#4664](https://github.com/OpenXiangShan/XiangShan/issues/4664) | Mismatch in `mcause` Between XiangShan and Spike After `lw` | LuLuji04 | 2025-05-07 | - | `-` | 1 |
| [#4663](https://github.com/OpenXiangShan/XiangShan/pull/4663) | fix(StoreQueue): cbozero flag should not be set on exception | Anzooooo | 2025-05-07 | - | `-` | 0 |
| [#4660](https://github.com/OpenXiangShan/XiangShan/pull/4660) | fix(StoreQueue): fix timeout of vecExceptionFlag when redirect | weidingliu | 2025-05-06 | - | `-` | 0 |
| [#4659](https://github.com/OpenXiangShan/XiangShan/pull/4659) | fix(MMU): fix MMU hit logic for napot cases | good-circle | 2025-05-03 | - | `-` | 0 |
| [#4658](https://github.com/OpenXiangShan/XiangShan/pull/4658) | fix(TLB): fix a bug in handle_block where s2_ppn is generated | good-circle | 2025-05-03 | - | `-` | 0 |
| [#4649](https://github.com/OpenXiangShan/XiangShan/pull/4649) | fix(Smstateen): fix access to sireg/vsireg | wissygh | 2025-04-29 | - | `-` | 0 |
| [#4648](https://github.com/OpenXiangShan/XiangShan/pull/4648) | fix(LCOFI): fix writable of LCOFI bit(13) of mvip/mvien/hvip/hvien | NewPaulWalker | 2025-04-29 | - | `-` | 0 |
| [#4647](https://github.com/OpenXiangShan/XiangShan/pull/4647) | fix(TLB): fix two issues in genVpn | good-circle | 2025-04-29 | - | `-` | 0 |
| [#4645](https://github.com/OpenXiangShan/XiangShan/pull/4645) | fix(wfi): add rnmi interrupt to wfievent | lewislzh | 2025-04-28 | - | `-` | 0 |
| [#4642](https://github.com/OpenXiangShan/XiangShan/pull/4642) | fix(rob): fix bug of robIdxNextLine may overflow | xiaofeibao-xjtu | 2025-04-28 | - | `-` | 0 |
| [#4641](https://github.com/OpenXiangShan/XiangShan/pull/4641) | fix(StoreQueue): strictly ensure deq moves in order | Maxpicca-Li | 2025-04-27 | - | `-` | 0 |
| [#4639](https://github.com/OpenXiangShan/XiangShan/issues/4639) | `mtval` different | ha0lyu | 2025-04-27 | - | `-` | 1 |
| [#4637](https://github.com/OpenXiangShan/XiangShan/issues/4637) | NEMU incorrectly allows sc.w to succeed after address change | ha0lyu | 2025-04-27 | - | `-` | 1 |
| [#4636](https://github.com/OpenXiangShan/XiangShan/pull/4636) | fix(LoadUnit): perfetch no longer generates nc access | Anzooooo | 2025-04-27 | - | `-` | 0 |
| [#4632](https://github.com/OpenXiangShan/XiangShan/pull/4632) | fix(Rob): calculate full PC for difftest by transType | Tang-Haojin | 2025-04-26 | - | `-` | 0 |
| [#4629](https://github.com/OpenXiangShan/XiangShan/pull/4629) | fix(step): fix step for exception. | wissygh | 2025-04-25 | - | `-` | 0 |
| [#4628](https://github.com/OpenXiangShan/XiangShan/pull/4628) | fix: use a more sensible entry priority of uncacheBuffer | Maxpicca-Li | 2025-04-25 | - | `-` | 0 |
| [#4626](https://github.com/OpenXiangShan/XiangShan/pull/4626) | chore: add the version info to the simulation print output | NewPaulWalker | 2025-04-25 | - | `-` | 0 |
| [#4623](https://github.com/OpenXiangShan/XiangShan/pull/4623) | fix(criticalError): Stop counting `wfi_cycles` when disable `wfiResume` | wissygh | 2025-04-24 | - | `-` | 0 |
| [#4622](https://github.com/OpenXiangShan/XiangShan/pull/4622) | fix(DCache): fix DCache replacement when replace a `BtoT` ways | cz4e | 2025-04-24 | - | `-` | 0 |
| [#4619](https://github.com/OpenXiangShan/XiangShan/pull/4619) | fix(LoadUnit, LSQ): fix report exception type for hardware error | cz4e | 2025-04-24 | - | `-` | 0 |
| [#4611](https://github.com/OpenXiangShan/XiangShan/pull/4611) | fix(AXI4Memory): fix write request enqueue DRAMSim logic for AXI4Memory | cz4e | 2025-04-22 | - | `-` | 0 |
| [#4597](https://github.com/OpenXiangShan/XiangShan/pull/4597) | fix(LLPTW): dup entry should consider s2xlate in need_to_waiting_vec | good-circle | 2025-04-20 | - | `-` | 0 |
| [#4596](https://github.com/OpenXiangShan/XiangShan/pull/4596) | fix(LLPTW): dup_wait_resp should not send last_hptw_req when excp | good-circle | 2025-04-20 | - | `-` | 0 |
| [#4594](https://github.com/OpenXiangShan/XiangShan/pull/4594) | fix(xiselect): set the minimum range for xiselect | NewPaulWalker | 2025-04-18 | - | `-` | 0 |
| [#4593](https://github.com/OpenXiangShan/XiangShan/pull/4593) | fix(VLSU): modifying vector misalign elemidx generation | Anzooooo | 2025-04-18 | - | `-` | 0 |
| [#4592](https://github.com/OpenXiangShan/XiangShan/pull/4592) | fix(StoreUnit): cbo violation check should check cacheline | Anzooooo | 2025-04-18 | - | `-` | 0 |
| [#4588](https://github.com/OpenXiangShan/XiangShan/pull/4588) | fix(TLB): explicitly specify the signal width again when truncated | good-circle | 2025-04-17 | - | `-` | 0 |
| [#4587](https://github.com/OpenXiangShan/XiangShan/pull/4587) | fix(TLB): onlyStage1 req should use s1_paddr rather than s2_paddr | good-circle | 2025-04-17 | - | `-` | 0 |
| [#4586](https://github.com/OpenXiangShan/XiangShan/pull/4586) | fix(PTW): false positive accessFault should not use af_level when resp | good-circle | 2025-04-17 | - | `-` | 0 |
| [#4583](https://github.com/OpenXiangShan/XiangShan/pull/4583) | fix(top): enable cpuclock when debug halt req | wissygh | 2025-04-17 | - | `-` | 0 |
| [#4582](https://github.com/OpenXiangShan/XiangShan/issues/4582) | Inconsistent `siselect` register range. | ha0lyu | 2025-04-17 | - | `-` | 1 |
| [#4581](https://github.com/OpenXiangShan/XiangShan/issues/4581) | Inconsistent `vsiselect` register range. | ha0lyu | 2025-04-17 | - | `-` | 1 |
| [#4580](https://github.com/OpenXiangShan/XiangShan/pull/4580) | fix(LoadUnit): fix ldld && stld query revoke logic | jin120811 | 2025-04-17 | - | `-` | 0 |
| [#4577](https://github.com/OpenXiangShan/XiangShan/issues/4577) | Mismatch in `sc.w` Instruction After `lr.w` | LuLuji04 | 2025-04-16 | - | `-` | 1 |
| [#4576](https://github.com/OpenXiangShan/XiangShan/issues/4576) | `unimp` After Returning from `ebreak` and Continuing Instructions Trigger Mismatch | LuLuji04 | 2025-04-16 | - | `-` | 1 |
| [#4575](https://github.com/OpenXiangShan/XiangShan/issues/4575) | `amomin.w` Instruction Behavior Inconsistency | LuLuji04 | 2025-04-16 | - | `-` | 1 |
| [#4574](https://github.com/OpenXiangShan/XiangShan/issues/4574) | Debug register `tdata1` mismatch | ha0lyu | 2025-04-16 | - | `-` | 1 |
| [#4572](https://github.com/OpenXiangShan/XiangShan/pull/4572) | fix(MainPipe): fix error valid when Atomics and SBuffer request miss | cz4e | 2025-04-16 | - | `-` | 0 |
| [#4571](https://github.com/OpenXiangShan/XiangShan/pull/4571) | fix(xtopi): fix xtopi generation conditions | sinceforYy | 2025-04-16 | - | `-` | 0 |
| [#4570](https://github.com/OpenXiangShan/XiangShan/pull/4570) | fix(exceptionGen): clear isEnqExcp when older or curr wb exception coming | Ziyue-Zhang | 2025-04-16 | - | `-` | 0 |
| [#4561](https://github.com/OpenXiangShan/XiangShan/pull/4561) | fix(trace): fix parameters of trace | wissygh | 2025-04-15 | - | `-` | 0 |
| [#4546](https://github.com/OpenXiangShan/XiangShan/pull/4546) | submodule(chiselAIA): bump chiselAIA to fix `imsic.toCSR.illegal` | wissygh | 2025-04-11 | - | `-` | 0 |
| [#4541](https://github.com/OpenXiangShan/XiangShan/pull/4541) | fix(L2TlbPrefetch): fix flush condition of L2 TLB Prefetch | good-circle | 2025-04-10 | - | `-` | 0 |
| [#4540](https://github.com/OpenXiangShan/XiangShan/pull/4540) | fix(PTW): fix exception gen when both af and (pf \| gpf) occur | good-circle | 2025-04-10 | - | `-` | 0 |
| [#4539](https://github.com/OpenXiangShan/XiangShan/pull/4539) | fix(PTWCache): hfence_gvma should ignore g bit | good-circle | 2025-04-10 | - | `-` | 0 |
| [#4535](https://github.com/OpenXiangShan/XiangShan/pull/4535) | fix(decode): block the vector decode until vsetvl has committed | Ziyue-Zhang | 2025-04-09 | - | `-` | 0 |
| [#4534](https://github.com/OpenXiangShan/XiangShan/pull/4534) | fix(prefetch): fix control signals of l1 prefetchers | Maxpicca-Li | 2025-04-09 | - | `-` | 0 |
| [#4533](https://github.com/OpenXiangShan/XiangShan/pull/4533) | fix(vstopi): remove SEI from Candidate 4 | sinceforYy | 2025-04-09 | - | `-` | 0 |
| [#4531](https://github.com/OpenXiangShan/XiangShan/pull/4531) | fix(StoreQueue): keep readPtr until slave ack when outstanding | Maxpicca-Li | 2025-04-09 | - | `-` | 0 |
| [#4527](https://github.com/OpenXiangShan/XiangShan/pull/4527) | fix(MMU): fix gvpn generate when PTWCache Stage1Hit a napot entry | good-circle | 2025-04-08 | - | `-` | 0 |
| [#4526](https://github.com/OpenXiangShan/XiangShan/pull/4526) | fix(LSU): fix exception for misalign access to `nc` space | Anzooooo | 2025-04-08 | - | `-` | 0 |
| [#4525](https://github.com/OpenXiangShan/XiangShan/pull/4525) | fix(LLPTW): should not check g-stage pf when vs-stage pf occured | good-circle | 2025-04-08 | - | `-` | 0 |
| [#4524](https://github.com/OpenXiangShan/XiangShan/pull/4524) | fix(PTW): should not do pmp check before G-stage finish | good-circle | 2025-04-08 | - | `-` | 0 |
| [#4519](https://github.com/OpenXiangShan/XiangShan/pull/4519) | fix(Svinval): remove assert related to Svinval extension in ROB | NewPaulWalker | 2025-04-08 | - | `-` | 0 |
| [#4517](https://github.com/OpenXiangShan/XiangShan/pull/4517) | fix(difftest): fix sync aia event valid | sinceforYy | 2025-04-08 | - | `-` | 0 |
| [#4510](https://github.com/OpenXiangShan/XiangShan/pull/4510) | fix(LLPTW): each LLPTW entry should use its own s2xlate | good-circle | 2025-04-07 | - | `-` | 0 |
| [#4509](https://github.com/OpenXiangShan/XiangShan/pull/4509) | feat(AIA): integrate ChiselAIA again | Tang-Haojin | 2025-04-07 | - | `-` | 0 |
| [#4504](https://github.com/OpenXiangShan/XiangShan/issues/4504) | ICacheMissEntry Assertion Violable | JacyCui | 2025-04-04 | - | `-` | 4 |
| [#4493](https://github.com/OpenXiangShan/XiangShan/pull/4493) | timing(StoreMisalignBuffer): fix misalign buffer enq timing | cz4e | 2025-04-02 | - | `-` | 0 |
| [#4491](https://github.com/OpenXiangShan/XiangShan/pull/4491) | feat(backend): make wfi timeout configurable | Tang-Haojin | 2025-04-01 | - | `-` | 0 |
| [#4485](https://github.com/OpenXiangShan/XiangShan/pull/4485) | fix(FTB, FTQ): dont use CPL2 SplittedSRAM | TheKiteRunner24 | 2025-03-31 | - | `-` | 0 |
| [#4473](https://github.com/OpenXiangShan/XiangShan/pull/4473) | fix(LLPTW): Should consider napot scenario when allStage | good-circle | 2025-03-27 | - | `-` | 0 |
| [#4472](https://github.com/OpenXiangShan/XiangShan/pull/4472) | fix(PTW): Should not do gvpn check when pageFault or ppn_af | good-circle | 2025-03-27 | - | `-` | 0 |
| [#4471](https://github.com/OpenXiangShan/XiangShan/pull/4471) | fix(TLB): explicitly specify the signal width when truncated | good-circle | 2025-03-27 | - | `-` | 0 |
| [#4468](https://github.com/OpenXiangShan/XiangShan/pull/4468) | area(ICache): split ICache meta SRAM | my-mayfly | 2025-03-27 | - | `-` | 0 |
| [#4456](https://github.com/OpenXiangShan/XiangShan/pull/4456) | fix(FusionDecoder): tie output to false when disabled | Tang-Haojin | 2025-03-24 | - | `-` | 0 |
| [#4455](https://github.com/OpenXiangShan/XiangShan/pull/4455) | fix(TLB): L1 TLB will not save the high bit of PPN | good-circle | 2025-03-23 | - | `-` | 0 |
| [#4454](https://github.com/OpenXiangShan/XiangShan/pull/4454) | fix(TLB): fix a typo about napot scenario | good-circle | 2025-03-23 | - | `-` | 0 |
| [#4453](https://github.com/OpenXiangShan/XiangShan/pull/4453) | fix(PTWCache): length of PPN should be gvpnLen when hypervisor | good-circle | 2025-03-23 | - | `-` | 0 |
| [#4449](https://github.com/OpenXiangShan/XiangShan/pull/4449) | fix(difftest, CSR): sync non-reg interrupt pending right after reset | Tang-Haojin | 2025-03-21 | - | `-` | 0 |
| [#4448](https://github.com/OpenXiangShan/XiangShan/pull/4448) | fix(MMU): Stage1Gpf should use hgatp instead of vsatp | good-circle | 2025-03-21 | - | `-` | 0 |
| [#4445](https://github.com/OpenXiangShan/XiangShan/pull/4445) | Remove frontend SRAM read-write conflict handling logic after it is moved into SRAMTemplate | castleberrysam | 2025-03-21 | - | `-` | 0 |
| [#4442](https://github.com/OpenXiangShan/XiangShan/pull/4442) | fix(LoadUnit): uncache should not be generated when page fault | Anzooooo | 2025-03-19 | - | `-` | 0 |
| [#4441](https://github.com/OpenXiangShan/XiangShan/pull/4441) | fix(StoreUnit): no uncache store misalign of mmio | Anzooooo | 2025-03-19 | - | `-` | 0 |
| [#4439](https://github.com/OpenXiangShan/XiangShan/pull/4439) | fix(fusion): block fusion when trigger fire and exception happen | wissygh | 2025-03-19 | - | `-` | 0 |
| [#4435](https://github.com/OpenXiangShan/XiangShan/pull/4435) | fix(amocas): fix amocas.q to avoid stalls | NewPaulWalker | 2025-03-18 | - | `-` | 0 |
| [#4426](https://github.com/OpenXiangShan/XiangShan/pull/4426) | fix(LoadUnit): fix misalign exception and clearer uncache semantics | Anzooooo | 2025-03-16 | - | `-` | 0 |
| [#4423](https://github.com/OpenXiangShan/XiangShan/pull/4423) | fix(IPrefetchPipe): consider backend exception as part of itlb exception | ngc7331 | 2025-03-14 | - | `-` | 0 |
| [#4422](https://github.com/OpenXiangShan/XiangShan/pull/4422) | fix(PTW): Fix exception handle logic when both pf and af occur | good-circle | 2025-03-14 | - | `-` | 0 |
| [#4419](https://github.com/OpenXiangShan/XiangShan/pull/4419) | fix(csr, difftest): do not update difftest framework on reset | sinceforYy | 2025-03-14 | - | `-` | 0 |
| [#4414](https://github.com/OpenXiangShan/XiangShan/pull/4414) | fix(DM): synchronize the `jtag_reset` in standaloneDM | wissygh | 2025-03-13 | - | `-` | 0 |
| [#4412](https://github.com/OpenXiangShan/XiangShan/pull/4412) | fix(csr): filter out Read-Only CSR in regOut | sinceforYy | 2025-03-13 | - | `-` | 0 |
| [#4407](https://github.com/OpenXiangShan/XiangShan/pull/4407) | fix(PTWCache): Should refill full GVPN to Page Cache | good-circle | 2025-03-12 | - | `-` | 0 |
| [#4406](https://github.com/OpenXiangShan/XiangShan/pull/4406) | fix(PTW): High bits of GVPN should not be truncated | good-circle | 2025-03-12 | - | `-` | 0 |
| [#4404](https://github.com/OpenXiangShan/XiangShan/pull/4404) | fix(LLPTW): Fix exception judgement for different virtualisation stages | good-circle | 2025-03-12 | - | `-` | 0 |
| [#4402](https://github.com/OpenXiangShan/XiangShan/issues/4402) | PC jump back followed by memory access triggers register errors | ha0lyu | 2025-03-11 | - | `-` | 1 |
| [#4401](https://github.com/OpenXiangShan/XiangShan/issues/4401) | NEMU has a problem when executing the `addi` instruction. | ha0lyu | 2025-03-11 | - | `-` | 1 |
| [#4400](https://github.com/OpenXiangShan/XiangShan/issues/4400) | The value read from the `sscratch` register is inconsistent. | ha0lyu | 2025-03-11 | - | `-` | 2 |
| [#4399](https://github.com/OpenXiangShan/XiangShan/issues/4399) | NEMU executes the `ori` instruction incorrectly. | ha0lyu | 2025-03-11 | - | `-` | 1 |
| [#4398](https://github.com/OpenXiangShan/XiangShan/issues/4398) | XiangShan and NEMU show inconsistencies when executing `amoswap.w` | ha0lyu | 2025-03-11 | - | `-` | 1 |
| [#4396](https://github.com/OpenXiangShan/XiangShan/pull/4396) | fix(L2TLB): Napot entries in LLPTW should not be compressed | good-circle | 2025-03-11 | - | `-` | 0 |
| [#4394](https://github.com/OpenXiangShan/XiangShan/pull/4394) | fix(MainPipe):  `error` and `writeback` addr generate logic | cz4e | 2025-03-11 | - | `-` | 0 |
| [#4393](https://github.com/OpenXiangShan/XiangShan/pull/4393) | fix(csr): CSRR instruction read xireg inOrder | sinceforYy | 2025-03-11 | - | `-` | 0 |
| [#4392](https://github.com/OpenXiangShan/XiangShan/pull/4392) | fix(reidrectGen): fix redirectGen valid signal | sinceforYy | 2025-03-11 | - | `-` | 0 |
| [#4388](https://github.com/OpenXiangShan/XiangShan/issues/4388) | Difference in the upper 16 bits of the PC being zero | ha0lyu | 2025-03-10 | - | `-` | 1 |
| [#4387](https://github.com/OpenXiangShan/XiangShan/issues/4387) | Xiangshan does not throw IAF trap, but NEMU throw error `mtval` | ha0lyu | 2025-03-10 | - | `-` | 1 |
| [#4386](https://github.com/OpenXiangShan/XiangShan/issues/4386) | Xiangshan does not throw IAF trap | ha0lyu | 2025-03-10 | - | `-` | 1 |
| [#4385](https://github.com/OpenXiangShan/XiangShan/issues/4385) | `sltiu` instruction bug in NEMU | ha0lyu | 2025-03-10 | - | `-` | 2 |
| [#4382](https://github.com/OpenXiangShan/XiangShan/pull/4382) | fix(amocas): re-split uops for amocas to avoid stalls | NewPaulWalker | 2025-03-10 | - | `-` | 0 |
| [#4369](https://github.com/OpenXiangShan/XiangShan/pull/4369) | fix(LSU): misaligned violation detection stuck | Anzooooo | 2025-03-06 | - | `-` | 0 |
| [#4367](https://github.com/OpenXiangShan/XiangShan/pull/4367) | fix(LoadUnit): exclude prefetch requests | cz4e | 2025-03-06 | - | `-` | 0 |
| [#4361](https://github.com/OpenXiangShan/XiangShan/pull/4361) | feat(Difftest): add multi-core vector load check | Anzooooo | 2025-03-04 | - | `-` | 0 |
| [#4360](https://github.com/OpenXiangShan/XiangShan/pull/4360) | feat(FTB, FTQ): split FTB meta SRAM and FTQ meta SRAM | TheKiteRunner24 | 2025-03-04 | - | `-` | 0 |
| [#4359](https://github.com/OpenXiangShan/XiangShan/pull/4359) | fix(LoadUnit): misalign wakeup should not set s0 valid | Anzooooo | 2025-03-04 | - | `-` | 0 |
| [#4354](https://github.com/OpenXiangShan/XiangShan/pull/4354) | fix(CSR): add VTYPE to in-order read CSRs | Squareless-XD | 2025-03-04 | - | `-` | 0 |
| [#4349](https://github.com/OpenXiangShan/XiangShan/pull/4349) | fix(MMU): incorrect generation of Exception vaddr | cebarobot | 2025-03-04 | - | `-` | 0 |
| [#4346](https://github.com/OpenXiangShan/XiangShan/pull/4346) | fix(Trigger): fix comparison between consecutive pc and tdada2 | wissygh | 2025-03-03 | - | `-` | 0 |
| [#4345](https://github.com/OpenXiangShan/XiangShan/pull/4345) | fix(MainPipe): fix `s3_l2_error` and `s3_error` enable signal | cz4e | 2025-03-03 | - | `-` | 0 |
| [#4337](https://github.com/OpenXiangShan/XiangShan/pull/4337) | fix(DCache): fix wrong condition for blocking lr | bosscharlie | 2025-02-28 | - | `-` | 0 |
| [#4335](https://github.com/OpenXiangShan/XiangShan/pull/4335) | feat(BEU): beu will trigger `NMI_31` non-maskable interrupt | cz4e | 2025-02-28 | - | `-` | 0 |
| [#4333](https://github.com/OpenXiangShan/XiangShan/pull/4333) | fix(LoadUnit): misalign load wakeup not enter loadunit | Anzooooo | 2025-02-28 | - | `-` | 0 |
| [#4324](https://github.com/OpenXiangShan/XiangShan/pull/4324) | fix(L2top): Shouldn't subtract dm from mmio_port when SeperateDMBus disable | wissygh | 2025-02-27 | - | `-` | 0 |
| [#4321](https://github.com/OpenXiangShan/XiangShan/pull/4321) | fix(PFEvent): use `CSRModule` for distribute_csr in PFEvent | wissygh | 2025-02-26 | - | `-` | 0 |
| [#4320](https://github.com/OpenXiangShan/XiangShan/issues/4320) | Unexpected PC Breakpoint Triggering in IFU's FrontendTrigger with 'Less Than' Mode | leesum1 | 2025-02-26 | - | `-` | 0 |
| [#4317](https://github.com/OpenXiangShan/XiangShan/pull/4317) | feat(RAS): change the stall mechanism upon return stack overflow to dynamically disable the return stack. | my-mayfly | 2025-02-25 | - | `-` | 0 |
| [#4307](https://github.com/OpenXiangShan/XiangShan/pull/4307) | fix(xtval): fix xtval when raise intr | sinceforYy | 2025-02-21 | - | `-` | 0 |
| [#4304](https://github.com/OpenXiangShan/XiangShan/pull/4304) | fix(uncache): correct the indexes | Maxpicca-Li | 2025-02-21 | - | `-` | 0 |
| [#4301](https://github.com/OpenXiangShan/XiangShan/pull/4301) | fix(IFU): handle uncache corrupt | ngc7331 | 2025-02-20 | - | `-` | 0 |
| [#4300](https://github.com/OpenXiangShan/XiangShan/pull/4300) | fix(LoadQueueUncache): exhaust the various cases of flush | Maxpicca-Li | 2025-02-20 | - | `-` | 1 |
| [#4298](https://github.com/OpenXiangShan/XiangShan/pull/4298) | submodule(CoupledL2): bump CoupledL2 | Ma-YX | 2025-02-20 | - | `-` | 0 |
| [#4292](https://github.com/OpenXiangShan/XiangShan/pull/4292) | fix(LoadUnit): corrupt should be triggered on valid mshr | Anzooooo | 2025-02-19 | - | `-` | 0 |
| [#4288](https://github.com/OpenXiangShan/XiangShan/pull/4288) | chore(dispatch): remove useless code and files | xiaofeibao-xjtu | 2025-02-19 | - | `-` | 0 |
| [#4285](https://github.com/OpenXiangShan/XiangShan/pull/4285) | fix(MainPipe): fix `s1_way_en` generate logic when ecc inject occur | cz4e | 2025-02-18 | - | `-` | 1 |
| [#4276](https://github.com/OpenXiangShan/XiangShan/issues/4276) | clang-14: error: clang frontend command failed with exit code 139 (use -v to see invocation) | zxl819 | 2025-02-14 | - | `-` | 0 |
| [#4275](https://github.com/OpenXiangShan/XiangShan/pull/4275) | fix(uncache): uncache load fails to replay | Maxpicca-Li | 2025-02-14 | - | `-` | 0 |
| [#4272](https://github.com/OpenXiangShan/XiangShan/pull/4272) | fix(DCache): pass `amo_cmp` to MSHR when cas req miss | bosscharlie | 2025-02-14 | - | `-` | 0 |
| [#4271](https://github.com/OpenXiangShan/XiangShan/issues/4271) | XSCore.scala:66:33: reference to MemBlock is ambiguous; | zxl819 | 2025-02-14 | - | `-` | 0 |
| [#4269](https://github.com/OpenXiangShan/XiangShan/pull/4269) | fix(PreDecode): fix fixedTaken for jalr | TheKiteRunner24 | 2025-02-13 | - | `-` | 0 |
| [#4268](https://github.com/OpenXiangShan/XiangShan/pull/4268) | fix(uncache): avoid merging the corner cases | Maxpicca-Li | 2025-02-12 | - | `-` | 0 |
| [#4267](https://github.com/OpenXiangShan/XiangShan/pull/4267) | submodule(ready-to-run): Bump ready-to-run | wissygh | 2025-02-12 | - | `-` | 0 |
| [#4266](https://github.com/OpenXiangShan/XiangShan/pull/4266) | fix(TLB): onlyS1 scene should not consider G-stage access fault | good-circle | 2025-02-12 | - | `-` | 0 |
| [#4263](https://github.com/OpenXiangShan/XiangShan/pull/4263) | fix(LoadUnit): fix  misalign load wrong wakeup | cz4e | 2025-02-11 | - | `-` | 0 |
| [#4262](https://github.com/OpenXiangShan/XiangShan/pull/4262) | fix(LSU): fix cbo instr exceptions and implementation | Anzooooo | 2025-02-11 | - | `-` | 0 |
| [#4257](https://github.com/OpenXiangShan/XiangShan/pull/4257) | fix(perfcct): fix the bug of some instructions being lost. | sinceforYy | 2025-02-10 | - | `-` | 0 |
| [#4256](https://github.com/OpenXiangShan/XiangShan/pull/4256) | fix(Mcontrol6): fix writing mcontrol6.dmode for trigger chain | wissygh | 2025-02-10 | - | `-` | 0 |
| [#4253](https://github.com/OpenXiangShan/XiangShan/pull/4253) | fix(MMU): Should consider s2xlate when calculate page level | good-circle | 2025-02-09 | - | `-` | 0 |
| [#4252](https://github.com/OpenXiangShan/XiangShan/pull/4252) | fix(L1TLB): Should consider s2xlate when refill Svnapot | good-circle | 2025-02-09 | - | `-` | 0 |
| [#4244](https://github.com/OpenXiangShan/XiangShan/pull/4244) | fix(vfalu): fix bug of allFFlagsEn when lastUop is reduction unorder sum | xiaofeibao-xjtu | 2025-02-05 | - | `-` | 0 |
| [#4242](https://github.com/OpenXiangShan/XiangShan/issues/4242) | Possible bug in statistical corrector | castleberrysam | 2025-02-02 | - | `-` | 0 |
| [#4239](https://github.com/OpenXiangShan/XiangShan/pull/4239) | fix(LSU): fix misalign store exception logic | Anzooooo | 2025-01-26 | - | `-` | 0 |
| [#4235](https://github.com/OpenXiangShan/XiangShan/pull/4235) | fix(Config): add the 'L3CacheCtrl' address space permission back | Anzooooo | 2025-01-24 | - | `-` | 0 |
| [#4234](https://github.com/OpenXiangShan/XiangShan/pull/4234) | fix(IFU): add range checking for instruction blocks containing jalr | TheKiteRunner24 | 2025-01-24 | - | `-` | 0 |
| [#4232](https://github.com/OpenXiangShan/XiangShan/pull/4232) | fix(RAS): adjust the signal judgment of isCall and isRet during redirection | my-mayfly | 2025-01-23 | - | `-` | 0 |
| [#4228](https://github.com/OpenXiangShan/XiangShan/pull/4228) | fix(StoreUnit): misaligned store need check `RAW` | Anzooooo | 2025-01-23 | - | `-` | 0 |
| [#4227](https://github.com/OpenXiangShan/XiangShan/pull/4227) | fix(StoreMisalignBuffer): fix state transition when writeback | Anzooooo | 2025-01-23 | - | `-` | 0 |
| [#4226](https://github.com/OpenXiangShan/XiangShan/pull/4226) | fix(LoadUnit): `dcache_kill` if `prf_wr` has no permissions | Anzooooo | 2025-01-23 | - | `-` | 0 |
| [#4223](https://github.com/OpenXiangShan/XiangShan/pull/4223) | timing(frontend): remove bad timing clock gating | eastonman | 2025-01-22 | - | `-` | 0 |
| [#4216](https://github.com/OpenXiangShan/XiangShan/pull/4216) | timing(ittage): optimize the timing of the ittage path for reading the jump address | my-mayfly | 2025-01-21 | - | `-` | 0 |
| [#4211](https://github.com/OpenXiangShan/XiangShan/pull/4211) | feat(Zawrs): support Zawrs extension | Tang-Haojin | 2025-01-20 | - | `-` | 0 |
| [#4202](https://github.com/OpenXiangShan/XiangShan/pull/4202) | fix(L2TLB): reset tlbCounter when flush | good-circle | 2025-01-20 | - | `-` | 0 |
| [#4198](https://github.com/OpenXiangShan/XiangShan/pull/4198) | feat(busytable): support eliminate old vd in new dispatch | Ziyue-Zhang | 2025-01-17 | - | `-` | 0 |
| [#4197](https://github.com/OpenXiangShan/XiangShan/pull/4197) | fix bug of snptSelect and bump yunsuan | xiaofeibao-xjtu | 2025-01-17 | - | `-` | 0 |
| [#4195](https://github.com/OpenXiangShan/XiangShan/pull/4195) | fix(PTWCache): avoid X-prop of spRefill | good-circle | 2025-01-17 | - | `-` | 0 |
| [#4194](https://github.com/OpenXiangShan/XiangShan/pull/4194) | fix(mnret): add the missing mnret output connection | lewislzh | 2025-01-17 | - | `-` | 0 |
| [#4191](https://github.com/OpenXiangShan/XiangShan/pull/4191) | fix(L2TLB): Fix stuck caused by MissQueue full | good-circle | 2025-01-17 | - | `-` | 0 |
| [#4190](https://github.com/OpenXiangShan/XiangShan/issues/4190) | KunminghuV2Config doesn't seem to dual issue vector instructions | camel-cdr | 2025-01-16 | - | `-` | 3 |
| [#4181](https://github.com/OpenXiangShan/XiangShan/pull/4181) | fix(VFALU): fix bug of f16FirstFoldMaskUnorder when fold to 1/2 | xiaofeibao-xjtu | 2025-01-16 | - | `-` | 0 |
| [#4174](https://github.com/OpenXiangShan/XiangShan/pull/4174) | fix(PTWRepeater): use PriorityMux for not one-hot vector | good-circle | 2025-01-14 | - | `-` | 0 |
| [#4173](https://github.com/OpenXiangShan/XiangShan/pull/4173) | timing(ICache): move mshr_resp selector 1 cycle ahead | ngc7331 | 2025-01-14 | - | `-` | 2 |
| [#4166](https://github.com/OpenXiangShan/XiangShan/pull/4166) | fix(aia): add the missing AIA-related permission checks | NewPaulWalker | 2025-01-13 | - | `-` | 0 |
| [#4164](https://github.com/OpenXiangShan/XiangShan/pull/4164) | feat(custom, csr): add two custom CSRs mcorepwr and mflushpwr to control power | NewPaulWalker | 2025-01-13 | - | `-` | 0 |
| [#4157](https://github.com/OpenXiangShan/XiangShan/pull/4157) | fix(CSR): fix xTIP update in sstcIRGen | sinceforYy | 2025-01-10 | - | `-` | 0 |
| [#4153](https://github.com/OpenXiangShan/XiangShan/pull/4153) | fix(rob): fix needflush when rob has redirect | sinceforYy | 2025-01-09 | - | `-` | 0 |
| [#4146](https://github.com/OpenXiangShan/XiangShan/pull/4146) | feat(exception): divide the exceptions raised from CSR access into different sources. | NewPaulWalker | 2025-01-07 | - | `-` | 0 |
| [#4145](https://github.com/OpenXiangShan/XiangShan/pull/4145) | feat(CSR): set init 0 for htimedelta csr | sinceforYy | 2025-01-07 | - | `-` | 0 |
| [#4139](https://github.com/OpenXiangShan/XiangShan/pull/4139) | fix(StoreQueue): remove the incorrect redirect logic | Anzooooo | 2025-01-06 | - | `-` | 0 |
| [#4134](https://github.com/OpenXiangShan/XiangShan/pull/4134) | feat(DM, hartReset): support `hartReset` which could reset selected harts | wissygh | 2025-01-06 | - | `-` | 0 |
| [#4132](https://github.com/OpenXiangShan/XiangShan/pull/4132) | fix(Unprivileged): wait a cycle to update `time` when `nextV =/= v` | Tang-Haojin | 2025-01-05 | - | `-` | 0 |
| [#4131](https://github.com/OpenXiangShan/XiangShan/pull/4131) | fix(Rename): fuse lui-load only if `rfWen` of lui is true | Tang-Haojin | 2025-01-05 | - | `-` | 0 |
| [#4129](https://github.com/OpenXiangShan/XiangShan/issues/4129) | Unexpected behavior with LUI and FLD instructions on zero register | fly-1011 | 2025-01-04 | - | `-` | 1 |
| [#4128](https://github.com/OpenXiangShan/XiangShan/pull/4128) | feat(Backend): Accelerate CSRR instructions by performing out-of-order execution on most CSRs | Squareless-XD | 2025-01-04 | - | `-` | 0 |
| [#4122](https://github.com/OpenXiangShan/XiangShan/pull/4122) | feat(TopDown): add TopDown PMU Events | sinceforYy | 2025-01-03 | - | `-` | 0 |
| [#4120](https://github.com/OpenXiangShan/XiangShan/issues/4120) | Inconsistency behavior between xiangshan and NEMU after setting PMP | ha0lyu | 2025-01-02 | - | `-` | 0 |
| [#4119](https://github.com/OpenXiangShan/XiangShan/pull/4119) | timing(CSR): using addr/wdata after 1 cycle for writing frontend and memory | sinceforYy | 2025-01-02 | - | `-` | 0 |
| [#4118](https://github.com/OpenXiangShan/XiangShan/pull/4118) | fix(redirectGen): fix bug of csr's cfiUpdate | xiaofeibao-xjtu | 2025-01-02 | - | `-` | 0 |
| [#4114](https://github.com/OpenXiangShan/XiangShan/pull/4114) | feat(commit): complete rewrite of commit mechanism | Yan-Muzi | 2024-12-31 | - | `-` | 0 |
| [#4112](https://github.com/OpenXiangShan/XiangShan/pull/4112) | fix(ICacheMissUnit): clear corrupt_r when response is sent to MainPipe | ngc7331 | 2024-12-30 | - | `-` | 0 |
| [#4108](https://github.com/OpenXiangShan/XiangShan/pull/4108) | fix(FusionDecoder): instructions may be HINT cannot be fused | Tang-Haojin | 2024-12-30 | - | `-` | 0 |
| [#4103](https://github.com/OpenXiangShan/XiangShan/pull/4103) | fix(VLSU): `mergebuffer` threshold was added | Anzooooo | 2024-12-29 | - | `-` | 0 |
| [#4102](https://github.com/OpenXiangShan/XiangShan/pull/4102) | fix(LoadUnit): `fastReplay` can only happen once | Anzooooo | 2024-12-29 | - | `-` | 0 |
| [#4101](https://github.com/OpenXiangShan/XiangShan/pull/4101) | fix(LoadUnit): fix Vector priority related issues | Anzooooo | 2024-12-29 | - | `-` | 0 |
| [#4096](https://github.com/OpenXiangShan/XiangShan/pull/4096) | fix(LQUncache): fix a potential deadblock when enqueue | Maxpicca-Li | 2024-12-27 | - | `-` | 0 |
| [#4090](https://github.com/OpenXiangShan/XiangShan/pull/4090) | fix(PTW): incorrect GPF due to timing mismatch | cebarobot | 2024-12-25 | - | `-` | 0 |
| [#4088](https://github.com/OpenXiangShan/XiangShan/pull/4088) | ppa(backend) | xiaofeibao-xjtu | 2024-12-25 | - | `-` | 0 |
| [#4086](https://github.com/OpenXiangShan/XiangShan/pull/4086) | fix(LoadQueueRAR): aligning the size of `RARSize` to `VLQSize` | Anzooooo | 2024-12-24 | - | `-` | 0 |
| [#4085](https://github.com/OpenXiangShan/XiangShan/pull/4085) | fix(LoadUnit): fix Load misalign related bugs | Anzooooo | 2024-12-24 | - | `-` | 0 |
| [#4084](https://github.com/OpenXiangShan/XiangShan/pull/4084) | fix(MemBlock): fix overflow during lsqptr calculation | Anzooooo | 2024-12-24 | - | `-` | 0 |
| [#4079](https://github.com/OpenXiangShan/XiangShan/pull/4079) | fix(CSR): flush CSR when inst redirect | sinceforYy | 2024-12-23 | - | `-` | 0 |
| [#4077](https://github.com/OpenXiangShan/XiangShan/pull/4077) | fix(StoreMisalignBuffer): crosspage can only be replaced when `s_idle` | Anzooooo | 2024-12-23 | - | `-` | 0 |
| [#4075](https://github.com/OpenXiangShan/XiangShan/pull/4075) | timing(backend): rob and vecExcpMod | xiaofeibao-xjtu | 2024-12-23 | - | `-` | 0 |
| [#4072](https://github.com/OpenXiangShan/XiangShan/pull/4072) | fix(FTQ): start of the first instruction in an entry | Yan-Muzi | 2024-12-22 | - | `-` | 0 |
| [#4070](https://github.com/OpenXiangShan/XiangShan/pull/4070) | fix(hideleg): fix the read value of the LCOFI bit of hideleg. | NewPaulWalker | 2024-12-20 | - | `-` | 0 |
| [#4069](https://github.com/OpenXiangShan/XiangShan/pull/4069) | submodule(yunsuan): bump yunsuan to fix VFMA/FMA area | lewislzh | 2024-12-19 | - | `-` | 0 |
| [#4067](https://github.com/OpenXiangShan/XiangShan/pull/4067) | timing(Rob): modify selection from robentries to robDeqGroup | wissygh | 2024-12-19 | - | `-` | 0 |
| [#4066](https://github.com/OpenXiangShan/XiangShan/pull/4066) | timing(Vector,Decode): judge isComplex by inst encoding directly  | xiaofeibao-xjtu | 2024-12-19 | - | `-` | 0 |
| [#4064](https://github.com/OpenXiangShan/XiangShan/pull/4064) | fix(NewCSR): fix the error of trap entry PC in vs mode interrupts | lewislzh | 2024-12-19 | - | `-` | 0 |
| [#4063](https://github.com/OpenXiangShan/XiangShan/pull/4063) | area(EXU): add parameter `needCopySrc` in FuConfig | wissygh | 2024-12-18 | - | `-` | 0 |
| [#4057](https://github.com/OpenXiangShan/XiangShan/pull/4057) | fix(LoadUnit): fix trigger exception when writeback and wakeup logic | Anzooooo | 2024-12-17 | - | `-` | 0 |
| [#4054](https://github.com/OpenXiangShan/XiangShan/pull/4054) | fix(dbltrp): fix sdt write and sdt/sie interaction logic | lewislzh | 2024-12-16 | - | `-` | 0 |
| [#4053](https://github.com/OpenXiangShan/XiangShan/pull/4053) | fix(MemBlock): fix misaligned exception and remove redundant reg from `SQ` | Anzooooo | 2024-12-16 | - | `-` | 0 |
| [#4049](https://github.com/OpenXiangShan/XiangShan/pull/4049) | ppa(backend) | xiaofeibao-xjtu | 2024-12-16 | - | `-` | 0 |
| [#4048](https://github.com/OpenXiangShan/XiangShan/pull/4048) | fix(RAS): bos pointer needs to be updated when the instruction is committed | my-mayfly | 2024-12-15 | - | `-` | 0 |
| [#4047](https://github.com/OpenXiangShan/XiangShan/issues/4047) | Load access fault exception related issue | fly-1011 | 2024-12-15 | - | `-` | 2 |
| [#4046](https://github.com/OpenXiangShan/XiangShan/issues/4046) | mstatus.sdt has different | fly-1011 | 2024-12-15 | - | `-` | 1 |
| [#4044](https://github.com/OpenXiangShan/XiangShan/pull/4044) | feat(ICache): ECC error injection | ngc7331 | 2024-12-15 | - | `-` | 2 |
| [#4042](https://github.com/OpenXiangShan/XiangShan/issues/4042) | The Value of the mtval Register Differs When the Address is Misaligned | fly-1011 | 2024-12-15 | - | `-` | 1 |
| [#4033](https://github.com/OpenXiangShan/XiangShan/pull/4033) | area(IssueQueue): encode exuOH as UInt to reduce storage | sinsanction | 2024-12-12 | - | `-` | 0 |
| [#4028](https://github.com/OpenXiangShan/XiangShan/pull/4028) | fix(vset): simplify vl compute in vsetrvfwvf module | Ziyue-Zhang | 2024-12-11 | - | `-` | 0 |
| [#4025](https://github.com/OpenXiangShan/XiangShan/pull/4025) | timing(decode): dequeue uops by indexing in order in DecodeUnitComp | wissygh | 2024-12-11 | - | `-` | 0 |
| [#4024](https://github.com/OpenXiangShan/XiangShan/pull/4024) | fix(uopsplit): set vector instructions never use simple split type | Ziyue-Zhang | 2024-12-11 | - | `-` | 0 |
| [#4020](https://github.com/OpenXiangShan/XiangShan/issues/4020) | Certain instructions cannot cause exceptions | fly-1011 | 2024-12-10 | - | `-` | 2 |
| [#4004](https://github.com/OpenXiangShan/XiangShan/issues/4004) | Inconsistent values ​​of mip registers | fly-1011 | 2024-12-09 | - | `-` | 1 |
| [#4002](https://github.com/OpenXiangShan/XiangShan/pull/4002) | fix(tage): avoid read/write to the same address in the tage bt table. | sleep-zzz | 2024-12-08 | - | `-` | 0 |
| [#3996](https://github.com/OpenXiangShan/XiangShan/pull/3996) | fix(ICache,ITLB): also flush itlb pipe when prefetchPipe s1_flush | ngc7331 | 2024-12-07 | - | `-` | 0 |
| [#3991](https://github.com/OpenXiangShan/XiangShan/pull/3991) | fix(interrupt): `Vset` should not respond to interrupts | Anzooooo | 2024-12-05 | - | `-` | 0 |
| [#3990](https://github.com/OpenXiangShan/XiangShan/pull/3990) | fix(vecExcpInfo): do not set `vecExcpInfo` if exception is an interrupt | Tang-Haojin | 2024-12-05 | - | `-` | 0 |
| [#3989](https://github.com/OpenXiangShan/XiangShan/pull/3989) | fix(csr, imsic): sync CSR access imsic | sinceforYy | 2024-12-05 | - | `-` | 0 |
| [#3986](https://github.com/OpenXiangShan/XiangShan/pull/3986) | fix(Parameters): add missing `ISAExtensions` | Tang-Haojin | 2024-12-05 | - | `-` | 0 |
| [#3985](https://github.com/OpenXiangShan/XiangShan/pull/3985) | fix(TLB): avoid refill when one cycle before need_gpa | good-circle | 2024-12-04 | - | `-` | 0 |
| [#3981](https://github.com/OpenXiangShan/XiangShan/pull/3981) | submodule(ready-to-run): spike rebase upstream master | lewislzh | 2024-12-04 | - | `-` | 0 |
| [#3979](https://github.com/OpenXiangShan/XiangShan/issues/3979) | `flh` instruction does not perform sign extension | youzi27 | 2024-12-03 | - | `-` | 1 |
| [#3978](https://github.com/OpenXiangShan/XiangShan/pull/3978) | fix(Smstateen): fix access check when Smstateen extension enable. | NewPaulWalker | 2024-12-03 | - | `-` | 0 |
| [#3966](https://github.com/OpenXiangShan/XiangShan/pull/3966) | fix(CSR): fix shadow writing for custom PMA CSRs not in `csrRwMap` | huxuan0307 | 2024-11-30 | - | `-` | 0 |
| [#3965](https://github.com/OpenXiangShan/XiangShan/pull/3965) | fix(vector): do not set vs.dirty for some type of vecInsts | Tang-Haojin | 2024-11-30 | - | `-` | 0 |
| [#3964](https://github.com/OpenXiangShan/XiangShan/pull/3964) | fix(TLB): avoid freeze when GPF occurs | cebarobot | 2024-11-29 | - | `-` | 0 |
| [#3963](https://github.com/OpenXiangShan/XiangShan/pull/3963) | fix(IFU): mark mmio mismatch exception only on the second line | ngc7331 | 2024-11-29 | - | `-` | 0 |
| [#3961](https://github.com/OpenXiangShan/XiangShan/pull/3961) | area(decode): move vecExceptionGen to complex docoder | Ziyue-Zhang | 2024-11-29 | - | `-` | 0 |
| [#3959](https://github.com/OpenXiangShan/XiangShan/issues/3959) | Unable to Handle Specific Sequences of Illegal Instructions | youzi27 | 2024-11-29 | - | `-` | 1 |
| [#3958](https://github.com/OpenXiangShan/XiangShan/pull/3958) | feat(Backend, MemBlock): add support for Zacas extension | linjuanZ | 2024-11-29 | - | `-` | 0 |
| [#3955](https://github.com/OpenXiangShan/XiangShan/pull/3955) | fix(dbltrp): fix sdt/dte interaction logic  | lewislzh | 2024-11-28 | - | `-` | 0 |
| [#3954](https://github.com/OpenXiangShan/XiangShan/issues/3954) | The UF flag in the fcsr register is different | fly-1011 | 2024-11-28 | - | `-` | 1 |
| [#3953](https://github.com/OpenXiangShan/XiangShan/pull/3953) | feat(isa): add isa-base and isa-extensions to param and dts | Tang-Haojin | 2024-11-28 | - | `-` | 0 |
| [#3952](https://github.com/OpenXiangShan/XiangShan/issues/3952) | fadd.h instruction operation results are different | fly-1011 | 2024-11-28 | - | `-` | 0 |
| [#3951](https://github.com/OpenXiangShan/XiangShan/issues/3951) | Sign bit handling error | fly-1011 | 2024-11-28 | - | `-` | 0 |
| [#3950](https://github.com/OpenXiangShan/XiangShan/pull/3950) | Frontend: modify the code related to configuration parameters | my-mayfly | 2024-11-27 | - | `-` | 0 |
| [#3948](https://github.com/OpenXiangShan/XiangShan/pull/3948) | fix(decode): not eliminate old vd when vstart is not zero | Ziyue-Zhang | 2024-11-27 | - | `-` | 0 |
| [#3946](https://github.com/OpenXiangShan/XiangShan/pull/3946) | timing(csr): add 1 cycle to csr read/write and select highest interrupt priority | sinceforYy | 2024-11-27 | - | `-` | 0 |
| [#3944](https://github.com/OpenXiangShan/XiangShan/pull/3944) | feat(IFU,Svpbmt): allow speculative fetch in pbmt.NC (idempotent) spaces | ngc7331 | 2024-11-26 | - | `-` | 0 |
| [#3942](https://github.com/OpenXiangShan/XiangShan/issues/3942) | Incorrect Behavior of `hvip` Bits 13 to 15 | youzi27 | 2024-11-26 | - | `-` | 1 |
| [#3939](https://github.com/OpenXiangShan/XiangShan/pull/3939) | Bump yunsuan | HeiHuDie | 2024-11-26 | - | `-` | 0 |
| [#3937](https://github.com/OpenXiangShan/XiangShan/issues/3937) | `mip.STIP` Not Set When `stimecmp` is Less Than `time` | youzi27 | 2024-11-26 | - | `-` | 1 |
| [#3935](https://github.com/OpenXiangShan/XiangShan/pull/3935) | area(ittage): Split the Target into Region and Offset, with Region stored in registers and Offset still using SRAM | sleep-zzz | 2024-11-26 | - | `-` | 0 |
| [#3934](https://github.com/OpenXiangShan/XiangShan/issues/3934) | Unexpected Modification of `xstatus` WPRI Field During `menvcfg` Reads/Writes | youzi27 | 2024-11-25 | - | `-` | 1 |
| [#3932](https://github.com/OpenXiangShan/XiangShan/pull/3932) | Bump yunsuan | HeiHuDie | 2024-11-25 | - | `-` | 0 |
| [#3927](https://github.com/OpenXiangShan/XiangShan/issues/3927) | Mismatch in fld Instruction Execution Between XS and REF | youzi27 | 2024-11-25 | - | `-` | 1 |
| [#3919](https://github.com/OpenXiangShan/XiangShan/issues/3919) | Decode related issue | MAX-max1118 | 2024-11-23 | - | `-` | 1 |
| [#3918](https://github.com/OpenXiangShan/XiangShan/pull/3918) | submodule(ready-to-run): bump ready-to-run | NewPaulWalker | 2024-11-22 | - | `-` | 0 |
| [#3909](https://github.com/OpenXiangShan/XiangShan/pull/3909) | fix(vlbusytable): fix int vl writeback wrong use vf vl writeback | Ziyue-Zhang | 2024-11-21 | - | `-` | 0 |
| [#3899](https://github.com/OpenXiangShan/XiangShan/pull/3899) | feat(ICache): re-fetch data from L2 if ECC error is detected | ngc7331 | 2024-11-19 | - | `-` | 0 |
| [#3898](https://github.com/OpenXiangShan/XiangShan/pull/3898) | fix(dret): fix update of privstate in dretevent | wissygh | 2024-11-19 | - | `-` | 0 |
| [#3894](https://github.com/OpenXiangShan/XiangShan/pull/3894) | fix(vnclip): use uimm instead of imm for vnclip_wi instructions | Ziyue-Zhang | 2024-11-19 | - | `-` | 0 |
| [#3889](https://github.com/OpenXiangShan/XiangShan/pull/3889) | feat(frontend): add ClockGate at frontend SRAMTemplate | Lawrence-ID | 2024-11-18 | - | `-` | 0 |
| [#3886](https://github.com/OpenXiangShan/XiangShan/pull/3886) | fix(RVCDecoder): add check for zcb reserved space | TheKiteRunner24 | 2024-11-18 | - | `-` | 0 |
| [#3885](https://github.com/OpenXiangShan/XiangShan/pull/3885) | fix(critical-error): critical-error pass early then trap | lewislzh | 2024-11-18 | - | `-` | 0 |
| [#3884](https://github.com/OpenXiangShan/XiangShan/pull/3884) | fix(LoadQueueReplay): fix enq mask generate when redirect | cz4e | 2024-11-18 | - | `-` | 0 |
| [#3879](https://github.com/OpenXiangShan/XiangShan/issues/3879) | c.unimp instruction problem | fly-1011 | 2024-11-16 | - | `-` | 1 |
| [#3878](https://github.com/OpenXiangShan/XiangShan/issues/3878) | `mcause` is different between xiangshan and spike when execute `sh`. | ha0lyu | 2024-11-16 | - | `-` | 5 |
| [#3875](https://github.com/OpenXiangShan/XiangShan/pull/3875) | fix(xtval): fix selection of tval for trap | wissygh | 2024-11-15 | - | `-` | 0 |
| [#3873](https://github.com/OpenXiangShan/XiangShan/pull/3873) | fix(IFU): check consistency of mmio states | ngc7331 | 2024-11-14 | - | `-` | 0 |
| [#3871](https://github.com/OpenXiangShan/XiangShan/pull/3871) | fix(TLB): incorrect tval2 info when IGPF occurs | cebarobot | 2024-11-13 | - | `-` | 0 |
| [#3870](https://github.com/OpenXiangShan/XiangShan/pull/3870) | submodule(ready-to-run): bump spike and nemu; spike support dbltrp | lewislzh | 2024-11-13 | - | `-` | 0 |
| [#3860](https://github.com/OpenXiangShan/XiangShan/issues/3860) | Wrong `mstatus, mtval` value when Xiangshan executes an illegal instruction. | ha0lyu | 2024-11-11 | - | `-` | 2 |
| [#3859](https://github.com/OpenXiangShan/XiangShan/pull/3859) | fix(CSR,RVC): c.fp instrs should be illegal when fs is off | ngc7331 | 2024-11-11 | - | `-` | 0 |
| [#3856](https://github.com/OpenXiangShan/XiangShan/issues/3856) | There is a problem with the SEIP bit handling in the sip register | MAX-max1118 | 2024-11-11 | - | `-` | 2 |
| [#3850](https://github.com/OpenXiangShan/XiangShan/pull/3850) | fix(BPU): fix potential bug on s2_fire_dup | Jerry-Tianchen | 2024-11-09 | - | `-` | 0 |
| [#3848](https://github.com/OpenXiangShan/XiangShan/pull/3848) | submodule(difftest): expand trapcode to 64bit to fix XStrap | lewislzh | 2024-11-08 | - | `-` | 0 |
| [#3847](https://github.com/OpenXiangShan/XiangShan/pull/3847) | feat(vl busytable): support eliminate old vd when read vl's state | Ziyue-Zhang | 2024-11-07 | - | `-` | 0 |
| [#3845](https://github.com/OpenXiangShan/XiangShan/pull/3845) | fix(aes): fix exception check for aes64ks1i. | NewPaulWalker | 2024-11-07 | - | `-` | 0 |
| [#3844](https://github.com/OpenXiangShan/XiangShan/issues/3844) | Handling Inconsistency in Load Address Misaligned and Load Access Fault Exceptions for Specific Instructions | fly-1011 | 2024-11-07 | - | `-` | 0 |
| [#3843](https://github.com/OpenXiangShan/XiangShan/pull/3843) | Feat(trace): support trace core interface | wissygh | 2024-11-07 | - | `-` | 0 |
| [#3842](https://github.com/OpenXiangShan/XiangShan/issues/3842) | make verilog fail | sasakiakaya | 2024-11-06 | - | `-` | 0 |
| [#3841](https://github.com/OpenXiangShan/XiangShan/pull/3841) | fix(zfh): flh/fsh should raise illegal exception when fs is off. | NewPaulWalker | 2024-11-06 | - | `-` | 0 |
| [#3840](https://github.com/OpenXiangShan/XiangShan/pull/3840) | feat(zvfh,zfh): add F16 support | HeiHuDie | 2024-11-06 | - | `-` | 0 |
| [#3839](https://github.com/OpenXiangShan/XiangShan/issues/3839) | When the fs field in the mstatus register is 0, executing instructions such as flh will not cause an illegal instruction exception | fly-1011 | 2024-11-06 | - | `-` | 2 |
| [#3838](https://github.com/OpenXiangShan/XiangShan/issues/3838) | Zknd extension `aes64ks1i` decode error. | ha0lyu | 2024-11-06 | - | `-` | 2 |
| [#3837](https://github.com/OpenXiangShan/XiangShan/issues/3837) | Potential Bug in BPU on s2_fire_dup? | Jerry-Tianchen | 2024-11-05 | - | `-` | 2 |
| [#3835](https://github.com/OpenXiangShan/XiangShan/pull/3835) | fix(dbltrp):critical-error is not treated as diff error | lewislzh | 2024-11-04 | - | `-` | 0 |
| [#3833](https://github.com/OpenXiangShan/XiangShan/pull/3833) | area(Rob): remove RobEntryBundle's parameters related to perfCount | sinceforYy | 2024-11-04 | - | `-` | 0 |
| [#3830](https://github.com/OpenXiangShan/XiangShan/issues/3830) | [Bug Report] Load access fault and store_address_misaligned cause processor to deadlock | chenhychen | 2024-11-04 | - | `-` | 1 |
| [#3829](https://github.com/OpenXiangShan/XiangShan/issues/3829) | `mcause` error in difftest when `raise intr cause NO: 4` | ha0lyu | 2024-11-02 | - | `-` | 3 |
| [#3828](https://github.com/OpenXiangShan/XiangShan/pull/3828) | fix(step): fix step for exception. | wissygh | 2024-11-01 | - | `-` | 0 |
| [#3827](https://github.com/OpenXiangShan/XiangShan/pull/3827) | fix(mip): add otherwise when wen mip and mip.seip is alias of mvip.seip when mvien.seie = 0 | sinceforYy | 2024-11-01 | - | `-` | 0 |
| [#3826](https://github.com/OpenXiangShan/XiangShan/pull/3826) | fix(CSR): Debug Interrupt is not invisible to M-mode. | wissygh | 2024-10-31 | - | `-` | 0 |
| [#3823](https://github.com/OpenXiangShan/XiangShan/pull/3823) | feat(zihintpause): support zihintpause | lewislzh | 2024-10-31 | - | `-` | 0 |
| [#3822](https://github.com/OpenXiangShan/XiangShan/pull/3822) | fix(misalign): fix gpaddr of misalign loads when onlyStage2 | good-circle | 2024-10-31 | - | `-` | 0 |
| [#3818](https://github.com/OpenXiangShan/XiangShan/pull/3818) | build(version): inject git commit SHA to hardware CommitIDModule | huxuan0307 | 2024-10-30 | - | `-` | 0 |
| [#3816](https://github.com/OpenXiangShan/XiangShan/pull/3816) | submodule(yunsuan): bump yunsuan | sinceforYy | 2024-10-30 | - | `-` | 0 |
| [#3814](https://github.com/OpenXiangShan/XiangShan/pull/3814) | submodule(CoupledL2): fix bug of CMO release data | cailuoshan | 2024-10-30 | - | `-` | 0 |
| [#3813](https://github.com/OpenXiangShan/XiangShan/issues/3813) | Exception priority mismatch between xiangshan and spike | ha0lyu | 2024-10-30 | - | `-` | 2 |
| [#3812](https://github.com/OpenXiangShan/XiangShan/pull/3812) | fix(intr): set the sequence of interrupt in different mode | sinceforYy | 2024-10-30 | - | `-` | 0 |
| [#3809](https://github.com/OpenXiangShan/XiangShan/pull/3809) | fix(MisalignBuffer): Use RegEnable in datapath to avoid xprop | good-circle | 2024-10-29 | - | `-` | 0 |
| [#3803](https://github.com/OpenXiangShan/XiangShan/pull/3803) | fix(AtomicsUnit): Assert `atom_override_xtval` when trigger fire. | wissygh | 2024-10-29 | - | `-` | 0 |
| [#3795](https://github.com/OpenXiangShan/XiangShan/pull/3795) | fix(CSR): correct the width of PC pgaddr for inst fetch exception | cebarobot | 2024-10-28 | - | `-` | 0 |
| [#3793](https://github.com/OpenXiangShan/XiangShan/pull/3793) | feat(dbltrp) : add support for critical error  | lewislzh | 2024-10-28 | - | `-` | 0 |
| [#3791](https://github.com/OpenXiangShan/XiangShan/pull/3791) | style(frontend): manually wrap some line | eastonman | 2024-10-28 | - | `-` | 0 |
| [#3789](https://github.com/OpenXiangShan/XiangShan/pull/3789) | feat(Ss/Smdbltrp) : Support RISC-V Ss/Smdbltrp Extension | lewislzh | 2024-10-28 | - | `-` | 0 |
| [#3787](https://github.com/OpenXiangShan/XiangShan/pull/3787) | fix(ICache): cancel prefetch when there is exception from backend | Yan-Muzi | 2024-10-25 | - | `-` | 0 |
| [#3786](https://github.com/OpenXiangShan/XiangShan/pull/3786) | fix(CSR): fix dcsr to support `stopcount`, `stoptime`, `nmip` and `cetrig` | wissygh | 2024-10-25 | - | `-` | 0 |
| [#3784](https://github.com/OpenXiangShan/XiangShan/pull/3784) | fix(ICache): use PriorityMux instead of Mux1H for io.error | ngc7331 | 2024-10-25 | - | `-` | 0 |
| [#3778](https://github.com/OpenXiangShan/XiangShan/pull/3778) | fix(VecExcp): isEnqExcp should be set 0 when writeback has older exception | huxuan0307 | 2024-10-23 | - | `-` | 0 |
| [#3772](https://github.com/OpenXiangShan/XiangShan/pull/3772) | fix(VSegmentUnit): fix VSegment trigger logic | wissygh | 2024-10-22 | - | `-` | 0 |
| [#3771](https://github.com/OpenXiangShan/XiangShan/pull/3771) | fix(csr): fix intermediate storage reg for EX_II and EX_VI | sinceforYy | 2024-10-21 | - | `-` | 0 |
| [#3769](https://github.com/OpenXiangShan/XiangShan/pull/3769) | fix(Ebreak): use isPcBkpt to hold exception raised by ebreak | huxuan0307 | 2024-10-21 | - | `-` | 0 |
| [#3762](https://github.com/OpenXiangShan/XiangShan/pull/3762) | fix(Breakpoint): memory trigger set {m\|s\|vs}tval with faulting address | huxuan0307 | 2024-10-17 | - | `-` | 0 |
| [#3759](https://github.com/OpenXiangShan/XiangShan/pull/3759) | fix(misalign): fix misaligned HLV and HLVX | happy-lx | 2024-10-17 | - | `-` | 0 |
| [#3758](https://github.com/OpenXiangShan/XiangShan/pull/3758) | fix(misalign): Dont mark misalign store as commit | happy-lx | 2024-10-16 | - | `-` | 0 |
| [#3753](https://github.com/OpenXiangShan/XiangShan/pull/3753) | fix(csr, aia): fix interrupt filter and deleg with AIA | sinceforYy | 2024-10-16 | - | `-` | 0 |
| [#3745](https://github.com/OpenXiangShan/XiangShan/pull/3745) |  fix(rob): VstartEn should be asserted when triggerAction is debug | wissygh | 2024-10-15 | - | `-` | 0 |
| [#3741](https://github.com/OpenXiangShan/XiangShan/pull/3741) | fix(MemBlock): more accurate vector ready signal | Anzooooo | 2024-10-15 | - | `-` | 0 |
| [#3737](https://github.com/OpenXiangShan/XiangShan/pull/3737) | timing(Issue): Opt wakeup and cancel logic and loadDependency timing | sinsanction | 2024-10-15 | - | `-` | 0 |
| [#3733](https://github.com/OpenXiangShan/XiangShan/pull/3733) | fix(VMergeBuffer): vl of fof only allows setting smaller values | Anzooooo | 2024-10-14 | - | `-` | 0 |
| [#3731](https://github.com/OpenXiangShan/XiangShan/pull/3731) | fix the write-back of loadMisalignBuffer polluting RegCache | sinsanction | 2024-10-14 | - | `-` | 0 |
| [#3728](https://github.com/OpenXiangShan/XiangShan/pull/3728) | fix(StoreQueue): fix bug in `uncacheState` FSM | linjuanZ | 2024-10-14 | - | `-` | 0 |
| [#3723](https://github.com/OpenXiangShan/XiangShan/issues/3723) | `csrc` instr get wrong `mstatus` value | ha0lyu | 2024-10-12 | - | `-` | 1 |
| [#3722](https://github.com/OpenXiangShan/XiangShan/pull/3722) | fix(ROB): exclude frontend exceptions from deqIsVlsException | huxuan0307 | 2024-10-12 | - | `-` | 0 |
| [#3721](https://github.com/OpenXiangShan/XiangShan/pull/3721) | fix(zcb): fix ill insn check for zcb arith insn | TheKiteRunner24 | 2024-10-12 | - | `-` | 0 |
| [#3720](https://github.com/OpenXiangShan/XiangShan/pull/3720) | fix(ROB): vector exception can only be handled when ROB is in idle state | huxuan0307 | 2024-10-11 | - | `-` | 0 |
| [#3719](https://github.com/OpenXiangShan/XiangShan/pull/3719) | fix(ICache): block waylookup if there is a pending gpf | ngc7331 | 2024-10-11 | - | `-` | 0 |
| [#3718](https://github.com/OpenXiangShan/XiangShan/pull/3718) | feat(ittage): Reuse always_taken to mark the first occurrence of the jalr inst | sleep-zzz | 2024-10-11 | - | `-` | 0 |
| [#3717](https://github.com/OpenXiangShan/XiangShan/pull/3717) | fix(csr): fix read/write stimecmp raise EX_II | sinceforYy | 2024-10-11 | - | `-` | 0 |
| [#3714](https://github.com/OpenXiangShan/XiangShan/pull/3714) | fix(ExceptionGen): assign vector exception info when robidxes equal | huxuan0307 | 2024-10-11 | - | `-` | 0 |
| [#3710](https://github.com/OpenXiangShan/XiangShan/pull/3710) | fix(csr): fix local counter overflow interrupt req to diff mip.lcofip | sinceforYy | 2024-10-10 | - | `-` | 0 |
| [#3709](https://github.com/OpenXiangShan/XiangShan/issues/3709) | D extension instr `fle.d` bug. | ha0lyu | 2024-10-10 | - | `-` | 2 |
| [#3705](https://github.com/OpenXiangShan/XiangShan/pull/3705) | fix(vtypegen): block the decode until vtype is recovered from walk | Ziyue-Zhang | 2024-10-09 | - | `-` | 0 |
| [#3704](https://github.com/OpenXiangShan/XiangShan/pull/3704) | fix(StoreQueue): commitLastFlow should be true when the port 1 has no exception | huxuan0307 | 2024-10-08 | - | `-` | 0 |
| [#3703](https://github.com/OpenXiangShan/XiangShan/pull/3703) | Fix csr distribute write | huxuan0307 | 2024-10-08 | - | `-` | 0 |
| [#3702](https://github.com/OpenXiangShan/XiangShan/pull/3702) | fix(ROB): vlsNeedCommit only assert one cycle to avoid dup message to RAB | huxuan0307 | 2024-10-05 | - | `-` | 0 |
| [#3701](https://github.com/OpenXiangShan/XiangShan/pull/3701) | fix(CSR): fix shadow write for many CSRs | huxuan0307 | 2024-10-04 | - | `-` | 0 |
| [#3700](https://github.com/OpenXiangShan/XiangShan/pull/3700) | fix(CSR): assert vsatpASIDChanged when actually write vsatp by satp | huxuan0307 | 2024-10-04 | - | `-` | 0 |
| [#3699](https://github.com/OpenXiangShan/XiangShan/pull/3699) | fix(LoadMisalignBuffer): all exception from misalignbuffer should ove… | good-circle | 2024-10-04 | - | `-` | 0 |
| [#3697](https://github.com/OpenXiangShan/XiangShan/pull/3697) | fix(TLB): Should not send gpa when prefetch or redirect | good-circle | 2024-10-04 | - | `-` | 0 |
| [#3696](https://github.com/OpenXiangShan/XiangShan/pull/3696) | fix(vector,decode): use OPFV[VF] encoded in inst to check if need FS not Off | huxuan0307 | 2024-10-03 | - | `-` | 0 |
| [#3695](https://github.com/OpenXiangShan/XiangShan/pull/3695) | feat(rv64v): fix exception for vector fof/non-fof load | huxuan0307 | 2024-10-03 | - | `-` | 0 |
| [#3693](https://github.com/OpenXiangShan/XiangShan/pull/3693) | feat(Trigger): Trigger Module support mcontrol6. | wissygh | 2024-09-30 | - | `-` | 0 |
| [#3691](https://github.com/OpenXiangShan/XiangShan/pull/3691) | fix(Smrnmi): expand NMI interrupt to two types and route the nmi signals to XSTOP | lewislzh | 2024-09-30 | - | `-` | 0 |
| [#3685](https://github.com/OpenXiangShan/XiangShan/pull/3685) | fix(TLB, RVH): delete the s1tagfix which maybe cause the tag check to fail | pxk27 | 2024-09-29 | - | `-` | 0 |
| [#3683](https://github.com/OpenXiangShan/XiangShan/pull/3683) | feat(Trigger): Trigger Module support mcontrol6. | wissygh | 2024-09-29 | - | `-` | 0 |
| [#3681](https://github.com/OpenXiangShan/XiangShan/pull/3681) | fix(PTW, RVH): add the high bits check of the first s2xlate when the req is allstage | pxk27 | 2024-09-29 | - | `-` | 0 |
| [#3679](https://github.com/OpenXiangShan/XiangShan/pull/3679) | fix(PTW, RVH): modify the logic of checking high bits of gpaddr | pxk27 | 2024-09-29 | - | `-` | 0 |
| [#3674](https://github.com/OpenXiangShan/XiangShan/pull/3674) | fix(tlb): overwrite resp information when high address exception happens | good-circle | 2024-09-27 | - | `-` | 0 |
| [#3671](https://github.com/OpenXiangShan/XiangShan/pull/3671) | fix(sc): SCTable dual port SRAM reads and writes to the same address processing | sleep-zzz | 2024-09-27 | - | `-` | 0 |
| [#3670](https://github.com/OpenXiangShan/XiangShan/pull/3670) | power(bpu): optimize CGE of bpu/previous_s2_* | Lawrence-ID | 2024-09-27 | - | `-` | 0 |
| [#3669](https://github.com/OpenXiangShan/XiangShan/pull/3669) | fix(BPU): remove reg of reset_vector | Tang-Haojin | 2024-09-27 | - | `-` | 2 |
| [#3668](https://github.com/OpenXiangShan/XiangShan/pull/3668) | fix(IMSIC): add TLBuffer for tilelink IO | Tang-Haojin | 2024-09-27 | - | `-` | 0 |
| [#3667](https://github.com/OpenXiangShan/XiangShan/pull/3667) | fix(combmem): remove x assignment if ren is low | Tang-Haojin | 2024-09-27 | - | `-` | 1 |
| [#3665](https://github.com/OpenXiangShan/XiangShan/pull/3665) | fix(CSR): remove reg in mhartid | huxuan0307 | 2024-09-26 | - | `-` | 0 |
| [#3664](https://github.com/OpenXiangShan/XiangShan/pull/3664) | fix(vtypegen): fix initial condition after receive redirect | Ziyue-Zhang | 2024-09-26 | - | `-` | 0 |
| [#3660](https://github.com/OpenXiangShan/XiangShan/pull/3660) | fix(PTW, RVH): add the check A bit in HPTW when G-stage is for VS-stage | pxk27 | 2024-09-26 | - | `-` | 0 |
| [#3658](https://github.com/OpenXiangShan/XiangShan/pull/3658) | fix(rv64v): not modify fflags when vl is zero | Ziyue-Zhang | 2024-09-26 | - | `-` | 0 |
| [#3657](https://github.com/OpenXiangShan/XiangShan/pull/3657) | fix(PTW, RVH): fix the priority of gpf, gaf and gvpn_gpf in PTW | pxk27 | 2024-09-26 | - | `-` | 0 |
| [#3648](https://github.com/OpenXiangShan/XiangShan/pull/3648) | submodule(CoupledL2): fix bugs in DCT and linkactive | Kumonda221-CrO3 | 2024-09-25 | - | `-` | 0 |
| [#3647](https://github.com/OpenXiangShan/XiangShan/pull/3647) | fix(csr): change connect0LatencyCtrlSingal to connectNonPipedCtrlSingal | xiaofeibao-xjtu | 2024-09-25 | - | `-` | 0 |
| [#3644](https://github.com/OpenXiangShan/XiangShan/pull/3644) | fix(CSR,interrupt): use rdata instead of regOut to produce interrupt | huxuan0307 | 2024-09-24 | - | `-` | 0 |
| [#3643](https://github.com/OpenXiangShan/XiangShan/pull/3643) | fix(vlwakeup): fix vl write back wakeup from intExu or vfExu | Ziyue-Zhang | 2024-09-24 | - | `-` | 0 |
| [#3641](https://github.com/OpenXiangShan/XiangShan/pull/3641) | fix(ftb): When FTB is closed, the s2_multi_hit_enable should be lowered & Add FTB reading port low fallthroughErr assert. | sleep-zzz | 2024-09-24 | - | `-` | 0 |
| [#3640](https://github.com/OpenXiangShan/XiangShan/pull/3640) | feat(CSR): add No.16,18 and 19 exceptions | huxuan0307 | 2024-09-24 | - | `-` | 0 |
| [#3639](https://github.com/OpenXiangShan/XiangShan/pull/3639) | fix(exception): fix exception vaddr generate logic | good-circle | 2024-09-24 | - | `-` | 0 |
| [#3637](https://github.com/OpenXiangShan/XiangShan/pull/3637) | submodule(CoupledL2): fix bug in ordering between snoop and read | linjuanZ | 2024-09-24 | - | `-` | 0 |
| [#3636](https://github.com/OpenXiangShan/XiangShan/pull/3636) | fix(BPU): adjust s3 target when fallThroughErr signal is high | my-mayfly | 2024-09-24 | - | `-` | 0 |
| [#3635](https://github.com/OpenXiangShan/XiangShan/pull/3635) | fix(ghist): fix ghist maintaining | eastonman | 2024-09-23 | - | `-` | 0 |
| [#3634](https://github.com/OpenXiangShan/XiangShan/pull/3634) | fix(csr): intermediate data should be stored when output not fire | sinceforYy | 2024-09-23 | - | `-` | 0 |
| [#3633](https://github.com/OpenXiangShan/XiangShan/pull/3633) | submodule(CoupledL2): bump CPL2 with MCP2 gated clock fix | Ivyfeather | 2024-09-23 | - | `-` | 0 |
| [#3629](https://github.com/OpenXiangShan/XiangShan/pull/3629) | fix(TLB): fix exception judgement condition | good-circle | 2024-09-23 | - | `-` | 0 |
| [#3628](https://github.com/OpenXiangShan/XiangShan/pull/3628) | fix(ftb): fix ftb pred_rdata not reset | eastonman | 2024-09-23 | - | `-` | 0 |
| [#3624](https://github.com/OpenXiangShan/XiangShan/pull/3624) | fix(PTW, RVH): fix the gpa high check fail in last s2xlate due to a change of gpaddr | pxk27 | 2024-09-21 | - | `-` | 0 |
| [#3621](https://github.com/OpenXiangShan/XiangShan/pull/3621) | submodule(CoupledL2): bump CoupledL2 | linjuanZ | 2024-09-20 | - | `-` | 0 |
| [#3620](https://github.com/OpenXiangShan/XiangShan/pull/3620) | fix(csr): fix trap inst update when CSRR insts raise trap and remove useless io | sinceforYy | 2024-09-20 | - | `-` | 0 |
| [#3612](https://github.com/OpenXiangShan/XiangShan/pull/3612) | Bump aia and fix exception generate when access imsic. | NewPaulWalker | 2024-09-19 | - | `-` | 0 |
| [#3607](https://github.com/OpenXiangShan/XiangShan/pull/3607) | fix(VCVT): disable logic about scalar move instructions. | wissygh | 2024-09-19 | - | `-` | 0 |
| [#3606](https://github.com/OpenXiangShan/XiangShan/pull/3606) | fix(tage): tage bt sram  read and write the same addr at the same time. | sleep-zzz | 2024-09-18 | - | `-` | 0 |
| [#3602](https://github.com/OpenXiangShan/XiangShan/pull/3602) | power(backend): add clock gate for Rob and IssueQueue | xiaofeibao-xjtu | 2024-09-18 | - | `-` | 0 |
| [#3588](https://github.com/OpenXiangShan/XiangShan/pull/3588) | fix(PageTableCache): fix ptwcache refill logic when exception | good-circle | 2024-09-15 | - | `-` | 0 |
| [#3587](https://github.com/OpenXiangShan/XiangShan/issues/3587) | reading fflags changes the status | ha0lyu | 2024-09-14 | - | `-` | 7 |
| [#3585](https://github.com/OpenXiangShan/XiangShan/pull/3585) | fix(Trigger): fix trigger's assign to exceptionGen in rob. | wissygh | 2024-09-14 | - | `-` | 0 |
| [#3583](https://github.com/OpenXiangShan/XiangShan/pull/3583) | power(IssueQueue): add clock gate for deqDelay reg | xiaofeibao-xjtu | 2024-09-14 | - | `-` | 0 |
| [#3580](https://github.com/OpenXiangShan/XiangShan/pull/3580) | fix(TLB, RVH): fix the bug that pf happens because s1 is nonleaf | pxk27 | 2024-09-14 | - | `-` | 0 |
| [#3579](https://github.com/OpenXiangShan/XiangShan/pull/3579) | power(bpu): optimize CGE of bpu/predictors_io_update | Lawrence-ID | 2024-09-14 | - | `-` | 0 |
| [#3577](https://github.com/OpenXiangShan/XiangShan/pull/3577) | fix(CSR): Add legalization code for mstatus.MPP, mnstatus.MNPP and dcsr.PRV | huxuan0307 | 2024-09-14 | - | `-` | 0 |
| [#3575](https://github.com/OpenXiangShan/XiangShan/pull/3575) | fix(PTW, RVH): fix the wrong state transition when has gpf or gaf | pxk27 | 2024-09-14 | - | `-` | 0 |
| [#3570](https://github.com/OpenXiangShan/XiangShan/pull/3570) | submodule(rocket-chip): fix Zcmop illegal instruction | ngc7331 | 2024-09-13 | - | `-` | 0 |
| [#3569](https://github.com/OpenXiangShan/XiangShan/pull/3569) | submodule(ready-to-run): bump nemu to fix the left shift bug | pxk27 | 2024-09-13 | - | `-` | 0 |
| [#3564](https://github.com/OpenXiangShan/XiangShan/pull/3564) | fix(ittage): fix useful bit update condition | eastonman | 2024-09-12 | - | `-` | 0 |
| [#3561](https://github.com/OpenXiangShan/XiangShan/pull/3561) | fix(L2TLB, RVH): fix the bug that gaf and gpf occur at the same time | pxk27 | 2024-09-12 | - | `-` | 0 |
| [#3560](https://github.com/OpenXiangShan/XiangShan/pull/3560) | area(MemBlock): remove redundant signals to optimise area | jin120811 | 2024-09-12 | - | `-` | 0 |
| [#3559](https://github.com/OpenXiangShan/XiangShan/pull/3559) | feat(Zicbom,Zicboz): add permission check and convert CBO.INVAL to CBO.FLUSH when CBIE=0b01 | huxuan0307 | 2024-09-12 | - | `-` | 0 |
| [#3558](https://github.com/OpenXiangShan/XiangShan/pull/3558) | fix(Svpbmt): let PBMTEs in [mh]envcfg be RW and have reset value 0 | huxuan0307 | 2024-09-12 | - | `-` | 0 |
| [#3557](https://github.com/OpenXiangShan/XiangShan/pull/3557) | fix(vstopi): wrong API usage in InterruptFilter | huxuan0307 | 2024-09-12 | - | `-` | 0 |
| [#3553](https://github.com/OpenXiangShan/XiangShan/pull/3553) | fix(L1TLB, RVH): fix the wrong pf because the perm check of fake pte | pxk27 | 2024-09-11 | - | `-` | 0 |
| [#3552](https://github.com/OpenXiangShan/XiangShan/pull/3552) | submodule(CoupledL2): optimize PCredit timing | linjuanZ | 2024-09-11 | - | `-` | 0 |
| [#3551](https://github.com/OpenXiangShan/XiangShan/pull/3551) | fix(L1TLB, RVH): fix the filter of the getGpa req | pxk27 | 2024-09-11 | - | `-` | 0 |
| [#3547](https://github.com/OpenXiangShan/XiangShan/pull/3547) | fix(aia): fix permit check for aia and fix wen for aia csr. | NewPaulWalker | 2024-09-11 | - | `-` | 0 |
| [#3545](https://github.com/OpenXiangShan/XiangShan/pull/3545) | timing(IPrefetch): add 1 cycle to s2_finish | ngc7331 | 2024-09-11 | - | `-` | 0 |
| [#3543](https://github.com/OpenXiangShan/XiangShan/pull/3543) | fix(FTB): Turn off FTB updates when FTB is closed. | sleep-zzz | 2024-09-11 | - | `-` | 0 |
| [#3542](https://github.com/OpenXiangShan/XiangShan/pull/3542) | timing(ICache): allow send MSHR response to (pre)fetch even when io.flush | ngc7331 | 2024-09-11 | - | `-` | 0 |
| [#3538](https://github.com/OpenXiangShan/XiangShan/pull/3538) | fix(XSNoCTop): add port `hartIsInReset` for StandAloneDebugModule. | wissygh | 2024-09-11 | - | `-` | 0 |
| [#3536](https://github.com/OpenXiangShan/XiangShan/pull/3536) | submodule(rocket-chip): bump rocket-chip to fix `SBA` in `DM`. | wissygh | 2024-09-10 | - | `-` | 0 |
| [#3535](https://github.com/OpenXiangShan/XiangShan/pull/3535) | fix(vecException): fix float exception generate when sew <= 16 | Ziyue-Zhang | 2024-09-10 | - | `-` | 0 |
| [#3534](https://github.com/OpenXiangShan/XiangShan/pull/3534) | fix(Svinval): make all insts in Sinval behavior like fence to avoid software wrong usage | huxuan0307 | 2024-09-10 | - | `-` | 0 |
| [#3531](https://github.com/OpenXiangShan/XiangShan/pull/3531) | timing(LsqEnqCtrl): fix timing of lqAllocNumber and sqAllocNumber | xiaofeibao-xjtu | 2024-09-10 | - | `-` | 0 |
| [#3528](https://github.com/OpenXiangShan/XiangShan/pull/3528) | fix(L1TLB, RVH): fix the bug that no tlbreplay for a long time in L1TLB because of getGpa | pxk27 | 2024-09-10 | - | `-` | 0 |
| [#3525](https://github.com/OpenXiangShan/XiangShan/pull/3525) | fix(PTW, RVH): delete the check_g_perm reg that is useless | pxk27 | 2024-09-09 | - | `-` | 0 |
| [#3524](https://github.com/OpenXiangShan/XiangShan/pull/3524) | fix(MMU, RVH): fix the bug that wrong trap when high bits is nonzero and pte.v is invalid | pxk27 | 2024-09-09 | - | `-` | 0 |
| [#3523](https://github.com/OpenXiangShan/XiangShan/pull/3523) | fix(L2TLB, RVH): fix the assert bug when two same vpn reqs are sent to L2TLB and have af | pxk27 | 2024-09-09 | - | `-` | 0 |
| [#3520](https://github.com/OpenXiangShan/XiangShan/pull/3520) | Backend fix timing | xiaofeibao-xjtu | 2024-09-09 | - | `-` | 0 |
| [#3517](https://github.com/OpenXiangShan/XiangShan/pull/3517) | timing(Rab): fix timing of state reg | xiaofeibao-xjtu | 2024-09-09 | - | `-` | 0 |
| [#3515](https://github.com/OpenXiangShan/XiangShan/pull/3515) | Fix mip implementation | huxuan0307 | 2024-09-09 | - | `-` | 0 |
| [#3514](https://github.com/OpenXiangShan/XiangShan/pull/3514) | fix(RAS): correct the Call and Ret signals during redirection, and modify the blocking mechanism of RAS. | my-mayfly | 2024-09-08 | - | `-` | 0 |
| [#3513](https://github.com/OpenXiangShan/XiangShan/pull/3513) | submodule(CoupledL2): fix bugs in PCredit management | linjuanZ | 2024-09-07 | - | `-` | 0 |
| [#3512](https://github.com/OpenXiangShan/XiangShan/pull/3512) | fix(PTW, RVH): the pte of G-stage which support VS-stage is load rather than original access type | pxk27 | 2024-09-07 | - | `-` | 0 |
| [#3510](https://github.com/OpenXiangShan/XiangShan/pull/3510) | fix(PTW, RVH): fix the high bits check of gpaddr when onlyS2 | pxk27 | 2024-09-06 | - | `-` | 0 |
| [#3502](https://github.com/OpenXiangShan/XiangShan/pull/3502) | fix(L1TLB, RVH): fix the length of tag_match about hit in MMUBundle | pxk27 | 2024-09-05 | - | `-` | 0 |
| [#3499](https://github.com/OpenXiangShan/XiangShan/pull/3499) | timing(FTQ): calculate requests sent to prefetcher one cycle in advance | Yan-Muzi | 2024-09-05 | - | `-` | 0 |
| [#3496](https://github.com/OpenXiangShan/XiangShan/pull/3496) | fix(csr): add support virtual interrupt for hvictl csr injection | sinceforYy | 2024-09-04 | - | `-` | 0 |
| [#3495](https://github.com/OpenXiangShan/XiangShan/pull/3495) | fix(rv64v): set vwredsum instructions always depend on oldvd | Ziyue-Zhang | 2024-09-04 | - | `-` | 0 |
| [#3494](https://github.com/OpenXiangShan/XiangShan/pull/3494) | submodule(YunSuan): bump yunsuan to fix neg of condition for f32toi16 | sinceforYy | 2024-09-04 | - | `-` | 0 |
| [#3492](https://github.com/OpenXiangShan/XiangShan/pull/3492) | fix(ICache): MSHR also update meta_codes when updating waymasks | ngc7331 | 2024-09-04 | - | `-` | 0 |
| [#3486](https://github.com/OpenXiangShan/XiangShan/pull/3486) | fix(csr): remove skip mhpmevents csr to diff mhpmevnts | sinceforYy | 2024-09-03 | - | `-` | 0 |
| [#3482](https://github.com/OpenXiangShan/XiangShan/pull/3482) | timing(Backend): add OG2 stage for vector mem | sinsanction | 2024-09-03 | - | `-` | 0 |
| [#3480](https://github.com/OpenXiangShan/XiangShan/pull/3480) | feat(riscv64): Support RISC-V Smrnmi extension | lewislzh | 2024-09-03 | - | `-` | 0 |
| [#3472](https://github.com/OpenXiangShan/XiangShan/pull/3472) | fix(Trigger): Breakpoint exception generated by trigger shouldn't enter dmode. | wissygh | 2024-09-02 | - | `-` | 0 |
| [#3471](https://github.com/OpenXiangShan/XiangShan/pull/3471) | timing(IssueQueue): change mem iq enqNum from 2 to 1 | xiaofeibao-xjtu | 2024-09-02 | - | `-` | 0 |
| [#3469](https://github.com/OpenXiangShan/XiangShan/pull/3469) | fix(csr): fix wen perfEvents to wen mhpmevents csr | sinceforYy | 2024-09-02 | - | `-` | 0 |
| [#3467](https://github.com/OpenXiangShan/XiangShan/pull/3467) | timing(MemBlock): optimize MemBlock timing | happy-lx | 2024-09-02 | - | `-` | 0 |
| [#3462](https://github.com/OpenXiangShan/XiangShan/pull/3462) | fix(VLSU): Vector Unit-Stride instr should trigger misaligned exception | Anzooooo | 2024-09-01 | - | `-` | 0 |
| [#3460](https://github.com/OpenXiangShan/XiangShan/pull/3460) | fix(Zicclsm): Vectors should not support misaligned access by Hardware | Anzooooo | 2024-09-01 | - | `-` | 0 |
| [#3458](https://github.com/OpenXiangShan/XiangShan/pull/3458) | fix(SQ, SimMMIO, L2): fix bugs in mtval when non-data error is raised | linjuanZ | 2024-09-01 | - | `-` | 0 |
| [#3457](https://github.com/OpenXiangShan/XiangShan/pull/3457) | DataPath fix timing and performance，MemBlock fix ssit performance | xiaofeibao-xjtu | 2024-09-01 | - | `-` | 0 |
| [#3453](https://github.com/OpenXiangShan/XiangShan/pull/3453) | fix(L2TLB): Fix exception generation logic  | good-circle | 2024-08-30 | - | `-` | 0 |
| [#3450](https://github.com/OpenXiangShan/XiangShan/pull/3450) | fix(NewCSR, RVH): fix the check of hypervisor load/store instruction when hstatus.hu is valid | pxk27 | 2024-08-30 | - | `-` | 0 |
| [#3447](https://github.com/OpenXiangShan/XiangShan/pull/3447) | fix(MMU, RVH): add the check of reserverd, n & pbmt of pte | pxk27 | 2024-08-29 | - | `-` | 0 |
| [#3442](https://github.com/OpenXiangShan/XiangShan/pull/3442) | fix(MMU, RVH): correct the gpaddr computation in TLB | pxk27 | 2024-08-29 | - | `-` | 1 |
| [#3441](https://github.com/OpenXiangShan/XiangShan/pull/3441) | Trigger: check tdata1.dmode before write `tdata` | wissygh | 2024-08-28 | - | `-` | 0 |
| [#3439](https://github.com/OpenXiangShan/XiangShan/pull/3439) | feat(riscv64): Support RISC-V Zfa extension | sinceforYy | 2024-08-28 | - | `-` | 0 |
| [#3437](https://github.com/OpenXiangShan/XiangShan/pull/3437) | Fix frontend topdown pmu & simulation perf ctr | eastonman | 2024-08-28 | - | `-` | 0 |
| [#3436](https://github.com/OpenXiangShan/XiangShan/pull/3436) | LoadQueueReplay: fix LoadQueueReplay enqueue logic | Anzooooo | 2024-08-27 | - | `-` | 0 |
| [#3434](https://github.com/OpenXiangShan/XiangShan/pull/3434) | fix(NewCSR): when STCE in menvcfg is zero, STCE in henvcfg is read-only zero | sinceforYy | 2024-08-27 | - | `-` | 0 |
| [#3433](https://github.com/OpenXiangShan/XiangShan/pull/3433) | IPrefetch: fix s1 fsm for softPrefetch | ngc7331 | 2024-08-27 | - | `-` | 0 |
| [#3430](https://github.com/OpenXiangShan/XiangShan/pull/3430) | rv64v: fix uop split for vfwredsum instructions when lmul==8 | Ziyue-Zhang | 2024-08-27 | - | `-` | 0 |
| [#3428](https://github.com/OpenXiangShan/XiangShan/pull/3428) | PTW, RVH: fix the bug about unaligned check in isPf and isAf | pxk27 | 2024-08-26 | - | `-` | 0 |
| [#3427](https://github.com/OpenXiangShan/XiangShan/pull/3427) | PTW, RVH: add the sv48 high gpaddr check | pxk27 | 2024-08-26 | - | `-` | 0 |
| [#3426](https://github.com/OpenXiangShan/XiangShan/pull/3426) | RVA23 CMO (Cache Maintenance Operation) | Ivyfeather | 2024-08-26 | - | `-` | 0 |
| [#3424](https://github.com/OpenXiangShan/XiangShan/pull/3424) | PMA, MMU: Fix bug of PA48 | good-circle | 2024-08-26 | - | `-` | 0 |
| [#3423](https://github.com/OpenXiangShan/XiangShan/pull/3423) | PTW, RVH: init the A、D、PPN of fake pte to avoid wrong pf and wrong gpaddr in L1TLB | pxk27 | 2024-08-26 | - | `-` | 0 |
| [#3422](https://github.com/OpenXiangShan/XiangShan/pull/3422) | DebugModule: fix bug, trap don't take place in dmode. | wissygh | 2024-08-26 | - | `-` | 0 |
| [#3421](https://github.com/OpenXiangShan/XiangShan/pull/3421) | zfhmin: add zfhmin extensions | zmx2018 | 2024-08-26 | - | `-` | 0 |
| [#3420](https://github.com/OpenXiangShan/XiangShan/pull/3420) | MMU, RVH: fix the refill of pte that has gpf and change the check of pf/gpf in PTW and HPTW | pxk27 | 2024-08-23 | - | `-` | 0 |
| [#3418](https://github.com/OpenXiangShan/XiangShan/pull/3418) | Rob: fix bug of rob commit. | wissygh | 2024-08-23 | - | `-` | 0 |
| [#3409](https://github.com/OpenXiangShan/XiangShan/pull/3409) | rv64: add Zimop extension support | Ziyue-Zhang | 2024-08-19 | - | `-` | 0 |
| [#3407](https://github.com/OpenXiangShan/XiangShan/pull/3407) | Support Sstvala and Shvstvala extensions | huxuan0307 | 2024-08-18 | - | `-` | 0 |
| [#3404](https://github.com/OpenXiangShan/XiangShan/pull/3404) | svpbmt: add simplified support | Maxpicca-Li | 2024-08-17 | - | `-` | 0 |
| [#3399](https://github.com/OpenXiangShan/XiangShan/pull/3399) | Vfalu: fix fflagsRedMask use outVecCtrl | lewislzh | 2024-08-16 | - | `-` | 0 |
| [#3397](https://github.com/OpenXiangShan/XiangShan/pull/3397) | fix the wrong condition of Mux1H about tval2 that makes wrong gpa written into htval or mtval2 | pxk27 | 2024-08-16 | - | `-` | 0 |
| [#3396](https://github.com/OpenXiangShan/XiangShan/pull/3396) | Frontend: implement prefetch.i support (RVA23 Zicbop) | ngc7331 | 2024-08-16 | - | `-` | 0 |
| [#3395](https://github.com/OpenXiangShan/XiangShan/pull/3395) | DebugModule: Fix bug of singleStep. | wissygh | 2024-08-16 | - | `-` | 0 |
| [#3391](https://github.com/OpenXiangShan/XiangShan/pull/3391) | Bump yunsuan:VIdiv fix state-machine, prioritize flush | lewislzh | 2024-08-16 | - | `-` | 0 |
| [#3389](https://github.com/OpenXiangShan/XiangShan/pull/3389) | RAS: Block BPU prediction when the speculative queue is about to overflow | my-mayfly | 2024-08-15 | - | `-` | 0 |
| [#3387](https://github.com/OpenXiangShan/XiangShan/pull/3387) | DataPath: write v0Regfile and vlRegfile add a pipe for fix timing | xiaofeibao-xjtu | 2024-08-15 | - | `-` | 0 |
| [#3385](https://github.com/OpenXiangShan/XiangShan/pull/3385) | L1TLB, RVH: fix the wrong gpf because checking s2 when ptw resp is onlystage1 | pxk27 | 2024-08-15 | - | `-` | 0 |
| [#3384](https://github.com/OpenXiangShan/XiangShan/pull/3384) | bump yunsuan: fix fflags update | Ziyue-Zhang | 2024-08-15 | - | `-` | 0 |
| [#3382](https://github.com/OpenXiangShan/XiangShan/pull/3382) | BusyTable: remove useless wakeup for fix timing | xiaofeibao-xjtu | 2024-08-15 | - | `-` | 0 |
| [#3379](https://github.com/OpenXiangShan/XiangShan/pull/3379) | CSR: miselect, siselect, vsiselect should have reset value since they are WARL | huxuan0307 | 2024-08-14 | - | `-` | 0 |
| [#3378](https://github.com/OpenXiangShan/XiangShan/pull/3378) | ROB: the interrupt_safe of CSR instruction should be false | pxk27 | 2024-08-14 | - | `-` | 0 |
| [#3375](https://github.com/OpenXiangShan/XiangShan/pull/3375) | CSR, RVH: fix the wrong val writen in htval when having igpf | pxk27 | 2024-08-13 | - | `-` | 0 |
| [#3374](https://github.com/OpenXiangShan/XiangShan/pull/3374) | Backend: remove useless loadCancel for fix timing | xiaofeibao-xjtu | 2024-08-13 | - | `-` | 0 |
| [#3370](https://github.com/OpenXiangShan/XiangShan/pull/3370) | style(Frontend): use scalafmt formatting frontend | Yan-Muzi | 2024-08-13 | - | `-` | 1 |
| [#3367](https://github.com/OpenXiangShan/XiangShan/pull/3367) | bpu: Ittage read during update | sleep-zzz | 2024-08-12 | - | `-` | 0 |
| [#3364](https://github.com/OpenXiangShan/XiangShan/pull/3364) | IssueQueue: only trans valid but not issued entry for fix ldCancel timing | xiaofeibao-xjtu | 2024-08-09 | - | `-` | 0 |
| [#3360](https://github.com/OpenXiangShan/XiangShan/pull/3360) | CSR: fix custom IRQ injection mechanism | huxuan0307 | 2024-08-08 | - | `-` | 0 |
| [#3359](https://github.com/OpenXiangShan/XiangShan/pull/3359) | Bump difftest. | NewPaulWalker | 2024-08-07 | - | `-` | 0 |
| [#3358](https://github.com/OpenXiangShan/XiangShan/pull/3358) | rv64v: fix temp vector register index which need to start from 32 | Ziyue-Zhang | 2024-08-07 | - | `-` | 0 |
| [#3357](https://github.com/OpenXiangShan/XiangShan/pull/3357) | PTW, RVH: fix the x state of stage1 pf/af when the first s2xlate happens gpf in PTW | pxk27 | 2024-08-07 | - | `-` | 0 |
| [#3353](https://github.com/OpenXiangShan/XiangShan/pull/3353) | CSR: use "ignore illegal write" WARL strategy for tselect | huxuan0307 | 2024-08-07 | - | `-` | 0 |
| [#3344](https://github.com/OpenXiangShan/XiangShan/pull/3344) | IBuffer: change read ptr logic for fix timing, change outputEntries logic for better performance | xiaofeibao-xjtu | 2024-08-06 | - | `-` | 0 |
| [#3343](https://github.com/OpenXiangShan/XiangShan/pull/3343) | LLPTW, RVH: fix the bug that llptw resp wrong stage1 when first s2xlate has gpf in LLPTW | pxk27 | 2024-08-05 | - | `-` | 0 |
| [#3342](https://github.com/OpenXiangShan/XiangShan/pull/3342) | PTW, RVH: fix the error S1 resp when gpf happened and s1_level == 0 | pxk27 | 2024-08-05 | - | `-` | 0 |
| [#3338](https://github.com/OpenXiangShan/XiangShan/pull/3338) | CSR: add custom IRQ injection mechanism | huxuan0307 | 2024-08-05 | - | `-` | 0 |
| [#3331](https://github.com/OpenXiangShan/XiangShan/pull/3331) | MMU, RVH, fix the af refill error when refilling page cache | pxk27 | 2024-08-01 | - | `-` | 0 |
| [#3327](https://github.com/OpenXiangShan/XiangShan/pull/3327) | CSR: initialize vstart to avoid X propagation at DecodeStage | huxuan0307 | 2024-08-01 | - | `-` | 0 |
| [#3324](https://github.com/OpenXiangShan/XiangShan/pull/3324) | NewCSR: fix condition of select candidates and trap taken to VS-mode | sinceforYy | 2024-08-01 | - | `-` | 0 |
| [#3317](https://github.com/OpenXiangShan/XiangShan/pull/3317) | PTW, RVH: rewrite the PTW resp logic when PTW get gpf or gaf from HPTW | pxk27 | 2024-07-31 | - | `-` | 0 |
| [#3314](https://github.com/OpenXiangShan/XiangShan/pull/3314) | CSR: enable misa.B which contains `Zba`, `Zbb` and `Zbs` extensions | huxuan0307 | 2024-07-30 | - | `-` | 0 |
| [#3308](https://github.com/OpenXiangShan/XiangShan/pull/3308) | PageCache, RVH: add the condition that page cache resp L1tlb when stage1 hit but has pf in allstage | pxk27 | 2024-07-29 | - | `-` | 0 |
| [#3305](https://github.com/OpenXiangShan/XiangShan/pull/3305) | MMU: replace RRArbiter with RRArbiterInit | pxk27 | 2024-07-29 | - | `-` | 0 |
| [#3301](https://github.com/OpenXiangShan/XiangShan/pull/3301) | NewCSR: fix mie.LCOFIE is RW and init value 0 | sinceforYy | 2024-07-26 | - | `-` | 0 |
| [#3300](https://github.com/OpenXiangShan/XiangShan/pull/3300) | NewCSR: skip *ip difftest | sinceforYy | 2024-07-26 | - | `-` | 0 |
| [#3298](https://github.com/OpenXiangShan/XiangShan/pull/3298) | LLPTW, RVH: fix the bug that llptw continue s2xlate when the pte which mem resp has pf | pxk27 | 2024-07-26 | - | `-` | 0 |
| [#3296](https://github.com/OpenXiangShan/XiangShan/pull/3296) | vtype: init vtype's vill to 1 and other fields to 0 | Ziyue-Zhang | 2024-07-26 | - | `-` | 0 |
| [#3294](https://github.com/OpenXiangShan/XiangShan/pull/3294) | difftest: support difftest for fcsr. | NewPaulWalker | 2024-07-26 | - | `-` | 0 |
| [#3293](https://github.com/OpenXiangShan/XiangShan/pull/3293) | Decode: add DecodeBuf for fix timing of ready to Ibuffer | xiaofeibao-xjtu | 2024-07-26 | - | `-` | 0 |
| [#3290](https://github.com/OpenXiangShan/XiangShan/pull/3290) | Backend: add Reg Cache for int register file | sinsanction | 2024-07-25 | - | `-` | 0 |
| [#3284](https://github.com/OpenXiangShan/XiangShan/pull/3284) | vtype: enq spec vtype to vtypebuffer's snapshot | Ziyue-Zhang | 2024-07-24 | - | `-` | 0 |
| [#3208](https://github.com/OpenXiangShan/XiangShan/pull/3208) | MemBlock: fix timing of scalar load/store issue and writeback | weidingliu | 2024-07-16 | - | `-` | 0 |
| [#3154](https://github.com/OpenXiangShan/XiangShan/issues/3154) | error massage | mlabaf2 | 2024-07-07 | - | `-` | 0 |
| [#3084](https://github.com/OpenXiangShan/XiangShan/issues/3084) | In VCS simulation, multi-core simulation of some harts ended prematurely due to incorrect execution of SEQZ instruction | xxq0902 | 2024-06-18 | - | `-` | 1 |
| [#3012](https://github.com/OpenXiangShan/XiangShan/issues/3012) | Difftest failed on a RISC-V Vector memcpy workload with misaligned(in vlen granularity, not element) unit stride load | cyyself | 2024-05-27 | - | `-` | 5 |
| [#2961](https://github.com/OpenXiangShan/XiangShan/issues/2961) | Can not generate RTL when NUM_CORES >= 3 | cyyself | 2024-05-10 | - | `-` | 2 |
| [#2890](https://github.com/OpenXiangShan/XiangShan/issues/2890) | Simulation hangs for longer running functions using the vector extension | camel-cdr | 2024-04-16 | - | `-` | 9 |
| [#2767](https://github.com/OpenXiangShan/XiangShan/issues/2767) | Incorrect Rounding Mode Handling for Specific Cases | youzi27 | 2024-03-09 | - | `-` | 2 |
| [#2658](https://github.com/OpenXiangShan/XiangShan/issues/2658) | TLB Timing interface not match with MMIO instruction fetch in IFU | euphgh | 2024-01-18 | - | `-` | 0 |
| [#2642](https://github.com/OpenXiangShan/XiangShan/issues/2642) | Non-Canonical NaN Representation in Double-Precision Results from fmadd.d Instruction | youzi27 | 2024-01-15 | - | `-` | 0 |
| [#2606](https://github.com/OpenXiangShan/XiangShan/issues/2606) | issue about kunminghu | menglinhan | 2024-01-02 | - | `-` | 0 |
| [#2534](https://github.com/OpenXiangShan/XiangShan/issues/2534) | L1D Cache Side-channal on Nanhu | nieeka | 2023-12-07 | - | `-` | 1 |
| [#2464](https://github.com/OpenXiangShan/XiangShan/issues/2464) | Fusion decoder does not prevent rs1=rs2 | poemonsense | 2023-11-07 | - | `-` | 1 |
| [#572](https://github.com/OpenXiangShan/XiangShan/pull/572) | TLB: wrap tlb's tag(vpn) with CAM | Lemover | 2021-02-23 | - | `-` | 0 |
| [#49](https://github.com/OpenXiangShan/XiangShan/issues/49) | 重命名表初始化与后续维护存在问题 | poemonsense | 2020-06-26 | - | `-` | 0 |
| [#48](https://github.com/OpenXiangShan/XiangShan/issues/48) | regfile写口仲裁 | poemonsense | 2020-06-26 | - | `-` | 0 |

### Pull Requests

| PR | Title | Author | State | Commits | Merged at |
|---:|---|---|---|---:|---|
| [#6286](https://github.com/OpenXiangShan/XiangShan/pull/6286) | perf(mdp): refine strict StoreSet prediction | weidingliu | closed | 1 | 2026-07-29T09:46:58Z |
| [#6275](https://github.com/OpenXiangShan/XiangShan/pull/6275) | fix(L1PF, LSQ): reject stale TLB updates and nonphysical store wakeups | fuhuakai | closed | 2 | 2026-07-24T07:37:16Z |
| [#6258](https://github.com/OpenXiangShan/XiangShan/pull/6258) | fix(nmi): fix trap to hs/vs event when both NMI and excp occur | sinceforYy | closed | 1 | 2026-07-21T09:48:07Z |
| [#6256](https://github.com/OpenXiangShan/XiangShan/pull/6256) | fix(csr,dbltrp): fix s_EX_DT should be controlled by sstatus.SDT | sinceforYy | closed | 1 | 2026-07-21T09:47:10Z |
| [#6243](https://github.com/OpenXiangShan/XiangShan/pull/6243) | chore: cherry-pick v2 fixes to v3 (260714) | wissygh | closed | 18 | 2026-07-17T10:02:04Z |
| [#6242](https://github.com/OpenXiangShan/XiangShan/pull/6242) | feat(loadUnit): add support of arbiter of RRBankConflict | weidingliu | closed | 1 | 2026-07-31T06:26:26Z |
| [#6241](https://github.com/OpenXiangShan/XiangShan/pull/6241) | fix(missQueue): remove incorrect XSError | jlong299 | closed | 1 | 2026-07-15T11:09:50Z |
| [#6235](https://github.com/OpenXiangShan/XiangShan/pull/6235) | fix(Ftq): fix backendExceptionPtr | ngc7331 | closed | 1 | 2026-07-14T07:23:13Z |
| [#6228](https://github.com/OpenXiangShan/XiangShan/pull/6228) | fix(storeQueue): fix write zero to sbuffer of cbo.zero | weidingliu | closed | 1 | 2026-07-16T02:04:38Z |
| [#6223](https://github.com/OpenXiangShan/XiangShan/pull/6223) | Fix nmie 0710 | wissygh | closed | 4 | 2026-07-15T13:30:50Z |
| [#6213](https://github.com/OpenXiangShan/XiangShan/pull/6213) | fix(Ifu): fix uncache with prevHalfRvi | ngc7331 | closed | 3 | 2026-07-10T08:05:30Z |
| [#6197](https://github.com/OpenXiangShan/XiangShan/pull/6197) | fix(MissQueue): fix nMaxPrefetchEntry logic | Ruomio | closed | 1 | 2026-07-08T10:05:41Z |
| [#6185](https://github.com/OpenXiangShan/XiangShan/pull/6185) | fix(LoadPipe): suppress S3 hit metadata updates on s2 kill | jlong299 | closed | 2 | 2026-07-08T10:50:34Z |
| [#6183](https://github.com/OpenXiangShan/XiangShan/pull/6183) | fix(Sbuffer): split CMO sbuffer drain empty checks | jlong299 | closed | 2 | 2026-07-08T10:43:59Z |
| [#6167](https://github.com/OpenXiangShan/XiangShan/pull/6167) | fix(Ittage): fix altDiffer condition | ngc7331 | closed | 1 | 2026-07-13T12:27:18Z |
| [#6147](https://github.com/OpenXiangShan/XiangShan/pull/6147) | fix(ftq): fix train cache flush condition | TheKiteRunner24 | closed | 1 | 2026-06-29T07:18:36Z |
| [#6144](https://github.com/OpenXiangShan/XiangShan/pull/6144) | fix(Ifu): Pbmt.IO should wait for last commit | ngc7331 | closed | 1 | 2026-06-29T07:19:26Z |
| [#6131](https://github.com/OpenXiangShan/XiangShan/pull/6131) | fix(vstopi): fix the mapping of vsei index | sinceforYy | closed | 1 | 2026-07-09T07:51:21Z |
| [#6121](https://github.com/OpenXiangShan/XiangShan/pull/6121) | fix(perf): fixed perf-event `frontend_stall_cycle` | wissygh | closed | 1 | 2026-07-08T06:41:17Z |
| [#6120](https://github.com/OpenXiangShan/XiangShan/pull/6120) | timing(loadQueueRAW): use age matrix for RAW oldest select | zzQGyy | closed | 1 | 2026-07-03T03:23:07Z |
| [#6117](https://github.com/OpenXiangShan/XiangShan/pull/6117) | perf(Sbuffer): set in.req.ready to true if req can be merged | jlong299 | closed | 9 | 2026-06-23T03:25:02Z |
| [#6104](https://github.com/OpenXiangShan/XiangShan/pull/6104) | fix(debug, csr): fix csr to support debug spec 1.0 | wissygh | closed | 1 | 2026-07-08T07:03:43Z |
| [#6102](https://github.com/OpenXiangShan/XiangShan/pull/6102) | timing(L1PrefetchComponent): defer sent_vec updates to pf fire | zzQGyy | closed | 1 | 2026-07-02T10:46:07Z |
| [#6101](https://github.com/OpenXiangShan/XiangShan/pull/6101) | fix(pmu): update resolve-to-perfQueue and commit pmu logic | Erlkonigal | closed | 1 | 2026-06-18T07:09:41Z |
| [#6100](https://github.com/OpenXiangShan/XiangShan/pull/6100) | fix(CSR): fix reset value of mstatus.mdt & mnstatus.nmie | wissygh | closed | 4 | 2026-07-10T06:09:49Z |
| [#6096](https://github.com/OpenXiangShan/XiangShan/pull/6096) | fix(Fence): fix fence opcodes | sinceforYy | closed | 1 | 2026-06-17T01:17:47Z |
| [#6095](https://github.com/OpenXiangShan/XiangShan/pull/6095) | fix(PMPChecker, PMAChecker): non-dmode can't access memory of debug | wissygh | closed | 1 | 2026-06-19T16:10:26Z |
| [#6086](https://github.com/OpenXiangShan/XiangShan/pull/6086) | fix(vstopi): fix iid when Candidate3 and Candidate5 enable | sinceforYy | closed | 2 | 2026-06-18T06:38:59Z |
| [#6084](https://github.com/OpenXiangShan/XiangShan/pull/6084) | fix(TrapInst): fix temporarily stored trapInstInfo generation | sinceforYy | closed | 1 | 2026-06-17T08:48:43Z |
| [#6081](https://github.com/OpenXiangShan/XiangShan/pull/6081) | fix(StoreQueue): fix cbo handle earlier than instruction commit | weidingliu | closed | 1 | 2026-06-18T08:21:32Z |
| [#6075](https://github.com/OpenXiangShan/XiangShan/pull/6075) | fix(mnret): fix MNret error and clear mnstatus.mnpv/mnpp | sinceforYy | closed | 1 | 2026-06-17T08:58:33Z |
| [#6074](https://github.com/OpenXiangShan/XiangShan/pull/6074) | fix(mret): fix vsstatus Valid Path in MretEvent | sinceforYy | closed | 1 | 2026-06-10T03:01:23Z |
| [#6071](https://github.com/OpenXiangShan/XiangShan/pull/6071) | fix(csr, xstatus): mark HLV/HLVX/HSV memory traps as virtual | wissygh | closed | 1 | 2026-06-17T08:47:25Z |
| [#6070](https://github.com/OpenXiangShan/XiangShan/pull/6070) | fix(rob): fix the X-state propagation for commit_w | xiaofeibao-xjtu | closed | 1 | 2026-06-18T06:37:19Z |
| [#6067](https://github.com/OpenXiangShan/XiangShan/pull/6067) | fix(CSR, vscause): gate VS hvictl interrupt cause by interrupt type | wissygh | closed | 1 | 2026-06-17T08:49:48Z |
| [#6058](https://github.com/OpenXiangShan/XiangShan/pull/6058) | fix(mtval2): fix the incorrect generation of mtval2 during IGPF | sinceforYy | closed | 2 | 2026-06-18T06:37:09Z |
| [#6051](https://github.com/OpenXiangShan/XiangShan/pull/6051) | fix(PMA,PMP): fix RMW base value for CSRRS/CSRRC in PMP and PMA | sinceforYy | closed | 1 | 2026-06-17T08:47:01Z |
| [#6031](https://github.com/OpenXiangShan/XiangShan/pull/6031) | fix(vstopi): fix vstopi Candidate3 enable conditation | sinceforYy | closed | 1 | 2026-06-17T08:41:19Z |
| [#6030](https://github.com/OpenXiangShan/XiangShan/pull/6030) | fix(vstopi): fix vstopi Candidate3 enable conditation | sinceforYy | closed | 1 | 2026-06-10T03:00:56Z |
| [#6010](https://github.com/OpenXiangShan/XiangShan/pull/6010) | fix(Intr): fix priority number of SEI when SEI is injected from M-level | sinceforYy | closed | 1 | 2026-06-10T03:04:57Z |
| [#6009](https://github.com/OpenXiangShan/XiangShan/pull/6009) | fix(ubtb): check if s0 hits t1 victim | ngc7331 | closed | 1 | 2026-06-05T03:01:19Z |
| [#6003](https://github.com/OpenXiangShan/XiangShan/pull/6003) | fix(StoreQueue): fix bug of fullOverlap when store is cross16B | weidingliu | closed | 1 | 2026-05-26T02:55:42Z |
| [#5993](https://github.com/OpenXiangShan/XiangShan/pull/5993) | feat(DCacheWrapper): add perfcnt for l2 hint accuracy | Frankslu | closed | 1 | 2026-05-20T08:18:56Z |
| [#5989](https://github.com/OpenXiangShan/XiangShan/pull/5989) | perf(l1pf): add prefetch request handshake to l2 | Maxpicca-Li | closed | 1 | 2026-05-21T10:00:20Z |
| [#5985](https://github.com/OpenXiangShan/XiangShan/pull/5985) | fix(ifu): fix instruction concatenation error during cross-channel fetch | my-mayfly | closed | 1 | 2026-05-22T08:06:09Z |
| [#5963](https://github.com/OpenXiangShan/XiangShan/pull/5963) | timing(MemBlock): optimize LSQ and L1 prefetch critical paths | zzQGyy | closed | 17 | 2026-06-12T02:52:30Z |
| [#5962](https://github.com/OpenXiangShan/XiangShan/pull/5962) | fix(ICache): explicitly set `s1_itlbPbmt`'s init width | ngc7331 | closed | 1 | 2026-05-20T07:32:15Z |
| [#5959](https://github.com/OpenXiangShan/XiangShan/pull/5959) | fix(Ifu,InstrUncache): do not mark incomplete if is RVC or has exception | ngc7331 | closed | 1 | 2026-05-20T04:20:16Z |
| [#5952](https://github.com/OpenXiangShan/XiangShan/pull/5952) | fix(debug, csr): fix csr to support debug spec 1.0 | wissygh | closed | 1 | 2026-05-18T07:09:59Z |
| [#5939](https://github.com/OpenXiangShan/XiangShan/pull/5939) | fix(LoadUnit): fix perfCounter of LoadUnit | weidingliu | closed | 2 | 2026-06-18T08:45:27Z |
| [#5926](https://github.com/OpenXiangShan/XiangShan/pull/5926) | fix(Interrupt): `stepie` should control hvictl inject interrupt | wissygh | closed | 1 | 2026-05-20T07:28:40Z |
| [#5913](https://github.com/OpenXiangShan/XiangShan/pull/5913) | fix(StoreQueue): fix entry of invalid in unalignQueue | weidingliu | closed | 1 | 2026-05-14T09:33:20Z |
| [#5887](https://github.com/OpenXiangShan/XiangShan/pull/5887) | refactor(MissQueue): Parallel enqueue in MissQueue | Ruomio | closed | 11 | 2026-06-17T02:42:27Z |
| [#5874](https://github.com/OpenXiangShan/XiangShan/pull/5874) | fix(ifu): do not defer exception signal until instruction reassembly is complete | my-mayfly | closed | 1 | 2026-05-06T07:21:41Z |
| [#5867](https://github.com/OpenXiangShan/XiangShan/pull/5867) | fix(jump, perf): fix redirect valid | sinceforYy | closed | 1 | 2026-04-29T01:56:08Z |
| [#5862](https://github.com/OpenXiangShan/XiangShan/pull/5862) | fix(CSR, mtvec): add reset value for mtvec | wissygh | closed | 1 | 2026-04-28T07:22:25Z |
| [#5860](https://github.com/OpenXiangShan/XiangShan/pull/5860) | fix(csr, satp): fix the update logic of xepc and xtval | sinceforYy | closed | 3 | 2026-04-29T09:19:13Z |
| [#5858](https://github.com/OpenXiangShan/XiangShan/pull/5858) | fix(Bitmap): cfs indexed with wrong truncated PPN in L2TLB | yxtx1994 | closed | 1 | 2026-04-27T08:11:31Z |
| [#5855](https://github.com/OpenXiangShan/XiangShan/pull/5855) | fix(StoreQueue): fix `cross16B` handle of `storeQueue`  | weidingliu | closed | 2 | 2026-05-07T02:57:11Z |
| [#5843](https://github.com/OpenXiangShan/XiangShan/pull/5843) | timing(sc): move the computation of totalSum and sumAboveThre to s1 | sleep-zzz | closed | 1 | 2026-04-23T10:30:53Z |
| [#5835](https://github.com/OpenXiangShan/XiangShan/pull/5835) | timing(ftq): add some additional stages in FTQ | Yan-Muzi | closed | 2 | 2026-04-23T10:29:24Z |
| [#5833](https://github.com/OpenXiangShan/XiangShan/pull/5833) | fix(csr): fix indirect csr RegOut | sinceforYy | closed | 1 | 2026-04-23T01:59:56Z |
| [#5823](https://github.com/OpenXiangShan/XiangShan/pull/5823) | fix(csr): fix indirect csr RegOut | sinceforYy | closed | 1 | 2026-04-23T02:00:19Z |
| [#5814](https://github.com/OpenXiangShan/XiangShan/pull/5814) | fix(StoreQueue): fix OverlapMask for cross16B forward | weidingliu | closed | 1 | 2026-04-20T02:44:17Z |
| [#5803](https://github.com/OpenXiangShan/XiangShan/pull/5803) | feat(topdown): Resolve the false positive issue caused by insufficient main pipeline resources. | lewislzh | closed | 2 | 2026-04-15T04:00:14Z |
| [#5797](https://github.com/OpenXiangShan/XiangShan/pull/5797) | timing(bpu): fix bpu s3 timing | TheKiteRunner24 | closed | 1 | 2026-04-22T06:53:38Z |
| [#5795](https://github.com/OpenXiangShan/XiangShan/pull/5795) | feat(VirtualLoadQueue): add pointer exceed assert for debug | weidingliu | closed | 1 | 2026-06-18T08:45:45Z |
| [#5787](https://github.com/OpenXiangShan/XiangShan/pull/5787) | fix(backend, ctrlblock): export empty state to ftq when backend drains | wissygh | closed | 4 | 2026-05-15T01:55:27Z |
| [#5783](https://github.com/OpenXiangShan/XiangShan/pull/5783) | fix(LoadUnit): fix unalignedHead replay stuck | weidingliu | closed | 1 | 2026-04-10T02:50:22Z |
| [#5762](https://github.com/OpenXiangShan/XiangShan/pull/5762) | fix(TopDown): fix mis_pred and total_flush | sinceforYy | closed | 1 | 2026-04-09T06:27:47Z |
| [#5756](https://github.com/OpenXiangShan/XiangShan/pull/5756) | feat(sc): open global table and refactor Sc parameter | sleep-zzz | closed | 4 | 2026-04-09T03:28:53Z |
| [#5754](https://github.com/OpenXiangShan/XiangShan/pull/5754) | Fix debug 260401 | wissygh | closed | 3 | 2026-04-09T06:24:07Z |
| [#5751](https://github.com/OpenXiangShan/XiangShan/pull/5751) | fix(storeQueue): fix bug of `pbmt` & `hsv_*` access device region | weidingliu | closed | 1 | 2026-04-01T10:38:03Z |
| [#5748](https://github.com/OpenXiangShan/XiangShan/pull/5748) | fix(storeQueue): fix deqPtr move early | weidingliu | closed | 1 | 2026-04-01T05:23:58Z |
| [#5743](https://github.com/OpenXiangShan/XiangShan/pull/5743) | fix(Rename): fix psrcVl bypass to use pdestVl | sinceforYy | closed | 1 | 2026-04-09T10:06:11Z |
| [#5740](https://github.com/OpenXiangShan/XiangShan/pull/5740) | fix(frontend,perf): bump utility & use XSPerfSeqAccumulate | ngc7331 | closed | 1 | 2026-03-30T09:20:16Z |
| [#5734](https://github.com/OpenXiangShan/XiangShan/pull/5734) | fix(bpu): fix the statistical error in counter train_stall | sleep-zzz | closed | 1 | 2026-04-01T07:06:45Z |
| [#5730](https://github.com/OpenXiangShan/XiangShan/pull/5730) | fix(debug): Hold dpc on critical-error debug reentry | wissygh | closed | 1 | 2026-03-30T08:18:41Z |
| [#5722](https://github.com/OpenXiangShan/XiangShan/pull/5722) | fix(CSR, Mcontrol6): fix chain of Mcontrol6/Tdata1 | wissygh | closed | 2 | 2026-03-30T08:34:09Z |
| [#5720](https://github.com/OpenXiangShan/XiangShan/pull/5720) | fix(L1Prefetcher): use a separate control signal to RegEnable PC | good-circle | closed | 1 | 2026-03-26T07:17:38Z |
| [#5705](https://github.com/OpenXiangShan/XiangShan/pull/5705) | perf(rob): fix `commitInstrBranch` & add `branch_jump` perfCounter | wissygh | closed | 1 | 2026-03-23T06:58:08Z |
| [#5704](https://github.com/OpenXiangShan/XiangShan/pull/5704) | fix(IQ, entryBundle): remove datasources from `commonOutBundle` | wissygh | closed | 1 | 2026-03-23T07:06:01Z |
| [#5700](https://github.com/OpenXiangShan/XiangShan/pull/5700) | fix(LoadUnit): raise af for unalign access on MMIO region | linjuanZ | closed | 1 | 2026-03-23T02:08:52Z |
| [#5698](https://github.com/OpenXiangShan/XiangShan/pull/5698) | timing(StoreQueue): optimize timing path of StoreQueue | weidingliu | closed | 1 | 2026-03-31T05:33:09Z |
| [#5697](https://github.com/OpenXiangShan/XiangShan/pull/5697) | timing(MemBlock): optimize timing | linjuanZ | closed | 8 | 2026-03-23T09:22:03Z |
| [#5687](https://github.com/OpenXiangShan/XiangShan/pull/5687) | fix(IFU): do fetch if only the second cacheline has exception | ngc7331 | closed | 1 | 2026-03-18T07:23:27Z |
| [#5685](https://github.com/OpenXiangShan/XiangShan/pull/5685) | timing(rename): fix rename timing | sinceforYy | closed | 3 | 2026-03-31T06:47:14Z |
| [#5680](https://github.com/OpenXiangShan/XiangShan/pull/5680) | fix(uras): correct S1-level RAS stack top address during override | my-mayfly | closed | 1 | 2026-04-13T03:18:55Z |
| [#5677](https://github.com/OpenXiangShan/XiangShan/pull/5677) | fix(tage): fix tage select allocate way logic | TheKiteRunner24 | closed | 1 | 2026-03-16T02:59:41Z |
| [#5675](https://github.com/OpenXiangShan/XiangShan/pull/5675) | fix(vldMergeUnit): fix the data output of v0 in vldMergeUnit | sinceforYy | closed | 1 | 2026-03-16T03:14:54Z |
| [#5674](https://github.com/OpenXiangShan/XiangShan/pull/5674) | fix(StoreUnit): fix the revoke logic of misalignbuffer | Anzooooo | closed | 1 | 2026-03-16T09:44:47Z |
| [#5652](https://github.com/OpenXiangShan/XiangShan/pull/5652) | chore(backend): improve code quality | xiaofeibao-xjtu | closed | 5 | 2026-03-11T01:56:43Z |
| [#5648](https://github.com/OpenXiangShan/XiangShan/pull/5648) | timing(sc): fix the timing of sc in train  | sleep-zzz | closed | 3 | 2026-03-09T08:43:33Z |
| [#5644](https://github.com/OpenXiangShan/XiangShan/pull/5644) | timing(MMU): avoid adder when generate gpaddr | cebarobot | closed | 1 | 2026-03-06T08:11:12Z |
| [#5640](https://github.com/OpenXiangShan/XiangShan/pull/5640) | fix(mmio): store mmio will also mark the rob | Anzooooo | closed | 2 | 2026-03-16T09:43:54Z |
| [#5638](https://github.com/OpenXiangShan/XiangShan/pull/5638) | fix(mbtb): fix addrField (cfi)Position width | ngc7331 | closed | 1 | 2026-03-06T08:53:49Z |
| [#5637](https://github.com/OpenXiangShan/XiangShan/pull/5637) | chore(Rat): move RatWrapper to Rename to check Rename timing | sinceforYy | closed | 1 | 2026-03-09T02:50:35Z |
| [#5636](https://github.com/OpenXiangShan/XiangShan/pull/5636) | timing(intRegion): reduce bju IssueQueue's size, fix IssueQueue's ready timing, fix timing of interrupt selection | xiaofeibao-xjtu | closed | 10 | 2026-03-05T08:27:41Z |
| [#5630](https://github.com/OpenXiangShan/XiangShan/pull/5630) | fix(uncache): fix forwarding order hazard when `mem_acquire` is not fired | Maxpicca-Li | closed | 1 | 2026-03-17T09:47:02Z |
| [#5625](https://github.com/OpenXiangShan/XiangShan/pull/5625) | fix(commonHR): commonHR restored using queue implementation in s3Override | sleep-zzz | closed | 2 | 2026-03-05T03:45:49Z |
| [#5614](https://github.com/OpenXiangShan/XiangShan/pull/5614) | timing(bpu): fix bpu s2 timing | TheKiteRunner24 | closed | 1 | 2026-02-26T09:06:36Z |
| [#5611](https://github.com/OpenXiangShan/XiangShan/pull/5611) | fix(bpu): fix s1 selection logic | TheKiteRunner24 | closed | 1 | 2026-02-25T02:50:33Z |
| [#5603](https://github.com/OpenXiangShan/XiangShan/pull/5603) | timing(bpu): move position comparation to s2 | Erlkonigal | closed | 1 | - |
| [#5602](https://github.com/OpenXiangShan/XiangShan/pull/5602) | fix(mbtb): train counter in all align banks | Erlkonigal | closed | 3 | 2026-03-16T03:01:54Z |
| [#5601](https://github.com/OpenXiangShan/XiangShan/pull/5601) | fix(tage,sc): add mbtb hit cond to avoid sc train error | out-of-order55 | closed | 2 | 2026-02-24T11:21:20Z |
| [#5583](https://github.com/OpenXiangShan/XiangShan/pull/5583) | fix(redirect): flushpipe shouldn't assert `redirect.interrupt` | wissygh | closed | 1 | 2026-01-30T08:22:32Z |
| [#5576](https://github.com/OpenXiangShan/XiangShan/pull/5576) | feat(MDP): support MDP of StoreSet | weidingliu | closed | 19 | 2026-02-02T07:37:45Z |
| [#5568](https://github.com/OpenXiangShan/XiangShan/pull/5568) | feat(pmu): enhance misprediction analysis with position comparison | my-mayfly | closed | 1 | 2026-01-29T03:11:12Z |
| [#5551](https://github.com/OpenXiangShan/XiangShan/pull/5551) | fix(SaturateCounter): fix signed isWeakPositive | ngc7331 | closed | 1 | 2026-01-23T03:15:47Z |
| [#5548](https://github.com/OpenXiangShan/XiangShan/pull/5548) | refactor: new LoadUnit and new StoreQueue | linjuanZ | closed | 29 | 2026-03-18T02:46:14Z |
| [#5545](https://github.com/OpenXiangShan/XiangShan/pull/5545) | fix(SaturateCounter): disallow signed step | ngc7331 | closed | 1 | 2026-01-21T08:55:08Z |
| [#5544](https://github.com/OpenXiangShan/XiangShan/pull/5544) | perf(pf): optimize l1 prefetcher training and for training | Maxpicca-Li | closed | 3 | 2026-06-28T02:56:23Z |
| [#5543](https://github.com/OpenXiangShan/XiangShan/pull/5543) | refactor(mbtb): eliminate mbtb trace warning | out-of-order55 | closed | 1 | 2026-03-16T03:00:29Z |
| [#5540](https://github.com/OpenXiangShan/XiangShan/pull/5540) | fix(mbtb): fix typo in trainTouchWay | ngc7331 | closed | 1 | 2026-01-15T14:52:54Z |
| [#5538](https://github.com/OpenXiangShan/XiangShan/pull/5538) | fix(Redirect): fix redirect and Topdown | sinceforYy | closed | 2 | 2026-01-20T08:44:07Z |
| [#5536](https://github.com/OpenXiangShan/XiangShan/pull/5536) | fix(pmu): adjust condition logic for specific performance counters | my-mayfly | closed | 1 | 2026-01-21T08:42:21Z |
| [#5526](https://github.com/OpenXiangShan/XiangShan/pull/5526) | fix(WriteBuffer): fix victim selection logic when setIdx matches | sleep-zzz | closed | 1 | 2026-01-14T02:52:39Z |
| [#5517](https://github.com/OpenXiangShan/XiangShan/pull/5517) | timing(utage): train and predict using the history from the previous cycle | my-mayfly | closed | 9 | 2026-06-15T14:50:41Z |
| [#5512](https://github.com/OpenXiangShan/XiangShan/pull/5512) | fix(LSQ): connect exception buffer enq.{sq/lq}Idx | Anzooooo | closed | 1 | 2026-01-13T02:36:30Z |
| [#5481](https://github.com/OpenXiangShan/XiangShan/pull/5481) | fix(ras): prevent incorrect RAS pointer updates under FTQ backpressure | my-mayfly | closed | 1 | 2026-01-07T03:59:07Z |
| [#5475](https://github.com/OpenXiangShan/XiangShan/pull/5475) | timing(CSR, Redirect): split targetPc into trap and xret paths | wissygh | closed | 1 | 2026-01-05T02:15:18Z |
| [#5469](https://github.com/OpenXiangShan/XiangShan/pull/5469) | fix(ghr): fix ghr losing updates when !s0_fire | sleep-zzz | closed | 2 | 2026-01-06T08:31:03Z |
| [#5465](https://github.com/OpenXiangShan/XiangShan/pull/5465) | fix(Backend, Region): use `basicDebugEn` for `diffVl` debug IO | wissygh | closed | 1 | 2025-12-31T08:55:32Z |
| [#5462](https://github.com/OpenXiangShan/XiangShan/pull/5462) | timing(CtrlBlock, Redirect): Move selection of oldestExuRedirect from ctrlblock to intRegion | wissygh | closed | 1 | 2025-12-31T01:51:47Z |
| [#5461](https://github.com/OpenXiangShan/XiangShan/pull/5461) | fix(tage): fix tage use meta condition | TheKiteRunner24 | closed | 1 | 2026-01-07T07:20:24Z |
| [#5459](https://github.com/OpenXiangShan/XiangShan/pull/5459) | fix(ifu): enable MMIO blocking and fix uncache exception assert | my-mayfly | closed | 4 | 2026-01-07T04:01:36Z |
| [#5441](https://github.com/OpenXiangShan/XiangShan/pull/5441) | chore(NewCSR): fix RegNext error usage | sinceforYy | closed | 1 | 2026-01-21T07:08:24Z |
| [#5440](https://github.com/OpenXiangShan/XiangShan/pull/5440) | fix(CSR): add CSRs.scala to keep track of CSR addresses | wissygh | closed | 1 | 2025-12-29T05:59:47Z |
| [#5434](https://github.com/OpenXiangShan/XiangShan/pull/5434) | submodule(ready-to-run): bump nemu to fix vfredusum | lewislzh | closed | 1 | 2025-12-26T09:19:16Z |
| [#5433](https://github.com/OpenXiangShan/XiangShan/pull/5433) | timing(tage): delay write one cycle for better timing | TheKiteRunner24 | closed | 1 | 2025-12-29T08:57:54Z |
| [#5427](https://github.com/OpenXiangShan/XiangShan/pull/5427) | Cherry-pick mvendorid from master | wissygh | closed | 2 | 2025-12-25T09:39:44Z |
| [#5422](https://github.com/OpenXiangShan/XiangShan/pull/5422) | fix(decode): fix priority for CSR read vl/vlenb causing EX_II | sinceforYy | closed | 1 | 2025-12-25T08:06:31Z |
| [#5421](https://github.com/OpenXiangShan/XiangShan/pull/5421) | fix(CtrlBlock): fix `rasAction` when commit | wissygh | closed | 1 | 2025-12-24T08:44:49Z |
| [#5420](https://github.com/OpenXiangShan/XiangShan/pull/5420) | fix(decode): fix priority for CSR read vl/vlenb causing EX_II | sinceforYy | closed | 1 | 2025-12-25T01:44:22Z |
| [#5418](https://github.com/OpenXiangShan/XiangShan/pull/5418) | timing(mbtb): latch write req before it goes into writebuffer | ngc7331 | closed | 1 | 2025-12-31T08:39:05Z |
| [#5417](https://github.com/OpenXiangShan/XiangShan/pull/5417) | timing(abtb): change abtb sram to 32x112 for better timing | out-of-order55 | closed | 2 | 2025-12-31T09:22:21Z |
| [#5415](https://github.com/OpenXiangShan/XiangShan/pull/5415) | fix(StoreUnit): fix multi-writeback when storeMisalignBuffer full | weidingliu | closed | 1 | 2025-12-25T06:21:44Z |
| [#5413](https://github.com/OpenXiangShan/XiangShan/pull/5413) | chore(backend): remove some connection with 0.U | xiaofeibao-xjtu | closed | 2 | 2025-12-25T06:44:50Z |
| [#5405](https://github.com/OpenXiangShan/XiangShan/pull/5405) | chore(backend): remove dead code | huxuan0307 | closed | 2 | 2025-12-22T09:31:17Z |
| [#5399](https://github.com/OpenXiangShan/XiangShan/pull/5399) | fix(bpu): fix decoupled train | ngc7331 | closed | 1 | 2025-12-24T08:19:24Z |
| [#5398](https://github.com/OpenXiangShan/XiangShan/pull/5398) | fix(perfCount): fix dispatch stall cycle | xiaofeibao-xjtu | closed | 1 | 2025-12-22T02:19:23Z |
| [#5395](https://github.com/OpenXiangShan/XiangShan/pull/5395) | refactor(Frontend): follow new style guide & fix IDE warnings | ngc7331 | closed | 4 | 2025-12-22T07:35:38Z |
| [#5392](https://github.com/OpenXiangShan/XiangShan/pull/5392) | fix(ittage): fix condition of IttageTable readWriteConflict | rich-cake | closed | 1 | 2025-12-22T07:25:05Z |
| [#5383](https://github.com/OpenXiangShan/XiangShan/pull/5383) | fix(mbtb,tage): fix basetable drop write counter typo | ngc7331 | closed | 1 | 2025-12-18T07:24:49Z |
| [#5378](https://github.com/OpenXiangShan/XiangShan/pull/5378) | fix(CtrlBlock, Redirect): reduce 1 cycle for redirect | wissygh | closed | 2 | 2025-12-26T01:48:46Z |
| [#5372](https://github.com/OpenXiangShan/XiangShan/pull/5372) | feat(pmu): add correct-path branch mispredict statistics. | eastonman | closed | 2 | 2025-12-19T08:07:27Z |
| [#5370](https://github.com/OpenXiangShan/XiangShan/pull/5370) | fix(pmu): fix s3Override_takenMismatch & ITTAGEMissBubble | rich-cake | closed | 1 | 2025-12-16T03:28:22Z |
| [#5368](https://github.com/OpenXiangShan/XiangShan/pull/5368) | refactor(backend): separate vl src config in every params class of Backend | huxuan0307 | closed | 6 | 2026-01-04T06:50:24Z |
| [#5367](https://github.com/OpenXiangShan/XiangShan/pull/5367) | fix(CSR, Mvendorid): Modify the value of `mvendorid` | wissygh | closed | 2 | 2025-12-17T09:11:39Z |
| [#5365](https://github.com/OpenXiangShan/XiangShan/pull/5365) | fix(L1StreamPrefetcher): change L1 and L2 depth | happy-lx | closed | 1 | 2025-12-18T12:52:12Z |
| [#5353](https://github.com/OpenXiangShan/XiangShan/pull/5353) | fix(pmu): enable ras pmu in bpu | out-of-order55 | closed | 1 | 2025-12-16T03:26:52Z |
| [#5352](https://github.com/OpenXiangShan/XiangShan/pull/5352) | fix(LoadQueueRAW): `storeIn.wlineflag` needs a one-cycle delay | Anzooooo | closed | 1 | 2025-12-12T08:23:54Z |
| [#5347](https://github.com/OpenXiangShan/XiangShan/pull/5347) | feat(perf): add perfQueue and collect perf statistics when commit | Yan-Muzi | closed | 1 | 2025-12-12T02:27:09Z |
| [#5345](https://github.com/OpenXiangShan/XiangShan/pull/5345) | fix(tage): fix cfiPc typo | TheKiteRunner24 | closed | 1 | 2025-12-11T03:07:48Z |
| [#5344](https://github.com/OpenXiangShan/XiangShan/pull/5344) | fix(resolve): bpu enqueue flush should not consider flag | Yan-Muzi | closed | 1 | 2025-12-11T03:40:56Z |
| [#5342](https://github.com/OpenXiangShan/XiangShan/pull/5342) | misc: update backend code-owners | lewislzh | closed | 1 | 2025-12-11T02:54:45Z |
| [#5340](https://github.com/OpenXiangShan/XiangShan/pull/5340) | fix(pmu): fix connection of TopDown interface in backend | sinceforYy | closed | 1 | 2025-12-10T10:27:27Z |
| [#5339](https://github.com/OpenXiangShan/XiangShan/pull/5339) | fix(pmu): fix topdown counters wiring | eastonman | closed | 1 | 2025-12-11T03:36:37Z |
| [#5332](https://github.com/OpenXiangShan/XiangShan/pull/5332) | fix(pmu): update bpu s1 prediction source | Erlkonigal | closed | 1 | 2025-12-10T10:22:44Z |
| [#5327](https://github.com/OpenXiangShan/XiangShan/pull/5327) | fix(LoadQueueReplay): fix dcache miss block | weidingliu | closed | 1 | 2025-12-08T10:34:21Z |
| [#5326](https://github.com/OpenXiangShan/XiangShan/pull/5326) | fix(ubtb): fix ubtb t0_hitT1Update condition | out-of-order55 | closed | 1 | 2025-12-11T03:17:19Z |
| [#5325](https://github.com/OpenXiangShan/XiangShan/pull/5325) | fix(tage): fix width typo for TageFoldedHist.forIdx | ngc7331 | closed | 1 | 2025-12-08T07:22:27Z |
| [#5324](https://github.com/OpenXiangShan/XiangShan/pull/5324) | chore(backend): remove some dead code | Squareless-XD | closed | 1 | 2025-12-24T06:53:21Z |
| [#5321](https://github.com/OpenXiangShan/XiangShan/pull/5321) | fix(ras): correct speculative pushAddr and enable return stack | my-mayfly | closed | 1 | 2025-12-10T10:08:59Z |
| [#5320](https://github.com/OpenXiangShan/XiangShan/pull/5320) | feat(phr): add the diff cnt btw predFoldedHist and trainFoldedHist | sleep-zzz | closed | 1 | 2025-12-09T02:45:49Z |
| [#5319](https://github.com/OpenXiangShan/XiangShan/pull/5319) | feat(mbtb): add write reason counter | ngc7331 | closed | 1 | 2025-12-09T07:53:00Z |
| [#5317](https://github.com/OpenXiangShan/XiangShan/pull/5317) | fix(frontend): bpu redirect need cfiPc instead of startPc | ngc7331 | closed | 1 | 2025-12-08T07:11:16Z |
| [#5306](https://github.com/OpenXiangShan/XiangShan/pull/5306) | feat(tage): use AddrFields & allow different NumWay for tables | ngc7331 | closed | 3 | 2025-12-05T05:47:04Z |
| [#5302](https://github.com/OpenXiangShan/XiangShan/pull/5302) | fix(mbtb): encode train mask to one-hot & pass it to replacer | ngc7331 | closed | 3 | 2025-12-05T03:08:17Z |
| [#5295](https://github.com/OpenXiangShan/XiangShan/pull/5295) | feat(AddrField): add extract methods | ngc7331 | closed | 1 | 2025-12-04T03:03:25Z |
| [#5291](https://github.com/OpenXiangShan/XiangShan/pull/5291) | timing(BypassNetwork): remove clock gate of bypass2DataVec | xiaofeibao-xjtu | closed | 1 | 2025-12-03T03:11:45Z |
| [#5276](https://github.com/OpenXiangShan/XiangShan/pull/5276) | fix(frontend): add `suffix` param to SRAMTemplate to prevent warning | ngc7331 | closed | 1 | 2025-12-01T11:22:38Z |
| [#5274](https://github.com/OpenXiangShan/XiangShan/pull/5274) | feat(log): add AddrField util to print address fields | ngc7331 | closed | 1 | 2025-12-01T11:18:55Z |
| [#5273](https://github.com/OpenXiangShan/XiangShan/pull/5273) | fix(resolve): condition of bpu enq overrides that of backend redirect | Yan-Muzi | closed | 1 | 2025-12-01T08:00:36Z |
| [#5272](https://github.com/OpenXiangShan/XiangShan/pull/5272) | refactor(prefetchmonitor):remove fdpmonitor and fix some statistical bugs | ywlcode | closed | 10 | 2025-12-23T07:17:21Z |
| [#5271](https://github.com/OpenXiangShan/XiangShan/pull/5271) | fix(bpu,ftq): s3_s1PredictionSource use s2_fire instead of s1_fire | Erlkonigal | closed | 1 | 2025-12-02T10:46:40Z |
| [#5270](https://github.com/OpenXiangShan/XiangShan/pull/5270) | fix(MemBlock): adjust the priority of misalign exception | Anzooooo | closed | 1 | 2025-12-01T16:30:26Z |
| [#5269](https://github.com/OpenXiangShan/XiangShan/pull/5269) | fix(MemBlock): adjust the logic where tilelink error generate exception | Anzooooo | closed | 1 | 2025-12-01T08:50:07Z |
| [#5266](https://github.com/OpenXiangShan/XiangShan/pull/5266) | fix(abtb): fix taken ctr update logic | TheKiteRunner24 | closed | 1 | 2025-12-01T11:16:43Z |
| [#5265](https://github.com/OpenXiangShan/XiangShan/pull/5265) | feat(tage): add more perf counters | eastonman | closed | 1 | 2025-12-09T03:06:02Z |
| [#5259](https://github.com/OpenXiangShan/XiangShan/pull/5259) | fix(wakeup): fix bug of csr wakeup | xiaofeibao-xjtu | closed | 1 | 2025-12-01T01:28:39Z |
| [#5255](https://github.com/OpenXiangShan/XiangShan/pull/5255) | fix(mbtb): fix internalBank flush and write conflict | sleep-zzz | closed | 1 | 2025-11-26T03:28:01Z |
| [#5254](https://github.com/OpenXiangShan/XiangShan/pull/5254) | fix(tage): fix tage allocate logic | TheKiteRunner24 | closed | 1 | 2025-11-26T07:29:18Z |
| [#5252](https://github.com/OpenXiangShan/XiangShan/pull/5252) | fix(tage): fix new entry taken ctr init value | TheKiteRunner24 | closed | 1 | 2025-11-26T06:52:02Z |
| [#5251](https://github.com/OpenXiangShan/XiangShan/pull/5251) | fix(tage): correct wrong parameter usage in condTrace signal | my-mayfly | closed | 1 | 2025-11-26T07:30:16Z |
| [#5244](https://github.com/OpenXiangShan/XiangShan/pull/5244) | fix(ittage): delay startVAddr & phr from io.train for update | rich-cake | closed | 1 | 2025-11-27T08:42:15Z |
| [#5242](https://github.com/OpenXiangShan/XiangShan/pull/5242) | timing(ICache,Ifu): remove sameCycle=true in pmp | ngc7331 | closed | 1 | 2025-11-25T06:23:24Z |
| [#5238](https://github.com/OpenXiangShan/XiangShan/pull/5238) | fix(resolve): flush resolve queue with wrong backend pointer | Yan-Muzi | closed | 1 | 2025-11-21T02:15:40Z |
| [#5233](https://github.com/OpenXiangShan/XiangShan/pull/5233) | fix(Unalign, Store): fix corner case of unalign store | weidingliu | closed | 1 | 2025-11-24T07:47:26Z |
| [#5232](https://github.com/OpenXiangShan/XiangShan/pull/5232) | refactor(ICache): refactoring to prepare for cleaner 2-fetch | ngc7331 | closed | 8 | 2025-11-27T09:26:05Z |
| [#5228](https://github.com/OpenXiangShan/XiangShan/pull/5228) | fix(DCache): fix timing mismatch of corrupt in DCache forwarding | linjuanZ | closed | 1 | 2025-11-20T20:56:20Z |
| [#5225](https://github.com/OpenXiangShan/XiangShan/pull/5225) | fix(resolve): drop entries that has been overwritten by BP | Yan-Muzi | closed | 1 | 2025-11-21T09:33:59Z |
| [#5215](https://github.com/OpenXiangShan/XiangShan/pull/5215) | fix(CSR, NMI): fix the logic for gating `nmi` | sinceforYy | closed | 1 | 2025-11-18T02:41:22Z |
| [#5201](https://github.com/OpenXiangShan/XiangShan/pull/5201) | fix(resolve): width of `bpTrainStallCnt` | Yan-Muzi | closed | 1 | 2025-11-17T06:37:00Z |
| [#5197](https://github.com/OpenXiangShan/XiangShan/pull/5197) | fix(abtb): hold abtb output when bpu s1 not fire | TheKiteRunner24 | closed | 1 | 2025-11-14T12:08:07Z |
| [#5189](https://github.com/OpenXiangShan/XiangShan/pull/5189) | fix(TLB): gpaddr should be same to vaddr when onlyS2 | cebarobot | closed | 1 | 2025-11-08T04:22:37Z |
| [#5185](https://github.com/OpenXiangShan/XiangShan/pull/5185) | timing(LRQ, DCache): optimize timing | jin120811 | closed | 11 | 2025-11-08T08:03:41Z |
| [#5184](https://github.com/OpenXiangShan/XiangShan/pull/5184) | fix(ittage): select lowest-index branch when multiple-train to avoid assertion | rich-cake | closed | 1 | 2025-11-12T02:23:54Z |
| [#5181](https://github.com/OpenXiangShan/XiangShan/pull/5181) | fix(mbtb): fix the error of multi-hit flush more than 1 | sleep-zzz | closed | 1 | 2025-11-07T08:11:08Z |
| [#5170](https://github.com/OpenXiangShan/XiangShan/pull/5170) | PTW timing (Should be rebase & merge) | good-circle | closed | 5 | 2025-11-07T02:27:30Z |
| [#5167](https://github.com/OpenXiangShan/XiangShan/pull/5167) | refactor(MemBlock): adjust the interface for issue and writeback | linjuanZ | closed | 5 | 2025-11-23T11:19:07Z |
| [#5164](https://github.com/OpenXiangShan/XiangShan/pull/5164) | fix(VsegmentUnit): fix latch of paddr when element is unalign | weidingliu | closed | 1 | 2025-11-07T09:33:44Z |
| [#5162](https://github.com/OpenXiangShan/XiangShan/pull/5162) | refactor(tage): add BaseTableAlignBank wrapper | ngc7331 | closed | 1 | 2025-11-10T09:42:05Z |
| [#5160](https://github.com/OpenXiangShan/XiangShan/pull/5160) | fix(abtb): fix condition of writing new entry to abtb | rich-cake | closed | 2 | 2025-11-05T10:02:14Z |
| [#5159](https://github.com/OpenXiangShan/XiangShan/pull/5159) | refactor(mbtb): re-structure code | ngc7331 | closed | 11 | 2025-11-04T11:11:15Z |
| [#5156](https://github.com/OpenXiangShan/XiangShan/pull/5156) | fix(tage): fix tage performance | TheKiteRunner24 | closed | 1 | 2025-11-14T11:14:30Z |
| [#5155](https://github.com/OpenXiangShan/XiangShan/pull/5155) | fix(Tage,mbtb): fix next set idx logic | TheKiteRunner24 | closed | 2 | 2025-11-03T03:08:49Z |
| [#5153](https://github.com/OpenXiangShan/XiangShan/pull/5153) | fix(abtb): hold sram read to prevent x-state | ngc7331 | closed | 1 | 2025-10-30T08:26:32Z |
| [#5149](https://github.com/OpenXiangShan/XiangShan/pull/5149) | fix(resolve): flush branches that are not flushed by backend redirect | Yan-Muzi | closed | 1 | 2025-11-14T03:06:52Z |
| [#5147](https://github.com/OpenXiangShan/XiangShan/pull/5147) | fix(ibuffer): do not store first encountered exception when currentLastHalfRvi | Erlkonigal | closed | 1 | 2025-10-29T02:34:35Z |
| [#5143](https://github.com/OpenXiangShan/XiangShan/pull/5143) | fix(writeBuffer): fix writePortValid not assigned correctly error | sleep-zzz | closed | 1 | 2025-10-28T03:05:13Z |
| [#5139](https://github.com/OpenXiangShan/XiangShan/pull/5139) | fix(phr): fix phrPtr meta error | sleep-zzz | closed | 2 | 2025-10-28T02:57:59Z |
| [#5135](https://github.com/OpenXiangShan/XiangShan/pull/5135) | chore(backend): improve code quality | xiaofeibao-xjtu | closed | 1 | 2025-11-20T06:08:55Z |
| [#5134](https://github.com/OpenXiangShan/XiangShan/pull/5134) | fix(WriteBuffer): fix writebuffer writeTouchVec idx usage error | sleep-zzz | closed | 1 | 2025-10-23T06:29:29Z |
| [#5132](https://github.com/OpenXiangShan/XiangShan/pull/5132) | fix(ras): fix rasPtr width based on wrong stack size | my-mayfly | closed | 1 | 2025-10-23T06:40:39Z |
| [#5131](https://github.com/OpenXiangShan/XiangShan/pull/5131) | fix(csr): fix csr out-of-order read xip registers | sinceforYy | closed | 1 | 2025-11-20T02:38:19Z |
| [#5122](https://github.com/OpenXiangShan/XiangShan/pull/5122) | fix(ifu): fix fetch size assertion not accounting for flush scenario. | my-mayfly | closed | 1 | 2025-10-17T02:11:30Z |
| [#5118](https://github.com/OpenXiangShan/XiangShan/pull/5118) | fix(sc): fix sc update logic error | sleep-zzz | closed | 2 | 2025-10-16T09:57:41Z |
| [#5114](https://github.com/OpenXiangShan/XiangShan/pull/5114) | fix(L1TLB): ignore addr when hfence.vvma or sfence.vma when v=1 | cebarobot | closed | 1 | 2025-10-30T06:01:14Z |
| [#5113](https://github.com/OpenXiangShan/XiangShan/pull/5113) | fix(mbtb): do not filter branches with position equal to start | ngc7331 | closed | 1 | 2025-10-16T07:40:35Z |
| [#5107](https://github.com/OpenXiangShan/XiangShan/pull/5107) | fix(resolve queue): only valid entries can be flushed | Yan-Muzi | closed | 1 | 2025-10-14T01:27:48Z |
| [#5104](https://github.com/OpenXiangShan/XiangShan/pull/5104) | fix(resolve queue): flushed entries should remain flushed | Yan-Muzi | closed | 1 | 2025-10-13T03:03:45Z |
| [#5103](https://github.com/OpenXiangShan/XiangShan/pull/5103) | feat(ifu): distinguish between fixedTaken and predTaken for BPU training | my-mayfly | closed | 1 | 2025-10-13T06:11:37Z |
| [#5096](https://github.com/OpenXiangShan/XiangShan/pull/5096) | fix(mbtb): fix typos and fix hitMask position comparison | TheKiteRunner24 | closed | 2 | 2025-10-11T09:29:55Z |
| [#5092](https://github.com/OpenXiangShan/XiangShan/pull/5092) | fix(resolve): enqueue branch slot index | Yan-Muzi | closed | 1 | 2025-10-09T02:28:35Z |
| [#5090](https://github.com/OpenXiangShan/XiangShan/pull/5090) | fix(tage): fix typos, BaseTable use Queue and store meta to Ftq | TheKiteRunner24 | closed | 2 | 2025-10-15T08:46:56Z |
| [#5087](https://github.com/OpenXiangShan/XiangShan/pull/5087) | fix(TLB): fix incorrect TLB level refill when has exception | good-circle | closed | 1 | 2025-09-30T06:07:17Z |
| [#5086](https://github.com/OpenXiangShan/XiangShan/pull/5086) | fix(tage): fix providerIdxOH | TheKiteRunner24 | closed | 1 | 2025-10-09T04:00:56Z |
| [#5085](https://github.com/OpenXiangShan/XiangShan/pull/5085) | fix(resolve): flush entries that have been redirected by backend | Yan-Muzi | closed | 4 | 2025-09-30T03:12:35Z |
| [#5082](https://github.com/OpenXiangShan/XiangShan/pull/5082) | fix(ICache): stall read when updating | ngc7331 | closed | 1 | 2025-09-29T02:21:45Z |
| [#5079](https://github.com/OpenXiangShan/XiangShan/pull/5079) | fix(Closecompress): when rob compress close, fusion which cross two ftq should be cancompressed | lewislzh | closed | 1 | 2025-10-10T06:16:35Z |
| [#5074](https://github.com/OpenXiangShan/XiangShan/pull/5074) | fix(Closecompress): when rob compress close, the brh instruction compress bit cannot be true | lewislzh | closed | 1 | 2025-09-27T07:23:39Z |
| [#5073](https://github.com/OpenXiangShan/XiangShan/pull/5073) | fix(Bitmap): fix bitmap check result wakeup `l0BitmapReg` logic | yxtx1994 | closed | 1 | 2025-10-10T10:28:37Z |
| [#5072](https://github.com/OpenXiangShan/XiangShan/pull/5072) | fix(ICache,Ifu): do not bpuFlush if not valid | ngc7331 | closed | 1 | 2025-09-28T02:21:16Z |
| [#5067](https://github.com/OpenXiangShan/XiangShan/pull/5067) | fix(CSR, NMI): fix the logic for gating `nmi` | sinceforYy | closed | 1 | 2025-11-13T08:43:33Z |
| [#5060](https://github.com/OpenXiangShan/XiangShan/pull/5060) | fix(mbtb): filter out cross-page branches | TheKiteRunner24 | closed | 1 | 2025-09-24T10:15:04Z |
| [#5059](https://github.com/OpenXiangShan/XiangShan/pull/5059) | fix(loadTrigger): the prefetch instructions don’t match trigger at all | wissygh | closed | 1 | 2025-09-25T09:02:28Z |
| [#5058](https://github.com/OpenXiangShan/XiangShan/pull/5058) | fix(fallthrough): fix cfipostion when cross page | ngc7331 | closed | 1 | 2025-09-30T03:07:54Z |
| [#5055](https://github.com/OpenXiangShan/XiangShan/pull/5055) | fix(ICache): bpu s3 flush waylookup & mainPipe s1 | ngc7331 | closed | 1 | 2025-09-25T09:58:31Z |
| [#5054](https://github.com/OpenXiangShan/XiangShan/pull/5054) | fix(ifu): fix s1 flush condition | TheKiteRunner24 | closed | 1 | 2025-09-24T07:53:53Z |
| [#5043](https://github.com/OpenXiangShan/XiangShan/pull/5043) | fix(tage): fix x-state issue | TheKiteRunner24 | closed | 1 | 2025-09-22T10:49:07Z |
| [#5040](https://github.com/OpenXiangShan/XiangShan/pull/5040) | feat(ifu): connect IFU redirect with the return stack | my-mayfly | closed | 2 | 2025-09-24T07:13:58Z |
| [#5035](https://github.com/OpenXiangShan/XiangShan/pull/5035) | fix(ftq): relax the gating of backendException. | my-mayfly | closed | 1 | 2025-09-18T02:13:14Z |
| [#5030](https://github.com/OpenXiangShan/XiangShan/pull/5030) | fix(prefetch): size of counter filter needs to add 1 | Maxpicca-Li | closed | 2 | 2025-09-18T06:05:40Z |
| [#5028](https://github.com/OpenXiangShan/XiangShan/pull/5028) | fix(abtb): fix abtb meta signal X-Propagation | sleep-zzz | closed | 1 | 2025-09-17T10:35:11Z |
| [#5027](https://github.com/OpenXiangShan/XiangShan/pull/5027) | fix(branchUnit): check target when predict and real are all taken | xiaofeibao-xjtu | closed | 1 | 2025-09-17T01:51:47Z |
| [#5019](https://github.com/OpenXiangShan/XiangShan/pull/5019) | fix(ibuffer): receive identifiedCfi and pass it to backend | Yan-Muzi | closed | 1 | 2025-09-12T05:16:39Z |
| [#5018](https://github.com/OpenXiangShan/XiangShan/pull/5018) | fix(Bitmap): fix not need bitmap check logic in LLPTW | yxtx1994 | closed | 1 | 2025-09-14T15:09:55Z |
| [#5016](https://github.com/OpenXiangShan/XiangShan/pull/5016) | fix(Ftq): add ExceptionType.fromBackend & fix write condition in Ftq | ngc7331 | closed | 1 | 2025-09-17T03:23:16Z |
| [#5012](https://github.com/OpenXiangShan/XiangShan/pull/5012) | fix(ifu): rewrite instruction boundary calculation and offset | Yan-Muzi | closed | 3 | 2025-09-22T02:32:39Z |
| [#5008](https://github.com/OpenXiangShan/XiangShan/pull/5008) | fix(ubtb): fix hit detection to resolve multi-hit | ngc7331 | closed | 1 | 2025-09-09T05:25:29Z |
| [#5006](https://github.com/OpenXiangShan/XiangShan/pull/5006) | fix(Vsegment): fix address generation of misaligned split | weidingliu | closed | 2 | 2025-09-06T05:58:25Z |
| [#5005](https://github.com/OpenXiangShan/XiangShan/pull/5005) | fix(prefetch): the statistic of prefetch hit | Maxpicca-Li | closed | 2 | 2025-09-05T08:21:58Z |
| [#5004](https://github.com/OpenXiangShan/XiangShan/pull/5004) | fix(ubtb): alloc new entry for taken branches only | ngc7331 | closed | 1 | 2025-09-04T12:43:17Z |
| [#5003](https://github.com/OpenXiangShan/XiangShan/pull/5003) | fix(RobCompress): fix isRVC transfer logic for new ftqoffset | lewislzh | closed | 1 | 2025-09-04T12:40:20Z |
| [#4997](https://github.com/OpenXiangShan/XiangShan/pull/4997) | fix(MMU): PMM is disabled if MXR is effective | cebarobot | closed | 1 | 2025-11-17T10:15:10Z |
| [#4983](https://github.com/OpenXiangShan/XiangShan/pull/4983) | fix(MMU): TLB freeze when ptw resp in particular cycle | cebarobot | closed | 1 | 2025-09-01T08:51:45Z |
| [#4979](https://github.com/OpenXiangShan/XiangShan/pull/4979) | fix(CSR): fix dpc for trapping to dmode | wissygh | closed | 1 | 2025-08-29T07:30:04Z |
| [#4977](https://github.com/OpenXiangShan/XiangShan/pull/4977) | fix(ubtb): alloc entry for taken branch only | ngc7331 | closed | 1 | 2025-08-28T03:47:11Z |
| [#4971](https://github.com/OpenXiangShan/XiangShan/pull/4971) | fix(FTB): X state in FTB | Yan-Muzi | closed | 1 | 2025-08-25T16:22:15Z |
| [#4968](https://github.com/OpenXiangShan/XiangShan/pull/4968) | fix(Bpu): wait Sram reset to avoid x-state | ngc7331 | closed | 1 | 2025-08-25T07:40:01Z |
| [#4965](https://github.com/OpenXiangShan/XiangShan/pull/4965) | fix(LoadUnit): reaccess the data even if it is a fast replay | Maxpicca-Li | closed | 1 | 2025-09-19T05:44:18Z |
| [#4962](https://github.com/OpenXiangShan/XiangShan/pull/4962) | feat(bp): train BPU with information provided by backend when branches are resolved | Yan-Muzi | closed | 6 | 2025-09-08T00:46:20Z |
| [#4956](https://github.com/OpenXiangShan/XiangShan/pull/4956) | fix(VSegmentUnit): fof instruction writeback origin vl | weidingliu | closed | 1 | 2025-08-18T10:25:04Z |
| [#4944](https://github.com/OpenXiangShan/XiangShan/pull/4944) | feat(rob): std uop use numWB instead of stdWritebacked | xiaofeibao-xjtu | closed | 1 | 2025-08-20T02:39:01Z |
| [#4941](https://github.com/OpenXiangShan/XiangShan/pull/4941) | fix(vlbusytable): remove wakeUpInt to avoid load fast wakes up vsetvli | Ziyue-Zhang | closed | 1 | 2025-08-14T07:15:33Z |
| [#4935](https://github.com/OpenXiangShan/XiangShan/pull/4935) | fix(Bitmap): fix jmp_bitmap_check logic in PtwCache | yxtx1994 | closed | 1 | 2025-08-18T02:52:56Z |
| [#4929](https://github.com/OpenXiangShan/XiangShan/pull/4929) | fix(pma): fix pma RegOut | sinceforYy | closed | 1 | 2025-08-06T08:49:16Z |
| [#4923](https://github.com/OpenXiangShan/XiangShan/pull/4923) | fix(excp): add SWC to exception priorities | sinceforYy | closed | 1 | 2025-09-01T03:14:51Z |
| [#4922](https://github.com/OpenXiangShan/XiangShan/pull/4922) | fix(ifu): fix IBuffer enqueue check for nc instructions. | my-mayfly | closed | 5 | 2025-08-05T02:21:08Z |
| [#4916](https://github.com/OpenXiangShan/XiangShan/pull/4916) | fix(PTW): Fix X-prop caused by using un-initialized stage1Hit in Mux() | forever043 | closed | 1 | 2025-08-19T09:04:30Z |
| [#4915](https://github.com/OpenXiangShan/XiangShan/pull/4915) | fix(CSR): initialize [m\|h\|s]context to 0 | wissygh | closed | 2 | 2025-07-31T10:25:39Z |
| [#4914](https://github.com/OpenXiangShan/XiangShan/pull/4914) | fix(misalign): fixed a hang issue caused by vector misalign | Anzooooo | closed | 4 | 2025-08-02T11:47:22Z |
| [#4913](https://github.com/OpenXiangShan/XiangShan/pull/4913) | fix(TLB): vaddr should be extended to PAddrBitsMax | good-circle | closed | 1 | 2025-07-30T02:16:39Z |
| [#4911](https://github.com/OpenXiangShan/XiangShan/pull/4911) | fix(TLB): fix GPA matching bug in Napot cases | good-circle | closed | 1 | 2025-07-30T02:15:51Z |
| [#4907](https://github.com/OpenXiangShan/XiangShan/pull/4907) | fix(DCache): there also needs memBackTypeMM setting | Maxpicca-Li | closed | 1 | 2025-07-25T06:19:40Z |
| [#4903](https://github.com/OpenXiangShan/XiangShan/pull/4903) | fix(Ifu,InstrUncache): flush mmio fsm | ngc7331 | closed | 1 | 2025-07-24T09:09:26Z |
| [#4900](https://github.com/OpenXiangShan/XiangShan/pull/4900) | fix(L2TLB): fix check condition for Napot pages | good-circle | closed | 1 | 2025-07-24T06:33:47Z |
| [#4899](https://github.com/OpenXiangShan/XiangShan/pull/4899) | fix(MainPipe): fix `s3_data_error_beu` generate logic avoid x state | cz4e | closed | 1 | 2025-07-24T07:07:50Z |
| [#4898](https://github.com/OpenXiangShan/XiangShan/pull/4898) | fix(ICache,Ifu): set memBackTypeMM and memPageTypeNC correctly | ngc7331 | closed | 3 | 2025-07-25T13:00:39Z |
| [#4896](https://github.com/OpenXiangShan/XiangShan/pull/4896) | feat(Bpu): v3 bpu part2 | ngc7331 | closed | 16 | 2025-07-24T04:06:50Z |
| [#4892](https://github.com/OpenXiangShan/XiangShan/pull/4892) | fix(VSegmentUnit): adjust the fullva bit width of the tlb req | Anzooooo | closed | 1 | 2025-07-29T08:03:46Z |
| [#4889](https://github.com/OpenXiangShan/XiangShan/pull/4889) | fix(VSplit): fix judgement of unaligned index vector load/store | weidingliu | closed | 1 | 2025-07-17T03:35:42Z |
| [#4886](https://github.com/OpenXiangShan/XiangShan/pull/4886) | fix(TLB): vbop should drive high the isPrefetch signal | good-circle | closed | 1 | 2025-07-15T02:18:16Z |
| [#4881](https://github.com/OpenXiangShan/XiangShan/pull/4881) | fix(ifu): fix speculative instruction fetch in MMIO region. | my-mayfly | closed | 1 | 2025-07-17T08:50:49Z |
| [#4877](https://github.com/OpenXiangShan/XiangShan/pull/4877) | fix(MisalignBuffer): fix vector misalign request writeback ready | weidingliu | closed | 1 | 2025-07-15T06:43:50Z |
| [#4876](https://github.com/OpenXiangShan/XiangShan/pull/4876) | fix(StoreQueue): fix not set vecExceptionFlagCancel | weidingliu | closed | 1 | 2025-07-15T02:53:03Z |
| [#4874](https://github.com/OpenXiangShan/XiangShan/pull/4874) | fix(rab): correct ismove sent to rab for instraction page fault caused by move elimination | xiaofeibao-xjtu | closed | 1 | 2025-07-11T02:56:16Z |
| [#4869](https://github.com/OpenXiangShan/XiangShan/pull/4869) | fix(Vsplit): fix Vec Store split stuck when misaligned | weidingliu | closed | 1 | 2025-07-15T02:34:55Z |
| [#4865](https://github.com/OpenXiangShan/XiangShan/pull/4865) | fix(VMergeBuffer): fix gpaddr calculation when Unit-Stride triggers an exception | Anzooooo | closed | 1 | 2025-07-05T12:10:44Z |
| [#4863](https://github.com/OpenXiangShan/XiangShan/pull/4863) | fix(DCache): add bypass switch for `L1FlagMetaArray` | cz4e | closed | 1 | 2025-07-07T12:56:26Z |
| [#4859](https://github.com/OpenXiangShan/XiangShan/pull/4859) | fix(StoreQueue): fix misalign forward fail stall | cz4e | closed | 9 | 2025-07-08T03:31:09Z |
| [#4856](https://github.com/OpenXiangShan/XiangShan/pull/4856) | fix(MainPipe): fix mainpipe x state when miss request after miss request | cz4e | closed | 1 | 2025-07-03T02:23:26Z |
| [#4855](https://github.com/OpenXiangShan/XiangShan/pull/4855) | fix(StoreQueue): adjust the stDataReadySqPtr generation logic when misalign | Anzooooo | closed | 1 | - |
| [#4854](https://github.com/OpenXiangShan/XiangShan/pull/4854) | fix(VMergeBuffer): adjust elemidx generation logic when exception | Anzooooo | closed | 1 | 2025-07-03T02:19:47Z |
| [#4853](https://github.com/OpenXiangShan/XiangShan/pull/4853) | fix(VSegmentUnit): flush sbuffer until sbuffer is empty | Anzooooo | closed | 1 | 2025-07-02T09:27:13Z |
| [#4851](https://github.com/OpenXiangShan/XiangShan/pull/4851) | feat(Bpu): v3 bpu part1 | ngc7331 | closed | 4 | 2025-07-01T11:07:27Z |
| [#4850](https://github.com/OpenXiangShan/XiangShan/pull/4850) | fix(VTypeBuffer): fix bug of commitCount, walkCount and spclWalkCount's width | xiaofeibao-xjtu | closed | 1 | 2025-07-03T02:13:36Z |
| [#4849](https://github.com/OpenXiangShan/XiangShan/pull/4849) | feat(ifu): pre-decoding delayed by one cycle; modify pre-decoder and checker submodule.  | my-mayfly | closed | 7 | 2025-07-01T08:56:25Z |
| [#4845](https://github.com/OpenXiangShan/XiangShan/pull/4845) | Backend-v3 | xiaofeibao-xjtu | closed | 20 | 2025-07-25T03:05:58Z |
| [#4842](https://github.com/OpenXiangShan/XiangShan/pull/4842) | fix(MainPipe): fix `extra_meta_resp` overrided | cz4e | closed | 1 | 2025-06-27T09:30:07Z |
| [#4840](https://github.com/OpenXiangShan/XiangShan/pull/4840) | fix(LSU): fix misalignbuffer response acceptance process | Anzooooo | closed | 1 | 2025-06-27T10:11:42Z |
| [#4836](https://github.com/OpenXiangShan/XiangShan/pull/4836) | fix(intr): fix xtopi genertation for second cycle | sinceforYy | closed | 1 | 2025-06-27T09:33:29Z |
| [#4833](https://github.com/OpenXiangShan/XiangShan/pull/4833) | fix(DCacheWrapper): fix `L1PrefetchSourceArray` write-after-read bypass logic | cz4e | closed | 1 | - |
| [#4831](https://github.com/OpenXiangShan/XiangShan/pull/4831) | fix(VSegmentUnit): `dcache.error_delayed` will be one beat latet than `dcache.resp` | Anzooooo | closed | 1 | 2025-06-24T05:01:42Z |
| [#4829](https://github.com/OpenXiangShan/XiangShan/pull/4829) | fix(LLPTW): fix bug in handling both virtualized & non-virtualized cases | good-circle | closed | 1 | 2025-06-24T04:47:15Z |
| [#4828](https://github.com/OpenXiangShan/XiangShan/pull/4828) | fix(MMU): fix the condition for identifying napot pages | good-circle | closed | 1 | 2025-07-11T09:46:17Z |
| [#4825](https://github.com/OpenXiangShan/XiangShan/pull/4825) | fix(CSR, NMI): fix the logic for clearing `nmip` | wissygh | closed | 1 | 2025-06-23T07:24:35Z |
| [#4820](https://github.com/OpenXiangShan/XiangShan/pull/4820) | fix(vleff): fix vleff writeback `vl` and `vta` | weidingliu | closed | 1 | 2025-06-24T08:15:33Z |
| [#4817](https://github.com/OpenXiangShan/XiangShan/pull/4817) | fix(csr): set xstatus.VS dirty when a vector memory access instr has exception | sinceforYy | closed | 1 | 2025-06-23T02:29:33Z |
| [#4814](https://github.com/OpenXiangShan/XiangShan/pull/4814) | fix(ICache): do not check meta(1) parity if !s1_doubleline | ngc7331 | closed | 2 | 2025-06-24T05:14:30Z |
| [#4812](https://github.com/OpenXiangShan/XiangShan/pull/4812) | fix(LSU): cbozero also needs to perform raw checks on the pipe | Anzooooo | closed | 1 | 2025-06-15T09:18:32Z |
| [#4811](https://github.com/OpenXiangShan/XiangShan/pull/4811) | fix(MMU): global entries should always hit even asid switch | good-circle | closed | 3 | 2025-06-15T09:14:55Z |
| [#4810](https://github.com/OpenXiangShan/XiangShan/pull/4810) | perf(MemBlock): optimize L1DCache index | jin120811 | closed | 250 | - |
| [#4809](https://github.com/OpenXiangShan/XiangShan/pull/4809) | fix(WritebackQueue): fix `probe` merge with `release` with same PA | cz4e | closed | 2 | - |
| [#4807](https://github.com/OpenXiangShan/XiangShan/pull/4807) | fix(StoreUnit): vec misalignBufferNack need to mergebuffer | Anzooooo | closed | 1 | 2025-06-15T09:09:21Z |
| [#4805](https://github.com/OpenXiangShan/XiangShan/pull/4805) | fix(LoadQueueRAR): fix `paddrModule` not match by replay | cz4e | closed | 2 | - |
| [#4803](https://github.com/OpenXiangShan/XiangShan/pull/4803) | fix(LoadQueueRAR): fix `paddrModule` write delay cycle | cz4e | closed | 1 | 2025-06-13T02:15:39Z |
| [#4802](https://github.com/OpenXiangShan/XiangShan/pull/4802) | fix(csr): fix CSR rData when CSR claim IMSIC | sinceforYy | closed | 1 | 2025-06-15T13:42:26Z |
| [#4801](https://github.com/OpenXiangShan/XiangShan/pull/4801) | submodule(rocket-chip): bump rocket-chip to fix dm_extTrigger | wissygh | closed | 1 | 2025-06-15T13:42:17Z |
| [#4800](https://github.com/OpenXiangShan/XiangShan/pull/4800) | fix(vsegment): vec segment should also respond to bus error | Anzooooo | closed | 1 | 2025-06-11T06:31:06Z |
| [#4799](https://github.com/OpenXiangShan/XiangShan/pull/4799) | fix(LSU): enable hardwareError exception for all memory access types | Anzooooo | closed | 1 | 2025-06-11T06:30:02Z |
| [#4795](https://github.com/OpenXiangShan/XiangShan/pull/4795) | fix(MetaArray): add bypass to read when write s0 | Anzooooo | closed | 1 | 2025-06-11T02:50:25Z |
| [#4793](https://github.com/OpenXiangShan/XiangShan/pull/4793) | fix(TLB): correct gpaddr generating for handle_block | cebarobot | closed | 1 | 2025-06-09T07:53:31Z |
| [#4792](https://github.com/OpenXiangShan/XiangShan/pull/4792) | fix(VLSU): vector ld/st does not generate misalign exception when mmio | Anzooooo | closed | 1 | 2025-06-15T08:59:45Z |
| [#4790](https://github.com/OpenXiangShan/XiangShan/pull/4790) | refactor(prefetch): add a wrapper and parameterization | Maxpicca-Li | closed | 14 | 2025-06-13T05:21:57Z |
| [#4788](https://github.com/OpenXiangShan/XiangShan/pull/4788) | fix(LLPTW): should block hptw req when dup with mem_out | good-circle | closed | 1 | 2025-06-10T09:33:46Z |
| [#4782](https://github.com/OpenXiangShan/XiangShan/pull/4782) | fix(MainPipe): adjust `s2_can_go_to_s3` select priority for refill ecc inject lead to tag miss | cz4e | closed | 2 | 2025-06-05T16:28:18Z |
| [#4780](https://github.com/OpenXiangShan/XiangShan/pull/4780) | fix(TLB): use the same hit logic for block as for bypass | cebarobot | closed | 1 | 2025-06-05T10:14:59Z |
| [#4779](https://github.com/OpenXiangShan/XiangShan/pull/4779) | fix(DiffStoreEvent): unify the difftest time of various store | Maxpicca-Li | closed | 1 | 2025-06-05T16:29:23Z |
| [#4776](https://github.com/OpenXiangShan/XiangShan/pull/4776) | feat: use custom HINT for simulation debug trigger | Tang-Haojin | closed | 2 | 2025-06-05T08:49:06Z |
| [#4774](https://github.com/OpenXiangShan/XiangShan/pull/4774) | fix(LoadQueueUncache): fix missing hardware error | Maxpicca-Li | closed | 1 | 2025-06-05T03:28:35Z |
| [#4769](https://github.com/OpenXiangShan/XiangShan/pull/4769) | fix(PMP): take a beat for `cmd` | Anzooooo | closed | 1 | 2025-06-05T03:34:38Z |
| [#4755](https://github.com/OpenXiangShan/XiangShan/pull/4755) | fix(MainPipe): add reg enable for mainpipe `ecc_delayed` | cz4e | closed | 3 | 2025-06-05T16:31:19Z |
| [#4754](https://github.com/OpenXiangShan/XiangShan/pull/4754) | fix(LLPTW): first_s2xlate_fault should be true when check_g_perm_fail | good-circle | closed | 1 | 2025-05-30T02:47:04Z |
| [#4753](https://github.com/OpenXiangShan/XiangShan/pull/4753) | fix(MainPipe): fix report error for probe/atomic request | cz4e | closed | 1 | 2025-06-05T12:50:54Z |
| [#4751](https://github.com/OpenXiangShan/XiangShan/pull/4751) | fix(LoadUnit): misaligned exception addr should use split addr | Anzooooo | closed | 1 | 2025-05-30T07:27:13Z |
| [#4750](https://github.com/OpenXiangShan/XiangShan/pull/4750) | fix(LoadPipe): load will not enter missqueue when btot grow fail | cz4e | closed | 3 | 2025-05-30T07:27:37Z |
| [#4748](https://github.com/OpenXiangShan/XiangShan/pull/4748) | fix(TLB): offset of paddr is vaddr[11:0] | good-circle | closed | 1 | 2025-05-29T09:22:20Z |
| [#4742](https://github.com/OpenXiangShan/XiangShan/pull/4742) | fix(csr, perf): skip CSR read xtopi | sinceforYy | closed | 1 | 2025-05-28T09:18:40Z |
| [#4741](https://github.com/OpenXiangShan/XiangShan/pull/4741) | fix(MainPipe): fix probe/replace stall for alias scheme | cz4e | closed | 2 | 2025-06-06T01:33:03Z |
| [#4737](https://github.com/OpenXiangShan/XiangShan/pull/4737) | fix(DCache): use `ParallelPrioirtyMux` instead of `ParallelMux` | cz4e | closed | 1 | 2025-05-27T02:41:56Z |
| [#4736](https://github.com/OpenXiangShan/XiangShan/pull/4736) | fix(trace): move checking xret from commit to rename | wissygh | closed | 1 | 2025-05-28T02:15:02Z |
| [#4731](https://github.com/OpenXiangShan/XiangShan/pull/4731) | fix(StoreQueue): fix vecExceptionFlag when flow is misaligned | weidingliu | closed | 3 | 2025-06-04T10:11:02Z |
| [#4730](https://github.com/OpenXiangShan/XiangShan/pull/4730) | fix(StoreUnit):  mask for the cmo instr is 0xFFFF | Anzooooo | closed | 1 | 2025-05-26T12:56:49Z |
| [#4729](https://github.com/OpenXiangShan/XiangShan/pull/4729) | fix(LoadPipe): fix `prefetch_w` btot stall | cz4e | closed | 1 | 2025-05-25T10:38:00Z |
| [#4728](https://github.com/OpenXiangShan/XiangShan/pull/4728) | fix(jump, branch): fix wrong pc sext when sv48x4 | Tang-Haojin | closed | 1 | 2025-05-26T02:24:08Z |
| [#4725](https://github.com/OpenXiangShan/XiangShan/pull/4725) | fix(ICache,Ifu,Perf): fix perfcounters | ngc7331 | closed | 3 | 2025-05-29T09:37:33Z |
| [#4724](https://github.com/OpenXiangShan/XiangShan/pull/4724) | fix(PMA): sc / amo should report af when !atomic | good-circle | closed | 1 | 2025-05-24T04:47:15Z |
| [#4723](https://github.com/OpenXiangShan/XiangShan/pull/4723) | fix(sstateenx): generate sstateen[1\|2\|3]Module to verilog | wissygh | closed | 1 | 2025-05-26T02:24:39Z |
| [#4720](https://github.com/OpenXiangShan/XiangShan/pull/4720) | fix(MainPipe): only requests from sbuffer are allowed to return replay response | cz4e | closed | 1 | 2025-05-22T10:59:18Z |
| [#4719](https://github.com/OpenXiangShan/XiangShan/pull/4719) | fix(MainPipe): add reg enable for mainpipe `ecc_delayed` | cz4e | closed | 4 | - |
| [#4718](https://github.com/OpenXiangShan/XiangShan/pull/4718) | fix(MainPipe): fix pseudo ecc inject report address | cz4e | closed | 1 | 2025-05-22T07:02:40Z |
| [#4717](https://github.com/OpenXiangShan/XiangShan/pull/4717) | fix(Uncache): add bus error handle for uncache store | Maxpicca-Li | closed | 1 | 2025-05-22T07:58:48Z |
| [#4711](https://github.com/OpenXiangShan/XiangShan/pull/4711) | fix(StoreQueue): redirect logic for vector exception | Anzooooo | closed | 1 | 2025-05-21T06:48:34Z |
| [#4702](https://github.com/OpenXiangShan/XiangShan/pull/4702) | fix(StoreUnit): cbo requires read permission | Anzooooo | closed | 1 | 2025-05-21T06:47:33Z |
| [#4698](https://github.com/OpenXiangShan/XiangShan/pull/4698) | fix(csr): CSRR instruction read xtopi/xtopei inOrder | sinceforYy | closed | 1 | 2025-05-16T11:26:51Z |
| [#4696](https://github.com/OpenXiangShan/XiangShan/pull/4696) | fix(csr): add [m\|h\|s]context for sdtrig extension | wissygh | closed | 2 | 2025-05-16T11:33:45Z |
| [#4694](https://github.com/OpenXiangShan/XiangShan/pull/4694) | fix(TLB): valididx(i) should all be true when isSuperPage | good-circle | closed | 1 | 2025-05-16T11:17:16Z |
| [#4686](https://github.com/OpenXiangShan/XiangShan/pull/4686) | fix(docker): Forward variables in the make command line to docker | forever043 | closed | 1 | 2025-05-14T10:40:02Z |
| [#4676](https://github.com/OpenXiangShan/XiangShan/pull/4676) | fix(smstateen): add [m\|h\|s]stateen[1\|2\|3] CSRs | wissygh | closed | 2 | 2025-05-16T07:04:21Z |
| [#4674](https://github.com/OpenXiangShan/XiangShan/pull/4674) | fix(LoadUnit): preventing raw jams caused by misalignment | Anzooooo | closed | 1 | 2025-05-09T18:18:50Z |
| [#4673](https://github.com/OpenXiangShan/XiangShan/pull/4673) | fix(LoadUnit): misaligned exception addr should use split addr | Anzooooo | closed | 1 | 2025-05-09T18:18:27Z |
| [#4672](https://github.com/OpenXiangShan/XiangShan/pull/4672) | fix(Smcsrind, Smstateen, Smaia): add missing permit check | NewPaulWalker | closed | 3 | 2025-05-15T13:07:48Z |
| [#4671](https://github.com/OpenXiangShan/XiangShan/pull/4671) | fix(CSR): fix trapInst update logic | lewislzh | closed | 2 | 2025-05-09T18:32:16Z |
| [#4663](https://github.com/OpenXiangShan/XiangShan/pull/4663) | fix(StoreQueue): cbozero flag should not be set on exception | Anzooooo | closed | 1 | 2025-05-16T02:57:18Z |
| [#4660](https://github.com/OpenXiangShan/XiangShan/pull/4660) | fix(StoreQueue): fix timeout of vecExceptionFlag when redirect | weidingliu | closed | 1 | 2025-05-14T06:31:30Z |
| [#4659](https://github.com/OpenXiangShan/XiangShan/pull/4659) | fix(MMU): fix MMU hit logic for napot cases | good-circle | closed | 1 | 2025-05-08T06:11:17Z |
| [#4658](https://github.com/OpenXiangShan/XiangShan/pull/4658) | fix(TLB): fix a bug in handle_block where s2_ppn is generated | good-circle | closed | 1 | 2025-05-08T06:05:06Z |
| [#4653](https://github.com/OpenXiangShan/XiangShan/pull/4653) | refactor(MainPipe): refactor `s1_way_en` generate logic | cz4e | closed | 5 | - |
| [#4649](https://github.com/OpenXiangShan/XiangShan/pull/4649) | fix(Smstateen): fix access to sireg/vsireg | wissygh | closed | 2 | 2025-05-01T13:53:28Z |
| [#4648](https://github.com/OpenXiangShan/XiangShan/pull/4648) | fix(LCOFI): fix writable of LCOFI bit(13) of mvip/mvien/hvip/hvien | NewPaulWalker | closed | 2 | 2025-05-07T11:04:33Z |
| [#4647](https://github.com/OpenXiangShan/XiangShan/pull/4647) | fix(TLB): fix two issues in genVpn | good-circle | closed | 1 | 2025-04-30T05:59:56Z |
| [#4645](https://github.com/OpenXiangShan/XiangShan/pull/4645) | fix(wfi): add rnmi interrupt to wfievent | lewislzh | closed | 1 | 2025-04-29T02:22:27Z |
| [#4642](https://github.com/OpenXiangShan/XiangShan/pull/4642) | fix(rob): fix bug of robIdxNextLine may overflow | xiaofeibao-xjtu | closed | 1 | 2025-04-28T06:22:43Z |
| [#4641](https://github.com/OpenXiangShan/XiangShan/pull/4641) | fix(StoreQueue): strictly ensure deq moves in order | Maxpicca-Li | closed | 2 | 2025-04-29T06:43:00Z |
| [#4636](https://github.com/OpenXiangShan/XiangShan/pull/4636) | fix(LoadUnit): perfetch no longer generates nc access | Anzooooo | closed | 1 | 2025-04-29T08:47:01Z |
| [#4632](https://github.com/OpenXiangShan/XiangShan/pull/4632) | fix(Rob): calculate full PC for difftest by transType | Tang-Haojin | closed | 1 | 2025-04-27T02:09:49Z |
| [#4631](https://github.com/OpenXiangShan/XiangShan/pull/4631) | fix(StoreMisalignBuffer): fix misalign store enq but revoke logic | cz4e | closed | 3 | 2025-04-29T08:44:21Z |
| [#4629](https://github.com/OpenXiangShan/XiangShan/pull/4629) | fix(step): fix step for exception. | wissygh | closed | 1 | 2025-04-26T13:08:59Z |
| [#4628](https://github.com/OpenXiangShan/XiangShan/pull/4628) | fix: use a more sensible entry priority of uncacheBuffer | Maxpicca-Li | closed | 1 | 2025-04-29T08:40:05Z |
| [#4626](https://github.com/OpenXiangShan/XiangShan/pull/4626) | chore: add the version info to the simulation print output | NewPaulWalker | closed | 1 | 2025-04-29T07:21:04Z |
| [#4623](https://github.com/OpenXiangShan/XiangShan/pull/4623) | fix(criticalError): Stop counting `wfi_cycles` when disable `wfiResume` | wissygh | closed | 1 | 2025-04-25T02:24:46Z |
| [#4622](https://github.com/OpenXiangShan/XiangShan/pull/4622) | fix(DCache): fix DCache replacement when replace a `BtoT` ways | cz4e | closed | 16 | 2025-05-12T09:27:16Z |
| [#4619](https://github.com/OpenXiangShan/XiangShan/pull/4619) | fix(LoadUnit, LSQ): fix report exception type for hardware error | cz4e | closed | 5 | 2025-04-29T08:34:46Z |
| [#4611](https://github.com/OpenXiangShan/XiangShan/pull/4611) | fix(AXI4Memory): fix write request enqueue DRAMSim logic for AXI4Memory | cz4e | closed | 2 | 2025-04-22T11:02:04Z |
| [#4598](https://github.com/OpenXiangShan/XiangShan/pull/4598) | fix(DCache): fix DCache replacement when replace a  `BtoT` ways | cz4e | closed | 6 | - |
| [#4597](https://github.com/OpenXiangShan/XiangShan/pull/4597) | fix(LLPTW): dup entry should consider s2xlate in need_to_waiting_vec | good-circle | closed | 1 | 2025-04-20T16:47:31Z |
| [#4596](https://github.com/OpenXiangShan/XiangShan/pull/4596) | fix(LLPTW): dup_wait_resp should not send last_hptw_req when excp | good-circle | closed | 1 | 2025-04-20T16:47:49Z |
| [#4594](https://github.com/OpenXiangShan/XiangShan/pull/4594) | fix(xiselect): set the minimum range for xiselect | NewPaulWalker | closed | 1 | 2025-04-22T01:52:44Z |
| [#4593](https://github.com/OpenXiangShan/XiangShan/pull/4593) | fix(VLSU): modifying vector misalign elemidx generation | Anzooooo | closed | 1 | 2025-04-22T08:32:08Z |
| [#4592](https://github.com/OpenXiangShan/XiangShan/pull/4592) | fix(StoreUnit): cbo violation check should check cacheline | Anzooooo | closed | 1 | 2025-04-21T10:55:13Z |
| [#4588](https://github.com/OpenXiangShan/XiangShan/pull/4588) | fix(TLB): explicitly specify the signal width again when truncated | good-circle | closed | 1 | 2025-04-20T07:17:20Z |
| [#4587](https://github.com/OpenXiangShan/XiangShan/pull/4587) | fix(TLB): onlyStage1 req should use s1_paddr rather than s2_paddr | good-circle | closed | 1 | 2025-04-20T07:17:06Z |
| [#4586](https://github.com/OpenXiangShan/XiangShan/pull/4586) | fix(PTW): false positive accessFault should not use af_level when resp | good-circle | closed | 1 | 2025-04-20T07:16:53Z |
| [#4583](https://github.com/OpenXiangShan/XiangShan/pull/4583) | fix(top): enable cpuclock when debug halt req | wissygh | closed | 1 | 2025-04-21T16:36:21Z |
| [#4580](https://github.com/OpenXiangShan/XiangShan/pull/4580) | fix(LoadUnit): fix ldld && stld query revoke logic | jin120811 | closed | 1 | 2025-04-18T04:32:08Z |
| [#4573](https://github.com/OpenXiangShan/XiangShan/pull/4573) | fix(MainPipe): fix `s1_way_en` logic when pseudo tag error inject | cz4e | closed | 1 | 2025-04-21T10:33:20Z |
| [#4572](https://github.com/OpenXiangShan/XiangShan/pull/4572) | fix(MainPipe): fix error valid when Atomics and SBuffer request miss | cz4e | closed | 3 | 2025-04-21T10:35:02Z |
| [#4571](https://github.com/OpenXiangShan/XiangShan/pull/4571) | fix(xtopi): fix xtopi generation conditions | sinceforYy | closed | 3 | 2025-04-22T12:50:21Z |
| [#4570](https://github.com/OpenXiangShan/XiangShan/pull/4570) | fix(exceptionGen): clear isEnqExcp when older or curr wb exception coming | Ziyue-Zhang | closed | 1 | 2025-04-16T10:51:44Z |
| [#4561](https://github.com/OpenXiangShan/XiangShan/pull/4561) | fix(trace): fix parameters of trace | wissygh | closed | 1 | 2025-04-15T12:53:26Z |
| [#4546](https://github.com/OpenXiangShan/XiangShan/pull/4546) | submodule(chiselAIA): bump chiselAIA to fix `imsic.toCSR.illegal` | wissygh | closed | 1 | 2025-04-12T04:34:15Z |
| [#4541](https://github.com/OpenXiangShan/XiangShan/pull/4541) | fix(L2TlbPrefetch): fix flush condition of L2 TLB Prefetch | good-circle | closed | 1 | 2025-04-13T23:26:29Z |
| [#4540](https://github.com/OpenXiangShan/XiangShan/pull/4540) | fix(PTW): fix exception gen when both af and (pf \| gpf) occur | good-circle | closed | 1 | 2025-04-13T23:26:13Z |
| [#4539](https://github.com/OpenXiangShan/XiangShan/pull/4539) | fix(PTWCache): hfence_gvma should ignore g bit | good-circle | closed | 1 | 2025-04-13T23:25:59Z |
| [#4535](https://github.com/OpenXiangShan/XiangShan/pull/4535) | fix(decode): block the vector decode until vsetvl has committed | Ziyue-Zhang | closed | 1 | 2025-04-10T07:53:17Z |
| [#4534](https://github.com/OpenXiangShan/XiangShan/pull/4534) | fix(prefetch): fix control signals of l1 prefetchers | Maxpicca-Li | closed | 1 | 2025-04-13T23:20:59Z |
| [#4533](https://github.com/OpenXiangShan/XiangShan/pull/4533) | fix(vstopi): remove SEI from Candidate 4 | sinceforYy | closed | 2 | 2025-04-15T02:01:59Z |
| [#4531](https://github.com/OpenXiangShan/XiangShan/pull/4531) | fix(StoreQueue): keep readPtr until slave ack when outstanding | Maxpicca-Li | closed | 1 | 2025-04-10T12:59:32Z |
| [#4527](https://github.com/OpenXiangShan/XiangShan/pull/4527) | fix(MMU): fix gvpn generate when PTWCache Stage1Hit a napot entry | good-circle | closed | 1 | 2025-04-09T09:40:07Z |
| [#4526](https://github.com/OpenXiangShan/XiangShan/pull/4526) | fix(LSU): fix exception for misalign access to `nc` space | Anzooooo | closed | 1 | 2025-04-13T23:24:33Z |
| [#4525](https://github.com/OpenXiangShan/XiangShan/pull/4525) | fix(LLPTW): should not check g-stage pf when vs-stage pf occured | good-circle | closed | 1 | 2025-04-09T09:34:22Z |
| [#4524](https://github.com/OpenXiangShan/XiangShan/pull/4524) | fix(PTW): should not do pmp check before G-stage finish | good-circle | closed | 1 | 2025-04-09T09:33:31Z |
| [#4519](https://github.com/OpenXiangShan/XiangShan/pull/4519) | fix(Svinval): remove assert related to Svinval extension in ROB | NewPaulWalker | closed | 1 | 2025-04-09T06:16:05Z |
| [#4517](https://github.com/OpenXiangShan/XiangShan/pull/4517) | fix(difftest): fix sync aia event valid | sinceforYy | closed | 1 | 2025-04-09T06:14:52Z |
| [#4510](https://github.com/OpenXiangShan/XiangShan/pull/4510) | fix(LLPTW): each LLPTW entry should use its own s2xlate | good-circle | closed | 1 | 2025-04-13T23:22:41Z |
| [#4509](https://github.com/OpenXiangShan/XiangShan/pull/4509) | feat(AIA): integrate ChiselAIA again | Tang-Haojin | closed | 1 | 2025-04-07T12:40:20Z |
| [#4493](https://github.com/OpenXiangShan/XiangShan/pull/4493) | timing(StoreMisalignBuffer): fix misalign buffer enq timing | cz4e | closed | 4 | 2025-04-09T09:53:24Z |
| [#4491](https://github.com/OpenXiangShan/XiangShan/pull/4491) | feat(backend): make wfi timeout configurable | Tang-Haojin | closed | 3 | 2025-04-04T01:44:34Z |
| [#4485](https://github.com/OpenXiangShan/XiangShan/pull/4485) | fix(FTB, FTQ): dont use CPL2 SplittedSRAM | TheKiteRunner24 | closed | 1 | 2025-04-03T02:52:06Z |
| [#4480](https://github.com/OpenXiangShan/XiangShan/pull/4480) | fix(MainPipe): fix error valid generate logic | cz4e | closed | 1 | 2025-04-01T06:30:20Z |
| [#4479](https://github.com/OpenXiangShan/XiangShan/pull/4479) | fix(MainPipe): fix tag match logic when ecc inject occur | cz4e | closed | 1 | 2025-04-01T06:22:40Z |
| [#4473](https://github.com/OpenXiangShan/XiangShan/pull/4473) | fix(LLPTW): Should consider napot scenario when allStage | good-circle | closed | 1 | 2025-03-29T06:56:29Z |
| [#4472](https://github.com/OpenXiangShan/XiangShan/pull/4472) | fix(PTW): Should not do gvpn check when pageFault or ppn_af | good-circle | closed | 1 | 2025-03-29T06:55:06Z |
| [#4471](https://github.com/OpenXiangShan/XiangShan/pull/4471) | fix(TLB): explicitly specify the signal width when truncated | good-circle | closed | 1 | 2025-03-29T06:51:46Z |
| [#4468](https://github.com/OpenXiangShan/XiangShan/pull/4468) | area(ICache): split ICache meta SRAM | my-mayfly | closed | 1 | 2025-03-31T07:58:51Z |
| [#4456](https://github.com/OpenXiangShan/XiangShan/pull/4456) | fix(FusionDecoder): tie output to false when disabled | Tang-Haojin | closed | 1 | 2025-03-25T10:07:45Z |
| [#4455](https://github.com/OpenXiangShan/XiangShan/pull/4455) | fix(TLB): L1 TLB will not save the high bit of PPN | good-circle | closed | 1 | 2025-03-27T14:35:46Z |
| [#4454](https://github.com/OpenXiangShan/XiangShan/pull/4454) | fix(TLB): fix a typo about napot scenario | good-circle | closed | 1 | 2025-03-24T02:38:30Z |
| [#4453](https://github.com/OpenXiangShan/XiangShan/pull/4453) | fix(PTWCache): length of PPN should be gvpnLen when hypervisor | good-circle | closed | 1 | 2025-03-24T02:37:08Z |
| [#4449](https://github.com/OpenXiangShan/XiangShan/pull/4449) | fix(difftest, CSR): sync non-reg interrupt pending right after reset | Tang-Haojin | closed | 1 | 2025-03-22T05:17:31Z |
| [#4448](https://github.com/OpenXiangShan/XiangShan/pull/4448) | fix(MMU): Stage1Gpf should use hgatp instead of vsatp | good-circle | closed | 1 | 2025-03-24T02:36:13Z |
| [#4445](https://github.com/OpenXiangShan/XiangShan/pull/4445) | Remove frontend SRAM read-write conflict handling logic after it is moved into SRAMTemplate | castleberrysam | closed | 12 | 2025-04-10T02:44:14Z |
| [#4442](https://github.com/OpenXiangShan/XiangShan/pull/4442) | fix(LoadUnit): uncache should not be generated when page fault | Anzooooo | closed | 1 | 2025-03-20T11:39:14Z |
| [#4441](https://github.com/OpenXiangShan/XiangShan/pull/4441) | fix(StoreUnit): no uncache store misalign of mmio | Anzooooo | closed | 1 | 2025-03-20T11:39:34Z |
| [#4439](https://github.com/OpenXiangShan/XiangShan/pull/4439) | fix(fusion): block fusion when trigger fire and exception happen | wissygh | closed | 1 | 2025-03-20T03:26:09Z |
| [#4435](https://github.com/OpenXiangShan/XiangShan/pull/4435) | fix(amocas): fix amocas.q to avoid stalls | NewPaulWalker | closed | 1 | 2025-03-19T09:26:42Z |
| [#4426](https://github.com/OpenXiangShan/XiangShan/pull/4426) | fix(LoadUnit): fix misalign exception and clearer uncache semantics | Anzooooo | closed | 1 | 2025-03-17T06:00:10Z |
| [#4423](https://github.com/OpenXiangShan/XiangShan/pull/4423) | fix(IPrefetchPipe): consider backend exception as part of itlb exception | ngc7331 | closed | 1 | 2025-03-28T10:35:56Z |
| [#4422](https://github.com/OpenXiangShan/XiangShan/pull/4422) | fix(PTW): Fix exception handle logic when both pf and af occur | good-circle | closed | 1 | 2025-03-23T11:23:19Z |
| [#4419](https://github.com/OpenXiangShan/XiangShan/pull/4419) | fix(csr, difftest): do not update difftest framework on reset | sinceforYy | closed | 1 | 2025-03-14T08:28:14Z |
| [#4414](https://github.com/OpenXiangShan/XiangShan/pull/4414) | fix(DM): synchronize the `jtag_reset` in standaloneDM | wissygh | closed | 1 | 2025-03-14T08:45:22Z |
| [#4412](https://github.com/OpenXiangShan/XiangShan/pull/4412) | fix(csr): filter out Read-Only CSR in regOut | sinceforYy | closed | 1 | 2025-04-25T02:51:19Z |
| [#4407](https://github.com/OpenXiangShan/XiangShan/pull/4407) | fix(PTWCache): Should refill full GVPN to Page Cache | good-circle | closed | 1 | 2025-03-13T09:04:01Z |
| [#4406](https://github.com/OpenXiangShan/XiangShan/pull/4406) | fix(PTW): High bits of GVPN should not be truncated | good-circle | closed | 1 | 2025-03-13T09:03:47Z |
| [#4404](https://github.com/OpenXiangShan/XiangShan/pull/4404) | fix(LLPTW): Fix exception judgement for different virtualisation stages | good-circle | closed | 1 | 2025-03-13T09:03:35Z |
| [#4396](https://github.com/OpenXiangShan/XiangShan/pull/4396) | fix(L2TLB): Napot entries in LLPTW should not be compressed | good-circle | closed | 1 | 2025-03-13T13:37:31Z |
| [#4394](https://github.com/OpenXiangShan/XiangShan/pull/4394) | fix(MainPipe):  `error` and `writeback` addr generate logic | cz4e | closed | 1 | 2025-03-17T03:12:17Z |
| [#4393](https://github.com/OpenXiangShan/XiangShan/pull/4393) | fix(csr): CSRR instruction read xireg inOrder | sinceforYy | closed | 1 | 2025-03-12T01:52:31Z |
| [#4392](https://github.com/OpenXiangShan/XiangShan/pull/4392) | fix(reidrectGen): fix redirectGen valid signal | sinceforYy | closed | 1 | 2025-03-12T00:54:51Z |
| [#4383](https://github.com/OpenXiangShan/XiangShan/pull/4383) | fix(AXI4Memory): remove `AWLEN == 0` Check | cz4e | closed | 1 | 2025-04-01T06:57:10Z |
| [#4382](https://github.com/OpenXiangShan/XiangShan/pull/4382) | fix(amocas): re-split uops for amocas to avoid stalls | NewPaulWalker | closed | 1 | 2025-03-17T08:46:03Z |
| [#4369](https://github.com/OpenXiangShan/XiangShan/pull/4369) | fix(LSU): misaligned violation detection stuck | Anzooooo | closed | 1 | 2025-03-07T03:50:50Z |
| [#4367](https://github.com/OpenXiangShan/XiangShan/pull/4367) | fix(LoadUnit): exclude prefetch requests | cz4e | closed | 3 | 2025-03-06T11:02:30Z |
| [#4363](https://github.com/OpenXiangShan/XiangShan/pull/4363) | fix(LoadUnit): enable EnableAccurateLoadError | cz4e | closed | 1 | 2025-03-06T11:03:56Z |
| [#4361](https://github.com/OpenXiangShan/XiangShan/pull/4361) | feat(Difftest): add multi-core vector load check | Anzooooo | closed | 1 | 2025-03-07T08:29:48Z |
| [#4360](https://github.com/OpenXiangShan/XiangShan/pull/4360) | feat(FTB, FTQ): split FTB meta SRAM and FTQ meta SRAM | TheKiteRunner24 | closed | 2 | 2025-03-17T06:39:26Z |
| [#4359](https://github.com/OpenXiangShan/XiangShan/pull/4359) | fix(LoadUnit): misalign wakeup should not set s0 valid | Anzooooo | closed | 1 | 2025-03-05T06:40:26Z |
| [#4354](https://github.com/OpenXiangShan/XiangShan/pull/4354) | fix(CSR): add VTYPE to in-order read CSRs | Squareless-XD | closed | 1 | 2025-03-07T06:15:46Z |
| [#4349](https://github.com/OpenXiangShan/XiangShan/pull/4349) | fix(MMU): incorrect generation of Exception vaddr | cebarobot | closed | 1 | 2025-03-04T08:23:19Z |
| [#4346](https://github.com/OpenXiangShan/XiangShan/pull/4346) | fix(Trigger): fix comparison between consecutive pc and tdada2 | wissygh | closed | 1 | 2025-03-07T08:26:37Z |
| [#4345](https://github.com/OpenXiangShan/XiangShan/pull/4345) | fix(MainPipe): fix `s3_l2_error` and `s3_error` enable signal | cz4e | closed | 1 | 2025-03-06T09:52:22Z |
| [#4340](https://github.com/OpenXiangShan/XiangShan/pull/4340) | fix(DCache): use `ParallelMux` instead of `Mux1H` | cz4e | closed | 1 | 2025-03-03T08:26:44Z |
| [#4339](https://github.com/OpenXiangShan/XiangShan/pull/4339) | fix(DCache): use `ParallelMux` instead of `Mux1H` | cz4e | closed | 2 | - |
| [#4337](https://github.com/OpenXiangShan/XiangShan/pull/4337) | fix(DCache): fix wrong condition for blocking lr | bosscharlie | closed | 1 | 2025-03-03T07:24:14Z |
| [#4335](https://github.com/OpenXiangShan/XiangShan/pull/4335) | feat(BEU): beu will trigger `NMI_31` non-maskable interrupt | cz4e | closed | 1 | 2025-03-03T08:03:03Z |
| [#4333](https://github.com/OpenXiangShan/XiangShan/pull/4333) | fix(LoadUnit): misalign load wakeup not enter loadunit | Anzooooo | closed | 1 | 2025-03-03T07:22:24Z |
| [#4324](https://github.com/OpenXiangShan/XiangShan/pull/4324) | fix(L2top): Shouldn't subtract dm from mmio_port when SeperateDMBus disable | wissygh | closed | 1 | 2025-02-27T08:38:04Z |
| [#4321](https://github.com/OpenXiangShan/XiangShan/pull/4321) | fix(PFEvent): use `CSRModule` for distribute_csr in PFEvent | wissygh | closed | 1 | 2025-02-28T16:36:54Z |
| [#4317](https://github.com/OpenXiangShan/XiangShan/pull/4317) | feat(RAS): change the stall mechanism upon return stack overflow to dynamically disable the return stack. | my-mayfly | closed | 1 | 2025-03-02T17:06:42Z |
| [#4307](https://github.com/OpenXiangShan/XiangShan/pull/4307) | fix(xtval): fix xtval when raise intr | sinceforYy | closed | 1 | 2025-02-21T16:14:44Z |
| [#4304](https://github.com/OpenXiangShan/XiangShan/pull/4304) | fix(uncache): correct the indexes | Maxpicca-Li | closed | 2 | 2025-02-24T05:38:33Z |
| [#4301](https://github.com/OpenXiangShan/XiangShan/pull/4301) | fix(IFU): handle uncache corrupt | ngc7331 | closed | 4 | 2025-03-10T06:10:22Z |
| [#4300](https://github.com/OpenXiangShan/XiangShan/pull/4300) | fix(LoadQueueUncache): exhaust the various cases of flush | Maxpicca-Li | closed | 1 | 2025-02-24T03:45:05Z |
| [#4298](https://github.com/OpenXiangShan/XiangShan/pull/4298) | submodule(CoupledL2): bump CoupledL2 | Ma-YX | closed | 1 | 2025-02-20T11:08:34Z |
| [#4292](https://github.com/OpenXiangShan/XiangShan/pull/4292) | fix(LoadUnit): corrupt should be triggered on valid mshr | Anzooooo | closed | 1 | 2025-02-20T02:35:12Z |
| [#4288](https://github.com/OpenXiangShan/XiangShan/pull/4288) | chore(dispatch): remove useless code and files | xiaofeibao-xjtu | closed | 1 | 2025-02-20T07:02:00Z |
| [#4285](https://github.com/OpenXiangShan/XiangShan/pull/4285) | fix(MainPipe): fix `s1_way_en` generate logic when ecc inject occur | cz4e | closed | 2 | 2025-02-20T02:38:40Z |
| [#4275](https://github.com/OpenXiangShan/XiangShan/pull/4275) | fix(uncache): uncache load fails to replay | Maxpicca-Li | closed | 1 | 2025-02-17T03:31:36Z |
| [#4272](https://github.com/OpenXiangShan/XiangShan/pull/4272) | fix(DCache): pass `amo_cmp` to MSHR when cas req miss | bosscharlie | closed | 1 | 2025-02-16T10:00:30Z |
| [#4269](https://github.com/OpenXiangShan/XiangShan/pull/4269) | fix(PreDecode): fix fixedTaken for jalr | TheKiteRunner24 | closed | 1 | 2025-02-14T02:32:30Z |
| [#4268](https://github.com/OpenXiangShan/XiangShan/pull/4268) | fix(uncache): avoid merging the corner cases | Maxpicca-Li | closed | 1 | 2025-02-17T05:28:29Z |
| [#4267](https://github.com/OpenXiangShan/XiangShan/pull/4267) | submodule(ready-to-run): Bump ready-to-run | wissygh | closed | 1 | 2025-02-13T07:17:25Z |
| [#4266](https://github.com/OpenXiangShan/XiangShan/pull/4266) | fix(TLB): onlyS1 scene should not consider G-stage access fault | good-circle | closed | 1 | 2025-02-16T09:40:17Z |
| [#4263](https://github.com/OpenXiangShan/XiangShan/pull/4263) | fix(LoadUnit): fix  misalign load wrong wakeup | cz4e | closed | 1 | 2025-02-16T09:38:10Z |
| [#4262](https://github.com/OpenXiangShan/XiangShan/pull/4262) | fix(LSU): fix cbo instr exceptions and implementation | Anzooooo | closed | 7 | 2025-02-17T15:08:52Z |
| [#4257](https://github.com/OpenXiangShan/XiangShan/pull/4257) | fix(perfcct): fix the bug of some instructions being lost. | sinceforYy | closed | 2 | 2025-02-11T01:33:34Z |
| [#4256](https://github.com/OpenXiangShan/XiangShan/pull/4256) | fix(Mcontrol6): fix writing mcontrol6.dmode for trigger chain | wissygh | closed | 1 | 2025-02-13T03:31:29Z |
| [#4253](https://github.com/OpenXiangShan/XiangShan/pull/4253) | fix(MMU): Should consider s2xlate when calculate page level | good-circle | closed | 1 | 2025-02-10T03:08:16Z |
| [#4252](https://github.com/OpenXiangShan/XiangShan/pull/4252) | fix(L1TLB): Should consider s2xlate when refill Svnapot | good-circle | closed | 1 | 2025-02-10T03:08:00Z |
| [#4244](https://github.com/OpenXiangShan/XiangShan/pull/4244) | fix(vfalu): fix bug of allFFlagsEn when lastUop is reduction unorder sum | xiaofeibao-xjtu | closed | 1 | 2025-02-06T04:25:44Z |
| [#4239](https://github.com/OpenXiangShan/XiangShan/pull/4239) | fix(LSU): fix misalign store exception logic | Anzooooo | closed | 1 | 2025-01-26T10:02:52Z |
| [#4235](https://github.com/OpenXiangShan/XiangShan/pull/4235) | fix(Config): add the 'L3CacheCtrl' address space permission back | Anzooooo | closed | 1 | 2025-01-25T03:33:38Z |
| [#4234](https://github.com/OpenXiangShan/XiangShan/pull/4234) | fix(IFU): add range checking for instruction blocks containing jalr | TheKiteRunner24 | closed | 1 | 2025-01-26T08:25:07Z |
| [#4232](https://github.com/OpenXiangShan/XiangShan/pull/4232) | fix(RAS): adjust the signal judgment of isCall and isRet during redirection | my-mayfly | closed | 2 | 2025-01-27T13:36:13Z |
| [#4228](https://github.com/OpenXiangShan/XiangShan/pull/4228) | fix(StoreUnit): misaligned store need check `RAW` | Anzooooo | closed | 1 | 2025-01-27T13:51:17Z |
| [#4227](https://github.com/OpenXiangShan/XiangShan/pull/4227) | fix(StoreMisalignBuffer): fix state transition when writeback | Anzooooo | closed | 1 | 2025-01-27T13:50:58Z |
| [#4226](https://github.com/OpenXiangShan/XiangShan/pull/4226) | fix(LoadUnit): `dcache_kill` if `prf_wr` has no permissions | Anzooooo | closed | 1 | 2025-01-27T13:50:30Z |
| [#4223](https://github.com/OpenXiangShan/XiangShan/pull/4223) | timing(frontend): remove bad timing clock gating | eastonman | closed | 1 | 2025-01-24T07:56:17Z |
| [#4216](https://github.com/OpenXiangShan/XiangShan/pull/4216) | timing(ittage): optimize the timing of the ittage path for reading the jump address | my-mayfly | closed | 1 | 2025-01-24T07:55:26Z |
| [#4211](https://github.com/OpenXiangShan/XiangShan/pull/4211) | feat(Zawrs): support Zawrs extension | Tang-Haojin | closed | 2 | 2025-01-22T03:35:09Z |
| [#4208](https://github.com/OpenXiangShan/XiangShan/pull/4208) | fix(MainPipe): use s3_tag_error to generate error report signal | cz4e | closed | 1 | 2025-01-21T02:31:31Z |
| [#4202](https://github.com/OpenXiangShan/XiangShan/pull/4202) | fix(L2TLB): reset tlbCounter when flush | good-circle | closed | 1 | 2025-01-20T14:07:08Z |
| [#4198](https://github.com/OpenXiangShan/XiangShan/pull/4198) | feat(busytable): support eliminate old vd in new dispatch | Ziyue-Zhang | closed | 1 | 2025-02-20T02:35:50Z |
| [#4197](https://github.com/OpenXiangShan/XiangShan/pull/4197) | fix bug of snptSelect and bump yunsuan | xiaofeibao-xjtu | closed | 2 | 2025-01-17T09:13:19Z |
| [#4195](https://github.com/OpenXiangShan/XiangShan/pull/4195) | fix(PTWCache): avoid X-prop of spRefill | good-circle | closed | 1 | 2025-01-17T08:51:34Z |
| [#4194](https://github.com/OpenXiangShan/XiangShan/pull/4194) | fix(mnret): add the missing mnret output connection | lewislzh | closed | 1 | 2025-01-17T08:22:21Z |
| [#4191](https://github.com/OpenXiangShan/XiangShan/pull/4191) | fix(L2TLB): Fix stuck caused by MissQueue full | good-circle | closed | 1 | 2025-01-17T08:50:13Z |
| [#4181](https://github.com/OpenXiangShan/XiangShan/pull/4181) | fix(VFALU): fix bug of f16FirstFoldMaskUnorder when fold to 1/2 | xiaofeibao-xjtu | closed | 1 | 2025-01-16T11:01:49Z |
| [#4174](https://github.com/OpenXiangShan/XiangShan/pull/4174) | fix(PTWRepeater): use PriorityMux for not one-hot vector | good-circle | closed | 1 | 2025-01-15T11:13:41Z |
| [#4173](https://github.com/OpenXiangShan/XiangShan/pull/4173) | timing(ICache): move mshr_resp selector 1 cycle ahead | ngc7331 | closed | 1 | 2025-01-22T09:32:17Z |
| [#4166](https://github.com/OpenXiangShan/XiangShan/pull/4166) | fix(aia): add the missing AIA-related permission checks | NewPaulWalker | closed | 4 | 2025-01-16T14:16:22Z |
| [#4164](https://github.com/OpenXiangShan/XiangShan/pull/4164) | feat(custom, csr): add two custom CSRs mcorepwr and mflushpwr to control power | NewPaulWalker | closed | 4 | 2025-01-16T05:05:57Z |
| [#4157](https://github.com/OpenXiangShan/XiangShan/pull/4157) | fix(CSR): fix xTIP update in sstcIRGen | sinceforYy | closed | 1 | 2025-01-10T10:01:05Z |
| [#4153](https://github.com/OpenXiangShan/XiangShan/pull/4153) | fix(rob): fix needflush when rob has redirect | sinceforYy | closed | 1 | 2025-01-09T14:54:39Z |
| [#4146](https://github.com/OpenXiangShan/XiangShan/pull/4146) | feat(exception): divide the exceptions raised from CSR access into different sources. | NewPaulWalker | closed | 13 | 2025-01-13T04:57:16Z |
| [#4145](https://github.com/OpenXiangShan/XiangShan/pull/4145) | feat(CSR): set init 0 for htimedelta csr | sinceforYy | closed | 1 | 2025-01-08T01:50:36Z |
| [#4139](https://github.com/OpenXiangShan/XiangShan/pull/4139) | fix(StoreQueue): remove the incorrect redirect logic | Anzooooo | closed | 1 | 2025-01-07T03:31:41Z |
| [#4134](https://github.com/OpenXiangShan/XiangShan/pull/4134) | feat(DM, hartReset): support `hartReset` which could reset selected harts | wissygh | closed | 2 | 2025-01-09T02:33:16Z |
| [#4132](https://github.com/OpenXiangShan/XiangShan/pull/4132) | fix(Unprivileged): wait a cycle to update `time` when `nextV =/= v` | Tang-Haojin | closed | 1 | 2025-01-05T18:32:04Z |
| [#4131](https://github.com/OpenXiangShan/XiangShan/pull/4131) | fix(Rename): fuse lui-load only if `rfWen` of lui is true | Tang-Haojin | closed | 1 | 2025-01-06T03:17:56Z |
| [#4128](https://github.com/OpenXiangShan/XiangShan/pull/4128) | feat(Backend): Accelerate CSRR instructions by performing out-of-order execution on most CSRs | Squareless-XD | closed | 1 | 2025-02-19T08:37:21Z |
| [#4122](https://github.com/OpenXiangShan/XiangShan/pull/4122) | feat(TopDown): add TopDown PMU Events | sinceforYy | closed | 5 | 2025-01-16T11:00:43Z |
| [#4119](https://github.com/OpenXiangShan/XiangShan/pull/4119) | timing(CSR): using addr/wdata after 1 cycle for writing frontend and memory | sinceforYy | closed | 1 | 2025-01-06T06:27:15Z |
| [#4118](https://github.com/OpenXiangShan/XiangShan/pull/4118) | fix(redirectGen): fix bug of csr's cfiUpdate | xiaofeibao-xjtu | closed | 1 | 2025-01-02T12:52:41Z |
| [#4114](https://github.com/OpenXiangShan/XiangShan/pull/4114) | feat(commit): complete rewrite of commit mechanism | Yan-Muzi | closed | 17 | 2025-03-31T07:57:33Z |
| [#4112](https://github.com/OpenXiangShan/XiangShan/pull/4112) | fix(ICacheMissUnit): clear corrupt_r when response is sent to MainPipe | ngc7331 | closed | 1 | 2025-01-03T03:57:51Z |
| [#4108](https://github.com/OpenXiangShan/XiangShan/pull/4108) | fix(FusionDecoder): instructions may be HINT cannot be fused | Tang-Haojin | closed | 1 | 2024-12-30T09:23:12Z |
| [#4103](https://github.com/OpenXiangShan/XiangShan/pull/4103) | fix(VLSU): `mergebuffer` threshold was added | Anzooooo | closed | 1 | 2024-12-30T13:38:30Z |
| [#4102](https://github.com/OpenXiangShan/XiangShan/pull/4102) | fix(LoadUnit): `fastReplay` can only happen once | Anzooooo | closed | 1 | 2024-12-30T06:12:18Z |
| [#4101](https://github.com/OpenXiangShan/XiangShan/pull/4101) | fix(LoadUnit): fix Vector priority related issues | Anzooooo | closed | 1 | 2024-12-30T09:28:55Z |
| [#4096](https://github.com/OpenXiangShan/XiangShan/pull/4096) | fix(LQUncache): fix a potential deadblock when enqueue | Maxpicca-Li | closed | 1 | 2025-01-02T03:30:58Z |
| [#4090](https://github.com/OpenXiangShan/XiangShan/pull/4090) | fix(PTW): incorrect GPF due to timing mismatch | cebarobot | closed | 1 | 2024-12-26T03:31:46Z |
| [#4088](https://github.com/OpenXiangShan/XiangShan/pull/4088) | ppa(backend) | xiaofeibao-xjtu | closed | 2 | 2024-12-26T04:48:06Z |
| [#4086](https://github.com/OpenXiangShan/XiangShan/pull/4086) | fix(LoadQueueRAR): aligning the size of `RARSize` to `VLQSize` | Anzooooo | closed | 1 | 2024-12-25T02:14:50Z |
| [#4085](https://github.com/OpenXiangShan/XiangShan/pull/4085) | fix(LoadUnit): fix Load misalign related bugs | Anzooooo | closed | 2 | 2024-12-27T02:37:09Z |
| [#4084](https://github.com/OpenXiangShan/XiangShan/pull/4084) | fix(MemBlock): fix overflow during lsqptr calculation | Anzooooo | closed | 1 | 2024-12-25T02:15:47Z |
| [#4079](https://github.com/OpenXiangShan/XiangShan/pull/4079) | fix(CSR): flush CSR when inst redirect | sinceforYy | closed | 1 | 2024-12-26T01:51:33Z |
| [#4077](https://github.com/OpenXiangShan/XiangShan/pull/4077) | fix(StoreMisalignBuffer): crosspage can only be replaced when `s_idle` | Anzooooo | closed | 1 | 2024-12-23T09:37:56Z |
| [#4075](https://github.com/OpenXiangShan/XiangShan/pull/4075) | timing(backend): rob and vecExcpMod | xiaofeibao-xjtu | closed | 3 | 2024-12-25T09:43:01Z |
| [#4072](https://github.com/OpenXiangShan/XiangShan/pull/4072) | fix(FTQ): start of the first instruction in an entry | Yan-Muzi | closed | 1 | 2024-12-26T02:04:16Z |
| [#4070](https://github.com/OpenXiangShan/XiangShan/pull/4070) | fix(hideleg): fix the read value of the LCOFI bit of hideleg. | NewPaulWalker | closed | 2 | 2025-01-06T06:47:21Z |
| [#4069](https://github.com/OpenXiangShan/XiangShan/pull/4069) | submodule(yunsuan): bump yunsuan to fix VFMA/FMA area | lewislzh | closed | 1 | 2024-12-23T07:57:49Z |
| [#4067](https://github.com/OpenXiangShan/XiangShan/pull/4067) | timing(Rob): modify selection from robentries to robDeqGroup | wissygh | closed | 1 | 2024-12-20T03:48:06Z |
| [#4066](https://github.com/OpenXiangShan/XiangShan/pull/4066) | timing(Vector,Decode): judge isComplex by inst encoding directly  | xiaofeibao-xjtu | closed | 2 | 2024-12-20T02:09:55Z |
| [#4064](https://github.com/OpenXiangShan/XiangShan/pull/4064) | fix(NewCSR): fix the error of trap entry PC in vs mode interrupts | lewislzh | closed | 1 | 2024-12-20T03:48:20Z |
| [#4063](https://github.com/OpenXiangShan/XiangShan/pull/4063) | area(EXU): add parameter `needCopySrc` in FuConfig | wissygh | closed | 1 | 2024-12-20T09:02:48Z |
| [#4057](https://github.com/OpenXiangShan/XiangShan/pull/4057) | fix(LoadUnit): fix trigger exception when writeback and wakeup logic | Anzooooo | closed | 1 | 2024-12-18T03:43:04Z |
| [#4054](https://github.com/OpenXiangShan/XiangShan/pull/4054) | fix(dbltrp): fix sdt write and sdt/sie interaction logic | lewislzh | closed | 2 | 2024-12-16T16:21:16Z |
| [#4053](https://github.com/OpenXiangShan/XiangShan/pull/4053) | fix(MemBlock): fix misaligned exception and remove redundant reg from `SQ` | Anzooooo | closed | 2 | 2024-12-16T16:15:56Z |
| [#4049](https://github.com/OpenXiangShan/XiangShan/pull/4049) | ppa(backend) | xiaofeibao-xjtu | closed | 32 | 2024-12-19T10:03:03Z |
| [#4048](https://github.com/OpenXiangShan/XiangShan/pull/4048) | fix(RAS): bos pointer needs to be updated when the instruction is committed | my-mayfly | closed | 2 | 2024-12-18T04:25:54Z |
| [#4044](https://github.com/OpenXiangShan/XiangShan/pull/4044) | feat(ICache): ECC error injection | ngc7331 | closed | 13 | 2024-12-30T05:16:55Z |
| [#4033](https://github.com/OpenXiangShan/XiangShan/pull/4033) | area(IssueQueue): encode exuOH as UInt to reduce storage | sinsanction | closed | 2 | 2024-12-16T06:20:13Z |
| [#4028](https://github.com/OpenXiangShan/XiangShan/pull/4028) | fix(vset): simplify vl compute in vsetrvfwvf module | Ziyue-Zhang | closed | 1 | 2024-12-12T06:23:48Z |
| [#4025](https://github.com/OpenXiangShan/XiangShan/pull/4025) | timing(decode): dequeue uops by indexing in order in DecodeUnitComp | wissygh | closed | 1 | 2024-12-13T04:46:01Z |
| [#4024](https://github.com/OpenXiangShan/XiangShan/pull/4024) | fix(uopsplit): set vector instructions never use simple split type | Ziyue-Zhang | closed | 1 | 2024-12-12T06:23:06Z |
| [#4018](https://github.com/OpenXiangShan/XiangShan/pull/4018) | fix(BankedDataArray): fix readline error_delayed selection | cz4e | closed | 2 | 2024-12-12T03:33:00Z |
| [#4002](https://github.com/OpenXiangShan/XiangShan/pull/4002) | fix(tage): avoid read/write to the same address in the tage bt table. | sleep-zzz | closed | 1 | 2024-12-10T01:51:29Z |
| [#4001](https://github.com/OpenXiangShan/XiangShan/pull/4001) | Why does this cause strange performance fluctuations :question: :facepunch: :zzz: | Anzooooo | closed | 1 | - |
| [#3996](https://github.com/OpenXiangShan/XiangShan/pull/3996) | fix(ICache,ITLB): also flush itlb pipe when prefetchPipe s1_flush | ngc7331 | closed | 1 | 2024-12-09T03:11:12Z |
| [#3991](https://github.com/OpenXiangShan/XiangShan/pull/3991) | fix(interrupt): `Vset` should not respond to interrupts | Anzooooo | closed | 1 | 2024-12-06T16:40:06Z |
| [#3990](https://github.com/OpenXiangShan/XiangShan/pull/3990) | fix(vecExcpInfo): do not set `vecExcpInfo` if exception is an interrupt | Tang-Haojin | closed | 1 | 2024-12-06T04:22:24Z |
| [#3989](https://github.com/OpenXiangShan/XiangShan/pull/3989) | fix(csr, imsic): sync CSR access imsic | sinceforYy | closed | 1 | 2024-12-09T01:54:44Z |
| [#3986](https://github.com/OpenXiangShan/XiangShan/pull/3986) | fix(Parameters): add missing `ISAExtensions` | Tang-Haojin | closed | 2 | 2024-12-05T15:52:07Z |
| [#3985](https://github.com/OpenXiangShan/XiangShan/pull/3985) | fix(TLB): avoid refill when one cycle before need_gpa | good-circle | closed | 1 | 2024-12-06T02:11:12Z |
| [#3981](https://github.com/OpenXiangShan/XiangShan/pull/3981) | submodule(ready-to-run): spike rebase upstream master | lewislzh | closed | 1 | 2024-12-06T05:03:28Z |
| [#3978](https://github.com/OpenXiangShan/XiangShan/pull/3978) | fix(Smstateen): fix access check when Smstateen extension enable. | NewPaulWalker | closed | 2 | 2024-12-09T10:38:49Z |
| [#3966](https://github.com/OpenXiangShan/XiangShan/pull/3966) | fix(CSR): fix shadow writing for custom PMA CSRs not in `csrRwMap` | huxuan0307 | closed | 1 | 2024-12-01T08:04:23Z |
| [#3965](https://github.com/OpenXiangShan/XiangShan/pull/3965) | fix(vector): do not set vs.dirty for some type of vecInsts | Tang-Haojin | closed | 1 | 2024-12-01T08:02:57Z |
| [#3964](https://github.com/OpenXiangShan/XiangShan/pull/3964) | fix(TLB): avoid freeze when GPF occurs | cebarobot | closed | 1 | 2024-12-02T03:50:11Z |
| [#3963](https://github.com/OpenXiangShan/XiangShan/pull/3963) | fix(IFU): mark mmio mismatch exception only on the second line | ngc7331 | closed | 1 | 2024-12-02T09:09:18Z |
| [#3961](https://github.com/OpenXiangShan/XiangShan/pull/3961) | area(decode): move vecExceptionGen to complex docoder | Ziyue-Zhang | closed | 1 | 2024-12-04T07:29:46Z |
| [#3958](https://github.com/OpenXiangShan/XiangShan/pull/3958) | feat(Backend, MemBlock): add support for Zacas extension | linjuanZ | closed | 7 | 2024-12-10T00:49:06Z |
| [#3955](https://github.com/OpenXiangShan/XiangShan/pull/3955) | fix(dbltrp): fix sdt/dte interaction logic  | lewislzh | closed | 2 | 2024-11-28T09:13:16Z |
| [#3953](https://github.com/OpenXiangShan/XiangShan/pull/3953) | feat(isa): add isa-base and isa-extensions to param and dts | Tang-Haojin | closed | 1 | 2024-11-28T15:18:01Z |
| [#3950](https://github.com/OpenXiangShan/XiangShan/pull/3950) | Frontend: modify the code related to configuration parameters | my-mayfly | closed | 1 | 2024-12-16T03:09:34Z |
| [#3948](https://github.com/OpenXiangShan/XiangShan/pull/3948) | fix(decode): not eliminate old vd when vstart is not zero | Ziyue-Zhang | closed | 1 | 2024-11-28T03:05:46Z |
| [#3946](https://github.com/OpenXiangShan/XiangShan/pull/3946) | timing(csr): add 1 cycle to csr read/write and select highest interrupt priority | sinceforYy | closed | 2 | 2024-11-29T10:07:27Z |
| [#3944](https://github.com/OpenXiangShan/XiangShan/pull/3944) | feat(IFU,Svpbmt): allow speculative fetch in pbmt.NC (idempotent) spaces | ngc7331 | closed | 2 | 2024-12-16T02:47:04Z |
| [#3939](https://github.com/OpenXiangShan/XiangShan/pull/3939) | Bump yunsuan | HeiHuDie | closed | 1 | 2024-11-29T07:48:24Z |
| [#3935](https://github.com/OpenXiangShan/XiangShan/pull/3935) | area(ittage): Split the Target into Region and Offset, with Region stored in registers and Offset still using SRAM | sleep-zzz | closed | 3 | 2024-12-05T09:32:42Z |
| [#3932](https://github.com/OpenXiangShan/XiangShan/pull/3932) | Bump yunsuan | HeiHuDie | closed | 1 | 2024-11-26T02:46:34Z |
| [#3918](https://github.com/OpenXiangShan/XiangShan/pull/3918) | submodule(ready-to-run): bump ready-to-run | NewPaulWalker | closed | 1 | 2024-11-25T03:14:10Z |
| [#3909](https://github.com/OpenXiangShan/XiangShan/pull/3909) | fix(vlbusytable): fix int vl writeback wrong use vf vl writeback | Ziyue-Zhang | closed | 1 | 2024-11-22T02:29:48Z |
| [#3899](https://github.com/OpenXiangShan/XiangShan/pull/3899) | feat(ICache): re-fetch data from L2 if ECC error is detected | ngc7331 | closed | 1 | 2024-11-25T02:34:32Z |
| [#3898](https://github.com/OpenXiangShan/XiangShan/pull/3898) | fix(dret): fix update of privstate in dretevent | wissygh | closed | 1 | 2024-11-20T10:49:31Z |
| [#3894](https://github.com/OpenXiangShan/XiangShan/pull/3894) | fix(vnclip): use uimm instead of imm for vnclip_wi instructions | Ziyue-Zhang | closed | 1 | 2024-11-21T02:52:50Z |
| [#3889](https://github.com/OpenXiangShan/XiangShan/pull/3889) | feat(frontend): add ClockGate at frontend SRAMTemplate | Lawrence-ID | closed | 2 | 2024-11-19T07:41:50Z |
| [#3886](https://github.com/OpenXiangShan/XiangShan/pull/3886) | fix(RVCDecoder): add check for zcb reserved space | TheKiteRunner24 | closed | 1 | 2024-11-21T05:45:46Z |
| [#3885](https://github.com/OpenXiangShan/XiangShan/pull/3885) | fix(critical-error): critical-error pass early then trap | lewislzh | closed | 1 | 2024-11-19T05:13:26Z |
| [#3884](https://github.com/OpenXiangShan/XiangShan/pull/3884) | fix(LoadQueueReplay): fix enq mask generate when redirect | cz4e | closed | 1 | 2024-11-18T10:59:55Z |
| [#3875](https://github.com/OpenXiangShan/XiangShan/pull/3875) | fix(xtval): fix selection of tval for trap | wissygh | closed | 2 | 2024-11-18T01:47:32Z |
| [#3873](https://github.com/OpenXiangShan/XiangShan/pull/3873) | fix(IFU): check consistency of mmio states | ngc7331 | closed | 1 | 2024-11-15T07:33:12Z |
| [#3871](https://github.com/OpenXiangShan/XiangShan/pull/3871) | fix(TLB): incorrect tval2 info when IGPF occurs | cebarobot | closed | 1 | 2024-11-14T07:02:29Z |
| [#3870](https://github.com/OpenXiangShan/XiangShan/pull/3870) | submodule(ready-to-run): bump spike and nemu; spike support dbltrp | lewislzh | closed | 1 | 2024-11-14T02:26:42Z |
| [#3859](https://github.com/OpenXiangShan/XiangShan/pull/3859) | fix(CSR,RVC): c.fp instrs should be illegal when fs is off | ngc7331 | closed | 2 | 2024-11-14T08:48:49Z |
| [#3850](https://github.com/OpenXiangShan/XiangShan/pull/3850) | fix(BPU): fix potential bug on s2_fire_dup | Jerry-Tianchen | closed | 2 | 2024-11-15T07:18:45Z |
| [#3848](https://github.com/OpenXiangShan/XiangShan/pull/3848) | submodule(difftest): expand trapcode to 64bit to fix XStrap | lewislzh | closed | 1 | 2024-11-08T07:54:48Z |
| [#3847](https://github.com/OpenXiangShan/XiangShan/pull/3847) | feat(vl busytable): support eliminate old vd when read vl's state | Ziyue-Zhang | closed | 1 | 2024-11-11T08:30:06Z |
| [#3845](https://github.com/OpenXiangShan/XiangShan/pull/3845) | fix(aes): fix exception check for aes64ks1i. | NewPaulWalker | closed | 1 | 2024-11-25T10:46:36Z |
| [#3843](https://github.com/OpenXiangShan/XiangShan/pull/3843) | Feat(trace): support trace core interface | wissygh | closed | 8 | 2024-12-10T10:41:17Z |
| [#3841](https://github.com/OpenXiangShan/XiangShan/pull/3841) | fix(zfh): flh/fsh should raise illegal exception when fs is off. | NewPaulWalker | closed | 1 | 2024-11-08T08:01:06Z |
| [#3840](https://github.com/OpenXiangShan/XiangShan/pull/3840) | feat(zvfh,zfh): add F16 support | HeiHuDie | closed | 4 | 2024-11-09T09:12:35Z |
| [#3835](https://github.com/OpenXiangShan/XiangShan/pull/3835) | fix(dbltrp):critical-error is not treated as diff error | lewislzh | closed | 1 | 2024-11-07T05:14:16Z |
| [#3833](https://github.com/OpenXiangShan/XiangShan/pull/3833) | area(Rob): remove RobEntryBundle's parameters related to perfCount | sinceforYy | closed | 1 | 2024-11-08T02:35:32Z |
| [#3828](https://github.com/OpenXiangShan/XiangShan/pull/3828) | fix(step): fix step for exception. | wissygh | closed | 1 | 2024-11-07T12:25:41Z |
| [#3827](https://github.com/OpenXiangShan/XiangShan/pull/3827) | fix(mip): add otherwise when wen mip and mip.seip is alias of mvip.seip when mvien.seie = 0 | sinceforYy | closed | 2 | 2024-11-08T11:10:34Z |
| [#3826](https://github.com/OpenXiangShan/XiangShan/pull/3826) | fix(CSR): Debug Interrupt is not invisible to M-mode. | wissygh | closed | 1 | 2024-11-04T03:53:43Z |
| [#3823](https://github.com/OpenXiangShan/XiangShan/pull/3823) | feat(zihintpause): support zihintpause | lewislzh | closed | 2 | 2024-11-01T10:33:02Z |
| [#3822](https://github.com/OpenXiangShan/XiangShan/pull/3822) | fix(misalign): fix gpaddr of misalign loads when onlyStage2 | good-circle | closed | 1 | 2024-11-05T03:12:23Z |
| [#3818](https://github.com/OpenXiangShan/XiangShan/pull/3818) | build(version): inject git commit SHA to hardware CommitIDModule | huxuan0307 | closed | 1 | 2024-11-04T13:19:22Z |
| [#3816](https://github.com/OpenXiangShan/XiangShan/pull/3816) | submodule(yunsuan): bump yunsuan | sinceforYy | closed | 1 | 2024-11-08T07:51:56Z |
| [#3814](https://github.com/OpenXiangShan/XiangShan/pull/3814) | submodule(CoupledL2): fix bug of CMO release data | cailuoshan | closed | 1 | 2024-10-30T12:00:20Z |
| [#3812](https://github.com/OpenXiangShan/XiangShan/pull/3812) | fix(intr): set the sequence of interrupt in different mode | sinceforYy | closed | 1 | 2024-11-08T07:50:01Z |
| [#3809](https://github.com/OpenXiangShan/XiangShan/pull/3809) | fix(MisalignBuffer): Use RegEnable in datapath to avoid xprop | good-circle | closed | 2 | 2024-10-30T06:39:02Z |
| [#3803](https://github.com/OpenXiangShan/XiangShan/pull/3803) | fix(AtomicsUnit): Assert `atom_override_xtval` when trigger fire. | wissygh | closed | 1 | 2024-10-30T01:56:49Z |
| [#3795](https://github.com/OpenXiangShan/XiangShan/pull/3795) | fix(CSR): correct the width of PC pgaddr for inst fetch exception | cebarobot | closed | 3 | 2024-11-20T11:19:27Z |
| [#3793](https://github.com/OpenXiangShan/XiangShan/pull/3793) | feat(dbltrp) : add support for critical error  | lewislzh | closed | 2 | 2024-11-01T04:21:00Z |
| [#3791](https://github.com/OpenXiangShan/XiangShan/pull/3791) | style(frontend): manually wrap some line | eastonman | closed | 1 | 2024-10-28T08:58:25Z |
| [#3789](https://github.com/OpenXiangShan/XiangShan/pull/3789) | feat(Ss/Smdbltrp) : Support RISC-V Ss/Smdbltrp Extension | lewislzh | closed | 3 | 2024-10-29T12:02:15Z |
| [#3787](https://github.com/OpenXiangShan/XiangShan/pull/3787) | fix(ICache): cancel prefetch when there is exception from backend | Yan-Muzi | closed | 4 | 2024-11-08T11:13:24Z |
| [#3786](https://github.com/OpenXiangShan/XiangShan/pull/3786) | fix(CSR): fix dcsr to support `stopcount`, `stoptime`, `nmip` and `cetrig` | wissygh | closed | 2 | 2024-11-14T10:13:16Z |
| [#3784](https://github.com/OpenXiangShan/XiangShan/pull/3784) | fix(ICache): use PriorityMux instead of Mux1H for io.error | ngc7331 | closed | 1 | 2024-10-26T14:03:10Z |
| [#3778](https://github.com/OpenXiangShan/XiangShan/pull/3778) | fix(VecExcp): isEnqExcp should be set 0 when writeback has older exception | huxuan0307 | closed | 1 | 2024-10-24T02:02:19Z |
| [#3772](https://github.com/OpenXiangShan/XiangShan/pull/3772) | fix(VSegmentUnit): fix VSegment trigger logic | wissygh | closed | 2 | 2024-10-24T01:56:08Z |
| [#3771](https://github.com/OpenXiangShan/XiangShan/pull/3771) | fix(csr): fix intermediate storage reg for EX_II and EX_VI | sinceforYy | closed | 1 | 2024-10-22T06:57:42Z |
| [#3769](https://github.com/OpenXiangShan/XiangShan/pull/3769) | fix(Ebreak): use isPcBkpt to hold exception raised by ebreak | huxuan0307 | closed | 1 | 2024-10-21T08:04:04Z |
| [#3762](https://github.com/OpenXiangShan/XiangShan/pull/3762) | fix(Breakpoint): memory trigger set {m\|s\|vs}tval with faulting address | huxuan0307 | closed | 1 | 2024-10-18T15:37:52Z |
| [#3759](https://github.com/OpenXiangShan/XiangShan/pull/3759) | fix(misalign): fix misaligned HLV and HLVX | happy-lx | closed | 1 | 2024-10-19T11:56:09Z |
| [#3758](https://github.com/OpenXiangShan/XiangShan/pull/3758) | fix(misalign): Dont mark misalign store as commit | happy-lx | closed | 1 | 2024-10-17T06:18:13Z |
| [#3753](https://github.com/OpenXiangShan/XiangShan/pull/3753) | fix(csr, aia): fix interrupt filter and deleg with AIA | sinceforYy | closed | 11 | 2024-11-22T01:43:22Z |
| [#3745](https://github.com/OpenXiangShan/XiangShan/pull/3745) |  fix(rob): VstartEn should be asserted when triggerAction is debug | wissygh | closed | 1 | 2024-10-16T08:38:28Z |
| [#3741](https://github.com/OpenXiangShan/XiangShan/pull/3741) | fix(MemBlock): more accurate vector ready signal | Anzooooo | closed | 2 | 2024-10-17T05:53:36Z |
| [#3737](https://github.com/OpenXiangShan/XiangShan/pull/3737) | timing(Issue): Opt wakeup and cancel logic and loadDependency timing | sinsanction | closed | 2 | 2024-10-25T02:06:40Z |
| [#3733](https://github.com/OpenXiangShan/XiangShan/pull/3733) | fix(VMergeBuffer): vl of fof only allows setting smaller values | Anzooooo | closed | 1 | 2024-10-16T01:44:17Z |
| [#3731](https://github.com/OpenXiangShan/XiangShan/pull/3731) | fix the write-back of loadMisalignBuffer polluting RegCache | sinsanction | closed | 1 | 2024-10-15T08:35:14Z |
| [#3728](https://github.com/OpenXiangShan/XiangShan/pull/3728) | fix(StoreQueue): fix bug in `uncacheState` FSM | linjuanZ | closed | 1 | 2024-10-15T02:43:40Z |
| [#3722](https://github.com/OpenXiangShan/XiangShan/pull/3722) | fix(ROB): exclude frontend exceptions from deqIsVlsException | huxuan0307 | closed | 1 | 2024-10-14T06:57:28Z |
| [#3721](https://github.com/OpenXiangShan/XiangShan/pull/3721) | fix(zcb): fix ill insn check for zcb arith insn | TheKiteRunner24 | closed | 1 | 2024-10-25T08:29:38Z |
| [#3720](https://github.com/OpenXiangShan/XiangShan/pull/3720) | fix(ROB): vector exception can only be handled when ROB is in idle state | huxuan0307 | closed | 1 | 2024-10-12T06:53:41Z |
| [#3719](https://github.com/OpenXiangShan/XiangShan/pull/3719) | fix(ICache): block waylookup if there is a pending gpf | ngc7331 | closed | 1 | 2024-10-12T03:48:27Z |
| [#3718](https://github.com/OpenXiangShan/XiangShan/pull/3718) | feat(ittage): Reuse always_taken to mark the first occurrence of the jalr inst | sleep-zzz | closed | 5 | 2024-10-30T11:35:05Z |
| [#3717](https://github.com/OpenXiangShan/XiangShan/pull/3717) | fix(csr): fix read/write stimecmp raise EX_II | sinceforYy | closed | 2 | 2024-10-12T01:49:17Z |
| [#3714](https://github.com/OpenXiangShan/XiangShan/pull/3714) | fix(ExceptionGen): assign vector exception info when robidxes equal | huxuan0307 | closed | 1 | 2024-10-12T06:24:54Z |
| [#3710](https://github.com/OpenXiangShan/XiangShan/pull/3710) | fix(csr): fix local counter overflow interrupt req to diff mip.lcofip | sinceforYy | closed | 1 | 2024-10-10T15:46:08Z |
| [#3705](https://github.com/OpenXiangShan/XiangShan/pull/3705) | fix(vtypegen): block the decode until vtype is recovered from walk | Ziyue-Zhang | closed | 1 | 2024-10-09T11:17:16Z |
| [#3704](https://github.com/OpenXiangShan/XiangShan/pull/3704) | fix(StoreQueue): commitLastFlow should be true when the port 1 has no exception | huxuan0307 | closed | 1 | 2024-10-09T06:19:56Z |
| [#3703](https://github.com/OpenXiangShan/XiangShan/pull/3703) | Fix csr distribute write | huxuan0307 | closed | 1 | 2024-10-09T06:20:59Z |
| [#3702](https://github.com/OpenXiangShan/XiangShan/pull/3702) | fix(ROB): vlsNeedCommit only assert one cycle to avoid dup message to RAB | huxuan0307 | closed | 8 | 2024-10-06T16:58:45Z |
| [#3701](https://github.com/OpenXiangShan/XiangShan/pull/3701) | fix(CSR): fix shadow write for many CSRs | huxuan0307 | closed | 1 | 2024-10-05T01:38:10Z |
| [#3700](https://github.com/OpenXiangShan/XiangShan/pull/3700) | fix(CSR): assert vsatpASIDChanged when actually write vsatp by satp | huxuan0307 | closed | 1 | 2024-10-05T01:32:59Z |
| [#3699](https://github.com/OpenXiangShan/XiangShan/pull/3699) | fix(LoadMisalignBuffer): all exception from misalignbuffer should ove… | good-circle | closed | 1 | 2024-10-05T01:32:03Z |
| [#3697](https://github.com/OpenXiangShan/XiangShan/pull/3697) | fix(TLB): Should not send gpa when prefetch or redirect | good-circle | closed | 1 | 2024-10-04T14:51:26Z |
| [#3696](https://github.com/OpenXiangShan/XiangShan/pull/3696) | fix(vector,decode): use OPFV[VF] encoded in inst to check if need FS not Off | huxuan0307 | closed | 1 | 2024-10-04T02:50:45Z |
| [#3695](https://github.com/OpenXiangShan/XiangShan/pull/3695) | feat(rv64v): fix exception for vector fof/non-fof load | huxuan0307 | closed | 10 | 2024-10-04T02:49:03Z |
| [#3693](https://github.com/OpenXiangShan/XiangShan/pull/3693) | feat(Trigger): Trigger Module support mcontrol6. | wissygh | closed | 4 | 2024-10-05T01:30:14Z |
| [#3692](https://github.com/OpenXiangShan/XiangShan/pull/3692) | fix(LoadUnit): add misalign and breakpoint exception check when cleaning up exception vector | cz4e | closed | 2 | 2024-10-02T03:48:43Z |
| [#3691](https://github.com/OpenXiangShan/XiangShan/pull/3691) | fix(Smrnmi): expand NMI interrupt to two types and route the nmi signals to XSTOP | lewislzh | closed | 1 | 2024-10-05T01:49:30Z |
| [#3685](https://github.com/OpenXiangShan/XiangShan/pull/3685) | fix(TLB, RVH): delete the s1tagfix which maybe cause the tag check to fail | pxk27 | closed | 1 | 2024-09-29T16:21:08Z |
| [#3683](https://github.com/OpenXiangShan/XiangShan/pull/3683) | feat(Trigger): Trigger Module support mcontrol6. | wissygh | closed | 1 | - |
| [#3681](https://github.com/OpenXiangShan/XiangShan/pull/3681) | fix(PTW, RVH): add the high bits check of the first s2xlate when the req is allstage | pxk27 | closed | 1 | 2024-10-25T09:44:08Z |
| [#3679](https://github.com/OpenXiangShan/XiangShan/pull/3679) | fix(PTW, RVH): modify the logic of checking high bits of gpaddr | pxk27 | closed | 5 | 2024-10-26T14:00:31Z |
| [#3674](https://github.com/OpenXiangShan/XiangShan/pull/3674) | fix(tlb): overwrite resp information when high address exception happens | good-circle | closed | 1 | 2024-09-27T16:30:57Z |
| [#3671](https://github.com/OpenXiangShan/XiangShan/pull/3671) | fix(sc): SCTable dual port SRAM reads and writes to the same address processing | sleep-zzz | closed | 2 | 2024-09-28T06:12:29Z |
| [#3670](https://github.com/OpenXiangShan/XiangShan/pull/3670) | power(bpu): optimize CGE of bpu/previous_s2_* | Lawrence-ID | closed | 4 | 2024-11-20T06:24:14Z |
| [#3669](https://github.com/OpenXiangShan/XiangShan/pull/3669) | fix(BPU): remove reg of reset_vector | Tang-Haojin | closed | 2 | 2024-09-27T17:37:40Z |
| [#3668](https://github.com/OpenXiangShan/XiangShan/pull/3668) | fix(IMSIC): add TLBuffer for tilelink IO | Tang-Haojin | closed | 1 | 2024-09-27T11:26:20Z |
| [#3667](https://github.com/OpenXiangShan/XiangShan/pull/3667) | fix(combmem): remove x assignment if ren is low | Tang-Haojin | closed | 1 | 2024-09-27T09:32:57Z |
| [#3665](https://github.com/OpenXiangShan/XiangShan/pull/3665) | fix(CSR): remove reg in mhartid | huxuan0307 | closed | 1 | 2024-09-27T01:43:10Z |
| [#3664](https://github.com/OpenXiangShan/XiangShan/pull/3664) | fix(vtypegen): fix initial condition after receive redirect | Ziyue-Zhang | closed | 1 | 2024-09-27T04:27:48Z |
| [#3660](https://github.com/OpenXiangShan/XiangShan/pull/3660) | fix(PTW, RVH): add the check A bit in HPTW when G-stage is for VS-stage | pxk27 | closed | 1 | 2024-09-27T02:49:58Z |
| [#3658](https://github.com/OpenXiangShan/XiangShan/pull/3658) | fix(rv64v): not modify fflags when vl is zero | Ziyue-Zhang | closed | 1 | 2024-09-27T04:28:29Z |
| [#3657](https://github.com/OpenXiangShan/XiangShan/pull/3657) | fix(PTW, RVH): fix the priority of gpf, gaf and gvpn_gpf in PTW | pxk27 | closed | 1 | 2024-09-27T02:50:09Z |
| [#3648](https://github.com/OpenXiangShan/XiangShan/pull/3648) | submodule(CoupledL2): fix bugs in DCT and linkactive | Kumonda221-CrO3 | closed | 1 | 2024-09-26T02:26:35Z |
| [#3647](https://github.com/OpenXiangShan/XiangShan/pull/3647) | fix(csr): change connect0LatencyCtrlSingal to connectNonPipedCtrlSingal | xiaofeibao-xjtu | closed | 1 | 2024-09-26T03:24:10Z |
| [#3644](https://github.com/OpenXiangShan/XiangShan/pull/3644) | fix(CSR,interrupt): use rdata instead of regOut to produce interrupt | huxuan0307 | closed | 1 | 2024-09-26T12:53:32Z |
| [#3643](https://github.com/OpenXiangShan/XiangShan/pull/3643) | fix(vlwakeup): fix vl write back wakeup from intExu or vfExu | Ziyue-Zhang | closed | 1 | 2024-09-25T02:31:43Z |
| [#3641](https://github.com/OpenXiangShan/XiangShan/pull/3641) | fix(ftb): When FTB is closed, the s2_multi_hit_enable should be lowered & Add FTB reading port low fallthroughErr assert. | sleep-zzz | closed | 2 | 2024-09-28T06:11:17Z |
| [#3640](https://github.com/OpenXiangShan/XiangShan/pull/3640) | feat(CSR): add No.16,18 and 19 exceptions | huxuan0307 | closed | 2 | 2024-09-28T10:52:02Z |
| [#3639](https://github.com/OpenXiangShan/XiangShan/pull/3639) | fix(exception): fix exception vaddr generate logic | good-circle | closed | 1 | 2024-09-27T02:41:06Z |
| [#3637](https://github.com/OpenXiangShan/XiangShan/pull/3637) | submodule(CoupledL2): fix bug in ordering between snoop and read | linjuanZ | closed | 1 | 2024-09-24T08:44:51Z |
| [#3636](https://github.com/OpenXiangShan/XiangShan/pull/3636) | fix(BPU): adjust s3 target when fallThroughErr signal is high | my-mayfly | closed | 4 | 2024-09-25T02:31:59Z |
| [#3635](https://github.com/OpenXiangShan/XiangShan/pull/3635) | fix(ghist): fix ghist maintaining | eastonman | closed | 1 | 2024-09-24T06:36:39Z |
| [#3634](https://github.com/OpenXiangShan/XiangShan/pull/3634) | fix(csr): intermediate data should be stored when output not fire | sinceforYy | closed | 2 | 2024-09-26T13:31:44Z |
| [#3633](https://github.com/OpenXiangShan/XiangShan/pull/3633) | submodule(CoupledL2): bump CPL2 with MCP2 gated clock fix | Ivyfeather | closed | 1 | 2024-09-24T02:48:26Z |
| [#3629](https://github.com/OpenXiangShan/XiangShan/pull/3629) | fix(TLB): fix exception judgement condition | good-circle | closed | 1 | 2024-09-24T02:37:09Z |
| [#3628](https://github.com/OpenXiangShan/XiangShan/pull/3628) | fix(ftb): fix ftb pred_rdata not reset | eastonman | closed | 1 | 2024-10-30T11:59:39Z |
| [#3624](https://github.com/OpenXiangShan/XiangShan/pull/3624) | fix(PTW, RVH): fix the gpa high check fail in last s2xlate due to a change of gpaddr | pxk27 | closed | 1 | 2024-09-23T08:02:48Z |
| [#3623](https://github.com/OpenXiangShan/XiangShan/pull/3623) | fix(TLB): Should check vmid when s2xlate in wbhit | good-circle | closed | 1 | 2024-09-23T05:13:02Z |
| [#3621](https://github.com/OpenXiangShan/XiangShan/pull/3621) | submodule(CoupledL2): bump CoupledL2 | linjuanZ | closed | 1 | 2024-09-20T19:54:54Z |
| [#3620](https://github.com/OpenXiangShan/XiangShan/pull/3620) | fix(csr): fix trap inst update when CSRR insts raise trap and remove useless io | sinceforYy | closed | 2 | 2024-09-21T02:39:30Z |
| [#3616](https://github.com/OpenXiangShan/XiangShan/pull/3616) | Fix multiple ftq entries in single rob entry | eastonman | closed | 5 | 2024-10-10T14:54:35Z |
| [#3612](https://github.com/OpenXiangShan/XiangShan/pull/3612) | Bump aia and fix exception generate when access imsic. | NewPaulWalker | closed | 2 | 2024-09-20T09:12:51Z |
| [#3607](https://github.com/OpenXiangShan/XiangShan/pull/3607) | fix(VCVT): disable logic about scalar move instructions. | wissygh | closed | 1 | 2024-09-19T07:28:01Z |
| [#3606](https://github.com/OpenXiangShan/XiangShan/pull/3606) | fix(tage): tage bt sram  read and write the same addr at the same time. | sleep-zzz | closed | 3 | 2024-09-23T03:13:51Z |
| [#3602](https://github.com/OpenXiangShan/XiangShan/pull/3602) | power(backend): add clock gate for Rob and IssueQueue | xiaofeibao-xjtu | closed | 4 | 2024-09-19T02:18:53Z |
| [#3588](https://github.com/OpenXiangShan/XiangShan/pull/3588) | fix(PageTableCache): fix ptwcache refill logic when exception | good-circle | closed | 1 | 2024-09-19T02:58:52Z |
| [#3585](https://github.com/OpenXiangShan/XiangShan/pull/3585) | fix(Trigger): fix trigger's assign to exceptionGen in rob. | wissygh | closed | 1 | 2024-09-17T12:57:15Z |
| [#3583](https://github.com/OpenXiangShan/XiangShan/pull/3583) | power(IssueQueue): add clock gate for deqDelay reg | xiaofeibao-xjtu | closed | 1 | 2024-09-18T02:09:06Z |
| [#3580](https://github.com/OpenXiangShan/XiangShan/pull/3580) | fix(TLB, RVH): fix the bug that pf happens because s1 is nonleaf | pxk27 | closed | 1 | 2024-09-15T04:12:55Z |
| [#3579](https://github.com/OpenXiangShan/XiangShan/pull/3579) | power(bpu): optimize CGE of bpu/predictors_io_update | Lawrence-ID | closed | 5 | 2024-11-19T07:52:03Z |
| [#3577](https://github.com/OpenXiangShan/XiangShan/pull/3577) | fix(CSR): Add legalization code for mstatus.MPP, mnstatus.MNPP and dcsr.PRV | huxuan0307 | closed | 3 | 2024-09-20T17:21:48Z |
| [#3575](https://github.com/OpenXiangShan/XiangShan/pull/3575) | fix(PTW, RVH): fix the wrong state transition when has gpf or gaf | pxk27 | closed | 1 | 2024-09-14T12:36:50Z |
| [#3570](https://github.com/OpenXiangShan/XiangShan/pull/3570) | submodule(rocket-chip): fix Zcmop illegal instruction | ngc7331 | closed | 1 | 2024-09-13T11:24:37Z |
| [#3569](https://github.com/OpenXiangShan/XiangShan/pull/3569) | submodule(ready-to-run): bump nemu to fix the left shift bug | pxk27 | closed | 1 | 2024-09-14T09:50:38Z |
| [#3564](https://github.com/OpenXiangShan/XiangShan/pull/3564) | fix(ittage): fix useful bit update condition | eastonman | closed | 1 | 2024-09-13T10:14:15Z |
| [#3561](https://github.com/OpenXiangShan/XiangShan/pull/3561) | fix(L2TLB, RVH): fix the bug that gaf and gpf occur at the same time | pxk27 | closed | 1 | 2024-09-13T02:33:09Z |
| [#3560](https://github.com/OpenXiangShan/XiangShan/pull/3560) | area(MemBlock): remove redundant signals to optimise area | jin120811 | closed | 5 | 2024-11-12T06:48:25Z |
| [#3559](https://github.com/OpenXiangShan/XiangShan/pull/3559) | feat(Zicbom,Zicboz): add permission check and convert CBO.INVAL to CBO.FLUSH when CBIE=0b01 | huxuan0307 | closed | 2 | 2024-09-14T05:16:33Z |
| [#3558](https://github.com/OpenXiangShan/XiangShan/pull/3558) | fix(Svpbmt): let PBMTEs in [mh]envcfg be RW and have reset value 0 | huxuan0307 | closed | 1 | 2024-09-13T06:02:07Z |
| [#3557](https://github.com/OpenXiangShan/XiangShan/pull/3557) | fix(vstopi): wrong API usage in InterruptFilter | huxuan0307 | closed | 1 | 2024-09-14T14:15:37Z |
| [#3553](https://github.com/OpenXiangShan/XiangShan/pull/3553) | fix(L1TLB, RVH): fix the wrong pf because the perm check of fake pte | pxk27 | closed | 3 | 2024-09-14T02:52:54Z |
| [#3552](https://github.com/OpenXiangShan/XiangShan/pull/3552) | submodule(CoupledL2): optimize PCredit timing | linjuanZ | closed | 1 | 2024-09-12T06:08:20Z |
| [#3551](https://github.com/OpenXiangShan/XiangShan/pull/3551) | fix(L1TLB, RVH): fix the filter of the getGpa req | pxk27 | closed | 1 | 2024-09-12T02:03:27Z |
| [#3547](https://github.com/OpenXiangShan/XiangShan/pull/3547) | fix(aia): fix permit check for aia and fix wen for aia csr. | NewPaulWalker | closed | 1 | 2024-09-13T01:49:38Z |
| [#3545](https://github.com/OpenXiangShan/XiangShan/pull/3545) | timing(IPrefetch): add 1 cycle to s2_finish | ngc7331 | closed | 1 | 2024-10-25T08:00:41Z |
| [#3543](https://github.com/OpenXiangShan/XiangShan/pull/3543) | fix(FTB): Turn off FTB updates when FTB is closed. | sleep-zzz | closed | 2 | 2024-10-30T11:33:10Z |
| [#3542](https://github.com/OpenXiangShan/XiangShan/pull/3542) | timing(ICache): allow send MSHR response to (pre)fetch even when io.flush | ngc7331 | closed | 1 | 2024-10-25T08:13:45Z |
| [#3538](https://github.com/OpenXiangShan/XiangShan/pull/3538) | fix(XSNoCTop): add port `hartIsInReset` for StandAloneDebugModule. | wissygh | closed | 1 | 2024-09-11T10:37:28Z |
| [#3536](https://github.com/OpenXiangShan/XiangShan/pull/3536) | submodule(rocket-chip): bump rocket-chip to fix `SBA` in `DM`. | wissygh | closed | 1 | 2024-09-11T01:54:02Z |
| [#3535](https://github.com/OpenXiangShan/XiangShan/pull/3535) | fix(vecException): fix float exception generate when sew <= 16 | Ziyue-Zhang | closed | 1 | 2024-09-12T09:13:33Z |
| [#3534](https://github.com/OpenXiangShan/XiangShan/pull/3534) | fix(Svinval): make all insts in Sinval behavior like fence to avoid software wrong usage | huxuan0307 | closed | 1 | 2024-09-13T11:25:29Z |
| [#3531](https://github.com/OpenXiangShan/XiangShan/pull/3531) | timing(LsqEnqCtrl): fix timing of lqAllocNumber and sqAllocNumber | xiaofeibao-xjtu | closed | 2 | 2024-09-18T02:17:38Z |
| [#3528](https://github.com/OpenXiangShan/XiangShan/pull/3528) | fix(L1TLB, RVH): fix the bug that no tlbreplay for a long time in L1TLB because of getGpa | pxk27 | closed | 1 | 2024-09-10T09:25:51Z |
| [#3525](https://github.com/OpenXiangShan/XiangShan/pull/3525) | fix(PTW, RVH): delete the check_g_perm reg that is useless | pxk27 | closed | 1 | 2024-09-10T03:19:07Z |
| [#3524](https://github.com/OpenXiangShan/XiangShan/pull/3524) | fix(MMU, RVH): fix the bug that wrong trap when high bits is nonzero and pte.v is invalid | pxk27 | closed | 1 | 2024-09-10T03:18:47Z |
| [#3523](https://github.com/OpenXiangShan/XiangShan/pull/3523) | fix(L2TLB, RVH): fix the assert bug when two same vpn reqs are sent to L2TLB and have af | pxk27 | closed | 1 | 2024-09-10T03:18:31Z |
| [#3520](https://github.com/OpenXiangShan/XiangShan/pull/3520) | Backend fix timing | xiaofeibao-xjtu | closed | 2 | 2024-09-11T03:11:00Z |
| [#3517](https://github.com/OpenXiangShan/XiangShan/pull/3517) | timing(Rab): fix timing of state reg | xiaofeibao-xjtu | closed | 1 | 2024-09-10T02:10:07Z |
| [#3515](https://github.com/OpenXiangShan/XiangShan/pull/3515) | Fix mip implementation | huxuan0307 | closed | 2 | 2024-09-09T10:11:54Z |
| [#3514](https://github.com/OpenXiangShan/XiangShan/pull/3514) | fix(RAS): correct the Call and Ret signals during redirection, and modify the blocking mechanism of RAS. | my-mayfly | closed | 2 | 2024-09-09T16:22:58Z |
| [#3513](https://github.com/OpenXiangShan/XiangShan/pull/3513) | submodule(CoupledL2): fix bugs in PCredit management | linjuanZ | closed | 1 | 2024-09-08T03:57:11Z |
| [#3512](https://github.com/OpenXiangShan/XiangShan/pull/3512) | fix(PTW, RVH): the pte of G-stage which support VS-stage is load rather than original access type | pxk27 | closed | 3 | 2024-09-09T03:56:06Z |
| [#3510](https://github.com/OpenXiangShan/XiangShan/pull/3510) | fix(PTW, RVH): fix the high bits check of gpaddr when onlyS2 | pxk27 | closed | 1 | 2024-09-07T12:08:00Z |
| [#3502](https://github.com/OpenXiangShan/XiangShan/pull/3502) | fix(L1TLB, RVH): fix the length of tag_match about hit in MMUBundle | pxk27 | closed | 1 | 2024-09-06T03:02:13Z |
| [#3499](https://github.com/OpenXiangShan/XiangShan/pull/3499) | timing(FTQ): calculate requests sent to prefetcher one cycle in advance | Yan-Muzi | closed | 1 | 2024-10-25T08:06:47Z |
| [#3496](https://github.com/OpenXiangShan/XiangShan/pull/3496) | fix(csr): add support virtual interrupt for hvictl csr injection | sinceforYy | closed | 3 | 2024-09-09T07:22:36Z |
| [#3495](https://github.com/OpenXiangShan/XiangShan/pull/3495) | fix(rv64v): set vwredsum instructions always depend on oldvd | Ziyue-Zhang | closed | 1 | 2024-09-05T08:54:09Z |
| [#3494](https://github.com/OpenXiangShan/XiangShan/pull/3494) | submodule(YunSuan): bump yunsuan to fix neg of condition for f32toi16 | sinceforYy | closed | 1 | 2024-09-05T02:18:05Z |
| [#3492](https://github.com/OpenXiangShan/XiangShan/pull/3492) | fix(ICache): MSHR also update meta_codes when updating waymasks | ngc7331 | closed | 1 | 2024-09-06T08:17:40Z |
| [#3486](https://github.com/OpenXiangShan/XiangShan/pull/3486) | fix(csr): remove skip mhpmevents csr to diff mhpmevnts | sinceforYy | closed | 1 | 2024-09-05T02:17:31Z |
| [#3482](https://github.com/OpenXiangShan/XiangShan/pull/3482) | timing(Backend): add OG2 stage for vector mem | sinsanction | closed | 3 | 2024-09-05T02:04:27Z |
| [#3480](https://github.com/OpenXiangShan/XiangShan/pull/3480) | feat(riscv64): Support RISC-V Smrnmi extension | lewislzh | closed | 2 | 2024-09-05T02:16:27Z |
| [#3472](https://github.com/OpenXiangShan/XiangShan/pull/3472) | fix(Trigger): Breakpoint exception generated by trigger shouldn't enter dmode. | wissygh | closed | 1 | 2024-09-03T01:40:04Z |
| [#3471](https://github.com/OpenXiangShan/XiangShan/pull/3471) | timing(IssueQueue): change mem iq enqNum from 2 to 1 | xiaofeibao-xjtu | closed | 2 | 2024-09-03T07:56:28Z |
| [#3469](https://github.com/OpenXiangShan/XiangShan/pull/3469) | fix(csr): fix wen perfEvents to wen mhpmevents csr | sinceforYy | closed | 1 | 2024-09-03T01:39:36Z |
| [#3467](https://github.com/OpenXiangShan/XiangShan/pull/3467) | timing(MemBlock): optimize MemBlock timing | happy-lx | closed | 50 | 2024-09-03T12:34:55Z |
| [#3462](https://github.com/OpenXiangShan/XiangShan/pull/3462) | fix(VLSU): Vector Unit-Stride instr should trigger misaligned exception | Anzooooo | closed | 1 | 2024-09-02T01:55:20Z |
| [#3460](https://github.com/OpenXiangShan/XiangShan/pull/3460) | fix(Zicclsm): Vectors should not support misaligned access by Hardware | Anzooooo | closed | 1 | 2024-09-02T02:22:27Z |
| [#3458](https://github.com/OpenXiangShan/XiangShan/pull/3458) | fix(SQ, SimMMIO, L2): fix bugs in mtval when non-data error is raised | linjuanZ | closed | 4 | 2024-09-13T01:11:56Z |
| [#3457](https://github.com/OpenXiangShan/XiangShan/pull/3457) | DataPath fix timing and performance，MemBlock fix ssit performance | xiaofeibao-xjtu | closed | 2 | 2024-09-01T09:41:15Z |
| [#3453](https://github.com/OpenXiangShan/XiangShan/pull/3453) | fix(L2TLB): Fix exception generation logic  | good-circle | closed | 4 | 2024-09-12T15:16:05Z |
| [#3450](https://github.com/OpenXiangShan/XiangShan/pull/3450) | fix(NewCSR, RVH): fix the check of hypervisor load/store instruction when hstatus.hu is valid | pxk27 | closed | 1 | 2024-08-31T15:00:44Z |
| [#3447](https://github.com/OpenXiangShan/XiangShan/pull/3447) | fix(MMU, RVH): add the check of reserverd, n & pbmt of pte | pxk27 | closed | 3 | 2024-08-30T09:05:48Z |
| [#3442](https://github.com/OpenXiangShan/XiangShan/pull/3442) | fix(MMU, RVH): correct the gpaddr computation in TLB | pxk27 | closed | 1 | 2024-08-29T13:14:35Z |
| [#3441](https://github.com/OpenXiangShan/XiangShan/pull/3441) | Trigger: check tdata1.dmode before write `tdata` | wissygh | closed | 1 | 2024-08-29T01:53:00Z |
| [#3439](https://github.com/OpenXiangShan/XiangShan/pull/3439) | feat(riscv64): Support RISC-V Zfa extension | sinceforYy | closed | 3 | 2024-09-03T02:47:05Z |
| [#3437](https://github.com/OpenXiangShan/XiangShan/pull/3437) | Fix frontend topdown pmu & simulation perf ctr | eastonman | closed | 2 | 2024-08-28T08:39:32Z |
| [#3436](https://github.com/OpenXiangShan/XiangShan/pull/3436) | LoadQueueReplay: fix LoadQueueReplay enqueue logic | Anzooooo | closed | 1 | 2024-08-28T03:43:12Z |
| [#3434](https://github.com/OpenXiangShan/XiangShan/pull/3434) | fix(NewCSR): when STCE in menvcfg is zero, STCE in henvcfg is read-only zero | sinceforYy | closed | 1 | 2024-09-02T03:48:36Z |
| [#3433](https://github.com/OpenXiangShan/XiangShan/pull/3433) | IPrefetch: fix s1 fsm for softPrefetch | ngc7331 | closed | 1 | 2024-08-30T07:49:48Z |
| [#3430](https://github.com/OpenXiangShan/XiangShan/pull/3430) | rv64v: fix uop split for vfwredsum instructions when lmul==8 | Ziyue-Zhang | closed | 1 | 2024-08-27T14:52:13Z |
| [#3428](https://github.com/OpenXiangShan/XiangShan/pull/3428) | PTW, RVH: fix the bug about unaligned check in isPf and isAf | pxk27 | closed | 2 | 2024-08-27T02:28:54Z |
| [#3427](https://github.com/OpenXiangShan/XiangShan/pull/3427) | PTW, RVH: add the sv48 high gpaddr check | pxk27 | closed | 1 | 2024-08-27T02:28:39Z |
| [#3426](https://github.com/OpenXiangShan/XiangShan/pull/3426) | RVA23 CMO (Cache Maintenance Operation) | Ivyfeather | closed | 16 | 2024-08-26T19:40:09Z |
| [#3424](https://github.com/OpenXiangShan/XiangShan/pull/3424) | PMA, MMU: Fix bug of PA48 | good-circle | closed | 1 | 2024-08-27T01:51:35Z |
| [#3423](https://github.com/OpenXiangShan/XiangShan/pull/3423) | PTW, RVH: init the A、D、PPN of fake pte to avoid wrong pf and wrong gpaddr in L1TLB | pxk27 | closed | 4 | 2024-08-27T02:28:20Z |
| [#3422](https://github.com/OpenXiangShan/XiangShan/pull/3422) | DebugModule: fix bug, trap don't take place in dmode. | wissygh | closed | 1 | 2024-08-26T09:35:10Z |
| [#3421](https://github.com/OpenXiangShan/XiangShan/pull/3421) | zfhmin: add zfhmin extensions | zmx2018 | closed | 3 | 2024-08-27T15:40:09Z |
| [#3420](https://github.com/OpenXiangShan/XiangShan/pull/3420) | MMU, RVH: fix the refill of pte that has gpf and change the check of pf/gpf in PTW and HPTW | pxk27 | closed | 1 | 2024-08-26T09:36:39Z |
| [#3418](https://github.com/OpenXiangShan/XiangShan/pull/3418) | Rob: fix bug of rob commit. | wissygh | closed | 2 | 2024-08-24T14:34:43Z |
| [#3413](https://github.com/OpenXiangShan/XiangShan/pull/3413) | ICacheMissUnit: wait for all beats even corrupt has already occurred | ngc7331 | closed | 1 | 2024-08-22T06:48:50Z |
| [#3409](https://github.com/OpenXiangShan/XiangShan/pull/3409) | rv64: add Zimop extension support | Ziyue-Zhang | closed | 1 | 2024-09-02T03:52:20Z |
| [#3407](https://github.com/OpenXiangShan/XiangShan/pull/3407) | Support Sstvala and Shvstvala extensions | huxuan0307 | closed | 13 | 2024-08-28T03:30:29Z |
| [#3404](https://github.com/OpenXiangShan/XiangShan/pull/3404) | svpbmt: add simplified support | Maxpicca-Li | closed | 6 | 2024-08-26T12:29:37Z |
| [#3399](https://github.com/OpenXiangShan/XiangShan/pull/3399) | Vfalu: fix fflagsRedMask use outVecCtrl | lewislzh | closed | 1 | 2024-08-19T01:55:19Z |
| [#3397](https://github.com/OpenXiangShan/XiangShan/pull/3397) | fix the wrong condition of Mux1H about tval2 that makes wrong gpa written into htval or mtval2 | pxk27 | closed | 1 | 2024-08-19T06:03:29Z |
| [#3396](https://github.com/OpenXiangShan/XiangShan/pull/3396) | Frontend: implement prefetch.i support (RVA23 Zicbop) | ngc7331 | closed | 2 | 2024-08-17T09:10:09Z |
| [#3395](https://github.com/OpenXiangShan/XiangShan/pull/3395) | DebugModule: Fix bug of singleStep. | wissygh | closed | 1 | 2024-08-16T16:17:04Z |
| [#3391](https://github.com/OpenXiangShan/XiangShan/pull/3391) | Bump yunsuan:VIdiv fix state-machine, prioritize flush | lewislzh | closed | 1 | 2024-08-16T18:55:59Z |
| [#3389](https://github.com/OpenXiangShan/XiangShan/pull/3389) | RAS: Block BPU prediction when the speculative queue is about to overflow | my-mayfly | closed | 1 | 2024-08-17T09:11:10Z |
| [#3387](https://github.com/OpenXiangShan/XiangShan/pull/3387) | DataPath: write v0Regfile and vlRegfile add a pipe for fix timing | xiaofeibao-xjtu | closed | 1 | 2024-08-19T06:59:50Z |
| [#3386](https://github.com/OpenXiangShan/XiangShan/pull/3386) | IFU: fix cross-page exception | ngc7331 | closed | 1 | 2024-08-16T06:21:07Z |
| [#3385](https://github.com/OpenXiangShan/XiangShan/pull/3385) | L1TLB, RVH: fix the wrong gpf because checking s2 when ptw resp is onlystage1 | pxk27 | closed | 1 | 2024-08-16T02:48:35Z |
| [#3384](https://github.com/OpenXiangShan/XiangShan/pull/3384) | bump yunsuan: fix fflags update | Ziyue-Zhang | closed | 1 | 2024-08-16T02:24:25Z |
| [#3382](https://github.com/OpenXiangShan/XiangShan/pull/3382) | BusyTable: remove useless wakeup for fix timing | xiaofeibao-xjtu | closed | 1 | 2024-08-19T02:07:23Z |
| [#3379](https://github.com/OpenXiangShan/XiangShan/pull/3379) | CSR: miselect, siselect, vsiselect should have reset value since they are WARL | huxuan0307 | closed | 1 | 2024-08-15T02:30:16Z |
| [#3378](https://github.com/OpenXiangShan/XiangShan/pull/3378) | ROB: the interrupt_safe of CSR instruction should be false | pxk27 | closed | 1 | 2024-08-15T02:30:01Z |
| [#3375](https://github.com/OpenXiangShan/XiangShan/pull/3375) | CSR, RVH: fix the wrong val writen in htval when having igpf | pxk27 | closed | 1 | 2024-08-14T02:20:46Z |
| [#3374](https://github.com/OpenXiangShan/XiangShan/pull/3374) | Backend: remove useless loadCancel for fix timing | xiaofeibao-xjtu | closed | 2 | 2024-08-16T02:24:06Z |
| [#3372](https://github.com/OpenXiangShan/XiangShan/pull/3372) | IPrefetch: disable IPrefetchPipe s2 stage if CSR does not enable iprefetch | ngc7331 | closed | 1 | 2024-08-14T17:22:47Z |
| [#3370](https://github.com/OpenXiangShan/XiangShan/pull/3370) | style(Frontend): use scalafmt formatting frontend | Yan-Muzi | closed | 12 | 2024-10-25T15:08:57Z |
| [#3367](https://github.com/OpenXiangShan/XiangShan/pull/3367) | bpu: Ittage read during update | sleep-zzz | closed | 7 | - |
| [#3364](https://github.com/OpenXiangShan/XiangShan/pull/3364) | IssueQueue: only trans valid but not issued entry for fix ldCancel timing | xiaofeibao-xjtu | closed | 1 | 2024-08-09T07:56:16Z |
| [#3360](https://github.com/OpenXiangShan/XiangShan/pull/3360) | CSR: fix custom IRQ injection mechanism | huxuan0307 | closed | 1 | 2024-08-08T09:24:43Z |
| [#3359](https://github.com/OpenXiangShan/XiangShan/pull/3359) | Bump difftest. | NewPaulWalker | closed | 2 | 2024-08-12T02:36:24Z |
| [#3358](https://github.com/OpenXiangShan/XiangShan/pull/3358) | rv64v: fix temp vector register index which need to start from 32 | Ziyue-Zhang | closed | 1 | 2024-08-08T02:22:20Z |
| [#3357](https://github.com/OpenXiangShan/XiangShan/pull/3357) | PTW, RVH: fix the x state of stage1 pf/af when the first s2xlate happens gpf in PTW | pxk27 | closed | 2 | 2024-08-08T17:36:20Z |
| [#3353](https://github.com/OpenXiangShan/XiangShan/pull/3353) | CSR: use "ignore illegal write" WARL strategy for tselect | huxuan0307 | closed | 1 | 2024-08-07T16:57:59Z |
| [#3344](https://github.com/OpenXiangShan/XiangShan/pull/3344) | IBuffer: change read ptr logic for fix timing, change outputEntries logic for better performance | xiaofeibao-xjtu | closed | 2 | 2024-08-12T02:27:21Z |
| [#3343](https://github.com/OpenXiangShan/XiangShan/pull/3343) | LLPTW, RVH: fix the bug that llptw resp wrong stage1 when first s2xlate has gpf in LLPTW | pxk27 | closed | 1 | 2024-08-07T07:23:59Z |
| [#3342](https://github.com/OpenXiangShan/XiangShan/pull/3342) | PTW, RVH: fix the error S1 resp when gpf happened and s1_level == 0 | pxk27 | closed | 1 | 2024-08-06T04:57:13Z |
| [#3338](https://github.com/OpenXiangShan/XiangShan/pull/3338) | CSR: add custom IRQ injection mechanism | huxuan0307 | closed | 1 | 2024-08-06T01:54:57Z |
| [#3332](https://github.com/OpenXiangShan/XiangShan/pull/3332) | Bump CoupledL2 and OpenLLC | linjuanZ | closed | 1 | 2024-08-02T16:33:01Z |
| [#3331](https://github.com/OpenXiangShan/XiangShan/pull/3331) | MMU, RVH, fix the af refill error when refilling page cache | pxk27 | closed | 2 | 2024-08-08T08:26:09Z |
| [#3329](https://github.com/OpenXiangShan/XiangShan/pull/3329) | IFU: fix mmio fsm for itlb handshake | ngc7331 | closed | 1 | 2024-08-06T10:03:48Z |
| [#3327](https://github.com/OpenXiangShan/XiangShan/pull/3327) | CSR: initialize vstart to avoid X propagation at DecodeStage | huxuan0307 | closed | 5 | 2024-08-08T01:48:52Z |
| [#3324](https://github.com/OpenXiangShan/XiangShan/pull/3324) | NewCSR: fix condition of select candidates and trap taken to VS-mode | sinceforYy | closed | 1 | 2024-08-04T10:06:35Z |
| [#3319](https://github.com/OpenXiangShan/XiangShan/pull/3319) | ICache: cancel (pre)fetch request if port1 is mmio | ngc7331 | closed | 2 | 2024-08-06T10:02:11Z |
| [#3317](https://github.com/OpenXiangShan/XiangShan/pull/3317) | PTW, RVH: rewrite the PTW resp logic when PTW get gpf or gaf from HPTW | pxk27 | closed | 1 | 2024-08-01T02:40:42Z |
| [#3314](https://github.com/OpenXiangShan/XiangShan/pull/3314) | CSR: enable misa.B which contains `Zba`, `Zbb` and `Zbs` extensions | huxuan0307 | closed | 1 | - |
| [#3308](https://github.com/OpenXiangShan/XiangShan/pull/3308) | PageCache, RVH: add the condition that page cache resp L1tlb when stage1 hit but has pf in allstage | pxk27 | closed | 2 | 2024-07-30T08:40:43Z |
| [#3305](https://github.com/OpenXiangShan/XiangShan/pull/3305) | MMU: replace RRArbiter with RRArbiterInit | pxk27 | closed | 1 | 2024-07-29T08:17:49Z |
| [#3301](https://github.com/OpenXiangShan/XiangShan/pull/3301) | NewCSR: fix mie.LCOFIE is RW and init value 0 | sinceforYy | closed | 1 | 2024-07-30T03:57:57Z |
| [#3300](https://github.com/OpenXiangShan/XiangShan/pull/3300) | NewCSR: skip *ip difftest | sinceforYy | closed | 1 | 2024-07-30T03:57:04Z |
| [#3298](https://github.com/OpenXiangShan/XiangShan/pull/3298) | LLPTW, RVH: fix the bug that llptw continue s2xlate when the pte which mem resp has pf | pxk27 | closed | 1 | 2024-07-29T02:11:50Z |
| [#3296](https://github.com/OpenXiangShan/XiangShan/pull/3296) | vtype: init vtype's vill to 1 and other fields to 0 | Ziyue-Zhang | closed | 1 | 2024-07-30T03:55:30Z |
| [#3295](https://github.com/OpenXiangShan/XiangShan/pull/3295) | MDP: fix mdp update logic | cz4e | closed | 1 | - |
| [#3294](https://github.com/OpenXiangShan/XiangShan/pull/3294) | difftest: support difftest for fcsr. | NewPaulWalker | closed | 4 | 2024-07-31T02:32:03Z |
| [#3293](https://github.com/OpenXiangShan/XiangShan/pull/3293) | Decode: add DecodeBuf for fix timing of ready to Ibuffer | xiaofeibao-xjtu | closed | 1 | 2024-07-26T08:18:43Z |
| [#3290](https://github.com/OpenXiangShan/XiangShan/pull/3290) | Backend: add Reg Cache for int register file | sinsanction | closed | 15 | 2024-07-26T09:05:13Z |
| [#3284](https://github.com/OpenXiangShan/XiangShan/pull/3284) | vtype: enq spec vtype to vtypebuffer's snapshot | Ziyue-Zhang | closed | 1 | 2024-07-25T02:55:50Z |
| [#3278](https://github.com/OpenXiangShan/XiangShan/pull/3278) | CoupledL2: support for MCP2 SRAM, CHILog and CHI Issue E.b | Maxpicca-Li | closed | 5 | 2024-07-31T08:49:14Z |
| [#3263](https://github.com/OpenXiangShan/XiangShan/pull/3263) | CoupledL2: optimize timing and add MCP2 SRAM | linjuanZ | closed | 2 | - |
| [#3208](https://github.com/OpenXiangShan/XiangShan/pull/3208) | MemBlock: fix timing of scalar load/store issue and writeback | weidingliu | closed | 10 | 2024-07-31T11:55:55Z |
| [#2704](https://github.com/OpenXiangShan/XiangShan/pull/2704) | ifu: fix mmioFlushWb condition when backend redirect | eastonman | closed | 1 | 2024-02-22T01:42:45Z |
| [#2670](https://github.com/OpenXiangShan/XiangShan/pull/2670) | prefetch: fix bug of sms evict | Maxpicca-Li | closed | 1 | - |
| [#2660](https://github.com/OpenXiangShan/XiangShan/pull/2660) | ICache: fix ICacheMainPipe bug about sfence | ssszwic | closed | 1 | 2024-01-23T06:31:55Z |
| [#2632](https://github.com/OpenXiangShan/XiangShan/pull/2632) | ICache: fix ICacheMainPipe bug about fencei | ssszwic | closed | 1 | 2024-01-16T01:51:40Z |
| [#2604](https://github.com/OpenXiangShan/XiangShan/pull/2604) | ICache: fix replacer bug | ssszwic | closed | 1 | 2024-01-02T15:14:42Z |
| [#2555](https://github.com/OpenXiangShan/XiangShan/pull/2555) | LQ: Fixed the bug that the load did not detect RAR violation | cz4e | closed | 2 | 2023-12-18T07:07:25Z |
| [#2554](https://github.com/OpenXiangShan/XiangShan/pull/2554) | LSQ: fix uncache req logic | cz4e | closed | 1 | 2023-12-15T09:10:02Z |
| [#2536](https://github.com/OpenXiangShan/XiangShan/pull/2536) | LDU: fix ld-ld nuke rollback logic | cz4e | closed | 1 | 2023-12-08T13:03:25Z |
| [#2520](https://github.com/OpenXiangShan/XiangShan/pull/2520) | LDU: fix ldu ldld nuke generate logic | cz4e | closed | 4 | 2023-12-05T04:06:14Z |
| [#2508](https://github.com/OpenXiangShan/XiangShan/pull/2508) | pf: fix negetive stream | happy-lx | closed | 1 | - |
| [#2504](https://github.com/OpenXiangShan/XiangShan/pull/2504) | Uncache: fix flush.empty logic | cz4e | closed | 1 | 2023-11-26T16:43:18Z |
| [#2482](https://github.com/OpenXiangShan/XiangShan/pull/2482) | PMA: lr should raise load access fault | good-circle | closed | 1 | 2023-11-16T01:57:40Z |
| [#2480](https://github.com/OpenXiangShan/XiangShan/pull/2480) | csr: fix interrupt priority | wakafa1 | closed | 1 | 2023-11-15T11:27:03Z |
| [#2478](https://github.com/OpenXiangShan/XiangShan/pull/2478) | PMP: Write to pmpicfg should be ignored when locked | good-circle | closed | 1 | 2023-11-16T01:49:06Z |
| [#2476](https://github.com/OpenXiangShan/XiangShan/pull/2476) | Bump CPL2 to master@Nov14 with timing fixes | Ivyfeather | closed | 1 | 2023-11-14T08:03:19Z |
| [#2473](https://github.com/OpenXiangShan/XiangShan/pull/2473) | PTW, MissQueue: Enlarge MSHR size for larger ptwfilter | good-circle | closed | 1 | 2023-11-13T01:23:02Z |
| [#2447](https://github.com/OpenXiangShan/XiangShan/pull/2447) | Bump coupledL2: fix several functional bugs | wakafa1 | closed | 1 | 2023-11-02T01:45:35Z |
| [#2445](https://github.com/OpenXiangShan/XiangShan/pull/2445) | LDU: fix rar flush logic | cz4e | closed | 6 | 2023-11-03T02:23:38Z |
| [#2440](https://github.com/OpenXiangShan/XiangShan/pull/2440) | UncacheBuffer: fix mmio data writeback logic | cz4e | closed | 2 | 2023-10-31T01:33:46Z |
| [#2405](https://github.com/OpenXiangShan/XiangShan/pull/2405) | sms: fix alias bug | Maxpicca-Li | closed | 2 | 2023-10-21T14:54:02Z |
| [#2388](https://github.com/OpenXiangShan/XiangShan/pull/2388) | LDU, LQ:fix wpu wakeup | cz4e | closed | 1 | 2023-10-17T01:30:32Z |
| [#2387](https://github.com/OpenXiangShan/XiangShan/pull/2387) | LDU: remove s3 nuke check logic | cz4e | closed | 1 | 2023-10-16T03:43:40Z |
| [#2384](https://github.com/OpenXiangShan/XiangShan/pull/2384) | Add a new AXI4UserYanker node to fix 4 core bug | sumailyyc | closed | 1 | 2023-10-15T02:03:43Z |
| [#2381](https://github.com/OpenXiangShan/XiangShan/pull/2381) | MemBlock: pass atomic exception through load port | Tang-Haojin | closed | 1 | 2023-10-13T01:34:32Z |
| [#2369](https://github.com/OpenXiangShan/XiangShan/pull/2369) | mainpipe: fix probe tob | happy-lx | closed | 1 | 2023-10-11T01:01:20Z |
| [#2346](https://github.com/OpenXiangShan/XiangShan/pull/2346) | Bump difftest | poemonsense | closed | 1 | 2023-09-28T01:46:41Z |
| [#2303](https://github.com/OpenXiangShan/XiangShan/pull/2303) | Bump difftest | poemonsense | closed | 1 | - |
| [#2300](https://github.com/OpenXiangShan/XiangShan/pull/2300) | ftq: fix predecode redirect use RAS condition | eastonman | closed | 1 | 2023-09-14T01:55:14Z |
| [#2299](https://github.com/OpenXiangShan/XiangShan/pull/2299) | bpu s3 redirect bug fix, add redirect latency stats, and use histogram for some old stats | Lingrui98 | closed | 2 | 2023-09-14T01:58:47Z |
| [#2298](https://github.com/OpenXiangShan/XiangShan/pull/2298) | LDU: fix load writeback twice | cz4e | closed | 6 | 2023-09-14T14:21:57Z |
| [#2296](https://github.com/OpenXiangShan/XiangShan/pull/2296) | CSR: fix the writable mask of `mie` | poemonsense | closed | 1 | 2023-09-13T02:34:58Z |
| [#2295](https://github.com/OpenXiangShan/XiangShan/pull/2295) | bump CPL2: make sure pftRespQueue will never overflow | Ivyfeather | closed | 3 | 2023-09-14T01:54:16Z |
| [#2294](https://github.com/OpenXiangShan/XiangShan/pull/2294) | CSR: mstatus bits 0 and 4 are read-only zeros | poemonsense | closed | 1 | 2023-09-12T10:25:19Z |
| [#2270](https://github.com/OpenXiangShan/XiangShan/pull/2270) | bump CPL2: fix grantBuf | Ivyfeather | closed | 5 | 2023-09-03T05:24:03Z |
| [#2259](https://github.com/OpenXiangShan/XiangShan/pull/2259) | wbq: fix wbq's FSM logic | happy-lx | closed | 1 | 2023-08-29T07:43:21Z |
| [#2244](https://github.com/OpenXiangShan/XiangShan/pull/2244) | bump CPL2: fix sinkC | Ivyfeather | closed | 4 | 2023-08-17T01:16:50Z |
| [#2234](https://github.com/OpenXiangShan/XiangShan/pull/2234) | Ldu: fix sms train logic | cz4e | closed | 1 | - |
| [#2219](https://github.com/OpenXiangShan/XiangShan/pull/2219) | Ldu: fix perf bug | cz4e | closed | 4 | - |
| [#2218](https://github.com/OpenXiangShan/XiangShan/pull/2218) | Refactor parameters of RegFile | huxuan0307 | closed | 5 | 2023-08-05T10:36:51Z |
| [#2203](https://github.com/OpenXiangShan/XiangShan/pull/2203) | Jal target fix | chenguokai | closed | 2 | 2023-09-04T11:42:25Z |
| [#2198](https://github.com/OpenXiangShan/XiangShan/pull/2198) | FTQ: fix debug cfi check condition | chenguokai | closed | 1 | 2023-07-23T04:13:45Z |
| [#2197](https://github.com/OpenXiangShan/XiangShan/pull/2197) | vector: fix vred instruction when mask is set | Ziyue-Zhang | closed | 1 | 2023-08-04T07:28:24Z |
| [#2194](https://github.com/OpenXiangShan/XiangShan/pull/2194) | Lsq: fix load exception buffer enqueue condition | cz4e | closed | 2 | 2023-07-21T01:19:32Z |
| [#2186](https://github.com/OpenXiangShan/XiangShan/pull/2186) | Predecode: fix ebreak predecoded as jalr | chenguokai | closed | 1 | 2023-07-20T07:46:40Z |
| [#2071](https://github.com/OpenXiangShan/XiangShan/pull/2071) | Fix constant | Maxpicca-Li | closed | 3 | 2023-05-09T02:50:25Z |
| [#1496](https://github.com/OpenXiangShan/XiangShan/pull/1496) | chore: test sram model | AugustusWillisWang | closed | 7 | - |
| [#572](https://github.com/OpenXiangShan/XiangShan/pull/572) | TLB: wrap tlb's tag(vpn) with CAM | Lemover | closed | 3 | 2021-02-23T06:48:26Z |
