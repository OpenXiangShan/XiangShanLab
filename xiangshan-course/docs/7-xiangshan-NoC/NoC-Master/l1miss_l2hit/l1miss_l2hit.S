.equ LOCAL_SET_STRIDE,   8 * 1024
.equ TARGET_ALIGNMENT, 128 * 1024
.equ TARGET_SET_OFFSET, 16 * 1024
.equ LINE_DATA_SIZE,              8
.equ SETTLE_ITERATIONS,         512

.section .text
.globl  main
.type   main, @function

main:
    # Warm the target before walking sixteen lines that alias the same L1 set.
    la      t0, target_line
    ld      t1, 0(t0)

    # Carry the warm-up result into the pointer chain as a zero-valued
    # dependency. This prevents the first conflict load from issuing early.
    la      t2, conflict_line_1
    and     t1, t1, zero
    add     t2, t2, t1

    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)
    ld      t2, 0(t2)

    # Let refill and replacement transactions settle without touching memory.
    li      t4, SETTLE_ITERATIONS
.Lsettle:
    addi    t4, t4, -1
    bnez    t4, .Lsettle

.globl target_reload
target_reload:
    # Keep the final target address dependent on the completed conflict chain.
    and     t2, t2, zero
    add     t0, t0, t2
    ld      t1, 0(t0)

.globl target_reload_end
target_reload_end:
    la      t2, conflict_line_1
    bne     t1, t2, fail

    li      a0, 0
    ret

fail:
    li      a0, 1
    ret

.size main, .-main


.section .data.cache_test, "aw", @progbits
.balign TARGET_ALIGNMENT
.space  TARGET_SET_OFFSET
.globl  target_line

target_line:
    .dword  conflict_line_1

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_1:
    .dword  conflict_line_2

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_2:
    .dword  conflict_line_3

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_3:
    .dword  conflict_line_4

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_4:
    .dword  conflict_line_5

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_5:
    .dword  conflict_line_6

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_6:
    .dword  conflict_line_7

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_7:
    .dword  conflict_line_8

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_8:
    .dword  conflict_line_9

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_9:
    .dword  conflict_line_10

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_10:
    .dword  conflict_line_11

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_11:
    .dword  conflict_line_12

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_12:
    .dword  conflict_line_13

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_13:
    .dword  conflict_line_14

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_14:
    .dword  conflict_line_15

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_15:
    .dword  conflict_line_16

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
conflict_line_16:
    .dword  0
