#include <am.h>
#include <klib.h>
#include <riscv.h>
#include <xsextra.h>

/*
 * Directed cbo.inval scenarios for Kunminghu V2.
 *
 * The configured L1 DCache is 64 KiB, 4-way, with 64-byte blocks and 256
 * sets.  Therefore VA[13:12] are the two alias (color) bits that are not in a
 * 4 KiB page offset.  C12 maps two VAs whose VA[13:12] differ to one PA.
 */
enum {
  PageBytes = 0x1000,
};

#define ROOT_PA           0x81000000UL
#define SHARED_L2_PA      0x81001000UL
#define TEST_L1_PA        0x81002000UL
#define TEST_L0_PA        0x81003000UL
#define SATP_MODE_SV48    (9UL << 60)
#define PTE_LEAF_RWXAD    (PTE_V | PTE_R | PTE_W | PTE_X | PTE_A | PTE_D)

#define VA_C9             0x0000000900000000UL
#define VA_C10            0x0000000900004000UL
#define VA_C11            0x0000000900008000UL
#define VA_C12_HOME       0x000000090000c000UL
#define VA_C12_ALIAS      0x000000090000d000UL

/* All C9 conflict VAs preserve VA[13:6], hence select the target's L1 set. */
#define VA_C9_CONFLICT_BASE  0x0000000900100000UL
#define C9_CONFLICT_COUNT    4

/* Deliberately irregular physical pages avoid a stride-prefetch sequence. */
#define PA_C9             0x87000000UL
#define PA_C10            0x85234000UL
#define PA_C11            0x83564000UL
#define PA_C12            0x819ab000UL

#define C11_VALUE         0xc110c110c110c110UL
#define C12_VALUE         0xc120c120c120c120UL

static const uintptr_t c9_conflict_pas[C9_CONFLICT_COUNT] = {
  0x86101000UL, 0x84456000UL, 0x82a9b000UL, 0x876de000UL,
};

static inline uint64_t pte_table(uintptr_t pa) {
  return (pa >> 2) | PTE_V;
}

static inline uint64_t pte_leaf(uintptr_t pa) {
  return (pa >> 2) | PTE_LEAF_RWXAD;
}

static void map_test_page(uintptr_t va, uintptr_t pa) {
  volatile uint64_t *test_l0 = (volatile uint64_t *)TEST_L0_PA;

  assert((va & (PageBytes - 1)) == 0);
  assert((pa & (PageBytes - 1)) == 0);
  test_l0[(va >> 12) & 0x1ffUL] = pte_leaf(pa);
}

__attribute__((noreturn, aligned(4)))
static void supervisor_trap_handler(void) {
  uintptr_t scause;
  uintptr_t sepc;
  uintptr_t stval;

  asm volatile("csrr %0, scause" : "=r"(scause));
  asm volatile("csrr %0, sepc" : "=r"(sepc));
  asm volatile("csrr %0, stval" : "=r"(stval));
  printf("FAIL: unexpected S-mode trap: scause=0x%lx sepc=0x%lx "
         "stval=0x%lx\n", scause, sepc, stval);
  _halt(1);
  __builtin_unreachable();
}

static void enter_supervisor_mode(void) {
  uintptr_t sstatus;

  asm volatile("csrr %0, sstatus" : "=r"(sstatus));
  sstatus &= ~MSTATUS_SPP(MODE_S);
  sstatus |= MSTATUS_SPP(MODE_S);

  asm volatile(
    "csrw sstatus, %0\n"
    "la t0, 1f\n"
    "csrw sepc, t0\n"
    "sret\n"
    "1:\n"
    :
    : "r"(sstatus)
    : "t0", "memory"
  );
}

static void prepare_machine_state(void) {
  uintptr_t menvcfg;
  uintptr_t mstatus;

  init_pmp();

  /*
   * CBIE=01 makes a lower-privilege cbo.inval execute with flush semantics.
   * That is the architectural policy required by C11/C12: a dirty line is
   * written back before it is invalidated.  CBIE=11 is destructive inval.
   */
  asm volatile("csrr %0, menvcfg" : "=r"(menvcfg));
  menvcfg &= ~(3UL << 4);
  menvcfg |= (1UL << 4) | (1UL << 6) | (1UL << 7);
  asm volatile("csrw menvcfg, %0" : : "r"(menvcfg) : "memory");

  /* Permit S-mode satp writes and sfence.vma regardless of reset state. */
  asm volatile("csrr %0, mstatus" : "=r"(mstatus));
  mstatus &= ~(1UL << 20); /* TVM */
  asm volatile("csrw mstatus, %0" : : "r"(mstatus) : "memory");

  asm volatile("csrw mideleg, %0" : : "r"(0xffffUL));
  asm volatile("csrw medeleg, %0" : : "r"(0xffffUL));
  asm volatile("csrw stvec, %0" : : "r"(supervisor_trap_handler));
}

