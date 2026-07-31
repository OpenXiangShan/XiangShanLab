# XiangShan Issue 6264 Fresh-Run Waveform Analysis

Issue: <https://github.com/OpenXiangShan/XiangShan/issues/6264>

## 结论

Issue 6264 在重新克隆、重新编译的 `xs-env` 中已经复现。复现程序从 issue 附件源码重新编译得到，没有使用旧环境或旧二进制作为结论依据。

根因是前端顺序取指路径对 Sv39 canonical address 的检查缺失。测试从 `0x0000003fffffffc0` 的 NOP sled 顺序落到 `0x0000004000000000`。这个地址在 Sv39 下是 non-canonical，因为 bit 38 为 1，但 bits 63:39 不是全 1。正确行为应当是 instruction page fault，`mcause=12`，`mepc=mtval=0x0000004000000000`。

实际波形显示 XiangShan 没有产生前端取指异常，而是把 `VA 0x0000004000000000` 通过 ITLB/ICache 路径翻译到 `PA 0x81200000`，取到 alias stub 第一条指令 `0x820102b7`，并最终提交。这就是 difftest mismatch 的直接原因。

## Fresh Environment

- Fresh workdir: `bug-analysis-6264/fresh-run`
- Fresh clone: `bug-analysis-6264/fresh-run/xs-env`
- `xs-env` HEAD: `33d5f6f611d15a65c6194290fa62caf0c0c27f41`
- XiangShan checkout: `7be121c71ff0534982ee0521e0b7fe8f2605a67c`
- Relevant submodules:
  - `NEMU`: `53bcb5686f8fd05248ae98546b7dc04bdca1bbb0`
  - `nexus-am`: `086a238062fdfb3281b745a537b361983d0154b5`
  - `DRAMsim3`: `1d2a9bf6da8e050d975ef2669c42faa93943f489`
- Network downloads used SOCKS5 proxy `172.38.10.247:8970` when needed.
- Old analysis artifacts outside this fresh workdir were not used for the waveform conclusion.

Build commands used in the fresh clone:

```bash
cd bug-analysis-6264/fresh-run/xs-env/XiangShan
make init
make emu EMU_TRACE=fst -j12
```

The host `sync` implementation rejected repeated `--data` options during the first `make emu` attempt. I applied a local build-compatibility patch only in the fresh clone, changing both `sync -d $(BUILD_DIR) -d $(VERILATOR_BUILD_DIR)` occurrences in `XiangShan/difftest/verilator.mk` to `sync -d $(BUILD_DIR) $(VERILATOR_BUILD_DIR)`, then reran `make emu EMU_TRACE=fst -j12`. This was not a functional RTL fix.

Fresh build artifact:

- `XiangShan/build/verilator-compile/emu`: 277 MB x86-64 executable
- `XiangShan/build/emu`: symlink to the executable above
- `XiangShan/ready-to-run/riscv64-nemu-interpreter-so`: NEMU reference shared object

## Reproducer

Issue attachment:

- `bug-analysis-6264/fresh-run/repro/xs-fetch-fallthrough-noncanonical.zip`
- SHA256: `1dc071c3681fae28f9f7dde7767f2b440dfdd1b0a5b730d8a487ea2e0e296a84`

The source was copied into a fresh `nexus-am` app:

- Source: `bug-analysis-6264/fresh-run/repro/extracted/source/main.c`
- Fresh app: `bug-analysis-6264/fresh-run/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough`

App build command:

```bash
cd bug-analysis-6264/fresh-run/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough
env \
  XS_PROJECT_ROOT=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env \
  NEMU_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/NEMU \
  AM_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/nexus-am \
  NOOP_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/XiangShan \
  DRAMSIM3_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/DRAMsim3 \
  make ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1
```

Fresh rebuilt binary:

- `bug-analysis-6264/fresh-run/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough/build/bug-canonical-fetch-fallthrough-riscv64-xs.bin`
- Size: 6980 bytes
- SHA256: `2c0b63a33f1b52a5195001e1878f11795917247724cd5410f96c6de126b5bb71`

