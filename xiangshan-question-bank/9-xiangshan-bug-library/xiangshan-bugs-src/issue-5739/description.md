### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`vl` is not visible to an immediately following `csrr vl` after `vsetvli`.

The minimal test case:
```asm
li t6, 0x600
csrs mstatus, t6
li a0, 16
vsetvli t1, a0, e8, m1, ta, ma
csrr t2, vl
```
Here, vsetvli should both:
- return the computed vector length in rd (t1)
- update CSR vl, so that the immediately following csrr vl reads back the same value

Therefore, `t2` should be `16`
However, difftest on XiangShan reports that `t2` becomes 0, which disagrees with Spike.

Spike Log:
```
0x0000000080000016 vsetvli t1, a0, e8, m1, ta, ma
0x000000008000001a csrr    t2, vl
0x0000000080000020 bne     t2, t3, pc + 6
>>>>  pass
```

Xiangshan Difftest:
```
[09] commit pc 0000000080000016 inst 0c057357 wen 1 dst 06 data 0000000000000010
[10] commit pc 000000008000001a inst c20023f3 wen 1 dst 07 data 0000000000000000
...
t2 different at pc = 0x0080000016, right = 0x0000000000000010, wrong = 0x0000000000000000
```

[vsetvli_read_vl_min.spike.log](https://github.com/user-attachments/files/26332816/vsetvli_read_vl_min.spike.log)
[vsetvli_read_vl_min.xiangshan.log](https://github.com/user-attachments/files/26332815/vsetvli_read_vl_min.xiangshan.log)

### Expected behavior

The expected architectural result is:
- t1 = 16
- t2 = 16

However, XiangShan does not behave this way. The actual result is:
- Spike (REF): t2 = 16
- XiangShan (DUT): t2 = 0



### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26332788/bug-report.tar.gz)

### To Reproduce

The code is as follows:
```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  # Enable vector state so vector CSRs/instructions are legal.
  li t6, 0x600
  csrs mstatus, t6

  # Minimal reproducer:
  # Spike updates vl to 16 here, while XiangShan currently exposes 0
  # to the immediately following csrr vl.
  li a0, 16
  vsetvli t1, a0, e8, m1, ta, ma
  csrr t2, vl

  li t3, 16
  bne t2, t3, bug

pass:
  j pass

bug:
  j bug

trap_handler:
  j bug
```

To Reproduce:
- Build XiangShan build/emu.
- Run native XiangShan with difftest enabled on the testcase ELF.
- Run native XiangShan with --no-diff on the same ELF.
- Run Spike standalone on the same ELF.

The vsetvli_read_vl_min.S and vsetvli_read_vl_min.elf are as follows:

[vsetvli_read_vl_min.zip](https://github.com/user-attachments/files/26332811/vsetvli_read_vl_min.zip)

### Additional context

_No response_
