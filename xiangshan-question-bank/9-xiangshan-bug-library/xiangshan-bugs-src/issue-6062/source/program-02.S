csrw mtvec, trap_entry
csrw satp, zero
sfence.vma

# Enable CBO from S-mode.
li t0, 0xf0
csrw menvcfg, t0

# PMP entry 0: no permission for target_data.
# PMP entry 1: catch-all RWX allow.
la t0, target_data
srli t0, t0, 2
li t1, 0x1ff
or t0, t0, t1
csrw pmpaddr0, t0
li t0, -1
csrw pmpaddr1, t0
li t0, (0x1f << 8) | 0x18
csrw pmpcfg0, t0

# Enter S-mode and execute one CBO to the denied block.
mret
la t0, target_data
cbo.clean 0(t0)
