# MDP Scenario Extraction

## Scope
- Mechanism: MDP
- Interpreted meaning: XiangShan memory dependence prediction, implemented by Store Set Identifier Table (SSIT), Last Fetched Store Table (LFST), and a currently non-wired WaitTable path.
- XiangShan source revision: `/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-code/XiangShan_src`, branch `master`, commit `1f8fa49`.
- Primary modules/paths:
  - `xiangshan/mem/mdp/StoreSet.scala`: `SSIT`, `LFST`
  - `xiangshan/mem/mdp/WaitTable.scala`: `WaitTable`
  - `xiangshan/backend/ctrlblock/MemCtrl.scala`: MDP integration
  - `xiangshan/backend/rename/Rename.scala`: SSIT result enters uop control fields
  - `xiangshan/backend/dispatch/Dispatch.scala`: LFST request/response overrides load wait control
  - `xiangshan/mem/lsqueue/LoadQueueReplay.scala`, `NewStoreQueue.scala`, `StoreUnit.scala`: downstream wait/replay/store-issue interaction
- Analyzer references used: `mem-cache.md`, `verification-special-attention.md`
- Verification-driver rules used: `xiangshanVerificationDriver.md`, `conflictScenarioDrivers.md`, `forwardProgressDrivers.md`, `performanceBottleneckDrivers.md`, `indexBusHashDrivers.md`

## Mechanism Model
| Aspect | Description | Source evidence |
| --- | --- | --- |
| Goal | Predict store-load memory dependence so loads can wait for the relevant older store instead of freely executing into a violation or conservatively waiting for all stores. | `StoreSet.scala:36-38`; Store Set paper acknowledgement at `StoreSet.scala:19-22` |
| Inputs | Folded decode PCs for SSIT lookup, `MemPredUpdateReq` violation-training request, dispatch LFST requests, store issue release events, redirect kill events, CSR controls. | `MemCtrl.scala:16-26`, `MemCtrl.scala:33-42`; `Bundle.scala:569-591` |
| Internal state | SSIT valid/data arrays (`valid`, `ssid`, `strict`), SSIT flush FSM, LFST `validVec`, `robIdxVec`, `allocPtr`, WaitTable 2-bit counters. | `StoreSet.scala:40-49`, `StoreSet.scala:86-101`, `StoreSet.scala:122-167`, `StoreSet.scala:375-378`; `WaitTable.scala:45-64` |
| Algorithm/control rule | SSIT lookup happens from decode to rename; violation update has s0 read, s1 read-result, s2 writeback; four update cases come directly from `Cat(s2_loadAssigned, s2_storeAssigned)`. | `StoreSet.scala:125-140`, `StoreSet.scala:172-187`, `StoreSet.scala:189-215`, `StoreSet.scala:246-305` |
| Outputs | Rename gets `storeSetHit`, `loadWaitStrict`, `ssid`; dispatch sends LFST req and overwrites `loadWaitBit`, `waitForRobIdx`, `loadWaitStrict`; store queue and replay logic consume those fields. | `Rename.scala:470-476`; `Dispatch.scala:1027-1038`; `NewStoreQueue.scala:364-372`; `LoadQueueReplay.scala:307-312`, `LoadQueueReplay.scala:722-726` |
| Observability | SSIT/LFST/WaitTable perf counters and debug signals expose update cases, prediction hits, strict cases, LFST overflow, and WaitTable set bits. | `StoreSet.scala:317-338`, `StoreSet.scala:461`; `WaitTable.scala:67-70`; `Dispatch.scala:1059-1071` |

