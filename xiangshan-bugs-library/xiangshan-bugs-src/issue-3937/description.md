### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Hi there,

I have identified a bug in the XiangShan where the `mip.STIP` bit is not set when the `stimecmp` register is set to `0` (a value less than the `time`). 

In my testing, I explicitly performed additional delay and differential checks, but `mip.STIP` in XiangShan still failed to be set to `1`. This behavior violates the RISC-V specification.

**Error log or Screenshots**
![image](https://github.com/user-attachments/assets/86c1568b-afa5-4820-8230-f384c8771ad4)


### Expected behavior

According to the RISC-V specification:

> A supervisor timer interrupt becomes pending - as reflected in the STIP bit in the mip and sip registers - whenever time contains a value greater than or equal to stimecmp, treating the values as unsigned integers. Writes to stimecmp are guaranteed to be reflected in STIP eventually, but not necessarily immediately. The interrupt remains posted until stimecmp becomes greater than time - typically as a result of writing stimecmp. The interrupt will be taken based on the standard interrupt enable and delegation rules.

### To Reproduce

testcase: [test.zip](https://github.com/user-attachments/files/17912569/test.zip)


### Environment

- ready-to-run's Spike commit id: [OpenXiangShan/ready-ro-run@3575e65](https://github.com/OpenXiangShan/ready-to-run/commit/3575e65) (Date: Fri Nov 22 17:06:53 2024 +0800) 
- XiangShan commit id: https://github.com/OpenXiangShan/XiangShan/commit/aecf601e803bfd2371667a3fb60bfcd83c333027 (Date: Tue Nov 19 16:16:35 2024 +0800)

### Additional context

**Note:** This behavior was primarily identified through differential testing with Spike. However, NEMU appears to have a similar issue, though the conditions to trigger the bug are different. A separate issue with detailed information will be submitted to the NEMU repository.
