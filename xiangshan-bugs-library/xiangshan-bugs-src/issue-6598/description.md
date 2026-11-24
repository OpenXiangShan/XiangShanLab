### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest),确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR,并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的,我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Executing any instruction from a large set of vector families causes the core to stop committing entirely: the instruction never retires, no trap is taken, and nothing commits afterwards. Reproducible with `--no-diff` (pure RTL), in M-mode or U-mode, with `vstart = 0` or any other value — so it is neither a difftest artifact nor vstart-dependent.

Observed families (each verified by a PoC or by batch difftest runs): `vid.v`, `viota.m`, `vwsll.{vi,vv,vx}`, `vandn.{vv,vx}`, `vrol.*`, `vror.*`, `vnclip/vnclipu` (all forms), `vnsra/vnsrl` (all forms), `vadc/vmadc/vmsbc`, `vslide1up/vslide1down/vslidedown`, `vrgather.vv`, `vrev8.v`, `vaaddu`, and the whole widening arithmetic family `vwmacc/vwmul/vwmaccu/vwmaccsu/vwmulsu/vwmulu/vwadd*/vwsub*` (.vx/.vv/.wx/.vi forms).

### Expected behavior

A legal vector instruction in a legal vector state must either execute and retire, or raise an exception. An implementation may choose to trap unsupported instructions with illegal-instruction, but must not stall the pipeline forever.

### To Reproduce

1. Build emu: `NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 SIM_ARGS=--fpga-platform -j$(nproc)`
2. Save the PoC below as `poc.S` and the linker script as `link.ld`, then compile:
   `riscv64-unknown-elf-gcc -nostdlib -nostartfiles -static -fno-pic -march=rv64gcv -mabi=lp64d -Wl,-T,link.ld -Wl,-e,_start -o poc.elf poc.S`
3. Run: `./build/emu -i poc.elf --no-diff --dump-commit-trace`
4. Observe: with `--dump-commit-trace` the last committed instruction is the `csrw vstart`; `vid.v` never commits and the GOODTRAP marker is never reached (verified 30+ s). Substituting `vwmacc.vx v0, x0, v2` (after `vsetvli zero, t1, e32, m1`) shows the same for the widening family.

PoC (VLEN=128):

```asm
.option norvc
.section .text.init
.globl _start
_start:
    # Minimal execution environment: enable the vector extension (mstatus.VS = Dirty)
    csrr t0, mstatus
    ori  t0, t0, 0x600
    csrw mstatus, t0

    vsetivli zero, 16, e8, m1, ta, ma   # SEW=8, LMUL=1, vl=16 (all-legal)
    csrw vstart, zero

    vid.v v0                    # <<< BUG TRIGGER: legal instruction, never
                                #     retires; nothing commits afterwards
                                #     (no trap is taken either).
    .4byte 0x0000006b           # GOODTRAP marker - never reached
1:  j 1b
```

Linker script (`link.ld`):

```
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS {
  . = 0x80000000;
  .text.init : { KEEP(*(.text.init)) }
  . = ALIGN(0x1000); .text : { *(.text .text.*) }
  . = ALIGN(0x1000); .data : { *(.data .data.* .sdata .sdata.*) }
  .bss (NOLOAD) : { *(.bss .bss.* COMMON) }
  /DISCARD/ : { *(.comment) *(.note*) *(.riscv.attributes) }
}
```

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
- Build & Run
  - Build command: `NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 SIM_ARGS=--fpga-platform -j$(nproc)`
  - Run command: `./build/emu -i poc.elf --no-diff --dump-commit-trace`
