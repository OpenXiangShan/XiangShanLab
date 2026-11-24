### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A **legal** segmented vector store commits the **wrong data** to memory — one
field is stored as `0` instead of its actual source value — with **no assertion
and no exception**. This is silent memory corruption caught only by difftest:

```
==============  Store Commit Event (Core 0)  ==============
Mismatch for store commits
  REF commits addr 0x0000000080010070, data 0x00000000dac10000, mask 0x0c, pc 0x000000008000031e   <- NEMU (correct)
  DUT commits addr 0x0000000080010070, data 0x0000000000000000, mask 0x0c, pc 0x000000008000031e, robidx 0xbb  <- XS (WRONG: zero)
```

The faulting instruction and its active `vtype` (from the disassembly):
```
80000312: vsetvli t0, zero, e16, m2, tu, mu     ; SEW=16, LMUL=2
80000316: auipc   t1, 0x10
8000031a: addi    t1, t1, -710                   ; t1 = 0x80010050
8000031e: vsseg2e16.v v8, (t1)                    ; <- commits data=0 for a field
```
This is **fully legal**: for `vsseg2e16.v` under `e16/m2`,
`EMUL = EEW/SEW * LMUL = 16/16 * 2 = 2`, and `nf*EMUL = 2*2 = 4 <= 8`. NEMU
executes the workload in lockstep for all 194 committed instructions; the
workload has no self-modifying code.

## Root cause (waveform-traced): the vector store-data unit (`Vstd`) emits 0

We dumped a VCD at the failing cycle (on a trace build at the newest tip). The
vector store-data unit `Vstd` produces the store data for each field. For this
store's failing field it outputs `out_ex_0_bits_data_vstd_data = 0x0`, **even
though the correct vector operand `0x...dac1...` is present on another `Vstd`
output the same cycle**. So the store-data path selects the wrong / stale source
for one field and writes 0.

Importantly, this store's ROB uop count is **correct** — `robEntries_187_uopNum`
reaches 0 normally and never underflows. So this is a **data-path** defect, not a
uop-count defect.

### Expected behavior

A legal segmented store should commit each field with its correct source data,
matching the reference model. No field should be silently stored as 0.

### Environment

- Hardware
  - CPU: x86_64 server
  - Memory (GB): 512
  - Storage (GB): (n/a)
- Software
  - Operating system: Linux
  - gcc version: gcc (conda-forge gcc 16.1.0-1) 16.1.0   (emu build compiler)
  - clang version: (not used)
  - java version: openjdk 20.0.2-internal (mill)
  - mill version: 0.12.17 (from .mill-version)
  - firtool version: 1.144.0
  - verilator version: Verilator 5.028 2024-08-21
- Repo
  - XiangShan commit id: `0cdabfc7c` (kunminghu-v3; **reproduced on this, the
    latest default-branch commit**, with a STOCK / non-instrumented emu and an
    unmodified difftest submodule)
  - NEMU commit id (if difftest failed with NEMU): shipped `ready-to-run` `4cf9983`
  - difftest submodule: `45638f51`
- Build & Run
  - Build command: `make emu` for `SimTop`, rv64gcv, VLEN=128
  - Run command: `./build/emu -i id_3_420.elf -C 20000 --diff <nemu-so>`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v3 `0cdabfc7c` (rv64gcv,
   VLEN=128) — a stock `make emu` build reproduces it (no instrumentation).
2. Run the attached workload:
   ```
   ./build/emu -i id_3_420.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe (around `instrCnt = 194`): a store-commit mismatch — the DUT commits
   `data = 0x0` at `0x80010070` (pc `0x8000031e`, `vsseg2e16.v`) where NEMU
   commits `0xdac10000`. Deterministic.

Attachments (tarball `xs_v3_vstd_wrong_store_data_attachments.tar.gz`):
- `id_3_420.elf` — reproducer binary
- `id_3_420.disasm` — disassembly
- `stock_0cdabfc7c_replay.log` — the stock-emu run at `0cdabfc7c` showing the mismatch

The workload is a ~200-instruction user-mode test found by differential fuzzing
against NEMU; not yet hand-minimized. The trigger is a legal `vsseg2e16.v` under
`vsetvli e16,m2` at pc `0x8000031e`.

[xs_v3_vstd_wrong_store_data_attachments.tar.gz](https://github.com/user-attachments/files/31826508/xs_v3_vstd_wrong_store_data_attachments.tar.gz)

### Additional context

_No response_