## Pipeline And State Machines From Source
| Pipeline / FSM | Stages or states | State transition / handoff | Source evidence |
| --- | --- | --- | --- |
| SSIT lookup pipeline | Decode read -> Rename result | `io.ren/raddr` drive SRAM read ports in decode, and `io.rdata.valid/ssid/strict` is consumed in rename. | `StoreSet.scala:125-140`; `MemCtrl.scala:19-22`, `MemCtrl.scala:30`; `Rename.scala:470-476` |
| SSIT update pipeline | s0 read -> s1 read result -> s2 update | `RegNext(io.update.valid)` and `RegEnable(io.update)` capture the request; update takes over read ports for load/store PCs; s2 writes load/store SSIT entries according to four code cases. | `StoreSet.scala:172-187`, `StoreSet.scala:189-215`, `StoreSet.scala:220-244`, `StoreSet.scala:246-305` |
| SSIT flush FSM | `s_flush` -> `s_idle` -> `s_flush` | Reset starts in `s_flush`; each step clears one valid entry; timeout from `csrCtrl.lvpred_timeout` returns to flush. | `StoreSet.scala:142-168`; CSR source at `CSR.scala:360-365` |
| LFST dispatch/issue/redirect state path | Dispatch lookup/update -> store issue release -> redirect cleanup -> post-redirect allocPtr recovery | Dispatch reads current latest store for the SSID and allocates stores into `validVec/robIdxVec`; store issue clears matching ROB entry; redirect flushes younger entries and later recovers `allocPtr`. | `StoreSet.scala:383-412`, `StoreSet.scala:414-437`, `StoreSet.scala:439-459`; `StoreUnit.scala:336-339` |
| WaitTable path | Decode combinational read, update write, timeout reset | The module implements 2-bit wait counters, but `MemCtrl` currently assigns `io.waitTable2Rename := DontCare` and does not instantiate/wire `WaitTable`. | `WaitTable.scala:33-70`; `MemCtrl.scala:28-30` |

## Scenario Taxonomy
| Family | Why it matters | Applicable driver files |
| --- | --- | --- |
| SSIT four-case training | These are the four explicit source branches for store-set creation/extension/merge. | `xiangshanVerificationDriver.md`, `conflictScenarioDrivers.md`, `indexBusHashDrivers.md` |
| LFST wait generation | Converts SSIT `ssid` into a concrete `waitForRobIdx` or same-dispatch-bundle wait. | `conflictScenarioDrivers.md`, `forwardProgressDrivers.md` |
| Recovery and cleanup | SSIT timeout flush and LFST redirect cleanup prevent stale dependence state from blocking new work. | `forwardProgressDrivers.md`, `conflictScenarioDrivers.md` |
| Observability | Perf/debug counters prove each source-derived case and catch strict/overflow/replay symptoms. | `performanceBottleneckDrivers.md`, `performanceMonitorCounterDrivers.md` |