The test program deliberately builds this page-table shape:

- `root[255] -> L1TAB`, `L1TAB[0x1ff]` maps the boundary NOP page to `PA 0x811ff000`.
- `root[256] -> L1TAB2`, `L1TAB2[0]` maps the non-canonical fall-through VPN to alias `PA 0x81200000`.
- It enters S-mode at `f7_s_entry`, computes `t0 = 0x3fffffffc0`, and executes `jr t0`.
- It writes alias code at `PA 0x81200000`; the first word is `0x820102b7`.

Source anchors:

- `main.c:75-80`: S-mode entry jumps to `0x3fffffffc0`.
- `main.c:130-140`: page tables create boundary page and `root[256]` alias mapping.
- `main.c:163-173`: boundary NOP sled and alias stub are installed.
- `main.c:191-198`: correct result is IPF; `flag == 0xfa` means bug reproduced.

## Fresh Run

Run command:

```bash
cd bug-analysis-6264/fresh-run/xs-env/XiangShan
env \
  XS_PROJECT_ROOT=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env \
  NEMU_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/NEMU \
  AM_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/nexus-am \
  NOOP_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/XiangShan \
  DRAMSIM3_HOME=/home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/DRAMsim3 \
  ./build/emu \
    --diff=ready-to-run/riscv64-nemu-interpreter-so \
    --dump-wave-full \
    --wave-path=../run-6264-full.fst \
    --max-instr=50000 \
    -i /home/yanyusong/XiangShanLab/tools/xiangshan-bugs-analyzer/bug-analysis-6264/fresh-run/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough/build/bug-canonical-fetch-fallthrough-riscv64-xs.bin \
    > ../run-6264.log 2>&1
```

Expected reproduction result: emu exits nonzero due to difftest mismatch. That is the reproduced failure, not a run failure.

Artifacts:

- Log: `bug-analysis-6264/fresh-run/xs-env/run-6264.log`
  - SHA256: `30eb93726592e0d2fc121dbd39f9368aae931820f069080607ca57615d1c8f10`
- Waveform: `bug-analysis-6264/fresh-run/xs-env/run-6264-full.fst`
  - Size: 200 MB
  - SHA256: `05878bf316c995d5259399d520abfe1bdc9f88db0c37d1ee0e29de299f9aae8c`

Fresh log evidence:

```text
=== bug: fetch fall-through across canonical boundary ===
[test] entering S-mode, jr 0x3FFFFFFFC0 (NOP sled), fall-through ...
[25] commit pc 0000003fffffffc0 inst 00000013 ...
[30] commit pc 0000003ffffffffc inst 00000013 ...
[31] commit pc 0000004000000000 inst 820102b7 wen 1 dst 05 data ffffffff82010000 idx 007 <--
```

Difftest reference raised the expected instruction page fault:

```text
REF: mcause=0x000000000000000c
REF: mepc=0x0000004000000000
REF: mtval=0x0000004000000000
```

But DUT did not trap before executing the alias code:

```text
mepc different at pc = 0x0080000fbe, right = 0x0000004000000000, wrong = 0x00000000800001cc
mtval different at pc = 0x0080000fbe, right = 0x0000004000000000, wrong = 0x0000000000000000
mcause different at pc = 0x0080000fbe, right = 0x000000000000000c, wrong = 0x0000000000000000
Core-0 instrCnt = 6836, cycleCnt = 23537
```

## Waveform Timeline

Waveform format is FST. I used `fst_probe` built from the Verilator/GTKWave FST API and `fstminer` for value search. FST time `#47148` to `#47150` increments `rob.timer` from `0x5be2` to `0x5be3`, so in this window 2 FST time units correspond to one RTL cycle. The table reports both absolute FST time and the stable `rob.timer` cycle where available.

