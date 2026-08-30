# Micro-Architecture Bug Summary

- Source: `/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-bugs-library/xiangshan-bugs-src`
- Generated at: `2026-08-29T14:56:20+00:00`
- Micro-architecture caused issues: **122**
- Format: Markdown table

## Keyword Group Counts

| Group | Matches |
|---|---:|
| memory/protection | 92 |
| pipeline/control | 45 |
| pipeline stage | 32 |
| forward progress | 22 |
| queue/buffer | 20 |
| data correctness | 19 |
| root-cause note | 15 |
| prediction | 14 |

## State Counts

| State | Matches |
|---|---:|
| open | 62 |
| closed | 60 |

## Author Counts

| Author | Matches |
|---|---:|
| YzhDDDing | 14 |
| LeeHaofeng | 12 |
| lhb-sec | 7 |
| ruc-jty | 7 |
| ScottaMcdonald | 7 |
| zhangkanqi | 7 |
| KnightGOKU | 5 |
| mmxsrup | 5 |
| jf-cc727 | 4 |
| Jason-Young123 | 4 |
| jimmymtest | 4 |
| oChunCai | 4 |
| DFPMTS | 3 |
| Hoshi44 | 3 |
| LuLuji04 | 3 |
| youzi27 | 2 |
| bantierr | 2 |
| poemonsense | 2 |
| ha0lyu | 2 |
| wndmll643 | 1 |
| Security-HC | 1 |
| Jacob-yen | 1 |
| ra4ing | 1 |
| Wowblk | 1 |
| biquanha | 1 |
| wang02020119 | 1 |
| 0x1B05 | 1 |
| maxiaoran24 | 1 |
| YAM2020er | 1 |
| quange51 | 1 |
| WHR-oss | 1 |
| cesarus777 | 1 |
| BoA5li | 1 |
| canxin121 | 1 |
| E1thannn | 1 |
| MrCookieeeee | 1 |
| timegoer | 1 |
| BaoBao-zhu | 1 |
| sasakiakaya | 1 |
| cyyself | 1 |
| camel-cdr | 1 |
| euphgh | 1 |
| menglinhan | 1 |
| nieeka | 1 |

## Year Counts

| Year | Matches |
|---|---:|
| 2026 | 99 |
| 2025 | 16 |
| 2024 | 6 |
| 2023 | 1 |

## Matched Issues

