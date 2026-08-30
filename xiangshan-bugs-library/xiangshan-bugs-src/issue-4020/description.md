### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When testing Xiangshan, I found that Xiangshan could not correctly cause illegal instruction exceptions for some specific instructions. The first one was the instruction that was displayed as `unknown` in the log information. The following is a screenshot of the log information：

![image](https://github.com/user-attachments/assets/d5037186-667a-43ca-9c06-231ca6fcb3b7)
![image](https://github.com/user-attachments/assets/787fb7d7-1649-45f5-9d28-a41acb33d0eb)

The log information of nemu and spike is the same.In my test sample, the instructions that cannot cause exceptions in Xiangshan are `.word 0x787aa557` and `.word 0x65e2aa57`

The **second** case is that the instruction is displayed as V extension in the log information. Xiangshan does not cause an illegal instruction exception, but nemu and spike both cause illegal instruction exceptions. The log information screenshot is as follows：

![image](https://github.com/user-attachments/assets/2b2b9955-9957-40f5-9f48-f996fa662548)
![image](https://github.com/user-attachments/assets/17437fba-15e2-4700-8041-2dabbeb1bdd6)


### Expected behavior

Xiangshan should cause the same illegal instruction exception as nemu and spike

### To Reproduce

This is my test program and log information：[testcase.zip](https://github.com/user-attachments/files/18078627/testcase.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id:  commit 9c1fdd07b152f789df771c255c1955602de54a3c 
- NEMU commit id: 4ecc99ec95eb3d72bae79b70d80b0df16c0e3b2f
- SPIKE commit id:
ready-to-run:commit 75d8a237055ba283141e4fb7cd6b23624f3259bd

### Additional context

_No response_
