# Frontend BPU

## Scope

- Source: OpenXiangShan/XiangShan `kunminghu-v2`, commit `52262f303fc06daf84cdab7011d59b7df65ce7e8`.
- Source root used in citations: `src/main/scala/xiangshan`.
- Files read: `Parameters.scala`, `frontend/BPU.scala`, `frontend/Composer.scala`, `frontend/FTB.scala`, `frontend/FauFTB.scala`, `frontend/Tage.scala`, `frontend/SC.scala`, `frontend/ITTAGE.scala`, `frontend/newRAS.scala`, `frontend/Bim.scala`.
- Weekly sync: skipped by helper because last sync was 2.24 days old.
- Design docs: local `XiangShan-Design-Doc` checkout was not found under `xiangshanlab_home`; theory context uses local XiangShanLab course docs, effective behavior uses source.

## Role and Boundary

`Predictor` in `frontend/BPU.scala` owns the frontend prediction pipeline, global history vector, folded-history movement, redirect repair, and the response channel to FTQ. It delegates actual prediction components to `Composer`, whose default component chain is configured in `Parameters.scala`: `FauFTB -> Tage_SC -> FTB -> ITTage -> RAS`, with `RAS` output as the final response (`Parameters.scala:124-143`).

`Bim.scala` is not in this chain; its content is commented out and is not effective in this commit.

## Theory-to-Code Mapping

| Concept | Course source | Code artifact | Concrete signal/state | Mapping |
| --- | --- | --- | --- | --- |
| Control hazard | `入门-超标量乱序处理器基础知识/3.结构冲突vs.数据冲突vs.控制冲突.md` | `Predictor` | `s2_redirect_dup`, `s3_redirect_dup`, `redirect_req` | Later prediction stages override earlier target/direction and flush wrong-path frontend state. |
| Frontend throughput | `初级-高性能香山处理器实现和原理/1.高性能乱序流水线经典划分.md` | `io.bpu_to_ftq.resp` | `valid/ready`, `ftqFullStall` | BPU can only retire a prediction block into FTQ when FTQ accepts it. |
| Speculation state | same | global history | `ghv`, `CGHPtr`, folded histories | Prediction-time history is updated speculatively and repaired on redirect. |

## Source Evidence

| Topic | Source lines | Core code | What it proves |
| --- | --- | --- | --- |
| Predictor chain | `Parameters.scala:124-143` | `val preds = Seq(uftb, tage, ftb, ittage, ras)` | Effective default order and final output. |
| Base predictor IO | `frontend/BPU.scala:141-170` | `in`, `out`, `update`, `redirect`, stage fire/ready | Common predictor interface. |
| S0-S3 pipeline valid/ready | `frontend/BPU.scala:381-455` | `s1_valid_dup`, `s2_valid_dup`, `s3_valid_dup`, `resp.valid` | Prediction pipeline and FTQ backpressure. |
| Global history storage | `frontend/BPU.scala:336-355` | `ghv`, `getHist(ptr)` | Circular global history vector and read function. |
| S2 override | `frontend/BPU.scala:698-725` | compare previous S1 prediction with S2 prediction | S2 can redirect when FTB/TAGE-level result differs. |
| S3 override | `frontend/BPU.scala:827-854` | compare S3 with previous S2 prediction | S3 can redirect on target/taken/multi-hit/fall-through differences. |
| Backend/FTQ redirect repair | `frontend/BPU.scala:915-1050` | `do_redirect`, `updated_ptr`, `updated_fh`, `redirect_ghv_wens` | Backend redirect restores prediction PC and history. |

## Pipeline and Handshake

`Predictor` has three prediction stages after S0. S0 fires only when predictor components are ready and the S1 slot can accept new work (`frontend/BPU.scala:391-397`). S1 can move to S2 only when S2 is ready and FTQ is ready (`frontend/BPU.scala:398-405`); this ties prediction throughput to `io.bpu_to_ftq.resp.ready`. S2 moves to S3 when S3 is ready (`frontend/BPU.scala:407-414`). S3 consumes itself every cycle while valid (`frontend/BPU.scala:437-447`).

`io.bpu_to_ftq.resp.valid` is asserted for normal S1->S2 output or override redirects from S2/S3 (`frontend/BPU.scala:452-456`). Therefore, the FTQ sees both ordinary prediction blocks and correction events through one response channel.

## Algorithms

### Component Composition

`Composer` forwards the common input, fire, redirect, control, and update signals into every component (`frontend/Composer.scala:37-56`). It concatenates each component's `last_stage_meta` into a single FTQ metadata word (`frontend/Composer.scala:35-70`) and splits it in reverse order on update (`frontend/Composer.scala:72-77`). This is why each predictor can train from its own metadata without owning FTQ storage.

