### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When we change the `mstatus` by `csrc` instr, then when execute `fle.d`  instr, a bug occurs:
<img width="820" alt="image" src="https://github.com/user-attachments/assets/30204555-dd89-44cb-9021-e41d9df93693">
<img width="695" alt="image" src="https://github.com/user-attachments/assets/9bf94d00-2bcc-4a34-8263-dbf2fa0e84a1">

**Note:** there are some instructions between `csrc` and `fle.d`, this is the reason why I think this bug is related to `fle.d` instr.

### Expected behavior

When we execute `fle.d`, `mstatus = 0x0000000a00002000`, `sstatus = 0x0000000200002000`.

### To Reproduce

Here is the instr code and bin/elf file to test.
[runxs_files.zip](https://github.com/user-attachments/files/17322806/runxs_files.zip)

Here is the complete log.
[fle.d.log](https://github.com/user-attachments/files/17322896/fle.d.log)


### Environment

- xs-env: 2bb84ce
- XiangShan branch: master
- XiangShan commit id: 8bb30a570
- NEMU commit id: 821ea961
- SPIKE commit id: 1.1.1-dev


### Additional context

Xiangshan make: ` make emu CONFIG=DefaultConfig -j48`.
NEMU make: `make clean; make riscv64-xs-ref_defconfig; make -j48`
