# BPU Scenario Extraction

## Scope
- Mechanism: Frontend BPU / branch prediction ensemble
- Interpreted meaning: `FauFTB -> Tage_SC -> FTB -> ITTAGE -> RAS`, with `TageBTable` as the current effective bimodal base predictor
- XiangShan source revision: `kunminghu-v2`, commit `52262f303fc06daf84cdab7011d59b7df65ce7e8`
- Primary modules/paths:
  - `src/main/scala/xiangshan/frontend/BPU.scala`
  - `src/main/scala/xiangshan/frontend/FauFTB.scala`
  - `src/main/scala/xiangshan/frontend/FTB.scala`
  - `src/main/scala/xiangshan/frontend/Tage.scala`
  - `src/main/scala/xiangshan/frontend/SC.scala`
  - `src/main/scala/xiangshan/frontend/ITTAGE.scala`
  - `src/main/scala/xiangshan/frontend/newRAS.scala`
  - `src/main/scala/xiangshan/frontend/NewFtq.scala`
  - `src/main/scala/xiangshan/frontend/Frontend.scala`
  - `src/main/scala/xiangshan/frontend/IBuffer.scala`
  - `src/main/scala/xiangshan/frontend/IFU.scala`
- Analyzer references used:
  - `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/references/frontend.md`
  - `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/references/predictor-papers.md`
  - `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/references/verification-special-attention.md`
  - `tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/scripts/add_verification_attention.py`
  - `tools/xiangshan-code-analyzer/tools/regenerate_frontend_docs.py`
- Verification-driver rules used:
  - `xiangshanVerificationDriver.md`
  - `conflictScenarioDrivers.md`
  - `forwardProgressDrivers.md`
  - `performanceBottleneckDrivers.md`
  - `cacheStructureDrivers.md`
  - `fsmScenarioDrivers.md`

## Mechanism Model
| Aspect | Description | Source evidence |
| --- | --- | --- |
| Goal | 用多级前端预测链在低延迟与高准确率之间折中，尽早给出下一 fetch PC，同时在晚级发现更准确目标/方向时修正早级结果 | `frontend.md`; `regenerate_frontend_docs.py` BPU/FauFTB/FTB/Tage/SC/ITTAGE/RAS 条目 |
| Inputs | reset vector, CSR 控制, FTQ ready, backend redirect, FTQ commit update, PC/history/meta, call/ret/JALR 类型 | `regenerate_frontend_docs.py` BPU/FauFTB/FTB/ITTAGE/RAS sections |
| Internal state | S0-S3 pipeline valid/ready/fire, FTQ pointers, history/folded history, FTB/ITTAGE tables, SC counters/threshold, RAS spec/commit stack, `TageBTable` base counters | `verification-special-attention.md`; `add_verification_attention.py` line refs below |
| Algorithm/control rule | 早级低延迟、晚级覆盖；TAGE 用 folded history/tag/provider/alternate；SC 只在阈值与加权和满足时翻转；ITTAGE 以更长历史选择 target provider；RAS 以 push/pop/cancel/recovery 维护 return target；BPU 负责组合、比较、redirect 优先级和历史修复 | `predictor-papers.md`; `regenerate_frontend_docs.py` |
| Outputs | FTQ prediction block, next PC, branch mask, target, redirect, history recovery, FTQ metadata | `frontend.md`; `regenerate_frontend_docs.py` |
| Observability | `S0-S3` valid/ready/fire, `multiHit`, `false hit`, `s2_redirect`, `s3_redirect`, FTQ occupancy/pointers, RAS snapshots, predictor metadata scoreboard | `add_verification_attention.py`; `verification-special-attention.md` |

