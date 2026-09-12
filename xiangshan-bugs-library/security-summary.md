# Security Bug Summary

- Source: `/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-bugs-library/xiangshan-bugs-src`
- Generated at: `2026-09-12T09:31:22+00:00`
- Matching scope: metadata, descriptions, and text reproducers; commit logs excluded by default
- Corpus: **265** issues
- Security-related matches: **75** issues

## Classification Rules

An item is counted when its metadata, description, or text reproducer contains an explicit security label/impact, a concrete access-protection or speculative-execution security signal, or side-channel evidence. Side-channel matches are reported as a security subset and may overlap with other security groups. Generic occurrences of words such as `cache`, `secret`, or `security` alone are not sufficient.

## Match Type Counts

| Match type | Matches |
|---|---:|
| security | 73 |
| side-channel | 10 |

## Keyword Group Counts

| Group | Matches |
|---|---:|
| memory/access protection | 57 |
| explicit security impact | 11 |
| speculation/wrong path | 10 |
| timing channel | 8 |
| cache/predictor channel | 7 |
| security label | 3 |
| physical leakage | 1 |

## State Counts

| Value | Matches |
|---|---:|
| open | 43 |
| closed | 32 |

## Year Counts

| Value | Matches |
|---|---:|
| 2026 | 57 |
| 2025 | 16 |
| 2024 | 1 |
| 2023 | 1 |

## Author Counts

| Value | Matches |
|---|---:|
| YzhDDDing | 14 |
| lhb-sec | 10 |
| zhangkanqi | 7 |
| youzi27 | 6 |
| Jason-Young123 | 4 |
| ruc-jty | 3 |
| Jacob-yen | 3 |
| poemonsense | 3 |
| Security-HC | 2 |
| ScottaMcdonald | 2 |
| KnightGOKU | 2 |
| chenjie35335 | 2 |
| timegoer | 2 |
| LeeHaofeng | 1 |
| ZhongYic00 | 1 |
| jimmymtest | 1 |
| biquanha | 1 |
| 0x1B05 | 1 |
| Hoshi44 | 1 |
| mmxsrup | 1 |
| YAM2020er | 1 |
| cesarus777 | 1 |
| E1thannn | 1 |
| MrCookieeeee | 1 |
| bantierr | 1 |
| LuLuji04 | 1 |
| ha0lyu | 1 |
| nieeka | 1 |

## Matched Issues

