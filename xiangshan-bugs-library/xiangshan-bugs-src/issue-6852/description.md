### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [ ] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

Reproduced on kunminghu-v2 at e7bab53e66 (KunminghuV2Config). The commits on kunminghu-v2 since then (up to 7b16188f7) don't touch `CSROoORead.scala` or the rename logic that uses it. I haven't tried kunminghu-v3. Its `CSROoORead.scala` list has changed, but it still has no counters.

### Branch

kunminghu-v2

### Describe the bug

A `csrr` of `minstret` or `instret` returns a value that doesn't include all older instructions. Two reads 8 instructions apart should differ by exactly 9 (the first `csrr` and the 8 instructions after it). On XiangShan they usually differ by 0, and sometimes by 5 or by 34.

The program reads the counter, runs 8 instructions, reads it again, and repeats this 4 times per case:

| Case | 8 instructions between the reads | Observed differences (required 9 each) |
|---|---|---|
| A | independent `addi`, `minstret` | 5 0 0 0 |
| B | independent `addi`, `instret` | 5 0 0 0 |
| C | dependent `mul` chain | 5 5 0 34 |
| D | `ld` and 7 instructions that use it | 0 0 0 0 |
| E | 2 `div` and 6 `addi` | 0 0 0 0 |

The [riscv-arch-test](https://github.com/riscv/riscv-arch-test) Zicntr, Sm_Zicntr and Sscofpmf tests show the same thing for the HPM counters: `mhpmcounter11` read right after the load it counts is still 0, and its overflow bit is still clear.

### Expected behavior

Every difference is 9. The Sail model (0.16) gives 9 in every case.

Zicsr, CSR instructions:

> Some CSRs, such as the instructions-retired counter, instret, may be modified as side effects of instruction execution. In these cases, if a CSR access instruction reads a CSR, it reads the value prior to the execution of the instruction.

Zicsr, CSR Access Ordering:

> In particular, unless specified otherwise, a CSR access is performed after the execution of any prior instructions in program order whose behavior modifies or is modified by the CSR state and before the execution of any subsequent instructions in program order whose behavior modifies or is modified by the CSR state. Furthermore, an explicit CSR read returns the CSR state before the execution of the instruction.

Every retired instruction modifies `minstret`, so a read of it must follow all older instructions.

### Environment

- Hardware
  - CPU: 32 cores, x86_64
  - Memory (GB): 185
- Software
  - Operating system: AlmaLinux 9.7
  - gcc version: riscv64-unknown-elf-gcc 15.2.0 (for the test)
- Repo
  - XiangShan commit id: `e7bab53e66dfb3c4a1d11cf9519b0396f8576cae` (kunminghu-v2)
  - difftest commit id: `3729300ae233816d332d057f170472ebe35147b0`
- Build & Run
  - Build command: `make emu CONFIG=KunminghuV2Config` (Verilator 5.052; the default `--issue E.b`)
  - Run command: `./emu -i instret.elf --no-diff -C 2000000`

### To Reproduce

Source, prebuilt ELFs, a run script, the Sail config and the observed output are in https://github.com/davidharrishmc/xiangshan-repros/tree/main/xiangshan-instret-read-order (tarball: https://github.com/davidharrishmc/xiangshan-repros/raw/main/xiangshan-instret-read-order.tar.gz).

1. `make` (needs riscv64-unknown-elf-gcc), or use the prebuilt `instret.elf`.
2. `XIANGSHAN_EMU=/path/to/emu ./run.sh`

The program prints one line per case and ends with a bad trap if any difference isn't 9.

### Additional context

`backend/fu/NewCSR/CSROoORead.scala` lists the CSRs whose `csrr` must wait for older instructions (`waitForwardInOrderCsrReadList`). `backend/rename/Rename.scala:294-295` lets a `csrr` of any CSR not on the list execute without waiting for them. The list has fflags, fcsr, vstart, the status registers and the interrupt registers, but none of the counters: cycle, instret, the hpmcounters, and their m* forms. Adding the counters to the list (at least `instret`/`minstret` and the hpmcounters) should fix it.

`mcycle` and `time` may arguably be read early, since the spec doesn't define which cycle a read samples. `instret` and event counters count events of older instructions, so a read has to wait for them.
