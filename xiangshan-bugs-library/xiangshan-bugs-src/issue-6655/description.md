### Before start

- [x] I have read the RISC-V ISA Manual and XiangShan's contributing guidance, and believe this is a XiangShan issue: the aligned RAM case must either complete or raise an architecturally valid exception; it cannot stop making progress.
- [x] I searched related issues and PRs. See “Additional context” for the closest reports.
- [x] I reproduced the issue on the latest `kunminghu-v3` head (`09453ff8f44e9d2b526e6149c60e1dc3f08c7192`) using a clean independent checkout.
- [x] This report was prepared with OpenAI Codex (GPT-6); a separate agent rebuilt the inline source and emulator from this text on each branch.

### Branch

`kunminghu-v3`; `kunminghu-v2` is a comparison run.

### Describe the bug

A legal, unmasked `vsse32.v v8, (x6), x7` stores four 32-bit zero elements to ordinary RAM in M-mode (`SEW=32`, `LMUL=1`, `vl=4`, `vstart=0`, `x7=4`). On the latest tested `kunminghu-v3` head `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`, with an **aligned** base `x6=0x80010000`, the DUT stops committing at the vector store. The preceding `li x7,4` commits at `0x80000030`; the `vsse32.v` at `0x80000034` and the following good-trap instruction do not. DiffTest's no-commit watchdog fires after 15,000 cycles and aborts (`instrCnt=19`, `cycleCnt=23331`). The same ELF without DiffTest reaches the 30,000-cycle cap with `instrCnt=19` and no good trap. That independent run shows the stall is in the DUT execution, not DiffTest's reference comparison.

```text
[17] commit pc 0000000080000030 inst 00400393 ...  # li x7,4: last commit
No instruction of core 0 commits for 15000 cycles, maybe get stuck
Core-0 instrCnt = 19, cycleCnt = 23,331
```

The original discovery used `x6=0x80010002`. A controlled rerun reproduces the no-commit signature **both** at `0x80010002` and at aligned `0x80010000` on the latest v3. Misalignment is therefore not the trigger, and the aligned case is the primary reproducer. Replacing only the target `vsse32.v v8, (x6), x7` with `vse32.v v8, (x6)` at aligned `0x80010000` reaches `HIT GOOD TRAP` (`cycleCnt=8327`) on the same latest v3 build. On latest `kunminghu-v2` `e7bab53e66dfb3c4a1d11cf9519b0396f8576cae`, all three target variants also reach `HIT GOOD TRAP`.

| Inline-source run | Latest v3 `09453ff8` | Latest v2 `e7bab53e` |
| --- | --- | --- |
| aligned `vsse32.v`, DiffTest | no-commit watchdog, ABORT; 19 commits / 23,331 cycles | GOOD TRAP; 8,358 cycles |
| same aligned ELF, `--no-diff` | 30,000-cycle cap, still 19 commits, no good trap | GOOD TRAP; 8,358 cycles |
| misaligned `vsse32.v`, DiffTest | no-commit watchdog, ABORT; 20 commits / 23,330 cycles | GOOD TRAP; 8,383 cycles |
| aligned `vse32.v` control, DiffTest | GOOD TRAP; 8,327 cycles | GOOD TRAP; 8,355 cycles |

The original `0x80010002` discovery was on older v3 commit `a13efc8775`; the table above gives the latest-branch result. DiffTest steps NEMU once after its watchdog fires, so any register difference printed afterward is diagnostic and does not mean the DUT retired the store. The tests establish the no-commit symptom, not its RTL root cause.

### Expected behavior

For the aligned RAM case, `vsse32.v` should store four zero words at `0x80010000`, `0x80010004`, `0x80010008`, and `0x8001000c`, retire, and reach `HIT GOOD TRAP`. An implementation may complete or trap on the optional misaligned variant, but the aligned primary case needs no misaligned-access support and must make progress.

### Environment

