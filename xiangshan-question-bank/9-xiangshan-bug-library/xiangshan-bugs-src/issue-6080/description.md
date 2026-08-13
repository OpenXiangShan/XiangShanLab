### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

Location: src/main/scala/yunsuan/vector/VectorFloatAdder.scala, Line 885, 1385, 2094

There is an implicit-width ```0.U``` inside ```Cat(...)``` in ```fp_*_mantissa_widen```, which leads to incorrect ```Mux``` width inference and wrong alignment downstream.

Take Line 885 as an example.

```scala
885 val fp_b_mantissa_widen = Mux(EOP,Cat(~significand_fp_b,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_b,0.U(widenWidth.W)))
```
Here the first 0.U has no explicit width. In Chisel, Mux will choose the maximum width of its branches, so if one branch infers a wider 0.U, the result becomes wider than intended.

```scala
886 val U_far_rshift_1 = Module(new FarShiftRightWithMuxInvFirst(fp_b_mantissa_widen.getWidth,farmaxShiftValue.getWidth))
```

```fp_b_mantissa_widen```  is then passed into FarShiftRightWithMuxInvFirst(...)

that module preserves the full input width in ```io.result```.
```far_rshift_widen_result``` therefore can become wider than expected.

```scala
901  val B = Mux(absEaSubEb_is_greater,Fill(significandWidth+1,EOP),far_rshift_widen_result.head(significandWidth+1))
```
later code extracts head(...), if the width is wrong, the extracted guard/round/sticky/alignment bits are wrong
this breaks the far-path alignment and can corrupt FP addition results



### Expected behavior

```fp_*_mantissa_widen``` should always have the intended fixed width

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
