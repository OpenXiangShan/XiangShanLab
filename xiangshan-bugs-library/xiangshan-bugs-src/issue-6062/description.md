### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan hits a real StoreQueue RTL assertion when an S-mode cache-block operation targets a PMP-denied cache block. The reduced reproducer executes one `cbo.clean` against a PMP no-permission NAPOT region. The access should raise a store/AMO access fault (`mcause = 7`). Instead, the emulator aborts before software can observe the trap:

```text
emu compiled at Jun  2 2026, 18:15:07
[INFO] init for constantin: loaded from init.
Using simulated 32768B flash
Core  0's Commit SHA is: 4c742fa44b, dirty: 1
Using simulated 8386560MB RAM
The image is program.elf
ELF file detected and loading image from extracted elf file
Loading 4232 bytes at address 0x80000000 at offset 0x0
Assertion failed at ./XiangShan/build/rtl/NewStoreQueue.sv:14793.
The simulation stopped. There might be some assertion failed.
Core 0: ABORT at pc = 0x124368d2ee4c2
Core-0 instrCnt = 38, cycleCnt = 2405, IPC = 0.015800
Seed=0 Guest cycle spent: 2410 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 8506ms
Assertion failed at LogUtils.scala:132
[ERROR][time=                2409] SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner.lsq.storeQueue: pointer update error!
```

The current reduced reproducer keeps only the following core behavior:

```asm
csrw mtvec, trap_entry
csrw satp, zero
sfence.vma

# Enable CBO from S-mode.
li t0, 0xf0
csrw menvcfg, t0

# PMP entry 0: no permission for target_data.
# PMP entry 1: catch-all RWX allow.
la t0, target_data
srli t0, t0, 2
li t1, 0x1ff
or t0, t0, t1
csrw pmpaddr0, t0
li t0, -1
csrw pmpaddr1, t0
li t0, (0x1f << 8) | 0x18
csrw pmpcfg0, t0

# Enter S-mode and execute one CBO to the denied block.
mret
la t0, target_data
cbo.clean 0(t0)
```

Standalone reduced source:

```asm
  .option push
  .option norelax

  .equ CAUSE_STORE_ACCESS_FAULT, 7
  .equ MSTATUS_MPP, 0x1800
  .equ MSTATUS_MPP_S, 0x0800
  .equ MENVCFG, 0x30a
  .equ PMP_NAPOT, 0x18
  .equ PMP_RWX_NAPOT, 0x1f

  .macro XS_EXIT
    .word 0x0005006b
  .endm

  .section .text.init, "ax"
  .globl _start
_start:
  la t0, trap_entry
  csrw mtvec, t0
  csrw medeleg, zero
  csrw mideleg, zero
  csrw satp, zero
  sfence.vma
  li t0, 0xf0
  csrw MENVCFG, t0

  la t0, target_data
  srli t0, t0, 2
  li t1, 0x1ff
  or t0, t0, t1
  csrw pmpaddr0, t0
  li t0, -1
  csrw pmpaddr1, t0
  li t0, (PMP_RWX_NAPOT << 8) | PMP_NAPOT
  csrw pmpcfg0, t0
  fence

  csrr t0, mstatus
  li t1, ~MSTATUS_MPP
  and t0, t0, t1
  li t1, MSTATUS_MPP_S
  or t0, t0, t1
  csrw mstatus, t0
  la t0, smode_probe
  csrw mepc, t0
  mret

smode_probe:
  la t0, target_data
  .word 0x0012a00f       # cbo.clean 0(t0)
  li a0, 80              # should not reach here
  XS_EXIT

trap_entry:
  csrr t5, mcause
  li t0, CAUSE_STORE_ACCESS_FAULT
  beq t5, t0, pass
  addi a0, t5, 1
  XS_EXIT

pass:
  li a0, 0
  XS_EXIT

  .section .data, "aw"
  .align 12
target_data:
  .dword 0x1122334455667788
  .zero 128

  .option pop
```

I also tested controls:

- `cbo.clean` to a PMP-allowed block reaches `HIT GOOD TRAP`.
- `cbo.zero` to the same PMP-denied block reaches `HIT GOOD TRAP` through the expected store access-fault path.
- Related no-zero CBO variants `cbo.flush` and `cbo.inval` against the PMP-denied block hit the same StoreQueue assertion class.

This suggests the problem is specific to the no-zero CBO PMP-deny exception path, rather than CBO in general or PMP deny in general.


### Expected behavior

XiangShan should either:

- execute the CBO normally when PMP allows the cache block, or
- raise a defined architectural exception when PMP denies the cache block.

For this reproducer, the expected behavior is a precise store/AMO access fault (`mcause = 7`) handled by the test trap handler.

It should not hit an internal StoreQueue RTL assertion.

### Environment

[bug-report.zip](https://github.com/user-attachments/files/28676825/bug-report.zip)

### To Reproduce

1. Build the reproducer:

```bash
riscv64-unknown-elf-gcc \
  -march=rv64ima_zicsr_zifencei -mabi=lp64 -static -mcmodel=medany \
  -fvisibility=hidden -nostdlib -nostartfiles \
  program.S -T link_xiangshan.ld \
  -o program.elf
```

2. Run the failing case from the XiangShan repository:

```bash
timeout 120 ./build/verilator-compile/emu \
  -i program.elf \
  --no-diff -C 500000
```

Expected observed failure:

```text
Assertion failed at ./XiangShan/build/rtl/NewStoreQueue.sv:14793.
Assertion failed at LogUtils.scala:132
[ERROR] ...storeQueue: pointer update error!
Core 0: ABORT
```


### Additional context

_No response_