static void build_page_tables(void) {
  volatile uint64_t *root = (volatile uint64_t *)ROOT_PA;
  volatile uint64_t *shared_l2 = (volatile uint64_t *)SHARED_L2_PA;
  volatile uint64_t *test_l1 = (volatile uint64_t *)TEST_L1_PA;
  uintptr_t test_l2_index = (VA_C9 >> 30) & 0x1ffUL;
  uintptr_t test_l1_index = (VA_C9 >> 21) & 0x1ffUL;
  uintptr_t satp;
  int i;

  /*
   * Fixed, sparse Sv48 tree.  The test only touches the entries below, so no
   * page-table memset/clear loop is needed.  root[0] covers low canonical VAs;
   * two 1 GiB leaves identity-map UART and the image/stack/page-table region.
   */
  root[0] = pte_table(SHARED_L2_PA);
  shared_l2[1] = pte_leaf(0x40000000UL);
  shared_l2[2] = pte_leaf(0x80000000UL);
  shared_l2[test_l2_index] = pte_table(TEST_L1_PA);
  test_l1[test_l1_index] = pte_table(TEST_L0_PA);

  map_test_page(VA_C9, PA_C9);
  for (i = 0; i < C9_CONFLICT_COUNT; ++i) {
    map_test_page(VA_C9_CONFLICT_BASE + (uintptr_t)i * 0x4000UL,
                  c9_conflict_pas[i]);
  }
  map_test_page(VA_C10, PA_C10);
  map_test_page(VA_C11, PA_C11);
  map_test_page(VA_C12_HOME, PA_C12);
  map_test_page(VA_C12_ALIAS, PA_C12);

  asm volatile("fence rw, rw" : : : "memory");
  satp = SATP_MODE_SV48 | (ROOT_PA >> 12);
  asm volatile(
    "csrw satp, %0\n"
    "sfence.vma\n"
    :
    : "r"(satp)
    : "memory"
  );
}

static inline void full_fence(void) {
  asm volatile("fence rw, rw" : : : "memory");
}

__attribute__((always_inline))
static inline void c9_prefetch_and_invalidate(uintptr_t target,
                                              uintptr_t final_conflict) {
  asm volatile(
    /*
     * Build 16 independent dirty victims in sets 1..16.  Warm tag4 first;
     * victim+tag1+tag2+tag3 then push tag4 out of L1 while retaining it in
     * L2.  The final tag4 burst is consequently a stream of L2-hit refills,
     * each evicting a dirty victim and contributing a two-beat ReleaseData.
     */
    "mv t3, %[tag4]\n"
    ".rept 16\n"
    "addi t3, t3, 64\n"
    "ld t2, 0(t3)\n"
    ".endr\n"
    "fence r, r\n"

    "mv t3, %[victim]\n"
    ".rept 16\n"
    "addi t3, t3, 64\n"
    "sd zero, 0(t3)\n"
    ".endr\n"
    "fence rw, rw\n"

    "mv t3, %[tag1]\n"
    ".rept 16\n"
    "addi t3, t3, 64\n"
    "ld t2, 0(t3)\n"
    ".endr\n"
    "fence r, r\n"

    "mv t3, %[tag2]\n"
    ".rept 16\n"
    "addi t3, t3, 64\n"
    "ld t2, 0(t3)\n"
    ".endr\n"
    "fence r, r\n"

    "mv t3, %[tag3]\n"
    ".rept 16\n"
    "addi t3, t3, 64\n"
    "ld t2, 0(t3)\n"
    ".endr\n"
    "fence r, r\n"

    /*
     * Software-prefetch the 16 tag4 lines.  Each hint retires without waiting
     * for refill, so their dirty replacements can keep filling TL-C while the
     * following CBO advances.  The address chain ensures the target prefetch
     * is issued only after all 16 dirty-prefetch hints have been launched.
     */
    "mv t0, %[tag4]\n"
    ".rept 16\n"
    "addi t0, t0, 64\n"
    ".word 0x0012e013\n"
    ".endr\n"
    "andi t2, t0, 0\n"
    "add t0, %[final_conflict], t2\n"
    "mv t1, %[target]\n"
    /*
     * prefetch.r 0(t0), encoded explicitly because this toolchain's ISA
     * string enables Zicbom but not Zicbop.  A software prefetch retires
     * without waiting for its refill; that refill evicts target locally while
     * the following CBO independently reaches L2 and causes a stale-client
     * Probe.  The intended C9 observation is therefore an S1 tag mismatch.
     */
    ".word 0x0012e013\n"
    ".option push\n"
    ".option norvc\n"
    ".globl cbo_c9_inval_pc\n"
    "cbo_c9_inval_pc:\n"
    "cbo.inval 0(t1)\n"
    ".globl cbo_c9_after_pc\n"
    "cbo_c9_after_pc:\n"
    "nop\n"
    ".option pop\n"
    :
    : [victim] "r"(0x82000000UL),
      [tag1] "r"(0x82004000UL),
      [tag2] "r"(0x82008000UL),
      [tag3] "r"(0x8200c000UL),
      [tag4] "r"(0x82010000UL),
      [target] "r"(target),
      [final_conflict] "r"(final_conflict)
    : "t0", "t1", "t2", "t3", "memory"
  );
}

