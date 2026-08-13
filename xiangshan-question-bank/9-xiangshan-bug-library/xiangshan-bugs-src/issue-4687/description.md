### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When executing the instruction `csrrc t3, tdata2, t5` after setting `tselect = 0` , the register `t3` unexpectedly receives a non-zero value (`0x73b14afa4b7ed3e6`). However, there was no instruction that wrote to `tdata2` beforehand; it should have been zero. This is further confirmed by the NEMU reference model, which also read `tdata2` as 0x0.

<img width="656" alt="Image" src="https://github.com/user-attachments/assets/460018d1-4a99-48ba-a828-906dcc48c31f" />
<img width="537" alt="Image" src="https://github.com/user-attachments/assets/78a21ced-f916-4388-bc75-b784be40ddae" />

### Expected behavior

Since `tdata2` was never explicitly written, the result of reading it (`t3`) should be `0x0000000000000000`.

### To Reproduce

Here is the testcase.
[test.zip](https://github.com/user-attachments/files/20192074/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 0c97e1df8323fdab545e7d336867b444745299bd
- NEMU commit id: 16e9c675a07886f46cbbd48cf69e2e13eb919f9f


### Additional context

_No response_
