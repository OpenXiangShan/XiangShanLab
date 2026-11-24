### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

After building an Sv39 page table in M-mode, enabling paging (`satp` Bare -> Sv39) and entering S-mode **without executing any `sfence.vma`**, the first S-mode access to a 4 KiB leaf mapping raises a **spurious load page fault** (scause=13, stval = the accessed virtual address), although the leaf PTE in memory is valid and was written **before** `satp`. Scalar `ld`, `vle64.v` and `vse64.v` are equally affected; the PoC below uses a scalar `ld` for minimality.

The behavior is deterministic (3/3 reruns) and has a sharp trigger threshold observed on this commit: the spurious fault depends on how many leaf entries are installed and where: with only `{0, 7, 8}` or `{0, 1, 6, 7, 8}` installed the identical access succeeds, while `{0, 1, 6, 7, 8, 9}` and the 8-entry set above fault - i.e. once more than one entry sits in the second 64-byte line of the leaf table, first accesses to entries in that region fault. Entries at L0 index <= 7 (the first 64 bytes of the leaf table) always work; entries at index >= 8 fail. A 2 MB megapage mapping keeps working in the same run. One `sfence.vma` anywhere before the access makes all entries work.

The PTE contents in memory were verified in-program (the dump loads in the PoC print `0x200800c7` and `0x200820c7` in the commit trace — both valid RW+A+D leaves, identical permissions), and the access to the L0 index-0 entry succeeds *in the same run* right before the index-8 access faults — so this is not a permissions issue and not a table-construction issue.


### Expected behavior

Per the Privileged Specification, *sfence.vma* section (normative statement on mode changes): "Changing `satp`.MODE from Bare to other modes and vice versa also takes effect immediately, without the need to execute an SFENCE.VMA instruction." Since the page tables are fully written to memory before `satp` is written (verified by the PTE dumps), the first translated access must walk the new tables and succeed. An `SFENCE.VMA` is only required when software modifies the tables of an **already-active** address space, which is not the case here.

### Environment

- Hardware
  - CPU: AMD EPYC 7543 32-Core Processor (128 hardware threads)
  - Memory (GB): 247
  - Storage (GB): 3727
- Software
  - Operating system: Ubuntu 26.04.1 LTS
  - gcc version: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0
  - clang version: not used
  - java version: openjdk 25.0.2 (2026-01-20)
  - mill version: repository wrapper (`./mill`)
- Repo
  - XiangShan commit id: `f86f50b834c26597426b5ede7c25afaff84d11ce` (branch `kunminghu-v2`)
  - NEMU commit id (if difftest failed with NEMU): ready-to-run submodule @ `5a63554d` (riscv64-nemu-interpreter-so)
  - SPIKE commit id (if difftest failed with SPIKE): not used
- Build & Run
  - Build command: `NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 -j112`
  - Run command: `./build/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so --dump-commit-trace -C 100000`


### To Reproduce

1. Build the emulator:

```bash
git clone https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan
git checkout kunminghu-v2
make init
NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 -j$(nproc)
```

2. Save the PoC as `poc.S` (self-contained: builds an Sv39 identity map with a 2 MiB megapage for code/data and an L0 leaf table covering VA 0x80200000-0x803FFFFF; installs 8 leaf entries; dumps two PTEs; then in S-mode accesses L0 index 0, then L0 index 8; a trap handler probes scause/stval and takes one of four distinct exit markers so every outcome is identifiable by the exit pc):

