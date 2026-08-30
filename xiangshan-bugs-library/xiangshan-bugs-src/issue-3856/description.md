### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Nemu is used as a reference model to test Xiangshan. When executing the `csrrwi t5, sip, 23` instruction, there is a difference between nemu and Xiangshan. The specific difference is the `9th` bit, which is the `SEIP` bit.

### Expected behavior

![image](https://github.com/user-attachments/assets/9d49a429-32ca-401b-9888-6723ebfaa33d)
![image](https://github.com/user-attachments/assets/213d7de5-1471-4395-96b6-89e746d1d82c)

The RISCv specification is as follows:
```
Bits sip.SEIP and sie.SEIE are the interrupt-pending and interrupt-enable bits for supervisor-level
external interrupts. If implemented, SEIP is read-only in sip, and is set and cleared by the execution
environment, typically through a platform-specific interrupt controller.
```


### To Reproduce

This is the test case [sip.zip](https://github.com/user-attachments/files/17699652/sip.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id: 7af39ad2ddb1305b2c4ddf4c3a9663a7c3615fa6
- NEMU commit id: 
- SPIKE commit id:
- ready-to-run:c09f524c6ecb43cd047b226e2da4aad7edd05702


### Additional context

_No response_
