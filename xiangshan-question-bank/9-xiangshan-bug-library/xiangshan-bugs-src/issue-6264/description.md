### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Sequential instruction fetch from the last canonical Sv39 virtual address (0x0000003FFFFFFFFFFF) falls through to the non-canonical address 0x0000004000000000 without raising an instruction page fault.
The ITLB walks the non-canonical VA using its VPN bits and executes whatever PTE sits at the resulting root-table index (here root[256] alias mapped to PA 0x81200000).

Execution flow: `jr 0x3FFFFFFFC0` (redirect, goes through backendIPF → canonical check passes → target valid) → NOP slide to page end → fall-through to 0x4000000000 → ITLB walks root[256] → fetches and executes the alias stub, which writes flag=0xFA and ecalls back to M-mode. No fault occurs on the fetch path.

The redirect target was canonical; the vulnerability is in the next-line sequential fetch.


### Expected behavior

Instruction page fault (mcause=12) with mepc mtval = 0x0000004000000000, as demonstrated by NEMU standalone (nemu.log):

```
[test] trap = 1, mcause = 12, mtval = 0x4000000000, mepc = 0x4000000000, alias flag = 0x0
CORRECT: fall-through to non-canonical VA raised IPF
```

**Actual behavior (kmhv2.log)**

```
[test] back in M, ecall mcause = 9
[test] trap = 0, mcause = 0, mtval = 0x0, mepc = 0x0, alias flag = 0xfa
BUG REPRODUCED: fetch crossed canonical boundary without
  fault and executed aliased code at PA 0x81200000
Core 0: HIT BAD TRAP at pc = 0x800004e4
```

**Difftest mismatch (diff.log)**

The difftest reference reports instruction page fault (mcause=12, mepc=0x4000000000, mtval=0x4000000000), while XiangShan reports ecall from S-mode (mcause=9) with no prior fault:

```
 mcause different at pc = 0x00800003da, right = 0x000000000000000c, wrong = 0x0000000000000000
   mepc different at pc = 0x00800003da, right = 0x0000004000000000, wrong = 0x00000000800001cc
  mtval different at pc = 0x00800003da, right = 0x0000004000000000, wrong = 0x0000000000000000
```

Commit trace (diff.log, last entry before mismatch):

```
[31] commit pc 0000004000000000 inst 820102b7 wen 1 dst 05 data ffffffff82010000 idx 01b lui     t0, 0x82010 <--
```

The REF state at mismatch:

```
mcause: 0x000000000000000c      mepc: 0x0000004000000000     mtval: 0x0000004000000000
```

### Environment

- XiangShan commit: 7be121c71f (kmh v2)
- NEMU: ready-to-run

### To Reproduce

Build and run the workload with difftest enabled:

```bash
cd /xs-env/XiangShan
./build/emu -i bug-canonical-fetch-fallthrough-riscv64-xs.bin \
  --diff ready-to-run/riscv64-nemu-interpreter-so --max-instr 50000
```

[xs-fetch-fallthrough-noncanonical.zip](https://github.com/user-attachments/files/30220794/xs-fetch-fallthrough-noncanonical.zip)

### Additional context

Seems related to #6211, but that bug and fix only considered direct jump to noncanonical addresses, not successive fetched across boundary. Also, that bug reports nonexistence on kmhv2, this is found on kmhv2.
