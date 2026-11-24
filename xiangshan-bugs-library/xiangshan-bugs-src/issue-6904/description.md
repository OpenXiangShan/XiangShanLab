### Before start

- [x] Reviewed the RVV 1.0 specification and XiangShan contributing/documentation guidance. The expected behavior below follows the ordinary vector store rules.
- [x] Searched previous issues and open PRs. Related reports **do exist**: #6655 and #6659. This report supplies a standalone segmented/no-active-elements reproducer; it does **not** claim a distinct RTL root cause. Please consolidate with #6655 if appropriate.
- [x] Reproduced on the latest default branch, `kunminghu-v3@ac7b34075a910809dde961abda8eda21b8941f52`, in a clean independent checkout with pinned dependencies and unmodified `DefaultConfig`. The default branch was checked again after the build and still pointed to this commit.
- [x] This report was prepared with OpenAI Codex (GPT-6), and its source, disassembly, logs, and conclusions were checked against the actual runs.

### Branch

kunminghu-v3

### Describe the bug

A legal masked `vssseg2e64.v v5,(t1),t2,v0.t` with `SEW=64`, `LMUL=1`, `vl=1`, and `vstart=1` does not retire. There are no body elements; VLMAX=2, so vstart=1 is within the supported element range. The base is aligned ordinary RAM (`0x80020940`), stride is only 16 bytes, and the initialized mask is all ones. The instruction should complete without accessing element memory, reset `vstart`, and reach the success marker.

The reproducer is standalone M-mode assembly. It enables VS, opens PMP, initializes the vector sources, sets a failure trap handler, and tests `vstart` and memory after the target. It does not use the fuzzer wrapper, vector initialization loads, privilege transitions, AMOs, or self-modifying code.

**Important scope:** the active-element and all-masked controls also stall in both the clean latest build and the historical build. Therefore `vstart>=vl` is an observed failing state, **not an established necessary trigger**. Related #6655 already reports aligned ordinary-RAM `vsse32.v` stalls. The root cause has not been identified.

Clean latest-branch results (`ac7b34075a`, unmodified `DefaultConfig`):

| Workload | Observed behavior | Instructions / cycles |
| --- | --- | --- |
| vssseg2e64, vstart=vl=1, mask=all ones | 15,000-cycle no-commit watchdog; target does not retire | 29 / 23,373 |
| vssseg2e64, vstart=0, unmasked | 15,000-cycle no-commit watchdog; target does not retire | 29 / 23,364 |
| vssseg2e64, vstart=0, mask=all zeros | 15,000-cycle no-commit watchdog; target does not retire | 29 / 23,364 |
| vsse64, vstart=vl=1, mask=all ones | 15,000-cycle no-commit watchdog; target does not retire | 29 / 23,373 |
| vsseg2e64, vstart=vl=1 | Target retires but issues a store when REF has none; store-commit mismatch | 30 / 8,391 |
| vse64, vstart=vl=1 | Target retires but issues a store when REF has none; store-commit mismatch | 30 / 8,391 |
| Original #6655 source: aligned vsse32, vstart=0 | 15,000-cycle no-commit watchdog; target does not retire | 19 / 23,330 |
| Same strseg_eq ELF, --no-diff | 30,000-cycle cap, no GOOD TRAP, target does not retire | 29 / 30,000 |

For the primary case, the last committed payload instruction is `csrwi vstart,1` at `0x80000058`; the `vssseg2e64` at `0x8000005c` does not appear in the commit trace. The CROB diagnostic points to `headPC=0x8000005c`, and reports `formerCnt=1`, `formerUopNum=2`. The active and all-masked controls stop at the same target. These observations describe incomplete progress/commit; they do not identify the RTL root cause.

All DiffTest cases exit 1. The no-diff run exits 0 at the cycle cap, which is **not** a pass. The strided cases never reach the following readback assertions. The unit-stride controls retire, but fail the store comparison; they are **not** functional passes.

Emulator SHA-256: `4196c6de1df8dca409abdcb1badecdbd4af83da1733b2957bea51c6b097c8d2d`. The input ELF for the primary and no-diff runs is identical (SHA-256 `3818c7085120522e86ee40fb357c1a4aa04cb3652729c267305ac5a2d95e8a30`).


### Expected behavior

For this legal ordinary vector store, with `vstart==vl`, there are no active elements and no element stores. The instruction completes, clears `vstart` to zero, and reaches GOOD TRAP with `a0=0`. The active-element control should store its selected values and complete. A core must not permanently stop committing.

