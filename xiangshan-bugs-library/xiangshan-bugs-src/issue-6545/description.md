### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. The instruction `vsrl.vv v12, v31, v11, v0.t` at `0x80001030` never retires on XiangShan: after the preceding `vsetvli` commits, the core commits nothing for 15000 cycles ("No instruction of core 0 commits for 15000 cycles, maybe get stuck"), and the destination vector register `v12` is left at its pre-instruction value.

The instruction encoding `0xa1f58657` decodes as `vsrl.vv v12, v31, v11, v0.t` (vector shift right logical, `OPMVV`, `vm=0`, i.e. masked by `v0`). The vector state at the time of execution is `vtype=0xc0` (`vsew=000`=SEW8, `vlmul=000`=m1, `vta=1`, `vma=1`) with `vl=7` and `VLEN=128` (`vlenb=16`).

The mask register is `v0 = 0x04f1ad04d0348b95_4329c10889697c10`. The mask bit for element `i` is bit `i` of `v0`, so elements `0..6` have mask bits `[0, 0, 0, 0, 1, 0, 0]`: only element 4 is active.

- `v31[4] = 0x8d`, `v11[4] = 0xf4`, shift amount `0xf4 & 0x7 = 4`, so   `0x8d >> 4 = 0x08`.

Because the preceding `vsetvli` sets `ta`/`ma` (tail/mask agnostic), NEMU overwrites the inactive (masked-off) and tail elements with 1s, producing:

```
v12 = 0xffffffffffffffff_ffffff08ffffffff
```

XiangShan, however, never performs the writeback. DiffTest reports:

```
No instruction of core 0 commits for 15000 cycles, maybe get stuck
...
v12_low  different at pc = 0xc178ef2885de, right = 0xffffff08ffffffff, wrong = 0xfa4a5c143bf87806
v12_high different at pc = 0xc178ef2885de, right = 0xffffffffffffffff, wrong = 0x8035ebe3997324ab
```

The `wrong` value `0x8035ebe3997324ab_fa4a5c143bf87806` is exactly the value of `v12` *before* the `vsrl.vv` (loaded by `vl1re8.v v12` during `init`), i.e. the instruction never wrote back its result. The pc field reported in the DiffTest mismatch (`0xc178ef2885de`) is not a valid program address.

As specified in the RISC-V Vector Extension:

> A full set of vector shift instructions are provided, including logical shift left (sll), and logical (zero-extending srl) and arithmetic (sign-extending sra)  shift right. The data to be shifted is in the vector register group specified by `vs2` and the shift  mount value can come from a vector register group `vs1`, a scalar integer register `rs1`, or a zero-extended 5-bit immediate. Only the low lg2(SEW) bits of the shift-amount value are used to control the shift amount.
>
> The inactive elements do not raise exceptions and do not update any destination vector register group unless masked agnostic is specified (`vtype[vma]`=1), in which case inactive elements may be overwritten with 1s.
>
> The tail elements do not raise exceptions, and do not update any destination vector register group unless tail agnostic is specified (`vtype[vta]`=1), in which case tail elements may be overwritten with 1s, or with the result of the instruction in the case of mask-producing instructions except for mask loads.

The root cause is hypothesized to be in the vector execution path for masked vector-vector `OPMVV` instructions. With `vm=0`, the masked `vsrl.vv` must read the `v0` mask together with `vs2` (shift data) and `vs1` (shift amount), compute the per-element shift for the active element, and produce a valid writeback for `vd`. XiangShan stalls immediately after the preceding `vsetvli` commits and never retires the `vsrl.vv`; `v12` remains entirely at its pre-instruction value, including the single active element. This suggests the masked execution unit never produces the completion/writeback handshake for this instruction, leaving it stuck in the vector pipeline.

The DiffTest report is as follows：

[seeds_3_.log](https://github.com/user-attachments/files/32048661/seeds_3_.log)

### Expected behavior

XiangShan must execute `vsrl.vv v12, v31, v11, v0.t` to completion and write the correct architectural result into `v12`: element 4 = `0x08` (the only active element), with the inactive/tail elements either retaining their old value or being overwritten with 1s (both legal under `ta`/`ma`). The instruction must retire promptly and must not stall the commit stream for thousands of cycles.


### Environment

- Repo
    - XiangShan commit id: `eedc9aa511`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_3_.zip](https://github.com/user-attachments/files/32048680/seeds_3_.zip)

### Additional context

_No response_
