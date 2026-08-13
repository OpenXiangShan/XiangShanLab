### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3 & kunminghu-v2

### Describe the bug

Location: src/main/scala/yunsuan/vector/VectorALU/VMask.scala, Line 228
```vstartRemain := Mux(vid_v, Mux(vstart >= (uopIdx(5, 1) << vsew_plus1), (vstart - (uopIdx(5, 1) << vsew_plus1)), 0.U), 0.U```

In ```VMask.scala```, the expression ```uopIdx(5,1) << vsew_plus1``` is computed on a 5-bit UInt, which is too narrow when ```SEW=8``` (```vsew_plus1 == 4```).

1.```uopIdx``` is defined as UInt(6.W) in ```VIFuInfo```
2.```uopIdx(5,1)``` therefore produces a 5-bit value (0..31)
3.Chisel preserves the operand width for ```<<```
4.For ```SEW=8```, ```uopIdx(5,1) << 4``` can require up to 9 bits
5.The 5-bit result is truncated, producing incorrect offsets

### Expected behavior

Widen the shift operand before left shifting.

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
