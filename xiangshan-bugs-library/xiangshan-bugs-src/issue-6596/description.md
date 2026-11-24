### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A unit-stride **segment load** whose element access is refused aborts the core on the ROB uop-count assertion instead of taking the load access fault:

```
Assertion failed: robEntries 16 uopNum is overflow!
Core 0: ABORT at pc = 0x80000032, instrCnt = 15
```

Fifteen instructions are enough. The base address has no memory behind it, so the very first element is refused:

```asm
  li      t3, 2
  vsetvli x0, t3, e32, m1, ta, ma   # SEW = 32, LMUL = 1, vl = 2
  li      t1, 0x90000000            # no memory behind this address
  .word   0x82036407                # vlseg5e32.v v8, (t1)
```

The same abort appears with `vlseg3e8.v`, `vlseg4e8.v`, `vlseg7e8ff.v`, `vlsseg6e32.v`, `vloxseg5ei64.v`, `vluxseg5ei32.v` and `vluxseg5ei8.v`, in M, S and U mode, and whether the refusal comes from an unbacked address or from a PMP-denied region. With `nf = 1` or `nf = 2` the same program takes the fault normally, so the trigger is a **refused** segment access with three or more fields.


### Expected behavior

The segment load should take a load access fault (`mcause = 5`) with `vstart` at the faulting element, as the reference model does, and the ROB's uop count for the entry should match the number of uops the aborted instruction actually wrote back.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv-none-elf-gcc (xPack) 15.2.0 (reproducer build)
  - java version: openjdk 17.0.20.1
  - mill version: 0.12.17
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `c8d7b3a5c1abf3f42c954e61abba20dd27e02a21` (kunminghu-v3, **the latest default-branch commit**; also reproduced on `37ce1b50b5`)
  - NEMU commit id: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `45638f5145aa9c7eee85cd9b08eb8962ee46b586`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig`
  - Run command: `./build/emu -i f3_segment_load_fault_rob.bin --diff <nemu-so> -I 60`

### To Reproduce



[f3_segment_load_fault_rob.elf.zip](https://github.com/user-attachments/files/32386645/f3_segment_load_fault_rob.elf.zip)

### Additional context

#6475 reports the same assertion (`robEntries ... uopNum is overflow!`) for a **legal** segmented *store* (`vsseg3e32.v`). This path is different: no store, no legal access, only a segment **load** whose element access is refused, and it aborts after 15 instructions. The two may share a root cause in the ROB uop accounting for segment instructions; if so, please treat this as extra evidence on #6475 rather than a separate defect.