## Code Evidence Map
| Module | Effective behavior anchored by analyzer line refs | Notes |
| --- | --- | --- |
| `Frontend.scala` | `103-109`, `120-179`, `199-220` | Top-level resource envelope, context/permission path, end-to-end backpressure |
| `BPU.scala` | `381-455`, `827-883` | FTQ backpressure hold; redirect priority / history repair |
| `FauFTB.scala` | `76-128`, `139-205` | S0/S1 lookup and update flow |
| `FTB.scala` | `663-719`, `849-878` | multi-hit, false-hit, update path |
| `Tage.scala` | `155-215`, `217-267`, `311-448`, `904-1006` | `TageBTable`, provider/alternate selection, allocation/useful behavior |
| `SC.scala` | `259-372`, `376-448` | weighted sum / threshold / override |
| `ITTAGE.scala` | `311-410`, `552-610` | indirect target provider/alternate, allocation/update |
| `newRAS.scala` | `494-508`, `511-565`, `594-617` | S3 cancel repair, recursion counter, near-overflow gating |
| `NewFtq.scala` | `524-590`, `662-680`, `756-779`, `966-1039` | pointers, entry lifecycle, redirect overwrite, late `pdWb` |
| `IBuffer.scala` | `158-215`, `188-215` | occupancy, empty/full, per-lane hold |
| `IFU.scala` | `655-846`, `915-943`, `953-980` | MMIO/FSM, partial instruction buffering, backpressure |

## Scenario Taxonomy
| Family | Why it matters | Applicable driver files |
| --- | --- | --- |
| Baseline | Establish cold-start and nominal prediction cadence | `xiangshanVerificationDriver.md`, `performanceBottleneckDrivers.md` |
| Saturation | Stress FTQ / predictor-chain backpressure and late override bandwidth | `performanceBottleneckDrivers.md`, `forwardProgressDrivers.md` |
| Conflict | Expose same-entry, multi-hit, and redirect-source priority | `conflictScenarioDrivers.md` |
| Recovery | Verify redirect, cancel, false-hit, and late metadata cleanup | `xiangshanVerificationDriver.md`, `forwardProgressDrivers.md` |
| Forward progress | Avoid deadlock/livelock/starvation in prediction/update/recovery loops | `forwardProgressDrivers.md` |
| Boundary | Cover alias, wrap, full/empty, and near-overflow cases | `cacheStructureDrivers.md`, `indexBusHashDrivers.md`, `fsmScenarioDrivers.md` |
| Observability | Make failures diagnosable with stage-valid, redirect, and pointer traces | `performanceMonitorCounterDrivers.md`, `verification-special-attention.md` |

