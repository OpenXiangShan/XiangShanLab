### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a minimized RVV testcase that triggers a `vstart` architectural-state mismatch between XiangShan and Spike after executing a single `vsse16.v`.

The minimized user instructions are:

```asm
li x19, 0xffffffffffff8000
vsetvli x9, x0, e64, m1
li x8, 0x0
vmv.v.x v22, x8
li x10, 2147491770
vsse16.v v22, (x10), x19
```

In the direct XiangShan difftest run, the mismatch is:

```text
The first instruction of core 0 has commited. Difftest enabled.

============== Commit Group Trace (Core 0) ==============
commit group [00]: pc 0010000000 cmtcnt 1
commit group [01]: pc 0010000004 cmtcnt 1
commit group [02]: pc 0010000008 cmtcnt 1
commit group [03]: pc 0080000000 cmtcnt 1
commit group [04]: pc 0080000004 cmtcnt 1
commit group [05]: pc 0080000008 cmtcnt 1
commit group [06]: pc 008000000c cmtcnt 1
commit group [07]: pc 0080000010 cmtcnt 2
commit group [08]: pc 0080000016 cmtcnt 1
commit group [09]: pc 008000001a cmtcnt 1
commit group [10]: pc 008000001e cmtcnt 1
commit group [11]: pc 0080000022 cmtcnt 1
commit group [12]: pc 0080000024 cmtcnt 1
commit group [13]: pc 0080000026 cmtcnt 2
commit group [14]: pc 008000002c cmtcnt 1
commit group [15]: pc 0080000030 cmtcnt 4 <--

============== Commit Instr Trace ==============
[00] commit pc 0000000010000000 inst 0010029b wen 1 dst 05 data 0000000000000001 idx 000
[01] commit pc 0000000010000004 inst 01f29293 wen 1 dst 05 data 0000000080000000 idx 001
[02] commit pc 0000000010000008 inst 00028067 wen 0 dst 00 data 0000000080000000 idx 002
[03] commit pc 0000000080000000 inst 00000297 wen 1 dst 05 data 0000000080000000 idx 003
[04] commit pc 0000000080000004 inst 05828293 wen 1 dst 05 data 0000000080000058 idx 004
[05] commit pc 0000000080000008 inst 30529073 wen 0 dst 00 data 0000000080000058 idx 005
[06] commit pc 000000008000000c inst 300022f3 wen 1 dst 05 data 0000000a00000000 idx 006
[07] commit pc 0000000080000010 inst 00003337 wen 1 dst 06 data 0000000000003600 idx 007
[08] commit pc 0000000080000016 inst 0062e2b3 wen 1 dst 05 data 0000000a00003600 idx 008
[09] commit pc 000000008000001a inst 30029073 wen 0 dst 00 data 0000000a00003600 idx 009
[10] commit pc 000000008000001e inst 00301073 wen 0 dst 00 data 0000000a00003600 idx 00a
[11] commit pc 0000000080000022 inst 0020006f wen 0 dst 00 data 0000000a00003600 idx 00b
[12] commit pc 0000000080000024 inst ffff89b7 wen 1 dst 19 data ffffffffffff8000 idx 00c
[13] commit pc 0000000080000026 inst 018074d7 wen 1 dst 09 data 0000000000000002 idx 00d
[14] commit pc 000000008000002a inst 00000413 wen 1 dst 08 data 0000000000000000 idx 00e
[15] commit pc 000000008000002c inst 5e044b57 wen 1 dst 32 data 0000000000000002 idx 00f
[16] commit pc 0000000080000030 inst 00040537 wen 1 dst 10 data 0000000000040001 idx 010
[17] commit pc 0000000080000036 inst 00d51513 wen 1 dst 10 data 0000000080002000 idx 011
[18] commit pc 0000000080000038 inst fba50513 wen 1 dst 10 data 0000000080001fba idx 012
[19] exception pc 000000008000003c inst 0b355b27 cause 0000000000000007 <--

==============  REF Regs  ==============
zero: 0x0000000000000000   ra: 0x0000000000000000   sp: 0x0000000000000000   gp: 0x0000000000000000
  tp: 0x0000000000000000   t0: 0x0000000a00003600   t1: 0x0000000000003600   t2: 0x0000000000000000
  s0: 0x0000000000000000   s1: 0x0000000000000002   a0: 0x0000000080001fba   a1: 0x0000000000000000
  a2: 0x0000000000000000   a3: 0x0000000000000000   a4: 0x0000000000000000   a5: 0x0000000000000000
  a6: 0x0000000000000000   a7: 0x0000000000000000   s2: 0x0000000000000000   s3: 0xffffffffffff8000
  s4: 0x0000000000000000   s5: 0x0000000000000000   s6: 0x0000000000000000   s7: 0x0000000000000000
  s8: 0x0000000000000000   s9: 0x0000000000000000  s10: 0x0000000000000000  s11: 0x0000000000000000
  t3: 0x0000000000000000   t4: 0x0000000000000000   t5: 0x0000000000000000   t6: 0x0000000000000000
 ft0: 0xf8277cbbf87e6d35  ft1: 0xd7cb82905603515d  ft2: 0x315cdea6df3e4b86  ft3: 0x5afeed0b77134ade
 ft4: 0xe15cd5f4da2e0227  ft5: 0x9622d759493e10cf  ft6: 0xa796ae017d9ab19a  ft7: 0x4b86abdadc917484
 fs0: 0x4465ab8dddf7b7a7  fs1: 0xe11da21c1aaa5a44  fa0: 0xd4b5b16a6b23166c  fa1: 0x865a19b41fa94f54
 fa2: 0x9210de15fdec3bd5  fa3: 0xe64de4aba7cb7b6f  fa4: 0x23611f5c3afcb937  fa5: 0x862bb8784820d32e
 fa6: 0x12499e90b3b924ce  fa7: 0xcfb707e5dba9d18d  fs2: 0x855caee23b713c5a  fs3: 0xffdd51c5073ca445
 fs4: 0xd3fb9b3f797dc1ec  fs5: 0x73306df7ecc8b781  fs6: 0xaf6476d883aaf74b  fs7: 0x4255fbcae7a66dd3
 fs8: 0x3079934014a43ef7  fs9: 0xd9877aa3a122bf93 fs10: 0x8388f791bf8e84d5 fs11: 0x6318ba8642b7655f
 ft8: 0x97ef289cb4ec71c9  ft9: 0xbcd9a2acb0139a11 ft10: 0x579b04a017bfd271 ft11: 0x046ca2a637d52c9d
pc: 0x0000000080000058 mstatus: 0x8000040a00007e00 mcause: 0x0000000000000007 mepc: 0x000000008000003c
                       sstatus: 0x8000000200006600 scause: 0x0000000000000000 sepc: 0x0000000000000000
satp: 0x0000000000000000
mip: 0x0000000000000000 mie: 0x0000000000000000 mscratch: 0x5cbd87288909eb70 sscratch: 0x5cbd87288909eb70
mideleg: 0x0000000000001444 medeleg: 0x0000000000000000
mtval: 0x000000007fff9fba stval: 0x0000000000000000 mtvec: 0x0000000080000058 stvec: 0x0000000000000000
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
 vstart different at pc = 0x0080000030, right = 0x0000000000000001, wrong = 0x0000000000000000
Core 0: ABORT at pc = 0xfffff6f7113d8310
Core-0 instrCnt = 21, cycleCnt = 8,378, IPC = 0.002507
Seed=0 Guest cycle spent: 8,382 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 158,810ms
```


### Expected behavior

After executing the vector instruction sequence, XiangShan and Spike should agree on the architectural value of `vstart`.
The RVV specification treats `vstart` as architectural state. So a `vstart` mismatch should not happen if both implementations are behaving correctly.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26698009/bug-report.tar.gz)

### To Reproduce

1. Run XiangShan with diff:
```bash
./build/verilator-compile/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so 
```
2. Observed:
```text
vstart different at pc = 0x0080000030, right = 0x0000000000000001, wrong = 0x0000000000000000
Core 0: ABORT ...
```

### Additional context

_No response_