### Redirect/Override Priority

S2 redirect compares S1's previous prediction against S2's richer prediction: target, last branch position, taken bit, and taken offset (`frontend/BPU.scala:606-635`, `698-705`). S3 redirect compares real branch-taken masks, targets, JALR target, fall-through error, and FTB multi-hit (`frontend/BPU.scala:827-854`). Backend/FTQ redirect has separate repair logic and writes the redirected target/folded-history/history pointer with higher-priority generator registrations (`frontend/BPU.scala:915-1050`).

### History Update

`ghv` is a circular vector. Prediction stages calculate possible next pointers for `0..numBr` branches and select by `lastBrPosOH` (`frontend/BPU.scala:530-544`, `639-654`, `741-756`). Each stage produces write enables/data for the bits it speculatively shifts (`frontend/BPU.scala:561-595`, `671-725`, `773-883`). Backend redirect recomputes folded history from the saved `histPtr` and resolved CFI update (`frontend/BPU.scala:939-963`) and writes corrected history bits (`frontend/BPU.scala:964-1050`).


## Algorithm Example Walkthrough

Example input: S0 fetch PC is `0x8000_1000`, current global-history pointer is `H`, and the predictor chain first emits a fast S1 target `0x8000_1040`. One cycle later S2 computes a richer target `0x8000_1080` from FTB/TAGE metadata.

1. Component chain setup: `Parameters.scala:126-143` instantiates `FauFTB`, `Tage_SC`, `FTB`, `ITTage`, and `RAS`, then wires each component's `resp_in(0)` from the previous component. In this example, `FauFTB` creates the early S1 guess, `Tage_SC` may change direction, `FTB` may change target/branch-slot metadata, `ITTAGE` may change indirect target, and `RAS` may change return target.
2. S0/S1 movement: `frontend/BPU.scala:391-405` lets S0 fire only when S1 is ready and components are ready; S1 advances to S2 only if FTQ is ready. If `io.bpu_to_ftq.resp.ready=false`, this exact example stays in S1 and `ftqFullStall` is marked by `frontend/BPU.scala:1090-1156`.
3. S2 override decision: `frontend/BPU.scala:606-635` builds a comparison vector for target, branch count, direction, and CFI index. `frontend/BPU.scala:698-705` sets `s2_redirect := s2_fire && diff`. With S1 target `0x8000_1040` and S2 target `0x8000_1080`, `targetDiff` is true, so `s2_redirect_dup(0)` is asserted.
4. Next PC/history repair: `frontend/BPU.scala:706-725` registers the S2 target, folded history, and history pointer into the physical priority generators. The output effect is that the next S0 PC becomes `0x8000_1080`, and younger S1 state is flushed through `frontend/BPU.scala:385-390` and `416-431`.
5. Backend redirect case: if backend later resolves the branch target as `0x8000_10c0`, `frontend/BPU.scala:915-1050` uses `cfiUpdate.histPtr`, `shift`, and `taken` to recompute folded history and global-history bits. That backend redirect wins over ordinary prediction because it registers `redirect_target`, `redirect_FGHT`, and `redirect_GHPtr` into the same PC/history generators with its own priority slot.

Downstream effect: FTQ receives either a normal prediction block or an override event through `io.bpu_to_ftq.resp` (`frontend/BPU.scala:452-458`). The example changes both `resp.bits.s2.hasRedirect` and the next fetch PC.

## Stage-by-Stage Algorithm

| Stage | Inputs | Algorithm and state | Ready/stall | Output | Source lines |
| --- | --- | --- | --- | --- | --- |
| S0 | Generated PC, folded history, global history pointer | Drives `predictors.io.in.bits.s0_pc`, `folded_hist`, `s1_folded_hist`, and `ghist`; `getHist(ptr)` reads `ghv` by circular pointer. | `s0_fire := s1_components_ready && s1_ready`. | Component lookup requests and S1 PC/history registers. | `frontend/BPU.scala:336-369`, `391-397` |
| S1 | S0 PC/history plus fast predictor outputs | Valid bit is set by S0 fire unless redirect/flush clears it; S1 can update speculative history from S1 prediction. | S1 can advance only when S2 components and FTQ are ready. | S1 prediction may be sent to FTQ and forms previous-pred info for S2 comparison. | `frontend/BPU.scala:416-422`, `528-595`, `889-895` |
| S2 | S1 registered PC/history and richer component outputs | Compares S1 previous prediction with S2 prediction; computes S2 predicted history/folded history. | `s2_ready := s2_fire || !s2_valid`; S2 flush comes from S3/backend redirect. | `s2_redirect`, S2 target/history generator entries, FTQ S2 override metadata. | `frontend/BPU.scala:424-435`, `638-725`, `897-898` |
| S3 | S2 registered prediction | Compares S3 prediction with previous S2 prediction; detects target, taken-mask, fall-through, and FTB multi-hit differences. | S3 consumes valid every cycle unless flushed by backend redirect. | `s3_redirect`, S3 target/history generator entries, FTQ S3 override metadata. | `frontend/BPU.scala:437-455`, `740-883`, `899-903` |
| Update | FTQ update | Sends update to Composer; recomputes update PC with segmented address register; supplies true history from `histPtr`. | Component-local ready can block S1 through Composer. | Predictor table training. | `frontend/BPU.scala:905-913`, `frontend/Composer.scala:72-77` |
| Redirect/recovery | FTQ/backend redirect | Reconstructs corrected pointer/folded history from `cfiUpdate`, writes global-history bits, and registers redirect target. | Redirect flushes S1/S2/S3 valid bits. | Next S0 PC/history becomes resolved redirect state. | `frontend/BPU.scala:378-389`, `915-1075` |

