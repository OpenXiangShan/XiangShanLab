### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When executing `.word 0x6781` instruction to Xiangshan, `riscv64-spike-so` has an exception, but Xiangshan does not. I think this is a decoding related problem .

### Expected behavior

When testing with `riscv64-spike-so`
![image](https://github.com/user-attachments/assets/e80d47d3-7791-4307-afc6-0ad60ea12a34)
![image](https://github.com/user-attachments/assets/7b054623-0fd7-44bd-87f2-b79fcf15042b)



When I debug with spike, the .word `0x6781 `instruction corresponds to `c.lui a5, 0x0`
![image](https://github.com/user-attachments/assets/4c93ccea-8a87-4cf0-8347-2501118df3fb)

In addition, I don't know if `nemu` has such a problem, because when I use nemu as a reference model, the following log message appears:
![image](https://github.com/user-attachments/assets/6e9c138c-2ef0-4c0d-8bb9-edb0aabf2788)


### To Reproduce

```
.section .text
.globl _start
_start:
   
     li     t0, 0x8000000a0014a00     
     csrw    mstatus, t0
     .word 0x6781
```
    
 
 

### Environment

- XiangShan branch: 
- XiangShan commit id: commit f12520cf2ac73390a4e7c9016fe7a7335236ecc5 (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: commit: ff37164781e23336ea7fc70f7bf7ea006ee9fbbc
- SPIKE commit id:
- ready-to-run:commit 3575e659bf3a1ed0e0de74bcef848b9be666725b (HEAD, origin/master, origin/HEAD)


### Additional context

_No response_
