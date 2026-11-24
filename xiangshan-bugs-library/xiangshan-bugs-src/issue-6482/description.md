### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

After an RVV memory instruction triggers an access fault, a following memory access fault of the same direction may not update `mtval`.

Example:

```text
first fault address  = 0x4
second fault address = 0x8

Expected mtval = 0x8
XiangShan mtval = 0x4
```

Difftest reports:

```text
mtval different at pc = ..., right = 0x0000000000000008, wrong = 0x0000000000000004
```

The issue is reproducible for all pairwise combinations of these Vector Load instructions:

```text
vle8.v   vlse8.v   vluxei8.v   vloxei8.v   vle8ff.v   vl1re8.v   vlm.v
```

That is, `7 × 7 = 49` failing `Vector Load -> Vector Load` combinations.

It is also reproducible for all pairwise combinations of these Vector Store instructions:

```text
vse8.v   vsse8.v   vsuxei8.v   vsoxei8.v   vs1r.v   vsm.v
```

That is, `6 × 6 = 36` failing `Vector Store -> Vector Store` combinations.

The same problem can also occur for `Vector Load -> scalar Load` and `Vector Store -> scalar Store`.

One of the DiffTest reports is shown below:

[001_unit__to__unit.log](https://github.com/user-attachments/files/31815750/001_unit__to__unit.log)

### Expected behavior

When the second memory instruction triggers an access fault at address `0x8`, `mtval` should be updated with the fault address of the current exception.

Expected:

```text
mtval = 0x8
```

Instead, XiangShan may retain the address from the previous RVV memory fault:

```text
mtval = 0x4
```

Therefore, after the second exception, `mtval` should reflect the second faulting memory address rather than the stale address from the first RVV memory exception.

### Environment

- Repo
    - XiangShan commit id: `5d39341327`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

I have attached the ELF files used to reproduce this issue.

[reproduce_elf.zip](https://github.com/user-attachments/files/31815867/reproduce_elf.zip)

They include:

```text
49 Vector Load -> Vector Load cases
36 Vector Store -> Vector Store cases
7 Vector Load -> scalar Load cases
6 Vector Store -> scalar Store cases
```

A total of 98 ELF files are provided for reproduction.

In the failing cases, Difftest reports:

```text
mtval different at pc = ..., right = 0x0000000000000008, wrong = 0x0000000000000004
```

### Additional context

_No response_
