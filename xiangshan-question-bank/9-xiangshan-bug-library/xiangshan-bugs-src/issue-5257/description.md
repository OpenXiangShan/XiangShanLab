### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

the core raises an Instruction Page Fault (mcause=0xC) when executing an instruction from a supervisor-only executable page, even though the page table entries (L2/L1/L0) form a fully valid Sv39 translation chain and grant correct execute permissions for S-mode.

<img width="773" height="271" alt="Image" src="https://github.com/user-attachments/assets/25f5d3a2-602a-4398-b822-c9dcfe339fb3" />

### Expected behavior

According to the RISC-V Sv39 specification, S-mode executing at a canonical VA mapped by a valid leaf PTE with X=1 and U=0 must be allowed to fetch instructions.
No page fault should occur.  
（but, XiangShan consistently reports an Instruction Page Fault(mcause=0xc ) at PC=0x8000201c immediately after enabling Sv39 and executing sfence.vma, despite the page tables being valid and granting execute permission.）

### To Reproduce

[archTest.zip](https://github.com/user-attachments/files/23757105/archTest.zip)

The minimal workload (source code and binaries) is all included in the following compressed file package.

### Environment

- XiangShan branch: (HEAD detached at 167da6a8f)
- XiangShan commit id: 167da6a8fc4130c82f3c6e5c3990fa54f958ac7f
- XiangShan config: KunminghuV2Config
- NEMU commit id:
- SPIKE commit id:


### Additional context

_No response_
