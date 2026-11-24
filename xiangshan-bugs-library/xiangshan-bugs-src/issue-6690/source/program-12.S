/* Store-queue forwarding with a virtual-address synonym: a minimal reproducer.
 *
 * VA1 = 0x100000000 and VA2 = 0x100002000 map to the same physical page P = 0x80400000;
 * VA3 = 0x100004000 maps to another page, P3 = 0x80401000. Every access is an aligned
 * doubleword at offset 0x40. Each step runs in S mode with Sv39 and returns with ECALL. M mode
 * then reads P + 0x40 through its physical address and prints
 *
 *     step <n> <first load> <second load> <P + 0x40>
 *
 * Before each step M mode writes 0x2020... to P + 0x40 and 0x3030... to P3 + 0x40, and sets s4 and s5
 * to 0x5e5e5e5e5e5e5e5e, so a load that did not execute is visible.
 */
    .option norvc
    .text
    .globl _start
_start:
    la t0, trap
    csrw mtvec, t0
    csrw medeleg, zero            # every trap goes to M mode
    csrw mideleg, zero
    csrw mie, zero
    csrci mstatus, 8              # mstatus.MIE = 0
    csrsi 0x744, 8                # mnstatus.NMIE = 1
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0              # mstatus.MDT = 0
    li t0, -1
    srli t0, t0, 10
    csrw pmpaddr0, t0             # PMP entry 0: all of memory, RWX, NAPOT
    li t0, 0x1f
    csrw pmpcfg0, t0
    li t0, 0x8000000000080200     # Sv39, root page table at 0x80200000
    csrw satp, t0
    sfence.vma

    # step 0: synonym
    li s0, 0
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100002000            # second store: VA2
    li s8, 0x100002000            # second load: VA2
    la t0, step0
    jal ra, run_step
    jal ra, report

    # step 1: synonym after a division chain
    li s0, 1
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100002000            # second store: VA2
    li s8, 0x100002000            # second load: VA2
    la t0, step1
    jal ra, run_step
    jal ra, report

    # step 2: second load through VA2
    li s0, 2
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100000000            # second store: VA1
    li s8, 0x100002000            # second load: VA2
    la t0, step2
    jal ra, run_step
    jal ra, report

    # step 3: one virtual address
    li s0, 3
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100000000            # second store: VA1
    li s8, 0x100000000            # second load: VA1
    la t0, step3
    jal ra, run_step
    jal ra, report

    # step 4: another physical page
    li s0, 4
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100004000            # second store: VA3
    li s8, 0x100004000            # second load: VA3
    la t0, step4
    jal ra, run_step
    jal ra, report

    la a0, text_done
    jal ra, puts
    li a0, 0
    .word 0x0000006b              # the emulator's trap instruction: a0 = 0 is a good trap

# Prepare step s0 and enter it in S mode at t0. The step's ECALL comes back to ra, in M mode.
run_step:
    mv s3, ra
    slli t2, s0, 8                # the step number, in every value below
    li t1, 0x2020000000000000
    or t1, t1, t2
    li t3, 0x80400040
    sd t1, 0(t3)                  # P + 0x40 before the step
    li t1, 0x3030000000000000
    or t1, t1, t2
    li t3, 0x80401040
    sd t1, 0(t3)                  # P3 + 0x40 before the step
    fence rw, rw
    sfence.vma
    li a1, 0xa0a0000000000000
    or a1, a1, t2                 # A, stored first
    li a2, 0xb0b0000000000000
    or a2, a2, t2                 # B, stored second
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li t1, 0x1880
    csrc mstatus, t1              # MPP = U, MPIE = 0
    li t1, 0x800
    csrs mstatus, t1              # MPP = S
    csrw mepc, t0
    mret

step0:                            # S mode: sd A via VA1, sd B via VA2, ld via VA1, ld via VA2
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step1:                            # S mode: sd A via VA1, sd B via VA2, ld via VA1, ld via VA2, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step2:                            # S mode: sd A via VA1, sd B via VA1, ld via VA1, ld via VA2, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step3:                            # S mode: sd A via VA1, sd B via VA1, ld via VA1, ld via VA1, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step4:                            # S mode: sd A via VA1, sd B via VA3, ld via VA1, ld via VA3, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

    .balign 64
trap:
    csrr t0, mcause
    li t1, 9                      # environment call from S mode: the step is over
    bne t0, t1, unexpected
    li t0, 0x1800
    csrs mstatus, t0              # MPP = M, so MRET returns to M mode
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0              # mstatus.MDT = 0
    csrw mepc, s3
    mret
unexpected:
    la a0, text_trap
    jal ra, puts
    csrr a0, mcause
    jal ra, puthex
    li a0, 32
    jal ra, putc
    csrr a0, mepc
    jal ra, puthex
    li a0, 32
    jal ra, putc
    csrr a0, mtval
    jal ra, puthex
    li a0, 10
    jal ra, putc
    li a0, 1
    .word 0x0000006b              # bad trap

# Read P + 0x40 into s6 and print "step <s0> <s4> <s5> <s6>".
report:
    mv s1, ra
    li t0, 0x80400040
    ld s6, 0(t0)
    la a0, text_step
    jal ra, puts
    addi a0, s0, 48
    jal ra, putc
    li a0, 32
    jal ra, putc
    mv a0, s4
    jal ra, puthex
    li a0, 32
    jal ra, putc
    mv a0, s5
    jal ra, puthex
    li a0, 32
    jal ra, putc
    mv a0, s6
    jal ra, puthex
    li a0, 10
    jal ra, putc
    mv ra, s1
    ret

# The UARTLite transmit register is at 0x40600004.
putc:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret
puts:
    li t0, 0x40600004
1:  lbu t1, 0(a0)
    beqz t1, 2f
    sw t1, 0(t0)
    addi a0, a0, 1
    j 1b
2:  ret
puthex:                           # a0 as 16 hexadecimal digits
    li t0, 0x40600004
    li t2, 60
1:  srl t1, a0, t2
    andi t1, t1, 15
    addi t1, t1, 48
    li t3, 58
    bltu t1, t3, 2f
    addi t1, t1, 39
2:  sw t1, 0(t0)
    addi t2, t2, -4
    bgez t2, 1b
    ret

    .section .rodata
text_step:
    .asciz "step "
text_trap:
    .asciz "trap "
text_done:
    .asciz "done\n"

# Sv39 page tables. Root at 0x80200000: entry 2 maps 0x80000000 as one 1 GiB page (the code),
# entry 4 points to 0x80204000, whose entry 0 points to 0x80208000. There entries 0 and 2 map P, and
# entry 4 maps P3.
    .section .pt, "aw", @progbits
.org 0x10
    .dword 0x200000cf
.org 0x20
    .dword 0x20081001
.org 0x4000
    .dword 0x20082001
.org 0x8000
    .dword 0x201000c7           # VA1
.org 0x8010
    .dword 0x201000c7           # VA2
.org 0x8020
    .dword 0x201004c7           # VA3
.org 0x9000
