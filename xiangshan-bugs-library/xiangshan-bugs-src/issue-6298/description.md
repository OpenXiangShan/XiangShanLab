### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. At PC `0x80001048`, the instruction `0x5e003457` (`vmv.v.i v8, 0`) causes a divergence: **NEMU raises an illegal instruction exception** (`mcause=2`), while **XiangShan commits the instruction** normally.

The encoding matches the standard `vmv.v.i` pattern (`0101111 00000 ----- 011 ----- 1010111`), per the RISC-V ISA manual.

NEMU raises illegal instruction:

```
mcause: 2 (illegal instruction)
mepc:   0x80001048
mtval:  0x5e003457
```

XiangShan's commit trace shows the instruction committed normally:

```
[31] commit pc 0x80001048 inst 5e003457 wen 1 dst 32 data 0x0000000000000000
```

The CSR state at the point of divergence:

| CSR | Value |
|-----|-------|
| vtype | `0xd0` |
| vl | `0x4` |
| vstart | `0x66` |

The `vtype` value `0xd0` is a valid configuration (SEW=32, LMUL=m1, mask-agnostic, tail-agnostic) and is not the cause of the mismatch. The root cause is **`vstart = 0x66`**, which is massively beyond `vl = 4`. The preceding instruction `csrrw tp, vstart, tp` at PC `0x80001040` writes a garbage value from `tp` into `vstart`, corrupting it.

Per the RISC-V V extension specification, values of `vstart` greater than the largest element index for the current `vtype` are reserved:

> The use of `vstart` values greater than the largest element index for the current `vtype` setting is reserved.
>
> NOTE: It is recommended that implementations trap if `vstart` is out of bounds. It is not required to trap, as a possible future use of upper `vstart` bits is to store imprecise trap information.

Here `vstart = 0x66` is far beyond VLMAX, falling squarely into the reserved range. NEMU correctly implements the recommended trap behavior and raises an illegal-instruction exception. The behavior is consistent with Spike, the golden reference model for RISC-V.


The DiffTest report is as follows：

[seeds_987.log](https://github.com/user-attachments/files/30455539/seeds_987.log)


### Expected behavior

XiangShan should trap when a vector instruction is executed with `vstart` holding a reserved/out-of-range value, consistent with both NEMU and Spike. While the specification does not mandate trapping (it is "not required"), trapping is the recommended behavior and is what both reference models implement.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_987.zip](https://github.com/user-attachments/files/30455559/seeds_987.zip)

### Additional context

_No response_
