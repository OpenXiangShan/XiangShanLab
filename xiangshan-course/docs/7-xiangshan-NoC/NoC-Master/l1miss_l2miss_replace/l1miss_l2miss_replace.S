.equ LOCAL_SET_STRIDE,  128 * 1024
.equ TARGET_SET_OFFSET,  16 * 1024
.equ LINE_DATA_SIZE,              8
.equ TARGET_VALUE,       0x12345678
.equ SETTLE_ITERATIONS,         512

.section .text
.globl  main
.type   main, @function

main:
    # Fill all eight ways of one Local TL2 set with valid cache lines.
    la      t0, fill_line_1
    ld      t0, 0(t0)
    ld      t0, 0(t0)
    ld      t0, 0(t0)
    ld      t0, 0(t0)
    ld      t0, 0(t0)
    ld      t0, 0(t0)
    ld      t0, 0(t0)
    ld      t0, 0(t0)

    # Let all fill transactions settle without issuing more memory accesses.
    li      t2, SETTLE_ITERATIONS
.Lsettle:
    addi    t2, t2, -1
    bnez    t2, .Lsettle

    # The target address depends on the completed fill chain. The chain ends
    # at zero, so the dependency does not alter the target address.
    la      t1, target_line
    add     t1, t1, t0

.globl target_load
target_load:
    ld      t3, 0(t1)

.globl target_load_end
target_load_end:
    li      t4, TARGET_VALUE
    bne     t3, t4, fail

    li      a0, 0
    ret

fail:
    li      a0, 1
    ret

.size main, .-main


.section .data.cache_test, "aw", @progbits
.balign LOCAL_SET_STRIDE
.space  TARGET_SET_OFFSET

fill_line_1:
    .dword  fill_line_2

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_2:
    .dword  fill_line_3

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_3:
    .dword  fill_line_4

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_4:
    .dword  fill_line_5

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_5:
    .dword  fill_line_6

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_6:
    .dword  fill_line_7

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_7:
    .dword  fill_line_8

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
fill_line_8:
    .dword  0

.space LOCAL_SET_STRIDE - LINE_DATA_SIZE
.globl target_line
target_line:
    .dword  TARGET_VALUE
