.option norvc
.option norelax
.section .text.entry,"ax",@progbits
.globl _start
_start:
    j main
.section .text.main,"ax",@progbits
.globl main
main:
    la t0, trap_entry
    csrw mtvec, t0
    csrw medeleg, zero
    csrw mideleg, zero
    csrw mie, zero
    li t0, 8
    csrc mstatus, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    li t0, -1
    srli t0, t0, 10
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    csrsi 0x744, 8
    la gp, vars
    li a0, 80
    jal ra, putchar
    li a0, 52
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 66
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 73
    jal ra, putchar
    li a0, 78
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0x5034464f4c390001
    li a1, 16
    jal ra, puthex
    li a0, 10
    jal ra, putchar
    la s3, c0_end
    jal ra, reset_vars
    li t0, 0x1800
    csrc mstatus, t0
    li t0, 0x4007001c
    li t1, 0x8
    sw t1, 0(t0)
    li a2, 600
c0_spin:
    addi a2, a2, -1
    bnez a2, c0_spin
c0_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    li a4, 0
    jal ra, print_N
    li s4, 1
    li s5, 49
    li s6, 1
cell_loop:
    la s3, cell_end
    jal ra, reset_vars
    li t0, 0x1800
    csrc mstatus, t0
    li t2, 0x2000
    or t1, s4, t2
    li t0, 0x4007001c
    sw t1, 0(t0)
    li a2, 600
cell_spin:
    addi a2, a2, -1
    bnez a2, cell_spin
cell_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    mv a4, s6
    jal ra, print_N
    addi s4, s4, 1
    addi s6, s6, 1
    blt s4, s5, cell_loop
    la s3, ch_end
    jal ra, reset_vars
    li t0, 0x1800
    csrc mstatus, t0
    li t0, 0x4007001c
    li t1, 0x2000
    sw t1, 0(t0)
    li a2, 600
ch_spin:
    addi a2, a2, -1
    bnez a2, ch_spin
ch_end:
    la t0, m_save
    csrw mscratch, t0
    la t0, n_save
    csrw 0x740, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    jal ra, quiesce
    li a4, 49
    jal ra, print_N
    li a0, 80
    jal ra, putchar
    li a0, 52
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 78
    jal ra, putchar
    li a0, 68
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0
    li a1, 2
    jal ra, puthex
    li a0, 10
    jal ra, putchar
    li a0, 0
    jal ra, finish
halt:
    j halt
print_N:
    mv s2, ra
    li a0, 78
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, a4
    li a1, 2
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 0(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 32(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 88(gp)
    li t3, 15
    bleu a0, t3, 1f
    li a0, 15
1:
    li a1, 1
    jal ra, puthex
    ld t3, 0(gp)
    beqz t3, print_N_no_m
    li a0, 32
    jal ra, putchar
    ld a0, 8(gp)
    li a1, 16
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 16(gp)
    li a1, 4
    jal ra, puthex
print_N_no_m:
    ld t3, 32(gp)
    beqz t3, print_N_no_n
    li a0, 32
    jal ra, putchar
    ld a0, 40(gp)
    li a1, 16
    jal ra, puthex
    li a0, 32
    jal ra, putchar
    ld a0, 56(gp)
    li a1, 4
    jal ra, puthex
print_N_no_n:
    ld t3, 88(gp)
    beqz t3, print_N_no_u
    li a0, 32
    jal ra, putchar
    ld a0, 96(gp)
    li a1, 16
    jal ra, puthex
print_N_no_u:
    li a0, 10
    jal ra, putchar
    mv ra, s2
    ret
ecall_stream:
    li a3, 8
ecall_loop:
.globl ecall_site
ecall_site:
    ecall
    addi a3, a3, -1
    bnez a3, ecall_loop
    ret
reset_vars:
    sd zero, 0(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 8(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 16(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 24(gp)
    sd zero, 32(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 40(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 48(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 56(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 64(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 72(gp)
    sd zero, 80(gp)
    sd zero, 88(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 96(gp)
    li t1, 0x5e5e5e5e5e5e5e5e
    sd t1, 104(gp)
    ret
quiesce:
    li t0, 0x4007001c
    sw zero, 0(t0)
    fence iorw, iorw
    lw t1, 0(t0)
    li t1, 64
quiesce_spin:
    addi t1, t1, -1
    bnez t1, quiesce_spin
    ret
puthex:
    mv s1, ra
    mv t4, a0
    slli t5, a1, 2
    addi t5, t5, -4
puthex_loop:
    srl a0, t4, t5
    andi a0, a0, 15
    li t3, 10
    bltu a0, t3, puthex_digit
    addi a0, a0, 87
    j puthex_put
puthex_digit:
    addi a0, a0, 48
puthex_put:
    jal ra, putchar
    addi t5, t5, -4
    bgez t5, puthex_loop
    mv ra, s1
    ret
putchar:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret
finish:
    .word 0x0000006b
    j finish
.balign 64
.globl trap_entry
trap_entry:
    csrr t6, 0x744
    andi t6, t6, 8
    beqz t6, rnmi_path
    j m_path
.globl m_path
m_path:
    csrrw sp, mscratch, sp
    sd t0, 0(sp)
    sd t1, 8(sp)
    sd t2, 16(sp)
    la t1, vars
    csrr t0, mcause
    bltz t0, m_interrupt
    li t2, 11
    bne t0, t2, m_unexpected
    ld t2, 80(t1)
    addi t2, t2, 1
    sd t2, 80(t1)
    j m_skip
m_unexpected:
    ld t2, 88(t1)
    bnez t2, m_unexpected_counted
    sd t0, 96(t1)
    csrr t0, mepc
    sd t0, 104(t1)
m_unexpected_counted:
    ld t2, 88(t1)
    addi t2, t2, 1
    sd t2, 88(t1)
m_skip:
    csrr t0, mepc
    addi t0, t0, 4
    csrw mepc, t0
    j m_return
m_interrupt:
    ld t2, 0(t1)
    bnez t2, m_interrupt_counted
    sd t0, 8(t1)
    csrr t0, mstatus
    sd t0, 16(t1)
    csrr t0, mepc
    sd t0, 24(t1)
m_interrupt_counted:
    ld t2, 0(t1)
    addi t2, t2, 1
    sd t2, 0(t1)
m_return:
    ld t0, 0(sp)
    ld t1, 8(sp)
    ld t2, 16(sp)
    csrrw sp, mscratch, sp
    mret
.globl rnmi_path
rnmi_path:
    csrrw sp, 0x740, sp
    la t1, vars
    ld t2, 32(t1)
    bnez t2, rnmi_counted
    csrr t0, 0x742
    sd t0, 40(t1)
    csrr t0, 0x741
    sd t0, 48(t1)
    csrr t0, 0x744
    sd t0, 56(t1)
    csrr t0, mstatus
    sd t0, 64(t1)
    csrr t0, mcause
    sd t0, 72(t1)
rnmi_counted:
    ld t2, 32(t1)
    addi t2, t2, 1
    sd t2, 32(t1)
    li t0, 0x4007001c
    sw zero, 0(t0)
    fence iorw, iorw
    lw t2, 0(t0)
    li t2, 64
rnmi_spin:
    addi t2, t2, -1
    bnez t2, rnmi_spin
    csrrw sp, 0x740, sp
    csrsi 0x744, 8
    jr s3
.section .data,"aw",@progbits
.balign 64
m_save:
.space 64
n_save:
.space 64
.globl vars
vars:
.space 112
.balign 64
nmi_source:
.dword 0
scratch:
.dword 0
