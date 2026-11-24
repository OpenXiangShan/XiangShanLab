.option norvc
.option norelax
.section .text.entry,"ax",@progbits
.globl _start
_start:
    j main
.section .text.main,"ax",@progbits
.globl main
main:
    la t0, m_trap
    csrw mtvec, t0
    csrw medeleg, zero
    csrw mideleg, zero
    csrw mie, zero
    csrsi 0x744, 8
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    li t0, -1
    srli t0, t0, 10
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0
    la s3, main_end
    li a0, 87
    jal ra, putchar
    li a0, 50
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
    li a0, 0x4c4f574d54320000
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, main_end
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li s6, 0x5e5e5e5e5e5e5e5e
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 83
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, cell_0_back
    csrr s4, time
    li t0, 0x3800bff8
    li t1, 0x123456700000000
    sd t1, 0(t0)
    fence iorw, iorw
    li t3, 20000
mt2_tmr_wait:
    addi t3, t3, -1
    bnez t3, mt2_tmr_wait
    csrr s5, time
    ld s6, 0(t0)
cell_0_back:
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 86
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 76
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s6
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, main_end
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li s6, 0x5e5e5e5e5e5e5e5e
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 83
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 1
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, cell_1_back
    csrr s4, time
    li t0, 0x3804bff8
    li t1, 0x123456700000000
    sd t1, 0(t0)
    fence iorw, iorw
    li t3, 20000
mt2_sys_wait:
    addi t3, t3, -1
    bnez t3, mt2_sys_wait
    csrr s5, time
    ld s6, 0(t0)
cell_1_back:
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 86
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 76
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s6
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, main_end
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li s6, 0x5e5e5e5e5e5e5e5e
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 83
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 2
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    la s3, cell_2_back
    csrr s4, time
    li t0, 0x3804bff8
    li t1, 0x123456700000000
    sd t1, 0(t0)
    li t2, 0x3804c010
    li t1, 1
    sd t1, 0(t2)
    fence iorw, iorw
    li t3, 20000
mt2_sw_wait:
    addi t3, t3, -1
    bnez t3, mt2_sw_wait
    csrr s5, time
    ld s6, 0(t0)
cell_2_back:
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 86
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 76
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s6
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
main_end:
    li a0, 87
    jal ra, putchar
    li a0, 50
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
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    li a0, 0
    jal ra, finish
halt:
    j halt

.balign 4

.balign 64
m_trap:
    csrr s10, mcause
    csrr s11, mepc
    csrr s9, mtval
    csrr s8, mtval2
    csrr s7, mtinst
    li a0, 87
    jal ra, putchar
    li a0, 50
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 84
    jal ra, putchar
    li a0, 82
    jal ra, putchar
    li a0, 65
    jal ra, putchar
    li a0, 80
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s10
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s11
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s9
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s8
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s7
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    li t0, 0x1800
    csrs mstatus, t0
    li t0, 1
    slli t0, t0, 39
    csrc mstatus, t0
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0
    csrw mepc, s3
    mret

puthex64:
    mv s1, ra
    mv t4, a0
    li t5, 60
1:  srl a0, t4, t5
    andi a0, a0, 15
    li t6, 10
    bltu a0, t6, 2f
    addi a0, a0, 87
    j 3f
2:  addi a0, a0, 48
3:  jal ra, putchar
    addi t5, t5, -4
    bgez t5, 1b
    mv ra, s1
    ret

putchar:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret
finish:
    .word 0x0000006b
    j finish

.section .data,"aw",@progbits
.balign 64
scratch:
    .dword 0
