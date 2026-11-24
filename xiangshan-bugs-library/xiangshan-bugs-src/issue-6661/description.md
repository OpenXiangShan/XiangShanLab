### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A leaf PTE with `V=0` and `G=1` takes a page fault, as it should. The page-table cache keeps that fault as a global entry. There are two consequences, each with its own workload.

`t47_pte_g_stale_fault`. ASID 1 loads from the page and faults. The M-mode handler rewrites the PTE to a valid one (same G bit), executes `sfence.vma x0, t0` with `t0 = 1` (the current ASID), and returns to retry the load. The retry faults again. Spike takes one fault and the retry succeeds.

`t49_pte_g_cross_asid`. ASID 1 loads from the page and faults. The handler rewrites the PTE to a valid, non-global one, switches `satp` to ASID 2 with the same root table and no fence at all, and returns. Under ASID 2 the load faults again. Spike walks the now-valid PTE and the load succeeds.

Sv39, identity map, PMP entry 0 covering everything with RWX. The test page is `0x80200000`, mapped by a 4 KiB leaf in a table the tests edit directly. The result bytes are `[number of page faults, loaded byte]`.

```
                    Spike     kunminghu-v3 (Min, Def)   kunminghu-v2
t47   RESULT:       01 5a     02 00                     02 00
t49   RESULT:       01 5a     02 00                     02 00
```

The bundled NEMU behaves like the RTL (two faults), and the difftest run reports `HIT GOOD TRAP`. The harness self-check and Spike show the difference.

### Expected behavior

In the RISC-V Privileged Architecture Specification, Section "Addressing and Memory Protection", Sv32 page-table entry description (`[[translation]]`):

> The V bit indicates whether the PTE is valid; if it is 0, all other bits in the PTE are don’t-cares and may be used freely by software.

Section "Supervisor Memory-Management Fence Instruction" (`[[sfence.vma]]`):

> This specification permits the caching of PTEs whose V (Valid) bit is clear. Operating systems must be written to cope with this possibility, but implementers are reminded that eagerly caching invalid PTEs will reduce performance by causing additional page faults.

The quotations above are direct specification statements. The report's inference is that, in the absence of a valid ancestor PTE marking the mapping global, the raw G bit of a `V=0` PTE cannot by itself justify global ownership of its cached fault. A cached entry may serve a later lookup only when its ASID matches or when it is associated with a global mapping. Both workloads initialize the valid ancestor PTEs with G=0, so the invalid target PTE's software-owned G bit should not let its cached fault bypass ASID matching or ASID-qualified invalidation.


### Environment

- Hardware
  - CPU: AMD EPYC 9355 32-Core Processor
  - Memory (GB): 245
  - Storage (GB): 877
- Software
  - Operating system: Ubuntu 25.04
  - gcc version: gcc (Ubuntu 14.2.0-19ubuntu2) 14.2.0
  - clang version: not used
  - java version: openjdk version "21.0.12.1" 2026-08-18 LTS (Temurin)
  - mill version: Mill Build Tool version 0.12.17
  - verilator version: Verilator 5.032 2025-01-01 rev (Debian 5.032-1)
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192` (kunminghu-v2: `e7bab53e66dfb3c4a1d11cf9519b0396f8576cae`)
  - NEMU commit id (if difftest failed with NEMU): `ready-to-run/riscv64-nemu-interpreter-so` from ready-to-run `4cf9983569432035d41956c65e6a58724b5954be` (difftest does not fail, NEMU matches the RTL)
  - SPIKE commit id (if difftest failed with SPIKE): Spike from `riscv-isa-sim` master, run offline with `--isa=rv64gcv_zicbom_zacas_zvl128b_svpbmt`
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig EMU_THREADS=8 -j60 OPT_FAST=-O3` (also `CONFIG=DefaultConfig`)
  - Run command: `./build/emu -i t47_pte_g_stale_fault.bin --no-diff -C 200000`
  - Workload: attached (`workload/`)

### To Reproduce

1. Build `t47_pte_g_stale_fault.S` and `t49_pte_g_cross_asid.S` (they include `common_xs.S` and `paging.S`, and `link.ld` is attached), or use the attached binaries.
2. Run on the emu and read the UART line `RESULT:`. `02 00` means two page faults and no data. `01 5a` is the expected result.
3. Run the same `.elf` on Spike for comparison. Spike takes one `trap_load_page_fault`, then the load returns `0x5a`.

The handler in the workloads counts faults in `s5`, repairs the PTE on the first fault, and gives up on the second, which terminates the test.

### Additional context

[xs_invalide_g_pte.zip](https://github.com/user-attachments/files/32894114/xs_invalide_g_pte.zip)
