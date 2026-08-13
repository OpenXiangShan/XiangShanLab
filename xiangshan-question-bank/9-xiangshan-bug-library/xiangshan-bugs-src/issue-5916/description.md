### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug


`prefetch.r` seems to take an unexpected exception path.

In my test case, the instruction at `0x80001198` is decoded as `prefetch.r 0(s5)`. After that instruction, trap-related architectural state changes unexpectedly, including privilege mode and trap CSRs such as `mcause`, `mepc`, `mtval`, and `mstatus`. The run eventually aborts due to architectural mismatch.

This suggests that `prefetch.r` may be handled like a faulting memory operation instead of a non-trapping software prefetch.

Log:
```
emu compiled at May  8 2026, 17:05:31
[INFO] init for constantin: loaded from init.
Using simulated 32768B flash
Core  0's Commit SHA is: 0f72de2702, dirty: 1
Using simulated 8386560MB RAM
The image is ./batch_000076__seeds_8_/seeds_8_.elf
ELF file detected and loading image from extracted elf file
Loading 7608 bytes at address 0x80000000 at offset 0x0
Loading 104 bytes at address 0x80002000 at offset 0x2000
The reference model is /home/server/RISCV/divefuzz-release/DiveFuzz/dut/XiangShan/ready-to-run/riscv64-spike-so
The first instruction of core 0 has commited. Difftest enabled. 

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0080001170 cmtcnt 1
commit group [01]: pc 0080001174 cmtcnt 1
commit group [02]: pc 0080001178 cmtcnt 1
commit group [03]: pc 0080001000 cmtcnt 1
commit group [04]: pc 0080001004 cmtcnt 1
commit group [05]: pc 0080001008 cmtcnt 1
commit group [06]: pc 008000100c cmtcnt 1
commit group [07]: pc 0080001180 cmtcnt 1
commit group [08]: pc 0080001184 cmtcnt 1
commit group [09]: pc 0080001000 cmtcnt 1
commit group [10]: pc 0080001004 cmtcnt 1
commit group [11]: pc 0080001008 cmtcnt 1
commit group [12]: pc 008000100c cmtcnt 1
commit group [13]: pc 008000118c cmtcnt 1
commit group [14]: pc 0080001190 cmtcnt 1
commit group [15]: pc 0080001194 cmtcnt 1 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001138 idx 025 csrw    mepc, a3
[01] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001138 idx 026 mret
[02] commit pc 0000000080001138 inst 42b1d413 wen 1 dst 08 data ffffffffffffffff idx 027 srai    s0, gp, 43
[03] exception pc 000000008000113c inst a3cfab53 cause 0000000000000002 feq.d   s6, ft11, ft8
[04] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000113c idx 028 csrr    a3, mepc
[05] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001140 idx 029 addi    a3, a3, 4
[06] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001140 idx 02a csrw    mepc, a3
[07] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001140 idx 02b mret
[08] commit pc 0000000080001140 inst 00000e97 wen 1 dst 29 data 0000000080001140 idx 02c auipc   t4, 0x0
[09] commit pc 0000000080001144 inst 028e8e93 wen 1 dst 29 data 0000000080001168 idx 02d addi    t4, t4, 40
[10] commit pc 0000000080001148 inst 000e8ae7 wen 1 dst 21 data 000000008000114c idx 02e jalr    s5, t4, 0
[11] commit pc 0000000080001168 inst 01b8583b wen 1 dst 16 data 000000000000000f idx 02f srlw    a6, a6, s11
[12] commit pc 000000008000116c inst 006aeeb3 wen 1 dst 29 data ffffffffffff7f4f idx 000 or      t4, s5, t1
[13] commit pc 0000000080001170 inst 035601bb wen 1 dst 03 data ffffffff9d6e0540 idx 001 mulw    gp, a2, s5
[14] commit pc 0000000080001174 inst 02d149bb wen 1 dst 19 data 0000000000000000 idx 002 divw    s3, sp, a3
[15] commit pc 0000000080001178 inst 610fb92f wen 1 dst 18 data 00000000618250f6 idx 003 amoand.d s2, a6, (t6)
[16] exception pc 000000008000117c inst d9efac27 cause 0000000000000002 fsw     ft10, -616(t6)
[17] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000117c idx 004 csrr    a3, mepc
[18] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001180 idx 005 addi    a3, a3, 4
[19] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001180 idx 006 csrw    mepc, a3
[20] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001180 idx 007 mret
[21] commit pc 0000000080001180 inst 032ec23b wen 1 dst 04 data 0000000000000000 idx 008 divw    tp, t4, s2
[22] commit pc 0000000080001184 inst 0031ee63 wen 0 dst 28 data 0000000000000000 idx 009 bltu    gp, gp, pc + 28
[23] exception pc 0000000080001188 inst e2069c53 cause 0000000000000002 fclass.d s8, fa3
[24] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 0000000080001188 idx 00a csrr    a3, mepc
[25] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 000000008000118c idx 00b addi    a3, a3, 4
[26] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 000000008000118c idx 00c csrw    mepc, a3
[27] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 000000008000118c idx 00d mret
[28] commit pc 000000008000118c inst 034c76bb wen 1 dst 13 data 00000000371a9f3a idx 00e remuw   a3, s8, s4
[29] commit pc 0000000080001190 inst 02d343b3 wen 1 dst 07 data 0000000000000000 idx 00f div     t2, t1, a3
[30] commit pc 0000000080001194 inst 026afabb wen 1 dst 21 data ffffffff8000114c idx 010 remuw   s5, s5, t1
[31] exception pc 0000000080001198 inst 001ae013 cause 0000000000000005 prefetch.r 0(s5) <--

==============  REF Regs  ==============
zero: 0x0000000000000000   ra: 0x90ab7d13bfa38e23   sp: 0x00000000618250f6   gp: 0xffffffff9d6e0540 
  tp: 0x0000000000000000   t0: 0x000000000fa61109   t1: 0xffffffffffff7f47   t2: 0x0000000000000000 
  s0: 0xffffffffffffffff   s1: 0x0000000000000001   a0: 0xa90833730cade365   a1: 0xcaecb60014e7bc1e 
  a2: 0x0005b48e416c4f70   a3: 0x00000000371a9f3a   a4: 0xbd40c309bb668746   a5: 0x000000004815e0a3 
  a6: 0x000000000000000f   a7: 0x0000000000000000   s2: 0x00000000618250f6   s3: 0x0000000000000000 
  s4: 0xffffffffbfa38e23   s5: 0xffffffff8000114c   s6: 0x0000000000000000   s7: 0x0000000000000001 
  s8: 0x00000000371a9f3a   s9: 0x0000000000000000  s10: 0x00000000618250f6  s11: 0x0000000000000000 
  t3: 0x0000000000000001   t4: 0xffffffffffff7f4f   t5: 0x0000000000000000   t6: 0x0000000080000000 
 ft0: 0x1bf7e3255acca2a6  ft1: 0xf7f69ca0da7f3acc  ft2: 0x1f94a3e8d297d74e  ft3: 0x03beb2bfac16f8f9 
 ft4: 0x150d44346f6dcf63  ft5: 0xdb13af7acf72d873  ft6: 0x78b76034474cafe2  ft7: 0xd4d455c688dff2b2 
 fs0: 0x2425444d49266897  fs1: 0x1f7bd6a6a5284dd6  fa0: 0x3a143f85c23062e2  fa1: 0xbf3269adb60b7b61 
 fa2: 0x2c32aa01b5e1515c  fa3: 0xe736354279953e97  fa4: 0x0cfbc14521ff3dcd  fa5: 0x5405c138cb15c41c 
 fa6: 0x816faab15181d9f3  fa7: 0xc2bd8230dd1e4c97  fs2: 0x0ef61b1e63ecd666  fs3: 0xd8304b73bd3c6e0b 
 fs4: 0xabe814dd90890326  fs5: 0x69f8d43ca9f5bf0d  fs6: 0x86ccea3c9e9c7209  fs7: 0x041069c3532fc6a4 
 fs8: 0x0579733e7f5677f3  fs9: 0x48ac420a996e0e6a fs10: 0xa97b7d791ee22e2a fs11: 0xc892afa29b5c9c83 
 ft8: 0x0c218ac54b58063e  ft9: 0x1d6b8fabd2a5a109 ft10: 0x8273f9495416c607 ft11: 0x9b71c84f0cbcf30a 
pc: 0x000000008000119c mstatus: 0x0000000a007c2482 mcause: 0x0000000000000002 mepc: 0x000000008000118c
                       sstatus: 0x0000000200000000 scause: 0x0000000000000000 sepc: 0x0000000000000000
satp: 0x0000000000000000
mip: 0x0000000000000000 mie: 0x0000000000000000 mscratch: 0xed688b92682379cd sscratch: 0xed688b92682379cd
mideleg: 0x0000000000001444 medeleg: 0x0000000000000000
mtval: 0x00000000e2069c53 stval: 0x0000000000000000 mtvec: 0x0000000080001000 stvec: 0x0000000000000000
privilege mode:1
 0: cfg:0x0f addr:0x0000000080001000 |  1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000 |  3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000 |  5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000 |  7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000 |  9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000 | 11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000 | 13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000 | 15: cfg:0x00 addr:0x0000000000000000
privilegeMode: 3
   mode different at pc = 0x0080001120, right = 0x0000000000000001, wrong = 0x0000000000000003
mstatus different at pc = 0x0080001120, right = 0x0000000a007c2482, wrong = 0x000004ca007c2c02
   mepc different at pc = 0x0080001120, right = 0x000000008000118c, wrong = 0x0000000080001198
  mtval different at pc = 0x0080001120, right = 0x00000000e2069c53, wrong = 0xffffffff8000114c
 mcause different at pc = 0x0080001120, right = 0x0000000000000002, wrong = 0x0000000000000005
      v different at pc = 0x0080001120, right = 0x0000000000000001, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0x80001120
Core-0 instrCnt = 417, cycleCnt = 5,500, IPC = 0.075818
Seed=0 Guest cycle spent: 5,505 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 9,619ms
```

### Expected behavior


`prefetch.r` should not unexpectedly enter a trap path or update trap CSRs in this case.

I would expect software prefetch instructions to behave as non-faulting hints here, so executing `prefetch.r` should not cause visible exception-state divergence.


### Environment



- Branch: `kunminghu-v3`
- Commit: `0f72de270`


### To Reproduce

[testcase_prefetch.zip](https://github.com/user-attachments/files/27514551/testcase_prefetch.zip)

### Additional context

_No response_
