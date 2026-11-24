### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

HLV / HLVX / HSV may execute from M-mode, but their memory-access privilege for the final PMP check must follow `hstatus.SPVP`. XiangShan already uses SPVP for the DTLB page walk. Every DTLB PMP checker still uses `tlbcsr.priv.dmode`. In M-mode, PMP `passThrough` (`mode > 1`) ignores unlocked entries, so an M-mode `HLV.WU` with `SPVP=U` bypasses an unlocked `R=0` PMP the way a native M-mode load would.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
lsu02-hlv-spvp-pmp start
trap_count=0 mcause=0 hlv=0x44332211
FAIL lsu02 M-mode HLV bypassed unlocked PMP under SPVP=U
HIT GOOD TRAP at pc = 0x80000262
instrCnt = 3,608  cycleCnt = 25,144
```

`hlv=0x44332211` is the little-endian word of page bytes `11 22 33 44`. The destination register is written. There is no trap.

`HIT GOOD TRAP` is not a pass. This pin's NEMU takes the same wrong path (no trap, same GPR). Oracle is the H-extension spec + the AM self-check, not DUT==REF.

This is not LSU-01 (HLVX final PMA/PMP execute permission). The page-walk privilege path is already SPVP; only the MemBlock PMP `p.apply` mode is wrong.

Related existing work (kunminghu-v3 has no merged fix):
- Issue https://github.com/OpenXiangShan/XiangShan/issues/6126 (closed because a PR was filed, not because v3 merged)
- Issue https://github.com/OpenXiangShan/XiangShan/issues/6259 Open (NEMU-side variant)
- PR https://github.com/OpenXiangShan/XiangShan/pull/6250 merged kunminghu-v2 only (`04c0d15`), not an ancestor of kunminghu-v3
- PR https://github.com/OpenXiangShan/XiangShan/pull/6164 Open on kunminghu-v3


### Expected behavior

For M-mode `HLV.WU` with `hstatus.SPVP=U` to a page covered by an unlocked NAPOT 4 KiB PMP with `R=W=X=0`:

1. Final PMP must use effective privilege `hstatus.SPVP` (U), not current `dmode` (M).
2. Unlocked PMP must not be passed through. M-mode `passThrough` applies to native M accesses, not to HLV/HLVX/HSV under SPVP=U.
3. Missing R must trap.
4. The exception must be a load access fault (mcause=5).
5. The destination register must not receive the target bytes (must not become 0x44332211).

ISA (riscv-isa-manual hypervisor.adoc): HLV/HLVX/HSV effective privilege is `hstatus.SPVP`, including PMP.

kunminghu-v2 already implements this by passing the TLB-computed request mode into MemBlock (`p.apply(pmpMode, ...)`, PR #6250, 04c0d15). That commit is not an ancestor of kunminghu-v3.

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
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `p.apply(tlbcsr.priv.dmode, ...)` is still present on every DTLB PMP checker; there is no `pmpMode`.
  - NEMU commit id (if difftest failed with NEMU): n/a, no mismatch (DUT and this NEMU both skip the trap)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i lsu02-hlv-spvp-pmp-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

Minimal AM workload: in M-mode, bare translation, program an unlocked NAPOT 4KiB PMP with R=W=X=0 over a known page, clear hstatus.SPVP, execute hlv.wu on that page. DUT should raise load access fault (mcause=5). It instead writes back the page bytes and takes no trap.

Workload (source + bin) is attached: lsu02-hlv-spvp-pmp.zip
  - main.c
  - Makefile
  - build/lsu02-hlv-spvp-pmp-riscv64-xs.bin
    sha256 5394d0213a40942c2e2e0b10e94e7c92f4711d029a40a0f4109469f515986e81

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has p.apply(tlbcsr.priv.dmode, ...) on DTLB PMP checkers).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C lsu02-hlv-spvp-pmp ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i lsu02-hlv-spvp-pmp/build/lsu02-hlv-spvp-pmp-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
lsu02-hlv-spvp-pmp start
trap_count=0 mcause=0 hlv=0x44332211
FAIL lsu02 M-mode HLV bypassed unlocked PMP under SPVP=U
HIT GOOD TRAP at pc = 0x80000262
instrCnt = 3,608  cycleCnt = 25,144
```

`--diff` does not abort: DUT and this NEMU both skip the trap, so they match. That is not a pass. The architectural check is the AM self-check (trap_count==0 → FAIL).

[lsu02-hlv-spvp-pmp.zip](https://github.com/user-attachments/files/32501529/lsu02-hlv-spvp-pmp.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d):

```
TLB.scala:    mode = Mux(isHyperInst, csr.priv.spvp, mode_tmp)   // page-walk is correct
MemBlock.scala: p.apply(tlbcsr.priv.dmode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
PMP.scala:    passThrough = (mode > 1.U); ignore = passThrough && !pmp.cfg.l
```

M-mode HLV therefore presents mode=M to PMP. Unlocked entries are ignored. SPVP=U never reaches this checker.

A fix should pass the TLB-computed request mode into MemBlock for HLV/HLVX/HSV only. Normal DTLB requests still use current privilege. That is what v2 PR #6250 does (`p.apply(pmpMode, ...)`).

Related, not this bug: HLVX final PMA/PMP execute permission (LSU-01). Track PR #6016. Do not fold it into this issue.

Please do not close this as already fixed on v2. PR #6250 is not in kunminghu-v3. Prefer landing PR #6164. Issue #6126 being closed does not mean v3 merged the patch.

DefaultConfig, HasHExtension=true. No extra inject. Bare satp/vsatp/hgatp so the VA is the physical page.

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
