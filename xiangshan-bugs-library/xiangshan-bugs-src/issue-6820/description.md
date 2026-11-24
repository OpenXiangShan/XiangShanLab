### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

`kunminghu-v3`

### Describe the bug

A program running on `kunminghu-v3` with `TestkitV3Config`, one hart, native small-cache parameters, and Verilator triggers a DTLB duplicate-refill assertion after remapping a global leaf page and executing an address-specific `sfence.vma`.

The program creates two address spaces, maps a virtual-address window to bank A using global leaf PTEs (`G=1`), switches address spaces, remaps the same window to bank B, and executes an address-specific `sfence.vma`. A subsequent access to the same VA triggers:

```text
Assertion failed at build/rtl/TLBFA_1.sv:13345 [ERROR][time=18688] SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner.dtlb_ld_tlb_ld.entries.page_ldtlb_storage_fa: ldtlb_storage refill, duplicate with existing entries
```

The corresponding assertion is in `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:191`:

```scala
XSError(io.w.valid && Cat(w_hit_vec).orR, s"${parentName} refill, duplicate with existing entries")
```

#### Waveform evidence

- At `t=18192`, entry 32 is filled with `tag=9216`, `s2xlate=0`, and `pteidx=bit3`. Its valid bit remains set (`v_32=1`).
- At `t=18683`, a new refill has `w_hit_vec_wb_vpn_32=9216`, matching the existing entry 32 and triggering the duplicate-refill assertion.

This indicates that the address-specific `sfence.vma` did not invalidate the cached global leaf translation.

The `hfence.vvma` variant exhibits the same failure at `t=23310`, with `s2xlate=3` and `pteidx=bit7`. All four combinations below trigger the assertion in both the `normal` and `mutant` runs:

| Fence variant | Test case |
| --- | --- |
| `sfence_vma` | `global_leaf_address` |
| `sfence_vma` | `global_leaf_all` |
| `hfence_vvma` | `global_leaf_address` |
| `hfence_vvma` | `global_leaf_all` |

#### Comparison runs

- The same image passes on KMHv2 at commit `fe2288cb09`, exiting with `a0=0` after 48,352 cycles and no assertion.
- The failure was also reproduced on default-branch commit `a0c2a26cbe` on November 2, 2026, at `time=18678`. See the attached `head-a0c2a26cbe.log`.

### Expected behavior

`sfence.vma rs1, x0` must invalidate cached translations for the specified VA across all address spaces, including global mappings (`G=1`). A subsequent access should walk the page tables again, and the new refill should not coexist with a still-valid entry for the same tag.

### Environment

#### Hardware

| Component | Details |
| --- | --- |
| CPU | AMD EPYC 7H12 64-Core Processor |
| Memory | 1,030 GB (1.0 TiB) |
| Storage | 250 GB available on an 879 GB partition |

#### Software

| Component | Version |
| --- | --- |
| Operating system | Ubuntu 24.04.3 LTS |
| Kernel | `6.17.0-23-generic x86_64` |
| GCC | 13.3.0 |
| Clang | Not used |
| Java | OpenJDK 21.0.2 |
| Mill | 0.12.17 |

#### Repository revisions

- XiangShan: `6705b63685d301f8c0960f7cbcc8b964660dc58e`
- Also reproduced on: `a0c2a26cbe3c862175174bad03a1a23c406315ed`
- NEMU: Not used; simulation runs with `--no-diff`.
- Spike: Not used.

`TestkitV3Config.scala` only composes the native `WithL3DebugConfig ++ DefaultConfig` small-cache parameters; CPU logic is unchanged.

The environment report was generated with `./scripts/bug-report.sh` and is attached as `bug-report.tar.gz`.

### To Reproduce

1. Build the emulator:

   ```bash
   make emu CONFIG=TestkitV3Config NUM_CORES=1 EMU_THREADS=1 SPLITVIEW=1 WITH_DRAMSIM3=0 -j32
   ```

2. Run the test image:

   ```bash
   ./build/emu --no-diff --ram-size=128MB -C 100000000 -i image.bin -b0 -e0
   ```

3. Observe the duplicate-refill assertion around `time=18688` (`time=18678` on commit `a0c2a26cbe`; approximately `time=23310` for the `hfence.vvma` variant).

As a comparison, the same `image.bin` exits successfully on the KMHv2 build.

Reproducer and supporting files: `image.bin` (test image), `prog.elf`, `prog.c` (source), `sim.log`, `sim-hfence.log`, `head-a0c2a26cbe.log`, and `TIMELINE.md` (waveform timing references). Available download links are listed below.

### Additional context

The following related issues were reviewed; none describes the same symptom:

- #6661: Invalid PTE with `G=1` is cached as a global PTE.
- #6698 / #6695: Ignore PTE `G` when `V` is clear.
- #4811: Global entries should always hit across ASID switches.
- #6745 / #6746: M-mode `SFENCE.VMA` with `MPRV`/`MPV`, and decoding with the current `V` value.

The DUT uses private DiffTest host-side patches in three files under `src/test/csrc/{difftest,emu,verilator}` to record only the exit code and UART output. These patches do not modify CPU RTL. The assertion originates in `TLBStorage.scala` itself and is unrelated to the observation patches.

#### Attachments

- [bug-report.tar.gz](https://github.com/user-attachments/files/34258347/bug-report.tar.gz): Environment report.
- [head-a0c2a26cbe.log](https://github.com/user-attachments/files/34258351/head-a0c2a26cbe.log): Reproduction log for commit `a0c2a26cbe`.
- [prog.c](https://github.com/user-attachments/files/34258350/prog.c): Test source.
- [sim.log](https://github.com/user-attachments/files/34258352/sim.log): `sfence.vma` simulation log.
- [sim-hfence.log](https://github.com/user-attachments/files/34258348/sim-hfence.log): `hfence.vvma` simulation log.
- [TIMELINE.md](https://github.com/user-attachments/files/34258349/TIMELINE.md): Waveform timing references.
