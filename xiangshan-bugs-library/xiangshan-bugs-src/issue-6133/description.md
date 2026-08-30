### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`LoadQueueUncache` can emit a full-buffer rollback redirect whose metadata names an MMIO load that was accepted into the uncache buffer, instead of the oldest MMIO load that failed allocation.

The trigger is a small bare-metal program that holds the ROB head with dependent divides, then issues twenty independent MMIO loads from `0x10000000..0x10000098`. This fills the 16-entry `LoadQueueUncache` and creates back-to-back cycles where some lanes can allocate and later lanes cannot.

The affected path is:

```scala
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
s2_enqValidVec(w) := s2_enqueue(w) && freeList.io.canAllocate(offset)

val reqNeedCheck = VecInit((0 until LoadPipelineWidth).map(w =>
  s2_enqueue(w) && !s2_enqValidVec(w)
))
...
io.rollback.valid := GatedValidRegNext(oldestRedirect.valid &&
                    !oldestRedirect.bits.robIdx.needFlush(io.redirect) &&
                    !oldestRedirect.bits.robIdx.needFlush(lastCycleRedirect) &&
                    !oldestRedirect.bits.robIdx.needFlush(lastLastCycleRedirect))
io.rollback.bits := RegEnable(oldestRedirect.bits, oldestRedirect.valid)
```

At VCD time `16586` / sampled cycle `3293`, the uncache freelist has one free slot:

```text
_freeList_io_validCount = 15
_freeList_io_canAllocate = [true, false, false]

lane 0 request: ROB 35 / FTQ 5 / offset 7 / paddr 0x10000090 / mmio 1
lane 1 request: ROB 36 / FTQ 5 / offset 9 / paddr 0x10000098 / mmio 1
```

Therefore ROB 35 is accepted and ROB 36 is the oldest rejected request. At VCD time `16590` / sampled cycle `3295`, `io_rollback_valid` asserts with:

```text
io_rollback_bits_robIdx_value = 35
io_rollback_bits_ftqIdx_value = 5
io_rollback_bits_ftqOffset = 7
io_rollback_bits_level = 1
```

The rollback pulse names the accepted lane instead of rejected ROB 36 / FTQ 5 / offset 9. The monitor also finds an earlier spurious rollback pulse at time `16540`: the source cycle had `canAllocate=[true,true,true]`, so no lane was rejected, but rollback still asserted for ROB 32 / FTQ 4 / offset 31.

This is a speculative/out-of-order memory-execution recovery bug. 


### Expected behavior

When `LoadQueueUncache` is full, the rollback redirect should name the oldest MMIO/NC load that failed to allocate an uncache-buffer entry.

It should not allow this combination:

```text
source cycle:
  free slots = 1
  lane 0 canAllocate = true   -> ROB35 accepted
  lane 1 canAllocate = false  -> ROB36 rejected

rollback two cycles later:
  io_rollback_valid = 1
  io_rollback_bits.robIdx = ROB35
```

The accepted request should not be reported as the flush-level memory-violation target. For MMIO/NC accesses, redirecting the wrong instruction can duplicate or reorder externally visible device accesses.

### Environment


  * RISC-V GCC used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
  * Toolchain path used locally: `/opt/riscv/bin`
  * XiangShan checkout branch: `kunminghu-v3`
  * XiangShan checkout HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`

### To Reproduce

The attachment `xiangshan-uncache-full-rollback-poc.zip` [xiangshan-uncache-full-rollback-poc.zip](https://github.com/user-attachments/files/29327468/xiangshan-uncache-full-rollback-poc.zip)  contains:

1. `poc_uncache_overflow.S`: bare-metal PoC.
2. `linker.ld`: linker script placing the workload at `0x80000000`.
3. `poc_uncache_overflow.{elf,bin,objdump}`: rebuilt PoC artifacts.
4. `validate_uncache_overflow_vcd.py`: hierarchy-based VCD monitor for the short validation window.
5. `validation_uncache_8200_8320.validation.json`: parsed short-window result showing rollback metadata mismatch.
6. `validation_uncache_8200_8320.validation.log`: short validation summary.
7. `validation_uncache_8200_8320_nodiff.run.log`: emulator log for the short validation run.
8. `parse_uncache_overflow_vcd.py`: fixed-ID monitor used for the long external-interface window.
9. `poc_uncache_overflow_5000_16000.monitor.json`: parsed long-window result showing `io_uncache_req` traffic.
10. `poc_uncache_overflow_5000_16000.monitor.log`: long-window monitor summary.
11. `poc_uncache_overflow_5000_16000.run.log`: emulator log for the long-window run.
12. `external_request_evidence.json`: compact evidence linking the bad rollback to a later external uncache request.

Large VCD files are not included in the zip. To regenerate the short validation waveform and parse it:

```bash
cd /path/to/unzipped/poc

