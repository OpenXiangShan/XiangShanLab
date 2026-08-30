### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

I noticed a possible mismatch with the spec behavior:

spec：

> When V=1, the VS CSRs substitute for the corresponding supervisor CSRs, taking over all functions of the usual supervisor CSRs except as specified otherwise. Instructions that normally read or modify a supervisor CSR shall instead access the corresponding VS CSR. When V=1, an attempt to read or write a VS CSR directly by its own separate CSR address causes a virtual-instruction exception. **(Attempts from U-mode cause an illegal-instruction exception as usual.)** The VS CSRs can be accessed as themselves only from M-mode or HS-mode.


When V=1, direct access to a VS CSR by its own CSR address should raise a virtual-instruction exception, except U-mode attempts, which should raise illegal-instruction.

In current XiangShan code, VU (U-mode with V=1) direct access to VS CSR (for example vsstatus, 0x200) appears to be classified as EX_VI instead of EX_II.

 I'm not sure whether the "U-mode" mentioned in the specification here includes VU-mode in the context of the XiangShan implementation.

Code path I checked:
isVirtual is true for both VS and VU:
[CSRBundles.scala (line 79)]
privilege classification sends virtual non-M illegal accesses to privilege_EX_VI:
[CSRPermitModule.scala (line 379)]
this is propagated to final CSR exception output as EX_VI:
[NewCSR.scala (line 1096)]
[CSR.scala (line 251)]

### Expected behavior

VS direct access to VS CSR own address -> EX_VI
VU direct access to VS CSR own address -> EX_II (illegal-instruction)

### Environment

XiangShan branch:master
XiangShan config:DefaultConfig

### To Reproduce

N/A —sorry for no minimal reproducer yet. The issue was found via code inspection.

### Additional context

_No response_