- Hardware: AMD EPYC 7543; 128 logical CPUs; 247 GiB RAM; 3.7 TiB workspace volume.
- Software: Linux x86-64; Clang `22.1.8` for emulator C++; GCC `15.2.0` also installed (fails the pinned DiffTest flag noted below); RISC-V GCC `14.2.0+19`; Verilator `5.038`; Mill `0.12.17` on v3 and `0.12.3` on v2; OpenJDK `25.0.2` for the v3 Mill build and `17.0.20.1` for v2.
- XiangShan v3: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`, `DefaultConfig`, clean checkout; aligned and misaligned strided stores stall.
- Comparison v2: `e7bab53e66dfb3c4a1d11cf9519b0396f8576cae`, `DefaultConfig`, clean checkout; both strided forms and the unit-stride control reach `HIT GOOD TRAP`.
- NEMU and difftest: pinned v3 `ready-to-run` `4cf9983569432035d41956c65e6a58724b5954be` and `difftest` `4c64fe86b17e0564232b52bb3800d9b15c45a3a0`; v2 `ready-to-run` `c4114ce3fffcd5c147c525014b40f1c841347238` and `difftest` `3729300ae233816d332d057f170472ebe35147b0`.
- Build: `make init`, then `NOOP_HOME="$PWD" make emu CONFIG=DefaultConfig CC=clang CXX=clang++ -j24` on each branch. A clean checkout needs `NOOP_HOME`; the pinned DiffTest C++ flags require Clang in this environment.
- Run: commands below. No RTL or emulator source modifications.

### To Reproduce

The following source, linker script, build command and run command are complete; no binary or other attachment is needed. Start from a clean XiangShan checkout of the branch being tested and initialize its pinned submodules. The RISC-V GCC must support `rv64gcv`.

```sh
git clone --branch kunminghu-v3 --single-branch --depth 1 \
  https://github.com/OpenXiangShan/XiangShan.git xs-v3
cd xs-v3
make init
export NOOP_HOME="$PWD"
make emu CONFIG=DefaultConfig CC=clang CXX=clang++ -j24

cat > repro.S <<'EOF'
    .option norvc
    .section .text.init
    .globl _start
_start:
    # Permit ordinary RAM accesses even if MPRV/MPP differs after reset.
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0

    li t0, 0x600
    csrs mstatus, t0           # VS=Dirty
    li t1, 4
    vsetvli zero, t1, e32, m1, ta, ma
    vmv.v.i v8, 0

#ifndef BASE_ADDR
#define BASE_ADDR 0x80010000
#endif
    li x6, BASE_ADDR
    li x7, 4
target:
    vsse32.v v8, (x6), x7
    .4byte 0x0000006b       # DiffTest good trap
hang:
    j hang
EOF

cat > link.ld <<'EOF'
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
EOF

riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64 \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -Wl,-e,_start -o repro.elf repro.S
riscv64-unknown-elf-objdump -d repro.elf | sed -n '/<target>:/,+3p'
./build/emu -i repro.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so \
  -C 30000 --dump-commit-trace
./build/emu -i repro.elf --no-diff -C 30000
```

On the tested RISC-V GCC `14.2.0+19`, the aligned case disassembles as `80000034: 0a736427  vsse32.v v8,(t1),t2`; the misaligned variant places this instruction at `0x80000038` because `li` takes one extra machine word. Check the `<target>` label in your own ELF if using a different toolchain. `-C 30000` is the run's cycle cap; the 15,000-cycle no-commit message is the DiffTest watchdog, not an architectural deadline. Under `--no-diff`, exit code 0 at the cycle cap is **not** a pass; look for `HIT GOOD TRAP`.

Optional checks using the **same** source:

```sh
# The originally found misaligned base, whose support is platform-dependent:
riscv64-unknown-elf-gcc -DBASE_ADDR=0x80010002 -march=rv64gcv -mabi=lp64 \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -Wl,-e,_start -o repro-misaligned.elf repro.S
./build/emu -i repro-misaligned.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 30000

# Aligned unit-stride control, differing only at the target instruction:
sed 's/vsse32.v v8, (x6), x7/vse32.v v8, (x6)/' repro.S > repro-control.S
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64 \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -Wl,-e,_start -o repro-control.elf repro-control.S
./build/emu -i repro-control.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 30000
```

### Additional context

Related reports have different triggering conditions: [#6262](https://github.com/OpenXiangShan/XiangShan/issues/6262) is a segmented **load** to an unmapped page, [#6605](https://github.com/OpenXiangShan/XiangShan/issues/6605) is strided/indexed access through a denied PMP entry, and [#6296](https://github.com/OpenXiangShan/XiangShan/issues/6296) is an exception-cause mismatch for a misaligned vector store in an I/O region. [#5279](https://github.com/OpenXiangShan/XiangShan/issues/5279) concerns a unit-stride `vse32.v` store committing with an empty write mask. This report's aligned, permitted RAM store has neither a faulting page nor denied PMP entry and does not retire at all. These observations do not prove a distinct RTL root cause.

[PR #5881](https://github.com/OpenXiangShan/XiangShan/pull/5881) (merged into `kunminghu-v2`) repaired a previously reported **misaligned** vector-store stall by propagating `blockScaleIssue` to both store units. The aligned primary reproducer here avoids relying on that condition. The two mechanisms have not been compared at RTL level.

This draft was prepared with OpenAI Codex (GPT-6). Separate agents rebuilt the inline source and the pinned emulators from the text on the latest v3 and v2 heads, then ran the listed controls.
