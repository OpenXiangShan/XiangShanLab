### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. A vector store instruction at `0x80001140` (encoding `0x3a3fae27`, opcode `0100111` = vector store) causes an exception. The effective address `0x40491397` is **not 4-byte aligned** (`0x40491397 mod 4 = 3`). NEMU correctly reports `mcause=6` (Store/AMO address misaligned), whereas XiangShan incorrectly reports `mcause=7` (Store/AMO access fault).

The processor is running in M-mode. PMP does not apply — M-mode accesses with MPRV=0 bypass PMP checks per norm `pmp_rwx_check` (PMP0 cfg=0x1f, NAPOT addr=0x3fffffffffff covers an unrestricted physical range). The faulting address `0x40491397` falls within PMA region 19 (`[0x4000000, 0x8000000)`, cfg=`0x0b`), which is a valid I/O region with read and write permissions (cfg `0x0b` = R=1, W=1). The only exceptional condition is the misalignment itself — there is no access-fault condition (the address is within a valid PMA region and PMP does not apply).

The instruction encoding `0x3a3fae27` decodes as a **vector store** with the following field layout:

| Field | Bits | Value | Meaning |
|-------|------|-------|---------|
| opcode | 6:0 | `0100111` | Vector store (STORE-FP) |
| vs3 | 11:7 | `11100` = v28 | Store data source |
| width | 14:12 | `101` | Element width |
| rs1 | 19:15 | `11111` = t6 | Base address |
| mew | 28 | `1` | Extended memory element width |
| mop | 27:26 | `10` | Constant-strided addressing |
| vm | 25 | `1` | Unmasked |
| nf | 31:29 | `001` = 1 | 2-field segment |

The vector state at the time of execution is `vtype=0xd0` (vsew=010=SEW32, vlmul=000=m1) with `vl=4`, so up to four 32-bit elements are to be stored from v28 (and v29 for field 1 of the segment).

The base register `t6=0x40490fdb`. The faulting address `mtval=0x40491397` is 4-byte misaligned for a 32-bit store.

After the fault, DiffTest reports:

```
mcause different at pc = 0xffffeeaaba4e86b2, right = 0x0000000000000006, wrong = 0x0000000000000007
```

- **NEMU**: traps to M-mode with `mcause=6` (Store/AMO address misaligned), `mepc=0x80001140`, `mtval=0x40491397`.
- **XiangShan**: traps to M-mode with `mcause=7` (Store/AMO access fault), same `mepc` and `mtval`.

As specified in the RISC-V Privileged Specification, Section "Machine Cause Register (mcause)":

> Load/store/AMO address-misaligned exceptions may have either higher or lower priority than load/store/AMO page-fault and access-fault exceptions.

The relative priority is **implementation-defined**, allowing two design points:

1. **Misaligned first** — raise misaligned without checking PMA/PMP.
2. **Access fault first** — check PMA/PMP first; raise access fault if violated,  otherwise raise misaligned.

In this case, **neither design point justifies `mcause=7`**:

- Under design point 1, misaligned is detected first → `mcause=6`.
- Under design point 2, PMP checks do not apply in M-mode, and the address falls within a valid PMA region (region 19, cfg=0x0b, R=1, W=1), so no access fault condition exists → deferred to misaligned → `mcause=6`.

XiangShan raises a Store/AMO access fault (`mcause=7`) when the only exceptional condition is address misalignment. This is incorrect regardless of the chosen exception priority model. The same incorrect behavior was previously observed for the scalar `sw` instruction (see Issue #6288), indicating that both the scalar StoreUnit and the Vector LSU share a common defect in how they prioritize misaligned exceptions relative to access faults.

The DiffTest report is as follows：

[seeds_828.log](https://github.com/user-attachments/files/30453586/seeds_828.log)

### Expected behavior

XiangShan should raise a precise Store/AMO address misaligned exception (`mcause=6`) at PC `0x80001140`, matching NEMU's behavior. Both the scalar StoreUnit and the Vector LSU must correctly prioritize address-misaligned exceptions when no access fault condition (PMA/PMP violation) exists.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_828.zip](https://github.com/user-attachments/files/30453594/seeds_828.zip)

### Additional context

_No response_