```asm
.option norvc
.section .text.init
.globl _start
_start:
    # ---------------- M-mode: CSR 基础 + 建 Sv39 页表(先表后 satp) ----------------
    la   t0, m_trap               # M-mode 兜底 handler(停车, 不应到达)
    csrw mtvec, t0
    li   t0, 0xb004               # medeleg: illegal|ipf|lpf|spf 委派 S
    csrw medeleg, t0
    li   t0, 0x1f                 # PMP0: NAPOT RWX 全空间(L=0)
    csrw pmpcfg0, t0
    li   t0, -1
    csrw pmpaddr0, t0
    la   t0, pt_area              # 清零 root + L1 + L0 (3*512 表项)
    li   t1, 0x600
1:  sd   x0, 0(t0)
    addi t0, t0, 8
    addi t1, t1, -1
    bnez t1, 1b
    la   t0, pt_area
    li   t5, 4096
    add  t1, t0, t5               # L1 基址
    srli t1, t1, 12
    slli t1, t1, 10
    ori  t1, t1, 1
    li   t5, 16
    add  t2, t0, t5
    sd   t1, 0(t2)                # root[2] -> L1
    li   t2, 0x200000cf           # L1[0] = 2MB 大页 @0x80000000, V|R|W|X|A|D
    li   t5, 4096
    add  t3, t0, t5
    sd   t2, 0(t3)
    li   t5, 8192
    add  t1, t0, t5               # L0 基址
    srli t1, t1, 12
    slli t1, t1, 10
    ori  t1, t1, 1
    li   t5, 4104
    add  t3, t0, t5
    sd   t1, 0(t3)                # L1[1] -> L0 (L0 覆盖 VA 0x80200000-0x803FFFFF)
    # L0 叶子: idx0/idx7/idx8 同属性 V|R|W|A|D, VA=PA 恒等
    li   t3, 0x200800c7           # L0[0]: 0x80200000
    li   t5, 0x2000
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x20081cc7           # L0[7]: 0x80207000
    li   t5, 0x2038
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200804c7           # L0[1]: 0x80201000 RW (dummy)
    li   t5, 0x2008
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200818c7           # L0[6]: 0x80206000 RW (dummy)
    li   t5, 0x2030
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200824c7           # L0[9]: 0x80209000 RW (dummy)
    li   t5, 0x2048
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200828c7           # L0[10]: 0x8020a000 RW (dummy)
    li   t5, 0x2050
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x20082cc7           # L0[11]: 0x8020b000 RW (dummy)
    li   t5, 0x2058
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200820c7           # L0[8]: 0x80208000
    li   t5, 0x2040
    add  t4, t0, t5
    sd   t3, 0(t4)
    # ---------------- 开分页, mret 进 S-mode (全程无 sfence.vma) ----------------
    # 注: L0 页共装入 8 条非零表项(0,1,6,7,8,9,10,11); 少于 7 条时 bug 不触发
    la   t1, pt_area
    srli t1, t1, 12
    li   t0, 8
    slli t0, t0, 60
    or   t0, t0, t1
    csrw satp, t0                 # satp: Bare -> Sv39, 页表已全部写入内存
    li   t1, 0x1800               # mstatus.MPP = 01 (S-mode)
    csrrc t0, mstatus, t1
    li   t1, 0x800
    csrs  mstatus, t1
    la   t1, s_entry
    csrw mepc, t1
    mret

.section .text
s_entry:
    la   t0, s_trap
    csrw stvec, t0
    # PTE dump: 下列 ld 的表项值直接出现在 commit trace 的 data 字段
    la   t1, pt_area
    li   t5, 0x2000
    add  t1, t1, t5
    ld   t2, 0(t1)                # PTE[0] -> data 0x00000000200800c7
    li   t5, 0x40
    add  t1, t1, t5
    ld   t2, 0(t1)                # PTE[8] -> data 0x00000000200820c7
    li   t1, 0x80200000
    ld   t2, 0(t1)                # [CONTROL] first access to L0 idx0, the first leaf of
                                  # the same table: retires normally (no trap)

    li   t1, 0x80208000
    ld   t2, 0(t1)                # <<<<< THE BUG-TRIGGERING INSTRUCTION >>>>>
                                  # - First access to L0 idx8 (VA 0x80208000), a fully valid
                                  #   RW leaf mapping (the PTE dump above proves it is in memory)
                                  # - Wrongly raises a load page fault: scause = 13,
                                  #   stval = 0x80208000, sepc = this instruction
                                  # - The trap lands in s_trap; after the probe checks it
                                  #   takes exit 1 (the bug-reproduced marker)
                                  # - Substitution experiments (each a single edit, each verified):
                                  #   (1) FIX BY SFENCE: insert one line `sfence.vma` right after
                                  #       `csrw stvec, t0` in s_entry -> this load succeeds (exit 2)
                                  #   (2) BOUNDARY CONTROL: change `li t1, 0x80208000` above to
                                  #       `li t1, 0x80207000` (L0 idx7, also installed), AND change
                                  #       the expected stval `li t4, 0x80208000` in s_trap to
                                  #       `li t4, 0x80207000` -> succeeds (exit 2)
                                  #   (3) ENTRY-COUNT THRESHOLD: delete the three dummy install
                                  #       quads L0[9]/L0[10]/L0[11] (the `li t3, 0x200824c7`,
                                  #       `0x200828c7`, `0x20082cc7` groups in _start) -> succeeds
                                  #       (exit 2)
    j    2f                       # 出口2: 全部成功(bug 不在 / 已 sfence / 访问 idx<=7)

s_trap:
    csrr t0, scause               # 探针: 值见 commit trace 的 data 字段
    csrr t1, stval
    csrr t2, sepc
    li   t4, 13
    bne  t0, t4, 3f               # 出口3: 有 trap 但 scause != 13
    li   t4, 0x80208000
    bne  t1, t4, 4f               # 出口4: scause=13 但 stval != idx8 VA
    j    1f                       # 出口1: 假 lpf13 复现(scause=13, stval=idx8 VA)

1:  .4byte 0x0000006b             # 出口1: idx8 假 page fault(bug 在)
2:  .4byte 0x0000006b             # 出口2: 无 trap, 全部成功
3:  .4byte 0x0000006b             # 出口3: trap 但非 lpf
4:  .4byte 0x0000006b             # 出口4: lpf 但 stval 不符

m_trap:
    csrr t0, mcause
    csrr t1, mtval
    csrr t2, mepc
5:  j 5b

.section .pagetables
.balign 4096
pt_area:
    .zero 12288
```

