### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`ITTAGE` currently issues table reads for every frontend `s1_fire`, even when the fetched block contains only direct control flow and no indirect branch.

The root cause is that `s1_isIndirect` is hardwired to `true.B` in `Ittage.scala`, and each ITTAGE table request is driven by `s1_fire && s1_isIndirect`. Therefore direct-only code still reads ITTAGE tables.

This is not only a low-power issue. ITTAGE tables are single-port SRAM slices. Pending ITTAGE writes drain from the per-bank write buffer only when the matching bank's read port is idle. As a result, direct-only ITTAGE reads can create bank-dependent read/write conflicts with real ITTAGE training traffic.

The attached PoC demonstrates an end-to-end software-visible timing channel:

1. A fixed non-RAS indirect jump workload alternates between two targets to create normal ITTAGE prediction/update activity.
2. Each target returns to a direct-only hammer block. The hammer phase contains no `jalr`, `jr`, or `ret`.
3. The bank0 and bank1 hammer variants differ only by the position of one 16-bit `c.nop`, moving `hammer_loop` from `0x80000060` to `0x80000062` while preserving instruction count.
4. The program reads `mcycle` around the measured workload and stores the result to `result_delta`.
5. The VCD monitor observes that architectural store through the simulator endpoint store signals.

The reproduced result is:

```text
case                 result_delta  ITTAGE conflict  branch mispredicts  TAGE-reason mispredicts
no hammer bank0      137           275              29                  9
no hammer bank1      137           275              29                  9
hammer bank0         787           91               59                  40
hammer bank1         560           12               47                  25
```

Derived verdict:

```text
control_bank_delta_cycles          = 0
hammer_bank0_minus_bank1_cycles    = 227
hammer_bank0_over_bank1_ratio      = 1.405357
hammer_conflict_bank0_minus_bank1  = 79
hammer_misp_bank0_minus_bank1      = 12
hammer_tage_misp_bank0_minus_bank1 = 15
e2e_timing_channel_observed        = true
```

The important point is that the timing value is software-visible. For example, the bank0 hammer run stores:

```json
{
  "addr": 2147483864,
  "data": 787,
  "pc": 2147483782,
  "slot": 0,
  "time": 19704
}
```

The shifted-bank run stores:

```json
{
  "addr": 2147483864,
  "data": 560,
  "pc": 2147483782,
  "slot": 0,
  "time": 19154
}
```

The no-hammer controls both store `137`, so the bank selection itself is not enough to create the timing difference.


### Expected behavior

Direct-only fetch blocks should not access ITTAGE tables.

In particular, the unsafe behavior should not be possible:

```text
direct-only fetch block:
  contains no jalr / jr / ret
  contains no non-RAS indirect branch

but:
  ittage.table.req.valid = 1
  direct-only fetches contend with ITTAGE write-buffer drain
  software-visible mcycle delta changes with direct-only hammer bank
```

`s1_isIndirect` should be computed from frontend/FTB/uBTB information indicating that ITTAGE may be needed, instead of being hardwired to `true.B`. If a fetch block cannot contain a non-RAS indirect branch, ITTAGE table reads should be suppressed.


### Environment

* RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
* XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
* Local branch: `kunminghu-v3`


### To Reproduce

