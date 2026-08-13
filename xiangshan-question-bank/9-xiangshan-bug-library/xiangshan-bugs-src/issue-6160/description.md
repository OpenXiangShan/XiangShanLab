### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan ITTAGE can assert `altDiffers` even when there is no valid alternate provider.

Current code computes `altDiffers` from the two target values directly:

```scala
ittageMeta.altDiffers := s3_providerTarget =/= s3_altProviderTarget
```

However, this expression is not guarded by `s3_altProvided`. When `altProvider.valid = 0`, `s3_altProviderTarget` is invalid data. In the reproduced waveform it is observed as `0x0`, so a normal nonzero provider target fabricates `altDiffers = 1`.

This is not just a debug-signal inconsistency. ITTAGE training consumes this metadata when updating the provider useful counter:

```scala
updateUsefulCnt(provider) := Mux(
  !t1_meta.altDiffers,
  t1_meta.providerUsefulCnt,
  (t1_meta.providerTarget === updateRealTarget).asTypeOf(UsefulCounter())
)
```

Therefore, a no-alternate-provider case is incorrectly treated like a real provider/alternate-provider disagreement, and the useful counter can be updated from the provider-vs-real-target comparison.

In the attached PoC, a fixed non-RAS indirect `jalr` with history-correlated alternating targets forces ITTAGE provider hits. The monitor checks the following predicate:

```text
updateValid = 1
provider.valid = 1
altProvider.valid = 0
altDiffers = 1
providerTarget != 0
```

Observed result:

```json
{
  "both_variants_reproduce_no_alt_altdiffers": 1,
  "secret_controls_buggy_useful_write": 1,
  "wrongpath_gadget_load_observed": 1
}
```

More detailed observed results:

```text
SECRET_VALUE=0:
  no_alt_event_count = 226
  secret_no_alt_event_count = 34
  secret useful writes = 1
  probe_redirect_count = 2
  wrongpath oracle LoadUnit requests = 0

SECRET_VALUE=1:
  no_alt_event_count = 232
  secret_no_alt_event_count = 32
  secret useful writes = 0
  probe_redirect_count = 2
  wrongpath oracle LoadUnit requests = 2
```

Representative wrong-path oracle load request from the `SECRET_VALUE=1` run:

```json
{
  "time": 49966,
  "unit": 0,
  "vaddr": "0x80001000",
  "ready": 1,
  "s1_kill": 1,
  "s2_kill": 1
}
```

The killed LoadUnit request is a microarchitectural/transient observation from VCD, not an architectural execution result. I am not claiming a guest-visible cache timing leak here; the current monitor did not observe an oracle DCache/L2 transaction.

The PoC program has three phases.

1. Warm-up/training phase

   The same indirect `jalr` PC repeatedly jumps to two 64-byte-aligned targets. A direct branch immediately before the `jalr` selects the target from a counter bit:

   ```asm
   warm_select:
       andi    t0, s1, 1
       beqz    t0, use_target_a
       mv      s0, s3
       j       indirect_site
   use_target_a:
       mv      s0, s2
   indirect_site:
       jalr    zero, 0(s0)
   ```

   This keeps the indirect branch PC constant while changing the recent path/history and the real target. That forces ITTAGE tagged-table allocation and later provider hits. The monitor then checks whether an ITTAGE update has a valid provider, no valid alternate provider, and `altDiffers=1`.

2. Two compile-time secret variants

   The wrapper builds two binaries:

   ```bash
   -DSECRET_VALUE=0
   -DSECRET_VALUE=1
   ```

   The secret changes the target mapping used during training, without adding a runtime secret branch before the measured loop. This avoids perturbing the predictor history with an extra conditional branch. The two variants are used as a controlled way to show that the no-alt `altDiffers` condition can lead to different useful-counter writes:

   ```text
   SECRET_VALUE=0 -> secret-window useful writes are 1
   SECRET_VALUE=1 -> secret-window useful writes are 0
   ```

   These useful writes are parsed from the ITTAGE table update signals, not inferred from architectural state.

3. Probe phase

   After training, the attacker probe uses the same indirect `jalr` PC, but the architectural/real target is fixed to `loop_target_a`:

   ```asm
   probe_select:
       la      s0, loop_target_a
       j       indirect_site
   ```

   The other target, `loop_target_b`, starts with a load from an oracle line:

   ```asm
   loop_target_b:
   target_b_gadget_load:
       la      t5, oracle_line
       ld      t6, 0(t5)
   ```

   Therefore, if the predictor sends the frontend down the `loop_target_b` path during the probe, the VCD monitor can observe a wrong-path LoadUnit request to `oracle_line` before the pipeline kills it.

The monitor (`monitor_noalt_secret_probe.py`) samples these signal groups:

- ITTAGE metadata: `updateValid`, `provider.valid`, `altProvider.valid`, `altDiffers`, provider/alternate targets, provider table bits, provider useful/counter values.
- ITTAGE table updates: update valid, update table id, useful-counter write enable/value, update start PC.
- Backend redirects for the indirect branch PC.
- LoadUnit requests to `oracle_line`, including `s1_kill` and `s2_kill`.
- Endpoint stores used as phase markers and probe timing markers.

The key interpretation is:

```text
no-alt altDiffers event
  -> useful-counter write changes across the two secret variants
  -> later probe can produce a wrong-path LoadUnit request to the oracle line
```

### Expected behavior

When there is no valid alternate provider, `altDiffers` should not assert due to the invalid alternate target value.

A possible fix direction is to qualify the target comparison with alternate-provider validity, for example:

```scala
ittageMeta.altDiffers := s3_provided && s3_altProvided && (s3_providerTarget =/= s3_altProviderTarget)
```

Equivalently, the update path should avoid treating no-alt-provider metadata as a real alternate-provider disagreement.

### Environment

- XiangShan branch: `kunminghu-v3`
- Observed XiangShan commit: `4c742fa44b76fe372f70c74aad2ca826be0de155`

### To Reproduce

Unpack the attached  [ittage-noalt-altdiffers-invalid-alt.zip](https://github.com/user-attachments/files/29404440/ittage-noalt-altdiffers-invalid-alt.zip) and run:

```bash
cd appendix
bash run_noalt_secret_probe.sh
```

The wrapper builds two binaries with `SECRET_VALUE=0` and `SECRET_VALUE=1`, runs XiangShan with a VCD window, and parses the waveform:

```text
WARM_ITERS=2048
PROBE_ITERS=32
CYCLES=30000
WAVE_BEGIN=22000
WAVE_END=26000
```

Expected local verdict:

```json
{
  "both_variants_reproduce_no_alt_altdiffers": 1,
  "secret_controls_buggy_useful_write": 1,
  "wrongpath_gadget_load_observed": 1
}
```

### Additional context

_No response_