## Detailed Scenarios
| ID | Scenario | Initial state | Stimulus sequence | Concurrent pressure | Expected observation | Failure signature | Checkers / coverage | Source evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `BPU_BASELINE_001` | Cold-start baseline prediction | Reset released, FTQ empty, predictor tables cold or reset-scanned | Issue one legal fetch PC; allow normal BPU->FTQ->IFU flow; no redirect | None | `FauFTB` miss falls through; one prediction block accepted; no spurious redirect; metadata initializes once | Unexpected redirect, stale payload, or nonzero update side effect before first legal fire | Handshake checker, reset/first-request cover, predictor metadata scoreboard | `BPU.scala:381-455`; `FauFTB.scala:76-128`; `Frontend.scala:103-109` |
| `BPU_BASELINE_002` | Bimodal/base-table nominal hit | `TageBTable` initialized, no tagged provider yet | Reuse same PC twice so base table can be read then trained | Single stream | Base 2-bit counter saturates but does not wrap; second lookup sees updated polarity when no better provider exists | Counter wrap, wrong alias leak to other index, or read/write corruption | Storage conflict checker, counter saturation cover | `Tage.scala:155-215`; `Tage.scala:217-267` |
| `BPU_SAT_001` | FTQ full backpressure | FTQ occupancy near/full, consumer ready low | Hold `io.bpu_to_ftq.resp.ready=0` while continuing legal queries | Stage skew across S0-S3 | Payload and stage metadata hold stable; no stage advances past the blocked boundary; recovery resumes with same entry state | Payload drift, double-accept, or dropped prediction block | Handshake checker, occupancy checker, stage-valid scoreboard | `BPU.scala:381-455`; `NewFtq.scala:524-590`; `verification-special-attention.md` |
| `BPU_CONFLICT_001` | `Tage` provider/alternate disagreement | One PC has both provider and alternate candidate | Drive a history pattern where provider and alternate disagree, then train with resolved branch | Same-entry update/read overlap | `useAltOnNa` / provider selection matches code; weak or missing provider can defer to alternate; later training updates the correct longer-history entry | Wrong provider chosen silently, or allocate/replace overflows | Predictor metadata scoreboard, storage conflict checker | `Tage.scala:311-448`; `Tage.scala:904-1006`; `predictor-papers.md` |
| `BPU_CONFLICT_002` | FTB multi-hit / false-hit recovery | Two FTB ways can match same PC or a saved slot disagrees with `pdWb` | Construct same-set/different-way hits, then a predecode mismatch on the chosen slot | Update-read conflict plus redirect/repair | `multiHit` or false-hit is visible; one path continues, but repair/redirect is triggered; stale entry is not reinforced | Both hits treated as valid without repair, or false entry becomes sticky | Multi-hit checker, false-hit cover, redirect coverage | `FTB.scala:663-719`; `FTB.scala:849-878`; `add_verification_attention.py` |
| `BPU_CONFLICT_003` | ITTAGE indirect-target override | JALR target depends on path/context | Use same JALR PC with two different object/context histories | Tagged table aliasing | Longer-history provider selects the correct target; late mismatch causes S3 override/redirect | Wrong target accepted as final, or allocate/recovery fails | Indirect-target scoreboard, alias cover, redirect checker | `ITTAGE.scala:311-410`; `ITTAGE.scala:552-610`; `predictor-papers.md` |
| `BPU_CONFLICT_004` | RAS push/pop/cancel overlap | Call/return stream with speculative path possible | Issue call, then return, then inject redirect/cancel in the same recovery window | Spec/commit stack overlap | Spec push/pop is repaired by `s3_cancel`; recursion counter and near-overflow gating keep state legal; return target restores from snapshot | Wrong-path return survives cancel, underflow leaks old top, or overflow corrupts stack | History/RAS recovery checker, pointer-age checker | `newRAS.scala:494-508`; `newRAS.scala:511-565`; `newRAS.scala:594-617` |
| `BPU_RECOVERY_001` | S2/S3/backend redirect competition | Multiple redirect sources can fire close together | Arrange early direction mismatch, late target mismatch, and backend redirect in one window | Redirect-source overlap | One code-derived winner owns recovery target; younger/wrong-path state is repaired once; FTQ keeps the final correct boundary | Multiple redirects both commit state, or recovery target flips incoherently | Redirect checker, history recovery checker | `BPU.scala:827-883`; `NewFtq.scala:756-779` |
| `BPU_BOUNDARY_001` | Pointer wrap and age preservation | FTQ/RAS pointers near max, some live entries | Cross wrap boundaries while enq/deq or push/pop continue | Same-cycle allocate/reclaim and wrap | Wrap does not break age ordering, empty/full flags, or restore snapshot boundaries | Pointer inversion, double allocate, or stale age comparison | Pointer-age checker, wrap cover, occupancy checker | `NewFtq.scala:524-590`; `newRAS.scala:511-565`; `verification-special-attention.md` |
| `BPU_PROGRESS_001` | Redirect storm eventual progress | Build repeated wrong-path overrides, then stop perturbation | Alternate taken/not-taken and target changes until steady stream resumes | Sustained replay/redirect pressure | After fair inputs return, prediction block generation resumes within finite recovery window; no livelock in stage handoff | Infinite redirect loop, old request starvation, or permanent throughput collapse | Forward-progress checker, recovery throughput checker | `BPU.scala:381-455`; `BPU.scala:827-883`; `forwardProgressDrivers.md` |
| `BPU_OBS_001` | Cross-stage observability | Any of the above scenarios | Sample stage-valid, FTQ pointers, redirect cause, and predictor meta each cycle | None | Debug trace can pinpoint which stage first diverged; failure localizes to a single predictor or override point | Failure is visible only as final wrong PC with no stage trace | Predictor metadata scoreboard, pointer-age checker, waveform/debug plan | `verification-special-attention.md`; `add_verification_attention.py` |

