### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Sdtrig: a two-trigger chain of execute(t0, `chain=1`) then matching load(t1) must fire when both match in program order. XiangShan splits trigger CSRs: execute/fetch updates go to the frontend (`isFetchTrigger = EXECUTE`); load/store updates go to MemBlock (`isMemAccTrigger = STORE || LOAD`). Programming t0 as execute therefore never sets `memTriggerUpdate`, so MemBlock's copy of the predecessor tdata stays stale and the chain does not fire.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt, `--no-diff`:

```
csr010-trig-chain start
traps=0 mcause=0 dummy=0x55
FAIL csr010 execute-load chain did not fire
HIT GOOD TRAP at pc = 0x80000264
instrCnt = 3,064  cycleCnt = 21,869
```

The AM `jal`s the execute target then loads `cell=0x55`. No breakpoint (`traps=0`). `dummy=0x55` is the load result; the chain did not stop the load.

`HIT GOOD TRAP` is not a pass. Oracle is Sdtrig chain, not DUT==REF. This run used `--no-diff` (AM self-check). It is not a NEMU mismatch dump.

No matching issue/PR found on kunminghu-v3.

### Expected behavior

For mcontrol6 t0 = execute + M + chain, tdata2 = `trig_target`; t1 = load + M, tdata2 = `&cell`; then `jal trig_target` followed by `ld cell`:

1. Both triggers match in program order. The chain must fire.
2. The hart must take a breakpoint exception (`mcause=3`, `traps≥1`) before or instead of completing the load as a normal `0x55`.
3. `traps=0 dummy=0x55` is not a legal outcome for this chain.

ISA (Sdtrig): if trigger *i* has `chain=1`, it is ANDed with trigger *i+1*. Both must match. Execute then load is a legal chain.

### Environment

- Hardware
  - CPU: AMD EPYC 7H12 64-Core Processor
  - Memory (GB): 1007
  - Storage (GB): 4946 (NFS home; 2297 available)
- Software
  - Operating system: Ubuntu 24.04.3 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - clang version: Ubuntu clang version 19.1.1 (1ubuntu1~24.04.2)
  - java version: openjdk version "21.0.12" 2026-07-21
  - mill version: Mill Build Tool version 0.12.3
- Repo
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `memTriggerUpdate` is still `isMemAccTrigger` only (`STORE || LOAD`). Execute programming still only raises `frontendTriggerUpdate`.
  - NEMU commit id (if difftest failed with NEMU): n/a, reproduced with `--no-diff` (AM self-check is the oracle)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i csr010-trig-chain-riscv64-xs.bin --max-instr=20000 --no-diff`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.

### To Reproduce

Minimal AM workload: PMP allow-all, program t0 execute+chain on `trig_target`, t1 load on `cell`, `jal trig_target` then `ld cell`. DUT should take breakpoint. It completes the load with `traps=0`.

Workload (source + bin) is attached: csr010-trig-chain.zip
  - main.c
  - Makefile
  - build/csr010-trig-chain-riscv64-xs.bin
    sha256 ace603b2033961f6d6ffbaf98fb4f07b8b9d90443c58a0c809609814c0ec50a3

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has `memTriggerUpdate` as mem-access-only).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C csr010-trig-chain ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i csr010-trig-chain/build/csr010-trig-chain-riscv64-xs.bin \
         --max-instr=20000 \
         --no-diff

Observed, twice, identical instrCnt:

```
csr010-trig-chain start
traps=0 mcause=0 dummy=0x55
FAIL csr010 execute-load chain did not fire
HIT GOOD TRAP at pc = 0x80000264
instrCnt = 3,064  cycleCnt = 21,869
```

`--no-diff` is intentional. The architectural check is the AM self-check (`traps=0` → FAIL). Do not treat `HIT GOOD TRAP` as a pass.

[csr010-trig-chain.zip](https://github.com/user-attachments/files/32513617/csr010-trig-chain.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d), `Debug.scala`:

```
val frontendTriggerUpdate =
  tdata1Update && tdata1TypeWdata.isLegal && mcontrol6Wdata.isFetchTrigger ||
    mcontrol6Selected.isFetchTrigger && (triggerUpdate || tdata3Update)

val memTriggerUpdate =
  tdata1Update && tdata1TypeWdata.isLegal && mcontrol6Wdata.isMemAccTrigger ||
    mcontrol6Selected.isMemAccTrigger && triggerUpdate
```

`DebugLevel.scala`: `isFetchTrigger = EXECUTE`, `isMemAccTrigger = STORE || LOAD`. Execute programming therefore never asserts `io.out.memTrigger.tUpdate`. MemBlock chain logic that needs the predecessor's tdata does not see t0.

A fix should also push execute (chain predecessor) tdata into MemBlock, or evaluate chain match in one place that sees both execute and load hits.

This is not a “triggers disabled in M” bug: t0/t1 both set `M`. Action is breakpoint exception (action=0), not debug-mode entry. DefaultConfig.

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
