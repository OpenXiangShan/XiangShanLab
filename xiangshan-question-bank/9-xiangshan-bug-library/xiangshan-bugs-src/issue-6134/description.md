### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

PBMT-IO instruction fetches can enter the IFU uncache path without the same last-commit / quiescence serialization used for PMP MMIO instruction fetches.

The current frontend treats PBMT uncache/IO instruction fetches as uncache fetches, but the IFU uncache serialization decision still follows only the PMP MMIO flag. As a result, a PBMT-IO fetch whose physical address is not PMP-MMIO can be routed to the uncache path and sent immediately, instead of waiting until the last committed instruction / frontend-backend quiescence condition.

The attached PoC installs:

1. a normal executable Sv39 mapping for VA `0x80000000` -> PA `0x80000000`;
2. a PBMT-IO executable alias for VA `0x40000000` -> PA `0x80000000`;
3. `MENVCFG.PBMTE=1`;
4. eight consecutive `divu` instructions before jumping through the PBMT-IO alias.

The reproduced waveform shows a PBMT-IO fetch entering IFU uncache while older backend work is still present.

First decisive event:

```text
cycle                 = 8717
s2_pbmt               = 2
s2_pmp_mmio           = 0
s3_use_uncache        = 1
s3_pmp_mmio           = 0
backend_empty         = 0
ibuffer_empty         = 1
is_first_instr        = 0
ifu_uncache_state     = 2
to_uncache_full_addr  = 0x80000114
```

One cycle later the InstrUncache entry emits a real TileLink Get:

```text
cycle        = 8718
tl_a_address = 0x80000110
```

The uncache response arrives later:

```text
cycle            = 8752
tl_d_data        = 0x0000006f00000063
delta_tl_to_grant = 34 cycles
```

This proves that a PBMT-IO instruction fetch can escape onto the uncache/interconnect path before the frontend/backend quiescence condition that is applied to PMP MMIO instruction fetches.

### Expected behavior

PBMT-IO instruction fetches should be serialized like side-effect-sensitive MMIO fetches.

If IFU routes a fetch to the uncache path because `Pbmt.isUncache(itlbPbmt)` is true, then the uncache unit should not decide last-commit waiting from PMP MMIO alone. For PBMT-IO, the request should either:

1. assert the same `isMmio`/side-effect-sensitive bit passed into `IfuUncacheUnit`, or
2. make `IfuUncacheUnit` explicitly check PBMT IO metadata before entering `SendReq`, for example by treating `io.req.bits.pbmt === Pbmt.io` as requiring `WaitLastCommit`.

The unsafe combination should not be possible:

```text
s2_pbmt            = 2
s2_pmp_mmio        = 0
s3_use_uncache     = 1
s3_pmp_mmio        = 0
ifu_uncache_state  = SendReq
is_first_instr     = 0
backend_empty      = 0
to_uncache.fire    = 1
```

### Environment


* RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
* XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`
* Local branch: `kunminghu-v3`


### To Reproduce

The attachment `xiangshan-pbmt-io-ifu-uncache-poc.zip` [xiangshan-pbmt-io-ifu-uncache-poc.zip](https://github.com/user-attachments/files/29328365/xiangshan-pbmt-io-ifu-uncache-poc.zip) contains:

1. `pbmt_ifu_poc.S`: minimal bare-metal PBMT-IO IFU uncache PoC.
2. `link.ld`: linker script placing `_start` at `0x80000000`.
3. `validate_pbmt_ifu_vcd.py`: monitor for the unsafe IFU uncache predicate.
4. `prove_pbmt_attack_chain.py`: monitor that connects the unsafe IFU event to an InstrUncache TileLink Get.
5. `prove_pbmt_external_timing.py`: monitor that connects the unsafe IFU event to TileLink grant timing feedback.
6. `validation_monitor_8div_7000_12000.json`, `attack_chain_monitor.json`, and `external_timing_monitor.json`: reproduced monitor outputs.
7. `validation_run_8div_7000_12000.log`, `validation_input_sha256.txt`, `pbmt_ifu_poc.objdump`, and `pbmt_ifu_poc.sections`: build/run metadata.
8. `side_effect_flash/`: a separate control PoC showing that the simulator has a visible flash read/return path for instruction-fetch reads. This control uses PMP-MMIO and is not the unsafe PBMT-IO path.

The large VCD files are not included in the attachment, but the commands below regenerate them.

Build the main PoC:

```bash
riscv64-unknown-elf-gcc \
  -nostdlib -nostartfiles -static \
  -march=rv64gc -mabi=lp64d \
  -T link.ld \
  -o pbmt_ifu_poc.elf \
  pbmt_ifu_poc.S

riscv64-unknown-elf-objcopy \
  -O binary \
  pbmt_ifu_poc.elf \
  pbmt_ifu_poc.bin

