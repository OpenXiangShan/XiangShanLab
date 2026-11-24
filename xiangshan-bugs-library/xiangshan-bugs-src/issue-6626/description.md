### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

RISC-V TOR lock of `addr_i` is via **next** `cfg_{i+1}` being locked TOR: writes to `pmpaddr_i` / the matching PMA address register are ignored. XiangShan hart CSR PMA already uses that neighbour (`PMPCfgLField.addrLocked(pmaEntry(i).cfg, pmaEntry(i+1).cfg)`). Memory-mapped PMA (MMPMA / TLPMA at `0x38021000`) uses the **previous** cfg instead (`next_cfg = if (i==0) 0 else cfg(i-1)`). Programming MMPMA cfg1 as locked TOR therefore does not freeze addr0.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
csr011-mmpma-tor-neighbor start
cfg0_after=0x8900 old=0x0 wrote=0x12345 read=0x12000
FAIL csr011 TOR next-lock did not freeze MMPMA addr0
HIT GOOD TRAP at pc = 0x800001e2
instrCnt = 5,083  cycleCnt = 30,387
```

`cfg0_after=0x8900` is entry 1 = L|TOR|R (`0x89` in bits 15:8). Write of `0x12345` to addr0 is grain-masked to `0x12000` and **sticks**. Spec requires the write to be ignored (`read == old`).

`HIT GOOD TRAP` is not a pass. This pin's NEMU matches the DUT. Oracle is the PMP TOR-lock rule applied to the advertised PMP-shaped MMPMA, not DUT==REF.

This is SoC MMPMA, not a hart `pmpaddr` CSR, and not CSR-001/006/007 (Ssdbltrp). No matching issue/PR found on kunminghu-v3.

### Expected behavior

For MMPMA (`0x38021000`) advertised as a PMP-shaped config/addr pair:

1. Programming cfg1 as locked TOR (`A=TOR`, `L=1`) must freeze addr0. Writes to addr0 must be ignored (`machine.adoc` `pmp_l_bit_write_protection`: locked TOR `cfg_{i+1}` ignores writes to `addr_i`).
2. After `*addr0 = old ^ 0x12345`, a read of addr0 must equal the old value, not a grain-masked new value.
3. Hart CSR PMA already implements the next-neighbour rule. MMPMA must match that advertised interface.

Grain masking of a **successful** write (`0x12345` → `0x12000`) still counts as a change. The defect is that the write was accepted at all.

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
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): MMPMA `next_cfg = if (i == 0) 0.U.asTypeOf(new PMPConfig()) else cfg(i-1)` is still present. Hart `PMAEntryModule` still uses `pmaEntry(i + 1)`.
  - NEMU commit id (if difftest failed with NEMU): n/a, no mismatch (DUT and this NEMU both accept the addr0 write)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i csr011-mmpma-tor-neighbor-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

Minimal AM workload: MMIO-write MMPMA cfg0 bits 15:8 to locked TOR+R, then write addr0 to `old ^ 0x12345` and read it back. DUT should keep the old addr0. It instead stores a grain-masked new value.

Workload (source + bin) is attached: csr011-mmpma-tor-neighbor.zip
  - main.c
  - Makefile
  - build/csr011-mmpma-tor-neighbor-riscv64-xs.bin
    sha256 8a0730d4d7c9a38d16341b825ba16cfd8f15ba776d4936a6f1ffec835dbd6966

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has MMPMA `next_cfg = cfg(i-1)`).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C csr011-mmpma-tor-neighbor ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i csr011-mmpma-tor-neighbor/build/csr011-mmpma-tor-neighbor-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
csr011-mmpma-tor-neighbor start
cfg0_after=0x8900 old=0x0 wrote=0x12345 read=0x12000
FAIL csr011 TOR next-lock did not freeze MMPMA addr0
HIT GOOD TRAP at pc = 0x800001e2
instrCnt = 5,083  cycleCnt = 30,387
```

`--diff` does not abort: DUT and this NEMU both accept the write. That is not a pass. The architectural check is the AM self-check (`read != old` → FAIL).

[csr011-mmpma-tor-neighbor.zip](https://github.com/user-attachments/files/32514493/csr011-mmpma-tor-neighbor.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d), `PMA.scala` MMPMA addr map:

```
val next_cfg = if (i == 0) 0.U.asTypeOf(new PMPConfig()) else cfg(i-1)
...
when (valid) { addr(i) := write_addr(next_cfg, mask(i))(...) }
```

`i=0` dummy `next_cfg=0` is unlocked, so addr0 is never frozen by cfg1.

Hart CSR PMA already uses the advertised next neighbour, `PMAEntryModule.scala`:

```
val addrNextLocked: Bool = PMPCfgLField.addrLocked(pmaEntry(i).cfg, pmaEntry(i + 1).cfg)
```

and `CSRPMP.scala` `addrLocked(cfg, next) = locked(cfg) || (locked(next) && tor(next))`.

A fix should pass `cfg(i+1)` (with a last-entry fallback) into MMPMA `write_addr`, matching the hart PMA / PMP rule.

Do not treat this as a hart `pmpcfg` bug. The AM never writes `pmpcfg`; it writes SoC MMPMA at `0x38021000`. Hardware indices 0/1 of this map are the first two MMPMA slots (cfg0 A stays OFF; cfg1 is the TOR lock). The AM restores cfg/addr before judging so leftover PMA does not panic later.

DefaultConfig. No extra inject.

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