The attachment `xiangshan-ittage-direct-only-timing-poc.zip` [xiangshan-ittage-direct-only-timing-poc.zip](https://github.com/user-attachments/files/29379399/xiangshan-ittage-direct-only-timing-poc.zip) contains:

1. `ittage_e2e_timing.S`: bare-metal end-to-end timing-channel / soft-DoS PoC.
2. `link.ld`: linker script placing `_start` at `0x80000000`.
3. `run_ittage_e2e_timing.sh`: build/run wrapper.
4. `monitor_ittage_e2e_timing.py`: VCD store monitor and PERF parser.
5. `summarize_ittage_e2e_verdict.py`: combines the four monitor outputs into `ittage_e2e_timing_verdict.json`.
6. `ittage_e2e_timing_verdict.json`: reproduced combined verdict.
7. `e2e_*monitor.json`: reproduced monitor outputs.
8. `e2e_*run.log`, `e2e_*objdump`, and `e2e_*symbols`: build/run metadata.
9. `validation_input_sha256.txt`: hashes for the included source/scripts/results.

The large VCD files are not included in the attachment, but the commands below regenerate them.

Run the hammer bank0 case:

```bash
HAMMER_BANK=0 WARM_ITERS=8 MEASURE_ITERS=8 HAMMER_ITERS=32 \
  PREFIX=e2e_bank0_w8_m8_h32_wave0_12k CYCLES=12000 \
  WAVE=1 WAVE_BEGIN=0 WAVE_END=12000 TIMEOUT_SECS=180 \
  ./run_ittage_e2e_timing.sh
```

Run the hammer bank1 case:

```bash
HAMMER_BANK=1 WARM_ITERS=8 MEASURE_ITERS=8 HAMMER_ITERS=32 \
  PREFIX=e2e_bank1_w8_m8_h32_wave8p5_11k CYCLES=12000 \
  WAVE=1 WAVE_BEGIN=8500 WAVE_END=11000 TIMEOUT_SECS=180 \
  ./run_ittage_e2e_timing.sh
```

Run no-hammer controls:

```bash
HAMMER_BANK=0 WARM_ITERS=8 MEASURE_ITERS=8 HAMMER_ITERS=0 \
  PREFIX=e2e_ctrl_bank0_w8_m8_h0_wave0_12k CYCLES=12000 \
  WAVE=1 WAVE_BEGIN=0 WAVE_END=12000 TIMEOUT_SECS=180 \
  ./run_ittage_e2e_timing.sh

HAMMER_BANK=1 WARM_ITERS=8 MEASURE_ITERS=8 HAMMER_ITERS=0 \
  PREFIX=e2e_ctrl_bank1_w8_m8_h0_wave0_12k CYCLES=12000 \
  WAVE=1 WAVE_BEGIN=0 WAVE_END=12000 TIMEOUT_SECS=180 \
  ./run_ittage_e2e_timing.sh
```

Summarize the verdict:

```bash
python3 ./summarize_ittage_e2e_verdict.py \
  --json-out ittage_e2e_timing_verdict.json
```

Expected reproduced predicate:

```json
{
  "all_direct_phases_have_no_indirect": true,
  "all_program_results_observed": true,
  "control_banks_have_equal_timing": true,
  "e2e_timing_channel_observed": true,
  "hammer_bank0_has_more_branch_mispredicts": true,
  "hammer_bank0_has_more_ittage_conflict": true,
  "hammer_bank0_is_slower_than_bank1": true
}
```


### Additional context

Current source analysis on `kunminghu-v3`:

```scala
// src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
private val s1_isIndirect = true.B // (!s1_uftbHit && !io.fromFtb.s1_ftbCloseReq) || s1_uftbHasIndirect

tables.foreach { t =>
  t.io.req.valid := s1_fire && s1_isIndirect // TODO: s1_isIndirect for low power
}
```

This causes ITTAGE reads for direct-only fetch blocks.

The table implementation explains why this becomes observable:

```scala
// src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala
val writeValid = readPort.valid && !bank.io.r.req.valid
bank.io.w.apply(writeValid, writeEntry, writeSetIdx, true.B, writeBitMask)
readPort.ready := bank.io.w.req.ready && !bank.io.r.req.valid

XSPerfAccumulate(
  "ittage_table_read_write_conflict",
  VecInit(tables.zip(writeBuffers).map { case (bank, buffer) =>
    bank.io.r.req.valid && buffer.io.read.head.valid
  }).asUInt.orR
)
```

So direct-only over-reads can delay ITTAGE write-buffer drain on the same bank.

The final target selection still appears to be gated correctly:

```scala
// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
private val s3_useIttage = s3_firstTakenBranch.bits.attribute.needIttage && ittage.io.prediction.hit

s3_prediction.target := MuxCase(
  s3_fallThroughPrediction.target,
  Seq(
    (s3_taken && s3_useRas)    -> ras.io.topRetAddr,
    (s3_taken && s3_useIttage) -> ittage.io.prediction.target,
    s3_taken                   -> s3_firstTakenBranch.bits.target
  )
)
```

Therefore I am not claiming that direct branches consume ITTAGE targets as final predictions.

Security impact:

* direct-only fetch blocks access ITTAGE because `s1_isIndirect` is hardwired true.
* software can observe a timing difference through architectural `mcycle` measurement and a normal store to memory.
* attacker-controlled direct-only hammer bank changes the measured runtime of a fixed indirect-branch workload.
* the slower hammer case also has more ITTAGE conflict and more branch mispredicts.

The concern is that code which should not need ITTAGE can still perturb ITTAGE predictor state and timing. If predictor state is shared across security domains without sufficient flushing or partitioning, this creates a timing-channel / predictor-interference risk. Even within one workload, the PoC demonstrates a software-visible soft-DoS slowdown.
