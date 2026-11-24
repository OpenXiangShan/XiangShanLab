### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Ssdbltrp: an `SRET` instruction sets `sstatus.SDT` to 0. An M-mode `SRET` that returns to HS is still an `SRET`. XiangShan `SretEvent` only clears SDT for `sretInHS` or for M-mode SRET to U / VS / VU. M→HS is the `MuxCase` default and keeps `in.mstatus.SDT`. With `menvcfg.DTE=1` the leftover bit is visible in `sstatus`.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
csr006-m-sret-hs-sdt start
hs_sstatus=0x8000000201006020 sdt=1
FAIL csr006 M-mode SRET to HS left SDT set
HIT GOOD TRAP at pc = 0x80000252
instrCnt = 3,221  cycleCnt = 23,476
```

`hs_sstatus` bit 24 is SDT. After M-mode `SRET` to HS (`SPP=S`, `SPV=0`), HS still reads `sstatus.SDT=1`.

`HIT GOOD TRAP` is not a pass. This pin's NEMU takes the same wrong path (SDT still set, same CSR). Oracle is Ssdbltrp (`norm:sstatus_sdt_sret`), not DUT==REF.

This is not CSR-001 (leftover raw SDT with DTE=0 upgrading an HS trap to double-trap) and not CSR-007 (DTE=0 leftover SDT clearing SIE on an `sstatus` write, issue #6381).

No matching issue or PR found on kunminghu-v3.

### Expected behavior

For M-mode `SRET` to HS (`mstatus.SPP=S`, `hstatus.SPV=0`) with `menvcfg.DTE=1` and `mstatus.SDT` already 1:

1. The instruction is an `SRET`. It must set `sstatus.SDT` to 0 (`norm:sstatus_sdt_sret`). Privilege of the SRET (M vs HS) and destination (HS vs U/VS/VU) do not waive that.
2. After the SRET, HS `csrr sstatus` must show SDT=0 (bit 24 clear).
3. Destination `s_entry` must not observe `hs_sstatus=0x8000000201006020` with `sdt=1`.

ISA (riscv-isa-manual supervisor.adoc, `norm:sstatus_sdt_sret`): An `SRET` instruction sets the `SDT` bit to 0.

MDT on MRET is a different field (`smdbltrp`). This ticket is SDT on SRET.

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
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `out.mstatus.bits.SDT := MuxCase(in.mstatus.SDT.asBool, Seq(sretInHS -> 0.U, (sretInM && (sretToU || sretToVS || sretToVU)) -> 0.U))` is still present. There is no `sretInM && sretToHS` (or `sretInM` alone) arm that clears SDT.
  - NEMU commit id (if difftest failed with NEMU): n/a, no mismatch (DUT and this NEMU both leave SDT set)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i csr006-m-sret-hs-sdt-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

Minimal AM workload: in M-mode, program an unlocked NAPOT identity PMP RWX, set `menvcfg.DTE=1`, set `mstatus.SDT=1`, set `SPP=S` / `SPV=0` / `sepc=s_entry`, execute `sret`. HS `s_entry` reads `sstatus`. DUT should have SDT=0. It still has SDT=1.

Workload (source + bin) is attached: csr006-m-sret-hs-sdt.zip
  - main.c
  - Makefile
  - build/csr006-m-sret-hs-sdt-riscv64-xs.bin
    sha256 3d214e54cdca30a2398a0cf588b291849775ed2a91e1fd980cffc7b948717083

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has `SretEvent` MuxCase default-keep for M→HS SDT).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C csr006-m-sret-hs-sdt ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i csr006-m-sret-hs-sdt/build/csr006-m-sret-hs-sdt-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
csr006-m-sret-hs-sdt start
hs_sstatus=0x8000000201006020 sdt=1
FAIL csr006 M-mode SRET to HS left SDT set
HIT GOOD TRAP at pc = 0x80000252
instrCnt = 3,221  cycleCnt = 23,476
```

`--diff` does not abort: DUT and this NEMU both leave SDT set, so they match. That is not a pass. The architectural check is the AM self-check (`sdt=1` → FAIL).

[csr006-m-sret-hs-sdt.zip](https://github.com/user-attachments/files/32511754/csr006-m-sret-hs-sdt.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d), `CSREvents/SretEvent.scala`:

```
out.mstatus.bits.SDT := MuxCase(in.mstatus.SDT.asBool, Seq(
  sretInHS -> 0.U,
  (sretInM && (sretToU || sretToVS || sretToVU)) -> 0.U
))
```

There is no `sretToHS` / `sretToS` helper. M-mode SRET with `SPP=S` and `SPV=0` matches neither arm, so the default keeps `in.mstatus.SDT`. The nearby comment (`sret will alway leave M mode`) is about MPRV/MDT, not a reason to keep SDT on M→HS.

A fix should clear SDT on every SRET that writes `mstatus`/`sstatus` (every `sretInHSorM`), not only HS-mode SRET and M-mode SRET to U/VS/VU. Equivalent: add M→HS to the clear list, or drop the destination qualifier for `sretInM`.

Related, not this bug:

- CSR-001: TrapHandle uses raw `mstatus.SDT` with DTE=0. Track separately; v2 PR #6256 is `sstatus.SDT` visibility, not this MuxCase.
- CSR-007: DTE=0 leftover SDT still fires `writeSDT` and clears SIE. Issue #6381. Do not fold it into this issue.

Please do not close this as already fixed by #6256 or #6381. Those are different assignments. kunminghu-v3 still has the MuxCase hole.

DefaultConfig, HasHExtension=true (HS destination). No extra inject. Bare `satp`. DTE is on so SDT is visible; the RTL hole is independent of DTE (DTE=0 would only hide the leftover bit in `sstatus`).

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
