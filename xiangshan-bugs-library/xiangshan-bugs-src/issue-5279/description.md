### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

I encountered a potential bug when testing XiangShan using a small RVV test case. During execution in M-mode, a vector store instruction with a misaligned base address triggered a store-commit mismatch. The difftest log shows that:

The NEMU commits a store with:
- a non-zero data mask,
- and a write to an address aligned to the corresponding 8-byte block.

The XiangShan reports a store commit for the same PC, but:
- the byte-enable mask is zero,
- and no data is written.


NEMU as the REF:
```
The reference model is ./ready-to-run/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x0000000080001024
the first commit instr pc of REF is 0x0000000080001024

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 008000011e cmtcnt 6
commit group [01]: pc 0080000134 cmtcnt 7
commit group [02]: pc 008000014c cmtcnt 5
commit group [03]: pc 008000015a cmtcnt 15
commit group [04]: pc 008000018e cmtcnt 9
commit group [05]: pc 00800001ac cmtcnt 10
commit group [06]: pc 00800001cc cmtcnt 2
commit group [07]: pc 00800001d2 cmtcnt 8
commit group [08]: pc 00800001ee cmtcnt 8
commit group [09]: pc 0080000208 cmtcnt 1
commit group [10]: pc 008000020c cmtcnt 1
commit group [11]: pc 008000020e cmtcnt 9
commit group [12]: pc 008000022c cmtcnt 3
commit group [13]: pc 0080000236 cmtcnt 1
commit group [14]: pc 0080001010 cmtcnt 2
commit group [15]: pc 0080001018 cmtcnt 3 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000080000170 inst 00d49493 wen 1 dst 09 data 000202164d082000 idx 035
[01] commit pc 0000000080000184 inst 4475051b wen 1 dst 10 data fffffffffffb9447 idx 036
[02] commit pc 000000008000018a inst acb50513 wen 1 dst 10 data fffffffee511bacb idx 037
[03] commit pc 000000008000018e inst 00e51513 wen 1 dst 10 data ffffb9446eb2c000 idx 038
[04] commit pc 0000000080000196 inst ccf50513 wen 1 dst 10 data b9446eb2c338fccf idx 039
[05] commit pc 000000008000019e inst 008f25b7 wen 1 dst 11 data 00000000008f1ab1 idx 03a
[06] commit pc 00000000800001a6 inst 00e59593 wen 1 dst 11 data 00000023c6ac4000 idx 03b
[07] commit pc 00000000800001a8 inst 4a158593 wen 1 dst 11 data 00000023c6ac44a1 idx 03c
[08] commit pc 00000000800001ac inst 00c59593 wen 1 dst 11 data 00023c6ac44a1000 idx 03d
[09] commit pc 00000000800001c0 inst 0976061b wen 1 dst 12 data 00000000007fb097 idx 03e
[10] commit pc 00000000800001ca inst 00c61613 wen 1 dst 12 data 0001fec25c72b000 idx 03f
[11] commit pc 00000000800001cc inst b4d60613 wen 1 dst 12 data 0001fec25c72ab4d idx 040
[12] commit pc 00000000800001d2 inst 23e60613 wen 1 dst 12 data 3fd84b8e5569a23e idx 041
[13] commit pc 00000000800001da inst 01f396b7 wen 1 dst 13 data 0000000001f3915d idx 042
[14] commit pc 00000000800001e2 inst 00d69693 wen 1 dst 13 data 0000003e722ba000 idx 043
[15] commit pc 00000000800001e8 inst 00c69693 wen 1 dst 13 data 0003e722ba087000 idx 044
[16] commit pc 00000000800001ea inst 19b68693 wen 1 dst 13 data 0003e722ba08719b idx 045
[17] commit pc 00000000800001ee inst 00c69693 wen 1 dst 13 data 3e722ba08719b000 idx 046
[18] commit pc 00000000800001fc inst 9677071b wen 1 dst 14 data fffffffffe5e7967 idx 047
[19] commit pc 0000000080000208 inst 3f770713 wen 1 dst 14 data fffe5e79677873f7 idx 048
[20] commit pc 000000008000020c inst 00d71713 wen 1 dst 14 data cbcf2cef0e7ee000 idx 049
[21] commit pc 000000008000020e inst 31470713 wen 1 dst 14 data cbcf2cef0e7ee314 idx 04a
[22] commit pc 0000000080000216 inst fd68d7b7 wen 1 dst 15 data fffffffffd68d697 idx 04b
[23] commit pc 000000008000021e inst 00c79793 wen 1 dst 15 data ffffffd68d697000 idx 04c
[24] commit pc 0000000080000224 inst 00c79793 wen 1 dst 15 data fffd68d69761f000 idx 04d
[25] commit pc 000000008000022a inst 00d79793 wen 1 dst 15 data ad1ad2ec3d676000 idx 04e
[26] commit pc 000000008000022c inst 2e078793 wen 1 dst 15 data ad1ad2ec3d6762e0 idx 04f
[27] commit pc 0000000080000236 inst 5db0006f wen 0 dst 00 data 0000000000000000 idx 050
[28] commit pc 0000000080001010 inst 000482b7 wen 1 dst 05 data 0000000000047ca7 idx 051
[29] commit pc 0000000080001018 inst 00d29293 wen 1 dst 05 data 000000008f94e000 idx 052
[30] commit pc 0000000080001020 inst 050f7057 wen 0 dst 00 data 0000000000000000 idx 053
[31] commit pc 0000000080001024 inst 0202e4a7 wen 1 dst 32 data 000000008f94dc2b idx 054 (00) <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0xa91481ed44a51f16   sp: 0x532659c3fefeedcc   gp: 0x9bf24ec6e92ba606 
  tp: 0x1a5e9671d10e3a98   t0: 0x000000008f94dc2b   t1: 0xcf1d9d041d540aef   t2: 0x587d6e54403ae736 
  s0: 0xb3ce2c44e0da4abe   s1: 0x202164d08243c8b6   a0: 0xb9446eb2c338fccf   a1: 0x478d58894164a7bb 
  a2: 0x3fd84b8e5569a23e   a3: 0x3e722ba08719aea3   a4: 0xcbcf2cef0e7ee314   a5: 0xad1ad2ec3d6762e0 
  a6: 0x0000000000000000   a7: 0x0000000000000000   s2: 0x0000000000000000   s3: 0x0000000000000000 
  s4: 0x0000000000000000   s5: 0x0000000000000000   s6: 0x0000000000000000   s7: 0x0000000000000000 
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x0000000000000000  s11: 0x0000000000000000 
  t3: 0x0000000000000000   t4: 0x0000000000000000   t5: 0x0000000000001000   t6: 0x0000000000000000 
---------------- Float Registers ----------------
 ft0: 0xffffffffffff0000  ft1: 0xffffffff44a51f16  ft2: 0xffffffffffffedcc  ft3: 0x9bf24ec6e92ba606 
 ft4: 0xffffffffd10e3a98  ft5: 0x3420f3d410cefc2f  ft6: 0xffffffffffff0aef  ft7: 0xffffffff403ae736 
 fs0: 0xffffffffe0da4abe  fs1: 0xffffffffffffc8b6  fa0: 0xb9446eb2c338fccf  fa1: 0x478d58894164a7bb 
 fa2: 0xffffffffffffa23e  fa3: 0x3e722ba08719aea3  fa4: 0xffffffffffffe314  fa5: 0xffffffff3d6762e0 
 fa6: 0xe422dfafbef1eddb  fa7: 0xc385a22ff91c8b44  fs2: 0x68d975b7a018626f  fs3: 0x56153110e043a661 
 fs4: 0x56e26e5d43ee10d3  fs5: 0x0000000000000000  fs6: 0xde6a624158451ce4  fs7: 0x0b87666bdb329239 
 fs8: 0xc228c163d1bcb313  fs9: 0x530dcddb03d80058 fs10: 0xea92f75ade8099bb fs11: 0xbef9b45da40171dd 
 ft8: 0x5099a5df4028e333  ft9: 0x418740071a07d6fc ft10: 0x73ae5b72d5414904 ft11: 0x5b39a09bd0077be5 
 fcsr: 0x0000000000000040 fflags: 0x0000000000000000 frm: 0x0000000000000002
---------------- Privileged CSRs ----------------
pc: 0x0000000080001028  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x8000004a00486788   sstatus: 0x8000000200086700  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000000      mepc: 0x000000008000004c     mtval: 0x0000000000000000
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0x04d607169a82bd3e  sscratch: 0x5142142f6fd11beb vsscratch: 0x8f4461d3e016af80
     mtvec: 0x0000000080001000     stvec: 0x0000000000000000    vstvec: 0x0000000000000000
       mip: 0x0000000000000000       mie: 0x0000000000000000
   mideleg: 0x0000000000001444   medeleg: 0x0000000000000000
   hideleg: 0x0000000000000000   hedeleg: 0x0000000000000000
      satp: 0x0000000000000000     hgatp: 0x0000000000000000     vsatp: 0x0000000000000000
 mcounteren: 0x0000000000000000 scounteren: 0x0000000000000000 hcounteren: 0x0000000000000000
  miselect: 0x0000000000000000  siselect: 0x0000000000000000 vsiselect: 0x0000000000000000
     mireg: 0x0000000000000000     sireg: 0x0000000000000000    vsireg: 0x0000000000000000
     mtopi: 0x0000000000000000     stopi: 0x0000000000000000    vstopi: 0x0000000000000000
     mvien: 0x0000000000000000     hvien: 0x0000000000000000      mvip: 0x0000000000000000
    mtopei: 0x0000000000000000    stopei: 0x0000000000000000   vstopei: 0x0000000000000000
    hvictl: 0x0000000000000000  hviprio1: 0x0000000000000000  hviprio2: 0x0000000000000000
---------------- PMP CSRs ----------------
pmp: 32 entries active, details:
 0: cfg:0x00 addr:0x0000000000000000| 1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000| 3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000| 5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000| 7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000| 9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000|11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000|13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000|15: cfg:0x00 addr:0x0000000000000000
16: cfg:0x00 addr:0x0000000000000000|17: cfg:0x00 addr:0x0000000000000000
18: cfg:0x00 addr:0x0000000000000000|19: cfg:0x00 addr:0x0000000000000000
20: cfg:0x00 addr:0x0000000000000000|21: cfg:0x00 addr:0x0000000000000000
22: cfg:0x00 addr:0x0000000000000000|23: cfg:0x00 addr:0x0000000000000000
24: cfg:0x00 addr:0x0000000000000000|25: cfg:0x00 addr:0x0000000000000000
26: cfg:0x00 addr:0x0000000000000000|27: cfg:0x00 addr:0x0000000000000000
28: cfg:0x00 addr:0x0000000000000000|29: cfg:0x00 addr:0x0000000000000000
30: cfg:0x00 addr:0x0000000000000000|31: cfg:0x00 addr:0x0000000000000000
32: cfg:0x00 addr:0x0000000000000000|33: cfg:0x00 addr:0x0000000000000000
34: cfg:0x00 addr:0x0000000000000000|35: cfg:0x00 addr:0x0000000000000000
36: cfg:0x00 addr:0x0000000000000000|37: cfg:0x00 addr:0x0000000000000000
38: cfg:0x00 addr:0x0000000000000000|39: cfg:0x00 addr:0x0000000000000000
40: cfg:0x00 addr:0x0000000000000000|41: cfg:0x00 addr:0x0000000000000000
42: cfg:0x00 addr:0x0000000000000000|43: cfg:0x00 addr:0x0000000000000000
44: cfg:0x00 addr:0x0000000000000000|45: cfg:0x00 addr:0x0000000000000000
46: cfg:0x00 addr:0x0000000000000000|47: cfg:0x00 addr:0x0000000000000000
48: cfg:0x00 addr:0x0000000000000000|49: cfg:0x00 addr:0x0000000000000000
50: cfg:0x00 addr:0x0000000000000000|51: cfg:0x00 addr:0x0000000000000000
52: cfg:0x00 addr:0x0000000000000000|53: cfg:0x00 addr:0x0000000000000000
54: cfg:0x00 addr:0x0000000000000000|55: cfg:0x00 addr:0x0000000000000000
56: cfg:0x00 addr:0x0000000000000000|57: cfg:0x00 addr:0x0000000000000000
58: cfg:0x00 addr:0x0000000000000000|59: cfg:0x00 addr:0x0000000000000000
60: cfg:0x00 addr:0x0000000000000000|61: cfg:0x00 addr:0x0000000000000000
62: cfg:0x00 addr:0x0000000000000000|63: cfg:0x00 addr:0x0000000000000000
---------------- PMA CSRs ----------------
pma: 32 entries active, details:
 0: cfg:0x00 addr:0x0000000000000000| 1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000| 3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000| 5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000| 7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000| 9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000|11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000|13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000|15: cfg:0x00 addr:0x0000000000000000
16: cfg:0x00 addr:0x0000000000000000|17: cfg:0x00 addr:0x0000000000000000
18: cfg:0x00 addr:0x0000000000000000|19: cfg:0x0b addr:0x0000000004000000
20: cfg:0x0f addr:0x0000000008000000|21: cfg:0x0b addr:0x000000000c004000
22: cfg:0x0b addr:0x000000000c014000|23: cfg:0x0b addr:0x000000000e008000
24: cfg:0x0f addr:0x000000000e008400|25: cfg:0x0b addr:0x000000000e008800
26: cfg:0x0b addr:0x000000000e400000|27: cfg:0x0b addr:0x000000000e400800
28: cfg:0x08 addr:0x000000000e800000|29: cfg:0x0b addr:0x0000000020000000
30: cfg:0x6f addr:0x0000020000000000|31: cfg:0x18 addr:0x00001fffffffffff
---------------- Vector Registers ----------------
v0 : 0xb3e3dc54c6817103_e5d52fc5cc96c091  v1 : 0x41042cb5b5f10328_8a0d049141f29f9c  
v2 : 0x34a02554e893dd87_569cf02bff1c80ef  v3 : 0xc3e0ac0d5fff42b0_c921d8f3b8a49222  
v4 : 0x1455640c2c837ed2_1c12b594ebd7ddf6  v5 : 0xb502e24b353fa492_f7a39cfc3bbc4411  
v6 : 0xa321721c8d5c27b2_594493007bee2bba  v7 : 0xbc401894584fd219_fedfcd9aad48a68b  
v8 : 0xc37f4592dc1c2110_a91b4a6dbb07758c  v9 : 0x0d302c967b20d32f_0bca7664d0f6c3ad  
v10: 0x7b6bc531cd1a4e21_29ef0b2a98b374e9  v11: 0x5c9450e692d00779_c8c76aca193e0197  
v12: 0xd498f8547f04cf74_734d4b1c1fbeaa46  v13: 0x765db968a6545d1e_c0ef57e143bb32ad  
v14: 0xe2481b4dda0abdea_4594d3c82841398d  v15: 0x16bee2ceb46ce309_91c1269ad299a578  
v16: 0xc83eb71e25ce6fb8_7a49b759cfef39ce  v17: 0xf750eb6800c7bca1_c574beff845787a1  
v18: 0x8b92c78998d3a348_a4e2e3cc906925f3  v19: 0xc55d40a30a67fabf_0da3a45024ce02a0  
v20: 0x29230ff31a7502c6_e224a59197179c70  v21: 0x2756ab1931338f81_fb70b5bb7a4e60c7  
v22: 0x7abdeeda99ae84bd_afc36c34584bb3a9  v23: 0xfeed2eece57a9229_a7357ddd18d2a5a7  
v24: 0xc857b64919bc87bb_ea5be4c1dbc8ffa2  v25: 0x2887d57ee6160dcb_f4c1fabe2895dd9b  
v26: 0x87c19af4a529206f_0cd43f5d61839947  v27: 0x7300ebdacf9562a0_a2a4d9b79dd34d82  
v28: 0x5bcbca38f1d82731_aaedbd7dbe953c84  v29: 0x78372ab9bc59689d_33cb7a6bf8e4a3e5  
v30: 0x260bf930762bb89e_ffb640e85982b2a5  v31: 0xf48c2d0a2c660778_d41aa58285be6654  
  vtype: 0x0000000000000050 vstart: 0x0000000000000000  vxsat: 0x0000000000000000
   vxrm: 0x0000000000000001     vl: 0x0000000000000004   vcsr: 0x0000000000000002
---------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 1: tdata1: 0xf000000000000000 tdata2: 0x5f70696b735f7473
 2: tdata1: 0xf000000000000000 tdata2: 0x722d6f742d796461
 3: tdata1: 0xf000000000000000 tdata2: 0x2d756d656e2d3436
 4: tdata1: 0x6572707265746e69 tdata2: 0x6f006f732d726574
privilegeMode: 3

==============  Store Commit Event (Core 0)  ==============
Mismatch for store commits 
  REF commits addr 0x000000008f94dc28, data 0x6400000000000000, mask 0x0080, pc 0x0000000080001024
  DUT commits addr 0x000000008f94dc20, data 0x0000000000000000, mask 0x0000, pc 0x0000000080001024, robidx 0x54
Core 0: ABORT at pc = 0xfffe5f06173a73e5
Core-0 instrCnt = 178, cycleCnt = 8,497, IPC = 0.020949
Seed=0 Guest cycle spent: 8,501 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 6,328ms
```

