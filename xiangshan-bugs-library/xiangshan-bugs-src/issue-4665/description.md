### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

There is a mismatch between Spike and XiangShan when executing a sequence of instructions that includes CSR writes to `pmpcfg0`. Spike correctly triggers an **Instruction Access Fault** (`mcause = 1`), while XiangShan does not raise any exception and continues execution.

<img width="677" alt="Image" src="https://github.com/user-attachments/assets/74394fb2-de0e-4d3d-a715-b2de8bc36484" />
<img width="548" alt="Image" src="https://github.com/user-attachments/assets/8e800590-e8f2-49c3-bf8d-4b1ef74e5946" />

### Expected behavior

Both simulators should raise an **Instruction Access Fault** if the next instruction fetch after PMP configuration is from an unauthorized region. XiangShan appears to incorrectly allow the instruction fetch to proceed.


### To Reproduce

Here is the source code and binary file.
[test.zip](https://github.com/user-attachments/files/20089527/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 7e564dbbfb630d3142a5c023ab16922e0497be9d
- SPIKE (ready-to-run/riscv64-spike-so) commit id: c73ba81be39b21d4d11b4e024b1074c9e9001fa2


### Additional context

_No response_
