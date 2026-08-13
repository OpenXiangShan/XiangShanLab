### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v3)

### Describe the bug

A load breakpoint trigger can be recognized too late to suppress the S1 DCache request/lookup. The load is eventually marked as a breakpoint exception and killed in S2, but the S1 DCache kill signal does not include the breakpoint trigger condition. As a result, a trigger-matched load can still access DCache state before exception recovery.

The attached PoC models a one-bit secret that selects between two hot same-set load addresses. Both candidate addresses have load breakpoint triggers installed. The selected load should become a breakpoint exception before any memory-system side effect. Instead, the selected trigger-killed hit updates DCache replacement/access metadata. A later same-set eviction then leaves a different cache-resident line depending on the selected secret value, and normal post-trap probes observe the difference.

For `SECRET_SELECT=1`, the selected line is `target_line`:

```text
trigger event:
  time              = 17512 / 17513
  selected vaddr    = 0x80004000
  trigger vaddr     = 0x80004000
  s1_kill           = 0
  s1_internal_kill  = 0
  s2_kill           = 1

killed-hit metadata update:
  time              = 17516 / 17517
  set               = 0
  way               = 0
  way_en            = 1
  recent S2 miss    = 0
  recent S2 kill    = 1

post-trap probes:
  target_line       = miss 0 at time 18186
  control_line      = miss 1 at time 18404
```

For `SECRET_SELECT=0`, the selected line is `control_line`:

```text
trigger event:
  time              = 17512 / 17513
  selected vaddr    = 0x80008000
  trigger vaddr     = 0x80008000
  s1_kill           = 0
  s1_internal_kill  = 0
  s2_kill           = 1

killed-hit metadata update:
  time              = 17516 / 17517
  set               = 0
  way               = 3
  way_en            = 8
  recent S2 miss    = 0
  recent S2 kill    = 1

post-trap probes:
  target_line       = miss 1 at time 18186
  control_line      = miss 0 at time 18426
```

The differential monitor result is:

```text
predicate_result = differential_plru_cache_oracle_reproduced

checks:
  secret1_predicate                         = true
  secret0_predicate                         = true
  same_addresses                            = true
  same_set_stride_0x4000                    = true
  secret1_target_trigger_not_s1_killed      = true
  secret0_control_trigger_not_s1_killed     = true
  secret1_killed_target_metadata_update     = true
  secret0_killed_control_metadata_update    = true
  secret1_probe_target_hit                  = true
  secret1_probe_control_miss                = true
  secret0_probe_target_miss                 = true
  secret0_probe_control_hit                 = true
```

This means the breakpoint-triggered load is architecturally squashed, but it can still leave a secret-dependent, post-trap cache state through DCache replacement/access metadata.

### Expected behavior

A load that matches a breakpoint trigger should not perform DCache side effects before the breakpoint exception/debug action suppresses the load. At minimum, the unsafe combination below should not be possible:

```text
load breakpoint trigger matched
s1 DCache kill       = 0
s2 kill              = 1
DCache hit           = 1
replace_access.valid = 1
access_flag.valid    = 1
post-trap hit/miss   = depends on selected trigger-matched address
```

If the S1 DCache request is killed for TLB exceptions, it should also be killed for load breakpoint exceptions. If timing prevents using the trigger result in the S1 kill path directly, the DCache hit-side metadata updates should still be gated so that trigger-killed loads cannot update replacement/access state.

### Environment

- branch: `kunminghu-v3`
- HEAD: `3931c5112c528299a23c256bdd77fb90813afa6e`


### To Reproduce

