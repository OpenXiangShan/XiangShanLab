### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. When executing the instruction `sw t0, 1144(t6)` at PC `0x800011d4` in M-mode, NEMU correctly reports `mcause=6` (Store/AMO address misaligned), whereas XiangShan incorrectly reports `mcause=7` (Store/AMO access fault).

The store address is `t6 + 1144 = 0x7f7fffff + 0x478 = 0x7f800477`, which is **not 4-byte aligned** (`0x7f800477 mod 4 = 3`). In M-mode with `MPRV=0`, PMP checks do not apply. The address falls within the PMP region (`PMP0: NAPOT [0, 256TB]`) and there is no access-fault condition. The only exceptional condition is the misalignment itself.

According to the RISC-V Privileged Specification:

> Load/store/AMO address-misaligned exceptions may have either higher or
> lower priority than load/store/AMO page-fault and access-fault exceptions.

The relative priority is **implementation-defined**, allowing two design points:

1. **Misaligned first**  ---  raise misaligned without checking PMA/PMP.
2. **Access fault first**  ---  check PMA/PMP first; raise access fault if violated, otherwise raise misaligned.

In this case, **neither design point justifies `mcause=7`**:

- Under design point 1, misaligned is detected first  ->  `mcause=6`.
- Under design point 2, PMP check does not apply (M-mode, PMP0 L=0, see norm:pmp_rwx_check) and the address falls within a valid PMA region, so no access fault exists  ->  deferred to misaligned  ->  `mcause=6`.

XiangShan should raise a Store/AMO address misaligned exception (`mcause=6`).
Instead, XiangShan raises a Store/AMO access fault (`mcause=7`), which subsequently leads to the following DiffTest mismatch:

```
mcause different at pc = 0x008000026c, right = 0x0000000000000006, wrong = 0x0000000000000007
```

The DiffTest report is as follows：

[emulator.log](https://github.com/user-attachments/files/30410833/emulator.log)

### Expected behavior

XiangShan should raise a precise Store/AMO address misaligned exception (`mcause=6`) at PC `0x800011d4` to match NEMU's behavior, consistent with the RISC-V exception priority rules.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seed_.zip](https://github.com/user-attachments/files/30410887/seed_.zip)

### Additional context

_No response_