__attribute__((noinline, aligned(64)))
static void c10_invalidate(const void *address) {
  asm volatile(
    ".option push\n"
    ".option norvc\n"
    ".globl cbo_c10_inval_pc\n"
    "cbo_c10_inval_pc:\n"
    "cbo.inval 0(%0)\n"
    ".globl cbo_c10_after_pc\n"
    "cbo_c10_after_pc:\n"
    "nop\n"
    ".option pop\n"
    :
    : "r"(address)
    : "memory"
  );
}

__attribute__((noinline, aligned(64)))
static void c11_invalidate(const void *address) {
  asm volatile(
    ".option push\n"
    ".option norvc\n"
    ".globl cbo_c11_inval_pc\n"
    "cbo_c11_inval_pc:\n"
    "cbo.inval 0(%0)\n"
    ".globl cbo_c11_after_pc\n"
    "cbo_c11_after_pc:\n"
    "nop\n"
    ".option pop\n"
    :
    : "r"(address)
    : "memory"
  );
}

__attribute__((noinline, aligned(64)))
static void c12_invalidate(const void *address) {
  asm volatile(
    ".option push\n"
    ".option norvc\n"
    ".globl cbo_c12_inval_pc\n"
    "cbo_c12_inval_pc:\n"
    "cbo.inval 0(%0)\n"
    ".globl cbo_c12_after_pc\n"
    "cbo_c12_after_pc:\n"
    "nop\n"
    ".option pop\n"
    :
    : "r"(address)
    : "memory"
  );
}

static int run_c9(void) {
  volatile uint64_t *target = (volatile uint64_t *)VA_C9;
  uint64_t after;

  /*
   * First place c3 in L2, then evict it from L1 by filling target+c0+c1+c2.
   * This leaves exactly four valid lines in the target set with target oldest;
   * the asynchronous c3 prefetch below will therefore replace target.
   */
  asm volatile(
    "ld t0, 0(%[c3])\n"
    "andi t0, t0, 0\n"
    "add t1, %[target], t0\n"
    "ld t0, 0(t1)\n"
    "andi t0, t0, 0\n"
    "add t1, %[c0], t0\n"
    "ld t0, 0(t1)\n"
    "andi t0, t0, 0\n"
    "add t1, %[c1], t0\n"
    "ld t0, 0(t1)\n"
    "andi t0, t0, 0\n"
    "add t1, %[c2], t0\n"
    "ld t0, 0(t1)\n"
    "fence rw, rw\n"
    :
    : [target] "r"((uintptr_t)target),
      [c0] "r"(VA_C9_CONFLICT_BASE + 0x0000UL),
      [c1] "r"(VA_C9_CONFLICT_BASE + 0x4000UL),
      [c2] "r"(VA_C9_CONFLICT_BASE + 0x8000UL),
      [c3] "r"(VA_C9_CONFLICT_BASE + 0xc000UL)
    : "t0", "t1", "memory"
  );

  c9_prefetch_and_invalidate((uintptr_t)target,
                             VA_C9_CONFLICT_BASE + 0xc000UL);
  full_fence();
  after = *target;
  return after != 0;
}

static int run_c10(void) {
  volatile uint64_t *target = (volatile uint64_t *)VA_C10;
  uint64_t before;
  uint64_t after;

  before = *target;
  full_fence();
  c10_invalidate((const void *)target);
  full_fence();
  after = *target;
  return before != after;
}

static int run_c11(void) {
  volatile uint64_t *target = (volatile uint64_t *)VA_C11;
  uint64_t after;

  *target = C11_VALUE;
  full_fence();
  c11_invalidate((const void *)target);
  full_fence();
  after = *target;
  return after != C11_VALUE;
}

static int run_c12(void) {
  volatile uint64_t *home = (volatile uint64_t *)VA_C12_HOME;
  volatile uint64_t *alias = (volatile uint64_t *)VA_C12_ALIAS;
  uint64_t after;

  *home = C12_VALUE;
  full_fence();
  /* Do not touch alias before this instruction: the dirty line stays at home. */
  c12_invalidate((const void *)alias);
  full_fence();
  after = *alias;
  return after != C12_VALUE;
}

int main(void) {
  int failures = 0;

  prepare_machine_state();
  enter_supervisor_mode();
  build_page_tables();

  printf("Sv48 sparse page table active; running C9-C12\n");

  failures += run_c9();
  failures += run_c10();
  failures += run_c11();
  failures += run_c12();

  if (failures != 0) {
    printf("FAIL: %d functional postcondition(s)\n", failures);
    return 1;
  }

  printf("PASS: C9-C12 functional postconditions\n");
  return 0;
}
