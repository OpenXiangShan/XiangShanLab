### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When executing `fadd.h fs11, ft0, fs7` instructions (`ft0: 0xffffffffffff0000` `fs7: 0xffffffffffff828d`), the operation results are different.

### Expected behavior

Log screenshot
![image](https://github.com/user-attachments/assets/99c170f6-3b70-4776-b044-f1c6eea61a65)
![image](https://github.com/user-attachments/assets/c9d843f2-ad56-46c0-86a1-cb1a7da78db0)
![image](https://github.com/user-attachments/assets/9c3866c9-5f87-4b08-a591-b37c2edb876f)

The log information of spike is the same, but the result of Xiangshan is unexpected.

### To Reproduce

Initialize ft0: 0xffffffffffff0000
Initialize fs7: 0xffffffffffff828d
Execute `fadd.h fs11, ft0, fs7`

### Environment

- XiangShan branch: 
- XiangShan commit id: b00d5822032eadad2744fcfc0a8c03fa011ff81c (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: 
- SPIKE commit id:
ready-to-run:commit 457f091898b2bcd26c8f6e983f3df174f990af43

### Additional context

_No response_
