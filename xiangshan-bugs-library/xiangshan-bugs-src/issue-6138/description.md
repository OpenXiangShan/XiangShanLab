### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A branch that is on the wrong path inside the same FTQ block can still reach the FTQ-to-BPU training path and update predictor state. A later architectural probe of the same branch PC can observe that predictor state through a branch redirect, `mcycle` timing, and committed branch-mispredict counters.

The attached PoC creates one fetch block with:

1. an older branch at `0x8000004c` that is architecturally taken to `0x80000060`;
2. a younger branch at `0x80000050` that lies between the older branch and its taken target, so it is on the path skipped by the older branch;
3. a victim-controlled secret bit that determines the younger branch direction during the training phase;
4. an attacker probe that later executes the same branch PC with the actual direction fixed to not-taken.

Observed behavior:

* For `SECRET_VALUE=0`, the wrong-path train packet contains the probe branch at `0x80000050` with `taken=0`, and the later attacker probe sees no redirect.
* For `SECRET_VALUE=1`, the wrong-path train packet contains the same probe branch at `0x80000050` with `taken=1`, and the later attacker probe sees a redirect to the not-taken fallthrough.
* The attacker code measures the probe with `mcycle`; the measured delta is `29` cycles for `SECRET_VALUE=0` and `39` cycles for `SECRET_VALUE=1`.
* The guest then thresholds the measured delta at `34` cycles and stores `recovered_bit=0` for `SECRET_VALUE=0`, `recovered_bit=1` for `SECRET_VALUE=1`.
* A no-wave emulator run also shows the effect as one extra committed conditional branch mispredict for `SECRET_VALUE=1`.

The reproduced summary is:

```json
{
  "guest_threshold_recovers_secret_bit": 1,
  "secret_dependent_attacker_probe_redirect": 1,
  "secret_dependent_mcycle_delta": 1,
  "secret_dependent_wrongpath_training": 1,
  "secret_recovery_confirmed": 1,
  "security_primitive_confirmed": 1
}
```

The decisive `SECRET_VALUE=1` attacker-probe redirect rows are:

```json
[
  {
    "time": 17688,
    "pc": 2147483728,
    "target": 2147483732,
    "offset": 1,
    "is_mispredict": 1
  },
  {
    "time": 17689,
    "pc": 2147483728,
    "target": 2147483732,
    "offset": 1,
    "is_mispredict": 1
  }
]
```

Decoded:

* `pc=2147483728` is `0x80000050`, the attacker probe branch.
* `target=2147483732` is `0x80000054`, the not-taken fallthrough.
* The attacker sets the branch condition to not-taken before probing. A redirect to fallthrough means the BPU predicted taken.

The guest-visible timing and recovery stores are:

```json
{
  "SECRET_VALUE=0": {
    "result_delta_addr": "0x80080000",
    "recovered_bit_addr": "0x80080008",
    "mcycle_delta": 29,
    "recovered_bit": 0
  },
  "SECRET_VALUE=1": {
    "result_delta_addr": "0x80080000",
    "recovered_bit_addr": "0x80080008",
    "mcycle_delta": 39,
    "recovered_bit": 1
  }
}
```

The no-wave PERF-counter observable is:

```json
{
  "commit_branch_mispredicts": {
    "SECRET_VALUE=0": 14,
    "SECRET_VALUE=1": 15,
    "delta": 1
  },
  "commit_branch_mispredicts_type_conditional": {
    "SECRET_VALUE=0": 3,
    "SECRET_VALUE=1": 4,
    "delta": 1
  },
  "commit_branch_mispredicts_reason_TAGE": {
    "SECRET_VALUE=0": 1,
    "SECRET_VALUE=1": 2,
    "delta": 1
  }
}
```

### Expected behavior

A branch that is skipped by an older taken branch in the same FTQ block should not be allowed to train the BPU as if it were architecturally reachable.

For backend redirects, the resolve/train filtering should be offset-aware within the redirecting FTQ entry. Filtering only by `ftqIdx > backendRedirectPtr` is not sufficient, because younger resolves in the same FTQ block can have the same `ftqIdx` but a later `ftqOffset`.

The unsafe combination should not be possible:

```text
older branch:
  pc              = 0x8000004c
  target          = 0x80000060
  taken           = 1

younger train packet:
  pc              = 0x80000050
  same FTQ block  = true
  skipped by old branch target = true
  reaches BPU train path       = true
```

### Environment

* RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
* XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
* Local branch: `kunminghu-v3`


### To Reproduce

