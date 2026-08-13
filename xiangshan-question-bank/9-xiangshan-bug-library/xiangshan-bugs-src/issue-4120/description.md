### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

After setting PMP region, inconsistency behavior occurred.

![image](https://github.com/user-attachments/assets/bb2baf9a-b5da-469f-85e9-642b53c5ba80)

![image](https://github.com/user-attachments/assets/12057910-e9db-4c64-8f55-f6edbc143320)



### Expected behavior

No consistence.

### To Reproduce

 _start:
   li t0, 0x70020000
    csrw pmpaddr0, t0
    li t1, 0x88
    csrw pmpcfg0, t1
    li t1, 0x1
    mret

### Environment

- XiangShan branch: master
- XiangShan commit id: 51aa1b60
- NEMU commit id: 1d750ea
- SPIKE commit id:


### Additional context

_No response_
