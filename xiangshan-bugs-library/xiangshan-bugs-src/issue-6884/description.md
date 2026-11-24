### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [ ] I have searched previous issues and PRs and did not find anything relevant. — **Not checked: #6670 is closely related, see below.**
- [ ] I have reproduced the issue using the latest commit on the default branch. — **Not checked: reproduced on `a13efc87` (older than the `09453ff8` used in #6670); we have not rebuilt on the latest commit.**
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。 — The test cases were generated with AI-assisted tooling from the Sail RISC-V model; the reproducer below was built and run by us exactly as written, and the results are copied from those runs.

### Branch

kunminghu-v3

### Describe the bug

An Sv39 leaf PTE whose PPN has bits set above XiangShan's physical address width (PTE[46]=1, i.e. the physical address is >= 2^48), **and** which must already fail with a page fault because of its **A or D bit**, makes XiangShan raise an access fault instead. NEMU raises the page fault.

This is closely related to #6670, which reports the same thing for permission failures (U/SUM/MXR and R/W/X). We found it from a different direction (page-table test cases generated from the Sail RISC-V model, replayed on the emu and compared with NEMU) and found that **A=0 (Svade) and D=0 on a store** behave the same way, which #6670 does not list. We open this as a separate issue because the checks involved are different.

All rows use one 1 GiB leaf at level 2 (Sv39), S-privilege translation (`MPRV=1`, `MPP=S`, `SUM=1`, `MXR=0`), PMP allowing everything:

| access | leaf PTE | why the walk must stop (privileged spec, translation algorithm) | NEMU | XiangShan |
|---|---|---|---|---|
| `ld` | `0x000040000000009f` (V R W X U, **A=0**, D=1) | step 9: A=0 (Svade) | 13 load page fault | **5** load access fault |
| `sd` | `0x0000400000000017` (V R W U, **A=0**, D=0) | step 9: A=0 (Svade) | 15 store page fault | **7** store access fault |
| `sd` | `0x0000400000000057` (V R W U, A=1, **D=0**) | step 9: D=0 on a store (Svade) | 15 store page fault | **7** store access fault |

Controls (the same program with only the PTE changed; XiangShan and NEMU agree, the access fault being correct once the walk succeeded):

| access | leaf PTE | both raise |
|---|---|---|
| `ld` | `0x00004000000000df` (A=1, D=1) | 5 load access fault |
| `sd` | `0x00004000000000d7` (A=1, D=1) | 7 store access fault |

So only "PPN too high **and** a page fault from the A/D (or permission) checks" differs. The privileged spec forms the physical address only in step 10, after the A/D checks of step 9, so the page fault should come first.

**What looks relevant in the source** (our reading; we have not changed the RTL): `PteBundle.isAf()` is true for any valid PTE with `ppn_high != 0` and `isPf()` does not cover the leaf A/D checks, so `af=1` is handed up. The A/D and permission page faults are evaluated later in the L1 TLB, where the `pf.ld / pf.st / pf.instr` outputs in `TLB.scala` are gated by `!af`, with the comment "pf has lower priority than af".

That differs from the priority written in #3453 (`pmp_af > {pf or gpf} > ppn_af`). As far as we can tell, the structural page faults (reserved bits, invalid encodings) already win over `ppn_af` in `L2TLB.scala` / `PageTableWalker.scala`; only the checks done in the L1 TLB lose.

**Question: is it intended that the A/D and permission page faults lose to the PPN-overflow access fault, or is this an oversight?** If it is intended, we would appreciate a pointer to the reasoning so that we can filter these cases in our differential testing.

### Expected behavior

Load / store page fault (13 / 15) for the three rows above, as NEMU raises, because the page fault from step 9 (and steps 6-8 for permissions) precedes the formation and checking of the physical address.

### Environment

- Hardware
  - CPU: not recorded
  - Memory (GB): not recorded
- Software
  - Operating system: Ubuntu 26.04.1 LTS
  - gcc version: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0 (emu build); riscv64-unknown-elf-gcc 14.2.0 (reproducer build)
  - java version: openjdk 25.0.2 2026-01-20
  - Verilator: 5.038 2025-07-08
- Repo
  - XiangShan commit id: `a13efc8775abb093e23727772a562f0605380685` (kunminghu-v3, `v3.2.2-alpha-2259-ga13efc877`)
  - NEMU commit id (if difftest failed with NEMU): the `ready-to-run/riscv64-nemu-interpreter-so` shipped in that tree (sha256 starts with `9188e7dd`); the NEMU commit itself is not recorded
- Build & Run
  - Build command: `make emu` (the exact options of this build were not recorded)
  - Run command: `./build/emu -i t.elf --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000`

### To Reproduce

Save the linker script as `t.ld`:

```
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS { . = 0x80000000; .text : { *(.text .text.*) } }
```

Save the program as `t.S` (`PTE`, `ACCESS` and `CAUSE` come from `-D`):

```asm
.section .text
.globl _start
.option norvc
.option norelax
_start:
    la t0, trap
    csrw mtvec, t0
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0            # NAPOT, all memory, RWX
    li t0, 0x80200000           # root page table (satp.PPN = 0x80200)
    li t1, PTE                  # the single 1 GiB leaf, slot 0
    sd t1, 0(t0)
    li t0, 0x8000000000080200
    csrw satp, t0               # Sv39
    sfence.vma x0, x0
    li t0, 0xa00060800          # MPP=S, MPRV=1, SUM=1, MXR=0
    csrs mstatus, t0
    li x1, 0x20000000           # VPN[2]=0 -> slot 0
    ACCESS x2, 0(x1)
    li a0, 1                    # no trap at all: bad
    .4byte 0x0005006b
trap:
    csrr s0, mcause
    li t0, CAUSE                # cause NEMU is expected to raise
    bne s0, t0, bad
    li a0, 0
    .4byte 0x0005006b
bad:
    li a0, 1
    .4byte 0x0005006b
```

Build and run one row (here the first one):

```
riscv64-unknown-elf-gcc -nostdlib -nostartfiles -static -fno-pic -march=rv64gc_zicsr -mabi=lp64d \
    -Wl,-T,t.ld -Wl,-e,_start -DPTE=0x000040000000009f -DACCESS=ld -DCAUSE=13 -o t.elf t.S
./build/emu -i t.elf --diff ready-to-run/riscv64-nemu-interpreter-so -I 3000
```

Observed on the three failing rows:

```
-DPTE=0x000040000000009f -DACCESS=ld -DCAUSE=13
 mcause different at pc = ..., right = 0x000000000000000d, wrong = 0x0000000000000005
-DPTE=0x0000400000000017 -DACCESS=sd -DCAUSE=15
 mcause different at pc = ..., right = 0x000000000000000f, wrong = 0x0000000000000007
-DPTE=0x0000400000000057 -DACCESS=sd -DCAUSE=15
 mcause different at pc = ..., right = 0x000000000000000f, wrong = 0x0000000000000007
```

(`right` is NEMU, `wrong` is the DUT.) The two control rows (`-DPTE=0x00004000000000df -DACCESS=ld -DCAUSE=5` and `-DPTE=0x00004000000000d7 -DACCESS=sd -DCAUSE=7`) run to `HIT GOOD TRAP` without a mismatch.

Note: with `--no-diff` the emu may print `HIT GOOD TRAP` even when the program ended at the `bad` label, so use `--diff` or judge by the exit pc / `mcause`.

### Additional context

- Related: #6670 (same root cause, permission checks), #6669 (leaf PPN overflow used without an access fault after accesses through other PTEs of its block), #3453 (priority of `pmp_af`, `pf`, `ppn_af`).
- We have not checked the non-leaf, G-stage / H-extension, or instruction-fetch variants of these A/D cases.
