.section .text
.globl _start
.option norvc
.option norelax
_start:
    la t0, trap
    csrw mtvec, t0
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0            # NAPOT, all memory, RWX
    li t0, 0x80200000           # root page table (satp.PPN = 0x80200)
    li t1, PTE                  # the single 1 GiB leaf, slot 0
    sd t1, 0(t0)
    li t0, 0x8000000000080200
    csrw satp, t0               # Sv39
    sfence.vma x0, x0
    li t0, 0xa00060800          # MPP=S, MPRV=1, SUM=1, MXR=0
    csrs mstatus, t0
    li x1, 0x20000000           # VPN[2]=0 -> slot 0
    ACCESS x2, 0(x1)
    li a0, 1                    # no trap at all: bad
    .4byte 0x0005006b
trap:
    csrr s0, mcause
    li t0, CAUSE                # cause NEMU is expected to raise
    bne s0, t0, bad
    li a0, 0
    .4byte 0x0005006b
bad:
    li a0, 1
    .4byte 0x0005006b