## Detailed Scenarios
| ID | Scenario | Initial state | Stimulus sequence | Concurrent pressure | Expected observation | Failure signature | Checkers / coverage | Source evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `MDP_SSIT_CASE_00` | Neither load nor store has an SSIT entry; allocate one store set for both PCs. | `valid_array(ldpc)=0`, `valid_array(stpc)=0`; SSIT not flushing. | Trigger a load-store violation update with `io.update.valid=1`, `ldpc=L`, `stpc=S`; let update pass s0/s1/s2. | Decode lookup is blocked or redirected because update takes SSIT read port; optionally choose same-index/different-PC hash pair to stress alias handling. | In s2, `Cat(s2_loadAssigned,s2_storeAssigned)=b00`; both load and store entries are written valid with `ssid=s2_allocSsid`, `strict=false`; `ssit_update_lxsx` increments. | Only one side becomes valid, load/store get different SSIDs, strict is set unexpectedly, same-write-port collision masks the wrong entry, or later rename does not see `storeSetHit`. | Storage write checker; SSIT update-case coverage `b00`; decode/update port conflict coverage; post-update rename scoreboard. | `StoreSet.scala:172-187`, `StoreSet.scala:193-215`, `StoreSet.scala:246-263`, `StoreSet.scala:317`; rename propagation at `Rename.scala:470-476` |
| `MDP_SSIT_CASE_10` | Load already has an SSIT entry, store does not. | `valid_array(ldpc)=1`, `valid_array(stpc)=0`; load entry has an old SSID. | Trigger violation update for the same load PC and a new store PC. | Same cycle can include decode lookup of unrelated PCs; update must reuse read ports and remain deterministic. | In s2, `Cat(...)=b10`; store entry is written valid and assigned `ssid=s2_ldSsidAllocate`, `strict=false`; `ssit_update_lysx` increments. | Store remains invalid, store SSID does not match code-derived allocation, load entry is overwritten unexpectedly, or downstream LFST cannot match store/load by SSID. | Training scoreboard; same-entry read/write checker; SSIT update-case coverage `b10`; LFST follow-up wait checker. | `StoreSet.scala:191-199`, `StoreSet.scala:211-215`, `StoreSet.scala:264-273`, `StoreSet.scala:318`; LFST request path at `Dispatch.scala:1027-1038` |
| `MDP_SSIT_CASE_01` | Store already has an SSIT entry, load does not. | `valid_array(ldpc)=0`, `valid_array(stpc)=1`; store entry has an old SSID. | Trigger violation update for a new load PC and the trained store PC. | Drive a later load dispatch with the new SSID while the matching store is live in LFST. | In s2, `Cat(...)=b01`; load entry is written valid and assigned `ssid=s2_stSsidAllocate`, `strict=false`; later rename marks the load `storeSetHit`; dispatch/LFST can return `shouldWait` and `waitForRobIdx`. | Load remains speculative with no store-set hit, LFST wait is not generated for a live store, or false independence leads to repeated `C_MA` replay. | Training scoreboard; LFST wait checker; replay prevention checker; SSIT update-case coverage `b01`. | `StoreSet.scala:274-283`, `StoreSet.scala:319`; `Rename.scala:470-476`; `StoreSet.scala:383-412`; `LoadQueueReplay.scala:722-726` |
| `MDP_SSIT_CASE_11` | Both load and store already have SSIT entries; merge to winner SSID, and set strict when the old SSIDs are equal. | `valid_array(ldpc)=1`, `valid_array(stpc)=1`; test subcases `loadOldSSID < storeOldSSID`, `storeOldSSID < loadOldSSID`, and `loadOldSSID == storeOldSSID`. | Trigger violation update after both PCs have prior entries. | Same entry conflict when `ldpc == stpc` or same write address; repeated violations with equal SSID to test strict update and `strict_failed` counter should remain zero. | In s2, both entries are written valid with `ssid=s2_winnerSSID`; if old SSIDs are equal, load entry strict bit is set; `ssit_update_lysy` and possibly `ssit_update_should_strict` increment. Same load/store write address disables the store write port, per code. | Loser SSID survives, merge chooses the larger SSID, strict is not set for same-SSID repeated violation, strict is set for unequal SSID, or `ssit_update_strict_failed` increments. | Store-set merge scoreboard; strict-bit checker; same-write-address conflict checker; SSIT update-case coverage `b11`. | `StoreSet.scala:211-218`, `StoreSet.scala:284-305`, `StoreSet.scala:308-324` |
| `MDP_LFST_WAIT_LIVE_STORE` | LFST has a live older store for the predicted SSID; younger load should wait for its ROB index. | SSIT lookup has produced `storeSetHit=1`, `ssid=X`; LFST `valid(X)=1`, latest `robIdx` points to an older store. | Dispatch a younger load with the same SSID after the store was dispatched but before store issue release. | Include same-dispatch bundle store/load with equal SSID and CSR variants `storeset_wait_store`, `lvpred_disable`, `no_spec_load`. | LFST response valid follows request valid; `shouldWait` is true when LFST valid or same-bundle hit and prediction is enabled; `waitForRobIdx` returns latest matching store or same-bundle store ROB index. Dispatch writes `loadWaitBit`, `waitForRobIdx`, `loadWaitStrict`. | Load issues without waiting for live predicted store, waits for wrong ROB index, waits even when `lvpred_disable` should suppress it, or ignores `no_spec_load`. | LFST dependency checker; CSR control cross coverage; same-dispatch-bundle coverage; wait-for-ROB scoreboard. | `StoreSet.scala:383-412`; `Dispatch.scala:1027-1038`; CSR fields at `Bundle.scala:583-591` |
| `MDP_LFST_RELEASE_REDIRECT_RECOVERY` | LFST live entries are released on store issue and killed on redirect. | LFST has one or more valid entries across SSIDs and LFSTWidth slots; `allocPtr` may be near wrap. | Issue stores through STA so `storeIssue.valid` carries matching `ssid`, `robIdx`, and `storeSetHit`; separately assert redirect that flushes younger stores. | Combine store issue and redirect around same entry; drive allocation near `allocPtr` wrap and overflow. | Matching store issue clears `validVec(ssid)(j)`; redirect clears entries whose `robIdx.needFlush(io.redirect)` is true; one cycle after redirect fire, `allocPtr` recovers to a non-valid slot. | Cleared store remains a blocking dependency, redirect leaves wrong-path store visible, allocPtr points to a live slot incorrectly, LFST overflow is silent under sustained store dispatch. | Release checker; redirect stale-dependency checker; pointer wrap checker; `LFST_Overflow_Count` coverage. | `StoreSet.scala:414-461`; store source at `StoreUnit.scala:336-339` |
| `MDP_REPLAY_STRICT_WAIT` | A store-load violation replay records the blocking store queue index and uses strict wait when trained by SSIT. | Load replay entry enqueues with `C_MA`; uop carries `loadWaitStrict` from rename/dispatch. | Cause a memory-order violation, then replay while store address/data readiness changes. | Vary `strict=0/1`, `stAddrReadyVec`, `stDataReadyVec`, `storeAddrInSameCycle`, and `sqEmpty`. | For `C_MA`, replay stores `addr_inv_sq_idx` into `blockSqIdx` and copies `uop.loadWaitStrict` into `strict`; dequeue address-not-blocked requires store address readiness, and strict mode removes the relaxed `!strict && stAddrReadyVec(...)` bypass. | Replay loops forever after store becomes ready, strict replay bypasses a not-ready address, non-strict replay waits unnecessarily, or load commits stale data. | Replay progress checker; strict wait checker; memory-order scoreboard. | `LoadQueueReplay.scala:307-312`, `LoadQueueReplay.scala:722-726`; dispatch strict propagation at `Dispatch.scala:1032-1038` |
| `MDP_WAITTABLE_NON_EFFECTIVE_PATH` | WaitTable implementation exists but is not wired by `MemCtrl` in this source revision. | `WaitTable.data` can update/reset internally, but `MemCtrl` does not instantiate it. | Inspect or instantiate WaitTable only in module-level unit testing; do not claim it affects rename in the integrated core unless wiring changes. | CSR `lvpred_disable`, `no_spec_load`, and `lvpred_timeout` still affect standalone WaitTable logic. | Standalone WaitTable rdata is `(counter selected bit or no_spec_load) && !lvpred_disable`; update shifts in true; timeout clears all entries. Integrated `waitTable2Rename` is `DontCare`. | A scenario assumes WaitTable changes core scheduling in this commit, or ignores the non-effective `DontCare` integration. | Evidence guard checker; standalone WaitTable unit coverage only. | `WaitTable.scala:45-70`; `MemCtrl.scala:28-30` |