Spike as the REF:
```
The reference model is ./ready-to-run/riscv64-spike-so
The first instruction of core 0 has commited. Difftest enabled. 

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x0000000080001024
the first commit instr pc of REF is 0x0000000080001024

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 008000011e cmtcnt 6
commit group [01]: pc 0080000134 cmtcnt 7
commit group [02]: pc 008000014c cmtcnt 5
commit group [03]: pc 008000015a cmtcnt 15
commit group [04]: pc 008000018e cmtcnt 9
commit group [05]: pc 00800001ac cmtcnt 10
commit group [06]: pc 00800001cc cmtcnt 2
commit group [07]: pc 00800001d2 cmtcnt 8
commit group [08]: pc 00800001ee cmtcnt 8
commit group [09]: pc 0080000208 cmtcnt 1
commit group [10]: pc 008000020c cmtcnt 1
commit group [11]: pc 008000020e cmtcnt 9
commit group [12]: pc 008000022c cmtcnt 3
commit group [13]: pc 0080000236 cmtcnt 1
commit group [14]: pc 0080001010 cmtcnt 2
commit group [15]: pc 0080001018 cmtcnt 3 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000080000170 inst 00d49493 wen 1 dst 09 data 000202164d082000 idx 035
[01] commit pc 0000000080000184 inst 4475051b wen 1 dst 10 data fffffffffffb9447 idx 036
[02] commit pc 000000008000018a inst acb50513 wen 1 dst 10 data fffffffee511bacb idx 037
[03] commit pc 000000008000018e inst 00e51513 wen 1 dst 10 data ffffb9446eb2c000 idx 038
[04] commit pc 0000000080000196 inst ccf50513 wen 1 dst 10 data b9446eb2c338fccf idx 039
[05] commit pc 000000008000019e inst 008f25b7 wen 1 dst 11 data 00000000008f1ab1 idx 03a
[06] commit pc 00000000800001a6 inst 00e59593 wen 1 dst 11 data 00000023c6ac4000 idx 03b
[07] commit pc 00000000800001a8 inst 4a158593 wen 1 dst 11 data 00000023c6ac44a1 idx 03c
[08] commit pc 00000000800001ac inst 00c59593 wen 1 dst 11 data 00023c6ac44a1000 idx 03d
[09] commit pc 00000000800001c0 inst 0976061b wen 1 dst 12 data 00000000007fb097 idx 03e
[10] commit pc 00000000800001ca inst 00c61613 wen 1 dst 12 data 0001fec25c72b000 idx 03f
[11] commit pc 00000000800001cc inst b4d60613 wen 1 dst 12 data 0001fec25c72ab4d idx 040
[12] commit pc 00000000800001d2 inst 23e60613 wen 1 dst 12 data 3fd84b8e5569a23e idx 041
[13] commit pc 00000000800001da inst 01f396b7 wen 1 dst 13 data 0000000001f3915d idx 042
[14] commit pc 00000000800001e2 inst 00d69693 wen 1 dst 13 data 0000003e722ba000 idx 043
[15] commit pc 00000000800001e8 inst 00c69693 wen 1 dst 13 data 0003e722ba087000 idx 044
[16] commit pc 00000000800001ea inst 19b68693 wen 1 dst 13 data 0003e722ba08719b idx 045
[17] commit pc 00000000800001ee inst 00c69693 wen 1 dst 13 data 3e722ba08719b000 idx 046
[18] commit pc 00000000800001fc inst 9677071b wen 1 dst 14 data fffffffffe5e7967 idx 047
[19] commit pc 0000000080000208 inst 3f770713 wen 1 dst 14 data fffe5e79677873f7 idx 048
[20] commit pc 000000008000020c inst 00d71713 wen 1 dst 14 data cbcf2cef0e7ee000 idx 049
[21] commit pc 000000008000020e inst 31470713 wen 1 dst 14 data cbcf2cef0e7ee314 idx 04a
[22] commit pc 0000000080000216 inst fd68d7b7 wen 1 dst 15 data fffffffffd68d697 idx 04b
[23] commit pc 000000008000021e inst 00c79793 wen 1 dst 15 data ffffffd68d697000 idx 04c
[24] commit pc 0000000080000224 inst 00c79793 wen 1 dst 15 data fffd68d69761f000 idx 04d
[25] commit pc 000000008000022a inst 00d79793 wen 1 dst 15 data ad1ad2ec3d676000 idx 04e
[26] commit pc 000000008000022c inst 2e078793 wen 1 dst 15 data ad1ad2ec3d6762e0 idx 04f
[27] commit pc 0000000080000236 inst 5db0006f wen 0 dst 00 data 0000000000000000 idx 050
[28] commit pc 0000000080001010 inst 000482b7 wen 1 dst 05 data 0000000000047ca7 idx 051
[29] commit pc 0000000080001018 inst 00d29293 wen 1 dst 05 data 000000008f94e000 idx 052
[30] commit pc 0000000080001020 inst 050f7057 wen 0 dst 00 data 0000000000000000 idx 053
[31] commit pc 0000000080001024 inst 0202e4a7 wen 1 dst 32 data 000000008f94dc2b idx 054 (00) <--

==============  REF Regs  ==============
zero: 0x0000000000000000   ra: 0xa91481ed44a51f16   sp: 0x532659c3fefeedcc   gp: 0x9bf24ec6e92ba606 
  tp: 0x1a5e9671d10e3a98   t0: 0x000000008f94dc2b   t1: 0xcf1d9d041d540aef   t2: 0x587d6e54403ae736 
  s0: 0xb3ce2c44e0da4abe   s1: 0x202164d08243c8b6   a0: 0xb9446eb2c338fccf   a1: 0x478d58894164a7bb 
  a2: 0x3fd84b8e5569a23e   a3: 0x3e722ba08719aea3   a4: 0xcbcf2cef0e7ee314   a5: 0xad1ad2ec3d6762e0 
  a6: 0x0000000000000000   a7: 0x0000000000000000   s2: 0x0000000000000000   s3: 0x0000000000000000 
  s4: 0x0000000000000000   s5: 0x0000000000000000   s6: 0x0000000000000000   s7: 0x0000000000000000 
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x0000000000000000  s11: 0x0000000000000000 
  t3: 0x0000000000000000   t4: 0x0000000000000000   t5: 0x0000000000001000   t6: 0x0000000000000000 
 ft0: 0xffffffffffff0000  ft1: 0xffffffff44a51f16  ft2: 0xffffffffffffedcc  ft3: 0x9bf24ec6e92ba606 
 ft4: 0xffffffffd10e3a98  ft5: 0x3420f3d410cefc2f  ft6: 0xffffffffffff0aef  ft7: 0xffffffff403ae736 
 fs0: 0xffffffffe0da4abe  fs1: 0xffffffffffffc8b6  fa0: 0xb9446eb2c338fccf  fa1: 0x478d58894164a7bb 
 fa2: 0xffffffffffffa23e  fa3: 0x3e722ba08719aea3  fa4: 0xffffffffffffe314  fa5: 0xffffffff3d6762e0 
 fa6: 0xe422dfafbef1eddb  fa7: 0xc385a22ff91c8b44  fs2: 0x68d975b7a018626f  fs3: 0x56153110e043a661 
 fs4: 0x56e26e5d43ee10d3  fs5: 0x0000000000000000  fs6: 0xde6a624158451ce4  fs7: 0x0b87666bdb329239 
 fs8: 0xc228c163d1bcb313  fs9: 0x530dcddb03d80058 fs10: 0xea92f75ade8099bb fs11: 0xbef9b45da40171dd 
 ft8: 0x5099a5df4028e333  ft9: 0x418740071a07d6fc ft10: 0x73ae5b72d5414904 ft11: 0x5b39a09bd0077be5 
pc: 0x0000000080001028 mstatus: 0x8000004a00486788 mcause: 0x0000000000000000 mepc: 0x000000008000004c
                       sstatus: 0x8000000200086700 scause: 0x0000000000000000 sepc: 0x0000000000000000
satp: 0x0000000000000000
mip: 0x0000000000000000 mie: 0x0000000000000000 mscratch: 0x04d607169a82bd3e sscratch: 0x04d607169a82bd3e
mideleg: 0x0000000000001444 medeleg: 0x0000000000000000
mtval: 0x0000000000000000 stval: 0x0000000000000000 mtvec: 0x0000000080001000 stvec: 0x0000000000000000
privilege mode:3
 0: cfg:0x00 addr:0x0000000000000000 |  1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000 |  3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000 |  5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000 |  7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000 |  9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000 | 11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000 | 13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000 | 15: cfg:0x00 addr:0x0000000000000000
privilegeMode: 3

==============  Store Commit Event (Core 0)  ==============
This version of 'REF' does not support the 'PC' value of store commit event. Please use a newer version of 'REF'.
Mismatch for store commits 
  REF commits addr 0x000000008f94dc28, data 0x6400000000000000, mask 0x0080, pc 0x0000000080001024
  DUT commits addr 0x000000008f94dc20, data 0x0000000000000000, mask 0x0000, pc 0x0000000080001024, robidx 0x54
Core 0: ABORT at pc = 0xfffe5f06173a73e5
Core-0 instrCnt = 178, cycleCnt = 8,497, IPC = 0.020949
Seed=0 Guest cycle spent: 8,501 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 6,830ms
```

### Expected behavior

The RVV allows implementations to either support or trap on misaligned vector memory accesses.
This report does not assume which behavior is correct for this implementation.
The only purpose is to confirm whether the DUT’s combination of:

- committing the instruction, while
- reporting a zero byte-enable mask

matches XiangShan’s intended behavior for misaligned vector stores.

### To Reproduce

In M-mode: 
```
li t0, 0x000000008f94dc2b # misaligned address
vse32.v v9, (t0)
```

[seeds_1810_.zip](https://github.com/user-attachments/files/23832551/seeds_1810_.zip)

### Environment

XiangShan commit: 10746d6aa982eb166f73cdb55c4334902ae604b9 (Wed Nov 26 17:46:13 2025)
Ready-to-run(NEMU) commit: c4e0350c0f686cfa206d5b47d80cfd730f39675a (Fri Nov 7 18:17:19 2025)


### Additional context

_No response_
