.equ TARGET_VALUE,      0x12345678
.equ TARGET_ALIGNMENT,  128 * 1024
.equ TARGET_OFFSET,      16 * 1024

.section .text
.globl  main
.type   main, @function

main:
    la      t0, target_line

.globl target_load
target_load:
    ld      t1, 0(t0)

.globl target_load_end
target_load_end:
    li      t2, TARGET_VALUE
    bne     t1, t2, fail

    li      a0, 0
    ret

fail:
    li      a0, 1
    ret

.size main, .-main


.section .data.cache_test, "aw", @progbits
.balign TARGET_ALIGNMENT
.space  TARGET_OFFSET
.globl  target_line

target_line:
    .dword  TARGET_VALUE
