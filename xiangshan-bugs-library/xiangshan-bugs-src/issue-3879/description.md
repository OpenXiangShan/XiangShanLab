### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集相关的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have searched the previous discussions and did not find anything relevant. 我已经搜索过之前的 discussions，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the question

When testing Xiangshan using NEMU as a reference model, many of my test cases showed inconsistencies similar to the following:

![image](https://github.com/user-attachments/assets/8740e67f-08d4-4df1-aa70-e866ad6e3d6d)
![image](https://github.com/user-attachments/assets/85351b0d-6a3a-4547-80c6-7d5bf1b61f7a)


At first I thought it was a NEMU-related issue. But I tested it with the following assembly instructions:

```
.section .text
.globl _start
_start:
   
    li     t0, 0x8000000a00100a00     
    csrw    mstatus, t0 
    .word 0x9efd

```
The following is a screenshot of the log information：

![image](https://github.com/user-attachments/assets/d956797b-8aea-42d8-82c3-a234be7875ac)

![image](https://github.com/user-attachments/assets/0586c2d6-5a29-4386-9462-535e458e5cf0)


When I use spike for debugging, .word 0x9efd corresponds to the unknown instruction：
![image](https://github.com/user-attachments/assets/cafe55f7-c0d6-4815-bdeb-fff6ea3c0c20)


### Version Information
XiangShan :commit dd02bc3f0e1adcf5fbaee614420772a94ccc0226 (HEAD -> master, origin/master, origin/HEAD)
ready-to-run: commit a449a38534ec8330842ad5e975b872686b421ebc (HEAD, origin/master, origin/HEAD)
nemu: 39f546c42275cb9bc2f74170e7ff6486c98ef4c9
