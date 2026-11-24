### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Ssdbltrp: with `menvcfg.DTE=0`, Ssdbltrp is unimplemented and `sstatus.SDT` is read-only 0. An unexpected trap taken in HS looks at that **visible** SDT. XiangShan already masks the `sstatus` read (`sstatus.SDT := regOut.SDT && menvcfg.DTE`), but `TrapHandleModule` still uses raw `mstatus.SDT`. A leftover raw latch after DTE is turned off therefore upgrades a delegated HS breakpoint into a double-trap (`mcause=16`) taken in M.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
csr001-hs-sdt-dte start
m_trap=1 mcause=16 s_trap=0 scause=0
FAIL csr001 leftover SDT raised HS double-trap
HIT GOOD TRAP at pc = 0x8000030e
instrCnt = 3,564  cycleCnt = 23,803
```

HS never entered (`s_trap=0`). The delegated `ebreak` is taken as M-mode double-trap instead of an HS breakpoint.

`HIT GOOD TRAP` is not a pass. This pin's NEMU takes the same wrong path. Oracle is Ssdbltrp (`norm:sstatus_sdt_trap` + DTE), not DUT==REF.

This is not CSR-006 (M-mode SRET to HS leaving SDT set) and not CSR-007 (DTE=0 leftover SDT clearing SIE on an `sstatus` write, issue #6381).

v2 PR #6256 (`sstatus.SDT` read mask) is merged on kunminghu-v2. origin/kunminghu-v3 already has `sstatus.SDT := regOut.SDT && menvcfg.DTE`. That is not this TrapHandle assignment. No matching v3 issue/PR found.

### Expected behavior

For leftover `mstatus.SDT=1` after `menvcfg.DTE` is written 0, with breakpoint delegated (`medeleg[3]=1`), MRET into HS that executes `ebreak`:

1. DTE=0 means Ssdbltrp is unimplemented. Visible `sstatus.SDT` must be 0. The leftover raw latch must not participate in HS double-trap.
2. The delegated breakpoint must enter HS (`s_trap≥1`, `scause=3`).
3. It must not be taken in M as double-trap (`mcause` must not be 16). After the HS handler `ecall`, M should see `mcause=9` (environment call from S).

ISA: `menvcfg.DTE=0` ⇒ behave as if Ssdbltrp were not implemented (`sstatus.SDT` read-only 0). Unexpected trap in HS becomes a double-trap only when the **visible** `sstatus.SDT` is 1 (`norm:sstatus_sdt_trap`).

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
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `s_EX_DT := handleTrapUnderHS && mstatus.SDT.asBool && hasTrap` is still present. Visible `sstatus.SDT := regOut.SDT && menvcfg.DTE` is already there; TrapHandle is not fed that masked value.
  - NEMU commit id (if difftest failed with NEMU): n/a, no mismatch (DUT and this NEMU both raise mcause=16)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i csr001-hs-sdt-dte-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.



### To Reproduce

Minimal AM workload: in M-mode, program an unlocked NAPOT identity PMP RWX, set then clear `menvcfg.DTE` leaving `mstatus.SDT=1`, delegate breakpoint, `mret` to HS `ebreak`. DUT should enter HS (`scause=3`). It instead takes M double-trap (`mcause=16`, `s_trap=0`).

Workload (source + bin) is attached: csr001-hs-sdt-dte.zip
  - main.c
  - Makefile
  - build/csr001-hs-sdt-dte-riscv64-xs.bin
    sha256 25733ab537ae49648f12c91731e0c18581e263ec8ddde61d4a382ff3871e70fb

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has TrapHandle `s_EX_DT` on raw `mstatus.SDT`).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C csr001-hs-sdt-dte ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i csr001-hs-sdt-dte/build/csr001-hs-sdt-dte-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
csr001-hs-sdt-dte start
m_trap=1 mcause=16 s_trap=0 scause=0
FAIL csr001 leftover SDT raised HS double-trap
HIT GOOD TRAP at pc = 0x8000030e
instrCnt = 3,564  cycleCnt = 23,803
```

`--diff` does not abort: DUT and this NEMU both raise double-trap. That is not a pass. The architectural check is the AM self-check (`mcause=16` → FAIL).

[csr001-hs-sdt-dte.zip](https://github.com/user-attachments/files/32512683/csr001-hs-sdt-dte.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d), `TrapHandleModule.scala`:

```
private val s_EX_DT  = handleTrapUnderHS && mstatus.SDT.asBool  && hasTrap
```

`MachineLevel.scala` already hides the bit on the `sstatus` alias:

```
sstatus.SDT := regOut.SDT && menvcfg.DTE
```

TrapHandle is wired `mstatus.regOut`, not the DTE-masked `sstatus`. Clearing DTE therefore does not stop `s_EX_DT`.

A fix should use the DTE-visible SDT for HS double-trap (e.g. `mstatus.SDT && menvcfg.DTE`, or the same value exported as `sstatus.SDT`).

Related, not this bug:

- CSR-006: M-mode SRET to HS leaves SDT set (`SretEvent` MuxCase). Separate assignment.
- CSR-007: DTE=0 leftover SDT still fires `writeSDT` and clears SIE. Issue #6381.
- v2 PR #6256: `sstatus.SDT` read-path mask. origin/v3 already has that line. Do not close this as already fixed by #6256.

Please do not close this as already fixed by #6256 or #6381.

DefaultConfig, HasHExtension=true. No extra inject. Bare `satp`. AM installs unlocked NAPOT PMP RWX so S-mode fetch is not an access fault (an earlier empty-PMP miss was `mcause=1`).

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
