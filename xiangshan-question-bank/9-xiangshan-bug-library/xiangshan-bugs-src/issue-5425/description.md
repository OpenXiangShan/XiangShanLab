### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug



XiangShan failed with assertion at DCache on vector indexed segment store instructions.

The mismatch is reproducible on all vsoxseg/vsuxseg instructions with `3 <= nf <= 8` and `eew` equal to 8, 16 or 64.

Simulation report:
```
Assertion failed at $NOOP_HOME/build/rtl/DCache.sv:1979.
The simulation stopped. There might be some assertion failed.
```

Tail of nemu trace:
```
[fetch_decode] (M) 0x000000008000266e:   37 7f 1f 81     lui        t5,0xffffffff811f7000
[fetch_decode] (M) 0x0000000080002672:   1b 0f bf b2     c_addiw    t5,t5,-1237
[fetch_decode] (M) 0x0000000080002676:   3b 0f 0f 08     adduw      t5,t5,$0
[fetch_decode] (M) 0x000000008000267a:   13 0a 90 40     c_li       s4,$0,1033
[fetch_decode] (M) 0x000000008000267e:   56 0a           c_slli     s4,s4,21
[fetch_decode] (M) 0x0000000080002680:   13 0a 0a 20     c_addi     s4,s4,512
[fetch_decode] (M) 0x0000000080002684:   87 02 8a 02     vlr        ft5,40(s4),40
```
Same part of elf disasm:
```
8000266e: 37 7f 1f 81   lui     t5, 0x811f7
80002672: 1b 0f bf b2   addiw   t5, t5, -0x4d5
80002676: 3b 0f 0f 08   zext.w  t5, t5
8000267a: 13 0a 90 40   li      s4, 0x409
8000267e: 56 0a         slli    s4, s4, 0x15
80002680: 13 0a 0a 20   addi    s4, s4, 0x200
80002684: 87 02 8a 02   vl1r.v  v5, (s4)
80002688: 27 0c 5f c4   vsuxseg7ei8.v   v24, (t5), v5, v0.t   // <-- First instruction after trace end (where assertion occured)
```


### Expected behavior

Normal instruction execution according to RISC-V Vector Specification.

### To Reproduce

Reproduction for vsuxseg7ei8.v:

```
$NOOP_HOME/build/emu -b 0 -e 0 -i snippy-rvv-test-994-suxi.elf --diff $NOOP_HOME/ready-to-run/riscv64-nemu-interpreter-debug-so
```

Binary file: [snippy-rvv-test-994-suxi.zip](https://github.com/user-attachments/files/24330794/snippy-rvv-test-994-suxi.zip).

### Environment

- XiangShan commit: `98e6aff91363332d7e45fa89c934d710aff662b9`
- ready-to-run commit: `c4e0350c0f686cfa206d5b47d80cfd730f39675a`


### Additional context

This test was generated using the LLVM Snippy random snippet generator and linked with a simple bare-metal boot code.
For more information about the generator, see: https://llvm-snippy.github.io/llvm-snippy/.
