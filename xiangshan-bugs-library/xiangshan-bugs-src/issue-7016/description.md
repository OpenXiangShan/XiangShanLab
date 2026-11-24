### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

I would like to report a correctness issue on `kunminghu-v2`. The standard Bug report form requires reproduction on the default branch, but this issue does not reproduce on `kunminghu-v3`. I am reporting it here to avoid incorrectly confirming that requirement. Please reclassify it as a bug report if appropriate.

### Bug description

On `kunminghu-v2` commit `e520652de55e`, a fault-only-first vector load (`vle8ff.v`) incorrectly modifies `vl` when element 0 raises a load page fault and a later element in the same access also faults.

The test runs in S-mode with Sv39, `mstatus.SUM = 0`, and VLEN = 128.

```asm
li       a0, 32
vsetvli  t3, a0, e8, m1, tu, mu
li       a1, 0x40001000 - 15
vle8ff.v v8, (a1)
csrr     s3, vl
```

The first page has a valid user PTE, which denies S-mode access. The second page has an invalid PTE.

### Expected behavior

According to RVV 1.0, if element 0 raises an exception during a fault-only-first load, the processor must take the trap without modifying `vl`.

Expected: `vl = 16 (0x10)`.

### Observed behavior

The load page fault is reported correctly, but `vl` is incorrectly changed to 15.

```text
mcause = 13
mtval  = 0x40000ff1
vstart = 0
vl     = 15 (expected 16)
```

Changing the page boundary produces incorrect `vl` values of 8 and 1.

The NEMU reference model preserves `vl = 16`, and difftest reports a mismatch.

The incorrect `vl` is observable in the trap handler and remains incorrect after `mret`.

### Environment

- Branch: `kunminghu-v2`
- Commit: `e520652de55e`
- OS: Ubuntu 24.04 LTS
- CPU: Intel Xeon 6731P
- Verilator: 5.048
- GCC: 13.3.0
- RISC-V GCC: 13.2.0

### Reproduction

The attached `vle8ff_vl_repro.zip` contains the assembly source, linker script, build/run scripts, precompiled binaries, and execution logs.

```bash
./build.sh
./run.sh <XiangShan directory>
```

Nine variants were tested, including five failing cases and four passing controls.

### Additional context

The issue also reproduces on an older master build (`0303f76a`).

It does not reproduce on `kunminghu-v3` commit `726d04003c8f`, where the vector memory unit has been rewritten.

A possible cause is the interaction between fault-only-first exception handling and `vl` trimming in `VMergeBuffer.scala` and `VfofBuffer.scala`. This has not been confirmed by modifying the RTL.

[vle8ff_vl_repro.zip](https://github.com/user-attachments/files/35075070/vle8ff_vl_repro.zip)
