### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

A mismatch has been observed in the `sc.w` instruction following the `lr.w`. The behavior differs between NEMU and XiangShan:

- **In NEMU**, `SC.W` writes `1` to `rd` on failure, indicating an unspecified error.
- **In XiangShan**, `SC.W` does not write a nonzero value to `rd` when failure occurs, instead assuming the operation was successful.

This mismatch needs to be addressed, as it affects the consistency of atomic memory operations between NEMU and XiangShan.
<img width="417" alt="Image" src="https://github.com/user-attachments/assets/7d96e814-f48a-4474-8226-c03fe833fdfd" />
<img width="344" alt="Image" src="https://github.com/user-attachments/assets/5bdf3c74-50dc-428d-8f04-e715335275ce" />

### Expected behavior

`SC.W` should write a nonzero value (typically `1`) to `rd` on failure in both NEMU and XiangShan for consistency.

### To Reproduce

Here is the source code, binary file:
[test.zip](https://github.com/user-attachments/files/19784049/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: c01e75b55fbe706c21954429f2ec968cbc61a2cc
- NEMU commit id: 738ae334edc62237c3d4b49c722882ee1b7324fc


### Additional context

_No response_
