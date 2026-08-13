### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`vsext.vf4` produces an incorrect result when `SEW = e32`. The low half of the destination vector is generated correctly, but the high half appears to be formed from incorrect source bytes.

The relevant instruction sequence is:

```asm
vsetivli x0, 4, e32, m1, ta, ma
...
vsext.vf4 v21, v16
```

At the mismatch point, the source vector register is:

```text
v16 = 0x18a9f96f2f6c0e15_dc29653c034faf37
```

With `SEW = e32`, `vsext.vf4` should treat the source elements as 8-bit values and sign-extend them into 32-bit destination elements. Since `VL = 4`, the first four 8-bit source elements should be used.

The first four source bytes of `v16` are:

```text
0x37
0xAF
0x4F
0x03
```

Therefore, the correct result of:

```asm
vsext.vf4 v21, v16
```

should be:

```text
low  64 bits = 0xffffffaf00000037
high 64 bits = 0x000000030000004f
```

The reference model produces the expected result. However, XiangShan produces an incorrect high 64-bit result:

```text
expected high 64 bits = 0x000000030000004f
actual   high 64 bits = 0x000000650000003c
```

This indicates that the first two destination elements are generated correctly, while destination elements 2 and 3 are extended from the wrong source bytes. Instead of using source bytes 2 and 3:

```text
0x4F
0x03
```

the DUT appears to use source bytes 4 and 5:

```text
0x3C
0x65
```


### Expected behavior

`vsext.vf4` should sign-extend each source element whose width is `SEW / 4` into one destination element of width `SEW`.

For this testcase, with `SEW = e32`, the source element width is 8 bits. The first four 8-bit elements of `v16` should be sign-extended into four 32-bit elements in `v21`.

The expected result is:

```text
v21[63:0]    = 0xffffffaf00000037
v21[127:64]  = 0x000000030000004f
```

### Environment

Branch: kunminghu-v3
Commit: https://github.com/OpenXiangShan/XiangShan/commit/0f72de270203701e4793cbfe4c20f3c9f398c4c8

### To Reproduce

[testcase-vsext.zip](https://github.com/user-attachments/files/27553715/testcase-vsext.zip)

### Additional context

_No response_
