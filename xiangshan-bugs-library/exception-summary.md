# Exception-Related Bug Summary

- Source: `/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-bugs-library/xiangshan-bugs-src`
- Generated at: `2026-08-29T14:56:20+00:00`
- Matching scope: metadata, descriptions, and text reproducers; commit logs excluded by default
- Corpus: **265** issues
- Exception-related matches: **142** issues

## Classification Rules

An item is counted when its metadata/title, description, or text reproducer contains a core exception-event term such as `exception`, `trap`, `fault`, `illegal instruction`, `interrupt`, `NMI`, `breakpoint`, `watchpoint`, or `DRET`. CSR names such as `mcause`/`mtval` and context terms such as `misaligned` are reported as groups after an item has matched the core exception-event rule. Commit logs are excluded unless `--include-commit-logs` is set, because implementation diffs often contain broad signal names that inflate the count.

## Keyword Group Counts

| Group | Matches |
|---|---:|
| exception/trap | 124 |
| trap CSR state | 96 |
| fault | 74 |
| illegal instruction | 33 |
| misalignment | 31 |
| interrupt/NMI | 21 |
| debug/breakpoint | 14 |

## State Counts

| State | Matches |
|---|---:|
| closed | 76 |
| open | 66 |

## Year Counts

| Year | Matches |
|---|---:|
| 2026 | 96 |
| 2025 | 31 |
| 2024 | 15 |

## Author Counts

| Author | Matches |
|---|---:|
| lhb-sec | 18 |
| KnightGOKU | 11 |
| youzi27 | 8 |
| ha0lyu | 8 |
| LuLuji04 | 8 |
| ruc-jty | 7 |
| zhangkanqi | 7 |
| oChunCai | 6 |
| LeeHaofeng | 5 |
| wndmll643 | 5 |
| Jason-Young123 | 5 |
| fly-1011 | 5 |
| jf-cc727 | 4 |
| ZhongYic00 | 3 |
| Jacob-yen | 3 |
| YzhDDDing | 3 |
| poemonsense | 3 |
| timegoer | 3 |
| ScottaMcdonald | 2 |
| jimmymtest | 2 |
| nyh1 | 2 |
| biquanha | 2 |
| bantierr | 2 |
| MAX-max1118 | 2 |
| Security-HC | 1 |
| ra4ing | 1 |
| 0x1B05 | 1 |
| Lruos | 1 |
| maxiaoran24 | 1 |
| Hoshi44 | 1 |
| ambulGruidae | 1 |
| quange51 | 1 |
| WHR-oss | 1 |
| BoA5li | 1 |
| canxin121 | 1 |
| E1thannn | 1 |
| MrCookieeeee | 1 |
| chenjie35335 | 1 |
| JacyCui | 1 |
| leesum1 | 1 |
| chenhychen | 1 |
| cyyself | 1 |

## Matched Issues

