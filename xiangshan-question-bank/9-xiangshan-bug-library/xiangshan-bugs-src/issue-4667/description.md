### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When executing an unaligned `amomin.d` (atomic doubleword minimum) instruction, XiangShan and Spike report different `mcause` values. The address accessed is intentionally unaligned. However, the two simulators disagree on which exception should take priority:

- **Spike** reports: `mcause = 7` → **Store/AMO access fault**
- **XiangShan** reports: `mcause = 6` → **Store/AMO address misaligned**

<img width="637" alt="Image" src="https://github.com/user-attachments/assets/b62bede5-77ea-4cb6-b7ef-95ff9f06023c" />
<img width="554" alt="Image" src="https://github.com/user-attachments/assets/1bc6cd4e-19de-4e85-b999-8ba133125f31" />



### Expected behavior

As Spike reflects the correct trap priority: **access fault > misaligned address**. XiangShan should report `mcause = 7` (**Store/AMO access fault**) instead of `6`.

### To Reproduce

Here is the source code and binary file.
[test.zip](https://github.com/user-attachments/files/20091536/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 7e564dbbfb630d3142a5c023ab16922e0497be9d
- SPIKE (ready-to-run/riscv64-spike-so) commit id: c73ba81be39b21d4d11b4e024b1074c9e9001fa2

### Additional context

_No response_
