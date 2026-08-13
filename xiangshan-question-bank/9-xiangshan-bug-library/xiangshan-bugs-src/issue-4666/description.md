### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

After executing the `sret` instruction in Supervisor mode, there is a mismatch between Spike and XiangShan regarding the `SIE` bit in `mstatus` and `sstatus`. Spike clears `SIE` (bit 1), while XiangShan sets it. This discrepancy may affect interrupt behavior and privilege mode consistency.

<img width="626" alt="Image" src="https://github.com/user-attachments/assets/1099783e-5e34-48e9-81a5-a1a3880bb009" />
<img width="552" alt="Image" src="https://github.com/user-attachments/assets/250357d5-4ddb-453f-80fe-f9f2884ec114" />

### Expected behavior

According to the RISC-V Privileged Spec, on executing `sret`, the `SIE` bit should be restored from `SPIE`, and `SPIE` should be cleared. Both Spike and XiangShan should behave consistently under this rule. The observed mismatch suggests that XiangShan incorrectly enables `SIE`.

### To Reproduce

Here is the source code and binary file.
[test.zip](https://github.com/user-attachments/files/20089759/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 7e564dbbfb630d3142a5c023ab16922e0497be9d
- SPIKE (ready-to-run/riscv64-spike-so) commit id: c73ba81be39b21d4d11b4e024b1074c9e9001fa2


### Additional context

_No response_
