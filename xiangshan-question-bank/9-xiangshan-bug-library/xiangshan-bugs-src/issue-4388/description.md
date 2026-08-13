### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

![Image](https://github.com/user-attachments/assets/d269ff33-3a4e-433d-a678-ec8156312462)
`s2 = 0xfffffffffffffbb5 `

![Image](https://github.com/user-attachments/assets/2f09492b-331f-4ae3-9fd9-698f518f95fa)

The difference in the mepc and mtval values between NEMU and XiangShan lies in the upper 16 bits.

![Image](https://github.com/user-attachments/assets/ce72109e-d3e5-48e5-8ee0-ce48a0cbd993)

### Expected behavior

The values of these two registers should be consistent.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19158658/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27ff
- ready-to-run commit id: 8c943ff
- SPIKE commit id:


### Additional context

_No response_