The attachment `xiangshan-trigger-load-dcache-state-poc.zip` [xiangshan-trigger-load-dcache-state-poc.zip](https://github.com/user-attachments/files/29381189/xiangshan-trigger-load-dcache-state-poc.zip) contains:

1. `exploit_plru_oracle_leak.S`: same-set PLRU/cache oracle PoC.
2. `link.ld`: linker script placing `_start` at `0x80000000`.
3. `run_exploit_cache_leak.sh`: build/run harness for the VCD-enabled emulator.
4. `monitor_plru_oracle.py`: VCD monitor for trigger events, metadata updates,
   and post-trap probe hit/miss results.
5. `compare_plru_oracle.py`: differential gate for the two secret values.
6. `monitor_replacement_metadata.py`: supporting metadata monitor.
7. `monitor_external_miss_leak.py`: supporting cold-miss/MSHR/external acquire
   monitor.
8. `exploit_plru_oracle_compare.json`: reproduced differential result.
9. `exploit_plru_oracle_secret1_v3_0_12500.monitor.json` and
   `exploit_plru_oracle_secret0_v3_0_12500.monitor.json`: reproduced monitor
   outputs.
10. `exploit_plru_oracle_secret*_v3.{bin,elf,objdump,sym}` and
    `exploit_plru_oracle_secret*_v3_0_12500.run.log`: build/run metadata.
11. `exploit_cache_leak_secret*_external_miss_*.json`: supporting evidence that
    cold miss requests are canceled before MSHR allocation/external acquire in
    this PoC family.

The large VCD files are not included in the attachment, but the commands below
regenerate them.

Build and run the `SECRET_SELECT=1` case:

```bash
SECRET_SELECT=1 ASM_SOURCE=exploit_plru_oracle_leak.S \
  PREFIX=exploit_plru_oracle_secret1_v3 \
  CYCLES=12500 WAVE_BEGIN=0 WAVE_END=12500 ./run_exploit_cache_leak.sh

./monitor_plru_oracle.py exploit_plru_oracle_secret1_v3_0_12500.vcd \
  --symbols exploit_plru_oracle_secret1_v3.sym \
  --secret-select 1 \
  --json-out exploit_plru_oracle_secret1_v3_0_12500.monitor.json
```

Build and run the `SECRET_SELECT=0` case:

```bash
SECRET_SELECT=0 ASM_SOURCE=exploit_plru_oracle_leak.S \
  PREFIX=exploit_plru_oracle_secret0_v3 \
  CYCLES=12500 WAVE_BEGIN=0 WAVE_END=12500 ./run_exploit_cache_leak.sh

./monitor_plru_oracle.py exploit_plru_oracle_secret0_v3_0_12500.vcd \
  --symbols exploit_plru_oracle_secret0_v3.sym \
  --secret-select 0 \
  --json-out exploit_plru_oracle_secret0_v3_0_12500.monitor.json
```

Run the differential gate:

```bash
./compare_plru_oracle.py \
  --secret1 exploit_plru_oracle_secret1_v3_0_12500.monitor.json \
  --secret0 exploit_plru_oracle_secret0_v3_0_12500.monitor.json \
  --json-out exploit_plru_oracle_compare.json
```

The reproduced result is:

```text
exploit_plru_oracle_compare.json:
  predicate_result = differential_plru_cache_oracle_reproduced

SECRET_SELECT=1:
  expected_secret_line = target
  target_line          = 0x80004000
  control_line         = 0x80008000
  evict_line           = 0x80014000
  target probe miss    = 0
  control probe miss   = 1

SECRET_SELECT=0:
  expected_secret_line = control
  target_line          = 0x80004000
  control_line         = 0x80008000
  evict_line           = 0x80014000
  target probe miss    = 1
  control probe miss   = 0
```

### Additional context

Source analysis on the reproduced `kunminghu-v3` checkout:

```scala
// src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
val killDCache = kill || tlbMiss || tlbException

val triggerAction = loadTrigger.io.toLoadStore.triggerAction
val bp = TriggerAction.isExp(triggerAction)

val exception = tlbException || bp

io.dcacheKill := killDCache
```

`bp` is included in the exception path, but not in `killDCache`. Therefore a breakpoint-triggered load can still issue a DCache lookup before the exception is observed downstream.

Cold miss requests are later canceled by S2 kill:

```scala
// src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
io.miss_req.bits.cancel := io.lsu.s2_kill || s2_tag_error || s2_btot_occupy_fail
```

The current `kunminghu-v3` `MissQueue` has parallel enqueue logic, but the cancel gating is the same at the accepted request boundary. `MissReq.cancel` is documented as part of the enqueue condition:

```scala
// src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
// Enqueue logic uses req.valid && !cancel && !wbq_block_miss_req
// - LoadPipe: io.lsu.s2_kill (...), plus s2_tag_error and s2_btot_occupy_fail
val cancel = Bool()
```

and the parallel pipe registers suppress alloc/merge when the request is canceled:

```scala
// src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
parallel_pipe_regs(i).alloc := ((analysis.strategy(i) & 1.U) =/= 0.U) &&
                                (analysis.compress_group(i) === i.U) &&
                                !io.queryMQ(i).req.bits.cancel &&
                                !io.wbq_block_miss_req(i)

parallel_pipe_regs(i).merge := ((analysis.strategy(i) & 2.U) =/= 0.U) &&
                                (analysis.compress_group(i) === i.U) &&
                                !io.queryMQ(i).req.bits.cancel &&
                                !io.wbq_block_miss_req(i)
```

The external acquire path is then driven from `parallel_pipe_regs(i).alloc`, so a canceled load miss does not allocate a pipe-reg acquire in the reproduced tests:

```scala
// src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
acquire_from_pipereg_vec(i).valid := parallel_pipe_regs(i).alloc &&
                                     !can_merge_store_from_pipe(i) &&
                                     !io.wfi.wfiReq
```

However, the hit-side replacement/access metadata updates do not appear to be gated by the load kill/exception condition:

```scala
// src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
io.replace_access.valid := s3_valid && s3_hit
io.access_flag_write.valid := s3_valid && s3_hit && !s3_is_prefetch
```

Security impact:

* a secret-dependent breakpoint-triggered load reaches the DCache request/lookup path before S1 DCache kill is asserted.
* if the selected line is hot, the trigger-killed hit updates replacement/access metadata.
* that metadata update can be converted into a persistent post-trap cache hit/miss oracle with ordinary DCache probes.

The concern is that trigger-matched loads are expected to suppress memory-system side effects before they can encode protected address information intomicroarchitectural state. In the reproduced PoC, the selected protected load address is recoverable after exception recovery via cache replacement state.
