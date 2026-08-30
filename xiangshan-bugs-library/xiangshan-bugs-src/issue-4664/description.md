### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When comparing XiangShan and Spike, there is a mismatch in the `mcause` after executing an `lw` instructions. Spike reports a **load access fault** (`mcause = 5`), while XiangShan reports an **instruction page fault** (`mcause = 13`). It is unclear why XiangShan triggers an instruction page fault at this point.

<img width="620" alt="Image" src="https://github.com/user-attachments/assets/f7baf88d-2b56-46fb-813a-96b510c18f6d" />

<img width="622" alt="Image" src="https://github.com/user-attachments/assets/6ae364a1-303e-4057-aea9-5112f7f4f2a9" />

### Expected behavior

The exception caused by `lw` should match Spike’s behavior, which reports a **load access fault**, since the instruction was fetched successfully and lw `t0 = 0`.


### To Reproduce

Here is the source code and binary file.
[test.zip](https://github.com/user-attachments/files/20089204/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 7e564dbbfb630d3142a5c023ab16922e0497be9d
- SPIKE (ready-to-run/riscv64-spike-so) commit id: c73ba81be39b21d4d11b4e024b1074c9e9001fa2


### Additional context

_No response_
