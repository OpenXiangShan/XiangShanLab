### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

The TAGE table can expose a newly written entry/tag/taken counter together with a stale useful counter. The stale useful value is not confined to the raw SRAM response: in the reproduced waveform it is consumed by valid FTQ metadata and later by the BPU train metadata.

The likely root cause is that `TageTable` stores entry state and useful-counter state in separate SRAM arrays with separate write-buffer paths. Entry writes and useful-counter writes for the same logical update can drain in different cycles. A read to the same table/bank/way/set between those two drains can observe a mixed state:

```text
new entry/tag/takenCtr from the entry SRAM
old usefulCtr from the useful-counter SRAM
```

The attached PoC uses a runtime-controlled seed to steer a compact branch pattern into this condition. The decisive reproduced `SECRET_VALUE=1` run has this summary:

```text
bad_read_observations             = 2
bad_read_any_meta_consumed        = 2
bad_read_to_ftq_meta_consumed     = 2
bad_read_train_meta_consumed      = 2
split_read_useful_mismatch        = 4
result_delta                      = 106
recovered_bit                     = 1
```

The first stale-useful read is:

```text
entry enqueue time      = 19086
entry commit time       = 19111
read request time       = 19151
read response time      = 19152

table                  = 0
bank                   = 0
way                    = 0
setIdx                 = 22
tag                    = 6
observed takenCtr      = 3
expected usefulCtr     = 1
observed usefulCtr     = 0
```

The same stale useful value is then propagated into prediction metadata:

```text
to_ftq metadata:
  time                 = 19178
  lane                 = 2
  providerTableIdx     = 0
  providerWayIdx       = 0
  providerTakenCtr     = 3
  providerUsefulCtr    = 0
  useProvider          = 1

train metadata:
  time                 = 19204
  lane                 = 2
  providerTableIdx     = 0
  providerWayIdx       = 0
  providerTakenCtr     = 3
  providerUsefulCtr    = 0
  useProvider          = 1
```

The same run also stores the guest-visible timing result:

```text
result_delta store:
  time                 = 65298
  addr                 = 0x80080000
  data                 = 106

recovered_bit store:
  time                 = 65310
  addr                 = 0x80080008
  data                 = 1
```

I also ran control cases with the same PoC family:

```text
runtime secret = 0, seed = 0x13579d00:
  bad_read_observations         = 0
  bad_read_any_meta_consumed    = 0
  bad_read_to_ftq_meta_consumed = 0
  bad_read_train_meta_consumed  = 0
  result_delta                  = 138
  recovered_bit                 = 0

runtime secret = 1, alternate seed = 0x13579d11:
  result_delta                  = 137
  recovered_bit                 = 0
```

The control cases are included to show that the endpoint is not just a generic `secret=1` artifact. The signal-level chain above is from one wide `SECRET_VALUE=1` waveform that covers both the early stale-useful metadata event and the later `result_delta` / `recovered_bit` stores.

### Impact:

- Predictor correctness: TAGE can make and train on a provider entry whose tag/taken counter and useful counter do not represent one coherent logical table state.
- Frontend metadata integrity: the stale useful value is accepted by valid `to_ftq` metadata and valid train metadata, so the mixed state can influence later predictor updates rather than remaining an isolated SRAM read artifact.
- Timing/security primitive: in the reproduced bare-metal PoC, a runtime-controlled seed selects whether the stale-useful metadata chain appears, and the guest later stores a different timing-derived `recovered_bit`. This demonstrates a one-bit same-program timing channel primitive.
- Scope of claim: The demonstrated impact is a signal-confirmed predictor-state consistency bug with a same-program timing endpoint and plausible speculative-execution/security relevance when predictor state is shared or attacker-controllable.

### Expected behavior

A TAGE read should not observe a logically mixed entry/useful state from one partial update. If a TAGE entry update and the corresponding useful-counter update refer to the same table/bank/way/set, the design should either make them visible atomically to reads or ensure that prediction metadata cannot consume the mixed state.

At minimum, this unsafe combination should not be possible:

