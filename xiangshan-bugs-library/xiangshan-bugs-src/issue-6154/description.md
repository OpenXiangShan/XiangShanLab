### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A scalar load in `NewLoadUnit` S3 can still assert ROB writeback, integer RF writeback valid, and backend integer RF write-enable in the same cycle where the load is killed by a redirect.

  The decisive waveform event is:

  ```json
  {
    "time": 8320,
    "load_unit": 1,
    "redirect_robIdx": { "flag": 0, "value": 9 },
    "writeback_robIdx": { "flag": 0, "value": 22 },
    "toRob_valid": "1",
    "toIntRf_valid": "1",
    "backend_rf_port": 7,
    "backend_rf_wen": "1",
    "backend_rf_pdest": 20,
    "cancel": "0"
  }
  ```

  The writeback ROB index [0,22] is younger than the redirect ROB index [0,9], so this load should be killed by the redirect. However, the scalar load still drives the ROB/RF writeback path.

  Security impact:
  - A redirect-killed scalar load can still drive ROB/RF writeback signals.
  - The data value carried by the killed load is PoC-controlled.
  - The killed load data reaches the backend integer RF write data bus.
  - A physical RF write-bus power/EM observer can distinguish Hamming-weight classes from the killed wrong-path value.
  - Under a spatial RF bitline observer model, exact held-out values are recoverable, including 0xdeadbeefcafebabe.
  - Post-redirect fastReplay activity was also observed for killed loads.

### Expected behavior

A load killed by redirect, including a load for which robIdx.needFlush(redirect) is true, should not assert architectural or microarchitectural writeback side effects.

The following combination should be impossible for the same killed scalar load:

redirect valid = 1
robIdx.needFlush(redirect) = true
io.ldout.toRob.valid = 1
io.ldout.toIntRf.valid = 1
backend integer RF wen = 1

The scalar path should be gated consistently with the vector path: killed S3 loads should not drive ROB writeback, integer RF writeback, LQ writeback, exception reporting, or fastReplay side effects.

### Environment

  Branch: kunminghu-v3
  Commit: 3931c5112c528299a23c256bdd77fb90813afa6e


### To Reproduce

I provide a compact PoC attachment  [xiangshan-killed-load-rf-sidechannel-poc.zip](https://github.com/user-attachments/files/29382234/xiangshan-killed-load-rf-sidechannel-poc.zip).

The attachment contains only PoC sources, scripts, and monitor JSON evidence. It does not include this markdown issue report.

```text
secret_writeback_train.S
secret_probe.S
secret_probe_aligned.S
secret_probe_aligned_a3.S
secret_probe_direct_ptr.S
secret_probe_gap.S
secret_delayed_writeback.S
secret_fastreplay_*.S
linker.ld
run_rf_train_once.sh
run_*_once.sh
monitor_secret_probe_vcd.py
rf_bus_leak_poc.py
rf_power_sidechannel_poc.py
rf_hw_template_attack.py
rf_bitline_exact_recovery.py
prove_security_vulnerability.py
scan_monitor_security_candidates.py
*.monitor.json
```

Large generated VCDs, ELF files, raw logs, and this issue report are not included in the attachment. The scripts regenerate the traces.

Example commands:

```bash
./run_rf_train_once.sh secret64 64
./run_rf_train_once.sh bitline_attack_deadbeef 0xdeadbeefcafebabe
./prove_security_vulnerability.py
```

Expected proof summary:

```text
security_vulnerability_proven = true
rf_power_sidechannel_leak_observed = true
rf_hw_template_attack_success = true
rf_bitline_exact_recovery_success = true
software_timing_leak_proven = false
```

Exact-value RF bitline recovery command:

```bash
./rf_bitline_exact_recovery.py \
  --zero-template train_secret0_8000_8500.monitor.json \
  --attack train_hw1_value1_8000_8500.monitor.json=0x1 \
  --attack train_hw8_attack_alt_8000_8500.monitor.json=0x55aa \
  --attack train_hw16_attack_alt_8000_8500.monitor.json=0x5555aaaa \
  --attack train_hw32_low32_8000_8500.monitor.json=0xffffffff \
  --attack train_bitline_attack_deadbeef_8000_8500.monitor.json=0xdeadbeefcafebabe
```

Expected result:

```text
rf_bitline_exact_recovery_success = true
same_microarchitectural_context = true
all_events_are_redirect_killed = true
all_events_drive_rf_write = true
held_out_attack_traces = true
exact_values_match = true
```

Optional scan for pure software-observable candidates:

```bash
./scan_monitor_security_candidates.py --limit 5
```

Expected result:

```text
software_observable_candidate_found = false

### Additional context

The suspected source-level issue is in:

```text
src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
```

The following source-level checks were made against the current GitHub `kunminghu-v3` source as well as the tested local commit.

S3 computes a kill condition similar to:

```scala
val kill = io.kill || robIdx.needFlush(redirect)
```

However, the scalar writeback path is not consistently gated by `!kill`.

The scalar load output valid is formed without `!kill`:

```scala
val ldoutValid = pipeIn.valid && shouldWriteback && !isVector && endPipe
```

The integer RF writeback valid path is also formed without `!kill`:

```scala
port.valid := uop.rfWen && pipeIn.valid && endPipe && shouldWakeup
```

The ROB writeback then consumes the ungated scalar valid:

```scala
ldout.toRob.valid := ldoutValid
```

Other S3 side-effect paths also appear ungated by `!kill`, for example LQ write and fastReplay:

```scala
val lqWriteValid = pipeIn.valid && !doFastReplay && endPipe
io.fastReplay.valid := pipeIn.valid && shouldFastReplay
```

In contrast, the vector load path includes the kill gate:

```scala
pipeIn.valid && !kill && shouldWriteback && isVector && endPipe
```

So scalar and vector load S3 kill handling are inconsistent.

The relevant propagation path is:

```text
NewLoadUnit.scala
  scalar ldout/toIntRf valid remains asserted after redirect kill

MemBlock.scala
  forwards newLoadUnits(i).io.ldout into load writeback ports

WbArbiter.scala
  arbitrates valid integer writebacks into backend RF write ports
```

In `WbArbiter.scala`, integer writeback arbitration accepts a load writeback when both the outer writeback and its integer RF sub-port are valid:

```scala
arbiterIn.valid := in.valid && in.bits.toIntRf.map(_.valid).getOrElse(false.B)
```

The backend integer RF write port is then generated from the arbitrated output fire:

```scala
intWbArbiterOut.map(x => x.bits.asIntRfWriteBundle(x.fire))
```

Security-relevant invariant:

```text
If S3 sees redirect kill for a load uop, then the same uop must not assert:
- ldout.toRob.valid
- ldout.toIntRf.valid
- backend RF write enable
- LQ write side effects
- exception side effects
- fastReplay side effects
```

The PoC demonstrates that this invariant is violated for scalar loads.

The security impact here is narrower than a normal architectural data leak, but still security-relevant: wrong-path secret-dependent load data can be driven onto the integer RF write data bus after the instruction is redirect-killed. A physical observer of RF write-bus power/EM/bitline activity can recover information about the killed wrong-path data. The provided monitor evidence includes held-out exact-value recovery for:

```text
0x0000000000000001
0x00000000000055aa
0x000000005555aaaa
0x00000000ffffffff
0xdeadbeefcafebabe
```
