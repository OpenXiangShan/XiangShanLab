### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

[BOT] DUT and REFs disagree on s10, mcause, mtval values.

## Basic Information

- Testcase: `17d263580549badc58c55b660cb7c334`
- NOOP Commit: `5adaa47ba55b0b877063612d991a1fd250c741a5`
- AI Analysis (Possible Root Cause):
The discrepancy in s10 after the csrrwi instruction indicates inconsistent handling of custom CSR 0x7d6 between NEMU and Spike, likely due to undefined initial values for non-standard CSRs in reference models. Additionally, the DUT's exception handling for the unaligned instruction fetch at 0x8000000e violates the RISC-V privileged spec by storing the instruction word (ac2a4a0f) in mtval instead of the faulting address (8000000e) and reporting an access fault (cause=2) rather than address misalignment (cause=1), suggesting the DUT may skip alignment checks before permission validation.

## NEMU Diff

- Commit: `bbeddeac1d589852ccac9fb99cdcb1477e25b97e`
- Command: `./build/emu -i ./errors/17d263580549badc58c55b660cb7c334 --max-cycles 10000 --diff /home/xuyinan/xs2/NEMU/build/riscv64-nemu-interpreter-so`
- Return Code: `1`
### NEMU stdout
```
emu compiled at Aug 25 2025, 19:57:26
Using simulated 32768B flash
Core  0's Commit SHA is: 5adaa47ba5, dirty: 0
Using simulated 8386560MB RAM
The image is ./errors/17d263580549badc58c55b660cb7c334
The reference model is /home/xuyinan/xs2/NEMU/build/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 
[1;34m[src/memory/paddr.c:239,check_paddr] isa pma check failed[0m

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x000000008000000c
the first commit instr pc of REF is 0x000000008000000c

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0010000000 cmtcnt 1
commit group [01]: pc 0010000004 cmtcnt 1
commit group [02]: pc 0010000008 cmtcnt 1
commit group [03]: pc 0080000000 cmtcnt 1
commit group [04]: pc 0080000002 cmtcnt 2
commit group [05]: pc 0080000008 cmtcnt 1
commit group [06]: pc 008000000c cmtcnt 1 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000010000000 inst 0010029b wen 1 dst 05 data 0000000000000001 idx 000 addiw   t0, zero, 1
[01] commit pc 0000000010000004 inst 01f29293 wen 1 dst 05 data 0000000080000000 idx 001 slli    t0, t0, 31
[02] commit pc 0000000010000008 inst 00028067 wen 0 dst 00 data 0000000000000000 idx 002 jr      t0
[03] commit pc 0000000080000000 inst 00b71123 wen 0 dst 02 data 0000000000000000 idx 003 (00) (S) sh      a1, 2(a4)
[04] commit pc 0000000080000002 inst 05d12c23 wen 0 dst 24 data 0000000000000000 idx 004 (01) (S) sw      t4, 88(sp)
[05] commit pc 0000000080000004 inst 6007001b wen 0 dst 00 data 0000000000000000 idx 005 addiw   zero, a4, 1536
[06] commit pc 0000000080000008 inst 7d64dd73 wen 1 dst 26 data 0000020000000000 idx 006 csrrwi  s10, unknown_7d6, 9
[07] commit pc 000000008000000c inst 00d7b423 wen 0 dst 08 data 0000000000000000 idx 007 (02) (S) sd      a3, 8(a5)
[08] exception pc 000000008000000e inst ac2a4a0f cause 0000000000000002 unknown <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0x0000000000000000   sp: 0x0000000000000000   gp: 0x0000000000000000 
  tp: 0x0000000000000000   t0: 0x0000000080000000   t1: 0x0000000000000000   t2: 0x0000000000000000 
  s0: 0x0000000000000000   s1: 0x0000000000000000   a0: 0x0000000000000000   a1: 0x0000000000000000 
  a2: 0x0000000000000000   a3: 0x0000000000000000   a4: 0x0000000000000000   a5: 0x0000000000000000 
  a6: 0x0000000000000000   a7: 0x0000000000000000   s2: 0x0000000000000000   s3: 0x0000000000000000 
  s4: 0x0000000000000000   s5: 0x0000000000000000   s6: 0x0000000000000000   s7: 0x0000000000000000 
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x0000020000000000  s11: 0x0000000000000000 
  t3: 0x0000000000000000   t4: 0x0000000000000000   t5: 0x0000000000000000   t6: 0x0000000000000000 
---------------- Float Registers ----------------
 ft0: 0x83720928b14f7ede  ft1: 0x06e02a1b9ad95e1a  ft2: 0x0c6d91c161353d9d  ft3: 0x05bc7aba82c679d0 
 ft4: 0x2ab3fcde459dae4f  ft5: 0x7c68394e98cce9fb  ft6: 0x932424c2e84bdbda  ft7: 0x851107f32494b32d 
 fs0: 0x68bbeb876e327430  fs1: 0x8b6dac8280448a62  fa0: 0xf72ac676c6eaf24e  fa1: 0x2ff97251189f6b2b 
 fa2: 0xdcc4e347dc327d80  fa3: 0xeddb1a97ae3dc698  fa4: 0xbe1f6612f6c63f99  fa5: 0x7d886c5e19c30c8c 
 fa6: 0x7be5468818d23360  fa7: 0xde5b10ac25491097  fs2: 0xadf066e1bb240882  fs3: 0x6345eddcff7b5cca 
 fs4: 0xbac25b1fa93eccaa  fs5: 0x5e827755d5e29523  fs6: 0xb6fd9c38870fcc27  fs7: 0xb2cded3c50a4037e 
 fs8: 0xdc05d41ba91a6f1c  fs9: 0xe88ee5eef3674f19 fs10: 0x0000000000000000 fs11: 0xb1b119ca0bbd1a9b 
 ft8: 0xdc536ea7ee9bb03d  ft9: 0x8e0844084359ba56 ft10: 0xeab2f59456a27122 ft11: 0x847591c49bc71b70 
 fcsr: 0x0000000000000000 fflags: 0x0000000000000000 frm: 0x0000000000000000
---------------- Privileged CSRs ----------------
pc: 0x0000000000000000  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x0000040a00001800   sstatus: 0x0000000200000000  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000001      mepc: 0x000000008000000e     mtval: 0x000000008000000e
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0xb31f54078bf0a73c  sscratch: 0xd57a0802bea3589e vsscratch: 0xc6ef3d8452df952f
     mtvec: 0x0000000000000000     stvec: 0x0000000000000000    vstvec: 0x0000000000000000
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
14: cfg:0x6f addr:0x0000000000000009|15: cfg:0x18 addr:0x00001fffffffffff
---------------- Vector Registers ----------------
v0 : 0xd65b578b62ebb4ee_97441b635158b255  v1 : 0x3193c904f8a8fd27_9cf33081b5f52dc7  
v2 : 0x5d9384479f8ebf13_489c4012299b5df5  v3 : 0x2d2e6ed100dfc61c_5e0f06217c56e448  
v4 : 0xee8b177098867afe_fb80a977f16f9361  v5 : 0xde819994a5fcb1fa_5b1ab0b54f262737  
v6 : 0x95659d6091464778_9efd67261798c1c7  v7 : 0x4ec948f70ccfb300_b68cbbf0c422036e  
v8 : 0x2442135de09ca5ae_68ef43d1ff008526  v9 : 0xce84913ad0812913_c4e4e436a3cdeedf  
v10: 0xa4749f6e6a00457b_eb8ae87d0e8b3904  v11: 0xa3fbeccbc2388a92_00d61ce2c8d0be4b  
v12: 0x958c831edc3d18f7_b2ce521d12bdc3dc  v13: 0xf67eea2184e2c3d1_0dfcc4854b3cfa32  
v14: 0x37ff3c4fa0317cb8_23acce7aa7646e75  v15: 0xe245998903c440d7_91352d2fdc1c66e9  
v16: 0x6cf9a9a8546b726a_d9a4c8fd3741201b  v17: 0x9ec317e69fb2ebc6_d104c8b51f4b6e19  
v18: 0x678b2c18d4f3ab64_2da17e845935e699  v19: 0x4958133840951633_3f73dec71c5efabf  
v20: 0x081d800b91fc5716_43357f6460f78c67  v21: 0xe603a4e118757114_44da037028a626a9  
v22: 0x43dc16d80c1b0c3e_85e674dd227d92d3  v23: 0x8f6bee861874485c_948c165e2db27ad1  
v24: 0x16cd070497a28ca8_7ff4db1d1e33ecbb  v25: 0xdc54cc960598462d_13644d0a7af88037  
v26: 0x9ee76ff3582816f6_d549ed4f9d988cc6  v27: 0xcd6ef43b0b11f331_3311d31c7ac8bff5  
v28: 0xbd2e62b391017f05_46daae6dc9bee4b0  v29: 0x22a56c685fe7d3f4_2f9a4752e6b6a18d  
v30: 0xb82fd02bea44893d_2fa2ae7ed37cd259  v31: 0xd5c291d1c93eabff_dbe4abc4f86821a9  
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
  mtval different at pc = 0x008000000c, right= 0x000000008000000e, wrong = 0x00000000ac2a4a0f
 mcause different at pc = 0x008000000c, right= 0x0000000000000001, wrong = 0x0000000000000002
Core 0: [31mABORT at pc = 0x8000001e
[0m[35mCore-0 instrCnt = 8, cycleCnt = 4,355, IPC = 0.001837
[0mSimMemory: img_size 9442, req_all 128, req_in_range 128
[34mSeed=0 Guest cycle spent: 4,359 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 1,443ms
[0m
```