RVV 1.0: [prestart/body/active definitions](https://github.com/riscvarchive/riscv-v-spec/blob/master/v-spec.adoc#prestart-active-inactive-body-and-tail-element-definitions) and [vstart](https://github.com/riscvarchive/riscv-v-spec/blob/master/v-spec.adoc#vector-start-index-csr-vstart). This is not a whole-register store, which uses a different effective length.

### Environment

- Hardware: AMD EPYC 7543, 128 logical CPUs, 247 GiB RAM, no swap, 3.7 TiB workspace filesystem.
- OS: Ubuntu 26.04.1 LTS, x86-64.
- RISC-V GCC: `riscv64-unknown-elf-gcc (14.2.0+19) 14.2.0`.
- Emulator compiler: Clang 22.1.8.
- Java used by Mill: OpenJDK 25.0.5.0.3 (system JAVA_HOME).
- Mill: 0.12.17.
- Verilator: 5.038; firtool: 1.149.0.
- XiangShan: clean `kunminghu-v3@ac7b34075a910809dde961abda8eda21b8941f52`, `DefaultConfig`, one core, VLEN=128. No source modifications.
- DiffTest: `ecdf41e225a93699b9a1141ec4d0fc1ff485b890`.
- NEMU: the pinned prebuilt interpreter from `ready-to-run@4cf9983569432035d41956c65e6a58724b5954be`; SHA-256 `9188e7dda30709962f0bfbc1dd87cea73d88a18c92c30892f93ed529c22def43`.
- Build command: `NOOP_HOME="$PWD" make emu CONFIG=DefaultConfig CC=clang CXX=clang++ -j8` (Mill 0.12.17 on PATH).
- Historical discovery binary: local instrumented build corresponding to `a253c11639`, SHA-256 `69dce87d9fb447e69b2cc5d38e9bc9b35c05d89792275393fd18305c73732ed5`. Historical results are separate from the clean latest build.

### To Reproduce

Build the clean commit above with its pinned submodules and `DefaultConfig`. Save the following as `repro.S` (the linked evidence also provides the exact source and encoded ELF):

```asm
.option norvc
.section .text.init
.globl _start
_start:
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    la t0, failed
    csrw mtvec, t0
    li t0, 0x600
    csrs mstatus, t0
    vsetivli zero, 2, e64, m1, tu, mu
    vmv.v.i v0, -1
    vmv.v.i v5, 1
    vmv.v.i v6, 2
    li t1, 0x80020940
    li t2, 16
    mv x31, t1
    sd zero, 0(t1)
    sd zero, 8(t1)
    vsetivli zero, 1, e64, m1, tu, mu
    csrwi vstart, 1
.globl target
target:
    vssseg2e64.v v5, (t1), t2, v0.t
    csrr t0, vstart
    bnez t0, failed
    ld t3, 0(t1)
    li t4, 0
    bne t3, t4, failed
    ld t3, 8(t1)
    li t4, 0
    bne t3, t4, failed
    li a0, 0
    .4byte 0x0000006b
1:  j 1b
failed:
    li a0, 1
    .4byte 0x0000006b
2:  j 2b
```

Save this linker script as `link.ld`:

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

From the XiangShan checkout:

```sh
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64d \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -o repro.elf repro.S
riscv64-unknown-elf-objdump -d repro.elf
mkdir -p run-diff/build run-nodiff/build
NOOP_HOME="$PWD/run-diff" ./build/emu -i "$PWD/repro.elf" \
  --diff "$PWD/ready-to-run/riscv64-nemu-interpreter-so" \
  -C 30000 --dump-commit-trace
NOOP_HOME="$PWD/run-nodiff" ./build/emu -i "$PWD/repro.elf" \
  --no-diff -C 30000 --dump-commit-trace
```

The tested GCC puts the target at `0x8000005c`; check your disassembly. The 15,000-cycle no-commit threshold is the DiffTest watchdog. Under `--no-diff`, exit code 0 at the cycle limit is not a pass: check the commit trace and the absence of GOOD TRAP. DiffTest advances REF once after its watchdog fires; any subsequent `vstart` difference does not mean DUT retired the instruction.

Evidence files (hosted as a public Gist, with separate logs rather than pasted log output): [https://gist.github.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005](https://gist.github.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005).

- [Primary DiffTest log](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/fe2fe6c5e6180ae12a68d12b19d4f09784f808de/strseg_eq.log).
- [Same-ELF no-diff log](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/f3edc1a2eca96ed51a8c93d6b847348742b98759/strseg_eq_nodiff.log).
- [All eight results](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/8bee06f777bfcd2d913d531c34e7f0288829c600/results.json).
- [Exact primary ELF, Base64 encoded](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/68d09df44301363f9e7b78b7954a17774e98cd52/strseg_eq.elf.b64).
- [Source](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/77884d462e3ebe7a03254aec3713c3ef346bdd89/strseg_eq.S).
- [Environment and emulator hash](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/71003fb9acaea57eb8ab457d792f8f3d827c8ce5/environment.json).
- [Checksums for decoded ELF files](https://gist.githubusercontent.com/msuadOf/075e31a87ea7cd765a3d514ced0e0005/raw/ea73c753a9e8ac3997038d8338b4a280157a543a/binary-sha256.txt).

The evidence also includes every control source/disassembly/encoded ELF/log, pinned submodule commits, and a compressed build log. Gist stores text, so the exact binary is supplied as Base64: `base64 -d strseg_eq.elf.b64 > strseg_eq.elf`; verify it against `binary-sha256.txt`. Log colors/PERF counters were removed and local path prefixes replaced; commit and error lines were preserved.

### Additional context

- #6655 is the closest report: an aligned, permitted-RAM non-segment strided store stops committing. The original inline source also reproduces on the clean latest build as well as the historical binary. A separate root cause is not established.
- #6659 already covers unit-stride stores incorrectly writing with `vstart==vl`. Both historical and clean-latest `vse64`/`vsseg2e64` controls here retire but produce a store-commit mismatch; they are not passing controls.
- #6597 describes the VSQ retirePtr assertion after a multi-register unit-stride segment store plus an AMO. This reproducer has no AMO or such assertion.
- #6598 primarily lists arithmetic vector families and explicitly describes vstart-independent stalls. Its broad title alone is not evidence of a shared cause.
- Open #6628 concerns vector memory exception handling/old-data merging; #6652 concerns out-of-window vector STD issue; #6653 changes a performance counter. Their descriptions do not establish that they fix this no-active-elements reproducer; they were not applied in the stock build.

I understand that vector correctness in kunminghu-v3 is under active development and temporarily deprioritized. This report records a small reproducible case, rather than claiming a new root cause or requesting a particular priority.

AI assistance: OpenAI Codex (GPT-6) prepared the assembly, performed the listed emulator runs and issue/PR checks, and drafted this report. The account owner requested submission after reviewing the verification summary.