## Redirect Signal Generation

| Signal | Producer and condition | Stage | Repaired state | Consumer/effect | Source lines |
| --- | --- | --- | --- | --- | --- |
| `s2_redirect_dup` | `preds_needs_redirect_vec_dup(previous_s1_pred_info, resp.s2)` detects target, branch-position, taken, or CFI-index mismatch. | S2 | PC, folded history, global-history pointer, history bits. | Flushes younger stage and sends S2 override to FTQ. | `frontend/BPU.scala:606-635`, `698-725` |
| `s3_redirect_dup` | S3 taken-mask differs from S2, target differs, fall-through error, or FTB multi-hit. | S3 | PC/history generators updated from S3 prediction. | Sends S3 override to FTQ and redirects fetch. | `frontend/BPU.scala:827-854`, `863-883` |
| Backend/FTQ redirect | `io.ftq_to_bpu.redirect.valid`. | Recovery path | Restores resolved target, folded history, global-history pointer and bits. | Flushes S1/S2/S3 and overrides next S0. | `frontend/BPU.scala:378-389`, `915-1050`, `1060-1075` |
| FTQ full stall, not redirect | `!io.bpu_to_ftq.resp.ready`. | S1/S2 boundary | No repair; holds pipeline. | Blocks S1 advance and marks topdown stall. | `frontend/BPU.scala:405`, `1090-1156` |

Example: if S1 predicts target `0x1040` and S2 predicts `0x1080`, `targetDiff` in `preds_needs_redirect_vec_dup` is true, so `s2_redirect_dup(0)` is asserted. The S2 target is registered into `npcGen`, and `s1_flush` becomes true through the flush chain.

## Predictor Relationship

The effective Kunminghu frontend predictor chain is not a set of independent predictors voting in parallel. `Parameters.scala:124-143` constructs `FauFTB`, `Tage_SC`, `FTB`, `ITTage`, and `RAS`, connects them as `resp_in -> uftb -> tage -> ftb -> ittage -> ras`, and returns `ras.io.out` as the final composed prediction. `Bim.scala` is not part of this chain in this commit; its effective role is replaced by the `TageBTable` base table inside `Tage.scala:143-270`.

