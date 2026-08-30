### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

emu compiled at Aug  6 2025, 10:41:03
Using simulated 32768B flash
Core  0's Commit SHA is: 7189933c87, dirty: 0
Using simulated 8386560MB RAM
The image is output_8_15/.input_0.bin
The reference model is /nfs/home/changgen/xs-env/NEMU/ready-to-run/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x00000000800003b4
the first commit instr pc of REF is 0x00000000800003b4

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0080000360 cmtcnt 2
commit group [01]: pc 0080000366 cmtcnt 1
commit group [02]: pc 008000036a cmtcnt 1
commit group [03]: pc 008000036e cmtcnt 1
commit group [04]: pc 0080000370 cmtcnt 2
commit group [05]: pc 00800003a6 cmtcnt 1
commit group [06]: pc 0080000120 cmtcnt 1
commit group [07]: pc 0080000124 cmtcnt 1
commit group [08]: pc 0080000126 cmtcnt 1
commit group [09]: pc 008000012a cmtcnt 1
commit group [10]: pc 00800003ae cmtcnt 1
commit group [11]: pc 0080000120 cmtcnt 1
commit group [12]: pc 0080000124 cmtcnt 1
commit group [13]: pc 0080000126 cmtcnt 1
commit group [14]: pc 008000012a cmtcnt 1
commit group [15]: pc 00800003b4 cmtcnt 2 <--

============== Commit Instr Trace ==============
[00] commit pc 000000008000032a inst 00000117 wen 1 dst 02 data 000000008000032a idx 085
[01] commit pc 000000008000032e inst 08e10113 wen 1 dst 02 data 00000000800003b8 idx 086
[02] commit pc 0000000080000332 inst ffc16083 wen 1 dst 01 data 00000000d90dd937 idx 087 (40)
[03] commit pc 0000000080000336 inst 01690ed3 wen 1 dst 29 data ffffffff7fc00000 idx 088
[04] commit pc 000000008000033e inst 00030717 wen 1 dst 14 data 000000008003033e idx 089
[05] commit pc 0000000080000342 inst da270713 wen 1 dst 14 data 00000000800300e0 idx 08a
[06] commit pc 0000000080000346 inst 01c71f03 wen 1 dst 30 data 0000000000006d17 idx 08b (41)
[07] commit pc 000000008000034a inst 20cb2dd3 wen 1 dst 27 data ffffffff7fc00000 idx 08c
[08] commit pc 000000008000034e inst 00030d17 wen 1 dst 26 data 000000008003034e idx 08d
[09] commit pc 0000000080000352 inst da2d0d13 wen 1 dst 26 data 00000000800300f0 idx 08e
[10] commit pc 0000000080000358 inst 81dd262f wen 1 dst 12 data 0000000071e90276 idx 08f
[11] commit pc 000000008000035c inst 00030597 wen 1 dst 11 data 000000008003035c idx 090
[12] commit pc 0000000080000360 inst d0458593 wen 1 dst 11 data 0000000080030060 idx 091
[13] commit pc 0000000080000366 inst a0d5a52f wen 1 dst 10 data ffffffffaa140e81 idx 092
[14] commit pc 000000008000036a inst 30431ff3 wen 1 dst 31 data 0000000000000000 idx 093
[15] commit pc 000000008000036e inst 000f8e93 wen 1 dst 29 data 0000000000000000 idx 094
[16] commit pc 0000000080000370 inst 01f12023 wen 0 dst 00 data 0000000000000000 idx 095 (00)
[17] commit pc 0000000080000372 inst 03675a63 wen 0 dst 20 data 0000000000000000 idx 096
[18] commit pc 00000000800003a6 inst 30005073 wen 0 dst 00 data 0000000000000000 idx 097
[19] exception pc 00000000800003aa inst f134df73 cause 0000000000000002
[20] commit pc 0000000080000120 inst 34102373 wen 1 dst 06 data 00000000800003aa idx 098
[21] commit pc 0000000080000124 inst 00430313 wen 1 dst 06 data 00000000800003ae idx 099
[22] commit pc 0000000080000126 inst 34131073 wen 0 dst 00 data 0000000000000000 idx 09a
[23] commit pc 000000008000012a inst 30200073 wen 0 dst 00 data 0000000000000000 idx 09b
[24] commit pc 00000000800003ae inst 01e12023 wen 0 dst 00 data 0000000000000000 idx 09c (01)
[25] exception pc 00000000800003b0 inst c0210053 cause 0000000000000002
[26] commit pc 0000000080000120 inst 34102373 wen 1 dst 06 data 00000000800003b0 idx 09d
[27] commit pc 0000000080000124 inst 00430313 wen 1 dst 06 data 00000000800003b4 idx 09e
[28] commit pc 0000000080000126 inst 34131073 wen 0 dst 00 data 0000000000000000 idx 09f
[29] commit pc 000000008000012a inst 30200073 wen 0 dst 00 data 0000000000000000 idx 000
[30] commit pc 00000000800003b4 inst d90dd937 wen 1 dst 18 data ffffffffd90dd000 idx 001
[31] commit pc 00000000800003b8 inst 00002097 wen 1 dst 01 data 00000000800023b8 idx 002 <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0x00000000d90dd937   sp: 0x00000000800003b8   gp: 0x0000000000000000 
  tp: 0x7412d2ae0bc04b71   t0: 0x0000000000000000   t1: 0x00000000800003b4   t2: 0x6bea44b0a66be6d0 
  s0: 0x000000000000c4f2   s1: 0x8fdd02f6ee86deba   a0: 0xffffffffaa140e81   a1: 0x000000008003005c 
  a2: 0x0000000071e90276   a3: 0xbc17ea79a6768bb5   a4: 0x00000000800300e0   a5: 0xe62b46f9001751d7 
  a6: 0x181c186f5e6e65bf   a7: 0xbc3f3c8176569d27   s2: 0xffffffffd90dd000   s3: 0x7fffffffffffffff 
  s4: 0x915badc60423b7c4   s5: 0xa01b61d4d8cd10e9   s6: 0x8c4bcf49c93e9228   s7: 0xe3b213192d554b55 
  s8: 0xf2277923b7bcd1d5   s9: 0xc4f51096fef1d308  s10: 0x00000000800063b8  s11: 0x0000000080030080 
  t3: 0xd827af5a7f01e589   t4: 0x0000000000000000   t5: 0x0000000000006d17   t6: 0x0000000000000000 