| FST time | Cycle source | Key wave evidence |
| --- | --- | --- |
| `#46954` | request in flight | `inner_icache.io_itlb_0_req_bits_vaddr = 0x4000000000`. This is the sequential fall-through fetch address. |
| `#47080` | `rob.timer=0x5bc0` = 23488 | ITLB/ICache response for that request has `paddr_0 = 0x81200000`, `miss = 0`, `pf_instr = 0`, `gpf_instr = 0`, `af_instr = 0`. |
| `#47086` | next ICache access | `inner_icache.missUnit.acquireArb...acquire_address = 0x81200000`; the translated physical line is fetched. |
| `#47148` | `rob.timer=0x5be2` = 23522 | `inner_icache.io_fetch_resp_bits_vaddr_0 = 0x4000000000`, `paddr_0 = 0x81200000`, `exception_0 = 0`, `exception_1 = 0`, `backendException = 0`. Response data contains the alias stub beginning with `0x820102b7`. |
| `#47150` | `rob.timer=0x5be3` = 23523 | IFU accepts the line: `inner_ifu.f3_pc_0 = 0x4000000000`, `f3_paddrs_0 = 0x81200000`, `f3_instr_0 = 0x820102b7`, `f3_exception_0 = 0`, `f3_exception_vec_0 = 0`, `f3_backendException = 0`, `f3_fire = 1`. |
| `#47172` | `rob.timer=0x5bee` = 23534 | ROB exposes a valid difftest commit: `difftest_commit_valid = 1`, `difftest_commit_pc = 0x4000000000`, `difftest_commit_instr = 0x820102b7`, `difftest_commit_rfwen = 1`, `wdest = 5`, `wpdest = 0x37`. |
| `#47180` | `endpoint.trap.cycleCnt=0x5bf1` = 23537 | Endpoint commit still sees `valid = 1`, `pc = 0x4000000000`, `instr = 0x820102b7`, `rfwen = 1`, while `endpoint.trap.io_bits_hasTrap = 0`. |

This is the complete failure chain in the wave: non-canonical VA enters ITLB, returns a normal translation to the crafted alias PA, carries no frontend exception, fires through IFU, and commits.

Useful wave queries:

```bash
cd bug-analysis-6264/fresh-run

./fst_probe xs-env/run-6264-full.fst sample 47080 inner_icache.io_itlb_0_resp_bits_paddr_0
./fst_probe xs-env/run-6264-full.fst sample 47080 inner_icache.io_itlb_0_resp_bits_excp
./fst_probe xs-env/run-6264-full.fst sample 47148 inner_icache io_fetch_resp_bits
./fst_probe xs-env/run-6264-full.fst sample 47150 inner_ifu f3_
./fst_probe xs-env/run-6264-full.fst sample 47172 inner_ctrlBlock.rob.difftest_commit_pc
./fst_probe xs-env/run-6264-full.fst sample 47180 endpoint.commit.io_bits_pc

cd bug-analysis-6264/fresh-run/xs-env
fstminer --hex 81200000 run-6264-full.fst | rg 'inner_itlb|inner_icache|inner_ifu'
fstminer --hex 4000000000 run-6264-full.fst | rg 'inner_icache|inner_ifu|difftest_commit|endpoint.commit'
```

`gtkwave` GUI could not be opened in this environment because there is no display, but the FST was read directly through FST APIs and value-mined from the actual waveform file.

## Source Analysis

The relevant frontend path is:

```text
FTQ/NewFtq -> ICache/IPrefetch -> ITLB/TLB -> ICacheMainPipe -> IFU -> IBuffer/backend -> ROB commit
```

Source anchors in the fresh XiangShan checkout:

- `src/main/scala/xiangshan/frontend/Frontend.scala:164-167`
  - Instantiates ITLB and connects the first requestor ports to `icache.io.itlb`; the last port goes to `ifu.io.iTLBInter`.
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:561-565`
  - `TlbReq` contains `vaddr`, `fullva`, `checkfullva`, and `cmd`.
- `src/main/scala/xiangshan/cache/mmu/TLB.scala:200-205`
  - Sv39/Sv48 canonical checks are computed from `EffectiveVa`, but are only applied inside `when (req(i).valid && req(i).bits.checkfullva)`.
- `src/main/scala/xiangshan/cache/mmu/TLB.scala:488-501`
  - The TLB response exception bits include `pf.instr`, `gpf.instr`, and `af.instr`.
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:173-180`
  - The ICache/IPrefetch ITLB request does `toITLB(i).bits := DontCare`, then explicitly drives `size`, `vaddr`, `debug.pc`, `cmd`, and `no_translate`. It does not explicitly drive `fullva` or `checkfullva` for the sequential fetch request.
