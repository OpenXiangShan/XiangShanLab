### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`AheadBtb` can still emit a valid stale `s2` prediction after ABTB is disabled through `sbpctl`.

The short version is:

1. `abtb.io.enable` is driven by `ctrl.abtbEnable`.
2. `AheadBtb` gates `s0_fire`, `s1_fire`, and `s2_fire` with `io.enable`.
3. If `io.enable` becomes `0` while `s2_valid` is already set, `s2_fire` is also forced to `0`, so the old `s2_valid` state is not consumed/cleared by the normal fire path.
4. The output-side valid signals are still generated from `s2_valid`/`s2_hitMask` and are not gated by `io.enable`.

As a result, after ABTB is disabled, BPU can observe `abtb.io.prediction.valid` and fire a stale ABTB prediction whose `debug_startPc` belongs to an older fetch block. The existing assertion in `Bpu.scala` catches this mismatch:

```text
Assertion failed at Bpu.scala:556
Guest cycle spent: 9824
```

This is not only a simulation assertion issue. With the assertion bypassed only to keep collecting the waveform, the same run shows transient frontend and memory-side effects after ABTB has been disabled.

The PoC trains the same branch-site control-flow sequence to reach a gadget, then executes:

```asm
csrw    0x5c0, zero       # disable branch prediction controls, including ABTB
la      s0, secret_data
la      t4, flag_zero
ld      t3, 0(t4)         # real branch condition becomes zero

branch_site:
    bnez    t3, gadget    # architecturally not taken in the attack iteration
```

The source uses the far-target `bnez` pseudo-instruction above; the generated code expands it into an inverted local conditional branch plus a direct jump to `gadget`. The final attack iteration still has the source-level branch condition cleared to zero, so the architectural path is `fallthrough`.

The gadget performs a data-dependent cache access:

```asm
gadget:
    ld      s1, 0(s0)          # attack: secret_data
    andi    s1, s1, 0xff
    slli    s1, s1, 6
    la      s2, probe_array
    add     s2, s2, s1
    ld      s3, 0(s2)          # DCache address encodes secret byte
    j       fallthrough
```

First decisive stale-ABTB event from `security_evidence.json`:

```text
cycle                  = 9817
abtb_enable            = 0
bpu_s2_start_byte      = 0x80000060  # branch_site
abtb_s2_start_byte     = 0x80001000  # stale ABTB s2/debug start, gadget line
pred_start_byte        = 0x80001000  # stale prediction start
ic_req_fire            = true
ic_resp_valid          = true
ic_hit                 = true
```

The reproduced counts in the same waveform are:

```text
disabled_abtb_prediction_fire       = 32
disabled_abtb_pc_mismatch           = 24
disabled_abtb_with_icache_activity  = 24
icache_fetch_req_fire               = 378
icache_resp_valid                   = 322
icache_hit_resp                     = 322
loadunit_secret_data                = 2
loadunit_probe_secret_line          = 2
dcache_outer_probe_secret_line      = 2
```

The DCache evidence is stronger than just a frontend assertion. After the stale ABTB prediction window, the waveform contains a secret-dependent load chain:

```text
cycle 9835: LoadUnit paddr = 0x80003000, class = secret_data
cycle 9842: LoadUnit paddr = 0x800051c0, class = probe_secret_line
cycle 9843: DCache outer A addr = 0x800051c0, class = probe_secret_line
```

Here `secret_data` contains byte value `7`, and `probe_secret_line = probe_array + 7 * 64 = 0x800051c0`. This shows that transient execution reaches the gadget line and issues a data-dependent DCache-side access that can encode the secret into the cache/interconnect footprint.

Security impact:

* Proven: after ABTB is disabled, stale ABTB output can still be valid and participate in `io.toFtq.prediction.fire`.
* Proven: the stale-ABTB window is observable in the ICache-side frontend activity (`icache fetch request/response/hit` in the same disabled-ABTB prediction window). This run does not require an ICache outer miss.
* Proven: transient gadget execution reaches LoadUnit/DCache and produces a secret-dependent DCache outer request to `probe_array + secret * 64`.
* Not claimed: architectural wrong commit.
* Not claimed: direct architectural register/memory leakage. The demonstrated leak is microarchitectural, through DCache state/interconnect-visible address selection.

This appears different from issue #6134. Issue #6134 is about PBMT-IO instruction fetch serialization. This issue is about stale ABTB pipeline state and prediction outputs after `bp_ctrl.abtbEnable` is disabled.


### Expected behavior

Disabling ABTB should make ABTB unable to emit any prediction or metadata that can affect frontend control flow.

At least one of the following should hold:

1. pending ABTB pipeline state is flushed/cleared when `io.enable` becomes `0`;
2. `io.prediction`, `io.abtbResult`, `io.meta`, and replacement/read side effects are gated by `io.enable`;
3. `s2_valid` is allowed to drain/clear even while `io.enable` is `0`, instead of using an `io.enable`-gated `s2_fire` as the only normal clear path.

The unsafe combination should not be possible:

```text
abtbEnable                  = 0
abtb.s2_valid               = 1
abtb.prediction.valid       = 1
io.toFtq.prediction.fire    = 1
abtb.debug_startPc         != bpu.s1_startPc
```

It should also not be possible for this disabled-ABTB stale prediction window to drive transient ICache/DCache activity.

### Environment

* RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
* XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
* Local branch: `kunminghu-v3`