---------------- Float Registers ----------------
 ft0: 0xffffffff51a05b0e  ft1: 0xffffffffb7f59bac  ft2: 0xffffffff11266ab9  ft3: 0xa63fddd0aac5dc40 
 ft4: 0xdf322d240a555883  ft5: 0xc3e96d350d957443  ft6: 0x7e1cfe9f1f1ee699  ft7: 0xffffffffced0c118 
 fs0: 0xc3e46c9d5ded700f  fs1: 0xd7623c6a91bd9925  fa0: 0xffffffff1accc555  fa1: 0xb61c444eb1bd9955 
 fa2: 0x47010d3b7a27a6af  fa3: 0x7b547fc24f4d3320  fa4: 0xffffffff6318dbf9  fa5: 0x8958e82f859eabbf 
 fa6: 0x71bb2557559d4e9f  fa7: 0xffffffffb65626fd  fs2: 0xffffffff7fc00000  fs3: 0xffffffff7d3adadf 
 fs4: 0xffffffff7fc00000  fs5: 0xffffffff9e6df3cb  fs6: 0x71600e25f97fc46c  fs7: 0x355065d6fb26ac22 
 fs8: 0x7ccba90dfd104e0b  fs9: 0x3964eae863770e1e fs10: 0xf8f24ffd544ba9be fs11: 0xffffffff7fc00000 
 ft8: 0x59845fa609f21d16  ft9: 0xffffffff7fc00000 ft10: 0xffffffff5e9c2085 ft11: 0xffffffffa6c426d4 
 fcsr: 0x0000000000000010 fflags: 0x0000000000000010 frm: 0x0000000000000000
---------------- Privileged CSRs ----------------
pc: 0x00000000800003bc  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x0000000a00000080   sstatus: 0x0000000200000000  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000002      mepc: 0x00000000800003b4     mtval: 0x00000000c0210053
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0x7e1e3fc8269cb76c  sscratch: 0xa14889f6b86bb454 vsscratch: 0x422930e2bd357d3e
     mtvec: 0x0000000080000120     stvec: 0x0000000080000110    vstvec: 0x0000000000000000
       mip: 0x0000000000000000       mie: 0x000000000000020e
   mideleg: 0x0000000000001444   medeleg: 0x000000000000b109
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
 0: cfg:0x1f addr:0x00003fffffffffff| 1: cfg:0x00 addr:0x0000000000000000
 2: cfg:0x00 addr:0x0000000000000000| 3: cfg:0x00 addr:0x0000000000000000
 4: cfg:0x00 addr:0x0000000000000000| 5: cfg:0x00 addr:0x0000000000000000
 6: cfg:0x00 addr:0x0000000000000000| 7: cfg:0x00 addr:0x0000000000000000
 8: cfg:0x00 addr:0x0000000000000000| 9: cfg:0x00 addr:0x0000000000000000
