### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

The `vmv<nr>r.v` `vstart >= evl` reserved case does not raise illegal instruction in DUT.

Instruction sequence:

```asm
vsetivli zero, 4, e64, m1, ta, ma
csrr t0, vlenb
srli t0, t0, 3
csrw vstart, t0
vmv1r.v v8, v10
```

For `vmv1r.v` and `SEW=64`:

```text
evl = NREG * VLEN / SEW = VLEN / 64 = vlenb / 8
```

The testcase writes `vstart = vlenb / 8`, so `vstart >= evl`.

NEMU REF traps:

```text
mcause = 0x2
mepc   = 0x800000d0
mtval  = 0x9ea03457
vstart = 0x2
```

DUT does not trap and `vstart` diverges:

```text
mcause = 0x0
mepc   = 0x0
mtval  = 0x0
vstart = 0x0
```

Key mismatch:

```text
mepc different at pc = 0xffffb3a31236d5d2, right = 0x00000000800000d0, wrong = 0x0000000000000000
mtval different at pc = 0xffffb3a31236d5d2, right = 0x000000009ea03457, wrong = 0x0000000000000000
mcause different at pc = 0xffffb3a31236d5d2, right = 0x0000000000000002, wrong = 0x0000000000000000
vstart different at pc = 0xffffb3a31236d5d2, right = 0x0000000000000002, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0xffffb3a31236d5d2
```

### Expected behavior

`vmv1r.v` should raise illegal instruction when `vstart >= evl`.

 Actual Behavior:
DUT executes past the instruction without taking the illegal-instruction exception, and `vstart` is cleared/changed to `0`.

### Environment

 - XiangShan commit id: f3cc750109cc2a0ff6c12a920221f1a5a324bc75
  - NEMU commit id (if difftest failed with NEMU): 4330f15c192f6add96e7e5190c51598aaf7728fc
  - ready-to-run commit: e745a730a26626ca2f3ab6ecb0d7ca8087b6f8b2
  message: Bump nemu ref in ready-to-run
  date: 2026-04-02




### To Reproduce

[r209_vmv1r_vstart_ge_evl_bundle.zip](https://github.com/user-attachments/files/28335715/r209_vmv1r_vstart_ge_evl_bundle.zip)

### Additional context

_No response_
