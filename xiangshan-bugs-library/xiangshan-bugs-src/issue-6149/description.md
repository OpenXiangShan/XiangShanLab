### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`sbpctl.RAS_ENABLE` is documented as the enable bit for the return-address stack predictor, but clearing it does not stop the frontend from selecting return targets from either MicroRas/URAS or the main RAS.

This is not only an internal stale-state issue. The attached PoC demonstrates an exploit-style primitive where a memory-loaded secret bit controls which wrong-path target is fetched while `RAS_ENABLE=0`.

The PoC does the following:

1. clears `sbpctl.RAS_ENABLE` by writing `0x3f` to CSR `0x5c0`;
2. trains the frontend metadata for a shared `ret` PC while `RAS_ENABLE` is already low;
3. briefly re-enables RAS and then clears it again as a monitor phase marker, with no call/return in flight;
4. loads a secret bit from memory;
5. uses that secret bit to choose one of two call sites after the final `RAS_ENABLE` clear;
6. does not architecturally return from the chosen call, leaving the chosen return address live in any RAS/URAS state that ignored the disabled bit;
7. executes an architectural `ret` whose real target is `safe_return`.

If `RAS_ENABLE` worked, the final return should not select a target from RAS/URAS. In the reproduced waveform, the frontend still uses RAS/URAS and fetches a secret-dependent wrong-path gadget.

The pair-level reproduced summary is:

```json
{
  "both_runs_ras_disabled": true,
  "both_runs_success": true,
  "prediction_targets_differ": true,
  "post_prediction_fetch_targets_differ": true,
  "secret_dependent_wrong_path_fetch": true
}
```

First decisive `SECRET_BIT=0` event:

```text
attack phase start = 16780  # final RAS_ENABLE 1->0 transition

disabled prediction:
  time           = 23800
  csr_ras_enable = 0
  type           = s1_disabled_uras_prediction
  target_name    = gadget0
  target         = 0x40000062
  uras_target    = 0x40000062

post-prediction FTQ fetch:
  time           = 23802
  csr_ras_enable = 0
  start          = 0x40000062
  target_name    = gadget0
```

First decisive `SECRET_BIT=1` event:

```text
attack phase start = 16780  # final RAS_ENABLE 1->0 transition

disabled prediction:
  time           = 16956
  csr_ras_enable = 0
  type           = s3_disabled_ras_prediction
  target_name    = gadget1
  target         = 0x40000042
  ras_top        = 0x40000042

post-prediction FTQ fetch:
  time           = 16958
  csr_ras_enable = 0
  start          = 0x40000042
  target_name    = gadget1
```

The symbol map confirms:

```text
gadget0 = 0x800000c4, pruned target = 0x40000062
gadget1 = 0x80000084, pruned target = 0x40000042
```

Security impact:

* after `RAS_ENABLE=0`, the frontend can still select return targets from MicroRas/URAS and main RAS.
* a memory-loaded secret bit can select which disabled-state return address is left in RAS/URAS.
* the selected RAS/URAS target becomes a different wrong-path FTQ fetch target.
* the two secret runs produce different post-prediction fetch targets while `csr_ras_enable=0`.

This appears different from issue #6134. Issue #6134 is about PBMT-IO instruction-fetch uncache serialization. This issue is about the `sbpctl.RAS_ENABLE` predictor-control bit not gating return prediction paths.


### Expected behavior

Clearing `sbpctl.RAS_ENABLE` should prevent RAS/URAS from affecting frontend control flow.

At least one of the following should hold:

1. main RAS and MicroRas/URAS state updates are gated or flushed when `RAS_ENABLE=0`;
2. `uras.io.specOut.isCanUse` and/or `uras.io.specOut.retTarget` are gated by `RAS_ENABLE`;
3. S1 return-target muxes cannot select `uras.io.specOut.retTarget` when `RAS_ENABLE=0`;
4. S3 return-target muxes cannot select `ras.io.topRetAddr` when `RAS_ENABLE=0`.

The unsafe combinations should not be possible:

```text
csr_ras_enable = 0
s1_prediction_attribute.rasAction = return
s1_prediction_target == uras.io.specOut.retTarget
```

or:

```text
csr_ras_enable = 0
s3_taken       = 1
s3_useRas      = 1
s3_prediction_target == ras.io.topRetAddr
```

It should also not be possible for the disabled-state RAS/URAS target to drive a secret-dependent wrong-path FTQ fetch.


### Environment

* RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
* XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
* Local branch: `kunminghu-v3`


### To Reproduce