## Directed Scenario Descriptions

### `BPU_BASELINE_001` - Cold-start baseline prediction
- Intent: establish the nominal no-conflict path from S0 lookup to FTQ acceptance.
- Code-derived trigger: `F_RESET_IDLE` and `F_FIRST_REQUEST` on `BPU`, `FauFTB`, and FTQ.
- Preconditions: reset released, FTQ empty, predictor tables cold or scanned to known reset state.
- Cycle-level stimulus: issue one legal PC, allow `FauFTB` to miss, let the chain proceed without backend redirect.
- Expected state transitions: S0 valid -> S1 response -> FTQ allocate -> no S2/S3 repair.
- Expected outputs: one valid prediction block, stable next PC, no redirect.
- Negative checks: no stale payload, no duplicate accept, no hidden update.
- Metrics: one-cycle stage-valid trace, allocation count 1, redirect count 0.
- Coverage bins: cold miss, first entry, one-live FTQ.
- Debug/waveform signals: `S0/S1 valid`, `resp.ready`, `ftq.alloc`, `redirect`.
- Source evidence: `BPU.scala:381-455`; `FauFTB.scala:76-128`; `Frontend.scala:103-109`.
- Evidence gaps: full local XiangShan source checkout is not present in this workspace; line anchors come from analyzer-derived mappings.

### `BPU_CONFLICT_001` - TAGE provider/alternate disagreement
- Intent: verify the code path where provider, alternate, and SC decide whether the final direction is flipped.
- Code-derived trigger: `TAGE_PROVIDER_ALT` and `SC_OVERRIDE_TAGE`.
- Preconditions: a PC/history pair that can hit multiple TAGE tables with different confidence.
- Cycle-level stimulus: create a weak provider, a viable alternate, and SC counters whose sum crosses the threshold in one direction.
- Expected state transitions: provider selection -> SC correction -> final direction commit -> possible redirect if early stage disagrees.
- Expected outputs: `final_taken` matches code priority, not the raw early guess.
- Negative checks: provider must not silently override a stronger SC correction; update must not train the wrong context.
- Metrics: provider hit, alternate hit, SC sum, final direction, redirect count.
- Coverage bins: weak provider, strong provider, alternate fallback, SC flip, SC confirm.
- Debug/waveform signals: `provider`, `alternate`, `sc_sum`, `final_taken`, `s2_redirect`.
- Source evidence: `Tage.scala:311-448`; `SC.scala:259-372`; `SC.scala:376-448`; `predictor-papers.md`.
- Evidence gaps: exact numeric thresholds and folded-history bit slices should be rechecked in the local Scala source before turning this into a directed test.

### `BPU_CONFLICT_002` - FTB multi-hit / false-hit recovery
- Intent: prove a multi-hit or false-hit path produces repair, not silent corruption.
- Code-derived trigger: `FTB_MULTI_HIT` and `FTB_FALSE_HIT`.
- Preconditions: two legal FTB ways can match the same PC, or the chosen slot can be disproved by predecode.
- Cycle-level stimulus: populate same-set entries, query them, then return a predecode mismatch on the selected slot.
- Expected state transitions: multi-hit detected -> chosen entry continues -> repair/redirect requested -> bad entry not reinforced.
- Expected outputs: one selected target plus repair metadata.
- Negative checks: no double training of both ways; no false entry survives as a clean hit.
- Metrics: hit vector, `multiHit`, false-hit flag, repair latency.
- Coverage bins: multi-hit, false-hit, stale-slot repair, replacement under pressure.
- Debug/waveform signals: `hitVec`, `multiHit`, `pdWb`, `update.valid`, `redirect`.
- Source evidence: `FTB.scala:663-719`; `FTB.scala:849-878`; `add_verification_attention.py`.
- Evidence gaps: way-selection policy should be confirmed directly in the local source if the test needs exact replacement expectations.