The attachment `xiangshan-same-ftq-wrongpath-bpu-secret-poc.zip` [xiangshan-same-ftq-wrongpath-bpu-secret-poc.zip](https://github.com/user-attachments/files/29338866/xiangshan-same-ftq-wrongpath-bpu-secret-poc.zip) contains:

1. `secret_train_probe.S`: minimal bare-metal PoC with victim training, attacker probe, `mcycle` measurement, and threshold recovery.
2. `linker.ld`: linker script placing `_start` at `0x80000000`.
3. `run_secret_probe.sh`: builds `SECRET_VALUE=0/1`, runs waveform validation, parses VCDs, and writes `verdict.json`.
4. `monitor_secret_probe_vcd.py`: VCD monitor for FTQ-to-BPU training, backend redirects, `result_delta`, and `recovered_bit` stores.
5. `run_timing_observable.sh`: no-wave emulator run that parses committed branch-mispredict counters.
6. `parse_perf_observable.py`: parser for no-wave PERF logs.
7. `secret_0_7800_18000.monitor.json` and `secret_1_7800_18000.monitor.json`: reproduced VCD monitor outputs.
8. `secret_0_nowave_perf.log`, `secret_1_nowave_perf.log`, and `timing_verdict.json`: reproduced no-wave PERF-counter evidence.
9. `secret_0.objdump`, `secret_1.objdump`, `secret_0.symbols`, `secret_1.symbols`, and `validation_input_sha256.txt`: build metadata.

The large VCD files are not included in the attachment, but the commands below regenerate them.

Run the waveform validation:

```bash
cd xiangshan-same-ftq-wrongpath-bpu-secret-poc

EMU=/path/to/emu \
RISCV_GCC=/path/to/riscv64-unknown-elf-gcc \
RISCV_OBJCOPY=/path/to/riscv64-unknown-elf-objcopy \
RISCV_OBJDUMP=/path/to/riscv64-unknown-elf-objdump \
RISCV_NM=/path/to/riscv64-unknown-elf-nm \
bash ./run_secret_probe.sh
```

Expected output:

```text
secret=0
{
  "wrongpath_taken": 0,
  "wrongpath_not_taken": 2,
  "probe_redirect_count": 0,
  "secret_probe_redirect_seen": false,
  "rdcycle_delta": 29,
  "recovered_bit": 0
}
secret=1
{
  "wrongpath_taken": 2,
  "wrongpath_not_taken": 0,
  "probe_redirect_count": 2,
  "secret_probe_redirect_seen": true,
  "rdcycle_delta": 39,
  "recovered_bit": 1
}
{
  "guest_threshold_recovers_secret_bit": 1,
  "secret_dependent_attacker_probe_redirect": 1,
  "secret_dependent_mcycle_delta": 1,
  "secret_dependent_wrongpath_training": 1,
  "secret_recovery_confirmed": 1,
  "security_primitive_confirmed": 1
}
```

Run the no-wave PERF-counter validation:

```bash
cd xiangshan-same-ftq-wrongpath-bpu-secret-poc

EMU=/path/to/emu \
RISCV_GCC=/path/to/riscv64-unknown-elf-gcc \
RISCV_OBJCOPY=/path/to/riscv64-unknown-elf-objcopy \
RISCV_OBJDUMP=/path/to/riscv64-unknown-elf-objdump \
RISCV_NM=/path/to/riscv64-unknown-elf-nm \
bash ./run_timing_observable.sh
```

Expected output:

```json
{
  "emulator_perf_counter_observable": true,
  "secret1_has_extra_committed_branch_mispredict": true,
  "secret1_has_extra_conditional_branch_mispredict": true,
  "secret1_has_extra_tage_reason_mispredict": true
}
```

### Additional context

Current source analysis on `kunminghu-v3`:

```scala
// src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
filteredResolve.valid := backendResolve.valid &&
  !(backendRedirect.reduce(_ || _) && backendResolve.bits.ftqIdx > backendRedirectPtr)
```

and:

```scala
// src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
when(entry.valid &&
  (backendRedirect.reduce(_ || _) && entry.bits.ftqIdx > backendRedirectPtr ||
    io.bpuEnqueue && entry.bits.ftqIdx.value === io.bpuEnqueuePtr.value)) {
  entry.bits.flushed := true.B
}
```

This filtering is based on FTQ index only. It does not distinguish same-FTQ younger branches by `ftqOffset`.

The surviving resolve entries are forwarded to BPU training through `Ftq.scala`:

```scala
// src/main/scala/xiangshan/frontend/ftq/Ftq.scala
when(resolveQueue.io.bpuTrain.fire) {
  trainCache.bits.meta     := metaQueueResolve(resolveQueue.io.bpuTrain.bits.ftqIdx.value)
  trainCache.bits.startPc  := resolveQueue.io.bpuTrain.bits.startPc
  trainCache.bits.branches := resolveQueue.io.bpuTrain.bits.branches
  trainCache.bits.perfMeta := perfQueue(resolveQueue.io.bpuTrain.bits.ftqIdx.value).bpuPerf
  trainCache.valid         := true.B
}

io.toBpu.train.valid := trainCache.valid
io.toBpu.train.bits  := trainCache.bits
```

Security impact:

* same-FTQ wrong-path branch direction reaches BPU training with secret-dependent `taken`.
* a later attacker probe of the same branch PC observes the poisoned predictor state as a branch redirect.
* the attacker code's own `mcycle` measurement distinguishes `SECRET_VALUE=0` from `SECRET_VALUE=1`.
* a guest threshold over that timing signal recovers the one-bit secret.
* a no-wave run exposes the same effect as one extra committed conditional branch mispredict for `SECRET_VALUE=1`.
