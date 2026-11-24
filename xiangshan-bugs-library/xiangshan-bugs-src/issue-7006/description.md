### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The seed enters S mode, clears `sstatus.FS`, and executes:

```asm
csrrwi x0, 0x100, 7         # write 7 to writable sstatus fields; FS becomes Off
addi x16, x15, 0
fmul.d f0, f15, f9, rmm     # illegal (FS=0), delegated to S
csrr x25, 0x306             # next sequential instruction; illegal if reached in S
```

`fmul.d` at `0x80000068` raises an illegal-instruction exception (cause 2), delegated to S. The following `csrr mcounteren` would also be illegal in S, but it is not architecturally executed in this failing path; it is the next sequential instruction after the faulting `fmul.d`. The S handler advances
`sepc` and reaches `ecall` at `0x80000080`.

REF takes the ECALL-from-S trap and enters M mode with `mcause=9` and `mepc=0x80000080`. The DUT remains in S mode with the ECALL at the CROB head and stops committing for 15000 cycles:

```text
[27] exception pc 0000000080000068 inst 1297c053 cause 0000000000000002
[31] commit pc 000000008000007c inst 51000f93 ...
No instruction of core 0 commits for 15000 cycles, maybe get stuck
[CROB-STUCK] ... valid=1 ... slotNeedFlush=01 ...
             headPC=0000080000080
```

After REF takes the ECALL-from-S trap, DiffTest reports:

```text
   mode different ..., right = 0x0000000000000003, wrong = 0x0000000000000001
mstatus different ..., right = 0x0000040a00200920, wrong = 0x0000000a002001a0
   mepc different ..., right = 0x0000000080000080, wrong = 0x0000000080000060
 mcause different ..., right = 0x0000000000000009, wrong = 0x0000000000000000
```

REF has entered M mode and recorded the ECALL at `mepc=0x80000080` with `mcause=9`. The DUT remains in S mode and has not updated `mepc`/`mcause` for the ECALL; they remain `0x80000060` and `0`. This shows that the DUT stalls before completing the new trap entry.

The DiffTest report is as follows：

[minimal.log](https://github.com/user-attachments/files/35064272/minimal.log)


### Expected behavior

The `ecall` at `0x80000080` should raise an ECALL-from-S exception and enter M mode at `mtvec` with `mcause=9` and `mepc=0x80000080`, leaving
`sepc=0x8000006c` from the previous S-mode trap. The ECALL exception should be taken and execution should continue at `mtvec`; it must not cause a 15000-cycle no-commit stall or leave the CROB stuck at the ECALL.

### Environment

- Repo
    - XiangShan commit id: `1e05481a4c`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[minimal.zip](https://github.com/user-attachments/files/35064258/minimal.zip)

### Additional context

_No response_
