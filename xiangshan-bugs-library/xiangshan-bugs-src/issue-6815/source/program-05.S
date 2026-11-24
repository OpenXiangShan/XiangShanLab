.section .text.init
.option norvc
.globl _start
_start:
    # 0. Cold-boot initialization for standalone XiangShan ./build/emu:
    csrw mstatus, x0
    csrw mie, x0
    li t0, 0x3fffffff
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0

    # 1. Enable Zicfilp Landing Pad in M-mode (mseccfg.MLPE = bit 10 of CSR 0x747)
    li t0, 0x400
    csrs 0x747, t0            # mseccfg.MLPE = 1

    # 2. Set M-mode trap handler
    la t0, trap_handler
    csrw mtvec, t0
    li s0, 0                  # s0: total trap counter
    li s1, 0                  # s1: 1st trap mcause
    li s2, 0                  # s2: last trap mcause

    # 3. Prepare MRET with:
    #    - mstatus.MPELP = 1 (bit 41 -> sets ELP = LP_EXPECTED on mret)
    #    - mstatus.MPP   = 3 (M-mode)
    #    - mstatus.MPIE  = 1 (bit 7  -> enables mstatus.MIE = 1 on mret)
    #    - mip.SSIP      = 1 (bit 1) and mie.SSIE = 1 (bit 1) -> pending enabled interrupt!
    li t0, 1
    slli t0, t0, 41
    csrs mstatus, t0          # mstatus.MPELP = 1
    li t0, (3 << 11) | 0x80
    csrs mstatus, t0          # mstatus.MPP = 3, mstatus.MPIE = 1

    li t0, 0x2
    csrs mip, t0              # mip.SSIP = 1
    csrs mie, t0              # mie.SSIE = 1

    la x30, scratch_line
    la t0, fault_insn
    csrw mepc, t0
    mret

.align 4
.global fault_insn
fault_insn:
    # At fault_insn:
    #   - Asynchronous interrupt (SSIP) is pending and enabled (mip.SSIP=1, mie.SSIE=1, mstatus.MIE=1)
    #   - Zicfilp ELP = 1 (LP_EXPECTED), and sc.d is NOT an lpad instruction (EX_SWC = 18)
    # - Expected (RISC-V Priv Spec §3.1.9, Sail & Spike):
    #   Trap 1: Asynchronous interrupt (mcause = 0x8000000000000001) takes priority over synchronous exception;
    #   Trap 2: Upon returning to fault_insn with ELP = 1, sc.d raises Software Check (mcause = 18).
    #   Total traps (s0) = 2.
    # - Actual on XiangShan (kunminghu-v3 & zicfilp PR #6801):
    #   Rob.scala:610 sets hasWaitForward := true.B on sc.d without checking !enqHasException,
    #   forcing intrEnable = false.B at Rob.scala:768. The pending asynchronous interrupt is dropped (s0 = 1)!
    sc.d x27, x29, 0(x30)

    csrr s3, mstatus
    csrr x28, mip
    andi x28, x28, 0x2a

    csrw mie, x0
    li t0, 0x22
    csrc mip, t0
    j finish

.align 4
trap_handler:
    addi s0, s0, 1
    csrr t1, mcause
    mv s2, t1
    li t2, 1
    bne s0, t2, 1f
    mv s1, t1                 # record 1st trap mcause in s1
1:
    bltz t1, async_irq_ret    # if mcause < 0 (asynchronous interrupt), handle IRQ

    # Synchronous exception (mcause = 18): clear mstatus.MPELP (bit 41) and advance mepc + 4
    li t0, 1
    slli t0, t0, 41
    csrc mstatus, t0
    csrr t0, mepc
    addi t0, t0, 4
    csrw mepc, t0
    mret

async_irq_ret:
    # Acknowledge asynchronous interrupt, preserve MPELP and mepc
    csrw mie, x0
    li t0, 0x22
    csrc mip, t0
    li t0, 0x88
    csrc mstatus, t0
    mret

finish:
    la t0, sig_out
    sd s0,  0(t0)             # sig_out[0]: total trap_count (Expected: 2; Actual on XiangShan: 1)
    sd s1,  8(t0)             # sig_out[1]: 1st mcause (Expected: 0x8000000000000001; Actual: 18)
    sd x28, 16(t0)            # sig_out[2]: mip & 0x2a after fault_insn (Expected: 0; Actual: 2)
    sd s3,  24(t0)            # sig_out[3]: mstatus after fault_insn

    la t0, 1f
    csrw mtvec, t0
    la t1, tohost
    li t2, 1
    sd t2, 0(t1)

    li t3, 2
    bne s0, t3, bug_triggered

.balign 256
.globl spec_passed
spec_passed:
    .word 0x0000006b          # PC = 0x80000200: Spec-compliant (trap_count == 2)
1:  j 1b

.balign 256
.globl bug_triggered
bug_triggered:
    .word 0x0000006b          # PC = 0x80000300: BUG TRIGGERED (pending IRQ dropped, trap_count == 1!)
    j 1b

.section .tohost,"aw",@progbits
.align 6
.globl tohost
tohost: .dword 0
.globl fromhost
fromhost: .dword 0

.section .data
.align 6
.globl begin_signature
begin_signature:
.globl sig_out
sig_out:
    .dword 0, 0, 0, 0
.globl end_signature
end_signature:
scratch_line:
    .dword 0, 0
