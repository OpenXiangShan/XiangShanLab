### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug
```
==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x000000008003a760
the first commit instr pc of REF is 0x000000008003a760

============== Commit Group Trace (Core 1) ==============
commit group [00]: pc 008003a760 cmtcnt 3 <--
commit group [01]: pc 00800290f4 cmtcnt 1
commit group [02]: pc 00800290f8 cmtcnt 1
commit group [03]: pc 0080011fc4 cmtcnt 6
commit group [04]: pc 0080011fdc cmtcnt 1
commit group [05]: pc 0080011fe0 cmtcnt 1
commit group [06]: pc 0080011fe4 cmtcnt 3
commit group [07]: pc 008003a6f0 cmtcnt 2
commit group [08]: pc 008003a6f8 cmtcnt 1
commit group [09]: pc 008003a6fc cmtcnt 4
commit group [10]: pc 008003a70c cmtcnt 6
commit group [11]: pc 008003a724 cmtcnt 4
commit group [12]: pc 008003a734 cmtcnt 1
commit group [13]: pc 008003a738 cmtcnt 4
commit group [14]: pc 008003a748 cmtcnt 1
commit group [15]: pc 008003a74c cmtcnt 5

============== Commit Instr Trace ==============
[00] commit pc 000000008003a718 inst 025942b3 wen 1 dst 05 data 0a6625683de52e97 idx 05f div     t0, s2, t0
[01] commit pc 000000008003a724 inst 00e2a223 wen 0 dst 04 data 0000000000000000 idx 060 (04) (S) sw      a4, 4(t0)
[02] commit pc 000000008003a728 inst 34402773 wen 1 dst 14 data 0000000000000008 idx 061 csrr    a4, mip
[03] commit pc 000000008003a72c inst fe070ee3 wen 0 dst 29 data 0000000000000000 idx 062 beqz    a4, pc - 4
[04] commit pc 000000008003a730 inst 38000137 wen 1 dst 02 data 0000000038000000 idx 063 lui     sp, 0x38000
[05] commit pc 000000008003a734 inst 00012223 wen 0 dst 04 data 0000000000000000 idx 064 (05) (S) sw      zero, 4(sp)
[06] commit pc 000000008003a738 inst 00410103 wen 1 dst 02 data 0000000000000000 idx 065 (3a) (S) lb      sp, 4(sp)
[07] commit pc 000000008003a73c inst fe011ce3 wen 0 dst 25 data 0000000000000000 idx 066 bnez    sp, pc - 8
[08] commit pc 000000008003a740 inst 00e7b2b3 wen 1 dst 05 data 0000000000000001 idx 067 sltu    t0, a5, a4
[09] commit pc 000000008003a748 inst 0012e293 wen 1 dst 05 data 0000000000000001 idx 068 ori     t0, t0, 1
[10] commit pc 000000008003a74c inst 00572223 wen 0 dst 04 data 0000000000000000 idx 069 (06) (S) sw      t0, 4(a4)
[11] commit pc 000000008003a750 inst 34402773 wen 1 dst 14 data 0000000000000008 idx 06a csrr    a4, mip
[12] commit pc 000000008003a754 inst fe070ee3 wen 0 dst 29 data 0000000000000000 idx 06b beqz    a4, pc - 4
[13] commit pc 000000008003a758 inst 38000837 wen 1 dst 16 data 0000000038000000 idx 06c lui     a6, 0x38000
[14] commit pc 000000008003a760 inst 00e82223 wen 0 dst 04 data 0000000000000000 idx 06d (07) (S) sw      a4, 4(a6)
[15] commit pc 000000008003a764 inst 344022f3 wen 1 dst 05 data 0000000000000000 idx 06e csrr    t0, mip
[16] commit pc 000000008003a768 inst fe028ee3 wen 0 dst 29 data 0000000000000000 idx 06f beqz    t0, pc - 4 <--
[17] commit pc 0000000080011fc4 inst 2a1412d3 wen 1 dst 05 data 3169478648bbe883 idx 050 fmax.d  ft5, fs0, ft1
[18] commit pc 0000000080011fdc inst 296c23c7 wen 1 dst 07 data ffffffff7fc00000 idx 051 fmsub.s ft7, fs8, fs6, ft5
[19] commit pc 0000000080011fe0 inst 2a729653 wen 1 dst 12 data 3169478648bbe883 idx 052 fmax.d  fa2, ft5, ft7
[20] commit pc 0000000080011fe4 inst a083a753 wen 1 dst 14 data 0000000000000000 idx 053 feq.s   a4, ft7, fs0
[21] commit pc 0000000080011fe8 inst fc0804e3 wen 0 dst 09 data 0000000000000000 idx 054 beqz    a6, pc - 56
[22] commit pc 0000000080011fec inst 7042876f wen 1 dst 14 data 0000000080011ff0 idx 055 jal     a4, pc + 0x28704
[23] commit pc 000000008003a6f0 inst 80037bb7 wen 1 dst 23 data ffffffff80037000 idx 056 lui     s7, 0x80037
[24] commit pc 000000008003a6f8 inst 00072223 wen 0 dst 04 data 0000000000000000 idx 057 (02) (S) sw      zero, 4(a4)
[25] commit pc 000000008003a6fc inst 00470703 wen 1 dst 14 data 0000000000000000 idx 058 (39) (S) lb      a4, 4(a4)
[26] commit pc 000000008003a700 inst fe071ce3 wen 0 dst 25 data 0000000000000000 idx 059 bnez    a4, pc - 8
[27] commit pc 000000008003a704 inst 38000837 wen 1 dst 16 data 0000000038000000 idx 05a lui     a6, 0x38000
[28] commit pc 000000008003a708 inst 00116113 wen 1 dst 02 data 0000000000000001 idx 05b ori     sp, sp, 1
[29] commit pc 000000008003a70c inst 00282223 wen 0 dst 04 data 0000000000000000 idx 05c (03) (S) sw      sp, 4(a6)
[30] commit pc 000000008003a710 inst 344022f3 wen 1 dst 05 data 0000000000000008 idx 05d csrr    t0, mip
[31] commit pc 000000008003a714 inst fe028ee3 wen 0 dst 29 data 0000000000000000 idx 05e beqz    t0, pc - 4

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0xb9508f4a1845b79d   sp: 0x0000000000000000   gp: 0x1cfadbb7674b2627
  tp: 0xc8491fc856fdb13f   t0: 0x0000000000000008   t1: 0x0000000000000000   t2: 0x68deea292e873eb4
  s0: 0x0a7037de223f68fd   s1: 0x000000008001f328   a0: 0x9d6457225e066ddf   a1: 0xc891f1c41b96a85c
  a2: 0x668c2590a06fe6f7   a3: 0x3e0db345b1bc8855   a4: 0x0000000000000009   a5: 0x0000000000000000
  a6: 0x0000000038000000   a7: 0x21a354d88873f18f   s2: 0x53312b41ef2974bb   s3: 0x98a8f113938a2a64
  s4: 0x5be20c04c47b51ef   s5: 0x000000008005963e   s6: 0x000000008004b9f2   s7: 0xffffffff80037000
  s8: 0x0000000000000000   s9: 0x00000000800001b8  s10: 0x0000000080180018  s11: 0x0000000000000000
  t3: 0x0000000000001800   t4: 0x0000000000006000   t5: 0x00000000ffffffff   t6: 0x0000000080000000
---------------- Float Registers ----------------
 ft0: 0xb76d4a7cdf20462c  ft1: 0x155593e4ad640e61  ft2: 0x5c02beac9d66f454  ft3: 0xffffffff00000000
 ft4: 0xa454dfa662d1310c  ft5: 0x3169478648bbe883  ft6: 0x21d107c10a41e7ac  ft7: 0xffffffff7fc00000
 fs0: 0x3169478648bbe883  fs1: 0xc68155b87d606d6f  fa0: 0xffffffff00000000  fa1: 0xe8154fabd70d18c6
 fa2: 0x3169478648bbe883  fa3: 0xd519d43261c491d7  fa4: 0x8e1cd12243b9d6c5  fa5: 0xde0960fdf41a849f
 fa6: 0x155593e4ad640e61  fa7: 0x07f8ef54364c708c  fs2: 0xb5d373535ad28a47  fs3: 0xf129858c5566eeab
 fs4: 0x4edb8f331a431a9c  fs5: 0x7f3b08a7ccc1730d  fs6: 0x087ac55725559678  fs7: 0x7b760df303f55451
 fs8: 0x7ff2347fd84edd53  fs9: 0xce279d327460bdfa fs10: 0x5e6ad4c5be6f752e fs11: 0x508c7888e7104c23
 ft8: 0xd98b196b3bb2da34  ft9: 0x0000000000000000 ft10: 0x280b384d7505606c ft11: 0xcedf68fd99c3c4f5
 fcsr: 0x0000000000000011 fflags: 0x0000000000000011 frm: 0x0000000000000000
---------------- Privileged CSRs ----------------
pc: 0x000000008003a76c  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x8000000a00006180   sstatus: 0x8000000200006100  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x8000000000000003      mepc: 0x0000000080011fc4     mtval: 0x0000000000000000
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0x0000000000000000  sscratch: 0x0000000000000000 vsscratch: 0x0ac12f34af27b074
     mtvec: 0x00000000800290ec     stvec: 0x0000000000000000    vstvec: 0x0000000000000000
       mip: 0x0000000000000008       mie: 0x0000000000000008
   mideleg: 0x0000000000001444   medeleg: 0x0000000000000000
   hideleg: 0x0000000000000000   hedeleg: 0x0000000000000000
      satp: 0x0000000000000000     hgatp: 0x0000000000000000     vsatp: 0x0000000000000000
 mcounteren: 0x0000000000000000 scounteren: 0x0000000000000000 hcounteren: 0x0000000000000000
  miselect: 0x0000000000000000  siselect: 0x0000000000000000 vsiselect: 0x0000000000000000
     mireg: 0x0000000000000000     sireg: 0x0000000000000000    vsireg: 0x0000000000000000
     mtopi: 0x0000000000030001     stopi: 0x0000000000000000    vstopi: 0x0000000000000000
     mvien: 0x0000000000000000     hvien: 0x0000000000000000      mvip: 0x0000000000000000
    mtopei: 0x0000000000000000    stopei: 0x0000000000000000   vstopei: 0x0000000000000000
    hvictl: 0x0000000000000000  hviprio1: 0x0000000000000000  hviprio2: 0x0000000000000000
---------------- PMP CSRs ----------------
pmp: 16 entries active, details:
 0: cfg:0x1f addr:0x00003fffffffffff| 1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000| 3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000| 5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000| 7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000| 9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000|11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000|13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000|15: cfg:0x00 addr:0x0000000000000000
---------------- Vector Registers ----------------
v0 : 0x19821face7903f6a_640e0fe727e3bafe  v1 : 0xebb1d3be8332f1d7_e5cc40064687365d
v2 : 0x8a5fd6dc38096fea_e6aa7646039afa73  v3 : 0x8dae5bb3d65f4fd7_808f49c0d2fbd5c3
v4 : 0x77063ee84c0830af_7b6a84600ad4c2ef  v5 : 0x5b2c362e09a85226_18f8c7e660eaca79
v6 : 0xe0fed7987435d5a2_bc96dc5c20c99f56  v7 : 0x09e52cff0b83a515_9fd986839b898ce7
v8 : 0xf5f48d027b6cb52c_9ce33523c14086d4  v9 : 0xb17ca46d0db3e562_237763a1381ba123
v10: 0xfbc570a34dc19d1a_853b3ee5bf650bf6  v11: 0xdc4f5cb5e0aafd09_cf543e40d99d8b15
v12: 0x62eae3448216326a_ba107235f331a6eb  v13: 0x8aab4e21b51d8b0f_4ed06bf4d88b57d5
v14: 0x58d574618b4de026_444a0023a19d0d8b  v15: 0xc7d112fc0f113146_1d5f9888a6e9415b
v16: 0x9294c1da657bca31_1a9e442b3bfe9f8d  v17: 0x2e0f80fbceb9e3b1_3cd6e657c05454d0
v18: 0xeb36197c9488e7c4_76ae7b44fca97a96  v19: 0xc81d540e7e98465c_e2bc6c87702062a5
v20: 0x26d983d7e0c646ad_114bfecda6879dfa  v21: 0xf0df76fba2dd70f1_6bc703017644bc6a
v22: 0xd5b1297eb4abacc9_1966106e5cf78b7a  v23: 0x3acf3c4a1518a3f8_203947fcf482b63a
v24: 0xfb33b3848b8b3f91_6655d4971e301c6f  v25: 0x820d0798c3d0f522_d3dedf1fe8edac94
v26: 0x712e65743b3789fa_47bf31160f54f33e  v27: 0x28812b5776a2b1ee_dcbf060bbe169713
v28: 0xf677833e3ccec342_9c47fe52000a982f  v29: 0xde9978484e330e53_fb3382318b8e61f1
v30: 0xa876da7561f99e04_a6637087fc694486  v31: 0x7eeffa7f25488f47_7828d18c254d313f
  vtype: 0x8000000000000000 vstart: 0x0000000000000000  vxsat: 0x0000000000000001
   vxrm: 0x0000000000000000     vl: 0x0000000000000000   vcsr: 0x0000000000000001
---------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 1: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 2: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 3: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 4: tdata1: 0x0000000000000000 tdata2: 0x0000000000000000
privilegeMode: 3
     t0 different at pc = 0x008003a760, right= 0x0000000000000008, wrong = 0x0000000000000000
```

### Expected behavior

There seems to be a problem with the way local interrupts are received. The register is expected to have received the interrupt. I am not very familiar with the way NEMU works, but it could very well be a NEMU bug, something with delays not being properly handled maybe?

### To Reproduce

`./emu -i /rtl414540_xiangshan_79_76_False.elf --diff=riscv64-nemu-interpreter-dual-so --ram-size=4MB`

### Environment

- XiangShan branch: main
- XiangShan commit id: 16ae9ddcd
- XiangShan config: `make emu CONFIG=DefaultConfig NUM_CORES=2 DISABLE_PERF=1 CXX=clang -j10`
- NEMU commit id: 53bcb568
- SPIKE commit id: \


### Additional context

[rtl414540_xiangshan_79_76_False.zip](https://github.com/user-attachments/files/22853367/rtl414540_xiangshan_79_76_False.zip)
