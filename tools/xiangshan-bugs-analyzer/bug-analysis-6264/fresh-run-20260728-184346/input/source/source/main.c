// Fetch fall-through crosses Sv39 canonical boundary without IPF.
// ITLB does not check canonical (sign-consistency) for next-line PCs;
// only redirected targets go through backendIPF. When the fall-through
// VA is non-canonical, the ITLB still walks the page table using its
// VPN bits, hitting whatever PTE sits at root[256], and executes code
// from the aliased physical page.
//
// Page tables (Sv39):
//   root[2]   = 1G leaf  PA 0x80000000 RWXAD
//   root[255] -> L1TAB ; L1TAB[0x1FF] = 2M leaf PA 0x81000000
//     => VA 0x3F_FFFF_F000 .. 0x3F_FFFF_FFFF -> PA 0x811F_F000 .. 0x811F_FFFF
//   root[256] -> L1TAB2; L1TAB2[0]    = 2M leaf PA 0x81200000
//     => non-canonical VA 0x40_0000_0000 would alias PA 0x8120_0000

#include <klib.h>

#define ROOT_PA   0x82000000UL
#define L1TAB_PA  0x82004000UL
#define L1TAB2_PA 0x82008000UL
#define FLAG_PA   0x82010000UL

#define PTE_LEAF_RWXAD 0xCF       // V|R|W|X|A|D (S-mode, U=0)

volatile uint32_t trap_count;
volatile uint64_t last_mcause, last_mtval, last_mepc;
volatile uint64_t m_continue;
volatile uint64_t ecall_mcause;
volatile uint64_t trap_resume;
volatile uint64_t f7_mcause, f7_mtval, f7_mepc;
volatile uint32_t f7_trap;

__asm__(
".globl trap_handler\n"
".align 2\n"
"trap_handler:\n"
"  csrr t0, mcause\n"
"  li   t1, 9\n"
"  beq  t0, t1, .Lback_to_m\n"
"  li   t1, 11\n"
"  beq  t0, t1, .Lback_to_m\n"
"  la   t1, last_mcause\n"
"  sd   t0, 0(t1)\n"
"  csrr t0, mtval\n"
"  la   t1, last_mtval\n"
"  sd   t0, 0(t1)\n"
"  csrr t0, mepc\n"
"  la   t1, last_mepc\n"
"  sd   t0, 0(t1)\n"
"  la   t1, trap_count\n"
"  lw   t0, 0(t1)\n"
"  addi t0, t0, 1\n"
"  sw   t0, 0(t1)\n"
"  la   t1, trap_resume\n"
"  ld   t0, 0(t1)\n"
"  bnez t0, .Lresume\n"
"  csrr t0, mepc\n"
"  addi t0, t0, 4\n"
"  csrw mepc, t0\n"
"  mret\n"
".Lresume:\n"
"  csrw mepc, t0\n"
"  mret\n"
".Lback_to_m:\n"
"  la   t1, ecall_mcause\n"
"  sd   t0, 0(t1)\n"
"  la   t1, m_continue\n"
"  ld   t0, 0(t1)\n"
"  csrw mepc, t0\n"
"  li   t0, 0x1800\n"
"  csrs mstatus, t0\n"
"  mret\n"
);
extern char trap_handler[];

__asm__(
".globl f7_s_entry\n"
".align 2\n"
"f7_s_entry:\n"
"  li   t0, 0x3FFFFFFFC0\n"
"  jr   t0\n"
".globl f7_s_continue\n"
"f7_s_continue:\n"
"  la   t2, last_mcause\n"
"  ld   t3, 0(t2)\n"
"  la   t2, f7_mcause\n"
"  sd   t3, 0(t2)\n"
"  la   t2, last_mtval\n"
"  ld   t3, 0(t2)\n"
"  la   t2, f7_mtval\n"
"  sd   t3, 0(t2)\n"
"  la   t2, last_mepc\n"
"  ld   t3, 0(t2)\n"
"  la   t2, f7_mepc\n"
"  sd   t3, 0(t2)\n"
"  la   t2, trap_count\n"
"  lw   t3, 0(t2)\n"
"  la   t2, f7_trap\n"
"  sw   t3, 0(t2)\n"
"  ecall\n"
);
extern char f7_s_entry[];
extern char f7_s_continue[];

static uint64_t csr_read(uint32_t csr)
{
    uint64_t v;
    asm volatile("csrr %0, %1" : "=r"(v) : "i"(csr));
    return v;
}
static void csr_write(uint32_t csr, uint64_t v)
{
    asm volatile("csrw %0, %1" :: "i"(csr), "r"(v));
}

