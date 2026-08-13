### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`DRET` executed outside Debug Mode is correctly classified as an illegal instruction by the CSR permission logic, but the CSR wrapper still treats the instruction as an xRET-style jump and emits a backend redirect.  The redirect target is taken from `NewCSR.io.xretTargetPc.bits` even when the current `DRET` is illegal and does not produce a valid DRET target.  As a result, an illegal `DRET` can reuse a stale target left by an earlier legal xRET.

This creates two observable incorrect behaviors:

1. An architecturally illegal instruction can simultaneously raise `EX_II` and send a frontend redirect.
2. The stale xRET target can be selected by a prior secret-dependent legal xRET target, and the later illegal `DRET` creates a matching secret-dependent FTQ/IFU fetch and ICache refill footprint.

The attached PoCs demonstrate both behaviors:

1. `stale_target_control/` trains `xretTargetPc` with a legal `MRET`, then executes an illegal `DRET`.  The illegal `DRET` writes back with `EX_II=1`, `redirect_valid=1`, and `redirect_target` equal to the previous legal `MRET` target.
2. `secret_fetch_oracle/` selects the previous legal `MRET` target from a secret bit, executes `fence.i`, then executes illegal `DRET`.  In the immediate post-DRET oracle window, only the secret-selected target is fetched/refilled by the frontend/ICache.

First decisive stale-target event:

```text
VCD time             = 16888
illegal instruction  = DRET outside Debug Mode
DRET PC              = 0x80000074
wb_valid             = 1
wb_ex2 / EX_II       = 1
wb_redir_valid       = 1
wb_redir_target      = 0x80000090
wb_redir_fullTarget  = 0x80000090
wb_redir_igpf/ipf/iaf = 0/0/0
```

The controlled target then reaches the frontend:

```text
VCD time             = 16898
ftq_to_ifu_valid     = 1
ftq_to_icache_valid  = 1
decoded fetch start  = 0x80000090
```

The secret-dependent fetch oracle result is:

```json
{
  "cross_variant_secret_oracle": "reproduced",
  "secret0_selected_target": "0x80000400",
  "secret0_selected_fetch_oracle_count": 9,
  "secret0_selected_refill_oracle_count": 4,
  "secret0_unselected_fetch_oracle_count": 0,
  "secret0_unselected_refill_oracle_count": 0,
  "secret1_selected_target": "0x80000800",
  "secret1_selected_fetch_oracle_count": 10,
  "secret1_selected_refill_oracle_count": 3,
  "secret1_unselected_fetch_oracle_count": 0,
  "secret1_unselected_refill_oracle_count": 0
}
```

Concrete oracle-window trace points:

```text
SECRET_VALUE=0
  illegal DRET writeback time = 17132
  stale redirect target       = 0x80000400
  selected-target FTQ redirect = 17136
  selected-target fetch        = 17142
  selected-target ICache refill = 17146
  unselected target 0x80000800 fetch/refill count in oracle window = 0

SECRET_VALUE=1
  illegal DRET writeback time = 17120
  stale redirect target       = 0x80000800
  selected-target FTQ redirect = 17124
  selected-target fetch        = 17130
  selected-target ICache refill = 17134
  unselected target 0x80000400 fetch/refill count in oracle window = 0
```

This is a frontend/ICache side-channel class impact.  I am not claiming a proven transient data-load/probe-array leak: a separate data-load gadget attempt did not reach LSQ and did not produce secret/probe DCache accesses.

### Expected behavior

An illegal `DRET` outside Debug Mode should trap as an illegal instruction and should not emit an xRET redirect to the frontend.

The unsafe combination should not be possible:

```text
instruction          = DRET
debugMode            = 0
EX_II                = 1
redirect_valid       = 1
redirect_target      = stale xRET target from an earlier legal xRET
```

The CSR wrapper should not consume `csrMod.io.xretTargetPc.bits` for a redirect unless the current xRET is legal and `xretTargetPc.valid` corresponds to the current instruction.  Possible fixes include gating the redirect with xRET legality / `xretTargetPc.valid`, or preventing xRET redirect generation when the CSR output carries `EX_II` / `EX_VI`.

### Environment

