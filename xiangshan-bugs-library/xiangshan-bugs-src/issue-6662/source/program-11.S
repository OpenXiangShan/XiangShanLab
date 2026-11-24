/* The program every row of the report runs; a second, per-program file supplies .scenario and .mpt.
 *
 * Constants come from mpt_constants.h. SC_* are the offsets of the per-program scenario words (the
 * inputs M mode reads from .scenario); RC_* are the offsets of the record words the program prints.
 * The per-program file also holds the three static Smmpt43 tables (root, level 1, level 0), so no MPTE
 * is written by a store while the program runs.
 *
 * M-mode setup: mtvec, mie/mideleg/medeleg = 0, mstatus = 0 (MXR = MPRV = SUM = 0),
 * satp = 0; PMP pmpaddr0, pmpaddr1, pmpcfg0 written and read back, then
 * SFENCE.VMA x0, x0; mmpt written and read back; when the program uses MPT, MFENCE (F0_WORD,
 * 0xbe000073) once, with the handler resuming at mepc + 4 and recording mcause and mtval; the
 * fixed-context readbacks; then either the privilege drop to S or U (MPP, mret) or,
 * for an M-mode program, the target access in M-mode.
 *
 * S/U path: the preamble (this code executing from the code range is the fetch; a load
 * from and a store to the data page), each completion marked in s9, then the
 * target access: a load of the target sentinel, a store of the target pattern, or a
 * jump into the target page's marker code; each ends in ECALL.
 *
 * The M-mode handler takes every trap. At the MFENCE stage it records the trap and
 * resumes; otherwise it records the final trap, reads back the data page's slot and (for
 * a store program whose final trap came at the target stage) the target slot, prints
 * one checksummed record line and terminates normally.
 *
 * Registers: s0 scenario base, s1 access, s2 target, s3 run-preamble flag, s4
 * data page base, s5 privilege, s6 trap count, s7 target load value, s8 preamble load
 * value, s9 preamble marks, s10 stage, s11 record base, t6 fetch mark.
 */
#include "mpt_constants.h"

/* The two nops are padding. */
#define MMPT_WRITE(r) csrw MMPT_CSR, r; nop; nop
#define MMPT_READ(r) csrr r, MMPT_CSR; nop; nop

.option norvc
.option norelax

.section .text.init,"ax",@progbits
.globl _start
_start:
    la t0, trap
    csrw mtvec, t0
    csrw mie, zero
    csrw mideleg, zero
    csrw medeleg, zero
    csrw mstatus, zero
    csrw satp, zero
    li s11, RECORD_BASE
    li s10, STAGE_SETUP
    li s6, 0
    li s7, 0
    li s8, 0
    li s9, 0
    li t6, 0
    li s0, SCENARIO_BASE
    li s4, SCRATCH_BASE
    li s1, ACC_NONE
    li s2, 0
    li s3, 0
    li s5, PRIV_M
    ld t0, SC_MAGIC(s0)
    li t1, SCENARIO_MAGIC
    bne t0, t1, emit
    li t0, RECORD_MAGIC
    sd t0, RC_MAGIC(s11)
    ld t0, SC_ORDINAL(s0)
    sd t0, RC_ORDINAL(s11)
    ld t0, SC_ARM(s0)
    sd t0, RC_ARM(s11)
    ld s5, SC_PRIV(s0)
    sd s5, RC_PRIV(s11)
    ld s1, SC_ACCESS(s0)
    sd s1, RC_ACCESS(s11)
    ld s2, SC_TARGET(s0)
    sd s2, RC_TARGET(s11)
    ld s3, SC_RUN_PREAMBLE(s0)

    /* PMP: address registers, then the configuration; read back; SFENCE.VMA x0, x0. */
    ld t0, SC_PMPADDR0(s0)
    sd t0, RC_PMPADDR0_W(s11)
    csrw pmpaddr0, t0
    ld t0, SC_PMPADDR1(s0)
    sd t0, RC_PMPADDR1_W(s11)
    csrw pmpaddr1, t0
    ld t0, SC_PMPCFG0(s0)
    sd t0, RC_PMPCFG0_W(s11)
    csrw pmpcfg0, t0
    csrr t0, pmpcfg0
    sd t0, RC_PMPCFG0_R(s11)
    csrr t0, pmpaddr0
    sd t0, RC_PMPADDR0_R(s11)
    csrr t0, pmpaddr1
    sd t0, RC_PMPADDR1_R(s11)
    sfence.vma zero, zero

    /* mmpt: write, then read back. */
    ld t0, SC_MMPT(s0)
    sd t0, RC_MMPT_W(s11)
    MMPT_WRITE(t0)
    MMPT_READ(t0)
    sd t0, RC_MMPT_R(s11)

    /* MFENCE once, when the program uses MPT, after the mmpt write and before the privilege drop. */
    ld t0, SC_RUN_F0(s0)
    sd t0, RC_F0_EXECUTED(s11)
    beqz t0, 1f
    li s10, STAGE_F0
.globl f0_insn
f0_insn:
    .word F0_WORD
    li s10, STAGE_F0_DONE
1:
    csrr t0, satp
    sd t0, RC_SATP_R(s11)
    csrr t0, medeleg
    sd t0, RC_MEDELEG_R(s11)
    csrr t0, mideleg
    sd t0, RC_MIDELEG_R(s11)
    csrr t0, mie
    sd t0, RC_MIE_R(s11)
    li t0, PRIV_M
    beq s5, t0, m_target
    slli t0, s5, 11
    csrw mstatus, t0
    csrr t0, mstatus
    sd t0, RC_MSTATUS_R(s11)
    la t0, su_entry
    csrw mepc, t0
    li s10, STAGE_DROP
    mret