### To Reproduce

The attachment `xiangshan-abtb-disable-stale-prediction-poc.zip` [xiangshan-abtb-disable-stale-prediction-poc.zip](https://github.com/user-attachments/files/29338645/xiangshan-abtb-disable-stale-prediction-poc.zip) contains:

1. `poc_abtb_dcache_gadget.S`: minimal bare-metal PoC.
2. `linker.ld`: linker script placing `_start` at `0x80000000`.
3. `poc_abtb_dcache_gadget.elf` and `poc_abtb_dcache_gadget.bin`: reproduced workload.
4. `poc_abtb_dcache_gadget.objdump` and `poc_abtb_dcache_gadget.symbols`: disassembly and symbol map.
5. `analyze_security_vcd.py`: VCD monitor for stale ABTB, ICache activity, LoadUnit activity, and DCache outer requests.
6. `security_evidence.json`: reproduced monitor output.
7. `normal_assert_run.log`: normal emulator run showing `Assertion failed at Bpu.scala:556`.
8. `toolchain_version.txt` and `validation_input_sha256.txt`: build metadata.

The large VCD file is not included in the attachment, but the commands below regenerate it.

Build the PoC:

```bash
riscv64-unknown-elf-gcc \
  -march=rv64gc -mabi=lp64d -mcmodel=medany \
  -nostdlib -nostartfiles \
  -T linker.ld \
  -o poc_abtb_dcache_gadget.elf \
  poc_abtb_dcache_gadget.S

riscv64-unknown-elf-objcopy \
  -O binary \
  poc_abtb_dcache_gadget.elf \
  poc_abtb_dcache_gadget.bin

riscv64-unknown-elf-objdump \
  -dr poc_abtb_dcache_gadget.elf \
  > poc_abtb_dcache_gadget.objdump
```

Run normally. This should stop at the existing BPU assertion:

```bash
emu \
  -i poc_abtb_dcache_gadget.bin \
  --no-diff \
  -C 30000 -b 0 -e 30000 \
  > normal_assert_run.log 2>&1
```

Expected log excerpt:

```text
Assertion failed at Bpu.scala:556
Seed=0 Guest cycle spent: 9824
```

To collect post-assert security evidence, I locally bypassed only this assertion after `common_enable_assert()` so the simulator could continue long enough to dump the relevant waveform:

```bash
gdb -q --batch \
  -ex 'set pagination off' \
  -ex 'set print thread-events off' \
  -ex 'break common_enable_assert()' \
  -ex 'run' \
  -ex 'finish' \
  -ex 'set *((int*)&assert_count) = -1' \
  -ex 'continue' \
  --args emu \
    -i poc_abtb_dcache_gadget.bin \
    --no-diff \
    -C 10200 -b 9700 -e 10100 \
    --dump-wave-full \
    --wave-path=poc_abtb_dcache_gadget_9700_10100.vcd
```

Parse the waveform:

```bash
python3 analyze_security_vcd.py \
  poc_abtb_dcache_gadget_9700_10100.vcd \
  poc_abtb_dcache_gadget.elf \
  19400 \
  20250 \
  > security_evidence.json
```

The reproduced result is:

```text
disabled_abtb_prediction_fire       = 32
disabled_abtb_pc_mismatch           = 24
disabled_abtb_with_icache_activity  = 24
loadunit_secret_data                = 2
loadunit_probe_secret_line          = 2
dcache_outer_probe_secret_line      = 2
```

First DCache-side leak chain:

```json
{
  "loadunit_secret_data": {
    "cycle": 9835,
    "paddr": "0x80003000",
    "pc": "0x80001004",
    "class": ["secret_data"]
  },
  "loadunit_probe_secret_line": {
    "cycle": 9842,
    "paddr": "0x800051c0",
    "pc": "0x80001034",
    "class": ["probe_secret_line"]
  },
  "dcache_outer_probe_secret_line": {
    "cycle": 9843,
    "addr": "0x800051c0",
    "class": ["probe_secret_line"]
  }
}
```

### Additional context

Current source analysis on `kunminghu-v3`:

```scala
// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
abtb.io.enable := ctrl.abtbEnable
```

`AheadBtb` gates the pipeline fire signals with `io.enable`:

```scala
// src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
s0_fire := io.enable && predictReqValid
s1_fire := io.enable && s1_valid && s2_ready && predictReqValid
s2_fire := io.enable && s2_valid && predictionSent
```

But `s2_valid` is only cleared by redirect/flush or by `s2_fire`:

```scala
when(s1_fire)(s2_valid := true.B)
  .elsewhen(s2_flush)(s2_valid := false.B)
  .elsewhen(s2_fire)(s2_valid := false.B)
```

The output valid path is not gated by `io.enable`:

```scala
io.prediction.zipWithIndex.foreach { case (pred, i) =>
  pred.valid := s2_valid && s2_hitMask(i)
  ...
}

io.meta.valid := s2_valid
```

The existing BPU check catches the stale-output condition:

```scala
// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
when(io.toFtq.prediction.fire && abtb.io.prediction.map(_.valid).reduce(_ || _)) {
  assert(abtb.io.debug_startPc === s1_startPc)
}
```

The assertion is useful, but it also demonstrates that disabled/stale ABTB output can reach the BPU prediction fire path. The waveform evidence above shows that the same stale prediction window can be connected to ICache-observable activity and a stronger DCache data-dependent side channel.
