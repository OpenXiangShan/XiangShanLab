### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When executing `fadd.h ft0, fa0, fa0` instruction, the value of fa0 is `0xffffffffffff8000` (**`-0.0`** in half precision format), and the results of Xiangshan and nemu are different.

### Expected behavior

Log information：
![image](https://github.com/user-attachments/assets/7303288c-59a3-40cc-8f26-b3c5deafff9f)
![image](https://github.com/user-attachments/assets/012c0e7c-24e7-48d4-aeb1-1650be73838c)
![image](https://github.com/user-attachments/assets/51c9855f-c336-4337-9d55-51582f0c505c)

The log information of spike is also shown above

### To Reproduce

Initialize fa0: 0xffffffffffff8000
Execute `fadd.h ft0, fa0, fa0`

### Environment

- XiangShan branch: 
- XiangShan commit id: b00d5822032eadad2744fcfc0a8c03fa011ff81c (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: 
- SPIKE commit id:
ready-to-run:457f091898b2bcd26c8f6e983f3df174f990af43 (HEAD -> master, origin/master, origin/HEAD)

### Additional context

_No response_
