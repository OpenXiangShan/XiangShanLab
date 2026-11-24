### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU for a `vmadc.vi v2, v26, 1` instruction at `0x80001034`. The vector state at the time of execution is `vtype=0xd1` (`vsew=010=SEW32, vlmul=001=LMUL2, vta=0, vma=1`) with `vl=6`, `VLEN=128`, so `VLMAX=8`.

`vmadc.vi` is a mask-producing instruction: it computes the carry-out of `vs2[i] + imm` for each active element and writes the result as a packed mask into the destination vector register (one bit per element). Here `imm=1`, and with `LMUL=2` the source register roup is `v26`-`v27`; the six active `vs2` elements (`SEW32`) are:

```
0xf1750b7b 0x1edf0ae6 0x00093dab 0x0868489e 0x2d2ce571 0x60600b26
```

None of these additions produce a carry, so active mask bits `0..5` are `0`.

As specified in the RISC-V Vector Extension:

> Because these instructions produce a mask value, they always operate with a tail-agnostic policy.
>
> Mask destination tail elements are always treated as tail-agnostic, regardless of the setting of `vta`.
>
> When a set is marked agnostic, the corresponding set of destination elements in any vector destination operand can either retain the value they previously held, or are overwritten with 1s.

NEMU (which implements the agnostic policy by writing 1s) therefore produces:

```
v2 : 0xffffffffffffffff_ffffffffffffffc0
```

XiangShan instead zeroes the low 64 bits of `v2` (DiffTest aborts at the first mismatch, so only `v2_low` is reported):

```
v2_low different: right=0xffffffffffffffc0, wrong=0x0000000000000000
```

The mismatch is reported at the commit of the `vmadc.vi` itself (`pc=0x80001034`), before the following instruction commits.

The mismatch is only exposed when `vmadc.vi` is immediately followed by another vector instruction (`vadd.vv v9, v7, v5`). When `vmadc.vi` is followed only by scalar `nop`, XiangShan matches NEMU and the test passes.
This indicates the `vmadc.vi` mask-destination write-back/merge (in particular the tail fill of the low 64-bit lane) depends on the instructions in the vector pipeline behind it, rather than being computed deterministically from the instruction's own operands.

The DiffTest report is as follows：

[seeds_1.log](https://github.com/user-attachments/files/32010282/seeds_1.log)


### Expected behavior

`vmadc.vi` must write the carry-out mask into active mask bits `0..5` and handle the mask tail as tail-agnostic: either preserve the old value or write 1s. It must not write 0s to the tail. The architectural destination value must also be independent of the surrounding instruction stream; a following `vadd.vv` must not change the result of the preceding `vmadc.vi`.


### Environment

- Repo
    - XiangShan commit id: `421ad2da18`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

Two attached reproducers: 
[seeds.zip](https://github.com/user-attachments/files/32010327/seeds.zip)

`seeds_1.elf` (`vmadc.vi v2, v26, 1` followed by `vadd.vv v9, v7, v5`) triggers the mismatch, while `seeds_0_.elf` — identical except that `vmadc.vi` is followed only by `nops` — passes, showing the result depends on the following instruction. 

### Additional context

_No response_
