// Minimal reproducer for OpenXiangShan/XiangShan#6265:
//   ld x1, 1(x0)  -> effective address 0x1, an 8-byte (misaligned) load into the
//   low-address non-cacheable / MMIO PMA region.
// Expectation on the pinned DUT/REF:
//   XiangShan (DUT):  mcause = 5 (load access fault, via afUnalignMMIO)
//   NEMU (REF):       mcause = 4 (load address misaligned)
//   -> Difftest aborts: "mcause different ... right = 4, wrong = 5"
    .section .text
    .global _start

_start:
    // Force machine mode (MPP = M) so the trap handler's mret stays in M.
    csrr   t0, mstatus
    li     t1, 3
    slli   t1, t1, 11          // MPP field (mstatus[12:11])
    or     t0, t0, t1
    csrw   mstatus, t0

    // Install a trap handler that skips the faulting instruction.
    la     t0, trap_entry
    csrw   mtvec, t0

    // Grant read/write/execute over all of memory so the only fault on the
    // misaligned MMIO load is the misalignment (not a PMP permission fault).
    li     t0, -1
    csrw   pmpaddr0, t0
    li     t0, 0x1f            // entry0 R|W|X, A=NAPOT match-all
    csrw   pmpcfg0, t0
    csrw   pmpcfg2, zero

    // ---------------- faulting instruction ----------------
    // 8-byte load from effective address x0 + 1 = 0x1. Misaligned and in the
    // non-cacheable/MMIO PMA region. Deterministic filler follows the trap so
    // Difftest has a clean resync point (mirrors the original seed).
    ld     x1, 1(x0)           // <- reproduces DUT cause=5 vs REF cause=4

    addi   a0, zero, 1
    addi   a1, zero, 2
    addi   a2, zero, 3
    addi   a3, zero, 4
    addi   a4, zero, 5
1:  j      1b                  // spin (Difftest should abort before here)

    .align 4
trap_entry:
    csrr   t0, mepc            // skip the 4-byte faulting instruction
    addi   t0, t0, 4
    csrw   mepc, t0
    mret

    .section .tohost,"aw",@progbits
    .align 3
tohost:
    .dword 0
