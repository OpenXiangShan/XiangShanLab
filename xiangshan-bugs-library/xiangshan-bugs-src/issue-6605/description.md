### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A PMP entry with `L = 1` applies to machine mode. An M-mode access to a block that such an entry covers with no permissions must be refused.

A **strided** or **indexed** vector access to that block retires silently instead. No trap is taken and no trap CSR is written, so execution falls through to the next instruction. The same access in its **unit-stride** form, to the same bytes, traps correctly.

```asm
  # PMP entry 0: NAPOT, one 4 KiB block at 0x80600000, locked, no R/W/X
  li    t0, 0x201801ff          # (0x80600000 >> 2) | 0x1ff
  csrw  pmpaddr0, t0
  li    t0, 0x98                # L | NAPOT, no permissions
  csrw  pmpcfg0, t0

  li    t1, 0x80600000          # inside the denied block
  li    t2, 4
  vsetvli x0, t2, e32, m1, ta, ma
  li    t3, 4                   # stride = element width: the same bytes a
  vsse32.v v8, (t1), t3         # unit-stride store would touch
```

difftest against the commit's own NEMU:

```
mstatus different at pc = ..., right = 0x8000040a00001e00, wrong = 0x8000000a00000600
   mepc different at pc = ..., right = 0x0000000080000036, wrong = 0x0000000000000000
  mtval different at pc = ..., right = 0x0000000080600000, wrong = 0x0000000000000000
 mcause different at pc = ..., right = 0x0000000000000007, wrong = 0x0000000000000000
```

All four trap CSRs stay at zero on the DUT: the core never took the trap.

### The clearest form of the evidence

Two programs that differ by one `li` and one instruction word — same PMP configuration, same address, same `vl` and `vtype`. With stride 4 at SEW 32 the strided store touches exactly the bytes the unit-stride store touches:

| instruction | encoding | result |
|---|---|---|
| `vse32.v  v8, (x6)` | `0x02036427` | traps correctly, `mcause = 7` |
| `vsse32.v v8, (x6), x7`, `x7 = 4` | `0x0a736427` | **no trap, all trap CSRs 0** |

Only the addressing mode encoded in the instruction differs, and one is stopped by the locked entry while the other is not.

A stride sweep of the reproducer at 0, 4, 8, 12, 16 and 64 gives the identical signature in all six, so the stride value is not part of the trigger.



### Expected behavior

The access raises a store/AMO access fault (`mcause = 7`) with `mtval` holding the faulting address, as NEMU and Spike both do.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv64-unknown-elf-gcc (reproducer build)
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `c8d7b3a5c1abf3f42c954e61abba20dd27e02a21` (kunminghu-v3; also reproduced on `37ce1b50b5`)
  - NEMU: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `45638f5145aa9c7eee85cd9b08eb8962ee46b586`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig`
  - Run command: `./build/emu -i f4_vsse_locked_pmp.bin --diff <nemu-so> -I 400`

### To Reproduce


1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i f4_vsse_locked_pmp.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 400
   ```
3. Observe the four trap CSRs differing, the DUT holding zero in all of them.

The reproducer also installs a trap handler and writes its own verdict to
`tohost`, so it can be read without difftest:

| `tohost` | meaning |
|---|---|
| 1 | the access trapped with `mcause = 7` (Spike, NEMU) |
| 3 | the access retired, no trap (XiangShan) |
| 5 | it trapped, with another cause |

Loading a flat image gives the emulator no symbol table, so `tohost` is not
printed in `--no-diff` mode; use difftest as above.

[f4_vsse_locked_pmp.elf.zip](https://github.com/user-attachments/files/32434437/f4_vsse_locked_pmp.elf.zip)

### Additional context

### How wide it is

Every memory instruction was run against this same PMP state. 68 of them walk through the entry:

| instruction class | bypasses | traps correctly |
|---|---:|---:|
| strided vector **stores** — `vsse8/16/32/64.v`, `vssseg<n>e<w>.v` | **32** | 0 |
| indexed vector loads and stores, segment count a power of two — `vloxei*`, `vluxei*`, `vsoxei*`, `vsuxei*` and their `seg` forms | **32** | 0 |
| `cbo.clean` / `cbo.flush` / `cbo.inval` / `cbo.zero` | 4 | 0 |
| **scalar, floating-point and atomic accesses** (`ld`, `sd`, `lw`, `amoadd.d`, …) | **0** | **35** |
| vector unit-stride, whole-register and mask forms | 0 | 16 |
| strided vector **loads** | 0 | — (diverge on `mtval` only, a separate matter) |

Indexed accesses whose segment count is not a power of two (`nf` = 3, 5, 6, 7) trip the ROB assertion of #6596 before the PMP question is answered, so no indexed access to this region behaves correctly.

### Relation to #6595

#6595 reports the same symptom for `cbo.*`. This is filed separately because the instructions are different and so is the path: a strided vector store is not a cache-block operation, and the scalar store to the same address is stopped correctly. The two may share a root cause in how the locked-entry check reaches the vector and CMO paths; if so, please treat this as the wider case of #6595 rather than a separate defect.

There is one concrete difference in what can be said about the branches. #6595 cannot be tested on kunminghu-v2, because `HasCMO = coreParams.HasCMO && p(EnableCHI)` makes every `cbo.*` an illegal instruction there under `DefaultConfig`. Vector strided stores exist on both branches, so this form can be tested — and it does not reproduce on v2.