- `kunminghu-v3`
-  HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`


### To Reproduce

The attachment `xiangshan-illegal-dret-xret-oracle-poc.zip` [xiangshan-illegal-dret-xret-oracle-poc.zip](https://github.com/user-attachments/files/29379954/xiangshan-illegal-dret-xret-oracle-poc.zip) contains:

1. `stale_target_control/poc.S`: minimal illegal-DRET stale xRET target control PoC.
2. `stale_target_control/linker.ld`: linker script placing `_start` at `0x80000000`.
3. `stale_target_control/parse_stale_target_vcd.py`: monitor for the illegal-DRET stale redirect predicate and downstream frontend evidence.
4. `stale_target_control/reproduce.sh`: build/run/parse helper for the stale-target control PoC.
5. `stale_target_control/stale_target.monitor.json`, `stale_target_control/stale_target.monitor.log`, `stale_target_control/stale_target.run.log`, and `stale_target_control/poc.objdump`: reproduced monitor outputs and metadata.
6. `secret_fetch_oracle/poc.S`: secret-selected stale xRET target fetch-oracle PoC.
7. `secret_fetch_oracle/linker.ld`: linker script placing `_start` at `0x80000000`.
8. `secret_fetch_oracle/parse_fetch_oracle_vcd.py`: monitor for illegal-DRET redirect, selected-target fetch, and selected-target ICache refill.
9. `secret_fetch_oracle/compare_fetch_oracle.py`: cross-variant checker for `SECRET_VALUE=0` and `SECRET_VALUE=1`.
10. `secret_fetch_oracle/reproduce.sh`: builds both secret variants, runs the waveform-enabled emulator, parses both traces, and compares the results.
11. `secret_fetch_oracle/secret0.monitor.json`, `secret_fetch_oracle/secret1.monitor.json`, monitor logs, run logs, objdumps, and `comparison.log`: reproduced outputs.
12. `attachment_manifest.txt` and `sha256sums.txt`: attachment metadata.

The large VCD files are not included in the attachment, but the commands below regenerate them.  The reproduce scripts accept `EMU_VCD=/path/to/waveform-enabled/emu`.

Run the stale-target control PoC:

```bash
cd stale_target_control
EMU_VCD=/path/to/emu ./reproduce.sh
cat stale_target.monitor.log
```

Expected result:

```json
{
  "predicate_result": "reproduced",
  "fetch_side_effect_result": "reproduced",
  "frontend_to_backend_result": "not_reproduced",
  "backend_lsq_result": "not_reproduced",
  "data_side_effect_result": "not_reproduced",
  "controlled_wb_count": 1,
  "controlled_ftq_count": 12,
  "post_illegal_fetch_req_count": 11,
  "post_illegal_icache_resp_count": 3,
  "post_illegal_victim_frontend_cf_count": 0,
  "post_illegal_secret_load_lsq_req_count": 0,
  "post_illegal_probe_load_lsq_req_count": 0
}
```

Run the secret-dependent fetch oracle PoC:

```bash
cd ../secret_fetch_oracle
EMU_VCD=/path/to/emu ./reproduce.sh
cat comparison.log
```

Expected result:

```json
{
  "cross_variant_secret_oracle": "reproduced",
  "secret0_selected_fetch_oracle_count": 9,
  "secret0_selected_refill_oracle_count": 4,
  "secret0_selected_target": "0x80000400",
  "secret0_unselected_fetch_oracle_count": 0,
  "secret0_unselected_refill_oracle_count": 0,
  "secret1_selected_fetch_oracle_count": 10,
  "secret1_selected_refill_oracle_count": 3,
  "secret1_selected_target": "0x80000800",
  "secret1_unselected_fetch_oracle_count": 0,
  "secret1_unselected_refill_oracle_count": 0
}
```

### Additional context

Current source analysis on `kunminghu-v3`:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
private val dret_EX_II = dret && !debugMode
private val dretIllegal = dret_EX_II

io.out.Xret_EX_II := mnret_EX_II || mret_EX_II || sret_EX_II || dret_EX_II
io.out.hasLegalDret := dret && !dretIllegal
```

`NewCSR` only produces a DRET target for legal DRET:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
dretEvent.valid := legalDret
```

and `xretTargetPc.bits` is held from previous legal xRET target updates:

```scala
// src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
private val xretTargetUpdate =
  mnretEvent.out.targetPc.valid ||
  mretEvent.out.targetPc.valid  ||
  sretEvent.out.targetPc.valid  ||
  dretEvent.out.targetPc.valid

io.xretTargetPc.bits := DataHoldBypass(..., xretTargetUpdate)
```

However, the wrapper generates redirect based on syntactic xRET-like jump classification, not xRET legality:

```scala
// src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
val isXRet = valid && func === CSROpType.jmp && !isEcall && !isEbreak
val isXRetReg = RegEnable(isXRet, false.B, io.in.fire)

io.out.bits.res.redirect.get.valid := io.out.valid && isXRetReg
redirect.fullTarget := csrMod.io.xretTargetPc.bits.pc
redirect.target     := csrMod.io.xretTargetPc.bits.pc
redirect.backendIPF := csrMod.io.xretTargetPc.bits.raiseIPF
redirect.backendIAF := csrMod.io.xretTargetPc.bits.raiseIAF
redirect.backendIGPF := csrMod.io.xretTargetPc.bits.raiseIGPF
```

Security impact:

* illegal `DRET` outside Debug Mode can raise `EX_II` and still emit an xRET frontend redirect.
* the redirect target is stale xRET state from an earlier legal xRET.
* if the previous legal xRET target is secret-selected, the later illegal `DRET` creates a secret-dependent FTQ/IFU fetch and ICache refill footprint.
