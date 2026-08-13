### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan hits a real RTL assertion failure on a reduced RVV load-path sequence derived from `bug_case_000632`.
The emulator aborts with:

```text
emu compiled at Apr 14 2026, 20:44:17
Using simulated 32768B flash
Core  0's Commit SHA is: 1e64239649, dirty: 1
Using simulated 8386560MB RAM
The image is program.elf
ELF file detected and loading image from extracted elf file
Loading 206 bytes at address 0x80000000 at offset 0x0
Loading 16 bytes at address 0x80001000 at offset 0x1000
The reference model is /home/projects/projects/XiangShan/ready-to-run/riscv64-spike-so
The first instruction of core 0 has commited. Difftest enabled.
Assertion failed at /home/projects/projects/XiangShan/build/rtl/LoadUnitS0.sv:780.
The simulation stopped. There might be some assertion failed.
Core 0: ABORT at pc = 0x80000060
Core-0 instrCnt = 23, cycleCnt = 8,408, IPC = 0.002735
Seed=0 Guest cycle spent: 8,412 (this will be different from cycleCnt if emu loads a snapshot)
Host time spent: 106,483ms
Assertion failed at NewLoadUnit.scala:319
```

The current reduced reproducer keeps only the following core behavior:

```asm
li x9, 0x8fffffb8
li x10, 0x7f
sd x10, 0(x9)

vsetivli x8, 27, e16, m4
li x8, 2415919032
vmv.v.i v24, 0
vloxei32.v v16, (x8), v24
vmsbf.m v0, v16
li x8, 2415919032
vl1re64.v v18, (x8)
li x11, 2415919032
vlseg4e8.v v24, (x11), v0.t
```

Standalone reduced source:

```asm
    .section .text
    .globl  _start

_start:
    la      t0, trap_handler
    csrw    mtvec, t0

    csrr    t0, mstatus
    li      t1, 0x00003600       # set FS+VS to Dirty (0b11) so FP/RVV regs are usable
    or      t0, t0, t1
    csrw    mstatus, t0
    csrw    fcsr, x0             # clear floating-point status

    j       user_code

user_code:
    li x9, 0x8fffffb8
    li x10, 0x7f
    sd x10, 0(x9)
    vsetivli x8, 27, e16, m4
    li x8, 2415919032
    vmv.v.i v24, 0
    vloxei32.v v16, (x8), v24
    vmsbf.m v0, v16
    li x8, 2415919032
    vl1re64.v v18, (x8)
    li x11, 2415919032
    vlseg4e8.v v24, (x11), v0.t
    j exit

exit:
    li      t0, 1                # report success
    la      t1, tohost
    sd      t0, 0(t1)
1:
    j       1b

    .align  2
trap_handler:
    csrr    t0, mepc             # faulting PC
    csrr    t1, mcause           # trap cause
    csrr    t4, mtval

    slli    t5, t1, 1            # strip interrupt bit
    srli    t1, t5, 1

    li      t2, 2                # default length assumes compressed
    li      t3, 2                # illegal instruction -> mtval holds encoding
    beq     t1, t3, use_mtval
    li      t3, 1                # instruction access fault
    beq     t1, t3, update_mepc
    li      t3, 12               # instruction page fault
    beq     t1, t3, update_mepc

    lhu     t4, 0(t0)
    j       decode_length

use_mtval:
    j       decode_length

decode_length:
    andi    t4, t4, 3
    li      t3, 3
    bne     t4, t3, compressed_len
    li      t2, 4                # standard 32-bit instruction
    j       update_mepc

compressed_len:
    li      t2, 2                # compressed instruction

update_mepc:
    add     t0, t0, t2           # skip offending instruction
    csrw    mepc, t0
    csrw    mcause, x0
    csrw    mtval, x0
    mret

    .section .tohost,"aw",@progbits
    .align  6
    .globl  tohost
    .globl  fromhost
tohost:
    .dword  0
fromhost:
    .dword  0
```

### Expected behavior

XiangShan should either:
- execute the sequence normally, or
- raise a defined architectural exception,

but it should not hit an internal RTL assertion in the load unit.


### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26975008/bug-report.tar.gz)

### To Reproduce

1. Run the script:

```bash
timeout 180 build/verilator-compile/emu --image program.elf --diff ./ready-to-run/riscv64-spike-so
```

Expected observed failure:

```text
Assertion failed at ./XiangShan/build/rtl/LoadUnitS0.sv:780.
Assertion failed at NewLoadUnit.scala:319
Core 0: ABORT at pc = 0x80000060
```

### Additional context

_No response_
