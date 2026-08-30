### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

There are two test cases, both of which use the `addi` instruction to update register values. NEMU, however, did not execute the addition operation correctly. 

Case A:
![Image](https://github.com/user-attachments/assets/adcf02cf-a72d-4b4e-83f8-15b7d80aa950)
![Image](https://github.com/user-attachments/assets/a9534c4e-ecfa-4ccc-acb2-4415ae569b05)
![Image](https://github.com/user-attachments/assets/d572b586-1b67-481f-92ac-9df16994fea1)

Case B:
![Image](https://github.com/user-attachments/assets/839a4504-ea5d-4b22-b252-45ea99b64e9a)
![Image](https://github.com/user-attachments/assets/ce480d3c-42cd-4f4b-8921-67c7710e1b36)

### Expected behavior

The added value should be same.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19196263/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27
- NEMU commit id: 2235c04
- ready-to-run commit id: 8c943ff


### Additional context

I wonder if this could be related to NEMU's slower execution speed?