| Issue | State | Created | Author | Match types | Groups | Evidence files | Title |
|---:|---|---|---|---|---|---:|---|
| [#6343](https://github.com/OpenXiangShan/XiangShan/issues/6343) | open | 2026-08-11 | LeeHaofeng | security | memory/access protection | 1 | AtomicsUnit retains stale `hardwareError` across atomic operations |
| [#6296](https://github.com/OpenXiangShan/XiangShan/issues/6296) | open | 2026-07-28 | lhb-sec | security | memory/access protection | 1 | Vector store reports wrong `mcause` in M-mode |
| [#6295](https://github.com/OpenXiangShan/XiangShan/issues/6295) | open | 2026-07-28 | lhb-sec | security | memory/access protection | 1 | `vle32ff.v` corrupts destination vector register on Load Access Fault |
| [#6293](https://github.com/OpenXiangShan/XiangShan/issues/6293) | open | 2026-07-28 | lhb-sec | security | memory/access protection | 2 | `vle64ff.v` misses Load Access Fault on misaligned access to I/O PMA region |
| [#6288](https://github.com/OpenXiangShan/XiangShan/issues/6288) | open | 2026-07-27 | lhb-sec | security | memory/access protection | 1 | `sw` instruction reports wrong `mcause` in M-mode |
| [#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267) | open | 2026-07-21 | lhb-sec | security | memory/access protection | 2 | `fsw fa1,716(t6)` to PMA-missing address `0x2cc` causes StoreUnit deadlock |
| [#6265](https://github.com/OpenXiangShan/XiangShan/issues/6265) | open | 2026-07-21 | lhb-sec | security | memory/access protection | 1 | Behavior mismatch on `ld s8,0xf0(t6)` to misaligned address `0xf1` |
| [#6259](https://github.com/OpenXiangShan/XiangShan/issues/6259) | open | 2026-07-21 | ZhongYic00 | security | memory/access protection | 2 | Variant of HLV.WU ignores SPVP=VU effective privilege for final PMP checks found on NEMU |
| [#6215](https://github.com/OpenXiangShan/XiangShan/issues/6215) | open | 2026-07-09 | ruc-jty | security | memory/access protection | 2 | Pointer masking (PMLEN=16) not applied to G-stage translation input under vsatp=Bare (onlyStage2) + hgatp=Sv48×4 |
| [#6214](https://github.com/OpenXiangShan/XiangShan/issues/6214) | open | 2026-07-09 | ruc-jty | security | memory/access protection | 2 | Pointer masking not applied to debug address-trigger comparison (MemTrigger compares raw vaddr) |
| [#6199](https://github.com/OpenXiangShan/XiangShan/issues/6199) | open | 2026-07-06 | Security-HC | security | memory/access protection, security label | 3 | [Bug][PMP][2-core] S-mode store to L=1 locked PMP region does not raise store access fault |
| [#6165](https://github.com/OpenXiangShan/XiangShan/issues/6165) | closed | 2026-06-28 | YzhDDDing | side-channel, security | cache/predictor channel, explicit security impact, speculation/wrong path, timing channel | 1 | `TAGE` can expose a new entry with a stale useful counter to FTQ/train metadata |
| [#6162](https://github.com/OpenXiangShan/XiangShan/issues/6162) | closed | 2026-06-28 | Jacob-yen | security | memory/access protection | 1 | TOR partial match admits an aligned 8-byte store and commits a side effect |
| [#6161](https://github.com/OpenXiangShan/XiangShan/issues/6161) | closed | 2026-06-28 | Jacob-yen | security | memory/access protection | 1 | PTW does not fault when a TOR entry covers only half of an 8-byte PTE |
| [#6160](https://github.com/OpenXiangShan/XiangShan/issues/6160) | closed | 2026-06-27 | ScottaMcdonald | side-channel, security | cache/predictor channel, speculation/wrong path | 1 | ITTAGE `altDiffers` asserts with no alternate provider and mistrains useful counter |
| [#6158](https://github.com/OpenXiangShan/XiangShan/issues/6158) | open | 2026-06-27 | ScottaMcdonald | security | memory/access protection | 1 | `prefetch.w` loses write intent and is admitted as a read prefetch on a read-only page |
| [#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154) | open | 2026-06-26 | YzhDDDing | side-channel, security | explicit security impact, physical leakage, speculation/wrong path, timing channel | 3 | `NewLoadUnit` scalar loads can write back after redirect kill and expose wrong-path data on the RF write bus |
| [#6153](https://github.com/OpenXiangShan/XiangShan/issues/6153) | open | 2026-06-26 | YzhDDDing | side-channel, security | cache/predictor channel, explicit security impact | 1 | `Load` breakpoint trigger does not kill S1 DCache lookup and can leave post-trap cache state |
| [#6152](https://github.com/OpenXiangShan/XiangShan/issues/6152) | open | 2026-06-26 | Security-HC | security | security label | 1 | [RVV] vzext.vf4 / vsext.vf4 writes zero to high 64-bit (elements 2..3) |
| [#6150](https://github.com/OpenXiangShan/XiangShan/issues/6150) | open | 2026-06-26 | YzhDDDing | side-channel, security | cache/predictor channel, explicit security impact, speculation/wrong path, timing channel | 3 | Illegal `DRET` outside Debug Mode emits stale xRET redirect and creates a secret-dependent ICache fetch oracle |
| [#6149](https://github.com/OpenXiangShan/XiangShan/issues/6149) | open | 2026-06-26 | YzhDDDing | security | explicit security impact, speculation/wrong path | 1 | `sbpctl.RAS_ENABLE` does not disable RAS/URAS and allows secret-dependent wrong-path fetches |
| [#6148](https://github.com/OpenXiangShan/XiangShan/issues/6148) | open | 2026-06-26 | YzhDDDing | side-channel, security | cache/predictor channel, explicit security impact, timing channel | 3 | `ITTAGE` is accessed by direct-only fetch blocks, creating a software-visible timing channel |
| [#6141](https://github.com/OpenXiangShan/XiangShan/issues/6141) | open | 2026-06-25 | Jason-Young123 | security | memory/access protection | 2 | Xiangshan and NEMU diverge between EX_LAM and EX_LAF on pure load cross-page MMIO access when only the second PMP region loses R permission |
| [#6139](https://github.com/OpenXiangShan/XiangShan/issues/6139) | open | 2026-06-25 | Jason-Young123 | security | memory/access protection | 2 | Xiangshan and NEMU diverge in mtval/tval on pure store cross-page MMIO access when only the second PMP region loses W permission |
| [#6138](https://github.com/OpenXiangShan/XiangShan/issues/6138) | open | 2026-06-25 | YzhDDDing | side-channel, security | cache/predictor channel, explicit security impact, timing channel | 3 | Same-FTQ wrong-path branch can train BPU and leak one bit through timing |
| [#6137](https://github.com/OpenXiangShan/XiangShan/issues/6137) | open | 2026-06-25 | YzhDDDing | side-channel, security | cache/predictor channel, explicit security impact, speculation/wrong path, timing channel | 3 | `ABTB` can emit stale predictions after `sbpctl` disables ABTB and drive transient I/D-cache accesses |
| [#6135](https://github.com/OpenXiangShan/XiangShan/issues/6135) | open | 2026-06-25 | YzhDDDing | side-channel | timing channel | 3 | RAS disable control is ignored for return prediction and leaves a secret-dependent timing side channel |
| [#6134](https://github.com/OpenXiangShan/XiangShan/issues/6134) | closed | 2026-06-25 | YzhDDDing | security | explicit security impact, memory/access protection | 1 | `PBMT-IO` instruction fetch skips `IFU` uncache last-commit serialization |
| [#6133](https://github.com/OpenXiangShan/XiangShan/issues/6133) | open | 2026-06-25 | YzhDDDing | security | explicit security impact, speculation/wrong path | 1 | `LoadQueueUncache` full-buffer rollback can carry accepted MMIO load metadata |
| [#6126](https://github.com/OpenXiangShan/XiangShan/issues/6126) | open | 2026-06-24 | Jacob-yen | security | memory/access protection | 2 | HLV.WU ignores SPVP=VU effective privilege for final PMP checks |
| [#6078](https://github.com/OpenXiangShan/XiangShan/issues/6078) | closed | 2026-06-09 | lhb-sec | security | memory/access protection | 1 | `prefetch.i` hangs on invalid address |
| [#6062](https://github.com/OpenXiangShan/XiangShan/issues/6062) | closed | 2026-06-07 | jimmymtest | security | memory/access protection | 2 | S-mode cbo.clean to a PMP-denied block aborts StoreQueue instead of raising access fault |
| [#6060](https://github.com/OpenXiangShan/XiangShan/issues/6060) | open | 2026-06-05 | ruc-jty | security | memory/access protection | 1 | [bug] XiangShan wrongly raises store access fault for cbo instruction on PBMT=NC memory |
| [#6042](https://github.com/OpenXiangShan/XiangShan/issues/6042) | open | 2026-05-29 | lhb-sec | security | memory/access protection | 1 | `vle16.v` fault handling incorrectly sets `vstart` to `vl` instead of the faulting element index |
| [#6035](https://github.com/OpenXiangShan/XiangShan/issues/6035) | open | 2026-05-28 | lhb-sec | security | memory/access protection | 1 | `vle32ff.v` fails to update exception CSRs upon Load Access Fault |
| [#6022](https://github.com/OpenXiangShan/XiangShan/issues/6022) | open | 2026-05-25 | lhb-sec | security | memory/access protection | 1 | `vlse64.v` fails to trigger Load Access Fault upon illegal address access |
| [#6002](https://github.com/OpenXiangShan/XiangShan/issues/6002) | open | 2026-05-22 | YzhDDDing | security | speculation/wrong path | 1 | Same-page cross-16B store-load OctaWord nuke mask is not shifted for upper VWord |
| [#5998](https://github.com/OpenXiangShan/XiangShan/issues/5998) | closed | 2026-05-21 | YzhDDDing | security | speculation/wrong path | 1 | StoreQueue cross-16B multi-match partial forward is treated as safe full overlap |
| [#5995](https://github.com/OpenXiangShan/XiangShan/issues/5995) | open | 2026-05-20 | biquanha | security | memory/access protection | 2 | [BUG]kunminghu-v2 HLVX does not check final PMA/PMP execute permission |
| [#5988](https://github.com/OpenXiangShan/XiangShan/issues/5988) | closed | 2026-05-19 | YzhDDDing | side-channel | timing channel | 1 | `MainBTB` replacement state aliases across different physical sets |
| [#5958](https://github.com/OpenXiangShan/XiangShan/issues/5958) | open | 2026-05-14 | 0x1B05 | security | memory/access protection | 1 | Kunminghu-v2: LoadQueueReplay assertion on translated cross-page vector byte load/store |
| [#5943](https://github.com/OpenXiangShan/XiangShan/issues/5943) | open | 2026-05-11 | youzi27 | security | memory/access protection | 1 | Incorrect `mtval` for faulting `vsse16.v` on kunminghu-v2 |
| [#5921](https://github.com/OpenXiangShan/XiangShan/issues/5921) | open | 2026-05-09 | youzi27 | security | memory/access protection | 1 | Incorrect `vstart` update after faulting vector indexed store |
| [#5910](https://github.com/OpenXiangShan/XiangShan/issues/5910) | closed | 2026-05-07 | Jason-Young123 | security | memory/access protection | 1 | Diff-test fails when EX_IAF occurs across 2 physical pages in 0x1000_0000 ~ 0x1fff_ffff, related to #5872 |
| [#5872](https://github.com/OpenXiangShan/XiangShan/issues/5872) | closed | 2026-04-28 | Jason-Young123 | security | memory/access protection | 1 | Diff-test fails when EX_IAF (Exception: InstrAccessFault) occurs across two physical pages (unexpected mtval and mcause) |
| [#5854](https://github.com/OpenXiangShan/XiangShan/issues/5854) | closed | 2026-04-23 | Hoshi44 | security | explicit security impact | 1 | [BUG] L2TLB: cfs indexed with wrong truncated PPN, defeats bitmap isolation |
| [#5851](https://github.com/OpenXiangShan/XiangShan/issues/5851) | closed | 2026-04-23 | mmxsrup | security | speculation/wrong path | 1 | [BUG] Cross-page misaligned sd followed by alias lbu reads stale data |
| [#5790](https://github.com/OpenXiangShan/XiangShan/issues/5790) | closed | 2026-04-08 | zhangkanqi | security | memory/access protection | 1 | Mismatch mcause and mtval when executing vssseg3e16.v |
| [#5780](https://github.com/OpenXiangShan/XiangShan/issues/5780) | closed | 2026-04-07 | zhangkanqi | security | memory/access protection | 1 | `amominu.w` instruction behavior mismatch between Xiangshan and  NEMU |
| [#5777](https://github.com/OpenXiangShan/XiangShan/issues/5777) | closed | 2026-04-07 | zhangkanqi | security | memory/access protection | 1 | No instructions have been submitted for a long time. Could this be a case of deadlock? |
| [#5773](https://github.com/OpenXiangShan/XiangShan/issues/5773) | open | 2026-04-06 | zhangkanqi | security | memory/access protection | 1 | Incorrect exception type raised when flh accessing addr=0x1 |
| [#5772](https://github.com/OpenXiangShan/XiangShan/issues/5772) | open | 2026-04-06 | zhangkanqi | security | memory/access protection | 1 | vmv4r.v with misaligned registers dosen't raise illegal instruction exception |
| [#5770](https://github.com/OpenXiangShan/XiangShan/issues/5770) | open | 2026-04-06 | zhangkanqi | security | memory/access protection | 1 | Vector whole register load(vl2re32.v) partially updates destination on exception |
| [#5769](https://github.com/OpenXiangShan/XiangShan/issues/5769) | open | 2026-04-06 | zhangkanqi | security | memory/access protection | 1 | Vector indexed segment store (vsuxseg*ei*) reports only base address in mtval on exception |
| [#5767](https://github.com/OpenXiangShan/XiangShan/issues/5767) | open | 2026-04-05 | KnightGOKU | security | memory/access protection | 1 | `vlseg2e8ff.v` with later-element fault triggers XiangShan internal critical error |
| [#5766](https://github.com/OpenXiangShan/XiangShan/issues/5766) | open | 2026-04-05 | KnightGOKU | security | memory/access protection | 1 | `vle8ff` fault-only-first followed by immediate `csrr vl` returns 0 on XiangShan, while Spike returns the expected `vl` |
| [#5689](https://github.com/OpenXiangShan/XiangShan/issues/5689) | closed | 2026-03-14 | YAM2020er | security | memory/access protection | 1 | Illegal address access |
| [#5426](https://github.com/OpenXiangShan/XiangShan/issues/5426) | closed | 2025-12-24 | cesarus777 | security | memory/access protection | 1 | v31_low different on vfredusum.vs |
| [#5288](https://github.com/OpenXiangShan/XiangShan/issues/5288) | closed | 2025-12-01 | youzi27 | security | memory/access protection | 1 | Unexpected interaction between vs1r.v and fence.i instructions |
| [#5282](https://github.com/OpenXiangShan/XiangShan/issues/5282) | closed | 2025-11-30 | youzi27 | security | memory/access protection | 1 | Incorrect mtval in both reference models for specific illegal-instruction sequences |
| [#5279](https://github.com/OpenXiangShan/XiangShan/issues/5279) | open | 2025-11-29 | youzi27 | security | memory/access protection | 1 | Mismatch in vector store commit behavior under misaligned base address |
| [#5168](https://github.com/OpenXiangShan/XiangShan/issues/5168) | closed | 2025-11-03 | chenjie35335 | security | memory/access protection | 1 | NEMU and Xiangshan generate unmatched MIE value. |
| [#5137](https://github.com/OpenXiangShan/XiangShan/issues/5137) | open | 2025-10-22 | E1thannn | security | memory/access protection | 1 | BUG when load word from illegal address |
| [#5129](https://github.com/OpenXiangShan/XiangShan/issues/5129) | closed | 2025-10-20 | MrCookieeeee | security | memory/access protection | 1 | Difference between NEMU, XiangshanCore and SPIKE |
| [#5109](https://github.com/OpenXiangShan/XiangShan/issues/5109) | closed | 2025-10-14 | chenjie35335 | security | memory/access protection | 1 | The NEMU and Xiangshan reports different exception when executing a illegal instruction |
| [#5102](https://github.com/OpenXiangShan/XiangShan/issues/5102) | closed | 2025-10-10 | bantierr | security | memory/access protection | 1 | [BUG] bug in local interrupt behaviour |
| [#4982](https://github.com/OpenXiangShan/XiangShan/issues/4982) | closed | 2025-08-27 | poemonsense | security | memory/access protection | 1 | [BOT] REFs report different exception causes at 0x80000000 |
| [#4981](https://github.com/OpenXiangShan/XiangShan/issues/4981) | closed | 2025-08-27 | poemonsense | security | memory/access protection | 1 | [BOT] DUT and REFs disagree on s10, mcause, mtval values. |
| [#4980](https://github.com/OpenXiangShan/XiangShan/issues/4980) | open | 2025-08-27 | poemonsense | security | memory/access protection | 1 | [BOT] mstatus/sstatus high bits set unexpectedly at exception entry |
| [#4952](https://github.com/OpenXiangShan/XiangShan/issues/4952) | closed | 2025-08-15 | timegoer | security | memory/access protection | 1 | Mismatch between Xiangshan and NEMU |
| [#4949](https://github.com/OpenXiangShan/XiangShan/issues/4949) | closed | 2025-08-14 | timegoer | security | memory/access protection | 1 | Mismatch at pc = 0x0080000c3c between Xiangshan and NEMU |
| [#4665](https://github.com/OpenXiangShan/XiangShan/issues/4665) | closed | 2025-05-07 | LuLuji04 | security | memory/access protection | 1 | Spike Triggers Instruction Access Fault, But XiangShan Does Not |
| [#4120](https://github.com/OpenXiangShan/XiangShan/issues/4120) | closed | 2025-01-02 | ha0lyu | security | memory/access protection | 2 | Inconsistency behavior between xiangshan and NEMU after setting PMP |
| [#3927](https://github.com/OpenXiangShan/XiangShan/issues/3927) | closed | 2024-11-25 | youzi27 | security | memory/access protection | 1 | Mismatch in fld Instruction Execution Between XS and REF |
| [#2534](https://github.com/OpenXiangShan/XiangShan/issues/2534) | closed | 2023-12-07 | nieeka | security | security label | 1 | L1D Cache Side-channal on Nanhu |
