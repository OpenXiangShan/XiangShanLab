## Before start

- [x] I have read the RISC-V ISA Manual and XiangShan Documents, and I believe this is a XiangShan RTL issue.
- [x] I have searched relevant issues and PRs and read the candidate issue bodies; I did not find another report with the same observable behavior.
- [x] I reproduced this on the latest commit of the default branch: `f91bdbb8c69e2ad9e041a7dcd92146f5a397d61c` (`kunminghu-v3`, remote `HEAD` at the time of testing).
- [x] AI-assisted report: the author must manually verify every field before checking this box.

## Branch

`kunminghu-v3`

## Describe the bug

After configuring a locked TOR entry with `X=0` to deny `[0x80000000, 0x80001000)`, the PoC executes:

```asm
la  t4, TARGET
jr  t4                # TARGET = 0x80000040
```

The first instruction at `TARGET` is:

```asm
TARGET:
li  t1, 0x5a
```

Fetching this instruction should raise an instruction access fault, but XiangShan executes it and reaches `FAIL`. The waveform shows `res_pmp_is_match=0` for the fetch at `0x80000040`.

ps. The relevant execution order in the PoC is:

  ```text
  0x80000000: j 0x80002000       # PMP is OFF; this fetch is allowed; it get out of deny region `[0x80000000, 0x80001000)` !!
  0x80002000: SAFE               # outside the range that will be denied
  0x8000200c: csrw pmpaddr0, zero
  0x80002018: csrw pmpaddr1, t1  # TOR upper bound = 0x80001000
  0x80002024: csrw pmpcfg0, t2   # entry 1: L=1, A=TOR, R=W=X=0
  0x80002028: sfence.vma
  0x8000202c: fence.i
  0x80002050: jr t4              # t4 = 0x80000040; this instruction is allowed
  0x80000040: li t1, 0x5a        # first fetch in the TOR range; should fault

  The jr instruction itself executes from the safe region. The bug is that the following instruction fetch at 0x80000040 is not blocked by the locked TOR entry.

## Expected behavior

According to the RISC-V Privileged Architecture PMP rules, a locked (`L=1`) TOR entry with `X=0` also applies to M-mode. When fetching from `0x80000040` inside `[0x80000000, 0x80001000)`, the core should raise an instruction access fault (`mcause=1`) and the trap handler should record `mepc=0x80000040`; the target code must not execute.

## Environment

```text
- Hardware
  - CPU: 11th Gen Intel(R) Core(TM) i5-11400 @ 2.60GHz
  - Memory: 31 GiB
  - Storage: 3.4 TiB total, 173 GiB free on the workspace filesystem
- Software
  - OS: Ubuntu 22.04.3 LTS
  - Python: 3.14.7
  - gcc: 11.4.0
  - clang: 22.1.8
  - Java: OpenJDK 25.0.2
  - mill: 0.12.17 (wrapper runtime: OpenJDK 17.0.20)
  - RISC-V GCC: riscv64-unknown-elf-gcc 15.1.0
- Repo
  - XiangShan commit: `f91bdbb8c69e2ad9e041a7dcd92146f5a397d61c`
- Build & Run
  - DUT: `xiangshan/build/verilator-compile/emu` (rebuilt from the commit above; MinimalConfig, one core)
```

## To Reproduce

The attachment contains the prebuilt ELF; no build step is required. From the extracted `xiangshan-pmp-tor-poc/` directory, run:

```sh
/path/to/emu --dump-commit-trace -C 120000 \
  -i pmp_tor_locked_ifetch_sfence.elf \
  --diff /path/to/riscv64-nemu-interpreter-so
```

On commit `f91bdbb8`, XiangShan executes `0x80000040` and reaches `FAIL` instead of raising an instruction access fault.

## Additional context

[xiangshan-pmp-tor-locked-ifetch-poc.tar.gz](https://github.com/user-attachments/files/31453771/xiangshan-pmp-tor-locked-ifetch-poc.tar.gz)
