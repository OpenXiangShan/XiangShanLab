### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

### Case1: 
In M-mode, I constructed a test that jumps to a 32-bit NOP(0x00000013) instruction at PA **0x99C72FFE**. This instruction is intentionally placed across a physical page boundary: its first 16 bits are in page **0x99C72000-0x99C72FFF**, and its second 16 bits are in page **0x99C73000-0x99C73FFF**. 
Both pages are covered by a PMP TOR region with execute permission disabled (RWX = 000), so the jump should raise an Instruction Access Fault. In
  this case, mtval is expected to report the faulting instruction address, i.e. **0x99C72FFE**. However, XiangShan DUT reports **mtval = 0x99C73000**
  instead, which is the base address of the second page rather than the start address of the faulting instruction.

### Case2: 
Similarly, in M-mode, I constructed another test that jumps to a 32-bit NOP instruction at PA **0x9BA2EFFE**. This instruction is also
  intentionally placed across a physical page boundary: its first 16 bits are in page **0x9BA2E000-0x9BA2EFFF**, and its second 16 bits are in page
  **0x9BA2F000-0x9BA2FFFF**.
  However, unlike Case1, the two pages are covered by two different PMP TOR regions. The first page is configured as RWX = 011 (execute disabled),
  while the second page is configured as RWX = 101 (execute enabled). Therefore, the first half of the instruction lies in a non-executable PMP
  region, and the fetch should raise an Instruction Access Fault immediately when accessing the first 16-bit parcel, with **mtval = 0x9BA2EFFE** and **mcause = 0x00000001**. However. XiangShan DUT reports **mtval = 0x00000000** and **macuse = 0x00000002**




### Common pattern regarding both cases (inst fetches across two physical pages ): 
  - As long as the first halfword is fetchable and the second halfword is not fetchable, the DUT behaves basically correctly.
  - As long as the first halfword is not fetchable, the DUT starts to behave incorrectly.
      - If both halfwords are not fetchable, the DUT reports mtval as the base address of the next page instead of the actual instruction
    start address.
      - If the first halfword is not fetchable but the second halfword is fetchable, the DUT not only reports the wrong mtval, but also
    reports the wrong mcause as Illegal Instruction.


This issue is related to #4981, #5282, but with different reproducer and exception scenario.

### Expected behavior

XiangShan DUT should reports mtval **0x99C72FFE** instead of **0x99C73000** with the provided Case1;
It should reports mtval **0x9BA2EFFE** and mcause **0x00000001** with the provided Case2

### Environment

[bug-report-Case1.tar.gz](https://github.com/user-attachments/files/27158617/bug-report-Case1.tar.gz)
[bug-report-Case2.tar.gz](https://github.com/user-attachments/files/27158616/bug-report-Case2.tar.gz)


### To Reproduce

bug-report-Case*.tar.gz includes the .elf, .bin and .txt(asm) file as well as stdout.log of Case*

### Additional context

_No response_
