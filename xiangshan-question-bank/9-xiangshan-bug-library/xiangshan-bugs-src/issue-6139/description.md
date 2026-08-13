### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In EX_SAM_SAF_m (namely store address misalignment and store access fault simultaneously occur in M-mode) batch test (up to thousands of test cases), 158 cases mismatch between REF (NEMU) and DUT (Xiangshan) ony on `mtval/tval`. All such mismatches are confined to pure-store cases under: `mmio + acrossPage + acrossPMP`;

Observed Behavior
- ref:
  `mcause = 7` (Store/AMO Access Fault)
  `mtval  =` the faulting address in the second half of the split store, for instance, **0x000000001ab25000**
- dut:
  `mcause = 7` (same as ref)
  `tval   =` the original store start address, for instance, **0x000000001ab24ffb**

Representative cases include:
- `sd/sw/sh`
- `fsd/fsw/fsh`
- `hsv.d / hsv.w / hsv.h`
- `c.sd / c.sw / c.sh / c.sdsp / c.swsp / c.fsd / c.fsdsp`

Test Construction
- All mismatching cases are pure stores with non-zero offset, so the access itself is misaligned and crosses page boundary.
- All mismatching cases are in MMIO space.
- All mismatching cases cross two PMP TOR regions.
- PMP write-permission pattern is consistent across all 158 failing pure-store cases, with first PMP region: `W = 1`
  and second PMP region `W = 0`; 
- In all cases, the PMA of targeted address is configured to be `R = 1, W = 1 `


This might not be a bug, but indicates the mismatching behavior between Xiangshan and NEMU under certain circumstances. 

### Expected behavior

Xiangshan and NEMU agrees on the `mtval` value in such cases. 

### Environment

[bug-report1.tar.gz](https://github.com/user-attachments/files/29339319/bug-report1.tar.gz)


### To Reproduce

Please refer to the attachment `bug-report1.tar.gz`

### Additional context

_No response_