/opt/riscv/bin/riscv64-unknown-elf-gcc \
  -nostdlib -nostartfiles \
  -march=rv64gc -mabi=lp64 \
  -T linker.ld \
  -o poc_uncache_overflow.elf \
  poc_uncache_overflow.S

/opt/riscv/bin/riscv64-unknown-elf-objcopy \
  -O binary \
  poc_uncache_overflow.elf \
  poc_uncache_overflow.bin

/opt/riscv/bin/riscv64-unknown-elf-objdump \
  -dr poc_uncache_overflow.elf \
  > poc_uncache_overflow.objdump

/root/HardwareAgent/XiangShan/build_vcd/emu \
  --max-cycles=8400 \
  -b 8200 -e 8320 \
  --dump-wave-full \
  --wave-path=validation_uncache_8200_8320.vcd \
  -i poc_uncache_overflow.bin \
  > validation_uncache_8200_8320_nodiff.run.log 2>&1

python3 validate_uncache_overflow_vcd.py \
  validation_uncache_8200_8320.vcd \
  validation_uncache_8200_8320.validation.json \
  > validation_uncache_8200_8320.validation.log
```

The reproduced short-window predicate is:

```json
{
  "mmio_or_nc_reqs_seen": true,
  "uncache_reqs_seen": false,
  "rollbacks_seen": true,
  "rollback_metadata_mismatch_seen": true,
  "reproduced": true
}
```

The decisive mismatches in `validation_uncache_8200_8320.validation.log` are:

```text
mismatch time=16540 cycle=70 rollback=rob32/ftq4/off31 expected=None
mismatch time=16590 cycle=95 rollback=rob35/ftq5/off7 expected=rob36/ftq5/off9
```

To regenerate the longer window that reaches the external uncache request interface:

```bash
cd /path/to/unzipped/poc

/root/HardwareAgent/XiangShan/build_vcd/emu \
  --max-cycles=16000 \
  -b 5000 -e 16000 \
  --dump-wave-full \
  --wave-path=poc_uncache_overflow_5000_16000.vcd \
  -i poc_uncache_overflow.bin \
  > poc_uncache_overflow_5000_16000.run.log 2>&1

python3 parse_uncache_overflow_vcd.py \
  poc_uncache_overflow_5000_16000.vcd \
  poc_uncache_overflow_5000_16000.monitor.json \
  > poc_uncache_overflow_5000_16000.monitor.log
```

The long-window predicate is:

```json
{
  "mmio_or_nc_reqs_seen": true,
  "uncache_reqs_seen": true,
  "rollbacks_seen": true,
  "rollback_metadata_mismatch_seen": true,
  "reproduced": true
}
```

The same bad rollback is seen at time `16590` / cycle `3295`. Later, the accepted ROB35 request reaches the external uncache request interface:

```text
time 16590: rollback valid, ROB35 / FTQ5 / offset7, expected rejected ROB36 / FTQ5 / offset9
time 18584: io_uncache_req_valid && io_uncache_req_ready, ROB35, addr 0x10000090
time 18684: io_uncache_req_valid && io_uncache_req_ready, ROB36, addr 0x10000098

### Additional context

This has security relevance for non-idempotent MMIO/NC regions. In this simulator run, I did not observe duplicate addresses and the simulated flash/MMIO model does not expose a device-state mutation. However, the issue is already externally visible at the uncache request interface: an accepted load is named by a flush-level rollback and later still issues as `io_uncache_req_valid && io_uncache_req_ready`.

For read-clear registers, FIFO-pop registers, status/ack registers, or other side-effecting MMIO reads, an incorrect replay, duplication, or reordering of the request can change device-visible state or expose stale/unauthorized device data to later architectural execution. The bug is therefore not just a performance artifact.

The local code shape also matches the likely fix direction: rollback valid and bits should advance in lockstep, and the recent revert-style change that restores plain `RegNext` behavior for the relevant request/redirect registers is consistent with this root cause.
