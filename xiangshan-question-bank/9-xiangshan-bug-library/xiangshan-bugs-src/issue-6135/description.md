### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

Writing `0x3f` to CSR `0x5c0` (`sbpctl`) clears `RAS_ENABLE` while leaving the other branch predictor enables set. In the reproduced run, the CSR write reaches the frontend control path and the internal `RAS_ENABLE` signal becomes zero. However, return prediction still consumes attacker-seeded RAS/uRAS state after that disable point.

The attached PoC models a cross-context pattern:

1. An attacker-like context seeds the RAS with one of two return sites.
2. A victim-like context writes `0x3f` to `sbpctl`, clearing `RAS_ENABLE`.
3. The victim executes a trained `ret`.
4. XiangShan still predicts the return to the attacker-selected RAS/uRAS target.
5. The wrong path fetches, decodes, and dispatches a secret-selected gadget.
6. The gadget issues a load to a secret-selected probe line.
7. After redirect recovery, architectural `rdcycle` measurements recover which probe line was touched.

This is not an architectural wrong-commit bug in the PoC. The architectural return is corrected to `common_after`. The security issue is that the documented/implemented RAS disable control does not stop return prediction from using RAS/uRAS state, so a victim that tries to disable RAS can still execute a secret-dependent wrong path and leave cache-timing traces.

### Expected behavior

After `sbpctl.RAS_ENABLE` is cleared:

- return prediction should not use RAS/uRAS entries,
- speculative RAS/uRAS push/pop state should not create a usable return target,
- an attacker-seeded RAS entry should not steer a later victim return,
- the victim return should not fetch/decode/dispatch a secret-dependent wrong-path gadget through disabled RAS state.

For the attached PoC, both `SECRET_BIT=0` and `SECRET_BIT=1` should fail to produce:

- disabled-RAS prediction to the secret-selected return site,
- FTQ fetch of the secret-selected return site/gadget,
- DCache request to the secret-selected probe line,
- a faster `rdcycle` measurement for the selected probe line.

### Environment

- XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
- RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
- Run mode: `--no-diff --dump-wave-full --force-dump-result`


### To Reproduce