riscv64-unknown-elf-objdump \
  -dr pbmt_ifu_poc.elf \
  > pbmt_ifu_poc.objdump
```

Run the waveform-enabled XiangShan emulator:

```bash
emu \
  -C 12000 -b 7000 -e 12000 \
  -i pbmt_ifu_poc.bin \
  --no-diff \
  --dump-wave \
  --wave-path=validation_pbmt_ifu_8div_7000_12000.vcd \
  > validation_run_8div_7000_12000.log 2>&1
```

Parse the VCD:

```bash
python3 validate_pbmt_ifu_vcd.py \
  validation_pbmt_ifu_8div_7000_12000.vcd \
  > validation_monitor_8div_7000_12000.json

python3 prove_pbmt_attack_chain.py \
  validation_pbmt_ifu_8div_7000_12000.vcd \
  > attack_chain_monitor.json

python3 prove_pbmt_external_timing.py \
  validation_pbmt_ifu_8div_7000_12000.vcd \
  > external_timing_monitor.json
```

The reproduced result is:

```text
validation_monitor_8div_7000_12000.json:
  predicate                  = PASS
  pbmt_seen_count            = 3292
  all_uncache_fire_count     = 81
  unsafe_uncache_fire_count  = 43

attack_chain_monitor.json:
  predicate            = PASS
  unsafe_ifu_fire_count = 43
  tl_get_fire_count     = 81
  attack_chain_count    = 81

external_timing_monitor.json:
  predicate             = PASS
  tl_timing_chain_count = 11
  tl_target_get_count   = 81
  tl_grant_valid_count  = 81
```

First complete timing chain from `external_timing_monitor.json`:

```json
{
  "pbmt_io_observed": {
    "time": 8717,
    "s2_pbmt": 2,
    "s2_pmp_mmio": 0
  },
  "unsafe_ifu_to_uncache_fire": {
    "time": 8717,
    "s3_use_uncache": 1,
    "s3_pmp_mmio": 0,
    "backend_empty": 0,
    "ibuffer_empty": 1,
    "is_first_instr": 0,
    "ifu_uncache_state": 2,
    "to_uncache_full_addr": 2147483924
  },
  "tl_get_fire": {
    "time": 8718,
    "tl_a_address": 2147483920
  },
  "tl_grant_valid": {
    "time": 8752,
    "tl_d_data": 476747837475
  },
  "delta_ifu_to_tl": 1,
  "delta_tl_to_grant": 34
}
```


### Additional context

This is a frontend speculative side-effect-suppression bug.

Current source analysis on `kunminghu-v3`:

```scala
// src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala
s1_isMmio = s1_pmpMmio || Pbmt.isUncache(s1_itlbPbmt)
```

This routes PMP MMIO and PBMT uncache/IO fetches into the frontend uncache path. Later, IFU forwards PBMT metadata but only forwards PMP MMIO as `isMmio`:

```scala
// src/main/scala/xiangshan/frontend/ifu/Ifu.scala
pbmt   := s3_icacheMeta(0).itlbPbmt
isMmio := s3_icacheMeta(0).pmpMmio
```

`IfuUncacheUnit` then decides whether to wait from `isMmio` only:

```scala
// src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
private val reqIsMmio = io.req.valid && io.req.bits.isMmio

uncacheState := Mux(reqIsMmio, UncacheFsmState.WaitLastCommit, UncacheFsmState.SendReq)
itlbPbmt     := io.req.bits.pbmt
```

The latched PBMT value is later used for downstream memory-type signaling:

```scala
toUncache.bits.memBackTypeMM := !isMmio
toUncache.bits.memPageTypeNC := itlbPbmt === Pbmt.nc
```

This downstream use does not fix the serialization issue, because the FSM has already chosen `SendReq` instead of `WaitLastCommit` for PBMT-IO when `pmpMmio=0`.

Security impact:

* Proven: a PBMT-IO + `pmpMmio=0` instruction fetch can be issued early while older backend work is still present.
* Proven: the early fetch emits an InstrUncache TileLink Get and later receives a timing-visible grant.
* Proven separately: the simulator has a visible flash read/return path for instruction-fetch reads, using the `side_effect_flash/` control PoC.
* Not claimed as proven in one run: PBMT-IO + `pmpMmio=0` reaching the flash side-effect path. Existing flash physical addresses are PMP-MMIO, so that control PoC follows the safe serialized PMP-MMIO path.

The concern is that platforms using PBMT-IO mappings for side-effect-sensitive regions expect those instruction fetches to obey MMIO-like serialization. If PBMT-IO is treated as uncache for routing but not as MMIO for serialization, an early fetch can create observable bus/device timing before the last-commit safety condition.
