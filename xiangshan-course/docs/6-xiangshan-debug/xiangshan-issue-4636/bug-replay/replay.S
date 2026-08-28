/*
 * Reproduce the pre-PR #4636 behavior in which PREFETCH.R to an Svpbmt.NC
 * page is converted into a real uncached read by the LoadUnit/LoadQueue path.
 *
 * The S-mode address space contains only two mappings:
 *   VA 0x0000 -> s_mode_payload (normal cacheable executable page)
 *   VA 0x1000 -> nc_data_page   (PBMT.NC read/write data page)
 *
 * PMP grants S-mode access; Svpbmt, not PMP, supplies the NC attribute.
 */

  .option norvc

  .equ PTE_V,       0x001
  .equ PTE_R,       0x002
  .equ PTE_W,       0x004
  .equ PTE_X,       0x008
  .equ PTE_A,       0x040
  .equ PTE_D,       0x080
  .equ PTE_TABLE,   PTE_V
  .equ PTE_CODE,    PTE_V | PTE_R | PTE_X | PTE_A
  .equ PTE_DATA,    PTE_V | PTE_R | PTE_W | PTE_A | PTE_D
  .equ PMP_NAPOT_RWX, 0x1f
  .equ MSTATUS_MPP, 0x1800
  .equ MSTATUS_MPP_S, 0x0800
  .equ MENVCFG,     0x30a

  .section .text
  .align 2
  .globl run_prefetch_replay
  .type run_prefetch_replay, @function
run_prefetch_replay:
  /* Any unexpected S-mode exception is reported as a failing AM trap. */
  la t0, machine_trap_fail
  csrw mtvec, t0

  /* One all-address NAPOT PMP entry grants S-mode R/W/X access. */
  li t0, -1
  csrw pmpaddr0, t0
  li t0, PMP_NAPOT_RWX
  csrw pmpcfg0, t0
  sfence.vma x0, x0

  /* Make the dependency-producing warm load deterministic. */
  la t0, nc_data_page
  sd zero, 0x40(t0)
  fence rw, rw

  /* root[0] -> level-1[0] -> level-0. */
  la t0, replay_pt_l1
  srli t0, t0, 2
  ori t0, t0, PTE_TABLE
  la t1, replay_pt_root
  sd t0, 0(t1)

  la t0, replay_pt_l0
  srli t0, t0, 2
  ori t0, t0, PTE_TABLE
  la t1, replay_pt_l1
  sd t0, 0(t1)

  /* l0[0]: executable S-mode payload at VA 0x0000, normal PBMT. */
  la t0, s_mode_payload
  srli t0, t0, 2
  ori t0, t0, PTE_CODE
  la t1, replay_pt_l0
  sd t0, 0(t1)

  /* l0[1]: data at VA 0x1000, PBMT.NC = PTE bits[62:61] == 01. */
  la t0, nc_data_page
  srli t0, t0, 2
  ori t0, t0, PTE_DATA
  li t2, 1
  slli t2, t2, 61
  or t0, t0, t2
  sd t0, 8(t1)
  fence rw, rw

  /* Enable Svpbmt. Non-zero PBMT would otherwise be a page fault. */
  li t0, 1
  slli t0, t0, 62
  csrs MENVCFG, t0
  csrr t1, MENVCFG
  and t1, t1, t0
  beqz t1, machine_trap_fail

  /* satp.MODE=Sv39, ASID=0, PPN=replay_pt_root>>12. */
  la t0, replay_pt_root
  srli t0, t0, 12
  li t1, 8
  slli t1, t1, 60
  or t0, t0, t1
  csrw satp, t0
  sfence.vma x0, x0

  /* Enter S-mode at VA 0, the start of s_mode_payload. */
  csrr t0, mstatus
  li t1, MSTATUS_MPP
  not t1, t1
  and t0, t0, t1
  li t1, MSTATUS_MPP_S
  or t0, t0, t1
  csrw mstatus, t0
  csrw mepc, zero
  mret

  .size run_prefetch_replay, .-run_prefetch_replay

  .align 2
machine_trap_fail:
  la t0, replay_mcause
  csrr t1, mcause
  sd t1, 0(t0)
  li a0, 1
  .word 0x0005006b
1:
  j 1b

  /* Keep the complete S-mode payload in one 4 KiB executable page. */
  .balign 4096
  .globl s_mode_payload
s_mode_payload:
  /* Warm the DTLB with an ordinary NC load at PA nc_data_page+0x40. */
  li a1, 0x1000
  .globl warm_nc_load
warm_nc_load:
  ld t3, 0x40(a1)
  bnez t3, s_mode_fail

  /*
   * t3 is zero but carries a true dependency on the warm NC response.
   * The raw instruction is PREFETCH.R 32(a2), so the measured VA is 0x1180.
   */
  li a2, 0x1160
  add a2, a2, t3
  .globl measured_prefetch_r
measured_prefetch_r:
  .word 0x02166013

  /* Give the NC request/response path ample time before the success trap. */
  li t0, 64
2:
  addi t0, t0, -1
  bnez t0, 2b

  li a0, 0
  .word 0x0005006b

s_mode_fail:
  li a0, 2
  .word 0x0005006b
3:
  j 3b

  .balign 4096

  .section .data
  .balign 4096
  .globl replay_pt_root
replay_pt_root:
  .zero 4096

  .balign 4096
  .globl replay_pt_l1
replay_pt_l1:
  .zero 4096

  .balign 4096
  .globl replay_pt_l0
replay_pt_l0:
  .zero 4096

  .balign 4096
  .globl nc_data_page
nc_data_page:
  .zero 4096

  .balign 8
  .globl replay_mcause
replay_mcause:
  .dword 0

  .section .note.GNU-stack,"",@progbits
