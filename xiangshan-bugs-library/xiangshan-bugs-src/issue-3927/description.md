### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When executing a carefully crafted `fld` instruction in M-mode without PMP configuration, XiangShan shows a mismatch with the REFs NEMU and SPIKE.

The `rd` register of the `fld` instruction contains inconsistent values between XiangShan and the REF.
Additionally, in the REFs (NEMU and SPIKE), the `tp` register is unexpectedly modified to all zeros. This behavior appears abnormal and unrelated to the execution of the `fld` instruction.

**Screenshots**
![image](https://github.com/user-attachments/assets/9e6a3314-4443-486b-a636-979ca80cfc65)

The logs from Spike are also identical.

### Expected behavior

None

### To Reproduce

testcase: [test.zip](https://github.com/user-attachments/files/17900247/test.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id: aecf601e803bfd2371667a3fb60bfcd83c333027 (Date:   Tue Nov 19 16:16:35 2024 +0800)
- Ready-to-run commit id: 3575e65 (Date:   Fri Nov 22 17:06:53 2024 +0800)

### Additional context

_No response_