| Issue | State | Created | Author | Module | Groups | Evidence files | Title |
|---:|---|---|---|---|---|---:|---|
| [#6343](https://github.com/OpenXiangShan/XiangShan/issues/6343) | open | 2026-08-11 | LeeHaofeng | Backend/ROB, Backend/Execution, Memory/LSU, Memory/Cache, Memory/MMU | pipeline/control, memory/protection | 3 | AtomicsUnit retains stale `hardwareError` across atomic operations |
| [#6318](https://github.com/OpenXiangShan/XiangShan/issues/6318) | open | 2026-08-01 | DFPMTS | Memory/LSU, Memory/MMU | forward progress, queue/buffer, memory/protection | 2 | Cross-page misaligned store can commit an untranslated tail address after a DTLB miss |
| [#6303](https://github.com/OpenXiangShan/XiangShan/issues/6303) | open | 2026-07-29 | lhb-sec | Frontend/IFU, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline/control, forward progress, queue/buffer, memory/protection, root-cause note | 4 | Nested exception in M-mode causes subsequent exception to deadlock |
| [#6302](https://github.com/OpenXiangShan/XiangShan/issues/6302) | open | 2026-07-28 | lhb-sec | Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline/control, memory/protection, root-cause note | 2 | XiangShan does not trap on vector instruction with reserved vsew encoding |
| [#6296](https://github.com/OpenXiangShan/XiangShan/issues/6296) | open | 2026-07-28 | lhb-sec | Backend/CSR/Trap, Backend/Execution, Memory/LSU, Memory/MMU | pipeline/control, memory/protection | 2 | Vector store reports wrong `mcause` in M-mode |
| [#6295](https://github.com/OpenXiangShan/XiangShan/issues/6295) | open | 2026-07-28 | lhb-sec | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 3 | `vle32ff.v` corrupts destination vector register on Load Access Fault |
| [#6293](https://github.com/OpenXiangShan/XiangShan/issues/6293) | open | 2026-07-28 | lhb-sec | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 3 | `vle64ff.v` misses Load Access Fault on misaligned access to I/O PMA region |
| [#6276](https://github.com/OpenXiangShan/XiangShan/issues/6276) | closed | 2026-07-23 | LeeHaofeng | Backend/CSR/Trap, Interconnect/SoC | memory/protection | 1 | IMSIC: VS-mode claim (`vstopei` EOI) is broadcast to all guest interrupt files instead of the `vgein`-selected one, causing silent MSI loss |
| [#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267) | open | 2026-07-21 | lhb-sec | Frontend/BPU, Frontend/IFU, Backend/CSR/Trap, Backend/Execution, Memory/LSU, Memory/MMU | pipeline/control, forward progress, memory/protection | 4 | `fsw fa1,716(t6)` to PMA-missing address `0x2cc` causes StoreUnit deadlock |
| [#6227](https://github.com/OpenXiangShan/XiangShan/issues/6227) | open | 2026-07-12 | wndmll643 | Frontend/IFU, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline stage, memory/protection, data correctness | 2 | Bare-mode trap zero-extends (truncates) trap-address CSRs (mepc/mtval/sepc/stval) in genTrapVA on kunminghu-v3 (refiled) |
| [#6215](https://github.com/OpenXiangShan/XiangShan/issues/6215) | open | 2026-07-09 | ruc-jty | Memory/MMU | pipeline/control, memory/protection, data correctness | 2 | Pointer masking (PMLEN=16) not applied to G-stage translation input under vsatp=Bare (onlyStage2) + hgatp=Sv48×4 |
| [#6214](https://github.com/OpenXiangShan/XiangShan/issues/6214) | open | 2026-07-09 | ruc-jty | Memory/LSU, Memory/MMU | memory/protection | 2 | Pointer masking not applied to debug address-trigger comparison (MemTrigger compares raw vaddr) |
| [#6212](https://github.com/OpenXiangShan/XiangShan/issues/6212) | open | 2026-07-09 | jf-cc727 | Frontend/IFU, Backend/CSR/Trap, Memory/MMU | forward progress, pipeline stage, memory/protection | 2 | VS-Stage PTW Guest-Page-Fault mtval2 Is Not Precise |
| [#6211](https://github.com/OpenXiangShan/XiangShan/issues/6211) | closed | 2026-07-09 | jf-cc727 | frontend, Frontend/IFU, Backend/CSR/Trap | forward progress, queue/buffer, pipeline stage, memory/protection | 2 | Cross-Page Instruction Page Fault Reports Wrong mepc and mtval |
| [#6210](https://github.com/OpenXiangShan/XiangShan/issues/6210) | closed | 2026-07-09 | jf-cc727 | frontend, Frontend/IFU, Backend/CSR/Trap, Memory/MMU | forward progress, pipeline stage, memory/protection, data correctness | 2 | Sv39 Non-Canonical Instruction Fetch Reaches the Low Alias |
| [#6209](https://github.com/OpenXiangShan/XiangShan/issues/6209) | open | 2026-07-09 | jf-cc727 | Backend/CSR/Trap, Memory/MMU | pipeline/control, forward progress, queue/buffer, memory/protection | 1 | Split Load Tail Page Fault Does Not Retire |
| [#6199](https://github.com/OpenXiangShan/XiangShan/issues/6199) | open | 2026-07-06 | Security-HC | Backend/CSR/Trap, Memory/MMU | memory/protection, root-cause note | 2 | [Bug][PMP][2-core] S-mode store to L=1 locked PMP region does not raise store access fault |
| [#6182](https://github.com/OpenXiangShan/XiangShan/issues/6182) | open | 2026-07-02 | LeeHaofeng | Backend/ROB, Backend/CSR/Trap, Backend/Execution | pipeline/control, memory/protection | 2 | Stale interrupt taken after `mstatus.MIE`/`sstatus.SIE`/`vsstatus.SIE` is cleared |
| [#6165](https://github.com/OpenXiangShan/XiangShan/issues/6165) | closed | 2026-06-28 | YzhDDDing | frontend, Frontend/BPU | pipeline/control, queue/buffer, prediction, root-cause note | 7 | `TAGE` can expose a new entry with a stale useful counter to FTQ/train metadata |
| [#6160](https://github.com/OpenXiangShan/XiangShan/issues/6160) | closed | 2026-06-27 | ScottaMcdonald | frontend, Frontend/BPU, Memory/LSU, Memory/Cache | pipeline/control, prediction, memory/protection | 2 | ITTAGE `altDiffers` asserts with no alternate provider and mistrains useful counter |
| [#6159](https://github.com/OpenXiangShan/XiangShan/issues/6159) | open | 2026-06-27 | ScottaMcdonald | frontend, Frontend/BPU, Backend/CSR/Trap | queue/buffer, prediction, memory/protection, root-cause note | 2 | `uTage` keeps issuing SRAM reads after `SBPCTL.ABTB_ENABLE` is cleared and can block table writes |
| [#6158](https://github.com/OpenXiangShan/XiangShan/issues/6158) | open | 2026-06-27 | ScottaMcdonald | Memory/Cache, Memory/MMU | memory/protection | 1 | `prefetch.w` loses write intent and is admitted as a read prefetch on a read-only page |
| [#6157](https://github.com/OpenXiangShan/XiangShan/issues/6157) | closed | 2026-06-27 | ScottaMcdonald | Frontend/BPU, Frontend/IFU | pipeline/control, forward progress, pipeline stage, prediction | 4 | `FTQ` accepts delayed BPU metadata for redirect-flushed entries |
| [#6156](https://github.com/OpenXiangShan/XiangShan/issues/6156) | open | 2026-06-27 | ScottaMcdonald | frontend, backend, Frontend/IFU, Backend/CSR/Trap, Memory/MMU | pipeline/control, pipeline stage, memory/protection | 2 | `satp` first-fetch page fault writes sign-extended mepc/mtval on kunminghu-v3 |
| [#6155](https://github.com/OpenXiangShan/XiangShan/issues/6155) | open | 2026-06-27 | ScottaMcdonald | frontend, Frontend/BPU, Backend/CSR/Trap | prediction, memory/protection, root-cause note | 2 | `uTage` SRAM banks continue read-clock activity after `ABTB_ENABLE` is cleared |
| [#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154) | open | 2026-06-26 | YzhDDDing | memory, Backend/ROB, Backend/Execution, Interconnect/SoC | pipeline/control, queue/buffer, pipeline stage | 6 | `NewLoadUnit` scalar loads can write back after redirect kill and expose wrong-path data on the RF write bus |
| [#6153](https://github.com/OpenXiangShan/XiangShan/issues/6153) | open | 2026-06-26 | YzhDDDing | memory, Backend/CSR/Trap, Memory/LSU, Memory/Cache, Memory/MMU | pipeline/control, memory/protection | 12 | `Load` breakpoint trigger does not kill S1 DCache lookup and can leave post-trap cache state |
| [#6151](https://github.com/OpenXiangShan/XiangShan/issues/6151) | open | 2026-06-26 | ruc-jty | Frontend/BPU, Frontend/IFU, Backend/ROB, Backend/Execution, Memory/LSU, Memory/MMU | pipeline/control, forward progress, pipeline stage, memory/protection, root-cause note | 3 | [Bug] VSegmentUnit: segment instructions with vl=0 stall the memory unit hundreds of cycles instead of retiring immediately |
| [#6150](https://github.com/OpenXiangShan/XiangShan/issues/6150) | open | 2026-06-26 | YzhDDDing | Frontend/BPU, Frontend/IFU, Backend/CSR/Trap, Memory/LSU, Memory/Cache | pipeline/control, pipeline stage, memory/protection | 6 | Illegal `DRET` outside Debug Mode emits stale xRET redirect and creates a secret-dependent ICache fetch oracle |
| [#6149](https://github.com/OpenXiangShan/XiangShan/issues/6149) | open | 2026-06-26 | YzhDDDing | frontend, Frontend/BPU, Frontend/IFU, Backend/CSR/Trap, Memory/MMU | pipeline/control, pipeline stage, prediction, memory/protection | 7 | `sbpctl.RAS_ENABLE` does not disable RAS/URAS and allows secret-dependent wrong-path fetches |
| [#6148](https://github.com/OpenXiangShan/XiangShan/issues/6148) | open | 2026-06-26 | YzhDDDing | frontend, Frontend/BPU, Frontend/IFU | queue/buffer, pipeline stage, prediction, memory/protection, root-cause note | 5 | `ITTAGE` is accessed by direct-only fetch blocks, creating a software-visible timing channel |
| [#6141](https://github.com/OpenXiangShan/XiangShan/issues/6141) | open | 2026-06-25 | Jason-Young123 | Backend/CSR/Trap, Memory/MMU | memory/protection | 2 | Xiangshan and NEMU diverge between EX_LAM and EX_LAF on pure load cross-page MMIO access when only the second PMP region loses R permission |
| [#6139](https://github.com/OpenXiangShan/XiangShan/issues/6139) | open | 2026-06-25 | Jason-Young123 | memory, Backend/CSR/Trap, Memory/MMU | memory/protection | 2 | Xiangshan and NEMU diverge in mtval/tval on pure store cross-page MMIO access when only the second PMP region loses W permission |
| [#6138](https://github.com/OpenXiangShan/XiangShan/issues/6138) | open | 2026-06-25 | YzhDDDing | frontend, Frontend/BPU, Frontend/IFU | pipeline/control, pipeline stage, prediction | 5 | Same-FTQ wrong-path branch can train BPU and leak one bit through timing |
| [#6137](https://github.com/OpenXiangShan/XiangShan/issues/6137) | open | 2026-06-25 | YzhDDDing | frontend, Frontend/BPU, Frontend/IFU, Memory/LSU, Memory/Cache, Memory/MMU, Interconnect/SoC | pipeline/control, pipeline stage, prediction, memory/protection | 7 | `ABTB` can emit stale predictions after `sbpctl` disables ABTB and drive transient I/D-cache accesses |
| [#6135](https://github.com/OpenXiangShan/XiangShan/issues/6135) | open | 2026-06-25 | YzhDDDing | frontend, Frontend/BPU, Frontend/IFU, Backend/ROB, Backend/CSR/Trap, Memory/LSU, Memory/Cache | pipeline/control, pipeline stage, prediction, memory/protection | 4 | RAS disable control is ignored for return prediction and leaves a secret-dependent timing side channel |
| [#6134](https://github.com/OpenXiangShan/XiangShan/issues/6134) | closed | 2026-06-25 | YzhDDDing | frontend, Frontend/IFU, Memory/MMU, Interconnect/SoC | pipeline stage, memory/protection | 2 | `PBMT-IO` instruction fetch skips `IFU` uncache last-commit serialization |
| [#6133](https://github.com/OpenXiangShan/XiangShan/issues/6133) | open | 2026-06-25 | YzhDDDing | memory, Frontend/BPU, Backend/ROB | pipeline/control, forward progress, queue/buffer, memory/protection, root-cause note | 3 | `LoadQueueUncache` full-buffer rollback can carry accepted MMIO load metadata |
| [#6130](https://github.com/OpenXiangShan/XiangShan/issues/6130) | open | 2026-06-24 | LeeHaofeng | memory, Memory/Cache, Interconnect/SoC | pipeline/control | 1 | In , / / error flags can be silently dropped when RXDAT and RXRSP fire in the same cycle |
| [#6127](https://github.com/OpenXiangShan/XiangShan/issues/6127) | open | 2026-06-24 | LeeHaofeng | Backend/CSR/Trap | memory/protection, data correctness | 2 | out-of-bounds dynamic index: CSR-writable event selector beyond range counts the wrong event |
| [#6126](https://github.com/OpenXiangShan/XiangShan/issues/6126) | open | 2026-06-24 | Jacob-yen | memory, Backend/CSR/Trap, Memory/MMU | memory/protection | 4 | HLV.WU ignores SPVP=VU effective privilege for final PMP checks |
| [#6113](https://github.com/OpenXiangShan/XiangShan/issues/6113) | open | 2026-06-18 | ruc-jty | Backend/CSR/Trap, Interconnect/SoC | pipeline/control, memory/protection | 1 | [Bug] VS-mode wrongly raises EX_II instead of EX_VI when mstateen0.AIA=0 and vsiselect ∈ 0x30-0x3F |
| [#6093](https://github.com/OpenXiangShan/XiangShan/issues/6093) | open | 2026-06-13 | LeeHaofeng | Backend/Execution | data correctness | 2 | overflow rounding uses wrong normal-path GRS bits |
| [#6068](https://github.com/OpenXiangShan/XiangShan/issues/6068) | open | 2026-06-08 | ruc-jty | Backend/CSR/Trap | forward progress, memory/protection | 1 | WFI does not resume execution when some interrupts become pending |
| [#6066](https://github.com/OpenXiangShan/XiangShan/issues/6066) | open | 2026-06-07 | LeeHaofeng | Backend/CSR/Trap, Backend/Execution | data correctness | 1 | VMask.scala truncation bug for and large |
| [#6063](https://github.com/OpenXiangShan/XiangShan/issues/6063) | open | 2026-06-07 | LeeHaofeng | Backend/Execution | data correctness | 1 | fflags lost for |
| [#6062](https://github.com/OpenXiangShan/XiangShan/issues/6062) | closed | 2026-06-07 | jimmymtest | memory, Backend/CSR/Trap, Memory/LSU, Memory/Cache, Memory/MMU | pipeline/control, memory/protection | 3 | S-mode cbo.clean to a PMP-denied block aborts StoreQueue instead of raising access fault |
| [#6059](https://github.com/OpenXiangShan/XiangShan/issues/6059) | open | 2026-06-05 | LeeHaofeng | Memory/Cache, Memory/MMU | memory/protection | 1 | PTWFilterEntry: `inflight_counter` bit-width too small (use log2Up(Size+1) not log2Up(Size)) |
| [#6057](https://github.com/OpenXiangShan/XiangShan/issues/6057) | open | 2026-06-05 | ra4ing | Backend/ROB, Backend/CSR/Trap, Memory/MMU | memory/protection | 3 | Illegal-instruction `mtval` uses a younger instruction encoding instead of the faulting instruction |
| [#6038](https://github.com/OpenXiangShan/XiangShan/issues/6038) | closed | 2026-05-28 | LeeHaofeng | Backend/CSR/Trap | pipeline/control | 2 | `NewCSR` Double-trap exception redirected to M-mode uses vectored interrupt offset instead of exception offset (`mtvec + 0`) |
| [#6032](https://github.com/OpenXiangShan/XiangShan/issues/6032) | closed | 2026-05-27 | ruc-jty | Backend/ROB, Backend/CSR/Trap | forward progress | 1 | [Bug] WFI does not resume execution when some interrupts become pending |
| [#6027](https://github.com/OpenXiangShan/XiangShan/issues/6027) | closed | 2026-05-26 | LeeHaofeng | backend, Backend/CSR/Trap | pipeline/control, root-cause note | 1 | `onlyC3Enable` is always false |
| [#6018](https://github.com/OpenXiangShan/XiangShan/issues/6018) | open | 2026-05-25 | LeeHaofeng | memory, Memory/Cache | data correctness, root-cause note | 2 | `triggerTag` truncated when `trainOnVaddr` is enabled (`vtag` 40 bit → `triggerTag` 32 bit) |
| [#6015](https://github.com/OpenXiangShan/XiangShan/issues/6015) | open | 2026-05-25 | lhb-sec | memory, Backend/ROB, Backend/Execution, Memory/LSU, Interconnect/SoC | pipeline/control, forward progress, queue/buffer | 2 | Pipeline hangs on `vluxei32.v` when accessing unmapped physical addresses |
| [#6002](https://github.com/OpenXiangShan/XiangShan/issues/6002) | open | 2026-05-22 | YzhDDDing | Backend/ROB, Memory/LSU | pipeline/control, queue/buffer, pipeline stage, memory/protection, data correctness | 11 | Same-page cross-16B store-load OctaWord nuke mask is not shifted for upper VWord |
| [#6001](https://github.com/OpenXiangShan/XiangShan/issues/6001) | closed | 2026-05-21 | ruc-jty | backend, Backend/CSR/Trap, Interconnect/SoC | pipeline/control | 2 | [BUG] SEI injected from M‑mode is encoded as priority 0 instead of 256 |
| [#6000](https://github.com/OpenXiangShan/XiangShan/issues/6000) | open | 2026-05-21 | Wowblk | frontend, backend, Frontend/IFU, Backend/Execution | pipeline/control, pipeline stage, prediction | 7 | JumpUnit compressed c.jr backend redirect clears isRVC |
| [#5998](https://github.com/OpenXiangShan/XiangShan/issues/5998) | closed | 2026-05-21 | YzhDDDing | memory, Memory/LSU | pipeline/control, forward progress, queue/buffer, pipeline stage, prediction, memory/protection, data correctness | 2 | StoreQueue cross-16B multi-match partial forward is treated as safe full overlap |
| [#5995](https://github.com/OpenXiangShan/XiangShan/issues/5995) | open | 2026-05-20 | biquanha | backend, Backend/CSR/Trap, Memory/LSU, Memory/MMU | queue/buffer, memory/protection, root-cause note | 6 | [BUG]kunminghu-v2 HLVX does not check final PMA/PMP execute permission |
| [#5994](https://github.com/OpenXiangShan/XiangShan/issues/5994) | closed | 2026-05-19 | ScottaMcdonald | frontend, backend | pipeline/control | 7 | Backend redirect for compressed `c.bnez` reports `isRVC=0`, causing wrong redirect CFI PC |
| [#5988](https://github.com/OpenXiangShan/XiangShan/issues/5988) | closed | 2026-05-19 | YzhDDDing | frontend, Frontend/BPU, Frontend/IFU, Backend/ROB | pipeline/control, prediction, memory/protection | 2 | `MainBTB` replacement state aliases across different physical sets |
| [#5960](https://github.com/OpenXiangShan/XiangShan/issues/5960) | open | 2026-05-14 | wang02020119 | Backend/ROB, Memory/Cache | pipeline/control, forward progress, queue/buffer, pipeline stage | 2 | Zicbop prefetch.r / prefetch.i hint can cause no-progress hang in TLMinimalConfig |
| [#5958](https://github.com/OpenXiangShan/XiangShan/issues/5958) | open | 2026-05-14 | 0x1B05 | Backend/CSR/Trap, Backend/Execution, Memory/LSU, Memory/MMU | pipeline/control, forward progress, queue/buffer, memory/protection | 7 | Kunminghu-v2: LoadQueueReplay assertion on translated cross-page vector byte load/store |
| [#5934](https://github.com/OpenXiangShan/XiangShan/issues/5934) | open | 2026-05-10 | KnightGOKU | Backend/CSR/Trap, Backend/Execution | data correctness | 1 | `vlseg2e16.v` corrupts an active destination lane during masked segment load |
| [#5921](https://github.com/OpenXiangShan/XiangShan/issues/5921) | open | 2026-05-09 | youzi27 | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | Incorrect `vstart` update after faulting vector indexed store |
| [#5910](https://github.com/OpenXiangShan/XiangShan/issues/5910) | closed | 2026-05-07 | Jason-Young123 | frontend, Frontend/IFU, Backend/CSR/Trap, Memory/MMU | pipeline stage, memory/protection | 1 | Diff-test fails when EX_IAF occurs across 2 physical pages in 0x1000_0000 ~ 0x1fff_ffff, related to #5872 |
| [#5908](https://github.com/OpenXiangShan/XiangShan/issues/5908) | closed | 2026-05-07 | mmxsrup | memory, Memory/LSU | memory/protection | 3 | [BUG] UnalignQueue "enqPtr < deqPtr" assertion triggered by cross-16B sd under sbuffer-stride pressure |
| [#5872](https://github.com/OpenXiangShan/XiangShan/issues/5872) | closed | 2026-04-28 | Jason-Young123 | frontend, Frontend/IFU, Backend/CSR/Trap, Memory/MMU | pipeline stage, memory/protection | 1 | Diff-test fails when EX_IAF (Exception: InstrAccessFault) occurs across two physical pages (unexpected mtval and mcause) |
| [#5865](https://github.com/OpenXiangShan/XiangShan/issues/5865) | open | 2026-04-27 | KnightGOKU | Backend/CSR/Trap, Backend/Execution, Memory/MMU | forward progress, data correctness | 1 | XiangShan misses illegal-instruction trap for reserved masked `vmerge.vvm` with vd = v0 |
| [#5861](https://github.com/OpenXiangShan/XiangShan/issues/5861) | closed | 2026-04-25 | DFPMTS | memory | memory/protection, data correctness | 2 | NewLoadUnit: `staNukeQueryReq` can miss second-half overlap of a cross16B non-cross-page store |
| [#5854](https://github.com/OpenXiangShan/XiangShan/issues/5854) | closed | 2026-04-23 | Hoshi44 | memory, Backend/Execution, Memory/MMU | memory/protection, data correctness | 2 | [BUG] L2TLB: cfs indexed with wrong truncated PPN, defeats bitmap isolation |
| [#5851](https://github.com/OpenXiangShan/XiangShan/issues/5851) | closed | 2026-04-23 | mmxsrup | memory | pipeline/control, forward progress, queue/buffer, memory/protection | 3 | [BUG] Cross-page misaligned sd followed by alias lbu reads stale data |
| [#5850](https://github.com/OpenXiangShan/XiangShan/issues/5850) | closed | 2026-04-23 | mmxsrup | memory, Memory/LSU | queue/buffer, memory/protection | 3 | [BUG] StoreQueue "double deq!" assertion triggered by two cross-page misaligned stores |
| [#5849](https://github.com/OpenXiangShan/XiangShan/issues/5849) | closed | 2026-04-23 | mmxsrup | memory, Memory/LSU | memory/protection | 3 | [BUG] StoreQueue "double deq!" assertion triggered by cross-16B stores interleaved with loads |
| [#5847](https://github.com/OpenXiangShan/XiangShan/issues/5847) | closed | 2026-04-22 | mmxsrup | memory, Memory/LSU, Memory/Cache | memory/protection | 2 | [BUG] StoreQueue "deqPtr > rdataPtr" assertion triggered by cross-16B misaligned store under sbuffer pressure |
| [#5846](https://github.com/OpenXiangShan/XiangShan/issues/5846) | closed | 2026-04-22 | DFPMTS | memory, Memory/LSU | memory/protection | 2 | NewStoreQueue: `cross16BDeqReg` can clear between the two sbuffer writes of one cross16B store and over-advance `deqPtr` |
| [#5832](https://github.com/OpenXiangShan/XiangShan/issues/5832) | open | 2026-04-20 | jimmymtest | Backend/CSR/Trap, Backend/Execution, Memory/MMU | data correctness | 1 | [BUG] `vl8re64.v` truncates upper 64-bit data in whole-register loads |
| [#5831](https://github.com/OpenXiangShan/XiangShan/issues/5831) | open | 2026-04-20 | jimmymtest | Backend/CSR/Trap, Memory/MMU | data correctness | 1 | [BUG] `vlse32.v` corrupts packed 32-bit element data under SEW=64, LMUL=8 mixed-EEW execution |
| [#5830](https://github.com/OpenXiangShan/XiangShan/issues/5830) | open | 2026-04-20 | jimmymtest | Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline stage | 1 | [BUG] `vfmv.f.s` fails to NaN-box 32-bit values when writing to a 64-bit floating-point registe |
| [#5792](https://github.com/OpenXiangShan/XiangShan/issues/5792) | closed | 2026-04-09 | maxiaoran24 | memory, Memory/LSU, Memory/Cache, Memory/MMU | pipeline/control, forward progress, pipeline stage, memory/protection | 2 | NewLoadUnit: matchInvalid/vp_match_fail path incorrectly preserves replay cause and creates a replay entry alongside rollback |
| [#5790](https://github.com/OpenXiangShan/XiangShan/issues/5790) | closed | 2026-04-08 | zhangkanqi | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | Mismatch mcause and mtval when executing vssseg3e16.v |
| [#5780](https://github.com/OpenXiangShan/XiangShan/issues/5780) | closed | 2026-04-07 | zhangkanqi | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | `amominu.w` instruction behavior mismatch between Xiangshan and NEMU |
| [#5779](https://github.com/OpenXiangShan/XiangShan/issues/5779) | closed | 2026-04-07 | Hoshi44 | backend, Memory/Cache, Memory/MMU | pipeline/control, memory/protection, data correctness | 3 | HFENCE.GVMA fails to ignore upper bits of rs2 (VMID) |
| [#5777](https://github.com/OpenXiangShan/XiangShan/issues/5777) | closed | 2026-04-07 | zhangkanqi | Frontend/BPU, Frontend/IFU, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline/control, forward progress, memory/protection | 3 | No instructions have been submitted for a long time. Could this be a case of deadlock? |
| [#5773](https://github.com/OpenXiangShan/XiangShan/issues/5773) | open | 2026-04-06 | zhangkanqi | backend, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline/control, memory/protection | 2 | Incorrect exception type raised when flh accessing addr=0x1 |
| [#5772](https://github.com/OpenXiangShan/XiangShan/issues/5772) | open | 2026-04-06 | zhangkanqi | backend, Frontend/BPU, Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | vmv4r.v with misaligned registers dosen't raise illegal instruction exception |
| [#5770](https://github.com/OpenXiangShan/XiangShan/issues/5770) | open | 2026-04-06 | zhangkanqi | backend, Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | Vector whole register load(vl2re32.v) partially updates destination on exception |
| [#5769](https://github.com/OpenXiangShan/XiangShan/issues/5769) | open | 2026-04-06 | zhangkanqi | backend, Frontend/IFU, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline stage, memory/protection | 2 | Vector indexed segment store (vsuxseg*ei*) reports only base address in mtval on exception |
| [#5768](https://github.com/OpenXiangShan/XiangShan/issues/5768) | open | 2026-04-05 | Hoshi44 | backend, Backend/CSR/Trap, Backend/Execution | memory/protection | 1 | Vector FP move/merge instructions missing `frm` reserved value check |
| [#5766](https://github.com/OpenXiangShan/XiangShan/issues/5766) | open | 2026-04-05 | KnightGOKU | backend, Backend/CSR/Trap, Memory/MMU | memory/protection | 3 | `vle8ff` fault-only-first followed by immediate `csrr vl` returns 0 on XiangShan, while Spike returns the expected `vl` |
| [#5739](https://github.com/OpenXiangShan/XiangShan/issues/5739) | closed | 2026-03-29 | KnightGOKU | backend, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline/control, memory/protection | 2 | `csrr vl` reads stale zero immediately after `vsetvli` |
| [#5725](https://github.com/OpenXiangShan/XiangShan/issues/5725) | closed | 2026-03-25 | KnightGOKU | backend, Frontend/BPU, Frontend/IFU, Backend/CSR/Trap, Memory/MMU | pipeline/control, memory/protection | 3 | Behavior mismatch on RVV testcase (`vsetvl x0, x0, rs2` path) |
| [#5721](https://github.com/OpenXiangShan/XiangShan/issues/5721) | closed | 2026-03-24 | oChunCai | backend | queue/buffer | 2 | Bug Report: Missing forward dmode check when writing mcontrol6.chain |
| [#5695](https://github.com/OpenXiangShan/XiangShan/issues/5695) | closed | 2026-03-17 | oChunCai | memory, Memory/LSU | pipeline/control | 1 | Misaligned accesses to non-idempotent regions raise AddrMisaligned instead of AccessFault |
| [#5689](https://github.com/OpenXiangShan/XiangShan/issues/5689) | closed | 2026-03-14 | YAM2020er | Memory/MMU | memory/protection | 1 | Illegal address access |
| [#5628](https://github.com/OpenXiangShan/XiangShan/issues/5628) | closed | 2026-02-27 | oChunCai | Backend/CSR/Trap | memory/protection | 2 | VU direct access to VS CSR reports EX_VI, expected EX_II (illegal-instruction) |
| [#5608](https://github.com/OpenXiangShan/XiangShan/issues/5608) | closed | 2026-02-06 | quange51 | top, Frontend/BPU, Frontend/IFU, Backend/ROB, Backend/CSR/Trap, Backend/Execution, Memory/LSU, Memory/Cache, Memory/MMU, Interconnect/SoC | forward progress, pipeline stage, memory/protection | 1 | Compilation failed on the nanhu branch |
| [#5565](https://github.com/OpenXiangShan/XiangShan/issues/5565) | closed | 2026-01-23 | oChunCai | backend, Memory/Cache | queue/buffer, memory/protection, data correctness | 1 | [Bug]IntRegCache perf history write index can exceed `Vec(48)` bounds (pointer wraps at 64) |
| [#5502](https://github.com/OpenXiangShan/XiangShan/issues/5502) | closed | 2026-01-08 | WHR-oss | Backend/ROB | pipeline stage | 1 | Error when run command such as "make verilog CONFIG=XSNoCTopConfig", "make verilog CONFIG=KunminghuV2Config" |
| [#5426](https://github.com/OpenXiangShan/XiangShan/issues/5426) | closed | 2025-12-24 | cesarus777 | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | v31_low different on vfredusum.vs |
| [#5282](https://github.com/OpenXiangShan/XiangShan/issues/5282) | closed | 2025-11-30 | youzi27 | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | Incorrect mtval in both reference models for specific illegal-instruction sequences |
| [#5257](https://github.com/OpenXiangShan/XiangShan/issues/5257) | closed | 2025-11-25 | BoA5li | Frontend/IFU, Backend/CSR/Trap, Memory/Cache, Memory/MMU | pipeline stage | 1 | Incorrect Instruction Page Fault When Executing From a Valid Sv39-Mapped Supervisor Page |
| [#5248](https://github.com/OpenXiangShan/XiangShan/issues/5248) | closed | 2025-11-24 | canxin121 | Backend/CSR/Trap | memory/protection | 2 | CSR exception behavior differs |
| [#5137](https://github.com/OpenXiangShan/XiangShan/issues/5137) | open | 2025-10-22 | E1thannn | Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection, root-cause note | 2 | BUG when load word from illegal address |
| [#5129](https://github.com/OpenXiangShan/XiangShan/issues/5129) | closed | 2025-10-20 | MrCookieeeee | Backend/CSR/Trap, Memory/MMU | memory/protection | 2 | Difference between NEMU, XiangshanCore and SPIKE |
| [#5102](https://github.com/OpenXiangShan/XiangShan/issues/5102) | closed | 2025-10-10 | bantierr | backend, Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection | 2 | [BUG] bug in local interrupt behaviour |
| [#4981](https://github.com/OpenXiangShan/XiangShan/issues/4981) | closed | 2025-08-27 | poemonsense | Frontend/IFU, Backend/CSR/Trap, Backend/Execution, Memory/MMU | pipeline/control, pipeline stage, memory/protection, root-cause note | 3 | [BOT] DUT and REFs disagree on s10, mcause, mtval values. |
| [#4980](https://github.com/OpenXiangShan/XiangShan/issues/4980) | open | 2025-08-27 | poemonsense | backend, Backend/CSR/Trap, Backend/Execution, Memory/MMU | memory/protection, root-cause note | 3 | [BOT] mstatus/sstatus high bits set unexpectedly at exception entry |
| [#4973](https://github.com/OpenXiangShan/XiangShan/issues/4973) | closed | 2025-08-25 | timegoer | Frontend/BPU, Backend/CSR/Trap | prediction | 1 | Mismatch between Xiangshan and NEMU in a random generated program |
| [#4798](https://github.com/OpenXiangShan/XiangShan/issues/4798) | closed | 2025-06-10 | BaoBao-zhu | frontend, Frontend/IFU, Backend/ROB, Memory/MMU | pipeline stage, memory/protection | 3 | XiangShan performance counter shows an abnormal proportion of ITLBMissBubble |
| [#4713](https://github.com/OpenXiangShan/XiangShan/issues/4713) | closed | 2025-05-20 | bantierr | Backend/CSR/Trap | memory/protection | 1 | Mstatus.MIE not set properly |
| [#4687](https://github.com/OpenXiangShan/XiangShan/issues/4687) | closed | 2025-05-13 | LuLuji04 | Backend/CSR/Trap | memory/protection | 1 | `tdata2` CSR read returns unexpected value |
| [#4682](https://github.com/OpenXiangShan/XiangShan/issues/4682) | closed | 2025-05-11 | LuLuji04 | Frontend/IFU, Backend/CSR/Trap | pipeline stage | 1 | Fails to raise Instruction Access Fault on invalid PC |
| [#4576](https://github.com/OpenXiangShan/XiangShan/issues/4576) | closed | 2025-04-16 | LuLuji04 | Backend/CSR/Trap | memory/protection | 1 | `unimp` After Returning from `ebreak` and Continuing Instructions Trigger Mismatch |
| [#4120](https://github.com/OpenXiangShan/XiangShan/issues/4120) | closed | 2025-01-02 | ha0lyu | Backend/CSR/Trap, Memory/MMU | memory/protection | 2 | Inconsistency behavior between xiangshan and NEMU after setting PMP |
| [#3842](https://github.com/OpenXiangShan/XiangShan/issues/3842) | closed | 2024-11-06 | sasakiakaya | Memory/Cache | memory/protection | 1 | make verilog fail |
| [#3709](https://github.com/OpenXiangShan/XiangShan/issues/3709) | closed | 2024-10-10 | ha0lyu | Backend/CSR/Trap, Memory/MMU | memory/protection | 1 | D extension instr `fle.d` bug. |
| [#3012](https://github.com/OpenXiangShan/XiangShan/issues/3012) | closed | 2024-05-27 | cyyself | Backend/CSR/Trap, Backend/Execution, Memory/Cache | memory/protection, data correctness | 2 | Difftest failed on a RISC-V Vector memcpy workload with misaligned(in vlen granularity, not element) unit stride load |
| [#2890](https://github.com/OpenXiangShan/XiangShan/issues/2890) | closed | 2024-04-16 | camel-cdr | Frontend/BPU, Backend/ROB, Backend/Execution, Memory/LSU | forward progress, queue/buffer | 2 | Simulation hangs for longer running functions using the vector extension |
| [#2658](https://github.com/OpenXiangShan/XiangShan/issues/2658) | closed | 2024-01-18 | euphgh | Frontend/IFU, Memory/MMU | pipeline/control, pipeline stage, memory/protection | 2 | TLB Timing interface not match with MMIO instruction fetch in IFU |
| [#2606](https://github.com/OpenXiangShan/XiangShan/issues/2606) | closed | 2024-01-02 | menglinhan | Frontend/IFU | pipeline stage | 1 | issue about kunminghu |
| [#2534](https://github.com/OpenXiangShan/XiangShan/issues/2534) | closed | 2023-12-07 | nieeka | Backend/CSR/Trap, Memory/Cache | memory/protection | 2 | L1D Cache Side-channal on Nanhu |