3. Build and run (single copy-paste; includes the linker script):

```bash
cat > link.ld <<'LD'
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS {
  . = 0x80000000;
  .text.init : { KEEP(*(.text.init)) }
  . = ALIGN(0x1000); .text : { *(.text .text.*) }
  . = ALIGN(0x1000); .data : { *(.data .data.* .sdata .sdata.* .rodata .rodata.*) }
  . = ALIGN(0x1000);
  .bss (NOLOAD) : { *(.bss .bss.* COMMON) }
  . = ALIGN(0x1000);
  .pagetables : { KEEP(*(.pagetables)) }
  /DISCARD/ : { *(.comment) *(.note*) *(.riscv.attributes) }
}
LD
riscv64-unknown-elf-gcc -nostdlib -nostartfiles -static -fno-pic \
  -march=rv64gcv -mabi=lp64d -Wl,-T,link.ld -Wl,-e,_start -o poc.elf poc.S
./build/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so \
  --dump-commit-trace -C 100000
```

4. Observed: `HIT GOOD TRAP at pc = 0x80001074` — exit 1, the spurious-fault path. The commit trace shows, in order: the PTE dump loads returning `0x00000000200800c7` and `0x00000000200820c7` (both leaf entries valid), the access to `0x80200000` (L0 index 0) succeeding, the access to `0x80208000` (L0 index 8) raising a trap, and the two probe instructions committing `scause = 0xd` (13, load page fault) and `stval = 0x80208000`.

5. Substitutions (each verified):

- Add `sfence.vma` as the first instruction after `csrw stvec, t0` in `s_entry` -> exits at `0x8000107c` (exit 2): **all accesses succeed; one sfence fixes everything**.
- Change the test address `0x80208000` (both the `li t1` in `s_mode` payload and the `li t4` in `s_trap`) to `0x80207000` (L0 index 7, also installed) -> exit 2: entries within the first 64 bytes of the leaf table work.
- Delete the three dummy installs `L0[9]`, `L0[10]`, `L0[11]` (leaving 5 installed entries) -> exit 2: below the trigger threshold the same access succeeds.


### Additional context

- Waveform evidence (Verilator fst dump around the fault, `--dump-wave -b 6300 -e 6700`): the PTW never fetches the L0 sector of the faulting access (`0x80004040`, entries 8-11) at all. Instead, an **L2TLB prefetch fetch of the *next* 16-entry leaf sector** (`0x80004080`, entries 16-19, never installed -> reads back all zeros) is issued right after the earlier walk, and the faulting access is then answered with a page fault without any walk of its own sector. Source pointers: the prefetch is generated by `L2TLB.scala` (first L2TLB miss triggers `L2TlbPrefetch`) with target `get_next_line(vpn) = dropL0SectorBits(vpn) + 1` (`MMUConst.scala`); the sector response is consumed via the `pteidx` extraction in `L2TLB.scala` and refilled into `PageTableCache.scala`. The exact tag/way mismatch that lets the prefetched zero sector answer a different vpn is the last unverified link (the PtwCache SRAM internals are blackboxed in the wave).
- The failing region (L0 index >= 8) starts exactly at byte offset 64 of the leaf table page (8 PTEs per 64 B line), which may be a hint for localization; no mechanism claim is made.
- Vector accesses are equally affected (`vle64.v` / `vse64.v` verified with the same page layout); the PoC is kept scalar for minimality.
- The trigger is a **race between the victim access's own walk and the prefetch response**, so it is sensitive to the surrounding instruction stream: a C-compiled boot writing identical PTE values to identical addresses does not trigger — its waveform shows the victim's own L0 sector fetched **concurrently** with the prefetch (both walks issued 4 cycles apart) and returning the correct PTE, so the zero-filled prefetch only pollutes an unaccessed sector. The PoC above must be used **verbatim**.
- Difftest does **not** flag this divergence: for page faults the difftest framework guides the reference model with the DUT's exception, so the exit pc and the commit-trace probes above are the primary evidence.
- Source pointers on this branch: the permission check at `TLB.scala` (`modeCheck`, uses the live `csr.priv.sum`) and the CSR binding at `NewCSR.scala` (`io.tlb.sum := mstatus.regOut.SUM`) look correct; `PageTableWalker.scala` / `L2TLB.scala` contain no independent permission checks. The defect appears to live in the walk/fill path; `PageTableCache.scala` / `L2TlbPrefetch.scala` may be relevant.
- Impact: any boot flow that builds page tables, enables paging and starts accessing memory without an early `sfence.vma` (early firmware / bare-metal bring-up / kernel entry on secondary tables) faults on every mapping beyond the first 64 bytes of a leaf table page.