| Component | Relationship to the chain | What it contributes | How disagreement is handled | Source lines |
| --- | --- | --- | --- | --- |
| `BPU` / `Predictor` | Owns the pipeline, history state, redirect generators, and FTQ response. It does not compute every prediction locally; it drives `Composer`. | Shared PC/history inputs, stage fire/ready, global-history/folded-history repair, and final `io.bpu_to_ftq.resp`. | Compares later-stage composed predictions against earlier predictions and emits S2/S3/backend redirects. | `frontend/BPU.scala:381-455`, `606-635`, `698-725`, `827-854`, `915-1050` |
| `Composer` | Broadcasts common inputs/control to every component and gathers the final response from the configured chain. | Shared `s0_pc`, folded history, global history, stage fires, redirect, control, update, and concatenated `last_stage_meta`. | All components see the same redirect/update event; `io.in.ready` is the AND of component readiness, so one blocked predictor stalls the chain. | `frontend/Composer.scala:22-77` |
| `FauFTB` / uFTB | First and fast predictor in the chain. Its output feeds `Tage_SC`, and its entry/hit information also feeds `FTB`. | Early target/fall-through/entry information for fast S1 prediction. | Later `FTB`/`Tage`/`ITTAGE`/`RAS` output can refine it; BPU turns the difference into S2/S3 redirect. | `Parameters.scala:127-139`, `frontend/Composer.scala:25-31`, `frontend/FauFTB.scala:76-128` |
| `Tage_SC` | Receives uFTB output, then updates conditional branch direction before FTB/ITTAGE/RAS see the prediction. | TAGE direction plus SC correction over the base table and tagged tables. | If direction or first-taken branch differs from the fast prediction, BPU S2 comparison observes `takenDiff`/`lastBrPosOHDiff`. | `Parameters.scala:128-140`, `frontend/Tage.scala:778-846`, `frontend/SC.scala:259-372` |
| `FTB` | Receives TAGE-refined prediction and uFTB cached entry/hit information. | Direct branch/JAL target entries, branch-slot metadata, fall-through, multi-hit detection. | Target, CFI slot, fall-through, or multi-hit differences become S2/S3 redirect causes. | `Parameters.scala:126-138`, `frontend/FTB.scala:683-811`, `frontend/BPU.scala:827-854` |
| `ITTAGE` | Receives FTB output and specializes the JALR/indirect target path. | Indirect target override and ITTAGE metadata for later training. | If the indirect target changes after earlier stages, BPU observes target/JALR target differences and redirects. | `Parameters.scala:130-141`, `frontend/ITTAGE.scala:418-470`, `frontend/BPU.scala:827-854` |
| `RAS` / `newRAS` | Last component in the chain and therefore the final returned response. | Return target prediction, speculative stack pointer/top metadata, cancel/recovery behavior. | RAS target or cancel effects are reflected in final S3 prediction; backend redirect repairs RAS/history state through shared redirect/update paths. | `Parameters.scala:129-143`, `frontend/newRAS.scala:696-706`, `frontend/Composer.scala:37-56` |

Metadata and training are also chained. `Composer` concatenates each component's `last_stage_meta` in chain order (`frontend/Composer.scala:58-70`), FTQ stores that combined metadata (`frontend/NewFtq.scala:637`), and update walks components in reverse order to split the metadata back to each predictor (`frontend/Composer.scala:72-77`). This means a single FTQ update can train TAGE counters, FTB entries, ITTAGE target entries, and RAS state with the metadata each component produced during prediction.

Cross-predictor example: suppose uFTB predicts fall-through for PC `0x8000_1000`, but TAGE later marks branch slot 0 taken and FTB supplies target `0x8000_1080`. The chain first carries the uFTB result into `Tage_SC` and `FTB` (`Parameters.scala:136-140`). BPU records the earlier S1 prediction, compares it with the richer S2 composed response, and detects direction/target/CFI-index differences (`frontend/BPU.scala:606-635`, `698-705`). It then registers the S2 target, folded history, and global-history pointer into the next-PC/history generators (`frontend/BPU.scala:707-725`). If an even later RAS or ITTAGE target differs in S3, the S3 comparison checks branch-taken mask, target, JALR target, fall-through error, and FTB multi-hit before generating `s3_redirect` (`frontend/BPU.scala:827-854`).

## Scenarios

| Scenario | Trigger | Code | Winner/effect | Blocked/loser |
| --- | --- | --- | --- | --- |
| FTQ full | `!io.bpu_to_ftq.resp.ready` | `frontend/BPU.scala:405`, `1093-1156` | S1 cannot advance; topdown marks FTQ full stall | New prediction blocks held in earlier stages |
| S2 override | S2 differs from S1 | `frontend/BPU.scala:698-725` | S2 target/history registered as next PC/history | S1 path is flushed by downstream flush chain |
| S3 override | S3 differs from S2 or FTB multi-hit/fall-through error | `frontend/BPU.scala:827-883` | S3 target/history registered | Younger prediction is invalidated |
| Backend redirect | `io.ftq_to_bpu.redirect.valid` | `frontend/BPU.scala:378-389`, `915-1050` | Redirect target and resolved history win | S1/S2/S3 valid bits flushed |
| Predictor write/read conflict | Component SRAM write blocks local read ready | e.g. `frontend/Tage.scala:1004-1006` | Component ready drops | BPU `s1_ready` drops through Composer |

## Diagrams

```mermaid
flowchart LR
  S0[PC + speculative history] --> UFTB[FauFTB]
  UFTB --> TAGE[Tage_SC]
  TAGE --> FTB[FTB]
  FTB --> ITTAGE[ITTAGE]
  ITTAGE --> RAS[RAS]
  RAS --> FTQ[FTQ response]
  FTQ -->|update/redirect| BPU[Predictor history repair]
  BPU --> S0
```

```waveform-draw
{ "signal": [
  {"name":"s0_fire","wave":"01010"},
  {"name":"s1_valid","wave":"00101"},
  {"name":"ftq_ready","wave":"11101"},
  {"name":"resp_valid","wave":"00010"},
  {"name":"redirect","wave":"00010"}
] }
```

