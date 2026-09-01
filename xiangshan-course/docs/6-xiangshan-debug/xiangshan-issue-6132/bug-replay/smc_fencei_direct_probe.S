.section .text
.globl _start
.option norvc

.equ PROBE_SHIFT, 12
.equ PROBE_VALUE, 3

_start:
    la      s0, target_func
    la      a1, probe_array

    # Warm old target_func into I-cache using a benign probe line.
    jal     ra, target_func
    addi    s1, a0, 0

    # Patch target_func to:
    #   addi a0, zero, 0x5a
    #   ret
    #   nop
    li      t0, 0x05a00513
    li      t1, 0x00008067
    sw      t0, 0(s0)
    sw      t1, 4(s0)
    sw      zero, 8(s0)

    # Architecturally, the patched function below must not read a1.
    # If stale post-fence code executes, its first instruction loads this
    # target line and should leave a DCache/LSU footprint.
    la      a1, probe_array
    li      t2, PROBE_VALUE
    slli    t2, t2, PROBE_SHIFT
    add     a1, a1, t2
    fence   rw, rw
    fence.i

    jal     ra, target_func
    li      t3, 0x5a
    bne     a0, t3, fail

pass:
    li      gp, 0x600d
    j       pass

fail:
    li      gp, 0xbad
    j       fail

.align 6
target_func:
    # Old body: a stale post-fence issue directly touches the attacker-chosen
    # probe line before the patched addi/ret is fetched.
    ld      t5, 0(a1)
    addi    a0, zero, 0x11
    ret

.section .data
.balign 4096
probe_array:
    .zero 32768
