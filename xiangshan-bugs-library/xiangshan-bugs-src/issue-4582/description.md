### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

The range of `siselect` register in Xiangshan is [0, 0xFF], but in NEMU is [0, 0xFFFFFFFFFFFFFFFF], the range is inconsistent.

An error occurred when writing 0x100 to the `siselect` register:
![Image](https://github.com/user-attachments/assets/85cf18b6-4453-45fc-a022-d144a84f911f)

### Expected behavior

According to Privileged [Manual](https://riscv.github.io/riscv-isa-manual/snapshot/privileged/) **Chapter 5.3. Supervisor-level CSRs**:
> The siselect register will support the value range 0..0xFFF at a minimum. 


### To Reproduce

```
csrw  siselect, zero
li t6, 0x100
csrw siselect, t6
csrr t6, siselect
nop
```

### Environment

- XiangShan branch: master
- XiangShan commit id: c01e75b55
- ready-to-run commit id: 75d8aeb


### Additional context

_No response_
