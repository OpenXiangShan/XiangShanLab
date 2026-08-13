### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

I think I noticed an inconsistency between XiangShan and the RISCV ISA during a testing round. I've attached the elf for reproduction. 

The issue arise at PC **0x80056cec** on core 1. We set status.MIE at PC **0x80056cb0** on core 1 and enter a busy loop, waiting for an interrupt from core 0 (core 0 send the interrupt at PC **0x800449f0** ).

When inspecting the CSR register value (from NEMU and the traces), it seems like mstatus.MIE is not set, which is confirmed by the interrupt never causing a trap, even if it is properly recieved and visible in mip.MSIP (and mie.MSIP is enabled). 

NEMU output:
```
---------------- Privileged CSRs ----------------
pc: 0x0000000080056cec  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x8000040a00007980   sstatus: 0x8000000200006100  vsstatus: 0x0000000200000000
    .
    .
    .
    mip: 0x0000000000000008       mie: 0x0000000000000008
```

```
=> mstaus.MIE set here has no effect 
    80056cb0:   30046073                csrsi   mstatus,8
    80056cb4:   000032b7                lui     t0,0x3
    80056cb8:   10000813                li      a6,256
    80056cbc:   30083073                csrc    mstatus,a6
    80056cc0:   28a28293                addi    t0,t0,650 # 328a <LEN-0x9f76e>
    80056cc4:   10000813                li      a6,256
    80056cc8:   30082073                csrs    mstatus,a6
    80056ccc:   305a9873                csrrw   a6,mtvec,s5
    80056cd0:   1c8a0a13                addi    s4,s4,456 # 331c8 <LEN-0x6f830>
    80056cd4:   08000a93                li      s5,128
    80056cd8:   300aa073                csrs    mstatus,s5
    80056cdc:   300e2073                csrs    mstatus,t3
    80056ce0:   030af83b                remuw   a6,s5,a6
    80056ce4:   01ed00a3                sb      t5,1(s10)
    80056ce8:   0ff0000f                fence
=> We wait for the inetrrupt here
    80056cec:   0000006f                j       80056cec
```

### Expected behavior

I would expect mstatus.MIE to be correctly set to 1, such that the interrupt received triggers a trap

### To Reproduce

Run this binary, which will never terminate, due to the interrupt no being received:

[rtl666102_xiangshan_0_73_True.zip](https://github.com/user-attachments/files/20349722/rtl666102_xiangshan_0_73_True.zip)

### Environment
latest commits

### Additional context

I found the issue after a fuzzing round, so the program might not always make logical sense. 
I looked at the waveform and tried to reduce the program to isolate the issue to a few instructions, without success. I would really appreciate if you could confirm this is not a programming error on my side :).
