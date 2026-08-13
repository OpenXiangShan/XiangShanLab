### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

当执行 lw	gp,0(a5)  指令时，xiangshan卡死，但是spike正常运行。 我在附件中提供了两个elf文件，correct.elf 以及 false.elf。 二者唯一的区别在pc= 0x801466d8处，false.elf 此处的指令为 lw	gp,0(a5) ， correct.elf 中是nop。

当执行 `emuDefaultConfig -i false.elf --no-diff `时，程序卡住，ctrl+c后显示 `Core 0: SOME SIGNAL STOPS THE PROGRAM at pc = 0xe`

当执行 `emuDefaultConfig -i false.elf --diff=nemu ` 时，显示

```
The first instruction of core 0 has commited. Difftest enabled.
[src/memory/paddr.c:239,check_paddr] isa pma check failed
[src/memory/paddr.c:239,check_paddr] isa pma check failed
Core 0 dump: HIT CRITICAL ERROR: please check if software cause a double trap.
Core 0: HIT GOOD TRAP at pc = 0xe
```

当执行 `emuDefaultConfig -i correct.elf --no-diff `时，程序可以运行至 `Core 0: HIT GOOD TRAP at pc = 0x80032b38`

随后我使用spike查看false.elf运行至pc=0x801466d8，查看a5指向的地址，和该存放的数据，发现a5指向0x000000008012ecd0，完全在合法地址空间内，所以排除了地址越界导致的错误。

spike 命令如下：
```bash
$ spike -d --pc=0x80000000 false.elf
warning: tohost and fromhost symbols not in ELF; can't communicate with target
(spike) until pc 0 801466d8
(spike) reg 0
zero: 0x0000000000000000  ra: 0x300e207300000013  sp: 0x0000000000000001  gp: 0x0000000000000000
  tp: 0xffffffffffffecd2  t0: 0x00000000660e0000  t1: 0x0a09b0870989b007  t2: 0x0b09b1870a89b107
  s0: 0x0c09b2870b89b207  s1: 0x0009b0830c89b307  a0: 0x0109b1830089b103  a1: 0x0000000000000001
  a2: 0x0309b3830289b303  a3: 0x0409b4830389b403  a4: 0x0000000000000000  a5: 0x000000008012ecd0
  a6: 0x0000000000000001  a7: 0x0809b8830789b803  s2: 0x000000004d9a26a2  s3: 0xf9341c685c0cb16f
  s4: 0x0000000000000000  s5: 0x0000000000000000  s6: 0x0000000000000000  s7: 0x0000000000000000
  s8: 0x0000000000000000  s9: 0x0000000000000000 s10: 0x0000000000000100 s11: 0x0000000000001000
  t3: 0x0000000000001800  t4: 0x0000000000002000  t5: 0x00000000ffffffff  t6: 0x0000000080000000
(spike) mem 0 0x8012ecd0
0x0000000000000000
(spike) r 1
core   0: 0x00000000801466d8 (0x0007a183) lw      gp, 0(a5)
(spike) r 1
core   0: 0x00000000801466dc (0x02f591b3) mulh    gp, a1, a5
(spike) reg 0
zero: 0x0000000000000000  ra: 0x300e207300000013  sp: 0x0000000000000001  gp: 0x0000000000000000
  tp: 0xffffffffffffecd2  t0: 0x00000000660e0000  t1: 0x0a09b0870989b007  t2: 0x0b09b1870a89b107
  s0: 0x0c09b2870b89b207  s1: 0x0009b0830c89b307  a0: 0x0109b1830089b103  a1: 0x0000000000000001
  a2: 0x0309b3830289b303  a3: 0x0409b4830389b403  a4: 0x0000000000000000  a5: 0x000000008012ecd0
  a6: 0x0000000000000001  a7: 0x0809b8830789b803  s2: 0x000000004d9a26a2  s3: 0xf9341c685c0cb16f
  s4: 0x0000000000000000  s5: 0x0000000000000000  s6: 0x0000000000000000  s7: 0x0000000000000000
  s8: 0x0000000000000000  s9: 0x0000000000000000 s10: 0x0000000000000100 s11: 0x0000000000001000
  t3: 0x0000000000001800  t4: 0x0000000000002000  t5: 0x00000000ffffffff  t6: 0x0000000080000000
```

### Expected behavior

程序运行至 Core 0: HIT GOOD TRAP at pc = 0x80032b38

### To Reproduce

运行出错elf并查看objdump：
emuDefaultConfig -i false.elf --no-diff
riscv64-unknown-elf-objdump -d false.elf > false_dump.txt

运行出错elf且启动差异测试:
emuDefaultConfig -i false.elf --diff=nemu

运行正确elf并查看objdump：
emuDefaultConfig -i correct.elf --no-diff
riscv64-unknown-elf-objdump -d correct.elf > correct_dump.txt

[files.zip](https://github.com/user-attachments/files/22997134/files.zip)

### Environment

- XiangShan branch:  main
- XiangShan commit id:  f6efe9e
- XiangShan config:  make emu CONFIG=DefaultConfig DISABLE_PERF=1 -j 16
- NEMU commit id: cad8a72
- SPIKE commit id: d1efcdf



### Additional context

_No response_
