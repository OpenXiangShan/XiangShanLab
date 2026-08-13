### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

我在仿真多hart的时候，发现四个hart有部分hart没有等到同步点而是提前结束了，然后找了一下问题，发现好像是伪指令seqz没有正确。

[TRANSLATION]

During the simulation of multiple harts, I noticed that some of the four harts ended prematurely without waiting for the synchronization point. Upon further investigation, it seems that the pseudo-instruction SEQZ was not executed correctly.

![cb94fe3f83541b1dd4a3c27afa0118cd](https://github.com/OpenXiangShan/XiangShan/assets/171529938/121f5c43-6ea0-4a1b-bb48-1dcfc7363bdd)

后面80003a8a这条分支指令的src0/1都是0， a4寄存器没有取反，应该是80003a66指令没有执行：

[TRANSLATION]

The source registers src0/1 for the branch instruction at 80003a8a are both zero, and register a4 has not been inverted, which suggests that the instruction at 80003a66 was not executed:

![efa400ce072deb1456f9c95dccc4eaf8](https://github.com/OpenXiangShan/XiangShan/assets/171529938/34f8fb9f-f597-4ae0-957a-1874e8dc0b28)

测试用例基于am->amtest, 问题发生点位于mpe.c的_barrier函数:

[TRANSLATION]

The test case is based on am->amtest, and the point of issue is located in the _barrier function of mpe.c:

![e8e52efecc8dc2701820e4c5fbd59b85](https://github.com/OpenXiangShan/XiangShan/assets/171529938/76874174-ae5d-4994-ba53-c1d882b45573)


### Expected behavior

应该等到4个hart都到达同步点后再打印结果（正确）：

[TRANSLATION]

The results should be printed only after all four harts have reached the synchronization point (correct):

![image](https://github.com/OpenXiangShan/XiangShan/assets/171529938/239b1586-b9e1-468c-8178-4c9d894e3617)
而不是部分hart提前结束（此错误）：
[TRANSLATION]
Instead of some harts ending prematurely (this is the error):
![image](https://github.com/OpenXiangShan/XiangShan/assets/171529938/9f8e3486-7f94-485a-9209-f605021e835e)


### To Reproduce

elf,bin,dasm
[amtest-riscv64-xs-dual.zip](https://github.com/user-attachments/files/15880420/amtest-riscv64-xs-dual.zip)


### Environment

- XiangShan branch:  master
- XiangShan commit id: commit 5adc4829471a0ea417766f3b0e57679ab3feb696
- NEMU commit id: 
- SPIKE commit id:


### Additional context

注：需要使用vcs仿真，verilator发现不了这个错误。

[TRANSLATION]

Note: VCS simulation is required, as Verilator cannot detect this error.