The attachment `xiangshan-ras-disable-sidechannel-poc.zip` [xiangshan-ras-disable-sidechannel-poc.zip](https://github.com/user-attachments/files/29328808/xiangshan-ras-disable-sidechannel-poc.zip) contains:

1. `ras_disable_sidechannel.S`: two-variant bare-metal PoC.
2. `link.ld`: linker script placing `_start` at `0x80000000`.
3. `run_sidechannel_poc.sh`: builds both variants, runs the waveform-enabled emulator, and parses the VCD.
4. `monitor_ras_disable_sidechannel.py`: VCD monitor for CSR, RAS/uRAS, FTQ, decode/dispatch, LSQ/DCache, commit/store, and `rdcycle` result stores.
5. `SUMMARY_7800_15000.json`: combined reproduced result for both secret variants.
6. `ras_disable_sidechannel_secret{0,1}_7800_15000.monitor.json`: full monitor outputs.
7. `ras_disable_sidechannel_secret{0,1}_7800_15000.monitor.log`: predicate summaries.
8. `ras_disable_sidechannel_secret{0,1}_7800_15000.run.log`: emulator logs.
9. `ras_disable_sidechannel_secret{0,1}.{elf,bin,objdump}`: reproduced binaries and disassembly.

The full VCD files are not included in the zip because they are about 1.9 GB per variant. The script regenerates them when run in the same XiangShan environment.

Run:

```bash
SECRETS="0 1" CYCLES=19000 WAVE_BEGIN=7800 WAVE_END=15000 bash run_sidechannel_poc.sh
```

The runner builds each variant with:

```bash
/opt/riscv/bin/riscv64-unknown-elf-gcc \
  -DSECRET_BIT=<0-or-1> \
  -march=rv64gc_zicsr_zifencei -mabi=lp64d -mcmodel=medany \
  -nostdlib -nostartfiles -static -fno-pic \
  -T link.ld -Wl,--no-relax \
  -o ras_disable_sidechannel_secret<secret>.elf \
  ras_disable_sidechannel.S
```

The emulator command used by the runner is:

```bash
/root/HardwareAgent/XiangShan/build_vcd/emu \
  -C 19000 -b 7800 -e 15000 \
  -i ras_disable_sidechannel_secret<secret>.bin \
  --no-diff \
  --dump-wave-full \
  --wave-path=ras_disable_sidechannel_secret<secret>_7800_15000.vcd \
  --force-dump-result
```

Both variants reproduce the side channel:

```text
SECRET_BIT=0:
  CSR disable write:                         17942
  internal RAS_ENABLE=0:                     17944
  first disabled RAS/uRAS prediction:        17996
  FTQ fetch of secret0_ret_site:             17998
  FTQ fetch of secret0_gadget:               18008
  decode/dispatch of secret0_gadget:         18206 / 18212
  LSQ/DCache-address load to probe0:         18222
  DCache request for probe0:                 18228
  redirect to common_after:                  18494
  rdcycle result: probe0=48, probe1=156
  timing branch reaches hit_loop:            19580

SECRET_BIT=1:
  CSR disable write:                         17902
  internal RAS_ENABLE=0:                     17904
  first disabled RAS/uRAS prediction:        17956
  FTQ fetch of secret1_ret_site:             17958
  FTQ fetch of secret1_gadget:               17968
  decode/dispatch of secret1_gadget:         18166 / 18172
  LSQ/DCache-address load to probe1:         18182
  DCache request for probe1:                 18188
  redirect to common_after:                  18454
  rdcycle result: probe1=48, probe0=156
  timing branch reaches hit_loop:            19540
```

Both monitor outputs report:

```text
csr_disable_seen: true
ras_enable_zero_seen: true
disabled_ras_prediction_to_expected_ret_site: true
expected_ret_site_ftq_fetch_after_prediction: true
expected_gadget_ftq_fetch_after_disable: true
expected_gadget_decode_after_disable: true
expected_gadget_dispatch_after_disable: true
expected_probe_load_after_prediction: true
expected_dcache_probe_request_after_prediction: true
selected_probe_faster_than_other: true
icache_ftq_sidechannel_success: true
dcache_sidechannel_success: true
timing_sidechannel_success: true
unexpected_ret_site_ftq_fetch_after_prediction: false
success: true
```

### Additional context

The CSR value `0x3f` intentionally leaves BTB/TAGE-style predictors enabled and clears only `RAS_ENABLE`. This report does not claim that BTB target selection is the carrier. The reproduced carrier is disabled RAS/uRAS state steering return prediction after the victim has cleared `sbpctl.RAS_ENABLE`.

The PoC demonstrates a Spectre-RSB-style primitive: attacker-controlled RAS state survives into a victim-like context, the victim disables RAS, and disabled RAS/uRAS state still creates a secret-dependent wrong path with observable frontend and DCache timing traces.

Source-level context that may help locate the problem:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
val RAS_ENABLE = RW(6).withReset(true.B).withDescription("Enable the return-address stack predictor.")

// src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
io.status.custom.bp_ctrl.rasEnable := sbpctl.regOut.RAS_ENABLE.asBool

// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
uras.io.enable := true.B
ras.io.enable  := ctrl.rasEnable
```

The main RAS receives `ctrl.rasEnable`, but the observed RAS/uRAS behavior is still active after the CSR disable. In particular:

- `MicroRas` can continue producing `specOut.isCanUse` / `specOut.retTarget`.
- `Bpu.scala` hardwires `uras.io.enable := true.B`.
- `Ras.scala` computes speculative push/pop and commit push/pop from `io.specIn` / `io.commit` without gating those paths with `io.enable`.
