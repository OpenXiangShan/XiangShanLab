### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A guest load through a G-stage leaf whose PTE has `PBMT=NC` returns zero instead of the value stored at the translated physical address. NC changes only cacheability; it must not change the address or the returned data.

`gs_pbmt_nc_min.S` sets up the failing access in M-mode:

1. Build a G-stage page table mapping GPA `0x20000000` to the physical page containing `nc_data`.
2. Set the leaf PTE flags to `U|A|D|W|R|V` and `PBMT=NC` (`PBMT=1`).
3. Set `hgatp`, `satp=0`, `vsatp=0`, and `MPRV=1 + MPP=S + MPV=1`, so the following load is performed as a guest access.
4. Store `0x1111222233334444` to `nc_data`, then execute `ld s2, 0(t4)` with `t4 = 0x20000000`.

Expected: `s2 = 0x1111222233334444`.

Actual: the DUT returns zero. With difftest enabled:

```
s2 different at pc = 0xfffe80dcd178bd42, right = 0x1111222233334444, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0xfffe80dcd178bd42
```

The same image under `--no-diff` prints the program's own failure line:

```
F 0000000000000000 0000000000000000 0000000000000000
HIT GOOD TRAP at pc = 0x800001e8
```

The control run proves that fetch, page-table setup, and the UART path execute. The architectural data is wrong only on the NC load path. NEMU returns the expected value, so this is a DUT disagreement, not a reference-model failure.

### Expected behavior

For a guest load through a G-stage leaf with `PBMT=NC`, the load must return the data at the translated physical address. The NC replay path must preserve the PADDR carried by the NC request; it must not replace it with a stale TLB response from a different access.

After the fix, the same image should print:

```
P 1111222233334444 0000000000000000 0000000000000000
HIT GOOD TRAP
```

and difftest should complete without `s2 different`.

The failure is silent: no guest exception is raised, so the guest observes a corrupted value and may make incorrect control-flow or data decisions.

### Environment

- Published Hunt V2 Verilator emu, not rebuilt for this report. emu sha256 `aa6823b15aa71b43df193ef493071340336f1fef64dcecb043772f6a5a52e880`.
- Hunt V2 NEMU sha256 `9a55ad96f49309d6b3620cd0632cce87cec62651b9e43c686cdf8f4aaf9f447e`.
- Workload toolchain: riscv64-unknown-linux-gnu-gcc (GCC) 12.0.1 20220505 (prerelease), `-march=rv64gcv -mabi=lp64d`; GNU objcopy 2.38.
- Image `case.bin` sha256 `5add227676ad6a5ad8ae2568e104a2467e3e32af791e4ffe6df5fea034032e20`.
- Source `gs_pbmt_nc_min.S` sha256 `f14f9dd0e18bb2daaea76053f136079ef360fa2a372d8673c53cb54a2fbf0496`.
- Linker script `link.ld` sha256 `d88f07d13f5ff39e307e272ed23143957f1c84fa9444ad7a2fe420565aba1384`.
- Farm evidence: run `run-20261026-161337-d595d2`, finding `bug-d7c19202ec`, key `rtl-loadunit-nc-replay-stale-paddr`.
- Hunt case: `XS-RT-033`, validation `b48b5496-68cf-47f9-892b-e5abd5daea19`, GitLab pipeline 993. Two runs with Seed=0 both reproduced.


### To Reproduce

`link.ld` places `.text.init` at `0x80000000`. `gs_pbmt_nc_min.S` is self-contained and uses the UART at `0x310b0000` for its report.

```
riscv64-unknown-linux-gnu-gcc -nostdlib -nostartfiles -static -fno-pic -fno-pie -no-pie \
  -mcmodel=medany -march=rv64gcv -mabi=lp64d -Wl,--build-id=none \
  -T link.ld gs_pbmt_nc_min.S -o case.elf
riscv64-unknown-linux-gnu-objcopy -O binary case.elf case.bin

./build/emu -b 0 -e 0 -s 0 -i case.bin --max-instr=100000 --no-diff
./build/emu -b 0 -e 0 -s 0 -i case.bin --max-instr=100000 --diff=<V2-release-nemu>
```

`case.bin` in this folder is the image that produced the log above. The difftest run aborts at `s2 different`; the no-diff run prints `F 0000000000000000 ...`.

### Additional context

Reading of `a4eeaabb4c`, not confirmed by editing the RTL:

- `mem/pipeline/LoadUnit.scala:795-797` selects `io.lsq.nc_ldin.bits.paddr` for the NC source in S0.
- `mem/pipeline/LoadUnit.scala:338-340` includes `nc_idx` in `s0_tlb_no_query`, so this NC replay does not issue a fresh TLB lookup.
- `mem/pipeline/LoadUnit.scala:944` unconditionally captures `io.tlb.resp.bits.paddr(0)` into `s1_paddr_dup_lsu`.
- `mem/pipeline/LoadUnit.scala:1014` assigns `s1_out.paddr := s1_paddr_dup_lsu`, overwriting the PADDR selected for the NC request.
- `mem/pipeline/LoadUnit.scala:1753` calls `shiftDataToHigh(s2_out.paddr, s2_out.data)`, so the NC data is aligned using the stale TLB PADDR and the guest observes zero.

The NC replay path needs to keep the PADDR from `io.lsq.nc_ldin.bits.paddr` (for example, select PADDR by source in S1 or preserve `s1_in.paddr` for NC/fast replay). An assertion that an NC request's S1 PADDR equals `io.lsq.nc_ldin.bits.paddr` would prevent the same regression.

[xs-rt-033-pbmt-nc.zip](https://github.com/user-attachments/files/34006968/xs-rt-033-pbmt-nc.zip)
