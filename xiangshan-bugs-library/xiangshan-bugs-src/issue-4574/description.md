### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

Write value to `tdata1` register, mismatch occurred:

![Image](https://github.com/user-attachments/assets/3681b635-c205-4db9-abf4-a6ccbc3600a3)

![Image](https://github.com/user-attachments/assets/6291b477-0c8d-4f83-8375-7340d6c9a967)

When the reference model is `./ready-to-run/riscv64-spike-so`, the `tdata1 = 0x600000000120001e`.
When the reference model is `./ready-to-run/riscv64-nemu-interpreter-so`, the `tdata1 = 0x600000000120001e`.
For Xiangshan: `tdata1 = 0x600000000100001e`

### Expected behavior

`tdata1` should update correctly.

### To Reproduce

```
csrw tdata1, zero
li t0, 0x600ff000012ff01e
csrw tdata1, t0 
csrr t1, tdata1 
```

### Environment

- XiangShan branch: master
- XiangShan commit id: c01e75b55
- ready-to-run commit id: 75d8aeb


### Additional context

_No response_