| Issue | State | Created | Author | Groups | Evidence files | Title |
|---:|---|---|---|---|---:|---|
| [#6343](https://github.com/OpenXiangShan/XiangShan/issues/6343) | open | 2026-08-11 | LeeHaofeng | debug/breakpoint, exception/trap, fault | 4 | AtomicsUnit retains stale `hardwareError` across atomic operations |
| [#6303](https://github.com/OpenXiangShan/XiangShan/issues/6303) | open | 2026-07-29 | lhb-sec | exception/trap, fault, illegal instruction, interrupt/NMI, trap CSR state | 6 | Nested exception in M-mode causes subsequent exception to deadlock |
| [#6302](https://github.com/OpenXiangShan/XiangShan/issues/6302) | open | 2026-07-28 | lhb-sec | exception/trap, fault, illegal instruction, trap CSR state | 5 | XiangShan does not trap on vector instruction with reserved vsew encoding |
| [#6298](https://github.com/OpenXiangShan/XiangShan/issues/6298) | open | 2026-07-28 | lhb-sec | exception/trap, illegal instruction, trap CSR state | 4 | XiangShan misses illegal-instruction trap for out-of-range vstart |
| [#6296](https://github.com/OpenXiangShan/XiangShan/issues/6296) | open | 2026-07-28 | lhb-sec | exception/trap, fault, misalignment, trap CSR state | 4 | Vector store reports wrong `mcause` in M-mode |
| [#6295](https://github.com/OpenXiangShan/XiangShan/issues/6295) | open | 2026-07-28 | lhb-sec | exception/trap, fault, interrupt/NMI, trap CSR state | 4 | `vle32ff.v` corrupts destination vector register on Load Access Fault |
| [#6294](https://github.com/OpenXiangShan/XiangShan/issues/6294) | open | 2026-07-28 | wndmll643 | exception/trap, fault, trap CSR state | 3 | A store-page-faulting AMO clobbers its destination register (rd) |
| [#6293](https://github.com/OpenXiangShan/XiangShan/issues/6293) | open | 2026-07-28 | lhb-sec | exception/trap, fault, illegal instruction, misalignment, trap CSR state | 4 | `vle64ff.v` misses Load Access Fault on misaligned access to I/O PMA region |
| [#6292](https://github.com/OpenXiangShan/XiangShan/issues/6292) | open | 2026-07-28 | wndmll643 | exception/trap, fault, misalignment, trap CSR state | 3 | vstart set to wrong element on a page-faulting vector load (for debugging assistance) |
| [#6289](https://github.com/OpenXiangShan/XiangShan/issues/6289) | open | 2026-07-27 | lhb-sec | exception/trap, fault, trap CSR state | 3 | AMO deadlocks after `vse32.v` store access fault |
| [#6288](https://github.com/OpenXiangShan/XiangShan/issues/6288) | open | 2026-07-27 | lhb-sec | exception/trap, fault, misalignment, trap CSR state | 5 | `sw` instruction reports wrong `mcause` in M-mode |
| [#6276](https://github.com/OpenXiangShan/XiangShan/issues/6276) | closed | 2026-07-23 | LeeHaofeng | interrupt/NMI | 2 | IMSIC: VS-mode claim (`vstopei` EOI) is broadcast to all guest interrupt files instead of the `vgein`-selected one, causing silent MSI loss |
| [#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267) | open | 2026-07-21 | lhb-sec | exception/trap, fault, trap CSR state | 2 | `fsw fa1,716(t6)` to PMA-missing address `0x2cc` causes StoreUnit deadlock |
| [#6265](https://github.com/OpenXiangShan/XiangShan/issues/6265) | open | 2026-07-21 | lhb-sec | exception/trap, fault, misalignment, trap CSR state | 3 | Behavior mismatch on `ld s8,0xf0(t6)` to misaligned address `0xf1` |
| [#6264](https://github.com/OpenXiangShan/XiangShan/issues/6264) | open | 2026-07-21 | ZhongYic00 | exception/trap, fault, trap CSR state | 6 | Sequential fetch fall-through across Sv39 canonical boundary does not raise instruction page fault |
| [#6262](https://github.com/OpenXiangShan/XiangShan/issues/6262) | open | 2026-07-21 | wndmll643 | exception/trap, fault, trap CSR state | 1 | XS bug-report — vlseg to unmapped page deadlocks (VSegmentUnit) |
| [#6259](https://github.com/OpenXiangShan/XiangShan/issues/6259) | open | 2026-07-21 | ZhongYic00 | exception/trap, fault, trap CSR state | 2 | Variant of HLV.WU ignores SPVP=VU effective privilege for final PMP checks found on NEMU |
| [#6229](https://github.com/OpenXiangShan/XiangShan/issues/6229) | open | 2026-07-13 | wndmll643 | fault, misalignment, trap CSR state | 6 | NewLoadUnit: misaligned load crossing a 16B (VWord) boundary misses store-to-load forwarding on the lower half and commits stale data |
| [#6227](https://github.com/OpenXiangShan/XiangShan/issues/6227) | open | 2026-07-12 | wndmll643 | exception/trap, fault, trap CSR state | 4 | Bare-mode trap zero-extends (truncates) trap-address CSRs (mepc/mtval/sepc/stval) in genTrapVA on kunminghu-v3 (refiled) |
| [#6215](https://github.com/OpenXiangShan/XiangShan/issues/6215) | open | 2026-07-09 | ruc-jty | fault | 1 | Pointer masking (PMLEN=16) not applied to G-stage translation input under vsatp=Bare (onlyStage2) + hgatp=Sv48×4 |
| [#6212](https://github.com/OpenXiangShan/XiangShan/issues/6212) | open | 2026-07-09 | jf-cc727 | exception/trap, fault, trap CSR state | 4 | VS-Stage PTW Guest-Page-Fault mtval2 Is Not Precise |
| [#6211](https://github.com/OpenXiangShan/XiangShan/issues/6211) | closed | 2026-07-09 | jf-cc727 | exception/trap, fault, misalignment, trap CSR state | 4 | Cross-Page Instruction Page Fault Reports Wrong mepc and mtval |
| [#6210](https://github.com/OpenXiangShan/XiangShan/issues/6210) | closed | 2026-07-09 | jf-cc727 | exception/trap, fault, trap CSR state | 3 | Sv39 Non-Canonical Instruction Fetch Reaches the Low Alias |
| [#6209](https://github.com/OpenXiangShan/XiangShan/issues/6209) | open | 2026-07-09 | jf-cc727 | exception/trap, fault, misalignment, trap CSR state | 4 | Split Load Tail Page Fault Does Not Retire |
| [#6199](https://github.com/OpenXiangShan/XiangShan/issues/6199) | open | 2026-07-06 | Security-HC | exception/trap, fault, trap CSR state | 2 | [Bug][PMP][2-core] S-mode store to L=1 locked PMP region does not raise store access fault |
| [#6182](https://github.com/OpenXiangShan/XiangShan/issues/6182) | open | 2026-07-02 | LeeHaofeng | interrupt/NMI | 2 | Stale interrupt taken after `mstatus.MIE`/`sstatus.SIE`/`vsstatus.SIE` is cleared |
| [#6168](https://github.com/OpenXiangShan/XiangShan/issues/6168) | open | 2026-06-29 | ruc-jty | debug/breakpoint, exception/trap, fault, trap CSR state | 3 | [Bug] debug watchpoint trigger lost on FOF non-first element |
| [#6162](https://github.com/OpenXiangShan/XiangShan/issues/6162) | closed | 2026-06-28 | Jacob-yen | exception/trap, fault, misalignment, trap CSR state | 4 | TOR partial match admits an aligned 8-byte store and commits a side effect |
| [#6161](https://github.com/OpenXiangShan/XiangShan/issues/6161) | closed | 2026-06-28 | Jacob-yen | exception/trap, fault, trap CSR state | 5 | PTW does not fault when a TOR entry covers only half of an 8-byte PTE |
| [#6158](https://github.com/OpenXiangShan/XiangShan/issues/6158) | open | 2026-06-27 | ScottaMcdonald | exception/trap | 1 | `prefetch.w` loses write intent and is admitted as a read prefetch on a read-only page |
| [#6156](https://github.com/OpenXiangShan/XiangShan/issues/6156) | open | 2026-06-27 | ScottaMcdonald | exception/trap, fault, trap CSR state | 4 | `satp` first-fetch page fault writes sign-extended mepc/mtval on kunminghu-v3 |
| [#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154) | open | 2026-06-26 | YzhDDDing | exception/trap | 2 | `NewLoadUnit` scalar loads can write back after redirect kill and expose wrong-path data on the RF write bus |
| [#6153](https://github.com/OpenXiangShan/XiangShan/issues/6153) | open | 2026-06-26 | YzhDDDing | debug/breakpoint, exception/trap, interrupt/NMI | 7 | `Load` breakpoint trigger does not kill S1 DCache lookup and can leave post-trap cache state |
| [#6150](https://github.com/OpenXiangShan/XiangShan/issues/6150) | open | 2026-06-26 | YzhDDDing | debug/breakpoint, exception/trap, illegal instruction | 6 | Illegal `DRET` outside Debug Mode emits stale xRET redirect and creates a secret-dependent ICache fetch oracle |
| [#6143](https://github.com/OpenXiangShan/XiangShan/issues/6143) | open | 2026-06-25 | Jason-Young123 | debug/breakpoint, exception/trap, trap CSR state | 2 | Newest REF (ready-to-run/riscv64-nemu-interpreter-so) misses an execute breakpoint (EX_BP) on pc-match, while Xiangshan and the previous REF trap correctly |
| [#6141](https://github.com/OpenXiangShan/XiangShan/issues/6141) | open | 2026-06-25 | Jason-Young123 | exception/trap, fault, misalignment, trap CSR state | 2 | Xiangshan and NEMU diverge between EX_LAM and EX_LAF on pure load cross-page MMIO access when only the second PMP region loses R permission |
| [#6139](https://github.com/OpenXiangShan/XiangShan/issues/6139) | open | 2026-06-25 | Jason-Young123 | exception/trap, fault, misalignment, trap CSR state | 2 | Xiangshan and NEMU diverge in mtval/tval on pure store cross-page MMIO access when only the second PMP region loses W permission |
| [#6126](https://github.com/OpenXiangShan/XiangShan/issues/6126) | open | 2026-06-24 | Jacob-yen | exception/trap, fault, trap CSR state | 3 | HLV.WU ignores SPVP=VU effective privilege for final PMP checks |
| [#6113](https://github.com/OpenXiangShan/XiangShan/issues/6113) | open | 2026-06-18 | ruc-jty | exception/trap, illegal instruction, trap CSR state | 3 | [Bug] VS-mode wrongly raises EX_II instead of EX_VI when mstateen0.AIA=0 and vsiselect ∈ 0x30-0x3F |
| [#6079](https://github.com/OpenXiangShan/XiangShan/issues/6079) | open | 2026-06-10 | lhb-sec | exception/trap, fault, interrupt/NMI, trap CSR state | 3 | `vl1re16.v` incorrectly computes `vstart` on Load Access Fault |
| [#6078](https://github.com/OpenXiangShan/XiangShan/issues/6078) | closed | 2026-06-09 | lhb-sec | exception/trap | 1 | `prefetch.i` hangs on invalid address |
| [#6068](https://github.com/OpenXiangShan/XiangShan/issues/6068) | open | 2026-06-08 | ruc-jty | exception/trap, interrupt/NMI | 2 | WFI does not resume execution when some interrupts become pending |
| [#6062](https://github.com/OpenXiangShan/XiangShan/issues/6062) | closed | 2026-06-07 | jimmymtest | exception/trap, fault, trap CSR state | 3 | S-mode cbo.clean to a PMP-denied block aborts StoreQueue instead of raising access fault |
| [#6060](https://github.com/OpenXiangShan/XiangShan/issues/6060) | open | 2026-06-05 | ruc-jty | exception/trap, fault, trap CSR state | 5 | [bug] XiangShan wrongly raises store access fault for cbo instruction on PBMT=NC memory |
| [#6057](https://github.com/OpenXiangShan/XiangShan/issues/6057) | open | 2026-06-05 | ra4ing | exception/trap, fault, illegal instruction, trap CSR state | 11 | Illegal-instruction `mtval` uses a younger instruction encoding instead of the faulting instruction |
| [#6042](https://github.com/OpenXiangShan/XiangShan/issues/6042) | open | 2026-05-29 | lhb-sec | exception/trap, fault, interrupt/NMI, trap CSR state | 5 | `vle16.v` fault handling incorrectly sets `vstart` to `vl` instead of the faulting element index |
| [#6039](https://github.com/OpenXiangShan/XiangShan/issues/6039) | open | 2026-05-28 | lhb-sec | exception/trap | 1 | `vsetvl` with `rd=zero` causes core hang |
| [#6038](https://github.com/OpenXiangShan/XiangShan/issues/6038) | closed | 2026-05-28 | LeeHaofeng | exception/trap, interrupt/NMI, trap CSR state | 2 | `NewCSR` Double-trap exception redirected to M-mode uses vectored interrupt offset instead of exception offset (`mtvec + 0`) |
| [#6037](https://github.com/OpenXiangShan/XiangShan/issues/6037) | closed | 2026-05-28 | nyh1 | exception/trap, illegal instruction, trap CSR state | 4 | [Bug] vmvnr_unaligned_regs |
| [#6036](https://github.com/OpenXiangShan/XiangShan/issues/6036) | closed | 2026-05-28 | nyh1 | exception/trap, illegal instruction, trap CSR state | 5 | [Bug] vmv1r_vstart_ge_evl |
| [#6035](https://github.com/OpenXiangShan/XiangShan/issues/6035) | open | 2026-05-28 | lhb-sec | exception/trap, fault, trap CSR state | 3 | `vle32ff.v` fails to update exception CSRs upon Load Access Fault |
| [#6032](https://github.com/OpenXiangShan/XiangShan/issues/6032) | closed | 2026-05-27 | ruc-jty | interrupt/NMI | 2 | [Bug] WFI does not resume execution when some interrupts become pending |
| [#6027](https://github.com/OpenXiangShan/XiangShan/issues/6027) | closed | 2026-05-26 | LeeHaofeng | interrupt/NMI | 1 | `onlyC3Enable` is always false |
| [#6023](https://github.com/OpenXiangShan/XiangShan/issues/6023) | closed | 2026-05-26 | ZhongYic00 | exception/trap, fault, trap CSR state | 8 | mtinst/htinst pseudo instruction missing bit 5 (store indication) for guest store page faults |
| [#6022](https://github.com/OpenXiangShan/XiangShan/issues/6022) | open | 2026-05-25 | lhb-sec | exception/trap, fault, trap CSR state | 3 | `vlse64.v` fails to trigger Load Access Fault upon illegal address access |
| [#6015](https://github.com/OpenXiangShan/XiangShan/issues/6015) | open | 2026-05-25 | lhb-sec | exception/trap, fault | 1 | Pipeline hangs on `vluxei32.v` when accessing unmapped physical addresses |
| [#6012](https://github.com/OpenXiangShan/XiangShan/issues/6012) | closed | 2026-05-22 | lhb-sec | exception/trap, fault | 2 | Reserved Hint (`ori rd=x0`) incorrectly triggers Load Access Fault (Cause 5) |
| [#6001](https://github.com/OpenXiangShan/XiangShan/issues/6001) | closed | 2026-05-21 | ruc-jty | interrupt/NMI | 3 | [BUG] SEI injected from M‑mode is encoded as priority 0 instead of 256 |
| [#5995](https://github.com/OpenXiangShan/XiangShan/issues/5995) | open | 2026-05-20 | biquanha | exception/trap, fault, trap CSR state | 6 | [BUG]kunminghu-v2 HLVX does not check final PMA/PMP execute permission |
| [#5958](https://github.com/OpenXiangShan/XiangShan/issues/5958) | open | 2026-05-14 | 0x1B05 | exception/trap, misalignment | 3 | Kunminghu-v2: LoadQueueReplay assertion on translated cross-page vector byte load/store |
| [#5943](https://github.com/OpenXiangShan/XiangShan/issues/5943) | open | 2026-05-11 | youzi27 | exception/trap, fault, trap CSR state | 5 | Incorrect `mtval` for faulting `vsse16.v` on kunminghu-v2 |
| [#5933](https://github.com/OpenXiangShan/XiangShan/issues/5933) | open | 2026-05-10 | KnightGOKU | exception/trap, illegal instruction, trap CSR state | 4 | `vlsseg3e64.v` loads wrong active destination data for zero-stride segment load |
| [#5932](https://github.com/OpenXiangShan/XiangShan/issues/5932) | open | 2026-05-10 | KnightGOKU | exception/trap, illegal instruction, trap CSR state | 4 | `vluxseg5ei32.v` corrupts active destination data for indexed segment load |
| [#5931](https://github.com/OpenXiangShan/XiangShan/issues/5931) | open | 2026-05-10 | KnightGOKU | fault, misalignment, trap CSR state | 4 | `vlseg2e32ff.v` loads wrong field0 data for misaligned fault-only-first segment load |
| [#5929](https://github.com/OpenXiangShan/XiangShan/issues/5929) | closed | 2026-05-10 | KnightGOKU | exception/trap, fault, trap CSR state | 7 | `vloxseg5ei64.v` misses load access fault and commits destination vector registers |
| [#5921](https://github.com/OpenXiangShan/XiangShan/issues/5921) | open | 2026-05-09 | youzi27 | exception/trap, fault, trap CSR state | 3 | Incorrect `vstart` update after faulting vector indexed store |
| [#5916](https://github.com/OpenXiangShan/XiangShan/issues/5916) | open | 2026-05-08 | youzi27 | exception/trap, fault, trap CSR state | 3 | Unexpected exception behavior for `prefetch.r` |
| [#5910](https://github.com/OpenXiangShan/XiangShan/issues/5910) | closed | 2026-05-07 | Jason-Young123 | exception/trap, misalignment, trap CSR state | 2 | Diff-test fails when EX_IAF occurs across 2 physical pages in 0x1000_0000 ~ 0x1fff_ffff, related to #5872 |
| [#5872](https://github.com/OpenXiangShan/XiangShan/issues/5872) | closed | 2026-04-28 | Jason-Young123 | exception/trap, fault, illegal instruction, trap CSR state | 2 | Diff-test fails when EX_IAF (Exception: InstrAccessFault) occurs across two physical pages (unexpected mtval and mcause) |
| [#5865](https://github.com/OpenXiangShan/XiangShan/issues/5865) | open | 2026-04-27 | KnightGOKU | exception/trap, illegal instruction, trap CSR state | 5 | XiangShan misses illegal-instruction trap for reserved masked `vmerge.vvm` with vd = v0 |
| [#5845](https://github.com/OpenXiangShan/XiangShan/issues/5845) | open | 2026-04-22 | jimmymtest | exception/trap, fault, illegal instruction, interrupt/NMI, trap CSR state | 2 | [Assertion Failure] XiangShan crashes in `LoadUnitS0` on a reduced `vmsbf.m -> vl1re64.v -> vlseg4e8.v` sequence |
| [#5809](https://github.com/OpenXiangShan/XiangShan/issues/5809) | closed | 2026-04-14 | KnightGOKU | exception/trap, illegal instruction, trap CSR state | 5 | `vmv.x.s` executes instead of raising illegal-instruction when `vsetvli` leaves `vtype.vill=1` |
| [#5808](https://github.com/OpenXiangShan/XiangShan/issues/5808) | open | 2026-04-14 | KnightGOKU | exception/trap, trap CSR state | 4 | `vstart` mismatch after a minimal `vsse16.v` RVV testcase |
| [#5807](https://github.com/OpenXiangShan/XiangShan/issues/5807) | open | 2026-04-14 | Lruos | debug/breakpoint, exception/trap, interrupt/NMI, misalignment, trap CSR state | 5 | [difftest] CSR mcause Exception Code Mismatch Between XiangShan RTL and NEMU When Exception Occurs |
| [#5792](https://github.com/OpenXiangShan/XiangShan/issues/5792) | closed | 2026-04-09 | maxiaoran24 | exception/trap | 1 | NewLoadUnit: matchInvalid/vp_match_fail path incorrectly preserves replay cause and creates a replay entry   alongside rollback |
| [#5790](https://github.com/OpenXiangShan/XiangShan/issues/5790) | closed | 2026-04-08 | zhangkanqi | exception/trap, fault, trap CSR state | 3 | Mismatch mcause and mtval when executing vssseg3e16.v |
| [#5780](https://github.com/OpenXiangShan/XiangShan/issues/5780) | closed | 2026-04-07 | zhangkanqi | exception/trap, fault, trap CSR state | 2 | `amominu.w` instruction behavior mismatch between Xiangshan and  NEMU |
| [#5777](https://github.com/OpenXiangShan/XiangShan/issues/5777) | closed | 2026-04-07 | zhangkanqi | exception/trap, fault, trap CSR state | 2 | No instructions have been submitted for a long time. Could this be a case of deadlock? |
| [#5773](https://github.com/OpenXiangShan/XiangShan/issues/5773) | open | 2026-04-06 | zhangkanqi | exception/trap, fault, misalignment, trap CSR state | 6 | Incorrect exception type raised when flh accessing addr=0x1 |
| [#5772](https://github.com/OpenXiangShan/XiangShan/issues/5772) | open | 2026-04-06 | zhangkanqi | exception/trap, illegal instruction, misalignment, trap CSR state | 3 | vmv4r.v with misaligned registers dosen't raise illegal instruction exception |
| [#5770](https://github.com/OpenXiangShan/XiangShan/issues/5770) | open | 2026-04-06 | zhangkanqi | exception/trap, fault, trap CSR state | 3 | Vector whole register load(vl2re32.v) partially updates destination on exception |
| [#5769](https://github.com/OpenXiangShan/XiangShan/issues/5769) | open | 2026-04-06 | zhangkanqi | debug/breakpoint, exception/trap, fault, misalignment, trap CSR state | 3 | Vector indexed segment store (vsuxseg*ei*) reports only base address in mtval on exception |
| [#5768](https://github.com/OpenXiangShan/XiangShan/issues/5768) | open | 2026-04-05 | Hoshi44 | exception/trap, illegal instruction | 1 | Vector FP move/merge instructions missing `frm` reserved value check |
| [#5767](https://github.com/OpenXiangShan/XiangShan/issues/5767) | open | 2026-04-05 | KnightGOKU | exception/trap, fault, trap CSR state | 3 | `vlseg2e8ff.v` with later-element fault triggers XiangShan internal critical error |
| [#5766](https://github.com/OpenXiangShan/XiangShan/issues/5766) | open | 2026-04-05 | KnightGOKU | exception/trap, fault, trap CSR state | 6 | `vle8ff` fault-only-first followed by immediate `csrr vl` returns 0 on XiangShan, while Spike returns the expected `vl` |
| [#5765](https://github.com/OpenXiangShan/XiangShan/issues/5765) | open | 2026-04-03 | KnightGOKU | interrupt/NMI, trap CSR state | 3 | Difftest mismatch on tail bits of v0 after vlm.v |
| [#5725](https://github.com/OpenXiangShan/XiangShan/issues/5725) | closed | 2026-03-25 | KnightGOKU | exception/trap, illegal instruction, misalignment, trap CSR state | 7 | Behavior mismatch on RVV testcase (`vsetvl x0, x0, rs2` path) |
| [#5724](https://github.com/OpenXiangShan/XiangShan/issues/5724) | closed | 2026-03-25 | oChunCai | debug/breakpoint, exception/trap, illegal instruction | 2 | 【Bug Report】Some CSRs is inaccessible incorrectly in Debug Mode |
| [#5721](https://github.com/OpenXiangShan/XiangShan/issues/5721) | closed | 2026-03-24 | oChunCai | debug/breakpoint | 1 | [Bug Report](Mcontrol6): Missing forward dmode check when writing mcontrol6.chain |
| [#5714](https://github.com/OpenXiangShan/XiangShan/issues/5714) | closed | 2026-03-23 | oChunCai | debug/breakpoint, exception/trap | 1 | [bug report](Trigger): triggerActionGen uses index-based priority instead of action-type priority when multiple triggers fire simultaneously |
| [#5713](https://github.com/OpenXiangShan/XiangShan/issues/5713) | closed | 2026-03-23 | oChunCai | debug/breakpoint | 1 | [Bug Report](Mcontrol6): Missing forward dmode check when writing mcontrol6.chain |
| [#5702](https://github.com/OpenXiangShan/XiangShan/issues/5702) | closed | 2026-03-19 | biquanha | exception/trap, fault, trap CSR state | 1 | Vector memory access |
| [#5695](https://github.com/OpenXiangShan/XiangShan/issues/5695) | closed | 2026-03-17 | oChunCai | exception/trap, fault, misalignment | 3 | Misaligned accesses to non-idempotent regions raise AddrMisaligned instead of AccessFault |
| [#5654](https://github.com/OpenXiangShan/XiangShan/issues/5654) | closed | 2026-03-06 | ambulGruidae | exception/trap | 1 | `ifuWbPtr` pointer not updated in FTQ |
| [#5628](https://github.com/OpenXiangShan/XiangShan/issues/5628) | closed | 2026-02-27 | oChunCai | exception/trap, illegal instruction | 2 | VU direct access to VS CSR reports EX_VI, expected EX_II (illegal-instruction) |
| [#5608](https://github.com/OpenXiangShan/XiangShan/issues/5608) | closed | 2026-02-06 | quange51 | exception/trap, trap CSR state | 1 | Compilation failed on the nanhu branch |
| [#5450](https://github.com/OpenXiangShan/XiangShan/issues/5450) | closed | 2025-12-27 | WHR-oss | exception/trap | 1 | Error when run “make verilog CONFIG=FpgaDefaultConfig” |
| [#5288](https://github.com/OpenXiangShan/XiangShan/issues/5288) | closed | 2025-12-01 | youzi27 | exception/trap, illegal instruction, trap CSR state | 3 | Unexpected interaction between vs1r.v and fence.i instructions |
| [#5282](https://github.com/OpenXiangShan/XiangShan/issues/5282) | closed | 2025-11-30 | youzi27 | exception/trap, illegal instruction, trap CSR state | 4 | Incorrect mtval in both reference models for specific illegal-instruction sequences |
| [#5279](https://github.com/OpenXiangShan/XiangShan/issues/5279) | open | 2025-11-29 | youzi27 | exception/trap, misalignment, trap CSR state | 5 | Mismatch in vector store commit behavior under misaligned base address |
| [#5257](https://github.com/OpenXiangShan/XiangShan/issues/5257) | closed | 2025-11-25 | BoA5li | fault, trap CSR state | 2 | Incorrect Instruction Page Fault When Executing From a Valid Sv39-Mapped Supervisor Page |
| [#5248](https://github.com/OpenXiangShan/XiangShan/issues/5248) | closed | 2025-11-24 | canxin121 | exception/trap, illegal instruction | 2 | CSR exception behavior differs |
| [#5137](https://github.com/OpenXiangShan/XiangShan/issues/5137) | open | 2025-10-22 | E1thannn | exception/trap, fault, trap CSR state | 2 | BUG when load word from illegal address |
| [#5129](https://github.com/OpenXiangShan/XiangShan/issues/5129) | closed | 2025-10-20 | MrCookieeeee | exception/trap | 2 | Difference between NEMU, XiangshanCore and SPIKE |
| [#5109](https://github.com/OpenXiangShan/XiangShan/issues/5109) | closed | 2025-10-14 | chenjie35335 | exception/trap, fault, illegal instruction, trap CSR state | 2 | The NEMU and Xiangshan reports different exception when executing a illegal instruction |
| [#5102](https://github.com/OpenXiangShan/XiangShan/issues/5102) | closed | 2025-10-10 | bantierr | interrupt/NMI, trap CSR state | 3 | [BUG] bug in local interrupt behaviour |
| [#4982](https://github.com/OpenXiangShan/XiangShan/issues/4982) | closed | 2025-08-27 | poemonsense | debug/breakpoint, exception/trap, fault, illegal instruction, misalignment, trap CSR state | 5 | [BOT] REFs report different exception causes at 0x80000000 |
| [#4981](https://github.com/OpenXiangShan/XiangShan/issues/4981) | closed | 2025-08-27 | poemonsense | exception/trap, fault, misalignment, trap CSR state | 5 | [BOT] DUT and REFs disagree on s10, mcause, mtval values. |
| [#4980](https://github.com/OpenXiangShan/XiangShan/issues/4980) | open | 2025-08-27 | poemonsense | exception/trap, interrupt/NMI, misalignment, trap CSR state | 5 | [BOT] mstatus/sstatus high bits set unexpectedly at exception entry |
| [#4952](https://github.com/OpenXiangShan/XiangShan/issues/4952) | closed | 2025-08-15 | timegoer | exception/trap, trap CSR state | 1 | Mismatch between Xiangshan and NEMU |
| [#4949](https://github.com/OpenXiangShan/XiangShan/issues/4949) | closed | 2025-08-14 | timegoer | exception/trap, trap CSR state | 1 | Mismatch at pc = 0x0080000c3c between Xiangshan and NEMU |
| [#4948](https://github.com/OpenXiangShan/XiangShan/issues/4948) | closed | 2025-08-14 | timegoer | exception/trap, trap CSR state | 2 | Mismatch at pc = 0x0080000344 between Xiangshan and NEMU |
| [#4864](https://github.com/OpenXiangShan/XiangShan/issues/4864) | closed | 2025-07-04 | ha0lyu | exception/trap, trap CSR state | 4 | `mcause` error when load data |
| [#4713](https://github.com/OpenXiangShan/XiangShan/issues/4713) | closed | 2025-05-20 | bantierr | exception/trap, interrupt/NMI | 1 | Mstatus.MIE not set properly |
| [#4689](https://github.com/OpenXiangShan/XiangShan/issues/4689) | closed | 2025-05-14 | LuLuji04 | exception/trap | 1 | XiangShan didn’t exit simulation in my test case |
| [#4682](https://github.com/OpenXiangShan/XiangShan/issues/4682) | closed | 2025-05-11 | LuLuji04 | exception/trap, fault, illegal instruction, trap CSR state | 2 | Fails to raise Instruction Access Fault on invalid PC |
| [#4668](https://github.com/OpenXiangShan/XiangShan/issues/4668) | closed | 2025-05-07 | LuLuji04 | exception/trap, fault, illegal instruction, trap CSR state | 2 | `mtval` Should Contain the `Illegal Instruction Encoding` on Illegal Instruction Exception |
| [#4667](https://github.com/OpenXiangShan/XiangShan/issues/4667) | closed | 2025-05-07 | LuLuji04 | exception/trap, fault, misalignment, trap CSR state | 2 | Mismatch in `mcause` for Unaligned `amomin.d` Access Between XiangShan and Spike |
| [#4666](https://github.com/OpenXiangShan/XiangShan/issues/4666) | closed | 2025-05-07 | LuLuji04 | interrupt/NMI | 1 | Mismatch in `mstatus.SIE` and `sstatus.SIE` After `sret` Instruction |
| [#4665](https://github.com/OpenXiangShan/XiangShan/issues/4665) | closed | 2025-05-07 | LuLuji04 | exception/trap, fault, trap CSR state | 2 | Spike Triggers Instruction Access Fault, But XiangShan Does Not |
| [#4664](https://github.com/OpenXiangShan/XiangShan/issues/4664) | closed | 2025-05-07 | LuLuji04 | exception/trap, fault, trap CSR state | 2 | Mismatch in `mcause` Between XiangShan and Spike After `lw` |
| [#4576](https://github.com/OpenXiangShan/XiangShan/issues/4576) | closed | 2025-04-16 | LuLuji04 | exception/trap, illegal instruction | 1 | `unimp` After Returning from `ebreak` and Continuing Instructions Trigger Mismatch |
| [#4504](https://github.com/OpenXiangShan/XiangShan/issues/4504) | closed | 2025-04-04 | JacyCui | exception/trap | 2 | ICacheMissEntry Assertion Violable |
| [#4398](https://github.com/OpenXiangShan/XiangShan/issues/4398) | closed | 2025-03-11 | ha0lyu | exception/trap, illegal instruction | 1 | XiangShan and NEMU show inconsistencies when executing `amoswap.w` |
| [#4387](https://github.com/OpenXiangShan/XiangShan/issues/4387) | closed | 2025-03-10 | ha0lyu | exception/trap, fault, trap CSR state | 2 | Xiangshan does not throw IAF trap, but NEMU throw error `mtval` |
| [#4386](https://github.com/OpenXiangShan/XiangShan/issues/4386) | closed | 2025-03-10 | ha0lyu | exception/trap | 2 | Xiangshan does not throw IAF trap |
| [#4320](https://github.com/OpenXiangShan/XiangShan/issues/4320) | closed | 2025-02-26 | leesum1 | debug/breakpoint | 2 | Unexpected PC Breakpoint Triggering in IFU's FrontendTrigger with 'Less Than' Mode |
| [#4047](https://github.com/OpenXiangShan/XiangShan/issues/4047) | closed | 2024-12-15 | fly-1011 | exception/trap, fault, trap CSR state | 2 | Load access fault exception related issue |
| [#4042](https://github.com/OpenXiangShan/XiangShan/issues/4042) | closed | 2024-12-15 | fly-1011 | debug/breakpoint, exception/trap, fault, misalignment, trap CSR state | 2 | The Value of the mtval Register Differs When the Address is Misaligned |
| [#4020](https://github.com/OpenXiangShan/XiangShan/issues/4020) | closed | 2024-12-10 | fly-1011 | exception/trap, illegal instruction | 2 | Certain instructions cannot cause exceptions |
| [#3959](https://github.com/OpenXiangShan/XiangShan/issues/3959) | closed | 2024-11-29 | youzi27 | exception/trap, illegal instruction | 2 | Unable to Handle Specific Sequences of Illegal Instructions |
| [#3937](https://github.com/OpenXiangShan/XiangShan/issues/3937) | closed | 2024-11-26 | youzi27 | interrupt/NMI | 1 | `mip.STIP` Not Set When `stimecmp` is Less Than `time` |
| [#3919](https://github.com/OpenXiangShan/XiangShan/issues/3919) | closed | 2024-11-23 | MAX-max1118 | exception/trap | 1 | Decode related issue |
| [#3878](https://github.com/OpenXiangShan/XiangShan/issues/3878) | closed | 2024-11-16 | ha0lyu | exception/trap, trap CSR state | 5 | `mcause` is different between xiangshan and spike when execute `sh`. |
| [#3860](https://github.com/OpenXiangShan/XiangShan/issues/3860) | closed | 2024-11-11 | ha0lyu | illegal instruction, trap CSR state | 3 | Wrong `mstatus, mtval` value when Xiangshan executes an illegal instruction. |
| [#3856](https://github.com/OpenXiangShan/XiangShan/issues/3856) | closed | 2024-11-11 | MAX-max1118 | interrupt/NMI | 2 | There is a problem with the SEIP bit handling in the sip register |
| [#3844](https://github.com/OpenXiangShan/XiangShan/issues/3844) | closed | 2024-11-07 | fly-1011 | exception/trap, fault, misalignment, trap CSR state | 2 | Handling Inconsistency in Load Address Misaligned and Load Access Fault Exceptions for Specific Instructions |
| [#3839](https://github.com/OpenXiangShan/XiangShan/issues/3839) | closed | 2024-11-06 | fly-1011 | exception/trap, illegal instruction | 2 | When the fs field in the mstatus register is 0, executing instructions such as flh will not cause an illegal instruction exception |
| [#3830](https://github.com/OpenXiangShan/XiangShan/issues/3830) | closed | 2024-11-04 | chenhychen | exception/trap, fault, misalignment | 2 | [Bug Report] Load access fault and store_address_misaligned cause processor to deadlock |
| [#3829](https://github.com/OpenXiangShan/XiangShan/issues/3829) | closed | 2024-11-02 | ha0lyu | exception/trap, misalignment, trap CSR state | 3 | `mcause` error in difftest when `raise intr cause NO: 4` |
| [#3813](https://github.com/OpenXiangShan/XiangShan/issues/3813) | closed | 2024-10-30 | ha0lyu | exception/trap, fault, misalignment | 2 | Exception priority mismatch between xiangshan and spike |
| [#3012](https://github.com/OpenXiangShan/XiangShan/issues/3012) | closed | 2024-05-27 | cyyself | exception/trap, misalignment | 3 | Difftest failed on a RISC-V Vector memcpy workload with misaligned(in vlen granularity, not element) unit stride load |
