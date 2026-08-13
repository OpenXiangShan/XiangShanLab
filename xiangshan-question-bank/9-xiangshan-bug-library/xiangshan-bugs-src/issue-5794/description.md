### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

I found a minimal scalar-store testcase that triggers a XiangShan difftest failure when executing two `sd` instructions to the same high-address region.

The smallest reproducer I found is:
```asm
.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  csrr t0, mstatus
  li t1, 0x00003600     
  or t0, t0, t1
  csrw mstatus, t0
  csrw fcsr, x0

  j user_code

user_code:
  li x9, 0x8fffffb8

  li x10, 0xfff54323999c313b
  sd x10, 0(x9)

  li x10, 0x157731e1e526f5a0
  sd x10, 40(x9)

exit:
  li t0, 1
  la t1, skiptrap_store_buf
  sd t0, 0(t1)

  # DiffTest STATE_GOODTRAP
  li t0, 0
  .insn i 0x6b, 0, x0, t0, 0
  ebreak

  .align 2
trap_handler:
  csrr t0, mepc
  csrr t1, mcause
  csrr t4, mtval

  slli t5, t1, 1
  srli t1, t5, 1

  li t2, 2
  li t3, 2
  beq t1, t3, use_mtval
  li t3, 1
  beq t1, t3, fetch_inst
  li t3, 12
  beq t1, t3, fetch_inst

fetch_inst:
  lhu t4, 0(t0)
  j decode_length

use_mtval:
  j decode_length

decode_length:
  andi t4, t4, 3
  li t3, 3
  bne t4, t3, compressed_len
  li t2, 4
  j update_mepc

compressed_len:
  li t2, 2

update_mepc:
  add t0, t0, t2
  csrw mepc, t0
  csrw mcause, x0
  csrw mtval, x0
  mret

.section .data
.align 3
skiptrap_store_buf:
  .dword 0

.align 6
fuzz_mem_pool:
  .space 4096
```

The observation is:
- sd x10, 0(x9) alone does not reproduce the same difftest failure.
- sd x10, 40(x9) alone does not reproduce the same difftest failure.
- The combination of the two stores does reproduce it.
So this appears to be a combination-triggered difftest/store-event problem, not a single-instruction failure.

> Store commit error: the store trace is empty.
> 
> ==============  Store Commit Event (Core 0)  ==============
> This version of 'REF' does not support the 'PC' value of store commit event. Please use a newer version of 'REF'.

> Mismatch for store commits
> REF commits addr 0x000000008fffffb8, data 0xfff54323999c313b, mask 0x00ff, pc 0x0000000080000042
> DUT commits addr 0x000000008fffffb8, data 0xfff54323999c313b, mask 0x00ff, pc 0x0000000080000042, robidx 0x14

> Core 0: ABORT ...


### Expected behavior

Executing two ordinary scalar `sd` instructions to the same high-address region should not trigger a difftest store-commit mismatch.

At minimum, XiangShan + REF should agree on the store commit event stream for this testcase.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26604924/bug-report.tar.gz)

### To Reproduce

1. Build the testcase ELF/binary.
2. Run XiangShan with diff:
> ./build/verilator-compile/emu --image first_and_last.elf --diff ./ready-to-run/riscv64-spike-so --ram-size=128MB --max-cycles=2000000 --max-instr=50000 --log-begin=0 --log-end=200000 --dump-commit-trace
3. Observe the store-commit mismatch.

### Additional context

_No response_
