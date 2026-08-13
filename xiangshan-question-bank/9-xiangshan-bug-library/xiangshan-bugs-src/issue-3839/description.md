### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When the fs field is 0, executing floating-point instructions such as `flh` will not cause an illegal instruction exception. This is inconsistent with the behavior of nemu and spike.

The assembly code is as follows:

```
.section .text
.globl _start
_start:
    li     t0, 0x8000000a00100a00     #FS: 00
    csrw    mstatus, t0 
    flh     ft0, 9(sp) 
```

1. When using nemu as a reference model：
![image](https://github.com/user-attachments/assets/aa9fccc4-30e6-4f91-9bef-5f43e5be9f55)
![image](https://github.com/user-attachments/assets/4f5a1d33-67c2-4dde-86d4-d902a3c2f378)

2. When using riscv64-spike-so as a reference model:
![image](https://github.com/user-attachments/assets/d52b3c55-cf8d-4126-ae8d-be8663cd4752)
![image](https://github.com/user-attachments/assets/4f3a7651-66fc-4c39-8359-36e084f4d5ae)

Please refer to: https://github.com/OpenXiangShan/NEMU/issues/641


### Expected behavior

FS is 0, xiangshan should also trigger an illegal instruction exception. After testing, there are also such instructions as `fsh`, etc.

### To Reproduce

```
.section .text
.globl _start
_start:
    li     t0, 0x8000000a00100a00     #FS: 00
    csrw    mstatus, t0 
    flh     ft0, 9(sp) 
```

### Environment

- XiangShan branch: 
- XiangShan commit id: 7af39ad2ddb1305b2c4ddf4c3a9663a7c3615fa6 (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: 861f8d3187fa8a58e14d2394d56b28f1f434adc2
- SPIKE commit id:
- ready-to-run:commit c09f524c6ecb43cd047b226e2da4aad7edd05702 (HEAD, origin/master, origin/HEAD)


### Additional context

_No response_