### `BPU_CONFLICT_003` - ITTAGE indirect-target override
- Intent: verify same JALR PC can resolve to different targets by path/context and that late override is legal.
- Code-derived trigger: `ITTAGE_TARGET_PROVIDER` and `ITTAGE_ALLOC_FULL`.
- Preconditions: one indirect branch with at least two contextual targets.
- Cycle-level stimulus: use two history contexts that point to different targets and then force a misprediction.
- Expected state transitions: longer-history provider selected -> target returned -> late mismatch repairs state and may allocate a longer-history entry.
- Expected outputs: correct `jalr_target`, repair metadata, no stale target leak.
- Negative checks: no empty-table underflow, no illegal allocation past capacity.
- Metrics: provider/alternate state, target mismatch count, allocation success/failure.
- Coverage bins: target alias, long-history resolve, allocation failure, aging.
- Debug/waveform signals: `provider`, `alternate`, `jalr_target`, `s3_redirect`, `update.valid`.
- Source evidence: `ITTAGE.scala:311-410`; `ITTAGE.scala:552-610`; `predictor-papers.md`.
- Evidence gaps: exact history folding should be captured from the local source if the test encodes indices mechanically.

### `BPU_CONFLICT_004` - RAS push/pop/cancel overlap
- Intent: ensure speculative stack repair matches the cancel/recover path.
- Code-derived trigger: `RAS_S3_CANCEL`, `RESOURCE_CONTENTION`, and `RAS_RECURSION_CTR`.
- Preconditions: speculative call/return sequence with snapshot available in FTQ.
- Cycle-level stimulus: issue call, then return, then inject redirect/cancel before commit repair completes.
- Expected state transitions: speculative push/pop -> cancel repair -> snapshot restore -> commit consolidation.
- Expected outputs: correct return target, valid stack top, no stale speculative top.
- Negative checks: no double pop, no underflow leak, no recursion counter wrap corruption.
- Metrics: `TOSR/TOSW/ssp/nsp` trace, near-overflow gating, recovery cycles.
- Coverage bins: empty pop, near-overflow, same-address recursion, cancel repair.
- Debug/waveform signals: `spec_push`, `spec_pop`, `s3_cancel`, `redirect_valid`, `top_snapshot`.
- Source evidence: `newRAS.scala:494-508`; `newRAS.scala:511-565`; `newRAS.scala:594-617`.
- Evidence gaps: exact stack-entry compression behavior should be verified in the local source before asserting compression-aware test stimuli.

### `BPU_RECOVERY_001` - S2/S3/backend redirect competition
- Intent: confirm a single winner owns recovery when multiple redirect sources coincide.
- Code-derived trigger: `C_REDIRECT_REDIRECT`.
- Preconditions: early-stage direction mismatch, later target mismatch, and backend redirect can overlap.
- Cycle-level stimulus: stagger one wrong direction, then one wrong target, then a backend redirect in the same recovery window.
- Expected state transitions: oldest legal recovery wins, younger wrong-path state is killed, FTQ boundary updates once.
- Expected outputs: one redirect target, one repaired history state, one preserved architectural boundary.
- Negative checks: no duplicate recovery, no split-brain history repair.
- Metrics: redirect cause ordering, overwrite count, boundary update count.
- Coverage bins: S2 vs S3 vs backend redirect, same-cycle overwrite, late cancel.
- Debug/waveform signals: `s2_redirect`, `s3_redirect`, `backend_redirect`, `ftq_boundary`, `history_repair`.
- Source evidence: `BPU.scala:827-883`; `NewFtq.scala:756-779`.
- Evidence gaps: exact priority encoding should be rechecked if the stimulus needs to discriminate every cause code.

