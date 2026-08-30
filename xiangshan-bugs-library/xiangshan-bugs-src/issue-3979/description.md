### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Hi,

I noticed that the `flh` instruction in XiangShan doesn't perform sign extension when loading a 16-bit floating-point value into a floating-point register.

**Screenshots**
When nemu as a ref:
![image](https://github.com/user-attachments/assets/afb2aefd-3cd3-46f1-a6ee-ba71a57805e7)

When spike as a ref:
![image](https://github.com/user-attachments/assets/2b111985-8e93-4b00-a0ff-83e53ae3151f)


### Expected behavior

When executing `flh`, sign extension should be applied before storing the value into the destination register.

### To Reproduce

testcase: [testcase.zip](https://github.com/user-attachments/files/17993242/testcase.zip)


### Environment

- ready-to-run's NEMU commit id: [OpenXiangShan/ready-ro-run@3575e65](https://github.com/OpenXiangShan/ready-to-run/commit/c1dc496) (Date:   Thu Nov 28 12:58:07 2024 +0800) 
- XiangShan commit id: fcefab3267fdc4c23472db8e6bca6d6054242e8a (Date:   Fri Nov 29 18:39:16 2024 +0800)


### Additional context

_No response_
