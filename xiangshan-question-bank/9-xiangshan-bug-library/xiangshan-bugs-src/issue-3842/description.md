### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

![屏幕截图 2024-11-06 211405](https://github.com/user-attachments/assets/5330d4f1-f442-4e3a-879e-783949abd069)

              total        used        free      shared  buff/cache   available
Mem:              60           1          55           0           3          58
Swap:             39           0          39


### Expected behavior

run make verilog should be PASS


### To Reproduce


make  init 
make verilog


### Environment

- XiangShan branch: master e80f666e9d8075dc7075808399dd6a91266845f2
OS: ubuntu 22.04
mem: 60 phy+30 swap
jdk:17


### Additional context

_No response_
