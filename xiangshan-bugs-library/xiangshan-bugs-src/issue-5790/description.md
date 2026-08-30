### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

When a vector strided segment store instruction `vssseg3e16.v` triggers  Store/AMO Access Fault, NEMU reports Load Access Fault.

```assembly
vssseg3e16.v v10, (a3), t0, v0.t
```

Register values at exception:
- Base address (a3): 0x0000000080001270
- Stride (t0): 0x2652aaaaaaaaaaa9
- Vector length (vl): 16
- Element triggering fault: index 2
- Expected fault address: 0x4ca55555d55567c2 (calculated as: base + 2 × stride)

**But I'm not sure if this is a NEMU bug?**


### Expected behavior

**mismatch details:**
```
emu compiled at Apr  8 2026, 16:14:04
Using simulated 32768B flash
Core  0's Commit SHA is: 54944b0202, dirty: 0
Using simulated 8386560MB RAM
The image is testcases/vssseg3e16_mcause.img
The reference model is riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled. 
[1;34m[src/memory/paddr.c:250,check_paddr] isa pma check failed, vaddr=0x8000000000000023, paddr=0x8000000000000023, len=0x4, type=0x1, mode=0x3[0m

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 008000100c cmtcnt 1
commit group [01]: pc 008000124c cmtcnt 3
commit group [02]: pc 0080001000 cmtcnt 1
commit group [03]: pc 0080001004 cmtcnt 1
commit group [04]: pc 0080001008 cmtcnt 1
commit group [05]: pc 008000100c cmtcnt 1
commit group [06]: pc 008000125c cmtcnt 4
commit group [07]: pc 0080001000 cmtcnt 1
commit group [08]: pc 0080001004 cmtcnt 1
commit group [09]: pc 0080001008 cmtcnt 1
commit group [10]: pc 008000100c cmtcnt 1
commit group [11]: pc 0080001000 cmtcnt 1
commit group [12]: pc 0080001004 cmtcnt 1
commit group [13]: pc 0080001008 cmtcnt 1
commit group [14]: pc 008000100c cmtcnt 1
commit group [15]: pc 0080001274 cmtcnt 2 <--

============== Commit Instr Trace ==============
[00] exception pc 000000008000122c inst 86312377 cause 0000000000000002 vsm4k.vi v6, v3, 2
[01] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000122c idx 074 csrr    a3, mepc
[02] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001230 idx 075 addi    a3, a3, 4
[03] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001230 idx 076 csrw    mepc, a3
[04] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001230 idx 077 mret
[05] commit pc 0000000080001230 inst 413459b3 wen 1 dst 19 data 00000001abdfffff idx 078 sra     s3, s0, s3
[06] commit pc 000000008000123c inst 0000100f wen 0 dst 00 data 00000001abdfffff idx 079 fence.i
[07] commit pc 0000000080001240 inst 0000100f wen 0 dst 00 data 00000001abdfffff idx 07a fence.i
[08] commit pc 0000000080001244 inst cb4fa487 wen 1 dst 09 data ffffffff00130000 idx 07b (09) flw     fs1, -844(t6)
[09] exception pc 0000000080001248 inst 8b17a777 cause 0000000000000002 vaeskf1.vi v14, v17, 15
[10] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 0000000080001248 idx 07c csrr    a3, mepc
[11] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 000000008000124c idx 07d addi    a3, a3, 4
[12] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 000000008000124c idx 07e csrw    mepc, a3
[13] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 000000008000124c idx 07f mret
[14] commit pc 000000008000124c inst 0a8c32b3 wen 1 dst 05 data 2652aaaaaaaaaaa9 idx 080 clmulh  t0, s8, s0
[15] exception pc 0000000080001258 inst bfd32d77 cause 0000000000000002 vsha2cl.vv v26, v29, v6
[16] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 0000000080001258 idx 081 csrr    a3, mepc
[17] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 000000008000125c idx 082 addi    a3, a3, 4
[18] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 000000008000125c idx 083 csrw    mepc, a3
[19] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 000000008000125c idx 084 mret
[20] commit pc 000000008000125c inst 600b969b wen 1 dst 13 data 0000000000000001 idx 085 clzw    a3, s7
[21] exception pc 000000008000126c inst 31d5ead7 cause 0000000000000002 vclmul.vx v21, v29, a1, v0.t
[22] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 000000008000126c idx 086 csrr    a3, mepc
[23] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001270 idx 087 addi    a3, a3, 4
[24] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001270 idx 088 csrw    mepc, a3
[25] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001270 idx 089 mret
[26] exception pc 0000000080001270 inst 4856d527 cause 0000000000000007 vssseg3e16.v v10, (a3), t0, v0.
[27] commit pc 0000000080001000 inst 341026f3 wen 1 dst 13 data 0000000080001270 idx 08a csrr    a3, mepc
[28] commit pc 0000000080001004 inst 00468693 wen 1 dst 13 data 0000000080001274 idx 08b addi    a3, a3, 4
[29] commit pc 0000000080001008 inst 34169073 wen 0 dst 00 data 0000000080001274 idx 08c csrw    mepc, a3
[30] commit pc 000000008000100c inst 30200073 wen 0 dst 00 data 0000000080001274 idx 08d mret
[31] commit pc 0000000080001274 inst 28b9a9b3 wen 1 dst 19 data 00000000000000ba idx 08e xperm4 (args unknown) <--

==============  REF Regs  ==============
---------------- Intger Registers ----------------
  $0: 0x0000000000000000   ra: 0x0000000000000010   sp: 0x7fffffffffffffff   gp: 0x0000000000000020 
  tp: 0x00000007ffffffd7   t0: 0x2652aaaaaaaaaaa9   t1: 0x0000000000000000   t2: 0x0200000000008010 
  s0: 0x6af7fffffffffffb   s1: 0x0000000fffffffae   a0: 0x354ea8badd37f125   a1: 0xffffffffffffff67 
  a2: 0x0000000000000000   a3: 0x0000000080001274   a4: 0x7fffffffffffffff   a5: 0x0000000000100200 
  a6: 0xfffffffffffffffe   a7: 0x0000000000000000   s2: 0x0000000000000001   s3: 0x00000001abdfffff 
  s4: 0x0000000000000000   s5: 0x0000000000000020   s6: 0xffffffffffffffff   s7: 0x2652aaad5555554d 
  s8: 0xffffffffffffffff   s9: 0x0000000000000000  s10: 0x00000000800011e0  s11: 0x0000000000000480 
  t3: 0xffffffffffffefff   t4: 0xfffffffffffffffe   t5: 0x0000000000000000   t6: 0x0000000080002070 
---------------- Float Registers ----------------
 ft0: 0xffffffffffff0000  ft1: 0xffffffffffff4a82  ft2: 0xffffffff00000000  ft3: 0x7ff8000000000000 
 ft4: 0x7fffffffffffffff  ft5: 0xffffffffffff6472  ft6: 0xffffffffffff7e00  ft7: 0xcfcab23967e159a4 
 fs0: 0xffffffffffffa6d7  fs1: 0xffffffff00130000  fa0: 0x354ea8badd37f125  fa1: 0xffffffffffff295e 
 fa2: 0xbff0000000000000  fa3: 0xffffffffbf800000  fa4: 0xffffffffffffb466  fa5: 0x4380000000000401 
 fa6: 0xc000000000000000  fa7: 0xe2fa7ae5a24e6726  fs2: 0xffffffffffff0000  fs3: 0x90337011ff905194 
 fs4: 0xe61198eb9389d507  fs5: 0x7ff8000000000000  fs6: 0x9cbdf3117f8a68ef  fs7: 0x0290c62b28d8e436 
 fs8: 0xffffffffffff7e00  fs9: 0x6fed38c1827ee87d fs10: 0xd76a2b032b6deb8a fs11: 0xee7399e1dec91ce2 
 ft8: 0xffffffffffff3c00  ft9: 0x1f1892b6a7ec521d ft10: 0xffedd6555028b2ce ft11: 0x991290f76611c485 
 fcsr: 0x0000000000000091 fflags: 0x0000000000000011 frm: 0x0000000000000004
---------------- Privileged CSRs ----------------
pc: 0x0000000080001004  privilege mode: M (mode: 3  v: 0  debug: 0)
   mstatus: 0x8000040a00147ea2   sstatus: 0x8000000200046622  vsstatus: 0x0000000200000000
   hstatus: 0x0000000200000000  mnstatus: 0x0000000000000008
    mcause: 0x0000000000000005      mepc: 0x0000000080001274     mtval: 0x8000000000000023
    scause: 0x0000000000000000      sepc: 0x0000000000000000     stval: 0x0000000000000000
   vscause: 0x0000000000000000     vsepc: 0x0000000000000000    vstval: 0x0000000000000000
   mncause: 0x0000000000000000     mnepc: 0x0000000000000000 mnscratch: 0x0000000000000000
    mtval2: 0x0000000000000000     htval: 0x0000000000000000
    mtinst: 0x0000000000000000    htinst: 0x0000000000000000
  mscratch: 0x11674600bca62a67  sscratch: 0xba88bb2a6b558502 vsscratch: 0x5a0792297088fc49
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
v0 : 0x77c7b3ca1ea5571f_37fbdb906b1b05ad  v1 : 0x1057a4711ab28588_79c98eb1a0c01de1  
v2 : 0x47ff3e18bb0462eb_7e80443a58096442  v3 : 0x09c7b19c6c6d0177_a5a3f6cc91a4f0ac  
v4 : 0x7b738f8a8988688f_065225a822f2824a  v5 : 0xfcda6933c83af5a6_9f4797d8c3046877  
v6 : 0xc68b362181927dd3_5de1ef09b2144f69  v7 : 0xa63e0380b88bd5f1_2426e3e678e43904  
v8 : 0x5d0c0a14772bb745_ad2a1fb49b0b5e4d  v9 : 0xfdd8fb11014fd81e_e16df81af76b9664  
v10: 0x66e8fd76bb438092_6b0e36ff22cd01dd  v11: 0xcf1505593f9be37d_8a22a4e7c57fdea4  
v12: 0x917a50e9c0829926_265075b1878d2e0c  v13: 0xa8dd72776821f9c4_358df661713de47d  
v14: 0xb52618522e66ead5_9a251668044f5b92  v15: 0x1ef0af48819b3387_6f00fe59fb9f08eb  
v16: 0xe4a1ba1e8e0f7d6a_eaef57e4119de86e  v17: 0x0567e75ecee9ff15_a6025de56495559d  
v18: 0x5e06f1a9aab018f8_6650723d5768a0f2  v19: 0x1010101010101010_1010101010101010  
v20: 0x1ce910b776f4d198_30cabee27cb57e1a  v21: 0xffffffffff88ff0d_04ff01ff0032ff02  
v22: 0x9bbc739eb95963d6_22b6e66c0e8b064f  v23: 0x81d556fdb324ca19_7a4f6a6edc4e8871  
v24: 0x1ce910b776f4d198_30cabee27cb57e1a  v25: 0x9405ab791b365ea0_bac55b7b7424110f  
v26: 0xf928ced3654bb8e2_b423902044f9433e  v27: 0x3222288f7657e86c_3b731f504176e57e  
v28: 0x7a13f2cae3f93415_a859452bc5c0a445  v29: 0xffffffffff9bff87_6ffffefffb9fffeb  
v30: 0xb6313c9df2b0ca36_f5c79caa5d297ae4  v31: 0xcd1468f6c40daafc_b60ee3b288cc3d73  
  vtype: 0x00000000000000c0 vstart: 0x0000000000000002  vxsat: 0x0000000000000000
   vxrm: 0x0000000000000002     vl: 0x0000000000000010   vcsr: 0x0000000000000004
---------------- Triggers ----------------
 tselect: 0x0000000000000000
 0: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 1: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 2: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 3: tdata1: 0xf000000000000000 tdata2: 0x0000000000000000
 4: tdata1: 0x0000000000000000 tdata2: 0x0000000000000000
privilegeMode: 3
     s3 different at pc = 0x0080001274, right = 0x00000001abdfffff, wrong = 0x00000000000000ba
    fa1 different at pc = 0x0080001274, right = 0xffffffffffff295e, wrong = 0x7ff8000000000000
mstatus different at pc = 0x0080001274, right = 0x8000040a00147ea2, wrong = 0x8000000a001466aa
  mtval different at pc = 0x0080001274, right = 0x8000000000000023, wrong = 0x4ca55555d55567c2
 mcause different at pc = 0x0080001274, right = 0x0000000000000005, wrong = 0x0000000000000007
Core 0: [31mABORT at pc = 0x8000100c
[0m[35mCore-0 instrCnt = 426, cycleCnt = 7,925, IPC = 0.053754
[0m[34mSeed=0 Guest cycle spent: 7,929 (this will be different from cycleCnt if emu loads a snapshot)
[0m[34mHost time spent: 18,553ms
[0m
```

### Environment

XiangShan:
- version: kunminghu-v2  commit 54944b0202f43034465e3609872cbd7f971eba96
build command: ``time make DISABLE_PERF=1 EMU_TRACE=1 emu CONFIG=KunminghuV2Config EMU_THREADS=2 -j$(nproc)``

NEMU:
- version: commit d046c0e9d6560f9c2f068e3973c9a7f904d6881c
- config: riscv64-xs-ref_defconfig


### To Reproduce

```shell
./build/emu -i vssseg3e16_mcause.img --diff riscv64-nemu-interpreter-so
```
where riscv64-nemu-interpreter-so is NEMU(commit: d046c0e9d6560f9c2f068e3973c9a7f904d6881c) built with riscv64-xs-ref_defconfig

[vssseg3e16_mcause_mismatch.zip](https://github.com/user-attachments/files/26571593/vssseg3e16_mcause_mismatch.zip)

### Additional context

_No response_