## Directed Scenario Descriptions

### `MDP_SSIT_CASE_00` - New Load And New Store Allocate Same Store Set
- Intent: verify the first source branch in the SSIT trainer.
- Code-derived trigger: `s2_mempred_update_req_valid && !s2_loadAssigned && !s2_storeAssigned`.
- Preconditions: SSIT is not in `s_flush`; load and store PC indexes read invalid in s1.
- Cycle-level stimulus: cycle N asserts `io.update.valid` with folded `ldpc` and `stpc`; cycle N uses update read ports; cycle N+1 captures read results; cycle N+2 writes both load/store entries.
- Expected state transitions: no SSIT FSM transition unless timeout is also triggered; SSIT data arrays write both PCs with `valid=true`, common `s2_allocSsid`, and `strict=false`.
- Expected outputs: later decode/rename lookup of either PC returns `rdata.valid=1`, same `ssid`, and `strict=0`; rename maps this into `storeSetHit=1`, `loadWaitStrict=0`, and `ssid`.
- Negative checks: no one-sided allocation; no unexpected strict bit; no wrong common SSID.
- Metrics: `ssit_update_lxsx`, `ssit_pred_dependence`.
- Coverage bins: ldpc/stpc distinct indexes, same index, min/max folded SSID, update while decode read port would otherwise be active.
- Debug/waveform signals: `io.update.valid`, `valid_array.io.raddr`, `s1_loadAssigned`, `s1_storeAssigned`, `s2_allocSsid`, `valid_array.io.wen`, `data_array.io.wdata.ssid`.
- Source evidence: `StoreSet.scala:172-187`, `StoreSet.scala:246-263`, `StoreSet.scala:317`, `Rename.scala:470-476`.
- Evidence gaps: exact folded PC generation should be traced in decode if this becomes a runnable test generator.

