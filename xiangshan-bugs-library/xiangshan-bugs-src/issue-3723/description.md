### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

After three operations on the `mstatus` register, using `csrw`, `csrrw`, and `csrrs` to modify `mstatus`, the use of the `csrc` instruction to clear certain bits did not work as expected.  Detail please check asm code in the zip file below.
Here are screenshots:
![image](https://github.com/user-attachments/assets/2f2b9c87-cee4-4412-b413-1958d841cf8e)

![image](https://github.com/user-attachments/assets/bf7865ea-ffab-48a2-8930-2b49e26f547f)


### Expected behavior

Since I am not aware of the initial value of `mstatus` in Xiangshan and NEMU, I cannot infer what the expected value should be. I ran spike got different value, so I am not sure whether NEMU is correct.
![image](https://github.com/user-attachments/assets/f8b38f45-6a5a-424b-9ba5-7d45e93154e8)


### To Reproduce

[csrc.zip](https://github.com/user-attachments/files/17349559/csrc.zip)


### Environment

- XiangShan branch: master
- XiangShan commit id: 8bb30a570
- NEMU commit id: 821ea961
- SPIKE commit id: 1.1.1-dev


### Additional context

I suspect that the issue #3709 with the `fle.d` instruction may be related to the `csrr` instruction.