10: cfg:0x00 addr:0x0000000000000000|11: cfg:0x00 addr:0x0000000000000000
12: cfg:0x00 addr:0x0000000000000000|13: cfg:0x00 addr:0x0000000000000000
14: cfg:0x00 addr:0x0000000000000000|15: cfg:0x00 addr:0x0000000000000000
---------------- Vector Registers ----------------
v0 : 0xa8dd7277b5261852_59035f00031f62fa  v1 : 0xe7fa11e12a56f0e6_8f138e8347aab2b1  
v2 : 0x7f3edde81ba956f4_59555f0732434acf  v3 : 0x13b83ab7e431f1c5_9e83632eaf253b07  
v4 : 0x93f2f02dbe080eb4_f10ccc9bc5d7fac7  v5 : 0xa38600247f926b65_2ab1c097ee514882  
v6 : 0xca448dfae264f162_5c0436e8be825c59  v7 : 0x126c3df16c09515a_0c93afa9d54da423  
v8 : 0x8076196f3af9b4c3_e5fc48445fb01858  v9 : 0xb885b736e35c3f57_98a7e3ee1e88be32  
v10: 0xd57bd208ebff280c_8a15376e135ac567  v11: 0x709dbe67bad6c48c_ce00582ae8691478  
v12: 0x8a8afc6497a3c0d9_a620f9ab1a926097  v13: 0xb5588bad1b855ef5_f3542d402d2f165d  
v14: 0xc6e1439f57c2a581_17ef02280a58a086  v15: 0x628dac50100d8661_fb2a9184245c8566  
v16: 0x09db6be50a9324f7_dca0f0f54ca9bda4  v17: 0x246f4e0b2d0ea775_dd3f06cc9ea2bb09  
v18: 0x17964da74f7a1f22_25785360b9aeb7f0  v19: 0xbeb438622b9523fa_46b738c45df2cd3a  
v20: 0xe5fe028d0b5cdaff_4aea758b3661aec4  v21: 0x8e0abcd08c925646_fe54bcfeac90eee6  
v22: 0x6067d72d112a6582_7e9e2a76cca8abe8  v23: 0xf2ae96326044801a_033849f35b975d4e  
v24: 0xcef3acb2701f7f4b_768392ca338bb770  v25: 0xd3edfb308c4a2981_c824130d1bd576f5  
v26: 0xef36843f137b4e20_bb9e5ce46b10252c  v27: 0x79f6dc7309f0b0ce_e05358f25505a4b7  
v28: 0xf39a39c13ec4b2a1_bcfc13fe2e350a5f  v29: 0x9afac068c4cd9109_996b4148f1df0b7f  
v30: 0x4eab781370a80c1b_97441b6399f539f7  v31: 0x730aec845830cdff_5158b255c2083ecb  
  vtype: 0x8000000000000000 vstart: 0x0000000000000000  vxsat: 0x0000000000000001
   vxrm: 0x0000000000000002     vl: 0x0000000000000000   vcsr: 0x0000000000000005
---------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 1: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 2: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 3: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 4: tdata1: 0x0000000000000000 tdata2: 0x0000000000000000
privilegeMode: 3
     ra different at pc = 0x00800003b4, right= 0x00000000d90dd937, wrong = 0x00000000800023b8
    s10 different at pc = 0x00800003b4, right= 0x00000000800063b8, wrong = 0x00000000800300e4
Core 0: [31mABORT at pc = 0x8000001c
[0m[35mCore-0 instrCnt = 178, cycleCnt = 5,302, IPC = 0.033572
[0m[34mSeed=0 Guest cycle spent: 5,306 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 36,839ms
[0m

### Expected behavior

pass

### To Reproduce

[testcase0.tar.gz](https://github.com/user-attachments/files/21791095/testcase0.tar.gz)

### Environment

- XiangShan branch: master
- XiangShan commit id: 7189933c87
- XiangShan config: KunminghuV2Config
- NEMU commit id: bbeddeac1d589852ccac9fb99cdcb1477e25b97e
- SPIKE commit id:


### Additional context

_No response_
