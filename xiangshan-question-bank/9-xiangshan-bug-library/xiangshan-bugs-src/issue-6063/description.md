### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3 & kunminghu-v2

### Describe the bug

Location: src/main/scala/yunsuan/vector/VectorConvert/CVT16.scala, Line 505
In ```fflags0 := Mux1H(...)```, the branch for ```is_vfrec7_reg0 && is_inf_reg0``` currently uses:
```scala
Mux(is_neginf_reg0, Cat(1.U, 0.U(15.W)), 0.U),
```
```fflags0``` is a 5-bit wire: ```Cat(NV, DZ, OF, UF, NX)```. Assigning a 16-bit value here causes Chisel to truncate to the low 5 bits, producing ```00000``` in both cases.


### Expected behavior

For vfrec7 infinity cases:

-inf should produce b10000 (NV)
+inf should produce b00000

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
