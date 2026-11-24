.option norvc
.section .text.init
.globl _start
_start:
    # ---------------- M-mode: CSR 基础 + 建 Sv39 页表(先表后 satp) ----------------
    la   t0, m_trap               # M-mode 兜底 handler(停车, 不应到达)
    csrw mtvec, t0
    li   t0, 0xb004               # medeleg: illegal|ipf|lpf|spf 委派 S
    csrw medeleg, t0
    li   t0, 0x1f                 # PMP0: NAPOT RWX 全空间(L=0)
    csrw pmpcfg0, t0
    li   t0, -1
    csrw pmpaddr0, t0
    la   t0, pt_area              # 清零 root + L1 + L0 (3*512 表项)
    li   t1, 0x600
1:  sd   x0, 0(t0)
    addi t0, t0, 8
    addi t1, t1, -1
    bnez t1, 1b
    la   t0, pt_area
    li   t5, 4096
    add  t1, t0, t5               # L1 基址
    srli t1, t1, 12
    slli t1, t1, 10
    ori  t1, t1, 1
    li   t5, 16
    add  t2, t0, t5
    sd   t1, 0(t2)                # root[2] -> L1
    li   t2, 0x200000cf           # L1[0] = 2MB 大页 @0x80000000, V|R|W|X|A|D
    li   t5, 4096
    add  t3, t0, t5
    sd   t2, 0(t3)
    li   t5, 8192
    add  t1, t0, t5               # L0 基址
    srli t1, t1, 12
    slli t1, t1, 10
    ori  t1, t1, 1
    li   t5, 4104
    add  t3, t0, t5
    sd   t1, 0(t3)                # L1[1] -> L0 (L0 覆盖 VA 0x80200000-0x803FFFFF)
    # L0 叶子: idx0/idx7/idx8 同属性 V|R|W|A|D, VA=PA 恒等
    li   t3, 0x200800c7           # L0[0]: 0x80200000
    li   t5, 0x2000
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x20081cc7           # L0[7]: 0x80207000
    li   t5, 0x2038
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200804c7           # L0[1]: 0x80201000 RW (dummy)
    li   t5, 0x2008
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200818c7           # L0[6]: 0x80206000 RW (dummy)
    li   t5, 0x2030
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200824c7           # L0[9]: 0x80209000 RW (dummy)
    li   t5, 0x2048
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200828c7           # L0[10]: 0x8020a000 RW (dummy)
    li   t5, 0x2050
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x20082cc7           # L0[11]: 0x8020b000 RW (dummy)
    li   t5, 0x2058
    add  t4, t0, t5
    sd   t3, 0(t4)
    li   t3, 0x200820c7           # L0[8]: 0x80208000
    li   t5, 0x2040
    add  t4, t0, t5
    sd   t3, 0(t4)
    # ---------------- 开分页, mret 进 S-mode (全程无 sfence.vma) ----------------
    # 注: L0 页共装入 8 条非零表项(0,1,6,7,8,9,10,11); 少于 7 条时 bug 不触发
    la   t1, pt_area
    srli t1, t1, 12
    li   t0, 8
    slli t0, t0, 60
    or   t0, t0, t1
    csrw satp, t0                 # satp: Bare -> Sv39, 页表已全部写入内存
    li   t1, 0x1800               # mstatus.MPP = 01 (S-mode)
    csrrc t0, mstatus, t1
    li   t1, 0x800
    csrs  mstatus, t1
    la   t1, s_entry
    csrw mepc, t1
    mret

.section .text
s_entry:
    la   t0, s_trap
    csrw stvec, t0
    # PTE dump: 下列 ld 的表项值直接出现在 commit trace 的 data 字段
    la   t1, pt_area
    li   t5, 0x2000
    add  t1, t1, t5
    ld   t2, 0(t1)                # PTE[0] -> data 0x00000000200800c7
    li   t5, 0x40
    add  t1, t1, t5
    ld   t2, 0(t1)                # PTE[8] -> data 0x00000000200820c7
    li   t1, 0x80200000
    ld   t2, 0(t1)                # [CONTROL] first access to L0 idx0, the first leaf of
                                  # the same table: retires normally (no trap)

    li   t1, 0x80208000
    ld   t2, 0(t1)                # <<<<< THE BUG-TRIGGERING INSTRUCTION >>>>>
                                  # - First access to L0 idx8 (VA 0x80208000), a fully valid
                                  #   RW leaf mapping (the PTE dump above proves it is in memory)
                                  # - Wrongly raises a load page fault: scause = 13,
                                  #   stval = 0x80208000, sepc = this instruction
                                  # - The trap lands in s_trap; after the probe checks it
                                  #   takes exit 1 (the bug-reproduced marker)
                                  # - Substitution experiments (each a single edit, each verified):
                                  #   (1) FIX BY SFENCE: insert one line `sfence.vma` right after
                                  #       `csrw stvec, t0` in s_entry -> this load succeeds (exit 2)
                                  #   (2) BOUNDARY CONTROL: change `li t1, 0x80208000` above to
                                  #       `li t1, 0x80207000` (L0 idx7, also installed), AND change
                                  #       the expected stval `li t4, 0x80208000` in s_trap to
                                  #       `li t4, 0x80207000` -> succeeds (exit 2)
                                  #   (3) ENTRY-COUNT THRESHOLD: delete the three dummy install
                                  #       quads L0[9]/L0[10]/L0[11] (the `li t3, 0x200824c7`,
                                  #       `0x200828c7`, `0x20082cc7` groups in _start) -> succeeds
                                  #       (exit 2)
    j    2f                       # 出口2: 全部成功(bug 不在 / 已 sfence / 访问 idx<=7)

s_trap:
    csrr t0, scause               # 探针: 值见 commit trace 的 data 字段
    csrr t1, stval
    csrr t2, sepc
    li   t4, 13
    bne  t0, t4, 3f               # 出口3: 有 trap 但 scause != 13
    li   t4, 0x80208000
    bne  t1, t4, 4f               # 出口4: scause=13 但 stval != idx8 VA
    j    1f                       # 出口1: 假 lpf13 复现(scause=13, stval=idx8 VA)

1:  .4byte 0x0000006b             # 出口1: idx8 假 page fault(bug 在)
2:  .4byte 0x0000006b             # 出口2: 无 trap, 全部成功
3:  .4byte 0x0000006b             # 出口3: trap 但非 lpf
4:  .4byte 0x0000006b             # 出口4: lpf 但 stval 不符

m_trap:
    csrr t0, mcause
    csrr t1, mtval
    csrr t2, mepc
5:  j 5b

.section .pagetables
.balign 4096
pt_area:
    .zero 12288
