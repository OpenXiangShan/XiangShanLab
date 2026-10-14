    .section .text
    .globl _start
    .option norvc
    .option norelax

_start:
    addi t0, zero, 1
    addi t1, zero, 2
    add  t2, t0, t1
    xor  t3, t2, t1
    ori  t4, t3, 8
    addi t5, t4, 1

    # 退出码为 0
    addi a0, zero, 0

    # 香山/NEMU 仿真结束指令
    .word 0x0000006b

    # 避免 BPU 提前预测、取指到程序末尾之外的全零区域
    .rept 64
    nop
    .endr