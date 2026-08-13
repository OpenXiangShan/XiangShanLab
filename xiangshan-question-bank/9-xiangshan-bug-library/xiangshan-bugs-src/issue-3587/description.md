### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

In M mode, we set the `mstatus.FS` value then read the `fflags`, we will see the `mstatus` and `sstatus` in Xiangshan changed.
`mstatus = 0x0000000a00006000` and `sstatus = 0x0000000200006000`
![image](https://github.com/user-attachments/assets/b6fd5e42-4415-4d6d-a687-1f4b48504f1d)


### Expected behavior

`mstatus = 0x0000000a00002000` and `sstatus = 0x0000000200002000`

### To Reproduce

The following zip file contains asm file, elf file, bin file and link file. If you want to assemble and link it, run:
```
riscv64-unknown-elf-as -o "$OBJECT_FILE" "$ASM_FILE"
riscv64-unknown-elf-ld -T "$LINKER_SCRIPT" -o "$NAME" "$OBJECT_FILE"
```
Run Xiangshan with: `./build/emu -i /path/to/readfflags.bin 2>/dev/null`
[read fflags.zip](https://github.com/user-attachments/files/17002122/read.fflags.zip)


### Environment

- XiangShan branch:  master
- XiangShan commit id: 8fae59bba
- NEMU commit id: 3033a69f
- SPIKE commit id: f7d0dba6


### Additional context

I built the env by [xs-env](https://github.com/OpenXiangShan/xs-env).
