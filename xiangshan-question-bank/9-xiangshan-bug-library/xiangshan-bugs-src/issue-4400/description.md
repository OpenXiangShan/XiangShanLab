### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

To eliminate the possibility that the `sscratch` register value is randomly initialized by XiangShan, I conducted the following test:
```
_start:
    csrr    sp, sscratch
end:    
    li a0, 0 
    .word 0x5006b
```
The test finished successfully without any differences, confirming that the initial `sscratch` register values are consistent between XiangShan and NEMU. **Therefore, random initialization can be ruled out.**
During the execution of a specific test case, an inconsistency was observed when reading the `sscratch` register.

![Image](https://github.com/user-attachments/assets/93ef2e34-c9a6-4cd5-bd91-1f1cec3d520e)

![Image](https://github.com/user-attachments/assets/cf8b3c5a-ee99-400d-aa2c-9ebfee0c8f89)

### Expected behavior

The `sp` value is expected to remain consistent.

### To Reproduce

[testcase.zip](https://github.com/user-attachments/files/19195850/testcase.zip)

### Environment

- XiangShan branch: master
- XiangShan commit id: d6b0a27
- ready-to-run commit id: 8c943ff
- SPIKE commit id:


### Additional context

_No response_
