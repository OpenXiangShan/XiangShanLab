### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

privilegeMode: 3
     a3 different at pc = 0x0080000344, right= 0x0000000000000000, wrong = 0x00000000800100c0
   mode different at pc = 0x0080000344, right= 0x0000000000000001, wrong = 0x0000000000000003
mstatus different at pc = 0x0080000344, right= 0x8000000a00006080, wrong = 0x8000040a00006800
   mepc different at pc = 0x0080000344, right= 0x000000008000033c, wrong = 0x0000000080000348
  mtval different at pc = 0x0080000344, right= 0x000000003511b673, wrong = 0x00000000150816f3

Full logs can be found in the attachment.

[countertimer_32.txt](https://github.com/user-attachments/files/21765759/countertimer_32.txt)

### Expected behavior

pass

### To Reproduce

[countertimer_32.tar.gz](https://github.com/user-attachments/files/21765809/countertimer_32.tar.gz)

### Environment

- XiangShan branch: master
- XiangShan commit id: 482b1daff8
- XiangShan config: KunminghuV2Config
- NEMU commit id: 
- SPIKE commit id:


### Additional context

_No response_
