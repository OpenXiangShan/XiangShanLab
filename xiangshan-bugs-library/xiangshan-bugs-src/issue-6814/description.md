### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A page walk that is in flight when `sfence.vma` (or the Svinval sequence) executes can still refill the load DTLB after the DTLB has been flushed. If that walk read the page tables before the fence, a load after the fence uses the old translation.

The attached workload runs in VS mode with the G-stage on and repeats one trial six times: the VS-stage leaf of VA 0x40002000 is changed from page A to page B, `prefetch.r 0x40002000` starts a walk, then `sfence.vma 0x40002000` and `ld a1, 8(0x40002000)` follow. In the third trial the load returns page A's data. DiffTest reports `a1 different at pc ..., right = 0xa500000c00000008, wrong = 0xa500000100000008`.

### Expected behavior


The load after the fence returns page B's data (`0xa500000c00000008`), as NEMU and Spike do.

### Environment


- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - clang version: not used
  - java version: openjdk version "17.0.20.1" 2026-08-18
  - mill version: 0.12.17
- Repo
  - XiangShan commit id: `a0c2a26cbe3c862175174bad03a1a23c406315ed`
  - NEMU commit id (if difftest failed with NEMU): `513786c4c597dd628c6126144937372d6f7fc92e`
  - SPIKE commit id (if difftest failed with SPIKE): not used
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig NO_ZSTD_COMPRESSION=1 OPT_FAST=-O3 -j20`
  - Run command (if applicable): `./build/emu -i late_refill_sfence.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.

### To Reproduce


Workload in `late_refill.zip`:
- `late_refill_sfence.elf`: fails in the third trial.
- `late_refill_svinval.elf`: the same trials with `sfence.w.inval; sinval.vma; sfence.inval.ir`, fails the same way.
- `late_refill_sfence_twice.elf`: `late_refill_sfence` with a second `sfence.vma` right after the first, passes.

One trial:
1. Point the VS-stage leaf of VA 0x40002000 at GPA page A, `sfence.vma`.
2. Touch another page of the same VS-stage L0 table, so that line is in the L2TLB page cache.
3. In M mode, write A's G-stage leaf back with its own value and run `hfence.gvma` for A only (the mapping does not change; the next walk of A just has to read the G-stage from memory).
4. Point the VS-stage leaf of VA 0x40002000 at page B.
5. `prefetch.r 0x40002000`, then 30 dependent `addi`.
6. `sfence.vma 0x40002000, x0` in VS mode.
7. `ld a1, 8(0x40002000)`.

Run: `./build/emu -i late_refill_sfence.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so`

[late_refill.zip](https://github.com/user-attachments/files/34232802/late_refill.zip)

### Additional context

_No response_