### `BPU_BOUNDARY_001` - Pointer wrap and age preservation
- Intent: cover FTQ/RAS wraparound without corrupting age or empty/full logic.
- Code-derived trigger: `I_WRAP_PTR` plus FTQ pointer lifecycle.
- Preconditions: pointers close to maximum, a few live entries, and both enqueue and reclaim allowed.
- Cycle-level stimulus: continue legal enq/deq or push/pop through wrap.
- Expected state transitions: pointer wrap -> age ordering preserved -> entry reuse legal only once.
- Expected outputs: consistent empty/full, no inverted age compare, no stale entry resurrection.
- Negative checks: no double allocation, no skipped live entry, no incorrect wrap-phase toggle.
- Metrics: pointer values, phase/age bit, occupancy curve.
- Coverage bins: `max-1 -> max -> 0`, simultaneous reclaim/allocate, wrapped empty/full.
- Debug/waveform signals: `bpuPtr`, `ifuPtr`, `pfPtr`, `ifuWbPtr`, `commPtr`, `robCommPtr`, `TOSR/TOSW`.
- Source evidence: `NewFtq.scala:524-590`; `newRAS.scala:511-565`; `verification-special-attention.md`.
- Evidence gaps: exact pointer count widths are not repeated here; inspect local source if the test must model bit-accurate wrap limits.

### `BPU_PROGRESS_001` - Redirect storm eventual progress
- Intent: ensure repeated wrong-path activity does not create a permanent redirect loop.
- Code-derived trigger: `P_LIVELOCK_REPLAY_LOOP` and `PB_RECOVERY_THROUGHPUT`.
- Preconditions: predictors and FTQ already active under fair downstream behavior.
- Cycle-level stimulus: alternate taken/not-taken and target overrides until the stream stabilizes.
- Expected state transitions: repeated redirect -> recovery -> stable prediction block generation.
- Expected outputs: eventual legal prediction block and steady throughput.
- Negative checks: no livelock, no permanent throughput collapse, no starved old request.
- Metrics: redirect count, recovery latency, post-recovery IPC/throughput.
- Coverage bins: repeated redirect, recovery exit, fair sink, stable steady state.
- Debug/waveform signals: `redirect`, `ready`, `valid`, `history_update`, `ftq_accept`.
- Source evidence: `BPU.scala:381-455`; `BPU.scala:827-883`; `forwardProgressDrivers.md`.
- Evidence gaps: none beyond the local-source availability note above.

### `BPU_OBS_001` - Cross-stage observability
- Intent: make failures diagnosable at the stage that first diverged.
- Code-derived trigger: any of the above scenarios.
- Preconditions: waveform capture on BPU/FTQ/RAS signals.
- Cycle-level stimulus: sample stage valid, pointers, redirect causes, and metadata every cycle.
- Expected state transitions: the first violated stage is visible before the final wrong PC is committed.
- Expected outputs: stage-local anomaly, pointer trace, and repair marker.
- Negative checks: no hidden stage advance, no “final-only” diagnosis.
- Metrics: trace completeness, divergence cycle, recovery cycle.
- Coverage bins: stage mismatch, metadata mismatch, pointer mismatch, repair confirmation.
- Debug/waveform signals: `S0/S1/S2/S3 valid`, `resp.ready`, `multiHit`, `false hit`, `s2_redirect`, `s3_redirect`, FTQ pointers, RAS snapshot fields.
- Source evidence: `verification-special-attention.md`; `add_verification_attention.py`.
- Evidence gaps: if the waveform is used in a testbench, the exact probe names should follow the local module instance hierarchy.

