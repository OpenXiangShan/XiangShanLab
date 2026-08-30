### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

The test cases with instructions triggering a load access fault exception or store address misaligned exception impede the processor's ability to commit instructions when using NEMU for differential testing. 
![image](https://github.com/user-attachments/assets/9bec7628-876e-4bb4-9a24-5cc1ec68ee18)


### Expected behavior

I have tested the same test cases on Spike, which throws exceptions correctly.
![image](https://github.com/user-attachments/assets/abcea1b9-ebe7-47ce-8908-bf93419b1be0)


### To Reproduce

I have attached the source codes that trigger this issue. 
I use VCS for simulation. example execution command: `./build/simv +workload=noissue.bin +diff=./xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`

[xs_bug.zip](https://github.com/user-attachments/files/17613075/xs_bug.zip)


### Environment

I installed XiangShan from https://github.com/OpenXiangShan/xs-env `2bb84ce`
- XiangShan commit id: 49162c9
- NEMU commit id: a6a5f9b
- SPIKE commit id: 530af85 (tag v1.1.0)

### Additional context

_No response_
