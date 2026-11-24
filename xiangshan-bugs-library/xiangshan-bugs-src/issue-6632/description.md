### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


With two-stage translation active (Sv39x4 in `hgatp`, Sv39 in `vsatp`) and the hart in VS-mode, a misaligned `sd` whose 8 bytes cross into the next page, where the VS-stage page-table access of that next page has no G-stage mapping, never commits. Nothing commits after it and no trap is taken:

```
No instruction of core 0 commits for 15000 cycles, maybe get stuck
   mode different at pc = ..., right = 0x0000000000000003, wrong = 0x0000000000000001
   mepc different at pc = ..., right = 0x0000000080002094, wrong = 0x000000008000208c
  mtval different at pc = ..., right = 0x0000000080202ffc, wrong = 0x0000000000000000
```

NEMU takes a store/AMO guest-page fault into M-mode: `mcause = 23`, `mtval = 0x80202ffc`, `mtval2 = 0x20140004`, `mtinst = 0x3000` (the pseudoinstruction for an implicit 64-bit VS-stage page-table read), `mstatus.GVA = 1`.

The instruction under test, after the setup (`x5 = 0x80202ffc`, `x6 = 0x1122334455667788`, `hgatp = 0x8000000000080014`, `vsatp = 0x800000000008001a`, `mstatus.MPV = 1`, `MPP = S`, `mret`):

```asm
__mode_1:
  la x31, __resume_2
  sd x6, 0(x5)          # hangs
```

It is not a difftest artifact. With `--no-diff -C 50000` the same image stops at the store:

| image | `--no-diff` |
|---|---|
| `f7_vs_crosspage_sd`, `20fd0d6097` | `EXCEEDING CYCLE LIMIT`, `instrCnt = 156` at `cycleCnt = 50,000` |
| `f7_ctrl_crosspage_ld`, `20fd0d6097` | `HIT GOOD TRAP`, `instrCnt = 194`, `cycleCnt = 9,631` |
| `f7_vs_crosspage_sd`, kunminghu-v2 `f86f50b834` | `HIT GOOD TRAP`, `instrCnt = 196`, `cycleCnt = 9,568` |

### Expected behavior

The store raises a store/AMO guest-page fault (or, if the implementation checks it first, the address-misaligned exception it is allowed to raise instead). It must not stop the pipeline.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv-none-elf-gcc (xPack GNU RISC-V Embedded GCC) 15.2.0 (reproducer build)
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `20fd0d6097e6be19b0fe49fed31763484af4e20f` (kunminghu-v3 HEAD)
  - NEMU: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `45638f5145aa9c7eee85cd9b08eb8962ee46b586`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig`
  - Run command: `./build/emu -i f7_vs_crosspage_sd.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000`

### To Reproduce

```
./build/emu -i f7_vs_crosspage_sd.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000   # stuck
./build/emu -i f7_vs_crosspage_sd.bin --no-diff -C 50000                                        # EXCEEDING CYCLE LIMIT, instrCnt 156
./build/emu -i f7_ctrl_crosspage_ld.bin --no-diff -C 50000                                      # HIT GOOD TRAP
```

[Archive.zip](https://github.com/user-attachments/files/32554188/Archive.zip)

### Additional context

_No response_
