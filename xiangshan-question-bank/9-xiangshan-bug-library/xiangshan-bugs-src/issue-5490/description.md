### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

Error when generating.v file by run "make verilog CONFIG=KunminghuV2MinimalConfig"

[676] [error] XiangShan/openLLC/src/main/scala/openLLC/LLCParam.scala 102:9: Invalid bit range [hi=-4, lo=0]
[676] [error] There were 1 error(s) and 92 warning(s) during hardware elaboration.
[676/676] =============== xiangshan.runMain top.TopMain --target-dir build/chi_mini_rtl --config...ll --remove-assert --reset-gen --firtool-opt --ignore-read-enable-mem =============== 201s
1 tasks failed
xiangshan.runMain Subprocess failed
make: *** [Makefile:181: build/chi_mini_rtl/XSTop.sv] Error 1

### Expected behavior

generated.v file

### To Reproduce

make verilog CONFIG=KunminghuV2MinimalConfig

### Environment

- XiangShan branch: master
- XiangShan commit id: 4b9ddb8a6311bff6f5e29cb3722d1f4236d66292
- XiangShan config:  KunminghuV2MinimalConfig
- NEMU commit id:
- SPIKE commit id:


### Additional context

_No response_