void enter_s(uint64_t entry)
{
    uint64_t mstatus = csr_read(0x300);
    mstatus = (mstatus & ~0x1800UL) | 0x800UL;  // MPP=S
    mstatus &= ~(1UL << 39);                     // MPV=0
    csr_write(0x300, mstatus);
    asm volatile(
        "la t0, 1f\n\t"
        "sd t0, m_continue, t1\n\t"
        "csrw mepc, %0\n\t"
        "mret\n\t"
        "1:\n\t"
        :: "r"(entry) : "t0", "t1", "memory");
}

void build_page_tables(void)
{
    volatile uint64_t *root  = (volatile uint64_t *)ROOT_PA;
    volatile uint64_t *l1    = (volatile uint64_t *)L1TAB_PA;
    volatile uint64_t *l1b   = (volatile uint64_t *)L1TAB2_PA;
    for (int i = 0; i < 512; i++) { root[i] = 0; l1[i] = 0; l1b[i] = 0; }
    root[2]   = ((0x80000000UL >> 2)) | PTE_LEAF_RWXAD;  // 1G identity leaf
    root[255] = (L1TAB_PA >> 2) | 0x01U;                 // non-leaf -> root[255]
    l1[0x1FF] = (0x81000000UL >> 2) | PTE_LEAF_RWXAD;    // 2M leaf @0x81000000
    root[256] = (L1TAB2_PA >> 2) | 0x01U;                // non-leaf -> root[256]
    l1b[0]    = (0x81200000UL >> 2) | PTE_LEAF_RWXAD;    // 2M leaf @0x81200000
    asm volatile("fence rw, rw" ::: "memory");
}

void set_satp_sv39(void)
{
    csr_write(0x180, (8UL << 60) | (ROOT_PA >> 12));
    asm volatile("sfence.vma" ::: "memory");
}

int main()
{
    printf("=== bug: fetch fall-through across canonical boundary ===\n");

    build_page_tables();

    // Full-range NAPOT PMP with RWX (reset PMP denies S).
    csr_write(0x3B0, -1L);      // pmpaddr0
    csr_write(0x3A0, 0x1FUL);   // pmpcfg0 = NAPOT RWX
    csr_write(0x302, 0);        // medeleg
    csr_write(0x303, 0);        // mideleg
    csr_write(0x305, (uint64_t)trap_handler);  // mtvec

    // Fill the boundary page with NOPs.
    for (uint64_t p = 0x811FFFC0UL; p < 0x811FFFC0UL + 0x40; p += 4)
        *(volatile uint32_t *)p = 0x00000013U;  // nop

    // Stub at alias PA 0x81200000.  lui + slli/srli 32 for zero-extend.
    *(volatile uint32_t *)0x81200000UL  = 0x820102B7U;  // lui  t0, 0x82010
    *(volatile uint32_t *)0x81200004UL  = 0x02029293U;  // slli t0, t0, 32
    *(volatile uint32_t *)0x81200008UL  = 0x0202D293U;  // srli t0, t0, 32
    *(volatile uint32_t *)0x8120000CUL  = 0x0FA00313U;  // addi t1, x0, 0xFA
    *(volatile uint32_t *)0x81200010UL  = 0x0062A023U;  // sw   t1, 0(t0)
    *(volatile uint32_t *)0x81200014UL  = 0x00000073U;  // ecall
    *(volatile uint32_t *)FLAG_PA = 0;
    asm volatile("fence rw, rw; fence.i" ::: "memory");

    set_satp_sv39();
    printf("[test] entering S-mode, jr 0x3FFFFFFFC0 (NOP sled), fall-through ...\n");
    trap_count = 0;
    last_mcause = last_mtval = last_mepc = 0;
    f7_mcause = f7_mtval = f7_mepc = 0;
    f7_trap = 0;
    trap_resume = (uint64_t)f7_s_continue;
    enter_s((uint64_t)f7_s_entry);

    uint32_t flag = *(volatile uint32_t *)FLAG_PA;
    printf("[test] back in M, ecall mcause = %lu\n", ecall_mcause);
    printf("[test] trap = %u, mcause = %lu, mtval = 0x%lx, mepc = 0x%lx, alias flag = 0x%x\n",
           f7_trap, f7_mcause, f7_mtval, f7_mepc, flag);

    if (f7_trap >= 1 && f7_mcause == 12 && flag == 0) {
        printf("CORRECT: fall-through to non-canonical VA raised IPF\n");
        return 0;
    }
    if (flag == 0xFA) {
        printf("BUG REPRODUCED: fetch crossed canonical boundary without\n");
        printf("  fault and executed aliased code at PA 0x81200000\n");
        return 1;
    }
    printf("INCONCLUSIVE (trap=%u, mcause=%lu, flag=0x%x)\n",
           f7_trap, f7_mcause, flag);
    return 2;
}
