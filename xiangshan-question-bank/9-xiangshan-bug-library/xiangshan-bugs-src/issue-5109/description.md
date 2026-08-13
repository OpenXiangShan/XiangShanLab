### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

The output of difftest is as follows:
`emu compiled at Oct 14 2025, 11:30:02
Using simulated 32768B flash
dump wave to /home/stormy/curricular/keyan/code/XiangShan/errors/run_wave.vcd...
Core  0's Commit SHA is: b096858e57, dirty: 0
Using simulated 8386560MB RAM
The image is /home/stormy/curricular/keyan/code/XiangShan/errors/2025-09-15-11-06-03_9578
The reference model is ./ready-to-run/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 
[00] commit pc 0000000010000000 inst 0010029b wen 1 dst 05 data 0000000000000001 idx 000
[01] commit pc 0000000010000004 inst 01f29293 wen 1 dst 05 data 0000000080000000 idx 001
[02] commit pc 0000000010000008 inst 00028067 wen 0 dst 00 data 0000000000000000 idx 002
[03] commit pc 0000000080000000 inst f14022f3 wen 1 dst 05 data 0000000000000000 idx 003
[04] commit pc 0000000080000004 inst 00000313 wen 1 dst 06 data 0000000000000000 idx 004
[05] commit pc 0000000080000006 inst 00628263 wen 0 dst 04 data 0000000000000000 idx 005
[06] commit pc 000000008000000a inst 00000797 wen 1 dst 15 data 000000008000000a idx 006
[07] commit pc 000000008000000e inst 00c78793 wen 1 dst 15 data 0000000080000016 idx 007
[08] commit pc 0000000080000012 inst 00078067 wen 0 dst 00 data 0000000000000000 idx 008
[09] commit pc 0000000080000016 inst fff0029b wen 1 dst 05 data ffffffffffffffff idx 009
[10] commit pc 0000000080000026 inst 12d28293 wen 1 dst 05 data 800000000084112d idx 00a
[11] commit pc 000000008000002a inst 30129073 wen 0 dst 00 data 0000000000000000 idx 00b
[12] commit pc 000000008000002e inst 00036897 wen 1 dst 17 data 000000008003602e idx 00c
[13] commit pc 0000000080000032 inst cca88893 wen 1 dst 17 data 0000000080035cf8 idx 00d
[14] commit pc 0000000080000036 inst 00010297 wen 1 dst 05 data 0000000080010036 idx 00e
[15] commit pc 000000008000003a inst fca28293 wen 1 dst 05 data 0000000080010000 idx 00f
[16] commit pc 0000000080000042 inst 30529073 wen 0 dst 00 data 0000000000000000 idx 010
[17] commit pc 0000000080000046 inst 00000297 wen 1 dst 05 data 0000000080000046 idx 011
[18] commit pc 000000008000004a inst 02c28293 wen 1 dst 05 data 0000000080000072 idx 012
[19] commit pc 000000008000004e inst 34129073 wen 0 dst 00 data 0000000000000000 idx 013
[20] commit pc 0000000080000052 inst 00000013 wen 0 dst 00 data 0000000000000000 idx 014
[21] commit pc 0000000080000054 inst 005002b7 wen 1 dst 05 data 0000000000500000 idx 015
[22] commit pc 0000000080000058 inst 0012829b wen 1 dst 05 data 0000000000500001 idx 016
[23] commit pc 0000000080000060 inst 30029073 wen 0 dst 00 data 0000000000000000 idx 017
[24] commit pc 0000000080000064 inst 000012b7 wen 1 dst 05 data 0000000000000b0b idx 018
[25] commit pc 000000008000006a inst 30429073 wen 0 dst 00 data 0000000000000000 idx 019
[26] commit pc 000000008000006e inst 30200073 wen 0 dst 00 data 0000000000000000 idx 01a
[27] commit pc 0000000080000072 inst 7bac2037 wen 0 dst 00 data 0000000000000000 idx 01b
[28] commit pc 000000008000007a inst 000f20b7 wen 1 dst 01 data 00000000000f2613 idx 01c
[29] commit pc 0000000080000082 inst 00c09093 wen 1 dst 01 data 00000000f2613000 idx 01d
[30] commit pc 0000000080000088 inst 00500113 wen 1 dst 02 data 0000000000000005 idx 01e
[31] commit pc 0000000080000090 inst 00000213 wen 1 dst 04 data 0000000000000000 idx 01f
[32] commit pc 0000000080000092 inst 0010029b wen 1 dst 05 data 0000000000000001 idx 020
[33] commit pc 0000000080000098 inst 11ce8337 wen 1 dst 06 data 0000000011ce7f26 idx 021
[34] commit pc 00000000800000a0 inst 00000393 wen 1 dst 07 data 0000000000000000 idx 022
[35] commit pc 00000000800000a6 inst b314041b wen 1 dst 08 data 00000000000f2b31 idx 023
[36] commit pc 00000000800000b0 inst 00000493 wen 1 dst 09 data 0000000000000000 idx 024
[37] commit pc 00000000800000b2 inst 0005c537 wen 1 dst 10 data 000000000005bd8f idx 025
[38] commit pc 00000000800000ba inst 00d51513 wen 1 dst 10 data 00000000b7b1e000 idx 026
[39] commit pc 00000000800000c6 inst 00000693 wen 1 dst 13 data 0000000000000000 idx 027
[40] commit pc 00000000800000c8 inst 6c6da737 wen 1 dst 14 data 000000006c6d9c3f idx 028
[41] commit pc 00000000800000d0 inst 00000793 wen 1 dst 15 data 0000000000000000 idx 029
[42] commit pc 00000000800000d2 inst 0010081b wen 1 dst 16 data 0000000000000001 idx 02a
[43] commit pc 00000000800000d8 inst 00078937 wen 1 dst 18 data 0000000000078567 idx 02b
[44] commit pc 00000000800000e0 inst 00d91913 wen 1 dst 18 data 00000000f0ace000 idx 02c
[45] commit pc 00000000800000e6 inst 00000993 wen 1 dst 19 data 0000000000000000 idx 02d
[46] commit pc 00000000800000ee inst 000fdab7 wen 1 dst 21 data 00000000000fd000 idx 02e
[47] commit pc 00000000800000f2 inst 55da8a9b wen 1 dst 21 data 00000000000fd55d idx 02f
[48] commit pc 00000000800000fc inst 1afc9b37 wen 1 dst 22 data 000000001afc8986 idx 000
[49] commit pc 0000000080000104 inst 00f00b93 wen 1 dst 23 data 000000000000000f idx 001
[50] commit pc 0000000080000106 inst 00000c13 wen 1 dst 24 data 0000000000000000 idx 002
[51] commit pc 000000008000010a inst 000f6d37 wen 1 dst 26 data 00000000000f66f1 idx 003
[52] commit pc 0000000080000112 inst 00cd1d13 wen 1 dst 26 data 00000000f66f1000 idx 004
[53] commit pc 000000008000011a inst 000f2eb7 wen 1 dst 29 data 00000000000f1e93 idx 005
[54] commit pc 0000000080000122 inst 00ce9e93 wen 1 dst 29 data 00000000f1e93000 idx 006
[55] commit pc 000000008000012a inst 0007bfb7 wen 1 dst 31 data 000000000007b787 idx 007
[56] commit pc 0000000080000132 inst 00df9f93 wen 1 dst 31 data 00000000f6f0e000 idx 008
[57] commit pc 0000000080000138 inst 0002e617 wen 1 dst 12 data 000000008002e138 idx 009
[58] commit pc 000000008000013c inst b0060613 wen 1 dst 12 data 000000008002dc38 idx 00a
[59] commit pc 0000000080000148 inst 01a6a5b3 wen 1 dst 11 data 0000000000000001 idx 00b
[60] commit pc 000000008000014c inst 0000be97 wen 1 dst 29 data 000000008000b14c idx 00c
[61] commit pc 0000000080000150 inst 59ce8e93 wen 1 dst 29 data 000000008000b6e8 idx 00d
[62] commit pc 0000000080000154 inst 01851e13 wen 1 dst 28 data 00b7b1db33000000 idx 00e
[63] commit pc 000000008000016a inst 00dd5463 wen 0 dst 08 data 0000000000000000 idx 00f
[64] commit pc 0000000080000172 inst db4e8967 wen 1 dst 18 data 0000000080000176 idx 010
[65] commit pc 000000008000b6e8 inst 00000013 wen 0 dst 00 data 0000000000000000 idx 011
[66] commit pc 000000008000b6ea inst 00030067 wen 0 dst 00 data 0000000000000000 idx 012
[67] exception pc 0000000011ce7f26 inst 00000000 cause 0000000000000002

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x000000008000b6e8
the first commit instr pc of REF is 0x000000008000b6e8

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0080000064 cmtcnt 2
commit group [01]: pc 008000006a cmtcnt 1
commit group [02]: pc 008000006e cmtcnt 1
commit group [03]: pc 0080000072 cmtcnt 4
commit group [04]: pc 0080000082 cmtcnt 6
commit group [05]: pc 0080000092 cmtcnt 6
commit group [06]: pc 00800000a6 cmtcnt 6
commit group [07]: pc 00800000ba cmtcnt 5
commit group [08]: pc 00800000c8 cmtcnt 13
commit group [09]: pc 00800000f2 cmtcnt 3
commit group [10]: pc 00800000fc cmtcnt 7
commit group [11]: pc 0080000112 cmtcnt 11
commit group [12]: pc 0080000132 cmtcnt 3
commit group [13]: pc 008000013c cmtcnt 13
commit group [14]: pc 0080000172 cmtcnt 1
commit group [15]: pc 008000b6e8 cmtcnt 2 <--

============== Commit Instr Trace ==============
[00] commit pc 00000000800000b0 inst 00000493 wen 1 dst 09 data 0000000000000000 idx 024
[01] commit pc 00000000800000b2 inst 0005c537 wen 1 dst 10 data 000000000005bd8f idx 025
[02] commit pc 00000000800000ba inst 00d51513 wen 1 dst 10 data 00000000b7b1e000 idx 026
[03] commit pc 00000000800000c6 inst 00000693 wen 1 dst 13 data 0000000000000000 idx 027
[04] commit pc 00000000800000c8 inst 6c6da737 wen 1 dst 14 data 000000006c6d9c3f idx 028
[05] commit pc 00000000800000d0 inst 00000793 wen 1 dst 15 data 0000000000000000 idx 029
[06] commit pc 00000000800000d2 inst 0010081b wen 1 dst 16 data 0000000000000001 idx 02a
[07] commit pc 00000000800000d8 inst 00078937 wen 1 dst 18 data 0000000000078567 idx 02b
[08] commit pc 00000000800000e0 inst 00d91913 wen 1 dst 18 data 00000000f0ace000 idx 02c
[09] commit pc 00000000800000e6 inst 00000993 wen 1 dst 19 data 0000000000000000 idx 02d
[10] commit pc 00000000800000ee inst 000fdab7 wen 1 dst 21 data 00000000000fd000 idx 02e
[11] commit pc 00000000800000f2 inst 55da8a9b wen 1 dst 21 data 00000000000fd55d idx 02f
[12] commit pc 00000000800000fc inst 1afc9b37 wen 1 dst 22 data 000000001afc8986 idx 000
[13] commit pc 0000000080000104 inst 00f00b93 wen 1 dst 23 data 000000000000000f idx 001
[14] commit pc 0000000080000106 inst 00000c13 wen 1 dst 24 data 0000000000000000 idx 002
[15] commit pc 000000008000010a inst 000f6d37 wen 1 dst 26 data 00000000000f66f1 idx 003
[16] commit pc 0000000080000112 inst 00cd1d13 wen 1 dst 26 data 00000000f66f1000 idx 004
[17] commit pc 000000008000011a inst 000f2eb7 wen 1 dst 29 data 00000000000f1e93 idx 005
[18] commit pc 0000000080000122 inst 00ce9e93 wen 1 dst 29 data 00000000f1e93000 idx 006
[19] commit pc 000000008000012a inst 0007bfb7 wen 1 dst 31 data 000000000007b787 idx 007
[20] commit pc 0000000080000132 inst 00df9f93 wen 1 dst 31 data 00000000f6f0e000 idx 008
[21] commit pc 0000000080000138 inst 0002e617 wen 1 dst 12 data 000000008002e138 idx 009
[22] commit pc 000000008000013c inst b0060613 wen 1 dst 12 data 000000008002dc38 idx 00a
[23] commit pc 0000000080000148 inst 01a6a5b3 wen 1 dst 11 data 0000000000000001 idx 00b
[24] commit pc 000000008000014c inst 0000be97 wen 1 dst 29 data 000000008000b14c idx 00c
[25] commit pc 0000000080000150 inst 59ce8e93 wen 1 dst 29 data 000000008000b6e8 idx 00d
[26] commit pc 0000000080000154 inst 01851e13 wen 1 dst 28 data 00b7b1db33000000 idx 00e
[27] commit pc 000000008000016a inst 00dd5463 wen 0 dst 08 data 0000000000000000 idx 00f
[28] commit pc 0000000080000172 inst db4e8967 wen 1 dst 18 data 0000000080000176 idx 010
[29] commit pc 000000008000b6e8 inst 00000013 wen 0 dst 00 data 0000000000000000 idx 011
[30] commit pc 000000008000b6ea inst 00030067 wen 0 dst 00 data 0000000000000000 idx 012
[31] exception pc 0000000011ce7f26 inst 00000000 cause 0000000000000002 <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0x00000000f26132b8   sp: 0x0000000000000005   gp: 0x0000000080000000 
  tp: 0x0000000000000000   t0: 0x0000000080000000   t1: 0x0000000011ce7f26   t2: 0x0000000000000000 
  s0: 0x00000000f2b30d92   s1: 0x0000000000000000   a0: 0x00000000b7b1db33   a1: 0x0000000000000001 
  a2: 0x000000008002dc38   a3: 0x0000000000000000   a4: 0x000000006c6d9c3f   a5: 0x0000000000000000 
  a6: 0x000000003d180000   a7: 0x0000000080035cf8   s2: 0x0000000080000176   s3: 0x0000000000000000 
  s4: 0x0000000080000000   s5: 0x00000000fd55ce45   s6: 0x000000001afc8986   s7: 0x000000000000000f 
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x00000000f66f0ffd  s11: 0x0000000000000003 
  t3: 0x00b7b1db33000000   t4: 0x000000008000b935   t5: 0xfffffffffff55739   t6: 0x0000000000000000 
---------------- Float Registers ----------------
 ft0: 0x2641b32f9a9e66af  ft1: 0xea654dcc60849e5c  ft2: 0x07e4d4c5fa5cb960  ft3: 0x421a3f4f74cc75dc 
 ft4: 0x846ae16843b8faf9  ft5: 0xa10b1de7010039e9  ft6: 0xdd59e85fcc12f5cd  ft7: 0xfe9746f69606068b 
 fs0: 0xcaaa445e656b0e51  fs1: 0x5a78e905f02dd62d  fa0: 0x2c9fc9601bf07347  fa1: 0x40f32579a6a8c02d 
 fa2: 0x4992c905b7b93946  fa3: 0x86894f29e84ba9fa  fa4: 0xd2e3db02a28efc63  fa5: 0xa62aa76f1fcd369b 
 fa6: 0xbedda81cb7124690  fa7: 0xf99a5c881f58dfb0  fs2: 0xf1616326aee1d051  fs3: 0x8fd6b724678cf230 
 fs4: 0x00536c07ab231b6b  fs5: 0xb58ff1188b1454e1  fs6: 0x0802fa7a86631bb9  fs7: 0xbf658e57e30cf80e 
 fs8: 0xc20b956e49ce7ff8  fs9: 0xaf1a46a7ac6d5ea3 fs10: 0x44d07f42a6975746 fs11: 0x783f93e0531fd3b7 
 ft8: 0xb010451bf7680f81  ft9: 0x411517dca3a25372 ft10: 0xc08505f7a249f1d8 ft11: 0x14bb8c3c07383173 
 fcsr: 0x0000000000000000 fflags: 0x0000000000000000 frm: 0x0000000000000000
---------------- Privileged CSRs ----------------
pc: 0x0000000080010000  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x0000040a000018a2   sstatus: 0x0000000200000022  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000001      mepc: 0x0000000011ce7f26     mtval: 0x0000000011ce7f26
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0x0c41b9baf0093174  sscratch: 0xd2104cfd40bb6c85 vsscratch: 0xd91ce32238181f8b
     mtvec: 0x0000000080010001     stvec: 0x0000000000000000    vstvec: 0x0000000000000000
       mip: 0x0000000000000000       mie: 0x0000000000000a0a
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
pmp: 16 entries active, details:
 0: cfg:0x00 addr:0x0000000000000000| 1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000| 3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000| 5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000| 7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000| 9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000|11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000|13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000|15: cfg:0x00 addr:0x0000000000000000
---------------- PMA CSRs ----------------
pma: 16 entries active, details:
 0: cfg:0x00 addr:0x0000000000000000| 1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000| 3: cfg:0x0b addr:0x0000000004000000
 4: cfg:0x0f addr:0x0000000008000000| 5: cfg:0x0b addr:0x000000000c004000
 6: cfg:0x0b addr:0x000000000c014000| 7: cfg:0x0b addr:0x000000000e008000
 8: cfg:0x0f addr:0x000000000e008400| 9: cfg:0x0b addr:0x000000000e008800
10: cfg:0x0b addr:0x000000000e400000|11: cfg:0x0b addr:0x000000000e400800
12: cfg:0x08 addr:0x000000000e800000|13: cfg:0x0b addr:0x0000000020000000
14: cfg:0x6f addr:0x0000020000000000|15: cfg:0x18 addr:0x00001fffffffffff
---------------- Vector Registers ----------------
v0 : 0xefd16526f9c08db1_5179b7e68f36afe1  v1 : 0x57b16f7165e1d586_fff71b246887dd2e  
v2 : 0x9ab02a313fcf314b_8eb77154c855ff3a  v3 : 0x180295ab3ce38655_81774021d295f2df  
v4 : 0x49450293106110d6_47c2cae9c768d195  v5 : 0xe9e3d6a64398bd1d_eaea959264f2bfc9  
v6 : 0xbbe286da52cd4508_9010a45e79aaafd7  v7 : 0x0b40c6f1521d1591_829d97065f6fb6db  
v8 : 0x2114255ee7e69b5b_618f88b027ef7672  v9 : 0x62a998d10e6d60e6_03a31585a13eac87  
v10: 0x175b9d7efa939074_7f0694106e2f0192  v11: 0xa7034dfe41d31f6f_44622727fe095824  
v12: 0x23d9c8b93485de39_152776ee36e1969f  v13: 0xdd58e681994afd68_1580516ba3c3853b  
v14: 0xff16afeaf1fc786d_7174a033d0703370  v15: 0xcebe9d80399622fc_4f7bcc9da81a5dad  
v16: 0x02ac57a88b0c3b45_7e4a5a8e1e1d5f37  v17: 0xc48ead593dff00ee_0f82f9a6d72c2d48  
v18: 0x76064115629f253d_01fd7a607e5d03bb  v19: 0x0b20392103948d21_c8d042ba8c6afe56  
v20: 0xe96b883098132e35_4d73e6424d150792  v21: 0xfd744acc00dc0964_7d43a85114ca7692  
v22: 0x9fe0e9de855db6d8_1d14f6743974868a  v23: 0x1d0215db1bdbefdf_dbd97bea90016ce3  
v24: 0x1fff56b43796a654_2e110a469b724eae  v25: 0xcd9f3d68b873dd96_6262da8516ad9ec5  
v26: 0xc38ffef10937c507_de9aefa2a2ea75d0  v27: 0x477483d368c62a96_4ac78d83e3c69fb9  
v28: 0xa46609371673096c_4b6cd6537118633e  v29: 0xe20fec0ad0cc8b6c_46f0d6d6b7b6d5c1  
v30: 0x4b3196e4a12804bb_04d344c1b70a804b  v31: 0x426c3a9323c5266b_5e042f25aa61da5c  
  vtype: 0x8000000000000000 vstart: 0x0000000000000000  vxsat: 0x0000000000000001
   vxrm: 0x0000000000000001     vl: 0x0000000000000000   vcsr: 0x0000000000000003
---------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 1: tdata1: 0xf000000000000000 tdata2: 0x5f70696b735f7473
 2: tdata1: 0xf000000000000000 tdata2: 0x722d6f742d796461
 3: tdata1: 0xf000000000000000 tdata2: 0x2d756d656e2d3436
 4: tdata1: 0x6572707265746e69 tdata2: 0x6f006f732d726574
privilegeMode: 3
  mtval different at pc = 0x008000b6e8, right= 0x0000000011ce7f26, wrong = 0x0000000000000000
 mcause different at pc = 0x008000b6e8, right= 0x0000000000000001, wrong = 0x0000000000000002
Core 0: [31mABORT at pc = 0x8000006e
[0m[35mCore-0 instrCnt = 116, cycleCnt = 2,616, IPC = 0.044343
[0m[34mSeed=0 Guest cycle spent: 2,620 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 10,159ms
[0m`

### Expected behavior

In this case, when NEMU and Xiangshan execute a illegal instruction, both of them should report a illegel instruction. Xiangshan has the right behavior but NEMU reports a Instruction access fault.

### To Reproduce

`./build/emu -i ./2025-09-15-11-06-03_9578 -I 10000 -C 10000 --dump-wave-full --diff ./ready-to-run/riscv64-nemu-interpreter-so --wave-path run_wave.vcd --dump-commit-trace --dump-ref-trace -b 0`

### Environment

- XiangShan branch: master
- XiangShan commit id:b096858e571c77e75f704785e81f533b2ff682e4
- XiangShan config: make emu CONFIG=MinimalConfig EMU_TRACE=1 -j15
- NEMU commit id: -
- SPIKE commit id: -
The NEMU is available in the submodule 'ready_to_run'


### Additional context

[2025-09-15-11-06-03_9578.zip](https://github.com/user-attachments/files/22897263/2025-09-15-11-06-03_9578.zip), This is the testcase we use to do the verification.
