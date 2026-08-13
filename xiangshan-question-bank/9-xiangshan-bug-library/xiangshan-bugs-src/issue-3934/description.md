### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Hi there,

When performing carefully crafted reads and writes(`csrrs`) to the `menvcfg` register in M-mode on XiangShan, the WPRI (Reserved Writes Preserve Values, Reads Ignore Values) field in `xstatus` is unexpectedly modified to 1. This behavior is not observed in either NEMU or SPIKE.

**Screenshots**
![image](https://github.com/user-attachments/assets/33a3948c-3160-468a-95a8-31bf93f1a631)


### Expected behavior

- The WPRI field in `xstatus` should remain unmodified during reads or writes to `menvcfg`, as specified by the RISC-V privileged architecture specification.
- Behavior should be consistent with other simulators such as NEMU and SPIKE.

### To Reproduce

testcase: [test.zip](https://github.com/user-attachments/files/17904432/test.zip)


### Environment

- ready-to-run's NEMU & SPIKE commit id: [OpenXiangShan/ready-ro-run@3575e65](https://github.com/OpenXiangShan/ready-to-run/commit/3575e65) (Date: Fri Nov 22 17:06:53 2024 +0800) 
- XiangShan commit id: https://github.com/OpenXiangShan/XiangShan/commit/aecf601e803bfd2371667a3fb60bfcd83c333027 (Date: Tue Nov 19 16:16:35 2024 +0800)

### Additional context

_No response_
