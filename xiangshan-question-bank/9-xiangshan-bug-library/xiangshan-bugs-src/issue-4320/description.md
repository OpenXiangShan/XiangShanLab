### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

In the IFU, the FrontendTrigger is used to implement hardware PC breakpoints. Currently, MatchType supports three modes:
- 0: Equal to
- 2: Greater than or equal to
- 3: Less than

However, setting a breakpoint in "less than" mode can lead to incorrect PC triggering.

![Image](https://github.com/user-attachments/assets/45c28297-5ad8-4a41-9eae-e4f4b09180d3)

As shown in the figure, I set a PC breakpoint at 0XDEADBEE6 on hardware breakpoint 0, with the trigger condition set to "less than." A few cycles later, I updated all io_pc values, starting from 0xDEADBEE0 and incrementing by 2. Under normal circumstances, io_pc0 and **io_pc1**, being less than **0XDEADBEE6**, should trigger the breakpoint, and this should be reflected in the corresponding io_triggered ports. The other PC ports should not trigger the breakpoint. However, unexpectedly, io_pc15 (0xDEADBF00) also triggered the breakpoint.

### Expected behavior

io_pc15 port should not trigger the breakpoint

### To Reproduce

see figure
You can also use #XS-MLVP/UnityChipForXiangShan/pull/69 to set up the test environment.

### Environment

- XiangShan branch: 
- XiangShan commit id: 
- NEMU commit id: 
- SPIKE commit id:


### Additional context

_No response_
