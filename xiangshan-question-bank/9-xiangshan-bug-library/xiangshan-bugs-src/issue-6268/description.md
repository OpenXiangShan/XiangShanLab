### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

XiangShan hits a hardware assertion in the L1 DCache MainPipe:

```
Assertion failed at MainPipe.sv:3409 / LogUtils.scala:133
L1DCache failed too many SCs in a row, resv set addr always match
Core 0: ABORT at pc = 0x80001df4
Core-0 instrCnt = 3,617, cycleCnt = 33,694
```

The seed places two SC instructions inside a backward loop (`bwd_0`, 62 iterations). After the first iteration's SC clears the LR reservation, the remaining ~123 SC instances all fail with `resv set addr always match` — the address still matches the old LR block but the reservation is no longer valid. The DCache's debug counter tracks consecutive such failures and asserts at 100.

##### Triggering Code Structure

```asm
80001c88:  lr.d.aqrl  a7,(t6)         ; sets reservation at (t6)

80001cb0:  sc.d       a0,s0,(t6)      ; first SC. Clears reservation per spec.

80001dcc:  li         s11,62           ; loop count = 62
80001dd0:  <bwd_0>:
     ...                                ; ~50 instructions, no LR
80001e6c:  sc.w.rl    s2,s1,(t6)      ; SC inside loop. No valid reservation.
80001e74:  sc.w.aq    s4,ra,(t6)      ; SC inside loop. No valid reservation.
     ...
80001e90:  bnez       s11,80001dd0    ; 62 iterations × 2 SCs = 124 SCs
```

After the first SC at `0x80001cb0`, no new LR executes. Every SC inside the loop reaches S3 with the old block address still matching but the reservation expired. After ~100 such SCs, the assertion fires.

##### Spec Reference

From RISC-V Unprivileged Specification
> Regardless of success or failure, executing an SC instruction invalidates any reservation held by this hart.

The first SC at `0x80001cb0` destroys the reservation set by the LR at `0x80001c88`. After this point, no valid reservation exists for `(t6)`.

> An SC must fail if there is another SC (to any address) between the LR and the SC in program order.

Every SC inside the loop occurs after the intervening SC at `0x80001cb0` — all must fail and return `rd≠0`. The hardware correctly fails these SCs; the issue is that the debug counter treats this correct behavior as an error condition.

The  report is as follows：

[emulator.log](https://github.com/user-attachments/files/30249683/emulator.log)

### Expected behavior

SC instructions whose reservation has been cleared by a prior SC correctly fail and return `rd≠0`. A software loop containing many such failing SCs should not trigger a fatal hardware assertion.

### Environment

- Repo
    - XiangShan commit id: `b90dbba40d`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seed.zip](https://github.com/user-attachments/files/30249690/seed.zip)

### Additional context

_No response_
