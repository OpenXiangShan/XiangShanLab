### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest),确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR,并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的,我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

When `vsm.v` (vector mask store) triggers a store access fault, the DUT reports the store **base address** in mtval while NEMU reports the **first faulting byte** address.

Setup: SEW=16/LMUL=4, vl=20, `vstart=2` (so the mask store writes its remaining byte(s) starting at base+2), base `x31 = 0x1fffffe` where bytes `0x1fffffe..0x1fffff` are accessible and `0x2000000+` are not. Both sides take the same store access fault (mcause matches), then difftest aborts on mtval:

```
mtval different ... right = 0x0000000002000000, wrong = 0x0000000001fffffe
```

NEMU (`right`) = 0x2000000, the first byte actually being stored that faults; DUT (`wrong`) = 0x1fffffe, the base address of the store.

### Expected behavior

mtval on a vector store access fault should report the effective address of the first faulting byte being stored, matching the reference model.

### To Reproduce

1. Build emu: `NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 SIM_ARGS=--fpga-platform -j$(nproc)`
2. Compile the PoC below (same command and `link.ld` as in the family-stall report; entry at 0x80000000)
3. Run: `./build/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so`
4. Observe the difftest abort at the vsm.v trap with the mtval diff above.

PoC:

```asm
.option norvc
.section .text.init
.globl _start
_start:
    csrr t0, mstatus
    li t1, 0x600
    or t0, t0, t1
    csrw mstatus, t0
    # Clear medeleg: the default medeleg=0x1444 would delegate U-mode illegal traps to stvec=0
    csrw medeleg, x0
    # All traps enter the M-mode trap_handler; mepc vs payload distinguishes trap location
    la t0, trap_handler
    csrw mtvec, t0
    li t1, 20
    .4byte 0x00a37057 # vsetvli zero, t1, 0xa   (SEW=16, LMUL=4, tu, mu)
    li t0, 2
    .4byte 0x00829073 # csrw vstart, t0
    li t0, 0
    .4byte 0x00a29073 # csrw vxrm, t0
    li t0, 0
    .4byte 0x00929073 # csrw vxsat, t0
payload:
    li x31, 0x1fffffe           # store base: bytes ..0x1fffff accessible, 0x2000000+ not
payload_ins:
    vsm.v v0, (x31)             # <<< BUG TRIGGER: store access fault on both sides,
                                #     but mtval differs (DUT=base, NEMU=first faulting byte)
    .4byte 0x00000000           # Sentinel illegal instruction: only reached if the
                                #     payload above did NOT trap
1:  j 1b
trap_handler:
    csrr t0, mepc
    la t1, payload_ins
    bne t0, t1, 2f
    .4byte 0x0000006b           # payload trapped as expected -> GOODTRAP
2:  j 2b
```

(Linker script identical to the one included in the family-stall report.)

### Environment

- Hardware
  - CPU: AMD EPYC 7543 32-Core Processor (128 threads)
  - Memory (GB): 247
  - Storage (GB): 2000
- Software
  - Operating system: Ubuntu 26.04.1 LTS
  - gcc version: riscv64-unknown-elf-gcc (14.2.0+19) 14.2.0
  - java version: openjdk version "25.0.2" 2026-01-20
  - mill version: Mill Build Tool version 0.12.17
- Repo
  - XiangShan commit id: `c8d7b3a`
  - NEMU commit id (if difftest failed with NEMU): `ready-to-run @ 4cf9983 (bundled riscv64-nemu-interpreter-so)`
- Build & Run
  - Build command: `NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 SIM_ARGS=--fpga-platform -j$(nproc)`
  - Run command: `./build/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so`

### Additional context

Guess: the vector store fault address for mask stores is taken from the store's base address (or first element) rather than the faulting element's byte address, in the vector store path.