- `src/main/scala/xiangshan/cache/mmu/TLB.scala:369-371`
  - The local comment says frontend handles cross-page instruction fetch itself and "the fullva of iTLB is not used and always zero".
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala:33-38`
  - Fetch request addresses are carried as `UInt(VAddrBits.W)` for `startAddr`, `nextlineStart`, and `nextStartAddr`.
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala:68-75`
  - FTQ-to-ICache address information also carries `startAddr` and `nextlineStart` as `UInt(VAddrBits.W)`.
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala:240-255`
  - Fetch-to-IBuffer PCs are also `UInt(VAddrBits.W)`.
- `src/main/scala/xiangshan/frontend/FrontendBundle.scala:142-150`
  - Frontend exception type is derived from the TLB response `pf.instr/gpf.instr/af.instr`.
- `src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala:32-40`
  - ICache response carries `vaddr`, `paddr`, `exception`, and `backendException`.
- `src/main/scala/xiangshan/frontend/IFU.scala:355-389`
  - IFU waits for a matching ICache response, then consumes `fromICache.bits.exception`, `backendException`, `paddr`, and related metadata.
- `src/main/scala/xiangshan/Bundle.scala:123-140`
  - Backend redirect fault flags cover `backendIGPF`, `backendIPF`, and `backendIAF`, but this test is not a redirect-target fault. The failing address is generated by straight-line fall-through.

The source and wave agree:

1. The TLB can perform canonical checks, but the check is gated by `checkfullva`.
2. The sequential ICache/IPrefetch ITLB request path does not explicitly provide `fullva/checkfullva`.
3. The wave shows the exact non-canonical address request returns `pf_instr=0/gpf_instr=0/af_instr=0`.
4. ICache and IFU propagate the line with `exception=0` and `backendException=0`.
5. ROB and endpoint commit the alias instruction.

## Root Cause

The failing path is the straight-line fetch fall-through path, not the initial `jr` redirect target. The redirect to `0x3fffffffc0` is canonical and legal. The bug appears when the sequential next PC crosses from `0x3ffffffffc` to `0x4000000000`.

In this commit, frontend instruction fetch metadata is carried primarily as `VAddrBits` addresses. The TLB has a full-VA canonical check mechanism, but the sequential ICache/IPrefetch ITLB request path does not supply the full XLEN VA and does not assert `checkfullva`. As a result, the ITLB page walk uses the VPN bits and accepts the crafted `root[256]` mapping instead of raising instruction page fault for the non-canonical VA.

That explains the observed architecture mismatch:

- Reference model: traps to M-mode with instruction page fault, `mcause=12`, `mepc=mtval=0x4000000000`.
- XiangShan DUT: remains in S-mode long enough to execute alias code at `PA 0x81200000`, writes through `lui t0, 0x82010`, and only later diverges in difftest.

## Fix Direction

No fix was applied for this teaching run. A fix should preserve enough full-VA/canonical metadata on all instruction-fetch paths and make the sequential fetch ITLB request participate in canonical checking:

- Drive `fullva` with the full instruction VA and assert `checkfullva` for instruction fetch translation when address translation is enabled.
- Ensure fall-through, next-line, cross-cacheline, cross-page, and refetch paths preserve the high/sign information needed by Sv39/Sv48 canonical checks.
- Keep redirect-target backend fault handling and sequential-fetch canonical checking separate; one does not cover the other.
- Add regression tests for Sv39 and Sv48 positive and negative canonical boundaries, including straight-line fall-through, direct branch/jump, indirect jump, and cross-line fetch cases.

## Status

Reproduction is complete and waveform-backed. The failure was observed in the fresh FST at the front-end translation, IFU accept, ROB difftest commit, and endpoint commit points.