The attachment `xiangshan-ras-enable-secret-fetch-poc.zip` [xiangshan-ras-enable-secret-fetch-poc.zip](https://github.com/user-attachments/files/29379710/xiangshan-ras-enable-secret-fetch-poc.zip) contains:

1. `ras_enable_secret_fetch.S`: minimal bare-metal PoC.
2. `linker.ld`: linker script placing `_start` at `0x80000000`.
3. `run_secret_fetch.sh`: builds and runs one `SECRET_BIT` value with waveform dumping.
4. `run_both_secret_fetch.sh`: runs both `SECRET_BIT=0` and `SECRET_BIT=1`, then summarizes the pair.
5. `monitor_ras_enable_secret_fetch.py`: FST/VCD monitor for `RAS_ENABLE`, RAS/URAS target selection, and FTQ fetches.
6. `summarize_pair.py`: pair-level checker for secret-dependent target/fetch differences.
7. `ras_enable_secret0_8000_15000.monitor.json` and `ras_enable_secret1_8000_15000.monitor.json`: reproduced monitor outputs.
8. `pair_summary.json`: reproduced pair verdict.
9. `ras_enable_secret0.elf`, `ras_enable_secret0.bin`, `ras_enable_secret1.elf`, and `ras_enable_secret1.bin`: reproduced workloads.
10. `ras_enable_secret0.objdump`, `ras_enable_secret1.objdump`, `toolchain_version.txt`, and `validation_input_sha256.txt`: build metadata.
11. `ras_enable_secret0_8000_15000.run.log`, `ras_enable_secret1_8000_15000.run.log`, and monitor logs.
12. `ATTACHMENT_CONTENTS.txt`: attachment manifest.

Large FST/VCD waveform files are not included in the attachment, but the commands below regenerate them. The attachment intentionally contains no Markdown files.

Build and run both secret values:

```bash
mkdir xiangshan-ras-enable-secret-fetch-poc
cd xiangshan-ras-enable-secret-fetch-poc
unzip /path/to/xiangshan-ras-enable-secret-fetch-poc.zip

EMU=/path/to/emu \
RISCV_GCC=/path/to/riscv64-unknown-elf-gcc \
RISCV_OBJCOPY=/path/to/riscv64-unknown-elf-objcopy \
RISCV_OBJDUMP=/path/to/riscv64-unknown-elf-objdump \
bash ./run_both_secret_fetch.sh
```

The expected final output is:

```json
{
  "both_runs_ras_disabled": true,
  "both_runs_success": true,
  "prediction_targets_differ": true,
  "post_prediction_fetch_targets_differ": true,
  "secret_dependent_wrong_path_fetch": true
}
```

To run one side manually:

```bash
SECRET_BIT=0 PREFIX=ras_enable_secret0 ./run_secret_fetch.sh
SECRET_BIT=1 PREFIX=ras_enable_secret1 ./run_secret_fetch.sh

python3 ./summarize_pair.py \
  --secret0 ras_enable_secret0_8000_15000.monitor.json \
  --secret1 ras_enable_secret1_8000_15000.monitor.json
```

The reproduced single-run predicates are:

```text
SECRET_BIT=0:
  attack_phase_start                         = 16780
  csr_ras_disabled_seen                      = true
  disabled_prediction_to_expected_gadget     = true
  disabled_prediction_to_opposite_gadget     = false
  ftq_fetch_expected_after_prediction        = true
  ftq_fetch_opposite_after_prediction        = false
  expected_gadget                            = gadget0
  success                                    = true

SECRET_BIT=1:
  attack_phase_start                         = 16780
  csr_ras_disabled_seen                      = true
  disabled_prediction_to_expected_gadget     = true
  disabled_prediction_to_opposite_gadget     = false
  ftq_fetch_expected_after_prediction        = true
  ftq_fetch_opposite_after_prediction        = false
  expected_gadget                            = gadget1
  success                                    = true
```


### Additional context

Current source analysis on `kunminghu-v3`:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
class SbpctlBundle extends CSRBundle {
  val RAS_ENABLE = RW(6).withReset(true.B)
    .withDescription("Enable the return-address stack predictor.")
}
```

`RAS_ENABLE` is mapped to frontend BPU control:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
io.status.custom.bp_ctrl.rasEnable := sbpctl.regOut.RAS_ENABLE.asBool
```

`Bpu.scala` drives the main RAS enable, but MicroRas/URAS is hardwired enabled:

```scala
// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
uras.io.enable := true.B
ras.io.enable  := ctrl.rasEnable
```

The S1 return-target muxes do not check `ctrl.rasEnable`:

```scala
s1_ubtbPrediction.target := Mux(
  ubtb.io.prediction.bits.attribute.isReturn && uras.io.specOut.isCanUse,
  uras.io.specOut.retTarget,
  ubtb.io.prediction.bits.target
)

s1_abtbResult.target := Mux(
  s1_abtbFirstTakenBr.attribute.isReturn && uras.io.specOut.isCanUse,
  uras.io.specOut.retTarget,
  s1_abtbFirstTakenBr.target
)
```

The S3 return-target mux also does not check `ctrl.rasEnable`:

```scala
s3_prediction.target := MuxCase(
  s3_fallThroughPrediction.target,
  Seq(
    (s3_taken && s3_useRas) -> ras.io.topRetAddr,
    ...
  )
)
```

In addition, local inspection found that `Ras.scala` and `MicroRas.scala` do not consume `io.enable` in the state/output logic that feeds these target muxes. This explains why the CSR output can be low while frontend return prediction still uses RAS/URAS-derived targets.
