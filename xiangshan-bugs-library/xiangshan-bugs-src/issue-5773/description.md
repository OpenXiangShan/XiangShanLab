### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

When executing a scalar halfword load with an unaligned effective address, XiangShan reports the wrong trap cause in `mcause`.

Representative instruction:
```assembly
flh fs3, 1(a7)
```

Representative context at exception:
- Log file: `divefuzz_ins_10_seeds_1041_.img.log`
- a7: `0x0000000000000000`
- Effective address: `a7 + 1 = 0x1`
- Access width: halfword (2 bytes), requiring 2-byte alignment

Observed mismatch:
```text
mcause different at pc = 0x0080001064, right = 0x0000000000000004, wrong = 0x0000000000000005
```


### Expected behavior

According to RISC-V Privileged Spec [Table 7. Synchronous exception priority in decreasing priority order.](https://docs.riscv.org/reference/isa/priv/machine.html#norm:exc_priority):

When a memory access instruction simultaneously triggers both a misaligned memory access exception and a load access fault exception, the misaligned memory access exception should be reported with higher priority.

**Expected Behavior(NEMU)**:
```text
mcause: 0x0000000000000004  (Load address misaligned)
```

**Actual Behavior(XiangShan)**:
```text
mcause: 0x0000000000000005  (Load access fault)
```

**mismatch details:**
```
emu compiled at Apr  6 2026, 02:30:36
Using simulated 32768B flash
Core  0's Commit SHA is: fb0b7e3ea7, dirty: 1
Using simulated 8386560MB RAM
The image is testcases/flh_mcause.img
The reference model is riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0080001000 cmtcnt 1
commit group [01]: pc 0080001004 cmtcnt 1
commit group [02]: pc 0080001008 cmtcnt 1
commit group [03]: pc 008000100c cmtcnt 1
commit group [04]: pc 0080001000 cmtcnt 1
commit group [05]: pc 0080001004 cmtcnt 1
commit group [06]: pc 0080001008 cmtcnt 1
commit group [07]: pc 008000100c cmtcnt 1
commit group [08]: pc 0080001054 cmtcnt 1
commit group [09]: pc 0080001058 cmtcnt 1
commit group [10]: pc 0080001000 cmtcnt 1
commit group [11]: pc 0080001004 cmtcnt 1
commit group [12]: pc 0080001008 cmtcnt 1
commit group [13]: pc 008000100c cmtcnt 1
commit group [14]: pc 0080001060 cmtcnt 1
commit group [15]: pc 0080001064 cmtcnt 1 <--

============== Commit Instr Trace ==============
[00] exception pc 000000008000103c inst b2d32d77 cause 0000000000000002 vghsh.vv v26, v13, v6
[01] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000103c idx 01e csrr    a3, mepc
[02] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001040 idx 01f addi    a3, a3, 4
[03] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001040 idx 020 csrw    mepc, a3
[04] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001040 idx 021 mret
[05] commit pc 0000000080001040 inst 214faa2f wen 1 dst 20 data 0000000000000000 idx 022 amoxor.w s4, s4, (t6)
[06] exception pc 0000000080001044 inst b06fd673 cause 0000000000000002 csrrwi  a2, mhpmcounter6, 31
[07] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 0000000080001044 idx 023 csrr    a3, mepc
[08] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001048 idx 024 addi    a3, a3, 4
[09] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001048 idx 025 csrw    mepc, a3
[10] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001048 idx 026 mret
[11] commit pc 0000000080001048 inst de9709b7 wen 1 dst 19 data ffffffffde970000 idx 027 lui     s3, 0xde970
[12] exception pc 000000008000104c inst d45cbcd7 cause 0000000000000002 vwsll.vi v25, v5, 25, v0.t
[13] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000104c idx 028 csrr    a3, mepc
[14] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001050 idx 029 addi    a3, a3, 4
[15] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001050 idx 02a csrw    mepc, a3
[16] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001050 idx 02b mret
[17] exception pc 0000000080001050 inst 479984a7 cause 0000000000000002 vsuxseg3ei8.v v9, (s3), v25
[18] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 0000000080001050 idx 02c csrr    a3, mepc
[19] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001054 idx 02d addi    a3, a3, 4
[20] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001054 idx 02e csrw    mepc, a3
[21] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001054 idx 02f mret
[22] commit pc 0000000080001054 inst 40da50b3 wen 1 dst 01 data 0000000000000000 idx 000 sra     ra, s4, a3
[23] commit pc 0000000080001058 inst 284a1393 wen 1 dst 07 data 0000000000000010 idx 001 bseti   t2, s4, 4
[24] exception pc 000000008000105c inst ba4caff7 cause 0000000000000002 vsha2ch.vv v31, v4, v25
[25] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000105c idx 002 csrr    a3, mepc
[26] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001060 idx 003 addi    a3, a3, 4
[27] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001060 idx 004 csrw    mepc, a3
[28] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001060 idx 005 mret
[29] commit pc 0000000080001060 inst 48bc9833 wen 1 dst 16 data 0000000000000000 idx 006 bclr    a6, s9, a1
[30] commit pc 0000000080001064 inst 801fb4af wen 1 dst 09 data 0000000000000000 idx 007 amomin.d s1, ra, (t6)
[31] exception pc 0000000080001068 inst 00189987 cause 0000000000000005 flh     fs3, 1(a7) <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0x0000000000000000   sp: 0xc4374a543d733bb7   gp: 0x51a3c5da2c255a8f 
  tp: 0x5849b0e95c45c8ba   t0: 0x9b9e1b92db5ee09d   t1: 0xba3a3cc129b4b4c2   t2: 0x0000000000000010 
  s0: 0xc8b630987d5b3123   s1: 0x0000000000000000   a0: 0x0a463486852ffe12   a1: 0x0374fb6984a031b0 
  a2: 0xb1ed6c72ff9a0db7   a3: 0x0000000080001060   a4: 0x8ec74510bba76dc5   a5: 0x19551e3e82a6cad5 
  a6: 0x0000000000000000   a7: 0x0000000000000000   s2: 0x0000000000000000   s3: 0xffffffffde970000 
  s4: 0x0000000000000000   s5: 0x0000000000000000   s6: 0x0000000000000000   s7: 0x0000000000000000 
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x0000000000000000  s11: 0x0000000000000600 
  t3: 0x0000000000000000   t4: 0x0000000000000000   t5: 0x0000000000000000   t6: 0x0000000080002070 
---------------- Float Registers ----------------
 ft0: 0x0000000000000000  ft1: 0xa95fd1f970dc2b0d  ft2: 0xc4374a543d733bb7  ft3: 0xffffffff2c255a8f 
 ft4: 0x5849b0e95c45c8ba  ft5: 0xffffffffffffe09d  ft6: 0xffffffffffffb4c2  ft7: 0xffffffff631bd7a4 
 fs0: 0xffffffff7d5b3123  fs1: 0xfffffffffb193648  fa0: 0x0a463486852ffe12  fa1: 0xffffffffffff31b0 
 fa2: 0xffffffffff9a0db7  fa3: 0xffffffff23e3a688  fa4: 0x8ec74510bba76dc5  fa5: 0xffffffffffffcad5 
 fa6: 0x156703ca5ed7557e  fa7: 0xffffffff00000013  fs2: 0x941d1edbef0f0a22  fs3: 0xee88cd0c7f71a378 
 fs4: 0x2a43e3b50899d0b9  fs5: 0x8f01cdd1865f0c82  fs6: 0x680056216b93da00  fs7: 0x2ac6d24536f9bb26 
 fs8: 0xce62b34329f01f0c  fs9: 0xbd3f95d2c6ca0c6a fs10: 0xf70be3466132c48d fs11: 0x4246502b34411727 
 ft8: 0x1141c23849a3d278  ft9: 0x98762e7440c155c1 ft10: 0xf1ffd2e1f166e0f5 ft11: 0x0ecdbd982576a4a0 
 fcsr: 0x0000000000000040 fflags: 0x0000000000000000 frm: 0x0000000000000002
---------------- Privileged CSRs ----------------
pc: 0x0000000080001000  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x8000040a00206120   sstatus: 0x8000000200006120  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000004      mepc: 0x0000000080001068     mtval: 0x0000000000000001
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0xd12fdb5644d90ee9  sscratch: 0xfe20b4e29c7aff24 vsscratch: 0xe2d872842db635bf
     mtvec: 0x0000000080001000     stvec: 0x0000000080001010    vstvec: 0x0000000000000000
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
 0: cfg:0x0f addr:0x000000002fffffff| 1: cfg:0x00 addr:0x0000000000000000
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
v0 : 0x40d684bd4869864e_933a8f6cca53ee2d  v1 : 0xa352582891e553b4_f6fa77f1c81f7dd4  
v2 : 0x605e706555f7a860_96fc703eb6c38d94  v3 : 0x3a2faccf88f1d289_20847e7606be887d  
v4 : 0x8f61549f7206a80d_a5534dbe2d494608  v5 : 0x274ba61216281af6_fd4f3a0bad5bc9d3  
v6 : 0x312916de98eab4e7_42ad8c11b880c2f7  v7 : 0xc33b6587c13ebc36_5f84bc904a24475e  
v8 : 0xe1dd57a300e46e57_51bd6a0d663db9de  v9 : 0xb0661952a23232cd_fc9c5e55945635f4  
v10: 0xd3def3721041a4f8_97c185ca2cba73e5  v11: 0x44f67581f1c70531_5de080a93409e4db  
v12: 0x5a1495e9017471b9_1b47fc7547301d8b  v13: 0x9b7a55295dc21901_db8c80ef5e697abe  
v14: 0x2bcd620c2b3bdec4_8d8667f98a995d6e  v15: 0xb994b41cb36c7734_d041aeffd28d0b75  
v16: 0xa5513f9b9b92d117_329b8e1fbf8a5361  v17: 0xc3cb4beb00c5ebd8_fbd1f3ca1d133ea1  
v18: 0x4f1f4b1690b83a06_6248950be5c263f4  v19: 0x78660a8b6ec8d4ed_de2b679038dc775b  
v20: 0x216f16361d091af7_159e1d210ee2d1d9  v21: 0x795d168d78741945_2098972ef68581dc  
v22: 0x3dbc301acc699808_dee16938a018c9ca  v23: 0xd7b384230a0aadd1_90da85f5a01cefaa  
v24: 0xd24a450333fd81e2_e11627112d99c78f  v25: 0xeee87911b73b0e2e_b11a169ca9907f51  
v26: 0xe81cee5eaad4f124_aa7e0045230e9720  v27: 0xbf49870d32070b43_56cd92b64bb90407  
v28: 0x37e3cfe621e7d91a_349024f6ab76b6b3  v29: 0xb8a3155d89f12c27_21505b3fbe60817b  
v30: 0xdeeaf8877dc147fe_ad09f2a9bd6b72a6  v31: 0xcbd012690d3a600d_f81ec038420a9867  
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
 mcause different at pc = 0x0080001064, right = 0x0000000000000004, wrong = 0x0000000000000005
Core 0: [31mABORT at pc = 0x80001084
[0m[35mCore-0 instrCnt = 215, cycleCnt = 2,294, IPC = 0.093723
[0m[34mSeed=0 Guest cycle spent: 2,298 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 3,934ms
[0m
```


### Environment

XiangShan:
- version: commit fb0b7e3ea7
- build command: `time make DISABLE_PERF=1 emu CONFIG=MinimalConfig EMU_THREADS=2 -j$(nproc)`

NEMU:
- version: commit d046c0e9d6560f9c2f068e3973c9a7f904d6881c
- config: riscv64-xs-ref_defconfig

For other environmental information, please refer to the compressed file.

### To Reproduce

```shell
./build/emu -i divefuzz_ins_10_seeds_1041_.img --diff riscv64-nemu-interpreter-so
```
where riscv64-nemu-interpreter-so is NEMU(commit: d046c0e9d6560f9c2f068e3973c9a7f904d6881c) built with riscv64-xs-ref_defconfig

[flh_mcause_mismatch.zip](https://github.com/user-attachments/files/26517616/flh_mcause_mismatch.zip)



### Additional context

_No response_
