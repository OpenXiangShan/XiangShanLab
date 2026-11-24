### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A misaligned `lw` at canonical Sv48 VA `0x00007ffffffffffd` has high split `0x0000800000000000`. That high VA is not canonical (bits 63–48 must equal bit 47). XiangShan still translates it: `unalignTail` is in `needTlbTransSources` and always queries the DTLB (`noQuery := false`), but `checkfullva` is raised only for `firstIssue` (`firstIssueSources = Seq(scalarIssue)`). The TLB canonicality precheck (`pf48`) runs only when `req.bits.checkfullva` is set. The high split therefore walks the page table, hits the alias mapping of the same VPN on the negative half, and the load commits.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
lsu05-split-load-fullva start load-alias
Difftest enabled.
commit pc 0000000080000260 inst 00052583 wen 1 dst 11 data ffffffff88000000
NEMU: mcause=0xd mepc=0x80000260 mtval=0x0000800000000000
a1 different at pc = 0x0080000346, right = 0x0000000020802401, wrong = 0xffffffff88000000
mcause different at pc = 0x0080000346, right = 0x000000000000000d, wrong = 0x0000000000000000
ABORT at pc = 0x80000346
instrCnt = 13,097  cycleCnt = 35,433
```

`00052583` is `lw a1, 0(a0)` with `a0=0x00007ffffffffffd`. DUT writes `a1=0xffffffff88000000` (high byte `0x88` is the first byte of alias page PA `0x82200000`, programmed as `0x55667788` LE). NEMU does not write the dest (difftest right=`0x0000000020802401` is leftover). DUT `mcause=0`; NEMU load page-fault `mcause=13`, `mtval=0x0000800000000000`.

`--diff` ABORT is the observation. This pin's NEMU takes the architectural path (page-fault). Oracle is Sv48 + NEMU, not an AM `HIT GOOD TRAP`.

This is not LSU-01 (HLVX final PMA/PMP execute permission) and not LSU-02 (HLV/HLVX/HSV PMP privilege vs `hstatus.SPVP`).

No matching issue or PR found on kunminghu-v3.

### Expected behavior

For S-mode Sv48 `lw` at `0x00007ffffffffffd` whose high split is `0x0000800000000000`:

1. Every translated VA must be canonical: bits 63–48 all equal to bit 47.
2. `0x0000800000000000` has bit 47 = 1 and bits 63–48 = 0, so it must not be translated.
3. The instruction must trap. Spec requires a page-fault exception (load page-fault, mcause=13, mtval the non-canonical VA). A load address-misaligned fault (mcause=4) would also prevent the alias data from committing; committing the load is not allowed.
4. Destination `a1` must not become `0xffffffff88000000`.
5. Alias page PA `0x82200000` must stay `0x55667788`.

ISA (riscv-isa-manual supervisor.adoc, Sv48): instruction-fetch addresses and load/store effective addresses, which are 64 bits, must have bits 63–48 all equal to bit 47, or else a page-fault exception will occur.

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
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `firstIssueSources = Seq(scalarIssue)`; `tlbCheckFullva := firstIssue`; `unalignTail` is still in `needTlbTransSources`; `io.tlbReq.bits.checkfullva := tlbCheckFullva`; `unalignTail.noQuery.get := false.B`.
  - NEMU commit id (if difftest failed with NEMU): `e884041bb6ab8be8c4123c7bc566d3bd32d7f7a6`
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i lsu05-split-load-fullva-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

Minimal AM workload: Sv48, map last positive page `0x00007ffffffff000` to PA `0x82100000` (`0x11223344`) and map the non-canonical high-split VPN of `0x0000800000000000` (same VPN bits as canonical negative-half `0xffff800000000000`) to alias PA `0x82200000` (`0x55667788`). In S-mode execute `lw a1, 0(a0)` with `a0=0x00007ffffffffffd`. DUT should raise load page-fault (mcause=13) on the high split. It instead commits the `lw` with `a1=0xffffffff88000000` and takes no trap.

Workload (source + bin) is attached: lsu05-split-load-fullva.zip
  - main.c
  - Makefile
  - build/lsu05-split-load-fullva-riscv64-xs.bin
    sha256 f777f798eae1202ae7e0a1b6e7e36980c18a1fa58e8e5627c1ff815054364492

Default `TEST_VARIANT` is `VARIANT_LOAD_ALIAS` (the failing path).

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has `tlbCheckFullva := firstIssue` with `unalignTail` in `needTlbTransSources` but not in `firstIssueSources`).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C lsu05-split-load-fullva ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i lsu05-split-load-fullva/build/lsu05-split-load-fullva-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
lsu05-split-load-fullva start load-alias
Difftest enabled.
commit pc 0000000080000260 inst 00052583 wen 1 dst 11 data ffffffff88000000
NEMU: mcause=0xd mepc=0x80000260 mtval=0x0000800000000000
a1 different at pc = 0x0080000346, right = 0x0000000020802401, wrong = 0xffffffff88000000
mcause different at pc = 0x0080000346, right = 0x000000000000000d, wrong = 0x0000000000000000
ABORT at pc = 0x80000346
instrCnt = 13,097  cycleCnt = 35,433
```

`--diff` aborting is the observation. NEMU raises load page-fault; DUT commits the load.

[lsu05-split-load-fullva.zip](https://github.com/user-attachments/files/32496345/lsu05-split-load-fullva.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d):

```
NewLoadUnit.scala:
  needTlbTransSources = Seq(unalignTail, replayHiPrio, replayLoPrio, scalarIssue)
  firstIssueSources   = Seq(scalarIssue)
  tlbCheckFullva      := firstIssue
  io.tlbReq.bits.checkfullva := tlbCheckFullva
  unalignTail.vaddr  := ((vaddr >> DCacheVWordOffset) + 1.U) << DCacheVWordOffset
  unalignTail.fullva := ((in.fullva >> DCacheVWordOffset) + 1.U) << DCacheVWordOffset
  unalignTail.noQuery.get := false.B   // always query TLB

TLB.scala:
  pf48 = SignExt(EffectiveVa(i)(47, 0), XLEN) =/= EffectiveVa(i)
  when (req(i).valid && req(i).bits.checkfullva) {
    when (Sv48Enable) { prepf(i) := pf48 }
  }
```

`unalignTail` is translated but never a `firstIssue`, so `checkfullva` is false and `pf48` is skipped. The high split `0x0000800000000000` is walked as if it were a 48-bit VPN (root index 256), which this AM maps to alias PA `0x82200000`.

A fix must run the Sv48 canonicality check on every translated VA, including `unalignTail` (and replay if the replayed VA can differ). Gating `checkfullva` on first issue of the original `scalarIssue` is not enough for a split whose high VA is computed later.

Related, not this bug: HLVX final PMA/PMP execute permission (LSU-01, issue #6616 / PR #6016) and HLV/HLVX/HSV PMP privilege vs `hstatus.SPVP` (LSU-02, PR #6164). Do not fold them into this issue.

DefaultConfig. Sv48 (`satp.MODE=9`). No extra inject. HasHExtension is not required for this path.

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.

[bug-report.tar.gz](https://github.com/user-attachments/files/32496365/bug-report.tar.gz)
