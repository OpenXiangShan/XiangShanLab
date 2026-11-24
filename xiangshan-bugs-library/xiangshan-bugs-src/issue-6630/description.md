### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A 32-bit instruction is fetched from physical address `0x1ffffffe`. The instruction crosses both an 8-byte uncache/AXI beat boundary and a 4-KiB page boundary.

The first halfword is fetched successfully from the QSPI flash MMIO region. IFU then requests the second half from `0x20000000`, where the PMA/X check rejects the access. The resulting exception is propagated through IFU, IBuffer, ROB, CtrlBlock, and FTQ redirect.

The backend redirect contains more than one instruction exception bit at the same time, violating the `ExceptionType` at-most-one-hot invariant.

### Expected behavior

The second-page access should produce one well-defined instruction exception. The backend redirect must contain at most one of `backendIPF`, `backendIGPF`, and `backendIAF`.

For this reproducer, the expected recovery metadata is:

```text
mepc   = 0x0000000001fffffe
mtval  = 0x0000000020000000
mcause = one instruction fetch exception
```

### Environment


- Hardware
  - CPU: Intel(R) Xeon(R) 6960P, 288 logical CPUs 
  - Memory (GB): 2263 total
  - Storage (GB): 1758 total on `/dev/sda2` (1565 GB used at verification time)
- Software
  - Operating system: Ubuntu 26.04 LTS
  - gcc version: `gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`
  - clang version: `Ubuntu clang version 19.1.1 (1ubuntu1~24.04.2)`
  - java version: `openjdk version "21.0.11" 2026-04-21`
  - mill version: `Mill Build Tool version 0.12.17`
- Repo
  - XiangShan commit id: `e4566c29e251c60159c6796db60a2569dd7700f2`
  - NEMU commit id (if difftest failed with NEMU): `N/A` (the supplied `/work/nemu-src` runtime directory is not a git checkout)
  - SPIKE commit id (if difftest failed with SPIKE): `N/A`
- Build & Run
  - Build command: `mill -i xiangshan.test.runMain top.XiangShanSim --target-dir build/rtl --config MinimalConfig --issue E.b --num-cores 1 --target systemverilog --split-verilog --dump-fir --enable-difftest --firtool-opt "-O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none" --firtool-opt "--default-layer-specialization=enable" && make -C difftest emu NUM_CORES=1 RTL_SUFFIX=sv OBJCACHE= -j8`
  - Run command (if applicable): `cd c2_poc/REPRO && ./run.sh /work/build/verilator-compile/emu`

The run uses `NEMU_HOME=/work/nemu-src`, `--no-diff`, and the required working-tree change `top/Top.scala: OpenNCB.writeNoError = false`.

The enabling change only allows the AXI read error to reach the core; the defect is in the stock IFU/backend exception-delivery path.


### To Reproduce

The reproducer requires the MMIO bridge setting below so that the AXI read error reaches the core:

```diff
- writeNoError = true
+ writeNoError = false
```

This is only an enabling condition; the defect is in the stock IFU/backend exception-delivery path.

The complete reproducer is in `c2_poc/REPRO/`:

- `p9.S`, `p9.elf`
- `flash.bin`
- `Top_wne.patch`
- `run.sh`
- `README.md`

Run:

```bash
cd c2_poc/REPRO
./run.sh /work/build/verilator-compile/emu
```

Expected key output:

```text
Assertion failed at /work/build/rtl/Ftq.sv:4896.
Core-0 instrCnt = 9, cycleCnt = 2137
ExceptionType receives input that is not one-hot
```

### Additional context

[xiangshan-c2-poc-repro.tar.gz](https://github.com/user-attachments/files/32549902/xiangshan-c2-poc-repro.tar.gz)
