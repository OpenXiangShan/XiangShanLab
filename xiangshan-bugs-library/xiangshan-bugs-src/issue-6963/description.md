### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and found no existing report covering this exact trigger; related reports are identified below.
- [ ] I have reproduced the issue using the latest commit on the default branch. This report targets `kunminghu-v2` at the commit recorded below; the current default `kunminghu-v3` branch has not been tested for this report.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. The inline source was rebuilt and replayed with the recorded V2 emulator and reference on 2026-11-13; the attached logs and ELF are from that replay.

### Branch

`kunminghu-v2`

### Describe the bug

On a local XiangShan `kunminghu-v2` build at commit `7a1bbc60873e61307c0c8cbe7c7242392f9c07bb`, a unit-stride vector load with `vl>=2` and `vstart=1` faults on its first active element. The DUT raises a load access fault (`mcause=5`, `mtval` equal to the faulting element address), but reports `mstatus.VS=Dirty` and `SD=1`. NEMU reports `VS=Clean` and `SD=0` for the same test. The vector destination value is unchanged.

Full difftest output is provided in the downloadable log below.

The same result is observable without difftest: the program's trap-handler check detects that `sstatus.VS` is Dirty.

This is a separate trigger from #6602: the `vl=0` case does not reproduce on the reported commit, while the faulting load with `vl>=2` and `vstart=1` does.

### Expected behavior

For this test, XiangShan and NEMU should report the same `mstatus.VS`/`sstatus.VS` state at the load access fault.

### Environment

- Hardware
  - CPU: 2 × AMD EPYC 7543 32-Core Processor (128 hardware threads total)
  - Memory: approximately 247 GiB
  - Storage: approximately 3.6 TiB filesystem capacity
- Software
  - Operating system: Ubuntu 26.04.1 LTS (x86_64)
  - gcc version: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0
  - clang version used for the emulator: Ubuntu clang 21.1.8 (6ubuntu1), confirmed from the emulator's ELF compiler information
  - RISC-V compiler: riscv64-unknown-elf-gcc (14.2.0+19) 14.2.0
  - Java: OpenJDK 21.0.13.0.3
  - Mill: 0.12.3
  - Verilator: 5.038
- Repo
  - XiangShan commit: `7a1bbc60873e61307c0c8cbe7c7242392f9c07bb` (`kunminghu-v2`)
  - DiffTest submodule: `3729300ae233816d332d057f170472ebe35147b0`
  - NEMU reference: bundled `riscv64-nemu-interpreter-so` from the `ready-to-run` submodule at `c5a45bc861b64db88e7048a62f6ed350c75639cf`; the NEMU source commit is not recorded separately in the available provenance
  - SPIKE: not used
  - Local source changes: CSR/uarch coverage instrumentation; the exact recorded XiangShan and DiffTest patches are included in the archive
- Build & Run
  - Configuration: `DefaultConfig`, one core, VLEN=128; AXI (`EnableCHI=false`)
  - Emulator SHA-256: `072820a6065816db80c9f6a8104eeecbbf25883a8ff64d7752c7b53847c682ad`
  - NEMU reference SHA-256: `f0c3361bb8f3e186ad55567918a7e8b93c28341366e02ba354229e825cfb8d0f`

Recorded emulator build command (the local instrumentation patches are supplied in the archive):

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export NOOP_HOME="$PWD"
make emu CONFIG=DefaultConfig CC=clang CXX=clang++ -j60 \
  SIM_ARGS='--csr-state-cover --uarch-state-cover' \
  PGO_CFLAGS=-fPIE PGO_LDFLAGS=-pie EMU_COMPILE_FILTER=-j60
```

### To Reproduce

[Source, exact ELF, logs, environment and build patches (ZIP)](https://github.com/msuadOf/XiangShan/releases/download/issue-repro-6963-6965-20261113/issue-6963-reproducer.zip) · [Full replay log](https://github.com/msuadOf/XiangShan/releases/download/issue-repro-6963-6965-20261113/issue-6963-replay.log)

Save the following as `poc.S`. It sets `vl=2`, sets `vstart=1`, sets `sstatus.VS=Clean`, then loads from aligned unmapped address `0x800`. Element 1 accesses `0x808` and faults.

```asm
.option norvc
.section .text.init
.globl _start
_start:
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    csrr t0, mstatus
    li t1, 0x600
    or t0, t0, t1
    csrw mstatus, t0
    csrw medeleg, x0
    la t0, trap_handler
    csrw mtvec, t0
    vsetivli zero, 2, e64, m1, tu, ma
    li t0, 1
    csrw vstart, t0
    csrr t0, sstatus
    li t1, -1537
    and t0, t0, t1
    li t1, 0x400
    or t0, t0, t1
    csrw sstatus, t0
    li t2, 0x800
    vle64.v v0, (t2)
    csrr t0, sstatus
    li t1, 0x600
    and t0, t0, t1
    li t1, 0x400
    bne t0, t1, 4f
    .4byte 0x0000006b
4:  .4byte 0x0000006b
1:  j 1b
trap_handler:
    csrr t0, sstatus
    li t1, 0x600
    and t0, t0, t1
    li t1, 0x400
    bne t0, t1, 5f
    .4byte 0x0000006b
5:  .4byte 0x0000006b
2:  j 2b
```

Linker script (`link.ld`):

```ld
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS {
  . = 0x80000000;
  .text.init : { KEEP(*(.text.init)) }
  . = ALIGN(0x1000); .text : { *(.text .text.*) }
  . = ALIGN(0x1000); .data : { *(.data .data.*) }
}
```

Build and run:

```bash
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64d -nostdlib -nostartfiles \
  -static -fno-pic -Wl,-T,link.ld -Wl,-e,_start -o poc.elf poc.S
./build/verilator-compile/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so -C 200000
./build/verilator-compile/emu -i poc.elf --no-diff -C 200000
```

The reported sweep also reproduces with `vl=4`, with `vle16.v`, and with aligned unmapped bases `0x10000` (fault at `0x10008`). Controls reported not to reproduce: `vstart=0`, `vstart>=2`, `vstart=vl`, and `vl=0`.

The `--no-diff` replay also reaches the trap-handler exit with VS Dirty (`pc=0x80000098`); its log is included in the archive.

### Additional context

AI assistance: OpenAI Codex (GPT-6), used to prepare and check this report. The reported PoC was rebuilt and replayed during this template audit. The report concerns the observed behavior and reproduction steps.

Related: #6561 and #6602 cover `vl=0` vector-memory VS Dirty behavior; this report has a different trigger (`vl>=2`, `vstart=1`, load access fault on the first active element).
