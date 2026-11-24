[###](### **url**) Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

HLVX.WU / HLVX.HU must check execute permission on the final physical address (PMA, then PMP), but must still report a load access fault (mcause=5) on failure. XiangShan already uses `hlvx` for the page-table X check. The final PMA/PMP path treats HLVX as a normal load (`TlbCmd.read`); the load pipe only consumes `pmp.ld`. A page with R=1 X=0 is read out and no trap is taken.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
hlvx-pma-xperm start
trap_count=0 mcause=0 mtval=0x0 mtval2=0x0 htval=0x0 hlvx=0xc33ca55a
FAIL hlvx bypassed PMA execute permission
HIT GOOD TRAP at pc = 0x800002ca
instrCnt = 8,627  cycleCnt = 46,983
```

`hlvx=0xc33ca55a` is the little-endian word of page bytes `5a a5 3c c3`. The destination register is written. There is no trap.

`HIT GOOD TRAP` is not a pass. This pin's NEMU takes the same wrong path (no trap, same GPR). Oracle is the H-extension spec + the AM self-check, not DUT==REF.

This is not a page-table bug: `ldPermFail := Mux(hlvx, perm.x, ...)` already exists. The AM uses bare translation so only the final PMA check is in the path.

Related existing work (kunminghu-v3 has no merged fix):
- Issue https://github.com/OpenXiangShan/XiangShan/issues/5995 (v2, same contract)
- PR https://github.com/OpenXiangShan/XiangShan/pull/6017 merged kunminghu-v2 only (`22cc0b6`), not an ancestor of kunminghu-v3
- PR https://github.com/OpenXiangShan/XiangShan/pull/6016 Open on kunminghu-v3
- Issue https://github.com/OpenXiangShan/XiangShan/issues/6614 closed not_planned (wrong template), not a fix


### Expected behavior

For HLVX.WU to a final physical page whose PMA is R=1 W=1 X=0 (NAPOT 4 KiB; default DRAM PMA is already R=1 X=0):

1. Final PMA/PMP must require execute permission (supervisor PMA must grant both execute and read).
2. Missing X must trap.
3. The exception must be a load access fault (mcause=5), not an instruction access fault.
4. The destination register must not receive the target bytes (must not become 0xc33ca55a).

ISA (riscv-isa-manual hypervisor.adoc): HLVX is like HLV.HU / HLV.WU except execute permission replaces read permission in the translation; supervisor PMA must grant both execute and read; HLVX cannot override machine-level PMP; failures still raise the same exceptions as other load instructions.

kunminghu-v2 already implements this with TlbCmd.read_exec (PR #6017, 22cc0b6). That commit is not an ancestor of kunminghu-v3.

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
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `io.tlbReq.bits.cmd := TlbCmd.read` is still present; there is no `TlbCmd.read_exec`.
  - NEMU commit id (if difftest failed with NEMU): n/a, no mismatch (DUT and this NEMU both skip the trap)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i lsu01-hlvx-pma-xperm-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.

### To Reproduce

Minimal AM workload: program hart PMA NAPOT 4KiB as R=1 W=1 C=1 X=0 over a known page, clear hstatus.SPVP, execute hlvx.wu on that page. DUT should raise load access fault (mcause=5). It instead writes back the page bytes and takes no trap.

Workload (source + bin) is attached: lsu01-hlvx-pma-xperm.zip
  - main.c
  - Makefile
  - build/lsu01-hlvx-pma-xperm-riscv64-xs.bin
    sha256 0a8b81fc0483e34277231ab3cc6ff21ddd3158075fad1aad7d7cff84d06d8296

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has io.tlbReq.bits.cmd := TlbCmd.read).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C lsu01-hlvx-pma-xperm ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i lsu01-hlvx-pma-xperm/build/lsu01-hlvx-pma-xperm-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
hlvx-pma-xperm start
trap_count=0 mcause=0 mtval=0x0 mtval2=0x0 htval=0x0 hlvx=0xc33ca55a
FAIL hlvx bypassed PMA execute permission
HIT GOOD TRAP at pc = 0x800002ca
instrCnt = 8,627  cycleCnt = 46,983
```

`--diff` does not abort: DUT and this NEMU both skip the trap, so they match. That is not a pass. The architectural check is the AM self-check (trap_count==0 → FAIL).

[lsu01-hlvx-pma-xperm.zip](https://github.com/user-attachments/files/32480628/lsu01-hlvx-pma-xperm.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d):

```
NewLoadUnit.scala: io.tlbReq.bits.cmd := TlbCmd.read
                   io.tlbReq.bits.hlvx := tlbHlvx
                   pmpUnaccessable = pmp.ld && tlbHit
PMA/PMP: resp.instr is raised only when TlbCmd.isExec(cmd).
v3 MMUBundle has read/write/exec only; no TlbCmd.read_exec.
```

A fix must not only flip cmd to exec if the load pipe then reports an instruction access fault. The spec requires the same exception as other loads (mcause=5). v2 PR #6017 adds TlbCmd.read_exec and maps execute-permission failure back to load AF. That is the intended shape.

Related, not this bug: HLV/HLVX/HSV PMP privilege uses tlbcsr.priv.dmode instead of hstatus.SPVP. Track PR #6164. Do not fold it into this issue.

Please do not close this as already fixed on v2. PR #6017 is not in kunminghu-v3. Prefer landing PR #6016.

DefaultConfig, HasHExtension=true. No extra inject.

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
