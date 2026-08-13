### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

The details of problem description has been summited in https://github.com/XS-MLVP/hackathon2512/issues/1 , please check it.
The core step of Vector floating-point multiply-add (VectorFMA) operations is to the decimal points of the product ($A \times B$) and the addend $C$. Hardware typically selects the number with the larger exponent as the reference and shifts the mantissa of the number with the smaller exponent to the right. However, if $C$ is significantly larger than the product, the FloatFMA module will produce completely incorrect results.

### Expected behavior

testcase: 
A=1.0, B=2.0, C=1.1529e+18 ( $2^{60}$ )
Expected Result: 1.1529e+18 (Product implies no change to C due to precision)

>Since $2^{60}$ is vastly larger than $2.0$, and Double Precision only holds 53 bits of significand, the addition $2^{60} + 2$ should result in $2^{60}$ (the $2.0$ is shifted out/lost to precision).

Actual Result: 8.5071e+37 (Hex: 0x47D0000000000000)

### To Reproduce

The testcase code and result is compressed to test.zip (install of toffee is needed). 
[FMA_align_logic_error.tar.gz](https://github.com/user-attachments/files/24348528/FMA_align_logic_error.tar.gz)

### Environment

- XiangShan branch: master
- XiangShan commit id: latest
- YunSuan bug file: https://github.com/OpenXiangShan/YunSuan/blob/5859c9a7989467c939d28c285c2dc77d30245dcc/src/main/scala/yunsuan/fpu/FloatFMA.scala


### Additional context

_No response_