m_target:
    csrr t0, mstatus
    sd t0, RC_MSTATUS_R(s11)
    li s10, STAGE_TARGET
    j target_access

/* ---- S or U from here until a trap ---- */
.globl su_entry
su_entry:
    li s10, STAGE_PREAMBLE
    beqz s3, 2f
    ori s9, s9, 1
    ld s8, 0(s4)
    ori s9, s9, 2
    li t0, SCRATCH_PATTERN
    sd t0, 8(s4)
    ori s9, s9, 4
2:
    li s10, STAGE_TARGET
.globl target_access
target_access:
    li t0, ACC_LOAD
    beq s1, t0, do_load
    li t0, ACC_STORE
    beq s1, t0, do_store
    li t0, ACC_FETCH
    beq s1, t0, do_fetch
.globl target_none_ecall
target_none_ecall:
    ecall
    j dead
do_load:
.globl target_load_insn
target_load_insn:
    ld s7, TARGET_LOAD_OFFSET(s2)
    ecall
    j dead
do_store:
    li t0, TARGET_PATTERN
.globl target_store_insn
target_store_insn:
    sd t0, TARGET_STORE_OFFSET(s2)
    ecall
    j dead
do_fetch:
.globl target_fetch_jump
target_fetch_jump:
    jalr ra, 0(s2)
dead:
    j dead

/* ---- M-mode trap handler ---- */
.balign 64
.globl trap
trap:
    addi s6, s6, 1
    csrr t0, mcause
    csrr t1, mepc
    csrr t2, mtval
    li t3, STAGE_EMIT
    beq s10, t3, emit_failed
    li t3, STAGE_F0
    beq s10, t3, f0_trap
    li t3, STAGE_READBACK
    beq s10, t3, readback_trap
    sd s10, RC_STAGE(s11)
    sd t0, RC_MCAUSE(s11)
    sd t1, RC_MEPC(s11)
    sd t2, RC_MTVAL(s11)
    sd s9, RC_PREAMBLE_MARKS(s11)
    sd s8, RC_PREAMBLE_LOAD(s11)
    sd s7, RC_TARGET_LOAD(s11)
    sd t6, RC_FETCH_MARK_R(s11)
    ld t3, 8(s4)
    sd t3, RC_SCRATCH_READBACK(s11)
    li t3, STAGE_TARGET
    bne s10, t3, emit
    li t3, ACC_STORE
    bne s1, t3, emit
    li s10, STAGE_READBACK
    ld t3, TARGET_STORE_OFFSET(s2)
    sd t3, RC_TARGET_READBACK(s11)
    j emit

readback_trap:
    li t3, 1
    sd t3, RC_READBACK_TRAPPED(s11)
    sd t0, RC_READBACK_MCAUSE(s11)
    j emit

f0_trap:
    li t3, 1
    sd t3, RC_F0_TRAPPED(s11)
    sd t0, RC_F0_MCAUSE(s11)
    sd t2, RC_F0_MTVAL(s11)
    addi t1, t1, 4
    csrw mepc, t1
    mret

/* One line: "G8R" and RECORD_WORDS sixteen-digit hex words, the last the XOR of the others. */
emit:
    li s10, STAGE_EMIT
    sd s6, RC_TRAP_COUNT(s11)
    li t3, 0
    li t4, 0
    li t5, RECORD_WORDS - 1
3:  slli a1, t4, 3
    add a1, a1, s11
    ld a1, 0(a1)
    xor t3, t3, a1
    addi t4, t4, 1
    bltu t4, t5, 3b
    sd t3, RC_CHECKSUM(s11)
    li a0, 71
    jal ra, putchar
    li a0, 56
    jal ra, putchar
    li a0, 82
    jal ra, putchar
    li t4, 0
4:  li a0, 32
    jal ra, putchar
    slli a1, t4, 3
    add a1, a1, s11
    ld a2, 0(a1)
    li a3, 60
5:  srl a0, a2, a3
    andi a0, a0, 15
    li a4, 10
    bltu a0, a4, 6f
    addi a0, a0, 87
    j 7f
6:  addi a0, a0, 48
7:  jal ra, putchar
    addi a3, a3, -4
    bgez a3, 5b
    addi t4, t4, 1
    li t5, RECORD_WORDS
    bltu t4, t5, 4b
    li a0, 10
    jal ra, putchar
    li a0, 0
    j finish

emit_failed:
    li a0, 1
    j finish

/* XiangShan's UARTLite write-only transmit register; putchar uses a5. */
putchar:
    li a5, UART_TX
    sw a0, 0(a5)
    ret
/* XiangShan's trap instruction: a0 = 0 is HIT GOOD TRAP. */
finish:
    .word 0x0000006b
    j finish

/* ---- the target range's page 0 and page 15: identical marker code, sentinel and slot ---- */
.macro TARGET_PAGE section
.section \section,"ax",@progbits
    li t6, FETCH_MARK
    ecall
12: j 12b
    .org TARGET_LOAD_OFFSET
    .dword TARGET_SENTINEL
    .dword TARGET_SLOT_INITIAL
.endm
TARGET_PAGE .target_lo
TARGET_PAGE .target_hi

.section .scratch,"aw",@progbits
    .dword SCRATCH_SENTINEL
    .dword SCRATCH_SLOT_INITIAL

.section .record,"aw",@progbits
    .zero 0x400
mmpt_emulated:
    .dword 0
