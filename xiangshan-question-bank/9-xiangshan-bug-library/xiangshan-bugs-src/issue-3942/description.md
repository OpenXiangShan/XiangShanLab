### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Hi there,

The RISC-V specification states that bits 11 to 15 of the `hvip` register should be zero. However, it has been observed that on the XiangShan, bits 13 to 15 of `hvip` can be written with 1. This behavior also exists in NEMU.

In the pre-refactoring CSR code, it appears that `hvipMask` was used to handle these fields, ensuring that the default read-only zero behavior was enforced. 
https://github.com/OpenXiangShan/XiangShan/blob/3956160f98fd99a914dc3ed63049918802875cfa/src/main/scala/xiangshan/backend/fu/CSR.scala#L593

Currently, this masking or validation logic seems to be missing, allowing writes to these reserved fields.

If this is an intentional design choice for XiangShan, please let me know. Any clarification on this behavior would be greatly appreciated.

**Screenshots**
![image](https://github.com/user-attachments/assets/4599d395-934f-473f-b780-39bfa329d219)


### Expected behavior

Bits 11 to 15 of the `hvip` register should be read-only zero.

![image](https://github.com/user-attachments/assets/54242b13-e3dd-4aa2-848d-c525d83b2bc5)


### To Reproduce

In S-mode, attempt to write all 1 to the `hvip` register.

### Environment

- ready-to-run's Spike commit id: [OpenXiangShan/ready-ro-run@3575e65](https://github.com/OpenXiangShan/ready-to-run/commit/3575e65) (Date: Fri Nov 22 17:06:53 2024 +0800) 
- XiangShan commit id: https://github.com/OpenXiangShan/XiangShan/commit/aecf601e803bfd2371667a3fb60bfcd83c333027 (Date: Tue Nov 19 16:16:35 2024 +0800)

### Additional context

_No response_
