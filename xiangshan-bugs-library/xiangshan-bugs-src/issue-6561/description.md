### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug


A DiffTest mismatch was observed between XiangShan and NEMU when a vector load/store instruction with `vl=0` executes after `csrrw` writes `VS=Clean` into `sstatus`.

The original failing seed is:

```asm
main:
  csrwi vcsr, 4
  csrwi vstart, 0
  vsetivli zero, 0, e64, m1, tu, ma

  fmv.x.d s7, fa0
  csrrw t2, sstatus, s7
  vsseg4e32.v v13, (t6), v0.t
```

The initial value of `fa0` is `0xffffffffbf361d42`, so `csrrw t2, sstatus, s7` writes `0xffffffffbf361d42` into `sstatus`. In this value the `VS` field is `10` (`Clean`), a legal write value for software. The `vsetivli zero, 0, ...` instruction sets `vl=0`, so the following `vsseg4e32.v` is a no-op.

The DiffTest mismatch is:

```
mstatus different at pc = 0x0080000154, right = 0x0000000a0004058a, wrong = 0x8000000a0004078a
sstatus different at pc = 0x0080000154, right = 0x0000000200040502, wrong = 0x8000000200040702
```

The difference is `0x8000000000000200`, which corresponds to:

* `SD` bit becoming set
* `VS` changing from Clean (`10`) to Dirty (`11`)

As specified in the RISC-V Privileged Specification:

> When a vector register or vector CSR is written, VS obtains the value 3.

Since `vsseg4e32.v` with `vl=0` writes no vector register and no vector CSR, `VS` should remain `Clean`. XiangShan, however, changes `VS` to `Dirty`.

The issue is scoped to the combination of a `sstatus` write that leaves `VS` at `Clean` followed by a `vl=0` vector load/store instruction. It does not occur with scalar instructions, CSR reads, or vector ALU instructions after the `sstatus` write, nor when `vl` is raised to `1`, nor when `VS` is written to `Off` (which traps) or is already `Dirty`.

Therefore, the trigger is a `VS=Clean` write to `sstatus` followed by a `vl=0` vector load/store instruction: XiangShan updates `mstatus.VS` from Clean to Dirty even though the vector memory instruction writes no vector architectural state, while NEMU keeps `VS` unchanged.

The DiffTest report is as follows：

[seeds_28_.log](https://github.com/user-attachments/files/32104442/seeds_28_.log)

### Expected behavior

Per the RISC-V Privileged Specification, `VS` becomes `Dirty` (3)  when a vector register or vector CSR is written:

> When a vector register or vector CSR is written, VS obtains the value 3.

For the failing testcase, `vsseg4e32.v` (and `vle32.v`) executes with `vl=0`, so it writes no vector register and no vector CSR. `mstatus.VS` should therefore remain `Clean`, and `SD` should remain `0`.

### Environment

- Repo
    - XiangShan commit id: `b36a1a2a57`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_28_.zip](https://github.com/user-attachments/files/32104469/seeds_28_.zip)

### Additional context

_No response_
