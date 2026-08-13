### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

Bare-metal workload. XiangShan commit trace shows no committed instructions between pc=0x800005f4 and pc=0x800005fe, while NEMU executes `c_addi a5,a5,-16` at 0x800005f8, `c_add a5,a5,s0` at 0x800005fa, `p_li_0 a3` at 0x800005fc, and `vsetvli` at 0x800005fe.

The 10-byte skip is consistent across minimal and kmh2 configurations.

**minimal (last commit group):**

```
commit group [15]: pc 00800005e2 cmtcnt 10 <--

============== Commit Instr Trace ==============
...
[27] commit pc 00000000800005e2 inst 02b700a7 wen 1 dst 32 data 0000000000000010 idx 02b (00) vsm.v   v1, (a4)
[28] commit pc 00000000800005e6 inst 0c507757 wen 1 dst 14 data 0000000000000002 idx 02c vsetvli a4, zero, e8, mf8, ta, 
[29] commit pc 00000000800005ea inst c22027f3 wen 1 dst 15 data 0000000000000010 idx 02d csrr    a5, vlenb
[30] commit pc 00000000800005f4 inst fa078793 wen 1 dst 15 data ffffffffffffff80 idx 02e addi    a5, a5, -96
[31] commit pc 00000000800005fe inst 0d877057 wen 0 dst 00 data ffffffffffffff9d idx 02f vsetvli zero, a4, e64, m1, ta,  <--
```

**kmh2 (last commit group):**

```
commit group [15]: pc 00800005ea cmtcnt 18 <--

============== Commit Instr Trace ==============
...
[25] commit pc 00000000800005f4 inst fa078793     addi    a5, a5, -96
[26] commit pc 00000000800005fe inst 0d877057     vsetvli zero, a4, e64, m1, ta
[27] commit pc 0000000080000602 inst 5e06c0d7     vmv.v.x v1, a3
[28] commit pc 0000000080000606 inst 028780a7     vs1r.v  v1, (a5)
[29] commit pc 000000008000060a inst c2202773     csrr    a4, vlenb
[30] commit pc 000000008000061a inst ff078793     addi    a5, a5, -16
[31] commit pc 000000008000061c inst 00878733     add     a4, a5, s0
...
```

**NEMU standalone trace (instructions in the 10-byte gap):**

```
(M)0x800005f4:   93 87 07 fa     c_addi     a5,a5,-96
(M)0x800005f8:   c1 17           c_addi     a5,a5,-16
(M)0x800005fa:   a2 97           c_add      a5,a5,s0
(M)0x800005fc:   81 46           p_li_0     a3
(M)0x800005fe:   57 70 87 0d     vsetvli    $0,a4,s8
```

NEMU ref state (difftest, both configurations):
```
mcause: 0x0000000000000004      mepc: 0x00000000800005fc     mtval: 0x00000000000000b2
```

### Expected behavior

XiangShan should commit instructions at 0x800005f8, 0x800005fa, 0x800005fc. Both emulators should have the same execution path through these PCs.


### Environment

- XiangShan commit: 7be121c71f
- NEMU: ready-to-run/riscv64-nemu-interpreter-so
- Reproduced on both MinimalConfig and kmh2

### To Reproduce

```
./build.minimal/emu -i 70ea8d559283afbf --diff ready-to-run/riscv64-nemu-interpreter-so
```

Seed: `70ea8d559283afbf`

[insnskip.zip](https://github.com/user-attachments/files/29175063/insnskip.zip)

### Additional context

Self-modifying code excluded: `--dump-ref-trace` shows all `paddr write` target 0x8001xxxx (data/stack/BSS), none fall in 0x8000xxxx (code segment).