## spke Diff

- Commit: `bdd447fc34a34146471afc4f43f8df0e96666b47`
- Command: `./build/emu -i ./errors/17d263580549badc58c55b660cb7c334 --max-cycles 10000 --diff /home/xuyinan/xs2/riscv-isa-sim/difftest/build/riscv64-spike-so`
- Return Code: `1`
### Spike stdout
```
emu compiled at Aug 25 2025, 19:57:26
Using simulated 32768B flash
Core  0's Commit SHA is: 5adaa47ba5, dirty: 0
Using simulated 8386560MB RAM
The image is ./errors/17d263580549badc58c55b660cb7c334
The reference model is /home/xuyinan/xs2/riscv-isa-sim/difftest/build/riscv64-spike-so
The first instruction of core 0 has commited. Difftest enabled. 

==============  In the last commit group  ==============
the first commit instr pc of DUT is 0x0000000080000008
the first commit instr pc of REF is 0x0000000080000008

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0010000000 cmtcnt 1
commit group [01]: pc 0010000004 cmtcnt 1
commit group [02]: pc 0010000008 cmtcnt 1
commit group [03]: pc 0080000000 cmtcnt 1
commit group [04]: pc 0080000002 cmtcnt 2
commit group [05]: pc 0080000008 cmtcnt 1 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000010000000 inst 0010029b wen 1 dst 05 data 0000000000000001 idx 000 addiw   t0, zero, 1
[01] commit pc 0000000010000004 inst 01f29293 wen 1 dst 05 data 0000000080000000 idx 001 slli    t0, t0, 31
[02] commit pc 0000000010000008 inst 00028067 wen 0 dst 00 data 0000000000000000 idx 002 jr      t0
[03] commit pc 0000000080000000 inst 00b71123 wen 0 dst 02 data 0000000000000000 idx 003 (00) (S) sh      a1, 2(a4)
[04] commit pc 0000000080000002 inst 05d12c23 wen 0 dst 24 data 0000000000000000 idx 004 (01) (S) sw      t4, 88(sp)
[05] commit pc 0000000080000004 inst 6007001b wen 0 dst 00 data 0000000000000000 idx 005 addiw   zero, a4, 1536
[06] commit pc 0000000080000008 inst 7d64dd73 wen 1 dst 26 data 0000020000000000 idx 006 csrrwi  s10, unknown_7d6, 9 <--

==============  REF Regs  ==============
zero: 0x0000000000000000   ra: 0x0000000000000000   sp: 0x0000000000000000   gp: 0x0000000000000000 
  tp: 0x0000000000000000   t0: 0x0000000080000000   t1: 0x0000000000000000   t2: 0x0000000000000000 
  s0: 0x0000000000000000   s1: 0x0000000000000000   a0: 0x0000000000000000   a1: 0x0000000000000000 
  a2: 0x0000000000000000   a3: 0x0000000000000000   a4: 0x0000000000000000   a5: 0x0000000000000000 
  a6: 0x0000000000000000   a7: 0x0000000000000000   s2: 0x0000000000000000   s3: 0x0000000000000000 
  s4: 0x0000000000000000   s5: 0x0000000000000000   s6: 0x0000000000000000   s7: 0x0000000000000000 
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x0000000000000000  s11: 0x0000000000000000 
  t3: 0x0000000000000000   t4: 0x0000000000000000   t5: 0x0000000000000000   t6: 0x0000000000000000 
 ft0: 0x83720928b14f7ede  ft1: 0x06e02a1b9ad95e1a  ft2: 0x0c6d91c161353d9d  ft3: 0x05bc7aba82c679d0 
 ft4: 0x2ab3fcde459dae4f  ft5: 0x7c68394e98cce9fb  ft6: 0x932424c2e84bdbda  ft7: 0x851107f32494b32d 
 fs0: 0x68bbeb876e327430  fs1: 0x8b6dac8280448a62  fa0: 0xf72ac676c6eaf24e  fa1: 0x2ff97251189f6b2b 
 fa2: 0xdcc4e347dc327d80  fa3: 0xeddb1a97ae3dc698  fa4: 0xbe1f6612f6c63f99  fa5: 0x7d886c5e19c30c8c 
 fa6: 0x7be5468818d23360  fa7: 0xde5b10ac25491097  fs2: 0xadf066e1bb240882  fs3: 0x6345eddcff7b5cca 
 fs4: 0xbac25b1fa93eccaa  fs5: 0x5e827755d5e29523  fs6: 0xb6fd9c38870fcc27  fs7: 0xb2cded3c50a4037e 
 fs8: 0xdc05d41ba91a6f1c  fs9: 0xe88ee5eef3674f19 fs10: 0x0000000000000000 fs11: 0xb1b119ca0bbd1a9b 
 ft8: 0xdc536ea7ee9bb03d  ft9: 0x8e0844084359ba56 ft10: 0xeab2f59456a27122 ft11: 0x847591c49bc71b70 
pc: 0x0000000000000000 mstatus: 0x0000040a00001800 mcause: 0x0000000000000002 mepc: 0x0000000080000008
                       sstatus: 0x0000000200000000 scause: 0x0000000000000000 sepc: 0x0000000000000000
satp: 0x0000000000000000
mip: 0x0000000000000000 mie: 0x0000000000000000 mscratch: 0xb31f54078bf0a73c sscratch: 0xb31f54078bf0a73c
mideleg: 0x0000000000001444 medeleg: 0x0000000000000000
mtval: 0x000000007d64dd73 stval: 0x0000000000000000 mtvec: 0x0000000000000000 stvec: 0x0000000000000000
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
    s10 different at pc = 0x0080000008, right= 0x0000000000000000, wrong = 0x0000020000000000
mstatus different at pc = 0x0080000008, right= 0x0000040a00001800, wrong = 0x0000000a00000000
   mepc different at pc = 0x0080000008, right= 0x0000000080000008, wrong = 0x0000000000000000
  mtval different at pc = 0x0080000008, right= 0x000000007d64dd73, wrong = 0x0000000000000000
 mcause different at pc = 0x0080000008, right= 0x0000000000000002, wrong = 0x0000000000000000
Core 0: [31mABORT at pc = 0xfffec077b2d3920c
[0m[35mCore-0 instrCnt = 7, cycleCnt = 4,280, IPC = 0.001636
[0mSimMemory: img_size 9442, req_all 128, req_in_range 128
[34mSeed=0 Guest cycle spent: 4,284 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 1,783ms
[0m
```



### Expected behavior

See above

### To Reproduce

[17d263580549badc58c55b660cb7c334.zip](https://github.com/user-attachments/files/22001919/17d263580549badc58c55b660cb7c334.zip)
[reasoning.txt](https://github.com/user-attachments/files/22001920/reasoning.txt)

### Environment

- XiangShan branch:
- XiangShan commit id:
- XiangShan config:
- NEMU commit id:
- SPIKE commit id:


### Additional context

Reported by a fuzzer
