### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

t6 different at pc = 0x0080000240, right= 0x0000000000000000, wrong = 0x9865a11f6ac4f174

### Expected behavior

t6 expected to be 0x0000000000000000

### To Reproduce

[cbo_144.tar.gz](https://github.com/user-attachments/files/21765491/cbo_144.tar.gz)

### Environment

- XiangShan branch: master
- XiangShan commit id: 482b1daff8
- XiangShan config: KunminghuV2Config
- NEMU commit id: 
- SPIKE commit id:


### Additional context

_No response_