```text
entry write for table/bank/way/set has committed
matching useful-counter write has not committed yet
readResp tag matches the new entry
readResp usefulCtr is the old value
FTQ metadata consumes providerUsefulCtr from that read
BPU train metadata consumes providerUsefulCtr from that read
```

Possible fix directions include holding the entry update until the matching useful-counter update can be made visible, adding useful-counter write-buffer bypassing on reads, or otherwise qualifying metadata consumption so that a split entry/useful update cannot be forwarded as a valid provider.

### Environment

- XiangShan branch: `kunminghu-v3`
- Checkout commit: `96c3f568f943a096ffd3d712dc6f462ac4b1ba33`
- Run mode: `--no-diff`

### To Reproduce

The attachment [xiangshan-tage-entry-useful-split-stale-meta-poc.zip](https://github.com/user-attachments/files/29434233/xiangshan-tage-entry-useful-split-stale-meta-poc.zip) contains the bare-metal PoC, run script, VCD monitors, compact reproduced JSON outputs, and an attachment manifest. The large VCD files are not included; the commands below regenerate them.

Run the wide `SECRET_VALUE=1` waveform that contains both the stale-useful metadata event and the guest-visible stores:

```bash
cd xiangshan-tage-entry-useful-split-stale-meta-poc

RUNTIME_SECRET=1 \
SECRETS=1 \
KEEP_VCD=1 \
WARM_ITERS=512 \
PHASE_ITERS=1 \
RECOVERY_THRESHOLD=120 \
INVERT_RECOVERY=1 \
CYCLES=40000 \
WAVE_BEGIN=7000 \
WAVE_END=40000 \
TIMEOUT_SECS=360 \
SUMMARY=phased_seed_probe_runtime_p1_wide_7k40k_secret1_summary.jsonl \
EMU=/path/to/XiangShan/build_vcd/emu \
./run_phased_seed_probe.sh
```

Expected summary:

```text
bad_read_observations         = 2
bad_read_to_ftq_meta_consumed = 2
bad_read_train_meta_consumed  = 2
delta                         = 106
recovered                     = 1
```

Run the correlation extractor on the regenerated VCD:

```bash
python3 ./correlate_tage_split_prediction.py \
  phased_seed_secret1_w512_p1_7000_40000.vcd \
  --strict-json phased_seed_secret1_w512_p1_7000_40000.tage_split.monitor.json \
  --json-out phased_seed_secret1_w512_p1_7000_40000.bad_read.correlation.json \
  --event-source bad-read \
  --before-cycles 3 \
  --after-cycles 70
```

Expected correlation:

```text
readResp at 19152:
  table=0 way=0 tag=6 takenCtr=3 usefulCtr=0
  tag_matches_entry_commit=true
  useful_matches_expected=false

to_ftq metadata at 19178:
  providerTableIdx=0 providerWayIdx=0 providerTakenCtr=3 providerUsefulCtr=0 useProvider=1

train metadata at 19204:
  providerTableIdx=0 providerWayIdx=0 providerTakenCtr=3 providerUsefulCtr=0 useProvider=1
```

### Additional context

Relevant source structure in `src/main/scala/xiangshan/frontend/bpu/tage/TageTable.scala`:

- `entrySram` stores `TageEntry`.
- `usefulCtrSram` stores the useful counters.
- `entryWriteBuffers` buffers entry writes per bank.
- `usefulCtrWriteBuffers` buffers useful-counter writes per bank and way.
- Entry writes and useful-counter writes are drained through separate paths, each blocked by its own SRAM read activity.
- `io.readResp` later combines `entrySram` data and `usefulCtrSram` data into one table response.

Relevant downstream source structure in `src/main/scala/xiangshan/frontend/bpu/tage/Tage.scala`:

- table responses select `result.usefulCtr` from `tableReadResp.usefulCtrs`;
- provider metadata records `providerUsefulCtr`;
- later training uses this metadata.

This means the mixed read response can become architectural predictor metadata inside the frontend, not only a transient internal SRAM artifact.
