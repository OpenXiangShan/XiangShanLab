### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Hi there,

I have discovered that in M-mode, XiangShan fails to handle **specific sequences** of illegal instructions correctly. When an illegal instruction exception is triggered by such a sequence, the XS does not function as expected, and execution is disrupted.

Although the illegal instruction involves accessing a custom register, a review of the code shows that XiangShan does not define this custom register. Additionally, logs indicate that XiangShan does raise an illegal instruction exception but fails to successfully enter the exception handler.

**Screenshots**
When spike as a ref:
![image](https://github.com/user-attachments/assets/386a8fad-14f9-4eaa-981d-4ff39f256fdf)

When nemu as a ref:
![image](https://github.com/user-attachments/assets/b326884f-01dc-4992-8e41-950c7a9ab8d4)






### Expected behavior

The XS should handle exceptions triggered by specific sequences of illegal instructions and continue execution, consistent with the behavior of NEMU and SPIKE.

### To Reproduce

testcase: [ill-test.zip](https://github.com/user-attachments/files/17954627/ill-test.zip)


### Environment


- XiangShan commit id: edb1dfa (Date:   Wed Nov 27 13:40:59 2024 +0800)
- Ready-to-run (NEMU & SPIKE) commit id: https://github.com/OpenXiangShan/ready-to-run/commit/c1dc496 (Date:   Thu Nov 28 12:58:07 2024 +0800)


### Additional context

_No response_
