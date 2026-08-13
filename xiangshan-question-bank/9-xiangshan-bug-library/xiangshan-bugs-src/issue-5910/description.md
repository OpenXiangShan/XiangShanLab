### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

This issue is closely related to #5872, seems that #5872 hasn't been completely solved;

After #5872, the xiangshan cpu can correctly handles most of the EX_IAF across 2 physical pages but with one exception, which triggers diff-test failure when all of the following conditions hold:
- The fetch address is in mmio area 0x1000_0000 ~ 0x1fff_ffff.
- The instruction starts at `...FFE`, so the first 16 bits are in page N and the second 16 bits are in page N+1.
- PMP is configured so that page N has `X=1` and page N+1 has `X=0`.
- The test runs in M-mode with locked PMP entries.

Under this circumstance, xiangshan cpu reports the mepc and mtval to be the start addr of page N+1, instead of the actual addr of pc (namely ...FFE);


This behavior does **not** reproduce when:
- the first page also has `X=0`, or
- the same cross-page PMP setting is moved outside the `0x1000_0000 ~ 0x1fff_ffff` window.

### Expected behavior

With provided condition, xiangshan cpu should report mepc and mtval to be ...FFE, instead of ...000 (the start addr of  next page)

### Environment

[bug-report-20250507.tar.gz](https://github.com/user-attachments/files/27479056/bug-report-20250507.tar.gz)


### To Reproduce

bug-report-20250507.tar.gz includes all the related files

### Additional context

_No response_
