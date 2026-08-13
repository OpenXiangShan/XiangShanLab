#### Before start
PLEASE MAKE SURE you have done these: 
- [x] (Select what you have done like this)
- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question.
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest).
- [x] I have searched the previous issues and did not find anything relevant.
- [x] I have reviewed the commit messages from the relevant commit history.

#### Describe the bug
In certain special cases, XiangShan exhibits incorrect behavior in handling rounding modes, including RNE (Round to Nearest, ties to Even), RUP (Round Up), RDN (Round Down), and RMM (Round to Nearest, ties to Max Magnitude). An example of this is when executing the instruction `fmsub.s fa6, fa2, ft7, ft6` with the following parameters:

```
fa2=0x91d2805f
ft7=0x8069010b
ft6=0x7f7fffff    # notably, this represents the largest positive number, not NaN
```
The expected correct result should be `fa6=0xff7fffff`, however, in XiangShan, the result is observed as `fa6=0xff7ffffe`, indicating a discrepancy. It's noteworthy that for RTZ (Round towards Zero) rounding, XiangShan's behavior is correct, but errors are present in the other rounding modes mentioned.

#### Screenshots
![image](https://github.com/OpenXiangShan/XiangShan/assets/62980522/32df3767-a41d-4d3d-9adc-d25d28d29c15)

For `RNE`, `RUP`, `RDN`, and `RMM`, the result is the same.
#### Expected behavior
The expected behavior was to receive `fa6=0xff7fffff` as the result of the aforementioned operation, following the IEEE 754 standard for floating-point arithmetic.

#### To Reproduce
Steps to reproduce the behavior:
1. Execute the fmsub.s fa6, fa2, ft7, ft6 instruction with the parameters:
        fa2=0x91d2805f
        ft7=0x8069010b
        ft6=0x7f7fffff
2. Observe the resulting value of fa6.

I have attached the screenshot of the reproduction. Please check the attachment.
#### Environment (please complete the following information):
 - XiangShan branch: [* main]
 - XiangShan commit id: [3b1a683bf8b2a904a7e4d56372b2cec6ba0ae66c]

 - SPIKE commit id: [f8b2e39258d6de74652203a5ca357bb918e3ed53]

#### Additional context
During testing, I found that the aforementioned inconsistency only occurs with specific instruction sequences (for example, it requires inserting `nop` or other irrelevant instructions before and after). I sincerely hope this piece of information can assist you in more rapidly identifying the root cause of the issue.
[testcase.zip](https://github.com/OpenXiangShan/XiangShan/files/14546059/testcase.zip)