### `MDP_SSIT_CASE_10` - Existing Load Extends Store Set To New Store
- Intent: verify the second source branch where only the load had a prior store-set assignment.
- Code-derived trigger: `s2_mempred_update_req_valid && s2_loadAssigned && !s2_storeAssigned`.
- Preconditions: load PC entry is valid; store PC entry is invalid.
- Cycle-level stimulus: train a violation between the assigned load PC and unassigned store PC.
- Expected state transitions: only the store PC entry is written by `update_st_ssit_entry`; load entry is not rewritten by this branch.
- Expected outputs: subsequent dispatch of the store with the assigned SSID can allocate LFST state for future younger loads.
- Negative checks: load entry is not corrupted; store entry becomes valid; same-address write conflict rule is respected if `ldpc == stpc`.
- Metrics: `ssit_update_lysx`.
- Coverage bins: load SSID low/high boundary, store PC same-index alias, update followed by LFST store allocation.
- Debug/waveform signals: `s2_loadAssigned`, `s2_storeAssigned`, `s2_ldSsidAllocate`, `SSIT_UPDATE_STORE_WRITE_PORT`.
- Source evidence: `StoreSet.scala:264-273`, `StoreSet.scala:308-318`, `Dispatch.scala:1027-1038`.
- Evidence gaps: confirm whether `s2_ldSsidAllocate` instead of `s2_loadOldSSID` is intended for this revision before writing a functional golden model.

### `MDP_SSIT_CASE_01` - Existing Store Extends Store Set To New Load
- Intent: verify the third source branch where only the store had a prior store-set assignment.
- Code-derived trigger: `s2_mempred_update_req_valid && !s2_loadAssigned && s2_storeAssigned`.
- Preconditions: store PC entry is valid; load PC entry is invalid.
- Cycle-level stimulus: train a violation between the unassigned load PC and assigned store PC; later dispatch a load of that PC while a matching store is live.
- Expected state transitions: only the load PC entry is written by `update_ld_ssit_entry`.
- Expected outputs: rename sees `storeSetHit`; dispatch sends LFST request; LFST may return `shouldWait` and `waitForRobIdx`.
- Negative checks: load does not remain false-independent; LFST wait target is not stale.
- Metrics: `ssit_update_lxsy`, `storeset_load_wait`.
- Coverage bins: store SSID low/high boundary, live LFST hit, no live LFST miss, same-dispatch-bundle hit.
- Debug/waveform signals: `s2_stSsidAllocate`, `io.ssit(i).valid`, `io.lfst.req.valid`, `io.lfst.resp.bits.shouldWait`.
- Source evidence: `StoreSet.scala:274-283`, `StoreSet.scala:319`, `Rename.scala:470-476`, `StoreSet.scala:383-412`.
- Evidence gaps: runnable test should also trace the store-load violation producer that emits `MemPredUpdateReq`.

### `MDP_SSIT_CASE_11` - Existing Load And Store Merge Store Sets
- Intent: verify the fourth source branch and strict-bit subcase.
- Code-derived trigger: `s2_mempred_update_req_valid && s2_loadAssigned && s2_storeAssigned`.
- Preconditions: both PCs have valid SSIT entries.
- Cycle-level stimulus: train violations for entries with different SSIDs and same SSID.
- Expected state transitions: both entries are rewritten to `s2_winnerSSID`; same old SSID additionally marks load strict through the load write port.
- Expected outputs: future dependent load carries `loadWaitStrict`; dispatch keeps strict only when LFST says wait; replay `C_MA` copies `uop.loadWaitStrict` into replay strict state.
- Negative checks: no larger-SSID winner; no missing strict bit on same-SSID repeated violation; `ssit_update_strict_failed` should remain zero.
- Metrics: `ssit_update_lysy`, `ssit_update_should_strict`, `ssit_update_strict_failed`, `storeset_load_strict_wait`.
- Coverage bins: load SSID lower, store SSID lower, equal SSID, `ldpc == stpc` write-address conflict, strict replay.
- Debug/waveform signals: `s2_winnerSSID`, `s2_ssidIsSame`, `data_array.io.wdata(...).strict`, `fromRenameUpdate.loadWaitStrict`, replay `strict`.
- Source evidence: `StoreSet.scala:284-324`, `Dispatch.scala:1032-1038`, `LoadQueueReplay.scala:722-726`.
- Evidence gaps: generated assertion should account for the code's same-write-address masking at `StoreSet.scala:308-315`.

