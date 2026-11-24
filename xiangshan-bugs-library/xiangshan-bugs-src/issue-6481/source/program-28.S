control_loop:
    la      t0, control_landing
    j       control_indirect

    .section .text.control, "ax", @progbits
    .globl control_indirect
control_indirect:
    jalr    x0, 0(t0)
