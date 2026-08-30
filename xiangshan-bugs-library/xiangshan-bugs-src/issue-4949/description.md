### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

emu compiled at Aug  6 2025, 10:41:03
Using simulated 32768B flash
Core  0's Commit SHA is: 482b1daff8, dirty: 0
Using simulated 8386560MB RAM
The image is output_8_14/.input_0.bin
The reference model is /nfs/home/changgen/xs-env/NEMU/ready-to-run/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x0000000080000c3c
the first commit instr pc of REF is 0x0000000080000c3c

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0080000120 cmtcnt 1
commit group [01]: pc 0080000124 cmtcnt 1
commit group [02]: pc 0080000126 cmtcnt 1
commit group [03]: pc 008000012a cmtcnt 1
commit group [04]: pc 0080000120 cmtcnt 1
commit group [05]: pc 0080000124 cmtcnt 1
commit group [06]: pc 0080000126 cmtcnt 1
commit group [07]: pc 008000012a cmtcnt 1
commit group [08]: pc 0080000c1e cmtcnt 1
commit group [09]: pc 0080000c22 cmtcnt 1
commit group [10]: pc 0080000c26 cmtcnt 1
commit group [11]: pc 0080000c2a cmtcnt 1
commit group [12]: pc 0080000c2e cmtcnt 2
commit group [13]: pc 0080000c34 cmtcnt 1
commit group [14]: pc 0080000c38 cmtcnt 1
commit group [15]: pc 0080000c3c cmtcnt 6 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000080000120 inst 34102373 wen 1 dst 06 data 0000000080000c0e idx 033
[01] commit pc 0000000080000124 inst 00430313 wen 1 dst 06 data 0000000080000c12 idx 034
[02] commit pc 0000000080000126 inst 34131073 wen 0 dst 00 data 0000000000000000 idx 035
[03] commit pc 000000008000012a inst 30200073 wen 0 dst 00 data 0000000000000000 idx 036
[04] exception pc 0000000080000c12 inst c007ba73 cause 0000000000000002
[05] commit pc 0000000080000120 inst 34102373 wen 1 dst 06 data 0000000080000c12 idx 037
[06] commit pc 0000000080000124 inst 00430313 wen 1 dst 06 data 0000000080000c16 idx 038
[07] commit pc 0000000080000126 inst 34131073 wen 0 dst 00 data 0000000000000000 idx 039
[08] commit pc 000000008000012a inst 30200073 wen 0 dst 00 data 0000000000000000 idx 03a
[09] exception pc 0000000080000c16 inst c007ba73 cause 0000000000000002
[10] commit pc 0000000080000120 inst 34102373 wen 1 dst 06 data 0000000080000c16 idx 03b
[11] commit pc 0000000080000124 inst 00430313 wen 1 dst 06 data 0000000080000c1a idx 03c
[12] commit pc 0000000080000126 inst 34131073 wen 0 dst 00 data 0000000000000000 idx 03d
[13] commit pc 000000008000012a inst 30200073 wen 0 dst 00 data 0000000000000000 idx 03e
[14] exception pc 0000000080000c1a inst c007ba73 cause 0000000000000002
[15] commit pc 0000000080000120 inst 34102373 wen 1 dst 06 data 0000000080000c1a idx 03f
[16] commit pc 0000000080000124 inst 00430313 wen 1 dst 06 data 0000000080000c1e idx 040
[17] commit pc 0000000080000126 inst 34131073 wen 0 dst 00 data 0000000000000000 idx 041
[18] commit pc 000000008000012a inst 30200073 wen 0 dst 00 data 0000000000000000 idx 042
[19] commit pc 0000000080000c1e inst 0003f297 wen 1 dst 05 data 000000008003fc1e idx 043
[20] commit pc 0000000080000c22 inst 49228293 wen 1 dst 05 data 00000000800400b0 idx 044
[21] commit pc 0000000080000c26 inst ff52c203 wen 1 dst 04 data 0000000000000014 idx 045 (41)
[22] commit pc 0000000080000c2a inst 001de2f3 wen 1 dst 05 data 0000000000000011 idx 046
[23] commit pc 0000000080000c2e inst 00028993 wen 1 dst 19 data 0000000000000011 idx 047
[24] commit pc 0000000080000c34 inst 0002f417 wen 1 dst 08 data 000000008002fc34 idx 048
[25] commit pc 0000000080000c38 inst 54c40413 wen 1 dst 08 data 0000000080030180 idx 049
[26] commit pc 0000000080000c3c inst fed401a3 wen 0 dst 03 data 0000000000000000 idx 04a (02)
[27] commit pc 0000000080000c40 inst 41a68bbb wen 1 dst 23 data 000000000626c26a idx 04b
[28] commit pc 0000000080000c48 inst 00000d17 wen 1 dst 26 data 0000000080000c48 idx 04c
[29] commit pc 0000000080000c4c inst 044d0d13 wen 1 dst 26 data 0000000080000c8c idx 04d
[30] commit pc 0000000080000c50 inst 000d0667 wen 1 dst 12 data 0000000080000c54 idx 04e
[31] exception pc 0000000080000c8c inst 2500a373 cause 0000000000000002 <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0x0000000000000000   sp: 0xac2fd9e3ffae562e   gp: 0x7fffffffffffffff 
  tp: 0x0000000000000014   t0: 0x0000000000000011   t1: 0x0000000000000000   t2: 0xdcd2a7e9504eeeec 
  s0: 0x0000000080030180   s1: 0xffffffffcc4f1e0a   a0: 0x8818821a2d10d3a2   a1: 0x21e12a737cdda28c 
  a2: 0x0000000080000c54   a3: 0x0000000080000bea   a4: 0x0000000080020068   a5: 0xffffffff80030070 
  a6: 0x0000000080040100   a7: 0xfa18e4ae9a70ef1b   s2: 0x0000000000000075   s3: 0x0000000000000011 
  s4: 0x0668996fd2c54036   s5: 0x0000000080030070   s6: 0x3341167a12cd924c   s7: 0x000000000626c26a 
  s8: 0xffff876297037d18   s9: 0xabfb774ebe30c1b1  s10: 0x0000000080000c8c  s11: 0x876297037d181c1a 
  t3: 0xf2063b74cc4f1e0a   t4: 0x1b8a12f1159ed7b4   t5: 0x0000000080030050   t6: 0x0000000080020000 