## Checker Plan
| Checker | Type | Watches | Pass condition | Failure message |
| --- | --- | --- | --- | --- |
| `mdp_ssit_case_scoreboard` | Storage/training scoreboard | `io.update`, s1 assigned bits, s2 writes, SSIT rdata | For each `Cat(s2_loadAssigned,s2_storeAssigned)` value, only the source-defined entries and fields update. | `SSIT update case wrote fields inconsistent with StoreSet.scala branch` |
| `mdp_same_entry_write_priority` | Conflict checker | SSIT load/store write port addresses and write enables | When load and store write addresses match, store write enable is masked as in source. | `SSIT same-address write conflict did not follow load-port priority` |
| `mdp_lfst_wait_scoreboard` | Dependency checker | dispatch LFST req/resp, `validVec`, `allocPtr`, `robIdxVec` | `shouldWait` and `waitForRobIdx` match live LFST or same-dispatch-bundle source rule. | `LFST returned wrong wait decision or ROB index` |
| `mdp_lfst_release_redirect_checker` | Recovery checker | `storeIssue`, `redirect`, `validVec`, `allocPtr` | Store issue clears matching live entry; redirect removes flushed entries and recovers allocation pointer. | `LFST stale dependency survived release/redirect` |
| `mdp_strict_replay_checker` | Replay/progress checker | `loadWaitStrict`, replay cause `C_MA`, `blockSqIdx`, `strict`, store readiness | Strict and non-strict replay release conditions match `LoadQueueReplay.scala`. | `MDP strict replay waited too little, too long, or livelocked` |
| `mdp_non_effective_waittable_guard` | Evidence guard | `MemCtrl.waitTable2Rename`, WaitTable instance presence | Integrated-core scenarios do not rely on WaitTable because `MemCtrl` drives `DontCare`. | `Scenario used WaitTable as effective core behavior without wiring evidence` |

## Coverage Plan
| Coverpoint | Bins | Crosses | Source rationale |
| --- | --- | --- | --- |
| `ssit_update_case` | `b00`, `b10`, `b01`, `b11` | same-index alias, distinct index, timeout flush active/inactive | Four source branches at `StoreSet.scala:246-305` |
| `ssit_ssid_relation` | load lower, store lower, equal | strict set, strict not set, same write address | Winner and strict logic at `StoreSet.scala:211-218`, `StoreSet.scala:284-315` |
| `lfst_occupancy` | empty, one live, multiple live, full/overflow, allocPtr wrap | dispatch store, store issue release, redirect | LFST arrays and pointer updates at `StoreSet.scala:375-461` |
| `lfst_wait_reason` | existing LFST hit, same-bundle hit, CSR forced no-spec, CSR disabled | load, store, `storeset_wait_store` | Wait decision at `StoreSet.scala:387-404` |
| `replay_strict` | strict false, strict true | store addr ready, store data ready, same-cycle store addr, SQ empty | Replay release logic at `LoadQueueReplay.scala:307-312`, `LoadQueueReplay.scala:722-726` |
| `waittable_status` | standalone update, standalone timeout reset, integrated non-effective | `lvpred_disable`, `no_spec_load` | WaitTable exists at `WaitTable.scala:33-70`, but integration is `DontCare` at `MemCtrl.scala:28-30` |

## Evidence Gaps
| Gap | Next file/search/action |
| --- | --- |
| Exact producer of `MemPredUpdateReq` for load-store violation | Trace `RedirectGenerator.scala`, load violation/nuke paths, and any producer connected to `MemCtrl.io.memPredUpdate`. |
| Folded PC expression feeding `mdpFlodPcVec` | Trace decode/control path that computes `mdpFoldPcVecVld` and `mdpFlodPcVec`. |
| Runnable stimulus mapping from scenario to testbench | Bind checkers to available simulator interfaces, difftest, perf counters, or waveform signals. |
| Configuration constants | Record effective values for `SSITSize`, `SSIDWidth`, `LFSTSize`, `LFSTWidth`, `DecodeWidth`, `RenameWidth`, `StoreSetEnable`, and `LFSTEnable` from the active parameter set. |
