### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

The `sc.w` instruction should fail because the address used for` sc.w` has been changed after the `lr.w` reservation. 

![Image](https://github.com/user-attachments/assets/10e60dd8-c788-4da4-95b5-d10705a6aaf9)
![Image](https://github.com/user-attachments/assets/6bbcb018-9121-4efe-84e9-7a1a9d548e12)

This indicates that NEMU incorrectly treats the `sc.w` as successful, even though the reserved address has changed.

### Expected behavior

According to the RISC-V specification, if the address does not match the reserved address, `sc.w` must fail and write a nonzero value to the destination register `(t2)`, which should be 0x1 in NEMU.

### To Reproduce

The reference model is `/home/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`.
[test.zip](https://github.com/user-attachments/files/19927558/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: e6d84857e
- ready-to-run: 638a01e


### Additional context

**Before start**:
I have searched the previous issues and **find something** relevant: #4577.