## Checker Plan
| Checker | Type | Watches | Pass condition | Failure message |
| --- | --- | --- | --- | --- |
| Handshake checker | ready/valid/fire | `S0-S3`, FTQ enqueue/response, late update paths | Payload stable while stalled; no double accept; only fire advances state | `BPU payload advanced without fire or was duplicated` |
| Occupancy checker | queue/model | FTQ, RAS stack, predictor-entry usage | Empty/full/almost-full transitions match code; drain is legal | `BPU resource occupancy diverged from code model` |
| Pointer-age checker | pointer/phase | FTQ pointers, RAS pointers/top | Wrap preserves age order and legality | `Pointer wrap broke age or empty/full state` |
| FSM checker | state-machine | FTQ lifecycle, RAS repair, IFU interaction | Legal transitions only; flush/redirect priority preserved | `Illegal or missing FSM transition` |
| Arbiter checker | priority/fairness | redirect sources, multi-hit selection, conflicting update paths | One winner, defined loser behavior, no silent drop | `Conflict did not resolve with code-defined priority` |
| Storage conflict checker | same-entry RW | `TageBTable`, FTB/ITTAGE tables, RAS state | Read-old/read-new/bypass/stall behavior matches code | `Storage conflict behavior mismatched source` |
| Flush/replay checker | recovery | redirect, false-hit, cancel, late metadata | Killed work never trains or commits twice | `Wrong-path work leaked into visible state` |
| Predictor metadata scoreboard | cross-module | FTQ entry, predictor meta, redirect repair | Update/recovery uses the same entry and same metadata | `Predictor metadata mismatched FTQ entry` |
| History/RAS recovery checker | recovery | history, folded history, RAS snapshots | History and stack restore exactly after redirect/cancel | `History or RAS snapshot not restored` |
| Forward-progress checker | progress | repeated redirect, update stalls, full FTQ | Eventual useful work under fair sinks; no livelock/starvation | `BPU stopped making forward progress` |
| Performance checker | throughput | stall cycles, recovery delay, sustained prediction rate | Recovery completes and throughput returns to baseline window | `Prediction throughput did not recover` |

## Coverage Plan
| Coverpoint | Bins | Crosses | Source rationale |
| --- | --- | --- | --- |
| BPU stage alignment | S0/S1/S2/S3 valid, ready, fire | stage x redirect source | `verification-special-attention.md` BPU / Composer routing |
| Redirect priority | S2, S3, backend | target mismatch, history mismatch, same-window overwrite | `BPU.scala:827-883`; `NewFtq.scala:756-779` |
| FTQ occupancy | empty, one-live, almost-full, full, wrap | allocate/reclaim vs redirect | `NewFtq.scala:524-590`; `verification-special-attention.md` |
| Predictor alias | same index/different tag; same index/same tag/different context | lookup/update/replace | `Tage.scala`; `FTB.scala`; `ITTAGE.scala` |
| Counter/threshold boundary | min, weak, strong, max, saturate | SC override / TAGE base | `Tage.scala:155-215`; `SC.scala:259-448` |
| RAS repair | push, pop, cancel, near-overflow, recursion | snapshot restore vs redirect | `newRAS.scala:494-617` |
| Multi-hit / false-hit | no-hit, single-hit, multi-hit, false-hit | update repair path | `FTB.scala:663-878` |
| Recovery throughput | pre-storm, storm, post-storm | stable stream after redirect | `BPU.scala:381-455`; `BPU.scala:827-883` |

## Waveform / Debug Observation Plan
| Signal group | What to inspect | Why it matters |
| --- | --- | --- |
| `BPU S0-S3` | `valid`, `ready`, `fire`, stage payload hold | Verify stage skew and no premature advancement |
| FTQ pointers | `bpuPtr`, `ifuPtr`, `pfPtr`, `ifuWbPtr`, `commPtr`, `robCommPtr` | Verify wrap, reclaim, overwrite, and recovery boundaries |
| Redirects | `s2_redirect`, `s3_redirect`, backend redirect cause | Verify one winner and one repair path |
| Predictor meta | provider/alternate IDs, `multiHit`, false-hit, target fields | Prove update uses the right entry and the right context |
| RAS state | stack top, speculative/commit snapshots, near-overflow gating | Prove call/return repair and underflow/overflow legality |
| FTQ / IBuffer / IFU backpressure | `resp.ready`, enqueue/dequeue, flush kill | Verify the chain does not lose payload under stall |

