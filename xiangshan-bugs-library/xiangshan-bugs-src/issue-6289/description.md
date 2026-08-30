### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest hang was observed between XiangShan and NEMU. After a `vse32.v` (vector store) raises a Store/AMO access fault (cause=7) and the exception handler returns via `mret`, the DUT stops committing instructions entirely when it encounters the next AMO instruction.

After 15,000 cycles of inactivity, the DiffTest framework lets NEMU run one more instruction. NEMU successfully executes the AMO, while the DUT remains stuck, resulting in a register mismatch:

```
a2 different at pc = 0x1099d9253ebf2, right = 0x00000000c0c0c0c0, wrong = 0xc6fe9d4ace7be0fa
```

The deadlock occurs regardless of AMO opcode (tested: `amoswap.d.aq`, `amominu.w.aq`), operand width (32-bit and 64-bit), and memory ordering annotation (`.aq`, `.rl`). The sole trigger is the `vse32.v` store access fault.

As specified in the RISC-V Privileged Specification:

> When a trap is taken into M-mode, `mepc` is written with the virtual address of the instruction that was interrupted or that encountered the exception.

No architectural state associated with the faulting instruction should prevent subsequent instructions from making forward progress after `mret`.

The root cause is hypothesized to be in the Vector LSU: when `vse32.v` raises a store access fault, the pipeline is flushed, but residual microarchitectural resources allocated by the aborted vector store (e.g., cache line reservations or store buffer entries) are not fully released. When a subsequent AMO attempts to acquire LSU resources, it deadlocks waiting on the stale state.

The DiffTest report is as follows：

[emulator.log](https://github.com/user-attachments/files/30415951/emulator.log)

### Expected behavior

After the exception handler returns from a `vse32.v` store access fault, subsequent AMO instructions should execute normally without deadlock. The LSU should fully release all resources allocated by the aborted vector store before `mret` completes.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds.zip](https://github.com/user-attachments/files/30416057/seeds.zip)

### Additional context

_No response_
