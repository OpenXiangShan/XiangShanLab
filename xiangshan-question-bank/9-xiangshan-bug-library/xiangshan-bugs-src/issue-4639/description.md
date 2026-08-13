### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

According to the information, it seems that the mismatch occurred at pc = 0x0000132e, but the `mtval` value indicates that the problem is at `pc = 0x0000133e`. If the issue is indeed at `pc = 0x0000133e` and `t5 = 0x0002`, then the `mtval` value from NEMU is correct. However, if the issue is at `pc = 0x0000132e`, then it’s unclear what the problem is.
Maybe it’s an issue with the diff test?
![Image](https://github.com/user-attachments/assets/77b05b16-9dfd-49bd-a98a-bb999b7fc467)

![Image](https://github.com/user-attachments/assets/664b78b2-825c-459f-85bc-33a91b8fa0e7)

### Expected behavior

Be consistent

### To Reproduce

The reference model is `/home/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`.
[test.zip](https://github.com/user-attachments/files/19927955/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: e6d84857e
- ready-to-run: 638a01e


### Additional context

Please reedit the issue title when you confirm what the problem is. Thanks.