## 验证特别注意
| Verification ID | Risk / invariant | Directed stimulus | Expected observation | Required checker / coverage |
| --- | --- | --- | --- | --- |
| `F_RESET_IDLE` | 复位后不能泄露未初始化预测 | reset 释放前后持续查询 PC | 首个有效预测块和首个更新都来自合法 entry | FSM checker, reset/first-request cover |
| `F_FIRST_REQUEST` | 第一个请求必须建立正确的 active transition | 空表/冷启动后发首个合法 PC | 无陈旧 payload、无重复训练 | Handshake checker, first-entry cover |
| `F_HOLD_BACKPRESSURE` | `valid && !ready` 期间 payload 必须稳定 | 拉低 FTQ/IFU/IBuffer ready | stage 不误推进，payload 不漂移 | Handshake checker, payload stability |
| `F_REQ_AND_FLUSH` | 接受与 flush/redirect 竞争时必须按代码优先级处理 | 在 lookup/update 同拍注入 redirect | 错误路径不训练、不提交 | Flush/replay checker, metadata scoreboard |
| `F_RESP_AND_REPLAY` | 完成与 replay/retry 竞争时只能有一次合法更新 | 让晚级错误和重试同时出现 | 只发生一次合法完成或一次合法重试 | Flush/replay checker |
| `C_SAME_ENTRY_RW` | 同 entry 读写必须符合旁路/旧值/新值规则 | lookup 与 update 同 index/way | 读旧/读新/旁路/阻塞与代码一致 | Storage conflict checker |
| `C_MULTI_WRITE_SAME_ENTRY` | 多写同 entry 不能静默丢失请求 | 同拍多个有效更新候选 | 有优先级或断言；未胜出请求有定义好的结局 | Multi-write checker |
| `C_REDIRECT_REDIRECT` | 多个 redirect 源必须收敛成一个恢复目标 | 同窗口制造 S2/S3/backend redirect | 只有一个 winner，history 只修复一次 | Redirect checker, recovery scoreboard |
| `RESOURCE_CONTENTION` | 资源填满后必须能排空 | FTQ / RAS / 表项 / 队列饱和后释放 sink | 满/空恢复正确，持续吞吐可回升 | Occupancy checker, performance checker |
| `I_WRAP_PTR` | 环形指针必须正确回绕 | 让 FTQ 和 RAS 指针跨最大值 | phase/age 不反转，old/new 顺序正确 | Pointer-age checker |
| `H_SAME_INDEX_DIFF_TAG` | alias 不能伪造成真 hit | 构造同 index 不同 tag 的 PC/历史 | 只能命中真实 tag；无 tag 表仅允许方向 alias | Index/hash checker |
| `P_LIVELOCK_REPLAY_LOOP` | 重复 redirect/replay 不能卡死 | 连续制造方向和 target 的晚级差异 | 在公平条件下有限周期恢复稳定 | Forward-progress checker |
| `PB_RECOVERY_THROUGHPUT` | 恢复后吞吐应回到基线窗口 | 饱和后注入 redirect，再恢复稳定流 | 旧路径被杀干净，持续吞吐恢复 | Performance checker |

## Evidence Gaps
| Gap | Next file/search/action |
| --- | --- |
| 本 workspace 没有直接挂出的 XiangShan checkout | 在完整本地 checkout 上读取 `src/main/scala/xiangshan/frontend/*.scala` 并用 `nl -ba` 复核 line refs |
| 目前的 line refs 来自 analyzer 的已整理锚点 | 用 `analyze-xiangshan-kunminghu` 重新对 `BPU.scala`, `FTB.scala`, `Tage.scala`, `SC.scala`, `ITTAGE.scala`, `newRAS.scala`, `NewFtq.scala` 做一次源码复核 |
| `TageBTable`、`useAltOnNa`、`multiHit` 等关键判定的精确 bit-slice 未重摘录 | 复查实际 Scala 源码中的 index/tag/history fold 定义，再把刺激转换成脚本化地址组 |
| `Bim.scala` 在当前实现中被块注释 | 若后续 commit 恢复独立 Bim，再补单独的 baseline/alias 场景 |