---------------- Float Registers ----------------
 ft0: 0xffffffffa91c05a8  ft1: 0xffffffff0064aa50  ft2: 0xfffffffff851bb39  ft3: 0x89afb3790ee8154b 
 ft4: 0x41954502b28f6002  ft5: 0x43117459d14e2fbd  ft6: 0xa0ab1541b9826a72  ft7: 0xffffffff90696027 
 fs0: 0xae1c919889281f32  fs1: 0xffffffff00000000  fa0: 0xffffffff3e60cf5d  fa1: 0xb37803bb45c889f8 
 fa2: 0x0ed388ead011c89a  fa3: 0xdcd2a7e9504eeeec  fa4: 0xffffffffdcc4ce8f  fa5: 0x4c3e3579c76e245f 
 fa6: 0x04057fa30130c46a  fa7: 0xffffffff08d95db6  fs2: 0xffffffff7fc00000  fs3: 0xffffffffb460d642 
 fs4: 0x4a3bf0bf66e4d223  fs5: 0xffffffff5698ddd1  fs6: 0xee88584fc926b346  fs7: 0xb1c52ab2415d7a90 
 fs8: 0x0f3400914a56b714  fs9: 0x1a7d3269b45af6e5 fs10: 0xf96d7bd97eb4c168 fs11: 0xffffffff7d181c1a 
 ft8: 0xffffffffffc00000  ft9: 0xffffffffa3c25ea3 ft10: 0xffffffff1b3f5d01 ft11: 0xffffffff6f39bdef 
 fcsr: 0x000000000000001b fflags: 0x000000000000001b frm: 0x0000000000000000
---------------- Privileged CSRs ----------------
pc: 0x0000000080000c90  privilege mode: S (mode: 1  v: 0  debug: 0)
   mstatus: 0x8000000a00006080   sstatus: 0x8000000200006000  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000002      mepc: 0x0000000080000c1e     mtval: 0x00000000c007ba73
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0x7e1e3fc8269cb76c  sscratch: 0xa14889f6b86bb454 vsscratch: 0x422930e2bd357d3e
     mtvec: 0x0000000080000120     stvec: 0x0000000080000110    vstvec: 0x0000000000000000
       mip: 0x0000000000000000       mie: 0x0000000000000000
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
     t1 different at pc = 0x0080000c3c, right= 0x0000000000000000, wrong = 0x0000000080000c1e
   mode different at pc = 0x0080000c3c, right= 0x0000000000000001, wrong = 0x0000000000000003
mstatus different at pc = 0x0080000c3c, right= 0x8000000a00006080, wrong = 0x8000040a00006800
   mepc different at pc = 0x0080000c3c, right= 0x0000000080000c1e, wrong = 0x0000000080000c8c
  mtval different at pc = 0x0080000c3c, right= 0x00000000c007ba73, wrong = 0x000000002500a373
Core 0: [31mABORT at pc = 0x80000124
[0m[35mCore-0 instrCnt = 414, cycleCnt = 9,186, IPC = 0.045069
[0m[34mSeed=0 Guest cycle spent: 9,190 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 118,585ms
[0m

### Expected behavior

pass

### To Reproduce

[countertimer_216.tar.gz](https://github.com/user-attachments/files/21767573/countertimer_216.tar.gz)

### Environment

- XiangShan branch: master
- XiangShan commit id: 482b1daff8
- XiangShan config: KunminghuV2Config
- NEMU commit id: 
- SPIKE commit id:


### Additional context

_No response_
