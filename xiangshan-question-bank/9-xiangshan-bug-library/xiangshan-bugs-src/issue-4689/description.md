### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

In the test case, after executing `mret`, the code jumps into the `_halt` function(provided by AM). However, the instruction `0005006b` did not cause XiangShan to exit to the simulation environment as expected. Instead, the instruction was executed, and even the following instruction was committed, until the simulation stopped due to a mismatch.

By contrast, NEMU exits successfully at the same point, despite hitting a bad trap:

<img width="686" alt="NEMU exit" src="https://github.com/user-attachments/assets/67a6af86-440f-4e62-b189-bed6c01bacdc" />

XiangShan appears to ignore `0005006b`:

<img width="617" alt="XiangShan ignore" src="https://github.com/user-attachments/assets/39381bc0-192a-4a02-8f5b-d1f171fd55f8" />

The simulation continues and only stops when a mismatch is finally triggered:

<img width="545" alt="Stopped at mismatch" src="https://github.com/user-attachments/assets/f7c1651a-7095-4352-bb2a-452f382c781e" />


### Expected behavior

Upon executing the instruction `0005006b` inside the `_halt` function, the processor should immediately trigger a simulation exit.

### To Reproduce

Here is the testcase.
[test.zip](https://github.com/user-attachments/files/20201360/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 0c97e1df8323fdab545e7d336867b444745299bd
- NEMU commit id: 16e9c675a07886f46cbbd48cf69e2e13eb919f9f

### Additional context

_No response_
