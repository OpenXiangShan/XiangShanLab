### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan (DUT) and NEMU (REF). Both models correctly trap on two consecutive illegal instructions inside a 5-iteration loop at `bwd_0`, forming a **nested exception** (a second trap taken inside the M-mode trap handler before the first `mret`). After the nested exception handler returns via `mret` and the loop iterates, the DUT **deadlocks** when attempting to take the next exception, while the REF continues normally.

The loop at `bwd_0`:

```
8000138c <bwd_0>:
    8000138c:   d0217bd3    fcvt.s.l fs7,sp       # illegal instruction (both trap)
    80001390:   ff47bf73    .word 0xff47bf73      # illegal instruction (both trap)
    80001394:   660fac87    flw fs9,1632(t6)
    80001398:   001d1d73    fsflags s10,s10
    8000139c:   fffd8d93    addi s11,s11,-1
    800013a0:   fff00493    li s1,-1
    800013a4:   0d84f057    vsetvli zero,s1,e64,m1,ta,ma
    800013a8:   fe0d92e3    bnez s11,8000138c <bwd_0>
```

The trap handler at `0x80001000` simply advances `mepc` by 4 and returns:

```
80001000 <other_exp>:
    80001000:   341026f3    csrr a3,mepc
    80001004:   00468693    addi a3,a3,4
    80001008:   34169073    csrw mepc,a3
    8000100c:   30200073    mret
```

In the first loop iteration (`s11=5`):

1. `fcvt.s.l` at `0x8000138c` traps (illegal instruction). Handler sets `mepc=0x80001390`, `mret`s.
2. `0xff47bf73` at `0x80001390` also traps (illegal instruction). This is a **nested exception** taken while still in the M-mode trap handler from step 1. Handler sets `mepc=0x80001394`, `mret`s.
3. Loop body (`0x80001394`–`0x800013a8`) executes normally. `bnez` branches back to `0x8000138c`.

In the second loop iteration (`s11=4`):

4. `fcvt.s.l` at `0x8000138c` traps again on the REF — handler executes normally.
5. The DUT **deadlocks** at `0x8000138c`: no instruction commits for 15000 cycles.

The deadlock triggers a timeout:

```
No instruction of core 0 commits for 15000 cycles, maybe get stuck
(please also check whether a fence.i instruction requires more than 15000 cycles to flush the icache)
Let REF run one more instruction.
```

The REF runs one more instruction from `0x8000138c` (traps on `fcvt.s.l`, enters the handler), then DiffTest compares states:

```
mstatus different at pc = 0x00800013cc, right = 0x8000040a006c7f22, wrong = 0x8000000a006c67a2
   mepc different at pc = 0x00800013cc, right = 0x000000008000138c, wrong = 0x0000000080001394
  mtval different at pc = 0x00800013cc, right = 0x00000000d0217bd3, wrong = 0x00000000ff47bf73
Core 0: ABORT at pc = 0x800013cc
```

The mismatches are a **consequence** of the DUT's deadlock, not the root cause:

- **REF** (`right`): `mepc=0x8000138c` — just trapped on `fcvt.s.l` in iteration 2. `mtval=0xd0217bd3` — the faulting `fcvt.s.l` instruction. `MPP=3` (Machine) — set by the new trap entry.
- **DUT** (`wrong`): `mepc=0x80001394` — still holds the value set by the handler from the **nested exception in iteration 1** (`0x80001390+4`). `mtval=0xff47bf73` — still holds the faulting instruction from iteration 1's nested exception. `MPP=0` (User) — set by the `mret` from the nested exception handler, never updated because the new trap never completed.

The DUT never took the trap at `0x8000138c` in iteration 2 — it deadlocked on the trap entry, leaving all trap CSRs (`mepc`, `mcause`, `mtval`, `mstatus.MPP`) frozen at their values from iteration 1.

As specified for `mepc`:

> When a trap is taken into M-mode, `mepc` is written with the virtual address of the instruction that was interrupted or that encountered the exception. Otherwise, `mepc` is never written by the implementation, though it may be explicitly written by software.

As specified for the `mstatus.MPP` field:

> Written by hardware in two cases:
>
> * Written with the prior nominal privilege level when entering M-mode from an exception/interrupt.
> * Written with 0 when executing an `mret` instruction to return from an exception in M-mode.

As specified for `mtval`:

> When a trap is taken into M-mode, `mtval` is either set to zero or written with exception-specific information to assist software in handling the trap. Otherwise, `mtval` is never written by the implementation, though it may be explicitly written by software.

All three CSRs must be updated on every trap entry. The DUT's failure to update them on the iteration-2 `fcvt.s.l` trap confirms that the trap was never architecturally taken — the pipeline deadlocked before completing the trap entry sequence. After the nested exception in iteration 1, some microarchitectural resource (e.g., pipeline flush logic, trap prioritization state machine, or CSR write port arbitration) was left in an inconsistent state, causing the next trap entry to hang indefinitely.

The DiffTest report is as follows：

[seeds_349_.log](https://github.com/user-attachments/files/30487884/seeds_349_.log)


### Expected behavior

After a nested exception is handled and `mret` returns to the original instruction stream, subsequent exceptions must be taken normally without deadlock. The DUT must correctly update `mepc`, `mcause`, `mtval`, and `mstatus` on every trap entry, regardless of whether a previous nested exception occurred.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds.zip](https://github.com/user-attachments/files/30487891/seeds.zip)

### Additional context

_No response_
