### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At PC `0x800012f8`, XiangShan executes `ld s8,0xf0(t6)` (`0x0f0fbc03`) with `t6=0x1`. The effective address `0xf1` is misaligned for an 8-byte load (`0xf1 & 0x7 = 1`). NEMU raises **Load address misaligned** (`mcause=4`). XiangShan raises **Load access fault** (`mcause=5`).



| Field | DUT (XiangShan) | REF (NEMU) |
|-------|-----------------|------------|
| **Instruction @ 0x800012f8** | `ld s8, 0xf0(t6)` (committed) | `ld s8, 0xf0(t6)` |
| **t6 (rs1)** | `0x0000000000000001` | same |
| **Effective address** | `0x1 + 0xf0 = 0xf1` | same |
| **mcause** | **`0x5`** (Load access fault) | `0x4` (Load address misaligned) |
| **mepc** | — | `0x800012f8` |
| **mtval** | — | `0x00000000000000f1` |


Address `0xf1` is not covered by any PMA memory region.
```
PMA20: cfg=0x0f  addr=0x0000000008000000  ← RAM
```


RISC-V Privileged Spec :

> Load/store/AMO address-misaligned exceptions may have either higher or lower priority than load/store/AMO page-fault and access-fault exceptions.

> [NOTE]
> The relative priority of load/store/AMO address-misaligned and page-fault exceptions is implementation-defined to flexibly cater to two design points. Implementations that never support misaligned accesses can unconditionally raise the misaligned-address exception without performing address translation or protection checks. Implementations that support misaligned accesses only to some physical addresses must translate and check the address before determining whether the misaligned access may proceed, in which case raising the page-fault exception or access is more appropriate.


NEMU follows design point 1 — raising `mcause=4` unconditionally before PMA checks. XiangShan currently follows design point 2 — checking PMA first and raising `mcause=5`.


The DiffTest report is as follows：

[seeds.log](https://github.com/user-attachments/files/30221865/seeds.log)


### Expected behavior

To align with the NEMU reference model, `ld s8,0xf0(t6)` with t6=1 should raise **Load address misaligned** (`mcause=4`) with `mepc=0x800012f8` and `mtval=0x00000000000000f1`, i.e., XiangShan should adopt design point 1 (check misalignment before PMA).

### Environment

- Repo
    - XiangShan commit id: `b90dbba40d`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seed.zip](https://github.com/user-attachments/files/30221846/seed.zip)

### Additional context

_No response_
