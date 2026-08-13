### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When executing a `csrr s0, pmpcfg1` instruction in **U-mode**, an **illegal instruction exception** is correctly triggered by both **Spike** and **XiangShan**. However, there is a mismatch in the value stored in the **`mtval`** CSR:

- **Spike** stores the faulting instruction's binary (`0x3a102473`) in `mtval`.
- **XiangShan** leaves `mtval` as `0x00000000`.


The log of NEMU, which implies the **illegal instruction exception** is caused by `csrr s0, pmpcfg1` instruction in **U-mode**
<img width="649" alt="Image" src="https://github.com/user-attachments/assets/32eb8cc3-34d0-4b5b-b3b4-75dc26b8079d" />

The log of XiangShan
<img width="668" alt="Image" src="https://github.com/user-attachments/assets/f063bb73-5ee4-46a5-a70e-8434472e7465" />
<img width="579" alt="Image" src="https://github.com/user-attachments/assets/070bdd52-0389-4e32-a9fd-c5eec7adc032" />


### Expected behavior

The `mtval` should contain the **actual instruction encoding**(in this case is `0x3a102473`) when the cause is an `illegal instruction`.


### To Reproduce

Here is the source code and binary file.
[test.zip](https://github.com/user-attachments/files/20092049/test.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: 7e564dbbfb630d3142a5c023ab16922e0497be9d
- NEMU commit id: 4a24b77a61505e34745667b1ad712a817b090cf8
- SPIKE (ready-to-run/riscv64-spike-so) commit id: c73ba81be39b21d4d11b4e024b1074c9e9001fa2


### Additional context

_No response_
